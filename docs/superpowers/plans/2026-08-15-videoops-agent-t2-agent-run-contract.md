# T2 可恢复 AgentRun 持久契约实现计划
> **面向 AI 代理的工作者：** 按本文逐项施工；只实现版本化交付契约与可恢复运行状态，不提前建设 Planner、工具调用或 UI。

**目标：** 用当前 `ai-video-core` 与独立 MySQL bootstrap，落地版本化 `DeliveryBrief`、版本化 `AcceptanceProfile` 和可恢复 `AgentRun`，并证明归属、幂等、重启恢复及迟到结果隔离。

**架构：** 新增两个不可变版本快照表和一个 AgentRun 当前状态表。AgentRun 只保存控制面状态并引用已有 `av_ai_task` / `av_dh_generation_job` 的任务事实；不复制任务执行模型，不调用 Provider。

**技术栈：** Java 21、Spring Service、MyBatis-Plus、MySQL 8.4、JUnit 5、Mockito、Pester、仓库既有 bootstrap validator。

---

## 用户可见结果

当前源码可持久化一份明确版本的交付目标和验收偏好，并创建一个归属正确、可在进程重启后领取同一身份继续执行的 AgentRun；旧租约或旧任务结果不能覆盖当前状态。

## 非目标

- 不做 `/agent` 页面、聊天 UI、Planner、PlanStep、ToolInvocation、RunEvent。
- 不做质量评分、返工执行、多 Agent、通用记忆/规则平台。
- 不调用 Provider，不改写 `av_ai_task*` 或 `av_dh_generation_job`。
- 不建立通用迁移框架，不改生产配置，不执行真实业务生成。

## 验收信号

1. Brief/Profile 只能追加版本；同 owner 幂等回放稳定，异摘要冲突，跨 owner 不可见。
2. AgentRun 以唯一 owner/key 创建；进程重建后过期租约领取同一 run，租约代次和行版本前进。
3. 旧 token、旧 lease generation、错误 task、旧 contract revision 或终态写回均影响 0 行；当前精确结果只推进一次。

## 停止条件

- schema、owner、幂等或 CAS 任一边界无法 fail-closed，立即停在首个根因，不以 Mock 或文档替代。
- 本机真实 IT 认证不可安全取得时，保留已通过的静态与单元证据并标记 `BLOCKED`，不得改连开发库。
- 实现若需要第四张表、现有任务重写、Provider、Controller 或前端，停止扩张并记录到后续阶段。

## 冻结修改面

### 生产 Java

- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/agent/domain/DeliveryBriefVersion.java`
- `.../agent/domain/AcceptanceProfileVersion.java`
- `.../agent/domain/AgentRun.java`
- `.../agent/enums/AgentRunStatus.java`
- `.../agent/mapper/DeliveryBriefVersionMapper.java`
- `.../agent/mapper/AcceptanceProfileVersionMapper.java`
- `.../agent/mapper/AgentRunMapper.java`
- `.../agent/service/IAgentRunService.java`
- `.../agent/service/impl/AgentRunServiceImpl.java`

### 数据库与文档

- `docs/sql/videoops-agent/mysql/100_agent_run_schema.sql`
- `docs/sql/videoops-agent/mysql/bootstrap-manifest.json`
- `scripts/validate-videoops-database-bootstrap.ps1`
- `docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`
- `docs/DOMAIN_MODEL.md`
- `docs/EXECUTION.md`

### 测试

- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/agent/service/impl/AgentRunServiceImplTest.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/agent/AgentRunPersistenceIT.java`
- 仅在现有 validator 不能覆盖新增步骤时，最小调整其现有 Pester 测试。

若实现需要 Controller、前端、Provider、第四张表或第二个无直接调用关系的模块，停止扩张并记录后续阶段。

## 数据契约

### `av_delivery_brief_version`

- 稳定 `brief_id` + 单调 `version_no` + `parent_version_id` 形成 append-only 版本链。
- `schema_version=delivery-brief-1`，首期 `delivery_type=image_to_digital_human_video`。
- 保存规范化 `brief_json`、SHA-256、写入幂等键/请求摘要和 app actor 审计列。
- 唯一约束：`owner + brief_id + version_no`、`owner + idempotency_key`。

### `av_acceptance_profile_version`

- 稳定 `acceptance_profile_id` + 单调版本链，精确绑定一个同 owner 的 Brief 版本。
- `schema_version=acceptance-profile-1`；主观项只是版本化偏好，不引入未经校准的自动通过阈值。
- 保存规范化 `profile_json`、SHA-256、幂等键/请求摘要和 app actor 审计列。

### `av_agent_run`

- 固定引用 Brief/Profile 版本；T2 当前创建时 `contract_revision=1`，不提供运行中切换契约入口。后续若增加切换能力，必须以 CAS 增长该修订号。
- 状态只覆盖 T2：`queued`、`running`、`waiting_external_task`、`completed`、`failed`、`cancelled`。
- 恢复字段：`row_version`、`lease_generation`、`lease_owner`、`lease_token_digest`、`lease_expires_at`、`resume_after`。
- 等待字段：既有任务来源、任务 ID、等待时的 contract revision；不复制 Provider 原始状态。
- 终态不可逆；completed 必须带候选资产。等待和结果 CAS 原子核对既有任务与资产事实；`finishLease(completed)` 只允许采用一份 owned + ready、未删除的既有视频输出。所有读取和 CAS 均包含 `owner_user_id`。

## 实施步骤

### 1. 冻结 schema 与 bootstrap

新增 `100_agent_run_schema.sql`：先精确校验 `DATABASE()='videoops_agent_dev'`，只创建上述三表并在尾部核对列、索引和 CHECK。将 `100` 插入 manifest 的 `090` 与 `900` 之间，更新 SHA 和 validator 的固定顺序；seed 与白名单保持不变。

检查：

```powershell
pwsh -NoProfile -File scripts/validate-videoops-database-bootstrap.ps1
Invoke-Pester -Script scripts/tests/validate-videoops-database-bootstrap.Tests.ps1 -PassThru
```

### 2. 实现版本与幂等

三个 Entity 均继承 `BaseEntity`、标注 `@AppAuditRequired`，主键使用 `IdType.INPUT`。Service 从 `AppPrincipalSnapshotDTO` 取得 owner，不接受调用方提交 owner/actor/hash。

- Brief/Profile JSON 在服务端解析为 JSON 后稳定序列化并计算 SHA-256。
- 同 owner/key + 同 digest 返回原行；同 key + 异 digest抛幂等冲突。
- 新版本只插入，父版本和 versionNo 必须属于同 owner；并发由唯一键仲裁后重读 winner。
- Profile 引用的 Brief 版本必须同 owner；跨 owner 统一表现为不存在。

### 3. 实现 AgentRun CAS 与恢复

- 创建 Run 时校验冻结的 Brief/Profile 版本对；同 owner/key 按摘要回放。
- 领取只接受 queued、lease 已过期的 running run，或恢复时间和 lease 都已到期的 waiting run；重新领取保持同一 run ID，增加 `lease_generation` 和 `row_version`，只保存 token digest。waiting 恢复仍保持 waiting 和原任务身份，不伪造一次 running 状态切换。
- 等待外部任务前，在同一 CAS 中确认任务属于当前 owner、类型正确且处于可等待状态，再持久化任务来源/ID、当前 contract revision 与恢复时间；保留当前租约摘要作为结果 fence，到期恢复时在同一 run 上旋转 generation/token。
- 写回必须匹配 owner、run、状态、rowVersion、leaseGeneration、tokenDigest、task source/id 和 contract revision，并原子确认任务成功及候选资产归属、ready、未删除、来源关系；影响 0 行即返回 stale，不继续副作用。
- completed/failed/cancelled 不得再次领取或回退。

### 4. 最小边界证明

单元测试至少覆盖：

- 版本追加、同摘要回放、异摘要冲突、跨 owner 拒绝。
- run 幂等与所有 Mapper wrapper/CAS 都带 owner。
- 未过期租约不能抢，过期租约复用同一 run，旧 token/result 更新为 0。
- 不存在、越权、错误类型/状态的任务不能进入等待；错误归属、未 ready、已删除或与任务不匹配的资产不能完成 run。
- 终态不可恢复，任务结果或直接采用既有输出都只写一次。

真实 MySQL IT 只连接仓库保留的本机 `ai_video_test`，使用随机测试表或受控测试 schema；不连接 `videoops_agent_dev`、Redis 或 Provider。它必须穿过 MySQL 唯一键、CHECK、CAS、数据库时间和既有任务/资产关系边界，证明新 Service 实例可恢复同一 run 且迟到或越权结果无法覆盖。

### 5. 收口

```powershell
Set-Location ai-video-api
.\mvnw.cmd -pl :ai-video-core -am "-Dmaven.test.skip=false" "-DskipTests=false" "-DskipITs=true" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=AgentRunServiceImplTest" test
.\mvnw.cmd -pl :ai-video-core -am "-Dmaven.test.skip=false" "-DskipTests=false" "-DskipITs=false" "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dit.test=AgentRunPersistenceIT" -Pdev,local-integration-test verify
Set-Location ..
pwsh -NoProfile -File scripts/validate-development-standards.ps1
git diff --check
```

最终在 `docs/EXECUTION.md` 记录当前 HEAD/diff、迁移哈希、测试真实性、`PASS/FAIL/NOT_RUN` 和剩余风险。T2 不以 Mock 冒充持久化恢复，也不自动进入 T3。
