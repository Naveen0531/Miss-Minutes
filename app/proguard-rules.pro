# Miss Minutes v2 — ProGuard Rules

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes Exceptions

# ── MediaPipe / LiteRT (used via reflection in LLMEngine) ────────────────────
-keep class com.google.mediapipe.** { *; }
-keep class com.google.ai.edge.** { *; }
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.mediapipe.tasks.genai.llminference.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.ai.edge.**

# ── Kotlin Coroutines ─────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Miss Minutes app classes ──────────────────────────────────────────────────
-keep class com.tva.missminutes.** { *; }

# ── Compose ───────────────────────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Android Speech + TTS ──────────────────────────────────────────────────────
-keep class android.speech.** { *; }
-keep class android.speech.tts.** { *; }
