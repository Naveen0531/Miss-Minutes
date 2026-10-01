@echo off
title J.A.R.V.I.S. Startup Sequence
color 1F
cls

echo.
echo  ___  ________  ________  ___      ___ ___  ________      
echo ^|^  ^\ ^|  __  __^|^|  __  __^|^  \    /  /^|  ^\/  /  ___^|  
echo  \  \^|  ^|\  ^|  ^|  ^|\  ^|  ^|\  \  /  / ^|  ;  ;^| /__    
echo   \  .  .  ^|  ^|  ^|  ^|  ^|  .\  \/  /  ^| /  / ^|    /  
echo    \_^|\_^|___/  ^|__^|  ^|__^|  ..\    /   ^|___/  ^|___/ 
echo         Just A Rather Very Intelligent System v3.0
echo.
echo  [STARK INDUSTRIES] Local AI Stack — Initializing...
echo ========================================================
echo.

REM ── Step 1: Check Docker ────────────────────────────────────────────
echo [1/4] Checking Docker Desktop...
docker --version >nul 2>&1
if %errorlevel% neq 0 (
    echo  ERROR: Docker Desktop is not running or not installed!
    echo  Please install from: https://www.docker.com/products/docker-desktop
    echo  Then restart this script.
    pause
    exit /b 1
)
echo  ✓ Docker is running
echo.

REM ── Step 2: Check Ollama ────────────────────────────────────────────
echo [2/4] Checking Ollama + gemma3:4b...
ollama --version >nul 2>&1
if %errorlevel% neq 0 (
    echo  WARNING: Ollama not found in PATH.
    echo  Download from: https://ollama.com
    echo  After installing, run: ollama pull gemma3:4b
    echo  Continuing anyway — you can install Ollama and restart.
    echo.
) else (
    echo  ✓ Ollama found
    echo  Pulling gemma3:4b model if not already present...
    ollama pull gemma3:4b
    echo  ✓ Gemma 3 4B ready
    echo.
)

REM ── Step 3: Start Docker Services ──────────────────────────────────
echo [3/4] Starting LiveKit Server + Kokoro TTS + Whisper STT...
echo  (First run will download Docker images — may take a few minutes)
echo.
docker compose -f "%~dp0docker-compose.yml" up -d
if %errorlevel% neq 0 (
    echo  ERROR: Failed to start Docker services!
    echo  Make sure Docker Desktop is running and try again.
    pause
    exit /b 1
)
echo.
echo  ✓ Services starting...
echo    LiveKit Server → ws://localhost:7880
echo    Kokoro TTS     → http://localhost:8880
echo    Whisper STT    → http://localhost:9000
echo.

REM ── Step 4: Wait for services to be ready ──────────────────────────
echo  Waiting 8 seconds for services to fully initialize...
timeout /t 8 /nobreak >nul

REM ── Step 5: Start Jarvis Agent ─────────────────────────────────────
echo [4/4] Activating J.A.R.V.I.S. Agent...
echo.
echo  Press Ctrl+C to shut down all systems
echo ========================================================
echo.

REM Check if Python venv exists
if exist "%~dp0venv\Scripts\activate.bat" (
    call "%~dp0venv\Scripts\activate.bat"
) else (
    echo  Creating Python virtual environment...
    python -m venv "%~dp0venv"
    call "%~dp0venv\Scripts\activate.bat"
    echo  Installing dependencies...
    pip install -r "%~dp0requirements.txt" -q
    echo  ✓ Dependencies installed
    echo.
)

REM Run the agent in dev mode
python "%~dp0agent.py" dev

REM Shutdown cleanup
echo.
echo  J.A.R.V.I.S. shutting down...
docker compose -f "%~dp0docker-compose.yml" down
echo  All systems offline.
pause
