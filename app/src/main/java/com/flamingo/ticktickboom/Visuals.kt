package com.flamingo.ticktickboom

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun FuseVisual(progress: Float, isCritical: Boolean, colors: AppColors) {
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

    // Hoisted dimensions
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

    LaunchedEffect(Unit) {
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
        Canvas(modifier = Modifier.fillMaxSize()) {
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
            if (isCritical) {
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

            smokePuffs.forEach { puff -> drawCircle(color = colors.smokeColor.copy(alpha = puff.alpha), radius = puff.size, center = Offset(puff.x, puff.y)) }
            sparks.forEach { spark ->
                val alpha = (spark.life / spark.maxLife).coerceIn(0f, 1f)
                drawCircle(color = NeonOrange.copy(alpha = alpha), radius = particleRad * alpha, center = Offset(spark.x, spark.y))
                drawCircle(color = Color.Yellow.copy(alpha = alpha), radius = particleRadS * alpha, center = Offset(spark.x, spark.y))
            }

            if (!isCritical) {
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
fun C4Visual(isLedOn: Boolean, isDarkMode: Boolean) {
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
                            Icon(Icons.Rounded.DeveloperBoard, null, tint = Color(0xFF64748B), modifier = Modifier.size(32.dp))
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
                Icon(Icons.Filled.Bolt, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("HIGH VOLTAGE // DO NOT TAMPER", color = Color(0xFFF59E0B), fontSize = 10.sp, fontFamily = CustomFont, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DynamiteVisual(timeLeft: Float) {
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

        Canvas(modifier = Modifier.size(160.dp).offset(y = 15.dp)) {
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
}

@Composable
fun ExplosionScreen(colors: AppColors, onReset: () -> Unit) {
    val context = LocalContext.current
    val particles = remember {
        val colorsList = listOf(NeonRed, NeonOrange, Color.Yellow, Color.White)
        List(100) { i -> Particle(i, Math.random() * 360, (200 + Math.random() * 800).toFloat(), (3 + Math.random() * 5).toFloat(), colorsList.random(), 0f, (Math.random() * 20 - 10).toFloat()) }
    }
    val smoke = remember {
        List(30) { _ -> SmokeParticle(x = 0f, y = 0f, vx = (Math.random() * 100 - 50).toFloat(), vy = (Math.random() * 100 - 50).toFloat(), size = (20 + Math.random() * 40).toFloat(), alpha = 0.8f, life = 1f, maxLife = 1f) }
    }
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { animationProgress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing)) }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator } else { @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 400, 100, 200), -1)) } else { @Suppress("DEPRECATION") vibrator.vibrate(800) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF431407)), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0x99DC2626)))
        Canvas(modifier = Modifier.fillMaxSize()) {
            val progress = animationProgress.value
            val center = Offset(size.width / 2, size.height / 2)

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
            Text("BOOM", fontSize = 96.sp, fontWeight = FontWeight.Black, style = TextStyle(brush = Brush.verticalGradient(listOf(Color.Yellow, NeonRed)), shadow = Shadow(color = NeonOrange, blurRadius = 40f)), fontFamily = CustomFont)
            Spacer(modifier = Modifier.height(80.dp))

            ActionButton(
                text = "RESTART",
                icon = Icons.Filled.Refresh,
                color = Slate900.copy(alpha = 0.5f),
                textColor = NeonOrange,
                borderColor = NeonOrange,
                borderWidth = 2.dp,
                onClick = { AudioService.playClick(); onReset() }
            )
        }
    }
}

// --- UPDATED FROG VISUAL (BLACK OUTLINES) ---
@Composable
fun FrogVisual(timeLeft: Float, isCritical: Boolean) {
    val density = LocalDensity.current

    val tickDuration = if (timeLeft <= 5f) 0.5f else 1.0f

    val rawProgress = (timeLeft % tickDuration) / tickDuration
    val tickProgress = 1f - rawProgress

    val bellyHeightScale by animateFloatAsState(
        targetValue = if (tickProgress < 0.2f) 1.15f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "croak"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(320.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val mainRadius = size.width * 0.35f

            // CHANGED: Outline color set to black
            val outlineColor = Color.Black
            val outlineStroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

            // LAYER 0: Shadow
            val shadowWidth = mainRadius * 2.2f
            val shadowHeight = mainRadius * 0.4f
            val shadowY = cy + mainRadius * 1.05f
            drawOval(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = Offset(cx - shadowWidth / 2, shadowY - shadowHeight / 2),
                size = Size(shadowWidth, shadowHeight)
            )

            // LAYER 1: Feet
            val footRadius = mainRadius * 0.2f
            val footY = cy + mainRadius * 0.85f
            val leftFootX = cx - mainRadius * 0.5f
            val rightFootX = cx + mainRadius * 0.5f

            drawCircle(outlineColor, radius = footRadius, center = Offset(leftFootX, footY), style = outlineStroke)
            drawCircle(FrogBody, radius = footRadius, center = Offset(leftFootX, footY))

            drawCircle(outlineColor, radius = footRadius, center = Offset(rightFootX, footY), style = outlineStroke)
            drawCircle(FrogBody, radius = footRadius, center = Offset(rightFootX, footY))

            // LAYER 2: Body & Head (Silhouette)
            val bumpRadius = mainRadius * 0.45f
            val bumpY = cy - mainRadius * 0.65f
            val bumpXOffset = mainRadius * 0.5f

            val bodyCircle = Path().apply {
                addOval(Rect(center = Offset(cx, cy), radius = mainRadius))
            }
            val leftBump = Path().apply {
                addOval(Rect(center = Offset(cx - bumpXOffset, bumpY), radius = bumpRadius))
            }
            val rightBump = Path().apply {
                addOval(Rect(center = Offset(cx + bumpXOffset, bumpY), radius = bumpRadius))
            }

            val silhouette = Path()
            silhouette.op(bodyCircle, leftBump, PathOperation.Union)
            silhouette.op(silhouette, rightBump, PathOperation.Union)

            drawPath(silhouette, outlineColor, style = outlineStroke)
            drawPath(silhouette, FrogBody)

            // LAYER 3: Belly (ANIMATED)
            val bellyWidth = mainRadius * 1.4f
            val baseBellyHeight = mainRadius * 1.0f
            val currentBellyHeight = baseBellyHeight * bellyHeightScale

            drawOval(
                color = FrogBelly,
                topLeft = Offset(cx - bellyWidth / 2, cy - baseBellyHeight * 0.05f),
                size = Size(bellyWidth, currentBellyHeight * 0.9f)
            )

            // LAYER 4: Arms (Blended into body)
            val armWidth = mainRadius * 0.25f
            val armHeight = mainRadius * 0.35f
            val armY = cy + mainRadius * 0.2f
            val armXOffset = mainRadius * 0.65f

            // Left Arm
            rotate(30f, pivot = Offset(cx - armXOffset, armY)) {
                // Outline (Skip right side to blend) - Starts at Top (-90) sweeps clockwise (270) to Bottom.
                val arcTopLeft = Offset(cx - armXOffset - armWidth, armY - armHeight/2)
                val arcSize = Size(armWidth*2, armHeight)
                drawArc(
                    color = outlineColor,
                    startAngle = -90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = outlineStroke
                )
                // Fill
                drawOval(FrogBody, topLeft = Offset(cx - armXOffset - armWidth, armY - armHeight/2), size = Size(armWidth*2, armHeight))
            }

            // Right Arm
            rotate(-30f, pivot = Offset(cx + armXOffset, armY)) {
                // Outline (Skip left side to blend) - Starts at Top (-90) sweeps counter-clockwise (-270) to Bottom.
                val arcTopLeft = Offset(cx + armXOffset - armWidth, armY - armHeight/2)
                val arcSize = Size(armWidth*2, armHeight)
                drawArc(
                    color = outlineColor,
                    startAngle = -90f,
                    sweepAngle = -270f,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = outlineStroke
                )
                // Fill
                drawOval(FrogBody, topLeft = Offset(cx + armXOffset - armWidth, armY - armHeight/2), size = Size(armWidth*2, armHeight))
            }

            // LAYER 5: Face
            val eyeWhiteRadius = bumpRadius * 0.7f
            val pupilRadius = eyeWhiteRadius * 0.9f
            val glintRadius = pupilRadius * 0.25f

            fun drawEye(centerX: Float, centerY: Float) {
                // Eye Outline
                drawCircle(outlineColor, radius = eyeWhiteRadius, center = Offset(centerX, centerY), style = outlineStroke)
                // Eye Fill
                drawCircle(Color.White, radius = eyeWhiteRadius, center = Offset(centerX, centerY))
                // Pupil
                drawCircle(Color.Black, radius = pupilRadius, center = Offset(centerX, centerY))
                // Glint
                drawCircle(Color.White, radius = glintRadius, center = Offset(centerX - pupilRadius*0.4f, centerY - pupilRadius*0.4f))
            }
            drawEye(cx - bumpXOffset, bumpY)
            drawEye(cx + bumpXOffset, bumpY)

            val cheekRadius = mainRadius * 0.15f
            val cheekY = cy - mainRadius * 0.18f
            val cheekXOffset = mainRadius * 0.65f
            drawCircle(color = FrogBlush, radius = cheekRadius, center = Offset(cx - cheekXOffset, cheekY))
            drawCircle(color = FrogBlush, radius = cheekRadius, center = Offset(cx + cheekXOffset, cheekY))

            val mouthWidth = mainRadius * 0.3f
            val mouthY = cy - mainRadius * 0.22f
            val mouthH = mainRadius * 0.08f

            val mouthPath = Path().apply {
                moveTo(cx - mouthWidth/2, mouthY)
                quadraticTo(cx - mouthWidth/4, mouthY + mouthH, cx, mouthY)
                quadraticTo(cx + mouthWidth/4, mouthY + mouthH, cx + mouthWidth/2, mouthY)
            }
            // CHANGED: Mouth color set to black
            drawPath(path = mouthPath, color = Color.Black, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}