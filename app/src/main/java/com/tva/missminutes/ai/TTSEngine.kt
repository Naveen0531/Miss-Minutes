package com.tva.missminutes.ai

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "TTSEngine"
private const val UTTERANCE_ID = "mm_speech"

/**
 * TTSEngine — Miss Minutes' voice engine.
 *
 * Voice quality priority (best to fallback):
 *  1. High-quality network voices (Google Cloud TTS — warm, expressive, female)
 *  2. Enhanced on-device female voices
 *  3. Any English female voice
 *  4. System default
 *
 * "Heart" style tuning: pitch 1.08, speed 0.90 — warm, expressive, confident
 */
class TTSEngine(private val context: Context) {

    private var tts: TextToSpeech? = null

    @Volatile var isReady = false
        private set

    @Volatile var isSpeaking = false
        private set

    private var onSpeakCompleteCallback: (() -> Unit)? = null
    private var onSpeakStartCallback: (() -> Unit)? = null

    // ── Audio-reactive amplitude (simulated during TTS) ────────────────────
    @Volatile var currentAmplitude: Float = 0f
        private set

    private var amplitudeThread: Thread? = null

    // ── Available voices cache ─────────────────────────────────────────────
    private var voiceList: List<Voice> = emptyList()
    var selectedVoiceName: String? = null

    // ── Current voice style settings ──────────────────────────────────────
    private var currentPitch  = 1.08f   // Warm "Heart" style (slightly higher, not shrill)
    private var currentSpeed  = 0.90f   // Slightly slower = more expressive & warm

    /**
     * Initialize the TTS engine.
     */
    fun initialize(onReady: (Boolean) -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configureTTS()
                isReady = true
                Log.i(TAG, "TTS Engine ready")
                onReady(true)
            } else {
                Log.e(TAG, "TTS init failed: $status")
                isReady = false
                onReady(false)
            }
        }
    }

    private fun configureTTS() {
        val engine = tts ?: return

        // Language: US English
        val langResult = engine.setLanguage(Locale.US)
        if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.ENGLISH)
        }

        // Cache voice list
        voiceList = engine.voices?.toList() ?: emptyList()
        Log.d(TAG, "Available voices: ${voiceList.size}")
        voiceList.forEach { Log.d(TAG, "  Voice: ${it.name} | locale: ${it.locale} | quality: ${it.quality} | network: ${it.isNetworkConnectionRequired}") }

        // Select best voice
        selectBestVoice(engine)

        // Apply warmth tuning
        engine.setPitch(currentPitch)
        engine.setSpeechRate(currentSpeed)

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        // Progress listener
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
                startAmplitudeSimulation()
                mainHandler.post { onSpeakStartCallback?.invoke() }
            }

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                stopAmplitudeSimulation()
                mainHandler.post { onSpeakCompleteCallback?.invoke() }
            }

            @Deprecated("Deprecated")
            override fun onError(utteranceId: String?) {
                isSpeaking = false
                stopAmplitudeSimulation()
                mainHandler.post { onSpeakCompleteCallback?.invoke() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                isSpeaking = false
                stopAmplitudeSimulation()
                mainHandler.post { onSpeakCompleteCallback?.invoke() }
                Log.e(TAG, "TTS error $errorCode")
            }
        })
    }

    /**
     * Selects the highest-quality English female voice available.
     * Priority: network female > neural female > enhanced female > any female > default
     */
    private fun selectBestVoice(engine: TextToSpeech) {
        val englishVoices = voiceList.filter { v ->
            v.locale.language == "en" && !v.features.contains("notInstalled")
        }

        val scored = englishVoices.map { v ->
            var score = 0
            val name = v.name.lowercase()

            // Female voice identifiers
            val isFemale = name.contains("female") || name.contains("woman") ||
                name.contains("f-") || name.contains("-f-") ||
                name.contains("en-us-x-sfg") || name.contains("en-us-x-sfd") ||
                name.contains("en-gb-x-gba") || name.contains("en-gb-x-gbg") ||
                name.contains("sara") || name.contains("zira") || name.contains("hazel") ||
                name.contains("samantha") || name.contains("karen") || name.contains("moira") ||
                name.contains("nerissa") || name.contains("tessa") || name.contains("victoria") ||
                name.contains("allison") || name.contains("ava") || name.contains("kate") ||
                name.contains("fiona") || name.contains("veena")
            if (isFemale) score += 100

            // Network voices are generally higher quality (Google Cloud TTS)
            if (v.isNetworkConnectionRequired) score += 50

            // Quality score from Voice API
            score += v.quality

            // Prefer US English
            if (v.locale == Locale.US) score += 30
            else if (v.locale.language == "en") score += 10

            // Penalize voices known to sound robotic or child-like
            if (name.contains("x-") && !name.contains("network")) score += 20 // On-device enhanced quality

            Pair(v, score)
        }

        val best = scored.maxByOrNull { it.second }?.first

        if (best != null) {
            engine.voice = best
            selectedVoiceName = best.name
            Log.i(TAG, "Selected voice: ${best.name} (locale: ${best.locale}, network: ${best.isNetworkConnectionRequired})")
        } else {
            selectedVoiceName = engine.voice?.name
            Log.w(TAG, "No enhanced voice found, using default: $selectedVoiceName")
        }
    }

    /**
     * Get list of available English voices for settings UI.
     */
    fun getAvailableVoices(): List<Voice> {
        return voiceList.filter { v ->
            v.locale.language == "en" && !v.features.contains("notInstalled")
        }.sortedWith(compareByDescending<Voice> { it.quality }
            .thenByDescending { it.name.lowercase().contains("female") }
            .thenByDescending { it.locale == Locale.US })
    }

    /**
     * Select voice by name (from settings picker).
     */
    fun setVoiceByName(name: String) {
        val engine = tts ?: return
        val voice = voiceList.find { it.name == name } ?: return
        engine.voice = voice
        selectedVoiceName = name
        Log.i(TAG, "Voice changed to: $name")
    }

    /**
     * Apply voice style preset.
     */
    fun applyVoiceStyle(style: com.tva.missminutes.viewmodel.VoiceStyle) {
        val engine = tts ?: return
        when (style) {
            com.tva.missminutes.viewmodel.VoiceStyle.HEART -> {
                // Warm, expressive (closest to the "Heart" voice)
                currentPitch = 1.08f
                currentSpeed = 0.90f
            }
            com.tva.missminutes.viewmodel.VoiceStyle.ENERGETIC -> {
                currentPitch = 1.20f
                currentSpeed = 1.05f
            }
            com.tva.missminutes.viewmodel.VoiceStyle.SOOTHING -> {
                currentPitch = 0.95f
                currentSpeed = 0.82f
            }
            com.tva.missminutes.viewmodel.VoiceStyle.NEUTRAL -> {
                currentPitch = 1.0f
                currentSpeed = 1.0f
            }
        }
        engine.setPitch(currentPitch)
        engine.setSpeechRate(currentSpeed)
        Log.i(TAG, "Voice style: $style (pitch=$currentPitch, speed=$currentSpeed)")
    }

    /**
     * Directly set speech rate and pitch.
     */
    fun setRateAndPitch(rate: Float, pitch: Float) {
        val engine = tts ?: return
        currentSpeed = rate.coerceIn(0.5f, 2.0f)
        currentPitch = pitch.coerceIn(0.5f, 2.0f)
        engine.setSpeechRate(currentSpeed)
        engine.setPitch(currentPitch)
        Log.i(TAG, "Speech rate=$currentSpeed pitch=$currentPitch")
    }

    /**
     * Set speech language.
     */
    fun setLanguage(locale: Locale) {
        val engine = tts ?: return
        val result = engine.setLanguage(locale)
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            Log.i(TAG, "Language set to: $locale")
        }
    }

    /**
     * Speak the given text.
     */
    fun speak(text: String, onStart: () -> Unit = {}, onComplete: () -> Unit = {}) {
        if (!isReady || tts == null) {
            Log.w(TAG, "TTS not ready")
            onComplete()
            return
        }
        if (text.isBlank()) {
            onComplete()
            return
        }

        onSpeakStartCallback = onStart
        onSpeakCompleteCallback = onComplete

        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_MUSIC)
        }

        tts?.stop()
        tts?.speak(text.take(4000), TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    /**
     * Suspending speak — waits until speech completes.
     */
    suspend fun speakSuspend(text: String) = suspendCancellableCoroutine<Unit> { cont ->
        speak(text = text, onStart = {}, onComplete = {
            if (cont.isActive) cont.resume(Unit)
        })
        cont.invokeOnCancellation { stop() }
    }

    fun stop() {
        tts?.stop()
        isSpeaking = false
        stopAmplitudeSimulation()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    // ── Amplitude Simulation ───────────────────────────────────────────────────
    // Simulates a realistic speech amplitude envelope since Android TTS
    // doesn't expose actual audio amplitude data.
    private fun startAmplitudeSimulation() {
        stopAmplitudeSimulation()
        amplitudeThread = Thread {
            var t = 0.0
            var smoothed = 0f
            while (isSpeaking && !Thread.currentThread().isInterrupted) {
                // Multi-frequency combination for natural speech rhythm
                val base   = 0.38f
                val word   = (Math.sin(t * 2.8)  * 0.22).toFloat()  // Word-level rhythm
                val syllable = (Math.sin(t * 8.5) * 0.15).toFloat()  // Syllable rhythm
                val micro  = (Math.sin(t * 19.2)  * 0.06).toFloat()  // Micro-variations

                val target = (base + word + syllable + micro).coerceIn(0.05f, 1.0f)

                // Smooth the value to avoid jitter
                smoothed = smoothed * 0.75f + target * 0.25f
                currentAmplitude = smoothed

                t += 0.055
                try { Thread.sleep(16) } catch (_: InterruptedException) { break }
            }
            currentAmplitude = 0f
        }.apply { isDaemon = true; start() }
    }

    private fun stopAmplitudeSimulation() {
        amplitudeThread?.interrupt()
        amplitudeThread = null
        currentAmplitude = 0f
    }
}
