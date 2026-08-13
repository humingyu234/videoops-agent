# 发现页 RunningHub 单执行配置实现计划

> **第一阶段优先覆盖（2026-08-11）：** 当前执行必须先且只按 `docs/superpowers/plans/2026-08-11-discovery-template-management-phase1.md` 完成模板 CRUD、唯一配置、账号最小管理和用户发现展示。该计划完成前，本计划中的上传、订单、任务执行、RunningHub 请求/轮询/连接测试均不得执行；凡与人工 enable、修改保持状态、测试记录不参与可见性冲突的历史步骤，均仅作后续设计证据，不是第一阶段待办。

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划；每个代码任务先使用 superpowers:test-driven-development，交付前使用 superpowers:verification-before-completion 和 superpowers:requesting-code-review。步骤使用复选框（`- [ ]`）跟踪进度。

**目标：** 完整交付发现页用户端、运营端及两端后端，使每个模板只维护一个当前 RunningHub 执行配置；用户直接填写表单生成，不能看到或指定服务商、执行模式、版本或执行方案；运营端仍可在 RunningHub Workflow 与 RunningHub AI App 之间二选一配置。

**架构：** 公共契约和前向迁移先形成 C0；`ai-video-core` 用贫血 Entity、Mapper 与 `I...Service` 编排模板、账号、订单、资产和统一任务，`ai-video-infra` 负责固定 RunningHub HTTPS API、密钥加密、OSS、文件扫描和不可信结果下载，用户与运营 HTTP 模块只做权限和协议适配。现有 `av_ai_task` 是唯一任务事实源，扩展为 `app_user` 与 `sys_user` 双执行主体；不创建工作流私有任务表、版本表、路由器或故障切换器。

**技术栈：** Java 21、Spring Boot 4.1、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、Redis 7、JUnit 5、JDK HTTP 测试服务器、React 19、Umi Max 4、Ant Design 6、ProComponents、React Query 5、Vitest、TypeScript。

**权威规格：** `docs/superpowers/specs/2026-08-10-discovery-runninghub-single-execution-design.md`。冲突时它优先于 `2026-08-05-discovery-multi-provider-workflow-template-design.md` 和旧发现页实现计划。

---

## 0. 冻结边界、工作方式与并行门禁

### 0.1 不得改变的产品决策

- 用户 wire、缓存、DOM、文案和请求中均不得出现或容忍 `self_hosted_comfyui`、`runninghub_workflow`、`runninghub_ai_app`、`providerKind`、`executionMode`、`executionPlanId`、`templateVersionId`、Workflow ID、Web App ID、节点 ID 或 RunningHub 外部任务 ID。
- 运营端执行模式只有 `runninghub_workflow|runninghub_ai_app`；每个 `(tenant_id, template_id)` 最多一条当前执行配置。
- 不创建模板版本、配置版本、密钥版本、订单执行配置快照、自动路由、自动切换、故障回退或人工重放同一订单。
- `row_revision` 只用于 CAS；未提交任务读取当前配置，已提交任务只依赖保存的账号 ID、模式和外部任务 ID 继续查询。
- RunningHub 不可用时任务失败；提交是否受理未知时收口为 `WORKFLOW_SUBMISSION_UNKNOWN`，绝不自动重复 POST。
- 用户生成与运营测试都进入现有统一任务模型；用户任务免费且 `usage_operation_id=null`，不写额度流水。

### 0.2 代码开始前的固定检查

- [ ] 从包含本规格与本计划的提交创建 `codex/discovery-runninghub-single-execution` 隔离 worktree；先使用 `using-git-worktrees`，不直接在脏工作区实现。
- [ ] 阅读 `AGENTS.md`、`RULES.md`、六份公共契约、RuoYi skill、generator 的 `domain/bo/vo/mapper/service/serviceImpl/controller` 模板和相似模块。
- [ ] 执行 `git status --short --branch`、`git rev-parse HEAD`，记录基线；只精确暂存本任务文件。
- [ ] 前端编码前处理仓库入口缺口：规范路径 `.agents/skills/antd/SKILL.md` 当前不存在，读取现存 `ai-video-ui/ai-video-webapp/.claude/skills/antd/SKILL.md` 并在任务记录中标明缺失；不得凭记忆写 Ant Design API。
- [ ] 重新核对 RunningHub 官方文档。当前官方文档仍采用“提交返回 `taskId`，再查询结果”的任务流；Workflow 为 `POST /task/openapi/create`，AI App 为 `POST /task/openapi/ai-app/run`，二进制上传返回供节点使用的 `fileName`，结果查询为 `POST /openapi/v2/query`。实现不得因为官方提供了 `workflow/webhookUrl/retainSeconds/instanceType/usePersonalQueue` 等可选项就越过本规格白名单。
- [ ] 未获得受控账号和明确费用授权时，只运行本地 Mock Server 契约测试；不得用连接测试或模板测试偷偷创建付费任务。

### 0.3 工作任务与并行顺序

| 工作任务 | 风险 | 开始条件 | 文件所有权 | 审查 |
| --- | --- | --- | --- | --- |
| C0 公共契约、DDL、统一任务边界 | 红色 | 立即 | 公共文档、迁移、共享 task/asset/workflow 类型 | 一名实现者 + 一名独立审查者 |
| B1 通用上传与 RunningHub 后端 | 红色 | C0 合入 | core/infra 的 asset、workflow、task scheduler | 一名实现者 + 一名独立安全审查者 |
| B2 用户与运营 HTTP 后端 | 红色 | B1 稳定 Service | ai-video-user、ai-video-platform、双启动器 | 一名实现者 + 一名独立权限审查者 |
| F1 用户前端 | 红色 | C0 TypeScript wire 冻结；真实闭环等 B2 | ai-video-webapp 发现、上传、订单、任务 | 一名实现者 + 一名独立交互审查者 |
| F2 运营前端 | 红色 | C0 管理 wire 冻结；真实闭环等 B2 | ai-video-platform-ui 新页面/API | 一名实现者 + 一名独立权限审查者 |
| I 联调与验收 | 红色 | B2、F1、F2 完成 | 集成测试、配置、验收证据 | 一次完整审查 + 一次差异复核 |

C0 未合入前不允许功能分支自行发明字段。C0 后 F1、F2 可基于严格 Mock 并行；B1 与 B2 顺序执行，避免多个实现者同时修改 task、asset 和 workflow 共享文件。同一红色工作任务最多两名智能体，禁止递归全量审查。

每个下列任务均按同一微步执行：先增加一个能明确失败的测试方法并运行；只写使其通过的最小实现；重跑定向测试和模块门禁；执行 `git diff --check`；精确暂存；使用给定提交信息提交。一个复选框若覆盖多个场景，实际执行时按每个测试方法拆成约 2 至 5 分钟的红灯/绿灯循环。

## 1. C0：同步公共契约并废止旧实现计划

**风险：** 红色。任何运行时代码开始前的阻塞任务。

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`docs/DOCUMENT_MAP.md`
- 修改：`ai-video-pages.md`
- 修改：`docs/superpowers/plans/2026-08-05-discovery-user-pages.md`
- 创建：`docs/contracts/discovery-runninghub/workflow-form-1.schema.json`
- 创建：`docs/contracts/discovery-runninghub/workflow-form-1.example.json`
- 创建：`docs/contracts/discovery-runninghub/user-wire-forbidden-fields.json`
- 创建：`docs/contracts/discovery-runninghub/ownership-manifest.md`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowContractFixtureTest.java`

- [ ] **先写失败的契约夹具测试。** 固定 `workflow-form-1` 的控件/值类型组合、RFC 8785 哈希输入、`sha256:` 格式、文件数组 wire、状态/阶段矩阵和禁止用户字段。测试必须拒绝额外属性、未知控件、指数数字和 `providerKind/executionMode/executionPlanId/templateVersionId`。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowContractFixtureTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

预期红灯：夹具不存在或仍包含旧多供应商字段，而不是无关编译失败。

- [ ] **改写 HTTP 契约。** 删除用户端 `execution-plans` 与方案级 schema，冻结 `GET /api/discovery/templates/{templateId}/creation-config`、通用上传上下文、无版本订单入参、订单详情、用户任务 wire、同步 `46501/46502/46503/46505/46506/46507/46509/46518` 和异步失败码；登记全部运营 API 与权限。沿用的发现运营端点精确冻结为 `GET/PUT /api/admin/discovery/home`、`GET/POST/PUT/DELETE /api/admin/discovery/banners`、`GET/POST/PUT/DELETE /api/admin/discovery/categories`、`GET/POST/PUT/DELETE /api/admin/discovery/tags` 与 `PUT /api/admin/discovery/recommendations`，单项修改/删除使用路径参数 ID；运营订单资产短链固定为 `GET /api/admin/workflow-orders/{orderId}/assets/{assetId}/access-url?disposition=inline|attachment`，不得用 Sys token 复用 App 的 `/api/assets/{assetId}/access-url`。
- [ ] **登记领域与异步事实。** 明确 11 张新增业务表及 `av_asset`、`av_ai_task`、`av_ai_task_execution`、`av_ai_task_attempt` 四张扩展表，登记统一任务双 actor、`workflow_template_generate|workflow_template_test`、`workflow_order|workflow_template`、提交未知、轮询、取消、结果登记、无快照语义和免费策略 `workflow-free-1`。
- [ ] **登记架构与页面。** 用户点击模板直接进入唯一动态表单；运营模板页只维护一个执行配置；`ruoyi-workflow` 与数字人 `ComfyUiClient` 均不属于本链路。
- [ ] **给旧计划加首屏废止提示。** 明确旧计划中的 provider Radio、版本、方案和伪上传不得执行，只允许复用壳层、严格 adapter、轮询与页面状态经验。
- [ ] **实现夹具测试并运行文档门禁。** `WorkflowContractFixtureTest` 用 Jackson 严格读取三个 JSON，额外扫描公共用户 wire 不得含禁止字段。

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-development-standards.ps1
git diff --check
```

预期：`DEVELOPMENT_STANDARDS_OK`，契约测试通过。

- [ ] 精确暂存上述文件，提交：`docs(发现): 冻结 RunningHub 单执行契约`。

## 2. C0：前向迁移、权限、菜单与开发基线

**风险：** 红色。不得修改 `20260808_01_creation_timeline.sql` 等历史迁移。

**文件：**

- 创建：`docs/sql/ai-video/mysql/20260811_01_discovery_runninghub_single_execution.sql`
- 创建：`docs/sql/ai-video/mysql/20260811_02_discovery_runninghub_admin_menu.sql`
- 修改：`docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql`
- 修改：`docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowMigrationContractTest.java`
- 创建集成测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowRunningHubMigrationIT.java`

- [ ] **先写 SQL 文本红灯。** 断言迁移存在且无物理外键、无版本/快照/自建 ComfyUI 列；包含唯一索引 `uk_av_workflow_execution_config_template (tenant_id,template_id)`、订单幂等、任务外部事实唯一键、CAS revision 和模式互斥 CHECK。
- [ ] **新建发现与工作流事实。** 迁移创建 `av_discovery_banner`、`av_discovery_category`、`av_discovery_tag`、`av_workflow_template`、`av_workflow_execution_config`、`av_runninghub_account`、`av_workflow_order`、`av_workflow_task_execution`、`av_workflow_order_asset`；模板媒体引用平台 `sys_oss` 的授权对象，订单输入/输出只引用私有 `av_asset`。
- [ ] **补通用上传最小事实。** 创建 `av_file_object`、`av_upload_session`，为现有 `av_asset` 前向增加 `file_id/thumbnail_file_id/asset_type/name/source_type/duration_ms/tags_json/reference_count` 等兼容列；保留人物形象/声音现用列和历史数据，禁止破坏式重命名。
- [ ] **扩展三张统一任务表。** `owner_user_id` 对 sys 测试可空；根任务幂等改为 `(actor_type,actor_id,idempotency_key)`；execution/attempt 唯一键分别改为 `(task_id,execution_no)` 与 `(task_execution_id,attempt_no)`；actor CHECK 固定：

```sql
(
  actor_type = 'app_user'
  AND owner_user_id IS NOT NULL
  AND actor_id = owner_user_id
)
OR (
  actor_type = 'sys_user'
  AND owner_user_id IS NULL
)
```

同时允许 `timeline-free-1|workflow-free-1`，扩展成功结果约束但保持四种时间线类型原语义。`av_workflow_task_execution` 用 `resource_type` 与 `order_id` CHECK 区分用户生成和运营测试；跨表 tenant/order/task 关系由 Service 和 IT 双重验证。
- [ ] **加入权限和菜单。** App 权限补 `aivideo:asset:query|upload|download` 并绑定个人创作者角色；Sys 菜单及按钮精确使用规格 10.4 的 20 个权限，动态组件路径精确使用 `aivideo/discovery-home/index`、`aivideo/workflow-template/index`、`aivideo/runninghub-account/index`、`aivideo/workflow-order/index`，详情路由不另造公开菜单。
- [ ] **更新开发初始化守卫与幂等种子。** 初始化脚本只校验迁移已应用并补开发账号权限/菜单，不复制新业务迁移、不删除数据；同步初始化文档的执行顺序和验证 SQL。
- [ ] **运行 SQL 单测与本机迁移 IT。** IT 必须通过 `LocalIntegrationEnvironment` 连接 `ai_video_test`，Redis 固定 DB 15/当前运行前缀；只重置专用测试库，不使用 Docker、WSL、Testcontainers、`FLUSHALL` 或业务库。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowMigrationContractTest' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowRunningHubMigrationIT' verify
```

预期：新表/索引/CHECK 可重复应用，无外键，现有时间线夹具仍可写入。

- [ ] 提交：`feat(发现): 建立 RunningHub 单执行数据基线`。

## 3. C0：把统一任务从时间线专用扩展为双 actor 通用任务

**风险：** 红色。此任务只扩展共享任务，不调用 RunningHub。

**修改文件：**

- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTask.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskExecution.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskAttempt.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskType.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskResourceType.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/enums/AiTaskStage.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskRequestPayloadDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskResultPayloadDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskLeaseDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskSummaryDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskTransactionService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/FreeAiTaskQuotaPolicyServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskTransactionServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskMapper.xml`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/task/AiTaskExecutionMapper.xml`

**创建文件：**

- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskActorDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAccessScopeDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/CreateWorkflowAiTaskDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/WorkflowAiTaskPayloadDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/WorkflowAiTaskResultPayloadDTO.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/WorkflowAiTaskContractTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowRunningHubTaskRuntimeIT.java`

- [ ] **先写双 actor 和回归红灯。** 测试用户订单任务 owner=actor；运营测试 owner 为空、actor=sys；二者幂等隔离；sys 任务不会进入 `pageOwned`；工作流订单任务必须按当前 tenant+owner+workspace 的 order EXISTS 过滤，时间线 `creation_project` 仍保持既有 owner 语义。
- [ ] **新增类型和严格 payload。** `WORKFLOW_TEMPLATE_GENERATE/TEST` 与 `WORKFLOW_ORDER/TEMPLATE`；`WorkflowAiTaskPayloadDTO` 只保存订单或模板测试输入事实、schemaHash 和资源 ID，不保存 Workflow、AI App、账号、映射、密钥或执行配置。
- [ ] **新增专用创建入口而不滥用时间线命令。** `createWorkflowTask(AiTaskActorDTO, CreateWorkflowAiTaskDTO)` 与现有 `createFreeTask(long, CreateFreeAiTaskDTO)` 并存；前者使用 `workflow-free-1`、estimatedUsage=0，并在外层订单/测试事务中以 REQUIRED 传播加入同一事务。
- [ ] **泛化审计上下文与租约。** 租约内部增加 actorType，所有写操作使用 `actorId` 打开 `AuditFillContext`；不删除 `@AppAuditRequired` 的失败关闭能力，不让 sys 请求依赖默认 `-1` 审计填充。
- [ ] **泛化领取与恢复。** 并发计数按 `(actor_type,actor_id)`；过期任务用 actorId 恢复而不是假定 owner 非空；`submitting` 且无外部任务号由工作流执行事实收口为 unknown，不能重新进入创建 POST。
- [ ] **把调度变为按类型分派。** `AiTaskServiceImpl` 保留时间线分支并通过 `ObjectProvider<IWorkflowTaskExecutionService>` 增加工作流分支；未知已持久化类型失败关闭，不让工作流任务落入 `executeSuggestion`。
- [ ] **回归现有时间线。** 先跑工作流定向测试，再跑 `AiTaskServiceImplTest`、`AiTaskTransactionServiceImplTest`、`AiTaskRuntimeIT` 和 `TimelineServiceBoundaryTest`。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowAiTaskContractTest,AiTaskServiceImplTest,AiTaskTransactionServiceImplTest,TimelineServiceBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.task.AiTaskRuntimeIT,org.dromara.aivideo.workflow.WorkflowRunningHubTaskRuntimeIT' verify
```

- [ ] 提交：`refactor(task): 支持工作流用户与运营任务`。

## 4. B1：实现通用私有上传会话的必要闭环

**风险：** 红色。本任务只实现发现页需要的创建会话、直传、完成、查询、取消和访问 URL，不顺带新增完整素材管理页面。

**Core 文件：**

- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/domain/AssetFile.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/AssetDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IAssetService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/AssetServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/domain/FileObject.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/domain/UploadSession.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/mapper/FileObjectMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/mapper/UploadSessionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/asset/FileObjectMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/asset/UploadSessionMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/AssetScopeDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/CreateUploadSessionDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/UploadSessionDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/UploadPartSignaturesDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/CompleteUploadDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/dto/ObjectStorageDTOs.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IFileObjectService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IFileUploadService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IObjectStorageService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/IFileSecurityScanService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/FileObjectServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/asset/service/impl/FileUploadServiceImpl.java`

**Infra 与测试文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/oss/AiVideoObjectStorageServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/asset/WorkflowFileSecurityScanServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/asset/WorkflowInputFileValidator.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/asset/service/impl/FileUploadServiceImplTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/asset/WorkflowInputFileValidatorTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowRunningHubUploadIT.java`

- [ ] **先写上传状态机、幂等和反向归属红灯。** 同 scope 同摘要复用；不同摘要冲突；跨 tenant/user/workspace 返回不存在；过期只返回 46212；完成/取消终态不可回退；不同完成 parts 集合冲突。
- [ ] **实现单 PUT 与 Multipart。** 所有 OSS 网络调用在数据库短事务外；签名 URL/UploadId/Object Key 不进入 VO 或普通日志；`parts` 一次最多 20 个升序编号；对象 PUT 只接受后端给出的 requiredHeaders。
- [ ] **实现完成后核验和 fail-closed 扫描。** HEAD、大小、ETag、扩展名、声明 MIME、魔数、真实解码/ffprobe 一致后才 ready；图片 ZIP 拒绝加密、软链、绝对/`..` 路径、嵌套包、非图片、超过 100 项或 300 MiB 解压量；扫描不可用保持不可访问。
- [ ] **兼容现有人物形象/声音。** 新 workflow asset 使用 file object；旧记录仍能按现有 objectKey 读取。人物形象、声音和时间线测试必须继续通过，不能一次性迁移/删除旧对象。
- [ ] **运行单测与本机 IT。** IT 只写当前运行前缀和 ID 范围，结束后精确清理；验证 ready 前不能签发访问 URL。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=FileUploadServiceImplTest,WorkflowInputFileValidatorTest,AssetServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowRunningHubUploadIT' verify
```

- [ ] 提交：`feat(asset): 实现工作流私有上传会话`。

## 5. B1：实现模板、单配置、账号、订单和审计核心服务

**风险：** 红色。业务编排留在 core；不创建 `application/port/adapter/command/model` 包。

**领域与 Mapper 文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/DiscoveryBanner.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/DiscoveryCategory.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/DiscoveryTag.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowTemplate.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowExecutionConfig.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/RunningHubAccount.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowOrder.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowTaskExecution.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/domain/WorkflowOrderAsset.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/DiscoveryBannerMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/DiscoveryCategoryMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/DiscoveryTagMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/WorkflowTemplateMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/WorkflowExecutionConfigMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/RunningHubAccountMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/WorkflowOrderMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/WorkflowTaskExecutionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/mapper/WorkflowOrderAssetMapper.java`
- 创建复杂查询 XML：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/workflow/WorkflowTemplateMapper.xml`
- 创建复杂查询 XML：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/workflow/WorkflowOrderMapper.xml`
- 创建 CAS XML：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/workflow/WorkflowTaskExecutionMapper.xml`

**契约、Service 与测试文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/constant/WorkflowErrorCodes.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/constant/WorkflowContractLimits.java`
- 创建枚举：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowChannel.java`
- 创建枚举：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowExecutionMode.java`
- 创建枚举：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowTemplateStatus.java`
- 创建枚举：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowSubmissionState.java`
- 创建枚举：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/enums/WorkflowTestStatus.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowTemplateDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowCreationConfigDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowExecutionConfigDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/RunningHubAccountDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowOrderDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowOrderDetailDTO.java`
- 创建 DTO：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowRemoteInspectionDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/validation/WorkflowSchemaCanonicalizer.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/validation/WorkflowInputValidator.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowTemplateService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IDiscoveryContentService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IRunningHubAccountService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowOrderService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowTaskExecutionService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/WorkflowTemplateServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/DiscoveryContentServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/RunningHubAccountServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/WorkflowOrderServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSecurityAuditServiceImpl.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/service/impl/WorkflowTemplateServiceImplTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/service/impl/DiscoveryContentServiceImplTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/service/impl/RunningHubAccountServiceImplTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/service/impl/WorkflowOrderServiceImplTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/WorkflowRunningHubIsolationIT.java`

- [ ] **先写 schema、唯一配置和 CAS 红灯。** 规范化器只接受冻结 schema AST，保留数组顺序并拒绝非安全整数/额外字段；保存第二条配置失败；模式与 workflowId/webappId 互斥；旧 expectedRevision 不能覆盖新值。
- [ ] ~~**实现模板可用性（历史门槛）。** 用户查询只返回 `enabled` 且当前配置启用、账号启用、成功测试三修订均匹配的模板。~~ **第一阶段禁止执行；** 当前规则只检查模板、未删除且启用的唯一配置、未删除且启用的账号，测试状态/修订不参与列表、详情、`creation-config` 或 enable。自动测试门槛仅可在后续阶段经新规格批准后重新设计。
- [ ] ~~**实现“保存即待测试”（历史门槛）。** 修改时递增 revision、状态转 `pending_test` 并清空成功资格。~~ **第一阶段禁止执行；** 修改模板或配置保持模板当前状态，`pending_test` 仅作历史兼容，`row_revision` 只用于乐观并发控制。
- [ ] **实现订单与任务原子创建。** 从 `AppPrincipalSnapshotDTO`/workspace snapshot 派生 tenant/owner/workspace；校验 schema 和 ready asset；保存仅用于展示的标题/封面/输入标签快照；订单和统一任务同一事务创建。相同 scope+key+hash 返回旧订单，不同 hash 返回 46507。
- [ ] ~~**实现当前配置测试资格 CAS。** 测试任务记录模板/配置/账号三个 revision；迟到成功只有三个值仍相等才写 success；enable 再次比对，禁止测试 A 启用配置 B。~~ **后续阶段（第一阶段禁止执行）：** 第一阶段不创建或读取测试资格，不以测试 revision 或 success 作为 enable 门槛；后续引入测试执行时必须经新规格批准。
- [ ] **扩展高权限审计。** 规格 14.1 的动作写操作/安全审计；只保存 tenant、actor、权限、目标、请求 ID、结果、失败分类、字段名和摘要哈希，测试明确 API Key、访问密码、密文、节点值、完整提示词和临时 URL 不出现。
- [ ] **运行 core 单测、隔离 IT 与时间线回归。** IT 覆盖跨 tenant/user/workspace、非 ready、类型伪造、事务回滚、取消/完成终态竞态。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=WorkflowTemplateServiceImplTest,DiscoveryContentServiceImplTest,RunningHubAccountServiceImplTest,WorkflowOrderServiceImplTest,AiTaskServiceImplTest,AiTaskTransactionServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowRunningHubIsolationIT' verify
```

- [ ] 提交：`feat(workflow): 实现单配置模板与订单核心服务`。

## 6. B1：实现 RunningHub 固定 API、密钥、下载安全与任务执行

**风险：** 红色。外部信任边界和可能产生费用的调用集中在本任务。

**Core 边界文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IRunningHubApiService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/IWorkflowCredentialWriteService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/RunningHubApiDTOs.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/dto/WorkflowTaskExecutionDTOs.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/workflow/service/impl/WorkflowTaskExecutionServiceImpl.java`

**Infra 文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/RunningHubProperties.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/RunningHubConfiguration.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/client/RunningHubClient.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/client/RunningHubWireDTOs.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/client/RunningHubResultDownloader.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/security/RunningHubCredentialCipher.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/runninghub/security/RunningHubResultUrlGuard.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/runninghub/client/RunningHubClientTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/runninghub/client/RunningHubResultDownloaderTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/runninghub/security/RunningHubCredentialCipherTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/runninghub/security/RunningHubCredentialBoundaryTest.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/workflow/service/impl/WorkflowTaskExecutionServiceImplTest.java`

**共享调度重命名：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/task/AiTaskScheduler.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/task/AiTaskSchedulerProperties.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/task/AiTaskSchedulerConfiguration.java`
- 创建测试：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/task/AiTaskSchedulerTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfiguration.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureProperties.java`
- 删除：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskScheduler.java`
- 删除：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/listener/TimelineTaskSchedulerTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructureConfigurationTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/infra/timeline/TimelineInfrastructurePropertiesTest.java`

- [ ] **先写八个端点的字段白名单红灯。** 用 JDK 本机 HTTP server 验证 `POST /uc/openapi/accountStatus`、`POST /api/openapi/getJsonApiFormat`、`GET /api/webapp/apiCallDemo`、`POST /openapi/v2/media/upload/binary`、`POST /task/openapi/create`、`POST /task/openapi/ai-app/run`、`POST /openapi/v2/query`、`POST /task/openapi/cancel` 的精确方法、路径、Bearer/body apiKey 形状、响应上限与未知字段失败；测试明确不发送 `workflow/webhookUrl/retainSeconds/instanceType/usePersonalQueue`。
- [ ] **实现 AES-GCM 且锁死解密边界。** Core 的 `IWorkflowCredentialWriteService` 只暴露加密写入，不提供 decrypt/read-plain 能力；随机 96-bit nonce、认证 tag 随密文保存，master key 仅从部署 secret/env 引用读取。解密只允许 `RunningHubClient` 在构建单次外部请求时调用 infra cipher，调用结束立即清空临时字符/字节数组，不返回给 Core、不缓存；`RunningHubCredentialBoundaryTest` 扫描调用点并断言 Core 无 infra 导入和解密 API。缺 key、tag 错、密文损坏全部 fail closed；任何 DTO/日志/异常/toString 不含明文、密文或 key。
- [ ] **实现无副作用账号测试和远端检查。** `accountStatus` 只测试连接；Workflow JSON/AI App 示例只提取受控候选节点，不持久化默认值；保存映射时校验最近候选集合。
- [ ] **实现二进制上传与两种 submit。** 文件由服务器读取平台资产流；Workflow 与 AI App 使用互斥 DTO；创建请求前 CAS 写 `submitting`、账号、模式、开始时间和 deadline，CAS 成功者才允许一次 POST。
- [ ] **实现无快照的有界查询/恢复/取消。** 外部 POST 前每次重新读取模板、当前配置、账号和三者 revision；接受 taskId 后只保存账号 ID、执行模式、外部 taskId 与受理/轮询事实，不保存配置或密钥快照。每次 query/cancel 通过已保存账号 ID 读取账号当前密文并只在 Client 内解密；结果处理重新读取模板当前 `output_policy_json`，不能退回提交时策略。指数退避+抖动、持久化 lastPolledAt/pollCount；重启从已保存事实继续 query。Workflow 才调用 cancel，AI App 提交后 `canCancel=false`。`WorkflowTaskExecutionServiceImplTest` 分别锁定提交前配置变化、受理后模式变化、账号换 key、输出策略变化四种行为。
- [ ] **实现未知提交收口。** CAS 后崩溃、超时、断连、非法响应、过期 submitting 都写 unknown 和非重试失败；单测用请求计数断言恢复不会第二次 POST。
- [ ] **实现不可信结果下载。** 仅 HTTPS+服务器只读 host allowlist；首次和每次重定向重做 host/DNS/实际连接地址检查并固定到已验证公网地址；拒绝私网、回环、链路本地、元数据、DNS 重绑定；限制重定向、时间、Content-Length、实际流字节与并发；下载后再验 MIME/魔数/输出类型。
- [ ] **成功顺序固定。** 全部允许结果下载、校验、登记 `av_asset`、写 order asset 与唯一主结果成功后，统一任务才能 success；资产事务失败时任务失败或留待有界补偿，不能伪造成功。
- [ ] **抽出共享调度器。** 保留现有任务 CAS/lease/recovery，移除 `aivideo.timeline.enabled` 对工作流任务的错误绑定；把 `workerId/pollDelay/recoveryBatchLimit` 迁入共享 properties，一个 scheduler 领取全部已登记任务类型，不创建第二套 workflow scheduler。同步改写 `TimelineInfrastructureConfigurationTest` 与 `TimelineInfrastructurePropertiesTest`，确保不再 import/断言已删除的 `TimelineTaskScheduler`。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=RunningHubClientTest,RunningHubResultDownloaderTest,RunningHubCredentialCipherTest,RunningHubCredentialBoundaryTest,WorkflowTaskExecutionServiceImplTest,AiTaskSchedulerTest,TimelineInfrastructureConfigurationTest,TimelineInfrastructurePropertiesTest,AiTaskServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：本地 server 请求计数和安全反例全部通过，测试没有访问真实 RunningHub。

- [ ] 提交：`feat(runninghub): 接入单配置任务执行链路`。

## 7. B2：实现用户端发现、上传、订单与任务 HTTP

**风险：** 红色。所有 app 归属只从会话派生。

**修改发现与任务文件：**

- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/controller/DiscoveryController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/bo/DiscoveryTemplateQueryBo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/DiscoveryHomeVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplateCardVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplatePageVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/service/IDiscoveryService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/service/impl/DiscoveryServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskListItemVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/discovery/controller/DiscoveryControllerTest.java`

**创建用户 HTTP 文件：**

- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowTemplateDetailVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/discovery/domain/vo/WorkflowCreationConfigVo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/asset/controller/AssetUploadController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/asset/domain/bo/AssetUploadBos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/asset/domain/vo/AssetUploadVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/workflow/controller/WorkflowOrderController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/workflow/domain/bo/WorkflowOrderBos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/workflow/domain/vo/WorkflowOrderVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/workflow/service/IUserWorkflowOrderService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/workflow/service/impl/UserWorkflowOrderServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/asset/controller/AssetUploadControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/workflow/controller/WorkflowOrderControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/controller/AiTaskControllerTest.java`

- [ ] **先写权限红灯。** 每个 Controller 方法反射/MockMvc 断言精确 `@SaCheckPermission(..., type="app")`；无登录、错误 client、无权限在进入 Service 前失败。
- [ ] **补四个发现查询。** 首页、列表、详情、creation-config 均从 `AppLoginHelper` 的 principal/workspace 派生 scope；用户 VO 不含禁止字段；媒体 URL 只接受安全 HTTPS/同源相对路径。
- [ ] **实现上传六端点。** `purpose=workflow_input` 只接 `templateId/schemaHash/inputKey`；BO 继承/采用严格未知字段拒绝；完成后 ready 前不能进订单，访问 URL 再校验 tenant/owner/workspace 和 `aivideo:asset:download`。
- [ ] **实现订单创建/详情/取消。** 创建 BO 精确为 `templateId/schemaHash/inputs`，Header 取 Idempotency-Key；请求体中的 owner/tenant/workspace/provider/version/plan 全部拒绝。查询和取消使用三维 scope；46509 只刷新真实状态。
- [ ] **收紧统一任务中心。** 用户列表排除 sys 测试任务，workflow order 额外做 tenant/workspace EXISTS；详情、取消也用 scope；公共 stage 映射为规格九个值，不暴露内部 timeline 阶段或 RunningHub 原始字段。
- [ ] **运行 Controller、Service 与装配单测。** 增加跨 workspace、额外字段、旧 executionPlanId、外部 taskId 泄漏反向测试。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=DiscoveryControllerTest,AssetUploadControllerTest,WorkflowOrderControllerTest,AiTaskControllerTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 提交：`feat(发现): 完成用户端工作流后端`。

## 8. B2：实现运营发现配置、模板、RunningHub 账号、测试与订单运维 HTTP

**风险：** 红色。密钥、测试和资产访问属于高权限操作。

**创建文件：**

- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/DiscoveryHomeAdminController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/WorkflowTemplateAdminController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/RunningHubAccountAdminController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/controller/WorkflowOrderAdminController.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/DiscoveryHomeAdminBos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/WorkflowTemplateAdminBos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/RunningHubAccountAdminBos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/bo/WorkflowOrderAdminQueryBo.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/DiscoveryHomeAdminVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/WorkflowTemplateAdminVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/RunningHubAccountAdminVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/domain/vo/WorkflowOrderAdminVos.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/IDiscoveryHomeAdminService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/IWorkflowTemplateAdminService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/IRunningHubAccountAdminService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/IWorkflowOrderAdminService.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/impl/DiscoveryHomeAdminServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/impl/WorkflowTemplateAdminServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/impl/RunningHubAccountAdminServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/workflow/service/impl/WorkflowOrderAdminServiceImpl.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/DiscoveryHomeAdminControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/WorkflowTemplateAdminControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/RunningHubAccountAdminControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/WorkflowOrderAdminControllerTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/DiscoveryHomeAdminServiceImplTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/WorkflowTemplateAdminServiceImplTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/RunningHubAccountAdminServiceImplTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/workflow/WorkflowOrderAdminServiceImplTest.java`

- [ ] **先写端点/权限矩阵红灯。** 覆盖规格 10.3 全部 URL、20 个权限、分页和 CAS，并覆盖 `GET /api/admin/workflow-orders/{orderId}/assets/{assetId}/access-url` 的 `aivideo:workflow-order:asset-access`；直接调用无权限接口不进入 Service，Sys token 访问 App 资产短链也必须失败。
- [ ] **实现发现首页配置。** Banner、推荐位、分类和标签只引用授权的 `sys_oss` 与当前可用模板；稳定 code 创建后不可改，排序、启停和展示窗口更新走 `aivideo:discover-home:edit`，查询走 `aivideo:discover-home:query`，引用校验和更新在 Service 完成。
- [ ] **实现模板与单配置。** PUT config 覆盖当前一行而非新增版本；Workflow/AI App 字段互斥；`accessPassword` 空表示不修改，显式清除是独立确认字段；self-hosted 值由 BO 枚举和 Service 双重拒绝。
- [ ] ~~**实现检查与测试。** inspections 只回候选节点；test-runs 要求 Idempotency-Key、测试输入和 `confirmExternalCost=true`；测试任务为 sys actor、orderId 为空。~~ **后续阶段（第一阶段禁止执行）：** 第一阶段不提供 inspections、test-runs 或任何连接测试端点。
- [ ] **第一阶段实现普通启停。** 模板和账号的 enable/disable 均比较 expectedRevision；配置保存继续使用 `row_revision` 做乐观并发控制，但不触发测试流程。
- [ ] **实现账号只写不读。** 列表/详情只返 masked/hasApiKey；更新掩码不能当新 key；API Key 与 `accessPassword` 只写或脱敏，普通 enable/disable 在第一阶段有效。
- [ ] ~~**实现账号连接测试与密钥更新测试门槛。** connection-test 只调用 accountStatus；密钥更新重置健康并使引用模板 pending_test。~~ **后续阶段（第一阶段禁止执行）：** 第一阶段没有 connection-test，密钥修改保持当前启停状态且不自动写 `pending_test`；后续若引入健康检查必须经新规格批准。
- [ ] **实现订单运维。** 列表/详情按 tenant 数据范围过滤；失败摘要脱敏；只有 `aivideo:workflow-order:asset-access` 才能通过 `GET /api/admin/workflow-orders/{orderId}/assets/{assetId}/access-url?disposition=inline|attachment` 签发短期 URL并记录审计，且 Service 同时校验 order-asset 关联、tenant 数据范围和素材 ready 状态。
- [ ] **配置 @Log。** 参考 `AppAuthClientAdminController`，对秘密相关接口排除请求/响应正文；业务安全审计仍写受控摘要。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=DiscoveryHomeAdminControllerTest,WorkflowTemplateAdminControllerTest,RunningHubAccountAdminControllerTest,WorkflowOrderAdminControllerTest,DiscoveryHomeAdminServiceImplTest,WorkflowTemplateAdminServiceImplTest,RunningHubAccountAdminServiceImplTest,WorkflowOrderAdminServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

- [ ] 提交：`feat(运营): 完成 RunningHub 模板与订单后端`。

## 9. F1：切换用户前端为唯一表单、真实上传与严格 wire

**风险：** 红色。前端隐藏不是安全边界，adapter 也必须拒绝旧字段。

**Service 文件：**

- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/queryKeys.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/api.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/adapter.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/discovery/adapter.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/adapter.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/api.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/workflow-orders/adapter.test.ts`
- 创建目录文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/types.ts`
- 创建目录文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/api.ts`
- 创建目录文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/adapter.ts`
- 创建目录文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/queryKeys.ts`
- 创建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/api.test.ts`
- 创建测试：`ai-video-ui/ai-video-webapp/src/services/ai-video/asset-uploads/adapter.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/adapter.test.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/polling.test.ts`

**页面与 Mock 文件：**

- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/discovery.module.css`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/template-detail/template-detail.module.css`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/index.module.css`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/discovery/discovery.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/discovery/template-create/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/index.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/workflow-orders/detail/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/tasks/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/tasks/index.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/mock/discovery.ts`
- 修改：`ai-video-ui/ai-video-webapp/mock/workflowOrders.ts`
- 修改：`ai-video-ui/ai-video-webapp/mock/workflowAssets.ts`
- 修改：`ai-video-ui/ai-video-webapp/mock/tasks.ts`

- [ ] **先写用户 wire 失败测试。** 所有 adapter 精确解析对象并拒绝旧 provider/version/plan/mode/remote 字段、不安全媒体 URL、未知控件和值形状；拒绝发生在写缓存和日志之前。
- [ ] **删除 provider 类型和查询。** `WorkflowProviderKind/getExecutionPlans/getFormSchema` 删除；新增 `getCreationConfig(templateId)`；创建订单只发 `templateId/schemaHash/inputs`。
- [ ] **首页和详情去方案化。** 删除方案数量和“选择制作服务”文案；详情不可用稳定展示并返回发现页；运营正文只按普通文本展示，不扫描其自然语言推断 provider。
- [ ] **制作页直接加载单表单。** 白名单控件注册表支持 11 种控件；未知控件阻断整个提交，禁止 `.filter(...)` 静默丢字段；免费文案来自 `billingPolicy.mode`。
- [ ] **替换 setTimeout 伪上传。** 实现会话、单 PUT/分片、complete/query/cancel；签名 PUT 不携带 app Authorization/clientid，只传 requiredHeaders；用 `uploadId+requestGeneration` 丢弃迟到响应；只有 ready asset 写入表单数组。
- [ ] **实现 schema stale 比较。** 46502 后刷新 config，按 inputKey 比较 control/valueType/约束，展示新增/变化/删除；用户确认后才清理不兼容值，不自动提交。
- [ ] **订单与任务。** 详情不显示执行模式/外部 ID/原始错误；终态停止轮询；取消冲突刷新；再次制作先取当前 config 再预填兼容输入；任务列表显示未知合法任务类型但只对白名单 detailTarget 导航。
- [ ] **更新严格 Mock。** Mock 不返回 self-hosted/RunningHub/方案/version，不把伪上传或伪 RunningHub 任务当真实闭环；生产构建仍关闭 Mock。

```powershell
Set-Location ai-video-ui\ai-video-webapp
npx antd info Upload --version 6.5.1 --format json
npx antd info Form --version 6.5.1 --format json
npm test -- src/services/ai-video/discovery/adapter.test.ts src/services/ai-video/workflow-orders/adapter.test.ts src/services/ai-video/asset-uploads/adapter.test.ts src/pages/discovery/template-create/index.test.tsx src/pages/workflow-orders/detail/index.test.tsx src/pages/tasks/index.test.tsx
npm run tsc
npm run biome:lint
npm test
npx antd lint ./src/pages/discovery --format json
npm run build
```

预期：测试、类型、lint、构建通过；静态控件/系统文案/DOM 属性扫描不存在 `ComfyUI|RunningHub|provider|executionPlan|templateVersion`。

- [ ] 提交：`feat(发现): 用户端改用 RunningHub 唯一执行配置`。

## 10. F2：实现运营端发现、模板单配置、账号和订单页面

**风险：** 红色。路由由后端菜单驱动，不新增平行静态路由系统。

**动态路由和 API 文件：**

- 修改：`ai-video-ui/ai-video-platform-ui/src/pages/dynamicPage.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/discovery/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/discovery/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/discovery/index.test.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-template/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-template/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-template/index.test.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/runninghub-account/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/runninghub-account/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/runninghub-account/index.test.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-order/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-order/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/workflow-order/index.test.ts`

**页面文件：**

- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/discovery-home/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/discovery-home/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/detail.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/detail.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/WorkflowTemplateForm.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/ExecutionConfigForm.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/RemoteStructureInspector.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-template/components/TestRunDrawer.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/runninghub-account/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/runninghub-account/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-order/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-order/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-order/detail.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/workflow-order/detail.test.tsx`

- [ ] **先写 API 路径、分页与权限红灯。** 所有路径只在 API 模块；ProTable request 映射 `{data,total,success}`；403、失败、空、筛选无结果、分页状态有测试。
- [ ] **注册动态页面。** `migratedPages` 登记四个列表组件，`migratedPathPrefixes` 登记模板/订单详情；不修改 `config/config.ts` 的 `* -> dynamicPage` 总体路由。
- [ ] **实现发现配置与模板列表。** 使用 PageContainer+ProTable；加载、空、失败、权限不足、分页、保存中/成功/失败齐全；按钮同时受 `hasPermi` 与后端授权保护。
- [ ] **实现单执行配置表单。** 只显示 Workflow/AI App；两模式字段互斥；没有方案子表、版本页、优先级、权重、failover 或 self-hosted 选项。
- [ ] ~~**实现远端检查和测试。** 节点只能从 inspection 候选选择；不能自由输入节点或上传 workflow JSON；测试前明确显示外部费用确认并生成单次 Idempotency-Key。~~ **后续阶段（第一阶段禁止执行）：** 第一阶段运营端不得出现 inspection、测试或连接测试入口。
- [ ] **实现密钥语义。** API Key 和 accessPassword 不回填；掩码只展示，永不回交；空表示不改，显式清除必须二次确认；关闭 modal/drawer 后清除本地秘密值。
- [ ] **实现第一阶段 revision 状态。** 保存/enable/disable 发 expectedRevision；409/稳定冲突刷新数据；`row_revision` 只用于乐观并发控制。
- [ ] ~~**以测试 revision 和 success 限制 enable。** 测试操作发 expectedRevision；UI 只有三修订仍匹配的 success 才允许 enable。~~ **后续阶段（第一阶段禁止执行）：** 第一阶段人工 enable 不检查测试状态或测试修订。
- [ ] **实现订单运维。** 过滤用户/工作空间/模板/状态/时间；详情脱敏；资产访问按钮需要独立权限并只调用 `GET /api/admin/workflow-orders/{orderId}/assets/{assetId}/access-url?disposition=inline|attachment`，不得调用用户端 `/api/assets/{assetId}/access-url`。

```powershell
Set-Location ai-video-ui\ai-video-platform-ui
pnpm test -- src/api/aivideo/workflow-template/index.test.ts src/api/aivideo/runninghub-account/index.test.ts src/api/aivideo/workflow-order/index.test.ts src/pages/aivideo/workflow-template/index.test.tsx src/pages/aivideo/workflow-template/detail.test.tsx src/pages/aivideo/runninghub-account/index.test.tsx src/pages/aivideo/workflow-order/index.test.tsx
pnpm lint
pnpm test
pnpm build
```

- [ ] 提交：`feat(运营): 增加 RunningHub 单配置管理页`。

## 11. I：双启动器、配置、端到端和最终收口

**风险：** 红色。只修复本需求与直接回归，不扩大到数字人、Warm-Flow 或完整素材管理。

**配置与集成文件：**

- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application.yml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-prod.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-prod.yml`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/WorkflowRunningHubDualStarterRouteIsolationIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/WorkflowRunningHubCrossAccountIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/identity/http/WorkflowRunningHubEndToEndIT.java`
- 修改装配测试：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserStarterAssemblyIT.java`
- 修改装配测试：`ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/bootstrap/PlatformStarterAssemblyIT.java`

- [ ] **先写配置失败测试。** 两端 starter 都能装配共享 scheduler、RunningHub client、workflow services；生产缺 master key 时账号功能 fail closed，但不会让与工作流无关的只读健康端点泄密或误启用。
- [ ] **配置只放非秘密。** 固定 RunningHub host、连接/响应/轮询/下载上限、结果 host allowlist、task scheduler 参数放配置；master key 只写 `${AIVIDEO_RUNNINGHUB_MASTER_KEY:}` 引用，禁止提交真实 key。
- [ ] **双启动器路由隔离。** app token 只能访问 `/api/discovery`、`/api/assets`、`/api/workflow-orders`、`/api/tasks`；sys token 只能访问 `/api/admin/discovery`、`/api/admin/workflow-templates`、`/api/admin/runninghub-accounts`、`/api/admin/workflow-orders`；错误 token、client、角色、权限在 Service 前失败。
- [ ] **本机完整数据库/Redis IT。** 依次覆盖迁移、单配置唯一、revision 竞态、上传、订单+任务事务、跨账号、unknown 不重提、终态保护、结果登记；只使用 `ai_video_test` 与 Redis DB 15 当前运行前缀。
- [ ] **四端 E2E 放在集成测试模块。** `WorkflowRunningHubEndToEndIT` 启动用户与运营两个 starter，并使用 JDK 本机 RunningHub Mock；覆盖运营建账号/模板/单配置/测试启用、用户查询/真实上传会话/下单、共享 worker 提交与结果登记、用户订单详情、运营订单与资产短链。断言 Sys token 不能复用 App 资产短链接口、App token 不能访问管理短链接口，测试不访问真实 RunningHub。

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-platform -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=AiTaskServiceImplTest,AiTaskTransactionServiceImplTest,RunningHubClientTest,RunningHubResultDownloaderTest,RunningHubCredentialCipherTest,DiscoveryControllerTest' test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user,ruoyi-modules/ai-video/ai-video-platform,ai-video-user-api,ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.workflow.WorkflowRunningHubMigrationIT,org.dromara.aivideo.workflow.WorkflowRunningHubUploadIT,org.dromara.aivideo.workflow.WorkflowRunningHubIsolationIT,org.dromara.aivideo.workflow.WorkflowRunningHubTaskRuntimeIT' verify
.\mvnw.cmd -pl ai-video-user-api,ruoyi-admin -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.bootstrap.UserStarterAssemblyIT,org.dromara.aivideo.bootstrap.PlatformStarterAssemblyIT' verify
.\mvnw.cmd -pl ai-video-integration-tests -am '-Pdev,local-integration-test,external-http-it' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dit.external.http.skip=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.identity.http.WorkflowRunningHubDualStarterRouteIsolationIT,org.dromara.aivideo.identity.http.WorkflowRunningHubCrossAccountIT,org.dromara.aivideo.identity.http.WorkflowRunningHubEndToEndIT' verify
```

- [ ] **跑用户端前端全门禁。** 分开逐条执行：

```powershell
Set-Location ai-video-ui\ai-video-webapp
npm run tsc
npm run biome:lint
npm test
npm run build
```

- [ ] **跑运营端前端全门禁。** 分开逐条执行：

```powershell
Set-Location ai-video-ui\ai-video-platform-ui
pnpm lint
pnpm test
pnpm build
```
- [ ] **浏览器验收。** 用真实本地双后端和真实上传会话检查 1440×900 与窄屏：用户端无 provider/mode/plan/version，唯一表单、错误、取消、再次制作正确；运营端单配置、密钥不回显、revision 冲突、权限不足正确。
- [ ] **受控真实烟雾测试。** 仅在项目负责人提供专用 RunningHub 账号并明确接受费用后，各执行一次 Workflow 与 AI App；记录内部 task/order/asset、耗时和稳定结果，不保存 key、节点值、临时 URL 或原始响应。没有授权时在验收报告写“真实付费烟雾未授权，未执行”，不得伪报通过。
- [ ] **完整标准验证。**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\validate-development-standards.ps1
git diff --check
git status --short
```

- [ ] **一次独立完整审查。** 只审规格覆盖、用户 wire 泄漏、双 actor、tenant/owner/workspace、密钥、SSRF、重复提交、revision、终态、额度和前后端状态；必须修复项完成后由同一审查者只复核差异和直接受影响测试。
- [ ] **最终提交。** 精确暂存集成/配置/测试文件，提交：`test(发现): 完成 RunningHub 单执行全链路验收`。
- [ ] 使用 `finishing-a-development-branch` 提供合并、PR 或保留分支选项；未获用户授权不推送、不创建 PR、不删除 worktree。

## 12. 最终验收清单

- [ ] 用户静态代码、系统文案、DOM、wire 和请求均无服务商/模式/执行方案/版本信息。
- [ ] 构造旧 `executionPlanId/templateVersionId/providerKind` 请求或响应会被严格拒绝。
- [ ] 每模板数据库最多一条当前配置，运营 UI 也只有一个配置表单。
- [ ] Workflow 只走 RunningHub Workflow API；AI App 仍可由运营绑定；self-hosted 不在本链路选项中。
- [ ] 不存在版本表、配置快照、密钥历史、自动选择、故障切换或自动回退。
- [ ] 用户生成和运营测试都使用统一任务；sys 测试不进入用户任务中心。
- [ ] 提交未知、崩溃恢复和过期 submitting 均不会重复创建 RunningHub 任务。
- [ ] 所有结果经 URL 安全、下载上限、类型和魔数验证并登记平台资产后才成功。
- [ ] API Key、密码、密文、节点值、外部临时 URL、外部原始错误不会进入用户响应、普通日志或审计载荷。
- [ ] tenant、owner、workspace、权限、幂等、revision、取消竞态和终态保护均有反向测试。
- [ ] 免费工作流任务没有额度冻结、扣减、结算或账本流水。
- [ ] 公共文档、迁移、用户/运营前后端、Mock、双启动器和验证证据在同一交付中闭合。
