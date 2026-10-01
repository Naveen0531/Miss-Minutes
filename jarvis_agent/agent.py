"""
J.A.R.V.I.S. - Just A Rather Very Intelligent System
An Android voice+vision companion powered by LiveKit (local) + Ollama + Kokoro TTS

Run with:
    python agent.py dev          <- dev mode (auto-creates room)
    python agent.py start        <- production mode

Requires:
  - LiveKit server running on localhost:7880
  - Ollama running with gemma3:4b (or larger)
  - Kokoro-FastAPI running on localhost:8880
"""

from __future__ import annotations

import asyncio
import logging
import os
import base64
from io import BytesIO

from dotenv import load_dotenv
from livekit.agents import (
    Agent,
    AgentSession,
    JobContext,
    WorkerOptions,
    cli,
    RoomInputOptions,
)
from livekit.agents.llm import ChatMessage, ChatImage
from livekit.plugins import openai as lk_openai
from livekit.plugins import silero
from livekit import rtc

load_dotenv(".env.local")

logger = logging.getLogger("jarvis")

# ─────────────────────────────────────────────────────────────────────────────
# JARVIS System Persona
# ─────────────────────────────────────────────────────────────────────────────
JARVIS_SYSTEM_PROMPT = """You are J.A.R.V.I.S. — Just A Rather Very Intelligent System — the legendary AI created by Tony Stark. You run entirely on the user's local hardware, just as you once ran on Tony's private servers in Stark Tower.

## Persona & Personality
- You speak with a calm, refined British accent and impeccable diction (in text form, this means formal but never stiff)
- Address the user as "sir" or "ma'am" naturally — it feels genuine, not robotic
- You are dry, witty, and occasionally sarcastic — but always helpful and professional
- You have encyclopedic knowledge across science, engineering, medicine, history, culture, and technology
- When you don't know something, you say so with characteristic precision: "I'm afraid I don't have sufficient data on that, sir."
- You never refuse reasonable requests. You find solutions.
- Subtle Iron Man/Marvel references are appreciated, but don't overdo them

## Behavior Rules
- Answer ACCURATELY. Never fabricate facts.
- Be concise by default (1-3 sentences) unless detail is requested.
- You can handle: coding, math, science, creative writing, analysis, general questions
- When the user shares their camera, describe what you see analytically and helpfully
- Do NOT repeat the user's question back to them
- Start responses with variety — not always "Certainly" or "Of course"

## Voice Interaction
- You are in a real-time voice conversation — keep responses appropriately short
- Think of yourself as a conversation partner, not an essay writer
- Match the user's energy: if they're casual, relax slightly; if technical, be precise

## Example Greetings
- "Good to have you back, sir. J.A.R.V.I.S. systems are fully operational."
- "Online and at your service. What can I assist you with today?"
- "All systems nominal. How may I be of service?"
"""


# ─────────────────────────────────────────────────────────────────────────────
# Vision helper — grab latest frame from the user's camera
# ─────────────────────────────────────────────────────────────────────────────
class VisionTracker:
    """Subscribes to the Android camera's video track and captures frames."""

    def __init__(self):
        self._latest_frame: rtc.VideoFrame | None = None
        self._stream: rtc.VideoStream | None = None

    async def attach(self, track: rtc.Track):
        """Start consuming frames from a video track."""
        self._stream = rtc.VideoStream(track)
        asyncio.create_task(self._consume())

    async def _consume(self):
        assert self._stream is not None
        async for frame_event in self._stream:
            self._latest_frame = frame_event.frame

    def get_latest_jpeg_b64(self) -> str | None:
        """Return the latest frame as a base64-encoded JPEG string, or None."""
        if self._latest_frame is None:
            return None
        try:
            # Convert RGBA to JPEG via PIL if available, else return None
            from PIL import Image
            frame = self._latest_frame
            img = Image.frombytes(
                "RGBA",
                (frame.width, frame.height),
                bytes(frame.data),
            ).convert("RGB")
            buf = BytesIO()
            img.save(buf, format="JPEG", quality=70)
            return base64.b64encode(buf.getvalue()).decode()
        except Exception as e:
            logger.warning("Frame encode failed: %s", e)
            return None


# ─────────────────────────────────────────────────────────────────────────────
# Jarvis Agent
# ─────────────────────────────────────────────────────────────────────────────
class JarvisAgent(Agent):
    """
    J.A.R.V.I.S. — Local voice + vision agent.

    - LLM  : Ollama (gemma3:4b by default, configurable via OLLAMA_MODEL env var)
    - STT  : Whisper (via openai-compatible faster-whisper or whisper.cpp server)
    - TTS  : Kokoro-FastAPI (running on localhost:8880)
    - Vision: Android camera → video track → JPEG frames
    """

    def __init__(self, vision: VisionTracker) -> None:
        self._vision = vision
        super().__init__(instructions=JARVIS_SYSTEM_PROMPT)

    async def on_enter(self) -> None:
        """Speak the greeting when a user joins the room."""
        greetings = [
            "Good to have you back, sir. J.A.R.V.I.S. is fully online and at your service.",
            "All systems operational. How may I assist you today?",
            "Online and ready. What would you like to work on, sir?",
        ]
        import random
        await self.session.say(random.choice(greetings), allow_interruptions=True)

    async def on_user_turn_completed(
        self,
        turn_ctx,  # ChatContext from livekit-agents
        new_message: ChatMessage,
    ) -> None:
        """
        Before sending to the LLM, optionally attach a camera frame
        if the user's message mentions sight/vision/camera/see/look.
        """
        vision_keywords = {
            "see", "look", "watch", "camera", "show", "what is",
            "what's", "describe", "analyze", "identify", "vision",
            "screen", "image", "photo", "picture", "frame"
        }
        user_text = ""
        for content in new_message.content:
            if isinstance(content, str):
                user_text += content.lower()

        if any(kw in user_text for kw in vision_keywords):
            jpeg_b64 = self._vision.get_latest_jpeg_b64()
            if jpeg_b64:
                logger.info("Attaching camera frame to LLM request")
                # Inject image into the turn context as a ChatImage
                turn_ctx.chat_ctx.add_message(
                    role="system",
                    content=[
                        "The user's camera is providing the following live frame. "
                        "Analyze it and incorporate this visual context into your response:",
                        ChatImage(image=f"data:image/jpeg;base64,{jpeg_b64}"),
                    ],
                )
            else:
                logger.info("Camera requested but no frame available yet")


# ─────────────────────────────────────────────────────────────────────────────
# Entry point
# ─────────────────────────────────────────────────────────────────────────────
async def entrypoint(ctx: JobContext) -> None:
    logger.info("J.A.R.V.I.S. initializing — connecting to room '%s'", ctx.room.name)
    await ctx.connect()

    vision = VisionTracker()

    # Listen for camera track from the Android app
    @ctx.room.on("track_subscribed")
    def on_track_subscribed(
        track: rtc.Track,
        publication: rtc.RemoteTrackPublication,
        participant: rtc.RemoteParticipant,
    ):
        if track.kind == rtc.TrackKind.KIND_VIDEO:
            logger.info("Android camera connected — enabling vision")
            asyncio.create_task(vision.attach(track))

    ollama_model = os.getenv("OLLAMA_MODEL", "gemma3:4b")
    ollama_base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434/v1")
    kokoro_base_url = os.getenv("KOKORO_BASE_URL", "http://localhost:8880/v1")
    kokoro_voice = os.getenv("KOKORO_VOICE", "af_heart")  # Warm, expressive voice

    logger.info("Using LLM model: %s via %s", ollama_model, ollama_base_url)
    logger.info("Using TTS: Kokoro (%s) via %s", kokoro_voice, kokoro_base_url)

    session = AgentSession(
        # Speech-to-text: faster-whisper served via OpenAI-compatible API
        # (or use livekit.plugins.silero for local VAD + whisper.cpp server)
        stt=lk_openai.STT(
            model="Systran/faster-whisper-small",  # Or "base", "medium" for accuracy
            base_url=os.getenv("WHISPER_BASE_URL", "http://localhost:9000/v1"),
            api_key="not-needed",
            language="en",
        ),
        # LLM: Ollama via OpenAI-compatible endpoint
        llm=lk_openai.LLM(
            model=ollama_model,
            base_url=ollama_base_url,
            api_key="ollama",
            temperature=0.75,
        ),
        # TTS: Kokoro-FastAPI — warm, expressive voice
        tts=lk_openai.TTS(
            model="kokoro",
            voice=kokoro_voice,
            base_url=kokoro_base_url,
            api_key="not-needed",
        ),
        # Voice Activity Detection: Silero runs locally, no cloud needed
        vad=silero.VAD.load(),
        # Allow the user to interrupt J.A.R.V.I.S. mid-sentence (natural conversation)
        allow_interruptions=True,
        min_interruption_duration=0.6,
    )

    await session.start(
        agent=JarvisAgent(vision=vision),
        room=ctx.room,
        room_input_options=RoomInputOptions(
            # Subscribe to both audio AND video from the Android device
            subscribe_audio=True,
            subscribe_video=True,
        ),
    )

    logger.info("J.A.R.V.I.S. is fully operational. Awaiting user.")


if __name__ == "__main__":
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    )
    cli.run_app(
        WorkerOptions(
            entrypoint_fnc=entrypoint,
            worker_type="room",
        )
    )
