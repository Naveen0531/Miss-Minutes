package com.tva.missminutes.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tva.missminutes.viewmodel.AssistantState
import kotlin.math.sin

/**
 * MicButton v2 — Jarvis-style activation button.
 *
 * States:
 *  Idle      → amber mic icon, subtle pulse
 *  Listening → animated waveform bars (5 bars, equalizer style)
 *  Thinking  → rotating dashed ring, pulsing dimly
 *  Speaking  → play icon, orange glow — tap to interrupt
 *  Error     → red flash, retry indication
 */
@Composable
fun MicButton(
    state: AssistantState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 78.dp,
    listenAmplitude: Float = 0f
) {
    val isListening = state is AssistantState.Listening
    val isThinking  = state is AssistantState.Thinking
    val isSpeaking  = state is AssistantState.Speaking
    val isError     = state is AssistantState.Error

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // ── Animated scale on press ──────────────────────────────────────────
    val buttonScale by animateFloatAsState(
        targetValue = when {
            isPressed   -> 0.88f
            isListening -> 1.08f
            else        -> 1.0f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "btnScale"
    )

    // ── State colors ──────────────────────────────────────────────────────
    val borderColor = when {
        isListening -> Color(0xFF00CFFF)
        isThinking  -> Color(0xFFBB66FF)
        isSpeaking  -> Color(0xFFFFCC44)
        isError     -> Color(0xFFFF4444)
        else        -> Color(0xFFFF8C42)
    }
    val bgColor = when {
        isListening -> Color(0xFF00CFFF)
        isThinking  -> Color(0xFFBB66FF)
        isSpeaking  -> Color(0xFFFFCC44)
        isError     -> Color(0xFFFF4444)
        else        -> Color(0xFFFF8C42)
    }

    val borderAlpha by animateFloatAsState(
        targetValue = when {
            isListening -> 0.95f
            isSpeaking  -> 0.85f
            isThinking  -> 0.65f
            else        -> 0.50f
        },
        animationSpec = tween(400),
        label = "borderAlpha"
    )
    val bgAlpha by animateFloatAsState(
        targetValue = when {
            isListening -> 0.22f
            isSpeaking  -> 0.18f
            isThinking  -> 0.12f
            else        -> 0.07f
        },
        animationSpec = tween(400),
        label = "bgAlpha"
    )

    // ── Expanding pulse rings (listening) ─────────────────────────────────
    val pulseInfinite = rememberInfiniteTransition(label = "pulse")
    val ring1Scale by pulseInfinite.animateFloat(
        initialValue = 1.0f, targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "r1"
    )
    val ring1Alpha by pulseInfinite.animateFloat(
        initialValue = 0.65f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "r1a"
    )
    val ring2Scale by pulseInfinite.animateFloat(
        initialValue = 1.0f, targetValue = 1.9f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing, delayMillis = 600), RepeatMode.Restart),
        label = "r2"
    )
    val ring2Alpha by pulseInfinite.animateFloat(
        initialValue = 0.55f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing, delayMillis = 600), RepeatMode.Restart),
        label = "r2a"
    )

    // ── Thinking spin animation ────────────────────────────────────────────
    val thinkRotation by pulseInfinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "thinkSpin"
    )

    // ── Waveform bars time source ──────────────────────────────────────────
    var waveTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isListening) {
        while (isListening) {
            withFrameMillis { ms -> waveTime = ms / 200f }
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {

        // ── Pulse rings (listening) ────────────────────────────────────────
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(size * ring1Scale)
                    .drawBehind {
                        drawCircle(
                            color  = borderColor.copy(alpha = ring1Alpha * 0.55f),
                            radius = this.size.minDimension / 2f,
                            style  = Stroke(1.5.dp.toPx())
                        )
                    }
            )
            Box(
                modifier = Modifier
                    .size(size * ring2Scale)
                    .drawBehind {
                        drawCircle(
                            color  = borderColor.copy(alpha = ring2Alpha * 0.45f),
                            radius = this.size.minDimension / 2f,
                            style  = Stroke(1.dp.toPx())
                        )
                    }
            )
        }

        // ── Thinking spin ring ─────────────────────────────────────────────
        if (isThinking) {
            Box(
                modifier = Modifier
                    .size(size * 1.18f)
                    .graphicsLayer(rotationZ = thinkRotation)
                    .drawBehind {
                        for (i in 0..5) {
                            val start = i * 60f
                            drawArc(
                                color = borderColor.copy(alpha = 0.50f),
                                startAngle = start,
                                sweepAngle = 30f,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(4f, 4f),
                                size = androidx.compose.ui.geometry.Size(this.size.width - 8f, this.size.height - 8f),
                                style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
            )
        }

        // ── Main circular button ───────────────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .graphicsLayer(scaleX = buttonScale, scaleY = buttonScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bgColor.copy(alpha = bgAlpha * 2.0f),
                            bgColor.copy(alpha = bgAlpha)
                        )
                    )
                )
                .border(
                    width = 2.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            borderColor.copy(alpha = borderAlpha),
                            borderColor.copy(alpha = borderAlpha * 0.6f),
                            borderColor.copy(alpha = borderAlpha),
                            borderColor.copy(alpha = borderAlpha * 0.6f),
                            borderColor.copy(alpha = borderAlpha)
                        )
                    ),
                    shape = CircleShape
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication        = ripple(color = borderColor, bounded = false),
                    onClick           = onClick
                )
        ) {
            when {
                // ── Waveform bars during listening ─────────────────────────
                isListening -> {
                    WaveformBars(
                        time      = waveTime,
                        amplitude = listenAmplitude,
                        color     = borderColor,
                        modifier  = Modifier.size(size * 0.58f)
                    )
                }

                // ── Mic icon (idle) ────────────────────────────────────────
                else -> {
                    val icon = when {
                        isSpeaking -> "▶"
                        isThinking -> "◌"
                        isError    -> "!"
                        else       -> "🎙"
                    }
                    Text(
                        text     = icon,
                        color    = borderColor.copy(alpha = 0.95f),
                        fontSize = (size.value * 0.36f).sp
                    )
                }
            }
        }
    }
}

/**
 * 5-bar animated equalizer waveform — shows during listening state.
 */
@Composable
fun WaveformBars(
    time: Float,
    amplitude: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val numBars = 5
        val barWidth = size.width / (numBars * 2 - 1)   // bars + gaps
        val maxHeight = size.height * 0.85f
        val minHeight = size.height * 0.15f
        val centerY = size.height / 2f

        for (i in 0 until numBars) {
            // Each bar oscillates at a different frequency for natural look
            val freq = 1.0f + i * 0.6f
            val phase = i * 0.8f
            val rawHeight = (sin((time * freq + phase).toDouble()) * 0.5 + 0.5).toFloat()
            val barH = (minHeight + rawHeight * (maxHeight - minHeight) * (0.4f + amplitude * 0.6f))
                .coerceAtLeast(minHeight)

            val x = i * barWidth * 2f

            // Bar alpha varies slightly for depth
            val barAlpha = 0.7f + (rawHeight * 0.3f)

            drawRoundRect(
                color        = color.copy(alpha = barAlpha),
                topLeft      = Offset(x, centerY - barH / 2f),
                size         = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f)
            )
        }
    }
}
