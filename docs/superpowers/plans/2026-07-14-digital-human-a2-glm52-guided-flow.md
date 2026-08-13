# 数字人 A2 六步主流程与 GLM-5.2 引导式需求实现计划

> **局部已被替代，禁止执行冲突任务。** 说需求、逐题问卷、分支失效、固定补充、逐次计费和文案候选任务，改为执行 `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md` 及其七份实施包；本文只保留不冲突的其他数字人步骤参考。

> **执行方式：** 本计划批准后优先使用 `subagent-driven-development` 在当前会话按波次执行；若改为独立会话执行，则使用 `executing-plans`，每个波次结束后进行一次契约与回归检查。

**目标：** 在现有 `digital-human-studio` 中完成一个固定桌面端 A2 视觉的六步数字人演示流程，真实调用 GLM-5.2、IndexTTS2、LTX2.3 和 FFmpeg，实现引导式需求收集、台词、临时上传、语义画中画、三种字幕、花字、生成进度、播放与下载。

**架构：** React/Vite/Ant Design 页面只负责交互与状态；本机 FastAPI 演示服务只监听 loopback，负责密钥隔离、模型调用、临时文件、任务快照、单 GPU 排队、合成和媒体输出；远端 ComfyUI 继续承载 IndexTTS2 与 LTX2.3。普通测试全部使用可注入 transport、Comfy stub 和小型真实媒体，真实模型只由显式 gated smoke 触发。

**技术栈：** React 19、TypeScript、Vite、Ant Design、Vitest、Testing Library、Playwright、FastAPI、Pydantic、httpx、pytest、Pillow、FFmpeg/ffprobe、现有 ComfyUI storyboard/job 插件。

**批准规格：** `docs/superpowers/specs/2026-07-14-digital-human-a2-glm52-guided-flow-design.md`

**取代计划：** 本文件取代 `docs/superpowers/plans/2026-07-14-digital-human-demo-main-flow.md`。旧计划只保留历史参考，不再作为执行依据。

---

## 0. 执行边界

本轮是演示主流程，不是生产 RuoYi 模块：

- 不修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`，因为 `/v1/demo/**` 不进入生产公共契约。
- 不接入项目、任务中心、资产、通知、数据库、账号、权限、额度或响应式布局。
- 不修改 `ai-video-api` 和 `ai-video-ui` 的生产业务代码。
- BigModel 密钥与 SSH 共享开发凭据统一写入并提交在两端 `application-dev.yml`；肖像、声音、绝对远端路径和完整台词不得写入代码、fixture、日志和 Git 历史，凭据不得进入日志或 fixture。
- “后端”仅指 `digital-human-studio/apps/api` 的本地执行服务；它让页面安全调用真实模型，不增加用户可见的管理功能。
- API 固定监听 `127.0.0.1:8765`，Web 固定监听 `127.0.0.1:5173`；浏览器不得直接访问 ComfyUI 或 BigModel。

## 1. 并行执行图

```text
Wave 0：任务 1 冻结契约、枚举、共享 fixture 与远端 voice-first 能力门禁 R1
                 │
                 ├──────────────┬──────────────┬──────────────┐
                 ▼              ▼              ▼              ▼
Wave 1：任务 2 GLM      任务 3 临时上传   任务 4 时间轴/合成   任务 5 前端状态/API
                 │              │              │              │
                 └──────────────┴──────┬───────┘              ├──────────┐
                                      ▼                      ▼          ▼
Wave 2：                       任务 8 Job 编排         任务 6 步骤 1–2  任务 7 步骤 3–4
                                      │                      │          │
                                      └──────────────────────┴────┬─────┘
                                                                 ▼
Wave 3：                                                  任务 9 步骤 5–6 与真实 adapter
                                                                 │
                                                                 ▼
Wave 4：                                             任务 10 离线 E2E → 任务 11 真实 smoke
```

并行执行规则：

- Wave 0 必须先完成，因为所有后续任务都引用同一 DTO、状态、错误码和 fixture。
- Wave 1 四个任务可以由四名 Agent 同时开发，文件所有权互不重叠；`routes/demo.py` 统一留到任务 8 串行聚合。
- 任务 6、7 可以在任务 5 后基于 mock 继续，不等待真实模型；二者只产出独立步骤组件，`App.tsx` 统一留到任务 9 串行组装。
- 任务 8 只在任务 2、3、4 的服务接口固定后开始。
- 任务 9 是前后端真实 adapter 的汇合点；合并前分别跑所属测试，避免一次性调试多处变化。
- 任务 10 全绿后才能运行任务 11；真实模型失败不能用 mock 成功结果代替。

每个任务按“写失败测试 → 确认失败原因 → 最小实现 → 定向测试 → 所属回归 → 小提交”执行。任何 DTO 或枚举变更先回到任务 1 更新两端契约测试。

命令执行约定：每个任务开始时工作目录设为 `D:\Workspace\ai\projects\ai-video\digital-human-studio`；因此测试命令直接使用 `uv`/`pnpm`，Git 命令统一通过 `git -C ..` 操作上一级项目仓库，避免相对路径歧义。

---

## 任务 1：冻结演示契约、状态机与共享 fixture

**文件**

- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_contracts.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_state.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_comfy_contract.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_runtime.py`
- 新建：`digital-human-studio/tests/demo/test_demo_contracts.py`
- 新建：`digital-human-studio/tests/demo/test_demo_state.py`
- 新建：`digital-human-studio/tests/demo/conftest.py`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/questionnaire.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/script.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/packaging-advice.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/upload-ref.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/job-running.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/job-done.json`
- 新建：`digital-human-studio/tests/demo/fixtures/contracts/error-responses.json`
- 新建：`digital-human-studio/tools/generate_demo_test_fixtures.py`
- 新建：`digital-human-studio/tools/probe_demo_comfy.py`
- 新建：`digital-human-studio/tests/demo/test_demo_comfy_contract.py`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/portrait-512.png`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/pip-512.png`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/voice-5s.wav`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/base-576x1024-h264-aac.mp4`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/video-only.mp4`
- 新建并生成：`digital-human-studio/tests/demo/fixtures/media/invalid.bin`

### 步骤 1：先写严格 DTO 与状态机失败测试

在 `test_demo_contracts.py` 覆盖：

- 未知字段被拒绝，camelCase 可读写，长度按 Unicode code point 校验。
- 问卷只能有 3–5 题，题型只允许三种，ID 唯一，推荐项属于可见选项。
- `OptimizeScriptRequest` 的 `preset` 和 `instruction` 恰好提供一个。
- `ScriptSegment` 连续、有序、无越界；包装建议锚点属于对应句子。
- 开关关闭时不接受附属字段；开启时校验必填组合。
- `DemoJobDetail.status` 只有四种总状态，`stageCode` 只有冻结阶段。
- 错误响应是 `{ "error": { "code", "message", "requestId", "retryable" } }`，不修改生产 `ErrorEnvelope`。

在 `test_demo_state.py` 覆盖合法流转与终态保护：

```python
def test_terminal_job_cannot_transition_back_to_running() -> None:
    machine = DemoJobStateMachine(status="done", stage_code="done", progress=100)

    with pytest.raises(InvalidDemoTransition):
        machine.transition(status="running", stage_code="composing", progress=90)


def test_progress_is_monotonic_or_unknown() -> None:
    machine = DemoJobStateMachine(status="running", stage_code="voiceGenerating", progress=20)
    machine.transition(status="running", stage_code="avatarGenerating", progress=None)
    with pytest.raises(InvalidDemoTransition):
        machine.transition(status="running", stage_code="composing", progress=19)
```

运行：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\digital-human-studio
uv run pytest tests/demo/test_demo_contracts.py tests/demo/test_demo_state.py -q
```

预期：因模块尚不存在而失败；失败仅来自目标模块/类型缺失。

### 步骤 2：实现冻结基础模型

`demo_contracts.py` 的所有请求与响应继承同一个严格基类：

```python
from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class DemoModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
        frozen=True,
    )


class DemoErrorBody(DemoModel):
    code: str
    message: str
    request_id: str
    retryable: bool


class DemoErrorEnvelope(DemoModel):
    error: DemoErrorBody
```

使用以下完整字段签名定义冻结模型；Python 属性使用 snake_case，JSON alias 使用括号内 camelCase：

- `QuestionnaireField(question_id/questionId: str, label: str, help_text/helpText: str | None, answer_type/answerType: Literal['singleChoice','multiChoice','shortText'], required: bool, options: Sequence[QuestionnaireOption], recommended_option_ids/recommendedOptionIds: Sequence[str], allow_supplement/allowSupplement: Literal[True], supplement_placeholder/supplementPlaceholder: str | None)`。
- `QuestionnaireResult(questionnaire_id/questionnaireId: str, input_hash/inputHash: str, client_revision/clientRevision: int, fields: Sequence[QuestionnaireField])`。
- `QuestionnaireAnswer(question_id/questionId: str, selected_option_ids/selectedOptionIds: Sequence[str], text_value/textValue: str, supplement_text/supplementText: str)`。
- `ScriptSegment(sentence_id/sentenceId: str, text: str, start_char/startChar: int, end_char/endChar: int)`。
- `ScriptResult(script_id/scriptId: str, revision: int, source_hash/sourceHash: str, client_revision/clientRevision: int, script_text/scriptText: str, estimated_duration_seconds/estimatedDurationSeconds: float, segments: Sequence[ScriptSegment])`。
- `PipRecommendation(recommendation_id/recommendationId: str, sentence_id/sentenceId: str, start_char/startChar: int, end_char/endChar: int, reason: str, material_kind/materialKind: Literal['product','scene','diagram','proof','other'], material_hint/materialHint: str, estimated_start_ratio/estimatedStartRatio: float, estimated_end_ratio/estimatedEndRatio: float, highlight_keywords/highlightKeywords: Sequence[str])`。
- `FlowerSuggestion(suggestion_id/suggestionId: str, sentence_id/sentenceId: str, start_char/startChar: int, end_char/endChar: int, text: str, style_id/styleId: Literal['goldBold','blueLabel','whiteShadow'])`。
- `PackagingAdvice(advice_id/adviceId: str, script_source_hash/scriptSourceHash: str, client_revision/clientRevision: int, pip_recommendations/pipRecommendations: Sequence[PipRecommendation], flower_suggestions/flowerSuggestions: Sequence[FlowerSuggestion])`。
- `CreateQuestionnaireRequest(industry: str, purpose: str, client_revision/clientRevision: int)`。
- `CreateScriptRequest(questionnaire_id/questionnaireId: str, answers: Sequence[QuestionnaireAnswer], additional_notes/additionalNotes: str, client_revision/clientRevision: int)`。

所有 `Sequence` 字段在 `mode='before'` validator 中规范化为 tuple，保证内部快照不可变；JSON 序列化仍输出数组。
- `OptimizeScriptRequest(script_id/scriptId: str, expected_revision/expectedRevision: int, script_text/scriptText: str, preset: OptimizePreset | None, instruction: str | None, client_revision/clientRevision: int)`。
- `AnalyzePackagingRequest(base_script_id/baseScriptId: str, base_revision/baseRevision: int, script_text/scriptText: str, client_revision/clientRevision: int)`。
- `DemoUploadRef(upload_id/uploadId: str, biz_type/bizType: Literal['portrait','voiceReference','pip'], original_file_name/originalFileName: str, size_bytes/sizeBytes: int, mime_type/mimeType: str, width: int | None, height: int | None, duration_seconds/durationSeconds: float | None, expires_at/expiresAt: datetime)`。
- `CreateDemoJobRequest(idempotency_key/idempotencyKey: str, packaging_advice_id/packagingAdviceId: str, portrait_upload_id/portraitUploadId: str, voice_upload_id/voiceUploadId: str, pip: PipConfig, subtitle: SubtitleConfig, flower: FlowerConfig)` 与 `RetryDemoJobRequest(idempotency_key/idempotencyKey: str)`。
- `DemoArtifact(artifact_id/artifactId: str, video_url/videoUrl: str, download_url/downloadUrl: str, file_name/fileName: str, mime_type/mimeType: Literal['video/mp4'], size_bytes/sizeBytes: int, sha256: str, width: int, height: int, duration_seconds/durationSeconds: float, video_codec/videoCodec: Literal['h264'], audio_codec/audioCodec: Literal['aac'])`。
- `DemoJobDetail(job_id/jobId: str, parent_job_id/parentJobId: str | None, status: DemoJobStatus, stage_code/stageCode: DemoStageCode, progress: float | None, failed_stage/failedStage: DemoNonTerminalStage | None, error_code/errorCode: str | None, user_message/userMessage: str | None, retryable: bool, input_snapshot/inputSnapshot: DemoInputSnapshot, artifact: DemoArtifact | None, actual_models/actualModels: ActualModels | None, created_at/createdAt: datetime, updated_at/updatedAt: datetime, expires_at/expiresAt: datetime)`。

`tests/demo/conftest.py` 提供 `contract_fixture(name)`、`frozen_clock`、`demo_test_app(runtime)`、`valid_job_payload` 四个测试 fixture；它们只读取任务 1 的合成 JSON/媒体，不访问网络，也不包含真实密钥或媒体。

`demo_state.py` 使用一张显式流转表，而不是字符串比较：

```python
ALLOWED_STAGE_TRANSITIONS = {
    "queued": {"voiceGenerating", "failed"},
    "voiceGenerating": {"avatarGenerating", "failed"},
    "avatarGenerating": {"timingCalibrating", "failed"},
    "timingCalibrating": {"composing", "failed"},
    "composing": {"validatingOutput", "failed"},
    "validatingOutput": {"done", "failed"},
    "done": set(),
    "failed": set(),
}
```

`demo_runtime.py` 在 Wave 0 只提供并行路由共用的 app-state seam；任务 8 在同一文件扩展真实生命周期，不改函数签名：

```python
def require_demo_runtime(request: Request) -> Any:
    runtime = getattr(request.app.state, "demo_runtime", None)
    if runtime is None:
        raise DemoServiceError("DEMO_RUNTIME_UNAVAILABLE", retryable=True)
    return runtime
```

任务 2、3 的最小测试应用都向 `app.state.demo_runtime` 注入各自需要的 service namespace，因此它们不依赖任务 8 的完整 runtime。

### 步骤 3：建立共享 JSON fixture

fixture 必须完整通过 Pydantic 校验。前端测试从同一目录读取 JSON，不复制第二份响应样例。`job-done.json` 的 `videoUrl` 与 `downloadUrl` 都指向同一 job 媒体端点，后者只增加 `?download=1`。

媒体生成脚本只生成确定性测试素材：纯色 PNG、5 秒无敏感内容 WAV、带 H.264/AAC 的 576×1024 MP4、仅视频 MP4 和非法字节。脚本提供 `--check`，重新生成到临时目录后逐项验证媒体属性；对有容器时间戳的 MP4 比较 ffprobe 结构而不是强制二进制 hash。

运行：

```powershell
uv run python tools/generate_demo_test_fixtures.py
uv run python tools/generate_demo_test_fixtures.py --check
uv run pytest tests/demo/test_demo_contracts.py tests/demo/test_demo_state.py -q
```

预期：fixture 校验通过；MP4 明确包含 H.264 和 AAC，`video-only.mp4` 明确没有音频流。

### 步骤 4：前置冻结 Comfy voice-first 能力契约

Job 编排不能等到最终 smoke 才发现远端联合接口缺少真实音频时长。先在 `demo_comfy_contract.py` 冻结以下响应：

```python
class DemoComfyCapabilities(DemoModel):
    contract_version: Literal["voice-first-v1"]
    voice_first_timeline: Literal[True]
    voice_engine: Literal["IndexTTS2"]
    avatar_engine: Literal["LTX2.3"]
    exposes_voice_duration_seconds: Literal[True]
    exposes_voice_download_url: Literal[True]
    exposes_actual_model_ids: Literal[True]


class DemoComfyVoiceOutput(DemoModel):
    duration_seconds: Annotated[float, Field(gt=0, le=600)]
    download_url: str
    sha256: Annotated[str, Field(pattern=r"^[0-9a-f]{64}$")]
```

远端 job 必须先完成 IndexTTS2，ffprobe 该输出并填入 `voiceOutput`，之后才按 `durationSeconds` 计算 LTX2.3 帧数；job detail 同时返回 `actualModels.voice` 和 `actualModels.avatar`。本地只接受 allowlist 内的 `voiceOutput.downloadUrl`。

先写完整客户端红灯测试：

```python
def test_capability_probe_requires_voice_first_contract() -> None:
    response = {
        "contractVersion": "voice-first-v1",
        "voiceFirstTimeline": True,
        "voiceEngine": "IndexTTS2",
        "avatarEngine": "LTX2.3",
        "exposesVoiceDurationSeconds": True,
        "exposesVoiceDownloadUrl": True,
        "exposesActualModelIds": True,
    }

    parsed = DemoComfyCapabilities.model_validate(response)

    assert parsed.voice_first_timeline is True
    assert parsed.exposes_voice_duration_seconds is True
    with pytest.raises(ValidationError):
        DemoComfyCapabilities.model_validate({**response, "voiceFirstTimeline": False})
```

`probe_demo_comfy.py` 对显式 `DEMO_COMFY_BASE_URL` 调用 `GET /ai-video/ltx23/capabilities`，再对不存在的 job ID 调用 job 查询确认插件路由；它只输出脱敏能力 JSON，不提交生成任务。先运行离线测试，再运行真实只读门禁：

```powershell
uv run pytest tests/demo/test_demo_comfy_contract.py -q
uv run python tools/probe_demo_comfy.py --require-contract voice-first-v1 --output .tmp/demo-comfy-capabilities.json
```

预期：离线测试退出码 0；真实 probe 只有在上述七项能力全部为真时退出码 0。`.tmp/demo-comfy-capabilities.json` 不进入 Git。

#### 远端契约缺失时的固定扩展路径（Gate R1）

如果真实 probe 失败，Wave 1 的前端/GLM/上传/合成可继续，但任务 8 不开始。远端契约 owner 执行以下明确工作：

- 从已部署 `/opt/ComfyUI/custom_nodes/ai_video_ltx23_storyboard/routes.py` 取得不含凭据的源文件，保存为 `digital-human-studio/integrations/comfyui/ai_video_ltx23_storyboard/routes.py`。
- 新建 `digital-human-studio/tests/demo/test_demo_comfy_extension.py`，以 fake IndexTTS2、fake ffprobe、fake LTX runner 断言调用顺序严格为 `tts -> ffprobe voice -> ltx`，并断言 job JSON 含 `voiceOutput.durationSeconds/downloadUrl/sha256` 与实际模型 ID。
- 在该 routes 文件增加 `GET /ai-video/ltx23/capabilities`，返回上面的 `voice-first-v1` 固定结构；把 storyboard job 改为在构建 LTX 场景前完成 TTS 与 ffprobe。
- 部署前确认远端 `/queue` 没有运行中的共享任务；先在服务器执行 `python3 -m py_compile`，再按当前 Comfy 管理方式做受控重载。不得在队列有任务时重启服务。

本地测试与无凭据部署命令：

```powershell
uv run pytest tests/demo/test_demo_comfy_extension.py tests/demo/test_demo_comfy_contract.py -q
scp -P $env:DEMO_SSH_PORT integrations/comfyui/ai_video_ltx23_storyboard/routes.py "$env:DEMO_SSH_USER@$env:DEMO_SSH_HOST`:/opt/ComfyUI/custom_nodes/ai_video_ltx23_storyboard/routes.py"
ssh -p $env:DEMO_SSH_PORT "$env:DEMO_SSH_USER@$env:DEMO_SSH_HOST" "python3 -m py_compile /opt/ComfyUI/custom_nodes/ai_video_ltx23_storyboard/routes.py"
uv run python tools/probe_demo_comfy.py --require-contract voice-first-v1 --output .tmp/demo-comfy-capabilities.json
git -C .. add digital-human-studio/integrations/comfyui/ai_video_ltx23_storyboard/routes.py digital-human-studio/tests/demo/test_demo_comfy_extension.py
git -C .. commit -m "feat(comfy): 暴露 voice-first 数字人能力契约"
```

只有最后一次 probe 退出码为 0，Gate R1 才通过。部署凭据默认读取两端 `application-dev.yml`，环境变量可选覆盖；不得写入命令参数或日志。

### 步骤 5：提交契约基线

```powershell
git -C .. add digital-human-studio/apps/api/src/dh_api/services/demo_contracts.py digital-human-studio/apps/api/src/dh_api/services/demo_state.py digital-human-studio/apps/api/src/dh_api/services/demo_comfy_contract.py digital-human-studio/apps/api/src/dh_api/services/demo_runtime.py digital-human-studio/tests/demo/conftest.py digital-human-studio/tests/demo/test_demo_contracts.py digital-human-studio/tests/demo/test_demo_state.py digital-human-studio/tests/demo/test_demo_comfy_contract.py digital-human-studio/tests/demo/fixtures digital-human-studio/tools/generate_demo_test_fixtures.py digital-human-studio/tools/probe_demo_comfy.py
git -C .. commit -m "test(demo): 冻结六步流程契约与媒体基线"
```

---

## 任务 2：实现 GLM-5.2 严格客户端、进程内快照与四类文本接口

**依赖：** 任务 1

**文件**

- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_bigmodel.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_ai_store.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/routes/demo_ai.py`
- 新建：`digital-human-studio/tests/demo/test_demo_bigmodel.py`
- 新建：`digital-human-studio/tests/demo/test_demo_ai_api.py`

### 步骤 1：写客户端与 API 失败测试

先实现下面这个完整红灯用例；它固定 URL、鉴权只在服务端、JSON mode、模型代码和 schema 解析：

```python
@pytest.mark.asyncio
async def test_questionnaire_uses_json_mode_and_server_only_authorization(
    contract_fixture,
) -> None:
    frozen = contract_fixture("questionnaire.json")
    captured: dict[str, object] = {}

    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        captured["authorization"] = request.headers.get("authorization")
        captured["body"] = json.loads(request.content.decode("utf-8"))
        return httpx.Response(
            200,
            json={
                "id": "glm-request-1",
                "model": "glm-5.2",
                "choices": [{"message": {"content": json.dumps(frozen, ensure_ascii=False)}}],
                "usage": {"prompt_tokens": 20, "completion_tokens": 40, "total_tokens": 60},
            },
        )

    settings = BigModelSettings(
        base_url="https://open.bigmodel.cn/api/paas/v4",
        model="glm-5.2",
        timeout_seconds=60,
        api_key=SecretStr("unit-test-key"),
    )
    client = BigModelClient(settings=settings, transport=httpx.MockTransport(handler))
    result = await client.create_questionnaire(
        CreateQuestionnaireRequest(industry="电商零售", purpose="产品口播", clientRevision=7)
    )

    assert captured["url"] == "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    assert captured["authorization"] == "Bearer unit-test-key"
    body = cast(dict[str, object], captured["body"])
    assert body["model"] == "glm-5.2"
    assert body["response_format"] == {"type": "json_object"}
    assert result.client_revision == 7
    assert len(result.fields) in range(3, 6)
```

随后增加并逐个运行这些测试：`test_text_model_timeout_is_retryable_and_preserves_no_raw_body`、`test_text_model_auth_failure_maps_to_configuration_error`、`test_text_model_rejects_markdown_invalid_json_and_unknown_fields`、`test_script_generation_rejects_answers_not_in_questionnaire`、`test_packaging_advice_is_reused_for_same_script_revision`、`test_stale_client_revision_response_is_not_persisted`。每个测试都使用明确的 MockTransport 响应并断言稳定错误码与 `retryable`。

API 测试覆盖四条路径和嵌套错误 envelope。扫描响应与日志，确认不含 `Authorization`、完整 prompt 或上游原始响应。

运行：

```powershell
uv run pytest tests/demo/test_demo_bigmodel.py tests/demo/test_demo_ai_api.py -q
```

预期：新模块和路由缺失导致失败。

### 步骤 2：实现受限配置和 GLM 客户端

配置只从服务端环境读取：

```python
@dataclass(frozen=True)
class BigModelSettings:
    base_url: str
    model: str
    timeout_seconds: float
    api_key: SecretStr

    @classmethod
    def from_env(cls) -> "BigModelSettings":
        key = os.environ.get("BIGMODEL_API_KEY")
        if not key:
            raise DemoServiceError("TEXT_MODEL_CONFIGURATION_ERROR", retryable=False)
        base_url = os.getenv("BIGMODEL_BASE_URL", "https://open.bigmodel.cn/api/paas/v4")
        require_allowed_https_url(base_url, allowed_hosts={"open.bigmodel.cn"})
        return cls(
            base_url=base_url.rstrip("/"),
            model=os.getenv("BIGMODEL_MODEL", "glm-5.2"),
            timeout_seconds=float(os.getenv("BIGMODEL_TIMEOUT_SECONDS", "60")),
            api_key=SecretStr(key),
        )
```

调用固定为 `POST {base_url}/chat/completions`，包含 `response_format={"type":"json_object"}`，提示中写入完整 JSON schema。上游内容先 `json.loads`，再由对应 Pydantic DTO 校验。禁止剥离 Markdown 围栏后继续成功；出现围栏就映射 `TEXT_MODEL_INVALID_RESPONSE`。

仅记录调用类型、request ID、状态码、耗时、token 用量、实际 model 和脱敏错误分类。401/403 映射配置错误，timeout 映射超时，其余网络/限流/5xx 映射 unavailable，解析/schema 失败映射 invalid response。

### 步骤 3：实现带 revision/hash 的 AI 快照 store

`demo_ai_store.py` 只在当前进程保留校验后的问卷、台词和包装建议：

```python
@dataclass(frozen=True)
class StoredScript:
    result: ScriptResult
    questionnaire_id: str
    input_hash: str
    last_accessed_at: datetime


def canonical_hash(payload: DemoModel) -> str:
    value = payload.model_dump_json(by_alias=True, exclude_none=True)
    return hashlib.sha256(value.encode("utf-8")).hexdigest()
```

- 生成台词前验证 `questionnaireId`、问题集合、选项集合和必填答案。
- 优化前验证 `scriptId + expectedRevision`；成功后 revision 加 1，失败不覆盖上一版。
- 包装分析绑定 `scriptId + revision + scriptText hash`；相同输入返回已有 advice，不重复调用模型。
- 所有条目最后访问 2 小时过期；被 job 引用后由任务生命周期延长。

### 步骤 4：实现四条路由并挂入聚合 router

`demo_ai.py` 通过一个统一的 runtime accessor 提供四条薄路由；路由只做输入/输出绑定，不写模型业务：

```python
ai_router = APIRouter(prefix="/ai", tags=["demo-ai"])

@ai_router.post("/questionnaires", response_model=QuestionnaireResult)
async def create_questionnaire(
    payload: CreateQuestionnaireRequest, request: Request
) -> QuestionnaireResult:
    return await require_demo_runtime(request).ai.create_questionnaire(payload)

@ai_router.post("/scripts", response_model=ScriptResult)
async def create_script(payload: CreateScriptRequest, request: Request) -> ScriptResult:
    return await require_demo_runtime(request).ai.create_script(payload)

@ai_router.post("/scripts/optimize", response_model=ScriptResult)
async def optimize_script(
    payload: OptimizeScriptRequest, request: Request
) -> ScriptResult:
    return await require_demo_runtime(request).ai.optimize_script(payload)

@ai_router.post("/packaging-advice", response_model=PackagingAdvice)
async def analyze_packaging(
    payload: AnalyzePackagingRequest, request: Request
) -> PackagingAdvice:
    return await require_demo_runtime(request).ai.analyze_packaging(payload)
```

任务 2 不修改聚合 `routes/demo.py`。`test_demo_ai_api.py` 创建最小 FastAPI 测试应用并直接 `include_router(ai_router, prefix="/v1/demo")`；正式聚合由任务 8 一次完成，避免与上传轨道并行写冲突。页面组件不拼 URL。

### 步骤 5：验证并提交

```powershell
uv run pytest tests/demo/test_demo_contracts.py tests/demo/test_demo_bigmodel.py tests/demo/test_demo_ai_api.py -q
uv run ruff check apps/api/src/dh_api/services/demo_bigmodel.py apps/api/src/dh_api/services/demo_ai_store.py apps/api/src/dh_api/routes/demo_ai.py tests/demo/test_demo_bigmodel.py tests/demo/test_demo_ai_api.py
uv run pyright apps/api/src/dh_api/services/demo_bigmodel.py apps/api/src/dh_api/services/demo_ai_store.py apps/api/src/dh_api/routes/demo_ai.py
git -C .. add digital-human-studio/apps/api/src/dh_api/services/demo_bigmodel.py digital-human-studio/apps/api/src/dh_api/services/demo_ai_store.py digital-human-studio/apps/api/src/dh_api/routes/demo_ai.py digital-human-studio/tests/demo/test_demo_bigmodel.py digital-human-studio/tests/demo/test_demo_ai_api.py
git -C .. commit -m "feat(demo): 接入 GLM-5.2 引导与台词接口"
```

---

## 任务 3：实现安全临时上传、媒体校验和上传 TTL

**依赖：** 任务 1

**文件**

- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_uploads.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/routes/demo_uploads.py`
- 新建：`digital-human-studio/tests/demo/test_demo_uploads.py`
- 新建：`digital-human-studio/tests/demo/support/__init__.py`
- 新建：`digital-human-studio/tests/demo/support/upload_fixtures.py`
- 修改：`digital-human-studio/apps/api/pyproject.toml`
- 修改：`digital-human-studio/uv.lock`

### 步骤 1：写文件攻击面失败测试

测试使用任务 1 的真实媒体 fixture，覆盖：

- portrait/pip JPEG、PNG 文件头、真实解码、尺寸、空文件、15 MiB 上限。
- voice WAV/MP3 解码、5–20 秒、16–48 kHz、1–2 声道、50 MiB 上限。
- MIME 伪装、图片解码炸弹、超长音频、路径穿越名、符号链接和原文件名不进入服务端路径。
- 替换策略是“新文件成功后再切换”；未被引用可删，被 job 引用返回 409。
- 未关联上传 30 分钟过期，过期返回 410 / `UPLOAD_EXPIRED`。

先写并运行下面的完整 API 红灯用例，固定 multipart 字段、响应元数据和服务端生成路径：

```python
def test_portrait_upload_is_decoded_and_stored_under_server_generated_id(
    demo_upload_client,
) -> None:
    client, upload_service, media_dir = demo_upload_client
    payload = (media_dir / "portrait-512.png").read_bytes()

    response = client.post(
        "/v1/demo/uploads",
        data={"bizType": "portrait"},
        files={"file": ("../../private-face.png", payload, "image/png")},
    )

    assert response.status_code == 201
    body = response.json()
    assert body["bizType"] == "portrait"
    assert body["width"] == 512
    assert body["height"] == 512
    assert body["sizeBytes"] == len(payload)
    stored = upload_service.require_path(body["uploadId"]).resolve()
    assert stored.is_relative_to(upload_service.root.resolve())
    assert "private-face" not in stored.name
    assert not stored.is_symlink()
```

随后增加：`test_upload_rejects_mime_spoof_oversize_and_empty_file`、`test_image_dimensions_are_taken_from_decoder_not_headers`、`test_voice_duration_and_stream_properties_come_from_ffprobe`、`test_storage_path_rejects_symlink_escape`、`test_referenced_upload_cannot_be_deleted`、`test_unreferenced_upload_returns_410_after_thirty_minutes`。`demo_upload_client` 定义在本任务独占的 `tests/demo/support/upload_fixtures.py`，返回最小 FastAPI app、注入的 service 和任务 1 媒体目录；`test_demo_uploads.py` 显式导入该 fixture，避免并行修改公共 conftest。

运行：

```powershell
uv run pytest tests/demo/test_demo_uploads.py -q
```

预期：上传服务和路由缺失而失败。

### 步骤 2：加入 Pillow 并实现校验器

在 API 依赖中加入 Pillow，运行 `uv lock` 更新锁文件。图片先按字节上限流式写入受控临时文件，再由 Pillow 解码和 `verify()`；设置像素上限并把 decompression bomb 当作拒绝。音频用参数数组调用 ffprobe：

```python
completed = subprocess.run(
    [
        "ffprobe", "-v", "error", "-show_streams", "-show_format",
        "-of", "json", str(candidate_path),
    ],
    check=True,
    capture_output=True,
    text=True,
    timeout=30,
    shell=False,
)
```

把参考声音转成模型要求的 WAV 时也使用参数数组，输出到同一上传目录内的服务端生成文件名。任何命令、异常或日志都不包含原文件名和绝对路径。

### 步骤 3：实现上传 store 与路由

存储根目录由 `DEMO_TEMP_ROOT` 配置，默认使用当前进程专属系统临时目录。每个上传目录使用随机内部 ID；解析后必须确认 `resolved_path.is_relative_to(root)` 且不是符号链接。

路由：

```python
upload_router = APIRouter(prefix="/uploads", tags=["demo-uploads"])

@upload_router.post("", response_model=DemoUploadRef, status_code=201)
async def create_upload(
    file: UploadFile,
    biz_type: Annotated[DemoUploadBizType, Form(alias="bizType")],
    request: Request,
) -> DemoUploadRef:
    return await require_demo_runtime(request).uploads.create(file=file, biz_type=biz_type)

@upload_router.delete("/{upload_id}", status_code=204)
async def delete_upload(upload_id: str, request: Request) -> Response:
    await require_demo_runtime(request).uploads.delete(upload_id)
    return Response(status_code=204)
```

FastAPI 413/415/422 和服务错误都映射为演示嵌套错误 envelope。上传响应保留 `originalFileName` 仅用于当次页面展示，日志不记录它。

任务 3 不修改聚合 `routes/demo.py`。上传 API 测试直接把 `upload_router` 挂到最小测试应用的 `/v1/demo` 前缀；任务 8 再集中挂入正式路由。

### 步骤 4：验证并提交

```powershell
uv lock
uv run pytest tests/demo/test_demo_uploads.py -q
uv run ruff check apps/api/src/dh_api/services/demo_uploads.py apps/api/src/dh_api/routes/demo_uploads.py tests/demo/test_demo_uploads.py
uv run pyright apps/api/src/dh_api/services/demo_uploads.py apps/api/src/dh_api/routes/demo_uploads.py
git -C .. add digital-human-studio/apps/api/pyproject.toml digital-human-studio/uv.lock digital-human-studio/apps/api/src/dh_api/services/demo_uploads.py digital-human-studio/apps/api/src/dh_api/routes/demo_uploads.py digital-human-studio/tests/demo/support/__init__.py digital-human-studio/tests/demo/support/upload_fixtures.py digital-human-studio/tests/demo/test_demo_uploads.py
git -C .. commit -m "feat(demo): 增加临时媒体上传与真实校验"
```

---

## 任务 4：实现真实音频时间轴、三种字幕、花字、画中画和 artifact 校验

**依赖：** 任务 1

**文件**

- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_timeline.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_artifacts.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_composer.py`
- 新建：`digital-human-studio/tests/demo/test_demo_timeline.py`
- 新建：`digital-human-studio/tests/demo/test_demo_artifacts.py`
- 修改：`digital-human-studio/tests/demo/test_demo_composer.py`

### 步骤 1：写时间轴和恶意文本失败测试

先用纯函数红灯固定 Unicode code point、空白零权重和 PIP 最少 1 秒扩展：

```python
def test_unicode_weighted_anchor_and_minimum_pip_interval() -> None:
    script = "你 好"

    assert offset_to_seconds(script, offset=0, audio_duration=4.0) == 0.0
    assert offset_to_seconds(script, offset=2, audio_duration=4.0) == 2.0
    assert offset_to_seconds(script, offset=3, audio_duration=4.0) == 4.0
    assert normalize_pip_interval(start=2.0, end=2.4, duration=4.0) == pytest.approx(
        (1.7, 2.7)
    )
```

随后增加：`test_offsets_are_half_open_and_reject_out_of_bounds`、`test_short_flower_interval_stays_inside_sentence`、`test_keyword_highlight_uses_only_verified_sentence_keywords`、`test_ass_escapes_braces_backslashes_newlines_and_override_tags`、`test_clean_keyword_and_sentence_pop_generate_distinct_events`。每项断言完整的事件起止时间、ASS 文本或稳定异常类型。

artifact 测试覆盖 H.264、AAC、9:16、有限正时长、音视频差不超过 0.25 秒、SHA-256 和无音频拒绝。

运行：

```powershell
uv run pytest tests/demo/test_demo_timeline.py tests/demo/test_demo_composer.py tests/demo/test_demo_artifacts.py -q
```

预期：时间轴和 artifact 模块缺失，现有 composer 不能满足三种预设而失败。

### 步骤 2：实现 canonical timeline

`demo_timeline.py` 只接受 ffprobe 得到的 `audio_duration`：

```python
def offset_to_seconds(script_text: str, offset: int, audio_duration: float) -> float:
    codepoints = list(script_text)
    if offset < 0 or offset > len(codepoints):
        raise InvalidTimelineAnchor("offset outside script")
    weights = [0 if char.isspace() else 1 for char in codepoints]
    total = sum(weights)
    if total == 0:
        raise InvalidTimelineAnchor("script has no spoken characters")
    return audio_duration * sum(weights[:offset]) / total
```

- 句子、PIP、花字、字幕共用同一映射函数。
- PIP 区间不足 1 秒时围绕中心扩展，且裁在 `[0, audioDuration]`。
- 花字通常 1–3 秒但不得越过句子；整句不足 1 秒时允许短于 1 秒。
- `keywordHighlight` 最多取 3 个经二次验证、真实存在于锚定句子的关键词。

### 步骤 3：扩展 composer

把现有固定 clean 字幕和前三秒花字改为结构化 `ComposeRequest`。FFmpeg filter graph 由受控 token 组装，用户/模型文本只写入独立 ASS 文件，不拼入 shell 命令。

三种字幕行为：

- `clean`：底部安全区、白字描边、逐句显示。
- `keywordHighlight`：相同句子时间段内用 ASS 分段样式突出已验证关键词。
- `sentencePop`：逐句使用短 `\fad` 和受控缩放动画。

画中画使用实际时间区间的 `enable='between(t,start,end)'`，对上传图做受控 scale/pad；花字使用固定 `goldBold`、`blueLabel`、`whiteShadow` 样式映射。

基础视频短于音频时 `tpad=stop_mode=clone`，长于音频时裁切；音频是唯一 canonical duration。最终统一编码 H.264/AAC MP4。

composer 同时返回仅供服务端内部使用的 `RenderManifest`：基础视频路径、最终视频路径，以及 PIP/字幕/花字各自的实际起止秒数和固定画面区域；manifest 不含完整台词、原文件名或用户绝对路径。任务 11 的视觉层验证使用它抽取同一时刻的 base/final 帧，不能只检查 job 配置。

### 步骤 4：实现 artifact 探测与同源元数据

`demo_artifacts.py` 对最终文件重新运行 ffprobe，校验通过后才返回：

```python
DemoArtifact(
    artifact_id=artifact_id,
    video_url=f"/v1/demo/jobs/{job_id}/video",
    download_url=f"/v1/demo/jobs/{job_id}/video?download=1",
    file_name=f"digital-human-{job_id}.mp4",
    mime_type="video/mp4",
    size_bytes=path.stat().st_size,
    sha256=sha256_file(path),
    width=video.width,
    height=video.height,
    duration_seconds=canonical_duration,
    video_codec="h264",
    audio_codec="aac",
)
```

任一验证失败映射 `OUTPUT_INVALID`，不能保存成功 artifact。

### 步骤 5：验证并提交

```powershell
uv run pytest tests/demo/test_demo_timeline.py tests/demo/test_demo_composer.py tests/demo/test_demo_artifacts.py -q
uv run ruff check apps/api/src/dh_api/services/demo_timeline.py apps/api/src/dh_api/services/demo_composer.py apps/api/src/dh_api/services/demo_artifacts.py tests/demo/test_demo_timeline.py tests/demo/test_demo_composer.py tests/demo/test_demo_artifacts.py
uv run pyright apps/api/src/dh_api/services/demo_timeline.py apps/api/src/dh_api/services/demo_composer.py apps/api/src/dh_api/services/demo_artifacts.py
git -C .. add digital-human-studio/apps/api/src/dh_api/services/demo_timeline.py digital-human-studio/apps/api/src/dh_api/services/demo_composer.py digital-human-studio/apps/api/src/dh_api/services/demo_artifacts.py digital-human-studio/tests/demo/test_demo_timeline.py digital-human-studio/tests/demo/test_demo_composer.py digital-human-studio/tests/demo/test_demo_artifacts.py
git -C .. commit -m "feat(demo): 按真实音频时间轴合成画面包装"
```

---

## 任务 5：实现前端契约 adapter、草稿 reducer、失效矩阵和轮询 hook

**依赖：** 任务 1

**文件**

- 修改：`digital-human-studio/apps/web/src/demo/types.ts`
- 修改：`digital-human-studio/apps/web/src/demo/api.ts`
- 修改：`digital-human-studio/apps/web/src/demo/api.test.ts`
- 修改：`digital-human-studio/apps/web/src/demo/script.ts`
- 修改：`digital-human-studio/apps/web/src/demo/script.test.ts`
- 新建：`digital-human-studio/apps/web/src/demo/flow.ts`
- 新建：`digital-human-studio/apps/web/src/demo/flow.test.ts`
- 新建：`digital-human-studio/apps/web/src/demo/hooks/useJobPolling.ts`
- 新建：`digital-human-studio/apps/web/src/demo/hooks/useJobPolling.test.ts`
- 新建：`digital-human-studio/apps/web/src/test/demoFixtures.ts`
- 新建：`digital-human-studio/apps/web/src/test/msw/handlers.ts`
- 新建：`digital-human-studio/apps/web/src/test/msw/server.ts`
- 修改：`digital-human-studio/apps/web/src/test/setup.ts`

### 步骤 1：先写 adapter 与 reducer 失败测试

前端契约测试读取任务 1 的 canonical JSON fixture，覆盖：

- 四类 GLM 请求序列化，包括每题 `supplementText` 和全局 `additionalNotes`。
- 上传 multipart 的 `file`、`bizType` 与进度。
- Job 只提交服务端 `uploadId`，不重新传浏览器 `File`。
- 只提交当前 enabled 的 PIP/字幕/花字字段。
- 稳定错误码集中映射，页面不按中文消息判断。
- 播放与下载 URL 来自同一 artifact。

先写一个完整 reducer 红灯用例固定行业变化的失效边界：

```ts
it('industry change invalidates semantic downstream but preserves uploads', () => {
  const before = readyDraft({
    industry: '电商零售',
    purpose: '产品口播',
    portraitUpload: portraitUploadRef,
    voiceReferenceUpload: voiceUploadRef,
    questionnaire,
    script,
    packagingAdvice,
  });

  const after = demoFlowReducer(before, {
    type: 'industryCommitted',
    industry: '教育培训',
  });

  expect(after.industry).toBe('教育培训');
  expect(after.purpose).toBe('');
  expect(after.questionnaire).toBeNull();
  expect(after.script).toBeNull();
  expect(after.packagingAdvice).toBeNull();
  expect(after.portraitUpload).toEqual(portraitUploadRef);
  expect(after.voiceReferenceUpload).toEqual(voiceUploadRef);
  expect(after.draftRevision).toBe(before.draftRevision + 1);
});
```

随后增加：`questionnaire answer change invalidates script confirmation and packaging advice`、`portrait replacement preserves script and packaging but detaches previous job snapshot`、`ignores an async response with a stale draft revision`、`keeps the last completed job immutable while the next draft changes`、`derives exactly one enabled primary action for each step`。每个用例使用 `readyDraft` 的完整可校验对象，并断言全部受影响/不受影响字段。

轮询 hook 测试 fake timers：2 秒一次、终态停止、jobId 切换清理、单次失败不改 job 状态、3 次警告、5 次停止并显示重新查询、404/410 进入失效。

运行：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\digital-human-studio
pnpm --filter @studio/web test -- src/demo/api.test.ts src/demo/flow.test.ts src/demo/hooks/useJobPolling.test.ts
```

预期：新类型、reducer 和 hook 缺失而失败。

### 步骤 2：实现 TypeScript DTO 与集中 API adapter

`types.ts` 与批准规格逐字段一致，不把页面 `File` 混进服务端 DTO。页面草稿使用：

```ts
export interface DemoDraft {
  draftRevision: number;
  industry: string;
  purpose: string;
  questionnaire: QuestionnaireResult | null;
  questionnaireAnswers: QuestionnaireAnswer[];
  additionalNotes: string;
  script: ScriptResult | null;
  scriptText: string;
  scriptConfirmed: boolean;
  portraitUpload: DemoUploadRef | null;
  voiceReferenceUpload: DemoUploadRef | null;
  packagingAdvice: PackagingAdvice | null;
  pipUpload: DemoUploadRef | null;
  packaging: PackagingConfig;
  currentJobId: string | null;
  latestJobSnapshot: DemoJobDetail | null;
}
```

`api.ts` 只暴露语义方法：`createQuestionnaire`、`createScript`、`optimizeScript`、`analyzePackaging`、`upload`、`deleteUpload`、`createJob`、`retryJob`、`getJob`、`downloadArtifact`。组件不得出现 `/v1/demo` 字符串。

下载使用 fetch 获取 blob，验证响应和 `Content-Type` 后再创建临时 object URL；失败抛集中错误，不改变 done job。

### 步骤 3：实现 reducer、revision 和取消规则

每个会影响下游的 action 原子增加 `draftRevision` 并清理精确依赖。异步 action 必须带 `requestRevision`，reducer 只接受与当前值相等的成功结果。组件层为每类 GLM 请求保存 `AbortController`，离开页面或发起新请求时取消旧请求。

`script.ts` 删除本地通用台词和任何模型失败回退，只保留 Unicode 字数和预计时长显示函数。

### 步骤 4：实现轮询 hook

```ts
useEffect(() => {
  if (!jobId || isTerminal(job?.status)) return;
  let disposed = false;
  const timer = window.setInterval(async () => {
    if (disposed) return;
    await pollOnce();
  }, 2_000);
  return () => {
    disposed = true;
    window.clearInterval(timer);
  };
}, [jobId, job?.status, pollOnce]);
```

首次恢复 job 时立即查询一次，不等 2 秒。连续失败计数只描述连接状态，不把服务端任务写成 failed。

### 步骤 5：验证并提交

```powershell
pnpm --filter @studio/web test -- src/demo/api.test.ts src/demo/script.test.ts src/demo/flow.test.ts src/demo/hooks/useJobPolling.test.ts
pnpm --filter @studio/web typecheck
git -C .. add digital-human-studio/apps/web/src/demo/types.ts digital-human-studio/apps/web/src/demo/api.ts digital-human-studio/apps/web/src/demo/api.test.ts digital-human-studio/apps/web/src/demo/script.ts digital-human-studio/apps/web/src/demo/script.test.ts digital-human-studio/apps/web/src/demo/flow.ts digital-human-studio/apps/web/src/demo/flow.test.ts digital-human-studio/apps/web/src/demo/hooks digital-human-studio/apps/web/src/test
git -C .. commit -m "feat(demo): 建立六步草稿状态与 API 适配层"
```

---

## 任务 6：实现 A2 桌面壳层、第一步引导问卷与第二步台词确认

**依赖：** 任务 5

**文件**

- 新建：`digital-human-studio/apps/web/src/demo/components/StudioShell.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/components/StepHeading.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/RequirementStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/ScriptStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/RequirementScriptSteps.test.tsx`
- 修改：`digital-human-studio/apps/web/src/main.tsx`
- 修改：`digital-human-studio/apps/web/src/styles.css`

### 步骤 1：写步骤 1–2 失败测试

使用 Testing Library 的 role/label 查询，覆盖：

- 预设行业和“自己输入行业”；预设用途和“自己输入视频用途”。
- 页面不存在“我不知道”或让 AI 猜行业/用途的入口。
- GLM 返回三种白名单题型；推荐选项首次以可见选中状态展示。
- 每道题都有“补充说明”，底部另有“还有什么想特别说明？”。
- 必填题未完成时主按钮禁用，加载时不能重复请求。
- GLM 问卷或台词失败时保留已输入内容并阻断下一步。
- 台词可编辑，固定优化与自由指令互斥提交；失败时保留当前版，只有成功重试或显式恢复上一有效版后可确认。
- 每个页面只有一个主 CTA；动态状态使用 `aria-live`。

先写完整的第一步红灯用例；`requirementHarness()` 返回任务 5 的完整 draft、mock API、dispatch 与完成回调：

```tsx
it('uses visible recommendations and keeps per-question plus global supplements', async () => {
  const user = userEvent.setup();
  const harness = requirementHarness({ questionnaire });
  render(
    <RequirementStep
      draft={harness.draft}
      api={harness.api}
      dispatch={harness.dispatch}
      onScriptCreated={harness.onScriptCreated}
    />,
  );

  await user.click(screen.getByRole('radio', { name: '电商零售' }));
  await user.click(screen.getByRole('button', { name: '下一步：选择用途' }));
  await user.click(screen.getByRole('radio', { name: '产品口播' }));
  await user.click(screen.getByRole('button', { name: '生成专属问题' }));

  expect(await screen.findByRole('radio', { name: '轻量防晒外套' })).toBeChecked();
  expect(screen.getByLabelText('补充说明：这条视频主要介绍什么？')).toBeVisible();
  expect(screen.getByLabelText('还有什么想特别说明？')).toBeVisible();
  expect(screen.queryByText(/我不知道|交给 AI 判断/)).not.toBeInTheDocument();
});
```

其余覆盖项分别形成独立测试，尤其断言 GLM 失败后 `onScriptCreated` 未调用、当前输入仍在表单中、主 CTA 保持禁用。

运行：

```powershell
pnpm --filter @studio/web test -- src/demo/steps/RequirementScriptSteps.test.tsx
```

预期：组件缺失而失败。

### 步骤 2：实现 A2 壳层

- `Layout.Sider` 固定 62 px，仅显示带可访问名称的图标入口。
- 内容区 `min-width: 1280px`，浅灰背景、白色细边卡片、8 px 圆角、克制阴影。
- 顶部六步 `Steps responsive={false}` 始终可见；完成态同时使用图标/文字，不只依赖颜色。
- `ConfigProvider` 主色 `#165DFF`；删除现有 320/720 px 响应式规则。
- 第 1–5 步单列任务区；不添加常驻项目、资产、通知或任务侧栏。

### 步骤 3：实现 RequirementStep

1A 和 1B 用语义化 `Radio.Group`；自定义项选中后展开 `Input`。行业变化提交前如会清除问卷，使用一次确认弹窗，取消时恢复原值。

问卷只按 `answerType` 白名单渲染：

```tsx
switch (field.answerType) {
  case 'singleChoice':
    return <Radio.Group aria-label={field.label} options={toOptions(field.options)} />;
  case 'multiChoice':
    return <Checkbox.Group aria-label={field.label} options={toOptions(field.options)} />;
  case 'shortText':
    return <Input aria-label={field.label} maxLength={200} />;
}
```

每题之后单独渲染 supplement 输入；全局补充使用独立 `Input.TextArea`，不预填示例。所有文本按 Unicode 长度显示剩余量。

### 步骤 4：实现 ScriptStep

展示可编辑 `Input.TextArea`、Unicode 字数和预计时长。优化期间禁用全部优化按钮；成功才替换台词并保存 previous valid version。失败显示 `Alert`，保留原台词。恢复按钮把台词恢复到上一有效版本并清除本次优化错误。

任务 6 不修改 `App.tsx`；测试通过 `requirementStepProps()`、`scriptStepProps()` builder 注入完整 props 后分别渲染两个组件。任务 9 负责一次性组装 App。

### 步骤 5：验证并提交

```powershell
pnpm --filter @studio/web test -- src/demo/steps/RequirementScriptSteps.test.tsx src/demo/flow.test.ts
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
git -C .. add digital-human-studio/apps/web/src/demo/components/StudioShell.tsx digital-human-studio/apps/web/src/demo/components/StepHeading.tsx digital-human-studio/apps/web/src/demo/steps/RequirementStep.tsx digital-human-studio/apps/web/src/demo/steps/ScriptStep.tsx digital-human-studio/apps/web/src/demo/steps/RequirementScriptSteps.test.tsx digital-human-studio/apps/web/src/main.tsx digital-human-studio/apps/web/src/styles.css
git -C .. commit -m "feat(demo): 完成 A2 引导问卷与台词步骤"
```

---

## 任务 7：实现第三步上传与第四步智能包装

**依赖：** 任务 5

**文件**

- 新建：`digital-human-studio/apps/web/src/demo/components/SingleUpload.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/UploadStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/PackagingStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/UploadPackagingSteps.test.tsx`

### 步骤 1：写步骤 3–4 失败测试

覆盖：

- 人物/声音并排的必填卡；前端类型/大小快速校验与服务端错误展示。
- 人物预览、音频试听、更换、上传进度和恢复的服务端文件引用。
- 明示固定 LTX2.3 与 IndexTTS2，不出现模型选择。
- 两个必填上传都成功前不能继续。
- 进入第四步只对当前 script revision 分析一次；失败保留上传和配置并阻断。
- 画中画默认开启，选择语义建议、展示锚定台词/理由/素材说明、上传真实 PIP 图。
- 画中画关闭时不要求图片；重新开启恢复先前 UI 值。
- 字幕默认 clean，可选 keywordHighlight、sentencePop；关键词预设要求独立 recommendation ID。
- 花字默认开启，可选建议、编辑文本和三种样式；开关关闭只提交 `enabled:false`。

先写一个完整包装红灯用例，固定画中画关闭时的提交形态和配置保留：

```tsx
it('submits only enabled packaging fields and restores pip values after re-enable', async () => {
  const user = userEvent.setup();
  const harness = packagingHarness({
    advice: packagingAdvice,
    pipUpload,
    subtitlePreset: 'keywordHighlight',
  });
  render(
    <PackagingStep
      draft={harness.draft}
      api={harness.api}
      dispatch={harness.dispatch}
      onComplete={harness.onComplete}
    />,
  );

  await user.click(screen.getByRole('switch', { name: '启用画中画' }));
  await user.click(screen.getByRole('button', { name: '确认包装' }));

  expect(harness.onComplete).toHaveBeenCalledWith(
    expect.objectContaining({ pip: { enabled: false } }),
  );
  expect(harness.onComplete.mock.calls[0][0].subtitle).toEqual({
    enabled: true,
    preset: 'keywordHighlight',
    keywordSourceRecommendationId: 'pip-main',
  });

  await user.click(screen.getByRole('switch', { name: '启用画中画' }));
  expect(screen.getByRole('radio', { name: /主要推荐位置/ })).toBeChecked();
  expect(screen.getByText(pipUpload.originalFileName)).toBeVisible();
});
```

上传测试另行断言人物和声音都获得服务端 `uploadId` 前，“继续智能包装”不可用；替换失败后旧 preview/ref 保持不变。

运行：

```powershell
pnpm --filter @studio/web test -- src/demo/steps/UploadPackagingSteps.test.tsx
```

预期：组件缺失而失败。

### 步骤 2：实现 SingleUpload 与 UploadStep

`SingleUpload` 使用 `Upload.Dragger` 的 customRequest 调统一 adapter。替换流程先上传新文件，成功后 dispatch 新 ref，再尽力删除旧未引用 ref；新上传失败不清除旧文件。

音频成功后使用本地 object URL 试听；卸载和替换时 revoke。刷新恢复只展示服务端 ref，不声称恢复浏览器 `File`。

### 步骤 3：实现 PackagingStep

语义推荐卡显示台词命中片段、理由、素材类型和预计时间比例。选择控件使用 `Radio.Group`，卡片只负责视觉容器。字幕/花字开关使用 `Switch` 并有显式 label。

表单提交构造器必须移除关闭功能的附属字段：

```ts
const pip = draft.packaging.pipEnabled
  ? { enabled: true, recommendationId: selectedRecommendationId, uploadId: pipUpload.uploadId }
  : { enabled: false };
```

包装分析失败使用集中 GLM 文案，不生成本地推荐。

任务 7 不修改 `App.tsx`；测试直接渲染 `UploadStep` 与 `PackagingStep` 并注入任务 5 的 mock API。正式步骤切换统一由任务 9 组装。

### 步骤 4：验证并提交

```powershell
pnpm --filter @studio/web test -- src/demo/steps/UploadPackagingSteps.test.tsx src/demo/api.test.ts src/demo/flow.test.ts
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
git -C .. add digital-human-studio/apps/web/src/demo/components/SingleUpload.tsx digital-human-studio/apps/web/src/demo/steps/UploadStep.tsx digital-human-studio/apps/web/src/demo/steps/PackagingStep.tsx digital-human-studio/apps/web/src/demo/steps/UploadPackagingSteps.test.tsx
git -C .. commit -m "feat(demo): 完成人物声音上传与语义包装步骤"
```

---

## 任务 8：实现不可变 Job、幂等、单 GPU 队列、Retry、TTL 和媒体 Range

**依赖：** 任务 2、3、4，以及任务 1 的远端能力门禁 R1 已通过

**文件**

- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_jobs.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_runtime.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_media.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/services/demo_security.py`
- 新建：`digital-human-studio/apps/api/src/dh_api/routes/demo_jobs.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/services/demo_gateway.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/routes/demo.py`
- 修改：`digital-human-studio/apps/api/src/dh_api/main.py`
- 新建：`digital-human-studio/tests/demo/test_demo_jobs.py`
- 新建：`digital-human-studio/tests/demo/test_demo_ttl.py`
- 新建：`digital-human-studio/tests/demo/test_demo_media.py`
- 新建：`digital-human-studio/tests/demo/test_demo_security.py`
- 新建：`digital-human-studio/tests/demo/support/job_fixtures.py`
- 修改：`digital-human-studio/tests/demo/test_demo_gateway.py`
- 修改：`digital-human-studio/tests/demo/test_demo_api.py`

### 步骤 1：写 Job 与生命周期失败测试

覆盖：

- 创建 job 只接受 JSON 中的 `adviceId` 和 upload IDs，并原子深冻结输入快照。
- 相同 idempotency key + 相同 canonical hash 返回原任务；相同 key + 不同 hash 返回 409。
- 过期上传、旧 advice 或 script hash 不一致时，在调用 gateway 之前失败。
- 单 GPU 同时只运行一个，其余保持 queued。
- retry 只允许 `failed && retryable && snapshot 未过期`，创建新 job 和 `parentJobId`。
- 旧任务终态不变；retry key 对相同父任务幂等，对冲突父任务拒绝。
- 上传 30 分钟、AI 快照 2 小时、终态 job/artifact 6 小时；运行中和流式读取中不清理。
- 普通视频 inline，下载 attachment；Range `bytes=0-1023` 返回 206、正确 `Content-Range/Accept-Ranges` 和 1024 字节。
- 未完成、失败、不存在、过期 artifact 返回稳定错误。

先写下面的完整 API 红灯用例，固定 JSON 提交和幂等冲突语义：

```python
def test_idempotency_reuses_same_payload_and_rejects_changed_payload(
    demo_job_client,
    valid_job_payload,
) -> None:
    client, fake_gateway = demo_job_client

    first = client.post("/v1/demo/jobs", json=valid_job_payload)
    same = client.post("/v1/demo/jobs", json=valid_job_payload)
    changed_payload = copy.deepcopy(valid_job_payload)
    changed_payload["subtitle"] = {"enabled": False}
    conflict = client.post("/v1/demo/jobs", json=changed_payload)

    assert first.status_code == 202
    assert same.status_code == 202
    assert same.json()["jobId"] == first.json()["jobId"]
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "IDEMPOTENCY_KEY_CONFLICT"
    assert fake_gateway.started_job_ids == [first.json()["jobId"]]
```

随后增加：`test_create_job_captures_server_resolved_immutable_snapshot`、`test_stale_advice_or_expired_upload_never_starts_gateway`、`test_single_gpu_queue_never_runs_two_jobs_concurrently`、`test_retry_creates_child_and_never_mutates_failed_parent`、`test_stream_reference_blocks_cleanup_until_response_closes`、`test_inline_and_download_bodies_have_identical_sha256`。本任务独占的 `support/job_fixtures.py` 构建 runtime、fake gateway 和已互相引用的 AI/upload fixture，不修改公共 conftest。

运行：

```powershell
uv run pytest tests/demo/test_demo_jobs.py tests/demo/test_demo_ttl.py tests/demo/test_demo_media.py tests/demo/test_demo_gateway.py tests/demo/test_demo_api.py -q
```

预期：新编排模块缺失，旧 multipart `/jobs` 与冻结 JSON 契约不匹配而失败。

### 步骤 2：实现原子快照和幂等索引

`DemoJobService.create()` 在同一锁内完成：解析 advice → 验证 script/hash → 解析上传 ref → 校验开关组合 → canonical hash → 幂等判定 → 写入不可变 snapshot → 入队。任何失败都不调用远端。

```python
async with self._lock:
    snapshot = self._snapshot_factory.resolve(request)
    request_hash = canonical_hash(request)
    existing = self._idempotency.get(request.idempotency_key)
    if existing and existing.request_hash != request_hash:
        raise DemoConflict("IDEMPOTENCY_KEY_CONFLICT")
    if existing:
        return self.require(existing.job_id)
    job = DemoJobRecord.from_snapshot(snapshot, request_hash=request_hash)
    self._jobs[job.id] = job
    self._queue.put_nowait(job.id)
    return job
```

队列 worker 固定一个；API 启动时检测 worker 数配置，不允许多进程共享内存 job store。

### 步骤 3：改造真实 gateway 顺序

删除代码中的公网 Comfy 默认地址，要求 `DEMO_COMFY_BASE_URL` 显式配置并通过 scheme/host/port allowlist。`demo_gateway.py` 消费不可变 snapshot，执行：

1. IndexTTS2 生成确认台词音频。
2. 获取或 ffprobe 实际音频时长。
3. 按真实时长请求 LTX2.3 数字人口播。
4. 探测基础视频音视频时长并校准。
5. 调任务 4 composer 合成。
6. 对最终文件再次 ffprobe，保存 artifact 后进入 done。

远端响应必须提供或可探测实际模型/工作流标识，保存到 `actualModels`；固定 UI 标签不能作为执行证据。总任务超时默认 45 分钟，FFmpeg 默认 5 分钟。远端原始错误映射为 `VOICE_MODEL_FAILED`、`AVATAR_MODEL_FAILED`、`COMPOSE_FAILED`、`OUTPUT_INVALID`，不回传堆栈。

### 步骤 4：实现 retry、TTL 和进程生命周期

retry 在新 key 下复制父任务 immutable snapshot，设置 `parentJobId` 并创建新状态机。注入 clock，让 TTL 测试无需真实等待。

`demo_runtime.py` 统一持有 AI store、upload store、job service、cleanup task 和单 worker queue。FastAPI lifespan 创建 runtime，正常退出先停止接单、等待安全点，再尽力清理当前进程未引用临时目录；启动扫描同一 demo 根下残留，只删除明确过期目录。

### 步骤 5：落实 loopback、Host、Origin 和单 worker 边界

`demo_security.py` 解析 `DEMO_API_HOST`、`DEMO_API_PORT`、`DEMO_WEB_ORIGINS`：host 必须解析为 loopback，默认 `127.0.0.1`；port 默认 `8765`；允许 origin 默认且仅为 `http://127.0.0.1:5173`，配置扩展也必须是 loopback HTTP origin。`main.run()` 使用这组设置并固定 `workers=1`：

```python
def run() -> None:
    settings = DemoBoundarySettings.from_env()
    uvicorn.run(
        "dh_api.main:app",
        host=settings.api_host,
        port=settings.api_port,
        workers=1,
    )
```

加入只作用于 `/v1/demo/` 的 `DemoBoundaryMiddleware`：拒绝不在 allowlist 的 `Host`，有 `Origin` 时只允许配置的本地 Web origin；同源无 Origin 的媒体请求允许。测试必须断言默认监听参数是 `127.0.0.1:8765/1 worker`、公网 host 配置启动失败、恶意 Host/Origin 返回 403、允许的 5173 origin 正常，并确认非 `/v1/demo/**` 响应不被该 middleware 改写。

代表性安全测试写成：

```python
def test_demo_boundary_rejects_non_loopback_host_and_origin(demo_security_client) -> None:
    client = demo_security_client

    bad_host = client.get(
        "/v1/demo/jobs/missing",
        headers={"host": "example.com", "origin": "http://127.0.0.1:5173"},
    )
    bad_origin = client.get(
        "/v1/demo/jobs/missing",
        headers={"host": "127.0.0.1:8765", "origin": "https://example.com"},
    )
    allowed = client.get(
        "/v1/demo/jobs/missing",
        headers={"host": "127.0.0.1:8765", "origin": "http://127.0.0.1:5173"},
    )

    assert bad_host.status_code == 403
    assert bad_origin.status_code == 403
    assert allowed.status_code == 404
    assert allowed.json()["error"]["code"] == "JOB_NOT_FOUND"
```

### 步骤 6：实现 Job 路由、聚合 router 与媒体 Range

```python
job_router = APIRouter(prefix="/jobs", tags=["demo-jobs"])

@job_router.post("", response_model=DemoJobDetail, status_code=202)
async def create_job(payload: CreateDemoJobRequest, request: Request) -> DemoJobDetail:
    return await require_demo_runtime(request).jobs.create(payload)

@job_router.post("/{job_id}/retry", response_model=DemoJobDetail, status_code=202)
async def retry_job(
    job_id: str, payload: RetryDemoJobRequest, request: Request
) -> DemoJobDetail:
    return await require_demo_runtime(request).jobs.retry(job_id, payload)

@job_router.get("/{job_id}", response_model=DemoJobDetail)
async def get_job(job_id: str, request: Request) -> DemoJobDetail:
    return require_demo_runtime(request).jobs.require_detail(job_id)

@job_router.get("/{job_id}/video")
async def get_video(
    job_id: str, request: Request, download: bool = False
) -> StreamingResponse:
    return require_demo_runtime(request).media.response(
        job_id=job_id,
        range_header=request.headers.get("range"),
        download=download,
    )
```

在 `routes/demo.py` 仅做一次串行聚合：`router.include_router(ai_router)`、`router.include_router(upload_router)`、`router.include_router(job_router)`。这一步同时替换旧 multipart `/jobs`，确保三条子路由没有重复 prefix/operation ID。

`demo_media.py` 解析单 Range，拒绝多段/越界 Range；流开始前增加 artifact 引用计数，生成器 `finally` 减少计数。inline 与 attachment 只改变响应头，不改变路径或字节。

`main.py` 只对 `/v1/demo/**` 把 FastAPI 422 和 demo exceptions 转换为嵌套 demo envelope，不改变其他 API 的错误结构。

### 步骤 7：验证并提交

```powershell
uv run pytest tests/demo/test_demo_jobs.py tests/demo/test_demo_ttl.py tests/demo/test_demo_media.py tests/demo/test_demo_security.py tests/demo/test_demo_gateway.py tests/demo/test_demo_api.py -q
uv run ruff check apps/api/src/dh_api/services/demo_jobs.py apps/api/src/dh_api/services/demo_runtime.py apps/api/src/dh_api/services/demo_media.py apps/api/src/dh_api/services/demo_security.py apps/api/src/dh_api/services/demo_gateway.py apps/api/src/dh_api/routes/demo.py apps/api/src/dh_api/routes/demo_jobs.py apps/api/src/dh_api/main.py tests/demo
uv run pyright apps/api/src/dh_api/services/demo_jobs.py apps/api/src/dh_api/services/demo_runtime.py apps/api/src/dh_api/services/demo_media.py apps/api/src/dh_api/services/demo_security.py apps/api/src/dh_api/services/demo_gateway.py apps/api/src/dh_api/routes/demo_jobs.py
git -C .. add digital-human-studio/apps/api/src/dh_api/services/demo_jobs.py digital-human-studio/apps/api/src/dh_api/services/demo_runtime.py digital-human-studio/apps/api/src/dh_api/services/demo_media.py digital-human-studio/apps/api/src/dh_api/services/demo_security.py digital-human-studio/apps/api/src/dh_api/services/demo_gateway.py digital-human-studio/apps/api/src/dh_api/routes/demo.py digital-human-studio/apps/api/src/dh_api/routes/demo_jobs.py digital-human-studio/apps/api/src/dh_api/main.py digital-human-studio/tests/demo/support/job_fixtures.py digital-human-studio/tests/demo/test_demo_jobs.py digital-human-studio/tests/demo/test_demo_ttl.py digital-human-studio/tests/demo/test_demo_media.py digital-human-studio/tests/demo/test_demo_security.py digital-human-studio/tests/demo/test_demo_gateway.py digital-human-studio/tests/demo/test_demo_api.py
git -C .. commit -m "feat(demo): 编排真实数字人任务与不可变成片"
```

---

## 任务 9：实现第五步生成、第六步结果、刷新恢复和真实前后端接线

**依赖：** 任务 6、7、8

**文件**

- 新建：`digital-human-studio/apps/web/src/demo/steps/GenerationStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/ResultStep.tsx`
- 新建：`digital-human-studio/apps/web/src/demo/steps/GenerationResultSteps.test.tsx`
- 修改：`digital-human-studio/apps/web/src/App.tsx`
- 修改：`digital-human-studio/apps/web/src/App.test.tsx`
- 修改：`digital-human-studio/apps/web/src/styles.css`

### 步骤 1：写步骤 5–6 与恢复失败测试

覆盖：

- 摘要包含台词、人物、声音、PIP、字幕、花字；不展示密钥和服务器路径。
- 双击/重复点击只创建一次 job；成功后立即 `history.replaceState` 写入 `?job=`。
- queued/running 刷新恢复第五步且不重复 POST；done 进入第六步；failed 恢复失败页；404/410 显示失效并回第一步。
- 阶段映射为“克隆声音、生成数字人、校准画面、合成字幕与花字、验证输出”；progress 为 null 时显示不确定进度。
- retry 创建新 job，URL 改为新 ID，并显示 parent 关系；旧 failed job 不变。
- 9:16 播放器 `object-fit: contain` 且不自动播放。
- 下载失败保留 done 状态和播放器；播放与下载引用同一 artifact。
- “修改后重新生成”按台词、人物声音、画面包装返回正确步骤和失效规则。

先写刷新恢复红灯用例，证明前端只 GET 现有 job，不重复 POST：

```tsx
it('restores a running job from the query string without creating another job', async () => {
  window.history.replaceState(null, '', '/?job=job-running-1');
  const api = createMockDemoApi({
    getJob: vi.fn().mockResolvedValue(jobRunningFixture),
    createJob: vi.fn(),
  });

  render(<App api={api} />);

  expect(await screen.findByRole('heading', { name: '正在生成视频' })).toBeVisible();
  expect(api.getJob).toHaveBeenCalledWith('job-running-1', expect.any(AbortSignal));
  expect(api.createJob).not.toHaveBeenCalled();
  expect(screen.getByText('生成数字人')).toBeVisible();
});
```

再增加双击“开始生成”只 POST 一次、done 恢复结果、failed retry 新建 ID、410 回第一步、下载失败不卸载播放器五个独立测试。

运行：

```powershell
pnpm --filter @studio/web test -- src/demo/steps/GenerationResultSteps.test.tsx src/App.test.tsx
```

预期：组件缺失和 App 尚未组装完整流程而失败。

### 步骤 2：实现 GenerationStep

提交时生成一次 UUID idempotency key，并在请求完成前复用同一 key。返回 202 后保存完整 server snapshot，再写 URL：

```ts
const url = new URL(window.location.href);
url.searchParams.set('job', job.jobId);
window.history.replaceState(null, '', url);
```

进入带 `?job=` 页面时先 GET，不创建草稿 job。生成期间前序步骤只读展示任务 snapshot；用户选择修改时建立新 draft revision，原任务继续独立存在。

### 步骤 3：实现 ResultStep

左侧 9:16 `<video controls preload="metadata">`，右侧只显示状态、时长、比例、格式和 `actualModels`。下载按钮调用 adapter 的受控 blob 下载，拥有独立 loading/error；失败只显示 Alert。

### 步骤 4：组装完整 App

`App.tsx` 只保留：API/runtime 注入、draft reducer、当前 step、URL 恢复和六个步骤组装。业务请求、错误码解析、上传和轮询不留在 App 中。

顶部已完成步骤可返回，但必须经过对应 guard；生成中的 snapshot 视图不允许修改。每一步仍只有一个 primary CTA。

### 步骤 5：验证并提交

```powershell
pnpm --filter @studio/web test -- src/demo/steps/GenerationResultSteps.test.tsx src/App.test.tsx src/demo
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
git -C .. add digital-human-studio/apps/web/src/demo/steps/GenerationStep.tsx digital-human-studio/apps/web/src/demo/steps/ResultStep.tsx digital-human-studio/apps/web/src/demo/steps/GenerationResultSteps.test.tsx digital-human-studio/apps/web/src/App.tsx digital-human-studio/apps/web/src/App.test.tsx digital-human-studio/apps/web/src/styles.css
git -C .. commit -m "feat(demo): 跑通生成进度、恢复、播放与下载"
```

---

## 任务 10：建立离线 API/E2E、故障注入和敏感信息扫描

**依赖：** 任务 9

**文件**

- 新建：`digital-human-studio/tests/__init__.py`
- 新建：`digital-human-studio/tests/demo/support/offline_app.py`
- 新建：`digital-human-studio/tests/demo/support/comfy_stub.py`
- 新建：`digital-human-studio/apps/web/e2e/demo-offline.spec.ts`
- 修改：`digital-human-studio/apps/web/playwright.config.ts`
- 新建：`digital-human-studio/tools/scan_demo_evidence.py`
- 新建：`digital-human-studio/tests/demo/test_demo_sensitive_scan.py`

### 步骤 1：先写离线 E2E 场景

先写完整 happy-path 红灯用例；任务 1 的问卷 fixture 把第一个必填单选项固定为“轻量防晒外套”，使角色查询稳定：

```ts
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';

const studioRoot = fileURLToPath(new URL('../../../', import.meta.url));

test('completes all six steps with frozen offline contracts', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('radio', { name: '电商零售' }).check();
  await page.getByRole('button', { name: '下一步：选择用途' }).click();
  await page.getByRole('radio', { name: '产品口播' }).check();
  await page.getByRole('button', { name: '生成专属问题' }).click();
  await page.getByRole('radio', { name: '轻量防晒外套' }).check();
  await page.getByLabel('补充说明：这条视频主要介绍什么？').fill('强调透气和轻便');
  await page.getByLabel('还有什么想特别说明？').fill('结尾提醒到店体验');
  await page.getByRole('button', { name: '生成台词草稿' }).click();

  await expect(page.getByRole('heading', { name: '确认台词' })).toBeVisible();
  await page.getByRole('button', { name: '确认台词' }).click();
  await page.getByLabel('上传人物照片').setInputFiles(
    path.join(studioRoot, 'tests/demo/fixtures/media/portrait-512.png'),
  );
  await page.getByLabel('上传参考声音').setInputFiles(
    path.join(studioRoot, 'tests/demo/fixtures/media/voice-5s.wav'),
  );
  await page.getByRole('button', { name: '继续智能包装' }).click();
  await page.getByRole('radio', { name: /主要推荐位置/ }).check();
  await page.getByLabel('上传画中画图片').setInputFiles(
    path.join(studioRoot, 'tests/demo/fixtures/media/pip-512.png'),
  );
  await page.getByRole('button', { name: '确认包装' }).click();
  await page.getByRole('button', { name: '开始生成' }).click();

  await expect(page).toHaveURL(/\?job=offline-job-/);
  await expect(page.getByText('验证输出')).toBeVisible();
  await expect(page.getByRole('heading', { name: '预览与下载' })).toBeVisible();
  await expect(page.getByLabel('数字人成片播放器')).toBeVisible();
  await expect(page.getByRole('button', { name: '下载 MP4' })).toBeEnabled();
});
```

随后增加：`blocks and preserves inputs after GLM failure`、`restores a running job after refresh without creating another job`、`restores done failed and expired jobs into distinct pages`、`keeps the player after a controlled download failure`。每个场景通过 `page.setExtraHTTPHeaders({ 'x-demo-test-scenario': scenario })` 选择 test-only 故障，明确断言请求计数、当前步骤、保留字段和稳定错误文案。

测试还需用键盘完成行业/用途/题目、上传、开关、步骤切换和下载触发；对 `aria-live` 错误与进度做可访问名称断言。

### 步骤 2：实现 test-only FastAPI 与 Comfy stub

`offline_app.py` 创建真实 demo router，但注入 mock BigModel transport、可控 clock、Comfy stub 和真实小型 composer。通过测试专用 header/fixture 选择 happy、GLM timeout、voice failed、avatar failed、compose failed、download failed、expired 场景；这些故障开关不进入生产 app。

`tests/__init__.py` 使 `tests.demo.support.offline_app` 可从 workspace root 导入。`playwright.config.ts` 明确冻结 `baseURL` 和每个进程的 cwd：

```ts
import { fileURLToPath } from 'node:url';

const studioRoot = fileURLToPath(new URL('../..', import.meta.url));

use: {
  baseURL: process.env.WEB_BASE_URL ?? 'http://127.0.0.1:5173',
  trace: 'retain-on-failure',
},
webServer: [
  {
    command: 'uv run uvicorn tests.demo.support.offline_app:app --host 127.0.0.1 --port 8765',
    cwd: studioRoot,
    url: 'http://127.0.0.1:8765/openapi.json',
    reuseExistingServer: false,
  },
  {
    command: 'pnpm --filter @studio/web dev --host 127.0.0.1 --port 5173',
    cwd: studioRoot,
    url: 'http://127.0.0.1:5173/',
    reuseExistingServer: false,
  },
]
```

配置测试先断言不再出现旧 `4173`，然后用 `pnpm --filter @studio/web exec playwright test --list` 验证模块导入和 web 配置可解析。

### 步骤 3：实现敏感信息扫描

扫描 demo 日志、pytest 临时产物、Playwright trace/截图旁的文本证据和 `.artifacts` JSON。规则检测 `Authorization`、BigModel key 形态、SSH credential 字段、完整 prompt/body、绝对远端路径；测试只使用合成 canary，不使用真实秘密。

### 步骤 4：运行完整离线基线

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\digital-human-studio
uv run python tools/generate_demo_test_fixtures.py --check
uv run pytest tests/demo -q
uv run ruff check apps/api/src tests/demo tools
uv run pyright
pnpm --filter @studio/web test
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
pnpm --filter @studio/web exec playwright test e2e/demo-offline.spec.ts
```

预期：所有命令退出码为 0；离线测试不访问 BigModel、Comfy 公网或共享 GPU。

### 步骤 5：提交离线验收

```powershell
git -C .. add digital-human-studio/tests/__init__.py digital-human-studio/tests/demo/support digital-human-studio/tests/demo/test_demo_sensitive_scan.py digital-human-studio/apps/web/e2e/demo-offline.spec.ts digital-human-studio/apps/web/playwright.config.ts digital-human-studio/tools/scan_demo_evidence.py
git -C .. commit -m "test(demo): 增加六步流程离线端到端验收"
```

---

## 任务 11：真实 GLM / IndexTTS2 / LTX2.3 / FFmpeg 预检、浏览器 smoke 与证据

**依赖：** 任务 10 全绿

**文件**

- 新建：`digital-human-studio/tools/demo_preflight.py`
- 新建：`digital-human-studio/tools/verify_demo_artifact.py`
- 新建：`digital-human-studio/tools/verify_demo_visual_layers.py`
- 新建：`digital-human-studio/tools/run_live_demo_smoke.py`
- 新建：`digital-human-studio/tests/demo/test_demo_preflight.py`
- 新建：`digital-human-studio/tests/demo/test_verify_demo_artifact.py`
- 新建：`digital-human-studio/tests/demo/test_verify_demo_visual_layers.py`
- 新建：`digital-human-studio/apps/web/e2e/demo-live.spec.ts`
- 新建：`digital-human-studio/apps/web/playwright.live.config.ts`
- 新建：`digital-human-studio/tests/demo/fixtures/live/README.md`
- 修改：`digital-human-studio/.gitignore`

### 步骤 1：先写 preflight/artifact 工具测试

preflight 测试注入进程 runner 和 httpx transport，覆盖：缺少有效模型凭据配置、非 HTTPS BigModel URL、Comfy 路由缺失、节点/模型能力缺失、GPU/队列不可用、FFmpeg 缺失、API worker 数不合法。错误必须在提交 job 前退出。

artifact 工具测试使用任务 1 的两个 MP4：完整文件通过，无音频文件失败；再覆盖错误比例、错误 codec、音视频差超限、SHA 不符。视觉层工具测试生成一段无图层 base 和一段带固定 PIP/字幕/花字的 final，断言三块预期区域在 cue 时间发生足够像素变化，并断言传入未合成 final 时失败。

先写未合成视频必须失败的完整红灯用例：

```python
def test_visual_layer_verifier_rejects_unchanged_final(media_dir, tmp_path) -> None:
    base = media_dir / "base-576x1024-h264-aac.mp4"
    manifest = RenderManifest(
        pip=RenderCue(start_seconds=1.0, end_seconds=2.0, region=(330, 80, 220, 300)),
        subtitle=RenderCue(start_seconds=1.0, end_seconds=2.0, region=(40, 820, 496, 140)),
        flower=RenderCue(start_seconds=1.0, end_seconds=2.0, region=(40, 120, 496, 180)),
    )

    with pytest.raises(VisualLayerVerificationError) as error:
        verify_visual_layers(
            base_video=base,
            final_video=base,
            pip_marker=media_dir / "pip-512.png",
            manifest=manifest,
            evidence_dir=tmp_path,
        )

    assert error.value.failed_layers == {"pip", "subtitle", "flower"}
```

随后生成带三层的短 final fixture，断言 verifier 返回三个 `passed=true`、六张抽帧存在且 `visual-layers.json` 不含字幕/花字原文。

运行：

```powershell
uv run pytest tests/demo/test_demo_preflight.py tests/demo/test_verify_demo_artifact.py tests/demo/test_verify_demo_visual_layers.py -q
```

预期：工具尚不存在而失败。

### 步骤 2：实现真实环境 preflight

GLM 检查：

- `BIGMODEL_API_KEY` 存在但不打印。
- Base URL 是允许主机的 HTTPS。
- 发一个最小 JSON-mode 请求，确认 2xx、实际 model 为配置的 `glm-5.2`、内容通过最小 schema。

Comfy 检查：

- `DEMO_COMFY_BASE_URL` 显式存在且在 allowlist。
- `/system_stats`、`/queue`、`/object_info` 正常。
- 一个不存在的 job 查询返回插件特定 not-found，证明 storyboard/job 路由加载。
- 从 object/workflow 信息验证 IndexTTS2、LTX2.3 所需节点和模型；确认 GPU 可用、队列状态可接受。
- 联合接口必须返回或允许探测 IndexTTS2 实际音频时长及实际模型标识；缺失时 preflight 阻断，不用估算时长继续。

本机检查 `ffmpeg -version`、`ffprobe -version`、API 单 worker、GPU 并发 1、5173/8765 loopback 可用。

preflight 只把脱敏结果写入 `.artifacts/demo-live/preflight.json`。

### 步骤 3：准备私有 live fixture 和忽略规则

`.gitignore` 增加：

```gitignore
tests/demo/fixtures/live/*.png
tests/demo/fixtures/live/*.jpg
tests/demo/fixtures/live/*.wav
tests/demo/fixtures/live/*.mp3
.artifacts/demo-live/
```

`README.md` 只说明需要一个 512–8192 px 人物图和一个 5–20 秒声音，不包含真实素材。人物和声音由演示人员本地放入；自动视觉验收固定使用任务 1 生成的高对比 `tests/demo/fixtures/media/pip-512.png`，它是真实参与 FFmpeg 的图片，并且内容已知，便于做像素断言：

```text
tests/demo/fixtures/live/portrait.png
tests/demo/fixtures/live/voice.wav
```

### 步骤 4：实现 live runner 与浏览器 smoke

`run_live_demo_smoke.py` 要求 `RUN_LIVE_DEMO=1`，随后：

1. 运行 preflight。
2. 仅在 loopback 启动 API:8765 和 Web:5173。
3. 运行 `demo-live.spec.ts`，从第一步完成到第六步。
4. 等待最长 45 分钟，记录每个真实阶段时间和 job ID。
5. 播放 Range 首段并下载完整 MP4。
6. 对播放完整响应与下载内容计算相同 SHA-256。
7. 运行 `verify_demo_artifact.py`。
8. 读取任务 4 的内部 `RenderManifest`，运行 `verify_demo_visual_layers.py` 对 base/final 同时刻抽帧。
9. 在 `finally` 停止本地子进程，不停止共享 ComfyUI。

证据固定写入：

```text
.artifacts/demo-live/preflight.json
.artifacts/demo-live/job.json
.artifacts/demo-live/stages.jsonl
.artifacts/demo-live/ffprobe.json
.artifacts/demo-live/artifact.sha256
.artifacts/demo-live/render-manifest.json
.artifacts/demo-live/visual-layers.json
.artifacts/demo-live/frames/pip-base.png
.artifacts/demo-live/frames/pip-final.png
.artifacts/demo-live/frames/subtitle-base.png
.artifacts/demo-live/frames/subtitle-final.png
.artifacts/demo-live/frames/flower-base.png
.artifacts/demo-live/frames/flower-final.png
.artifacts/demo-live/playwright/
```

`verify_demo_visual_layers.py` 用 ffmpeg 在每种图层 cue 的中点分别从 base/final 抽帧，再由 Pillow 计算区域差异：

- PIP：固定 PIP 区域与已知 `pip-512.png` 缩放结果的归一化误差低于阈值，同时 base/final 区域变化像素比例高于阈值。
- 字幕：底部安全区在字幕 cue 中点的 base/final 变化像素比例高于阈值；cue 外对照帧不得出现同等字幕区域变化。
- 花字：固定花字区域在花字 cue 中点的 base/final 变化像素比例高于阈值；cue 外对照帧不得出现同等花字区域变化。

阈值在合成媒体单测中固定并覆盖通过/失败样本，不能在 live 运行时自动放宽。`visual-layers.json` 记录时间、区域、差异比例和判定，不包含完整台词。任一层失败时 live smoke 退出非零。

`demo-live.spec.ts` 还必须断言页面显示服务端确认的实际模型代码，成片可加载 metadata，播放器与下载 SHA 一致，且真实启用的 PIP、字幕、花字配置出现在 job snapshot。视觉证据至少包含步骤 1 问卷、步骤 4 包装、步骤 5 进度、步骤 6 成片四张截图；配置断言与像素证据必须同时通过。

### 步骤 5：运行 gated live

在 `digital-human-studio` 下、环境变量已由演示人员在当前 shell 注入时执行：

```powershell
$env:RUN_LIVE_DEMO='1'
uv run python tools/demo_preflight.py --live --evidence-dir .artifacts/demo-live
uv run python tools/run_live_demo_smoke.py --fixture-dir tests/demo/fixtures/live --pip-image tests/demo/fixtures/media/pip-512.png --evidence-dir .artifacts/demo-live
```

预期：

- GLM-5.2 真正生成问卷、台词和包装建议。
- IndexTTS2 真正生成确认台词的克隆声音。
- LTX2.3 真正生成数字人口播。
- FFmpeg 真正合成所选 PIP、字幕预设和花字。
- 三种视觉层的 base/final 抽帧断言通过，证据写入 `visual-layers.json` 与 `frames/`。
- 最终 ffprobe 为单个有效 H.264 视频流 + AAC 音频流、9:16、正时长、音视频差不超过 0.25 秒。
- `Range: bytes=0-1023` 返回 206、1024 字节和正确 Range 头；完整播放与下载 SHA 相同。
- 浏览器 URL 可从 `http://127.0.0.1:5173/?job=<jobId>` 刷新恢复到真实终态。

任何一项不满足都保留 failed job 和脱敏证据，不能把任务标记 done。

### 步骤 6：最终回归与提交工具

```powershell
uv run pytest tests/demo -q
uv run ruff check apps/api/src tests/demo tools
uv run pyright
pnpm --filter @studio/web test
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
pnpm --filter @studio/web exec playwright test e2e/demo-offline.spec.ts
uv run python tools/scan_demo_evidence.py .artifacts/demo-live
git -C .. add digital-human-studio/tools/demo_preflight.py digital-human-studio/tools/verify_demo_artifact.py digital-human-studio/tools/verify_demo_visual_layers.py digital-human-studio/tools/run_live_demo_smoke.py digital-human-studio/tests/demo/test_demo_preflight.py digital-human-studio/tests/demo/test_verify_demo_artifact.py digital-human-studio/tests/demo/test_verify_demo_visual_layers.py digital-human-studio/apps/web/e2e/demo-live.spec.ts digital-human-studio/apps/web/playwright.live.config.ts digital-human-studio/tests/demo/fixtures/live/README.md digital-human-studio/.gitignore
git -C .. commit -m "test(demo): 增加真实模型预检与演示验收"
```

---

## 2. 每个波次的合并门禁

### Wave 0 门禁

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\digital-human-studio
uv run python tools/generate_demo_test_fixtures.py --check
uv run pytest tests/demo/test_demo_contracts.py tests/demo/test_demo_state.py tests/demo/test_demo_comfy_contract.py -q
uv run python tools/probe_demo_comfy.py --require-contract voice-first-v1 --output .tmp/demo-comfy-capabilities.json
```

检查：Pydantic DTO、TypeScript 设计、JSON fixture、状态枚举和错误码与批准规格逐字段一致；远端 Gate R1 明确证明 voice-first 时序、真实音频时长和实际模型 ID 可用。

### Wave 1 门禁

```powershell
uv run pytest tests/demo/test_demo_bigmodel.py tests/demo/test_demo_ai_api.py tests/demo/test_demo_uploads.py tests/demo/test_demo_timeline.py tests/demo/test_demo_composer.py tests/demo/test_demo_artifacts.py -q
pnpm --filter @studio/web test -- src/demo
```

检查：四条并行轨道没有修改彼此所有文件；GLM 失败无本地降级；媒体时间来自真实 ffprobe fixture。

### Wave 2 门禁

```powershell
uv run pytest tests/demo/test_demo_jobs.py tests/demo/test_demo_ttl.py tests/demo/test_demo_media.py tests/demo/test_demo_security.py tests/demo/test_demo_gateway.py tests/demo/test_demo_api.py -q
pnpm --filter @studio/web test -- src/demo/steps src/App.test.tsx
```

检查：任务快照不可变、Job 幂等、retry 新建任务、步骤守卫和 A2 页面状态一致。

### Wave 3/4 最终门禁

```powershell
uv run python tools/generate_demo_test_fixtures.py --check
uv run pytest tests/demo -q
uv run ruff check apps/api/src tests/demo tools
uv run pyright
pnpm --filter @studio/web test
pnpm --filter @studio/web typecheck
pnpm --filter @studio/web build
pnpm --filter @studio/web exec playwright test e2e/demo-offline.spec.ts
```

真实 smoke 只在上述命令全部通过后运行。

## 3. 浏览器人工验收清单

从 `http://127.0.0.1:5173/` 开始：

1. 选择预设行业与用途，再验证两个“自己输入”入口；确认没有 AI 猜测入口。
2. 查看 GLM 生成的 3–5 个行业问题；每题补充和全局补充同时存在。
3. 生成、编辑、优化并确认台词；模拟 GLM 失败时页面留在当前步骤。
4. 上传人物和声音，验证预览/试听/替换；确认只显示固定 LTX2.3 与 IndexTTS2。
5. 查看语义 PIP 建议，上传真实 PIP；依次选择三个字幕预设和三种花字样式。
6. 确认摘要后只创建一次任务；URL 立即出现 `?job=`；刷新不重复创建。
7. 查看真实阶段，遇到无可靠比例的阶段不显示伪百分比。
8. 第六步播放器完整显示 9:16，不自动播放；下载与播放器文件一致。
9. 刷新 done job 能恢复；failed job retry 产生新 job；expired job 回到第一步。
10. 用键盘走完主要控件，检查焦点、错误播报和选择状态不只依赖颜色。

## 4. 实施完成定义

只有同时满足以下条件才可宣布完成：

- 六步页面在 loopback URL 可打开，普通离线 E2E 全绿。
- GLM 失败在对应步骤阻断，页面没有内置问卷、默认台词或包装建议降级。
- 上传只产生临时 `uploadId`，真实文件校验和生命周期测试通过。
- Job 的状态/阶段、幂等、单 GPU 排队、retry、刷新恢复和终态保护通过。
- IndexTTS2 实际音频时长是 LTX 与所有包装时间轴唯一来源。
- 三种字幕、PIP 和花字都进入真实 FFmpeg 输出。
- 最终 MP4 通过 ffprobe 和 SHA 校验，播放器与下载完全同源。
- 真实 gated smoke 有脱敏 preflight、阶段、ffprobe、SHA 和浏览器证据。
- 日志与证据扫描不含任何密钥、凭据、完整敏感输入或远端绝对路径。
- 未把本演示误接入生产项目、任务中心、资产、通知、数据库、账号、权限或额度。

## 5. 计划自检

- [x] 规格路径、模块名、技术边界和取代关系明确。
- [x] 前端、后端执行服务、远端模型和联调任务都有精确文件路径。
- [x] 每个任务先写失败测试，再给最小实现、定向命令、预期和提交边界。
- [x] DTO、错误码、上传限制、状态流转和 fixture 在并行前冻结。
- [x] 四条 Wave 1 轨道文件互斥，说明了必须串行的汇合点。
- [x] 覆盖加载、失败、提交中、成功、重试、过期和下载失败状态。
- [x] 覆盖 GLM-5.2、IndexTTS2、LTX2.3、真实 FFmpeg、ffprobe 和浏览器验收。
- [x] 明确无响应式、无生产管理能力、无秘密入库。
- [x] 没有用假视频或固定模型标签替代真实模型验收。
- [x] 普通回归不消耗共享模型，真实 smoke 有显式开关与最长等待时间。
