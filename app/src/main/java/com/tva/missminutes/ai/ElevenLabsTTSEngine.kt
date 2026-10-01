package com.tva.missminutes.ai

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ElevenLabsTTS"

/**
 * ElevenLabsTTSEngine — Premium cloud voice using ElevenLabs API.
 *
 * Uses the "Heart" voice (warm, expressive, female) from ElevenLabs.
 * This engine only activates when:
 *  1. An API key is configured via [setApiKey]
 *  2. Network connectivity is available
 *
 * Falls back to the on-device [TTSEngine] when offline or key is missing.
 *
 * Voice IDs (curated warm female voices):
 *  - "Heart"   → ElevenLabs voice ID: "FGY2WhTYpPnrIDTdsKH5"
 *  - "Serena"  → ElevenLabs voice ID: "pFZP5JQG7iQjIQuC4Bku"
 *  - "Rachel"  → ElevenLabs voice ID: "21m00Tcm4TlvDq8ikWAM"
 *
 * API Reference: https://api.elevenlabs.io/v1/text-to-speech/{voice_id}
 */
class ElevenLabsTTSEngine(private val context: Context) {

    private var apiKey: String? = null
    private var voiceId: String = VOICE_HEART

    private var mediaPlayer: MediaPlayer? = null

    @Volatile var isSpeaking = false
        private set

    /** Simulated amplitude during playback — hooks into the orb animation */
    @Volatile var currentAmplitude: Float = 0f
        private set

    private var amplitudeThread: Thread? = null

    companion object {
        // ElevenLabs voice IDs
        const val VOICE_HEART  = "FGY2WhTYpPnrIDTdsKH5"   // Heart — warm, expressive
        const val VOICE_SERENA = "pFZP5JQG7iQjIQuC4Bku"   // Serena — calm, professional
        const val VOICE_RACHEL = "21m00Tcm4TlvDq8ikWAM"   // Rachel — neutral female

        private const val API_BASE = "https://api.elevenlabs.io/v1/text-to-speech"
    }

    // ── Configuration ──────────────────────────────────────────────────────────

    fun setApiKey(key: String) {
        apiKey = key.trim()
        Log.i(TAG, "ElevenLabs API key set (length=${key.trim().length})")
    }

    fun setVoice(id: String) {
        voiceId = id
        Log.i(TAG, "ElevenLabs voice changed to: $id")
    }

    fun isConfigured(): Boolean = !apiKey.isNullOrBlank()

    // ── Speech ─────────────────────────────────────────────────────────────────

    /**
     * Synthesize and play the given text using ElevenLabs cloud TTS.
     * Must be called from a coroutine (uses [Dispatchers.IO] for network).
     *
     * @param text         The text to speak (max 5000 characters).
     * @param onStart      Called on the main thread when playback begins.
     * @param onComplete   Called on the main thread when playback finishes or on error.
     */
    suspend fun speak(
        text: String,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        val key = apiKey
        if (key.isNullOrBlank()) {
            Log.w(TAG, "No API key — cannot use ElevenLabs TTS")
            onComplete()
            return
        }
        if (text.isBlank()) {
            onComplete()
            return
        }

        stop()

        try {
            val audioBytes = withContext(Dispatchers.IO) {
                fetchAudio(text.take(4000), key, voiceId)
            }

            if (audioBytes == null) {
                Log.w(TAG, "Audio fetch returned null — fallback to device TTS")
                onComplete()
                return
            }

            // Write audio to temp file so MediaPlayer can play it
            val tempFile = File(context.cacheDir, "elevenlabs_tts.mp3")
            withContext(Dispatchers.IO) {
                tempFile.writeBytes(audioBytes)
            }

            // Play via MediaPlayer
            withContext(Dispatchers.Main) {
                playAudio(tempFile, onStart, onComplete)
            }

        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs TTS failed", e)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    private fun playAudio(file: File, onStart: () -> Unit, onComplete: () -> Unit) {
        try {
            val player = MediaPlayer()
            mediaPlayer = player

            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                isSpeaking = true
                startAmplitudeSimulation()
                onStart()
                it.start()
            }
            player.setOnCompletionListener {
                isSpeaking = false
                stopAmplitudeSimulation()
                it.release()
                mediaPlayer = null
                onComplete()
            }
            player.setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                isSpeaking = false
                stopAmplitudeSimulation()
                mp.release()
                mediaPlayer = null
                onComplete()
                true
            }
            player.prepareAsync()

        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer failed", e)
            isSpeaking = false
            stopAmplitudeSimulation()
            onComplete()
        }
    }

    /**
     * HTTP POST to ElevenLabs TTS endpoint, returns raw MP3 bytes.
     * Returns null on any network/API error.
     */
    private fun fetchAudio(text: String, apiKey: String, voiceId: String): ByteArray? {
        return try {
            val url = URL("$API_BASE/$voiceId")
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                setRequestProperty("xi-api-key", apiKey)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "audio/mpeg")
                connectTimeout = 10_000
                readTimeout    = 25_000
                doOutput = true
            }

            // ElevenLabs TTS request body
            val body = """
                {
                  "text": ${escapeJson(text)},
                  "model_id": "eleven_turbo_v2",
                  "voice_settings": {
                    "stability": 0.45,
                    "similarity_boost": 0.80,
                    "style": 0.30,
                    "use_speaker_boost": true
                  }
                }
            """.trimIndent()

            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code == 200) {
                val bytes = conn.inputStream.use { it.readBytes() }
                Log.i(TAG, "ElevenLabs OK — ${bytes.size / 1024}KB audio received")
                bytes
            } else {
                val err = conn.errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                Log.e(TAG, "ElevenLabs HTTP $code: $err")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "ElevenLabs network error", e)
            null
        }
    }

    /** JSON-safe string escaping */
    private fun escapeJson(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"'  -> sb.append("\\\"")
                '\\'  -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append("\\u${c.code.toString(16).padStart(4, '0')}") else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    fun stop() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        isSpeaking = false
        stopAmplitudeSimulation()
    }

    fun shutdown() = stop()

    // ── Amplitude simulation (mirrors TTSEngine) ───────────────────────────────
    private fun startAmplitudeSimulation() {
        stopAmplitudeSimulation()
        amplitudeThread = Thread {
            var t = 0.0
            var smoothed = 0f
            while (isSpeaking && !Thread.currentThread().isInterrupted) {
                val base     = 0.40f
                val word     = (Math.sin(t * 2.8)  * 0.22).toFloat()
                val syllable = (Math.sin(t * 8.5)  * 0.15).toFloat()
                val micro    = (Math.sin(t * 19.2) * 0.06).toFloat()
                val target   = (base + word + syllable + micro).coerceIn(0.05f, 1.0f)
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
