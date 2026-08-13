# 数字人声音与视频纵向链规格

**状态：** 已批准实施

**批准依据：** 项目负责人已确认方案 A：IndexTTS2 独立生成声音，用户确认声音后，ComfyUI 使用人物图片和已确认音频生成数字人口播视频；并批准 F1 前仅对这两个已登记服务做非敏感短样例联调。

## 1. 本批范围

本批只跑通以下纵向链：

1. 用户提交已确认的口播正文和参考音频。
2. 后端调用 IndexTTS2 生成 WAV，并保存可追踪任务和私有媒体。
3. 用户试听并显式确认声音。
4. 用户提交人物图片。
5. 后端把人物图片与已确认 WAV 提交 ComfyUI。
6. 前端轮询平台任务；后端查询 ComfyUI、下载 MP4、验证并保存。
7. 用户在创作页播放生成的视频。

本批不实现额度、计费流水、草稿、共享、运营端、搜索、其他模型、时间轴和 P3。K0 知识库只负责后续向文案阶段提供素材，本链输入是已经确认的正文快照。

## 2. 业务边界

- 浏览器不得直连 IndexTTS2 或 ComfyUI。
- `ownerUserId` 与 `tenantId` 只从当前 App 登录会话派生，客户端不得提交。
- 声音任务和视频任务是两个不同任务；视频任务必须引用当前用户已经成功且已确认的声音任务。
- 生成文件写入服务端私有目录，数据库只保存安全的相对媒体键、摘要和元数据。
- 媒体读取必须再次校验当前用户和租户归属。
- 当前使用 `av_dh_generation_job` 作为这一纵链的最小持久化任务事实源；完整 P0-C 任务中心上线时必须迁移/吸收，禁止长期双写两套任务事实。
- Java 进程内执行声音任务；视频提交后由查询接口推进 ComfyUI 状态。该受限实现不新建 Worker/MQ，退出条件是统一任务执行器合入。

## 3. 平台 API

### 3.1 创建声音任务

`POST /api/studio/voice-jobs`

`multipart/form-data`：

- Header `Idempotency-Key`：必填，1～64 个安全字符。
- `scriptText`：必填，1～1000 字符。
- `referenceAudio`：必填，WAV/MP3/M4A/FLAC，最大 10 MiB。

返回 `DigitalHumanJobVo`。同一 owner、任务类型和幂等键且输入摘要相同时幂等回读；输入摘要不同时拒绝。

### 3.2 确认声音

`POST /api/studio/voice-jobs/{jobId}/confirmation`

只允许确认当前 owner 的 `voice_generate/succeeded` 任务；重复确认幂等。

### 3.3 创建视频任务

`POST /api/studio/video-jobs`

`multipart/form-data`：

- Header `Idempotency-Key`：必填。
- `voiceJobId`：必填，当前 owner 已确认的声音任务。
- `portraitImage`：必填，JPG/PNG/WebP，最大 10 MiB。

返回独立的 `video_generate` 任务，其 `parentJobId` 为声音任务编号。

### 3.4 查询和媒体读取

- `GET /api/studio/jobs/{jobId}`：查询当前 owner 的任务；视频处于运行态时最多执行一次 ComfyUI 状态刷新。
- `GET /api/studio/jobs/{jobId}/media`：仅成功任务可读取输出，返回真实 `audio/wav` 或 `video/mp4` 字节流。

任务状态固定为 `queued | running | succeeded | failed`；阶段固定为 `queued | voice_synthesizing | awaiting_voice_confirmation | video_submitted | video_rendering | completed | failed`；进度为 0～100。

## 4. Provider 契约

### IndexTTS2

- `POST {baseUrl}/v1/indextts2/clone`
- `multipart/form-data`：`text`、`reference_audio`
- 认证：可选 Basic Auth，同时使用 `X-API-Key`
- 成功：`200 audio/wav`
- 超时：连接 10 秒、整体 300 秒；本批不自动重放真实生成请求。
- HTTPS 正常校验证书；私有 CA 通过运行时 PEM 路径注入，禁止 trust-all。

### ComfyUI

- `POST {baseUrl}/codex/digital-human/run`
- `multipart/form-data`：`portrait`、`audio`
- 成功：JSON 中包含 `prompt_id`
- 查询：`GET {baseUrl}/history/{prompt_id}`
- 成功输出：从 `outputs` 中选择 MP4 条目，再调用 `GET {baseUrl}/view?filename=...&subfolder=...&type=...`
- 认证：支持运行时 Basic Auth；当前本机服务未挑战认证时仍可发送。
- 提交超时 30 秒、查询 10 秒、下载 300 秒。
- 开发环境安全例外：仅 `application-dev.yml` 可通过 `insecure-http-allowed-hosts` 精确允许
  `36.133.55.206` 的远程 HTTP；默认及其他环境保持禁止，不支持通配符。远端具备 HTTPS 或受控加密代理后删除该例外。

供应商 JSON 和认证细节只存在于 `ai-video-infra`，不得泄漏到 Controller、前端、异常消息或日志。

## 5. 配置

以下共享开发值直接登记并提交在两端 `application-dev.yml`，同名环境变量仅作为可选覆盖：

- `DEMO_INDEXTTS_BASE_URL`
- `DEMO_INDEXTTS_API_KEY`
- `DEMO_INDEXTTS_BASIC_USER`
- `DEMO_INDEXTTS_BASIC_PASSWORD`
- `DEMO_INDEXTTS_CA_CERTIFICATE`
- `DEMO_COMFY_BASE_URL`
- `DEMO_COMFY_BASIC_USER`
- `DEMO_COMFY_BASIC_PASSWORD`
- `AI_VIDEO_DH_MEDIA_ROOT`

## 6. 验收

- 前端不再用定时器伪造声音和视频进度。
- 所有创建、确认、查询和媒体读取都经过 App 登录门禁和 owner 校验。
- 失败进入可见终态，不把供应商认证信息、完整私有路径或响应体写入日志。
- 单元测试覆盖 multipart 字段、认证头、状态解析、owner 隔离、声音确认门禁和幂等冲突。
- 受限真实联调只使用非敏感短文本、合成参考音频和测试人物图；不得调用搜索或其他模型，不得据此宣布 F1 或整体项目 PASS。
