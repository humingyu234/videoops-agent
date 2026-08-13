from __future__ import annotations

import asyncio
import hmac
import re
import tempfile
from collections.abc import Callable
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Annotated

from fastapi import FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse

from aivideo_whisper.config import Settings
from aivideo_whisper.transcriber import FasterWhisperTranscriber, Transcriber

ALLOWED_CONTENT_TYPES = {"audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/x-m4a"}


def create_app(
    settings: Settings,
    *,
    transcriber: Transcriber | None = None,
    model_loader: Callable[[Settings], Transcriber | None] | None = None,
) -> FastAPI:
    if settings.max_concurrency != 1:
        raise ValueError("Whisper worker concurrency must remain 1")

    loader = model_loader or FasterWhisperTranscriber

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        if app.state.transcriber is None:
            app.state.transcriber = await run_in_threadpool(loader, settings)
        yield

    app = FastAPI(title="AI Video Local Whisper Worker", lifespan=lifespan)
    app.state.transcriber = transcriber
    app.state.inference_semaphore = asyncio.Semaphore(1)

    @app.get("/health")
    async def health() -> JSONResponse:
        if app.state.transcriber is None:
            return JSONResponse(status_code=503, content={"status": "unavailable"})
        return JSONResponse(
            content={"status": "ok", "modelPath": str(settings.model_path), "concurrency": 1}
        )

    @app.post("/internal/v1/transcriptions")
    async def transcribe(
        file: Annotated[UploadFile, File()],
        request_id: Annotated[str | None, Form(alias="requestId")] = None,
        language: Annotated[str | None, Form(max_length=16)] = None,
        word_timestamps: Annotated[bool, Form(alias="wordTimestamps")] = False,
        internal_token: Annotated[
            str | None, Header(alias="X-Internal-Token")
        ] = None,
    ) -> dict[str, object]:
        if internal_token is None or not hmac.compare_digest(
            internal_token, settings.internal_token
        ):
            raise HTTPException(status_code=401, detail="invalid internal token")
        if request_id is None or not request_id.strip() or len(request_id) > 128:
            raise HTTPException(status_code=422, detail="invalid requestId")
        if file.content_type not in ALLOWED_CONTENT_TYPES:
            raise HTTPException(status_code=415, detail="unsupported audio content type")
        if app.state.transcriber is None:
            raise HTTPException(status_code=503, detail="Whisper model unavailable")

        settings.temp_dir.mkdir(parents=True, exist_ok=True)
        suffix = Path(file.filename or "audio.bin").suffix[:10]
        temp_path: Path | None = None
        try:
            with tempfile.NamedTemporaryFile(
                dir=settings.temp_dir, suffix=suffix, delete=False
            ) as output:
                temp_path = Path(output.name)
                total = 0
                while chunk := await file.read(1024 * 1024):
                    total += len(chunk)
                    if total > settings.max_upload_bytes:
                        raise HTTPException(status_code=413, detail="audio file too large")
                    output.write(chunk)
            if total == 0:
                raise HTTPException(status_code=422, detail="empty audio file")

            async with app.state.inference_semaphore:
                result = await run_in_threadpool(
                    app.state.transcriber.transcribe,
                    temp_path,
                    language,
                    word_timestamps,
                )
            normalized_text = re.sub(r"\s+", " ", result.text).strip()
            if not normalized_text:
                raise HTTPException(status_code=422, detail="empty transcription")
            response: dict[str, object] = {
                "requestId": request_id,
                "text": normalized_text,
                "language": result.language,
                "durationMillis": result.duration_millis,
            }
            if word_timestamps:
                response["words"] = [
                    {
                        "text": word.text,
                        "startMillis": word.start_millis,
                        "endMillis": word.end_millis,
                    }
                    for word in result.words
                ]
            return response
        finally:
            await file.close()
            if temp_path is not None:
                temp_path.unlink(missing_ok=True)

    return app


def create_default_app() -> FastAPI:
    return create_app(Settings.from_env())
