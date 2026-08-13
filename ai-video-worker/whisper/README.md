# 本地 Whisper Worker

本服务只允许 Java 后端通过回环地址调用。生产环境必须预先准备 CTranslate2 格式的本地模型，运行时不会下载模型。

```powershell
$env:AIVIDEO_WHISPER_INTERNAL_TOKEN='replace-with-a-long-random-secret'
$env:AIVIDEO_WHISPER_MODEL_PATH='D:\models\faster-whisper-large-v3'
uv sync --project ai-video-worker/whisper
uv run --project ai-video-worker/whisper uvicorn aivideo_whisper.app:create_default_app --factory --host 127.0.0.1 --port 18181 --workers 1
```

`GET /health` 仅在模型成功加载后返回 200。转写入口为 `POST /internal/v1/transcriptions`，必须携带与 Java 配置一致的 `X-Internal-Token`。
