package com.tva.missminutes.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min

/**
 * SplashScreen — TVA Boot Sequence.
 *
 * Plays a Jarvis-style boot animation for 3.2 seconds before transitioning
 * to the main AssistantScreen. Sequence:
 *  0.0s → Fade in concentric circles / radar scan
 *  0.8s → "TVA SYSTEMS" teletype text
 *  1.4s → "MISS MINUTES" fades in
 *  2.2s → Status lines teletype
 *  3.2s → Fade out → main screen
 */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    var phase by remember { mutableIntStateOf(0) }
    var statusLine by remember { mutableStateOf("") }
    var showMissMinutes by remember { mutableStateOf(false) }
    var showStatus by remember { mutableStateOf(false) }
    var screenAlpha by remember { mutableFloatStateOf(0f) }

    // Screen alpha animation
    val screenAlphaAnim by animateFloatAsState(
        targetValue   = screenAlpha,
        animationSpec = tween(500),
        label         = "screenAlpha"
    )

    // Boot sequence controller
    LaunchedEffect(Unit) {
        // Fade in
        screenAlpha = 1f
        delay(300)

        // Phase 0: Radar spin starts (driven by infinite transition below)
        phase = 1
        delay(800)

        // Phase 1: Teletype "INITIALIZING TVA SYSTEMS"
        val line1 = "INITIALIZING TVA SYSTEMS..."
        for (c in line1) {
            statusLine += c
            delay(35)
        }
        delay(200)

        // Phase 2: Miss Minutes name appears
        showMissMinutes = true
        delay(500)

        // Phase 3: Status lines
        showStatus = true
        val statusLines = listOf(
            "NEURAL CORE............ONLINE",
            "TEMPORAL DATABASE......LINKED",
            "VOICE SYSTEMS..........READY",
            "INTELLIGENCE ENGINE....ACTIVE",
            "MISS MINUTES...........AWAKE"
        )

        statusLine = ""
        for (line in statusLines) {
            statusLine = line
            delay(380)
        }
        delay(400)

        // Fade out
        screenAlpha = 0f
        delay(600)
        onComplete()
    }

    // Radar rotation
    val radarInfinite = rememberInfiniteTransition(label = "radar")
    val radarAngle by radarInfinite.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "radarSpin"
    )
    val ringPulse by radarInfinite.animateFloat(
        initialValue = 0.8f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ringPulse"
    )

    // Title glow
    val titleGlow by radarInfinite.animateFloat(
        initialValue = 0.7f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "titleGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(screenAlphaAnim)
            .background(Color(0xFF050304)),
        contentAlignment = Alignment.Center
    ) {

        // ── Radar / Holographic circle ────────────────────────────────────
        Box(
            modifier = Modifier
                .size(280.dp)
                .drawBehind {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxR   = min(size.width, size.height) / 2f

                    // Concentric rings
                    for (i in 1..5) {
                        val r = maxR * (i / 5f)
                        drawCircle(
                            color  = Color(0xFFFF8C42).copy(alpha = 0.08f + 0.04f * (5 - i)),
                            radius = r,
                            center = center,
                            style  = Stroke(1.dp.toPx())
                        )
                    }

                    // Radar sweep
                    val sweepRad = Math.toRadians(radarAngle.toDouble())
                    val sweepColors = listOf(
                        Color(0xFFFF8C42).copy(alpha = 0.5f),
                        Color(0xFFFF8C42).copy(alpha = 0.2f),
                        Color.Transparent
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = sweepColors,
                            center = center
                        ),
                        startAngle = radarAngle - 60f,
                        sweepAngle = 60f,
                        useCenter  = true,
                        topLeft    = Offset(center.x - maxR * 0.92f, center.y - maxR * 0.92f),
                        size       = androidx.compose.ui.geometry.Size(maxR * 1.84f, maxR * 1.84f),
                        alpha      = 0.7f
                    )

                    // Rotating dots on outer ring
                    for (i in 0 until 12) {
                        val dotAngle = Math.toRadians((i * 30 + radarAngle * 0.6).toDouble())
                        val dotR    = maxR * 0.93f
                        val dotAlpha = if (i % 3 == 0) 0.8f else 0.3f
                        drawCircle(
                            color  = Color(0xFFFF8C42).copy(alpha = dotAlpha * ringPulse),
                            radius = if (i % 3 == 0) 3.5.dp.toPx() else 1.5.dp.toPx(),
                            center = Offset(
                                center.x + cos(dotAngle).toFloat() * dotR,
                                center.y + sin(dotAngle).toFloat() * dotR
                            )
                        )
                    }

                    // Center cross-hair
                    val chSize = maxR * 0.06f
                    drawLine(Color(0xFFFF8C42).copy(alpha = 0.6f), Offset(center.x - chSize, center.y), Offset(center.x + chSize, center.y), 1.dp.toPx())
                    drawLine(Color(0xFFFF8C42).copy(alpha = 0.6f), Offset(center.x, center.y - chSize), Offset(center.x, center.y + chSize), 1.dp.toPx())
                    drawCircle(Color(0xFFFF8C42).copy(alpha = 0.8f), 3.dp.toPx(), center)
                }
        )

        // ── Text overlay ──────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // "MISS MINUTES" title
            val mmAlpha by animateFloatAsState(
                targetValue   = if (showMissMinutes) titleGlow else 0f,
                animationSpec = tween(600),
                label         = "mmAlpha"
            )
            Text(
                text  = "MISS MINUTES",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color         = Color(0xFFFF8C42).copy(alpha = mmAlpha),
                    letterSpacing = 8.sp,
                    fontSize      = 28.sp,
                    fontFamily    = FontFamily.Monospace
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text  = "TEMPORAL AI ASSISTANT",
                style = MaterialTheme.typography.labelSmall.copy(
                    color         = Color(0xFFCC6E35).copy(alpha = mmAlpha * 0.7f),
                    letterSpacing = 4.sp,
                    fontFamily    = FontFamily.Monospace
                ),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // Status teletype lines
            val statusAlpha by animateFloatAsState(
                targetValue   = if (showStatus || statusLine.isNotBlank()) 1f else 0f,
                animationSpec = tween(400),
                label         = "statusAlpha"
            )
            if (statusLine.isNotBlank()) {
                Text(
                    text  = "> $statusLine█",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color         = Color(0xFF7A3E1A).copy(alpha = statusAlpha * 0.9f),
                        letterSpacing = 1.sp,
                        fontFamily    = FontFamily.Monospace,
                        fontSize      = 11.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
