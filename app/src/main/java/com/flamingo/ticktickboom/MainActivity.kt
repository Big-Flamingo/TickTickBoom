package com.flamingo.ticktickboom

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColor
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AudioService.init(this)

        val prefs = getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE)
        AudioService.timerVolume = prefs.getFloat("vol_timer", 0.8f)
        AudioService.explosionVolume = prefs.getFloat("vol_explode", 1.0f)

        setContent {
            MaterialTheme {
                BombApp()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isChangingConfigurations) {
            AudioService.stopAll()
        }
    }
}

@Composable
fun BombApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }
    var isDarkMode by remember { mutableStateOf(prefs.getBoolean("dark_mode", true)) }

    val colors = if (isDarkMode) {
        AppColors(Slate950, Slate900, Slate800, Color.White, TextGray, SmokeLight)
    } else {
        AppColors(Slate50, Color.White, Slate200, Slate900, Color.Gray, SmokeDark)
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
        }
    }

    fun toggleTheme() {
        isDarkMode = !isDarkMode
        prefs.edit { putBoolean("dark_mode", isDarkMode) }
        AudioService.playClick()
    }

    var appState by rememberSaveable { mutableStateOf(AppState.SETUP) }
    var duration by rememberSaveable { mutableIntStateOf(0) }
    var bombStyle by rememberSaveable { mutableStateOf("C4") }
    var startTime by rememberSaveable { mutableLongStateOf(0L) }

    var isPaused by rememberSaveable { mutableStateOf(false) }
    var totalPausedTime by rememberSaveable { mutableLongStateOf(0L) }
    var currentPauseStart by rememberSaveable { mutableLongStateOf(0L) }

    var explosionOrigin by remember { mutableStateOf(Offset.Zero) }

    fun handleStart(settings: TimerSettings) {
        duration = Random.nextInt(settings.minSeconds, settings.maxSeconds + 1)
        bombStyle = settings.style
        startTime = System.currentTimeMillis()
        isPaused = false
        totalPausedTime = 0L
        currentPauseStart = 0L
        appState = AppState.RUNNING
    }

    fun handleExplode() {
        AudioService.stopAll()
        appState = AppState.EXPLODED
    }

    fun handleReset() {
        AudioService.stopAll()
        appState = AppState.SETUP
    }

    fun handleAbort() {
        AudioService.stopAll()
        appState = AppState.SETUP
    }

    fun handleTogglePause() {
        if (appState != AppState.RUNNING) return
        isPaused = !isPaused
        AudioService.playPauseInteraction(bombStyle, isPaused)
        if (isPaused) {
            currentPauseStart = System.currentTimeMillis()
            if (bombStyle != "FROG") {
                AudioService.stopAll()
            }
        } else {
            if (currentPauseStart > 0) {
                totalPausedTime += (System.currentTimeMillis() - currentPauseStart)
            }
        }
    }

    BackHandler(enabled = appState != AppState.SETUP) {
        if (appState == AppState.RUNNING) handleAbort()
        else if (appState == AppState.EXPLODED) handleReset()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.background) {
        when (appState) {
            AppState.SETUP -> SetupScreen(colors, isDarkMode, { toggleTheme() }, { handleStart(it) })
            AppState.RUNNING -> BombScreen(
                duration = duration,
                startTime = startTime,
                style = bombStyle,
                colors = colors,
                isDarkMode = isDarkMode,
                isPaused = isPaused,
                totalPausedTime = totalPausedTime,
                currentPauseStart = currentPauseStart,
                onExplode = { handleExplode() },
                onAbort = { handleAbort() },
                onTogglePause = { handleTogglePause() },
                onUpdateExplosionOrigin = { explosionOrigin = it }
            )
            AppState.EXPLODED -> ExplosionScreen(colors, bombStyle, explosionOrigin, { handleReset() })
        }
    }
}

@Composable
fun SetupScreen(colors: AppColors, isDarkMode: Boolean, onToggleTheme: () -> Unit, onStart: (TimerSettings) -> Unit) {
    val context = LocalContext.current
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

    fun tryStart() {
        AudioService.playClick()
        val min = minText.toIntOrNull() ?: 0
        var max = maxText.toIntOrNull() ?: 0

        if (min <= 0) { errorMsg = "Time must be > 0"; return }

        // FIX: Auto-correct if max is lower than min
        if (max < min) {
            max = min
            maxText = max.toString() // Update the UI so the user sees it
            prefs.edit { putInt("max", max) }
        }

        // Removed the error "Min must be less than Max" since we just fixed it.
        onStart(TimerSettings(min, max, style))
    }

    // FROG: Tap 3 times to fly DOWN (+height)
    fun handleBombTap() {
        if (easterEggTaps >= 3) return
        scope.launch {
            easterEggTaps++
            AudioService.playBombCroak()
            wobbleAnim.snapTo(0f); wobbleAnim.animateTo(-15f, tween(50)); wobbleAnim.animateTo(15f, tween(50)); wobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            if (easterEggTaps >= 3) {
                val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
                flyAwayAnim.animateTo(screenHeight + 500f, tween(800, easing = FastOutSlowInEasing))

                var min = minText.toIntOrNull() ?: 5
                var max = maxText.toIntOrNull() ?: 10

                // FIX: Silent Correction to prevent crash
                if (max < min) max = min

                prefs.edit { putInt("min", min); putInt("max", max); putFloat("vol_timer", timerVol); putFloat("vol_explode", explodeVol) }
                AudioService.timerVolume = timerVol; AudioService.explosionVolume = explodeVol

                onStart(TimerSettings(min, max, "FROG"))

                easterEggTaps = 0; delay(500); flyAwayAnim.snapTo(0f)
            }
        }
    }

    // HEN: Hold to fly UP (-height)
    fun launchHen() {
        scope.launch {
            // 1. Stop holding sound cleanly
            isHoldingBomb = false
            AudioService.stopHoldingCluck()

            // 2. Play Launch Sound
            AudioService.playLoudCluck()

            // 3. Animate UP off-screen
            val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
            flyAwayAnim.animateTo(-screenHeight - 500f, tween(800, easing = FastOutSlowInEasing))

            // 4. Start Game
            var min = minText.toIntOrNull() ?: 5
            var max = maxText.toIntOrNull() ?: 10

            // FIX: Silent Correction to prevent crash
            if (max < min) max = min

            onStart(TimerSettings(min, max, "HEN"))

            // 5. Reset for next time
            delay(500)
            flyAwayAnim.snapTo(0f)
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

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                // Start HOLD logic
                                val job = scope.launch {
                                    delay(200) // 200ms buffer
                                    isHoldingBomb = true
                                    AudioService.startHoldingCluck()
                                }
                                tryAwaitRelease()
                                // Released
                                job.cancel()
                                if (isHoldingBomb) {
                                    // If we were holding, stop the sound (launchHen will handle logic if triggered)
                                    isHoldingBomb = false
                                    AudioService.stopHoldingCluck()
                                }
                            },
                            onTap = { handleBombTap() },
                            onLongPress = {
                                // Trigger the Transition Animation
                                launchHen()
                            }
                        )
                    }
                    .graphicsLayer {
                        rotationZ = if (isHoldingBomb) holdingShakeOffset else wobbleAnim.value
                        scaleX = if (isHoldingBomb) 1.1f else 1f
                        scaleY = if (isHoldingBomb) 1.1f else 1f
                    },
                contentAlignment = Alignment.Center
            ) {
                val path = remember { Path() }
                Canvas(modifier = Modifier.size(32.dp).offset(y = 10.dp)) {
                    val shadowOffsetX = 10.dp.toPx(); val shadowOffsetY = 10.dp.toPx(); val shadowW = 20.dp.toPx(); val shadowH = 6.dp.toPx()
                    val fuseMoveToY = 14.dp.toPx(); val fuseQuadX = 8.dp.toPx(); val fuseQuadY = 20.dp.toPx(); val fuseEndX = 12.dp.toPx(); val fuseEndY = 12.dp.toPx(); val fuseStroke = 2.dp.toPx()
                    val neckOffsetX = 4.dp.toPx(); val neckOffsetY = 14.dp.toPx(); val neckW = 8.dp.toPx(); val neckH = 4.dp.toPx()
                    val bodyOffsetX = 4.dp.toPx(); val bodyOffsetY = 4.dp.toPx(); val gradRadius = 16.dp.toPx(); val bodyRadius = 12.dp.toPx()
                    val glintPivot = 3.dp.toPx(); val glintOffsetX = 8.dp.toPx(); val glintOffsetY = 8.dp.toPx(); val glintW = 8.dp.toPx(); val glintH = 5.dp.toPx()

                    drawOval(brush = Brush.radialGradient(colors = listOf(Color.Black.copy(0.6f), Color.Transparent)), topLeft = Offset(center.x - shadowOffsetX, center.y + shadowOffsetY), size = Size(shadowW, shadowH))
                    path.reset(); path.moveTo(center.x, center.y - fuseMoveToY); path.quadraticTo(center.x + fuseQuadX, center.y - fuseQuadY, center.x + fuseEndX, center.y - fuseEndY)
                    drawPath(path = path, brush = Brush.linearGradient(colors = listOf(Color(0xFFB91C1C), NeonRed, Color(0xFFB91C1C))), style = Stroke(width = fuseStroke, cap = StrokeCap.Round))
                    drawRoundRect(brush = Brush.linearGradient(colors = listOf(NeonRed, Color(0xFF7F1D1D))), topLeft = Offset(center.x - neckOffsetX, center.y - neckOffsetY), size = Size(neckW, neckH), cornerRadius = CornerRadius(2f, 2f))
                    drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFFF6B6B), NeonRed, Color(0xFF991B1B)), center = Offset(center.x - bodyOffsetX, center.y - bodyOffsetY), radius = gradRadius), radius = bodyRadius, center = center)
                    withTransform({ rotate(-20f, pivot = Offset(center.x - glintPivot, center.y - glintPivot)) }) {
                        drawOval(brush = Brush.linearGradient(colors = listOf(Color.White.copy(0.4f), Color.White.copy(0.05f))), topLeft = Offset(center.x - glintOffsetX, center.y - glintOffsetY), size = Size(glintW, glintH))
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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
                TimeInput("MIN SECS", minText, { saveMin(it) }, NeonCyan, colors, Modifier.weight(1f))
                TimeInput("MAX SECS", maxText, { saveMax(it) }, NeonRed, colors, Modifier.weight(1f))
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

    // SEQUENCE SOUND FLAGS
    var hasPlayedFly by rememberSaveable { mutableStateOf(false) }
    var hasPlayedThud by rememberSaveable { mutableStateOf(false) }
    var hasPlayedSlide by rememberSaveable { mutableStateOf(false) }

    var hasCracked1 by rememberSaveable { mutableStateOf(false) }
    var hasCracked2 by rememberSaveable { mutableStateOf(false) }
    var hasCracked3 by rememberSaveable { mutableStateOf(false) }

    var lastTickRunTime by rememberSaveable { mutableLongStateOf((initialElapsed * 1000).toLong() - 1000) }

    val flashAnim = remember { Animatable(0f) }
    val eggWobbleAnim = remember { Animatable(0f) }

    // Master clock for Hen Sequence
    var henSequenceElapsed by rememberSaveable { mutableFloatStateOf(0f) }
    var isHenSequenceActive by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isPaused) {
        if (isPaused) {
            AudioService.stopSlide() // Stop completely on pause
            AudioService.stopHoldingCluck()
        } else {
            // UNPAUSED: Check time window and RESTART sound if needed
            if (style == "HEN" && henSequenceElapsed > 3.5f && henSequenceElapsed < 6.5f) {
                AudioService.playHenSlide()
            }
            if (style == "FUSE") AudioService.startFuse(false)
        }
    }

    LaunchedEffect(key1 = startTime, key2 = isPaused, key3 = totalPausedTime) {
        var lastFrameTime = 0L

        // NEW: Loop runs as long as the timer is active
        while (timeLeft > 0.01) {
            // NEW: withFrameNanos locks execution to the display's VSync (smooth!)
            withFrameNanos { frameTimeNanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = frameTimeNanos

                if (!isPaused) {
                    val now = System.currentTimeMillis()
                    val currentRunTimeMs = now - startTime - totalPausedTime
                    val elapsed = currentRunTimeMs / 1000f
                    timeLeft = (duration - elapsed)

                    // --- HEN SEQUENCE LOGIC (Start at 11s) ---
                    if (style == "HEN" && timeLeft <= 11f && !isHenSequenceActive) {
                        isHenSequenceActive = true
                    }

                    if (isHenSequenceActive) {
                        henSequenceElapsed += dt

                        // Sound Triggers
                        // 0.3s: Pre-fire FLY sound
                        if (henSequenceElapsed > 0.3f && !hasPlayedFly) {
                            AudioService.playLoudCluck()
                            hasPlayedFly = true
                        }

                        // 2.75s: Impact Thud
                        if (henSequenceElapsed > 2.75f && !hasPlayedThud) {
                            AudioService.playGlassTap()
                            hasPlayedThud = true
                        }

                        // 3.25s: Slide Start
                        if (henSequenceElapsed > 3.25f && !hasPlayedSlide) {
                            AudioService.playHenSlide()
                            hasPlayedSlide = true
                        }

                        // Slide Audio Fade Out (Ends 0.5s sooner now)
                        if (henSequenceElapsed > 3.25f) {
                            val slideProgress = (henSequenceElapsed - 3.25f) / 2.5f
                            val fadeVol = (1f - slideProgress).coerceIn(0f, 1f)
                            AudioService.updateSlideVolume(fadeVol)
                        }
                    }

                    // Fuse Logic
                    if (timeLeft <= 5f && !isFuseFinished) {
                        isFuseFinished = true
                        if (style == "FUSE") AudioService.dimFuse()
                    }

                    // Standard Sounds
                    if (style == "DYNAMITE" && timeLeft <= 1.0f && !hasPlayedDing && timeLeft > 0) {
                        AudioService.playDing(); hasPlayedDing = true
                    }
                    if (style == "FROG" && timeLeft <= 1.05f && !hasPlayedAlert && timeLeft > 0) {
                        AudioService.playAlert(); hasPlayedAlert = true
                    }
                    if (style == "FROG" && timeLeft <= 1.0f && !hasPlayedFlail && timeLeft > 0) {
                        AudioService.playFlail(); hasPlayedFlail = true
                    }

                    // --- EGG CRACK LOGIC ---
                    val isHenDone = henSequenceElapsed > 2.75f || duration <= 6

                    if (style == "HEN" && timeLeft <= 5f) {
                        if (timeLeft <= 4.0f && !hasCracked1 && isHenDone) {
                            AudioService.playCrack()
                            launch { eggWobbleAnim.snapTo(0f); eggWobbleAnim.animateTo(15f, tween(50)); eggWobbleAnim.animateTo(-15f, tween(50)); eggWobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            hasCracked1 = true
                        }
                        if (timeLeft <= 2.5f && !hasCracked2 && isHenDone) {
                            AudioService.playCrack()
                            launch { eggWobbleAnim.snapTo(0f); eggWobbleAnim.animateTo(15f, tween(50)); eggWobbleAnim.animateTo(-15f, tween(50)); eggWobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            hasCracked2 = true
                        }
                        if (timeLeft <= 1.0f && !hasCracked3 && isHenDone) {
                            AudioService.playCrack()
                            launch { eggWobbleAnim.snapTo(0f); eggWobbleAnim.animateTo(15f, tween(50)); eggWobbleAnim.animateTo(-15f, tween(50)); eggWobbleAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            hasCracked3 = true
                        }
                    }

                    val tickInterval = if (timeLeft < 5) 500L else 1000L

                    if (currentRunTimeMs - lastTickRunTime >= tickInterval && timeLeft > 0) {
                        if (style == "C4") { AudioService.playTick(); isLedOn = true }
                        if (style == "DYNAMITE" && timeLeft > 1.0) AudioService.playClockTick()
                        if (style == "FROG") { AudioService.playCroak(timeLeft < 5) }
                        if (style == "HEN") {
                            if (!isHenSequenceActive) {
                                AudioService.playHenCluck()
                            }
                        }
                        lastTickRunTime = currentRunTimeMs
                    }
                    if (isLedOn && (currentRunTimeMs - lastTickRunTime > 50)) isLedOn = false
                } else {
                    lastFrameTime = frameTimeNanos // Reset frame time on pause to prevent jumps
                }
            }
        }

        // Timer Finished
        if (!isPaused) {
            timeLeft = 0f
            launch { flashAnim.animateTo(1f, tween(50, easing = LinearOutSlowInEasing)) }
            AudioService.playExplosion(context)
            delay(100)
            onExplode()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    // NEW: zIndex(100f) ensures visuals render ON TOP of buttons for the slide effect
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().zIndex(100f), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.onGloballyPositioned { onUpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)) }) {
                            BombVisualContent(style, duration, timeLeft, isCritical, isLedOn, isDarkMode, colors, isPaused, onTogglePause, eggWobbleAnim.value, henSequenceElapsed)
                        }
                    }
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        // PASSED timeLeft to content
                        BombTextContent(style, timeLeft, isFuseFinished, isPaused, colors)
                        Spacer(modifier = Modifier.height(32.dp))
                        AbortButtonContent(colors, onAbort)
                    }
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // PASSED timeLeft to content
                    BombTextContent(style, timeLeft, isFuseFinished, isPaused, colors)
                    Spacer(modifier = Modifier.height(32.dp))
                    // NEW: zIndex(100f) ensures visuals render ON TOP of buttons
                    Box(modifier = Modifier.zIndex(100f).onGloballyPositioned { onUpdateExplosionOrigin(it.positionInRoot() + Offset(it.size.width/2f, it.size.height/2f)) }) {
                        BombVisualContent(style, duration, timeLeft, isCritical, isLedOn, isDarkMode, colors, isPaused, onTogglePause, eggWobbleAnim.value, henSequenceElapsed)
                    }
                    Spacer(modifier = Modifier.height(64.dp))
                    AbortButtonContent(colors, onAbort)
                }
            }
        }
        if (flashAnim.value > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAnim.value)))
    }
}

// Updated Signature
@Composable
fun BombVisualContent(
    style: String,
    duration: Int,
    timeLeft: Float,
    isCritical: Boolean,
    isLedOn: Boolean,
    isDarkMode: Boolean,
    colors: AppColors,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    eggWobbleRotation: Float = 0f,
    henSequenceElapsed: Float = 0f // New Parameter
) {
    when (style) {
        "FUSE" -> {
            val fuseBurnDuration = (duration - 5).coerceAtLeast(0)
            val currentBurnTime = (duration - timeLeft).coerceAtLeast(0f)
            val progress = if (fuseBurnDuration > 0) (currentBurnTime / fuseBurnDuration).coerceIn(0f, 1f) else 1f
            FuseVisual(progress, isCritical, colors, isPaused, onTogglePause)
        }
        "C4" -> C4Visual(isLedOn, isDarkMode, isPaused, onTogglePause)
        "DYNAMITE" -> DynamiteVisual(timeLeft, isPaused, onTogglePause)
        "FROG" -> FrogVisual(timeLeft, isCritical, isPaused, onTogglePause)
        "HEN" -> HenVisual(timeLeft, isCritical, isPaused, onTogglePause, eggWobbleRotation, henSequenceElapsed)
    }
}

// UPDATE: Added timeLeft parameter for precise frog text logic
@Composable
fun BombTextContent(style: String, timeLeft: Float, isCritical: Boolean, isPaused: Boolean, colors: AppColors) {
    if (isPaused) {
        when (style) {
            "FUSE" -> {
                Text("PAUSED", color = NeonCyan, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = NeonCyan, blurRadius = 20f)))
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonCyan), shape = RoundedCornerShape(50)) {
                    Text("EXTINGUISHED", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
            "FROG", "HEN" -> {
                Text("PAUSED", color = NeonCyan, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = NeonCyan, blurRadius = 20f)))
            }
            else -> {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonCyan), shape = RoundedCornerShape(50), modifier = Modifier.padding(bottom = 48.dp)) {
                    Text("SYSTEM PAUSED", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
        }
        return
    }

    when (style) {
        "FUSE" -> {
            if (!isCritical) {
                Text("ARMED", color = NeonOrange, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = NeonOrange, blurRadius = 20f)))
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonOrange), shape = RoundedCornerShape(50)) {
                    Text("FUSE BURNING", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            } else {
                val infiniteTransition = rememberInfiniteTransition()
                val color by infiniteTransition.animateColor(initialValue = NeonRed, targetValue = colors.text, animationSpec = infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "crit")
                Text("CRITICAL", color = color, fontSize = 48.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = NeonRed, blurRadius = 40f)), fontFamily = CustomFont)
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonRed), shape = RoundedCornerShape(50)) {
                    Text("DETONATION IMMINENT", color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
        }
        "FROG" -> {
            // NEW LOGIC: RIBBIT -> RIBBIT? (Critical) -> RIBBIT! (Panic < 1.05s)
            val text = when {
                timeLeft <= 1.05f -> "RIBBIT!"
                isCritical -> "RIBBIT?"
                else -> "RIBBIT"
            }
            Text(text, color = FrogBody, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = FrogBody, blurRadius = 20f)))
        }
        "HEN" -> {
            val text = if (isCritical) "CRACKING" else "CLUCK"
            val color = if (isCritical) NeonRed else NeonOrange
            Text(text, color = color, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 2.sp, style = TextStyle(shadow = Shadow(color = color, blurRadius = 20f)))
        }
        else -> {
            Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonRed), shape = RoundedCornerShape(50), modifier = Modifier.padding(bottom = 48.dp)) {
                Text("DETONATION SEQUENCE", color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
            }
        }
    }
}

@Composable
fun AbortButtonContent(colors: AppColors, onAbort: () -> Unit) {
    ActionButton(
        text = "ABORT",
        icon = Icons.Filled.Close,
        color = colors.surface.copy(alpha=0.5f),
        textColor = colors.textSecondary,
        borderColor = colors.textSecondary,
        borderWidth = 1.dp,
        onClick = { AudioService.playClick(); onAbort() }
    )
}