package com.flamingo.ticktickboom

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Timer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var presetToDelete by remember { mutableStateOf<GroupPreset?>(null) }
    // --- UPDATED: Remembers the last expanded state ---
    var isRosterExpanded by remember { mutableStateOf(prefs.getBoolean("is_roster_expanded", false)) }
    var isOtherPresetsExpanded by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var newPlayerName by remember { mutableStateOf("") }
    var tempPlayers by remember { mutableStateOf(emptyList<Player>()) }
    var resetTimeRule by remember { mutableStateOf(false) }

    // --- NEW: In-line Editing State ---
    var editingPlayerId by remember { mutableStateOf<String?>(null) }
    var editingPlayerName by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }

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
                        // --- THE FIX: Give the hit-box a strict name ---
                        .semantics { contentDescription = "Random Tab" }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { randomPressed.value = true; tryAwaitRelease(); randomPressed.value = false },
                                onTap = { selectedTabIndex = 0; prefs.edit { putInt("last_tab", 0) }; audio.playClick() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.tab_random_mode), color = if (isRandomSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Normal, fontFamily = CustomFont) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(groupAnimatedBg, RoundedCornerShape(8.dp))
                        .border(if (isGroupSelected) 2.dp else 1.dp, groupAnimatedBorder, RoundedCornerShape(8.dp)) // Independent border!
                        .clip(RoundedCornerShape(8.dp))
                        // --- THE FIX: Give the hit-box a strict name ---
                        .semantics { contentDescription = "Group Tab" }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { groupPressed.value = true; tryAwaitRelease(); groupPressed.value = false },
                                onTap = { selectedTabIndex = 1; prefs.edit { putInt("last_tab", 1) }; audio.playClick() }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text(stringResource(R.string.tab_group_mode), color = if (isGroupSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Normal, fontFamily = CustomFont) }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // THE STYLE BUTTONS (Shared across both modes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StyleButton(stringResource(R.string.style_digital), Icons.Rounded.DeveloperBoard, style == "C4", NeonCyan, colors) { saveStyle("C4") }
                StyleButton(stringResource(R.string.style_fuse), Icons.Rounded.LocalFireDepartment, style == "FUSE", NeonOrange, colors) { saveStyle("FUSE") }
                StyleButton(stringResource(R.string.style_dynamite), Icons.Outlined.Timer, style == "DYNAMITE", NeonRed, colors) { saveStyle("DYNAMITE") }
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
                    val firstPresetName = stringResource(R.string.default_preset_name, 1)
                    val nextPresetName = stringResource(R.string.default_preset_name, savedPresets.size + 1)
                    if (savedPresets.isEmpty()) {
                        Text(stringResource(R.string.no_saved_presets), color = NeonRed, fontFamily = CustomFont)
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(
                            text = stringResource(R.string.btn_create_preset),
                            icon = Icons.Filled.Add,
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
                            newPresetName = firstPresetName
                            groupTimeText = "10"
                            resetTimeRule = false
                        }
                    } else {
                        // THE DROPDOWN SELECTOR
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // Calculate the dynamic title
                            val presetCount = savedPresets.size
                            val presetTitle = if (presetCount == 1) {
                                stringResource(R.string.label_single_preset)
                            } else {
                                stringResource(R.string.label_multiple_presets, presetCount)
                            }

                            // Display the dynamic text
                            Text(
                                text = presetTitle,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = CustomFont
                            )
                            // Hollowed out the button to match the bomb style!
                            ActionButton(
                                text = stringResource(R.string.btn_new),
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
                                newPresetName = nextPresetName
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
                                    Text(preset.presetName, color = if (isSelected) colors.text else colors.textSecondary, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val pCount = preset.players.size
                                        val countRes = if (pCount == 1) R.string.player_count_single else R.string.player_count_multiple
                                        Text(stringResource(countRes, pCount), color = if (isSelected) colors.text else colors.textSecondary, fontSize = 12.sp, fontFamily = CustomFont)

                                        Spacer(modifier = Modifier.width(8.dp))

                                        val isThisExpanded = isSelected && isRosterExpanded
                                        Icon(
                                            imageVector = if (isThisExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                            contentDescription = stringResource(R.string.desc_toggle_roster),
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
                                                    Text(stringResource(R.string.label_roster_uncheck), color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                                        Text(stringResource(R.string.label_start_time, preset.defaultTime.toString()), color = NeonCyan, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)

                                                        val needsReset = preset.players.any { it.timeLeft != preset.defaultTime }
                                                        if (needsReset) {
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = stringResource(R.string.btn_reset_times),
                                                                color = NeonOrange,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Normal,
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

                                                    val quickToggleScale by androidx.compose.animation.core.animateFloatAsState(
                                                        targetValue = if (preset.resetOnExplosion) 1.2f else 1.0f,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                                                        label = "quick_toggle_scale"
                                                    )

                                                    Icon(
                                                        // --- THE FIX: Use 'painter =' and rememberVectorPainter! ---
                                                        painter = if (preset.resetOnExplosion) {
                                                            androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Filled.History)
                                                        } else {
                                                            androidx.compose.ui.res.painterResource(id = R.drawable.ic_pace)
                                                        },
                                                        contentDescription = stringResource(R.string.desc_toggle_auto_reset),
                                                        tint = if (preset.resetOnExplosion) NeonOrange else colors.text,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .graphicsLayer {
                                                                scaleX = quickToggleScale
                                                                scaleY = quickToggleScale
                                                            }
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null
                                                            ) {
                                                                // ... (Keep your click logic exactly the same!)
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
                                                        // --- THE FIX: Trigger the dialog instead of instantly deleting! ---
                                                        presetToDelete = preset
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
                                                                maxLines = 1,
                                                                softWrap = false,
                                                                modifier = Modifier.width(with(density) { 40.sp.toDp() })
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
                                Box(modifier = Modifier.zIndex(2f).fillMaxWidth()) {

                                    // --- THE "NO-JUMP" DUMMY CARD ---
                                    val showDummy = visuallyOrderedPresets.size > 1 && !isOtherPresetsExpanded
                                    val dummyAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (showDummy) 1f else 0f,
                                        animationSpec = if (showDummy) {
                                            tween(durationMillis = 150, delayMillis = 150, easing = LinearEasing)
                                        } else {
                                            tween(0)
                                        },
                                        label = "dummy_alpha"
                                    )

                                    if (dummyAlpha > 0f) {
                                        Box(
                                            modifier = Modifier
                                                // 1. THE MAGIC: This forces the dummy to perfectly match the main card's size
                                                // without taking up any actual layout space of its own!
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    alpha = dummyAlpha
                                                    // 2. THE MAGIC: translationY moves the pixels on the GPU,
                                                    // leaving the physical layout bounds completely undisturbed.
                                                    translationY = 8.dp.toPx()
                                                }
                                                .background(unselectedGlass, RoundedCornerShape(8.dp))
                                                .border(1.dp, faintOutline, RoundedCornerShape(8.dp))
                                        )
                                    }

                                    // The Actual Active Preset Card (This strictly controls the layout height)
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
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally // Centers the text!
                                    ) {
                                        if (visuallyOrderedPresets.size > 1) {
                                            // Draw the other presets if we have them
                                            for (i in 1 until visuallyOrderedPresets.size) {
                                                renderPresetCard(visuallyOrderedPresets[i].first, visuallyOrderedPresets[i].second)
                                            }
                                        } else {
                                            // --- NEW: The "No other presets" warning ---
                                            Text(
                                                text = stringResource(R.string.no_other_presets),
                                                color = NeonRed,
                                                fontSize = 14.sp,
                                                fontFamily = CustomFont,
                                                modifier = Modifier.padding(vertical = 12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (presetToDelete != null) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { presetToDelete = null },
                                containerColor = if (isDarkMode) Slate900 else Color.White,
                                titleContentColor = NeonRed,
                                textContentColor = colors.text,
                                title = {
                                    Text(stringResource(R.string.title_delete_preset), fontFamily = CustomFont, fontWeight = FontWeight.Bold)
                                },
                                text = {
                                    Text(stringResource(R.string.desc_delete_preset, presetToDelete?.presetName ?: ""), fontFamily = CustomFont)
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            audio.playClick()
                                            val updatedPresets = savedPresets.filter { it.id != presetToDelete?.id }
                                            savedPresets = updatedPresets
                                            groupManager.savePresets(updatedPresets)

                                            if (updatedPresets.isNotEmpty()) {
                                                selectedPresetIndex = 0
                                                prefs.edit { putString("last_preset_id", updatedPresets[0].id) }
                                            } else {
                                                selectedPresetIndex = null
                                                prefs.edit { remove("last_preset_id") }
                                            }

                                            isRosterExpanded = false
                                            presetToDelete = null // Close dialog
                                        }
                                    ) {
                                        Text(stringResource(R.string.btn_delete), color = NeonRed, fontFamily = CustomFont, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            audio.playClick()
                                            presetToDelete = null // Close dialog
                                        }
                                    ) {
                                        Text(stringResource(R.string.btn_cancel), color = colors.textSecondary, fontFamily = CustomFont)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    // THE UNIVERSAL EDITOR UI (Handles Create AND Edit)
                    // We can remove editorBg since it is going transparent!

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Transparent, RoundedCornerShape(8.dp))
                            .border(1.dp, faintOutline, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(stringResource(if (isEditingPreset) R.string.title_edit_preset else R.string.title_create_new_preset), color = NeonOrange, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                        Spacer(modifier = Modifier.height(16.dp))

                        CustomTextField(
                            value = newPresetName,
                            onValueChange = { newPresetName = it },
                            placeholder = stringResource(R.string.hint_preset_name),
                            color = NeonOrange,
                            colors = colors,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // THE FIX: Clears focus on Done, and swapped to NeonOrange for consistency!
                        TimeInput(stringResource(R.string.label_starting_time), groupTimeText, { groupTimeText = it }, NeonOrange, colors, Modifier.fillMaxWidth(), { focusManager.clearFocus() })
                        Spacer(modifier = Modifier.height(16.dp))

                        // --- NEW: Mirrored Toggle Rule Layout ---
                        Text(stringResource(R.string.label_on_explosion), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                        Spacer(modifier = Modifier.height(8.dp))

                        // 1. Declare the Bouncy Springs
                        val keepScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (!resetTimeRule) 1.3f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "keep_scale"
                        )
                        val resetScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (resetTimeRule) 1.3f else 1.0f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "reset_scale"
                        )

                        // 2. Apply them to the Icons
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_pace), null, tint = if(!resetTimeRule) colors.text else colors.textSecondary, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = keepScale; scaleY = keepScale })
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.rule_keep_times), color = if(!resetTimeRule) colors.text else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = resetTimeRule,
                                onCheckedChange = { resetTimeRule = it; audio.playClick() },
                                colors = SwitchDefaults.colors(checkedThumbColor = NeonOrange, checkedTrackColor = Color.Transparent, checkedBorderColor = NeonOrange, uncheckedThumbColor = colors.text, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = colors.text)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(stringResource(R.string.rule_reset_times), color = if(resetTimeRule) NeonOrange else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.History, null, tint = if(resetTimeRule) NeonOrange else colors.textSecondary, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = resetScale; scaleY = resetScale })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // THE FIX: Added a sleek INVERT ORDER button!
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.title_add_edit_players), color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)

                            if (tempPlayers.size > 1) {
                                // 1. Create a state to track the spins!
                                var invertSpins by remember { mutableIntStateOf(0) }
                                val rotation by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = invertSpins * 180f,
                                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                                    label = "invert_spin"
                                )

                                // --- NEW: Animatable for the text beat ---
                                val textScale = remember { Animatable(1f) }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() }, indication = null
                                    ) {
                                        audio.playClick()
                                        tempPlayers = tempPlayers.reversed()
                                        invertSpins++

                                        // --- NEW: Trigger the inverted beat animation! ---
                                        scope.launch {
                                            textScale.snapTo(1f) // Reset in case of rapid clicks

                                            // 1. Shrink down to 80% size quickly
                                            textScale.animateTo(0.8f, tween(100))

                                            // 2. Spring back out to 100% size
                                            textScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                        }
                                    }
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_invert_order),
                                        color = NeonCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = CustomFont,
                                        // --- NEW: Apply the scale to the text ---
                                        modifier = Modifier.graphicsLayer {
                                            scaleX = textScale.value
                                            scaleY = textScale.value
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Filled.SwapVert, null, tint = NeonCyan, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotation })
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CustomTextField(
                                value = newPlayerName,
                                onValueChange = { newPlayerName = it },
                                placeholder = stringResource(R.string.hint_player_name),
                                color = NeonOrange,
                                colors = colors,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
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
                            Icon(Icons.Filled.PersonAdd, null, tint = NeonOrange, modifier = Modifier.size(36.dp).clickable(
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
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier.width(with(density) { 40.sp.toDp() })
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Spacer(modifier = Modifier.width(8.dp))

                                            // --- THE IN-LINE EDITING SWAP ---
                                            if (editingPlayerId == p.id) {
                                                // 1. The Active Text Box
                                                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }

                                                // Auto-pop the keyboard when tapped!
                                                LaunchedEffect(Unit) { focusRequester.requestFocus() }

                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = editingPlayerName,
                                                    onValueChange = { editingPlayerName = it },
                                                    textStyle = androidx.compose.ui.text.TextStyle(
                                                        color = NeonOrange,
                                                        fontFamily = CustomFont,
                                                        fontSize = 16.sp // Matches your standard font size
                                                    ),
                                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.text),
                                                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                                    ),
                                                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                                        onDone = {
                                                            if (editingPlayerName.text.isNotBlank()) {
                                                                tempPlayers = tempPlayers.map {
                                                                    if (it.id == p.id) it.copy(name = editingPlayerName.text.trim()) else it
                                                                }
                                                            }
                                                            editingPlayerId = null // Close the editor
                                                            focusManager.clearFocus() // Hide keyboard
                                                            audio.playClick()
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .focusRequester(focusRequester)
                                                )
                                            } else {
                                                // 2. The Normal Clickable Text
                                                Text(
                                                    text = p.name,
                                                    color = colors.text,
                                                    fontFamily = CustomFont,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null // Keeps the click invisible so it doesn't flash
                                                        ) {
                                                            audio.playClick()
                                                            editingPlayerId = p.id
                                                            editingPlayerName = androidx.compose.ui.text.input.TextFieldValue(
                                                                text = p.name,
                                                                selection = androidx.compose.ui.text.TextRange(p.name.length)
                                                            )
                                                        }
                                                )
                                            }
                                            // --- END SWAP ---
                                        }

                                        // NEW: SET AS STARTING PLAYER (Rotates the circle)
                                        if (i > 0) {
                                            Icon(Icons.Filled.Star, "Set as First", tint = NeonOrange, modifier = Modifier.size(24.dp).clickable(
                                                interactionSource = remember { MutableInteractionSource() }, indication = null
                                            ) {
                                                audio.playClick()
                                                // Drops everything before this player, and tacks it onto the end!
                                                tempPlayers = tempPlayers.drop(i) + tempPlayers.take(i)
                                            })
                                        } else { Spacer(modifier = Modifier.size(24.dp)) }

                                        Spacer(modifier = Modifier.width(4.dp))

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
                                    text = stringResource(R.string.btn_cancel),
                                    icon = Icons.Filled.Close,
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
                                    text = stringResource(R.string.btn_save),
                                    icon = Icons.Filled.Check,
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

            // 1. The Timer Volume Slider
            VolumeSlider(
                label = stringResource(R.string.timer_volume),
                iconPainter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_share_eta), // <-- Pass Share ETA SVG
                value = timerVol,
                color = NeonCyan,
                colors = colors
            ) { saveTimerVol(it) }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. The Explosion Volume Slider
            VolumeSlider(
                label = stringResource(R.string.explosion_volume),
                iconPainter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_explosion), // <-- Pass Explosion SVG
                value = explodeVol,
                color = NeonRed,
                colors = colors
            ) { saveExplodeVol(it) }

            Spacer(modifier = Modifier.height(24.dp))

            // 1. Declare the Bouncy Springs
            val lightScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (!isDarkMode) 1.3f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "light_scale"
            )
            val darkScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isDarkMode) 1.3f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "dark_scale"
            )

            // 2. Apply them to the Icons
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Filled.LightMode, null, tint = if(!isDarkMode) NeonOrange else colors.textSecondary, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = lightScale; scaleY = lightScale })
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.light_mode), color = if(!isDarkMode) NeonOrange else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)

                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { onToggleTheme() },
                    colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = Color.Transparent, checkedBorderColor = NeonCyan, uncheckedThumbColor = NeonOrange, uncheckedTrackColor = Color.Transparent, uncheckedBorderColor = NeonOrange)
                )
                Spacer(modifier = Modifier.width(16.dp))

                Text(stringResource(R.string.dark_mode), color = if(isDarkMode) NeonCyan else colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Normal, fontFamily = CustomFont)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.DarkMode, null, tint = if(isDarkMode) NeonCyan else colors.textSecondary, modifier = Modifier.size(20.dp).graphicsLayer { scaleX = darkScale; scaleY = darkScale })
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
                Icon(Icons.Filled.PlayArrow, null, tint = colors.text)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.arm_system), color = colors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontFamily = CustomFont)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Spacer(modifier = Modifier.navigationBarsPadding())
        }
    }
}