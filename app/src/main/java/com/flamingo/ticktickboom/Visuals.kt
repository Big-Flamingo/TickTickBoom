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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlin.random.Random

// --- PARTICLE CLASSES ---
data class FrogSweatParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float
)

data class VisualTextEffect(
    val text: String,
    val x: Float,
    val y: Float,
    val color: Color,
    val gradientColors: List<Color>? = null,
    val alpha: Float = 1f,
    val life: Float = 1.0f,
    val fontSize: Float
)

@Composable
fun FuseVisual(progress: Float, isCritical: Boolean, colors: AppColors, isPaused: Boolean, onTogglePause: () -> Unit) {
    val sparks = remember { mutableListOf<Spark>() }
    val smokePuffs = remember { mutableListOf<SmokeParticle>() }
    var frame by remember { mutableLongStateOf(0L) }
    var lastFrameTime = remember { 0L }

    val infiniteTransition = rememberInfiniteTransition("glint")
    val glintScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glint"
    )

    val density = LocalDensity.current

    val fuseYOffset = with(density) { 5.dp.toPx() }
    val protrusionW = with(density) { 16.dp.toPx() }
    val protrusionH = with(density) { 8.dp.toPx() }
    val connectorH = with(density) { 15.dp.toPx() }
    val cornerRad = with(density) { 4.dp.toPx() }
    val cornerRadSmall = with(density) { 2.dp.toPx() }
    val fuseHoleRad = with(density) { 4.dp.toPx() }
    val strokeW = with(density) { 6.dp.toPx() }
    val rimStrokeW = with(density) { 2.dp.toPx() }
    val rimHighlightStrokeW = with(density) { 1.dp.toPx() }
    val neckGap = with(density) { 2.dp.toPx() }
    val holeOffset = with(density) { 10.dp.toPx() }
    val glintSizeL = with(density) { 24.dp.toPx() }
    val glintSizeS = with(density) { 4.dp.toPx() }
    val glintOffsetL = with(density) { 12.dp.toPx() }
    val glintOffsetS = with(density) { 2.dp.toPx() }
    val glowRadius = with(density) { 25.dp.toPx() }
    val coreRadius = with(density) { 8.dp.toPx() }
    val whiteRadius = with(density) { 4.dp.toPx() }
    val particleRad = with(density) { 3.dp.toPx() }
    val particleRadS = with(density) { 1.5.dp.toPx() }

    LaunchedEffect(isPaused) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else (nanos - lastFrameTime) / 1_000_000_000f
                lastFrameTime = nanos

                val sparkIter = sparks.iterator()
                while (sparkIter.hasNext()) {
                    val spark = sparkIter.next()
                    spark.life -= dt
                    spark.x += spark.vx * dt * 100
                    spark.y += spark.vy * dt * 100 + (9.8f * dt * dt * 50)
                    if (spark.life <= 0) sparkIter.remove()
                }

                val smokeIter = smokePuffs.iterator()
                while (smokeIter.hasNext()) {
                    val puff = smokeIter.next()
                    puff.life -= dt
                    if (puff.life <= 0) smokeIter.remove()
                    else {
                        puff.x += puff.vx * dt * 50 + (Math.random() - 0.5f).toFloat() * 10f * dt
                        puff.y += puff.vy * dt * 50
                        val p = 1f - (puff.life / puff.maxLife)
                        puff.size = 10f + p * 20f
                        puff.alpha = (1f - p).coerceIn(0f, 0.6f)
                    }
                }
                frame++
            }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(300.dp)) {
        var currentSparkCenter by remember { mutableStateOf(Offset.Zero) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val dx = tapOffset.x - currentSparkCenter.x
                        val dy = tapOffset.y - currentSparkCenter.y
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < 60.dp.toPx()) {
                            onTogglePause()
                        }
                    }
                }
        ) {
            if (frame >= 0) Unit

            val width = size.width
            val height = size.height
            val bombCenterX = width / 2
            val bombCenterY = height * 0.6f
            val bodyRadius = width * 0.28f
            val neckTopY = bombCenterY - bodyRadius - holeOffset

            val shadowW = width * 0.6f
            val shadowH = 20.dp.toPx()
            val shadowCenterY = bombCenterY + bodyRadius

            drawOval(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = Offset(bombCenterX - shadowW / 2, shadowCenterY - shadowH / 2),
                size = Size(shadowW, shadowH)
            )

            drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF475569), Slate950), center = Offset(bombCenterX - 20, bombCenterY - 20), radius = width * 0.35f), radius = bodyRadius, center = Offset(bombCenterX, bombCenterY))
            val specularCenter = Offset(bombCenterX - bodyRadius * 0.4f, bombCenterY - bodyRadius * 0.4f)
            drawCircle(brush = Brush.radialGradient(colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent), center = specularCenter, radius = bodyRadius * 0.3f), radius = bodyRadius * 0.3f, center = specularCenter)

            val protrusionTopY = neckTopY - protrusionH + neckGap
            drawRoundRect(color = Slate800, topLeft = Offset(bombCenterX - protrusionW / 2, protrusionTopY + protrusionH - 1f), size = Size(protrusionW, connectorH), cornerRadius = CornerRadius(cornerRad, cornerRad))
            drawRoundRect(color = Slate800, topLeft = Offset(bombCenterX - protrusionW / 2, protrusionTopY), size = Size(protrusionW, protrusionH), cornerRadius = CornerRadius(cornerRadSmall, cornerRadSmall))
            val rimRect = androidx.compose.ui.geometry.Rect(offset = Offset(bombCenterX - protrusionW / 2, protrusionTopY - neckGap), size = Size(protrusionW, 4.dp.toPx()))
            drawOval(color = Color(0xFF64748B), topLeft = rimRect.topLeft, size = rimRect.size)

            val fuseBase = Offset(bombCenterX, protrusionTopY)

            if (isCritical && !isPaused) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonRed.copy(alpha=0.6f), NeonRed.copy(alpha=0f)),
                        center = fuseBase,
                        radius = 20.dp.toPx()
                    ),
                    radius = 20.dp.toPx(),
                    center = fuseBase
                )
                drawCircle(color = Color.Black, radius = fuseHoleRad, center = fuseBase)
            } else {
                drawCircle(color = Color(0xFF0F172A), radius = fuseHoleRad, center = fuseBase)
            }

            val path = Path().apply {
                moveTo(fuseBase.x, fuseBase.y)
                quadraticTo(width * 0.6f, height * 0.1f, width * 0.75f, height * 0.15f)
                quadraticTo(width * 0.85f, height * 0.2f, width * 0.8f, height * 0.3f)
            }

            val androidMeasure = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val length = androidMeasure.length
            val effectiveProgress = if (isCritical) 1f else progress
            val currentBurnPoint = length * (1f - effectiveProgress)

            if (!isCritical) {
                val androidSegmentPath = android.graphics.Path()
                androidMeasure.getSegment(0f, currentBurnPoint, androidSegmentPath, true)
                drawPath(path = androidSegmentPath.asComposePath(), color = Color(0xFFD6D3D1), style = Stroke(width = strokeW, cap = StrokeCap.Round))
            }

            drawArc(color = Color(0xFF64748B), startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = rimRect.topLeft, size = rimRect.size, style = Stroke(width = rimStrokeW))
            drawArc(color = Color(0xFF94A3B8), startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = rimRect.topLeft, size = rimRect.size, style = Stroke(width = rimHighlightStrokeW))

            val pos = floatArrayOf(0f, 0f)
            androidMeasure.getPosTan(currentBurnPoint, pos, null)
            val sparkCenter = Offset(pos[0], pos[1])

            currentSparkCenter = sparkCenter

            if (!isPaused) {
                if (Math.random() < 0.3) {
                    val angle = Math.random() * Math.PI * 2
                    val speed = (2f + Math.random() * 4f).toFloat()
                    val sx = if (isCritical) fuseBase.x else sparkCenter.x
                    val sy = if (isCritical) fuseBase.y else sparkCenter.y
                    sparks.add(Spark(x = sx, y = sy, vx = cos(angle).toFloat() * speed, vy = sin(angle).toFloat() * speed - 2f, life = (0.2f + Math.random() * 0.3f).toFloat(), maxLife = 0.5f))
                }
                if (Math.random() < 0.2) {
                    val angle = -Math.PI / 2 + (Math.random() - 0.5) * 0.5
                    val speed = (1f + Math.random() * 2f).toFloat()
                    val sx = if (isCritical) fuseBase.x else sparkCenter.x
                    val sy = if (isCritical) fuseBase.y else sparkCenter.y
                    val smokeVy = if (isCritical) sin(angle).toFloat() * speed * 2f else sin(angle).toFloat() * speed
                    smokePuffs.add(SmokeParticle(x = sx, y = sy - fuseYOffset, vx = cos(angle).toFloat() * speed, vy = smokeVy, size = 10f, alpha = 0.6f, life = (1f + Math.random().toFloat() * 0.5f), maxLife = 1.5f))
                }
            }

            smokePuffs.forEach { puff -> drawCircle(color = colors.smokeColor.copy(alpha = puff.alpha), radius = puff.size, center = Offset(puff.x, puff.y)) }
            sparks.forEach { spark ->
                val alpha = (spark.life / spark.maxLife).coerceIn(0f, 1f)
                drawCircle(color = NeonOrange.copy(alpha = alpha), radius = particleRad * alpha, center = Offset(spark.x, spark.y))
                drawCircle(color = Color.Yellow.copy(alpha = alpha), radius = particleRadS * alpha, center = Offset(spark.x, spark.y))
            }

            if (!isCritical && !isPaused) {
                drawCircle(brush = Brush.radialGradient(colors = listOf(NeonOrange.copy(alpha = 0.5f), Color.Transparent), center = sparkCenter, radius = glowRadius), radius = glowRadius, center = sparkCenter)
                drawCircle(color = NeonOrange.copy(alpha=0.8f), radius = coreRadius, center = sparkCenter)
                drawCircle(color = Color.White, radius = whiteRadius, center = sparkCenter)

                withTransform({
                    rotate(45f, pivot = sparkCenter)
                    scale(scaleX = glintScale, scaleY = glintScale, pivot = sparkCenter)
                }) {
                    drawOval(color = Color.White.copy(alpha=0.8f), topLeft = Offset(sparkCenter.x - glintOffsetL, sparkCenter.y - glintOffsetS), size = Size(glintSizeL, glintSizeS))
                }
                withTransform({
                    rotate(-45f, pivot = sparkCenter)
                    scale(scaleX = glintScale, scaleY = glintScale, pivot = sparkCenter)
                }) {
                    drawOval(color = Color.White.copy(alpha=0.8f), topLeft = Offset(sparkCenter.x - glintOffsetL, sparkCenter.y - glintOffsetS), size = Size(glintSizeL, glintSizeS))
                }
            }
        }
    }
}

@Composable
fun C4Visual(isLedOn: Boolean, isDarkMode: Boolean, isPaused: Boolean, onTogglePause: () -> Unit) {
    val density = LocalDensity.current
    val ledSize = with(density) { 12.dp.toPx() }
    val ledRadius = with(density) { 6.dp.toPx() }

    val c4BodyColor = if (isDarkMode) Color(0xFFD6D0C4) else Color(0xFFC8C2B4)
    val c4BlockColor = if (isDarkMode) Color(0xFFC7C1B3) else Color(0xFFB9B3A5)
    val c4BorderColor = if (isDarkMode) Color(0xFF9E9889) else Color(0xFF8C8677)
    val c4TapeColor = Color.Black

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.width(320.dp).height(200.dp).background(c4BodyColor, RoundedCornerShape(4.dp))) {
            Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                for(i in 0..2) Box(Modifier.width(80.dp).fillMaxHeight().background(c4BlockColor).border(1.dp, c4BorderColor))
            }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                Box(Modifier.fillMaxWidth().height(24.dp).background(c4TapeColor))
                Box(Modifier.fillMaxWidth().height(24.dp).background(c4TapeColor))
            }
            Box(modifier = Modifier.align(Alignment.Center).width(280.dp).height(140.dp).background(C4ScreenBg, RoundedCornerShape(8.dp)).border(4.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))) {
                Canvas(modifier = Modifier.fillMaxSize().alpha(0.2f)) {
                    val step = 20.dp.toPx()
                    for (x in 0..size.width.toInt() step step.toInt()) drawLine(NeonCyan, Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 2f)
                    for (y in 0..size.height.toInt() step step.toInt()) drawLine(NeonCyan, Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 2f)
                }
                Column(Modifier.fillMaxSize().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(0.6f).fillMaxWidth().padding(horizontal = 16.dp).background(LcdDarkBackground, RoundedCornerShape(4.dp)).border(2.dp, Slate800, RoundedCornerShape(4.dp))) {
                        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onTogglePause() }
                                    .padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.DeveloperBoard,
                                    null,
                                    tint = if (isPaused) Color(0xFF3B82F6) else Color(0xFF64748B),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "PAUSED",
                                    color = if (isPaused) Color(0xFF3B82F6) else Color(0xFF1E293B),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = CustomFont,
                                    style = TextStyle(
                                        shadow = if (isPaused) Shadow(color = Color(0xFF3B82F6), blurRadius = 20f) else Shadow.None
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))
                            Text("--:--", color = NeonRed, fontSize = 56.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont, letterSpacing = (-1).sp, modifier = Modifier.padding(bottom = 6.dp).offset(y = (-5).dp), style = TextStyle(shadow = Shadow(color = NeonRed, offset = Offset.Zero, blurRadius = 12f)))
                        }
                    }
                    Box(Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(y = 5.dp)) {
                            Canvas(modifier = Modifier.size(12.dp)) {
                                if (isLedOn) drawCircle(brush = Brush.radialGradient(colors = listOf(NeonRed.copy(alpha=0.8f), Color.Transparent), center = center, radius = ledSize), radius = ledSize)
                                drawCircle(color = if (isLedOn) NeonRed else Color(0xFF450a0a), radius = ledRadius)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ARMED", color = NeonRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont, style = if (isLedOn) TextStyle(shadow = Shadow(color = NeonRed, blurRadius = 8f)) else LocalTextStyle.current)
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
                Text("HIGH VOLTAGE // DO NOT TAMPER", color = Color(0xFFF59E0B), fontSize = 10.sp, fontFamily = CustomFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DynamiteVisual(timeLeft: Float, isPaused: Boolean, onTogglePause: () -> Unit) {
    val density = LocalDensity.current
    val stickW = with(density) { 60.dp.toPx() }
    val stickH = with(density) { 220.dp.toPx() }
    val spacing = with(density) { 5.dp.toPx() }
    val cornerRad = with(density) { 4.dp.toPx() }
    val tapeH = with(density) { 30.dp.toPx() }
    val tapeOver = with(density) { 20.dp.toPx() }
    val clockRad = with(density) { 80.dp.toPx() }
    val clockStroke = with(density) { 8.dp.toPx() }
    val tickL = with(density) { 12.dp.toPx() }
    val tickS = with(density) { 6.dp.toPx() }
    val tickWidthLong = with(density) { 3.dp.toPx() }
    val tickWidthShort = with(density) { 1.dp.toPx() }
    val tickGap = with(density) { 10.dp.toPx() }
    val handL = with(density) { 50.dp.toPx() }
    val handS = with(density) { 40.dp.toPx() }
    val pinL = with(density) { 4.dp.toPx() }
    val pinS = with(density) { 2.dp.toPx() }
    val textYOffset = with(density) { 25.dp.toPx() }
    val stickTextYOffset = with(density) { 4.dp.toPx() }

    val textMeasurer = rememberTextMeasurer()
    val textLayoutResult = textMeasurer.measure(
        text = "ACME CORP",
        style = TextStyle(color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
    )
    val stickTextResult = textMeasurer.measure(
        text = "HIGH EXPLOSIVE",
        style = TextStyle(color = Color.Black.copy(alpha=0.3f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont)
    )

    val textEffectsSaver = listSaver<SnapshotStateList<VisualTextEffect>, List<Any>>(
        save = { stateList ->
            stateList.map { effect ->
                listOf(
                    effect.text,
                    effect.x,
                    effect.y,
                    effect.color.toArgb(),
                    effect.gradientColors?.map { it.toArgb() } ?: emptyList<Int>(),
                    effect.alpha,
                    effect.life,
                    effect.fontSize
                )
            }
        },
        restore = { savedList ->
            val mutableList = mutableStateListOf<VisualTextEffect>()
            savedList.forEach { item ->
                @Suppress("UNCHECKED_CAST")
                val props = item as List<Any>
                @Suppress("UNCHECKED_CAST")
                val gradInts = props[4] as List<Int>

                mutableList.add(VisualTextEffect(
                    text = props[0] as String,
                    x = (props[1] as Number).toFloat(),
                    y = (props[2] as Number).toFloat(),
                    color = Color(props[3] as Int),
                    gradientColors = if (gradInts.isEmpty()) null else gradInts.map { Color(it) },
                    alpha = (props[5] as Number).toFloat(),
                    life = (props[6] as Number).toFloat(),
                    fontSize = (props[7] as Number).toFloat()
                ))
            }
            mutableList
        }
    )

    val textEffects = rememberSaveable(saver = textEffectsSaver) { mutableStateListOf<VisualTextEffect>() }

    var lastTriggerStep by rememberSaveable { mutableIntStateOf(-1) }
    var hasShownDing by rememberSaveable { mutableStateOf(false) }

    val tickOffsetY = with(density) { -130.dp.toPx() }
    val dingOffsetY = with(density) { -160.dp.toPx() }

    LaunchedEffect(timeLeft) {
        val currentStep = ceil(timeLeft * 2).toInt()
        val isFast = timeLeft <= 5f

        val stepChanged = currentStep != lastTriggerStep
        val shouldTrigger = stepChanged && (isFast || currentStep % 2 == 0)

        if (stepChanged) {
            if (shouldTrigger && timeLeft > 1.1f && !isPaused) {
                textEffects.add(VisualTextEffect(
                    text = "TICK",
                    x = (Math.random() * 100 - 50).toFloat(),
                    y = tickOffsetY,
                    color = if (isFast) NeonRed else TextGray,
                    fontSize = 20f
                ))
            }
            lastTriggerStep = currentStep
        }

        if (timeLeft <= 1.0f && !hasShownDing && timeLeft > 0 && !isPaused) {
            textEffects.add(VisualTextEffect(
                text = "DING!",
                x = 0f,
                y = dingOffsetY,
                color = Color(0xFFFFD700),
                gradientColors = listOf(Color(0xFFFFFACD), Color(0xFFFFD700)),
                life = 2.0f,
                fontSize = 48f
            ))
            hasShownDing = true
        }
    }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while(true) {
            withFrameNanos { nanos ->
                val dt = if (lastTime == 0L) 0.016f else (nanos - lastTime) / 1_000_000_000f
                lastTime = nanos

                val iter = textEffects.listIterator()
                while (iter.hasNext()) {
                    val effect = iter.next()
                    val newLife = effect.life - dt

                    if (newLife <= 0) {
                        iter.remove()
                    } else {
                        val newY = effect.y - (15f * dt)
                        val newAlpha = (newLife / 1.0f).coerceIn(0f, 1f)

                        iter.set(effect.copy(
                            y = newY,
                            life = newLife,
                            alpha = newAlpha
                        ))
                    }
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
                drawRoundRect(brush = stickBrush, topLeft = Offset(stickLeft, startY), size = Size(stickW, stickH + 30.dp.toPx()), cornerRadius = CornerRadius(cornerRad, cornerRad))

                val stickCenterX = stickLeft + stickW / 2
                val stickCenterY = startY + (stickH / 2)
                withTransform({ rotate(-90f, pivot = Offset(stickCenterX, stickCenterY)); translate(left = stickCenterX - stickTextResult.size.width / 2, top = stickCenterY - stickTextResult.size.height / 2 + stickTextYOffset) }) {
                    drawText(stickTextResult)
                }
            }
            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + 40), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
            drawRect(color = Color.Black.copy(alpha=0.9f), topLeft = Offset(startX - tapeOver, startY + stickH - 40), size = Size(totalSticksWidth + (tapeOver * 2), tapeH))
        }

        Box(
            modifier = Modifier
                .offset(y = 15.dp)
                .size(160.dp)
                .clip(CircleShape)
                .clickable { onTogglePause() }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val clockCenter = center
                drawCircle(brush = Brush.radialGradient(colors = listOf(MetallicLight, MetallicDark), center = clockCenter, radius = clockRad), center = clockCenter, radius = clockRad)
                drawCircle(color = Slate800, style = Stroke(width = clockStroke), center = clockCenter)
                drawText(textLayoutResult = textLayoutResult, topLeft = Offset(clockCenter.x - textLayoutResult.size.width / 2, clockCenter.y + textYOffset))
                for (index in 0 until 60) {
                    val isHour = index % 5 == 0
                    val length = if (isHour) tickL else tickS
                    val width = if (isHour) tickWidthLong else tickWidthShort
                    rotate(index * 6f, pivot = clockCenter) {
                        drawLine(color = Slate800, start = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap), end = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap + length), strokeWidth = width)
                    }
                }
                val secondAngle = (timeLeft % 60) * 6f
                val minuteAngle = (timeLeft / 60) * 6f
                rotate(-minuteAngle, pivot = clockCenter) { drawLine(color = Slate800, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handS), strokeWidth = 8f, cap = StrokeCap.Round) }
                rotate(-secondAngle, pivot = clockCenter) { drawLine(color = NeonRed, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handL), strokeWidth = 4f, cap = StrokeCap.Round) }
                drawCircle(color = Slate800, radius = pinL, center = clockCenter)
                drawCircle(color = NeonRed, radius = pinS, center = clockCenter)
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2

            textEffects.forEach { effect ->
                val style = if (effect.gradientColors != null) {
                    val fadedColors = effect.gradientColors.map { it.copy(alpha = effect.alpha) }
                    TextStyle(
                        brush = Brush.verticalGradient(fadedColors),
                        fontSize = effect.fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CustomFont
                    )
                } else {
                    TextStyle(
                        color = effect.color.copy(alpha = effect.alpha),
                        fontSize = effect.fontSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = CustomFont
                    )
                }

                val textResult = textMeasurer.measure(
                    text = effect.text,
                    style = style
                )

                val drawX = cx + effect.x - (textResult.size.width / 2)
                val drawY = cy + effect.y - (textResult.size.height / 2)

                drawText(
                    textLayoutResult = textResult,
                    topLeft = Offset(drawX, drawY)
                )
            }
        }
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

    var hasPlayedExplosion by rememberSaveable { mutableStateOf(false) }
    val animationProgress = remember { Animatable(if (hasPlayedExplosion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayedExplosion) {
            AudioService.playExplosion(context)
            launch { animationProgress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) }
            hasPlayedExplosion = true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF431407)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99DC2626)))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = animationProgress.value
            val center = if (explosionOrigin != null && explosionOrigin != Offset.Zero) explosionOrigin else Offset(size.width / 2, size.height / 2)

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

        val flashAlpha = (1f - (animationProgress.value * 5)).coerceIn(0f, 1f)
        if (flashAlpha > 0f) Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = flashAlpha)))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val titleText = if (style == "FROG") "CROAKED" else "BOOM"
            val titleSize = if (style == "FROG") 72.sp else 96.sp
            Text(titleText, fontSize = titleSize, fontWeight = FontWeight.Black, style = TextStyle(brush = Brush.verticalGradient(listOf(Color.Yellow, NeonRed)), shadow = Shadow(color = NeonOrange, blurRadius = 40f)), fontFamily = CustomFont)
            Spacer(modifier = Modifier.height(80.dp))

            ActionButton(
                text = "RESTART",
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                onClick = {
                    AudioService.playClick()
                    onReset()
                }
            )
        }
    }
}

@Composable
fun FrogVisual(timeLeft: Float, isCritical: Boolean, isPaused: Boolean, onTogglePause: () -> Unit) {

    val isPanic = timeLeft <= 1.05f

    val tickDuration = if (timeLeft <= 5f) 0.5f else 1.0f

    val currentProgress = if (isPaused) 0f else (timeLeft % tickDuration) / tickDuration
    val tickProgress = 1f - currentProgress

    val bellyHeightScale by animateFloatAsState(
        targetValue = if (tickProgress < 0.2f && !isPaused) 1.15f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "croak"
    )

    val infiniteTransition = rememberInfiniteTransition("flail")
    val flailRotation by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flail"
    )

    val alertScale = remember { Animatable(0f) }
    LaunchedEffect(isPanic) {
        if (isPanic) {
            alertScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
            )
        } else {
            alertScale.snapTo(0f)
        }
    }

    // --- BOING ANIMATION SETUP ---
    val scope = rememberCoroutineScope()
    val boingAnim = remember { Animatable(1f) }

    val sweatDrops = remember { mutableListOf<FrogSweatParticle>() }
    var frame by remember { mutableLongStateOf(0L) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }

    var timeAccumulator by remember { mutableFloatStateOf(0.5f) }
    val currentIsCritical by rememberUpdatedState(isCritical)

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else ((nanos - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.1f)
                lastFrameTime = nanos

                if (currentIsCritical) {
                    timeAccumulator += dt
                }

                val iter = sweatDrops.iterator()
                while (iter.hasNext()) {
                    val p = iter.next()
                    p.life -= dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    p.vy += 500f * dt
                    if (p.life <= 0) iter.remove()
                }

                frame++
            }
        }
    }

    // Blink Animation
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2000, 4000))
            isBlinking = true
            delay(150)
            isBlinking = false
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(300.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // TRIGGER BOING
                scope.launch {
                    boingAnim.snapTo(1f)
                    boingAnim.animateTo(0.9f, tween(50))
                    boingAnim.animateTo(1.05f, tween(100))
                    boingAnim.animateTo(1.0f, spring(dampingRatio = 0.4f, stiffness = 400f))
                }
                onTogglePause()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (frame >= 0) Unit

            val cx = size.width / 2
            val cy = size.height / 2
            val bodyRadius = size.width * 0.35f

            // --- PASTEL THEME COLORS (Reverted from Neon) ---
            val frogGreen = FrogBody
            val bellyGreen = FrogBelly

            // --- SHADOW (Static floor shadow) ---
            val shadowWidth = bodyRadius * 2.2f
            val shadowHeight = bodyRadius * 0.4f
            val floorY = cy + bodyRadius * 1.05f
            val shadowY = floorY

            drawOval(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = Offset(cx - shadowWidth / 2, shadowY - shadowHeight / 2),
                size = Size(shadowWidth, shadowHeight)
            )

            // --- FROG BODY (Applied Boing) ---
            val squashY = boingAnim.value
            val stretchX = 2f - squashY
            val pivotY = cy + bodyRadius

            withTransform({
                scale(stretchX, squashY, pivot = Offset(cx, pivotY))
            }) {
                val mainRadius = bodyRadius
                val bumpRadius = mainRadius * 0.45f
                val bumpY = cy - mainRadius * 0.65f
                val bumpXOffset = mainRadius * 0.5f

                val rightBumpCenterX = cx + bumpXOffset
                val rightBumpCenterY = bumpY

                val spawnX = rightBumpCenterX + (bumpRadius * 1.05f)
                val spawnY = rightBumpCenterY - (bumpRadius * 1.15f)

                if (currentIsCritical && timeAccumulator >= 0.5f) {
                    timeAccumulator -= 0.5f

                    repeat(3) { i ->
                        val baseAngle = -PI / 4
                        val spreadOffset = (i - 1) * 0.5
                        val angle = baseAngle + spreadOffset + ((Math.random() - 0.5) * 0.1)

                        val lifeSpan = 0.5f
                        val targetDist = bumpRadius * 1.0f
                        val speed = (targetDist / lifeSpan) * (0.9f + Math.random().toFloat() * 0.2f)

                        sweatDrops.add(FrogSweatParticle(
                            x = spawnX,
                            y = spawnY,
                            vx = (cos(angle) * speed).toFloat(),
                            vy = (sin(angle) * speed).toFloat(),
                            life = lifeSpan,
                            maxLife = lifeSpan
                        ))
                    }
                }

                val outlineColor = Color.Black
                val outlineStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                val footRadius = mainRadius * 0.2f
                val footY = cy + mainRadius * 0.85f
                val leftFootX = cx - mainRadius * 0.5f
                val rightFootX = cx + mainRadius * 0.5f

                drawCircle(outlineColor, radius = footRadius, center = Offset(leftFootX, footY), style = outlineStroke)
                drawCircle(frogGreen, radius = footRadius, center = Offset(leftFootX, footY))

                drawCircle(outlineColor, radius = footRadius, center = Offset(rightFootX, footY), style = outlineStroke)
                drawCircle(frogGreen, radius = footRadius, center = Offset(rightFootX, footY))

                val bodyCircle = Path().apply { addOval(Rect(center = Offset(cx, cy), radius = mainRadius)) }
                val leftBump = Path().apply { addOval(Rect(center = Offset(cx - bumpXOffset, bumpY), radius = bumpRadius)) }
                val rightBump = Path().apply { addOval(Rect(center = Offset(cx + bumpXOffset, bumpY), radius = bumpRadius)) }
                val silhouette = Path()
                silhouette.op(bodyCircle, leftBump, PathOperation.Union)
                silhouette.op(silhouette, rightBump, PathOperation.Union)

                drawPath(silhouette, outlineColor, style = outlineStroke)
                drawPath(silhouette, frogGreen)

                val currentBellyHeight = mainRadius * 1.0f * bellyHeightScale
                val bellyWidth = mainRadius * 1.4f
                drawOval(color = bellyGreen, topLeft = Offset(cx - bellyWidth / 2, cy - (mainRadius * 1.0f) * 0.05f), size = Size(bellyWidth, currentBellyHeight * 0.9f))
                drawOval(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(cx - bellyWidth * 0.3f, cy + (mainRadius * 1.0f) * 0.1f), size = Size(bellyWidth * 0.2f, currentBellyHeight * 0.15f))

                val armWidth = mainRadius * 0.25f
                val armHeight = mainRadius * 0.35f
                val armY = cy + mainRadius * 0.2f
                val armXOffset = mainRadius * 0.65f

                val leftArmRot = if (isPanic) flailRotation else 30f
                val rightArmRot = if (isPanic) -flailRotation else -30f

                rotate(leftArmRot, pivot = Offset(cx - armXOffset, armY)) {
                    val arcTopLeft = Offset(cx - armXOffset - armWidth, armY - armHeight/2)
                    drawArc(color = outlineColor, startAngle = -90f, sweepAngle = 270f, useCenter = false, topLeft = arcTopLeft, size = Size(armWidth*2, armHeight), style = outlineStroke)
                    drawOval(frogGreen, topLeft = Offset(cx - armXOffset - armWidth, armY - armHeight/2), size = Size(armWidth*2, armHeight))
                }
                rotate(rightArmRot, pivot = Offset(cx + armXOffset, armY)) {
                    val arcTopLeft = Offset(cx + armXOffset - armWidth, armY - armHeight/2)
                    drawArc(color = outlineColor, startAngle = -90f, sweepAngle = -270f, useCenter = false, topLeft = arcTopLeft, size = Size(armWidth*2, armHeight), style = outlineStroke)
                    drawOval(frogGreen, topLeft = Offset(cx + armXOffset - armWidth, armY - armHeight/2), size = Size(armWidth*2, armHeight))
                }

                val eyeWhiteRadius = bumpRadius * 0.7f
                val pupilRadius = eyeWhiteRadius * 0.9f
                val glintRadius = pupilRadius * 0.25f

                fun drawEye(centerX: Float, centerY: Float, isLeft: Boolean) {
                    drawCircle(outlineColor, radius = eyeWhiteRadius, center = Offset(centerX, centerY), style = outlineStroke)
                    drawCircle(Color.White, radius = eyeWhiteRadius, center = Offset(centerX, centerY))
                    if (isPanic) { /* Empty Eyes */ }
                    else if (isCritical) {
                        val size = eyeWhiteRadius * 1.2f
                        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        val path = Path()
                        val offset = size * 0.15f
                        if (isLeft) {
                            val eyeX = centerX + offset
                            path.moveTo(eyeX - size/2, centerY - size/2)
                            path.lineTo(eyeX + size/4, centerY)
                            path.lineTo(eyeX - size/2, centerY + size/2)
                        } else {
                            val eyeX = centerX - offset
                            path.moveTo(eyeX + size/2, centerY - size/2)
                            path.lineTo(eyeX - size/4, centerY)
                            path.lineTo(eyeX + size/2, centerY + size/2)
                        }
                        drawPath(path, Color.Black, style = stroke)
                    } else {
                        // Regular Eyes: Changed logic to ONLY close if isBlinking is true
                        // Removed "|| isPaused" so eyes stay open (but blink) when paused
                        if (isBlinking) {
                            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(color = Color.Black, start = Offset(centerX - 15.dp.toPx(), centerY), end = Offset(centerX + 15.dp.toPx(), centerY), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                        } else {
                            drawCircle(Color.Black, radius = pupilRadius, center = Offset(centerX, centerY))
                            drawCircle(Color.White, radius = glintRadius, center = Offset(centerX - pupilRadius*0.4f, centerY - pupilRadius*0.4f))
                        }
                    }
                }
                drawEye(cx - bumpXOffset, bumpY, true)
                drawEye(cx + bumpXOffset, bumpY, false)

                // --- BLUSHES (Solid & Lower) ---
                val cheekW = mainRadius * 0.35f
                val cheekH = mainRadius * 0.22f
                val cheekY = cy - mainRadius * 0.22f
                val cheekXOffset = mainRadius * 0.65f

                // FIX: Specific user hex #fad4ca (Soft Peach)
                val blushColor = Color(0xFFFAD4CA)

                // Left Cheek
                drawOval(
                    color = blushColor,
                    topLeft = Offset(cx - cheekXOffset - cheekW/2, cheekY - cheekH/2),
                    size = Size(cheekW, cheekH)
                )

                // Right Cheek
                drawOval(
                    color = blushColor,
                    topLeft = Offset(cx + cheekXOffset - cheekW/2, cheekY - cheekH/2),
                    size = Size(cheekW, cheekH)
                )

                val mouthWidth = if (isCritical) mainRadius * 0.2f else mainRadius * 0.3f
                val mouthY = cy - mainRadius * 0.22f

                if (isPanic) {
                    val path = Path().apply { moveTo(cx - mouthWidth/2, mouthY + 10f); lineTo(cx, mouthY - 10f); lineTo(cx + mouthWidth/2, mouthY + 10f) }
                    drawPath(path, Color.Black, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                } else if (isCritical) {
                    drawCircle(Color.Black, radius = 6.dp.toPx(), center = Offset(cx, mouthY))
                } else {
                    val mouthH = mainRadius * 0.08f
                    val mouthPath = Path().apply {
                        moveTo(cx - mouthWidth/2, mouthY)
                        quadraticTo(cx - mouthWidth/4, mouthY + mouthH, cx, mouthY)
                        quadraticTo(cx + mouthWidth/4, mouthY + mouthH, cx + mouthWidth/2, mouthY)
                    }
                    drawPath(path = mouthPath, color = Color.Black, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                if (alertScale.value > 0f) {
                    val markCenter = Offset(cx, bumpY - bumpRadius * 1.8f)
                    val markW = mainRadius * 0.15f
                    val markH = mainRadius * 0.6f

                    withTransform({
                        scale(scaleX = alertScale.value, scaleY = alertScale.value, pivot = Offset(markCenter.x, markCenter.y + markH/2))
                    }) {
                        val neonRed = Color(0xFFFF0000)
                        drawRoundRect(
                            color = neonRed,
                            topLeft = Offset(markCenter.x - markW/2, markCenter.y - markH/2),
                            size = Size(markW, markH * 0.65f),
                            cornerRadius = CornerRadius(markW/2, markW/2)
                        )
                        drawCircle(
                            color = neonRed,
                            radius = markW/1.8f,
                            center = Offset(markCenter.x, markCenter.y + markH/2 - markW/2)
                        )
                    }
                }

                sweatDrops.forEach { p ->
                    val dropSize = mainRadius * 0.1f
                    val dropColor = Color(0xFF60A5FA)

                    val rotation = (atan2(p.vy, p.vx) * (180f / PI)).toFloat() + 90f

                    withTransform({
                        translate(p.x, p.y)
                        rotate(rotation, pivot = Offset.Zero)
                    }) {
                        val w = dropSize
                        val h = dropSize * 1.3f
                        val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)

                        val path = Path().apply {
                            moveTo(-w, -w * 0.2f)
                            arcTo(
                                rect = Rect(topLeft = Offset(-w, -w), bottomRight = Offset(w, w)),
                                startAngleDegrees = 180f,
                                sweepAngleDegrees = 180f,
                                forceMoveTo = false
                            )
                            lineTo(0f, h)
                            close()
                        }

                        drawPath(path = path, color = dropColor.copy(alpha = alpha))

                        drawOval(
                            color = Color.White.copy(alpha = 0.6f * alpha),
                            topLeft = Offset(-w * 0.4f, -w * 0.6f),
                            size = Size(w * 0.5f, w * 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HenVisual(
    timeLeft: Float,
    isCritical: Boolean,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    eggWobbleRotation: Float,
    henSequenceElapsed: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hen_anim")
    val wingFlapRotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 45f,
        animationSpec = infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse),
        label = "wing"
    )

    // --- BOING ANIMATION SETUP ---
    val scope = rememberCoroutineScope()
    val boingAnim = remember { Animatable(1f) }

    // --- BEAK LOGIC ---
    val fraction = timeLeft % 1f

    // 1. Standard Ticking
    var isStandardBeakOpen = !isPaused && (fraction > 0.8f && fraction < 0.95f)

    // FIX: Completely disable standard ticking once the sequence starts.
    // This prevents phantom pecks while flying or after the triple cluck.
    if (henSequenceElapsed > 0.0f) {
        isStandardBeakOpen = false
    }

    // 2. Rapid Cluck
    val isRapidCluck = if (henSequenceElapsed > 0.35f && henSequenceElapsed < 0.85f) {
        (henSequenceElapsed % 0.166f) < 0.08f
    } else {
        false
    }

    // While in the launch window (0.3 - 1.0), use rapid cluck. Otherwise use standard (which is now false).
    val isBeakOpen = if (henSequenceElapsed > 0.3f && henSequenceElapsed < 1.0f) isRapidCluck else isStandardBeakOpen

    // --- ANIMATION CALCULATOR ---
    var animOffsetY = 0f
    var glassRotation = 0f
    var glassScale = 1f
    var isSliding = false
    var isFlapping = false
    var isSmushed = false

    // Shadow Logic
    var henShadowAlpha = 1.0f
    var henShadowScale = 1.0f
    var eggShadowAlpha = 0.0f
    var drawHenShadow = true

    if (henSequenceElapsed <= 0f) {
        animOffsetY = 0f
        isFlapping = false
    } else if (henSequenceElapsed <= 0.5f) {
        animOffsetY = 0f
        isFlapping = true
    } else if (henSequenceElapsed <= 2.0f) {
        // Fly Up
        val t = (henSequenceElapsed - 0.5f) / 1.5f
        animOffsetY = -2000f * (t * t)
        isFlapping = true
        henShadowAlpha = (1f - t).coerceIn(0f, 1f)
        henShadowScale = (1f - t * 0.5f).coerceIn(0f, 1f)
        eggShadowAlpha = t.coerceIn(0f, 1f)
    } else if (henSequenceElapsed <= 2.5f) {
        // Hover
        animOffsetY = -2000f
        drawHenShadow = false
        eggShadowAlpha = 1.0f
    } else if (henSequenceElapsed <= 2.75f) {
        // Fast Fall (Zooming In 1.0 -> 1.4)
        val t = (henSequenceElapsed - 2.5f) / 0.25f
        animOffsetY = -1000f * (1f - t)
        glassScale = 1.0f + (t * 0.4f) // Zooms to 1.4f
        isSliding = true
        drawHenShadow = false
        eggShadowAlpha = 1.0f
    } else if (henSequenceElapsed <= 3.25f) {
        // Splat (Impact)
        animOffsetY = 0f
        isSliding = true
        isSmushed = true

        // FIX: Pop scale to 1.5f immediately on impact for emphasis
        glassScale = 1.5f

        glassRotation = 5f
        drawHenShadow = false
        eggShadowAlpha = 1.0f
    } else {
        // Slide
        val t = (henSequenceElapsed - 3.25f) / 3.0f
        animOffsetY = 4000f * t
        isSliding = true
        isSmushed = true

        // Maintain large size during slide
        glassScale = 1.5f

        glassRotation = 10f
        drawHenShadow = false
        eggShadowAlpha = 1.0f
    }

    val crackProgress = if (timeLeft <= 5f) (5f - timeLeft) / 5f else 0f
    val density = LocalDensity.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(300.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // TRIGGER BOING ON CLICK
                scope.launch {
                    // Squash (0.9) then Stretch (1.05) then Settle (1.0)
                    boingAnim.snapTo(1f)
                    boingAnim.animateTo(0.9f, tween(50))
                    boingAnim.animateTo(1.05f, tween(100))
                    boingAnim.animateTo(1.0f, spring(dampingRatio = 0.4f, stiffness = 400f))
                }
                onTogglePause()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val henBodyRadius = 110.dp.toPx()
            val floorY = cy + henBodyRadius - 10.dp.toPx()

            with(density) {
                // --- SHADOWS LAYER ---
                if (drawHenShadow && henShadowAlpha > 0f) {
                    val hShadowW = henBodyRadius * 2.2f * henShadowScale
                    val hShadowH = henBodyRadius * 0.6f * henShadowScale
                    drawOval(color = Color.Black.copy(alpha = 0.3f * henShadowAlpha), topLeft = Offset(cx - hShadowW/2, floorY - hShadowH/2), size = Size(hShadowW, hShadowH))
                }

                if (eggShadowAlpha > 0f) {
                    val eggWidth = 120.dp.toPx()
                    val eShadowW = eggWidth * 0.8f
                    val eShadowH = 15.dp.toPx()
                    val wobbleX = if (!isPaused) eggWobbleRotation * 1.0f else 0f
                    drawOval(color = Color.Black.copy(alpha = 0.2f * eggShadowAlpha), topLeft = Offset(cx - eShadowW/2 + wobbleX, floorY - eShadowH/2), size = Size(eShadowW, eShadowH))
                }

                // --- EGG LAYER ---
                val showEgg = henSequenceElapsed > 0.0f
                if (showEgg) {
                    val eggScale = 1.0f
                    val currentRotation = if (!isPaused) eggWobbleRotation else 0f
                    val eggWidth = 120.dp.toPx()
                    val eggHeight = 150.dp.toPx()
                    val eggColor = Color(0xFFFEF3C7)
                    val eggTop = floorY - eggHeight
                    val eggCenterY = eggTop + eggHeight/2

                    withTransform({
                        scale(eggScale, eggScale, pivot = Offset(cx, eggCenterY))
                        rotate(currentRotation, pivot = Offset(cx, floorY))
                    }) {
                        // 1. Egg Body (Just the fill)
                        drawOval(color = eggColor, topLeft = Offset(cx - eggWidth/2, eggTop), size = Size(eggWidth, eggHeight))

                        // (Removed Outline and Inner Shadow per request)

                        // 2. Specular Highlight (Shine)
                        withTransform({
                            rotate(-20f, pivot = Offset(cx - eggWidth * 0.2f, eggTop + eggHeight * 0.25f))
                        }) {
                            drawOval(
                                color = Color.White.copy(alpha = 0.4f),
                                topLeft = Offset(cx - eggWidth * 0.3f, eggTop + eggHeight * 0.15f),
                                size = Size(eggWidth * 0.3f, eggHeight * 0.15f)
                            )
                        }

                        // 3. Cracks
                        val crackStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        if (crackProgress > 0.2f) {
                            val path1 = Path().apply { moveTo(cx, eggTop + eggHeight * 0.1f); lineTo(cx + 5, eggTop + eggHeight * 0.3f); lineTo(cx - 5, eggCenterY) }
                            drawPath(path1, Color.Black, style = crackStroke)
                        }
                        if (crackProgress > 0.5f) {
                            val path2 = Path().apply { moveTo(cx - 5, eggCenterY); lineTo(cx - 25, eggCenterY + eggHeight * 0.2f); lineTo(cx - 15, eggCenterY + eggHeight * 0.35f) }
                            drawPath(path2, Color.Black, style = crackStroke)
                        }
                        if (crackProgress > 0.8f) {
                            val path3 = Path().apply { moveTo(cx - 25, eggCenterY + eggHeight * 0.2f); lineTo(cx + 10, eggCenterY + eggHeight * 0.25f); lineTo(cx + 35, eggCenterY + eggHeight * 0.4f) }
                            drawPath(path3, Color.Black, style = crackStroke)
                        }
                    }
                }

                // --- HEN LAYER ---
                val henY = cy + animOffsetY

                if (henY > -2200f && henY < size.height + 5000f) {

                    // NEW: Apply Boing Transformation
                    // Scale Y = animated value (squash)
                    // Scale X = inverse (stretch to keep volume)
                    val squashY = boingAnim.value
                    val stretchX = 2f - squashY

                    withTransform({
                        // Apply standard glass animations first
                        rotate(glassRotation, pivot = Offset(cx, henY))
                        scale(glassScale, glassScale, pivot = Offset(cx, henY))

                        // Apply Boing (Pivot at BOTTOM of body so she squashes to floor)
                        scale(stretchX, squashY, pivot = Offset(cx, henY + henBodyRadius))
                    }) {
                        val faceEdgeX = cx + henBodyRadius * 0.82f
                        val beakYBase = henY - 6.dp.toPx()
                        val beakH = 24.dp.toPx()
                        val beakBaseHalfH = beakH / 2

                        // 1. HEART COMB
                        val combPath = Path().apply {
                            val bottomX = cx
                            val bottomY = henY - henBodyRadius + 35.dp.toPx()
                            val topY = henY - henBodyRadius - 45.dp.toPx()
                            val cpWidth = 45.dp.toPx()
                            moveTo(bottomX, bottomY)
                            cubicTo(bottomX - cpWidth, bottomY - 30.dp.toPx(), bottomX - cpWidth, topY, bottomX, topY + 25.dp.toPx())
                            cubicTo(bottomX + cpWidth, topY, bottomX + cpWidth, bottomY - 30.dp.toPx(), bottomX, bottomY)
                            close()
                        }
                        drawPath(combPath, NeonRed)

                        // 2. Body White Fill
                        drawCircle(color = Color.White, radius = henBodyRadius, center = Offset(cx, henY))

                        // SMUSH CONTACT PATCH
                        if (isSmushed) {
                            drawCircle(color = Color(0xFFE0E0E0), radius = henBodyRadius * 0.75f, center = Offset(cx, henY))
                        }

                        // 3. Body Outline
                        drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), style = Stroke(width = 4.dp.toPx()))

                        // 4. Wattle
                        val wattleTopX = faceEdgeX - 6.dp.toPx()
                        val wattleTopY = beakYBase + beakBaseHalfH - 2.dp.toPx()
                        val wattleWidth = 24.dp.toPx()
                        val wattleHeight = 32.dp.toPx()
                        val wattlePath = Path().apply {
                            moveTo(wattleTopX, wattleTopY)
                            cubicTo(wattleTopX - wattleWidth, wattleTopY + wattleHeight * 0.5f, wattleTopX - (wattleWidth * 0.5f), wattleTopY + wattleHeight, wattleTopX, wattleTopY + wattleHeight)
                            cubicTo(wattleTopX + (wattleWidth * 0.5f), wattleTopY + wattleHeight, wattleTopX + wattleWidth, wattleTopY + wattleHeight * 0.5f, wattleTopX, wattleTopY)
                            close()
                        }
                        drawPath(wattlePath, NeonRed)

                        // 5. Beak
                        val beakLen = 26.dp.toPx()
                        val lowerBeakLen = beakLen * 0.6f
                        val beakPivot = Offset(faceEdgeX, beakYBase)
                        val upperBeak = Path().apply { moveTo(faceEdgeX, beakYBase - beakBaseHalfH); lineTo(faceEdgeX + beakLen, beakYBase); lineTo(faceEdgeX, beakYBase); close() }
                        val lowerBeak = Path().apply { moveTo(faceEdgeX, beakYBase); lineTo(faceEdgeX + lowerBeakLen, beakYBase); lineTo(faceEdgeX, beakYBase + beakBaseHalfH); close() }
                        val upperRot = if (isBeakOpen || isSliding) -25f else 0f
                        val lowerRot = if (isBeakOpen || isSliding) 15f else 0f
                        withTransform({ rotate(degrees = upperRot, pivot = beakPivot) }) { drawPath(upperBeak, NeonOrange) }
                        withTransform({ rotate(degrees = lowerRot, pivot = beakPivot) }) { drawPath(lowerBeak, NeonOrange) }

                        // 6. Eye
                        val eyeX = faceEdgeX - 25.dp.toPx()
                        val eyeYBase = henY - 25.dp.toPx()

                        if (isSmushed) {
                            val eyeSize = 18.dp.toPx()
                            val eyeStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            val wincePath = Path().apply {
                                moveTo(eyeX - 8.dp.toPx(), eyeYBase - 8.dp.toPx())
                                lineTo(eyeX, eyeYBase)
                                lineTo(eyeX - 8.dp.toPx(), eyeYBase + 8.dp.toPx())
                            }
                            drawPath(wincePath, Color.Black, style = eyeStroke)
                        } else {
                            drawCircle(color = Color.Black, radius = 12.dp.toPx(), center = Offset(eyeX, eyeYBase))
                            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(eyeX + 3.dp.toPx(), eyeYBase - 3.dp.toPx()))
                            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = Offset(eyeX - 3.dp.toPx(), eyeYBase + 3.dp.toPx()))
                        }

                        // 7. Wing
                        val currentWingRot = if (isFlapping) wingFlapRotation else if (isSliding) -20f else 0f
                        val wingPivot = Offset(cx - 40.dp.toPx(), henY + 10.dp.toPx())

                        val wingColor = if (isSmushed) Color(0xFFE0E0E0) else Color.White

                        withTransform({ rotate(currentWingRot, pivot = wingPivot) }) {
                            drawArc(color = wingColor, startAngle = 10f, sweepAngle = 160f, useCenter = false, topLeft = Offset(wingPivot.x - 10.dp.toPx(), wingPivot.y - 30.dp.toPx()), size = Size(60.dp.toPx(), 65.dp.toPx()))
                            drawArc(color = Color.Black, startAngle = 10f, sweepAngle = 160f, useCenter = false, topLeft = Offset(wingPivot.x - 10.dp.toPx(), wingPivot.y - 30.dp.toPx()), size = Size(60.dp.toPx(), 65.dp.toPx()), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                }
            }
        }
    }
}