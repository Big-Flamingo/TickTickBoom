package com.flamingo.ticktickboom

import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.os.ConfigurationCompat

// --- SHARED DRAWING HELPERS ---

@Composable
fun StrokeGlowText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    gradientBrush: Brush? = null, // <-- NEW PARAMETER
    letterSpacing: androidx.compose.ui.unit.TextUnit = 2.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    glowIntensity: Float = 1f,
    strokeWidth: Float = 8f,
    blurRadius: Float = 15f
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // LAYER 1: INVISIBLE STROKE GLOW (Background)
        Text(
            text = text,
            color = color.copy(alpha = 0.01f),
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            fontFamily = CustomFont,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                drawStyle = Stroke(width = strokeWidth * glowIntensity, join = StrokeJoin.Round),
                shadow = Shadow(color = color, blurRadius = blurRadius * glowIntensity, offset = Offset.Zero)
            )
        )

        // LAYER 2: SHARP TEXT (Foreground)
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            fontFamily = CustomFont,
            overflow = TextOverflow.Visible,
            softWrap = false,
            modifier = Modifier.wrapContentSize(unbounded = true),
            style = TextStyle(
                brush = gradientBrush // <-- APPLIES THE GRADIENT
            )
        )
    }
}

// Linear Interpolation for Colors
fun lerp(start: Color, stop: Color, fraction: Float): Color {
    // This uses the built-in optimize Compose lerp
    return androidx.compose.ui.graphics.lerp(start, stop, fraction.coerceIn(0f, 1f))
}

// Helper for Reflections (Used in all Visuals)
// 1. Update signature to accept a 'clipHeight'
fun DrawScope.drawReflection(
    isDarkMode: Boolean,
    pivotY: Float,
    alpha: Float = 0.25f,
    clipHeight: Float? = null, // <-- New Parameter
    content: DrawScope.(Boolean) -> Unit
) {
    content(false) // Real Object
    if (isDarkMode && alpha > 0f) {
        withTransform({
            scale(1f, -1f, pivot = Offset(center.x, pivotY))
        }) {
            drawIntoCanvas { canvas ->
                // 2. Use clipHeight if provided, otherwise default to full size
                val layerBottom = clipHeight ?: (size.height * 2f)

                // OPTIMIZATION: Only allocate what we actually need!
                canvas.nativeCanvas.saveLayerAlpha(
                    0f, -size.height, size.width, layerBottom,
                    (alpha * 255).toInt()
                )
            }
            content(true) // Reflection
            drawIntoCanvas { canvas -> canvas.nativeCanvas.restore() }
        }
    }
}

// --- GENERIC COMPONENTS ---

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    color: Color,
    textColor: Color,
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 1.dp,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }, // <-- NEW PARAMETER!
    onClick: () -> Unit
) {
    val isPressed by interactionSource.collectIsPressedAsState()

    // Smooth fade to a neutral gray for universal light/dark mode support
    val targetColor = if (isPressed) lerp(color, Color.Gray, 0.4f) else color
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 150), // Subtle yet quick transition
        label = "buttonHighlight"
    )

    Row(
        modifier = Modifier
            .width(200.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(50))
            .background(animatedColor)
            .border(borderWidth, borderColor, RoundedCornerShape(50))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = textColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
    }
}

@Composable
fun RowScope.StyleButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    colors: AppColors,
    onClick: () -> Unit
) {
    // 1. We use our own state instead of interactionSource to bypass the scroll delay!
    // Changed 'var' to 'val' and removed 'by'
    val isActuallyPressed = remember { mutableStateOf(false) }

    val baseBgColor = if (isSelected) colors.surface else colors.surface.copy(alpha = 0.3f)

    // 2. Animate based on our new raw touch state
    val pressBlend by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isActuallyPressed.value) 0.4f else 0f, // <-- Added .value
        animationSpec = if (isActuallyPressed.value) tween(0) else tween(150), // <-- Added .value
        label = "pressBlend"
    )
    val animatedBgColor = lerp(baseBgColor, Color.Gray, pressBlend)

    val borderColor = if (isSelected) color else colors.border
    val contentColor = if (isSelected) color else colors.textSecondary

    val selectDesc = stringResource(R.string.desc_select_style, label)

    Column(modifier = Modifier
        .weight(1f)
        .height(100.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(animatedBgColor)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        // --- NEW: Accessibility Semantics ---
        .semantics {
            role = Role.Button
            contentDescription = selectDesc
        }
        // 3. Swap .clickable for .pointerInput to catch the exact moment of touch
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isActuallyPressed.value = true  // <-- Added .value
                    tryAwaitRelease()
                    isActuallyPressed.value = false // <-- Added .value
                },
                onTap = { onClick() }         // Trigger the actual button logic
            )
        }
        .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = contentColor)
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, color = contentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
    }
}

@Composable
fun TimeInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    color: Color,
    colors: AppColors,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    Column(modifier = modifier.background(colors.surface.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).border(1.dp, colors.border, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AccessTime, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
        }
        BasicTextField(
            value = value,
            onValueChange = { if (it.all { char -> char.isDigit() }) onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(color = colors.text, fontSize = 32.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun VolumeSlider(
    label: String,
    value: Float,
    color: Color,
    colors: AppColors,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (label == stringResource(R.string.timer_volume)) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Warning, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
            }
            Text("${(value * 100).toInt()}%", color = colors.textSecondary, fontSize = 10.sp, fontFamily = CustomFont)
        }
        Slider(value = value, onValueChange = onValueChange, colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color.copy(alpha=0.7f), inactiveTrackColor = colors.border))
    }
}

// --- BOMB SPECIFIC COMPONENTS ---

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
    onShock: () -> Unit = {}, // <-- NEW CALLBACK
    eggWobbleRotation: Float = 0f,
    henSequenceElapsed: Float = 0f,
    showEgg: Boolean = true,
    crackStage: Int = 0,
    isPainedBeakOpen: Boolean = false,
    isPainedBeakClosed: Boolean = false,
    isDarkModeShadows: Boolean = false
) {
    if (style == "HEN") {
        HenVisual(
            timeLeft = timeLeft,
            isPaused = isPaused,
            onTogglePause = onTogglePause,
            eggWobbleRotation = eggWobbleRotation,
            henSequenceElapsed = henSequenceElapsed,
            showEgg = showEgg,
            crackStage = crackStage,
            isPainedBeakOpen = isPainedBeakOpen,
            isPainedBeakClosed = isPainedBeakClosed,
            isDarkMode = isDarkModeShadows,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier.size(320.dp),
            contentAlignment = Alignment.Center
        ) {
            when (style) {
                "FUSE" -> {
                    val fuseBurnDuration = (duration - 5).coerceAtLeast(0)
                    val currentBurnTime = (duration - timeLeft).coerceAtLeast(0f)
                    val progress = if (fuseBurnDuration > 0) (currentBurnTime / fuseBurnDuration).coerceIn(0f, 1f) else 1f
                    FuseVisual(progress, isCritical, colors, isPaused, onTogglePause, isDarkMode = isDarkMode)
                }
                "C4" -> C4Visual(isLedOn, isDarkMode, isPaused, onTogglePause, onShock)
                "DYNAMITE" -> DynamiteVisual(timeLeft, isPaused, onTogglePause, isDarkMode)
                "FROG" -> FrogVisual(timeLeft, isCritical, isPaused, onTogglePause, isDarkMode = isDarkMode)
            }
        }
    }
}

@Composable
fun BombTextContent(
    style: String,
    timeLeft: Float,
    isCritical: Boolean,
    isPaused: Boolean,
    colors: AppColors,
    modifier: Modifier = Modifier,
    henSequenceElapsed: Float = 0f
) {
    // NOTE: Removed local StrokeGlowText. Using top-level version now.

    if (isPaused) {
        when (style) {
            "FUSE" -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StrokeGlowText(stringResource(R.string.paused), NeonCyan, 48.sp)
                    Surface(
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, NeonCyan),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            stringResource(R.string.extinguished),
                            color = NeonCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontFamily = CustomFont
                        )
                    }
                }
            }
            "FROG", "HEN" -> StrokeGlowText(stringResource(R.string.paused), NeonCyan, 48.sp)
            else -> {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonCyan), shape = RoundedCornerShape(50), modifier = modifier) {
                    Text(stringResource(R.string.system_paused), color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
        }
        return
    }

    when (style) {
        "FUSE" -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (!isCritical) {
                    StrokeGlowText(stringResource(R.string.armed), NeonOrange, 48.sp)
                    Surface(
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, NeonOrange),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            stringResource(R.string.fuse_burning),
                            color = NeonOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontFamily = CustomFont
                        )
                    }
                } else {
                    val infiniteTransition =
                        androidx.compose.animation.core.rememberInfiniteTransition()
                    val color by infiniteTransition.animateColor(
                        initialValue = NeonRed,
                        targetValue = colors.text,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            tween(200), androidx.compose.animation.core.RepeatMode.Reverse
                        ),
                        label = "crit"
                    )

                    StrokeGlowText(
                        stringResource(R.string.critical),
                        color,
                        48.sp,
                        fontWeight = FontWeight.Black,
                        glowIntensity = 1.3f
                    )

                    Surface(
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, NeonRed),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            stringResource(R.string.detonation_imminent),
                            color = NeonRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            fontFamily = CustomFont
                        )
                    }
                }
            }
        }
        "FROG" -> {
            val text = when {
                timeLeft <= 1.05f -> stringResource(R.string.ribbit_exclaim)
                isCritical -> stringResource(R.string.ribbit_question)
                else -> stringResource(R.string.ribbit)
            }
            StrokeGlowText(text, FrogBody, 48.sp)
        }
        "HEN" -> {
            val showCracking = henSequenceElapsed > 0.5f
            val text = if (showCracking) stringResource(R.string.cracking) else stringResource(R.string.cluck)
            val color = if (showCracking) NeonRed else NeonOrange
            StrokeGlowText(text, color, 48.sp)
        }
        else -> {
            Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonRed), shape = RoundedCornerShape(50), modifier = modifier) {
                Text(stringResource(R.string.detonation_sequence), color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
            }
        }
    }
}

@Composable
fun AbortButtonContent(colors: AppColors, onAbort: () -> Unit) {
    ActionButton(
        text = stringResource(R.string.abort),
        icon = Icons.Filled.Close,
        color = Color.Transparent, // <-- Changed from colors.surface.copy(alpha=0.5f)
        textColor = colors.textSecondary,
        borderColor = colors.textSecondary,
        borderWidth = 1.dp,
        onClick = { onAbort() }
    )
}

@Composable
fun LanguageSwitch(
    colors: AppColors,
    onClick: () -> Unit
) {
    // 1. Determine current language state
    val configuration = LocalConfiguration.current
    val currentLocale = ConfigurationCompat.getLocales(configuration)[0]
    val isEnglish = currentLocale?.language == "en"

    // Constants for the animation offsets
    val selectedOffset = 0.dp
    val restingBackOffset = 12.dp
    // This is how far out it pushes before dropping behind.
    // Since chips are 28dp, 30dp ensures it completely clears the other one.
    val farOutOffset = 30.dp

    // Animation timing specs
    val animationDuration = 350 // Slightly longer total time for the complex move
    val halfDuration = animationDuration / 2

    // 2. Helper to draw a single language chip with complex animation logic
    @Composable
    fun LanguageChip(
        text: String,
        amISelected: Boolean, // Renamed for clarity inside the effect
        modifier: Modifier = Modifier
    ) {
        // We need manual control over the offset value
        val offsetAnimatable = remember { Animatable(if (amISelected) selectedOffset else restingBackOffset, Dp.VectorConverter) }
        // We also need manual control over the zIndex to flip it mid-animation
        var zIndex by remember { mutableFloatStateOf(if (amISelected) 1f else 0f) }

        // NEW: Remember if this is the first time the chip is being drawn
        var isInitialRender by remember { mutableStateOf(true) }

        // The complex animation sequence
        LaunchedEffect(amISelected) {
            // NEW: Skip the animation if we just got to the screen!
            if (isInitialRender) {
                isInitialRender = false
                return@LaunchedEffect
            }

            if (amISelected) {
                // CASE 1: Becoming Selected (Moving to Front-Left)
                // Incoming chip goes to Layer 1
                zIndex = 1f
                offsetAnimatable.animateTo(
                    targetValue = selectedOffset,
                    animationSpec = tween(animationDuration, easing = FastOutSlowInEasing)
                )
            } else {
                // CASE 2: Becoming Unselected (The "Pop-Out" move)
                // Fix: Push the outgoing chip to Layer 2 ("Super Foreground")
                // so it always beats the incoming chip!
                zIndex = 2f

                // Stage 1: Slide far out diagonally (down-right)
                offsetAnimatable.animateTo(
                    targetValue = farOutOffset,
                    animationSpec = tween(halfDuration, easing = LinearOutSlowInEasing)
                )

                // Stage 2: MID-POINT - Drop to the background layer (Layer 0)
                zIndex = 0f

                // Stage 3: Slide back in to the resting position (down-right)
                offsetAnimatable.animateTo(
                    targetValue = restingBackOffset,
                    animationSpec = tween(halfDuration, easing = FastOutSlowInEasing)
                )
            }
        }

        // Color animation stays simple
        val targetColor = if (amISelected) NeonRed else Color.Gray
        val animatedColor by animateColorAsState(
            targetValue = targetColor,
            animationSpec = tween(animationDuration),
            label = "color"
        )

        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                // Use the current value of our manual animatable
                .offset(x = offsetAnimatable.value, y = offsetAnimatable.value)
                // Use our manually managed zIndex
                .zIndex(zIndex)
                .size(28.dp)
                .border(1.5.dp, animatedColor, RoundedCornerShape(8.dp))
                // Crucial: Opaque background so the "pop-out" actually hides things
                .background(colors.background, RoundedCornerShape(8.dp))
        ) {
            Text(
                text = text,
                color = animatedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = CustomFont
            )
        }
    }

    // 3. Container Box
    Box(
        modifier = Modifier
            // Increased slightly from 42dp to accommodate the "far out" push without clipping
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        // English Chip
        LanguageChip(
            text = "En",
            amISelected = isEnglish
        )

        // Chinese Chip
        LanguageChip(
            text = "中",
            amISelected = !isEnglish
        )
    }
}