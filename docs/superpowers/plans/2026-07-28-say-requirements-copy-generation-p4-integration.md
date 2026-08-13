# P4 集成、资料迁移、灰度与上线验收实现计划

> **2026-08-01 RuoYi 分层硬约束：** 本计划正文中出现的 `application`、`port`、`command`、`model` 等非 RuoYi 路径均已被项目硬约束覆盖，不能直接执行。集成业务聚合只能使用 `domain`、与其平级的 `dto`、`mapper`、`service/I...Service`、`service.impl/...ServiceImpl`；端侧 HTTP 模块另使用 `domain.bo`、`domain.vo`、`controller`。AI 视频业务专属的稳定跨模块 DTO 归 `ai-video-core` 对应聚合的 `dto` 包，不得迁入全局 `ruoyi-api`；直接技术集成归 `ai-video-infra`。禁止以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 贫血 Entity（实体）加 Service（业务服务）编排；先完成路径整改计划并获项目负责人确认，才能实施。

> **面向 AI（人工智能）代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（子代理驱动开发，推荐）或 superpowers:executing-plans（分批执行计划）逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把账号安全、工作区、任务额度、系统知识、逐题问卷和三套文案连接为可迁移、可观测、可灰度、可回退的完整生产流程。

**架构：** 任务终态事务只写数据库 Outbox（事务发件箱）事件，异步投递器再幂等创建用户通知；P0-C 已交付的用户任务中心、运营任务页和计费操作页在本阶段只做跨域回归，不重复建设。对外部 `fenjing` 资料只读生成清单并经人工审核导入。功能开关只控制新入口和新任务创建，历史任务始终按冻结快照完成；上线前由 Playwright（浏览器自动化测试）覆盖两个独立登录域和完整业务链。

**技术栈：** Java 21（后端编程语言）、Spring Boot 4.1.0（Java 应用框架）、MyBatis-Plus（数据访问增强工具）、SnailJob（分布式任务调度）、Micrometer（应用指标）、React 19（前端视图库）、Ant Design 6（蚂蚁设计组件库）、ProComponents 3（中后台高级组件库）、Vitest（前端单元测试）、Playwright（浏览器端到端测试）、PowerShell（自动化脚本）、MySQL（关系型数据库）、Redis（缓存数据库）。

**阅读约定：** 正文中的 API（应用编程接口）、SQL（结构化查询语言）、DDL（数据定义语言）、IT（集成测试）、JSON（结构化数据格式）、ZIP（压缩包）、Outbox（事务发件箱）、OSS（对象存储服务）等英文术语首次出现时附中文含义；反引号中的类名、字段名、文件名、命令和接口路径是必须原样使用的程序标识符，不翻译其拼写，但由相邻中文解释其用途。

**本机 IT 命令约定：** 全部 `*IT`（集成测试）通过 `'-Plocal-integration-test'` 执行，并复用 P0-A 的 `LocalIntegrationEnvironment`（本机受控集成环境夹具）。夹具默认读取用户端 `application-dev.yml` 的标准数据源和 Redis 配置，固定派生本机 `ai_video_test`、Redis 独立逻辑库和当前 `aivideo:it:<runId>:` 前缀，`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖；它在任何迁移、夹具写入或启动子进程前失败关闭。P4 可以启动三个 Java（编程语言）测试子进程与进程内 WireMock（HTTP 模拟服务），但绝不启动 Docker、容器、WSL、虚拟机或其他虚拟化服务。

---

## 前置门禁

- `P0-A` 至 `P3` 的最终提交、数据库脚本、接口契约和测试证据全部可追溯。
- 创作端和运营端令牌仍通过双向隔离测试；P4 不允许为端到端测试增加通用免登录白名单。
- 任务、额度、知识快照、文案版本和用户文案库均已使用真实数据库事实源。
- 模型与 Web Search（网页检索）提供商的共享开发凭据直接写入并提交在两端 `application-dev.yml`，环境变量可选覆盖；测试使用本地模拟服务，不消耗生产额度。
- 外部资料根目录 `D:\Workspace\ai\projects\文案\fenjing` 仅作为只读输入；脚本不得改名、移动、删除或改写其中任何文件。
- P0-A 的 Surefire（单元测试插件）与 Failsafe（集成测试插件）都按 `${profiles.active}` 分组执行；本计划创建或修改的每个 JUnit 测试类（包括 `*Test` 与 `*IT`）都必须在类级标注 `@Tag("dev")`，最终门禁分别确认目标单元测试报告和集成测试报告的测试数大于 0。

## 文件结构

### 契约、数据库和运行手册

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`ai-video-ui/ai-video-webapp/PRD.md`
- 创建：`docs/sql/ai-video/mysql/20260728_08_p4_integration.sql`
- 创建：`docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql`
- 创建：`docs/sql/ai-video/mysql/README.md`
- 创建：`docs/runbooks/say-requirements-deployment.md`
- 创建：`docs/runbooks/say-requirements-rollback.md`
- 创建：`docs/runbooks/say-requirements-observability.md`
- 创建：`docs/runbooks/say-requirements-acceptance.md`

### 通知与可靠发件

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/UserNotification.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/OutboxEvent.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/bo/NotificationQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/vo/UserNotificationVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/UserNotificationMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/OutboxEventMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/UserNotificationMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/OutboxEventMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskTerminalNotificationCommand.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskNotificationPolicy.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/NotificationOutboxService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/NotificationOutboxServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxClaimService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxRetryService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryException.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailure.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailureClassifier.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxClaimServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxDeliveryServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxRetryServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/UserNotificationService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/UserNotificationServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxWorkerId.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/controller/NotificationController.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/notification/TaskNotificationPolicyTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/NotificationControllerTest.java`

### 功能开关、通知入口与既有聚合页验收

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureProperties.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/feature/SayRequirementsFeatureServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/observability/AiVideoMetrics.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/observability/MicrometerAiVideoMetrics.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/observability/MicrometerAiVideoMetricsTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/application/impl/ScriptDraftServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptGenerationServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptVersionServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/application/impl/QuotaBillingServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/application/impl/KnowledgeRoutingServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/ScriptGenerationBillingIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptVersionServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeRoutingServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-prod.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-prod.yml`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/components/RightContent/index.tsx`
- 验证：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx`
- 验证：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx`
- 验证：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx`

### 资料清单与导入验收

- 创建：`scripts/knowledge/FenjingManifest.Common.ps1`
- 创建：`scripts/knowledge/build-fenjing-manifest.ps1`
- 创建：`scripts/knowledge/validate-fenjing-manifest.ps1`
- 创建：`scripts/knowledge/package-fenjing-import.ps1`
- 创建：`ai-video-api/script/data/knowledge/fenjing/manifest.json`
- 创建：`ai-video-api/script/data/knowledge/fenjing/manifest.sha256`
- 创建：`ai-video-api/script/data/knowledge/fenjing/offline-acceptance-samples.json`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/integration/FenjingKnowledgeRoutingIT.java`
- 创建：`docs/runbooks/fenjing-knowledge-import.md`

### 浏览器端到端测试

- 修改：`ai-video-ui/ai-video-webapp/package.json`
- 修改：`ai-video-ui/ai-video-webapp/package-lock.json`
- 创建：`ai-video-ui/ai-video-webapp/playwright.config.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/global-setup.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/support.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/auth-isolation.spec.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/say-requirements.spec.ts`
- 修改：`ai-video-ui/ai-video-platform-ui/package.json`
- 修改：`ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml`
- 创建：`ai-video-ui/ai-video-platform-ui/playwright.config.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/global-setup.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/support.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/app-identity-admin.spec.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/knowledge-and-billing.spec.ts`

### 集成测试

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/NotificationOutboxIT.java`
- 修改：`ai-video-api/pom.xml`
- 创建：`ai-video-api/ai-video-integration-tests/pom.xml`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/resources/sql/integration-fixtures.sql`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/OrderedSqlBootstrap.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/IntegrationFixtureIds.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/DualApplicationHarness.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashFailpoint.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashStage.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/NoopAiVideoCrashFailpoint.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/ItProcessCrashFailpoint.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/infra/SnailJobAiTaskExecutionScanner.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/script/task/ScriptGenerateTaskHandler.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsHappyPathIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsRecoveryIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/CrossApplicationIsolationIT.java`

### 任务 1：冻结最终契约、SQL（结构化查询语言）顺序和通知权限

**文件：**

- 修改：`docs/API_CONTRACT.md`
- 修改：`docs/DOMAIN_MODEL.md`
- 修改：`docs/ASYNC_TASKS.md`
- 修改：`docs/ARCHITECTURE.md`
- 修改：`ai-video-ui/ai-video-webapp/PRD.md`
- 创建：`docs/sql/ai-video/mysql/20260728_08_p4_integration.sql`
- 创建：`docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql`
- 创建：`docs/sql/ai-video/mysql/README.md`

- [ ] **步骤 1（2–5 分钟）：编写 SQL 顺序验证清单**

`README.md` 固定列出：

```text
业务库迁移：
01 P0-A 账号与安全
02 P0-B 工作区与授权
03 P0-C 任务、额度与方向
04 P0 种子数据
05 P1 系统知识
06 P2 逐题问卷
07 P3 文案版本
08 P4 通知、可靠发件与功能开关

SnailJob 调度库：
S0 docs/sql/ry_job.sql
   仅用于全新调度库建立基线，已有调度库升级时禁止重跑
S1 docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql
   必须在 S0 已建立 sj_namespace、sj_group_config、sj_job 后独立执行
```

每项记录文件 SHA-256（安全摘要）、目标数据源、执行账号所需最小权限、验证查询和失败后的停止条件。01～08 只进入业务库迁移链；S1 只进入 SnailJob 调度库升级链，不能编号成 09，也不能为了登记或升级任务而重跑整份 `ry_job.sql`。

- [ ] **步骤 2（2–5 分钟）：创建独立、幂等且可升级的生产调度任务登记脚本**

`20260728_01_say_requirements_jobs.sql` 必须由部署工具连接 SnailJob 数据源执行。它不创建任何 `sj_*` 表、不创建命名空间或组、不写固定主键；只在调用方选择的既有命名空间和启用组内登记下列两个生产任务：

| 稳定 `biz_id` | `executor_info`（执行器名称） | 用途 |
|---|---|---|
| `ai-video-task-execution-scanner` | `aiVideoTaskExecutionScanner` | 领取并分发业务执行任务 |
| `ai-video-outbox-delivery` | `aiVideoOutboxDeliveryJob` | 领取并投递 Outbox 事件 |

脚本使用会话变量 `@aivideo_job_namespace`、`@aivideo_job_group`、`@aivideo_job_status`；未赋值时分别取 `prod`、`ruoyi_group`、`0`。默认关闭任务，保证可以先登记、再部署执行器，部署验证完成后由同一脚本把状态升级为 `1`。准确实现骨架如下：

```sql
SET NAMES utf8mb4;
SET @aivideo_job_namespace :=
  COALESCE(NULLIF(@aivideo_job_namespace, ''), 'prod');
SET @aivideo_job_group :=
  COALESCE(NULLIF(@aivideo_job_group, ''), 'ruoyi_group');
SET @aivideo_job_status := COALESCE(@aivideo_job_status, 0);

DROP PROCEDURE IF EXISTS upgrade_say_requirements_jobs;
DELIMITER $$
CREATE PROCEDURE upgrade_say_requirements_jobs()
BEGIN
  DECLARE namespace_count INT DEFAULT 0;
  DECLARE group_count INT DEFAULT 0;
  DECLARE ownership_conflict_count INT DEFAULT 0;
  DECLARE executor_conflict_count INT DEFAULT 0;
  DECLARE exact_count INT DEFAULT 0;
  DECLARE migration_lock_name VARCHAR(64);
  DECLARE migration_lock_acquired INT DEFAULT 0;

  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    IF migration_lock_acquired = 1 THEN
      DO RELEASE_LOCK(migration_lock_name);
    END IF;
    RESIGNAL;
  END;

  SET migration_lock_name = CONCAT(
    'aivideo:',
    LEFT(
      SHA2(
        CONCAT(@aivideo_job_namespace, ':', @aivideo_job_group),
        256
      ),
      56
    )
  );
  SELECT GET_LOCK(migration_lock_name, 10)
    INTO migration_lock_acquired;
  IF COALESCE(migration_lock_acquired, 0) <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_UPGRADE_LOCK_TIMEOUT';
  END IF;

  START TRANSACTION;

  IF CAST(@aivideo_job_status AS CHAR) NOT IN ('0', '1') THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_INVALID_JOB_STATUS';
  END IF;
  SET @aivideo_job_status := CAST(@aivideo_job_status AS UNSIGNED);

  SELECT COUNT(*) INTO namespace_count
  FROM sj_namespace
  WHERE unique_id = @aivideo_job_namespace
    AND deleted = 0;
  IF namespace_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_NAMESPACE_NOT_READY';
  END IF;

  SELECT COUNT(*) INTO group_count
  FROM sj_group_config
  WHERE namespace_id = @aivideo_job_namespace
    AND group_name = @aivideo_job_group
    AND group_status = 1;
  IF group_count <> 1 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_GROUP_NOT_READY';
  END IF;

  SELECT COUNT(*) INTO ownership_conflict_count
  FROM sj_job
  WHERE namespace_id = @aivideo_job_namespace
    AND biz_id IN (
      'ai-video-task-execution-scanner',
      'ai-video-outbox-delivery'
    )
    AND (
      deleted <> 0
      OR group_name <> @aivideo_job_group
      OR COALESCE(labels, '') <> 'managed-by:ai-video'
    );
  IF ownership_conflict_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_BIZ_ID_OWNERSHIP_CONFLICT';
  END IF;

  SELECT COUNT(*) INTO executor_conflict_count
  FROM sj_job
  WHERE namespace_id = @aivideo_job_namespace
    AND deleted = 0
    AND executor_info IN (
      'aiVideoTaskExecutionScanner',
      'aiVideoOutboxDeliveryJob'
    )
    AND biz_id NOT IN (
      'ai-video-task-execution-scanner',
      'ai-video-outbox-delivery'
    );
  IF executor_conflict_count <> 0 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_EXECUTOR_ALREADY_BOUND';
  END IF;

  INSERT INTO sj_job (
    namespace_id, biz_id, group_name, job_name, args_str, args_type,
    next_trigger_at, job_status, task_type, route_key, executor_type,
    executor_info, trigger_type, trigger_interval, block_strategy,
    executor_timeout, max_retry_times, parallel_num, retry_interval,
    bucket_index, resident, notify_ids, owner_id, labels, description,
    ext_attrs, deleted, create_dt, update_dt
  ) VALUES
    (
      @aivideo_job_namespace, 'ai-video-task-execution-scanner',
      @aivideo_job_group, 'AI 视频业务任务扫描', NULL, 1,
      CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED),
      @aivideo_job_status, 1, 4, 1, 'aiVideoTaskExecutionScanner',
      2, '1', 1, 0, 0, 1, 0, 0, 0, '', NULL,
      'managed-by:ai-video', '每秒领取可执行的 AI 视频业务任务',
      'registration=20260728_01', 0, NOW(), NOW()
    ),
    (
      @aivideo_job_namespace, 'ai-video-outbox-delivery',
      @aivideo_job_group, 'AI 视频 Outbox 投递', NULL, 1,
      CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED),
      @aivideo_job_status, 1, 4, 1, 'aiVideoOutboxDeliveryJob',
      2, '1', 1, 0, 0, 1, 0, 1, 0, '', NULL,
      'managed-by:ai-video', '每秒投递 AI 视频 Outbox 事件',
      'registration=20260728_01', 0, NOW(), NOW()
    )
  ON DUPLICATE KEY UPDATE id = sj_job.id;

  UPDATE sj_job
  SET group_name = @aivideo_job_group,
      job_name = CASE biz_id
        WHEN 'ai-video-task-execution-scanner' THEN 'AI 视频业务任务扫描'
        ELSE 'AI 视频 Outbox 投递'
      END,
      args_str = NULL,
      args_type = 1,
      next_trigger_at =
        CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED),
      job_status = @aivideo_job_status,
      task_type = 1,
      route_key = 4,
      executor_type = 1,
      executor_info = CASE biz_id
        WHEN 'ai-video-task-execution-scanner'
          THEN 'aiVideoTaskExecutionScanner'
        ELSE 'aiVideoOutboxDeliveryJob'
      END,
      trigger_type = 2,
      trigger_interval = '1',
      block_strategy = 1,
      executor_timeout = 0,
      max_retry_times = 0,
      parallel_num = 1,
      retry_interval = 0,
      bucket_index = CASE biz_id
        WHEN 'ai-video-task-execution-scanner' THEN 0 ELSE 1
      END,
      resident = 0,
      description = CASE biz_id
        WHEN 'ai-video-task-execution-scanner'
          THEN '每秒领取可执行的 AI 视频业务任务'
        ELSE '每秒投递 AI 视频 Outbox 事件'
      END,
      ext_attrs = 'registration=20260728_01',
      update_dt = NOW()
  WHERE namespace_id = @aivideo_job_namespace
    AND group_name = @aivideo_job_group
    AND labels = 'managed-by:ai-video'
    AND deleted = 0
    AND biz_id IN (
      'ai-video-task-execution-scanner',
      'ai-video-outbox-delivery'
    );

  SELECT COUNT(*) INTO exact_count
  FROM sj_job
  WHERE namespace_id = @aivideo_job_namespace
    AND group_name = @aivideo_job_group
    AND job_status = @aivideo_job_status
    AND args_str IS NULL
    AND args_type = 1
    AND task_type = 1
    AND route_key = 4
    AND executor_type = 1
    AND trigger_type = 2
    AND trigger_interval = '1'
    AND block_strategy = 1
    AND executor_timeout = 0
    AND max_retry_times = 0
    AND parallel_num = 1
    AND retry_interval = 0
    AND resident = 0
    AND labels = 'managed-by:ai-video'
    AND ext_attrs = 'registration=20260728_01'
    AND deleted = 0
    AND (
      (
        biz_id = 'ai-video-task-execution-scanner'
        AND executor_info = 'aiVideoTaskExecutionScanner'
        AND bucket_index = 0
        AND job_name = 'AI 视频业务任务扫描'
        AND description = '每秒领取可执行的 AI 视频业务任务'
      )
      OR (
        biz_id = 'ai-video-outbox-delivery'
        AND executor_info = 'aiVideoOutboxDeliveryJob'
        AND bucket_index = 1
        AND job_name = 'AI 视频 Outbox 投递'
        AND description = '每秒投递 AI 视频 Outbox 事件'
      )
    );
  IF exact_count <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'AIVIDEO_SJ_REGISTRATION_NOT_EXACT';
  END IF;

  COMMIT;
  DO RELEASE_LOCK(migration_lock_name);
  SET migration_lock_acquired = 0;
END$$
DELIMITER ;
CALL upgrade_say_requirements_jobs();
DROP PROCEDURE upgrade_say_requirements_jobs;
```

`GET_LOCK` 的锁名按命名空间和组确定，避免两次 S1 并发升级同一组；10 秒内无法取得锁即停止。过程取得锁后才开启事务，`INSERT`、受管 `UPDATE` 与最终精确断言全部处于同一事务；任何 SQL 异常或 `SIGNAL` 都由 `EXIT HANDLER` 执行 `ROLLBACK`、释放命名锁并 `RESIGNAL`，成功则先 `COMMIT` 再释放锁。因此失败后只能保留升级前状态，初次登记时受管行数必须为 0，成功后必须为 2，禁止出现 1 条或半升级配置。

`ON DUPLICATE KEY UPDATE` 只做主键自赋值，真正升级只允许命中 `labels='managed-by:ai-video'` 的受管行，避免并发或历史冲突时接管别人的任务。已有同 `biz_id` 但不是本脚本所有、已有其他 `biz_id` 绑定同执行器、目标命名空间不存在、组未启用或状态值非法都必须 `SIGNAL` 失败并由人工审查；不得用删除再插入、固定 `id` 或 `INSERT IGNORE` 掩盖冲突。调度层不重试且不设置执行超时，业务任务租约与 Outbox 租约才是事实源恢复机制；`block_strategy=1` 防止同一调度任务重入。

- [ ] **步骤 3（2–5 分钟）：创建通知表、发件表并验证既有通知权限**

```sql
CREATE TABLE IF NOT EXISTS av_user_notification (
  notification_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  recipient_user_id BIGINT NOT NULL,
  root_task_id BIGINT NOT NULL,
  terminal_status VARCHAR(16) NOT NULL,
  title VARCHAR(128) NOT NULL,
  summary VARCHAR(500) NOT NULL,
  read_status VARCHAR(16) NOT NULL,
  result_ref_type VARCHAR(32) NULL,
  result_ref_id BIGINT NULL,
  read_time DATETIME NULL,
  created_by_type VARCHAR(16) NOT NULL,
  created_by_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  updated_by_type VARCHAR(16) NOT NULL,
  updated_by_id BIGINT NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (notification_id),
  UNIQUE KEY uk_notification_terminal_recipient (
    tenant_id, root_task_id, terminal_status, recipient_user_id
  ),
  KEY idx_notification_recipient_read (
    tenant_id, recipient_user_id, read_status, create_time
  ),
  CONSTRAINT ck_notification_terminal_status
    CHECK (terminal_status IN ('success', 'failed', 'cancelled')),
  CONSTRAINT ck_notification_read_status
    CHECK (read_status IN ('unread', 'read')),
  CONSTRAINT ck_notification_created_by_type
    CHECK (created_by_type IN ('app_user', 'sys_user')),
  CONSTRAINT ck_notification_updated_by_type
    CHECK (updated_by_type IN ('app_user', 'sys_user'))
) ENGINE=InnoDB COMMENT='创作端任务通知';

CREATE TABLE IF NOT EXISTS av_outbox_event (
  event_id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  payload_schema_version INT NOT NULL,
  aggregate_type VARCHAR(32) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  terminal_status VARCHAR(16) NOT NULL,
  recipient_user_id BIGINT NOT NULL,
  payload_json JSON NOT NULL,
  payload_hash CHAR(64) NOT NULL,
  dedup_key VARCHAR(190) NOT NULL,
  delivery_status VARCHAR(16) NOT NULL DEFAULT 'pending',
  retry_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL DEFAULT 10,
  next_attempt_time DATETIME NULL,
  locked_by VARCHAR(64) NULL,
  lock_token CHAR(36) NULL,
  locked_at DATETIME NULL,
  delivered_at DATETIME NULL,
  last_error_code VARCHAR(64) NULL,
  last_error_summary VARCHAR(500) NULL,
  occurred_at DATETIME NOT NULL,
  created_by_type VARCHAR(16) NOT NULL,
  created_by_id BIGINT NOT NULL,
  create_time DATETIME NOT NULL,
  updated_by_type VARCHAR(16) NOT NULL,
  updated_by_id BIGINT NOT NULL,
  update_time DATETIME NOT NULL,
  PRIMARY KEY (event_id),
  UNIQUE KEY uk_outbox_dedup_key (dedup_key),
  KEY idx_outbox_claim (
    delivery_status, next_attempt_time, locked_at
  ),
  KEY idx_outbox_aggregate (
    tenant_id, aggregate_type, aggregate_id
  ),
  CONSTRAINT ck_outbox_delivery_status
    CHECK (delivery_status IN ('pending', 'processing', 'delivered', 'dead')),
  CONSTRAINT ck_outbox_retry_count
    CHECK (retry_count >= 0 AND max_attempts > 0 AND retry_count <= max_attempts),
  CONSTRAINT ck_outbox_terminal_status
    CHECK (terminal_status IN ('success', 'failed', 'cancelled')),
  CONSTRAINT ck_outbox_created_by_type
    CHECK (created_by_type IN ('app_user', 'sys_user')),
  CONSTRAINT ck_outbox_updated_by_type
    CHECK (updated_by_type IN ('app_user', 'sys_user')),
  CONSTRAINT ck_outbox_processing_lock
    CHECK (
      (
        delivery_status = 'processing'
        AND locked_by IS NOT NULL
        AND lock_token IS NOT NULL
        AND locked_at IS NOT NULL
      )
      OR
      (
        delivery_status <> 'processing'
        AND locked_by IS NULL
        AND lock_token IS NULL
        AND locked_at IS NULL
      )
    ),
  CONSTRAINT ck_outbox_delivered_time
    CHECK (
      (delivery_status = 'delivered' AND delivered_at IS NOT NULL)
      OR (delivery_status <> 'delivered' AND delivered_at IS NULL)
    )
) ENGINE=InnoDB COMMENT='可靠通知事务发件事件';

DROP PROCEDURE IF EXISTS assert_p4_notification_permissions;
DELIMITER $$
CREATE PROCEDURE assert_p4_notification_permissions()
BEGIN
  DECLARE permission_count INT DEFAULT 0;
  DECLARE mapping_count INT DEFAULT 0;

  SELECT COUNT(DISTINCT p.permission_code)
    INTO permission_count
  FROM app_permission p
  WHERE p.permission_code IN (
      'aivideo:notification:query',
      'aivideo:notification:edit'
    )
    AND p.status = 'active';

  IF permission_count <> 2 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'P4_GATE_MISSING_NOTIFICATION_PERMISSION';
  END IF;

  SELECT COUNT(*)
    INTO mapping_count
  FROM (
    SELECT 'personal_creator' AS role_code
    UNION ALL SELECT 'organization_owner'
    UNION ALL SELECT 'organization_admin'
    UNION ALL SELECT 'organization_member'
  ) expected_role
  CROSS JOIN (
    SELECT 'aivideo:notification:query' AS permission_code
    UNION ALL SELECT 'aivideo:notification:edit'
  ) expected_permission
  JOIN app_role r
    ON r.role_code = expected_role.role_code
   AND r.built_in = 1
   AND r.status = 'active'
   AND r.del_flag = '0'
  JOIN app_permission p
    ON p.permission_code = expected_permission.permission_code
   AND p.status = 'active'
  JOIN app_role_permission rp
    ON rp.role_id = r.role_id
   AND rp.permission_id = p.permission_id
   AND rp.status = 'active';

  IF mapping_count <> 8 THEN
    SIGNAL SQLSTATE '45000'
      SET MESSAGE_TEXT = 'P4_GATE_MISSING_NOTIFICATION_ROLE_MAPPING';
  END IF;
END$$
DELIMITER ;
CALL assert_p4_notification_permissions();
DROP PROCEDURE assert_p4_notification_permissions;
```

`payload_schema_version` 固定从 `1` 起步；`payload_hash` 是规范化 UTF-8 JSON 的 SHA-256；`last_error_summary` 只能保存裁剪后的分类摘要，不保存堆栈、令牌或正文。`dedup_key` 固定为 `task:{tenantId}:{rootTaskId}:{terminalStatus}:{recipientUserId}`，唯一索引保证同一任务终态事件只写一次。认领只能把已到期的 `pending`，即 `next_attempt_time IS NULL OR next_attempt_time <= now`，或租约过期的 `processing` 条件更新为新一轮 `processing`；每轮认领同时写进程级 `locked_by`、本轮唯一 `lock_token` 和 `locked_at`。投递成功写 `delivered`，第 10 次失败写 `dead`，三种离开 `processing` 的路径都必须清空三个锁字段；只有 `delivered` 可带非空 `delivered_at`。两张表都遵循 P0-A 的 typed actor（带类型操作者）约定，不继承 `BaseEntity`：终态事务必须从已经持久化的根任务逐字复制冻结的 `actor_type + actor_id` 到 Outbox，投递生成通知时再从 Outbox 复制，不得读取当前登录上下文；异步工作器身份只存 `locked_by`。用户标记已读时才把通知的更新主体改为当前 `app_user`。四个 actor type 字段均由数据库限制为 `app_user|sys_user`，不得用无类型的 `create_by/update_by`，也不得从异步线程猜测当前登录人。

P4 不插入任何权限、角色或角色权限映射。上述临时过程以 `permission_id`、`role_id` 做三表 JOIN（连接查询），准确断言 2 个有效权限和四个内置角色的 8 个有效映射；任一项缺失即用 `SIGNAL SQLSTATE '45000'` 终止，成功后立即删除临时过程。`aivideo/task/index`、`aivideo/usage/index` 及其运营菜单已由 P0-C 的 `20260728_03_p0c_task_quota_direction.sql` 和 `20260728_04_p0_seed.sql` 负责，P4 同样只验证、不重复插入。

- [ ] **步骤 4（2–5 分钟）：同步最终公共契约**

公共文档必须明确：

- 用户端和运营端身份、客户端、权限和会话双向隔离。
- 每题逐次计费、操作槽、不可变流水和恢复语义。
- 系统知识与用户文案分离。
- 通知只引用根任务结果，不复制业务结果正文。
- `AiTaskServiceImpl` 完成根任务终态、结算或释放、条件清槽和 Outbox 插入必须在同一事务；该事务不得同步插入 `av_user_notification`。
- 功能关闭统一返回 `46135 FEATURE_NOT_AVAILABLE` 和稳定 `gate` 值，只拒绝新的 `entry`、`questionnaire_task` 或 `script_task` 创建。
- 所有接口不增加 `/v1` 路径段。

- [ ] **步骤 5（2–5 分钟）：运行文档检查**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '开发规范检查失败' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '差异空白检查失败' }
```

预期：输出 `DEVELOPMENT_STANDARDS_OK`，差异检查无输出。

- [ ] **步骤 6（2–5 分钟）：提交最终契约**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交最终契约'
}
$expected = @(
  'docs/API_CONTRACT.md'
  'docs/DOMAIN_MODEL.md'
  'docs/ASYNC_TASKS.md'
  'docs/ARCHITECTURE.md'
  'ai-video-ui/ai-video-webapp/PRD.md'
  'docs/sql/ai-video/mysql/20260728_08_p4_integration.sql'
  'docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql'
  'docs/sql/ai-video/mysql/README.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存最终契约失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与最终契约清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "docs: 冻结说需求上线契约"
if ($LASTEXITCODE -ne 0) { throw '提交最终契约失败' }
```

### 任务 2：实现事务通知和可靠发件

**文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/UserNotification.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/OutboxEvent.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/bo/NotificationQueryBo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/vo/UserNotificationVo.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/UserNotificationMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/OutboxEventMapper.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/UserNotificationMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/OutboxEventMapper.xml`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskTerminalNotificationCommand.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskNotificationPolicy.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/NotificationOutboxService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/NotificationOutboxServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxClaimService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxRetryService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryException.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailure.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailureClassifier.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxClaimServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxDeliveryServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxRetryServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/UserNotificationService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/UserNotificationServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxWorkerId.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/controller/NotificationController.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/notification/TaskNotificationPolicyTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/NotificationOutboxIT.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/NotificationControllerTest.java`

- [ ] **步骤 1（2–5 分钟）：编写失败的通知策略和事务一致性测试**

```java
@Test
void terminalTaskShouldWriteOnlyOneOutboxEvent() {
    taskFixture.completeScriptTask(rootTaskId, "success");
    assertThat(notificationMapper.countByRootTask(rootTaskId)).isZero();
    assertThat(outboxMapper.countByDedupKey(
        "task:" + tenantId + ":" + rootTaskId + ":success:" + appUserId))
        .isEqualTo(1);
}

@Test
void transactionRollbackShouldPersistNoOutboxEvent() {
    assertThatThrownBy(() -> taskFixture.completeAndFailOutbox(rootTaskId))
        .isInstanceOf(RuntimeException.class);
    assertThat(taskMapper.selectById(rootTaskId).getStatus())
        .isEqualTo("running");
    assertThat(notificationMapper.selectAll()).isEmpty();
    assertThat(outboxMapper.selectAll()).isEmpty();
}

@Test
void repeatedDeliveryShouldCreateOneNotification() {
    OutboxEvent event = fixture.pendingTerminalEvent(
        rootTaskId, "success", appUserId);

    deliveryJob.execute(jobArgs());
    deliveryJob.execute(jobArgs());

    assertThat(notificationMapper.countByTerminal(
        rootTaskId, "success", appUserId)).isEqualTo(1);
}

@Test
void policyOnlyEmitsWhitelistedAppUserTerminals() {
    assertThat(policy.forTerminal(questionRoot("success"))).isEmpty();
    assertThat(policy.forTerminal(questionRoot("failed")))
        .get()
        .extracting(TaskTerminalNotificationCommand::recipientUserId)
        .isEqualTo(persistedCreatorUserId);
    assertThat(policy.forTerminal(scriptRoot("success"))).isPresent();
    assertThat(policy.forTerminal(scriptOptimizeRoot("failed"))).isPresent();
    assertThat(policy.forTerminal(evidenceRoot("failed"))).isEmpty();
    assertThat(policy.forTerminal(cancelledScriptRoot())).isEmpty();
}

@Test
void sysKnowledgeImportNeverCreatesAppNotificationOrOutbox() {
    AvAiTask knowledgeImport = sysRoot("knowledge_import", "success");
    assertThat(policy.forTerminal(knowledgeImport)).isEmpty();
    taskFixture.completeKnowledgeImport(knowledgeImport.getId());
    assertThat(outboxMapper.countByRootTask(knowledgeImport.getId())).isZero();
}
```

本任务的五个 JUnit 类都在类级标注 `@Tag("dev")`。`NotificationOutboxIT` 还要覆盖：终态事务中的 Outbox 插入失败或唯一键冲突会回滚任务终态、结算/释放和清槽；同一回调重复到达因 `changedToTerminal=false` 而不执行第二次插入；`sys_user` 发起的 `knowledge_import` 成功或失败均产生 0 条 Outbox；创建通知后重复投递仍只有一条通知；未来 `next_attempt_time` 的事件不能提前认领；两个投递器不能同时认领同一事件；同一进程以新 `lock_token` 重新认领后，持有旧 token 的调用无法投递或重排；同批第一条投递失败会被重排但第二条仍能成功；重排事务失败会使调度任务失败并可在租约过期后恢复；第 10 次失败进入 `dead`；`processing`/`delivered` 状态与锁字段、投递时间满足数据库约束；分别向通知和 Outbox 的 created/updated actor type 写入非法值都被 MySQL `CHECK` 拒绝；合法终态写入复制根任务冻结 actor，即使测试线程登录人为另一账号也不改变；typed actor 字段始终区分 `app_user` 与编号；其他用户和其他租户不能查询或标记该通知。

- [ ] **步骤 2（2–5 分钟）：运行单元测试和集成测试并确认失败**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=TaskNotificationPolicyTest,AiTaskServiceTest,OutboxDeliveryJobTest,NotificationControllerTest test
$unitExit = $LASTEXITCODE
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=NotificationOutboxIT verify
$integrationExit = $LASTEXITCODE
if ($unitExit -eq 0 -or $integrationExit -eq 0) {
  throw '红灯门禁未按预期失败；先确认测试确实命中新实现缺口'
}
```

预期：FAIL（失败），策略、终态发件、异步投递和通知去重实现尚不存在。

- [ ] **步骤 3（2–5 分钟）：实现命令、策略和确定性去重键**

```java
public record TaskTerminalNotificationCommand(
    Long tenantId,
    Long rootTaskId,
    String taskType,
    String terminalStatus,
    Long recipientUserId,
    String title,
    String summary,
    String resultRefType,
    Long resultRefId,
    Instant occurredAt
) {}

public interface NotificationOutboxService {
    void appendTaskTerminal(TaskTerminalNotificationCommand command);
}

public Optional<TaskTerminalNotificationCommand> forTerminal(AvAiTask root) {
    if (!"root".equals(root.getTaskRole())
        || !Set.of("success", "failed").contains(root.getStatus())
        || !"app_user".equals(root.getActorType())
        || root.getActorId() == null) {
        return Optional.empty();
    }

    boolean aggregateQuestionFailure =
        "question_generate".equals(root.getTaskType())
            && "failed".equals(root.getStatus());
    boolean userFacingScriptTerminal =
        Set.of("script_generate", "script_optimize")
            .contains(root.getTaskType());
    if (!aggregateQuestionFailure && !userFacingScriptTerminal) {
        return Optional.empty();
    }
    return Optional.of(TaskTerminalNotificationCommand.fromPersistedRoot(root));
}

@Transactional
public void appendTaskTerminal(TaskTerminalNotificationCommand command) {
    String dedupKey = "task:" + command.tenantId()
        + ":" + command.rootTaskId()
        + ":" + command.terminalStatus()
        + ":" + command.recipientUserId();
    String payload = canonicalJson(command);
    outboxMapper.insert(OutboxEvent.pending(
        command, dedupKey, sha256(payload), payload, 1));
}
```

接收人必须来自根任务持久化的租户和实际发起用户，不能来自回调请求、当前线程身份或前端字段。通知策略采用白名单：仅 `app_user` 发起的 `script_generate`、`script_optimize` 成功或失败，以及 `question_generate` 的聚合失败可以生成事件；`question_generate` 成功、`evidence_retrieve`、取消终态、`sys_user` 发起的 `knowledge_import` 和其他运营任务都返回空。事件只冻结 `rootTaskId`、任务类型、终态、实际接收用户、中文摘要和结果引用，不复制问卷答案、模型请求、文案正文、成本或凭据；同一问卷根任务的失败及其多次执行尝试只聚合为一次根任务失败通知。

- [ ] **步骤 4（2–5 分钟）：把 Outbox 接到 P0-C 终态事务**

在 P0-C 已创建的 `AiTaskServiceImpl` 中只扩展既有 `markSuccess`、`markFailed` 和 `requestCancel`，不得引入 `AiTaskRootSnapshot`、`terminalWriter`、`billingFinalizer` 或 `operationSlotService` 等第二套抽象。实现顺序固定为：

1. 保留 P0-C `AiTaskMapper` 的根任务条件更新，并把准确更新行数保存为局部 `changedToTerminal`；更新数为 `0` 时按既有幂等/迟到规则返回或拒绝，不追加事件。
2. 用 P0-C 已有 `QuotaBillingService.settle(operationId, rootTaskId)` 或 `release(operationId, rootTaskId, failureCode)` 完成收费任务终态；免费任务不访问额度表。
3. 用 P0-C 已有 `AiOperationSlotMapper` 按 `tenant_id + slot_key + active_root_task_id` 条件清槽。
4. 通过 `AiTaskMapper.selectById(rootTaskId)` 重读已经持久化的 `AvAiTask` 根任务，交给 `TaskNotificationPolicy.forTerminal(AvAiTask)`；策略返回命令时调用 `NotificationOutboxService.appendTaskTerminal`。

上述四步都处于 P0-C 既有的同一个 `@Transactional(rollbackFor = Exception.class)` 数据库事务中。Outbox 必须使用普通 `insert`，禁止 `INSERT IGNORE`、`insertIgnore` 或吞掉唯一键异常；若发生重复键或任何 Outbox 写入失败，整笔终态、结算/释放和清槽都回滚。终态事务绝不能调用 `UserNotificationMapper`、`UserNotificationService` 或直接写 `av_user_notification`。

- [ ] **步骤 5（2–5 分钟）：实现带租约的认领、重试和死亡事件**

```java
public interface OutboxClaimService {
    List<Long> claimPending(
        String workerId,
        String lockToken,
        int limit,
        Instant now,
        Instant leaseExpiredBefore);
}

public interface OutboxDeliveryService {
    void deliverClaimed(Long eventId, String workerId, String lockToken);
}

public interface OutboxRetryService {
    void rescheduleOrDead(
        Long eventId,
        String workerId,
        String lockToken,
        OutboxFailure failure,
        Instant now);
}

public record OutboxFailure(String code, String safeSummary) {}

@JobExecutor(name = "aiVideoOutboxDeliveryJob")
public ExecuteResult execute(JobArgs args) {
    Instant now = clock.instant();
    String lockToken = UUID.randomUUID().toString();
    List<Long> eventIds = claimService.claimPending(
        workerId.value(), lockToken, 100, now, now.minusSeconds(60));
    int processed = 0;
    int failed = 0;
    boolean rescheduleFailed = false;
    for (Long eventId : eventIds) {
        try {
            deliveryService.deliverClaimed(
                eventId, workerId.value(), lockToken);
            processed++;
        } catch (RuntimeException exception) {
            failed++;
            OutboxFailure failure = failureClassifier.classify(
                OutboxDeliveryException.from(exception));
            try {
                retryService.rescheduleOrDead(
                    eventId,
                    workerId.value(),
                    lockToken,
                    failure,
                    clock.instant());
            } catch (RuntimeException ignored) {
                rescheduleFailed = true;
                log.error("AIVIDEO_OUTBOX_RESCHEDULE_FAILED eventId={}", eventId);
            }
        }
    }
    if (rescheduleFailed) {
        return ExecuteResult.failure(
            "processed=" + processed + ",failed=" + failed
                + ",rescheduleFailed=true");
    }
    return ExecuteResult.success(
        "processed=" + processed + ",failed=" + failed);
}
```

`OutboxClaimServiceImpl.claimPending` 使用普通 `@Transactional`，候选条件必须完整写成 `(delivery_status='pending' AND (next_attempt_time IS NULL OR next_attempt_time <= now)) OR (delivery_status='processing' AND locked_at < leaseExpiredBefore)`，并逐行使用带旧状态、旧锁时间的条件更新写入同一轮新 `lockToken`。`OutboxDeliveryServiceImpl.deliverClaimed` 与 `OutboxRetryServiceImpl.rescheduleOrDead` 分别标注 `@Transactional(propagation = REQUIRES_NEW)`，是由 `OutboxDeliveryJob` 注入的两个独立 Spring Bean（组件），禁止同类自调用：前者校验 `processing + locked_by + lock_token`，以 `(tenant_id, root_task_id, terminal_status, recipient_user_id)` 唯一键插入或读取原通知，再把事件写为 `delivered` 并清空锁字段，事务失败时两者一起回滚；后者也用相同三元租约条件执行指数退避、裁剪错误摘要、递增次数并清空锁字段，第 10 次写 `dead` 并停止自动投递。任一条件更新影响行数不是 1 都分类为 `lease_lost`，旧调用不得提交新一轮租约的投递或重排结果。

`OutboxDeliveryException.from(Throwable)` 只保留异常类别和最深 20 层因果链中的稳定类型，不保留正文；`OutboxFailureClassifier.classify` 只返回白名单代码 `duplicate_notification`、`lease_lost`、`database_unavailable`、`serialization_failed`、`unexpected_delivery_failure` 以及最多 500 字的固定中文摘要。`OutboxWorkerId` 在进程启动时生成一次 `hostname:pid:uuid`，最长 64 字符；`lockToken` 则由每次 `execute` 单独生成 UUID（通用唯一标识符）。二者必须共同参与投递、成功和重排条件，避免同一进程的旧调用在租约过期并由该进程重新认领后误提交。调度循环捕获每个事件的所有 `RuntimeException` 并统一包装分类，单个事件失败不得阻断同批后续事件；重排事务本身失败时本轮返回失败，等待租约过期恢复。不得把异常堆栈、正文或凭据写入事件。

- [ ] **步骤 6（2–5 分钟）：实现当前用户通知查询和幂等已读**

```java
public interface UserNotificationService {
    PageResult<UserNotificationVo> pageCurrentUser(
        NotificationQueryBo bo, PageQuery pageQuery);
    void markRead(Long notificationId);
    void deliverTaskTerminal(OutboxEvent event);
}

@SaCheckPermission(value = "aivideo:notification:query", type = "app")
@GetMapping("/api/notifications")
public R<PageResult<UserNotificationVo>> page(NotificationQueryBo bo, PageQuery pageQuery) {
    return R.ok(service.pageCurrentUser(bo, pageQuery));
}

@SaCheckPermission(value = "aivideo:notification:edit", type = "app")
@PutMapping("/api/notifications/{id}/read")
public R<Void> markRead(@PathVariable Long id) {
    service.markRead(id);
    return R.ok();
}
```

`pageCurrentUser` 和 `markRead` 都从 `AppLoginHelper` 与当前工作区取得租户、实际用户和资源范围；`markRead` 使用 `notification_id + tenant_id + recipient_user_id` 条件更新并允许重复调用。不存在、他租户和他用户通知统一按不可见处理，不泄露其存在性。

- [ ] **步骤 7（2–5 分钟）：运行通知单元测试并断言确实执行**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=TaskNotificationPolicyTest,AiTaskServiceTest,OutboxDeliveryJobTest,NotificationControllerTest test
if ($LASTEXITCODE -ne 0) { throw '通知单元测试失败' }
foreach ($className in @(
    'TaskNotificationPolicyTest',
    'AiTaskServiceTest',
    'OutboxDeliveryJobTest',
    'NotificationControllerTest')) {
  $report = Get-ChildItem -Path . -Recurse -Filter "TEST-*$className.xml" |
    Where-Object {
      $_.LastWriteTime -ge $testStartedAt -and
      $_.FullName -match 'surefire-reports'
    } |
    Select-Object -First 1
  if ($null -eq $report) { throw "$className 未产生本次 Surefire 报告" }
  [xml]$xml = Get-Content -LiteralPath $report.FullName
  if ([int]$xml.testsuite.tests -le 0 -or
      [int]$xml.testsuite.failures -ne 0 -or
      [int]$xml.testsuite.errors -ne 0) {
    throw "$className 未执行或存在失败"
  }
}
```

预期：PASS（通过），且至少一个 Surefire（单元测试执行器）报告包含非零测试数。

- [ ] **步骤 8（2–5 分钟）：运行通知集成测试并断言确实执行**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=NotificationOutboxIT verify
if ($LASTEXITCODE -ne 0) { throw 'NotificationOutboxIT 失败' }
$report = Get-ChildItem `
    -Path .\ruoyi-modules\ai-video\ai-video-infra\target\failsafe-reports `
    -Filter 'TEST-*NotificationOutboxIT.xml' |
  Where-Object { $_.LastWriteTime -ge $testStartedAt } |
  Select-Object -First 1
if ($null -eq $report) { throw 'NotificationOutboxIT 未产生本次报告' }
[xml]$xml = Get-Content -LiteralPath $report.FullName
if ([int]$xml.testsuite.tests -le 0 -or
    [int]$xml.testsuite.failures -ne 0 -or
    [int]$xml.testsuite.errors -ne 0) {
  throw 'NotificationOutboxIT 未执行或存在失败'
}
```

预期：PASS；重复终态、事务回滚、并发认领、租约恢复、重试、死亡事件和跨用户读取全部通过。

- [ ] **步骤 9（2–5 分钟）：精确提交通知能力**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交通知能力'
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/UserNotification.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/OutboxEvent.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/bo/NotificationQueryBo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/domain/vo/UserNotificationVo.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/UserNotificationMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/mapper/OutboxEventMapper.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/UserNotificationMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/notification/OutboxEventMapper.xml'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskTerminalNotificationCommand.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/TaskNotificationPolicy.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/NotificationOutboxService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/NotificationOutboxServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxClaimService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxRetryService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxDeliveryException.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailure.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/OutboxFailureClassifier.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxClaimServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxDeliveryServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/OutboxRetryServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/UserNotificationService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/notification/application/impl/UserNotificationServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxWorkerId.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/controller/NotificationController.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/notification/TaskNotificationPolicyTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/NotificationOutboxIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/NotificationControllerTest.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存通知能力失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与通知能力清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 增加任务结果可靠通知"
if ($LASTEXITCODE -ne 0) { throw '提交通知能力失败' }
```

### 任务 3：实现功能开关和可观测指标

**文件：**

- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureProperties.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureService.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/feature/SayRequirementsFeatureServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/application/impl/ScriptDraftServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewService.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptGenerationServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptVersionServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/ScriptGenerationBillingIT.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptVersionServiceTest.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/observability/AiVideoMetrics.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/observability/MicrometerAiVideoMetrics.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/observability/MicrometerAiVideoMetricsTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/application/impl/QuotaBillingServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/application/impl/KnowledgeRoutingServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeRoutingServiceTest.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ai-video-user-api/src/main/resources/application-prod.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-dev.yml`
- 修改：`ai-video-api/ruoyi-admin/src/main/resources/application-prod.yml`
- 创建：`docs/runbooks/say-requirements-observability.md`

- [ ] **步骤 1（2–5 分钟）：编写失败的三开关边界和指标测试**

```java
@ParameterizedTest
@ValueSource(ints = {0, 25, 100})
void sameUserShouldAlwaysReceiveSamePercentageDecision(int percentage) {
    SayRequirementsFeatureService decision =
        fixture.serviceWithPercentage(ENTRY, percentage);
    boolean first = decision.isEnabledFor(
        SayRequirementsGate.ENTRY, tenantId, appUserId);
    assertThat(decision.isEnabledFor(
        SayRequirementsGate.ENTRY, tenantId, appUserId)).isEqualTo(first);
}

@Test
void disabledGatesBlockOnlyTheirNewCreationBoundary() {
    disable(ENTRY, QUESTIONNAIRE_TASK, SCRIPT_TASK);

    assertThatThrownBy(() -> draftService.create(createDraftCommand()))
        .hasFieldOrPropertyWithValue("code", 46135);
    questionnaireService.loadSnapshot(existingDraftId);
    evidenceReviewService.acceptedContext(existingDraftId, existingBranchId);
    scriptQueryService.detail(existingScriptId);
    recoveryWorker.resume(existingRootTaskId);
    quotaBillingService.compensate(existingLedgerId, 2L, "retry-1", "修正");
    outboxDeliveryJob.execute(jobArgs());
}
```

`ScriptDraftServiceIT` 验证 `entry` 关闭时既不插入草稿也不创建授权；`QuestionnaireApplicationServiceTest` 验证已提交答案仍保存，但创建下一题根任务前受 `questionnaire_task` 控制；`EvidenceReviewServiceTest` 验证新证据检索受同一开关控制，而历史证据查询和已保存决定不受影响；`ScriptGenerationBillingIT` 与 `ScriptVersionServiceTest` 分别验证新生成、新优化受 `script_task` 控制，而结果查询、运行中处理器、恢复和终态回调继续执行。

- [ ] **步骤 2（2–5 分钟）：运行开关和指标测试并确认失败**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=SayRequirementsFeatureServiceTest,QuestionnaireApplicationServiceTest,EvidenceReviewServiceTest,ScriptVersionServiceTest,MicrometerAiVideoMetricsTest,AiTaskServiceTest,QuotaBillingServiceTest,KnowledgeRoutingServiceTest,OutboxDeliveryJobTest test
$unitExit = $LASTEXITCODE
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=ScriptDraftServiceIT,ScriptGenerationBillingIT verify
$integrationExit = $LASTEXITCODE
if ($unitExit -eq 0 -or $integrationExit -eq 0) {
  throw '功能开关红灯门禁未按预期失败'
}
```

预期：FAIL；三个独立门禁、`46135` 和指标埋点尚未实现。

- [ ] **步骤 3（2–5 分钟）：实现三个独立且确定性的创建门禁**

```java
@ConfigurationProperties(prefix = "aivideo.features.say-requirements")
public record SayRequirementsFeatureProperties(
    Gate entry,
    Gate questionnaireTask,
    Gate scriptTask
) {
    public record Gate(
        boolean enabled,
        int percentage,
        Set<Long> userAllowlist
    ) {}
}

public void requireEnabled(
    SayRequirementsGate gate,
    Long tenantId,
    Long appUserId
) {
    if (!isEnabledFor(gate, tenantId, appUserId)) {
        throw new AiVideoBusinessException(
            AiVideoErrorCode.FEATURE_NOT_AVAILABLE,
            "该能力暂未对当前用户开放",
            Map.of("gate", gate.code()));
    }
}
```

桶值固定为 `SHA-256("{gate}:{tenantId}:{appUserId}")` 前 4 字节的无符号整数对 100 取模，避免 JVM 实现差异；白名单只绕过百分比，不绕过总开关。三个边界只能如下接入：

- `entry`：P0-C `ScriptDraftServiceImpl.create` 在任何写入前检查。
- `questionnaire_task`：P2 `QuestionnaireApplicationService` 在创建首题或下一题根任务前检查；`EvidenceReviewService.startSearch` 在创建新证据检索根任务前检查。保存当前答案、补充、事实决定以及问卷/证据历史读取不检查。
- `script_task`：P3 `ScriptGenerationServiceImpl.create` 在创建生成根任务前检查；P3 `ScriptVersionServiceImpl.createOptimization` 在任何优化根任务或账本写入前检查。文案列表、详情、版本编辑、确认和删除不检查。

禁止在 `AiTaskServiceImpl` 的恢复/终态方法、`QuotaBillingServiceImpl` 的结算/释放/补偿、生成与优化 Handler、通知查询或 `OutboxDeliveryJob` 中加功能开关；关闭开关不得取消或阻断已创建任务。

- [ ] **步骤 4（2–5 分钟）：增加三组功能开关与调度客户端环境配置**

```yaml
aivideo:
  features:
    say-requirements:
      entry:
        enabled: ${AIVIDEO_SAY_REQUIREMENTS_ENTRY_ENABLED:false}
        percentage: ${AIVIDEO_SAY_REQUIREMENTS_ENTRY_PERCENTAGE:0}
        user-allowlist: ${AIVIDEO_SAY_REQUIREMENTS_ENTRY_USER_ALLOWLIST:}
      questionnaire-task:
        enabled: ${AIVIDEO_SAY_REQUIREMENTS_QUESTIONNAIRE_TASK_ENABLED:false}
        percentage: ${AIVIDEO_SAY_REQUIREMENTS_QUESTIONNAIRE_TASK_PERCENTAGE:0}
        user-allowlist: ${AIVIDEO_SAY_REQUIREMENTS_QUESTIONNAIRE_TASK_USER_ALLOWLIST:}
      script-task:
        enabled: ${AIVIDEO_SAY_REQUIREMENTS_SCRIPT_TASK_ENABLED:false}
        percentage: ${AIVIDEO_SAY_REQUIREMENTS_SCRIPT_TASK_PERCENTAGE:0}
        user-allowlist: ${AIVIDEO_SAY_REQUIREMENTS_SCRIPT_TASK_USER_ALLOWLIST:}

snail-job:
  enabled: ${AIVIDEO_SNAIL_JOB_ENABLED:false}
  namespace: ${AIVIDEO_SNAIL_JOB_NAMESPACE:${spring.profiles.active}}
  group: ${AIVIDEO_SNAIL_JOB_GROUP:ruoyi_group}
  token: ${AIVIDEO_SNAIL_JOB_TOKEN:}
  server:
    host: ${AIVIDEO_SNAIL_JOB_SERVER_HOST:127.0.0.1}
    port: ${AIVIDEO_SNAIL_JOB_SERVER_PORT:17888}
  # 继续随各自主应用端口漂移；集成测试用命令行覆盖为不同随机端口
  port: 2${server.port}
```

四个 YAML 使用完全相同的九个功能开关变量和六个 SnailJob 变量。功能默认全部关闭且百分比为 `0`，SnailJob 客户端也默认关闭；任何环境把 `AIVIDEO_SNAIL_JOB_ENABLED` 设为 `true` 时，`AIVIDEO_SNAIL_JOB_NAMESPACE`、`AIVIDEO_SNAIL_JOB_GROUP`、非空 `AIVIDEO_SNAIL_JOB_TOKEN` 与服务端地址都必须和目标调度库的启用组完全一致，否则应用启动就绪检查失败。共享开发令牌、数据源、Redis 和模型配置统一写入并提交在 `ai-video-api/ai-video-user-api` 与 `ai-video-api/ruoyi-admin` 的 `application-dev.yml`，环境变量可选覆盖；两端开发配置不得一个连接 `ai_video`、另一个连接 `ry-vue`。

- [ ] **步骤 5（2–5 分钟）：在四个准确业务点注册低基数指标**

```java
public void taskTerminal(String taskType, String terminalStatus, Duration queued) {
    registry.counter(
        "aivideo.task.terminal",
        "task_type", allowedTaskType(taskType),
        "terminal_status", allowedTerminalStatus(terminalStatus)
    ).increment();
    registry.timer(
        "aivideo.task.queue.wait",
        "task_type", allowedTaskType(taskType)
    ).record(queued);
}

public void quotaCompensated(String reasonCode) {
    registry.counter(
        "aivideo.quota.compensation",
        "reason_code", allowedCompensationReason(reasonCode)
    ).increment();
}
```

接入点和恰好一次语义固定为：

- `AiTaskServiceImpl`：只有状态条件更新成功的根任务，在事务 `afterCommit` 回调中调用 `taskTerminal`；重复/迟到回调不计数。
- `QuotaBillingServiceImpl`：只有唯一补偿流水首次插入且事务提交后调用 `quotaCompensated`；幂等重放不计数。
- `KnowledgeRoutingServiceImpl`：围绕 `route` 记录 `aivideo.knowledge.route` 计数和 `aivideo.knowledge.route.duration`；标签只允许 `outcome=success|failed` 与白名单业务错误码，不能包含行业、用户输入或异常消息。
- `OutboxDeliveryJob`：每轮查询后刷新 `aivideo.outbox.pending`、`aivideo.outbox.dead`、`aivideo.outbox.oldest.pending.seconds` 三个 Gauge（仪表值），并按 `outcome=delivered|retry|dead` 记录 `aivideo.outbox.delivery`；同一事件成功只计一次 `delivered`。

`ai-video-core` 只依赖 `AiVideoMetrics` 端口；`ai-video-infra` 的 `MicrometerAiVideoMetrics` 实现该端口，禁止让核心模块反向依赖基础设施模块。`MicrometerAiVideoMetricsTest` 使用 `SimpleMeterRegistry` 断言准确名称、次数、时长和标签白名单，并断言 Meter ID（指标标识）中不出现租户、用户、草稿、任务、原始错误文本或正文。`AiTaskServiceTest`、`QuotaBillingServiceTest`、`KnowledgeRoutingServiceTest` 和 `OutboxDeliveryJobTest` 分别验证上述调用点及幂等重放不重复计数。模型结构失败、操作槽冲突和跨应用凭据拒绝仍可登记，但必须使用固定枚举标签。

- [ ] **步骤 6（2–5 分钟）：编写指标字典和告警阈值**

`docs/runbooks/say-requirements-observability.md` 固定记录每个指标的类型、单位、四个接入类、允许标签、查询表达式和处置人，并写入：

- 5 分钟任务失败率超过 10% 且样本不少于 20 次：告警并停止扩大灰度。
- Outbox 最老待投递超过 5 分钟或死亡事件大于 0：告警。
- 额度补偿连续 5 分钟有新增：告警并核对任务终态。
- 跨类型令牌拒绝突然超过过去 7 天同时间段三倍：安全告警。
- 知识路由不可用超过 1%：回退知识发布版本并停止新文案任务。

- [ ] **步骤 7（2–5 分钟）：运行全部开关与指标单元测试并断言确实执行**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra,ruoyi-modules/ai-video/ai-video-user -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=true `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -Dtest=SayRequirementsFeatureServiceTest,QuestionnaireApplicationServiceTest,EvidenceReviewServiceTest,ScriptVersionServiceTest,MicrometerAiVideoMetricsTest,AiTaskServiceTest,QuotaBillingServiceTest,KnowledgeRoutingServiceTest,OutboxDeliveryJobTest test
if ($LASTEXITCODE -ne 0) { throw '功能开关或指标单元测试失败' }
$expected = @(
  'SayRequirementsFeatureServiceTest',
  'QuestionnaireApplicationServiceTest',
  'EvidenceReviewServiceTest',
  'ScriptVersionServiceTest',
  'MicrometerAiVideoMetricsTest',
  'AiTaskServiceTest',
  'QuotaBillingServiceTest',
  'KnowledgeRoutingServiceTest',
  'OutboxDeliveryJobTest'
)
foreach ($className in $expected) {
  $report = Get-ChildItem -Path . -Recurse -Filter "TEST-*$className.xml" |
    Where-Object {
      $_.LastWriteTime -ge $testStartedAt -and
      $_.FullName -match 'surefire-reports'
    } |
    Select-Object -First 1
  if ($null -eq $report) { throw "$className 未产生本次 Surefire 报告" }
  [xml]$xml = Get-Content -LiteralPath $report.FullName
  if ([int]$xml.testsuite.tests -le 0 -or
      [int]$xml.testsuite.failures -ne 0 -or
      [int]$xml.testsuite.errors -ne 0) {
    throw "$className 未执行或存在失败"
  }
}
```

预期：`BUILD SUCCESS`，且至少一个 Surefire 报告包含非零测试数。

- [ ] **步骤 8（2–5 分钟）：运行两个开关集成测试并断言确实执行**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core,ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=ScriptDraftServiceIT,ScriptGenerationBillingIT verify
if ($LASTEXITCODE -ne 0) { throw '功能开关集成测试失败' }
$expectedReports = @{
  '.\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports' =
    'ScriptDraftServiceIT'
  '.\ruoyi-modules\ai-video\ai-video-infra\target\failsafe-reports' =
    'ScriptGenerationBillingIT'
}
foreach ($reportDirectory in $expectedReports.Keys) {
  $className = $expectedReports[$reportDirectory]
  $report = Get-ChildItem -Path $reportDirectory -Filter "TEST-*$className.xml" |
    Where-Object { $_.LastWriteTime -ge $testStartedAt } |
    Select-Object -First 1
  if ($null -eq $report) { throw "$className 未产生本次 Failsafe 报告" }
  [xml]$xml = Get-Content -LiteralPath $report.FullName
  if ([int]$xml.testsuite.tests -le 0 -or
      [int]$xml.testsuite.failures -ne 0 -or
      [int]$xml.testsuite.errors -ne 0) {
    throw "$className 未执行或存在失败"
  }
}
```

预期：`BUILD SUCCESS`，且 Failsafe（集成测试执行器）报告显示两个 `*IT` 至少执行一个测试。

- [ ] **步骤 9（2–5 分钟）：精确提交功能开关和指标**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交功能开关和指标'
}
$expected = @(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureProperties.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/feature/SayRequirementsFeatureService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/feature/SayRequirementsFeatureServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/application/impl/ScriptDraftServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewService.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/QuestionnaireApplicationServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/application/EvidenceReviewServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptGenerationServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/application/impl/ScriptVersionServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/ScriptGenerationBillingIT.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptVersionServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/observability/AiVideoMetrics.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/observability/MicrometerAiVideoMetrics.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/observability/MicrometerAiVideoMetricsTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/task/AiTaskServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/quota/application/impl/QuotaBillingServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/quota/QuotaBillingServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/application/impl/KnowledgeRoutingServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeRoutingServiceTest.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/OutboxDeliveryJobTest.java'
  'ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml'
  'ai-video-api/ai-video-user-api/src/main/resources/application-prod.yml'
  'ai-video-api/ruoyi-admin/src/main/resources/application-dev.yml'
  'ai-video-api/ruoyi-admin/src/main/resources/application-prod.yml'
  'docs/runbooks/say-requirements-observability.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存功能开关和指标失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与功能开关和指标清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 增加说需求灰度和监控"
if ($LASTEXITCODE -ne 0) { throw '提交功能开关和指标失败' }
```

### 任务 4：接入创作端通知入口并回归既有任务、费用页面

**文件：**

- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/types.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.test.ts`
- 创建：`ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.tsx`
- 创建：`ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/components/RightContent/index.tsx`
- 验证：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskCenterView.test.tsx`
- 验证：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/task/index.test.tsx`
- 验证：`ai-video-ui/ai-video-platform-ui/src/pages/aivideo/usage/index.test.tsx`

- [ ] **步骤 1（2–5 分钟）：编写失败的通知请求契约测试**

```ts
it('分页读取通知并以通知编号幂等标记已读', async () => {
  server.use(
    http.get('/api/notifications', () =>
      HttpResponse.json({ code: 200, data: pageFixture })),
    http.put('/api/notifications/901/read', () =>
      HttpResponse.json({ code: 200, data: null })),
  );

  await expect(queryNotifications({ pageNum: 1, pageSize: 10 }))
    .resolves.toEqual(pageFixture);
  await expect(markNotificationRead('901')).resolves.toBeUndefined();
});
```

- [ ] **步骤 2（2–5 分钟）：定义通知类型和唯一请求层**

```ts
export type TaskNotification = {
  notificationId: string;
  rootTaskId: string;
  terminalStatus: 'success' | 'failed' | 'cancelled';
  title: string;
  summary: string;
  read: boolean;
  resultRef?: { type: string; id: string };
  createdAt: string;
};

type NotificationPagePayload = {
  rows?: TaskNotification[];
  total?: number;
};

export type NotificationPage = {
  items: TaskNotification[];
  total: number;
};

export const queryNotifications = async (
  params: PageParams,
): Promise<NotificationPage> => {
  const page = await requestR<NotificationPagePayload>(
    '/api/notifications',
    { method: 'GET', params },
  );
  return {
    items: page.rows ?? [],
    total: page.total ?? 0,
  };
};

export const markNotificationRead = (notificationId: string) =>
  requestR<void>(`/api/notifications/${notificationId}/read`, {
    method: 'PUT',
  });
```

`requestR` 只从 P0-C 的 `src/services/ai-video/core/ruoyiAdapter.ts` 导入；不得再创建 `getRuoYiPage`、`putRuoYi` 或第二个响应解包器。所有编号继续保持字符串，`rows` 缺失稳定适配为空数组，接口错误统一经过该适配器，组件不得散落路径或自行判断响应包装。契约测试把原始 `pageFixture` 设为 `{ rows: [...], total: 1 }`，断言领域结果是 `{ items: [...], total: 1 }`，另加 `data={}` 时返回 `{ items: [], total: 0 }`。

- [ ] **步骤 3（2–5 分钟）：编写失败的通知入口状态测试**

```tsx
it.each([
  ['loading', '正在加载通知'],
  ['empty', '暂无任务通知'],
  ['error', '通知加载失败'],
  ['forbidden', '暂无通知查看权限'],
] as const)('状态 %s 显示中文反馈', async (state, expected) => {
  renderNotificationDropdown({ state });
  expect(await screen.findByText(expected)).toBeInTheDocument();
});

it('点击未读通知后标记已读并按结果引用进入资源', async () => {
  renderNotificationDropdown({ state: 'success', resultRef: scriptRef });
  await userEvent.click(await screen.findByText('三套文案已生成'));
  expect(markNotificationRead).toHaveBeenCalledWith('901');
  expect(navigateToResult).toHaveBeenCalledWith(scriptRef);
});
```

- [ ] **步骤 4（2–5 分钟）：实现右上角通知下拉入口**

```tsx
export const TaskNotificationDropdown = () => {
  const access = useAccess();
  const notifications = useTaskNotifications({
    enabled: access.canReadTaskNotifications,
  });

  if (!access.canReadTaskNotifications) {
    return null;
  }
  return (
    <Dropdown
      trigger={['click']}
      popupRender={() => <TaskNotificationList {...notifications} />}
    >
      <button type="button" aria-label="任务通知">
        <Badge count={notifications.unreadCount} overflowCount={99}>
          <BellOutlined />
        </Badge>
      </button>
    </Dropdown>
  );
};
```

`Dropdown.popupRender`、`trigger` 和 `Badge.overflowCount` 已按 Ant Design 6 API 核对。`TaskNotificationList` 覆盖加载、空、失败、权限不足、分页和标记中状态；只展示中文摘要，不展示模型请求、原始正文或成本字段。沿用 P0-A 已建立的 `ai-video-ui/ai-video-webapp/vitest.config.ts`、`ai-video-ui/ai-video-platform-ui/vitest.config.ts` 和 `ai-video-ui/ai-video-platform-ui/tests/setupTests.ts`；本任务不得创建或复制任何 `vitest.config.*`、`setupTests.*`。

- [ ] **步骤 5（2–5 分钟）：回归 P0-C 的任务和费用权限边界**

运行：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
function Assert-VitestReport([string]$Path, [string]$Label) {
  if (-not (Test-Path -LiteralPath $Path)) { throw "$Label 未产生 Vitest JSON 报告" }
  $report = Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
  if ([int]$report.numTotalTests -le 0 -or
      [int]$report.numFailedTests -ne 0 -or
      [int]$report.numPassedTests -le 0) {
    throw "$Label 测试数为 0、没有通过用例或存在失败"
  }
}
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
$userReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-user-task-{0}.json" -f [guid]::NewGuid())
npm.cmd test -- src/pages/digital-human-studio/components/TaskCenterView.test.tsx `
  --reporter=json --outputFile=$userReport
if ($LASTEXITCODE -ne 0) { throw '创作端任务中心回归失败' }
Assert-VitestReport $userReport '创作端任务中心'
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
$adminReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-admin-task-usage-{0}.json" -f [guid]::NewGuid())
pnpm.cmd test -- src/pages/aivideo/task/index.test.tsx src/pages/aivideo/usage/index.test.tsx `
  --reporter=json --outputFile=$adminReport
if ($LASTEXITCODE -ne 0) { throw '运营端任务与费用权限回归失败' }
Assert-VitestReport $adminReport '运营端任务与费用'
```

预期：创作端任务中心仍只读取当前用户可见任务；运营端成本抽屉只有 `aivideo:usage-cost:query` 时才渲染并发起成本请求。P4 不创建第二套任务、计费页面或动态组件键。

- [ ] **步骤 6（2–5 分钟）：运行通知测试和两端构建**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
$notificationReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-user-notification-{0}.json" -f [guid]::NewGuid())
npm.cmd test -- src/services/ai-video/notifications/api.test.ts `
  src/components/RightContent/TaskNotificationDropdown.test.tsx `
  --reporter=json --outputFile=$notificationReport
if ($LASTEXITCODE -ne 0) { throw '创作端通知测试失败' }
$notificationTests =
  Get-Content -LiteralPath $notificationReport -Raw -Encoding UTF8 |
  ConvertFrom-Json
if ([int]$notificationTests.numTotalTests -le 0 -or
    [int]$notificationTests.numFailedTests -ne 0 -or
    [int]$notificationTests.numPassedTests -le 0) {
  throw '创作端通知测试数为 0、没有通过用例或存在失败'
}
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '创作端代码检查失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '创作端构建失败' }
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
pnpm.cmd lint
if ($LASTEXITCODE -ne 0) { throw '运营端代码检查失败' }
pnpm.cmd build:prod
if ($LASTEXITCODE -ne 0) { throw '运营端构建失败' }
```

预期：全部命令退出码为 `0`。

- [ ] **步骤 7（2–5 分钟）：精确提交通知入口**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交创作端通知入口'
}
$expected = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/types.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.ts'
  'ai-video-ui/ai-video-webapp/src/services/ai-video/notifications/api.test.ts'
  'ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.tsx'
  'ai-video-ui/ai-video-webapp/src/components/RightContent/TaskNotificationDropdown.test.tsx'
  'ai-video-ui/ai-video-webapp/src/components/RightContent/index.tsx'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存创作端通知入口失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与创作端通知入口清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "feat: 接入创作端任务通知"
if ($LASTEXITCODE -ne 0) { throw '提交创作端通知入口失败' }
```

### 任务 5：只读生成 `fenjing` 资料清单和离线样本

**文件：**

- 创建：`scripts/knowledge/FenjingManifest.Common.ps1`
- 创建：`scripts/knowledge/build-fenjing-manifest.ps1`
- 创建：`scripts/knowledge/validate-fenjing-manifest.ps1`
- 创建：`scripts/knowledge/package-fenjing-import.ps1`
- 创建：`ai-video-api/script/data/knowledge/fenjing/manifest.json`
- 创建：`ai-video-api/script/data/knowledge/fenjing/manifest.sha256`
- 创建：`ai-video-api/script/data/knowledge/fenjing/offline-acceptance-samples.json`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/integration/FenjingKnowledgeRoutingIT.java`
- 创建：`docs/runbooks/fenjing-knowledge-import.md`

- [ ] **步骤 1（2–5 分钟）：定义只读清单架构和允许扩展名**

只读盘点确认当前资料只有 `.md`、`.txt`、`.zip`；脚本必须把这三项作为显式白名单，发现空扩展名或其他扩展名立即失败，不得静默跳过。`manifest.json` 固定为：

```json
{
  "schemaVersion": 1,
  "sourceName": "fenjing",
  "allowedExtensions": [".md", ".txt", ".zip"],
  "classificationRulesVersion": "fenjing-v1",
  "entries": [
    {
      "relativePath": "知识库/05-模板层/视频类型判断.md",
      "extension": ".md",
      "length": 1234,
      "lastWriteTimeUtc": "2026-07-28T01:02:03.0000000Z",
      "sha256": "64位小写十六进制",
      "suggestedDomainCode": "copywriting",
      "suggestedTypeCode": "template",
      "classificationRule": "knowledge-library",
      "parseMode": "text",
      "splitStrategy": "markdown_h1_h2",
      "segments": [
        {
          "segmentNo": 1,
          "title": "稳定标题",
          "startLine": 1,
          "endLine": 40,
          "sha256": "规范化分段文本的64位小写摘要"
        }
      ]
    }
  ],
  "totals": {
    "fileCount": 83,
    "totalBytes": 2060174,
    "extensionCounts": {
      ".md": 72,
      ".txt": 10,
      ".zip": 1
    },
    "domainCounts": {
      "copywriting": 27,
      "storyboard": 17,
      "production": 14,
      "raw_record": 25
    }
  }
}
```

顶层路径分类规则固定为：

- `wenan/**`、`知识库/**` → `copywriting`。
- `分镜逻辑/**`、`分镜头训练/**` → `storyboard`。
- `口播ai出片/**` → `production`。
- `老师方法论/**`、`示范文案/**`、`修改记录/**` 和根目录文件（包括 `知识库.zip`、交接文档）→ `raw_record`。

`suggestedTypeCode` 也只能使用公共契约中的稳定代码：`知识库/05-模板层/**` 为 `template`，`02-技法层/**` 为 `technique`，`03-心理学层/**` 为 `psychology`，`04-案例库/**` 与成品文案为 `case`，`06-规则层/**` 为 `rule`，其余文案资料先标 `source_material`；分镜模板、训练、规则和案例分别使用 `storyboard_template`、`storyboard_training`、`shot_rule`、`storyboard_case`；成片模块按画面、字幕、画中画、音效和完整案例使用 `visual_description_rule`、`subtitle_rule`、`picture_in_picture_rule`、`music_sound_rule`、`production_case`；老师课程、示范、修改记录、交接和 ZIP 分别使用 `course_transcript`、`sample_material`、`revision_record`、`handover_record`、`archive_file`。文件名无法唯一决定类型时固定为该领域的来源材料/案例建议并要求人工确认，不能自动发布。

无法命中或命中多条规则必须失败并要求人工更新规则，不能猜测。`.md` 按一级/二级标题拆分，`.txt` 按连续空行分段；超过 64 KiB 的文本段在段落边界继续拆分；`.zip` 固定为 `attachment_only` 且不在清单阶段解压。分类和拆分都是建议，不改写源文件，最终仍由 P1 导入审核确认。

- [ ] **步骤 2（2–5 分钟）：实现只读快照、规范 JSON 和摘要写入**

`FenjingManifest.Common.ps1` 是三个脚本唯一允许点入的公共函数文件，文件开头启用 `Set-StrictMode -Version Latest` 和 `$ErrorActionPreference = 'Stop'`。下列函数名、参数和输出必须全部在该文件中实现，不允许调用未在同一文件定义的私有 helper（辅助函数）：

| 函数 | 准确输入 | 准确输出与不变量 |
|---|---|---|
| `Resolve-NormalizedRelativePath` | 已解析根路径、已解析文件路径 | 先验证文件路径以 `root + '\'` 为边界前缀，再按根路径长度截取；把 `\` 转为 `/`，拒绝绝对路径、`.`、`..`、空段和大小写重复。 |
| `Get-FileSha256Lower` | 文件绝对路径 | 以只读流计算 64 位小写 SHA-256；流使用 `FileShare.Read`，不写源文件。 |
| `Get-SourceSnapshot` | `-Root` | 返回 `[pscustomobject]`：`root`、按相对路径序数排序的 `files`、`snapshotSha256`；每个文件准确含 `relativePath/fullPath/extension/length/lastWriteTimeUtc/sha256/isReparsePoint`。发现重解析点或白名单外扩展名立即失败。快照摘要是上述公开字段规范 JSON 的 SHA-256。 |
| `Split-FenjingText` | 快照文件、`markdown_h1_h2` 或 `blank_line` | 用严格 UTF-8（允许 UTF-8 BOM）读取，拒绝替换字符；返回连续、非空且不重叠的 `segmentNo/title/startLine/endLine/sha256`，超过 64 KiB 时只在段落边界续拆。 |
| `Get-ManifestEntries` | `-Root`、`-Snapshot` | 按步骤 1 的完整路径规则产生固定属性顺序的条目；`.zip` 固定 `attachment_only` 且 `segments=[]`，文本调用 `Split-FenjingText`。无法唯一分类立即失败。 |
| `ConvertTo-CanonicalManifestJson` | `-Entries` | 返回包含步骤 1 顶层字段与重算 totals（汇总）的单行规范 JSON；所有对象使用 `[ordered]`，数组按规范路径/分段号排序，调用 `ConvertTo-Json -Depth 20 -Compress`，结果自身不带换行。 |
| `Assert-SameSourceSnapshot` | `-Before`、`-After` | 比较 `snapshotSha256` 以及每个文件的路径、长度、时间和摘要；任何差异抛出包含相对路径、不含绝对路径的错误。 |
| `Assert-FenjingManifest` | 源快照、清单、摘要文件 | 独立重算步骤 3 的每条规则并只在全部通过时输出 `MANIFEST_VALID`。 |
| `New-FenjingImportArchive` | `-SourceRoot`、`-SourceSnapshot`、`-ManifestFile`、`-HashFile`、`-TemporaryRoot`、`-OutputZip` | 只复制清单条目和三个 `_manifest` 文件，按 `/` 路径序数排序写 ZIP；拒绝符号链接、越界路径、源变化、重复路径及超过 500 MB。 |

`build-fenjing-manifest.ps1` 只能按以下已闭合调用链组装这些函数：

```powershell-script
param(
  [Parameter(Mandatory = $true)][string]$SourceRoot,
  [Parameter(Mandatory = $true)][string]$OutputFile,
  [Parameter(Mandatory = $true)][string]$HashFile
)
. (Join-Path $PSScriptRoot 'FenjingManifest.Common.ps1')
$resolvedSource = (Resolve-Path -LiteralPath $SourceRoot).Path.TrimEnd('\')
$resolvedOutputParent =
  (Resolve-Path -LiteralPath (Split-Path -Parent $OutputFile)).Path
if ($resolvedOutputParent.StartsWith(
    $resolvedSource,
    [System.StringComparison]::OrdinalIgnoreCase)) {
  throw '输出文件不能位于只读资料根目录内'
}
$sourceBefore = Get-SourceSnapshot -Root $resolvedSource
$entries = Get-ManifestEntries -Root $resolvedSource -Snapshot $sourceBefore |
  Sort-Object -Property relativePath
$json = ConvertTo-CanonicalManifestJson -Entries $entries
[IO.File]::WriteAllText(
  $OutputFile, $json + "`n", [Text.UTF8Encoding]::new($false))
$hash = (Get-FileHash -LiteralPath $OutputFile -Algorithm SHA256).
  Hash.ToLowerInvariant()
[IO.File]::WriteAllText(
  $HashFile, "$hash  manifest.json`n", [Text.UTF8Encoding]::new($false))
$sourceAfter = Get-SourceSnapshot -Root $resolvedSource
Assert-SameSourceSnapshot -Before $sourceBefore -After $sourceAfter
```

当前 Windows PowerShell 不提供 `[IO.Path]::GetRelativePath`，`Resolve-NormalizedRelativePath` 必须使用已解析根路径长度做边界校验后再取子串，不能调用不存在的 API。规范 JSON 使用 `[ordered]` 对象、按规范相对路径序数排序、固定属性顺序、UTF-8 无 BOM 和一个末尾换行；不得写入绝对源路径或生成时间，因此同一源快照重复运行应产生完全相同字节和摘要。`validate-fenjing-manifest.ps1` 与 `package-fenjing-import.ps1` 都必须先点入同一公共文件，分别只调用 `Assert-FenjingManifest` 与 `New-FenjingImportArchive`，不得复制快照、摘要、路径边界或分类算法。

- [ ] **步骤 3（2–5 分钟）：实现独立验证器和临时夹具自测**

验证器必须独立重新计算并验证：

- `manifest.sha256` 与 `manifest.json` 的准确字节匹配。
- 相对路径无绝对路径、`.`、`..`、反斜杠或大小写重复，且按序数升序。
- 扩展名只允许 `.md`、`.txt`、`.zip`，扩展名、长度、修改时间和文件摘要与源文件一致。
- 分类只能是 `copywriting`、`storyboard`、`production`、`raw_record`，四类均非零且命中准确规则。
- 文本的编码、拆分策略、连续行号、非空分段和分段摘要可重算；ZIP 只能是 `attachment_only` 且不得包含文本段。
- 文件数、总字节数、各扩展名数和领域数与条目汇总一致。

`-SelfTest` 只在仓库 `D:\Workspace\ai\projects\ai-video\.tmp\fenjing-manifest-self-test` 创建小型夹具，覆盖未知扩展名、重复规范路径、修改时间变化、摘要变化、分类失败、分段越界和清单摘要错误；它不得读取或写入真实 `fenjing` 根目录。

- [ ] **步骤 4（2–5 分钟）：生成并验证真实清单且证明源目录未变**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\knowledge\validate-fenjing-manifest.ps1 -SelfTest
if ($LASTEXITCODE -ne 0) { throw 'fenjing 清单验证器自测失败' }
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\knowledge\build-fenjing-manifest.ps1 `
  -SourceRoot "D:\Workspace\ai\projects\文案\fenjing" `
  -OutputFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.json" `
  -HashFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.sha256"
if ($LASTEXITCODE -ne 0) { throw 'fenjing 清单生成失败' }
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\knowledge\validate-fenjing-manifest.ps1 `
  -SourceRoot "D:\Workspace\ai\projects\文案\fenjing" `
  -ManifestFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.json" `
  -HashFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.sha256"
if ($LASTEXITCODE -ne 0) { throw 'fenjing 真实清单验证失败' }
```

预期：输出 `.md=72`、`.txt=10`、`.zip=1`、四领域非零、`MANIFEST_VALID` 和相同的前后源快照摘要；源目录文件数量、长度、修改时间和 SHA-256 全部不变。

- [ ] **步骤 5（2–5 分钟）：实现只读打包脚本**

`package-fenjing-import.ps1` 的实现体固定为下列调用顺序，任何调用均已在公共函数文件中定义：

```powershell-script
param(
  [Parameter(Mandatory = $true)][string]$SourceRoot,
  [Parameter(Mandatory = $true)][string]$ManifestFile,
  [Parameter(Mandatory = $true)][string]$HashFile,
  [Parameter(Mandatory = $true)][string]$OutputZip
)
. (Join-Path $PSScriptRoot 'FenjingManifest.Common.ps1')
$resolvedSource = (Resolve-Path -LiteralPath $SourceRoot).Path.TrimEnd('\')
$before = Get-SourceSnapshot -Root $resolvedSource
Assert-FenjingManifest `
  -SourceSnapshot $before `
  -ManifestFile (Resolve-Path -LiteralPath $ManifestFile).Path `
  -HashFile (Resolve-Path -LiteralPath $HashFile).Path
$temporaryRoot = Join-Path `
  ([IO.Path]::GetTempPath()) `
  ("fenjing-import-{0}" -f [guid]::NewGuid())
New-FenjingImportArchive `
  -SourceRoot $resolvedSource `
  -SourceSnapshot $before `
  -ManifestFile (Resolve-Path -LiteralPath $ManifestFile).Path `
  -HashFile (Resolve-Path -LiteralPath $HashFile).Path `
  -TemporaryRoot $temporaryRoot `
  -OutputZip ([IO.Path]::GetFullPath($OutputZip))
$after = Get-SourceSnapshot -Root $resolvedSource
Assert-SameSourceSnapshot -Before $before -After $after
```

`New-FenjingImportArchive` 只按清单列出的规范相对路径把源字节复制到临时打包目录，同时加入 `_manifest/manifest.json`、`_manifest/manifest.sha256` 和 `_manifest/import-decisions.json`。`import-decisions.json` 只包含建议分类、类型、拆分行号和源摘要，不含源正文。脚本拒绝符号链接、清单外文件、源文件变化、输出位于源根目录、同名规范路径和超过 P1 500 MB 限制；最终 ZIP 固定写入：

```text
D:\Workspace\ai\projects\ai-video\.tmp\fenjing-import\fenjing-knowledge-import.zip
```

包内路径使用 `/`，排序稳定；打包前后再次比较源快照。`.tmp` 已被仓库根 `.gitignore` 忽略，生成 ZIP 不加入 Git。

- [ ] **步骤 6（2–5 分钟）：创建固定离线路由样本**

人工审核最小 `copywriting` 集合时固定分配下列稳定代码，并把绑定角度与视频类型规则一并纳入审核：`fenjing.copywriting.template.no_sales_formula`、`fenjing.copywriting.template.t01_step_teaching`、`fenjing.copywriting.template.t03_cognitive_breakthrough`、`fenjing.copywriting.template.t06_pain_contrast`、`fenjing.copywriting.technique.hook`、`fenjing.copywriting.technique.pain_point`、`fenjing.copywriting.technique.reframe`、`fenjing.copywriting.psychology.consumer_weapon`、`fenjing.copywriting.rule.pitfall`。稳定代码对应的清单相对路径和 SHA-256 必须与下列样本一致；不一致即先更新样本并重新审核，不能在测试中放宽。

`offline-acceptance-samples.json` 的完整结构和四条确定性记录固定为：

```json
{
  "schemaVersion": 1,
  "manifestSchemaVersion": 1,
  "samples": [
    {
      "sampleCode": "ecommerce-product-60",
      "direction": {
        "catalogVersion": "1",
        "industryCode": "ecommerce",
        "purposeCode": "product_service_intro",
        "purposeCustomText": null,
        "targetDurationSeconds": 60
      },
      "slots": {
        "targetAudience": "希望减少挑选时间的线上消费者",
        "coreOffer": "一套可快速理解的商品选择方案",
        "painPoint": "参数多且难比较",
        "proof": "提供公开规格对照",
        "callToAction": "查看完整选择清单"
      },
      "expected": {
        "videoTypeCode": "pain_point_conversion",
        "plans": [
          {
            "planCode": "A",
            "primaryTemplateStableCode": "fenjing.copywriting.template.no_sales_formula",
            "angleCode": "pain_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.hook"
          },
          {
            "planCode": "B",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t06_pain_contrast",
            "angleCode": "contrast_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.pain_point"
          },
          {
            "planCode": "C",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t03_cognitive_breakthrough",
            "angleCode": "cognitive_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.reframe"
          }
        ],
        "requiredStableCodes": [
          "fenjing.copywriting.psychology.consumer_weapon",
          "fenjing.copywriting.rule.pitfall"
        ],
        "forbiddenDomainCodes": ["storyboard", "production", "raw_record"],
        "sourceFiles": [
          {
            "relativePath": "知识库/05-模板层/无销售方程式5步.md",
            "sha256": "e2612bd5d50026d2d13908218ee5b48a25393cf54392e075b0225846b64ac425"
          },
          {
            "relativePath": "知识库/02-技法层/钩子写法.md",
            "sha256": "ddc7bed8c22c625f45b68f97e6bd13c60fbfffca48cdfa246d7d90336082037d"
          }
        ]
      }
    },
    {
      "sampleCode": "education-custom-120",
      "direction": {
        "catalogVersion": "1",
        "industryCode": "education",
        "purposeCode": "custom",
        "purposeCustomText": "解释课程价值并建立信任",
        "targetDurationSeconds": 120
      },
      "slots": {
        "targetAudience": "需要判断课程是否适合自己的成年人",
        "coreOffer": "结构化学习路径说明",
        "painPoint": "课程信息分散",
        "proof": "提供公开课程安排",
        "callToAction": "领取课程结构说明"
      },
      "expected": {
        "videoTypeCode": "step_teaching",
        "plans": [
          {
            "planCode": "A",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t01_step_teaching",
            "angleCode": "step_first",
            "differentiatorTechniqueStableCode": "template_default"
          },
          {
            "planCode": "B",
            "primaryTemplateStableCode": "fenjing.copywriting.template.no_sales_formula",
            "angleCode": "proof_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.hook"
          },
          {
            "planCode": "C",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t03_cognitive_breakthrough",
            "angleCode": "cognitive_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.reframe"
          }
        ],
        "requiredStableCodes": [
          "fenjing.copywriting.rule.pitfall"
        ],
        "forbiddenDomainCodes": ["storyboard", "production", "raw_record"],
        "sourceFiles": [
          {
            "relativePath": "知识库/05-模板层/T01-T11模板索引.md",
            "sha256": "5da1a7a83b4ccc5a4f7f2e48a7da5fb7697e06d97ed43a98d1ddf4b85556653a"
          },
          {
            "relativePath": "知识库/02-技法层/颠覆与重塑.md",
            "sha256": "45b1e6cb7cdae741a2f8b3d14f39e536e8715d82753adc35bd3d380024656003"
          }
        ]
      }
    },
    {
      "sampleCode": "home-custom-90",
      "direction": {
        "catalogVersion": "1",
        "industryCode": "home",
        "purposeCode": "custom",
        "purposeCustomText": "用改造案例获取咨询",
        "targetDurationSeconds": 90
      },
      "slots": {
        "targetAudience": "准备改善旧房空间的家庭",
        "coreOffer": "旧房空间规划咨询",
        "painPoint": "担心预算和动线同时失控",
        "proof": "展示脱敏前后方案对比",
        "callToAction": "预约空间评估"
      },
      "expected": {
        "videoTypeCode": "case_story",
        "plans": [
          {
            "planCode": "A",
            "primaryTemplateStableCode": "fenjing.copywriting.template.no_sales_formula",
            "angleCode": "case_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.pain_point"
          },
          {
            "planCode": "B",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t06_pain_contrast",
            "angleCode": "contrast_first",
            "differentiatorTechniqueStableCode": "template_default"
          },
          {
            "planCode": "C",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t03_cognitive_breakthrough",
            "angleCode": "proof_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.reframe"
          }
        ],
        "requiredStableCodes": [
          "fenjing.copywriting.psychology.consumer_weapon",
          "fenjing.copywriting.rule.pitfall"
        ],
        "forbiddenDomainCodes": ["storyboard", "production", "raw_record"],
        "sourceFiles": [
          {
            "relativePath": "知识库/02-技法层/痛点写法.md",
            "sha256": "63fa756ef814bf5460f7d07f930b3e1437685290758d9233d1b3ea755550b836"
          },
          {
            "relativePath": "知识库/03-心理学层/消费心理7武器.md",
            "sha256": "6b446766e89da578dce495d0aed4935281194ab99da770cc240a35ccef31072f"
          }
        ]
      }
    },
    {
      "sampleCode": "local-custom-45",
      "direction": {
        "catalogVersion": "1",
        "industryCode": "local",
        "purposeCode": "custom",
        "purposeCustomText": "介绍门店服务并引导到店",
        "targetDurationSeconds": 45
      },
      "slots": {
        "targetAudience": "门店周边有即时需求的顾客",
        "coreOffer": "透明的到店服务流程",
        "painPoint": "不了解服务步骤和等待时间",
        "proof": "提供公开服务流程",
        "callToAction": "预约到店时段"
      },
      "expected": {
        "videoTypeCode": "scene_conversion",
        "plans": [
          {
            "planCode": "A",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t06_pain_contrast",
            "angleCode": "scene_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.pain_point"
          },
          {
            "planCode": "B",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t03_cognitive_breakthrough",
            "angleCode": "cognitive_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.reframe"
          },
          {
            "planCode": "C",
            "primaryTemplateStableCode": "fenjing.copywriting.template.t01_step_teaching",
            "angleCode": "step_first",
            "differentiatorTechniqueStableCode": "fenjing.copywriting.technique.hook"
          }
        ],
        "requiredStableCodes": [
          "fenjing.copywriting.rule.pitfall"
        ],
        "forbiddenDomainCodes": ["storyboard", "production", "raw_record"],
        "sourceFiles": [
          {
            "relativePath": "知识库/06-规则层/避坑清单.md",
            "sha256": "f436d8b549ca960d1b3588afc6e74c9818a541563546c70f78b6a50bfed4cd58"
          },
          {
            "relativePath": "知识库/05-模板层/视频类型判断.md",
            "sha256": "4cacd4bc2a18c7da0ab664e243f4be8c470ea80cac35d50d40489e5d86c94d9e"
          }
        ]
      }
    }
  ]
}
```

样本只含合成业务信息，不复制资料正文。`FenjingKnowledgeRoutingIT` 先逐条验证 `sourceFiles` 在本次 `manifest.json` 中存在且摘要完全一致，再按稳定代码解析本批次已发布版本编号，并把 `primaryTemplateStableCode + angleCode + differentiatorTechniqueStableCode` 转成 P1 的真实 `primaryTemplateVersionId + angleCode + differentiatorTechniqueCode` 三元组进行比较；不得按列表位置或中文名称猜测。

- [ ] **步骤 7（2–5 分钟）：记录打包、上传、预览、审核、提交和发布闭环**

`docs/runbooks/fenjing-knowledge-import.md` 必须给出下列准确链路和停止条件：

1. 从仓库根运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\knowledge\validate-fenjing-manifest.ps1 -SourceRoot "D:\Workspace\ai\projects\文案\fenjing" -ManifestFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.json" -HashFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.sha256"`；任何源变化或清单摘要错误立即停止。
2. 运行 `package-fenjing-import.ps1` 生成上述 ZIP，并记录 ZIP SHA-256。
3. 使用具有 `system:oss:upload` 权限的运营账号，把 ZIP 作为 `file` multipart（多段表单）字段上传到 `POST /resource/oss/upload`，只保存响应 `data.ossId`；不把下载 URL（资源定位地址）当授权。
4. 使用 `aivideo:knowledge:import` 调用 `POST /api/admin/knowledge/import-batches`，请求包含字符串 `ossId`、`clientManifestHash`、`idempotencyKey`；轮询 `GET /api/admin/knowledge/import-batches/{id}`，直到免费 `knowledge_import` 根任务成功并出现预览。
5. 在“系统知识中心 → 导入批次 → 查看审核”逐项比较相对路径、源摘要、领域、类型和拆分行号；人工标记重复、冲突、敏感和排除项，保存审核决定。四领域预览数或摘要不一致立即停止。
6. 调用 `POST /api/admin/knowledge/import-batches/{id}/commit` 提交准确审核修订；断言响应版本全部为 `draft`、`usageOperationId=null` 且没有任何 `published` 版本。
7. 由具有 `aivideo:knowledge:review` 权限的知识审核人调用 P1 版本状态机契约 `POST /api/admin/knowledge/versions/{id}/submit-review`，请求体精确为 `{ "expectedRevision": <当前修订号> }`，把选中的最小完整 `copywriting` 集合从 `draft` 提交为 `reviewing`。响应必须返回递增后的 `revision` 和 `status=reviewing`；接口、权限、状态转换或管理页动作任一不存在都在 P4 前置验收停止，严禁直接更新数据库。
8. 使用 `aivideo:knowledge:publish` 调用 `POST /api/admin/knowledge/versions/{id}/publish`，请求包含准确 `expectedRevision` 和非空 `publishNote`；确认新发布版本唯一，旧版本退役，其他三个领域仍不参与路由。
9. 从 `D:\Workspace\ai\projects\ai-video\ai-video-api` 运行 `.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false -Dit.test=FenjingKnowledgeRoutingIT verify`；`failIfNoSpecifiedTests=false` 只允许没有该类的上游依赖模块继续构建，随后必须精确确认 `ai-video-infra/target/failsafe-reports` 中该类本次报告的测试数大于 0、没有失败或错误、并非全部跳过；全部通过后才允许扩大 `script_task`（文案任务）灰度。

打包、上传和创建批次的可复制命令固定为：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$package = 'D:\Workspace\ai\projects\ai-video\.tmp\fenjing-import\fenjing-knowledge-import.zip'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\knowledge\package-fenjing-import.ps1 `
  -SourceRoot "D:\Workspace\ai\projects\文案\fenjing" `
  -ManifestFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.json" `
  -HashFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.sha256" `
  -OutputZip $package
if ($LASTEXITCODE -ne 0) { throw 'fenjing 导入包生成失败' }
$uploadJson = curl.exe --fail-with-body --silent --show-error `
  -X POST "$env:P4_ADMIN_BASE_URL/resource/oss/upload" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID" `
  -F "file=@$package;type=application/zip"
if ($LASTEXITCODE -ne 0) { throw 'fenjing ZIP 上传失败' }
$upload = $uploadJson | ConvertFrom-Json
$manifestHash = (
  Get-Content -LiteralPath .\ai-video-api\script\data\knowledge\fenjing\manifest.sha256
).Split(' ')[0]
$batchBody = @{
  ossId = [string]$upload.data.ossId
  clientManifestHash = $manifestHash
  idempotencyKey = "fenjing-$manifestHash"
} | ConvertTo-Json -Compress
$batchJson = curl.exe --fail-with-body --silent --show-error `
  -X POST "$env:P4_ADMIN_BASE_URL/api/admin/knowledge/import-batches" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID" `
  -H "Content-Type: application/json" `
  --data-binary $batchBody
if ($LASTEXITCODE -ne 0) { throw 'P1 知识导入批次创建失败' }
$batch = $batchJson | ConvertFrom-Json
$batchId = [string]$batch.data.batchId
$previewJson = curl.exe --fail-with-body --silent --show-error `
  -X GET "$env:P4_ADMIN_BASE_URL/api/admin/knowledge/import-batches/$batchId" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID"
if ($LASTEXITCODE -ne 0) { throw 'P1 知识导入预览读取失败' }
$preview = ($previewJson | ConvertFrom-Json).data
$preview | Select-Object batchId,status,revisionNo,totalEntries,reviewEntries
```

命令不得打印令牌；运行手册要求终端历史和 CI（持续集成）日志对令牌脱敏。人工审核与发布不得被脚本自动确认。

人工在 P1 管理页保存分类、拆分、重复、冲突和敏感决定后，使用准确修订提交草稿；审核人通过上述明确的 `submit-review` 契约把选中版本合法转换为 `reviewing` 后，再逐个发布：

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$commitBody = @{
  expectedRevision = [int]$env:P4_FENJING_BATCH_REVISION
} | ConvertTo-Json -Compress
$commitJson = curl.exe --fail-with-body --silent --show-error `
  -X POST "$env:P4_ADMIN_BASE_URL/api/admin/knowledge/import-batches/$env:P4_FENJING_BATCH_ID/commit" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID" `
  -H "Content-Type: application/json" `
  --data-binary $commitBody
if ($LASTEXITCODE -ne 0) { throw 'P1 知识导入提交失败' }
$commit = $commitJson | ConvertFrom-Json
if (@($commit.data.versions | Where-Object status -ne 'draft').Count -ne 0) {
  throw '导入提交产生了非 draft 版本'
}
$reviewBody = @{
  expectedRevision = [int]$env:P4_FENJING_DRAFT_REVISION
} | ConvertTo-Json -Compress
$reviewJson = curl.exe --fail-with-body --silent --show-error `
  -X POST "$env:P4_ADMIN_BASE_URL/api/admin/knowledge/versions/$env:P4_FENJING_VERSION_ID/submit-review" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID" `
  -H "Content-Type: application/json" `
  --data-binary $reviewBody
if ($LASTEXITCODE -ne 0) { throw 'P1 知识版本提交审核失败' }
$reviewed = ($reviewJson | ConvertFrom-Json).data
if ($reviewed.status -ne 'reviewing' -or
    [int]$reviewed.revision -le [int]$env:P4_FENJING_DRAFT_REVISION) {
  throw '知识版本未合法进入 reviewing 或修订号未递增'
}
$publishBody = @{
  expectedRevision = [int]$reviewed.revision
  publishNote = 'fenjing 人工审核与离线路由验收'
} | ConvertTo-Json -Compress
$publishJson = curl.exe --fail-with-body --silent --show-error `
  -X POST "$env:P4_ADMIN_BASE_URL/api/admin/knowledge/versions/$env:P4_FENJING_VERSION_ID/publish" `
  -H "Authorization: Bearer $env:P4_SYS_TOKEN" `
  -H "clientid: $env:P4_SYS_CLIENT_ID" `
  -H "Content-Type: application/json" `
  --data-binary $publishBody
if ($LASTEXITCODE -ne 0) { throw 'P1 知识版本发布失败' }
$published = $publishJson | ConvertFrom-Json
if ($published.data.status -ne 'published') { throw '知识版本未进入 published' }
```

- [ ] **步骤 8（2–5 分钟）：实现并运行发布后离线路由验收**

类级标注 `@Tag("dev")` 的 `FenjingKnowledgeRoutingIT` 从 `offline-acceptance-samples.json` 逐项调用公开接口 `KnowledgeRoutingService`，断言恰好返回 A/B/C 三个不同三元组、视频类型准确、所有 `requiredStableCodes` 命中、`forbiddenDomainCodes`（至少 `storyboard`、`production`、`raw_record`）完全不命中，并验证每个快照版本属于本任务经审核和发布接口发布的批次。缺少 `P4_FENJING_BATCH_ID` 或批次尚未发布时测试必须失败，不能跳过。

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=FenjingKnowledgeRoutingIT verify
if ($LASTEXITCODE -ne 0) { throw 'FenjingKnowledgeRoutingIT 失败' }
$report = Get-ChildItem `
    -Path .\ruoyi-modules\ai-video\ai-video-infra\target\failsafe-reports `
    -Filter 'TEST-*FenjingKnowledgeRoutingIT.xml' |
  Where-Object { $_.LastWriteTime -ge $testStartedAt } |
  Select-Object -First 1
if ($null -eq $report) { throw 'FenjingKnowledgeRoutingIT 未产生本次报告' }
[xml]$xml = Get-Content -LiteralPath $report.FullName
$suite = $xml.testsuite
if ([int]$suite.tests -le 0 -or [int]$suite.failures -ne 0 -or
    [int]$suite.errors -ne 0 -or [int]$suite.skipped -eq [int]$suite.tests) {
  throw 'FenjingKnowledgeRoutingIT 未执行、全部跳过或存在失败'
}
```

预期：`BUILD SUCCESS`，四组样本全部通过且 Failsafe 报告测试数非零。

- [ ] **步骤 9（2–5 分钟）：精确提交清单工具和验收样本**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交 fenjing 清单工具和样本'
}
$expected = @(
  'scripts/knowledge/FenjingManifest.Common.ps1'
  'scripts/knowledge/build-fenjing-manifest.ps1'
  'scripts/knowledge/validate-fenjing-manifest.ps1'
  'scripts/knowledge/package-fenjing-import.ps1'
  'ai-video-api/script/data/knowledge/fenjing/manifest.json'
  'ai-video-api/script/data/knowledge/fenjing/manifest.sha256'
  'ai-video-api/script/data/knowledge/fenjing/offline-acceptance-samples.json'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/integration/FenjingKnowledgeRoutingIT.java'
  'docs/runbooks/fenjing-knowledge-import.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存 fenjing 清单工具和样本失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与 fenjing 清单工具和样本清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "data: 增加系统知识迁移清单"
if ($LASTEXITCODE -ne 0) { throw '提交 fenjing 清单工具和样本失败' }
```

### 任务 6：建立创作端和运营端浏览器端到端测试

**文件：**

- 修改：`ai-video-ui/ai-video-webapp/package.json`
- 修改：`ai-video-ui/ai-video-webapp/package-lock.json`
- 创建：`ai-video-ui/ai-video-webapp/playwright.config.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/global-setup.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/support.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/auth-isolation.spec.ts`
- 创建：`ai-video-ui/ai-video-webapp/tests/e2e/say-requirements.spec.ts`
- 修改：`ai-video-ui/ai-video-platform-ui/package.json`
- 修改：`ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml`
- 创建：`ai-video-ui/ai-video-platform-ui/playwright.config.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/global-setup.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/support.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/app-identity-admin.spec.ts`
- 创建：`ai-video-ui/ai-video-platform-ui/tests/e2e/knowledge-and-billing.spec.ts`

- [ ] **步骤 1（2–5 分钟）：增加测试脚本和依赖**

用户端和运营端分别增加：

```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:serve": "cross-env PORT=8000 UMI_ENV=test MOCK=none max dev"
  },
  "devDependencies": {
    "@playwright/test": "^1.55.0"
  }
}
```

运营端的 `test:e2e:serve` 固定为 `cross-env PORT=8001 UMI_ENV=development MOCK=none BABEL_POLYFILL=none max dev`。使用各自锁文件安装；创作端使用 `npm.cmd install`，运营端使用 `pnpm.cmd install`，不得交叉改写锁文件，也不得新增或复制 P0-A 已建立的 `vitest.config.*` 和测试初始化文件。

- [ ] **步骤 2（2–5 分钟）：配置稳定测试环境**

```ts
import { defineConfig } from '@playwright/test';

const userBaseURL = process.env.E2E_USER_BASE_URL;
if (!userBaseURL) {
  throw new Error('缺少 E2E_USER_BASE_URL');
}

export default defineConfig({
  testDir: './tests/e2e',
  globalSetup: './tests/e2e/global-setup.ts',
  retries: process.env.CI ? 2 : 0,
  webServer: {
    command: 'npm.cmd run test:e2e:serve',
    url: userBaseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
  use: {
    baseURL: userBaseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
});
```

运营端配置使用 `E2E_ADMIN_BASE_URL`、`pnpm.cmd run test:e2e:serve` 和同名 `global-setup.ts`，其余选项一致。两个全局安装文件都必须在任何登录前逐项检查下列 11 个变量，缺一即列出准确变量名并失败，不把值或凭据写入日志：

```text
E2E_USER_BASE_URL
E2E_ADMIN_BASE_URL
E2E_USER_API_BASE_URL
E2E_ADMIN_API_BASE_URL
E2E_APP_CLIENT_ID
E2E_SYS_CLIENT_ID
E2E_APP_USERNAME
E2E_APP_PASSWORD
E2E_SYS_USERNAME
E2E_SYS_PASSWORD
E2E_MODEL_STUB_BASE_URL
```

`global-setup.ts` 内部必须完整定义并只调用 `requireEnvironment`、`assertHealthy`、`resetWireMock`、`registerModelMappings` 四个函数：`assertHealthy` 分别访问两个 API 地址的 `/actuator/health` 并要求 HTTP 200 和 `status=UP`；`resetWireMock` 对 `${E2E_MODEL_STUB_BASE_URL}/__admin/reset` 发 POST；`registerModelMappings` 再对 `/__admin/mappings/import` 发 POST，使用 `deleteAllNotInImport=true` 安装七组确定性映射 `question-1`～`question-5`、`script-generate`、`script-optimize`。映射按模型请求中固定的 `[P4_FIXTURE:<key>]` 提示标记匹配，不依赖请求到达顺序；五个问题响应每次只有一道多选题并包含“自定义”选项，生成响应恰含 A/B/C 三套，优化响应只含一个新版本。任一健康检查、重置或映射导入非 2xx 都抛错并输出状态码和最多 500 字脱敏响应，不输出请求正文。

用例开始时还要 GET `${E2E_MODEL_STUB_BASE_URL}/__admin/mappings`，断言恰好存在上述七个 `name`；每个测试文件在 `beforeEach` 中 POST `/__admin/scenarios/reset`，清理场景状态但保留映射。生产模型适配器不得增加测试分支；`[P4_FIXTURE:*]` 只来自测试夹具输入并进入本地 WireMock（本地 HTTP 模拟服务）。

两个 `support.ts` 都必须在本文件内完整实现并导出 `requiredE2eEnvironment()`、`loginAppUser(APIRequestContext)`、`loginSysUser(APIRequestContext)`、`resetWireMockScenarios(APIRequestContext)`；前两者分别只向 `${E2E_USER_API_BASE_URL}` 和 `${E2E_ADMIN_API_BASE_URL}` 的既有 P0-A 登录接口发送对应账号、密码和客户端编号，验证 `code=200` 后只返回令牌字符串，响应非成功时抛出不含密码/令牌的错误。禁止从另一身份域回退登录。`global-setup.ts` 与 spec（测试规格）只从同目录 `support.ts` 导入这四个已定义函数，不得再出现未声明的 `loginSysUser`、`loginAppUser` 或 WireMock helper。

- [ ] **步骤 3（2–5 分钟）：编写双向隔离浏览器测试**

```ts
test('运营端令牌不能进入创作工作台', async ({ request }) => {
  const sysToken = await loginSysUser(request);
  const response = await request.get(
    `${process.env.E2E_USER_API_BASE_URL}/api/auth/me`,
    {
    headers: { Authorization: `Bearer ${sysToken}`, clientid: process.env.E2E_SYS_CLIENT_ID! },
    },
  );
  expect(response.status()).toBe(401);
});
```

另一个方向使用绝对 `${E2E_ADMIN_API_BASE_URL}/api/admin/app-users` 验证 `app` 令牌为 401；登录 helper（辅助函数）也分别只访问对应 API 基址。交换客户端键、重复认证头由后端集成测试覆盖，浏览器请求不得依赖当前页面基址把运营请求误发到创作 API。

- [ ] **步骤 4（2–5 分钟）：编写完整创作链测试**

用固定模拟模型依次返回 3～5 道题和 A/B/C 文案，浏览器完成：登录 → 选择工作区 → 新建草稿 → 行业/用途/时长 → 每答一题等待下一题 → 自定义取消后重新勾选保留文字 → 第 5 题固定补充 → 证据决定 → 生成三套文案 → 编辑 → 优化 → 确认 → 文案库查看 → 任务与账单查看。

- [ ] **步骤 5（2–5 分钟）：编写运营管理链测试**

运营端完成：查询/停用/启用创作用户 → 撤销会话 → 查看登录审计 → 修改创作角色权限 → 管理组织成员 → 发布方向目录 → 导入知识草稿 → 审核发布 → 查询任务与额度流水。测试断言停用创作用户后旧 `app` 会话失效，但运营会话仍有效。

- [ ] **步骤 6（2–5 分钟）：运行端到端测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$requiredE2eEnvironment = @(
  'E2E_USER_BASE_URL',
  'E2E_ADMIN_BASE_URL',
  'E2E_USER_API_BASE_URL',
  'E2E_ADMIN_API_BASE_URL',
  'E2E_APP_CLIENT_ID',
  'E2E_SYS_CLIENT_ID',
  'E2E_APP_USERNAME',
  'E2E_APP_PASSWORD',
  'E2E_SYS_USERNAME',
  'E2E_SYS_PASSWORD',
  'E2E_MODEL_STUB_BASE_URL'
)
$missingE2eEnvironment = @(
  $requiredE2eEnvironment |
    Where-Object {
      [string]::IsNullOrWhiteSpace(
        [Environment]::GetEnvironmentVariable($_))
    }
)
if ($missingE2eEnvironment.Count -gt 0) {
  throw "缺少 E2E 环境变量：$($missingE2eEnvironment -join ', ')"
}
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
$userJunit = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-user-e2e-{0}.xml" -f [guid]::NewGuid())
$env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $userJunit
npm.cmd run test:e2e -- --reporter=junit
if ($LASTEXITCODE -ne 0) { throw '创作端端到端测试失败' }
[xml]$userXml = Get-Content -LiteralPath $userJunit -Raw -Encoding UTF8
if ($userXml.SelectNodes('//testcase').Count -le 0) {
  throw '创作端 Playwright 测试数为 0'
}
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
$adminJunit = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-admin-e2e-{0}.xml" -f [guid]::NewGuid())
$env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $adminJunit
pnpm.cmd run test:e2e -- --reporter=junit
if ($LASTEXITCODE -ne 0) { throw '运营端端到端测试失败' }
[xml]$adminXml = Get-Content -LiteralPath $adminJunit -Raw -Encoding UTF8
if ($adminXml.SelectNodes('//testcase').Count -le 0) {
  throw '运营端 Playwright 测试数为 0'
}
Remove-Item Env:\PLAYWRIGHT_JUNIT_OUTPUT_FILE
```

预期：两份 JUnit（Java 单元测试报告格式）XML 都有非零 `testcase`，全部浏览器测试通过；失败时保留追踪和截图。

- [ ] **步骤 7（2–5 分钟）：精确提交端到端测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交双端端到端测试'
}
$expected = @(
  'ai-video-ui/ai-video-webapp/package.json'
  'ai-video-ui/ai-video-webapp/package-lock.json'
  'ai-video-ui/ai-video-webapp/playwright.config.ts'
  'ai-video-ui/ai-video-webapp/tests/e2e/global-setup.ts'
  'ai-video-ui/ai-video-webapp/tests/e2e/support.ts'
  'ai-video-ui/ai-video-webapp/tests/e2e/auth-isolation.spec.ts'
  'ai-video-ui/ai-video-webapp/tests/e2e/say-requirements.spec.ts'
  'ai-video-ui/ai-video-platform-ui/package.json'
  'ai-video-ui/ai-video-platform-ui/pnpm-lock.yaml'
  'ai-video-ui/ai-video-platform-ui/playwright.config.ts'
  'ai-video-ui/ai-video-platform-ui/tests/e2e/global-setup.ts'
  'ai-video-ui/ai-video-platform-ui/tests/e2e/support.ts'
  'ai-video-ui/ai-video-platform-ui/tests/e2e/app-identity-admin.spec.ts'
  'ai-video-ui/ai-video-platform-ui/tests/e2e/knowledge-and-billing.spec.ts'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存双端端到端测试失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与双端端到端测试清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "test: 覆盖说需求双端端到端流程"
if ($LASTEXITCODE -ne 0) { throw '提交双端端到端测试失败' }
```

### 任务 7：运行恢复、补偿和完整安全集成测试

**文件：**

- 修改：`ai-video-api/pom.xml`
- 创建：`ai-video-api/ai-video-integration-tests/pom.xml`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/resources/sql/integration-fixtures.sql`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/OrderedSqlBootstrap.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/IntegrationFixtureIds.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/DualApplicationHarness.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashFailpoint.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashStage.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/NoopAiVideoCrashFailpoint.java`
- 创建：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/ItProcessCrashFailpoint.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/infra/SnailJobAiTaskExecutionScanner.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/script/task/ScriptGenerateTaskHandler.java`
- 修改：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsHappyPathIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsRecoveryIT.java`
- 创建：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/CrossApplicationIsolationIT.java`

- [ ] **步骤 1（2–5 分钟）：建立位于两个启动应用下游的测试专用模块**

在根 `pom.xml` 的 `<modules>` 最后一项增加 `ai-video-integration-tests`。该模块只承载黑盒集成测试，不进入任一生产应用依赖图；其 `pom.xml` 必须：

- 以 `test` 范围依赖 `org.dromara:ai-video-user-api:${revision}`、`org.dromara:ruoyi-admin:${revision}` 与 `org.dromara:ruoyi-snailjob-server:${revision}`，从而建立两个业务启动应用和一个调度基础设施进程都先完成打包的 Reactor（Maven 多模块构建顺序）。SnailJob JAR 只是测试基础设施，不是第三个业务应用。
- 以 `test` 范围依赖 Spring Boot Test（Spring Boot 测试工具）、P0-A 导出的 `LocalIntegrationEnvironment` test-jar（测试归档）以及 `org.wiremock:wiremock-standalone:3.13.1`（独立 HTTP 模拟服务）；MySQL JDBC 和 Redis 客户端由既有依赖清单管理，不另造版本或容器测试依赖。
- 在 `pre-integration-test` 阶段由固定 `3.8.1` 版本的 `maven-dependency-plugin`（Maven 依赖复制插件）执行 `copy-dependencies`，设置 `includeArtifactIds=ai-video-user-api,ruoyi-admin,ruoyi-snailjob-server`、`includeScope=test`、`excludeTransitive=true`、`stripVersion=false` 和 `outputDirectory=${project.build.directory}/it-apps`，恰好复制三个可执行 JAR（Java 归档）；若任一 JAR 不存在、出现重复匹配或不是 Spring Boot 可执行包，阶段立即失败。
- 在 Failsafe 的 `systemPropertyVariables` 中把 `${maven.multiModuleProjectDirectory}` 以 `p4.repositoryRoot` 传入测试，把 `${project.build.directory}/it-apps` 以 `p4.itAppsDirectory` 传入；测试代码不得依赖当前工作目录猜测 SQL 或 JAR 路径。
- 由 Maven Failsafe（集成测试插件）只执行 `*IT`，继承 P0-A 的 `groups=dev`，并保持 `failIfNoTests=true`；不得把该模块包装成第三个 Spring Boot 应用。

`DualApplicationHarness` 固定提供下列能力，不允许各测试类自造未定义的 `fixture`：

```java
public final class DualApplicationHarness implements AutoCloseable {
    static DualApplicationHarness start();
    void assertSchedulerReady();
    AppSession loginAppUser();
    SysSession loginSysUser();
    DraftId createDraft(AppSession session);
    void answerUntilReady(DraftId draftId, int questionCount);
    void stubNextModelResponse(String fixtureKey);
    TaskId generateScripts(DraftId draftId);
    TaskTerminal awaitTerminal(TaskId taskId, Duration timeout);
    VersionId confirmCandidate(DraftId draftId, String candidateKey, long revision);
    DraftSnapshot loadDraft(DraftId draftId);
    List<String> settledOperations(DraftId draftId);
    RecoveryScenario crashAt(CrashPoint point);
    TaskTerminal awaitSchedulerRecovery(long rootTaskId, Duration timeout);
    long businessResultCount(long rootTaskId);
    List<BillingTerminalEntry> billingTerminalEntries(long rootTaskId);
    long minimumAvailableBalance();
    void awaitOutboxDelivery(long rootTaskId, Duration timeout);
    long outboxCount(long rootTaskId);
    long notificationCount(long rootTaskId);
    HttpResult callProtectedEndpoint(
        TargetApplication target, CredentialShape credential);
    List<CrossCredentialCase> crossApplicationCredentialCases();
    DualIdentity sameRawIdentifiersInBothApplications();
    void disableAppIdentity(long appUserId);
    boolean appSessionValid(String token);
    boolean sysSessionValid(String token);
    Set<String> userApiMappings();
    Set<String> platformMappings();
    void assertNoLeakedWork(Set<Long> allowedRootTaskIds);
}
```

上述类型都作为 `DualApplicationHarness` 的静态嵌套类型声明，不允许留给测试类自行发明：`AppSession(String token, String clientId, long appUserId)`、`SysSession(String token, String clientId, long sysUserId)`、`DraftId(long value)`、`TaskId(long value)`、`VersionId(long value)`、`TaskTerminal(String status, String failureCode)`、`DraftSnapshot(Long confirmedVersionId)`、`BillingTerminalEntry(long ledgerId, String type)`、`RecoveryScenario(long rootTaskId, CrashPoint point)`、`HttpResult(int statusCode, String body)`、`CredentialShape(Map<String,String> headers, List<HttpCookie> cookies)`、`CrossCredentialCase(TargetApplication target, CredentialShape credential, String caseName)`、`DualIdentity(long appUserId, long sysUserId, String appToken, String sysToken)`；`TargetApplication` 只含 `USER_API`、`PLATFORM_API`，`CrashPoint` 只含步骤 4 列出的五项。

`stubNextModelResponse` 必须从 Harness 内部由稳定 `fixtureKey` 映射到完整确定性 JSON，在创建相应业务任务之前向 WireMock 注册一次性、按 `[P4_FIXTURE:<fixtureKey>]` 匹配的响应；未知键立即失败，不让测试类另造 `validABCResponse` 等未声明 helper（辅助函数）。`awaitTerminal` 只通过用户公开任务查询接口轮询，禁止直接更新任务或调用测试端点。`answerUntilReady` 在每次提交上一题答案前注册下一题响应并等待真实调度器生成下一题，因此仍严格逐题生成，不预生成整份问卷。

三个 `*IT` 类都必须直接声明 `private static DualApplicationHarness fixture`，在 `@BeforeAll` 中赋值 `DualApplicationHarness.start()` 并调用 `assertSchedulerReady()`，在 `@AfterEach` 中调用 `assertNoLeakedWork(Set.of())`，在 `@AfterAll` 中调用 `close()`；不得依赖计划中未创建的测试基类或 JUnit 扩展。

- [ ] **步骤 2（2–5 分钟）：实现空库初始化、真实调度进程和确定性夹具**

`OrderedSqlBootstrap.ORDERED_SQL` 必须是下列不可变列表，路径以 `p4.repositoryRoot` 指向的 `ai-video-api` 根目录解析：

```java
static final List<String> ORDERED_SQL = List.of(
    "docs/sql/ry_vue.sql",
    "docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql",
    "docs/sql/ai-video/mysql/20260728_02_p0b_workspace_authorization.sql",
    "docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql",
    "docs/sql/ai-video/mysql/20260728_04_p0_seed.sql",
    "docs/sql/ai-video/mysql/20260728_05_p1_knowledge.sql",
    "docs/sql/ai-video/mysql/20260728_06_p2_questionnaire.sql",
    "docs/sql/ai-video/mysql/20260728_07_p3_script.sql",
    "docs/sql/ai-video/mysql/20260728_08_p4_integration.sql",
    "docs/sql/ry_job.sql",
    "docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql",
    "ai-video-integration-tests/src/test/resources/sql/integration-fixtures.sql"
);
```

启动任何 Java 子进程前，`bootstrap` 通过 `LocalIntegrationEnvironment` 校验并重置本机专用 `ai_video_test`，随后严格按“`ry_vue.sql` → 01～08 业务迁移 → `ry_job.sql` → SnailJob S1 生产任务登记 → 测试夹具”逐文件执行，支持 `DELIMITER` 过程块，首条 SQL 失败即停止并报告文件相对路径与语句序号，不输出 SQL 正文。它验证 01～08 的连续顺序以及 S1 的相对路径和 SHA-256 都与 `docs/sql/ai-video/mysql/README.md` 完全一致，并断言 `ORDERED_SQL` 中 `ry_job.sql` 恰好位于 08 之后、S1 之前，S1 又恰好位于测试夹具之前；不得把 S1 移到 08 后、`sj_job` 建表前执行。

为真实验证原子性和幂等升级，`OrderedSqlBootstrap` 在列表走到 S1 时执行一个固定探针，而不是普通执行器直接略过失败：

1. 在空的 `prod + ruoyi_group` 目标上创建测试临时 `BEFORE UPDATE` 触发器，只要更新 `labels='managed-by:ai-video'` 就以 `P4_IT_FORCE_S1_ROLLBACK` 发出 `SIGNAL`。以 `@aivideo_job_status=0` 执行 S1，必须收到这一个准确错误；在 `finally` 删除触发器，并断言两种 `biz_id` 的受管行总数为 0，证明两行插入已由过程 handler 整体回滚。
2. 以状态 `0` 正常执行 S1 两次，断言两个受管任务仍恰好各一条、总数为 2、配置完全符合脚本，证明重复登记不增行。
3. 对已有两行再次安装同一触发器，以状态 `1` 执行 S1 并期待同一准确错误；删除触发器后断言仍是两条完整的状态 `0` 记录，不允许一条启用、一条关闭或部分字段升级。最后把会话状态重置为 `0`。

所有预期失败都必须校验 SQLState 和错误码，其他数据库错误直接失败；触发器与遗留过程都在 `finally` 清理，禁止把测试钩子写入生产脚本。S1 路径在 `ORDERED_SQL` 只出现一次，也只写一条摘要。摘要文件 `target/p4-it-sql-sha256.txt` 按 `ry_vue.sql`、01～08、`ry_job.sql`、S1、测试夹具的准确顺序每个物理文件只记录一次；任一文件不存在、越出仓库根、摘要错误、重复编号、顺序不符、原子性探针失败、S1 重跑失败或行数漂移都不得启动进程。

`IntegrationFixtureIds` 固定并集中声明测试编号和凭据，不允许散落魔法值：`TENANT_ID=910000`、`APP_USER_ID=910001`、`SYS_USER_ID=910001`（故意使用相同原始数字验证身份域隔离）、`PERSONAL_WORKSPACE_ID=910010`、`ORGANIZATION_ID=910020`、`ORGANIZATION_WORKSPACE_ID=910021`、`QUOTA_ACCOUNT_ID=910030`、`DIRECTION_CATALOG_ID=910040`、`KNOWLEDGE_BATCH_ID=910050`、`APP_CLIENT_KEY=p4-it-app-client`、`SYS_CLIENT_KEY=p4-it-sys-client`、`FIXTURE_REVISION=p4-it-20260728-01`。测试专用明文密码只作为该类常量使用，SQL 只保存框架兼容哈希；该类和 SQL 都只位于 `ai-video-integration-tests`，不得写入 `20260728_04_p0_seed.sql`。

`integration-fixtures.sql` 必须可在全新测试库中确定性执行一次，并包含：

- 独立 `app`/`sys` 客户端、独立创作用户/运营用户；两身份可同号但没有跨域外键、角色或会话。
- 四个创作内置角色、准确权限映射、个人与组织工作区、owner/admin/member、资源授权，以及创作测试用户的个人角色。
- 已发布方向目录 `catalogVersion=1`、六类行业和 30/45/60/90/120 秒；至少覆盖 `ecommerce/product_service_intro`、`education/custom`、`home/custom`、`local/custom`。
- 额度价目、账户、初始可用额度和零锁定额；余额足够完成五次出题、一次生成和恢复重放。
- 已发布的最小 `copywriting` 知识版本、绑定、角度和视频类型规则，稳定代码与任务 5 的四条离线路由样本一致；`storyboard`、`production`、`raw_record` 仅可作为不参与路由的已审核夹具。
- 若模型适配器需要数据库提供商配置，只写启动后取得的回环 WireMock 基址和固定测试模型代码 `p4-it-model`，不写任何真实密钥。
- 测试专用 `p4_it_fixture_revision` 单行、所有稳定编号唯一。脚本末尾用 `SIGNAL SQLSTATE '45000'` 断言修订号、两身份同号但表不同、两个客户端类型不同、工作区/授权/额度/方向/知识的准确行数；任一事实不匹配立即失败。

同一夹具还必须精确登记 SnailJob 命名空间、组和两个执行器任务：

```sql
INSERT INTO sj_namespace (
  id, name, unique_id, description, deleted, create_dt, update_dt
) VALUES (
  91000, 'P4 Integration Test', 'p4-it', '仅 P4 集成测试', 0, NOW(), NOW()
);

INSERT INTO sj_group_config (
  id, namespace_id, group_name, description, token, group_status,
  version, group_partition, id_generator_mode, init_scene, create_dt, update_dt
) VALUES (
  91000, 'p4-it', 'p4_it_group', '仅 P4 集成测试',
  'SJ_P4_INTEGRATION_TEST_ONLY', 1, 1, 0, 1, 1, NOW(), NOW()
);

INSERT INTO sj_job (
  id, namespace_id, biz_id, group_name, job_name, args_str, args_type,
  next_trigger_at, job_status, task_type, route_key, executor_type,
  executor_info, trigger_type, trigger_interval, block_strategy,
  executor_timeout, max_retry_times, parallel_num, retry_interval,
  bucket_index, resident, notify_ids, owner_id, labels, description,
  ext_attrs, deleted, create_dt, update_dt
) VALUES
  (
    91001, 'p4-it', 'p4-it-task-scanner', 'p4_it_group',
    'P4 AI task scanner', NULL, 1,
    CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED),
    1, 1, 4, 1, 'aiVideoTaskExecutionScanner', 2, '1', 1,
    0, 0, 1, 0, 0, 0, '', NULL, 'p4-it', '每秒扫描任务',
    '', 0, NOW(), NOW()
  ),
  (
    91002, 'p4-it', 'p4-it-outbox-delivery', 'p4_it_group',
    'P4 outbox delivery', NULL, 1,
    CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000 AS UNSIGNED),
    1, 1, 4, 1, 'aiVideoOutboxDeliveryJob', 2, '1', 1,
    0, 0, 1, 0, 1, 0, '', NULL, 'p4-it', '每秒投递 Outbox',
    '', 0, NOW(), NOW()
  );
```

每次 P4 运行都由 `LocalIntegrationEnvironment` 在本机专用 `ai_video_test` 中先完成受限清理和重建，所以上述测试夹具使用普通 `INSERT`；出现重复键代表启动顺序或夹具污染，必须失败，不能 `ON DUPLICATE KEY UPDATE` 掩盖。

`DualApplicationHarness.start()` 的准确顺序固定为：

1. 先由 `LocalIntegrationEnvironment` 校验本机 MySQL 8 和 Redis 7、清理专用测试库及当前 Redis 前缀，再启动进程内 WireMock 并执行 `OrderedSqlBootstrap`；它不能安装、停止或重启本机数据库/Redis 服务。
2. 在 `p4.itAppsDirectory` 分别要求恰好一个 `ai-video-user-api-*.jar`、`ruoyi-admin-*.jar`、`ruoyi-snailjob-server-*.jar`，排除 `.original`，并验证每个 ZIP 都含 `org/springframework/boot/loader/launch/JarLauncher.class`。
3. 先以随机 HTTP 端口和随机 SnailJob 服务端端口执行 `java -jar ruoyi-snailjob-server-*.jar --spring.profiles.active=dev --server.port=<http> --snail-job.server-port=<grpc> --spring.datasource.url=<test-jdbc> --spring.datasource.username=<user> --spring.datasource.password=<password> --spring.boot.admin.client.enabled=false`；等待 `/snail-job/actuator/health` 返回 `UP` 且 gRPC（远程过程调用）端口可连接。
4. 再用两个随机且不同的业务 HTTP 端口和两个随机客户端端口启动用户端、运营端 JAR。正常、故障和恢复三种启动都必须显式传入 `--snail-job.enabled=true --snail-job.namespace=p4-it --snail-job.group=p4_it_group --snail-job.token=SJ_P4_INTEGRATION_TEST_ONLY --snail-job.server.host=127.0.0.1 --snail-job.server.port=<grpc> --snail-job.port=<本进程随机客户端端口>`，并注入同一测试数据库、Redis 和 WireMock；不得依赖 `application-dev.yml` 中默认的 `snail-job.enabled=false`，也不得只设置未证明已绑定的 `SNAIL_JOB_*` 环境变量。认证客户端配置仍分别只注入 `app` 与 `sys`。
5. 等待两个业务健康端点为 `UP`，随后轮询 `sj_job_task`：任务 `91001`、`91002` 都必须产生本次启动后的成功记录，`client_info` 非空，证明 `aiVideoTaskExecutionScanner` 与 `aiVideoOutboxDeliveryJob` 已注册且可调度；在此之前不得开始用例。两个业务应用同组注册，由 SnailJob 负载均衡；任务和 Outbox 自身的数据库条件领取负责并发正确性。

启动超时、进程提前退出、任意端口相同、任一执行器未调度或夹具断言失败都必须附三个子进程各最多 200 行脱敏日志尾部后失败。每个测试 `afterEach` 先调用 `assertNoLeakedWork(Set.of())`：轮询直到没有 `queued/running` 执行任务、没有 `pending/processing` Outbox、没有 `locked` 额度操作、没有活跃操作槽；恢复用例只能把当前参数显式传入 `allowedRootTaskIds`，并必须在测试结束前再次以空集合通过。`close()` 最后依次优雅终止两个业务进程、SnailJob 进程，再关闭 WireMock 并只清理当前 `runId` 的 Redis 前缀；不得停止本机 MySQL/Redis 服务、不得在同一个 Spring 上下文扫描两个业务应用，也不得直连 Controller 或直接写终态。

- [ ] **步骤 3（2–5 分钟）：编写完整成功链测试**

```java
@Tag("dev")
class SayRequirementsHappyPathIT {
@Test
void shouldCompleteFiveQuestionsAndConfirmOneOfThreeScripts() {
    AppSession session = fixture.loginAppUser();
    DraftId draft = fixture.createDraft(session);
    fixture.answerUntilReady(draft, 5);
    fixture.stubNextModelResponse("script-generate");
    TaskId generation = fixture.generateScripts(draft);
    assertThat(fixture.awaitTerminal(generation, Duration.ofSeconds(90)).status())
        .isEqualTo("success");
    VersionId confirmed = fixture.confirmCandidate(draft, "B", 1);
    assertThat(fixture.loadDraft(draft).confirmedVersionId()).isEqualTo(confirmed.value());
    assertThat(fixture.settledOperations(draft)).containsExactly(
        "question_generate", "question_generate", "question_generate",
        "question_generate", "question_generate", "script_generate"
    );
}
}
```

- [ ] **步骤 4（2–5 分钟）：编写可实现的进程崩溃、恢复和补偿测试**

```java
@Tag("dev")
class SayRequirementsRecoveryIT {
@ParameterizedTest
@EnumSource(CrashPoint.class)
void recoveryConvergesToOneResultAndOneBillingTerminal(CrashPoint point) {
    RecoveryScenario scenario = fixture.crashAt(point);

    TaskTerminal terminal = fixture.awaitSchedulerRecovery(
        scenario.rootTaskId(), Duration.ofSeconds(120));

    assertThat(terminal.status()).isIn("success", "failed");
    assertThat(fixture.businessResultCount(scenario.rootTaskId()))
        .isLessThanOrEqualTo(1);
    assertThat(fixture.billingTerminalEntries(scenario.rootTaskId()))
        .hasSize(1)
        .allMatch(entry -> Set.of("settle", "release").contains(entry.type()));
    assertThat(fixture.minimumAvailableBalance()).isGreaterThanOrEqualTo(0);
}

@Test
void outboxCrashIsRecoveredWithoutDuplicateNotification() {
    Long rootTaskId = fixture.crashAt(
        CrashPoint.AFTER_OUTBOX_BEFORE_DELIVERY).rootTaskId();
    fixture.awaitOutboxDelivery(rootTaskId, Duration.ofSeconds(120));
    assertThat(fixture.outboxCount(rootTaskId)).isEqualTo(1);
    assertThat(fixture.notificationCount(rootTaskId)).isEqualTo(1);
}
}
```

`CrashPoint` 对外固定包含 `AFTER_QUOTA_LOCK`、`AFTER_PROVIDER_SUCCESS`、`AFTER_RESULT_BEFORE_SETTLEMENT`、`AFTER_OUTBOX_BEFORE_DELIVERY`、`AFTER_TASK_LEASE_EXPIRED`。内部 hook（钩子）只能使用下列闭合端口和阶段：

```java
public enum AiVideoCrashStage {
    AFTER_QUOTA_LOCK,
    AFTER_PROVIDER_RESPONSE,
    AFTER_RESULT_INSERT,
    AFTER_TASK_LEASE_ACQUIRED,
    AFTER_OUTBOX_CLAIM
}

public interface AiVideoCrashFailpoint {
    void hit(AiVideoCrashStage stage, String correlationKey);
}
```

- `AiTaskServiceImpl.createChargeableTask` 在 `QuotaBillingService.lock` 返回后、写根任务前调用 `AFTER_QUOTA_LOCK`。
- `ScriptGenerateTaskHandler` 在模型 HTTP 成功并完成响应解析后、开启结果事务前调用 `AFTER_PROVIDER_RESPONSE`。
- `AiTaskServiceImpl.markSuccess` 参与 Handler 的结果事务；结果版本已插入后、调用 `QuotaBillingService.settle` 前调用 `AFTER_RESULT_INSERT`，因此进程死亡会让结果、终态、结算、清槽和 Outbox 同时回滚。
- `SnailJobAiTaskExecutionScanner` 在领取事务提交、发布 `AiTaskExecutionReadyEvent` 前调用 `AFTER_TASK_LEASE_ACQUIRED`。对外场景 `AFTER_TASK_LEASE_EXPIRED` 在子进程退出后轮询数据库直到真实 `lease_expires_at < CURRENT_TIMESTAMP(3)`，再重启；禁止直接把租约时间改成过期。
- `OutboxDeliveryJob` 在 `claimPending` 提交后、调用 `deliverClaimed` 前调用 `AFTER_OUTBOX_CLAIM`，对应 `AFTER_OUTBOX_BEFORE_DELIVERY`。

`NoopAiVideoCrashFailpoint` 在非 `it-crash` profile（测试崩溃配置）中是唯一实现且永远无动作。`ItProcessCrashFailpoint` 只在 `it-crash` profile 装配，并在启动时同时校验：`AIVIDEO_IT_ALLOW_PROCESS_HALT=I_UNDERSTAND_LOCAL_TEST_ONLY`、数据库名精确为 `ai_video_test`、模型基址为回环地址、`AIVIDEO_IT_RUN_ID` 非空、marker（一次性标记文件）规范路径位于 `${java.io.tmpdir}/ai-video-p4-it/<runId>`。任一条件不满足即拒绝启动；它没有 Controller、HTTP 路由或远程开关。命中 `AIVIDEO_IT_FAILPOINT_STAGE + AIVIDEO_IT_CORRELATION_KEY` 时用 `Files.createFile` 原子创建 marker，只有首次创建成功者调用 `Runtime.getRuntime().halt(137)`，重放不会再次崩溃。

`DualApplicationHarness.crashAt` 必须先注册模型响应，再通过公开业务 API 创建/推进任务；故障进程固定以 `--spring.profiles.active=dev,it-crash` 启动，并等待 marker 出现和目标业务子进程以 `137` 退出。`AFTER_QUOTA_LOCK` 因未提交事务不会留下任务，Harness 重启同一 JAR 后以同一幂等键重放公开请求，并把重放返回的根任务作为场景编号；其他场景使用数据库中已提交的根任务。恢复进程固定只以 `--spring.profiles.active=dev` 启动，且删除全部 failpoint 环境变量，继续使用同一 MySQL、Redis、SnailJob 与 WireMock，并等待真实 `aiVideoTaskExecutionScanner`/`aiVideoOutboxDeliveryJob` 收敛。不得新增测试专用生产端点、直接调用扫描器、直接写终态或用串行 Mock（串行模拟对象）代替多进程；每种情况最终只能有一份业务结果和一次结算或一次释放。

- [ ] **步骤 5（2–5 分钟）：编写跨应用安全测试**

```java
@Tag("dev")
class CrossApplicationIsolationIT {
@Test
void rejectsEveryCrossApplicationCredential() {
    List<CrossCredentialCase> cases = fixture.crossApplicationCredentialCases();
    assertThat(cases).isNotEmpty();
    for (CrossCredentialCase testCase : cases) {
        HttpResult response = fixture.callProtectedEndpoint(
            testCase.target(), testCase.credential());
        assertThat(response.statusCode())
            .as(testCase.caseName())
            .isEqualTo(401);
        assertThat(response.body()).doesNotContain("userId", "tenantId", "token");
    }
}

@Test
void revokingOneNamespaceDoesNotAffectTheOther() {
    DualIdentity identity = fixture.sameRawIdentifiersInBothApplications();
    fixture.disableAppIdentity(identity.appUserId());
    assertThat(fixture.appSessionValid(identity.appToken())).isFalse();
    assertThat(fixture.sysSessionValid(identity.sysToken())).isTrue();
}

@Test
void applicationContextsDoNotAssembleOppositeControllers() {
    assertThat(fixture.userApiMappings())
        .noneMatch(path -> path.startsWith("/api/admin/"));
    assertThat(fixture.platformMappings())
        .noneMatch(path -> path.startsWith("/api/studio/"));
}
}
```

`crossApplicationCredentialCases` 必须返回非空列表，并包含相同用户名/密码/数字编号/同名权限、交换客户端键、重复认证头、逗号拼接认证头、Cookie 与头混合、原始会话值相同等独立参数；测试先断言列表非空，再逐条断言。另行断言一端强踢、改密或停用不影响另一端，运营数据权限不扩大创作查询。

- [ ] **步骤 6（2–5 分钟）：运行三个 `*IT` 并断言确实执行**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ai-video-integration-tests -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=SayRequirementsHappyPathIT,SayRequirementsRecoveryIT,CrossApplicationIsolationIT verify
if ($LASTEXITCODE -ne 0) { throw '跨应用集成测试失败' }
$expected = @(
  'SayRequirementsHappyPathIT',
  'SayRequirementsRecoveryIT',
  'CrossApplicationIsolationIT'
)
foreach ($className in $expected) {
  $report = Get-ChildItem `
      -Path .\ai-video-integration-tests\target\failsafe-reports `
      -Filter "TEST-*$className.xml" |
    Where-Object { $_.LastWriteTime -ge $testStartedAt } |
    Select-Object -First 1
  if ($null -eq $report) { throw "$className 未产生本次 Failsafe 报告" }
  [xml]$xml = Get-Content -LiteralPath $report.FullName
  $suite = $xml.testsuite
  if ([int]$suite.tests -le 0 -or [int]$suite.failures -ne 0 -or
      [int]$suite.errors -ne 0 -or [int]$suite.skipped -eq [int]$suite.tests) {
    throw "$className 未执行、全部跳过或存在失败"
  }
}
```

预期：`BUILD SUCCESS`，零失败，三个 Failsafe XML 报告都包含非零测试数。

- [ ] **步骤 7（2–5 分钟）：精确提交集成测试**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交跨进程集成测试'
}
$expected = @(
  'ai-video-api/pom.xml'
  'ai-video-api/ai-video-integration-tests/pom.xml'
  'ai-video-api/ai-video-integration-tests/src/test/resources/sql/integration-fixtures.sql'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/OrderedSqlBootstrap.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/IntegrationFixtureIds.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/support/DualApplicationHarness.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashFailpoint.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/testing/AiVideoCrashStage.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/NoopAiVideoCrashFailpoint.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/testing/ItProcessCrashFailpoint.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/application/impl/AiTaskServiceImpl.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/task/infra/SnailJobAiTaskExecutionScanner.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/script/task/ScriptGenerateTaskHandler.java'
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/notification/job/OutboxDeliveryJob.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsHappyPathIT.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsRecoveryIT.java'
  'ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/CrossApplicationIsolationIT.java'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存跨进程集成测试失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与跨进程集成测试清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "test: 覆盖说需求恢复与越权场景"
if ($LASTEXITCODE -ne 0) { throw '提交跨进程集成测试失败' }
```

### 任务 8：编写部署、灰度和回退手册

**文件：**

- 创建：`docs/runbooks/say-requirements-deployment.md`
- 创建：`docs/runbooks/say-requirements-rollback.md`
- 修改：`docs/runbooks/say-requirements-observability.md`

- [ ] **步骤 1（2–5 分钟）：编写部署顺序**

部署手册固定顺序：

1. 分别备份并校验业务数据库和 SnailJob 调度数据库。
2. 关闭新功能开关。
3. 连接业务数据库，按 01～08 执行 SQL 并保存摘要和验证查询结果。
4. 连接 SnailJob 调度数据库，先验证 `sj_namespace`、`sj_group_config`、`sj_job` 已由当前基线建立；已有调度库禁止重跑 `docs/sql/ry_job.sql`。设置准确的 `@aivideo_job_namespace`、`@aivideo_job_group`、`@aivideo_job_status=0`，独立执行 `docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql` 两次，保存 S1 摘要，并断言两个稳定 `biz_id` 各一条且关闭。
5. 部署只含管理能力的后端和运营端页面，开关仍关闭。
6. 验证 `app`/`sys` 双向隔离、账号管理、方向和知识配置。
7. 部署用户端后端和创作端页面，开关仍关闭；用户端与运营端都设置 `AIVIDEO_SNAIL_JOB_ENABLED=true`，并使用和 S1 完全相同的命名空间、组、非空生产令牌与服务端地址，各进程使用不冲突的客户端端口。
8. 等待两个业务应用健康且在 SnailJob 中出现对应客户端执行器；任一应用仍报告客户端关闭、令牌错误或执行器缺失都停止上线。
9. 再次连接 SnailJob 调度数据库，以 `@aivideo_job_status=1` 执行同一 S1 脚本；断言 `ai-video-task-execution-scanner` 与 `ai-video-outbox-delivery` 均启用，并在 `sj_job_task` 中各产生部署后的成功记录。
10. 用模拟提供商执行冒烟测试，并验证一条业务任务和一条 Outbox 事件都由真实调度收敛。
11. 配置生产模型密钥但保持用户灰度为 0%。
12. 加入内部创作用户白名单。
13. 依次扩大到 5%、25%、50%、100%，每档至少观察 30 分钟并检查运行手册指标。

- [ ] **步骤 2（2–5 分钟）：编写回退规则**

回退手册明确：

- 先把新任务创建开关关闭，不取消已运行任务。
- 应用代码回退不删除表、不回滚不可变流水、不覆盖已发布知识。
- 知识或提示词异常时发布回上一已发布版本，只影响新任务。
- 额度异常先停止新收费任务，再用补偿流水修正，禁止直接更新余额。
- 身份越权、跨会话读取或错误客户端被接受时立即停止全部用户端流量并撤销相关 `app` 会话。
- 数据库脚本失败时停止后续编号；只执行脚本中经过审查的反向 DDL（数据定义语言），不得自动删除含业务数据的表。
- S1 登记或升级冲突时保留原 `sj_job` 和全部调度历史，停止后续上线并人工审查，禁止删除后重建或重跑 `ry_job.sql`。
- 一般应用回退先关闭三个新任务创建开关，但保持两个调度任务和 SnailJob 客户端运行，直到已有 `queued/running` 业务任务及 `pending/processing` Outbox 全部排空；只有确认无待恢复工作后，才允许以受审计的 S1 参数把两个任务设为 `job_status=0`。恢复旧调度配置必须执行上一已发布的独立登记脚本，不直接改表。

- [ ] **步骤 3（2–5 分钟）：建立桌面演练记录表**

在部署手册中为每一步建立固定列：演练编号、角色、准确命令、输入来源、预期输出、停止条件、恢复联系人、开始/结束时间和证据链接。安全、后端、前端和联调角色分别签名；任何字段为空即不能上线。

- [ ] **步骤 4（2–5 分钟）：演练安全与后端切片**

安全角色核对双向身份隔离、令牌撤销和紧急停流；后端角色核对业务 SQL 与 S1 的独立摘要和数据源、S1 双次执行幂等性、两个生产任务调度记录、Outbox 租约、任务恢复、额度补偿与三开关。每次只执行一个 2～5 分钟切片并立即记录结果；无法在 5 分钟内完成的动作继续拆分，不能用“已整体演练”代替证据。

- [ ] **步骤 5（2–5 分钟）：演练前端与联调切片**

前端角色核对两个登录域、加载/空/失败/权限/分页状态和开关关闭反馈；联调角色核对 `fenjing` 导入、模型模拟服务、通知、任务中心与费用页。每个切片保存命令退出码、截图或测试报告路径，失败立即按停止条件回退。

- [ ] **步骤 6（2–5 分钟）：精确提交运行手册**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交运行手册'
}
$expected = @(
  'docs/runbooks/say-requirements-deployment.md'
  'docs/runbooks/say-requirements-rollback.md'
  'docs/runbooks/say-requirements-observability.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存部署、回退和可观测手册失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与运行手册清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "docs: 增加说需求部署与回退手册"
if ($LASTEXITCODE -ne 0) { throw '提交运行手册失败' }
```

### 任务 9：运行全仓最终门禁

**文件：**

- 创建：`docs/runbooks/say-requirements-acceptance.md`
- 验证：`scripts/validate-development-standards.ps1`
- 验证：`scripts/knowledge/validate-fenjing-manifest.ps1`
- 验证：`docs/sql/ai-video/snailjob/20260728_01_say_requirements_jobs.sql`
- 验证：`ai-video-api/pom.xml` 聚合的单元测试
- 验证：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/notification/NotificationOutboxIT.java`
- 验证：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/studio/ScriptDraftServiceIT.java`
- 验证：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/ScriptGenerationBillingIT.java`
- 验证：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsHappyPathIT.java`
- 验证：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/SayRequirementsRecoveryIT.java`
- 验证：`ai-video-api/ai-video-integration-tests/src/test/java/org/dromara/aivideo/integration/CrossApplicationIsolationIT.java`
- 验证：`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/integration/FenjingKnowledgeRoutingIT.java`
- 验证：`ai-video-ui/ai-video-webapp/package.json` 的 `test`、`lint`、`build`、`test:e2e`
- 验证：`ai-video-ui/ai-video-platform-ui/package.json` 的 `test`、`lint`、`build:prod`、`test:e2e`

- [ ] **步骤 1（2–5 分钟）：创建验收证据表并运行文档检查**

`say-requirements-acceptance.md` 逐条记录：准确命令、开始/结束时间、退出码、测试数、报告路径、业务 SQL 与 SnailJob S1/清单/ZIP 的 SHA-256、S1 目标数据源和命名空间/组、两条生产任务的精确行数与部署后成功调度证据、导入批次编号、发布知识版本编号、环境名、执行人和停止条件；不得写令牌、密码、模型请求或正文。

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '开发规范检查失败' }
git diff --check
if ($LASTEXITCODE -ne 0) { throw '差异空白检查失败' }
```

预期：输出 `DEVELOPMENT_STANDARDS_OK`，差异检查无输出。

- [ ] **步骤 2（2–5 分钟）：运行后端单元测试并断言非零**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -Dmaven.test.skip=false -DskipTests=false -DskipITs=true test
if ($LASTEXITCODE -ne 0) { throw '全仓单元测试失败' }
$unitReports = Get-ChildItem -Path . -Recurse -Filter 'TEST-*.xml' |
  Where-Object {
    $_.LastWriteTime -ge $testStartedAt -and
    $_.FullName -match 'surefire-reports'
  } |
  Select-String -Pattern 'tests="[1-9][0-9]*"'
if ($unitReports.Count -eq 0) { throw '全仓单元测试执行数为 0' }
```

预期：`BUILD SUCCESS`、零失败，至少一个 Surefire XML 报告测试数非零。

- [ ] **步骤 3（2–5 分钟）：运行七个后端 `*IT` 并断言非零**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-core -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=ScriptDraftServiceIT verify
if ($LASTEXITCODE -ne 0) { throw '核心模块集成测试失败' }
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=NotificationOutboxIT,ScriptGenerationBillingIT,FenjingKnowledgeRoutingIT verify
if ($LASTEXITCODE -ne 0) { throw '基础设施模块集成测试失败' }
.\mvnw.cmd -pl ai-video-integration-tests -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=SayRequirementsHappyPathIT,SayRequirementsRecoveryIT,CrossApplicationIsolationIT verify
if ($LASTEXITCODE -ne 0) { throw '跨应用集成测试失败' }
$expectedReports = @{
  '.\ruoyi-modules\ai-video\ai-video-core\target\failsafe-reports' =
    @('ScriptDraftServiceIT')
  '.\ruoyi-modules\ai-video\ai-video-infra\target\failsafe-reports' =
    @('NotificationOutboxIT', 'ScriptGenerationBillingIT', 'FenjingKnowledgeRoutingIT')
  '.\ai-video-integration-tests\target\failsafe-reports' =
    @('SayRequirementsHappyPathIT', 'SayRequirementsRecoveryIT', 'CrossApplicationIsolationIT')
}
foreach ($reportDirectory in $expectedReports.Keys) {
  foreach ($className in $expectedReports[$reportDirectory]) {
    $report = Get-ChildItem -Path $reportDirectory -Filter "TEST-*$className.xml" |
      Where-Object { $_.LastWriteTime -ge $testStartedAt } |
      Select-Object -First 1
    if ($null -eq $report) { throw "$className 未产生本次 Failsafe 报告" }
    [xml]$xml = Get-Content -LiteralPath $report.FullName
    $suite = $xml.testsuite
    if ([int]$suite.tests -le 0 -or [int]$suite.failures -ne 0 -or
        [int]$suite.errors -ne 0 -or
        [int]$suite.skipped -eq [int]$suite.tests) {
      throw "$className 未执行、全部跳过或存在失败"
    }
  }
}
```

预期：`BUILD SUCCESS`、零失败，七个 `*IT` 都有非零测试数；禁止用 `-Dtest` 或 `-Dgroups=integration` 代替 Failsafe 的 `-Dit.test`。

- [ ] **步骤 4（2–5 分钟）：运行创作端完整验证**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-webapp
$vitestReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-final-user-vitest-{0}.json" -f [guid]::NewGuid())
npm.cmd test -- --reporter=json --outputFile=$vitestReport
if ($LASTEXITCODE -ne 0) { throw '创作端单元测试失败' }
$vitest = Get-Content -LiteralPath $vitestReport -Raw -Encoding UTF8 |
  ConvertFrom-Json
if ([int]$vitest.numTotalTests -le 0 -or
    [int]$vitest.numFailedTests -ne 0 -or
    [int]$vitest.numPassedTests -le 0) {
  throw '创作端 Vitest 测试数为 0、没有通过用例或存在失败'
}
npm.cmd run lint
if ($LASTEXITCODE -ne 0) { throw '创作端代码检查失败' }
npm.cmd run build
if ($LASTEXITCODE -ne 0) { throw '创作端构建失败' }
$e2eReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-final-user-e2e-{0}.xml" -f [guid]::NewGuid())
$env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $e2eReport
npm.cmd run test:e2e -- --reporter=junit
if ($LASTEXITCODE -ne 0) { throw '创作端端到端测试失败' }
[xml]$e2e = Get-Content -LiteralPath $e2eReport -Raw -Encoding UTF8
if ($e2e.SelectNodes('//testcase').Count -le 0) {
  throw '创作端 Playwright 测试数为 0'
}
Remove-Item Env:\PLAYWRIGHT_JUNIT_OUTPUT_FILE
```

预期：四条命令退出码为 `0`，Vitest 与 Playwright 输出的测试数均大于 `0`。

- [ ] **步骤 5（2–5 分钟）：运行运营端完整验证**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-ui\ai-video-platform-ui
$vitestReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-final-admin-vitest-{0}.json" -f [guid]::NewGuid())
pnpm.cmd test -- --reporter=json --outputFile=$vitestReport
if ($LASTEXITCODE -ne 0) { throw '运营端单元测试失败' }
$vitest = Get-Content -LiteralPath $vitestReport -Raw -Encoding UTF8 |
  ConvertFrom-Json
if ([int]$vitest.numTotalTests -le 0 -or
    [int]$vitest.numFailedTests -ne 0 -or
    [int]$vitest.numPassedTests -le 0) {
  throw '运营端 Vitest 测试数为 0、没有通过用例或存在失败'
}
pnpm.cmd lint
if ($LASTEXITCODE -ne 0) { throw '运营端代码检查失败' }
pnpm.cmd build:prod
if ($LASTEXITCODE -ne 0) { throw '运营端构建失败' }
$e2eReport = Join-Path ([IO.Path]::GetTempPath()) (
  "p4-final-admin-e2e-{0}.xml" -f [guid]::NewGuid())
$env:PLAYWRIGHT_JUNIT_OUTPUT_FILE = $e2eReport
pnpm.cmd run test:e2e -- --reporter=junit
if ($LASTEXITCODE -ne 0) { throw '运营端端到端测试失败' }
[xml]$e2e = Get-Content -LiteralPath $e2eReport -Raw -Encoding UTF8
if ($e2e.SelectNodes('//testcase').Count -le 0) {
  throw '运营端 Playwright 测试数为 0'
}
Remove-Item Env:\PLAYWRIGHT_JUNIT_OUTPUT_FILE
```

预期：四条命令退出码为 `0`，Vitest 与 Playwright 输出的测试数均大于 `0`；沿用 P0-A 的唯一 Vitest 配置，不生成第二份配置或初始化文件。

- [ ] **步骤 6（2–5 分钟）：复验 `fenjing` 清单和发布后路由**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\scripts\knowledge\validate-fenjing-manifest.ps1 `
  -SourceRoot "D:\Workspace\ai\projects\文案\fenjing" `
  -ManifestFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.json" `
  -HashFile ".\ai-video-api\script\data\knowledge\fenjing\manifest.sha256"
if ($LASTEXITCODE -ne 0) { throw 'fenjing 清单复验失败' }
Set-Location D:\Workspace\ai\projects\ai-video\ai-video-api
$testStartedAt = Get-Date
.\mvnw.cmd -pl ruoyi-modules/ai-video/ai-video-infra -am `
  -Dmaven.test.skip=false -DskipTests=false -DskipITs=false `
  -Dfailsafe.failIfNoSpecifiedTests=false `
  -Dit.test=FenjingKnowledgeRoutingIT verify
if ($LASTEXITCODE -ne 0) { throw 'FenjingKnowledgeRoutingIT 复验失败' }
$report = Get-ChildItem `
    -Path .\ruoyi-modules\ai-video\ai-video-infra\target\failsafe-reports `
    -Filter 'TEST-*FenjingKnowledgeRoutingIT.xml' |
  Where-Object { $_.LastWriteTime -ge $testStartedAt } |
  Select-Object -First 1
if ($null -eq $report) { throw 'FenjingKnowledgeRoutingIT 未产生本次报告' }
[xml]$xml = Get-Content -LiteralPath $report.FullName
if ([int]$xml.testsuite.tests -le 0 -or
    [int]$xml.testsuite.failures -ne 0 -or
    [int]$xml.testsuite.errors -ne 0 -or
    [int]$xml.testsuite.skipped -eq [int]$xml.testsuite.tests) {
  throw 'FenjingKnowledgeRoutingIT 未执行、全部跳过或存在失败'
}
```

预期：`MANIFEST_VALID`，源快照不变，路由验收 `BUILD SUCCESS` 且测试数非零。

- [ ] **步骤 7（2–5 分钟）：核对规格关键字、准确证据和工作树**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$aiVideoTests = Get-ChildItem -Path .\ai-video-api -Recurse -Filter '*.java' |
  Where-Object {
    $_.FullName -match '\\src\\test\\java\\org\\dromara\\aivideo\\' -and
    $_.Name -match '(Test|IT)\.java$'
  }
if ($aiVideoTests.Count -eq 0) { throw '没有发现 AI 视频 JUnit 测试' }
foreach ($testFile in $aiVideoTests) {
  $source = Get-Content -LiteralPath $testFile.FullName -Raw
  if ($source -notmatch '@Tag\("dev"\)') {
    throw "缺少类级 @Tag(`"dev`"): $($testFile.FullName)"
  }
}
rg -n "P0-A|P0-B|每答一题|自定义|第 5 题|A/B/C|三个标题|不可变|详细流水|双向隔离" docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md
if ($LASTEXITCODE -ne 0) { throw '规格关键字检查失败' }
rg -n "BUILD SUCCESS|MANIFEST_VALID|tests=|SHA-256|批次|版本" docs/runbooks/say-requirements-acceptance.md
if ($LASTEXITCODE -ne 0) { throw '验收证据关键字检查失败' }
$worktree = @(git status --porcelain=v1 --untracked-files=all)
if ($LASTEXITCODE -ne 0) { throw '读取工作树状态失败' }
if ($worktree.Count -ne 1 -or
    $worktree[0] -notmatch '^( M|\?\?) docs/runbooks/say-requirements-acceptance\.md$') {
  $worktree | ForEach-Object { Write-Host $_ }
  throw '工作树必须只剩未暂存的最终验收记录'
}
$stagedBeforeAcceptance = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
if ($stagedBeforeAcceptance.Count -ne 0) {
  throw '最终验收提交前不允许已有暂存文件'
}
```

预期：规格关键约束和每类证据均可定位；工作树只剩 `docs/runbooks/say-requirements-acceptance.md` 的本次验收记录。若还有其他文件，逐项核对归属并停止提交，禁止扩大暂存范围。

- [ ] **步骤 8（2–5 分钟）：只提交最终验收记录**

```powershell
Set-Location D:\Workspace\ai\projects\ai-video
$stagedBefore = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存区初始状态失败' }
if ($stagedBefore.Count -ne 0) {
  $stagedBefore | Sort-Object | Out-String | Write-Host
  throw '暂存区非空；请先处理既有暂存内容，再提交最终验收记录'
}
$expected = @(
  'docs/runbooks/say-requirements-acceptance.md'
)
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '暂存最终验收记录失败' }
$actual = @(git diff --cached --name-only --)
if ($LASTEXITCODE -ne 0) { throw '读取暂存文件清单失败' }
$difference = Compare-Object `
  -ReferenceObject @($expected | Sort-Object) `
  -DifferenceObject @($actual | Sort-Object)
if ($difference) {
  $difference | Format-Table -AutoSize | Out-String | Write-Host
  throw '暂存文件集合与最终验收记录清单不一致'
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异检查失败' }
git commit -m "test: 完成说需求生产验收"
if ($LASTEXITCODE -ne 0) { throw '提交最终验收记录失败' }
```
