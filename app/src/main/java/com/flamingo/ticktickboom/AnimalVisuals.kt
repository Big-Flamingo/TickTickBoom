package com.flamingo.ticktickboom

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// NOTE: This file uses VisualParticle from AppModels.kt
// and drawReflection / lerp from Components.kt

@Composable
fun FrogVisual(timeLeft: Float, isCritical: Boolean, isPaused: Boolean, onTogglePause: () -> Unit, isDarkMode: Boolean) {
    val squishMagnitude = 0.98f
    val shadowResponseFactor = 1.5f

    val isPanic = timeLeft <= 1.05f
    val tickDuration = if (timeLeft <= 5f) 0.5f else 1.0f
    val currentProgress = if (isPaused) 0f else (timeLeft % tickDuration) / tickDuration

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

    val targetEyeScale = when {
        isPanic -> 1.0f
        isCritical -> 0.9f
        else -> 0.8f
    }

    val animatedEyeScale by animateFloatAsState(
        targetValue = targetEyeScale,
        animationSpec = tween(durationMillis = 50, easing = FastOutSlowInEasing),
        label = "EyeBulge"
    )

    val silhouettePath = remember { Path() }
    val bodyCirclePath = remember { Path() }
    val leftBumpPath = remember { Path() }
    val rightBumpPath = remember { Path() }
    val sweatDropPath = remember { Path() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope()
    val boingAnim = remember { Animatable(1f) }
    val sweatDrops = remember { mutableListOf<VisualParticle>() }
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var timeAccumulator by remember { mutableFloatStateOf(0.5f) }
    val currentIsCritical by rememberUpdatedState(isCritical)

    val density = LocalDensity.current
    val d = density.density

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
            val totalSquash = boingAnim.value * croakSquish
            val totalStretch = (2f - boingAnim.value) * (2f - croakSquish)
            val pivotY = cy + bodyRadius

            drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->
                if (!isDarkMode && !isReflection) {
                    val baseShadowW = bodyRadius * 2.5f
                    val baseShadowH = baseShadowW * 0.2f
                    val shadowScale = 1f + (totalStretch - 1f) * shadowResponseFactor
                    val currentShadowW = baseShadowW * shadowScale
                    val currentShadowH = baseShadowH * shadowScale

                    drawOval(color = Color.Black.copy(alpha = 0.2f), topLeft = Offset(cx - currentShadowW / 2, floorY - currentShadowH / 2), size = Size(currentShadowW, currentShadowH))
                }

                fun getMod(x: Float, y: Float): Offset {
                    val newX = cx + (x - cx) * totalStretch
                    val newY = pivotY + (y - pivotY) * totalSquash
                    return Offset(newX, newY)
                }
                val outlineStroke = Stroke(width = 6f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)

                // --- NEW: The shadow color for our overhead lighting ---
                val frogShadow = Color(0xFF7CB342)

                // --- NEW: Shared Drop Shadow Color ---
                val dropShadowColor = Color.Black.copy(alpha = 0.15f)

                // --- 1. FEET (Now deeply shaded by the body) ---
                val footRadius = bodyRadius * 0.2f; val footY = cy + bodyRadius * 0.85f; val footOffset = bodyRadius * 0.65f
                val lFoot = getMod(cx - footOffset, footY); val rFoot = getMod(cx + footOffset, footY)

                val footBrush = Brush.verticalGradient(colors = listOf(FrogBody, frogShadow), startY = footY - footRadius, endY = footY + footRadius)

                // Left Foot
                drawCircle(color = Color.Black, radius = footRadius, center = lFoot, style = outlineStroke)
                drawCircle(brush = footBrush, radius = footRadius, center = lFoot)
                drawCircle(color = dropShadowColor, radius = footRadius, center = lFoot) // NEW: Full shade overlay

                // Right Foot
                drawCircle(color = Color.Black, radius = footRadius, center = rFoot, style = outlineStroke)
                drawCircle(brush = footBrush, radius = footRadius, center = rFoot)
                drawCircle(color = dropShadowColor, radius = footRadius, center = rFoot) // NEW: Full shade overlay

                // 2. BODY (With shading)
                val bodyBrush = Brush.verticalGradient(colors = listOf(FrogBody, frogShadow), startY = cy - bodyRadius, endY = cy + bodyRadius)

                withTransform({ scale(totalStretch, totalSquash, pivot = Offset(cx, pivotY)) }) {
                    drawPath(path = silhouettePath, color = Color.Black, style = outlineStroke)
                    drawPath(path = silhouettePath, brush = bodyBrush)

                    val bumpRadius = bodyRadius * 0.42f; val bumpY = cy - bodyRadius * 0.68f; val eyeW = bumpRadius * 0.56f
                    val cheekTopY = (bumpY + eyeW) - (eyeW * 0.1f); val blushBottomY = cheekTopY + (bodyRadius * 0.22f)
                    clipPath(path = silhouettePath) {
                        val anchorXDist = bodyRadius * 0.45f; val anchorY = cy + bodyRadius * 0.95f
                        val targetBellyTop = blushBottomY + bodyRadius * 0.05f
                        val bellyHeight = (anchorY - targetBellyTop) * bellyHeightScale
                        val bellyRadius = (anchorXDist.pow(2) + bellyHeight.pow(2)) / (2 * bellyHeight)
                        val bellyCenterY = targetBellyTop + bellyRadius

                        // --- NEW: Dynamic Belly Gradient ---
                        val bellyShadow = Color(0xFFFFD54F) // A warm, golden amber
                        val bellyBrush = Brush.verticalGradient(
                            colors = listOf(FrogBelly, bellyShadow),
                            startY = targetBellyTop,
                            endY = targetBellyTop + bellyHeight // Maps perfectly to the visible belly!
                        )

                        // Replaced solid color with our new bellyBrush
                        drawCircle(brush = bellyBrush, radius = bellyRadius, center = Offset(cx, bellyCenterY))
                        drawOval(color = Color.White.copy(alpha = 0.3f), topLeft = Offset(cx - bellyRadius * 0.3f, targetBellyTop + bellyHeight * 0.15f), size = Size(bellyRadius * 0.6f, bellyHeight * 0.15f))
                    }
                }

                // Cheeks
                val bumpRadius = bodyRadius * 0.42f; val bumpY = cy - bodyRadius * 0.68f; val bumpXOffset = bodyRadius * 0.56f
                val cheekW = bodyRadius * 0.35f; val cheekH = bodyRadius * 0.22f; val eyeW = bumpRadius * 0.56f
                val cheekTopY = (bumpY + eyeW) - (eyeW * 0.1f); val cheekCenterY = cheekTopY + cheekH/2
                val cheekOffsetX = bodyRadius * 0.65f
                val lCheek = getMod(cx - cheekOffsetX, cheekCenterY); val rCheek = getMod(cx + cheekOffsetX, cheekCenterY)

                // --- NEW: Cheek Gradients ---
                // A softer, lighter pink for the top highlight
                val baseCheekColor = Color(0xFFFFC4C2)
                // Your original pink is now perfectly serving as the shadow!
                val shadowCheekColor = Color(0xFFff9693)

                val lCheekBrush = Brush.verticalGradient(
                    colors = listOf(baseCheekColor, shadowCheekColor),
                    startY = lCheek.y - cheekH/2,
                    endY = lCheek.y + cheekH/2
                )
                val rCheekBrush = Brush.verticalGradient(
                    colors = listOf(baseCheekColor, shadowCheekColor),
                    startY = rCheek.y - cheekH/2,
                    endY = rCheek.y + cheekH/2
                )

                // Replaced solid color with our new cheek brushes
                drawOval(brush = lCheekBrush, topLeft = Offset(lCheek.x - cheekW/2, lCheek.y - cheekH/2), size = Size(cheekW, cheekH))
                drawOval(brush = rCheekBrush, topLeft = Offset(rCheek.x - cheekW/2, rCheek.y - cheekH/2), size = Size(cheekW, cheekH))

                // --- NEW: A global brush that perfectly maps to the squished body's shadow ---
                val globalBodyBrush = Brush.verticalGradient(
                    colors = listOf(FrogBody, frogShadow),
                    startY = pivotY - (2f * bodyRadius * totalSquash),
                    endY = pivotY
                )

                val armW = bodyRadius * 0.18f; val armH = bodyRadius * 0.28f
                val armY = cy - bodyRadius * 0.06f; val armX = bodyRadius * 0.72f
                val lArm = getMod(cx - armX, armY); val rArm = getMod(cx + armX, armY)

                // --- LEFT ARM ---
                val lArmRot = if (isPanic) flailRotation else 30f
                val lHingePivot = Offset(lArm.x, lArm.y + armH / 2f)

                rotate(lArmRot, pivot = lArm) {
                    // 1. Draw Shadow FIRST (Only in Light Mode!)
                    if (!isDarkMode) {
                        rotate(25f, pivot = lHingePivot) {
                            drawArc(color = dropShadowColor, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(lArm.x - armW, lArm.y - armH/2), size = Size(armW*2, armH))
                        }
                    }

                    // 2. Draw the Arm on top
                    drawArc(color = Color.Black, startAngle = -90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(lArm.x - armW, lArm.y - armH/2), size = Size(armW*2, armH), style = outlineStroke)
                    drawOval(brush = globalBodyBrush, topLeft = Offset(lArm.x - armW, lArm.y - armH/2), size = Size(armW*2, armH))
                }

                // --- RIGHT ARM ---
                val rArmRot = if (isPanic) -flailRotation else -30f
                val rHingePivot = Offset(rArm.x, rArm.y + armH / 2f)

                rotate(rArmRot, pivot = rArm) {
                    // 1. Draw Shadow FIRST (Only in Light Mode!)
                    if (!isDarkMode) {
                        rotate(-25f, pivot = rHingePivot) {
                            drawArc(color = dropShadowColor, startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(rArm.x - armW, rArm.y - armH/2), size = Size(armW*2, armH))
                        }
                    }

                    // 2. Draw the Arm on top
                    drawArc(color = Color.Black, startAngle = 90f, sweepAngle = 180f, useCenter = false, topLeft = Offset(rArm.x - armW, rArm.y - armH/2), size = Size(armW*2, armH), style = outlineStroke)
                    drawOval(brush = globalBodyBrush, topLeft = Offset(rArm.x - armW, rArm.y - armH/2), size = Size(armW*2, armH))
                }

                fun drawEye(baseX: Float, baseY: Float, isLeft: Boolean) {
                    val pos = getMod(baseX, baseY)
                    val currentEyeRadius = eyeW * animatedEyeScale
                    val currentPupilRadius = currentEyeRadius + (outlineStroke.width / 2f)

                    // --- NEW: Universal Glassy Pupil Gradient ---
                    val pupilBrush = Brush.verticalGradient(
                        colors = listOf(Color.Black, Color(0xFF3A3A4A)),
                        startY = pos.y - currentPupilRadius,
                        endY = pos.y + currentPupilRadius
                    )

                    val isNormalOpenEye = !isCritical && !isPanic && !isBlinking
                    if (!isNormalOpenEye) { drawCircle(color = Color.Black, radius = currentEyeRadius, center = pos, style = outlineStroke) }

                    // REVERTED: The Whites of the Eyes remain pure, crisp white!
                    drawCircle(color = Color.White, radius = currentEyeRadius, center = pos)

                    if (isCritical && !isPanic) {
                        val path = Path(); val size = currentEyeRadius * 1.2f; val off = size * 0.15f; val ex = pos.x + if(isLeft) off else -off
                        path.moveTo(ex - size/2, pos.y - size/2); path.lineTo(ex + size/4, pos.y); path.lineTo(ex - size/2, pos.y + size/2)
                        if (!isLeft) { path.reset(); path.moveTo(ex + size/2, pos.y - size/2); path.lineTo(ex - size/4, pos.y); path.lineTo(ex + size/2, pos.y + size/2) }

                        // Apply the glossy gradient to the Critical 'X' pupils!
                        drawPath(path, brush = pupilBrush, style = Stroke(8f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    } else if (!isPanic) {
                        if (isBlinking) {
                            val blinkWidth = 15f * d * animatedEyeScale
                            drawLine(color = Color.Black, start = Offset(pos.x - blinkWidth, pos.y), end = Offset(pos.x + blinkWidth, pos.y), strokeWidth = 4f * d, cap = StrokeCap.Round)
                        } else {
                            // Apply the glossy gradient to the normal pupils!
                            drawCircle(brush = pupilBrush, radius = currentPupilRadius, center = pos)
                            drawCircle(color = Color.White, radius = currentPupilRadius * 0.25f, center = Offset(pos.x - currentPupilRadius*0.4f, pos.y - currentPupilRadius*0.4f))
                        }
                    }
                }
                drawEye(cx - bumpXOffset, bumpY, true); drawEye(cx + bumpXOffset, bumpY, false)

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

                if (alertScale.value > 0f) {
                    val markBaseY = bumpY - bumpRadius * 1.8f; val markPos = getMod(cx, markBaseY)
                    val markW = bodyRadius * 0.15f; val markH = bodyRadius * 0.6f
                    withTransform({ scale(alertScale.value, alertScale.value, pivot = Offset(markPos.x, markPos.y + markH/2)) }) {
                        drawRoundRect(color = Color(0xFFFF0000), topLeft = Offset(markPos.x - markW/2, markPos.y - markH/2), size = Size(markW, markH * 0.65f), cornerRadius = CornerRadius(markW/2, markW/2))
                        drawCircle(color = Color(0xFFFF0000), radius = markW/1.8f, center = Offset(markPos.x, markPos.y + markH/2 - markW/2))
                    }
                }

                if (!isReflection && currentIsCritical && timeAccumulator >= 0.5f) {
                    timeAccumulator -= 0.5f
                    repeat(3) { i ->
                        val angle = -PI / 4 + (i - 1) * 0.5 + ((Math.random() - 0.5) * 0.1)
                        val speed = (bumpRadius / 0.5f) * (0.9f + Math.random().toFloat() * 0.2f)
                        val spawnX = (cx + bumpXOffset) + (bumpRadius * 1.05f)
                        val spawnY = bumpY - (bumpRadius * 1.15f)
                        val modSpawn = getMod(spawnX, spawnY)
                        sweatDrops.add(VisualParticle(modSpawn.x, modSpawn.y, (cos(angle) * speed).toFloat(), (sin(angle) * speed).toFloat(), 0.5f, 0.5f))
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
    val density = LocalDensity.current
    val d = density.density
    var isBlinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(Random.nextLong(2000, 4000)); isBlinking = true; delay(150); isBlinking = false } }

    val wingFlapRotation by infiniteTransition.animateFloat(0f, 45f, infiniteRepeatable(tween(150, easing = LinearEasing), RepeatMode.Reverse), "wing")
    val combPath = remember { Path() }; val wattlePath = remember { Path() }; val upperBeakPath = remember { Path() }; val lowerBeakPath = remember { Path() }; val wincePath = remember { Path() }; val crack1Path = remember { Path() }; val crack2Path = remember { Path() }; val crack3Path = remember { Path() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
    val scope = rememberCoroutineScope(); val boingAnim = remember { Animatable(1f) }
    val fraction = timeLeft % 1f
    var isStandardBeakOpen = !isPaused && (fraction > 0.8f && fraction < 0.95f); if (henSequenceElapsed > 0.0f) isStandardBeakOpen = false
    val isRapidCluck = if (henSequenceElapsed > 0.35f && henSequenceElapsed < 0.85f) (henSequenceElapsed % 0.166f) < 0.08f else false
    val baseBeakOpen = isPainedBeakOpen || (if (henSequenceElapsed > 0.3f && henSequenceElapsed < 1.0f) isRapidCluck else isStandardBeakOpen)

    var animOffsetY = 0f; var glassRotation = 0f; var glassScale = 1f; var isSliding = false; var isFlapping = false; var isSmushed = false; var henShadowAlpha = 1.0f; var henShadowScale = 1.0f; var eggShadowAlpha = 0.0f; var drawHenShadow = true
    var shadowBlurRadius = 0f

    if (henSequenceElapsed <= 0f) isFlapping = false
    else if (henSequenceElapsed <= 0.5f) isFlapping = true
    else if (henSequenceElapsed <= 2.0f) {
        val t = (henSequenceElapsed - 0.5f) / 1.5f
        animOffsetY = -2000f * (t * t)
        isFlapping = true
        henShadowAlpha = (1f - t).coerceIn(0f, 1f)
        henShadowScale = 1.0f
        shadowBlurRadius = t * 15f * d
        eggShadowAlpha = t.coerceIn(0f, 1f)
    }
    else if (henSequenceElapsed <= 4.2f) { animOffsetY = -2000f; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else if (henSequenceElapsed <= 4.5f) { val t = (henSequenceElapsed - 4.2f) / 0.3f; animOffsetY = -2000f * (1f - t * t); glassScale = 1.0f + (t * 0.4f); isSliding = true; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else if (henSequenceElapsed <= 6.0f) { animOffsetY = 0f; isSliding = true; isSmushed = true; glassScale = 1.5f; glassRotation = 5f; drawHenShadow = false; eggShadowAlpha = 1.0f }
    else { val t = (henSequenceElapsed - 6.0f) / 3.0f; animOffsetY = 4000f * t; isSliding = true; isSmushed = true; glassScale = 1.5f; glassRotation = 10f; drawHenShadow = false; eggShadowAlpha = 1.0f }

    val effectiveBeakOpen = (baseBeakOpen || isSliding) && !isPainedBeakClosed
    val currentAnimOffsetY = remember { mutableFloatStateOf(0f) }; currentAnimOffsetY.floatValue = animOffsetY

    // --- NEW: Smart Cluck Detection ---
    // If smushed on glass, she clucks by closing her mouth. Otherwise, she clucks by opening it!
    val isActivelyClucking = if (isSmushed) isPainedBeakClosed else (baseBeakOpen && !isPaused)

    // --- UPDATED: Automatic rhythmic squish ---
    val cluckSquish by animateFloatAsState(
        targetValue = if (isActivelyClucking) 0.98f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "cluck_squish"
    )

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
                val wattleWidth = 24f * d; val wattleHeight = 32f * d
                wattlePath.reset(); wattlePath.moveTo(faceEdgeX, lowerBeakBottomY)
                wattlePath.cubicTo(faceEdgeX - wattleWidth, lowerBeakBottomY + wattleHeight * 0.5f, faceEdgeX - (wattleWidth * 0.5f), lowerBeakBottomY + wattleHeight, faceEdgeX, lowerBeakBottomY + wattleHeight)
                wattlePath.cubicTo(faceEdgeX + (wattleWidth * 0.5f), lowerBeakBottomY + wattleHeight, faceEdgeX + wattleWidth, lowerBeakBottomY + wattleHeight * 0.5f, faceEdgeX, lowerBeakBottomY)
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
                                val squashY = boingAnim.value * cluckSquish
                                val stretchX = (2f - boingAnim.value) * (2f - cluckSquish)

                                withTransform({
                                    rotate(glassRotation, pivot = Offset(cx, henY))
                                    scale(glassScale, glassScale, pivot = Offset(cx, henY))
                                }) {
                                    drawIntoCanvas { it.nativeCanvas.saveLayerAlpha(0f, -size.height, size.width, size.height * 2f, (heightFade * 255).toInt()) }

                                    // 1. SQUISHED PARTS (Comb & Body)
                                    withTransform({ scale(stretchX, squashY, pivot = Offset(cx, henY + henBodyRadius)) }) {
                                        drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), blendMode = BlendMode.Clear)
                                        withTransform({ translate(cx, henY) }) { drawPath(combPath, NeonRed) }
                                        drawCircle(color = Color.White, radius = henBodyRadius, center = Offset(cx, henY))
                                        drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), style = Stroke(width = 4f * d))
                                    }

                                    // 2. NON-SQUISHED PARTS (Wattle, Beak, Eye, Wing)
                                    val faceRelX = henBodyRadius * 0.82f; val faceRelY = -6f * d
                                    val faceShiftX = faceRelX * (stretchX - 1f); val faceShiftY = (faceRelY - henBodyRadius) * (squashY - 1f)

                                    withTransform({ translate(cx + faceShiftX, henY + faceShiftY) }) {
                                        drawPath(wattlePath, NeonRed)
                                        val relBeakY = -6f * d
                                        withTransform({ rotate(if (effectiveBeakOpen) -15f else 0f, pivot = Offset(faceRelX, relBeakY)) }) { drawPath(upperBeakPath, NeonOrange) }
                                        withTransform({ rotate(if (effectiveBeakOpen) 10f else 0f, pivot = Offset(faceRelX, relBeakY)) }) { drawPath(lowerBeakPath, NeonOrange) }
                                    }

                                    val eyeRelX = faceRelX - 25f * d; val eyeRelY = -25f * d
                                    val eyeShiftX = eyeRelX * (stretchX - 1f); val eyeShiftY = (eyeRelY - henBodyRadius) * (squashY - 1f)

                                    if (isSmushed) {
                                        withTransform({ translate(cx + eyeShiftX, henY + eyeShiftY) }) { drawPath(wincePath, Color.Black, style = Stroke(5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                                    } else if (isBlinking) {
                                        val eyeCenter = Offset(cx + eyeRelX + eyeShiftX, henY + eyeRelY + eyeShiftY)
                                        drawLine(color = Color.Black, start = Offset(eyeCenter.x - 12f * d, eyeCenter.y), end = Offset(eyeCenter.x + 12f * d, eyeCenter.y), strokeWidth = 5f * d, cap = StrokeCap.Round)
                                    } else {
                                        val eyeCenter = Offset(cx + eyeRelX + eyeShiftX, henY + eyeRelY + eyeShiftY)
                                        drawCircle(color = Color.Black, radius = 12f * d, center = eyeCenter)
                                        drawCircle(color = Color.White, radius = 4f * d, center = Offset(eyeCenter.x + 3f * d, eyeCenter.y - 3f * d))
                                        drawCircle(color = Color.White, radius = 2f * d, center = Offset(eyeCenter.x - 3f * d, eyeCenter.y + 3f * d))
                                    }

                                    val wingRelX = -40f * d; val wingRelY = 10f * d
                                    val wingShiftX = wingRelX * (stretchX - 1f); val wingShiftY = (wingRelY - henBodyRadius) * (squashY - 1f)
                                    val wingP = Offset(cx + wingRelX + wingShiftX, henY + wingRelY + wingShiftY)
                                    val wingRot = if (isFlapping) wingFlapRotation else if (isSliding) -20f else 0f
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

            if (!isDarkMode) {
                if (drawHenShadow && henShadowAlpha > 0f) {
                    // Shadow reacts to both tapping and clucking
                    val stretchX = (2f - boingAnim.value) * (2f - cluckSquish)
                    val shadowResponseFactor = 1.5f
                    val boingScale = 1f + (stretchX - 1f) * shadowResponseFactor

                    val hShadowW = henBodyRadius * 2.0f * henShadowScale * boingScale
                    val hShadowH = hShadowW * 0.2f

                    if (shadowBlurRadius > 0.5f) {
                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint()
                            paint.color = Color.Black.copy(alpha = 0.3f * henShadowAlpha).toArgb()
                            paint.maskFilter = BlurMaskFilter(shadowBlurRadius, Blur.NORMAL)
                            canvas.nativeCanvas.drawOval(android.graphics.RectF(cx - hShadowW/2, floorY - hShadowH/2, cx + hShadowW/2, floorY + hShadowH/2), paint)
                        }
                    } else {
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

            val henY = cy + animOffsetY
            if (henY > -3000f && henY < size.height + 5000f) {
                // Combine tap boing and automatic cluck squish for the main draw
                val squashY = boingAnim.value * cluckSquish
                val stretchX = (2f - boingAnim.value) * (2f - cluckSquish)

                withTransform({
                    rotate(glassRotation, pivot = Offset(cx, henY))
                    scale(glassScale, glassScale, pivot = Offset(cx, henY))
                }) {
                    // 1. SQUISHED PARTS (Comb & Body)
                    withTransform({ scale(stretchX, squashY, pivot = Offset(cx, henY + henBodyRadius)) }) {
                        withTransform({ translate(cx, henY) }) { drawPath(combPath, NeonRed) }
                        drawCircle(color = Color.White, radius = henBodyRadius, center = Offset(cx, henY))
                        if (isSmushed) drawCircle(color = Color(0xFFE0E0E0), radius = henBodyRadius * 0.75f, center = Offset(cx, henY))
                        drawCircle(color = Color.Black, radius = henBodyRadius, center = Offset(cx, henY), style = Stroke(width = 4f * d))
                    }

                    // 2. NON-SQUISHED PARTS (Wattle, Beak, Eye, Wing)
                    val faceRelX = henBodyRadius * 0.82f; val faceRelY = -6f * d
                    val faceShiftX = faceRelX * (stretchX - 1f); val faceShiftY = (faceRelY - henBodyRadius) * (squashY - 1f)

                    withTransform({ translate(cx + faceShiftX, henY + faceShiftY) }) {
                        drawPath(wattlePath, NeonRed)
                        val relBeakY = -6f * d
                        withTransform({ rotate(if (effectiveBeakOpen) -15f else 0f, pivot = Offset(faceRelX, relBeakY)) }) { drawPath(upperBeakPath, NeonOrange) }
                        withTransform({ rotate(if (effectiveBeakOpen) 10f else 0f, pivot = Offset(faceRelX, relBeakY)) }) { drawPath(lowerBeakPath, NeonOrange) }
                    }

                    val eyeRelX = faceRelX - 25f * d; val eyeRelY = -25f * d
                    val eyeShiftX = eyeRelX * (stretchX - 1f); val eyeShiftY = (eyeRelY - henBodyRadius) * (squashY - 1f)

                    if (isSmushed) {
                        withTransform({ translate(cx + eyeShiftX, henY + eyeShiftY) }) { drawPath(wincePath, Color.Black, style = Stroke(5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                    } else if (isBlinking) {
                        val eyeCenter = Offset(cx + eyeRelX + eyeShiftX, henY + eyeRelY + eyeShiftY)
                        drawLine(color = Color.Black, start = Offset(eyeCenter.x - 12f * d, eyeCenter.y), end = Offset(eyeCenter.x + 12f * d, eyeCenter.y), strokeWidth = 5f * d, cap = StrokeCap.Round)
                    } else {
                        val eyeCenter = Offset(cx + eyeRelX + eyeShiftX, henY + eyeRelY + eyeShiftY)
                        drawCircle(color = Color.Black, radius = 12f * d, center = eyeCenter)
                        drawCircle(color = Color.White, radius = 4f * d, center = Offset(eyeCenter.x + 3f * d, eyeCenter.y - 3f * d))
                        drawCircle(color = Color.White, radius = 2f * d, center = Offset(eyeCenter.x - 3f * d, eyeCenter.y + 3f * d))
                    }

                    val wingRelX = -40f * d; val wingRelY = 10f * d
                    val wingShiftX = wingRelX * (stretchX - 1f); val wingShiftY = (wingRelY - henBodyRadius) * (squashY - 1f)
                    val wingP = Offset(cx + wingRelX + wingShiftX, henY + wingRelY + wingShiftY)
                    val wingRot = if (isFlapping) wingFlapRotation else if (isSliding) -20f else 0f
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