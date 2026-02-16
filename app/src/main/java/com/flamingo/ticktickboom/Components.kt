package com.flamingo.ticktickboom

import androidx.compose.animation.animateColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.remember

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
fun DrawScope.drawReflection(
    isDarkMode: Boolean,
    pivotY: Float,
    alpha: Float = 0.25f,
    content: DrawScope.(Boolean) -> Unit
) {
    content(false) // Real Object
    if (isDarkMode && alpha > 0f) {
        withTransform({
            scale(1f, -1f, pivot = Offset(center.x, pivotY))
        }) {
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.saveLayerAlpha(
                    0f, -size.height, size.width, size.height * 2f,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseBgColor = if (isSelected) colors.surface else colors.surface.copy(alpha = 0.3f)

    // --- OPTIMIZED: Animate a float instead of the color! ---
    // This makes theme swaps instant, but keeps the press animation smooth.
    val pressBlend by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.4f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "pressBlend"
    )
    val animatedBgColor = lerp(baseBgColor, Color.Gray, pressBlend)

    val borderColor = if (isSelected) color else colors.border
    val contentColor = if (isSelected) color else colors.textSecondary

    Column(modifier = Modifier
        .weight(1f)
        .height(100.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(animatedBgColor)
        .border(1.dp, borderColor, RoundedCornerShape(16.dp))
        .clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() }
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
                Icon(if (label.contains("TIMER")) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.Warning, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
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
                StrokeGlowText("PAUSED", NeonCyan, 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonCyan), shape = RoundedCornerShape(50)) {
                    Text("EXTINGUISHED", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
            "FROG", "HEN" -> StrokeGlowText("PAUSED", NeonCyan, 48.sp)
            else -> {
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonCyan), shape = RoundedCornerShape(50), modifier = modifier) {
                    Text("SYSTEM PAUSED", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
        }
        return
    }

    when (style) {
        "FUSE" -> {
            if (!isCritical) {
                StrokeGlowText("ARMED", NeonOrange, 48.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonOrange), shape = RoundedCornerShape(50)) {
                    Text("FUSE BURNING", color = NeonOrange, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            } else {
                val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                val color by infiniteTransition.animateColor(initialValue = NeonRed, targetValue = colors.text, animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    tween(200), androidx.compose.animation.core.RepeatMode.Reverse), label = "crit")

                StrokeGlowText("CRITICAL", color, 48.sp, fontWeight = FontWeight.Black, glowIntensity = 1.3f)

                Spacer(modifier = Modifier.height(16.dp))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonRed), shape = RoundedCornerShape(50)) {
                    Text("DETONATION IMMINENT", color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), fontFamily = CustomFont)
                }
            }
        }
        "FROG" -> {
            val text = when {
                timeLeft <= 1.05f -> "RIBBIT!"
                isCritical -> "RIBBIT?"
                else -> "RIBBIT"
            }
            StrokeGlowText(text, FrogBody, 48.sp)
        }
        "HEN" -> {
            val showCracking = henSequenceElapsed > 0.5f
            val text = if (showCracking) "CRACKING" else "CLUCK"
            val color = if (showCracking) NeonRed else NeonOrange
            StrokeGlowText(text, color, 48.sp)
        }
        else -> {
            Surface(color = Color.Transparent, border = BorderStroke(1.dp, NeonRed), shape = RoundedCornerShape(50), modifier = modifier) {
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