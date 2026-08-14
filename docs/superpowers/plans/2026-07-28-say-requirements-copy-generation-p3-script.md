# P3 三套文案、不可变版本与用户文案库实现计划

> 2026-08-05 前向修订：`av_user_script` 与 `av_script_version` 已由 `20260805_01_user_script_manual_input.sql` 首次创建。P3 实施时必须采用前向扩列迁移，不能按本计划旧版 `20260728_07_p3_script.sql` 重建两表；同时保留 `manual_input/manual_edit`、可空 `draft_id`、独立 `current_version_id`，生成来源的三标题与确认约束必须按 `source_type` 条件化。

> 面向执行者：本计划必须通过 `subagent-driven-development` 或 `executing-plans` 逐卡执行；实现功能或修复缺陷时先用 `test-driven-development`，完成声明前用 `verification-before-completion`。复选框是执行状态，不是验收证据。

**目标：** 在不等待 P1/P2 全部完成的前提下，让第三名开发先完成 P3 的纯规则、不可变版本树、前端类型与独立 Mock；随后分别真实 rebase 已审核的 P1、P2 handoff，最终以一个收费根任务原子生成 A/B/C 三套文案、每套三个标题，并交付人工编辑、付费优化、准确确认、用户文案库和可追溯 F4 证据。

**架构：** 后端严格采用 RuoYi 贫血 Entity + Mapper + Service 编排。`ai-video-core` 的 P3 业务代码只能位于 `script/domain`、`script/dto`、`script/mapper`、`script/service`、`script/service/impl`；HTTP BO/VO 与 Controller 只能位于 `ai-video-user/.../user/script/domain/{bo,vo}` 和 `ai-video-user/.../user/script/controller`；模型提供商原始类型、提示词、解析器与任务 Handler 只位于 `ai-video-infra`。禁止新增平行业务分层或第二套跨模块契约。

**质量底线：** 本计划保留九张业务任务卡。每张卡都必须有独立 reviewer、先失败后通过的 fresh evidence、正反验收、越界扫描和固定输出；红色任务同一时刻最多两人参与。Token 或时间压力不构成删减测试、字段、状态、证据或复核的理由。

---

## 1. 权威基线、冻结范围与三人并行切片

### 1.1 权威源

执行前按以下顺序核对，发现冲突立即停止并由契约 owner 先改公共契约：

1. `AGENTS.md`、`RULES.md`、`docs/DOCUMENT_MAP.md`。
2. `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`。
3. `docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md`。
4. `docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`、`.agents/skills/ruoyi-plus-ai-coding/SKILL.md` 及其 `references/backend.md`。
5. 总计划提交 `37e2a15aa`、并行调和提交 `5ae54d31c`、已批准并行规格提交 `bb3d2b22e`。
6. P0-B 权威提交 `cb32b656`；P0-C 权威提交 `305b2d939` 及下游修订 `713c15c21`；P1 权威提交 `eb5aac8a`。不得把 P0-B 提交冒充 P0-C 基线。
7. P1/P2 只能通过各自独立审核通过、不可变且绑定 candidate HEAD 的 handoff 文件成为下游权威；开发 worktree 中未审核的源码不构成契约。

### 1.2 F0—F4 和三个人同时开工

| 切片 | P3 开发者可做 | 明确禁止 | 进入下一切片的证据 |
|---|---|---|---|
| F0 | 验证 P0-B/P0-C 稳定 I/DTO 契约、创建 P3 linked worktree、冻结路径和测试注册表 | 修改 P0-B/P0-C；伪造 handoff | F0 基线记录、gate 自测、clean worktree |
| F1 | 真实 rebase P0-C reviewed amendment HEAD；Tasks 1–3 中不依赖 P1/P2 的契约、纯校验规则和不可变版本树；Task 1 只做 `07` 静态 DDL/registry 校验，不执行依赖 `05/06` 的真实 migration IT；Tasks 7–8 的 TypeScript 类型、独立 Mock 与本地组件状态 | 调用或编译依赖 P1/P2 Service/DTO；执行 `05/06` 或完整迁移链；在生产或真实 IT 留上游 fake；改共享根入口 | `p3-f1-rebase.json` + P3 F1 单元/Vitest fresh evidence |
| F2 | 真实 rebase P1 审核 HEAD；完成 Task 2 的 `ScriptRecommendationReasonFormatter` 与对应测试；Task 4 只用 P1 五个 DTO 构造确定性 fixtures、序列化与哈希 | 注入或调用 P1 两个 Service；读取 P1 Mapper/Entity/table/user VO | `p3-f2-rebase.json`、P1 DTO import scan、Task 2 formatter + Task 4 F2 tests |
| F3 | 真实 rebase P2 审核 HEAD；删除 production 与真实 IT 中所有 P1/P2 fake；Task 4–6 接真实 P1/P2 Service，完成冻结、任务、provider、确认与 HTTP | 保留 fake、绕过相同读快照、从上游 Mapper/表取数 | `p3-f3-rebase.json`、真实 IT、零 fake/零越界 scan |
| F4 | Task 9 串行合入共享前端入口，执行迁移 `01→02→03→04→04a→05→06→07`、全链、UI、标准、独立 review 并创建 handoff | writer 自签、旧报告、跳过 `04a/07`、无 IT profile、直接 push | 独立 PASS review + 不可变 `p3-f4-handoff.json` |

三名开发的默认安排是：开发 A 继续 P1、开发 B 继续 P2、开发 C 执行 P3 F1；P1 审核完成后开发 C 进入 F2，P2 审核完成后进入 F3。每个任务卡只允许一名 writer 和一名独立 reviewer，reviewer 可由当时不在该卡写代码的人轮换担任。共享契约、迁移号、根入口与 handoff 写入必须由当前契约 owner 串行化。

### 1.3 P3 文件所有权

P3 独占：

- `docs/sql/ai-video/mysql/20260728_07_p3_script.sql`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/**`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/**`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/script/**`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/**`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script/**`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/script/**`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/scripts/**`
- `ai-video-ui/ai-video-webapp/mock/aivideo-scripts.ts`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useScriptFlow*.ts*`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptCandidateTabs*.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptVersionEditor*.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptOptimizationActions*.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/UserScriptLibrary*.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/ScriptDetailDrawer*.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/LibraryView.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ScriptStep.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/ScriptStep.test.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/VoiceStep.tsx`
- `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/BaseStep.tsx`

共享文件 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`ai-video-ui/ai-video-webapp/src/app.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioTopbar.tsx`，以及 owner registry 外的 `ai-video-integration-tests/**`、`ai-video-user-api/**`，只允许共享 owner 在对应任务卡的串行窗口修改；P3 task writer 只能提交变更请求和验收断言，不能直接写这些路径。`LibraryView.tsx` 已是 P3 独占文件，不再按共享文件处理。跨模块字段或状态变化先改公共契约并运行 `scripts/validate-development-standards.ps1`。

---

## 2. 冻结跨阶段契约

### 2.1 P3 唯一稳定 Service 与八个 DTO

P3 只发布以下三个 Service；名称、包和方法不得增加同义接口：

```java
package org.dromara.aivideo.script.service;

public interface IScriptGenerationService {
    ScriptGenerationResultDTO createGeneration(ScriptGenerationRequestDTO request);
    ScriptOptimizationResultDTO createOptimization(ScriptOptimizationRequestDTO request);
}

public interface IScriptVersionService {
    ScriptVersionDTO createManualVersion(
        Long scriptId,
        Long sourceVersionId,
        String scriptText,
        List<String> publishTitles,
        Integer selectedTitleIndex,
        String idempotencyKey,
        Long expectedBranchRevision,
        Long expectedGenerationContextRevision,
        String expectedGenerationInputHash);

    ScriptConfirmationDTO confirm(ScriptConfirmationDTO request);
}

public interface IUserScriptQueryService {
    PageResult<UserScriptSummaryDTO> queryVisible(
        String keyword,
        Long draftId,
        String industryCode,
        String purposeCode,
        String sourceType,
        String confirmationStatus,
        Instant updatedTimeStart,
        Instant updatedTimeEnd,
        String sortField,
        String sortOrder,
        PageQuery pageQuery);

    UserScriptSummaryDTO getVisibleSummary(Long scriptId);
    List<ScriptVersionDTO> getVersions(Long scriptId);
    ScriptConfirmationDTO getCurrentConfirmation(Long scriptId);
    void remove(Long scriptId, Long expectedDraftRevision);
}
```

八个稳定 DTO 全部位于 `org.dromara.aivideo.script.dto`，Java 内部编号用 `Long`；HTTP VO 和 TypeScript 才把编号变成十进制字符串：

```java
public record ScriptGenerationRequestDTO(
    Long draftId,
    Long branchId,
    Long draftRevision,
    Long branchRevision,
    String questionnaireHash,
    String knowledgeContextHash,
    Long generationContextRevision,
    String generationInputHash,
    String generationMode,
    String idempotencyKey,
    Long tariffVersion) {
}

public record ScriptGenerationResultDTO(
    Long rootTaskId,
    Long executionTaskId,
    Long usageOperationId,
    boolean reused) {
}

public record ScriptOptimizationRequestDTO(
    Long scriptId,
    Long sourceVersionId,
    Long draftId,
    Long branchId,
    Long branchRevision,
    String questionnaireHash,
    String knowledgeContextHash,
    Long generationContextRevision,
    String generationInputHash,
    String optimizationType,
    String customInstruction,
    String idempotencyKey,
    Long tariffVersion) {
}

public record ScriptOptimizationResultDTO(
    Long rootTaskId,
    Long executionTaskId,
    Long usageOperationId,
    boolean reused) {
}

public record ScriptFrozenInputDTO(
    Long rootTaskId,
    Long draftId,
    Long branchId,
    String operationType,
    String generationMode,
    Long sourceVersionId,
    String optimizationType,
    String customInstruction,
    Long promptVersionId,
    Long knowledgeSnapshotId,
    Long draftRevision,
    Long branchRevision,
    String questionnaireHash,
    String knowledgeContextHash,
    Long generationContextRevision,
    String generationInputHash,
    String requestHash,
    String inputSnapshotHash,
    String industryCode,
    String purposeCode,
    Integer targetDurationSeconds,
    Integer durationToleranceBasisPoints,
    Integer effectiveCharsPerMinute,
    String ruleConfigVersionsJson,
    String revisionSnapshotJson,
    String inputSummaryJson,
    String questionnaireRefsJson,
    String supplementRefJson,
    String planRefsJson,
    String factRefsJson,
    String prohibitedContentsJson,
    String actorType,
    Long actorId) {
}

public record ScriptVersionDTO(
    Long scriptId,
    Long versionId,
    Long parentVersionId,
    String candidateCode,
    String sourceType,
    String planCode,
    String angleCode,
    Long primaryTemplateVersionId,
    String differentiatorTechniqueCode,
    String angleSummary,
    List<String> publishTitles,
    Integer selectedTitleIndex,
    String scriptText,
    Integer effectiveCharacterCount,
    Integer estimatedDurationSeconds,
    Integer targetDurationSeconds,
    Integer effectiveCharsPerMinute,
    Integer durationToleranceBasisPoints,
    String validatorVersion,
    Long knowledgeSnapshotId,
    Long sourceTaskId,
    Long branchRevision,
    String questionnaireHash,
    String knowledgeContextHash,
    Long generationContextRevision,
    String generationInputHash,
    Instant createdAt) {
}

public record ScriptConfirmationDTO(
    Long confirmationId,
    Long scriptId,
    Long versionId,
    Integer selectedTitleIndex,
    String selectedTitle,
    Long draftId,
    Long branchId,
    Long expectedBranchRevision,
    Long expectedGenerationContextRevision,
    String expectedGenerationInputHash,
    String idempotencyKey,
    Long confirmedDraftRevision,
    Instant confirmedAt) {
}

public record UserScriptSummaryDTO(
    Long scriptId,
    Long draftId,
    String industryCode,
    String purposeCode,
    String displayTitle,
    Long currentConfirmedVersionId,
    String sourceType,
    String confirmationStatus,
    Integer versionCount,
    Integer referenceCount,
    Integer voiceCount,
    Integer digitalHumanCount,
    Integer workCount,
    Instant latestGeneratedAt,
    Instant createdAt,
    Instant updatedAt) {
}
```

`ScriptConfirmationDTO` 作为输入时 `confirmationId`、`selectedTitle`、`confirmedDraftRevision`、`confirmedAt` 必须为 `null`，作为输出时必须全部非空；Service 对混合态 fail-fast。除这八个 DTO 外，内部 JSON 解析节点和校验结果必须是 `service.impl` 的包私有类型，HTTP 请求响应必须是 user 模块 BO/VO，provider 原始请求响应必须留在 infra。

### 2.2 只允许消费的上游表面

P3 **不得在本计划或 P3 包中重声明任何 P0-C interface/record component/method signature**。所有任务创建、领取、续租、失败记录、attempt、终态与 dispatcher 调用，只 import P0-C F1 handoff 及 addendum reviewed HEAD 中的现行 `IAiTaskService`、`IAiTaskExecutionDispatcher`、`IAiTaskExecutionHandler`、`IAiTaskAttemptService` 和对应 DTO。F3 gate 直接对 rebase 后实际源码做 exact reflection/source signature 校验，并把源码文件 SHA 写入 evidence；本计划中的参数记忆不能成为契约。

签名 gate 的 method registry 至少覆盖 P0-C 当前 `createChargeableTask`、`claimExecutableTasks`、`renewLease`、`recordHandlerFailure`、`markSuccess`、`markFailed`、`requireGenerationContextWritable`、`inheritQuestionnaireTaskGroupMembers`，以及 dispatcher enqueue 和 attempt 的 start/complete/fail 三个现行方法。参数类型、参数顺序、返回类型、异常语义全部以 F1 addendum 实际源码为准；少方法、旧 overload 或 P3 wrapper 均失败。

F1 addendum handoff 顶层字段数量、名称和顺序精确为 12 项：`originalF1Head`、`amendmentHead`、`originalF1HandoffSha256`、`requiredMethods`、`schemaAddendum`、`owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`reviewCompletedAtUtc`、`evidence`、`capturedAtUtc`；旧七字段 addendum 或额外字段一律拒绝。gate 必须实时重算 original F1 handoff SHA 并与 `originalF1HandoffSha256` 相等，验证 owner/reviewer trim 后大小写不敏感不同、`reviewStatus='PASS'`、`reviewedHead == amendmentHead`，两个时间均显式 UTC 且 `reviewCompletedAtUtc <= capturedAtUtc`。

`evidence` 是 exact ordered 三项数组，每项字段名称与顺序精确为 `{kind,path,sha256}`：第一项 `{source-signatures,git-metadata:p0c-f1-addendum/source-signatures.manifest.json,<live sha256>}`，第二项 `{migration-04a,git-metadata:p0c-f1-addendum/migration-04a.manifest.json,<live sha256>}`，第三项 `{independent-review,git-metadata:p0c-f1-contract-addendum-review.json,<live sha256>}`。`git-metadata:` 必须通过 `git rev-parse --git-path` 解析；三份 regular file 和小写 SHA-256 均现场重算，重复、缺失、额外/乱序 kind、path 漂移、越界路径或 stale hash 均失败。独立 review JSON 的字段名称与顺序精确为九项：`owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`originalF1Head`、`originalF1HandoffSha256`、`requiredMethodsSha256`、`schemaAddendumSha256`、`reviewCompletedAtUtc`；其中两个 digest 分别对 addendum 中对应值的 canonical compact JSON UTF-8 计算，且 review 的身份、状态、HEAD、original SHA、UTC 必须逐项与 addendum 相等。

其中 `requiredMethods` 必须与 reviewed amendment HEAD 的现行 method registry exact 对齐；`schemaAddendum` 的字段顺序精确为 `forwardMigration,taskGroupMemberTable,activeTaskIndex,originValues,creatorTypes,globalLockOrder,scriptGroupKey,inheritanceScope,forbiddenCopies`，值依次精确为 `20260728_04a_p0c_task_group_guard.sql`、`av_ai_task_group_member`、`idx_av_ai_task_active_group`、`[origin,inherited]`、`[app_user,sys_user]`、`[draft,current_branch,operation_slot,quota_account,task_or_group_member]`、`script:{draftId}:{branchRevision}`、`membership_only`、`[task,usage,ledger,operation_slot]`。gate 必须同时核对 addendum JSON、实际 migration 文件 SHA/DDL 和现行 Service 源码；只搜索表名、索引或方法关键词不能通过。

- 生成与优化都先在 P3 外层写事务中通过 P2 F3 handoff 冻结的锁定入口依次锁住 `draft -> current_branch`；持锁后重新读取并精确核对 `branchRevision`、`questionnaireHash`、`knowledgeContextHash`。任一值变化在 `operation_slot` 或 `quota_account` 之前失败，不能使用锁前快照继续。
- 全局锁序唯一且完整为 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`：P2 锁入口完成前两级，P3 持有它们后调用 `createChargeableTask`，P0-C 从后三步接续。P3 不得提前抢 operation slot/quota/group 锁，也不得在后级锁之后回头锁 draft/current branch。首次创建后仍在同一事务冻结输入、enqueue 和写同事务审计；`reused=true` 时只校验既有冻结输入，不重复冻结或 enqueue。
- 只有紧邻真实 `ModelProvider` 调用前才 `startAttempt`。`initial`、`repair`、租约恢复后的真实调用共享根任务全局序号，最多 3 次；申请第 4 次必须在 provider 前失败。
- provider 超时必须至少早于 lease 到期 10 秒；剩余时间不足先 `renewLease`，后续所有条件更新使用返回的新 lease。
- 正常路径的三个版本、结果引用、`markSuccess` 与 settle 必须在同一业务事务。Handler 不注入、不直接调用 `IQuotaBillingService`。
- `STALE_BRANCH_RESULT`：provider attempt 仍以真实 usage 完成；不得写业务版本、不得 `markSuccess`、不得 provider retry；交给扫描器把根/执行任务失败并 release，不 settle。
- `ProviderUsageDTO.actualCost` 和 `currency` 来自提供商真实返回；未知 usage 用 `null`，禁止伪造 0。

P3 F2 只允许 import P1 五个 DTO：`KnowledgeRouteRequestDTO`、`KnowledgeRouteResultDTO`、`KnowledgePlanDTO`、`KnowledgeSnapshotRequestDTO`、`KnowledgeSnapshotDTO`。F3 才允许调用 `IKnowledgeRoutingService.route(KnowledgeRouteRequestDTO)` 和 `IKnowledgeSnapshotService.create(KnowledgeSnapshotRequestDTO)` / `getByRootTaskId(Long)`。

P3 F3 只允许调用 P2 handoff 最终发布的 `IQuestionnaireContextService`、`IEvidenceReviewService`，并消费 handoff 最终登记的 `QuestionnaireContextDTO`、`QuestionnaireAnswerRevisionDTO`、`QuestionnaireSupplementRevisionDTO`、`EvidenceReviewContextDTO`、`AcceptedEvidenceFactDTO`、`EvidenceDecisionRevisionDTO`。本计划只冻结类型名称与消费语义，**不提前冻结任何 record component、Java 类型、字段顺序或 JSON 子结构**。

P2 `p2-f3-handoff.json` 必须提供并由 F3 rebase gate 严格校验：六 DTO 的 component registry/hash、两个 Service 的最终方法签名、P2 写入守卫协议、排序/identity/revision 语义，以及 `answerIdentityJson`、`answerContextJson` 等最终组件的名称、类型、顺序和含义。锁入口已冻结为 `QuestionnaireContextDTO IQuestionnaireContextService.lockCurrentContextForGeneration(Long draftId, Long branchId)`：`Propagation.MANDATORY`、非 readOnly，内部按 tenant/owner scope 完成全局锁序前两级 `draft -> current_branch`，验证 `branchId == currentBranchId`，并在同一事务快照返回 context。生成和优化都必须从外层写事务调用该方法；只调用 readOnly `getCurrentContext` 或在 P3 自造另一个锁方法均失败。

除上述已由 owner 冻结的锁方法外，实现者只能从审核 handoff 生成其余反射断言和访问代码；handoff 缺任一 component registry/guard 项即阻塞 F3，禁止根据本计划、旧 P2 草稿或训练记忆补字段。

生成与优化在锁住 draft/current branch 的同一事务中读取 P2 最终上下文，并以 handoff 指定的 identity/revision 规则核对问卷、补充与事实集合。P2 返回的答案、补充或事实正文只允许在该事务和 prompt/snapshot 组装的短生命周期内存中使用；P3 冻结表、版本表、审计、日志和前端响应一律不得持久化或复制任何 P2 正文。P3 只持久化 handoff 明确标记为 identity/revision/hash 的最小引用及其 schema digest。P3 禁止 import P1/P2 Mapper、Entity、表名或 user VO。

### 2.3 创作端 actor、所有权与审计

- P3 创作端 Entity **不继承 `BaseEntity`**。每张表显式存储 `tenant_id`、`owner_type`、`owner_id`、`created_by_user_id` 以及所需时间/修订字段；禁止把 sys_user 审计字段当 app_user。
- Controller 和 Service 写边界只从 `AppActorContext` 取得 `actorType=app_user`、`actorId`、tenant/workspace；禁止使用默认 `LoginHelper`，sys token 进入创作端返回 401。
- Controller 不使用默认 `@Log`。必须审计的写操作在**同一业务事务内**调用 `IAppSecurityAuditService.append(AppSecurityAuditDTO)`，字段固定为 `resourceType`、`resourceId`、`action`、`actorType`、`actorId`、`beforeDigest`、`afterDigest`、`reason`；append 失败使业务写、任务/额度状态和审计一起回滚。P3 不得使用事务完成后的异步审计回调；会话失效类既有例外与本阶段无关。
- 审计 action 只允许 `script_generate_requested`、`script_version_created`、`script_optimization_requested`、`script_confirmed`、`script_removed`；摘要不得包含正文、标题、提示词、token、模型原始输出或自定义优化全文。
- 每次 detail/edit/confirm/delete/optimize 都先做 workspace 授权，再按 `(tenant_id, owner_type, owner_id, created_by_user_id)` 做对象归属；跨 workspace、跨 owner 和伪造 actor 一律 fail-closed。

---

## 3. 数据、冻结格式与业务不变量

### 3.1 `07` 四张表

`av_user_script`：显式字段 `id`、`tenant_id`、`owner_type`、`owner_id`、`created_by_user_id`、`draft_id`、`industry_code`、`purpose_code`、`display_title`、`current_confirmed_version_id`、`script_revision`、`created_at`、`updated_at`、`deleted`。唯一键 `(tenant_id,draft_id,deleted)`；索引 `(tenant_id,owner_type,owner_id,updated_at,id)`、`(tenant_id,created_by_user_id,updated_at,id)`；`owner_type` 只允许 `personal/workspace`，personal 时 owner_id 等于 app user，workspace 时 owner_id 等于已授权 workspace；仅此表逻辑删除，删除前必须确认引用数为 0。

`av_script_version`：显式字段 `id`、`tenant_id`、`owner_type`、`owner_id`、`created_by_user_id`、`script_id`、`parent_version_id`、`candidate_code`、`source_type`、`plan_code`、`angle_code`、`primary_template_version_id`、`differentiator_technique_code`、`angle_summary`、`publish_titles_json`、`selected_title_index`、`script_text`、`effective_character_count`、`estimated_duration_seconds`、`target_duration_seconds`、`effective_chars_per_minute`、`duration_tolerance_basis_points`、`rule_config_versions_json`、`validator_version`、`knowledge_snapshot_id`、`source_task_id`、`manual_idempotency_key`、`branch_revision`、`generation_context_revision`、`generation_input_hash`、`questionnaire_hash`、`knowledge_context_hash`、`created_at`。外键 `script_id -> av_user_script.id`，`parent_version_id -> av_script_version.id`，禁止 update/delete；唯一键 `(tenant_id,source_task_id,candidate_code)` 和 `(tenant_id,script_id,manual_idempotency_key)`；索引 `(tenant_id,script_id,parent_version_id,created_at,id)`、`(tenant_id,knowledge_snapshot_id)`；`source_type` 只允许 `generated/manual_edit/ai_optimized`，生成候选只能 A/B/C，标题必须正好三个且 index 为 0..2。

`av_script_confirmation`：显式字段 `id`、`tenant_id`、`owner_type`、`owner_id`、`created_by_user_id`、`script_id`、`version_id`、`draft_id`、`branch_id`、`branch_revision`、`generation_context_revision`、`generation_input_hash`、`selected_title_index`、`selected_title_snapshot`、`idempotency_key`、`confirmed_draft_revision`、`created_at`。外键指向 script/version，禁止 update/delete；唯一键 `(tenant_id,script_id,idempotency_key)`；索引 `(tenant_id,draft_id,created_at,id)`、`(tenant_id,version_id)`；历史确认不可被“当前确认”覆盖。

`av_script_task_input`：显式字段 `id`、`tenant_id`、`owner_type`、`owner_id`、`created_by_user_id`、`root_task_id`、`draft_id`、`branch_id`、`operation_type`、`generation_mode`、`source_version_id`、`optimization_type`、`custom_instruction_ciphertext`、`prompt_version_id`、`knowledge_snapshot_id`、`draft_revision`、`branch_revision`、`generation_context_revision`、`generation_input_hash`、`questionnaire_hash`、`knowledge_context_hash`、`request_hash`、`input_snapshot_hash`、`industry_code`、`purpose_code`、`target_duration_seconds`、`duration_tolerance_basis_points`、`effective_chars_per_minute`、`rule_config_versions_json`、`revision_snapshot_json`、`input_summary_json`、`questionnaire_refs_json`、`supplement_ref_json`、`plan_refs_json`、`fact_refs_json`、`prohibited_contents_json`、`actor_type`、`actor_id`、`created_at`。唯一键 `(tenant_id,root_task_id)`；索引 `(tenant_id,draft_id,branch_revision)`、`(tenant_id,source_version_id)`；禁止 update/delete，写后只按 root task 读取。自定义优化指令按现有敏感字段能力加密，DTO 解密只存在 Handler 内存，日志和审计永不输出。

四个 Entity 都显式声明字段且不继承默认审计基类。Mapper 的所有对象查询必须带 tenant/owner/created_by_user_id 约束；唯一键不能替代归属校验。`07` 必须能在干净 MySQL 8 上按 `01→02→03→04→04a→05→06→07` 执行，并在同一 schema 第二次执行 `07` 不改变对象定义和数据。

### 3.2 冻结 JSON 与哈希

- `revision_snapshot_json` 精确包含 P0-C 四个修订字段和按数值 `factId` 升序的 `factDecisionRevisions`。
- `input_summary_json` 精确包含 `industryCode`、`purposeCode`、`targetDurationSeconds`、`branchRevision`、`questionnaireHash`、`knowledgeContextHash`、`generationContextRevision`、`generationInputHash`。
- `questionnaire_refs_json` 与 `supplement_ref_json` 不在本计划预定义子字段。F3 只能按 P2 handoff 的最终 component registry，从其 identity/revision/hash 分类中投影最小引用，并连同 registry schema digest 规范化；正文分类组件一律不进入冻结 JSON。
- `plan_refs_json` 正好 A/B/C，依序存 `candidateCode/planCode/primaryTemplateVersionId/angleCode/differentiatorTechniqueCode`。
- `fact_refs_json` 的 P3 canonical output 只存 `factId/decisionRevision/factHash`，按 factId 升序；这些是 P3 冻结 schema 名，不是对 P2 record component 的预声明。F3 adapter 必须从 P2 final component registry/semantics 显式映射并校验，handoff 未授权任一 identity/revision/hash 来源时阻塞，不能按同名字段猜测。
- 冻结表不复制知识正文、问题正文、事实正文、prompt、模型输出、来源文案正文或标题。优化时按 `source_version_id` 从不可变版本加载正文/标题到 Handler 内存。
- `input_snapshot_hash` 是除数据库自增 ID、密文随机 nonce 和时间之外所有语义列与规范 JSON 的 SHA-256；相同 root/request/hash 回读，任何差异抛 46116。

### 3.3 provider 输出与严格校验

生成 schema 固定 `script-generation-1`，顶层只有 `schemaVersion`、`candidates`。每个候选只有 `candidateCode`、`planCode`、`angleCode`、`primaryTemplateVersionId`、`differentiatorTechniqueCode`、`publishTitles`、`scriptText`、`factRefs`；事实引用只有 `factId`、`textFragment`。优化 schema 固定 `script-optimization-1`，顶层只有 `schemaVersion`、`sourceVersionId`、`publishTitles`、`scriptText`、`factRefs`。所有对象 `additionalProperties=false`；ID 必须是无前导零的十进制字符串，拒绝 JSON number、溢出、重复 key 和未知 key。provider 若返回 `angleSummary` 或任何推荐理由字段，必须作为 unknown property 拒绝，不能覆盖服务端推荐理由。

`ScriptVersionDTO.angleSummary` 和页面“为什么推荐”是服务端派生字段，不是模型输出。`ScriptRecommendationReasonFormatter` 按 `KnowledgeRouteResultDTO.plans` 的确定顺序得到 1-based rank，并仅使用对应 `KnowledgePlanDTO` 的 `candidateCode/planCode/angleCode/primaryTemplateVersionId/differentiatorTechniqueCode`，以 formatter version `script-recommendation-1` 生成固定模板：`候选{candidateCode}为路由排序第{rank}位，采用{angleCode}角度、{differentiatorTechniqueCode}差异化技法和模板版本{primaryTemplateVersionId}（方案{planCode}）`。同 route/plan 必须逐字相同；provider payload、prompt 文本或当前字典 label 都不能改变它。

等效字符：每个 CJK code point 计 1，每个连续 Latin/数字 token 计 1，空白和标点计 0；时长 `ceil(effectiveCount * 60 / effectiveCharsPerMinute)`，并按 basis points 校验目标区间。候选必须精确且只含 A/B/C，route 身份与冻结计划逐字段相等；每套正好三个不同标题、每标题最多 100 code points，正文最多 20,000 code points。拒绝未知事实、修订不匹配、引用片段不是正文子串、无事实引用的事实数字、候选计划重复、正文前 40 个等效字符相同、三元字符 Jaccard `>= 0.85`、镜头指令、无依据价格/数据/保证和 prohibited contents。所有确定性结构失败统一 `MODEL_INVALID_RESPONSE`（46112）。

### 3.4 错误码

- 46112 `MODEL_INVALID_RESPONSE`
- 46116 `SCRIPT_REVISION_CONFLICT`
- 46117 `SCRIPT_CURRENT_BRANCH_REQUIRED`
- 46118 `SCRIPT_REFERENCED`
- 46123 `AI_TASK_SLOT_OCCUPIED`（复用 P0-C）
- 46125 `SCRIPT_CONTEXT_STALE`

不得依据中文 message 分支，不新增 HTTP 错误 envelope。

---

## 4. 测试与统一证据 gate

所有新增或修改的 JUnit `*Test` / `*IT` 都在类级标注 `@Tag("dev")`。每个 `*IT` 第一条环境动作是 `LocalIntegrationEnvironment.requireFromEnvironment()`；默认读取用户端 `application-dev.yml` 并固定派生本机 MySQL 8 的 `ai_video_test`、Redis 7 DB 15 和当前 `aivideo:it:<runId>:` 前缀，环境变量仅可选覆盖。禁止容器、WSL、虚拟机、Testcontainers、开发/生产库和 `FLUSHALL`；不安全配置必须失败，不能 skip 后宣称通过。

Task 1 将下面脚本以 UTF-8 无 BOM、LF 写入 `git rev-parse --git-path p3-evidence-gate.ps1`。所有 RED/GREEN 命令先删除本 selector 的精确报告、记录 UTC start、运行 selector，再调用本 gate。`StartedAtUtc` 每次必传且必须是 UTC；RED 还必须传只能命中预期断言失败的 `ExpectedFailurePattern`，编译、装配、连接或环境错误不得通过。每次调用的 `AllowedPaths` 必须逐个列出当前任务卡文件；不得传宽泛仓库根。报告引用允许普通 repo-relative path，或以 `git-metadata:` 开头并由 `git rev-parse --git-path` 动态解析的 Git metadata path。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$RepoRoot,
  [Parameter(Mandatory=$true)][ValidateSet('worktree','junit','vitest','selftest')][string]$Mode,
  [string[]]$AllowedPaths=@(),
  [switch]$RequireClean,
  [string]$ReportRelativePath,
  [string]$ExpectedSuite,
  [string[]]$ExpectedTestFiles=@(),
  [ValidateSet('RED','GREEN')][string]$Phase='GREEN',
  [Parameter(Mandatory=$true)][DateTimeOffset]$StartedAtUtc,
  [string]$ExpectedFailurePattern
)
$ErrorActionPreference='Stop'
$rootText=(& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$root=[IO.Path]::GetFullPath($rootText.Trim())
if($root -cne [IO.Path]::GetFullPath($RepoRoot)){throw 'RepoRoot 不准确'}
Set-Location -LiteralPath $root
if($StartedAtUtc.Offset -ne [TimeSpan]::Zero -or $StartedAtUtc -eq [DateTimeOffset]::MinValue){
  throw 'StartedAtUtc 必须是显式 UTC fresh start'
}
if($StartedAtUtc -gt [DateTimeOffset]::UtcNow.AddMinutes(1)){throw 'StartedAtUtc 不得位于未来'}

function Resolve-P3Path([string]$reference,[string]$name){
  if($reference.StartsWith('git-metadata:',[StringComparison]::Ordinal)){
    $relative=$reference.Substring('git-metadata:'.Length)
    if([string]::IsNullOrWhiteSpace($relative) -or [IO.Path]::IsPathRooted($relative) -or $relative.Contains('..')){
      throw "$name Git metadata path 非法"
    }
    $raw=(& git rev-parse --git-path $relative).Trim()
    if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)){throw "$name Git metadata path 无法解析"}
    return $(if([IO.Path]::IsPathRooted($raw)){[IO.Path]::GetFullPath($raw)}else{[IO.Path]::GetFullPath((Join-Path $root $raw))})
  }
  if([IO.Path]::IsPathRooted($reference)){throw "$name 必须是 repo-relative 或 git-metadata 引用"}
  $resolved=[IO.Path]::GetFullPath((Join-Path $root $reference))
  if(-not $resolved.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
    throw "$name 越出仓库"
  }
  return $resolved
}

function Assert-Allowed([string]$path,[string[]]$allowed){
  $n=$path.Replace('\','/').Trim()
  foreach($entry in $allowed){if($n -ceq $entry.Replace('\','/').Trim()){return}}
  throw "P3 越界文件：$n"
}
function Assert-Worktree([switch]$clean){
  $branch=(& git branch --show-current).Trim()
  if($branch -cnotlike 'codex/*' -or $branch -ceq 'main'){throw 'P3 实现必须在 codex/* linked worktree'}
  $gitDir=[IO.Path]::GetFullPath((& git rev-parse --git-dir).Trim())
  $commonDir=[IO.Path]::GetFullPath((& git rev-parse --git-common-dir).Trim())
  if($gitDir -ceq $commonDir){throw 'P3 实现不得使用主工作树'}
  foreach($marker in @('rebase-merge','rebase-apply','MERGE_HEAD','CHERRY_PICK_HEAD')){
    $p=(& git rev-parse --git-path $marker).Trim()
    if(Test-Path -LiteralPath $p){throw "未完成 Git 操作：$marker"}
  }
  $dirty=@(& git status --porcelain=v1 -uall)
  foreach($line in $dirty){
    $path=$line.Substring(3)
    if($path.Contains(' -> ')){$path=$path.Split(' -> ')[-1]}
    Assert-Allowed $path $AllowedPaths
  }
  if($clean -and $dirty.Count -ne 0){throw '要求 clean worktree'}
}

if($Mode -ceq 'selftest'){
  $rejected=$false
  try{Assert-Allowed '../escape' @('allowed/file')}catch{$rejected=$true}
  if(-not $rejected){throw '越界自测意外通过'}
  'P3_EVIDENCE_GATE_SELFTEST_OK'; exit 0
}
Assert-Worktree -clean:$RequireClean
if($Mode -ceq 'worktree'){'P3_WORKTREE_GATE_OK'; exit 0}
if([string]::IsNullOrWhiteSpace($ReportRelativePath)){throw '缺 ReportRelativePath'}
$report=Resolve-P3Path $ReportRelativePath '报告'
if(-not (Test-Path -LiteralPath $report -PathType Leaf)){throw 'fresh 报告缺失'}
$mtime=[DateTimeOffset](Get-Item -LiteralPath $report).LastWriteTimeUtc
if($mtime -lt $StartedAtUtc.AddSeconds(-2)){throw '报告早于本次执行'}
if($Phase -ceq 'RED' -and [string]::IsNullOrWhiteSpace($ExpectedFailurePattern)){
  throw 'RED 必须提供 ExpectedFailurePattern'
}
if($Phase -ceq 'RED'){
  try{[void][regex]::new($ExpectedFailurePattern)}catch{throw 'ExpectedFailurePattern 不是有效 regex'}
}

if($Mode -ceq 'junit'){
  [xml]$xml=Get-Content -LiteralPath $report -Raw -Encoding UTF8
  $suites=@($xml.SelectNodes('//testsuite') | Where-Object {$_.name -ceq $ExpectedSuite})
  if($suites.Count -ne 1){throw 'suite 缺失或重复'}
  $suite=$suites[0]
  $tests=[int]$suite.tests; $failures=[int]$suite.failures
  $errors=[int]$suite.errors; $skipped=[int]$suite.skipped
  if($tests -le 0 -or $skipped -ne 0){throw 'tests 必须 >0 且 skipped=0'}
  if($Phase -ceq 'GREEN' -and ($failures -ne 0 -or $errors -ne 0)){throw 'GREEN 报告不绿'}
  if($Phase -ceq 'RED'){
    if($failures -le 0 -or $errors -ne 0){throw 'RED 必须是断言 failure，不能是 error'}
    $failureText=@($suite.SelectNodes('.//testcase/failure') | ForEach-Object{([string]$_.message)+' '+([string]$_.InnerText)}) -join "`n"
    if($failureText -cnotmatch $ExpectedFailurePattern){throw 'RED 未命中预期失败模式'}
  }
  'P3_JUNIT_EVIDENCE_OK'; exit 0
}

$json=Get-Content -LiteralPath $report -Raw -Encoding UTF8 | ConvertFrom-Json
if([int]$json.numTotalTests -le 0 -or [int]$json.numPendingTests -ne 0){throw 'Vitest tests 必须 >0 且 pending=0'}
if($Phase -ceq 'GREEN' -and ([int]$json.numFailedTests -ne 0 -or [int]$json.numFailedTestSuites -ne 0 -or -not [bool]$json.success)){throw 'Vitest GREEN 不绿'}
if($Phase -ceq 'RED'){
  if([int]$json.numFailedTests -le 0 -or [bool]$json.success){throw 'Vitest RED 未产生测试断言失败'}
  $failureText=@($json.testResults | ForEach-Object{
    @($_.message)+@($_.assertionResults | Where-Object{$_.status -ceq 'failed'} | ForEach-Object{@($_.failureMessages)})
  }) -join "`n"
  if($failureText -cnotmatch $ExpectedFailurePattern){throw 'Vitest RED 未命中预期失败模式'}
}
$actual=@($json.testResults | ForEach-Object{
  $testPath=[IO.Path]::GetFullPath($_.name)
  if(-not $testPath.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Vitest test file 越出仓库'}
  $testPath.Substring($root.Length+1).Replace('\','/')
} | Sort-Object -Unique)
$expected=@($ExpectedTestFiles | ForEach-Object{$_.Replace('\','/')} | Sort-Object -Unique)
if(($actual -join '|') -cne ($expected -join '|')){throw 'Vitest 文件集合漂移'}
'P3_VITEST_EVIDENCE_OK'
```

统一 JVM 命令骨架固定为：动态解析 `$repoRoot`；删除 exact XML；记录 `$started`；从 `ai-video-api` 执行 `mvnw.cmd -pl :<artifact> -am -Dmaven.test.skip=false -DskipTests=false '-Dtest=<FQCN或FQCN#method>' test`，IT 改用 `-DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=<FQCN>' '-Pdev,local-integration-test' verify`；然后用 exact suite/report 调 gate。RED 调用必须另传仅匹配预期断言的 `-ExpectedFailurePattern`。统一前端命令从 `$repoRoot/ai-video-ui/ai-video-webapp` 执行 exact files、JSON reporter、lint；Task 9 另执行 build。

每张卡固定输出四段：`完成项`、`风险`、`验证证据`、`阻塞项`。writer 只能报告 candidate，不得把自己的复跑写成独立 PASS。

---

## 5. 九张最小可执行任务卡

### 任务 1：冻结 P3 契约、`07` 迁移和统一 gate

**文件：** 创建 `docs/sql/ai-video/mysql/20260728_07_p3_script.sql`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script/domain/UserScript.java`、`ScriptVersion.java`、`ScriptConfirmation.java`、`ScriptTaskInput.java`、四个同名 Mapper、三个稳定 Service、八个稳定 DTO；创建 `ScriptContractRegistryTest.java`、`ScriptSchemaMigrationIT.java`；F1 rebase/addendum binding 与 gate 只写当前 worktree Git metadata 的 `p3-f1-rebase.json`、`p3-evidence-gate.ps1`、`p3-evidence-manifest-gate.ps1` 和 evidence。

**最小任务卡：**

- **单一目标／不做：** 建立唯一 P3 数据/Java 契约和可复用 fresh evidence gate；不实现业务分支、provider、HTTP 或 UI。
- **权威源：** Sections 1–4、`37e2a15aa`、`5ae54d31c`、`bb3d2b22e`、P0-B/P0-C 权威提交、generator 模板和相似 RuoYi Entity/Mapper。
- **治理等级／触发项：** 红色；触发新迁移、跨模块契约、typed actor、不可变数据和所有后续任务。
- **实施者／reviewer／并发：** 开发 C writer；开发 A 数据/契约 reviewer；最多 2 人，迁移号和稳定接口只由 writer 修改。
- **精确路径／数据范围：** 仅本卡“文件”行、`git rev-parse --git-path p3-evidence-gate.ps1`、`git rev-parse --git-path p3-evidence-manifest-gate.ps1` 和 F1 exact Surefire XML；`ScriptSchemaMigrationIT` 只登记实现，真实 Failsafe 执行延后 F4 的安全本机 `ai_video_test`，Redis 本卡不用。
- **允许影响：** 可增加 P3 四表、Entity/Mapper、稳定 I/DTO；禁止改 `01..06`、继承 `BaseEntity`、使用默认 actor、创建同义 Service/DTO 或写业务实现。
- **前置／退出：** F0 authority/linked worktree/clean gate 完成，F1 addendum 的 12 顶层字段、original handoff live SHA、独立 PASS/UTC 和三类 live evidence gate 全部通过；F1 退出只要求 `07` 静态字段/约束/幂等守卫检查和 registry exact report GREEN。不得在 F1 执行尚未具备 `05/06` 的完整迁移链；`01→02→03→04→04a→05→06→07`、`07` replay 与 migration exact report 统一延后 F4。
- **结构签名检查点：** reviewer 对照 Section 2.1 八 DTO/三接口逐字段反射；对 P0-C 校验 F1 addendum 的 12 顶层字段顺序、`originalF1HandoffSha256` live 值、owner/reviewer/PASS/reviewed HEAD/UTC、三个 evidence kind/path/live SHA、actual source signature 与 schemaAddendum（`04a`、全局锁序、script group key、membership-only）；对照 Section 3.1 逐表核对字段、FK、唯一键、索引、逻辑删除/不可变语义及 tenant/owner/created_by_user_id。
- **GREEN 独立复跑检查点：** F1 reviewer 只删除并重建 registry exact XML，独立复跑反射与 `07` 静态 DDL 检查并核对 gate sentinel；F4 reviewer 才在安全本机库删除并重建 migration XML、执行 `ScriptSchemaMigrationIT` 和 `07` replay。
- **正向／反向验收：** 正向 12 字段 addendum、live original handoff/evidence SHA、独立 review、`04a`、四表/索引/约束、`07` replay、actor 字段和签名精确；反向拒绝旧七字段 addendum、stale/missing/extra evidence、owner=reviewer、非 PASS、reviewedHead 漂移、非 UTC、旧 P0-C overload、漏 `requireGenerationContextWritable/inheritQuestionnaireTaskGroupMembers`、错误锁序/group key、inherit 复制 task/usage/ledger/operation_slot、错误 schema、BaseEntity、零测试、skip、旧报告和不安全数据库。
- **统一 gate：** 本卡先运行 gate `selftest`，随后所有证据调用同一脚本；`AllowedPaths` 精确传本卡文件。
- **准确命令／证据：** F1 只运行 registry selector `org.dromara.aivideo.script.ScriptContractRegistryTest`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.ScriptContractRegistryTest.xml`；F4 才运行 migration selector `org.dromara.aivideo.script.ScriptSchemaMigrationIT`，报告同模块 `target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptSchemaMigrationIT.xml`，命令必须带 `'-Pdev,local-integration-test'`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置：读取 RuoYi skill/backend reference、generator Entity/Mapper/XML、P0-C migration IT 和 `LocalIntegrationEnvironment`；先按 Section 15.2 对 reviewed amendmentHead 做真实 F1 rebase，并 CreateNew `p3-f1-rebase.json` 绑定 addendum/original handoff/三类 evidence；再把两个 gate 写入 Git metadata并执行 `& $gate -RepoRoot $repoRoot -Mode selftest -StartedAtUtc ([DateTimeOffset]::UtcNow)`，必须精确返回 `P3_EVIDENCE_GATE_SELFTEST_OK`。
- [ ] RED：先写 P3 registry 的精确类/字段/方法/包反射断言；P0-C addendum test 直接读取 actual source/handoff，精确断言 12 顶层字段及顺序、original handoff live SHA、owner!=reviewer、PASS、reviewedHead=amendmentHead、UTC、三项 ordered `{kind,path,sha256}` live evidence、九字段 independent-review、现行 method registry、`20260728_04a_p0c_task_group_guard.sql` SHA/DDL、完整全局锁序 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`、group `script:{draftId}:{branchRevision}`、membership-only 且 task/usage/ledger/operation_slot 不复制；再写 `07` 字段/约束/replay/actor 断言。每项均有缺失/篡改反例，测试必须运行后失败，保存 fresh RED XML。
- [ ] GREEN 数据：实现 Section 3.1 全字段 SQL；四 Entity 显式字段、不继承默认审计基类；Mapper 查询测试证明 tenant/owner/creator 条件不能省略。
- [ ] GREEN 契约：创建且只创建 Section 2.1 三 Service/八 DTO；业务失败复用共享异常和错误码。
- [ ] GREEN 验证：F1 删除 registry exact XML、记录 UTC、运行 registry unit，断言 `tests>0`、failures/errors/skipped=0、mtime fresh；静态解析 `07` 的字段、约束和重复执行守卫；运行 `git diff --check`。真实 migration IT 不在 F1 运行，由 F4 统一执行。
- [ ] review/提交：独立 reviewer 核对 SQL/反射报告；writer 只在 review 意见关闭后提交 `feat(script): freeze p3 contracts and schema`。

### 任务 2：实现冻结结果解析、等效字符和严格规则

**文件：** F1 创建 `script/service/impl/EffectiveCharacterCounter.java`、`ScriptGenerationResultValidator.java`、`ScriptOptimizationResultValidator.java`、`ScriptFrozenInputValidator.java` 及四个同包测试，并创建 `ai-video-infra/.../script/provider/ScriptProviderResponseParser.java` 及测试；F2 真实 rebase P1 后才创建 `ScriptRecommendationReasonFormatter.java`，其 `KnowledgePlanDTO` 派生断言再加入 `ScriptGenerationResultValidatorTest`。所有 helper 均包私有或最终类，不发布新 Service。

**最小任务卡：**

- **单一目标／不做：** 把 Section 3.2/3.3 的规范 JSON、哈希、解析和纯校验变成确定性代码；不访问数据库、不调用 provider、不创建任务。
- **权威源：** Section 3、八 DTO、现有 JSON 安全配置和 P0-C 错误码。
- **治理等级／触发项：** 黄色；触发模型输出安全、事实引用、时长和三候选差异。
- **实施者／reviewer／并发：** 开发 C writer；开发 B 安全/边界 reviewer；最多 2 人，可与 P1/P2 工作并行。
- **精确路径／数据范围：** 仅本卡点名的五个 core helper、四个 core test、一个 infra parser/测试和 exact Surefire XML；纯内存 fixtures，不写数据库/网络。
- **允许影响：** 可使用八稳定 DTO和 infra 原始 JSON node；禁止增加第九稳定 DTO、宽松 unknown field、默认补值或把 provider 类型放 core。
- **前置／退出：** F1 依赖 Task 1 registry GREEN，退出为不依赖 P1 的解析、哈希、字符、生成/优化校验测试 fresh GREEN；formatter 子项必须等待 F2 rebase P1 reviewed HEAD，退出为 `KnowledgePlanDTO` 派生测试 fresh GREEN。
- **结构签名检查点：** F1 reviewer 核对 schema version、additionalProperties、字符串 ID、A/B/C、三标题、事实片段、Jaccard、时长公式和统一 46112；F2 reviewer 在 P1 已 rebase 后核对推荐理由只能由 route rank + `KnowledgePlanDTO` 经固定 formatter 派生。
- **GREEN 独立复跑检查点：** reviewer 以不同 JSON key 顺序、Unicode supplementary code point、边界时长和 duplicate-key payload 重跑 exact suite。
- **正向／反向验收：** 正向规范 payload 稳定解析/哈希并确定性派生推荐理由；反向覆盖 number ID、前导零/溢出、未知/重复 key、provider 夹带/覆盖推荐理由、候选非精确 A/B/C、标题非 3 个/重复/超长、未知事实、无引用数字、相似正文、镜头指令、禁止声明和优化 source mismatch。
- **统一 gate：** Task 1 gate；`AllowedPaths` 逐个传本卡实现/测试文件。
- **准确命令／证据：** exact selector `org.dromara.aivideo.script.service.impl.EffectiveCharacterCounterTest,org.dromara.aivideo.script.service.impl.ScriptGenerationResultValidatorTest,org.dromara.aivideo.script.service.impl.ScriptOptimizationResultValidatorTest,org.dromara.aivideo.script.service.impl.ScriptFrozenInputValidatorTest`；报告为四个同名 Surefire XML；parser selector 在 `:ai-video-infra`，报告 `TEST-org.dromara.aivideo.script.provider.ScriptProviderResponseParserTest.xml`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] RED 字符/哈希：写 CJK、Latin/数字 token、标点、emoji、空正文、`ceil` 和 basis points 边界；写 canonical key/order 改变但 hash 不变、语义改变 hash 必变。
- [ ] RED schema：F1 逐项写负例，必须包含测试方法 `rejectsCandidateCodesOtherThanExactlyABC`、`rejectsCandidateWithoutExactlyThreeDistinctTitles`、`rejectsProviderSuppliedAngleSummaryAsUnknownProperty`、`rejectsProviderSuppliedRecommendationReasonAsUnknownProperty`；F2 rebase P1 后才增加 `derivesRecommendationReasonFromRouteRankAndKnowledgePlanOnly`，每份测试先取得可解释 RED。
- [ ] GREEN：F1 实现严格 parser，拒绝 duplicate/unknown 和 provider 推荐理由，并实现 pure counter/validators；F2 才实现依赖 P1 `KnowledgePlanDTO` 的 formatter。所有失败产生同一 typed business code 且不记录敏感 payload。
- [ ] GREEN 验证：逐份删除 exact XML、记录 start、运行 exact selector 和 gate；执行 `git diff --check`。
- [ ] review/提交：reviewer 独立 mutation-style 反例复跑后提交 `feat(script): validate generated script results`。

### 任务 3：实现文案主体、不可变版本树和准确确认

**文件：** 创建 `script/service/impl/ScriptVersionServiceImpl.java`、`UserScriptQueryServiceImpl.java` 及测试；完善四 Mapper；创建 `ScriptVersionPersistenceIT.java`。不得创建新的跨模块 DTO。

**最小任务卡：**

- **单一目标／不做：** 实现人工子版本、不可变版本集合、确认历史、五类素材库过滤、当前显示标题和下游引用删除保护；不创建收费任务、不调用 P1/P2/provider、不暴露 HTTP。
- **权威源：** Sections 2.1、2.3、3.1、错误码表和 Task 1 schema。
- **治理等级／触发项：** 红色；触发用户正文、确认版本、draft revision、幂等与逻辑删除。
- **实施者／reviewer／并发：** 开发 C writer；开发 A 事务/权限 reviewer；最多 2 人。
- **精确路径／数据范围：** 仅本卡实现/测试、Task 1 Mapper 的必要语句和 exact reports；本机 IT 只写当前 run tenant/owner fixture。
- **允许影响：** 可插入 immutable version/confirmation、条件更新 user script 和草稿 revision；禁止 update/delete version/confirmation、从请求复制选中标题、修改父版本、跨 owner 查询。
- **前置／退出：** Tasks 1–2 GREEN；退出为 unit + native IT 证明版本树、确认幂等、并发冲突、归属与引用保护。
- **结构签名检查点：** reviewer 核对 `IScriptVersionService`/`IUserScriptQueryService` 精确签名、五类 filter 语义、flat versions/current confirmation/downstream counts 查询、manual child 继承历史 snapshot/规则/修订、确认标题只从 version 的三个标题按 index 读取。
- **GREEN 独立复跑检查点：** reviewer 用两个 app_user 和两个 workspace、同/异 idempotency payload、并发 expected revision 独立复跑。
- **正向／反向验收：** 正向手工子版本不改父级、同请求复用、确认新增历史并更新当前指针/标题/draft revision、五过滤任意组合、flat versions 和三类下游计数；反向拒绝非法过滤值/时间范围、跨 owner、错误分支 46117、上下文陈旧 46125、同 key 异 payload 46116、请求伪造标题、环/断链、被引用删除 46118。
- **统一 gate：** Task 1 gate；IT 必带 local profile 并逐份 fresh XML。
- **准确命令／证据：** unit selector `org.dromara.aivideo.script.service.impl.ScriptVersionServiceImplTest,org.dromara.aivideo.script.service.impl.UserScriptQueryServiceImplTest`；IT selector `org.dromara.aivideo.script.ScriptVersionPersistenceIT`，报告分别位于 core Surefire/Failsafe 目录。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] RED manual：同一 source 产生子版本、继承 source task/snapshot/context/duration configs、parent 不变；same-key same-payload 回读、different-payload 46116。
- [ ] RED query/confirm/delete：写 `filtersByIndustryPurposeSourceConfirmationAndUpdatedRangeTogether`，冻结 `updated_at >= start AND updated_at < end`、display version sourceType、confirmed/unconfirmed、稳定分页；确认输入/输出 nullability、title index、条件递增 draft revision、历史不覆盖、current confirmation；detail 返回 flat versions 与 `voice/digitalHuman/work/total/latestGeneratedAt`；再覆盖 cross-owner、context/hash、引用保护、cycle/broken relation。
- [ ] GREEN：实现显式 actor/owner Mapper 条件、不可变 insert、条件 update；确认事务把 history + current pointer + display title + draft revision 放在同一事务。
- [ ] GREEN 验证：unit 与带 profile IT 分开运行并过 fresh gate；SQL 日志断言无 version/confirmation update/delete；运行 `git diff --check`。
- [ ] review/提交：独立 reviewer 核对事务边界和所有负例后提交 `feat(script): add immutable script versions`。

### 任务 4：真实接入 F1/F2/F3，冻结输入并创建收费任务

**文件：** 创建 `script/service/impl/ScriptFrozenInputServiceImpl.java`、`ScriptFrozenInputAssembler.java`、`ScriptGenerationServiceImpl.java` 及测试；完善 `ScriptTaskInputMapper`；创建 `ScriptFrozenInputPersistenceIT.java`。F2/F3 rebase 记录只写当前 worktree Git metadata。

**最小任务卡：**

- **单一目标／不做：** 在 F2 验证 P1 DTO-only 组装，在 F3 以 draft/current branch 悲观锁消除 P2 写入与收费任务创建之间的 TOCTOU，并按唯一锁序创建任务与不可变冻结输入；不调用 provider、不写生成版本、不直接结算。
- **权威源：** Sections 1.2、2.2、3.2，P1 `p1-f2-handoff.json`、P2 `p2-f3-handoff.json` 的 strict schema，P0-C I/DTO 权威。
- **治理等级／触发项：** 红色；触发三次真实 rebase、上游契约、P2 写入守卫、悲观锁，以及完整 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member` 锁协议和幂等。
- **实施者／reviewer／并发：** 开发 C writer；开发 B 上下文/任务 reviewer；最多 2 人。rebase 和生产接线由 writer 串行执行。
- **精确路径／数据范围：** 仅本卡五个实现/测试文件、Task 1 `ScriptTaskInputMapper` 的必要语句、只读核验既有 `p3-f1-rebase.json`、创建 Git metadata 的 `p3-f2-rebase.json`/`p3-f3-rebase.json` 和 exact reports；不得改 P0-C/P1/P2 源码。
- **允许影响：** F2 可 import P1 五 DTO；F3 可 import P1 两 Service、P2 两 Service/六 DTO和 P0-C I/DTO；禁止 import 任一上游 Mapper/Entity/table/user VO，禁止 production/真实 IT fake，禁止 P0-C 同义包装。
- **前置／退出：** Tasks 1–3 F1 GREEN，P1/P2 handoff 各自独立 PASS；退出为两次 rebase 记录、DTO-only F2 tests、P2 最终 component registry 校验、真实锁/写入守卫并发 IT、冻结哈希/创建顺序/重用行为 GREEN。
- **结构签名检查点：** reviewer 核对生成/优化严格遵循完整全局锁序 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`：P2 锁入口先完成前两级，锁内重读 `branchRevision/questionnaireHash/knowledgeContextHash`，P0-C 接续后三步，再 freeze→enqueue→audit；`reused=true` 无重复副作用；P1 route A/B/C 及 snapshot 与 root task 一一对应。
- **GREEN 独立复跑检查点：** reviewer 在同一 candidate HEAD 删除 exact reports，以两个真实数据库连接和 barrier 独立复跑“任务先锁/写入先提交”两种交错，核对 P2 写入守卫、锁等待、hash 重读、quota/task/slot 行数和 enqueue/audit 回滚。
- **正向／反向验收：** 正向生成/重生成/优化均遵循唯一锁序、typed initiator、稳定 hash、重启回读；反向拒绝锁前快照继续、branch 后改问卷/知识 hash、倒序拿 quota/slot、非当前 branch、跨 owner source、重复 root 不同 payload、enqueue/audit 回滚和 P1/P2 越界依赖。
- **统一 gate：** Task 1 gate；rebase 前后都要求 clean linked worktree，unit/IT reports fresh，IT 带 local profile。
- **准确命令／证据：** unit selector `org.dromara.aivideo.script.service.impl.ScriptFrozenInputAssemblerTest,org.dromara.aivideo.script.service.impl.ScriptGenerationServiceImplTest`；IT selector `org.dromara.aivideo.script.ScriptFrozenInputPersistenceIT`；reports 分别位于 core Surefire/Failsafe 目录。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] F1/F2 rebase binding：先 strict 回读 Task 1 已创建的 `p3-f1-rebase.json`，现场重算 live P0-C addendum SHA/amendmentHead/original handoff/三类 evidence并要求全等；再按 Section 15.2 精确解析 P1 当前 23 字段 F2 schema，逐项核对 original F1/addendum/header、heads/ancestry/review/UTC、两个 live SHA、migration、stable Services/DTO、五 DTO record component/source registry、downstream/revision owner 和六类 live evidence。仅在 `handoff == live` 后生成 P3 component/source digest；旧 schema、缺失/额外/别名、binding/component/source 漂移均 RED。记录 before HEAD/merge-base，以 `git rebase $f2Head` 真实 rebase，确认 ancestry 后用 `FileMode.CreateNew` 写 exact `p3-f2-rebase.json`；同 payload 回读，差异拒绝覆盖。
- [ ] F2 RED/GREEN：`ScriptFrozenInputAssembler` 只接 DTO 参数和纯 scalar；使用精确 DTO fixtures 证明 A/B/C/fact refs/canonical JSON/hash。扫描 main 源码必须没有 P1 Service 调用；取得 fresh unit GREEN 后提交 `feat(script): assemble p1 dto frozen input`。
- [ ] F3 rebase：按 Section 15.3 精确解析 P2 当前 31 字段顶层 schema 和 12 字段 nested `p3ConsumerContract`；先要求七个 top-level↔nested 对象 canonical-equal，再验证 heads/hashes/review/evidence、六 DTO record header/component/source、两个 Service reflection/source、9 字段 locked-current-branch、7 字段 write-guard 和 11 字段 `contextSemantics` 全部等于 live。alias、缺失/额外字段、nested drift、旧 protocol 先 RED；全部通过后才真实 `git rebase $f3Head` 并以 CreateNew 写 `p3-f3-rebase.json`，绑定 F1 addendum、P1 component/source 与含 `p2ContextSemanticsSha256` 的 P2 七个 canonical digest；`lockCurrentContextForGeneration(Long,Long)` exact return/params、`Propagation.MANDATORY`、非 readOnly、全局锁序前两级 `draft -> current_branch` 的 scope/hash recheck 必须同时 live 证明。旧 readOnly context 不能冒充锁入口，任何旧草稿字段不得进入实现。
- [ ] F3 RED lock/TOCTOU：在 `ScriptFrozenInputPersistenceIT` 使用真实 P2 Service/写入守卫、两个事务连接和可控 barrier。交错 A：任务事务依次锁 `draft -> current_branch` 并暂停，P2 改答/补充/证据事务必须等待；A 继续按 `operation_slot -> quota_account -> task_or_group_member` 创建并提交后，B 通过 actual F1 source registry 的 `requireGenerationContextWritable` 精确拒绝且零业务写。交错 B：P2 写入先提交并改变 `questionnaireHash` 或 `knowledgeContextHash`，A 随后加锁重读并在 `operation_slot/quota_account/task_or_group_member` 之前以 46125 失败。锁 trace 必须逐字证明完整全局序列 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`，且没有 TOCTOU、死锁或倒序锁。
- [ ] GREEN generation：生成与优化外层 Service 都开启非 readOnly 写事务，并首先调用 `lockCurrentContextForGeneration(draftId,branchId)`；由 P2 在同一事务内按 tenant/owner 锁 `draft -> current_branch` 并验证 current。P3 只使用其返回 context 重检 request/source 的 `branchRevision/questionnaireHash/knowledgeContextHash`，再按 F1 addendum 现行 DTO 构造收费请求。生成固定 `SCRIPT_GENERATE`、`script_generate/script_regenerate`、resource `script_draft`、slot `script:{draftId}:{branchRevision}:{generationInputHash}`、family `script:{draftId}`、group `script:{draftId}:{branchRevision}`；优化固定 `SCRIPT_OPTIMIZE`、`script_optimize`、slot `optimize:{sourceVersionId}`、相同 family/group。P0-C 从 `operation_slot -> quota_account -> task_or_group_member` 接续，合并后的唯一全局锁序仍是 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`；现行 `inheritQuestionnaireTaskGroupMembers` 只继承 membership，两类非终态均被现行 `requireGenerationContextWritable` fail-closed。
- [ ] GREEN 冻结：P1 route/snapshot 只在 F3 首次生成调用；优化继承 immutable source snapshot。冻结 `questionnaireHash/knowledgeContextHash` 和 P2 handoff 标记的最小 identity/revision/hash 引用及 schema digest；P2 答案/补充/事实正文、source 文本/标题只在短生命周期内存。same root/request/input hash 回读，任一差异 46116。
- [ ] 验证/扫描：删除 exact XML、记录 start、运行 unit + 带 profile IT 并过 gate；对 production 和 `*IT.java` 扫描 `Fake|Stub|Mock`，对 core/import 扫描上游 mapper/entity/table/user VO，命中即失败；运行 `git diff --check`。
- [ ] review/提交：独立 reviewer 复核两个 rebase 文件、transaction trace、typed actor、rollback 和扫描，再提交 `feat(script): freeze chargeable script inputs`。

### 任务 5：实现 provider Handler、三版本原子落库和 P0-C 终态

**文件：** 创建 `ai-video-infra/.../script/provider/ScriptPromptFactory.java`、`ScriptGenerationTaskHandler.java`、`ScriptOptimizationTaskHandler.java` 及 unit tests；在 P3 已拥有的 `ai-video-infra/src/test/java/org/dromara/aivideo/script/` 创建 `ScriptGenerationBillingIT.java`；在 core `service/impl` 创建包私有 `ScriptGeneratedVersionWriter.java` 及测试。只注入现有 `ModelProvider`，不创建包装端口；本卡不得直接修改 owner registry 外的 `ai-video-integration-tests/**`。

**最小任务卡：**

- **单一目标／不做：** 通过真实 P0-C scanner/registry/lease/attempt 流程完成生成、修复、恢复、原子 A/B/C 写入、优化和唯一终态；不直接改 quota 表、不从 Controller 同步调用模型。
- **权威源：** P0-C 权威 I/DTO、Sections 2.2/3.3、Task 2 validators、Task 4 immutable input。
- **治理等级／触发项：** 红色；触发外部模型、真实成本、lease、最多三次 attempt、额度 settle/release 和 stale 结果。
- **实施者／reviewer／并发：** 开发 C writer；开发 A P0-C/事务 reviewer；最多 2 人。
- **精确路径／数据范围：** 仅本卡八个实现/测试文件、必要的 Handler registry 配置和 exact reports；IT 只写本机隔离 tenant/Redis run prefix，provider 使用可编程测试实现但 scanner/registry/task/billing 全真实。
- **允许影响：** 可注入 `IAiTaskService`、`IAiTaskAttemptService`、`ModelProvider`、core writer；禁止 Handler 注入 `IQuotaBillingService`、修改 P0-C、伪造 usage、在 provider 调用期间持有数据库事务或新增 provider wrapper。
- **前置／退出：** Task 4 F3 GREEN 且 production/real IT 无上游 fake；退出为 normal/repair/recovery/stale/invalid/lease/billing 全矩阵 unit + native IT GREEN。
- **结构签名检查点：** reviewer 核对 Handler 只实现 F1 actual source registry 中 `IAiTaskExecutionHandler` 的现行唯一执行方法，不在 P3 复述或包装参数签名；attempt 紧邻调用；10 秒 lease margin；result transaction 同时写三版本、result ref、markSuccess/settle。
- **GREEN 独立复跑检查点：** reviewer 用可编程 provider 分别返回 valid、invalid→valid、三次 infrastructure failure、第四次请求、stale、usage null、lease near-expiry，并核对 DB/Redis/调用计数。
- **正向／反向验收：** 正向一次 provider 生成 A/B/C 顺序三个版本、一次 settle、结果引用 A；优化一个 child；反向 invalid 两次无版本并 release、freeze 缺失/损坏 attempt=0、第四次 provider=0、stale 无版本/无 settle/无 retry、lease owner/expiry 错误不能推进。
- **统一 gate：** Task 1 gate；billing IT 必带 local profile，删除 exact Failsafe XML 后运行；provider unit 与 IT reports 分开验证。
- **准确命令／证据：** unit selector `org.dromara.aivideo.script.provider.ScriptGenerationTaskHandlerTest,org.dromara.aivideo.script.provider.ScriptOptimizationTaskHandlerTest,org.dromara.aivideo.script.service.impl.ScriptGeneratedVersionWriterTest`；IT selector `org.dromara.aivideo.script.ScriptGenerationBillingIT` 位于 `:ai-video-infra`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptGenerationBillingIT.xml`，命令含 `'-Pdev,local-integration-test'`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] RED attempts/lease：覆盖 initial=1、repair=2、recovery=3、申请 4 在 provider 前拒绝；provider deadline 至少 `leaseExpiresAt-10s`，不足时先 renew 且后续条件写使用新 lease。
- [ ] RED invalid/freeze：缺失、hash 不符、JSON 损坏转换为 non-retryable `SCRIPT_FROZEN_INPUT_INVALID`，attempt=0；结构 invalid repair 后仍失败则无版本、扫描器 markFailed/release 恰一次。
- [ ] RED stale：provider 返回后重新条件读取 `branchRevision/questionnaireHash/knowledgeContextHash/generationContextRevision/generationInputHash`；任一变化完成 attempt 的真实 usage，但不写版本、不 markSuccess、不 settle、不 provider retry，扫描器以 `STALE_BRANCH_RESULT` 失败并 release。
- [ ] GREEN provider：事务外构建 prompt/调用现有 `ModelProvider`；敏感字段不进日志。调用前 `startAttempt`，成功/失败都记录真实 `ProviderUsageDTO`；未知值保持 null。
- [ ] GREEN persistence：生成结果按 A/B/C 固定顺序一次插入三 immutable versions；写入前由 `ScriptRecommendationReasonFormatter` 使用冻结 route rank + 对应 `KnowledgePlanDTO` 派生 angleSummary，provider 无输入权。三个都成功才以 A version 作为 `TaskResultReferenceDTO`；同一事务 `markSuccess`。优化只生成 source 的一个 child，继承 source snapshot/context/rank 并用同 formatter version 重建理由，不能 reroute。
- [ ] GREEN billing IT：使用真实 scanner、handler registry、lease、task/attempt/billing Mapper 与可编程 provider；断言锁一次、settle 或 release 恰一次、无双终态、恢复不重复收费、三个版本原子性、跨 tenant 不可见。
- [ ] 验证/review：逐份 fresh gate、标准扫描、`git diff --check`；独立 reviewer 重放 attempt/lease/stale trace 后提交 `feat(script): execute billed script generation`。

### 任务 6：实现七个用户端接口、typed actor 与脱敏审计

**文件：** 创建 `ai-video-user/src/main/java/org/dromara/aivideo/user/script/controller/UserScriptController.java`；创建 `domain/bo/ScriptGenerationBo.java`、`ScriptManualVersionBo.java`、`ScriptOptimizationBo.java`、`ScriptConfirmationBo.java`、`UserScriptQueryBo.java`；创建 `domain/vo/ScriptTaskVo.java`、`ScriptVersionVo.java`、`ScriptConfirmationVo.java`、`UserScriptSummaryVo.java`、`UserScriptDetailVo.java`；创建同模块 `UserScriptControllerTest.java`。P3 writer 另提交 `ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/ScriptUserApiIT.java` 的 exact change request；该 IT 只能由共享 owner 在显式 Task 6 串行窗口创建，P3 writer 不得直接修改 owner registry 外的 `ai-video-user-api/**`。

**最小任务卡：**

- **单一目标／不做：** 暴露七个 app_user HTTP 接口并落实权限、owner、分页、错误、幂等和同事务安全审计；不在 Controller 写业务逻辑、不使用 sys_user 上下文、不增加同步模型接口。
- **权威源：** `docs/API_CONTRACT.md`、Sections 2.1/2.3、P0-A/B 稳定契约、现有 app Controller/exception handler/MapstructUtils 相似实现。
- **治理等级／触发项：** 红色；触发用户鉴权、对象越权、正文读写、删除、审计与外部 API。
- **实施者／reviewer／并发：** 开发 C writer；开发 B API/安全 reviewer；最多 2 人。
- **精确路径／数据范围：** P3 writer 的 `AllowedPaths` 仅含 12 个 master-owned main/unit files；共享 owner 的独立窗口仅含 exact `ScriptUserApiIT.java` 与其 report，不允许整个 `ai-video-user-api/**` 通配。两者身份、UTC 窗口和 change-request SHA 必须进入本卡 review evidence。
- **允许影响：** 可调用三个 P3 Service、`AppActorContext`、workspace authorizer、`IAppSecurityAuditService`；禁止 `LoginHelper`、默认 `@Log`、裸 Mapper、散落错误 envelope、审计正文/标题/prompt/token/output。
- **前置／退出：** Tasks 3–5 F3 GREEN；退出为 Controller unit + 共享 owner 创建的 `ScriptUserApiIT` 对七接口、permission、typed actor、cross-owner、audit、分页/状态全部 GREEN。
- **结构签名检查点：** reviewer 核对路径/HTTP method/权限、Java Long→HTTP string、五类 library filters、flat versions/currentConfirmation/downstreamReferences、sort whitelist、`@RepeatSubmit` 写操作、同事务 audit exact DTO fields/action 及 append 失败整体回滚。
- **GREEN 独立复跑检查点：** reviewer 用 app token/sys token、两个 tenant/workspace/owner、重复请求、失败事务、分页越界和敏感 payload 独立复跑。
- **正向／反向验收：** 正向七接口、rows=[]、五过滤组合、flat versions、currentConfirmation、三类下游引用摘要、审计五 action；反向 sys token=401、权限不足=403、跨 workspace/owner fail-closed、失败事务无审计、pageSize>100、非法过滤/时间/sort、嵌套 versions、伪造 ID/title、引用删除 46118、上下文冲突。
- **统一 gate：** Task 1 gate；P3 writer 与共享 owner 分别用不重叠的 exact `AllowedPaths`，前者 12 个 owned files，后者只允许 `ScriptUserApiIT.java`/fresh report；Controller unit 用 Surefire，启动装配/真实 DB Redis 用 Failsafe + local profile。
- **准确命令／证据：** unit selector `org.dromara.aivideo.user.script.controller.UserScriptControllerTest`；共享 owner IT selector `org.dromara.aivideo.bootstrap.ScriptUserApiIT` 位于 `:ai-video-user-api`，exact report `ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.ScriptUserApiIT.xml`，IT 命令必须含 `'-Pdev,local-integration-test'`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] RED endpoint：逐一覆盖 `POST /api/studio/script-generations`、`POST /api/studio/scripts/{id}/versions`、`POST /api/studio/scripts/{id}/optimizations`、`POST /api/studio/scripts/{id}/confirmations`、`GET /api/studio/scripts`、`GET /api/studio/scripts/{id}`、`DELETE /api/studio/scripts/{id}`。
- [ ] RED security：权限分别为 generate=`aivideo:studio:generate`、edit=`aivideo:script:edit`、optimize=generate+Service 内 edit/quota use、confirm=`aivideo:script:confirm`、query、remove；sys token 401，跨 workspace/object 拒绝；写操作 `@RepeatSubmit`。
- [ ] RED query：pageSize 1..100、空页 `rows=[]`、sort 只允许 `updateTime/createTime/displayTitle`；list 精确支持 `industryCode/purposeCode/sourceType/confirmationStatus/updatedTimeStart/updatedTimeEnd`，覆盖五类组合与 start-inclusive/end-exclusive。detail 响应精确为 flat immutable `versions`、`currentConfirmation={confirmationId,versionId,selectedTitleIndex,selectedTitle,confirmedAt}`、`downstreamReferences={voiceCount,digitalHumanCount,workCount,total,latestGeneratedAt}`；禁止嵌套 children，历史 snapshot 不替换当前知识，断链/环仍 fail-closed。
- [ ] GREEN mapping：BO/VO 只在 user 模块；Controller 只做校验、typed actor/权限、Service 调用、MapstructUtils 映射。所有 Long ID 输出十进制 string；输入拒绝 number/非十进制/溢出。
- [ ] GREEN audit：generate/version/optimize/confirm/remove 在各自业务事务提交前 append exact `AppSecurityAuditDTO`；before/after digest 只含 resource/revision/hash，不含敏感正文。append 失败使业务和审计整体回滚；业务失败/幂等回读不新增审计。
- [ ] GREEN IT：真实启动装配、app auth、workspace owner、P3 Service/DB/Redis；不用 production/P1/P2 fake。七路径状态码、错误码、响应 shape、审计行全部断言。
- [ ] 验证/review：fresh XML gate、敏感词/`@Log`/`LoginHelper`/裸 Mapper scan、`git diff --check`；独立 reviewer 复跑安全矩阵后提交 `feat(script): expose app user script api`。

### 任务 7：实现 TypeScript 边界、独立 Mock 和文案工作流

**文件：** 创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/scripts/types.ts`、`api.ts`、`queryKeys.ts`、`adapter.ts`、`contract.test.ts`、`mock/aivideo-scripts.ts`、`src/pages/digital-human-studio/hooks/useScriptFlow.ts`、`useScriptFlow.test.tsx`、`components/ScriptCandidateTabs.tsx`、`ScriptCandidateTabs.test.tsx`、`components/ScriptVersionEditor.tsx`、`ScriptVersionEditor.test.tsx`、`components/ScriptOptimizationActions.tsx`、`ScriptOptimizationActions.test.tsx`；修改 `steps/ScriptStep.tsx` 及其测试。组件/测试文件名与 master ownership registry 逐项一致，禁止创建 Cards/Editor/ConfirmationPanel 同义文件。

**最小任务卡：**

- **单一目标／不做：** 建立七接口唯一 TypeScript client/adapter/query-key，独立 Mock 和可恢复的生成—编辑—优化—确认 UI；不接共享根路由、不实现用户文案库。
- **权威源：** Task 6 HTTP shape、Section 2.1 DTO、`docs/FRONTEND_*`、现有 P0-C task/quota adapter、Ant Design 官方 CLI/文档。
- **治理等级／触发项：** 黄色；触发付费确认、任务恢复、ID 精度、冲突、失败/空状态和用户正文编辑。
- **实施者／reviewer／并发：** 开发 C writer；开发 A 前端状态/a11y reviewer；最多 2 人。可在 F1 先做 types/mock/local component，F3 后只替换真实 client 接线。
- **精确路径／数据范围：** 仅本卡 16 个 tracked 文件；JSON 报告固定写 `git-metadata:p3-reports/p3-task7-script-flow.json`，不进入 worktree status。types/API/queryKeys/runtime adapter/contract test 全部收敛在 `src/services/ai-video/scripts/`，query state 按 workspace/draft/branch/script/rootTask 隔离。
- **允许影响：** 可复用 P0-C task/quota 类型但不复制；禁止散落 URL/状态字符串/错误码、复用 P2 Mock、number ID、自动换幂等 key、无限重试或把 cancel 当 failure。
- **前置／退出：** F1 可先基于冻结 shape 开发，Task 6 F3 GREEN 后执行 contract replay；退出为 exact Vitest files、lint、API+Mock+组件三重状态矩阵 GREEN。
- **结构签名检查点：** reviewer 核对七 API、所有 ID string、page adapter `rows:null -> []`、query key 全维度、六个精确优化类型、同一用户 intent 稳定 idempotency、Clipboard 成功/失败反馈、AbortController/timer cleanup。
- **GREEN 独立复跑检查点：** reviewer 删除 exact JSON，用 fake timers、online/focus、网络断开、Clipboard resolve/reject 和 401/403/46116/46123/46125 独立复跑，确认请求/复制/反馈次数精确。
- **正向／反向验收：** 正向 A/B/C 三卡、每卡三标题/确定性推荐理由/字符/时长/模板、六类优化、复制成功/失败反馈、人工编辑、确认、任务恢复；反向拒绝 provider 推荐理由、未知优化类型、复制失败却提示成功、number ID、缺候选/标题、重复提交、自动换 key、401 logout 循环、403 重试、取消失败化、终态继续轮询、跨 branch 缓存。
- **统一 gate：** Task 1 gate 的 Vitest 模式；`AllowedPaths` 精确列 16 个 tracked 文件，`ReportRelativePath='git-metadata:p3-reports/p3-task7-script-flow.json'`，expected files 精确六个测试文件。
- **准确命令／证据：** 动态解析 `git rev-parse --git-path p3-reports/p3-task7-script-flow.json` 为绝对输出路径，从 web root 运行 `npm.cmd test -- src/services/ai-video/scripts/contract.test.ts src/pages/digital-human-studio/hooks/useScriptFlow.test.tsx src/pages/digital-human-studio/components/ScriptCandidateTabs.test.tsx src/pages/digital-human-studio/components/ScriptVersionEditor.test.tsx src/pages/digital-human-studio/components/ScriptOptimizationActions.test.tsx src/pages/digital-human-studio/steps/ScriptStep.test.tsx --reporter=json --outputFile=<动态 Git metadata 路径>`，随后 `npm.cmd run lint`，再以六个 repo-relative test files 调 Vitest gate。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置：使用项目 `antd` skill/官方 CLI 核验 Card/Tabs/Radio/Alert/Progress/Skeleton/Modal/Input/TextArea API、semantic DOM 和 token；读取现有 task polling/query key/401 logout 实现。
- [ ] RED services contract：`src/services/ai-video/scripts/contract.test.ts` 证明 ID 只能 string、请求/响应字段 exact、list null rows、未知 enum/过滤、非法 ID、重复候选/标题均 fail-closed；七 URL 只在 `api.ts`，query keys 只在 `queryKeys.ts`，runtime validation 只在 `adapter.ts`。扫描旧 `src/services/aivideo`、顶层 `src/types/aivideo-script.ts`、`src/adapters/aivideo-script.ts` 必须零命中。
- [ ] RED Mock：`mock/aivideo-scripts.ts` 自己维护 workspace/draft/branch/task/version fixtures，不 import `aivideo-studio.ts`；覆盖正常、无 candidates、quota low、rate changed、slot occupied、stale、conflict、failed/cancelled/expired 和 network/timeout/5xx。
- [ ] RED hook：一个用户 intent 只生成一个 key，网络/超时/5xx 的用户点击 retry 复用；46123 绑定 active root；46116 要求重新确认；46125 刷新 context；轮询前 10 秒每秒、之后每 2 秒，success/failed/cancelled/expired/离页/branch change 清理。
- [ ] GREEN API/query：query keys 精确包含 workspace/draft/branch/script/version/rootTask；成功只 invalidate 受影响 key；401 只触发一次 logout，403 不 retry；网络最多有界提示且只有用户动作重试；cancel 显示中性终态。
- [ ] GREEN UI：提交前显示费用/费率/额度和重复保护；loading skeleton、initial empty、no candidates、queued/running/success/failed/cancelled/expired；A/B/C 固定顺序，每套三个 title radio、服务端确定性推荐原因、规则指标。优化下拉精确六项 `shorter/more_colloquial/strengthen_selling_points/change_hook/strengthen_call_to_action/custom`；复制按钮调用 `navigator.clipboard.writeText(scriptText)`，resolve 显示“文案已复制”，reject 显示“复制失败，请手动选择文本”，失败不得提示成功；编辑/优化/确认都显示 context/version 冲突恢复。
- [ ] GREEN 测试：Testing Library 只按 role/name/visible text；fake timers 后无 pending timer；a11y 键盘遍历和 focus return；删除旧 JSON、记录 start、运行 exact tests/lint/Vitest gate。
- [ ] review/提交：独立 reviewer 用 Mock 状态表逐行核对 API+Mock+组件都有断言，再提交 `feat(studio): add script generation workflow`。

### 任务 8：实现用户文案库、版本详情与下游准确引用

**文件：** 创建 `components/UserScriptLibrary.tsx`、`UserScriptLibrary.test.tsx`、`components/ScriptDetailDrawer.tsx`、`ScriptDetailDrawer.test.tsx`；修改 P3 已拥有的 `components/LibraryView.tsx`、`steps/VoiceStep.tsx`、`steps/BaseStep.tsx` 和 `steps/ScriptStep.test.tsx`。LibraryView/Voice/Base/model 的场景测试合并进上述三个已拥有 test files；本卡不创建或修改 owner registry 外的 `LibraryView.test.tsx`、`VoiceStep.test.tsx`、`BaseStep.test.tsx`、`model.ts`、`model.test.ts`。

**最小任务卡：**

- **单一目标／不做：** 提供五类可组合过滤的分页文案库，由 flat immutable versions 在前端组装版本树，展示当前确认和三类下游引用摘要，并让下游只引用已确认的准确版本/标题 index；不复制正文到下游状态、不提前改根 app 路由。
- **权威源：** Task 6 list/detail/delete API、Task 7 adapter/query keys、PRD 文案库/步骤流、Ant Design ProComponents 官方文档。
- **治理等级／触发项：** 黄色；触发历史版本、删除、下游引用和共享工作台状态。
- **实施者／reviewer／并发：** 开发 C writer；开发 B 产品/联调 reviewer；最多 2 人。
- **精确路径／数据范围：** 仅本卡八个 master-owned tracked files；JSON 报告固定写 `git-metadata:p3-reports/p3-task8-script-library.json`，不进入 worktree status。若 confirmed reference 的最小 shape 需要 `model.ts` 变化，本卡只提交 exact change request，由共享 owner 在 Task 9 串行窗口应用，P3 task writer 不直接写。
- **允许影响：** 可使用 ProTable/Drawer/Descriptions/Tree、Task 7 client；禁止直接 URL、array index 充当 version ID、存正文副本、跳过 access revalidation、未确认版本进入 Voice/Base。
- **前置／退出：** Tasks 3/6/7 GREEN；退出为列表/详情/删除/下游引用全状态 exact Vitest + lint GREEN。
- **结构签名检查点：** reviewer 核对 server pagination/sort/filter mapping、后端 flat versions 与前端唯一组树器、currentConfirmation、downstreamReferences 五字段、source task/snapshot/三标题、confirmed reference shape `{scriptId,versionId,selectedTitleIndex}`。
- **GREEN 独立复跑检查点：** reviewer 在两 workspace、两页数据、断链/cycle、引用 count 变化、确认版本失权场景中独立复跑；检查下游 state 无正文/标题副本。
- **正向／反向验收：** 正向 skeleton、初始空、搜索无结果、五过滤任意组合、分页/排序、retry、403、flat→tree、当前确认、三类引用摘要、删除确认、copy 成败、cache invalidate、下游准确引用；反向非法过滤/时间/sort、Mock 漏过滤、服务端嵌套 tree、客户端全量分页、删除无确认、46118 静默、断链/cycle 渲染、未确认/跨 owner/失权版本下游继续、下游 state 复制正文。
- **统一 gate：** Task 1 Vitest gate；`AllowedPaths` 精确列本卡八个 master-owned tracked files，`ReportRelativePath='git-metadata:p3-reports/p3-task8-script-library.json'`，ExpectedTestFiles 精确三个测试文件。
- **准确命令／证据：** exact tests 为 `UserScriptLibrary.test.tsx`、`ScriptDetailDrawer.test.tsx`、`steps/ScriptStep.test.tsx`；后者集中覆盖 LibraryView/Voice/Base 和 confirmed reference 行为。JSON 使用动态 Git metadata 路径 `git-metadata:p3-reports/p3-task8-script-library.json`，随后 lint 和 gate。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置：使用项目 `antd` skill/CLI 核验 ProTable/Drawer/Descriptions/Tree/Popconfirm/Result/Pagination 的 API、empty/loading/error semantic DOM；读取现有 LibraryView 与步骤 state。
- [ ] RED list：HTTP server params exact 为 `pageNum/pageSize/keyword/draftId/industryCode/purposeCode/sourceType/confirmationStatus/updatedTimeStart/updatedTimeEnd/sortField/sortOrder`，ProTable `current` 只在 adapter 映射为 pageNum；五类 filters 单独与全组合都由 adapter、Mock、ProTable 贯穿，时间 start-inclusive/end-exclusive；UI sort 映射 `updatedAt->updateTime`、`createdAt->createTime`、`title->displayTitle`；再覆盖 loading、empty、page boundary、network/timeout/5xx、401/403、rows null。
- [ ] RED detail/delete：response 必须是 flat generated/manual_edit/ai_optimized versions；前端按 parentVersionId 组树并检测 duplicate/missing/cycle。断言 exact currentConfirmation 和 downstreamReferences，copy resolve/reject 反馈；删除用 downstreamReferences.total，二次确认、46118 阻塞和 list/detail keys 精确 invalidation。
- [ ] RED downstream：在 master-owned `ScriptStep.test.tsx` 中证明 Voice/Base 只接受当前 app_user 可访问且已确认的 `{scriptId,versionId,selectedTitleIndex}`；进入步骤和提交生成前都 revalidate；版本被删/失权/取消确认时阻止继续并返回 ScriptStep；同时覆盖 LibraryView tab/deep link/cache scope，不创建三个未登记的同名 step/view tests。
- [ ] GREEN：ProTable 只做展示/事件，分页排序全部服务端；Drawer 不替换历史 snapshot；下游需要展示标题时按 ID 再查当前确认结果，state 不存正文或 array position。
- [ ] GREEN 验证：三个 exact Vitest files 的 JSON fresh、tests>0/pending=0/failed=0、lint；用 `rg` 证明 downstream state 没有 `scriptText/publishTitles/selectedTitle` 字段副本，并证明本卡 diff 未触及 owner registry 外的 view/step/model tests 或 `model.ts`。
- [ ] review/提交：独立 reviewer 逐状态键盘复跑和引用数据审计后提交 `feat(studio): add user script library`。

### 任务 9：串行集成工作台，执行完整门禁并冻结 F4

**文件：** P3 writer 提交 exact change request；共享 owner 在显式串行窗口内修改 `ai-video-ui/ai-video-webapp/src/app.tsx`、`src/pages/digital-human-studio/index.tsx`、`index.test.tsx`、`components/StudioTopbar.tsx`、`model.ts`，如且仅如契约已有差异再修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`。P3 writer 不得直接修改这些 owner registry 外路径；`LibraryView.tsx` 已在 Task 8 由 P3 owner 完成，不属于本卡共享修改。所有 acceptance/review/evidence/handoff 只写当前 worktree Git metadata。

**最小任务卡：**

- **单一目标／不做：** 由共享 owner 串行接入 P3 根入口，运行 `01→02→03→04→04a→05→06→07`、14 个 unit、5 个真实 IT、9 个 unique Vitest files、标准/扫描和独立 review，创建不可变 F4 handoff；不实现 P4、不让 writer 自签、不 push。
- **权威源：** 前八卡、F2/F3 rebase 记录、P1/P2 handoff、master 代表性门禁、Section 6 F4 schema。
- **治理等级／触发项：** 红色；触发共享入口、完整收费链、迁移、验收证据、独立 review 和下游 handoff。
- **实施者／reviewer／并发：** 开发 C 是 change-request/证据 owner；当前共享 owner 是唯一 tracked-file writer；开发 A 或 B 中未参与本卡写代码者担任独立 reviewer；最多 2 人。共享 owner 窗口、acceptance、review、handoff 全部串行。
- **精确路径／数据范围：** 共享 owner 窗口只含本卡五个 tracked files、必要时三个契约文档；P3 evidence 范围仅含三个 frontend JSON、14 Surefire XML、5 Failsafe XML、标准/扫描日志及 Git metadata manifests/review/handoff；本机数据库/Redis 仍按独立 run 隔离。
- **允许影响：** 可将 Tasks 7–8 接入根工作台、创建 F4 evidence；禁止改 P1/P2、保留 production/real IT fake、用旧报告、宽泛 clean/delete、覆盖 handoff、访问开发/生产数据。
- **前置／退出：** Tasks 1–8 均提交，F2/F3 rebase 记录可验证且 worktree clean；退出为所有 fresh gates、迁移 replay、完整 UI 状态、独立 PASS、F4 幂等回读。
- **结构签名检查点：** reviewer 核对三 Service/八 DTO registry、四表、七 API、14/5/9 unique test registry、前端 `services/ai-video/scripts/**` ownership、P0-C attempt/lease/settle、P1/P2 import 白名单、typed actor/audit、F4 schema/hash/window。
- **GREEN 独立复跑检查点：** reviewer 在同一 candidate HEAD 和 acceptance window 内删除并重建所有 exact reports，复算 bytes/mtime/SHA、运行代表性全链与 scan；只有 reviewer 可创建 PASS record。
- **正向／反向验收：** 正向 F0→F4 ancestry、`07` replay、A/B/C/优化/确认/库/下游、全状态与 handoff 幂等；反向拒绝 HEAD 漂移、旧/零/skip 报告、缺 IT profile、假服务、越界 import、writer 自签、证据 hash 漂移、不同 payload 覆盖。
- **统一 gate：** Task 1 gate + 本卡 Section 6 机械扫描；共享 owner 开窗时 `AllowedPaths` 只列本卡五个 shared tracked files 与已批准的契约文档，禁止加入 `LibraryView.tsx`、owner registry 外 tests 或目录通配符；P3 writer 的 change request 与 shared-owner 身份/UTC 窗口由 reviewer 回读。最终 worktree gate 必须同时传 `-RequireClean -StartedAtUtc ([DateTimeOffset]::UtcNow)` 且返回 `P3_WORKTREE_GATE_OK`。
- **准确命令／证据：** 使用下面“完整命令注册表”和“F4 机械扫描”；所有 IT 命令逐条含 `'-Pdev,local-integration-test'`，不接受只跑 Maven aggregate 退出码。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 串行入口 RED：共享 owner 在 `index.test.tsx` 覆盖 auth recovery、步骤/库切换、deep link detail、workspace/branch change 清 cache、未确认版本阻止下游、全局 401/403/404/5xx boundary；取得 fresh Vitest RED。P3 writer 只提供断言/change request，不直接写该文件。
- [ ] 串行入口 GREEN：共享 owner 使 `app.tsx` 只注册路由/Mock，`index.tsx` 组合页面，Topbar/model 只使用 Task 7 query key/types；Task 8 的 owned `LibraryView.tsx` 只读取这些公开入口，不复制 URL/状态/正文。运行 exact test、lint、build 和 Vitest gate。
- [ ] 接受窗口：严格引用 Section 15.7；在 clean candidate HEAD 以 `FileMode.CreateNew` 创建 `p3-acceptance-window-<f4Head>.json`，字段名称和顺序逐字为 `f1Head/f1AmendmentHead/f2Head/f3Head/f4Head/p1HandoffSha256/p2HandoffSha256/f1AddendumSha256/startedAtUtc`。存在时 exact 回读；旧 7 字段 `f1Head/f2Head/f3Head/f4Head/p1HandoffSha256/p2HandoffSha256/startedAtUtc`、缺失/额外/乱序字段或 payload 不同必须拒绝，后续 report mtime 必须位于 window 内。
- [ ] 迁移：先安全环境检查，再运行 `ScriptSchemaMigrationIT`，它必须从空 schema 执行 `01→02→03→04→04a→05→06→07`，验证 `04a` addendum 精确 DDL/锁序相关约束，再执行一次 `07` 并断言四表/约束/索引/不可变保护不漂移；保存 fresh Failsafe XML。
- [ ] Unit/IT：逐模块执行 Section 6.1 精确 selector；每次先删本 selector exact XML、记录 start、执行、以 expected suite 调 gate；编译失败、selector 未命中、tests=0、skipped>0、报告旧均失败。
- [ ] 前端：运行 Task 7 六文件、Task 8 三文件、Task 9 `index.test.tsx` 三份 fresh JSON；`ScriptStep.test.tsx` 在 Task 7/8 两组重复执行但 manifest 按路径去重，三组并集精确 9 files。再执行 lint/build，逐行核对 API + 独立 Mock + 可见组件三重状态矩阵，并扫描旧前端路径零命中。
- [ ] 标准/扫描：运行 `scripts/validate-development-standards.ps1`、Section 6.2 scan、`git diff --check`；成功日志分别以 sentinel `P3_STANDARDS_OK`、`P3_SCAN_OK` 结束并 CreateNew 存于 `git-metadata:p3-evidence/<f4Head>/`；standards.log 必须同时包含真实脚本的 `DEVELOPMENT_STANDARDS_OK`。
- [ ] 六 manifests：以 CreateNew/幂等回读创建 `unit/it/migration/vitest/standards/scan.manifest.json`；每个 artifact 记录 repo-relative path、SHA-256、bytes、UTC mtime、suite/test files/count；集合必须与注册表精确相等。
- [ ] 独立 review：严格引用 Section 15.9；reviewer 复跑代表性硬门禁并实时复算六 manifest，只能 reviewer 创建 `p3-independent-review.json`，字段名称和顺序逐字为 `owner/reviewer/reviewStatus/reviewedHead/windowStartUtc/windowEndUtc/representativeSelectors/evidence/findingsClosed/reviewedAtUtc`，status 只能 PASS，owner/reviewer 不同。readback 必须拒绝旧 8 字段 `owner/reviewer/reviewStatus/reviewedHead/windowStartUtc/windowEndUtc/evidenceSha256/reviewedAtUtc`、缺失/额外/乱序字段和 scalar evidence；writer 无权创建或覆盖。
- [ ] F4 handoff：writer 只能在独立 PASS 后按 Section 6.3 CreateNew；二次运行返回原 hash/time，不同 HEAD/payload 拒绝覆盖。最终 gate 要求 clean，不 push。

---

## 6. F4 可执行门禁与 handoff

### 6.1 完整测试注册表和命令

所有命令都先执行：

```powershell
$ErrorActionPreference='Stop'
$rootText=(& git rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$repoRoot=[IO.Path]::GetFullPath($rootText.Trim())
Set-Location -LiteralPath $repoRoot
$apiRoot=Join-Path $repoRoot 'ai-video-api'
$webRoot=Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'
$gateText=(& git rev-parse --git-path 'p3-evidence-gate.ps1').Trim()
$gate=if([IO.Path]::IsPathRooted($gateText)){$gateText}else{Join-Path $repoRoot $gateText}
if(-not (Test-Path -LiteralPath $gate -PathType Leaf)){throw '缺 P3 evidence gate'}
function Resolve-P3ArtifactPath([string]$reference){
  if($reference.StartsWith('git-metadata:',[StringComparison]::Ordinal)){
    $relative=$reference.Substring('git-metadata:'.Length)
    if([string]::IsNullOrWhiteSpace($relative) -or [IO.Path]::IsPathRooted($relative) -or $relative.Contains('..')){throw '非法 Git metadata artifact path'}
    $raw=(& git rev-parse --git-path $relative).Trim()
    if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)){throw '无法解析 Git metadata artifact path'}
    return $(if([IO.Path]::IsPathRooted($raw)){[IO.Path]::GetFullPath($raw)}else{[IO.Path]::GetFullPath((Join-Path $repoRoot $raw))})
  }
  if([IO.Path]::IsPathRooted($reference)){throw 'artifact 必须是 repo-relative 或 git-metadata 引用'}
  return [IO.Path]::GetFullPath((Join-Path $repoRoot $reference))
}
```

Unit 注册表固定 14 个 suite：

```text
org.dromara.aivideo.script.ScriptContractRegistryTest
org.dromara.aivideo.script.service.impl.EffectiveCharacterCounterTest
org.dromara.aivideo.script.service.impl.ScriptGenerationResultValidatorTest
org.dromara.aivideo.script.service.impl.ScriptOptimizationResultValidatorTest
org.dromara.aivideo.script.service.impl.ScriptFrozenInputValidatorTest
org.dromara.aivideo.script.service.impl.ScriptVersionServiceImplTest
org.dromara.aivideo.script.service.impl.UserScriptQueryServiceImplTest
org.dromara.aivideo.script.service.impl.ScriptFrozenInputAssemblerTest
org.dromara.aivideo.script.service.impl.ScriptGenerationServiceImplTest
org.dromara.aivideo.script.service.impl.ScriptGeneratedVersionWriterTest
org.dromara.aivideo.script.provider.ScriptProviderResponseParserTest
org.dromara.aivideo.script.provider.ScriptGenerationTaskHandlerTest
org.dromara.aivideo.script.provider.ScriptOptimizationTaskHandlerTest
org.dromara.aivideo.user.script.controller.UserScriptControllerTest
```

Core、infra、user 分别执行，禁止把跨模块 selector 塞进单模块：

```powershell
$unitGroups=@(
  [pscustomobject]@{
    Artifact='ai-video-core'
    ReportDir='ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports'
    Suites=@(
      'org.dromara.aivideo.script.ScriptContractRegistryTest',
      'org.dromara.aivideo.script.service.impl.EffectiveCharacterCounterTest',
      'org.dromara.aivideo.script.service.impl.ScriptGenerationResultValidatorTest',
      'org.dromara.aivideo.script.service.impl.ScriptOptimizationResultValidatorTest',
      'org.dromara.aivideo.script.service.impl.ScriptFrozenInputValidatorTest',
      'org.dromara.aivideo.script.service.impl.ScriptVersionServiceImplTest',
      'org.dromara.aivideo.script.service.impl.UserScriptQueryServiceImplTest',
      'org.dromara.aivideo.script.service.impl.ScriptFrozenInputAssemblerTest',
      'org.dromara.aivideo.script.service.impl.ScriptGenerationServiceImplTest',
      'org.dromara.aivideo.script.service.impl.ScriptGeneratedVersionWriterTest'
    )
  },
  [pscustomobject]@{
    Artifact='ai-video-infra'
    ReportDir='ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports'
    Suites=@(
      'org.dromara.aivideo.script.provider.ScriptProviderResponseParserTest',
      'org.dromara.aivideo.script.provider.ScriptGenerationTaskHandlerTest',
      'org.dromara.aivideo.script.provider.ScriptOptimizationTaskHandlerTest'
    )
  },
  [pscustomobject]@{
    Artifact='ai-video-user'
    ReportDir='ai-video-api/ruoyi-modules/ai-video/ai-video-user/target/surefire-reports'
    Suites=@('org.dromara.aivideo.user.script.controller.UserScriptControllerTest')
  }
)
foreach($group in $unitGroups){
  foreach($suite in $group.Suites){
    $report=Join-Path $repoRoot ($group.ReportDir+'/TEST-'+$suite+'.xml')
    Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue
  }
  $started=[DateTimeOffset]::UtcNow
  $selector=$group.Suites -join ','
  Push-Location $apiRoot
  try{
    & .\mvnw.cmd -pl (':' + $group.Artifact) -am -Dmaven.test.skip=false -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false ('-Dtest=' + $selector) test
    if($LASTEXITCODE -ne 0){throw "unit 失败：$($group.Artifact)"}
  }finally{Pop-Location}
  foreach($suite in $group.Suites){
    $relative=$group.ReportDir+'/TEST-'+$suite+'.xml'
    $sentinel=& $gate -RepoRoot $repoRoot -Mode junit -AllowedPaths @() -ReportRelativePath $relative -ExpectedSuite $suite -Phase GREEN -StartedAtUtc $started
    if($LASTEXITCODE -ne 0 -or $sentinel -cne 'P3_JUNIT_EVIDENCE_OK'){throw "unit gate 失败：$suite"}
  }
}
```

上面脚本已在每组执行前删除 exact XML并记录 start，执行后对 14 个 suite 逐份调用 junit gate；不能删掉这些步骤只检查三条 Maven 退出码。

IT 注册表固定 5 个 suite，逐条执行以避免共享数据库 fixture 相互污染：

```powershell
$itCases=@(
  [pscustomobject]@{Artifact='ai-video-core';Suite='org.dromara.aivideo.script.ScriptSchemaMigrationIT';Report='ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptSchemaMigrationIT.xml'},
  [pscustomobject]@{Artifact='ai-video-core';Suite='org.dromara.aivideo.script.ScriptVersionPersistenceIT';Report='ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptVersionPersistenceIT.xml'},
  [pscustomobject]@{Artifact='ai-video-core';Suite='org.dromara.aivideo.script.ScriptFrozenInputPersistenceIT';Report='ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptFrozenInputPersistenceIT.xml'},
  [pscustomobject]@{Artifact='ai-video-infra';Suite='org.dromara.aivideo.script.ScriptGenerationBillingIT';Report='ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptGenerationBillingIT.xml'},
  [pscustomobject]@{Artifact='ai-video-user-api';Suite='org.dromara.aivideo.bootstrap.ScriptUserApiIT';Report='ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.ScriptUserApiIT.xml'}
)
foreach($case in $itCases){
  $report=Join-Path $repoRoot $case.Report
  Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue
  $started=[DateTimeOffset]::UtcNow
  Push-Location $apiRoot
  try{
    & .\mvnw.cmd -pl (':' + $case.Artifact) -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false ('-Dit.test=' + $case.Suite) '-Pdev,local-integration-test' verify
    if($LASTEXITCODE -ne 0){throw "IT 失败：$($case.Suite)"}
  }finally{Pop-Location}
  $sentinel=& $gate -RepoRoot $repoRoot -Mode junit -AllowedPaths @() -ReportRelativePath $case.Report -ExpectedSuite $case.Suite -Phase GREEN -StartedAtUtc $started
  if($LASTEXITCODE -ne 0 -or $sentinel -cne 'P3_JUNIT_EVIDENCE_OK'){throw "IT gate 失败：$($case.Suite)"}
}
```

最终前端三份报告：

```powershell
$frontGroups=@(
  [pscustomobject]@{Report='git-metadata:p3-reports/p3-task7-script-flow.json';Files=@('src/services/ai-video/scripts/contract.test.ts','src/pages/digital-human-studio/hooks/useScriptFlow.test.tsx','src/pages/digital-human-studio/components/ScriptCandidateTabs.test.tsx','src/pages/digital-human-studio/components/ScriptVersionEditor.test.tsx','src/pages/digital-human-studio/components/ScriptOptimizationActions.test.tsx','src/pages/digital-human-studio/steps/ScriptStep.test.tsx')},
  [pscustomobject]@{Report='git-metadata:p3-reports/p3-task8-script-library.json';Files=@('src/pages/digital-human-studio/components/UserScriptLibrary.test.tsx','src/pages/digital-human-studio/components/ScriptDetailDrawer.test.tsx','src/pages/digital-human-studio/steps/ScriptStep.test.tsx')},
  [pscustomobject]@{Report='git-metadata:p3-reports/p3-task9-studio-integration.json';Files=@('src/pages/digital-human-studio/index.test.tsx')}
)
$frontUnique=@($frontGroups.Files | ForEach-Object{$_} | Sort-Object -Unique)
if($frontUnique.Count -ne 9){throw "Vitest unique 文件注册表漂移：$($frontUnique.Count)"}
foreach($group in $frontGroups){
  $report=Resolve-P3ArtifactPath $group.Report
  $reportDirectory=Split-Path -Parent $report
  if(-not (Test-Path -LiteralPath $reportDirectory -PathType Container)){[void](New-Item -ItemType Directory -Path $reportDirectory)}
  Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue
  $started=[DateTimeOffset]::UtcNow
  $testArgs=@('test','--')+@($group.Files)+@('--reporter=json',('--outputFile='+$report))
  Push-Location $webRoot
  try{
    & npm.cmd @testArgs
    if($LASTEXITCODE -ne 0){throw "Vitest 失败：$($group.Report)"}
  }finally{Pop-Location}
  $expected=@($group.Files | ForEach-Object{('ai-video-ui/ai-video-webapp/'+$_).Replace('\','/')})
  $sentinel=& $gate -RepoRoot $repoRoot -Mode vitest -AllowedPaths @() -ReportRelativePath $group.Report -ExpectedTestFiles $expected -Phase GREEN -StartedAtUtc $started
  if($LASTEXITCODE -ne 0 -or $sentinel -cne 'P3_VITEST_EVIDENCE_OK'){throw "Vitest gate 失败：$($group.Report)"}
}
Push-Location $webRoot
try{
  & npm.cmd run lint; if($LASTEXITCODE -ne 0){throw 'lint 失败'}
  & npm.cmd run build; if($LASTEXITCODE -ne 0){throw 'build 失败'}
}finally{Pop-Location}
```

### 6.2 F4 机械扫描

F4 acceptance owner 先用下面可复制命令运行真实标准脚本，并把完整输出原子冻结到 Git metadata；不能把手写 `P3_STANDARDS_OK` 当作脚本成功：

```powershell
$ErrorActionPreference='Stop'
$rootText=(& git rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$repoRoot=[IO.Path]::GetFullPath($rootText.Trim())
Set-Location -LiteralPath $repoRoot
$head=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
if($LASTEXITCODE -ne 0 -or $head -cnotmatch '^[0-9a-f]{40}$'){throw '无法解析 F4 HEAD'}
$started=[DateTimeOffset]::UtcNow
$standardsOutput=@(& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/validate-development-standards.ps1') 2>&1 | ForEach-Object{[string]$_})
$exitCode=$LASTEXITCODE
$ended=[DateTimeOffset]::UtcNow
if($exitCode -ne 0){$standardsOutput;throw '开发标准失败'}
$nonEmpty=@($standardsOutput | Where-Object{-not [string]::IsNullOrWhiteSpace($_)})
if($nonEmpty.Count -eq 0 -or $nonEmpty[-1] -cne 'DEVELOPMENT_STANDARDS_OK'){
  throw '标准脚本未返回真实 DEVELOPMENT_STANDARDS_OK'
}
$logReference="p3-evidence/$head/standards.log"
$raw=(& git rev-parse --git-path $logReference).Trim()
$logPath=if([IO.Path]::IsPathRooted($raw)){[IO.Path]::GetFullPath($raw)}else{[IO.Path]::GetFullPath((Join-Path $repoRoot $raw))}
$logLines=@(
  'command=powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/validate-development-standards.ps1'
  "startedAtUtc=$($started.ToString('o'))"
  "endedAtUtc=$($ended.ToString('o'))"
  "exitCode=$exitCode"
  "head=$head"
  'output:'
)+$standardsOutput+@('P3_STANDARDS_OK')
$logBytes=[Text.UTF8Encoding]::new($false).GetBytes(($logLines -join "`n")+"`n")
if(Test-Path -LiteralPath $logPath -PathType Leaf){
  $existing=[IO.File]::ReadAllText($logPath,[Text.Encoding]::UTF8)
  if($existing -cnotmatch "(?m)^head=$head$" -or $existing -cnotmatch '(?m)^DEVELOPMENT_STANDARDS_OK$' -or $existing -cnotmatch '(?m)^P3_STANDARDS_OK$'){
    throw '既有 standards.log 与当前 HEAD/真实 sentinel 不一致'
  }
}else{
  $directory=Split-Path -Parent $logPath
  if(-not (Test-Path -LiteralPath $directory -PathType Container)){[void](New-Item -ItemType Directory -Path $directory)}
  $stream=[IO.File]::Open($logPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try{$stream.Write($logBytes,0,$logBytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
}
$readback=[IO.File]::ReadAllText($logPath,[Text.Encoding]::UTF8)
if($readback -cnotmatch '(?m)^DEVELOPMENT_STANDARDS_OK$' -or $readback -cnotmatch '(?m)^P3_STANDARDS_OK$'){throw 'standards.log 回读失败'}
'P3_STANDARDS_LOG_OK'
```

下面脚本必须在 fresh tests 后运行；它只读源码和报告，不替代独立 review：

```powershell
$ErrorActionPreference='Stop'
$rootText=(& git rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$repoRoot=[IO.Path]::GetFullPath($rootText.Trim())
Set-Location -LiteralPath $repoRoot

$status=@(& git status --porcelain=v1 -uall)
if($status.Count -ne 0){$status;throw 'F4 scan 要求 clean worktree'}
$branch=(& git branch --show-current).Trim()
if($branch -cnotlike 'codex/*' -or $branch -ceq 'main'){throw 'F4 必须位于 linked codex/*'}
$gitDir=[IO.Path]::GetFullPath((& git rev-parse --git-dir).Trim())
$commonDir=[IO.Path]::GetFullPath((& git rev-parse --git-common-dir).Trim())
if($gitDir -ceq $commonDir){throw 'F4 不得在主工作树'}

$core='ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script'
$coreFiles=@(& rg --files $core)
if($LASTEXITCODE -ne 0 -or $coreFiles.Count -eq 0){throw 'P3 core 文件缺失'}
$forbiddenSegments=@('application','port','adapter','command','model','aggregate','repository','routing','validation')
foreach($file in $coreFiles){
  $segments=$file.Replace('\','/').Split('/')
  if(@($segments | Where-Object{$forbiddenSegments -ccontains $_}).Count -ne 0){throw "P3 core 非法分层：$file"}
}
$allowedSegments=@('domain','dto','mapper','service','impl')
foreach($file in $coreFiles){
  $relative=$file.Replace('\','/').Substring($core.Length)
  $first=@($relative.Trim('/').Split('/'))[0]
  if($allowedSegments -cnotcontains $first){throw "P3 core 顶层目录非法：$file"}
}

$mainRoots=@(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/script',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/script',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script'
)
$infraTestRoot='ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script'
if(-not (Test-Path -LiteralPath $infraTestRoot -PathType Container)){throw 'P3 infra test package root 缺失'}
$legacyInfraPackage=('infra'+'/script')
foreach($oldInfraRoot in @(
  ('ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/'+$legacyInfraPackage),
  ('ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/'+$legacyInfraPackage)
)){
  if(Test-Path -LiteralPath $oldInfraRoot){throw "P3 旧 infra Java package root 仍存在：$oldInfraRoot"}
}
$realIts=@(
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptSchemaMigrationIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptVersionPersistenceIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/script/ScriptFrozenInputPersistenceIT.java',
  'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/script/ScriptGenerationBillingIT.java',
  'ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/ScriptUserApiIT.java'
)
$fakeHits=@(& rg -n --glob '*.java' '\b(Fake|Stub|Mock)[A-Za-z0-9_]*\b' @($mainRoots+$realIts))
if($LASTEXITCODE -eq 0 -and $fakeHits.Count -ne 0){$fakeHits;throw 'production/真实 IT 仍有 fake'}
if($LASTEXITCODE -notin @(0,1)){throw 'fake scan 执行失败'}

$upstreamForbidden=@(
  ('questionnaire'+'.mapper'),('questionnaire'+'.domain'),('questionnaire'+'_'),
  ('evidence'+'.mapper'),('evidence'+'.domain'),('evidence'+'_'),
  ('knowledge'+'.mapper'),('knowledge'+'.domain'),('knowledge'+'_')
)
foreach($pattern in $upstreamForbidden){
  $hits=@(& rg -n --fixed-strings $pattern @($mainRoots+$realIts))
  if($LASTEXITCODE -eq 0 -and $hits.Count -ne 0){$hits;throw "上游越界依赖：$pattern"}
  if($LASTEXITCODE -notin @(0,1)){throw "upstream scan 失败：$pattern"}
}

$coreJava=Join-Path $repoRoot $core
$serviceCount=@(Get-ChildItem -LiteralPath (Join-Path $coreJava 'service') -Filter 'I*Service.java' -File).Count
$dtoCount=@(Get-ChildItem -LiteralPath (Join-Path $coreJava 'dto') -Filter '*DTO.java' -File).Count
if($serviceCount -ne 3 -or $dtoCount -ne 8){throw "P3 registry 漂移：services=$serviceCount dtos=$dtoCount"}
$entityFiles=@('UserScript.java','ScriptVersion.java','ScriptConfirmation.java','ScriptTaskInput.java')
foreach($name in $entityFiles){
  $path=Join-Path (Join-Path $coreJava 'domain') $name
  if(-not (Test-Path -LiteralPath $path)){throw "Entity 缺失：$name"}
  $text=Get-Content -LiteralPath $path -Raw -Encoding UTF8
  if($text -match 'extends\s+BaseEntity'){throw "创作端 Entity 不得继承 BaseEntity：$name"}
  foreach($field in @('tenantId','ownerType','ownerId','createdByUserId')){
    if($text -cnotmatch ('\b'+$field+'\b')){throw "$name 缺显式 actor/owner 字段 $field"}
  }
}

$userRoot='ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/script'
$badUser=@(& rg -n 'LoginHelper|@Log\b' $userRoot)
if($LASTEXITCODE -eq 0 -and $badUser.Count -ne 0){$badUser;throw '用户接口使用默认 actor/@Log'}
if($LASTEXITCODE -notin @(0,1)){throw 'user boundary scan 失败'}
$mock='ai-video-ui/ai-video-webapp/mock/aivideo-scripts.ts'
if(-not (Test-Path -LiteralPath $mock)){throw 'P3 独立 Mock 缺失'}
$mockReuse=@(& rg -n 'aivideo-studio' $mock)
if($LASTEXITCODE -eq 0 -and $mockReuse.Count -ne 0){throw 'P3 Mock 复用了 P2 Mock'}
if($LASTEXITCODE -notin @(0,1)){throw 'mock scan 失败'}

$scriptServices='ai-video-ui/ai-video-webapp/src/services/ai-video/scripts'
$serviceOwned=@('types.ts','api.ts','queryKeys.ts','adapter.ts','contract.test.ts')
foreach($name in $serviceOwned){
  if(-not (Test-Path -LiteralPath (Join-Path $scriptServices $name) -PathType Leaf)){throw "P3 services ownership 文件缺失：$name"}
}
foreach($legacy in @(
  'ai-video-ui/ai-video-webapp/src/services/aivideo',
  'ai-video-ui/ai-video-webapp/src/types/aivideo-script.ts',
  'ai-video-ui/ai-video-webapp/src/adapters/aivideo-script.ts'
)){
  if(Test-Path -LiteralPath $legacy){throw "P3 legacy frontend ownership 路径仍存在：$legacy"}
}
$ownedComponents=@(
  'ScriptCandidateTabs.tsx','ScriptCandidateTabs.test.tsx',
  'ScriptVersionEditor.tsx','ScriptVersionEditor.test.tsx',
  'ScriptOptimizationActions.tsx','ScriptOptimizationActions.test.tsx',
  'UserScriptLibrary.tsx','UserScriptLibrary.test.tsx',
  'ScriptDetailDrawer.tsx','ScriptDetailDrawer.test.tsx',
  'LibraryView.tsx'
)
$componentRoot='ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components'
foreach($name in $ownedComponents){
  if(-not (Test-Path -LiteralPath (Join-Path $componentRoot $name) -PathType Leaf)){throw "P3 master component 缺失：$name"}
}
foreach($legacyName in @('ScriptCandidateCards.tsx','ScriptCandidateCards.test.tsx','ScriptEditor.tsx','ScriptEditor.test.tsx','ScriptConfirmationPanel.tsx','ScriptConfirmationPanel.test.tsx')){
  if(Test-Path -LiteralPath (Join-Path $componentRoot $legacyName)){throw "P3 未登记同义组件仍存在：$legacyName"}
}
$stepRoot='ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps'
$ownedSteps=@('ScriptStep.tsx','ScriptStep.test.tsx','VoiceStep.tsx','BaseStep.tsx')
foreach($name in $ownedSteps){
  if(-not (Test-Path -LiteralPath (Join-Path $stepRoot $name) -PathType Leaf)){throw "P3 master step 缺失：$name"}
}
$urlFiles=@(& rg -l --fixed-strings '/api/studio/script' 'ai-video-ui/ai-video-webapp/src')
if($LASTEXITCODE -eq 0){
  $unexpected=@($urlFiles | Where-Object{$_.Replace('\','/') -cne "$scriptServices/api.ts"})
  if($unexpected.Count -ne 0){$unexpected;throw 'P3 URL 出现在 api.ts 之外'}
} elseif($LASTEXITCODE -ne 1){throw 'P3 URL ownership scan 失败'}

$itText=Get-Content -LiteralPath $realIts -Raw -Encoding UTF8
if(@($itText | Where-Object{$_ -cnotmatch '@Tag\("dev"\)'}).Count -ne 0){throw '真实 IT 缺 @Tag(dev)'}
foreach($file in $realIts){
  $text=Get-Content -LiteralPath $file -Raw -Encoding UTF8
  if($text -cnotmatch 'LocalIntegrationEnvironment\.requireFromEnvironment\(\)'){throw "IT 缺安全环境检查：$file"}
}
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/validate-development-standards.ps1')
if($LASTEXITCODE -ne 0){throw '开发标准失败'}
& git diff --check
if($LASTEXITCODE -ne 0){throw 'git diff --check 失败'}
'P3_SCAN_OK'
```

### 6.3 F4 handoff 严格 schema

`git rev-parse --git-path p3-f4-handoff.json` 的字段及顺序固定为：

```text
fullF4Ready
f1Head
f1AmendmentHead
f1AddendumSha256
f2Head
f3Head
f4Head
owner
reviewer
reviewStatus
reviewCompletedAtUtc
p3AcceptanceWindowStart
p3AcceptanceWindowEnd
p1HandoffSha256
p2HandoffSha256
migrationChain
migrationRepeat07
stableServices
stableDtos
testRegistry
upstreamConsumerContract
uiStateMatrix
evidence
capturedAtUtc
```

类型和值固定：

- `fullF4Ready`、`migrationRepeat07` 必须是 JSON boolean `true`；`reviewStatus` 精确 `PASS`；owner/reviewer trim 后大小写不敏感比较必须不同。
- `f1Head/f1AmendmentHead/f2Head/f3Head/f4Head` 是小写 40 位 SHA；`f1Head` 是 original P0-C F1 handoff head，`f1AmendmentHead` 精确等于已验证 addendum amendmentHead，`f1AddendumSha256` 为 live addendum SHA。真实 ancestry 要求 `f1Head -> f2Head -> f3Head -> f4Head`；由于 F2 可能把 amendment 补丁重放成新 commit，不强行要求旧 `f1AmendmentHead` 在最终 ancestry，但必须用 F1/F2/F3 binding record、actual signatures 和 `04a` DDL 证明 amendment 语义仍在。`f4Head` 精确等于独立 reviewed HEAD。
- `migrationChain` 精确 `['01','02','03','04','04a','05','06','07']`；migration report 同时证明 F1 schemaAddendum/forward migration、clean apply 和 repeat `07`。
- `stableServices` 精确 `['IScriptGenerationService','IScriptVersionService','IUserScriptQueryService']`。
- `stableDtos` 精确为 Section 2.1 八项且顺序一致。
- `testRegistry` 精确 `{surefireSuites:14,failsafeSuites:5,localIntegrationProfiles:5,vitestFiles:9,vitestReports:3}`，全部为 JSON integer；`vitestFiles` 按三份 report 的 repo-relative test path 并集去重，Task 7/8 重复执行的 `ScriptStep.test.tsx` 只计一次。
- `upstreamConsumerContract` 精确记录 P1 五 DTO、P1 两 Service、P2 两 Service/六 DTO、`sameReadSnapshot=true`、`removeFakesFrom=['production','all-real-it']`、`forbiddenDependencies=['upstream-mapper','upstream-table','upstream-entity','user-vo']`。
- `uiStateMatrix` 精确列出 `auth-recovery/loading/initial-empty/search-empty/pagination/network-timeout-5xx-retry/401-once/403/submit-guard/cancel-neutral/query-key-refresh/revision-conflict/context-stale/queued/running/success/failed/cancelled/expired/generate/repair/no-candidates/quota-low/rate-changed/slot-conflict/save/confirm/copy-success/copy-failure/version-tree/delete-confirm/reference-block`，不得用“等”省略。
- `evidence` 只允许 `unit/it/migration/vitest/standards/scan`；每项只含 `path/sha256`，路径精确 `p3-evidence/<f4Head>/<kind>.manifest.json`，SHA 实时重算。

创建规则：以除 `capturedAtUtc` 外的 exact core payload 计算 SHA-256。文件不存在时使用 `FileMode.CreateNew` 原子写入 UTF-8 无 BOM；存在时 strict field/type/order 回读，同 payload 返回原 hash 和原时间，任一字段、HEAD、artifact hash 不同立即失败。writer 不得创建或修改 `p3-independent-review.json`；没有同 HEAD 的独立 PASS 不能创建 handoff。

---

## 7. 完成交接清单

- [ ] 九张任务卡逐卡有 RED、GREEN、fresh report、独立 reviewer 复跑和四段固定输出。
- [ ] F1/F2/F3 都是真实 rebase；F1 绑定 addendum/amendment/live evidence，F2 绑定 P1 component/source registry，F3 绑定 P2 component/source/signature/lock/write/context-semantics digests；均有 before/after/merge-base/handoff SHA 不可变记录。F2 没调用 P1 Service，F3 production/真实 IT 没有上游 fake。
- [ ] P3 只发布三 Service/八 DTO；core 只含 `domain/dto/mapper/service/service.impl`。
- [ ] 四表字段、唯一键/FK/索引、tenant/owner/created_by_user_id、不可变/逻辑删除/修订语义与 `07` replay 均有真实 IT。
- [ ] 所有创作端 Entity 不继承默认审计基类；写边界只用 `AppActorContext`，无默认 `LoginHelper/@Log`；五类审计均使用脱敏 `AppSecurityAuditDTO`。
- [ ] P0-C attempt 总数最多 3、lease margin/renew、正常 settle、失败 release、stale 无业务写/无 settle/无 retry 均有全链证据。
- [ ] 生成与优化先调用 P2 exact MANDATORY 写事务锁入口，按完整全局锁序持锁重检 branch/questionnaire/knowledge hashes；fact/revision 集合按 P2 F3 final semantics 严格相等，只消费允许的 P1/P2 I/DTO。
- [ ] 七接口、独立 `mock/aivideo-scripts.ts`、`src/services/ai-video/scripts/**` contract test、三份报告的 9 个 unique 前端 test files 和完整 UI state matrix 通过。
- [ ] 五个 IT 都带 `'-Pdev,local-integration-test'`，安全本机 MySQL 8/Redis 7 fail-fast；所有 XML/JSON fresh、tests>0、failures/errors/skipped/pending=0。
- [ ] `scripts/validate-development-standards.ps1`、F4 scan、PowerShell AST、`git diff --check` 全部为 0；独立 reviewer PASS 后 F4 handoff CreateNew/幂等回读。
- [ ] 不 push；把 F4 HEAD、handoff SHA、六 manifest SHA、风险与阻塞项交给下一阶段 owner。

---

## 8. 逐文件实现注册表

本节防止执行者把任务卡中的目录概括理解成自行设计分层。未列出的 P3 生产文件需要先由契约 owner 补充本表，再实现。

### 8.1 `ai-video-core` production

| 仓库相对路径 | 唯一职责 | 必须依赖 | 明确禁止 |
|---|---|---|---|
| `.../script/domain/UserScript.java` | 文案主体贫血 Entity | MyBatis-Plus annotations | BaseEntity、业务方法、sys_user 字段 |
| `.../script/domain/ScriptVersion.java` | 不可变版本贫血 Entity | MyBatis-Plus annotations | update helper、正文校验逻辑 |
| `.../script/domain/ScriptConfirmation.java` | 不可变确认历史 Entity | MyBatis-Plus annotations | current 状态覆盖、业务方法 |
| `.../script/domain/ScriptTaskInput.java` | 不可变收费任务输入 Entity | MyBatis-Plus annotations、type handler | 解密/组 prompt、update/delete |
| `.../script/mapper/UserScriptMapper.java` | 主体 owner-scoped CRUD/条件更新 | `BaseMapperPlus` | 无 owner 查询、业务编排 |
| `.../script/mapper/UserScriptMapper.xml` | page/detail/条件 revision SQL、五类组合过滤、下游引用聚合 | sort whitelist 后的枚举列 | `${sortField}` 原样拼接 |
| `.../script/mapper/ScriptVersionMapper.java` | immutable insert/tree select | `BaseMapperPlus` | update/delete API |
| `.../script/mapper/ScriptVersionMapper.xml` | 按 script/owner 返回完整 flat versions | tenant/owner/creator predicates | 服务端 children/tree、跨 owner parent |
| `.../script/mapper/ScriptConfirmationMapper.java` | history insert/select | `BaseMapperPlus` | update/delete API |
| `.../script/mapper/ScriptConfirmationMapper.xml` | idempotency/history query | tenant/script/key | 只按 key 查询 |
| `.../script/mapper/ScriptTaskInputMapper.java` | root-scoped insert/read | `BaseMapperPlus` | update/delete API |
| `.../script/mapper/ScriptTaskInputMapper.xml` | same-root exact payload read | tenant/root/owner predicates | current alias join、上游表 join |
| `.../script/dto/ScriptGenerationRequestDTO.java` | 生成稳定入参 | Jakarta validation 可在边界复用 | HTTP annotation、provider 字段 |
| `.../script/dto/ScriptGenerationResultDTO.java` | 生成任务稳定结果 | P0-C task IDs | HTTP string 化逻辑 |
| `.../script/dto/ScriptOptimizationRequestDTO.java` | 优化稳定入参 | immutable source id | source 正文/标题副本 |
| `.../script/dto/ScriptOptimizationResultDTO.java` | 优化任务稳定结果 | P0-C task IDs | HTTP string 化逻辑 |
| `.../script/dto/ScriptFrozenInputDTO.java` | 冻结输入稳定读模型 | canonical JSON strings | provider response、用户 VO |
| `.../script/dto/ScriptVersionDTO.java` | 跨 user 模块版本读模型 | immutable version fields | raw Entity、可变 List |
| `.../script/dto/ScriptConfirmationDTO.java` | 确认输入/输出稳定模型 | strict input/output null rules | 请求提供 selected title 文本 |
| `.../script/dto/UserScriptSummaryDTO.java` | 文案库稳定行/详情聚合模型 | industry/purpose/source/confirmation、三类引用 count/time | page envelope、HTTP annotation |
| `.../script/service/IScriptGenerationService.java` | 生成/优化两个收费入口 | 四个 request/result DTO | Mapper、provider raw types |
| `.../script/service/IScriptVersionService.java` | 人工版本与确认入口 | version/confirmation DTO | HTTP BO/VO、provider |
| `.../script/service/IUserScriptQueryService.java` | 五过滤 page、summary、flat versions、current confirmation、delete入口 | `PageQuery/PageResult` | HTTP response envelope、嵌套 tree |
| `.../script/service/impl/EffectiveCharacterCounter.java` | code point/token 纯计数 | JDK Unicode APIs | DB、Spring bean 必需性 |
| `.../script/service/impl/ScriptGenerationResultValidator.java` | 生成结果纯校验 | frozen DTO、shared error | lenient 修补、日志正文 |
| `.../script/service/impl/ScriptRecommendationReasonFormatter.java` | F2 rebase P1 后由 route rank + KnowledgePlanDTO 确定性派生推荐理由 | P1 reviewed `KnowledgePlanDTO`、`script-recommendation-1` 固定模板 | F1 创建/编译、provider 字段、prompt/当前 label |
| `.../script/service/impl/ScriptOptimizationResultValidator.java` | 优化结果纯校验 | source version/frozen DTO | reroute、修改 source |
| `.../script/service/impl/ScriptFrozenInputValidator.java` | 冻结行/hash/canonical JSON 校验 | SHA-256、strict JSON | 读取 current context |
| `.../script/service/impl/ScriptFrozenInputAssembler.java` | DTO/scalar→canonical refs | P1/P2 允许 DTO | 调用 Service、Mapper、provider |
| `.../script/service/impl/ScriptFrozenInputServiceImpl.java` | idempotent persist/read | task input Mapper、cipher | update/delete frozen row |
| `.../script/service/impl/ScriptGenerationServiceImpl.java` | F3 编排 route/context/task/freeze/enqueue | 允许的 I/DTO | provider、billing direct、上游 Mapper |
| `.../script/service/impl/ScriptVersionServiceImpl.java` | manual/confirm 事务编排 | P3 Mappers、actor/authorizer/同事务 audit | Controller concern、provider |
| `.../script/service/impl/UserScriptQueryServiceImpl.java` | visible filtered page/flat detail/downstream aggregate/delete | owner-scoped Mappers | 裸全表 query、HTTP concern、服务端组 tree |
| `.../script/service/impl/ScriptGeneratedVersionWriter.java` | handler 结果事务内原子写版本 | validators、P3 Mappers、task service | provider call、billing direct |

上表中的 `...` 精确展开为 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo`。`service.impl` helper 不能变成额外 public stable Service；必须 public 的 Spring bean 也只能暴露已冻结接口。

### 8.2 `ai-video-infra` production

| 仓库相对路径 | 唯一职责 | 输入/输出 | 明确禁止 |
|---|---|---|---|
| `.../script/provider/ScriptProviderResponseParser.java` | strict JSON token/duplicate-key/parser | raw string →八 DTO 可用的包私有节点 | core provider DTO、宽松 coercion |
| `.../script/provider/ScriptPromptFactory.java` | 从 validated frozen refs + runtime bodies 组 prompt | frozen DTO、snapshot、runtime facts/source | 日志输出 prompt、持久化正文副本 |
| `.../script/provider/ScriptGenerationTaskHandler.java` | SCRIPT_GENERATE lease/attempt/provider/write | lease → task terminal | Controller 调用、billing direct |
| `.../script/provider/ScriptOptimizationTaskHandler.java` | SCRIPT_OPTIMIZE lease/attempt/provider/write | lease → task terminal | reroute、修改 source version |

上表 `...` 精确展开为 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo`。Handler registry 沿用 P0-C 既有注册机制；若 generator 使用 configuration 文件，只能在 P0-C 允许的现有配置点增加两个 bean，不能创建第二套 scanner。

### 8.3 `ai-video-user` production

| 路径 | 类型 | 只允许字段/责任 |
|---|---|---|
| `.../user/script/controller/UserScriptController.java` | Controller | 七路由、validation、permission、typed actor、Service、MapstructUtils；审计由业务事务 Service 完成 |
| `.../user/script/domain/bo/ScriptGenerationBo.java` | BO | draft/branch/revisions/generationInputHash/questionnaireHash/knowledgeContextHash/mode/idempotency/tariff |
| `.../user/script/domain/bo/ScriptManualVersionBo.java` | BO | sourceVersionId/text/3 titles/index/idempotency/expected context |
| `.../user/script/domain/bo/ScriptOptimizationBo.java` | BO | sourceVersionId/type/custom/idempotency/tariff/branch/context/input/questionnaire/knowledge hashes |
| `.../user/script/domain/bo/ScriptConfirmationBo.java` | BO | versionId/titleIndex/idempotency/expected context；无 title 文本 |
| `.../user/script/domain/bo/UserScriptQueryBo.java` | BO | keyword/draftId/industryCode/purposeCode/sourceType/confirmationStatus/updatedTimeStart/updatedTimeEnd/pageNum/pageSize/sortField/sortOrder |
| `.../user/script/domain/vo/ScriptTaskVo.java` | VO | rootTaskId/executionTaskId/usageOperationId 十进制 string、reused |
| `.../user/script/domain/vo/ScriptVersionVo.java` | VO | stable version 字段；所有 ID string；titles defensive copy |
| `.../user/script/domain/vo/ScriptConfirmationVo.java` | VO | confirmation/version IDs、index/snapshot、confirmed revision/time |
| `.../user/script/domain/vo/UserScriptSummaryVo.java` | VO | page row string IDs、industry/purpose/source/confirmation、count/time |
| `.../user/script/domain/vo/UserScriptDetailVo.java` | VO | summary、flat immutable versions、currentConfirmation、downstreamReferences；无 children、无当前知识替换 |

上表 `...` 精确展开为 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo`。禁止在 `ai-video-user-api` 重复任何 BO/VO/Controller；P3 task writer 不得直接在该 owner-registry 外模块添加 IT。完整启动/auth/HTTP 场景由共享 owner 在 Task 6 显式串行窗口按 exact change request 创建 `ScriptUserApiIT`，其他 user-api 文件不在该窗口内。

### 8.4 Frontend production 与 Mock

| 路径 | 唯一职责 | 禁止 |
|---|---|---|
| `src/services/ai-video/scripts/types.ts` | HTTP domain types、enum/filter/detail literal unions | URL、React state、number ID |
| `src/services/ai-video/scripts/api.ts` | 七 endpoint 调用 | 组件 import、状态字符串散落 |
| `src/services/ai-video/scripts/queryKeys.ts` | scoped canonical query keys | URL、HTTP transport |
| `src/services/ai-video/scripts/adapter.ts` | envelope/page/ID/enum/filter/detail runtime validation | 容错伪造缺失字段 |
| `src/services/ai-video/scripts/contract.test.ts` | types/API/queryKeys/adapter/ownership contract | component rendering |
| `mock/aivideo-scripts.ts` | P3 独立 fixtures/state transitions | import P2 Mock、共用 mutable store |
| `hooks/useScriptFlow.ts` | intent/idempotency/mutations/polling/cache | JSX、自动无限 retry |
| `components/ScriptCandidateTabs.tsx` | A/B/C、标题选择、指标、复制和确认入口 | API 调用、数组位置当 ID |
| `components/ScriptVersionEditor.tsx` | manual child editor、确认 review/冲突恢复 | 原地修改 version、autosave 新 key、发送 selected title 文本 |
| `components/ScriptOptimizationActions.tsx` | 六种 exact 优化类型、自定义指令和费用确认 | 旧优化别名、绕过 quota/rate reconfirm |
| `steps/ScriptStep.tsx` | 工作流组合/状态路由 | 裸 URL、复制 server state |
| `components/UserScriptLibrary.tsx` | server page/sort/search | 客户端全量分页 |
| `components/ScriptDetailDrawer.tsx` | immutable version tree/detail | 当前知识覆盖历史 snapshot |
| `components/LibraryView.tsx` | library tab/container | 重复 query state |
| `steps/VoiceStep.tsx` | 使用 confirmed reference | script text/title 副本 |
| `steps/BaseStep.tsx` | 使用 confirmed reference | 未确认/失权版本继续 |
| `model.ts`（shared owner only） | 最小跨步骤 reference；仅 Task 9 显式串行窗口可改 | P3 task writer 直写、server version tree、正文、标题数组 |

前端根精确为 `ai-video-ui/ai-video-webapp`。所有组件必须从 `src/services/ai-video/scripts/` 的公开入口或 hook 获取数据；Mock 和真实接口必须走同一 `adapter.ts`。禁止重新创建 `src/services/aivideo/**`、顶层 `src/types/aivideo-script.ts` 或 `src/adapters/aivideo-script.ts`。

### 8.5 测试文件归属

| 模块 | 测试文件 | 证据插件 |
|---|---|---|
| core | `ScriptContractRegistryTest` | Surefire |
| core | `EffectiveCharacterCounterTest` | Surefire |
| core | `ScriptGenerationResultValidatorTest` | Surefire |
| core | `ScriptOptimizationResultValidatorTest` | Surefire |
| core | `ScriptFrozenInputValidatorTest` | Surefire |
| core | `ScriptVersionServiceImplTest` | Surefire |
| core | `UserScriptQueryServiceImplTest` | Surefire |
| core | `ScriptFrozenInputAssemblerTest` | Surefire |
| core | `ScriptGenerationServiceImplTest` | Surefire |
| core | `ScriptGeneratedVersionWriterTest` | Surefire |
| infra | `ScriptProviderResponseParserTest` | Surefire |
| infra | `ScriptGenerationTaskHandlerTest` | Surefire |
| infra | `ScriptOptimizationTaskHandlerTest` | Surefire |
| user | `UserScriptControllerTest` | Surefire |
| core | `ScriptSchemaMigrationIT` | Failsafe/local profile |
| core | `ScriptVersionPersistenceIT` | Failsafe/local profile |
| core | `ScriptFrozenInputPersistenceIT` | Failsafe/local profile |
| infra | `ScriptGenerationBillingIT` | Failsafe/local profile |
| user-api（shared owner window） | `ScriptUserApiIT` | Failsafe/local profile |
| web | Task 7 六文件 | Vitest JSON |
| web | Task 8 三文件（含复跑 `ScriptStep.test.tsx`） | Vitest JSON |
| web | Task 9 index | Vitest JSON |

---

## 9. `07` SQL 逐字段与约束矩阵

### 9.1 `av_user_script`

| 字段 | MySQL 类型/空值 | 来源与语义 | 约束/测试 |
|---|---|---|---|
| `id` | `BIGINT NOT NULL` | 雪花 ID | PK；HTTP string |
| `tenant_id` | `BIGINT NOT NULL` | `AppActorContext` tenant | 所有查询首条件 |
| `owner_type` | `VARCHAR(16) NOT NULL` | personal/workspace | CHECK exact enum |
| `owner_id` | `BIGINT NOT NULL` | app user 或 workspace | 与授权结果一致 |
| `created_by_user_id` | `BIGINT NOT NULL` | 当前 app_user | 禁止 sys user/default 0 |
| `draft_id` | `BIGINT NOT NULL` | P2 当前草稿 | tenant 内未删除唯一 |
| `industry_code` | `VARCHAR(64) NOT NULL` | 冻结输入摘要 | 创建后只随明确业务更新 |
| `purpose_code` | `VARCHAR(64) NOT NULL` | 冻结输入摘要 | 非空稳定 code |
| `display_title` | `VARCHAR(100) NULL` | 最近确认标题 snapshot | 只由 confirm transaction 更新 |
| `current_confirmed_version_id` | `BIGINT NULL` | 当前确认 immutable version | 逻辑 FK，同 script/owner |
| `script_revision` | `BIGINT NOT NULL DEFAULT 0` | 主体条件更新 revision | 每次确认/删除语义递增 |
| `created_at` | `DATETIME(3) NOT NULL` | DB/app clock UTC | insert only |
| `updated_at` | `DATETIME(3) NOT NULL` | current pointer/title/revision | 条件更新同步 |
| `deleted` | `CHAR(1) NOT NULL DEFAULT '0'` | RuoYi 逻辑删除 | only `0/1`；其他三表无 deleted |

约束和索引精确命名：

- `PRIMARY KEY pk_av_user_script (id)`。
- `UNIQUE KEY uk_av_user_script_tenant_draft_del (tenant_id,draft_id,deleted)`。
- `KEY idx_av_user_script_owner_page (tenant_id,owner_type,owner_id,updated_at,id)`。
- `KEY idx_av_user_script_creator_page (tenant_id,created_by_user_id,updated_at,id)`。
- `KEY idx_av_user_script_confirmed_version (tenant_id,current_confirmed_version_id)`。
- `CHECK ck_av_user_script_owner_type (owner_type IN ('personal','workspace'))`。
- `CHECK ck_av_user_script_deleted (deleted IN ('0','1'))`。
- `current_confirmed_version_id` 采用逻辑外键：version 在后建表且逻辑删除顺序需避免循环物理 FK；Service/IT 必须证明它存在、同 script、同 owner。不得假称有数据库 FK。

### 9.2 `av_script_version`

| 字段 | MySQL 类型/空值 | 来源与语义 | 约束/测试 |
|---|---|---|---|
| `id` | `BIGINT NOT NULL` | 雪花 ID | PK |
| `tenant_id` | `BIGINT NOT NULL` | actor scope | 与 script 一致 |
| `owner_type` | `VARCHAR(16) NOT NULL` | actor scope | personal/workspace |
| `owner_id` | `BIGINT NOT NULL` | actor scope | 与 script 一致 |
| `created_by_user_id` | `BIGINT NOT NULL` | app_user | manual/AI result creator |
| `script_id` | `BIGINT NOT NULL` | parent aggregate | 物理 FK user script |
| `parent_version_id` | `BIGINT NULL` | manual/optimized parent | self physical FK；generated root null |
| `candidate_code` | `CHAR(1) NULL` | generated A/B/C | generated 必填，其余 null |
| `source_type` | `VARCHAR(24) NOT NULL` | generated/manual_edit/ai_optimized | CHECK enum |
| `plan_code` | `VARCHAR(64) NULL` | P1 exact plan | generated 必填；child 继承 |
| `angle_code` | `VARCHAR(64) NULL` | P1 exact route | generated 必填；child 继承 |
| `primary_template_version_id` | `BIGINT NULL` | P1 plan | generated 必填；child 继承 |
| `differentiator_technique_code` | `VARCHAR(64) NULL` | P1 plan | generated 必填；child 继承 |
| `angle_summary` | `VARCHAR(500) NOT NULL` | server `script-recommendation-1` deterministic formatter | provider 无此字段；manual/optimized 继承 route 并由同 formatter 重建 |
| `publish_titles_json` | `JSON NOT NULL` | 正好三个标题 | JSON_LENGTH=3；Service distinct/length |
| `selected_title_index` | `TINYINT NOT NULL` | 0/1/2 | CHECK 0..2 |
| `script_text` | `LONGTEXT NOT NULL` | immutable 正文 | 非空，<=20k code points Service 校验 |
| `effective_character_count` | `INT NOT NULL` | pure counter | >=1 |
| `estimated_duration_seconds` | `INT NOT NULL` | ceil formula | >0；与 Service 重算相等 |
| `target_duration_seconds` | `INT NOT NULL` | frozen target | >0 |
| `effective_chars_per_minute` | `INT NOT NULL` | frozen config | >0 |
| `duration_tolerance_basis_points` | `INT NOT NULL` | frozen config | 0..10000 |
| `rule_config_versions_json` | `JSON NOT NULL` | frozen rule versions | canonical object |
| `validator_version` | `VARCHAR(64) NOT NULL` | 校验器稳定版本 | 不为空 |
| `knowledge_snapshot_id` | `BIGINT NOT NULL` | P1 immutable snapshot | 逻辑 FK；不得 current alias |
| `source_task_id` | `BIGINT NULL` | generated/optimized root task | AI source 必填，manual 继承 |
| `manual_idempotency_key` | `VARCHAR(128) NULL` | manual intent | manual 必填，AI null |
| `branch_revision` | `BIGINT NOT NULL` | frozen branch | >0 |
| `generation_context_revision` | `BIGINT NOT NULL` | frozen context | >0 |
| `generation_input_hash` | `CHAR(64) NOT NULL` | lowercase SHA-256 | regex/service exact |
| `questionnaire_hash` | `CHAR(64) NOT NULL` | P2 locked context | lowercase SHA-256；与 task input/source 一致 |
| `knowledge_context_hash` | `CHAR(64) NOT NULL` | P2 locked context | lowercase SHA-256；与 task input/source 一致 |
| `created_at` | `DATETIME(3) NOT NULL` | immutable insert time | 无 updated/deleted |

约束和索引精确命名：

- `PRIMARY KEY pk_av_script_version (id)`。
- `CONSTRAINT fk_av_script_version_script FOREIGN KEY (script_id) REFERENCES av_user_script(id)`。
- `CONSTRAINT fk_av_script_version_parent FOREIGN KEY (parent_version_id) REFERENCES av_script_version(id)`；Service 另验同 script/tenant/owner，阻止环。
- `UNIQUE KEY uk_av_script_version_task_candidate (tenant_id,source_task_id,candidate_code)`；MySQL null 语义允许 manual。
- `UNIQUE KEY uk_av_script_version_manual_intent (tenant_id,script_id,manual_idempotency_key)`。
- `KEY idx_av_script_version_tree (tenant_id,script_id,parent_version_id,created_at,id)`。
- `KEY idx_av_script_version_snapshot (tenant_id,knowledge_snapshot_id)`。
- `KEY idx_av_script_version_source_task (tenant_id,source_task_id)`。
- `CHECK ck_av_script_version_source_type`、`ck_av_script_version_candidate`、`ck_av_script_version_title_index`、`ck_av_script_version_duration`、`ck_av_script_version_tolerance`、`ck_av_script_version_generation_input_hash`、`ck_av_script_version_questionnaire_hash`、`ck_av_script_version_knowledge_context_hash`。
- DB 不允许业务 update/delete：Mapper 不暴露对应方法，IT 用 SQL 变更尝试必须被权限/guard 测试拒绝；若当前迁移规范不允许 trigger，不得擅自引入 trigger，而以 Mapper boundary + DB account privilege 证据实现。

### 9.3 `av_script_confirmation`

| 字段 | MySQL 类型/空值 | 来源与语义 | 约束/测试 |
|---|---|---|---|
| `id` | `BIGINT NOT NULL` | 雪花 ID | PK |
| `tenant_id` | `BIGINT NOT NULL` | actor scope | owner query 条件 |
| `owner_type` | `VARCHAR(16) NOT NULL` | actor scope | exact enum |
| `owner_id` | `BIGINT NOT NULL` | actor scope | 与 script/version 一致 |
| `created_by_user_id` | `BIGINT NOT NULL` | confirming app_user | 非 sys user |
| `script_id` | `BIGINT NOT NULL` | confirmed script | physical FK |
| `version_id` | `BIGINT NOT NULL` | immutable version | physical FK |
| `draft_id` | `BIGINT NOT NULL` | current draft | 与 script 一致 |
| `branch_id` | `BIGINT NOT NULL` | current branch | 请求与 current 一致 |
| `branch_revision` | `BIGINT NOT NULL` | expected/current/frozen | 三方相等 |
| `generation_context_revision` | `BIGINT NOT NULL` | expected/current/frozen | 三方相等 |
| `generation_input_hash` | `CHAR(64) NOT NULL` | expected/current/frozen | 三方相等 |
| `selected_title_index` | `TINYINT NOT NULL` | request index | 0..2 |
| `selected_title_snapshot` | `VARCHAR(100) NOT NULL` | version titles[index] | 绝不读 request title text |
| `idempotency_key` | `VARCHAR(128) NOT NULL` | confirm intent | same payload reuse |
| `confirmed_draft_revision` | `BIGINT NOT NULL` | 条件更新后的 draft revision | previous+1 |
| `created_at` | `DATETIME(3) NOT NULL` | confirmation history time | immutable |

约束/索引：PK；FK `script_id`、`version_id`；唯一 `(tenant_id,script_id,idempotency_key)`；索引 `(tenant_id,draft_id,created_at,id)`、`(tenant_id,version_id)`；CHECK owner type/title index/hash。FK 不能证明 version 属于 script，Service/IT 必须额外证明。

### 9.4 `av_script_task_input`

| 字段 | MySQL 类型/空值 | 来源与语义 | 约束/测试 |
|---|---|---|---|
| `id` | `BIGINT NOT NULL` | 雪花 ID | PK |
| `tenant_id` | `BIGINT NOT NULL` | actor scope | root read 条件 |
| `owner_type` | `VARCHAR(16) NOT NULL` | actor scope | exact enum |
| `owner_id` | `BIGINT NOT NULL` | actor scope | workspace/personal |
| `created_by_user_id` | `BIGINT NOT NULL` | requesting app_user | 非 sys user |
| `root_task_id` | `BIGINT NOT NULL` | P0-C root | tenant 内唯一 |
| `draft_id` | `BIGINT NOT NULL` | request/current | resource id |
| `branch_id` | `BIGINT NOT NULL` | current branch | 非 current 46117 |
| `operation_type` | `VARCHAR(32) NOT NULL` | generate/regenerate/optimize | CHECK exact set |
| `generation_mode` | `VARCHAR(32) NULL` | generation strategy | optimize 可 null |
| `source_version_id` | `BIGINT NULL` | optimize/manual source | optimize 必填，逻辑/物理 FK按迁移序 |
| `optimization_type` | `VARCHAR(64) NULL` | `shorter/more_colloquial/strengthen_selling_points/change_hook/strengthen_call_to_action/custom` | optimize 必填；CHECK exact 六项 |
| `custom_instruction_ciphertext` | `TEXT NULL` | existing cipher | custom 才必填；不得日志 |
| `prompt_version_id` | `BIGINT NOT NULL` | selected prompt | immutable version |
| `knowledge_snapshot_id` | `BIGINT NOT NULL` | P1 snapshot | root 唯一映射 |
| `draft_revision` | `BIGINT NOT NULL` | P0-C snapshot | create-time reference |
| `branch_revision` | `BIGINT NOT NULL` | P0-C snapshot | stale check |
| `generation_context_revision` | `BIGINT NOT NULL` | P0-C snapshot | stale check |
| `generation_input_hash` | `CHAR(64) NOT NULL` | P0-C snapshot | stale check |
| `questionnaire_hash` | `CHAR(64) NOT NULL` | P2 locked context | request/locked context exact |
| `knowledge_context_hash` | `CHAR(64) NOT NULL` | P2 locked context | request/locked context exact |
| `request_hash` | `CHAR(64) NOT NULL` | idempotency payload hash | same key comparison |
| `input_snapshot_hash` | `CHAR(64) NOT NULL` | all semantic columns hash | restart integrity |
| `industry_code` | `VARCHAR(64) NOT NULL` | input summary | provider input |
| `purpose_code` | `VARCHAR(64) NOT NULL` | input summary | provider input |
| `target_duration_seconds` | `INT NOT NULL` | rule input | >0 |
| `duration_tolerance_basis_points` | `INT NOT NULL` | rule input | 0..10000 |
| `effective_chars_per_minute` | `INT NOT NULL` | rule input | >0 |
| `rule_config_versions_json` | `JSON NOT NULL` | canonical versions | exact keys |
| `revision_snapshot_json` | `JSON NOT NULL` | P0-C revision snapshot | exact fields/order |
| `input_summary_json` | `JSON NOT NULL` | minimal scalar summary | no body/title |
| `questionnaire_refs_json` | `JSON NOT NULL` | question/revision/hash refs | stable order |
| `supplement_ref_json` | `JSON NOT NULL` | supplement revision/hash | exact null policy |
| `plan_refs_json` | `JSON NOT NULL` | A/B/C plan refs | exactly 3 |
| `fact_refs_json` | `JSON NOT NULL` | accepted fact refs | exact decision set |
| `prohibited_contents_json` | `JSON NOT NULL` | rule codes/pattern refs | no secret raw prompt |
| `actor_type` | `VARCHAR(16) NOT NULL` | `app_user` | CHECK exact |
| `actor_id` | `BIGINT NOT NULL` | same as creator | equality test |
| `created_at` | `DATETIME(3) NOT NULL` | freeze time | 无 updated/deleted |

约束/索引：PK；唯一 `(tenant_id,root_task_id)`；索引 `(tenant_id,draft_id,branch_revision)`、`(tenant_id,source_version_id)`、`(tenant_id,owner_type,owner_id,created_at,id)`；CHECK owner/actor/operation/duration，并分别约束 `generation_input_hash/questionnaire_hash/knowledge_context_hash/request_hash/input_snapshot_hash` 为 64 位小写 hex。`source_version_id` 对 P3 表采用 FK；`root_task_id/prompt_version_id/knowledge_snapshot_id` 跨模块仅逻辑 FK，避免迁移/模块耦合，但 Service 与真实 IT 必须证明存在且同 tenant/root。

### 9.5 Migration replay 和安全断言

- clean schema 运行顺序精确 `01,02,03,04,04a,05,06,07`；任何缺号、交换顺序或重复对象错误失败。
- 第二次只运行 `07`；允许 `IF NOT EXISTS` 的对象必须与第一次 `SHOW CREATE TABLE` 规范化结果逐字一致。
- replay 前后四表的列名、类型、nullable、default、comment、PK、unique、FK、index、check 集合相等。
- replay 不删除/重建已有表，不清空数据，不重置自增/雪花 ID，不宽化字段。
- migration IT 用独立 schema 名只允许由 `LocalIntegrationEnvironment` 派生，schema 必须位于 `ai_video_test` 安全前缀；结束只删除当前 run 创建的数据/对象。
- Redis 此 IT 不使用；如果启动装配自动连接 Redis，也必须验证独立 DB/run prefix，不能 `FLUSHALL/FLUSHDB`。
- 两个 tenant 写相同 draftId/rootTaskId/idempotencyKey，唯一键隔离正确；同 tenant 冲突准确。
- personal owner 与 workspace owner 的 owner_id 规则分别有正例；伪造 created_by_user_id/actor_id 不同有反例。
- raw SQL 能绕过 Service 时，DB 账号权限/constraint 能拒绝的项目逐项测试；无法用 DB 拒绝的跨行不变量由 Mapper/Service contract test 证明。

---

## 10. 规范 JSON、哈希与 provider schema

### 10.1 Canonical JSON 通用规则

1. UTF-8，无 BOM；序列化结果不含无意义空格或换行。
2. 对象 key 使用本节给定顺序；禁止依赖 HashMap 遍历顺序。
3. 列表按指定业务 key 排序；不按数据库返回顺序或字符串化数字排序。
4. Long ID 在 JSON 中写十进制字符串；revision/count/duration 等明确数值仍写 JSON integer。
5. 缺省语义使用明确 `null`；禁止省字段、空字符串代替 null 或自动填 0。
6. hash 输入按 UTF-8 bytes；输出 64 位小写 SHA-256。
7. parser 开启 duplicate-key detection、fail-on-unknown、fail-on-trailing-tokens、fail-on-number-for-ID。
8. string 在 hash 前做 Unicode NFC；不 trim 用户正文，只按字段规则拒绝首尾/空值；code/idempotency 可 trim 后校验并存 canonical 值。
9. 数组去重必须在校验阶段完成：有重复即失败，不能静默去重后继续。
10. 同一个 semantic payload 不因 JSON 输入 key 顺序变化而改变 canonical/hash；任何被列入语义列的值变化必须改变 hash。

### 10.2 `revision_snapshot_json`

```json
{
  "draftRevision": 41,
  "branchRevision": 17,
  "generationContextRevision": 9,
  "generationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "factDecisionRevisions": [
    {"factId": "101", "decisionRevision": 3},
    {"factId": "205", "decisionRevision": 8}
  ]
}
```

- `factDecisionRevisions` 从 P0-C Map 按 Long `factId` 升序转数组，避免 JSON object key 排序歧义。
- Map key/value null、factId <=0、revision <=0、重复 factId 均拒绝。
- 该数组必须与 P2 accepted facts/decision DTO 的 pair 集合完全相等。
- P0-C snapshot 的四个 scalar 与请求 expected/context read 分别相等；不相等在 task 创建前 46125。

### 10.3 `input_summary_json`

```json
{
  "industryCode": "education",
  "purposeCode": "lead_generation",
  "targetDurationSeconds": 60,
  "branchRevision": 17,
  "questionnaireHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "knowledgeContextHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "generationContextRevision": 9,
  "generationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

不允许出现 draft title、用户昵称、正文、问答全文、事实全文、联系方式、prompt 或 model 名。industry/purpose code 必须来自公共字典或已冻结业务 code，不存展示 label。

### 10.4 `questionnaire_refs_json` 与 `supplement_ref_json`

本计划不提供这两个字段的 JSON 示例，也不规定 question/supplement component 名称、Java 类型、排序 key 或 null 形态。F3 rebase 后，assembler 必须从 P2 handoff 的 component registry 读取最终结构和 schema digest，并把 P2 的 `answerIdentityJson`、`answerContextJson` 及其他答案/补充正文或规范 JSON 仅用于锁内校验、P1 route/snapshot 和 prompt 组装的短生命周期内存。

`questionnaire_refs_json`/`supplement_ref_json` 只能保存 P2 handoff 明确标记为可持久化 identity/revision/hash 的最小投影或其 digest，并绑定 component-registry SHA；任何被 handoff 标记为 answer context、supplement context、正文或规范 JSON 的原值都不得落入 task input。缺失/顺序/identity 规则完全由已审核 handoff 驱动；P3 不写旧字段兼容分支。F3 gate 对冻结行做敏感 canary 反测，确保传入 P2 正文/JSON 的唯一标记在数据库、日志、审计和 HTTP 响应中零命中。

### 10.5 `plan_refs_json`

```json
[
  {
    "candidateCode": "A",
    "planCode": "plan_a",
    "primaryTemplateVersionId": "9101",
    "angleCode": "angle_problem",
    "differentiatorTechniqueCode": "contrast"
  },
  {
    "candidateCode": "B",
    "planCode": "plan_b",
    "primaryTemplateVersionId": "9102",
    "angleCode": "angle_story",
    "differentiatorTechniqueCode": "narrative"
  },
  {
    "candidateCode": "C",
    "planCode": "plan_c",
    "primaryTemplateVersionId": "9103",
    "angleCode": "angle_proof",
    "differentiatorTechniqueCode": "evidence_first"
  }
]
```

- 列表长度和顺序精确 A/B/C；candidateCode 不能由列表位置推导后覆盖 provider 值。
- 每一项逐字段等于 P1 `KnowledgePlanDTO`；三项 `(planCode,angleCode,primaryTemplateVersionId,differentiatorTechniqueCode)` 组合不能重复。
- `KnowledgeRouteResultDTO.routingVersion/videoTypeCode/contentHash` 用于 snapshot 请求和 `inputSnapshotHash` 的语义前缀；若不单独列入 JSON，必须列入 hash scalar registry，不能丢失。
- 优化不重新创建 plans；从 source version/snapshot 恢复同一计划身份，provider 不允许更换 candidate/plan。

### 10.6 `fact_refs_json`

```json
[
  {
    "factId": "101",
    "decisionRevision": 3,
    "factHash": "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
  },
  {
    "factId": "205",
    "decisionRevision": 8,
    "factHash": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
  }
]
```

- 数值 Long 排序，禁止字符串顺序导致 `100` 在 `20` 前。
- accepted facts 中每项必须有 decision 对应，decision 也不能多出未接受 fact。
- `factHash` 与事实 DTO 同一快照一致；同 ID/revision 但 hash 不同视为 context stale。
- 事实正文和 sourceTitle/evidenceRef 不落 task input；调用 provider 时从 immutable knowledge snapshot 按这些 refs 恢复。

### 10.7 `prohibited_contents_json` 和规则版本

```json
{
  "policyVersion": "script-policy-1",
  "codes": [
    "camera_instruction",
    "unsupported_guarantee",
    "unsupported_price",
    "unsafe_contact"
  ]
}
```

```json
{
  "characterCounter": "effective-char-1",
  "durationEstimator": "script-duration-1",
  "resultValidator": "script-result-1",
  "similarity": "trigram-jaccard-1"
}
```

code 数组按 ordinal 排序且唯一。配置必须是版本/代码引用，不复制管理员可编辑的长规则文本。执行恢复时按冻结版本解析；版本不存在是 `SCRIPT_FROZEN_INPUT_INVALID`，不能回退当前版本。

### 10.8 `input_snapshot_hash` 精确 framing

hash 输入不是字符串拼接，按以下 frame 依序写入：

```text
P3_SCRIPT_INPUT\0
schemaVersion\0script-frozen-input-1\0
tenantId\0<decimal>\0
ownerType\0<utf8>\0
ownerId\0<decimal>\0
createdByUserId\0<decimal>\0
rootTaskId\0<decimal>\0
draftId\0<decimal>\0
branchId\0<decimal>\0
operationType\0<utf8>\0
generationMode\0<utf8-or-null-marker>\0
sourceVersionId\0<decimal-or-null-marker>\0
optimizationType\0<utf8-or-null-marker>\0
customInstructionDigest\0<sha256-or-null-marker>\0
promptVersionId\0<decimal>\0
knowledgeSnapshotId\0<decimal>\0
draftRevision\0<integer>\0
branchRevision\0<integer>\0
generationContextRevision\0<integer>\0
generationInputHash\0<lower-hex>\0
questionnaireHash\0<lower-hex>\0
knowledgeContextHash\0<lower-hex>\0
requestHash\0<lower-hex>\0
industryCode\0<utf8>\0
purposeCode\0<utf8>\0
targetDurationSeconds\0<integer>\0
durationToleranceBasisPoints\0<integer>\0
effectiveCharsPerMinute\0<integer>\0
ruleConfigVersionsJson\0<canonical-json>\0
revisionSnapshotJson\0<canonical-json>\0
inputSummaryJson\0<canonical-json>\0
questionnaireRefsJson\0<canonical-json>\0
supplementRefJson\0<canonical-json-or-null>\0
planRefsJson\0<canonical-json>\0
factRefsJson\0<canonical-json>\0
prohibitedContentsJson\0<canonical-json>\0
actorType\0app_user\0
actorId\0<decimal>\0
```

null marker 是单 byte `0xFF`，不能与空字符串相同。自定义指令只 hash plaintext digest，密文 nonce 不参与；重启解密后必须重算 digest。`inputSnapshotHash` 自身、数据库 ID、createdAt 不参与。

### 10.9 生成 provider 请求/响应

Prompt 必须包含结构化 section：schema version、三个冻结 plan、允许 facts、questionnaire/supplement material、duration config、prohibited codes、JSON-only instruction。Prompt 不包含 tenant/owner/actor/idempotency/额度/审计信息。

合法生成响应示例：

```json
{
  "schemaVersion": "script-generation-1",
  "candidates": [
    {
      "candidateCode": "A",
      "planCode": "plan_a",
      "angleCode": "angle_problem",
      "primaryTemplateVersionId": "9101",
      "differentiatorTechniqueCode": "contrast",
      "publishTitles": ["标题 A1", "标题 A2", "标题 A3"],
      "scriptText": "已校验的文案正文……",
      "factRefs": [{"factId": "101", "textFragment": "正文中的准确片段"}]
    },
    {
      "candidateCode": "B",
      "planCode": "plan_b",
      "angleCode": "angle_story",
      "primaryTemplateVersionId": "9102",
      "differentiatorTechniqueCode": "narrative",
      "publishTitles": ["标题 B1", "标题 B2", "标题 B3"],
      "scriptText": "与 A 明显不同的文案正文……",
      "factRefs": []
    },
    {
      "candidateCode": "C",
      "planCode": "plan_c",
      "angleCode": "angle_proof",
      "primaryTemplateVersionId": "9103",
      "differentiatorTechniqueCode": "evidence_first",
      "publishTitles": ["标题 C1", "标题 C2", "标题 C3"],
      "scriptText": "与 A、B 明显不同的文案正文……",
      "factRefs": [{"factId": "205", "textFragment": "另一准确片段"}]
    }
  ]
}
```

parser 必须检查顶层/候选/factRef exact key set。候选数组先验证输入顺序就是 A/B/C，再按 code 建映射复核唯一，不能排序后掩盖 provider 顺序错误。

### 10.10 优化 provider 请求/响应

合法响应示例：

```json
{
  "schemaVersion": "script-optimization-1",
  "sourceVersionId": "5001",
  "publishTitles": ["优化标题 1", "优化标题 2", "优化标题 3"],
  "scriptText": "优化后的文案正文……",
  "factRefs": [{"factId": "101", "textFragment": "优化正文中的准确片段"}]
}
```

sourceVersionId 必须等于 frozen source；优化结果保留 source 的 candidate/plan/snapshot/context/duration config，以 `source_type=ai_optimized`、`parent_version_id=source` 插入，不允许 provider 提交新的 plan 字段。

### 10.11 输出校验顺序

校验顺序固定，确保同一 payload 的错误码/attempt 结果确定：

1. raw byte size 上限和 UTF-8 合法性。
2. strict JSON syntax、duplicate key、trailing token。
3. schemaVersion 和 exact key set。
4. ID lexical/range。
5. candidate/cardinality/title cardinality。
6. route/source identity。
7. string code point length/nonblank/control characters。
8. fact ID/revision/hash/substring。
9. unsupported factual number/price/guarantee/prohibited policy。
10. effective character/duration tolerance。
11. A/B/C first-40 和 trigram similarity。

任一步失败只返回 46112 + 脱敏 rule code，不回显正文/JSON；repair prompt 可包含 rule code 和字段路径，不能包含日志之外的新敏感副本。

---

## 11. 收费任务、事务、lease 与幂等状态机

### 11.1 生成请求同步事务

```text
Controller validation + permission
  -> AppActorContext(app_user)
  -> workspace/object authorization
  -> IScriptGenerationService.createGeneration
      -> begin outer non-readOnly transaction
      -> FIRST call P2 lockCurrentContextForGeneration(draftId, branchId)
         (locks draft -> current_branch; verifies requested branch is current)
      -> while locks held, compare branchRevision + questionnaireHash + knowledgeContextHash
         and reject before operation_slot/quota_account/task_or_group_member
      -> read P2 accepted evidence/context identity under the same transaction snapshot
      -> compare final P2 handoff contextSemantics
      -> P1 route(request DTO)
      -> IAiTaskService actual `createChargeableTask`
         (continues operation_slot -> quota_account -> task_or_group_member;
          group exact script:{draftId}:{branchRevision})
      -> if reused: verify existing frozen input; return existing task IDs
      -> P1 snapshot(request DTO bound to rootTaskId)
      -> assemble + hash + insert av_script_task_input
      -> IAiTaskExecutionDispatcher actual `enqueue`
      -> IAppSecurityAuditService.append(script_generate_requested)
      -> commit；audit append 失败整体回滚
  -> return task IDs/reused
```

完整全局锁序始终是 `draft -> current_branch -> operation_slot -> quota_account -> task_or_group_member`。若 locked context/hash 或 route 在 task 创建前失败，没有 operation slot、额度锁、任务或冻结；若 snapshot/freeze/enqueue 在 task 创建后失败，外层事务回滚 task/lock/input/enqueue outbox。禁止 task 成功提交但 frozen input 不存在。

### 11.2 优化请求同步事务

```text
Controller validation + permission
  -> AppActorContext + owner authorization
  -> IScriptGenerationService.createOptimization
      -> begin outer non-readOnly transaction
      -> FIRST call P2 lockCurrentContextForGeneration(draftId, branchId)
         (locks draft -> current_branch; verifies requested branch is current)
      -> while locks held, compare branchRevision + questionnaireHash + knowledgeContextHash
         and reject before operation_slot/quota_account/task_or_group_member
      -> load owner-scoped immutable source version and historical snapshot refs
      -> verify locked eligibility hashes against request/source; never replace historical refs
      -> IAiTaskService actual `createChargeableTask`
         (continues operation_slot -> quota_account -> task_or_group_member;
          group exact script:{draftId}:{branchRevision})
      -> if reused: verify existing frozen input; return
      -> freeze sourceVersionId + inherited refs/config + encrypted custom instruction
      -> enqueue
      -> IAppSecurityAuditService.append(script_optimization_requested)
      -> commit；audit append 失败整体回滚
```

优化不调用 P1 route/snapshot；它必须调用 P2 locked current context，但只用于 branch/questionnaire/knowledge eligibility 和写入守卫，绝不拿 current context 替换 source 的历史 refs。source body/titles runtime load 后不能落 task input/prompt log。生成与优化都不得在调用 exact P2 锁入口前加载 source/current context、抢 slot、锁额度或创建任务。

### 11.3 Handler 正常生成时序

```text
scanner claims execution -> AiTaskExecutionLeaseDTO L1
  -> Handler registry selects SCRIPT_GENERATE
  -> read + validate frozen input by rootTaskId/owner/hash
  -> resolve immutable snapshot material by frozen IDs
  -> compute remaining lease
  -> if remaining <= providerTimeout + 10s: renew -> L2
  -> actual `startAttempt` using L2 and purpose initial -> attempt #1
  -> call ModelProvider outside transaction
  -> actual `completeAttempt` using attempt #1 and real ProviderUsageDTO
  -> strict parse/validate
  -> begin result transaction
      -> conditional read current branch/context/hash
      -> insert UserScript if absent using tenant/owner/creator
      -> insert A/B/C versions in fixed order
      -> actual `markSuccess` using current lease and A-version result reference
      -> P0-C settles exactly once inside terminal transition
  -> commit
```

`markSuccess` 影响行数不是 1 时回滚三个版本；重复 handler 在 unique `(rootTask,candidate)`/lease 条件下不能写第二组。provider 调用期间 Spring transaction synchronization 必须为空，IT 可通过 connection/transaction probe 断言。

### 11.4 Repair 和恢复

| 情况 | attempt purpose/序号 | provider 调用 | 业务写 | 终态 |
|---|---:|---:|---:|---|
| initial valid | initial/#1 | 1 | A/B/C | success+settle |
| initial structural invalid | initial/#1 | 1 | 0 | 同 lease 进入 repair |
| repair valid | repair/#2 | +1 | A/B/C | success+settle |
| repair invalid | repair/#2 | +1 | 0 | nonretryable failed+release |
| initial infra timeout | initial/#1 failed usage可空 | 1 | 0 | scanner retry after lease |
| recovery valid | recovery/#2 或 #3 | +1 | A/B/C | success+settle |
| recovery 仍 infra failure | recovery/至 #3 | +1 | 0 | 可恢复直到上限 |
| 请求第 4 次 | 无 handle | 0 | 0 | attempts exhausted+release |
| frozen input invalid | 无 attempt | 0 | 0 | nonretryable+release |
| stale after valid provider | attempt success+真实 usage | 1 | 0 | STALE_BRANCH_RESULT+release |

结构 invalid 是模型确定性输出问题，只允许同一处理过程一次 repair；租约恢复不能把已完成 initial/repair 的 purpose 重置。P0-C attempt 表是全局事实源，Handler 不用内存计数。

### 11.5 Lease 规则

- 每次 DB condition update 使用完整 `(executionTaskId,leaseOwner,leaseExpiresAt,status)` 条件。
- 读取 frozen/snapshot/组 prompt 可在 L1；在 provider 前重算 `remaining = expiresAt-now`。
- provider timeout 上限为 `remaining-10s`；值 <=0 必须 renew，不得传 0/负值或使用 provider default。
- renew 返回的新 owner/expiry snapshot L2 替代 L1；attempt、markSuccess、record failure 均传 L2。
- provider 回来后 lease 已过期时仍完成 attempt usage，但不得用过期 lease 写版本；record failure 交 scanner 恢复。
- scanner 同时只能有一个有效 lease；旧 owner 即使收到迟到响应也不能推进终态。
- 日志只记 root/execution/attempt sequence/purpose/provider request id/duration/error code，不记 prompt/output/body/title/custom instruction。

### 11.6 Stale 检查

provider 前可做一次快速检查节省外调，但最终权威检查必须在结果事务内重新读取：

| 冻结字段 | current 来源 | 比较 | 变化结果 |
|---|---|---|---|
| branchRevision | 当前 branch | exact Long | stale |
| questionnaireHash | P2 current context | constant-time lower hex | stale |
| knowledgeContextHash | P2 current context | constant-time lower hex | stale |
| generationContextRevision | P2 current context | exact Long | stale |
| generationInputHash | P2 current context | constant-time lower hex | stale |

异步最终 stale 判断不额外比较 draftRevision；draft 可能因无关状态更新而递增。fact decision 变化应推动 generationContextRevision/hash，因此不另读 Mapper。stale 路径 attempt 保持 success/real usage，不产生版本，不 `markSuccess`，不 settle，不 repair/retry provider。

### 11.7 结果事务和优化事务

- 生成三 insert 是一个 transaction；任何一项 duplicate/constraint/validation 失败全部回滚。
- `av_user_script` 首次创建与三 version 同事务；已存在时只增加 versions，不覆盖 current confirmed title。
- result reference 固定 `resultRefType=script_version`、`resultRefId=A.versionId`；B/C 通过 root task + candidate 查询。
- 优化只插入一个 child，result reference 为该 child；source/version 不变。
- terminal transition settle 由 P0-C 统一执行；P3 不写 usage/quota ledger。
- 同一结果事务内的 `script_version_created` audit 对生成动作只写一个 root-level digest；append 失败使三个版本、终态、settle 一起回滚，且不能为 A/B/C 重复写三条含正文的审计。

### 11.8 Idempotency 与 slot 矩阵

| 操作 | key scope | request hash 语义 | slot | same key/same hash | same key/diff hash |
|---|---|---|---|---|---|
| initial generate | actor+draft | branch/context/input + questionnaire/knowledge hashes + mode + tariff | `script:{draft}:{branchRev}:{inputHash}` | reused | 46116 |
| regenerate | actor+draft | 同上 + regenerate intent | 同 branch/hash slot | reused/46123按 P0-C | 46116 |
| optimize | actor+sourceVersion | source + branch/context/input + questionnaire/knowledge hashes + type + custom digest + tariff | `optimize:{sourceVersion}` | reused | 46116 |
| manual version | actor+script | source+text digest+titles+index+expected | 不创建 task | existing version | 46116 |
| confirmation | actor+script | version+index+expected triple | 不创建 task | existing confirmation | 46116 |

- 前端 retry 不创建新 key；用户明确点击“新一轮生成/再次优化”才创建新 intent key。
- 46123 返回/查询 active task 时前端附着原 root，不自动新建任务。
- key 至少 128-bit 随机、只允许固定 lexical 格式；服务端不接受空/超长/复用到其他 actor/resource。
- request hash 不含响应字段、数据库 ID/时间；custom instruction 用 plaintext digest。

### 11.9 Manual version transaction

```text
authorize actor/owner
  -> load source immutable version
  -> verify expected branch/context/hash
  -> validate exactly 3 distinct titles + index + text/duration/policy
  -> compute request hash
  -> lookup tenant/script/idempotency
  -> same hash: return existing
  -> different hash: 46116
  -> insert child source_type=manual_edit
  -> append audit script_version_created
  -> commit；audit append 失败整体回滚
```

child 继承 `knowledgeSnapshotId/sourceTaskId/plan/angle/template/differentiator/target/cpm/tolerance/rule versions/validator`，以及 `branchRevision/questionnaireHash/knowledgeContextHash/generationContextRevision/generationInputHash` 五项冻结上下文；重算正文字符/时长。人工改动不能改事实 snapshot 或伪造成新 generated candidate。

### 11.10 Confirmation transaction

```text
authorize actor/owner
  -> load script + version + current branch/context
  -> require version belongs to script/owner and expected triple exact
  -> read selectedTitle = version.publishTitles[index]
  -> idempotency same/different payload check
  -> insert immutable confirmation history
  -> conditional update draft revision expected -> expected+1
  -> conditional update user script pointer/title/scriptRevision
  -> append audit script_confirmed
  -> commit；audit append 失败整体回滚
```

任何 conditional update 影响 0 行都回滚 confirmation，映射 46116 或 46125；客户端必须 refresh/reconfirm。确认不会修改 version.selectedTitleIndex；confirmation 自己记录 index/snapshot，保留历史选择。

---

## 12. 七接口精确 HTTP 契约

### 12.1 通用边界

- Base path `/api/studio`，沿用共享 `R<T>` 与 `PageResult`/HTTP envelope，不定义第二套结果。
- Request/response `Content-Type: application/json`；删除 expected revision 使用明确 query 参数并 validation。
- 所有 Long ID 输入/输出是无前导零十进制 string；`"0"`、负数、空白、指数、小数、JSON number、超过 Long 均 400。
- `idempotencyKey` 是 string，trim 后按公共格式验证；服务端 hash 使用 canonical BO，不用 raw JSON key 顺序。
- 时间使用现有 UTC ISO-8601 格式；不得返回本机无 offset 时间。
- 业务错误使用共享 code；401/403 不包装为 200；分页无数据 `rows=[]` 且 total=0。
- write endpoint 使用 `@RepeatSubmit`，但重复保护不能替代业务 idempotency。

### 12.2 `POST /api/studio/script-generations`

请求字段 exact：

```json
{
  "draftId": "1001",
  "branchId": "2001",
  "draftRevision": 41,
  "branchRevision": 17,
  "generationContextRevision": 9,
  "generationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "questionnaireHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "knowledgeContextHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "generationMode": "initial",
  "idempotencyKey": "018f0b30-7a3d-7c00-8000-000000000001",
  "tariffVersion": "3001"
}
```

`generationMode` 只允许公共契约列出的 initial/regenerate code。响应 data exact：

```json
{
  "rootTaskId": "4001",
  "executionTaskId": "4002",
  "usageOperationId": "4003",
  "reused": false
}
```

权限 `aivideo:studio:generate`；Service 内再校验 workspace/draft owner、quota use。审计 `script_generate_requested` 在任务/冻结/enqueue 的同一事务内一次，append 失败整体回滚。

### 12.3 `POST /api/studio/scripts/{scriptId}/versions`

请求 exact：

```json
{
  "sourceVersionId": "5001",
  "scriptText": "人工编辑后的正文",
  "publishTitles": ["标题一", "标题二", "标题三"],
  "selectedTitleIndex": 0,
  "idempotencyKey": "018f0b30-7a3d-7c00-8000-000000000002",
  "expectedBranchRevision": 17,
  "expectedGenerationContextRevision": 9,
  "expectedGenerationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
}
```

响应 `ScriptVersionVo`。权限 `aivideo:script:edit`；审计 `script_version_created`。拒绝第四标题、重复标题、index 3、空正文、跨 script source、请求修改 snapshot/plan/sourceType。

### 12.4 `POST /api/studio/scripts/{scriptId}/optimizations`

请求 exact：

```json
{
  "sourceVersionId": "5001",
  "draftId": "1001",
  "branchId": "2001",
  "branchRevision": 17,
  "generationContextRevision": 9,
  "generationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "questionnaireHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "knowledgeContextHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "optimizationType": "shorter",
  "customInstruction": null,
  "idempotencyKey": "018f0b30-7a3d-7c00-8000-000000000003",
  "tariffVersion": "3001"
}
```

`optimizationType` 只允许 `shorter`、`more_colloquial`、`strengthen_selling_points`、`change_hook`、`strengthen_call_to_action`、`custom` 六项；`custom` 时 customInstruction 1..2000 code points，其余五项必须 null。响应 shape 同 ScriptTaskVo。Controller 需要 generate permission，Service 再检查 edit/quota use/owner。审计 `script_optimization_requested` 不含 custom 文本。

### 12.5 `POST /api/studio/scripts/{scriptId}/confirmations`

请求 exact：

```json
{
  "versionId": "5001",
  "selectedTitleIndex": 1,
  "draftId": "1001",
  "branchId": "2001",
  "expectedBranchRevision": 17,
  "expectedGenerationContextRevision": 9,
  "expectedGenerationInputHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
  "idempotencyKey": "018f0b30-7a3d-7c00-8000-000000000004"
}
```

请求不允许 `selectedTitle`。响应 exact 包含 `confirmationId/scriptId/versionId/selectedTitleIndex/selectedTitle/draftId/branchId/confirmedDraftRevision/confirmedAt`。权限 `aivideo:script:confirm`；审计 `script_confirmed` 只记录 version/index/revision digest。

### 12.6 `GET /api/studio/scripts`

query exact：`keyword` optional、`draftId` optional decimal string、`industryCode` optional、`purposeCode` optional、`sourceType` optional in `generated/manual_edit/ai_optimized`、`confirmationStatus` optional in `confirmed/unconfirmed`、`updatedTimeStart` optional UTC ISO instant、`updatedTimeEnd` optional UTC ISO instant、`pageNum` 1-based、`pageSize` 1..100、`sortField` in `updateTime/createTime/displayTitle`、`sortOrder` in `ascend/descend`。updated-time range 的 start/end 必须同时为空或同时存在，且 start < end；下界 `>= updatedTimeStart`、上界 `< updatedTimeEnd`。五类过滤（industryCode、purposeCode、sourceType、confirmationStatus、updated-time range）与 keyword/draftId 之间全部 AND 组合；缺 sort 使用 `updateTime desc,id desc`，同值必须以 id 稳定翻页。

`sourceType` 过滤作用于 display version：存在当前确认时取 current confirmed version，否则取按 `createdAt desc,versionId desc` 的最新 immutable version。`confirmationStatus=confirmed` 表示存在 current confirmation，`unconfirmed` 表示不存在；industry/purpose 必须来自 script 创建时冻结的分类字段，不回看当前 draft。非法 enum、半边时间范围、start>=end 在 Controller validation 失败且不调用 Service。

row exact：

```json
{
  "scriptId": "6001",
  "draftId": "1001",
  "industryCode": "beauty",
  "purposeCode": "conversion",
  "sourceType": "manual_edit",
  "confirmationStatus": "confirmed",
  "displayTitle": "已确认标题",
  "currentConfirmedVersionId": "5001",
  "versionCount": 7,
  "referenceCount": 1,
  "createdAt": "2026-08-02T01:02:03.000Z",
  "updatedAt": "2026-08-02T02:03:04.000Z"
}
```

权限 `aivideo:script:query`；只返回 actor visible owner；keyword 按 escape 后 title 搜索，禁止 raw `%/_` 扩大范围。

### 12.7 `GET /api/studio/scripts/{scriptId}`

响应 exact 只有 `summary`、flat immutable `versions`、`currentConfirmation`、`downstreamReferences`；禁止服务端返回 `children` 或 tree-shaped versions。`versions` 每项含 Section 2.1 `ScriptVersionDTO` 全部字段，ID 为 decimal string，并按 `createdAt asc,versionId asc` 稳定排序。前端 adapter 先检测 duplicate ID、missing parent、cross-script parent、cycle，再由前端组装树；失败显示共享 typed error，不返回或渲染部分树。

```json
{
  "summary": {
    "scriptId": "6001",
    "draftId": "1001",
    "industryCode": "beauty",
    "purposeCode": "conversion",
    "sourceType": "manual_edit",
    "confirmationStatus": "confirmed",
    "displayTitle": "已确认标题",
    "currentConfirmedVersionId": "5001",
    "versionCount": 7,
    "referenceCount": 4,
    "createdAt": "2026-08-02T01:02:03.000Z",
    "updatedAt": "2026-08-02T02:03:04.000Z"
  },
  "versions": [],
  "currentConfirmation": {
    "confirmationId": "7001",
    "versionId": "5001",
    "selectedTitleIndex": 1,
    "selectedTitle": "已确认标题",
    "confirmedAt": "2026-08-02T02:03:04.000Z"
  },
  "downstreamReferences": {
    "voiceCount": 1,
    "digitalHumanCount": 1,
    "workCount": 2,
    "total": 4,
    "latestGeneratedAt": "2026-08-02T03:04:05.000Z"
  }
}
```

没有确认时 `currentConfirmation=null`。三个引用计数均为非负 integer，`total=voiceCount+digitalHumanCount+workCount`；无引用时 total=0 且 `latestGeneratedAt=null`，否则它是三个下游类别最后生成时间的 UTC 最大值。`summary.referenceCount` 必须等于 `downstreamReferences.total`，adapter 发现不一致即 fail-closed。

详情只能按 stored `knowledgeSnapshotId` 返回历史 snapshot ref；不把当前发布知识或 P2 current context 混入历史。权限 `aivideo:script:query`；cross-owner 与不存在对调用方使用项目统一 anti-enumeration 语义。

### 12.8 `DELETE /api/studio/scripts/{scriptId}`

query `expectedDraftRevision` 为 positive integer。权限 `aivideo:script:remove`。执行顺序：authorize→owner→重新聚合 `downstreamReferences` 三类计数→expected revision→logical delete user script→同事务 append `script_removed`→commit；append 失败回滚逻辑删除。versions/confirmations/task inputs 保留用于审计/引用；查询默认排除 deleted script。权威 `downstreamReferences.total>0` 返回 46118；重复删除遵循公共 delete idempotency，不泄露跨 owner 存在性。

### 12.9 Permission、actor 与 audit 矩阵

| 路由 | Controller 权限 | Service 内二次检查 | actor | 同事务 audit action |
|---|---|---|---|---|
| create generation | `aivideo:studio:generate` | draft owner + quota use | app_user | script_generate_requested |
| manual version | `aivideo:script:edit` | script/source owner | app_user | script_version_created |
| optimize | `aivideo:studio:generate` | edit + quota use + source owner | app_user | script_optimization_requested |
| confirm | `aivideo:script:confirm` | script/version/draft owner | app_user | script_confirmed |
| list | `aivideo:script:query` | visible scope | app_user | none |
| detail | `aivideo:script:query` | object owner | app_user | none |
| delete | `aivideo:script:remove` | object owner + reference check | app_user | script_removed |

每个写接口测试以下 audit assertions：resourceType=`script` 或 `script_task` 按公共契约；resourceId 是业务主体/root；actorType=`app_user`；actorId=current app user；before/after digest 是固定字段 SHA；reason 是稳定 code。事务 rollback、validation、401、403、owner reject、idempotent read 不写新审计。

### 12.10 HTTP 状态/错误矩阵

| 场景 | HTTP | 业务 code/动作 |
|---|---:|---|
| app token valid | 200/accepted per shared contract | data exact |
| sys token | 401 | app auth handler |
| missing permission | 403 | no retry/audit |
| cross workspace/owner | project anti-enumeration status | no data/audit |
| malformed decimal ID/JSON | 400 | validation envelope |
| active slot | business response | 46123 + attach task |
| idempotency/revision conflict | business response | 46116 |
| non-current branch | business response | 46117 |
| referenced delete | business response | 46118 |
| context stale | business response | 46125 |
| model invalid async | task failed | 46112 in task error |
| network/provider infra | task pending/retry or failed | P0-C typed state |

Controller tests必须断言 status、code、data/null、audit call count、Service call count；不能只断言中文 message。

---

## 13. 前端类型、query key、Mock 与完整可见状态

### 13.1 TypeScript 基础类型

`src/services/ai-video/scripts/types.ts` 至少冻结以下类型；`DecimalId` 是品牌类型或 runtime-refined string，不能只是任意 string 后不校验：

```ts
export type DecimalId = string & { readonly __decimalId: unique symbol };

export type ScriptTaskStatus =
  | 'pending'
  | 'queued'
  | 'running'
  | 'success'
  | 'failed'
  | 'cancelled'
  | 'expired';

export type ScriptSourceType =
  | 'generated'
  | 'manual_edit'
  | 'ai_optimized';

export type ScriptCandidateCode = 'A' | 'B' | 'C';

export interface ScriptTaskRef {
  rootTaskId: DecimalId;
  executionTaskId: DecimalId;
  usageOperationId: DecimalId;
  reused: boolean;
}

export interface ScriptVersion {
  scriptId: DecimalId;
  versionId: DecimalId;
  parentVersionId: DecimalId | null;
  candidateCode: ScriptCandidateCode | null;
  sourceType: ScriptSourceType;
  planCode: string | null;
  angleCode: string | null;
  primaryTemplateVersionId: DecimalId | null;
  differentiatorTechniqueCode: string | null;
  angleSummary: string;
  publishTitles: readonly [string, string, string];
  selectedTitleIndex: 0 | 1 | 2;
  scriptText: string;
  effectiveCharacterCount: number;
  estimatedDurationSeconds: number;
  targetDurationSeconds: number;
  effectiveCharsPerMinute: number;
  durationToleranceBasisPoints: number;
  validatorVersion: string;
  knowledgeSnapshotId: DecimalId;
  sourceTaskId: DecimalId | null;
  branchRevision: number;
  generationContextRevision: number;
  generationInputHash: string;
  questionnaireHash: string;
  knowledgeContextHash: string;
  createdAt: string;
}

export interface UserScriptSummary {
  scriptId: DecimalId;
  draftId: DecimalId;
  industryCode: string;
  purposeCode: string;
  sourceType: ScriptSourceType;
  confirmationStatus: 'confirmed' | 'unconfirmed';
  displayTitle: string | null;
  currentConfirmedVersionId: DecimalId | null;
  versionCount: number;
  referenceCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CurrentScriptConfirmation {
  confirmationId: DecimalId;
  versionId: DecimalId;
  selectedTitleIndex: 0 | 1 | 2;
  selectedTitle: string;
  confirmedAt: string;
}

export interface ScriptDownstreamReferences {
  voiceCount: number;
  digitalHumanCount: number;
  workCount: number;
  total: number;
  latestGeneratedAt: string | null;
}

export interface UserScriptDetail {
  summary: UserScriptSummary;
  versions: readonly ScriptVersion[];
  currentConfirmation: CurrentScriptConfirmation | null;
  downstreamReferences: ScriptDownstreamReferences;
}

export interface CanonicalScriptListFilters {
  keyword: string | null;
  draftId: DecimalId | null;
  industryCode: string | null;
  purposeCode: string | null;
  sourceType: ScriptSourceType | null;
  confirmationStatus: 'confirmed' | 'unconfirmed' | null;
  updatedTimeStart: string | null;
  updatedTimeEnd: string | null;
  pageNum: number;
  pageSize: number;
  sortField: 'updateTime' | 'createTime' | 'displayTitle';
  sortOrder: 'ascend' | 'descend';
}
```

Runtime adapter 必须验证：decimal ID lexical/range、ISO time、integer revisions/counts、64 lower hex、tuple length=3/distinct titles、enum closed set、candidate A/B/C、flat versions 没有 `children`、parent references、`summary.referenceCount===downstreamReferences.total`、三类计数之和等于 total、confirmation version/title/index 与对应 immutable version 一致。`as DecimalId` 只能出现在 adapter 的单一 refine 函数之后；组件和 Mock 不得自行 cast。服务端响应一旦出现 tree-shaped versions、未知 filter enum、半边 updated-time range 或计数不一致即 fail-closed。

### 13.2 请求类型

```ts
export interface CreateScriptGenerationInput {
  draftId: DecimalId;
  branchId: DecimalId;
  draftRevision: number;
  branchRevision: number;
  generationContextRevision: number;
  generationInputHash: string;
  questionnaireHash: string;
  knowledgeContextHash: string;
  generationMode: 'initial' | 'regenerate';
  idempotencyKey: string;
  tariffVersion: DecimalId;
}

export interface CreateManualScriptVersionInput {
  sourceVersionId: DecimalId;
  scriptText: string;
  publishTitles: readonly [string, string, string];
  selectedTitleIndex: 0 | 1 | 2;
  idempotencyKey: string;
  expectedBranchRevision: number;
  expectedGenerationContextRevision: number;
  expectedGenerationInputHash: string;
}

export interface CreateScriptOptimizationInput {
  sourceVersionId: DecimalId;
  draftId: DecimalId;
  branchId: DecimalId;
  branchRevision: number;
  generationContextRevision: number;
  generationInputHash: string;
  questionnaireHash: string;
  knowledgeContextHash: string;
  optimizationType:
    | 'shorter'
    | 'more_colloquial'
    | 'strengthen_selling_points'
    | 'change_hook'
    | 'strengthen_call_to_action'
    | 'custom';
  customInstruction: string | null;
  idempotencyKey: string;
  tariffVersion: DecimalId;
}

export interface ConfirmScriptVersionInput {
  versionId: DecimalId;
  selectedTitleIndex: 0 | 1 | 2;
  draftId: DecimalId;
  branchId: DecimalId;
  expectedBranchRevision: number;
  expectedGenerationContextRevision: number;
  expectedGenerationInputHash: string;
  idempotencyKey: string;
}
```

confirm input 没有 selectedTitle。`customInstruction` 的 discriminated validation 在提交前执行：custom 必填，其他 type 必须 null。请求 serialization 必须保持 ID string，禁止 `Number(id)`。

### 13.3 七个 service 函数

`src/services/ai-video/scripts/api.ts` 只导出：

```ts
createScriptGeneration(input): Promise<ScriptTaskRef>
createManualScriptVersion(scriptId, input): Promise<ScriptVersion>
createScriptOptimization(scriptId, input): Promise<ScriptTaskRef>
confirmScriptVersion(scriptId, input): Promise<ScriptConfirmation>
queryUserScripts(params): Promise<PageResult<UserScriptSummary>>
getUserScript(scriptId): Promise<UserScriptDetail>
deleteUserScript(scriptId, expectedDraftRevision): Promise<void>
```

URL 建立在该文件私有常量 `/api/studio` 上；任何其他 P3 文件出现 `/api/studio/script` 片段都使 scan 失败。response 先经共享 envelope，再经 P3 adapter；错误只用共享 typed code。

### 13.4 Query keys

```ts
export const scriptKeys = {
  all: (workspaceId: DecimalId) => ['aivideo', 'scripts', workspaceId] as const,
  lists: (workspaceId: DecimalId) => [...scriptKeys.all(workspaceId), 'list'] as const,
  list: (workspaceId: DecimalId, filters: CanonicalScriptListFilters) =>
    [...scriptKeys.lists(workspaceId), filters] as const,
  details: (workspaceId: DecimalId) => [...scriptKeys.all(workspaceId), 'detail'] as const,
  detail: (workspaceId: DecimalId, scriptId: DecimalId) =>
    [...scriptKeys.details(workspaceId), scriptId] as const,
  flow: (workspaceId: DecimalId, draftId: DecimalId, branchId: DecimalId) =>
    ['aivideo', 'script-flow', workspaceId, draftId, branchId] as const,
  task: (
    workspaceId: DecimalId,
    draftId: DecimalId,
    branchId: DecimalId,
    rootTaskId: DecimalId,
  ) => ['aivideo', 'script-task', workspaceId, draftId, branchId, rootTaskId] as const,
};
```

- filters 必须 canonical：固定字段顺序为 `keyword,draftId,industryCode,purposeCode,sourceType,confirmationStatus,updatedTimeStart,updatedTimeEnd,pageNum,pageSize,sortField,sortOrder`；trim keyword/industryCode/purposeCode，空串转 null；page/pageSize integer；sort 映射 server value；updatedTimeStart/end 同时为空或同时为规范 UTC ISO 且 start<end。五类过滤的任一变化都进入 query key 并把 pageNum 重置为 1，不能把未 canonical 的 ProTable 对象直接放入 key。
- generation success invalidates flow、task、lists、detail(new script)；manual success invalidates detail/list；confirm success invalidates detail/list/draft downstream reference；delete success remove detail + invalidate lists。
- workspace logout/switch 清该 workspace prefix；branch switch 清 flow/task，不清历史 library detail。
- 任何 mutation 不得 `invalidateQueries()` 无 key，也不得跨 workspace invalidate。

### 13.5 `useScriptFlow` 状态机

```text
idle
  -> cost_confirmation
  -> submitting
  -> queued
  -> running
  -> success(candidates)
  -> editing
  -> manual_saving
  -> optimizing
  -> confirming
  -> confirmed

submitting/queued/running
  -> failed | cancelled | expired

any request state
  -> auth_required | forbidden | network_error | context_stale | revision_conflict
```

状态和允许动作：

| 状态 | 可见组件 | 主按钮 | 禁用/允许 |
|---|---|---|---|
| idle | 生成说明/当前 context | 生成三套文案 | context ready 才允许 |
| cost_confirmation | 费率/预计额度/余额 | 确认并生成 | 可取消为 idle |
| submitting | Spin/禁用表单 | 提交中 | 禁止第二次点击 |
| queued | task panel/排队说明 | 无 | 可离页恢复 |
| running | progress/阶段说明 | 无 | 可离页恢复 |
| success | A/B/C cards | 选标题/编辑/优化/确认 | 保留 task ref |
| editing | editor/三个标题 | 保存新版本 | source immutable |
| manual_saving | saving | 无 | 防重复 |
| optimizing | task panel | 无 | source/card 保留 |
| confirming | confirm summary | 确认中 | 防重复 |
| confirmed | result/下一步 | 进入声音 | reference exact |
| failed | typed error/retry | 重试 | 明确用户点击，same key policy |
| cancelled | neutral Result | 重新开始 | 不标红失败 |
| expired | expired Result | 重新开始 | 新 intent key |
| auth_required | 登录恢复 | 去登录 | 401 只触发一次 |
| forbidden | 403 Result | 返回 | 不自动 retry |
| network_error | network alert | 重试 | same key、有界 |
| context_stale | stale alert | 刷新上下文 | 清旧选择，不自动提交 |
| revision_conflict | conflict Modal | 刷新并重新确认 | 不覆盖 server version |

### 13.6 Polling 和 retry

- submit 成功拿到 root 后立即写可恢复 state，再开始 polling；刷新页面从 query/cache/active task 恢复。
- elapsed `0..10s` 每 1 秒；之后每 2 秒；后台 tab 可按共享策略降频但恢复 focus 只发一次即时请求。
- terminal status 后不再 schedule；unmount、workspace/draft/branch/root 变化、logout 都 abort fetch + clear timer。
- 连续三次 network/timeout/5xx 后停止自动 polling并显示用户重试；点击重试只恢复 polling，不新建收费 task。
- 401 调共享 logout once；后续并发 401 不重复；403 立即终止；429/46123 按 typed code，不当 network retry。
- `failed` 是否允许重新发起按 error retryable 标志；新收费操作必须重新显示费用确认。
- `cancelled` 是用户/系统中性终态；`expired` 表示任务不可恢复，不能附着旧 execution。

### 13.7 A/B/C 卡片

每张卡必须显示：

1. candidate A/B/C 固定标签和推荐顺序。
2. `angleSummary` 作为“为什么推荐”，必须是后端 `script-recommendation-1` 按 route rank + P1 plan 派生的非空值；adapter 拒绝空值，页面不从 provider 字段、本地字典或组件文案重新计算。
3. 三个 publish titles 的 radio group，label 包含候选与序号。
4. scriptText 可展开预览，保留换行，不用 `dangerouslySetInnerHTML`。
5. effective character count、estimated/target duration、tolerance。
6. plan/angle/template/differentiator 的用户可理解 label；label 查不到显示 code，不丢字段。
7. “复制文案”“人工编辑”“AI 优化”“确认使用”四个动作；复制只写当前卡 `scriptText`，成功/失败显示精确反馈；权限/额度/任务占用时分别 disabled + 可见原因。
8. confirmed/manual/optimized 节点显示 source badge 和 parent link。

候选 payload 不完整时整个结果进入 `no-candidates/invalid-result` error state，不渲染两个卡后让用户误选。三卡正文相同/标题重复等应由后端拒绝，adapter/Mock 仍写反例保护。

### 13.8 Editor、optimization、confirmation

- Editor 初始化 source 文本和三个标题的副本只存在 local form；保存产生 child，不 mutation cached source。
- 字符/标题/时长提示与后端同规则，但前端提示不作为安全边界；emoji/CJK 计数使用 shared tested helper或明确后端预览结果。
- 离开 dirty editor 有确认；保存成功 reset dirty state 并定位 child；失败保留输入。
- Optimization type 的 closed options 精确为 `shorter`、`more_colloquial`、`strengthen_selling_points`、`change_hook`、`strengthen_call_to_action`、`custom`；不得保留旧别名。custom 字符计数；提交前展示费用/费率/余额变化，rate changed 必须重新确认。
- Candidate card 与 detail drawer 的“复制文案”都调用 `navigator.clipboard.writeText(scriptText)`；Promise resolve 显示“文案已复制”，reject/throw 显示“复制失败，请手动选择文本”。组件不能吞异常、不能在失败分支先发成功 message，也不能把复制动作当服务端写操作。
- Confirmation panel 显示 script/version/candidate/source、选中标题、context revision/hash 摘要；请求只发送 index。
- 46116 打开 conflict Modal，用户可 refresh/cancel；46125 清旧 context 并返回需求步骤；不能静默重试写。

### 13.9 文案库页面

| 状态 | API/Mock | 页面要求 | 测试断言 |
|---|---|---|---|
| loading | delayed list | table skeleton | 无假空态闪烁 |
| initial empty | rows=[] keyword empty | 引导生成 | 按钮回 ScriptStep |
| search empty | rows=[] keyword nonempty | 清搜索/说明 | 不显示初始引导 |
| one page | rows<pageSize | rows/count | pagination 合理 |
| many pages | total>pageSize | server paging | page 参数精确 |
| sort update | server order | column sorter | updateTime mapping |
| sort create | server order | column sorter | createTime mapping |
| sort title | server order | column sorter | displayTitle mapping |
| null rows | adapter input null | [] | 组件不 crash |
| network | rejected fetch | Alert + retry | 不自动无限 |
| timeout | AbortError typed | timeout message + retry | same params |
| 5xx | typed error | generic service error | 用户 retry |
| 401 | auth error | shared recovery | logout once |
| 403 | forbidden | Result 403 | 无 retry loop |
| page emptied after delete | last row removed | previous page/refetch | 不留空 page |

ProTable `request` 返回 `{data,success,total}` 只能由 adapter page result生成。keyword 输入 debounce/submit 按现有规范，旧请求 abort，迟到响应不能覆盖新 keyword。

ProTable 必须精确提供五类过滤控件：industryCode、purposeCode、sourceType、confirmationStatus、updated-time range。sourceType 选项只含 generated/manual_edit/ai_optimized；confirmationStatus 只含 confirmed/unconfirmed；时间范围提交 UTC 的 `updatedTimeStart/updatedTimeEnd`。任意组合按 AND 发送，清除单项只清该项，点击“重置”清五类过滤、keyword 和 draftId 并回到第一页。Mock、api query serialization、query key、adapter 和真实 Service/BO 必须使用同名字段，组合过滤测试至少覆盖五类全选、逐项清除、半边时间拒绝和稳定翻页。

### 13.10 详情版本树

- HTTP/Mock 只提供 flat immutable `versions`，禁止 `children`；先由 adapter 校验所有 node ID 唯一、parent 存在或 null、同 script、无 cycle，再由前端唯一 tree builder 构树。
- roots 可以是 A/B/C 三个 generated versions；manual/optimized 挂 source parent；按 createdAt/id 稳定排序。
- 每节点显示 sourceType、candidate、三 titles/selected index、正文、metrics、snapshot/task/context refs、createdAt。
- `currentConfirmation` exact 为 `{confirmationId,versionId,selectedTitleIndex,selectedTitle,confirmedAt}` 或 null；`currentConfirmedVersionId` 节点高亮，标题/index 只取 currentConfirmation，并验证它指向的 immutable version 对应 title，不能从 array position 或 version 的历史 selectedTitleIndex 猜当前确认。
- `downstreamReferences` exact 为 `{voiceCount,digitalHumanCount,workCount,total,latestGeneratedAt}`；详情分别展示三类计数、合计和最近生成时间，无引用时最近时间显示“暂无”。
- broken tree/cycle 显示错误 Result + retry/report，绝不 silently promote orphan root。
- historical snapshot 只显示 frozen ID/refs；若 snapshot service返回 unavailable 显示历史资料不可用，不替换当前资料。

### 13.11 删除与引用

- delete button 对无 remove permission disabled；点击先显示 script title/version count，以及 voice/digital-human/work 分项和 `downstreamReferences.total` 的不可逆影响。
- `downstreamReferences.total>0` 可提前 disabled，但提交时仍由服务端重新聚合权威计数；46118 显示三类引用阻塞和去查看引用入口。`summary.referenceCount` 仅为列表兼容汇总，详情动作不得绕开结构化 downstreamReferences。
- confirm Modal 要求明确危险按钮；Esc/cancel 不发请求；double click 只一个 mutation。
- success remove detail cache、invalidate list、选择相邻 row；failure 保留 drawer/row。
- deleted script 的历史 version 不能再被下游选择；已打开 Voice/Base 必须 revalidate 并阻止继续。

### 13.12 下游 reference

跨步骤 state 唯一允许：

```ts
export interface ConfirmedScriptReference {
  scriptId: DecimalId;
  versionId: DecimalId;
  selectedTitleIndex: 0 | 1 | 2;
}
```

禁止 `scriptText`、`publishTitles`、`selectedTitle`、candidate array index、knowledge snapshot body。Voice/Base 每次进入和最终提交前用 IDs revalidate：actor 可访问、script 未删除、version 属于 script、该版本/标题 index 仍是当前确认。失败返回 ScriptStep 并显示 typed reason。

### 13.13 API + Mock + 组件三重状态矩阵

每一行都必须同时有 adapter/API test、`mock/aivideo-scripts.ts` fixture/handler、可见组件 test：

| 状态 | API/adapter | Mock | 组件 |
|---|---|---|---|
| auth recovery | 401 typed | 401 once scenario | login recovery |
| loading | pending promise | delay | Skeleton/Spin |
| initial empty | rows=[] | empty store | initial Empty |
| search empty | rows=[]+keyword | no match | search Empty |
| pagination | page/total | >1 page | ProTable page |
| network error | network typed | reject | Alert/retry |
| timeout | timeout typed | delayed abort | timeout/retry |
| 5xx | server typed | 500 | service Alert |
| 401 once | auth typed | concurrent 401 | single logout |
| 403 | forbidden typed | 403 | Result 403 |
| submit guard | inFlight state | delayed POST | disabled button |
| cancel neutral | cancelled enum | task cancelled | neutral Result |
| query refresh | scoped keys | updated store | new visible row |
| revision conflict | 46116 | conflicting request | conflict Modal |
| context stale | 46125 | bumped context | refresh flow |
| queued | queued enum | queued task | queue panel |
| running | running enum | progress | progress panel |
| success | success/results | A/B/C | candidate cards |
| failed | typed task error | failed task | retry decision |
| expired | expired enum | expired task | new intent action |
| generate | task ref | POST handler | cost→task |
| repair | task phase/status | invalid→valid scenario | phase explanation |
| no candidates | strict adapter fail | malformed result | invalid result |
| quota low | quota typed | low balance | recharge/disabled |
| rate changed | tariff conflict | changed tariff | reconfirm cost |
| slot conflict | 46123+active root | occupied slot | attach active |
| save manual | version response | child inserted | tree update |
| confirm | confirmation response | current pointer update | confirmed state |
| copy success | no HTTP write | clipboard resolve | “文案已复制” |
| copy failure | no HTTP write | clipboard reject | “复制失败，请手动选择文本” |
| version tree | detail nodes | parent fixtures | tree/drawer |
| delete confirm | delete response | remove script | Modal/cache |
| reference block | 46118 | authoritative `downstreamReferences.total>0` | three-category blocked explanation |

缺任一列即状态矩阵未完成；不能用一个快照测试替代交互/请求计数断言。

### 13.14 Accessibility 和视觉约束

- 所有 icon-only button 有 accessible name；radio group/tabpanel/tree/drawer 标题关系正确。
- Modal/Drawer 打开 focus 到标题/首控件，关闭回触发按钮；错误 summary 可被 screen reader 宣告。
- loading 不移除整个页面 landmark；Skeleton 有可见/隐藏合理 label。
- disabled 原因不能只靠颜色/tooltip，附近有文本/Alert；candidate A/B/C 不只用颜色区分。
- 键盘可完成候选、标题、编辑、优化、确认、库搜索/分页/详情/删除。
- 使用 Ant Design token/semantic DOM，不写依赖内部 class 的 brittle selector；不得通过 `!important` 修复状态。
- 长标题/正文、中文/英文/emoji、窄宽度和 200% zoom 不遮挡主动作；这些至少在组件测试或视觉 review 记录中覆盖。

---

## 14. 精确测试目录与 TDD 断言

方法名可以因项目命名规范做等价调整，但本节列出的行为一项不能少；master 指定的两个方法名必须原样保留。每个 RED 必须是测试已运行后的断言失败，不接受编译失败、selector 未命中或环境 skip。

### 14.1 `ScriptContractRegistryTest`

- `publishesExactlyThreeStableScriptServices`
- `publishesExactlyEightStableScriptDtos`
- `matchesEveryFrozenServiceMethodSignature`
- `matchesEveryFrozenDtoComponentInOrder`
- `keepsStableDtosUnderScriptDtoPackage`
- `keepsHttpBoAndVoOutOfCore`
- `keepsProviderTypesOutOfCore`
- `rejectsParallelBusinessLayerPackages`
- `scriptEntitiesDoNotExtendBaseEntity`
- `scriptEntitiesDeclareTenantOwnerAndCreatorFields`
- `scriptMappersDoNotExposeVersionOrConfirmationUpdateDelete`
- `scriptServicesUseInterfacePrefixAndImplSuffix`

RED：先写 reflection/package scan，确认因类不存在或签名不符产生 assertion failure。GREEN：仅用 Task 1 文件满足；禁止在 test 中用 optional/assumption 跳过不存在类。

### 14.2 `EffectiveCharacterCounterTest`

- `countsEveryCjkCodePointAsOne`
- `countsOneContinuousLatinTokenAsOne`
- `countsOneContinuousDigitTokenAsOne`
- `countsMixedLatinAndDigitsAsOneTokenPerBoundaryRule`
- `doesNotCountWhitespaceOrPunctuation`
- `handlesSupplementaryUnicodeWithoutSplittingSurrogates`
- `doesNotCountEmojiAsEffectiveCharacter`
- `normalizesUnicodeNfcBeforeCounting`
- `returnsZeroForBlankPunctuationOnlyInput`
- `estimatesDurationUsingCeiling`
- `acceptsExactLowerDurationBoundary`
- `acceptsExactUpperDurationBoundary`
- `rejectsOneSecondOutsideTolerance`
- `rejectsNonPositiveCharactersPerMinute`

边界 fixture 明确 target/cpm/tolerance 数值，expected 手算写死，不能复制 production 方法计算 expected。

### 14.3 `ScriptProviderResponseParserTest`

- `parsesStrictGenerationSchema`
- `parsesStrictOptimizationSchema`
- `rejectsMalformedUtf8`
- `rejectsPayloadAboveConfiguredByteLimit`
- `rejectsDuplicateObjectKeys`
- `rejectsUnknownTopLevelKey`
- `rejectsUnknownCandidateKey`
- `rejectsProviderSuppliedAngleSummaryAsUnknownProperty`
- `rejectsProviderSuppliedRecommendationReasonAsUnknownProperty`
- `rejectsUnknownFactReferenceKey`
- `rejectsTrailingJsonTokens`
- `rejectsNumericIds`
- `rejectsLeadingZeroStringIds`
- `rejectsNegativeStringIds`
- `rejectsLongOverflowIds`
- `rejectsMissingRequiredFieldInsteadOfDefaulting`
- `preservesScriptTextExactly`
- `doesNotLogRawPayloadOnFailure`

logging 测试捕获 appender 并断言 unique secret marker 不出现；只允许 schema/rule code/byte count。

### 14.4 `ScriptGenerationResultValidatorTest`

- `acceptsExactlyThreeDistinctValidCandidates`
- `rejectsCandidateCodesOtherThanExactlyABC`
- `rejectsCandidateWithoutExactlyThreeDistinctTitles`
- `rejectsCandidatesInWrongOrder`
- `derivesRecommendationReasonFromRouteRankAndKnowledgePlanOnly`
- `keepsRecommendationReasonByteIdenticalForSameRouteAndPlan`
- `doesNotUseProviderPayloadPromptOrCurrentDictionaryLabelForRecommendation`
- `rejectsDuplicateCandidateCode`
- `rejectsPlanCodeDifferentFromFrozenRoute`
- `rejectsAngleCodeDifferentFromFrozenRoute`
- `rejectsTemplateVersionDifferentFromFrozenRoute`
- `rejectsDifferentiatorDifferentFromFrozenRoute`
- `rejectsUnknownFactId`
- `rejectsFactWithDifferentDecisionRevisionOrHash`
- `rejectsTextFragmentNotContainedInScript`
- `rejectsFactualNumberWithoutFactReference`
- `rejectsTitleAboveOneHundredCodePoints`
- `rejectsScriptAboveTwentyThousandCodePoints`
- `rejectsDurationBelowFrozenTolerance`
- `rejectsDurationAboveFrozenTolerance`
- `rejectsDuplicatePlanIdentityAcrossCandidates`
- `rejectsCandidatesSharingFirstFortyEffectiveCharacters`
- `rejectsCandidatePairAtOrAbovePointEightFiveTrigramJaccard`
- `acceptsCandidatePairJustBelowSimilarityThreshold`
- `rejectsCameraInstructions`
- `rejectsUnsupportedPriceDataOrGuarantee`
- `rejectsConfiguredProhibitedContent`
- `mapsEveryDeterministicFailureToModelInvalidResponse`
- `doesNotIncludeBodyOrTitleInExceptionMessage`

事实数字测试同时写日期/普通序号/版本号允许例，避免把所有数字粗暴拒绝；规则边界由已冻结版本决定。

### 14.5 `ScriptOptimizationResultValidatorTest`

- `acceptsEachOfTheSixExactOptimizationTypes`
- `rejectsEveryLegacyOrUnknownOptimizationType`
- `requiresCustomInstructionOnlyForCustomType`
- `acceptsValidOptimizationForFrozenSource`
- `rejectsDifferentSourceVersionId`
- `rejectsMissingSourceVersionId`
- `rejectsAnythingOtherThanThreeDistinctTitles`
- `rejectsUnknownOrChangedFactReference`
- `rejectsFactFragmentOutsideOptimizedText`
- `rejectsDurationOutsideOptimizationPolicy`
- `rejectsProhibitedInstructionOrClaim`
- `inheritsCandidatePlanAndSnapshotFromSource`
- `doesNotPermitProviderToChangeRoute`
- `doesNotMutateSourceVersion`
- `mapsFailureToModelInvalidResponseWithoutSensitiveText`

### 14.6 `ScriptFrozenInputValidatorTest`

- `acceptsCanonicalFrozenInputAndHash`
- `rejectsMissingFrozenInput`
- `rejectsInputSnapshotHashMismatch`
- `rejectsRequestHashMismatch`
- `rejectsQuestionnaireHashMismatch`
- `rejectsKnowledgeContextHashMismatch`
- `rejectsNonCanonicalRevisionSnapshot`
- `rejectsP2ReferencesAgainstFinalHandoffOrderingRegistry`
- `rejectsPlanRefsNotExactlyABC`
- `rejectsFactRefsOutOfLongNumericOrder`
- `rejectsFactDecisionSetMismatch`
- `rejectsActorTypeOtherThanAppUser`
- `rejectsActorIdDifferentFromCreatedByUserId`
- `rejectsUnknownFrozenRuleVersion`
- `rejectsCorruptEncryptedCustomInstruction`
- `rejectsCurrentAliasOrBodyFieldsInFrozenJson`
- `mapsEveryFailureToFrozenInputInvalidMarker`

### 14.7 `ScriptVersionServiceImplTest`

- `createsImmutableManualChildWithoutUpdatingParent`
- `inheritsSnapshotTaskPlanContextAndDurationConfiguration`
- `recalculatesCharacterCountAndDurationForManualText`
- `returnsExistingManualVersionForSameIntentAndPayload`
- `rejectsSameManualIntentWithDifferentPayload`
- `rejectsSourceFromAnotherScript`
- `rejectsSourceFromAnotherOwner`
- `rejectsSourceFromAnotherTenant`
- `rejectsStaleBranchContextOrInputHash`
- `rejectsInvalidTitlesIndexAndText`
- `confirmsUsingTitleReadFromVersionOnly`
- `rejectsConfirmationDtoMixedInputOutputState`
- `insertsConfirmationAndUpdatesPointersAtomically`
- `rollsBackConfirmationWhenDraftRevisionUpdateLosesRace`
- `returnsExistingConfirmationForSamePayload`
- `rejectsSameConfirmationKeyWithDifferentPayload`
- `rollsBackWriteWhenAuditAppendFails`
- `neverCallsDefaultLoginHelper`

### 14.8 `UserScriptQueryServiceImplTest`

- `returnsOnlyCurrentActorVisibleScripts`
- `supportsPersonalAndWorkspaceOwnerScopes`
- `combinesIndustryPurposeSourceConfirmationAndUpdatedTimeRangeWithAnd`
- `filtersSourceTypeAgainstConfirmedOrLatestDisplayVersion`
- `filtersConfirmedAndUnconfirmedByCurrentConfirmationExistence`
- `usesFrozenIndustryAndPurposeInsteadOfCurrentDraftValues`
- `usesInclusiveStartAndExclusiveEndForUpdatedTimeRange`
- `rejectsPartialOrReversedUpdatedTimeRange`
- `returnsEmptyRowsInsteadOfNull`
- `capsPageSizeAtOneHundred`
- `usesStableIdTieBreakerForEverySort`
- `escapesKeywordWildcards`
- `returnsFlatImmutableVersionsInCreatedAtAndIdOrder`
- `returnsExactCurrentConfirmationProjection`
- `returnsExactDownstreamReferenceCountsTotalAndLatestGeneratedAt`
- `returnsNullCurrentConfirmationAndLatestGeneratedAtWhenAbsent`
- `rejectsReferenceTotalOrSummaryMismatch`
- `rejectsDuplicateVersionIds`
- `rejectsMissingParent`
- `rejectsCrossScriptParent`
- `rejectsVersionCycle`
- `keepsHistoricalKnowledgeSnapshotReference`
- `blocksDeleteWhenAuthoritativeDownstreamReferenceTotalIsPositive`
- `logicallyDeletesOnlyUserScript`
- `rollsBackDeleteWhenAuditAppendFails`
- `doesNotExposeDeletedScriptToDefaultQueries`

### 14.9 `ScriptFrozenInputAssemblerTest`

- `assemblesStableCanonicalJsonFromAllowedDtos`
- `usesFinalP2HandoffComponentRegistryNamesTypesOrderAndSemantics`
- `usesFinalP2HandoffOrderingWithoutInventingQuestionKeys`
- `sortsFactsByLongFactId`
- `keepsPlansExactlyABC`
- `matchesAcceptedFactsAndDecisionRevisionsExactly`
- `usesP0cRevisionSnapshotFieldsWithoutAliases`
- `hashesEverySemanticScalarAndJsonField`
- `doesNotHashCipherNonceOrCreatedAt`
- `hashesPlaintextCustomInstructionDigest`
- `doesNotPersistQuestionFactOrKnowledgeBodies`
- `doesNotPersistPromptSourceTextOrTitles`
- `rejectsContextThatViolatesFinalP2HandoffReadinessSemantics`
- `rejectsRevisionOrGenerationHashMismatch`
- `rejectsDuplicateOrIncompleteUpstreamDtos`

F2 此测试只能向 assembler 传五个 P1 DTO fixtures；不得构造或 mock P1 Service。

### 14.10 `ScriptGenerationServiceImplTest`

- `generationCallsExactLockedP2ContextMethodFirstInOuterWriteTransaction`
- `optimizationCallsExactLockedP2ContextMethodFirstInOuterWriteTransaction`
- `rechecksBranchQuestionnaireAndKnowledgeHashesWhileDraftAndCurrentBranchAreLocked`
- `rejectsHashChangeBeforeSlotQuotaOrTaskCreation`
- `usesGlobalLockOrderBranchSlotQuotaTaskGroupMember`
- `rejectsMixedP2Snapshots`
- `rejectsAcceptedFactDecisionSetMismatch`
- `routesAndSnapshotsOnlyInitialGeneration`
- `createsChargeableTaskThenFreezesThenEnqueues`
- `rollsBackTaskLockFreezeAndOutboxWhenSnapshotFails`
- `rollsBackTaskLockFreezeAndOutboxWhenEnqueueFails`
- `returnsReusedTaskWithoutRefreezingOrReenqueueing`
- `rejectsReusedTaskWithDifferentFrozenHash`
- `usesTypedAppUserInitiator`
- `usesExactGenerationSlotFamilyAndGroup`
- `usesExactOptimizationSlotFamilyAndGroup`
- `allowsP0cToBlockContextWritesForBothActiveTaskTypes`
- `optimizationInheritsHistoricalSnapshotWithoutRerouting`
- `rejectsOptimizationSourceFromAnotherOwner`
- `appendsRequestedAuditInsideTheOuterTransaction`
- `rollsBackEverythingWhenRequestedAuditFails`
- `doesNotInjectBillingOrProvider`

### 14.11 `ScriptGeneratedVersionWriterTest`

- `writesExactlyABCInFixedOrder`
- `persistsOnlyServerDerivedRecommendationReasonForGeneratedVersions`
- `rebuildsInheritedOptimizationRecommendationWithSameFormatterVersion`
- `writesNoVersionWhenAnyCandidateFailsValidation`
- `usesCandidateAAsTaskResultReference`
- `marksSuccessInSameTransactionAsThreeVersions`
- `rollsBackThreeVersionsWhenMarkSuccessLosesLease`
- `settlesOnlyThroughP0cTerminalTransition`
- `writesOneOptimizedChildAndKeepsSourceImmutable`
- `rejectsStaleBranchRevision`
- `rejectsStaleQuestionnaireHash`
- `rejectsStaleKnowledgeContextHash`
- `rejectsStaleGenerationContextRevision`
- `rejectsStaleGenerationInputHash`
- `doesNotCompareUnrelatedDraftRevisionForAsyncStale`
- `appendsOneRedactedVersionAuditInsideResultTransaction`
- `rollsBackVersionsTerminalAndSettlementWhenAuditFails`
- `neverWritesProviderPayloadToLogs`

### 14.12 `ScriptGenerationTaskHandlerTest`

- `implementsOnlyTheSharedExecutionHandlerContract`
- `rejectsMissingFrozenInputBeforeStartingAttempt`
- `rejectsCorruptFrozenInputBeforeStartingAttempt`
- `renewsLeaseWhenProviderDeadlineMarginIsBelowTenSeconds`
- `usesRenewedLeaseForAttemptAndTerminalWrites`
- `startsAttemptImmediatelyBeforeProviderCall`
- `callsProviderOutsideDatabaseTransaction`
- `completesInitialAttemptWithRealUsage`
- `repairsOneStructurallyInvalidInitialResponse`
- `failsAfterInvalidRepairWithoutWritingVersions`
- `countsRecoveryCallsInSameGlobalAttemptSequence`
- `rejectsFourthProviderCallBeforeEnteringProvider`
- `recordsUnknownUsageAsNullNotZero`
- `recordsProviderFailureUsageWhenAvailable`
- `doesNotRetryProviderAfterStaleResult`
- `doesNotWriteMarkSuccessOrSettleStaleResult`
- `doesNotInjectOrCallQuotaBillingService`
- `doesNotLogPromptOutputBodyTitleOrCustomInstruction`

### 14.13 `ScriptOptimizationTaskHandlerTest`

- `loadsSourceBodyAndTitlesOnlyIntoRuntimeMemory`
- `doesNotPersistRuntimeSourceMaterialIntoFrozenInput`
- `inheritsSourceSnapshotAndPlanWithoutReroute`
- `usesInitialRepairRecoveryAttemptLimitSharedWithRoot`
- `renewsLeaseBeforeProviderWhenRequired`
- `writesOneOptimizedChildOnValidResponse`
- `doesNotModifySourceVersion`
- `handlesInvalidFrozenInputBeforeAttempt`
- `handlesStaleResultWithoutWriteSettleOrRetry`
- `recordsRealUsageForSuccessFailureAndStale`
- `doesNotInjectBillingService`

### 14.14 `UserScriptControllerTest`

- `exposesExactlySevenScriptRoutes`
- `requiresExpectedPermissionForEveryRoute`
- `addsRepeatSubmitToEveryWriteRoute`
- `doesNotUseDefaultLogAnnotation`
- `rejectsSystemTokenAtCreatorBoundary`
- `usesAppActorContextForEveryWrite`
- `rejectsNumericOrInvalidDecimalIds`
- `mapsLongIdsToDecimalStrings`
- `mapsNullRowsToEmptyRows`
- `rejectsPageSizeAboveOneHundred`
- `rejectsUnknownSortFieldOrOrder`
- `bindsFiveExactLibraryFilterCategories`
- `rejectsUnknownSourceOrConfirmationFilter`
- `rejectsPartialOrReversedUpdatedTimeRangeWithoutCallingService`
- `returnsFlatVersionsCurrentConfirmationAndDownstreamReferencesExactShape`
- `neverReturnsVersionChildrenOrLegacyDetailReferenceCount`
- `doesNotCallServiceWhenValidationFails`
- `doesNotWriteAuditFromControllerOutsideTransaction`
- `doesNotIncludeSelectedTitleInConfirmationRequest`
- `returnsSharedErrorEnvelopeWithoutMessageBranching`

同事务 audit rollback 在 Service/IT 验证；Controller test 证明它没有异步审计回调或第二条审计路径。

### 14.15 `ScriptSchemaMigrationIT`

- `requiresSafeLocalIntegrationEnvironmentBeforeDataSourceUse`
- `appliesMigrationsOneThroughFourFourAFiveSixSevenOnCleanSchema`
- `verifiesP0cAddendum04aExactDdlBeforeP3Migration`
- `replaysP3MigrationWithoutSchemaDrift`
- `createsExactlyFourP3Tables`
- `matchesEveryColumnTypeNullDefaultAndComment`
- `matchesEveryPrimaryUniqueForeignIndexAndCheckConstraint`
- `allowsSameDraftAndRootAcrossTenants`
- `rejectsDuplicateDraftAndRootWithinTenant`
- `enforcesVersionScriptAndParentForeignKeys`
- `enforcesConfirmationScriptAndVersionForeignKeys`
- `rejectsInvalidOwnerActorSourceCandidateAndTitleIndexChecks`
- `keepsOnlyUserScriptLogicallyDeletable`
- `doesNotMutateExistingRowsOnReplay`
- `doesNotUseContainerVirtualizationOrUnsafeSchema`

### 14.16 `ScriptVersionPersistenceIT`

- `persistsManualChildAndConfirmationHistoryWithRealMappers`
- `keepsParentAndPriorConfirmationsImmutable`
- `enforcesTenantOwnerAndCreatorOnEveryRead`
- `isolatesPersonalOwners`
- `isolatesWorkspaceOwners`
- `rejectsCrossOwnerParentEvenWhenForeignKeyExists`
- `preventsCycleAndBrokenTree`
- `serializesConcurrentManualIntentToOneVersion`
- `rejectsConcurrentDifferentPayloadForSameIntent`
- `serializesConfirmationRevisionUpdate`
- `rollsBackConfirmationAndPointersOnAuditFailure`
- `blocksReferencedDeleteAndAllowsUnreferencedLogicalDelete`

### 14.17 `ScriptFrozenInputPersistenceIT`

- `persistsAndReadsFrozenInputByTenantOwnerAndRoot`
- `returnsSameRowForSameRootRequestAndSnapshotHash`
- `rejectsSameRootWithDifferentRequestHash`
- `rejectsSameRootWithDifferentInputSnapshotHash`
- `recoversExactFrozenInputAfterApplicationRestart`
- `detectsTamperedCanonicalJsonOrCiphertext`
- `neverUpdatesOrDeletesFrozenInput`
- `doesNotJoinCurrentQuestionnaireEvidenceOrKnowledgeTables`
- `isolatesSameRootNumberAcrossTenants`
- `usesTypedAppUserActorAndCreator`
- `rollsBackFreezeWhenEnqueueOrAuditFails`
- `p2WriteWaitsForGenerationLockThenGuardRejectsAfterActiveGroupCommit`
- `p2WriteFirstChangesQuestionnaireHashThenGenerationFailsBeforeSlotQuotaTask`
- `p2WriteFirstChangesKnowledgeContextHashThenOptimizationFailsBeforeSlotQuotaTask`
- `recordsExactDraftCurrentBranchOperationSlotQuotaAccountTaskOrGroupMemberLockTrace`

### 14.18 `ScriptGenerationBillingIT`

- `requiresSafeMysqlAndRedisBeforeStartingScanner`
- `usesRealScannerRegistryLeaseAttemptTaskAndBillingServices`
- `generatesABCWithOneProviderCallAndOneSettlement`
- `writesThreeVersionsAndTerminalStateAtomically`
- `repairsInvalidInitialResponseAndStillSettlesOnce`
- `failsInvalidRepairWithNoVersionsAndOneRelease`
- `failsMissingFrozenInputBeforeProviderAndReleasesOnce`
- `failsCorruptFrozenInputBeforeProviderAndReleasesOnce`
- `recoversExpiredLeaseWithoutDuplicateBillingOrVersions`
- `blocksFourthProviderAttemptBeforeProviderCall`
- `renewsNearExpiryLeaseAndUsesNewLeaseSnapshot`
- `recordsActualCostCurrencyTokensAndProviderRequestId`
- `preservesNullUnknownUsageInsteadOfZero`
- `handlesStaleAfterProviderAsSuccessfulAttemptButFailedTask`
- `writesNoVersionSettleOrProviderRetryForStaleResult`
- `rollsBackVersionsTerminalSettlementWhenAuditAppendFails`
- `isolatesTenantOwnerTaskAndRedisRunPrefix`
- `neverUsesFlushAllFlushDbContainerOrProductionResources`

真实 IT 的可编程 provider 只替代外部网络，不替代 P0-C scanner/registry/lease/attempt/task/billing 或 P1/P2真实 Service。F3 前的 DTO-only unit fixture 不进入此类。

### 14.19 `ScriptUserApiIT`（shared owner window）

- `startsUserApiWithRealScriptBeansAndSecurityBoundary`
- `rejectsSystemTokenAndAcceptsAppToken`
- `enforcesAllSevenPermissions`
- `rejectsCrossWorkspaceAndCrossOwnerObjects`
- `createsGenerationTaskWithTypedInitiatorAndSameTransactionAudit`
- `rollsBackGenerationTaskFreezeLockAndAuditTogetherOnAuditFailure`
- `createsManualVersionAndAuditAtomically`
- `createsOptimizationTaskWithWritableContextFamilyGroup`
- `confirmsVersionTitleIndexAndAuditAtomically`
- `listsVisibleScriptsWithStablePaginationAndSort`
- `combinesAllFiveLibraryFilterCategoriesAcrossHttpBoServiceAndMapper`
- `rejectsPartialUpdatedTimeRangeAndUnknownFilterEnums`
- `returnsEmptyRowsForNoResults`
- `returnsFlatImmutableVersionsCurrentConfirmationAndDownstreamReferences`
- `keepsMockAndRealDetailShapeByteCompatibleAtContractBoundary`
- `rejectsBrokenOrCyclicVersionTree`
- `blocksReferencedDelete`
- `logicallyDeletesUnreferencedScriptAndAuditsAtomically`
- `returnsExactSharedErrorCodesForConflictsAndStaleContext`
- `usesNoUpstreamFakeMapperEntityTableOrUserVo`

### 14.20 Task 7 Vitest files

`src/services/ai-video/scripts/contract.test.ts`：

- 七 URL 只在 `api.ts`；types、query keys、runtime adapter 分属冻结文件；旧 services/types/adapters 路径零命中。
- generation/optimization 请求 exact 包含 questionnaireHash/knowledgeContextHash；六个优化 type exact，未知/旧 alias fail-closed；所有 ID 只能 decimal string。
- list 五类 filters exact serialize/canonicalize，半边/倒序时间拒绝；`rows:null -> []`。
- detail 只接受 flat versions + exact currentConfirmation/downstreamReferences；拒绝 children、未知字段、计数和式/summary 不一致、confirmation 指向/标题不一致。
- Mock 与真实 response 都经同一 adapter fixtures；provider `angleSummary` 不属于原始 provider schema，最终 ScriptVersion 的服务端派生值必须非空。

`useScriptFlow.test.tsx`：

- one intent creates one idempotency key；explicit new intent creates another。
- network/timeout/5xx user retry reuses key；46123 attaches active root。
- polling 1s→2s，focus/online immediate once，terminal/unmount/branch switch cleanup。
- 401 logout once、403 no retry、cancel neutral、expired new intent、46116 conflict、46125 refresh。
- quota low/rate changed/submit guard/repair phase/no candidates visible。

`ScriptCandidateTabs.test.tsx`：

- A/B/C fixed order；exact three title radio each；why recommended/metrics/template labels。
- keyboard selection/action；missing/duplicate candidates reject；long text safe rendering。
- `navigator.clipboard.writeText` resolve 精确显示“文案已复制”，reject/throw 精确显示“复制失败，请手动选择文本”；permission/quota/slot disabled reason visible；no dangerouslySetInnerHTML。

`ScriptVersionEditor.test.tsx`：

- source immutable；dirty leave confirm；three distinct titles/index/text validation。
- save same intent retry；failure preserves input；success focuses child/version tree。
- confirmation request sends index not title；shows exact version/context summary；conflict refresh/cancel；stale returns Demand/Script flow；double click one request。

`ScriptOptimizationActions.test.tsx`：

- options exact only `shorter/more_colloquial/strengthen_selling_points/change_hook/strengthen_call_to_action/custom`，逐项可提交且旧 alias/unknown 不渲染、不发送。
- custom only requires 1..2000 code points；其他五项强制 null；permission/quota/slot disabled reason visible。
- fee confirmation、rate changed reconfirm、same-intent retry 保持 key；复制优化结果 resolve/reject 使用相同精确反馈。

`ScriptStep.test.tsx`：

- complete idle/cost/submitting/queued/running/success/editing/optimizing/confirming/confirmed/error matrix。
- auth/loading/initial/no candidate/network/timeout/5xx/401/403/cancel/expired states have visible assertions。

### 14.21 Task 8 Vitest files

`UserScriptLibrary.test.tsx`：loading、initial empty、search empty、pagination、three sorts、network/timeout/5xx retry、401 once、403、null rows、delete page correction；industryCode/purposeCode/sourceType/confirmationStatus/updated-time range 五类控件 exact query，五类全选 AND、逐项清除、reset、半边时间拒绝、过滤变化回第一页和 stable query key。

`ScriptDetailDrawer.test.tsx`：只接收 flat immutable versions 并在前端构 generated/manual/optimized tree；拒绝 response children、duplicate/missing/cross-script/cycle；`currentConfirmation={confirmationId,versionId,selectedTitleIndex,selectedTitle,confirmedAt}` exact 高亮/标题；`downstreamReferences={voiceCount,digitalHumanCount,workCount,total,latestGeneratedAt}` exact 展示、和式/summary 一致性、null latest、引用阻塞；snapshot/task/context refs、history unavailable、keyboard/focus；复制 resolve/reject 精确反馈。

`ScriptStep.test.tsx`（Task 8 复跑同一 master-owned file）：LibraryView tab/router state、deep link、workspace switch、list/detail cache scope、back navigation；Voice/Base confirmed reference only、enter/submit revalidate、deleted/unauthorized/unconfirmed 返回 ScriptStep、selected index exact、access loss fail-closed、no array-position identity；只允许 `{scriptId,versionId,selectedTitleIndex}` 跨步骤，serialize/restore ID strings、workspace logout clear，legacy body/title copied fields 被拒绝。不得为这些断言创建 owner registry 外的独立 view/step/model test files。

### 14.22 Task 9 Vitest `index.test.tsx`

- routes ScriptStep and LibraryView without duplicate server state。
- recovers auth once and keeps intended deep link。
- clears flow/task on workspace/draft/branch change without deleting history cache from other scope。
- blocks downstream when reference revalidation fails。
- presents global loading/401/403/404/network/5xx boundaries。
- does not expose P3 route when app security runtime is disabled。
- uses shared layout/topbar semantics and keyboard landmarks。

### 14.23 Fresh evidence assertions common to every test

- selector string is exact FQCN/file path and appears in manifest。
- report file deleted before start；mtime >= start-2 seconds and <= acceptance window end。
- JUnit suite `tests>0`、failures=0、errors=0、skipped=0；Vitest total>0、failed=0、pending=0。
- report suite/file set has no extra stale suites/files；same class name from another module cannot satisfy。
- every touched JUnit class has class-level `@Tag("dev")`；every IT command has local profile。
- RED evidence records tests>0 plus failure/error>0；GREEN must be later than RED and on intended implementation state。
- compilation error、no tests、skip、assumption、disabled test、zero-byte report、copied report、mtime touch 都不能当 evidence。
- manifest hash/bytes/mtime recomputed at review time；HEAD or artifact change invalidates prior review。

---

## 15. Rebase、证据 manifest、独立 review 与 F4 冻结细则

### 15.1 F1/F2/F3 rebase 前置安全条件

每次 rebase 前全部成立：

- 当前分支匹配 `codex/*` 且不是 main。
- `git rev-parse --git-dir` 与 `--git-common-dir` 不同，证明 linked worktree。
- `git status --porcelain=v1 -uall` 为空。
- 不存在 rebase/merge/cherry-pick marker。
- handoff 文件来自 `git rev-parse --git-path`，不是仓库中可伪造 tracked 文件。
- handoff exact field/type/order、reviewStatus、owner/reviewer、reviewed HEAD、evidence hash 全部通过。
- target SHA 是小写 40 位 commit，且符合 handoff ancestry。
- before HEAD 和 before merge-base 已用 `FileMode.CreateNew` 记录 pending；不能 rebase 后补写“之前”数据。

每次运行先由操作者设置 `AI_VIDEO_P3_REBASE_PHASE=F1|F2|F3` 和新的小写 UUID
`AI_VIDEO_P3_REBASE_ATTEMPT_ID`。target 不允许手填：脚本按 phase 从已经通过上一条 strict gate 的
Git metadata handoff/addendum 实时读取。pending 路径固定为
`git-metadata:p3-rebase-attempts/<phase-lower>/<beforeHead>-to-<targetHead>/<attemptId>.pending.json`，
字段及顺序精确为 `phase/attemptId/bindingPath/beforeHead/targetHead/beforeMergeBase/startedAtUtc/status`，
`status='PENDING'`。同 attempt exact 回读；abort 或其他重试必须使用新 UUID，旧 pending 永不覆盖。
真实命令只允许：

```powershell
$ErrorActionPreference='Stop'
$rootText=(& git rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw '无法解析仓库根'}
$repoRoot=[IO.Path]::GetFullPath($rootText.Trim())
Set-Location -LiteralPath $repoRoot
$phase=[string]$env:AI_VIDEO_P3_REBASE_PHASE
$attemptId=[string]$env:AI_VIDEO_P3_REBASE_ATTEMPT_ID
if($phase -cnotin @('F1','F2','F3')){throw 'AI_VIDEO_P3_REBASE_PHASE 必须是 F1/F2/F3'}
if($attemptId -cnotmatch '^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$'){throw 'AI_VIDEO_P3_REBASE_ATTEMPT_ID 必须是新的小写 UUID v4'}
$bindingSpec=switch($phase){
  'F1'{@('p0c-f1-contract-addendum.json','amendmentHead')}
  'F2'{@('p1-f2-handoff.json','f2Head')}
  'F3'{@('p2-f3-handoff.json','f3Head')}
}
$bindingRaw=(& git rev-parse --git-path $bindingSpec[0]).Trim()
$bindingFile=if([IO.Path]::IsPathRooted($bindingRaw)){[IO.Path]::GetFullPath($bindingRaw)}else{[IO.Path]::GetFullPath((Join-Path $repoRoot $bindingRaw))}
if(-not (Test-Path -LiteralPath $bindingFile -PathType Leaf)){throw "缺 rebase binding：$($bindingSpec[0])"}
$binding=Get-Content -LiteralPath $bindingFile -Raw -Encoding UTF8 | ConvertFrom-Json
$targetHead=[string]$binding.($bindingSpec[1])
if($targetHead -cnotmatch '^[0-9a-f]{40}$'){throw 'binding targetHead 非小写 40 SHA'}
& git cat-file -e "$targetHead^{commit}"
if($LASTEXITCODE -ne 0){throw 'binding targetHead 不是本地 commit'}
$branch=(& git branch --show-current).Trim()
if($branch -cnotlike 'codex/*' -or $branch -ceq 'main'){throw 'rebase 必须位于 codex/* linked worktree'}
$gitDir=[IO.Path]::GetFullPath((& git rev-parse --git-dir).Trim())
$commonDir=[IO.Path]::GetFullPath((& git rev-parse --git-common-dir).Trim())
if($gitDir -ceq $commonDir){throw 'rebase 不得在主工作树'}
if(@(& git status --porcelain=v1 -uall).Count -ne 0){throw 'rebase 前 worktree 必须 clean'}
foreach($marker in @('rebase-merge','rebase-apply','MERGE_HEAD','CHERRY_PICK_HEAD')){
  $markerPath=(& git rev-parse --git-path $marker).Trim()
  if(Test-Path -LiteralPath $markerPath){throw "存在未完成 Git 操作：$marker"}
}
$beforeHead=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
$beforeMergeBase=(& git merge-base $beforeHead $targetHead).Trim().ToLowerInvariant()
if($LASTEXITCODE -ne 0 -or $beforeMergeBase -cnotmatch '^[0-9a-f]{40}$'){throw '无法记录 rebase 前 merge-base'}
$pendingRelative="p3-rebase-attempts/$($phase.ToLowerInvariant())/$beforeHead-to-$targetHead/$attemptId.pending.json"
$pendingRaw=(& git rev-parse --git-path $pendingRelative).Trim()
$pendingPath=if([IO.Path]::IsPathRooted($pendingRaw)){[IO.Path]::GetFullPath($pendingRaw)}else{[IO.Path]::GetFullPath((Join-Path $repoRoot $pendingRaw))}
$pendingFields=@('phase','attemptId','bindingPath','beforeHead','targetHead','beforeMergeBase','startedAtUtc','status')
if(Test-Path -LiteralPath $pendingPath -PathType Leaf){
  $pending=Get-Content -LiteralPath $pendingPath -Raw -Encoding UTF8 | ConvertFrom-Json
  if((@($pending.PSObject.Properties.Name)-join '|') -cne ($pendingFields-join '|')){throw 'pending 字段/顺序漂移'}
  if($pending.phase -cne $phase -or $pending.attemptId -cne $attemptId -or $pending.bindingPath -cne ('git-metadata:'+$bindingSpec[0]) -or $pending.beforeHead -cne $beforeHead -or $pending.targetHead -cne $targetHead -or $pending.beforeMergeBase -cne $beforeMergeBase -or $pending.status -cne 'PENDING'){throw 'pending payload 漂移'}
  $started=[DateTimeOffset]::Parse([string]$pending.startedAtUtc)
  if($started.Offset -ne [TimeSpan]::Zero){throw 'pending startedAtUtc 非 UTC'}
}else{
  $pending=[ordered]@{phase=$phase;attemptId=$attemptId;bindingPath=('git-metadata:'+$bindingSpec[0]);beforeHead=$beforeHead;targetHead=$targetHead;beforeMergeBase=$beforeMergeBase;startedAtUtc=[DateTimeOffset]::UtcNow.ToString('o');status='PENDING'}
  $directory=Split-Path -Parent $pendingPath
  if(-not (Test-Path -LiteralPath $directory -PathType Container)){[void](New-Item -ItemType Directory -Path $directory)}
  $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($pending | ConvertTo-Json -Compress))
  $stream=[IO.File]::Open($pendingPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
}
& git rebase $targetHead
if($LASTEXITCODE -ne 0){
  throw 'rebase 冲突：逐文件核验后 git add + git rebase --continue；放弃只能 git rebase --abort 并核对 beforeHead'
}
$afterHead=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
$afterMergeBase=(& git merge-base $afterHead $targetHead).Trim().ToLowerInvariant()
if($afterMergeBase -cne $targetHead){throw 'rebase 后 merge-base 不等目标 HEAD'}
& git merge-base --is-ancestor $targetHead $afterHead
if($LASTEXITCODE -ne 0){throw 'rebase 后目标不是祖先'}
```

冲突处理不得机械选 ours/theirs；涉及 DTO/signature/migration/shared docs 时按 `receiving-code-review` 重读双方权威并让契约 owner 确认。rebase 成功后必须重新编译/运行当前切片 tests，旧 evidence 全部作废。

### 15.2 `p3-f1-rebase.json` 与 `p3-f2-rebase.json`

读取 P1 F2 handoff 时必须启用 duplicate-key、unknown-field、missing-field 和 trailing-token 拒绝；顶层字段名称、数量和顺序精确为 23 项：

```text
fullF2Ready
f1Head
originalF1Head
f1AmendmentHead
f2Head
owner
reviewer
reviewStatus
reviewCompletedAtUtc
p1AcceptanceWindowStart
p1AcceptanceWindowEnd
originalF1HandoffSha256
f1AddendumSha256
migrationChain
migrationRepeat05
stableServices
stableDtos
stableDtoComponentRegistry
stableDtoSourceSha256
downstreamConsumers
revisionMappingContractOwner
evidence
capturedAtUtc
```

先逐项证明 handoff 与 live 一致，才允许生成任何 P3 digest：`fullF2Ready/migrationRepeat05` 均为 JSON true；`f1Head == f1AmendmentHead`，`originalF1Head` 是其祖先，三者分别等于 live F1/addendum header；`f2Head` 等于 reviewed target；owner/reviewer trim 后大小写不敏感不同，review 为 PASS，review/acceptance/captured 时间是有序 UTC；两个 SHA 分别等于 live original F1 handoff 与 addendum bytes；migrationChain exact 为 `['01','02','03','04','04a','05']`；stableServices exact 为 `['IKnowledgeRoutingService','IKnowledgeSnapshotService']`；stableDtos exact 为 `['KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO']`；downstreamConsumers exact 为 `['P2','P3']`；revisionMappingContractOwner 与 reviewed P1 当前值一致且非空。`stableDtoComponentRegistry` 的 key 名称/顺序必须与 stableDtos 相同，每个值是从 reviewed source 的 Java record header 解析出的 exact ordered `Type name` 字符串数组；`stableDtoSourceSha256` 同 key/顺序且每个值等于 live source bytes 的小写 SHA-256。`evidence` 是 exact ordered object `unit/it/migration/vitest/standards/scan`，每个值 exact `{path,sha256}`，path 为 `p1-evidence/<f2Head>/<kind>.manifest.json` 且 SHA 与 live regular file 相等。只有上述 handoff==live 后，才按 stableDtos 顺序对实际 record component registry 与 source-SHA registry canonical compact JSON UTF-8 计算 P3 binding digest。旧 schema、缺失/额外/别名字段、F1/addendum binding 漂移、component/header/type/order 漂移、source hash 漂移和 stale evidence 均必须在 rebase 前失败。

P3 在开始 F1 代码前必须真实 rebase reviewed P0-C amendment HEAD，并以 CreateNew 写 `p3-f1-rebase.json`，字段顺序固定：

```text
phase
attemptId
pendingPath
beforeHead
targetHead
originalF1Head
amendmentHead
beforeMergeBase
afterHead
afterMergeBase
addendumPath
addendumSha256
originalF1HandoffPath
originalF1HandoffSha256
requiredMethodsSha256
schemaAddendumSha256
sourceSignaturesEvidenceSha256
migration04aEvidenceSha256
independentReviewEvidenceSha256
completedAtUtc
```

`phase='F1'`，`attemptId/pendingPath` 精确绑定本次不可变 pending，`targetHead=amendmentHead`，afterMergeBase=targetHead；`addendumPath='git-metadata:p0c-f1-contract-addendum.json'`，`originalF1HandoffPath='git-metadata:p0c-f1-handoff.json'`。所有 digest 必须从刚通过 Section 2.2 exact gate 的 live 文件/canonical compact JSON 现场计算；三个 evidence SHA 分别绑定固定 source-signatures、migration-04a、independent-review path。任何旧七字段 addendum、HEAD/hash/证据漂移都阻止 F1，不能只把 addendum 内容抄进 record。

F2 rebase 后写 `p3-f2-rebase.json`，字段顺序固定：

```text
phase
attemptId
pendingPath
beforeHead
targetHead
beforeMergeBase
afterHead
afterMergeBase
handoffPath
handoffSha256
f1AddendumSha256
f1AmendmentHead
stableDtos
p1DtoComponentRegistrySha256
p1DtoSourceRegistrySha256
serviceImportsAllowed
completedAtUtc
```

值固定：`phase='F2'`；`attemptId/pendingPath` 精确绑定本次不可变 pending；stableDtos 是 P1 五 DTO exact array；`serviceImportsAllowed=false` JSON boolean。handoffPath=`p1-f2-handoff.json`，`f1AddendumSha256/f1AmendmentHead` 必须与已验证 F1 record/live addendum 相等，afterMergeBase=targetHead。`p1DtoComponentRegistrySha256` 是五 DTO 按 stableDtos 顺序生成的 record component 名称/Java 类型/顺序 canonical registry digest；`p1DtoSourceRegistrySha256` 是 `{dtoName:liveSourceSha256}` ordered registry digest。两者都必须在 rebase 后从实际源码重算，并与 F2 tests 使用的 registry 相等；不能只记录 handoff 的 DTO 名称。记录创建后 F2 production import scan 证明 P1 Service 0 次，P1 DTO 恰为允许集合。

### 15.3 `p3-f3-rebase.json`

读取 P2 F3 handoff 时必须启用 duplicate-key、unknown-field、missing-field 和 trailing-token 拒绝；顶层字段名称、数量和顺序精确为 31 项：

```text
fullF3Ready
f1Head
f1AmendmentHead
f2Head
f3Head
owner
reviewer
reviewStatus
reviewCompletedAtUtc
p2AcceptanceWindowStart
p2AcceptanceWindowEnd
f1HandoffSha256
f1AddendumSha256
f2HandoffSha256
migrationChain
migrationRepeat06
stableServices
stableDtos
serviceSignatures
serviceSourceSha256
dtoComponentRegistry
dtoSourceSha256
lockedCurrentBranchProtocol
p2WriteGuardProtocol
contextSemantics
downstreamConsumers
testRegistry
p3ConsumerContract
revisionMappingContractOwner
evidence
capturedAtUtc
```

其中 `p3ConsumerContract` 的字段名称、数量和顺序精确为 12 项：

```text
requiredBaseHead
removeFakesFrom
stableServices
stableDtos
serviceSignatures
serviceSourceSha256
dtoComponentRegistry
dtoSourceSha256
lockedCurrentBranchProtocol
p2WriteGuardProtocol
contextSemantics
forbiddenDependencies
```

第一层 gate 先对 top-level 与 nested 的七个对象 `serviceSignatures/serviceSourceSha256/dtoComponentRegistry/dtoSourceSha256/lockedCurrentBranchProtocol/p2WriteGuardProtocol/contextSemantics` 分别做 canonical compact JSON UTF-8 byte-equality；同时要求 nested `requiredBaseHead == f3Head`、`removeFakesFrom == ['production','all-real-it']`、stableServices/stableDtos 与顶层 exact 相等、forbiddenDependencies exact 为 `['questionnaire-mapper','questionnaire-table','questionnaire-entity','user-vo']`。任一处不相等时不得进入源码检查或生成 digest。

第二层才验证 handoff 与 live：ready/repeat06 为 JSON true；heads、三份 upstream handoff/addendum SHA、review/UTC/ancestry/evidence 均为 live；migrationChain exact 为 `['01','02','03','04','04a','05','06']`；stableServices exact 为 `['IQuestionnaireContextService','IEvidenceReviewService']`；stableDtos exact 为 `['QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO','QuestionnaireSupplementRevisionDTO','EvidenceReviewContextDTO','AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO']`；downstreamConsumers exact 为 `['P3']`；testRegistry exact 为 `{javaSelectors:20,surefireSelectors:14,failsafeSelectors:6,localIntegrationProfiles:6,vitestFiles:11,vitestReportsMinimum:5}`；evidence exact ordered `unit/it/migration/vitest/standards/scan`，每项 exact `{path,sha256}` 且 path 为 `p2-evidence/<f3Head>/<kind>.manifest.json`。逐 Service 现场重算完整反射签名和 source SHA；逐 DTO 现场重算 source SHA、record header、ordered `Type name` components 和 componentSha256。`lockedCurrentBranchProtocol` exact 9 字段为 `method/propagation/readOnly/scope/lockOrder/currentBranchCheck/snapshot/p3RecheckFields/nextLockOrder`；`p2WriteGuardProtocol` exact 7 字段为 `method/propagation/lockOrder/guardedWrites/businessCode/errorDataFields/zeroSideEffects`；`contextSemantics` exact 11 字段为 `answerHashInput/answerIdentityKeys/answerContextRole/answerOrder/supplementIdentity/factIdentity/factOrder/decisionRevisionMeaning/acceptedFactDecisionSets/listMutability/hashEncoding`，值必须与 reviewed source、反射、records 和现行协议一致。拒绝任何 alias（特别是旧 identity/order 名称）、缺失/额外顶层或 nested 字段、七对象 nested drift、旧 readOnly/锁方法/锁序/recheck 协议，以及旧 write-guard 协议。

字段顺序固定：

```text
phase
attemptId
pendingPath
beforeHead
targetHead
beforeMergeBase
afterHead
afterMergeBase
p1HandoffSha256
p2HandoffPath
p2HandoffSha256
f1AddendumSha256
f1AmendmentHead
p1DtoComponentRegistrySha256
p1DtoSourceRegistrySha256
stableServices
stableDtos
p2ServiceSignaturesSha256
p2ServiceSourceRegistrySha256
p2DtoComponentRegistrySha256
p2DtoSourceRegistrySha256
p2LockedCurrentBranchProtocolSha256
p2WriteGuardProtocolSha256
p2ContextSemanticsSha256
removeFakesFrom
sameReadSnapshot
completedAtUtc
```

值固定：`phase='F3'`；`attemptId/pendingPath` 精确绑定本次不可变 pending；stableServices 按 P1 两项、P2 两项的冻结顺序；stableDtos 按 P1 五项、P2 六项顺序；`removeFakesFrom=['production','all-real-it']`；`sameReadSnapshot=true`。handoff target 精确 P2 `f3Head`，且 P1 target 仍是 ancestor。F1/P1 四个 binding 字段必须与既有 F1/F2 record 及 live 文件逐项相等。

七个 P2 digest 不能由字符串关键词拼装：只有前述 top-level↔nested equality 与 handoff==live 两层 gate 全部通过后，才分别对 P2 handoff `p3ConsumerContract` 中 final service signatures、service source SHA registry、六 DTO component registry、DTO source SHA registry、locked-current-branch protocol、write-guard protocol、`contextSemantics` 的 canonical compact JSON UTF-8 计算。锁 digest 必须覆盖 exact `lockCurrentContextForGeneration(Long,Long)`、MANDATORY/non-readOnly、draft/current branch scope/order/hash recheck；write digest 必须覆盖 `requireGenerationContextWritable`；`p2ContextSemanticsSha256` 只取 P2 F3 final `contextSemantics`，P3 不补字段。这样 F3 record 实际绑定 component/source/signature/lock/write/context semantics，而不是只保存 `stableDtos` 名称。

### 15.4 Rebase 记录幂等

pending 和完成记录均写 Git metadata。pending 必须遵循 Section 15.1 的 UUID attempt 路径、八字段 schema 和 exact readback，完成记录必须保存同一 `attemptId/pendingPath`。文件不存在时 CreateNew；存在时 exact schema 回读。核心 payload相同返回原文件 SHA/time，不同拒绝；禁止 `Set-Content` 覆盖、删除后重建或只更新 completedAt。abort 后 pending 保留作为失败审计，下一次开始必须设置新 UUID 并按固定路径规则创建新 attempt 文件，不能覆盖历史。

### 15.5 Evidence manifest gate

Task 1 同时将下面脚本写入 `git rev-parse --git-path p3-evidence-manifest-gate.ps1`，编码/换行同 evidence gate：

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][string]$RepoRoot,
  [Parameter(Mandatory=$true)][ValidateSet('unit','it','migration','vitest','standards','scan')][string]$Kind,
  [Parameter(Mandatory=$true)][string]$ExpectedHead,
  [Parameter(Mandatory=$true)][DateTimeOffset]$WindowStartUtc
)
$ErrorActionPreference='Stop'
$rootText=(& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)){throw 'manifest gate 无法解析仓库根'}
$root=[IO.Path]::GetFullPath($rootText.Trim())
if($root -cne [IO.Path]::GetFullPath($RepoRoot)){throw 'manifest RepoRoot 不准确'}
Set-Location -LiteralPath $root
if($ExpectedHead -cnotmatch '^[0-9a-f]{40}$'){throw 'ExpectedHead 非小写 40 SHA'}
$head=(& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
if($head -cne $ExpectedHead){throw 'manifest HEAD 漂移'}
if(@(& git status --porcelain=v1 -uall).Count -ne 0){throw 'manifest 要求 clean worktree'}
if($WindowStartUtc.Offset -ne [TimeSpan]::Zero){throw 'window start 必须 UTC'}
$artifactRegistry=switch($Kind){
  'unit'{@(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.ScriptContractRegistryTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.EffectiveCharacterCounterTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptGenerationResultValidatorTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptOptimizationResultValidatorTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptFrozenInputValidatorTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptVersionServiceImplTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.UserScriptQueryServiceImplTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptFrozenInputAssemblerTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptGenerationServiceImplTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.script.service.impl.ScriptGeneratedVersionWriterTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.script.provider.ScriptProviderResponseParserTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.script.provider.ScriptGenerationTaskHandlerTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.script.provider.ScriptOptimizationTaskHandlerTest.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-user/target/surefire-reports/TEST-org.dromara.aivideo.user.script.controller.UserScriptControllerTest.xml'
  )}
  'it'{@(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptSchemaMigrationIT.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptVersionPersistenceIT.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptFrozenInputPersistenceIT.xml',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptGenerationBillingIT.xml',
    'ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.ScriptUserApiIT.xml'
  )}
  'migration'{@(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.script.ScriptSchemaMigrationIT.xml',
    "git-metadata:p3-evidence/$head/migration-07-replay.log"
  )}
  'vitest'{@(
    'git-metadata:p3-reports/p3-task7-script-flow.json',
    'git-metadata:p3-reports/p3-task8-script-library.json',
    'git-metadata:p3-reports/p3-task9-studio-integration.json'
  )}
  'standards'{@("git-metadata:p3-evidence/$head/standards.log")}
  'scan'{@("git-metadata:p3-evidence/$head/scan.log")}
}
$normalized=@($artifactRegistry | ForEach-Object{$_.Replace('\','/').Trim()} | Sort-Object)
if(@($normalized | Sort-Object -Unique).Count -ne $normalized.Count){throw 'artifact path 重复'}
$artifacts=@()
foreach($relative in $normalized){
  if([string]::IsNullOrWhiteSpace($relative) -or $relative.Contains('..')){
    throw "非法 artifact path：$relative"
  }
  if($relative.StartsWith('git-metadata:',[StringComparison]::Ordinal)){
    $metadataRelative=$relative.Substring('git-metadata:'.Length)
    if([IO.Path]::IsPathRooted($metadataRelative)){throw "Git metadata artifact 非相对路径：$relative"}
    $raw=(& git rev-parse --git-path $metadataRelative).Trim()
    if($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($raw)){throw "Git metadata artifact 无法解析：$relative"}
    $absolute=if([IO.Path]::IsPathRooted($raw)){[IO.Path]::GetFullPath($raw)}else{[IO.Path]::GetFullPath((Join-Path $root $raw))}
  }else{
    if([IO.Path]::IsPathRooted($relative)){throw "repo artifact 非相对路径：$relative"}
    $absolute=[IO.Path]::GetFullPath((Join-Path $root $relative))
    if(-not $absolute.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){
      throw "artifact 越出仓库：$relative"
    }
  }
  if(-not (Test-Path -LiteralPath $absolute -PathType Leaf)){throw "artifact 缺失：$relative"}
  $file=Get-Item -LiteralPath $absolute
  $mtime=[DateTimeOffset]$file.LastWriteTimeUtc
  if($mtime -lt $WindowStartUtc.AddSeconds(-2)){throw "artifact 非 fresh：$relative"}
  $artifacts+=[ordered]@{
    path=$relative
    sha256=(Get-FileHash -LiteralPath $absolute -Algorithm SHA256).Hash.ToLowerInvariant()
    bytes=[long]$file.Length
    mtimeUtc=$mtime.ToUniversalTime().ToString('o')
  }
}
$manifestText=(& git rev-parse --git-path ("p3-evidence/$head/$Kind.manifest.json")).Trim()
$manifest=if([IO.Path]::IsPathRooted($manifestText)){
  [IO.Path]::GetFullPath($manifestText)
}else{
  [IO.Path]::GetFullPath((Join-Path $root $manifestText))
}
function Assert-Fields([object]$value,[string[]]$expected,[string]$name){
  if((@($value.PSObject.Properties.Name)-join '|') -cne ($expected-join '|')){throw "$name 字段/顺序漂移"}
}
if(Test-Path -LiteralPath $manifest -PathType Leaf){
  $existing=Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $existing @('schemaVersion','kind','head','windowStartUtc','windowEndUtc','artifacts','capturedAtUtc') 'manifest'
  if($existing.schemaVersion -cne 'p3-evidence-1' -or $existing.kind -cne $Kind -or
     $existing.head -cne $head -or
     ([DateTimeOffset]::Parse($existing.windowStartUtc)).ToUniversalTime() -ne $WindowStartUtc.ToUniversalTime()){
    throw '既有 manifest core 漂移'
  }
  if(@($existing.artifacts).Count -ne $artifacts.Count){throw '既有 manifest artifact count 漂移'}
  for($i=0;$i -lt $artifacts.Count;$i++){
    Assert-Fields $existing.artifacts[$i] @('path','sha256','bytes','mtimeUtc') "artifact[$i]"
    $current=$artifacts[$i]
    if($existing.artifacts[$i].path -cne $current.path -or
       $existing.artifacts[$i].sha256 -cne $current.sha256 -or
       [long]$existing.artifacts[$i].bytes -ne [long]$current.bytes -or
       $existing.artifacts[$i].mtimeUtc -cne $current.mtimeUtc){throw "artifact[$i] 已变化"}
  }
  'P3_EVIDENCE_MANIFEST_OK'; exit 0
}
$now=[DateTimeOffset]::UtcNow
$document=[ordered]@{
  schemaVersion='p3-evidence-1'
  kind=$Kind
  head=$head
  windowStartUtc=$WindowStartUtc.ToUniversalTime().ToString('o')
  windowEndUtc=$now.ToUniversalTime().ToString('o')
  artifacts=$artifacts
  capturedAtUtc=$now.ToUniversalTime().ToString('o')
}
$directory=Split-Path -Parent $manifest
if(-not (Test-Path -LiteralPath $directory -PathType Container)){
  [void](New-Item -ItemType Directory -Path $directory)
}
$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($document | ConvertTo-Json -Depth 8 -Compress))
$stream=[IO.File]::Open($manifest,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
try{$stream.Write($bytes,0,$bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
'P3_EVIDENCE_MANIFEST_OK'
```

### 15.6 六个 manifest artifact 集合

| kind | exact artifact count | 集合 |
|---|---:|---|
| unit | 14 | Section 6.1 十四个 Surefire XML |
| it | 5 | Section 6.1 五个 Failsafe XML |
| migration | 2 | `ScriptSchemaMigrationIT.xml` + CreateNew `migration-07-replay.log` |
| vitest | 3 | Git metadata 的 `p3-reports/p3-task7-script-flow.json`、`p3-task8-script-library.json`、`p3-task9-studio-integration.json`；内部 testResults 的 repo-relative path 并集去重后 exact 9 files，重复的 `ScriptStep.test.tsx` 只计一次 |
| standards | 1 | `git-metadata:p3-evidence/<f4Head>/standards.log`，包含实际 `DEVELOPMENT_STANDARDS_OK` 且末行 `P3_STANDARDS_OK` |
| scan | 1 | `git-metadata:p3-evidence/<f4Head>/scan.log`，末行 `P3_SCAN_OK` |

manifest gate 按 `Kind` 内建上表 exact path 集合和数量，不接受调用方替换路径或只提供相同数量的任意文件。unit/it 与 migration 可共享 migration XML 但各 manifest 语义不同；reviewer 对相同 path 仍实时复算同 SHA。日志必须包含执行命令、UTC start/end、exit code、HEAD、sentinel；不能只有手写 sentinel。

### 15.7 Acceptance window

`p3-acceptance-window-<f4Head>.json` exact fields：

```text
f1Head
f1AmendmentHead
f2Head
f3Head
f4Head
p1HandoffSha256
p2HandoffSha256
f1AddendumSha256
startedAtUtc
```

- `f1Head` 精确为 original P0-C F1 handoff head，`f1AmendmentHead` 精确为 addendum amendmentHead，`f1AddendumSha256` 为 live `p0c-f1-contract-addendum.json` SHA；F2/F3 分别是 P1/P2 reviewed handoff head；F4 是当前 clean candidate。
- 所有 `*Head` 为小写 40 位，handoff/addendum hash 为小写 64 位；真实 ancestry 只要求 original `f1Head→f2Head→f3Head→f4Head`，旧 amendmentHead 按 binding record/actual contract 证明语义存续。
- startedAtUtc 在所有 fresh artifacts 之前；manifest windowEnd 和 independent review 时间之后。
- candidate HEAD 变化必须创建新 window；禁止沿用旧 reports/manifests/review。

### 15.8 独立 review 最小复跑

独立 reviewer 必须至少亲自运行：

1. `ScriptContractRegistryTest`。
2. `ScriptGenerationResultValidatorTest#rejectsCandidateCodesOtherThanExactlyABC`。
3. `ScriptGenerationResultValidatorTest#rejectsCandidateWithoutExactlyThreeDistinctTitles`。
4. `ScriptSchemaMigrationIT`（带 local profile）。
5. `ScriptGenerationBillingIT`（带 local profile）。
6. `ScriptUserApiIT`（shared owner window，带 local profile）。
7. Task 7/8/9 三组 Vitest、lint、build。
8. standards、F4 scan、`git diff --check`。
9. 六 manifest 全 artifact hash/bytes/mtime/count/window 实时重算。
10. F1/F2/F3 rebase record、P0-C addendum 与 P1/P2 handoff hash/ancestry/binding digest 复核。

reviewer 发现失败只写 review findings，不修改源码后立即自签；修复后 candidate HEAD 变化必须新 acceptance window 和重新 review。

### 15.9 `p3-independent-review.json`

字段顺序固定：

```text
owner
reviewer
reviewStatus
reviewedHead
windowStartUtc
windowEndUtc
representativeSelectors
evidence
findingsClosed
reviewedAtUtc
```

`representativeSelectors` exact 为 Section 15.8 的测试/命令稳定名称；`evidence` 六项 path/sha；`findingsClosed=true` JSON boolean；reviewStatus 只能 PASS。Owner/Reviewer 不同。只能 reviewer 的会话/凭据 CreateNew，writer 无权写或覆盖。

### 15.10 F4 freeze 前最后核对

- current HEAD 等于 review reviewedHead、acceptance f4Head、manifest head。
- worktree clean，linked codex branch，无 Git operation marker。
- review window 包含所有 report/log mtime，reviewedAt 晚于 manifest window end。
- review evidence path/sha 与六 manifest实时值相等。
- stable Service/DTO反射值、migration/table值、test registry、UI matrix与 handoff payload exact。
- F1/F2/F3 target 仍是 current ancestry，P0-C addendum 与 P1/P2 handoff 文件 hash 没变化，所有 component/source/signature/lock/write/context-semantics binding digest 现场重算一致。
- fake、上游越界依赖、默认 actor、异步审计回调和敏感日志的 scan 均为 0。
- `requireGenerationContextWritable` 能通过相同 family/group识别 generate 与 optimize active tasks。
- audit append failure rollback tests 在 unit、billing IT、user API IT 都有 fresh evidence。

全部成立才按 Section 6.3 CreateNew `p3-f4-handoff.json`。handoff 创建本身不修改 candidate HEAD，因此创建前后 HEAD 相同；handoff 位于 Git metadata，不 stage/commit/push。
