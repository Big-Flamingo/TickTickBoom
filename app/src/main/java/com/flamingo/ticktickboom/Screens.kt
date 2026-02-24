package com.flamingo.ticktickboom

import android.content.Context
import android.content.res.Configuration
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
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
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// NOTE: Uses VisualParticle from AppModels.kt
// NOTE: Uses drawReflection and StrokeGlowText from Components.kt
// NOTE: Uses HenVisual and FrogVisual from AnimalVisuals.kt

// --- SCREENS ---

@Composable
fun SetupScreen(colors: AppColors, isDarkMode: Boolean, audio: AudioController, onToggleTheme: () -> Unit, onStart: (TimerSettings) -> Unit, onToggleLanguage: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }

    var minText by remember { mutableStateOf(prefs.getInt("min", 10).toString()) }
    var maxText by remember { mutableStateOf(prefs.getInt("max", 20).toString()) }
    var style by remember { mutableStateOf(prefs.getString("style", "C4") ?: "C4") }
    var timerVol by remember { mutableFloatStateOf(prefs.getFloat("vol_timer", 0.8f)) }
    var explodeVol by remember { mutableFloatStateOf(prefs.getFloat("vol_explode", 1.0f)) }
    // Holds the Resource ID of the error, or null if there is no error
    var errorResId by remember { mutableStateOf<Int?>(null) }

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
    fun saveStyle(newStyle: String) { style = newStyle; prefs.edit { putString("style", newStyle) }; audio.playClick() }
    fun saveTimerVol(vol: Float) { timerVol = vol; audio.timerVolume = vol; prefs.edit { putFloat("vol_timer", vol) } }
    fun saveExplodeVol(vol: Float) { explodeVol = vol; audio.explosionVolume = vol; prefs.edit { putFloat("vol_explode", vol) } }

    fun validateMin() {
        val rawMin = minText.toIntOrNull() ?: 1

        // --- Assign the Resource ID instead of the string! ---
        errorResId = when {
            rawMin > 99999 -> R.string.error_max_time
            rawMin <= 0 -> R.string.error_min_time
            else -> null
        }

        var min = rawMin.coerceAtMost(99999)
        if (min <= 0) min = 1
        minText = min.toString()
        prefs.edit { putInt("min", min) }

        val max = maxText.toIntOrNull() ?: min
        if (min > max) {
            maxText = min.toString()
            prefs.edit { putInt("max", min) }
        }
        focusManager.clearFocus()
    }

    fun validateMax() {
        val rawMax = maxText.toIntOrNull() ?: 1

        // --- Assign the Resource ID instead of the string! ---
        errorResId = when {
            rawMax > 99999 -> R.string.error_max_time
            rawMax <= 0 -> R.string.error_min_time
            else -> null
        }

        var max = rawMax.coerceAtMost(99999)
        if (max <= 0) max = 1
        maxText = max.toString()
        prefs.edit { putInt("max", max) }

        val min = minText.toIntOrNull() ?: max
        if (max < min) {
            minText = max.toString()
            prefs.edit { putInt("min", max) }
        }
        focusManager.clearFocus()
    }

    fun tryStart() {
        audio.playClick()

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
            audio.playBombCroak()
            wobbleAnim.snapTo(0f); wobbleAnim.animateTo(-15f, tween(50)); wobbleAnim.animateTo(15f, tween(50)); wobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (easterEggTaps >= 3) {
                // Shorter distance, faster time, accelerates out!
                flyAwayAnim.animateTo(screenHeightPx + 100f, tween(400, easing = FastOutLinearInEasing))

                val min = minText.toIntOrNull() ?: 5
                var max = maxText.toIntOrNull() ?: 10
                if (max < min) max = min

                prefs.edit { putInt("min", min); putInt("max", max); putFloat("vol_timer", timerVol); putFloat("vol_explode", explodeVol) }
                audio.timerVolume = timerVol; audio.explosionVolume = explodeVol
                onStart(TimerSettings(min, max, "FROG"))
            }
        }
    }

    fun launchHen() {
        scope.launch {
            isHoldingBomb = false
            audio.stopHoldingCluck()
            audio.playLoudCluck()

            // Shorter distance, faster time, accelerates out!
            flyAwayAnim.animateTo(-screenHeightPx - 100f, tween(400, easing = FastOutLinearInEasing))

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
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.statusBarsPadding())
            // Spacer(modifier = Modifier.height(16.dp))

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
                                        audio.startHoldingCluck()
                                    }
                                    tryAwaitRelease()
                                    job.cancel()
                                    if (isHoldingBomb) {
                                        isHoldingBomb = false
                                        audio.stopHoldingCluck()
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

            // --- NEW: If we have an ID, grab the string for the current language! ---
            if (errorResId != null) {
                Text(
                    text = stringResource(errorResId!!),
                    color = NeonRed,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                    fontFamily = CustomFont
                )
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

            // 1. Swap from interactionSource to raw state
            var isArmPressed by remember { mutableStateOf(false) }

            val targetArmColor = if (isArmPressed) Color(0xFF991B1B) else NeonRed
            val armAnimatedColor by animateColorAsState(
                targetValue = targetArmColor,
                // 2. Snap instantly to crimson on touch, fade back smoothly on release
                animationSpec = if (isArmPressed) tween(0) else tween(150),
                label = "armButtonHighlight"
            )

            val armDesc = stringResource(R.string.desc_arm_system)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(armAnimatedColor)
                    // --- NEW: Accessibility Semantics ---
                    .semantics {
                        role = Role.Button
                        contentDescription = armDesc
                    }
                    // 3. Swap clickable for pointerInput to catch the exact touch millisecond
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isArmPressed = true
                                tryAwaitRelease()
                                isArmPressed = false
                            },
                            onTap = { tryStart() }
                        )
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.arm_system), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
fun BombScreen(
    state: GameState,
    colors: AppColors,
    isDarkMode: Boolean,
    audio: AudioController, // <-- ADDED
    onIntent: (GameIntent) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // We only keep purely VISUAL state in the Composable now (like wobbles and flashes)
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

    LaunchedEffect(state.bombStyle) {
        if (state.bombStyle == "FROG" || state.bombStyle == "HEN") {
            slideInAnim.animateTo(0f, tween(500, easing = LinearOutSlowInEasing))
        }
    }

    // Calculate Crack Stage purely based on the State's Time Left
    val crackStage = if (state.bombStyle != "HEN") 0 else when {
        state.timeLeft <= 1.5f -> 3
        state.timeLeft <= 3.0f -> 2
        state.timeLeft <= 4.5f -> 1
        else -> 0
    }

    LaunchedEffect(crackStage) {
        if (state.bombStyle == "HEN" && !state.isPaused) {
            val shouldPlay = (crackStage == 1 && !hasPlayedCrack1) ||
                    (crackStage == 2 && !hasPlayedCrack2) ||
                    (crackStage == 3 && !hasPlayedCrack3)

            if (shouldPlay) {
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
            // Only resume if we are actively in the middle of the flapping animation!
            if (henSequenceElapsed > 0.5f && henSequenceElapsed < 2.5f) {
                var resumeVol = 1.0f

                // Match the exact fade-out math used in the animation loop
                if (henSequenceElapsed > 1.0f) {
                    resumeVol = (2.5f - henSequenceElapsed) / 1.5f
                }

                val finalVol = resumeVol.coerceIn(0f, 1f) * audio.timerVolume
                audio.playWingFlap(startVol = finalVol)
            }
        }
    }

    // --- MASTER VISUAL TIMER (Client-Side Prediction) ---
    val currentIsPaused by rememberUpdatedState(state.isPaused)
    val masterTimeLeft by rememberUpdatedState(state.timeLeft)

    // The smooth timer that the UI will actually use to draw
    var visualTimeLeft by remember(state.duration) { mutableFloatStateOf(state.duration.toFloat()) }

    LaunchedEffect(state.duration) {
        var lastFrameNanos = System.nanoTime()
        while(true) {
            withFrameNanos { nanos ->
                val dt = ((nanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameNanos = nanos

                if (!currentIsPaused && visualTimeLeft > 0f) {
                    visualTimeLeft -= dt

                    // Soft-Sync: If the visual timer drifts from the ViewModel's master timer
                    // by more than 50ms, we gently snap it back into place to prevent desync.
                    if (kotlin.math.abs(visualTimeLeft - masterTimeLeft) > 0.05f) {
                        visualTimeLeft = masterTimeLeft
                    }
                } else if (currentIsPaused) {
                    visualTimeLeft = masterTimeLeft // Keep perfectly synced when paused
                }
            }
        }
    }

    // --- THE FIX: Display-synced animation loop! ---
    LaunchedEffect(state.bombStyle) {
        if (state.bombStyle == "HEN") {
            var lastFrameNanos = System.nanoTime()
            while(true) {
                withFrameNanos { nanos ->
                    val dt = ((nanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                    lastFrameNanos = nanos

                    if (!currentIsPaused && visualTimeLeft <= 6.0f) {
                        henSequenceElapsed += dt

                        if (henSequenceElapsed > 0.5f && !hasPlayedFly) {
                            audio.playLoudCluck()
                            audio.playWingFlap(audio.timerVolume)
                            hasPlayedFly = true
                        }
                        if (henSequenceElapsed > 1.0f && henSequenceElapsed < 2.5f) {
                            val currentVol = (2.5f - henSequenceElapsed) / 1.5f
                            audio.updateWingFlapVolume(currentVol.coerceIn(0f, 1f))
                        }
                        if (henSequenceElapsed > 2.5f) audio.stopWingFlap()
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
        if (flashAnim.value > 0f) {
            val flashColor = if (isDarkMode) Color.White else Slate950
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(flashColor.copy(alpha = flashAnim.value))
                    .zIndex(-1f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .onGloballyPositioned {
                    // Send Intent to ViewModel regarding origin!
                    onIntent(GameIntent.UpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)))
                },
            contentAlignment = Alignment.Center
        ) {
            BombVisualContent(
                style = state.bombStyle,
                duration = state.duration,
                timeLeft = visualTimeLeft,
                isCritical = state.isCritical,
                isLedOn = state.isLedOn,
                isDarkMode = isDarkMode,
                colors = colors,
                isPaused = state.isPaused,
                onTogglePause = { onIntent(GameIntent.TogglePause) }, // Send Intent!
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
                        ) {
                            BombTextContent(
                                style = state.bombStyle,
                                visualTimeLeft,
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BombTextContent(
                        style = state.bombStyle,
                        timeLeft = visualTimeLeft,
                        isCritical = state.isCritical,
                        isPaused = state.isPaused,
                        colors = colors,
                        henSequenceElapsed = henSequenceElapsed
                    )
                    Spacer(modifier = Modifier.height(64.dp))
                    Box(modifier = Modifier.size(300.dp))
                    Spacer(modifier = Modifier.height(64.dp))
                    AbortButtonContent(colors) {
                        audio.playClick()
                        onIntent(GameIntent.Abort)
                    }
                }
            }
        }
    }
}

@Composable
fun ExplosionScreen(colors: AppColors, state: GameState, audio: AudioController, onIntent: (GameIntent) -> Unit) {
    var hasPlayedExplosion by remember { mutableStateOf(false) }
    val animationProgress = remember { Animatable(if (hasPlayedExplosion) 1f else 0f) }

    // --- AGSL SHADER STATE ---
    var shaderTime by remember { mutableFloatStateOf(0f) }
    val explosionShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(EXPLOSION_SHADER)
        } else null
    }

    // THE FIX 1: Wrap the Brush creation in a strict API check!
    val explosionBrush = remember(explosionShader) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && explosionShader != null) {
            ShaderBrush(explosionShader as android.graphics.Shader)
        } else null
    }

    // --- LOAD THE WEBP SPRITE ---
    val smokeSprite = ImageBitmap.imageResource(id = R.drawable.smoke_wisp)

    LaunchedEffect(Unit) {
        if (!hasPlayedExplosion) {
            launch { animationProgress.animateTo(1f, tween(2000, easing = LinearOutSlowInEasing)) }
            hasPlayedExplosion = true
        }

        var lastTimeNanos = 0L
        // THE FIX: Kill the loop the millisecond the explosion is finished!
        while (animationProgress.value < 1f) {
            withFrameNanos { nanos ->
                val dt = if (lastTimeNanos == 0L) 0.016f else ((nanos - lastTimeNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                shaderTime += dt
                lastTimeNanos = nanos
            }
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

            // 1. DRAW THE BACKGROUND EXPLOSION (ONLY IF IT'S STILL HAPPENING!)
            if (progress < 1f) {
                if (explosionShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    explosionShader.setFloatUniform("resolution", size.width, size.height)
                    explosionShader.setFloatUniform("center", center.x, center.y)
                    explosionShader.setFloatUniform("time", shaderTime)
                    explosionShader.setFloatUniform("progress", progress)

                    // THE FIX 2: Add !! to force-unwrap the safely checked variable
                    drawRect(brush = explosionBrush!!)
                } else {
                    val shockwaveRadius = progress * size.width * 0.8f
                    val shockwaveAlpha = (1f - progress).coerceIn(0f, 1f)
                    if (shockwaveAlpha > 0) drawCircle(color = Color.White, alpha = shockwaveAlpha * 0.5f, radius = shockwaveRadius, center = center, style = Stroke(width = 50f * (1f - progress)))

                    val flashAlpha = (1f - (progress * 2.0f)).coerceIn(0f, 1f)
                    if (flashAlpha > 0f) drawRect(Color.White.copy(alpha = flashAlpha))
                }
            }

            // 2. THE UPGRADED WEBP SMOKE
            state.smoke.forEachIndexed { i, s ->
                val currentX = center.x + (s.vx * progress * 3f)
                val currentY = center.y + (s.vy * progress * 3f)
                val currentSize = s.size + (progress * 150f)

                // THE MATH FIX: Force the alpha to hit 0 before the animation finishes!
                val fadeProgress = (progress * 1.5f).coerceIn(0f, 1f)
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
                            colorFilter = ColorFilter.tint(colors.smokeColor)
                        )
                    }
                }
            }

            // 3. THE SHRAPNEL PARTICLES
            state.particles.forEach { p ->
                val dist = p.velocity * progress * 2f
                val x = center.x + (p.dirX * dist)
                val y = center.y + (p.dirY * dist)
                val alpha = (1f - progress).coerceIn(0f, 1f)
                if (alpha > 0) drawCircle(color = p.color, alpha = alpha, radius = p.size, center = Offset(x, y))
            }
        }

        val titleText = if (state.bombStyle == "FROG") stringResource(R.string.croaked) else stringResource(R.string.boom)
        val titleSize = if (state.bombStyle == "FROG") 72.sp else 96.sp

        val boomBrush = Brush.verticalGradient(
            colors = listOf(Color.Yellow, NeonRed)
        )

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

            ActionButton(
                text = stringResource(R.string.restart),
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                interactionSource = sharedRestartInteraction,
                onClick = { /* Handled by top layer */ }
            )
        }

        // THE LAG FIX: Call the isolated wrapper instead of doing the math here!
        if (state.bombStyle == "HEN") {
            AnimatedHenOverlay(state = state, onIntent = onIntent)
        }

        // Top Layer (Ghost Clicks)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.zIndex(300f)) {
            Text(titleText, fontSize = titleSize, fontWeight = FontWeight.Black, fontFamily = CustomFont, color = Color.Transparent)
            Spacer(modifier = Modifier.height(80.dp))

            Box(
                modifier = Modifier
                    .size(200.dp, 60.dp)
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

    // THE FIX: The loop is restored! The timer will now advance past 2.5s,
    // triggering the Hen's slide animation perfectly.
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while(true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameNanos == 0L) 0.016f else ((nanos - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameNanos = nanos
                if (!currentIsHenPaused) {
                    visualHenAnimTime += dt
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().zIndex(200f), contentAlignment = Alignment.Center) {
        HenVisual(
            timeLeft = 0f,
            isPaused = state.isHenPaused,
            onTogglePause = { onIntent(GameIntent.ToggleHenPause) },
            eggWobbleRotation = 0f,
            henSequenceElapsed = visualHenAnimTime, // Passes the live time again!
            showEgg = false,
            isPainedBeakOpen = false,
            isPainedBeakClosed = state.isPainedBeakClosed,
            isDarkMode = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}