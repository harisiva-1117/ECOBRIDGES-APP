"""Security and contract tests for the FastAPI voice service.

Run:  python -m pytest -q
The STT model is disabled (WHISPER_DISABLE_MODEL=1) so the suite is fast,
offline and deterministic. It asserts the service never fabricates a
transcript and never leaks internal error details.
"""

import base64
import os

import pytest
from fastapi.testclient import TestClient

os.environ["WHISPER_DISABLE_MODEL"] = "1"
os.environ["CORS_ORIGINS"] = "https://app.example.org"
os.environ["MAX_AUDIO_BYTES"] = "1024"
os.environ.pop("VOICE_API_KEY", None)

import main  # noqa: E402  (must come after the env vars above)


@pytest.fixture(scope="module")
def client():
    return TestClient(main.app)


def test_health_reports_model_state(client):
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "healthy"
    # The test environment disables the model on purpose.
    assert body["whisperReady"] is False


def test_transcribe_fails_closed_without_model(client):
    response = client.post(
        "/api/v1/voice/transcribe",
        files={"file": ("sample.wav", b"x" * 512, "audio/wav")},
    )
    assert response.status_code == 503
    assert "not available" in response.json()["detail"]
    # Never a fabricated transcript.
    assert "transcription" not in response.json()


def test_transcribe_rejects_oversized_upload(client):
    response = client.post(
        "/api/v1/voice/transcribe",
        files={"file": ("big.wav", b"x" * 4096, "audio/wav")},
    )
    assert response.status_code == 413


def test_transcribe_rejects_empty_upload(client):
    response = client.post(
        "/api/v1/voice/transcribe",
        files={"file": ("empty.wav", b"", "audio/wav")},
    )
    assert response.status_code == 400


def test_process_audio_rejects_invalid_base64(client):
    response = client.post(
        "/api/v1/voice/process-audio",
        json={"audioBase64": "not*valid*base64!!"},
    )
    assert response.status_code == 400
    assert "base64" in response.json()["detail"].lower()


def test_process_audio_rejects_oversized_decoded_payload(client):
    # 1032 bytes decode from 1376 base64 chars: under the encoded cap, over
    # the decoded MAX_AUDIO_BYTES (1024).
    payload = base64.b64encode(b"x" * 1032).decode("ascii")
    assert len(payload) <= 1381
    response = client.post("/api/v1/voice/process-audio", json={"audioBase64": payload})
    assert response.status_code == 413


def test_semantic_intent_returns_structured_result(client):
    response = client.post(
        "/api/v1/voice/semantic-intent",
        json={"transcript": "copper wire ka rate kya hai", "language": "en"},
    )
    assert response.status_code == 200
    assert isinstance(response.json(), dict)


def test_transcript_length_is_bounded(client):
    response = client.post(
        "/api/v1/voice/semantic-intent",
        json={"transcript": "a" * 5000},
    )
    assert response.status_code == 422


def test_cors_is_not_wildcard(client):
    allowed = client.options(
        "/api/v1/voice/semantic-intent",
        headers={
            "Origin": "https://app.example.org",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert allowed.headers.get("access-control-allow-origin") == "https://app.example.org"

    denied = client.options(
        "/api/v1/voice/semantic-intent",
        headers={
            "Origin": "https://evil.example.net",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert denied.headers.get("access-control-allow-origin") is None
    assert "*" not in str(denied.headers.get("access-control-allow-origin"))


def test_optional_api_key_gate(client, monkeypatch):
    monkeypatch.setattr(main, "VOICE_API_KEY", "test-secret")

    unauthorized = client.post(
        "/api/v1/voice/semantic-intent",
        json={"transcript": "hello"},
    )
    assert unauthorized.status_code == 401

    wrong = client.post(
        "/api/v1/voice/semantic-intent",
        json={"transcript": "hello"},
        headers={"X-API-Key": "wrong"},
    )
    assert wrong.status_code == 401

    accepted = client.post(
        "/api/v1/voice/semantic-intent",
        json={"transcript": "hello"},
        headers={"X-API-Key": "test-secret"},
    )
    assert accepted.status_code == 200
