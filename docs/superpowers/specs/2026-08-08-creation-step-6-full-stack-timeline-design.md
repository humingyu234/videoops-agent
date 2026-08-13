# 创作第 6 步完整前后端时间轴设计规格

## 1. 文档状态与冻结结论

- 日期：2026-08-08
- 模块：数字人工作台 / 创作 / 第 6 步“时间轴编辑”
- 页面入口：用户端 `/studio` 七步创作流程的第 6 步
- 风险等级：红色
- 风险触发：用户私有媒体与访问授权、公共 API、数据库结构、共享状态机、异步任务、幂等与恢复、FFmpeg 进程、外部 AI 服务以及三台设备并行实施
- 设计结论：采用“当前登录用户归属的创作项目 + 核心关系表 + 版本化时间轴 JSON + 素材引用索引 + 统一任务 + 服务端重合成”的完整前后端方案
- 数据库结论：新增表不保存租户或工作区字段，不建立物理外键；使用逻辑关联、索引、事务、Service 校验与一致性巡检保证完整性
- 渲染结论：浏览器实时预览，Java 负责任务编排，服务器使用 FFmpeg、ffprobe 与 ASS 完成最终合成
- AI 结论：AI 只返回可校验、可确认的图片提示词和花字建议，不直接改写时间轴；AI 不可用时手动编辑和视频合成仍可使用
- 协作结论：三个人中的一人先用独立 Codex 任务和独立工作目录生成冻结提交 `C0`；随后三台设备从同一 `C0` 并行开发
- 实施门禁：本规格经用户审阅确认后，才允许使用 `writing-plans` 生成全新实现计划；规格确认不等于允许直接开始业务编码

本规格是创作第 6 步后续设计与实施的唯一模块级权威来源。以下历史材料不得作为新实现的依赖、前置门禁或验收来源：

- 所有名称含 P0 的旧计划及其上下游计划。
- 旧的第 6 步时间轴实现计划。
- `docs/superpowers/specs/2026-08-07-creation-step-6-timeline-editor-design.md`。

上述文件保持历史记录，不修改、不删除，也不得从其中复制未在本规格重新确认的表、字段、接口或状态。本规格仍直接遵守 `RULES.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md` 及前后端编码规范；`C0` 负责将本规格确认的新公共事实前向同步到这些公共文档。

## 2. 最小任务卡

### 2.1 单一目标

将当前静态演示的第 6 步改造成可以真实打开第 5 步数字人底片、编辑并保存时间轴、生成 AI 建议、提交服务端重合成、进入任务中心追踪，并由第 7 步预览和下载成品的完整闭环。

### 2.2 权威输入

- 用户已经逐节确认的本规格决策。
- 当前 `main` 上的七步创作页面、数字人生成、素材、用户认证和任务中心代码事实。
- 本文件第 1 节列出的当前公共规则与契约。
- `C0` 冻结后的数据库迁移、HTTP 契约、跨模块 DTO、时间轴示例和错误码。

### 2.3 必须交付

1. 当前登录用户拥有的创作项目。
2. 可恢复的当前时间轴草稿和不可变历史版本。
3. 图片、画中画、字幕、花字、背景音乐、音效和基础画面特效。
4. 预览区与时间轴双向选择、拖动、缩放、裁剪、循环和属性编辑。
5. AI 图片提示词和 AI 花字建议。
6. 字幕完整性、单行、无标点、无换行和安全区约束。
7. 服务端 FFmpeg 重合成、任务进度、取消、失败和主动重试。
8. 第 5 步来源桥接、第 7 步最新成品读取。
9. 跨账号、素材失效、草稿冲突、任务重复提交和媒体异常等反向场景。

### 2.4 明确不做

- 不在本轮删除现有租户、工作区、旧会话、旧素材或旧数字人任务字段。
- 不重构第 1 至第 4 步，也不把七步所有临时状态一次性持久化。
- 不让发现页生成结果一键进入时间轴。
- 不实现实时多人协同、CRDT、操作日志回放或在线共同编辑。
- 不实现完整作品中心、完整计费账本、全站通知重构或发现页任务迁移。
- 不实现实际 AI 生图；本轮只生成可以复制或后续消费的图片提示词。
- 不引入 DDD、Clean Architecture、Hexagonal Architecture，也不新增 `application`、`port`、`adapter`、`command`、`model` 等平行业务层。
- 不引入新的浏览器端或 Node 服务承担最终视频合成。
- 不修改、补做或依赖任何旧 P0 计划。

### 2.5 完成定义

只有本文件第 20 节全部验收项通过、实际验证证据记录完整、红色任务独立审查完成且没有未关闭的 `[必须修复]` 时，才能声明第 6 步完成。

## 3. 当前实现事实

### 3.1 前端

- `/studio` 仍由 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx` 维护一份页面内 `StudioState`。
- `TimelineStep.tsx` 当前使用静态片段和演示属性，没有后端草稿、版本、任务或真实素材接口。
- 页面刷新会丢失七步临时状态；当前没有稳定的创作项目编号。
- 第 5 步已经存在真实数字人视频任务，能够获得视频任务编号和受授权媒体。
- 第 7 步仍是展示型页面，没有根据创作项目读取最新成品。

### 3.2 后端

- 现有 `av_dh_generation_job` 只承载声音与数字人视频的受限纵链任务，不是长期统一任务事实源。
- 数字人任务保存脚本文本、父声音任务、输出媒体键、媒体类型、大小与摘要，可以作为第 5 步来源桥接输入。
- 当前 `main` 没有创作项目、时间轴草稿、时间轴版本、素材引用索引和第 6 步重合成实现。
- 当前 `main` 没有可供第 6 步直接复用的完整统一任务持久化实现；`C0` 不得假定旧 P0 计划已经落地。
- 现有素材、人物、声音和数字人任务仍按旧租户、工作区或 owner 规则校验；本轮只能通过兼容 Service 使用，不能直接删除这些条件。

## 4. 总体架构与数据流

```text
第 5 步成功数字人视频任务
  -> 来源兼容 Service 校验当前 app_user 归属
  -> 登记或解析稳定基础视频素材、主声音素材和脚本文本快照
  -> 创建当前用户的创作项目及初始时间轴草稿
  -> 第 6 步加载并编辑草稿
  -> 自动保存草稿 / 手动生成历史版本
  -> AI 建议任务只返回建议
  -> 用户确认后写入草稿
  -> 提交重合成时冻结不可变版本并创建统一任务
  -> 媒体 Worker 使用 FFmpeg / ffprobe / ASS 合成
  -> 成品登记到素材系统并回写项目最新成品
  -> 第 7 步按创作项目读取、预览和下载
```

核心原则：

- 创作项目是第 6 步的稳定业务根，不再把 `用户脚本编号 + 脚本版本编号 + 基础视频任务编号` 当作时间轴联合主身份。
- 第 5 步任务编号只用于来源追踪；进入第 6 步后，实际编辑输入是稳定素材编号和冻结的脚本文本快照。
- 当前草稿允许变化；历史版本和合成输入不可变化。
- 浏览器预览和服务器合成消费同一个规范时间轴文档与同一组模板代码。
- 任务状态、进度、失败与结果以后端持久化为准。

## 5. 数据模型

### 5.1 存储策略

采用增强型混合模型：

```text
关系表负责
  用户归属、项目状态、来源、草稿修订、版本、任务、输出、索引和审计

时间轴 JSON 负责
  轨道、元素、时间、层级、坐标、样式、动画、播放和混音参数

素材引用索引负责
  从 JSON 派生素材使用关系，用于授权校验、删除保护、查询和巡检
```

时间轴 JSON 是轨道和元素内容的唯一事实源。素材引用索引是可重建投影，不得演变为第二份可独立编辑的时间轴。

### 5.2 新增关系表

#### `av_creation_asset`

本轮新增当前用户专用的通用创作素材登记表，弥补现有素材 Service 只覆盖人物图片和声音样本、无法承载时间轴通用媒体的缺口。

| 字段 | 含义与约束 |
| --- | --- |
| `asset_id` | 稳定素材主键 |
| `owner_user_id` | 当前 `app_user` 编号，所有读取直接按此列隔离 |
| `asset_type` | `video \| image \| audio` |
| `usage_origin` | `upload \| digital_human_output \| timeline_render_output` |
| `source_ref_id` | 可空；旧数字人任务或根任务编号，仅用于幂等登记和追踪 |
| `asset_status` | `pending \| ready \| failed`；删除只使用 `del_flag`，不维护第二套 `deleted` 状态 |
| `storage_key` | 仅服务端可见的内部对象键或受控存储标识，禁止进入前端 JSON |
| `mime_type` / `size_bytes` / `sha256` | 经真实探测得到的媒体事实 |
| `duration_ms` / `width` / `height` | 按素材类型可空的 ffprobe 或图片解码结果 |
| `has_video_stream` / `has_audio_stream` | 视频媒体流事实 |
| `idempotency_key` / `request_digest` | 上传登记或成品登记幂等身份与规范摘要 |
| `del_flag` | 仅在引用占用检查通过后逻辑删除；`pending/failed` 补偿另按状态处理 |
| `actor_type` / `actor_id` | 首版固定 `app_user` 和当前用户编号，表示业务请求主体 |
| 审计字段 | 继承 `BaseEntity`；不作为归属或 typed actor 事实源 |

#### `av_creation_project`

一行代表一个当前用户的视频创作项目。

| 字段 | 含义与约束 |
| --- | --- |
| `project_id` | 雪花主键，HTTP 以十进制字符串返回 |
| `owner_user_id` | 当前 `app_user` 编号，只能由服务端会话写入 |
| `project_title` | 项目标题，默认从脚本或创建时间生成，可修改 |
| `idempotency_key` | 创建项目幂等键，只能用于当前用户范围内去重 |
| `request_digest` | 服务端对规范创建请求计算的摘要，用于识别同键异请求 |
| `source_type` | 首版固定支持 `digital_human_job`；以后新增来源必须扩展公共契约 |
| `source_ref_id` | 第 5 步成功数字人视频任务编号，只用于追踪 |
| `base_video_asset_id` | 稳定基础视频素材编号 |
| `primary_audio_asset_id` | 可空；独立主配音素材编号。基础视频已含唯一主声音时不得重复混入 |
| `script_text_snapshot` | 创建项目时冻结的原始脚本文本，用于字幕完整性和 AI 文案选择 |
| `canvas_width` / `canvas_height` | 输出画布像素尺寸 |
| `frame_rate` | 输出帧率，使用明确整数或有界小数规则 |
| `duration_ms` | 项目规范总时长，毫秒整数 |
| `project_status` | `editing \| rendering \| ready \| archived` |
| `current_output_asset_id` | 可空；最近一次成功成品素材编号 |
| `del_flag` | 逻辑删除标记 |
| `actor_type` / `actor_id` | 首版固定 `app_user` 和当前用户编号，表示业务请求主体 |
| 审计字段 | 默认遵守 `BaseEntity` 和项目审计规则 |

#### `av_timeline_draft`

每个未删除项目恰好一条当前草稿。

| 字段 | 含义与约束 |
| --- | --- |
| `timeline_draft_id` | 主键 |
| `owner_user_id` | 当前 `app_user` 编号；必须与项目一致并直接用于 SQL 归属过滤 |
| `project_id` | 逻辑关联创作项目；唯一索引保证一项目一草稿 |
| `revision` | 从 1 开始单调增加的乐观并发修订号 |
| `schema_version` | 首版固定 `timeline-1` |
| `content_json` | 完整规范时间轴文档；可使用数据库 JSON 或长文本，C0 统一决定，不允许两种并存 |
| `content_hash` | 服务端对规范 JSON 计算的 SHA-256，用于幂等与诊断 |
| `duration_ms` | 从文档校验得到的规范总时长，便于索引和快速检查 |
| `del_flag` | 随项目逻辑删除，不做数据库级联 |
| `actor_type` / `actor_id` | 首版固定 `app_user` 和当前用户编号 |
| 审计字段 | 默认遵守 `BaseEntity` |

#### `av_timeline_version`

保存不可修改的历史快照。

| 字段 | 含义与约束 |
| --- | --- |
| `timeline_version_id` | 主键 |
| `owner_user_id` | 当前 `app_user` 编号；必须与项目一致并直接用于 SQL 归属过滤 |
| `project_id` | 逻辑关联创作项目 |
| `version_no` | 项目内单调递增，项目编号加版本号唯一 |
| `source_draft_revision` | 生成该版本时的草稿修订号 |
| `version_reason` | `manual_save \| render_input \| restored` |
| `idempotency_key` | 创建该版本的幂等键；自动渲染版本可复用根任务业务操作身份派生 |
| `request_digest` | 服务端对版本创建意图计算的规范摘要 |
| `schema_version` | 时间轴结构版本 |
| `content_json` | 完整不可变时间轴文档 |
| `content_hash` | 服务端规范摘要 |
| `duration_ms` | 版本时长 |
| `source_version_id` | 仅恢复操作可空外使用；指向被恢复的历史版本 |
| `actor_type` / `actor_id` | 创建该不可变版本的业务请求主体，首版固定当前 `app_user` |
| 审计字段 | 只记录创建事实；业务 Service 禁止更新版本内容 |

#### `av_timeline_asset_ref`

保存从草稿或版本 JSON 中提取的素材引用。

| 字段 | 含义与约束 |
| --- | --- |
| `timeline_asset_ref_id` | 主键 |
| `owner_user_id` | 当前 `app_user` 编号；必须与项目、文档和素材一致 |
| `project_id` | 所属项目，便于归属和批量清理 |
| `document_type` | `draft \| version` |
| `document_id` | 草稿编号或版本编号 |
| `element_id` | 时间轴元素稳定编号；基础视频使用保留元素编号 |
| `asset_id` | 逻辑关联现有素材 |
| `usage_type` | `base_video \| primary_audio \| image \| pip_video \| background_music \| sound_effect` |
| `start_ms` / `end_ms` | 引用生效时间范围 |
| `actor_type` / `actor_id` | 生成该投影的业务请求主体，首版固定当前 `app_user` |
| 审计字段 | 继承 `BaseEntity`；草稿投影可重建，版本投影随版本永久保留 |

草稿保存时，在同一事务中更新草稿并重建该草稿的引用索引。版本创建时，在同一事务中插入不可变版本和对应引用索引。

#### `av_timeline_write_receipt`

保存非任务型写操作的幂等结果，避免响应丢失后重复写入或误报冲突：

| 字段 | 含义与约束 |
| --- | --- |
| `timeline_write_receipt_id` | 主键 |
| `owner_user_id` / `project_id` | 当前用户与所属项目 |
| `operation_type` | `draft_save \| manual_version \| version_restore` |
| `idempotency_key` / `request_digest` | 幂等身份和规范请求摘要 |
| `expected_revision` / `result_revision` | 输入预期修订和提交后的草稿修订 |
| `result_version_id` | 手动版本或恢复版本编号；草稿保存为空 |
| `response_summary_json` | 只保存结果修订、内容摘要、版本编号和有界规范化变更，不复制完整时间轴，不保存媒体路径或敏感值 |
| `actor_type` / `actor_id` | 首版固定当前 `app_user` |
| 审计字段 | 继承 `BaseEntity`，记录创建事实；业务禁止更新或删除 |

#### 统一任务表

当前代码没有可供本模块复用的 `av_ai_task` 运行时，因此表、状态和 CAS 不能留给 C0 临场决定。第 6 步前向建立以下统一事实源，不新增只供页面使用的 `av_creation_task`：

##### `av_ai_task`

- `task_id`、`owner_user_id`、`task_type`。
- `resource_type` 首版为 `creation_project`，`resource_id` 保存项目编号，`input_version_id` 对合成任务非空。
- `idempotency_key`、`request_digest`；唯一键固定为 `(owner_user_id, idempotency_key)`，摘要必须覆盖任务类型、资源、草稿修订、输入范围和输出配置。
- `request_schema_version`、`request_payload_json` 保存可恢复的强类型有界输入快照；只允许素材编号、版本编号、文案码点范围和白名单选项，不保存路径、URL、凭据或供应商原始请求。
- `task_status`、`stage`、`progress_percent`、`row_version`、`cancel_requested`。
- `active_execution_id`、`result_asset_id`、`result_schema_version`、有界强类型 `result_payload_json`、安全错误码和有界错误摘要；AI 结果使用 payload，合成结果使用素材编号，均不保存供应商原始响应。
- `quota_policy_version` 和 `estimated_usage`；本轮免费任务仍记录稳定策略版本且预计消耗固定为零。
- 创建、开始、结束时间，`actor_type=app_user`、`actor_id=owner_user_id` 与 `BaseEntity` 审计字段。

##### `av_ai_task_execution`

- `task_execution_id`、`owner_user_id`、`task_id`、项目资源编号、`execution_no`。
- `execution_status` 固定 `queued \| running \| success \| failed \| cancelled`，并保存 `stage`、`progress_percent`、`row_version`、`next_run_at`。
- `lease_owner`、不可预测 `lease_token`、`lease_expires_at` 和取消快照。
- 本次执行输入版本、输出配置摘要、结果素材编号、安全错误码和有界诊断摘要。
- 冻结根任务的 `actor_type/actor_id` 与 `BaseEntity` 审计字段；技术执行者只写 `lease_owner`。
- 唯一键 `(task_id, execution_no)`；根任务只通过 `active_execution_id` 指向当前执行。

##### `av_ai_task_attempt`

- `task_attempt_id`、`owner_user_id`、`task_id`、`task_execution_id`、`attempt_no`。
- `attempt_status` 固定 `running \| success \| failed \| cancelled \| abandoned`，并保存 `row_version`、领取 Worker 标识、租约令牌摘要、开始/结束时间、退出分类和安全诊断摘要。
- 冻结根任务的 `actor_type/actor_id` 与 `BaseEntity` 审计字段。
- 唯一键 `(task_execution_id, attempt_no)`；尝试只允许由 `running` 条件推进到一次终态，随后不可变且不可删除，不保存命令全文、路径、凭据、脚本全文或供应商原始响应。

用户主动重试创建新根任务。租约过期或 Worker 恢复始终复用原执行，只在重新实际调用外部 AI 或媒体进程前新增尝试；只有任务类型契约明确批准、次数有上限且原因可审计的内部重试才允许在同一根任务下增加 `execution_no`。旧 `av_dh_generation_job` 本轮保持原状，不与新任务双写，也不伪装成统一任务。首批只实现本规格列出的第 6 步任务类型，但上述表和 Service 不得写死为时间轴专用结构。

### 5.3 不建立物理外键

所有新增表之间以及新增表与旧素材、旧数字人任务之间只建立逻辑关联，不创建 `FOREIGN KEY`，也不使用数据库级联删除。

替代控制必须全部实施：

1. 业务编号列设置正确的非空、唯一和普通索引。
2. 所有写入在 Service 中检查被引用记录存在、未删除、状态合法且属于当前用户。
3. 草稿、引用索引、版本与任务的关联写入使用明确事务。
4. 删除由 Service 编排，禁止直接级联物理删除历史版本和任务。
5. Worker 执行前重新校验项目、版本和素材。
6. 提供只报告、不自动篡改数据的一致性巡检。

没有物理外键不影响 SQL `JOIN`；任何实现不得把“无外键”误解为使用名称、URL 或任意字符串代替稳定编号。

### 5.4 索引最低要求

- `av_creation_asset(owner_user_id, del_flag, asset_status, update_time)`。
- `av_creation_asset(owner_user_id, idempotency_key)` 唯一。
- `av_creation_asset(owner_user_id, usage_origin, source_ref_id)` 唯一；`source_ref_id` 为空的普通上传不参与来源去重。
- `av_creation_project(owner_user_id, del_flag, update_time)`。
- `av_creation_project(owner_user_id, idempotency_key)` 唯一，用于创建项目最终幂等仲裁。
- `av_creation_project(owner_user_id, source_type, source_ref_id)`，用于按来源查找；同一来源是否允许显式复制项目由后续独立操作决定，不依赖该索引去重。
- 分别为 `av_creation_project(owner_user_id, base_video_asset_id, del_flag)`、`av_creation_project(owner_user_id, primary_audio_asset_id, del_flag)` 和 `av_creation_project(owner_user_id, current_output_asset_id, del_flag)` 建立删除保护反查索引。
- `av_timeline_draft(owner_user_id, project_id)` 唯一。
- `av_timeline_version(owner_user_id, project_id, version_no)` 唯一。
- `av_timeline_version(owner_user_id, project_id, idempotency_key)` 唯一。
- `av_timeline_version(owner_user_id, project_id, create_time)`。
- `av_timeline_asset_ref(owner_user_id, document_type, document_id)`。
- `av_timeline_asset_ref(owner_user_id, asset_id, project_id)`，用于删除保护和反向查询。
- `av_timeline_asset_ref(owner_user_id, document_type, document_id, element_id, asset_id, usage_type)` 唯一，阻止重复派生投影。
- `av_timeline_write_receipt(owner_user_id, project_id, idempotency_key)` 唯一；操作类型进入摘要，同键跨操作复用视为冲突。
- `av_ai_task(owner_user_id, idempotency_key)` 唯一；另建 `(owner_user_id, task_status, create_time)` 和 `(owner_user_id, resource_type, resource_id, create_time)`。
- `av_ai_task(owner_user_id, result_asset_id)` 与 `av_ai_task_execution(owner_user_id, result_asset_id)`，用于成品删除保护。
- `av_ai_task_execution(owner_user_id, task_id, execution_no)` 唯一，并按 `(execution_status, next_run_at)`、`(execution_status, lease_expires_at)` 支持领取与恢复。
- `av_ai_task_attempt(owner_user_id, task_execution_id, attempt_no)` 唯一。

不得为未确认的跨项目字幕搜索、花字统计或未来发现页接入提前建索引。

### 5.5 `BaseEntity` 决策

本规格不新增 `BaseEntity` 继承例外：全部新增 Entity 均继承 `BaseEntity`，并显式声明 `ownerUserId` 与 typed actor 字段。`owner_user_id` 才是数据归属事实；`actor_type/actor_id` 才是可区分账号体系的业务请求主体；`create_by/update_by` 只是框架兼容审计，三者不能互相替代。

- 项目、素材和草稿是可变用户数据，包含 `del_flag` 并按 `@TableLogic` 逻辑删除。
- 不可变版本、版本素材引用和写回执只追加、永久保留业务事实，不包含 `del_flag`，Service 禁止更新或删除；这只是逻辑删除字段例外，不是 `BaseEntity` 继承例外。
- 草稿素材引用是可重建投影，保存事务可按当前草稿文档删除后重建；版本素材引用不可修改。
- 根任务、执行任务和尝试因状态机需要条件更新，不包含 `del_flag`；终态后禁止业务改写，任何迟到更新由 CAS 拒绝。后续补充诊断只能新增独立安全审计或新尝试事实，不能改写旧终态。
- `BaseEntity.createBy/updateBy/createDept` 的实际 Java 类型都是 `Long`。C0 冻结 app 专用填充方案：在 `ruoyi-common-mybatis` 新增框架中立的 `AppAuditRequired` 标记和可嵌套、`finally` 必清理的 `AuditFillContext<Long>`；新 Entity 全部带该标记。现有 `InjectionMetaObjectHandler` 对标记实体优先且只读取该上下文，把 `create_by/update_by` 写为当前 `app_user` 的 `Long` 编号、`create_dept` 置空；上下文缺失时直接失败，禁止回退运营端 `LoginHelper` 或 `-1`。未标记的既有运营实体保持现状。
- `ai-video-user-api` 的 app 请求过滤器从经过认证的 `AppLoginHelper` 打开上述上下文，不能读取 BO 自报编号；异步 Worker 在短事务外从根任务冻结的 `actor_id` 打开同一上下文。Service 仍显式写 `actor_type=app_user`、`actor_id=owner_user_id`，MetaObjectHandler 不代替 typed actor 校验。
- Worker 没有登录态，沿用根任务冻结的 `app_user` 请求主体填充业务 actor，另以 `lease_owner`、Worker 标识和尝试记录保存技术执行者；不得把 Worker 伪装成运营用户或从线程上下文猜主体。

C0 必须把上述 app 审计填充方式同步到 `docs/DOMAIN_MODEL.md` 并用同号 `app_user/sys_user` 反向测试证明不会串域；实现阶段不得重新选择另一套审计模型。

## 6. 时间轴 JSON 文档

### 6.1 根结构

首版结构版本固定为 `timeline-1`。示意结构如下，C0 必须提供可被前后端测试直接消费的完整 JSON Schema 或等价强类型契约和样例：

```json
{
  "schemaVersion": "timeline-1",
  "canvas": {
    "width": 1080,
    "height": 1920,
    "frameRate": 30,
    "durationMs": 60000,
    "safeMarginRatio": 0.05
  },
  "tracks": [
    {
      "trackId": "track-main-video",
      "trackType": "main_video",
      "area": "center",
      "order": 0,
      "locked": true,
      "muted": false,
      "elements": []
    }
  ]
}
```

根文档不保存 `ownerUserId`、租户、工作区、项目状态、任务状态、密钥、真实文件路径、供应商配置或临时访问 URL。

### 6.2 轨道顺序

时间轴纵向排列，默认顺序为：

```text
上方画面区域
  花字轨道
  字幕轨道
  画面特效轨道
  图片与画中画轨道，可按重叠派生多个子轨

中间固定区域
  主视频轨道，始终锁定在中间

下方声音区域
  主配音轨道
  背景音乐轨道
  音效轨道，可按重叠派生多个子轨
```

轨道稳定类型为：

- `fancy_text`
- `subtitle`
- `visual_effect`
- `image_overlay`
- `pip_video`
- `main_video`
- `primary_audio`
- `background_music`
- `sound_effect`

主视频轨道只能存在一个。主配音来源只能存在一个有效事实；基础视频已包含主声音时，独立主配音轨道只作为可视化映射，最终合成不得重复叠加同一声音。

### 6.3 元素公共字段

所有元素是带 `elementType` 判别字段的强类型联合结构，至少包含：

- `elementId`：客户端生成的稳定随机标识，项目内唯一。
- `elementType`：稳定英文类型。
- `startMs`：相对项目起点的开始毫秒。
- `endMs`：结束毫秒，必须大于开始且不超过项目总时长。
- `zIndex`：同一画面时间内的明确层级。
- `enabled`：是否参与预览和合成。
- `locked`：是否允许移动或修改。
- `label`：用户可见名称，不作为业务判断值。

画面元素统一使用归一化坐标：

- `xRatio` / `yRatio`：元素中心相对于画布的比例。
- `widthRatio` / `heightRatio`：相对于画布的比例。
- `rotationDeg`：旋转角度。
- `opacity`：0 到 1。

坐标、尺寸、时间和层级均由后端做范围校验。前端视口尺寸变化不能修改文档中的规范坐标。

### 6.4 类型专有字段

#### 图片

- `assetId`
- `fitMode`：`contain \| cover`
- 可选裁剪范围
- 淡入、淡出
- 可选 AI 生成元信息：来源文案范围、采用的提示词和来源任务编号；不得包含供应商密钥或原始响应

#### 画中画视频

- `assetId`
- `sourceDurationMs`
- 源视频裁剪起点
- `loopWhenOverflow`，首版固定默认 `true`
- `audioEnabled`，默认 `false`
- 四角快捷位置只是编辑命令，最终仍落为规范坐标和安全边距

当元素持续时间超过可用源视频时长，预览与合成都从裁剪起点循环，直到元素 `endMs`，不能静默冻结最后一帧。

#### 字幕

- `sourceTextSnapshot`：原始文案片段。
- `displayText`：移除标点和换行后的展示文字。
- `sourceStartOffset` / `sourceEndOffset`：在项目脚本文本快照中的字符范围。
- 字体、字号、颜色、背景开关、背景色、描边开关、描边颜色、描边宽度。
- 单行安全区位置和对齐方式。

字幕不能保存省略号、截断标志或隐藏字符。全部字幕按来源范围排序并拼接后，必须与项目脚本文本移除标点和换行后的结果逐字一致。

`timeline-1` 的字幕字符规范由 C0 冻结为跨 Java 与 TypeScript 一致的算法：

1. 原文和展示文字先做 Unicode NFC 规范化。
2. `sourceStartOffset` / `sourceEndOffset` 按 Unicode 码点计数，不按 Java `char` 或 JavaScript UTF-16 code unit 计数；结束位置使用左闭右开范围。
3. `CRLF`、`CR`、`LF` 和其他换行统一移除；普通空白、全角空格和制表符统一为单个普通空格，并去除片段首尾空格。
4. Unicode 标点类别中的字符统一移除。两个数字之间的小数点先转换为中文“点”，例如 `3.5` 规范为 `3点5`，避免去标点后改变读法；英文缩写中的点按普通标点移除。
5. 字母、汉字、数字、组合标记、可见符号和表情保留；服务端登记字体不支持某个保留码点时返回字体或文本校验错误，不能静默删除或替换。
6. 完整性比较使用上述算法得到的规范展示基线，不使用 UTF-16 字符串长度，也不把视觉字形数当作字符数。

C0 必须把该算法、边界样例和码点偏移样例写入唯一契约夹具；任何语言侧实现不得自行添加另一套标点白名单。

#### 花字

- `text`
- `templateCode`
- 字体、颜色、强调色、位置和缩放。
- 入场、持续和出场动画参数，只允许模板登记的有界参数。
- 可选 AI 建议任务编号和建议理由。

首批模板代码固定为：

| 模板代码 | 中文名称 | 语义 |
| --- | --- | --- |
| `keyword_pop` | 关键词弹入 | 放大回弹，强调关键词 |
| `gold_impact` | 金色冲击 | 金色描边与短促闪光，强调数字或成果 |
| `neon_breathe` | 霓虹呼吸 | 渐变发光，适合科技内容 |
| `handwriting_reveal` | 手写描边 | 按方向揭示笔画或遮罩，适合观点与引用 |
| `bubble_bounce` | 气泡弹跳 | 轻量弹跳，适合轻松内容 |
| `title_wipe` | 标题横扫 | 遮罩横向展开，适合章节标题 |

#### 音频

- `assetId`
- 源音频裁剪起点与终点。
- 音量、淡入、淡出。
- 背景音乐可循环。
- 音效允许重叠并派生子轨。
- 背景音乐可选择对主配音自动压低音量。

#### 画面特效

首版只允许契约登记的基础特效和参数，至少覆盖淡入淡出、基础缩放或轻微模糊中的已确认集合。不得接受任意 FFmpeg 表达式、脚本或滤镜字符串。

### 6.5 JSON 约束

- 后端使用强类型 DTO 解析，不使用无约束的 `Map<String, Object>` 直接进入业务 Service。
- 未知 `schemaVersion`、未知轨道类型、未知元素类型、未知花字模板和未知字段必须拒绝。
- 时间统一使用整数毫秒，禁止前后端混用秒和毫秒。
- 规范序列化需要固定字段、稳定数组顺序和统一数字表达，服务端据此计算摘要。
- 文档需要明确最大字节数、最大轨道数和最大元素数；具体上限由 C0 根据短视频目标和测试数据冻结。
- `timeline-1` 后续升级必须提供显式转换器，不能在读取时猜测旧字段。
- 高频查询事实不能长期藏在 JSON；新增查询需求先评估关系投影，不直接写复杂 JSON 路径查询。

## 7. 草稿、版本与并发

### 7.1 自动保存

- 前端编辑操作先进入本地历史栈，实现撤销和重做。
- 自动保存采用延迟合并，不在每次指针移动时请求后端。
- 每次逻辑保存生成一个稳定 `idempotencyKey`；未知网络结果重试必须复用原键，新的用户编辑才生成新键。请求携带 `idempotencyKey`、`expectedRevision` 和完整规范时间轴，不携带服务端摘要。
- 前端先用 C0 的同一字幕规范化算法做即时反馈；后端仍是最终裁定者，并在写事务前使用服务器登记字体的真实度量执行规范化、最小允许字号缩小和连续片段拆分。
- 字幕拆分产生的新元素编号必须由 C0 冻结的确定性算法生成，确保同一保存请求重复规范化得到相同文档和摘要。
- 后端先按当前用户、项目、`draft_save` 和幂等键查询 `av_timeline_write_receipt`：同键异摘要返回 `TIMELINE_IDEMPOTENCY_CONFLICT`；同键同摘要确认原操作已经成功，绝不再次写入。若当前草稿仍是回执结果修订和摘要，则直接返回当前规范时间轴；若草稿已经前进，则返回 `replayed=true`、`superseded=true`、原操作结果修订／摘要和当前修订，不返回旧时间轴，前端立即读取最新草稿且不得覆盖它。
- 未命中回执时，后端将规范化后的完整时间轴作为实际保存内容，在同一事务中校验归属、预期修订、文档、素材和字幕完整性，更新草稿、增加修订号、重建草稿素材引用并插入写回执。
- 并发插入回执唯一键冲突时，当前事务整笔回滚，再读取赢家回执；同摘要返回赢家结果，异摘要返回幂等冲突。
- 保存成功返回新修订号、服务端摘要、规范化后的完整 `timeline` 和有界 `normalizationChanges`。前端必须用返回的规范时间轴替换本地已保存基线；不能继续把提交前的副本当作已保存内容。
- 网络取消不显示为业务失败；明确失败保留本地未保存状态。

### 7.2 冲突

当前端提交修订号与服务端不一致时：

- 后端返回稳定冲突错误，不执行覆盖。
- 前端停止自动重放，显示“重新加载”和“另存为手动版本前复制本地内容”的明确选择。
- 首版不做字段级自动合并，也不做两页面实时协同。

### 7.3 历史版本

- 用户主动保存必须携带 `expectedRevision` 和幂等键，只能从该精确草稿修订产生 `manual_save` 版本；版本、版本素材引用和写回执在一个事务中提交。
- 提交重合成前产生 `render_input` 版本。
- 恢复历史版本时，旧版本保持不变；恢复请求携带 `expectedRevision` 和幂等键。后端在一个事务中校验预期草稿修订，把来源版本内容复制到草稿、增加修订号、重建草稿引用、创建带 `source_version_id` 的 `restored` 不可变版本，并插入写回执。
- 手动保存和恢复都先查写回执，同键同摘要返回不可变结果版本及原操作结果修订而不覆盖后来草稿；若草稿已前进，同时标记 `superseded=true` 并要求读取最新草稿。同键异摘要冲突。并发唯一键冲突采用与自动保存相同的整笔回滚和赢家回读规则。
- 历史版本只能按当前用户和项目查询，不能通过版本编号跨项目访问。

### 7.4 重合成一致性

创建重合成任务前，先根据当前用户、任务类型、项目、`expectedRevision`、输出配置和幂等键计算规范 `request_digest`，并按 `(owner_user_id, idempotency_key)` 查询根任务：同键同摘要立即返回原根任务，不能再次创建 `render_input` 版本、执行任务或入队；同键异摘要返回幂等冲突。

未命中时，在同一数据库事务中严格按以下顺序完成：

1. 先插入 `pending` 根任务，以唯一键取得该幂等身份；任何后续失败都回滚该插入。
2. 校验当前用户、项目、预期草稿修订、全部素材以及当前草稿已经通过服务器字体度量和字幕规范化；不合格时拒绝提交并要求先保存规范草稿。
3. 生成不可变 `render_input` 版本及素材引用，并回填根任务 `input_version_id`。
4. 创建唯一首个执行任务；业务提交事务不创建尝试记录。
5. 将根任务和执行任务从创建态条件推进到可领取队列态。

并发插入根任务唯一键冲突时，输家事务必须连同可能生成的版本、引用和执行记录整笔回滚，再读取赢家根任务；同摘要返回赢家，异摘要返回 `TIMELINE_IDEMPOTENCY_CONFLICT`。不得捕获唯一键后继续提交半笔事务。

外部 AI、文件下载、FFmpeg 和 OSS 调用不允许进入该数据库长事务。
`render_input` 一旦创建就不得被 Worker 或任何后台任务改写；合成发现字幕、字体或布局与冻结契约不一致时必须失败，不能暗中缩字、拆片或替换内容。

## 8. 第 5 步来源与旧租户/工作区兼容

### 8.1 新数据归属

- 新增通用创作素材、创作项目、草稿、版本、素材引用、写回执、根任务、执行任务和尝试记录均显式保存 `owner_user_id`，不保存 `tenant_id` 或 `workspace_id`。
- 用户编号只能从当前 `app` 登录会话取得；所有 HTTP BO 禁止出现用户、租户、工作区或 owner 字段。
- 新模块查询、修改、保存、恢复、合成、取消、重试、预览和下载均在 SQL 或 Service 查询条件中包含当前用户归属。
- 所有子表写入同时校验其 `owner_user_id` 与项目、素材、版本和根任务一致；不能只验证父编号存在。

### 8.2 来源兼容 Service

现有 `IAssetService` 只覆盖人物图片和声音样本，不能被规格描述为已存在的通用素材能力。本轮在标准 RuoYi `service/service.impl` 中新增并冻结 `ICreationAssetService`，其公共 DTO 只含 owner-only 的素材编号、类型、状态和必要媒体元数据。该 Service 是时间轴核心访问旧素材和新 `av_creation_asset` 的唯一入口，至少提供以下稳定能力：

1. `resolveOwnedAsset`：按当前账号、素材编号和用途解析 `ready` 素材并验证类型、摘要、状态和归属。
2. `openOwnedMedia`：在服务端打开受控媒体内容或返回受控读取句柄；不向调用者暴露任意路径、URL、对象键、租户或工作区。
3. `registerDigitalHumanOutput`：把已有成功数字人成品和父声音产物幂等登记为 `av_creation_asset`，不要求浏览器下载再上传。
4. `registerPendingRenderOutput` 与 `markRenderOutputReady`：以根任务和规范输出配置幂等登记 `pending` 成品，在真实上传和探测完成后条件推进为 `ready`。
5. `assertAssetDeletable`：统一检查 `av_timeline_asset_ref` 的草稿／不可变版本占用，`av_creation_project` 的基础视频、主配音和当前成品直接引用，以及根任务／执行任务的结果素材直接引用；任一占用存在都拒绝删除并返回不泄露其他对象的安全摘要。
6. `markOutputFailedOrCleanupPending`：配合补偿器处理失败、取消或超时的待登记成品。

从第 5 步初始化时，该兼容 Service 还负责：

1. 使用当前旧会话上下文调用现有数字人 Service，校验成功视频任务属于当前用户。
2. 要求任务类型、成功状态、输出媒体类型、大小与摘要合法。
3. 将已有私有媒体键通过 `registerDigitalHumanOutput` 登记为稳定基础视频素材。
4. 从父声音任务解析唯一主配音素材，避免成品重复混音。
5. 冻结任务中的脚本文本作为项目脚本文本快照。
6. 只向新创作 Service 返回用户编号、素材编号、来源任务编号和媒体元数据。

兼容实现内部可以在调用旧数字人、人物或声音 Service 时短暂使用旧会话中的租户／工作区参数，但不得把它们写入新 DTO、时间轴核心 Entity、Mapper、Service 或 JSON。通用素材上传、图片、画中画视频、音乐、音效、已有数字人成品和合成成品都必须经过该接口登记和校验，不得绕回某个只支持人物或声音的旧 Service。不得新建名为 `adapter` 的业务层。

### 8.3 后续删除边界

以后删除旧租户和工作区代码时，只替换 `ICreationAssetService` 内部的旧来源读取路径；本规格的新表、公共 DTO、API 和时间轴 JSON 不应迁移归属字段。本轮不执行该删除工作。

## 9. HTTP 与权限契约

### 9.1 资源路径

路径前缀统一位于 `/api/studio/creation-projects`，只装配到 `ai-video-user-api`：

```text
POST /api/studio/creation-projects
GET  /api/studio/creation-projects/{projectId}
PUT  /api/studio/creation-projects/{projectId}

GET  /api/studio/creation-projects/{projectId}/timeline-draft
PUT  /api/studio/creation-projects/{projectId}/timeline-draft

GET  /api/studio/creation-projects/{projectId}/timeline-versions
POST /api/studio/creation-projects/{projectId}/timeline-versions
POST /api/studio/creation-projects/{projectId}/timeline-versions/{versionId}/restorations

POST /api/studio/creation-projects/{projectId}/image-prompt-tasks
POST /api/studio/creation-projects/{projectId}/fancy-text-suggestion-tasks
POST /api/studio/creation-projects/{projectId}/subtitle-alignment-tasks
POST /api/studio/creation-projects/{projectId}/render-tasks

GET  /api/studio/creation-projects/{projectId}/outputs/latest
```

时间轴通用素材使用独立用户端资源，同样只装配到 `ai-video-user-api`：

```text
GET    /api/studio/creation-assets
POST   /api/studio/creation-assets
GET    /api/studio/creation-assets/{assetId}
GET    /api/studio/creation-assets/{assetId}/content
DELETE /api/studio/creation-assets/{assetId}
```

上传接口只接受受限 multipart 文件、`usageIntent` 白名单和幂等键，不接受 owner、租户、工作区、对象键、路径或 URL；请求摘要由服务端按当前用户、用途和真实文件 SHA-256 计算。列表按当前用户、素材类型和 `ready` 状态分页；content 支持受控二进制 Range 或短期授权地址。删除必须经过素材引用占用检查。

任务列表、详情、取消和主动重试复用统一 `/api/tasks` 资源，不能在时间轴模块复制第二套任务查询接口。实际动作子资源名称由 C0 与当前任务中心契约统一冻结。

### 9.2 权限

建议的稳定权限为：

- 查询项目、草稿、版本和成品：`aivideo:creation:query`
- 创建项目、保存草稿、保存或恢复版本：`aivideo:creation:edit`
- AI 建议、字幕对齐和视频重合成：`aivideo:creation:generate`
- 任务列表、取消和重试：复用 `aivideo:task:query`、`aivideo:task:cancel` 及统一任务契约确认的重试权限
- 通用创作素材列表／预览：`aivideo:creation-asset:query`
- 上传通用创作素材：`aivideo:creation-asset:upload`
- 删除通用创作素材：`aivideo:creation-asset:delete`

C0 数据库迁移必须同时登记权限并明确当前个人创作者角色的授权结果，避免页面可见但接口持续 403。前端可见性不能替代后端权限和资源归属校验。

### 9.3 初始化项目请求

请求只允许：

- `sourceType`，首版固定 `digital_human_job`
- `sourceId`，第 5 步视频任务编号字符串
- `projectTitle`，可选
- `idempotencyKey`

同一当前用户、来源和相同幂等键的相同请求返回原项目；同键不同规范请求返回幂等冲突。请求不得携带脚本编号、脚本版本编号、用户编号、租户、工作区、媒体路径或媒体 URL。

### 9.4 保存草稿请求

请求精确包含：

- `idempotencyKey`
- `expectedRevision`
- `schemaVersion`
- `timeline`

后端派生并返回：

- `projectId`
- `timelineDraftId`
- `revision`
- `schemaVersion`
- `contentHash`
- `savedAt`
- `timeline`，服务器规范化并实际持久化的完整时间轴
- `normalizationChanges`，有界变更列表，至少能指出受影响元素、字号调整或连续拆分；无变化时返回空数组

幂等重放响应增加 `replayed`。回执结果已被后续编辑超越时再增加 `superseded=true`、`operationResultRevision`、`operationContentHash` 和 `currentRevision`，并省略旧 `timeline`；前端必须读取最新草稿，不能把旧响应覆盖到新修订。

### 9.5 AI 建议请求

图片提示词和花字建议不能接受任意项目外文案。请求携带：

- 当前草稿修订号。
- 项目脚本文本中的来源字符范围，或已存在字幕元素编号集合。
- 有界风格选项。
- `idempotencyKey`。

后端从项目脚本文本快照重新取得真实文本并校验范围。AI 原始输出经过严格结构解析、模板白名单和原文包含关系校验后才写入任务结果。

手动创建版本请求精确携带 `expectedRevision` 与 `idempotencyKey`。恢复版本请求通过路径给出来源版本，并精确携带当前草稿 `expectedRevision` 与 `idempotencyKey`。同一项目内同键同规范意图返回写回执记录的原结果；同键对应不同预期修订、不同来源版本或不同内容时返回 `TIMELINE_IDEMPOTENCY_CONFLICT`。

### 9.6 重合成请求

请求携带：

- `expectedRevision`
- `idempotencyKey`
- 允许的输出配置，例如确认过的分辨率或质量档位

请求不携带任意 FFmpeg 参数、滤镜表达式、文件路径、素材 URL、任务状态或输出素材编号。

### 9.7 统一响应与标识

- 普通 JSON 继续使用 `R<T>`，分页使用 `R<PageResult<T>>`。
- 业务 ID、修订号和大整数以十进制字符串跨 HTTP 边界传输。
- 前端通过模块 Service 和 RuoYi adapter 解析，不在页面散写路径、状态、错误码或 envelope。
- 素材预览和下载继续通过授权接口取得短期 URL 或受控二进制响应，不能在 JSON 中返回内部媒体键。

## 10. 状态、任务和错误

### 10.1 项目状态

| 状态 | 中文展示 | 允许行为 |
| --- | --- | --- |
| `editing` | 编辑中 | 编辑、保存、生成建议、提交合成 |
| `rendering` | 合成中 | 继续编辑草稿、查看任务；不得把进行中任务伪装为成品 |
| `ready` | 已生成 | 编辑、重新合成、预览和下载最新成品 |
| `archived` | 已归档 | 只读或恢复；不能创建新任务 |

合成失败记录在任务中。没有历史成品时项目回到 `editing`；已有历史成品时仍为 `ready`，旧成品继续可用。

### 10.2 统一任务状态

后端保留统一状态：

- `pending`
- `queued`
- `running`
- `success`
- `failed`
- `cancelled`

前端可以把 `pending` 和 `queued` 都展示为“排队中”，但不能删除、改写或本地推导后端状态。终态不得被 Worker、回调或迟到结果改回非终态。

### 10.3 第 6 步任务类型

- `timeline_image_prompt_generate`
- `timeline_fancy_text_suggest`
- `timeline_subtitle_align`
- `timeline_render`

任务中心必须能展示未知但合法的其他任务类型，不能变成只支持第 6 步的列表。

### 10.4 阶段

重合成至少提供下列可展示阶段：

- 等待调度
- 准备与校验素材
- 下载或读取素材
- 生成字幕与花字脚本
- 构建媒体合成计划
- 视频编码
- 校验成品
- 上传并登记成品
- 完成、失败或取消

稳定英文阶段值由 C0 冻结，中文只用于展示。

### 10.5 幂等、取消与重试

- 创建任务只使用 `(owner_user_id, idempotencyKey)` 做数据库最终唯一仲裁；任务类型、资源、修订、输入范围和输出配置进入 `request_digest`。该规则与统一任务公共契约保持一致。
- 同键同规范请求返回原任务；同键不同请求返回冲突。唯一键竞态必须整笔回滚后回读赢家，不能留下多余版本、执行或尝试。
- 用户主动重试或重新合成必须生成新幂等键和新根任务，旧任务保持不可变。
- Worker 租约恢复复用原根任务和原执行；可恢复的同次执行在下一次真实外部调用前产生新尝试，只有任务契约明确批准且次数有上限的内部执行重试才增加 `execution_no`，都不能创建第二条根任务。
- 取消只在后端状态允许且实际执行能力支持时成功；前端不能先行改为已取消。
- 所有根任务、执行和尝试状态更新都携带 `row_version` 或等价版本条件以及允许的来源状态。领取只在一个短事务中以条件更新把到期 `queued` 执行推进为 `running` 并写入新租约；完成本地准备后、实际开始一次外部 AI 调用或首个 ffprobe/FFmpeg 媒体进程前，才在另一短事务中校验有效租约并插入本轮 `running` 尝试。进度、续租、结果和终态更新必须同时匹配执行编号、`running`、当前 `lease_token` 和版本号。
- 根任务只有当前 `active_execution_id` 可以推进；`success/failed/cancelled` 是不可逆终态，过期租约、迟到 Worker 和重复回调的条件更新必须影响 0 行并停止副作用。
- 统一任务响应至少包含字符串任务编号、任务类型、资源类型与编号、输入版本编号、状态、阶段、进度、是否可取消/重试、按任务类型解析的安全结果或结果素材编号、安全错误编号/摘要以及创建、开始、结束时间；不得暴露租约、内部诊断、对象键或供应商原始响应。

### 10.6 额度与通知

- 本轮四类第 6 步任务按明确的免费策略创建。创建前仍通过统一额度策略入口确认该任务类型为免费和当前功能可用，并把稳定 `quota_policy_version` 和 `estimated_usage=0` 冻结在根任务中；不要求余额账户、不冻结、不扣减、不退回额度。这是一条可审计业务策略，不是跳过任务、权限或归属校验。
- 统一任务 Service 应使用免费任务入口或等价稳定能力，不能虚构扣费流水或返回假余额。
- 后续收费属于独立契约变更，必须新增计价、冻结、结算和补偿设计后才能启用。
- 本轮不新增站内通知或推送；任务中心是任务进度和终态的唯一用户入口。以后增加通知不能改变任务事实源。

### 10.7 稳定错误码预留

C0 在确认无冲突后，为本模块冻结 `466xx` 范围：

| 错误码 | 稳定标识 | 语义 |
| --- | --- | --- |
| `46601` | `CREATION_PROJECT_NOT_FOUND` | 项目不存在、已删除或不属于当前用户 |
| `46602` | `CREATION_SOURCE_INVALID` | 第 5 步来源任务无效、未成功或不可访问 |
| `46603` | `TIMELINE_REVISION_CONFLICT` | 草稿修订号冲突 |
| `46604` | `TIMELINE_SCHEMA_UNSUPPORTED` | 时间轴结构版本不支持 |
| `46605` | `TIMELINE_DOCUMENT_INVALID` | 时间轴结构、时间、坐标或元素规则无效 |
| `46606` | `TIMELINE_ASSET_INVALID` | 素材不存在、失效、越权或类型不匹配 |
| `46607` | `TIMELINE_TEXT_INTEGRITY_FAILED` | 字幕少字、顺序错误、换行、标点或溢出校验失败 |
| `46608` | `TIMELINE_VERSION_NOT_FOUND` | 历史版本不存在或不属于项目 |
| `46609` | `TIMELINE_IDEMPOTENCY_CONFLICT` | 同一幂等键对应不同规范请求 |
| `46610` | `TIMELINE_RENDER_UNAVAILABLE` | 媒体合成能力不可用或输入无法合成 |
| `46611` | `TIMELINE_FONT_UNAVAILABLE` | 版本要求的字体未在服务器登记 |
| `46612` | `CREATION_PROJECT_STATE_CONFLICT` | 当前项目状态不允许该操作 |

功能权限不足仍使用统一 403 语义；对象编号越权与不存在统一按 `46601` 或对应资源不存在处理，避免泄露其他用户数据。错误响应不得包含堆栈、SQL、内部路径、完整命令、内部 URL、凭据或临时签名地址。

### 10.8 任务刷新与停止条件

首版使用统一任务接口轮询，不在本轮引入新的 WebSocket 或 SSE 通道：

- 时间轴当前任务详情在页面可见且浏览器获得焦点时每 2 秒刷新；任务中心列表中存在活跃任务时每 5 秒刷新。
- 页面隐藏后退避到 15 秒；连续隐藏 5 分钟后停止网络刷新，重新可见或获得焦点时立即刷新一次并恢复正常间隔。
- 网络错误按 2、4、8、16、30 秒指数退避并加入小幅随机抖动；浏览器离线时暂停，恢复在线后立即刷新。
- 任务进入 `success`、`failed` 或 `cancelled` 后立即停止该任务轮询并做一次最终详情读取。
- 页面卸载、项目切换、退出登录时取消定时器和在途请求；收到 401 时清除创作端会话并停止全部任务刷新，收到 403 或资源不存在时停止对应资源刷新并展示权限或不存在状态。
- 前端通过 React Query 或现有请求层的条件 `refetchInterval` 实现，禁止多个组件为同一任务各自启动轮询。

## 11. 前端页面与交互

### 11.1 布局

页面沿用用户已经确认的结构：

```text
顶部：返回、项目名称、保存状态、撤销、重做、预览合成、导出

上部工作区
  左侧：视频画面预览
  右侧：当前元素信息，包含基础、样式、动画和素材信息

中部横向添加元素区
  图片、画中画、字幕、花字、背景音乐、音效、画面特效

下部时间轴
  时间刻度、播放头、缩放、吸附、拆分
  上方画面轨道
  中间固定主视频轨道
  下方声音轨道
```

添加元素区必须位于画面预览区下方、时间轴上方，不能放入页面侧边栏。右侧元素信息区属于上部预览工作区，不是全局导航侧边栏。

### 11.2 Ant Design 组件边界

当前用户端使用 Ant Design 6.5.1。规格阶段已经通过官方 CLI 核对 `Splitter`、`Flex`、`Form`、`Upload` 和 `ColorPicker` 的 Ant Design 6 API；实现时仍须针对实际使用组件再次执行官方查询，不能凭记忆编写属性。

建议：

- `Splitter`：上部预览与右侧属性区、上部与时间轴区域的可调整布局。
- `Flex`、`Space`：工具带和属性分组。
- `Form`、`InputNumber`、`Select`、`Slider`、`Switch`、`ColorPicker`：元素属性。
- `Upload`：受控上传入口，实际网络行为走现有素材上传 Service。
- `Button`、`Tooltip`、`Dropdown`、`Modal`、`Progress`、`Alert`、`Result`、`Spin`、`Empty`：操作、反馈和页面状态。
- 时间轴刻度、轨道、片段和画面操作框是领域组件，不能强行用表格或列表组件代替。

样式使用项目 Token 和组件公开语义，禁止查询 Ant Design 内部 DOM 或依赖内部类名。

### 11.3 选择与联动

- 预览画面和时间轴共享唯一 `selectedElementId`。
- 点击任一处元素，另一处同步高亮，右侧立即显示该元素信息。
- 未选择元素时，右侧显示画布、分辨率、时长和项目基础信息。
- 选择元素后，键盘删除、复制、撤销和重做作用于明确选中对象。
- 选中态不能只依赖颜色，必须同时具备边框、标识和可访问名称。

### 11.4 时间轴操作

- 拖动片段主体改变开始时间，拖动左右把手改变持续时间。
- 吸附播放头、相邻片段边缘和主视频首尾；按约定修饰键可临时关闭吸附。
- 支持横向滚动、时间刻度缩放、拆分、复制、删除、锁定和静音。
- 主视频轨道默认锁定，不能拖到其他轨道。
- 同类元素重叠时派生视觉子轨，不改变稳定轨道类型。
- 元素不能越过项目起点或总时长；拖到边界时停止或按明确规则截断，不能产生负时间。
- 画中画持续时间超过源视频时，片段显示循环标识。

### 11.5 画面操作

- 图片、画中画和花字可在视频预览区直接拖动和缩放。
- 显示安全区、中心线、边缘吸附线和画布边界。
- 画中画提供左上、右上、左下、右下四个快捷位置，并保留统一安全边距。
- 快捷位置与手动拖动最终都写入归一化坐标。
- 字幕默认位于下方安全区；位置调整仍不能越出安全区。

### 11.6 添加图片与画中画

- 点击工具带后在弹窗或紧邻工具带的浮层中选择上传、素材库或 AI 提示词，不打开新的页面侧边栏。
- 新元素默认从当前播放头开始。
- 图片使用明确默认时长，用户可在时间轴拉伸。
- 画中画视频默认使用素材可用时长；拉伸超过后自动循环。
- 上传、素材选择、预览授权和删除保护统一通过第 8.2 节新增的 `ICreationAssetService`；其内部可复用合适的旧能力，但页面和时间轴 Service 不直接调用只支持人物或声音的旧素材接口。

### 11.7 字幕

字幕添加流程：

1. 优先消费第 4/5 步已经存在的可信时间信息。
2. 没有可信时间信息时，创建 `timeline_subtitle_align` 任务。
3. 对齐结果只提供时间锚点，展示文字始终来自项目脚本文本快照，不能使用模型转写文本替换原文。
4. 用户可以调整片段时间和样式，但不能无提示地造成文字缺失。

处理规则：

- 按第 6.4 节冻结的 Unicode NFC、码点偏移、标点、空白、小数、表情和组合标记规则生成规范展示基线。
- 按语义与配音时间拆成连续片段。
- 每个片段只显示一行。
- 优先在允许范围内自动缩小字号。
- 仍放不下时拆为前后连续片段，不截断、不隐藏、不加省略号。
- 前端先执行相同算法提供即时反馈；保存时由后端使用服务器登记字体做最终测量、缩小和拆分，并将实际保存的规范时间轴返回前端。
- 提交保存和冻结合成版本前都执行逐码点完整性检查；已经冻结的版本不再发生自动修改。

### 11.8 花字

- AI 返回原文关键词、建议开始时间、持续时间、模板、位置、颜色和理由。
- 用户确认后才加入时间轴。
- 用户也可输入自定义花字并选择模板。
- 花字可以在预览画面自由拖动，在时间轴调整开始和持续时间，在右侧修改文字、字体、颜色、模板和有界动画参数。

### 11.9 保存与页面状态

顶部持续显示：

- 未保存
- 保存中
- 已保存
- 保存失败
- 草稿冲突

页面必须分别处理：

- 初次加载
- 项目不存在
- 空草稿初始化
- 接口失败
- 功能权限不足
- 素材失效
- 字体失效
- 自动保存失败
- 修订冲突
- AI 任务排队、执行、失败、取消和成功
- 重合成任务状态

离开页面前存在未保存修改时必须提示。前端不能因本地预览成功就显示“合成成功”。

## 12. 浏览器预览

- 主视频使用浏览器视频元素播放。
- 图片、字幕和花字使用受控图层或 Canvas/SVG 渲染；画中画视频使用同步视频图层。
- 播放头是唯一规范时间源，所有图层按毫秒时间显示、隐藏和播放。
- 拖动、缩放、样式修改和花字移动即时预览，不请求服务器生成临时视频。
- 预览使用与服务端相同的字体文件清单、模板代码、规范坐标和时间语义。
- 允许浏览器和 libass 的文字抗锯齿存在轻微像素差异；文字内容、位置、安全区、开始结束时间、循环和动画节奏必须一致。
- 预览不能加载时间轴提交的任意 URL，只加载后端授权得到的同源地址或允许的短期素材地址。

## 13. 服务端媒体合成

### 13.1 边界

- Java Service 负责任务、归属、素材、状态和执行编排。
- `ai-video-infra` 的直接技术集成负责 FFmpeg、ffprobe、ASS、外部 AI 和受控进程执行。
- 最终视频合成只在服务器执行，不能依赖用户浏览器持续在线。
- 本地开发可以在同一 Java 进程中运行 Worker；Service 契约应允许以后独立部署 Worker，不改变 HTTP 接口和业务表。

### 13.2 合成流程

1. Worker 原子领取执行任务并取得租约。
2. 读取不可变时间轴版本，重新校验项目与当前用户事实。
3. 通过 `ICreationAssetService` 根据素材编号解析受授权文件，不接受时间轴中的路径或 URL。
4. 使用 ffprobe 校验视频流、音频流、时长、尺寸和编码。
5. 以根任务、输入版本和输出配置摘要，通过 `registerPendingRenderOutput` 幂等取得唯一 `pending` 素材编号和服务端确定性对象键。
6. 生成规范媒体计划与素材清单。
7. 使用专用 ASS 编码器生成字幕和花字脚本。
8. 构建受白名单控制的 FFmpeg 参数数组与滤镜图。
9. 执行合成并解析进度。
10. 使用 ffprobe 校验本地成品流、时长、尺寸与格式并计算内容摘要。
11. 幂等上传到确定性对象键；同键已存在时校验内容摘要，不创建第二个对象。
12. 在短事务中条件推进素材 `pending → ready`，同时把当前执行和根任务写为成功并更新项目最新成品。
13. 清理任务临时目录与可安全清理的缓存；失败、取消或数据库提交失败进入第 13.6 节补偿流程。

默认成品为 MP4，视频 H.264，音频 AAC。输出分辨率、帧率和质量档位由 C0 契约白名单确认。

### 13.3 画面和声音规则

- 图片按时间范围叠加，支持缩放、裁剪、透明度和淡入淡出。
- 画中画视频按元素范围裁剪；超过源时长时循环；默认静音。
- 字幕和花字使用 ASS 与登记字体实现字体、颜色、背景、描边和模板动画。
- 背景音乐支持裁剪、循环、音量和淡入淡出。
- 音效按指定时间混入，允许多个音效重叠。
- 主配音保持唯一；背景音乐自动压低音量是可选参数。
- 画面特效只映射白名单模板，不能让用户输入原始滤镜。
- 字幕和花字 Event 文本只能通过专用 ASS 文本编码器写入：先按 NFC 处理，字幕按第 6.4 节规范化，花字拒绝换行、NUL、C0/C1 控制字符和双向控制字符；随后对反斜杠、左右花括号以及 `\N`、`\n`、`\h`、override tag 和 drawing tag 等 ASS 特殊序列做可逆编码。无法保证渲染文字与规范输入逐码点一致时拒绝保存或合成，不能直接拼接、删除或静默替换。
- 用户文字、字体名称、颜色、模板参数和元素编号均不得进入滤镜图、样式名或 FFmpeg 选项；字体、样式和动画由内部登记代码映射，ASS 文本只进入已转义的 Event 文本字段。

### 13.4 字幕最终检查

服务器实际字体测量、字号缩小和连续拆分必须发生在保存草稿或冻结版本之前，并写入规范时间轴。提交合成前和 Worker 执行时只做最终校验：

- 字幕不能包含换行和应移除的标点。
- 全部展示文字必须与规范原文逐字一致。
- 每一片段必须位于安全宽度内。
- 任一片段仍超宽时返回 `46607 TIMELINE_TEXT_INTEGRITY_FAILED`，不能由 Worker 自动改字号、拆片或继续合成。
- 字体缺失时返回明确错误并停止，不能静默替换字体后继续。

Worker 必须按不可变 `render_input` 生成 ASS；若本机登记字体版本、测量结果或规范算法与版本冻结时不一致，返回 `46611 TIMELINE_FONT_UNAVAILABLE` 或 `46607 TIMELINE_TEXT_INTEGRITY_FAILED` 并保留诊断摘要，不修改版本内容。

### 13.5 进程安全与恢复

- FFmpeg 通过参数数组启动，不通过 shell 拼接完整命令。
- 所有输入来自内部解析后的文件，不接受用户文件路径、协议或滤镜表达式。
- 每个任务使用位于批准根目录下的独立绝对工作目录。读取已有输入时对每一级路径执行符号链接／Windows reparse point 拒绝和真实路径校验，最终 `toRealPath` 必须仍位于批准真实根目录下；创建输出时先对已存在父目录做同样校验，禁止用字符串前缀代替路径边界判断。
- FFmpeg 固定启用 `-nostdin`，显式关闭不需要的网络协议；协议白名单只允许实现确实需要的本地 `file` 和受控 `pipe`。用户输入不得决定协议白名单。
- 设置时长、输出大小、并发数和进程超时上限。
- 取消任务时只终止该任务已登记的进程树。
- Worker 定期续租；过期 Worker 不能写回进度、成品或终态。
- Java 重启后由扫描器识别过期租约并恢复或失败，不依赖内存事件作为唯一事实。
- 失败、取消或超时后清理临时文件；清理失败可巡检重试，但不得把任务改回成功。

### 13.6 成品登记、幂等与补偿

- 成品素材唯一身份由当前用户、根任务、输入版本和输出配置摘要派生；数据库唯一键和确定性对象键共同保证 Worker 重试不会生成第二条素材或第二个对象。
- `pending` 素材登记在上传前用独立短事务完成。上传成功但最终数据库事务失败时，素材保持 `pending`，根任务不能显示成功；重试先检查确定性对象键与摘要，存在且一致就复用并继续提交，不重复上传。
- 如果确定性对象键已存在但摘要不同，任务进入安全失败并报告存储冲突，禁止覆盖未知内容。
- 只有“素材 `ready`、执行成功、根任务成功、项目最新成品”四项在同一条件事务中成功后，前端才可看到成功终态。
- 失败或取消的 `pending` 素材由补偿器在确认没有项目、版本或任务结果引用后删除对象，再条件标记 `failed` 或逻辑删除；对象删除失败保留记录供重试，不把数据库行先删掉。
- 一致性巡检报告超时 `pending` 素材、无素材行对象、无对象素材行、成功任务无 `ready` 成品、同一根任务多个成品和项目指向非 `ready` 成品；自动补偿只处理可证明无引用的待定对象，其余只报告。

## 14. AI 与字幕对齐

### 14.1 图片提示词

输入：当前用户已授权项目、来源文案范围、前后文、画面比例和白名单风格。

输出：结构化提示词建议、可选反向提示词、风格标签和任务编号。提示词只是建议，本轮不调用 ComfyUI 或 RunningHub 生成图片。

### 14.2 花字建议

输出只能包含：

- 能在项目脚本文本快照中定位的关键词。
- 建议时间范围。
- 已登记模板代码。
- 有界位置、颜色和动画强度。
- 简短理由。

模型不得直接输出完整时间轴 JSON。未知模板、项目外文字、越界时间和未知字段必须拒绝。

### 14.3 字幕对齐

- 优先使用上游可信时间戳。
- 缺失时可复用本地语音分析能力得到时间锚点，但显示文字仍以项目脚本文本快照为准。
- 对齐置信不足时返回可编辑的近似时间和明确提示，不能把模型转写的漏字结果当成最终字幕。
- 对齐任务失败不阻止用户手工添加和调整字幕。

### 14.4 AI 失败降级

AI 服务超时、拒绝、格式错误或不可用时：

- 任务进入失败终态并返回安全错误编号。
- 不写入时间轴。
- 不改变项目和草稿修订。
- 上传图片、自定义花字、手工字幕和最终合成仍可继续。

## 15. 后端 RuoYi 落点

### 15.1 模块

```text
ai-video-core
  creation/
    domain/
    dto/
    mapper/
    service/
    service/impl/
  timeline/
    domain/
    dto/
    mapper/
    service/
    service/impl/
  task/
    domain/        av_ai_task、av_ai_task_execution、av_ai_task_attempt 贫血 Entity 与统一枚举
    dto/           C0 冻结的跨模块任务 DTO
    mapper/
    service/
    service/impl/

ai-video-user
  creation/
    controller/
    domain/bo/
    domain/vo/

ai-video-infra
  timeline/
    client/ 或 provider/ 中的 FFmpeg、ffprobe、ASS、AI 直接技术实现
```

具体包名以相邻合规模块为准，但职责必须满足：

- Entity 贫血，仅保存持久化字段和简单判断。
- Controller 只处理入参、登录主体、权限、防重、日志和 `R<T>`。
- Service 负责归属、事务、状态、幂等、版本、引用索引和任务编排。
- `ICreationAssetService` 负责新通用创作素材登记以及旧数字人/人物/声音能力的兼容读取；当前代码中不存在可直接替代它的通用 `IAssetService`。
- Mapper 负责数据访问，归属条件进入 SQL 或条件构造。
- 稳定跨模块 Service 数据使用 `ai-video-core` 对应聚合平级 `dto` 下的 `*DTO`。
- 供应商原始请求响应只留在 `ai-video-infra` 的直接集成边界。
- 禁止创建 `application`、`port`、`adapter`、`command`、`model`、`aggregate` 或 `repository` 业务层。

### 15.2 事务边界

以下操作使用短数据库事务：

- 项目与初始草稿创建。
- 草稿、草稿素材引用与 `draft_save` 写回执更新。
- `manual_save` 版本、版本素材引用与写回执创建。
- 恢复版本时的草稿、修订号、草稿素材引用、`restored` 版本、版本素材引用与写回执更新。
- 根任务幂等占位、合成版本、版本素材引用和首个执行创建及入队；业务提交事务不创建尝试，任何唯一键或校验失败整笔回滚。
- 待定成品素材幂等登记。
- 成品素材 `pending → ready`、当前执行终态、根任务终态与项目最新成品的条件写入。

外部 AI、OSS、文件读取、ffprobe 和 FFmpeg 调用全部在事务外执行，并以可恢复状态、事务后唤醒或 Worker 扫描衔接。任何先于数据库终态发生的外部副作用必须有确定性幂等身份和第 13.6 节补偿，不允许用一个跨网络长事务伪装原子性。

### 15.3 一致性巡检

至少报告：

- 没有项目的草稿或版本。
- 一个项目存在多条有效草稿。
- JSON 素材引用与索引不一致。
- 素材不存在、已删除或不属于项目用户。
- 素材删除请求绕过引用占用检查，或有效草稿／版本引用指向已删除素材。
- 任务找不到固定版本。
- 成功任务没有成品素材。
- 项目最新成品与最近成功任务不一致。
- 运行任务租约过期。
- 超时 `pending` 成品、对象与素材登记不一致、同一根任务多个成品。

巡检默认只报告并提供编号，不自动删除、回填或改变终态。

## 16. 安全与内容访问

- 所有接口使用 `app` 创作端登录逻辑和明确 `type = app` 权限注解。
- 前端不能提交 `ownerUserId`、租户、工作区、权限、任务终态或成品编号。
- 项目、草稿、版本、任务、素材、预览和下载逐资源校验当前用户归属。
- 素材访问只允许稳定素材编号，拒绝任意 URL、协议、UNC 路径、盘符路径和路径穿越。
- 素材删除必须先通过 `ICreationAssetService.assertAssetDeletable` 检查草稿／不可变版本投影、项目基础视频／主配音／当前成品以及根任务／执行结果等全部直接引用；Controller、旧素材 Service 和后台清理任务都不能绕过。
- 上传校验扩展名、MIME、文件头、真实解码、大小、时长、像素和媒体流。
- 时间轴不保存短期访问 URL、内部对象键、服务器路径或凭据。
- 用户只能选择服务器登记字体和模板，不能上传任意字体、脚本或滤镜。
- ASS、路径和 FFmpeg 进程必须执行第 13.3、13.5 节的专用编码、real-path/reparse point 校验、协议限制和 `-nostdin` 规则；参数数组本身不能替代这些控制。
- 日志、`@Log`、异常、任务结果和测试快照不得包含脚本全文之外的敏感配置、Token、密钥、内部路径、完整命令或临时签名地址。
- 外部 AI 只接收完成当前任务所需的最小文案范围；供应商响应经过结构校验后才进入任务结果。
- 设置用户级与系统级媒体任务并发上限，防止单账号耗尽 CPU、内存、磁盘和进程数。

## 17. 前端状态、类型与 Mock 边界

- 页面依赖方向固定为 Page → 时间轴模块 Service → RuoYi adapter → Umi Request。
- 服务端项目、草稿、版本、任务和素材使用 React Query 或现有请求层管理；不要复制成多份可独立修改的服务端真相。
- 编辑器本地历史栈只保存尚未提交的编辑状态，不裁定后端任务和输出。
- TypeScript ID 一律使用字符串，不使用 `Number`、`parseInt` 或算术处理。
- C0 在 `docs/contracts/creation-timeline/` 建立唯一契约事实源，固定包含：
  - `timeline-1.schema.json`
  - `project.example.json`
  - `timeline-draft.example.json`
  - `timeline-task.example.json`
  - `timeline-errors.example.json`
  - `subtitle-normalization.example.json`
  - `ownership-manifest.md`
- 前端、后端与媒体端可以生成或手写各自语言类型，但不得复制后再维护另一套规范夹具。三端契约测试必须直接读取上述仓库路径中的同一批文件，并验证 schema、固定样例、状态、错误和字幕规范化结果。
- C0 提供严格请求、响应、时间轴和错误样例；前端可基于这些唯一样例先行开发。
- Mock 只存在于测试或明确开发开关，生产默认请求真实接口。
- 前端 adapter 必须拒绝未知顶层字段、未知状态、未知元素类型和非法 ID，不根据中文 `msg` 分支。
- 关闭 Mock 的真实联调是合并到集成分支前的强制门禁。前端生产构建和 CI 必须有自动断言：生产配置未启用 Mock、生产 bundle 不包含时间轴 Mock 注册入口；该断言失败即阻止合并。

## 18. 测试设计

### 18.1 前端

- 时间轴文档解析、序列化和规范坐标。
- 轨道顺序与主视频固定居中。
- 预览和时间轴双向选择。
- 片段拖动、拉伸、吸附、拆分、复制、删除、撤销和重做。
- 花字在画面拖动并回写规范坐标。
- 画中画四角定位、边距与超时长循环。
- 字幕去标点、单行、完整性、自动缩小和连续拆分。
- 自动保存延迟、成功、失败、取消和修订冲突。
- 自动保存响应丢失后使用原幂等键安全重放；手动版本与恢复携带预期修订且不会覆盖后续编辑。
- 加载、空、失败、403、素材失效、字体失效和任务失败状态。
- 活跃任务 2/5 秒刷新、隐藏退避、离线恢复，以及终态、卸载、退出登录、401/403 时停止轮询。
- 前端生产配置关闭 Mock。
- 契约测试直接读取 `docs/contracts/creation-timeline/`，生产构建自动证明未注册时间轴 Mock。

### 18.2 后端 Service、Controller 和 Mapper

- 当前用户项目创建、来源幂等和同键异请求冲突。
- 每张新增表的 `owner_user_id` 与 typed actor 一致；同号 `app_user/sys_user` 不能串域。
- `AppAuditRequired` Entity 的 `Long createBy/updateBy` 只取 app 审计上下文，缺失时 fail closed，异常后 ThreadLocal 清理；未标记运营 Entity 仍使用原填充规则，Worker 使用根任务 actor 且不写 `-1`。
- 跨账号项目、草稿、版本、任务、素材和成品全部拒绝。
- 请求中伪造 owner、tenant 或 workspace 字段因未知字段或契约校验被拒绝。
- 草稿乐观锁、重复保存、版本不可变和恢复复制。
- 草稿、手动版本和恢复写回执的同键同摘要回放、同键异摘要冲突、唯一键竞态整笔回滚。
- JSON 未知结构、越界时间、无效坐标、未知模板和超限文档拒绝。
- 素材引用索引与草稿或版本在同一事务成功或回滚。
- 无物理外键情况下的孤立记录巡检。
- 合成版本和任务原子创建、幂等、终态保护、取消、主动重试和租约恢复。
- 根任务、执行和尝试的唯一键、CAS、租约令牌、迟到 Worker 拒绝，以及任务幂等赢家回读不产生第二版本。
- `ICreationAssetService` 覆盖上传、已有数字人成品、读取、待定成品、ready 推进、引用占用和删除拒绝。
- 素材删除保护覆盖时间轴投影、项目三个直接素材字段以及根任务／执行结果，不允许遗漏任一引用路径。
- 空分页稳定返回 `rows=[]`。
- 两个启动应用分别验证路由隔离，用户端接口不能出现在运营端。
- 契约测试直接读取 `docs/contracts/creation-timeline/`，验证 Java DTO、响应 envelope、JSON Schema 和字幕码点规范与固定样例一致。

### 18.3 媒体与 AI

- 使用固定短视频、音频、图片和字体夹具。
- ffprobe 解析视频流、音频流、时长、尺寸和损坏文件。
- FFmpeg 参数构建使用参数数组，测试文件名和元数据中的注入字符。
- ASS 恶意文本夹具覆盖 `{\\p1}`、override/drawing tag、左右花括号、反斜杠、`\\N`、`\\n`、`\\h`、NUL、C0/C1 和双向控制字符；只能正确显示或明确拒绝，不能改变样式或进入绘图模式。
- 图片、画中画循环、字幕、六种花字、背景音乐、音效和主配音合成。
- 字幕实际字体宽度、安全区和逐字完整性。
- 取消、超时、非零退出、磁盘不足、素材缺失、字体缺失和临时目录清理。
- 符号链接、Windows reparse point、真实路径越界、网络协议和 stdin 均被拒绝或关闭。
- Worker 重复领取、租约过期和迟到结果不能覆盖终态。
- 上传成功后数据库提交失败、相同对象重试、对象摘要冲突、超时 `pending` 和补偿清理；同一根任务最终只有一个 ready 成品。
- AI 固定响应、未知字段、项目外关键词、未知模板和超时降级。
- 单元测试不访问真实外部 AI 网络；真实网络只进入经批准的专项联调。
- 媒体计划与 ASS 测试直接读取 `docs/contracts/creation-timeline/`，不得维护第三份时间轴夹具结构。

### 18.4 端到端

```text
第 5 步成功任务
  -> 创建项目
  -> 刷新恢复草稿
  -> 添加全部元素
  -> 保存版本
  -> 生成 AI 建议并由用户确认
  -> 提交重合成
  -> 任务中心查看进度
  -> 成品成功登记
  -> 第 7 步预览与下载
```

反向链路至少覆盖：

- 未登录、错误客户端、运营端令牌访问用户端接口。
- 缺少功能权限。
- 伪造他人项目、素材、版本、任务和成品编号。
- 过期草稿修订。
- 重复任务提交和同键异请求。
- 媒体损坏、字体缺失、AI 超时、FFmpeg 超时和 Java 重启。
- 成品任务成功但输出缺失时不得显示成功。

### 18.5 环境与验证

- 后端自动化与集成测试只连接本机 MySQL 8 的 `ai_video_test` 和隔离 Redis 逻辑库。
- 测试连接信息和凭据默认读取并提交在用户端 `application-dev.yml` 的标准数据源与 Redis 配置中，管理端保持同值；环境变量仅用于可选覆盖。日志、异常和测试报告不得输出凭据。
- 禁止 Docker、Docker Compose、Testcontainers、WSL、虚拟机、Podman 和 `FLUSHALL`。
- 前端至少运行受影响包的类型检查、测试和构建。
- 后端至少运行相关 Maven 模块测试和构建。
- 媒体至少运行固定夹具集成测试并使用 ffprobe 检查输出。
- 无法运行的外部场景必须记录原因、替代证据和剩余风险，不能描述为已通过。

## 19. 三设备协作、C0 与 C1

### 19.1 阶段 0：独立契约任务

三个人中临时指定一名契约负责人。该负责人在自己的设备和 Codex 账号中建立独立 Codex 任务与独立 Git 工作目录：

```text
任务：step6-contract
分支：codex/step6-contract
```

开始前由集成负责人从可复现的远程起点建立分支：

1. 在干净工作目录执行 `git fetch origin --prune`，把当时 `origin/main` 的完整 40 位提交号记录为 `BASE_SHA`。
2. 从该精确 `BASE_SHA` 创建 `codex/step6-integration` 并立即推送到远程；禁止从某台设备尚未同步的本地 `main` 创建。
3. 从远程 `codex/step6-integration` 创建并推送 `codex/step6-contract`。
4. C0 经审查合入集成分支并推送后，集成负责人再次 fetch，把远程集成分支当前完整 40 位提交号发布为 `C0_SHA`，记录在任务卡或 PR 交付信息中。
5. 三台设备均从远程 fetch 后检出该 `C0_SHA`，先验证 `HEAD == C0_SHA`，再创建各自功能分支；口头说“已经最新”不能替代提交号校验。

该任务只允许修改以下实际路径：

- `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md` 和 `docs/ARCHITECTURE.md`。
- `docs/contracts/creation-timeline/**`，作为 JSON Schema、固定样例、字幕规范化样例和所有权清单的唯一事实源。
- 唯一首版迁移 `docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql`，包含新表、索引、权限和角色授权。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/dto/**`。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/enums/**`。
- 仅跨后端业务与媒体模块使用的两个稳定接口文件：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineMediaRenderService.java` 和 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/ITimelineAiSuggestionService.java`。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/dto/**` 及稳定接口 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/service/ICreationAssetService.java`。
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/**`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/**` 及稳定接口 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`；只冻结第 5.2、10 节已决定的公共字段和方法，不重新设计任务表。

C0 不创建或修改 TypeScript 类型、页面、Service 业务实现、媒体实现、应用配置或现有任务中心代码。`ownership-manifest.md` 必须列出 C0 实际新增的每个 Java 接口文件，消除“公共骨架”与后端实现的路径重叠。

`20260808_01_creation_timeline.sql` 只由契约负责人单写；C0 一旦共享或执行就永久不可改。C1 确需追加 DDL 时只能由同一角色新增 `20260808_02_creation_timeline_c1.sql`。前端、后端和媒体功能分支均不得修改 `_01`、自行占用迁移编号或把迁移复制到其他目录。

完成后提交记为 `C0`，合入 `codex/step6-integration`，契约任务和工作目录保留但暂停。契约负责人随后另开自己的功能 Codex 任务和独立工作目录。

### 19.2 三台设备

三条功能分支都从同一个 `C0` 创建：

| 设备 | 分支 | 独占路径与责任 |
| --- | --- | --- |
| 后端设备 | `codex/step6-backend` | `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/creation/**`、`.../timeline/**`、`.../task/**` 中排除 C0 清单后的 Entity、Mapper、Service、任务实现及其测试；`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/creation/**`、`.../timeline/**`、`.../task/**` 的 BO、VO、Controller、用户端编排及测试；现有数字人包只允许做第 5/7 步服务端桥接，不改前端页面 |
| 前端设备 | `codex/step6-ui` | `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/**`，包括 `index.tsx`、`model.ts`、第 5/6/7 步衔接、`TimelineStep.tsx`、`ExportStep.tsx` 和样式；`src/services/ai-video/creation-timeline/**` 与 `src/services/ai-video/creation-assets/**` 的全部 TypeScript 类型、adapter、API 与 Mock；现有 `src/pages/tasks/**` 和 `src/services/ai-video/tasks/**` 的任务中心接入及其测试 |
| 媒体设备 | `codex/step6-media` | `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/**`、对应 `src/test/**/timeline/**`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`；`ai-video-api/ai-video-user-api/src/main/resources/application*.yml` 中仅 `aivideo.timeline.*` 命名空间的媒体配置；固定媒体二进制夹具与测试 |

表内未写仓库前缀的 `src/...` 是其所在模块内的补充路径；启动模块 `ai-video-api/ai-video-user-api/` 与业务模块目录不同，不得错误放到 `ruoyi-modules/ai-video/` 下。三者不得修改冻结迁移、公共契约、C0 DTO/枚举/接口或其他分支的独占路径。

app 专用 MyBatis 审计填充由后端设备单独负责，前端和媒体设备不得触碰。独占文件冻结为：

- `ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AppAuditRequired.java`
- `ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/audit/AuditFillContext.java`
- `ai-video-api/ruoyi-common/ruoyi-common-mybatis/src/main/java/org/dromara/common/mybatis/handler/InjectionMetaObjectHandler.java`
- `ai-video-api/ai-video-user-api/src/main/java/org/dromara/aivideo/bootstrap/AppMybatisAuditContextFilter.java`
- 上述类的同模块测试文件

若实现核对发现过滤器应落入已有安全配置包而不是 `bootstrap`，必须在 C0 的 `ownership-manifest.md` 先记录唯一替代完整路径；类职责、单写负责人和 fail-closed 行为不变。

根构建文件、模块聚合 POM、`ai-video-user-api/pom.xml`、前端 `package.json`/锁文件、全局路由、共享 adapter 和其他跨模块配置默认只由集成负责人修改；确需提前修改时，必须在 `ownership-manifest.md` 指定唯一负责人后其他两条分支停止触碰。任何任务首次需要表外路径时先发最小变更卡调整所有权，不能先写再解决冲突。

### 19.3 Mock 先行

- 前端直接读取 `docs/contracts/creation-timeline/` 的固定 JSON 样例建立模块 Mock，不等待后端完成，也不把样例复制到前端目录继续维护。
- 后端使用测试目录中的 Mockito 或假媒体 Service，不在生产代码提供“假成功”实现。
- 媒体端直接对 C0 冻结 DTO 和 Service 接口开发，契约测试读取同一目录，使用固定短媒体和测试作用域假进程执行器，不等待 Controller 或数据库。
- 前端生产构建和 CI 的自动门禁必须证明时间轴 Mock 注册关闭；人工删除一个开关或口头确认不算通过。

### 19.4 需要 C1 时

不存在可供三个账号共同“唤醒”的第四个 AI 窗口。默认由原契约负责人处理：

1. 发现者提交一张最小契约变更卡，写明原因、影响字段和受影响分支。
2. 所有受影响的功能开发暂停，禁止各自修改契约；如果原契约负责人正在自己的功能 Codex 任务中实施，必须先停止该功能任务，确保同一人同一时间只承担契约修订。
3. 契约负责人在自己的账号中打开原契约 Codex 任务；若任务丢失，则新建只处理契约的任务，并读取本规格、C0、当前公共契约和变更卡。
4. 从最新集成分支创建 `codex/step6-contract-c1`，不得重写已经共享的 C0。
5. 生成前向提交 C1 并合入集成分支。
6. 三个功能分支同步集成分支后继续。

本需求只允许一次自动契约返工 C1。若 C1 后仍发现需要 C2，全部受影响工作继续暂停并回到项目负责人确认，不能让 AI 自行开启无限契约轮次。AI 对话不是事实源；规格、C0/C1 提交和公共契约才是事实源。

### 19.5 Git 规则

- 所有功能分支只通过 PR 合入 `codex/step6-integration`，不直接合入 `main`。
- 不从旧 P0 分支、旧工作树或旧时间轴分支创建功能分支。
- 不用 cherry-pick 拼装三条开发线。
- 主分支和集成分支禁止强推或 rebase。
- 功能分支 rebase 后只允许对自己的分支使用 `force-with-lease`。
- 只能显式暂存自己负责的路径，禁止 `git add -A`。
- 当前工作区中旧 P0 文档的行尾状态不得被纳入任何新提交。
- `main` 在开发期间前进时，三个功能分支都不得自行同步 `main`。只允许集成负责人在“首个功能 PR 合入前”这个固定检查点 fetch 并至多一次把最新 `origin/main` 合入集成分支；随后三条分支从集成分支同步。该检查点后除紧急安全修复并经项目负责人批准外，不再引入新的 `main` 变更。
- 后端 PR 阶段只要求相关模块编译、Service/Controller/Mapper 测试和测试作用域假媒体实现通过，不能宣称完整应用已可生产启动。媒体 PR 合入后必须立即执行真实媒体 Bean 装配和完整用户端应用启动门禁。
- 合并顺序：C0 → 固定 `main` 同步检查点 → 后端能力 → 媒体实现及完整应用启动 → 前端关闭 Mock 并真实联调 → 完整验证 → 独立审查 → 集成分支合入主分支。
- 后端负责人负责 Maven 模块测试、数据/权限/幂等/事务证据；前端负责人负责类型检查、组件与 adapter 测试、生产构建和 Mock 门禁；媒体负责人负责固定媒体、进程安全和 ffprobe 证据；集成负责人负责空库迁移、双应用启动、第 5→6→7 步真实联调和跨账号反向验收。

### 19.6 三个 AI 同时实施的治理例外

本需求属于红色任务，通常不允许三个实施型 AI 同时参与同一目标。项目负责人已经明确要求“三个人在三台设备用各自的 Codex 编码”，并逐节确认了 C0/C1、独占分支和文件边界；该原话及后续确认构成本需求“三名实施型 AI 同时写各自独占范围”的明确授权，因此本规格记录一次仅限本需求实施阶段的并发例外。

- 原因：三名开发者和三台设备需要在有限时间内并行交付前端、后端与媒体能力。
- 放宽范围：只放宽 C0 后三个独占功能任务的实施并发人数。
- 不放宽：安全、账号归属、迁移恢复、公共契约、媒体异常、测试、独立审查和交付验证。
- 剩余风险：契约冻结遗漏、状态机漂移、跨分支文件冲突、Mock 与真实接口偏差、集成后媒体链路缺口。
- 额外控制：C0 单写、C1 前向变更、独占文件、集成分支、真实联调、跨账号反向测试、迁移恢复验证、媒体专项验证和最终独立审查。
- 终止条件：三条功能分支合入集成分支后例外立即结束；不得自动传播到其他模块或下一轮开发。
- 例外结束后，在任意一个账号中新建一个从未修改本需求代码的只读 Codex 审查任务，并使用干净只读工作目录审查集成分支相对 `BASE_SHA` 的整体差异。该任务不得补写功能，只输出按风险分级的审查结果；三名实施任务彼此互审不能替代这次最终独立审查。
- 必须修复项由原路径负责人修复；独立审查任务只对这些已报告问题做一次定向复核，不开启递归全量审查。

### 19.7 分阶段最小任务卡

| 工作任务 | 风险 | 单一目标与不做范围 | 权威输入与允许影响 | 独立审查 | 最低验证门禁 |
| --- | --- | --- | --- | --- | --- |
| C0 契约与数据库 | 红色 | 冻结新表、索引、权限、HTTP、DTO、状态、错误和样例；不写页面、Service 业务实现或媒体实现 | 本规格与四份公共契约；只改公共文档、迁移、冻结 DTO/接口和夹具 | 规格/契约审查加迁移与数据专项审查 | 文档校验、迁移在空 `ai_video_test` 执行、索引与权限检查、契约样例解析 |
| 后端业务 | 红色 | 完成项目、草稿、版本、引用、任务、权限和第 5/7 步桥接；不改 C0、前端和 FFmpeg 实现 | C0、RuoYi skill、相邻 core/user 模块；只改后端独占路径和测试 | 独立账号归属、数据一致性与任务状态审查 | Maven 测试/构建、跨账号、幂等、修订冲突、事务回滚、任务恢复、双启动路由检查 |
| 前端编辑器 | 黄色；总体集成仍按红色处理 | 完成页面、预览、轨道、属性、保存和任务状态；不改后端、SQL、C0 或生产假成功 | C0 样例、前端规范、Ant Design 6 官方资料；只改 webapp 独占路径 | 独立前端交互、契约和无障碍审查 | 类型检查、组件/Hook/adapter 测试、构建、Mock 关闭、加载/403/冲突/失败场景 |
| 媒体与 AI | 红色 | 完成 FFmpeg、ffprobe、ASS、AI 建议和受控进程；不改 Controller、核心 Entity、迁移或冻结 DTO | C0 媒体 DTO、后端规范和固定媒体夹具；只改 infra 独占路径与测试 | 独立文件/进程/外部服务安全专项审查 | 单元测试、真实 FFmpeg 短夹具、注入、超时、取消、租约、清理、字幕和成品 ffprobe |
| 集成与交付 | 红色 | 将三个分支接入真实接口并验证第 5→6→7 步；不顺带整改旧模块或扩大范围 | 集成分支、C0/C1、本规格与各任务验证证据 | 未参与对应实现的独立完整审查，修复后仅定向复核 | 干净库迁移、前后端完整构建、真实媒体联调、跨账号反向验收、未完成项记录 |

每张实施任务卡交给对应 Codex 时，只引用本表、本规格相关章节、C0/C1 和直接代码位置，不复制完整历史对话或无关旧计划。输出固定为完成项、风险、验证证据和阻塞项。

## 20. 验收清单

### 20.1 正向验收

1. 第 5 步成功数字人视频能够幂等创建当前用户创作项目。
2. 项目新表和 HTTP 请求均不出现租户、工作区或客户端自报用户字段。
3. 第 6 步刷新后草稿、选择和规范时间轴能够恢复。
4. 图片、画中画、字幕、花字、背景音乐、音效和基础画面特效可以真实添加、修改、保存和预览。
5. 花字可以在视频预览区拖动并在时间轴调整持续时间。
6. 画中画可以选四角并保留边距，超过源视频时长后预览和成品都循环。
7. 字幕无标点、无换行、不少字、不溢出安全区，并可修改字体、字号、颜色、背景和描边。
8. 六种花字模板在浏览器预览和最终成品中具有一致的模板语义。
9. AI 可以生成图片提示词和花字建议，用户拒绝建议时不修改时间轴。
10. 手动保存产生不可变版本，恢复版本不修改旧版本。
11. 重合成绑定固定版本，编辑当前草稿不改变正在执行的任务。
12. 任务进入统一任务中心，展示状态、阶段、进度、取消、失败和主动重试。
13. 成功成品登记为素材，第 7 步可以预览和下载最新成品。
14. Java 重启后项目、草稿、版本、任务和成品关联不丢失。
15. 图片、画中画视频、音乐和音效通过当前用户通用创作素材上传、分页选择、受控预览和引用保护真实工作。
16. 草稿保存、手动版本或恢复响应丢失后，原幂等键可以确认原操作结果且不产生重复写入；若草稿已前进则明确标记已被超越并读取最新草稿。
17. 活跃任务按约定刷新，隐藏页面退避，终态、卸载、退出登录和 401/403 后停止轮询。

### 20.2 反向验收

1. 未登录、错误客户端、运营端令牌和缺少权限均被正确拒绝。
2. 修改项目、素材、版本、任务或成品编号不能读取或操作其他用户数据。
3. 请求附带 owner、tenant、workspace、文件路径、任意 URL 或 FFmpeg 表达式时被拒绝。
4. 过期修订不能覆盖服务器新草稿。
5. 同一幂等键不会创建重复项目、版本、任务或成品；同键异请求明确冲突。
6. 有草稿、不可变版本、项目或任务直接引用的素材不能删除；素材已删除、损坏、类型不符、字体缺失或字幕少字时不能进入虚假成功。
7. FFmpeg 超时、被取消、非零退出或 Java 重启后，任务状态和临时文件能够受控收口。
8. AI 返回未知字段、未知模板或原文外关键词时被拒绝且不改草稿。
9. 任务成功但成品素材不存在时，第 7 步不能展示可下载成功状态。
10. 无物理外键情况下，巡检能够报告孤立关系和引用漂移。
11. 恶意 ASS 标签、控制字符、符号链接、Windows reparse point、越界真实路径和 FFmpeg 网络协议不能改变样式、执行绘图、越权读文件或访问网络。
12. 上传成功但数据库终态提交失败时不显示成功；重试复用同一待定素材和对象，补偿器可收口无引用孤儿。

## 21. 公共文档与 C0 必须同步的内容

C0 至少同步：

- `docs/API_CONTRACT.md`：新增通用创作素材、项目、时间轴、版本、AI、重合成和错误契约；明确所有请求不接收归属字段。
- `docs/DOMAIN_MODEL.md`：登记通用创作素材、项目、草稿、版本、写回执、素材引用和任务对象的 `owner_user_id`、typed actor、BaseEntity 填充、JSON 结构、无物理外键、索引、删除和状态规则。
- `docs/ASYNC_TASKS.md`：登记根任务／执行／尝试的精确字段、唯一键、CAS、四类任务、免费策略版本、幂等、租约恢复、取消和主动重试；不得继续保留与本规格冲突的“待 C0 决定”。
- `docs/ARCHITECTURE.md`：登记第 5 → 6 → 7 步数据流、浏览器预览、Java 编排和媒体 Worker 边界。
- `docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql`：表、索引、权限和角色授权；C0 共享后不可修改，C1 需要追加 DDL 时只新增 `20260808_02_creation_timeline_c1.sql`。
- `docs/contracts/creation-timeline/`：唯一 `timeline-1` JSON Schema，以及项目、草稿、任务、错误、字幕规范化和所有权固定样例；前后端与媒体测试直接消费，不复制维护。

C0 不修改旧 P0 计划，不以旧 P0 提交、handoff、addendum 或计划编号作为新功能前置条件。

## 22. 规格自检

- [x] 用户目标、页面入口、完整正向链路和反向场景已明确。
- [x] 第 5 步来源、第 6 步编辑与第 7 步成品读取已闭环。
- [x] 核心关系表、时间轴 JSON、素材引用索引和无物理外键规则已明确。
- [x] 当前用户归属和旧租户/工作区兼容边界已明确。
- [x] 通用创作素材、全部直接引用删除保护、待定成品登记与外部对象补偿已明确。
- [x] 所有新表 `owner_user_id`、typed actor、`Long` BaseEntity app 专用审计填充和 fail-closed 边界已明确。
- [x] 草稿、版本、乐观并发、幂等和终态保护已明确。
- [x] 根任务、执行、尝试、唯一键、CAS、租约、attempt 创建时机和轮询停止条件已明确。
- [x] 图片、画中画、字幕、花字、音乐、音效与特效规则已明确。
- [x] 浏览器预览、FFmpeg/ffprobe/ASS 合成、AI 建议与降级已明确。
- [x] 任务中心、免费额度策略、取消、重试和恢复已明确。
- [x] RuoYi Entity、DTO、Mapper、Service、BO、VO、Controller 与基础设施边界已明确，未引入平行业务层。
- [x] Ant Design 生产工作台组件建议和实现前官方查询要求已明确。
- [x] 加载、空、失败、权限、素材失效、冲突和任务异常状态已明确。
- [x] 三设备 C0/C1 实际操作、Git 规则和并发例外已记录。
- [x] 公共文档同步、测试、验收和独立审查门禁已明确。
- [x] 唯一契约夹具、生产 Mock 自动门禁、ASS 注入、真实路径与协议安全已明确。
- [x] 所有旧 P0 与旧第 6 步材料均被明确排除为未来依赖。
