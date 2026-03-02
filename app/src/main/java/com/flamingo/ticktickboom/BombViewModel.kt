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

class BombViewModel(private val audio: AudioController, private val groupManager: GroupPresetManager) : ViewModel() {

    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()
    private var gameLoopJob: Job? = null

    // --- NEW: Tracks which preset is currently playing so we can update it ---
    private var activePresetId: String? = null

    // --- NEW UNIFIED CLOCK SYSTEM ---
    private var exactElapsedSeconds = 0.0 // Only used to feed the UI Throttler so it doesn't freeze
    private var internalTimeLeft = 0.0    // The master countdown clock
    private var nextAudioTickTime = 0.0   // The exact boundary we are waiting to cross!
    private var lastLedTurnOnTimeMs = 0L

    private var isFuseFinished = false
    private var hasPlayedDing = false
    private var hasPlayedAlert = false
    private var hasPlayedFlail = false

    private var lastPlayedCrackStage = 0
    private var hasPlayedWhistle = false
    private var hasPlayedThud = false
    private var hasPlayedSlide = false
    private var postGameJob: Job? = null
    private var logicalHenAnimTime = 0f
    @Volatile private var isAppInForeground = true

    // Calculates the next whole number (or half number) downwards
    private fun getNextTickBoundary(currentTime: Double, style: String): Double {
        val interval = if (style == "HEN") 1.0 else if (currentTime <= 5.0) 0.5 else 1.0
        // THE FIX: Floor naturally snaps to the exact current integer!
        return kotlin.math.floor(currentTime / interval) * interval
    }

    fun processIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.StartTimer -> handleStart(intent.settings)
            is GameIntent.TogglePause -> handleTogglePause()
            is GameIntent.ToggleHenPause -> handleToggleHenPause()
            is GameIntent.Abort -> handleAbort()
            is GameIntent.Reset -> handleReset()
            is GameIntent.UpdateExplosionOrigin -> { _state.update { it.copy(explosionOrigin = intent.offset) } }
            is GameIntent.StartGroupTimer -> handleGroupStart(intent.preset, intent.style)
            is GameIntent.NextPlayer -> handlePlayerSwap(isNext = true)
            is GameIntent.PreviousPlayer -> handlePlayerSwap(isNext = false)
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

            viewModelScope.launch {
                _state.update { it.copy(isPainedBeakClosed = true) }
                delay(150)
                _state.update { it.copy(isPainedBeakClosed = false) }
                if (_state.value.isHenPaused && isAppInForeground) audio.playPainedCluck()
            }
        } else {
            audio.playPauseInteraction("HEN", false)
            if (logicalHenAnimTime > 2.5f && logicalHenAnimTime < 4.5f) audio.playWhistle()
            if (logicalHenAnimTime > 6.0f && logicalHenAnimTime < 8.5f) audio.playHenSlide()
        }
    }

    private fun handleStart(settings: TimerSettings) {
        val calculatedDuration = Random.nextInt(settings.minSeconds, settings.maxSeconds + 1)

        exactElapsedSeconds = 0.0
        internalTimeLeft = calculatedDuration.toDouble()
        nextAudioTickTime = getNextTickBoundary(internalTimeLeft, settings.style)
        isFuseFinished = calculatedDuration <= 5

        hasPlayedDing = false; hasPlayedAlert = false; hasPlayedFlail = false
        lastPlayedCrackStage = 0; hasPlayedWhistle = false; hasPlayedThud = false; hasPlayedSlide = false

        _state.update {
            it.copy(
                appState = AppState.RUNNING,
                playMode = PlayMode.SOLO,
                bombStyle = settings.style,
                duration = calculatedDuration,
                timeLeft = calculatedDuration.toFloat(),
                isPaused = false,
                isLedOn = false,
                particles = emptyList(),
                smoke = emptyList()
            )
        }

        if (settings.style == "FUSE") audio.startFuse(isFuseFinished)
        startGameLoop()
    }

    private fun handleGroupStart(preset: GroupPreset, style: String) {
        val playingRoster = preset.players.filter { !it.isAbsent && !it.isEliminated }
        if (playingRoster.isEmpty()) return

        val startingPlayer = playingRoster[0]

        activePresetId = preset.id

        exactElapsedSeconds = 0.0
        internalTimeLeft = startingPlayer.timeLeft.toDouble()
        nextAudioTickTime = getNextTickBoundary(internalTimeLeft, style)
        isFuseFinished = startingPlayer.timeLeft <= 5f

        hasPlayedDing = false; hasPlayedAlert = false; hasPlayedFlail = false
        lastPlayedCrackStage = 0; hasPlayedWhistle = false; hasPlayedThud = false; hasPlayedSlide = false

        _state.update {
            it.copy(
                appState = AppState.RUNNING,
                playMode = PlayMode.GROUP,
                bombStyle = style,
                activePlayers = playingRoster,
                currentPlayerIndex = 0,
                resetTimeOnExplosion = preset.resetOnExplosion,
                duration = preset.defaultTime.toInt(),
                timeLeft = startingPlayer.timeLeft,
                isPaused = false,
                isLedOn = false,
                particles = emptyList(),
                smoke = emptyList()
            )
        }

        if (style == "FUSE") audio.startFuse(isFuseFinished)
        startGameLoop()
    }

    private fun handlePlayerSwap(isNext: Boolean) {
        val currentState = _state.value
        if (currentState.playMode != PlayMode.GROUP || currentState.activePlayers.isEmpty()) return

        val updatedPlayers = currentState.activePlayers.toMutableList()
        val currentIndex = currentState.currentPlayerIndex
        updatedPlayers[currentIndex] = updatedPlayers[currentIndex].copy(timeLeft = currentState.timeLeft)

        var nextIndex = currentIndex
        do {
            nextIndex = if (isNext) {
                (nextIndex + 1) % updatedPlayers.size
            } else {
                if (nextIndex - 1 < 0) updatedPlayers.size - 1 else nextIndex - 1
            }
        } while (updatedPlayers[nextIndex].isEliminated || updatedPlayers[nextIndex].isAbsent)

        val nextPlayerTime = updatedPlayers[nextIndex].timeLeft

        internalTimeLeft = nextPlayerTime.toDouble()
        nextAudioTickTime = getNextTickBoundary(internalTimeLeft, currentState.bombStyle)
        isFuseFinished = nextPlayerTime <= 5f

        if (nextPlayerTime > 1.0f) hasPlayedDing = false
        if (nextPlayerTime > 1.05f) hasPlayedAlert = false
        if (nextPlayerTime > 1.0f) hasPlayedFlail = false

        lastPlayedCrackStage = when {
            nextPlayerTime <= 1.5f -> 3
            nextPlayerTime <= 3.0f -> 2
            nextPlayerTime <= 4.5f -> 1
            else -> 0
        }

        _state.update {
            it.copy(
                activePlayers = updatedPlayers,
                currentPlayerIndex = nextIndex,
                timeLeft = nextPlayerTime,
                isLedOn = false
            )
        }
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
            var lastUiUpdateMs = 0L

            while (_state.value.timeLeft > 0.01f) {
                delay(5)

                val currentNanos = System.nanoTime()
                val dt = ((currentNanos - lastTimeNanos) / 1_000_000_000.0).coerceAtMost(0.5)
                lastTimeNanos = currentNanos

                val currentState = _state.value

                if (!currentState.isPaused) {
                    exactElapsedSeconds += dt
                    internalTimeLeft -= dt

                    val newTimeLeft = internalTimeLeft.coerceAtLeast(0.0).toFloat()
                    val currentRunTimeMs = (exactElapsedSeconds * 1000).toLong()

                    var newIsLedOn = currentState.isLedOn

                    if (newTimeLeft <= 5f && !isFuseFinished) {
                        isFuseFinished = true
                        if (currentState.bombStyle == "FUSE") audio.dimFuse()
                    }

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

                    // THE FIX: We wait to cross the exact next mathematical boundary!
                    if (internalTimeLeft <= nextAudioTickTime) {

                        newIsLedOn = true // <-- MOVED OUTSIDE! Now the metronome pulses for all bombs!

                        if (newTimeLeft > 0.05f) {
                            if (currentState.bombStyle == "C4") { audio.playTick() } // <-- Removed newIsLedOn from here
                            if (currentState.bombStyle == "DYNAMITE" && newTimeLeft > 1.0) audio.playClockTick()
                            if (currentState.bombStyle == "FROG") audio.playCroak(newTimeLeft <= 5f)
                            if (currentState.bombStyle == "HEN" && newTimeLeft > 6.0f) audio.playHenCluck()
                        }

                        // Calculate the NEXT boundary to aim for!
                        val isFast = internalTimeLeft <= 5.0
                        val interval = if (currentState.bombStyle == "HEN") 1.0 else if (isFast) 0.5 else 1.0
                        nextAudioTickTime -= interval

                        // Failsafe in case of huge lag spikes
                        while (nextAudioTickTime >= internalTimeLeft) nextAudioTickTime -= interval

                        lastLedTurnOnTimeMs = currentRunTimeMs
                    }

                    if (newIsLedOn && (currentRunTimeMs - lastLedTurnOnTimeMs > 50)) newIsLedOn = false

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
        audio.playExplosion()

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

        val finalState = _state.value
        var finalPlayers = finalState.activePlayers

        if (finalState.playMode == PlayMode.GROUP && finalPlayers.isNotEmpty()) {
            val updatedPlayers = finalPlayers.toMutableList()
            val doomedIndex = finalState.currentPlayerIndex

            // --- FIX 1: Instantly reset the loser's time so they are ready for the next round! ---
            updatedPlayers[doomedIndex] = updatedPlayers[doomedIndex].copy(
                isEliminated = true,
                isAbsent = true,
                timeLeft = finalState.duration.toFloat() // <-- Resets their specific timer!
            )

            // 2. Handle the "Reset Time" rule for survivors
            if (finalState.resetTimeOnExplosion) {
                for (i in updatedPlayers.indices) {
                    if (i != doomedIndex && !updatedPlayers[i].isEliminated) {
                        updatedPlayers[i] = updatedPlayers[i].copy(timeLeft = finalState.duration.toFloat())
                    }
                }
            }

            // 3. --- SAVE THE ROUND RESULTS TO DISK INSTANTLY ---
            activePresetId?.let { presetId ->
                val allPresets = groupManager.loadPresets().toMutableList()
                val targetIndex = allPresets.indexOfFirst { it.id == presetId }

                if (targetIndex != -1) {
                    val targetPreset = allPresets[targetIndex]
                    var masterPlayers = targetPreset.players.toMutableList()

                    // Sync the active players' exact remaining times back to the master roster
                    for (i in masterPlayers.indices) {
                        val activeMatch = updatedPlayers.find { it.id == masterPlayers[i].id }
                        if (activeMatch != null) {
                            masterPlayers[i] = masterPlayers[i].copy(
                                timeLeft = activeMatch.timeLeft,
                                isAbsent = activeMatch.isAbsent
                            )
                        }
                    }

                    // --- FIX 2: THE AUTO-RESET (Match Over!) ---
                    // Count how many people in the whole class are still checked-in
                    val survivors = masterPlayers.count { !it.isAbsent }

                    // If only 1 (or 0) players are left, the match is over! Reset everyone.
                    if (survivors <= 1) {
                        masterPlayers = masterPlayers.map {
                            it.copy(
                                isAbsent = false,
                                isEliminated = false,
                                timeLeft = targetPreset.defaultTime
                            )
                        }.toMutableList()
                    }

                    // --- NEW CLASSROOM QOL: ROTATE THE CIRCLE! ---
                    // We find the doomed player in the MASTER list (because indices might differ)
                    val doomedId = updatedPlayers[doomedIndex].id
                    val masterDoomedIndex = masterPlayers.indexOfFirst { it.id == doomedId }

                    if (masterDoomedIndex != -1) {
                        // The next player in the circle should be the start of the list.
                        // So we split the list *after* the doomed player.
                        val splitIndex = (masterDoomedIndex + 1) % masterPlayers.size
                        masterPlayers = (masterPlayers.drop(splitIndex) + masterPlayers.take(splitIndex)).toMutableList()
                    }

                    allPresets[targetIndex] = targetPreset.copy(players = masterPlayers)
                    groupManager.savePresets(allPresets)
                }
            }

            finalPlayers = updatedPlayers
        }

        _state.update {
            it.copy(
                appState = AppState.EXPLODED,
                activePlayers = finalPlayers,
                particles = newParticles,
                smoke = newSmoke
            )
        }

        if (_state.value.bombStyle == "HEN") startHenPostGameLoop()
    }

    private fun startHenPostGameLoop() {
        postGameJob?.cancel()
        postGameJob = viewModelScope.launch(Dispatchers.Default) {
            var lastTimeNanos = System.nanoTime()
            var lastVolumeUpdateTime = 0L
            var painedCluckTimer = 0f
            var nextPainedCluckTarget = Random.nextFloat() * 2f + 1f

            logicalHenAnimTime = 2.5f
            _state.update { it.copy(isHenPaused = false, isPainedBeakClosed = false) }

            while (_state.value.appState == AppState.EXPLODED) {
                delay(10)
                val currentNanos = System.nanoTime()
                val dt = ((currentNanos - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastTimeNanos = currentNanos

                val currentState = _state.value

                if (currentState.isHenPaused) {
                    if (isAppInForeground) {
                        painedCluckTimer += dt
                        if (painedCluckTimer >= nextPainedCluckTarget) {
                            _state.update { it.copy(isPainedBeakClosed = true) }
                            delay(150)
                            _state.update { it.copy(isPainedBeakClosed = false) }

                            if (_state.value.isHenPaused && isAppInForeground) {
                                audio.playPainedCluck()
                            }
                            lastTimeNanos = System.nanoTime()
                            painedCluckTimer = 0f
                            nextPainedCluckTarget = Random.nextFloat() * 2f + 1f
                        }
                    } else {
                        lastTimeNanos = System.nanoTime()
                    }
                } else {
                    logicalHenAnimTime += dt

                    if (logicalHenAnimTime > 2.5f && !hasPlayedWhistle) { audio.playWhistle(); hasPlayedWhistle = true }
                    if (logicalHenAnimTime > 4.5f && !hasPlayedThud) {
                        audio.stopWhistle()
                        audio.playGlassTap()
                        hasPlayedThud = true
                    }
                    if (logicalHenAnimTime > 6.0f && !hasPlayedSlide) { audio.playHenSlide(); hasPlayedSlide = true }

                    val currentMs = System.currentTimeMillis()
                    if (logicalHenAnimTime > 6.0f && (currentMs - lastVolumeUpdateTime > 50)) {
                        val slideProgress = (logicalHenAnimTime - 6.0f) / 2.5f
                        audio.updateSlideVolume((1f - slideProgress).coerceIn(0f, 1f))
                        lastVolumeUpdateTime = currentMs
                    }

                    if (logicalHenAnimTime > 9.0f) break
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

        if (currentState.appState == AppState.RUNNING && !currentState.isPaused) {
            handleTogglePause()
        }
        else if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && !currentState.isHenPaused) {
            if (logicalHenAnimTime <= 9.0f) {
                handleToggleHenPause()
            }
        }
    }

    private fun handleAppForegrounded() {
        isAppInForeground = true
        val currentState = _state.value

        if (currentState.appState == AppState.EXPLODED && currentState.bombStyle == "HEN" && currentState.isHenPaused) {
            if (logicalHenAnimTime < 4.5f || (logicalHenAnimTime > 7.0f && logicalHenAnimTime <= 9.0f)) {
                handleToggleHenPause()
            }
        }

        if (currentState.appState == AppState.RUNNING && currentState.bombStyle == "FROG" && currentState.isPaused && currentState.timeLeft <= 1.0f) {
            audio.playFlail()
        }
    }
}