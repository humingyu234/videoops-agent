from dataclasses import replace
from pathlib import Path
from types import SimpleNamespace

import pytest
from fastapi.testclient import TestClient

from aivideo_whisper.app import create_app
from aivideo_whisper.config import Settings
from aivideo_whisper.transcriber import TranscriptionResult

WAV = b"RIFF\x24\x00\x00\x00WAVEfmt "


class FakeTranscriber:
    def __init__(
        self,
        text: str = " 欢迎   使用声音工作台。 ",
        words: tuple[SimpleNamespace, ...] = (),
    ) -> None:
        self.text = text
        self.words = words
        self.paths: list[Path] = []
        self.word_timestamp_requests: list[bool] = []

    def transcribe(
        self,
        path: Path,
        language: str | None,
        word_timestamps: bool = False,
    ) -> TranscriptionResult:
        self.paths.append(path)
        self.word_timestamp_requests.append(word_timestamps)
        assert path.exists()
        if self.words:
            return SimpleNamespace(
                text=self.text,
                language=language or "zh",
                duration_millis=1234,
                words=self.words,
            )
        return TranscriptionResult(text=self.text, language=language or "zh", duration_millis=1234)


@pytest.fixture
def settings(tmp_path: Path) -> Settings:
    return Settings(
        internal_token="test-secret",
        model_path=tmp_path / "model",
        temp_dir=tmp_path / "uploads",
        max_upload_bytes=1024,
    )


@pytest.fixture
def fake_model() -> FakeTranscriber:
    return FakeTranscriber()


@pytest.fixture
def client(settings: Settings, fake_model: FakeTranscriber) -> TestClient:
    return TestClient(create_app(settings, transcriber=fake_model))


def test_health_reports_loaded_model(client: TestClient) -> None:
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_health_returns_503_when_model_is_not_loaded(settings: Settings) -> None:
    with TestClient(create_app(settings, model_loader=lambda _: None)) as unloaded:
        response = unloaded.get("/health")
    assert response.status_code == 503


def test_transcribe_requires_internal_token(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/transcriptions",
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.status_code == 401


def test_transcribe_returns_normalized_text(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1", "language": "zh", "wordTimestamps": "false"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.status_code == 200
    assert response.json() == {
        "requestId": "1:1:1",
        "text": "欢迎 使用声音工作台。",
        "language": "zh",
        "durationMillis": 1234,
    }


def test_transcribe_passes_word_timestamp_request_to_model(
    client: TestClient, fake_model: FakeTranscriber
) -> None:
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1", "language": "zh", "wordTimestamps": "true"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )

    assert response.status_code == 200
    assert fake_model.word_timestamp_requests == [True]
    assert response.json()["words"] == []


def test_transcribe_returns_word_timestamps(settings: Settings) -> None:
    model = FakeTranscriber(
        words=(
            SimpleNamespace(text="微信", start_millis=120, end_millis=480),
            SimpleNamespace(text="公众号", start_millis=500, end_millis=920),
        )
    )
    client = TestClient(create_app(settings, transcriber=model))

    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1", "language": "zh", "wordTimestamps": "true"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )

    assert response.status_code == 200
    assert response.json()["words"] == [
        {"text": "微信", "startMillis": 120, "endMillis": 480},
        {"text": "公众号", "startMillis": 500, "endMillis": 920},
    ]


def test_rejects_unsupported_mime(client: TestClient) -> None:
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1"},
        files={"file": ("a.txt", b"hello", "text/plain")},
    )
    assert response.status_code == 415


def test_rejects_oversized_upload(settings: Settings, fake_model: FakeTranscriber) -> None:
    tiny = replace(settings, max_upload_bytes=4)
    client = TestClient(create_app(tiny, transcriber=fake_model))
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.status_code == 413


def test_rejects_empty_transcript(settings: Settings) -> None:
    client = TestClient(create_app(settings, transcriber=FakeTranscriber("  \n ")))
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.status_code == 422


def test_removes_temporary_file_after_transcription(
    client: TestClient, fake_model: FakeTranscriber
) -> None:
    response = client.post(
        "/internal/v1/transcriptions",
        headers={"X-Internal-Token": "test-secret"},
        data={"requestId": "1:1:1"},
        files={"file": ("a.wav", WAV, "audio/wav")},
    )
    assert response.status_code == 200
    assert fake_model.paths
    assert not fake_model.paths[0].exists()
