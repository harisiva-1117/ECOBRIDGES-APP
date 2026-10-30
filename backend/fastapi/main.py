"""
ECOBRIDGES Voice & Speech Processing Engine (FastAPI)

Provides speech-to-text (faster-whisper) and multilingual NLP intent
understanding (Hindi, Marathi, English, Hinglish).

Security posture:
  * CORS is deny-by-default: only origins listed in CORS_ORIGINS are allowed.
  * Uploads are capped by MAX_AUDIO_BYTES before any decoding/transcription.
  * Internal errors are logged server-side and returned as generic messages.
  * No simulated transcripts: if the STT model is unavailable the API returns
    503 instead of inventing text.
  * Optional shared-secret gate (VOICE_API_KEY) for internal deployments.
"""

import base64
import binascii
import logging
import os
from typing import Optional

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from whisper_service import TranscriptionUnavailableError, WhisperSpeechService
from nlp_intent_service import NlpIntentService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("FastAPIVoice")


def _int_env(name: str, default: int) -> int:
    raw = os.getenv(name, "").strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        logger.warning("Ignoring non-numeric %s; using %s", name, default)
        return default
    return value if value > 0 else default


MAX_AUDIO_BYTES = _int_env("MAX_AUDIO_BYTES", 10 * 1024 * 1024)
MAX_TRANSCRIPT_CHARS = _int_env("MAX_TRANSCRIPT_CHARS", 2000)
VOICE_API_KEY = os.getenv("VOICE_API_KEY", "").strip()

ALLOWED_ORIGINS = [
    origin.strip()
    for origin in os.getenv("CORS_ORIGINS", "").split(",")
    if origin.strip()
]

app = FastAPI(
    title="ECOBRIDGES Multilingual Voice & Intent Service",
    version="1.1.0",
    description=(
        "Speech-to-text and multilingual intent service for Hindi, Marathi, "
        "English and Hinglish."
    ),
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=False,
    allow_methods=["POST", "GET"],
    allow_headers=["Content-Type", "Accept", "X-API-Key"],
    max_age=3600,
)

whisper_service = WhisperSpeechService()
nlp_service = NlpIntentService()


def require_api_key(x_api_key: Optional[str] = Header(default=None)) -> None:
    """Optional shared-secret gate.

    When VOICE_API_KEY is unset the service is open (use only behind a private
    network / the authenticated NestJS gateway). When set, every business
    endpoint requires a matching X-API-Key header.
    """
    if not VOICE_API_KEY:
        return
    if not x_api_key or not _constant_time_equals(x_api_key, VOICE_API_KEY):
        raise HTTPException(status_code=401, detail="Invalid or missing API key.")


def _constant_time_equals(left: str, right: str) -> bool:
    import hmac

    return hmac.compare_digest(left.encode("utf-8"), right.encode("utf-8"))


class SemanticIntentRequest(BaseModel):
    transcript: str = Field(min_length=0, max_length=MAX_TRANSCRIPT_CHARS)
    language: Optional[str] = Field(default="en", max_length=16)
    currentScreen: Optional[str] = Field(default="INTRO", max_length=64)
    currentRole: Optional[str] = Field(default=None, max_length=64)


class Base64AudioRequest(BaseModel):
    # Base64 is ~4/3 of the binary size; cap the encoded payload too.
    audioBase64: str = Field(min_length=1, max_length=(MAX_AUDIO_BYTES * 4 // 3) + 16)
    language: Optional[str] = Field(default=None, max_length=16)
    currentScreen: Optional[str] = Field(default="INTRO", max_length=64)
    currentRole: Optional[str] = Field(default=None, max_length=64)


async def _read_limited(upload: UploadFile) -> bytes:
    """Reads an upload in chunks and aborts as soon as the cap is exceeded."""
    chunks: list[bytes] = []
    total = 0
    while True:
        chunk = await upload.read(64 * 1024)
        if not chunk:
            break
        total += len(chunk)
        if total > MAX_AUDIO_BYTES:
            raise HTTPException(
                status_code=413,
                detail=f"Audio file exceeds the {MAX_AUDIO_BYTES} byte limit.",
            )
        chunks.append(chunk)
    if total == 0:
        raise HTTPException(status_code=400, detail="The uploaded audio file is empty.")
    return b"".join(chunks)


def _decode_audio_b64(payload: str) -> bytes:
    try:
        audio = base64.b64decode(payload, validate=True)
    except (binascii.Error, ValueError):
        raise HTTPException(status_code=400, detail="audioBase64 is not valid base64.")
    if not audio:
        raise HTTPException(status_code=400, detail="audioBase64 decoded to an empty payload.")
    if len(audio) > MAX_AUDIO_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Decoded audio exceeds the {MAX_AUDIO_BYTES} byte limit.",
        )
    return audio


@app.get("/health")
def health_check():
    return {
        "status": "healthy",
        "service": "ECOBRIDGES FastAPI Voice Layer",
        "whisperReady": whisper_service.model is not None,
        "supportedLanguages": ["hi", "mr", "en", "hinglish"],
    }


@app.post("/api/v1/voice/transcribe", dependencies=[Depends(require_api_key)])
async def transcribe_audio(
    file: UploadFile = File(...),
    language: Optional[str] = Form(None),
):
    """Transcribes an uploaded audio file with the local Whisper model."""
    preferred = language if language in ("hi", "mr", "en") else None
    audio_content = await _read_limited(file)
    try:
        return whisper_service.transcribe_audio(audio_content, preferred_language=preferred)
    except TranscriptionUnavailableError:
        logger.error("Transcription requested but the STT model is unavailable.")
        raise HTTPException(
            status_code=503,
            detail="Speech recognition is not available on this server.",
        )
    except Exception:
        logger.exception("Unexpected failure while transcribing an upload.")
        raise HTTPException(status_code=500, detail="Transcription failed.")


@app.post("/api/v1/voice/semantic-intent", dependencies=[Depends(require_api_key)])
def analyze_semantic_intent(request: SemanticIntentRequest):
    """Maps a transcript to a semantic intent for the given screen/role."""
    try:
        return nlp_service.parse_intent(
            transcript=request.transcript,
            current_screen=request.currentScreen or "INTRO",
            current_role=request.currentRole,
            language=request.language or "en",
        )
    except Exception:
        logger.exception("Unexpected failure while parsing an intent.")
        raise HTTPException(status_code=500, detail="Intent analysis failed.")


@app.post("/api/v1/voice/process-audio", dependencies=[Depends(require_api_key)])
async def process_full_voice_pipeline(request: Base64AudioRequest):
    """Base64 audio -> Whisper STT -> NLP intent."""
    audio_bytes = _decode_audio_b64(request.audioBase64)
    preferred = request.language if request.language in ("hi", "mr", "en") else None
    try:
        transcription = whisper_service.transcribe_audio(
            audio_bytes, preferred_language=preferred
        )
    except TranscriptionUnavailableError:
        logger.error("Voice pipeline requested but the STT model is unavailable.")
        raise HTTPException(
            status_code=503,
            detail="Speech recognition is not available on this server.",
        )
    except Exception:
        logger.exception("Unexpected failure while transcribing base64 audio.")
        raise HTTPException(status_code=500, detail="Transcription failed.")

    try:
        intent_result = nlp_service.parse_intent(
            transcript=transcription.get("text", ""),
            current_screen=request.currentScreen or "INTRO",
            current_role=request.currentRole,
            language=transcription.get("language", request.language or "en"),
        )
    except Exception:
        logger.exception("Unexpected failure while parsing the pipeline intent.")
        raise HTTPException(status_code=500, detail="Intent analysis failed.")

    return {
        "transcription": transcription,
        "intentAnalysis": intent_result,
        "detectedLanguage": transcription.get("language"),
        "isHinglish": transcription.get("is_hinglish", False),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        app,
        host=os.getenv("HOST", "127.0.0.1"),
        port=_int_env("PORT", 8000),
    )
