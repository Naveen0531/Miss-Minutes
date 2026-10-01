package com.tva.missminutes.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.tva.missminutes.ui.components.HolographicOrb
import com.tva.missminutes.ui.components.MicButton
import com.tva.missminutes.ui.theme.*
import com.tva.missminutes.viewmodel.*
import kotlin.random.Random

/**
 * AssistantScreen v3 — Main screen of J.A.R.V.I.S.
 *
 * Layout (from top to bottom):
 *  • Top bar (Stark Industries label + mode badge + settings icon)
 *  • Holographic AI Orb (state-driven, 220dp)
 *  • "J.A.R.V.I.S." title with sub-label
 *  • Chat conversation list (scrollable)
 *  • Current state response display
 *  • Text input field + send button
 *  • Mic button + camera FAB (in LiveKit mode)
 *  • Status footer
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel = viewModel()
) {
    val state           by viewModel.state.collectAsStateWithLifecycle()
    val chatHistory     by viewModel.chatHistory.collectAsStateWithLifecycle()
    val llmStatus       by viewModel.llmStatus.collectAsStateWithLifecycle()
    val voiceStyle      by viewModel.voiceStyle.collectAsStateWithLifecycle()
    val amplitude       by viewModel.amplitude.collectAsStateWithLifecycle()
    val listenAmplitude by viewModel.listenAmplitude.collectAsStateWithLifecycle()
    val hologramFidelity by viewModel.hologramFidelity.collectAsStateWithLifecycle()
    val fontSize        by viewModel.fontSize.collectAsStateWithLifecycle()
    val livekitMode     by viewModel.livekitMode.collectAsStateWithLifecycle()
    val livekitState    by viewModel.livekitState.collectAsStateWithLifecycle()
    val cameraEnabled   by viewModel.livekitCameraEnabled.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val micPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)
    var showPermissionDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importModel(context, it) } }

    // ── Grain / noise animation ────────────────────────────────────────────
    val grainAlpha by rememberInfiniteTransition(label = "grain").animateFloat(
        initialValue = 0.015f,
        targetValue  = 0.030f,
        animationSpec = infiniteRepeatable(tween(100, easing = LinearEasing), RepeatMode.Reverse),
        label = "grain"
    )

    if (showPermissionDialog) {
        JarvisPermissionDialog(
            onConfirm = { showPermissionDialog = false; micPermission.launchPermissionRequest() },
            onDismiss = { showPermissionDialog = false }
        )
    }

    if (showSettings) {
        JarvisSettingsBottomSheet(
            viewModel     = viewModel,
            onDismiss     = { showSettings = false },
            onImportModel = { filePickerLauncher.launch(arrayOf("*/*")) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TVABackground)
            .drawBehind {
                if (hologramFidelity == HologramFidelity.HIGH) {
                    // Film grain noise
                    val rng = Random(System.currentTimeMillis() / 80)
                    repeat(250) {
                        val x = rng.nextFloat() * size.width
                        val y = rng.nextFloat() * size.height
                        drawCircle(
                            color  = TVAAmber.copy(alpha = grainAlpha * rng.nextFloat()),
                            radius = rng.nextFloat() * 1.0f,
                            center = Offset(x, y)
                        )
                    }
                }
                // Vignette
                drawRect(
                    brush = Brush.radialGradient(
                        colors  = listOf(Color.Transparent, Color(0xFF000000).copy(alpha = 0.65f)),
                        center  = Offset(size.width / 2f, size.height / 2f),
                        radius  = size.width * 0.80f
                    )
                )
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            // ── Top bar ────────────────────────────────────────────────────
            TopBar(
                onSettingsClick = { showSettings = true }
            )

            // ── Holographic Orb ────────────────────────────────────────────
            HolographicOrb(
                state          = state,
                audioAmplitude = if (state is AssistantState.Listening) listenAmplitude else amplitude,
                modifier       = Modifier.padding(top = 8.dp)
            )

            Spacer(Modifier.height(10.dp))

            // ── Title ──────────────────────────────────────────────────────
            AppTitle()

            Spacer(Modifier.height(16.dp))

            // ── Conversation + Status area ─────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                ConversationArea(
                    state      = state,
                    history    = chatHistory,
                    fontSize   = fontSize
                )
            }

            // ── Text input row ─────────────────────────────────────────────
            TextInputRow(
                value       = textInput,
                onValueChange = { textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.submitTextInput(textInput.trim())
                        textInput = ""
                        keyboardController?.hide()
                    }
                },
                fontSize = fontSize
            )

            Spacer(Modifier.height(8.dp))

            // ── Mic + Camera + LiveKit Connect row ─────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Camera FAB (LiveKit mode only)
                if (livekitMode) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (cameraEnabled)
                                    Brush.verticalGradient(listOf(JarvisBlue.copy(alpha = 0.4f), JarvisBlue.copy(alpha = 0.2f)))
                                else
                                    Brush.verticalGradient(listOf(TVASurface.copy(alpha = 0.8f), TVASurface.copy(alpha = 0.5f)))
                            )
                            .border(1.5.dp, if (cameraEnabled) JarvisBlue else TVADimmed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (cameraPermission.status.isGranted) viewModel.toggleCamera()
                                else cameraPermission.launchPermissionRequest()
                            }
                    ) {
                        Text(text = if (cameraEnabled) "\uD83D\uDCF9" else "\uD83D\uDCF7", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(20.dp))
                }

                MicButton(
                    state           = state,
                    listenAmplitude = listenAmplitude,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (micPermission.status.isGranted) {
                            viewModel.onMicTapped()
                        } else if (micPermission.status.shouldShowRationale) {
                            showPermissionDialog = true
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    size = 78.dp
                )

                // LiveKit connect / disconnect button
                if (livekitMode) {
                    Spacer(Modifier.width(20.dp))
                    val lkConnected = livekitState is com.tva.missminutes.ai.LiveKitState.Connected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (lkConnected)
                                    Brush.verticalGradient(listOf(Color(0xFF00FF88).copy(alpha = 0.25f), Color(0xFF00FF88).copy(alpha = 0.1f)))
                                else
                                    Brush.verticalGradient(listOf(TVASurface.copy(alpha = 0.8f), TVASurface.copy(alpha = 0.5f)))
                            )
                            .border(1.5.dp, if (lkConnected) Color(0xFF00FF88) else TVADimmed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (lkConnected) viewModel.disconnectFromLiveKit()
                                else viewModel.connectToLiveKit()
                            }
                    ) {
                        Text(text = if (lkConnected) "\uD83D\uDD0C" else "\uD83D\uDD17", fontSize = 22.sp)
                    }
                }
            }

            // Status hint text
            val lkHint = when {
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connected -> "\uD83D\uDD17 J.A.R.V.I.S. VOICE ACTIVE"
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connecting -> "CONNECTING TO JARVIS..."
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Error ->
                    "\u26A0 ${(livekitState as com.tva.missminutes.ai.LiveKitState.Error).message.take(40)}"
                livekitMode -> "TAP \uD83D\uDD17 TO CONNECT \u2022 LIVEKIT MODE"
                state is AssistantState.Idle -> "tap mic to speak  \u2022  type below"
                else -> ""
            }
            val lkHintColor = when {
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connected -> JarvisBlue
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Error -> TVAError
                else -> TVADimmed.copy(alpha = 0.5f)
            }
            if (lkHint.isNotEmpty()) {
                Text(
                    text = lkHint,
                    style = MaterialTheme.typography.bodySmall.copy(color = lkHintColor, letterSpacing = 0.5.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Status footer ──────────────────────────────────────────────
            LLMStatusFooter(
                status  = if (livekitMode) "J.A.R.V.I.S. VOICE \u2014 Ollama + Kokoro TTS" else llmStatus,
                isReady = if (livekitMode) livekitState is com.tva.missminutes.ai.LiveKitState.Connected
                          else viewModel.llmEngine.isReady
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}


                state           = state,
                listenAmplitude = listenAmplitude,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (micPermission.status.isGranted) {
                        viewModel.onMicTapped()
                    } else if (micPermission.status.shouldShowRationale) {
                        showPermissionDialog = true
                    } else {
                        micPermission.launchPermissionRequest()
                    }
                },
                size = 78.dp
            )

            // Hint / LiveKit status text
            AnimatedContent(
                targetState = livekitMode to livekitState,
                transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(300))) },
                label = "hintContent"
            ) { (lkMode, lkState) ->
                val hintText = when {
                    lkMode && lkState is com.tva.missminutes.ai.LiveKitState.Connected ->
                        "🔗 J.A.R.V.I.S. VOICE ACTIVE"
                    lkMode && lkState is com.tva.missminutes.ai.LiveKitState.Connecting ->
                        "CONNECTING TO JARVIS..."
                    lkMode && lkState is com.tva.missminutes.ai.LiveKitState.Error ->
                        "⚠ ${(lkState as com.tva.missminutes.ai.LiveKitState.Error).message.take(40)}"
                    lkMode ->
                        "TAP 🔗 TO CONNECT • LIVEKIT MODE"
                    state is AssistantState.Idle ->
                        "tap mic to speak  •  type below"
                    else -> ""
                }
                val hintColor = when {
                    lkMode && lkState is com.tva.missminutes.ai.LiveKitState.Connected -> JarvisBlue
                    lkMode && lkState is com.tva.missminutes.ai.LiveKitState.Error -> TVAError
                    else -> TVADimmed.copy(alpha = 0.5f)
                }
                if (hintText.isNotEmpty()) {
                    Text(
                        text = hintText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = hintColor,
                            letterSpacing = 0.5.sp
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── LLM status footer ──────────────────────────────────────────
            LLMStatusFooter(status = llmStatus, isReady = viewModel.llmEngine.isReady)

            Spacer(Modifier.height(8.dp))

            // ── Mic + Camera + LiveKit Connect row ─────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Camera FAB (LiveKit mode only)
                if (livekitMode) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (cameraEnabled)
                                    Brush.verticalGradient(listOf(JarvisBlue.copy(alpha = 0.4f), JarvisBlue.copy(alpha = 0.2f)))
                                else
                                    Brush.verticalGradient(listOf(TVASurface.copy(alpha = 0.8f), TVASurface.copy(alpha = 0.5f)))
                            )
                            .border(1.5.dp, if (cameraEnabled) JarvisBlue else TVADimmed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (cameraPermission.status.isGranted) viewModel.toggleCamera()
                                else cameraPermission.launchPermissionRequest()
                            }
                    ) {
                        Text(text = if (cameraEnabled) "\uD83D\uDCF9" else "\uD83D\uDCF7", fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(20.dp))
                }

                MicButton(
                    state           = state,
                    listenAmplitude = listenAmplitude,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (micPermission.status.isGranted) {
                            viewModel.onMicTapped()
                        } else if (micPermission.status.shouldShowRationale) {
                            showPermissionDialog = true
                        } else {
                            micPermission.launchPermissionRequest()
                        }
                    },
                    size = 78.dp
                )

                // LiveKit connect / disconnect button
                if (livekitMode) {
                    Spacer(Modifier.width(20.dp))
                    val lkConnected = livekitState is com.tva.missminutes.ai.LiveKitState.Connected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (lkConnected)
                                    Brush.verticalGradient(listOf(Color(0xFF00FF88).copy(alpha = 0.25f), Color(0xFF00FF88).copy(alpha = 0.1f)))
                                else
                                    Brush.verticalGradient(listOf(TVASurface.copy(alpha = 0.8f), TVASurface.copy(alpha = 0.5f)))
                            )
                            .border(1.5.dp, if (lkConnected) Color(0xFF00FF88) else TVADimmed.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (lkConnected) viewModel.disconnectFromLiveKit()
                                else viewModel.connectToLiveKit()
                            }
                    ) {
                        Text(text = if (lkConnected) "\uD83D\uDD0C" else "\uD83D\uDD17", fontSize = 22.sp)
                    }
                }
            }

            // Hint / LiveKit status text
            val hintText2 = when {
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connected ->
                    "\uD83D\uDD17 J.A.R.V.I.S. VOICE ACTIVE"
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connecting ->
                    "CONNECTING TO JARVIS..."
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Error ->
                    "\u26A0 ${(livekitState as com.tva.missminutes.ai.LiveKitState.Error).message.take(40)}"
                livekitMode -> "TAP \uD83D\uDD17 TO CONNECT • LIVEKIT MODE"
                state is AssistantState.Idle -> "tap mic to speak  •  type below"
                else -> ""
            }
            val hintColor2 = when {
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Connected -> JarvisBlue
                livekitMode && livekitState is com.tva.missminutes.ai.LiveKitState.Error -> TVAError
                else -> TVADimmed.copy(alpha = 0.5f)
            }
            if (hintText2.isNotEmpty()) {
                Text(
                    text = hintText2,
                    style = MaterialTheme.typography.bodySmall.copy(color = hintColor2, letterSpacing = 0.5.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Status footer ──────────────────────────────────────────────
            LLMStatusFooter(
                status  = if (livekitMode) "J.A.R.V.I.S. VOICE \u2014 Ollama + Kokoro TTS" else llmStatus,
                isReady = if (livekitMode) livekitState is com.tva.missminutes.ai.LiveKitState.Connected
                          else viewModel.llmEngine.isReady
            )

            Spacer(Modifier.height(8.dp))

// ── Top Bar ───────────────────────────────────────────────────────────────────
@Composable
private fun TopBar(onSettingsClick: () -> Unit) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text(
            text  = "TVA",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 4.sp,
                color = TVADimmed.copy(alpha = 0.25f)
            )
        )
        Text(
            text  = "TEMPORAL CLASSIFICATION: SACRED",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.sp,
                color = TVADimmed.copy(alpha = 0.15f),
                fontSize = 8.sp
            )
        )
        // Settings button
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(TVASurface.copy(alpha = 0.6f))
                .border(1.dp, TVADimmed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .clickable(onClick = onSettingsClick)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text  = "⚙",
                color = TVAMuted.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}

// ── App Title ─────────────────────────────────────────────────────────────────
@Composable
private fun AppTitle() {
    val glow by rememberInfiniteTransition(label = "titleGlow").animateFloat(
        initialValue = 0.75f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text  = "MISS MINUTES",
            style = MaterialTheme.typography.headlineMedium.copy(
                color         = TVAAmber.copy(alpha = glow),
                letterSpacing = 6.sp
            ),
            textAlign = TextAlign.Center
        )
        Text(
            text  = "TVA TEMPORAL AI",
            style = MaterialTheme.typography.labelSmall.copy(
                color         = TVAMuted.copy(alpha = 0.55f),
                letterSpacing = 3.sp,
                fontSize      = 10.sp
            ),
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(top = 2.dp)
        )
    }
}

// ── Conversation Area ─────────────────────────────────────────────────────────
@Composable
private fun ConversationArea(
    state: AssistantState,
    history: List<ChatTurn>,
    fontSize: Float
) {
    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(history.size, state) {
        if (history.isNotEmpty()) {
            listState.animateScrollToItem(index = maxOf(0, history.size - 1))
        }
    }

    // Label text / color
    val (labelText, labelColor) = when (state) {
        is AssistantState.Idle         -> "[ TAP TO SPEAK ]" to TVAMuted
        is AssistantState.Initializing -> "[ INITIALIZING ]" to TVADimmed
        is AssistantState.Listening    -> "[ LISTENING ]" to Color(0xFF00CFFF)
        is AssistantState.Thinking     -> "[ PROCESSING ]" to Color(0xFFBB66FF)
        is AssistantState.Speaking     -> "[ SPEAKING ]" to Color(0xFFFFCC44)
        is AssistantState.Error        -> "[ ERROR ]" to TVAError
    }

    // Blinking cursor
    val blink by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (state is AssistantState.Idle) 0.45f else 1.0f,
        animationSpec = tween(400), label = "labelAlpha"
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Status label
        AnimatedContent(
            targetState = labelText,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically { it / 3 })
                    .togetherWith(fadeOut(tween(200)) + slideOutVertically { -it / 3 })
            },
            label = "stateLabel"
        ) { label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text     = label,
                    style    = MaterialTheme.typography.labelLarge.copy(color = labelColor),
                    modifier = Modifier.alpha(labelAlpha),
                    textAlign = TextAlign.Center
                )
                if (state is AssistantState.Listening) {
                    Spacer(Modifier.width(4.dp))
                    Text("█", style = MaterialTheme.typography.labelLarge.copy(color = labelColor),
                        modifier = Modifier.alpha(blink))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Chat history list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(history.takeLast(6)) { turn ->
                ChatBubble(turn = turn, fontSize = fontSize)
            }

            // Current response bubble (thinking / speaking)
            when (state) {
                is AssistantState.Thinking -> {
                    item {
                        ThinkingBubble(query = state.userQuery, fontSize = fontSize)
                    }
                }
                is AssistantState.Speaking -> {
                    item {
                        ResponseBubble(
                            text     = state.partialResponse,
                            fontSize = fontSize,
                            animate  = true
                        )
                    }
                }
                is AssistantState.Error -> {
                    item {
                        ErrorBubble(message = state.message, fontSize = fontSize)
                    }
                }
                else -> {}
            }
        }
    }
}

// ── Chat Bubble ───────────────────────────────────────────────────────────────
@Composable
private fun ChatBubble(turn: ChatTurn, fontSize: Float) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // User message (right-aligned)
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                .background(TVASurface.copy(alpha = 0.7f))
                .border(1.dp, TVADimmed.copy(alpha = 0.3f), RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text  = turn.userMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color    = TVAMuted,
                    fontSize = (fontSize - 2f).sp
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        // Assistant response (left-aligned)
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(TVASurface.copy(alpha = 0.9f), TVASurfaceVariant.copy(alpha = 0.7f))
                    )
                )
                .border(1.dp, TVAAmber.copy(alpha = 0.2f), RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text  = turn.assistantMessage,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color    = TVAAmber.copy(alpha = 0.90f),
                    fontSize = fontSize.sp
                )
            )
        }
    }
}

// ── Thinking Bubble ───────────────────────────────────────────────────────────
@Composable
private fun ThinkingBubble(query: String, fontSize: Float) {
    val dot by rememberInfiniteTransition(label = "dots").animateFloat(
        initialValue = 0f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "dotAnim"
    )
    val dotStr = ".".repeat(dot.toInt() + 1)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp, 4.dp, 12.dp, 12.dp))
                .background(TVASurface.copy(alpha = 0.6f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(text = query, style = MaterialTheme.typography.bodyMedium.copy(
                color = TVAMuted, fontSize = (fontSize - 2f).sp))
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .align(Alignment.Start)
                .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                .background(TVASurface.copy(alpha = 0.7f))
                .border(1.dp, JarvisBlue.copy(alpha = 0.4f), RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text  = "Processing$dotStr", style = MaterialTheme.typography.bodyMedium.copy(
                color = JarvisBlue.copy(alpha = 0.85f), fontSize = fontSize.sp))
        }
    }
}

// ── Response Bubble ───────────────────────────────────────────────────────────
@Composable
private fun ResponseBubble(text: String, fontSize: Float, animate: Boolean) {
    var displayLen by remember(text) { mutableIntStateOf(if (animate) 0 else text.length) }
    LaunchedEffect(text, animate) {
        if (!animate) { displayLen = text.length; return@LaunchedEffect }
        for (i in displayLen until text.length) {
            displayLen = i + 1
            kotlinx.coroutines.delay(14)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clip(RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
            .background(
                Brush.horizontalGradient(listOf(TVASurface.copy(alpha = 0.9f), TVASurface.copy(alpha = 0.7f)))
            )
            .border(1.dp, TVAAmber.copy(alpha = 0.35f), RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text  = text.take(displayLen),
            style = MaterialTheme.typography.bodyMedium.copy(
                color    = TVAAmber,
                fontSize = fontSize.sp
            )
        )
    }
}

// ── Error Bubble ──────────────────────────────────────────────────────────────
@Composable
private fun ErrorBubble(message: String, fontSize: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(8.dp))
            .background(TVAErrorMuted.copy(alpha = 0.3f))
            .border(1.dp, TVAError.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text = "⚠ $message", style = MaterialTheme.typography.bodySmall.copy(
            color = TVAError.copy(alpha = 0.9f), fontSize = (fontSize - 2f).sp))
    }
}

// ── Text Input Row ────────────────────────────────────────────────────────────
@Composable
private fun TextInputRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    fontSize: Float
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.weight(1f),
            textStyle     = MaterialTheme.typography.bodyMedium.copy(
                color    = TVAAmber,
                fontSize = fontSize.sp
            ),
            placeholder = {
                Text(
                    "Ask J.A.R.V.I.S....",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color    = TVADimmed.copy(alpha = 0.5f),
                        fontSize = fontSize.sp
                    )
                )
            },
            keyboardOptions = KeyboardOptions(
                imeAction      = ImeAction.Send,
                capitalization = KeyboardCapitalization.Sentences
            ),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            minLines = 1,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = TVAAmber.copy(alpha = 0.7f),
                unfocusedBorderColor    = TVAAmber.copy(alpha = 0.3f),
                focusedTextColor        = TVAAmber,
                unfocusedTextColor      = TVAAmber,
                cursorColor             = TVAAmber,
                focusedContainerColor   = TVASurface.copy(alpha = 0.4f),
                unfocusedContainerColor = TVASurface.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(Modifier.width(8.dp))

        // Send button
        val sendEnabled = value.isNotBlank()
        val sendAlpha by animateFloatAsState(if (sendEnabled) 1f else 0.3f, label = "sendAlpha")
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .alpha(sendAlpha)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(TVAAmber.copy(alpha = 0.25f), TVAAmber.copy(alpha = 0.12f))
                    )
                )
                .border(1.5.dp, TVAAmber.copy(alpha = if (sendEnabled) 0.7f else 0.25f), RoundedCornerShape(14.dp))
                .clickable(enabled = sendEnabled, onClick = onSend)
        ) {
            Text("▶", color = TVAAmber, fontSize = 18.sp)
        }
    }
}

// ── LLM Status Footer ─────────────────────────────────────────────────────────
@Composable
private fun LLMStatusFooter(status: String, isReady: Boolean) {
    val dotColor = if (isReady) TVAAmber else TVAMuted
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = if (isReady) 1f else 0.4f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "dotPulse"
    )
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TVASurface.copy(alpha = 0.5f))
            .border(1.dp, TVADimmed.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("●", style = MaterialTheme.typography.bodySmall.copy(
            color = dotColor.copy(alpha = dotAlpha), fontSize = 7.sp))
        Spacer(Modifier.width(8.dp))
        Text(
            text     = status,
            style    = MaterialTheme.typography.bodySmall.copy(
                color = TVADimmed.copy(alpha = 0.7f), letterSpacing = 0.3.sp, fontSize = 11.sp
            ),
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis
        )
    }
}

// ── Permission Dialog ─────────────────────────────────────────────────────────
@Composable
private fun TVAPermissionDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = TVASurface,
        titleContentColor = TVAAmber,
        textContentColor  = TVAMuted,
        title = {
            Text("TVA MIC AUTHORIZATION", style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        },
        text = {
            Text(
                "Miss Minutes needs microphone access to hear you speak. " +
                "Grant permission to use voice input.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("AUTHORIZE", color = TVAAmber, style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 2.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("DENY", color = TVAMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

// ── Settings Bottom Sheet ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TVASettingsBottomSheet(
    viewModel: AssistantViewModel,
    onDismiss: () -> Unit,
    onImportModel: () -> Unit
) {
    val responseLength   by viewModel.responseLength.collectAsStateWithLifecycle()
    val fidelity         by viewModel.hologramFidelity.collectAsStateWithLifecycle()
    val fontSize         by viewModel.fontSize.collectAsStateWithLifecycle()
    val currentStyle     by viewModel.voiceStyle.collectAsStateWithLifecycle()
    val speechRate       by viewModel.speechRate.collectAsStateWithLifecycle()
    val speechPitch      by viewModel.speechPitch.collectAsStateWithLifecycle()
    val autoListen       by viewModel.autoListen.collectAsStateWithLifecycle()
    val isSpeechEnabled  by viewModel.isSpeechEnabled.collectAsStateWithLifecycle()
    val elevenLabsKey    by viewModel.elevenLabsApiKey.collectAsStateWithLifecycle()
    val livekitMode      by viewModel.livekitMode.collectAsStateWithLifecycle()
    val livekitUrl       by viewModel.livekitServerUrl.collectAsStateWithLifecycle()
    val livekitState     by viewModel.livekitState.collectAsStateWithLifecycle()

    var elevenLabsKeyInput by remember(elevenLabsKey) { mutableStateOf(elevenLabsKey) }
    var livekitUrlInput by remember(livekitUrl) { mutableStateOf(livekitUrl) }
    var showApiKey by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = TVABackground,
        scrimColor       = Color.Black.copy(alpha = 0.7f),
        dragHandle = {
            Box(Modifier.padding(vertical = 10.dp).size(44.dp, 4.dp)
                .background(TVAMuted.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("⚙  STARK INDUSTRIES CONTROL PANEL", style = MaterialTheme.typography.titleMedium,
                        color = TVAAmber, letterSpacing = 3.sp)
                    Text("J.A.R.V.I.S. SYSTEM CONFIGURATION v3.0", style = MaterialTheme.typography.labelSmall,
                        color = TVAMuted.copy(alpha = 0.6f), letterSpacing = 2.sp)
                }
            }

            // ── Intelligence Core ──────────────────────────────────────────────
            item {
                SettingsSection(title = "INTELLIGENCE CORE") {
                    Text(
                        text = viewModel.llmEngine.getModelInfo(),
                        style = MaterialTheme.typography.bodySmall.copy(color = TVAAmber.copy(alpha = 0.8f))
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SettingsButton("IMPORT MODEL (.task)", TVAAmber) { onImportModel() }
                        SettingsButton("CLEAR MEMORY", TVAHotOrange) { viewModel.clearMemory(); onDismiss() }
                    }
                }
            }

            // ── Personality & Voice Profiles ──────────────────────────────────
            item {
                SettingsSection(title = "PERSONALITY & VOICE PROFILES") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceStyle.values().forEach { style ->
                            val isSelected = currentStyle == style
                            val title = when (style) {
                                VoiceStyle.HEART     -> "🧡 Miss Minutes (Southern Warmth)"
                                VoiceStyle.ENERGETIC -> "⚡ TVA AI (Confident & Sharp)"
                                VoiceStyle.SOOTHING  -> "🌙 Temporal AI (Soft & Calm)"
                                VoiceStyle.NEUTRAL   -> "🤖 Neutral Assistant"
                            }
                            val desc = when (style) {
                                VoiceStyle.HEART     -> "Warm Southern charm with expressive pitch & tone"
                                VoiceStyle.ENERGETIC -> "Fast, crisp responses with high clarity"
                                VoiceStyle.SOOTHING  -> "Relaxed, soothing pace for quiet environments"
                                VoiceStyle.NEUTRAL   -> "Standard balanced system voice"
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) TVAAmber.copy(alpha = 0.15f) else TVASurface.copy(alpha = 0.3f))
                                    .border(
                                        1.dp,
                                        if (isSelected) TVAAmber else TVADimmed.copy(alpha = 0.2f),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setVoiceStyle(style) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isSelected) TVAAmber else TVAMuted,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    )
                                    Text(
                                        text = desc,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = TVADimmed.copy(alpha = 0.7f),
                                            fontSize = 10.sp
                                        )
                                    )
                                }
                                if (isSelected) {
                                    Text("ACTIVE", color = TVAAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // ── ElevenLabs Cloud Voice ────────────────────────────────────────
            item {
                SettingsSection(title = "🌐 ELEVENLABS CLOUD VOICE") {
                    val isActive = elevenLabsKey.isNotBlank()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isActive) "✅ ELEVENLABS ACTIVE" else "📱 OFFLINE MODE (On-Device TTS)",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isActive) Color(0xFF66DD88) else TVADimmed,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value         = elevenLabsKeyInput,
                        onValueChange = { elevenLabsKeyInput = it },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("ElevenLabs API Key", fontSize = 11.sp) },
                        placeholder   = { Text("Paste sk_... key here", fontSize = 11.sp) },
                        singleLine    = true,
                        visualTransformation = if (showApiKey) VisualTransformation.None
                                               else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "HIDE" else "SHOW", fontSize = 9.sp, color = TVAMuted)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction    = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            viewModel.setElevenLabsApiKey(elevenLabsKeyInput)
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = TVAAmber.copy(alpha = 0.7f),
                            unfocusedBorderColor    = TVAAmber.copy(alpha = 0.3f),
                            focusedTextColor        = TVAAmber,
                            unfocusedTextColor      = TVAAmber,
                            cursorColor             = TVAAmber,
                            focusedContainerColor   = TVASurface.copy(alpha = 0.4f),
                            unfocusedContainerColor = TVASurface.copy(alpha = 0.2f),
                            focusedLabelColor       = TVAMuted,
                            unfocusedLabelColor     = TVADimmed
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton("SAVE API KEY", TVAAmber) {
                            viewModel.setElevenLabsApiKey(elevenLabsKeyInput)
                        }
                        if (isActive) {
                            SettingsButton("REMOVE KEY", TVAHotOrange) {
                                elevenLabsKeyInput = ""
                                viewModel.setElevenLabsApiKey("")
                            }
                        }
                    }
                }
            }

            // ── Voice Fine-Tuning ─────────────────────────────────────────────
            item {
                SettingsSection(title = "VOICE SPEED & PITCH") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Speech Speed", style = MaterialTheme.typography.bodySmall.copy(color = TVAMuted))
                                Text("${String.format("%.2f", speechRate)}x", color = TVAAmber, fontSize = 11.sp)
                            }
                            Slider(
                                value = speechRate,
                                onValueChange = { viewModel.setSpeechRate(it) },
                                valueRange = 0.75f..1.25f,
                                colors = SliderDefaults.colors(thumbColor = TVAAmber, activeTrackColor = TVAAmber)
                            )
                        }

                        Column {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Voice Pitch", style = MaterialTheme.typography.bodySmall.copy(color = TVAMuted))
                                Text("${String.format("%.2f", speechPitch)}x", color = TVAAmber, fontSize = 11.sp)
                            }
                            Slider(
                                value = speechPitch,
                                onValueChange = { viewModel.setSpeechPitch(it) },
                                valueRange = 0.80f..1.20f,
                                colors = SliderDefaults.colors(thumbColor = TVAAmber, activeTrackColor = TVAAmber)
                            )
                        }
                    }
                }
            }

            // ── Conversation Options ─────────────────────────────────────────
            item {
                SettingsSection(title = "CONVERSATION FEATURES") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hands-Free Auto-Listen", style = MaterialTheme.typography.bodySmall.copy(color = TVAMuted))
                                Text("Auto-open mic after response", style = MaterialTheme.typography.labelSmall.copy(color = TVADimmed, fontSize = 9.sp))
                            }
                            Switch(
                                checked = autoListen,
                                onCheckedChange = { viewModel.setAutoListen(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = TVAAmber, checkedTrackColor = TVAAmber.copy(alpha = 0.3f))
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Read Responses Aloud", style = MaterialTheme.typography.bodySmall.copy(color = TVAMuted))
                                Text("Voice output toggle", style = MaterialTheme.typography.labelSmall.copy(color = TVADimmed, fontSize = 9.sp))
                            }
                            Switch(
                                checked = isSpeechEnabled,
                                onCheckedChange = { viewModel.setSpeechEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = TVAAmber, checkedTrackColor = TVAAmber.copy(alpha = 0.3f))
                            )
                        }
                    }
                }
            }

            // ── Response Detail & Display ─────────────────────────────────────
            item {
                SettingsSection(title = "RESPONSE LENGTH") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ResponseLength.values().forEach { len ->
                            val active = responseLength == len
                            SettingsButton(
                                label  = if (len == ResponseLength.SHORT) "SHORT (Concise)" else "DETAILED (Full)",
                                color  = if (active) TVAAmber else TVAMuted,
                                filled = active
                            ) { viewModel.setResponseLength(len) }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "HOLOGRAM FIDELITY") {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        HologramFidelity.values().forEach { f ->
                            val active = fidelity == f
                            SettingsButton(
                                label  = if (f == HologramFidelity.HIGH) "HIGH (EFFECTS)" else "OFF (BATTERY)",
                                color  = if (active) TVAAmber else TVAMuted,
                                filled = active
                            ) { viewModel.setHologramFidelity(f) }
                        }
                    }
                }
            }

            item {
                SettingsSection(title = "TEXT SIZE (${fontSize.toInt()}sp)") {
                    Slider(
                        value         = fontSize,
                        onValueChange = { viewModel.setFontSize(it) },
                        valueRange    = 12f..24f,
                        steps         = 6,
                        colors        = SliderDefaults.colors(
                            thumbColor         = TVAAmber,
                            activeTrackColor   = TVAAmber,
                            inactiveTrackColor = TVASurface
                        )
                    )
                }
            }

            // ── About ──────────────────────────────────────────────────────
            item {
                SettingsSection(title = "ABOUT MISS MINUTES") {
                    Text(
                        text = "Miss Minutes AI v2.0\nPowered by Google Gemma 3\nBuilt for Android 12+\n\nModel Status: ${viewModel.llmEngine.getModelInfo()}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color    = TVADimmed.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

// ── Settings helpers ──────────────────────────────────────────────────────────
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelMedium.copy(color = TVAMuted, letterSpacing = 1.sp)
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SettingsButton(label: String, color: Color, filled: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(
        onClick  = onClick,
        colors   = ButtonDefaults.outlinedButtonColors(
            contentColor     = color,
            containerColor   = if (filled) color.copy(alpha = 0.12f) else Color.Transparent
        ),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp, if (filled) color else color.copy(alpha = 0.5f)
        )
    ) {
        Text(label, fontSize = 11.sp)
    }
}
