"""
ECOBRIDGES Whisper Speech-to-Text Service
Configured for native Hindi (hi), Marathi (mr), English (en), and Hinglish speech recognition.
Utilizes faster-whisper with language-specific beam search, temperature fallback,
and multi-script normalization.
"""

import io
import os
import logging
from typing import Dict, Any, Tuple, Optional

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("WhisperService")

# Supported language codes
SUPPORTED_LANGUAGES = ["hi", "mr", "en"]


class TranscriptionUnavailableError(RuntimeError):
    """Raised when the STT model is not loaded.

    The service must never invent a transcript: a fabricated result would be
    indistinguishable from real speech recognition and could drive real
    business actions (lot creation, price queries) from audio that was never
    understood.
    """


class WhisperSpeechService:
    def __init__(self):
        self.model_size = os.getenv("WHISPER_MODEL_SIZE", "base")
        self.device = os.getenv("WHISPER_DEVICE", "cpu")
        self.compute_type = os.getenv("WHISPER_COMPUTE_TYPE", "int8")
        self.model = None
        self._initialize_model()

    def _initialize_model(self):
        if os.getenv("WHISPER_DISABLE_MODEL", "").strip().lower() in ("1", "true", "yes"):
            logger.warning("WHISPER_DISABLE_MODEL is set: running without an STT model.")
            self.model = None
            return
        try:
            from faster_whisper import WhisperModel
            logger.info(f"Loading faster-whisper model '{self.model_size}' on {self.device} ({self.compute_type})...")
            self.model = WhisperModel(
                self.model_size,
                device=self.device,
                compute_type=self.compute_type,
                download_root=os.getenv("WHISPER_CACHE_DIR", None)
            )
            logger.info("faster-whisper model loaded successfully.")
        except Exception as e:
            logger.error(
                "Could not load faster-whisper locally (%s). The transcribe "
                "endpoints will return 503 until a model is available; no "
                "simulated transcript is produced.",
                e,
            )
            self.model = None

    def transcribe_audio(
        self,
        audio_bytes: bytes,
        preferred_language: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Transcribes incoming audio bytes into text with native language detection.
        Specifically configured for Hindi, Marathi, English, and Hinglish code-switching.
        """
        if not audio_bytes or len(audio_bytes) < 100:
            return {
                "text": "",
                "language": preferred_language or "en",
                "confidence": 0.0,
                "is_hinglish": False
            }

        if self.model is None:
            raise TranscriptionUnavailableError(
                "Speech recognition model is not loaded. Set WHISPER_MODEL_SIZE "
                "and ensure faster-whisper models are available; no simulated "
                "transcript is produced."
            )

        try:
            audio_stream = io.BytesIO(audio_bytes)
            # Configure transcription with language detection
            segments, info = self.model.transcribe(
                audio_stream,
                beam_size=5,
                language=preferred_language if preferred_language in SUPPORTED_LANGUAGES else None,
                task="transcribe",
                initial_prompt="ECOBRIDGES, CPCB, E-Waste, Recycler, Collector, Admin, Scrap, Metal, Rupees, Lot, किलोग्राम, दर, भाव, कचरा",
                vad_filter=True,
                vad_parameters=dict(min_silence_duration_ms=500)
            )

            text_segments = [segment.text.strip() for segment in segments]
            full_transcript = " ".join(text_segments)

            detected_lang = info.language
            prob = info.language_probability

            is_hinglish = self._detect_hinglish(full_transcript, detected_lang)

            return {
                "text": full_transcript,
                "language": detected_lang,
                "confidence": float(prob),
                "is_hinglish": is_hinglish,
                "duration": info.duration
            }
        except Exception as e:
            logger.error(f"Error during transcription: {e}")
            raise TranscriptionUnavailableError(
                "Speech recognition failed for this audio file."
            ) from e

    def _detect_hinglish(self, text: str, detected_lang: str) -> bool:
        """
        Detects Hinglish or code-mixed Indian dialect (Latin script mixing Hindi/Marathi words).
        """
        lower = text.lower()
        hinglish_markers = [
            "kholo", "batao", "chahiye", "mera", "meri", "kiti", "ahe", "dena", "kaise",
            "karo", "karna", "chalega", "paisa", "rupaye", "bhav", "rate", "scrap", "lot"
        ]
        has_latin = any(c.isascii() and c.isalpha() for c in text)
        has_hinglish_keyword = any(marker in lower for marker in hinglish_markers)
        return has_latin and has_hinglish_keyword
