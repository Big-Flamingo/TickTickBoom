package com.flamingo.ticktickboom

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun ExplosionScreen(state: GameState, audio: AudioController, onIntent: (GameIntent) -> Unit) {
    var hasPlayedExplosion by remember { mutableStateOf(false) }
    val animationProgress = remember { Animatable(if (hasPlayedExplosion) 1f else 0f) }

    // --- AGSL SHADER STATE ---
    var shaderTime by remember { mutableFloatStateOf(0f) }
    val explosionShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(EXPLOSION_SHADER)
        } else null
    }

    val explosionBrush = remember(explosionShader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && explosionShader != null) {
            ShaderBrush(explosionShader as android.graphics.Shader)
        } else null
    }

    val smokeSprite = ImageBitmap.imageResource(id = R.drawable.smoke_wisp)

// --- THE FIX 1: Cache the filter so it doesn't create 900 objects a second! ---
    val smokeColorFilter = remember { ColorFilter.tint(Color.Gray) }

    LaunchedEffect(Unit) {
        if (!hasPlayedExplosion) {
            launch { animationProgress.animateTo(1f, tween(2000, easing = LinearOutSlowInEasing)) }
            hasPlayedExplosion = true
        }

        var lastTimeNanos = 0L
        while (animationProgress.value < 1f) {
            // THE FIX: Return 'it' to prevent the lambda from generating memory garbage!
            val nanos = withFrameNanos { it }
            val dt = if (lastTimeNanos == 0L) 0.016f else ((nanos - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            shaderTime += dt
            lastTimeNanos = nanos
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF431407)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99DC2626)))

        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = animationProgress.value
            var center = if (state.explosionOrigin != Offset.Zero) state.explosionOrigin else Offset(size.width / 2f, size.height / 2f)

            val yOffset = when (state.bombStyle) {
                "HEN" -> 35.dp.toPx()
                "FROG" -> 20.dp.toPx()
                "FUSE" -> 15.dp.toPx()
                "DYNAMITE" -> 0.dp.toPx()
                "C4" -> (-30).dp.toPx()
                else -> 0f
            }
            center = center.copy(y = center.y + yOffset)

            // 1. THE SHRAPNEL PARTICLES (Zero-Allocation Primitive Loop!)
            for (i in state.particles.indices) {
                val p = state.particles[i]
                val dist = p.velocity * progress * 2.5f
                val x = center.x + (p.dirX * dist)
                val y = center.y + (p.dirY * dist)
                val alpha = (1f - progress).coerceIn(0f, 1f)
                if (alpha > 0) drawCircle(color = p.color, alpha = alpha, radius = p.size * 1.5f, center = Offset(x, y))
            }

            // 2. THE WEBP SMOKE (Zero-Allocation Primitive Loop!)
            for (i in state.smoke.indices) {
                val s = state.smoke[i]
                val currentX = center.x + (s.vx * progress * 3f)
                val currentY = center.y + (s.vy * progress * 3f)
                val currentSize = s.size + (progress * 250f)

                val fadeProgress = (progress * 1.1f).coerceIn(0f, 1f)
                val currentAlpha = (s.alpha * (1f - fadeProgress)).coerceIn(0f, 1f)

                if (currentAlpha > 0f) {
                    val halfSize = currentSize / 2f
                    val rotationSpeed = if (i % 2 == 0) 50f else -50f
                    val currentRotation = progress * rotationSpeed

                    withTransform({
                        translate(currentX, currentY)
                        rotate(currentRotation, pivot = Offset.Zero)
                        translate(-halfSize, -halfSize)
                    }) {
                        drawImage(
                            image = smokeSprite,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(currentSize.toInt(), currentSize.toInt()),
                            alpha = currentAlpha,
                            colorFilter = smokeColorFilter
                        )
                    }
                }
            }

            // 3. GPU EXPLOSION
            if (progress < 1f) {
                if (explosionShader != null && explosionBrush != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    explosionShader.setFloatUniform("resolution", size.width, size.height)
                    explosionShader.setFloatUniform("center", center.x, center.y)
                    explosionShader.setFloatUniform("time", shaderTime)
                    explosionShader.setFloatUniform("progress", progress)

                    drawRect(brush = explosionBrush)
                } else {
                    val shockwaveRadius = progress * size.width * 0.8f
                    val shockwaveAlpha = (1f - progress).coerceIn(0f, 1f)
                    if (shockwaveAlpha > 0) drawCircle(color = Color.White, alpha = shockwaveAlpha * 0.5f, radius = shockwaveRadius, center = center, style = Stroke(width = 50f * (1f - progress)))

                    val flashAlpha = (1f - (progress * 2.0f)).coerceIn(0f, 1f)
                    if (flashAlpha > 0f) drawRect(Color.White.copy(alpha = flashAlpha))
                }
            }
        }

        val titleText = if (state.bombStyle == "FROG") stringResource(R.string.croaked) else stringResource(R.string.boom)
        val titleSize = if (state.bombStyle == "FROG") 72.sp else 96.sp

        // THE FIX 2: Cache the Brush so it doesn't allocate memory on recomposition!
        val boomBrush = remember { Brush.verticalGradient(listOf(Color.Yellow, NeonRed)) }

        val sharedRestartInteraction = remember { MutableInteractionSource() }

        // --- THE FIX 3: Cache the Winner Logic so .filter doesn't spawn new ArrayLists! ---
        val winnerName = remember(state.activePlayers, state.playMode) {
            val survivors = state.activePlayers.filter { !it.isEliminated }
            if (state.playMode == PlayMode.GROUP && survivors.size == 1) survivors.first().name else null
        }

        val maxConfetti = 150
        val cX = remember { FloatArray(maxConfetti) }
        val cY = remember { FloatArray(maxConfetti) }
        val cVx = remember { FloatArray(maxConfetti) }
        val cVy = remember { FloatArray(maxConfetti) }
        val cRot = remember { FloatArray(maxConfetti) }
        val cRotSpeed = remember { FloatArray(maxConfetti) }

        val cColorIndex = remember { IntArray(maxConfetti) }

        var confettiFrame by remember { mutableIntStateOf(0) }
        var hasSpawnedConfetti by remember { mutableStateOf(false) }

        val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
        val screenWidthPx = windowInfo.containerSize.width.toFloat()
        val screenHeightPx = windowInfo.containerSize.height.toFloat()

        // --- THE FIX: Create dynamically updating bounds for the physics engine! ---
        val currentScreenWidth by rememberUpdatedState(screenWidthPx)
        val currentScreenHeight by rememberUpdatedState(screenHeightPx)

        LaunchedEffect(winnerName) {
            if (winnerName != null && !hasSpawnedConfetti) {
                hasSpawnedConfetti = true
                audio.playVictory()
                for (i in 0 until maxConfetti) {
                    cX[i] = (Math.random() * currentScreenWidth).toFloat()
                    cY[i] = (-50f - Math.random() * 800f).toFloat()
                    cVx[i] = ((Math.random() - 0.5) * 300f).toFloat()
                    cVy[i] = (300f + Math.random() * 400f).toFloat()
                    cColorIndex[i] = (Math.random() * 6).toInt()
                    cRot[i] = (Math.random() * 360f).toFloat()
                    cRotSpeed[i] = ((Math.random() - 0.5) * 500f).toFloat()
                }

                var lastTime = 0L
                var isRunning = true // Track physics state without allocating!

                while (isRunning) {
                    // THE FIX: Returning 'it' prevents the lambda from capturing variables. ZERO heap allocations!
                    val nanos = withFrameNanos { it }

                    val dt = if (lastTime == 0L) 0.016f else ((nanos - lastTime) / 1_000_000_000f).coerceAtMost(0.1f)
                    lastTime = nanos
                    var activeParticles = 0

                    for (i in 0 until maxConfetti) {
                        if (cY[i] < currentScreenHeight + 100f) {
                            cX[i] += cVx[i] * dt + (kotlin.math.sin(nanos / 400_000_000.0 + i) * 150f * dt).toFloat()
                            cY[i] += cVy[i] * dt
                            cRot[i] += cRotSpeed[i] * dt
                            activeParticles++
                        } else {
                            // --- THE FIX: The Despawn Black Hole ---
                            // The moment it falls off the bottom edge, teleport it so far down
                            // that no portrait rotation can ever reveal it again!
                            cY[i] = 99999f
                        }
                    }

                    // THE FIX: Zero allocations! If activeParticles is 0, they all fell off the screen!
                    if (activeParticles > 0) {
                        confettiFrame++
                    } else {
                        isRunning = false
                    }
                }
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "winner_glint")
        val glintScale1 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glint_scale_1"
        )
        // Made this one slightly slower so they drift beautifully out of sync!
        val glintScale2 by infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(tween(2300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "glint_scale_2"
        )

        val winnerTextBrush = remember { Brush.verticalGradient(listOf(Color(0xFFFFFACD), Color(0xFFFFD700))) }
        val confettiOffset = remember { Offset(-8f, -12f) }
        val confettiSize = remember { Size(16f, 24f) }

        // --- Z-INDEX 90: CONFETTI CANVAS ---
        if (winnerName != null) {
            Canvas(modifier = Modifier.fillMaxSize().zIndex(90f)) {
                if (confettiFrame >= 0) Unit

                for (i in 0 until maxConfetti) {
                    if (cY[i] < size.height + 50f) {
                        withTransform({
                            translate(cX[i], cY[i])
                            rotate(cRot[i], pivot = Offset.Zero)
                        }) {
                            // THE FIX: Bypassing arrays entirely eliminates 9,000 unboxing operations per second!
                            val pColor = when (cColorIndex[i]) {
                                0 -> Color(0xFFEF4444)
                                1 -> Color(0xFF3B82F6)
                                2 -> Color(0xFF10B981)
                                3 -> Color(0xFFF59E0B)
                                4 -> Color(0xFF8B5CF6)
                                else -> Color(0xFFEC4899)
                            }
                            drawRect(
                                color = pColor,
                                topLeft = confettiOffset,
                                size = confettiSize
                            )
                        }
                    }
                }
            }
        }

        // --- Z-INDEX 100: VISUAL TEXT & GLINTS ---
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(100f)) {
            StrokeGlowText(
                text = titleText,
                color = NeonOrange,
                gradientBrush = boomBrush,
                fontSize = titleSize,
                fontWeight = FontWeight.Black,
                strokeWidth = 10f,
                blurRadius = 25f
            )

            if (winnerName != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(contentAlignment = Alignment.Center) {
                    StrokeGlowText(
                        text = "#1 ${winnerName.uppercase()}",
                        color = Color(0xFFFFD700),
                        gradientBrush = winnerTextBrush,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black,
                        strokeWidth = 8f,
                        blurRadius = 20f
                    )

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val gWidth = 40f
                        val gHeight = 8f

                        // --- TWEAK THESE TO MOVE GLINT 1 (Top Left) ---
                        // size.width * 0.0f is far left, size.width * 1.0f is far right
                        // size.height * 0.0f is top, size.height * 1.0f is bottom
                        val glint1X = size.width * 0.07f
                        val glint1Y = size.height * 0.20f

                        withTransform({
                            translate(glint1X, glint1Y)
                            scale(glintScale1, glintScale1, pivot = Offset.Zero)
                        }) {
                            drawOval(Color.White, topLeft = Offset(-gWidth/2, -gHeight/2), size = Size(gWidth, gHeight))
                            drawOval(Color.White, topLeft = Offset(-gHeight/2, -gWidth/2), size = Size(gHeight, gWidth))
                        }

                        // --- TWEAK THESE TO MOVE GLINT 2 (Bottom Right) ---
                        val glint2X = size.width * 0.93f
                        val glint2Y = size.height * 0.80f

                        withTransform({
                            translate(glint2X, glint2Y)
                            scale(glintScale2, glintScale2, pivot = Offset.Zero)
                        }) {
                            drawOval(Color.White, topLeft = Offset(-gWidth/2, -gHeight/2), size = Size(gWidth, gHeight))
                            drawOval(Color.White, topLeft = Offset(-gHeight/2, -gWidth/2), size = Size(gHeight, gWidth))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(80.dp))
            }

            ActionButton(
                text = stringResource(R.string.restart),
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                pressedBgColor = NeonOrange,
                pressedBorderColor = NeonOrange,
                pressedContentColor = Color(0xFF020617),
                interactionSource = sharedRestartInteraction,
                onClick = { /* Handled by top layer */ }
            )
        }

        // --- Z-INDEX 200: HEN OVERLAY ---
        if (state.bombStyle == "HEN") {
            AnimatedHenOverlay(state = state, onIntent = onIntent)
        }

        // --- Z-INDEX 300: GHOST CLICKS ---
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(300f)) {
            Text(titleText, fontSize = titleSize, fontWeight = FontWeight.Black, fontFamily = CustomFont, color = Color.Transparent)

            if (winnerName != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("#1 ${winnerName.uppercase()}", fontSize = 42.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont, color = Color.Transparent)
                Spacer(modifier = Modifier.height(32.dp))
            } else {
                Spacer(modifier = Modifier.height(80.dp))
            }

            Box(
                modifier = Modifier
                    .size(200.dp, 60.dp)
                    // --- THE FIX: Tell UI Automator exactly where this invisible box is! ---
                    .semantics {
                        contentDescription = "Restart Button"
                    }
                    .clickable(
                        interactionSource = sharedRestartInteraction,
                        indication = null
                    ) {
                        audio.playClick()
                        onIntent(GameIntent.Reset)
                    }
            )
        }
    }
}

@Composable
fun AnimatedHenOverlay(state: GameState, onIntent: (GameIntent) -> Unit) {
    val currentIsHenPaused by rememberUpdatedState(state.isHenPaused)
    var visualHenAnimTime by remember { mutableFloatStateOf(2.5f) }

    // THE FIX 1: Terminate the loop when she leaves the screen!
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while(visualHenAnimTime <= 9.0f) {
            // THE FIX: Return 'it' to prevent the lambda from generating memory garbage!
            val nanos = withFrameNanos { it }
            val dt = if (lastFrameNanos == 0L) 0.016f else ((nanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastFrameNanos = nanos
            if (!currentIsHenPaused) {
                visualHenAnimTime += dt
            }
        }
    }

    // THE FIX 2: Cull the entire Hen from the Compose UI Tree!
    if (visualHenAnimTime <= 9.0f) {
        Box(modifier = Modifier.fillMaxSize().zIndex(200f), contentAlignment = Alignment.Center) {
            HenVisual(
                timeLeft = 0f,
                isPaused = state.isHenPaused,
                onTogglePause = { onIntent(GameIntent.ToggleHenPause) },
                eggWobbleRotation = 0f,
                henSequenceElapsed = visualHenAnimTime,
                showEgg = false,
                isPainedBeakOpen = false,
                isPainedBeakClosed = state.isPainedBeakClosed,
                isDarkMode = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}