package com.flamingo.ticktickboom

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache // <--- New optimization import
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.launch
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent

// --- FONTS ---
val VisualsFont = FontFamily(Font(R.font.orbitron_bold))

// NOTE: VisualParticle and VisualText are now in AppModels.kt
// NOTE: drawReflection, lerp, and StrokeGlowText are now in Components.kt

// --- VISUALS ---

@Composable
fun FuseVisual(progress: Float, isCritical: Boolean, colors: AppColors, isPaused: Boolean, onTogglePause: () -> Unit, isDarkMode: Boolean) {
    // Now using the shared VisualParticle class from AppModels.kt
    val sparks = remember { mutableListOf<VisualParticle>() }
    val smokePuffs = remember { mutableListOf<VisualParticle>() }

    var frame by remember { mutableLongStateOf(0L) }
    var lastFrameTime = remember { 0L }

    val fusePath = remember { Path() }
    val frontRimPath = remember { Path() }
    val pathMeasure = remember { android.graphics.PathMeasure() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }

    val infiniteTransition = rememberInfiniteTransition("glint")
    val glintScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glint"
    )

    val showBloom = isCritical || progress > 0.95f
    val criticalAlpha by animateFloatAsState(
        targetValue = if (showBloom) 1f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "criticalBloom"
    )

    val density = LocalDensity.current
    val d = density.density

    // Measurements
    val protrusionW = 40f * d
    val protrusionH = 24f * d
    val cylinderOvalH = 14f * d
    val fuseInnerOffset = 3f * d
    val holeW = 12f * d
    val holeH = holeW * (cylinderOvalH / protrusionW)
    val strokeW = 6f * d

    // Particles/Glint configs...
    val glintSizeL = 24f * d; val glintSizeS = 4f * d; val glintOffsetL = 12f * d; val glintOffsetS = 2f * d
    val glowRadius = 25f * d; val coreRadius = 8f * d; val whiteRadius = 4f * d
    val particleRad = 3f * d; val particleRadS = 1.5f * d; val tapThreshold = 60f * d; val fuseYOffset = 5f * d

    LaunchedEffect(isPaused) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else (nanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = nanos
                for (i in sparks.indices.reversed()) { val s = sparks[i]; s.life -= dt; s.x += s.vx * dt * 100; s.y += s.vy * dt * 100 + (9.8f * dt * dt * 50); if (s.life <= 0) sparks.removeAt(i) }
                for (i in smokePuffs.indices.reversed()) { val p = smokePuffs[i]; p.life -= dt; if (p.life <= 0) smokePuffs.removeAt(i) else { p.x += p.vx * dt * 50; p.y += p.vy * dt * 50 } }
                frame++
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        var currentSparkCenter by remember { mutableStateOf(Offset.Zero) }

        Canvas(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { tapOffset -> val dx = tapOffset.x - currentSparkCenter.x; val dy = tapOffset.y - currentSparkCenter.y; if (sqrt(dx * dx + dy * dy) < tapThreshold) onTogglePause() } }
        ) {
            if (frame >= 0) Unit
            val width = size.width
            val height = size.height
            val bombCenterX = width / 2
            val bombCenterY = height * 0.6f
            val bodyRadius = width * 0.35f

            val halfNeckW = protrusionW / 2
            val verticalDrop = sqrt(bodyRadius.pow(2) - halfNeckW.pow(2))
            val neckBaseY = bombCenterY - verticalDrop
            val neckTopY = neckBaseY - protrusionH
            val floorY = bombCenterY + bodyRadius

            if (size != cachedSize) {
                fusePath.reset()
                fusePath.moveTo(bombCenterX, neckTopY + fuseInnerOffset)
                fusePath.lineTo(bombCenterX, neckTopY - 20f * d)
                fusePath.cubicTo(bombCenterX, neckTopY - 70f * d, bombCenterX + 70f * d, neckTopY - 70f * d, bombCenterX + 80f * d, neckTopY + 10f * d)
                cachedSize = size
            }

            drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->
                if (!isDarkMode && !isReflection) {
                    val shadowW = bodyRadius * 2f
                    val shadowH = shadowW * 0.2f
                    drawOval(color = Color.Black.copy(alpha = 0.2f), topLeft = Offset(bombCenterX - shadowW / 2, floorY - shadowH / 2), size = Size(shadowW, shadowH))
                }

                drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF475569), Color(0xFF0F172A)), center = Offset(bombCenterX - 20, bombCenterY - 20), radius = bodyRadius), radius = bodyRadius, center = Offset(bombCenterX, bombCenterY))

                pathMeasure.setPath(fusePath.asAndroidPath(), false)
                val totalLength = pathMeasure.length
                val visibleLength = totalLength - fuseInnerOffset
                val effectiveProgress = if (isCritical) 1f else progress
                val currentBurnPoint = fuseInnerOffset + (visibleLength * (1f - effectiveProgress))

                val sparkPos = floatArrayOf(0f, 0f)
                pathMeasure.getPosTan(currentBurnPoint, sparkPos, null)
                val realSparkX = sparkPos[0]
                val realSparkY = sparkPos[1]

                val straightLen = 23f * d
                val curveLen = (totalLength - straightLen).coerceAtLeast(1f)
                val distOnCurve = (currentBurnPoint - straightLen).coerceAtLeast(0f)
                val curveProgress = distOnCurve / curveLen
                val symmetry = (1f - curveProgress).coerceIn(0f, 1f)

                val fadeThreshold = 15f * d
                val distToHole = (currentBurnPoint - fuseInnerOffset).coerceAtLeast(0f)
                val lightIntensity = if (!isCritical && !isPaused) {
                    (distToHole / fadeThreshold).coerceIn(0f, 1f)
                } else {
                    0f
                }

                if (lightIntensity > 0f) {
                    val dx = realSparkX - bombCenterX
                    val dy = realSparkY - bombCenterY
                    val angleRad = atan2(dy, dx)
                    val orbitRadius = bodyRadius * 0.9f
                    val lightX = bombCenterX + cos(angleRad) * orbitRadius
                    val lightY = bombCenterY + sin(angleRad) * orbitRadius
                    val radialGlowRadius = bodyRadius * (0.8f + (0.2f * symmetry))
                    val radialAlpha = (0.5f + (0.2f * symmetry)) * lightIntensity
                    drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFFF9800).copy(alpha = radialAlpha), Color(0xFFFF5722).copy(alpha = radialAlpha * 0.5f), Color.Transparent), center = Offset(lightX, lightY), radius = radialGlowRadius), radius = bodyRadius, center = Offset(bombCenterX, bombCenterY))
                }

                val specularCenter = Offset(bombCenterX - bodyRadius * 0.4f, bombCenterY - bodyRadius * 0.4f)
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = specularCenter, radius = bodyRadius * 0.3f), radius = bodyRadius * 0.3f, center = specularCenter)

                val baseDark = Color(0xFF0F172A)
                val baseLight = Color(0xFF475569)
                val rimDark = Color(0xFF1E293B)
                val rimLight = Color(0xFF64748B)
                val orangeDark = Color(0xFFD97706)
                val orangeLight = Color(0xFFFFB74D)

                val rimLeft = lerp(rimDark, orangeDark, symmetry * lightIntensity)
                val rimCenter = lerp(rimLight, orangeLight, symmetry * lightIntensity)
                val rimRight = lerp(rimDark, orangeDark, lightIntensity)
                val rimCenterOffset = 0.7f - (0.2f * symmetry)

                val rectLeft = bombCenterX - protrusionW / 2
                val rectRight = bombCenterX + protrusionW / 2

                val metalGradient = Brush.horizontalGradient(0.0f to baseDark, 0.5f to baseLight, 1.0f to baseDark, startX = rectLeft, endX = rectRight)
                val neckRightColor = lerp(baseDark, orangeDark, lightIntensity)
                val litGradient = Brush.horizontalGradient(0.0f to baseDark, 0.5f to baseLight, 1.0f to neckRightColor, startX = rectLeft, endX = rectRight)

                val outerRimRect = Rect(offset = Offset(bombCenterX - protrusionW / 2, neckTopY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                val innerHoleRect = Rect(center = outerRimRect.center, radius = holeW / 2).copy(top = outerRimRect.center.y - holeH / 2, bottom = outerRimRect.center.y + holeH / 2)

                drawOval(brush = metalGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckBaseY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                drawRect(brush = metalGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckTopY), size = Size(protrusionW, neckBaseY - neckTopY))

                val triggerDist = 40f * d
                val distToEdge = (realSparkX - (bombCenterX + protrusionW / 2))
                val curtainProgress = if (isPaused) 1f else (1f - (distToEdge / triggerDist)).coerceIn(0f, 1f)

                if (curtainProgress < 1f) {
                    val fadeWidth = 20f * d
                    val maxSweep = protrusionW + fadeWidth
                    val currentSweep = maxSweep * curtainProgress

                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(outerRimRect.left, neckTopY, outerRimRect.right, neckBaseY + 15f * d), Paint())
                        drawOval(brush = litGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckBaseY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                        drawRect(brush = litGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckTopY), size = Size(protrusionW, neckBaseY - neckTopY))

                        if (curtainProgress > 0f) {
                            val eraserRight = outerRimRect.left + currentSweep
                            val slidingEraserBrush = Brush.horizontalGradient(0.0f to Color.Black, 1.0f to Color.Transparent, startX = eraserRight - fadeWidth, endX = eraserRight)
                            val drawHeight = (neckBaseY - neckTopY) + 20f * d
                            drawRect(brush = slidingEraserBrush, topLeft = Offset(outerRimRect.left, neckTopY), size = Size(currentSweep, drawHeight), blendMode = BlendMode.DstOut)
                        }
                        canvas.restore()
                    }
                }

                val rimGradient = Brush.horizontalGradient(0.0f to rimLeft, rimCenterOffset to rimCenter, 1.0f to rimRight, startX = outerRimRect.left, endX = outerRimRect.right)
                drawOval(brush = rimGradient, topLeft = outerRimRect.topLeft, size = outerRimRect.size)

                val heatThreshold = 60f * d
                val heatFactor = when {
                    isPaused -> 0f
                    isCritical -> 1f
                    else -> {
                        val dist = (currentBurnPoint - fuseInnerOffset).coerceAtLeast(0f)
                        (1f - (dist / heatThreshold)).coerceIn(0f, 1f)
                    }
                }
                val holeDark = Color(0xFF0F172A)
                val holeHot = Color(0xFFFFD700)
                val currentHoleColor = lerp(holeDark, holeHot, heatFactor)
                drawOval(color = currentHoleColor, topLeft = innerHoleRect.topLeft, size = innerHoleRect.size)

                if (!isCritical) {
                    val androidSegmentPath = android.graphics.Path()
                    pathMeasure.getSegment(0f, currentBurnPoint, androidSegmentPath, true)
                    val composePath = androidSegmentPath.asComposePath()
                    drawPath(path = composePath, color = Color(0xFFCCC9C6), style = Stroke(width = strokeW, cap = StrokeCap.Round))
                    drawPath(path = composePath, color = Color(0xFFD6D3D1), style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round))
                    drawPath(path = composePath, color = Color.White.copy(alpha = 0.5f), style = Stroke(width = strokeW * 0.4f, cap = StrokeCap.Round))
                }

                frontRimPath.reset(); frontRimPath.arcTo(outerRimRect, 0f, 180f, false); frontRimPath.lineTo(innerHoleRect.left, innerHoleRect.center.y); frontRimPath.arcTo(innerHoleRect, 180f, -180f, false); frontRimPath.close()
                drawPath(path = frontRimPath, brush = rimGradient)

                if (criticalAlpha > 0f && !isPaused) {
                    val fuseBase = innerHoleRect.center
                    drawOval(brush = Brush.radialGradient(colors = listOf(Color(0xFFFFFFE0).copy(alpha = criticalAlpha), Color(0xFFFFD700).copy(alpha = criticalAlpha)), center = fuseBase, radius = holeW), topLeft = innerHoleRect.topLeft, size = innerHoleRect.size)
                    val bloomRect = innerHoleRect.inflate(20f * d)
                    val bloomAspectRatio = bloomRect.height / bloomRect.width
                    withTransform({ scale(1f, bloomAspectRatio, pivot = fuseBase) }) {
                        val drawRadius = bloomRect.width / 2f
                        drawCircle(brush = Brush.radialGradient(colors = listOf(NeonRed.copy(alpha = 0.6f * criticalAlpha), NeonRed.copy(alpha = 0f)), center = fuseBase, radius = drawRadius, tileMode = TileMode.Clamp), radius = drawRadius, center = fuseBase)
                    }
                }

                val pos = floatArrayOf(0f, 0f); pathMeasure.getPosTan(currentBurnPoint, pos, null); val sparkCenter = Offset(pos[0], pos[1]); if (!isReflection) currentSparkCenter = sparkCenter
                if (!isPaused && !isReflection) {
                    if (Math.random() < 0.3) { val angle = Math.random() * Math.PI * 2; val speed = (2f + Math.random() * 4f).toFloat(); val sx = if (isCritical) innerHoleRect.center.x else sparkCenter.x; val sy = if (isCritical) innerHoleRect.center.y else sparkCenter.y; sparks.add(VisualParticle(x = sx, y = sy, vx = cos(angle).toFloat() * speed, vy = (sin(angle) * speed - 2f).toFloat(), life = (0.2f + Math.random() * 0.3f).toFloat(), maxLife = 0.5f)) }
                    if (Math.random() < 0.2) { val angle = -Math.PI / 2 + (Math.random() - 0.5) * 0.5; val speed = (1f + Math.random() * 2f).toFloat(); val sx = if (isCritical) innerHoleRect.center.x else sparkCenter.x; val sy = if (isCritical) innerHoleRect.center.y else sparkCenter.y; val smokeVy = if (isCritical) sin(angle).toFloat() * speed * 2f else sin(angle).toFloat() * speed; smokePuffs.add(VisualParticle(x = sx, y = sy - fuseYOffset, vx = (cos(angle) * speed).toFloat(), vy = smokeVy, life = (1f + Math.random().toFloat() * 0.5f), maxLife = 1.5f)) }
                }
                smokePuffs.forEach { puff -> val p = 1f - (puff.life / puff.maxLife); val size = 10f + p * 20f; val alpha = (1f - p).coerceIn(0f, 0.6f); drawCircle(color = colors.smokeColor.copy(alpha = alpha), radius = size, center = Offset(puff.x, puff.y)) }
                sparks.forEach { spark -> val alpha = (spark.life / spark.maxLife).coerceIn(0f, 1f); drawCircle(color = NeonOrange.copy(alpha = alpha), radius = particleRad * alpha, center = Offset(spark.x, spark.y)); drawCircle(color = Color.Yellow.copy(alpha = alpha), radius = particleRadS * alpha, center = Offset(spark.x, spark.y)) }
                if (!isCritical && !isPaused) {
                    drawCircle(brush = Brush.radialGradient(colors = listOf(NeonOrange.copy(alpha = 0.5f * lightIntensity), Color.Transparent), center = sparkCenter, radius = glowRadius), radius = glowRadius, center = sparkCenter)
                    drawCircle(color = NeonOrange.copy(alpha=0.8f * lightIntensity), radius = coreRadius, center = sparkCenter); drawCircle(color = Color.White.copy(alpha=lightIntensity), radius = whiteRadius, center = sparkCenter)
                    withTransform({ rotate(45f, pivot = sparkCenter); scale(glintScale, glintScale, pivot = sparkCenter) }) { drawOval(color = Color.White.copy(alpha=0.8f * lightIntensity), topLeft = Offset(sparkCenter.x - glintOffsetL, sparkCenter.y - glintOffsetS), size = Size(glintSizeL, glintSizeS)) }
                    withTransform({ rotate(-45f, pivot = sparkCenter); scale(glintScale, glintScale, pivot = sparkCenter) }) { drawOval(color = Color.White.copy(alpha=0.8f * lightIntensity), topLeft = Offset(sparkCenter.x - glintOffsetL, sparkCenter.y - glintOffsetS), size = Size(glintSizeL, glintSizeS)) }
                }
            }
        }
    }
}

@Composable
fun C4Visual(
    isLedOn: Boolean,
    isDarkMode: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onShock: () -> Unit = {} // <-- NEW CALLBACK
) {
    val density = LocalDensity.current
    val d = density.density

    val ledSize = 12f * d
    val ledRadius = 6f * d
    val c4BodyColor = if (isDarkMode) Color(0xFFD6D0C4) else Color(0xFFC8C2B4)
    val c4BlockColor = if (isDarkMode) Color(0xFFC7C1B3) else Color(0xFFB9B3A5)
    val c4BorderColor = if (isDarkMode) Color(0xFF9E9889) else Color(0xFF8C8677)

    val pausedColor = Color(0xFF3B82F6)

    // --- NEW: High Voltage Shock State ---
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val zapAnim = remember { Animatable(0f) }

    // --- NEW: Electric Spark Physics ---
    val sparks = remember { mutableListOf<VisualParticle>() }
    var sparkFrame by remember { mutableLongStateOf(0L) }
    var sparkTrigger by remember { mutableIntStateOf(0) } // <-- NEW STATE

    // The engine now wakes up every time the trigger number changes!
    LaunchedEffect(sparkTrigger) {
        var lastSparkTime = 0L
        while (sparks.isNotEmpty()) {
            withFrameNanos { nanos ->
                val dt = if (lastSparkTime == 0L) 0.016f else ((nanos - lastSparkTime) / 1_000_000_000f).coerceAtMost(0.1f)
                lastSparkTime = nanos
                for (i in sparks.indices.reversed()) {
                    val s = sparks[i]
                    s.life -= dt

                    // --- NEW: Drag / Air Resistance ---
                    // Rapidly bleed off velocity (decelerates fast)
                    s.vx -= s.vx * 8f * dt
                    s.vy -= s.vy * 8f * dt

                    // --- UPDATED: Stronger Gravity ---
                    // Pulls them down after the drag slows their initial burst
                    s.vy += 15f * dt

                    // Apply final velocity to position
                    s.x += s.vx * dt * 250
                    s.y += s.vy * dt * 250

                    if (s.life <= 0) sparks.removeAt(i)
                }
                sparkFrame++ // Forces the canvas to redraw
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // A sharp, centered drop shadow simulating an overhead light
        Box(
            modifier = Modifier
                .width(320.dp)
                .height(200.dp)
                .drawBehind {
                    val insetX = 12.dp.toPx()
                    val offsetVal = 12.dp.toPx()
                    val shadowColor = Color.Black.copy(alpha = 0.25f)

                    // 1. THE NORMAL SHADOW (Bottom) - Only in Light Mode
                    if (!isDarkMode) {
                        drawRoundRect(
                            color = shadowColor.copy(alpha = shadowColor.alpha * (1f - zapAnim.value)),
                            topLeft = Offset(insetX, offsetVal),
                            size = Size(size.width - (insetX * 2), size.height),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // 2. THE ZAP SHADOW (Top) - Unified 0.4f Alpha
                    // Snaps in at 40% opacity and fades out with the zap animation
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.4f * zapAnim.value),
                        topLeft = Offset(insetX, -offsetVal),
                        size = Size(size.width - (insetX * 2), size.height),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }
                .background(c4BodyColor, RoundedCornerShape(4.dp))
                .drawWithContent {
                    drawContent()
                    if (zapAnim.value > 0f) {
                        drawRect(
                            color = NeonOrange.copy(alpha = 0.3f * zapAnim.value),
                            size = size, // Now strictly limited to the 320x200 box
                            blendMode = BlendMode.SrcAtop
                        )
                    }
                }
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { Box(Modifier.width(80.dp).fillMaxHeight().background(c4BlockColor).border(1.dp, c4BorderColor)) }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black))
                Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black))
            }

            // A sharp, centered drop shadow for the LCD screen
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(280.dp)
                    .height(140.dp)
                    .drawBehind {
                        val insetX = 8.dp.toPx()
                        val offsetVal = 8.dp.toPx()
                        val shadowColor = Color.Black.copy(alpha = 0.25f)

                        // 1. THE NORMAL SHADOW (Bottom) - Only in Light Mode
                        if (!isDarkMode) {
                            drawRoundRect(
                                color = shadowColor.copy(alpha = shadowColor.alpha * (1f - zapAnim.value)),
                                topLeft = Offset(insetX, offsetVal),
                                size = Size(size.width - (insetX * 2), size.height),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                        }

                        // 2. THE ZAP SHADOW (Top) - Unified 0.4f Alpha
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.4f * zapAnim.value),
                            topLeft = Offset(insetX, -offsetVal),
                            size = Size(size.width - (insetX * 2), size.height),
                            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                        )
                    }
                    .background(C4ScreenBg, RoundedCornerShape(8.dp))
                    .border(4.dp, Slate800, RoundedCornerShape(8.dp))
                    .drawWithContent {
                        drawContent()
                        if (zapAnim.value > 0f) {
                            // Brighten the screen with a light orange wash
                            drawRect(
                                color = Color.Yellow.copy(alpha = 0.2f * zapAnim.value),
                                size = size,
                                blendMode = BlendMode.Overlay
                            )
                        }
                    }
            ) {
                // OPTIMIZATION: Use drawWithCache instead of looping on every frame in a standard Canvas
                // The grid is static, so we can prepare the path once.
                Spacer(modifier = Modifier.fillMaxSize().alpha(0.2f).drawWithCache {
                    val step = 20f * d
                    val gridPath = Path()
                    // Vertical lines
                    for (x in 0..size.width.toInt() step step.toInt()) {
                        gridPath.moveTo(x.toFloat(), 0f)
                        gridPath.lineTo(x.toFloat(), size.height)
                    }
                    // Horizontal lines
                    for (y in 0..size.height.toInt() step step.toInt()) {
                        gridPath.moveTo(0f, y.toFloat())
                        gridPath.lineTo(size.width, y.toFloat())
                    }
                    onDrawBehind {
                        drawPath(gridPath, color = NeonCyan, style = Stroke(width = 2f))
                    }
                })

                Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(0.6f).fillMaxWidth().padding(horizontal = 16.dp).background(LcdDarkBackground, RoundedCornerShape(4.dp)).border(2.dp, Slate800, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onTogglePause() }.padding(8.dp)) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (isPaused) {
                                        Canvas(modifier = Modifier.size(32.dp)) {
                                            drawIntoCanvas { canvas ->
                                                val blurRadius = 15f * d
                                                val paint = android.graphics.Paint().apply {
                                                    color = pausedColor.toArgb()
                                                    maskFilter = BlurMaskFilter(blurRadius, Blur.NORMAL)
                                                }
                                                val inset = 4.dp.toPx()
                                                val rect = android.graphics.RectF(inset, inset, size.width - inset, size.height - inset)
                                                canvas.nativeCanvas.drawRoundRect(rect, 8.dp.toPx(), 8.dp.toPx(), paint)
                                            }
                                        }
                                    }
                                    Icon(Icons.Rounded.DeveloperBoard, null, tint = if (isPaused) pausedColor else Color(0xFF64748B), modifier = Modifier.size(32.dp))
                                }
                                // INVISIBLE ANCHOR: Perfectly matches StrokeGlowText properties to prevent sub-pixel jumping
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "PAUSED",
                                        color = Color.Transparent,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        fontFamily = CustomFont, // Matches Glow component
                                        overflow = TextOverflow.Visible,
                                        softWrap = false
                                    )

                                    if (isPaused) {
                                        StrokeGlowText("PAUSED", pausedColor, 8.sp, letterSpacing = 2.sp)
                                    } else {
                                        Text(
                                            text = "PAUSED",
                                            color = Color(0xFF1E293B),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp,
                                            fontFamily = CustomFont, // Matches Glow component
                                            overflow = TextOverflow.Visible,
                                            softWrap = false,
                                            modifier = Modifier.wrapContentSize(unbounded = true) // Matches Glow layout behavior
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 6.dp).offset(y = (-5).dp)) {
                                StrokeGlowText("--:--", NeonRed, 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Black, strokeWidth = 12f, blurRadius = 40f)
                            }
                        }
                    }

                    Box(Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = 5.dp)) {
                            Canvas(modifier = Modifier.size(12.dp).offset(x = 3.5.dp, y = 0.dp)) {
                                if (isLedOn) drawCircle(brush = Brush.radialGradient(colors = listOf(NeonRed.copy(alpha=0.8f), Color.Transparent), center = center, radius = ledSize), radius = ledSize)
                                drawCircle(color = if (isLedOn) NeonRed else Color(0xFF450a0a), radius = ledRadius)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // USING SHARED GLOW COMPONENT
                            if (isLedOn) {
                                StrokeGlowText("ARMED", NeonRed, 12.sp, blurRadius = 20f)
                            } else {
                                Text("ARMED", color = Color(0xFF450a0a), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = VisualsFont)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        val warningBase = Color(0xFFF59E0B)
        val warningBg = lerp(Color(0x33F59E0B), Color.White.copy(alpha = 0.9f), zapAnim.value)
        val warningBorder = lerp(warningBase.copy(alpha=0.5f), Color.Black, zapAnim.value) // <-- CHANGED TO BLACK!
        val warningTextIcon = lerp(warningBase, Color.Black, zapAnim.value)

        Box(contentAlignment = Alignment.Center) {
            Surface(
                color = warningBg,
                border = BorderStroke(1.dp, warningBorder),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { tapOffset ->
                            coroutineScope.launch {
                                AudioService.playZap()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onShock() // <-- TELL THE SCREEN TO INVERT!
                                zapAnim.snapTo(1f)
                                zapAnim.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
                            }

                            // SPAWN 20 ELECTRIC SPARKS
                            repeat(20) {
                                val angle = Math.random() * Math.PI * 2
                                // --- UPDATED: Much higher initial velocity ---
                                val speed = (4f + Math.random() * 6f).toFloat()
                                sparks.add(
                                    VisualParticle(
                                        x = tapOffset.x,
                                        y = tapOffset.y,
                                        vx = (cos(angle) * speed).toFloat(),
                                        vy = (sin(angle) * speed).toFloat(),
                                        life = (0.2f + Math.random() * 0.4f).toFloat(),
                                        maxLife = 0.6f
                                    )
                                )
                            }
                            sparkTrigger++
                        }
                    )
                }
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = warningTextIcon, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HIGH VOLTAGE // DO NOT TAMPER", color = warningTextIcon, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
                }
            }

            // --- NEW OPTIMIZATION: Cache a single base brush! ---
            // Centered at 0,0 with a fixed radius of 100f.
            // We use 100% opacity colors here and fade them during the actual draw call.
            val baseGlowBrush = remember {
                Brush.radialGradient(
                    0.0f to Color.Yellow,
                    0.3f to NeonRed,
                    0.6f to NeonOrange.copy(alpha = 0.6f),
                    1.0f to Color.Transparent,
                    center = Offset.Zero,
                    radius = 100f
                )
            }

            // THE OVERLAY CANVAS: Highly Optimized Fiery Sparks
            Canvas(modifier = Modifier.matchParentSize()) {
                if (sparkFrame >= 0) {
                    sparks.forEach { s ->
                        val alpha = (s.life / s.maxLife).coerceIn(0f, 1f)
                        val coreRadius = 3.dp.toPx() * alpha
                        val glowRadius = coreRadius * 4f

                        if (glowRadius > 0f) {
                            // Slide the canvas to the spark, and scale it to the exact size we need
                            withTransform({
                                translate(s.x, s.y)
                                // Tell it to scale perfectly outward from the spark's own center
                                scale(glowRadius / 100f, glowRadius / 100f, pivot = Offset.Zero)
                            }) {
                                // Draw using the CACHED brush, applying the fading alpha globally!
                                drawCircle(
                                    brush = baseGlowBrush,
                                    radius = 100f,
                                    center = Offset.Zero,
                                    alpha = alpha
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DynamiteVisual(
    timeLeft: Float,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    isDarkMode: Boolean = false // <-- NEW PARAMETER
) {
    val density = LocalDensity.current
    val d = density.density

    // RAW PIXEL MATH
    val stickW = 60f * d
    val stickH = 220f * d
    val spacing = 5f * d
    val cornerRad = 4f * d
    val tapeH = 30f * d
    val tapeOver = 20f * d
    val clockRad = 80f * d
    val clockStroke = 8f * d
    val tickL = 12f * d
    val tickS = 6f * d
    val tickWidthLong = 3f * d
    val tickWidthShort = 1f * d
    val tickGap = 10f * d
    val handL = 50f * d
    val handS = 40f * d
    val pinL = 4f * d
    val pinS = 2f * d
    val textYOffset = 25f * d
    val stickTextYOffset = 4f * d
    val tickOffsetY = -140f * d
    val dingOffsetY = -170f * d

    // --- NEW: Bell Vibration Animation ---
    val infiniteTransition = rememberInfiniteTransition()
    val bellVibrate by infiniteTransition.animateFloat(
        initialValue = -15f, // <-- INCREASE THIS (make it more negative)
        targetValue = 15f,   // <-- INCREASE THIS (make it more positive)
        animationSpec = infiniteRepeatable(
            animation = tween(40, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BellVibration"
    )

    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = textMeasurer.measure(
        text = "ACME CORP",
        style = TextStyle(color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
    )
    val stickTextResult = textMeasurer.measure(
        text = "HIGH EXPLOSIVE",
        style = TextStyle(color = Color.Black.copy(alpha=0.3f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = VisualsFont)
    )

    @Suppress("RemoveExplicitTypeArguments", "UNCHECKED_CAST")
    val textEffectsSaver = listSaver<SnapshotStateList<VisualText>, Any>(
        save = { stateList ->
            stateList.map {
                listOf<Any>(
                    it.text,
                    it.x,
                    it.y,
                    it.color.toArgb(),
                    it.gradientColors?.map { c -> c.toArgb() } ?: emptyList<Int>(),
                    it.alpha,
                    it.life,
                    it.fontSize
                )
            }
        },
        restore = { savedList ->
            val mutableList = mutableStateListOf<VisualText>()
            savedList.forEach { item ->
                val p = item as List<Any>
                val g = p[4] as List<Int>
                mutableList.add(VisualText(
                    text = p[0] as String,
                    x = (p[1] as? Number)?.toFloat() ?: 0f,
                    y = (p[2] as? Number)?.toFloat() ?: 0f,
                    color = Color(p[3] as Int),
                    gradientColors = if (g.isEmpty()) null else g.map { Color(it) },
                    alpha = (p[5] as? Number)?.toFloat() ?: 1f,
                    life = (p[6] as? Number)?.toFloat() ?: 0f,
                    fontSize = (p[7] as? Number)?.toFloat() ?: 0f
                ))
            }
            mutableList
        }
    )
    val textEffects = rememberSaveable(saver = textEffectsSaver) { mutableStateListOf() }
    var lastTriggerStep by rememberSaveable { mutableIntStateOf(-1) }
    var hasShownDing by rememberSaveable { mutableStateOf(false) }

    // --- NEW: Bell Decay Animation ---
    val vibrationMagnitude = remember { Animatable(1f) }
    LaunchedEffect(hasShownDing) {
        if (hasShownDing) {
            // Decays the multiplier from 1f to 0f over 2 seconds!
            vibrationMagnitude.animateTo(0f, tween(2000, easing = LinearOutSlowInEasing))
        }
    }

    LaunchedEffect(timeLeft) {
        val currentStep = ceil(timeLeft * 2).toInt()
        val isFast = timeLeft <= 5f
        if (currentStep != lastTriggerStep) {
            if ((isFast || currentStep % 2 == 0) && timeLeft > 1.1f && !isPaused) {
                textEffects.add(VisualText("TICK", (Math.random() * 100 - 50).toFloat(), tickOffsetY, if (isFast) NeonRed else TextGray, fontSize = 20f))
            }
            lastTriggerStep = currentStep
        }
        if (timeLeft <= 1.0f && !hasShownDing && timeLeft > 0 && !isPaused) {
            textEffects.add(VisualText("DING!", 0f, dingOffsetY, Color(0xFFFFD700), listOf(Color(0xFFFFFACD), Color(0xFFFFD700)), life = 2.0f, fontSize = 48f))
            hasShownDing = true
        }
    }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while(true) {
            withFrameNanos { nanos ->
                val dt = if (lastTime == 0L) 0.016f else (nanos - lastTime) / 1_000_000_000f
                lastTime = nanos
                for (i in textEffects.indices.reversed()) {
                    val effect = textEffects[i]
                    val newLife = effect.life - dt
                    if (newLife <= 0) textEffects.removeAt(i) else textEffects[i] = effect.copy(y = effect.y - (15f * dt), life = newLife, alpha = (newLife / 1.0f).coerceIn(0f, 1f))
                }
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalSticksWidth = 3 * stickW + 2 * spacing
            val startX = (size.width - totalSticksWidth) / 2
            val startY = (size.height - stickH) / 2

            val stickBrush = Brush.horizontalGradient(colors = listOf(Color(0xFF7F1D1D), Color(0xFFEF4444), Color(0xFF7F1D1D)))

            // --- OPTIMIZATION: Helper lambda to draw any stick ---
            val drawStick = { index: Int ->
                val stickLeft = startX + index * (stickW + spacing)

                // Draw Stick Shadow
                if (!isDarkMode) {
                    val offsetY = 8f * d
                    val right = stickLeft + stickW
                    val top = startY + offsetY
                    val bottom = startY + stickH + 30f * d + offsetY
                    val curveOffset = 12f * d

                    val stickShadowPath = Path().apply {
                        moveTo(stickLeft, top)
                        lineTo(stickLeft, bottom)
                        quadraticTo(
                            x1 = stickLeft + (stickW / 2f),
                            y1 = bottom + curveOffset,
                            x2 = right,
                            y2 = bottom
                        )
                        lineTo(right, top)
                        close()
                    }
                    drawPath(path = stickShadowPath, color = Color.Black.copy(alpha = 0.25f))
                }

                // Draw Red Stick Body
                drawRoundRect(brush = stickBrush, topLeft = Offset(stickLeft, startY), size = Size(stickW, stickH + 30f * d), cornerRadius = CornerRadius(cornerRad, cornerRad))

                // Draw Sideways Text
                val stickCenterX = stickLeft + stickW / 2
                val stickCenterY = startY + (stickH / 2)
                withTransform({
                    rotate(-90f, pivot = Offset(stickCenterX, stickCenterY))
                    translate(left = stickCenterX - stickTextResult.size.width / 2, top = stickCenterY - stickTextResult.size.height / 2 + stickTextYOffset)
                }) {
                    drawText(textLayoutResult = stickTextResult)
                }
            }

            // --- 1. DRAW CENTER STICK (Background Layer) ---
            drawStick(1)

            // --- 2. DRAW WIRES (Middle Layer) ---
            val clockCx = size.width / 2f
            val clockCy = size.height / 2f + 15f * d

            val leftStickX = startX + stickW / 2f
            val midStickX = startX + stickW + spacing + stickW / 2f
            val rightStickX = startX + 2 * (stickW + spacing) + stickW / 2f

            // --- UPDATED: Meet exactly at the top of the center stick ---
            val meetY = startY
            // --- UPDATED: Slightly tighter arc to match the lowered meet point ---
            val apexY = startY - 18f * d

            // --- NEW: The true mathematical peak of the Bézier curve ---
            val trueCurvePeakY = startY - 13.5f * d

            // Yellow Wire (Center)
            val yellowWirePath = Path().apply {
                moveTo(midStickX, clockCy)
                lineTo(midStickX, trueCurvePeakY) // <-- UPDATED: Snaps perfectly to the curve's height
            }

            // Red Wire (Left Stick)
            val redWirePath = Path().apply {
                moveTo(leftStickX, startY + 20f * d)
                cubicTo(
                    x1 = leftStickX, y1 = apexY,
                    x2 = clockCx - 8f * d, y2 = apexY,
                    x3 = clockCx - 4f * d, y3 = meetY
                )
                lineTo(clockCx - 4f * d, clockCy)
            }

            // Blue Wire (Right Stick)
            val blueWirePath = Path().apply {
                moveTo(rightStickX, startY + 20f * d)
                cubicTo(
                    x1 = rightStickX, y1 = apexY,
                    x2 = clockCx + 8f * d, y2 = apexY,
                    x3 = clockCx + 4f * d, y3 = meetY
                )
                lineTo(clockCx + 4f * d, clockCy)
            }

            // Draw Wire Shadows
            if (!isDarkMode) {
                // --- UPDATED: 0f left offset to cast the shadow straight down! ---
                translate(left = 0f, top = 8f * d) {
                    val shadowStroke = Stroke(width = 5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val shadowColor = Color.Black.copy(alpha = 0.35f)
                    drawPath(path = yellowWirePath, color = shadowColor, style = shadowStroke)
                    drawPath(path = redWirePath, color = shadowColor, style = shadowStroke)
                    drawPath(path = blueWirePath, color = shadowColor, style = shadowStroke)
                }
            }

            // Draw Colored Plastic
            val wireStroke = Stroke(width = 5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawPath(path = yellowWirePath, color = Color(0xFFEAB308), style = wireStroke)
            drawPath(path = redWirePath, color = Color(0xFFEF4444), style = wireStroke)
            drawPath(path = blueWirePath, color = Color(0xFF3B82F6), style = wireStroke)

            // Draw Glossy Highlights
            translate(left = -1f * d, top = -1f * d) {
                val glossStroke = Stroke(width = 1.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glossColor = Color.White.copy(alpha = 0.4f)
                drawPath(path = yellowWirePath, color = glossColor, style = glossStroke)
                drawPath(path = redWirePath, color = glossColor, style = glossStroke)
                drawPath(path = blueWirePath, color = glossColor, style = glossStroke)
            }

            // --- 3. DRAW OUTER STICKS (Foreground Layer) ---
            // These draw over the red/blue wire tips, hiding the flat connection points!
            drawStick(0)
            drawStick(2)

            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + 40f), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + stickH - 40f), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
        }
        // Clean Box with no clipping!
        Box(modifier = Modifier.offset(y = 15.dp).size(160.dp)) {

            // Your exact Canvas drawing code
            Canvas(modifier = Modifier.fillMaxSize()) {
                val clockCenter = center

                // Hard-edged centered shadow for the clock
                if (!isDarkMode) {
                    val insetRadius = 6f * d
                    val offsetY = 20f * d
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.25f),
                        center = Offset(clockCenter.x, clockCenter.y + offsetY),
                        radius = clockRad - insetRadius
                    )
                }

                // --- NEW: ALARM BELL ---
                val isRinging = hasShownDing
                // --- UPDATED: Multiply the vibration by the decaying magnitude! ---
                val currentVibrate = if (isRinging) bellVibrate * vibrationMagnitude.value else 0f

                val drawBell = { angle: Float ->
                    val bellDistance = clockRad + 4f * d
                    val bellRadius = 28f * d

                    withTransform({
                        translate(clockCenter.x, clockCenter.y)
                        rotate(angle, pivot = Offset.Zero)
                        translate(bellDistance, 0f)
                        rotate(currentVibrate, pivot = Offset.Zero)
                    }) {
                        if (!isDarkMode) {
                            drawArc(
                                color = Color.Black.copy(alpha = 0.35f),
                                startAngle = -90f,
                                sweepAngle = 180f,
                                useCenter = true,
                                topLeft = Offset(-bellRadius - 8f * d, -bellRadius),
                                size = Size(bellRadius * 2, bellRadius * 2)
                            )
                        }

                        // --- UPDATED: Draw the knob FIRST so it sits behind the bell body! ---
                        val knobBrush = Brush.linearGradient(
                            colors = listOf(Color(0xFFEAB308), Color(0xFF78350F)), // Gold -> Dark Bronze
                            // Swap these so the top (+X) is Gold, and the bottom is Dark Bronze
                            start = Offset(bellRadius + 6f * d, 0f),
                            end = Offset(bellRadius - 2f * d, 0f)
                        )
                        drawCircle(
                            brush = knobBrush,
                            radius = 4f * d,
                            center = Offset(bellRadius + 1f * d, 0f)
                        )

                        // Brass Bell Body
                        val bellBrush = Brush.linearGradient(
                            colors = listOf(Color.White, Color(0xFFEAB308), Color(0xFF78350F)),
                            start = Offset(bellRadius, 0f),
                            end = Offset(0f, 0f)
                        )

                        drawArc(
                            brush = bellBrush,
                            startAngle = -90f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(-bellRadius, -bellRadius),
                            size = Size(bellRadius * 2, bellRadius * 2)
                        )
                    }
                }
                // Draw a single bell exactly at 12 o'clock (270 degrees)
                drawBell(270f)

                // --- NEW: MECHANICAL HAMMER ---
                // Map the bell's vibration directly into a swinging pendulum angle!
                // bellVibrate oscillates from -15 to +15. We normalize that to a 1f -> 0f multiplier.
                val hammerRestAngle = 305f // Just past 1 o'clock to give it room to swing
                val hammerHitAngle = 286f  // Swings inward to smack the edge of the bell
                val normalizedSwing = (bellVibrate - 15f) / -30f // 1f when hitting, 0f when resting

                // --- FIXED: Only apply the swing if the bell is actively ringing! ---
                val activeSwing = if (isRinging) normalizedSwing * vibrationMagnitude.value else 0f
                val currentHammerAngle = hammerRestAngle + ((hammerHitAngle - hammerRestAngle) * activeSwing)

                val shaftStart = clockRad - 15f * d
                // --- UPDATED: Shorter shaft so it extends outward less ---
                val shaftLength = 22f * d
                // --- UPDATED: Thinner shaft ---
                val shaftWidth = 4.5f * d
                val shaftEnd = shaftStart + shaftLength

                // --- UPDATED: Smaller, more proportionate hammer head ---
                val headW = 9f * d
                val headH = 16f * d

                // 1. Hammer Shadow (Global drop down)
                // We translate the canvas DOWN before rotating so the shadow casts perfectly straight!
                if (!isDarkMode) {
                    withTransform({
                        translate(clockCenter.x, clockCenter.y + 8f * d)
                        rotate(currentHammerAngle, pivot = Offset.Zero)
                    }) {
                        drawRect(color = Color.Black.copy(alpha = 0.35f), topLeft = Offset(shaftStart, -shaftWidth / 2f), size = Size(shaftLength, shaftWidth))
                        drawRoundRect(color = Color.Black.copy(alpha = 0.35f), topLeft = Offset(shaftEnd, -headH / 2f), size = Size(headW, headH), cornerRadius = CornerRadius(4f * d, 4f * d))
                    }
                }

                // 2. Hammer Body
                withTransform({
                    translate(clockCenter.x, clockCenter.y)
                    rotate(currentHammerAngle, pivot = Offset.Zero)
                }) {
                    // Shaft: Lighter in the middle, darker at the sides (Shading across the local Y axis)
                    val shaftBrush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF4B5563), Color(0xFFE5E7EB), Color(0xFF4B5563)),
                        startY = -shaftWidth / 2f,
                        endY = shaftWidth / 2f
                    )
                    drawRect(brush = shaftBrush, topLeft = Offset(shaftStart, -shaftWidth / 2f), size = Size(shaftLength, shaftWidth))

                    // Head: Metallic Gray, lighter at the top, darker at the bottom (Shading down the local Y axis)
                    val headBrush = Brush.linearGradient(
                        colors = listOf(Color.White, Color(0xFF9CA3AF), Color(0xFF374151)),
                        start = Offset(shaftEnd, -headH / 2f),
                        end = Offset(shaftEnd, headH / 2f)
                    )
                    drawRoundRect(brush = headBrush, topLeft = Offset(shaftEnd, -headH / 2f), size = Size(headW, headH), cornerRadius = CornerRadius(4f * d, 4f * d))
                }

                // The actual metal clock base
                drawCircle(brush = Brush.radialGradient(colors = listOf(MetallicLight, MetallicDark), center = clockCenter, radius = clockRad), center = clockCenter, radius = clockRad)
                drawCircle(color = Slate800, style = Stroke(width = clockStroke), center = clockCenter)

                drawText(textLayoutResult = textLayoutResult, topLeft = Offset(clockCenter.x - textLayoutResult.size.width / 2, clockCenter.y + textYOffset))

                for (index in 0 until 60) {
                    val isHour = index % 5 == 0
                    rotate(index * 6f, pivot = clockCenter) { drawLine(color = Slate800, start = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap), end = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap + if(isHour) tickL else tickS), strokeWidth = if(isHour) tickWidthLong else tickWidthShort) }
                }
                rotate(-((timeLeft / 60) * 6f), pivot = clockCenter) { drawLine(color = Slate800, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handS), strokeWidth = 8f, cap = StrokeCap.Round) }
                rotate(-((timeLeft % 60) * 6f), pivot = clockCenter) { drawLine(color = NeonRed, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handL), strokeWidth = 4f, cap = StrokeCap.Round) }
                drawCircle(color = Slate800, radius = pinL, center = clockCenter)
                drawCircle(color = NeonRed, radius = pinS, center = clockCenter)

                val glareCenter = Offset(clockCenter.x - clockRad * 0.35f, clockCenter.y - clockRad * 0.35f)
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFFE0F7FA).copy(alpha = 0.3f), Color.Transparent), center = glareCenter, radius = clockRad * 0.8f), radius = clockRad * 0.7f, center = glareCenter)

                // --- PERFECTED: True Hard-Edged Crescent Shadow ---
                if (!isDarkMode) {
                    val innerRimRadius = clockRad - (clockStroke / 2f) + 0.5f

                    // 1. The full inner circle of the clock face
                    val facePath = Path().apply {
                        addOval(Rect(center = clockCenter, radius = innerRimRadius))
                    }

                    // 2. The "Light" cutout circle, shifted down
                    val shadowThickness = 12f * d
                    val lightPath = Path().apply {
                        addOval(Rect(center = Offset(clockCenter.x, clockCenter.y + shadowThickness), radius = innerRimRadius))
                    }

                    // 3. Subtract the light from the face to get a mathematically perfect crescent!
                    val crescentPath = Path()
                    crescentPath.op(facePath, lightPath, PathOperation.Difference)

                    drawPath(path = crescentPath, color = Color.Black.copy(alpha = 0.25f))
                }

                // --- NEW: Sharp "Glass Cut" Reflection ---
                val glarePath = Path().apply {
                    val rect = Rect(center = clockCenter, radius = clockRad * 0.88f)
                    // Sweep a chunk of the top-left rim
                    arcTo(rect, startAngleDegrees = 185f, sweepAngleDegrees = 95f, forceMoveTo = true)

                    val startX = clockCenter.x + clockRad * 0.88f * cos(Math.toRadians(185.0)).toFloat()
                    val startY = clockCenter.y + clockRad * 0.88f * sin(Math.toRadians(185.0)).toFloat()

                    // Draw a sharp, swooping curve back across the glass
                    quadraticTo(
                        x1 = clockCenter.x - (clockRad * 0.1f),
                        y1 = clockCenter.y - (clockRad * 0.1f),
                        x2 = startX,
                        y2 = startY
                    )
                    close()
                }

                // A crisp, semi-transparent whitewash
                drawPath(path = glarePath, color = Color.White.copy(alpha = 0.12f))
            }

            // --- NEW: The Invisible Click Layer ---
            // Sits precisely on top of the clock to handle the circular ripple
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .clickable { onTogglePause() }
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            textEffects.forEach { effect ->
                val style = if (effect.gradientColors != null) {
                    TextStyle(brush = Brush.verticalGradient(effect.gradientColors.map { it.copy(alpha = effect.alpha) }), fontSize = effect.fontSize.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
                } else {
                    TextStyle(color = effect.color.copy(alpha = effect.alpha), fontSize = effect.fontSize.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
                }
                val textResult = textMeasurer.measure(effect.text, style)
                drawText(textLayoutResult = textResult, topLeft = Offset(cx + effect.x - (textResult.size.width / 2), cy + effect.y - (textResult.size.height / 2)))
            }
        }
    }
}