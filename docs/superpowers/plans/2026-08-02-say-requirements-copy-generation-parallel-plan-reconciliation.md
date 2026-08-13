# “创作—说需求”并行计划整改实现计划

> **面向 AI 代理的工作者：** REQUIRED SUB-SKILL: Use `subagent-driven-development`（同一会话逐任务执行）或 `executing-plans`（独立会话分批执行）来实施本计划。

**执行约定：** 所有原子步骤都使用 `- [ ]` 复选框；完成一步并记录证据后才能勾选，失败时保持未勾选并填写阻塞项。

**目标：** 将主计划、P0-B、P0-C、P1、P2、P3 六份现有实现计划统一整改为符合当前 RuoYi 分层、P0-A 已落地事实和三人错峰并行设计的可执行计划。

**架构：** 以已批准的并行交付规格为唯一增量设计依据，由一名契约 owner 串行修改共享计划，一名独立 reviewer 按三视角复核；第三人只做下一份计划的只读预审。整改只改变计划文档，不实现业务代码；后续业务开发按 P0-B／P0-C 基座、P1／P2／P3 独立切片和 F0～F4 集成门禁推进。

**技术栈：** Markdown、PowerShell、`rg`、Git、RuoYi-Vue-Plus 6.x 项目分层约束。

---

## 一、执行基线与范围

### 已批准输入

- 设计规格：`docs/superpowers/specs/2026-08-02-say-requirements-copy-generation-parallel-delivery-design.md`。
- 规格批准提交：`bb3d2b22e docs: 设计 P0-B 至 P3 三人并行交付方案`。
- P0-A 已落地基线：`main` 上的身份、安全、会话和本机集成测试实现；计划不得再引用已不存在的 `identity.application` 或 `identity.model` 类型。
- 用户已明确批准本轮计划文档直接在当前 `main` 工作区修改，不创建 worktree。该授权不延伸到后续业务代码开发；业务实现仍使用独立分支或独立工作目录。

### 本计划只修改

- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md`

### 只读核对，不修改

- `AGENTS.md`、`RULES.md`、`docs/DOCUMENT_MAP.md`
- `docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`
- `docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/DOMAIN_MODEL.md`
- `ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md` 及其 `references/backend.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0a-identity-security.md`
- `docs/superpowers/plans/2026-08-01-p0a-ruoyi-layering-remediation.md`
- `docs/superpowers/plans/2026-08-01-p0a-local-integration-test-remediation.md`
- `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p4-integration.md`
- P0-A 实际 Java 类型、服务实现与测试。

P4 不在本轮整改范围内。主计划只登记“P4 在进入实施前必须另做一次与最终 P0-B～P3 契约的对账”，不得顺手改 P4 文件。

## 二、不可变整改规则

### 2.1 后端包与类型规则

每份计划中的业务聚合只能规划以下包：

- `domain`：Entity、业务枚举及简单内聚判断；
- 与 `domain` 平级的 `dto`、`mapper`、`service`、`service.impl`；
- HTTP 启动模块中的 `domain.bo`、`domain.vo`、`controller`；
- `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 只承担直接技术职责，不得承载业务编排。

必须执行以下映射：

| 旧规划 | 新规划 |
|---|---|
| `application/*Service` | `service/I*Service` |
| `application/impl/*ServiceImpl` | `service/impl/*ServiceImpl` |
| `model`、`command`、`routing` 中的稳定跨模块数据 | 聚合平级 `dto/*DTO` |
| 业务状态、类型和值域枚举 | 聚合 `domain` 根 |
| 核心模块中的 `domain.bo`、`domain.vo` | 对应用户端或平台端 HTTP 模块的 `domain.bo`、`domain.vo` |
| `repository` 业务抽象 | `mapper` 数据访问加 `service` 业务编排 |
| 端侧 `adapter` 主体解析器 | 对应端侧 `security` |
| 供应商 client、provider、原始请求、响应和输出校验 | `ai-video-infra` 的直接 `client` 或 `provider` |
| 任务、事件、Job 入口 | 直接技术职责的 `listener` 或 `provider`，业务编排仍回到核心 `service` |

禁止把 `dto`、`provider` 或 `listener` 变成新的业务层。所有公开 Service 接口都以 `I` 开头，实现以 `ServiceImpl` 结尾。

本轮所有文档编辑必须使用 `apply_patch`；不得用 shell 重写整文件。每次只格式化和暂存当前目标计划，保留工作区中任何非本任务改动。

### 2.2 跨阶段稳定服务

六份计划必须使用同一份最小稳定服务登记表：

| 阶段 | 稳定 Service |
|---|---|
| P0-A 已存在 | `IAppIdentityService`、`IAppSessionService`、`IAppPermissionService`、`IAppSecurityAuditService` |
| P0-B | `IWorkspaceAuthorizationService` |
| P0-C | `IAiTaskService`、`IAiTaskExecutionDispatcher`、`IAiTaskAttemptService`、`IQuotaBillingService`、`IDirectionCatalogService` |
| P1 | `IKnowledgeRoutingService`、`IKnowledgeSnapshotService` |
| P2 | `IQuestionnaireContextService`、`IEvidenceReviewService` |
| P3 | `IScriptGenerationService`、`IScriptVersionService`、`IUserScriptQueryService` |

各阶段内部服务也必须使用 `I*Service`／`*ServiceImpl`，但不得擅自登记成新的跨阶段依赖。

P0-C 对 P2/P3 冻结的两个写事务入口不得缩写或改名：

```java
void requireGenerationContextWritable(Long draftId, Long branchRevision);
void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);
```

两者均为非 `readOnly` 的 `Propagation.MANDATORY` 入口。P2 对 P3 冻结的并发读入口精确为
`QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId,Long branchId)`，同样为非 `readOnly` 的
`Propagation.MANDATORY` 入口，并按 `draft → current_branch` 执行 `SELECT ... FOR UPDATE`。

### 2.3 稳定 DTO 与跨聚合边界

- P0-B：`WorkspaceContextDTO`、`WorkspaceSummaryDTO`、`ResourceOwnershipDTO`、`ResourceDataScopeDTO`、`SwitchWorkspaceDTO`。
- P0-C：`CreateScriptDraftDTO`、`ScriptDraftOverviewDTO`、`StepGuardDTO`、`QuotaLockRequestDTO`、`QuotaLockResultDTO`、`QuotaAccountSnapshotDTO`、`TaskInitiatorDTO`、`ChargeableTaskDTO`、`FreeTaskDTO`、`TaskRevisionSnapshotDTO`、`TaskCreationResultDTO`、`TaskResultReferenceDTO`、`AiTaskExecutionLeaseDTO`、`AiTaskAttemptHandleDTO`、`ProviderUsageDTO`、`DirectionCatalogSnapshotDTO`。
- P1：`KnowledgeRouteRequestDTO`、`KnowledgeRouteResultDTO`、`KnowledgePlanDTO`、`KnowledgeSnapshotRequestDTO`、`KnowledgeSnapshotDTO`。
- P2：`QuestionnaireContextDTO`、`QuestionnaireAnswerRevisionDTO`、`QuestionnaireSupplementRevisionDTO`、`EvidenceReviewContextDTO`、`AcceptedEvidenceFactDTO`、`EvidenceDecisionRevisionDTO`。其中 `EvidenceReviewContextDTO` 必须同时携带已接受事实和按 `factId` 排序的决定修订；每个已接受事实必须携带准确的 `factId` 与 `decisionRevision`，供 P3 原子冻结输入。
- P3：生成、优化、冻结输入和版本结果使用 `script/dto/*DTO`；HTTP BO/VO 只位于 `ai-video-user`。

P3 不得 import、注入、mock 或修改 P2 的 `ScriptBranchEvidenceDecisionMapper`／XML。P2 独占该 Mapper，P3 只调用 `IQuestionnaireContextService`、`IEvidenceReviewService` 并消费上述 DTO。P2 同理只调用 P1 稳定 Service/DTO，不访问知识 Mapper。

`DirectionCatalogSnapshotDTO` 的八个 record component 与顺序精确为 `Long catalogVersion`、`String contentHash`、`Long industryCatalogVersion`、`Long purposeCatalogVersion`、`String durationRuleVersion`、`List<IndustryOption> industries`、`Map<String,List<PurposeOption>> purposesByIndustry`、`List<TargetDurationOption> targetDurations`；聚合版本和两个目录子版本必须为正数，时长规则版本必须非空。该 DTO 是 core 内部完整快照，不是 HTTP VO：方向选项响应只公开聚合 `catalogVersion` 与选项，保存请求只接收 `expectedCatalogVersion` 与用户选择，`contentHash` 和三个子版本都不得由客户端提交。P2 必须从同一次 published snapshot 校验聚合版本、code 和时长，再由服务端持久化三个追溯子版本。

F2 handoff 必须同时冻结 P1 五个 DTO 的 `stableDtoComponentRegistry` 与 `stableDtoSourceSha256`；至少
`KnowledgePlanDTO` 的组件及顺序精确为 `String candidateCode`、`String planCode`、
`Long primaryTemplateVersionId`、`String angleCode`、`String differentiatorTechniqueCode`。F3 handoff 必须冻结
P2 上述六个 DTO 的 component registry/source SHA、两个 Service 最终签名、锁入口、P2 写守卫以及答案
`answerIdentityJson`／`answerContextJson` 双 JSON 与排序协议。P1 只冻结 DTO 与 A/B/C 顺序；`angleSummary`
由 P3 的版本化 formatter 确定性派生，不进入 provider schema，也不得由 provider 输出或覆盖。

### 2.4 并行与合并门禁

| 门禁 | 必须完成 |
|---|---|
| F0 | P0-A 实际类型映射与最终安全门禁证据可追溯、P0-B 组织工作区会话扩展方案、P0-B/P0-C 稳定 Service 与 DTO 冻结 |
| F1 | P0-B、P0-C 最终实现、`02→03→04→04a` 迁移及公共鉴权、任务、额度、任务组写守卫契约通过 |
| F2 | P1 的 `05` 迁移、知识路由、知识快照和平台端验收通过 |
| F3 | P2 的 `06` 迁移、问卷上下文、已接受事实及准确决策修订映射通过 |
| F4 | P3 的 `07` 迁移、三候选、优化、确认、版本树和用户文案库通过 |

开发切片可错峰重叠，真实集成、迁移和合并仍按 F0→F4。数据库顺序固定为 `01→02→03→04→04a→05→06→07`，其中 `04a` 精确为 `20260728_04a_p0c_task_group_guard.sql`。

### 2.5 红色任务协作约束

本轮 R0 属于共享契约红色任务。同一时刻同一计划最多两名参与者：

- A：契约 owner，唯一编辑者；
- B：独立 reviewer，不与 A 共同编写。

本计划和六份目标计划中的每张 R0 任务卡必须显式填写：

- 单一目标与不做范围；
- 权威来源；
- 红色触发项；
- 允许影响的精确文件；
- 前置依赖与退出门禁；
- 第一检查点：结构/签名改完后由 B 审查；
- 第二检查点：GREEN 扫描后由 B 独立复跑；
- 正向验收、反向验收和准确验证命令；
- 实施者 A、reviewer B、并发上限 2。

第三人 C 的工作必须另建 R1 只读任务卡，不计入 R0：

- 单一目标：预审下一份计划的旧路径、共享文件和依赖缺口；
- 不做：不编辑、不暂存、不提交、不与 A/B 共写当前计划；
- 权威来源：批准规格、当前目标计划、P0-A 实际代码和项目规则；
- 允许影响：只读，无文件写入；
- 依赖：不依赖 R0 未提交内容；
- 验收：`git status --short` 在预审前后完全一致；
- 输出：完成项、风险、验证证据、阻塞项。

每个任务的交接固定输出：

1. 完成项；
2. 风险；
3. 验证证据；
4. 阻塞项。

## 三、统一命令与预检

### Task 1：锁定工作区、目标文件和基线

**任务卡**

- 单一目标／不做：只锁定基线与 RED 证据；不编辑、暂存或提交文件。
- 权威来源：批准规格、六份目标计划、P0-A 实际代码、项目治理与 RuoYi 规则。
- 风险等级：红色，共享计划与公共契约。
- 并发：A 执行；B 只读核对目标集；上限 2。
- 允许影响／依赖：零文件影响；依赖用户批准和干净工作区。
- 两次检查点：B 先核对目标集合，再独立复核 RED 数量和 P0-A 事实。
- 反向验收／命令：`git status` 前后相同；执行本任务步骤 1～4 的精确命令。
- 前置：已批准规格和本计划均存在，工作区无未说明改动。
- 产物：精确目标数组和基线扫描记录。

- [ ] **步骤 1（2–5 分钟）：确认当前工作区**

运行：

~~~powershell
git status --short --branch
git log -5 --oneline
$baselineCommit = git rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or -not $baselineCommit) { throw '无法记录整改基线提交' }
"BASELINE_COMMIT=$baselineCommit"
~~~

预期：当前分支为 `main`；没有未暂存或未提交文件；输出唯一 `BASELINE_COMMIT=<sha>`。把该 SHA 写入 Task 1 的“验证证据”。允许 `main` 领先远端，但不得在本任务中自动 push。

- [ ] **步骤 2（2–5 分钟）：建立六文件目标数组**

运行：

~~~powershell
$targets = @(
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-master.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p3-script.md'
)
$missing = $targets | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; throw '计划文件缺失' }
$targets
~~~

预期：输出且只输出六个目标文件，无异常。

- [ ] **步骤 3（2–5 分钟）：记录 RED 基线**

运行：

~~~powershell
$targets = @(
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-master.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  '.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Measure-RequiredPlanMatch([string] $Name, [string] $Pattern, [string[]] $Paths) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Name" }
  if ($matches.Count -eq 0) { throw "RED 基线意外无命中：$Name" }
  [pscustomobject]@{ name = $Name; count = $matches.Count }
}
Measure-RequiredPlanMatch 'old-package' 'org\.dromara\.aivideo\.[A-Za-z0-9_.]+\.(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)\.' $targets
Measure-RequiredPlanMatch 'core-forbidden' 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $targets
Measure-RequiredPlanMatch 'core-bo-vo' 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]domain[\\/](?:bo|vo)[\\/]' $targets
Measure-RequiredPlanMatch 'core-domain-enums' 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]domain[\\/]enums[\\/]' $targets
Measure-RequiredPlanMatch 'core-provider' 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]provider[\\/]' $targets
$p3 = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p3-script.md')
Measure-RequiredPlanMatch 'p3-questionnaire-mapper' 'ScriptBranchEvidenceDecisionMapper|scriptBranchEvidenceDecisionMapper|questionnaire[\\/]mapper' $p3
~~~

预期：六条命令均显示旧规划命中。按跨平台分隔符 PCRE 复核，当前基线为 367 行核心禁止业务路径（含 adapter/aggregate）、162 行核心 BO/VO、18 行 core `domain/enums`、39 行 core provider，P3 有 9 个直接 Mapper 语义整改点；若上游提交改变计划，以执行时新鲜输出为准。

- [ ] **步骤 4（2–5 分钟）：核对 P0-A 真实接口**

运行：

~~~powershell
$identityPath = @('.\ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity')
$sessionImpl = @('.\ai-video-api\ruoyi-modules\ai-video\ai-video-core\src\main\java\org\dromara\aivideo\identity\service\impl\AppSessionServiceImpl.java')
foreach ($scan in @(
  @{ pattern = 'interface IApp|replaceWorkspace|invalidateUserSessions|invalidateOrganizationSessions'; paths = $identityPath; message = 'P0-A 接口事实缺失' },
  @{ pattern = 'canonicalPersonalWorkspace|replaceWorkspace'; paths = $sessionImpl; message = 'P0-A 个人工作区限定事实缺失' }
)) {
  $matches = @(rg -n -P $scan.pattern -- $scan.paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$($scan.message)" }
  if ($matches.Count -eq 0) { throw $scan.message }
  $matches
}
~~~

预期：接口均位于 `identity/service`；`replaceWorkspace` 当前调用 `canonicalPersonalWorkspace`，且个人工作区限定被明确显示。

- [ ] **步骤 5（2–5 分钟）：输出任务卡结果**

不修改文件，按“完成项、风险、验证证据、阻塞项”交接。若工作区不干净、目标缺失或 P0-A 接口事实变化，停止后续编辑。

## 四、逐文件整改任务

### Task 2：整改主计划的契约注册表与三人时间线

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md`
- 只读：批准规格和 P0-A 实际接口。

**任务卡**

- 单一目标／不做：只改主计划的总契约与时间线；不改子计划、P4 或业务代码。
- 权威来源：批准规格第 5～10、14～16 节和本计划 2.1～2.5。
- 风险等级：红色，主计划是六份子计划的总入口。
- 并发：A 编辑；B 只读审查主计划；上限 2。
- 允许影响／依赖：只允许主计划；依赖 Task 1 基线。
- 两次检查点：共享注册表完成后 review；步骤 6 GREEN 后独立复跑。
- 反向验收／命令：旧 Java 包零命中，子计划/P4/业务代码零改动；执行步骤 6。
- 验收：主计划成为 F0～F4、稳定 Service/DTO、共享文件 owner 和数据库顺序的唯一汇总。

- [ ] **步骤 1（2–5 分钟）：更新规格与执行边界**

在“规格与执行边界”加入 2026-08-02 批准规格，声明它只覆盖 P0-B/P0-C 与 P1～P3 的并行组织和分层纠偏；原业务规格仍定义业务范围。登记本轮直接修改当前工作区的用户决定，并保留业务实现隔离要求。

- [ ] **步骤 2（2–5 分钟）：替换后端共享接口表**

把“后端模块与共享接口”中的旧 `application`、`model`、`command`、`port` 路径替换为本计划 2.1～2.3 的服务和 DTO 注册表。P0-A 必须写真实的 `IApp*Service`、`identity.dto` 与 `identity.domain.AppSessionInvalidationReason`。

- [ ] **步骤 3（2–5 分钟）：重写依赖顺序**

将单一严格串行描述改为两个维度：

- 开发：P0-B 期间 B 做 P1 独立切片、C 做 P2 独立切片；P0-C 期间继续；F1 后 A 进入 P3 独立切片。
- 集成：F0→F1→F2→F3→F4，迁移 `01→02→03→04→04a→05→06→07`。

明确“并行开发不等于提前执行下游迁移或真实联调”。

- [ ] **步骤 4（2–5 分钟）：登记三人占用和共享 owner**

在依赖顺序后加入 A/B/C 时间线和共享文件表。`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、公共错误码、用户端根 `app.tsx`、工作台根 `index.tsx`、`StudioTopbar.tsx`、公共 adapter 和共享 model 在任一时间只由契约 owner 修改。

- [ ] **步骤 5（2–5 分钟）：更新任务 1 与任务 3～7**

先更新任务 1～4：

- 任务 1：先完成六份计划整改和独立 review。
- 任务 2：P0-A 只作为已完成基线和 F0 证据来源。
- 任务 3、4：共同构成 F0/F1，补 P0-A 组织会话扩展阻塞。

任务 1～8 均补风险等级、实施者、独立审查者、并发上限、文件所有权、前置/退出门禁、验证命令和固定四项输出；已完成的 P0-A 任务改为只读基线及 F0 证据来源，不重复实施。

- [ ] **步骤 5A（2–5 分钟）：更新任务 5～8**

- 任务 5、6、7：每项拆为“独立切片”“集成切片”“rebase 点”“完成门禁”。
- 任务 8：P4 保持未改，只登记进入 P4 前另行对账。
- 逐项核对风险、owner、reviewer、共享文件和退出证据没有空项。

- [ ] **步骤 6（2–5 分钟）：运行主计划 GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-master.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('F0','F1','F2','F3','F4','01→02→03→04→04a→05→06→07','IWorkspaceAuthorizationService','IQuotaBillingService','IKnowledgeRoutingService','IQuestionnaireContextService','IScriptGenerationService')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "主计划缺少：$required"
}
Assert-NoPlanMatch 'org\.dromara\.aivideo\.(?!infra(?:\.|$))[A-Za-z0-9_.]+\.(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)\.' $target '主计划仍含旧 Java 包引用'
~~~

预期：前两条显示全部门禁和稳定服务；旧 Java 包扫描无命中。

- [ ] **步骤 7（2–5 分钟）：review、暂存并提交**

B 按前端状态、后端分层、联调顺序三视角输出审查。A 修正后运行：

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw '主计划差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw '主计划暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw '主计划暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '主计划暂存差异检查失败' }
git commit -m "docs: 对齐说需求主计划并行门禁"
if ($LASTEXITCODE -ne 0) { throw '主计划提交失败' }
~~~

预期：暂存清单只有主计划，提交成功。

### Task 3：整改 P0-B 并补上组织工作区会话扩展

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md`
- 计划内新增修改目标：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImpl.java`
- 计划内新增测试目标：`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/identity/service/impl/AppSessionServiceImplTest.java`
- 只读契约：`IAppSessionService.java`、`AppWorkspaceSessionSnapshotDTO.java`。

**任务卡**

- 单一目标／不做：只整改 P0-B 计划及其 P0-A 兼容扩展描述；不实施 Java 或迁移。
- 权威来源：P0-A 实际 Service/DTO/实现、批准规格 F0/F1、授权安全规则。
- 风险等级：红色，涉及会话与组织越权边界。
- 并发：A 编辑 P0-B；B 安全/契约 review；上限 2。
- 允许影响／依赖：只允许 P0-B 计划；依赖 Task 2 主注册表。
- 两次检查点：签名/信任边界完成后 review；步骤 6 GREEN 后独立复跑。
- 反向验收／命令：伪造/跨账号/失效成员均有测试且旧 P0-A 包、旧审计字段零命中；执行步骤 6。
- 验收：P0-B 不再依赖不存在类型，组织切换有可实施且有反向测试的会话路径。

- [ ] **步骤 1（2–5 分钟）：修正 P0-A 消费清单**

用以下实际映射替换旧清单：

| 旧计划 | `main` 实际类型或方法 |
|---|---|
| `identity.application.AppIdentityService` | `identity.service.IAppIdentityService` |
| `AppSessionService` | `IAppSessionService` |
| `AppPermissionService` | `IAppPermissionService` |
| `AppSecurityAuditService` | `IAppSecurityAuditService` |
| `identity.model.AppPrincipalSnapshot` | `identity.dto.AppPrincipalSnapshotDTO` |
| `AppWorkspaceSessionSnapshot` | `AppWorkspaceSessionSnapshotDTO` |
| `AppSecurityAuditCommand` | `AppSecurityAuditDTO` |
| `identity.model.AppSessionInvalidationReason` | `identity.domain.AppSessionInvalidationReason` |
| `AppSessionRevisionGuard.requireCurrent(...)` | `AppSessionRevisionGuard.checkCurrentSession()` |
| `AppLoginHelper.requirePrincipal()` | `AppLoginHelper.getPrincipal()` |

固定以下实际签名：

~~~java
AppPrincipalSnapshotDTO replaceWorkspace(AppWorkspaceSessionSnapshotDTO workspace);
void invalidateUserSessions(Long appUserId, AppSessionInvalidationReason reason);
void invalidateOrganizationSessions(Long organizationId, AppSessionInvalidationReason reason);
void append(AppSecurityAuditDTO command);
~~~

`AppSecurityAuditDTO` 固定八个字段：`resourceType`、`resourceId`、`action`、`actorType`、`actorId`、`beforeDigest`、`afterDigest`、`reason`。删除旧计划的 `beforeHash`、`afterHash`、请求体 `traceId`。

- [ ] **步骤 2（2–5 分钟）：修正 P0-B 文件结构**

- 枚举与 Entity 放 `authorization/domain` 根。
- 五个跨模块值对象改为本计划 2.3 的 `authorization/dto/*DTO`。
- 服务改为 `IWorkspaceAuthorizationService`、内部 `IOrganizationAdminService`、内部 `IResourceOwnershipService` 和对应 `service.impl`。
- 删除 `repository.ResourceOwnershipResolver` 规划，改为 Mapper 查询加 `IResourceOwnershipService` 编排。
- 核心模块的组织 BO/VO 移到 `ai-video-platform/.../authorization/domain/bo|vo`。
- `AppAuthorizationActorAdapter`、`SysAuthorizationActorAdapter` 分别改为端侧 `security/*ActorResolver`。
- 测试包从 `application` 改为 `service` 或 `service.impl`。

- [ ] **步骤 3（2–5 分钟）：把真实阻塞写入前置门禁**

删除“P0-B 不修改 P0-A 语义”的绝对声明，改成：

- 不改 `IAppSessionService` 和 `AppWorkspaceSessionSnapshotDTO` 的公开签名；
- P0-B 允许最小扩展 `AppSessionServiceImpl.replaceWorkspace`，使它在保留个人工作区规范化的同时接受由 `IWorkspaceAuthorizationService` 完成账号、候选工作区、组织成员、角色权限和修订交叉验证后的组织快照；
- 控制器只接受不透明 `workspaceKey`，不得接收客户端拼装的会话快照；
- P0-B 不改 P0-A 表结构和身份表归属。

- [ ] **步骤 4（2–5 分钟）：改写任务 4 的测试先行顺序**

在任务 4 前半段加入 RED 测试：

1. `AppSessionServiceImplTest` 证明当前组织快照被拒绝；
2. `WorkspaceAuthorizationServiceTest` 拒绝伪造 workspaceKey；
3. 拒绝跨账号 actor；
4. 拒绝已退出、已禁用或修订过期成员；
5. 上述失败均验证 `replaceWorkspace` 从未被调用。

随后规划最小实现和 GREEN 测试：

1. 个人工作区原行为保持；
2. 已由 P0-B 服务规范化的组织快照可替换当前会话；
3. 缺少 `membershipRevision`、owner/billing 不一致或权限集合异常的组织快照被拒绝；
4. 组织/成员修订变化仍使旧会话失效。

- [ ] **步骤 5（2–5 分钟）：更新任务 1～3**

逐段替换任务 1～3 的旧路径、import、测试包、示例签名和暂存清单，并为每项增加风险等级、实施者、独立审查者、并发上限、精确文件所有权、前置门禁、验证命令和固定四项输出。

任务 1 必须引用 P0-A 双向令牌隔离、会话修订、运营审计及本机集成测试的最终证据；其契约冻结属于 F0。

- [ ] **步骤 5A（2–5 分钟）：更新任务 4～6**

将组织会话扩展、对象授权和组织管理的全部文件路径、代码片段、测试及 `git add` 清单切换到 `service`／`dto`／端侧 BO/VO；加入本任务步骤 4 的反向测试。

- [ ] **步骤 5B（2–5 分钟）：更新任务 7～9**

修正用户端/平台端 Controller、主体解析器、双启动模块装配和测试路径；确认端侧解析器位于 `security`。

- [ ] **步骤 5C（2–5 分钟）：更新任务 10～13**

修正前端类型、切换器、运营页面和最终门禁。任务 13 的全量安全、迁移、双端和前端状态通过属于 F1。

P0-B 实施期间，另外两人可做 P1/P2 独立切片，但不得编辑 P0-B 共享文件。

- [ ] **步骤 6（2–5 分钟）：运行 P0-B GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('IAppSessionService','AppWorkspaceSessionSnapshotDTO','IWorkspaceAuthorizationService','IOrganizationAdminService','IResourceOwnershipService','AppSessionServiceImpl.java','AppSessionServiceImplTest.java','伪造','跨账号','membershipRevision','beforeDigest','afterDigest')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "P0-B 缺少：$required"
}
Assert-NoPlanMatch 'identity\.(?:application|model)\.' $target 'P0-B 仍引用旧 P0-A 包'
Assert-NoPlanMatch '\b(?:AppIdentityService|AppSessionService|AppPermissionService|AppSecurityAuditService|AppPrincipalSnapshot|AppWorkspaceSessionSnapshot|AppSecurityAuditCommand)\b|AppSessionRevisionGuard\.requireCurrent|AppLoginHelper\.requirePrincipal' $target 'P0-B 仍引用旧 P0-A 简单类名或方法'
Assert-NoPlanMatch '\b(?:beforeHash|afterHash)\b|(?:AppSecurityAuditDTO|AppSecurityAuditCommand)[^\r\n]*\btraceId\b|\btraceId\b[^\r\n]*(?:AppSecurityAuditDTO|AppSecurityAuditCommand)' $target 'P0-B 仍保留错误审计字段'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $target 'P0-B 仍含旧核心业务分层'
~~~

预期：服务、扩展文件、实际审计字段和反向测试均命中；两条旧包扫描无命中。

- [ ] **步骤 7（2–5 分钟）：review、暂存并提交**

B 必须重点核对信任边界和“不接收客户端快照”。A 修正后：

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P0-B 差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P0-B 暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw 'P0-B 暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'P0-B 暂存差异检查失败' }
git commit -m "docs: 修订 P0-B 授权实施计划"
if ($LASTEXITCODE -ne 0) { throw 'P0-B 提交失败' }
~~~

预期：暂存清单只有 P0-B 计划，提交成功。

### Task 4：整改 P0-C 任务、额度、方向和草稿底座

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md`

**任务卡**

- 单一目标／不做：只整改 P0-C 计划的底座契约与分层，并冻结方向完整快照的八个 component 及 HTTP 聚合版本边界；不实现任务、额度或 provider。
- 权威来源：批准规格 F0/F1、异步任务/后端规则、P0-B 稳定授权契约。
- 风险等级：红色，P1～P3 都依赖任务、额度和草稿契约。
- 并发：A 编辑；B 后端/异步 review；上限 2。
- 允许影响／依赖：只允许 P0-C 计划；依赖 Task 2/3 的稳定注册表。
- 两次检查点：Service/DTO/provider 边界完成后 review；步骤 6 GREEN 后独立复跑。
- 反向验收／命令：core resolver、旧 StepGuard、common.actor、core provider 和旧分层零命中；执行步骤 6。
- 验收：P0-C 文件树、服务签名、任务扩展点和双端 BO/VO 全部符合当前规则。

- [ ] **步骤 1（2–5 分钟）：整改方向与草稿聚合**

- `DirectionCatalogStatus` 放 `direction/domain` 根。
- 核心方向快照改为 `direction/dto/DirectionCatalogSnapshotDTO`，component 顺序精确冻结为 `catalogVersion,contentHash,industryCatalogVersion,purposeCatalogVersion,durationRuleVersion,industries,purposesByIndustry,targetDurations`，并对正数子版本与非空时长规则版本做构造校验和负向测试；用户端和平台端各自声明 HTTP BO/VO。
- HTTP 方向选项 VO 只映射聚合 `catalogVersion` 与三组选项，方向保存 BO 只接收 `expectedCatalogVersion` 与用户选择；`contentHash` 和三个追溯子版本不得出现在客户端请求。更新所有 snapshot fixture、record component 顺序测试和契约扫描。
- 服务改为 `IDirectionCatalogService`／`DirectionCatalogServiceImpl`。
- `CreateScriptDraftCommand`、`ScriptDraftOverview` 改为 `CreateScriptDraftDTO`、`ScriptDraftOverviewDTO`。
- `StepGuard` 改为 `StepGuardDTO`，放 `studio/dto`。
- 服务改为内部 `IScriptDraftService`／`ScriptDraftServiceImpl`。

- [ ] **步骤 2（2–5 分钟）：整改额度与任务聚合**

- `quota/model` 的四个业务枚举移到 `quota/domain` 根。
- 三个值对象改为 `QuotaLockRequestDTO`、`QuotaLockResultDTO`、`QuotaAccountSnapshotDTO`。
- 服务改为 `IQuotaBillingService`／`QuotaBillingServiceImpl`。
- 删除 `common.actor` 和 core resolver 接口；core 只保留 `task/dto/TaskInitiatorDTO`。用户端、平台端分别在自身 `security` 使用具体 `AppTaskInitiatorResolver`、`SysTaskInitiatorResolver` 构造 DTO，再传给核心 Service。
- `task/model` 枚举移到 `task/domain` 根，其余命令、结果、快照、lease 和 handle 改为 `task/dto/*DTO`。
- 任务服务与处理器使用 `IAiTaskService`、`IAiTaskExecutionDispatcher`、`IAiTaskAttemptService`、内部 `IAiTaskExecutionHandler`。
- `task/infra` 扫描器和注册器改到直接 SnailJob 集成职责的 `ai-video-infra/.../task/provider`。

- [ ] **步骤 2A（2–5 分钟）：增加 F1 前向修订与任务组守卫**

保留原 `p0c-f1-handoff.json` 字节不变，新增前向迁移
`20260728_04a_p0c_task_group_guard.sql`、exact 12-field `p0c-f1-contract-addendum.json`、三项 evidence 与独立
review。`IAiTaskService` 必须增加并机械核对以下两个签名：

```java
void requireGenerationContextWritable(Long draftId, Long branchRevision);
void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);
```

`04a` 创建 `av_ai_task_group_member`，唯一键为 `(tenant_id,task_group_key,root_task_id)`，`origin` 仅允许
`origin|inherited`，`creator_type` 仅允许 `app_user|sys_user`，根任务使用同租户外键，并新增
`idx_av_ai_task_active_group`。全局锁序固定为
`draft → current_branch → operation_slot → quota_account → task_or_group_member`。

任务组键只允许 `script:{draftId}:{branchRevision}`。分支复用只继承 membership，不复制
`task`、`usage`、`ledger` 或 `operation_slot`；继承前必须校验 tenant、app user、resource、family 与 source
membership。完全相同重放幂等，partial/superset/conflict/origin 异常全部 fail-closed。活跃
`script_generate|script_optimize` 根任务处于 `pending|queued|running` 时，写守卫返回 `46123`，响应 `data`
只包含 `rootTaskId`、`taskType`、`status`。计费聚合按 `usageOperationId` 去重，禁止
`SUM(DISTINCT amount)`。

addendum 顶层字段顺序固定为
`originalF1Head,amendmentHead,originalF1HandoffSha256,requiredMethods,schemaAddendum,owner,reviewer,reviewStatus,reviewedHead,reviewCompletedAtUtc,evidence,capturedAtUtc`；
`schemaAddendum` 必须逐字段绑定迁移名、成员表、索引、枚举、全局锁序、任务组键、`membership_only` 与四项
forbidden copies。evidence 顺序固定为 source-signatures、migration-04a、independent-review；review 必须
owner/reviewer 异人、`PASS`、`reviewedHead=amendmentHead`、UTC 时间有效且 `capturedAtUtc >= reviewCompletedAtUtc`。

- [ ] **步骤 3（2–5 分钟）：固定 provider 边界**

删除 `ai-video-core/org/dromara/aivideo/provider` 规划。`ModelGateway`、`WebSearchGateway` 及其请求、响应、用量对象改为 `ai-video-infra` 的直接 `client`／`provider`；不得成为 P1～P3 的跨阶段核心契约。

P1～P3 的 infra 任务处理器调用直接 provider/client，将供应商结果映射为本阶段中性 DTO 后再调用核心 Service。可计费的规范化用量使用 `task/dto/ProviderUsageDTO`，不得包含 SDK 类型。

- [ ] **步骤 4（2–5 分钟）：移动核心 BO/VO**

`direction`、`quota`、`task` 的所有 HTTP BO/VO 从 `ai-video-core` 移到 `ai-video-user` 或 `ai-video-platform` 对应聚合。Controller 只依赖端侧 BO/VO，映射后调用核心 Service/DTO。

- [ ] **步骤 5（2–5 分钟）：更新任务 1～3**

逐段修正公共契约、数据库与方向目录的路径、import、测试包、示例签名及暂存清单。每个任务加入风险等级、实施者、审查者、并发上限、文件所有权、前置/退出门禁、验证和四项输出。任务 1 明确 F0 契约冻结。

- [ ] **步骤 5A（2–5 分钟）：更新任务 4～6**

修正草稿启动、额度账户、价格、操作槽和不可变流水，统一 `studio/dto`、`quota/dto` 与标准 Service。

- [ ] **步骤 5B（2–5 分钟）：更新任务 7～9**

修正收费/免费任务、执行尝试、SnailJob 扫描与 provider 边界；所有 core 业务类型使用 `task/domain`、`task/dto`、`task/service`。

- [ ] **步骤 5C（2–5 分钟）：更新任务 10～12**

修正用户端方向、草稿、任务、额度接口以及前端公共 adapter、轮询和根容器；共享入口只由用户端集成 owner 修改。

- [ ] **步骤 5D（2–5 分钟）：更新任务 13～15**

修正用户端基础页、平台共享分页适配和方向管理页，逐项对齐加载、空、失败、权限和分页状态。

- [ ] **步骤 5E（2–5 分钟）：更新任务 16～17**

修正平台额度/价格/任务页面与最终门禁。任务 17 明确 F1；F1 通过后 P1/P2 分支 rebase，P3 从同一 F1 基线创建或 rebase。

P1/P2 在此期间只能使用冻结 DTO、Mock 和纯逻辑切片，不得提前执行 `05`／`06` 或真实模型/搜索联调。

- [ ] **步骤 6（2–5 分钟）：运行 P0-C GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('IDirectionCatalogService','DirectionCatalogSnapshotDTO','expectedCatalogVersion','industryCatalogVersion','purposeCatalogVersion','durationRuleVersion','IScriptDraftService','IQuotaBillingService','IAiTaskService','IAiTaskExecutionDispatcher','IAiTaskAttemptService','TaskInitiatorDTO','AppTaskInitiatorResolver','SysTaskInitiatorResolver','StepGuardDTO','ChargeableTaskDTO','QuotaLockRequestDTO','ProviderUsageDTO','ai-video-infra','requireGenerationContextWritable','inheritQuestionnaireTaskGroupMembers','20260728_04a_p0c_task_group_guard.sql','av_ai_task_group_member','idx_av_ai_task_active_group','p0c-f1-contract-addendum.json','membership_only','usageOperationId')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "P0-C 缺少：$required"
}
$p0cSource = Get-Content -Raw -LiteralPath $target[0]
$directionHeader = '(?s)record\s+DirectionCatalogSnapshotDTO\s*\(\s*Long\s+catalogVersion\s*,\s*String\s+contentHash\s*,\s*Long\s+industryCatalogVersion\s*,\s*Long\s+purposeCatalogVersion\s*,\s*String\s+durationRuleVersion\s*,\s*List<IndustryOption>\s+industries\s*,\s*Map<String,\s*List<PurposeOption>>\s+purposesByIndustry\s*,\s*List<TargetDurationOption>\s+targetDurations\s*\)'
if (-not [regex]::IsMatch($p0cSource, $directionHeader)) {
  throw 'P0-C 缺少 DirectionCatalogSnapshotDTO 八个 component 的精确顺序'
}
Assert-NoPlanMatch 'common[./\\]+actor|\bITaskInitiatorResolver\b|\bStepGuard\b' $target 'P0-C 仍保留 common.actor、core resolver 或旧 StepGuard'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $target 'P0-C 仍含旧核心业务分层'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]provider[\\/]' $target 'P0-C 仍把 provider 放在 core'
~~~

预期：稳定服务和 DTO 命中；旧核心业务分层与 core provider 无命中。

- [ ] **步骤 7（2–5 分钟）：review、暂存并提交**

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P0-C 差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P0-C 暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw 'P0-C 暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'P0-C 暂存差异检查失败' }
git commit -m "docs: 修订 P0-C 业务底座实施计划"
if ($LASTEXITCODE -ne 0) { throw 'P0-C 提交失败' }
~~~

预期：暂存清单只有 P0-C 计划，提交成功。

### Task 5：整改 P1 知识中心并拆分独立／集成切片

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md`

**任务卡**

- 单一目标／不做：只整改 P1 计划并定义独立/集成切片，同时同步 P0-C 八 component 方向快照 fixture；不执行 `05` 或实现知识代码，不向平台 HTTP 类型泄漏方向追溯子版本。
- 权威来源：批准规格 P1/F2、P0-C 稳定任务/方向契约、RuoYi 分层。
- 风险等级：红色，P2/P3 消费知识路由和不可变快照。
- 并发：A 编辑；B 前后端契约 review；上限 2。
- 允许影响／依赖：只允许 P1 计划；依赖主计划与 P0-C 注册表。
- 两次检查点：DTO/未定义协作者收口后 review；步骤 5 GREEN 后独立复跑。
- 反向验收／命令：旧类型、旧协作者、routing/application 层零命中；执行步骤 5。
- 验收：P1 可在 P0-B/P0-C 期间推进安全切片，真实联调只在 F1 后发生。

- [ ] **步骤 1（2–5 分钟）：整改知识核心文件树**

- `domain/enums` 的三个业务枚举移到 `knowledge/domain` 根。
- 核心 BO/VO 移到 `ai-video-platform/.../knowledge/domain/bo|vo`。
- `knowledge/application` 改为 `knowledge/service` 和 `service.impl`。
- 服务使用 `IKnowledgeCatalogService`、`IKnowledgePublicationService`、`IKnowledgeRoutingService`、`IKnowledgeSnapshotService`、`IKnowledgeImportService`。
- `knowledge/routing` 三个值对象改为本计划 2.3 的 `knowledge/dto`。
- 删除独立 `KnowledgePublicationValidator.java`；发布校验唯一归入 `knowledge/service/impl/KnowledgePublicationServiceImpl.java` 的私有方法 `validateForPublication(KnowledgeVersion version)`，由 `publish(...)` 在写库前调用，不再创建第二个校验类型或 Service。
- `knowledge/task/KnowledgeImportTaskHandler` 改到直接任务集成职责的 `ai-video-infra/.../knowledge/listener`。
- ZIP 读取、解码和 manifest 原始结构规划到 `ai-video-infra/.../knowledge/provider`。

- [ ] **步骤 2（2–5 分钟）：消除未定义片段类型**

逐个处理旧任务 4～5 中的未定义名称：

- 数据查询改为已列出的具体 Mapper，不保留 `repository`；
- `TemplateCandidate` 定义为 `KnowledgeTemplateCandidateDTO`；
- `RequiredRule` 定义为 `RequiredKnowledgeRuleDTO`；
- `PlanTriple` 统一为 `KnowledgePlanDTO`；
- `AcceptedFactSnapshot` 定义为 `AcceptedFactSnapshotDTO`；
- `FrozenKnowledgePayload`、核心 `KnowledgeSnapshotVo` 统一为 `KnowledgeSnapshotDTO`。

若某名称只用于重复包装，应删除并直接使用已冻结 DTO，不能留下不可执行示例。

`IKnowledgeSnapshotService` 固定为：

~~~java
KnowledgeSnapshotDTO create(KnowledgeSnapshotRequestDTO request);
KnowledgeSnapshotDTO getByRootTaskId(Long rootTaskId);
~~~

`IKnowledgeRoutingService` 固定为：

~~~java
KnowledgeRouteResultDTO route(KnowledgeRouteRequestDTO request);
~~~

`KnowledgeRouteResultDTO` 固定包含按 A、B、C 顺序且恰好三个的 `List<KnowledgePlanDTO> plans`；`KnowledgePlanDTO` 固定承载 `candidateCode`、`planCode`、`primaryTemplateVersionId`、`angleCode`、`differentiatorTechniqueCode`，不得另造 `PlanTriple`。

`KnowledgeSnapshotRequestDTO` 固定携带 `rootTaskId`、`promptVersionId`、`generationContextRevision`、`generationInputHash`、`KnowledgeRouteResultDTO route`、`List<AcceptedFactSnapshotDTO> acceptedFacts`。

- [ ] **步骤 2A（2–5 分钟）：绑定 F1 addendum 并冻结 F2 DTO 证据**

P1 的 F1 rebase 门禁必须同时读取不可变原 `p0c-f1-handoff.json` 与
`p0c-f1-contract-addendum.json`，逐字段验证 exact schema、三项 evidence、独立 review、live SHA、原
`originalF1Head` 与当前 `f1AmendmentHead`；缺失、字段增减、顺序漂移、SHA 漂移或 review 非 `PASS` 均
fail-closed。F2 handoff 必须增加且按 live file hash 回读
`originalF1HandoffSha256`、`f1AddendumSha256`、`originalF1Head`、`f1AmendmentHead`、
`stableDtoComponentRegistry`、`stableDtoSourceSha256`。

`stableDtoComponentRegistry` 精确覆盖 `KnowledgeRouteRequestDTO`、`KnowledgeRouteResultDTO`、
`KnowledgePlanDTO`、`KnowledgeSnapshotRequestDTO`、`KnowledgeSnapshotDTO`；至少
`KnowledgePlanDTO` component 顺序精确为 `String candidateCode`、`String planCode`、
`Long primaryTemplateVersionId`、`String angleCode`、`String differentiatorTechniqueCode`。每个 registry
项必须与 Java record/component 解析结果逐项相等，并以对应源文件 live SHA-256 填入
`stableDtoSourceSha256`。P1 只冻结 DTO 和 A/B/C 顺序；不冻结 `angleSummary` 展示文案。

旧片段协作者按以下方式消除，不创建新平行层：

| 旧名称 | 精确归宿 |
|---|---|
| `VideoTypeRouter`／`videoTypeRouter` | `KnowledgeRoutingServiceImpl.resolveVideoType(KnowledgeRouteRequestDTO)` 私有方法，查询 `VideoTypeRuleMapper` |
| `repository` | 已列出的具体 Knowledge Mapper |
| `requiredRuleValidator` | `KnowledgeRoutingServiceImpl.validateRequiredRules(List<RequiredKnowledgeRuleDTO>)` 私有方法 |
| `planAssembler` | `KnowledgeRoutingServiceImpl.toPlan(...)` 私有方法，返回 `KnowledgePlanDTO` |
| `contextBudget` | `KnowledgeRoutingServiceImpl.enforceContextBudget(List<KnowledgePlanDTO>, int)` 私有方法 |
| `snapshotAssembler` | `KnowledgeSnapshotServiceImpl.freezePayload(KnowledgeSnapshotRequestDTO)` 私有方法 |
| `canonicalJsonWriter` | `KnowledgeSnapshotServiceImpl.toCanonicalJson(KnowledgeSnapshotDTO)` 私有方法 |
| `idGenerator` | 删除；由 Entity 的 MyBatis-Plus `@TableId` 策略在 Mapper insert 时分配 |

- [ ] **步骤 3（2–5 分钟）：拆分独立切片**

明确 P1 在 F1 前可完成：

- `05` SQL 的文本设计与迁移测试草案，但不在共享测试库提前执行；
- 知识状态机、路由纯逻辑、快照哈希与压缩包安全校验测试；
- 平台端 API 类型、`mock/aivideo-knowledge.ts`、ProComponents 页面和状态矩阵。

F1 后才允许执行 `05`、接入 P0-C 任务/方向服务、做真实导入和双端装配。所有构造或读取 `DirectionCatalogSnapshotDTO` 的 P1 fixture／集成门禁必须使用八个精确 component，并断言 `industryCatalogVersion`、`purposeCatalogVersion` 为正数、`durationRuleVersion` 非空；平台 HTTP Mock 仍只使用聚合 `catalogVersion`，不得增加三个子版本。

- [ ] **步骤 4（2–5 分钟）：更新任务 1～3**

逐段修正契约、`05` 迁移、知识版本/审核/发布/退役的文件路径、示例、测试和暂存清单，并加入完整任务卡。

- [ ] **步骤 4A（2–5 分钟）：更新任务 4～6**

修正绑定、确定性路由、不可变快照和安全导入；把未定义类型落实为本任务步骤 2 的 DTO/Mapper，所有示例改用 `service`、`dto`。

- [ ] **步骤 4B（2–5 分钟）：更新任务 7～10**

修正平台 Controller、前端 adapter/Mock/页面、菜单种子和门禁。任务 1～10 均加入风险、实施者、reviewer、并发、所有权、前置/退出门禁、验证及四项输出。

明确平台状态矩阵：加载、初始空、搜索空、分页、网络/5xx 重试、403、导入解析中/冲突/失败、草稿→审核→发布、引用检查和发布确认。

P1 分支在 F1 后 rebase；完成 `05`、知识发布、确定性路由、不可变快照、导入任务和平台端验收后形成 F2。P2 真实后端联调必须等待 F2。

- [ ] **步骤 5（2–5 分钟）：运行 P1 GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p1-knowledge.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('IDirectionCatalogService','DirectionCatalogSnapshotDTO','industryCatalogVersion','purposeCatalogVersion','durationRuleVersion','IKnowledgeRoutingService','IKnowledgeSnapshotService','KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO','validateForPublication','F1','F2','p0c-f1-contract-addendum.json','originalF1HandoffSha256','f1AddendumSha256','originalF1Head','f1AmendmentHead','stableDtoComponentRegistry','stableDtoSourceSha256')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "P1 缺少：$required"
}
Assert-NoPlanMatch '\b(?:TemplateCandidate|RequiredRule|PlanTriple|AcceptedFactSnapshot|FrozenKnowledgePayload|KnowledgeSnapshotVo|VideoTypeRouter)\b|requiredRuleValidator|planAssembler|contextBudget|snapshotAssembler|canonicalJsonWriter|idGenerator' $target 'P1 仍含未整改旧类型或协作者'
Assert-NoPlanMatch '\bKnowledgePublicationValidator\b' $target 'P1 仍保留独立发布校验器'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $target 'P1 仍含旧核心业务分层'
~~~

预期：稳定服务、DTO、门禁均命中；旧名称与旧核心业务分层无命中。

- [ ] **步骤 6（2–5 分钟）：review、暂存并提交**

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P1 差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P1 暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw 'P1 暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'P1 暂存差异检查失败' }
git commit -m "docs: 修订 P1 知识中心实施计划"
if ($LASTEXITCODE -ne 0) { throw 'P1 提交失败' }
~~~

预期：暂存清单只有 P1 计划，提交成功。

### Task 6：整改 P2 问卷并发布 P3 可消费上下文

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md`

**任务卡**

- 单一目标／不做：只整改 P2 计划并冻结方向保存与上下文服务；方向保存只接收聚合预期版本，三个追溯子版本由服务端从同一次快照派生；不执行 `06` 或实现问卷代码。
- 权威来源：批准规格 P2/F3、P1/P0-C 的 `IDirectionCatalogService`／`DirectionCatalogSnapshotDTO` 等稳定契约、事实证据与安全检索规则。
- 风险等级：红色，涉及付费任务、事实证据、分支一致性和 P3 输入。
- 并发：A 编辑；B 安全/事实链 review；上限 2。
- 允许影响／依赖：只允许 P2 计划；依赖 P0-C 注册表和 P1 稳定 Service/DTO。
- 两次检查点：P1/P3 边界完成后 review；步骤 8 GREEN 后独立复跑。
- 反向验收／命令：P1 Mapper/表直查、核心 Port/provider 和旧分层零命中；执行步骤 8。
- 验收：P2 只通过 P1 Service/DTO 获取知识，只向 P3 输出 Service/DTO，不暴露 Mapper。

- [ ] **步骤 1（2–5 分钟）：整改核心 BO/VO、枚举与服务**

- `domain/enums` 的三个业务枚举移到 `questionnaire/domain` 根。
- 核心问卷 BO/VO 移到 `ai-video-user/.../studio/domain/bo|vo`。
- `questionnaire/application` 改为 `questionnaire/service` 与 `service.impl`。
- 公开业务服务统一 `I*Service`；纯业务校验协作者位于 `service.impl`，不得新建 `validation` 或 `model` 层。
- `QuestionIdentity`、`NormalizedAnswer`、`EvidenceContextFact`、执行输入等数据改为 `questionnaire/dto/*DTO`。

- [ ] **步骤 2（2–5 分钟）：删除核心 Port 和 raw provider 对象**

删除核心 `QuestionGenerationPort`、`EvidenceRetrievalPort`、`QuestionGenerationPayload`、`RawQuestionOutput`、`RawQuestionOption`、`QuestionGenerationOutput` 规划。

问题模型调用、JSON Schema 校验、安全 URI 策略、检索抓取和供应商原始类型均位于 `ai-video-infra/.../questionnaire/provider`。infra 任务处理器把结果映射为中性 `questionnaire/dto` 后调用核心 Service，不让核心依赖 infra 类型。

- [ ] **步骤 3（2–5 分钟）：补齐 P1 稳定服务消费**

在真实出题和上下文冻结任务中明确注入：

- `IKnowledgeRoutingService`：按方向、行业、用途和阶段返回 A/B/C 知识计划；
- `IKnowledgeSnapshotService`：创建并读取不可变知识快照；
- 只消费 `knowledge/dto`，不得 import、注入或查询任何 `knowledge.mapper`。

P2 的替身只存在于 F2 前的独立测试与 Mock。F2 rebase 后，生产源码和真实集成测试必须移除 P1 替身；隔离单元测试可保留显式命名、受测试作用域约束的 fake。

- [ ] **步骤 3A（2–5 分钟）：固定方向保存的聚合版本与服务端追溯边界**

`SaveDirectionBo` 与前端保存请求只允许 `draftRevision`、`branchRevision`、`expectedCatalogVersion`、`industryCode`、`industryCustomText`、`purposeCode`、`purposeCustomText`、`targetDurationSeconds`；客户端不得提交 `catalogVersion`、`contentHash`、`industryCatalogVersion`、`purposeCatalogVersion` 或 `durationRuleVersion`。

方向保存 Service 在一次事务内只调用一次 `IDirectionCatalogService.currentPublishedCatalog()`，用返回的同一个 `DirectionCatalogSnapshotDTO` 校验 `expectedCatalogVersion`、行业／用途 code 与时长白名单，再把该快照的 `industryCatalogVersion`、`purposeCatalogVersion`、`durationRuleVersion` 写入 `av_script_direction_revision`。三者是历史追溯事实，不从客户端推导，不用当前目录回填旧修订。Controller／VO 测试必须证明请求增加任一服务端字段都会失败，持久化测试必须证明三个子版本来自同一次快照。

- [ ] **步骤 4（2–5 分钟）：冻结 P3 消费契约**

明确：

- `IQuestionnaireContextService` 按 draft、branch 和 revision 返回准确的当前分支、问答修订、补充修订、上下文就绪状态；
- `IEvidenceReviewService` 返回已接受事实及每个 `factId` 对应的 `decisionRevision`；
- `QuestionnaireContextDTO` 至少包含 `currentBranch`、`answerRevisions`、`supplementRevision` 和上下文就绪状态；接受事实及决定修订只由 `EvidenceReviewContextDTO` 返回；
- 其他输出使用本计划 2.3 的 DTO；
- P3 不得读取 `ScriptBranchEvidenceDecisionMapper`，P2 仍独占其 Entity、Mapper 和 XML。

两个跨阶段接口的签名固定为：

~~~java
public interface IQuestionnaireContextService {
    QuestionnaireContextDTO getCurrentContext(Long draftId, Long branchId);
    QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId, Long branchId);
}

public interface IEvidenceReviewService {
    EvidenceReviewContextDTO getAcceptedContext(Long draftId, Long branchId);
}
~~~

`QuestionnaireContextDTO` 的 `draftId`、`currentBranchId`、`branchRevision`、`generationContextRevision`、`generationInputHash`、问答修订和补充修订均来自同一次只读快照。`EvidenceReviewContextDTO` 固定包含 `draftId`、`branchId`、`List<AcceptedEvidenceFactDTO> acceptedFacts`、`List<EvidenceDecisionRevisionDTO> decisionRevisions`；两组数据都按 `factId` 排序并在同一事务快照中读取，P3 不得拆成两次 Mapper 查询。

`QuestionnaireAnswerRevisionDTO` 必须分别携带 `answerIdentityJson` 与 `answerContextJson`：identity 只由规范化题目身份字段生成，context 只由回答内容与上下文事实生成；HTTP 提交请求禁止接收 owner、identity/hash/context 派生字段。生成入口必须调用
`lockCurrentContextForGeneration`，该方法为非 `readOnly` 的 `Propagation.MANDATORY` 入口，按
`draft → current_branch` 执行 `SELECT ... FOR UPDATE`，在锁内重新校验 tenant/owner、当前分支、双 JSON、
`questionNo`/`factId` 顺序与摘要；随后才按全局锁序进入 `operation_slot → quota_account → task_or_group_member`。
答案、补充、方向和证据决定写入前必须在同一事务调用
`IAiTaskService.requireGenerationContextWritable(Long draftId, Long branchRevision)`。

- [ ] **步骤 5（2–5 分钟）：拆分独立与集成切片**

- P0-C 完成前：只做答案规范化、稳定哈希、完整性策略、Schema、固定 fixture、TypeScript 类型、`mock/aivideo-studio.ts` 和局部组件。
- F1 后：可实现 P2 自有分支/修订持久化与 Service，但 P1 仍使用可删除替身。
- F2 后：rebase，从生产源码和真实集成测试删除 P1 替身，执行 `06`，接入真实知识路由/快照、收费任务、模型和证据检索；单元测试受控 fake 可保留。
- 全部问卷、证据、上下文、迁移和用户端状态验收后形成 F3。

F3 handoff 是 P3 唯一可消费的 P2 冻结证据：必须登记六个稳定 DTO 的
`dtoComponentRegistry`/`dtoSourceSha256`、`IQuestionnaireContextService` 与 `IEvidenceReviewService` 的最终
`serviceSignatures`/`serviceSourceSha256`、`lockedCurrentBranchProtocol`、`p2WriteGuardProtocol`、双 JSON
identity/context 语义以及 `questionNo`/`factId` 稳定排序。P3 在完整 F3 前只能使用可删除替身；F3 后
rebase、核对这些字段及 live SHA，并从生产源码和真实 IT 移除 P2 替身。

- [ ] **步骤 6（2–5 分钟）：固定共享前端 owner 与状态矩阵**

P2 用户端集成 owner 独占 `app.tsx`、工作台根 `index.tsx`、`StudioTopbar.tsx`、`model.ts`、`DemandStep.tsx` 和 `mock/aivideo-studio.ts` 的集成修改。P3 只能先交付受控 props 的 `ScriptStep` 与自身组件。

保留并显式验收：登录恢复、工作区切换、方向空态、排队/运行/成功/失败/过期、额度不足、费率重确认、同答案复用、分支冲突、补充字段、证据空/失败/冲突、提交防重和服务端成功后刷新。

- [ ] **步骤 7（2–5 分钟）：更新任务 1～4**

逐段修正公共契约、`06` 迁移、答案规范化、复用和分支服务的路径、示例、测试、暂存清单，并加入完整任务卡。

- [ ] **步骤 7A（2–5 分钟）：更新任务 5～8**

修正完整性策略、出题任务、安全事实检索和用户端 Controller；明确 F2 前后的 P1 替身边界，所有原始 provider 类型只在 infra。

- [ ] **步骤 7B（2–5 分钟）：更新任务 9～13**

修正 TypeScript 类型、Mock、方向/问答/任务/补充/证据组件和工作台集成。每个任务加入风险等级、实施者、独立审查者、并发上限、精确文件所有权、前置/退出门禁、验证命令和固定四项输出。

任务 13 的退出门禁为 F3，并写明 P3 rebase 与替身删除责任。

- [ ] **步骤 8（2–5 分钟）：运行 P2 GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p2-questionnaire.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('IDirectionCatalogService','DirectionCatalogSnapshotDTO','expectedCatalogVersion','industryCatalogVersion','purposeCatalogVersion','durationRuleVersion','IKnowledgeRoutingService','IKnowledgeSnapshotService','IQuestionnaireContextService','IEvidenceReviewService','QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO','QuestionnaireSupplementRevisionDTO','EvidenceReviewContextDTO','AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO','getCurrentContext','lockCurrentContextForGeneration','getAcceptedContext','requireGenerationContextWritable','answerIdentityJson','answerContextJson','dtoComponentRegistry','dtoSourceSha256','serviceSignatures','serviceSourceSha256','lockedCurrentBranchProtocol','p2WriteGuardProtocol','decisionRevision','F2','F3')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "P2 缺少：$required"
}
Assert-PlanMatch 'ScriptBranchEvidenceDecisionMapper' $target 'P2 缺少自有事实决策 Mapper'
Assert-NoPlanMatch 'QuestionGenerationPort|EvidenceRetrievalPort' $target 'P2 仍规划核心 Port'
Assert-NoPlanMatch 'knowledge[./\\]+mapper|\b(?:KnowledgeItemMapper|KnowledgeVersionMapper|KnowledgeBindingMapper|KnowledgeImportBatchMapper|KnowledgeImportEntryMapper|VideoTypeRuleMapper|KnowledgeSnapshotMapper)\b|(?i:\b(?:from|join|update|insert\s+into|delete\s+from)\s+(?:av_knowledge|av_knowledge_item|av_knowledge_version|av_knowledge_binding|av_knowledge_import_batch|av_knowledge_import_entry|av_video_type_rule|av_knowledge_snapshot)\b)' $target 'P2 仍直接访问 P1 Mapper 或知识表'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $target 'P2 仍含旧核心业务分层'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]provider[\\/]' $target 'P2 仍把 provider 放在 core'
~~~

预期：稳定服务/DTO 和 P2 自有 Mapper 命中；核心 Port、P1 Mapper、旧核心分层和 core provider 无命中。

- [ ] **步骤 9（2–5 分钟）：review、暂存并提交**

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P2 差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P2 暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw 'P2 暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'P2 暂存差异检查失败' }
git commit -m "docs: 修订 P2 问卷实施计划"
if ($LASTEXITCODE -ne 0) { throw 'P2 提交失败' }
~~~

预期：暂存清单只有 P2 计划，提交成功。

### Task 7：整改 P3 文案并彻底移除跨聚合 Mapper

**文件**

- 修改：`docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md`

**任务卡**

- 单一目标／不做：只整改 P3 计划并移除问卷直访；不执行 `07` 或实现文案代码。
- 权威来源：批准规格 P3/F4、P1/P2/P0-C 稳定契约、版本与收费任务规则。
- 风险等级：红色，涉及收费生成、版本确认和事实引用。
- 并发：A 编辑；B 跨聚合/前端 review；上限 2。
- 允许影响／依赖：只允许 P3 计划；依赖 P1/P2 稳定 Service/DTO 注册表。
- 两次检查点：Mapper/冻结输入边界完成后 review；步骤 6 GREEN 后独立复跑。
- 反向验收／命令：问卷 Mapper/表直查、旧 validation/application 层零命中；执行步骤 6。
- 验收：P3 只消费 P1/P2 Service/DTO；Mock 和独立切片足以在 F1 后并行开发。

- [ ] **步骤 1（2–5 分钟）：整改脚本聚合文件树**

- `ScriptVersionSource`、`OptimizationType` 保持在 `script/domain` 根。
- 核心 BO/VO 移到 `ai-video-user/.../script/domain/bo|vo`，Controller 移到 `user/script/controller`。
- `script/application` 改为 `script/service` 与 `service.impl`。
- 稳定服务使用 `IScriptGenerationService`、`IScriptVersionService`、`IUserScriptQueryService`。
- 冻结输入、生成结果、优化结果和命令构造结果改为 `script/dto/*DTO`。
- `script/validation` 的业务规则协作者移入 `script/service.impl`；其中的数据对象移入 `script/dto`；异常使用项目业务异常模型。
- `ai-video-infra/.../script/provider` 保留为直接集成边界；若定义 Service 接口则用 `I*Service`，否则命名为明确的 client/provider 类。

- [ ] **步骤 2（2–5 分钟）：移除 9 个 P2 Mapper 语义点**

从前置依赖、文件清单、任务 4、示例 import、构造器、变量调用、mock 和测试中删除：

- `ScriptBranchEvidenceDecisionMapper.java`；
- `ScriptBranchEvidenceDecisionMapper.xml`；
- `scriptBranchEvidenceDecisionMapper.selectAcceptedDecisionRevisions(...)`。

旧计划显式名称/路径有 7 行，另有 2 个变量级直接调用；整改后 P3 对该 Mapper、`questionnaire/mapper` 和问卷表均为零引用。

冻结输入装配只注入 `IQuestionnaireContextService`、`IEvidenceReviewService`、`IKnowledgeRoutingService`、`IKnowledgeSnapshotService`，并消费稳定 DTO。

- [ ] **步骤 2A（2–5 分钟）：固定 F1→F2→F3 消费链与推荐理由派生**

P3 真实集成前必须依次验证：F1 原 handoff 与 F1 addendum 的 live SHA/exact schema/review；F2 handoff 的五 DTO
`stableDtoComponentRegistry`/`stableDtoSourceSha256`；F3 handoff 的六 DTO registry/source SHA、两个 Service
最终签名、`lockCurrentContextForGeneration` 锁协议、P2 写守卫、答案双 JSON 与顺序协议。任何一层字段、SHA、
head 或 review 漂移都 fail-closed；验证通过后才可移除 P1/P2 替身并接真实冻结输入。

`ScriptVersionDTO.angleSummary` 只能由服务端 `ScriptRecommendationReasonFormatter` 使用 formatter version
`script-recommendation-1`，按 F2 冻结的 A/B/C route rank 和对应 `KnowledgePlanDTO` 的
`candidateCode/planCode/angleCode/primaryTemplateVersionId/differentiatorTechniqueCode` 确定性派生。同一
route/plan 必须逐字相同。生成/优化 provider schema 均禁止 `angleSummary` 或推荐理由字段；provider 夹带时按
unknown property 拒绝，绝不能输出、默认补值或覆盖服务端派生结果。

- [ ] **步骤 3（2–5 分钟）：拆分独立与集成切片**

- F1 后可做：有效字符、时长、相似度和内容规则；版本树纯逻辑；前端 scripts 类型、adapter、query key、候选/编辑/优化/文案库组件。
- F3 前：P1/P2 使用可删除替身，禁止真实收费生成、事实冻结和确认。
- F3 后：rebase，从生产源码和真实集成测试删除 P1/P2 替身，接入真实问卷/事实、冻结输入、收费任务、模型调用、优化、确认和下游引用；隔离单元测试的受控 fake 可保留。
- 全链路和 `07` 迁移通过后形成 F4。

- [ ] **步骤 4（2–5 分钟）：增加独立 Mock 文件**

在文件结构和任务 7 中创建 `ai-video-ui/ai-video-webapp/mock/aivideo-scripts.ts`，覆盖：

- 生成中与修复中；
- A/B/C 三候选及每套三个标题；
- 额度不足、费率变化、上下文过期、403、版本冲突；
- 文案库初始空、搜索空、分页；
- 删除确认和被下游引用阻止。

不得复用或修改 P2 的 `mock/aivideo-studio.ts` 来承载 P3 fixtures。

- [ ] **步骤 5（2–5 分钟）：更新任务 1～3**

逐段修正 `07` 迁移、输出校验、文案主体和版本树的路径、示例、测试、暂存清单，并加入完整任务卡。

- [ ] **步骤 5A（2–5 分钟）：更新任务 4～6**

修正冻结输入、收费生成、优化和用户端接口；删除全部 P2 Mapper 语义点，改用 P1/P2 稳定 Service/DTO。

- [ ] **步骤 5B（2–5 分钟）：更新任务 7～9**

修正 scripts TypeScript 边界、独立 Mock、文案库、下游版本引用和最终门禁。任务 1～9 各加入风险等级、实施者、独立审查者、并发上限、精确文件所有权、前置/退出门禁、验证命令和固定四项输出；任务 9 的退出门禁为 F4。

P3 独占 `services/ai-video/scripts/**`、`ScriptStep.tsx`、`LibraryView.tsx`、`VoiceStep.tsx`、`BaseStep.tsx` 和自身组件。工作台根集成由共享 owner 在 F3 后串行完成。

显式验收 P3 状态矩阵：上下文未就绪、生成/修复中、无候选、额度不足、费率变化、上下文过期、403、版本冲突、保存/确认中、版本树、文案库空/搜索空/分页、删除确认和引用阻止。

- [ ] **步骤 6（2–5 分钟）：运行 P3 GREEN 检查**

运行：

~~~powershell
$target = @('.\docs\superpowers\plans\2026-07-28-say-requirements-copy-generation-p3-script.md')
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 执行失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
foreach ($required in @('IScriptGenerationService','IScriptVersionService','IUserScriptQueryService','IQuestionnaireContextService','IEvidenceReviewService','aivideo-scripts.ts','p0c-f1-contract-addendum.json','stableDtoComponentRegistry','stableDtoSourceSha256','dtoComponentRegistry','dtoSourceSha256','lockCurrentContextForGeneration','answerIdentityJson','answerContextJson','ScriptRecommendationReasonFormatter','script-recommendation-1','angleSummary','F1','F2','F3','F4')) {
  Assert-PlanMatch ([regex]::Escape($required)) $target "P3 缺少：$required"
}
$forbiddenQuestionnaireAccess = ('questionnaire[./\\]+mapper|\b(?:EvidenceBatchMapper|EvidenceSourceMapper|EvidenceFactMapper|EvidenceFactDecisionMapper|QuestionnaireExecutionInputMapper|ScriptQuestionMapper|ScriptAnswerRevisionMapper|ScriptSupplementRevisionMapper|ScriptBranchMapper|ScriptBranchQuestionMapper|ScriptDirectionRevisionMapper|ScriptBranchEvidence' + 'DecisionMapper)\b|\bscriptBranchEvidence' + 'DecisionMapper\b|(?i:\b(?:from|join|update|insert\s+into|delete\s+from)\s+(?:av_evidence_batch|av_evidence_source|av_evidence_fact|av_evidence_fact_decision|av_questionnaire_execution_input|av_script_question|av_script_answer_revision|av_script_supplement_revision|av_script_branch|av_script_branch_question|av_script_direction_revision|av_script_branch_evidence_decision)\b)')
Assert-NoPlanMatch $forbiddenQuestionnaireAccess $target 'P3 仍直接依赖 P2 Mapper 或问卷表'
Assert-NoPlanMatch 'ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]' $target 'P3 仍含旧核心业务分层'
~~~

预期：稳定服务、Mock 和门禁命中；P2 Mapper 与旧核心业务分层无命中。P3 计划自身不得把禁止 Mapper 名写进扫描命令，否则无法满足零引用。

- [ ] **步骤 7（2–5 分钟）：review、暂存并提交**

~~~powershell
$expected = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md')
$stagedBefore = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0 -or $stagedBefore.Count -ne 0) { throw '提交前暂存区必须为空' }
git diff --check -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P3 差异检查失败' }
git add -- $expected
if ($LASTEXITCODE -ne 0) { throw 'P3 暂存失败' }
$actual = @(git diff --cached --name-only)
$difference = Compare-Object -ReferenceObject $expected -DifferenceObject $actual
if ($LASTEXITCODE -ne 0 -or $difference) { $difference; throw 'P3 暂存集合不准确' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'P3 暂存差异检查失败' }
git commit -m "docs: 修订 P3 文案实施计划"
if ($LASTEXITCODE -ne 0) { throw 'P3 提交失败' }
~~~

预期：暂存清单只有 P3 计划，提交成功。

## 五、跨计划收口与最终验证

### Task 8：执行六计划零漂移扫描和独立验收

**文件**

- 复核：六份目标计划。
- 不修改：P4 和所有业务代码。
- 如发现一致性问题，只由 A 修正相应目标计划；B 重新验证。

**任务卡**

- 单一目标／不做：只验证并收口六份计划；不改 P4 或业务代码。
- 权威来源：批准规格、六份已整改计划、P0-A 实际代码和文档规范脚本。
- 风险等级：红色，决定计划是否可交给三人实施。
- 并发：R0 只含 A 运行检查/修正与 B 独立复跑，最多两人；前端文件所有权审计另建 R1 只读任务。
- 允许影响／依赖：必要修正仅限六份计划；依赖 Task 2～7 已提交。
- 两次检查点：A 完成步骤 1～8 后 B 独立复跑；任何修正提交后 B 再完整复跑。
- 反向验收／命令：跨聚合访问、旧分层、占位、P4/业务改动均为零；执行步骤 1～8。
- 验收：分层、类型、门禁、迁移、文件所有权、Mock 和 P3 边界全部一致。

- [ ] **步骤 1（2–5 分钟）：重建目标集并恢复基线 SHA**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
$missing = $plans | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) { $missing; throw '目标计划缺失' }
$masterCommit = @(git log --format='%H' --fixed-strings --grep='docs: 对齐说需求主计划并行门禁' -1)
if ($LASTEXITCODE -ne 0 -or $masterCommit.Count -ne 1) { throw '无法定位主计划整改提交' }
$baselineCommit = git rev-parse "$($masterCommit[0])^"
if ($LASTEXITCODE -ne 0 -or -not $baselineCommit) { throw '无法恢复整改基线提交' }
"BASELINE_COMMIT=$baselineCommit"
~~~

预期：六个文件均存在，输出的 SHA 与 Task 1“验证证据”记录完全相同。

- [ ] **步骤 2（2–5 分钟）：扫描禁止的 core 规划路径**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
$coreForbidden = 'ai-video-core[\\/]src[\\/](?:main|test)[\\/]java[\\/]org[\\/]dromara[\\/]aivideo[\\/][^ \r\n]*[\\/](?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)[\\/]'
$coreBoVo = 'ai-video-core[\\/]src[\\/]main[\\/]java[\\/]org[\\/]dromara[\\/]aivideo[\\/][^ \r\n]*[\\/]domain[\\/](?:bo|vo)[\\/]'
$coreDomainEnums = 'ai-video-core[\\/]src[\\/]main[\\/]java[\\/]org[\\/]dromara[\\/]aivideo[\\/][^ \r\n]*[\\/]domain[\\/]enums[\\/]'
$coreProvider = 'ai-video-core[\\/]src[\\/]main[\\/]java[\\/]org[\\/]dromara[\\/]aivideo[\\/](?:[^ \r\n]*[\\/])?provider[\\/]'
Assert-NoPlanMatch $coreForbidden $plans '计划仍含禁止的 core 业务路径'
Assert-NoPlanMatch $coreBoVo $plans '计划仍把 HTTP BO/VO 放在 core'
Assert-NoPlanMatch $coreDomainEnums $plans '计划仍把业务枚举放在 domain/enums'
Assert-NoPlanMatch $coreProvider $plans '计划仍把外部 provider 放在 core'
Assert-NoPlanMatch 'org\.dromara\.aivideo\.(?!infra(?:\.|$))[A-Za-z0-9_.]+\.(?:application|port|adapter|command|model|aggregate|repository|routing|validation|infra)\.' $plans '计划仍含禁止的点式 Java 包引用'
~~~

预期：四次断言无输出、无异常。

- [ ] **步骤 3（2–5 分钟）：扫描跨聚合和旧 P0-A 引用**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
Assert-NoPlanMatch 'identity[./\\]+(?:application|model)[./\\]+' $plans '计划仍引用旧 P0-A 包'
Assert-NoPlanMatch 'common[./\\]+actor|\bITaskInitiatorResolver\b' $plans '计划仍保留 common.actor 或 core resolver'
$p3 = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md')
$p2 = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md')
$p3Access = ('questionnaire[./\\]+mapper|\b(?:EvidenceBatchMapper|EvidenceSourceMapper|EvidenceFactMapper|EvidenceFactDecisionMapper|QuestionnaireExecutionInputMapper|ScriptQuestionMapper|ScriptAnswerRevisionMapper|ScriptSupplementRevisionMapper|ScriptBranchMapper|ScriptBranchQuestionMapper|ScriptDirectionRevisionMapper|ScriptBranchEvidence' + 'DecisionMapper)\b|\bscriptBranchEvidence' + 'DecisionMapper\b|(?i:\b(?:from|join|update|insert\s+into|delete\s+from)\s+(?:av_evidence_batch|av_evidence_source|av_evidence_fact|av_evidence_fact_decision|av_questionnaire_execution_input|av_script_question|av_script_answer_revision|av_script_supplement_revision|av_script_branch|av_script_branch_question|av_script_direction_revision|av_script_branch_evidence_decision)\b)')
$p2Access = 'knowledge[./\\]+mapper|\b(?:KnowledgeItemMapper|KnowledgeVersionMapper|KnowledgeBindingMapper|KnowledgeImportBatchMapper|KnowledgeImportEntryMapper|VideoTypeRuleMapper|KnowledgeSnapshotMapper)\b|(?i:\b(?:from|join|update|insert\s+into|delete\s+from)\s+(?:av_knowledge|av_knowledge_item|av_knowledge_version|av_knowledge_binding|av_knowledge_import_batch|av_knowledge_import_entry|av_video_type_rule|av_knowledge_snapshot)\b)'
Assert-NoPlanMatch $p3Access $p3 'P3 仍直接访问 P2 Mapper 或问卷表'
Assert-NoPlanMatch $p2Access $p2 'P2 仍直接访问 P1 Mapper 或知识表'
~~~

预期：四次断言无输出、无异常。

- [ ] **步骤 4（2–5 分钟）：核对稳定服务和 DTO**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
foreach ($required in @('IWorkspaceAuthorizationService','IAiTaskService','IQuotaBillingService','IDirectionCatalogService','DirectionCatalogSnapshotDTO','expectedCatalogVersion','industryCatalogVersion','purposeCatalogVersion','durationRuleVersion','IKnowledgeRoutingService','IKnowledgeSnapshotService','IQuestionnaireContextService','IEvidenceReviewService','IScriptGenerationService','IScriptVersionService','IUserScriptQueryService','WorkspaceContextDTO','TaskCreationResultDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO','KnowledgeSnapshotDTO','QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO','QuestionnaireSupplementRevisionDTO','EvidenceReviewContextDTO','AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO','requireGenerationContextWritable','inheritQuestionnaireTaskGroupMembers','lockCurrentContextForGeneration','stableDtoComponentRegistry','stableDtoSourceSha256','dtoComponentRegistry','dtoSourceSha256','answerIdentityJson','answerContextJson','script-recommendation-1','decisionRevision')) {
  Assert-PlanMatch ([regex]::Escape($required)) $plans "六计划缺少：$required"
}
Assert-PlanMatch 'KnowledgeRouteResultDTO\s+route\s*\(\s*KnowledgeRouteRequestDTO\s+request\s*\)' $plans '六计划缺少 IKnowledgeRoutingService 精确签名'
Assert-PlanMatch 'QuestionnaireContextDTO\s+getCurrentContext\s*\(\s*Long\s+draftId\s*,\s*Long\s+branchId\s*\)' $plans '六计划缺少 IQuestionnaireContextService 精确签名'
Assert-PlanMatch 'void\s+requireGenerationContextWritable\s*\(\s*Long\s+draftId\s*,\s*Long\s+branchRevision\s*\)' $plans '六计划缺少 IAiTaskService 写守卫精确签名'
Assert-PlanMatch 'void\s+inheritQuestionnaireTaskGroupMembers\s*\(\s*Long\s+draftId\s*,\s*Long\s+sourceBranchRevision\s*,\s*Long\s+targetBranchRevision\s*,\s*List<Long>\s+retainedRootTaskIds\s*,\s*TaskInitiatorDTO\s+initiator\s*\)' $plans '六计划缺少任务组继承精确签名'
Assert-PlanMatch 'QuestionnaireContextDTO\s+lockCurrentContextForGeneration\s*\(\s*Long\s+draftId\s*,\s*Long\s+branchId\s*\)' $plans '六计划缺少生成上下文锁精确签名'
Assert-PlanMatch 'EvidenceReviewContextDTO\s+getAcceptedContext\s*\(\s*Long\s+draftId\s*,\s*Long\s+branchId\s*\)' $plans '六计划缺少 IEvidenceReviewService 精确签名'
$p0c = 'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md'
$p0cSource = Get-Content -Raw -LiteralPath $p0c
$directionHeader = '(?s)record\s+DirectionCatalogSnapshotDTO\s*\(\s*Long\s+catalogVersion\s*,\s*String\s+contentHash\s*,\s*Long\s+industryCatalogVersion\s*,\s*Long\s+purposeCatalogVersion\s*,\s*String\s+durationRuleVersion\s*,\s*List<IndustryOption>\s+industries\s*,\s*Map<String,\s*List<PurposeOption>>\s+purposesByIndustry\s*,\s*List<TargetDurationOption>\s+targetDurations\s*\)'
if (-not [regex]::IsMatch($p0cSource, $directionHeader)) {
  throw 'P0-C 方向快照八个 component 名称或顺序漂移'
}
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
Assert-NoPlanMatch '\b(?:AppIdentityService|AppSessionService|AppPermissionService|AppSecurityAuditService|AppPrincipalSnapshot|AppWorkspaceSessionSnapshot|AppSecurityAuditCommand)\b|AppSessionRevisionGuard\.requireCurrent|AppLoginHelper\.requirePrincipal' $plans '六计划仍保留旧 P0-A 简单类型或方法'
Assert-NoPlanMatch '\b(?:WorkspaceAuthorizationService|QuotaBillingService|KnowledgeRoutingService|KnowledgeSnapshotService|QuestionnaireContextService|EvidenceReviewService|ScriptGenerationService|ScriptVersionService|UserScriptQueryService)\b' $plans '六计划仍保留无 I 前缀的跨阶段 Service'
$p2 = @('docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md')
Assert-NoPlanMatch 'SaveDirectionBo[^\r\n]*(?:contentHash|industryCatalogVersion|purposeCatalogVersion|durationRuleVersion)' $p2 'P2 的 SaveDirectionBo 仍接收服务端目录摘要或追溯子版本'
$p2Source = Get-Content -Raw -LiteralPath $p2[0]
$directionFixtureMatches = [regex]::Matches($p2Source, '(?s)const\s+directionCatalogFixture\s*=\s*\{(?<body>.*?)\};')
foreach ($fixture in $directionFixtureMatches) {
  if ($fixture.Groups['body'].Value -match '\b(?:contentHash|industryCatalogVersion|purposeCatalogVersion|durationRuleVersion)\s*:') {
    throw 'P2 的前端方向目录 fixture 泄漏服务端目录摘要或追溯子版本'
  }
}
~~~

预期：主计划和对应子计划使用完全相同拼写；不得出现无 `I` 的同义 Service 或无 `DTO` 的同义跨模块值对象。

- [ ] **步骤 5（2–5 分钟）：核对门禁、迁移和 Mock**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Assert-PlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -eq 0) { throw $Message }
  $matches
}
foreach ($required in @('F0','F1','F2','F3','F4','20260728_01_p0a_identity_security.sql','20260728_02_p0b_workspace_authorization.sql','20260728_03_p0c_task_quota_direction.sql','20260728_04_p0_seed.sql','20260728_04a_p0c_task_group_guard.sql','20260728_05_p1_knowledge.sql','20260728_06_p2_questionnaire.sql','20260728_07_p3_script.sql','mock/aivideo-knowledge.ts','mock/aivideo-studio.ts','mock/aivideo-scripts.ts')) {
  Assert-PlanMatch ([regex]::Escape($required)) $plans "六计划缺少：$required"
}
~~~

预期：门禁无缺号；迁移阶段所有权符合 `01→02→03→04→04a→05→06→07`；P1/P2/P3 各有独立 Mock。

- [ ] **步骤 6（2–5 分钟）：扫描不完整占位**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
function Assert-NoPlanMatch([string] $Pattern, [string[]] $Paths, [string] $Message) {
  $matches = @(rg -n -P $Pattern -- $Paths)
  if ($LASTEXITCODE -gt 1) { throw "rg 扫描失败：$Message" }
  if ($matches.Count -gt 0) { $matches; throw $Message }
}
$placeholderPattern = @('TO' + 'DO', 'FIX' + 'ME', 'T' + 'BD', '待' + '定', '待补' + '充', '后续实' + '现', '类似任' + '务', '类似上' + '面') -join '|'
Assert-NoPlanMatch $placeholderPattern $plans '计划仍有不完整占位'
~~~

预期：无输出、无异常。

- [ ] **步骤 7（2–5 分钟）：确认只修改六份计划**

运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
$masterCommit = @(git log --format='%H' --fixed-strings --grep='docs: 对齐说需求主计划并行门禁' -1)
if ($LASTEXITCODE -ne 0 -or $masterCommit.Count -ne 1) { throw '无法定位主计划整改提交' }
$baselineCommit = git rev-parse "$($masterCommit[0])^"
if ($LASTEXITCODE -ne 0 -or -not $baselineCommit) { throw '无法恢复整改基线提交' }
$actual = @(git diff --name-only "$baselineCommit..HEAD" | Sort-Object -Unique)
if ($LASTEXITCODE -ne 0) { throw '无法读取整改文件集合' }
$difference = Compare-Object -ReferenceObject @($plans | Sort-Object -Unique) -DifferenceObject $actual
if ($difference) { $difference; throw '整改文件集合不等于六份目标计划' }
$actual
~~~

预期：输出且只输出六份目标计划；P4 和业务代码不在清单中。补交、合并或额外收口提交不会改变判定。

- [ ] **步骤 8（2–5 分钟）：运行文档规范与 Git 校验**

运行：

~~~powershell
$masterCommit = @(git log --format='%H' --fixed-strings --grep='docs: 对齐说需求主计划并行门禁' -1)
if ($LASTEXITCODE -ne 0 -or $masterCommit.Count -ne 1) { throw '无法定位主计划整改提交' }
$baselineCommit = git rev-parse "$($masterCommit[0])^"
if ($LASTEXITCODE -ne 0 -or -not $baselineCommit) { throw '无法恢复整改基线提交' }
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\validate-development-standards.ps1
if ($LASTEXITCODE -ne 0) { throw '文档规范校验失败' }
git diff --check "$baselineCommit..HEAD"
if ($LASTEXITCODE -ne 0) { throw '整改提交差异检查失败' }
git status --short --branch
~~~

预期：输出 `DEVELOPMENT_STANDARDS_OK`；`git diff --check` 无输出；工作区干净。

- [ ] **步骤 9（2–5 分钟）：独立 reviewer 复跑**

B 不读取 A 的结论，独立复跑步骤 1～8，并从以下三视角给出结论：

- 前端：页面、状态、字段、Mock、共享文件 owner；
- 后端：RuoYi 分层、权限、归属、任务、额度、账号/工作区、跨聚合 DTO；
- 联调：独立切片、F0～F4、rebase、迁移、验收和剩余风险。

输出必须使用“完成项、风险、验证证据、阻塞项”。任何“必须修复”项由 A 修正并重新执行全部扫描。

- [ ] **步骤 10（2–5 分钟）：必要时提交一致性修正**

仅当步骤 9 产生实际文档修正时运行：

~~~powershell
$plans = @(
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0b-workspace-authorization.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md',
  'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p3-script.md'
)
$allChanged = @(git diff --name-only)
if ($LASTEXITCODE -ne 0) { throw '无法读取收口改动' }
$unexpected = @($allChanged | Where-Object { $_ -notin $plans })
if ($unexpected) { $unexpected; throw '收口改动超出六份目标计划' }
if ($allChanged.Count -eq 0) { 'NO_CONSISTENCY_FIX_COMMIT_REQUIRED'; exit 0 }
git diff --check -- $allChanged
if ($LASTEXITCODE -ne 0) { throw '收口改动差异检查失败' }
git add -- $allChanged
if ($LASTEXITCODE -ne 0) { throw '收口改动暂存失败' }
$staged = @(git diff --cached --name-only)
if ($LASTEXITCODE -ne 0) { throw '无法读取收口暂存区' }
$difference = Compare-Object -ReferenceObject @($allChanged | Sort-Object) -DifferenceObject @($staged | Sort-Object)
if ($difference) { $difference; throw '收口暂存集合与改动集合不一致' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '收口暂存差异检查失败' }
git commit -m "docs: 收口说需求并行计划契约"
if ($LASTEXITCODE -ne 0) { throw '收口提交失败' }
~~~

预期：暂存清单只含实际修正的目标计划；无修正时输出 `NO_CONSISTENCY_FIX_COMMIT_REQUIRED`，不创建空提交。提交后重新执行步骤 1～9。

## 六、完成定义

本计划完成必须同时满足：

- 六份计划均引用批准规格并与 P0-A 实际类型、方法和审计 DTO 一致；
- P0-B 明确解决“`replaceWorkspace` 只接受个人工作区”的实现阻塞，公开接口不变且反向测试完整；
- 所有业务规划使用 RuoYi `domain/dto/mapper/service/service.impl` 与端侧 BO/VO；
- 外部 AI/检索 provider、client 和 raw 类型只位于 `ai-video-infra`；
- 主计划和子计划的稳定 Service/DTO 拼写一致；
- `DirectionCatalogSnapshotDTO` 的八个 component、正数／非空校验和 fixture 已冻结；HTTP 仅公开聚合版本，P2 从同一次 published snapshot 服务端持久化三个追溯子版本；
- 每个目标任务都有风险、实施者、审查者、并发、所有权、门禁、验证和四项输出；
- 三人始终有独立工作，但共享契约仍由单一 owner 串行落地；
- P1/P2/P3 都写清独立切片、集成切片、rebase 点和 F2/F3/F4；
- P1 不再保留未定义片段类型，P2 对知识 Mapper 为零引用；
- P3 对问卷 Mapper 和 `ScriptBranchEvidenceDecisionMapper` 为零引用；
- P3 拥有独立 `mock/aivideo-scripts.ts` 和完整状态矩阵；
- 数据库顺序严格为 `01→02→03→04→04a→05→06→07`；
- P4 与业务代码未被本轮修改；
- 文档规范校验、`git diff --check` 和独立 review 全部通过，工作区干净。

## 七、实施后的下一步

计划整改通过后，不直接把三个人都投入同一基座文件。按批准规格启动业务开发：

1. A 执行 P0-B；B 执行 P1 独立切片；C 执行 P2 独立切片。
2. A 转入 P0-C 时，B/C 继续各自独立切片。
3. F1 后，A 转入 P3 独立切片；B 完成 P1 集成；C 在 F2 前完成 P2 自有实现。
4. F2 后 P2 rebase，并从生产/真实集成移除 P1 替身；F3 后 P3 rebase，并从生产/真实集成移除 P1/P2 替身；单元测试受控 fake 可保留，F4 后才评估 P4。
