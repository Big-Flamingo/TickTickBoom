package com.flamingo.ticktickboom

import android.content.Context
import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// NOTE: Uses VisualParticle from AppModels.kt
// NOTE: Uses drawReflection and StrokeGlowText from Components.kt
// NOTE: Uses HenVisual and FrogVisual from AnimalVisuals.kt

// --- SCREENS ---

@Composable
fun SetupScreen(colors: AppColors, isDarkMode: Boolean, onToggleTheme: () -> Unit, onStart: (TimerSettings) -> Unit, onToggleLanguage: () -> Unit) {
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
    val setupFusePath = remember { Path() }

    // --- UPDATED: Grab the density and use it to calculate pixels! ---
    val density = LocalDensity.current
    val bombFuseStroke = remember(density) {
        with(density) {
            Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }

    var isHoldingBomb by remember { mutableStateOf(false) }
    val shakeAnim = rememberInfiniteTransition(label = "bomb_shake")
    val holdingShakeOffset by shakeAnim.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(50, easing = LinearEasing), RepeatMode.Reverse),
        label = "shake"
    )

    val scope = rememberCoroutineScope()

    // --- ADD THIS BLOCK ---
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val screenHeightPx = windowInfo.containerSize.height.toFloat()

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
                flyAwayAnim.animateTo(screenHeightPx + 500f, tween(800, easing = FastOutSlowInEasing))

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

            flyAwayAnim.animateTo(-screenHeightPx - 500f, tween(800, easing = FastOutSlowInEasing))

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

            // Capture the rotation value outside the Canvas
            val currentRotation = if (isHoldingBomb) holdingShakeOffset else wobbleAnim.value

            // --- NEW: Wrapper Box to align Bomb and Language Switch ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // 1. THE BOMB (Centered)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
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

                        // Using the shared drawReflection from Components.kt
                        drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->

                            // 1. SHADOW (Stays flat! Outside the rotation block)
                            if (!isReflection) {
                                val shadowW = bodyRadius * 2f
                                val shadowH = shadowW * 0.2f

                                drawOval(
                                    color = Color.Black.copy(alpha = 0.2f),
                                    topLeft = Offset(center.x - shadowW / 2, floorY - shadowH / 2),
                                    size = Size(shadowW, shadowH)
                                )
                            }

                            // 2. ROTATING BOMB PARTS
                            withTransform({ rotate(currentRotation, pivot = center) }) {

                                // A. FUSE
                                val fuseStart = Offset(center.x, center.y - 14.dp.toPx())
                                setupFusePath.reset()
                                setupFusePath.moveTo(fuseStart.x, fuseStart.y)
                                setupFusePath.lineTo(fuseStart.x, fuseStart.y - 2.dp.toPx())
                                setupFusePath.cubicTo(
                                    fuseStart.x, fuseStart.y - 7.dp.toPx(),
                                    fuseStart.x + 7.dp.toPx(), fuseStart.y - 7.dp.toPx(),
                                    fuseStart.x + 8.dp.toPx(), fuseStart.y + 1.dp.toPx()
                                )
                                drawPath(path = setupFusePath, brush = Brush.linearGradient(colors = listOf(Color(0xFFB91C1C), NeonRed, Color(0xFFB91C1C))), style = bombFuseStroke)

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

                // 2. THE LANGUAGE SWITCH (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 24.dp, end = 0.dp) // <--- FIX 2: Nudges it down and left!
                ) {
                    LanguageSwitch(colors = colors, onClick = onToggleLanguage)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_title_tick), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 1.sp)
                Text(stringResource(R.string.app_title_boom), color = NeonRed, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 1.sp)
            }
            Text(stringResource(R.string.randomized_sequence), color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top=4.dp), fontFamily = CustomFont)
            Spacer(modifier = Modifier.height(32.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StyleButton(stringResource(R.string.style_digital), Icons.Rounded.DeveloperBoard, style == "C4", NeonCyan, colors) { saveStyle("C4") }
                StyleButton(stringResource(R.string.style_fuse), Icons.Rounded.LocalFireDepartment, style == "FUSE", NeonOrange, colors) { saveStyle("FUSE") }
                StyleButton(stringResource(R.string.style_timer), Icons.Rounded.AccessTime, style == "DYNAMITE", NeonRed, colors) { saveStyle("DYNAMITE") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TimeInput(stringResource(R.string.min_secs), minText, { saveMin(it) }, NeonCyan, colors, Modifier.weight(1f), { validateMin() })
                TimeInput(stringResource(R.string.max_secs), maxText, { saveMax(it) }, NeonRed, colors, Modifier.weight(1f), { validateMax() })
            }
            Spacer(modifier = Modifier.height(32.dp))

            VolumeSlider(stringResource(R.string.timer_volume), timerVol, NeonCyan, colors) { saveTimerVol(it) }
            Spacer(modifier = Modifier.height(16.dp))
            VolumeSlider(stringResource(R.string.explosion_volume), explodeVol, NeonRed, colors) { saveExplodeVol(it) }
            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.LightMode, null, tint = if(!isDarkMode) NeonOrange else colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.light_mode), color = if(!isDarkMode) NeonOrange else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color.Transparent, checkedBorderColor = NeonCyan, uncheckedThumbColor = NeonOrange, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = NeonOrange)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.dark_mode), color = if(isDarkMode) NeonCyan else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.DarkMode, null, tint = if(isDarkMode) NeonCyan else colors.textSecondary, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
            if (errorMsg.isNotEmpty()) { Text(errorMsg, color = NeonRed, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp), fontFamily = CustomFont) }

            val armInteractionSource = remember { MutableInteractionSource() }
            val isArmPressed by armInteractionSource.collectIsPressedAsState()

            // Blend the red towards gray for a tactile press feel
            val targetArmColor = if (isArmPressed) lerp(NeonRed, Color.Gray, 0.4f) else NeonRed
            val armAnimatedColor by animateColorAsState(
                targetValue = targetArmColor,
                animationSpec = tween(durationMillis = 150),
                label = "armButtonHighlight"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(armAnimatedColor)
                    .clickable(
                        interactionSource = armInteractionSource,
                        indication = null
                    ) { tryStart() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.arm_system), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
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
    val coroutineScope = rememberCoroutineScope()
    var timeLeft by remember { mutableFloatStateOf((duration - initialElapsed).coerceAtLeast(0f)) }
    var isLedOn by remember { mutableStateOf(false) }

    val isCriticalStart = duration <= 5
    val isCritical = timeLeft <= 5f
    var isFuseFinished by remember { mutableStateOf(isCriticalStart) }

    var hasPlayedDing by remember { mutableStateOf(false) }
    var hasPlayedFlail by remember { mutableStateOf(false) }
    var hasPlayedAlert by remember { mutableStateOf(false) }
    var hasPlayedFly by remember { mutableStateOf(false) }

    var lastTickRunTime by remember { mutableLongStateOf((initialElapsed * 1000).toLong() - 1000) }

    // HEN ANIMATION STATE (Linear, always starts at 0)
    var henSequenceElapsed by remember { mutableFloatStateOf(0f) }
    var isHenSequenceActive by remember { mutableStateOf(false) }

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

    // Save the last played stage so it survives rotation
    var lastPlayedCrackStage by remember { mutableIntStateOf(0) }

    LaunchedEffect(crackStage) {
        if (style == "HEN" && crackStage > lastPlayedCrackStage && !isPaused) {
            lastPlayedCrackStage = crackStage
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

            if (style == "HEN" && henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                var resumeVol = 1.0f
                if (henSequenceElapsed > 2.0f) {
                    resumeVol = (2.5f - henSequenceElapsed) / 0.5f
                }
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

                    if (style == "HEN" && timeLeft <= 6.0f && !isHenSequenceActive) {
                        isHenSequenceActive = true
                    }

                    if (isHenSequenceActive) {
                        henSequenceElapsed += dt

                        if (henSequenceElapsed > 0.5f && !hasPlayedFly) {
                            AudioService.playLoudCluck()
                            AudioService.playWingFlap(AudioService.timerVolume)
                            hasPlayedFly = true
                        }

                        if (henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                            var currentVol = 1.0f
                            if (henSequenceElapsed > 2.0f) {
                                currentVol = (2.5f - henSequenceElapsed) / 0.5f
                            }
                            AudioService.updateWingFlapVolume(currentVol.coerceIn(0f, 1f))
                        }

                        if (henSequenceElapsed > 2.5f) {
                            AudioService.stopWingFlap()
                        }
                    }

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
                            if (!isHenSequenceActive) {
                                AudioService.playHenCluck()
                            }
                        }
                        if (currentRunTimeMs - lastTickRunTime > tickInterval * 1.5f) {
                            lastTickRunTime = currentRunTimeMs
                        } else {
                            lastTickRunTime += tickInterval
                        }
                    }
                    if (isLedOn && (currentRunTimeMs - lastTickRunTime > 50)) isLedOn = false
                } else {
                    lastFrameTime = frameTimeNanos
                }
            }
        }

        if (!isPaused) {
            timeLeft = 0f
            onExplode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- NEW: INVERTED BACKGROUND FLASH ---
        if (flashAnim.value > 0f) {
            // Dark mode flashes White, Light mode flashes Dark
            val flashColor = if (isDarkMode) Color.White else Slate950
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = flashAnim.value))
                    .zIndex(-1f) // Render strictly behind the bomb!
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .onGloballyPositioned { onUpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)) },
            contentAlignment = Alignment.Center
        ) {
            BombVisualContent(
                style = style,
                duration = duration,
                timeLeft = timeLeft,
                isCritical = isCritical,
                isLedOn = isLedOn,
                isDarkMode = isDarkMode,
                colors = colors,
                isPaused = isPaused,
                onTogglePause = onTogglePause,
                onShock = {
                    // Trigger the screen flash animation!
                    coroutineScope.launch {
                        flashAnim.snapTo(1f)
                        flashAnim.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
                    }
                },
                eggWobbleRotation = eggWobbleAnim.value,
                henSequenceElapsed = henSequenceElapsed.coerceAtMost(2.5f),
                showEgg = true,
                crackStage = crackStage,
                isPainedBeakOpen = false,
                isPainedBeakClosed = false,
                isDarkModeShadows = isDarkMode
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(16.dp).zIndex(1f), contentAlignment = Alignment.Center) {
            if (isLandscape) {
                val (ghostText, ghostSize) = when (style) {
                    "FUSE" -> stringResource(R.string.critical) to 48.sp
                    "FROG" -> stringResource(R.string.ribbit_panic) to 48.sp
                    "HEN" -> stringResource(R.string.cracking) to 48.sp
                    else -> stringResource(R.string.detonation_sequence) to 12.sp
                }

                val leftPadding = if (style == "HEN") 160.dp else 192.dp

                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))

                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Column(
                            modifier = Modifier.padding(start = leftPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = ghostText,
                                color = Color.Transparent,
                                fontSize = ghostSize,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontFamily = CustomFont,
                                modifier = Modifier.height(0.dp).padding(horizontal = 16.dp)
                            )

                            BombTextContent(
                                style = style,
                                timeLeft = timeLeft,
                                isCritical = isFuseFinished,
                                isPaused = isPaused,
                                colors = colors,
                                modifier = Modifier.padding(bottom = 48.dp),
                                henSequenceElapsed = henSequenceElapsed
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            AbortButtonContent(colors, onAbort)
                        }
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BombTextContent(
                        style = style,
                        timeLeft = timeLeft,
                        isCritical = isFuseFinished,
                        isPaused = isPaused,
                        colors = colors,
                        henSequenceElapsed = henSequenceElapsed
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.size(300.dp))
                    Spacer(modifier = Modifier.height(64.dp))
                    AbortButtonContent(colors, onAbort)
                }
            }
        }
    }
}

@Composable
fun ExplosionScreen(colors: AppColors, style: String?, explosionOrigin: Offset? = null, onReset: () -> Unit) {
    val context = LocalContext.current
    val particles = remember {
        val colorsList = listOf(NeonRed, NeonOrange, Color.Yellow, Color.White)
        List(100) { i ->
            val angle = Math.random() * Math.PI * 2 // Calculate angle once!
            Particle(
                id = i,
                dirX = cos(angle).toFloat(),
                dirY = sin(angle).toFloat(),
                velocity = (200 + Math.random() * 800).toFloat(),
                size = (3 + Math.random() * 5).toFloat(),
                color = colorsList.random(),
                rotationSpeed = (Math.random() * 20 - 10).toFloat()
            )
        }
    }
    val smoke = remember {
        List(30) { _ -> SmokeParticle(x = 0f, y = 0f, vx = (Math.random() * 100 - 50).toFloat(), vy = (Math.random() * 100 - 50).toFloat(), size = (20 + Math.random() * 40).toFloat(), alpha = 0.8f, life = 1f, maxLife = 1f) }
    }

    var hasPlayedExplosion by remember { mutableStateOf(false) }
    val animationProgress = remember { Animatable(if (hasPlayedExplosion) 1f else 0f) }

    var henAnimTime by remember { mutableFloatStateOf(2.5f) }
    var isHenPaused by remember { mutableStateOf(false) }

    var hasPlayedWhistle by remember { mutableStateOf(false) }
    var hasPlayedThud by remember { mutableStateOf(false) }
    var hasPlayedSlide by remember { mutableStateOf(false) }

    var isPainedBeakClosed by remember { mutableStateOf(false) }
    var isPainedBeakOpen by remember { mutableStateOf(false) }

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
                val dist = p.velocity * progress * 2f
                val x = center.x + (p.dirX * dist)
                val y = center.y + (p.dirY * dist)
                val alpha = (1f - progress).coerceIn(0f, 1f)
                if (alpha > 0) drawCircle(color = p.color.copy(alpha = alpha), radius = p.size, center = Offset(x, y))
            }
            val shockwaveRadius = progress * size.width * 0.8f
            val shockwaveAlpha = (1f - progress).coerceIn(0f, 1f)
            if (shockwaveAlpha > 0) drawCircle(color = Color.White.copy(alpha = shockwaveAlpha * 0.5f), radius = shockwaveRadius, center = center, style = Stroke(width = 50f * (1f - progress)))
        }

        val flashAlpha = (1f - (animationProgress.value * 2.0f)).coerceIn(0f, 1f)
        if (flashAlpha > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        val titleText = if (style == "FROG") stringResource(R.string.croaked) else stringResource(R.string.boom)
        val titleSize = if (style == "FROG") 72.sp else 96.sp

        // --- NEW FIERY BRUSH (Old Brighter Colors) ---
        val boomBrush = Brush.verticalGradient(
            colors = listOf(Color.Yellow, NeonRed)
        )

        // --- NEW: THE SHARED WALKIE-TALKIE ---
        val sharedRestartInteraction = remember { MutableInteractionSource() }

        // Bottom Layer (Visuals)
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
            Spacer(modifier = Modifier.height(80.dp))

            // The visual button listens to the shared source to animate, but doesn't need click logic
            ActionButton(
                text = stringResource(R.string.restart),
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                interactionSource = sharedRestartInteraction, // <-- LISTENING
                onClick = { /* Handled by top layer */ }
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

        // Top Layer (Ghost Clicks)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(300f)) {
            Text(titleText, fontSize = titleSize, fontWeight = FontWeight.Black, fontFamily = CustomFont, color = Color.Transparent)
            Spacer(modifier = Modifier.height(80.dp))

            // The Ghost Box catches the click above the Hen and triggers the animation below!
            Box(
                modifier = Modifier
                    .size(200.dp, 60.dp)
                    .clickable(
                        interactionSource = sharedRestartInteraction, // <-- SENDING
                        indication = null
                    ) {
                        AudioService.playClick()
                        AudioService.stopSlide()
                        AudioService.stopWhistle()
                        onReset()
                    }
            )
        }
    }
}