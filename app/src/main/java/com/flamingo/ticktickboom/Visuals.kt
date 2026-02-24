package com.flamingo.ticktickboom

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// --- VISUALS ---

const val C4_PLASMA_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float intensity;
    uniform float touchCount;
    uniform float2 touch1;
    uniform float2 touch2;

    float hash(vec2 p) {
        vec3 p3  = fract(vec3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    // NEW: Reusable lightning generator!
    float getLightning(vec2 fragCoord, vec2 touchPos, float t) {
        vec2 uv = (fragCoord - touchPos) / resolution.y; 
        vec2 warpedUv = uv + vec2(noise(uv * 15.0 - t), noise(uv * 15.0 + t)) * 0.1;
        float r = length(warpedUv);
        float angle = atan(warpedUv.y, warpedUv.x);
        
        float lightning = 0.0;
        for(float i = 1.0; i <= 3.0; i++) {
            float bolt = abs(sin(angle * (8.0 * i) + t * 1.5 * i + noise(uv * 10.0) * 15.0));
            lightning += (0.003 * i) / abs(r - 0.2 * i + bolt * 0.08);
        }
        return lightning + (0.01 / (length(uv) + 0.01)); // Add hot core
    }

    half4 main(float2 fragCoord) {
        float t = time * 6.0;
        float lightning = 0.0;
        float mask = 0.0;
        
        // Accumulate lightning for up to 2 fingers!
        if (touchCount > 0.5) {
            lightning += getLightning(fragCoord, touch1, t);
            mask = max(mask, smoothstep(0.8, 0.0, length((fragCoord - touch1) / resolution.y)));
        }
        if (touchCount > 1.5) {
            lightning += getLightning(fragCoord, touch2, t);
            mask = max(mask, smoothstep(0.8, 0.0, length((fragCoord - touch2) / resolution.y)));
        }
        
        mask *= intensity;
        
        vec3 col = vec3(1.0, 0.8, 0.2) * lightning; 
        col += vec3(1.0, 1.0, 0.8) * pow(lightning, 2.0) * 0.3; 
        
        float finalAlpha = clamp(lightning * mask, 0.0, 1.0);
        vec3 finalColor = clamp(col * mask, 0.0, finalAlpha);
        
        return half4(finalColor, finalAlpha);
    }
"""

const val EXPLOSION_SHADER = """
    uniform float2 resolution;
    uniform float2 center; // <-- NEW: Receives your dynamic origin!
    uniform float time;
    uniform float progress; 

    float hash(vec2 p) {
        vec3 p3  = fract(vec3(p.xyx) * 0.1031);
        p3 += dot(p3, p3.yzx + 33.33);
        return fract((p3.x + p3.y) * p3.z);
    }

    float noise(vec2 x) {
        vec2 i = floor(x);
        vec2 f = fract(x);
        float a = hash(i);
        float b = hash(i + vec2(1.0, 0.0));
        float c = hash(i + vec2(0.0, 1.0));
        float d = hash(i + vec2(1.0, 1.0));
        vec2 u = f * f * (3.0 - 2.0 * f);
        return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
    }

    float fbm(vec2 uv) {
        float f = 0.0;
        float amp = 0.5;
        // THE FIX: Changed from 4 to 3! Cuts GPU load by 25% with zero visual difference.
        for(int i = 0; i < 3; i++) { 
            f += amp * noise(uv);
            uv *= 2.0;
            amp *= 0.5;
        }
        return f;
    }

    half4 main(float2 fragCoord) {
        // <-- UPDATED: Now centers the effect exactly on your dynamic offset!
        vec2 uv = (fragCoord - center) / min(resolution.x, resolution.y); 
        float d = length(uv);

        float flash = exp(-progress * 15.0); 
        float shockRadius = 1.2 * (1.0 - exp(-progress * 5.0)); 
        float shockThickness = 0.05 * (1.0 - progress);
        
        float ring = smoothstep(shockRadius + shockThickness, shockRadius, d) * smoothstep(shockRadius - shockThickness * 3.0, shockRadius, d);
        float ringGlow = ring * (1.0 - progress) * 2.0;

        float n = fbm(uv * 8.0 - vec2(0.0, time * 3.0));
        float fireRadius = 0.8 * (1.0 - exp(-progress * 4.0));
        
        float fireMask = smoothstep(fireRadius, fireRadius - 0.3, d);
        float fireIntensity = n * fireMask * (1.0 - progress);

        vec3 col = vec3(0.0);
        col += vec3(1.0) * flash;
        col += vec3(0.8, 0.95, 1.0) * ringGlow;
        
        vec3 smoke = vec3(0.1, 0.1, 0.12);
        vec3 orange = vec3(1.0, 0.4, 0.0);
        vec3 yellow = vec3(1.0, 0.9, 0.2);
        
        vec3 fireCol = mix(smoke, orange, smoothstep(0.1, 0.5, fireIntensity));
        fireCol = mix(fireCol, yellow, smoothstep(0.5, 0.8, fireIntensity));
        
        col += fireCol * smoothstep(0.0, 0.2, fireIntensity) * 2.0;

        float finalAlpha = max(max(flash, ringGlow), fireIntensity * 1.5);
        finalAlpha = clamp(finalAlpha, 0.0, 1.0) * (1.0 - pow(progress, 2.0));

        return half4(col * finalAlpha, finalAlpha);
    }
"""

@Composable
fun FuseVisual(progress: Float, isCritical: Boolean, colors: AppColors, isPaused: Boolean, onTogglePause: () -> Unit, isDarkMode: Boolean) {
    // Now using the shared VisualParticle class from AppModels.kt
    val sparks = remember { mutableListOf<VisualParticle>() }
    val smokePuffs = remember { mutableListOf<VisualParticle>() }

    var frame by remember { mutableLongStateOf(0L) }
    var lastFrameTime = remember { 0L }

    val fusePath = remember { Path() }
    val fuseLayerPaint = remember { Paint() }
    val frontRimPath = remember { Path() }
    val ashPath = remember { Path() } // Create ONE Compose Path
    val pathMeasure = remember { android.graphics.PathMeasure() }
    var cachedSize by remember { mutableStateOf(Size.Zero) }
// --- NEW: Size-dependent Brush Caches ---
    var cachedBombBodyBrush by remember { mutableStateOf<Brush?>(null) }
    var cachedSpecularBrush by remember { mutableStateOf<Brush?>(null) }

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

    // --- NEW: Hoisted Ash Strokes ---
    val ashStrokeMain = remember(d) { Stroke(width = strokeW, cap = StrokeCap.Round) }
    val ashStrokeMid = remember(d) { Stroke(width = strokeW * 0.8f, cap = StrokeCap.Round) }
    val ashStrokeInner = remember(d) { Stroke(width = strokeW * 0.4f, cap = StrokeCap.Round) }

    // Particles/Glint configs...
    val glintSizeL = 24f * d; val glintSizeS = 4f * d; val glintOffsetL = 12f * d; val glintOffsetS = 2f * d
    val glowRadius = 25f * d; val coreRadius = 8f * d; val whiteRadius = 4f * d
    val particleRad = 3f * d; val particleRadS = 1.5f * d; val tapThreshold = 60f * d; val fuseYOffset = 5f * d
    val sparkPos = remember { floatArrayOf(0f, 0f) }

    val smokeSprite = ImageBitmap.imageResource(id = R.drawable.smoke_wisp)

    LaunchedEffect(isPaused) {
        while (true) {
            withFrameNanos { nanos ->
                val dt = if (lastFrameTime == 0L) 0.016f else ((nanos - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.1f)
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
                // --- NEW: Cache the heavy radial gradients! ---
                cachedBombBodyBrush = Brush.radialGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFF0F172A)),
                    center = Offset(bombCenterX - 20, bombCenterY - 20),
                    radius = bodyRadius
                )
                cachedSpecularBrush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                    center = Offset(bombCenterX - bodyRadius * 0.4f, bombCenterY - bodyRadius * 0.4f),
                    radius = bodyRadius * 0.3f
                )
                cachedSize = size
            }

            drawReflection(isDarkMode, floorY, 0.25f) { isReflection ->
                if (!isDarkMode && !isReflection) {
                    val shadowW = bodyRadius * 2f
                    val shadowH = shadowW * 0.2f
                    drawOval(color = Color.Black.copy(alpha = 0.2f), topLeft = Offset(bombCenterX - shadowW / 2, floorY - shadowH / 2), size = Size(shadowW, shadowH))
                }

                drawCircle(brush = cachedBombBodyBrush!!, radius = bodyRadius, center = Offset(bombCenterX, bombCenterY))

                pathMeasure.setPath(fusePath.asAndroidPath(), false)
                val totalLength = pathMeasure.length
                val visibleLength = totalLength - fuseInnerOffset
                val effectiveProgress = if (isCritical) 1f else progress
                val currentBurnPoint = fuseInnerOffset + (visibleLength * (1f - effectiveProgress))

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
                drawCircle(brush = cachedSpecularBrush!!, radius = bodyRadius * 0.3f, center = specularCenter)

                val baseDark = Color(0xFF0F172A)
                val baseLight = Color(0xFF475569)
                val rimDark = Color(0xFF1E293B)
                val rimLight = Color(0xFF64748B)
                val orangeDark = Color(0xFFD97706)
                val orangeLight = Color(0xFFFFB74D)

                val rimLeft = lerp(rimDark, orangeDark, symmetry * lightIntensity)
                val rimCenter = lerp(rimLight, orangeLight, symmetry * lightIntensity)
                val rimRight = lerp(rimDark, orangeDark, lightIntensity)
                // --- FIX: Defaults to 0.5f (dead center) and dynamically shifts only when lit! ---
                val rimCenterOffset = 0.5f + (0.2f * (1f - symmetry) * lightIntensity)

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
                        // USE CACHED PAINT!
                        canvas.saveLayer(Rect(outerRimRect.left, neckTopY, outerRimRect.right, neckBaseY + 15f * d), fuseLayerPaint)

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
                    val androidAshPath = ashPath.asAndroidPath()
                    androidAshPath.rewind() // CLEAR the cached path!
                    pathMeasure.getSegment(0f, currentBurnPoint, androidAshPath, true)

                    // USE CACHED STROKES! (No new objects created!)
                    drawPath(path = ashPath, color = Color(0xFFCCC9C6), style = ashStrokeMain)
                    drawPath(path = ashPath, color = Color(0xFFD6D3D1), style = ashStrokeMid)
                    drawPath(path = ashPath, color = Color.White.copy(alpha = 0.5f), style = ashStrokeInner)
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
                // FIX SMOKE: Stable Rotation & Normal Blending
                smokePuffs.forEach { puff -> // Removed the 'Indexed' and 'i'!
                    val p = 1f - (puff.life / puff.maxLife)

                    val currentSize = 30f + (p * 60f)
                    val halfSize = currentSize / 2f
                    val currentAlpha = (1f - p).coerceIn(0f, 0.6f)

                    // FIX 1: Stable rotation based on the particle's own X velocity!
                    val rotationSpeed = if (puff.vx > 0) 50f else -50f
                    val currentRotation = puff.life * rotationSpeed

                    withTransform({
                        translate(left = puff.x, top = puff.y)
                        rotate(degrees = currentRotation, pivot = Offset.Zero)
                        translate(left = -halfSize, top = -halfSize)
                    }) {
                        drawImage(
                            image = smokeSprite,
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(currentSize.toInt(), currentSize.toInt()),
                            alpha = currentAlpha,
                            colorFilter = ColorFilter.tint(colors.smokeColor)
                            // FIX 2: BlendMode.Screen completely removed!
                        )
                    }
                }

                // FIX SPARKS:
                sparks.forEach { spark ->
                    val currentAlpha = (spark.life / spark.maxLife).coerceIn(0f, 1f)
                    // Pass alpha separately!
                    drawCircle(color = NeonOrange, alpha = currentAlpha, radius = particleRad * currentAlpha, center = Offset(spark.x, spark.y))
                    drawCircle(color = Color.Yellow, alpha = currentAlpha, radius = particleRadS * currentAlpha, center = Offset(spark.x, spark.y))
                }

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

    val armedText = stringResource(R.string.armed)
    val pausedText = stringResource(R.string.paused)
    val warningText = stringResource(R.string.high_voltage_warning)
    val emptyTimeText = stringResource(R.string.empty_time) // For "--:--"

    val ledSize = 12f * d
    val ledRadius = 6f * d
    val c4BodyColor = if (isDarkMode) Color(0xFFD6D0C4) else Color(0xFFC8C2B4)
    val c4BlockColor = if (isDarkMode) Color(0xFFC7C1B3) else Color(0xFFB9B3A5)
    val c4BorderColor = if (isDarkMode) Color(0xFF9E9889) else Color(0xFF8C8677)

    val pausedColor = Color(0xFF3B82F6)

    val pausedBlurPaint = remember(d) {
        android.graphics.Paint().apply {
            color = pausedColor.toArgb()
            maskFilter = BlurMaskFilter(15f * d, Blur.NORMAL)
        }
    }
    val pausedBlurRect = remember { android.graphics.RectF() }

    // --- NEW: High Voltage Shock State ---
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val zapAnim = remember { Animatable(0f) }

    // --- NEW: Electric Spark Physics ---
    val sparks = remember { mutableListOf<VisualParticle>() }
    var sparkFrame by remember { mutableLongStateOf(0L) }
    var sparkTrigger by remember { mutableIntStateOf(0) } // <-- NEW STATE

    // --- AGSL SHADER STATE ---
    var shaderTime by remember { mutableFloatStateOf(0f) }
    var touchCount by remember { mutableFloatStateOf(0f) }
    var touch1 by remember { mutableStateOf(Offset(0f, 0f)) }
    var touch2 by remember { mutableStateOf(Offset(0f, 0f)) }

    // THE FIX 1: CACHE THE SHADER ONCE!
    // This compiles the script into GPU memory once when the bomb loads, preventing 120fps crashes.
    val plasmaShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(C4_PLASMA_SHADER)
        } else null
    }

    // Smooth timer specifically for the GPU animation
    LaunchedEffect(zapAnim.value > 0f) {
        var lastTimeNanos = System.nanoTime()
        while (zapAnim.value > 0f) {
            withFrameNanos { nanos ->
                shaderTime += (nanos - lastTimeNanos) / 1_000_000_000f
                lastTimeNanos = nanos
            }
        }
    }

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
                                                val inset = 4.dp.toPx()
                                                pausedBlurRect.set(inset, inset, size.width - inset, size.height - inset)
                                                canvas.nativeCanvas.drawRoundRect(pausedBlurRect, 8.dp.toPx(), 8.dp.toPx(), pausedBlurPaint)
                                            }
                                        }
                                    }
                                    Icon(Icons.Rounded.DeveloperBoard, null, tint = if (isPaused) pausedColor else Color(0xFF64748B), modifier = Modifier.size(32.dp))
                                }
                                // INVISIBLE ANCHOR: Perfectly matches StrokeGlowText properties to prevent sub-pixel jumping
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = pausedText,
                                        color = Color.Transparent,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp,
                                        fontFamily = CustomFont, // Matches Glow component
                                        overflow = TextOverflow.Visible,
                                        softWrap = false
                                    )

                                    if (isPaused) {
                                        StrokeGlowText(pausedText, pausedColor, 8.sp, letterSpacing = 2.sp)
                                    } else {
                                        Text(
                                            text = pausedText,
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
                                StrokeGlowText(emptyTimeText, NeonRed, 56.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Black, strokeWidth = 12f, blurRadius = 40f)
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
                                StrokeGlowText(armedText, NeonRed, 12.sp, blurRadius = 20f)
                            } else {
                                Text(armedText, color = Color(0xFF450a0a), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontFamily = CustomFont)
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
            val shockDesc = stringResource(R.string.desc_trigger_shock)
            Surface(
                color = warningBg,
                border = BorderStroke(1.dp, warningBorder),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    // --- NEW: Accessibility Semantics ---
                    .semantics {
                        role = Role.Button
                        contentDescription = shockDesc
                    }
                    .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()

                            // 1. Find fingers that JUST touched the screen this exact frame
                            val newDowns = event.changes.filter { it.changedToDown() }

                            if (newDowns.isNotEmpty()) {
                                // 2. Get the coordinates of up to 2 currently pressed fingers
                                val activePointers = event.changes.filter { it.pressed }.map { it.position }.take(2)
                                touchCount = activePointers.size.toFloat()
                                if (activePointers.isNotEmpty()) touch1 = activePointers[0]
                                if (activePointers.size > 1) touch2 = activePointers[1]

                                // 3. Trigger the animation and audio
                                coroutineScope.launch {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onShock()
                                    zapAnim.snapTo(1f)
                                    zapAnim.animateTo(0f, tween(1000, easing = FastOutSlowInEasing))
                                }

                                // 4. Spawn 20 sparks for EVERY finger that just touched down!
                                newDowns.forEach { change ->
                                    repeat(20) {
                                        val angle = Math.random() * Math.PI * 2
                                        val speed = (4f + Math.random() * 6f).toFloat()
                                        sparks.add(
                                            VisualParticle(
                                                x = change.position.x,
                                                y = change.position.y,
                                                vx = (cos(angle) * speed).toFloat(),
                                                vy = (sin(angle) * speed).toFloat(),
                                                life = (0.2f + Math.random() * 0.4f).toFloat(),
                                                maxLife = 0.6f
                                            )
                                        )
                                    }
                                }
                                sparkTrigger++
                            }
                        }
                    }
                }
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null, tint = warningTextIcon, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(warningText, color = warningTextIcon, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
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

            // THE OVERLAY CANVAS: GPU Shaders (API 33+) with Canvas Fallback
            Canvas(modifier = Modifier.matchParentSize()) {
                if (zapAnim.value > 0f) {
                    if (plasmaShader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                        // 1. Update dynamic variables on the pre-compiled shader
                        plasmaShader.setFloatUniform("resolution", size.width, size.height)
                        plasmaShader.setFloatUniform("time", shaderTime)
                        plasmaShader.setFloatUniform("intensity", zapAnim.value)

                        // NEW: Pass both touches and the count!
                        plasmaShader.setFloatUniform("touchCount", touchCount)
                        plasmaShader.setFloatUniform("touch1", touch1.x, touch1.y)
                        plasmaShader.setFloatUniform("touch2", touch2.x, touch2.y)

                        // 2. Draw the GPU effect instantly!
                        drawRect(brush = ShaderBrush(plasmaShader))

                    } else {
                        // FALLBACK FOR ANDROID 12 AND BELOW (Optional: You can paste your old static circle drawing here)
                    }
                }

                // THE FIX 2: FREED THE SPARKS!
                // This sits entirely outside the shader's if/else block, so the physical sparks
                // always explode outward, regardless of whether the phone is using the GPU shader or not!
                if (sparkFrame >= 0) {
                    sparks.forEach { s ->
                        val alpha = (s.life / s.maxLife).coerceIn(0f, 1f)
                        val coreRadius = 3.dp.toPx() * alpha
                        val glowRadius = coreRadius * 4f

                        if (glowRadius > 0f) {
                            withTransform({
                                translate(s.x, s.y)
                                scale(glowRadius / 100f, glowRadius / 100f, pivot = Offset.Zero)
                            }) {
                                drawCircle(brush = baseGlowBrush, radius = 100f, center = Offset.Zero, alpha = alpha)
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
    val tickOffsetY = -140f * d
    val dingOffsetY = -170f * d

    // --- HOISTED PATHS (Prevents 60fps Garbage Collection Churn!) ---
    val stickShadowPath = remember { Path() }
    val yellowWirePath = remember { Path() }
    val redWirePath = remember { Path() }
    val blueWirePath = remember { Path() }
    val facePath = remember { Path() }
    val lightPath = remember { Path() }
    val crescentPath = remember { Path() }
    val glarePath = remember { Path() }

    // --- NEW: HOISTED STROKES ---
    val wireStroke = remember(d) { Stroke(width = 5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val glossStroke = remember(d) { Stroke(width = 1.5f * d, cap = StrokeCap.Round, join = StrokeJoin.Round) }
    val clockOutlineStroke = remember(d) { Stroke(width = clockStroke) }

    // --- RESTORED: Explicitly Sized Local Brushes ---
    // 1. Dynamite Sticks (Horizontal: 0 to 60dp wide)
    val stickBrush = remember(d) { Brush.horizontalGradient(colors = listOf(Color(0xFFB91C1C), Color(0xFFEF4444), Color(0xFFB91C1C)), startX = 0f, endX = 60f * d) }

    // 2. Hammer Shaft (Vertical: Top to Bottom, 0 to 4.5dp thick)
    val shaftBrush = remember(d) { Brush.verticalGradient(colors = listOf(Color(0xFF4B5563), Color(0xFFE5E7EB), Color(0xFF4B5563)), startY = 0f, endY = 4.5f * d) }

    // 3. Hammer Head (Vertical: Top to Bottom, 0 to 16dp tall)
    val headBrush = remember(d) { Brush.verticalGradient(colors = listOf(Color.White, Color(0xFF9CA3AF), Color(0xFF374151)), startY = 0f, endY = 16f * d) }

    // 4. Bell Knob (Horizontal: Right to Left, 0 to 8dp wide)
    val knobBrush = remember(d) { Brush.horizontalGradient(colors = listOf(Color.White, Color(0xFFEAB308), Color(0xFF78350F)), startX = 8f * d, endX = 5f) }

    // 5. Brass Bell Body (Horizontal: Right to Left, mapping to its -28dp to +28dp drawing bounds)
    val bellBrush = remember(d) { Brush.horizontalGradient(colors = listOf(Color.White, Color(0xFFEAB308), Color(0xFF78350F)), startX = 28f * d, endX = 0f * d) }

    // 6. Clock Base (Vertical metallic shine: Top to Bottom, -80dp to +80dp)
    val clockBaseBrush = remember(d) {
        Brush.verticalGradient(
            colors = listOf(MetallicLight, MetallicDark),
            startY = -80f * d,
            endY = 80f * d
        )
    }

    // 7. Clock Rim (Vertical Dark Gray Metallic shine: Top to Bottom)
    val clockRimBrush = remember(d) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF334155), Color(0xFF0F172A)), // Light slate edge to deep slate shadow
            startY = -80f * d,
            endY = 80f * d
        )
    }

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

    val acmeText = stringResource(R.string.acme_corp)
    val explosiveText = stringResource(R.string.high_explosive)
    val tickText = stringResource(R.string.tick)
    val dingText = stringResource(R.string.ding)

    val textMeasurer = rememberTextMeasurer()

    // --- OPTIMIZATION: Cache the text layout calculations! ---
    val textLayoutResult = remember(d, acmeText) {
        textMeasurer.measure(
            text = acmeText,
            style = TextStyle(color = TextGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
        )
    }
    val stickTextResult = remember(d, explosiveText) {
        textMeasurer.measure(
            text = explosiveText,
            style = TextStyle(color = Color.Black.copy(alpha=0.3f), fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = CustomFont)
        )
    }

    val tickLayoutResult = remember(d, tickText) {
        textMeasurer.measure(
            text = tickText,
            style = TextStyle(color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
        )
    }

    // We measure "DING!" once with its specific gradient brush.
    val dingLayoutResult = remember(d, dingText) {
        val dingBrush = Brush.verticalGradient(listOf(Color(0xFFFFFACD), Color(0xFFFFD700)))
        textMeasurer.measure(
            text = dingText,
            style = TextStyle(brush = dingBrush, fontSize = 48.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
        )
    }

    // --- CACHED GLARE BRUSH ---
    val glareBrush = remember(d) {
        val staticCenter = Offset(80f * d, 80f * d) // Center of the 160.dp canvas
        val gCenter = Offset(staticCenter.x - clockRad * 0.35f, staticCenter.y - clockRad * 0.35f)
        Brush.radialGradient(
            colors = listOf(Color(0xFFE0F7FA).copy(alpha = 0.3f), Color.Transparent),
            center = gCenter,
            radius = clockRad * 1.2f
        )
    }

    val textEffects = remember { mutableListOf<VisualText>() }
    var hasShownDing by remember { mutableStateOf(false) }

    // --- NEW: Bell Decay Animation ---
    val vibrationMagnitude = remember { Animatable(1f) }
    LaunchedEffect(hasShownDing) {
        if (hasShownDing) {
            // Decays the multiplier from 1f to 0f over 2 seconds!
            vibrationMagnitude.animateTo(0f, tween(2000, easing = LinearOutSlowInEasing))
        }
    }

    val currentTimeLeft by rememberUpdatedState(timeLeft)
    val currentIsPaused by rememberUpdatedState(isPaused)

    // THE FIX: Unified Physics & Bulletproof Trigger Loop
    LaunchedEffect(Unit) {
        var lastTime = 0L
        var localLastTickTime = Float.MAX_VALUE // Guarantees an immediate tick on frame 1
        var localHasShownDing = false

        while(true) {
            withFrameNanos { nanos ->
                val dt = if (lastTime == 0L) 0.016f else ((nanos - lastTime) / 1_000_000_000f).coerceAtMost(0.1f)
                lastTime = nanos

                val tLeft = currentTimeLeft
                val paused = currentIsPaused

                // 1. Check for Ticks
                val isFast = tLeft <= 5f
                val tickInterval = if (isFast) 0.5f else 1.0f

                // --- THE FIX: Removed the manual pause reset! ---
                // By just checking !paused, it remembers the last TRUE tick (e.g., 10.0)
                // and perfectly waits for the next integer boundary (e.g., 9.0) even if paused in between!
                if (!paused && (localLastTickTime - tLeft >= tickInterval) && tLeft > 1.1f) {
                    textEffects.add(VisualText(tickText, (Math.random() * 100 - 50).toFloat(), tickOffsetY, if (isFast) NeonRed else TextGray, fontSize = 20f))
                    // Snap the tracker to the perfect mathematical interval to prevent drifting
                    localLastTickTime = kotlin.math.ceil(tLeft / tickInterval) * tickInterval
                }

                // 2. Check for the Ding
                if (tLeft <= 1.0f && !localHasShownDing && tLeft > 0 && !paused) {
                    textEffects.add(VisualText(dingText, 0f, dingOffsetY, Color(0xFFFFD700), listOf(Color(0xFFFFFACD), Color(0xFFFFD700)), life = 2.0f, fontSize = 48f))
                    localHasShownDing = true
                    hasShownDing = true
                }

                // 3. Update Physics
                for (i in textEffects.indices.reversed()) {
                    val effect = textEffects[i]
                    effect.life -= dt
                    if (effect.life <= 0f) {
                        textEffects.removeAt(i)
                    } else {
                        effect.y -= (15f * dt)
                        effect.alpha = (effect.life / 1.0f).coerceIn(0f, 1f)
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

                    // USE CACHED PATH!
                    stickShadowPath.reset()
                    stickShadowPath.moveTo(stickLeft, top)
                    stickShadowPath.lineTo(stickLeft, bottom)
                    stickShadowPath.quadraticTo(
                        x1 = stickLeft + (stickW / 2f),
                        y1 = bottom + curveOffset,
                        x2 = right,
                        y2 = bottom
                    )
                    stickShadowPath.lineTo(right, top)
                    stickShadowPath.close()

                    drawPath(path = stickShadowPath, color = Color.Black.copy(alpha = 0.25f))
                }

                // Draw Red Stick Body
                withTransform({ translate(stickLeft, startY) }) {
                    drawRoundRect(brush = stickBrush, topLeft = Offset.Zero, size = Size(stickW, stickH + 30f * d), cornerRadius = CornerRadius(cornerRad, cornerRad))

                    val stickCenterX = stickW / 2f
                    val stickCenterY = (stickH + 30f * d) / 2f
                    withTransform({
                        translate(stickCenterX, stickCenterY)
                        rotate(-90f, pivot = Offset.Zero)
                    }) {
                        drawText(textLayoutResult = stickTextResult, topLeft = Offset(-stickTextResult.size.width / 2f, -stickTextResult.size.height / 2f))
                    }
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
            yellowWirePath.reset()
            yellowWirePath.moveTo(midStickX, clockCy)
            yellowWirePath.lineTo(midStickX, trueCurvePeakY)

            // Red Wire (Left Stick)
            redWirePath.reset()
            redWirePath.moveTo(leftStickX, startY + 20f * d)
            redWirePath.cubicTo(
                x1 = leftStickX, y1 = apexY,
                x2 = clockCx - 8f * d, y2 = apexY,
                x3 = clockCx - 4f * d, y3 = meetY
            )
            redWirePath.lineTo(clockCx - 4f * d, clockCy)

            // Blue Wire (Right Stick)
            blueWirePath.reset()
            blueWirePath.moveTo(rightStickX, startY + 20f * d)
            blueWirePath.cubicTo(
                x1 = rightStickX, y1 = apexY,
                x2 = clockCx + 8f * d, y2 = apexY,
                x3 = clockCx + 4f * d, y3 = meetY
            )
            blueWirePath.lineTo(clockCx + 4f * d, clockCy)

            // Draw Wire Shadows
            if (!isDarkMode) {
                // --- UPDATED: 0f left offset to cast the shadow straight down! ---
                translate(left = 0f, top = 8f * d) {
                    val shadowColor = Color.Black.copy(alpha = 0.35f)
                    drawPath(path = yellowWirePath, color = shadowColor, style = wireStroke)
                    drawPath(path = redWirePath, color = shadowColor, style = wireStroke)
                    drawPath(path = blueWirePath, color = shadowColor, style = wireStroke)
                }
            }

            // Draw Colored Plastic
            drawPath(path = yellowWirePath, color = Color(0xFFEAB308), style = wireStroke)
            drawPath(path = redWirePath, color = Color(0xFFEF4444), style = wireStroke)
            drawPath(path = blueWirePath, color = Color(0xFF3B82F6), style = wireStroke)

            // Draw Glossy Highlights
            translate(top = -2f * d) {
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
                        // 1. The Bell Knob
                        withTransform({ translate(bellRadius + -4f * d, -4f * d) }) { // Shift translation up
                            drawCircle(brush = knobBrush, radius = 4f * d, center = Offset(4f * d, 4f * d)) // Draw in positive space
                        }

                        // Brass Bell Body
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
                    // 2. Hammer Shaft
                    withTransform({ translate(shaftStart, -shaftWidth / 2f) }) { // Apply Y offset to translation
                        drawRect(brush = shaftBrush, topLeft = Offset.Zero, size = Size(shaftLength, shaftWidth)) // Draw at Zero
                    }

                    // Head: Metallic Gray, lighter at the top, darker at the bottom (Shading down the local Y axis)
                    // 3. Hammer Head
                    withTransform({ translate(shaftEnd, -headH / 2f) }) { // Apply Y offset to translation
                        drawRoundRect(brush = headBrush, topLeft = Offset.Zero, size = Size(headW, headH), cornerRadius = CornerRadius(4f * d, 4f * d)) // Draw at Zero
                    }
                }

                // The actual metal clock base
                withTransform({ translate(clockCenter.x, clockCenter.y) }) {
                    drawCircle(
                        brush = clockBaseBrush,
                        radius = clockRad,
                        center = Offset.Zero // Draws at local 0,0 to match the brush bounds!
                    )
                }

                // USE CACHED STROKE AND BRUSH!
                withTransform({ translate(clockCenter.x, clockCenter.y) }) {
                    drawCircle(
                        brush = clockRimBrush, // <--- Replaced solid color with the Gradient Brush!
                        radius = clockRad,
                        center = Offset.Zero,
                        style = clockOutlineStroke // <--- Keeps it hollow!
                    )
                }

                drawText(textLayoutResult = textLayoutResult, topLeft = Offset(clockCenter.x - textLayoutResult.size.width / 2, clockCenter.y + textYOffset))

                for (index in 0 until 60) {
                    val isHour = index % 5 == 0
                    rotate(index * 6f, pivot = clockCenter) { drawLine(color = Slate800, start = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap), end = Offset(clockCenter.x, clockCenter.y - clockRad + tickGap + if(isHour) tickL else tickS), strokeWidth = if(isHour) tickWidthLong else tickWidthShort) }
                }
                rotate(-((timeLeft / 60) * 6f), pivot = clockCenter) { drawLine(color = Slate800, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handS), strokeWidth = 8f, cap = StrokeCap.Round) }
                rotate(-((timeLeft % 60) * 6f), pivot = clockCenter) { drawLine(color = NeonRed, start = clockCenter, end = Offset(clockCenter.x, clockCenter.y - handL), strokeWidth = 4f, cap = StrokeCap.Round) }
                drawCircle(color = Slate800, radius = pinL, center = clockCenter)
                drawCircle(color = NeonRed, radius = pinS, center = clockCenter)

                // --- CONSTRAINED GLARE ---
                // 1. Create a stencil that perfectly matches the inner rim of the clock
                facePath.reset()
                facePath.addOval(Rect(center = clockCenter, radius = clockRad - (clockStroke / 2f)))

                // 2. Draw the glare inside the stencil using the CACHED brush!
                clipPath(facePath) {
                    val glareCenter = Offset(clockCenter.x - clockRad * 0.35f, clockCenter.y - clockRad * 0.35f)
                    drawCircle(
                        brush = glareBrush, // <--- ZERO ALLOCATION HERE NOW!
                        radius = clockRad * 1.2f,
                        center = glareCenter
                    )
                }

                // --- PERFECTED: True Hard-Edged Crescent Shadow ---
                if (!isDarkMode) {
                    val innerRimRadius = clockRad - (clockStroke / 2f) + 0.5f

                    // 1. The full inner circle of the clock face
                    facePath.reset()
                    facePath.addOval(Rect(center = clockCenter, radius = innerRimRadius))

                    // 2. The "Light" cutout circle, shifted down
                    val shadowThickness = 12f * d
                    lightPath.reset()
                    lightPath.addOval(Rect(center = Offset(clockCenter.x, clockCenter.y + shadowThickness), radius = innerRimRadius))

                    // 3. Subtract the light from the face to get a mathematically perfect crescent!
                    crescentPath.reset()
                    crescentPath.op(facePath, lightPath, PathOperation.Difference)

                    drawPath(path = crescentPath, color = Color.Black.copy(alpha = 0.25f))
                }

                // --- NEW: Sharp "Glass Cut" Reflection ---
                glarePath.reset()
                val glareRect = Rect(center = clockCenter, radius = clockRad * 0.88f)
                // Sweep a chunk of the top-left rim
                glarePath.arcTo(glareRect, startAngleDegrees = 185f, sweepAngleDegrees = 95f, forceMoveTo = true)

                val startX = clockCenter.x + clockRad * 0.88f * cos(Math.toRadians(185.0)).toFloat()
                val startY = clockCenter.y + clockRad * 0.88f * sin(Math.toRadians(185.0)).toFloat()

                // Draw a sharp, swooping curve back across the glass
                glarePath.quadraticTo(
                    x1 = clockCenter.x - (clockRad * 0.1f),
                    y1 = clockCenter.y - (clockRad * 0.1f),
                    x2 = startX,
                    y2 = startY
                )
                glarePath.close()

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
                // 1. Pick the cached layout based on the text content
                val textResult = when (effect.text) {
                    tickText -> tickLayoutResult
                    dingText -> dingLayoutResult
                    else -> {
                        // Fallback for any unknown text (creates a new style on the fly)
                        val style = if (effect.gradientColors != null) {
                            TextStyle(brush = Brush.verticalGradient(effect.gradientColors), fontSize = effect.fontSize.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                        } else {
                            TextStyle(color = effect.color, fontSize = effect.fontSize.sp, fontWeight = FontWeight.Bold, fontFamily = CustomFont)
                        }
                        textMeasurer.measure(effect.text, style)
                    }
                }

                // 2. Draw it!
                val topLeft = Offset(cx + effect.x - (textResult.size.width / 2), cy + effect.y - (textResult.size.height / 2))

                if (effect.text == tickText) {
                    // For TICK: We override the cached Black color with the specific Red or Gray needed
                    drawText(textLayoutResult = textResult, color = effect.color.copy(alpha = effect.alpha), topLeft = topLeft)
                } else {
                    // For DING (or others): We use the cached gradient and just apply the alpha
                    drawText(textLayoutResult = textResult, alpha = effect.alpha, topLeft = topLeft)
                }
            }
        }
    }
}