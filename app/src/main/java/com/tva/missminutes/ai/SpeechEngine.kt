package com.tva.missminutes.ai

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private const val TAG = "SpeechEngine"

sealed class SpeechResult {
    data class Success(val transcript: String) : SpeechResult()
    data class Error(val message: String, val errorCode: Int = -1) : SpeechResult()
    object Timeout : SpeechResult()
}

/**
 * SpeechEngine — wraps Android's SpeechRecognizer for voice input.
 */
class SpeechEngine(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var resultChannel = Channel<SpeechResult>(Channel.UNLIMITED)

    val isRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Start listening for speech. Returns a Flow that emits exactly one SpeechResult.
     * Call from the UI thread (SpeechRecognizer requires main thread).
     */
    fun startListening(): Flow<SpeechResult> {
        // Clean up any previous recognizer
        stopListening()
        resultChannel = Channel(Channel.UNLIMITED)

        if (!isRecognitionAvailable) {
            Log.w(TAG, "Speech recognition is not available on this device")
            resultChannel.trySend(SpeechResult.Error("Speech recognition is not available on this device, partner!"))
            return resultChannel.receiveAsFlow()
        }

        // Create standard recognizer safely (handles both online and on-device transparently)
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Log.i(TAG, "Initialized standard speech recognizer")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer: ${e.message}")
            resultChannel.trySend(SpeechResult.Error("Couldn't start speech recognizer on this device, sugar!"))
            return resultChannel.receiveAsFlow()
        }

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Speech begun")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "Speech ended, processing...")
            }

            override fun onError(error: Int) {
                val message = mapErrorToMessage(error)
                Log.w(TAG, "Speech recognition error $error: $message")
                resultChannel.trySend(SpeechResult.Error(message, error))
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val transcript = matches?.firstOrNull()?.trim()

                if (transcript.isNullOrBlank()) {
                    resultChannel.trySend(SpeechResult.Error("Didn't catch that, sugar! Tap the mic to try again."))
                } else {
                    Log.i(TAG, "Transcript: $transcript")
                    resultChannel.trySend(SpeechResult.Success(transcript))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Build the recognition intent
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        }

        try {
            recognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
            resultChannel.trySend(SpeechResult.Error("Couldn't start mic listening, partner!"))
        }

        return resultChannel.receiveAsFlow()
    }

    fun stopListening() {
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer: ${e.message}")
        }
        recognizer = null
    }

    fun destroy() {
        stopListening()
        resultChannel.close()
    }

    private fun mapErrorToMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO ->
            "Hmm, couldn't pick up the audio — try in a quieter spot, partner!"
        SpeechRecognizer.ERROR_CLIENT ->
            "Well that's odd — recognizer client error. Tap the mic to try again!"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Need microphone permission first, sugar!"
        SpeechRecognizer.ERROR_NETWORK ->
            "Network error — make sure voice typing is enabled on your phone!"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Timed out waiting for speech service. Tap the mic to try again!"
        SpeechRecognizer.ERROR_NO_MATCH ->
            "Didn't catch that one, partner. Speak up a little and try again!"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "My ears are a little tied up right now — try again in a sec!"
        SpeechRecognizer.ERROR_SERVER ->
            "Server error from speech service — try again in a sec!"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "Well, I didn't hear anything! Tap the mic and speak up, sugar."
        else -> "Well shoot, something went sideways! Tap the mic to try again."
    }
}
