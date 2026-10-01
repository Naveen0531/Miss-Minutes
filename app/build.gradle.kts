plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.tva.missminutes"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tva.missminutes"
        minSdk = 31          // Android 12+ (Realme P4 Pro runs Android 16 — safe)
        targetSdk = 36
        versionCode = 3
        versionName = "3.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // Arm64 for modern phones (Realme P4 Pro), armeabi-v7a for older Android 12 devices
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose    = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/DEPENDENCIES"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)

    // Android Core
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.animation:animation-core")
    implementation("androidx.compose.runtime:runtime")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Accompanist Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.37.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")

    // LiteRT-LM — On-Device LLM (MediaPipe Gemma 3) — fallback mode
    implementation("com.google.mediapipe:tasks-genai:0.10.35")

    // ── LiveKit Android SDK — v3.0 Voice + Camera ─────────────────────────
    // WebRTC room connection, mic + camera track publishing, agent audio
    implementation("io.livekit:livekit-android:2.10.0")
    // CameraX integration for front/back camera video tracks
    implementation("io.livekit:livekit-android-camerax:2.10.0")

    // CameraX (required by livekit-android-camerax)
    implementation("androidx.camera:camera-core:1.5.0-alpha06")
    implementation("androidx.camera:camera-camera2:1.5.0-alpha06")
    implementation("androidx.camera:camera-lifecycle:1.5.0-alpha06")
    implementation("androidx.camera:camera-view:1.5.0-alpha06")

    // Networking (for token endpoint / HTTP health checks)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
