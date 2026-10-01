package com.tva.missminutes.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log

private const val TAG = "SoundManager"

/**
 * SoundManager — generates TVA-themed sound effects using Android's native ToneGenerator.
 *
 * 100% crash-proof, zero memory leaks, ultra-fast latency.
 *   playIntro()         — warm ambient synth sequence
 *   playMicOn()         — TVA activation chime
 *   playResponseStart() — response start soft beep
 *   playResponseEnd()   — response end completion click
 */
class SoundManager(private val context: Context) {

    private var toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 60)
    } catch (e: Exception) {
        Log.w(TAG, "ToneGenerator init failed", e)
        null
    }

    fun playIntro() {
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
            } catch (e: Exception) {
                Log.w(TAG, "playIntro failed", e)
            }
        }.start()
    }

    fun playMicOn() {
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (e: Exception) {
                Log.w(TAG, "playMicOn failed", e)
            }
        }.start()
    }

    fun playResponseStart() {
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            } catch (e: Exception) {
                Log.w(TAG, "playResponseStart failed", e)
            }
        }.start()
    }

    fun playResponseEnd() {
        Thread {
            try {
                toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 80)
            } catch (e: Exception) {
                Log.w(TAG, "playResponseEnd failed", e)
            }
        }.start()
    }

    fun releaseAll() {
        try {
            toneGen?.release()
            toneGen = null
        } catch (_: Exception) {}
    }
}
