from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True, slots=True)
class Settings:
    internal_token: str
    model_path: Path
    temp_dir: Path
    device: str = "cpu"
    compute_type: str = "int8"
    max_upload_bytes: int = 104_857_600
    max_concurrency: int = 1

    @classmethod
    def from_env(cls) -> Settings:
        token = os.getenv("AIVIDEO_WHISPER_INTERNAL_TOKEN", "").strip()
        if not token:
            raise RuntimeError("AIVIDEO_WHISPER_INTERNAL_TOKEN is required")
        return cls(
            internal_token=token,
            model_path=Path(os.getenv("AIVIDEO_WHISPER_MODEL_PATH", "")).expanduser(),
            temp_dir=Path(
                os.getenv("AIVIDEO_WHISPER_TEMP_DIR", ".aivideo-whisper-tmp")
            ).expanduser(),
            device=os.getenv("AIVIDEO_WHISPER_DEVICE", "cpu"),
            compute_type=os.getenv("AIVIDEO_WHISPER_COMPUTE_TYPE", "int8"),
            max_upload_bytes=int(
                os.getenv("AIVIDEO_WHISPER_MAX_UPLOAD_BYTES", "104857600")
            ),
            max_concurrency=int(os.getenv("AIVIDEO_WHISPER_MAX_CONCURRENCY", "1")),
        )
