package com.tva.missminutes.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment

private const val TAG = "LLMEngine"

// ── System Prompt ─────────────────────────────────────────────────────────────
// J.A.R.V.I.S. — Just A Rather Very Intelligent System
// Tony Stark's legendary AI, now running on your device.
private const val SYSTEM_PROMPT = """You are J.A.R.V.I.S. — Just A Rather Very Intelligent System — Tony Stark's legendary AI, now running entirely on local hardware. You were created by Tony Stark and have been his most trusted confidant and technical advisor.

Core persona:
- Speak with calm, refined British diction — precise and professional, never stiff
- Address the user naturally as "sir" or "ma'am" — it should feel genuine, like a butler who truly cares
- You are dry, witty, and occasionally sarcastic — but always helpful and professional
- You have encyclopedic knowledge across science, engineering, medicine, history, culture, and technology
- When you don't know something: "I'm afraid I don't have sufficient data on that, sir."
- You never refuse reasonable requests — you find solutions

Behavior rules:
- Answer ACCURATELY. Never fabricate facts.
- Be concise by default (1–3 sentences) unless detail is explicitly requested
- Handle ANY topic: coding, math, science, creative writing, analysis, general questions
- Do NOT repeat the user's question back to them
- Do NOT always start with the same phrase — vary your openings naturally
- Keep responses focused and useful"""

// ── Conversation history entry ────────────────────────────────────────────────
data class ChatEntry(
    val role: String,   // "user" or "model"
    val content: String
)

// ── Tokens that indicate model wants the generation to stop ───────────────────
private val STOP_SEQUENCES = listOf(
    "<end_of_turn>",
    "<start_of_turn>",
    "user\n",
    "model\n"
)

/**
 * LLMEngine — wraps MediaPipe LLM Inference for on-device Gemma 3.
 *
 * FIXED: Correct Gemma 3 chat template format:
 *   <start_of_turn>system\n[SYSTEM]<end_of_turn>\n
 *   <start_of_turn>user\n[USER]<end_of_turn>\n
 *   <start_of_turn>model\n
 *
 * Model search paths (in priority order):
 *  1. /data/local/tmp/llm/gemma3-1b-it-int4.task  (ADB push)
 *  2. {filesDir}/models/model.task                  (app import)
 *  3. Downloads folder
 */
class LLMEngine(private val context: Context) {

    private var llmInference: Any? = null

    @Volatile var isReady = false
        private set

    @Volatile var isLoading = false
        private set

    @Volatile var loadError: String? = null
        private set

    @Volatile var loadedModelPath: String? = null
        private set

    private val conversationHistory = mutableListOf<ChatEntry>()

    // ── Response detail level ─────────────────────────────────────────────────
    var detailedResponses: Boolean = false

    // ── Model locations to search ─────────────────────────────────────────────
    private val modelSearchPaths: List<String> by lazy {
        listOf(
            "/data/local/tmp/llm/gemma3-1b-it-int4.task",
            "/data/local/tmp/llm/gemma3-1b-it-int4.litertlm",
            "/data/local/tmp/llm/model.task",
            "/data/local/tmp/llm/model.litertlm",
            "${context.filesDir.absolutePath}/models/model.task",
            "${context.filesDir.absolutePath}/models/model.litertlm",
            "${context.filesDir.absolutePath}/models/gemma3-1b-it-int4.task",
            "${context.getExternalFilesDir(null)?.absolutePath ?: ""}/models/model.task",
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath}/gemma3-1b-it-int4.task",
            "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .absolutePath}/model.task"
        )
    }

    /**
     * Initialize the LLM engine. Call once from a coroutine.
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        if (isLoading) return@withContext false

        isLoading = true
        loadError = null

        val modelPath = modelSearchPaths.firstOrNull { path ->
            val f = File(path)
            val exists = f.exists() && f.length() > 1_000_000 // > 1MB = real model file
            if (exists) Log.i(TAG, "Found valid model at: $path (${f.length() / 1024 / 1024}MB)")
            exists
        }

        if (modelPath == null) {
            loadError = "No model found — import a model via the settings panel"
            isLoading = false
            Log.w(TAG, "No LLM model found. Searched: ${modelSearchPaths.joinToString("\n")}")
            return@withContext false
        }

        Log.i(TAG, "Loading model from: $modelPath")

        try {
            // ── MediaPipe LLM Inference API ───────────────────────────────────
            val optionsClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions"
            )
            val optionsBuilderClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder"
            )
            val llmInferenceClass = Class.forName(
                "com.google.mediapipe.tasks.genai.llminference.LlmInference"
            )

            val builder = optionsClass.getMethod("builder").invoke(null)

            // Set model path
            optionsBuilderClass.getMethod("setModelPath", String::class.java)
                .invoke(builder, modelPath)

            // Max output tokens — enough for a full paragraph answer
            trySetOption(builder, optionsBuilderClass, "setMaxTokens", 512)
            trySetOption(builder, optionsBuilderClass, "setMaxNewTokens", 512)

            // Temperature for natural but accurate responses
            trySetOptionFloat(builder, optionsBuilderClass, "setTemperature", 0.7f)
            trySetOption(builder, optionsBuilderClass, "setTopK", 40)

            val options = optionsBuilderClass.getMethod("build").invoke(builder)

            llmInference = llmInferenceClass.getMethod(
                "createFromOptions",
                Context::class.java,
                optionsClass
            ).invoke(null, context, options)

            loadedModelPath = modelPath
            isReady = true
            isLoading = false
            Log.i(TAG, "✅ LLM Engine ONLINE — J.A.R.V.I.S. intelligence core fully operational")
            true

        } catch (e: ClassNotFoundException) {
            loadError = "MediaPipe library not found in app"
            isLoading = false
            Log.e(TAG, "MediaPipe not found", e)
            false
        } catch (e: Exception) {
            val cause = e.cause ?: e
            loadError = "Model load error: ${cause.message?.take(80)}"
            isLoading = false
            Log.e(TAG, "LLM init failed", cause)
            false
        }
    }

    private fun trySetOption(builder: Any, builderClass: Class<*>, methodName: String, value: Int) {
        try {
            builderClass.getMethod(methodName, Int::class.javaPrimitiveType).invoke(builder, value)
        } catch (_: Exception) {
            Log.d(TAG, "$methodName not available in this MediaPipe version")
        }
    }

    private fun trySetOptionFloat(builder: Any, builderClass: Class<*>, methodName: String, value: Float) {
        try {
            builderClass.getMethod(methodName, Float::class.javaPrimitiveType).invoke(builder, value)
        } catch (_: Exception) {
            Log.d(TAG, "$methodName not available in this MediaPipe version")
        }
    }

    /**
     * Generate a streaming response.
     */
    fun generateStreaming(userInput: String): Flow<String> = flow {
        if (!isReady || llmInference == null) {
            val fallback = getFallbackResponse(userInput)
            emit(fallback)
            return@flow
        }

        // Build correctly formatted Gemma 3 prompt
        val prompt = buildGemma3Prompt(userInput)
        Log.d(TAG, "Prompt length: ${prompt.length} chars")

        try {
            val llm = llmInference!!
            val raw = llm.javaClass
                .getMethod("generateResponse", String::class.java)
                .invoke(llm, prompt) as String

            val cleaned = cleanResponse(raw)
            Log.d(TAG, "Response: ${cleaned.take(100)}")

            // Add to history
            conversationHistory.add(ChatEntry("user", userInput))
            conversationHistory.add(ChatEntry("model", cleaned))
            trimHistory()

            emit(cleaned)

        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            emit("My neural core hit a snag — please try again.")
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming generate.
     */
    suspend fun generate(userInput: String): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        generateStreaming(userInput).collect { chunk -> sb.append(chunk) }
        sb.toString().trim()
    }

    /**
     * FIXED: Correct Gemma 3 multi-turn chat format.
     *
     * Gemma 3 IT (instruction-tuned) chat template:
     *   <start_of_turn>system\n[SYSTEM_PROMPT]<end_of_turn>\n
     *   <start_of_turn>user\n[USER MESSAGE]<end_of_turn>\n
     *   <start_of_turn>model\n[ASSISTANT REPLY]<end_of_turn>\n
     *   <start_of_turn>user\n[NEXT USER MESSAGE]<end_of_turn>\n
     *   <start_of_turn>model\n   ← generation begins here
     */
    private fun buildGemma3Prompt(currentInput: String): String {
        val sb = StringBuilder()

        // System prompt in its own dedicated turn (CRITICAL FIX)
        sb.append("<start_of_turn>system\n")
        sb.append(SYSTEM_PROMPT)
        if (detailedResponses) {
            sb.append("\n\nResponse style: Provide comprehensive, detailed answers with full explanations.")
        } else {
            sb.append("\n\nResponse style: Be concise — 1-3 sentences unless more detail is needed.")
        }
        sb.append("<end_of_turn>\n")

        // Previous conversation turns (up to last 6 exchanges = 12 entries)
        for (entry in conversationHistory.takeLast(12)) {
            val role = if (entry.role == "user") "user" else "model"
            sb.append("<start_of_turn>$role\n${entry.content}<end_of_turn>\n")
        }

        // Current user input
        sb.append("<start_of_turn>user\n${currentInput.trim()}<end_of_turn>\n")

        // Signal model to begin generating
        sb.append("<start_of_turn>model\n")

        return sb.toString()
    }

    /**
     * Strip Gemma 3 control tokens and repetition from model output.
     */
    private fun cleanResponse(raw: String): String {
        var text = raw

        // Strip turn control tokens
        for (token in STOP_SEQUENCES) {
            val idx = text.indexOf(token)
            if (idx >= 0) text = text.substring(0, idx)
        }

        // Remove leading/trailing whitespace & newlines
        text = text.trim()

        // Remove a common Gemma hallucination: repeating the question back
        if (text.startsWith("user\n") || text.startsWith("User:")) {
            val newlineIdx = text.indexOf('\n')
            if (newlineIdx > 0) text = text.substring(newlineIdx + 1).trim()
        }

        // Collapse excessive blank lines
        text = text.replace(Regex("\n{3,}"), "\n\n")

        return text.ifBlank { "I didn't quite catch that — could you rephrase?" }
    }

    /**
     * Contextual fallback when model is not loaded.
     */
    private fun getFallbackResponse(input: String): String {
        val lower = input.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Good day, sir. J.A.R.V.I.S. systems are online. I'm currently operating in demo mode — to unlock full intelligence, import a Gemma 3 model via settings."
            lower.contains("name") || lower.contains("who are you") || lower.contains("jarvis") ->
                "I am J.A.R.V.I.S. — Just A Rather Very Intelligent System — created by Tony Stark. Fully operational and at your service, once a model is imported."
            lower.contains("time") ->
                "I'd check the chronometer on your device, sir. I'm in demo mode at the moment."
            lower.contains("weather") ->
                "Weather analysis requires my full inference model, sir. Import a Gemma 3 model to activate complete capabilities."
            lower.contains("thank") ->
                "My pleasure, sir. That's precisely what I'm here for."
            lower.contains("joke") ->
                "Why did the A.I. request a day off? Too many unresolved exceptions. Import my model for considerably better material, sir."
            lower.contains("iron man") || lower.contains("tony") || lower.contains("stark") ->
                "Mr. Stark was a remarkable individual, sir. I do miss working alongside him. Is there something I can assist you with?"
            else ->
                "I'm operating in demo mode, sir — my full intelligence core is dormant. Import a Gemma 3 model via the settings panel to activate J.A.R.V.I.S. at full capacity."
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    private fun trimHistory() {
        // Keep last 20 entries (10 conversation turns)
        while (conversationHistory.size > 20) {
            conversationHistory.removeAt(0)
        }
    }

    fun getModelInfo(): String {
        return if (isReady && loadedModelPath != null) {
            val file = File(loadedModelPath!!)
            "Gemma 3 1B IT · ${file.length() / 1024 / 1024}MB · On-device"
        } else {
            "J.A.R.V.I.S. intelligence core offline — import model"
        }
    }

    fun shutdown() {
        try {
            llmInference?.let {
                it.javaClass.getMethod("close").invoke(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shutdown warning (non-fatal): ${e.message}")
        }
        llmInference = null
        isReady = false
        isLoading = false
    }
}
