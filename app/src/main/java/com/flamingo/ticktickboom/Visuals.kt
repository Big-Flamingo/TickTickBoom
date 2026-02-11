package com.flamingo.ticktickboom

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// --- FONTS ---
val VisualsFont = FontFamily(Font(R.font.orbitron_bold))

// --- PRIVATE DATA CLASSES ---
private data class LocalVisualParticle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float
)

private data class LocalVisualText(
    val text: String, val x: Float, val y: Float,
    val color: Color, val gradientColors: List<Color>? = null,
    val alpha: Float = 1f, val life: Float = 1.0f, val fontSize: Float
)

// --- HELPER FOR REFLECTIONS ---
fun DrawScope.drawReflection(
    isDarkMode: Boolean,
    pivotY: Float,
    alpha: Float = 0.25f,
    content: DrawScope.(Boolean) -> Unit
) {
    // 1. Draw Real Object
    content(false)

    // 2. Draw Reflection (Dark Mode Only)
    if (isDarkMode && alpha > 0f) {
        withTransform({
            scale(1f, -1f, pivot = Offset(center.x, pivotY))
        }) {
            drawIntoCanvas { canvas ->
                // FIX: Define bounds from NEGATIVE height to POSITIVE height * 2.
                // This ensures the layer captures drawing regardless of which way Y is flipped.
                canvas.nativeCanvas.saveLayerAlpha(
                    0f, -size.height, size.width, size.height * 2f,
                    (alpha * 255).toInt()
                )
            }
            content(true)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.restore()
            }
        }
    }
}

// --- VISUALS ---

@Composable
fun FuseVisual(progress: Float, isCritical: Boolean, colors: AppColors, isPaused: Boolean, onTogglePause: () -> Unit, isDarkMode: Boolean) {
    val sparks = remember { mutableListOf<LocalVisualParticle>() }
    val smokePuffs = remember { mutableListOf<LocalVisualParticle>() }

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

    // Start fading in the bloom when the fuse is > 90% burnt ("Pre-Critical"),
    // OR if the system explicitly flags 'isCritical'.
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
                    // --- MODIFIED: CONSISTENT 5:1 RATIO ---
                    val shadowW = bodyRadius * 2f
                    val shadowH = shadowW * 0.2f // Height is 20% of width

                    drawOval(color = Color.Black.copy(alpha = 0.2f), topLeft = Offset(bombCenterX - shadowW / 2, floorY - shadowH / 2), size = Size(shadowW, shadowH))
                }

                // --- BOMB BODY (Base) ---
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF475569), Color(0xFF0F172A)), center = Offset(bombCenterX - 20, bombCenterY - 20), radius = bodyRadius), radius = bodyRadius, center = Offset(bombCenterX, bombCenterY))

                // === 1. LIGHTING MATH ===
                pathMeasure.setPath(fusePath.asAndroidPath(), false)
                val totalLength = pathMeasure.length
                val visibleLength = totalLength - fuseInnerOffset
                val effectiveProgress = if (isCritical) 1f else progress
                val currentBurnPoint = fuseInnerOffset + (visibleLength * (1f - effectiveProgress))

                // A. Calculate Physical Spark Position
                val sparkPos = floatArrayOf(0f, 0f)
                pathMeasure.getPosTan(currentBurnPoint, sparkPos, null)
                val realSparkX = sparkPos[0]
                val realSparkY = sparkPos[1]

                // B. GEOMETRY (Symmetry)
                val straightLen = 23f * d
                val curveLen = (totalLength - straightLen).coerceAtLeast(1f)
                val distOnCurve = (currentBurnPoint - straightLen).coerceAtLeast(0f)
                val curveProgress = distOnCurve / curveLen
                val symmetry = (1f - curveProgress).coerceIn(0f, 1f) // 0.0 (Tip) -> 1.0 (Straight)

                // C. INTENSITY (Fade Out at Hole)
                val fadeThreshold = 15f * d
                val distToHole = (currentBurnPoint - fuseInnerOffset).coerceAtLeast(0f)
                val lightIntensity = if (!isCritical && !isPaused) {
                    (distToHole / fadeThreshold).coerceIn(0f, 1f)
                } else {
                    0f
                }

                // === 2. DYNAMIC BODY HIGHLIGHT (RADIAL ONLY) ===
                if (lightIntensity > 0f) {
                    // 1. Calculate Angle based on REAL spark position relative to bomb center
                    // This projects the light onto the surface exactly underneath the fuse tip
                    val dx = realSparkX - bombCenterX
                    val dy = realSparkY - bombCenterY
                    val angleRad = atan2(dy, dx)

                    // 2. Position & Size
                    // We push the light orbit slightly inward (0.9x) so the "hotspot" isn't cut off by the edge
                    val orbitRadius = bodyRadius * 0.9f
                    val lightX = bombCenterX + cos(angleRad) * orbitRadius
                    val lightY = bombCenterY + sin(angleRad) * orbitRadius

                    val radialGlowRadius = bodyRadius * (0.8f + (0.2f * symmetry))
                    val radialAlpha = (0.5f + (0.2f * symmetry)) * lightIntensity

                    // 3. Draw Radial Glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF9800).copy(alpha = radialAlpha),       // Hot Center
                                Color(0xFFFF5722).copy(alpha = radialAlpha * 0.5f), // Warm Edge
                                Color.Transparent                            // Fade Out
                            ),
                            center = Offset(lightX, lightY),
                            radius = radialGlowRadius
                        ),
                        radius = bodyRadius,
                        center = Offset(bombCenterX, bombCenterY)
                    )
                }

                // Specular Glint (Static reflection)
                val specularCenter = Offset(bombCenterX - bodyRadius * 0.4f, bombCenterY - bodyRadius * 0.4f)
                drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent), center = specularCenter, radius = bodyRadius * 0.3f), radius = bodyRadius * 0.3f, center = specularCenter)

                // === 3. DYNAMIC NECK & RIMS (Horizontal Eraser) ===
                val baseDark = Color(0xFF0F172A)
                val baseLight = Color(0xFF475569)
                val rimDark = Color(0xFF1E293B)
                val rimLight = Color(0xFF64748B)
                val orangeDark = Color(0xFFD97706)
                val orangeLight = Color(0xFFFFB74D)

                // 1. RIM LOGIC
                val rimLeft = lerp(rimDark, orangeDark, symmetry * lightIntensity)
                val rimCenter = lerp(rimLight, orangeLight, symmetry * lightIntensity)
                val rimRight = lerp(rimDark, orangeDark, lightIntensity)
                val rimCenterOffset = 0.7f - (0.2f * symmetry)

                // 2. NECK GRADIENTS
                val rectLeft = bombCenterX - protrusionW / 2
                val rectRight = bombCenterX + protrusionW / 2

                // Base: Unlit Metal
                val metalGradient = Brush.horizontalGradient(0.0f to baseDark, 0.5f to baseLight, 1.0f to baseDark, startX = rectLeft, endX = rectRight)

                // Overlay: Lit Orange
                val neckRightColor = lerp(baseDark, orangeDark, lightIntensity)
                val litGradient = Brush.horizontalGradient(0.0f to baseDark, 0.5f to baseLight, 1.0f to neckRightColor, startX = rectLeft, endX = rectRight)

                val outerRimRect = Rect(offset = Offset(bombCenterX - protrusionW / 2, neckTopY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                val innerHoleRect = Rect(center = outerRimRect.center, radius = holeW / 2).copy(top = outerRimRect.center.y - holeH / 2, bottom = outerRimRect.center.y + holeH / 2)

                // 3. DRAW BASE LAYER (Permanent Unlit Background)
                drawOval(brush = metalGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckBaseY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                drawRect(brush = metalGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckTopY), size = Size(protrusionW, neckBaseY - neckTopY))

                // 4. DRAW LIT LAYER WITH "HORIZONTAL SLIDING ERASER" MASK
                // Use REAL X position for accurate lighting
                val triggerDist = 40f * d
                val distToEdge = (realSparkX - (bombCenterX + protrusionW / 2))
                val curtainProgress = if (isPaused) 1f else (1f - (distToEdge / triggerDist)).coerceIn(0f, 1f)

                // Only draw if not fully erased
                if (curtainProgress < 1f) {
                    val fadeWidth = 20f * d

                    // Extend sweep so "Solid Black" can eventually cover the whole width
                    val maxSweep = protrusionW + fadeWidth
                    val currentSweep = maxSweep * curtainProgress

                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(outerRimRect.left, neckTopY, outerRimRect.right, neckBaseY + 15f * d), Paint())

                        // A. Draw Full Lit Orange Layer
                        drawOval(brush = litGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckBaseY - cylinderOvalH / 2), size = Size(protrusionW, cylinderOvalH))
                        drawRect(brush = litGradient, topLeft = Offset(bombCenterX - protrusionW / 2, neckTopY), size = Size(protrusionW, neckBaseY - neckTopY))

                        // B. Erase Left-to-Right
                        if (curtainProgress > 0f) {
                            val eraserRight = outerRimRect.left + currentSweep

                            val slidingEraserBrush = Brush.horizontalGradient(
                                0.0f to Color.Black,       // Solid Erase
                                1.0f to Color.Transparent, // Soft Edge
                                startX = eraserRight - fadeWidth,
                                endX = eraserRight
                            )

                            // Draw the eraser rect
                            val drawHeight = (neckBaseY - neckTopY) + 20f * d

                            drawRect(
                                brush = slidingEraserBrush,
                                topLeft = Offset(outerRimRect.left, neckTopY),
                                size = Size(currentSweep, drawHeight),
                                blendMode = BlendMode.DstOut
                            )
                        }
                        canvas.restore()
                    }
                }

                // 5. DRAW RIM (Sits on top)
                val rimGradient = Brush.horizontalGradient(0.0f to rimLeft, rimCenterOffset to rimCenter, 1.0f to rimRight, startX = outerRimRect.left, endX = outerRimRect.right)
                drawOval(brush = rimGradient, topLeft = outerRimRect.topLeft, size = outerRimRect.size)

                // 6. HEAT LOGIC
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

                // === 3D FUSE (Layered Stroke Technique) ===
                if (!isCritical) {
                    val androidSegmentPath = android.graphics.Path()
                    pathMeasure.getSegment(0f, currentBurnPoint, androidSegmentPath, true)
                    val composePath = androidSegmentPath.asComposePath()

                    // 1. Dark Base (Shadow/Sides)
                    // CHANGED: "Ghost Grey" (0xFFCCC9C6).
                    // This is the faintest possible shadow, just enough to show it's round.
                    drawPath(
                        path = composePath,
                        color = Color(0xFFCCC9C6),
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )

                    // 2. Main Body (Midtone)
                    // Drawn at 80% width.
                    drawPath(
                        path = composePath,
                        color = Color(0xFFD6D3D1),
                        style = Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round)
                    )

                    // 3. Specular Highlight (The "Roundness")
                    // Drawn at 40% width.
                    drawPath(
                        path = composePath,
                        color = Color.White.copy(alpha = 0.5f),
                        style = Stroke(width = strokeW * 0.4f, cap = StrokeCap.Round)
                    )
                }

                // Draw Rim Mask
                frontRimPath.reset(); frontRimPath.arcTo(outerRimRect, 0f, 180f, false); frontRimPath.lineTo(innerHoleRect.left, innerHoleRect.center.y); frontRimPath.arcTo(innerHoleRect, 180f, -180f, false); frontRimPath.close()
                drawPath(path = frontRimPath, brush = rimGradient)

                // --- CRITICAL BLOOM ---
                // We check if alpha > 0 so it draws while fading out even if isCritical becomes false
                if (criticalAlpha > 0f && !isPaused) {
                    val fuseBase = innerHoleRect.center

                    // 1. Inner Hot Glow (Fade in opacity)
                    drawOval(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFFFE0).copy(alpha = criticalAlpha),
                                Color(0xFFFFD700).copy(alpha = criticalAlpha)
                            ),
                            center = fuseBase,
                            radius = holeW
                        ),
                        topLeft = innerHoleRect.topLeft,
                        size = innerHoleRect.size
                    )

                    // 2. Outer Red Halo (Fade in opacity)
                    val bloomRect = innerHoleRect.inflate(20f * d)
                    val bloomAspectRatio = bloomRect.height / bloomRect.width

                    withTransform({ scale(1f, bloomAspectRatio, pivot = fuseBase) }) {
                        val drawRadius = bloomRect.width / 2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    NeonRed.copy(alpha = 0.6f * criticalAlpha), // Scales with animation
                                    NeonRed.copy(alpha = 0f)
                                ),
                                center = fuseBase,
                                radius = drawRadius,
                                tileMode = TileMode.Clamp
                            ),
                            radius = drawRadius,
                            center = fuseBase
                        )
                    }
                }

                // --- SPARKS & PARTICLES ---
                val pos = floatArrayOf(0f, 0f); pathMeasure.getPosTan(currentBurnPoint, pos, null); val sparkCenter = Offset(pos[0], pos[1]); if (!isReflection) currentSparkCenter = sparkCenter
                if (!isPaused && !isReflection) {
                    if (Math.random() < 0.3) { val angle = Math.random() * Math.PI * 2; val speed = (2f + Math.random() * 4f).toFloat(); val sx = if (isCritical) innerHoleRect.center.x else sparkCenter.x; val sy = if (isCritical) innerHoleRect.center.y else sparkCenter.y; sparks.add(LocalVisualParticle(x = sx, y = sy, vx = cos(angle).toFloat() * speed, vy = (sin(angle) * speed - 2f).toFloat(), life = (0.2f + Math.random() * 0.3f).toFloat(), maxLife = 0.5f)) }
                    if (Math.random() < 0.2) { val angle = -Math.PI / 2 + (Math.random() - 0.5) * 0.5; val speed = (1f + Math.random() * 2f).toFloat(); val sx = if (isCritical) innerHoleRect.center.x else sparkCenter.x; val sy = if (isCritical) innerHoleRect.center.y else sparkCenter.y; val smokeVy = if (isCritical) sin(angle).toFloat() * speed * 2f else sin(angle).toFloat() * speed; smokePuffs.add(LocalVisualParticle(x = sx, y = sy - fuseYOffset, vx = (cos(angle) * speed).toFloat(), vy = smokeVy, life = (1f + Math.random().toFloat() * 0.5f), maxLife = 1.5f)) }
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
fun C4Visual(isLedOn: Boolean, isDarkMode: Boolean, isPaused: Boolean, onTogglePause: () -> Unit) {
    val density = LocalDensity.current
    val d = density.density

    val ledSize = 12f * d
    val ledRadius = 6f * d
    val c4BodyColor = if (isDarkMode) Color(0xFFD6D0C4) else Color(0xFFC8C2B4)
    val c4BlockColor = if (isDarkMode) Color(0xFFC7C1B3) else Color(0xFFB9B3A5)
    val c4BorderColor = if (isDarkMode) Color(0xFF9E9889) else Color(0xFF8C8677)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(320.dp).height(200.dp).background(c4BodyColor, RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { Box(Modifier.width(80.dp).fillMaxHeight().background(c4BlockColor).border(1.dp, c4BorderColor)) }
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black))
                Box(Modifier.fillMaxWidth().height(24.dp).background(Color.Black))
            }
            Box(modifier = Modifier.align(Alignment.Center).width(280.dp).height(140.dp).background(C4ScreenBg, RoundedCornerShape(8.dp)).border(4.dp, Slate800, RoundedCornerShape(8.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
                    val step = 20f * d
                    for (x in 0..size.width.toInt() step step.toInt()) drawLine(color = NeonCyan, start = Offset(x.toFloat(), 0f), end = Offset(x.toFloat(), size.height), strokeWidth = 2f)
                    for (y in 0..size.height.toInt() step step.toInt()) drawLine(color = NeonCyan, start = Offset(0f, y.toFloat()), end = Offset(size.width, y.toFloat()), strokeWidth = 2f)
                }
                Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(0.6f).fillMaxWidth().padding(horizontal = 16.dp).background(LcdDarkBackground, RoundedCornerShape(4.dp)).border(2.dp, Slate800, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onTogglePause() }.padding(8.dp)) {
                                Icon(Icons.Rounded.DeveloperBoard, null, tint = if (isPaused) Color(0xFF3B82F6) else Color(0xFF64748B), modifier = Modifier.size(32.dp))
                                Text("PAUSED", color = if (isPaused) Color(0xFF3B82F6) else Color(0xFF1E293B), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("--:--", color = NeonRed, fontSize = 56.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 6.dp).offset(y = (-5).dp), style = TextStyle(fontFamily = VisualsFont, shadow = Shadow(color = NeonRed, offset = Offset.Zero, blurRadius = 12f)))
                        }
                    }
                    Box(Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = 5.dp)) {
                            Canvas(modifier = Modifier
                                .size(12.dp)
                                .offset(x = 3.5.dp, y = 0.dp) // <--- Add this! Change X/Y to move just the LED
                            ) {
                                if (isLedOn) drawCircle(brush = Brush.radialGradient(colors = listOf(NeonRed.copy(alpha=0.8f), Color.Transparent), center = center, radius = ledSize), radius = ledSize)
                                drawCircle(color = if (isLedOn) NeonRed else Color(0xFF450a0a), radius = ledRadius)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ARMED", color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, style = if (isLedOn) TextStyle(fontFamily = VisualsFont, shadow = Shadow(color = NeonRed, blurRadius = 8f)) else LocalTextStyle.current.copy(fontFamily = VisualsFont))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Surface(color = Color(0x33F59E0B), border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha=0.5f)), shape = RoundedCornerShape(4.dp)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("HIGH VOLTAGE // DO NOT TAMPER", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
            }
        }
    }
}

@Composable
fun DynamiteVisual(timeLeft: Float, isPaused: Boolean, onTogglePause: () -> Unit) {
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
    val tickOffsetY = -130f * d
    val dingOffsetY = -160f * d

    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = textMeasurer.measure(
        text = "ACME CORP",
        style = TextStyle(color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = VisualsFont)
    )
    val stickTextResult = textMeasurer.measure(
        text = "HIGH EXPLOSIVE",
        style = TextStyle(color = Color.Black.copy(alpha=0.3f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = VisualsFont)
    )

    // FIX: Added Suppression to stop the IDE from complaining about the necessary explicit types
    @Suppress("RemoveExplicitTypeArguments", "UNCHECKED_CAST")
    val textEffectsSaver = listSaver<SnapshotStateList<LocalVisualText>, Any>(
        save = { stateList ->
            stateList.map {
                // FIX: Explicit <Any> is required here to prevent Type Mismatch errors,
                // even if the IDE says it's inferred.
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
            val mutableList = mutableStateListOf<LocalVisualText>()
            savedList.forEach { item ->
                val p = item as List<Any>
                val g = p[4] as List<Int>
                mutableList.add(LocalVisualText(
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

    LaunchedEffect(timeLeft) {
        val currentStep = ceil(timeLeft * 2).toInt()
        val isFast = timeLeft <= 5f
        if (currentStep != lastTriggerStep) {
            if ((isFast || currentStep % 2 == 0) && timeLeft > 1.1f && !isPaused) {
                textEffects.add(LocalVisualText("TICK", (Math.random() * 100 - 50).toFloat(), tickOffsetY, if (isFast) NeonRed else TextGray, fontSize = 20f))
            }
            lastTriggerStep = currentStep
        }
        if (timeLeft <= 1.0f && !hasShownDing && timeLeft > 0 && !isPaused) {
            textEffects.add(LocalVisualText("DING!", 0f, dingOffsetY, Color(0xFFFFD700), listOf(Color(0xFFFFFACD), Color(0xFFFFD700)), life = 2.0f, fontSize = 48f))
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

            for (index in 0..2) {
                val stickLeft = startX + index * (stickW + spacing)
                drawRoundRect(brush = stickBrush, topLeft = Offset(stickLeft, startY), size = Size(stickW, stickH + 30f * d), cornerRadius = CornerRadius(cornerRad, cornerRad))
                val stickCenterX = stickLeft + stickW / 2
                val stickCenterY = startY + (stickH / 2)
                withTransform({ rotate(-90f, pivot = Offset(stickCenterX, stickCenterY)); translate(left = stickCenterX - stickTextResult.size.width / 2, top = stickCenterY - stickTextResult.size.height / 2 + stickTextYOffset) }) { drawText(textLayoutResult = stickTextResult) }
            }
            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + 40f), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + stickH - 40f), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
        }
        Box(modifier = Modifier.offset(y = 15.dp).size(160.dp).clip(CircleShape).clickable { onTogglePause() }) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val clockCenter = center
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

                // --- [UPDATED GLASS SHADOW & HIGHLIGHT] ---

                // 1. Main Lens Glare (Keep the Ice Blue tint for glass feel)
                val glareCenter = Offset(clockCenter.x - clockRad * 0.35f, clockCenter.y - clockRad * 0.35f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFE0F7FA).copy(alpha = 0.3f), Color.Transparent),
                        center = glareCenter,
                        radius = clockRad * 0.8f
                    ),
                    radius = clockRad * 0.7f,
                    center = glareCenter
                )

                // 2. Upper Rim Shadow (The "Overhang" Effect)
                // Gradient: Gray at the top (shadow) -> Transparent White downwards
                val shadowBrush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Gray.copy(alpha = 0.6f),  // Top: Darker Gray Shadow
                        Color.White.copy(alpha = 0f)    // Bottom: Fades to transparent
                    ),
                    startY = clockCenter.y - clockRad,  // Starts at the very top of the clock
                    endY = clockCenter.y - (clockRad * 0.4f) // Fades out by the middle of the top half
                )

                drawArc(
                    brush = shadowBrush, // <--- Using the gradient brush now
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(clockCenter.x - (clockRad * 0.92f), clockCenter.y - (clockRad * 0.92f)),
                    size = Size(clockRad * 1.84f, clockRad * 1.84f),
                    style = Stroke(width = 6f * d, cap = StrokeCap.Round) // Increased width (4->6) to make the shadow softer
                )
            }
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

@Composable
fun FrogVisual(timeLeft: Float, isCritical: Boolean, isPaused: Boolean, onTogglePause: () -> Unit, isDarkMode: Boolean) {
    // --- VARIABLES ---
    val squishMagnitude = 0.98f
    val shadowResponseFactor = 1.5f // Controls how much the shadow grows/shrinks

    val isPanic = timeLeft <= 1.05f
    val tickDuration = if (timeLeft <= 5f) 0.5f else 1.0f
    val currentProgress = if (isPaused) 0f else (timeLeft % tickDuration) / tickDuration

    // 1. Animations
    val isCroaking = (1f - currentProgress) < 0.2f && !isPaused
    val bellyHeightScale by animateFloatAsState(if (isCroaking) 1.15f else 1.0f, spring(stiffness = Spring.StiffnessMediumLow), label = "croak")

    val croakSquish by animateFloatAsState(
        targetValue = if (isCroaking) squishMagnitude else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "squish"
    )

    val mouthOpenProgress = (bellyHeightScale - 1.0f) / 0.15f
    val infiniteTransition = rememberInfiniteTransition("flail")
    val flailRotation by infiniteTransition.animateFloat(-40f, 40f, infiniteRepeatable(tween(100, easing = LinearEasing), RepeatMode.Reverse), label = "flail")
    val alertScale = remember { Animatable(0f) }
    LaunchedEffect(isPanic) { if (isPanic) alertScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy)) else alertScale.snapTo(0f) }

    // Define the target scale based on the game state
    val targetEyeScale = when {
        isPanic -> 1.0f
        isCritical -> 0.9f
        else -> 0.8f
    }

    // Animate the scale over 300ms for a "popping" effect
    val animatedEyeScale by animateFloatAsState(
        targetValue = targetEyeScale,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "EyeBulge"
    )

    // Paths & State
    val silhouettePath = remember { Path() }
    val bodyCirclePath = remember { Path() }
    val leftBumpPath = remember { Path() }
    val rightBumpPath = remember { Path() }
    val sweatDropPath = remember { Path() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    val boingAnim = remember { Animatable(1f) }
    val sweatDrops = remember { mutableListOf<LocalVisualParticle>() }
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var timeAccumulator by remember { mutableFloatStateOf(0.5f) }
    val currentIsCritical by rememberUpdatedState(isCritical)

    val density = LocalDensity.current
    val d = density.density

    // Physics Loop
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else ((nanos - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameTime = nanos
                if (currentIsCritical) timeAccumulator += dt
                for (i in sweatDrops.indices.reversed()) {
                    val p = sweatDrops[i]; p.life -= dt; p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 500f * dt
                    if (p.life <= 0) sweatDrops.removeAt(i)
                }
            }
        }
    }
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(Random.nextLong(2000, 4000)); isBlinking = true; delay(150); isBlinking = false } }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
        scope.launch { boingAnim.snapTo(1f); boingAnim.animateTo(0.9f, tween(50)); boingAnim.animateTo(1.05f, tween(100)); boingAnim.animateTo(1.0f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
        onTogglePause()
    }) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2; val cy = size.height / 2

            // Rebuild Paths if size changes
            if (size != cachedSize) {
                val bodyRadius = size.width * 0.35f
                val bumpRadius = bodyRadius * 0.42f
                val bumpY = cy - bodyRadius * 0.68f
                val bumpXOffset = bodyRadius * 0.56f

                bodyCirclePath.reset()
                val n = 2.4f; val a = bodyRadius * 1.15f; val b = bodyRadius * 0.95f; val k = 0.3f
                val steps = 120
                for (i in 0..steps) {
                    val t = (i.toFloat() / steps) * 2 * PI
                    val cosT = cos(t); val sinT = sin(t)
                    val signX = if (cosT >= 0) 1f else -1f; val signY = if (sinT >= 0) 1f else -1f
                    val rawY = b * signY * abs(sinT).pow(2.0 / n).toFloat()
                    val widthScale = 1.0f + k * (rawY / b)
                    val rawX = a * widthScale * signX * abs(cosT).pow(2.0 / n).toFloat()
                    if (i == 0) bodyCirclePath.moveTo(cx + rawX, cy + rawY) else bodyCirclePath.lineTo(cx + rawX, cy + rawY)
                }
                bodyCirclePath.close()

                leftBumpPath.reset(); leftBumpPath.addOval(Rect(center = Offset(cx - bumpXOffset, bumpY), radius = bumpRadius))
                rightBumpPath.reset(); rightBumpPath.addOval(Rect(center = Offset(cx + bumpXOffset, bumpY), radius = bumpRadius))
                silhouettePath.reset(); silhouettePath.op(bodyCirclePath, leftBumpPath, PathOperation.Union); silhouettePath.op(silhouettePath, rightBumpPath, PathOperation.Union)
                val dropSize = bodyRadius * 0.1f
                sweatDropPath.reset(); sweatDropPath.moveTo(-dropSize, -dropSize * 0.2f); sweatDropPath.arcTo(Rect(topLeft = Offset(-dropSize, -dropSize), bottomRight = Offset(dropSize, dropSize)), 180f, 180f, false); sweatDropPath.lineTo(0f, dropSize * 1.3f); sweatDropPath.close()
                cachedSize = size
            }

            val bodyRadius = size.width * 0.35f
            val floorY = cy + bodyRadius * 1.05f + 1.5f * d

            // --- CALCULATE PHYSICS HERE ---
            // Calculate total stretch/squash BEFORE drawing the shadow
            val totalSquash = boingAnim.value * croakSquish
            val totalStretch = (2f - boingAnim.value) * (2f - croakSquish)
            val pivotY = cy + bodyRadius

            drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->
                if (!isDarkMode && !isReflection) {
                    val baseShadowW = bodyRadius * 2.3f
                    val baseShadowH = baseShadowW * 0.2f

                    // --- DYNAMIC SHADOW SCALING ---
                    // When the body stretches horizontally (totalStretch > 1.0), the shadow expands.
                    val shadowScale = 1f + (totalStretch - 1f) * shadowResponseFactor

                    val currentShadowW = baseShadowW * shadowScale
                    val currentShadowH = baseShadowH * shadowScale // Keep aspect ratio consistent

                    drawOval(color = Color.Black.copy(alpha = 0.2f), topLeft = Offset(cx - currentShadowW / 2, floorY - currentShadowH / 2), size = Size(currentShadowW, currentShadowH))
                }

                // Helper to map coordinates based on squash
                fun getMod(x: Float, y: Float): Offset {
                    val newX = cx + (x - cx) * totalStretch
                    val newY = pivotY + (y - pivotY) * totalSquash
                    return Offset(newX, newY)
                }
                val outlineStroke = Stroke(width = 6f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)

                // 1. DRAW FEET (BEHIND)
                val footRadius = bodyRadius * 0.2f; val footY = cy + bodyRadius * 0.85f; val footOffset = bodyRadius * 0.65f
                val lFoot = getMod(cx - footOffset, footY); val rFoot = getMod(cx + footOffset, footY)
                drawCircle(color = Color.Black, radius = footRadius, center = lFoot, style = outlineStroke)
                drawCircle(color = FrogBody, radius = footRadius, center = lFoot)
                drawCircle(color = Color.Black, radius = footRadius, center = rFoot, style = outlineStroke)
                drawCircle(color = FrogBody, radius = footRadius, center = rFoot)

                // 2. DRAW BODY (TRANSFORMED)
                withTransform({ scale(totalStretch, totalSquash, pivot = Offset(cx, pivotY)) }) {
                    drawPath(path = silhouettePath, color = Color.Black, style = outlineStroke)
                    drawPath(path = silhouettePath, color = FrogBody)

                    // Clipped Belly
                    val bumpRadius = bodyRadius * 0.42f; val bumpY = cy - bodyRadius * 0.68f; val eyeW = bumpRadius * 0.56f
                    val cheekTopY = (bumpY + eyeW) - (eyeW * 0.1f); val blushBottomY = cheekTopY + (bodyRadius * 0.22f)
                    clipPath(path = silhouettePath) {
                        val anchorXDist = bodyRadius * 0.45f; val anchorY = cy + bodyRadius * 0.95f
                        val targetBellyTop = blushBottomY + bodyRadius * 0.05f
                        val bellyHeight = (anchorY - targetBellyTop) * bellyHeightScale

                        // FIX: Removed 'bellyHalfWidth' and used 'anchorXDist' directly
                        val bellyRadius = (anchorXDist.pow(2) + bellyHeight.pow(2)) / (2 * bellyHeight)
                        val bellyCenterY = targetBellyTop + bellyRadius

                        drawCircle(color = FrogBelly, radius = bellyRadius, center = Offset(cx, bellyCenterY))
                        drawOval(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(cx - bellyRadius * 0.3f, targetBellyTop + bellyHeight * 0.15f), size = Size(bellyRadius * 0.6f, bellyHeight * 0.15f))
                    }
                }

                // 3. DRAW FEATURES (ON TOP)
                val bumpRadius = bodyRadius * 0.42f; val bumpY = cy - bodyRadius * 0.68f; val bumpXOffset = bodyRadius * 0.56f

                // Blushes
                val cheekW = bodyRadius * 0.35f; val cheekH = bodyRadius * 0.22f; val eyeW = bumpRadius * 0.56f
                val cheekTopY = (bumpY + eyeW) - (eyeW * 0.1f); val cheekCenterY = cheekTopY + cheekH/2
                val cheekOffsetX = bodyRadius * 0.65f
                val lCheek = getMod(cx - cheekOffsetX, cheekCenterY); val rCheek = getMod(cx + cheekOffsetX, cheekCenterY)
                drawOval(color = Color(0xFFff9693), topLeft = Offset(lCheek.x - cheekW/2, lCheek.y - cheekH/2), size = Size(cheekW, cheekH))
                drawOval(color = Color(0xFFff9693), topLeft = Offset(rCheek.x - cheekW/2, rCheek.y - cheekH/2), size = Size(cheekW, cheekH))

                // Arms
                val armW = bodyRadius * 0.18f; val armH = bodyRadius * 0.28f
                val armY = cy - bodyRadius * 0.06f; val armX = bodyRadius * 0.72f
                val lArm = getMod(cx - armX, armY); val rArm = getMod(cx + armX, armY)
                rotate(if (isPanic) flailRotation else 30f, pivot = lArm) {
                    drawArc(color = Color.Black, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(lArm.x - armW, lArm.y - armH/2), size = Size(armW*2, armH), style = outlineStroke)
                    drawOval(color = FrogBody, topLeft = Offset(lArm.x - armW, lArm.y - armH/2), size = Size(armW*2, armH))
                }
                rotate(if (isPanic) -flailRotation else -30f, pivot = rArm) {
                    drawArc(color = Color.Black, startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(rArm.x - armW, rArm.y - armH/2), size = Size(armW*2, armH), style = outlineStroke)
                    drawOval(color = FrogBody, topLeft = Offset(rArm.x - armW, rArm.y - armH/2), size = Size(armW*2, armH))
                }

                fun drawEye(baseX: Float, baseY: Float, isLeft: Boolean) {
                    val pos = getMod(baseX, baseY)

                    // --- DYNAMIC ANIMATED SIZE ---
                    // The multiplier now flows smoothly between 0.8 -> 1.0 -> 1.2
                    val currentEyeRadius = eyeW * animatedEyeScale
                    val currentPupilRadius = currentEyeRadius + (outlineStroke.width / 2f)

                    // Foundation: Outline for everything except normal open eyes
                    val isNormalOpenEye = !isCritical && !isPanic && !isBlinking
                    if (!isNormalOpenEye) {
                        drawCircle(
                            color = Color.Black,
                            radius = currentEyeRadius,
                            center = pos,
                            style = outlineStroke
                        )
                    }

                    // White eyeball base
                    drawCircle(color = Color.White, radius = currentEyeRadius, center = pos)

                    if (isCritical && !isPanic) {
                        // Squinting path scales with the animated radius
                        val path = Path(); val size = currentEyeRadius * 1.2f; val off = size * 0.15f; val ex = pos.x + if(isLeft) off else -off
                        path.moveTo(ex - size/2, pos.y - size/2); path.lineTo(ex + size/4, pos.y); path.lineTo(ex - size/2, pos.y + size/2)
                        if (!isLeft) { path.reset(); path.moveTo(ex + size/2, pos.y - size/2); path.lineTo(ex - size/4, pos.y); path.lineTo(ex + size/2, pos.y + size/2) }
                        drawPath(path, Color.Black, style = Stroke(8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else if (!isPanic) {
                        if (isBlinking) {
                            // Blink line scales so it doesn't look too small/large for the eye
                            val blinkWidth = 15f * d * animatedEyeScale
                            drawLine(
                                color = Color.Black,
                                start = Offset(pos.x - blinkWidth, pos.y),
                                end = Offset(pos.x + blinkWidth, pos.y),
                                strokeWidth = 4f * d,
                                cap = StrokeCap.Round
                            )
                        } else {
                            // Normal Pupil: Seamlessly covers the eye as it grows
                            drawCircle(color = Color.Black, radius = currentPupilRadius, center = pos)
                            drawCircle(color = Color.White, radius = currentPupilRadius * 0.25f, center = Offset(pos.x - currentPupilRadius*0.4f, pos.y - currentPupilRadius*0.4f))
                        }
                    }
                }
                drawEye(cx - bumpXOffset, bumpY, true); drawEye(cx + bumpXOffset, bumpY, false)

                // Mouth
                val mouthY = bumpY + eyeW * 0.65f
                val mouthCenter = getMod(cx, mouthY); val mcx = mouthCenter.x; val mcy = mouthCenter.y
                val mouthW = if (isCritical) bodyRadius * 0.2f else bodyRadius * 0.3f
                val mouthColor = Color(0xFF9E2A2B)
                val mouthStroke = Stroke(width = 3.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val jawDrop = bodyRadius * 0.08f * mouthOpenProgress

                if (isPanic) {
                    val panicH = 5f * d; val panicW = mouthW * 0.3f
                    if (mouthOpenProgress > 0.05f) {
                        val path = Path()
                        path.moveTo(mcx - panicW, mcy + panicH)
                        path.quadraticTo(mcx, mcy + panicH + jawDrop + (2f*d), mcx + panicW, mcy + panicH)
                        path.lineTo(mcx, mcy - panicH)
                        path.lineTo(mcx - panicW, mcy + panicH)
                        path.close()
                        drawPath(path, mouthColor); drawPath(path, Color.Black, style = mouthStroke)
                    }
                    drawPath(path = Path().apply { moveTo(mcx - panicW, mcy + panicH); lineTo(mcx, mcy - panicH); lineTo(mcx + panicW, mcy + panicH) }, color = Color.Black, style = mouthStroke)
                }
                else if (isCritical) {
                    val dotBaseRadius = 5f * d; val currentDotRadius = dotBaseRadius * (1f + mouthOpenProgress * 0.5f)
                    drawCircle(color = Color.Black, radius = currentDotRadius, center = mouthCenter)
                }
                else {
                    val smileDip = bodyRadius * 0.08f; val redRadius = mouthW * 0.25f; val startY = mcy + (smileDip * 0.5f)
                    if (mouthOpenProgress > 0.05f) {
                        val path = Path()
                        path.moveTo(mcx - redRadius, startY)
                        path.quadraticTo(mcx, startY + jawDrop + (3f*d), mcx + redRadius, startY)
                        path.quadraticTo(mcx + redRadius/2, startY, mcx, mcy)
                        path.quadraticTo(mcx - redRadius/2, startY, mcx - redRadius, startY)
                        path.close()
                        drawPath(path, mouthColor); drawPath(path, Color.Black, style = mouthStroke)
                    }
                    val smilePath = Path()
                    smilePath.moveTo(mcx - mouthW/2, mcy)
                    smilePath.quadraticTo(mcx - mouthW/4, mcy + smileDip, mcx, mcy)
                    smilePath.quadraticTo(mcx + mouthW/4, mcy + smileDip, mcx + mouthW/2, mcy)
                    drawPath(smilePath, Color.Black, style = mouthStroke)
                }

                // Alert Mark
                if (alertScale.value > 0f) {
                    val markBaseY = bumpY - bumpRadius * 1.8f; val markPos = getMod(cx, markBaseY)
                    val markW = bodyRadius * 0.15f; val markH = bodyRadius * 0.6f
                    withTransform({ scale(alertScale.value, alertScale.value, pivot = Offset(markPos.x, markPos.y + markH/2)) }) {
                        drawRoundRect(color = Color(0xFFFF0000), topLeft = Offset(markPos.x - markW/2, markPos.y - markH/2), size = Size(markW, markH * 0.65f), cornerRadius = CornerRadius(markW/2, markW/2))
                        drawCircle(color = Color(0xFFFF0000), radius = markW/1.8f, center = Offset(markPos.x, markPos.y + markH/2 - markW/2))
                    }
                }

                // Particles
                if (!isReflection && currentIsCritical && timeAccumulator >= 0.5f) {
                    timeAccumulator -= 0.5f
                    repeat(3) { i ->
                        val angle = -PI / 4 + (i - 1) * 0.5 + ((Math.random() - 0.5) * 0.1)
                        val speed = (bumpRadius / 0.5f) * (0.9f + Math.random().toFloat() * 0.2f)
                        val spawnX = (cx + bumpXOffset) + (bumpRadius * 1.05f)
                        val spawnY = bumpY - (bumpRadius * 1.15f)
                        val modSpawn = getMod(spawnX, spawnY)
                        sweatDrops.add(LocalVisualParticle(modSpawn.x, modSpawn.y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), 0.5f, 0.5f))
                    }
                }
                sweatDrops.forEach { p ->
                    withTransform({ translate(p.x, p.y); rotate((atan2(p.vy, p.vx) * (180f / PI)).toFloat() + 90f, Offset.Zero) }) {
                        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f); val dropSize = bodyRadius * 0.1f
                        drawPath(path = sweatDropPath, color = Color(0xFF60A5FA).copy(alpha = alpha))
                        drawOval(color = Color.White.copy(alpha = 0.6f * alpha), topLeft = Offset(-dropSize * 0.4f, -dropSize * 0.6f), size = Size(dropSize * 0.5f, dropSize * 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
fun HenVisual(modifier: Modifier = Modifier, timeLeft: Float, isPaused: Boolean, onTogglePause: () -> Unit, eggWobbleRotation: Float, henSequenceElapsed: Float, showEgg: Boolean = true, crackStage: Int = 0, isPainedBeakOpen: Boolean = false, isPainedBeakClosed: Boolean = false, isDarkMode: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition("hen_anim")

    // 1. MOVED UP: Density is needed for blur calculations
    val density = LocalDensity.current
    val d = density.density

    // 2. BLINK STATE & TIMER
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2000, 4000))
            isBlinking = true
            delay(150)
            isBlinking = false
        }
    }

    val wingFlapRotation by infiniteTransition.animateFloat(0f, 45f, infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse), "wing")
    val combPath = remember { Path() }; val wattlePath = remember { Path() }; val upperBeakPath = remember { Path() }; val lowerBeakPath = remember { Path() }; val wincePath = remember { Path() }; val crack1Path = remember { Path() }; val crack2Path = remember { Path() }; val crack3Path = remember { Path() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope(); val boingAnim = remember { Animatable(1f) }
    val fraction = timeLeft % 1f
    var isStandardBeakOpen = !isPaused && (fraction > 0.8f && fraction < 0.95f); if (henSequenceElapsed > 0.0f) isStandardBeakOpen = false
    val isRapidCluck = if (henSequenceElapsed > 0.35f && henSequenceElapsed < 0.85f) (henSequenceElapsed % 0.166f) < 0.08f else false
    val baseBeakOpen = isPainedBeakOpen || (if (henSequenceElapsed > 0.3f && henSequenceElapsed < 1.0f) isRapidCluck else isStandardBeakOpen)

    var animOffsetY = 0f; var glassRotation = 0f; var glassScale = 1f; var isSliding = false; var isFlapping = false; var isSmushed = false; var henShadowAlpha = 1.0f; var henShadowScale = 1.0f; var eggShadowAlpha = 0.0f; var drawHenShadow = true

    // NEW: Variable to track how much we should blur the shadow
    var shadowBlurRadius = 0f

    if (henSequenceElapsed <= 0f) isFlapping = false
    else if (henSequenceElapsed <= 0.5f) isFlapping = true
    else if (henSequenceElapsed <= 2.0f) {
        // FLYING UP PHASE
        val t = (henSequenceElapsed - 0.5f) / 1.5f
        animOffsetY = -2000f * (t * t)
        isFlapping = true
        henShadowAlpha = (1f - t).coerceIn(0f, 1f)

        // Shadow stays constant size (Sunlight logic)
        henShadowScale = 1.0f

        // NEW: Blur increases as she flies higher (Max ~15dp)
        shadowBlurRadius = t * 15f * d

        eggShadowAlpha = t.coerceIn(0f, 1f)
    }
    else if (henSequenceElapsed <= 4.2f) { animOffsetY = -2000f; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else if (henSequenceElapsed <= 4.5f) { val t = (henSequenceElapsed - 4.2f) / 0.3f; animOffsetY = -2000f * (1f - t * t); glassScale = 1.0f + (t * 0.4f); isSliding = true; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else if (henSequenceElapsed <= 6.0f) { animOffsetY = 0f; isSliding = true; isSmushed = true; glassScale = 1.5f; glassRotation = 5f; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else { val t = (henSequenceElapsed - 6.0f) / 3.0f; animOffsetY = 4000f * t; isSliding = true; isSmushed = true; glassScale = 1.5f; glassRotation = 10f; drawHenShadow = false; eggShadowAlpha = 1.0f }

    val effectiveBeakOpen = (baseBeakOpen || isSliding) && !isPainedBeakClosed
    val currentAnimOffsetY = remember { mutableFloatStateOf(0f) }; currentAnimOffsetY.floatValue = animOffsetY

    val henRadiusPx = 110f * d
    val eggRadiusPx = 80f * d
    val eggHitOffsetY = (110f - 10f - 75f) * d

    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { tapOffset ->
                val cx = size.width / 2; val cy = size.height / 2; val henCenterY = cy + currentAnimOffsetY.floatValue
                val hitHen = sqrt((tapOffset.x - cx).pow(2) + (tapOffset.y - henCenterY).pow(2)) <= henRadiusPx * 1.2f
                val hitEgg = showEgg && sqrt((tapOffset.x - cx).pow(2) + (tapOffset.y - (cy + eggHitOffsetY)).pow(2)) <= eggRadiusPx

                if (hitHen || hitEgg) { scope.launch { boingAnim.snapTo(1f); boingAnim.animateTo(0.9f, tween(50)); boingAnim.animateTo(1.05f, tween(100)); boingAnim.animateTo(1.0f, spring(dampingRatio = 0.4f, stiffness = 400f)) }; onTogglePause() }
            }
        }) {
            val cx = size.width / 2; val cy = size.height / 2

            if (size != cachedSize) {
                val henBodyRadius = 110f * d
                val cRadius = 28f * d; val cX = 0f * d; val cY = -henBodyRadius + 5f * d
                val lRadius = 20f * d; val lX = -38f * d; val lY = -henBodyRadius + 10f * d
                val rRadius = 32f * d; val rX = 50f * d; val rY = -henBodyRadius + 5f * d

                combPath.reset()
                combPath.addOval(Rect(center = Offset(cX, cY), radius = cRadius))
                combPath.addOval(Rect(center = Offset(lX, lY), radius = lRadius))
                combPath.addOval(Rect(center = Offset(rX, rY), radius = rRadius))

                val faceEdgeX = henBodyRadius * 0.82f; val beakYBase = -6f * d; val beakH = 24f * d; val beakLen = 26f * d
                upperBeakPath.reset(); upperBeakPath.moveTo(faceEdgeX, beakYBase - beakH/2)
                upperBeakPath.quadraticTo(faceEdgeX + beakLen * 0.5f, beakYBase - beakH * 0.7f, faceEdgeX + beakLen, beakYBase)
                upperBeakPath.lineTo(faceEdgeX, beakYBase); upperBeakPath.close()

                lowerBeakPath.reset(); lowerBeakPath.moveTo(faceEdgeX, beakYBase); lowerBeakPath.lineTo(faceEdgeX + beakLen * 0.8f, beakYBase)
                lowerBeakPath.quadraticTo(faceEdgeX + beakLen * 0.3f, beakYBase + beakH * 0.7f, faceEdgeX, beakYBase + beakH / 2); lowerBeakPath.close()

                val lowerBeakBottomY = beakYBase + beakH / 2
// FIX: Inlined 'wattleTopX' (faceEdgeX) and 'wattleTopY' (lowerBeakBottomY)
                val wattleWidth = 24f * d; val wattleHeight = 32f * d
                wattlePath.reset(); wattlePath.moveTo(faceEdgeX, lowerBeakBottomY)
                wattlePath.cubicTo(
                    faceEdgeX - wattleWidth, lowerBeakBottomY + wattleHeight * 0.5f,
                    faceEdgeX - (wattleWidth * 0.5f), lowerBeakBottomY + wattleHeight,
                    faceEdgeX, lowerBeakBottomY + wattleHeight
                )
                wattlePath.cubicTo(
                    faceEdgeX + (wattleWidth * 0.5f), lowerBeakBottomY + wattleHeight,
                    faceEdgeX + wattleWidth, lowerBeakBottomY + wattleHeight * 0.5f,
                    faceEdgeX, lowerBeakBottomY
                )
                wattlePath.close()

                val eyeX = faceEdgeX - 25f * d; val eyeYBase = -25f * d; wincePath.reset(); wincePath.moveTo(eyeX - 8f * d, eyeYBase - 8f * d); wincePath.lineTo(eyeX, eyeYBase); wincePath.lineTo(eyeX - 8f * d, eyeYBase + 8f * d)
                val eggHeight = 150f * d; val eggTop = (cy + henBodyRadius - 10f * d) - eggHeight; val eggCenterY = eggTop + eggHeight/2
                crack1Path.reset(); crack1Path.moveTo(0f, eggTop + eggHeight * 0.1f); crack1Path.lineTo(5f, eggTop + eggHeight * 0.3f); crack1Path.lineTo(-5f, eggCenterY)
                crack2Path.reset(); crack2Path.moveTo(-5f, eggCenterY); crack2Path.lineTo(-25f, eggCenterY + eggHeight * 0.2f); crack2Path.lineTo(-15f, eggCenterY + eggHeight * 0.35f)
                crack3Path.reset(); crack3Path.moveTo(-25f, eggCenterY + eggHeight * 0.2f); crack3Path.lineTo(10f, eggCenterY + eggHeight * 0.25f); crack3Path.lineTo(35f, eggCenterY + eggHeight * 0.4f)
                cachedSize = size
            }

            val henBodyRadius = 110f * d
            val floorY = cy + henBodyRadius

            val heightFade = (1f - (abs(animOffsetY) / 800f)).coerceIn(0f, 1f)
            val layerVisible = !(isSmushed || animOffsetY > 0f)
            val baseReflectionAlpha = if (layerVisible) 0.25f else 0f

            if (isDarkMode && baseReflectionAlpha > 0f) {
                drawReflection(true, floorY, baseReflectionAlpha) { isReflection ->
                    if (isReflection) {
                        if (showEgg) {
                            val eggWidth = 120f * d; val eggHeight = 150f * d
                            val eggTop = floorY - eggHeight
                            withTransform({ scale(1f, 1f, pivot = Offset(cx, eggTop + eggHeight/2)); rotate(if (!isPaused) eggWobbleRotation else 0f, pivot = Offset(cx, floorY)) }) {
                                drawOval(color = Color(0xFFFEF3C7), topLeft = Offset(cx - eggWidth/2, eggTop), size = Size(eggWidth, eggHeight))
                                withTransform({ rotate(-20f, pivot = Offset(cx - eggWidth * 0.2f, eggTop + eggHeight * 0.25f)) }) { drawOval(color = Color.White.copy(alpha = 0.4f), topLeft = Offset(cx - eggWidth * 0.3f, eggTop + eggHeight * 0.15f), size = Size(eggWidth * 0.3f, eggHeight * 0.15f)) }
                                val crackStroke = Stroke(width = 3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                withTransform({ translate(cx, 0f) }) { if (crackStage >= 1) drawPath(crack1Path, Color.Black, style = crackStroke); if (crackStage >= 2) drawPath(crack2Path, Color.Black, style = crackStroke); if (crackStage >= 3) drawPath(crack3Path, Color.Black, style = crackStroke) }
                            }
                        }
                        val henY = cy + animOffsetY
                        if (henY > -3000f && henY < size.height + 5000f && henSequenceElapsed < 2.5f) {
                            if (heightFade > 0.01f) {
                                val squashY = boingAnim.value; val stretchX = 2f - squashY
                                withTransform({ rotate(glassRotation, pivot = Offset(cx, henY)); scale(glassScale, glassScale, pivot = Offset(cx, henY)); scale(stretchX, squashY, pivot = Offset(cx, henY + henBodyRadius)) }) {
                                    drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), blendMode = BlendMode.Clear)
                                    drawIntoCanvas { it.nativeCanvas.saveLayerAlpha(0f, -size.height, size.width, size.height * 2f, (heightFade * 255).toInt()) }
                                    withTransform({ translate(cx, henY) }) { drawPath(combPath, NeonRed) }
                                    drawCircle(color = Color.White, radius = henBodyRadius, center = Offset(cx, henY))
                                    drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), style = Stroke(width = 4f * d))
                                    withTransform({ translate(cx, henY) }) { drawPath(wattlePath, NeonRed) }
                                    val relFaceEdgeX = henBodyRadius * 0.82f; val relBeakY = -6f * d
                                    withTransform({ translate(cx, henY); rotate(if (effectiveBeakOpen) -15f else 0f, pivot = Offset(relFaceEdgeX, relBeakY)) }) { drawPath(upperBeakPath, NeonOrange) }
                                    withTransform({ translate(cx, henY); rotate(if (effectiveBeakOpen) 10f else 0f, pivot = Offset(relFaceEdgeX, relBeakY)) }) { drawPath(lowerBeakPath, NeonOrange) }
                                    if (isSmushed) { withTransform({ translate(cx, henY) }) { drawPath(wincePath, Color.Black, style = Stroke(5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
                                    else if (isBlinking) { val eyeXRel = relFaceEdgeX - 25f * d; val eyeYRel = -25f * d; val eyeCenter = Offset(cx + eyeXRel, henY + eyeYRel); drawLine(color = Color.Black, start = Offset(eyeCenter.x - 12f * d, eyeCenter.y), end = Offset(eyeCenter.x + 12f * d, eyeCenter.y), strokeWidth = 5f * d, cap = StrokeCap.Round) }
                                    else { val eyeXRel = relFaceEdgeX - 25f * d; val eyeYRel = -25f * d; val eyeCenter = Offset(cx + eyeXRel, henY + eyeYRel); drawCircle(color = Color.Black, radius = 12f * d, center = eyeCenter); drawCircle(color = Color.White, radius = 4f * d, center = Offset(eyeCenter.x + 3f * d, eyeCenter.y - 3f * d)); drawCircle(color = Color.White, radius = 2f * d, center = Offset(eyeCenter.x - 3f * d, eyeCenter.y + 3f * d)) }
                                    val wingP = Offset(cx - 40f * d, henY + 10f * d); val wingRot = if (isFlapping) wingFlapRotation else if (isSliding) -20f else 0f
                                    withTransform({ rotate(wingRot, pivot = wingP) }) {
                                        val wW = 60f * d; val wH = 60f * d; val wTopLeft = Offset(wingP.x - 10f * d, wingP.y - 30f * d)
                                        drawArc(color = if(isSmushed) Color(0xFFE0E0E0) else Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = wTopLeft, size = Size(wW, wH))
                                        drawArc(color = Color.Black, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = wTopLeft, size = Size(wW, wH), style = Stroke(4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                    }
                                    drawIntoCanvas { it.nativeCanvas.restore() }
                                }
                            }
                        }
                    }
                }
            }

            // --- 2. REAL WORLD PASS ---
            if (!isDarkMode) {
                if (drawHenShadow && henShadowAlpha > 0f) {
                    val squashY = boingAnim.value
                    val stretchX = 2f - squashY
                    val shadowResponseFactor = 1.5f
                    val boingScale = 1f + (stretchX - 1f) * shadowResponseFactor

                    val hShadowW = henBodyRadius * 2.0f * henShadowScale * boingScale
                    val hShadowH = hShadowW * 0.2f

                    // --- NEW BLURRED SHADOW LOGIC ---
                    if (shadowBlurRadius > 0.5f) {
                        // Use native canvas to apply BlurMaskFilter when hen is high up
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint()
                            paint.color = Color.Black.copy(alpha = 0.3f * henShadowAlpha).toArgb()
                            // Blur Radius increases as she flies up
                            paint.maskFilter = android.graphics.BlurMaskFilter(shadowBlurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)

                            canvas.nativeCanvas.drawOval(
                                android.graphics.RectF(
                                    cx - hShadowW/2,
                                    floorY - hShadowH/2,
                                    cx + hShadowW/2,
                                    floorY + hShadowH/2
                                ),
                                paint
                            )
                        }
                    } else {
                        // Use standard optimized draw for sharp shadow (on ground)
                        drawOval(color = Color.Black.copy(alpha = 0.3f * henShadowAlpha), topLeft = Offset(cx - hShadowW/2, floorY - hShadowH/2), size = Size(hShadowW, hShadowH))
                    }
                }
                if (showEgg && eggShadowAlpha > 0f) {
                    val eShadowW = 120f * d
                    val eShadowH = eShadowW * 0.2f
                    val wobbleX = if (!isPaused) eggWobbleRotation * 1.0f else 0f
                    drawOval(color = Color.Black.copy(alpha = 0.2f * eggShadowAlpha), topLeft = Offset(cx - eShadowW/2 + wobbleX, floorY - eShadowH / 2), size = Size(eShadowW, eShadowH))
                }
            }

            // Egg
            if (showEgg && henSequenceElapsed > 0.0f) {
                val eggWidth = 120f * d; val eggHeight = 150f * d
                val eggTop = floorY - eggHeight
                withTransform({ scale(1f, 1f, pivot = Offset(cx, eggTop + eggHeight/2)); rotate(if (!isPaused) eggWobbleRotation else 0f, pivot = Offset(cx, floorY)) }) {
                    drawOval(color = Color(0xFFFEF3C7), topLeft = Offset(cx - eggWidth/2, eggTop), size = Size(eggWidth, eggHeight))
                    withTransform({ rotate(-20f, pivot = Offset(cx - eggWidth * 0.2f, eggTop + eggHeight * 0.25f)) }) { drawOval(color = Color.White.copy(alpha = 0.4f), topLeft = Offset(cx - eggWidth * 0.3f, eggTop + eggHeight * 0.15f), size = Size(eggWidth * 0.3f, eggHeight * 0.15f)) }
                    val crackStroke = Stroke(width = 3f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    withTransform({ translate(cx, 0f) }) { if (crackStage >= 1) drawPath(crack1Path, Color.Black, style = crackStroke); if (crackStage >= 2) drawPath(crack2Path, Color.Black, style = crackStroke); if (crackStage >= 3) drawPath(crack3Path, Color.Black, style = crackStroke) }
                }
            }

            // Hen
            val henY = cy + animOffsetY
            if (henY > -3000f && henY < size.height + 5000f) {
                val squashY = boingAnim.value; val stretchX = 2f - squashY
                withTransform({ rotate(glassRotation, pivot = Offset(cx, henY)); scale(glassScale, glassScale, pivot = Offset(cx, henY)); scale(stretchX, squashY, pivot = Offset(cx, henY + henBodyRadius)) }) {
                    withTransform({ translate(cx, henY) }) { drawPath(combPath, NeonRed) }
                    drawCircle(color = Color.White, radius = henBodyRadius, center = Offset(cx, henY))
                    if (isSmushed) drawCircle(color = Color(0xFFE0E0E0), radius = henBodyRadius * 0.75f, center = Offset(cx, henY))
                    drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), style = Stroke(width = 4f * d))
                    withTransform({ translate(cx, henY) }) { drawPath(wattlePath, NeonRed) }
                    val relFaceEdgeX = henBodyRadius * 0.82f; val relBeakY = -6f * d
                    withTransform({ translate(cx, henY); rotate(if (effectiveBeakOpen) -15f else 0f, pivot = Offset(relFaceEdgeX, relBeakY)) }) { drawPath(upperBeakPath, NeonOrange) }
                    withTransform({ translate(cx, henY); rotate(if (effectiveBeakOpen) 10f else 0f, pivot = Offset(relFaceEdgeX, relBeakY)) }) { drawPath(lowerBeakPath, NeonOrange) }

                    if (isSmushed) { withTransform({ translate(cx, henY) }) { drawPath(wincePath, Color.Black, style = Stroke(5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)) } }
                    else if (isBlinking) { val eyeXRel = relFaceEdgeX - 25f * d; val eyeYRel = -25f * d; val eyeCenter = Offset(cx + eyeXRel, henY + eyeYRel); drawLine(color = Color.Black, start = Offset(eyeCenter.x - 12f * d, eyeCenter.y), end = Offset(eyeCenter.x + 12f * d, eyeCenter.y), strokeWidth = 5f * d, cap = StrokeCap.Round) }
                    else { val eyeXRel = relFaceEdgeX - 25f * d; val eyeYRel = -25f * d; val eyeCenter = Offset(cx + eyeXRel, henY + eyeYRel); drawCircle(color = Color.Black, radius = 12f * d, center = eyeCenter); drawCircle(color = Color.White, radius = 4f * d, center = Offset(eyeCenter.x + 3f * d, eyeCenter.y - 3f * d)); drawCircle(color = Color.White, radius = 2f * d, center = Offset(eyeCenter.x - 3f * d, eyeCenter.y + 3f * d)) }

                    val wingP = Offset(cx - 40f * d, henY + 10f * d); val wingRot = if (isFlapping) wingFlapRotation else if (isSliding) -20f else 0f
                    withTransform({ rotate(wingRot, pivot = wingP) }) {
                        val wW = 60f * d; val wH = 60f * d; val wTopLeft = Offset(wingP.x - 10f * d, wingP.y - 30f * d)
                        drawArc(color = if(isSmushed) Color(0xFFE0E0E0) else Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = wTopLeft, size = Size(wW, wH))
                        drawArc(color = Color.Black, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = wTopLeft, size = Size(wW, wH), style = Stroke(4f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

// Linear Interpolation for Floats
@Suppress("SpellCheckingInspection")
fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

// Linear Interpolation for Colors (already exists in Compose, but good to have if needed)
@Suppress("SpellCheckingInspection")
fun lerp(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = lerp(start.red, stop.red, f),
        green = lerp(start.green, stop.green, f),
        blue = lerp(start.blue, stop.blue, f),
        alpha = lerp(start.alpha, stop.alpha, f)
    )
}