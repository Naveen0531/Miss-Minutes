package com.tva.missminutes.audio

import android.content.Context
import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AudioVisualizer"

/**
 * AudioVisualizer — provides real-time audio amplitude data for the orb.
 *
 * When TTS is speaking, this captures the system audio amplitude and
 * exposes it as a StateFlow<Float> (0.0 - 1.0) for the HolographicOrb.
 *
 * NOTE: Requires android.permission.RECORD_AUDIO (already in manifest).
 * If Visualizer is not available (some OEMs restrict it), the amplitude
 * falls back to 0 and the TTSEngine's sine-wave simulation takes over.
 */
class AudioVisualizer {

    private var visualizer: Visualizer? = null
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /**
     * Attach to an AudioTrack or MediaPlayer session.
     * Pass audioSessionId = 0 for the global session.
     */
    fun start(audioSessionId: Int = 0) {
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[0]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            waveform ?: return
                            // Calculate RMS from waveform
                            val sumSquares = waveform.sumOf { sample ->
                                val centered = (sample.toInt() and 0xFF) - 128
                                (centered * centered).toLong()
                            }
                            val rms = kotlin.math.sqrt(sumSquares.toDouble() / waveform.size)
                            _amplitude.value = (rms / 128.0).toFloat().coerceIn(0f, 1f)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) { /* unused */ }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
                )
                enabled = true
            }
            Log.i(TAG, "AudioVisualizer started on session $audioSessionId")
        } catch (e: Exception) {
            Log.w(TAG, "AudioVisualizer unavailable (using TTS simulation): ${e.message}")
            // Non-fatal — TTSEngine provides simulated amplitude as fallback
        }
    }

    fun stop() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer release error: ${e.message}")
        }
        visualizer = null
        _amplitude.value = 0f
    }
}
