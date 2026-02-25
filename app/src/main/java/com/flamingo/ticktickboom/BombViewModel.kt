package com.flamingo.ticktickboom

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class BombViewModel(private val audio: AudioController) : ViewModel() {

    // 1. THE STATE: Backing property (mutable) and Public property (read-only)
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // 2. THE MASTER JOB: Controls our background physics loop
    private var gameLoopJob: Job? = null

    // Private timing variables (The UI doesn't need to know about these!)
    private var exactElapsedSeconds = 0.0
    private var lastTickRunTimeMs = 0L
    private var isFuseFinished = false
    private var hasPlayedDing = false
    private var hasPlayedAlert = false
    private var hasPlayedFlail = false

    // --- NEW: Hen Specific Trackers ---
    private var lastPlayedCrackStage = 0
    private var hasPlayedWhistle = false
    private var hasPlayedThud = false
    private var hasPlayedSlide = false
    private var postGameJob: Job? = null
    private var logicalHenAnimTime = 0f
    // --- NEW: App Lifecycle Tracker ---
    @Volatile private var isAppInForeground = true

    // 3. THE INTENT PROCESSOR: The only way the UI can talk to the logic
    fun processIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.StartTimer -> handleStart(intent.settings)
            is GameIntent.TogglePause -> handleTogglePause()
            is GameIntent.ToggleHenPause -> handleToggleHenPause()
            is GameIntent.Abort -> handleAbort()
            is GameIntent.Reset -> handleReset()
            is GameIntent.UpdateExplosionOrigin -> {
                _state.update { it.copy(explosionOrigin = intent.offset) }
            }
            // --- NEW ---
            is GameIntent.AppEnteredBackground -> handleAppBackgrounded()
            is GameIntent.AppEnteredForeground -> handleAppForegrounded()
        }
    }

    private fun handleToggleHenPause() {
        if (_state.value.appState != AppState.EXPLODED || _state.value.bombStyle != "HEN") return
        val isNowPaused = !_state.value.isHenPaused
        _state.update { it.copy(isHenPaused = isNowPaused) }

        if (isNowPaused) {
            audio.playPauseInteraction("HEN", true)
            audio.stopSlide()
            audio.stopWhistle()

            // --- THE FIX: A quick coroutine to animate the beak on impact! ---
            viewModelScope.launch {
                // 1. Clamp the beak shut instantly
                _state.update { it.copy(isPainedBeakClosed = true) }

                // 2. Wait for the impact to settle
                delay(150)

                // 3. Open the beak
                _state.update { it.copy(isPainedBeakClosed = false) }

                // 4. Play the sound (Only if we are STILL paused AND the app is open!)
                if (_state.value.isHenPaused && isAppInForeground) {
                    audio.playPainedCluck()
                }
            }
        } else {
            audio.playPauseInteraction("HEN", false)
            // --- THE FIX: RESUME AUDIO IF NEEDED ---
            if (logicalHenAnimTime > 2.5f && logicalHenAnimTime < 4.5f) audio.playWhistle()
            if (logicalHenAnimTime > 6.0f && logicalHenAnimTime < 8.5f) audio.playHenSlide()
        }
    }

    private fun handleStart(settings: TimerSettings) {
        val calculatedDuration = Random.nextInt(settings.minSeconds, settings.maxSeconds + 1)

        exactElapsedSeconds = 0.0
        lastTickRunTimeMs = -1000L
        isFuseFinished = calculatedDuration <= 5

        // --- NEW: Reset trackers on start! ---
        hasPlayedDing = false
        hasPlayedAlert = false
        hasPlayedFlail = false
        lastPlayedCrackStage = 0 // <-- NEW
        hasPlayedWhistle = false // <-- NEW
        hasPlayedThud = false // <-- NEW
        hasPlayedSlide = false // <-- NEW

        // Update the state to RUNNING
        _state.update {
            it.copy(
                appState = AppState.RUNNING,
                bombStyle = settings.style,
                duration = calculatedDuration,
                timeLeft = calculatedDuration.toFloat(),
                isPaused = false,
                isLedOn = false,
                particles = emptyList(),
                smoke = emptyList()
            )
        }

        // --- NEW: Start the Fuse audio immediately if applicable! ---
        if (settings.style == "FUSE") {
            audio.startFuse(isFuseFinished)
        }

        startGameLoop()
    }

    private fun handleTogglePause() {
        if (_state.value.appState != AppState.RUNNING) return

        val isNowPaused = !_state.value.isPaused

        _state.update { it.copy(isPaused = isNowPaused) }
        audio.playPauseInteraction(_state.value.bombStyle, isNowPaused)

        if (isNowPaused) {
            if (_state.value.bombStyle != "FROG") audio.stopAll()
        } else {
            if (_state.value.bombStyle == "FUSE") audio.startFuse(state.value.isCritical)
        }
    }

    private fun handleAbort() {
        gameLoopJob?.cancel()
        postGameJob?.cancel()
        audio.stopAll()
        _state.update { GameState(appState = AppState.SETUP) }
    }

    private fun handleReset() {
        postGameJob?.cancel()
        audio.stopAll()
        _state.update { GameState(appState = AppState.SETUP) }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()

        gameLoopJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTimeNanos = System.nanoTime()

            // --- FIX 1: Add a tracker for UI throttling ---
            var lastUiUpdateMs = 0L

            // The True Game Loop
            while (_state.value.timeLeft > 0.01f) {
                delay(5)

                val currentNanos = System.nanoTime()

                // --- FIX 2: Increase clamp to 0.5 to prevent Time Deletion! ---
                val dt = ((currentNanos - lastTimeNanos) / 1_000_000_000.0).coerceAtMost(0.5)
                lastTimeNanos = currentNanos

                val currentState = _state.value

                if (!currentState.isPaused) {
                    exactElapsedSeconds += dt
                    val newTimeLeft = (currentState.duration - exactElapsedSeconds).coerceAtLeast(0.0).toFloat()
                    val currentRunTimeMs = (exactElapsedSeconds * 1000).toLong()

                    var newIsLedOn = currentState.isLedOn

                    // --- Audio & Trigger Logic ---
                    if (newTimeLeft <= 5f && !isFuseFinished) {
                        isFuseFinished = true
                        if (currentState.bombStyle == "FUSE") audio.dimFuse()
                    }

                    // --- One-Shot Audio Triggers ---
                    if (currentState.bombStyle == "DYNAMITE" && newTimeLeft <= 1.0f && !hasPlayedDing && newTimeLeft > 0f) {
                        audio.playDing()
                        hasPlayedDing = true
                    }
                    if (currentState.bombStyle == "FROG" && newTimeLeft <= 1.05f && !hasPlayedAlert && newTimeLeft > 0f) {
                        audio.playAlert()
                        hasPlayedAlert = true
                    }
                    if (currentState.bombStyle == "FROG" && newTimeLeft <= 1.0f && !hasPlayedFlail && newTimeLeft > 0f) {
                        audio.playFlail()
                        hasPlayedFlail = true
                    }

                    // --- Crack Audio Logic ---
                    if (currentState.bombStyle == "HEN") {
                        val currentCrackStage = when {
                            newTimeLeft <= 1.5f -> 3
                            newTimeLeft <= 3.0f -> 2
                            newTimeLeft <= 4.5f -> 1
                            else -> 0
                        }
                        if (currentCrackStage > lastPlayedCrackStage) {
                            audio.playCrack()
                            lastPlayedCrackStage = currentCrackStage
                        }
                    }

                    // --- UPGRADE: Use <= 5f to perfectly match the visual animation threshold! ---
                    val tickInterval = if (currentState.bombStyle == "HEN") 1000L else if (newTimeLeft <= 5f) 500L else 1000L

                    if (currentRunTimeMs - lastTickRunTimeMs >= tickInterval) {
                        if (newTimeLeft > 0.05f) {
                            if (currentState.bombStyle == "C4") { audio.playTick(); newIsLedOn = true }
                            if (currentState.bombStyle == "DYNAMITE" && newTimeLeft > 1.0) audio.playClockTick()
                            if (currentState.bombStyle == "FROG") audio.playCroak(newTimeLeft <= 5f)
                            if (currentState.bombStyle == "HEN" && newTimeLeft > 6.0f) audio.playHenCluck()
                        }

                        // --- PROPORTIONAL SOFT SYNC ---
                        // 1. Calculate the ideal mathematical grid
                        val idealGridTime = (currentRunTimeMs / tickInterval) * tickInterval

                        // 2. Calculate the exact Time Debt
                        val driftError = currentRunTimeMs - idealGridTime

                        // 3. Proportional Correction: Pay off 25% of the debt!
                        // (We cap the maximum possible jump at 25ms just in case of a 500ms mega-freeze)
                        val proportionalCorrection = (driftError * 0.25).toLong().coerceAtMost(25L)

                        // 4. Apply the smooth, scaling correction
                        lastTickRunTimeMs = currentRunTimeMs - proportionalCorrection
                    }

                    if (newIsLedOn && (currentRunTimeMs - lastTickRunTimeMs > 50)) newIsLedOn = false

                    // --- FIX 3: Throttle UI updates to ~60fps (16ms) ---
                    // We also force an immediate update if the LED state flips so the flash never gets missed!
                    if (currentRunTimeMs - lastUiUpdateMs >= 16L || newIsLedOn != currentState.isLedOn) {
                        _state.update {
                            it.copy(
                                timeLeft = newTimeLeft,
                                isLedOn = newIsLedOn
                            )
                        }
                        lastUiUpdateMs = currentRunTimeMs
                    }
                }
            }

            if (!_state.value.isPaused) triggerExplosion()
        }
    }

    private suspend fun triggerExplosion() {
        audio.stopAll()

        // --- RESTORED: The actual BOOM! ---
        audio.playExplosion()

        // --- STEP 3 PREVIEW: Coroutine Optimization ---
        // We calculate particles on the Default (Background) thread so the UI doesn't stutter!
        val (newParticles, newSmoke) = withContext(Dispatchers.Default) {
            val colorsList = listOf(androidx.compose.ui.graphics.Color(0xFFEF4444), androidx.compose.ui.graphics.Color(0xFFFB923C), androidx.compose.ui.graphics.Color.Yellow, androidx.compose.ui.graphics.Color.White)

            val p = List(100) { i ->
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                Particle(
                    id = i,
                    dirX = cos(angle),
                    dirY = sin(angle),
                    velocity = Random.nextFloat() * 800f + 200f,
                    size = Random.nextFloat() * 5f + 3f,
                    color = colorsList.random(),
                    rotationSpeed = Random.nextFloat() * 20f - 10f
                )
            }

            val s = List(30) {
                SmokeParticle(
                    x = 0f, y = 0f,
                    vx = Random.nextFloat() * 100f - 50f,
                    vy = Random.nextFloat() * 100f - 50f,
                    size = Random.nextFloat() * 40f + 20f,
                    alpha = 0.8f, life = 1f, maxLife = 1f
                )
            }
            Pair(p, s)
        }

        _state.update {
            it.copy(
                appState = AppState.EXPLODED,
                timeLeft = 0f,
                particles = newParticles,
                smoke = newSmoke
            )
        }

        // 1. THE TRIGGER: Needs to be inside triggerExplosion!
        if (_state.value.bombStyle == "HEN") startHenPostGameLoop()
    } // <-- End of triggerExplosion

    // 2. THE NEW LOOP: Needs to be INSIDE the BombViewModel class!
    private fun startHenPostGameLoop() {
        postGameJob?.cancel()
        postGameJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTimeNanos = System.nanoTime()
            var lastVolumeUpdateTime = 0L
            var painedCluckTimer = 0f
            var nextPainedCluckTarget = Random.nextFloat() * 2f + 1f

            // --- NEW: Internal tracker just for Audio! ---
            logicalHenAnimTime = 2.5f

            _state.update { it.copy(isHenPaused = false, isPainedBeakClosed = false) }

            while (_state.value.appState == AppState.EXPLODED) {
                delay(10)
                val currentNanos = System.nanoTime()
                val dt = ((currentNanos - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastTimeNanos = currentNanos

                val currentState = _state.value

                if (currentState.isHenPaused) {
                    // THE FIX: Completely freeze the cluck math if we are in the background!
                    if (isAppInForeground) {
                        painedCluckTimer += dt
                        if (painedCluckTimer >= nextPainedCluckTarget) {
                            // 1. Close the beak
                            _state.update { it.copy(isPainedBeakClosed = true) }

                            // 2. Wait for 150ms
                            delay(150)

                            // 3. Open the beak
                            _state.update { it.copy(isPainedBeakClosed = false) }

                            // 4. Play sound
                            if (_state.value.isHenPaused && isAppInForeground) {
                                audio.playPainedCluck()
                            }
                            lastTimeNanos = System.nanoTime()
                            painedCluckTimer = 0f
                            nextPainedCluckTarget = Random.nextFloat() * 2f + 1f
                        }
                    } else {
                        // Just update the clock so we don't get a massive 'dt' jump when we return
                        lastTimeNanos = System.nanoTime()
                    }
                } else {
                    logicalHenAnimTime += dt

                    if (logicalHenAnimTime > 2.5f && !hasPlayedWhistle) { audio.playWhistle(); hasPlayedWhistle = true }
                    if (logicalHenAnimTime > 4.5f && !hasPlayedThud) {
                        audio.stopWhistle() // THE FIX: Forcefully cut the whistle on impact!
                        audio.playGlassTap()
                        hasPlayedThud = true
                    }
                    if (logicalHenAnimTime > 6.0f && !hasPlayedSlide) { audio.playHenSlide(); hasPlayedSlide = true }

                    // THE FIX: Grab the current time before doing the math!
                    val currentMs = System.currentTimeMillis()

                    if (logicalHenAnimTime > 6.0f && (currentMs - lastVolumeUpdateTime > 50)) {
                        val slideProgress = (logicalHenAnimTime - 6.0f) / 2.5f
                        audio.updateSlideVolume((1f - slideProgress).coerceIn(0f, 1f))
                        lastVolumeUpdateTime = currentMs
                    }

                    // THE GOOD PRACTICE FIX: Kill the coroutine when she is off-screen!
                    if (logicalHenAnimTime > 9.0f) {
                        break // Breaks out of the while loop and ends the Coroutine!
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }

    private fun handleAppBackgrounded() {
        isAppInForeground = false
        val currentState = _state.value

        // 1. Pause the ticking bomb
        if (currentState.appState == AppState.RUNNING && !currentState.isPaused) {
            handleTogglePause()
        }
        // 2. Pause the Hen Explosion animation!
        else if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && !currentState.isHenPaused) {
            // THE FIX: Only auto-pause if the slide hasn't finished yet!
            if (logicalHenAnimTime <= 9.0f) {
                handleToggleHenPause()
            }
        }
    }

    private fun handleAppForegrounded() {
        isAppInForeground = true
        val currentState = _state.value

        // 1. Hen Auto-Resume: ONLY if falling (< 4.5s) OR sliding off-screen but still active (> 7.0s to 9.0s)
        if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && currentState.isHenPaused) {
            // THE FIX: Add an explicit ceiling so she is ignored forever after 9.0s!
            if (logicalHenAnimTime < 4.5f || (logicalHenAnimTime > 7.0f && logicalHenAnimTime <= 9.0f)) {
                handleToggleHenPause()
            }
        }

        // 2. Frog Flail Resume: If it's a Frog, running, paused, and in the critical panic phase
        if (currentState.appState == AppState.RUNNING && currentState.bombStyle == "FROG" && currentState.isPaused && currentState.timeLeft <= 1.0f) {
            audio.playFlail()
        }
    }
}

