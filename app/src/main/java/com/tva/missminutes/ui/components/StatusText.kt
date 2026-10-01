package com.tva.missminutes.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tva.missminutes.ui.theme.*
import com.tva.missminutes.viewmodel.AssistantState
import com.tva.missminutes.viewmodel.ChatTurn

/**
 * StatusText — below-orb display showing current state + last conversation.
 *
 * Layout:
 *  [STATE LABEL]       ← "TAP TO SPEAK" / "LISTENING..." / etc.
 *  [USER QUERY]        ← dimmed, smaller (last spoken query)
 *  [MISS MINUTES RESP] ← amber, typewriter reveal animation
 */
@Composable
fun StatusText(
    state: AssistantState,
    chatHistory: List<ChatTurn>,
    modifier: Modifier = Modifier
) {
    // ── State label text & color ──────────────────────────────────────
    val (labelText, labelColor) = when (state) {
        is AssistantState.Idle          -> "[ TAP TO SPEAK ]" to TVAMuted
        is AssistantState.Initializing  -> "[ INITIALIZING... ]" to TVADimmed
        is AssistantState.Listening     -> "[ LISTENING... ]" to TVAAmber
        is AssistantState.Thinking      -> "[ PROCESSING... ]" to TVAWarmOrange
        is AssistantState.Speaking      -> "[ SPEAKING ]" to TVAHotOrange
        is AssistantState.Error         -> "[ ERROR ]" to TVAError
    }

    // ── Blinking cursor for Listening state ───────────────────────────
    val cursorInfinite = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorInfinite.animateFloat(
        initialValue = 1f,
        targetValue  = 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    // ── Label alpha (dimmer when idle) ────────────────────────────────
    val labelAlpha by animateFloatAsState(
        targetValue = when (state) {
            is AssistantState.Idle -> 0.5f
            else -> 1.0f
        },
        animationSpec = tween(500),
        label = "labelAlpha"
    )

    // ── Content from current state or history ─────────────────────────
    val currentUserQuery: String? = when (state) {
        is AssistantState.Thinking -> state.userQuery
        is AssistantState.Speaking -> chatHistory.lastOrNull()?.userMessage
        else -> chatHistory.lastOrNull()?.userMessage
    }

    val currentResponse: String? = when (state) {
        is AssistantState.Speaking -> state.partialResponse.ifBlank { null }
        is AssistantState.Error    -> state.message
        else -> chatHistory.lastOrNull()?.assistantMessage
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // ── Status label ──────────────────────────────────────────────
        AnimatedContent(
            targetState = labelText,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically { it / 2 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically { -it / 2 })
            },
            label = "statusLabel"
        ) { label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(color = labelColor),
                    modifier = Modifier.alpha(labelAlpha),
                    textAlign = TextAlign.Center
                )
                if (state is AssistantState.Listening) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "█",
                        style = MaterialTheme.typography.labelLarge.copy(color = TVAAmber),
                        modifier = Modifier.alpha(cursorAlpha)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── User query (dimmed) ───────────────────────────────────────
        AnimatedVisibility(
            visible = currentUserQuery != null,
            enter   = fadeIn(tween(400)) + expandVertically(tween(400)),
            exit    = fadeOut(tween(300)) + shrinkVertically(tween(300))
        ) {
            currentUserQuery?.let { query ->
                Text(
                    text  = "▶ $query",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TVAMuted),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Miss Minutes response (typewriter reveal) ─────────────────
        AnimatedVisibility(
            visible = currentResponse != null,
            enter   = fadeIn(tween(300)) + expandVertically(tween(400)),
            exit    = fadeOut(tween(400)) + shrinkVertically(tween(400)),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            currentResponse?.let { response ->
                TypewriterText(
                    text     = response,
                    modifier = Modifier.padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
                    isActive = state is AssistantState.Speaking
                )
            }
        }
    }
}

/**
 * Types out the text character by character when active (Speaking state).
 * Shows full text when inactive (history display).
 */
@Composable
fun TypewriterText(
    text: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    // When speaking, animate character count; otherwise show full text
    var displayedLength by remember(text) { mutableIntStateOf(if (isActive) 0 else text.length) }

    LaunchedEffect(text, isActive) {
        if (!isActive) {
            displayedLength = text.length
            return@LaunchedEffect
        }
        displayedLength = 0
        for (i in text.indices) {
            displayedLength = i + 1
            kotlinx.coroutines.delay(18) // ~55 chars/second
        }
    }

    val displayText = text.take(displayedLength)

    Text(
        text      = displayText,
        style     = MaterialTheme.typography.bodyLarge.copy(color = TVAAmber),
        textAlign = TextAlign.Center,
        modifier  = modifier
    )
}
