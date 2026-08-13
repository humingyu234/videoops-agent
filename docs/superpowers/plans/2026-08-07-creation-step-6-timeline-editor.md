# 创作第 6 步时间轴编辑与重合成实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将数字人创作第 6 步从静态演示替换为可保存、可恢复、可审计、可重合成的全功能时间轴编辑器：用户能预览底片，添加图片/画中画/字幕/花字/音频，编辑并保存七轨时间线，发起 AI 辅助任务与免费重合成任务，并在任务中心和第 7 步取得最终视频。

**架构：** 后端在 `ai-video-core` 新增 `timeline` 贫血实体、Mapper、Service 编排与任务处理器，在 `ai-video-user` 提供仅限当前 App 用户和当前工作区的 HTTP 边界；时间轴快照通过强 ETag、业务 revision、幂等回执实现整表替换的并发安全。前端在既有 Umi + Ant Design 工作台中，以受控 reducer 管理仅存于页面内存的编辑态，以严格 API adapter 同步快照、资产、报价和异步任务；第 5 步只负责编辑，第 7 步只消费已确认版本的重合成产物。

**技术栈：** Java 17、Spring Boot、Sa-Token、MyBatis-Plus、RuoYi-Vue-Plus 6.x、MySQL、Redis、FFmpeg/ffprobe、React 19、TypeScript、Umi、Ant Design 6、ProComponents、Vitest、Testing Library。

---

## 已冻结的输入、边界与工作卡

- 设计规格：[`docs/superpowers/specs/2026-08-07-creation-step-6-timeline-editor-design.md`](../specs/2026-08-07-creation-step-6-timeline-editor-design.md)。实现时逐条以该文档第 4、5、7、8、9、10、11、13、14、15、17、18、19 节为验收来源。
- 公共契约来源：[`docs/API_CONTRACT.md`](../../API_CONTRACT.md)、[`docs/DOMAIN_MODEL.md`](../../DOMAIN_MODEL.md)、[`docs/ASYNC_TASKS.md`](../../ASYNC_TASKS.md)。规格与这三份公共契约不一致时，先修改公共契约并审查，再写运行时代码。
- 现有页面入口：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`；现有静态样机：`steps/TimelineStep.tsx`；第 7 步入口：`steps/ExportStep.tsx`。
- 后端模块边界：`ai-video-api/ruoyi-modules/ai-video/ai-video-core` 只放 Entity/DTO/Mapper/Service/任务处理；`ai-video-api/ruoyi-modules/ai-video/ai-video-user` 只放 App 用户 HTTP BO/VO/Controller 与边界适配。
- 不在本计划中：服务端直接生成图片或花字文案、改写底片生成链路、以浏览器本地存储保存完整时间线、将任意资产 URL 或第三方凭据下发给浏览器、绕过工作区/资产/额度/权限校验。

### 风险和协作规则

本模块命中公共接口、数据库结构、资产访问、AI 外部服务、额度、异步任务、FFmpeg 输出和用户授权，整体为**红色高风险**。每个实施任务建立一张最小任务卡，卡中必须保留：目标、只允许修改的文件、规格小节、成功与反向验收、验证命令、实施者、独立审查者。并发仅在任务没有共享文件、共享契约、状态机或迁移顺序依赖时开启；共享契约、SQL 迁移、权限和任务状态机由一名契约 owner 串行合入。

完成每个任务后只复核该任务修改的差异与直接受影响测试；发现与本计划无关的问题登记 backlog，不扩展当前范围。对授权、资产、额度、任务幂等和输出归属执行独立专项审查；该审查者不能是对应实现者。

### 固定术语和 API 类型

| 概念 | 固定值或类型 | 说明 |
| --- | --- | --- |
| 基准轨 | `V1`、`A1` | V1 是不可删除的已合成底片；A1 是其音频。两者固定在轨道中线。 |
| 上层轨 | `S1`、`P1`、`T1` | 字幕、画中画、花字，按该视觉顺序显示于 V1 上方。 |
| 下层轨 | `BGM1`、`SFX1` | 背景音乐、音效，按该视觉顺序显示于 A1 下方。 |
| 元素区间 | `[startMs,endMs)` | 内部毫秒整数、半开区间；显示向最近帧对齐，V1 固定 30 fps。 |
| 编辑身份 | `appUserId + tenantId + workspaceId + draftId` | 所有读写、资产解析、任务查询和输出下载均以此范围约束。 |
| 并发版本 | 强 `ETag` + `timelineRevision` | `PUT` 同时必须带 `If-Match`、`Idempotency-Key`、`expectedTimelineRevision`。 |
| 保存结果 | `TimelineSaveReceiptVo` | 初次保存和同键重放都返回同一 receipt 与临时 ID 映射。 |
| 任务资源 | `timeline_version` 或 `timeline` | AI 候选绑定已保存时间线版本；重合成绑定 timeline。 |

## 执行顺序和阻断门

```text
任务 0 前置基础门禁
        │
        ├─ 未通过 ──> 先执行 P0-C 业务基础计划；本计划不写 timeline 运行代码
        │
        └─ 通过 ──> 任务 1 公共契约与权限迁移
                            │
               ┌────────────┴────────────┐
               │                         │
  任务 2–7 后端模型/HTTP/任务          任务 8–10 前端 adapter/编辑器
               │                         │
               └────────────┬────────────┘
                            │
                任务 11 任务中心与第 7 步
                            │
                 任务 12 联调、审查与交付
```

**禁止跳过任务 0。** 当前仓库已存在 `asset`、`script`、`quota` 等模块，但尚未在 `ai-video-core/src/main/java/org/dromara/aivideo/task/` 发现规格要求的统一任务服务实现。时间轴不得另起一套任务、扣费、工作区或草稿权限模型来绕过这一依赖。

## 任务 0：确认 P0-C 业务基础是否已经落地

**风险：红色。** 这是后续所有后端任务的前置检查，不写运行时代码、不执行迁移。实施者：契约 owner；审查：后端架构审查者；依赖：无。

- [ ] 读取并逐项核对既有基础计划 [`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md`](2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md) 与实际源码，而不是依据计划文件推断已实施。

- [ ] 用以下命令确认统一任务、计费、工作区和草稿能力的实际路径，并把结果记录在任务卡中：

  ```powershell
  rg -n "interface IAiTaskService|class AiTask|timeline_recompose|ChargeableTask|FreeTask" `
    ai-video-api/ruoyi-modules/ai-video
  rg -n "workspaceId|current_workspace|script_draft|draftId" `
    ai-video-api/ruoyi-modules/ai-video
  ```

- [ ] 门禁通过必须同时满足：

  1. `IAiTaskService` 与可重试的统一任务状态机已在运行时代码中存在，支持 `timeline_version` 和 `timeline` 资源归属。
  2. 付费 AI 任务的报价读取、扣减、幂等账本、失败退回/补偿和任务结果回调已存在；免费任务也具有统一入队、状态、重试和可查询能力。
  3. 当前 App 用户的 `tenantId`、`workspaceId`、`appUserId` 与草稿所有权检查存在且可复用，草稿可扩展 `current_timeline_id`。
  4. 任务中心可列出新 operation type，并支持 `studio_timeline` 的详情跳转目标。
  5. 本机集成测试支持 `LocalIntegrationEnvironment`、`-Plocal-integration-test`、独立 `ai_video_test` 数据库及独立 Redis database/prefix。

- [ ] 若任一条件缺失：停止本计划的任务 1–12 的后端实现，改由实施者执行上述 P0-C 基础计划并完成其审查与验收。只允许保留本计划文件和门禁记录，不得创建替代性的 `timeline` 私有队列、私有额度字段或匿名草稿访问。

- [ ] 验证：将 `rg` 命中路径、类名和最小可运行测试类列入门禁记录；独立审查者确认五项均有源码及测试证据。预期：门禁结论清晰为“通过”或“未通过”，不能以“计划中存在”代替源码证据。

## 任务 1：冻结公共契约、权限字典与迁移顺序

**风险：红色。** 文件：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md`（仅在新增处理器注册边界时）、`docs/sql/ai-video/mysql/20260807_01_timeline_editor.sql`。实施者：契约 owner；审查：前端契约审查者 + 后端任务/额度审查者；依赖：任务 0 通过。

- [ ] 先为本任务写失败测试清单，不写 Java/TypeScript：缺 `If-Match`、ETag 冲突、相同幂等键重放、跨工作区 draft、非 V1/A1 基准轨、超过媒体长度、AI 候选过期、重合成同版本重复提交、资产跨用户、额度不足、价格变化。

- [ ] 在 `docs/API_CONTRACT.md` 写入并锁定以下 HTTP 表面：

  ```text
  GET  /api/studio/timeline-template-catalog
  GET  /api/studio/script-drafts/{draftId}/timeline
  PUT  /api/studio/script-drafts/{draftId}/timeline
  POST /api/studio/timeline-recompositions
  POST /api/studio/timeline-image-prompt-generations
  POST /api/studio/timeline-fancy-text-suggestion-generations
  GET  /api/studio/timeline-ai-results/{resultId}
  GET  /api/assets?purpose=timeline_element&mediaType={image|video|audio}
  POST /api/assets/upload-sessions
  POST /api/assets/upload-sessions/{sessionId}/complete
  GET  /api/assets/{assetId}/access-url
  GET  /api/quota/tariffs?operationType={operationType}
  ```

  每个端点写明 permission、actor scope、request/response 字段、分页、ETag、缓存限制、幂等语义、状态码和 `46601`–`46610` 错误数据结构。

- [ ] 在 `docs/DOMAIN_MODEL.md` 注册 9 张时间轴表与关系：`av_timeline`、`av_timeline_element`、`av_timeline_version`、`av_timeline_ai_result`、`av_timeline_save_receipt`、`av_timeline_task_input`、`av_timeline_task_asset_ref`、`av_timeline_recompose_slot`、`av_timeline_output_attempt`；同时登记 `av_script_draft.current_timeline_id`、唯一键、保留策略和回收动作。

- [ ] 在 `docs/ASYNC_TASKS.md` 注册 operation type `timeline_image_prompt_generate`、`timeline_fancy_text_suggest`、`timeline_recompose`，以及资源类型、可重试条件、计费类别、进度阶段、补偿和任务中心跳转规则。不得将外部模型返回视为可直接采用的时间轴元素。

- [ ] 在迁移脚本按现有 `20260806_01_creation_asset_selection.sql` 风格，先创建表和索引，再插入 `aivideo:studio:edit`、资产查看/上传/下载所需权限及 `personal_creator` 默认授权，最后递增权限修订版本。保留幂等 SQL 和可重复执行保护；禁止修改既有历史迁移文件。

- [ ] 用下列强类型作为 HTTP 契约的最小骨架；字段名称、可空性和枚举只允许以设计规格为准：

  ```java
  public record TimelineReplaceBo(
      String expectedTimelineRevision,
      List<TimelineElementBo> elements,
      List<Long> deletedElementIds
  ) {}

  public record TimelineSnapshotVo(
      Long timelineId,
      String timelineRevision,
      TimelineBaseMediaVo baseMedia,
      List<TimelineTrackVo> tracks,
      List<TimelineElementVo> elements
  ) {}
  ```

- [ ] 验证：运行 `& .\scripts\validate-development-standards.ps1` 和 `git diff --check`。独立审查必须逐项对照设计规格的 API 表、错误码表、权限表和数据表；预期是不出现未登记字段、隐式权限或前端自行拼接的错误码。

- [ ] 提交：`docs: 冻结时间轴编辑公共契约`。提交只包含四份文档和本次 SQL 迁移；运行时代码另行提交。

## 任务 2：建立时间轴持久化模型、Mapper 与迁移集成测试

**风险：红色。** 文件：`docs/sql/ai-video/mysql/20260807_01_timeline_editor.sql`、`ai-video-core/src/main/java/org/dromara/aivideo/timeline/{domain,dto,mapper}/**`、对应 `src/test/java/org/dromara/aivideo/timeline/TimelineMigrationIT.java`。实施者：后端数据实施者；审查：数据库/隔离审查者；依赖：任务 1。

- [ ] 先新增 `TimelineMigrationIT`，继承现有 `LocalIntegrationEnvironment` 使用模式，默认读取用户端 `application-dev.yml` 中的共享开发凭据并派生专用测试库，断言九张表、`av_script_draft.current_timeline_id`、索引、唯一约束与 permission seed 存在。

- [ ] 测试环境仅通过 `AI_VIDEO_IT_MYSQL_URL`、`AI_VIDEO_IT_MYSQL_USERNAME`、`AI_VIDEO_IT_MYSQL_PASSWORD`、`AI_VIDEO_IT_REDIS_HOST`、`AI_VIDEO_IT_REDIS_PORT`、`AI_VIDEO_IT_REDIS_DATABASE`、`AI_VIDEO_IT_REDIS_PASSWORD` 提供连接信息；数据库名必须为 `ai_video_test`，Redis database 必须与本地开发隔离。命令使用：

  ```powershell
  Set-Location ai-video-api
  .\mvnw.cmd -Plocal-integration-test -pl ruoyi-modules/ai-video/ai-video-core -am `
    -Dtest=TimelineMigrationIT test
  ```

- [ ] 新增贫血实体 `AvTimeline`、`AvTimelineElement`、`AvTimelineVersion`、`AvTimelineAiResult`、`AvTimelineSaveReceipt`、`AvTimelineTaskInput`、`AvTimelineTaskAssetRef`、`AvTimelineRecomposeSlot`、`AvTimelineOutputAttempt`，使用项目已有 `BaseEntity`、MyBatis-Plus 注解和逻辑删除约定；不向 Entity 塞入业务流程。

- [ ] 为每张实体新增 `Mapper` 与仅需要的 SQL 方法。`AvTimelineMapper` 必须支持按 `(tenant_id, workspace_id, app_user_id, draft_id)` 加锁查询；`AvTimelineSaveReceiptMapper` 必须支持按范围和 `idempotency_key` 查询；`AvTimelineRecomposeSlotMapper` 必须以 `(timeline_id, timeline_version_id)` 唯一约束保证单一活跃重合成。

- [ ] 创建 DTO：`TimelineAggregateDto`、`TimelineElementDto`、`TimelineVersionSnapshotDto`、`TimelineTaskInputDto`、`TimelineTaskAssetRefDto`。聚合序列化只存版本快照 JSON；素材实际访问始终靠 assetId 二次鉴权。

- [ ] 验证：先跑 `TimelineMigrationIT`，再运行 ` .\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dtest=TimelineMigrationIT test`。预期：重复迁移不破坏数据；重复 receipt 和重合成 slot 的插入被唯一键拦截；测试清理仅清理自身 `runId` 范围。

- [ ] 提交：`feat(timeline): 建立时间轴存储模型`。

## 任务 3：实现时间轴聚合、保存事务与版本快照服务

**风险：红色。** 文件：`ai-video-core/src/main/java/org/dromara/aivideo/timeline/service/{ITimelineService,impl/TimelineServiceImpl}.java`、`timeline/dto/**`、`timeline/mapper/**`、`src/test/java/org/dromara/aivideo/timeline/service/TimelineServiceImplTest.java`。实施者：后端业务实施者；审查：并发/数据归属审查者；依赖：任务 2。

- [ ] 先写 `TimelineServiceImplTest`：首次读取初始化 V1/A1、读返回强 ETag 源 revision、合法 replace、V1/A1 被删除、跨轨类型、负时长、越界、同键重放、旧 ETag、更新后的 receipt、临时 ID 映射、版本快照不可变。

- [ ] 建立服务接口，保持 RuoYi Service 编排，而非 Controller 业务逻辑：

  ```java
  public interface ITimelineService {
      TimelineSnapshotDto getOrInitialize(TimelineActor actor, Long draftId);
      TimelineSaveReceiptDto replace(TimelineActor actor, Long draftId,
                                     TimelineReplaceCommand command, String ifMatch,
                                     String idempotencyKey);
      TimelineVersionSnapshotDto requireVersion(TimelineActor actor, Long timelineVersionId);
  }
  ```

- [ ] `getOrInitialize` 必须在当前用户、当前工作区的草稿与已完成底片都合法时创建 V1/A1；取不到底片应返回已登记业务错误，不能创建空 V1。初始化、读取和写入都不得泄漏其它用户的 `draftId` 是否存在。

- [ ] `replace` 在单个事务中按 actor scope 锁定 timeline，验证 `If-Match` 与 `expectedTimelineRevision` 一致，先查 receipt 再校验/更新；成功时复制完整 immutable version snapshot、替换元素、递增 revision、写 receipt 和 tempId→elementId 映射。相同幂等键返回原 receipt；其它请求已推进 revision 时返回 `46602` 并带最新 revision，不做静默覆盖。

- [ ] 将校验拆为纯函数 `TimelineInvariantValidator`：七轨元素类型、固定 V1/A1、`[start,end)`、30 fps 舍入、媒体时长、画中画循环标识、字幕文本非空和样式范围、花字模板/位置范围、assetId 与 track 的媒体类型。纯校验不得读 HTTP 上下文。

- [ ] 为版本快照生成 canonical JSON 与 SHA-256；版本创建后只允许被任务引用，不能被保存覆盖。`timelineRevision` 是业务字符串，ETag 由服务层以强比较值派生，禁止用弱 ETag 或时间戳猜测。

- [ ] 验证：运行 `TimelineServiceImplTest`。预期：两次相同请求只产生一次版本和一次元素写入；并发失败方收到 `46602`；任何不合法基准元素都不会落库。

- [ ] 提交：`feat(timeline): 实现版本化时间线保存`。

## 任务 4：实现 App 用户时间轴 HTTP 边界与错误映射

**风险：红色。** 文件：`ai-video-user/src/main/java/org/dromara/aivideo/user/studio/timeline/{TimelineController.java,bo/**,vo/**,assembler/**}`、`ai-video-user/src/test/java/org/dromara/aivideo/user/studio/timeline/TimelineControllerTest.java`。实施者：后端 HTTP 实施者；审查：App 授权/契约审查者；依赖：任务 3。

- [ ] 先写 `TimelineControllerTest`，覆盖无会话、缺 `aivideo:studio:edit`、缺/格式错误的 `If-Match`、缺/非法 `Idempotency-Key`、跨用户/跨工作区草稿、读取 ETag、首次保存、重放 receipt、`46601`–`46610` 映射。

- [ ] 创建 `@RequestMapping("/api/studio/script-drafts")` 的 `TimelineController`，沿用 `AppLoginHelper` 和 `@SaCheckPermission(value = "aivideo:studio:edit", type = "app")`。Controller 只解析 actor、参数、header、BO/VO 和 `R`；归属、保存、任务编排必须进入 Service。

- [ ] `GET /{draftId}/timeline` 返回 `TimelineSnapshotVo`，响应头 `ETag` 为强 ETag、`Cache-Control: no-store`；`PUT /{draftId}/timeline` 要求 `If-Match` 和 `Idempotency-Key`，返回 `TimelineSaveReceiptVo`。接口不得从 body 接受 `tenantId`、`workspaceId`、`appUserId`。

- [ ] 显式创建 `TimelineApiException` 到既有 `R` 错误结构的映射，数据字段只含能让当前用户安全处理的 `latestTimelineRevision`、`tariffVersion`、`taskId`、`retryAfterSeconds` 等登记字段；不得把栈、SQL、对象存储地址、其它账户 ID 返回浏览器。

- [ ] 使用 `@Validated` 和 BO 约束检查外形，服务层仍执行完整业务校验。`deletedElementIds` 与元素 tempId/真实 ID 的冲突必须拒绝，不能依靠前端正确性。

- [ ] 验证：运行 `TimelineControllerTest` 与现有 App 身份/权限集成测试的直接受影响集合。预期：所有拒绝场景返回登记错误且无数据变更；成功 GET 能被前端读取 ETag。

- [ ] 提交：`feat(timeline): 提供用户端时间轴接口`。

## 任务 5：补齐时间轴资产访问、模板目录与引用保留

**风险：红色。** 文件：既有 `asset/service/IAssetService.java`、`asset/service/impl/AssetServiceImpl.java`、新建或扩展 `ai-video-user/.../user/asset/**`、`ai-video-user/.../user/studio/timeline/TimelineTemplateCatalogController.java`、对应单元/HTTP 测试。实施者：资产实施者；审查：文件访问审查者；依赖：任务 1、任务 3。

- [ ] 先检查 `20260806_01_creation_asset_selection.sql` 与现有用户资产 API。若通用 `/api/assets`、上传 session、完成上传、受控 access URL 已存在，仅扩展 `purpose=timeline_element` 白名单和 VO；若缺失，按现有 `IAssetService` 约定补齐而不重写用户已有的上传改动。

- [ ] 先写测试：同用户同工作区可按 image/video/audio 查询，非法 purpose/媒体类型被拒绝，跨用户 assetId、已删除资产、非 timeline 可用资产、过期 access URL、上传完成前 asset、被时间线版本引用的资产删除。

- [ ] `TimelineAssetResolver` 只接收 actor 与 assetId，解析时核验 tenant、workspace、owner、purpose、媒体类型、状态和可访问时效；服务端版本快照保存 assetId、原始时长/尺寸校验结果、必要的内容 hash，不保存临时签名 URL。

- [ ] 时间轴保存和任务入队时在 `av_timeline_task_asset_ref` 记录引用；资产删除/回收需要查询活动时间线版本和活跃任务引用，按公共契约阻止删除或延迟物理回收。

- [ ] `GET /api/studio/timeline-template-catalog` 返回服务端白名单的花字特效模板、字幕字体、颜色/字号范围、位置枚举和版本号。前端不能用任意 CSS/FFmpeg filter 参数替代该目录。

- [ ] 验证：运行资产 service 测试、新增 asset HTTP 测试、模板目录测试。预期：访问 URL 只对已授权 caller 有效且短时；任何跨作用域 assetId 不能出现在快照、任务输入或下载结果中。

- [ ] 提交：`feat(timeline): 接入受控资产和模板目录`。

## 任务 6：实现 AI 提示词与花字候选任务

**风险：红色。** 文件：`ai-video-core/src/main/java/org/dromara/aivideo/timeline/{service/ITimelineAiService.java,service/impl/TimelineAiServiceImpl.java,task/**}`、`ai-video-user/.../timeline/TimelineAiController.java`、`quota` 扩展、对应 tests。实施者：任务/额度实施者；审查：计费和外部服务审查者；依赖：任务 0、任务 5。

- [ ] 先写服务测试：选中文案片段生成 `timeline_image_prompt_generate`，花字语义建议生成 `timeline_fancy_text_suggest`，报价预览、价格版本变更 `46115`、余额不足 `46114`、任务重放、回调重复、候选过期、他人查询 result。

- [ ] 扩展 quota 公共服务和 `GET /api/quota/tariffs`，读取当前 operation type 对应 tariff、currency、price、tariffVersion、可用余额。前端确认后提交的 `expectedTariffVersion` 必须与服务端原子比较，不能依赖页面缓存。

- [ ] 在 `ITimelineAiService` 编排：加载已保存的 `timelineVersionId`、解析当前 actor 可见文案、建立统一付费任务、写 `av_timeline_ai_result` 初始记录、由统一任务调度器执行外部模型调用、持久化候选。文本模型只能返回候选文案、推荐位置/时长/模板；不直接插入 `av_timeline_element`。

- [ ] 端点必须为：

  ```java
  @PostMapping("/timeline-image-prompt-generations")
  @PostMapping("/timeline-fancy-text-suggestion-generations")
  @GetMapping("/timeline-ai-results/{resultId}")
  ```

  两个 POST 均要求 `aivideo:studio:edit`、幂等键、已保存 `timelineVersionId` 和明确的报价确认；GET 只允许该 actor 的版本结果。

- [ ] 任务回调写结果时使用任务 ID 唯一约束，重复回调无副作用；外部超时、拒绝、非法载荷走统一失败状态和账务补偿，不吞异常为“空候选”。候选应含 `expiresAt` 与 `sourceTimelineVersionId`，版本推进后不允许直接套用旧候选。

- [ ] 验证：unit test 覆盖账务原子性和结果幂等；本机集成测试默认读取用户端 `application-dev.yml`，`AI_VIDEO_IT_*` 环境变量可选覆盖；外部模型用项目现有受控测试 fake，不调用生产服务。预期：账本只扣一次，失败恰当补偿，旧版本/无权限候选不可采用。

- [ ] 提交：`feat(timeline): 接入时间轴 AI 候选任务`。

## 任务 7：实现免费重合成任务与 FFmpeg 处理器

**风险：红色。** 文件：`ai-video-core/src/main/java/org/dromara/aivideo/timeline/{service/ITimelineRecomposeService.java,service/impl/TimelineRecomposeServiceImpl.java,task/TimelineRecomposeTaskHandler.java,task/TimelineFfmpegComposer.java}`、对应 mapper/entity、`ai-video-user/.../TimelineRecomposeController.java`、tests。实施者：媒体任务实施者；审查：任务一致性/输出安全审查者；依赖：任务 0、任务 5、任务 3。

- [ ] 先写失败测试：版本不存在或不归属、版本与 timeline 不一致、相同 timelineVersion 重复提交、仅允许一个 active slot、asset 无权限/已删除、FFmpeg 非零退出、ffprobe 规格不符、输出上传成功但数据库提交失败、回调重复、任务失败后 slot 释放、旧重合成结果不可覆盖新版本。

- [ ] `POST /api/studio/timeline-recompositions` 接收 `timelineVersionId` 与 idempotency key，验证当前 actor、建立 `av_timeline_recompose_slot`、`av_timeline_task_input`、`av_timeline_task_asset_ref`，再调用统一 `IAiTaskService` 创建**免费** `timeline_recompose` 任务。数据库事务提交后才允许调度；调度失败走可恢复的 outbox/统一重试机制。

- [ ] 处理器读取 immutable version snapshot，不读取页面临时元素，也不重新生成 V1/A1。它从受控资产服务取得本地受限读取流/临时工作文件，将图层按 `[startMs,endMs)` 放入 FFmpeg filter graph；视频画中画跨原媒体时长时按快照 `loop=true` 循环。

- [ ] `TimelineFfmpegComposer` 必须显式执行并校验：1080×1920、30 fps、H.264 视频、AAC 音频。字幕文本保留全部字符但不换行；布局器先缩字号，再压缩字距，再截断渲染区域外的视觉扩展而非丢字；背景、描边、花字模板和音轨音量仅允许目录中登记的值。

- [ ] 输出先写 `av_timeline_output_attempt`，上传为受控输出资产，最后在同一状态推进中标记 attempt 成功并更新统一任务结果。上传成功后数据库失败必须执行补偿回收或将资产标记孤儿待回收；失败 attempt 保留诊断码但不暴露命令行、内部目录或存储凭据。

- [ ] 验证：单元测试校验 command/filter graph 结构；使用短基准视频、图片、循环画中画、BGM、SFX、字幕、花字的受控 fixture 做本机集成测试，运行：

  ```powershell
  Set-Location ai-video-api
  .\mvnw.cmd -Plocal-integration-test -pl ruoyi-modules/ai-video/ai-video-core -am `
    -Dtest=TimelineRecompose*IT test
  ```

  预期：ffprobe 精确返回指定编码/分辨率/fps，重复请求只创建一个活跃任务，错误场景无未归属输出资产。

- [ ] 提交：`feat(timeline): 支持版本化视频重合成`。

## 任务 8：建立前端严格 API adapter、类型与查询模型

**风险：黄色。** 文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/timeline/{api.ts,types.ts,adapter.ts,queryKeys.ts,api.test.ts,adapter.test.ts}`、`core/ruoyiAdapter.ts`、`quota/{api.ts,types.ts,api.test.ts}`、`tasks/{api.ts,types.ts,api.test.ts}`。实施者：前端数据实施者；审查：前后端契约审查者；依赖：任务 1 的文档契约，运行联调依赖任务 4/6/7。

- [ ] 先写 adapter 测试：所有 JSON 输入均作运行时 shape 校验；缺 ETag、缺 revision、未知 element type、错误时间字段、错误 error payload 都转为可呈现的 `ApiError`；自定义 header 不覆盖 Authorization/clientid/content-language。

- [ ] 在 `RuoYiAdapter` 增加不破坏现有调用的元数据请求能力，而不是让页面绕过 adapter：

  ```ts
  export interface RuoYiResponse<T> {
    data: T;
    headers: Headers;
  }

  export interface RuoYiAdapter {
    request<T>(options: RuoYiRequestOptions): Promise<T>;
    requestWithMeta?<T>(options: RuoYiRequestOptions): Promise<RuoYiResponse<T>>;
  }
  ```

  运行时 adapter 实现必须让 timeline GET 取得 `ETag`；不允许从 body 伪造 ETag。

- [ ] 创建 `TimelineApi`：`getTemplateCatalog`、`getTimeline`、`replaceTimeline`、`listAssets`、`createUploadSession`、`completeUploadSession`、`getAssetAccessUrl`、`getTariff`、`createImagePromptTask`、`createFancyTextTask`、`getAiResult`、`createRecomposition`。写请求均发送生成的 UUID 幂等键；保存显式发送 `If-Match`。

- [ ] `types.ts` 定义 discriminated union：`TimelineElement`、`SubtitleElement`、`PictureInPictureElement`、`FancyTextElement`、`BgmElement`、`SfxElement`、`TimelineTrackId`、`TimelineSaveReceipt`、`TimelineAiResult`、`TariffQuote`。禁止 `any`、字符串散落比较和把 API 时间直接当秒数。

- [ ] 扩展任务 API/type，使 `timeline_recompose`、两个 AI operation type 和 `studio_timeline` detailTarget 有固定枚举与展示文案；扩展 quota API 获取价格而不影响现有个人额度查询。

- [ ] 验证：

  ```powershell
  Set-Location ai-video-ui/ai-video-webapp
  npx vitest run src/services/ai-video/timeline src/services/ai-video/core/ruoyiAdapter.test.ts `
    src/services/ai-video/quota src/services/ai-video/tasks
  npm run tsc
  ```

  预期：浏览器端不保存完整 timeline、媒体 URL、access token 或幂等回执到 localStorage/sessionStorage。

- [ ] 提交：`feat(timeline-ui): 增加时间轴数据契约`。

## 任务 9：实现编辑器纯状态、时间计算和字幕布局

**风险：黄色。** 文件：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/{types.ts,timelineMath.ts,subtitleLayout.ts,timelineReducer.ts,useTimelineEditor.ts}` 与同目录测试。实施者：前端编辑器实施者；审查：交互/边界审查者；依赖：任务 8。

- [ ] 先写 `timelineMath.test.ts`：毫秒与 30 fps 帧的双向舍入、`[start,end)` 相邻不重叠、拖拽最小时长、缩放、滚动时间换算、入点/出点边界、视频画中画循环标识、插入位置选择。

- [ ] 先写 `timelineReducer.test.ts`：加载快照、选择/取消选择、添加/拖动/裁切/删除、修改字幕样式、移动花字安全区、采用 AI 候选、undo/redo、dirty 标记、保存成功回执、ETag 冲突、任务完成后刷新版本。每次状态变更必须保持 V1/A1 不可修改。

- [ ] 建立编辑器内存状态，不复用 `StudioState.timelineSelected: string` 作为事实来源：

  ```ts
  type TimelineEditorState = {
    snapshot: TimelineSnapshot | null;
    etag: string | null;
    selection: TimelineSelection | null;
    history: TimelineCommand[];
    redo: TimelineCommand[];
    save: 'idle' | 'saving' | 'conflict' | 'error';
  };
  ```

- [ ] `timelineMath` 是所有 canvas、轨道条、inspector 输入的唯一时间换算来源；不得由不同组件各自换算 px、秒、毫秒。所有修改转为 command，只有用户点击“保存”或离开步骤且用户确认保存时调用 `replaceTimeline`。

- [ ] `subtitleLayout` 接收文本、画布安全区、字体、字号、颜色、背景和描边设置，输出单行渲染尺寸和降级字号；它禁止换行与删除字符，溢出时按照规格依次缩字、压缩字距、水平截取视觉区域。

- [ ] 验证：运行同目录测试。预期：所有 seven-track 元素状态可序列化成后端 BO；任何 reducer 路径都不产生 NaN、负长度、V1/A1 删除、未定义轨道或把媒体 URL 写入持久化存储。

- [ ] 提交：`feat(timeline-ui): 实现编辑器状态与时间计算`。

## 任务 10：实现时间轴工作台界面与元素交互

**风险：黄色。** 文件：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/timeline/{TimelineEditor.tsx,TimelinePreviewCanvas.tsx,TimelineElementInspector.tsx,TimelineAddRibbon.tsx,TimelineTracks.tsx,TimelineClip.tsx,TimelineAssetPicker.tsx,TimelineAiAssistant.tsx}`、相关 tests、`steps/TimelineStep.tsx`、`style.css`。实施者：前端界面实施者；审查：可用性/无障碍审查者；依赖：任务 8、任务 9。

- [ ] 在实际编写 Ant Design 组件前运行并记录当前版本组件文档查询；实现只使用查询得到的 API：

  ```powershell
  Set-Location ai-video-ui/ai-video-webapp
  npx antd info --version 6.5.1 --format json
  npx antd doc Drawer --version 6.5.1 --format json
  npx antd doc Form --version 6.5.1 --format json
  ```

- [ ] 先写 `TimelineEditor.test.tsx` 和组件测试：顶部左预览、顶部右 inspector、两者下方的元素添加带、全宽七轨时间线；轨道视图必须为 `T1/P1/S1/V1/A1/BGM1/SFX1`，V1/A1 处于中线并固定；小屏只在明确响应式断点切为纵向，不隐藏保存、预览或元素工具。

- [ ] `TimelinePreviewCanvas` 使用视频的受控播放时间驱动预览层；禁止在没有底片的情况下伪造成功预览。画中画位置通过左上/右上/左下/右下及固定安全边距计算；花字在预览区可拖拽，拖拽结果经过安全区 clamp 后写回 reducer。

- [ ] `TimelineAddRibbon` 只放“图片/视频画中画、字幕、花字、背景音乐、音效、AI 提示词、AI 花字建议”入口。图片/视频入口打开 `TimelineAssetPicker` 并允许上传 session；插入必须由用户明确选择开始位置和时长，默认建议值来自当前 playhead 和媒体元数据。

- [ ] `TimelineTracks` 支持选中、键盘可达的移动/裁切、鼠标拖动和可见 focus ring。视频 PIP 拉长超过原素材时长只修改 timeline duration，预览和服务端重合成均以 `loop=true` 解释；音频不默认循环，除非用户显式选择且契约支持。

- [ ] `TimelineElementInspector` 根据 selection 显示当前元素信息，绝不要求用户跨越长距离寻找操作。字幕表单使用受控值而非 `defaultValue`，可修改字体、字号、颜色、背景色、描边与描边色；花字表单分“基本信息”和“特效模板”；PIP 表单显示资源、位置、入点/出点与循环说明。

- [ ] `TimelineAiAssistant` 在发起付费 AI 前显示 operation、价格、余额、tariffVersion 和明确确认按钮；轮询/刷新候选只能展示，不自动写入 timeline。采用候选时调用 reducer，令它成为待保存改动；候选过期、版本已更新、额度不足和价格变化分别展示登记错误与下一步动作。

- [ ] 验证：运行组件测试和人工键盘检查（Tab、Shift+Tab、Enter、Space、Escape、方向键拖动、中文 IME 输入）。预期：选中轨道元素会更新右侧 inspector；预览拖动花字不离开安全区；字幕不换行且不丢字；所有添加入口位于预览下方和时间线上方。

- [ ] 提交：`feat(timeline-ui): 完成时间轴编辑工作台`。

## 任务 11：把保存、重合成、任务中心与第 7 步串为闭环

**风险：红色。** 文件：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/{index.tsx,model.ts,steps/TimelineStep.tsx,steps/ExportStep.tsx}`、`services/ai-video/tasks/**`、对应 tests；必要时后端 task 查询 VO/adapter。实施者：全栈集成实施者；审查：任务状态/权限审查者；依赖：任务 4、任务 6、任务 7、任务 10。

- [ ] 先写 `TimelineStep.test.tsx`、`ExportStep.test.tsx`、`index.test.tsx`：成功视频底片才能进入第 6 步；未保存状态不能误进入重合成；保存成功带 receipt；重合成提交后显示任务状态；任务成功后第 7 步展示该版本 output；失败/无权限/空任务/加载态均可恢复。

- [ ] 在 `model.ts` 将旧的 `timelineSelected` 从全局草稿状态移除或降为 UI 兼容字段，编辑事实状态交由 `useTimelineEditor`；不在 StudioState 保存完整 elements、asset access URL、ETag 或 AI 结果。

- [ ] `TimelineStep` 负责读取当前 draft/base video、挂载 `TimelineEditor`、在退出时提示未保存改动、保存后刷新 revision/ETag；重合成入口先要求 clean saved snapshot，再调用 `createRecomposition`，成功后跳转任务中心或保留可轮询的任务卡。

- [ ] `ExportStep` 不再显示静态占位信息：按 `timeline_recompose` 任务状态显示加载、队列、运行、失败、无权限、空结果、成功播放与受控下载。下载必须调用资产 `access-url` API，不能拼存储路径；成功结果必须与所选 `timelineVersionId` 匹配。

- [ ] 任务中心详情目标新增 `studio_timeline`，携带仅必要的 draftId/timelineVersionId/taskId；页面恢复时仍由后端 actor scope 重新鉴权，前端路由参数不能授予权限。

- [ ] 验证：

  ```powershell
  Set-Location ai-video-ui/ai-video-webapp
  npx vitest run src/pages/digital-human-studio
  npm run tsc
  npm run biome:lint
  npm run build
  ```

  预期：编辑、保存、任务、导出四阶段可单独刷新恢复；任何阶段的 403、466xx 或网络错误不会把用户跳回登录页或伪造成功。

- [ ] 提交：`feat(studio): 串联时间轴编辑与导出任务`。

## 任务 12：按固定验收矩阵完成联调、专项审查和交付

**风险：红色。** 文件：测试代码、直接受影响的 API/领域/异步任务文档；不新增产品功能。实施者：任务 owner；审查：独立前端状态审查者、独立后端权限/额度/任务审查者；依赖：任务 2–11。

- [ ] 后端单元与 HTTP 验证：

  ```powershell
  Set-Location ai-video-api
  .\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-user -am test
  ```

  聚焦确认：保存幂等、ETag 冲突、actor/workspace 隔离、资产隔离、AI 扣减/补偿、免费重合成 slot、输出 attempt 补偿、错误码和任务状态。

- [ ] 本机集成验证只在本地独立数据库与独立 Redis database/prefix 已配置时运行：

  ```powershell
  Set-Location ai-video-api
  .\mvnw.cmd -Plocal-integration-test -pl ruoyi-modules/ai-video/ai-video-core -am test
  ```

  命令默认读取用户端 `application-dev.yml` 的标准数据源和 Redis 配置，`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖且不得输出其值；若配置缺失或不安全，记录为未执行项及原因，不改用开发/生产库。

- [ ] 前端验证：

  ```powershell
  Set-Location ai-video-ui/ai-video-webapp
  npm test -- --run
  npm run tsc
  npm run biome:lint
  npm run build
  ```

- [ ] 人工验收按规格固定清单执行一次：播放/暂停/定位预览；图片上传和插入位置/时长；四角 PIP 与循环；字幕全字单行、背景/描边；花字拖拽、模板和自定义文本；七轨上下关系；元素选中到 inspector；保存、刷新恢复、并发冲突；AI 报价确认和候选手动采用；重合成提交、任务中心、输出预览和受控下载；加载、空、失败、权限不足和分页。

- [ ] 独立审查 A 只检查前端：类型、adapter header、受控输入、浏览器存储、加载/空/失败/权限态、可达性、时间计算与样式安全区。独立审查 B 只检查后端：Controller 边界、actor/workspace/asset 所有权、权限 seed、ETag/幂等/事务、账务、任务状态、FFmpeg 输出及补偿。两份结论仅列直接证据和修复差异。

- [ ] 修复只针对两份审查中与本变更直接相关的必须项；随后仅复核对应文件和测试。执行 `git diff --check` 与 `& .\scripts\validate-development-standards.ps1`，记录实际命令、结果、未运行项及其原因。

- [ ] 提交建议按逻辑分组保留任务 1–11 的提交；最终只在完整验证通过且工作树中无本模块遗漏文件时创建汇总提交：`feat(timeline): 交付创作第六步时间轴编辑`。不得把用户已有的无关修改加入暂存区。

## 最终验收证据清单

| 领域 | 必须提供的证据 |
| --- | --- |
| 契约 | 三份公共契约与迁移中的端点、字段、错误码、权限、表和任务类型一致。 |
| 并发与数据 | 强 ETag、业务 revision、幂等回执、版本快照、唯一重合成 slot 的自动化测试。 |
| 权限与资产 | 当前 App 用户/工作区/草稿/资产隔离的正反向 HTTP 或集成测试；受控下载 URL 不入库。 |
| 额度与 AI | 价格预览、版本确认、只扣一次、失败补偿、候选不可自动采用的测试。 |
| 重合成 | 1080×1920、30 fps、H.264/AAC 的 ffprobe 输出；失败无孤儿输出资产。 |
| 编辑体验 | 七轨、固定 V1/A1、插入/拖拽/裁切/选择/inspector、字幕与花字要求、键盘交互的组件测试与一次人工验收。 |
| 构建质量 | 后端测试、前端 Vitest/TypeScript/lint/build、文档规范脚本、`git diff --check` 的实际输出。 |

## 交付限制

- 禁止宣称“已完成”而没有上述命令的实际结果。
- 若任务 0 未通过，本计划的交付是明确的前置阻断记录；时间轴运行时代码仍未开始，必须先完成 P0-C 计划。
- 若本机集成环境未配置，只能交付已运行的 unit/HTTP/前端证据及未执行原因，不能用开发库或生产资源补测。
- 新增 API、表、任务类型、权限或 UI 状态时，先回到任务 1 更新公共契约并获得审查；不在实现过程中暗中扩展。
