package com.flamingo.ticktickboom

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

// NOTE: Uses VisualParticle from AppModels.kt
// NOTE: Uses drawReflection and StrokeGlowText from Components.kt
// NOTE: Uses HenVisual and FrogVisual from AnimalVisuals.kt

@Composable
fun BombScreen(
    state: GameState,
    colors: AppColors,
    isDarkMode: Boolean,
    audio: AudioController, // <-- ADDED
    onIntent: (GameIntent) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    val visualTimeLeftState = remember { mutableFloatStateOf(state.duration.toFloat()) }
    val timeProvider = remember { { visualTimeLeftState.floatValue } }

    // We only keep purely VISUAL state in the Composable now
    var hasPlayedCrack1 by remember { mutableStateOf(false) }
    var hasPlayedCrack2 by remember { mutableStateOf(false) }
    var hasPlayedCrack3 by remember { mutableStateOf(false) }

    var hasPlayedFly by remember { mutableStateOf(false) }
    var henSequenceElapsed by remember { mutableFloatStateOf(0f) }

    val flashAnim = remember { Animatable(0f) }
    val eggWobbleAnim = remember { Animatable(0f) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

    val slideInAnim = remember { Animatable(
        when (state.bombStyle) {
            "FROG" -> -screenHeightPx - 100f
            "HEN" -> screenHeightPx + 100f
            else -> 0f
        }
    ) }

    // --- THE GRAVITY RESTORED! ---
    LaunchedEffect(state.bombStyle) {
        if (state.bombStyle == "FROG" || state.bombStyle == "HEN") {
            slideInAnim.animateTo(0f, tween(500, easing = LinearOutSlowInEasing))
        }
    }

    // Snap animations instantly on Player Change to prevent lag!
    LaunchedEffect(state.currentPlayerIndex, state.duration) {
        visualTimeLeftState.floatValue = state.timeLeft

        // Reset Hen Animation States
        hasPlayedFly = false
        hasPlayedCrack1 = false
        hasPlayedCrack2 = false
        hasPlayedCrack3 = false
        eggWobbleAnim.snapTo(0f)

        audio.stopWingFlap()
        audio.stopFlail()

        // --- THE FIX: Use timeLeft, not duration! ---
        if (state.bombStyle == "FUSE" && !state.isPaused) {
            audio.startFuse(startMuffled = state.timeLeft <= 5f)
        }
    }

    // THE FIX: Track the crack stage as a state so we don't read the timer in the UI body!
    var crackStage by remember { mutableIntStateOf(0) }

    LaunchedEffect(crackStage) {
        if (state.bombStyle == "HEN" && !state.isPaused && crackStage > 0) {
            val shouldPlay = (crackStage == 1 && !hasPlayedCrack1) ||
                    (crackStage == 2 && !hasPlayedCrack2) ||
                    (crackStage == 3 && !hasPlayedCrack3)

            if (shouldPlay) {
                // Set the booleans FIRST to prevent Compose from double-firing!
                if (crackStage == 1) hasPlayedCrack1 = true
                if (crackStage == 2) hasPlayedCrack2 = true
                if (crackStage == 3) hasPlayedCrack3 = true

                launch {
                    eggWobbleAnim.snapTo(0f)
                    eggWobbleAnim.animateTo(15f, tween(50))
                    eggWobbleAnim.animateTo(-15f, tween(50))
                    eggWobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
    }

    // --- RESTORED: Resume Wing Flap Audio when Unpausing ---
    LaunchedEffect(state.isPaused) {
        if (!state.isPaused && state.bombStyle == "HEN") {
            if (henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                var resumeVol = 1.0f
                if (henSequenceElapsed > 1.0f) {
                    resumeVol = (2.5f - henSequenceElapsed) / 1.5f
                }
                val finalVol = resumeVol.coerceIn(0f, 1f) * audio.timerVolume
                audio.playWingFlap(startVol = finalVol)
            }
        }
    }

    val currentIsPaused by rememberUpdatedState(state.isPaused)
    val masterTimeLeft by rememberUpdatedState(state.timeLeft)
    val currentBombStyle by rememberUpdatedState(state.bombStyle)

    // The Unified Master Loop! (Timer Math AND Hen Math together)
    LaunchedEffect(Unit) {
        var lastFrameNanos = System.nanoTime()
        while(true) {
            // THE FIX: Return 'it' to prevent the lambda from generating memory garbage!
            val nanos = withFrameNanos { it }
            val dt = ((nanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastFrameNanos = nanos

            // 1. Visual Timer Engine
            if (!currentIsPaused && visualTimeLeftState.floatValue > 0f) {
                visualTimeLeftState.floatValue -= dt

                if (kotlin.math.abs(visualTimeLeftState.floatValue - masterTimeLeft) > 0.2f) {
                    visualTimeLeftState.floatValue = masterTimeLeft
                }
            } else if (currentIsPaused) {
                visualTimeLeftState.floatValue = masterTimeLeft
            }

            // --- THE FIX: Calculate Crack Stage in the background! ---
            if (currentBombStyle == "HEN") {
                val currentVisual = visualTimeLeftState.floatValue
                val newCrackStage = when {
                    currentVisual <= 1.5f -> 3
                    currentVisual <= 3.0f -> 2
                    currentVisual <= 4.5f -> 1
                    else -> 0
                }
                // It only forces a UI update 3 times total, instead of 600 times!
                if (newCrackStage != crackStage) {
                    crackStage = newCrackStage
                }
            }

            // 2. Hen Fly Engine
            if (currentBombStyle == "HEN") {
                // THE FIX 2: Mathematically bind the animation directly to the master timer!
                henSequenceElapsed = (6.0f - visualTimeLeftState.floatValue).coerceAtLeast(0f)

                if (!currentIsPaused) {
                    if (henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                        if (!hasPlayedFly) {
                            audio.playLoudCluck()
                            audio.playWingFlap(audio.timerVolume)
                            hasPlayedFly = true
                        }
                        val currentVol = (2.5f - henSequenceElapsed) / 1.5f
                        audio.updateWingFlapVolume(currentVol.coerceIn(0f, 1f))
                    } else if (henSequenceElapsed >= 2.5f) {
                        audio.stopWingFlap()
                        hasPlayedFly = true // Prevents loud cluck if swapping to someone already post-flight!
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = slideInAnim.value }
    ) {
        // --- THE FIX: Draw Phase Deferral! 0 Recompositions! (Removed the 'if' wrapper) ---
        val flashColor = if (isDarkMode) Color.White else Slate950
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val currentFlash = flashAnim.value
                    if (currentFlash > 0f) {
                        drawRect(color = flashColor.copy(alpha = currentFlash))
                    }
                }
                .zIndex(-1f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .onGloballyPositioned {
                    onIntent(GameIntent.UpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)))
                },
            contentAlignment = Alignment.Center
        ) {
            // --- THE FIX 2: No more keys, no more delays! ---
            // The bomb persists, allowing internal clock loops to run flawlessly.
            BombVisualContent(
                style = state.bombStyle,
                duration = state.duration,
                timeLeftProvider = timeProvider,
                isCritical = state.isCritical,

                isLedOn = state.isLedOn, // Back to the pure, un-delayed state!

                isDarkMode = isDarkMode,
                colors = colors,
                isPaused = state.isPaused,
                onTogglePause = { onIntent(GameIntent.TogglePause) },
                onShock = {
                    audio.playZap()
                    coroutineScope.launch {
                        flashAnim.snapTo(1f)
                        flashAnim.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
                    }
                },
                eggWobbleRotation = eggWobbleAnim.value,
                henSequenceElapsed = henSequenceElapsed.coerceAtMost(2.5f),
                showEgg = true,
                crackStage = crackStage,
                isDarkModeShadows = isDarkMode
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // --- THE FIX: Protects the Bomb Screen UI! ---
                .padding(16.dp)
                .zIndex(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isLandscape) {
                val bombHalfWidth = when (state.bombStyle) {
                    "C4" -> 160.dp
                    "FUSE" -> 130.dp
                    "DYNAMITE" -> 110.dp
                    "FROG" -> 140.dp
                    "HEN" -> 130.dp
                    else -> 160.dp
                }

                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(start = bombHalfWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        // 1. THE LANDSCAPE COLUMN
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                        ) {
                            // --- NEW: Add the Player UI! ---
                            if (state.playMode == PlayMode.GROUP) {
                                PlayerTurnUI(state, colors, audio, onIntent)
                            }

                            BombTextContent(
                                style = state.bombStyle,
                                timeLeftProvider = timeProvider,
                                isCritical = state.isCritical,
                                isPaused = state.isPaused,
                                colors = colors,
                                henSequenceElapsed = henSequenceElapsed
                            )
                            AbortButtonContent(colors) {
                                audio.playClick()
                                onIntent(GameIntent.Abort)
                            }
                        }
                    }
                }
            } else {
                // --- PORTRAIT LAYOUT ---
                // Root Box allows the Player UI and the Bomb Column to float independently.
                Box(modifier = Modifier.fillMaxSize()) {

                    // 1. PLAYER TURN UI (Anchored to Top)
                    if (state.playMode == PlayMode.GROUP) {
                        val playerTopPadding = when (state.bombStyle) {
                            "FUSE" -> 40.dp
                            "C4", "DYNAMITE" -> 110.dp
                            "FROG", "HEN" -> 64.dp
                            else -> 48.dp
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(top = playerTopPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            PlayerTurnUI(state, colors, audio, onIntent)
                        }
                    }

                    // 2. RIGID BOMB COLUMN (Anchored to Center)
                    // Total height is mathematically locked to 608.dp so elements never shift.
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        // A. Text Content Area
                        Box(
                            modifier = Modifier
                                .height(140.dp)
                                .offset(y = (-52).dp), // Visually nudges text up without altering layout height
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            BombTextContent(
                                style = state.bombStyle,
                                timeLeftProvider = timeProvider,
                                isCritical = state.isCritical,
                                isPaused = state.isPaused,
                                colors = colors,
                                henSequenceElapsed = henSequenceElapsed
                            )
                        }

                        Spacer(modifier = Modifier.height(44.dp))

                        // B. Target Bomb Area
                        Box(modifier = Modifier.size(300.dp))

                        // C. Abort Button (With split-spacers to shift position while maintaining 64.dp total gap)
                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier.height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AbortButtonContent(colors) {
                                audio.playClick()
                                onIntent(GameIntent.Abort)
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerTurnUI(state: GameState, colors: AppColors, audio: AudioController, onIntent: (GameIntent) -> Unit) { // <-- Added colors parameter
    if (state.playMode != PlayMode.GROUP || state.activePlayers.isEmpty()) return

    val currentPlayer = state.activePlayers.getOrNull(state.currentPlayerIndex) ?: return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        // THE FIX: Stripped out the background and border modifiers!
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.desc_previous_player),
            tint = NeonCyan,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    audio.playClick()
                    onIntent(GameIntent.PreviousPlayer)
                }
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = currentPlayer.name.uppercase(),
            color = colors.text, // <-- THE FIX: Adapts dynamically to Light/Dark mode!
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CustomFont
        )

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.desc_next_player),
            tint = NeonCyan,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    audio.playClick()
                    onIntent(GameIntent.NextPlayer)
                }
                .padding(4.dp)
        )
    }
}