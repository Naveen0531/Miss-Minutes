package com.tva.missminutes.viewmodel

import android.app.Application
import android.content.Context
import android.speech.tts.Voice
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tva.missminutes.ai.ElevenLabsTTSEngine
import com.tva.missminutes.ai.JarvisLiveKitManager
import com.tva.missminutes.ai.LiveKitState
import com.tva.missminutes.ai.LLMEngine
import com.tva.missminutes.ai.SpeechEngine
import com.tva.missminutes.ai.SpeechResult
import com.tva.missminutes.ai.TTSEngine
import com.tva.missminutes.audio.SoundManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val TAG = "AssistantViewModel"

// ── App State Machine ─────────────────────────────────────────────────────────
sealed class AssistantState {
    object Idle : AssistantState()
    object Initializing : AssistantState()
    object Listening : AssistantState()
    data class Thinking(val userQuery: String) : AssistantState()
    data class Speaking(
        val response: String,
        val partialResponse: String = "",
        val amplitude: Float = 0f
    ) : AssistantState()
    data class Error(val message: String) : AssistantState()
}

// ── Chat Turn ─────────────────────────────────────────────────────────────────
data class ChatTurn(
    val userMessage: String,
    val assistantMessage: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Voice Styles ──────────────────────────────────────────────────────────────
enum class VoiceStyle {
    HEART,      // Warm, expressive (closest to soundtools "Heart")
    ENERGETIC,  // Brighter, quicker
    SOOTHING,   // Calmer, slower
    NEUTRAL     // Flat / natural
}

// ── Settings enums ────────────────────────────────────────────────────────────
enum class ResponseLength { SHORT, DETAILED }
enum class HologramFidelity { HIGH, OFF }

// Legacy alias for backward compat
typealias TemporalSyncRate = ResponseLength

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    // ── AI Engines ─────────────────────────────────────────────────────────────
    val llmEngine         = LLMEngine(application)
    val ttsEngine         = TTSEngine(application)
    val speechEngine      = SpeechEngine(application)
    val soundManager      = SoundManager(application)
    val elevenLabsEngine  = ElevenLabsTTSEngine(application)
    val liveKitManager    = JarvisLiveKitManager(application)

    // ── UI State ───────────────────────────────────────────────────────────────
    private val _state = MutableStateFlow<AssistantState>(AssistantState.Initializing)
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _chatHistory = MutableStateFlow<List<ChatTurn>>(emptyList())
    val chatHistory: StateFlow<List<ChatTurn>> = _chatHistory.asStateFlow()

    private val _llmStatus = MutableStateFlow("Initializing...")
    val llmStatus: StateFlow<String> = _llmStatus.asStateFlow()

    // ── Persistent Settings ──────────────────────────────────────────────────
    private val prefs = application.getSharedPreferences("tva_settings", Context.MODE_PRIVATE)

    private val _voiceStyle = MutableStateFlow(VoiceStyle.valueOf(prefs.getString("voice_style", VoiceStyle.HEART.name) ?: VoiceStyle.HEART.name))
    val voiceStyle: StateFlow<VoiceStyle> = _voiceStyle.asStateFlow()

    private val _responseLength = MutableStateFlow(ResponseLength.valueOf(prefs.getString("response_length", ResponseLength.SHORT.name) ?: ResponseLength.SHORT.name))
    val responseLength: StateFlow<ResponseLength> = _responseLength.asStateFlow()

    private val _hologramFidelity = MutableStateFlow(HologramFidelity.valueOf(prefs.getString("hologram_fidelity", HologramFidelity.HIGH.name) ?: HologramFidelity.HIGH.name))
    val hologramFidelity: StateFlow<HologramFidelity> = _hologramFidelity.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", 16f))  // sp
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    private val _speechRate = MutableStateFlow(prefs.getFloat("speech_rate", 0.90f))
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private val _speechPitch = MutableStateFlow(prefs.getFloat("speech_pitch", 1.08f))
    val speechPitch: StateFlow<Float> = _speechPitch.asStateFlow()

    private val _autoListen = MutableStateFlow(prefs.getBoolean("auto_listen", false))
    val autoListen: StateFlow<Boolean> = _autoListen.asStateFlow()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices: StateFlow<List<Voice>> = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName: StateFlow<String?> = _selectedVoiceName.asStateFlow()

    private val _isSpeechEnabled = MutableStateFlow(prefs.getBoolean("speech_enabled", true))
    val isSpeechEnabled: StateFlow<Boolean> = _isSpeechEnabled.asStateFlow()

    // ElevenLabs premium voice key (persisted)
    private val _elevenLabsApiKey = MutableStateFlow(prefs.getString("eleven_labs_key", "") ?: "")
    val elevenLabsApiKey: StateFlow<String> = _elevenLabsApiKey.asStateFlow()

    // ── Audio amplitude (for orb animation) ───────────────────────────────────
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    // ── Listening amplitude (microphone level) ─────────────────────────────────
    private val _listenAmplitude = MutableStateFlow(0f)
    val listenAmplitude: StateFlow<Float> = _listenAmplitude.asStateFlow()

    // ── LiveKit Mode State ──────────────────────────────────────────────────
    private val _livekitMode = MutableStateFlow(prefs.getBoolean("livekit_mode", false))
    val livekitMode: StateFlow<Boolean> = _livekitMode.asStateFlow()

    private val _livekitServerUrl = MutableStateFlow(
        prefs.getString("livekit_url", "ws://192.168.1.100:7880") ?: "ws://192.168.1.100:7880"
    )
    val livekitServerUrl: StateFlow<String> = _livekitServerUrl.asStateFlow()

    // Expose LiveKit connection state directly from manager
    val livekitState = liveKitManager.state
    val livekitCameraEnabled = liveKitManager.cameraEnabled

    private var activeJob: Job? = null
    private var amplitudePollJob: Job? = null
    private var listenPulseJob: Job? = null

    init {
        initializeEngines()
        startAmplitudePolling()
    }

    // ── Initialization ─────────────────────────────────────────────────────────
    private fun initializeEngines() {
        viewModelScope.launch {
            _state.value = AssistantState.Initializing

            // Init TTS first (fast)
            ttsEngine.initialize { success ->
                if (success) {
                    _availableVoices.value = ttsEngine.getAvailableVoices()
                    _selectedVoiceName.value = ttsEngine.selectedVoiceName
                    Log.i(TAG, "TTS ready. ${_availableVoices.value.size} voices available.")
                }
            }

            delay(400)
            soundManager.playIntro()

            // Init LLM (slow — loads model file from storage)
            _llmStatus.value = "Loading AI model..."
            val llmReady = withContext(Dispatchers.IO) { llmEngine.initialize() }

            if (llmReady) {
                _llmStatus.value = "✅ ${llmEngine.getModelInfo()}"
            } else {
                _llmStatus.value = llmEngine.loadError ?: "No model — import via settings"
            }

            delay(600)
            _state.value = AssistantState.Idle

            // Speak welcome greeting after a short pause
            delay(800)
            if (_isSpeechEnabled.value) {
                speakGreeting(llmReady)
            }
        }
    }

    private fun speakGreeting(modelLoaded: Boolean) {
        val greeting = if (modelLoaded) {
            "J.A.R.V.I.S. online, sir. How may I assist you today?"
        } else {
            "Good day. J.A.R.V.I.S. systems are initialised. I'm currently in demo mode — import a Gemma model via settings to activate full intelligence."
        }
        speakResponse(greeting)
    }

    // ── Mic / Voice Input ──────────────────────────────────────────────────────
    fun onMicTapped() {
        when (_state.value) {
            is AssistantState.Listening -> {
                cancelCurrentOperation()
                return
            }
            is AssistantState.Speaking, is AssistantState.Thinking, is AssistantState.Error -> {
                ttsEngine.stop()
                speechEngine.stopListening()
            }
            else -> { /* idle — fall through to start listening */ }
        }
        startListening()
    }

    private fun startListening() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            soundManager.playMicOn()
            _state.value = AssistantState.Listening
            startListenPulse()

            // SpeechRecognizer must run on Main thread
            withContext(Dispatchers.Main) {
                speechEngine.startListening().collect { result ->
                    when (result) {
                        is SpeechResult.Success -> {
                            stopListenPulse()
                            handleUserInput(result.transcript)
                        }
                        is SpeechResult.Error -> {
                            stopListenPulse()
                            // Only show error for critical failures, not timeout
                            if (result.errorCode != android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                                result.errorCode != android.speech.SpeechRecognizer.ERROR_NO_MATCH) {
                                handleError(result.message)
                            } else {
                                _state.value = AssistantState.Idle
                            }
                        }
                        is SpeechResult.Timeout -> {
                            stopListenPulse()
                            _state.value = AssistantState.Idle
                        }
                    }
                }
            }
        }
    }

    // ── Text Input ─────────────────────────────────────────────────────────────
    fun submitTextInput(transcript: String) {
        if (transcript.isBlank()) return

        // Stop any current operation
        if (_state.value is AssistantState.Speaking) ttsEngine.stop()
        cancelCurrentOperation()

        handleUserInput(transcript.trim())
    }

    // ── Core AI Processing ─────────────────────────────────────────────────────
    private fun handleUserInput(transcript: String) {
        activeJob?.cancel()
        ttsEngine.stop()
        elevenLabsEngine.stop()

        activeJob = viewModelScope.launch {
            _state.value = AssistantState.Thinking(transcript)
            Log.d(TAG, "Processing: $transcript")

            // Set response detail in engine
            llmEngine.detailedResponses = (_responseLength.value == ResponseLength.DETAILED)

            val responseBuilder = StringBuilder()

            llmEngine.generateStreaming(transcript).collect { chunk ->
                responseBuilder.append(chunk)
                val currentText = responseBuilder.toString()
                _state.value = AssistantState.Speaking(
                    response = currentText,
                    partialResponse = currentText,
                    amplitude = _amplitude.value
                )
            }

            val fullResponse = responseBuilder.toString().trim()
            val textToSpeak = fullResponse.ifBlank { "I'm not sure what to say to that, partner." }

            // Final state with full response
            _state.value = AssistantState.Speaking(
                response = textToSpeak,
                partialResponse = textToSpeak,
                amplitude = 0f
            )

            // Save to chat history
            if (fullResponse.isNotBlank()) {
                val turn = ChatTurn(transcript, fullResponse)
                _chatHistory.value = (_chatHistory.value + turn).takeLast(20)
            }

            // Speak the complete response ONCE generation finishes
            if (_isSpeechEnabled.value) {
                soundManager.playResponseStart()
                speakResponse(textToSpeak)
            } else {
                _state.value = AssistantState.Idle
            }
        }
    }

    private fun speakResponse(text: String) {
        val onCompleteAction = {
            soundManager.playResponseEnd()
            viewModelScope.launch {
                _state.value = AssistantState.Idle
                if (_autoListen.value) {
                    delay(500)
                    startListening()
                }
            }
        }

        if (elevenLabsEngine.isConfigured()) {
            // Premium ElevenLabs cloud voice
            viewModelScope.launch {
                elevenLabsEngine.speak(
                    text       = text,
                    onStart    = {},
                    onComplete = { onCompleteAction() }
                )
            }
        } else {
            // On-device TTS fallback
            ttsEngine.speak(
                text       = text,
                onStart    = {},
                onComplete = { onCompleteAction() }
            )
        }
    }

    private suspend fun speakResponseAndWait(text: String) {
        ttsEngine.speakSuspend(text)
        soundManager.playResponseEnd()
        _state.value = AssistantState.Idle
    }

    private fun handleError(message: String) {
        _state.value = AssistantState.Error(message)
        viewModelScope.launch {
            if (_isSpeechEnabled.value) {
                ttsEngine.speak(message) { _state.value = AssistantState.Idle }
            }
            delay(4000)
            if (_state.value is AssistantState.Error) _state.value = AssistantState.Idle
        }
    }

    fun cancelCurrentOperation() {
        activeJob?.cancel()
        speechEngine.stopListening()
        ttsEngine.stop()
        stopListenPulse()
        _state.value = AssistantState.Idle
    }

    // ── Amplitude Polling ──────────────────────────────────────────────────────
    private fun startAmplitudePolling() {
        amplitudePollJob = viewModelScope.launch {
            while (true) {
                _amplitude.value = ttsEngine.currentAmplitude
                delay(16)
            }
        }
    }

    // ── Listening pulse animation ──────────────────────────────────────────────
    private fun startListenPulse() {
        listenPulseJob?.cancel()
        listenPulseJob = viewModelScope.launch {
            var t = 0.0
            while (true) {
                val pulse = (Math.sin(t * 4.0) * 0.3 + 0.4).toFloat().coerceIn(0f, 1f)
                _listenAmplitude.value = pulse
                t += 0.08
                delay(16)
            }
        }
    }

    private fun stopListenPulse() {
        listenPulseJob?.cancel()
        _listenAmplitude.value = 0f
    }

    // ── Model Import ───────────────────────────────────────────────────────────
    fun importModel(context: Context, uri: android.net.Uri) {
        _llmStatus.value = "Copying model..."
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val destFile = java.io.File(context.filesDir, "models/model.task")
                destFile.parentFile?.mkdirs()

                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        Log.i(TAG, "Model copied: ${totalBytes / 1024 / 1024}MB")
                    }
                }

                withContext(Dispatchers.Main) {
                    _llmStatus.value = "Model imported! Initializing..."
                    llmEngine.shutdown()
                    val ready = withContext(Dispatchers.IO) { llmEngine.initialize() }
                    _llmStatus.value = if (ready) "✅ ${llmEngine.getModelInfo()}" else
                        (llmEngine.loadError ?: "Init failed")
                    if (ready) _state.value = AssistantState.Idle
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _llmStatus.value = "Import failed: ${e.message?.take(50)}"
                    Log.e(TAG, "Import failed", e)
                }
            }
        }
    }

    // ── Settings ───────────────────────────────────────────────────────────────
    fun clearMemory() {
        llmEngine.clearHistory()
        _chatHistory.value = emptyList()
        _state.value = AssistantState.Idle
        _llmStatus.value = "Memory cleared."
        viewModelScope.launch {
            delay(2000)
            _llmStatus.value = if (llmEngine.isReady) "✅ ${llmEngine.getModelInfo()}" else
                (llmEngine.loadError ?: "No model")
        }
    }

    fun setVoiceStyle(style: VoiceStyle) {
        _voiceStyle.value = style
        ttsEngine.applyVoiceStyle(style)
        prefs.edit().putString("voice_style", style.name).apply()
    }

    fun setVoiceByName(name: String) {
        ttsEngine.setVoiceByName(name)
        _selectedVoiceName.value = name
    }

    fun setResponseLength(length: ResponseLength) {
        _responseLength.value = length
        llmEngine.detailedResponses = (length == ResponseLength.DETAILED)
        prefs.edit().putString("response_length", length.name).apply()
    }

    fun setSyncRate(rate: TemporalSyncRate) = setResponseLength(rate)

    fun setHologramFidelity(fidelity: HologramFidelity) {
        _hologramFidelity.value = fidelity
        prefs.edit().putString("hologram_fidelity", fidelity.name).apply()
    }

    fun setFontSize(size: Float) {
        val s = size.coerceIn(12f, 24f)
        _fontSize.value = s
        prefs.edit().putFloat("font_size", s).apply()
    }

    fun setSpeechRate(rate: Float) {
        val r = rate.coerceIn(0.5f, 2.0f)
        _speechRate.value = r
        ttsEngine.setRateAndPitch(r, _speechPitch.value)
        prefs.edit().putFloat("speech_rate", r).apply()
    }

    fun setSpeechPitch(pitch: Float) {
        val p = pitch.coerceIn(0.5f, 2.0f)
        _speechPitch.value = p
        ttsEngine.setRateAndPitch(_speechRate.value, p)
        prefs.edit().putFloat("speech_pitch", p).apply()
    }

    fun setAutoListen(enabled: Boolean) {
        _autoListen.value = enabled
        prefs.edit().putBoolean("auto_listen", enabled).apply()
    }

    fun setSpeechEnabled(enabled: Boolean) {
        _isSpeechEnabled.value = enabled
        prefs.edit().putBoolean("speech_enabled", enabled).apply()
        if (!enabled) {
            ttsEngine.stop()
            elevenLabsEngine.stop()
        }
    }

    fun setElevenLabsApiKey(key: String) {
        val cleanKey = key.trim()
        _elevenLabsApiKey.value = cleanKey
        prefs.edit().putString("eleven_labs_key", cleanKey).apply()
        if (cleanKey.isBlank()) {
            elevenLabsEngine.setApiKey("")
        } else {
            elevenLabsEngine.setApiKey(cleanKey)
        }
        Log.i(TAG, "ElevenLabs key updated — configured: ${elevenLabsEngine.isConfigured()}")
    }

    fun setLanguage(locale: Locale) {
        ttsEngine.setLanguage(locale)
    }

    fun refreshVoiceList() {
        _availableVoices.value = ttsEngine.getAvailableVoices()
        _selectedVoiceName.value = ttsEngine.selectedVoiceName
    }


    // ── LiveKit Mode ───────────────────────────────────────────────────────────
    fun setLivekitMode(enabled: Boolean) {
        _livekitMode.value = enabled
        prefs.edit().putBoolean("livekit_mode", enabled).apply()
        if (!enabled) liveKitManager.disconnect()
    }

    fun setLivekitServerUrl(url: String) {
        val clean = url.trim()
        _livekitServerUrl.value = clean
        prefs.edit().putString("livekit_url", clean).apply()
    }

    fun connectToLiveKit() {
        viewModelScope.launch {
            val url = _livekitServerUrl.value
            val token = liveKitManager.generateDevToken(
                roomName = "jarvis-room",
                identity = "android-user"
            )
            liveKitManager.connect(url, token)
        }
    }

    fun disconnectFromLiveKit() {
        liveKitManager.disconnect()
    }

    fun toggleCamera() {
        viewModelScope.launch {
            liveKitManager.toggleCamera(useFrontCamera = true)
        }
    }

    fun flipCamera() {
        viewModelScope.launch {
            liveKitManager.flipCamera()
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        amplitudePollJob?.cancel()
        listenPulseJob?.cancel()
        ttsEngine.shutdown()
        elevenLabsEngine.shutdown()
        speechEngine.destroy()
        llmEngine.shutdown()
        soundManager.releaseAll()
        liveKitManager.release()
    }
}
