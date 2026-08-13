# “创作—说需求”P0-C 通用业务底座实现计划

> **面向 AI（人工智能）代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（子代理驱动开发，推荐）或 superpowers:executing-plans（分批执行计划）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 P0-B 工作区与对象授权门禁之上，交付方向目录、幂等草稿启动、统一任务、额度账户、价格版本、每次调用的不可变账单明细、收费操作槽、任务组 membership（成员关系）并发守卫，以及用户端和运营端的公共请求/任务/额度基础。

**架构：** `ai-video-core` 以 `direction`、`studio`、`task`、`quota` 四个聚合的贫血 Entity 加 Service 编排共同消费唯一 `IWorkspaceAuthorizationService`；所有脚本草稿、问卷和生成写事务使用唯一锁序 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`，并一次写入根任务、首个执行任务、用量操作和锁定流水。`ai-video-user` 与 `ai-video-platform` 分别通过端侧 BO／VO 和具体身份 resolver 暴露用户与运营接口；`ai-video-infra` 只承载 SnailJob 扫描器、任务处理器注册表及直接 `provider`／`client` 技术边界，P0-C 不发起真实模型或检索调用。

**技术栈：** Java 21（后端编程语言）、Spring Boot 4.1.0（Java 应用框架）、RuoYi-Vue-Plus 6.0.0-BETA（若依增强版测试版本）、MyBatis-Plus（数据访问增强工具）、MySQL 8（关系型数据库）、Redis 7（缓存数据库）、JUnit 5（Java 测试框架）、Mockito（模拟测试框架）、本机受控集成测试（直接连接本机 MySQL/Redis，不使用容器或虚拟化环境）、React 19（前端视图库）、Umi Max 4（前端应用框架）、Ant Design 6（蚂蚁设计组件库）、ProComponents 3（中后台高级组件库）、React Query 5（服务端状态查询库）、Vitest（前端单元测试）。

**阅读约定：** 正文中的 API（应用编程接口）、SQL（结构化查询语言）、DDL（数据定义语言）、IT（集成测试）、Mapper（数据映射器）、Token（令牌）等英文术语首次出现时附中文含义；反引号中的类名、字段名、文件名、命令和接口路径是必须原样使用的程序标识符，不翻译其拼写，但由相邻中文解释其用途。

**本机 IT 命令约定：** 所有 `*IT`（集成测试）复用 P0-A 的 `LocalIntegrationEnvironment`（本机受控集成环境夹具），并在 Maven 命令中带 `'-Pdev,local-integration-test'`。夹具默认读取用户端 `application-dev.yml` 的标准数据源与 Redis 配置，固定派生本机 `ai_video_test`、Redis DB 15 及 `aivideo:it:<runId>:` 前缀；`AI_VIDEO_IT_*` 环境变量仅可选覆盖。迁移前先清理专用测试库，再按 `01 -> 02 -> 03 -> 04 -> 04a` 顺序执行，禁止容器、虚拟化和任何开发/生产数据源。

**全局 TDD 红灯约定：** 在编写 RED（红灯）测试前，必须先在本任务列出的精确生产文件中创建“可编译但无业务实现”的骨架：Java 方法统一 `throw new UnsupportedOperationException("RED skeleton")`，React 组件统一 `return null`，TypeScript 异步函数统一 `return Promise.reject(new Error("RED skeleton"))`。只有测试已被正确发现和执行、且目标业务断言失败才算有效 RED；编译失败、装配失败、测试未发现或选择器未命中一律不算 RED。进入 GREEN（绿灯）时只替换骨架业务实现，不得改变红灯断言来制造通过。

---

## 规格来源、依赖门禁与范围

- 唯一业务规格：`docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md`，重点执行第 2.4、6.1、8、9、10.1～10.5、11.1～11.5、12、13.1～13.4、13.6、14、16、17 和 19 节。
- 前置实施包：
  - P0-A 提供独立创作端身份、`app` 会话和安全审计。
  - P0-B 提供 `org.dromara.aivideo.authorization.service.IWorkspaceAuthorizationService`、工作区／对象授权和 SQL 数据范围。
  - P0-B 同时提供 `org.dromara.aivideo.user.authorization.security.AppAuthorizationActorResolver` 与 `org.dromara.aivideo.platform.authorization.security.SysAuthorizationActorResolver`，分别作为两端读取登录助手并构造强类型 actor 的唯一边界。
- 本包固定服务：
  - `org.dromara.aivideo.task.service.IAiTaskService`
  - `org.dromara.aivideo.task.service.IAiTaskExecutionDispatcher`
  - `org.dromara.aivideo.task.service.IAiTaskAttemptService`
  - `org.dromara.aivideo.quota.service.IQuotaBillingService`
  - `org.dromara.aivideo.direction.service.IDirectionCatalogService`
- 本包内部服务固定为 `org.dromara.aivideo.studio.service.IScriptDraftService` 与 `org.dromara.aivideo.task.service.IAiTaskExecutionHandler`；不得将其登记为跨阶段稳定 Service。
- 模型／搜索直接 provider、client 及供应商原始类型只允许位于 `ai-video-infra`；task 扫描器与处理器注册表固定位于 `ai-video-infra/src/main/java/org/dromara/aivideo/task/provider`。跨 core 用量只使用 `org.dromara.aivideo.task.dto.ProviderUsageDTO`。
- 本包不实现动态问题、答案修订、知识路由、证据抓取、三候选、文案版本、通知投递或真实提供商调用。
- P0-C 只建设文本生成规格所需的五个价格项：`question_generate`、`evidence_retrieve`、`script_generate`、`script_regenerate`、`script_optimize`。
- 生产迁移不写任意固定价格；测试在测试事务中创建价格版本。运营人员通过价格管理接口发布真实价格。
- Java 内部编号和修订号使用 `Long`，额度使用非负 `Long` 最小单位，提供商成本使用 `BigDecimal`；HTTP 与 TypeScript 中编号、额度和成本全部使用十进制 `string`。

## 文件结构

### 公共契约与数据库

- 创建：`docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql`
  - 创建方向目录、草稿/首分支、统一任务、任务组、操作槽、执行尝试、额度账户、价格版本、计费操作和不可变流水表。
- 创建：`docs/sql/ai-video/mysql/20260728_04_p0_seed.sql`
  - 可重复写入首个已发布方向目录、六类行业及用途、目标时长规则、运营菜单与权限。
  - 不写生产价格、测试账户、测试余额或演示任务。
- 向前追加：`docs/sql/ai-video/mysql/20260728_04a_p0c_task_group_guard.sql`
  - 创建 `av_ai_task_group_member` 与 `idx_av_ai_task_active_group`，冻结任务组 membership、共同分支锁、写守卫和继承语义；不得回改已经执行的 `03`／`04`。
- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`docs/BACKEND_GUIDE.md`
- 修改：`docs/FRONTEND_GUIDE.md`
- 修改：`ai-video-ui/ai-video-webapp/PRD.md`

### `ai-video-core` 公共错误

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoBusinessException.java`

### 方向目录

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/DirectionCatalogStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvDirectionCatalogVersion.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvIndustryOption.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvPurposeOption.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/dto/DirectionCatalogSnapshotDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/DirectionCatalogVersionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/IndustryOptionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/PurposeOptionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/IDirectionCatalogService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/impl/DirectionCatalogServiceImpl.java`

### 草稿启动

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptDraft.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/CreateScriptDraftDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/ScriptDraftOverviewDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/StepGuardDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/IScriptDraftService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/impl/ScriptDraftServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/security/ScriptDraftOwnershipResolver.java`

### 额度、价格与账单

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/QuotaUnit.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/TariffStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/UsageOperationStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/LedgerType.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockRequestDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockResultDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaAccountSnapshotDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaAccount.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaTariff.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvAiUsageOperation.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaLedgerEntry.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaAccountMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaTariffMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/AiUsageOperationMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaLedgerEntryMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaAccountMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/AiUsageOperationMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaLedgerEntryMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/impl/QuotaBillingServiceImpl.java`

### 统一任务、耐久调度与提供商边界

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskInitiatorDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskRole.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskType.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskBillingMode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ChargeableTaskDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/FreeTaskDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskRevisionSnapshotDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskCreationResultDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskResultReferenceDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionLeaseDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTask.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskGroupMember.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiOperationSlot.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskAttempt.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskGroupMemberMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiOperationSlotMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiOperationSlotMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskAttemptMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionDispatcher.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskAttemptService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionHandler.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/AiTaskNonRetryableException.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskExecutionDispatcherImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskAttemptServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptHandleDTO.java`
- 允许按需修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/SnailJobAiTaskExecutionScanner.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/AiTaskExecutionHandlerRegistry.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelProvider.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallRequest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallResult.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ProviderUsageDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchClient.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchRequest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchResult.java`

### 用户端控制器

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolver.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/direction/controller/DirectionController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/direction/domain/vo/DirectionOptionsVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/controller/ScriptDraftController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo/CreateScriptDraftBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/vo/ScriptDraftOverviewVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/bo/AiTaskQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskSummaryVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/domain/vo/AiTaskDetailVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/controller/QuotaController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/domain/vo/QuotaAccountVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/domain/vo/QuotaTariffVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/common/AiVideoExceptionHandler.java`

### 运营端控制器

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolver.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/controller/DirectionCatalogAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/domain/bo/DirectionCatalogQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/domain/bo/SaveDirectionCatalogBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/domain/vo/DirectionCatalogVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/QuotaAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/TariffAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/bo/QuotaAccountQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/bo/QuotaLedgerQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/bo/CreateTariffBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/bo/AdjustQuotaBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/vo/QuotaAccountVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/vo/QuotaTariffVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/domain/vo/QuotaLedgerEntryVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiTaskAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiUsageAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/domain/bo/AiTaskQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/domain/vo/AiTaskSummaryVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/domain/vo/AiTaskDetailVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/domain/vo/AiUsageCostVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/common/AiVideoExceptionHandler.java`

### 后端测试

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/P0cSchemaIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/direction/DirectionCatalogServiceIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskExecutionDispatcherTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskAttemptServiceIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java`
- 允许按需修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/ModuleDependencyTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/P0cUserApiIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolverTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/P0cPlatformApiIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolverTest.java`
- 创建：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/assembly/P0cUserBoundaryIT.java`
- 创建：`ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/assembly/P0cPlatformBoundaryIT.java`

本计划创建的每个 JUnit 测试类都在类级标注 `@Tag("dev")`，与 P0-A 固定、P0-B 已验证的 Surefire/Failsafe `${profiles.active}` 分组一致。

### 用户端前端公共服务

- 只读复用：`ai-video-ui/ai-video-webapp/biome.json`
- 只读复用：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 只读复用：`ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/queryClient.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.test.tsx`

### 用户端基础页面

- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuotaSummary.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

### 运营端共享接口与页面

- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/constants.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/pageAdapter.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/components/OperationCostDrawer.tsx`

## 固定状态、错误和服务签名

跨阶段稳定 Service 只允许 `IAiTaskService`、`IAiTaskExecutionDispatcher`、`IAiTaskAttemptService`、`IQuotaBillingService`、`IDirectionCatalogService`；同聚合内部只允许 `IScriptDraftService`、`IAiTaskExecutionHandler`。稳定 DTO 唯一集合为：

- 草稿／步骤：`CreateScriptDraftDTO`、`ScriptDraftOverviewDTO`、`StepGuardDTO`；
- 额度：`QuotaLockRequestDTO`、`QuotaLockResultDTO`、`QuotaAccountSnapshotDTO`；
- 任务：`TaskInitiatorDTO`、`ChargeableTaskDTO`、`FreeTaskDTO`、`TaskRevisionSnapshotDTO`、`TaskCreationResultDTO`、`TaskResultReferenceDTO`、`AiTaskExecutionLeaseDTO`、`AiTaskAttemptHandleDTO`、`ProviderUsageDTO`；
- 方向：`DirectionCatalogSnapshotDTO`。

这些类型全部位于 `ai-video-core` 对应聚合的 `dto` 包；不得创建同义 Command／Result／Snapshot，不得在 core 创建 HTTP BO／VO。

```java
public enum AiVideoErrorCode {
    QUOTA_INSUFFICIENT(46114),
    TARIFF_VERSION_CHANGED(46115),
    IDEMPOTENCY_KEY_CONFLICT(46116),
    TASK_NOT_CANCELLABLE(46119),
    GENERATION_CONTEXT_LOCKED(46123),
    BILLING_SUBJECT_FORBIDDEN(46124);

    private final int code;

    AiVideoErrorCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}

public final class AiVideoBusinessException extends RuntimeException {
    private final int code;
    private final Object data;

    public AiVideoBusinessException(
        AiVideoErrorCode error, String message, Object data) {
        this(error.code(), message, data);
    }

    public AiVideoBusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}

public enum AiTaskType {
    QUESTION_GENERATE(
        "question_generate", Set.of("question_generate")),
    EVIDENCE_RETRIEVE(
        "evidence_retrieve", Set.of("evidence_retrieve")),
    SCRIPT_GENERATE(
        "script_generate",
        Set.of("script_generate", "script_regenerate")),
    SCRIPT_OPTIMIZE(
        "script_optimize", Set.of("script_optimize")),
    KNOWLEDGE_IMPORT(
        "knowledge_import", Set.of("knowledge_import"));

    private final String code;
    private final Set<String> operationTypes;

    AiTaskType(String code, Set<String> operationTypes) {
        this.code = code;
        this.operationTypes = Set.copyOf(operationTypes);
    }

    public String code() {
        return code;
    }

    public boolean supports(String operationType) {
        return operationTypes.contains(operationType);
    }
}

public enum AiTaskBillingMode {
    CHARGEABLE("chargeable"),
    FREE("free");

    private final String code;

    AiTaskBillingMode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}

public record CreateScriptDraftDTO(String idempotencyKey) {
    public CreateScriptDraftDTO {
        if (idempotencyKey == null || idempotencyKey.isBlank()
            || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey 非法");
        }
    }
}

public record StepGuardDTO(
    String stepCode,
    String status,
    @Nullable String blockedCode,
    @Nullable String blockedReason
) {
    private static final Set<String> STEP_CODES = Set.of(
        "demand", "script_confirmation", "avatar_voice",
        "voice_generation", "avatar_base", "timeline",
        "preview_download");
    private static final Set<String> STATUSES = Set.of(
        "locked", "available", "current", "completed");

    public StepGuardDTO {
        if (!STEP_CODES.contains(stepCode) || !STATUSES.contains(status)) {
            throw new IllegalArgumentException("步骤守卫代码或状态非法");
        }
        if ("locked".equals(status)) {
            if (blockedCode == null || blockedCode.isBlank()
                || blockedReason == null || blockedReason.isBlank()) {
                throw new IllegalArgumentException("锁定步骤必须给出稳定代码和中文原因");
            }
        } else if (blockedCode != null || blockedReason != null) {
            throw new IllegalArgumentException("非锁定步骤不得携带阻塞信息");
        }
    }
}

public record ScriptDraftOverviewDTO(
    Long draftId,
    long draftRevision,
    long branchRevision,
    long generationContextRevision,
    String draftStatus,
    String currentStepCode,
    SubjectSnapshot resourceScope,
    SubjectSnapshot billingSubject,
    List<StepGuardDTO> stepGuards,
    boolean reused
) {
    public ScriptDraftOverviewDTO {
        Objects.requireNonNull(draftId, "draftId");
        if (draftId <= 0 || draftRevision < 0 || branchRevision < 1
            || generationContextRevision < 0) {
            throw new IllegalArgumentException("草稿编号或修订号非法");
        }
        if (!Set.of("draft", "generating", "confirmed", "archived")
            .contains(draftStatus)) {
            throw new IllegalArgumentException("draftStatus 非法");
        }
        Objects.requireNonNull(resourceScope, "resourceScope");
        Objects.requireNonNull(billingSubject, "billingSubject");
        stepGuards = List.copyOf(stepGuards);
        if (stepGuards.size() != 7
            || stepGuards.stream().map(StepGuardDTO::stepCode)
                .distinct().count() != 7
            || stepGuards.stream().noneMatch(guard ->
                guard.stepCode().equals(currentStepCode)
                    && guard.status().equals("current"))) {
            throw new IllegalArgumentException("步骤守卫必须恰好覆盖七步并包含当前步骤");
        }
    }

    public record SubjectSnapshot(
        String type, Long id, String displayName) {
        public SubjectSnapshot {
            if (!Set.of("app_user", "organization").contains(type)
                || id == null || id <= 0 || displayName == null
                || displayName.isBlank()) {
                throw new IllegalArgumentException("资源／计费主体摘要非法");
            }
        }
    }
}

public record TaskInitiatorDTO(String actorType, Long actorId) {
    public TaskInitiatorDTO {
        if (!Set.of("app_user", "sys_user").contains(actorType)
            || actorId == null || actorId <= 0) {
            throw new IllegalArgumentException("任务发起主体非法");
        }
    }

    public void requireActorType(String requiredType) {
        if (!actorType.equals(requiredType)) {
            throw new AiVideoBusinessException(
                AiVideoErrorCode.BILLING_SUBJECT_FORBIDDEN,
                "发起主体类型不匹配", Map.of("requiredActorType", requiredType));
        }
    }
}

public record DirectionCatalogSnapshotDTO(
    Long catalogVersion,
    String contentHash,
    Long industryCatalogVersion,
    Long purposeCatalogVersion,
    String durationRuleVersion,
    List<IndustryOption> industries,
    Map<String, List<PurposeOption>> purposesByIndustry,
    List<TargetDurationOption> targetDurations
) {
    public DirectionCatalogSnapshotDTO {
        if (catalogVersion == null || catalogVersion < 1
            || contentHash == null
            || !contentHash.matches("[0-9a-f]{64}")
            || industryCatalogVersion == null || industryCatalogVersion < 1
            || purposeCatalogVersion == null || purposeCatalogVersion < 1
            || durationRuleVersion == null || durationRuleVersion.isBlank()) {
            throw new IllegalArgumentException("方向目录版本、子版本或摘要非法");
        }
        industries = List.copyOf(industries);
        purposesByIndustry = purposesByIndustry.entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> List.copyOf(entry.getValue())));
        targetDurations = List.copyOf(targetDurations);
        Set<String> industryCodes = industries.stream()
            .map(IndustryOption::code).collect(Collectors.toUnmodifiableSet());
        if (industryCodes.size() != industries.size()
            || !industryCodes.equals(purposesByIndustry.keySet())
            || !targetDurations.stream().map(TargetDurationOption::seconds)
                .collect(Collectors.toUnmodifiableSet())
                .equals(Set.of(30, 45, 60, 90, 120))) {
            throw new IllegalArgumentException("方向目录选项绑定或目标时长非法");
        }
    }

    public record IndustryOption(
        String code, String name, int sort, boolean customAllowed) {
        public IndustryOption {
            requireOption(code, name, sort);
        }
    }

    public record PurposeOption(
        String code, String name, int sort, boolean customAllowed) {
        public PurposeOption {
            requireOption(code, name, sort);
        }
    }

    public record TargetDurationOption(int seconds, String label) {
        public TargetDurationOption {
            if (!Set.of(30, 45, 60, 90, 120).contains(seconds)
                || label == null || label.isBlank()) {
                throw new IllegalArgumentException("目标时长非法");
            }
        }
    }

    private static void requireOption(String code, String name, int sort) {
        if (code == null || !code.matches("[a-z][a-z0-9_]{1,63}")
            || name == null || name.isBlank() || sort < 0) {
            throw new IllegalArgumentException("方向选项非法");
        }
    }
}

public record QuotaLockRequestDTO(
    Long tenantId,
    String billingSubjectType,
    Long billingSubjectId,
    String operationType,
    String resourceType,
    Long resourceId,
    String idempotencyKey,
    String requestHash,
    Long expectedTariffVersion,
    TaskInitiatorDTO initiator
) {
    public QuotaLockRequestDTO {
        if (tenantId == null || tenantId <= 0
            || billingSubjectId == null || billingSubjectId <= 0
            || resourceId == null || resourceId <= 0
            || expectedTariffVersion == null || expectedTariffVersion < 1) {
            throw new IllegalArgumentException("额度锁定编号或版本非法");
        }
        if (!Set.of("app_user", "organization")
            .contains(billingSubjectType)) {
            throw new IllegalArgumentException("计费主体类型非法");
        }
        operationType = required(operationType, "operationType", 64);
        resourceType = required(resourceType, "resourceType", 64);
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
        if (requestHash == null
            || !requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash 必须是 SHA-256");
        }
        Objects.requireNonNull(initiator, "initiator");
        initiator.requireActorType("app_user");
    }

    private static String required(
        String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 非法");
        }
        return value;
    }
}

public record QuotaAccountSnapshotDTO(
    Long accountId,
    Long tenantId,
    String billingSubjectType,
    Long billingSubjectId,
    String quotaUnit,
    long availableBalance,
    long lockedBalance,
    long revision
) {
    public QuotaAccountSnapshotDTO {
        if (accountId == null || accountId <= 0
            || tenantId == null || tenantId <= 0
            || billingSubjectId == null || billingSubjectId <= 0
            || availableBalance < 0 || lockedBalance < 0 || revision < 0) {
            throw new IllegalArgumentException("额度账户快照非法");
        }
        if (!Set.of("app_user", "organization")
            .contains(billingSubjectType)
            || quotaUnit == null || quotaUnit.isBlank()) {
            throw new IllegalArgumentException("额度账户主体或单位非法");
        }
    }

    public long totalBalance() {
        return Math.addExact(availableBalance, lockedBalance);
    }
}

public record QuotaLockResultDTO(
    Long operationId,
    Long accountId,
    Long tariffVersion,
    long lockedQuota,
    QuotaAccountSnapshotDTO account,
    boolean reused
) {
    public QuotaLockResultDTO {
        if (operationId == null || operationId <= 0
            || accountId == null || accountId <= 0
            || tariffVersion == null || tariffVersion < 1
            || lockedQuota <= 0) {
            throw new IllegalArgumentException("额度锁定结果非法");
        }
        Objects.requireNonNull(account, "account");
        if (!accountId.equals(account.accountId())) {
            throw new IllegalArgumentException("额度锁定结果账户不一致");
        }
    }
}

public record TaskRevisionSnapshotDTO(
    Long draftRevision,
    Long branchRevision,
    Long generationContextRevision,
    String generationInputHash,
    Map<Long, Long> factDecisionRevisions
) {
    public TaskRevisionSnapshotDTO {
        Objects.requireNonNull(draftRevision, "draftRevision");
        Objects.requireNonNull(branchRevision, "branchRevision");
        Objects.requireNonNull(
            generationContextRevision, "generationContextRevision");
        if (draftRevision < 0 || branchRevision < 1
            || generationContextRevision < 0) {
            throw new IllegalArgumentException("任务修订号非法");
        }
        if (generationInputHash == null
            || !generationInputHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("生成输入摘要必须是 SHA-256");
        }
        factDecisionRevisions = factDecisionRevisions == null
            ? Map.of()
            : Map.copyOf(factDecisionRevisions);
        if (factDecisionRevisions.entrySet().stream().anyMatch(entry ->
            entry.getKey() == null || entry.getKey() <= 0
                || entry.getValue() == null || entry.getValue() < 1)) {
            throw new IllegalArgumentException("事实决定修订快照非法");
        }
    }
}

public record ChargeableTaskDTO(
    AiTaskType taskType,
    String operationType,
    String resourceType,
    Long resourceId,
    String slotKey,
    String taskFamilyKey,
    String taskGroupKey,
    String idempotencyKey,
    String requestHash,
    Long tariffVersion,
    TaskInitiatorDTO initiator,
    TaskRevisionSnapshotDTO revisionSnapshot
) {
    private static final Set<String> OPERATION_TYPES = Set.of(
        "question_generate",
        "evidence_retrieve",
        "script_generate",
        "script_regenerate",
        "script_optimize");

    public ChargeableTaskDTO {
        Objects.requireNonNull(taskType, "taskType");
        operationType = required(operationType, "operationType", 64);
        resourceType = required(resourceType, "resourceType", 64);
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId 非法");
        }
        slotKey = required(slotKey, "slotKey", 255);
        taskFamilyKey = required(taskFamilyKey, "taskFamilyKey", 255);
        taskGroupKey = required(taskGroupKey, "taskGroupKey", 255);
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
        requestHash = required(requestHash, "requestHash", 64);
        if (!requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash 必须是 SHA-256");
        }
        if (!OPERATION_TYPES.contains(operationType)) {
            throw new IllegalArgumentException("operationType 非法");
        }
        if (!taskType.supports(operationType)) {
            throw new IllegalArgumentException(
                "taskType 与 operationType 不匹配");
        }
        Objects.requireNonNull(tariffVersion, "tariffVersion");
        if (tariffVersion < 1) {
            throw new IllegalArgumentException("tariffVersion 非法");
        }
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(revisionSnapshot, "revisionSnapshot");
    }

    private static String required(
        String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 非法");
        }
        return value;
    }
}

public record FreeTaskDTO(
    AiTaskType taskType,
    String resourceType,
    Long resourceId,
    String taskFamilyKey,
    String taskGroupKey,
    String idempotencyKey,
    String requestHash,
    TaskInitiatorDTO initiator,
    TaskRevisionSnapshotDTO revisionSnapshot
) {
    public FreeTaskDTO {
        if (taskType != AiTaskType.KNOWLEDGE_IMPORT) {
            throw new IllegalArgumentException("免费任务类型非法");
        }
        resourceType = required(resourceType, "resourceType", 64);
        Objects.requireNonNull(resourceId, "resourceId");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId 非法");
        }
        taskFamilyKey = required(taskFamilyKey, "taskFamilyKey", 255);
        taskGroupKey = required(taskGroupKey, "taskGroupKey", 255);
        idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
        requestHash = required(requestHash, "requestHash", 64);
        if (!requestHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("requestHash 必须是 SHA-256");
        }
        Objects.requireNonNull(initiator, "initiator");
        Objects.requireNonNull(revisionSnapshot, "revisionSnapshot");
    }

    public String operationType() {
        return "knowledge_import";
    }

    private static String required(
        String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " 非法");
        }
        return value;
    }
}

public record TaskCreationResultDTO(
    Long rootTaskId,
    Long executionTaskId,
    Long usageOperationId,
    boolean reused
) {
}

public record TaskResultReferenceDTO(
    String resultRefType,
    Long resultRefId
) {
    public TaskResultReferenceDTO {
        if (resultRefType == null || resultRefType.isBlank()
            || resultRefType.length() > 64) {
            throw new IllegalArgumentException("resultRefType 非法");
        }
        Objects.requireNonNull(resultRefId, "resultRefId");
        if (resultRefId <= 0) {
            throw new IllegalArgumentException("resultRefId 非法");
        }
    }

    public static TaskResultReferenceDTO of(String type, Long id) {
        return new TaskResultReferenceDTO(type, id);
    }
}

public record AiTaskExecutionLeaseDTO(
    Long rootTaskId,
    Long executionTaskId,
    AiTaskType taskType,
    String leaseOwner,
    Instant leaseExpiresAt
) {
    public AiTaskExecutionLeaseDTO {
        if (rootTaskId == null || rootTaskId <= 0
            || executionTaskId == null || executionTaskId <= 0
            || leaseOwner == null || leaseOwner.isBlank()) {
            throw new IllegalArgumentException("任务租约非法");
        }
        Objects.requireNonNull(taskType, "taskType");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
    }
}

public record ProviderUsageDTO(
    @Nullable String providerRequestId,
    @Nullable Long inputTokens,
    @Nullable Long outputTokens,
    @Nullable BigDecimal actualCost,
    @Nullable String currency
) {
    public ProviderUsageDTO {
        if ((inputTokens != null && inputTokens < 0)
            || (outputTokens != null && outputTokens < 0)
            || (actualCost != null && actualCost.signum() < 0)) {
            throw new IllegalArgumentException("提供商用量或成本不得为负数");
        }
        if (actualCost != null
            && (currency == null || !currency.matches("[A-Z]{3}"))) {
            throw new IllegalArgumentException("已知成本必须携带三位大写币种");
        }
    }
}

public interface IScriptDraftService {
    ScriptDraftOverviewDTO create(CreateScriptDraftDTO request);
}

public interface IAiTaskExecutionHandler {
    AiTaskType supports();
    void handle(AiTaskExecutionLeaseDTO lease);
}

public interface IDirectionCatalogService {
    DirectionCatalogSnapshotDTO currentPublishedCatalog();
}

public interface IQuotaBillingService {
    QuotaLockResultDTO lock(QuotaLockRequestDTO request);
    QuotaAccountSnapshotDTO settle(Long operationId, Long rootTaskId);
    QuotaAccountSnapshotDTO release(Long operationId, Long rootTaskId, String failureCode);
    void publishTariff(
        Long tariffId, LocalDateTime effectiveAt,
        String idempotencyKey, TaskInitiatorDTO initiator);
    QuotaAccountSnapshotDTO adjust(
        Long accountId, long delta, String reason,
        String idempotencyKey, TaskInitiatorDTO initiator);
    QuotaAccountSnapshotDTO refund(
        Long relatedLedgerId, String reason,
        TaskInitiatorDTO initiator);
    QuotaAccountSnapshotDTO compensate(
        Long relatedLedgerId, long delta, String correctionKey,
        String reason, TaskInitiatorDTO initiator);
}

public interface IAiTaskService {
    TaskCreationResultDTO createChargeableTask(ChargeableTaskDTO request);
    TaskCreationResultDTO createFreeTask(FreeTaskDTO request);
    void requireGenerationContextWritable(
        Long draftId,
        Long branchRevision);
    void inheritQuestionnaireTaskGroupMembers(
        Long draftId,
        Long sourceBranchRevision,
        Long targetBranchRevision,
        List<Long> retainedRootTaskIds,
        TaskInitiatorDTO initiator);
    List<AiTaskExecutionLeaseDTO> claimExecutableTasks(
        Instant now, String workerId, Instant leaseExpiresAt, int limit);
    AiTaskExecutionLeaseDTO renewLease(
        AiTaskExecutionLeaseDTO lease, Instant newLeaseExpiresAt);
    void recordHandlerFailure(
        AiTaskExecutionLeaseDTO lease,
        String failureCode,
        String failureMessage,
        boolean retryable);
    void markSuccess(
        AiTaskExecutionLeaseDTO lease, TaskResultReferenceDTO result);
    void markFailed(
        AiTaskExecutionLeaseDTO lease,
        String failureCode,
        String failureMessage);
}

public interface IAiTaskExecutionDispatcher {
    void enqueue(Long rootTaskId, Long executionTaskId);
}

public interface IAiTaskAttemptService {
    AiTaskAttemptHandleDTO startAttempt(
        Long rootTaskId,
        Long executionTaskId,
        String leaseOwner,
        String callPurpose,
        String provider,
        @Nullable String model,
        String inputHash);
    void completeAttempt(
        Long attemptId,
        ProviderUsageDTO usage,
        String outputHash);
    void failAttempt(
        Long attemptId,
        ProviderUsageDTO usage,
        String failureCode,
        String failureMessage);
}

public record AiTaskAttemptHandleDTO(
    Long attemptId,
    int providerCallSequence,
    String callPurpose) {
}
```

`requireGenerationContextWritable` 与 `inheritQuestionnaireTaskGroupMembers` 都必须使用
`@Transactional(propagation = Propagation.MANDATORY)`，拒绝无外层事务调用。前者在 P2
修改答案、补充或接受事实之前，按 `draft -> current_branch` 锁定共同 branch 行，在锁内构造
`script:{draftId}:{branchRevision}`，检查 root task 的类型只限 `script_generate|script_optimize`
且状态只限 `pending|queued|running`；命中时抛 `46123`，响应 `data` 精确且仅含
`rootTaskId`、`taskType`、`status`，不得泄露提示词、答案、提供商请求或成本。

`inheritQuestionnaireTaskGroupMembers` 只继承任务组 membership 引用：校验
`initiator.actorType=app_user` 且与当前工作区一致，`sourceBranchRevision >= 1`、
`targetBranchRevision = sourceBranchRevision + 1`，`retainedRootTaskIds` 为 1～5 个正数、去重、升序；
每个 root 必须同 tenant、`taskType=question_generate`、资源为当前 `script_draft/draftId`、任务族一致，
并已经属于 source 问卷任务组。target 为空时写 `origin_type=inherited`；完全相同重放幂等，
partial、superset、conflict 或 target 中已有 `origin` 行全部失败关闭。不得复制 task、usage、ledger、operation slot。

两方法与 P1／P2／P3 的创建链共同遵守全局锁序
`draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`；
所有参与者必须先锁同一 branch 行，禁止依赖不存在行的 gap lock。跨 inherited/origin membership
聚合费用必须按 `usageOperationId` 去重，禁止 `SUM(DISTINCT amount)`，因为不同操作可能金额相同。

控制器把 HTTP 的 `expectedTariffVersion` 映射到 `ChargeableTaskDTO.tariffVersion`。出题和资料检索传空的 `factDecisionRevisions`；文案生成/重新生成传当前有效事实编号到决定修订号的完整映射；优化传来源版本冻结的映射。处理器成功落库业务结果后调用 `markSuccess(lease, TaskResultReferenceDTO.of("script_version", versionId))`；问题和证据分别使用 `question`、`evidence_batch` 结果类型。`markSuccess/markFailed/recordHandlerFailure/renewLease` 都必须用完整 `AiTaskExecutionLeaseDTO` 条件校验执行任务仍为同一 `running + leaseOwner`，过期工作器不能写结果、成本或终态。

P1 知识导入调用 `createFreeTask(new FreeTaskDTO(KNOWLEDGE_IMPORT, "knowledge_import_batch", batchId, "knowledge-import:" + batchId, "knowledge-import:" + batchId, idempotencyKey, requestHash, sysInitiator, revisionSnapshot))`。免费 DTO 没有槽键、价格版本或计费字段；`sysInitiator` 必须由平台端具体 resolver 从 P0-B 运营 actor resolver 构造，core 不读取登录上下文。`TaskCreationResultDTO.usageOperationId` 对免费任务为 `null`，其余编号仍在 HTTP 输出转成十进制字符串。稳定 `taskType` 代码只有 `question_generate`、`evidence_retrieve`、`script_generate`、`script_optimize`、`knowledge_import`；重新生成仍使用 `taskType=script_generate`，只把 `operationType` 设为 `script_regenerate`。

统一任务状态只允许 `pending`、`queued`、`running`、`success`、`failed`、`cancelled`。`AiTaskBillingMode` 只允许 `chargeable`、`free`：收费根任务的 `usage_operation_id` 必填且唯一，免费根任务必须为空，所有执行任务也必须为空。账单类型只允许 `grant`、`adjustment`、`lock`、`settle`、`release`、`refund`、`compensation`。

固定注册确定性非重试失败码 `STALE_BRANCH_RESULT`（旧分支结果已丢弃），大小写不得
变化并满足 `AiTaskNonRetryableException` 的
`[A-Z][A-Z0-9_]{2,63}` 校验。下游提供商 attempt（调用尝试）已经成功，但业务结果
事务在任何结果写入前发现冻结的 `branchRevision`、`generationContextRevision` 或
`generationInputHash` 与当前分支/上下文不一致时，处理器必须抛
`AiTaskNonRetryableException("STALE_BRANCH_RESULT", 脱敏消息)`。扫描器本轮立即把
execution/root（执行/根任务）收敛为 `failed/STALE_BRANCH_RESULT`，释放当前收费操作
且不结算；成功 attempt 及其成本明细原样保留，不得再次调用提供商。

`IAiTaskService.createChargeableTask/createFreeTask` 只创建 `pending` 根任务和执行任务，不得自行入队。`IAiTaskExecutionDispatcher.enqueue(rootTaskId, executionTaskId)` 是 P0-C 冻结的唯一公共调度入口；实现必须使用 `@Transactional(propagation = Propagation.MANDATORY)` 或等价显式守卫拒绝无事务调用，按 `(rootTaskId, executionTaskId)` 校验父子关系，并在同一事务内条件更新执行任务 `pending -> queued` 与聚合根任务 `pending -> queued`。父子任一更新失败时整笔事务回滚；同一根/执行对已经一致处于 `queued/running/终态` 时重复调用不得再写任务、额度记录或投递事实。它不执行模型、不创建或扣费任务。

P1/P2/P3 的业务 orchestrator（流程编排服务）必须提供外层同一 `@Transactional` 事务，固定执行 `create -> freeze immutable input -> enqueue`；`TaskCreationResultDTO.reused=true` 时立即返回，不再次冻结或入队。冻结或入队失败时，根任务、执行任务、额度锁、不可变输入和 `queued` 状态随同一事务全部回滚且不可见，禁止捕获冻结异常后调用 `markFailed` 补偿。提交前的 `queued` 行受数据库隔离保护，工作器无法领取，因此扫描器永远看不到缺少冻结输入的 `queued` 行。

`ai-video-user` 与 `ai-video-platform` 分别只装配 `AppTaskInitiatorResolver` 和
`SysTaskInitiatorResolver`。前者只能依赖 P0-B `AppAuthorizationActorResolver`，把其
返回的 `AppActorContext.appUser` 转成
`TaskInitiatorDTO("app_user", appUserId)`；后者只能依赖
`SysAuthorizationActorResolver`，把其返回的 `AppActorContext.sysUser` 转成
`TaskInitiatorDTO("sys_user", sysUserId)`。Resolver 自身不得再次导入 `AppLoginHelper`
或默认 `LoginHelper`，两个具体 resolver 及其上游 actor resolver 都不得查询、尝试或回退另一身份
域。收费任务只接受 `app_user` 且必须与 `IWorkspaceAuthorizationService` 的当前创作
用户一致；唯一免费任务 `knowledge_import` 只接受 `sys_user`。运营额度调整、退款、
补偿同样冻结 `sys_user + sysUserId`；用户侧额度锁定冻结根任务的
`app_user + initiatedByUserId`，异步结算/释放只读取冻结主体，绝不猜测当前线程登录。

`ai-video-infra` 的 `SnailJobAiTaskExecutionScanner`（SnailJob 分布式任务扫描器）周期扫描 `queued` 和租约过期的 `running`。创建阶段只产生一个根任务及一个 `executionNo=1` 的执行任务；首次领取同一事务条件更新该执行任务 `queued -> running` 与根任务 `queued -> running`。租约恢复／过期扫描必须复用同一 `executionTaskId` 和 `executionNo=1`，只更新租约，根任务保持 `running`，不得创建第二个执行任务。扫描器、恢复、免费任务和所有未发生 provider 调用的路径均保持 `attempt=0`。只有 P1／P2／P3 的 `IAiTaskExecutionHandler` 在紧邻每次真实 `ModelProvider` 或 `WebSearchClient` 调用前才调用 `IAiTaskAttemptService.startAttempt`；调用序号在同一根任务内单调递增且最多 3 次，随后补记中性 `ProviderUsageDTO`、成功输出摘要或失败信息。扫描器作为最终恢复来源，进程在提交后立即崩溃也不会丢任务；任何 after-commit（事务提交后）唤醒只能是可选加速，唤醒失败仍由耐久 `queued` 行恢复。本阶段禁止真实外调、测试专用生产端点和伪造 attempt，测试直接驱动 Service、真实事务和测试替身工作器。

## 阶段基线与六个共享文件所有权

P0-C 必须从经独立 reviewer 批准并已提交的 P0-B candidate HEAD 开始。任务 17 已冻结的原始
`p0c-f1-handoff.json` 是不可变历史证据，禁止覆盖、重排或补字段；任务 18 只能向前追加
`20260728_04a_p0c_task_group_guard.sql`、源签名证据、addendum 与独立 review。当前可执行迁移全链固定为
`20260728_01_p0a_identity_security.sql -> 02 -> 03 -> 04 -> 04a -> 05 -> 06 -> 07`。
`originalF1Head` 必须是 `amendmentHead` 的祖先；P1／P2／P3 必须按主计划 Tasks 5–7 rebase 到同一
F1 amendment HEAD，并同时校验原 handoff SHA 与 addendum SHA，不能只消费旧 F1 HEAD。

以下六个文件由 P0-C 独占写入直至完整 F1：`AvScriptBranch.java`、`ScriptBranchMapper.java`、`ScriptDraftMapper.java`、`studio/types.ts`、`studio/api.ts`、`studio/queryKeys.ts`。移交前 P2 对它们只读；任务 17 记录 baseline SHA、acceptance window 和 reviewer 签署后，所有权一次性移交 P2，此后 P0-C 对六文件只读。任何跨 owner 变更必须先更新公共契约并由新 owner 单独提交，禁止双方同时修改。

## 任务 1：冻结 P0-C 公共契约并验证前置授权门禁

**最小任务卡：**

- **单一目标／不做：** 冻结 P0-C 契约、F0 输入和实施 worktree 门禁；不写业务实现，不冻结完整 F1。
- **风险／触发：** 红色；命中共享契约、身份边界、迁移顺序和下游公共依赖。
- **权威来源：** 批准规格、主计划 Task 4 冻结表、P0-B candidate 交接、公共契约与 AI 治理规范。
- **成功／反向验收：** 完整 F0 与已提交 candidate 可追溯，缺失／伪造／非祖先／共享 main 均失败关闭；契约没有旧分层或同义类型。
- **所有权／数据范围：** 仅本任务文档清单与当前 P0-C worktree Git 元数据；不访问下游 worktree，不改业务表。
- **依赖／人员／并发：** 依赖 P0-B candidate；开发 A 唯一 writer、开发 C 独立契约 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 运行门禁、契约扫描和规范校验但不得判 PASS；独立 reviewer 复跑并签署结论。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`docs/BACKEND_GUIDE.md`
- 修改：`docs/FRONTEND_GUIDE.md`
- 修改：`ai-video-ui/ai-video-webapp/PRD.md`

- [ ] **步骤 1（2–5 分钟）：建立独立 P0-C worktree 门禁并验证 P0-B candidate**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
if ([System.IO.Path]::GetFullPath((Get-Location).Path) -ne $repoRoot) {
  throw 'P0-C 启动门禁必须在分配的 worktree 根目录执行'
}
$currentBranch = (& git branch --show-current).Trim()
$gitDir = [System.IO.Path]::GetFullPath((& git rev-parse --git-dir).Trim())
$commonGitDir = [System.IO.Path]::GetFullPath((& git rev-parse --git-common-dir).Trim())
if ($LASTEXITCODE -ne 0 -or $currentBranch -notlike 'codex/*' -or $currentBranch -eq 'main') {
  throw "P0-C 必须位于分配的 codex/* 分支：$currentBranch"
}
if ($gitDir -eq $commonGitDir) { throw 'P0-C 必须使用独立 linked worktree，禁止共享 main 工作区' }
$dirty = @(git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0 -or $dirty.Count -ne 0) { $dirty; throw '建立 P0-C 门禁前 worktree 必须干净' }
$providedF0 = $env:AI_VIDEO_F0_HEAD
$providedCandidate = $env:AI_VIDEO_P0B_CANDIDATE_HEAD
$handoffPathText = $env:AI_VIDEO_P0B_CANDIDATE_HANDOFF
if ($providedF0 -notmatch '^[0-9a-fA-F]{40}$' `
    -or $providedCandidate -notmatch '^[0-9a-fA-F]{40}$' `
    -or [string]::IsNullOrWhiteSpace($handoffPathText)) {
  throw '必须提供完整 AI_VIDEO_F0_HEAD、AI_VIDEO_P0B_CANDIDATE_HEAD 与已批准 candidate handoff'
}
$handoffPath = [System.IO.Path]::GetFullPath($handoffPathText)
if (-not (Test-Path -LiteralPath $handoffPath -PathType Leaf)) { throw 'P0-B candidate handoff 不存在' }
$handoff = Get-Content -Raw -LiteralPath $handoffPath | ConvertFrom-Json
$f0Head = (& git rev-parse "$providedF0^{commit}").Trim()
$p0bCandidateHead = (& git rev-parse "$providedCandidate^{commit}").Trim()
if ($LASTEXITCODE -ne 0 -or $f0Head -ne $providedF0.ToLowerInvariant() `
    -or $p0bCandidateHead -ne $providedCandidate.ToLowerInvariant()) {
  throw 'F0 或 P0-B candidate 不是完整已提交 SHA'
}
if ($handoff.f0Head -ne $f0Head -or $handoff.p0bCandidateHead -ne $p0bCandidateHead `
    -or $handoff.fullF1Ready -ne $false `
    -or $handoff.downstreamRebaseBlockedUntil -ne 'P0-C complete F1') {
  throw 'P0-B candidate handoff 与批准输入不一致'
}
git merge-base --is-ancestor $f0Head $p0bCandidateHead
if ($LASTEXITCODE -ne 0 -or $f0Head -eq $p0bCandidateHead) {
  throw 'P0-B candidate 必须是完整 F0 的非空已提交后继'
}
$baselineHead = (& git rev-parse 'HEAD^{commit}').Trim()
if ($LASTEXITCODE -ne 0 -or $baselineHead -ne $p0bCandidateHead) {
  throw 'P0-C worktree 初始 HEAD 必须精确等于已批准 P0-B candidate'
}
$required = @(
  'docs/sql/ai-video/mysql/20260728_02_p0b_workspace_authorization.sql',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/authorization/service/IWorkspaceAuthorizationService.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/authorization/security/AppAuthorizationActorResolver.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/security/SysAuthorizationActorResolver.java'
)
$missing = $required | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; throw 'P0-B candidate 缺少冻结依赖' }
$baselineRecordPath = (& git rev-parse --git-path 'p0c-baseline.json').Trim()
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$vitestEvidenceGatePath = (& git rev-parse --git-path 'p0c-vitest-evidence-gate.ps1').Trim()
[pscustomobject]@{
  branch = $currentBranch
  worktreeRoot = $repoRoot
  baselineHead = $baselineHead
  f0Head = $f0Head
  p0bCandidateHead = $p0bCandidateHead
  p0bCandidateHandoff = $handoffPath
  owner = '开发 A'
  capturedAtUtc = [DateTime]::UtcNow.ToString('o')
} | ConvertTo-Json | Set-Content -LiteralPath $baselineRecordPath -Encoding UTF8
$gateScript = @'
param([Parameter(Mandatory = $true)][string] $RepoRoot)
$resolvedRoot = [System.IO.Path]::GetFullPath($RepoRoot)
if ([System.IO.Path]::GetFullPath((Get-Location).Path) -ne $resolvedRoot) { throw '执行目录不是已登记 P0-C worktree 根' }
$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -notlike 'codex/*' -or $branch -eq 'main') { throw 'P0-C 分支门禁失败' }
$recordPath = (& git rev-parse --git-path 'p0c-baseline.json').Trim()
if (-not (Test-Path -LiteralPath $recordPath -PathType Leaf)) { throw '缺少 P0-C 基线记录' }
$record = Get-Content -Raw -LiteralPath $recordPath | ConvertFrom-Json
if ($record.branch -ne $branch -or [System.IO.Path]::GetFullPath($record.worktreeRoot) -ne $resolvedRoot) { throw 'P0-C worktree／分支漂移' }
$baseline = (& git rev-parse "$($record.baselineHead)^{commit}").Trim()
$candidate = (& git rev-parse "$($record.p0bCandidateHead)^{commit}").Trim()
$head = (& git rev-parse 'HEAD^{commit}').Trim()
if ($LASTEXITCODE -ne 0 -or $baseline -ne $candidate -or $head -notmatch '^[0-9a-f]{40}$') { throw 'P0-C 基线／HEAD 无法验证' }
git merge-base --is-ancestor $candidate $head
if ($LASTEXITCODE -ne 0) { throw '当前 P0-C HEAD 不再包含已批准 P0-B candidate' }
'P0C_WORKTREE_GATE_OK'
'@
Set-Content -LiteralPath $gateScriptPath -Value $gateScript -Encoding UTF8
$itEvidenceGate = @'
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [Parameter(Mandatory = $true)][string[]] $Selector,
  [ValidateSet('Prepare', 'Assert')][string] $Mode = 'Assert',
  [ValidateSet('Surefire', 'Failsafe')][string] $ReportKind = 'Failsafe',
  [Nullable[DateTime]] $StartedAtUtc,
  [ValidateSet('Red', 'Green')][string] $ExpectedOutcome = 'Green'
)
$apiRoot = Join-Path ([System.IO.Path]::GetFullPath($RepoRoot)) 'ai-video-api'
$reportFolder = if ($ReportKind -eq 'Surefire') {
  'surefire-reports'
} else {
  'failsafe-reports'
}
if ($Mode -eq 'Prepare') {
  $oldReports = @(Get-ChildItem -LiteralPath $apiRoot -Recurse -File -Filter 'TEST-*.xml' |
    Where-Object {
      $reportName = $_.Name
      $_.FullName -match "[\\/]target[\\/]$reportFolder[\\/]" -and
      @($Selector | Where-Object {
        $reportName -like "TEST-*${_}.xml"
      }).Count -gt 0
    })
  foreach ($report in $oldReports) {
    Remove-Item -LiteralPath $report.FullName -Force
  }
  [DateTime]::UtcNow
  return
}
if (-not $StartedAtUtc.HasValue) { throw 'Assert 模式缺少 UTC 开始时间' }
$freshReports = @(Get-ChildItem -LiteralPath $apiRoot -Recurse -File -Filter 'TEST-*.xml' |
  Where-Object {
    $_.FullName -match "[\\/]target[\\/]$reportFolder[\\/]" -and
    $_.LastWriteTimeUtc -ge $StartedAtUtc.Value.ToUniversalTime()
  })
foreach ($className in $Selector) {
  $classReports = @($freshReports | Where-Object {
    $_.Name -like "TEST-*$className.xml"
  })
  if (-not $classReports) { throw "$className 未产生本次 $ReportKind XML" }
  $totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
  foreach ($report in $classReports) {
    [xml]$suite = Get-Content -Raw -LiteralPath $report.FullName
    $totals.tests += [int]$suite.testsuite.tests
    $totals.failures += [int]$suite.testsuite.failures
    $totals.errors += [int]$suite.testsuite.errors
    $totals.skipped += [int]$suite.testsuite.skipped
  }
  if ($totals.tests -le 0 -or $totals.skipped -ge $totals.tests) {
    throw "$className 没有本次非零、非全跳过执行证据"
  }
  if ($ExpectedOutcome -eq 'Green' -and
      ($totals.failures -ne 0 -or $totals.errors -ne 0 -or
       $totals.skipped -ne 0)) {
    throw "$className GREEN $ReportKind 证据不全绿"
  }
  if ($ExpectedOutcome -eq 'Red' -and
      ($totals.failures + $totals.errors) -le 0) {
    throw "$className RED 未产生真实失败断言"
  }
}
'P0C_IT_EVIDENCE_OK'
'@
Set-Content -LiteralPath $itEvidenceGatePath -Value $itEvidenceGate -Encoding UTF8
$vitestEvidenceGate = @'
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [Parameter(Mandatory = $true)][string] $ReportPath,
  [ValidateSet('Prepare', 'Assert')][string] $Mode = 'Assert',
  [Nullable[DateTime]] $StartedAtUtc,
  [ValidateSet('Red', 'Green')][string] $ExpectedOutcome = 'Green'
)
$resolvedRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$gitMetadataRoot = (& git -C $resolvedRoot rev-parse --absolute-git-dir).Trim()
if ($LASTEXITCODE -ne 0) { throw '无法解析当前 worktree Git 元数据目录' }
$resolvedMetadataRoot = [System.IO.Path]::GetFullPath($gitMetadataRoot)
$resolvedReportPath = [System.IO.Path]::GetFullPath($ReportPath)
$metadataPrefix = $resolvedMetadataRoot.TrimEnd(
  [System.IO.Path]::DirectorySeparatorChar,
  [System.IO.Path]::AltDirectorySeparatorChar) +
  [System.IO.Path]::DirectorySeparatorChar
if (-not $resolvedReportPath.StartsWith(
    $metadataPrefix, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Vitest JSON 必须写入当前 worktree Git 元数据目录'
}
if ($Mode -eq 'Prepare') {
  if (Test-Path -LiteralPath $resolvedReportPath) {
    Remove-Item -LiteralPath $resolvedReportPath -Force
  }
  [DateTime]::UtcNow
  return
}
if (-not $StartedAtUtc.HasValue) { throw 'Assert 模式缺少 UTC 开始时间' }
if (-not (Test-Path -LiteralPath $resolvedReportPath -PathType Leaf)) {
  throw '本次 Vitest 未产生 JSON 报告'
}
$reportFile = Get-Item -LiteralPath $resolvedReportPath
if ($reportFile.LastWriteTimeUtc -lt $StartedAtUtc.Value.ToUniversalTime()) {
  throw 'Vitest JSON 不是本次命令的新鲜证据'
}
$report = Get-Content -Raw -LiteralPath $resolvedReportPath | ConvertFrom-Json
$total = [int]$report.numTotalTests
$failed = [int]$report.numFailedTests
$pending = [int]$report.numPendingTests
if ($total -le 0) { throw 'Vitest 没有执行任何测试' }
if ($ExpectedOutcome -eq 'Red') {
  if ($failed -le 0 -or $pending -ge $total) {
    throw 'Vitest RED 必须包含真实失败且不能全部 pending'
  }
} elseif ($failed -ne 0 -or $pending -ne 0 -or
          -not [bool]$report.success) {
  throw 'Vitest GREEN 必须失败为零、pending 为零且 success=true'
}
'P0C_VITEST_EVIDENCE_OK'
'@
Set-Content -LiteralPath $vitestEvidenceGatePath `
  -Value $vitestEvidenceGate -Encoding UTF8
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁自检失败' }
```

预期：输出 `P0C_WORKTREE_GATE_OK`；任何分支、worktree、完整 SHA、handoff、祖先关系、已提交 candidate 或冻结依赖缺失都立即失败。

- [ ] **步骤 2（2–5 分钟）：编写失败的契约扫描**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$checks = @(
  @{ File = 'docs/API_CONTRACT.md'; Pattern = 'POST /api/studio/script-drafts' },
  @{ File = 'docs/API_CONTRACT.md'; Pattern = 'GET /api/tasks' },
  @{ File = 'docs/API_CONTRACT.md'; Pattern = 'GET /api/quota/ledger' },
  @{ File = 'docs/DOMAIN_MODEL.md'; Pattern = 'av_ai_operation_slot' },
  @{ File = 'docs/ASYNC_TASKS.md'; Pattern = 'root' },
  @{ File = 'docs/ASYNC_TASKS.md'; Pattern = 'IAiTaskExecutionDispatcher' },
  @{ File = 'docs/ASYNC_TASKS.md'; Pattern = 'STALE_BRANCH_RESULT' },
  @{ File = 'ai-video-ui/ai-video-webapp/PRD.md'; Pattern = '个人或组织' }
)
$failed = $checks | Where-Object {
  -not (Select-String -LiteralPath $_.File -SimpleMatch $_.Pattern -Quiet)
}
$redExitCode = if ($failed) { 1 } else { 0 }
if ($failed) { $failed | ForEach-Object { "$($_.File) => $($_.Pattern)" } }
if ($redExitCode -eq 0) { throw 'P0-C 契约扫描意外通过，未观察到预期红灯' }
```

预期：至少一个检查项缺失并退出 `1`。

- [ ] **步骤 3（2–5 分钟）：写入完整 P0-C 契约**

文档必须准确登记：

```text
方向：一个 published 目录、稳定行业/用途代码、30/45/60/90/120 秒。
草稿：请求只收 idempotencyKey；初始 draftRevision=0、
      branchRevision=1、generationContextRevision=0，并创建空首分支。
任务：root/execution、收费根任务唯一计费、免费根任务和执行任务不带 usageOperationId。
调度：create* 只创建 pending；阶段编排固定 create -> freeze -> enqueue，
      enqueue 同事务把根/执行都变 queued；首次领取把根/执行都变 running，
      租约恢复时根保持 running。
过期结果：提供商 attempt 可保持 success；结果事务发现冻结分支/上下文已变化时，
          父子任务 failed/STALE_BRANCH_RESULT、release 且不 settle、不重试提供商。
额度：ai_text_credit 整数；个人和组织不回退、不拼接、不透支。
账单：每次事件只追加；amount 正数，方向由 availableDelta/lockedDelta 表达。
并发：脚本资源固定先锁 draft、current branch，再锁 tenant+slotKey、quota account、task/group member；不同幂等键命中活跃槽返回 46123。
```

在 `docs/API_CONTRACT.md` 固定任务响应状态，在 `docs/DOMAIN_MODEL.md` 固定任务状态条件更新，在 `docs/ASYNC_TASKS.md` 固定 `enqueue`、领取租约、恢复流程和大写稳定失败码 `STALE_BRANCH_RESULT`，在 `docs/ARCHITECTURE.md` 固定 core（核心模块）端口和 infra（基础设施模块）SnailJob 归属。同步固定用户、运营接口、权限和数字错误码 `46114`～`46116`、`46119`、`46123`、`46124`。

- [ ] **步骤 4（2–5 分钟）：运行文档规范校验**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：输出 `DEVELOPMENT_STANDARDS_OK`。

- [ ] **步骤 5（2–5 分钟）：提交契约**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'docs/API_CONTRACT.md'
  'docs/DOMAIN_MODEL.md'
  'docs/ASYNC_TASKS.md'
  'docs/ARCHITECTURE.md'
  'docs/BACKEND_GUIDE.md'
  'docs/FRONTEND_GUIDE.md'
  'ai-video-ui/ai-video-webapp/PRD.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "docs: 冻结任务额度与草稿底座契约"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 2：创建 P0-C 数据库结构与可重复种子

**最小任务卡：**

- **单一目标／不做：** 创建 `03`／`04` 迁移、约束和可重复种子；不写生产价格、测试余额或演示任务。
- **风险／触发：** 红色；命中数据库结构、额度资产、任务状态与不可逆迁移。
- **权威来源：** `docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、批准规格和主计划迁移顺序。
- **成功／反向验收：** 本任务重放原始 `01→02→03→04` 结构；全计划完成前还必须由任务 18 将完整 `01→02→03→04→04a` 再执行两次并验证一致。非法父子、计费形状、租约、状态、跨租户 membership 和非法 origin／creator 均被数据库拒绝；越过 candidate 立即阻塞。
- **所有权／数据范围：** 仅两份 SQL 与 `P0cSchemaIT`；测试只清理本机 `ai_video_test` 和本次 Redis 前缀。
- **依赖／人员／并发：** 依赖任务 1；开发 A 实施、开发 C 独立迁移 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑精确 RED/GREEN 与本次 XML，不得判 PASS；reviewer 核对 DDL、幂等和回滚证据。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql`
- 创建：`docs/sql/ai-video/mysql/20260728_04_p0_seed.sql`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/P0cSchemaIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的结构、约束和种子测试**

```java
private void execute(String relativePath) throws SQLException {
    Path sqlRoot = Path.of(
        System.getProperty("maven.multiModuleProjectDirectory"),
        "script", "sql").toAbsolutePath().normalize();
    Path script = sqlRoot.resolve(relativePath).normalize();
    if (!script.startsWith(sqlRoot)) {
        throw new IllegalArgumentException("SQL 路径越界");
    }
    ScriptUtils.executeSqlScript(
        connection, new FileSystemResource(script));
}

@Test
void schemaAndSeedAreRepeatableAndEnforceRootBillingShape() throws Exception {
    execute("ai-video/mysql/20260728_01_p0a_identity_security.sql");
    execute("ai-video/mysql/20260728_02_p0b_workspace_authorization.sql");
    execute("ai-video/mysql/20260728_03_p0c_task_quota_direction.sql");
    execute("ai-video/mysql/20260728_04_p0_seed.sql");
    execute("ai-video/mysql/20260728_04_p0_seed.sql");

    assertThat(count("av_direction_catalog_version", "status = 'published'"))
        .isEqualTo(1);
    assertThat(count("av_industry_option", "enabled = 1")).isEqualTo(6);
    assertThat(count("av_quota_tariff", "1 = 1")).isZero();

    assertThatThrownBy(() -> insertExecutionTaskWithUsageOperation())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertChargeableRootWithoutUsageOperation())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertFreeRootWithUsageOperation())
        .isInstanceOf(SQLException.class);
    insertFreeRootWithoutUsageOperation();
    assertThatThrownBy(() -> insertExecutionWithoutRoot())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertRootWithParent())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertRunningExecutionWithoutLease())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertQueuedExecutionWithLease())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertDuplicateRootUsageOperation())
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertTaskWithBillingMode("legacy"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertAttemptWithoutLeaseExpiry())
        .isInstanceOf(SQLException.class);
    assertThat(indexColumns("av_ai_task", "idx_av_ai_task_dispatch"))
        .containsExactly(
            "task_role", "status", "lease_expires_at", "id");
    assertThat(indexColumns(
        "av_ai_task_attempt", "uk_av_ai_task_attempt_execution_call"))
        .containsExactly("execution_task_id", "provider_call_sequence");
    assertThatThrownBy(() ->
        insertTaskWithType("question_" + "generation"))
        .isInstanceOf(SQLException.class);
    assertThatThrownBy(() -> insertDuplicateSlot("tenant-1", "script:1:1:hash"))
        .isInstanceOf(SQLException.class);
}
```

- [ ] **步骤 2（2–5 分钟）：运行集成测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('P0cSchemaIT') `
  -Mode 'Prepare' | Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=P0cSchemaIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('P0cSchemaIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：迁移骨架可执行、Failsafe 有非零执行数，并由目标数据库结构断言失败。

- [ ] **步骤 3（2–5 分钟）：写最小完整 DDL 和确定性种子**

关键表约束必须包含：

```sql
CREATE TABLE IF NOT EXISTS av_quota_account (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    unit_code VARCHAR(32) NOT NULL,
    available_balance BIGINT NOT NULL DEFAULT 0,
    locked_balance BIGINT NOT NULL DEFAULT 0,
    used_balance BIGINT NOT NULL DEFAULT 0,
    account_revision BIGINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_quota_subject_unit
        (tenant_id, subject_type, subject_id, unit_code),
    CONSTRAINT ck_av_quota_available_nonnegative CHECK (available_balance >= 0),
    CONSTRAINT ck_av_quota_locked_nonnegative CHECK (locked_balance >= 0),
    CONSTRAINT ck_av_quota_used_nonnegative CHECK (used_balance >= 0),
    CONSTRAINT ck_av_quota_personal_subject
        CHECK (subject_type = 'app_user')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_ai_operation_slot (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    slot_key VARCHAR(255) NOT NULL,
    active_root_task_id BIGINT NULL,
    slot_revision BIGINT NOT NULL DEFAULT 0,
    occupied_at DATETIME NULL,
    released_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_ai_operation_slot (tenant_id, slot_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_quota_ledger_entry (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id BIGINT NOT NULL,
    operation_id BIGINT NULL,
    operation_type VARCHAR(64) NOT NULL,
    ledger_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    available_delta BIGINT NOT NULL,
    locked_delta BIGINT NOT NULL,
    available_before BIGINT NOT NULL,
    available_after BIGINT NOT NULL,
    locked_before BIGINT NOT NULL,
    locked_after BIGINT NOT NULL,
    tariff_code VARCHAR(64) NULL,
    tariff_version BIGINT NULL,
    idempotency_key VARCHAR(128) NULL,
    request_hash CHAR(64) NULL,
    draft_id BIGINT NULL,
    question_id BIGINT NULL,
    root_task_id BIGINT NULL,
    task_id BIGINT NULL,
    attempt_id BIGINT NULL,
    script_id BIGINT NULL,
    script_version_id BIGINT NULL,
    provider VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    provider_cost DECIMAL(20, 8) NULL,
    currency CHAR(3) NULL,
    failure_code VARCHAR(64) NULL,
    related_ledger_id BIGINT NULL,
    event_key VARCHAR(255) NOT NULL,
    created_by_type VARCHAR(32) NOT NULL,
    created_by_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_quota_ledger_event (event_key),
    CONSTRAINT ck_av_quota_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_av_quota_ledger_available_equation
        CHECK (available_after = available_before + available_delta),
    CONSTRAINT ck_av_quota_ledger_locked_equation
        CHECK (locked_after = locked_before + locked_delta),
    CONSTRAINT ck_av_quota_ledger_after_nonnegative
        CHECK (available_after >= 0 AND locked_after >= 0),
    CONSTRAINT ck_av_quota_ledger_creator
        CHECK (created_by_type IN ('app_user', 'sys_user'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`av_script_draft` 对 `(tenant_id, created_by_user_id, create_idempotency_key)` 建唯一键。`av_ai_usage_operation` 使用 `initiated_by_type + initiated_by_id` 保存强类型发起主体，并对 `(tenant_id, initiated_by_type, initiated_by_id, operation_type, idempotency_key)` 建唯一键；不得以同号推导或关联 `app_user/sys_user`。任务与尝试表至少包含以下完整关键列、索引和约束：

```sql
CREATE TABLE IF NOT EXISTS av_ai_task (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_role VARCHAR(16) NOT NULL,
    root_task_id BIGINT NULL,
    execution_no INT NULL,
    billing_mode VARCHAR(16) NOT NULL,
    usage_operation_id BIGINT NULL,
    task_type VARCHAR(32) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id BIGINT NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    actor_id BIGINT NOT NULL,
    task_family_key VARCHAR(255) NOT NULL,
    task_group_key VARCHAR(255) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    free_root_idempotency_key VARCHAR(128)
        GENERATED ALWAYS AS (
            CASE
                WHEN task_role = 'root' AND billing_mode = 'free'
                THEN idempotency_key
                ELSE NULL
            END
        ) STORED,
    request_hash CHAR(64) NOT NULL,
    revision_snapshot_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    lease_owner VARCHAR(128) NULL,
    lease_expires_at DATETIME(3) NULL,
    lease_count INT NOT NULL DEFAULT 0,
    max_lease_attempts INT NOT NULL DEFAULT 3,
    last_handler_error_code VARCHAR(64) NULL,
    last_handler_error_message VARCHAR(512) NULL,
    result_type VARCHAR(64) NULL,
    result_id BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    queued_at DATETIME(3) NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    task_revision BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_ai_task_usage_operation (usage_operation_id),
    UNIQUE KEY uk_av_ai_task_free_root_intent
        (tenant_id, actor_type, actor_id, task_type,
         free_root_idempotency_key),
    UNIQUE KEY uk_av_ai_task_single_execution
        (root_task_id),
    KEY idx_av_ai_task_root
        (root_task_id, task_role, id),
    KEY idx_av_ai_task_dispatch
        (task_role, status, lease_expires_at, id),
    CONSTRAINT fk_av_ai_task_root
        FOREIGN KEY (root_task_id) REFERENCES av_ai_task (id),
    CONSTRAINT fk_av_ai_task_usage_operation
        FOREIGN KEY (usage_operation_id)
        REFERENCES av_ai_usage_operation (id),
    CONSTRAINT ck_av_ai_task_role
        CHECK (task_role IN ('root', 'execution')),
    CONSTRAINT ck_av_ai_task_billing_mode
        CHECK (billing_mode IN ('chargeable', 'free')),
    CONSTRAINT ck_av_ai_task_parent_shape CHECK (
        (task_role = 'root'
         AND root_task_id IS NULL
         AND execution_no IS NULL)
        OR
        (task_role = 'execution'
         AND root_task_id IS NOT NULL
         AND execution_no = 1)
    ),
    CONSTRAINT ck_av_ai_task_billing_shape CHECK (
        (task_role = 'execution' AND usage_operation_id IS NULL)
        OR
        (task_role = 'root'
         AND billing_mode = 'chargeable'
         AND usage_operation_id IS NOT NULL)
        OR
        (task_role = 'root'
         AND billing_mode = 'free'
         AND usage_operation_id IS NULL)
    ),
    CONSTRAINT ck_av_ai_task_type CHECK (
        task_type IN (
            'question_generate',
            'evidence_retrieve',
            'script_generate',
            'script_optimize',
            'knowledge_import'
        )
    ),
    CONSTRAINT ck_av_ai_task_status CHECK (
        status IN (
            'pending', 'queued', 'running',
            'success', 'failed', 'cancelled'
        )
    ),
    CONSTRAINT ck_av_ai_task_lease_count CHECK (
        lease_count >= 0
        AND max_lease_attempts > 0
        AND lease_count <= max_lease_attempts
    ),
    CONSTRAINT ck_av_ai_task_actor_type
        CHECK (actor_type IN ('app_user', 'sys_user')),
    CONSTRAINT ck_av_ai_task_lease_shape CHECK (
        (task_role = 'execution'
         AND status = 'running'
         AND lease_owner IS NOT NULL
         AND lease_expires_at IS NOT NULL)
        OR
        ((task_role <> 'execution' OR status <> 'running')
         AND lease_owner IS NULL
         AND lease_expires_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_ai_task_attempt (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    root_task_id BIGINT NOT NULL,
    execution_task_id BIGINT NOT NULL,
    provider_call_sequence INT NOT NULL,
    call_purpose VARCHAR(64) NOT NULL,
    lease_owner VARCHAR(128) NOT NULL,
    lease_expires_at DATETIME(3) NOT NULL,
    status VARCHAR(16) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NULL,
    provider_request_id VARCHAR(255) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    provider_cost DECIMAL(20, 8) NULL,
    currency CHAR(3) NULL,
    input_hash CHAR(64) NOT NULL,
    output_hash CHAR(64) NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(512) NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_ai_task_attempt_execution_call
        (execution_task_id, provider_call_sequence),
    KEY idx_av_ai_task_attempt_root
        (root_task_id, started_at, id),
    KEY idx_av_ai_task_attempt_status
        (status, started_at, id),
    CONSTRAINT fk_av_ai_task_attempt_root
        FOREIGN KEY (root_task_id) REFERENCES av_ai_task (id),
    CONSTRAINT fk_av_ai_task_attempt_execution
        FOREIGN KEY (execution_task_id) REFERENCES av_ai_task (id),
    CONSTRAINT ck_av_ai_task_attempt_call_sequence
        CHECK (provider_call_sequence > 0),
    CONSTRAINT ck_av_ai_task_attempt_status
        CHECK (status IN ('running', 'success', 'failed', 'cancelled')),
    CONSTRAINT ck_av_ai_task_attempt_tokens CHECK (
        (input_tokens IS NULL OR input_tokens >= 0)
        AND (output_tokens IS NULL OR output_tokens >= 0)
    ),
    CONSTRAINT ck_av_ai_task_attempt_cost
        CHECK (provider_cost IS NULL OR provider_cost >= 0),
    CONSTRAINT ck_av_ai_task_attempt_finish CHECK (
        (status = 'running' AND finished_at IS NULL)
        OR
        (status IN ('success', 'failed', 'cancelled')
         AND finished_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`idx_av_ai_task_dispatch(task_role, status, lease_expires_at, id)` 是 `queued` 与租约过期 `running` 的唯一扫描索引；`fk_av_ai_task_root` 固定 execution（执行任务）到 root（根任务）的父子引用，`uk_av_ai_task_single_execution(root_task_id)` 利用 MySQL 允许多个 `NULL` 的唯一索引语义，硬性保证每个根任务恰好最多一个 `executionNo=1` 执行子任务，服务和集成测试再断言被引用行确为同租户 root。`uk_av_ai_task_usage_operation` 保证一个收费根任务操作只绑定一次；MySQL 对免费根任务的 `NULL` 不施加互斥。`enqueue` 同事务更新 execution/root `pending -> queued`，首次领取同事务更新 execution/root `queued -> running`；SQL `CHECK` 不能跨行比较父子状态，因此 Mapper 条件更新数与集成测试共同守住父子状态一致性，任一更新失败都回滚。`lease_count/max_lease_attempts` 只统计执行租约次数，与真实提供商调用次数完全分开；`av_ai_task_attempt` 只按 `(execution_task_id, provider_call_sequence)` 记录真实模型或检索调用。

相同免费任务键和摘要复用，相同键不同摘要返回 `46116`。价格对 `(tariff_code, version)` 唯一，发布事务按价格代码锁定并禁止生效区间重叠。种子发布六个行业项：电商零售、教育培训、餐饮美食、家居装修、本地生活、自定义；每个行业写入稳定用途代码和 `custom`，目标时长只写 30/45/60/90/120。运营菜单的组件 key 分别固定为 `aivideo/task/index`（任务链）和 `aivideo/usage/index`（计费操作），不得合并成同一个页面。

- [ ] **步骤 4（2–5 分钟）：重新运行结构集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('P0cSchemaIT') `
  -Mode 'Prepare' | Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=P0cSchemaIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('P0cSchemaIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：迁移和种子重复执行通过；没有生产价格；唯一键与检查约束断言通过。

- [ ] **步骤 5（2–5 分钟）：提交迁移**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql'
  'docs/sql/ai-video/mysql/20260728_04_p0_seed.sql'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/P0cSchemaIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 建立任务额度方向与草稿表"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 3：实现方向目录查询、复制、编辑和发布

**最小任务卡：**

- **单一目标／不做：** 实现已发布方向快照及运营复制／编辑／发布；不把端侧 BO／VO 放进 core，不让下游直读 Mapper。
- **风险／触发：** 红色；命中共享跨阶段 DTO、发布状态和高权限运营写入。
- **权威来源：** 主计划 `IDirectionCatalogService`／`DirectionCatalogSnapshotDTO` 冻结项、API 契约和 RuoYi 分层。
- **成功／反向验收：** 仅已发布版本可被跨阶段读取，修订冲突／无权限／重复发布失败关闭，HTTP BO／VO 只在端侧。
- **所有权／数据范围：** 仅 direction core、platform 端侧对象和本任务测试；只影响方向目录表。
- **依赖／人员／并发：** 依赖任务 2；开发 A 实施、开发 C 独立契约／权限 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑方向 RED/GREEN 与 XML，不得判 PASS；reviewer 核对稳定快照和权限反例。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskInitiatorDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoBusinessException.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/DirectionCatalogStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvDirectionCatalogVersion.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvIndustryOption.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvPurposeOption.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/DirectionCatalogVersionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/IndustryOptionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/PurposeOptionMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/IDirectionCatalogService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/impl/DirectionCatalogServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/direction/DirectionCatalogServiceIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的目录版本测试**

```java
@Test
void publishedOptionsAreStableAndPurposeGroupedByIndustry() {
    DirectionCatalogSnapshotDTO options = service.currentPublishedCatalog();
    assertThat(options.catalogVersion()).isEqualTo(1L);
    assertThat(options.industryCatalogVersion()).isPositive();
    assertThat(options.purposeCatalogVersion()).isPositive();
    assertThat(options.durationRuleVersion()).isNotBlank();
    assertThat(options.industries()).extracting("code")
        .containsExactly("ecommerce", "education", "food", "home", "local", "custom");
    assertThat(options.targetDurations()).extracting("seconds")
        .containsExactly(30, 45, 60, 90, 120);
    assertThat(options.purposesByIndustry().get("ecommerce"))
        .extracting("code")
        .contains("product_service_intro", "custom");
}

@Test
void snapshotFreezesAggregateAndServerOnlyTraceVersionsInExactOrder() {
    assertThat(DirectionCatalogSnapshotDTO.class.getRecordComponents())
        .extracting("name")
        .containsExactly(
            "catalogVersion", "contentHash",
            "industryCatalogVersion", "purposeCatalogVersion", "durationRuleVersion",
            "industries", "purposesByIndustry", "targetDurations");
    assertThatThrownBy(() -> snapshotFixture(0L, 1L, "duration-v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotFixture(1L, 0L, "duration-v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotFixture(1L, 1L, " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotFixture(null, 1L, "duration-v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotFixture(1L, null, "duration-v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> snapshotFixture(1L, 1L, null))
        .isInstanceOf(IllegalArgumentException.class);
}

private DirectionCatalogSnapshotDTO snapshotFixture(
    Long industryCatalogVersion,
    Long purposeCatalogVersion,
    String durationRuleVersion
) {
    DirectionCatalogSnapshotDTO published = service.currentPublishedCatalog();
    return new DirectionCatalogSnapshotDTO(
        published.catalogVersion(), published.contentHash(),
        industryCatalogVersion, purposeCatalogVersion, durationRuleVersion,
        published.industries(), published.purposesByIndustry(),
        published.targetDurations());
}

@Test
void publishUsesRevisionAndRetiresPreviousVersionAtomically() {
    TaskInitiatorDTO initiator = new TaskInitiatorDTO("sys_user", 9001L);
    Long draftId = service.copyPublishedToDraft("调整电商用途", initiator);
    service.publish(draftId, 1L, "发布目录第 2 版", initiator);
    assertThat(catalogMapper.countByStatus("published")).isEqualTo(1);
    assertThat(catalogMapper.selectById(draftId).getVersion()).isEqualTo(2L);
}
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=DirectionCatalogServiceIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('DirectionCatalogServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：生产骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现公共错误与最小目录服务**

```java
@Transactional
public void publish(Long catalogId, Long expectedRevision, String note,
                    TaskInitiatorDTO initiator) {
    initiator.requireActorType("sys_user");
    AvDirectionCatalogVersion draft = catalogMapper.selectForUpdate(catalogId);
    if (!"draft".equals(draft.getStatus())
        || !Objects.equals(draft.getRevision(), expectedRevision)) {
        throw new AiVideoBusinessException(
            AiVideoErrorCode.DIRECTION_CATALOG_CHANGED,
            "方向目录已变化，请刷新后重新发布",
            Map.of("currentRevision", Long.toString(draft.getRevision())));
    }
    validateStableCodesAndBindings(catalogId);
    catalogMapper.retirePublished(LocalDateTime.now());
    draft.publish(catalogMapper.nextVersion(), contentHash(catalogId),
        initiator, note, LocalDateTime.now());
    catalogMapper.updateById(draft);
}
```

`AiVideoErrorCode` 与 `AiVideoBusinessException` 严格按本计划固定签名创建，不借用 HTTP 状态代替业务码。`TaskInitiatorDTO` 是 core 唯一的强类型发起主体值对象；当前请求身份只能由 user/platform 端侧具体 Resolver 解析并显式传入 Service，core 不声明 Resolver bean，也不依赖任一登录助手。所有目录代码唯一、名称非空、排序为非负整数；用途必须引用同版本行业；`custom` 项允许自定义。目录复制、编辑和发布要求显式 `sys_user`，发布后旧版本只退役，不更新选项正文。`DirectionCatalogSnapshotDTO` 的八个 record component 必须保持上述精确顺序；`industryCatalogVersion`、`purposeCatalogVersion` 必须为正数，`durationRuleVersion` 必须非空。三个子版本与 `contentHash` 都是服务端追溯事实，HTTP 方向选项与保存契约仍只公开聚合 `catalogVersion` / 接收 `expectedCatalogVersion`，客户端不得提交或覆盖三个子版本。当前行业／用途共用一份 published 目录时，目录装配器可分别把该已发布版本显式写入两个子版本，但下游仍只能读取 DTO 字段，不能假设二者永远相等；`durationRuleVersion` 来自服务端拥有的固定时长规则版本。三个子版本、选项正文和时长规则共同参与 `contentHash`，任一变化都必须发布新的聚合 `catalogVersion`。后续 P2 保存方向时必须读取同一次 published snapshot，在校验聚合版本和选项后从该快照派生并持久化三个子版本。

- [ ] **步骤 4（2–5 分钟）：运行目录集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=DirectionCatalogServiceIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('DirectionCatalogServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：查询、复制、保存、修订冲突、并发发布和唯一 published 版本用例通过。

- [ ] **步骤 5（2–5 分钟）：提交方向目录**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskInitiatorDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoBusinessException.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/DirectionCatalogStatus.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvDirectionCatalogVersion.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvIndustryOption.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/domain/AvPurposeOption.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/DirectionCatalogVersionMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/IndustryOptionMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/mapper/PurposeOptionMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/IDirectionCatalogService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/impl/DirectionCatalogServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/direction/DirectionCatalogServiceIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 实现版本化方向目录"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 4：实现工作区冻结的幂等草稿启动

**最小任务卡：**

- **单一目标／不做：** 以当前工作区可信事实幂等创建草稿和首分支；不接受客户端 owner／billing 主体，不重绑既有草稿。
- **风险／触发：** 红色；命中身份授权、跨账号数据、共享文件与幂等并发。
- **权威来源：** P0-B `IWorkspaceAuthorizationService`、主计划草稿 DTO 注册表和对象归属契约。
- **成功／反向验收：** 同键同摘要复用、异摘要冲突，伪造主体／跨账号／并发重复创建零副作用。
- **所有权／数据范围：** 仅 studio core、六个共享文件中的本阶段 owner 范围及测试；只写目标草稿／首分支。
- **依赖／人员／并发：** 依赖任务 1–3；开发 A 独占共享文件、开发 C 独立授权 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑草稿 RED/GREEN 与本次报告，不得判 PASS；reviewer 核对 owner、幂等和移交边界。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptDraft.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/CreateScriptDraftDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/ScriptDraftOverviewDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/StepGuardDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/IScriptDraftService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/impl/ScriptDraftServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/security/ScriptDraftOwnershipResolver.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的草稿幂等与归属测试**

```java
@Test
void createFreezesWorkspaceAndCreatesInitialBranch() {
    when(workspaceAuthorizationService.resolveCurrentWorkspace())
        .thenReturn(organizationWorkspace(7L, 101L));

    ScriptDraftOverviewDTO created = service.create(
        new CreateScriptDraftDTO("intent-001"));

    assertThat(created.draftRevision()).isZero();
    assertThat(created.branchRevision()).isEqualTo(1L);
    assertThat(created.generationContextRevision()).isZero();
    assertThat(created.resourceScope().type()).isEqualTo("organization");
    assertThat(created.billingSubject().type()).isEqualTo("app_user");
    assertThat(created.billingSubject().id()).isEqualTo(101L);
    assertThat(branchMapper.countByDraftId(created.draftId())).isEqualTo(1);
    verify(workspaceAuthorizationService).initializeCreatorGrant(
        any(), eq(AppActorContext.appUser(101L)));
}

@Test
void sameKeySameWorkspaceReturnsOriginalButChangedWorkspaceConflicts() {
    ScriptDraftOverviewDTO first = service.create(
        new CreateScriptDraftDTO("intent-002"));
    ScriptDraftOverviewDTO retry = service.create(
        new CreateScriptDraftDTO("intent-002"));
    assertThat(retry.draftId()).isEqualTo(first.draftId());
    assertThat(retry.reused()).isTrue();

    switchToOtherWorkspace();
    assertThatThrownBy(() -> service.create(
        new CreateScriptDraftDTO("intent-002")))
        .isInstanceOf(AiVideoBusinessException.class)
        .extracting("code")
        .isEqualTo(46116);
}
```

- [ ] **步骤 2（2–5 分钟）：运行集成测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=ScriptDraftServiceIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('ScriptDraftServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：草稿服务骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小草稿事务和七步守卫**

创建事务固定为：

```java
@Transactional
public ScriptDraftOverviewDTO create(CreateScriptDraftDTO command) {
    WorkspaceContextDTO workspace =
        workspaceAuthorizationService.resolveCurrentWorkspace();
    workspaceAuthorizationService.requireWorkspacePermission(
        "aivideo:studio:create");
    String requestHash = draftRequestHash(workspace);
    AvScriptDraft existing = draftMapper.selectByIdempotency(
        workspace.tenantId(), workspace.actorUserId(), command.idempotencyKey());
    if (existing != null) {
        if (!existing.getCreateRequestHash().equals(requestHash)) {
            throw new AiVideoBusinessException(46116,
                "该新建请求已用于另一个工作区", null);
        }
        return overview(existing, true);
    }

    AvScriptDraft draft = AvScriptDraft.initial(
        workspace, command.idempotencyKey(), requestHash);
    draftMapper.insert(draft);
    branchMapper.insert(AvScriptBranch.initial(draft.getId()));
    workspaceAuthorizationService.initializeCreatorGrant(
        draft.toOwnership(),
        AppActorContext.appUser(workspace.actorUserId()));
    return overview(draft, false);
}
```

总览恰好返回七个 `StepGuardDTO`：`demand` 为 `current`，其余六项为 `locked`，中文原因分别说明方向/问卷/文案确认或对应产品能力尚未完成；不能以本地点击记录解锁。

并发唯一键冲突读取原草稿并比较请求摘要；HTTP 请求对象只含 `idempotencyKey`，不含租户、用户、所有者和计费主体字段。

- [ ] **步骤 4（2–5 分钟）：运行草稿集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=ScriptDraftServiceIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('ScriptDraftServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：个人/组织归属、首分支、三修订号、并发幂等、工作区摘要变化冲突、对象授权和七步守卫全部通过。

- [ ] **步骤 5（2–5 分钟）：提交草稿底座**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptDraft.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/CreateScriptDraftDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/ScriptDraftOverviewDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/StepGuardDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/IScriptDraftService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/impl/ScriptDraftServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/security/ScriptDraftOwnershipResolver.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 实现工作区冻结的草稿入口"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 5：实现额度账户、价格版本和管理员调整

**最小任务卡：**

- **单一目标／不做：** 实现额度账户、价格版本和运营调整；不允许透支、固定生产价格或跨计费主体回退。
- **风险／触发：** 红色；命中资金资产、高权限运营、审计与并发锁。
- **权威来源：** 额度／价格公共契约、主计划 `IQuotaBillingService` 签名和 RuoYi 权限规则。
- **成功／反向验收：** 个人账户按用户隔离，价格版本精确，重复调整幂等；错误用户、错误个人租户、过期版本、账户缺失和额度不足均零副作用，且不得创建零账户。
- **所有权／数据范围：** 仅 quota core、platform BO／VO／接口及测试；只影响目标账户、价格和审计行。
- **依赖／人员／并发：** 依赖任务 2；开发 A 实施、开发 C 独立资金 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑额度 RED/GREEN 与 XML，不得判 PASS；reviewer 复核锁顺序、审计和负向矩阵。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/QuotaUnit.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/TariffStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/UsageOperationStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/LedgerType.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockRequestDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockResultDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaAccountSnapshotDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaAccount.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaTariff.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvAiUsageOperation.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaLedgerEntry.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaAccountMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaTariffMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/AiUsageOperationMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaLedgerEntryMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaAccountMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/AiUsageOperationMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaLedgerEntryMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/impl/QuotaBillingServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的账户和价格测试**

```java
@Test
void organizationWorkspaceUsesInitiatingUsersPersonalAccount() {
    WorkspaceContextDTO organization = organizationWorkspace(7L, 101L);
    when(accountMapper.selectBySubject(
        organization.personalTenantId(), "app_user", 101L, "ai_text_credit"))
        .thenReturn(account(0L, 0L));

    assertThatThrownBy(() -> service.lock(lockRequest(
        organization, 10L,
        new TaskInitiatorDTO("app_user", 101L))))
        .isInstanceOf(AiVideoBusinessException.class)
        .satisfies(error -> {
            AiVideoBusinessException business = (AiVideoBusinessException) error;
            assertThat(business.getCode()).isEqualTo(46114);
            assertThat(business.getData()).extracting("billingSubjectType")
                .isEqualTo("app_user");
        });
    verify(accountMapper).selectBySubject(
        organization.personalTenantId(), "app_user", 101L, "ai_text_credit");
    verifyNoMoreInteractions(accountMapper);
}

@Test
void changedTariffRequiresExplicitReconfirmation() {
    when(tariffMapper.selectActive("question_generate"))
        .thenReturn(activeTariff("question_generate", 3L, 10L));
    assertThatThrownBy(() -> service.lock(
        lockRequestWithExpectedTariff(
            "question_generate", 2L,
            new TaskInitiatorDTO("app_user", 101L))))
        .isInstanceOf(AiVideoBusinessException.class)
        .extracting("code")
        .isEqualTo(46115);
}

@Test
void adjustmentRequiresSysInitiatorAndPersistsTypedActor() {
    TaskInitiatorDTO initiator =
        new TaskInitiatorDTO("sys_user", 9001L);

    service.adjust(adjustRequest(25L), initiator);
    AvQuotaLedgerEntry adjusted = ledgerMapper.selectLatest();

    assertThat(adjusted.getCreatedByType()).isEqualTo("sys_user");
    assertThat(adjusted.getCreatedById()).isEqualTo(9001L);
}

@Test
void userLockFreezesAppInitiatorForLaterAsyncSettlement() {
    TaskInitiatorDTO initiator =
        new TaskInitiatorDTO("app_user", 101L);
    QuotaLockResultDTO lock = service.lock(
        lockRequest(personalWorkspace(101L), 10L, initiator));

    assertThat(usageOperationMapper.selectById(lock.operationId()))
        .extracting("initiatedByType", "initiatedById")
        .containsExactly("app_user", 101L);
}
```

- [ ] **步骤 2（2–5 分钟）：运行单元测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('QuotaBillingServiceTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=QuotaBillingServiceTest test
$redExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：额度服务骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现账户与价格最小规则**

`resolveAccount` 只读取已由明确配置或发放流程创建的 `app_user` 个人账户；账户缺失时返回稳定错误并保持零写入，不得在查询或任务链路隐式创建零余额账户。`resolveDraftAccount` 先对草稿执行 `requireResourceAction(..., QUERY)`，再只读取草稿冻结的发起用户个人主体；组织工作区不产生独立额度账户。

价格发布事务固定为：

```java
@Transactional
public void publishTariff(Long tariffId, LocalDateTime effectiveAt,
                          String publishNote,
                          TaskInitiatorDTO initiator) {
    initiator.requireActorType("sys_user");
    AvQuotaTariff draft = tariffMapper.selectForUpdate(tariffId);
    requirePositive(draft.getFixedQuota());
    tariffMapper.lockByTariffCode(draft.getTariffCode());
    tariffMapper.retireOverlapping(
        draft.getTariffCode(), effectiveAt, initiator);
    draft.publish(nextVersion(draft.getTariffCode()), effectiveAt,
        initiator, publishNote);
    tariffMapper.updateById(draft);
}
```

`TaskInitiatorDTO` 只允许 `actorType=app_user/sys_user` 且编号为正数。`IQuotaBillingService.lock` 必须解析并要求 `app_user`，再校验其编号等于工作区当前创作用户；它把该强类型主体写入 `av_ai_usage_operation` 和根任务，供异步结算/释放读取。价格发布、管理员调整请求必须解析并要求 `sys_user`；调整使用有符号 `delta`，写流水时 `amount = abs(delta)`，`availableDelta = delta`，`lockedDelta = 0`。负向调整只扣可用余额，条件更新保证不为负。核心服务不得无条件依赖 `AppActorContext`、默认 `LoginHelper` 或任一 Web 登录上下文。

- [ ] **步骤 4（2–5 分钟）：运行单元测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('QuotaBillingServiceTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=QuotaBillingServiceTest test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
```

预期：个人账户缺失零写入、个人账户隔离、无跨用户或跨主体回退、正负调整、价格冲突和版本生效测试通过。

- [ ] **步骤 5（2–5 分钟）：提交账户和价格**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/QuotaUnit.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/TariffStatus.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/UsageOperationStatus.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/LedgerType.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockRequestDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockResultDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaAccountSnapshotDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaAccount.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaTariff.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvAiUsageOperation.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/domain/AvQuotaLedgerEntry.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaAccountMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaTariffMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/AiUsageOperationMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/mapper/QuotaLedgerEntryMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaAccountMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/AiUsageOperationMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaLedgerEntryMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/impl/QuotaBillingServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 实现额度账户与价格版本"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 6：实现不可变流水和恰好一次结算

**最小任务卡：**

- **单一目标／不做：** 实现 lock／settle／release／refund／compensation 不可变流水；不更新历史流水，不重复扣减或释放。
- **风险／触发：** 红色；命中资产、并发竞争、补偿和审计。
- **权威来源：** 主计划 `IQuotaBillingService` 冻结签名、领域模型与异步任务结算规则。
- **成功／反向验收：** 结算／释放竞争仅一方成功，重复调用幂等，余额与流水守恒；任何失败不留半成状态。
- **所有权／数据范围：** 仅 quota Service／Mapper／测试；只影响同一操作和计费主体账户。
- **依赖／人员／并发：** 依赖任务 5；开发 A 实施、开发 C 独立资金／并发 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑单元、并发 IT 与精确报告，不得判 PASS；reviewer 独立核对守恒和竞态。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/impl/QuotaBillingServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaAccountMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/AiUsageOperationMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaLedgerEntryMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的账务恒等和并发终态测试**

```java
@ParameterizedTest
@MethodSource("ledgerCases")
void everyLedgerEventPreservesBalanceEquations(
    LedgerType type, long amount, long availableDelta, long lockedDelta) {
    AvQuotaLedgerEntry entry = ledger(type, amount, availableDelta, lockedDelta);
    assertThat(entry.getAvailableAfter())
        .isEqualTo(entry.getAvailableBefore() + entry.getAvailableDelta());
    assertThat(entry.getLockedAfter())
        .isEqualTo(entry.getLockedBefore() + entry.getLockedDelta());
    assertThat(entry.getAmount()).isPositive();
}

@Test
void settleAndReleaseRaceHasOneWinnerAndOneTerminalLedger() {
    Long operationId = lockedOperation(10L);
    runConcurrently(
        () -> service.settle(operationId, 500L),
        () -> service.release(operationId, 500L, "cancelled"));

    AvAiUsageOperation operation = operationMapper.selectById(operationId);
    assertThat(operation.getStatus()).isIn("settled", "released");
    assertThat(ledgerMapper.countTerminalEvents(operationId)).isEqualTo(1);
    assertThat(accountMapper.selectById(operation.getAccountId())
        .getLockedBalance()).isZero();
    assertThat(ledgerMapper.selectTerminalByOperation(operationId))
        .extracting("createdByType", "createdById")
        .containsExactly(
            operation.getInitiatedByType(), operation.getInitiatedById());
}

@Test
void refundAndCompensationRequireAndPersistSysInitiator() {
    TaskInitiatorDTO initiator =
        new TaskInitiatorDTO("sys_user", 9002L);

    service.refund(700L, "人工复核退款", initiator);
    service.compensate(
        701L, -2L, "case-91", "成本纠正", initiator);
    List<AvQuotaLedgerEntry> persisted =
        ledgerMapper.selectByCreatedBy("sys_user", 9002L);

    assertThat(persisted)
        .hasSize(2)
        .allSatisfy(entry -> {
            assertThat(entry.getCreatedByType()).isEqualTo("sys_user");
            assertThat(entry.getCreatedById()).isEqualTo(9002L);
        });
}
```

- [ ] **步骤 2（2–5 分钟）：运行集成测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=QuotaBillingServiceIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('QuotaBillingServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：锁定/结算/释放事务尚未实现而失败。

- [ ] **步骤 3（2–5 分钟）：实现条件更新和事件唯一键**

事件键固定为：

```java
private String operationEventKey(Long operationId, LedgerType type) {
    return "operation:" + operationId + ":" + type.code();
}

@Transactional
public QuotaAccountSnapshotDTO settle(Long operationId, Long rootTaskId) {
    AvAiUsageOperation operation = operationMapper.selectForUpdate(operationId);
    if ("settled".equals(operation.getStatus())) {
        return accountSnapshot(operation.getAccountId());
    }
    if (!"locked".equals(operation.getStatus())) {
        throw terminalConflict(operation);
    }
    AvQuotaAccount account = accountMapper.selectForUpdate(operation.getAccountId());
    int changed = operationMapper.transitionLockedToSettled(
        operationId, operation.getLockedQuota());
    if (changed != 1) {
        return accountSnapshot(operation.getAccountId());
    }
    account.setLockedBalance(account.getLockedBalance() - operation.getLockedQuota());
    account.incrementRevision();
    accountMapper.updateById(account);
    ledgerMapper.insert(settleEntry(operation, account, rootTaskId));
    return QuotaAccountSnapshotDTO.from(account);
}
```

`release` 使用 `locked -> released` 条件更新；`lock`、`settle`、`release` 的唯一事件键分别写一次。重复事件读取既有流水，不再次改余额。异步 `settle/release` 不读取任何请求 Resolver，只把 usage operation（计费操作）和根任务已经冻结的 `app_user + initiatedByUserId` 复制到流水。退款只允许一次全额；退款和补偿必须由 platform 入口通过 `SysTaskInitiatorResolver` 解析并显式传入 `sys_user`，将该 `sysUserId` 写入流水；补偿使用 `compensation:{relatedLedgerId}:{correctionKey}`。任何后台线程都不得用 `AppLoginHelper`、`LoginHelper` 或空主体替代冻结因果主体。

- [ ] **步骤 4（2–5 分钟）：运行账务集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=QuotaBillingServiceIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('QuotaBillingServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：赠送、正负调整、锁定、结算、释放、退款、正负补偿、重复事件和结算/释放竞态全部通过。

- [ ] **步骤 5（2–5 分钟）：提交不可变账单**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/impl/QuotaBillingServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaAccountMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/AiUsageOperationMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/quota/QuotaLedgerEntryMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 实现不可变账单与恰好一次结算"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 7：实现收费任务操作槽、免费根/执行任务与耐久入队

**最小任务卡：**

- **单一目标／不做：** 创建唯一根任务、`executionNo=1` 执行任务、操作槽与耐久入队；不调用 provider，不在创建阶段追加 attempt。
- **风险／触发：** 红色；命中额度、幂等、任务状态、事务和并发。
- **权威来源：** 主计划冻结 Service／DTO 签名、`create -> freeze -> enqueue` 契约和 `docs/ASYNC_TASKS.md`。
- **成功／反向验收：** root 恰一个且首执行号为 1；复用不重复冻结／入队，免费任务无计费操作，回滚后扫描器不可见。
- **所有权／数据范围：** 仅 task／quota core 与本任务测试；只写目标任务族、操作槽和额度操作。
- **依赖／人员／并发：** 依赖任务 4–6；开发 A 实施、开发 C 独立资金／并发 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑任务 RED/GREEN、`AiTaskConcurrencyIT` 与报告，不得判 PASS；reviewer 核对根／执行唯一性。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskStatus.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskRole.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskType.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskBillingMode.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ChargeableTaskDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/FreeTaskDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskRevisionSnapshotDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskCreationResultDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskResultReferenceDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionLeaseDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTask.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskGroupMember.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiOperationSlot.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskAttempt.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskGroupMemberMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiOperationSlotMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiOperationSlotMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionDispatcher.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskExecutionDispatcherImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskExecutionDispatcherTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的幂等、槽并发和耐久入队测试**

```java
@Test
void taskTypeCodesMatchThePublicContractExactly() {
    assertThat(Arrays.stream(AiTaskType.values()).map(AiTaskType::code))
        .containsExactly(
            "question_generate",
            "evidence_retrieve",
            "script_generate",
            "script_optimize",
            "knowledge_import");
    assertThat(AiTaskType.SCRIPT_GENERATE.supports("script_regenerate")).isTrue();
}

@Test
void sameIntentReturnsRootButDifferentHashConflicts() {
    TaskCreationResultDTO first = service.createChargeableTask(command(
        "question:81:1:1", "intent-1", sha256("payload-a")));
    TaskCreationResultDTO retry = service.createChargeableTask(command(
        "question:81:1:1", "intent-1", sha256("payload-a")));
    assertThat(retry.rootTaskId()).isEqualTo(first.rootTaskId());
    assertThat(retry.reused()).isTrue();

    assertThatThrownBy(() -> service.createChargeableTask(command(
        "question:81:1:1", "intent-1", sha256("payload-b"))))
        .isInstanceOf(AiVideoBusinessException.class)
        .extracting("code")
        .isEqualTo(46116);
}

@Test
void twoActorsAndTwoKeysCannotDoubleLockSameSlot() {
    List<ResultOrError> results = runConcurrently(
        () -> service.createChargeableTask(commandForActor(
            101L, "script:81:1:inputhash", "key-a")),
        () -> service.createChargeableTask(commandForActor(
            102L, "script:81:1:inputhash", "key-b")));

    assertThat(results).filteredOn(ResultOrError::success).hasSize(1);
    assertThat(results).filteredOn(result -> result.code() == 46123).hasSize(1);
    assertThat(taskMapper.countRootTasks()).isEqualTo(1);
    assertThat(ledgerMapper.countByType("lock")).isEqualTo(1);
}

@Test
void freeKnowledgeImportIsIdempotentAndNeverCreatesBillingFacts() {
    FreeTaskDTO command = knowledgeImportCommand(
        301L, "import-key-1", sha256("batch-301-revision-1"),
        new TaskInitiatorDTO("sys_user", 9001L));
    TaskCreationResultDTO first = service.createFreeTask(command);
    TaskCreationResultDTO retry = service.createFreeTask(command);

    assertThat(retry.rootTaskId()).isEqualTo(first.rootTaskId());
    assertThat(retry.reused()).isTrue();
    assertThat(first.usageOperationId()).isNull();
    assertThat(taskMapper.selectById(first.rootTaskId()).getBillingMode())
        .isEqualTo("free");
    assertThat(taskMapper.selectById(first.executionTaskId()).getUsageOperationId())
        .isNull();
    assertThat(taskMapper.selectById(first.rootTaskId()))
        .extracting("actorType", "actorId")
        .containsExactly("sys_user", 9001L);
    verifyNoInteractions(quotaBillingService, operationSlotMapper);
    assertThat(ledgerMapper.countAll()).isZero();
}

@Test
void taskCreationRejectsTheOppositeIdentityDomainInBothDirections() {
    assertThatThrownBy(() -> service.createChargeableTask(command(
        "question:81:1:1", "wrong-sys", sha256("payload-sys"),
        new TaskInitiatorDTO("sys_user", 9001L))))
        .isInstanceOf(AiVideoBusinessException.class);

    assertThatThrownBy(() -> service.createFreeTask(knowledgeImportCommand(
        301L, "wrong-app", sha256("batch-app"),
        new TaskInitiatorDTO("app_user", 101L))))
        .isInstanceOf(AiVideoBusinessException.class);
}

@Test
void createOnlyPersistsPendingUntilOuterOrchestratorEnqueues() {
    TaskCreationResultDTO created = service.createChargeableTask(command(
        "question:81:1:1", "intent-2", sha256("payload-c"),
        new TaskInitiatorDTO("app_user", 101L)));

    assertThat(taskMapper.selectById(created.rootTaskId()).getStatus())
        .isEqualTo("pending");
    assertThat(taskMapper.selectById(created.executionTaskId()).getStatus())
        .isEqualTo("pending");
}

@Test
void enqueueRequiresTransactionIsIdempotentAndRollbackKeepsPending() {
    AvAiTask root = persistedRoot();
    AvAiTask execution = persistedPendingExecution(root);

    assertThatThrownBy(() ->
        dispatcher.enqueue(root.getId(), execution.getId()))
        .isInstanceOf(IllegalTransactionStateException.class);

    transactionTemplate.executeWithoutResult(status -> {
        dispatcher.enqueue(root.getId(), execution.getId());
        dispatcher.enqueue(root.getId(), execution.getId());
    });
    assertThat(taskMapper.selectById(root.getId()).getStatus())
        .isEqualTo("queued");
    assertThat(taskMapper.selectById(execution.getId()).getStatus())
        .isEqualTo("queued");
    assertThat(taskMapper.countStatusTransitions(
        root.getId(), "pending", "queued")).isOne();
    assertThat(taskMapper.countStatusTransitions(
        execution.getId(), "pending", "queued")).isOne();

    TaskPair rolledBack = persistedPendingPair();
    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
        dispatcher.enqueue(
            rolledBack.rootTaskId(), rolledBack.executionTaskId());
        throw new ForcedRollbackException();
    })).isInstanceOf(ForcedRollbackException.class);
    assertThat(taskMapper.selectById(rolledBack.rootTaskId()).getStatus())
        .isEqualTo("pending");
    assertThat(taskMapper.selectById(rolledBack.executionTaskId()).getStatus())
        .isEqualTo("pending");
}

@Test
void rootTransitionFailureRollsBackExecutionTransition() {
    TaskPair pair = persistedPendingPair();
    doThrow(new ForcedRootTransitionFailure())
        .when(taskMapper)
        .markRootQueuedIfPending(pair.rootTaskId());

    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status ->
        dispatcher.enqueue(pair.rootTaskId(), pair.executionTaskId())))
        .isInstanceOf(ForcedRootTransitionFailure.class);

    assertThat(taskMapper.selectById(pair.rootTaskId()).getStatus())
        .isEqualTo("pending");
    assertThat(taskMapper.selectById(pair.executionTaskId()).getStatus())
        .isEqualTo("pending");
}
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AiTaskServiceTest', 'AiTaskExecutionDispatcherTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=AiTaskServiceTest,AiTaskExecutionDispatcherTest" test
$redExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=AiTaskConcurrencyIT verify
$redExitCode = $LASTEXITCODE
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('AiTaskConcurrencyIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：任务服务、免费入口与槽事务骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现固定加锁顺序与任务形状**

`createChargeableTask` 的事务顺序不得变化：

```java
@Transactional
public TaskCreationResultDTO createChargeableTask(ChargeableTaskDTO command) {
    TaskInitiatorDTO initiator = command.initiator();
    initiator.requireActorType("app_user");
    WorkspaceContextDTO workspace =
        workspaceAuthorizationService.resolveCurrentWorkspace();
    requireSameAppUser(initiator, workspace);
    workspaceAuthorizationService.requireResourceAction(
        command.resourceType(), command.resourceId(), ResourceAction.GENERATE);
    workspaceAuthorizationService.requireWorkspacePermission(
        "aivideo:quota:use");

    lockDraftAndCurrentBranchIfScriptResource(command);
    recheckRevisionSnapshotInsideBranchLock(command);

    operationSlotMapper.insertIfAbsent(
        workspace.tenantId(), command.slotKey(), idGenerator.nextId());
    AvAiOperationSlot slot = operationSlotMapper.selectForUpdate(
        workspace.tenantId(), command.slotKey());
    TaskCreationResultDTO occupied = resolveOccupiedSlot(slot, command);
    if (occupied != null) {
        return occupied;
    }

    QuotaLockRequestDTO quotaRequest =
        QuotaLockRequestDTO.from(workspace, command);
    if (!quotaRequest.initiator().equals(command.initiator())) {
        throw new IllegalStateException("额度锁主体未从任务命令显式冻结");
    }
    QuotaLockResultDTO quota = quotaBillingService.lock(quotaRequest);
    AvAiTask root = AvAiTask.root(command, workspace, quota.operationId());
    taskMapper.insert(root);
    AvAiTask execution = AvAiTask.firstExecution(root);
    taskMapper.insert(execution);
    taskGroupMemberMapper.insertOrigin(command.taskGroupKey(), root.getId());
    operationSlotMapper.occupy(slot.getId(), root.getId(), slot.getSlotRevision());
    return new TaskCreationResultDTO(
        root.getId(), execution.getId(), quota.operationId(), false);
}
```

`IQuotaBillingService.lock` 内部在槽已锁定之后再 `SELECT ... FOR UPDATE` 账户。活跃槽：相同键+相同摘要返回原根任务；相同键+不同摘要返回 `46116`；任何不同键返回 `46123` 和脱敏活跃任务摘要，不建立别名。

收费根任务 `usageOperationId` 非空，执行任务为空；自动重试只能创建执行任务并复用根任务操作。免费入口使用独立事务：

```java
@Transactional
public TaskCreationResultDTO createFreeTask(FreeTaskDTO command) {
    Long tenantId = TenantHelper.getTenantId();
    TaskInitiatorDTO actor = command.initiator();
    actor.requireActorType("sys_user");
    if (command.taskType() != AiTaskType.KNOWLEDGE_IMPORT) {
        throw new IllegalArgumentException("P0-C 免费任务只允许 knowledge_import");
    }
    AvAiTask existing = taskMapper.selectFreeRootByIdempotency(
        tenantId, actor.actorType(), actor.actorId(),
        command.taskType().code(), command.idempotencyKey());
    if (existing != null) {
        if (!existing.getRequestHash().equals(command.requestHash())) {
            throw new AiVideoBusinessException(
                AiVideoErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                "该免费任务幂等键已被不同请求占用", null);
        }
        return taskResult(existing, true);
    }

    AvAiTask root = AvAiTask.freeRoot(command, tenantId, actor);
    taskMapper.insert(root);
    AvAiTask execution = AvAiTask.firstExecution(root);
    taskMapper.insert(execution);
    taskGroupMemberMapper.insertOrigin(
        command.taskGroupKey(), root.getId());
    return new TaskCreationResultDTO(
        root.getId(), execution.getId(), null, false);
}
```

免费入口不调用 `IQuotaBillingService`、不创建 `av_ai_usage_operation`/额度流水、不占收费操作槽，只接受 `SysTaskInitiatorResolver` 解析的 `sys_user`。数据库唯一键冲突时按 `(tenantId, actorType, actorId, taskType, idempotencyKey)` 重读免费根任务并比较摘要；相同摘要复用，不同摘要返回 `46116`。收费入口只接受 `AppTaskInitiatorResolver` 的 `app_user` 并与当前工作区创作用户交叉校验；两种入口都不允许从另一身份域回退。

两个 `create*` 方法只返回状态为 `pending` 的新执行任务。P1/P2/P3 必须按以下模板在各自业务 orchestrator（流程编排服务）的外层事务中完成冻结与入队，不允许在 `create*` 内提前入队：

```java
@Transactional
public TaskCreationResultDTO createFreezeAndEnqueue(StageCommand command) {
    TaskCreationResultDTO task = aiTaskService.createChargeableTask(
        command.toTaskCommand());
    if (task.reused()) {
        return task;
    }
    immutableInputRepository.freeze(
        task.rootTaskId(), task.executionTaskId(), command.frozenInput());
    aiTaskExecutionDispatcher.enqueue(
        task.rootTaskId(), task.executionTaskId());
    return task;
}
```

`AiTaskExecutionDispatcherImpl.enqueue` 标注 `@Transactional(propagation = Propagation.MANDATORY)` 并显式断言真实事务活动，只执行父子关系校验、`AiTaskMapper.markExecutionQueuedIfPending(rootTaskId, executionTaskId)` 和 `AiTaskMapper.markRootQueuedIfPending(rootTaskId)`。两个条件更新都必须返回 `1` 才算首次入队；执行任务更新后根任务返回 `0` 或抛错时必须抛出异常，让执行任务更新随事务回滚。更新数为 `0` 时重读，只有同一根/执行对已一致处于 `queued`、`running` 或同一终态才视为幂等重复；父子不匹配、未知编号或父子状态分裂一律拒绝。它不得调用模型、SnailJob、额度服务或创建新任务；不得捕获冻结异常后调用 `markFailed`。数据库事务提交后 `queued` 才对扫描器可见，回滚后任务、额度锁、冻结输入和入队状态均不可见。

- [ ] **步骤 4（2–5 分钟）：运行任务单元与并发集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AiTaskServiceTest', 'AiTaskExecutionDispatcherTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false "-Dtest=AiTaskServiceTest,AiTaskExecutionDispatcherTest" test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=AiTaskConcurrencyIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('AiTaskConcurrencyIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：收费幂等、不同摘要、不同键、两协作者并发、根/执行形状、单笔锁定、`create*` 只产生 `pending`、无事务入队被拒绝、同一执行任务只入队一次、回滚保持 `pending`，以及免费知识导入无计费事实全部通过。

- [ ] **步骤 5（2–5 分钟）：提交任务创建底座**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskStatus.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskRole.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskType.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AiTaskBillingMode.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ChargeableTaskDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/FreeTaskDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskRevisionSnapshotDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskCreationResultDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskResultReferenceDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionLeaseDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTask.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskGroupMember.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiOperationSlot.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/domain/AvAiTaskAttempt.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskGroupMemberMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiOperationSlotMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiOperationSlotMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionDispatcher.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskExecutionDispatcherImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskExecutionDispatcherTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 实现统一收费与免费任务底座"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 8：实现任务终态、取消、详情、尝试和成本裁剪

**最小任务卡：**

- **单一目标／不做：** 实现租约条件终态、取消、详情和 provider attempt 生命周期；不在无真实 provider 调用路径创建 attempt。
- **风险／触发：** 红色；命中任务终态、外部边界、成本、重试和租约并发。
- **权威来源：** 主计划 `IAiTaskService`／`IAiTaskAttemptService` 精确签名、失败码和三次尝试上限。
- **成功／反向验收：** 只有紧邻真实调用的 `startAttempt` 才追加单调序号，最多 3 次；过期 worker、第四次、免费／无 provider 路径均失败关闭或 attempt=0。
- **所有权／数据范围：** 仅 task core 与本任务测试；仅修改同一 root／execution／attempt。
- **依赖／人员／并发：** 依赖任务 7；开发 A 实施、开发 C 独立外部边界／并发 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑 `AiTaskAttemptServiceIT` 等精确 RED/GREEN，不得判 PASS；reviewer 核对租约、成本和上限。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的终态与成本权限测试**

```java
@Test
void oldCallbackCannotReleaseNewRootSlot() {
    AiTaskExecutionLeaseDTO staleLease = finishedRootAndReturnOldLease();
    Long newRoot =
        occupySameReusableSlotAfterTerminal(staleLease.rootTaskId());
    assertThatThrownBy(() ->
        service.markFailed(staleLease, "late_callback", "迟到回调"))
        .isInstanceOf(AiVideoBusinessException.class);
    assertThat(slotMapper.selectByRootTaskId(newRoot).getActiveRootTaskId())
        .isEqualTo(newRoot);
}

@Test
void userCancelOnlyAcceptsNonTerminalRoot() {
    Long successRoot = successfulRoot();
    assertThatThrownBy(() -> service.requestCancel(successRoot, "用户取消"))
        .isInstanceOf(AiVideoBusinessException.class)
        .extracting("code")
        .isEqualTo(46119);
}
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AiTaskServiceTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AiTaskServiceTest test
$redExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：终态保护、条件释放或取消规则失败。

- [ ] **步骤 3（2–5 分钟）：实现最小终态和查询规则**

`markSuccess` 在同一事务中以完整 `AiTaskExecutionLeaseDTO` 条件校验执行任务仍是同一 `running + leaseOwner` 且根/执行均未终态，再写结果引用和父子终态；租约已经过期、被续租替换或被其他工作器重领时必须拒绝落库。`billingMode=chargeable` 才调用 `quotaBillingService.settle`，`billingMode=free` 不访问任何计费表。收费任务根状态转 `success` 后执行：

```sql
UPDATE av_ai_operation_slot
SET active_root_task_id = NULL,
    slot_revision = slot_revision + 1,
    released_at = CURRENT_TIMESTAMP
WHERE tenant_id = #{tenantId}
  AND slot_key = #{slotKey}
  AND active_root_task_id = #{rootTaskId}
```

收费任务失败和取消调用 `release`，也只用同一条件清槽；免费任务直接进入失败/取消终态，不调用 `release` 且没有槽可清。任务详情返回根任务、计费模式、活跃执行任务、完整尝试链、重试来源、结果引用和失败信息。

每次真实提供商调用必须先创建一条不可变 `av_ai_task_attempt`，终态补齐 `provider/model/input_tokens/output_tokens/provider_cost/currency/status/started_at/finished_at/input_hash/output_hash/failure_code`；自动重试追加新尝试，不能覆盖旧尝试。`AiUsageAdminController` 的成本接口按根任务返回全部尝试，结算或释放流水引用导致最终业务终态的准确 `attempt_id`。P0-C 只建表、Mapper、VO 和查询，不生成伪造调用记录。

用户任务范围：个人仅实际发起用户；组织普通成员仅本人发起；组织 owner/admin 可查当前组织。成本 DTO 只在独立方法中构造，普通任务详情不含 `providerCost`、令牌和币种。

- [ ] **步骤 4（2–5 分钟）：运行终态与并发测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AiTaskServiceTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AiTaskServiceTest test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=AiTaskConcurrencyIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('AiTaskConcurrencyIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：终态不可回退、取消、迟到回调、旧根不能释放新槽、成本裁剪和结果引用用例通过。

- [ ] **步骤 5（2–5 分钟）：提交任务生命周期**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 完成任务终态与可见范围"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 9：实现 SnailJob 耐久扫描并定义模型、检索端口

**最小任务卡：**

- **单一目标／不做：** 在 infra 实现耐久扫描、租约恢复、处理器注册和直接 provider／client 边界；P0-C 不真实外调，不在 core 定义 provider 端口。
- **风险／触发：** 红色；命中调度、恢复、可用性、外部信任边界和模块依赖。
- **权威来源：** 主计划扫描／attempt 规则、`docs/ASYNC_TASKS.md` 与 infra 技术边界。
- **成功／反向验收：** 恢复复用同一 executionTaskId／executionNo 和新租约，attempt=0；重复 handler 阻止启动，未知类型确定失败且批次继续。
- **所有权／数据范围：** 仅 infra task/provider/client、required 测试与必要 POM；core provider 路径必须为空。
- **依赖／人员／并发：** 依赖任务 7–8；开发 A 实施、开发 C 独立恢复／依赖 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 必跑 required 三测试和动态 staging 门禁但不得判 PASS；reviewer 独立复跑扫描恢复证据。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 允许的可选修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml`（仅缺少 ArchUnit 测试依赖时）
- 允许的可选修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml`（仅现有依赖无法编译 SnailJob 测试时）
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskAttemptService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionHandler.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/AiTaskNonRetryableException.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskAttemptServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptHandleDTO.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskAttemptMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/SnailJobAiTaskExecutionScanner.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/AiTaskExecutionHandlerRegistry.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskAttemptServiceIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelProvider.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallRequest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallResult.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ProviderUsageDTO.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchClient.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchRequest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchResult.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/ModuleDependencyTest.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的模块边界测试**

```java
// `ModuleDependencyTest` 是单元测试；下列扫描与 attempt 用例分别位于
// `@Tag("dev") class AiTaskExecutionScannerIT` 和
// `@Tag("dev") class AiTaskAttemptServiceIT`。
@Test
void coreHasPortsButNoProviderSdkOrControllers() {
    JavaClasses classes = new ClassFileImporter()
        .importPackages("org.dromara.aivideo");

    noClasses().that().resideInAPackage("org.dromara.aivideo..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "org.dromara.aivideo.user..",
            "org.dromara.aivideo.platform..",
            "com.openai..")
        .check(classes);

    classes().that().haveSimpleNameEndingWith("Controller")
        .should().resideOutsideOfPackage("org.dromara.aivideo.provider..")
        .check(classes);
}

@Test
void rootHasExactlyOneExecutionNumberOneAndRecoveryReusesIt() {
    TaskCreationResultDTO created = createFreezeEnqueueAndCommitWithResult();
    List<AvAiTask> before =
        taskMapper.selectExecutionsByRootId(created.rootTaskId());
    assertThat(before).singleElement().satisfies(execution -> {
        assertThat(execution.getId()).isEqualTo(created.executionTaskId());
        assertThat(execution.getExecutionNo()).isEqualTo(1);
    });

    scanner.jobExecute(jobArgs());
    expireLease(created.executionTaskId());
    scanner.jobExecute(jobArgs());

    List<AvAiTask> after =
        taskMapper.selectExecutionsByRootId(created.rootTaskId());
    assertThat(after).singleElement().satisfies(execution -> {
        assertThat(execution.getId()).isEqualTo(created.executionTaskId());
        assertThat(execution.getExecutionNo()).isEqualTo(1);
        assertThat(execution.getLeaseOwner()).isNotBlank();
    });
    assertThat(taskMapper.selectById(created.rootTaskId()).getStatus())
        .isEqualTo("running");
    assertThat(taskAttemptMapper.countByRootTaskId(created.rootTaskId()))
        .isZero();
}

@Test
void scannerClaimsQueuedAndExpiredRunningButNotActiveLease() {
    TaskPair queued = insertFrozenPair("queued", "queued", null);
    TaskPair expired = insertFrozenPair(
        "running", "running", now.minusSeconds(1));
    TaskPair active = insertFrozenPair(
        "running", "running", now.plusSeconds(60));

    scanner.jobExecute(jobArgs());

    assertThat(handledExecutionIds()).containsExactlyInAnyOrder(
        queued.executionTaskId(), expired.executionTaskId());
    assertThat(handledExecutionIds()).doesNotContain(active.executionTaskId());
    assertThat(taskMapper.selectById(queued.executionTaskId()).getLeaseOwner())
        .isNotBlank();
    assertThat(taskMapper.selectById(queued.rootTaskId()).getStatus())
        .isEqualTo("running");
    assertThat(taskMapper.selectById(expired.rootTaskId()).getStatus())
        .isEqualTo("running");
    assertThat(taskMapper.selectById(active.rootTaskId()).getStatus())
        .isEqualTo("running");
    assertThat(taskAttemptMapper.countAll()).isZero();
}

@Test
void durableQueuedRowSurvivesMissedWakeupAndIsEventuallyScanned() {
    long executionId = createFreezeEnqueueAndCommit();
    simulateProcessCrashBeforeOptionalWakeup();

    scanner.jobExecute(jobArgs());

    assertThat(handledExecutionIds()).containsExactly(executionId);
    assertThat(frozenInputRepository.existsByExecutionId(executionId)).isTrue();
}

@Test
void freezeFailureRollsBackTaskQuotaInputAndQueueBeforeScannerCanSeeIt() {
    assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
        TaskCreationResultDTO task =
            aiTaskService.createChargeableTask(command());
        frozenInputRepository.freeze(task.executionTaskId(), frozenInput());
        throw new ForcedFreezeFailure();
    })).isInstanceOf(ForcedFreezeFailure.class);

    scanner.jobExecute(jobArgs());

    assertThat(taskMapper.countAll()).isZero();
    assertThat(quotaLedgerMapper.countByType("lock")).isZero();
    assertThat(frozenInputRepository.countAll()).isZero();
    assertThat(handledExecutionIds()).isEmpty();
}

@Test
void oneHandlerFailureDoesNotBlockLaterLeasesAndCreatesNoProviderAttempt() {
    TaskPair first = insertFrozenPair("queued", "queued", null);
    TaskPair second = insertFrozenPair("queued", "queued", null);
    fakeHandler.failFor(first.executionTaskId());

    scanner.jobExecute(jobArgs());

    assertThat(fakeHandler.invokedExecutionIds())
        .containsExactly(first.executionTaskId(), second.executionTaskId());
    assertThat(taskMapper.selectById(first.executionTaskId())
        .getLastHandlerErrorCode()).isEqualTo("AI_TASK_HANDLER_EXCEPTION");
    assertThat(taskAttemptMapper.countAll()).isZero();
}

@Test
void deterministicInputFailureIsNonRetryableAndDoesNotBlockNextLease() {
    TaskPair invalid = insertPairWithMissingFrozenInput();
    TaskPair valid = insertFrozenPair("queued", "queued", null);
    fakeHandler.throwNonRetryableFor(
        invalid.executionTaskId(), "AI_TASK_FROZEN_INPUT_MISSING");

    scanner.jobExecute(jobArgs());

    assertThat(taskMapper.selectById(invalid.executionTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "AI_TASK_FROZEN_INPUT_MISSING");
    assertThat(taskMapper.selectById(invalid.rootTaskId()).getStatus())
        .isEqualTo("failed");
    assertThat(fakeHandler.invokedExecutionIds())
        .contains(valid.executionTaskId());
}

@Test
void successfulProviderAttemptThenStaleResultFailsWithoutRetryAndReleasesQuota() {
    TaskPair pair = insertFrozenChargeablePairWithRevisions(
        4L, 7L, sha256("frozen-input"));
    advanceCurrentBranchAndContext(
        pair.resourceId(), 5L, 8L, sha256("new-input"));
    fakeGateway.succeedWith(new ModelCallResult(
        "provider-output",
        usage("request-stale", 120L, 45L, "0.0060", "USD")));
    fakeHandler.handleWith(pair.executionTaskId(), lease -> {
        AiTaskAttemptHandleDTO attempt = attemptService.startAttempt(
            lease.rootTaskId(), lease.executionTaskId(),
            lease.leaseOwner(), "initial",
            "openai-compatible", "model-a",
            sha256("frozen-input"));
        ModelCallResult providerResult =
            fakeGateway.execute(modelRequest("frozen-input"));
        attemptService.completeAttempt(
            attempt.attemptId(),
            providerResult.usage(),
            sha256(providerResult.text()));
        persistBusinessResultInNewTransaction(lease, () -> {
            requireFrozenBranchAndContextStillCurrent(lease);
            fail("过期分支不得落业务结果");
        });
    });

    scanner.jobExecute(jobArgs());

    assertThat(taskAttemptMapper.selectByRootTaskId(pair.rootTaskId()))
        .singleElement()
        .satisfies(attempt -> {
            assertThat(attempt.getStatus()).isEqualTo("success");
            assertThat(attempt.getProviderCallSequence()).isEqualTo(1);
            assertThat(attempt.getProviderRequestId())
                .isEqualTo("request-stale");
            assertThat(attempt.getProviderCost())
                .isEqualByComparingTo("0.0060");
        });
    assertThat(taskMapper.selectById(pair.executionTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "STALE_BRANCH_RESULT");
    assertThat(taskMapper.selectById(pair.rootTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "STALE_BRANCH_RESULT");
    assertThat(businessResultRepository.countByRootTaskId(pair.rootTaskId()))
        .isZero();
    assertThat(operationMapper.selectById(pair.usageOperationId()).getStatus())
        .isEqualTo("released");
    assertThat(ledgerMapper.countByOperationAndType(
        pair.usageOperationId(), "release")).isOne();
    assertThat(ledgerMapper.countByOperationAndType(
        pair.usageOperationId(), "settle")).isZero();
    assertThat(fakeGateway.invocationCount()).isOne();

    scanner.jobExecute(jobArgs());

    assertThat(fakeGateway.invocationCount()).isOne();
    assertThat(taskAttemptMapper.countByRootTaskId(pair.rootTaskId())).isOne();
    assertThat(ledgerMapper.countByOperationAndType(
        pair.usageOperationId(), "release")).isOne();
}

@Test
void freeKnowledgeImportWithoutGatewayCallCreatesNoProviderAttempt() {
    TaskPair pair = insertFrozenKnowledgeImportPair();
    knowledgeImportHandler.completeWithoutProviderCall();

    scanner.jobExecute(jobArgs());

    assertThat(taskMapper.selectById(pair.rootTaskId()).getStatus())
        .isEqualTo("success");
    assertThat(taskAttemptMapper.countAll()).isZero();
}

@Test
void unknownTypeFailsRootAndExecutionWithFixedCode() {
    TaskPair pair = insertFrozenPairWithUnsupportedTaskType();

    scanner.jobExecute(jobArgs());

    assertThat(taskMapper.selectById(pair.rootTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "AI_TASK_HANDLER_NOT_FOUND");
    assertThat(taskMapper.selectById(pair.executionTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "AI_TASK_HANDLER_NOT_FOUND");
}

@Test
void retryableFailureWaitsForLeaseAndExhaustionEventuallyFailsBothTasks() {
    TaskPair pair = insertFrozenPair("queued", "queued", null, 2);
    fakeHandler.alwaysFailFor(pair.executionTaskId());

    scanner.jobExecute(jobArgs());
    scanner.jobExecute(jobArgs());
    assertThat(fakeHandler.invocationCount(pair.executionTaskId())).isOne();

    advancePastLease();
    scanner.jobExecute(jobArgs());
    advancePastLease();
    scanner.jobExecute(jobArgs());

    assertThat(taskMapper.selectById(pair.rootTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "AI_TASK_RETRY_EXHAUSTED");
    assertThat(taskMapper.selectById(pair.executionTaskId()))
        .extracting("status", "failureCode")
        .containsExactly("failed", "AI_TASK_RETRY_EXHAUSTED");
    assertThat(fakeHandler.invocationCount(pair.executionTaskId())).isEqualTo(2);
}

@Test
void duplicateHandlerForSameTaskTypeFailsRegistryStartup() {
    assertThatThrownBy(() -> new AiTaskExecutionHandlerRegistry(List.of(
        handlerFor(AiTaskType.SCRIPT_GENERATE),
        handlerFor(AiTaskType.SCRIPT_GENERATE))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("AI_TASK_HANDLER_DUPLICATE");
}

@Test
void realProviderAttemptsUseLeaseAndAllocateMonotonicSequence() {
    AiTaskExecutionLeaseDTO lease = runningLease("worker-a");

    AiTaskAttemptHandleDTO first = attemptService.startAttempt(
        lease.rootTaskId(), lease.executionTaskId(), lease.leaseOwner(),
        "initial", "openai-compatible", "model-a", sha256("input-a"));
    attemptService.failAttempt(
        first.attemptId(),
        new ProviderUsageDTO(null, null, null, null, null),
        "repair_required", "结构校验失败，进入修复调用");
    AiTaskAttemptHandleDTO second = attemptService.startAttempt(
        lease.rootTaskId(), lease.executionTaskId(), lease.leaseOwner(),
        "repair", "openai-compatible", "model-a", sha256("input-b"));

    assertThat(first.providerCallSequence()).isEqualTo(1);
    assertThat(second.providerCallSequence()).isEqualTo(2);
    assertThatThrownBy(() -> attemptService.startAttempt(
        lease.rootTaskId(), lease.executionTaskId(), "stale-worker",
        "repair", "openai-compatible", "model-a", sha256("input-c")))
        .isInstanceOf(AiVideoBusinessException.class);

    attemptService.completeAttempt(
        second.attemptId(),
        new ProviderUsageDTO("request-2", 120L, 80L,
            new BigDecimal("0.0125"), "USD"),
        sha256("output-b"));
    assertThat(taskAttemptMapper.selectById(second.attemptId()))
        .extracting(
            "status", "providerRequestId", "inputTokens",
            "outputTokens", "providerCost", "currency")
        .containsExactly(
            "success", "request-2", 120L, 80L,
            new BigDecimal("0.0125"), "USD");

    AiTaskExecutionLeaseDTO staleLease = runningLease("worker-old");
    AiTaskAttemptHandleDTO old = seedRunningAttemptWithObservedUsage(
        staleLease, 1, "request-old", new BigDecimal("0.0040"), "USD");
    AiTaskExecutionLeaseDTO reclaimed =
        expireAndReclaim(staleLease, "worker-b");
    AiTaskAttemptHandleDTO next = attemptService.startAttempt(
        reclaimed.rootTaskId(), reclaimed.executionTaskId(),
        reclaimed.leaseOwner(), "repair", "openai-compatible",
        "model-a", sha256("input-c"));

    assertThat(next.providerCallSequence()).isEqualTo(2);
    assertThat(taskAttemptMapper.selectById(old.attemptId()))
        .extracting(
            "status", "failureCode", "providerRequestId",
            "providerCost", "currency")
        .containsExactly(
            "failed", "worker_lease_lost", "request-old",
            new BigDecimal("0.0040"), "USD");
}

@Test
void fourthProviderCallIsRejectedByRootBudget() {
    AiTaskExecutionLeaseDTO lease = runningLease("worker-budget");
    for (int expectedSequence = 1; expectedSequence <= 3;
         expectedSequence++) {
        AiTaskAttemptHandleDTO attempt = attemptService.startAttempt(
            lease.rootTaskId(), lease.executionTaskId(), lease.leaseOwner(),
            "initial", "openai-compatible", "model-a",
            sha256("input-" + expectedSequence));
        assertThat(attempt.providerCallSequence())
            .isEqualTo(expectedSequence);
        attemptService.failAttempt(
            attempt.attemptId(),
            new ProviderUsageDTO(null, null, null, null, null),
            "provider_transient", "提供商暂时不可用");
    }

    assertThatThrownBy(() -> attemptService.startAttempt(
        lease.rootTaskId(), lease.executionTaskId(), lease.leaseOwner(),
        "initial", "openai-compatible", "model-a", sha256("input-4")))
        .isInstanceOf(AiTaskNonRetryableException.class)
        .extracting("failureCode")
        .isEqualTo("AI_TASK_PROVIDER_ATTEMPTS_EXHAUSTED");
}
```

测试辅助方法 `requireFrozenBranchAndContextStillCurrent` 必须在新事务内锁定并比较
冻结的 `branchRevision/generationContextRevision/generationInputHash`；任一不一致就
直接抛 `AiTaskNonRetryableException("STALE_BRANCH_RESULT", 脱敏消息)`。它不能返回
布尔值交给调用方忽略，也不能先写业务结果。`fakeGateway` 只返回本地固定结果，不发生
真实网络调用。

- [ ] **步骤 2（2–5 分钟）：运行边界测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('ModuleDependencyTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ModuleDependencyTest test
$redExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$redItSelectors = @('AiTaskExecutionScannerIT', 'AiTaskAttemptServiceIT')
foreach ($targetIt in $redItSelectors) {
  $itRunStartedAt = [DateTime]::UtcNow
  & $itEvidenceGatePath -RepoRoot $repoRoot -Selector @($targetIt) `
    -Mode 'Prepare' | Out-Null
  .\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am `
    -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
    -Dfailsafe.failIfNoSpecifiedTests=false `
    '-Pdev,local-integration-test' `
    "-Dit.test=$targetIt" verify
  $redExitCode = $LASTEXITCODE
  if ($redExitCode -eq 0) { throw "$targetIt 红灯命令意外通过" }
  & $itEvidenceGatePath -RepoRoot $repoRoot -Selector @($targetIt) `
    -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
}
```

预期：提供商端口、SnailJob 扫描器、租约领取实现或
`STALE_BRANCH_RESULT` 非重试释放骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现中立端口**

先运行 `./mvnw dependency:tree -Dincludes=com.tngtech.archunit:archunit-junit5` 检查现有测试依赖；仅当依赖树确实缺少 ArchUnit 时，才在 `ai-video-core/pom.xml` 增加仅测试使用的结构守卫，已经存在则不得改动 POM：

```xml
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit</artifactId>
    <version>1.4.1</version>
    <scope>test</scope>
</dependency>
```

```java
public interface ModelProvider {
    ModelCallResult execute(ModelCallRequest request);
}

public record ModelCallRequest(
    String modelCode,
    String systemPrompt,
    String userPrompt,
    String responseSchemaJson,
    Integer timeoutSeconds,
    String traceId) {
}

public record ModelCallResult(
    String text,
    ProviderUsageDTO usage) {
}

public interface WebSearchClient {
    WebSearchResult search(WebSearchRequest request);
}

public record WebSearchRequest(
    String query,
    Integer maxResults,
    Integer timeoutSeconds,
    String traceId) {
}

public record WebSearchResult(
    List<Hit> hits,
    ProviderUsageDTO usage) {
    public record Hit(String title, String url, String snippet) {
    }
}

public final class AiTaskNonRetryableException extends RuntimeException {
    private final String failureCode;

    public AiTaskNonRetryableException(
        String failureCode, String safeMessage) {
        super(safeMessage);
        if (failureCode == null
            || !failureCode.matches("[A-Z][A-Z0-9_]{2,63}")) {
            throw new IllegalArgumentException(
                "failureCode 必须是稳定的大写错误码");
        }
        this.failureCode = failureCode;
    }

    public String failureCode() {
        return failureCode;
    }
}
```

模型请求／结果和接口放在 `org.dromara.aivideo.provider`，检索请求／结果和接口放在 `org.dromara.aivideo.client`；中性的 `ProviderUsageDTO` 唯一放在 core 的 `task.dto`，infra 两类原始结果都引用它。紧凑构造器校验必填文本、正数超时、结果数量、非负 token/成本，以及成本非空时三位大写币种必填。搜索无 token 时可留空，但已知成本不得丢弃，失败响应能取得的脱敏用量和成本也传给 `IAiTaskAttemptService.failAttempt`。字段只含提供商无关的提示、结构 schema、字符串、数值和追踪编号。P0-C 不在 Spring 容器中注入或调用这两个端口；P1/P2 的适配器实现放入 `ai-video-infra`。

`IAiTaskService.claimExecutableTasks` 标注 `@Transactional`，先按 `(task_role, status, lease_expires_at, id)` 选择 execution（执行任务）角色中 `queued` 或租约过期 `running` 的候选，再逐条条件领取。首次领取用 `WHERE id = ? AND task_role = 'execution' AND status = 'queued'` 把执行任务改为 `running`、写入租约并递增 `leaseCount`，随后用 `WHERE id = ? AND task_role = 'root' AND status = 'queued'` 把根任务改为 `running`；父子任一更新数不是 `1` 就抛错并回滚。租约恢复只用 `WHERE id = ? AND task_role = 'execution' AND status = 'running' AND lease_expires_at < ? AND lease_count < max_lease_attempts` 刷新执行任务租约、递增 `leaseCount`，并校验根任务已经是 `running`，不得再次改写根状态。租约已过期且 `leaseCount >= maxLeaseAttempts` 时，同一事务把执行任务和根任务终结为 `failed/AI_TASK_RETRY_EXHAUSTED`，不再返回处理器。成功领取只返回 `AiTaskExecutionLeaseDTO`，绝不创建 `AvAiTaskAttempt`。活跃租约、终态和取消任务绝不返回。

冻结输入由 P1/P2/P3 各自的 `IAiTaskExecutionHandler.handle(lease)` 首步按 `taskType` 从本域不可变表加载和校验；P0-C 的 task mapper（任务映射器）不得反向依赖下游表。`create -> freeze -> enqueue` 同事务不变量是扫描器看不到缺失冻结输入的主保证；若历史脏数据或迁移错误导致输入缺失，处理器抛出固定的非重试错误并由 `recordHandlerFailure` 使父子任务失败收敛。

`IAiTaskAttemptService.startAttempt` 标注 `@Transactional`，对 execution 行 `SELECT ... FOR UPDATE`，校验完整根/执行关系、`running` 状态、当前 `leaseOwner` 和未过期租约。P0-C 首次创建每个根任务时只创建 `executionNo=1` 的一个执行子任务，P1/P2/P3 的结构修复与租约恢复不得追加执行子任务；因此该行锁也是当前根任务提供商调用的唯一串行化锁。服务先把同一 execution 上属于旧租约且仍为 `running` 的尝试条件更新为 `failed/worker_lease_lost`，只补终态和错误，不覆盖已经存在的 `providerRequestId`、token、成本或币种；同一当前租约仍有未终态尝试则拒绝并行调用。随后在锁内查询该 `root_task_id` 的准确尝试总数，并按当前 execution 的 `MAX(provider_call_sequence) + 1` 分配新序号；收费根任务已有 3 条尝试时抛 `AiTaskNonRetryableException("AI_TASK_PROVIDER_ATTEMPTS_EXHAUSTED", 脱敏消息)`，禁止继续产生平台成本，调用方不传序号。`callPurpose` 使用稳定值说明 `initial/repair/search` 等调用目的，但不参与序号分配。`completeAttempt/failAttempt` 锁定 attempt（尝试）及其 execution，使用尝试冻结的 `leaseOwner` 再次校验当前租约；相同终态和相同摘要幂等，不同终态或过期 worker 拒绝，任何已知用量/成本字段不得被空值覆盖。P1/P2/P3 的 `ProviderCallService` 必须在每次真实 gateway（网关）调用前调用一次 `startAttempt`，成功后用 `completeAttempt` 写输出摘要和完整 `ProviderUsageDTO`，异常后用 `failAttempt` 写能取得的脱敏用量/成本；未调用 gateway 不得创建尝试。模型/搜索超时必须早于 `leaseExpiresAt` 至少 10 秒，预计不足时先用当前 lease 条件调用 `renewLease`；过期 worker 的尝试或业务结果更新必须被拒绝。这里的三次是同一收费逻辑操作的内部成本上限，不产生第二次用户扣费；三次明细和已知真实提供商成本全部保留。

提供商调用成功与业务结果有效是两个独立事实。P1/P2/P3 处理器可以先提交
`completeAttempt`，随后在独立业务结果事务的第一步锁定当前分支/上下文并重核冻结的
三项修订摘要；若已过期，则该事务必须在任何结果 INSERT/UPDATE 和 `markSuccess`
之前回滚，并抛出固定的
`AiTaskNonRetryableException("STALE_BRANCH_RESULT", 脱敏消息)`。禁止把已经成功的
attempt 改回失败，也禁止把该错误包装成普通 `Exception`。因此扫描器能走
`retryable=false` 分支，保留真实提供商调用和成本，同时终结任务并释放锁定额度。

`SnailJobAiTaskExecutionScanner` 使用仓库现有 `@JobExecutor`（任务执行器注解）约定：

```java
@Component
@JobExecutor(name = "aiVideoTaskExecutionScanner")
public final class SnailJobAiTaskExecutionScanner {
    private final IAiTaskService aiTaskService;
    private final AiTaskExecutionHandlerRegistry handlerRegistry;
    private final Clock clock;

    public ExecuteResult jobExecute(JobArgs jobArgs) {
        Instant now = clock.instant();
        String workerId = UUID.randomUUID().toString();
        List<AiTaskExecutionLeaseDTO> leases =
            aiTaskService.claimExecutableTasks(
                now, workerId, now.plusSeconds(60), 50);
        for (AiTaskExecutionLeaseDTO lease : leases) {
            try {
                handlerRegistry.handle(lease);
            } catch (AiTaskHandlerNotFoundException exception) {
                aiTaskService.recordHandlerFailure(
                    lease, "AI_TASK_HANDLER_NOT_FOUND",
                    exception.getMessage(), false);
            } catch (AiTaskNonRetryableException exception) {
                aiTaskService.recordHandlerFailure(
                    lease, exception.failureCode(),
                    safeMessage(exception), false);
            } catch (Exception exception) {
                aiTaskService.recordHandlerFailure(
                    lease, "AI_TASK_HANDLER_EXCEPTION",
                    safeMessage(exception), true);
            }
        }
        return ExecuteResult.success(
            "claimed=" + leases.size());
    }
}
```

`AiTaskExecutionHandlerRegistry` 在构造时按 `supports()` 建不可变映射；同一 `AiTaskType` 出现两个处理器时以 `AI_TASK_HANDLER_DUPLICATE` 阻止应用启动，取不到处理器时抛 `AiTaskHandlerNotFoundException`。调用是同步且逐条 `try/catch`，第一条异常不能阻断同批后续租约，不使用可能“发布成功但无人监听”的事件广播。P1 实现 `knowledge_import` 处理器，P2 实现 `question_generate/evidence_retrieve`，P3 实现 `script_generate/script_optimize`，每个类型恰好一个。

未知类型、冻结输入缺失和 `STALE_BRANCH_RESULT` 都是非重试错误：
`recordHandlerFailure(..., retryable=false)` 条件校验当前 lease 后，同一事务将
execution/root 置为 `failed/<固定失败码>`、清租约，并对收费根任务调用
`quotaBillingService.release`；免费任务不访问计费表。释放操作与额度流水保持既有
幂等约束，绝不能产生 `settle`。普通处理器抛错记录
`AI_TASK_HANDLER_EXCEPTION`，保持 `running` 到租约过期再重领；进程直接崩溃走相同
租约恢复路径。达到 `maxLeaseAttempts` 后以 `AI_TASK_RETRY_EXHAUSTED` 终结父子任务，
禁止无限 `running`。处理器正常返回前必须已通过当前 lease 条件调用
`markSuccess/markFailed` 进入终态；返回后仍为 `running` 记作
`AI_TASK_HANDLER_INCOMPLETE` 并按可重试错误处理。周期扫描是最终恢复来源，不依赖事务
提交后回调；本计划不增加测试专用 Controller（控制器）或生产端点。

- [ ] **步骤 4（2–5 分钟）：运行边界测试和编译**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('ModuleDependencyTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false -Dtest=ModuleDependencyTest test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=AiTaskExecutionScannerIT,AiTaskAttemptServiceIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('AiTaskExecutionScannerIT', 'AiTaskAttemptServiceIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：边界测试通过，两个模块编译成功；已提交的 `queued`、租约过期的 `running` 可恢复，活跃租约不被重复领取，没有真实网络调用；成功提供商 attempt 后发现旧分支时，成功 attempt/成本保留，父子任务立即以 `STALE_BRANCH_RESULT` 失败，只产生一次额度释放且二次扫描不重试提供商。

- [ ] **步骤 5（2–5 分钟）：提交提供商端口与耐久扫描器**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$required = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskAttemptService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionHandler.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/AiTaskNonRetryableException.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/impl/AiTaskAttemptServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptHandleDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/mapper/AiTaskAttemptMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/task/AiTaskAttemptMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/SnailJobAiTaskExecutionScanner.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/provider/AiTaskExecutionHandlerRegistry.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskAttemptServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallRequest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelCallResult.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ProviderUsageDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchClient.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchRequest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchResult.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/ModuleDependencyTest.java'
)
$allowedOptional = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/pom.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/pom.xml'
)
$missingRequired = @($required | Where-Object {
  -not (Test-Path -LiteralPath $_ -PathType Leaf)
})
if ($missingRequired) { $missingRequired; throw '任务 9 必需文件缺失' }
$changed = @(
  git diff --name-only
  git ls-files --others --exclude-standard
) | Sort-Object -Unique
$outOfScope = @($changed | Where-Object {
  $_ -notin $required -and $_ -notin $allowedOptional
})
if ($outOfScope) { $outOfScope; throw '任务 9 出现清单外改动' }
$expected = @($required + @($allowedOptional | Where-Object {
  $_ -in $changed
}))
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 固定模型检索端口与耐久任务扫描"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 10：暴露用户端方向、草稿、任务和额度接口

**最小任务卡：**

- **单一目标／不做：** 暴露用户端方向、草稿、任务、额度与费率接口；不接受可信主体字段，不回退 sys 身份。
- **风险／触发：** 红色；命中身份、授权、资产、跨账号与 HTTP 公共契约。
- **权威来源：** API 契约、P0-B user resolver、P0-C 稳定 Service／DTO 和端侧 BO／VO 规则。
- **成功／反向验收：** 具体 `AppTaskInitiatorResolver` 只构造 app_user DTO；无凭据、sys token、跨账号、过期修订均拒绝且零副作用。
- **所有权／数据范围：** 仅 ai-video-user controller／security／domain.bo／domain.vo 与测试；不修改 core DTO。
- **依赖／人员／并发：** 依赖任务 3–8；开发 A 实施、开发 C 独立身份／接口 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑 user API／resolver／starter IT，不得判 PASS；reviewer 核对每条权限和反向身份域。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolver.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/direction/controller/DirectionController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/controller/ScriptDraftController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo/CreateScriptDraftBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/vo/ScriptDraftOverviewVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/controller/QuotaController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/common/AiVideoExceptionHandler.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/P0cUserApiIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolverTest.java`
- 创建：`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/assembly/P0cUserBoundaryIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的用户接口契约测试**

```java
@Test
void createDraftRejectsOwnershipFieldsAndReturnsSevenGuards() throws Exception {
    mockMvc.perform(post("/api/studio/script-drafts")
            .header("Authorization", appToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "idempotencyKey": "new-draft-001",
                  "ownerId": "7"
                }
                """))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/studio/script-drafts")
            .header("Authorization", appToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"idempotencyKey": "new-draft-001"}
                """))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.stepGuards.length()").value(7))
        .andExpect(jsonPath("$.data.draftRevision").value("0"))
        .andExpect(jsonPath("$.data.branchRevision").value("1"));
}

@Test
void organizationWorkspaceReturnsInitiatingUsersPersonalQuota() throws Exception {
    mockMvc.perform(get("/api/quota/account")
            .header("Authorization", organizationToken("9")))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.billingSubjectType").value("app_user"))
        .andExpect(jsonPath("$.data.billingSubjectId").value("101"));

    verify(quotaBillingService).resolveAccount(argThat(context ->
        context.billingSubjectType() == BillingSubjectType.APP_USER
            && context.billingSubjectId().equals(101L)));
    verifyNoMoreInteractions(quotaBillingService);
}

@Test
void userStarterHasOnlyAppInitiatorResolverAndRejectsSysToken() {
    assertThat(applicationContext.getBeansOfType(AppTaskInitiatorResolver.class))
        .hasSize(1);
    assertThat(applicationContext.containsBean("sysTaskInitiatorResolver"))
        .isFalse();
    withAppLogin(101L, () ->
        assertThat(appTaskInitiatorResolver.resolveRequired())
            .isEqualTo(new TaskInitiatorDTO("app_user", 101L)));
    assertThatThrownBy(() ->
        withOnlySysLogin(9001L, appTaskInitiatorResolver::resolveRequired))
        .isInstanceOf(NotLoginException.class);
}

@Tag("dev")
class AppTaskInitiatorResolverTest {
    private final AppAuthorizationActorResolver actorAdapter =
        mock(AppAuthorizationActorResolver.class);
    private final AppTaskInitiatorResolver resolver =
        new AppTaskInitiatorResolver(actorAdapter);

    @Test
    void resolvesOnlyAppActorAndNeverTouchesSysLoginDomain() {
        when(actorAdapter.requireActor())
            .thenReturn(AppActorContext.appUser(1001L));

        try (MockedStatic<LoginHelper> sysLogin =
                 mockStatic(LoginHelper.class)) {
            assertThat(resolver.resolveRequired())
                .isEqualTo(new TaskInitiatorDTO("app_user", 1001L));
            sysLogin.verifyNoInteractions();
        }
        verify(actorAdapter).requireActor();
    }
}
```

测试夹具让 `organizationToken("9")` 解析为组织工作区，但额度服务只读取实际发起用户
`101` 的既有 `app_user` 个人账户，不创建或查询组织额度账户。`AppTaskInitiatorResolver` 只依赖 P0-B
`AppAuthorizationActorResolver`，不得直接导入 `AppLoginHelper`、默认
`LoginHelper` 或查询 `sys_user`；用户 starter（启动应用）只装配具体
`AppTaskInitiatorResolver`，并断言不存在 `sysTaskInitiatorResolver` Bean。`P0cUserBoundaryIT` 枚举 Spring MVC 映射，断言用户启动
模块不存在 `/api/admin/**` 路由，也不存在包名以
`org.dromara.aivideo.platform` 开头的控制器 Bean。

- [ ] **步骤 2（2–5 分钟）：运行接口测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AppTaskInitiatorResolverTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=AppTaskInitiatorResolverTest test
$resolverRedExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=P0cUserApiIT verify
$redExitCode = $LASTEXITCODE
if ($resolverRedExitCode -eq 0) {
  throw 'AppTaskInitiatorResolverTest 红灯命令意外通过'
}
if ($redExitCode -eq 0) { throw 'P0cUserApiIT 红灯命令意外通过' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('P0cUserApiIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
```

预期：路由与异常数据契约骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现薄控制器和结构化异常**

`AppTaskInitiatorResolver` 的完整身份逻辑固定为：

```java
@Component
@RequiredArgsConstructor
public final class AppTaskInitiatorResolver {
    private final AppAuthorizationActorResolver actorAdapter;

    public TaskInitiatorDTO resolveRequired() {
        AppActorContext actor = actorAdapter.requireActor();
        if (actor.actorType() != AppActorType.APP_USER) {
            throw new IllegalStateException("用户端发起主体类型错误");
        }
        return new TaskInitiatorDTO("app_user", actor.actorId());
    }
}
```

用户接口与 `app` 权限固定为：

```text
GET  /api/studio/direction-options              aivideo:studio:query
POST /api/studio/script-drafts                  aivideo:studio:create
GET  /api/studio/script-drafts/{id}             aivideo:studio:query
GET  /api/tasks                                 aivideo:task:query
GET  /api/tasks/{id}                            aivideo:task:query
POST /api/tasks/{id}/cancel                     aivideo:task:cancel
GET  /api/quota/account                         aivideo:quota:query
GET  /api/quota/tariffs                         aivideo:quota:query
GET  /api/quota/ledger                          aivideo:quota:query
```

每个注解显式 `type = "app"`。`CreateScriptDraftBo` 只定义 `idempotencyKey`；Jackson 会把 `@JsonAnySetter` 抛出的异常包装成请求体读取错误，因此所有者、租户和计费主体字段收到 HTTP `400`，而不是被静默忽略：

```java
@Data
public class CreateScriptDraftBo {
    @NotBlank(message = "创建幂等键不能为空")
    private String idempotencyKey;

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("不支持的请求字段：" + fieldName);
    }
}

@ExceptionHandler(AiVideoBusinessException.class)
public R<Object> handleAiVideoBusinessException(
    AiVideoBusinessException exception) {
    R<Object> response = R.fail(exception.getCode(), exception.getMessage());
    response.setData(exception.getData());
    return response;
}
```

普通异常仍交给框架处理。任务和流水分页使用 `PageQuery`/`PageResult`。

- [ ] **步骤 4（2–5 分钟）：运行用户接口与装配测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('AppTaskInitiatorResolverTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=AppTaskInitiatorResolverTest test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '用户发起主体隔离单元测试失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-user,ai-video-user-api -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=P0cUserApiIT,P0cUserBoundaryIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('P0cUserApiIT', 'P0cUserBoundaryIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：字符串编号、分页、权限、对象范围、结构化错误和用户启动模块不暴露 `/api/admin/**` 全部通过。

- [ ] **步骤 5（2–5 分钟）：提交用户接口**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolver.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/direction/controller/DirectionController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/controller/ScriptDraftController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo/CreateScriptDraftBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/vo/ScriptDraftOverviewVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/controller/AiTaskController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/quota/controller/QuotaController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/common/AiVideoExceptionHandler.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/P0cUserApiIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolverTest.java'
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/assembly/P0cUserBoundaryIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 提供草稿任务与额度用户接口"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 11：暴露运营端方向、额度、价格、用量和任务接口

**最小任务卡：**

- **单一目标／不做：** 暴露运营端方向、额度、费率、用量和任务接口；不回退 app 身份，不把业务编排放 Controller。
- **风险／触发：** 红色；命中高权限运营、资产、审计和跨用户操作。
- **权威来源：** API 契约、P0-B platform resolver、RuoYi 权限／日志／防重规则。
- **成功／反向验收：** 具体 `SysTaskInitiatorResolver` 只构造 sys_user DTO；app token、缺权限、越权目标均拒绝且审计／数据零副作用。
- **所有权／数据范围：** 仅 ai-video-platform controller／security／domain.bo／domain.vo 与测试；影响显式目标组织／账户／任务。
- **依赖／人员／并发：** 依赖任务 3、5、6、8；开发 A 实施、开发 C 独立高权限／资金 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑 platform API／resolver／starter IT，不得判 PASS；reviewer 核对权限、审计和反向身份域。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolver.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/controller/DirectionCatalogAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/QuotaAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/TariffAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiTaskAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiUsageAdminController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/common/AiVideoExceptionHandler.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/P0cPlatformApiIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolverTest.java`
- 创建：`ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/assembly/P0cPlatformBoundaryIT.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的管理权限和成本裁剪测试**

```java
@Test
void ordinaryUsagePermissionNeverReturnsProviderCost() throws Exception {
    mockMvc.perform(get("/api/admin/ai-usage/operations")
            .header("Authorization", sysTokenWith("aivideo:usage:query")))
        .andExpect(jsonPath("$.code").value(200))
        .andExpect(jsonPath("$.data.rows[0].provider").doesNotExist())
        .andExpect(jsonPath("$.data.rows[0].providerCost").doesNotExist());
}

@Test
void costEndpointRequiresDedicatedPermission() throws Exception {
    mockMvc.perform(get("/api/admin/ai-usage/operations/91/costs")
            .header("Authorization", sysTokenWith("aivideo:usage:query")))
        .andExpect(jsonPath("$.code").value(not(200)));
}

@Test
void platformStarterUsesOnlySysInitiatorForFreeTaskAndQuotaMutation() {
    assertThat(applicationContext.getBeansOfType(SysTaskInitiatorResolver.class))
        .hasSize(1);
    assertThat(applicationContext.containsBean("appTaskInitiatorResolver"))
        .isFalse();
    withSysLogin(9001L, () -> {
        TaskInitiatorDTO initiator =
            sysTaskInitiatorResolver.resolveRequired();
        assertThat(initiator)
            .isEqualTo(new TaskInitiatorDTO("sys_user", 9001L));
        aiTaskService.createFreeTask(knowledgeImportCommand(initiator));
        quotaBillingService.adjust(adjustRequest(), initiator);
    });
    assertThat(taskMapper.latestRoot())
        .extracting("actorType", "actorId")
        .containsExactly("sys_user", 9001L);
    assertThat(ledgerMapper.latest())
        .extracting("createdByType", "createdById")
        .containsExactly("sys_user", 9001L);
    assertThatThrownBy(() ->
        withOnlyAppLogin(101L, sysTaskInitiatorResolver::resolveRequired))
        .isInstanceOf(NotLoginException.class);
}

@Tag("dev")
class SysTaskInitiatorResolverTest {
    private final SysAuthorizationActorResolver actorAdapter =
        mock(SysAuthorizationActorResolver.class);
    private final AppLoginHelper appLoginHelper = mock(AppLoginHelper.class);
    private final SysTaskInitiatorResolver resolver =
        new SysTaskInitiatorResolver(actorAdapter);

    @Test
    void resolvesOnlySysActorAndNeverTouchesAppLoginDomain() {
        when(actorAdapter.requireActor())
            .thenReturn(AppActorContext.sysUser(1001L));

        assertThat(resolver.resolveRequired())
            .isEqualTo(new TaskInitiatorDTO("sys_user", 1001L));

        verify(actorAdapter).requireActor();
        verifyNoInteractions(appLoginHelper);
    }
}
```

- [ ] **步骤 2（2–5 分钟）：运行平台接口测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('SysTaskInitiatorResolverTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=SysTaskInitiatorResolverTest test
$resolverRedExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Red'
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$redItSelectors = @('P0cPlatformApiIT', 'P0cPlatformBoundaryIT')
foreach ($targetIt in $redItSelectors) {
  $itRunStartedAt = [DateTime]::UtcNow
  & $itEvidenceGatePath -RepoRoot $repoRoot -Selector @($targetIt) `
    -Mode 'Prepare' | Out-Null
  .\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform,ruoyi-admin -am `
    -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
    -Dfailsafe.failIfNoSpecifiedTests=false `
    '-Pdev,local-integration-test' `
    "-Dit.test=$targetIt" verify
  $redExitCode = $LASTEXITCODE
  if ($redExitCode -eq 0) { throw "$targetIt 红灯命令意外通过" }
  & $itEvidenceGatePath -RepoRoot $repoRoot -Selector @($targetIt) `
    -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Red'
}
if ($resolverRedExitCode -eq 0) {
  throw 'SysTaskInitiatorResolverTest 红灯命令意外通过'
}
```

预期：接口与字段裁剪骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现管理端接口和精确权限**

`SysTaskInitiatorResolver` 的完整身份逻辑固定为：

```java
@Component
@RequiredArgsConstructor
public final class SysTaskInitiatorResolver {
    private final SysAuthorizationActorResolver actorAdapter;

    public TaskInitiatorDTO resolveRequired() {
        AppActorContext actor = actorAdapter.requireActor();
        if (actor.actorType() != AppActorType.SYS_USER) {
            throw new IllegalStateException("运营端发起主体类型错误");
        }
        return new TaskInitiatorDTO("sys_user", actor.actorId());
    }
}
```

控制器路径与权限固定为：

```text
DirectionCatalogAdminController:
  GET  /api/admin/studio/direction-catalogs               aivideo:direction:query
  POST /api/admin/studio/direction-catalogs               aivideo:direction:edit
  PUT  /api/admin/studio/direction-catalogs/{id}          aivideo:direction:edit
  POST /api/admin/studio/direction-catalogs/{id}/publish  aivideo:direction:publish
QuotaAdminController:
  GET  /api/admin/quotas/accounts                         aivideo:quota:admin-query
  POST /api/admin/quotas/accounts/{accountId}/adjustments aivideo:quota:adjust
  GET  /api/admin/quotas/ledger                           aivideo:quota:ledger-query
TariffAdminController:
  GET  /api/admin/quotas/tariffs                          aivideo:tariff:query
  POST /api/admin/quotas/tariffs                          aivideo:tariff:publish
  POST /api/admin/quotas/tariffs/{id}/publish             aivideo:tariff:publish
AiTaskAdminController:
  GET  /api/admin/tasks                                   aivideo:task:admin-query
  GET  /api/admin/tasks/{id}                              aivideo:task:admin-query
  POST /api/admin/tasks/{id}/cancel                       aivideo:task:admin-cancel
AiUsageAdminController:
  GET  /api/admin/ai-usage/operations                     aivideo:usage:query
  GET  /api/admin/ai-usage/operations/{id}/costs          aivideo:usage-cost:query
```

写接口使用 `@Log`、中文原因、幂等键和预期修订号。
`SysTaskInitiatorResolver` 只依赖 P0-B `SysAuthorizationActorResolver`，不得再次直接导入
默认 `LoginHelper`，也不导入 `AppLoginHelper`；平台 starter 只装配这一个
具体 `SysTaskInitiatorResolver`，并断言不存在 `appTaskInitiatorResolver` Bean。它为免费知识导入以及额度调整、退款、补偿提供
`sys_user + sysUserId`，app token 不能触发回退。成本字段只由 `AiUsageCostVo` 返回，
普通 VO 根本不定义这些属性。

`P0cPlatformBoundaryIT` 读取 `RequestMappingHandlerMapping` 的 pattern values，断言不存在 `/api/auth/**`、`/api/studio/**`、`/api/tasks/**`；同时枚举 `@RestController` Bean，断言没有类来自 `org.dromara.aivideo.user` 包。

- [ ] **步骤 4（2–5 分钟）：运行平台接口和装配测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelector = @('SysTaskInitiatorResolverTest')
$unitEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelector -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=SysTaskInitiatorResolverTest test
$greenExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelector `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '运营发起主体隔离单元测试失败' }
$itRunStartedAt = [DateTime]::UtcNow
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-platform,ruoyi-admin -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  -Dit.test=P0cPlatformApiIT,P0cPlatformBoundaryIT verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
& $itEvidenceGatePath -RepoRoot $repoRoot `
  -Selector @('P0cPlatformApiIT', 'P0cPlatformBoundaryIT') `
  -StartedAtUtc $itRunStartedAt -ExpectedOutcome 'Green'
```

预期：方向、账户、调整、流水、价格、任务、成本权限和平台启动模块隔离用例通过。

- [ ] **步骤 5（2–5 分钟）：提交运营接口**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolver.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/direction/controller/DirectionCatalogAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/QuotaAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/quota/controller/TariffAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiTaskAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/controller/AiUsageAdminController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/common/AiVideoExceptionHandler.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/P0cPlatformApiIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolverTest.java'
  'ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/assembly/P0cPlatformBoundaryIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 提供任务额度与方向管理接口"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 12：建立用户端 RuoYi（若依框架）适配、React Query（服务端状态查询库）根容器和任务轮询

**最小任务卡：**

- **单一目标／不做：** 只新增 tasks／quota／studio 领域 API、query 与轮询并接既有适配；不修改 core/auth/workspace 共享契约，不造第二套 unwrap/request。
- **风险／触发：** 红色；命中共享前端请求契约、鉴权和任务恢复。
- **权威来源：** 前端指南、既有 `createRuoYiAdapter`／`ApiError`／`R<T>|null` 和 API 契约。
- **成功／反向验收：** 只读公共文件零 diff；401／403／461xx 保真，轮询停止条件准确，重复请求／卸载后更新被阻止。
- **所有权／数据范围：** 仅 tasks／quota／studio 新文件与测试；shared 变更只能由独立 owner 另提交。
- **依赖／人员／并发：** 依赖任务 10；开发 A 实施、开发 C 独立前端契约 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑目标 Vitest／类型检查但不得判 PASS；reviewer 核对只读 diff 和错误适配。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 只读复用：`ai-video-ui/ai-video-webapp/biome.json`
- 只读复用：`ai-video-ui/ai-video-webapp/src/app.tsx`
- 只读复用：`ai-video-ui/ai-video-webapp/src/requestErrorConfig.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/queryClient.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/errors.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/core/ruoyiAdapter.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/auth/api.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/types.ts`
- 只读复用：`ai-video-ui/ai-video-webapp/src/services/ai-video/workspace/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/quota/api.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.test.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的响应、分页和轮询测试**

```typescript
it('listActiveTariffs 只调用既有适配器并保留版本与金额字符串', async () => {
  adapter.request.mockResolvedValue([
    { taskType: 'question_generate', tariffVersion: '3', amount: '10' },
  ]);
  const api = createQuotaApi(adapter);

  await expect(api.listActiveTariffs()).resolves.toEqual([
    { taskType: 'question_generate', tariffVersion: '3', amount: '10' },
  ]);
  expect(adapter.request).toHaveBeenCalledWith('/api/quota/tariffs');
});

it('前十秒每秒轮询，之后每两秒，终态停止', () => {
  expect(taskPollInterval(runningTask, 9_999, 0, false)).toBe(1_000);
  expect(taskPollInterval(runningTask, 10_001, 0, false)).toBe(2_000);
  expect(taskPollInterval(successTask, 2_000, 0, false)).toBe(false);
  expect(taskPollInterval(runningTask, 2_000, 3, false)).toBe(false);
});
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task12-red.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- `
  src/services/ai-video/quota/api.test.ts `
  src/services/ai-video/tasks/useTaskPolling.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$redExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：适配器、分页与轮询函数骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：基于既有适配器实现三域 API／query**

```typescript
import type { RuoYiAdapter } from '../core/ruoyiAdapter';

export function createQuotaApi(adapter: RuoYiAdapter) {
  return {
    listActiveTariffs: () =>
      adapter.request<QuotaTariff[]>('/api/quota/tariffs'),
  };
}

export interface QuotaTariff {
  taskType: string;
  tariffVersion: string;
  amount: string;
}
```

tasks／quota／studio API 都只接收已配置的 `RuoYiAdapter`，由既有 `createRuoYiAdapter`、`ApiError` 与 `R<T>|null` 契约统一处理响应；严禁创建第二套响应解包函数、请求包装、错误类或修改 core／auth／workspace／全局请求配置。query key 各域集中定义；如确需共享文件变更，必须由独立 owner 单独任务、单独提交并先更新本计划，Task 12 不得顺手修改。

任务轮询在页面隐藏时改为 10 秒，重新聚焦立即刷新；连续三次网络失败停止并展示中文离线提示；终态后按 `resultRefType/resultRefId` 刷新业务资源，不把任务载荷当作业务快照。

- [ ] **步骤 4（2–5 分钟）：运行测试、Lint 和构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task12-green.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- `
  src/services/ai-video/quota/api.test.ts `
  src/services/ai-video/tasks/useTaskPolling.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$greenExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：适配、错误码、轮询、类型、Lint 和构建全部通过。

- [ ] **步骤 5（2–5 分钟）：提交用户端公共基础**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/queryKeys.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/quota/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/quota/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/quota/queryKeys.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/quota/api.test.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/tasks/useTaskPolling.test.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 建立用户端业务请求与任务轮询"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 13：接入草稿启动、真实额度摘要和任务中心基础页

**最小任务卡：**

- **单一目标／不做：** 接入工作台草稿启动、额度摘要和任务中心可观察状态；不实现 P1～P3 生成流程，不重绑已有草稿。
- **风险／触发：** 红色；命中用户数据、额度展示、任务取消和共享 studio 文件。
- **权威来源：** 用户端 PRD、API 契约、前端状态矩阵和六共享文件 owner 规则。
- **成功／反向验收：** loading／empty／no-result／network+retry／403／pagination 及防重、确认、冲突均有测试；失败不重放写请求。
- **所有权／数据范围：** 仅本任务页面／测试和 F1 前 P0-C 独占 studio 文件；不改 P2 文件。
- **依赖／人员／并发：** 依赖任务 12；开发 A 实施、开发 C 独立 UX／状态 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑逐项 Vitest／构建但不得判 PASS；reviewer 对照状态方法名逐项验收。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.test.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuotaSummary.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

- [ ] **步骤 1（2–5 分钟）：编写失败的草稿启动和任务中心状态测试**

```tsx
it('无 draftId 时只创建一次并把编号写回地址', async () => {
  renderHook(() => useStudioBootstrap(), { wrapper: strictModeWrapper() });
  await waitFor(() => expect(createDraftMock).toHaveBeenCalledTimes(1));
  expect(history.location.search).toBe('?draftId=190000000000000001');
});

it('任务中心覆盖空、失败、403 和分页', async () => {
  listTasksMock.mockResolvedValueOnce({ items: [], total: 0 });
  const { rerender } = render(<TaskCenterView />);
  expect(await screen.findByText('暂无任务')).toBeInTheDocument();

  listTasksMock.mockRejectedValueOnce(apiError(403, '权限不足'));
  rerender(<TaskCenterView />);
  expect(await screen.findByText('无权查看任务')).toBeInTheDocument();
});

it('个人额度不足不切换其他用户或主体账户', async () => {
  render(<QuotaSummary account={personalZeroAccount} />);
  expect(screen.getByText('个人积分不足')).toBeInTheDocument();
  expect(screen.queryByText(/改用其他账户/)).not.toBeInTheDocument();
});
```

- [ ] **步骤 2（2–5 分钟）：运行页面测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task13-red.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- `
  src/pages/digital-human-studio/hooks/useStudioBootstrap.test.tsx `
  src/pages/digital-human-studio/components/TaskCenterView.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$redExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：Hook 与页面骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小页面闭环**

`useStudioBootstrap` 用 `useRef(crypto.randomUUID())` 保存一次“新建创作”意图键；React StrictMode 重渲染和传输重试复用该键，用户再次点击“新建作品”才生成新键。URL 有 `draftId` 时只恢复，不创建同名草稿掩盖 404/403。

`StudioRoute` 增加 `tasks`，`StudioSider` 增加“任务”入口并用 `QuotaSummary` 替换静态积分。`TaskCenterView` 显示类型、中文状态、进度、工作区、实际发起人、费用、来源和时间；免费任务费用明确显示“免费”，不伪造零额度流水。支持筛选、分页、详情、可取消状态和立即重试网络读取。

错误状态使用 Ant Design `Result` 的 `403/error`、`Alert` 和 `Skeleton`；界面所有文字中文，编号保持字符串。

- [ ] **步骤 4（2–5 分钟）：运行页面测试、全量前端测试和构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task13-green.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- `
  src/pages/digital-human-studio/hooks/useStudioBootstrap.test.tsx `
  src/pages/digital-human-studio/components/TaskCenterView.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$greenExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：草稿幂等、恢复、工作区冻结摘要、额度无回退、任务状态和分页测试通过；构建退出码为 `0`。

- [ ] **步骤 5（2–5 分钟）：提交用户端基础页面**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.ts'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioBootstrap.test.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuotaSummary.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioSider.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 接入草稿启动额度与任务中心"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 14：建立运营端共享类型和分页适配

**最小任务卡：**

- **单一目标／不做：** 建立运营端 P0-C 共享类型、常量与分页适配；不复制用户端 DTO，不改变 RuoYi 响应语义。
- **风险／触发：** 红色；命中平台共享契约、编号精度和分页语义。
- **权威来源：** API 契约、平台现有请求层、HTTP string 边界和 ProComponents 分页约定。
- **成功／反向验收：** `R<T>|null`、string 编号／金额／版本和分页映射保真；空 data、业务错误与网络错误可区分。
- **所有权／数据范围：** 仅 platform `api/aivideo/shared` 与测试；不影响用户端共享文件。
- **依赖／人员／并发：** 依赖任务 11；开发 A 实施、开发 C 独立契约 reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑共享适配 Vitest／类型检查但不得判 PASS；reviewer 核对精度与错误分支。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/constants.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/pageAdapter.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/pageAdapter.test.ts`

- [ ] **步骤 1（2–5 分钟）：编写失败的字符串金额与分页测试**

```typescript
it('保持编号、额度和成本字符串不做浮点转换', () => {
  const page = toAivideoTableData({
    code: 200,
    msg: '操作成功',
    data: {
      rows: [{
        operationId: '9007199254740993',
        amount: '10',
        providerCost: '0.12345678',
      }],
      total: 1,
    },
  });
  expect(page.data[0].operationId).toBe('9007199254740993');
  expect(page.data[0].amount).toBe('10');
  expect(page.data[0].providerCost).toBe('0.12345678');
});
```

- [ ] **步骤 2（2–5 分钟）：运行测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task14-red.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run src/api/aivideo/shared/pageAdapter.test.ts `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$redExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：共享适配器骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现共享类型和映射**

```typescript
export interface AivideoPage<T> {
  rows: T[];
  total: number;
}

export const TASK_STATUS_LABEL: Record<AiTaskStatus, string> = {
  pending: '待提交',
  queued: '排队中',
  running: '运行中',
  success: '成功',
  failed: '失败',
  cancelled: '已取消',
};

export function toAivideoTableData<T>(response: R<AivideoPage<T>>) {
  return {
    data: response.data.rows,
    total: response.data.total,
    success: response.code === 200,
  };
}
```

共享类型只定义稳定状态、字符串编号/金额和分页，不复制各页面表单字段。

- [ ] **步骤 4（2–5 分钟）：运行共享测试和类型检查**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task14-green.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run src/api/aivideo/shared/pageAdapter.test.ts `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$greenExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：测试和 Lint 通过。

- [ ] **步骤 5（2–5 分钟）：提交运营端共享适配**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/constants.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/pageAdapter.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/shared/pageAdapter.test.ts'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 建立运营端业务共享类型"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 15：实现运营端方向目录管理页

**最小任务卡：**

- **单一目标／不做：** 实现运营端方向列表、复制、编辑和发布页；不绕过修订与权限，不直接写 Entity。
- **风险／触发：** 红色；命中高权限发布、共享目录和并发覆盖。
- **权威来源：** API 契约、方向发布规则、ProTable／ProForm 约定与状态矩阵。
- **成功／反向验收：** loading／empty／no-result／network+retry／403／pagination、提交防重、发布确认、修订冲突均可观察测试。
- **所有权／数据范围：** 仅 platform direction API／页面／测试；只影响目标目录草稿与发布版本。
- **依赖／人员／并发：** 依赖任务 3、14；开发 A 实施、开发 C 独立高权限／UX reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑逐状态 Vitest／构建但不得判 PASS；reviewer 核对权限、确认和冲突恢复。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.test.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的目录页面测试**

```tsx
it('发布必须显示版本、摘要和二次确认', async () => {
  renderDirectionPage();
  await user.click(await screen.findByRole('button', { name: '发布' }));
  expect(screen.getByText('将发布目录第 2 版')).toBeInTheDocument();
  expect(screen.getByText(/内容摘要/)).toBeInTheDocument();
  expect(publishMock).not.toHaveBeenCalled();
  await user.click(screen.getByRole('button', { name: '确认发布' }));
  expect(publishMock).toHaveBeenCalledWith('12', {
    expectedRevision: '3',
    publishNote: expect.any(String),
  });
});
```

- [ ] **步骤 2（2–5 分钟）：运行页面测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task15-red.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run src/pages/aivideo/direction/index.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$redExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：页面骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现 ProTable 与版本表单**

页面使用 `PageContainer`、`ProTable`、`ModalForm`、`ProFormList` 和 `ProDescriptions`，覆盖加载、空、无搜索结果、失败、403、分页、复制草稿、编辑行业/用途/排序/启用状态、修订冲突和发布确认。只有 `aivideo:direction:publish` 显示发布按钮，后端权限仍是最终门禁。

- [ ] **步骤 4（2–5 分钟）：运行页面测试、Lint 和构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task15-green.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run src/pages/aivideo/direction/index.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$greenExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
pnpm run build:prod
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：页面测试、Lint 和生产构建通过。

- [ ] **步骤 5（2–5 分钟）：提交方向管理页**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/direction/index.ts'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.test.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 提供方向目录管理页"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 16：实现运营端额度、价格、任务链与计费操作基础页

**最小任务卡：**

- **单一目标／不做：** 实现运营端额度、费率、任务和用量页面；不在前端计算余额／终态，不省略高风险确认。
- **风险／触发：** 红色；命中资产、高权限运营、任务补偿和审计。
- **权威来源：** API 契约、额度／任务状态机、ProComponents 和前端状态矩阵。
- **成功／反向验收：** 每列表覆盖 loading／empty／no-result／network+retry／403／pagination；调整／退款／补偿／取消含防重确认与冲突测试。
- **所有权／数据范围：** 仅 platform quota／tariff／task／usage API、页面和测试；不写后端事实。
- **依赖／人员／并发：** 依赖任务 5、6、8、14；开发 A 实施、开发 C 独立资金／UX reviewer，同一红色任务最多 2 人。
- **验证／检查点：** writer 跑逐状态 Vitest／构建但不得判 PASS；reviewer 核对金额精度、权限和风险操作。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

**文件：**
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/types.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/index.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx`
- 创建：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/components/OperationCostDrawer.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的额度凭据、页面拆分与成本权限测试**

```tsx
it('调整成功展示流水编号和前后余额', async () => {
  adjustQuotaMock.mockResolvedValue({
    ledgerId: '7001',
    availableBefore: '100',
    availableAfter: '125',
    lockedBefore: '0',
    lockedAfter: '0',
  });
  renderTextQuotaPage();
  await submitAdjustment(user, { delta: '25', reason: '运营赠送' });
  expect(await screen.findByText('流水编号：7001')).toBeInTheDocument();
  expect(screen.getByText('100 → 125')).toBeInTheDocument();
});

it('没有成本权限时不请求成本端点', async () => {
  renderUsagePage({ permissions: ['aivideo:usage:query'] });
  await openOperationDetail(user, '计费操作 91');
  expect(costApiMock).not.toHaveBeenCalled();
  expect(screen.queryByText('提供商成本')).not.toBeInTheDocument();
});

it('任务链页只查任务接口，不请求计费操作列表', async () => {
  renderTaskPage({ permissions: ['aivideo:task:admin-query'] });
  expect(await screen.findByText('任务 81')).toBeInTheDocument();
  expect(listTasksMock).toHaveBeenCalledTimes(1);
  expect(listUsageOperationsMock).not.toHaveBeenCalled();
});
```

- [ ] **步骤 2（2–5 分钟）：运行页面测试并确认失败**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task16-red.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run `
  src/pages/aivideo/textQuota/index.test.tsx `
  src/pages/aivideo/textTariff/index.test.tsx `
  src/pages/aivideo/task/index.test.tsx `
  src/pages/aivideo/usage/index.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$redExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Red'
if ($redExitCode -eq 0) { throw '红灯命令意外通过，必须先看到预期失败' }
```

预期：页面与接口模块骨架可编译、测试被正确发现，并由目标业务断言失败。

- [ ] **步骤 3（2–5 分钟）：实现最小管理页**

`textQuota` 包含账户表、赠送/正负调整弹窗、不可变流水和补偿链；表单预览 `availableBefore + delta`，负数导致预计余额小于零时禁用提交。`textTariff` 包含五个固定价格项、历史版本、草稿、新建、定时生效和发布确认。

`task` 的动态组件 key 固定为 `aivideo/task/index`，只展示任务筛选、分页、根/执行链、尝试、结果引用、失败信息和取消原因。`usage` 的动态组件 key 固定为 `aivideo/usage/index`，展示计费操作状态、账户主体、锁定/结算/释放、价格版本和流水链。`OperationCostDrawer` 只属于 `usage/components`；只有同时具备 `aivideo:usage-cost:query` 时才渲染入口和调用 `GET /api/admin/ai-usage/operations/{id}/costs`，仅有 `aivideo:usage:query` 时不得发出成本请求。

所有成功操作展示流水、价格版本或任务编号；所有列表覆盖加载、空、无搜索结果、失败、403 和分页。

- [ ] **步骤 4（2–5 分钟）：运行页面测试、Lint 和生产构建**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestEvidenceGatePath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task16-green.json').Trim())
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$vitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run `
  src/pages/aivideo/textQuota/index.test.tsx `
  src/pages/aivideo/textTariff/index.test.tsx `
  src/pages/aivideo/task/index.test.tsx `
  src/pages/aivideo/usage/index.test.tsx `
  --reporter=json "--outputFile=$vitestEvidenceGatePath"
$greenExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $vitestEvidenceGatePath -Mode 'Assert' `
  -StartedAtUtc $vitestStartedAt -ExpectedOutcome 'Green'
if ($greenExitCode -ne 0) { throw '验证命令执行失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
pnpm run build:prod
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：额度、价格、任务、成本权限测试通过，Lint 和构建退出码为 `0`。

- [ ] **步骤 5（2–5 分钟）：提交运营端基础管理**

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$expected = @(
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota/index.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/task/index.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/types.ts'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo/usage/index.ts'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/components/OperationCostDrawer.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '按任务文件清单暂存失败' }
$actual = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与本任务文件清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 提供额度价格任务与计费操作管理页"
if ($LASTEXITCODE -ne 0) { throw '提交失败' }
```

## 任务 17：执行 P0-C 全量门禁和三视角验收

**最小任务卡：**

- **单一目标／不做：** 执行完整后端、前端、迁移、分层和 F1 退出门禁并冻结唯一完整 F1；不访问／修改下游 worktree，不实际 rebase。
- **风险／触发：** 红色；命中全量集成、资产、安全、迁移、恢复和阶段交接。
- **权威来源：** 批准规格、主计划 Task 4 F1 门禁、任务 1–16 证据和公共开发规范。
- **成功／反向验收：** 完整 unit／IT／前端清单本次非零零跳过全绿，raw 负向与 AST 全绿；知识导入 revision 契约已经独立审核并与当前 F1 HEAD 一致；任一缺失即不冻结 F1。
- **所有权／数据范围：** 只验证 P0-C 文件集、写当前 worktree F1 证据并记录六共享文件移交；不操作 P1／P2／P3 分支。
- **依赖／人员／并发：** 依赖任务 1–16、已提交 P0-B candidate，以及由契约 owner 和独立 reviewer 预先签署的知识导入 revision 契约；开发 A 提证据／修复但不得判 PASS，开发 C 独立主审，同一红色任务最多 2 人。
- **验证／检查点：** writer 逐项执行门禁但不得代写 review 或 revision 契约记录；独立 reviewer 复跑关键证据后才能签署，P1／P2／P3 后续分别按主计划 Tasks 5–7 rebase 同一 F1。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`、只读 revision 契约校验结果和 `p0c-f1-handoff.json` 回读结果。

**文件：**
- 验证/按失败定向修改：`docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql`
- 验证/按失败定向修改：`docs/sql/ai-video/mysql/20260728_04_p0_seed.sql`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user`
- 验证/按失败定向修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform`
- 验证/按失败定向修改：`ai-video-ui/ai-video-webapp/src/services/ai-video`
- 验证/按失败定向修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio`
- 验证/按失败定向修改：`ai-video-ui/ai-video-platform-ui/src/api/aivideo`
- 验证/按失败定向修改：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo`
- 验证：`docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md`

- [ ] **步骤 1（2–5 分钟）：显式运行后端单元测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$unitSelectors = @(
  'QuotaBillingServiceTest'
  'AiTaskServiceTest'
  'AiTaskExecutionDispatcherTest'
  'ModuleDependencyTest'
  'AppTaskInitiatorResolverTest'
  'SysTaskInitiatorResolverTest'
)
$unitEvidenceGatePath = (& git rev-parse --git-path `
  'p0c-it-evidence-gate.ps1').Trim()
$unitStartedAt = & $unitEvidenceGatePath -RepoRoot $repoRoot `
  -Selector $unitSelectors -Mode 'Prepare' -ReportKind 'Surefire'
.\mvnw.cmd -pl `
  ruoyi-modules/ai-video/ai-video-core,`
  ruoyi-modules/ai-video/ai-video-infra,`
  ruoyi-modules/ai-video/ai-video-user,`
  ruoyi-modules/ai-video/ai-video-platform,`
  ai-video-user-api,ruoyi-admin -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
$unitExitCode = $LASTEXITCODE
& $unitEvidenceGatePath -RepoRoot $repoRoot -Selector $unitSelectors `
  -Mode 'Assert' -ReportKind 'Surefire' -StartedAtUtc $unitStartedAt `
  -ExpectedOutcome 'Green'
if ($unitExitCode -ne 0) { throw '验证命令执行失败' }
```

预期：六个精确 Surefire 目标类分别有本次 `tests > 0`、`failures = 0`、`errors = 0`、`skipped = 0` 报告，日志不含 `Tests are skipped`，Maven 输出 `BUILD SUCCESS`。

- [ ] **步骤 2（2–5 分钟）：显式运行后端单元与集成测试**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-api')
$expectedItClasses = @(
  'P0cSchemaIT'
  'DirectionCatalogServiceIT'
  'ScriptDraftServiceIT'
  'QuotaBillingServiceIT'
  'AiTaskConcurrencyIT'
  'AiTaskAttemptServiceIT'
  'AiTaskExecutionScannerIT'
  'P0cUserApiIT'
  'P0cUserBoundaryIT'
  'P0cPlatformApiIT'
  'P0cPlatformBoundaryIT'
)
$itSelector = $expectedItClasses -join ','
$itEvidenceGatePath = (& git rev-parse --git-path 'p0c-it-evidence-gate.ps1').Trim()
$itGateStartedAt = [DateTime]::UtcNow
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector @('*') -Mode 'Prepare' |
  Out-Null
.\mvnw.cmd -pl `
  ruoyi-modules/ai-video/ai-video-core,`
  ruoyi-modules/ai-video/ai-video-infra,`
  ruoyi-modules/ai-video/ai-video-user,`
  ruoyi-modules/ai-video/ai-video-platform,`
  ai-video-user-api,ruoyi-admin -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  '-Pdev,local-integration-test' `
  "-Dit.test=$itSelector" verify
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
$itReports = @(Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' |
  Where-Object {
    $_.FullName -match '[\\/]target[\\/]failsafe-reports[\\/]' -and
    $_.LastWriteTime -ge $itGateStartedAt
  })
foreach ($className in $expectedItClasses) {
  $classReports = @($itReports |
    Where-Object { $_.Name -like "TEST-*$className.xml" })
  if (-not $classReports) {
    throw "$className 未产生本次 Failsafe XML"
  }
  $totals = @{ tests = 0; failures = 0; errors = 0; skipped = 0 }
  foreach ($report in $classReports) {
    [xml]$suiteXml = Get-Content -LiteralPath $report.FullName
    $totals.tests += [int]$suiteXml.testsuite.tests
    $totals.failures += [int]$suiteXml.testsuite.failures
    $totals.errors += [int]$suiteXml.testsuite.errors
    $totals.skipped += [int]$suiteXml.testsuite.skipped
  }
  if ($totals.tests -le 0 -or
      $totals.failures -ne 0 -or
      $totals.errors -ne 0 -or
      $totals.skipped -ne 0) {
    throw "$className Failsafe 门禁失败：$($totals | ConvertTo-Json -Compress)"
  }
}
& $itEvidenceGatePath -RepoRoot $repoRoot -Selector $expectedItClasses `
  -StartedAtUtc $itGateStartedAt -ExpectedOutcome 'Green'
```

预期：六个准确 Surefire 目标类和 11 个准确 Failsafe 目标类逐个有本次报告、`tests > 0`、`failures = 0`、`errors = 0`、`skipped = 0`。目录、草稿、任务、额度、流水、并发槽、接口与装配测试全部通过；仅有 `BUILD SUCCESS` 不足以通过门禁。

- [ ] **步骤 3（2–5 分钟）：运行两端前端和文档验证**

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
$vitestGateScriptPath = (& git rev-parse --git-path `
  'p0c-vitest-evidence-gate.ps1').Trim()
$webappVitestReportPath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task17-webapp-green.json').Trim())
$webappVitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $webappVitestReportPath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-webapp')
npm.cmd test -- --reporter=json "--outputFile=$webappVitestReportPath"
$webappTestExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $webappVitestReportPath -Mode 'Assert' `
  -StartedAtUtc $webappVitestStartedAt -ExpectedOutcome 'Green'
if ($webappTestExitCode -ne 0) { throw '验证命令执行失败' }
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
Set-Location -LiteralPath $repoRoot
$platformVitestReportPath = [System.IO.Path]::GetFullPath(
  (& git rev-parse --git-path 'p0c-vitest-task17-platform-green.json').Trim())
$platformVitestStartedAt = & $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $platformVitestReportPath -Mode 'Prepare'
Set-Location -LiteralPath (Join-Path $repoRoot 'ai-video-ui\ai-video-platform-ui')
pnpm exec vitest run --reporter=json `
  "--outputFile=$platformVitestReportPath"
$platformTestExitCode = $LASTEXITCODE
& $vitestGateScriptPath -RepoRoot $repoRoot `
  -ReportPath $platformVitestReportPath -Mode 'Assert' `
  -StartedAtUtc $platformVitestStartedAt -ExpectedOutcome 'Green'
if ($platformTestExitCode -ne 0) { throw '验证命令执行失败' }
pnpm run lint
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
pnpm run build:prod
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
Set-Location -LiteralPath $repoRoot
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '验证命令执行失败' }
```

预期：两端测试、Lint、构建成功，文档校验输出 `DEVELOPMENT_STANDARDS_OK`。

- [ ] **步骤 4（2–5 分钟）：运行账务、边界与占位扫描**

独立 reviewer 必须在重新运行本步骤全部门禁后，亲自在当前 worktree 的 Git metadata 创建 `p0c-independent-review.json`；实现 writer 禁止创建、修改或补写该文件。记录 schema 固定为 `owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`f0Head`、`p0bCandidateHead`、`reviewCompletedAtUtc`：`reviewStatus` 只接受 `PASS`，`reviewer` 必须与 `owner` 不同，`reviewedHead` 必须精确等于待冻结 F1 HEAD，两个基线 SHA 必须与 baseline metadata 一致，完成时间必须是 UTC。

冻结前还必须由知识导入 revision 契约 owner 与独立 reviewer 在当前 worktree 的 Git metadata 准备只读 `p0c-knowledge-import-revision-contract.json`。F1 handoff writer 禁止创建、修改或补写该记录，只能 fail-closed 读取。记录字段精确固定为 `status`、`contractOwner`、`reviewer`、`reviewStatus`、`f1Head`、`confirmedAtUtc`、`draftRevisionSource`、`branchRevisionSource`、`generationContextRevisionSource`，九个字段的 JSON 类型都必须是 string：`status` 只接受 `CONFIRMED`，`reviewStatus` 只接受 `PASS`，owner／reviewer 必须非空且不同，`f1Head` 必须是与待冻结 F1 HEAD 大小写精确一致的 40 位 SHA，确认时间必须显式以 `Z` 或 `+00:00` 结尾且解析为 UTC，三个 revision 来源 Trim 后必须非空并给出可执行的契约说明。缺字段、额外字段、类型错误、未独立审核、HEAD 漂移或来源为空时均不得冻结 F1。

运行：

```powershell
$repoRootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRootText)) {
  throw '无法从当前执行位置解析 worktree 根目录'
}
$repoRoot = [System.IO.Path]::GetFullPath($repoRootText.Trim())
$gateScriptPath = (& git rev-parse --git-path 'p0c-worktree-gate.ps1').Trim()
if (-not (Test-Path -LiteralPath $gateScriptPath -PathType Leaf)) {
  throw '缺少 P0-C worktree 门禁；先执行任务 1 启动门禁'
}
Set-Location -LiteralPath $repoRoot
& $gateScriptPath -RepoRoot $repoRoot
if ($LASTEXITCODE -ne 0) { throw 'P0-C worktree 门禁失败' }
Set-Location -LiteralPath $repoRoot
$redFlags = @(
  ('TO' + 'DO'),
  ('待' + '定'),
  ('后续' + '实现'),
  ('补充' + '细节'),
  ('添加' + '适当'),
  ('处理' + '边界'),
  ('类似' + '任务')
)
$hits = Select-String `
  -LiteralPath docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md `
  -Pattern $redFlags
if ($hits) { $hits; throw '计划仍含提交占位' }
$numericTypeHits = @(rg -n "double|float" `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota `
  ai-video-ui/ai-video-webapp/src/services/ai-video/quota `
  ai-video-ui/ai-video-platform-ui/src/api/aivideo/quota)
$numericScanExitCode = $LASTEXITCODE
if ($numericScanExitCode -gt 1) { throw '额度数值类型扫描命令执行失败' }
if ($numericTypeHits) { $numericTypeHits; throw '额度代码出现禁止的浮点类型' }
$providerLeakHits = @(rg -n "com\\.openai|http://|https://" `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo)
$providerScanExitCode = $LASTEXITCODE
if ($providerScanExitCode -gt 1) { throw '提供商泄漏扫描命令执行失败' }
if ($providerLeakHits) { $providerLeakHits; throw '核心模块泄漏了提供商或网络实现' }
$ownerFieldHits = @(rg -n "ownerId|tenantId|billingSubjectId" `
  ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo)
$ownerScanExitCode = $LASTEXITCODE
if ($ownerScanExitCode -gt 1) { throw '用户写请求归属字段扫描命令执行失败' }
if ($ownerFieldHits) { $ownerFieldHits; throw '用户写请求包含禁止的归属字段' }
$nonRetryableException = Get-Content -Raw -LiteralPath `
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/AiTaskNonRetryableException.java'
if ($nonRetryableException -notmatch
    '\[A-Z\]\[A-Z0-9_\]\{2,63\}') {
  throw 'AiTaskNonRetryableException 缺少稳定大写失败码校验'
}
$scannerTest = Get-Content -Raw -LiteralPath `
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java'
$requiredStaleBranchAssertions = @(
  'successfulProviderAttemptThenStaleResultFailsWithoutRetryAndReleasesQuota',
  'STALE_BRANCH_RESULT',
  '"success"',
  '"released"',
  '"release"',
  '"settle"',
  'fakeGateway.invocationCount',
  'scanner.jobExecute'
)
foreach ($assertion in $requiredStaleBranchAssertions) {
  if ($scannerTest -notmatch [regex]::Escape($assertion)) {
    throw "AiTaskExecutionScannerIT 缺少旧分支终态断言：$assertion"
  }
}
if ($scannerTest -cmatch '\bstale_branch_result\b') {
  throw '旧分支失败码错误使用了小写 stale_branch_result'
}
$appResolver = `
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/task/security/AppTaskInitiatorResolver.java'
$sysResolver = `
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/task/security/SysTaskInitiatorResolver.java'
if (-not (Select-String -LiteralPath $appResolver `
    -SimpleMatch 'AppAuthorizationActorResolver' -Quiet)) {
  throw 'AppTaskInitiatorResolver 未依赖用户 actor adapter'
}
if (Select-String -LiteralPath $appResolver `
    -Pattern 'AppLoginHelper|\bLoginHelper\b|SysAuthorizationActorResolver|org\.dromara\.system' `
    -Quiet) {
  throw 'AppTaskInitiatorResolver 直接读取或尝试了另一身份域'
}
if (-not (Select-String -LiteralPath $sysResolver `
    -SimpleMatch 'SysAuthorizationActorResolver' -Quiet)) {
  throw 'SysTaskInitiatorResolver 未依赖运营 actor adapter'
}
if (Select-String -LiteralPath $sysResolver `
    -Pattern 'AppLoginHelper|\bLoginHelper\b|AppAuthorizationActorResolver|AppUserMapper' `
    -Quiet) {
  throw 'SysTaskInitiatorResolver 直接读取或尝试了另一身份域'
}
$platformRoot = `
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform'
$defaultLoginHelperActual = @(
  Get-ChildItem -LiteralPath $platformRoot -Recurse -File -Filter '*.java' |
    Select-String -SimpleMatch `
      'org.dromara.common.satoken.utils.LoginHelper' |
    Select-Object -ExpandProperty Path -Unique |
    ForEach-Object { [System.IO.Path]::GetFullPath($_) }
)
$defaultLoginHelperExpected = @(
  [System.IO.Path]::GetFullPath(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/identity/service/impl/AppIdentityAdminServiceImpl.java'),
  [System.IO.Path]::GetFullPath(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo/platform/authorization/security/SysAuthorizationActorResolver.java')
)
$loginHelperDiff = Compare-Object `
  ($defaultLoginHelperExpected | Sort-Object -Unique) `
  ($defaultLoginHelperActual | Sort-Object -Unique)
if ($loginHelperDiff) {
  $loginHelperDiff
  throw '累计默认 LoginHelper 调用点偏离精确白名单'
}
$backgroundIdentityHits = @(rg -n `
  '\bLoginHelper\b|AppLoginHelper' `
  ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task `
  ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task)
$backgroundIdentityExitCode = $LASTEXITCODE
if ($backgroundIdentityExitCode -gt 1) { throw '后台身份依赖扫描执行失败' }
if ($backgroundIdentityHits) {
  $backgroundIdentityHits
  throw '后台任务代码读取了 Web 登录身份'
}
$backendScanRoots = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test'
  'ai-video-api/ai-video-user-api/src/test'
  'ai-video-api/ruoyi-admin/src/test'
)
$typescriptScanRoots = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video'
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio'
  'ai-video-ui/ai-video-platform-ui/src/api/aivideo'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo'
)
$requiredPaths = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ProviderUsageDTO.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/provider/ModelProvider.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/client/WebSearchClient.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskAttemptServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/ModuleDependencyTest.java'
)
$missingRequiredPaths = @($requiredPaths | Where-Object {
  -not (Test-Path -LiteralPath $_ -PathType Leaf)
})
if ($missingRequiredPaths) {
  $missingRequiredPaths
  throw 'P0-C 必需边界文件缺失'
}
$coreJavaRoot = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/'
$forbiddenCorePaths = @(
  ($coreJavaRoot + 'pro' + 'vider')
  ($coreJavaRoot + 'cli' + 'ent')
  ($coreJavaRoot + 'common/' + 'ac' + 'tor')
  ($coreJavaRoot + 'direction/domain/' + 'b' + 'o')
  ($coreJavaRoot + 'direction/domain/' + 'v' + 'o')
  ($coreJavaRoot + 'quota/domain/' + 'b' + 'o')
  ($coreJavaRoot + 'quota/domain/' + 'v' + 'o')
  ($coreJavaRoot + 'task/domain/' + 'b' + 'o')
  ($coreJavaRoot + 'task/domain/' + 'v' + 'o')
)
$presentForbiddenPaths = @($forbiddenCorePaths | Where-Object {
  Test-Path -LiteralPath $_
})
if ($presentForbiddenPaths) {
  $presentForbiddenPaths
  throw 'core 出现端侧 BO／VO、身份 Resolver 或 provider/client 路径'
}
$rawNegativePatterns = @(
  ('(?<!App)(?<!Sys)\b' + ('TaskInitiator' + 'Resolver') + '\b')
  ('\b' + ('Model' + 'Gateway') + '\b')
  ('\b' + ('WebSearch' + 'Gateway') + '\b')
  ('\b' + ('unwrap' + 'R') + '\b')
  ('\b' + ('request' + 'R') + '\b')
  ('\b' + ('ChargeableTask' + 'Command') + '\b')
  ('\b' + ('FreeTask' + 'Command') + '\b')
  ('\b' + ('QuotaLock' + 'Command') + '\b')
  ('\b' + ('TaskCreation' + 'Result') + '\b')
)
$allRawTargets = @($backendScanRoots + $typescriptScanRoots + @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md'
))
foreach ($pattern in $rawNegativePatterns) {
  $rawHits = @(rg --pcre2 --case-sensitive -n -- $pattern @allRawTargets)
  $rawExitCode = $LASTEXITCODE
  if ($rawExitCode -gt 1) { throw "raw negative 扫描失败：$pattern" }
  if ($rawHits) { $rawHits; throw "raw negative 命中：$pattern" }
}
$coreMainTestRoots = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java'
)
$endpointBoundaryTypeNames = @(
  ('DirectionOptions' + 'Vo')
  ('DirectionCatalog' + 'Vo')
  ('CreateScriptDraft' + 'Bo')
  ('ScriptDraftOverview' + 'Vo')
  ('AiTaskQuery' + 'Bo')
  ('AiTaskSummary' + 'Vo')
  ('AiTaskDetail' + 'Vo')
  ('QuotaAccountQuery' + 'Bo')
  ('QuotaLedgerQuery' + 'Bo')
  ('CreateTariff' + 'Bo')
  ('AdjustQuota' + 'Bo')
  ('QuotaAccount' + 'Vo')
  ('QuotaTariff' + 'Vo')
  ('QuotaLedgerEntry' + 'Vo')
  ('AiUsageCost' + 'Vo')
)
$coreBoundaryPatterns = @(
  ('\b' + ('taskInitiator' + 'Resolver') + '\b')
  ('org\.dromara\.aivideo\.(user|platform)\..*\.domain\.(' +
    ('b' + 'o') + '|' + ('v' + 'o') + ')\.')
)
$coreBoundaryPatterns += @($endpointBoundaryTypeNames | ForEach-Object {
  '\b' + [regex]::Escape($_) + '\b'
})
foreach ($pattern in $coreBoundaryPatterns) {
  $coreBoundaryHits = @(
    rg --pcre2 --case-sensitive -n -- $pattern @coreMainTestRoots)
  $coreBoundaryExitCode = $LASTEXITCODE
  if ($coreBoundaryExitCode -gt 1) {
    throw "core Resolver／端侧 BO/VO 扫描失败：$pattern"
  }
  if ($coreBoundaryHits) {
    $coreBoundaryHits
    throw "core Resolver／端侧 BO/VO 引用命中：$pattern"
  }
}
$oldPackagePatterns = @(
  ('package org\.dromara\.aivideo\.common\.' + 'ac' + 'tor')
  'package org\.dromara\.aivideo\.(direction|quota|task)\.domain\.(bo|vo)'
  'package org\.dromara\.aivideo\.task\.infra'
  'package org\.dromara\.aivideo\.model'
)
foreach ($pattern in $oldPackagePatterns) {
  $oldPackageHits = @(rg --pcre2 --case-sensitive -n -- $pattern @backendScanRoots)
  $oldPackageExitCode = $LASTEXITCODE
  if ($oldPackageExitCode -gt 1) { throw "旧 package 扫描失败：$pattern" }
  if ($oldPackageHits) { $oldPackageHits; throw "旧 package 命中：$pattern" }
}
$contractChecks = @(
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/service/IDirectionCatalogService.java'
    Required = @(
      'public interface IDirectionCatalogService',
      'DirectionCatalogSnapshotDTO currentPublishedCatalog();')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/service/IQuotaBillingService.java'
    Required = @(
      'public interface IQuotaBillingService',
      'QuotaLockResultDTO lock(QuotaLockRequestDTO request);',
      'QuotaAccountSnapshotDTO settle(Long operationId, Long rootTaskId);',
      'QuotaAccountSnapshotDTO release(Long operationId, Long rootTaskId, String failureCode);',
      'TaskInitiatorDTO initiator')
    RequiredRegex = @(
      'void\s+publishTariff\s*\([^)]*TaskInitiatorDTO\s+initiator\s*\)',
      'QuotaAccountSnapshotDTO\s+adjust\s*\([^)]*TaskInitiatorDTO\s+initiator\s*\)',
      'QuotaAccountSnapshotDTO\s+refund\s*\([^)]*TaskInitiatorDTO\s+initiator\s*\)',
      'QuotaAccountSnapshotDTO\s+compensate\s*\([^)]*TaskInitiatorDTO\s+initiator\s*\)')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java'
    Required = @(
      'public interface IAiTaskService',
      'createChargeableTask(', 'createFreeTask(', 'claimExecutableTasks(',
      'renewLease(', 'recordHandlerFailure(', 'markSuccess(', 'markFailed('
    )
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionDispatcher.java'
    Required = @(
      'public interface IAiTaskExecutionDispatcher',
      'void enqueue(Long rootTaskId, Long executionTaskId);')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskAttemptService.java'
    Required = @(
      'public interface IAiTaskAttemptService',
      'startAttempt(', 'completeAttempt(', 'failAttempt(')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/service/IScriptDraftService.java'
    Required = @(
      'public interface IScriptDraftService',
      'ScriptDraftOverviewDTO create(CreateScriptDraftDTO request);')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskExecutionHandler.java'
    Required = @(
      'public interface IAiTaskExecutionHandler',
      'AiTaskType supports();',
      'void handle(AiTaskExecutionLeaseDTO lease);')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/CreateScriptDraftDTO.java'
    Required = @('public record CreateScriptDraftDTO(', 'String idempotencyKey')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/ScriptDraftOverviewDTO.java'
    Required = @(
      'public record ScriptDraftOverviewDTO(', 'long draftRevision',
      'List<StepGuardDTO> stepGuards')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/dto/StepGuardDTO.java'
    Required = @(
      'public record StepGuardDTO(', 'String stepCode', 'String status')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockRequestDTO.java'
    Required = @(
      'public record QuotaLockRequestDTO(', 'Long expectedTariffVersion',
      'TaskInitiatorDTO initiator')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaLockResultDTO.java'
    Required = @(
      'public record QuotaLockResultDTO(', 'Long operationId',
      'QuotaAccountSnapshotDTO account')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/dto/QuotaAccountSnapshotDTO.java'
    Required = @(
      'public record QuotaAccountSnapshotDTO(', 'Long accountId',
      'long availableBalance', 'long lockedBalance')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskInitiatorDTO.java'
    Required = @(
      'public record TaskInitiatorDTO(', 'String actorType', 'Long actorId')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ChargeableTaskDTO.java'
    Required = @(
      'public record ChargeableTaskDTO(', 'Long tariffVersion',
      'TaskInitiatorDTO initiator',
      'TaskRevisionSnapshotDTO revisionSnapshot')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/FreeTaskDTO.java'
    Required = @(
      'public record FreeTaskDTO(', 'TaskInitiatorDTO initiator',
      'TaskRevisionSnapshotDTO revisionSnapshot')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskRevisionSnapshotDTO.java'
    Required = @(
      'public record TaskRevisionSnapshotDTO(',
      'Long generationContextRevision', 'String generationInputHash',
      'Map<Long, Long> factDecisionRevisions')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskCreationResultDTO.java'
    Required = @(
      'public record TaskCreationResultDTO(', 'Long rootTaskId',
      'Long executionTaskId', 'Long usageOperationId')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/TaskResultReferenceDTO.java'
    Required = @(
      'public record TaskResultReferenceDTO(', 'String resultRefType',
      'Long resultRefId')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskExecutionLeaseDTO.java'
    Required = @(
      'public record AiTaskExecutionLeaseDTO(', 'Long rootTaskId',
      'Long executionTaskId', 'String leaseOwner', 'Instant leaseExpiresAt')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/AiTaskAttemptHandleDTO.java'
    Required = @(
      'public record AiTaskAttemptHandleDTO(', 'Long attemptId',
      'int providerCallSequence', 'String callPurpose')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/dto/ProviderUsageDTO.java'
    Required = @(
      'public record ProviderUsageDTO(', 'Long inputTokens',
      'Long outputTokens', 'BigDecimal actualCost', 'String currency')
  }
  @{
    Path = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/dto/DirectionCatalogSnapshotDTO.java'
    Required = @(
      'public record DirectionCatalogSnapshotDTO(', 'Long catalogVersion',
      'String contentHash', 'Long industryCatalogVersion',
      'Long purposeCatalogVersion', 'String durationRuleVersion',
      'List<IndustryOption> industries',
      'Map<String, List<PurposeOption>> purposesByIndustry',
      'List<TargetDurationOption> targetDurations',
      'industryCatalogVersion == null', 'industryCatalogVersion < 1',
      'purposeCatalogVersion == null', 'purposeCatalogVersion < 1',
      'durationRuleVersion == null', 'durationRuleVersion.isBlank()')
    RequiredRegex = @(
      '(?s)record\s+DirectionCatalogSnapshotDTO\s*\(\s*Long\s+catalogVersion\s*,\s*String\s+contentHash\s*,\s*Long\s+industryCatalogVersion\s*,\s*Long\s+purposeCatalogVersion\s*,\s*String\s+durationRuleVersion\s*,\s*List<IndustryOption>\s+industries\s*,\s*Map<String,\s*List<PurposeOption>>\s+purposesByIndustry\s*,\s*List<TargetDurationOption>\s+targetDurations\s*\)')
  }
)
if ($contractChecks.Count -ne 23) {
  throw '冻结契约门禁必须精确覆盖 5 个稳定服务、2 个内部服务和 16 个 DTO'
}
foreach ($check in $contractChecks) {
  if (-not (Test-Path -LiteralPath $check.Path -PathType Leaf)) {
    throw "稳定接口缺失：$($check.Path)"
  }
  $source = Get-Content -Raw -LiteralPath $check.Path
  $normalizedSource = [regex]::Replace($source, '\s+', ' ')
  foreach ($signature in $check.Required) {
    if (-not $normalizedSource.Contains($signature)) {
      throw "$($check.Path) 缺少冻结签名：$signature"
    }
  }
  foreach ($signaturePattern in @($check.RequiredRegex)) {
    if (-not [regex]::IsMatch($source, $signaturePattern)) {
      throw "$($check.Path) 缺少冻结签名模式：$signaturePattern"
    }
  }
}
$expectedItSources = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/foundation/P0cSchemaIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/direction/DirectionCatalogServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskConcurrencyIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskAttemptServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/task/provider/AiTaskExecutionScannerIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/P0cUserApiIT.java'
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/assembly/P0cUserBoundaryIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/platform/P0cPlatformApiIT.java'
  'ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/assembly/P0cPlatformBoundaryIT.java'
)
foreach ($itSourcePath in $expectedItSources) {
  if (-not (Test-Path -LiteralPath $itSourcePath -PathType Leaf)) {
    throw "IT 源文件缺失：$itSourcePath"
  }
  $itSource = Get-Content -Raw -LiteralPath $itSourcePath
  if ($itSource -cnotmatch '@Tag\("dev"\)' -or
      $itSource -cnotmatch 'LocalIntegrationEnvironment\.requireFromEnvironment\(\)') {
    throw "$itSourcePath 缺少类级 @Tag(dev) 或本机安全夹具"
  }
}
$frontStateTestNames = @(
  'showsLoadingState'
  'showsEmptyState'
  'showsNoResultState'
  'showsNetworkErrorAndRetry'
  'showsForbiddenState'
  'supportsPagination'
)
$frontStateTestFiles = @(
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/direction/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textQuota/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/textTariff/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx'
  'ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx'
)
foreach ($testFile in $frontStateTestFiles) {
  if (-not (Test-Path -LiteralPath $testFile -PathType Leaf)) {
    throw "前端状态矩阵文件缺失：$testFile"
  }
  $testSource = Get-Content -Raw -LiteralPath $testFile
  foreach ($testName in $frontStateTestNames) {
    $singleQuotedName = [regex]::Escape(
      ([char]39) + $testName + ([char]39))
    $doubleQuotedName = [regex]::Escape(
      ([char]34) + $testName + ([char]34))
    $testCallPattern = '(?m)\b(?:it|test)\s*\(\s*(?:' +
      $singleQuotedName + '|' + $doubleQuotedName + ')'
    if (-not [regex]::IsMatch($testSource, $testCallPattern)) {
      throw "$testFile 缺少真实 it/test(...) 调用：$testName"
    }
  }
}
$planPath = 'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md'
$planText = Get-Content -Raw -LiteralPath $planPath
$ticks = -join @([char]96, [char]96, [char]96)
$powerShellFence = $ticks + 'powershell'
$powerShellPattern = '(?ms)^' + [regex]::Escape($powerShellFence) +
  '\r?\n(?<script>.*?)^' + [regex]::Escape($ticks) + '[ \t]*\r?$'
$powerShellBlocks = [regex]::Matches($planText, $powerShellPattern)
if ($powerShellBlocks.Count -ne 53) {
  throw "PowerShell 块数量漂移：$($powerShellBlocks.Count)"
}
foreach ($block in $powerShellBlocks) {
  $script = $block.Groups['script'].Value
  $tokens = $null
  $parseErrors = $null
  [void][System.Management.Automation.Language.Parser]::ParseInput(
    $script, [ref]$tokens, [ref]$parseErrors)
  if ($parseErrors.Count -ne 0) {
    $parseErrors | ForEach-Object { $_.Message }
    throw 'PowerShell 代码块 AST 解析失败'
  }
  if ($script -notmatch 'git rev-parse --show-toplevel' -or
      $script -notmatch '& \$gateScriptPath -RepoRoot \$repoRoot') {
    throw 'PowerShell 代码块缺少动态根目录或 P0-C worktree 门禁'
  }
  if ($script -match '-Dit\.test=' -and
      ($script -notmatch '-Pdev,local-integration-test' -or
       $script -notmatch "Mode 'Prepare'" -or
       $script -notmatch 'p0c-it-evidence-gate\.ps1')) {
    throw 'IT 代码块缺少 profile、旧 XML 清理或 fresh report 断言'
  }
}
$dtestMarker = '-D' + 'test='
$dtestBlocks = @($powerShellBlocks | Where-Object {
  $_.Groups['script'].Value.Contains($dtestMarker)
})
if ($dtestBlocks.Count -ne 12 -or @($dtestBlocks | Where-Object {
    $unitScript = $_.Groups['script'].Value
    $unitScript -notmatch "Mode 'Prepare'" -or
    $unitScript -notmatch "Mode 'Assert'" -or
    $unitScript -notmatch "ReportKind 'Surefire'"
  }).Count -ne 0) {
  throw '12 个 -Dtest 单元块必须逐块具备 Surefire Prepare + Assert'
}
$vitestCommandPattern = '(npm\.cmd test|pnpm exec vite' + 'st run)'
$vitestBlocks = @($powerShellBlocks | Where-Object {
  $_.Groups['script'].Value -match $vitestCommandPattern
})
if ($vitestBlocks.Count -ne 11 -or @($vitestBlocks | Where-Object {
    $vitestScript = $_.Groups['script'].Value
    $vitestScript -notmatch '--reporter=json' -or
    $vitestScript -notmatch '--outputFile=' -or
    $vitestScript -notmatch "Mode 'Prepare'" -or
    $vitestScript -notmatch "Mode 'Assert'"
  }).Count -ne 0) {
  throw '11 个 Vitest 块必须逐块具备 JSON Prepare + Assert'
}
$itMarker = '-Dit.' + 'test='
$itBlocks = @($powerShellBlocks | Where-Object {
  $_.Groups['script'].Value.Contains($itMarker)
})
if ($itBlocks.Count -ne 18) {
  throw "IT PowerShell 块数量漂移：$($itBlocks.Count)"
}
$absolutePrefix = 'D:' + [char]92 + 'Workspace' + [char]92
if ($planText.Contains($absolutePrefix)) {
  throw '计划仍含开发机绝对路径'
}
'P0C_RAW_NEGATIVES_OK'
'P0C_POWERSHELL_AST_OK'
git diff --check
if ($LASTEXITCODE -ne 0) { throw '工作树差异检查失败' }
$dirtyBeforeFreeze = @(git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0 -or $dirtyBeforeFreeze.Count -ne 0) {
  $dirtyBeforeFreeze
  throw '冻结完整 F1 前当前 P0-C worktree 必须干净'
}
$baselineRecordPath = (& git rev-parse --git-path 'p0c-baseline.json').Trim()
if (-not (Test-Path -LiteralPath $baselineRecordPath -PathType Leaf)) {
  throw '缺少 P0-C baseline metadata，不能冻结完整 F1'
}
$baselineRecord = Get-Content -Raw -LiteralPath $baselineRecordPath |
  ConvertFrom-Json
$f0Head = (& git rev-parse "$($baselineRecord.f0Head)^{commit}").Trim()
$p0bCandidateHead = (& git rev-parse `
  "$($baselineRecord.p0bCandidateHead)^{commit}").Trim()
$p0cAcceptanceWindowStart = (& git rev-parse `
  "$($baselineRecord.baselineHead)^{commit}").Trim()
$f1Head = (& git rev-parse 'HEAD^{commit}').Trim()
if ($LASTEXITCODE -ne 0 -or
    $f0Head -notmatch '^[0-9a-f]{40}$' -or
    $p0bCandidateHead -notmatch '^[0-9a-f]{40}$' -or
    $p0cAcceptanceWindowStart -notmatch '^[0-9a-f]{40}$' -or
    $f1Head -notmatch '^[0-9a-f]{40}$') {
  throw 'F0、P0-B candidate、acceptance start 或完整 F1 不是可解析的 40 位提交 SHA'
}
if ($p0cAcceptanceWindowStart -ne $p0bCandidateHead) {
  throw 'P0-C acceptance window 起点必须精确等于已批准 P0-B candidate'
}
git merge-base --is-ancestor $f0Head $p0bCandidateHead
if ($LASTEXITCODE -ne 0 -or $f0Head -eq $p0bCandidateHead) {
  throw 'P0-B candidate 必须是 F0 的非空后继'
}
git merge-base --is-ancestor $p0bCandidateHead $f1Head
if ($LASTEXITCODE -ne 0 -or $p0bCandidateHead -eq $f1Head) {
  throw '完整 F1 必须是已批准 P0-B candidate 的非空后继'
}
$reviewRecordPath = (& git rev-parse --git-path `
  'p0c-independent-review.json').Trim()
if (-not (Test-Path -LiteralPath $reviewRecordPath -PathType Leaf)) {
  throw '缺少独立 reviewer 创建的 p0c-independent-review.json'
}
$reviewRecord = Get-Content -Raw -LiteralPath $reviewRecordPath |
  ConvertFrom-Json
$reviewCompletedAtUtc = [DateTimeOffset]::Parse(
  [string]$reviewRecord.reviewCompletedAtUtc)
if ([string]::IsNullOrWhiteSpace([string]$reviewRecord.owner) -or
    [string]::IsNullOrWhiteSpace([string]$reviewRecord.reviewer) -or
    $reviewRecord.owner -eq $reviewRecord.reviewer -or
    $reviewRecord.reviewStatus -ne 'PASS' -or
    $reviewRecord.reviewedHead -ne $f1Head -or
    $reviewRecord.f0Head -ne $f0Head -or
    $reviewRecord.p0bCandidateHead -ne $p0bCandidateHead -or
    $reviewCompletedAtUtc.Offset -ne [TimeSpan]::Zero) {
  throw '独立 reviewer 记录未通过身份、PASS、HEAD、基线或 UTC 校验'
}
$revisionContractFields = @(
  'status'
  'contractOwner'
  'reviewer'
  'reviewStatus'
  'f1Head'
  'confirmedAtUtc'
  'draftRevisionSource'
  'branchRevisionSource'
  'generationContextRevisionSource'
)
function Assert-ExactFieldSet {
  param(
    [AllowNull()] [object] $Value,
    [Parameter(Mandatory)] [string[]] $Expected,
    [Parameter(Mandatory)] [string] $Name
  )
  if ($Value -isnot [pscustomobject]) {
    throw "$Name 必须是 JSON object"
  }
  $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
  $fieldDiff = Compare-Object `
    @($Expected | Sort-Object) @($actual | Sort-Object) -CaseSensitive
  if ($actual.Count -ne $Expected.Count -or $fieldDiff) {
    $fieldDiff
    throw "$Name 字段集合不精确"
  }
}
function Assert-StrictString {
  param(
    [AllowNull()] [object] $Actual,
    [Parameter(Mandatory)] [string] $Expected,
    [Parameter(Mandatory)] [string] $Name
  )
  if ($Actual -isnot [string] -or $Actual -cne $Expected) {
    throw "$Name 必须是值精确匹配的 JSON string"
  }
}
function Assert-StrictBoolean {
  param(
    [AllowNull()] [object] $Actual,
    [Parameter(Mandatory)] [bool] $Expected,
    [Parameter(Mandatory)] [string] $Name
  )
  if ($Actual -isnot [bool] -or -not $Actual.Equals($Expected)) {
    throw "$Name 必须是值精确匹配的 JSON boolean"
  }
}
function Assert-ExactStringArray {
  param(
    [Parameter(Mandatory)] [string[]] $Expected,
    [AllowNull()] [object] $Actual,
    [Parameter(Mandatory)] [string] $Name
  )
  if ($Actual -isnot [System.Array]) {
    throw "$Name 必须是 JSON array"
  }
  if ($Actual.Count -ne $Expected.Count) {
    throw "$Name 数量不一致"
  }
  for ($index = 0; $index -lt $Expected.Count; $index++) {
    if ($Actual[$index] -isnot [string] -or
        $Actual[$index] -cne $Expected[$index]) {
      throw "$Name 第 $index 项不一致"
    }
  }
}
function Assert-RevisionContract {
  param(
    [AllowNull()] [object] $Contract,
    [Parameter(Mandatory)] [string] $ExpectedF1Head,
    [Parameter(Mandatory)] [string[]] $ExpectedFields
  )
  Assert-ExactFieldSet -Value $Contract -Expected $ExpectedFields `
    -Name '知识导入 revision 契约'
  foreach ($field in $ExpectedFields) {
    if ($Contract.$field -isnot [string]) {
      throw "知识导入 revision 契约字段必须是 JSON string：$field"
    }
  }
  if ($Contract.confirmedAtUtc -cnotmatch '(?:Z|\+00:00)$') {
    throw '知识导入 revision 契约 confirmedAtUtc 必须显式使用 Z 或 +00:00'
  }
  $confirmedAtUtc = [DateTimeOffset]::Parse($Contract.confirmedAtUtc)
  if ($Contract.status -cne 'CONFIRMED' -or
      $Contract.reviewStatus -cne 'PASS' -or
      [string]::IsNullOrWhiteSpace($Contract.contractOwner) -or
      [string]::IsNullOrWhiteSpace($Contract.reviewer) -or
      $Contract.contractOwner.Trim() -ieq $Contract.reviewer.Trim() -or
      $Contract.f1Head -cnotmatch '^[0-9a-f]{40}$' -or
      $Contract.f1Head -cne $ExpectedF1Head -or
      $confirmedAtUtc.Offset -ne [TimeSpan]::Zero -or
      [string]::IsNullOrWhiteSpace($Contract.draftRevisionSource) -or
      [string]::IsNullOrWhiteSpace($Contract.branchRevisionSource) -or
      [string]::IsNullOrWhiteSpace(
        $Contract.generationContextRevisionSource)) {
    throw '知识导入 revision 契约未通过 CONFIRMED、独立 PASS、HEAD、UTC 或来源校验'
  }
  'P0C_REVISION_CONTRACT_OK'
}
function Assert-MustReject {
  param(
    [Parameter(Mandatory)] [scriptblock] $Case,
    [Parameter(Mandatory)] [string] $Name
  )
  $rejected = $false
  try {
    & $Case | Out-Null
  } catch {
    $rejected = $true
  }
  if (-not $rejected) {
    throw "严格 JSON schema 负向自测未拒绝：$Name"
  }
}
$validRevisionFixture = [pscustomobject][ordered]@{
  status = 'CONFIRMED'
  contractOwner = 'contract-owner'
  reviewer = 'independent-reviewer'
  reviewStatus = 'PASS'
  f1Head = $f1Head
  confirmedAtUtc = '2026-08-02T00:00:00Z'
  draftRevisionSource = 'fixture:draftRevision'
  branchRevisionSource = 'fixture:branchRevision'
  generationContextRevisionSource = 'fixture:generationContextRevision'
}
if ((Assert-RevisionContract -Contract $validRevisionFixture `
    -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields) -cne
    'P0C_REVISION_CONTRACT_OK') {
  throw '知识导入 revision 契约正向自测失败'
}
foreach ($field in $revisionContractFields) {
  $numberFixture = $validRevisionFixture | ConvertTo-Json -Depth 4 |
    ConvertFrom-Json
  $numberFixture.$field = 1
  Assert-MustReject -Name "revision number field $field" -Case {
    Assert-RevisionContract -Contract $numberFixture `
      -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields
  }
  $arrayFixture = $validRevisionFixture | ConvertTo-Json -Depth 4 |
    ConvertFrom-Json
  $arrayFixture.$field = [object[]]@('invalid-array-value')
  Assert-MustReject -Name "revision array field $field" -Case {
    Assert-RevisionContract -Contract $arrayFixture `
      -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields
  }
}
$timezoneFixture = $validRevisionFixture | ConvertTo-Json -Depth 4 |
  ConvertFrom-Json
$timezoneFixture.confirmedAtUtc = '2026-08-02T00:00:00'
Assert-MustReject -Name 'confirmedAtUtc without timezone' -Case {
  Assert-RevisionContract -Contract $timezoneFixture `
    -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields
}
$sameReviewerFixture = $validRevisionFixture | ConvertTo-Json -Depth 4 |
  ConvertFrom-Json
$sameReviewerFixture.contractOwner = 'alice'
$sameReviewerFixture.reviewer = 'Alice'
Assert-MustReject -Name 'case-only reviewer identity difference' -Case {
  Assert-RevisionContract -Contract $sameReviewerFixture `
    -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields
}
Assert-MustReject -Name 'string true as JSON boolean' -Case {
  Assert-StrictBoolean -Actual 'true' -Expected $true -Name 'fullF1Ready'
}
Assert-MustReject -Name 'scalar as single-item JSON array' -Case {
  Assert-ExactStringArray -Expected @('handler') -Actual 'handler' `
    -Name 'internalSpis'
}
Assert-MustReject -Name 'numeric JSON array item' -Case {
  Assert-ExactStringArray -Expected @('1') -Actual ([object[]]@(1)) `
    -Name 'stableServices'
}
Assert-MustReject -Name 'JSON field-name case drift' -Case {
  Assert-ExactFieldSet -Value ([pscustomobject]@{ F1Head = $f1Head }) `
    -Expected @('f1Head') -Name 'handoff fields'
}
Assert-StrictBoolean -Actual $true -Expected $true -Name 'fullF1Ready'
Assert-ExactStringArray -Expected @('handler') `
  -Actual ([object[]]@('handler')) -Name 'internalSpis'
'P0C_HANDOFF_STRICT_SCHEMA_SELFTEST_OK'
$revisionContractPath = (& git rev-parse --git-path `
  'p0c-knowledge-import-revision-contract.json').Trim()
if (-not (Test-Path -LiteralPath $revisionContractPath -PathType Leaf)) {
  throw '缺少契约 owner 与独立 reviewer 预先签署的知识导入 revision 契约'
}
$revisionContract = Get-Content -Raw -LiteralPath $revisionContractPath |
  ConvertFrom-Json
if ((Assert-RevisionContract -Contract $revisionContract `
    -ExpectedF1Head $f1Head -ExpectedFields $revisionContractFields) -cne
    'P0C_REVISION_CONTRACT_OK') {
  throw '知识导入 revision 契约校验 sentinel 缺失'
}
$revisionContractOwner = $revisionContract.contractOwner
$revisionContractReviewer = $revisionContract.reviewer
# 这是不可变原 F1 handoff 的历史迁移数组；当前完整链由 Task 18 addendum 追加 04a，禁止回写本数组。
$migrations = @(
  'docs/sql/ai-video/mysql/20260728_02_p0b_workspace_authorization.sql'
  'docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql'
  'docs/sql/ai-video/mysql/20260728_04_p0_seed.sql'
)
$sharedFiles = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts'
)
if (@($sharedFiles | Sort-Object -Unique).Count -ne 6) {
  throw '完整 F1 handoff 的共享文件清单必须精确为 6 个'
}
$downstreamRebaseOwners = [ordered]@{
  P1 = '主计划 Task 5'
  P2 = '主计划 Task 6'
  P3 = '主计划 Task 7'
}
$stableServices = @(
  'org.dromara.aivideo.task.service.IAiTaskService'
  'org.dromara.aivideo.task.service.IAiTaskExecutionDispatcher'
  'org.dromara.aivideo.direction.service.IDirectionCatalogService'
)
$internalSpis = @(
  'org.dromara.aivideo.task.service.IAiTaskExecutionHandler'
)
$stableDomainAndDtos = @(
  'org.dromara.aivideo.task.domain.AiTaskType'
  'org.dromara.aivideo.direction.dto.DirectionCatalogSnapshotDTO'
  'org.dromara.aivideo.task.dto.TaskInitiatorDTO'
  'org.dromara.aivideo.task.dto.FreeTaskDTO'
  'org.dromara.aivideo.task.dto.TaskRevisionSnapshotDTO'
  'org.dromara.aivideo.task.dto.TaskCreationResultDTO'
  'org.dromara.aivideo.task.dto.AiTaskExecutionLeaseDTO'
  'org.dromara.aivideo.task.dto.TaskResultReferenceDTO'
)
$knowledgeImportRevisionMapping = [ordered]@{
  status = [string]$revisionContract.status
  contractOwner = $revisionContractOwner
  reviewer = $revisionContractReviewer
  reviewStatus = [string]$revisionContract.reviewStatus
  f1Head = [string]$revisionContract.f1Head
  confirmedAtUtc = [string]$revisionContract.confirmedAtUtc
  draftRevisionSource = [string]$revisionContract.draftRevisionSource
  branchRevisionSource = [string]$revisionContract.branchRevisionSource
  generationContextRevisionSource =
    [string]$revisionContract.generationContextRevisionSource
}
$f1HandoffPath = (& git rev-parse --git-path 'p0c-f1-handoff.json').Trim()
$f1Handoff = [ordered]@{
  f1Head = $f1Head
  fullF1Ready = $true
  f0Head = $f0Head
  p0bCandidateHead = $p0bCandidateHead
  p0cAcceptanceWindowStart = $p0cAcceptanceWindowStart
  p0cAcceptanceWindowEnd = $f1Head
  owner = [string]$reviewRecord.owner
  reviewer = [string]$reviewRecord.reviewer
  reviewStatus = [string]$reviewRecord.reviewStatus
  reviewedHead = [string]$reviewRecord.reviewedHead
  reviewCompletedAtUtc = [string]$reviewRecord.reviewCompletedAtUtc
  migrations = $migrations
  sharedFiles = $sharedFiles
  sharedFileHandoffTarget = 'P2'
  sharedFileBaselineHead = $f1Head
  downstreamRebaseOwners = $downstreamRebaseOwners
  stableServices = $stableServices
  internalSpis = $internalSpis
  stableDomainAndDtos = $stableDomainAndDtos
  knowledgeImportRevisionMapping = $knowledgeImportRevisionMapping
  capturedAtUtc = [DateTime]::UtcNow.ToString('o')
}
$f1HandoffFields = @(
  'f1Head'
  'fullF1Ready'
  'f0Head'
  'p0bCandidateHead'
  'p0cAcceptanceWindowStart'
  'p0cAcceptanceWindowEnd'
  'owner'
  'reviewer'
  'reviewStatus'
  'reviewedHead'
  'reviewCompletedAtUtc'
  'migrations'
  'sharedFiles'
  'sharedFileHandoffTarget'
  'sharedFileBaselineHead'
  'downstreamRebaseOwners'
  'stableServices'
  'internalSpis'
  'stableDomainAndDtos'
  'knowledgeImportRevisionMapping'
  'capturedAtUtc'
)
if (Test-Path -LiteralPath $f1HandoffPath -PathType Leaf) {
  $verifiedF1Handoff = Get-Content -Raw -LiteralPath $f1HandoffPath |
    ConvertFrom-Json
  Assert-StrictString -Actual $verifiedF1Handoff.f1Head `
    -Expected $f1Head -Name '既有完整 F1 handoff f1Head'
} else {
  $handoffBytes = [Text.UTF8Encoding]::new($false).GetBytes(
    ($f1Handoff | ConvertTo-Json -Depth 6))
  $handoffStream = [IO.File]::Open(
    $f1HandoffPath,
    [IO.FileMode]::CreateNew,
    [IO.FileAccess]::Write,
    [IO.FileShare]::None)
  try {
    $handoffStream.Write($handoffBytes, 0, $handoffBytes.Length)
    $handoffStream.Flush($true)
  } finally {
    $handoffStream.Dispose()
  }
  $verifiedF1Handoff = Get-Content -Raw -LiteralPath $f1HandoffPath |
    ConvertFrom-Json
}
Assert-ExactFieldSet -Value $verifiedF1Handoff `
  -Expected $f1HandoffFields -Name '完整 F1 handoff'
$expectedHandoffStrings = [ordered]@{
  f1Head = $f1Head
  f0Head = $f0Head
  p0bCandidateHead = $p0bCandidateHead
  p0cAcceptanceWindowStart = $p0cAcceptanceWindowStart
  p0cAcceptanceWindowEnd = $f1Head
  owner = [string]$reviewRecord.owner
  reviewer = [string]$reviewRecord.reviewer
  reviewStatus = [string]$reviewRecord.reviewStatus
  reviewedHead = [string]$reviewRecord.reviewedHead
  reviewCompletedAtUtc = [string]$reviewRecord.reviewCompletedAtUtc
  sharedFileHandoffTarget = 'P2'
  sharedFileBaselineHead = $f1Head
}
foreach ($field in $expectedHandoffStrings.Keys) {
  Assert-StrictString -Actual $verifiedF1Handoff.$field `
    -Expected $expectedHandoffStrings[$field] `
    -Name "完整 F1 handoff $field"
}
Assert-StrictBoolean -Actual $verifiedF1Handoff.fullF1Ready `
  -Expected $true -Name '完整 F1 handoff fullF1Ready'
Assert-ExactStringArray -Expected $migrations `
  -Actual $verifiedF1Handoff.migrations -Name '完整 F1 handoff 迁移链'
Assert-ExactStringArray -Expected $sharedFiles `
  -Actual $verifiedF1Handoff.sharedFiles -Name '完整 F1 handoff 六共享文件'
Assert-ExactStringArray -Expected $stableServices `
  -Actual $verifiedF1Handoff.stableServices -Name '完整 F1 stable Services'
Assert-ExactStringArray -Expected $internalSpis `
  -Actual $verifiedF1Handoff.internalSpis -Name '完整 F1 internal SPIs'
Assert-ExactStringArray -Expected $stableDomainAndDtos `
  -Actual $verifiedF1Handoff.stableDomainAndDtos `
  -Name '完整 F1 stable domain/DTOs'
Assert-ExactFieldSet -Value $verifiedF1Handoff.downstreamRebaseOwners `
  -Expected @('P1', 'P2', 'P3') -Name '完整 F1 downstream rebase owners'
foreach ($stage in @('P1', 'P2', 'P3')) {
  Assert-StrictString `
    -Actual $verifiedF1Handoff.downstreamRebaseOwners.$stage `
    -Expected $downstreamRebaseOwners[$stage] `
    -Name "完整 F1 handoff 下游 rebase owner $stage"
}
Assert-ExactFieldSet `
  -Value $verifiedF1Handoff.knowledgeImportRevisionMapping `
  -Expected $revisionContractFields `
  -Name '完整 F1 knowledge import revision mapping'
foreach ($field in $revisionContractFields) {
  Assert-StrictString `
    -Actual $verifiedF1Handoff.knowledgeImportRevisionMapping.$field `
    -Expected $knowledgeImportRevisionMapping[$field] `
    -Name "完整 F1 knowledge import revision mapping $field"
}
if ($verifiedF1Handoff.capturedAtUtc -isnot [string] -or
    $verifiedF1Handoff.capturedAtUtc -cnotmatch '(?:Z|\+00:00)$') {
  throw '完整 F1 handoff capturedAtUtc 必须是显式 Z 或 +00:00 的 JSON string'
}
$capturedAtUtc = [DateTimeOffset]::Parse(
  $verifiedF1Handoff.capturedAtUtc)
if ($capturedAtUtc.Offset -ne [TimeSpan]::Zero) {
  throw '完整 F1 handoff capturedAtUtc 不是 UTC'
}
$dirtyAfterFreeze = @(git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0 -or $dirtyAfterFreeze.Count -ne 0) {
  $dirtyAfterFreeze
  throw 'F1 handoff 必须只写 Git metadata，不能污染当前 worktree'
}
"P0C_FULL_F1_HEAD=$f1Head"
"P0C_F1_HANDOFF_READBACK_OK=$f1HandoffPath"
```

预期：占位扫描退出 `0`，数值、提供商、归属和后台身份扫描以及
`git diff --check` 无输出。`AiTaskExecutionScannerIT` 明确覆盖成功 attempt 后的
`STALE_BRANCH_RESULT`、父子失败、额度 release/零 settle 和二次扫描零提供商重试。
两个 Resolver 分别只依赖 P0-B `AppAuthorizationActorResolver` 与
`SysAuthorizationActorResolver`；累计默认
`LoginHelper` 精确白名单仍只有 `AppIdentityAdminServiceImpl` 与
`SysAuthorizationActorResolver`。允许 SQL/实体/VO 持有归属字段，但用户写请求 BO
不得持有。纯内存对抗自测必须先输出 `P0C_HANDOFF_STRICT_SCHEMA_SELFTEST_OK`；全部
门禁通过后还必须输出 `P0C_FULL_F1_HEAD=<40 位 SHA>` 和
`P0C_F1_HANDOFF_READBACK_OK=<Git metadata 路径>`；独立 review、只读知识导入 revision
契约或幂等 handoff 回读任一字段不一致都不得冻结完整 F1，既有 handoff 不得覆盖。

- [ ] **步骤 5（2–5 分钟）：记录门禁证据并提交修正**

拉取请求记录：

```text
前端：草稿 StrictMode 幂等、加载/空/失败/403/分页、个人额度不切换其他账户、任务轮询停止条件、成本权限。
后端：方向版本、工作区冻结、draft→current branch→槽→账户→task/group member 全局锁序、根/执行计费形状、不可变流水恒等式、结算/释放竞态。
联调：个人与组织工作区各创建草稿但都绑定发起用户个人账户；同键重试；不同键抢同槽；额度不足；价格变化；取消释放；迟到回调。
```

如审查产生修正，必须回到产生问题的任务，重新执行该任务的红灯、绿灯和验证步骤，并使用该任务已经列出的精确 `git add` 清单提交；禁止在此处用项目根目录或大目录执行补漏提交。没有修正时不创建空提交。

## 任务 18：向前追加 F1 任务组守卫修订与独立证据

**最小任务卡：**

- **单一目标／不做：** 在不可变原 F1 之后追加 `04a`、两个 `IAiTaskService` 方法、任务组 membership、统一锁序与 addendum；不覆盖原 `p0c-f1-handoff.json`，不复制 task／usage／ledger／operation slot，不修改 P2／P3 文件。
- **风险／触发：** 红色；命中公共 Service、数据库结构、并发锁、额度聚合、下游 handoff 与恢复一致性。
- **权威来源：** 主计划冻结签名、`docs/ASYNC_TASKS.md`、`docs/DOMAIN_MODEL.md`、原 F1 handoff 及本任务 exact schema。
- **成功／反向验收：** `01 -> 02 -> 03 -> 04 -> 04a` 两次执行一致；活动文案任务阻断问卷写；branch 锁内重检消除 TOCTOU（检查时与使用时状态变化）；membership 完全重放幂等且 partial／superset／conflict／origin 失败关闭；费用按 `usageOperationId` 去重；原 handoff SHA 不变；addendum／三项 evidence／独立 review exact schema 与 live SHA 全通过。
- **所有权／数据范围：** 仅 P0-C task 聚合、`20260728_04a_p0c_task_group_guard.sql`、公共契约和当前 worktree Git metadata；六个已移交 P2 的共享文件保持只读。
- **依赖／人员／并发：** 依赖已完成 Task 17 的原 F1；原 P0-C writer 实施，未参与实施的独立 reviewer 复核；同一红色任务最多 2 人。
- **验证／检查点：** 规格／契约审查后执行并发／额度专项审查；修复后只复核本任务差异和关联测试。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`、`originalF1Head`、`amendmentHead`、`originalF1HandoffSha256`、addendum/review 路径。

**文件：**

- 创建 `docs/sql/ai-video/mysql/20260728_04a_p0c_task_group_guard.sql`。
- 修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java`、`service/impl/AiTaskServiceImpl.java`、`mapper/AiTaskMapper.java`／XML、`mapper/AiTaskGroupMemberMapper.java` 及必要的贫血 Entity 字段映射。
- 创建或修改 `AiTaskGenerationContextGuardTest.java`、`AiTaskGroupInheritanceIT.java`、`TaskGroupGuardMigrationIT.java`、`TaskGroupBillingAggregationTest.java`。
- 只读 `git rev-parse --git-path p0c-f1-handoff.json`；通过同一动态方式 CreateNew `p0c-f1-contract-addendum.json`、`p0c-f1-addendum/source-signatures.manifest.json`、`p0c-f1-addendum/migration-04a.manifest.json`、`p0c-f1-contract-addendum-review.json`，禁止假设 `.git` 或 worktree metadata 的物理位置。

- [ ] **步骤 1（5–10 分钟）：写迁移和 Service RED**

先写失败测试，至少覆盖：

```java
@Test void activeScriptGenerationBlocksQuestionnaireMutationWithExact46123Data() { }
@Test void commonBranchLockSerializesQuestionnaireWriteAndScriptCreation() { }
@Test void identicalInheritanceReplayIsIdempotent() { }
@Test void partialSupersetConflictAndOriginReplayFailClosed() { }
@Test void aggregationDeduplicatesEqualAmountsByUsageOperationIdNotAmount() { }
@Test void migrationCreatesSameTenantMembershipForeignKeyAndActiveTaskIndex() { }
```

RED 必须因 `04a`、两个方法或行为尚未实现而失败；编译失败、零测试或错误环境不算 RED。

- [ ] **步骤 2（20–40 分钟）：实现 `04a` 与统一锁序**

`20260728_04a_p0c_task_group_guard.sql` 必须以可重复执行的 `information_schema` 守卫完成：

```sql
CREATE TABLE IF NOT EXISTS av_ai_task_group_member (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    task_group_key VARCHAR(255) NOT NULL,
    root_task_id BIGINT NOT NULL,
    source_task_group_key VARCHAR(255) NULL,
    origin_type VARCHAR(16) NOT NULL,
    created_by_type VARCHAR(32) NOT NULL,
    created_by_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_av_ai_task_group_member
        (tenant_id, task_group_key, root_task_id),
    KEY idx_av_ai_task_group_member_root
        (tenant_id, root_task_id, task_group_key),
    CONSTRAINT ck_av_ai_task_group_member_origin
        CHECK (origin_type IN ('origin', 'inherited')),
    CONSTRAINT ck_av_ai_task_group_member_creator
        CHECK (created_by_type IN ('app_user', 'sys_user')),
    CONSTRAINT ck_av_ai_task_group_member_source CHECK (
        (origin_type = 'origin' AND source_task_group_key IS NULL)
        OR
        (origin_type = 'inherited' AND source_task_group_key IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

迁移还必须为 `av_ai_task` 增加支持同 tenant 外键的唯一键
`(tenant_id, id)`，从 member `(tenant_id, root_task_id)` 指向 task `(tenant_id, id)`，并新增
`idx_av_ai_task_active_group (tenant_id, task_group_key, task_role, status, task_type, id)`。
每个约束／索引都必须按名称检查后再创建，第二次执行不得报错或产生重复对象。

两个方法的最终源码签名必须与本计划前部逐字一致。`requireGenerationContextWritable` 只查询
`task_role=root`、`task_type IN ('script_generate','script_optimize')`、
`status IN ('pending','queued','running')`，并以稳定顺序选择第一条冲突记录。
`inheritQuestionnaireTaskGroupMembers` 内部构造 source/target 问卷组键，绝不接受调用方传任意 group key。
所有创建／变更路径固定按
`draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member` 加锁，并在 branch 锁内重检当前修订、冻结摘要与活动任务。

- [ ] **步骤 3（10–20 分钟）：跑 GREEN、迁移重放与额度专项门禁**

```powershell
$repoRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($repoRoot)) { throw '无法解析仓库根' }
Set-Location (Join-Path $repoRoot 'ai-video-api')
.\mvnw.cmd -pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true '-Dtest=AiTaskGenerationContextGuardTest,TaskGroupBillingAggregationTest' test
if ($LASTEXITCODE -ne 0) { throw 'P0-C 04a 单元测试失败' }
.\mvnw.cmd -pl :ai-video-core -am '-Pdev,local-integration-test' -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=AiTaskGroupInheritanceIT,TaskGroupGuardMigrationIT' verify
if ($LASTEXITCODE -ne 0) { throw 'P0-C 04a 集成测试失败' }
```

每份本次生成的 Surefire／Failsafe XML 必须 `tests > 0`、`failures=0`、`errors=0`、`skipped=0`。
SQL／Mapper／Service 扫描必须找到 `usageOperationId` 去重键并拒绝 `SUM(DISTINCT amount)`。

- [ ] **步骤 4（15–25 分钟）：生成不可变 F1 addendum 与独立 review**

原 `git-metadata/p0c-f1-handoff.json` 只能读取并实时计算 SHA-256；任何写入时间、长度或 SHA 变化立即失败。
`git-metadata/p0c-f1-contract-addendum.json` 的顶层键及顺序精确为：

```text
originalF1Head
amendmentHead
originalF1HandoffSha256
requiredMethods
schemaAddendum
owner
reviewer
reviewStatus
reviewedHead
reviewCompletedAtUtc
evidence
capturedAtUtc
```

`requiredMethods` 必须包含两个最终源码签名，不能用方法简称；`schemaAddendum` 精确为：

```json
{
  "forwardMigration": "20260728_04a_p0c_task_group_guard.sql",
  "taskGroupMemberTable": "av_ai_task_group_member",
  "activeTaskIndex": "idx_av_ai_task_active_group",
  "originValues": ["origin", "inherited"],
  "creatorTypes": ["app_user", "sys_user"],
  "globalLockOrder": ["draft", "current_branch", "operation_slot", "quota_account", "task_or_group_member"],
  "scriptGroupKey": "script:{draftId}:{branchRevision}",
  "inheritanceScope": "membership_only",
  "forbiddenCopies": ["task", "usage", "ledger", "operation_slot"]
}
```

`evidence` 是固定顺序三项，每项的键及顺序只能是 `kind`、`path`、`sha256`：

1. `source-signatures` → `git-metadata:p0c-f1-addendum/source-signatures.manifest.json`；
2. `migration-04a` → `git-metadata:p0c-f1-addendum/migration-04a.manifest.json`；
3. `independent-review` → `git-metadata:p0c-f1-contract-addendum-review.json`。

独立 reviewer 亲自创建 review 文件，键及顺序精确为
`owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`originalF1Head`、
`originalF1HandoffSha256`、`requiredMethodsSha256`、`schemaAddendumSha256`、
`reviewCompletedAtUtc`。`owner` 与 `reviewer` 经 trim 后忽略大小写仍必须不同；
`reviewStatus=PASS`；`reviewedHead=amendmentHead`；`originalF1Head` 必须是
`amendmentHead` 的祖先；所有 SHA 从当前 bytes 实时计算；时间显式 UTC，且
`capturedAtUtc >= reviewCompletedAtUtc`。缺字段、额外字段、错误顺序、错误类型、漂移 SHA、未来 review、
非祖先或原 handoff 被覆盖都必须 fail-closed。

先由独立 reviewer 设置 `AI_VIDEO_P0C_ADDENDUM_OWNER`、`AI_VIDEO_P0C_ADDENDUM_REVIEWER` 并运行下面脚本；它独立冻结 live source/migration manifests 和 exact 9-field review：

```powershell
$ErrorActionPreference='Stop'
$owner=[string]$env:AI_VIDEO_P0C_ADDENDUM_OWNER
$reviewer=[string]$env:AI_VIDEO_P0C_ADDENDUM_REVIEWER
if([string]::IsNullOrWhiteSpace($owner) -or [string]::IsNullOrWhiteSpace($reviewer) -or $owner.Trim().Equals($reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase)){throw 'owner/reviewer 必须非空且独立'}
$rootText=(& git rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$root=[IO.Path]::GetFullPath($rootText.Trim()); Set-Location -LiteralPath $root
function Meta([string]$name){$raw=(& git rev-parse --git-path $name).Trim();if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)){throw "无法解析 metadata：$name"};if([IO.Path]::IsPathRooted($raw)){return [IO.Path]::GetFullPath($raw)};return [IO.Path]::GetFullPath((Join-Path $root $raw))}
function Fields($value,[string[]]$expected,[string]$name){if($value -isnot [pscustomobject] -or (@($value.PSObject.Properties.Name)-join '|') -cne ($expected-join '|')){throw "$name 字段/顺序漂移"}}
function Sha([string]$value,[string]$name){if($value -cnotmatch '^[0-9a-f]{64}$'){throw "$name 非小写 SHA-256"}}
function Utc([string]$value,[string]$name){if($value -cnotmatch '(?:Z|\+00:00)$'){throw "$name 必须显式 UTC"};$parsed=[DateTimeOffset]::Parse($value,[Globalization.CultureInfo]::InvariantCulture);if($parsed.Offset -ne [TimeSpan]::Zero){throw "$name 非 UTC"};return $parsed}
function Json($value){return ($value | ConvertTo-Json -Depth 20 -Compress)}
function Reject([scriptblock]$case,[string]$name){$ok=$false;try{& $case | Out-Null}catch{$ok=$true};if(-not $ok){throw "负向自测未拒绝：$name"}}
function Freeze([string]$path,[System.Collections.IDictionary]$document,[string[]]$fields,[string[]]$core,[string]$timeField){
  if(Test-Path -LiteralPath $path -PathType Leaf){$actual=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $actual $fields $path;foreach($name in $core){if((Json $actual.$name) -cne (Json $document[$name])){throw "$path payload 漂移：$name"}};[void](Utc ([string]$actual.$timeField) "$path.$timeField");return $actual}
  $dir=Split-Path -Parent $path;if(-not(Test-Path -LiteralPath $dir -PathType Container)){[void](New-Item -ItemType Directory -Path $dir)}
  $bytes=[Text.UTF8Encoding]::new($false).GetBytes((Json $document));$stream=[IO.File]::Open($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()};return (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json)
}
Reject {Fields ([pscustomobject][ordered]@{b=1;a=2}) @('a','b') 'fixture'} '字段顺序'
Reject {Utc '2026-08-02T00:00:00' 'fixture'} '非 UTC'
Reject {Sha ('A'*64) 'fixture'} '大写 SHA'
'P0C_ADDENDUM_REVIEW_NEGATIVE_SELFTEST_OK'
$handoffPath=Meta 'p0c-f1-handoff.json';if(-not(Test-Path -LiteralPath $handoffPath -PathType Leaf)){throw '原 F1 handoff 缺失'}
$handoffBefore=Get-Item -LiteralPath $handoffPath;$handoffSha=(Get-FileHash -LiteralPath $handoffPath -Algorithm SHA256).Hash.ToLowerInvariant();$handoff=Get-Content -LiteralPath $handoffPath -Raw -Encoding UTF8 | ConvertFrom-Json
$originalHead=[string]$handoff.f1Head;$amendmentHead=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant();if($originalHead -cnotmatch '^[0-9a-f]{40}$' -or $amendmentHead -cnotmatch '^[0-9a-f]{40}$' -or $originalHead -ceq $amendmentHead){throw 'original/amendment HEAD 非法'}
& git merge-base --is-ancestor $originalHead $amendmentHead;if($LASTEXITCODE -ne 0){throw 'originalF1Head 不是 amendmentHead 祖先'}
$required=@('void requireGenerationContextWritable(Long draftId, Long branchRevision);','void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);')
$schema=[ordered]@{forwardMigration='20260728_04a_p0c_task_group_guard.sql';taskGroupMemberTable='av_ai_task_group_member';activeTaskIndex='idx_av_ai_task_active_group';originValues=@('origin','inherited');creatorTypes=@('app_user','sys_user');globalLockOrder=@('draft','current_branch','operation_slot','quota_account','task_or_group_member');scriptGroupKey='script:{draftId}:{branchRevision}';inheritanceScope='membership_only';forbiddenCopies=@('task','usage','ledger','operation_slot')}
$sourceRel='ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service/IAiTaskService.java';$source=Join-Path $root $sourceRel;if(-not(Test-Path -LiteralPath $source -PathType Leaf)){throw 'IAiTaskService source 缺失'}
$sourceText=[regex]::Replace((Get-Content -LiteralPath $source -Raw -Encoding UTF8),'\s+',' ');foreach($signature in $required){if(-not $sourceText.Contains($signature)){throw "live source 缺 exact signature：$signature"}}
$migrationRel='docs/sql/ai-video/mysql/20260728_04a_p0c_task_group_guard.sql';$migration=Join-Path $root $migrationRel;if(-not(Test-Path -LiteralPath $migration -PathType Leaf)){throw '04a migration 缺失'}
$tokens=@('av_ai_task_group_member','idx_av_ai_task_active_group','idx_av_ai_task_group_member_root','origin','inherited','app_user','sys_user');$migrationText=Get-Content -LiteralPath $migration -Raw -Encoding UTF8;foreach($token in $tokens){if(-not $migrationText.Contains($token)){throw "live migration 缺 token：$token"}}
$now=[DateTimeOffset]::UtcNow.ToString('o')
$sourceDoc=[ordered]@{kind='source-signatures';path=$sourceRel;sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant();requiredMethods=$required;amendmentHead=$amendmentHead;capturedAtUtc=$now}
$migrationDoc=[ordered]@{kind='migration-04a';path=$migrationRel;sha256=(Get-FileHash -LiteralPath $migration -Algorithm SHA256).Hash.ToLowerInvariant();requiredTokens=$tokens;amendmentHead=$amendmentHead;capturedAtUtc=$now}
$sourcePath=Meta 'p0c-f1-addendum/source-signatures.manifest.json';$migrationPath=Meta 'p0c-f1-addendum/migration-04a.manifest.json'
[void](Freeze $sourcePath $sourceDoc @('kind','path','sha256','requiredMethods','amendmentHead','capturedAtUtc') @('kind','path','sha256','requiredMethods','amendmentHead') 'capturedAtUtc')
[void](Freeze $migrationPath $migrationDoc @('kind','path','sha256','requiredTokens','amendmentHead','capturedAtUtc') @('kind','path','sha256','requiredTokens','amendmentHead') 'capturedAtUtc')
$requiredSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes((Json $required)))).ToLowerInvariant();$schemaSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes((Json $schema)))).ToLowerInvariant()
$reviewDoc=[ordered]@{owner=$owner.Trim();reviewer=$reviewer.Trim();reviewStatus='PASS';reviewedHead=$amendmentHead;originalF1Head=$originalHead;originalF1HandoffSha256=$handoffSha;requiredMethodsSha256=$requiredSha;schemaAddendumSha256=$schemaSha;reviewCompletedAtUtc=[DateTimeOffset]::UtcNow.ToString('o')}
$reviewPath=Meta 'p0c-f1-contract-addendum-review.json';$reviewFields=@('owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256','requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc');$verified=Freeze $reviewPath $reviewDoc $reviewFields @('owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256','requiredMethodsSha256','schemaAddendumSha256') 'reviewCompletedAtUtc';Fields $verified $reviewFields 'review';if($verified.owner.Trim().Equals($verified.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase)){throw 'reviewer 不独立'}
$handoffAfter=Get-Item -LiteralPath $handoffPath;if($handoffBefore.Length -ne $handoffAfter.Length -or $handoffBefore.LastWriteTimeUtc -ne $handoffAfter.LastWriteTimeUtc -or (Get-FileHash -LiteralPath $handoffPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $handoffSha){throw '原 F1 handoff 被改写'}
'P0C_F1_ADDENDUM_REVIEW_OK'
```

reviewer 完成后，原 P0-C writer 使用同一 owner 值运行下面脚本；它只 CreateNew／幂等回读 exact 12-field addendum，绝不创建或修改 review：

```powershell
$ErrorActionPreference='Stop'
$owner=[string]$env:AI_VIDEO_P0C_ADDENDUM_OWNER;if([string]::IsNullOrWhiteSpace($owner)){throw 'AI_VIDEO_P0C_ADDENDUM_OWNER 必填'}
$rootText=(& git rev-parse --show-toplevel 2>$null);if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'};$root=[IO.Path]::GetFullPath($rootText.Trim());Set-Location -LiteralPath $root
function Meta([string]$name){$raw=(& git rev-parse --git-path $name).Trim();if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)){throw "无法解析 metadata：$name"};if([IO.Path]::IsPathRooted($raw)){return [IO.Path]::GetFullPath($raw)};return [IO.Path]::GetFullPath((Join-Path $root $raw))}
function Fields($value,[string[]]$expected,[string]$name){if($value -isnot [pscustomobject] -or (@($value.PSObject.Properties.Name)-join '|') -cne ($expected-join '|')){throw "$name 字段/顺序漂移"}}
function Json($value){return ($value | ConvertTo-Json -Depth 20 -Compress)}
function Utc([string]$value,[string]$name){if($value -cnotmatch '(?:Z|\+00:00)$'){throw "$name 必须显式 UTC"};$parsed=[DateTimeOffset]::Parse($value,[Globalization.CultureInfo]::InvariantCulture);if($parsed.Offset -ne [TimeSpan]::Zero){throw "$name 非 UTC"};return $parsed}
function Reject([scriptblock]$case,[string]$name){$ok=$false;try{& $case | Out-Null}catch{$ok=$true};if(-not $ok){throw "负向自测未拒绝：$name"}}
$addendumFields=@('originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum','owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc');$reviewFields=@('owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256','requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc');$evidenceFields=@('kind','path','sha256')
Reject {Fields ([pscustomobject][ordered]@{amendmentHead='x';originalF1Head='y'}) @('originalF1Head','amendmentHead') 'fixture'} '12 字段顺序'
Reject {Fields ([pscustomobject][ordered]@{kind='x';sha256='y';path='z'}) $evidenceFields 'fixture'} '3 字段顺序'
Reject {Fields ([pscustomobject][ordered]@{owner='x'}) $reviewFields 'fixture'} '9 字段缺失'
'P0C_ADDENDUM_WRITER_NEGATIVE_SELFTEST_OK'
$handoffPath=Meta 'p0c-f1-handoff.json';$sourcePath=Meta 'p0c-f1-addendum/source-signatures.manifest.json';$migrationPath=Meta 'p0c-f1-addendum/migration-04a.manifest.json';$reviewPath=Meta 'p0c-f1-contract-addendum-review.json';foreach($path in @($handoffPath,$sourcePath,$migrationPath,$reviewPath)){if(-not(Test-Path -LiteralPath $path -PathType Leaf)){throw "依赖 evidence 缺失：$path"}}
$handoffBefore=Get-Item -LiteralPath $handoffPath;$handoffSha=(Get-FileHash -LiteralPath $handoffPath -Algorithm SHA256).Hash.ToLowerInvariant();$handoff=Get-Content -LiteralPath $handoffPath -Raw -Encoding UTF8 | ConvertFrom-Json;$originalHead=[string]$handoff.f1Head;$amendmentHead=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant();& git merge-base --is-ancestor $originalHead $amendmentHead;if($LASTEXITCODE -ne 0 -or $originalHead -ceq $amendmentHead){throw 'HEAD ancestry 非法'}
$required=@('void requireGenerationContextWritable(Long draftId, Long branchRevision);','void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);')
$schema=[ordered]@{forwardMigration='20260728_04a_p0c_task_group_guard.sql';taskGroupMemberTable='av_ai_task_group_member';activeTaskIndex='idx_av_ai_task_active_group';originValues=@('origin','inherited');creatorTypes=@('app_user','sys_user');globalLockOrder=@('draft','current_branch','operation_slot','quota_account','task_or_group_member');scriptGroupKey='script:{draftId}:{branchRevision}';inheritanceScope='membership_only';forbiddenCopies=@('task','usage','ledger','operation_slot')}
$sourceManifest=Get-Content -LiteralPath $sourcePath -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $sourceManifest @('kind','path','sha256','requiredMethods','amendmentHead','capturedAtUtc') 'source manifest';$sourceFile=Join-Path $root ([string]$sourceManifest.path);if((Get-FileHash -LiteralPath $sourceFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $sourceManifest.sha256 -or (Json $sourceManifest.requiredMethods) -cne (Json $required) -or $sourceManifest.amendmentHead -cne $amendmentHead){throw 'live source manifest 漂移'}
$sourceText=[regex]::Replace((Get-Content -LiteralPath $sourceFile -Raw -Encoding UTF8),'\s+',' ');foreach($signature in $required){if(-not $sourceText.Contains($signature)){throw "live source 缺 exact signature：$signature"}}
$migrationManifest=Get-Content -LiteralPath $migrationPath -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $migrationManifest @('kind','path','sha256','requiredTokens','amendmentHead','capturedAtUtc') 'migration manifest';$migrationFile=Join-Path $root ([string]$migrationManifest.path);if((Get-FileHash -LiteralPath $migrationFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $migrationManifest.sha256 -or $migrationManifest.amendmentHead -cne $amendmentHead){throw 'live migration manifest 漂移'};foreach($token in @($migrationManifest.requiredTokens)){if(-not (Get-Content -LiteralPath $migrationFile -Raw -Encoding UTF8).Contains([string]$token)){throw "live migration 缺 token：$token"}}
$review=Get-Content -LiteralPath $reviewPath -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $review $reviewFields 'review';$requiredSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes((Json $required)))).ToLowerInvariant();$schemaSha=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes((Json $schema)))).ToLowerInvariant();$reviewTime=Utc ([string]$review.reviewCompletedAtUtc) 'review.reviewCompletedAtUtc'
if($review.owner -cne $owner.Trim() -or $review.owner.Trim().Equals($review.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase) -or $review.reviewStatus -cne 'PASS' -or $review.reviewedHead -cne $amendmentHead -or $review.originalF1Head -cne $originalHead -or $review.originalF1HandoffSha256 -cne $handoffSha -or $review.requiredMethodsSha256 -cne $requiredSha -or $review.schemaAddendumSha256 -cne $schemaSha){throw 'independent review 与 live payload 不一致'}
$evidence=@([ordered]@{kind='source-signatures';path='git-metadata:p0c-f1-addendum/source-signatures.manifest.json';sha256=(Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()},[ordered]@{kind='migration-04a';path='git-metadata:p0c-f1-addendum/migration-04a.manifest.json';sha256=(Get-FileHash -LiteralPath $migrationPath -Algorithm SHA256).Hash.ToLowerInvariant()},[ordered]@{kind='independent-review';path='git-metadata:p0c-f1-contract-addendum-review.json';sha256=(Get-FileHash -LiteralPath $reviewPath -Algorithm SHA256).Hash.ToLowerInvariant()});foreach($item in $evidence){Fields ([pscustomobject]$item) $evidenceFields 'evidence item'}
$document=[ordered]@{originalF1Head=$originalHead;amendmentHead=$amendmentHead;originalF1HandoffSha256=$handoffSha;requiredMethods=$required;schemaAddendum=$schema;owner=[string]$review.owner;reviewer=[string]$review.reviewer;reviewStatus='PASS';reviewedHead=$amendmentHead;reviewCompletedAtUtc=[string]$review.reviewCompletedAtUtc;evidence=$evidence;capturedAtUtc=[DateTimeOffset]::UtcNow.ToString('o')};if((Utc $document.capturedAtUtc 'capturedAtUtc') -lt $reviewTime){throw 'capturedAtUtc 早于 review'}
$addendumPath=Meta 'p0c-f1-contract-addendum.json'
if(Test-Path -LiteralPath $addendumPath -PathType Leaf){$actual=Get-Content -LiteralPath $addendumPath -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $actual $addendumFields 'addendum';foreach($name in $addendumFields | Where-Object{$_ -cne 'capturedAtUtc'}){if((Json $actual.$name) -cne (Json $document[$name])){throw "既有 addendum payload 漂移：$name"}};if((Utc ([string]$actual.capturedAtUtc) 'addendum.capturedAtUtc') -lt $reviewTime){throw '既有 capturedAtUtc 早于 review'}}else{$dir=Split-Path -Parent $addendumPath;if(-not(Test-Path -LiteralPath $dir -PathType Container)){[void](New-Item -ItemType Directory -Path $dir)};$bytes=[Text.UTF8Encoding]::new($false).GetBytes((Json $document));$stream=[IO.File]::Open($addendumPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None);try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}}
$verified=Get-Content -LiteralPath $addendumPath -Raw -Encoding UTF8 | ConvertFrom-Json;Fields $verified $addendumFields 'addendum readback';for($i=0;$i -lt 3;$i++){Fields $verified.evidence[$i] $evidenceFields "evidence[$i]";if((Json $verified.evidence[$i]) -cne (Json ([pscustomobject]$evidence[$i]))){throw "evidence[$i] 漂移"}}
$handoffAfter=Get-Item -LiteralPath $handoffPath;if($handoffBefore.Length -ne $handoffAfter.Length -or $handoffBefore.LastWriteTimeUtc -ne $handoffAfter.LastWriteTimeUtc -or (Get-FileHash -LiteralPath $handoffPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $handoffSha){throw '原 F1 handoff 被改写'}
'P0C_F1_ADDENDUM_WRITER_OK'
```

- [ ] **步骤 5（5–10 分钟）：只提交 04a 实现并移交下游**

提交前运行相关模块完整测试、`scripts/validate-development-standards.ps1`、`git diff --check` 和精确 diff scope。
提交必须是原 F1 的后继；addendum 通过后，P1／P2／P3 分别 rebase 到同一 `amendmentHead`，同时消费
`originalF1HandoffSha256` 与 addendum SHA。不得重写、amend 或 force-push 原 F1。

## P0-C 完成定义

- 一个有效 published 方向目录可由用户读取，运营端可复制、编辑、发布和追溯版本。
- 创建草稿只接收幂等键，从 P0-B 当前工作区冻结资源作用域和计费主体，并创建 `0/1/0` 初始修订与空首分支。
- 草稿总览始终返回七个服务端步骤守卫；不存在或无权限不会被自动新草稿掩盖。
- 个人账户按用户独立；查询和任务链路不创建零账户，账户缺失或余额不足时不回退、不拼接、不透支其他用户或主体账户。
- 五个价格项版本化；价格变化返回 `46115`，没有生产固定价格种子。
- 每次额度事件写不可变明细，金额、增量、前后值和事件键满足第 14 节恒等式。
- 同一计费操作的锁定、结算和释放幂等；结算与释放并发只有一个终态。
- 所有写链固定按 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member` 加锁并在共同 branch 锁内重检；同槽不同幂等键返回 `46123`，最多一条根任务和一笔锁定。
- 收费根任务与计费操作一对一；执行任务和自动重试不创建第二个计费操作。
- `requireGenerationContextWritable` 在活动 `script_generate|script_optimize` 根任务处于 `pending|queued|running` 时以 `46123` 阻断问卷上下文写，响应 data 仅含 `rootTaskId`、`taskType`、`status`。
- `inheritQuestionnaireTaskGroupMembers` 只继承 membership；同租户、工作区 app actor、连续分支修订、问题根任务、资源、任务族与 source membership 全部校验，完整重放幂等，partial／superset／conflict／origin 失败关闭。
- 跨 origin／inherited 任务组费用按 `usageOperationId` 去重，禁止 `SUM(DISTINCT amount)`；相同金额的不同计费操作仍分别计入。
- `knowledge_import` 免费根任务通过独立幂等入口创建，计费操作编号为空且不产生额度流水。
- 用户/运营 starter 分别只装配 app/sys Resolver；它们分别只消费 P0-B
  `AppAuthorizationActorResolver`／`SysAuthorizationActorResolver`，
  `verifyNoInteractions` 与依赖扫描共同证明不查询、不尝试、不回退另一身份域。
- 用户任务/额度查询执行 P0-B 工作区和对象范围；运营成本只由独立权限和独立接口返回。
- 用户端具备统一 RuoYi 适配、React Query 根容器、草稿启动、真实额度摘要和任务中心基础。
- 运营端具备方向、额度、价格、账单、任务和成本权限基础管理页，覆盖加载、空、失败、403 和分页。
- `ModelProvider` 与 `WebSearchClient` 已定义但本包没有真实外部调用。
- 成功提供商 attempt 后若结果事务发现冻结分支/上下文已过期，固定以大写
  `STALE_BRANCH_RESULT` 非重试终结父子任务；attempt 和真实成本保留，业务结果不
  落库，收费操作只 release（释放）一次且不 settle（结算），再次扫描不调用提供商。
- Maven 单元与集成测试均被显式启用；原 F1 的六个精确 Surefire 类与 11 个精确 Failsafe 类，以及 Task 18 新增的两个单元类与两个集成类，均为本次 `tests > 0`、失败/错误/跳过全零。
- Task 17 在干净 P0-C worktree 上只读校验由契约 owner 和独立 reviewer 预先签署的
  `p0c-knowledge-import-revision-contract.json`，确认九个 JSON string、`CONFIRMED`、
  独立 `PASS`、当前 F1 HEAD、显式 UTC 时间和三个 revision 来源；handoff writer
  不得代写该记录；同一验证函数的纯内存对抗自测覆盖九字段逐一 number／array、
  无时区时间、字符串布尔、标量伪数组、数字数组项和字段名大小写漂移，并输出固定
  `P0C_HANDOFF_STRICT_SCHEMA_SELFTEST_OK`。
- Task 17 输出原始 F1 的 40 位 SHA，并把不可变 `p0c-f1-handoff.json` 写入当前 worktree
  的 Git metadata；回读确认 F1、P0-B candidate、F0→P0-B→F1、acceptance window、
  独立 reviewer PASS、原始 F1 历史链 `02 -> 03 -> 04`、六共享文件、`sharedFileHandoffTarget=P2`、
  `sharedFileBaselineHead=F1`、P1／P2／P3 rebase owner、精确 stable Services／internal
  SPI／stable domain and DTO 数组，以及完整 `knowledgeImportRevisionMapping` 全部一致；
  同一 F1 且内容完全一致时只回读，缺字段、额外字段、不同内容或不同 F1 立即失败并
  绝不覆盖。
- Task 18 在原始 F1 之后以 `04a` 完成当前完整链 `01 -> 02 -> 03 -> 04 -> 04a -> 05 -> 06 -> 07`，并生成 exact 12-field addendum、固定顺序三项 evidence 和 exact 9-field 独立 review；原 handoff SHA 必须不变，`originalF1Head` 必须是 `amendmentHead` 祖先，所有 SHA 为 live bytes，reviewer 独立且时间为 UTC。

## 自检结果

- 规格覆盖：P0-C 的方向目录、草稿入口、统一任务、任务中心接口、额度账户、价格版本、每次调用详细账单、幂等/操作槽、任务组 membership／并发写守卫、不可变 F1 addendum、用户请求适配和运营基础管理均有任务。
- 范围隔离：动态问卷、知识路由、证据抓取、三候选、文案版本、通知和真实提供商调用未进入本包。
- 类型一致：固定服务和端口、任务/账单状态、五个价格项、数字错误码、稳定大写失败码
  `STALE_BRANCH_RESULT`、Java `Long`/`BigDecimal` 与 HTTP/TypeScript `string`
  在全文一致。
- 可执行性：每个实现任务都有失败测试、失败命令、最小实现、通过命令和提交；最终 Maven 命令显式设置 `maven.test.skip`、`skipTests`、`skipITs`。
