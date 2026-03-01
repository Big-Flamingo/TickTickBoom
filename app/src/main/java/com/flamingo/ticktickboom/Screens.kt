package com.flamingo.ticktickboom

import android.content.Context
import android.content.res.Configuration
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.ui.draw.drawBehind
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
fun SetupScreen(colors: AppColors, isDarkMode: Boolean, audio: AudioController, onToggleTheme: () -> Unit, onStart: (TimerSettings) -> Unit, onGroupStart: (GroupPreset, String) -> Unit, onToggleLanguage: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences("bomb_timer_prefs", Context.MODE_PRIVATE) }

    // --- DESIGN TOKENS (Change these once to update the whole screen!) ---
    val unselectedGlass = colors.surface.copy(alpha = 0.3f)
    val faintOutline = colors.border
    val touchDarkenAmount = 0.4f // <-- NEW: Controls how dark buttons get when tapped!
    val darkOrangePressed = Color(0xFFB24D00)
    val darkCyanPressed = Color(0xFF007A8A)

    // --- NEW: Group Manager ---
    val groupManager = remember { GroupPresetManager(context) }
    var savedPresets by remember { mutableStateOf(groupManager.loadPresets()) }
    var selectedTabIndex by remember { mutableIntStateOf(prefs.getInt("last_tab", 0)) }
    var selectedPresetIndex by remember {
        mutableStateOf(
            if (savedPresets.isEmpty()) null
            else {
                val lastId = prefs.getString("last_preset_id", null)
                val index = savedPresets.indexOfFirst { it.id == lastId }
                // If the saved ID was found, use its index. Otherwise, default to 0.
                if (index >= 0) index else 0
            }
        )
    }

    // Random Mode Data
    var minText by remember { mutableStateOf(prefs.getInt("min", 10).toString()) }
    var maxText by remember { mutableStateOf(prefs.getInt("max", 20).toString()) }

    // Group Mode Data (If creating a new preset or editing)
    var groupTimeText by remember { mutableStateOf("10") }
    var isCreatingNewPreset by remember { mutableStateOf(false) }
    var isEditingPreset by remember { mutableStateOf(false) }
    var editingPresetId by remember { mutableStateOf<String?>(null) }
    // --- UPDATED: Remembers the last expanded state ---
    var isRosterExpanded by remember { mutableStateOf(prefs.getBoolean("is_roster_expanded", false)) }
    var isOtherPresetsExpanded by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }
    var tempPlayers by remember { mutableStateOf(emptyList<Player>()) }
    var resetTimeRule by remember { mutableStateOf(false) }

    var style by remember { mutableStateOf(prefs.getString("style", "C4") ?: "C4") }
    var timerVol by remember { mutableFloatStateOf(prefs.getFloat("vol_timer", 1.0f)) }
    var explodeVol by remember { mutableFloatStateOf(prefs.getFloat("vol_explode", 1.0f)) }
    var errorResId by remember { mutableStateOf<Int?>(null) }

    var easterEggTaps by remember { mutableIntStateOf(0) }
    val wobbleAnim = remember { Animatable(0f) }
    val flyAwayAnim = remember { Animatable(0f) }
    val setupFusePath = remember { Path() }

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
        if (selectedTabIndex == 0) {
            var min = minText.toIntOrNull() ?: 1
            if (min <= 0) min = 1
            var max = maxText.toIntOrNull() ?: 1
            if (max < min) max = min

            minText = min.toString()
            maxText = max.toString()
            prefs.edit { putInt("min", min); putInt("max", max) }

            onStart(TimerSettings(min, max, style))
        } else {
            // Group Mode Start!
            if (selectedPresetIndex != null && savedPresets.isNotEmpty()) {
                val preset = savedPresets[selectedPresetIndex!!]
                onGroupStart(preset, style)
            }
        }
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

                // THE FIX: Check which tab we are on before launching!
                if (selectedTabIndex == 0) {
                    val min = minText.toIntOrNull() ?: 5
                    var max = maxText.toIntOrNull() ?: 10
                    if (max < min) max = min

                    prefs.edit { putInt("min", min); putInt("max", max); putFloat("vol_timer", timerVol); putFloat("vol_explode", explodeVol) }
                    audio.timerVolume = timerVol; audio.explosionVolume = explodeVol
                    onStart(TimerSettings(min, max, "FROG"))
                } else {
                    // Group Mode Frog!
                    if (selectedPresetIndex != null && savedPresets.isNotEmpty()) {
                        val preset = savedPresets[selectedPresetIndex!!]
                        onGroupStart(preset, "FROG")
                    } else {
                        // Failsafe: Reset if they somehow trigger it with no presets
                        easterEggTaps = 0
                        flyAwayAnim.snapTo(0f)
                    }
                }
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

            // THE FIX: Check which tab we are on before launching!
            if (selectedTabIndex == 0) {
                val min = minText.toIntOrNull() ?: 5
                var max = maxText.toIntOrNull() ?: 10
                if (max < min) max = min

                onStart(TimerSettings(min, max, "HEN"))
            } else {
                // Group Mode Hen!
                if (selectedPresetIndex != null && savedPresets.isNotEmpty()) {
                    val preset = savedPresets[selectedPresetIndex!!]
                    onGroupStart(preset, "HEN")
                } else {
                    // Failsafe
                    flyAwayAnim.snapTo(0f)
                }
            }
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

            // --- THE FIX 3: Pre-allocate lists so shaking the bomb doesn't spawn memory garbage! ---
            val fuseColors = remember { listOf(Color(0xFFB91C1C), NeonRed, Color(0xFFB91C1C)) }
            val neckColors = remember { listOf(NeonRed, Color(0xFF7F1D1D)) }
            val bodyColors = remember { listOf(Color(0xFFFF6B6B), NeonRed, Color(0xFF991B1B)) }
            val glintColors = remember { listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0.05f)) }

            // --- NEW: Wrapper Box to align Bomb and Language Switch ---
            Box(modifier = Modifier.fillMaxWidth()) {
                // 1. THE BOMB (Centered)
                // THE FIX: A pure class bypasses Compose's state-tracking overhead entirely!
                class BombCache {
                    var size: Size = Size.Zero
                    var fuse: Brush? = null
                    var neck: Brush? = null
                    var body: Brush? = null
                    var glint: Brush? = null
                }
                val cache = remember { BombCache() }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(64.dp)
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
                    Canvas(modifier = Modifier.size(40.dp).offset(y = 2.dp)) {
                        val currentRotation = if (isHoldingBomb) holdingShakeOffset else wobbleAnim.value
                        val bodyRadius = 12.dp.toPx()
                        val floorY = center.y + bodyRadius

                        val neckOffsetX = 4.dp.toPx(); val neckOffsetY = 14.dp.toPx()
                        val neckW = 8.dp.toPx(); val neckH = 4.dp.toPx()

                        // --- THE FIX: Generate Shaders exactly once! ---
                        if (size != cache.size) {
                            cache.fuse = Brush.linearGradient(fuseColors)
                            cache.neck = Brush.linearGradient(neckColors)
                            val bodyOffsetX = 4.dp.toPx(); val bodyOffsetY = 4.dp.toPx(); val gradRadius = 16.dp.toPx()
                            cache.body = Brush.radialGradient(bodyColors, center = Offset(center.x - bodyOffsetX, center.y - bodyOffsetY), radius = gradRadius)
                            cache.glint = Brush.linearGradient(glintColors)
                            cache.size = size
                        }

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
                                drawPath(path = setupFusePath, brush = cache.fuse!!, style = bombFuseStroke)

                                // B. NECK
                                drawRoundRect(brush = cache.neck!!, topLeft = Offset(center.x - neckOffsetX, center.y - neckOffsetY), size = Size(neckW, neckH), cornerRadius = CornerRadius(2f, 2f))

                                // C. BODY
                                drawCircle(brush = cache.body!!, radius = bodyRadius, center = center)

                                // D. GLINT
                                val glintPivot = 3.dp.toPx(); val glintOffsetX = 8.dp.toPx(); val glintOffsetY = 8.dp.toPx(); val glintW = 8.dp.toPx(); val glintH = 5.dp.toPx()
                                withTransform({ rotate(-20f, pivot = Offset(center.x - glintPivot, center.y - glintPivot)) }) {
                                    drawOval(brush = cache.glint!!, topLeft = Offset(center.x - glintOffsetX, center.y - glintOffsetY), size = Size(glintW, glintH))
                                }
                            }
                        }
                    }
                }

                // 2. THE LANGUAGE SWITCH (Top Right)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 0.dp)
                        .offset(x = 10.dp)
                ) {
                    LanguageSwitch(colors = colors, onClick = onToggleLanguage)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_title_tick), color = colors.text, fontSize = 26.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont, letterSpacing = 1.sp)
                Text(stringResource(R.string.app_title_boom), color = NeonRed, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont, letterSpacing = 1.sp)
            }
            Text(stringResource(R.string.randomized_sequence), color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 1.sp, modifier = Modifier.padding(top=4.dp), fontFamily = CustomFont)
            Spacer(modifier = Modifier.height(24.dp))

            // --- THE NEW TABS ---
            val randomPressed = remember { mutableStateOf(false) }
            val groupPressed = remember { mutableStateOf(false) }

            // 1. RANDOM MODE MATH (NeonCyan)
            val isRandomSelected = selectedTabIndex == 0
            val randomPressProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (randomPressed.value) 1f else 0f,
                animationSpec = if (randomPressed.value) tween(0) else tween(150),
                label = "randomPress"
            )

            val randomBaseBg = if (isRandomSelected) NeonCyan else unselectedGlass
            val randomPressedBg = if (isRandomSelected) darkCyanPressed else lerp(unselectedGlass, Color.Gray, touchDarkenAmount)
            val randomAnimatedBg = lerp(randomBaseBg, randomPressedBg, randomPressProgress)

            val randomBaseBorder = if (isRandomSelected) NeonCyan else faintOutline
            val randomPressedBorder = if (isRandomSelected) darkCyanPressed else Color.Transparent
            val randomAnimatedBorder = lerp(randomBaseBorder, randomPressedBorder, randomPressProgress)

            // 2. GROUP MODE MATH (NeonOrange)
            val isGroupSelected = selectedTabIndex == 1
            val groupPressProgress by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (groupPressed.value) 1f else 0f,
                animationSpec = if (groupPressed.value) tween(0) else tween(150),
                label = "groupPress"
            )

            val groupBaseBg = if (isGroupSelected) NeonOrange else unselectedGlass
            val groupPressedBg = if (isGroupSelected) darkOrangePressed else lerp(unselectedGlass, Color.Gray, touchDarkenAmount)
            val groupAnimatedBg = lerp(groupBaseBg, groupPressedBg, groupPressProgress)

            val groupBaseBorder = if (isGroupSelected) NeonOrange else faintOutline
            val groupPressedBorder = if (isGroupSelected) darkOrangePressed else Color.Transparent
            val groupAnimatedBorder = lerp(groupBaseBorder, groupPressedBorder, groupPressProgress)

            // 3. THE SPLIT LAYOUT
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp) // <-- THE FIX: Splits them apart!
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(randomAnimatedBg, RoundedCornerShape(8.dp))
                        .border(if (isRandomSelected) 2.dp else 1.dp, randomAnimatedBorder, RoundedCornerShape(8.dp)) // Independent border!
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { randomPressed.value = true; tryAwaitRelease(); randomPressed.value = false },
                                onTap = { selectedTabIndex = 0; prefs.edit { putInt("last_tab", 0) }; audio.playClick() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text("RANDOM MODE", color = if (isRandomSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Bold, fontFamily = CustomFont) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(groupAnimatedBg, RoundedCornerShape(8.dp))
                        .border(if (isGroupSelected) 2.dp else 1.dp, groupAnimatedBorder, RoundedCornerShape(8.dp)) // Independent border!
                        .clip(RoundedCornerShape(8.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { groupPressed.value = true; tryAwaitRelease(); groupPressed.value = false },
                                onTap = { selectedTabIndex = 1; prefs.edit { putInt("last_tab", 1) }; audio.playClick() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text("GROUP MODE", color = if (isGroupSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Bold, fontFamily = CustomFont) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // THE STYLE BUTTONS (Shared across both modes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StyleButton(stringResource(R.string.style_digital), Icons.Rounded.DeveloperBoard, style == "C4", NeonCyan, colors) { saveStyle("C4") }
                StyleButton(stringResource(R.string.style_fuse), Icons.Rounded.LocalFireDepartment, style == "FUSE", NeonOrange, colors) { saveStyle("FUSE") }
                StyleButton(stringResource(R.string.style_timer), Icons.Rounded.AccessTime, style == "DYNAMITE", NeonRed, colors) { saveStyle("DYNAMITE") }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // --- DYNAMIC CONTENT BASED ON TAB ---
            if (selectedTabIndex == 0) {
                // RANDOM MODE UI (Your original inputs)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TimeInput(stringResource(R.string.min_secs), minText, { saveMin(it) }, NeonCyan, colors, Modifier.weight(1f), { validateMin() })
                    TimeInput(stringResource(R.string.max_secs), maxText, { saveMax(it) }, NeonRed, colors, Modifier.weight(1f), { validateMax() })
                }
                if (errorResId != null) {
                    Text(text = stringResource(errorResId!!), color = NeonRed, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp), fontFamily = CustomFont)
                }
            } else {
                // GROUP MODE UI
                if (!isCreatingNewPreset && !isEditingPreset) {
                    if (savedPresets.isEmpty()) {
                        Text("No saved presets!", color = NeonRed, fontFamily = CustomFont)
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(
                            text = "Create Preset",
                            icon = Icons.Filled.PlayArrow,
                            color = NeonOrange,
                            textColor = colors.text,
                            borderColor = NeonOrange,
                            borderWidth = 0.dp,
                            interactionSource = remember { MutableInteractionSource() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            audio.playClick()
                            isCreatingNewPreset = true
                            tempPlayers = emptyList()
                            newPresetName = "Preset 1"
                            groupTimeText = "10"
                            resetTimeRule = false
                        }
                    } else {
                        // THE DROPDOWN SELECTOR
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("SELECT PRESET:", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                            // Hollowed out the button to match the bomb style!
                            ActionButton(
                                text = "New",
                                icon = Icons.Filled.Add,
                                color = unselectedGlass,
                                textColor = colors.text,
                                borderColor = faintOutline,
                                borderWidth = 1.dp,
                                interactionSource = remember { MutableInteractionSource() }
                            ) {
                                audio.playClick()
                                isCreatingNewPreset = true
                                tempPlayers = emptyList()
                                newPresetName = "Preset ${savedPresets.size + 1}"
                                groupTimeText = "10"
                                resetTimeRule = false
                            }
                        }

                        // --- UNIFIED ACCORDION PRESET LIST ---
                        // 1. Build the dropdown order: Selected ALWAYS on top!
                        val visuallyOrderedPresets = remember(savedPresets, selectedPresetIndex) {
                            val list = mutableListOf<Pair<Int, GroupPreset>>()
                            if (selectedPresetIndex != null && selectedPresetIndex!! in savedPresets.indices) {
                                list.add(selectedPresetIndex!! to savedPresets[selectedPresetIndex!!])
                                savedPresets.forEachIndexed { i, p -> if (i != selectedPresetIndex) list.add(i to p) }
                            } else {
                                savedPresets.forEachIndexed { i, p -> list.add(i to p) }
                            }
                            list
                        }

                        // 2. Define the Reusable Card Composable (Saves 200 lines of duplication!)
                        val renderPresetCard: @Composable (Int, GroupPreset) -> Unit = { originalIndex, preset ->
                            val isSelected = selectedPresetIndex == originalIndex
                            val rosterBg = if (isDarkMode) Slate900 else Color.White

                            val headerPressed = remember(preset.id) { mutableStateOf(false) }
                            val arrowPressed = remember(preset.id) { mutableStateOf(false) }

                            val currentIsSelected by rememberUpdatedState(isSelected)
                            val currentIsRosterExpanded by rememberUpdatedState(isRosterExpanded)
                            val currentIndex by rememberUpdatedState(originalIndex)

                            val isRowPressed = headerPressed.value || arrowPressed.value

                            val rowPressProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = if (isRowPressed) 1f else 0f,
                                animationSpec = if (isRowPressed) tween(0) else tween(150),
                                label = "rowPress"
                            )

                            val baseBgColor = if (isSelected) NeonOrange else unselectedGlass
                            val pressedBgColor = if (isSelected) darkOrangePressed else lerp(unselectedGlass, Color.Gray, touchDarkenAmount)
                            val animatedBgColor = lerp(baseBgColor, pressedBgColor, rowPressProgress)

                            val baseBorderColor = if (isSelected) NeonOrange else faintOutline
                            val pressedBorderColor = if (isSelected) darkOrangePressed else Color.Transparent
                            val animatedBorderColor = lerp(baseBorderColor, pressedBorderColor, rowPressProgress)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(animatedBgColor, RoundedCornerShape(8.dp))
                                    .border(if (isSelected) 2.dp else 1.dp, animatedBorderColor, RoundedCornerShape(8.dp))
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(preset.id) {
                                            detectTapGestures(
                                                onPress = { headerPressed.value = true; tryAwaitRelease(); headerPressed.value = false },
                                                onTap = {
                                                    // THE FIX: Smart Dropdown Toggling!
                                                    if (!currentIsSelected) {
                                                        selectedPresetIndex = currentIndex
                                                        prefs.edit { putString("last_preset_id", preset.id) }
                                                        isRosterExpanded = false
                                                        prefs.edit { putBoolean("is_roster_expanded", false) }
                                                        isOtherPresetsExpanded = false // Collapse list on pick
                                                    } else {
                                                        isOtherPresetsExpanded = !isOtherPresetsExpanded // Toggle list
                                                    }
                                                    audio.playClick()
                                                }
                                            )
                                        }
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(preset.presetName, color = if (isSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val pCount = preset.players.size
                                        Text("$pCount Player${if (pCount == 1) "" else "s"}", color = if (isSelected) colors.text else colors.textSecondary, fontSize = 12.sp, fontFamily = CustomFont)

                                        Spacer(modifier = Modifier.width(8.dp))

                                        val isThisExpanded = isSelected && isRosterExpanded
                                        Icon(
                                            imageVector = if (isThisExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Toggle Roster",
                                            tint = if (arrowPressed.value) Color.Gray else (if (isSelected) colors.text else colors.textSecondary),
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .pointerInput(preset.id) {
                                                    detectTapGestures(
                                                        onPress = { arrowPressed.value = true; tryAwaitRelease(); arrowPressed.value = false },
                                                        onTap = {
                                                            if (!currentIsSelected) {
                                                                selectedPresetIndex = currentIndex
                                                                prefs.edit { putString("last_preset_id", preset.id) }
                                                                isRosterExpanded = true
                                                                isOtherPresetsExpanded = false
                                                            } else {
                                                                isRosterExpanded = !currentIsRosterExpanded
                                                            }
                                                            prefs.edit { putBoolean("is_roster_expanded", isRosterExpanded) }
                                                            audio.playClick()
                                                        }
                                                    )
                                                }
                                                .padding(2.dp)
                                        )
                                    }
                                }

                                AnimatedVisibility(
                                    visible = isSelected && isRosterExpanded,
                                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rosterBg)
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Column {
                                                    Text("ROSTER (Uncheck if absent)", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                                        Text("${preset.defaultTime}s start time", color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)

                                                        val needsReset = preset.players.any { it.timeLeft != preset.defaultTime }
                                                        if (needsReset) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "RESET TIMES",
                                                                color = NeonOrange,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                fontFamily = CustomFont,
                                                                modifier = Modifier.clickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null
                                                                ) {
                                                                    audio.playClick()
                                                                    val resetPlayers = preset.players.map { it.copy(timeLeft = preset.defaultTime) }
                                                                    val updatedPreset = preset.copy(players = resetPlayers)
                                                                    val updatedPresetsList = savedPresets.toMutableList()
                                                                    updatedPresetsList[selectedPresetIndex!!] = updatedPreset
                                                                    savedPresets = updatedPresetsList
                                                                    groupManager.savePresets(savedPresets)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Edit, "Edit", tint = NeonCyan, modifier = Modifier.size(20.dp).clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        audio.playClick()
                                                        isEditingPreset = true
                                                        editingPresetId = preset.id
                                                        newPresetName = preset.presetName
                                                        groupTimeText = preset.defaultTime.toInt().toString()
                                                        resetTimeRule = preset.resetOnExplosion
                                                        tempPlayers = preset.players
                                                    })

                                                    Icon(
                                                        imageVector = Icons.Filled.Refresh,
                                                        contentDescription = "Toggle Auto-Reset",
                                                        tint = if (preset.resetOnExplosion) NeonOrange else colors.textSecondary,
                                                        modifier = Modifier.size(20.dp).clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            audio.playClick()
                                                            val updatedPreset = preset.copy(resetOnExplosion = !preset.resetOnExplosion)
                                                            val updatedPresetsList = savedPresets.toMutableList()
                                                            updatedPresetsList[selectedPresetIndex!!] = updatedPreset
                                                            savedPresets = updatedPresetsList
                                                            groupManager.savePresets(savedPresets)
                                                        }
                                                    )

                                                    Icon(Icons.Filled.Delete, "Delete", tint = NeonRed, modifier = Modifier.size(20.dp).clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) {
                                                        audio.playClick()
                                                        val updatedPresets = savedPresets.filter { it.id != preset.id }
                                                        savedPresets = updatedPresets
                                                        groupManager.savePresets(updatedPresets)
                                                        if (updatedPresets.isNotEmpty()) {
                                                            selectedPresetIndex = 0
                                                            prefs.edit { putString("last_preset_id", updatedPresets[0].id) }
                                                        } else {
                                                            selectedPresetIndex = null
                                                            prefs.edit { remove("last_preset_id") }
                                                        }
                                                    })
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            preset.players.forEachIndexed { playerIndex, player ->
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(end = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.weight(1f),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "${playerIndex + 1}.",
                                                                color = if (player.isAbsent) colors.textSecondary else colors.text,
                                                                fontFamily = CustomFont,
                                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                                modifier = Modifier.width(28.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = player.name,
                                                                color = if (player.isAbsent) colors.textSecondary else colors.text,
                                                                fontFamily = CustomFont,
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        Switch(
                                                            checked = !player.isAbsent,
                                                            onCheckedChange = { isPresent ->
                                                                audio.playClick()
                                                                val updatedPlayers = preset.players.toMutableList()
                                                                updatedPlayers[playerIndex] = player.copy(isAbsent = !isPresent)
                                                                val updatedPreset = preset.copy(players = updatedPlayers)
                                                                val updatedPresetsList = savedPresets.toMutableList()
                                                                updatedPresetsList[selectedPresetIndex!!] = updatedPreset
                                                                savedPresets = updatedPresetsList
                                                                groupManager.savePresets(savedPresets)
                                                            },
                                                            colors = SwitchDefaults.colors(
                                                                checkedThumbColor = NeonRed,
                                                                checkedTrackColor = if (isDarkMode) Slate800 else Color(0xFFE5E7EB),
                                                                uncheckedThumbColor = if (isDarkMode) Slate800 else Color.White,
                                                                uncheckedTrackColor = if (isDarkMode) Slate950 else Color(0xFFD1D5DB)
                                                            ),
                                                            modifier = Modifier.size(36.dp, 20.dp)
                                                        )
                                                    }

                                                    if (playerIndex < preset.players.size - 1) {
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(start = 36.dp, end = 8.dp)
                                                                .padding(top = 8.dp)
                                                                .height(1.dp)
                                                                .background(colors.border.copy(alpha = 0.5f))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. The New Z-Indexed Slide Engine
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                        ) {
                            if (visuallyOrderedPresets.isNotEmpty()) {
                                // A. The Top Card (Sits above the others)
                                Box(modifier = Modifier.zIndex(2f)) {
                                    renderPresetCard(visuallyOrderedPresets[0].first, visuallyOrderedPresets[0].second)
                                }

                                // B. The Sliding Dropdown (Sits behind the top card and slides down)
                                AnimatedVisibility(
                                    visible = isOtherPresetsExpanded,
                                    enter = expandVertically(expandFrom = Alignment.Top) +
                                            slideInVertically(initialOffsetY = { -it }) +
                                            fadeIn(),
                                    exit = shrinkVertically(shrinkTowards = Alignment.Top) +
                                            slideOutVertically(targetOffsetY = { -it }) +
                                            fadeOut(),
                                    modifier = Modifier.zIndex(1f)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        for (i in 1 until visuallyOrderedPresets.size) {
                                            renderPresetCard(visuallyOrderedPresets[i].first, visuallyOrderedPresets[i].second)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // THE UNIVERSAL EDITOR UI (Handles Create AND Edit)
                    // We can remove editorBg since it is going transparent!
                    val fieldBg = if (isDarkMode) Slate800 else Color(0xFFE5E7EB)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.dp, faintOutline, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(if (isEditingPreset) "EDIT PRESET" else "CREATE NEW PRESET", color = NeonOrange, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                        Spacer(modifier = Modifier.height(16.dp))

                        CustomTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            placeholder = "Preset Name (e.g. Group 1)",
                            color = NeonOrange,
                            colors = colors,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // THE FIX: Clears focus on Done, and swapped to NeonOrange for consistency!
                        TimeInput("Starting Time (Seconds)", groupTimeText, { groupTimeText = it }, NeonOrange, colors, Modifier.fillMaxWidth(), { focusManager.clearFocus() })
                        Spacer(modifier = Modifier.height(16.dp))

                        // Toggle Rule
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reset times on explosion?", color = colors.text, fontFamily = CustomFont)
                            Switch(
                                checked = resetTimeRule,
                                onCheckedChange = {
                                    resetTimeRule = it
                                    audio.playClick() // <-- ADDED AUDIO FEEDBACK!
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = NeonRed,
                                    checkedTrackColor = fieldBg,
                                    uncheckedThumbColor = if (isDarkMode) Slate800 else Color.White,
                                    uncheckedTrackColor = if (isDarkMode) Slate950 else Color(0xFFD1D5DB)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text("ADD/EDIT PLAYERS", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CustomTextField(
                                value = newPlayerName,
                                onValueChange = { newPlayerName = it },
                                placeholder = "Player Name",
                                color = NeonOrange,
                                colors = colors,
                                modifier = Modifier.weight(1f), // Keeps it perfectly aligned next to the (+) button!
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                                    if (newPlayerName.isNotBlank()) {
                                        val time = groupTimeText.toFloatOrNull() ?: 10f
                                        tempPlayers = tempPlayers + Player(id = java.util.UUID.randomUUID().toString(), name = newPlayerName.trim(), timeLeft = time)
                                        newPlayerName = ""
                                        audio.playClick()
                                    }
                                    focusManager.clearFocus()
                                })
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Filled.Add, null, tint = NeonOrange, modifier = Modifier.size(36.dp).clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                if (newPlayerName.isNotBlank()) {
                                    val time = groupTimeText.toFloatOrNull() ?: 10f
                                    tempPlayers = tempPlayers + Player(id = java.util.UUID.randomUUID().toString(), name = newPlayerName.trim(), timeLeft = time)
                                    newPlayerName = ""
                                    audio.playClick()
                                }
                            })
                        }

                        // Interactive Temp Player List
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tempPlayers.forEachIndexed { i, p ->
                                // THE FIX 1: Wrap in a Column to hold the Row and the Divider!
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        // THE FIX 2: Split the number and name so the periods align perfectly here too!
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${i + 1}.",
                                                color = colors.text,
                                                fontFamily = CustomFont,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                                modifier = Modifier.width(28.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = p.name,
                                                color = colors.text,
                                                fontFamily = CustomFont,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                        }

                                        // Move Up
                                        if (i > 0) {
                                            Icon(Icons.Filled.KeyboardArrowUp, null, tint = NeonCyan, modifier = Modifier.size(24.dp).clickable(
                                                interactionSource = remember { MutableInteractionSource() }, indication = null
                                            ) {
                                                audio.playClick()
                                                val list = tempPlayers.toMutableList()
                                                val temp = list[i]
                                                list[i] = list[i-1]
                                                list[i-1] = temp
                                                tempPlayers = list
                                            })
                                        } else { Spacer(modifier = Modifier.size(24.dp)) }

                                        // Move Down
                                        if (i < tempPlayers.size - 1) {
                                            Icon(Icons.Filled.KeyboardArrowDown, null, tint = NeonCyan, modifier = Modifier.size(24.dp).clickable(
                                                interactionSource = remember { MutableInteractionSource() }, indication = null
                                            ) {
                                                audio.playClick()
                                                val list = tempPlayers.toMutableList()
                                                val temp = list[i]
                                                list[i] = list[i+1]
                                                list[i+1] = temp
                                                tempPlayers = list
                                            })
                                        } else { Spacer(modifier = Modifier.size(24.dp)) }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        // Remove Player
                                        Icon(Icons.Filled.Close, null, tint = NeonRed, modifier = Modifier.size(20.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null
                                        ) {
                                            audio.playClick()
                                            tempPlayers = tempPlayers.filter { it.id != p.id }
                                        })
                                    }

                                    // THE FIX 3: Inject the perfectly centered, indented divider!
                                    if (i < tempPlayers.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 36.dp, end = 8.dp)
                                                .padding(top = 8.dp)
                                                .height(1.dp)
                                                .background(colors.border.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.weight(1f)) {
                                ActionButton(
                                    text = "Cancel",
                                    icon = Icons.Filled.Refresh,
                                    color = unselectedGlass,
                                    textColor = colors.text,
                                    borderColor = faintOutline, // <-- Updated Cancel Border
                                    borderWidth = 1.dp,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    audio.playClick()
                                    isCreatingNewPreset = false
                                    isEditingPreset = false
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                ActionButton(
                                    text = "Save",
                                    icon = Icons.Filled.PlayArrow,
                                    color = NeonOrange,
                                    textColor = colors.text,
                                    borderColor = NeonOrange,
                                    borderWidth = 0.dp,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    if (newPresetName.isNotBlank() && tempPlayers.isNotEmpty()) {
                                        val time = groupTimeText.toFloatOrNull() ?: 10f

                                        // If the time changed, we update all surviving players to the new time!
                                        val updatedPlayers = tempPlayers.map {
                                            if (!it.isEliminated) it.copy(timeLeft = time) else it
                                        }

                                        if (isEditingPreset && editingPresetId != null) {
                                            // Update existing preset
                                            val updatedPreset = GroupPreset(
                                                id = editingPresetId!!,
                                                presetName = newPresetName,
                                                players = updatedPlayers,
                                                defaultTime = time,
                                                resetOnExplosion = resetTimeRule
                                            )
                                            savedPresets = savedPresets.map { if (it.id == editingPresetId) updatedPreset else it }
                                        } else {
                                            // Save new preset
                                            val newPreset = GroupPreset(
                                                id = java.util.UUID.randomUUID().toString(),
                                                presetName = newPresetName,
                                                players = updatedPlayers,
                                                defaultTime = time,
                                                resetOnExplosion = resetTimeRule
                                            )
                                            savedPresets = savedPresets + newPreset
                                        }

                                        groupManager.savePresets(savedPresets)

                                        // --- UPDATED TARGETING & SAVING LOGIC ---
                                        val targetIndex = savedPresets.indexOfFirst { it.presetName == newPresetName }.takeIf { it >= 0 } ?: (savedPresets.size - 1)
                                        selectedPresetIndex = targetIndex
                                        prefs.edit { putString("last_preset_id", savedPresets[targetIndex].id) }

                                        isCreatingNewPreset = false
                                        isEditingPreset = false
                                        audio.playClick()
                                    }
                                }
                            }
                        }
                    }
                }
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

    val visualTimeLeftState = remember { mutableFloatStateOf(state.duration.toFloat()) }
    val timeProvider = { visualTimeLeftState.floatValue }

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
        // (Side note: we should actually use timeLeft here too so the visual
        // timer doesn't accidentally jump to the max time for a split second!)
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
                                .height(120.dp)
                                .offset(y = (-32).dp), // Visually nudges text up without altering layout height
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

                        Spacer(modifier = Modifier.height(64.dp))

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
            contentDescription = "Previous Player",
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
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = CustomFont
        )

        Spacer(modifier = Modifier.width(16.dp))

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Next Player",
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