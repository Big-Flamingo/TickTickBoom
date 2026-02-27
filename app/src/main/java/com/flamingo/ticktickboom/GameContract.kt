package com.flamingo.ticktickboom

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset

/**
 * THE MODEL (State)
 * This is the single source of truth for the entire UI.
 * It is 100% immutable. The UI cannot change these values directly;
 * it can only observe them and draw what they say.
 */
@Immutable
data class GameState(
    val appState: AppState = AppState.SETUP,
    val bombStyle: String = "C4",
    val duration: Int = 10,
    val timeLeft: Float = 0f,
    val isPaused: Boolean = false,
    val isLedOn: Boolean = false,
    val explosionOrigin: Offset = Offset.Zero,
    // --- NEW GROUP MODE STATE ---
    val playMode: PlayMode = PlayMode.SOLO,
    val activePlayers: List<Player> = emptyList(), // The roster of students
    val currentPlayerIndex: Int = 0, // Who is holding the potato right now?
    val resetTimeOnExplosion: Boolean = false, // The teacher's rule choice
    // Pre-calculated lists for the explosion screen
    val particles: List<Particle> = emptyList(),
    val smoke: List<SmokeParticle> = emptyList(),
    val isHenPaused: Boolean = false,
    val isPainedBeakClosed: Boolean = false
) {
    // Derived state! The UI doesn't need to calculate this anymore,
    // it just asks the state if it is currently critical.
    val isCritical: Boolean
        get() = timeLeft > 0f && timeLeft <= 5f
}

/**
 * THE INTENT (Actions)
 * These represent every single action a user can take or event that can occur.
 * The UI will send these "Intents" to the ViewModel.
 */
sealed class GameIntent {
    data class StartTimer(val settings: TimerSettings) : GameIntent()
    object TogglePause : GameIntent()
    object ToggleHenPause : GameIntent()
    object Abort : GameIntent()
    object Reset : GameIntent()

    // Allows the UI to tell the logic layer exactly where the bomb is on screen
    data class UpdateExplosionOrigin(val offset: Offset) : GameIntent()

    // --- NEW LIFECYCLE INTENTS ---
    object AppEnteredBackground : GameIntent()
    object AppEnteredForeground : GameIntent()

    // --- NEW GROUP MODE INTENTS ---
    // We pass the preset into the start command instead of the basic TimerSettings
    data class StartGroupTimer(val preset: GroupPreset, val style: String) : GameIntent()
    object NextPlayer : GameIntent()
    object PreviousPlayer : GameIntent()
}