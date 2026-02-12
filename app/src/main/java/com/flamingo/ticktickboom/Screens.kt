package com.flamingo.ticktickboom

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.graphics.StrokeJoin

// --- SCREENS ---

@Composable
fun SetupScreen(colors: AppColors, isDarkMode: Boolean, onToggleTheme: () -> Unit, onStart: (TimerSettings) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }

    var minText by remember { mutableStateOf(prefs.getInt("min", 3).toString()) }
    var maxText by remember { mutableStateOf(prefs.getInt("max", 12).toString()) }
    var style by remember { mutableStateOf(prefs.getString("style", "C4") ?: "C4") }
    var timerVol by remember { mutableFloatStateOf(prefs.getFloat("vol_timer", 0.8f)) }
    var explodeVol by remember { mutableFloatStateOf(prefs.getFloat("vol_explode", 1.0f)) }
    var errorMsg by remember { mutableStateOf("") }

    var easterEggTaps by remember { mutableIntStateOf(0) }
    val wobbleAnim = remember { Animatable(0f) }
    val flyAwayAnim = remember { Animatable(0f) }

    var isHoldingBomb by remember { mutableStateOf(false) }
    val shakeAnim = rememberInfiniteTransition(label = "bomb_shake")
    val holdingShakeOffset by shakeAnim.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake"
    )

    val scope = rememberCoroutineScope()

    fun saveMin(text: String) { minText = text; text.toIntOrNull()?.let { prefs.edit { putInt("min", it) } } }
    fun saveMax(text: String) { maxText = text; text.toIntOrNull()?.let { prefs.edit { putInt("max", it) } } }
    fun saveStyle(newStyle: String) { style = newStyle; prefs.edit { putString("style", newStyle) }; AudioService.playClick() }
    fun saveTimerVol(vol: Float) { timerVol = vol; AudioService.timerVolume = vol; prefs.edit { putFloat("vol_timer", vol) } }
    fun saveExplodeVol(vol: Float) { explodeVol = vol; AudioService.explosionVolume = vol; prefs.edit { putFloat("vol_explode", vol) } }

    fun validateMin() {
        var min = minText.toIntOrNull() ?: 1
        if (min <= 0) min = 1
        minText = min.toString()
        prefs.edit { putInt("min", min) }

        val max = maxText.toIntOrNull() ?: min
        // Rule 1: If Min is higher than Max, push Max UP to match Min
        if (min > max) {
            maxText = min.toString()
            prefs.edit { putInt("max", min) }
        }
        focusManager.clearFocus()
    }

    fun validateMax() {
        var max = maxText.toIntOrNull() ?: 1
        if (max <= 0) max = 1
        maxText = max.toString()
        prefs.edit { putInt("max", max) }

        val min = minText.toIntOrNull() ?: max
        // Rule 2: If Max is lower than Min, pull Min DOWN to match Max
        if (max < min) {
            minText = max.toString()
            prefs.edit { putInt("min", max) }
        }
        focusManager.clearFocus()
    }

    fun tryStart() {
        AudioService.playClick()

        // Final Safety Check before starting
        // We re-run the logic just to ensure no weird states if the user tapped 'Start' while typing
        var min = minText.toIntOrNull() ?: 1
        if (min <= 0) min = 1
        var max = maxText.toIntOrNull() ?: 1
        if (max < min) max = min // Default safety: ensure range is valid

        minText = min.toString()
        maxText = max.toString()
        prefs.edit { putInt("min", min); putInt("max", max) }

        onStart(TimerSettings(min, max, style))
    }

    fun handleBombTap() {
        if (easterEggTaps >= 3) return
        scope.launch {
            easterEggTaps++
            AudioService.playBombCroak()
            wobbleAnim.snapTo(0f); wobbleAnim.animateTo(-15f, tween(50)); wobbleAnim.animateTo(15f, tween(50)); wobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (easterEggTaps >= 3) {
                val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                flyAwayAnim.animateTo(screenHeight + 500f, tween(800, easing = FastOutSlowInEasing))

                val min = minText.toIntOrNull() ?: 5
                var max = maxText.toIntOrNull() ?: 10
                if (max < min) max = min

                prefs.edit { putInt("min", min); putInt("max", max); putFloat("vol_timer", timerVol); putFloat("vol_explode", explodeVol) }
                AudioService.timerVolume = timerVol; AudioService.explosionVolume = explodeVol
                onStart(TimerSettings(min, max, "FROG"))
            }
        }
    }

    fun launchHen() {
        scope.launch {
            isHoldingBomb = false
            AudioService.stopHoldingCluck()
            AudioService.playLoudCluck()

            val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
            flyAwayAnim.animateTo(-screenHeight - 500f, tween(800, easing = FastOutSlowInEasing))

            val min = minText.toIntOrNull() ?: 5
            var max = maxText.toIntOrNull() ?: 10
            if (max < min) max = min

            onStart(TimerSettings(min, max, "HEN"))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().graphicsLayer { translationY = flyAwayAnim.value; clip = false }
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // In Screens.kt -> SetupScreen

            // In Screens.kt -> SetupScreen

            // Capture the rotation value outside the Canvas
            val currentRotation = if (isHoldingBomb) holdingShakeOffset else wobbleAnim.value

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val job = scope.launch {
                                    delay(200)
                                    isHoldingBomb = true
                                    AudioService.startHoldingCluck()
                                }
                                tryAwaitRelease()
                                job.cancel()
                                if (isHoldingBomb) {
                                    isHoldingBomb = false
                                    AudioService.stopHoldingCluck()
                                }
                            },
                            onTap = { handleBombTap() },
                            onLongPress = { launchHen() }
                        )
                    }
                    .graphicsLayer {
                        // FIX: Only handle SCALE here. Rotation is now handled inside the Canvas.
                        scaleX = if (isHoldingBomb) 1.1f else 1f
                        scaleY = if (isHoldingBomb) 1.1f else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(40.dp).offset(y = 10.dp)) {
                    val bodyRadius = 12.dp.toPx()
                    val floorY = center.y + bodyRadius

                    val neckOffsetX = 4.dp.toPx(); val neckOffsetY = 14.dp.toPx()
                    val neckW = 8.dp.toPx(); val neckH = 4.dp.toPx()
                    val fuseStroke = 2.dp.toPx()

                    drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->

                        // 1. SHADOW (Stays flat! Outside the rotation block)
                        if (!isReflection) {
                            // --- MODIFIED: CONSISTENT 5:1 RATIO ---
                            // Width = Body Diameter (Radius * 2)
                            val shadowW = bodyRadius * 2f
                            // Height = 20% of Width
                            val shadowH = shadowW * 0.2f

                            drawOval(
                                color = Color.Black.copy(alpha = 0.2f),
                                topLeft = Offset(center.x - shadowW / 2, floorY - shadowH / 2),
                                size = Size(shadowW, shadowH)
                            )
                        }

                        // 2. ROTATING BOMB PARTS
                        // We apply rotation around the bomb's center.
                        // Since drawReflection flips the context, this automatically creates the correct "mirrored pivot" effect.
                        withTransform({ rotate(currentRotation, pivot = center) }) {

                            // A. FUSE
                            val fuseStart = Offset(center.x, center.y - 14.dp.toPx())
                            val path = Path() // Create local path
                            path.moveTo(fuseStart.x, fuseStart.y)
                            path.lineTo(fuseStart.x, fuseStart.y - 2.dp.toPx())
                            path.cubicTo(
                                fuseStart.x, fuseStart.y - 7.dp.toPx(),
                                fuseStart.x + 7.dp.toPx(), fuseStart.y - 7.dp.toPx(),
                                fuseStart.x + 8.dp.toPx(), fuseStart.y + 1.dp.toPx()
                            )
                            drawPath(path = path, brush = Brush.linearGradient(colors = listOf(Color(0xFFB91C1C), NeonRed, Color(0xFFB91C1C))), style = Stroke(width = fuseStroke, cap = StrokeCap.Round))

                            // B. NECK
                            drawRoundRect(brush = Brush.linearGradient(colors = listOf(NeonRed, Color(0xFF7F1D1D))), topLeft = Offset(center.x - neckOffsetX, center.y - neckOffsetY), size = Size(neckW, neckH), cornerRadius = CornerRadius(2f, 2f))

                            // C. BODY
                            val bodyOffsetX = 4.dp.toPx(); val bodyOffsetY = 4.dp.toPx(); val gradRadius = 16.dp.toPx()
                            drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFFF6B6B), NeonRed, Color(0xFF991B1B)), center = Offset(center.x - bodyOffsetX, center.y - bodyOffsetY), radius = gradRadius), radius = bodyRadius, center = center)

                            // D. GLINT
                            val glintPivot = 3.dp.toPx(); val glintOffsetX = 8.dp.toPx(); val glintOffsetY = 8.dp.toPx(); val glintW = 8.dp.toPx(); val glintH = 5.dp.toPx()
                            withTransform({ rotate(-20f, pivot = Offset(center.x - glintPivot, center.y - glintPivot)) }) {
                                drawOval(brush = Brush.linearGradient(colors = listOf(Color.White.copy(0.4f), Color.White.copy(0.05f))), topLeft = Offset(center.x - glintOffsetX, center.y - glintOffsetY), size = Size(glintW, glintH))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            @Suppress("SpellCheckingInspection")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TICKTICK", color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 1.sp)
                Text("BOOM", color = NeonRed, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 1.sp)
            }
            Text("RANDOMIZED DETONATION SEQUENCE", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top=4.dp), fontFamily = CustomFont)
            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StyleButton("DIGITAL", Icons.Rounded.DeveloperBoard, style == "C4", NeonCyan, colors) { saveStyle("C4") }
                StyleButton("FUSE", Icons.Rounded.LocalFireDepartment, style == "FUSE", NeonOrange, colors) { saveStyle("FUSE") }
                StyleButton("TIMER", Icons.Rounded.AccessTime, style == "DYNAMITE", NeonRed, colors) { saveStyle("DYNAMITE") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // UPDATE: Use validateMin() for the first input
                TimeInput("MIN SECS", minText, { saveMin(it) }, NeonCyan, colors, Modifier.weight(1f), { validateMin() })

                // UPDATE: Use validateMax() for the second input
                TimeInput("MAX SECS", maxText, { saveMax(it) }, NeonRed, colors, Modifier.weight(1f), { validateMax() })
            }
            Spacer(modifier = Modifier.height(32.dp))

            VolumeSlider("TIMER VOLUME", timerVol, NeonCyan, colors) { saveTimerVol(it) }
            Spacer(modifier = Modifier.height(16.dp))
            VolumeSlider("EXPLOSION VOLUME", explodeVol, NeonRed, colors) { saveExplodeVol(it) }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.LightMode, null, tint = if(!isDarkMode) NeonOrange else colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("LIGHT MODE", color = if(!isDarkMode) NeonOrange else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color.Transparent, checkedBorderColor = NeonCyan, uncheckedThumbColor = NeonOrange, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = NeonOrange)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("DARK MODE", color = if(isDarkMode) NeonCyan else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.DarkMode, null, tint = if(isDarkMode) NeonCyan else colors.textSecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
            if (errorMsg.isNotEmpty()) { Text(errorMsg, color = NeonRed, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp), fontFamily = CustomFont) }

            Button(onClick = { tryStart() }, colors = ButtonDefaults.buttonColors(containerColor = NeonRed), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(60.dp)) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ARM SYSTEM", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
fun BombScreen(
    duration: Int,
    startTime: Long,
    style: String,
    colors: AppColors,
    isDarkMode: Boolean,
    isPaused: Boolean,
    totalPausedTime: Long,
    currentPauseStart: Long,
    onExplode: () -> Unit,
    onAbort: () -> Unit,
    onTogglePause: () -> Unit,
    onUpdateExplosionOrigin: (Offset) -> Unit
) {
    val initialElapsed = if (isPaused) {
        (currentPauseStart - startTime - totalPausedTime) / 1000f
    } else {
        (System.currentTimeMillis() - startTime - totalPausedTime) / 1000f
    }

    var timeLeft by remember { mutableFloatStateOf((duration - initialElapsed).coerceAtLeast(0f)) }
    var isLedOn by remember { mutableStateOf(false) }

    val isCriticalStart = duration <= 5
    val isCritical = timeLeft <= 5f
    var isFuseFinished by remember { mutableStateOf(isCriticalStart) }

    var hasPlayedDing by rememberSaveable { mutableStateOf(false) }
    var hasPlayedFlail by rememberSaveable { mutableStateOf(false) }
    var hasPlayedAlert by rememberSaveable { mutableStateOf(false) }
    var hasPlayedFly by rememberSaveable { mutableStateOf(false) }

    var lastTickRunTime by rememberSaveable { mutableLongStateOf((initialElapsed * 1000).toLong() - 1000) }

    // HEN ANIMATION STATE (Linear, always starts at 0)
    var henSequenceElapsed by rememberSaveable { mutableFloatStateOf(0f) }
    var isHenSequenceActive by rememberSaveable { mutableStateOf(false) }

    val flashAnim = remember { Animatable(0f) }
    val eggWobbleAnim = remember { Animatable(0f) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Calculate Crack Stage purely based on Time Left
    val crackStage = if (style != "HEN") 0 else when {
        timeLeft <= 1.5f -> 3
        timeLeft <= 3.0f -> 2
        timeLeft <= 4.5f -> 1
        else -> 0
    }

    // NEW: Save the last played stage so it survives rotation
    var lastPlayedCrackStage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(crackStage) {
        // Check if we are in a NEW, higher stage than before
        if (style == "HEN" && crackStage > lastPlayedCrackStage && !isPaused) {
            lastPlayedCrackStage = crackStage // Mark this stage as "played"
            AudioService.playCrack()
            launch {
                eggWobbleAnim.snapTo(0f)
                eggWobbleAnim.animateTo(15f, tween(50))
                eggWobbleAnim.animateTo(-15f, tween(50))
                eggWobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) {
            AudioService.stopSlide()
            AudioService.stopHoldingCluck()
            AudioService.stopWingFlap()
            AudioService.stopLoudCluck()
        } else {
            if (style == "FUSE") AudioService.startFuse(isCritical)

            // FIX: Resume flapping with correct volume
            if (style == "HEN" && henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                // Calculate volume exactly as we do in the loop
                var resumeVol = 1.0f
                if (henSequenceElapsed > 2.0f) {
                    resumeVol = (2.5f - henSequenceElapsed) / 0.5f
                }
                // Pass the volume so it doesn't "pop" to 100%
                AudioService.playWingFlap(startVol = resumeVol.coerceIn(0f, 1f) * AudioService.timerVolume)
            }
        }
    }

    LaunchedEffect(key1 = startTime, key2 = isPaused, key3 = totalPausedTime) {
        var lastFrameTime = 0L

        while (timeLeft > 0.01) {
            withFrameNanos { frameTimeNanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                if (!isPaused) {
                    val now = System.currentTimeMillis()
                    val currentRunTimeMs = now - startTime - totalPausedTime
                    val elapsed = currentRunTimeMs / 1000f
                    timeLeft = (duration - elapsed)

                    // --- HEN LOGIC ---
                    // 1. Activate Sequence: Whenever time is <= 6.0 (or immediately if start < 6)
                    if (style == "HEN" && timeLeft <= 6.0f && !isHenSequenceActive) {
                        isHenSequenceActive = true
                    }

                    if (isHenSequenceActive) {
                        henSequenceElapsed += dt

                        // 3. Trigger Fly Sound/Anim at 0.5s mark
                        if (henSequenceElapsed > 0.5f && !hasPlayedFly) {
                            AudioService.playLoudCluck()
                            // Start at full volume
                            AudioService.playWingFlap(AudioService.timerVolume)
                            hasPlayedFly = true
                        }

                        // FIX: Fade out for the last 0.5 seconds (2.0s to 2.5s)
                        if (henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                            var currentVol = 1.0f

                            // If we are in the last 0.5s of the window
                            if (henSequenceElapsed > 2.0f) {
                                currentVol = (2.5f - henSequenceElapsed) / 0.5f
                            }

                            // Apply the volume frame-by-frame
                            AudioService.updateWingFlapVolume(currentVol.coerceIn(0f, 1f))
                        }

                        // Stop the flapping when she leaves the screen (2.5s)
                        if (henSequenceElapsed > 2.5f) {
                            AudioService.stopWingFlap()
                        }
                    }

                    // ... (Standard Sounds logic) ...
                    if (timeLeft <= 5f && !isFuseFinished) {
                        isFuseFinished = true
                        if (style == "FUSE") AudioService.dimFuse()
                    }
                    if (style == "DYNAMITE" && timeLeft <= 1.0f && !hasPlayedDing && timeLeft > 0) {
                        AudioService.playDing(); hasPlayedDing = true
                    }
                    if (style == "FROG" && timeLeft <= 1.05f && !hasPlayedAlert && timeLeft > 0) {
                        AudioService.playAlert(); hasPlayedAlert = true
                    }
                    if (style == "FROG" && timeLeft <= 1.0f && !hasPlayedFlail && timeLeft > 0) {
                        AudioService.playFlail(); hasPlayedFlail = true
                    }

                    val tickInterval = if (style == "HEN") 1000L else if (timeLeft < 5) 500L else 1000L

                    if (currentRunTimeMs - lastTickRunTime >= tickInterval && timeLeft > 0) {
                        if (style == "C4") { AudioService.playTick(); isLedOn = true }
                        if (style == "DYNAMITE" && timeLeft > 1.0) AudioService.playClockTick()
                        if (style == "FROG") { AudioService.playCroak(timeLeft < 5) }
                        if (style == "HEN") {
                            // FIX: Disable ticks completely once sequence starts (prevents phantom beakless cluck)
                            if (!isHenSequenceActive) {
                                AudioService.playHenCluck()
                            }
                        }
                        // FIX: Add the interval to the last time instead of resetting to "now".
                        // This ensures that if we are 10ms late this frame, we are 10ms early next frame to catch up.
                        if (currentRunTimeMs - lastTickRunTime > tickInterval * 1.5f) {
                            // Safety: If we are WAY behind (like after a pause or phase change), just reset.
                            lastTickRunTime = currentRunTimeMs
                        } else {
                            // Normal operation: Keep the metronome perfect
                            lastTickRunTime += tickInterval
                        }
                    }
                    if (isLedOn && (currentRunTimeMs - lastTickRunTime > 50)) isLedOn = false
                } else {
                    lastFrameTime = frameTimeNanos
                }
            }
        }

        // ... (inside LaunchedEffect)

        if (!isPaused) {
            timeLeft = 0f
            // FIX: Removed the local flash, audio, and delay.
            // We transfer control immediately to ExplosionScreen, which handles the
            // flash and sound instantly for a zero-latency feel.
            onExplode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .onGloballyPositioned { onUpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)) },
            contentAlignment = Alignment.Center
        ) {
            // FIX: Clamp time to 2.5s to prevent "Phantom Hen" falling back down
            BombVisualContent(
                style = style,
                duration = duration,
                timeLeft = timeLeft,
                isCritical = isCritical,
                isLedOn = isLedOn,
                isDarkMode = isDarkMode, // This is for C4/Fuse
                colors = colors,
                isPaused = isPaused,
                onTogglePause = onTogglePause,
                eggWobbleRotation = eggWobbleAnim.value,
                henSequenceElapsed = henSequenceElapsed.coerceAtMost(2.5f),
                showEgg = true,
                crackStage = crackStage,
                isPainedBeakOpen = false,   // Default
                isPainedBeakClosed = false, // Default
                isDarkModeShadows = isDarkMode // <--- THIS IS THE UPDATE!
            )
        }

        // LAYER 2: UI (Text & Buttons)
        Box(modifier = Modifier.fillMaxSize().padding(16.dp).zIndex(1f), contentAlignment = Alignment.Center) {
            if (isLandscape) {
                // --- UNIFIED LANDSCAPE LAYOUT ---

                // 1. Determine Ghost Text & Size based on what is widest for THAT bomb
                val (ghostText, ghostSize) = when (style) {
                    "FUSE" -> "CRITICAL" to 48.sp       // Title is widest
                    "FROG" -> "RIBBIT!!" to 48.sp       // Title is widest
                    "HEN" -> "CRACKING" to 48.sp        // Title is widest
                    else -> "DETONATION SEQUENCE" to 12.sp // Pill text is widest (C4/Dynamite)
                }

                // 2. Adjust Position: Move Hen slightly left (160dp) to prevent "CRACKING" line-break
                val leftPadding = if (style == "HEN") 160.dp else 192.dp

                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))

                    // RIGHT HALF: Text & Buttons
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Column(
                            modifier = Modifier.padding(start = leftPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // STABILIZER: Dynamic Ghost Text
                            Text(
                                text = ghostText,
                                color = Color.Transparent,
                                fontSize = ghostSize,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontFamily = CustomFont,
                                modifier = Modifier.height(0.dp).padding(horizontal = 16.dp)
                            )

                            // Actual Text Elements
                            BombTextContent(
                                style = style,
                                timeLeft = timeLeft,
                                isCritical = isFuseFinished,
                                isPaused = isPaused,
                                colors = colors,
                                modifier = Modifier.padding(bottom = 48.dp), // Passed explicitly
                                henSequenceElapsed = henSequenceElapsed // Named argument prevents mix-up
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Button
                            AbortButtonContent(colors, onAbort)
                        }
                    }
                }
            } else {
                // PORTRAIT LAYOUT (Unchanged)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BombTextContent(
                        style = style,
                        timeLeft = timeLeft,
                        isCritical = isFuseFinished,
                        isPaused = isPaused,
                        colors = colors,
                        henSequenceElapsed = henSequenceElapsed // Named argument skips over the 'modifier' slot safely
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.size(300.dp))
                    Spacer(modifier = Modifier.height(64.dp))
                    AbortButtonContent(colors, onAbort)
                }
            }
        }
        if (flashAnim.value > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAnim.value)).zIndex(1000f))
    }
}

@Composable
fun ExplosionScreen(colors: AppColors, style: String?, explosionOrigin: Offset? = null, onReset: () -> Unit) {
    val context = LocalContext.current
    val particles = remember {
        val colorsList = listOf(NeonRed, NeonOrange, Color.Yellow, Color.White)
        List(100) { i -> Particle(i, Math.random() * 360, (200 + Math.random() * 800).toFloat(), (3 + Math.random() * 5).toFloat(), colorsList.random(), (Math.random() * 20 - 10).toFloat()) }
    }
    val smoke = remember {
        List(30) { _ -> SmokeParticle(x = 0f, y = 0f, vx = (Math.random() * 100 - 50).toFloat(), vy = (Math.random() * 100 - 50).toFloat(), size = (20 + Math.random() * 40).toFloat(), alpha = 0.8f, life = 1f, maxLife = 1f) }
    }

    // Use rememberSaveable so these don't reset on rotation
    var hasPlayedExplosion by rememberSaveable { mutableStateOf(false) }
    val animationProgress = remember { Animatable(if (hasPlayedExplosion) 1f else 0f) }

    // FIX: All Hen animation states must be saved
    var henAnimTime by rememberSaveable { mutableFloatStateOf(2.5f) }
    var isHenPaused by rememberSaveable { mutableStateOf(false) }

    var hasPlayedWhistle by rememberSaveable { mutableStateOf(false) }
    var hasPlayedThud by rememberSaveable { mutableStateOf(false) }
    var hasPlayedSlide by rememberSaveable { mutableStateOf(false) }

    var isPainedBeakClosed by rememberSaveable { mutableStateOf(false) }
    var isPainedBeakOpen by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (!hasPlayedExplosion) {
            AudioService.playExplosion(context)
            launch { animationProgress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) }
            hasPlayedExplosion = true
        }
    }

    if (style == "HEN") {
        LaunchedEffect(isHenPaused) {
            if (isHenPaused) {
                AudioService.stopSlide()
                AudioService.stopWhistle()

                while (true) {
                    delay(Random.nextLong(1000, 3000))
                    isPainedBeakClosed = true
                    delay(150)
                    isPainedBeakClosed = false
                    AudioService.playPainedCluck()
                }
            } else {
                if (henAnimTime > 6.0f && henAnimTime < 8.5f) {
                    AudioService.playHenSlide()
                }
                if (henAnimTime > 2.5f && henAnimTime < 4.5f) {
                    AudioService.playWhistle()
                }
            }
        }

        LaunchedEffect(isHenPaused) {
            var lastFrameTime = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (lastFrameTime == 0L) 0.016f else (nanos - lastFrameTime) / 1_000_000_000f
                    lastFrameTime = nanos

                    if (!isHenPaused) {
                        henAnimTime += dt

                        if (henAnimTime > 2.5f && !hasPlayedWhistle) {
                            AudioService.playWhistle()
                            hasPlayedWhistle = true
                        }

                        if (henAnimTime > 4.5f && !hasPlayedThud) {
                            AudioService.playGlassTap()
                            hasPlayedThud = true
                        }

                        if (henAnimTime > 6.0f && !hasPlayedSlide) {
                            AudioService.playHenSlide()
                            hasPlayedSlide = true
                        }

                        if (henAnimTime > 6.0f) {
                            val slideProgress = (henAnimTime - 6.0f) / 2.5f
                            val fadeVol = (1f - slideProgress).coerceIn(0f, 1f)
                            AudioService.updateSlideVolume(fadeVol)
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF431407)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99DC2626)))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = animationProgress.value
            var center = if (explosionOrigin != null && explosionOrigin != Offset.Zero) explosionOrigin else Offset(size.width / 2, size.height / 2)

            if (style == "HEN") {
                val eggOffset = 35.dp.toPx()
                center = center.copy(y = center.y + eggOffset)
            }

            smoke.forEach { s ->
                val currentX = center.x + (s.vx * progress * 3f)
                val currentY = center.y + (s.vy * progress * 3f)
                val currentSize = s.size + (progress * 150f)
                val currentAlpha = (s.alpha * (1f - progress)).coerceIn(0f, 1f)
                drawCircle(color = colors.smokeColor.copy(alpha = currentAlpha), radius = currentSize, center = Offset(currentX, currentY))
            }
            particles.forEach { p ->
                val rad = p.angle * (PI / 180)
                val dist = p.velocity * progress * 2f
                val x = center.x + (cos(rad) * dist).toFloat()
                val y = center.y + (sin(rad) * dist).toFloat()
                val alpha = (1f - progress).coerceIn(0f, 1f)
                if (alpha > 0) drawCircle(color = p.color.copy(alpha = alpha), radius = p.size, center = Offset(x, y))
            }
            val shockwaveRadius = progress * size.width * 0.8f
            val shockwaveAlpha = (1f - progress).coerceIn(0f, 1f)
            if (shockwaveAlpha > 0) drawCircle(color = Color.White.copy(alpha = shockwaveAlpha * 0.5f), radius = shockwaveRadius, center = center, style = Stroke(width = 50f * (1f - progress)))
        }

        // CHANGED: Reduced multiplier from 5 to 2.0f to make the white flash last longer
        val flashAlpha = (1f - (animationProgress.value * 2.0f)).coerceIn(0f, 1f)
        if (flashAlpha > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        val titleText = if (style == "FROG") "CROAKED" else "BOOM"
        val titleSize = if (style == "FROG") 72.sp else 96.sp

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(100f)) {
            Box(contentAlignment = Alignment.Center) {
                // 1. Invisible Stroke with Glow (Background)
                Text(
                    titleText,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = CustomFont,
                    color = NeonOrange.copy(alpha = 0.01f), // Invisible
                    style = TextStyle(
                        drawStyle = Stroke(width = 10f, join = StrokeJoin.Round),
                        shadow = Shadow(color = NeonOrange, blurRadius = 25f)
                    )
                )
                // 2. Sharp Gradient Text (Foreground)
                Text(
                    titleText,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = CustomFont,
                    style = TextStyle(brush = Brush.verticalGradient(listOf(Color.Yellow, NeonRed)))
                )
            }
            Spacer(modifier = Modifier.height(80.dp))

            ActionButton(
                text = "RESTART",
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                onClick = { /* Click handled by Ghost Layer */ }
            )
        }

        if (style == "HEN") {
            Box(modifier = Modifier.fillMaxSize().zIndex(200f), contentAlignment = Alignment.Center) {
                HenVisual(
                    timeLeft = 0f,
                    isPaused = isHenPaused,
                    onTogglePause = {
                        isHenPaused = !isHenPaused
                        if (isHenPaused) {
                            // UPDATE: Play Boing sound on PAUSE as well
                            AudioService.playPauseInteraction("HEN", true)

                            scope.launch {
                                isPainedBeakClosed = true
                                delay(150)
                                isPainedBeakClosed = false
                                AudioService.playPainedCluck()
                            }
                        } else {
                            AudioService.playPauseInteraction("HEN", false)
                        }
                    },
                    eggWobbleRotation = 0f,
                    henSequenceElapsed = henAnimTime,
                    showEgg = false,
                    isPainedBeakOpen = isPainedBeakOpen,
                    isPainedBeakClosed = isPainedBeakClosed,
                    isDarkMode = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(300f)) {
            Text(titleText, fontSize = titleSize, fontWeight = FontWeight.Black, fontFamily = CustomFont, color = Color.Transparent)
            Spacer(modifier = Modifier.height(80.dp))
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        AudioService.playClick()
                        AudioService.stopSlide()
                        AudioService.stopWhistle()
                        onReset()
                    }
            )
        }
    }
}