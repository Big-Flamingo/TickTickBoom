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
    private var exactElapsedSeconds = 0f
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
        } else {
            audio.playPauseInteraction("HEN", false)
            // --- THE FIX: RESUME AUDIO IF NEEDED ---
            if (logicalHenAnimTime > 2.5f && logicalHenAnimTime < 4.5f) audio.playWhistle()
            if (logicalHenAnimTime > 6.0f && logicalHenAnimTime < 8.5f) audio.playHenSlide()
        }
    }

    private fun handleStart(settings: TimerSettings) {
        val calculatedDuration = Random.nextInt(settings.minSeconds, settings.maxSeconds + 1)

        exactElapsedSeconds = 0f
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

            // The True Game Loop
            while (_state.value.timeLeft > 0.01f) {
                // We use a tiny delay so we don't lock up the CPU, achieving roughly 60+ ticks per second
                delay(10)

                val currentNanos = System.nanoTime()
                val dt = ((currentNanos - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastTimeNanos = currentNanos

                val currentState = _state.value

                if (!currentState.isPaused) {
                    exactElapsedSeconds += dt
                    val newTimeLeft = (currentState.duration - exactElapsedSeconds).coerceAtLeast(0f)
                    val currentRunTimeMs = (exactElapsedSeconds * 1000).toLong()

                    var newIsLedOn = currentState.isLedOn

                    // --- Audio & Trigger Logic ---
                    if (newTimeLeft <= 5f && !isFuseFinished) {
                        isFuseFinished = true
                        if (currentState.bombStyle == "FUSE") audio.dimFuse()
                    }

                    // --- NEW: Restored One-Shot Audio Triggers ---
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

                    // --- THE FIX: Crack Audio Logic ---
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

                    val tickInterval = if (currentState.bombStyle == "HEN") 1000L else if (newTimeLeft < 5) 500L else 1000L

                    if (currentRunTimeMs - lastTickRunTimeMs >= tickInterval) {
                        if (currentState.bombStyle == "C4") { audio.playTick(); newIsLedOn = true }
                        if (currentState.bombStyle == "DYNAMITE" && newTimeLeft > 1.0) audio.playClockTick()
                        if (currentState.bombStyle == "FROG") audio.playCroak(newTimeLeft < 5)

                        // --- FIX: Only play standard clucks if she hasn't started panicking (6.0s)! ---
                        if (currentState.bombStyle == "HEN" && newTimeLeft > 6.0f) {
                            audio.playHenCluck()
                        }

                        lastTickRunTimeMs = currentRunTimeMs
                    }

                    if (newIsLedOn && (currentRunTimeMs - lastTickRunTimeMs > 50)) newIsLedOn = false

                    // Push the new math to the UI!
                    _state.update {
                        it.copy(
                            timeLeft = newTimeLeft,
                            isLedOn = newIsLedOn
                        )
                    }
                }
            }

            // Loop finished! Time to explode!
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
                    painedCluckTimer += dt
                    if (painedCluckTimer >= nextPainedCluckTarget) {
                        _state.update { it.copy(isPainedBeakClosed = true) }
                        audio.playPainedCluck()
                        delay(150)
                        _state.update { it.copy(isPainedBeakClosed = false) }
                        lastTimeNanos = System.nanoTime()
                        painedCluckTimer = 0f
                        nextPainedCluckTarget = Random.nextFloat() * 2f + 1f
                    }
                } else {
                    // --- THE FIX: Only update internal logic! ---
                    logicalHenAnimTime += dt

                    if (logicalHenAnimTime > 2.5f && !hasPlayedWhistle) { audio.playWhistle(); hasPlayedWhistle = true }
                    if (logicalHenAnimTime > 4.5f && !hasPlayedThud) { audio.playGlassTap(); hasPlayedThud = true }
                    if (logicalHenAnimTime > 6.0f && !hasPlayedSlide) { audio.playHenSlide(); hasPlayedSlide = true }

                    val currentMs = currentNanos / 1_000_000
                    if (logicalHenAnimTime > 6.0f && (currentMs - lastVolumeUpdateTime > 50)) {
                        lastVolumeUpdateTime = currentMs
                        val slideProgress = (logicalHenAnimTime - 6.0f) / 2.5f
                        val fadeVol = (1f - slideProgress).coerceIn(0f, 1f)
                        audio.updateSlideVolume(fadeVol)
                    }
                    // REMOVED: _state.update { it.copy(henAnimTime = newAnimTime) }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
    }
}