# P1 系统文案知识中心实现计划

> 状态：按 P0-C F1 冻结契约和并行交付规格重写。执行时使用 `executing-plans` 或 `subagent-driven-development`；每完成一张任务卡，先过该卡证据门禁。

**目标：** 建成系统知识中心，支持知识条目、不可变版本、审核发布、适用绑定、确定性 A/B/C 路由、历史快照和安全 ZIP 导入，并形成 P2/P3 只能通过稳定 Service/DTO 消费的 F2 交接面。

**架构：** 后端严格使用 RuoYi-Vue-Plus 的贫血 Entity + Mapper + Service 编排。`ai-video-core` 保存领域对象、DTO、Mapper、Service 与 `service.impl`；`ai-video-platform` 只保存 HTTP Controller/BO/VO；`ai-video-infra` 只保存 ZIP provider、外部 client 和异步 listener。禁止创建平行业务层。

**计划依据：** `AGENTS.md`、`RULES.md`、`docs/DOCUMENT_MAP.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、前后端指南与编码规范、`docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`、并行交付规格、计划整改文档 Task 5，以及 `ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`。

---

## 0. 固定边界、阶段和证据规则

### 0.1 P1 稳定跨阶段契约

P1 对 P2/P3 只公开以下两个稳定接口；接口包固定为 `org.dromara.aivideo.knowledge.service`，五个 DTO 包固定为 `org.dromara.aivideo.knowledge.dto`：

```java
public interface IKnowledgeRoutingService {
    KnowledgeRouteResultDTO route(KnowledgeRouteRequestDTO request);
}

public interface IKnowledgeSnapshotService {
    KnowledgeSnapshotDTO create(KnowledgeSnapshotRequestDTO request);
    KnowledgeSnapshotDTO getByRootTaskId(Long rootTaskId);
}
```

阶段内 Service 固定为 `IKnowledgeCatalogService`、`IKnowledgePublicationService`、`IKnowledgeImportService`。

稳定 DTO 注册表只能有以下五个顶层文件：

1. `KnowledgeRouteRequestDTO`
2. `KnowledgeRouteResultDTO`
3. `KnowledgePlanDTO`
4. `KnowledgeSnapshotRequestDTO`
5. `KnowledgeSnapshotDTO`

五个文件的 record 组件类型、名称和顺序冻结如下；省略的只有 `package` 与 `java.time.Instant`／`java.util.List` import，不得增加、删除、重排组件或改用 primitive/别名类型：

```java
public record KnowledgeRouteRequestDTO(
    Long directionCatalogVersionId,
    String industryCode,
    String purposeCode,
    Integer targetDurationSeconds,
    List<String> tagCodes
) {
}

public record KnowledgePlanDTO(
    String candidateCode,
    String planCode,
    Long primaryTemplateVersionId,
    String angleCode,
    String differentiatorTechniqueCode
) {
}

public record KnowledgeRouteResultDTO(
    String routingVersion,
    String videoTypeCode,
    List<KnowledgePlanDTO> plans,
    String contentHash
) {
}

public record KnowledgeSnapshotRequestDTO(
    Long rootTaskId,
    Long promptVersionId,
    Long generationContextRevision,
    String generationInputHash,
    KnowledgeRouteResultDTO route,
    List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts
) {
    public record AcceptedFactSnapshotDTO(
        Long factId,
        Long decisionRevision,
        String factText,
        String evidenceRef
    ) {
    }
}

public record KnowledgeSnapshotDTO(
    Long snapshotId,
    Long rootTaskId,
    Long promptVersionId,
    Long generationContextRevision,
    String generationInputHash,
    KnowledgeRouteResultDTO route,
    List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts,
    List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials,
    String contentHash,
    Instant createdAt
) {
    public record KnowledgeMaterialSnapshotDTO(
        Long knowledgeVersionId,
        Long bindingVersionId,
        Long videoRuleVersionId,
        String contentExcerpt,
        Integer injectionOrder
    ) {
    }
}
```

字段与不变量冻结如下：

- `KnowledgeRouteRequestDTO`：`directionCatalogVersionId`、`industryCode`、`purposeCode`、`targetDurationSeconds`、`tagCodes`；集合在构造时复制并排序，稳定代码非空，目录版本大于零。
- `KnowledgePlanDTO` 的五个主字段固定为：`candidateCode`、`planCode`、`primaryTemplateVersionId`、`angleCode`、`differentiatorTechniqueCode`。
- `KnowledgeRouteResultDTO`：`routingVersion`、`videoTypeCode`、`plans`、`contentHash`；`plans` 必须恰好是有序 A/B/C。`candidateCode`、`planCode` 和完整 `primaryTemplateVersionId + angleCode + differentiatorTechniqueCode` 三元组分别唯一；优先选择不同主模板，模板不足时允许复用主模板并用唯一三元组补位。
- `KnowledgeSnapshotRequestDTO`：`rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、`route`、`acceptedFacts`。字段类型精确为 `List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO>`；`AcceptedFactSnapshotDTO` 是该文件内的嵌套不可变 record，包含 `factId`、`decisionRevision`、`factText`、`evidenceRef`。
- `KnowledgeSnapshotDTO`：`snapshotId`、`rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、`route`、`acceptedFacts`、`knowledgeMaterials`、`contentHash`、`createdAt`。`acceptedFacts` 复用 `KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO`；`knowledgeMaterials` 使用本文件内嵌套不可变 `KnowledgeMaterialSnapshotDTO`，冻结 `knowledgeVersionId`、`bindingVersionId`、`videoRuleVersionId`、实际注入的 `contentExcerpt` 和 `injectionOrder`。所有集合防御性复制；canonical hash 必须按固定字段顺序覆盖除 `snapshotId`、`contentHash`、`createdAt` 外的全部不可变 payload，即 `rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、有序 `route`、有序 `acceptedFacts`、有序 `knowledgeMaterials`，历史内容不可回写。

`KnowledgeRouteResultDTO` 的三个方案必须由已发布的 `copywriting` 知识版本产生；其他知识域只可管理、导入和查询。内部 DTO 只允许 `knowledge/dto/internal/KnowledgeTemplateCandidateDTO` 与 `RequiredKnowledgeRuleDTO`，不进入稳定注册表。实现类私有方法名称固定为 `resolveVideoType`、`validateRequiredRules`、`toPlan`、`enforceContextBudget`、`freezePayload`、`toCanonicalJson`；不得创建额外协调类型。

发布校验只能是 `KnowledgePublicationServiceImpl` 内的私有方法，并在发布写库前调用：

```java
private void validateForPublication(KnowledgeVersion version) {
    // 校验领域、状态、方向绑定、内容和乐观锁修订号
}
```

不得拆成独立校验类型。

### 0.2 P0-C F1 消费边界

P0-C 白名单分三层：

- 稳定 Service：`org.dromara.aivideo.task.service.IAiTaskService`、`org.dromara.aivideo.task.service.IAiTaskExecutionDispatcher`、`org.dromara.aivideo.direction.service.IDirectionCatalogService`；
- 限定内部 SPI：`org.dromara.aivideo.task.service.IAiTaskExecutionHandler`，只能由 infra `KnowledgeImportTaskHandler` 实现，不列为 P1 跨阶段稳定 Service；
- 领域/DTO：`org.dromara.aivideo.task.domain.AiTaskType`、`org.dromara.aivideo.direction.dto.DirectionCatalogSnapshotDTO`，以及 `org.dromara.aivideo.task.dto` 下的 `TaskInitiatorDTO`、`FreeTaskDTO`、`TaskRevisionSnapshotDTO`、`TaskCreationResultDTO`、`AiTaskExecutionLeaseDTO`、`TaskResultReferenceDTO`。

真实调用面只允许 `IAiTaskService.createFreeTask(FreeTaskDTO)`、`markSuccess(AiTaskExecutionLeaseDTO, TaskResultReferenceDTO)`、`markFailed(AiTaskExecutionLeaseDTO, String failureCode, String failureMessage)`，`IAiTaskExecutionDispatcher.enqueue(Long, Long)` 和 `IDirectionCatalogService.currentPublishedCatalog()`。P1 不得调用 `claimExecutableTasks`、`renewLease` 或 `recordHandlerFailure`；不得注入 attempt 服务，不得接触 quota、usage、task Mapper/表或自建任务状态推进。

`DirectionCatalogSnapshotDTO` 的 component 名称与顺序精确为 `catalogVersion`、`contentHash`、`industryCatalogVersion`、`purposeCatalogVersion`、`durationRuleVersion`、`industries`、`purposesByIndustry`、`targetDurations`。P1 在 F1 后构造或读取该 DTO 的 fixture／集成测试必须提供全部八项，并断言两个目录子版本为正数、时长规则版本非空；不得继续使用五 component 旧 fixture。`contentHash` 与三个子版本只属于 core 服务端快照，P1 的平台 HTTP BO／VO、TypeScript 类型和 Mock 不得序列化这些字段，HTTP 方向选项仍只公开聚合 `catalogVersion`。

`SysTaskInitiatorResolver` 只允许位于 `ai-video-platform` 的 HTTP 端层，负责构造 `TaskInitiatorDTO` 后传入核心 Service。只有 F1 后 Task 7–8 的 core 单测可显式构造 P0-C `TaskInitiatorDTO`；Task 1–5 不得编译依赖、复制或仿造该类型，core 始终不得读取当前登录态。

`knowledge_import` 是免费任务，事务固定为：

```text
createFreeTask -> freeze immutable input -> enqueue
```

三步在一个外层事务中完成；复用任务立即返回，不重复冻结、不重复入队。P0-C 幂等维度固定为 `(tenantId, actorType, actorId, taskType, idempotencyKey)`；同维度同 hash 恢复并复用唯一 root 与 `executionNo=1`，同维度不同 hash 返回 46116。resourceType/resourceId 是任务资源引用，不得擅自加入或替代冻结幂等维度。`usageOperationId` 必须为 null，且不存在 usage 记录。handler 实现 `IAiTaskExecutionHandler`，支持 `AiTaskType.KNOWLEDGE_IMPORT`，不创建 attempt，不记录 usage，不调用模型或搜索 provider；正常返回前必须以完整 lease 调用 `markSuccess` 或 `markFailed` 达到终态。意外基础设施异常向 P0-C 注册器抛出，由注册器处理；P1 自身不调用 `recordHandlerFailure`。

P0-C 的 `FreeTaskDTO` 强制携带合法 `TaskRevisionSnapshotDTO`，且 `branchRevision >= 1`；但当前冻结契约没有定义 `knowledge_import` 的 `draftRevision`、`branchRevision`、`generationContextRevision` 映射。F1 handoff 必须由契约 owner 明确并签字确认该映射；缺少该字段时，P1 的真实导入装配、05 集成和 F2 冻结全部阻断。F1 前纯逻辑测试只能使用标明为 fixture 的合法显式值，不得把 fixture 当成业务决定，也不得传 null/0 或自造 DTO。

导入成功引用采用 `TaskResultReferenceDTO.of("knowledge_import_batch", batchId)`；`knowledge_import_batch` 是 P1 本域在 API/领域文档中登记的约定，不宣称它是 P0-C 已冻结事实。

原 `p0c-f1-handoff.json` 是不可变历史证据；P1 还必须消费
`AI_VIDEO_P0C_F1_ADDENDUM` 指向的 `p0c-f1-contract-addendum.json` 与其三项 evidence／独立 review。
addendum 顶层键及顺序精确为 `originalF1Head`、`amendmentHead`、
`originalF1HandoffSha256`、`requiredMethods`、`schemaAddendum`、`owner`、`reviewer`、
`reviewStatus`、`reviewedHead`、`reviewCompletedAtUtc`、`evidence`、`capturedAtUtc`。
`requiredMethods` 必须冻结 `requireGenerationContextWritable(...)` 与
`inheritQuestionnaireTaskGroupMembers(...)` 的完整源码签名；`schemaAddendum` 必须精确冻结
`20260728_04a_p0c_task_group_guard.sql`、`av_ai_task_group_member`、
`idx_av_ai_task_active_group`、`origin|inherited`、`app_user|sys_user`、全局锁序、
`script:{draftId}:{branchRevision}`、`membership_only` 与禁止复制的
`task|usage|ledger|operation_slot`。三项 evidence 固定顺序为 `source-signatures`、
`migration-04a`、`independent-review`，每项只含 `kind/path/sha256` 并必须实时复核 bytes。
review 只含 `owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`originalF1Head`、
`originalF1HandoffSha256`、`requiredMethodsSha256`、`schemaAddendumSha256`、
`reviewCompletedAtUtc`；owner/reviewer 独立、状态 `PASS`、`reviewedHead=amendmentHead`、UTC，且
`capturedAtUtc >= reviewCompletedAtUtc`。`originalF1Head` 必须是 `amendmentHead` 的祖先，
addendum 中的原 handoff SHA 必须等于只读原文件 live SHA。

### 0.3 F1 前后切片

| 阶段 | 允许做 | 禁止做 | 阶段出口 |
|---|---|---|---|
| F1 前独立切片 | 五个稳定 DTO/两个稳定接口；领域状态机；路由、快照和 ZIP 安全纯逻辑；平台 API 类型、Mock、ProComponents 页面和状态测试；discovery 外 05 草案 | 创建/执行正式 05；调用 P0-C 实现；真实导入任务；真实 Controller 联调 | Task 1–5 全绿 |
| F1 后集成切片 | 在隔离且 clean 的 P1 worktree 同时核验不可变原 handoff 与 F1 addendum，真实 rebase 到 `amendmentHead`；执行 01→02→03→04→04a→05 并复跑 05；接入任务、方向、导入、Controller 和真实前端 API | 绕过 F1 amendment；在共享工作区 rebase；P2/P3 直连 Mapper/表 | Task 6–10 全绿并形成 F2 |

执行本计划整改时不得提前执行 rebase。只有 Task 6 的所有前置核验通过，才在专属 P1 worktree 执行计划中给出的 rebase 命令，并记录 `beforeHead`、`f1Head`、`afterHead` 和 merge-base。

### 0.4 全局 TDD 与报告证据

所有 Java 测试类（`*Test` 与 `*IT`）必须类级标注 `@Tag("dev")`。每个 RED 的顺序固定为“创建可编译 skeleton → 创建目标测试 → `Prepare` → 执行目标测试 → `AssertRed`”；Java 未实现方法抛 `UnsupportedOperationException("RED skeleton")`，React skeleton 只渲染最小占位，API skeleton 显式 reject。RED 必须由该卡新增的业务/安全断言失败；编译失败、测试未发现、环境失败、全 skipped 或缺报告均不算 RED。GREEN 必须复用同一测试与同一精确报告名，重新 `Prepare` 后执行并 `AssertGreen`。

Task 1 在当前 Git metadata 目录创建四份统一门禁脚本，不提交到仓库：

- `p1-worktree-gate.ps1`：验证动态 repo root、`codex/*` 分支、专属 worktree、基线 HEAD 和任务所有权；仅在传入 `-RequireClean` 的 F1 rebase/F2 冻结点强制 clean，集成阶段还验证 F1 祖先和 rebase 记录。
- `p1-jvm-evidence-gate.ps1`：`Prepare` 删除本轮精确 Surefire/Failsafe XML 并返回 UTC 起点；`AssertRed` 要求 fresh、tests > 0、真实 failure/error > 0 且 skipped < tests；`AssertGreen` 要求 fresh、tests > 0 且 failure/error/skipped 全为 0。它同时支持单个 `-ModulePath/-ReportName` 和最终门禁的 `-SearchRoot/-ReportNames`，后者逐个精确报告验证，禁止用旧报告或宽泛 glob 代替。
- `p1-vitest-evidence-gate.ps1`：JSON 报告写入当前 Git metadata；RED 要求 fresh、总数 > 0、failed > 0 且不是全 pending；GREEN 要求 fresh、总数 > 0、failed/pending = 0 且 success 为 true。
- `p1-evidence-manifest-gate.ps1`：把 final window 内的真实 artifact 规范化为六类固定 manifest，实时记录 scope、相对路径、bytes、mtime 与 SHA-256，并以目标路径 `CreateNew` 幂等冻结。

每个 PowerShell 验证块必须自行解析当前 worktree 根与统一 gate，禁止硬编码机器路径。所有 `*IT` 命令都带 `-Pdev,local-integration-test`，只允许 `LocalIntegrationEnvironment` 指向受控本机 `ai_video_test` 和独立 Redis 前缀。

标准前导：

```text
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gitPath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gitPath)) { [IO.Path]::GetFullPath($gitPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gitPath)) }
if (-not (Test-Path -LiteralPath $gate)) { throw 'P1 worktree gate is missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
```

---

## 文件边界

### `ai-video-core`

所有 core 文件位于 `org.dromara.aivideo.knowledge`：

- `domain/`：`KnowledgeItem`、`KnowledgeVersion`、`KnowledgeBinding`、`VideoTypeRule`、`KnowledgeImportBatch`、`KnowledgeImportEntry`、`KnowledgeSnapshot`、`KnowledgeImportFrozenInput`，以及根目录枚举 `KnowledgeDomainCode`、`KnowledgeTypeCode`、`KnowledgeVersionStatus`、`KnowledgeImportStatus`
- `dto/`：五个稳定 DTO 文件；`dto/internal/` 仅放两个明确的 P1 内部 DTO
- `mapper/`：每个 Entity 对应的 `BaseMapperPlus`
- `service/`：五个固定接口
- `service/impl/`：五个对应实现

### `ai-video-platform`

- `knowledge/controller/KnowledgeController.java`
- `knowledge/domain/bo/`：`KnowledgeItemQueryBo`、`CreateKnowledgeItemBo`、`CreateKnowledgeVersionBo`、`SubmitKnowledgeReviewBo`、`PublishKnowledgeVersionBo`、`KnowledgeBindingBo`、`VideoTypeRuleBo`、`CreateKnowledgeImportBatchBo`
- `knowledge/domain/vo/`：`KnowledgeItemVo`、`KnowledgeVersionVo`、`KnowledgeBindingVo`、`VideoTypeRuleVo`、`KnowledgeImportBatchVo`

HTTP BO/VO 不得进入 core；稳定 DTO 不得进入 platform。

### `ai-video-infra`

- `knowledge/provider/KnowledgeArchiveReader.java`
- `knowledge/provider/SafeZipKnowledgeArchiveReader.java`
- `knowledge/provider/KnowledgeTextDecoder.java`
- `knowledge/provider/KnowledgeArchiveManifest.java`
- `knowledge/listener/KnowledgeImportTaskHandler.java`

ZIP 原始 manifest、解码和读档类型只在 infra；其他模块不得出现直接 provider/client 实现。

### 平台前端

- `src/api/aivideo/knowledge/`：`index.ts`、`types.ts`
- `mock/aivideo-knowledge.ts`：仅开发/测试 Mock
- `src/pages/aivideo/knowledge/`：`index.tsx`、`index.test.tsx`
- `src/pages/dynamicPage.tsx`：受控共享动态页面入口
- `src/pages/aivideo/knowledge/components/`：`KnowledgeImportReviewDrawer.tsx`、`KnowledgeImportReviewDrawer.test.tsx`

---

## Task 1：冻结稳定契约和统一证据门禁（F1 前）

### 最小任务卡

- **单一目标／不做：** 固定两个跨阶段 Service、五个 DTO 和统一证据脚本；不实现路由、快照或数据库。
- **权威来源：** 本计划 0.1、并行交付规格的 P1 稳定边界、整改计划 Task 5。
- **风险／触发：** 红；接口签名、顶层 DTO 数量或证据 helper 漂移即阻塞 P2/P3。
- **所有权／数据范围：** P1 owner；只改知识 DTO/Service 骨架、契约测试和当前 Git metadata。
- **依赖／人员／并发：** 无 F1 依赖；可与 Task 5 的前端 Mock 设计并行，协作者不超过两人。
- **允许影响：** core 编译面与测试辅助；禁止修改 P0-C 实现和共享 DTO。
- **成功／反向验收：** 契约反射测试真实 RED→GREEN；缺签名、第六个顶层 DTO 或伪 RED 均失败。
- **固定输出：** 两个稳定接口、五个 DTO、`KnowledgeContractTest`、四份 metadata gate。

### 实现步骤

1. 在任何 RED 命令前执行下方 bootstrap：fail-closed 校验动态 root/分支，创建 baseline metadata 和四份 helper，并分别自测 worktree/JVM/Vitest 的严格正反语义；manifest helper 在 Task 10 的六类真实 artifact 上验证 `CreateNew` 与幂等回读。
2. 创建五个 DTO 和两个接口的可编译 RED skeleton；所有未实现路径显式抛 `UnsupportedOperationException("RED skeleton")`。
3. 再创建 `KnowledgeContractTest`，标注 `@Tag("dev")`，反射逐一断言两个接口签名、五个顶层 record 和两个嵌套 record 的全部组件类型／名称／顺序、稳定 DTO 文件清单和不可变集合；测试必须因明确断言失败。
4. 实现 DTO 构造校验及防御性复制，使同一测试转绿。

### Gate bootstrap（必须先执行）

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
Set-Location $repoRoot
$branch = (& git branch --show-current).Trim()
if ($branch -notlike 'codex/*' -or $branch -in @('main','master')) { throw 'P1 requires a codex/* branch' }
$initialDirty = @(& git status --porcelain)
if ($initialDirty.Count -ne 0) { throw 'Commit or remove pre-existing changes before P1 bootstrap' }
$head = (& git rev-parse HEAD).Trim()
if ($head -notmatch '^[0-9a-f]{40}$') { throw 'Invalid baseline HEAD' }
function Resolve-GitMetadataPath([string]$name) {
    $raw = (& git rev-parse --git-path $name).Trim()
    if ([IO.Path]::IsPathRooted($raw)) { return [IO.Path]::GetFullPath($raw) }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $raw))
}
$gate = Resolve-GitMetadataPath 'p1-worktree-gate.ps1'
$jvmGate = Resolve-GitMetadataPath 'p1-jvm-evidence-gate.ps1'
$vitestGate = Resolve-GitMetadataPath 'p1-vitest-evidence-gate.ps1'
$manifestGate = Resolve-GitMetadataPath 'p1-evidence-manifest-gate.ps1'
$baselineFile = Resolve-GitMetadataPath 'p1-baseline.json'
$baselinePayload = [ordered]@{
    repoRoot = $repoRoot
    branch = $branch
    owner = [Environment]::UserName
    phase = 'independent'
    independentHead = $head
    createdAtUtc = [DateTime]::UtcNow.ToString('o')
}
if (Test-Path -LiteralPath $baselineFile) {
    $existing = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json
    if ([IO.Path]::GetFullPath([string]$existing.repoRoot) -ne $repoRoot -or $existing.branch -ne $branch) {
        throw 'Existing P1 baseline belongs to another root or branch'
    }
} else {
    [IO.File]::WriteAllText($baselineFile, ($baselinePayload | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
}

$worktreeSource = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$RepoRoot,
    [string]$ExpectedPhase = 'independent',
    [switch]$RequireClean,
    [switch]$RequireIsolated,
    [switch]$RegisterF1,
    [string]$F1Head,
    [string]$BeforeHead,
    [string]$AfterHead,
    [string]$TransactionId,
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'
$expectedRoot = [IO.Path]::GetFullPath($RepoRoot)
$actualRoot = [IO.Path]::GetFullPath((& git -C $expectedRoot rev-parse --show-toplevel).Trim())
if ($actualRoot -ne $expectedRoot) { throw 'RepoRoot is not the current worktree root' }
function Assert-ExactFields($Value, [string[]]$Expected, [string]$Label) {
    if ($Value -isnot [pscustomobject]) { throw "$Label must be one JSON object" }
    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    if ($actual.Count -ne $Expected.Count) { throw "$Label field count drifted" }
    for ($i = 0; $i -lt $Expected.Count; $i++) {
        if (-not [string]::Equals($actual[$i], $Expected[$i], [StringComparison]::Ordinal)) { throw "$Label fields/order drifted at index $i" }
    }
}
function Assert-JsonString($Value, [string]$Label) {
    if ($Value -isnot [string]) { throw "$Label must be a JSON string" }
    if ([string]::IsNullOrWhiteSpace($Value) -or $Value -ne $Value.Trim()) { throw "$Label must be a trimmed nonblank string" }
}
function Assert-Sha40($Value, [string]$Label) {
    Assert-JsonString $Value $Label
    if ($Value -cnotmatch '^[0-9a-f]{40}$') { throw "$Label must be lowercase 40-hex" }
}
function Assert-Sha256($Value, [string]$Label) {
    Assert-JsonString $Value $Label
    if ($Value -cnotmatch '^[0-9a-f]{64}$') { throw "$Label must be lowercase SHA-256" }
}
function Assert-UtcTimestamp($Value, [string]$Label) {
    Assert-JsonString $Value $Label
    if ($Value -cnotmatch '(?:Z|\+00:00)$') { throw "$Label must have an explicit UTC suffix" }
    $parsed = [DateTimeOffset]::ParseExact($Value, 'o', [Globalization.CultureInfo]::InvariantCulture)
    if ($parsed.Offset -ne [TimeSpan]::Zero) { throw "$Label must be UTC" }
}
function Assert-Baseline($Value) {
    if ($null -eq $Value) { throw 'P1 baseline metadata is missing' }
    Assert-JsonString $Value.phase 'baseline.phase'
    if ($Value.phase -cnotin @('independent','integrated')) { throw "Unknown P1 baseline phase: $($Value.phase)" }
    $fields = if ($Value.phase -ceq 'independent') {
        @('repoRoot','branch','owner','phase','independentHead','createdAtUtc')
    } else {
        @('repoRoot','branch','owner','phase','independentHead','f1Head','beforeHead','integratedHead','transactionId','createdAtUtc','integratedAtUtc')
    }
    Assert-ExactFields $Value $fields 'baseline'
    foreach ($name in @('repoRoot','branch','owner')) { Assert-JsonString $Value.$name "baseline.$name" }
    Assert-Sha40 $Value.independentHead 'baseline.independentHead'
    Assert-UtcTimestamp $Value.createdAtUtc 'baseline.createdAtUtc'
    if ($Value.phase -ceq 'integrated') {
        foreach ($name in @('f1Head','beforeHead','integratedHead')) { Assert-Sha40 $Value.$name "baseline.$name" }
        Assert-Sha256 $Value.transactionId 'baseline.transactionId'
        Assert-UtcTimestamp $Value.integratedAtUtc 'baseline.integratedAtUtc'
    }
}
function Assert-FinalRecord($Value) {
    if ($null -eq $Value) { throw 'Final F1 integration record is missing' }
    Assert-ExactFields $Value @('transactionId','beforeHead','originalF1Head','f1Head','afterHead','baseBefore','handoffSha256','f1AddendumSha256','revisionMappingContractOwner','integratedAtUtc') 'F1 final record'
    Assert-Sha256 $Value.transactionId 'final.transactionId'
    foreach ($name in @('beforeHead','originalF1Head','f1Head','afterHead','baseBefore')) { Assert-Sha40 $Value.$name "final.$name" }
    Assert-Sha256 $Value.handoffSha256 'final.handoffSha256'
    Assert-Sha256 $Value.f1AddendumSha256 'final.f1AddendumSha256'
    Assert-JsonString $Value.revisionMappingContractOwner 'final.revisionMappingContractOwner'
    Assert-UtcTimestamp $Value.integratedAtUtc 'final.integratedAtUtc'
}
function Assert-PendingRecord($Value) {
    if ($null -eq $Value) { throw 'Pending F1 integration record is missing' }
    Assert-ExactFields $Value @('transactionId','repoRoot','branch','owner','independentHead','beforeHead','originalF1Head','f1Head','baseBefore','handoffSha256','f1AddendumSha256','revisionMappingContractOwner','createdAtUtc') 'F1 pending record'
    Assert-Sha256 $Value.transactionId 'pending.transactionId'
    foreach ($name in @('repoRoot','branch','owner','revisionMappingContractOwner')) { Assert-JsonString $Value.$name "pending.$name" }
    foreach ($name in @('independentHead','beforeHead','originalF1Head','f1Head','baseBefore')) { Assert-Sha40 $Value.$name "pending.$name" }
    Assert-Sha256 $Value.handoffSha256 'pending.handoffSha256'
    Assert-Sha256 $Value.f1AddendumSha256 'pending.f1AddendumSha256'
    Assert-UtcTimestamp $Value.createdAtUtc 'pending.createdAtUtc'
}
function Assert-MustReject([string]$Label, [scriptblock]$Action) {
    $rejected = $false
    try { & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative self-test unexpectedly passed: $Label" }
}
if ($ExpectedPhase -cnotin @('independent','integrated')) { throw "Unknown expected phase: $ExpectedPhase" }
if ($SelfTest) {
    Assert-MustReject 'missing baseline' { Assert-Baseline $null }
    $badPhase = [pscustomobject][ordered]@{ repoRoot=$expectedRoot; branch='codex/selftest'; owner='selftest'; phase='future'; independentHead=('a' * 40); createdAtUtc='2026-01-01T00:00:00.0000000Z' }
    Assert-MustReject 'unknown baseline phase' { Assert-Baseline $badPhase }
    $badExtra = [pscustomobject][ordered]@{ repoRoot=$expectedRoot; branch='codex/selftest'; owner='selftest'; phase='independent'; independentHead=('a' * 40); createdAtUtc='2026-01-01T00:00:00.0000000Z'; extra=$true }
    Assert-MustReject 'extra baseline field' { Assert-Baseline $badExtra }
    Assert-MustReject 'missing final record' { Assert-FinalRecord $null }
    Write-Output 'P1_WORKTREE_GATE_SELFTEST_OK'
    return
}
function Resolve-Metadata([string]$name) {
    $raw = (& git -C $expectedRoot rev-parse --git-path $name).Trim()
    if ([IO.Path]::IsPathRooted($raw)) { return [IO.Path]::GetFullPath($raw) }
    return [IO.Path]::GetFullPath((Join-Path $expectedRoot $raw))
}
$baselineFile = Resolve-Metadata 'p1-baseline.json'
if (-not (Test-Path -LiteralPath $baselineFile)) { throw 'P1 baseline metadata is missing' }
$baseline = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json
Assert-Baseline $baseline
$branch = (& git -C $expectedRoot branch --show-current).Trim()
$head = (& git -C $expectedRoot rev-parse HEAD).Trim()
Assert-Sha40 $head 'current HEAD'
if (-not [IO.Path]::GetFullPath($baseline.repoRoot).Equals($expectedRoot, [StringComparison]::OrdinalIgnoreCase) -or
    -not [string]::Equals($baseline.branch, $branch, [StringComparison]::Ordinal) -or
    -not [string]::Equals($baseline.owner, [Environment]::UserName, [StringComparison]::Ordinal)) { throw 'P1 baseline owner/root/branch mismatch' }
if ($branch -notlike 'codex/*' -or $branch -in @('main','master')) { throw 'Invalid P1 branch' }
if ($RequireIsolated) {
    $gitDirRaw = (& git -C $expectedRoot rev-parse --git-dir).Trim()
    $commonDirRaw = (& git -C $expectedRoot rev-parse --git-common-dir).Trim()
    $gitDir = if ([IO.Path]::IsPathRooted($gitDirRaw)) { [IO.Path]::GetFullPath($gitDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $expectedRoot $gitDirRaw)) }
    $commonDir = if ([IO.Path]::IsPathRooted($commonDirRaw)) { [IO.Path]::GetFullPath($commonDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $expectedRoot $commonDirRaw)) }
    if ($gitDir -eq $commonDir) { throw 'This checkpoint requires an isolated linked worktree' }
}
$dirty = @(& git -C $expectedRoot status --porcelain)
if ($RequireClean -and $dirty.Count -ne 0) {
    throw 'This checkpoint requires a clean worktree'
}
if ($RegisterF1) {
    if (-not $RequireClean -or -not $RequireIsolated) { throw 'RegisterF1 itself requires clean and isolated switches' }
    foreach ($sha in @($F1Head,$BeforeHead,$AfterHead)) {
        if ($sha -notmatch '^[0-9a-f]{40}$') { throw 'F1 registration contains an invalid commit' }
    }
    Assert-Sha256 $TransactionId 'F1 transactionId'
    $pendingFile = Resolve-Metadata 'p1-f1-integration.pending.json'
    $finalFile = Resolve-Metadata 'p1-f1-integration.json'
    if (Test-Path -LiteralPath $pendingFile -PathType Leaf) {
        $proof = Get-Content -LiteralPath $pendingFile -Raw | ConvertFrom-Json
        Assert-PendingRecord $proof
    } elseif (Test-Path -LiteralPath $finalFile -PathType Leaf) {
        $proof = Get-Content -LiteralPath $finalFile -Raw | ConvertFrom-Json
        Assert-FinalRecord $proof
    } else {
        throw 'F1 registration requires an exact pending or final proof record'
    }
    if ($proof.transactionId -cne $TransactionId -or $proof.f1Head -cne $F1Head -or $proof.beforeHead -cne $BeforeHead) { throw 'F1 registration proof payload differs' }
    if ($proof.PSObject.Properties.Name -ccontains 'afterHead' -and $proof.afterHead -cne $AfterHead) { throw 'F1 final proof afterHead differs' }
    & git -C $expectedRoot merge-base --is-ancestor $AfterHead $head
    if ($LASTEXITCODE -ne 0) { throw 'F1 integrated HEAD is not an ancestor of current HEAD' }
    & git -C $expectedRoot merge-base --is-ancestor $baseline.independentHead $BeforeHead
    if ($LASTEXITCODE -ne 0) { throw 'Pre-rebase HEAD is not descended from P1 baseline' }
    & git -C $expectedRoot merge-base --is-ancestor $F1Head $AfterHead
    if ($LASTEXITCODE -ne 0) { throw 'F1 is not an ancestor of integrated HEAD' }
    if ($baseline.phase -ceq 'integrated') {
        if ($baseline.f1Head -cne $F1Head -or $baseline.beforeHead -cne $BeforeHead -or $baseline.integratedHead -cne $AfterHead -or $baseline.transactionId -cne $TransactionId) { throw 'A different F1 integration is already registered' }
    } else {
        $next = [ordered]@{
            repoRoot = $baseline.repoRoot
            branch = $baseline.branch
            owner = $baseline.owner
            phase = 'integrated'
            independentHead = $baseline.independentHead
            f1Head = $F1Head
            beforeHead = $BeforeHead
            integratedHead = $AfterHead
            transactionId = $TransactionId
            createdAtUtc = $baseline.createdAtUtc
            integratedAtUtc = [DateTime]::UtcNow.ToString('o')
        }
        $tempBaseline = Join-Path (Split-Path -Parent $baselineFile) ('.p1-baseline-' + [Guid]::NewGuid().ToString('N') + '.tmp')
        [IO.File]::WriteAllText($tempBaseline, ($next | ConvertTo-Json -Depth 5 -Compress), [Text.UTF8Encoding]::new($false))
        try { [IO.File]::Move($tempBaseline, $baselineFile, $true) } finally { if (Test-Path -LiteralPath $tempBaseline) { Remove-Item -LiteralPath $tempBaseline } }
        $baseline = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json
        Assert-Baseline $baseline
    }
    Write-Output 'P1_WORKTREE_GATE_OK'
    return
} elseif ($baseline.phase -ceq 'independent') {
    & git -C $expectedRoot merge-base --is-ancestor $baseline.independentHead $head
    if ($LASTEXITCODE -ne 0) { throw 'Current independent HEAD is not descended from P1 baseline' }
}
if ($baseline.phase -cne $ExpectedPhase) { throw "P1 phase mismatch: expected $ExpectedPhase, got $($baseline.phase)" }
if ($ExpectedPhase -ceq 'integrated') {
    if ($baseline.f1Head -notmatch '^[0-9a-f]{40}$') { throw 'F1 integration is not registered' }
    & git -C $expectedRoot merge-base --is-ancestor $baseline.f1Head $head
    if ($LASTEXITCODE -ne 0) { throw 'Registered F1 is not an ancestor' }
    & git -C $expectedRoot merge-base --is-ancestor $baseline.integratedHead $head
    if ($LASTEXITCODE -ne 0) { throw 'Current HEAD is not descended from integrated P1 baseline' }
    $finalFile = Resolve-Metadata 'p1-f1-integration.json'
    if (-not (Test-Path -LiteralPath $finalFile -PathType Leaf)) { throw 'Final F1 integration record is missing' }
    $final = Get-Content -LiteralPath $finalFile -Raw | ConvertFrom-Json
    Assert-FinalRecord $final
    if ($final.transactionId -cne $baseline.transactionId -or $final.f1Head -cne $baseline.f1Head -or $final.beforeHead -cne $baseline.beforeHead -or $final.afterHead -cne $baseline.integratedHead) { throw 'Final F1 record and baseline differ' }
    & git -C $expectedRoot merge-base --is-ancestor $final.afterHead $head
    if ($LASTEXITCODE -ne 0) { throw 'Final F1 afterHead is not an ancestor of current HEAD' }
}
Write-Output 'P1_WORKTREE_GATE_OK'
'@

$jvmSource = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare','AssertRed','AssertGreen')][string]$Mode,
    [Parameter(Mandatory)][ValidateSet('Surefire','Failsafe')][string]$Kind,
    [string]$ModulePath,
    [string]$ReportName,
    [string]$SearchRoot,
    [string[]]$ReportNames,
    [string]$StartedAtUtc
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
if (($ModulePath -and $SearchRoot) -or (-not $ModulePath -and -not $SearchRoot)) { throw 'Choose exactly one report root mode' }
$root = [IO.Path]::GetFullPath($(if ($ModulePath) { $ModulePath } else { $SearchRoot }))
$gitDirRaw = (& git rev-parse --git-dir).Trim()
$gitDir = if ([IO.Path]::IsPathRooted($gitDirRaw)) { [IO.Path]::GetFullPath($gitDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gitDirRaw)) }
$insideRepo = $root -eq $repoRoot -or $root.StartsWith($repoRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
$insideGitMetadata = $root -eq $gitDir -or $root.StartsWith($gitDir + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
if (-not $insideRepo -and -not $insideGitMetadata) { throw 'Report root escapes repo and Git metadata' }
$names = if ($ReportName) { @($ReportName) } else { @($ReportNames) }
if ($names.Count -eq 0) { throw 'At least one exact report name is required' }
foreach ($name in $names) {
    if ([IO.Path]::GetFileName($name) -ne $name -or $name -notmatch '^TEST-[^\\/]+\.xml$') { throw "Unsafe report name: $name" }
}
function Find-ExactReports([string]$name) {
    $folder = if ($Kind -eq 'Surefire') { 'surefire-reports' } else { 'failsafe-reports' }
    return @(Get-ChildItem -LiteralPath $root -Recurse -File -Filter $name -ErrorAction SilentlyContinue |
        Where-Object { $_.Directory.Name -eq $folder })
}
if ($Mode -eq 'Prepare') {
    foreach ($name in $names) {
        foreach ($file in @(Find-ExactReports $name)) { Remove-Item -LiteralPath $file.FullName }
        if (@(Find-ExactReports $name).Count -ne 0) { throw "Failed to remove old report: $name" }
    }
    Write-Output ([DateTime]::UtcNow.ToString('o'))
    return
}
$started = [DateTime]::Parse($StartedAtUtc).ToUniversalTime()
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
foreach ($name in $names) {
    $hits = @(Find-ExactReports $name)
    if ($hits.Count -ne 1) { throw "Expected one exact report for $name, got $($hits.Count)" }
    if ($hits[0].LastWriteTimeUtc -lt $started) { throw "Stale report: $name" }
    [xml]$xml = Get-Content -LiteralPath $hits[0].FullName -Raw
    $suites = @($xml.SelectNodes('/testsuite | /testsuites/testsuite'))
    if ($suites.Count -eq 0) { throw "No testsuite in $name" }
    foreach ($suite in $suites) {
        $tests += [int]$suite.tests
        $failures += [int]$suite.failures
        $errors += [int]$suite.errors
        $skipped += [int]$suite.skipped
    }
}
if ($tests -le 0) { throw 'Evidence contains zero tests' }
if ($Mode -eq 'AssertRed') {
    if (($failures + $errors) -le 0 -or $skipped -ge $tests) { throw 'RED lacks a real non-skipped test failure' }
} elseif (($failures + $errors + $skipped) -ne 0) {
    throw 'GREEN contains failure, error, or skipped tests'
}
Write-Output 'P1_JVM_EVIDENCE_OK'
'@

$vitestSource = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('Prepare','AssertRed','AssertGreen')][string]$Mode,
    [Parameter(Mandatory)][string]$ReportPath,
    [string]$StartedAtUtc
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gitDirRaw = (& git rev-parse --git-dir).Trim()
$gitDir = if ([IO.Path]::IsPathRooted($gitDirRaw)) { [IO.Path]::GetFullPath($gitDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gitDirRaw)) }
$report = [IO.Path]::GetFullPath($ReportPath)
if (-not $report.StartsWith($gitDir + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Vitest report must stay in current Git metadata' }
if ([IO.Path]::GetExtension($report) -ne '.json') { throw 'Vitest report must be JSON' }
if ($Mode -eq 'Prepare') {
    if (Test-Path -LiteralPath $report) { Remove-Item -LiteralPath $report }
    if (Test-Path -LiteralPath $report) { throw 'Failed to remove old Vitest report' }
    Write-Output ([DateTime]::UtcNow.ToString('o'))
    return
}
if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw 'Vitest report is missing' }
$started = [DateTime]::Parse($StartedAtUtc).ToUniversalTime()
if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $started) { throw 'Vitest report is stale' }
$json = Get-Content -LiteralPath $report -Raw | ConvertFrom-Json
function Assert-JsonNonNegativeInteger($Value, [string]$Label) {
    if ($Value -is [string] -or $Value -is [bool] -or $null -eq $Value -or $Value -isnot [ValueType]) { throw "$Label must be a JSON number" }
    $number = [decimal]$Value
    if ($number -lt 0 -or $number -ne [Math]::Floor($number) -or $number -gt [int]::MaxValue) { throw "$Label must be a non-negative integer" }
    return [int]$number
}
$total = Assert-JsonNonNegativeInteger $json.numTotalTests 'numTotalTests'
$failed = Assert-JsonNonNegativeInteger $json.numFailedTests 'numFailedTests'
$pending = Assert-JsonNonNegativeInteger $json.numPendingTests 'numPendingTests'
if ($json.success -isnot [bool]) { throw 'success must be a JSON boolean' }
$success = $json.success
if ($total -le 0) { throw 'Vitest discovered zero tests' }
if ($Mode -eq 'AssertRed') {
    if ($failed -le 0 -or $pending -ge $total) { throw 'Vitest RED lacks a real failure' }
} elseif ($failed -ne 0 -or $pending -ne 0 -or -not $success) {
    throw 'Vitest GREEN contains failed/pending tests or success=false'
}
Write-Output 'P1_VITEST_EVIDENCE_OK'
'@

$manifestSource = @'
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('unit','it','migration','vitest','standards','scan')][string]$Kind,
    [Parameter(Mandatory)][string]$CandidateHead,
    [Parameter(Mandatory)][string]$F1Head,
    [Parameter(Mandatory)][string]$WindowStartedAtUtc,
    [Parameter(Mandatory)][string[]]$ArtifactPaths,
    [Parameter(Mandatory)][string]$SummaryJson
)
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$head = (& git rev-parse HEAD).Trim()
if ($CandidateHead -cnotmatch '^[0-9a-f]{40}$' -or $F1Head -cnotmatch '^[0-9a-f]{40}$' -or $head -cne $CandidateHead) { throw 'Evidence HEAD binding is invalid' }
$window = [DateTimeOffset]::Parse($WindowStartedAtUtc)
if ($WindowStartedAtUtc -cnotmatch '(?:Z|\+00:00)$' -or $window.Offset -ne [TimeSpan]::Zero) { throw 'Evidence window must be explicit UTC' }
$summary = $SummaryJson | ConvertFrom-Json
if ($summary -isnot [pscustomobject] -or $summary.status -isnot [string] -or $summary.status -cne 'PASS') { throw 'Evidence summary must be a PASS JSON object' }
$gitDirRaw = (& git rev-parse --git-dir).Trim()
$gitDir = if ([IO.Path]::IsPathRooted($gitDirRaw)) { [IO.Path]::GetFullPath($gitDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gitDirRaw)) }
function Test-Inside([string]$Path, [string]$Root) {
    return $Path.Equals($Root, [StringComparison]::OrdinalIgnoreCase) -or $Path.StartsWith($Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)
}
$artifacts = @()
$seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach ($artifactPath in $ArtifactPaths) {
    $full = [IO.Path]::GetFullPath($artifactPath)
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { throw "Evidence artifact is missing: $full" }
    if (Test-Inside $full $gitDir) { $scope='git-metadata'; $scopeRoot=$gitDir }
    elseif (Test-Inside $full $repoRoot) { $scope='repo'; $scopeRoot=$repoRoot }
    else { throw "Evidence artifact escapes allowed scopes: $full" }
    $relative = [IO.Path]::GetRelativePath($scopeRoot, $full).Replace([IO.Path]::DirectorySeparatorChar, '/')
    if ([IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)' -or -not $seen.Add($scope + ':' + $relative)) { throw "Unsafe or duplicate evidence artifact: $relative" }
    $item = Get-Item -LiteralPath $full
    if ($item.LastWriteTimeUtc -lt $window.UtcDateTime) { throw "Evidence artifact predates acceptance window: $relative" }
    $artifacts += [ordered]@{
        pathScope=$scope; relativePath=$relative
        sha256=(Get-FileHash -LiteralPath $full -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes=[long]$item.Length; lastWriteUtc=$item.LastWriteTimeUtc.ToString('o')
    }
}
if ($artifacts.Count -le 0) { throw 'At least one evidence artifact is required' }
$core = [ordered]@{
    schemaVersion=1; kind=$Kind; candidateHead=$CandidateHead; f1Head=$F1Head
    windowStartedAtUtc=$WindowStartedAtUtc; artifacts=$artifacts; summary=$summary
}
$targetRelative = "p1-evidence/$CandidateHead/$Kind.manifest.json"
$target = [IO.Path]::GetFullPath((Join-Path $gitDir $targetRelative))
[void](New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force)
if (Test-Path -LiteralPath $target -PathType Leaf) {
    $existing = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
    $fields = @($existing.PSObject.Properties | ForEach-Object { $_.Name })
    $expectedFields = @('schemaVersion','kind','candidateHead','f1Head','windowStartedAtUtc','generatedAtUtc','artifacts','summary')
    if ($existing -isnot [pscustomobject] -or $fields.Count -ne $expectedFields.Count) { throw 'Existing evidence manifest schema drifted' }
    for ($i=0; $i -lt $expectedFields.Count; $i++) { if ($fields[$i] -cne $expectedFields[$i]) { throw 'Existing evidence manifest field order drifted' } }
    $existingCore = [ordered]@{
        schemaVersion=$existing.schemaVersion; kind=$existing.kind; candidateHead=$existing.candidateHead; f1Head=$existing.f1Head
        windowStartedAtUtc=$existing.windowStartedAtUtc; artifacts=@($existing.artifacts); summary=$existing.summary
    }
    if (($existingCore | ConvertTo-Json -Depth 8 -Compress) -cne ($core | ConvertTo-Json -Depth 8 -Compress)) { throw 'Existing evidence manifest payload differs; never overwrite it' }
} else {
    $document = [ordered]@{
        schemaVersion=1; kind=$Kind; candidateHead=$CandidateHead; f1Head=$F1Head
        windowStartedAtUtc=$WindowStartedAtUtc; generatedAtUtc=[DateTime]::UtcNow.ToString('o')
        artifacts=$artifacts; summary=$summary
    }
    $temp = Join-Path (Split-Path -Parent $target) ('.p1-evidence-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    [IO.File]::WriteAllText($temp, ($document | ConvertTo-Json -Depth 8 -Compress), [Text.UTF8Encoding]::new($false))
    try { [IO.File]::Move($temp, $target) } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
}
$readback = Get-Content -LiteralPath $target -Raw | ConvertFrom-Json
if ($readback.kind -cne $Kind -or $readback.candidateHead -cne $CandidateHead -or $readback.f1Head -cne $F1Head) { throw 'Evidence manifest readback drifted' }
$sha = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "P1_EVIDENCE_MANIFEST_OK=$targetRelative|$sha"
'@

foreach ($entry in @(
    @{ Path=$gate; Source=$worktreeSource },
    @{ Path=$jvmGate; Source=$jvmSource },
    @{ Path=$vitestGate; Source=$vitestSource },
    @{ Path=$manifestGate; Source=$manifestSource }
)) {
    $tokens = $null
    $errors = $null
    [void][Management.Automation.Language.Parser]::ParseInput($entry.Source, [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "Helper AST failed: $($entry.Path)" }
    [IO.File]::WriteAllText($entry.Path, $entry.Source, [Text.UTF8Encoding]::new($false))
}
function Assert-Rejected([string]$Label, [scriptblock]$Action) {
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw "Negative bootstrap self-test unexpectedly passed: $Label" }
}
function Write-JvmSelfReport([string]$Path, [int]$Tests, [int]$Failures, [int]$Errors, [int]$Skipped) {
    [IO.File]::WriteAllText($Path, "<testsuite tests=`"$Tests`" failures=`"$Failures`" errors=`"$Errors`" skipped=`"$Skipped`"></testsuite>", [Text.UTF8Encoding]::new($false))
}
function Invoke-IndependentGateConsumer([string]$HelperPath) {
    $sentinel = & $HelperPath -RepoRoot $repoRoot -ExpectedPhase independent
    if ($sentinel -cne 'P1_WORKTREE_GATE_OK') { throw 'Consumer did not receive the worktree sentinel' }
    return 'P1_GATE_CONSUMER_OK'
}
if ((Invoke-IndependentGateConsumer $gate) -cne 'P1_GATE_CONSUMER_OK') { throw 'Worktree helper consumer self-test failed' }
if ((& $gate -RepoRoot $repoRoot -SelfTest) -ne 'P1_WORKTREE_GATE_SELFTEST_OK') { throw 'Worktree strict self-test sentinel missing' }
Assert-Rejected 'unknown expected phase' { & $gate -RepoRoot $repoRoot -ExpectedPhase future }
$missingHelper = Resolve-GitMetadataPath ('.p1-helper-missing-' + [Guid]::NewGuid().ToString('N') + '.ps1')
Assert-Rejected 'missing helper consumer' { Invoke-IndependentGateConsumer $missingHelper }

$selfRoot = Resolve-GitMetadataPath 'p1-jvm-selftest'
[void](New-Item -ItemType Directory -Path $selfRoot -Force)
$selfReport = 'TEST-p1.HelperSelfTest.xml'
$surefireDir = Join-Path $selfRoot 'target/surefire-reports'
[void](New-Item -ItemType Directory -Path $surefireDir -Force)
$selfReportPath = Join-Path $surefireDir $selfReport

$started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport
Assert-Rejected 'JVM missing report' { & $jvmGate -Mode AssertGreen -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport -StartedAtUtc $started }
$started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport
Write-JvmSelfReport $selfReportPath 1 0 0 0
[IO.File]::SetLastWriteTimeUtc($selfReportPath, ([DateTime]::Parse($started).ToUniversalTime().AddMinutes(-1)))
Assert-Rejected 'JVM stale report' { & $jvmGate -Mode AssertGreen -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport -StartedAtUtc $started }
foreach ($case in @(
    @{ Label='JVM zero tests'; Mode='AssertGreen'; Values=@(0,0,0,0) },
    @{ Label='JVM all skipped RED'; Mode='AssertRed'; Values=@(1,0,0,1) },
    @{ Label='JVM GREEN failure'; Mode='AssertGreen'; Values=@(1,1,0,0) },
    @{ Label='JVM GREEN error'; Mode='AssertGreen'; Values=@(1,0,1,0) },
    @{ Label='JVM GREEN skipped'; Mode='AssertGreen'; Values=@(1,0,0,1) }
)) {
    $started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport
    Write-JvmSelfReport $selfReportPath $case.Values[0] $case.Values[1] $case.Values[2] $case.Values[3]
    Assert-Rejected $case.Label { & $jvmGate -Mode $case.Mode -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport -StartedAtUtc $started }
}
$started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport
Write-JvmSelfReport $selfReportPath 1 0 0 0
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM GREEN self-test failed' }
$started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport
Write-JvmSelfReport $selfReportPath 1 1 0 0
if ((& $jvmGate -Mode AssertRed -Kind Surefire -SearchRoot $selfRoot -ReportNames $selfReport -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM RED self-test failed' }

$vitestSelf = Resolve-GitMetadataPath 'p1-vitest-selftest.json'
$started = & $vitestGate -Mode Prepare -ReportPath $vitestSelf
Assert-Rejected 'Vitest missing report' { & $vitestGate -Mode AssertGreen -ReportPath $vitestSelf -StartedAtUtc $started }
$started = & $vitestGate -Mode Prepare -ReportPath $vitestSelf
[IO.File]::WriteAllText($vitestSelf, '{"numTotalTests":1,"numFailedTests":0,"numPendingTests":0,"success":true}')
[IO.File]::SetLastWriteTimeUtc($vitestSelf, ([DateTime]::Parse($started).ToUniversalTime().AddMinutes(-1)))
Assert-Rejected 'Vitest stale report' { & $vitestGate -Mode AssertGreen -ReportPath $vitestSelf -StartedAtUtc $started }
foreach ($case in @(
    @{ Label='Vitest zero tests'; Json='{"numTotalTests":0,"numFailedTests":0,"numPendingTests":0,"success":true}' },
    @{ Label='Vitest pending'; Json='{"numTotalTests":1,"numFailedTests":0,"numPendingTests":1,"success":true}' },
    @{ Label='Vitest failed'; Json='{"numTotalTests":1,"numFailedTests":1,"numPendingTests":0,"success":false}' },
    @{ Label='Vitest success false'; Json='{"numTotalTests":1,"numFailedTests":0,"numPendingTests":0,"success":false}' },
    @{ Label='Vitest string counts'; Json='{"numTotalTests":"1","numFailedTests":"0","numPendingTests":"0","success":true}' },
    @{ Label='Vitest string boolean'; Json='{"numTotalTests":1,"numFailedTests":0,"numPendingTests":0,"success":"false"}' }
)) {
    $started = & $vitestGate -Mode Prepare -ReportPath $vitestSelf
    [IO.File]::WriteAllText($vitestSelf, $case.Json, [Text.UTF8Encoding]::new($false))
    Assert-Rejected $case.Label { & $vitestGate -Mode AssertGreen -ReportPath $vitestSelf -StartedAtUtc $started }
}
$started = & $vitestGate -Mode Prepare -ReportPath $vitestSelf
[IO.File]::WriteAllText($vitestSelf, '{"numTotalTests":1,"numFailedTests":0,"numPendingTests":0,"success":true}')
if ((& $vitestGate -Mode AssertGreen -ReportPath $vitestSelf -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest GREEN self-test failed' }
$started = & $vitestGate -Mode Prepare -ReportPath $vitestSelf
[IO.File]::WriteAllText($vitestSelf, '{"numTotalTests":1,"numFailedTests":1,"numPendingTests":0,"success":false}')
if ((& $vitestGate -Mode AssertRed -ReportPath $vitestSelf -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest RED self-test failed' }
Write-Output 'P1_BOOTSTRAP_STRICT_SELFTEST_OK'
Write-Output 'P1_BOOTSTRAP_OK'
```

### RED

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeContractTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeContractTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeContractTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeContractTest' test
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 2：建立 RuoYi 领域对象、Mapper 与 05 注释草案（F1 前）

### 最小任务卡

- **单一目标／不做：** 建立贫血 Entity、根目录枚举、Mapper 和数据库设计清单；不连接数据库、不执行 05。
- **权威来源：** `docs/DOMAIN_MODEL.md`、RuoYi skill、generator 模板和仓库相似模块。
- **风险／触发：** 红；Entity、状态、审计字段或草案位置偏差会污染后续所有层。
- **所有权／数据范围：** P1 owner；知识聚合及 migration discovery 外的 `20260728_05_p1_knowledge.sql.draft`。
- **依赖／人员／并发：** 依赖 Task 1；可与 Task 5 并行，协作者不超过两人。
- **允许影响：** core domain/mapper、领域文档和 discovery 外草案；禁止创建正式 `.sql`、执行 DDL/DML 或运行迁移。
- **成功／反向验收：** 领域规则测试 RED→GREEN；出现可执行 05、错误状态迁移或 HTTP 依赖均失败。
- **固定输出：** 8 个 Entity、4 个根目录枚举、对应 Mapper、`KnowledgeDomainRulesTest`、`20260728_05_p1_knowledge.sql.draft`。

### 实现步骤

1. 先阅读 generator Entity/Mapper 模板和两个相似业务模块，记录 `BaseEntity`、`@TableName`、`@TableId`、乐观锁和逻辑删除惯例。
2. 测试覆盖 `draft -> reviewing -> published -> retired` 唯一允许状态、已发布版本不可原地修改、绑定修订号和导入状态。
3. Entity 只保存字段与框架注解；业务编排留给 Service。Mapper 继承 `BaseMapperPlus`，不得依赖 HTTP VO。
4. `20260728_05_p1_knowledge.sql.draft` 只写拟建表/索引/唯一键/字典/菜单/权限的注释或评审文本；它必须位于 migration discovery 外。F1 前正式 `20260728_05_p1_knowledge.sql` 必须不存在，避免空迁移或 checksum 污染。
5. 在 `docs/DOMAIN_MODEL.md` 登记知识版本不可变、快照不可变和表关系草案。

### RED

先创建可编译 Entity/枚举/Mapper skeleton，再写 `KnowledgeDomainRulesTest`；运行 `Prepare → Maven → AssertRed`，失败必须来自状态/不可变断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeDomainRulesTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeDomainRulesTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeDomainRulesTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeDomainRulesTest' test
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 3：实现发布纯状态机与发布规则（F1 前）

### 最小任务卡

- **单一目标／不做：** 实现 Entity 上方的无 IO 发布状态机与 `validateForPublication` 纯规则；不声明最终目录/发布 Service，不编译依赖 P0-C actor DTO，不注入方向服务、不写库、不实现 HTTP。
- **权威来源：** `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md` 和本计划 F1 前后切片。
- **风险／触发：** 红；越级状态、原地修改或提前绑定 F1 类型会破坏生成可追溯性与独立切片。
- **所有权／数据范围：** P1 owner；`KnowledgeVersion` 状态、`KnowledgePublicationServiceImpl` 的纯规则 skeleton；fixture 只存在于测试源码。
- **依赖／人员／并发：** 依赖 Task 2；可与 Task 5 并行，协作者不超过两人。
- **允许影响：** core Entity、service.impl 的纯规则 skeleton 与测试 fixture；禁止 `TaskInitiatorDTO`、最终 Service 签名、真实方向 Service、Mapper IO、端层登录态、BO/VO 和独立发布校验类型。
- **成功／反向验收：** 状态机真实 RED→GREEN；越级状态、已发布正文修改、P0-C 类型 import 或独立校验类型均失败。
- **固定输出：** 无 IO 状态迁移、私有 `validateForPublication` 规则和 `KnowledgePublicationServiceTest`；最终阶段内接口留给 Task 8。

### 实现步骤

1. `KnowledgePublicationServiceImpl` 先只提供可编译纯规则 skeleton；不得创建 `IKnowledgeCatalogService`／`IKnowledgePublicationService` 最终签名，也不得 import、复制或用本域 record 仿造 `TaskInitiatorDTO`。
2. 私有 `validateForPublication(KnowledgeVersion version)` 只校验领域、状态、方向字段形状、正文和版本不可变条件；不注入 `IDirectionCatalogService`，不调用 `currentPublishedCatalog()`。
3. package-private 纯状态内核的 main 源码签名只接收 Entity、事件、`expectedRevision`、`Long reviewerId` 与 `Instant reviewedAt` 等 primitive/JDK 值，绝不能引用测试类型。测试内 `PublicationAuditFixture` 只能保存固定 reviewer/time，并在调用处解包为 `reviewerId`、`reviewedAt`；fixture 不承担 actor 身份判断、不进入 main 源码，也不得替代 P0-C 类型。
4. 无效迁移、重复提交、过期修订、引用中和已发布正文修改都必须失败。最终 Service 接口、`TaskInitiatorDTO` actor 校验、服务端 `Clock`、事务、Mapper、乐观锁和真实方向交叉校验全部移至 Task 8。

`KnowledgePublicationServiceTest` 在 F1 前只使用测试文件内的 `PublicationAuditFixture`，每次调用都显式传入 `fixture.reviewerId()` 与 `fixture.reviewedAt()`，证明纯状态与发布规则；main 源码零 `PublicationAuditFixture`，测试源码不得 import `org.dromara.aivideo.task.dto.TaskInitiatorDTO`。Task 8 在 F1 后扩展同一测试，届时才显式构造真实 P0-C DTO 并验证 actor/time。

### RED

先创建可编译纯规则 skeleton，再写 `KnowledgePublicationServiceTest`；运行 `Prepare → Maven → AssertRed`，失败必须来自状态、不可变或发布规则断言，不能来自缺失 P0-C 类型。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgePublicationServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgePublicationServiceTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgePublicationServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgePublicationServiceTest' test
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 4：实现确定性路由与不可变快照（F1 前）

### 最小任务卡

- **单一目标／不做：** 实现两个稳定 ServiceImpl 内的无 IO 路由、冻结与 canonical hash 算法，并提前完成 ZIP 安全纯测试；不查 Mapper、不落库、不读取登录态。
- **权威来源：** 本计划 0.1、并行交付规格 F2 契约、`docs/DOMAIN_MODEL.md`。
- **风险／触发：** 红；非确定路由、内容未冻结或 IO 越界会直接污染 P2/P3。
- **所有权／数据范围：** P1 owner；只使用显式 candidate/rule/material fixture，不读取真实发布目录。
- **依赖／人员／并发：** 依赖 Task 2–3；可与 Task 5 并行，协作者不超过两人。
- **允许影响：** 两个稳定 ServiceImpl 的私有纯方法、两个内部 DTO、infra ZIP reader 与纯测试；禁止 Mapper 查询、`@TableId`、INSERT、历史读取和额外稳定 DTO。
- **成功／反向验收：** 路由、快照和 ZIP 安全各自真实 RED→GREEN；非 A/B/C、hash 漂移或攻击样本放行均失败。
- **固定输出：** 两个 ServiceImpl 的纯算法、`KnowledgeTemplateCandidateDTO`、`RequiredKnowledgeRuleDTO`、三类 unit 测试。

### 实现步骤

1. `KnowledgeTemplateCandidateDTO` 和 `RequiredKnowledgeRuleDTO` 只保存已解析 fixture 的稳定 ID、代码、版本引用、实际片段和顺序；测试不得复制 P0-C 类型。
2. `KnowledgeRoutingServiceImpl` 的私有 `resolveVideoType`、`validateRequiredRules`、`toPlan`、`enforceContextBudget` 只接收不可变 fixture；通过反射或同包测试入口验证，不访问 Mapper。
3. A/B/C 恰好三项且顺序固定；`candidateCode`、`planCode`、完整模板/角度/差异化三元组唯一。优先不同模板，模板不足允许复用模板补位。
4. `KnowledgeSnapshotServiceImpl` 的私有 `freezePayload` 和 `toCanonicalJson` 按固定字段顺序冻结 `rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、有序 route、accepted facts 与 material reference/excerpt，计算 SHA-256；同一 fixture 字节一致，上述任一标量、集合顺序、引用或正文逐项变化都必须改 hash。
5. `route/create/getByRootTaskId` 的真实 Mapper 查询、幂等 INSERT、历史读取和 `@TableId` 只创建可编译 RED skeleton，统一在 Task 8 完成。ZIP 测试覆盖路径穿越、绝对路径、符号链接、加密/重复条目、炸弹、条目数/单项/总大小/深度/编码限制。

### 路由 RED

先创建两个内部 DTO 和可编译路由 skeleton，再写路由测试；`AssertRed` 必须确认是 A/B/C、补位或预算断言失败。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeRoutingServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeRoutingServiceTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### 路由 GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeRoutingServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeRoutingServiceTest' test
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

### 快照 RED

先创建可编译快照 skeleton，再写冻结正文/顺序/hash 测试；固定测试名 `canonicalHashChangesForEveryImmutablePayloadField`，逐项改变 `rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、`route`、`acceptedFacts`、`knowledgeMaterials` 并断言 hash 全部变化，同时证明相同有序 payload 字节稳定；`AssertRed` 必须确认是 payload/hash 断言失败。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeSnapshotServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeSnapshotServiceTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### 快照 GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeSnapshotServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeSnapshotServiceTest' test
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

### ZIP 安全纯测试 RED（仍属 F1 前）

先创建可编译 `SafeZipKnowledgeArchiveReader` skeleton，再创建攻击 fixture 测试；RED 必须来自穿越/炸弹/条目数或编码断言，不得来自依赖或编译失败。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-infra'
$report = 'TEST-org.dromara.aivideo.knowledge.SafeZipKnowledgeArchiveReaderTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-infra' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SafeZipKnowledgeArchiveReaderTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'ZIP RED must fail by a security assertion' }
```

### ZIP 安全纯测试 GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-infra'
$report = 'TEST-org.dromara.aivideo.knowledge.SafeZipKnowledgeArchiveReaderTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-infra' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SafeZipKnowledgeArchiveReaderTest' test
if ($LASTEXITCODE -ne 0) { throw 'ZIP GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 5：完成平台前端独立切片与状态矩阵（F1 前）

### 最小任务卡

- **单一目标／不做：** 完成类型、Mock、ProComponents 页面和逐文件状态测试；不连接真实后端。
- **权威来源：** `docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md`、`docs/API_CONTRACT.md`、Ant Design 官方资料。
- **风险／触发：** 中；状态遗漏、宽泛测试或 Mock 泄入生产会导致联调返工。
- **所有权／数据范围：** P1 前端 owner；仅知识管理页面、API 类型、Mock 和测试。
- **依赖／人员／并发：** 仅依赖 Task 1 稳定字段；可与 Task 2–4 并行，协作者不超过两人。
- **允许影响：** 平台前端知识目录；`package.json`/lock 仅在确实缺依赖时条件修改，禁止无条件 install 或暂存。
- **成功／反向验收：** 两文件各自产生 fresh Vitest JSON 且 23 个测试名逐项锚定；宽泛断言或缺态均失败。
- **固定输出：** API 类型、`mock/aivideo-knowledge.ts`、页面/抽屉、两个测试文件和独立切片证据。

### 状态矩阵

`index.test.tsx` 必须逐项包含真实 `it/test`：

1. `loading`
2. `initial-empty`
3. `search-empty`
4. `pagination-and-refresh`
5. `network-or-5xx-retry`
6. `401-single-logout`
7. `403-forbidden`
8. `draft-review-publish`
9. `reference-check-and-confirm`
10. `revision-conflict`
11. `retire-confirmation`

`KnowledgeImportReviewDrawer.test.tsx` 必须逐项包含：

1. `file-validation`
2. `parsing`
3. `conflict`
4. `failed`
5. `duplicate-submit-guard`
6. `cancel-is-not-failure`
7. `queued`
8. `running`
9. `success`
10. `failure`
11. `cancel`
12. `retry`

P1 是免费导入，不展示额度、余额或价格状态。

以上 23 项中的每一项都必须是直接 `it("state", callback)`／`test("state", callback)`，callback 的第一条可执行语句必须是唯一的 `expect.assertions(N)`，且 `N` 为大于零的整数字面量；禁止 `.skip`、`.todo`、`.only`、`.each`、`xit`、`xtest`、动态测试名或只做渲染不执行断言的占位测试。

### 实现步骤

1. 先查询 Ant Design/ProComponents 官方组件文档，采用 `ProTable`、`ProForm`、`ProDescriptions` 或 `ProList` 的既有项目模式。
2. 类型集中在 `api/aivideo/knowledge/types.ts`；受控共享入口只改 `src/pages/dynamicPage.tsx`；页面不得散落接口路径、状态字符串或错误码。
3. `mock/aivideo-knowledge.ts` 明确模拟 loading、空、401、403、5xx、分页、修订冲突和所有导入状态。
4. 每个测试文件单独 RED/GREEN；不得用同一个宽泛测试代替矩阵项。

### 列表页 RED

先创建能渲染的页面 skeleton，再逐项写 11 个命名测试；`AssertRed` 必须来自缺失页面状态。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-index-red-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/index.test.tsx' --reporter=json "--outputFile=$report"
$testExit = $LASTEXITCODE
if ((& $vitestGate -Mode AssertRed -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### 列表页 GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-index-green-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/index.test.tsx' --reporter=json "--outputFile=$report"
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $vitestGate -Mode AssertGreen -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
$testSource = Get-Content -LiteralPath (Join-Path $ui 'src/pages/aivideo/knowledge/index.test.tsx') -Raw
$expected = @('loading','initial-empty','search-empty','pagination-and-refresh','network-or-5xx-retry','401-single-logout','403-forbidden','draft-review-publish','reference-check-and-confirm','revision-conflict','retire-confirmation')
foreach ($name in $expected) {
    $pattern = '(?:it|test)\s*\(\s*[''"]' + [regex]::Escape($name) + '[''"]'
    if ([regex]::Matches($testSource, $pattern).Count -ne 1) { throw "Missing exact index state test: $name" }
}
```

### 导入抽屉 RED

先创建能渲染的抽屉 skeleton，再逐项写 12 个命名测试；`AssertRed` 必须来自缺失导入状态。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-import-drawer-red-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx' --reporter=json "--outputFile=$report"
$testExit = $LASTEXITCODE
if ((& $vitestGate -Mode AssertRed -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by assertion' }
```

### 导入抽屉 GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-import-drawer-green-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx' --reporter=json "--outputFile=$report"
if ($LASTEXITCODE -ne 0) { throw 'GREEN command failed' }
if ((& $vitestGate -Mode AssertGreen -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
$testSource = Get-Content -LiteralPath (Join-Path $ui 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx') -Raw
$expected = @('file-validation','parsing','conflict','failed','duplicate-submit-guard','cancel-is-not-failure','queued','running','success','failure','cancel','retry')
foreach ($name in $expected) {
    $pattern = '(?:it|test)\s*\(\s*[''"]' + [regex]::Escape($name) + '[''"]'
    if ([regex]::Matches($testSource, $pattern).Count -ne 1) { throw "Missing exact import state test: $name" }
}
```

---

## Task 6：核验 F1、隔离 rebase 并执行 05 迁移（F1 后）

### 最小任务卡

- **单一目标／不做：** 从已审核的不可变原 F1 handoff 与向前 addendum 进入集成阶段并落地 05；不在共享工作区操作、不接受缺字段／漂移 SHA／未独立审核的任一证据。
- **权威来源：** P0-C 原 F1 handoff、F1 contract addendum／三项 evidence／独立 review、迁移链规范、`LocalIntegrationEnvironment` 和本计划 0.2–0.3。
- **风险／触发：** 红；错误基线、未确认 revision 映射或迁移乱序会破坏三人并行成果。
- **所有权／数据范围：** P1 集成 owner；P1 专属 worktree、当前 Git metadata、受控测试库和 05 文件。
- **依赖／人员／并发：** Task 1–5 已分别提交且全绿；契约 owner 已确认 revision 映射；rebase 窗口只允许 P1 owner 操作。
- **允许影响：** P1 分支历史、05、测试库；禁止修改 P0-C worktree、开发库或生产库。
- **成功／反向验收：** 原 F1 与 addendum 全字段、顺序、类型、live SHA、祖先、独立 review、方向快照八 component 精确源码签名、before/after/base 与 `01→02→03→04→04a→05` 迁移真 RED→GREEN；任一缺失即阻断。
- **固定输出：** `p1-f1-integration.json`、可执行 05、`P1KnowledgeMigrationIT` 和 fresh Failsafe 证据。

### 原 F1 handoff 与 addendum 强制字段

由 `AI_VIDEO_P0C_F1_HANDOFF` 指向只读 JSON。顶层必须是单一 object，字段及顺序精确为：`f1Head`、`fullF1Ready`、`f0Head`、`p0bCandidateHead`、`p0cAcceptanceWindowStart`、`p0cAcceptanceWindowEnd`、`owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`reviewCompletedAtUtc`、`migrations`、`sharedFiles`、`sharedFileHandoffTarget`、`sharedFileBaselineHead`、`downstreamRebaseOwners`、`stableServices`、`internalSpis`、`stableDomainAndDtos`、`knowledgeImportRevisionMapping`、`capturedAtUtc`。拒绝缺字段、额外字段、字段大小写/顺序漂移及 JSON 类型冒充。

- `fullF1Ready` 必须是 JSON boolean `true`；所有标量必须是 JSON string。所有提交值为小写 40 hex、可解析，且满足 `f0Head → p0bCandidateHead → f1Head`；`p0cAcceptanceWindowStart=p0bCandidateHead`，`p0cAcceptanceWindowEnd=reviewedHead=sharedFileBaselineHead=f1Head`。P0-C 计划事实源提交 `713c15c2198d489d960a23ba3ec325c95bc92261` 必须是 `f1Head` 的祖先。
- `owner`、`reviewer` 必须是非空 JSON string；独立性只在比较时按 trim + 大小写不敏感归一化，不能额外要求上游已经 trim。`reviewStatus` 精确为 `PASS`。`reviewCompletedAtUtc`、`capturedAtUtc` 必须显式以 `Z` 或 `+00:00` 结束且 offset 为零。
- 原 handoff 的历史 `migrations` 精确有序为 02/03/04 的完整项目相对路径且不得回写；当前完整迁移链必须由 addendum 追加 `04a` 后成为 `01/02/03/04/04a/05`。`sharedFiles` 精确有序为 P0-C 冻结的六个共享文件；`sharedFileHandoffTarget` 精确为 `P2`；`downstreamRebaseOwners` 仅有 `P1/P2/P3`，值分别为 `主计划 Task 5/6/7`。
- `stableServices` 精确有序为三个全限定名；`internalSpis` 精确有序为一个 handler；`stableDomainAndDtos` 精确有序为 `AiTaskType`、`DirectionCatalogSnapshotDTO` 和六个 task DTO。还必须读取真实 `DirectionCatalogSnapshotDTO.java`，机械核对八个 component 的精确顺序以及 `industryCatalogVersion`／`purposeCatalogVersion` 正数、`durationRuleVersion` 非空构造校验；旧五 component 源码或 fixture 均阻断。
- `knowledgeImportRevisionMapping` 仅有九个 JSON string：`status`、`contractOwner`、`reviewer`、`reviewStatus`、`f1Head`、`confirmedAtUtc`、`draftRevisionSource`、`branchRevisionSource`、`generationContextRevisionSource`。其值满足 `CONFIRMED`、`PASS`、同一 F1、独立 owner/reviewer、显式 UTC 和三个非空来源。

只验证契约 owner 给出的映射，不在 P1 中自行设定业务值。任何字段、类型、值或祖先关系不满足，立即阻断本 Task、真实导入和 F2。

由 `AI_VIDEO_P0C_F1_ADDENDUM` 指向只读 addendum。其 exact 12-field 顶层、
`schemaAddendum` 精确九字段和值、三项 evidence 的 `kind/path/sha256` 固定顺序，以及 exact 9-field
review 必须逐项按 0.2 节验证。`amendmentHead` 是本计划后续统一使用的活动 `f1Head`；
`originalF1Head` 必须与原 handoff 的 `f1Head` 相等且为 amendment 祖先。
P1 必须实时计算 `originalF1HandoffSha256`、addendum SHA、三个 manifest SHA 和 manifest 内 artifact SHA；
不得信任 JSON 自报哈希。原 handoff、addendum、evidence、review 任一缺失、可覆盖、越界、额外字段、错误顺序、类型冒充、非 UTC、future review 或 SHA 漂移均阻断 rebase。

### 隔离 rebase 与记录

以下命令是 Task 6 到达时的未来执行步骤；本计划整改阶段不得运行：

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
if (-not (Test-Path -LiteralPath $gate)) { throw 'P1 worktree gate is missing' }
Set-Location $repoRoot
function Resolve-Metadata([string]$Name) {
    $raw = (& git rev-parse --git-path $Name).Trim()
    if ([IO.Path]::IsPathRooted($raw)) { return [IO.Path]::GetFullPath($raw) }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $raw))
}
function Assert-ExactFields($Value, [string[]]$Expected, [string]$Label) {
    if ($Value -isnot [pscustomobject]) { throw "$Label must be one JSON object" }
    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    if ($actual.Count -ne $Expected.Count) { throw "$Label field count drifted" }
    for ($i = 0; $i -lt $Expected.Count; $i++) {
        if (-not [string]::Equals($actual[$i], $Expected[$i], [StringComparison]::Ordinal)) { throw "$Label fields/order drifted at index $i" }
    }
}
function Assert-String($Value, [string]$Label) {
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value)) { throw "$Label must be a nonblank JSON string" }
}
function Assert-StringEquals($Value, [string]$Expected, [string]$Label) {
    Assert-String $Value $Label
    if (-not [string]::Equals($Value, $Expected, [StringComparison]::Ordinal)) { throw "$Label value drifted" }
}
function Assert-BoolEquals($Value, [bool]$Expected, [string]$Label) {
    if ($Value -isnot [bool] -or -not $Value.Equals($Expected)) { throw "$Label must be the exact JSON boolean $Expected" }
}
function Assert-Sha40($Value, [string]$Label) { Assert-String $Value $Label; if ($Value -cnotmatch '^[0-9a-f]{40}$') { throw "$Label must be lowercase 40-hex" } }
function Assert-Sha256($Value, [string]$Label) { Assert-String $Value $Label; if ($Value -cnotmatch '^[0-9a-f]{64}$') { throw "$Label must be lowercase SHA-256" } }
function Assert-Utc($Value, [string]$Label) {
    Assert-String $Value $Label
    if ($Value -cnotmatch '(?:Z|\+00:00)$') { throw "$Label must explicitly end in Z or +00:00" }
    $parsed = [DateTimeOffset]::Parse($Value, [Globalization.CultureInfo]::InvariantCulture)
    if ($parsed.Offset -ne [TimeSpan]::Zero) { throw "$Label must be UTC" }
}
function Assert-StringArray($Value, [string[]]$Expected, [string]$Label) {
    if ($Value -isnot [System.Array]) { throw "$Label must be one JSON array" }
    if ($Value.Count -ne $Expected.Count) { throw "$Label count drifted" }
    for ($i = 0; $i -lt $Expected.Count; $i++) {
        if ($Value[$i] -isnot [string] -or -not [string]::Equals($Value[$i], $Expected[$i], [StringComparison]::Ordinal)) { throw "$Label item $i drifted or is not a string" }
    }
}
function Assert-Ancestor([string]$Ancestor, [string]$Descendant, [string]$Label) {
    & git cat-file -e "$Ancestor^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "$Label ancestor commit is unavailable" }
    & git cat-file -e "$Descendant^{commit}"
    if ($LASTEXITCODE -ne 0) { throw "$Label descendant commit is unavailable" }
    & git merge-base --is-ancestor $Ancestor $Descendant
    if ($LASTEXITCODE -ne 0) { throw "$Label ancestry failed" }
}
function Get-TransactionId([System.Collections.IDictionary]$Payload) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($Payload | ConvertTo-Json -Depth 5 -Compress))
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
}
function Write-CreateNewJson([string]$Path, [System.Collections.IDictionary]$Payload, [string]$Label) {
    if (Test-Path -LiteralPath $Path) { throw "$Label already exists" }
    $temp = Join-Path (Split-Path -Parent $Path) ('.p1-create-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    [IO.File]::WriteAllText($temp, ($Payload | ConvertTo-Json -Depth 8 -Compress), [Text.UTF8Encoding]::new($false))
    try { [IO.File]::Move($temp, $Path) } finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp } }
}
$handoffSource = $env:AI_VIDEO_P0C_F1_HANDOFF
if ([string]::IsNullOrWhiteSpace($handoffSource)) { throw 'AI_VIDEO_P0C_F1_HANDOFF is required' }
$handoffPath = [IO.Path]::GetFullPath($handoffSource)
if (-not (Test-Path -LiteralPath $handoffPath -PathType Leaf)) { throw 'F1 handoff file is missing' }
$handoff = Get-Content -LiteralPath $handoffPath -Raw | ConvertFrom-Json
Assert-ExactFields $handoff @(
    'f1Head','fullF1Ready','f0Head','p0bCandidateHead','p0cAcceptanceWindowStart','p0cAcceptanceWindowEnd','owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','migrations','sharedFiles','sharedFileHandoffTarget','sharedFileBaselineHead','downstreamRebaseOwners','stableServices','internalSpis','stableDomainAndDtos','knowledgeImportRevisionMapping','capturedAtUtc'
) 'P0-C F1 handoff'
Assert-BoolEquals $handoff.fullF1Ready $true 'handoff.fullF1Ready'
foreach ($name in @('f1Head','f0Head','p0bCandidateHead','p0cAcceptanceWindowStart','p0cAcceptanceWindowEnd','reviewedHead','sharedFileBaselineHead')) { Assert-Sha40 $handoff.$name "handoff.$name" }
foreach ($name in @('owner','reviewer','reviewStatus','reviewCompletedAtUtc','sharedFileHandoffTarget','capturedAtUtc')) { Assert-String $handoff.$name "handoff.$name" }
Assert-StringEquals $handoff.reviewStatus 'PASS' 'handoff.reviewStatus'
Assert-StringEquals $handoff.sharedFileHandoffTarget 'P2' 'handoff.sharedFileHandoffTarget'
if ($handoff.owner.Trim().Equals($handoff.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'F1 reviewer must be independent after normalized comparison' }
Assert-Utc $handoff.reviewCompletedAtUtc 'handoff.reviewCompletedAtUtc'
Assert-Utc $handoff.capturedAtUtc 'handoff.capturedAtUtc'
$originalF1Head = $handoff.f1Head
Assert-Ancestor $handoff.f0Head $handoff.p0bCandidateHead 'F0 to P0-B'
Assert-Ancestor $handoff.p0bCandidateHead $originalF1Head 'P0-B to original F1'
Assert-Ancestor '713c15c2198d489d960a23ba3ec325c95bc92261' $originalF1Head 'Pinned P0-C plan commit to original F1'
foreach ($pair in @(
    @($handoff.p0cAcceptanceWindowStart,$handoff.p0bCandidateHead,'acceptance start'),
    @($handoff.p0cAcceptanceWindowEnd,$originalF1Head,'acceptance end'),
    @($handoff.reviewedHead,$originalF1Head,'reviewed HEAD'),
    @($handoff.sharedFileBaselineHead,$originalF1Head,'shared baseline')
)) { if (-not [string]::Equals($pair[0], $pair[1], [StringComparison]::Ordinal)) { throw "F1 $($pair[2]) drifted" } }
$requiredMigrations = @(
    'docs/sql/ai-video/mysql/20260728_02_p0b_workspace_authorization.sql',
    'docs/sql/ai-video/mysql/20260728_03_p0c_task_quota_direction.sql',
    'docs/sql/ai-video/mysql/20260728_04_p0_seed.sql'
)
$requiredSharedFiles = @(
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java',
    'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java',
    'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts',
    'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts',
    'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts'
)
$requiredServices = @(
    'org.dromara.aivideo.task.service.IAiTaskService',
    'org.dromara.aivideo.task.service.IAiTaskExecutionDispatcher',
    'org.dromara.aivideo.direction.service.IDirectionCatalogService'
)
$requiredSpis = @('org.dromara.aivideo.task.service.IAiTaskExecutionHandler')
$requiredDomainAndDtos = @(
    'org.dromara.aivideo.task.domain.AiTaskType',
    'org.dromara.aivideo.direction.dto.DirectionCatalogSnapshotDTO',
    'org.dromara.aivideo.task.dto.TaskInitiatorDTO',
    'org.dromara.aivideo.task.dto.FreeTaskDTO',
    'org.dromara.aivideo.task.dto.TaskRevisionSnapshotDTO',
    'org.dromara.aivideo.task.dto.TaskCreationResultDTO',
    'org.dromara.aivideo.task.dto.AiTaskExecutionLeaseDTO',
    'org.dromara.aivideo.task.dto.TaskResultReferenceDTO'
)
Assert-StringArray $handoff.migrations $requiredMigrations 'handoff.migrations'
Assert-StringArray $handoff.sharedFiles $requiredSharedFiles 'handoff.sharedFiles'
Assert-StringArray $handoff.stableServices $requiredServices 'handoff.stableServices'
Assert-StringArray $handoff.internalSpis $requiredSpis 'handoff.internalSpis'
Assert-StringArray $handoff.stableDomainAndDtos $requiredDomainAndDtos 'handoff.stableDomainAndDtos'
$directionDtoPath = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/direction/dto/DirectionCatalogSnapshotDTO.java'
if (-not (Test-Path -LiteralPath $directionDtoPath -PathType Leaf)) { throw 'DirectionCatalogSnapshotDTO source is missing' }
$directionDtoSource = Get-Content -Raw -LiteralPath $directionDtoPath
$directionDtoHeader = '(?s)record\s+DirectionCatalogSnapshotDTO\s*\(\s*Long\s+catalogVersion\s*,\s*String\s+contentHash\s*,\s*Long\s+industryCatalogVersion\s*,\s*Long\s+purposeCatalogVersion\s*,\s*String\s+durationRuleVersion\s*,\s*List<IndustryOption>\s+industries\s*,\s*Map<String,\s*List<PurposeOption>>\s+purposesByIndustry\s*,\s*List<TargetDurationOption>\s+targetDurations\s*\)'
if (-not [regex]::IsMatch($directionDtoSource, $directionDtoHeader)) {
    throw 'DirectionCatalogSnapshotDTO eight-component header drifted'
}
$directionValidationPatterns = @(
    'industryCatalogVersion\s*==\s*null\s*\|\|\s*industryCatalogVersion\s*<\s*1',
    'purposeCatalogVersion\s*==\s*null\s*\|\|\s*purposeCatalogVersion\s*<\s*1',
    'durationRuleVersion\s*==\s*null\s*\|\|\s*durationRuleVersion\.isBlank\(\)'
)
foreach ($pattern in $directionValidationPatterns) {
    if (-not [regex]::IsMatch($directionDtoSource, $pattern)) {
        throw "DirectionCatalogSnapshotDTO trace-version validation drifted: $pattern"
    }
}
Assert-ExactFields $handoff.downstreamRebaseOwners @('P1','P2','P3') 'handoff.downstreamRebaseOwners'
$ownerValues = [ordered]@{ P1='主计划 Task 5'; P2='主计划 Task 6'; P3='主计划 Task 7' }
foreach ($name in $ownerValues.Keys) { Assert-StringEquals $handoff.downstreamRebaseOwners.$name $ownerValues[$name] "handoff.downstreamRebaseOwners.$name" }
$mapping = $handoff.knowledgeImportRevisionMapping
Assert-ExactFields $mapping @('status','contractOwner','reviewer','reviewStatus','f1Head','confirmedAtUtc','draftRevisionSource','branchRevisionSource','generationContextRevisionSource') 'knowledgeImportRevisionMapping'
foreach ($name in @('status','contractOwner','reviewer','reviewStatus','f1Head','confirmedAtUtc','draftRevisionSource','branchRevisionSource','generationContextRevisionSource')) { Assert-String $mapping.$name "mapping.$name" }
Assert-StringEquals $mapping.status 'CONFIRMED' 'mapping.status'
Assert-StringEquals $mapping.reviewStatus 'PASS' 'mapping.reviewStatus'
Assert-StringEquals $mapping.f1Head $originalF1Head 'mapping.f1Head'
Assert-Utc $mapping.confirmedAtUtc 'mapping.confirmedAtUtc'
if ($mapping.contractOwner.Trim().Equals($mapping.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'Revision mapping reviewer must be independent' }
$handoffSha256 = (Get-FileHash -LiteralPath $handoffPath -Algorithm SHA256).Hash.ToLowerInvariant()
Assert-Sha256 $handoffSha256 'handoff SHA-256'
$addendumSource = $env:AI_VIDEO_P0C_F1_ADDENDUM
if ([string]::IsNullOrWhiteSpace($addendumSource)) { throw 'AI_VIDEO_P0C_F1_ADDENDUM is required' }
$addendumPath = [IO.Path]::GetFullPath($addendumSource)
if (-not (Test-Path -LiteralPath $addendumPath -PathType Leaf)) { throw 'F1 addendum file is missing' }
$addendum = Get-Content -LiteralPath $addendumPath -Raw | ConvertFrom-Json
Assert-ExactFields $addendum @(
    'originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum',
    'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc'
) 'P0-C F1 addendum'
foreach ($name in @('originalF1Head','amendmentHead','reviewedHead')) { Assert-Sha40 $addendum.$name "addendum.$name" }
foreach ($name in @('owner','reviewer','reviewStatus','reviewCompletedAtUtc','capturedAtUtc')) { Assert-String $addendum.$name "addendum.$name" }
Assert-Sha256 $addendum.originalF1HandoffSha256 'addendum.originalF1HandoffSha256'
Assert-StringEquals $addendum.originalF1Head $originalF1Head 'addendum.originalF1Head'
Assert-StringEquals $addendum.originalF1HandoffSha256 $handoffSha256 'addendum.originalF1HandoffSha256'
Assert-StringEquals $addendum.reviewStatus 'PASS' 'addendum.reviewStatus'
Assert-StringEquals $addendum.reviewedHead $addendum.amendmentHead 'addendum.reviewedHead'
if ($addendum.owner.Trim().Equals($addendum.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'F1 addendum reviewer must be independent' }
Assert-Ancestor $originalF1Head $addendum.amendmentHead 'original F1 to amendment'
$requiredMethods = @(
    'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
    'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
)
Assert-StringArray $addendum.requiredMethods $requiredMethods 'addendum.requiredMethods'
$schema = $addendum.schemaAddendum
Assert-ExactFields $schema @(
    'forwardMigration','taskGroupMemberTable','activeTaskIndex','originValues','creatorTypes',
    'globalLockOrder','scriptGroupKey','inheritanceScope','forbiddenCopies'
) 'addendum.schemaAddendum'
Assert-StringEquals $schema.forwardMigration '20260728_04a_p0c_task_group_guard.sql' 'schema.forwardMigration'
Assert-StringEquals $schema.taskGroupMemberTable 'av_ai_task_group_member' 'schema.taskGroupMemberTable'
Assert-StringEquals $schema.activeTaskIndex 'idx_av_ai_task_active_group' 'schema.activeTaskIndex'
Assert-StringArray $schema.originValues @('origin','inherited') 'schema.originValues'
Assert-StringArray $schema.creatorTypes @('app_user','sys_user') 'schema.creatorTypes'
Assert-StringArray $schema.globalLockOrder @('draft','current_branch','operation_slot','quota_account','task_or_group_member') 'schema.globalLockOrder'
Assert-StringEquals $schema.scriptGroupKey 'script:{draftId}:{branchRevision}' 'schema.scriptGroupKey'
Assert-StringEquals $schema.inheritanceScope 'membership_only' 'schema.inheritanceScope'
Assert-StringArray $schema.forbiddenCopies @('task','usage','ledger','operation_slot') 'schema.forbiddenCopies'
if ($addendum.evidence -isnot [System.Array] -or $addendum.evidence.Count -ne 3) { throw 'addendum.evidence must contain exactly three ordered objects' }
$expectedAddendumEvidence = @(
    @('source-signatures','git-metadata:p0c-f1-addendum/source-signatures.manifest.json'),
    @('migration-04a','git-metadata:p0c-f1-addendum/migration-04a.manifest.json'),
    @('independent-review','git-metadata:p0c-f1-contract-addendum-review.json')
)
$addendumMetadataRoot = [IO.Path]::GetFullPath((Split-Path -Parent $addendumPath))
for ($i = 0; $i -lt $expectedAddendumEvidence.Count; $i++) {
    $binding = $addendum.evidence[$i]
    Assert-ExactFields $binding @('kind','path','sha256') "addendum.evidence[$i]"
    Assert-StringEquals $binding.kind $expectedAddendumEvidence[$i][0] "addendum.evidence[$i].kind"
    Assert-StringEquals $binding.path $expectedAddendumEvidence[$i][1] "addendum.evidence[$i].path"
    Assert-Sha256 $binding.sha256 "addendum.evidence[$i].sha256"
    $relativeEvidencePath = $binding.path.Substring('git-metadata:'.Length)
    $evidenceFile = [IO.Path]::GetFullPath((Join-Path $addendumMetadataRoot $relativeEvidencePath))
    if (-not $evidenceFile.StartsWith($addendumMetadataRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'F1 addendum evidence escapes metadata root' }
    if (-not (Test-Path -LiteralPath $evidenceFile -PathType Leaf)) { throw "F1 addendum evidence is missing: $($binding.kind)" }
    $actualEvidenceSha = (Get-FileHash -LiteralPath $evidenceFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualEvidenceSha -cne $binding.sha256) { throw "F1 addendum evidence SHA drifted: $($binding.kind)" }
}
$reviewPath = Join-Path $addendumMetadataRoot 'p0c-f1-contract-addendum-review.json'
$addendumReview = Get-Content -LiteralPath $reviewPath -Raw | ConvertFrom-Json
Assert-ExactFields $addendumReview @(
    'owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256',
    'requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc'
) 'P0-C F1 addendum review'
foreach ($name in @('owner','reviewer','reviewStatus','reviewCompletedAtUtc')) { Assert-String $addendumReview.$name "addendumReview.$name" }
foreach ($name in @('reviewedHead','originalF1Head')) { Assert-Sha40 $addendumReview.$name "addendumReview.$name" }
foreach ($name in @('originalF1HandoffSha256','requiredMethodsSha256','schemaAddendumSha256')) { Assert-Sha256 $addendumReview.$name "addendumReview.$name" }
$requiredMethodsBytes = [Text.UTF8Encoding]::new($false).GetBytes(($addendum.requiredMethods | ConvertTo-Json -Depth 5 -Compress))
$requiredMethodsSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($requiredMethodsBytes)).ToLowerInvariant()
$schemaBytes = [Text.UTF8Encoding]::new($false).GetBytes(($schema | ConvertTo-Json -Depth 10 -Compress))
$schemaSha256 = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($schemaBytes)).ToLowerInvariant()
Assert-StringEquals $addendumReview.owner $addendum.owner 'addendumReview.owner'
Assert-StringEquals $addendumReview.reviewer $addendum.reviewer 'addendumReview.reviewer'
Assert-StringEquals $addendumReview.reviewStatus 'PASS' 'addendumReview.reviewStatus'
Assert-StringEquals $addendumReview.reviewedHead $addendum.amendmentHead 'addendumReview.reviewedHead'
Assert-StringEquals $addendumReview.originalF1Head $originalF1Head 'addendumReview.originalF1Head'
Assert-StringEquals $addendumReview.originalF1HandoffSha256 $handoffSha256 'addendumReview.originalF1HandoffSha256'
Assert-StringEquals $addendumReview.requiredMethodsSha256 $requiredMethodsSha256 'addendumReview.requiredMethodsSha256'
Assert-StringEquals $addendumReview.schemaAddendumSha256 $schemaSha256 'addendumReview.schemaAddendumSha256'
Assert-StringEquals $addendumReview.reviewCompletedAtUtc $addendum.reviewCompletedAtUtc 'addendumReview.reviewCompletedAtUtc'
if ($addendumReview.owner.Trim().Equals($addendumReview.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'F1 addendum review owner/reviewer are not independent' }
Assert-Utc $addendumReview.reviewCompletedAtUtc 'addendumReview.reviewCompletedAtUtc'
Assert-Utc $addendum.capturedAtUtc 'addendum.capturedAtUtc'
$reviewTime = [DateTimeOffset]::Parse($addendumReview.reviewCompletedAtUtc, [Globalization.CultureInfo]::InvariantCulture)
$capturedTime = [DateTimeOffset]::Parse($addendum.capturedAtUtc, [Globalization.CultureInfo]::InvariantCulture)
if ($capturedTime -lt $reviewTime) { throw 'F1 addendum capturedAtUtc predates independent review' }
$f1AddendumSha256 = (Get-FileHash -LiteralPath $addendumPath -Algorithm SHA256).Hash.ToLowerInvariant()
Assert-Sha256 $f1AddendumSha256 'F1 addendum SHA-256'
$f1Head = $addendum.amendmentHead
$recordFile = Resolve-Metadata 'p1-f1-integration.json'
$pendingFile = Resolve-Metadata 'p1-f1-integration.pending.json'
$baselineFile = Resolve-Metadata 'p1-baseline.json'
function Get-CoreFromRecord($Record) {
    return [ordered]@{
        repoRoot=$Record.repoRoot; branch=$Record.branch; owner=$Record.owner; independentHead=$Record.independentHead
        beforeHead=$Record.beforeHead; originalF1Head=$Record.originalF1Head; f1Head=$Record.f1Head; baseBefore=$Record.baseBefore
        handoffSha256=$Record.handoffSha256; f1AddendumSha256=$Record.f1AddendumSha256
        revisionMappingContractOwner=$Record.revisionMappingContractOwner
    }
}
function Assert-Pending($Value) {
    Assert-ExactFields $Value @('transactionId','repoRoot','branch','owner','independentHead','beforeHead','originalF1Head','f1Head','baseBefore','handoffSha256','f1AddendumSha256','revisionMappingContractOwner','createdAtUtc') 'F1 pending record'
    Assert-Sha256 $Value.transactionId 'pending.transactionId'
    foreach ($name in @('repoRoot','branch','owner','revisionMappingContractOwner')) { Assert-String $Value.$name "pending.$name" }
    foreach ($name in @('independentHead','beforeHead','originalF1Head','f1Head','baseBefore')) { Assert-Sha40 $Value.$name "pending.$name" }
    Assert-Sha256 $Value.handoffSha256 'pending.handoffSha256'
    Assert-Sha256 $Value.f1AddendumSha256 'pending.f1AddendumSha256'
    Assert-Utc $Value.createdAtUtc 'pending.createdAtUtc'
    if ((Get-TransactionId (Get-CoreFromRecord $Value)) -cne $Value.transactionId) { throw 'Pending transaction hash drifted' }
}
function Assert-Final($Value) {
    Assert-ExactFields $Value @('transactionId','beforeHead','originalF1Head','f1Head','afterHead','baseBefore','handoffSha256','f1AddendumSha256','revisionMappingContractOwner','integratedAtUtc') 'F1 final record'
    Assert-Sha256 $Value.transactionId 'final.transactionId'
    foreach ($name in @('beforeHead','originalF1Head','f1Head','afterHead','baseBefore')) { Assert-Sha40 $Value.$name "final.$name" }
    Assert-Sha256 $Value.handoffSha256 'final.handoffSha256'
    Assert-Sha256 $Value.f1AddendumSha256 'final.f1AddendumSha256'
    Assert-String $Value.revisionMappingContractOwner 'final.revisionMappingContractOwner'
    Assert-Utc $Value.integratedAtUtc 'final.integratedAtUtc'
}
$branch = (& git branch --show-current).Trim()
$currentHead = (& git rev-parse HEAD).Trim()
if (Test-Path -LiteralPath $recordFile -PathType Leaf) {
    $final = Get-Content -LiteralPath $recordFile -Raw | ConvertFrom-Json
    Assert-Final $final
    if ($final.originalF1Head -cne $originalF1Head -or $final.f1Head -cne $f1Head -or $final.handoffSha256 -cne $handoffSha256 -or $final.f1AddendumSha256 -cne $f1AddendumSha256 -or $final.revisionMappingContractOwner -cne $mapping.contractOwner) { throw 'Existing final record has a different payload' }
    $baseline = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json
    $finalCore = [ordered]@{
        repoRoot=$baseline.repoRoot; branch=$baseline.branch; owner=$baseline.owner; independentHead=$baseline.independentHead
        beforeHead=$final.beforeHead; originalF1Head=$final.originalF1Head; f1Head=$final.f1Head; baseBefore=$final.baseBefore
        handoffSha256=$final.handoffSha256; f1AddendumSha256=$final.f1AddendumSha256
        revisionMappingContractOwner=$final.revisionMappingContractOwner
    }
    if ((Get-TransactionId $finalCore) -cne $final.transactionId) { throw 'Final F1 transaction hash drifted' }
    if ($baseline.phase -ceq 'independent') {
        if ((& $gate -RepoRoot $repoRoot -RegisterF1 -F1Head $final.f1Head -BeforeHead $final.beforeHead -AfterHead $final.afterHead -TransactionId $final.transactionId -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Final-record baseline recovery failed' }
    }
    if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Integrated idempotent gate failed' }
    if (Test-Path -LiteralPath $pendingFile) {
        $pending = Get-Content -LiteralPath $pendingFile -Raw | ConvertFrom-Json
        Assert-Pending $pending
        if ($pending.transactionId -cne $final.transactionId) { throw 'Pending/final transactions differ' }
        Remove-Item -LiteralPath $pendingFile
    }
    $currentHead = (& git rev-parse HEAD).Trim()
    Write-Output "P1_F1_INTEGRATION_OK=$currentHead"
    return
}
$baseline = Get-Content -LiteralPath $baselineFile -Raw | ConvertFrom-Json
if ($baseline.phase -cnotin @('independent','integrated')) { throw 'Unknown baseline phase before F1 recovery' }
if (Test-Path -LiteralPath $pendingFile -PathType Leaf) {
    $pending = Get-Content -LiteralPath $pendingFile -Raw | ConvertFrom-Json
    Assert-Pending $pending
    if ($pending.originalF1Head -cne $originalF1Head -or $pending.f1Head -cne $f1Head -or $pending.handoffSha256 -cne $handoffSha256 -or $pending.f1AddendumSha256 -cne $f1AddendumSha256 -or $pending.revisionMappingContractOwner -cne $mapping.contractOwner -or
        -not $pending.repoRoot.Equals($repoRoot, [StringComparison]::OrdinalIgnoreCase) -or $pending.branch -cne $branch -or $pending.owner -cne [Environment]::UserName) { throw 'Existing pending record has a different payload/worktree owner' }
} else {
    if ($baseline.phase -cne 'independent') { throw 'Integrated baseline without pending/final proof cannot be recovered' }
    if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Independent, clean, isolated gate failed' }
    $beforeHead = (& git rev-parse HEAD).Trim()
    $baseBefore = (& git merge-base $beforeHead $f1Head).Trim()
    $core = [ordered]@{
        repoRoot=$repoRoot; branch=$branch; owner=[Environment]::UserName; independentHead=$baseline.independentHead
        beforeHead=$beforeHead; originalF1Head=$originalF1Head; f1Head=$f1Head; baseBefore=$baseBefore
        handoffSha256=$handoffSha256; f1AddendumSha256=$f1AddendumSha256
        revisionMappingContractOwner=$mapping.contractOwner
    }
    $transactionId = Get-TransactionId $core
    $pendingPayload = [ordered]@{ transactionId=$transactionId }
    foreach ($key in $core.Keys) { $pendingPayload[$key] = $core[$key] }
    $pendingPayload.createdAtUtc = [DateTime]::UtcNow.ToString('o')
    Write-CreateNewJson $pendingFile $pendingPayload 'F1 pending record'
    $pending = Get-Content -LiteralPath $pendingFile -Raw | ConvertFrom-Json
    Assert-Pending $pending
}
if ($baseline.phase -ceq 'independent') {
    if ((& $gate -RepoRoot $repoRoot -ExpectedPhase independent -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Independent recovery gate failed' }
    $currentHead = (& git rev-parse HEAD).Trim()
    if ($currentHead -ceq $pending.beforeHead) {
        & git merge-base --is-ancestor $f1Head $currentHead
        if ($LASTEXITCODE -ne 0) {
            git rebase $f1Head
            if ($LASTEXITCODE -ne 0) { throw 'Resolve or abort rebase before continuing' }
        }
    } else {
        Assert-Ancestor $f1Head $currentHead 'Recovered rebase'
    }
    $afterHead = (& git rev-parse HEAD).Trim()
    Assert-Ancestor $f1Head $afterHead 'F1 after rebase'
    if ((& $gate -RepoRoot $repoRoot -RegisterF1 -F1Head $f1Head -BeforeHead $pending.beforeHead -AfterHead $afterHead -TransactionId $pending.transactionId -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'F1 baseline registration failed' }
} else {
    if ($baseline.transactionId -cne $pending.transactionId -or $baseline.f1Head -cne $pending.f1Head -or $baseline.beforeHead -cne $pending.beforeHead) { throw 'Integrated baseline and pending transaction differ' }
    $afterHead = $baseline.integratedHead
    if ((& $gate -RepoRoot $repoRoot -RegisterF1 -F1Head $f1Head -BeforeHead $pending.beforeHead -AfterHead $afterHead -TransactionId $pending.transactionId -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Integrated baseline recovery failed' }
}
$recordPayload = [ordered]@{
    transactionId=$pending.transactionId; beforeHead=$pending.beforeHead; originalF1Head=$originalF1Head
    f1Head=$f1Head; afterHead=$afterHead; baseBefore=$pending.baseBefore
    handoffSha256=$handoffSha256; f1AddendumSha256=$f1AddendumSha256
    revisionMappingContractOwner=$mapping.contractOwner; integratedAtUtc=[DateTime]::UtcNow.ToString('o')
}
Write-CreateNewJson $recordFile $recordPayload 'F1 final record'
$final = Get-Content -LiteralPath $recordFile -Raw | ConvertFrom-Json
Assert-Final $final
if ($final.transactionId -cne $pending.transactionId -or $final.afterHead -cne $afterHead) { throw 'Final F1 readback differs' }
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean -RequireIsolated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Final integrated gate failed' }
Remove-Item -LiteralPath $pendingFile
$currentHead = (& git rev-parse HEAD).Trim()
Write-Output "P1_F1_INTEGRATION_OK=$currentHead"
```

若冲突，停止并按 `receiving-code-review` 的技术验证流程处理；禁止用丢弃本地成果的命令跳过冲突。登记顺序固定为 pending `CreateNew` → rebase → baseline 原子替换 → final `CreateNew` → 严格回读 → 删除 pending。任一阶段崩溃后，相同 transaction/payload 必须可恢复；不同 payload 永远不得覆盖。后续提交允许推进 HEAD，但 final `afterHead` 必须始终是当前 HEAD 的祖先。

### 迁移测试要求

`P1KnowledgeMigrationIT` 必须：

1. 通过 `LocalIntegrationEnvironment` 拒绝非 `ai_video_test` 数据库、非隔离 Redis、容器和开发/生产配置。
2. 清理只属于本 runId 的 P1 测试对象。
3. 依次执行 01、02、03、04、04a、05。
4. 断言 P1 表、索引、唯一键、字典、菜单和权限。
5. 再执行一次 05，断言幂等且数据不重复。

### 迁移 RED

先创建带 `@Tag("dev")` 的可编译 IT，再保持正式 05 不存在运行；`AssertRed` 必须来自 P1 表/索引缺失断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.P1KnowledgeMigrationIT.xml'
$started = & $jvmGate -Mode Prepare -Kind Failsafe -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' '-Dit.test=P1KnowledgeMigrationIT' verify
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Failsafe -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail because formal 05 does not exist yet' }
```

### 迁移 GREEN

根据已审核的 `.sql.draft` 新建正式 `20260728_05_p1_knowledge.sql` 并实现幂等 DDL/DML；逐项核对完成后用文件补丁删除 `.sql.draft`，不得把草案直接改名放行，也不得更改 01–04a。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.P1KnowledgeMigrationIT.xml'
$started = & $jvmGate -Mode Prepare -Kind Failsafe -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' '-Dit.test=P1KnowledgeMigrationIT' verify
if ($LASTEXITCODE -ne 0) { throw 'Migration GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Failsafe -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 7：接入免费导入任务和安全 ZIP provider（F1 后）

### 最小任务卡

- **单一目标／不做：** 接入 P0-C 免费任务并安全解析 ZIP 为草稿；不计费、不创建 attempt、不自动发布。
- **权威来源：** 已核验 F1 handoff、`docs/ASYNC_TASKS.md`、`docs/API_CONTRACT.md` 和 ZIP 安全规则。
- **风险／触发：** 红；事务缝隙、lease 未终态或恶意压缩包会造成悬空任务与安全问题。
- **所有权／数据范围：** P1 core import Service、infra provider/listener、知识导入冻结表和草稿。
- **依赖／人员／并发：** Task 6 及 revision 映射确认；core 与 infra 可由两人并行，最后由 import owner 联调。
- **允许影响：** P1 core/infra 与必要的 Maven 依赖；禁止 task 表/Mapper、计费、模型、搜索和当前登录态。
- **成功／反向验收：** 两组 unit 真 RED→GREEN且导入 IT 全绿；重复冻结、attempt/usage/provider 或非终态均失败。
- **固定输出：** `IKnowledgeImportService` 及实现、三类 infra 文件、两个 unit 测试、`KnowledgeImportFlowIT`。

### 固定装配

`IKnowledgeImportService` 使用接口内嵌不可变 `ImportRequest` / `ImportResult` 表达阶段内参数，不新增跨阶段 DTO 文件。签名和 record header 精确冻结如下；实现不得改成当前登录态、BO/VO、Map 或位置参数集合：

```java
public interface IKnowledgeImportService {
    ImportResult create(ImportRequest request, TaskInitiatorDTO initiator);

    record ImportRequest(Long batchId, String idempotencyKey) {
    }

    record ImportResult(
        Long batchId,
        Long rootTaskId,
        Long executionTaskId,
        boolean reused
    ) {
    }
}
```

compact constructor 必须校验三个编号为正数、`idempotencyKey` 非空且不超过 128 字符。真实实现从批次不可变内容计算 `requestHash`，并从 F1 已确认映射构造合法 `TaskRevisionSnapshotDTO`；二者都不得由 HTTP 请求自由传入。F1 前 fixture 不得进入生产代码。

```java
Objects.requireNonNull(request, "request");
Objects.requireNonNull(initiator, "initiator");
initiator.requireActorType("sys_user");
KnowledgeImportBatch batch = requireSubmittableBatch(request.batchId());
String requestHash = hashImportRequest(batch);
TaskRevisionSnapshotDTO revisionSnapshot = toRevisionSnapshot(batch);
Long batchId = batch.getId();

FreeTaskDTO freeTask = new FreeTaskDTO(
    AiTaskType.KNOWLEDGE_IMPORT,
    "knowledge_import_batch",
    batchId,
    "knowledge-import:" + batchId,
    "knowledge-import:" + batchId,
    request.idempotencyKey(),
    requestHash,
    initiator,
    revisionSnapshot
);
TaskCreationResultDTO created = aiTaskService.createFreeTask(freeTask);
if (created.reused()) {
    return new ImportResult(
        batchId, created.rootTaskId(), created.executionTaskId(), true);
}
freezeImmutableInput(
    created.rootTaskId(), created.executionTaskId(), batch, requestHash);
dispatcher.enqueue(created.rootTaskId(), created.executionTaskId());
return new ImportResult(
    batchId, created.rootTaskId(), created.executionTaskId(), false);
```

以上是 `create` 方法的完整局部变量来源并位于同一个外层 `@Transactional` 中；`requireSubmittableBatch`、`hashImportRequest`、`toRevisionSnapshot` 和 `freezeImmutableInput` 是实现类私有方法。入口第一步必须以 `initiator.requireActorType("sys_user")` 拒绝 `app_user`，不得等到创建任务后再校验。相同 `tenantId/actorType/actorId/taskType/idempotencyKey` + 相同 hash 复用既有 root 和唯一 `executionNo=1`，包括故障恢复后的再次请求；相同维度 + 不同 hash 返回 46116。复用分支必须在 freeze/enqueue 前立即返回。resourceType/resourceId 只保持固定资源引用。免费任务的 `usageOperationId` 必须为 null，P1 不查询或写入 usage。

`KnowledgeImportServiceTest` 在 F1 后直接构造真实 `new TaskInitiatorDTO("sys_user", sysUserId)` 验证上述事务顺序，并以真实 `new TaskInitiatorDTO("app_user", appUserId)` 断言在批次查询、`createFreeTask`、冻结和入队之前失败且所有依赖零调用。Task 8 的 Controller 测试只验证 `SysTaskInitiatorResolver` 把当前后台用户转换成同一 DTO 后调用二参 `create`。

`KnowledgeImportTaskHandler` 位于 infra listener，直接实现 P0-C `IAiTaskExecutionHandler`：

- `supports()` 精确返回 `AiTaskType.KNOWLEDGE_IMPORT`；
- `handle(AiTaskExecutionLeaseDTO lease)` 只按 lease 的任务编号读取冻结输入；
- 成功调用 `markSuccess(lease, TaskResultReferenceDTO.of("knowledge_import_batch", batchId))`；
- 可预期业务失败调用 `markFailed(lease, failureCode, failureMessage)` 并保留可审计错误；正常返回前 lease 必须已完整终态；
- 结果引用类型是 P1 本域约定，须同步 `docs/API_CONTRACT.md` / `docs/DOMAIN_MODEL.md`；
- 不注入 attempt、usage、quota、模型、搜索或任意外部网络 client。
- 不调用 `claimExecutableTasks`、`renewLease`、`recordHandlerFailure`；意外异常抛给 P0-C handler registry，不能在 P1 内伪造 lease 恢复。

`SafeZipKnowledgeArchiveReader` 必须拒绝 zip-slip、绝对路径、符号链接、加密条目、重复规范化路径、目录深度超限、单文件/总解压大小超限、压缩比超限、非法 manifest、非允许字符集及 URL/网络引用。只读取冻结对象流，不访问宿主机任意路径，不修改原 ZIP。Apache Commons Compress 仅在现有依赖无法满足时条件加入 POM；不得无条件 install、改 lock 或暂存。

### Import Service RED

先创建可编译 Import Service skeleton，再写事务/幂等/usage-null 测试；`AssertRed` 必须来自业务断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeImportServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeImportServiceTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by import assertion' }
```

### Import Service GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeImportServiceTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-core' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeImportServiceTest' test
if ($LASTEXITCODE -ne 0) { throw 'Import Service GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

### ZIP provider RED

此处先增加冻结对象流和 manifest wiring 的新断言，再保持集成 skeleton 运行；F1 前安全算法已绿，当前 `AssertRed` 必须来自新增 wiring 断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-infra'
$report = 'TEST-org.dromara.aivideo.knowledge.SafeZipKnowledgeArchiveReaderTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-infra' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SafeZipKnowledgeArchiveReaderTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by ZIP security assertion' }
```

### ZIP provider GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-infra'
$report = 'TEST-org.dromara.aivideo.knowledge.SafeZipKnowledgeArchiveReaderTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-infra' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=SafeZipKnowledgeArchiveReaderTest' test
if ($LASTEXITCODE -ne 0) { throw 'ZIP provider GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

### 导入流程 IT

`KnowledgeImportFlowIT` 必须验证 create→freeze→enqueue 的事务可见性、唯一 root + `executionNo=1`、故障恢复复用立即返回、46116 冲突、`usageOperationId=null`、worker 无登录态、零 attempt/usage/provider、草稿不自动发布、成功/失败完整 lease 终态和 P1 结果引用。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeImportFlowIT.xml'
$started = & $jvmGate -Mode Prepare -Kind Failsafe -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' '-Dit.test=KnowledgeImportFlowIT' verify
if ($LASTEXITCODE -ne 0) { throw 'Knowledge import IT failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Failsafe -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 8：实现 platform HTTP BO/VO、Controller 与装配（F1 后）

### 最小任务卡

- **单一目标／不做：** 完成 F1 后 Mapper/方向/快照持久化，再暴露真实 HTTP 并完成 Spring 装配；不把 BO/VO 或登录解析下沉到 core。
- **权威来源：** `docs/API_CONTRACT.md`、后端指南、RuoYi 相似 Controller 和 P0-A/P0-B 安全契约。
- **风险／触发：** 红；权限、actor 或装配错误会造成越权与运行时失败。
- **所有权／数据范围：** P1 backend owner；core 的 F1 后持久化编排、platform Controller/BO/VO、权限、日志和 assembly IT。
- **依赖／人员／并发：** Task 6–7；可与 Task 9 的前端 API adapter 并行，协作者不超过两人。
- **允许影响：** core ServiceImpl 的具体 Mapper/事务、platform 知识端点及公共契约文档；禁止 Controller 直接 Mapper、core HTTP 类型和伪造 initiator。
- **成功／反向验收：** Controller 真 RED→GREEN且 assembly IT fresh 全绿；BO 覆盖可信字段或 core 解析登录态均失败。
- **固定输出：** 真实 catalog/routing/snapshot/publication 编排、精确 BO/VO、`KnowledgeController`、unit 与 assembly IT。

### F1 后 core 集成

执行次序是：先保留 Task 3/4 的纯规则与 public RED skeleton，创建 `PlatformKnowledgeAssemblyIT` 并运行下方 RED；确认失败来自最终阶段内 Service、方向、Mapper、快照 bean 尚未装配后，再实现以下六项。

1. 此时才创建最终 `IKnowledgeCatalogService` 与 `IKnowledgePublicationService`；禁止把 P0-C 类型复制进 P1。发布接口必须直接使用 F1 的真实 DTO，三条变更方法固定为：

   ```java
   public interface IKnowledgePublicationService {
       KnowledgeVersion submitReview(Long versionId, int expectedRevision, TaskInitiatorDTO initiator);
       KnowledgeVersion publish(Long versionId, int expectedRevision, String publishNote, TaskInitiatorDTO initiator);
       KnowledgeVersion retire(Long versionId, int expectedRevision, TaskInitiatorDTO initiator);
   }
   ```

2. `KnowledgePublicationServiceImpl` 的三个入口都先执行 `initiator.requireActorType("sys_user")`，只取 `initiator.actorId()` 写审核/发布/退役人，并只取注入的服务端 `Clock` 生成时间；随后委托 Task 3 已验证的 package-private 纯状态内核。不得读取登录态、接受 BO 中的 actor/time，或使用测试 fixture 代替真实 DTO。
3. `KnowledgePublicationServiceImpl` 注入 `IDirectionCatalogService`，在私有 `validateForPublication` 中调用 `currentPublishedCatalog()`，对真实 `DirectionCatalogSnapshotDTO` 做行业/用途交叉校验；测试 fixture 必须按 `catalogVersion,contentHash,industryCatalogVersion,purposeCatalogVersion,durationRuleVersion,industries,purposesByIndustry,targetDurations` 构造八个 component，并断言两个子版本为正数、时长规则版本非空；Mapper、事务、乐观锁和唯一键冲突翻译均在这里接入。
4. `KnowledgeRoutingServiceImpl.route` 用具体 Mapper 查询 `copywriting + published`，转换成两个内部 DTO 后只调用 Task 4 已验证的私有纯方法；不把 Mapper/Entity 暴露给 P2/P3。
5. `KnowledgeSnapshotServiceImpl.create` 解析并冻结实际消费的 knowledge/binding/video-rule 版本与正文片段，按 `rootTaskId` 幂等 INSERT；同 root 同 hash 复用，同 root 不同 hash 冲突。`getByRootTaskId` 只读冻结 payload，不回查当前知识正文。Entity 主键使用 `@TableId`。
6. 扩展 `KnowledgePublicationServiceTest` 时直接构造真实 `new TaskInitiatorDTO(...)` 和完整八 component `DirectionCatalogSnapshotDTO(...)`，覆盖非 `sys_user`、actorId、固定 `Clock`、正数／非空追溯版本、真实方向目录和 Mapper 条件更新；不得依赖当前登录态，也不得把 `contentHash` 或三个子版本映射进平台 HTTP 类型。另扩展 `KnowledgeRoutingServiceTest`、`KnowledgeSnapshotServiceTest`，并由 `PlatformKnowledgeAssemblyIT` 证明真实装配。

### Core assembly RED

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.PlatformKnowledgeAssemblyIT.xml'
$started = & $jvmGate -Mode Prepare -Kind Failsafe -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' '-Dit.test=PlatformKnowledgeAssemblyIT' verify
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Failsafe -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'Assembly RED must fail by missing core integration assertion' }
```

### HTTP 面

Controller 统一位于 `/api/admin/knowledge`，至少覆盖：

- 分页查询/创建条目；
- 创建版本、送审、发布、退役；
- 绑定和视频类型规则维护；
- 创建导入批次和查询导入结果。

必须使用项目既有 `R`、分页、Bean 校验、异常映射、`@SaCheckPermission` 和 `@Log`；权限按 `aivideo:knowledge:action` 命名。审核人/审核时间、任务发起人、租户/账号归属均来自可信服务，不接受 BO 覆盖。修订冲突映射稳定错误码，401 只触发一次登出，403 不伪装为空数据。

创建导入时只允许：

```java
TaskInitiatorDTO initiator = sysTaskInitiatorResolver.resolveRequired();
return knowledgeImportService.create(requestFrom(bo), initiator);
```

`SysTaskInitiatorResolver` 使用 platform 已有具体类型；core 不声明、不注入、不解析登录态。

### Controller RED

先创建可编译 BO/VO/Controller skeleton，再写权限、可信 actor、修订冲突测试；`AssertRed` 必须来自 HTTP 契约断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeControllerTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeControllerTest' test
$testExit = $LASTEXITCODE
if ((& $jvmGate -Mode AssertRed -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail by Controller assertion' }
```

### Controller GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.KnowledgeControllerTest.xml'
$started = & $jvmGate -Mode Prepare -Kind Surefire -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dtest=KnowledgeControllerTest' test
if ($LASTEXITCODE -ne 0) { throw 'Controller GREEN command failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

### Assembly IT

`PlatformKnowledgeAssemblyIT` 必须启动真实 Spring 上下文，证明五个 Service 唯一装配、infra listener 可发现、Controller 权限注解存在、platform resolver 生效且 core 不依赖端层类。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$module = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform'
$report = 'TEST-org.dromara.aivideo.knowledge.PlatformKnowledgeAssemblyIT.xml'
$started = & $jvmGate -Mode Prepare -Kind Failsafe -ModulePath $module -ReportName $report
& (Join-Path $repoRoot 'ai-video-api/mvnw.cmd') -pl 'ruoyi-modules/ai-video/ai-video-platform' -am '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' '-Dit.test=PlatformKnowledgeAssemblyIT' verify
if ($LASTEXITCODE -ne 0) { throw 'Platform knowledge assembly IT failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Failsafe -ModulePath $module -ReportName $report -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
```

---

## Task 9：切换真实前端 API 并回归完整状态矩阵（F1 后）

### 最小任务卡

- **单一目标／不做：** 把独立切片从 Mock 切到真实 Controller 契约并保持全部状态；不改稳定后端 DTO 或绕过 API 层。
- **权威来源：** Task 5 状态矩阵、Task 8 HTTP 契约、前端指南和共享响应/分页适配器。
- **风险／触发：** 中；接口漂移、错误态折叠或业务状态缺失会造成页面假成功。
- **所有权／数据范围：** P1 frontend owner；知识 API adapter、页面和两个既有测试文件。
- **依赖／人员／并发：** Task 8 HTTP 面冻结；可与 Task 8 assembly 验证并行，最终由同一联调 checkpoint 收口。
- **允许影响：** 知识前端目录；依赖文件只在确有缺项时条件修改，禁止无条件 install、lock 重写或暂存。
- **成功／反向验收：** 合并报告真 RED→GREEN且 lint/build 通过；任一状态合并、401 重复登出或 Mock 生产引用均失败。
- **固定输出：** 真实 `index.ts`、无生产引用的 `mock/aivideo-knowledge.ts`、页面联调测试、fresh Vitest JSON 和构建结果。

### 实现步骤

1. 先让测试断言真实 URL、分页适配、错误码、修订号和导入轮询；保留 Mock 实现时必须真实 RED。
2. `index.ts` 统一封装所有路径；`types.ts` 对齐 BO/VO；页面只能调用命名函数。
3. `mock/aivideo-knowledge.ts` 仅由测试/开发开关引用，生产构建不得导入。
4. loading、两种空态、分页刷新、5xx 重试、401 单次登出、403、送审/发布/退役，以及导入 queued/running/success/failure/cancel/retry 全部保持独立断言。
5. 失败 toast 不得替代页面失败态；取消不映射为失败；防重期间按钮禁用；发布必须完成引用检查、二次确认和 revision 冲突处理。

### Live API RED

先保留可编译且显式 reject 的真实 API adapter skeleton，再把两个测试文件切到真实请求断言；`AssertRed` 必须来自 adapter/状态断言。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-live-api-red-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/index.test.tsx' 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx' --reporter=json "--outputFile=$report"
$testExit = $LASTEXITCODE
if ((& $vitestGate -Mode AssertRed -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
if ($testExit -eq 0) { throw 'RED must fail while production adapter still uses Mock' }
```

### Live API GREEN

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-live-api-green-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run 'src/pages/aivideo/knowledge/index.test.tsx' 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx' --reporter=json "--outputFile=$report"
if ($LASTEXITCODE -ne 0) { throw 'Live API GREEN command failed' }
if ((& $vitestGate -Mode AssertGreen -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
```

### Lint 与构建

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
if (-not (Test-Path -LiteralPath $gate)) { throw 'P1 worktree gate is missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
Set-Location $ui
pnpm lint
if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed' }
pnpm build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed' }
```

---

## Task 10：全量验收、独立 review 与 F2 冻结

### 最小任务卡

- **单一目标／不做：** 在同一 clean HEAD 的 acceptance window 内完成全量证据、独立 review 和幂等 F2；不边验收边改代码。
- **权威来源：** `docs/AI_AGENT_GOVERNANCE.md`、`verification-before-completion`、`requesting-code-review` 和 F2 验收条件。
- **风险／触发：** 红；旧报告、未审核 HEAD 或可覆盖 handoff 会造成错误放行。
- **所有权／数据范围：** P1 owner 运行验证；独立 reviewer 写 review metadata；F2 owner 只在 PASS 后写 handoff。
- **依赖／人员／并发：** Task 1–9 已提交，F1 为祖先且差异非空；最终测试可并行跑，但 review 必须等待全部证据。
- **允许影响：** 当前 Git metadata 中的 acceptance/review/F2 JSON 和测试产物；禁止修改代码、暂存、提交或覆盖不同 HEAD 的 metadata。
- **成功／反向验收：** unit/IT/Vitest/构建/规范/静态扫描全绿；任一报告旧、reviewer=owner 或 HEAD 漂移均失败。
- **固定输出：** 六类 fresh evidence manifest、`p1-independent-review.json`、`p1-f2-handoff.json`。

### Acceptance window

1. 先提交 Task 1–9 的实现，确保专属 P1 worktree clean。
2. `p1-acceptance-window.json` 记录 `f1Head`、`candidateHead`、`startedAtUtc`；相同 HEAD 可复用，不同 HEAD 必须新开带 HEAD 后缀的窗口，不得覆盖旧文件。
3. 后续所有最终报告的 mtime 必须晚于 `startedAtUtc`，且直到 F2 形成前 HEAD 不变。
4. 任一代码变更都会使窗口失效：提交新 HEAD、创建新的 metadata 文件名后重跑全部最终门禁。

六类证据固定为 `unit`、`it`、`migration`、`vitest`、`standards`、`scan`，每类只允许一个固定路径：`<当前 Git metadata>/p1-evidence/<candidateHead>/<kind>.manifest.json`。manifest 顶层字段及顺序精确为 `schemaVersion`、`kind`、`candidateHead`、`f1Head`、`windowStartedAtUtc`、`generatedAtUtc`、`artifacts`、`summary`；`schemaVersion` 为 JSON number `1`，其余标量为 JSON string，`artifacts` 为 JSON array，`summary` 为 JSON object 且 `status` 精确为 `PASS`。每个 artifact 字段及顺序精确为 `pathScope`、`relativePath`、`sha256`、`bytes`、`lastWriteUtc`：scope 只能是 `repo` 或 `git-metadata`，relative path 必须归一化且不能为绝对路径/包含 `..`，SHA 为小写 64 hex，bytes 为非负 JSON number，mtime 为显式 UTC 且不早于窗口。所有 manifest 使用同目录临时文件加目标路径 `CreateNew` 原子形成；相同 payload 幂等只读，不同 payload 禁止覆盖。

### 全量 unit

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$candidateHead = (& git rev-parse HEAD).Trim()
$manifestPath = (& git rev-parse --git-path 'p1-evidence-manifest-gate.ps1').Trim()
$manifestGate = if ([IO.Path]::IsPathRooted($manifestPath)) { [IO.Path]::GetFullPath($manifestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $manifestPath)) }
if (-not (Test-Path -LiteralPath $manifestGate -PathType Leaf)) { throw 'P1 evidence manifest gate is missing' }
$integrationPath = (& git rev-parse --git-path 'p1-f1-integration.json').Trim()
$integrationFile = if ([IO.Path]::IsPathRooted($integrationPath)) { [IO.Path]::GetFullPath($integrationPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $integrationPath)) }
$f1Head = [string](Get-Content -LiteralPath $integrationFile -Raw | ConvertFrom-Json).f1Head
$windowPath = (& git rev-parse --git-path "p1-acceptance-window-$candidateHead.json").Trim()
$windowFile = if ([IO.Path]::IsPathRooted($windowPath)) { [IO.Path]::GetFullPath($windowPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $windowPath)) }
if (Test-Path -LiteralPath $windowFile) {
    $window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
} else {
    $windowPayload = [ordered]@{ f1Head=$f1Head; candidateHead=$candidateHead; startedAtUtc=[DateTime]::UtcNow.ToString('o') }
    $windowBytes = [Text.UTF8Encoding]::new($false).GetBytes(($windowPayload | ConvertTo-Json -Compress))
    $stream = [IO.File]::Open($windowFile, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($windowBytes, 0, $windowBytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    $window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
}
$windowFields = @($window.PSObject.Properties | ForEach-Object { $_.Name })
if ($window -isnot [pscustomobject] -or $windowFields.Count -ne 3 -or $windowFields[0] -cne 'f1Head' -or $windowFields[1] -cne 'candidateHead' -or $windowFields[2] -cne 'startedAtUtc') { throw 'Acceptance window schema drifted' }
foreach ($name in @('f1Head','candidateHead','startedAtUtc')) { if ($window.$name -isnot [string]) { throw "Acceptance window $name must be a JSON string" } }
if ($window.candidateHead -cne $candidateHead -or $window.f1Head -cne $f1Head) { throw 'Acceptance window belongs to another HEAD' }
if ($window.startedAtUtc -cnotmatch '(?:Z|\+00:00)$' -or [DateTimeOffset]::Parse($window.startedAtUtc).Offset -ne [TimeSpan]::Zero) { throw 'Acceptance window start must be explicit UTC' }
$api = Join-Path $repoRoot 'ai-video-api'
$reports = @(
    'TEST-org.dromara.aivideo.knowledge.KnowledgeContractTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeDomainRulesTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgePublicationServiceTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeRoutingServiceTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeSnapshotServiceTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeImportServiceTest.xml',
    'TEST-org.dromara.aivideo.knowledge.SafeZipKnowledgeArchiveReaderTest.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeControllerTest.xml'
)
$started = & $jvmGate -Mode Prepare -Kind Surefire -SearchRoot $api -ReportNames $reports
Set-Location $api
& (Join-Path $api 'mvnw.cmd') '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=true' test
if ($LASTEXITCODE -ne 0) { throw 'Full backend unit suite failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Surefire -SearchRoot $api -ReportNames $reports -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
$reportFiles = foreach ($name in $reports) {
    $hits = @(Get-ChildItem -LiteralPath $api -Recurse -File -Filter $name | Where-Object { $_.Directory.Name -eq 'surefire-reports' })
    if ($hits.Count -ne 1) { throw "Unit manifest requires one exact report: $name" }
    $hits[0].FullName
}
function Copy-CreateNewUnitSnapshot([string]$Source) {
    $name = [IO.Path]::GetFileName($Source)
    $raw = (& git rev-parse --git-path "p1-evidence/$candidateHead/unit-reports/$name").Trim()
    $target = if ([IO.Path]::IsPathRooted($raw)) { [IO.Path]::GetFullPath($raw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $raw)) }
    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force)
    $bytes = [IO.File]::ReadAllBytes($Source)
    $sourceHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
    if (Test-Path -LiteralPath $target -PathType Leaf) {
        if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -cne $sourceHash) {
            throw "Existing immutable unit snapshot differs: $name"
        }
    } else {
        $stream = [IO.File]::Open($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -cne $sourceHash) { throw "Unit snapshot readback differs: $name" }
    return $target
}
$unitSnapshots = @($reportFiles | ForEach-Object { Copy-CreateNewUnitSnapshot $_ })
if ($unitSnapshots.Count -ne $reports.Count) { throw 'Immutable unit snapshot count drifted' }
$manifestResult = & $manifestGate -Kind unit -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths $unitSnapshots -SummaryJson (([ordered]@{status='PASS'; reportCount=$unitSnapshots.Count}) | ConvertTo-Json -Compress)
if ($manifestResult -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/unit\.manifest\.json\|[0-9a-f]{64}$') { throw 'Unit manifest sentinel missing' }
```

### 全量 IT

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$jvmPath = (& git rev-parse --git-path 'p1-jvm-evidence-gate.ps1').Trim()
$jvmGate = if ([IO.Path]::IsPathRooted($jvmPath)) { [IO.Path]::GetFullPath($jvmPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $jvmPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $jvmGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$api = Join-Path $repoRoot 'ai-video-api'
$candidateHead = (& git rev-parse HEAD).Trim()
$manifestPath = (& git rev-parse --git-path 'p1-evidence-manifest-gate.ps1').Trim()
$manifestGate = if ([IO.Path]::IsPathRooted($manifestPath)) { [IO.Path]::GetFullPath($manifestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $manifestPath)) }
$integrationPath = (& git rev-parse --git-path 'p1-f1-integration.json').Trim()
$integrationFile = if ([IO.Path]::IsPathRooted($integrationPath)) { [IO.Path]::GetFullPath($integrationPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $integrationPath)) }
$f1Head = (Get-Content -LiteralPath $integrationFile -Raw | ConvertFrom-Json).f1Head
$windowPath = (& git rev-parse --git-path "p1-acceptance-window-$candidateHead.json").Trim()
$windowFile = if ([IO.Path]::IsPathRooted($windowPath)) { [IO.Path]::GetFullPath($windowPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $windowPath)) }
$window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $manifestGate -PathType Leaf) -or $window.candidateHead -cne $candidateHead -or $window.f1Head -cne $f1Head) { throw 'IT evidence binding is missing or drifted' }
$reports = @(
    'TEST-org.dromara.aivideo.knowledge.P1KnowledgeMigrationIT.xml',
    'TEST-org.dromara.aivideo.knowledge.KnowledgeImportFlowIT.xml',
    'TEST-org.dromara.aivideo.knowledge.PlatformKnowledgeAssemblyIT.xml'
)
$started = & $jvmGate -Mode Prepare -Kind Failsafe -SearchRoot $api -ReportNames $reports
Set-Location $api
$itOutput = @(& (Join-Path $api 'mvnw.cmd') '-Dmaven.test.skip=false' '-DskipTests=false' '-DskipITs=false' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Pdev,local-integration-test' verify 2>&1)
$itExit = $LASTEXITCODE
$itText = ($itOutput | Out-String -Width 4096)
Write-Output $itText
if ($itExit -ne 0) { throw 'Full local integration suite failed' }
if ((& $jvmGate -Mode AssertGreen -Kind Failsafe -SearchRoot $api -ReportNames $reports -StartedAtUtc $started) -ne 'P1_JVM_EVIDENCE_OK') { throw 'JVM sentinel missing' }
$itTranscriptRaw = (& git rev-parse --git-path "p1-evidence/$candidateHead/full-it.verify.log").Trim()
$itTranscript = if ([IO.Path]::IsPathRooted($itTranscriptRaw)) { [IO.Path]::GetFullPath($itTranscriptRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $itTranscriptRaw)) }
[void](New-Item -ItemType Directory -Path (Split-Path -Parent $itTranscript) -Force)
$itBytes = [Text.UTF8Encoding]::new($false).GetBytes($itText)
if (Test-Path -LiteralPath $itTranscript -PathType Leaf) {
    $expectedItHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($itBytes)).ToLowerInvariant()
    if ((Get-FileHash -LiteralPath $itTranscript -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedItHash) { throw 'Existing full IT transcript differs; open a new acceptance window' }
} else {
    $stream = [IO.File]::Open($itTranscript, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($itBytes, 0, $itBytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}
$reportFiles = foreach ($name in $reports) {
    $hits = @(Get-ChildItem -LiteralPath $api -Recurse -File -Filter $name | Where-Object { $_.Directory.Name -eq 'failsafe-reports' })
    if ($hits.Count -ne 1) { throw "IT manifest requires one exact report: $name" }
    $hits[0].FullName
}
$itManifest = & $manifestGate -Kind it -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths $reportFiles -SummaryJson (([ordered]@{status='PASS'; reportCount=$reportFiles.Count}) | ConvertTo-Json -Compress)
if ($itManifest -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/it\.manifest\.json\|[0-9a-f]{64}$') { throw 'IT manifest sentinel missing' }
$migrationReport = @($reportFiles | Where-Object { [IO.Path]::GetFileName($_) -ceq 'TEST-org.dromara.aivideo.knowledge.P1KnowledgeMigrationIT.xml' })
if ($migrationReport.Count -ne 1) { throw 'Migration evidence requires one exact migration IT report' }
$migrationArtifacts = @($migrationReport[0], $itTranscript)
$migrationManifest = & $manifestGate -Kind migration -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths $migrationArtifacts -SummaryJson (([ordered]@{status='PASS'; artifactCount=$migrationArtifacts.Count}) | ConvertTo-Json -Compress)
if ($migrationManifest -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/migration\.manifest\.json\|[0-9a-f]{64}$') { throw 'Migration manifest sentinel missing' }
```

### 全量前端、lint 与 build

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
$vitestPath = (& git rev-parse --git-path 'p1-vitest-evidence-gate.ps1').Trim()
$vitestGate = if ([IO.Path]::IsPathRooted($vitestPath)) { [IO.Path]::GetFullPath($vitestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $vitestPath)) }
$reportPath = (& git rev-parse --git-path 'p1-full-vitest.json').Trim()
$report = if ([IO.Path]::IsPathRooted($reportPath)) { [IO.Path]::GetFullPath($reportPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reportPath)) }
if (-not (Test-Path -LiteralPath $gate) -or -not (Test-Path -LiteralPath $vitestGate)) { throw 'P1 gates are missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$ui = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$candidateHead = (& git rev-parse HEAD).Trim()
$manifestPath = (& git rev-parse --git-path 'p1-evidence-manifest-gate.ps1').Trim()
$manifestGate = if ([IO.Path]::IsPathRooted($manifestPath)) { [IO.Path]::GetFullPath($manifestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $manifestPath)) }
$integrationPath = (& git rev-parse --git-path 'p1-f1-integration.json').Trim()
$integrationFile = if ([IO.Path]::IsPathRooted($integrationPath)) { [IO.Path]::GetFullPath($integrationPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $integrationPath)) }
$f1Head = (Get-Content -LiteralPath $integrationFile -Raw | ConvertFrom-Json).f1Head
$windowPath = (& git rev-parse --git-path "p1-acceptance-window-$candidateHead.json").Trim()
$windowFile = if ([IO.Path]::IsPathRooted($windowPath)) { [IO.Path]::GetFullPath($windowPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $windowPath)) }
$window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $manifestGate -PathType Leaf) -or $window.candidateHead -cne $candidateHead -or $window.f1Head -cne $f1Head) { throw 'Vitest evidence binding is missing or drifted' }
function Write-FixedFrontendArtifact([string]$Name, [string]$Text) {
    $raw = (& git rev-parse --git-path "p1-evidence/$candidateHead/$Name").Trim()
    $path = if ([IO.Path]::IsPathRooted($raw)) { [IO.Path]::GetFullPath($raw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $raw)) }
    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $expectedHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedHash) { throw "Existing frontend artifact differs: $Name" }
    } else {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    }
    return $path
}
$started = & $vitestGate -Mode Prepare -ReportPath $report
Set-Location $ui
pnpm exec vitest run --reporter=json "--outputFile=$report"
if ($LASTEXITCODE -ne 0) { throw 'Full Vitest suite failed' }
if ((& $vitestGate -Mode AssertGreen -ReportPath $report -StartedAtUtc $started) -ne 'P1_VITEST_EVIDENCE_OK') { throw 'Vitest sentinel missing' }
$lintOutput = @(pnpm lint 2>&1)
$lintExit = $LASTEXITCODE
$lintText = ($lintOutput | Out-String -Width 4096)
Write-Output $lintText
if ($lintExit -ne 0) { throw 'Frontend lint failed' }
$lintArtifact = Write-FixedFrontendArtifact 'frontend-lint.log' $lintText
$buildOutput = @(pnpm build 2>&1)
$buildExit = $LASTEXITCODE
$buildText = ($buildOutput | Out-String -Width 4096)
Write-Output $buildText
if ($buildExit -ne 0) { throw 'Frontend build failed' }
$buildArtifact = Write-FixedFrontendArtifact 'frontend-build.log' $buildText
$frontendArtifacts = @($report,$lintArtifact,$buildArtifact)
$vitestManifest = & $manifestGate -Kind vitest -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths $frontendArtifacts -SummaryJson (([ordered]@{status='PASS'; artifactCount=$frontendArtifacts.Count}) | ConvertTo-Json -Compress)
if ($vitestManifest -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/vitest\.manifest\.json\|[0-9a-f]{64}$') { throw 'Vitest manifest sentinel missing' }
```

---

### 规范与静态扫描

扫描必须输出任务卡数、PowerShell 块数、AST 错误数、动态 root/gate 覆盖数、稳定文件数、旧层/旧标识命中数和前端矩阵命中数。

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
if (-not (Test-Path -LiteralPath $gate)) { throw 'P1 worktree gate is missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$candidateHead = (& git rev-parse HEAD).Trim()
$manifestPath = (& git rev-parse --git-path 'p1-evidence-manifest-gate.ps1').Trim()
$manifestGate = if ([IO.Path]::IsPathRooted($manifestPath)) { [IO.Path]::GetFullPath($manifestPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $manifestPath)) }
$integrationPath = (& git rev-parse --git-path 'p1-f1-integration.json').Trim()
$integrationFile = if ([IO.Path]::IsPathRooted($integrationPath)) { [IO.Path]::GetFullPath($integrationPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $integrationPath)) }
$f1Head = (Get-Content -LiteralPath $integrationFile -Raw | ConvertFrom-Json).f1Head
$windowPath = (& git rev-parse --git-path "p1-acceptance-window-$candidateHead.json").Trim()
$windowFile = if ([IO.Path]::IsPathRooted($windowPath)) { [IO.Path]::GetFullPath($windowPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $windowPath)) }
$window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
if (-not (Test-Path -LiteralPath $manifestGate -PathType Leaf) -or $window.candidateHead -cne $candidateHead -or $window.f1Head -cne $f1Head) { throw 'Standards/scan evidence binding is missing or drifted' }
function Write-FixedValidationArtifact([string]$Name, [string]$Text) {
    $raw = (& git rev-parse --git-path "p1-evidence/$candidateHead/$Name").Trim()
    $path = if ([IO.Path]::IsPathRooted($raw)) { [IO.Path]::GetFullPath($raw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $raw)) }
    [void](New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $expectedHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)).ToLowerInvariant()
        if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $expectedHash) { throw "Existing validation artifact differs: $Name" }
    } else {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    }
    return $path
}
$standardsOutput = @(& (Join-Path $repoRoot 'scripts/validate-development-standards.ps1') 2>&1)
$standardsExit = $LASTEXITCODE
$standardsText = ($standardsOutput | Out-String -Width 4096)
Write-Output $standardsText
if ($standardsExit -ne 0) { throw 'Development standards validation failed' }
$standardsArtifact = Write-FixedValidationArtifact 'standards.log' $standardsText

$plan = Join-Path $repoRoot 'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md'
$content = Get-Content -LiteralPath $plan -Raw -Encoding utf8
$tasks = [regex]::Matches($content, '(?m)^## Task \d+：')
if ($tasks.Count -ne 10) { throw "Expected 10 tasks, got $($tasks.Count)" }
$cardLabels = @('单一目标／不做','权威来源','风险／触发','所有权／数据范围','依赖／人员／并发','允许影响','成功／反向验收','固定输出')
foreach ($label in $cardLabels) {
    $count = [regex]::Matches($content, [regex]::Escape("**$label：**")).Count
    if ($count -ne 10) { throw "Task-card field $label count is $count" }
}
if ([regex]::IsMatch($content, '(?i)(?<![A-Za-z0-9_])[A-Z]:\\')) { throw 'Hard-coded Windows absolute path found' }
if ([regex]::IsMatch($content, '(?m)-D(?:it\.)?test=[^''"\s]+,')) { throw 'Comma-separated Maven selector found' }
$itLines = [regex]::Matches($content, '(?m)^.*-Dit\.test=.*$')
if ($itLines.Count -ne 5) { throw "Expected exactly five selector IT commands, got $($itLines.Count)" }
$expectedItInvocations = [ordered]@{ P1KnowledgeMigrationIT=2; KnowledgeImportFlowIT=1; PlatformKnowledgeAssemblyIT=2 }
foreach ($line in $itLines) {
    foreach ($flag in @('-Dmaven.test.skip=false','-DskipTests=false','-DskipITs=false','-Dfailsafe.failIfNoSpecifiedTests=false','-Pdev,local-integration-test','verify')) {
        if ($line.Value -notmatch [regex]::Escape($flag)) { throw "IT command is missing $flag" }
    }
    if ($line.Value -notmatch '-Dit\.test=(?<class>[A-Za-z][A-Za-z0-9]*IT)(?:''|"|\s)') { throw 'IT command must select one exact IT class' }
}
foreach ($entry in $expectedItInvocations.GetEnumerator()) {
    $actualCount = @($itLines | Where-Object { $_.Value -match ('-Dit\.test=' + [regex]::Escape($entry.Key) + '(?:''|"|\s)') }).Count
    if ($actualCount -ne $entry.Value) { throw "IT invocation count drifted for $($entry.Key): $actualCount" }
}
$selectorlessVerify = [regex]::Matches($content, '(?m)^.*mvnw\.cmd.*-Pdev,local-integration-test.*\sverify(?:\s+2>&1)?\)?\s*$') | Where-Object { $_.Value -notmatch '-Dit\.test=' }
if (@($selectorlessVerify).Count -ne 1) { throw 'Exactly one selectorless full verify with local profile is required' }
$vitestPathMatches = [regex]::Matches($content, '\$reportPath\s*=\s*\(& git rev-parse --git-path ''(?<path>[^'']+\.json)''\)')
$vitestReportPaths = @($vitestPathMatches | ForEach-Object { $_.Groups['path'].Value })
if ($vitestReportPaths.Count -ne 7 -or @($vitestReportPaths | Sort-Object -Unique).Count -ne 7) { throw 'Seven Vitest runs require seven unique metadata JSON paths' }

$ticks = ([string][char]96) + [char]96 + [char]96
$blockPattern = '(?ms)^' + [regex]::Escape($ticks) + 'powershell\r?\n(?<body>.*?)^' + [regex]::Escape($ticks) + '[ \t]*\r?$'
$lfContent = $content.Replace("`r`n", "`n")
$crlfContent = $lfContent.Replace("`n", "`r`n")
$lfBlockCount = [regex]::Matches($lfContent, $blockPattern).Count
$crlfBlockCount = [regex]::Matches($crlfContent, $blockPattern).Count
if ($lfBlockCount -ne 37 -or $crlfBlockCount -ne 37) { throw "PowerShell fence scan must find 37 blocks under LF and CRLF, got $lfBlockCount/$crlfBlockCount" }
$blocks = [regex]::Matches($content, $blockPattern)
if ($blocks.Count -ne 37) { throw "Expected exactly 37 PowerShell blocks, got $($blocks.Count)" }
$astErrors = 0
foreach ($block in $blocks) {
    $tokens = $null
    $errors = $null
    $body = $block.Groups['body'].Value
    [void][Management.Automation.Language.Parser]::ParseInput($body, [ref]$tokens, [ref]$errors)
    $astErrors += $errors.Count
    if ($body -notmatch 'git rev-parse --show-toplevel') { throw 'PowerShell block lacks dynamic repo root' }
    if ($body -notmatch 'p1-worktree-gate\.ps1') { throw 'PowerShell block lacks unified gate' }
    if ($body -notmatch '\$ErrorActionPreference\s*=\s*''Stop''') { throw 'PowerShell block is not fail-closed' }
    if ($body -notmatch 'P1_WORKTREE_GATE_OK') { throw 'PowerShell block lacks worktree sentinel validation' }
}
if ($astErrors -ne 0) { throw "PowerShell AST errors: $astErrors" }

$core = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge'
$platform = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/main/java/org/dromara/aivideo'
$expectedDtos = @('KnowledgePlanDTO.java','KnowledgeRouteRequestDTO.java','KnowledgeRouteResultDTO.java','KnowledgeSnapshotDTO.java','KnowledgeSnapshotRequestDTO.java')
$actualDtos = @(Get-ChildItem -LiteralPath (Join-Path $core 'dto') -File -Filter '*.java' | Select-Object -ExpandProperty Name | Sort-Object)
if (Compare-Object ($expectedDtos | Sort-Object) $actualDtos) { throw 'Stable DTO registry differs from the five frozen files' }
function Assert-RecordHeader([string]$Source, [string]$RecordName, [string[]]$ExpectedComponents) {
    $pattern = '(?s)(?:public\s+)?record\s+' + [regex]::Escape($RecordName) + '\s*\((?<components>.*?)\)\s*\{'
    $match = [regex]::Match($Source, $pattern)
    if (-not $match.Success) { throw "Record header missing: $RecordName" }
    $actual = @($match.Groups['components'].Value -split ',' | ForEach-Object { ($_ -replace '\s+', ' ').Trim() })
    if ($actual.Count -ne $ExpectedComponents.Count) { throw "$RecordName component count drifted" }
    for ($i=0; $i -lt $ExpectedComponents.Count; $i++) {
        if (-not [string]::Equals($actual[$i], $ExpectedComponents[$i], [StringComparison]::Ordinal)) {
            throw "$RecordName component $i drifted: $($actual[$i])"
        }
    }
}
$dtoSources = [ordered]@{
    KnowledgeRouteRequestDTO = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeRouteRequestDTO.java') -Raw
    KnowledgePlanDTO = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgePlanDTO.java') -Raw
    KnowledgeRouteResultDTO = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeRouteResultDTO.java') -Raw
    KnowledgeSnapshotRequestDTO = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeSnapshotRequestDTO.java') -Raw
    KnowledgeSnapshotDTO = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeSnapshotDTO.java') -Raw
}
Assert-RecordHeader $dtoSources.KnowledgeRouteRequestDTO 'KnowledgeRouteRequestDTO' @('Long directionCatalogVersionId','String industryCode','String purposeCode','Integer targetDurationSeconds','List<String> tagCodes')
Assert-RecordHeader $dtoSources.KnowledgePlanDTO 'KnowledgePlanDTO' @('String candidateCode','String planCode','Long primaryTemplateVersionId','String angleCode','String differentiatorTechniqueCode')
Assert-RecordHeader $dtoSources.KnowledgeRouteResultDTO 'KnowledgeRouteResultDTO' @('String routingVersion','String videoTypeCode','List<KnowledgePlanDTO> plans','String contentHash')
Assert-RecordHeader $dtoSources.KnowledgeSnapshotRequestDTO 'KnowledgeSnapshotRequestDTO' @('Long rootTaskId','Long promptVersionId','Long generationContextRevision','String generationInputHash','KnowledgeRouteResultDTO route','List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts')
Assert-RecordHeader $dtoSources.KnowledgeSnapshotRequestDTO 'AcceptedFactSnapshotDTO' @('Long factId','Long decisionRevision','String factText','String evidenceRef')
Assert-RecordHeader $dtoSources.KnowledgeSnapshotDTO 'KnowledgeSnapshotDTO' @('Long snapshotId','Long rootTaskId','Long promptVersionId','Long generationContextRevision','String generationInputHash','KnowledgeRouteResultDTO route','List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts','List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials','String contentHash','Instant createdAt')
Assert-RecordHeader $dtoSources.KnowledgeSnapshotDTO 'KnowledgeMaterialSnapshotDTO' @('Long knowledgeVersionId','Long bindingVersionId','Long videoRuleVersionId','String contentExcerpt','Integer injectionOrder')
$expectedServices = @('IKnowledgeCatalogService.java','IKnowledgeImportService.java','IKnowledgePublicationService.java','IKnowledgeRoutingService.java','IKnowledgeSnapshotService.java')
$actualServices = @(Get-ChildItem -LiteralPath (Join-Path $core 'service') -File -Filter '*.java' | Select-Object -ExpandProperty Name | Sort-Object)
if (Compare-Object ($expectedServices | Sort-Object) $actualServices) { throw 'Knowledge Service registry differs from the five fixed interfaces' }
$routeSource = Get-Content -LiteralPath (Join-Path $core 'service/IKnowledgeRoutingService.java') -Raw
$snapshotSource = Get-Content -LiteralPath (Join-Path $core 'service/IKnowledgeSnapshotService.java') -Raw
$importSource = Get-Content -LiteralPath (Join-Path $core 'service/IKnowledgeImportService.java') -Raw
if ($routeSource -notmatch 'KnowledgeRouteResultDTO\s+route\s*\(\s*KnowledgeRouteRequestDTO\s+request\s*\)') { throw 'Routing signature drifted' }
if ($snapshotSource -notmatch 'KnowledgeSnapshotDTO\s+create\s*\(\s*KnowledgeSnapshotRequestDTO\s+request\s*\)') { throw 'Snapshot create signature drifted' }
if ($snapshotSource -notmatch 'KnowledgeSnapshotDTO\s+getByRootTaskId\s*\(\s*Long\s+rootTaskId\s*\)') { throw 'Snapshot query signature drifted' }
if ($importSource -notmatch 'ImportResult\s+create\s*\(\s*ImportRequest\s+request\s*,\s*TaskInitiatorDTO\s+initiator\s*\)') { throw 'Import create signature drifted' }
Assert-RecordHeader $importSource 'ImportRequest' @('Long batchId','String idempotencyKey')
Assert-RecordHeader $importSource 'ImportResult' @('Long batchId','Long rootTaskId','Long executionTaskId','boolean reused')
foreach ($badDir in @(
    (Join-Path $core ('app' + 'lication')),(Join-Path $core ('rout' + 'ing')),
    (Join-Path $core ('po' + 'rt')),(Join-Path $core ('adap' + 'ter')),(Join-Path $core ('com' + 'mand')),(Join-Path $core ('mo' + 'del')),
    (Join-Path $core 'domain/bo'),(Join-Path $core 'domain/vo')
)) {
    if (Test-Path -LiteralPath $badDir) { throw "Forbidden core directory: $badDir" }
}
$enumDir = Join-Path $core 'domain/enums'
if (Test-Path -LiteralPath $enumDir) { throw 'Knowledge enums must stay at the domain root' }
$expectedInternalDtos = @('KnowledgeTemplateCandidateDTO.java','RequiredKnowledgeRuleDTO.java')
$actualInternalDtos = @(Get-ChildItem -LiteralPath (Join-Path $core 'dto/internal') -File -Filter '*.java' | Select-Object -ExpandProperty Name | Sort-Object)
if (Compare-Object ($expectedInternalDtos | Sort-Object) $actualInternalDtos) { throw 'Internal DTO registry drifted' }
$oldIdentifiers = @(
    ('Template' + 'Candidate'),('Required' + 'Rule'),('Plan' + 'Triple'),('AcceptedFact' + 'Snapshot'),
    ('FrozenKnowledge' + 'Payload'),('KnowledgeSnapshot' + 'Vo'),('VideoType' + 'Router'),
    ('requiredRule' + 'Validator'),('plan' + 'Assembler'),('context' + 'Budget'),
    ('snapshot' + 'Assembler'),('canonicalJson' + 'Writer'),('id' + 'Generator'),
    ('KnowledgePublication' + 'Validator'),('FreeTask' + 'Command'),('TaskCreation' + 'Result'),
    ('TaskResult' + 'Reference'),('AiTaskExecution' + 'Lease'),('AiTask' + 'Service'),
    ('AiTaskExecution' + 'Dispatcher'),('AiTaskExecution' + 'Handler')
)
$infraKnowledge = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/knowledge'
$platformKnowledge = Join-Path $platform 'knowledge'
$javaFiles = @(
    Get-ChildItem -LiteralPath $core,$infraKnowledge,$platformKnowledge -Recurse -File -Filter '*.java'
)
foreach ($name in $oldIdentifiers) {
    $pattern = '\b' + [regex]::Escape($name) + '\b'
    if ($javaFiles | Select-String -Pattern $pattern) { throw "Old or parallel identifier found in core: $name" }
}
$frozenP0Types = @('AiTaskType','DirectionCatalogSnapshotDTO','TaskInitiatorDTO','FreeTaskDTO','TaskRevisionSnapshotDTO','TaskCreationResultDTO','AiTaskExecutionLeaseDTO','TaskResultReferenceDTO')
foreach ($type in $frozenP0Types) {
    if ($javaFiles | Select-String -Pattern ('\b(?:class|record|enum|interface)\s+' + [regex]::Escape($type) + '\b')) { throw "P1 copied a P0-C type: $type" }
}
$resolverName = ('TaskInitiator' + 'Resolver')
if ((Get-ChildItem -LiteralPath $core -Recurse -File -Filter '*.java') | Select-String -SimpleMatch $resolverName) { throw 'Endpoint actor resolver leaked into core' }
$forbiddenTaskCalls = @(
    ('IAiTask' + 'AttemptService'),
    ('claimExecutable' + 'Tasks('),
    ('renew' + 'Lease('),
    ('recordHandler' + 'Failure(')
)
foreach ($token in $forbiddenTaskCalls) {
    if ($javaFiles | Select-String -SimpleMatch $token) { throw "Forbidden P0-C task dependency/call in P1: $token" }
}
$listener = Join-Path $infraKnowledge 'listener/KnowledgeImportTaskHandler.java'
$listenerSource = Get-Content -LiteralPath $listener -Raw
foreach ($required in @('implements IAiTaskExecutionHandler','AiTaskType.KNOWLEDGE_IMPORT','markSuccess(','markFailed(','TaskResultReferenceDTO.of("knowledge_import_batch"')) {
    if ($listenerSource -notmatch [regex]::Escape($required)) { throw "Import listener is missing: $required" }
}
$outsideInfra = @(Get-ChildItem -LiteralPath $core,$platform -Recurse -File -Filter '*.java')
if ($outsideInfra | Select-String -Pattern 'package\s+.*\.knowledge\.(provider|client)(\.|;)') { throw 'Direct provider/client package exists outside infra' }
$publicationSource = Get-Content -LiteralPath (Join-Path $core 'service/impl/KnowledgePublicationServiceImpl.java') -Raw
if ($publicationSource -notmatch 'private\s+void\s+validateForPublication\s*\(\s*KnowledgeVersion\s+version\s*\)') { throw 'Publication validation is not the required private method' }
$publicationContract = Get-Content -LiteralPath (Join-Path $core 'service/IKnowledgePublicationService.java') -Raw
foreach ($signature in @(
    'submitReview\s*\(\s*Long\s+versionId\s*,\s*int\s+expectedRevision\s*,\s*TaskInitiatorDTO\s+initiator\s*\)',
    'publish\s*\(\s*Long\s+versionId\s*,\s*int\s+expectedRevision\s*,\s*String\s+publishNote\s*,\s*TaskInitiatorDTO\s+initiator\s*\)',
    'retire\s*\(\s*Long\s+versionId\s*,\s*int\s+expectedRevision\s*,\s*TaskInitiatorDTO\s+initiator\s*\)'
)) { if ($publicationContract -notmatch $signature) { throw "Final publication actor-bound signature drifted: $signature" } }
foreach ($requiredActorUse in @('requireActorType("sys_user")','actorId()','Clock')) {
    if ($publicationSource -notmatch [regex]::Escape($requiredActorUse)) { throw "Publication implementation is missing trusted actor/time use: $requiredActorUse" }
}
foreach ($method in @('resolveVideoType','validateRequiredRules','toPlan','enforceContextBudget','freezePayload','toCanonicalJson')) {
    if (-not ($javaFiles | Select-String -Pattern ('private\s+.*\b' + [regex]::Escape($method) + '\s*\('))) { throw "Missing private pure method: $method" }
}
$requestDtoSource = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeSnapshotRequestDTO.java') -Raw
$snapshotDtoSource = Get-Content -LiteralPath (Join-Path $core 'dto/KnowledgeSnapshotDTO.java') -Raw
if ($requestDtoSource -notmatch 'List<\s*KnowledgeSnapshotRequestDTO\.AcceptedFactSnapshotDTO\s*>\s+acceptedFacts') { throw 'acceptedFacts type drifted' }
foreach ($field in @('knowledgeVersionId','bindingVersionId','videoRuleVersionId','contentExcerpt','injectionOrder')) {
    if ($snapshotDtoSource -notmatch ('\b' + $field + '\b')) { throw "Snapshot material field is missing: $field" }
}
$snapshotImplSource = Get-Content -LiteralPath (Join-Path $core 'service/impl/KnowledgeSnapshotServiceImpl.java') -Raw
$snapshotTestPath = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/knowledge/KnowledgeSnapshotServiceTest.java'
$snapshotTestSource = Get-Content -LiteralPath $snapshotTestPath -Raw
$hashFields = @('rootTaskId','promptVersionId','generationContextRevision','generationInputHash','route','acceptedFacts','knowledgeMaterials')
$canonicalStart = $snapshotImplSource.IndexOf('toCanonicalJson', [StringComparison]::Ordinal)
if ($canonicalStart -lt 0) { throw 'Snapshot canonical serializer is missing' }
$canonicalSlice = $snapshotImplSource.Substring($canonicalStart, [Math]::Min(6000, $snapshotImplSource.Length - $canonicalStart))
$cursor = -1
foreach ($field in $hashFields) {
    $next = $canonicalSlice.IndexOf($field, $cursor + 1, [StringComparison]::Ordinal)
    if ($next -lt 0) { throw "Canonical hash omits or reorders immutable payload field: $field" }
    $cursor = $next
    if ($snapshotTestSource -notmatch ('\b' + [regex]::Escape($field) + '\b')) { throw "Snapshot hash test omits payload field: $field" }
}
if ($snapshotTestSource -notmatch '\bcanonicalHashChangesForEveryImmutablePayloadField\s*\(') { throw 'Snapshot test must mutate every immutable payload field under one named contract test' }
$formalMigration = Join-Path $repoRoot 'docs/sql/ai-video/mysql/20260728_05_p1_knowledge.sql'
$draftMigration = $formalMigration + '.draft'
if (-not (Test-Path -LiteralPath $formalMigration -PathType Leaf) -or (Test-Path -LiteralPath $draftMigration)) { throw 'F2 requires formal 05 and no leftover draft file' }
$controllerPath = Join-Path $platformKnowledge 'controller/KnowledgeController.java'
$dynamicPagePath = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui/src/pages/dynamicPage.tsx'
$mockPath = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui/mock/aivideo-knowledge.ts'
foreach ($requiredPath in @($controllerPath,$dynamicPagePath,$mockPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Required endpoint file missing: $requiredPath" }
}

$uiRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-platform-ui'
$indexTestPath = Join-Path $uiRoot 'src/pages/aivideo/knowledge/index.test.tsx'
$drawerTestPath = Join-Path $uiRoot 'src/pages/aivideo/knowledge/components/KnowledgeImportReviewDrawer.test.tsx'
$indexTest = Get-Content -LiteralPath $indexTestPath -Raw
$drawerTest = Get-Content -LiteralPath $drawerTestPath -Raw
$indexStates = @('loading','initial-empty','search-empty','pagination-and-refresh','network-or-5xx-retry','401-single-logout','403-forbidden','draft-review-publish','reference-check-and-confirm','revision-conflict','retire-confirmation')
$drawerStates = @('file-validation','parsing','conflict','failed','duplicate-submit-guard','cancel-is-not-failure','queued','running','success','failure','cancel','retry')
$typescriptModule = Join-Path $uiRoot 'node_modules/typescript/lib/typescript.js'
if (-not (Test-Path -LiteralPath $typescriptModule -PathType Leaf)) { throw 'TypeScript compiler API is required for exact frontend state scanning' }
$scannerRaw = (& git rev-parse --git-path ('p1-state-scanner-' + [Guid]::NewGuid().ToString('N') + '.cjs')).Trim()
$scannerFile = if ([IO.Path]::IsPathRooted($scannerRaw)) { [IO.Path]::GetFullPath($scannerRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $scannerRaw)) }
$stateScannerSource = @'
const fs = require('fs');
const ts = require(process.argv[2]);
const file = process.argv[3];
const expected = JSON.parse(process.argv[4]);
if (new Set(expected).size !== expected.length) throw new Error('duplicate expected state');
const source = ts.createSourceFile(file, fs.readFileSync(file, 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
if (source.parseDiagnostics.length) throw new Error('TypeScript parse failed');
const hits = new Map(expected.map((name) => [name, []]));
let assertionGuards = 0;
let skipped = 0;
const isExpectAssertions = (node) => ts.isCallExpression(node) &&
  ts.isPropertyAccessExpression(node.expression) && node.expression.name.text === 'assertions' &&
  ts.isIdentifier(node.expression.expression) && node.expression.expression.text === 'expect';
const countGuards = (node) => {
  let count = 0;
  const visit = (child) => { if (isExpectAssertions(child)) count++; ts.forEachChild(child, visit); };
  visit(node);
  return count;
};
const visit = (node) => {
  if (ts.isCallExpression(node)) {
    const callee = node.expression;
    if (ts.isIdentifier(callee) && ['xit', 'xtest'].includes(callee.text)) skipped++;
    if (ts.isPropertyAccessExpression(callee) && ts.isIdentifier(callee.expression) &&
        ['it', 'test'].includes(callee.expression.text) && ['skip', 'todo', 'only', 'each'].includes(callee.name.text)) skipped++;
    if (ts.isIdentifier(callee) && ['it', 'test'].includes(callee.text)) {
      if (node.arguments.length < 2 || !ts.isStringLiteral(node.arguments[0])) throw new Error('dynamic or incomplete state test is forbidden');
      const name = node.arguments[0].text;
      if (!hits.has(name)) throw new Error(`unexpected state test: ${name}`);
      const callback = node.arguments[1];
      if ((!ts.isArrowFunction(callback) && !ts.isFunctionExpression(callback)) || !ts.isBlock(callback.body)) throw new Error(`state ${name} requires a direct block callback`);
      const statements = callback.body.statements;
      if (!statements.length || !ts.isExpressionStatement(statements[0]) || !isExpectAssertions(statements[0].expression)) throw new Error(`state ${name} must start with expect.assertions`);
      const guard = statements[0].expression;
      if (guard.arguments.length !== 1 || !ts.isNumericLiteral(guard.arguments[0]) || Number(guard.arguments[0].text) <= 0 || !Number.isInteger(Number(guard.arguments[0].text))) throw new Error(`state ${name} assertion count must be a positive integer literal`);
      const guards = countGuards(callback.body);
      if (guards !== 1) throw new Error(`state ${name} must contain exactly one expect.assertions guard`);
      assertionGuards += guards;
      hits.get(name).push(node.pos);
    }
  }
  ts.forEachChild(node, visit);
};
visit(source);
let duplicates = 0;
let matchedStates = 0;
for (const [name, positions] of hits) {
  if (positions.length !== 1) { if (positions.length > 1) duplicates += positions.length - 1; throw new Error(`state ${name} appears ${positions.length} times`); }
  matchedStates++;
}
if (skipped !== 0) throw new Error(`forbidden skip/todo/only/each calls: ${skipped}`);
process.stdout.write(JSON.stringify({ expectedStates: expected.length, matchedStates, assertionGuards, duplicates, skipped }));
'@
[IO.File]::WriteAllText($scannerFile, $stateScannerSource, [Text.UTF8Encoding]::new($false))
try {
    $frontResults = @()
    foreach ($spec in @(@($indexTestPath,$indexStates),@($drawerTestPath,$drawerStates))) {
        $rawResult = @(& node $scannerFile $typescriptModule $spec[0] ($spec[1] | ConvertTo-Json -Compress))
        if ($LASTEXITCODE -ne 0) { throw "Frontend state AST scanner failed: $($spec[0])" }
        $frontResults += (($rawResult -join "`n") | ConvertFrom-Json)
    }
} finally {
    if (Test-Path -LiteralPath $scannerFile) { Remove-Item -LiteralPath $scannerFile }
}
$frontExpected = ($frontResults | Measure-Object -Property expectedStates -Sum).Sum
$frontMatched = ($frontResults | Measure-Object -Property matchedStates -Sum).Sum
$frontGuards = ($frontResults | Measure-Object -Property assertionGuards -Sum).Sum
$frontDuplicates = ($frontResults | Measure-Object -Property duplicates -Sum).Sum
$frontSkipped = ($frontResults | Measure-Object -Property skipped -Sum).Sum
if ($frontExpected -ne 23 -or $frontMatched -ne 23 -or $frontGuards -ne 23 -or $frontDuplicates -ne 0 -or $frontSkipped -ne 0) { throw 'Frontend state matrix must be exactly 23/23/23/0/0' }
if (($indexTest + $drawerTest) -match '(?i)\b(quota|balance|price)\b') { throw 'P1 frontend contains quota/price state' }
$productionTs = @(Get-ChildItem -LiteralPath (Join-Path $uiRoot 'src') -Recurse -File -Include '*.ts','*.tsx' | Where-Object {
    $_.FullName -notmatch '(?i)(?:[\\/](?:__tests__|mock)[\\/]|\.(?:test|spec)\.[^.]+$)'
})
if ($productionTs | Select-String -Pattern '(?i)(?:from\s*|import\s*\(|require\s*\()\s*[''"][^''"]*mock/aivideo-knowledge') { throw 'Production TypeScript imports mock/aivideo-knowledge' }
$testRoot = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video'
$knowledgeTests = @(Get-ChildItem -LiteralPath $testRoot -Recurse -File -Include '*Test.java','*IT.java' | Where-Object { $_.FullName -match '[\\/]knowledge[\\/]' })
foreach ($testFile in $knowledgeTests) {
    if ((Get-Content -LiteralPath $testFile.FullName -Raw) -notmatch '@Tag\("dev"\)') { throw "Missing dev tag: $($testFile.FullName)" }
}
$itSourceRoot = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-platform/src/test/java/org/dromara/aivideo/knowledge'
$itSources = @(
    (Join-Path $itSourceRoot 'P1KnowledgeMigrationIT.java'),
    (Join-Path $itSourceRoot 'KnowledgeImportFlowIT.java'),
    (Join-Path $itSourceRoot 'PlatformKnowledgeAssemblyIT.java')
)
$safeEnvironmentBindings = 0
foreach ($itSourcePath in $itSources) {
    if (-not (Test-Path -LiteralPath $itSourcePath -PathType Leaf)) { throw "Required IT source missing: $itSourcePath" }
    $itSource = Get-Content -LiteralPath $itSourcePath -Raw
    $itClassName = [IO.Path]::GetFileNameWithoutExtension($itSourcePath)
    $tagMatches = [regex]::Matches($itSource, '@Tag\(\s*"dev"\s*\)')
    if ($tagMatches.Count -ne 1) { throw "IT source requires exactly one dev tag: $itSourcePath" }
    $classAdjacentTag = '(?s)@Tag\(\s*"dev"\s*\)(?:\s*@[A-Za-z0-9_.$]+(?:\s*\(.*?\))?)*\s*(?:(?:public|abstract|final)\s+)*class\s+' + [regex]::Escape($itClassName) + '\b'
    if ($itSource -notmatch $classAdjacentTag) { throw "IT dev tag is not attached to the top-level class: $itSourcePath" }
    $environmentCalls = [regex]::Matches($itSource, 'LocalIntegrationEnvironment\.requireFromEnvironment\s*\(\s*\)')
    if ($environmentCalls.Count -ne 1) { throw "IT class requires exactly one safe environment binding: $itSourcePath" }
    if ($itSource -notmatch '(?s)static\s+final\s+LocalIntegrationEnvironment\s+\w+\s*=\s*LocalIntegrationEnvironment\.requireFromEnvironment\s*\(\s*\)\s*;.*?(?:@BeforeAll|@Test)') { throw "IT environment must be bound in static fixture initialization before tests: $itSourcePath" }
    if ($itSource -match 'LocalIntegrationEnvironment\.(?:from|create)\s*\(' -or $itSource -match '(?i)jdbc:(?:mysql|postgresql)|redis://') { throw "IT source bypasses LocalIntegrationEnvironment: $itSourcePath" }
    if (-not $expectedItInvocations.Contains($itClassName)) { throw "IT source has no verified command mapping: $itClassName" }
    $safeEnvironmentBindings += $environmentCalls.Count * [int]$expectedItInvocations[$itClassName]
}
if ($safeEnvironmentBindings -ne $itLines.Count) { throw 'Each of the five IT invocations must map to one verified safe environment binding' }
$scanResult = [ordered]@{
    tasks=$tasks.Count; taskCardFields=$cardLabels.Count; powershellBlocks=$blocks.Count; astErrors=$astErrors
    stableDtos=$actualDtos.Count; stableServices=$actualServices.Count; snapshotHashFields=$hashFields.Count
    itCommands=$itLines.Count; uniqueItClasses=$itSources.Count; safeEnvironmentBindings=$safeEnvironmentBindings
    vitestReports=$vitestReportPaths.Count; expectedStates=$frontExpected; matchedStates=$frontMatched
    assertionGuards=$frontGuards; duplicates=$frontDuplicates; skipped=$frontSkipped
}
$scanJson = $scanResult | ConvertTo-Json -Compress
Write-Output $scanJson
$scanArtifact = Write-FixedValidationArtifact 'scan.json' $scanJson
$standardsManifest = & $manifestGate -Kind standards -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths @($standardsArtifact) -SummaryJson (([ordered]@{status='PASS'; artifactCount=1}) | ConvertTo-Json -Compress)
if ($standardsManifest -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/standards\.manifest\.json\|[0-9a-f]{64}$') { throw 'Standards manifest sentinel missing' }
$scanManifest = & $manifestGate -Kind scan -CandidateHead $candidateHead -F1Head $f1Head -WindowStartedAtUtc $window.startedAtUtc -ArtifactPaths @($scanArtifact) -SummaryJson (([ordered]@{status='PASS'; artifactCount=1}) | ConvertTo-Json -Compress)
if ($scanManifest -cnotmatch '^P1_EVIDENCE_MANIFEST_OK=p1-evidence/.+/scan\.manifest\.json\|[0-9a-f]{64}$') { throw 'Scan manifest sentinel missing' }
```

### 独立 review

P1 writer 按 `requesting-code-review` 提交 `F1..candidateHead` diff、六类 fresh 证据、05 二次执行结果和扫描 JSON。独立 reviewer 检查正向验收及以下反向验收：未发布知识不得路由、非 copywriting 不得路由、快照不可变、ZIP 攻击被拒、重复任务不冻结/入队、无 attempt/usage/provider、401/403 分离、P2/P3 无 Mapper/表依赖。

reviewer（不得是 owner/writer）在当前 Git metadata 以 `CreateNew` 写 `p1-independent-review.json`。字段及顺序精确为 `owner`、`reviewer`、`reviewStatus`、`reviewedHead`、`f1Head`、`reviewCompletedAtUtc`、`revisionMappingContractOwner`、`evidence`；前七项必须是 JSON string，reviewer 与 owner 按 trim + 大小写不敏感归一化后不同，状态精确 `PASS`，时间显式 UTC 且晚于 acceptance start。

`evidence` 只允许 `unit`、`it`、`migration`、`vitest`、`standards`、`scan` 六项；每项只允许 `path`、`sha256` 两个 JSON string。`path` 必须精确等于 `p1-evidence/<reviewedHead>/<kind>.manifest.json`，SHA 必须是对应 manifest 实时重算的小写 64 hex。reviewer 必须逐一重算 manifest 及其全部 artifact，而不是接受 writer 提供的裸摘要哈希。

writer 不得代写 review 文件。文件若已存在，只允许 reviewer 对同一 `reviewedHead` 和同一 payload 幂等读取；不同 HEAD/内容拒绝覆盖。任何 FAIL、缺字段、额外字段、类型冒充、路径越界、旧 artifact 或 hash 不一致都阻断 F2。

### F2 幂等冻结

```powershell
$ErrorActionPreference = 'Stop'
$repoRoot = [IO.Path]::GetFullPath((& git rev-parse --show-toplevel).Trim())
$gatePath = (& git rev-parse --git-path 'p1-worktree-gate.ps1').Trim()
$gate = if ([IO.Path]::IsPathRooted($gatePath)) { [IO.Path]::GetFullPath($gatePath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gatePath)) }
if (-not (Test-Path -LiteralPath $gate)) { throw 'P1 worktree gate is missing' }
Set-Location $repoRoot
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree sentinel missing' }
$head = (& git rev-parse HEAD).Trim()
function Resolve-Metadata([string]$Name) {
    $raw = (& git rev-parse --git-path $Name).Trim()
    if ([IO.Path]::IsPathRooted($raw)) { return [IO.Path]::GetFullPath($raw) }
    return [IO.Path]::GetFullPath((Join-Path $repoRoot $raw))
}
function Assert-ExactFields($Value, [string[]]$Expected, [string]$Label) {
    if ($Value -isnot [pscustomobject]) { throw "$Label must be one JSON object" }
    $actual = @($Value.PSObject.Properties | ForEach-Object { $_.Name })
    if ($actual.Count -ne $Expected.Count) { throw "$Label field count drifted" }
    for ($i=0; $i -lt $Expected.Count; $i++) {
        if (-not [string]::Equals($actual[$i], $Expected[$i], [StringComparison]::Ordinal)) { throw "$Label fields/order drifted at index $i" }
    }
}
function Assert-String($Value, [string]$Label) {
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace($Value) -or $Value -ne $Value.Trim()) { throw "$Label must be a trimmed nonblank JSON string" }
}
function Assert-StringEquals($Value, [string]$Expected, [string]$Label) {
    Assert-String $Value $Label
    if (-not [string]::Equals($Value, $Expected, [StringComparison]::Ordinal)) { throw "$Label value drifted" }
}
function Assert-BoolEquals($Value, [bool]$Expected, [string]$Label) {
    if ($Value -isnot [bool] -or -not $Value.Equals($Expected)) { throw "$Label must be the exact JSON boolean $Expected" }
}
function Assert-Sha40($Value, [string]$Label) { Assert-String $Value $Label; if ($Value -cnotmatch '^[0-9a-f]{40}$') { throw "$Label must be lowercase 40-hex" } }
function Assert-Sha256($Value, [string]$Label) { Assert-String $Value $Label; if ($Value -cnotmatch '^[0-9a-f]{64}$') { throw "$Label must be lowercase SHA-256" } }
function Assert-Utc($Value, [string]$Label) {
    Assert-String $Value $Label
    if ($Value -cnotmatch '(?:Z|\+00:00)$') { throw "$Label must explicitly end in Z or +00:00" }
    $parsed = [DateTimeOffset]::Parse($Value, [Globalization.CultureInfo]::InvariantCulture)
    if ($parsed.Offset -ne [TimeSpan]::Zero) { throw "$Label must be UTC" }
    return $parsed
}
function Assert-StringArray($Value, [string[]]$Expected, [string]$Label) {
    if ($Value -isnot [System.Array] -or $Value.Count -ne $Expected.Count) { throw "$Label must be the exact JSON array" }
    for ($i=0; $i -lt $Expected.Count; $i++) {
        if ($Value[$i] -isnot [string] -or -not [string]::Equals($Value[$i], $Expected[$i], [StringComparison]::Ordinal)) { throw "$Label item $i drifted or is not a string" }
    }
}
function Assert-JsonNumber($Value, [string]$Label, [decimal]$Minimum) {
    if ($Value -is [string] -or $Value -is [bool] -or $Value -isnot [ValueType]) { throw "$Label must be a JSON number" }
    $number = [decimal]$Value
    if ($number -lt $Minimum -or $number -ne [Math]::Floor($number)) { throw "$Label must be an integer >= $Minimum" }
    return $number
}
function Assert-SafeRelativePath($Value, [string]$Label) {
    Assert-String $Value $Label
    if ([IO.Path]::IsPathRooted($Value) -or $Value -match '(^|[\\/])\.\.([\\/]|$)' -or $Value -match '\\') { throw "$Label is not a normalized relative path" }
}
function Assert-ArtifactPathContract($Artifact, [string]$Label) {
    Assert-ExactFields $Artifact @('pathScope','relativePath','sha256','bytes','lastWriteUtc') $Label
    Assert-String $Artifact.pathScope "$Label.pathScope"
    if ($Artifact.pathScope -cnotin @('repo','git-metadata')) { throw "$Label pathScope is invalid" }
    Assert-SafeRelativePath $Artifact.relativePath "$Label.relativePath"
}
function Resolve-ScopedArtifact([string]$Scope, [string]$RelativePath, [string]$GitMetadataRoot) {
    $root = if ($Scope -ceq 'repo') { $repoRoot } elseif ($Scope -ceq 'git-metadata') { $GitMetadataRoot } else { throw "Unknown artifact scope: $Scope" }
    $resolved = [IO.Path]::GetFullPath((Join-Path $root $RelativePath))
    if (-not ($resolved.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or $resolved.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase))) { throw 'Artifact path escapes declared scope' }
    return $resolved
}
$integrationPath = (& git rev-parse --git-path 'p1-f1-integration.json').Trim()
$integrationFile = if ([IO.Path]::IsPathRooted($integrationPath)) { [IO.Path]::GetFullPath($integrationPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $integrationPath)) }
$integration = Get-Content -LiteralPath $integrationFile -Raw | ConvertFrom-Json
Assert-ExactFields $integration @('transactionId','beforeHead','originalF1Head','f1Head','afterHead','baseBefore','handoffSha256','f1AddendumSha256','revisionMappingContractOwner','integratedAtUtc') 'F1 integration record'
Assert-Sha256 $integration.transactionId 'integration.transactionId'
foreach ($name in @('beforeHead','originalF1Head','f1Head','afterHead','baseBefore')) { Assert-Sha40 $integration.$name "integration.$name" }
Assert-Sha256 $integration.handoffSha256 'integration.handoffSha256'
Assert-Sha256 $integration.f1AddendumSha256 'integration.f1AddendumSha256'
Assert-String $integration.revisionMappingContractOwner 'integration.revisionMappingContractOwner'
[void](Assert-Utc $integration.integratedAtUtc 'integration.integratedAtUtc')
$f1Head = $integration.f1Head
Assert-Sha40 $head 'F2 HEAD'
git merge-base --is-ancestor $f1Head $head
if ($LASTEXITCODE -ne 0) { throw 'F1 is not an ancestor of F2 candidate' }
git diff --quiet "$f1Head..$head"
$diffExit = $LASTEXITCODE
if ($diffExit -eq 0) { throw 'F2 candidate has no P1 change from F1' }
if ($diffExit -ne 1) { throw "git diff --quiet failed with exit code $diffExit" }
$windowPath = (& git rev-parse --git-path "p1-acceptance-window-$head.json").Trim()
$windowFile = if ([IO.Path]::IsPathRooted($windowPath)) { [IO.Path]::GetFullPath($windowPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $windowPath)) }
$reviewPath = (& git rev-parse --git-path 'p1-independent-review.json').Trim()
$reviewFile = if ([IO.Path]::IsPathRooted($reviewPath)) { [IO.Path]::GetFullPath($reviewPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $reviewPath)) }
$window = Get-Content -LiteralPath $windowFile -Raw | ConvertFrom-Json
$review = Get-Content -LiteralPath $reviewFile -Raw | ConvertFrom-Json
Assert-ExactFields $window @('f1Head','candidateHead','startedAtUtc') 'acceptance window'
Assert-StringEquals $window.f1Head $f1Head 'window.f1Head'
Assert-StringEquals $window.candidateHead $head 'window.candidateHead'
$windowStarted = Assert-Utc $window.startedAtUtc 'window.startedAtUtc'
Assert-ExactFields $review @('owner','reviewer','reviewStatus','reviewedHead','f1Head','reviewCompletedAtUtc','revisionMappingContractOwner','evidence') 'independent review'
foreach ($name in @('owner','reviewer','reviewStatus','reviewedHead','f1Head','reviewCompletedAtUtc','revisionMappingContractOwner')) { Assert-String $review.$name "review.$name" }
Assert-StringEquals $review.reviewStatus 'PASS' 'review.reviewStatus'
Assert-StringEquals $review.reviewedHead $head 'review.reviewedHead'
Assert-StringEquals $review.f1Head $f1Head 'review.f1Head'
Assert-StringEquals $review.revisionMappingContractOwner $integration.revisionMappingContractOwner 'review.revisionMappingContractOwner'
if ($review.owner.Trim().Equals($review.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'Owner/reviewer identity is invalid after normalized comparison' }
$reviewCompleted = Assert-Utc $review.reviewCompletedAtUtc 'review.reviewCompletedAtUtc'
if ($reviewCompleted -le $windowStarted) { throw 'Review predates acceptance evidence' }
$evidenceKinds = @('unit','it','migration','vitest','standards','scan')
Assert-ExactFields $review.evidence $evidenceKinds 'review.evidence'
$gitDirRaw = (& git rev-parse --git-dir).Trim()
$gitMetadataRoot = if ([IO.Path]::IsPathRooted($gitDirRaw)) { [IO.Path]::GetFullPath($gitDirRaw) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $gitDirRaw)) }
$verifiedEvidence = [ordered]@{}
foreach ($kind in $evidenceKinds) {
    $binding = $review.evidence.$kind
    Assert-ExactFields $binding @('path','sha256') "review.evidence.$kind"
    $expectedManifestPath = "p1-evidence/$head/$kind.manifest.json"
    Assert-StringEquals $binding.path $expectedManifestPath "review.evidence.$kind.path"
    Assert-Sha256 $binding.sha256 "review.evidence.$kind.sha256"
    Assert-SafeRelativePath $binding.path "review.evidence.$kind.path"
    $manifestFile = Resolve-ScopedArtifact 'git-metadata' $binding.path $gitMetadataRoot
    if (-not (Test-Path -LiteralPath $manifestFile -PathType Leaf)) { throw "Evidence manifest missing: $kind" }
    $actualManifestHash = (Get-FileHash -LiteralPath $manifestFile -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actualManifestHash -cne $binding.sha256) { throw "Evidence manifest SHA drifted: $kind" }
    if ((Get-Item -LiteralPath $manifestFile).LastWriteTimeUtc -lt $windowStarted.UtcDateTime) { throw "Evidence manifest predates acceptance window: $kind" }
    $manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
    Assert-ExactFields $manifest @('schemaVersion','kind','candidateHead','f1Head','windowStartedAtUtc','generatedAtUtc','artifacts','summary') "$kind manifest"
    if ((Assert-JsonNumber $manifest.schemaVersion "$kind.schemaVersion" 1) -ne 1) { throw "$kind schemaVersion must equal 1" }
    Assert-StringEquals $manifest.kind $kind "$kind.kind"
    Assert-StringEquals $manifest.candidateHead $head "$kind.candidateHead"
    Assert-StringEquals $manifest.f1Head $f1Head "$kind.f1Head"
    Assert-StringEquals $manifest.windowStartedAtUtc $window.startedAtUtc "$kind.windowStartedAtUtc"
    $generatedAt = Assert-Utc $manifest.generatedAtUtc "$kind.generatedAtUtc"
    if ($generatedAt -lt $windowStarted -or $generatedAt -gt $reviewCompleted) { throw "$kind generatedAtUtc is outside the acceptance/review interval" }
    if ($manifest.artifacts -isnot [System.Array] -or $manifest.artifacts.Count -le 0) { throw "$kind artifacts must be a non-empty JSON array" }
    if ($manifest.summary -isnot [pscustomobject]) { throw "$kind summary must be a JSON object" }
    Assert-StringEquals $manifest.summary.status 'PASS' "$kind.summary.status"
    $artifactKeys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($artifact in $manifest.artifacts) {
        Assert-ArtifactPathContract $artifact "$kind artifact"
        Assert-Sha256 $artifact.sha256 "$kind artifact.sha256"
        $artifactBytes = Assert-JsonNumber $artifact.bytes "$kind artifact.bytes" 0
        $listedWrite = Assert-Utc $artifact.lastWriteUtc "$kind artifact.lastWriteUtc"
        if ($listedWrite -lt $windowStarted -or $listedWrite -gt $reviewCompleted) { throw "$kind artifact mtime is outside the acceptance/review interval" }
        $artifactKey = $artifact.pathScope + ':' + $artifact.relativePath
        if (-not $artifactKeys.Add($artifactKey)) { throw "$kind manifest has duplicate artifact path: $artifactKey" }
        $artifactFile = Resolve-ScopedArtifact $artifact.pathScope $artifact.relativePath $gitMetadataRoot
        if (-not (Test-Path -LiteralPath $artifactFile -PathType Leaf)) { throw "$kind artifact is missing: $artifactKey" }
        $item = Get-Item -LiteralPath $artifactFile
        if ([decimal]$item.Length -ne $artifactBytes) { throw "$kind artifact byte count drifted: $artifactKey" }
        if ($item.LastWriteTimeUtc.Ticks -ne $listedWrite.UtcDateTime.Ticks) { throw "$kind artifact mtime drifted: $artifactKey" }
        $artifactHash = (Get-FileHash -LiteralPath $artifactFile -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($artifactHash -cne $artifact.sha256) { throw "$kind artifact SHA drifted: $artifactKey" }
    }
    $verifiedEvidence[$kind] = [ordered]@{ path=$binding.path; sha256=$binding.sha256 }
}
$stableDtoComponents = [ordered]@{
    KnowledgeRouteRequestDTO = @('Long directionCatalogVersionId','String industryCode','String purposeCode','Integer targetDurationSeconds','List<String> tagCodes')
    KnowledgeRouteResultDTO = @('String routingVersion','String videoTypeCode','List<KnowledgePlanDTO> plans','String contentHash')
    KnowledgePlanDTO = @('String candidateCode','String planCode','Long primaryTemplateVersionId','String angleCode','String differentiatorTechniqueCode')
    KnowledgeSnapshotRequestDTO = @('Long rootTaskId','Long promptVersionId','Long generationContextRevision','String generationInputHash','KnowledgeRouteResultDTO route','List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts')
    KnowledgeSnapshotDTO = @('Long snapshotId','Long rootTaskId','Long promptVersionId','Long generationContextRevision','String generationInputHash','KnowledgeRouteResultDTO route','List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts','List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials','String contentHash','Instant createdAt')
}
$stableDtoSourceSha256 = [ordered]@{}
$dtoRoot = Join-Path $repoRoot 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto'
foreach ($dtoName in $stableDtoComponents.Keys) {
    $dtoFile = Join-Path $dtoRoot ($dtoName + '.java')
    if (-not (Test-Path -LiteralPath $dtoFile -PathType Leaf)) { throw "Stable DTO source is missing: $dtoName" }
    $source = Get-Content -LiteralPath $dtoFile -Raw
    $match = [regex]::Match($source, '(?s)(?:public\s+)?record\s+' + [regex]::Escape($dtoName) + '\s*\((?<components>.*?)\)\s*\{')
    if (-not $match.Success) { throw "Stable DTO record header is missing: $dtoName" }
    $actualComponents = @($match.Groups['components'].Value -split ',' | ForEach-Object { ($_ -replace '\s+', ' ').Trim() })
    Assert-StringArray $actualComponents $stableDtoComponents[$dtoName] "stableDtoComponentRegistry.$dtoName"
    $stableDtoSourceSha256[$dtoName] = (Get-FileHash -LiteralPath $dtoFile -Algorithm SHA256).Hash.ToLowerInvariant()
    Assert-Sha256 $stableDtoSourceSha256[$dtoName] "stableDtoSourceSha256.$dtoName"
}
$handoffPath = (& git rev-parse --git-path 'p1-f2-handoff.json').Trim()
$handoffFile = if ([IO.Path]::IsPathRooted($handoffPath)) { [IO.Path]::GetFullPath($handoffPath) } else { [IO.Path]::GetFullPath((Join-Path $repoRoot $handoffPath)) }
$corePayload = [ordered]@{
    fullF2Ready = $true
    f1Head = $f1Head
    originalF1Head = $integration.originalF1Head
    f1AmendmentHead = $integration.f1Head
    f2Head = $head
    owner = $review.owner
    reviewer = $review.reviewer
    reviewStatus = 'PASS'
    reviewCompletedAtUtc = $review.reviewCompletedAtUtc
    p1AcceptanceWindowStart = $window.startedAtUtc
    p1AcceptanceWindowEnd = $review.reviewCompletedAtUtc
    originalF1HandoffSha256 = $integration.handoffSha256
    f1AddendumSha256 = $integration.f1AddendumSha256
    migrationChain = @('01','02','03','04','04a','05')
    migrationRepeat05 = $true
    stableServices = @('IKnowledgeRoutingService','IKnowledgeSnapshotService')
    stableDtos = @('KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO')
    stableDtoComponentRegistry = $stableDtoComponents
    stableDtoSourceSha256 = $stableDtoSourceSha256
    downstreamConsumers = @('P2','P3')
    revisionMappingContractOwner = $review.revisionMappingContractOwner
    evidence = $verifiedEvidence
}
function Assert-F2Handoff($Value) {
    Assert-ExactFields $Value @(
        'fullF2Ready','f1Head','originalF1Head','f1AmendmentHead','f2Head','owner','reviewer','reviewStatus','reviewCompletedAtUtc',
        'p1AcceptanceWindowStart','p1AcceptanceWindowEnd','originalF1HandoffSha256','f1AddendumSha256','migrationChain','migrationRepeat05',
        'stableServices','stableDtos','stableDtoComponentRegistry','stableDtoSourceSha256',
        'downstreamConsumers','revisionMappingContractOwner','evidence','capturedAtUtc'
    ) 'F2 handoff'
    Assert-BoolEquals $Value.fullF2Ready $true 'F2.fullF2Ready'
    Assert-Sha40 $Value.f1Head 'F2.f1Head'
    Assert-Sha40 $Value.originalF1Head 'F2.originalF1Head'
    Assert-Sha40 $Value.f1AmendmentHead 'F2.f1AmendmentHead'
    Assert-Sha40 $Value.f2Head 'F2.f2Head'
    Assert-StringEquals $Value.f1Head $Value.f1AmendmentHead 'F2.f1Head'
    git merge-base --is-ancestor $Value.originalF1Head $Value.f1AmendmentHead
    if ($LASTEXITCODE -ne 0) { throw 'F2 original F1 is not an ancestor of amendment F1' }
    foreach ($name in @('owner','reviewer','reviewStatus','reviewCompletedAtUtc','p1AcceptanceWindowStart','p1AcceptanceWindowEnd','revisionMappingContractOwner')) { Assert-String $Value.$name "F2.$name" }
    Assert-StringEquals $Value.reviewStatus 'PASS' 'F2.reviewStatus'
    if ($Value.owner.Trim().Equals($Value.reviewer.Trim(), [StringComparison]::OrdinalIgnoreCase)) { throw 'F2 owner/reviewer are not independent' }
    [void](Assert-Utc $Value.reviewCompletedAtUtc 'F2.reviewCompletedAtUtc')
    [void](Assert-Utc $Value.p1AcceptanceWindowStart 'F2.p1AcceptanceWindowStart')
    [void](Assert-Utc $Value.p1AcceptanceWindowEnd 'F2.p1AcceptanceWindowEnd')
    Assert-Sha256 $Value.originalF1HandoffSha256 'F2.originalF1HandoffSha256'
    Assert-Sha256 $Value.f1AddendumSha256 'F2.f1AddendumSha256'
    Assert-StringArray $Value.migrationChain @('01','02','03','04','04a','05') 'F2.migrationChain'
    Assert-BoolEquals $Value.migrationRepeat05 $true 'F2.migrationRepeat05'
    Assert-StringArray $Value.stableServices @('IKnowledgeRoutingService','IKnowledgeSnapshotService') 'F2.stableServices'
    Assert-StringArray $Value.stableDtos @('KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO') 'F2.stableDtos'
    Assert-ExactFields $Value.stableDtoComponentRegistry @('KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO') 'F2.stableDtoComponentRegistry'
    Assert-ExactFields $Value.stableDtoSourceSha256 @('KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO') 'F2.stableDtoSourceSha256'
    foreach ($dtoName in @('KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO')) {
        Assert-StringArray $Value.stableDtoComponentRegistry.$dtoName $stableDtoComponents[$dtoName] "F2.stableDtoComponentRegistry.$dtoName"
        Assert-Sha256 $Value.stableDtoSourceSha256.$dtoName "F2.stableDtoSourceSha256.$dtoName"
        Assert-StringEquals $Value.stableDtoSourceSha256.$dtoName $stableDtoSourceSha256[$dtoName] "F2.stableDtoSourceSha256.$dtoName"
    }
    Assert-StringArray $Value.downstreamConsumers @('P2','P3') 'F2.downstreamConsumers'
    Assert-ExactFields $Value.evidence @('unit','it','migration','vitest','standards','scan') 'F2.evidence'
    foreach ($kind in @('unit','it','migration','vitest','standards','scan')) {
        Assert-ExactFields $Value.evidence.$kind @('path','sha256') "F2.evidence.$kind"
        Assert-StringEquals $Value.evidence.$kind.path "p1-evidence/$($Value.f2Head)/$kind.manifest.json" "F2.evidence.$kind.path"
        Assert-Sha256 $Value.evidence.$kind.sha256 "F2.evidence.$kind.sha256"
    }
    [void](Assert-Utc $Value.capturedAtUtc 'F2.capturedAtUtc')
}
function Assert-MustReject([string]$Label, [scriptblock]$Action) {
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw "Strict F2 schema self-test unexpectedly passed: $Label" }
}
Assert-MustReject 'string as boolean' { Assert-BoolEquals 'true' $true 'fixture.fullF2Ready' }
Assert-MustReject 'scalar as array' { Assert-StringArray 'P2' @('P2') 'fixture.downstreamConsumers' }
Assert-MustReject 'extra field' { Assert-ExactFields ([pscustomobject]@{ expected='x'; extra='y' }) @('expected') 'fixture' }
foreach ($badRelativePath in @(123, $true)) {
    Assert-MustReject "relativePath JSON type $($badRelativePath.GetType().Name)" {
        $fixture = [pscustomobject][ordered]@{
            pathScope='git-metadata'; relativePath=$badRelativePath; sha256=('a' * 64)
            bytes=1; lastWriteUtc='2026-01-01T00:00:00.0000000Z'
        }
        Assert-ArtifactPathContract $fixture 'fixture.artifact'
    }
}
Write-Output 'P1_F2_STRICT_SCHEMA_SELFTEST_OK'
function Invoke-F2Freeze([System.Collections.IDictionary]$Core) {
    $coreJson = $Core | ConvertTo-Json -Depth 8 -Compress
    if (Test-Path -LiteralPath $handoffFile) {
        $existing = Get-Content -LiteralPath $handoffFile -Raw | ConvertFrom-Json
        Assert-F2Handoff $existing
        $existingCore = [ordered]@{
            fullF2Ready=$existing.fullF2Ready; f1Head=$existing.f1Head
            originalF1Head=$existing.originalF1Head; f1AmendmentHead=$existing.f1AmendmentHead; f2Head=$existing.f2Head
            owner=$existing.owner; reviewer=$existing.reviewer; reviewStatus=$existing.reviewStatus
            reviewCompletedAtUtc=$existing.reviewCompletedAtUtc
            p1AcceptanceWindowStart=$existing.p1AcceptanceWindowStart
            p1AcceptanceWindowEnd=$existing.p1AcceptanceWindowEnd
            originalF1HandoffSha256=$existing.originalF1HandoffSha256
            f1AddendumSha256=$existing.f1AddendumSha256
            migrationChain=@($existing.migrationChain); migrationRepeat05=$existing.migrationRepeat05
            stableServices=@($existing.stableServices); stableDtos=@($existing.stableDtos)
            stableDtoComponentRegistry=$existing.stableDtoComponentRegistry
            stableDtoSourceSha256=$existing.stableDtoSourceSha256
            downstreamConsumers=@($existing.downstreamConsumers)
            revisionMappingContractOwner=$existing.revisionMappingContractOwner
            evidence=$existing.evidence
        }
        if (($existingCore | ConvertTo-Json -Depth 8 -Compress) -cne $coreJson) { throw 'Existing F2 core payload differs; never overwrite it' }
        return [pscustomobject]@{ Hash=(Get-FileHash -LiteralPath $handoffFile -Algorithm SHA256).Hash; CapturedAtUtc=$existing.capturedAtUtc }
    }
    $document = [ordered]@{}
    foreach ($key in $Core.Keys) { $document[$key] = $Core[$key] }
    $document.capturedAtUtc = [DateTime]::UtcNow.ToString('o')
    $json = $document | ConvertTo-Json -Depth 8 -Compress
    $directory = Split-Path -Parent $handoffFile
    $tempFile = Join-Path $directory ('.p1-f2-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    [IO.File]::WriteAllText($tempFile, $json, [Text.UTF8Encoding]::new($false))
    try {
        [IO.File]::Move($tempFile, $handoffFile)
    } catch {
        if (Test-Path -LiteralPath $tempFile) { Remove-Item -LiteralPath $tempFile }
        throw
    }
    $created = Get-Content -LiteralPath $handoffFile -Raw | ConvertFrom-Json
    Assert-F2Handoff $created
    return [pscustomobject]@{ Hash=(Get-FileHash -LiteralPath $handoffFile -Algorithm SHA256).Hash; CapturedAtUtc=$created.capturedAtUtc }
}
$first = Invoke-F2Freeze $corePayload
$second = Invoke-F2Freeze $corePayload
if ($first.Hash -cne $second.Hash -or $first.CapturedAtUtc -cne $second.CapturedAtUtc) { throw 'F2 idempotent read changed file hash or capturedAtUtc' }
$headAfterFreeze = (& git rev-parse HEAD).Trim()
if ($headAfterFreeze -cne $head) { throw 'HEAD changed during F2 freeze' }
if ((& $gate -RepoRoot $repoRoot -ExpectedPhase integrated -RequireClean) -ne 'P1_WORKTREE_GATE_OK') { throw 'Worktree changed during F2 freeze' }
foreach ($kind in $evidenceKinds) {
    $binding = $review.evidence.$kind
    $manifestFile = Resolve-ScopedArtifact 'git-metadata' $binding.path $gitMetadataRoot
    if ((Get-FileHash -LiteralPath $manifestFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $binding.sha256) { throw "Evidence manifest changed during F2 freeze: $kind" }
    $manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
    foreach ($artifact in $manifest.artifacts) {
        $artifactFile = Resolve-ScopedArtifact $artifact.pathScope $artifact.relativePath $gitMetadataRoot
        $item = Get-Item -LiteralPath $artifactFile
        if ($item.Length -ne $artifact.bytes -or $item.LastWriteTimeUtc.Ticks -ne ([DateTimeOffset]::Parse($artifact.lastWriteUtc)).UtcDateTime.Ticks -or
            (Get-FileHash -LiteralPath $artifactFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $artifact.sha256) { throw "Evidence artifact changed during F2 freeze: $kind/$($artifact.relativePath)" }
    }
}
Write-Output "P1_FULL_F2_HEAD=$head"
```

F2 形成后，P2/P3 只可消费 `IKnowledgeRoutingService`、`IKnowledgeSnapshotService` 和五个稳定 DTO；必须先核对 `originalF1HandoffSha256`、`f1AddendumSha256`、`originalF1Head`、`f1AmendmentHead`、`stableDtoComponentRegistry` 与 `stableDtoSourceSha256`，不得读取 P1 Mapper、表、Entity、platform BO/VO 或 infra provider。P1 只冻结 DTO component 和 A/B/C 顺序；`angleSummary` 由 P3 的版本化 formatter 确定性派生，provider 不得输出或覆盖。真实联调必须以 `fullF2Ready=true`、同一 `f2Head` 和完整证据哈希为前提。

---

## 计划完成自检

- [ ] 恰好 10 张最小任务卡，且每张 8 个字段完整。
- [ ] Task 1–5 是 F1 前独立切片；05 只有 discovery 外草案且未执行。
- [ ] 不可变原 F1 handoff 与 exact addendum／三项 evidence／独立 review 均已通过 live SHA、祖先、独立性和 UTC 门禁；knowledge import revision 映射已由契约 owner 确认。
- [ ] Task 6 只在隔离 clean worktree 核验后 rebase，并记录 before/after/base。
- [ ] 迁移专库完整执行 01→02→03→04→04a→05 并复跑 05。
- [ ] core 只有 RuoYi domain/dto/mapper/service/service.impl，HTTP BO/VO 只在 platform。
- [ ] 两个稳定接口、五个稳定 DTO、A/B/C 和快照签名无漂移。
- [ ] 发布校验是 ServiceImpl 私有方法，ZIP/provider/listener 边界正确。
- [ ] 免费导入 create→freeze→enqueue 同事务；复用立即返回；无 attempt/usage/provider。
- [ ] 23 个前端状态逐文件存在，且无额度/价格态。
- [ ] 所有 RED/GREEN 都有 fresh Surefire/Failsafe/Vitest 真实报告。
- [ ] 所有 Java 测试有 `@Tag("dev")`，所有 IT 使用 `-Pdev,local-integration-test`。
- [ ] PowerShell AST、动态 root、统一 gate、绝对路径和逗号 selector 扫描通过。
- [ ] acceptance window 内 HEAD 不变，独立 reviewer PASS。
- [ ] F2 handoff 同 HEAD 幂等、不同内容拒绝覆盖，包含原 handoff/addendum SHA、原/amendment F1 HEAD、五 DTO component registry/source SHA；P2/P3 只消费稳定 Service/DTO。
