# Miss Minutes — On-Device AI Voice Assistant for Android

> *"Well hey there, partner! I'm Miss Minutes, and I'm here to help!"*

A fully offline, TVA-themed AI voice assistant for Android. No internet. No API keys. Pure on-device AI.

---

## Screenshots

The app features:
- 🟠 Animated holographic clock face (Miss Minutes' "body")
- 🎙️ Voice input → on-device LLM → female TTS voice
- ⚫ Deep black TVA aesthetic with amber glow and CRT scanlines
- ⏰ Real-time clock hand inside the orb

---

## Requirements

| Item | Requirement |
|------|-------------|
| Android | 11+ (API 30) |
| RAM | 4GB minimum |
| Architecture | ARM64 |
| Storage | ~2GB free (for model) |
| **Recommended Device** | Pixel 8, Samsung S23 or newer |

> ⚠️ **LLM inference does NOT work on emulators.** All other UI features work fine without the model.

---

## Quick Start

### Step 1 — Open in Android Studio

1. Open Android Studio (Hedgehog or newer)
2. **File → Open** → select the `Miss Minutes/` folder
3. Wait for Gradle sync to complete
4. Connect your physical Android device

### Step 2 — Download the Gemma Model

Download **Gemma 3 1B (4-bit quantized)** from Hugging Face:

```
https://huggingface.co/litert-community/Gemma3-1B-IT
```

Download the file named: `gemma3-1b-it-int4.litertlm`

### Step 3 — Push Model to Device via ADB

```bash
# Enable USB debugging on your device first
# Then run these commands in your terminal:

adb shell rm -rf /data/local/tmp/llm/
adb shell mkdir -p /data/local/tmp/llm/

# Push the model (this takes 2-5 minutes for ~700MB)
adb push gemma3-1b-it-int4.litertlm /data/local/tmp/llm/model.litertlm

# Verify
adb shell ls -la /data/local/tmp/llm/
```

### Step 4 — Install Offline Speech Pack

On your device, go to:
**Settings → System → Languages → Speech → Offline speech recognition**

Download **English (United States)** offline pack. This enables fully offline voice input.

### Step 5 — Build & Run

In Android Studio:
- Click **Run ▶** or press `Shift+F10`
- Select your device
- The app installs and launches

---

## Sound Effects (Optional)

The app ships with placeholder silent audio. To add real TVA-themed sounds:

Download from [Freesound.org](https://freesound.org) (CC0 license):

| File | Replace with |
|------|-------------|
| `res/raw/tva_intro.mp3` | Ambient clock + theremin, ~3 seconds |
| `res/raw/mic_on.mp3` | Soft clock chime / bell ding |
| `res/raw/response_start.mp3` | Sci-fi whoosh / hologram materializing |
| `res/raw/response_end.mp3` | Soft clock tick |

Suggested Freesound searches:
- "clock chime soft"
- "theremin ambient"
- "sci-fi whoosh short"
- "analog clock tick"

---

## Project Structure

```
Miss Minutes/
├── app/src/main/
│   ├── java/com/tva/missminutes/
│   │   ├── MainActivity.kt          — Entry point
│   │   ├── MissMinutesApp.kt        — Application class
│   │   ├── ai/
│   │   │   ├── LLMEngine.kt         — LiteRT-LM wrapper (Gemma 3)
│   │   │   ├── TTSEngine.kt         — Android TTS (female voice)
│   │   │   └── SpeechEngine.kt      — Offline speech recognition
│   │   ├── audio/
│   │   │   └── SoundManager.kt      — SFX player
│   │   ├── ui/
│   │   │   ├── theme/               — TVA orange/black palette
│   │   │   ├── components/
│   │   │   │   ├── HolographicOrb.kt — 9-layer animated clock face
│   │   │   │   ├── MicButton.kt     — Animated mic + state rings
│   │   │   │   └── StatusText.kt    — Typewriter response display
│   │   │   └── screen/
│   │   │       └── AssistantScreen.kt — Main screen
│   │   └── viewmodel/
│   │       └── AssistantViewModel.kt — State machine
│   └── res/
│       ├── raw/                     — Sound effects
│       ├── values/                  — Strings, colors, themes
│       └── drawable/                — Icon drawables
└── README.md
```

---

## How It Works

```
[You speak]
    ↓
[SpeechEngine] — Android SpeechRecognizer (offline mode)
    ↓
[LLMEngine] — Gemma 3 1B via LiteRT-LM (streaming tokens)
    ↓
[TTSEngine] — Android TTS (female voice, pitch 1.15)
    ↓
[HolographicOrb] — Pulses in sync with amplitude simulation
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "Model not found" footer message | Push model via ADB (Step 3) |
| Voice recognition not working | Install offline speech pack (Step 4) |
| App crashes on launch | Ensure device has ARM64 arch + 4GB RAM |
| LLM very slow | Normal for first response; try Gemma 3 1B over 2B |
| No sound from mic | Grant RECORD_AUDIO permission in app settings |
| TTS voice sounds robotic | Normal — varies by device. Install Google TTS for best quality |

---

## Alternative Model Locations

The app searches for the model in this order:
1. `/data/local/tmp/llm/model.litertlm`
2. `/data/local/tmp/llm/gemma3-1b-it-int4.litertlm`
3. `/data/local/tmp/llm/gemma-3-1b-it-int4.litertlm`
4. App's internal files dir: `[app_files]/models/model.litertlm`
5. External files dir: `[external]/models/model.litertlm`

---

## Miss Minutes Personality

The LLM is primed with this system prompt:

> *"You are Miss Minutes, the animated AI assistant from the Time Variance Authority (TVA). Speak with Southern charm, reference TVA protocols, address users as 'partner' or 'sugar', keep responses 2-3 sentences maximum..."*

---

## License

Personal use only. Miss Minutes, the TVA, and all related characters are property of Marvel Studios / Disney. This is a fan project for educational purposes. Do not distribute.

---

*"For all time. Always."* — TVA
