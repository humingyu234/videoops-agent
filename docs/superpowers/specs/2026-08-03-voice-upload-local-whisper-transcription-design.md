# 声音上传与本地 Whisper 异步转写设计规格

## 1. 结论与用户决策

本规格扩展数字人工作台“声音”菜单，使用户能够上传真实音频，并由服务器本地常驻
`faster-whisper` Worker 在后台异步解析声音文本。用户已确认：

- 使用服务器本地 `faster-whisper`，不接入 IndexTTS2。
- 不做声音质量检测、声音克隆、额度、统一任务中心或任务追踪页面。
- 只实现声音上传、受控试听、后台文本解析、解析结果展示和现有页面中的文本修正。
- Java/RuoYi 后端负责认证、权限、工作区归属、文件与声音记录、状态持久化和失败恢复；
  Python Worker 只负责 Whisper 推理，不访问业务数据库。

本规格遵守 `RULES.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、
`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md`、`docs/BACKEND_GUIDE.md`、
`docs/BACKEND_CODING_STANDARDS.md`、`docs/FRONTEND_GUIDE.md`、
`docs/FRONTEND_CODING_STANDARDS.md` 与 `docs/AI_AGENT_GOVERNANCE.md`。

## 2. 目标、范围与不做范围

### 2.1 单一目标

用户在数字人工作台“声音”菜单上传一段 MP3、WAV 或 M4A 音频后，HTTP 请求立即返回已持久化的
声音资源；Java 后台可靠调用本机 Whisper Worker，将转写文本写回声音资源，页面自动刷新并展示结果。

### 2.2 必须实现

- 当前创作端用户上传单个声音文件并填写名称、性别、风格、标签和备注。
- 文件大小、扩展名、声明 MIME、文件头、用户权限和当前工作区归属校验。
- 声音资源的分页列表、关键词／类型／解析状态筛选和详情查询。
- 受授权的 120 秒音频试听 URL，页面使用真实音频播放，不暴露永久对象地址。
- `pending → transcribing → ready|failed` 的资源解析状态。
- Java 事务提交后异步领取解析工作；服务重启、Worker 短暂不可用或租约过期后可恢复。
- 本机 `faster-whisper` Worker 常驻并预加载本地模型，输出文本、检测语言和音频时长。
- 页面轮询含未完成记录的列表或详情，终态后停止轮询。
- 用户修正已解析文本时进行乐观并发控制；失败记录支持显式重试。
- 加载、空、搜索无结果、上传中、解析中、失败、权限不足和接口失败状态。

### 2.3 明确不做

- 不生成克隆音色，不调用 IndexTTS2，不新增 `/api/voice-clone/tasks`。
- 不做音量、噪声、清晰度、静音比例或内容质量判断；文件安全边界校验不属于质量检测，必须保留。
- 不冻结、扣减或退回额度。
- 不创建 `av_ai_task` 根任务，不进入任务中心，不提供独立任务进度页面。
- 不做实时流式识别、说话人分离、翻译、字幕文件导出或人工审核流程。
- 不让浏览器、Electron 或 Python Worker访问业务数据库或永久对象存储凭据。
- 不把现有 7 条前端演示声音写入生产数据库；它们只作为组件与视觉测试 fixture。

## 3. 风险等级与最小任务卡

### 3.1 风险等级

本任务是红色高风险，命中文件上传与访问、用户数据归属、公共 API、数据库结构、后台恢复和内部
模型服务信任边界。不得因为“不做任务中心和额度”降低风险等级。

### 3.2 任务卡

- 目标：完成声音上传、本机 Whisper 异步转写和声音菜单真实接口接入。
- 允许影响：`ai-video-core` 的 `voice` 聚合、`ai-video-user` 的用户端 HTTP 入口、
  `ai-video-infra` 的 Whisper 直接集成、`ai-video-user-api` 装配与配置、声音前端 Service/页面、
  新增 Worker、前向 SQL 和相关公共契约。
- 不允许影响：声音克隆、IndexTTS2、额度、任务中心、运营端声音管理、其他创作步骤和用户未提交改动。
- 实施：最多 1 名实施者；完成后 1 名独立审查者进行安全／数据专项审查，修复后只定向复核。
- 反向验收：匿名、错误权限、运营端 Token、跨工作区 ID、伪造 MIME、超限文件、重复提交、
  过期试听 URL、Worker 伪造响应、旧租约、旧修订和删除资源写回必须被拒绝。
- 强制验证：Java 单元/Web/模块测试、Worker 单元测试、前端测试、TypeScript、Biome、生产构建、
  本机 MySQL/Redis 专用测试环境中的最小集成测试以及两个启动应用的路由边界 Smoke Test。

## 4. 总体架构与职责

```text
React 声音菜单
  -> ai-video-user-api / ai-video-user Controller
    -> ai-video-core voice Service / Mapper / MySQL
      -> RuoYi OSS/文件能力保存音频
      -> 事务提交后进入声音资源自身的解析队列状态
        -> ai-video-infra 定时唤醒
          -> ai-video-core 条件领取租约
            -> ai-video-infra Whisper HTTP client
              -> 127.0.0.1:18181 常驻 faster-whisper Worker
                -> Java 条件写回 ready / pending-retry / failed
```

- `ai-video-core` 使用 RuoYi 贫血 Entity 加 Service 编排，目录只使用 `domain`、`dto`、`mapper`、
  `service`、`service.impl`；不新增 `application`、`port`、`adapter`、`command` 或 `model` 业务层。
- `ai-video-user` 只放用户端 Controller、BO、VO 和显式 `type = app` 权限入口。
- `ai-video-infra` 只放 Whisper HTTP client、配置和定时唤醒；不承载用户权限或资源归属判断。
- Worker 放在新根目录 `ai-video-worker/whisper`，只监听回环地址，只接收 Java 内部请求。
- Worker 不读取业务表，不扫描业务文件目录，不接收用户 URL，不回写数据库。

## 5. 用户端 API 契约

所有接口只装配到 `ai-video-user-api`，使用 `R<T>` / `R<PageResult<T>>`，只接受创作端 `app`
令牌。所有 `Long` ID、文件大小和毫秒时长使用 JSON 十进制字符串。

### 5.1 权限

- `aivideo:voice:query`
- `aivideo:voice:upload`
- `aivideo:voice:edit`
- `aivideo:voice:transcribe`

权限不能替代 tenant、workspace 和 `ownerId` 的 SQL 条件；Controller 不接收归属字段。

### 5.2 上传并创建声音

`POST /api/voices`

请求为 `multipart/form-data`，字段集合精确为：

```text
file                   # 必填，单文件
metadata               # 必填，application/json
```

`metadata` 精确包含：

```json
{
  "idempotencyKey": "客户端生成的唯一键",
  "name": "亲切女声",
  "gender": "female",
  "style": "friendly",
  "tags": ["直播", "产品介绍"],
  "note": "可选备注"
}
```

- `gender` 允许 `female|male|unspecified`；默认 `unspecified`。
- `name` 1–80 字，`style` 最多 40 字，标签最多 8 个且单项最多 20 字，备注最多 500 字。
- 文件允许 MP3、WAV、M4A，最大 100MB；扩展名、声明 MIME 和文件头必须一致。
- 不在本范围校验 30 秒至 5 分钟建议时长；Whisper 返回实际时长后持久化。
- `ownerId + idempotencyKey` 去重。完全相同重放返回原声音；文件摘要或元数据不同则返回冲突。
- 文件先写入受控存储，再在事务中创建素材和声音记录；数据库失败时执行可恢复的孤儿文件清理。

成功响应 `data` 为 `VoiceVo`，初始 `transcriptionStatus=pending`。

### 5.3 声音分页列表

`GET /api/voices`

支持：

```text
keyword
voiceType              # origin|clone|public；本阶段只创建 origin
transcriptionStatus    # pending|transcribing|ready|failed
pageNum
pageSize               # 最大 50
```

- 当前阶段数据库只返回真实持久化资源；前端 7 条参考数据不得与线上结果混合。
- 关键词匹配名称、风格、标签和解析文本。
- 固定按 `create_time DESC, voice_id DESC` 排序，不接收原始 SQL 排序字段。
- 空页稳定返回 `rows=[]`。

### 5.4 声音详情

`GET /api/voices/{voiceId}`

仅返回当前 tenant、workspace 和 owner 的非删除声音。不存在或越权统一返回 `46401`，不泄露资源是否存在。

### 5.5 试听授权

`GET /api/voices/{voiceId}/access-url`

要求 `aivideo:voice:query`、当前资源归属和素材可用，返回：

```json
{
  "url": "短期受控 URL",
  "expiresAt": "2026-08-03 12:00:00",
  "contentType": "audio/wav",
  "fileName": "voice.wav"
}
```

有效期固定 120 秒，支持音频 Range 请求；页面不得长期保存 URL。

### 5.6 修正解析文本

`PUT /api/voices/{voiceId}/transcript`

请求字段精确为：

```json
{
  "transcriptText": "修正后的文本",
  "expectedRevision": "3"
}
```

- 只允许 `ready` 状态，文本去首尾空白后不能为空，最大 20000 字。
- 条件更新 `record_revision`，冲突返回 `46403`。
- 手工修改后只更新展示文本和修订号，不重新调用 Whisper。

### 5.7 重试失败解析

`POST /api/voices/{voiceId}/transcription/retry`

请求：

```json
{
  "expectedRevision": "3"
}
```

- 只允许 `failed` 状态；成功后清理对外失败信息并回到 `pending`。
- 这是声音资源动作，不创建统一任务记录，也不改变声音 ID 或素材 ID。
- 重复的相同修订请求只有一次成功，后续请求返回修订冲突或状态不允许。

### 5.8 `VoiceVo`

```text
voiceId
assetId
name
voiceType              # 本阶段新建值固定 origin
gender
style
tags
note
fileName
contentType
fileSizeBytes
durationMillis
transcriptionStatus
transcriptText
detectedLanguage
failureCode
failureMessage
recordRevision
createTime
updateTime
```

- `pending|transcribing` 时 `transcriptText=null`。
- `ready` 时 `transcriptText` 必须非空，`failureCode/failureMessage=null`。
- `failed` 只返回稳定失败码和脱敏提示，不返回模型堆栈、文件路径或内部地址。

## 6. Worker 内部协议

Worker 绑定 `127.0.0.1:18181`，不经用户端网关暴露。

### 6.1 健康检查

`GET /health`

成功只表示进程、模型和推理设备已就绪：

```json
{
  "status": "ok",
  "model": "large-v3",
  "device": "cuda"
}
```

模型未加载返回 HTTP 503。该端点不得返回本地模型绝对路径、GPU 序列号或环境变量。

### 6.2 转写

`POST /internal/v1/transcriptions`

请求为 `multipart/form-data`：

```text
file
requestId              # Java 生成：voiceId:revision:attempt
language               # 本阶段固定 zh，可由配置调整
wordTimestamps         # 固定 false，本范围只持久化文本
```

请求必须携带 `X-Internal-Token`；Worker 使用恒定时间比较，缺失或错误返回 401。Worker 使用
`UploadFile`/临时文件，完成或失败后都删除临时文件。

响应：

```json
{
  "requestId": "9824516531:1:1",
  "text": "欢迎来到我们的直播间。",
  "language": "zh",
  "languageProbability": 0.98,
  "durationSeconds": 12.46,
  "model": "large-v3"
}
```

- `text` 归一化连续空白并去首尾空白；空文本按不可用结果处理。
- Worker 不返回原文件名、临时路径、堆栈、模型目录或 CUDA 诊断详情。
- Java 校验响应字段、`requestId`、长度、有限数值和允许语言后才允许写回。

### 6.3 Worker 配置

共享开发值直接写入并提交在两端 `application-dev.yml`，以下同名环境变量仅作为可选覆盖：

```text
AIVIDEO_WHISPER_HOST=127.0.0.1
AIVIDEO_WHISPER_PORT=18181
AIVIDEO_WHISPER_MODEL_PATH=<服务器本地模型目录>
AIVIDEO_WHISPER_DEVICE=cuda|cpu
AIVIDEO_WHISPER_COMPUTE_TYPE=float16|int8_float16|int8
AIVIDEO_WHISPER_INTERNAL_TOKEN=<内部凭据>
AIVIDEO_WHISPER_MAX_CONCURRENCY=1
```

生产启动使用 `local_files_only=true`，模型目录缺失时启动失败；运行时禁止自动下载模型。

## 7. 数据模型

新增 `av_voice`：

| 字段 | 规则 |
| --- | --- |
| `voice_id` | `BIGINT` 雪花主键。 |
| `tenant_id` / `workspace_id` / `owner_id` | 创作端归属，全部由登录态派生。 |
| `asset_id` | 受控音频素材 ID，唯一绑定一条声音。 |
| `idempotency_key` / `upload_fingerprint` | 用户范围去重与冲突检测。 |
| `voice_type` | 本阶段固定 `origin`。 |
| `name` / `gender` / `style` / `tags_json` / `note` | 展示元数据。 |
| `transcript_text` | Whisper 结果或用户修正文本。 |
| `detected_language` / `duration_millis` | Worker 返回并由 Java 校验后写入。 |
| `transcription_status` | `pending|transcribing|ready|failed`。 |
| `failure_code` / `failure_message` | 稳定失败语义与脱敏提示。 |
| `attempt_count` / `next_attempt_at` | 有界自动重试。 |
| `lease_owner` / `lease_expires_at` | 防止多 Java 实例重复处理，并支持崩溃恢复。 |
| `record_revision` | 乐观并发修订号，从 1 开始。 |
| `del_flag` | 逻辑删除。 |
| BaseEntity 审计字段 | `create_by/update_by` 写当前 `appUserId`，`create_dept` 可空。 |

约束与索引：

- 唯一 `(tenant_id, owner_id, idempotency_key)`。
- 唯一 `(tenant_id, asset_id)`。
- 所有权列表索引 `(tenant_id, workspace_id, owner_id, del_flag, create_time, voice_id)`。
- 领取索引 `(transcription_status, next_attempt_at, lease_expires_at)`。
- `CHECK` 约束固定枚举和非负时长／尝试次数。

Entity 默认继承 `BaseEntity`，没有新增分层或对象职责例外。

## 8. 异步领取、重试与一致性

### 8.1 创建与唤醒

- 上传接口在事务中创建 `pending` 声音。
- 提交后事件只负责唤醒扫描器；数据库 `pending` 状态是唯一恢复事实，事件丢失不影响最终处理。
- HTTP 请求不等待 Whisper 推理。

### 8.2 领取

- 扫描器只选择 `pending AND next_attempt_at <= now`，以及租约已过期的 `transcribing`。
- Service 使用状态、修订和租约条件更新领取；只有影响 1 行的实例获得执行权。
- 外部 Worker 调用不包在数据库长事务内。

### 8.3 写回

- 成功写回要求 `voice_id + status=transcribing + lease_owner + record_revision` 全部匹配。
- 旧 Worker、过期租约、已删除资源或用户已经修改修订时，结果必须丢弃。
- 成功原子写入文本、语言、时长、`ready`、清理租约和失败字段并递增修订。

### 8.4 失败与恢复

- 网络超时、HTTP 503 和临时设备繁忙最多自动尝试 3 次，指数退避后回到 `pending`。
- 无效音频、空文本、协议异常或达到上限进入 `failed`。
- Java 或 Worker 重启后，扫描器回收过期租约；不需要统一任务中心。
- 人工重试从新修订开始，旧请求结果不能覆盖新尝试。

## 9. 稳定错误码

文件通用错误继续复用 `46201 FILE_TYPE_NOT_ALLOWED`、`46202 FILE_SIZE_EXCEEDED`、
`46204 UPLOAD_SESSION_CONFLICT`、`46209 FILE_NOT_READY`。

新增声音错误：

| 错误码 | 稳定标识 | 语义 |
| --- | --- | --- |
| `46401` | `VOICE_NOT_FOUND` | 声音不存在、已删除或不属于当前工作区。 |
| `46402` | `VOICE_INPUT_INVALID` | 元数据或声音文本不符合规则。 |
| `46403` | `VOICE_REVISION_CONFLICT` | 预期修订号过期。 |
| `46404` | `VOICE_TRANSCRIPTION_STATE_INVALID` | 当前解析状态不允许编辑或重试。 |
| `46405` | `VOICE_TRANSCRIPTION_UNAVAILABLE` | 本地 Whisper 在有界重试后仍不可用。 |

前端只按数字码和稳定状态处理，不解析中文 `msg`。

## 10. 前端接入

### 10.1 Service 与状态

- 新增 `src/services/ai-video/voice`，依赖方向保持 Page → Voice Service → RuoYi adapter → Umi Request。
- React Query 查询键包含关键词、类型、状态和页码；上传、文本修改和重试成功后失效声音查询。
- 只要当前页存在 `pending|transcribing`，每 2 秒刷新一次；没有未完成项立即停止。
- 页面卸载、查询变化和取消请求必须中止旧请求，旧响应不能覆盖新条件。

### 10.2 页面行为

- 保留现有一比一布局、卡片、分页、展开、时间轴和编辑视觉。
- 生产运行时只展示 API 数据；7 条参考声音只进入测试/mock，不与线上声音拼接。
- 状态筛选文案调整为“全部状态 / 已解析 / 解析中 / 解析失败”，不再把转写冒充质量校验。
- 上传弹窗真实选择文件并调用 `POST /api/voices`，上传中禁用重复提交。
- `pending|transcribing` 卡片显示“解析中”，展开后显示稳定提示，不展示伪造文本。
- `failed` 显示脱敏失败原因和重试按钮；`ready` 展示解析文本并允许原位修正。
- 播放前请求短期 access URL，使用单实例 `HTMLAudioElement`；新声音开始时停止上一条。
- URL 过期时只重新获取一次；401/403 交给集中 adapter，取消播放不显示业务失败。

## 11. 安全与隐私

- 用户端 Controller 权限注解必须显式 `type = app`；运营端 Token 和同号用户不得访问。
- tenant、workspace、owner 条件进入 SQL，不允许查出后在内存过滤。
- 文件名安全化；对象 Key 不含用户名、手机号、邮箱和原文件名。
- Java 以流式 multipart 把受控资产发送给 Worker，不把对象存储签名 URL、永久路径或凭据交给 Worker。
- Worker 只绑定回环地址并校验内部 Token；共享开发 Token 直接写入并提交在两端 `application-dev.yml`，环境变量可选覆盖，日志不得记录 Token。
- 普通日志不记录音频、完整转写文本、请求体、临时路径或内部响应。
- 临时文件在成功、异常、超时和客户端断开时都清理。
- Worker 设最大并发、文件上限和推理超时，防止单用户耗尽 GPU/CPU。

## 12. 实现文件边界

### 12.1 Java

- `ai-video-core/voice/domain`：`Voice`。
- `ai-video-core/voice/dto`：查询、创建、解析结果和展示 DTO。
- `ai-video-core/voice/mapper`：`VoiceMapper`。
- `ai-video-core/voice/service`：`IVoiceService`、`IWhisperTranscriptionService`。
- `ai-video-core/voice/service/impl`：`VoiceServiceImpl`。
- `ai-video-user/user/voice/domain/bo|vo` 与 `controller`：用户端 HTTP 对象和入口。
- `ai-video-infra/voice/client`：Worker 原始协议和 HTTP client。
- `ai-video-infra/voice/service/impl`：核心转写 Service 契约实现。
- `ai-video-infra/voice/listener`：纯技术定时唤醒，业务领取与写回仍调用核心 Service。

### 12.2 Worker

```text
ai-video-worker/whisper/
  pyproject.toml
  uv.lock
  src/aivideo_whisper/app.py
  src/aivideo_whisper/config.py
  src/aivideo_whisper/transcriber.py
  tests/
  README.md
```

### 12.3 SQL 与文档

- 新增前向迁移 `20260803_04_voice_upload_transcription.sql`，不修改已发布迁移。
- 更新 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ARCHITECTURE.md`。
- `docs/ASYNC_TASKS.md` 仅登记该流程是声音资源内部后台处理，不创建统一生成任务；不得改变其他生成任务规则。
- 文档变更后运行 `scripts/validate-development-standards.ps1`。

## 13. 测试与验证

### 13.1 Java

- Service：权限、归属、幂等、状态机、租约领取、过期恢复、成功／失败写回、旧租约和修订冲突。
- Web：multipart 校验、401、403、运营端 Token、跨账号、分页空数组、详情、access URL、文本修改和重试。
- Mapper：owner SQL 条件、最大页大小、固定排序和并发条件更新。
- Worker client：超时、401、413、503、非法 JSON、错误 requestId、空文本、NaN/负时长和过长文本。
- 使用本机 `ai_video_test` 和隔离 Redis 逻辑库；禁止 Docker、Testcontainers、WSL 或远程数据库。

### 13.2 Worker

- 模型加载失败使 `/health` 返回 503。
- 错误内部 Token 返回 401。
- 超限或不允许文件返回 413/415。
- 使用 fake WhisperModel 验证文本、语言、时长归一化和临时文件清理。
- 最小非敏感 WAV fixture 验证 multipart；真实模型 Smoke Test 独立标记为 `slow`。

### 13.3 前端

- adapter 解析分页、ID/大小/时长字符串、401/403 和业务错误。
- 上传成功立即出现“解析中”；重复提交被禁用。
- 轮询仅在存在未完成项时运行并在终态停止。
- ready 展示文本并持久化修改；failed 显示重试；空／失败／权限状态完整。
- 真音频播放保持单实例，URL 过期只刷新一次，卸载释放 Audio 与请求。
- 运行声音测试、数字人工作台回归、TypeScript、Biome 和生产构建。

### 13.4 部署与人工验收

- Worker 启动后 `GET http://127.0.0.1:18181/health` 返回 `status=ok`；转写端点不能通过浏览器 GET 使用。
- 分别验证 CPU 和目标服务器 GPU 配置，记录模型、设备和平均处理耗时，不记录用户文本。
- 上传一条非敏感中文短音频，确认 HTTP 立即返回、后台进入 ready、页面出现真实文本并可试听／修正。
- 停止 Worker 后上传，确认有界重试与失败状态；恢复 Worker 并手工重试后成功。
- 分别验证 `ai-video-user-api` 暴露声音接口，`ruoyi-admin` 不暴露用户端声音接口。

## 14. 完成定义

- 用户能够上传真实 MP3/WAV/M4A 并立即得到持久化声音 ID。
- 本机常驻 `faster-whisper` 在后台解析文本，重启和短暂故障不会永久丢失工作。
- 页面展示真实服务端列表、解析状态、文本、试听和文本修改，不再把生产数据保存在本地常量。
- 无 IndexTTS2、声音克隆、质量检测、额度或任务中心副作用。
- 权限、归属、文件安全、内部 Worker 身份、租约和乐观并发反向场景通过。
- Java、Worker、前端和文档验证全部通过；无法运行的真实 GPU Smoke Test 明确列为阻塞，不得描述为已完成。
