from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from aivideo_whisper.config import Settings


@dataclass(frozen=True, slots=True)
class TranscriptionWord:
    text: str
    start_millis: int
    end_millis: int


@dataclass(frozen=True, slots=True)
class TranscriptionResult:
    text: str
    language: str
    duration_millis: int
    words: tuple[TranscriptionWord, ...] = ()


class Transcriber(Protocol):
    def transcribe(
        self,
        path: Path,
        language: str | None,
        word_timestamps: bool = False,
    ) -> TranscriptionResult: ...


class FasterWhisperTranscriber:
    def __init__(self, settings: Settings) -> None:
        if not settings.model_path.is_dir():
            raise RuntimeError(f"Local Whisper model directory not found: {settings.model_path}")
        from faster_whisper import WhisperModel

        self._model = WhisperModel(
            str(settings.model_path),
            device=settings.device,
            compute_type=settings.compute_type,
            local_files_only=True,
        )

    def transcribe(
        self,
        path: Path,
        language: str | None,
        word_timestamps: bool = False,
    ) -> TranscriptionResult:
        segments, info = self._model.transcribe(
            str(path),
            language=language,
            vad_filter=True,
            word_timestamps=word_timestamps,
        )
        duration_millis = max(0, round(float(info.duration) * 1000))
        text_parts: list[str] = []
        words: list[TranscriptionWord] = []
        for segment in segments:
            segment_text = segment.text.strip()
            if segment_text:
                text_parts.append(segment_text)
            if not word_timestamps:
                continue
            for word in segment.words or ():
                word_text = " ".join(str(word.word).split())
                if not word_text:
                    continue
                start_millis = max(
                    0,
                    min(duration_millis, round(float(word.start or 0) * 1000)),
                )
                end_millis = max(
                    start_millis,
                    min(duration_millis, round(float(word.end or word.start or 0) * 1000)),
                )
                words.append(
                    TranscriptionWord(
                        text=word_text,
                        start_millis=start_millis,
                        end_millis=end_millis,
                    )
                )
        return TranscriptionResult(
            text=" ".join(text_parts),
            language=info.language or language or "unknown",
            duration_millis=duration_millis,
            words=tuple(words),
        )
