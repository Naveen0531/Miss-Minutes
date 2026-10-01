package com.tva.missminutes.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.tva.missminutes.viewmodel.AssistantState
import kotlinx.coroutines.isActive
import kotlin.math.*

/**
 * HolographicOrb v2 — Living AI Sphere.
 *
 * Inspired by Jarvis/Friday from Iron Man + TVA holographic aesthetic.
 * No clock face — this is a living, breathing AI entity.
 *
 * Visual layers (back to front):
 *  1. Deep ambient radial glow (state-colored)
 *  2. Outer energy field rings (rotating)
 *  3. Particle orbit ring (60 particles)
 *  4. Core plasma sphere with gradient
 *  5. Inner energy lattice (rotating opposite)
 *  6. Plasma surface ripples (speaking/listening)
 *  7. Face / expression (Miss Minutes identity)
 *  8. Inner bright core highlight
 *  9. Audio waveform blast ring (speaking only)
 * 10. Scanline overlay
 */
@Composable
fun HolographicOrb(
    state: AssistantState,
    audioAmplitude: Float,
    modifier: Modifier = Modifier
) {
    val isListening  = state is AssistantState.Listening
    val isThinking   = state is AssistantState.Thinking
    val isSpeaking   = state is AssistantState.Speaking
    val isIdle       = state is AssistantState.Idle || state is AssistantState.Initializing
    val isError      = state is AssistantState.Error

    // ── State-driven color palette ─────────────────────────────────────────
    // Idle: warm amber | Listening: electric cyan-blue | Thinking: violet pulse | Speaking: gold bloom
    val coreColorTarget = when {
        isListening -> Color(0xFF00CFFF)   // Electric cyan — feels alive, actively hearing
        isThinking  -> Color(0xFFBB66FF)   // Violet/purple — computational, mysterious
        isSpeaking  -> Color(0xFFFFCC44)   // Warm gold — bright, confident, speaking
        isError     -> Color(0xFFFF4444)   // Red — error state
        else        -> Color(0xFFFF8C42)   // Warm amber — Miss Minutes' signature color
    }
    val ringColorTarget = when {
        isListening -> Color(0xFF00A8FF)
        isThinking  -> Color(0xFF9944DD)
        isSpeaking  -> Color(0xFFFFAA22)
        isError     -> Color(0xFFDD2222)
        else        -> Color(0xFFFF7733)
    }
    val glowColorTarget = when {
        isListening -> Color(0xFF003355)   // Deep blue ambient
        isThinking  -> Color(0xFF220033)   // Deep violet ambient
        isSpeaking  -> Color(0xFF331100)   // Deep orange ambient
        isError     -> Color(0xFF330000)
        else        -> Color(0xFF1A0600)   // Deep warm amber
    }

    val coreColor by animateColorAsState(
        targetValue   = coreColorTarget,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label         = "coreColor"
    )
    val ringColor by animateColorAsState(
        targetValue   = ringColorTarget,
        animationSpec = tween(600),
        label         = "ringColor"
    )
    val glowColor by animateColorAsState(
        targetValue   = glowColorTarget,
        animationSpec = tween(800),
        label         = "glowColor"
    )

    // ── Glow intensity ─────────────────────────────────────────────────────
    val glowAlpha by animateFloatAsState(
        targetValue = when {
            isSpeaking  -> 0.65f + audioAmplitude * 0.35f
            isThinking  -> 0.55f
            isListening -> 0.50f
            else        -> 0.30f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "glowAlpha"
    )

    // ── Core scale ────────────────────────────────────────────────────────
    val idleInfinite = rememberInfiniteTransition(label = "idle")
    val idlePulse by idleInfinite.animateFloat(
        initialValue = 0.97f,
        targetValue  = 1.03f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse"
    )
    val thinkInfinite = rememberInfiniteTransition(label = "think")
    val thinkFlicker by thinkInfinite.animateFloat(
        initialValue = 0.98f,
        targetValue  = 1.05f,
        animationSpec = infiniteRepeatable(tween(180, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "thinkFlicker"
    )
    val audioPulseScale by animateFloatAsState(
        targetValue   = 1.0f + audioAmplitude.coerceIn(0f, 1f) * 0.07f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessHigh),
        label         = "audioScale"
    )
    val effectiveScale = when {
        isSpeaking  -> audioPulseScale
        isThinking  -> thinkFlicker
        isListening -> idlePulse * 1.04f
        else        -> idlePulse
    }

    // ── Face expression ────────────────────────────────────────────────────
    val faceAlpha by animateFloatAsState(
        targetValue = when {
            isSpeaking  -> 1.0f
            isListening -> 0.95f
            isThinking  -> 0.80f
            else        -> 0.70f
        },
        animationSpec = tween(500),
        label = "faceAlpha"
    )

    // ── Rotation angles (frame-by-frame) ──────────────────────────────────
    var outerRingRot   by remember { mutableFloatStateOf(0f) }
    var innerLatticeRot by remember { mutableFloatStateOf(0f) }
    var particlePhase  by remember { mutableFloatStateOf(0f) }
    var plasmaPhase    by remember { mutableFloatStateOf(0f) }
    var lastFrameMs    by remember { mutableLongStateOf(0L) }

    // Speed multipliers per state
    val outerSpeed = when {
        isSpeaking  -> 180f
        isThinking  -> 110f
        isListening -> 70f
        else        -> 22f
    }
    val targetOuterSpeed by animateFloatAsState(
        targetValue   = outerSpeed,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label         = "outerSpeed"
    )

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { ms ->
                val delta = if (lastFrameMs == 0L) 16L else (ms - lastFrameMs).coerceIn(0L, 64L)
                lastFrameMs = ms

                val dt = delta / 1000f
                outerRingRot    = (outerRingRot    + targetOuterSpeed * dt) % 360f
                innerLatticeRot = (innerLatticeRot - targetOuterSpeed * 0.6f * dt + 360f) % 360f
                particlePhase   = (particlePhase   + dt * 1.2f) % (2f * PI.toFloat())
                plasmaPhase     = (plasmaPhase     + dt * 2.5f) % (2f * PI.toFloat())
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(220.dp)
            .graphicsLayer(scaleX = effectiveScale, scaleY = effectiveScale)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) / 2f

            drawOrb(
                center         = center,
                radius         = radius,
                coreColor      = coreColor,
                ringColor      = ringColor,
                glowColor      = glowColor,
                glowAlpha      = glowAlpha,
                faceAlpha      = faceAlpha,
                outerRingRot   = outerRingRot,
                innerLatticeRot = innerLatticeRot,
                particlePhase  = particlePhase,
                plasmaPhase    = plasmaPhase,
                audioAmplitude = audioAmplitude,
                isSpeaking     = isSpeaking,
                isListening    = isListening,
                isThinking     = isThinking
            )
        }
    }
}

// ── All drawing in one pure function ───────────────────────────────────────────
private fun DrawScope.drawOrb(
    center: Offset,
    radius: Float,
    coreColor: Color,
    ringColor: Color,
    glowColor: Color,
    glowAlpha: Float,
    faceAlpha: Float,
    outerRingRot: Float,
    innerLatticeRot: Float,
    particlePhase: Float,
    plasmaPhase: Float,
    audioAmplitude: Float,
    isSpeaking: Boolean,
    isListening: Boolean,
    isThinking: Boolean
) {
    // ── Layer 1: Deep ambient glow ─────────────────────────────────────────
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to coreColor.copy(alpha = glowAlpha * 0.45f),
                0.4f to coreColor.copy(alpha = glowAlpha * 0.20f),
                0.70f to ringColor.copy(alpha = glowAlpha * 0.08f),
                1.0f to Color.Transparent
            ),
            center = center,
            radius = radius * 1.8f
        ),
        radius = radius * 1.8f,
        center = center
    )

    // ── Layer 2: Outer energy field rings (3 rotating rings) ──────────────
    rotate(outerRingRot, center) {
        for (i in 0..2) {
            val startAngle = i * 120f
            drawArc(
                color      = ringColor.copy(alpha = 0.65f),
                startAngle = startAngle,
                sweepAngle = 80f,
                useCenter  = false,
                topLeft    = Offset(center.x - radius * 0.96f, center.y - radius * 0.96f),
                size       = Size(radius * 1.92f, radius * 1.92f),
                style      = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
            // Dot at arc end
            val endRad = Math.toRadians((startAngle + 80.0))
            drawCircle(
                color  = ringColor,
                radius = 3.5.dp.toPx(),
                center = Offset(
                    center.x + cos(endRad).toFloat() * radius * 0.96f,
                    center.y + sin(endRad).toFloat() * radius * 0.96f
                )
            )
        }
        // Thin full ring
        drawCircle(
            color  = ringColor.copy(alpha = 0.15f),
            radius = radius * 0.96f,
            center = center,
            style  = Stroke(1.dp.toPx())
        )
    }

    // ── Layer 3: Particle orbit ring (60 particles) ───────────────────────
    val particleRingR = radius * 0.82f
    for (i in 0 until 48) {
        val baseAngle = (i * 360f / 48f)
        val angle = baseAngle + particlePhase * (180f / PI.toFloat()) * 0.4f
        val angleRad = Math.toRadians(angle.toDouble())

        // Size varies with position for depth illusion
        val t = ((sin(angleRad * 2) + 1) / 2).toFloat()
        val pRadius = 1.5f + t * 2.5f
        val pAlpha  = 0.25f + t * 0.55f

        drawCircle(
            color  = coreColor.copy(alpha = pAlpha),
            radius = pRadius.dp.toPx(),
            center = Offset(
                center.x + cos(angleRad).toFloat() * particleRingR,
                center.y + sin(angleRad).toFloat() * particleRingR
            )
        )
    }

    // ── Layer 4: Core plasma sphere ────────────────────────────────────────
    // Background dark fill
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.9f),
                Color(0xFF060206).copy(alpha = 0.98f),
                Color(0xFF020102)
            ),
            center = center,
            radius = radius * 0.76f
        ),
        radius = radius * 0.76f,
        center = center
    )

    // Plasma surface gradient (state-colored)
    val plasmaShift = audioAmplitude * 0.15f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to coreColor.copy(alpha = (0.25f + plasmaShift).coerceIn(0f, 1f)),
                0.4f to coreColor.copy(alpha = (0.12f + plasmaShift * 0.5f).coerceIn(0f, 1f)),
                0.75f to coreColor.copy(alpha = 0.04f),
                1.0f to Color.Transparent
            ),
            center = Offset(
                center.x - radius * 0.12f,
                center.y - radius * 0.12f
            ),
            radius = radius * 0.7f
        ),
        radius = radius * 0.76f,
        center = center
    )

    // Rim light — bright edge highlight
    drawCircle(
        color  = coreColor.copy(alpha = 0.70f),
        radius = radius * 0.76f,
        center = center,
        style  = Stroke(width = 3.dp.toPx())
    )
    // Inner rim
    drawCircle(
        color  = coreColor.copy(alpha = 0.20f),
        radius = radius * 0.70f,
        center = center,
        style  = Stroke(width = 1.dp.toPx())
    )

    // ── Layer 5: Inner energy lattice (counter-rotating) ─────────────────
    rotate(innerLatticeRot, center) {
        for (i in 0..5) {
            val startAngle = i * 60f
            drawArc(
                color = coreColor.copy(alpha = 0.35f),
                startAngle = startAngle,
                sweepAngle = 40f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.55f, center.y - radius * 0.55f),
                size = Size(radius * 1.10f, radius * 1.10f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        drawCircle(
            color = coreColor.copy(alpha = 0.10f),
            radius = radius * 0.55f,
            center = center,
            style = Stroke(1.dp.toPx())
        )
    }

    // ── Layer 6: Plasma surface ripples (listening/speaking) ──────────────
    if (isListening || isSpeaking) {
        val numRipples = if (isSpeaking) 3 else 2
        for (r in 0 until numRipples) {
            val phase = plasmaPhase + r * (2f * PI.toFloat() / numRipples)
            val rippleR = radius * (0.50f + 0.20f * ((sin(phase.toDouble()) + 1) / 2).toFloat())
            val rippleA = 0.30f * ((sin(phase.toDouble() + 1.5) + 1) / 2).toFloat()
            drawCircle(
                color = coreColor.copy(alpha = rippleA + audioAmplitude * 0.2f),
                radius = rippleR,
                center = center,
                style = Stroke(1.5.dp.toPx())
            )
        }
    }

    // ── Layer 7: Miss Minutes Face ─────────────────────────────────────────
    drawFace(
        center     = center,
        radius     = radius,
        faceAlpha  = faceAlpha,
        faceColor  = coreColor,
        audioAmp   = audioAmplitude,
        isSpeaking = isSpeaking,
        plasmaPhase = plasmaPhase
    )

    // ── Layer 8: Core bright highlight (inner light source) ───────────────
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.18f + audioAmplitude * 0.12f),
                coreColor.copy(alpha = 0.10f),
                Color.Transparent
            ),
            center = Offset(center.x - radius * 0.18f, center.y - radius * 0.18f),
            radius = radius * 0.32f
        ),
        radius = radius * 0.32f,
        center = Offset(center.x - radius * 0.18f, center.y - radius * 0.18f)
    )

    // ── Layer 9: Audio-reactive waveform blast (speaking/listening) ────────
    if ((isSpeaking || isListening) && audioAmplitude > 0.05f) {
        val numBars = 64
        for (i in 0 until numBars) {
            val angleRad = Math.toRadians(i * 360.0 / numBars)
            val waveAmp = (sin(i * 0.7 + plasmaPhase.toDouble() * 3.0) * 0.5 + 0.5).toFloat()
            val expansion = audioAmplitude * radius * 0.5f * waveAmp
            val innerR = radius * 0.965f
            val outerR = innerR + expansion

            if (outerR > innerR) {
                drawLine(
                    color = coreColor.copy(alpha = (audioAmplitude * 0.75f * waveAmp).coerceIn(0f, 1f)),
                    start = Offset(
                        center.x + cos(angleRad).toFloat() * innerR,
                        center.y + sin(angleRad).toFloat() * innerR
                    ),
                    end = Offset(
                        center.x + cos(angleRad).toFloat() * outerR,
                        center.y + sin(angleRad).toFloat() * outerR
                    ),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
        }
        // Resonant outer bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    coreColor.copy(alpha = audioAmplitude * 0.35f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.1f + audioAmplitude * 0.35f)
            ),
            radius = radius * (1.1f + audioAmplitude * 0.35f),
            center = center
        )
    }

    // ── Layer 10: CRT scanline overlay ─────────────────────────────────────
    val step = 5.dp.toPx()
    var y = 0f
    while (y < size.height) {
        drawLine(
            color = coreColor.copy(alpha = 0.025f),
            start = Offset(0f, y),
            end   = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

// ── Face Drawing ───────────────────────────────────────────────────────────────
private fun DrawScope.drawFace(
    center: Offset,
    radius: Float,
    faceAlpha: Float,
    faceColor: Color,
    audioAmp: Float,
    isSpeaking: Boolean,
    plasmaPhase: Float
) {
    val faceY = center.y - radius * 0.08f
    val eyeSpacing = radius * 0.24f
    val eyeR = radius * 0.095f
    val eyeY = faceY - radius * 0.07f

    // ── Eyes ──────────────────────────────────────────────────────────────
    for (side in listOf(-1f, 1f)) {
        val eyeX = center.x + side * eyeSpacing

        // Eye socket
        drawCircle(
            color  = Color(0xFF050208),
            radius = eyeR * 1.2f,
            center = Offset(eyeX, eyeY)
        )
        // Eye glow halo
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(faceColor.copy(alpha = faceAlpha * 0.6f), Color.Transparent),
                center = Offset(eyeX, eyeY),
                radius = eyeR * 1.8f
            ),
            radius = eyeR * 1.8f,
            center = Offset(eyeX, eyeY)
        )
        // Iris
        drawCircle(
            color  = faceColor.copy(alpha = faceAlpha),
            radius = eyeR,
            center = Offset(eyeX, eyeY)
        )
        // Pupil
        drawCircle(
            color  = Color(0xFF0A0208),
            radius = eyeR * 0.52f,
            center = Offset(eyeX, eyeY)
        )
        // Specular highlight
        drawCircle(
            color  = Color.White.copy(alpha = faceAlpha * 0.85f),
            radius = eyeR * 0.20f,
            center = Offset(eyeX + eyeR * 0.22f, eyeY - eyeR * 0.22f)
        )
    }

    // ── Animated mouth (open when speaking) ───────────────────────────────
    val mouthY = faceY + radius * 0.14f
    val mouthW = eyeSpacing * 2.3f
    val mouthH = radius * 0.12f + (if (isSpeaking) audioAmp * radius * 0.10f else 0f)
    val mouthOpen = if (isSpeaking) audioAmp * 28f else 0f

    // Mouth path (arc)
    drawArc(
        color      = faceColor.copy(alpha = faceAlpha * 0.9f),
        startAngle = 8f  - mouthOpen / 2f,
        sweepAngle = 164f + mouthOpen,
        useCenter  = false,
        topLeft    = Offset(center.x - mouthW / 2f, mouthY - mouthH * 0.2f),
        size       = Size(mouthW, mouthH),
        style      = Stroke(
            width = (2.5.dp.toPx() + if (isSpeaking) audioAmp * 2f else 0f),
            cap   = StrokeCap.Round
        )
    )

    // Subtle cheek blush
    for (side in listOf(-1f, 1f)) {
        drawCircle(
            color  = faceColor.copy(alpha = faceAlpha * 0.14f),
            radius = eyeR * 1.2f,
            center = Offset(center.x + side * eyeSpacing * 1.65f, eyeY + eyeR * 0.9f)
        )
    }
}
