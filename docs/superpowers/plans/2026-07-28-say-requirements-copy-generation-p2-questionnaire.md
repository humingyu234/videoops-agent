# P2 逐题问卷与事实证据实现计划

> **面向执行者：** 实施本计划时必须使用 `subagent-driven-development` 或 `executing-plans`，逐任务执行 TDD（测试驱动开发）；完成声明前必须使用 `verification-before-completion`，合并前必须使用 `requesting-code-review`。

**目标：** 在不裁剪原始需求的前提下，交付可恢复、可分支、可计费、可追溯的逐题问卷、确定性补充字段和可选事实证据审核，并向 P3 冻结只读问卷上下文与接受事实契约。

**架构：** P2 在 `ai-video-core` 使用 RuoYi 的贫血 Entity（实体）加 Service（业务服务）编排，目录只使用 `domain`、平级 `dto`、`mapper`、`service`、`service.impl`；HTTP（超文本传输协议）BO/VO 和 Controller 只位于 `ai-video-user`；直接模型、搜索、抓取、SSRF（服务端请求伪造）防护和任务 Handler 位于 `ai-video-infra` 的 `provider`、`client`、`listener`。P2 只消费 P0-C/P1 的稳定 Service/DTO，不读取上游 Mapper、表、Entity、端侧 BO/VO 或供应商原始类型。

**技术栈：** Java 21、Spring Boot 4.1、RuoYi-Vue-Plus 6.x、MyBatis-Plus、MySQL 8、Redis 7、SnailJob、React 19、TypeScript、Umi Max 4、Ant Design 6、React Query 5、Vitest、Testing Library、MSW。

---

## 1. 权威基线、范围和执行纪律

本计划的权威输入按优先级固定为：

1. `AGENTS.md`、`RULES.md`、`docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`；
2. `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-master.md`；
3. `docs/superpowers/specs/2026-08-02-say-requirements-copy-generation-parallel-delivery-design.md`；
4. `docs/superpowers/plans/2026-08-02-say-requirements-copy-generation-parallel-plan-reconciliation.md` 的 P2/Task 6；
5. `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p0c-business-foundation.md`；
6. 提交 `eb5aac8a` 中的 `docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p1-knowledge.md`；
7. 原业务规格 `docs/superpowers/specs/2026-07-28-say-requirements-copy-generation-design.md`；
8. `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/FRONTEND_GUIDE.md`、`docs/BACKEND_GUIDE.md` 与对应编码标准。

本文件只定义实施步骤，不实施业务。开始业务实现时必须从批准基线创建独立 `codex/` 分支和隔离 worktree；P2 writer 不得在 P0-C/P1 worktree 写文件。所有公共契约、共享入口和迁移清单由当期单一 owner 串行修改，reviewer 只读复核，禁止两人同时编辑同一文件。

### 1.1 完整业务范围

以下范围全部交付，不允许以 MVP（最小可行产品）为由删除：

1. 行业和用途使用“已发布稳定代码单选 + 可选自定义文本”，自定义文本最多 100 字；目标时长只接受 `30/45/60/90/120`。
2. 每次只展示一个问题；模型响应使用 `question-generation-1`，包含目标槽、问题文本、2–6 个业务选项和推荐项。普通选项代码由服务端按 `option_01` 起顺序生成，固定多选，并追加唯一 `{code:"custom", label:"自定义"}`。
3. HTTP 请求只使用 `selectedOptionCodes`；服务端校验后映射到内部 identity 的 `selectedCodes`。`custom` 永不进入任一数组；未勾选的本地自定义文本不得进入请求、哈希、上下文或日志。
4. 答案先做普通代码排序去重、自定义文本 NFC、首尾裁剪和空白折叠；`answerHash` 只对十进制字符串 `questionId`、问题摘要 `questionHash`、有序 `selectedCodes`、显式 `customSelected` 和有效 `customText` 五项内部 identity JSON 计算 SHA-256。问题编号、正文、目标槽和选项语义只进入独立 context/DTO，不得改变答案身份。
5. 首次回答只递增 `draftRevision` 和 `generationContextRevision`；相同答案哈希不新增修订、任务或账本；改答创建子分支并令三个修订各加一，只复制到当前题，排除后续问题和任务。
6. 保存答案与创建下一次收费任务是两个事务；额度不足或费率需确认时答案仍已保存，并以成功响应返回 `nextAction=resolve_quota|reconfirm_tariff`。
7. 问卷至少三题、最多五题；前三题固定 `subject → audience → coreMessage`，第 4/5 题按 `subject → audience → coreMessage → callToAction → mustKeepFacts → prohibitedContent` 选择首个缺失槽。
8. 第五题后仍不完整时展示九个免费确定性补充字段：`subject`、`audience`、`coreMessages`、`targetDurationSeconds`、`callToAction`、`mustKeepFacts`、`prohibitedContents`、`toneStyles`、`otherNotes`；规范文本总长不超过 16,000 字。
9. 外部事实检索是显式、可选、收费操作；候选事实状态为 `pending/conflicted`，不可变用户决定只允许 `accepted/rejected`；冲突事实默认不能接受。
10. 只有当前分支采用的 `accepted` 决定修订进入后续上下文；`factId → decisionRevision` 必须来自同一次只读事务快照。
11. 同一操作槽只有“相同幂等键 + 相同请求哈希”可复用；同槽不同幂等键冲突返回 `46123`，客户端不得自动换键重试。
12. 任务轮询前 10 秒每秒一次，之后每 2 秒一次；终态、分支切换、离页或连续 3 次网络失败停止，联网恢复和窗口聚焦时各刷新一次。
13. 页面覆盖登录恢复、工作区切换、加载、初始空、方向空、请求失败、403、额度不足、费率重确认、排队、运行、成功、失败、取消、过期、修订冲突、同答案复用、改答警告、补充、证据空/失败/冲突和提交防重。
14. `question_generate` 与 `evidence_retrieve` 都遵守 `create pending → freeze immutable input → enqueue` 同一事务；复用时立即返回，不再次冻结或入队。
15. 每次真实模型或搜索调用恰好创建一个 P0-C attempt；初次问题结构无效时同一 execution/lease 只允许一个 `repair`，全部真实调用共同受单根任务最多 3 次上限约束。
16. provider attempt 成功不等于业务结果采用；结果事务写入前必须锁定并重核 `branchRevision`、`generationContextRevision`、`generationInputHash`，过期时按 `STALE_BRANCH_RESULT` 确定性失败。

### 1.2 修订状态机与继承/排除矩阵

方向修订必须冻结 `industryCatalogVersion`、`purposeCatalogVersion`、`durationRuleVersion` 以及已发布目录条目的稳定 code。保存事务只读取一次当前 published `DirectionCatalogSnapshotDTO`；客户端只传 `expectedCatalogVersion`，它与 snapshot `catalogVersion` 不同即返回目录漂移，三个子版本只从该 snapshot 派生，禁止客户端传入、二次读取或把旧 code 静默映射到新目录。

| 操作 | `draftRevision` | `branchRevision` | `generationContextRevision` | 新分支 | 继承 | 排除 |
|---|---:|---:|---:|---|---|---|
| 首次完整方向 | +1 | 不变 | +1 | 否 | 当前草稿空上下文 | 无 |
| 同方向规范 hash | 不变 | 不变 | 不变 | 否 | 原方向修订 | 新任务、账本均不创建 |
| 替换行业/用途/时长 | +1 | +1 | +1 | `direction_changed` | 仅新方向修订 | 旧问题/答案、补充、事实决定、问卷任务组、候选文案 |
| 首次答案 | +1 | 不变 | +1 | 否 | 当前方向、此前题目/答案 | 无 |
| 同答案 hash | 不变 | 不变 | 不变 | 否 | 原答案修订 | 新答案、任务、账本均不创建 |
| 改答第 N 题 | +1 | +1 | +1 | `answer_changed` | 方向、题号 `1..N` 的问题及采用答案，保留问题 `sourceRootTaskId` | `N+1..末题` 问题/答案、其任务组、补充、事实决定、候选文案 |
| 首次有效补充 | +1 | 不变 | +1 | 否 | 方向、全部当前问题/答案 | 旧空补充 |
| 同补充 hash | 不变 | 不变 | 不变 | 否 | 原补充修订 | 新行、任务、账本均不创建 |
| 替换或移除有效补充 | +1 | +1 | +1 | `supplement_changed` | 方向、全部当前问题/答案及任务成员 | 旧补充、旧事实决定、候选文案 |
| 首次事实决定（批量请求内可多项） | +1（整批一次） | 不变 | +1（整批一次） | 否 | 当前分支其他决定 | 无 |
| 整批决定全部同值 | 不变 | 不变 | 不变 | 否 | 原决定修订 | 新决定行、任务、账本均不创建 |
| 批量中至少一项替换 | +1（整批一次） | +1（整批一次） | +1（整批一次） | `evidence_decision_changed`，整批只建一个 | 方向、问题/答案、补充、未变事实的准确决定修订 | 被替换决定、旧候选文案；`conflicted` 事实不得 accepted |

每个写入口先按 `draftRevision + branchRevision` 做精确乐观校验，再在同一事务内锁定当前分支。`decisionRevision` 是每个事实决定聚合的单调业务修订，不是决定表主键；批量替换必须复制未变事实当前决定映射，不得查询“最新决定”猜测分支采用版本。

### 1.3 规范 JSON 冻结格式

`answerIdentityJson` 使用 UTF-8、NFC、无多余空格，且恰好五个键，顺序固定为：

```json
{
  "questionId": "9",
  "questionHash": "9f31dd08e36fca5ad8bb8848ef76e958f8f96f57bf8bd5ea64aa48de56ad2562",
  "selectedCodes": ["option_01", "option_03"],
  "customSelected": true,
  "customText": "咖啡 élite"
}
```

`answerContextJson` 是供 P3 使用的独立语义快照，键顺序固定为 `questionText`、`targetSlotCode`、`selectedOptions`、`customText`：

```json
{
  "questionText": "这次视频最想传达什么？",
  "targetSlotCode": "coreMessage",
  "selectedOptions": [
    {
      "code": "option_01",
      "normalizedValue": "强调产品的核心价值",
      "slotContributions": ["coreMessage"]
    }
  ],
  "customText": "咖啡 élite"
}
```

内部 `selectedCodes` 按 Unicode code point 排序去重且永不包含保留码 `custom`；HTTP `selectedOptionCodes` 只在边界 DTO 存在，映射后不得写入内部 JSON/DTO/DDL。`questionId` 必须是无正号、无前导零的正十进制字符串，`questionHash` 从不可变 `av_script_question.question_hash` 读取；`customSelected=false` 时 `customText` 必须写 JSON `null`。`answerContextJson.selectedOptions` 只由服务端按 identity 中的代码从不可变问题版本解析，按 `code` 排序；每个 `slotContributions` 排序去重；`questionText`、`normalizedValue` 和有效 `customText` 做 NFC、首尾裁剪、内部空白折叠。客户端最多提交 500 个 Unicode code point 的自定义答案；服务端从不可变问题记录加载 `questionId/questionHash/questionNo/questionText/targetSlotCode/optionsJson`，不得信任客户端回传。`answerHash = SHA-256(answerIdentityJson UTF-8 bytes)`；上述 JSON 的黄金 SHA-256 固定为 `e1f99e5ac2f55989e059a4caccee90da1f102484bcd1e3b45bc638ea9d24a9b3`。改变 `questionNo/questionText/targetSlotCode/normalizedValue/slotContributions` 而保持五项 identity 不变时，金丝雀必须证明 hash 不变；旧内部键 `questionNo/questionVersionHash/selectedOptionCodes` 在 identity 中零命中。

`canonicalSupplementJson` 键顺序固定为：

```json
{
  "schemaVersion": "questionnaire-supplement-1",
  "subject": "...",
  "audience": "...",
  "coreMessages": ["..."],
  "targetDurationSeconds": 60,
  "callToAction": "...",
  "mustKeepFacts": ["..."],
  "prohibitedContents": ["..."],
  "toneStyles": [
    {"code": "authoritative", "customText": null},
    {"code": "custom", "customText": "..."}
  ],
  "otherNotes": "..."
}
```

`coreMessages`、`mustKeepFacts`、`prohibitedContents` 三个字符串数组逐项规范化后按值的 Unicode code point 排序去重。`toneStyles` 精确为最多 5 个 `{code,customText}` 对象：`code` 去重并按 code point 排序；普通项 `code != "custom"`，规范化后的 `code` 为 1–100 个 Unicode code point 且 `customText=null`；自定义项 `code="custom"` 最多一项，`customText` 经 NFC、首尾裁剪、内部空白折叠后为 1–200 个 Unicode code point。不得把 `toneStyles` 简化为字符串数组。空可选字符串写 `null`，空数组写 `[]`；九字段规范文本（含每个 tone `code` 与有效 `customText`）合计最多 16,000 Unicode code point。`supplementHash = SHA-256(canonicalSupplementJson UTF-8 bytes)`。`QuestionnaireStableContractTest` 必须对两个完整 JSON 金丝雀做字节级断言，且补充金丝雀同时包含普通 tone 和唯一 custom tone，不能只断言 hash 长度。

## 2. F0/F1/F2/F3 切片和三人并行边界

| 门禁 | P2 可做 | P2 禁止 | 退出证据 |
|---|---|---|---|
| F0 | 固定答案规范化、哈希、完整性策略、JSON Schema、TypeScript 类型、`mock/aivideo-studio.ts`、局部组件和固定 fixture | 执行 `06`、访问 P0-C/P1 运行时、真实模型/搜索、编辑六个 P0-C 共享文件 | P0-A 证据可追溯；P0-B/P0-C 稳定契约冻结 |
| F1 | 在完整 F1 上 rebase；实现 P2 自有 Entity/Mapper/Service、分支/修订逻辑；接收六个共享文件所有权；P1 仅用可删除替身 | 执行 `06`、真实知识联调、生产/真实 IT 保留永久 P1 替身 | `p0c-f1-handoff.json` 严格通过，`fullF1Ready=true`，F1 是当前候选祖先 |
| F2 | 在完整 F2 上 rebase；删除生产源码和真实 IT 的 P1 替身；执行 `01→02→03→04→04a→05→06` 并复跑 `06`；接真实知识、收费任务、模型和检索 | 读取 P1 Mapper/表/Entity/platform BO/VO/infra provider；在错误 `f2Head` 联调 | `p1-f2-handoff.json` 严格通过，`fullF2Ready=true`，稳定 Service/DTO 数组完全一致且 migrationChain 已包含 addendum `04a` |
| F3 | 全量问卷、证据、用户端和本机 IT 验收；冻结 P2 Service/DTO 与证据哈希；向 P3 移交 | P3 直接访问 P2 Mapper/表；writer 自签 review；覆盖不同 HEAD 的旧 handoff | 独立 reviewer PASS；`p2-f3-handoff.json` 幂等回读；迁移链与六类 fresh 证据全部通过 |

三名开发持续有工作：开发 A 推进 P0-B/P0-C 和随后 P3 独立切片；开发 B 推进 P1；开发 C 是 P2 writer。F0/F1 期间开发 C 只做 P2 独立切片；F1 后开始 P2 自有持久层；F2 后接真实 P1/P0-C；开发 B 作为 P2 独立 reviewer，不与开发 C 共写。共享文件在任一时刻只有一名 owner。

P2 用户端集成 owner 在 F1 移交后独占：

- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java`
- `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts`
- `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts`

`app.tsx`、工作台根 `index.tsx`、`StudioTopbar.tsx`、`model.ts`、`DemandStep.tsx` 和 `mock/aivideo-studio.ts` 也由 P2 集成 owner 串行合入；P3 在 F3 前只能交付自身目录中通过受控 props 工作的局部组件。

## 3. 冻结的跨阶段契约

### 3.1 P0-C：真实任务、attempt、lease 与结算边界

P2 只使用以下 P0-C 类型，名称和 `DTO` 后缀不得缩写：

- `IAiTaskService`
- `IAiTaskExecutionDispatcher`
- `IAiTaskAttemptService`
- `IQuotaBillingService`
- `IDirectionCatalogService`
- `IAiTaskExecutionHandler`（P0-C 内部任务 SPI，由 infra Handler 实现）
- `org.dromara.aivideo.provider.ModelProvider`（全局唯一模型外调端口）
- `org.dromara.aivideo.client.WebSearchClient`（全局唯一搜索外调客户端）
- `TaskInitiatorDTO`、`ChargeableTaskDTO`、`TaskRevisionSnapshotDTO`、`TaskCreationResultDTO`
- `TaskResultReferenceDTO`、`AiTaskExecutionLeaseDTO`、`AiTaskAttemptHandleDTO`、`ProviderUsageDTO`
- `DirectionCatalogSnapshotDTO`

`ChargeableTaskDTO` 的操作键是冻结契约，花括号表示对应十进制值；禁止把 `branchId` 或 `generationContextRevision` 混入三键：

| 任务 | operation slot | operation family | operation group |
|---|---|---|---|
| 出题 | `question:{draftId}:{branchRevision}:{nextQuestionOrdinal}` | `questionnaire:{draftId}` | `questionnaire:{draftId}:{branchRevision}` |
| 证据检索 | `evidence:{draftId}:{branchRevision}` | `evidence:{draftId}` | `evidence:{draftId}:{branchRevision}` |

F1 handoff 前存在两个**上游 P0-C 稳定合同补丁**，必须由 P0-C 契约 owner 先串行同步公共契约、master 和 P0-C 计划并实现/验收；P2 只消费，不得自造同名 Service、直读 `av_ai_task` 或直接调用 `AiTaskGroupMemberMapper`：

```java
void IAiTaskService.requireGenerationContextWritable(
    Long draftId, Long branchRevision);

void IAiTaskService.inheritQuestionnaireTaskGroupMembers(
    Long draftId,
    Long sourceBranchRevision,
    Long targetBranchRevision,
    List<Long> retainedRootTaskIds,
    TaskInitiatorDTO initiator);
```

`requireGenerationContextWritable` 必须是 MANDATORY 事务调用；P0-C 按当前 tenant 和准确组键 `script:{draftId}:{branchRevision}` 查找 `SCRIPT_GENERATE`、`SCRIPT_OPTIMIZE` 中 `pending/queued/running` root。存在任一项即 fail-closed 抛 `46123`，data 恰含脱敏十进制字符串 `rootTaskId/taskType/status` 三字段。方向、答案、补充、事实决定均在固定 `draft → current branch` 行锁后、第一次 INSERT/UPDATE/审计/新任务前调用；P3 创建脚本也使用同一方法。拒绝路径必须是业务零写、审计零写、新任务零创建。P2 不按 task 表状态自行判断，也不扩大 `46123` 的其他语义。

`inheritQuestionnaireTaskGroupMembers` 也是 MANDATORY 事务调用，并由 P0-C 内部唯一构造 `questionnaire:{draftId}` family、`questionnaire:{draftId}:{sourceBranchRevision}` source group 和 target group，防止 confused deputy。固定要求 `targetBranchRevision=sourceBranchRevision+1`；rootIds 为 1–5 个、升序唯一；`initiator` 必须是当前 workspace 的同一 `app_user`。P0-C 锁定 source group，确认每个 root 是当前 tenant、`QUESTION_GENERATE`、`script_draft/draftId`、source group 的既有 member。target 为空时一次批量写 `member_role=inherited`；target 与精确 payload 完全一致才幂等回读；部分集合、超集、冲突 root 或既有 `origin` 全部 fail-closed。继承不创建 root/execution、usage operation、额度锁或账本，费用只能按 `usageOperationId` 去重，不得用 `SUM(DISTINCT amount)`。P0-C owner 同步补齐 append-only `av_ai_task_group_member` DDL（tenant/group/root 唯一、`origin|inherited` CHECK、`source_task_group_key`、typed creator、同 tenant root FK），并给 `av_ai_task` 增加 `idx_av_ai_task_active_group(tenant_id,task_group_key,task_role,status,task_type,id)`；P2 迁移不得代建这两项。

继承矩阵固定为：改答第 N 题只传新分支当前 `branch_question` 中题号 `1..N` 的精确 rootIds；补充替换/移除和事实决定替换传全部当前题 rootIds；方向替换不调用继承方法（等价零成员）；后续新生成问题由 P0-C 创建时写 `origin`。IT 必须逐分支断言后缀 root 排除、`origin/inherited`、`usageOperationId` 唯一、`previouslySettled/currentBranchSettled` 均准确且继承零重复收费。

若原 `p0c-f1-handoff.json` 已存在，owner 不得覆盖；补丁必须 CreateNew `p0c-f1-contract-addendum.json`，并证明 `originalF1Head` 是 `amendmentHead` 祖先。P2 的第一次 F1 rebase 目标改为经过严格审核的 `amendmentHead`；没有 addendum 或祖先/字段/hash/独立 review/evidence 漂移立即停止。锁序固定：P2 写为 `draft → current branch → requireGenerationContextWritable → P2 rows/task-group inherit → audit`；P3 创建为 `draft → current branch → slot → quota → task/origin member → freeze → enqueue`，不得倒序。

addendum 顶层字段顺序精确为 `originalF1Head,amendmentHead,originalF1HandoffSha256,requiredMethods,schemaAddendum,owner,reviewer,reviewStatus,reviewedHead,reviewCompletedAtUtc,evidence,capturedAtUtc`。`requiredMethods` 恰为 owner 源码完整字符串 `void requireGenerationContextWritable(Long draftId, Long branchRevision);`、`void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);`，返回类型、参数名、逗号后空格都属于 canonical digest。`schemaAddendum` 字段顺序精确为 `forwardMigration,taskGroupMemberTable,activeTaskIndex,originValues,creatorTypes,globalLockOrder,scriptGroupKey,inheritanceScope,forbiddenCopies`：值依次为 `20260728_04a_p0c_task_group_guard.sql`、`av_ai_task_group_member`、`idx_av_ai_task_active_group`、`[origin,inherited]`、`[app_user,sys_user]`、`[draft,current_branch,operation_slot,quota_account,task_or_group_member]`、`script:{draftId}:{branchRevision}`、`membership_only`、`[task,usage,ledger,operation_slot]`。继承只复制 membership，绝不复制 task、usage、ledger 或 operation slot。

`originalF1HandoffSha256` 必须现场重算不可变原 handoff；owner/reviewer trim 后大小写不敏感不同；`reviewStatus=PASS`、`reviewedHead=amendmentHead`；`reviewCompletedAtUtc/capturedAtUtc` 都显式 UTC且 captured 不早于 review。`evidence` 是精确顺序的三项数组，每项字段顺序精确为 `kind,path,sha256`：`source-signatures → git-metadata:p0c-f1-addendum/source-signatures.manifest.json`、`migration-04a → git-metadata:p0c-f1-addendum/migration-04a.manifest.json`、`independent-review → git-metadata:p0c-f1-contract-addendum-review.json`；三个小写 SHA-256 均现场重算。独立 review JSON 字段顺序精确为 `owner,reviewer,reviewStatus,reviewedHead,originalF1Head,originalF1HandoffSha256,requiredMethodsSha256,schemaAddendumSha256,reviewCompletedAtUtc`，两个 digest 分别是对应 canonical compact JSON UTF-8 的 SHA-256。P2 writer 只读且 fail-closed，不得代写 addendum review/evidence。原 handoff hash 只绑定不可变原文件，绝不能拿 addendum 重生成或替换它。F2 若仍基于原始 F1，第二次真实 rebase 会重放 amendment 补丁而不保证原 `amendmentHead` 仍为祖先，因此完成记录必须同时保存 `originalF1Head/amendmentHead`，并由稳定契约测试和 exact signature/schema 扫描证明两个方法/DDL 仍存在；不得把这解释为可跳过第一次 amendment rebase。

固定调用面：

```java
TaskCreationResultDTO IAiTaskService.createChargeableTask(ChargeableTaskDTO request);
void IAiTaskExecutionDispatcher.enqueue(Long rootTaskId, Long executionTaskId);
AiTaskAttemptHandleDTO IAiTaskAttemptService.startAttempt(
    Long rootTaskId, Long executionTaskId, String leaseOwner,
    String callPurpose, String provider, String model, String inputHash);
void IAiTaskAttemptService.completeAttempt(
    Long attemptId, ProviderUsageDTO usage, String outputHash);
void IAiTaskAttemptService.failAttempt(
    Long attemptId, ProviderUsageDTO usage, String failureCode, String failureMessage);
void IAiTaskService.markSuccess(
    AiTaskExecutionLeaseDTO lease, TaskResultReferenceDTO result);
AiTaskExecutionLeaseDTO IAiTaskService.renewLease(
    AiTaskExecutionLeaseDTO lease, Instant newLeaseExpiresAt);
```

P2 的 `startQuestionGeneration` 和 `startEvidenceRetrieval` 都是外层 `@Transactional(rollbackFor = Exception.class)` Service 方法，固定顺序如下；`reused=true` 立即返回：

```java
TaskCreationResultDTO task = aiTaskService.createChargeableTask(request);
if (task.reused()) {
    return task;
}
questionnaireExecutionInputService.freeze(
    task.rootTaskId(), task.executionTaskId(), frozenInput);
aiTaskExecutionDispatcher.enqueue(task.rootTaskId(), task.executionTaskId());
return task;
```

`enqueue` 不执行模型、不创建任务、不结算额度。冻结或入队失败时，任务、额度锁、冻结输入和 `queued` 状态全部回滚。Handler 只按 `AiTaskExecutionLeaseDTO` 的根/执行编号加载不可变输入，不读取当前登录、HTTP 请求、当前分支别名或进程缓存。

每次真实 `ModelProvider` 或 `WebSearchClient` 调用前紧邻调用 `startAttempt`；严格解析、Schema 和业务校验全部通过后才 `completeAttempt`，调用异常或确定性输出无效只 `failAttempt`。同一 attempt 只终结一次，`providerCallSequence` 只由 P0-C 在锁内分配。问题调用目的只允许 `initial/repair`，检索使用 `search`；第四次真实调用由 P0-C 以 `AI_TASK_PROVIDER_ATTEMPTS_EXHAUSTED` 拒绝。

每次 provider 超时必须早于当前 `leaseExpiresAt` 至少 10 秒。剩余租约不足以覆盖“provider timeout + 10 秒”时，Handler 必须先调用 `renewLease`，并把返回的新 `AiTaskExecutionLeaseDTO` 同时用于后续 `startAttempt`、结果写入和 `markSuccess`；禁止继续使用旧 lease 或只改本地过期时间。

真实调用成功后进入独立结果事务。事务第一步锁定当前草稿和分支，逐项比较冻结与当前的 `branchRevision`、`generationContextRevision`、`generationInputHash`。任一不一致时，在首次业务 INSERT/UPDATE 和 `markSuccess` 前抛出：

```java
throw new AiTaskNonRetryableException(
    "STALE_BRANCH_RESULT", "需求上下文已更新，本次结果已安全丢弃");
```

三摘要一致时，必须在同一结果事务中先写业务结果，再显式调用 `aiTaskService.markSuccess(currentLease, TaskResultReferenceDTO.of("question", questionId))` 或 `aiTaskService.markSuccess(currentLease, TaskResultReferenceDTO.of("evidence_batch", batchId))`；P0-C 在该调用所属事务中完成 settle。P2 不得只构造 result reference 而漏调 `markSuccess`，也不得在业务写入前标记成功。

`STALE_BRANCH_RESULT` 路径中 provider attempt 保持 `success`，问题/证据业务表零写入，不调用 `markSuccess`，不把 attempt 改为失败，不重新调用 provider；P0-C scanner 负责把 execution/root 收敛为 `failed/STALE_BRANCH_RESULT` 并 `release` 额度，P2 不直接调用 `settle` 或 `release`。

### 3.2 P1：F2 后唯一允许的消费面

P2 生产源码和真实 IT 只能调用：

```java
public interface IKnowledgeRoutingService {
    KnowledgeRouteResultDTO route(KnowledgeRouteRequestDTO request);
}

public interface IKnowledgeSnapshotService {
    KnowledgeSnapshotDTO create(KnowledgeSnapshotRequestDTO request);
    KnowledgeSnapshotDTO getByRootTaskId(Long rootTaskId);
}
```

且只消费五个顶层 DTO：

1. `KnowledgeRouteRequestDTO`
2. `KnowledgeRouteResultDTO`
3. `KnowledgePlanDTO`
4. `KnowledgeSnapshotRequestDTO`
5. `KnowledgeSnapshotDTO`

F2 handoff 顶层字段/顺序精确为 `fullF2Ready,f1Head,originalF1Head,f1AmendmentHead,f2Head,owner,reviewer,reviewStatus,reviewCompletedAtUtc,p1AcceptanceWindowStart,p1AcceptanceWindowEnd,originalF1HandoffSha256,f1AddendumSha256,migrationChain,migrationRepeat05,stableServices,stableDtos,stableDtoComponentRegistry,stableDtoSourceSha256,downstreamConsumers,revisionMappingContractOwner,evidence,capturedAtUtc`。三处消费者 parser 都必须证明 `f1Head=f1AmendmentHead=addendum.amendmentHead`、`originalF1Head=addendum.originalF1Head=原 handoff f1Head`，并现场重算原 handoff/addendum SHA。`stableDtoComponentRegistry` 与 `stableDtoSourceSha256` 外层键都精确按上述五 DTO 顺序；逐 DTO 对照实际 record header、canonical compact component array 和源码 bytes，不能只信 handoff 自报。

五 DTO 类型和顺序以 F2 live registry 为准：`KnowledgeRouteRequestDTO` 使用 `Long directionCatalogVersionId`、`String industryCode`、`String purposeCode`、`Integer targetDurationSeconds`、`List<String> tagCodes`；快照请求/结果使用 `Long rootTaskId`、`Long promptVersionId`、`Long generationContextRevision`、`String generationInputHash`、路由结果和接受事实快照。P2 不创建同义类型，不注入或 mock P1 其他 Service，不访问 P1 Mapper、表、Entity、platform BO/VO 或 infra provider。F2 前替身只能位于隔离单元测试或前端 Mock；F2 后生产源码和真实 IT 零替身。

### 3.3 P2：向 P3 冻结的两个 Service 和六个 DTO

稳定文件精确位于：

```text
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionnaireContextService.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IEvidenceReviewService.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireContextDTO.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireAnswerRevisionDTO.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireSupplementRevisionDTO.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/EvidenceReviewContextDTO.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/AcceptedEvidenceFactDTO.java
ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/EvidenceDecisionRevisionDTO.java
```

接口签名固定为：

```java
public interface IQuestionnaireContextService {
    QuestionnaireContextDTO getCurrentContext(Long draftId, Long branchId);
    QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId, Long branchId);
}

public interface IEvidenceReviewService {
    EvidenceReviewContextDTO getAcceptedContext(Long draftId, Long branchId);
}
```

六个 DTO 的 record 组件、类型和顺序冻结如下，不得增删、重排或改名：

```java
public record QuestionnaireAnswerRevisionDTO(
    Long questionId,
    Integer questionNo,
    String targetSlotCode,
    String questionHash,
    Long answerRevisionId,
    Long answerRevision,
    String answerHash,
    String answerIdentityJson,
    String answerContextJson
) {
}

public record QuestionnaireSupplementRevisionDTO(
    Long supplementRevisionId,
    Long supplementRevision,
    String supplementHash,
    String canonicalSupplementJson
) {
}

public record QuestionnaireContextDTO(
    Long draftId,
    Long currentBranchId,
    Long branchRevision,
    Long generationContextRevision,
    String questionnaireHash,
    String knowledgeContextHash,
    String generationInputHash,
    List<QuestionnaireAnswerRevisionDTO> answerRevisions,
    QuestionnaireSupplementRevisionDTO supplementRevision,
    boolean contextReady
) {
}

public record AcceptedEvidenceFactDTO(
    Long factId,
    Long decisionRevision,
    String factHash,
    String factText,
    String sourceTitle,
    String evidenceRef
) {
}

public record EvidenceDecisionRevisionDTO(
    Long factId,
    Long decisionRevision
) {
}

public record EvidenceReviewContextDTO(
    Long draftId,
    Long branchId,
    List<AcceptedEvidenceFactDTO> acceptedFacts,
    List<EvidenceDecisionRevisionDTO> decisionRevisions
) {
}
```

`getCurrentContext` 与 `getAcceptedContext` 都先校验当前工作区与草稿归属，再分别在一个不可拆分的只读事务快照中读取。新增的 `lockCurrentContextForGeneration` 必须标注 `@Transactional(propagation = Propagation.MANDATORY)` 且不得 `readOnly=true`；它按 tenant/owner scope 依次 `SELECT ... FOR UPDATE` 锁 draft→`draft.current_branch`，校验传入 `branchId == currentBranchId`，随后在调用方同一外层写事务、同一锁定快照内组装并返回 `QuestionnaireContextDTO`。P3 生成/优化必须先调用该方法，并只用返回值重检请求/来源的 `branchRevision/questionnaireHash/knowledgeContextHash`，一致后才能取得 operation slot/quota；旧的外层 readOnly `getCurrentContext` 不能冒充锁入口。P2 所有上下文写路径继续遵守相同 draft→current branch 锁序。

问答按 `questionNo`、`answerRevision` 稳定排序；`questionnaireHash` 只由按该顺序的答案 identity/revision/hash 引用、补充 revision/hash 与方向 revision/hash 规范计算，`knowledgeContextHash` 来自 P1 不可变知识快照引用，`generationInputHash` 再按冻结 schema 合成，三者都为小写 SHA-256 且不能用正文日志重算。`QuestionnaireAnswerRevisionDTO.answerIdentityJson` 是 answerHash 的唯一输入，`answerContextJson` 是 P3 语义输入，两者都来自同一不可变答案修订且不得互相重算。接受事实和决定修订都按 `factId` 排序、返回不可变 List，且两组 `factId` 集合必须完全一致。`AcceptedEvidenceFactDTO.decisionRevision` 必须与同 `factId` 的 `EvidenceDecisionRevisionDTO.decisionRevision` 相同；这里的 `decisionRevision` 是事实决定的业务聚合修订号，不是 `av_evidence_fact_decision` 行主键。P3 只消费这两个 Service 和六个 DTO，不访问 P2 Mapper、表、Entity 或 user VO。

### 3.4 用户 actor、创作作用域与安全审计

用户端所有写 Controller 唯一允许的主体解析调用是 P0-B `AppAuthorizationActorResolver.requireActor()`。Controller 在 BO→core command 映射前取得 `AppActorContext actor`，显式传给 Service；Controller 禁止运营端 `@Log`，禁止 `LoginHelper`、`AppLoginHelper`、`StpUtil` 或第二个 `requireActor()`。core Service 也禁止读取任何登录上下文。

同步写入的 `tenant_id/owner_type/owner_id` 只取本次 `WorkspaceContextDTO`，`created_by_user_id` 只取已交叉验证的 `AppActorContext.appUser.actorId`。异步结果写入只取 `av_questionnaire_execution_input` 冻结的 workspace scope 与 `TaskInitiatorDTO`；P2 冻结任务只接受 `actorType=app_user`，不得以同数值 `sys_user` 代替。六个 BO 一律禁止 `tenantId/ownerType/ownerId/createdByUserId/billingSubjectId/branchId/currentBranchId/generationContextRevision/rootTaskId/executionTaskId`。

每个采用业务写入在**同一事务、所有业务 INSERT/UPDATE 之后、提交之前**调用一次：

```java
appSecurityAuditService.append(new AppSecurityAuditDTO(
    resourceType,
    resourceId,
    action,
    actor.actorType(),
    actor.actorId(),
    beforeDigest,
    afterDigest,
    reason));
```

`AppSecurityAuditDTO` 必须恰好八字段并保持 `resourceType,resourceId,action,actorType,actorId,beforeDigest,afterDigest,reason` 顺序；摘要来自固定键序规范 JSON，审计只存脱敏摘要。审计 append 失败必须回滚业务、修订指针、分支成员和任务组继承；写锁守卫拒绝发生在首个业务写和 audit 之前，因此拒绝路径审计也是零写。

| 场景 | `resourceType` | `action` | actor 来源 | before/after 摘要 |
|---|---|---|---|---|
| 首次/替换方向 | `script_direction_revision` | `DIRECTION_SAVED` / `DIRECTION_BRANCH_FORKED` | Controller app actor | 旧/新方向 identity 与三个修订 |
| 首次开始/解除阻塞继续 | `script_draft` | `QUESTIONNAIRE_STARTED` | Controller app actor | 当前修订、题号、operation slot；复用不追加审计 |
| 题生成结果采用 | `script_question` | `QUESTION_GENERATION_RESULT_ADOPTED` | 冻结 app initiator | 冻结三摘要、新 questionId/questionHash |
| 首答/改答分支 | `script_answer_revision` | `QUESTION_ANSWER_SAVED` / `QUESTION_BRANCH_FORKED` | Controller app actor | 旧/新 answerHash 与分支修订 |
| 首次/替换/移除补充 | `script_supplement_revision` | `SUPPLEMENT_SAVED` / `SUPPLEMENT_BRANCH_FORKED` | Controller app actor | 旧/新 supplementHash 与分支修订 |
| 检索结果有/无事实 | `evidence_batch` | `EVIDENCE_RETRIEVAL_BATCH_SAVED` / `EVIDENCE_NO_RESULTS_SAVED` | 冻结 app initiator | requestHash、status、source/fact counts |
| 首次/替换事实决定 | `evidence_fact_decision` | `EVIDENCE_FACT_DECISION_SAVED` / `EVIDENCE_DECISION_BRANCH_FORKED` | Controller app actor | 排序的 factId→decisionRevision 映射 |

`PlatformQuestionnaireIsolationTest` 与各 Service IT 必须用 `AppActorContext.appUser(1001L)` 和 `AppActorContext.sysUser(1001L)` 证明同号不同域不可混用；扫描生产 P2 对默认 `LoginHelper|StpUtil|@Log` 零命中。每种 action 至少一例让 `IAppSecurityAuditService.append` 抛异常，并断言业务表、指针、任务组成员、审计表均零新增。

### 3.5 错误码、端点、BO/VO 与内部 Service 签名

错误码唯一 owner 文件是 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`。P2 只新增下列九项：

| code | 枚举 | 精确语义 |
|---:|---|---|
| 46103 | `DRAFT_REVISION_CONFLICT` | 草稿/分支/事实决定预期修订冲突 |
| 46104 | `DIRECTION_INCOMPLETE` | 行业、用途或时长不完整 |
| 46105 | `QUESTION_NOT_ACTIVE` | 问题不是当前分支当前题 |
| 46106 | `QUESTION_OPTION_INVALID` | 普通选项未知、重复规则非法或保留码混入 |
| 46107 | `CUSTOM_TEXT_REQUIRED` | 已选 custom 但规范文本为空/越界 |
| 46108 | `QUESTION_LIMIT_REACHED` | 请求生成第六题或越界题号 |
| 46109 | `QUESTIONNAIRE_INCOMPLETE` | 三题前结束或五题后必填仍缺失 |
| 46112 | `MODEL_INVALID_RESPONSE` | 模型 JSON/Schema/业务结构无效且 repair 失败 |
| 46113 | `MODEL_CONTENT_REFUSED` | provider 明确内容拒绝 |

只复用上游 `46114 QUOTA_INSUFFICIENT`、`46115 TARIFF_VERSION_CHANGED`、`46116 IDEMPOTENCY_KEY_CONFLICT`、`46122 DIRECTION_CATALOG_CHANGED`、`46123 GENERATION_CONTEXT_LOCKED`、`46124 BILLING_SUBJECT_FORBIDDEN`、`46126 WORKSPACE_NOT_AVAILABLE`、`46127 WORKSPACE_ACTION_FORBIDDEN`，不得重复声明或改义。运行中脚本写锁和同操作槽冲突都复用 `46123`，但 data 必须分别给出脱敏 task/group 摘要。SSRF 使用 infra 内部 `UnsafeEvidenceUriException` + failure code `UNSAFE_EVIDENCE_URI`；补充长度走 Bean Validation；冲突事实接受走领域拒绝，三者都不得借用 billing/workspace/idempotency 码。

端点由 `studio/api.ts` 唯一拼接，后端固定为：

```text
GET  /api/studio/direction-options
PUT  /api/studio/script-drafts/{draftId}/direction
POST /api/studio/script-drafts/{draftId}/questionnaire/start
POST /api/studio/script-drafts/{draftId}/questionnaire/turns
GET  /api/studio/script-drafts/{draftId}/questionnaire
PUT  /api/studio/script-drafts/{draftId}/questionnaire/supplements
POST /api/studio/script-drafts/{draftId}/evidence-searches
GET  /api/studio/script-drafts/{draftId}/evidence
PUT  /api/studio/script-drafts/{draftId}/evidence-decisions
```

user 端六个 BO 字段和校验冻结如下；表中未列字段一律拒绝，Controller 只映射成 core DTO：

| BO | 精确字段 | 校验 |
|---|---|---|
| `SaveDirectionBo` | `draftRevision,branchRevision,expectedCatalogVersion,industryCode,industryCustomText,purposeCode,purposeCustomText,targetDurationSeconds` | 三个 Long 均为规范十进制 string；code 1–64；仅 code=custom 时文本 1–100，否则必须 null；时长白名单；三个 server-only 子版本出现即拒绝 |
| `StartQuestionnaireBo` | `draftRevision,branchRevision,idempotencyKey,expectedTariffVersion` | key 1–128；价格版本正数；客户端不得传题号 |
| `SubmitQuestionTurnBo` | `questionId,selectedOptionCodes,customSelected,customText,draftRevision,branchRevision,idempotencyKey,expectedTariffVersion` | 普通 code 0–6 且格式 `option_0[1-6]`；custom 不进数组；有效 custom 1–500；只有需下一收费题时 key/价格必填 |
| `SaveSupplementBo` | `draftRevision,branchRevision,subject,audience,coreMessages,targetDurationSeconds,callToAction,mustKeepFacts,prohibitedContents,toneStyles,otherNotes` | 完整数值矩阵见 3.6；免费请求不得带幂等键/价格 |
| `CreateEvidenceSearchBo` | `draftRevision,branchRevision,queryIntent,idempotencyKey,expectedTariffVersion` | queryIntent 可空、有效时 1–500；key 1–128；价格版本正数 |
| `SaveEvidenceDecisionsBo` | `draftRevision,branchRevision,decisions`，元素恰为 `factId,decision,expectedDecisionRevision` | 1–100 项、factId 唯一；decision 仅 accepted/rejected；revision 正数 |

core 内部 command 与 Service 方法签名一次性冻结，不能让 core import user BO/VO：

```java
public record SaveDirectionCommandDTO(
    Long draftRevision, Long branchRevision,
    Long expectedCatalogVersion,
    String industryCode, String industryCustomText,
    String purposeCode, String purposeCustomText, Integer targetDurationSeconds) {}

public record StartQuestionnaireCommandDTO(
    Long draftRevision, Long branchRevision,
    String idempotencyKey, Long expectedTariffVersion) {}

public record SubmitAnswerCommandDTO(
    Long questionId, List<String> selectedOptionCodes,
    boolean customSelected, String customText,
    Long draftRevision, Long branchRevision,
    String idempotencyKey, Long expectedTariffVersion) {}

public record SaveSupplementCommandDTO(
    Long draftRevision, Long branchRevision,
    String subject, List<String> audience, List<String> coreMessages,
    Integer targetDurationSeconds, String callToAction,
    List<String> mustKeepFacts, List<String> prohibitedContents,
    List<ToneStyleDTO> toneStyles, String otherNotes) {}

public record StartEvidenceRetrievalCommandDTO(
    Long draftRevision, Long branchRevision, String queryIntent,
    String idempotencyKey, Long expectedTariffVersion) {}

public record SaveEvidenceDecisionsCommandDTO(
    Long draftRevision, Long branchRevision,
    List<EvidenceDecisionCommandDTO> decisions) {}

DirectionRevisionResultDTO IDirectionRevisionService.saveDirection(
    Long draftId, SaveDirectionCommandDTO command, AppActorContext actor);
QuestionnaireAdvanceDTO IQuestionTurnService.start(
    Long draftId, StartQuestionnaireCommandDTO command, AppActorContext actor);
QuestionTurnResultDTO IQuestionTurnService.submitAnswer(
    Long draftId, SubmitAnswerCommandDTO command, AppActorContext actor);
SavedTurnDTO IQuestionTurnWriteService.saveAnswerAndAdvance(
    Long draftId, SubmitAnswerCommandDTO command, AppActorContext actor);
NormalizedAnswerDTO IAnswerNormalizationService.normalize(
    ScriptQuestion question, SubmitAnswerCommandDTO command);
BranchForkResultDTO IQuestionnaireBranchService.forkForChangedAnswer(
    Long draftId, Long sourceBranchId, Integer throughQuestionNo,
    Long newAnswerRevisionId, AppActorContext actor);
CompletenessResultDTO IQuestionnaireCompletenessService.evaluate(
    Long draftId, Long branchId);
SupplementRevisionResultDTO ISupplementRevisionService.save(
    Long draftId, SaveSupplementCommandDTO command, AppActorContext actor);
TaskCreationResultDTO IQuestionGenerationService.start(
    Long draftId, Integer nextQuestionOrdinal,
    StartQuestionnaireCommandDTO command, AppActorContext actor);
void IQuestionGenerationResultService.adopt(
    AiTaskExecutionLeaseDTO lease, FrozenQuestionInputDTO input,
    GeneratedQuestionDTO result);
TaskCreationResultDTO IEvidenceRetrievalService.start(
    Long draftId, StartEvidenceRetrievalCommandDTO command, AppActorContext actor);
void IEvidenceResultService.adopt(
    AiTaskExecutionLeaseDTO lease, FrozenEvidenceInputDTO input,
    EvidenceRetrievalResultDTO result);
EvidenceDecisionResultDTO IEvidenceDecisionService.save(
    Long draftId, SaveEvidenceDecisionsCommandDTO command, AppActorContext actor);
void IQuestionnaireExecutionInputService.freeze(
    Long rootTaskId, Long executionTaskId, FrozenQuestionnaireInputDTO input);
FrozenQuestionnaireInputDTO IQuestionnaireExecutionInputService.require(
    Long rootTaskId, Long executionTaskId);
```

保存方向的 user HTTP/TypeScript 请求只允许 `draftRevision,branchRevision,expectedCatalogVersion,industryCode,industryCustomText,purposeCode,purposeCustomText,targetDurationSeconds`。Service 在持有 draft→current branch 锁后只调用一次 `IDirectionCatalogService.currentPublishedCatalog()`，取得同一个不可变八组件 `DirectionCatalogSnapshotDTO(catalogVersion,contentHash,industryCatalogVersion,purposeCatalogVersion,durationRuleVersion,industries,purposesByIndustry,targetDurations)`：先比较 `expectedCatalogVersion`，再用该快照校验 code/绑定/时长，最后把三个 server-only 子版本派生写入既有 DDL。不得二次读取目录，不得从客户端、缓存散项或三个独立查询拼装；篡改子版本、聚合版本漂移和校验后发布切换的 TOCTOU fixture 都必须在写入前失败。

HTTP VO/TypeScript 中所有 Java `Long`（ID、聚合目录版本、业务修订、任务号）和额度/金额都编码为十进制 `string`；`targetDurationSeconds/questionNo/progress/businessCode` 为 `number`。`industryCatalogVersion/purposeCatalogVersion/durationRuleVersion` 是 server-only 追溯字段，不出现在 user 保存 BO、HTTP request 或 TypeScript request。`nextAction` 是判别联合：`wait_task` 必有 `rootTask`；`show_question` 必有当前唯一 `question`；`show_supplement|review_evidence|generate_script` 两者皆 null；`resolve_quota|reconfirm_tariff` 必有 `blockingDetail`。`BlockingDetail` 恰含 `businessCode,billingSubject,requiredQuota,availableQuota,lockedQuota,previousTariffVersion,currentTariffVersion,currentUnitQuota,draftRevision,branchRevision,generationContextRevision,resumeOperation`，其中 `resumeOperation='generate_next_question'`。

### 3.6 补充数值矩阵与模型输出 Schema

| 字段 | 规范化后约束 |
|---|---|
| `subject` | 条件必填时 1–2000 code points |
| `audience` | 条件必填时 1–8 项，每项 1–100 |
| `coreMessages` | 条件必填时 1–8 项，每项 1–300 |
| `targetDurationSeconds` | 只能为 `30/45/60/90/120` |
| `callToAction` | 条件必填时 1–500 |
| `mustKeepFacts` | 条件必填时 1–10 项，每项 1–500 |
| `prohibitedContents` | 条件必填时 1–10 项，每项 1–300 |
| `toneStyles` | 最多 5 项；普通 code 1–100 且 text=null；唯一 custom text 1–200 |
| `otherNotes` | 可空；有效时 1–2000 |

前三题和第 4/5 题未填满的 `missingSlots` 对应字段条件必填；已有完整问答语义的字段可以 null/空数组。`toneStyles` 为空时按用途目录冻结的默认 tone 生成规范对象数组；不得使用页面硬编码默认。全部九字段按第 1.3 节规范化后的有效文本合计不超过 16,000 code points。

`question-generation-1.schema.json` 根对象和 option 对象都必须 `additionalProperties=false`。根字段恰为 `schemaVersion,targetSlotCode,questionText,options,recommendedOptionIndexes`；option 恰为 `label,normalizedValue,slotContributions`。`questionText` 4–200，options 2–6，label 1–80，normalizedValue 1–300，slotContributions 至少 1 且只含稳定槽位；recommended 最多 2 个、下标唯一且在 options 范围。任何额外字段、重复 recommended、空贡献、保留标签或越界值都必须在 `completeAttempt` 前失败。

## 4. 数据库迁移、RuoYi 文件树和公共文件所有权

### 4.1 `20260728_06_p2_questionnaire.sql` 精确增量

P2 迁移只新增下列 11 张表；主键均为业务侧雪花 `BIGINT`，禁止自增。每张创作表逐列带 `tenant_id/owner_type/owner_id/created_by_user_id/create_time`，并有 owner-scope 索引。`owner_type` 只允许 `personal/organization`。迁移不得创建第二张 `av_script_branch`，不得创建 P0-C 的 task-group 表。

```sql
CREATE TABLE IF NOT EXISTS av_script_direction_revision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  revision_no BIGINT NOT NULL,
  industry_catalog_version BIGINT NOT NULL,
  purpose_catalog_version BIGINT NOT NULL,
  duration_rule_version VARCHAR(64) NOT NULL,
  industry_code VARCHAR(64) NOT NULL,
  industry_custom_text VARCHAR(100) NULL,
  purpose_code VARCHAR(64) NOT NULL,
  purpose_custom_text VARCHAR(100) NULL,
  target_duration_seconds SMALLINT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  supersedes_direction_revision_id BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_direction_scope_id (tenant_id, owner_type, owner_id, draft_id, id),
  UNIQUE KEY uk_direction_revision (tenant_id, owner_type, owner_id, draft_id, revision_no),
  KEY idx_direction_owner (tenant_id, owner_type, owner_id, draft_id, revision_no),
  CONSTRAINT fk_direction_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_direction_supersedes FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, supersedes_direction_revision_id)
    REFERENCES av_script_direction_revision(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT ck_direction_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_direction_duration CHECK (target_duration_seconds IN (30,45,60,90,120)),
  CONSTRAINT ck_direction_revision CHECK (revision_no >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_question (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  source_root_task_id BIGINT NOT NULL,
  source_branch_revision BIGINT NOT NULL,
  question_no TINYINT NOT NULL,
  target_slot_code VARCHAR(32) NOT NULL,
  question_text VARCHAR(200) NOT NULL,
  options_json JSON NOT NULL,
  question_hash CHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_question_scope_id (tenant_id, owner_type, owner_id, draft_id, id),
  UNIQUE KEY uk_question_source_task (tenant_id, owner_type, owner_id, source_root_task_id),
  UNIQUE KEY uk_question_draft_hash (tenant_id, owner_type, owner_id, draft_id, question_hash),
  KEY idx_question_owner (tenant_id, owner_type, owner_id, draft_id, question_no),
  CONSTRAINT fk_question_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_question_root_task FOREIGN KEY (tenant_id, source_root_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT ck_question_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_question_no CHECK (question_no BETWEEN 1 AND 5),
  CONSTRAINT ck_question_text CHECK (CHAR_LENGTH(question_text) BETWEEN 4 AND 200)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_answer_revision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  question_id BIGINT NOT NULL,
  question_hash CHAR(64) NOT NULL,
  revision_no BIGINT NOT NULL,
  supersedes_answer_revision_id BIGINT NULL,
  answer_hash CHAR(64) NOT NULL,
  answer_identity_json JSON NOT NULL,
  answer_context_json JSON NOT NULL,
  selected_codes_json JSON NOT NULL,
  custom_selected TINYINT(1) NOT NULL,
  custom_text VARCHAR(500) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_answer_scope_id (tenant_id, owner_type, owner_id, draft_id, id),
  UNIQUE KEY uk_answer_question_scope_id
    (tenant_id, owner_type, owner_id, draft_id, question_id, id),
  UNIQUE KEY uk_answer_revision
    (tenant_id, owner_type, owner_id, draft_id, question_id, revision_no),
  KEY idx_answer_owner (tenant_id, owner_type, owner_id, draft_id, question_id),
  KEY idx_answer_hash (tenant_id, owner_type, owner_id, draft_id, question_id, answer_hash),
  CONSTRAINT fk_answer_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_answer_question FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, question_id)
    REFERENCES av_script_question(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_answer_supersedes FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, supersedes_answer_revision_id)
    REFERENCES av_script_answer_revision(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT ck_answer_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_answer_revision CHECK (revision_no >= 1),
  CONSTRAINT ck_answer_custom CHECK (
    (custom_selected=0 AND custom_text IS NULL) OR
    (custom_selected=1 AND CHAR_LENGTH(custom_text) BETWEEN 1 AND 500))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_branch_question (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  question_no TINYINT NOT NULL,
  question_id BIGINT NOT NULL,
  answer_revision_id BIGINT NULL,
  source_root_task_id BIGINT NOT NULL,
  member_role VARCHAR(16) NOT NULL,
  copied_from_branch_id BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_branch_question_scope_id (tenant_id, owner_type, owner_id, draft_id, id),
  UNIQUE KEY uk_branch_question_no
    (tenant_id, owner_type, owner_id, draft_id, branch_id, question_no),
  UNIQUE KEY uk_branch_question
    (tenant_id, owner_type, owner_id, draft_id, branch_id, question_id),
  KEY idx_branch_question_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  KEY idx_branch_question_root (tenant_id, branch_id, source_root_task_id),
  CONSTRAINT fk_branch_question_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_branch_question_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_branch_question_question FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, question_id)
    REFERENCES av_script_question(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_branch_question_answer FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, question_id, answer_revision_id)
    REFERENCES av_script_answer_revision
      (tenant_id, owner_type, owner_id, draft_id, question_id, id),
  CONSTRAINT fk_branch_question_root FOREIGN KEY (tenant_id, source_root_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT fk_branch_question_source_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, copied_from_branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT ck_branch_question_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_branch_question_no CHECK (question_no BETWEEN 1 AND 5),
  CONSTRAINT ck_branch_question_role CHECK (member_role IN ('origin','inherited'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_supplement_revision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  revision_no BIGINT NOT NULL,
  supplement_hash CHAR(64) NOT NULL,
  normalized_text_length INT NOT NULL,
  completeness_result_json JSON NOT NULL,
  canonical_supplement_json JSON NOT NULL,
  supersedes_supplement_revision_id BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplement_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_supplement_revision
    (tenant_id, owner_type, owner_id, draft_id, branch_id, revision_no),
  KEY idx_supplement_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  KEY idx_supplement_hash (tenant_id, branch_id, supplement_hash),
  CONSTRAINT fk_supplement_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_supplement_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_supplement_supersedes FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, supersedes_supplement_revision_id)
    REFERENCES av_script_supplement_revision
      (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT ck_supplement_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_supplement_revision CHECK (revision_no >= 1),
  CONSTRAINT ck_supplement_length CHECK (normalized_text_length BETWEEN 0 AND 16000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_evidence_batch (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  source_root_task_id BIGINT NOT NULL,
  query_intent VARCHAR(500) NULL,
  request_hash CHAR(64) NOT NULL,
  source_count INT NOT NULL DEFAULT 0,
  fact_count INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_evidence_batch_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_evidence_batch_task
    (tenant_id, owner_type, owner_id, source_root_task_id),
  KEY idx_evidence_batch_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  CONSTRAINT fk_evidence_batch_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_evidence_batch_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_evidence_batch_root FOREIGN KEY (tenant_id, source_root_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT ck_evidence_batch_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_evidence_batch_status CHECK (status IN ('completed','no_results')),
  CONSTRAINT ck_evidence_batch_counts CHECK (
    (status='no_results' AND source_count=0 AND fact_count=0) OR
    (status='completed' AND source_count>0 AND fact_count>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_evidence_source (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  batch_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  source_root_task_id BIGINT NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  canonical_url_hash CHAR(64) NOT NULL,
  title VARCHAR(500) NOT NULL,
  publisher VARCHAR(255) NULL,
  published_at DATETIME(3) NULL,
  retrieved_at DATETIME(3) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_evidence_source_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_evidence_url_task
    (tenant_id, owner_type, owner_id, source_root_task_id, canonical_url_hash),
  KEY idx_evidence_source_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  CONSTRAINT fk_evidence_source_batch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, batch_id)
    REFERENCES av_evidence_batch(tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT fk_evidence_source_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_evidence_source_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_evidence_source_root FOREIGN KEY (tenant_id, source_root_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT ck_evidence_source_owner CHECK (owner_type IN ('personal','organization'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_evidence_fact (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  source_id BIGINT NOT NULL,
  fact_order SMALLINT NOT NULL,
  fact_text VARCHAR(2000) NOT NULL,
  content_hash CHAR(64) NOT NULL,
  quote_excerpt VARCHAR(1000) NULL,
  locator VARCHAR(500) NULL,
  conflict_group_key VARCHAR(128) NULL,
  initial_status VARCHAR(24) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_evidence_fact_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_source_fact_order
    (tenant_id, owner_type, owner_id, draft_id, branch_id, source_id, fact_order),
  KEY idx_evidence_fact_owner (tenant_id, owner_type, owner_id, draft_id, branch_id, source_id),
  KEY idx_fact_conflict (tenant_id, conflict_group_key),
  CONSTRAINT fk_evidence_fact_source FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, source_id)
    REFERENCES av_evidence_source(tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT ck_evidence_fact_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_evidence_fact_order CHECK (fact_order >= 1),
  CONSTRAINT ck_evidence_fact_status CHECK (initial_status IN ('pending','conflicted'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_evidence_fact_decision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  fact_id BIGINT NOT NULL,
  decision_revision BIGINT NOT NULL,
  supersedes_decision_id BIGINT NULL,
  decision_status VARCHAR(16) NOT NULL,
  replacement_source_id BIGINT NULL,
  generation_context_revision BIGINT NOT NULL,
  decided_by_user_id BIGINT NOT NULL,
  decided_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_fact_decision_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_fact_decision_fact_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, id),
  UNIQUE KEY uk_fact_decision_revision
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, decision_revision),
  KEY idx_fact_decision_owner (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id),
  CONSTRAINT fk_fact_decision_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_fact_decision_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_fact_decision_fact FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id)
    REFERENCES av_evidence_fact(tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT fk_fact_decision_supersedes FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, supersedes_decision_id)
    REFERENCES av_evidence_fact_decision
      (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, id),
  CONSTRAINT fk_fact_decision_replacement_source FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, replacement_source_id)
    REFERENCES av_evidence_source(tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT ck_fact_decision_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_fact_decision_status CHECK (decision_status IN ('accepted','rejected')),
  CONSTRAINT ck_fact_decision_revision CHECK (decision_revision >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_script_branch_evidence_decision (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  fact_id BIGINT NOT NULL,
  decision_revision_id BIGINT NOT NULL,
  member_role VARCHAR(16) NOT NULL,
  copied_from_branch_id BIGINT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_branch_evidence_scope_id
    (tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  UNIQUE KEY uk_branch_fact_decision
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id),
  KEY idx_branch_evidence_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  KEY idx_branch_decision_revision (tenant_id, decision_revision_id),
  CONSTRAINT fk_branch_evidence_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_branch_evidence_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT fk_branch_evidence_fact FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id)
    REFERENCES av_evidence_fact(tenant_id, owner_type, owner_id, draft_id, branch_id, id),
  CONSTRAINT fk_branch_evidence_revision FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, decision_revision_id)
    REFERENCES av_evidence_fact_decision
      (tenant_id, owner_type, owner_id, draft_id, branch_id, fact_id, id),
  CONSTRAINT fk_branch_evidence_source_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, copied_from_branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT ck_branch_evidence_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_branch_evidence_role CHECK (member_role IN ('origin','inherited'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS av_questionnaire_execution_input (
  id BIGINT NOT NULL,
  tenant_id BIGINT NOT NULL,
  owner_type VARCHAR(16) NOT NULL,
  owner_id BIGINT NOT NULL,
  created_by_user_id BIGINT NOT NULL,
  root_task_id BIGINT NOT NULL,
  execution_task_id BIGINT NOT NULL,
  task_type VARCHAR(32) NOT NULL,
  draft_id BIGINT NOT NULL,
  branch_id BIGINT NOT NULL,
  draft_revision BIGINT NOT NULL,
  branch_revision BIGINT NOT NULL,
  generation_context_revision BIGINT NOT NULL,
  generation_input_hash CHAR(64) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  frozen_input_json JSON NOT NULL,
  frozen_input_hash CHAR(64) NOT NULL,
  initiator_type VARCHAR(32) NOT NULL,
  initiator_id BIGINT NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_execution_input_scope_id (tenant_id, owner_type, owner_id, draft_id, id),
  UNIQUE KEY uk_questionnaire_execution_task (tenant_id, execution_task_id),
  UNIQUE KEY uk_questionnaire_root_execution (tenant_id, root_task_id, execution_task_id),
  KEY idx_execution_input_owner (tenant_id, owner_type, owner_id, draft_id, branch_id),
  KEY idx_questionnaire_execution_draft
    (tenant_id, draft_id, branch_id, task_type, create_time),
  CONSTRAINT fk_execution_input_root FOREIGN KEY (tenant_id, root_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT fk_execution_input_execution FOREIGN KEY (tenant_id, execution_task_id)
    REFERENCES av_ai_task(tenant_id, id),
  CONSTRAINT fk_execution_input_draft FOREIGN KEY (tenant_id, owner_type, owner_id, draft_id)
    REFERENCES av_script_draft(tenant_id, owner_type, owner_id, id),
  CONSTRAINT fk_execution_input_branch FOREIGN KEY
    (tenant_id, owner_type, owner_id, draft_id, branch_id)
    REFERENCES av_script_branch(tenant_id, owner_type, owner_id, draft_id, id),
  CONSTRAINT ck_execution_input_owner CHECK (owner_type IN ('personal','organization')),
  CONSTRAINT ck_execution_input_type CHECK (task_type IN ('question_generate','evidence_retrieve')),
  CONSTRAINT ck_execution_input_initiator CHECK (initiator_type='app_user')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`av_questionnaire_execution_input` 与 P0-C execution task 严格一对一且 append-only。重复 `execution_task_id` 后必须重读并比较 `rootTaskId/taskType/draftId/branchId/draftRevision/branchRevision/generationContextRevision/generationInputHash/requestHash/frozenInputHash/initiatorType/initiatorId` 全部摘要；完全相同才幂等回读，任一不同抛 `46116`，永不 UPDATE 或覆盖 JSON。

所有 P2 parent 表必须提供以 `tenant_id,owner_type,owner_id` 开头并包含聚合 scope/id 的唯一键，所有可表达 owner scope 的 child FK 必须使用相同复合列，单列 `... REFERENCES ...(id)` 在 P2 DDL 中禁止。`av_ai_task` 没有 owner 列，故只允许使用 addendum `04a` 已冻结的 `(tenant_id,id)` 复合 FK；P2 Service 还必须用冻结 `TaskInitiatorDTO` 与 workspace scope 证明 task owner，迁移 IT 对同 tenant 不同 owner 的 task 引用做 Service/零写反例，不虚称数据库能验证不存在的 owner 列。`av_evidence_fact` 因此显式携带 `draft_id/branch_id`，不得靠 source join 猜 scope。

`av_script_draft` 的 `current_direction_revision_id/current_branch_id/current_supplement_revision_id`，以及既有 `av_script_branch` 的 `fork_question_no/fork_reason/direction_revision_id/supplement_revision_id/current_question_no` 和 scope unique 必须使用 `information_schema` 守卫的幂等 ALTER。迁移内创建临时过程后逐项调用，最后删除过程；列、索引、FK 都必须同样处理：

```sql
DROP PROCEDURE IF EXISTS p2_add_column_if_missing;
DROP PROCEDURE IF EXISTS p2_add_index_if_missing;
DROP PROCEDURE IF EXISTS p2_add_fk_if_missing;
DELIMITER $$
CREATE PROCEDURE p2_add_column_if_missing(
  IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column
  ) THEN
    SET @p2_sql=p_ddl; PREPARE p2_stmt FROM @p2_sql;
    EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
  END IF;
END$$
CREATE PROCEDURE p2_add_index_if_missing(
  IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name=p_table AND index_name=p_index
  ) THEN
    SET @p2_sql=p_ddl; PREPARE p2_stmt FROM @p2_sql;
    EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
  END IF;
END$$
CREATE PROCEDURE p2_add_fk_if_missing(
  IN p_table VARCHAR(64), IN p_constraint VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE table_schema=DATABASE() AND table_name=p_table
      AND constraint_name=p_constraint AND constraint_type='FOREIGN KEY'
  ) THEN
    SET @p2_sql=p_ddl; PREPARE p2_stmt FROM @p2_sql;
    EXECUTE p2_stmt; DEALLOCATE PREPARE p2_stmt;
  END IF;
END$$
DELIMITER ;

CALL p2_add_column_if_missing('av_script_draft','current_direction_revision_id',
  'ALTER TABLE av_script_draft ADD COLUMN current_direction_revision_id BIGINT NULL');
CALL p2_add_column_if_missing('av_script_draft','current_branch_id',
  'ALTER TABLE av_script_draft ADD COLUMN current_branch_id BIGINT NULL');
CALL p2_add_column_if_missing('av_script_draft','current_supplement_revision_id',
  'ALTER TABLE av_script_draft ADD COLUMN current_supplement_revision_id BIGINT NULL');
CALL p2_add_column_if_missing('av_script_branch','fork_question_no',
  'ALTER TABLE av_script_branch ADD COLUMN fork_question_no TINYINT NULL');
CALL p2_add_column_if_missing('av_script_branch','fork_reason',
  'ALTER TABLE av_script_branch ADD COLUMN fork_reason VARCHAR(64) NULL');
CALL p2_add_column_if_missing('av_script_branch','direction_revision_id',
  'ALTER TABLE av_script_branch ADD COLUMN direction_revision_id BIGINT NULL');
CALL p2_add_column_if_missing('av_script_branch','supplement_revision_id',
  'ALTER TABLE av_script_branch ADD COLUMN supplement_revision_id BIGINT NULL');
CALL p2_add_column_if_missing('av_script_branch','current_question_no',
  'ALTER TABLE av_script_branch ADD COLUMN current_question_no TINYINT NOT NULL DEFAULT 0');
CALL p2_add_index_if_missing('av_script_branch','uk_draft_branch_revision',
  'ALTER TABLE av_script_branch ADD UNIQUE KEY uk_draft_branch_revision (tenant_id,draft_id,branch_revision)');
CALL p2_add_index_if_missing('av_script_draft','uk_draft_owner_scope_id',
  'ALTER TABLE av_script_draft ADD UNIQUE KEY uk_draft_owner_scope_id (tenant_id,owner_type,owner_id,id)');
CALL p2_add_index_if_missing('av_script_branch','uk_branch_owner_scope_id',
  'ALTER TABLE av_script_branch ADD UNIQUE KEY uk_branch_owner_scope_id (tenant_id,owner_type,owner_id,draft_id,id)');
CALL p2_add_fk_if_missing('av_script_draft','fk_draft_current_direction',
  'ALTER TABLE av_script_draft ADD CONSTRAINT fk_draft_current_direction FOREIGN KEY (tenant_id,owner_type,owner_id,id,current_direction_revision_id) REFERENCES av_script_direction_revision(tenant_id,owner_type,owner_id,draft_id,id)');
CALL p2_add_fk_if_missing('av_script_draft','fk_draft_current_branch',
  'ALTER TABLE av_script_draft ADD CONSTRAINT fk_draft_current_branch FOREIGN KEY (tenant_id,owner_type,owner_id,id,current_branch_id) REFERENCES av_script_branch(tenant_id,owner_type,owner_id,draft_id,id)');
CALL p2_add_fk_if_missing('av_script_draft','fk_draft_current_supplement',
  'ALTER TABLE av_script_draft ADD CONSTRAINT fk_draft_current_supplement FOREIGN KEY (tenant_id,owner_type,owner_id,id,current_branch_id,current_supplement_revision_id) REFERENCES av_script_supplement_revision(tenant_id,owner_type,owner_id,draft_id,branch_id,id)');
CALL p2_add_fk_if_missing('av_script_branch','fk_branch_direction_revision',
  'ALTER TABLE av_script_branch ADD CONSTRAINT fk_branch_direction_revision FOREIGN KEY (tenant_id,owner_type,owner_id,draft_id,direction_revision_id) REFERENCES av_script_direction_revision(tenant_id,owner_type,owner_id,draft_id,id)');
CALL p2_add_fk_if_missing('av_script_branch','fk_branch_supplement_revision',
  'ALTER TABLE av_script_branch ADD CONSTRAINT fk_branch_supplement_revision FOREIGN KEY (tenant_id,owner_type,owner_id,draft_id,id,supplement_revision_id) REFERENCES av_script_supplement_revision(tenant_id,owner_type,owner_id,draft_id,branch_id,id)');

DROP PROCEDURE p2_add_column_if_missing;
DROP PROCEDURE p2_add_index_if_missing;
DROP PROCEDURE p2_add_fk_if_missing;
```

以上五个指针 FK 必须逐项检查 constraint name、复合列顺序和引用 unique，不能只检查列存在。权限种子不创建第二套权限；精确的 4×4 seed 使用稳定 link id。已存在且 active 的相同 `(role_id,permission_id)` 是一致 no-op；缺角色/权限、inactive 映射、link id 被另一映射占用都 `SIGNAL` fail closed，禁止 `ON DUPLICATE KEY UPDATE` 修复脏状态：

```sql
DROP PROCEDURE IF EXISTS p2_seed_role_permissions;
DELIMITER $$
CREATE PROCEDURE p2_seed_role_permissions()
BEGIN
  CREATE TEMPORARY TABLE p2_permission_seed(
    link_id BIGINT PRIMARY KEY, role_code VARCHAR(64) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    UNIQUE KEY uk_p2_permission_seed_pair(role_code,permission_code));
  INSERT INTO p2_permission_seed(link_id,role_code,permission_code) VALUES
    (2026072806000001,'personal_creator','aivideo:studio:query'),
    (2026072806000002,'personal_creator','aivideo:studio:edit'),
    (2026072806000003,'personal_creator','aivideo:studio:generate'),
    (2026072806000004,'personal_creator','aivideo:quota:use'),
    (2026072806000005,'organization_owner','aivideo:studio:query'),
    (2026072806000006,'organization_owner','aivideo:studio:edit'),
    (2026072806000007,'organization_owner','aivideo:studio:generate'),
    (2026072806000008,'organization_owner','aivideo:quota:use'),
    (2026072806000009,'organization_admin','aivideo:studio:query'),
    (2026072806000010,'organization_admin','aivideo:studio:edit'),
    (2026072806000011,'organization_admin','aivideo:studio:generate'),
    (2026072806000012,'organization_admin','aivideo:quota:use'),
    (2026072806000013,'organization_member','aivideo:studio:query'),
    (2026072806000014,'organization_member','aivideo:studio:edit'),
    (2026072806000015,'organization_member','aivideo:studio:generate'),
    (2026072806000016,'organization_member','aivideo:quota:use');

  IF (SELECT COUNT(*) FROM p2_permission_seed s
      JOIN app_role r ON r.role_code=s.role_code AND r.built_in=1
        AND r.status='active' AND r.del_flag='0'
      JOIN app_permission p ON p.permission_code=s.permission_code
        AND p.status='active') <> 16 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P2 permission seed dependency missing or ambiguous';
  END IF;
  IF EXISTS (SELECT 1 FROM p2_permission_seed s
      JOIN app_role r ON r.role_code=s.role_code AND r.built_in=1
      JOIN app_permission p ON p.permission_code=s.permission_code
      JOIN app_role_permission x ON x.id=s.link_id
      WHERE x.role_id<>r.role_id OR x.permission_id<>p.permission_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P2 permission deterministic id conflict';
  END IF;
  IF EXISTS (SELECT 1 FROM p2_permission_seed s
      JOIN app_role r ON r.role_code=s.role_code AND r.built_in=1
      JOIN app_permission p ON p.permission_code=s.permission_code
      JOIN app_role_permission x ON x.role_id=r.role_id AND x.permission_id=p.permission_id
      WHERE x.status<>'active') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='P2 permission existing mapping is inconsistent';
  END IF;

  INSERT INTO app_role_permission(
    id,role_id,permission_id,status,created_by_type,created_by_id,
    updated_by_type,updated_by_id,create_time,update_time)
  SELECT s.link_id,r.role_id,p.permission_id,'active','sys_user',1,'sys_user',1,NOW(),NOW()
  FROM p2_permission_seed s
  JOIN app_role r ON r.role_code=s.role_code AND r.built_in=1
    AND r.status='active' AND r.del_flag='0'
  JOIN app_permission p ON p.permission_code=s.permission_code AND p.status='active'
  LEFT JOIN app_role_permission x ON x.role_id=r.role_id AND x.permission_id=p.permission_id
  WHERE x.id IS NULL;
  DROP TEMPORARY TABLE p2_permission_seed;
END$$
DELIMITER ;
CALL p2_seed_role_permissions();
DROP PROCEDURE p2_seed_role_permissions;
```

迁移 IT 必须从 addendum 的 `schemaAddendum.forwardMigration` 读取并执行 `01→02→03→04→04a→05→06→06`，断言 11 张表、P0-C member/active index、全部列类型/nullability/default、复合 FK/unique/index/CHECK、三个 draft current 指针、`uk_draft_branch_revision`。每条 parent-child 关系都至少插入一次 tenant 不同、同 tenant owner 不同、同 owner draft/branch 不同的反例并由真实 DB FK 拒绝；`av_ai_task` owner 反例由 Service IT 证明零写。权限覆盖首次插入 16、第二次一致 no-op、inactive pair/link-id 冲突 fail closed。`no_results(0,0)` 与 `completed(>0,>0)` 双向通过，`no_results` 非零及 `completed` 任一零都由 CHECK 拒绝；Service IT 再证明 completed 的持久化 child 行数精确等于两个 count。

### 4.2 RuoYi 文件树与 Entity 边界

核心业务结构固定如下，枚举直接位于 `questionnaire/domain` 根，核心不放 BO/VO/Controller：

```text
ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/
├── domain/
├── dto/
├── mapper/
├── service/
└── service/impl/
```

Service 接口统一 `I...Service`，实现统一 `...ServiceImpl`。事务、状态流转、归属、幂等、额度和跨 Mapper 编排都在 Service；P2 Entity 是字段完整的贫血 POJO，**不继承 `BaseEntity`**，避免其默认 creator/update 语义覆盖 app 身份域；Mapper 继承 `BaseMapperPlus`，只在确有复杂联表时增加 XML。所有 scope/creator 字段由 Service 按 3.4 节显式赋值，Entity/Mapper 不调用默认 `LoginHelper/StpUtil`。禁止新增平行业务层，禁止 core 供应商原始类型，禁止 core `domain/bo` 或 `domain/vo`。

端侧请求/响应文件位于：

```text
ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo/
ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/vo/
ai-video-user/src/main/java/org/dromara/aivideo/user/studio/controller/
```

直接技术集成文件位于：

```text
ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/
ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/listener/
```

跨模块字段、状态、接口、错误码或任务规则变化时，先由契约 owner 串行修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、必要时 `docs/ARCHITECTURE.md`，随后运行 `scripts/validate-development-standards.ps1`。P2 独占迁移 `docs/sql/ai-video/mysql/20260728_06_p2_questionnaire.sql` 和 `ai-video-ui/ai-video-webapp/mock/aivideo-studio.ts`。

## 5. 测试、证据与命令约定

- 所有新增/修改 JUnit `*Test` 和 `*IT` 类都在类级标注 `@Tag("dev")`。
- 所有 `*IT` 首行环境准备调用 `LocalIntegrationEnvironment.requireFromEnvironment()`；只允许本机 `ai_video_test`，Redis 使用独立逻辑库和当前 `aivideo:it:<runId>:` 前缀；禁止 Docker、WSL、Testcontainers、开发/生产库和 `FLUSHALL`。
- IT 命令必须同时带 `'-Pdev,local-integration-test'`；单独激活 `local-integration-test` 会关闭 `dev` 的 `activeByDefault`，使 Surefire/Failsafe 的 `groups=${profiles.active}` 失去 `dev`。用户端 `application-dev.yml` 配置缺失或目标不安全时 fail-fast，环境变量仅可选覆盖，不得跳过后宣称通过。
- RED/GREEN 必须保存 fresh Surefire/Failsafe/Vitest 原始报告，并检查目标测试数 `> 0`、failures/errors `= 0`；仅凭 Maven/npm 退出码不足以过门禁。
- RED/GREEN 阶段调用 JVM/Vitest/manifest gate 时，必须把本任务卡“文件”行的完整仓库相对路径原样传给 `-AllowedPaths`；三个 evidence gate 再把它透传统一 worktree gate。最终 clean review/freeze 才传空数组，禁止用空数组误杀合法 TDD 改动或用宽目录掩盖越界文件。
- 每张任务卡固定输出：`完成项`、`风险`、`验证证据`、`阻塞项`。writer 不得判自己的独立 review 为 PASS。

### 5.1 统一 worktree gate

Task 1 把下列完整脚本以 UTF-8 无 BOM、LF 写到 `git rev-parse --git-path p2-worktree-gate.ps1`。13 张任务卡的每条 RED/GREEN/提交前命令都必须先调用它；返回值不是精确 `P2_WORKTREE_GATE_OK` 时立即停止。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [ValidateSet('inspect','f1-pending','f1-rebase','f2-pending','f2-rebase','final','selftest')]
  [string] $Phase = 'inspect',
  [string] $ExpectedOriginalF1Head,
  [string] $ExpectedF1Head,
  [string] $ExpectedF2Head,
  [string[]] $AllowedPaths = @(),
  [switch] $RequireClean
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) {
  throw 'P2 gate 无法解析仓库根'
}
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'P2 gate RepoRoot 不准确' }
Set-Location -LiteralPath $root

function Assert-Sha40([object] $Value, [string] $Name) {
  if ($Value -isnot [string] -or $Value -cnotmatch '^[0-9a-f]{40}$') {
    throw "$Name 必须是小写 40 位提交 SHA"
  }
}
function Assert-Allowed([string] $Path, [string[]] $Allowed) {
  $normalized = $Path.Replace('\','/').Trim()
  foreach ($entry in $Allowed) {
    $prefix = $entry.Replace('\','/').TrimEnd('/')
    if ($normalized -ceq $prefix -or $normalized.StartsWith($prefix + '/', [StringComparison]::Ordinal)) {
      return
    }
  }
  throw "P2 gate 检测到越界文件：$normalized"
}
function Write-ImmutableRecord([string] $Name, [System.Collections.IDictionary] $Core) {
  $pathText = (& git rev-parse --git-path $Name).Trim()
  $path = if ([IO.Path]::IsPathRooted($pathText)) {
    [IO.Path]::GetFullPath($pathText)
  } else {
    [IO.Path]::GetFullPath((Join-Path $root $pathText))
  }
  $coreJson = $Core | ConvertTo-Json -Depth 8 -Compress
  if (Test-Path -LiteralPath $path -PathType Leaf) {
    $existing = Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    $existingCore = [ordered]@{}
    foreach ($key in $Core.Keys) {
      if ($existing.PSObject.Properties.Name -cnotcontains $key) { throw "$Name 缺字段 $key" }
      $existingCore[$key] = $existing.$key
    }
    $expectedFields = @($Core.Keys) + 'capturedAtUtc'
    if (@($existing.PSObject.Properties.Name).Count -ne $expectedFields.Count -or
        @($existing.PSObject.Properties.Name | Where-Object { $expectedFields -cnotcontains $_ }).Count -ne 0) {
      throw "$Name 字段集合漂移"
    }
    if (($existingCore | ConvertTo-Json -Depth 8 -Compress) -cne $coreJson) {
      throw "$Name 已存在但 payload 不同，拒绝覆盖"
    }
    return $existing
  }
  $document = [ordered]@{}
  foreach ($key in $Core.Keys) { $document[$key] = $Core[$key] }
  $document.capturedAtUtc = [DateTime]::UtcNow.ToString('o')
  $bytes = [Text.UTF8Encoding]::new($false).GetBytes(
    ($document | ConvertTo-Json -Depth 8 -Compress))
  $directory = Split-Path -Parent $path
  if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
    [void](New-Item -ItemType Directory -Path $directory)
  }
  $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
  return (Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json)
}

if ($Phase -eq 'selftest') {
  Assert-Sha40 ('a' * 40) 'fixture.sha'
  Assert-Allowed 'allowed/file.java' @('allowed')
  $rejected = $false
  try { Assert-Allowed '../escape' @('allowed') } catch { $rejected = $true }
  if (-not $rejected) { throw 'P2 worktree gate 越界自测意外通过' }
  'P2_WORKTREE_GATE_SELFTEST_OK'
  exit 0
}

$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or $branch -cnotlike 'codex/*' -or $branch -ceq 'main') {
  throw 'P2 必须位于 linked codex/* 分支'
}
$gitDir = [IO.Path]::GetFullPath((& git rev-parse --git-dir).Trim())
$commonDir = [IO.Path]::GetFullPath((& git rev-parse --git-common-dir).Trim())
if ($gitDir -ceq $commonDir) { throw 'P2 必须使用 linked worktree，不能使用主工作树' }
$worktreeRecords = @(& git worktree list --porcelain)
if ($LASTEXITCODE -ne 0 -or $worktreeRecords -cnotcontains ('worktree ' + $root.Replace('\','/'))) {
  $nativeRoot = 'worktree ' + $root
  if ($worktreeRecords -cnotcontains $nativeRoot) { throw '当前目录未登记为 git worktree' }
}
foreach ($marker in @('rebase-merge','rebase-apply','MERGE_HEAD','CHERRY_PICK_HEAD')) {
  $markerText = (& git rev-parse --git-path $marker).Trim()
  $markerPath = if ([IO.Path]::IsPathRooted($markerText)) { $markerText } else { Join-Path $root $markerText }
  if (Test-Path -LiteralPath $markerPath) { throw "P2 检测到未完成 Git 操作：$marker" }
}
$head = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
Assert-Sha40 $head 'HEAD'
if ($ExpectedOriginalF1Head) {
  $ExpectedOriginalF1Head = $ExpectedOriginalF1Head.ToLowerInvariant()
  Assert-Sha40 $ExpectedOriginalF1Head 'original F1'
  if ($Phase -cne 'f1-pending') {
    & git merge-base --is-ancestor $ExpectedOriginalF1Head $head
    if ($LASTEXITCODE -ne 0) { throw '原始 F1 不是当前 HEAD 祖先' }
  }
}
if ($ExpectedF1Head) {
  $ExpectedF1Head = $ExpectedF1Head.ToLowerInvariant(); Assert-Sha40 $ExpectedF1Head 'F1'
  if ($Phase -in @('f1-rebase','f2-pending')) {
    & git merge-base --is-ancestor $ExpectedF1Head $head
    if ($LASTEXITCODE -ne 0) { throw 'F1 amendment 不是当前 HEAD 祖先' }
  }
}
if ($ExpectedF2Head) {
  $ExpectedF2Head = $ExpectedF2Head.ToLowerInvariant(); Assert-Sha40 $ExpectedF2Head 'F2'
  if ($Phase -cne 'f2-pending') {
    & git merge-base --is-ancestor $ExpectedF2Head $head
    if ($LASTEXITCODE -ne 0) { throw 'F2 不是当前 HEAD 祖先' }
  }
}
$dirty = @(& git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0) { throw '无法读取 P2 worktree 状态' }
foreach ($line in $dirty) {
  $path = $line.Substring(3)
  if ($path.Contains(' -> ')) { $path = $path.Split(' -> ')[-1] }
  Assert-Allowed $path $AllowedPaths
}
if ($RequireClean -and $dirty.Count -ne 0) { $dirty; throw 'P2 gate 要求 clean worktree' }

$f1PendingName = 'p2-f1-rebase-pending.json'
$f1RebaseName = 'p2-f1-rebase.json'
$f2PendingName = 'p2-f2-rebase-pending.json'
$f2RebaseName = 'p2-f2-rebase.json'
$finalName = 'p2-integration-final.json'
if ($Phase -eq 'f1-pending') {
  if (-not $RequireClean -or -not $ExpectedOriginalF1Head -or -not $ExpectedF1Head) {
    throw 'f1-pending 必须 RequireClean 并提供原始 F1/F1 amendment'
  }
  $mergeBase = (& git merge-base $head $ExpectedF1Head).Trim().ToLowerInvariant()
  Assert-Sha40 $mergeBase 'F1 beforeMergeBase'
  [void](Write-ImmutableRecord $f1PendingName ([ordered]@{
    phase='f1-pending'; branch=$branch; beforeHead=$head
    originalF1Head=$ExpectedOriginalF1Head; targetHead=$ExpectedF1Head
    beforeMergeBase=$mergeBase
  }))
}
if ($Phase -eq 'f1-rebase') {
  if (-not $RequireClean -or -not $ExpectedOriginalF1Head -or -not $ExpectedF1Head) {
    throw 'f1-rebase 必须 RequireClean 并提供原始 F1/F1 amendment'
  }
  $pendingPath = (& git rev-parse --git-path $f1PendingName).Trim()
  if (-not (Test-Path -LiteralPath $pendingPath -PathType Leaf)) { throw '缺少 F1 pending 记录' }
  $pending = Get-Content -LiteralPath $pendingPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $afterMergeBase = (& git merge-base $head $ExpectedF1Head).Trim().ToLowerInvariant()
  if ($pending.originalF1Head -cne $ExpectedOriginalF1Head -or
      $pending.targetHead -cne $ExpectedF1Head -or $afterMergeBase -cne $ExpectedF1Head) {
    throw 'F1 amendment rebase 目标或 merge-base 不一致'
  }
  [void](Write-ImmutableRecord $f1RebaseName ([ordered]@{
    phase='f1-rebase'; branch=$branch; beforeHead=$pending.beforeHead
    originalF1Head=$ExpectedOriginalF1Head; targetHead=$ExpectedF1Head
    beforeMergeBase=$pending.beforeMergeBase; afterHead=$head; afterMergeBase=$afterMergeBase
  }))
}
if ($Phase -eq 'f2-pending') {
  if (-not $RequireClean -or -not $ExpectedOriginalF1Head -or
      -not $ExpectedF1Head -or -not $ExpectedF2Head) {
    throw 'f2-pending 必须 RequireClean 并提供原始 F1/F1 amendment/F2'
  }
  $f1RecordPath = (& git rev-parse --git-path $f1RebaseName).Trim()
  if (-not (Test-Path -LiteralPath $f1RecordPath -PathType Leaf)) { throw '缺少 F1 rebase 完成记录' }
  $mergeBase = (& git merge-base $head $ExpectedF2Head).Trim().ToLowerInvariant()
  Assert-Sha40 $mergeBase 'F2 beforeMergeBase'
  [void](Write-ImmutableRecord $f2PendingName ([ordered]@{
    phase='f2-pending'; branch=$branch; beforeHead=$head
    originalF1Head=$ExpectedOriginalF1Head; amendmentHead=$ExpectedF1Head
    targetHead=$ExpectedF2Head; beforeMergeBase=$mergeBase
  }))
}
if ($Phase -eq 'f2-rebase') {
  if (-not $RequireClean -or -not $ExpectedOriginalF1Head -or
      -not $ExpectedF1Head -or -not $ExpectedF2Head) {
    throw 'f2-rebase 必须 RequireClean 并提供原始 F1/F1 amendment/F2'
  }
  $pendingPath = (& git rev-parse --git-path $f2PendingName).Trim()
  if (-not (Test-Path -LiteralPath $pendingPath -PathType Leaf)) { throw '缺少 F2 pending 记录' }
  $pending = Get-Content -LiteralPath $pendingPath -Raw -Encoding UTF8 | ConvertFrom-Json
  $afterMergeBase = (& git merge-base $head $ExpectedF2Head).Trim().ToLowerInvariant()
  if ($pending.originalF1Head -cne $ExpectedOriginalF1Head -or
      $pending.amendmentHead -cne $ExpectedF1Head -or $pending.targetHead -cne $ExpectedF2Head -or
      $afterMergeBase -cne $ExpectedF2Head) { throw 'F2 rebase 目标或 merge-base 不一致' }
  [void](Write-ImmutableRecord $f2RebaseName ([ordered]@{
    phase='f2-rebase'; branch=$branch; beforeHead=$pending.beforeHead
    originalF1Head=$ExpectedOriginalF1Head; amendmentHead=$ExpectedF1Head
    targetHead=$ExpectedF2Head; beforeMergeBase=$pending.beforeMergeBase
    afterHead=$head; afterMergeBase=$afterMergeBase
  }))
}
if ($Phase -eq 'final') {
  if (-not $RequireClean) { throw 'final 阶段必须 RequireClean' }
  $f1RebasePath = (& git rev-parse --git-path $f1RebaseName).Trim()
  $f2RebasePath = (& git rev-parse --git-path $f2RebaseName).Trim()
  if (-not (Test-Path -LiteralPath $f1RebasePath -PathType Leaf) -or
      -not (Test-Path -LiteralPath $f2RebasePath -PathType Leaf)) { throw '缺少 F1/F2 rebase 完成记录' }
  $f1Rebase = Get-Content -LiteralPath $f1RebasePath -Raw -Encoding UTF8 | ConvertFrom-Json
  $f2Rebase = Get-Content -LiteralPath $f2RebasePath -Raw -Encoding UTF8 | ConvertFrom-Json
  if ($f1Rebase.originalF1Head -cne $ExpectedOriginalF1Head -or
      $f1Rebase.targetHead -cne $ExpectedF1Head -or
      $f2Rebase.originalF1Head -cne $ExpectedOriginalF1Head -or
      $f2Rebase.amendmentHead -cne $ExpectedF1Head -or
      $f2Rebase.targetHead -cne $ExpectedF2Head) {
    throw 'final F1/F2 与 rebase 记录不同'
  }
  [void](Write-ImmutableRecord $finalName ([ordered]@{
    phase='final'; branch=$branch; originalF1Head=$ExpectedOriginalF1Head
    amendmentHead=$ExpectedF1Head
    f2Head=$ExpectedF2Head; candidateHead=$head
  }))
}
'P2_WORKTREE_GATE_OK'
```

Task 1 还要把下列执行器以 UTF-8 无 BOM、LF 写到 `git rev-parse --git-path p2-rebase-baseline.ps1`。F1 handoff 后先执行一次 `-Baseline F1 -Mode start`；F2 handoff 后再执行一次 `-Baseline F2 -Mode start`。它不是第五个业务 gate，而是唯一获准真正调用 `git rebase` 的动态根执行器。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [Parameter(Mandatory = $true)][ValidateSet('F1','F2')][string] $Baseline,
  [ValidateSet('start','finish','abort','selftest')][string] $Mode = 'start'
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'rebase 执行器无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'rebase 执行器 RepoRoot 不准确' }
Set-Location -LiteralPath $root
function Resolve-GitPath([string] $Name) {
  $text = (& git rev-parse --git-path $Name).Trim()
  if ([IO.Path]::IsPathRooted($text)) { return [IO.Path]::GetFullPath($text) }
  return [IO.Path]::GetFullPath((Join-Path $root $text))
}
function Assert-Sha([object] $Value,[int] $Length,[string] $Name) {
  if ($Value -isnot [string] -or $Value -cnotmatch ('^[0-9a-f]{' + $Length + '}$')) { throw "$Name SHA 非法" }
}
function Assert-Fields([object] $Value,[string[]] $Expected,[string] $Name) {
  if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($Expected -join '|')) { throw "$Name 字段/顺序漂移" }
}
function Assert-Array([object] $Value,[string[]] $Expected,[string] $Name) {
  if ($Value -isnot [System.Array]) { throw "$Name 必须是真实 JSON array，拒绝 scalar" }
  $actual = [object[]]$Value
  if ($actual.Count -ne $Expected.Count) { throw "$Name 数组长度漂移" }
  for ($index = 0; $index -lt $Expected.Count; $index++) {
    if ($actual[$index] -isnot [string] -or $actual[$index] -cne $Expected[$index]) {
      throw "$Name 数组第 $index 项类型/大小写/顺序漂移"
    }
  }
}
function Get-TextSha256([string] $Value) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try {
    return ([BitConverter]::ToString($sha.ComputeHash(
      [Text.UTF8Encoding]::new($false).GetBytes($Value)))).Replace('-','').ToLowerInvariant()
  } finally { $sha.Dispose() }
}
function Get-CanonicalJsonSha256([object] $Value) {
  return Get-TextSha256 ($Value | ConvertTo-Json -Depth 12 -Compress)
}
function Normalize-JavaContract([string] $Source) {
  $value = [regex]::Replace($Source, '(?s)/[*].*?[*]/|//[^\r\n]*', ' ')
  $value = [regex]::Replace($value, '@(?:[A-Za-z_][\w.]*)(?:\([^)]*\))?\s*', '')
  $value = [regex]::Replace($value, '\s+', ' ')
  return [regex]::Replace($value, '\s*([(),;<>{}])\s*', '$1').Trim()
}
function Assert-JavaSignature([string] $Source,[string] $Signature,[string] $Name) {
  $count = [regex]::Matches($Source, [regex]::Escape($Signature)).Count
  if ($count -ne 1) { throw "$Name exact signature 计数必须为 1，实际 $count" }
}
function Assert-P0cTaskContracts([string] $Root) {
  $serviceRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service'
  $task = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskService.java') -Raw -Encoding UTF8)
  $attempt = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskAttemptService.java') -Raw -Encoding UTF8)
  $dispatcher = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskExecutionDispatcher.java') -Raw -Encoding UTF8)
  foreach ($signature in @(
    'TaskCreationResultDTO createChargeableTask(ChargeableTaskDTO request);',
    'TaskCreationResultDTO createFreeTask(FreeTaskDTO request);',
    'List<AiTaskExecutionLeaseDTO> claimExecutableTasks(Instant now,String workerId,Instant leaseExpiresAt,int limit);',
    'AiTaskExecutionLeaseDTO renewLease(AiTaskExecutionLeaseDTO lease,Instant newLeaseExpiresAt);',
    'void recordHandlerFailure(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage,boolean retryable);',
    'void markSuccess(AiTaskExecutionLeaseDTO lease,TaskResultReferenceDTO result);',
    'void markFailed(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $task $signature "IAiTaskService.$signature" }
  foreach ($ownerSignature in @(
    'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
    'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
  )) {
    $signature = Normalize-JavaContract $ownerSignature
    Assert-JavaSignature $task $signature "IAiTaskService.$ownerSignature"
  }
  foreach ($signature in @(
    'AiTaskAttemptHandleDTO startAttempt(Long rootTaskId,Long executionTaskId,String leaseOwner,String callPurpose,String provider,String model,String inputHash);',
    'void completeAttempt(Long attemptId,ProviderUsageDTO usage,String outputHash);',
    'void failAttempt(Long attemptId,ProviderUsageDTO usage,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $attempt $signature "IAiTaskAttemptService.$signature" }
  Assert-JavaSignature $dispatcher 'void enqueue(Long rootTaskId,Long executionTaskId);' 'IAiTaskExecutionDispatcher.enqueue'
}
function Assert-F2StableDtoSources([object] $F2,[string] $Root) {
  $dtoNames = @(
    'KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO',
    'KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO')
  $expectedComponents = [ordered]@{
    KnowledgeRouteRequestDTO=@(
      'Long directionCatalogVersionId','String industryCode','String purposeCode',
      'Integer targetDurationSeconds','List<String> tagCodes')
    KnowledgeRouteResultDTO=@(
      'String routingVersion','String videoTypeCode','List<KnowledgePlanDTO> plans','String contentHash')
    KnowledgePlanDTO=@(
      'String candidateCode','String planCode','Long primaryTemplateVersionId',
      'String angleCode','String differentiatorTechniqueCode')
    KnowledgeSnapshotRequestDTO=@(
      'Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts')
    KnowledgeSnapshotDTO=@(
      'Long snapshotId','Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts',
      'List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials',
      'String contentHash','Instant createdAt')
  }
  Assert-Array $F2.stableDtos $dtoNames 'F2.stableDtos'
  Assert-Fields $F2.stableDtoComponentRegistry $dtoNames 'F2.stableDtoComponentRegistry'
  Assert-Fields $F2.stableDtoSourceSha256 $dtoNames 'F2.stableDtoSourceSha256'
  $dtoRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto'
  foreach ($dtoName in $dtoNames) {
    Assert-Array $F2.stableDtoComponentRegistry.$dtoName $expectedComponents[$dtoName] "F2.stableDtoComponentRegistry.$dtoName"
    $dtoFile = Join-Path $dtoRoot ($dtoName + '.java')
    if (-not (Test-Path -LiteralPath $dtoFile -PathType Leaf)) { throw "F2 稳定 DTO 源码缺失：$dtoName" }
    $source = Get-Content -LiteralPath $dtoFile -Raw -Encoding UTF8
    $header = [regex]::Match($source,
      '(?s)(?:public\s+)?record\s+' + [regex]::Escape($dtoName) + '\s*\((?<components>.*?)\)\s*\{')
    if (-not $header.Success) { throw "F2 稳定 DTO record header 缺失：$dtoName" }
    $actualComponents = @($header.Groups['components'].Value -split ',' |
      ForEach-Object { ($_ -replace '\s+', ' ').Trim() })
    Assert-Array $actualComponents $expectedComponents[$dtoName] "F2.liveComponents.$dtoName"
    $expectedSha = $F2.stableDtoSourceSha256.$dtoName
    if ($expectedSha -isnot [string] -or $expectedSha -cnotmatch '^[0-9a-f]{64}$' -or
        $expectedSha -cne (Get-FileHash -LiteralPath $dtoFile -Algorithm SHA256).Hash.ToLowerInvariant()) {
      throw "F2 稳定 DTO source SHA 漂移：$dtoName"
    }
  }
}
if ($Mode -ceq 'selftest') {
  Assert-Sha ('a' * 40) 40 'fixture.head'
  Assert-Array @('alpha','beta') @('alpha','beta') 'array-positive-canary'
  foreach ($arrayCanary in @(
    [pscustomobject]@{label='scalar';value='alpha'},
    [pscustomobject]@{label='reversed';value=@('beta','alpha')},
    [pscustomobject]@{label='duplicate';value=@('alpha','alpha')}
  )) {
    $rejected = $false
    try { Assert-Array $arrayCanary.value @('alpha','beta') "array-$($arrayCanary.label)" } catch { $rejected = $true }
    if (-not $rejected) { throw "Assert-Array $($arrayCanary.label) 自测意外通过" }
  }
  $rejected = $false
  try { Assert-Fields ([pscustomobject][ordered]@{a=1;extra=2}) @('a') 'fixture' } catch { $rejected = $true }
  if (-not $rejected) { throw 'rebase exact-field 自测意外通过' }
  $canary = Normalize-JavaContract 'interface X { void m(Long draftId, Long branchRevision); }'
  Assert-JavaSignature $canary 'void m(Long draftId,Long branchRevision);' 'signature-canary'
  $rejected = $false
  try { Assert-JavaSignature $canary 'void m(Long branchRevision,Long draftId);' 'signature-order-canary' } catch { $rejected = $true }
  if (-not $rejected) { throw 'rebase signature 参数顺序自测意外通过' }
  $addendumCanary = [pscustomobject][ordered]@{
    originalF1Head=('a' * 40); amendmentHead=('b' * 40); originalF1HandoffSha256=('c' * 64)
    requiredMethods=@(); schemaAddendum=[ordered]@{}; owner='writer'; reviewer='reviewer'
    reviewStatus='PASS'; reviewedHead=('b' * 40); reviewCompletedAtUtc='2026-01-01T00:00:00Z'
    evidence=@(); capturedAtUtc='2026-01-01T00:00:01Z'
  }
  Assert-Fields $addendumCanary @(
    'originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum',
    'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc') 'addendum-field-canary'
  Assert-Array @('source-signatures','migration-04a','independent-review') @(
    'source-signatures','migration-04a','independent-review') 'addendum-evidence-order-canary'
  $rejected = $false
  try { Assert-Array @('independent-review','migration-04a','source-signatures') @(
      'source-signatures','migration-04a','independent-review') 'bad-evidence-order' } catch { $rejected = $true }
  if (-not $rejected) { throw 'addendum evidence 顺序负向自测意外通过' }
  'P2_REBASE_BASELINE_SELFTEST_OK'
  exit 0
}
$f1File = Resolve-GitPath 'p0c-f1-handoff.json'
if (-not (Test-Path -LiteralPath $f1File -PathType Leaf)) { throw '缺少 F1 handoff' }
$f1 = Get-Content -LiteralPath $f1File -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $f1 @(
  'f1Head','fullF1Ready','f0Head','p0bCandidateHead','p0cAcceptanceWindowStart','p0cAcceptanceWindowEnd',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','migrations','sharedFiles',
  'sharedFileHandoffTarget','sharedFileBaselineHead','downstreamRebaseOwners','stableServices','internalSpis',
  'stableDomainAndDtos','knowledgeImportRevisionMapping','capturedAtUtc') 'F1 handoff'
if ($f1.fullF1Ready -isnot [bool] -or -not $f1.fullF1Ready -or $f1.sharedFileHandoffTarget -cne 'P2') {
  throw 'F1 ready/所有权非法'
}
Assert-Sha $f1.f1Head 40 'F1.head'
$addendumFile = Resolve-GitPath 'p0c-f1-contract-addendum.json'
if (-not (Test-Path -LiteralPath $addendumFile -PathType Leaf)) { throw '缺少不可覆盖的 F1 contract addendum' }
$addendum = Get-Content -LiteralPath $addendumFile -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $addendum @(
  'originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc') 'F1 contract addendum'
Assert-Sha $addendum.originalF1Head 40 'addendum.originalF1Head'
Assert-Sha $addendum.amendmentHead 40 'addendum.amendmentHead'
Assert-Sha $addendum.originalF1HandoffSha256 64 'addendum.originalF1HandoffSha256'
if ($addendum.originalF1Head -cne $f1.f1Head -or
    $addendum.originalF1HandoffSha256 -cne (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $addendum.owner -isnot [string] -or $addendum.reviewer -isnot [string] -or
    [string]::IsNullOrWhiteSpace($addendum.owner) -or [string]::IsNullOrWhiteSpace($addendum.reviewer) -or
    $addendum.owner.Trim().Equals($addendum.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase) -or
    $addendum.reviewStatus -cne 'PASS' -or $addendum.reviewedHead -cne $addendum.amendmentHead) {
  throw 'F1 contract addendum 未绑定原始 F1 或未独立审核'
}
Assert-Array $addendum.requiredMethods @(
  'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
  'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
) 'addendum.requiredMethods'
Assert-Fields $addendum.schemaAddendum @(
  'forwardMigration','taskGroupMemberTable','activeTaskIndex','originValues','creatorTypes',
  'globalLockOrder','scriptGroupKey','inheritanceScope','forbiddenCopies') 'addendum.schemaAddendum'
if ($addendum.schemaAddendum.forwardMigration -cne '20260728_04a_p0c_task_group_guard.sql' -or
    $addendum.schemaAddendum.taskGroupMemberTable -cne 'av_ai_task_group_member' -or
    $addendum.schemaAddendum.activeTaskIndex -cne 'idx_av_ai_task_active_group' -or
    $addendum.schemaAddendum.scriptGroupKey -cne 'script:{draftId}:{branchRevision}' -or
    $addendum.schemaAddendum.inheritanceScope -cne 'membership_only') {
  throw 'F1 schema addendum migration/table/index/group/inheritance 漂移'
}
Assert-Array $addendum.schemaAddendum.originValues @('origin','inherited') 'addendum.originValues'
Assert-Array $addendum.schemaAddendum.creatorTypes @('app_user','sys_user') 'addendum.creatorTypes'
Assert-Array $addendum.schemaAddendum.globalLockOrder @(
  'draft','current_branch','operation_slot','quota_account','task_or_group_member') 'addendum.globalLockOrder'
Assert-Array $addendum.schemaAddendum.forbiddenCopies @(
  'task','usage','ledger','operation_slot') 'addendum.forbiddenCopies'
$reviewCompleted = [DateTimeOffset]::Parse($addendum.reviewCompletedAtUtc)
$captured = [DateTimeOffset]::Parse($addendum.capturedAtUtc)
if ($reviewCompleted.Offset -ne [TimeSpan]::Zero -or $captured.Offset -ne [TimeSpan]::Zero -or
    $captured -lt $reviewCompleted) { throw 'F1 addendum review/captured 时间非法' }
$expectedEvidence = [ordered]@{
  'source-signatures'='git-metadata:p0c-f1-addendum/source-signatures.manifest.json'
  'migration-04a'='git-metadata:p0c-f1-addendum/migration-04a.manifest.json'
  'independent-review'='git-metadata:p0c-f1-contract-addendum-review.json'
}
$evidenceItems = @($addendum.evidence)
if ($evidenceItems.Count -ne 3) { throw 'F1 addendum evidence 必须恰为三项' }
for ($index = 0; $index -lt 3; $index++) {
  $item = $evidenceItems[$index]
  $kind = @($expectedEvidence.Keys)[$index]
  Assert-Fields $item @('kind','path','sha256') "addendum.evidence[$index]"
  Assert-Sha $item.sha256 64 "addendum.evidence[$index].sha256"
  if ($item.kind -cne $kind -or $item.path -cne $expectedEvidence[$kind]) { throw 'F1 addendum evidence 顺序/路径漂移' }
  $evidenceFile = Resolve-GitPath $item.path.Substring('git-metadata:'.Length)
  if (-not (Test-Path -LiteralPath $evidenceFile -PathType Leaf) -or
      (Get-FileHash -LiteralPath $evidenceFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item.sha256) {
    throw "F1 addendum evidence hash 漂移：$kind"
  }
}
$reviewFile = Resolve-GitPath 'p0c-f1-contract-addendum-review.json'
$addendumReview = Get-Content -LiteralPath $reviewFile -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $addendumReview @(
  'owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256',
  'requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc') 'F1 addendum independent review'
Assert-Sha $addendumReview.requiredMethodsSha256 64 'review.requiredMethodsSha256'
Assert-Sha $addendumReview.schemaAddendumSha256 64 'review.schemaAddendumSha256'
if ($addendumReview.owner -cne $addendum.owner -or $addendumReview.reviewer -cne $addendum.reviewer -or
    $addendumReview.reviewStatus -cne 'PASS' -or $addendumReview.reviewedHead -cne $addendum.amendmentHead -or
    $addendumReview.originalF1Head -cne $addendum.originalF1Head -or
    $addendumReview.originalF1HandoffSha256 -cne $addendum.originalF1HandoffSha256 -or
    $addendumReview.requiredMethodsSha256 -cne (Get-CanonicalJsonSha256 $addendum.requiredMethods) -or
    $addendumReview.schemaAddendumSha256 -cne (Get-CanonicalJsonSha256 $addendum.schemaAddendum) -or
    $addendumReview.reviewCompletedAtUtc -cne $addendum.reviewCompletedAtUtc) {
  throw 'F1 addendum independent review 绑定漂移'
}
& git merge-base --is-ancestor $addendum.originalF1Head $addendum.amendmentHead
if ($LASTEXITCODE -ne 0) { throw '原始 F1 不是 amendmentHead 祖先' }
$f2 = $null
if ($Baseline -ceq 'F2') {
  $f2File = Resolve-GitPath 'p1-f2-handoff.json'
  if (-not (Test-Path -LiteralPath $f2File -PathType Leaf)) { throw '缺少 F2 handoff' }
  $f2 = Get-Content -LiteralPath $f2File -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $f2 @(
    'fullF2Ready','f1Head','originalF1Head','f1AmendmentHead','f2Head','owner','reviewer','reviewStatus','reviewCompletedAtUtc',
    'p1AcceptanceWindowStart','p1AcceptanceWindowEnd','originalF1HandoffSha256','f1AddendumSha256','migrationChain','migrationRepeat05',
    'stableServices','stableDtos','stableDtoComponentRegistry','stableDtoSourceSha256',
    'downstreamConsumers','revisionMappingContractOwner','evidence','capturedAtUtc') 'F2 handoff'
  if ($f2.fullF2Ready -isnot [bool] -or -not $f2.fullF2Ready -or
      $f2.f1Head -cne $addendum.amendmentHead -or $f2.f1Head -cne $f2.f1AmendmentHead -or
      $f2.originalF1Head -cne $f1.f1Head -or $f2.originalF1Head -cne $addendum.originalF1Head) {
    throw 'F2 ready/original/amendment F1 baseline 非法'
  }
  Assert-Array $f2.migrationChain @('01','02','03','04','04a','05') 'F2 migrationChain'
  foreach ($shaName in @('f1Head','originalF1Head','f1AmendmentHead','f2Head')) {
    Assert-Sha $f2.$shaName 40 "F2.$shaName"
  }
  Assert-Sha $f2.originalF1HandoffSha256 64 'F2.originalF1HandoffSha256'
  Assert-Sha $f2.f1AddendumSha256 64 'F2.f1AddendumSha256'
  if ((Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -cne $f2.originalF1HandoffSha256 -or
      $addendum.originalF1HandoffSha256 -cne $f2.originalF1HandoffSha256 -or
      (Get-FileHash -LiteralPath $addendumFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $f2.f1AddendumSha256) {
    throw 'F2 未绑定 live original F1 handoff/addendum hash'
  }
  Assert-Array $f2.stableServices @('IKnowledgeRoutingService','IKnowledgeSnapshotService') 'F2.stableServices'
  Assert-F2StableDtoSources $f2 $root
}
$targetHead = if ($Baseline -ceq 'F1') { $addendum.amendmentHead } else { $f2.f2Head }
$pendingPhase = if ($Baseline -ceq 'F1') { 'f1-pending' } else { 'f2-pending' }
$completePhase = if ($Baseline -ceq 'F1') { 'f1-rebase' } else { 'f2-rebase' }
$pendingName = if ($Baseline -ceq 'F1') { 'p2-f1-rebase-pending.json' } else { 'p2-f2-rebase-pending.json' }
$gate = Resolve-GitPath 'p2-worktree-gate.ps1'
$gateArgs = @{
  RepoRoot=$root; Phase=$pendingPhase; ExpectedOriginalF1Head=$f1.f1Head
  ExpectedF1Head=$addendum.amendmentHead; AllowedPaths=@(); RequireClean=$true
}
if ($Baseline -ceq 'F2') { $gateArgs.ExpectedF2Head = $f2.f2Head }
if ($Mode -ceq 'abort') {
  $pending = Get-Content -LiteralPath (Resolve-GitPath $pendingName) -Raw -Encoding UTF8 | ConvertFrom-Json
  $active = (Test-Path -LiteralPath (Resolve-GitPath 'rebase-merge')) -or (Test-Path -LiteralPath (Resolve-GitPath 'rebase-apply'))
  if (-not $active) { throw 'abort 要求存在进行中的 rebase' }
  & git rebase --abort
  if ($LASTEXITCODE -ne 0) { throw 'git rebase --abort 失败' }
  $restored = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
  if ($restored -cne $pending.beforeHead -or (& git status --porcelain=v1 -uall).Count -ne 0) { throw 'abort 未恢复 pending beforeHead/clean' }
  'P2_REBASE_ABORTED_OK'
  exit 0
}
if ($Mode -ceq 'start') {
  $pendingResult = & $gate @gateArgs
  if ($LASTEXITCODE -ne 0 -or $pendingResult -cne 'P2_WORKTREE_GATE_OK') { throw "$Baseline pending gate 失败" }
  & git rebase $targetHead
  if ($LASTEXITCODE -ne 0) {
    throw "$Baseline git rebase 冲突：先按 receiving-code-review 核验每个冲突，解决并暂存后执行 git rebase --continue；完成后运行本脚本 -Mode finish。若放弃则运行 -Mode abort"
  }
}
$finishArgs = @{
  RepoRoot=$root; Phase=$completePhase; ExpectedOriginalF1Head=$f1.f1Head
  ExpectedF1Head=$addendum.amendmentHead; AllowedPaths=@(); RequireClean=$true
}
if ($Baseline -ceq 'F2') { $finishArgs.ExpectedF2Head = $f2.f2Head }
$finishResult = & $gate @finishArgs
if ($LASTEXITCODE -ne 0 -or $finishResult -cne 'P2_WORKTREE_GATE_OK') { throw "$Baseline rebase 完成 gate 失败" }
& git merge-base --is-ancestor $f1.f1Head HEAD
if ($LASTEXITCODE -ne 0) { throw 'rebase 后原始 F1 不是 HEAD 祖先' }
if ($Baseline -ceq 'F1') {
  & git merge-base --is-ancestor $addendum.amendmentHead HEAD
  if ($LASTEXITCODE -ne 0) { throw 'F1 rebase 后 amendmentHead 不是 HEAD 祖先' }
}
if ($Baseline -ceq 'F2') {
  & git merge-base --is-ancestor $f2.f2Head HEAD
  if ($LASTEXITCODE -ne 0) { throw 'rebase 后 F2 不是 HEAD 祖先' }
  Assert-P0cTaskContracts $root
  $migrationFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'docs/sql/ai-video/mysql') -Filter '*.sql' -File)
  $forwardMigration = Join-Path $root ('docs/sql/ai-video/mysql/' +
    $addendum.schemaAddendum.forwardMigration)
  if (-not (Test-Path -LiteralPath $forwardMigration -PathType Leaf)) {
    throw 'F2 rebase 丢失 P0-C forward migration'
  }
  $migrationText = ($migrationFiles | ForEach-Object {
    Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8
  }) -join "`n"
  foreach ($schemaToken in @('av_ai_task_group_member','idx_av_ai_task_active_group')) {
    if ($migrationText.IndexOf($schemaToken,[StringComparison]::Ordinal) -lt 0) {
      throw "F2 rebase 丢失 F1 schema addendum：$schemaToken"
    }
  }
}
'P2_REBASE_BASELINE_OK'
```

四份 rebase 记录 `p2-f1-rebase-pending.json`、`p2-f1-rebase.json`、`p2-f2-rebase-pending.json`、`p2-f2-rebase.json` 与 `final` 记录都不可变：同 payload 只回读，不同 payload 拒绝覆盖。冲突时执行器 fail-closed 保留 Git rebase 状态；开发者必须先按 `receiving-code-review` 核验冲突，解决并暂存后真实执行 `git rebase --continue`，直到成功，再运行执行器 `-Mode finish`。放弃时只允许执行器 `-Mode abort` 调用 `git rebase --abort` 并核对恢复到 pending `beforeHead`；不得跳过 F1/F2 pending 直接 final。

### 5.2 统一 JVM fresh evidence gate

Task 1 把下列脚本写到 `git rev-parse --git-path p2-jvm-evidence-gate.ps1`。Maven 命令必须先删除目标精确 XML、记录 UTC start、执行精确 selector，再把 exact report、suite FQCN、RED/GREEN 传给 gate；编译失败、selector 拼错、报告缺失/过期/重复均不能冒充测试证据。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [string[]] $AllowedPaths = @(),
  [Parameter(Mandatory = $true)][string] $ReportRelativePath,
  [Parameter(Mandatory = $true)][string] $ExpectedSuite,
  [Parameter(Mandatory = $true)][ValidateSet('RED','GREEN')][string] $Phase,
  [Parameter(Mandatory = $true)][DateTimeOffset] $StartedAtUtc,
  [switch] $SelfTest
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'JVM gate 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'JVM gate RepoRoot 不准确' }
Set-Location -LiteralPath $root
$worktreeGateText = (& git rev-parse --git-path 'p2-worktree-gate.ps1').Trim()
$worktreeGate = if ([IO.Path]::IsPathRooted($worktreeGateText)) { $worktreeGateText } else { Join-Path $root $worktreeGateText }
if ((& $worktreeGate -RepoRoot $root -Phase inspect -AllowedPaths $AllowedPaths) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'JVM gate 未通过统一 worktree gate'
}
function Assert-Counts([int] $Tests, [int] $Failures, [int] $Errors, [int] $Skipped, [string] $ExpectedPhase) {
  if ($Tests -le 0 -or $Failures -lt 0 -or $Errors -lt 0 -or $Skipped -lt 0 -or $Skipped -gt $Tests) {
    throw 'JVM 报告计数非法'
  }
  if ($ExpectedPhase -ceq 'RED') {
    if (($Failures + $Errors) -le 0 -or $Skipped -ge $Tests) { throw 'RED 必须有真实执行测试和 failure/error' }
  } elseif ($Failures -ne 0 -or $Errors -ne 0 -or $Skipped -ne 0) {
    throw 'GREEN 必须 failures/errors/skipped 全零'
  }
}
if ($SelfTest) {
  Assert-Counts 2 1 0 0 'RED'
  Assert-Counts 2 0 0 0 'GREEN'
  $rejected = $false
  try { Assert-Counts 0 0 0 0 'GREEN' } catch { $rejected = $true }
  if (-not $rejected) { throw 'JVM gate 零测试自测意外通过' }
  'P2_JVM_EVIDENCE_GATE_SELFTEST_OK'
  exit 0
}
if ($StartedAtUtc.Offset -ne [TimeSpan]::Zero) { throw 'JVM StartedAtUtc 必须是 UTC' }
$relative = $ReportRelativePath.Replace('\','/').Trim()
if ([IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)') { throw 'JVM 报告路径越界' }
$report = [IO.Path]::GetFullPath((Join-Path $root $relative))
if (-not $report.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'JVM 报告不在 worktree'
}
if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw '目标 JVM XML 缺失，selector/编译失败不得冒充 RED' }
$item = Get-Item -LiteralPath $report
if ($item.LastWriteTimeUtc -lt $StartedAtUtc.UtcDateTime) { throw '目标 JVM XML 不是本次生成' }
[xml]$xml = Get-Content -LiteralPath $report -Raw -Encoding UTF8
$suite = $xml.testsuite
if ($null -eq $suite -or [string]$suite.name -cne $ExpectedSuite) { throw 'JVM suite FQCN 与 selector 不一致' }
foreach ($attribute in @('tests','failures','errors','skipped')) {
  if ([string]$suite.$attribute -notmatch '^\d+$') { throw "JVM $attribute 不是非负整数" }
}
Assert-Counts ([int]$suite.tests) ([int]$suite.failures) ([int]$suite.errors) ([int]$suite.skipped) $Phase
'P2_JVM_EVIDENCE_OK'
```

### 5.3 统一 Vitest fresh evidence gate

Task 1 把下列脚本写到 `git rev-parse --git-path p2-vitest-evidence-gate.ps1`。所有前端命令必须使用 `--reporter=json --outputFile=<绝对文件>`；`ReportRelativePath` 和每个 `ExpectedTestFiles` 必须是从仓库根起算的完整相对路径，不能传 Web 根相对 selector。gate 将精确 report 路径追加到调用方传入的 tracked-file `AllowedPaths` 后再调用 worktree gate；至少生成五份 fresh JSON，合计精确覆盖 11 个测试文件 selector。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [string[]] $AllowedPaths = @(),
  [Parameter(Mandatory = $true)][string] $ReportRelativePath,
  [Parameter(Mandatory = $true)][string[]] $ExpectedTestFiles,
  [Parameter(Mandatory = $true)][ValidateSet('RED','GREEN')][string] $Phase,
  [Parameter(Mandatory = $true)][DateTimeOffset] $StartedAtUtc,
  [switch] $SelfTest
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'Vitest gate 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'Vitest gate RepoRoot 不准确' }
Set-Location -LiteralPath $root
$worktreeGateText = (& git rev-parse --git-path 'p2-worktree-gate.ps1').Trim()
$worktreeGate = if ([IO.Path]::IsPathRooted($worktreeGateText)) { $worktreeGateText } else { Join-Path $root $worktreeGateText }
if ((& $worktreeGate -RepoRoot $root -Phase inspect -AllowedPaths $AllowedPaths) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'Vitest gate 未通过统一 worktree gate'
}
function Assert-JsonCount([object] $Value, [string] $Name) {
  if ($Value -isnot [long] -and $Value -isnot [int]) { throw "$Name 必须是 JSON integer" }
  if ([long]$Value -lt 0) { throw "$Name 不得为负数" }
}
if ($SelfTest) {
  Assert-JsonCount ([long]1) 'fixture.total'
  $rejected = $false
  try { Assert-JsonCount '1' 'fixture.total' } catch { $rejected = $true }
  if (-not $rejected) { throw 'Vitest gate 字符串计数自测意外通过' }
  'P2_VITEST_EVIDENCE_GATE_SELFTEST_OK'
  exit 0
}
if ($StartedAtUtc.Offset -ne [TimeSpan]::Zero) { throw 'Vitest StartedAtUtc 必须是 UTC' }
if ($ExpectedTestFiles.Count -eq 0 -or @($ExpectedTestFiles | Sort-Object -Unique).Count -ne $ExpectedTestFiles.Count) {
  throw 'Vitest expected selector 不能为空或重复'
}
$relative = $ReportRelativePath.Replace('\','/').Trim()
if ([IO.Path]::IsPathRooted($relative) -or $relative -match '(^|/)\.\.(/|$)') { throw 'Vitest 报告路径越界' }
$effectiveAllowedPaths = @($AllowedPaths) + $relative
if ((& $worktreeGate -RepoRoot $root -Phase inspect -AllowedPaths $effectiveAllowedPaths) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'Vitest gate 未通过统一 worktree gate'
}
$report = [IO.Path]::GetFullPath((Join-Path $root $relative))
if (-not $report.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
  throw 'Vitest 报告不在 worktree'
}
if (-not (Test-Path -LiteralPath $report -PathType Leaf)) { throw 'Vitest JSON 缺失，selector/编译失败不得冒充 RED' }
if ((Get-Item -LiteralPath $report).LastWriteTimeUtc -lt $StartedAtUtc.UtcDateTime) { throw 'Vitest JSON 不是本次生成' }
$json = Get-Content -LiteralPath $report -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($name in @('numTotalTests','numPassedTests','numFailedTests','numPendingTests')) {
  if ($json.PSObject.Properties.Name -cnotcontains $name) { throw "Vitest JSON 缺字段 $name" }
  Assert-JsonCount $json.$name $name
}
if ([long]$json.numTotalTests -le 0) { throw 'Vitest 总测试数必须大于 0' }
if ([long]$json.numTotalTests -ne
    ([long]$json.numPassedTests + [long]$json.numFailedTests + [long]$json.numPendingTests)) {
  throw 'Vitest 测试计数不守恒'
}
if ($Phase -ceq 'RED') {
  if ([long]$json.numFailedTests -le 0) { throw 'Vitest RED 必须有真实失败测试' }
} elseif ([long]$json.numFailedTests -ne 0 -or [long]$json.numPendingTests -ne 0 -or
          [long]$json.numPassedTests -ne [long]$json.numTotalTests) {
  throw 'Vitest GREEN 计数无效'
}
$reportedRaw = @($json.testResults | ForEach-Object {
  if ($_.name -isnot [string]) { throw 'Vitest testResults.name 必须是 string' }
  $candidatePath = [IO.Path]::GetFullPath($_.name)
  if (-not $candidatePath.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Vitest testResults 路径越界'
  }
  $candidate = $candidatePath.Replace('\','/')
  $candidate
})
$reported = @($reportedRaw | Sort-Object -Unique)
if ($reported.Count -ne $reportedRaw.Count) { throw 'Vitest testResults 文件重复' }
$expected = @($ExpectedTestFiles | ForEach-Object {
  [IO.Path]::GetFullPath((Join-Path $root $_)).Replace('\','/')
} | Sort-Object -Unique)
if (Compare-Object $expected $reported -CaseSensitive) { throw 'Vitest JSON 目标文件与 selector 不一致' }
'P2_VITEST_EVIDENCE_OK'
```

### 5.4 统一 evidence manifest gate

Task 1 把下列脚本写到 `git rev-parse --git-path p2-evidence-manifest-gate.ps1`。调用方以 JSON 数组传入 `{pathScope,relativePath}`；gate 实时冻结 SHA-256、字节数和 mtime，限制 acceptance window，并以 CreateNew/幂等回读生成 manifest。不同 payload、路径越界、旧 artifact、hash/bytes/mtime 变化全部 fail-closed。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [string[]] $AllowedPaths = @(),
  [Parameter(Mandatory = $true)][string] $ManifestRelativePath,
  [Parameter(Mandatory = $true)][ValidateSet('unit','it','migration','vitest','standards','scan')][string] $Kind,
  [Parameter(Mandatory = $true)][string] $ArtifactsJson,
  [Parameter(Mandatory = $true)][DateTimeOffset] $WindowStartUtc,
  [Parameter(Mandatory = $true)][DateTimeOffset] $WindowEndUtc,
  [ValidateSet('create','verify','selftest')][string] $Mode = 'verify'
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'manifest gate 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'manifest gate RepoRoot 不准确' }
Set-Location -LiteralPath $root
$worktreeGateText = (& git rev-parse --git-path 'p2-worktree-gate.ps1').Trim()
$worktreeGate = if ([IO.Path]::IsPathRooted($worktreeGateText)) { $worktreeGateText } else { Join-Path $root $worktreeGateText }
if ((& $worktreeGate -RepoRoot $root -Phase inspect -AllowedPaths $AllowedPaths) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'manifest gate 未通过统一 worktree gate'
}
$gitDirText = (& git rev-parse --git-dir).Trim()
$gitMetadataRoot = if ([IO.Path]::IsPathRooted($gitDirText)) {
  [IO.Path]::GetFullPath($gitDirText)
} else {
  [IO.Path]::GetFullPath((Join-Path $root $gitDirText))
}
function Resolve-ScopedPath([string] $Scope, [object] $RelativePath) {
  if ($RelativePath -isnot [string] -or [string]::IsNullOrWhiteSpace($RelativePath) -or
      [IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '(^|[\\/])\.\.([\\/]|$)') {
    throw 'artifact relativePath 类型或范围非法'
  }
  $scopeRoot = switch ($Scope) {
    'worktree' { $root }
    'git-metadata' { $gitMetadataRoot }
    default { throw 'artifact pathScope 只允许 worktree/git-metadata' }
  }
  $full = [IO.Path]::GetFullPath((Join-Path $scopeRoot $RelativePath))
  if (-not $full.StartsWith($scopeRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'artifact 路径逃逸 scope'
  }
  return $full
}
function Assert-ExactFields([object] $Value, [string[]] $Expected, [string] $Name) {
  $actual = @($Value.PSObject.Properties.Name)
  if ($actual.Count -ne $Expected.Count -or @($actual | Where-Object { $Expected -cnotcontains $_ }).Count -ne 0) {
    throw "$Name 字段集合漂移"
  }
}
function Assert-ArtifactBytes([object] $Value) {
  if (($Value -isnot [long] -and $Value -isnot [int]) -or [long]$Value -lt 0) {
    throw 'manifest artifact bytes 必须是非负 JSON integer'
  }
}
if ($Mode -ceq 'selftest') {
  $rejected = $false
  try { [void](Resolve-ScopedPath 'worktree' '../escape') } catch { $rejected = $true }
  if (-not $rejected) { throw 'manifest 路径逃逸自测意外通过' }
  $rejected = $false
  try { [void](Resolve-ScopedPath 'worktree' 123) } catch { $rejected = $true }
  if (-not $rejected) { throw 'manifest JSON 类型自测意外通过' }
  Assert-ArtifactBytes (('{"bytes":7}' | ConvertFrom-Json).bytes)
  foreach ($invalid in @(-1,1.5,'7')) {
    $rejected = $false
    try { Assert-ArtifactBytes $invalid } catch { $rejected = $true }
    if (-not $rejected) { throw "manifest bytes 非法值自测意外通过：$invalid" }
  }
  'P2_EVIDENCE_MANIFEST_GATE_SELFTEST_OK'
  exit 0
}
if ($WindowStartUtc.Offset -ne [TimeSpan]::Zero -or $WindowEndUtc.Offset -ne [TimeSpan]::Zero -or
    $WindowEndUtc -lt $WindowStartUtc) { throw 'acceptance window 必须是有效 UTC 区间' }
$inputArtifacts = @($ArtifactsJson | ConvertFrom-Json)
if ($inputArtifacts.Count -eq 0) { throw 'manifest artifacts 不能为空' }
$artifacts = @()
$seenArtifacts = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($artifact in $inputArtifacts) {
  Assert-ExactFields $artifact @('pathScope','relativePath') 'artifact input'
  if ($artifact.pathScope -isnot [string]) { throw 'artifact pathScope 必须是 JSON string' }
  $artifactKey = "$($artifact.pathScope):$($artifact.relativePath.Replace('\','/'))"
  if (-not $seenArtifacts.Add($artifactKey)) { throw "manifest artifact 重复：$artifactKey" }
  $file = Resolve-ScopedPath $artifact.pathScope $artifact.relativePath
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "artifact 不存在：$($artifact.relativePath)" }
  $item = Get-Item -LiteralPath $file
  if ($item.LastWriteTimeUtc -lt $WindowStartUtc.UtcDateTime -or $item.LastWriteTimeUtc -gt $WindowEndUtc.UtcDateTime) {
    throw "artifact 不在 acceptance window：$($artifact.relativePath)"
  }
  $artifacts += [ordered]@{
    pathScope = $artifact.pathScope
    relativePath = $artifact.relativePath.Replace('\','/')
    sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    bytes = [long]$item.Length
    lastWriteUtc = $item.LastWriteTimeUtc.ToString('o')
  }
}
$core = [ordered]@{
  schemaVersion = 'p2-evidence-manifest-1'
  kind = $Kind
  windowStartUtc = $WindowStartUtc.ToString('o')
  windowEndUtc = $WindowEndUtc.ToString('o')
  artifacts = @($artifacts | Sort-Object pathScope,relativePath)
}
$manifest = Resolve-ScopedPath 'git-metadata' $ManifestRelativePath
$coreJson = $core | ConvertTo-Json -Depth 8 -Compress
if (Test-Path -LiteralPath $manifest -PathType Leaf) {
  $existing = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-ExactFields $existing @('schemaVersion','kind','windowStartUtc','windowEndUtc','artifacts','capturedAtUtc') 'manifest'
  $existingCore = [ordered]@{
    schemaVersion=$existing.schemaVersion; kind=$existing.kind
    windowStartUtc=$existing.windowStartUtc; windowEndUtc=$existing.windowEndUtc
    artifacts=@($existing.artifacts)
  }
  if (($existingCore | ConvertTo-Json -Depth 8 -Compress) -cne $coreJson) {
    throw '既有 manifest payload 不同，拒绝覆盖'
  }
} elseif ($Mode -ceq 'create') {
  $document = [ordered]@{}
  foreach ($key in $core.Keys) { $document[$key] = $core[$key] }
  $document.capturedAtUtc = [DateTime]::UtcNow.ToString('o')
  $directory = Split-Path -Parent $manifest
  if (-not (Test-Path -LiteralPath $directory -PathType Container)) { [void](New-Item -ItemType Directory -Path $directory) }
  $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($document | ConvertTo-Json -Depth 8 -Compress))
  $stream = [IO.File]::Open($manifest, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
  try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
} else {
  throw 'verify 模式要求 manifest 已存在'
}
$verified = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($artifact in $verified.artifacts) {
  $file = Resolve-ScopedPath $artifact.pathScope $artifact.relativePath
  $item = Get-Item -LiteralPath $file
  Assert-ArtifactBytes $artifact.bytes
  if ($artifact.sha256 -isnot [string] -or $artifact.sha256 -cnotmatch '^[0-9a-f]{64}$' -or
      [long]$artifact.bytes -ne $item.Length -or
      (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() -cne $artifact.sha256 -or
      ([DateTimeOffset]::Parse($artifact.lastWriteUtc)).UtcDateTime.Ticks -ne $item.LastWriteTimeUtc.Ticks) {
    throw "artifact hash/bytes/mtime 漂移：$($artifact.relativePath)"
  }
}
'P2_EVIDENCE_MANIFEST_OK'
```

### 5.5 精确测试注册表

13 张任务卡只能从下表选择 selector；最终门禁要求 Java 精确 `20/20`（Surefire `14/14`、Failsafe `6/6`），本机 IT profile `6/6`，Vitest 文件 `11/11` 且至少五份 fresh JSON。所有 Java 类必须 `@Tag("dev")`；六个 `*IT` 必须调用 `LocalIntegrationEnvironment.requireFromEnvironment()`。

| # | 模块 | selector / 精确报告 | 类型 |
|---:|---|---|---|
| 1 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest.xml` | Surefire |
| 2 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest.xml` | Surefire |
| 3 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest.xml` | Surefire |
| 4 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest.xml` | Surefire |
| 5 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest.xml` | Surefire |
| 6 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest.xml` | Surefire |
| 7 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest.xml` | Surefire |
| 8 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest.xml` | Surefire |
| 9 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest.xml` | Surefire |
| 10 | `:ai-video-infra` | `org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest.xml` | Surefire |
| 11 | `:ai-video-infra` | `org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest.xml` | Surefire |
| 12 | `:ai-video-infra` | `org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest.xml` | Surefire |
| 13 | `:ai-video-user` | `org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest` / `ai-video-api/ruoyi-modules/ai-video/ai-video-user/target/surefire-reports/TEST-org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest.xml` | Surefire |
| 14 | `:ruoyi-admin` | `org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest` / `ai-video-api/ruoyi-admin/target/surefire-reports/TEST-org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest.xml` | Surefire，仅平台隔离负测 |
| 15 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT.xml` | Failsafe |
| 16 | `:ai-video-core` | `org.dromara.aivideo.questionnaire.QuestionnaireBranchIT` / `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireBranchIT.xml` | Failsafe |
| 17 | `:ai-video-infra` | `org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT` / `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT.xml` | Failsafe |
| 18 | `:ai-video-infra` | `org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT` / `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT.xml` | Failsafe |
| 19 | `:ai-video-user-api` | `org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT` / `ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT.xml` | Failsafe，用户启动装配 |
| 20 | `:ai-video-user-api` | `org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT` / `ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT.xml` | Failsafe，用户 E2E |

Vitest 精确文件：`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx`。

### 5.6 API + Mock + 组件三重状态矩阵

下表每行都必须同时存在真实 API 契约断言、`mock/aivideo-studio.ts` 命名 fixture 和组件/Hook 行为测试；不得只测快照或把多行合并成一个“状态覆盖”用例。

| 状态 | `api.test.ts` / Mock fixture | 精确组件或 Hook 测试方法 |
|---|---|---|
| 登录恢复 | `mapsAuthRestoring` / `authRestoring` | `DemandStep.test.tsx#restoresAuthenticatedStudioBeforeRenderingQuestionnaire` |
| 工作区切换 | `mapsWorkspaceSwitch` / `workspaceSwitched` | `useStudioDraft.test.tsx#clearsPreviousWorkspaceCacheOnSwitch` |
| 初始加载 | `mapsLoadingSnapshot` / `snapshotLoading` | `DemandStep.test.tsx#rendersLoadingWithoutStaleContent` |
| 初始空 | `mapsInitialEmpty` / `initialEmpty` | `DemandStep.test.tsx#rendersInitialEmptyState` |
| 方向目录空 | `mapsDirectionEmpty` / `directionEmpty` | `DirectionForm.test.tsx#rendersPublishedDirectionEmptyState` |
| 一般请求失败 | `mapsRequestFailure` / `requestFailed` | `DemandStep.test.tsx#rendersSafeRequestFailure` |
| 403 权限不足 | `mapsForbidden` / `forbidden` | `DemandStep.test.tsx#rendersPermissionDeniedWithoutRetryLoop` |
| 额度不足 | `mapsQuotaInsufficient` / `quotaInsufficient` | `GenerationCostConfirm.test.tsx#preservesSavedAnswerWhenQuotaIsInsufficient` |
| 费率重确认 | `mapsTariffChanged` / `tariffChanged` | `GenerationCostConfirm.test.tsx#requiresExplicitTariffReconfirmation` |
| `pending/queued` | `mapsQueuedTask` / `taskQueued` | `TaskProgressPanel.test.tsx#rendersQueuedTask` |
| `running` | `mapsRunningTask` / `taskRunning` | `TaskProgressPanel.test.tsx#rendersRunningProgress` |
| `success` | `mapsSuccessfulTask` / `taskSuccess` | `useQuestionnaireTask.test.tsx#invalidatesExactContextAfterSuccess` |
| `failed` | `mapsFailedTask` / `taskFailed` | `TaskProgressPanel.test.tsx#rendersFailedTaskWithSafeMessage` |
| `cancelled` | `mapsCancelledTask` / `taskCancelled` | `TaskProgressPanel.test.tsx#rendersCancelledAsNeutralNotFailure` |
| 过期任务 | `mapsExpiredTask` / `taskExpired` | `useQuestionnaireTask.test.tsx#stopsAndOffersExplicitRestartForExpiredTask` |
| draft/branch 修订冲突 | `mapsRevisionConflict` / `revisionConflict` | `DemandStep.test.tsx#refreshesAfterRevisionConflictWithoutOverwritingInput` |
| 相同答案复用 | `mapsReusedAnswer` / `answerReused` | `AdaptiveQuestionCard.test.tsx#showsReusedAnswerWithoutCreatingTask` |
| 改答分支警告 | `mapsAnswerForkWarning` / `answerForkWarning` | `AdaptiveQuestionCard.test.tsx#requiresConfirmationBeforeForkingChangedAnswer` |
| 九项补充 | `mapsSupplementRequired` / `supplementRequired` | `SupplementFields.test.tsx#rendersAllNineDeterministicFields` |
| tone 对象契约 | `mapsToneStyleObjects` / `toneStyleObjects` | `SupplementFields.test.tsx#usesSortedToneObjectsAndOneCustomTone` |
| 证据初始空 | `mapsEvidenceEmpty` / `evidenceEmpty` | `EvidenceReviewPanel.test.tsx#rendersEvidenceEmptyState` |
| 证据请求失败 | `mapsEvidenceFailure` / `evidenceFailed` | `EvidenceReviewPanel.test.tsx#offersExplicitRetryAfterEvidenceFailure` |
| 证据冲突 | `mapsEvidenceConflict` / `evidenceConflicted` | `EvidenceReviewPanel.test.tsx#disablesAcceptForConflictedFact` |
| 提交防重 | `mapsDuplicateSubmission` / `duplicateSubmission` | `AdaptiveQuestionCard.test.tsx#submitsOnlyOnceOnRapidClicks` |
| 收费检索 `success/no_results` | `mapsEvidenceSearchNoResults` / `noResultsEvidence` | `EvidenceReviewPanel.test.tsx#rendersSearchNoResultsSeparatelyFromInitialEmpty` |
| 证据分页 | `mapsEvidencePage` / `evidencePageTwo` | `EvidenceReviewPanel.test.tsx#changesEvidencePageWithStableQueryKey` |
| 网络超时 | `mapsNetworkTimeout` / `networkTimeout` | `useQuestionnaireTask.test.tsx#stopsAfterThreeTimeoutsAndWaitsForUserRetry` |
| 5xx | `mapsServerError` / `serverError` | `useStudioDraft.test.tsx#retriesFiveHundredOnlyAfterUserAction` |
| 401 | `mapsUnauthorized` / `unauthorized` | `useStudioDraft.test.tsx#logsOutExactlyOnceOnUnauthorized` |
| 取消非失败 | `mapsCancelledNeutral` / `cancelledNeutral` | `TaskProgressPanel.test.tsx#doesNotRenderFailureActionsForCancelledTask` |
| 版本冲突 | `mapsVersionConflict` / `versionConflict` | `DemandStep.test.tsx#blocksWriteAndReloadsOnVersionConflict` |
| 上下文过期 | `mapsContextExpired` / `contextExpired` | `DemandStep.test.tsx#discardsStaleContextAndRequiresRefresh` |
| 危险操作二次确认 | `mapsDestructiveConfirmation` / `destructiveConfirmation` | `EvidenceReviewPanel.test.tsx#confirmsBatchDecisionReplacementTwice` |
| 失败后用户重试 | `mapsRetryableFailure` / `retryableFailure` | `TaskProgressPanel.test.tsx#retriesOnlyAfterExplicitUserAction` |
| 推荐项仅标记 | `mapsRecommendedOptions` / `recommendedOptions` | `AdaptiveQuestionCard.test.tsx#marksRecommendationWithoutAutoSelectionOrSubmit` |
| 自定义答案 500 字 | `mapsCustomAnswerBoundary` / `customAnswer500` | `AdaptiveQuestionCard.test.tsx#acceptsFiveHundredCodePointsAndRejectsFiveHundredOne` |

此外，Mock 与真实 API 都要覆盖保存答案成功但 `nextAction=resolve_quota`、保存答案成功但 `nextAction=reconfirm_tariff`，并断言 `answerSaved=true` 与保存后的 `draftRevision/branchRevision/generationContextRevision` 不丢失。

## 6. 十三张最小可执行任务卡

### 任务 1：冻结 P2 契约、门禁和独立切片

**文件：** 修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md`、`ai-video-ui/ai-video-webapp/PRD.md`、`.gitignore`（只追加 `ai-video-ui/ai-video-webapp/.vitest-evidence/`）、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/common/error/AiVideoErrorCode.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionnaireContextService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IEvidenceReviewService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireContextDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireAnswerRevisionDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionnaireSupplementRevisionDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/EvidenceReviewContextDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/AcceptedEvidenceFactDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/EvidenceDecisionRevisionDTO.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/QuestionnaireStableContractTest.java`。

**最小任务卡：**

- **单一目标／不做：** 冻结跨阶段契约、F0–F3 和四个集中 gate；不实现问卷业务、不执行 `06`、不真实外调。
- **权威源：** 第 1 节全部来源，重点是原业务规格、reconciliation P2/Task 6、P0-C/P1 精确契约。
- **治理等级／触发项：** 红色；触发共享契约、任务/额度、安全、跨阶段 DTO、Git 门禁；必须双人检查点。
- **实施者／reviewer／并发：** 开发 C 唯一 writer，开发 B 独立 reviewer，同一任务最多 2 人。
- **精确路径／数据范围：** 只允许本卡“文件”行列出的完整仓库相对路径，以及由 `git rev-parse --git-path` 解析的 `p2-worktree-gate.ps1`、`p2-jvm-evidence-gate.ps1`、`p2-vitest-evidence-gate.ps1`、`p2-evidence-manifest-gate.ps1`、`p2-rebase-baseline.ps1`；`.gitignore` 只允许追加一条精确目录规则，不接触业务表或真实数据。
- **允许影响：** 可改变 P2 公共文档与编译期契约；不得改变 P0-C/P1 文件、P4、SQL、前端根页面。
- **前置／退出：** F0 已可追溯；退出时四个 gate 自测、契约 RED/GREEN、标准脚本和零漂移扫描通过。
- **结构签名检查点：** reviewer 逐组件核对两个接口（含 `lockCurrentContextForGeneration`）、六 record header（含 context 三 hash 与答案 identity/context）、Task 1 gate 参数/返回 sentinel，确认脚本 UTF-8 无 BOM、LF、AST 0 error。
- **GREEN 独立复跑检查点：** reviewer 从 clean linked `codex/*` worktree 独立运行四个 `-SelfTest/-Mode selftest` 和契约 GREEN，不复用 writer 控制台摘要。
- **正向／反向验收：** 正向命中精确接口/字段/排序/不可变；反向拒绝额外字段、primitive/string 冒充、旧核心路径、P1 数据访问、越界 worktree、旧/缺报告和 manifest payload 覆盖。
- **统一 gate：** 先落 Section 5.1–5.4，随后 13/13 任务每条命令都要求 `P2_WORKTREE_GATE_OK`；本任务 bootstrap 自测分别要求四个 `*_SELFTEST_OK`。
- **准确命令／证据：** 模块 `:ai-video-core`；selector `org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest`；报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest.xml`；Maven 参数 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest' test`；RED/GREEN 分别调用 JVM gate。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：完整读取 `.agents/skills/ruoyi-plus-ai-coding/SKILL.md`、`references/backend.md`、generator 的 domain/service/serviceImpl/mapper 模板及最相似 P0-C/P1 Service；记录采用项。
- [ ] gate bootstrap：按 Section 5.1–5.4 原样创建四个 gate 和 `p2-rebase-baseline.ps1`；分别运行 selftest，随后用 PowerShell Parser 对 native/LF/CRLF 三种内存文本 `ParseInput`，均要求 0 error。先严格读取不可覆盖的 `p0c-f1-contract-addendum.json` 并证明 original F1 是 amendment 祖先，再以 amendmentHead 运行 rebase 执行器 `-Baseline F1 -Mode start`，不得等到 Task 13。
- [ ] RED：写 `QuestionnaireStableContractTest`，反射精确断言两个 P2 接口、`lockCurrentContextForGeneration(Long,Long)` 返回类型、六个 record 组件、上游 `IAiTaskService` 两个补丁签名以及 identity/context 双 JSON；context header 必须含 `questionnaireHash/knowledgeContextHash/generationInputHash`，answer 金丝雀证明 context 文本变化不改变 hash，补充金丝雀保持 tone 对象。删除 exact XML，记录 UTC start，执行准确命令；Maven 必须非零且 JVM gate `-Phase RED` 通过，否则不算 RED。
- [ ] GREEN：创建接口/DTO，DTO 构造器执行非空、正 ID、修订非负、List defensive copy、排序和 fact 集合一致校验；补齐五份公共文档；向根 `.gitignore` 只追加 `ai-video-ui/ai-video-webapp/.vitest-evidence/`，并用 `git check-ignore -v ai-video-ui/ai-video-webapp/.vitest-evidence/p2-canary.json` 证明 report 不污染 final clean worktree。
- [ ] GREEN 验证：重新删除 exact XML/记录 start，执行同一 selector，Maven 必须 0 且 JVM gate `-Phase GREEN` 返回 `P2_JVM_EVIDENCE_OK`；运行 `$repoRoot = (& git rev-parse --show-toplevel).Trim(); powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/validate-development-standards.ps1')`。
- [ ] review/提交：完成两个检查点后只暂存本任务允许文件，提交 `feat(questionnaire): freeze P2 context contracts`；gate metadata 不暂存。

可复制 RED fixture 与最小 header（RED 必须因 `NoSuchMethodException` 或 record header 不匹配，而不是零测试）：

```java
@Tag("dev")
class QuestionnaireStableContractTest {
    @Test
    void freezesUpstreamGuardsAndAnswerIdentityContextHeader() throws Exception {
        assertThat(IAiTaskService.class.getMethod(
            "requireGenerationContextWritable", Long.class, Long.class)).isNotNull();
        assertThat(IAiTaskService.class.getMethod(
            "inheritQuestionnaireTaskGroupMembers",
            Long.class, Long.class, Long.class, List.class, TaskInitiatorDTO.class)).isNotNull();
        assertThat(IQuestionnaireContextService.class.getMethod(
            "lockCurrentContextForGeneration", Long.class, Long.class).getReturnType())
            .isEqualTo(QuestionnaireContextDTO.class);
        assertThat(Arrays.stream(QuestionnaireAnswerRevisionDTO.class
            .getRecordComponents()).map(RecordComponent::getName)).containsExactly(
                "questionId", "questionNo", "targetSlotCode", "questionHash",
                "answerRevisionId", "answerRevision", "answerHash",
                "answerIdentityJson", "answerContextJson");
        assertThat(Arrays.stream(QuestionnaireContextDTO.class
            .getRecordComponents()).map(RecordComponent::getName)).containsExactly(
                "draftId", "currentBranchId", "branchRevision",
                "generationContextRevision", "questionnaireHash", "knowledgeContextHash",
                "generationInputHash", "answerRevisions", "supplementRevision", "contextReady");
    }
}

public interface IQuestionnaireContextService {
    QuestionnaireContextDTO getCurrentContext(Long draftId, Long branchId);
    QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId, Long branchId);
}

public record QuestionnaireAnswerRevisionDTO(
    Long questionId, Integer questionNo, String targetSlotCode,
    String questionHash, Long answerRevisionId, Long answerRevision,
    String answerHash, String answerIdentityJson, String answerContextJson) {}

public record QuestionnaireContextDTO(
    Long draftId, Long currentBranchId, Long branchRevision,
    Long generationContextRevision, String questionnaireHash,
    String knowledgeContextHash, String generationInputHash,
    List<QuestionnaireAnswerRevisionDTO> answerRevisions,
    QuestionnaireSupplementRevisionDTO supplementRevision,
    boolean contextReady) {}
```

### 任务 2：建立 `06` 迁移、贫血 Entity 和 Mapper

**文件：** 创建 `docs/sql/ai-video/mysql/20260728_06_p2_questionnaire.sql`；在 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/domain/` 创建 `ScriptDirectionRevision.java`、`ScriptQuestion.java`、`ScriptAnswerRevision.java`、`ScriptBranchQuestion.java`、`ScriptSupplementRevision.java`、`EvidenceBatch.java`、`EvidenceSource.java`、`EvidenceFact.java`、`EvidenceFactDecision.java`、`ScriptBranchEvidenceDecision.java`、`QuestionnaireExecutionInput.java`、`QuestionSlotCode.java`、`QuestionnaireNextAction.java`、`EvidenceDecisionStatus.java`；在 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/mapper/` 创建 `ScriptDirectionRevisionMapper.java`、`ScriptQuestionMapper.java`、`ScriptAnswerRevisionMapper.java`、`ScriptBranchQuestionMapper.java`、`ScriptSupplementRevisionMapper.java`、`EvidenceBatchMapper.java`、`EvidenceSourceMapper.java`、`EvidenceFactMapper.java`、`EvidenceFactDecisionMapper.java`、`ScriptBranchEvidenceDecisionMapper.java`、`QuestionnaireExecutionInputMapper.java`；复杂查询只创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/questionnaire/ScriptBranchQuestionMapper.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/questionnaire/ScriptBranchEvidenceDecisionMapper.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/resources/mapper/aivideo/questionnaire/QuestionnaireExecutionInputMapper.xml`；修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/QuestionnaireMigrationIT.java`。

**最小任务卡：**

- **单一目标／不做：** 建立 P2 自有迁移、贫血 Entity、Mapper 和约束；不实现 Service/UI，不重复创建 P0-C 表。
- **权威源：** 原业务规格数据模型、master 迁移顺序、reconciliation RuoYi 边界、generator Mapper/Entity 模板。
- **治理等级／触发项：** 红色；触发共享数据库、修订并发、权限种子、F1 所有权移交和不可逆数据约束。
- **实施者／reviewer／并发：** 开发 C writer，开发 B 数据/并发 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅上述 SQL、14 个 domain、11 个 Mapper、其 XML、三个已移交 studio 文件和一个 IT；P0-C addendum/handoff 只读；只允许本机 `ai_video_test` 与隔离 Redis 前缀。
- **允许影响：** 可新增 P2 表/索引/权限并扩展 P0-C 分支字段；禁止更新 P0-C/P1 表语义、`ry_vue.sql`、开发/生产库。
- **前置／退出：** `p0c-f1-handoff.json fullF1Ready=true`、`p0c-f1-contract-addendum.json` 严格通过、六共享文件 target=P2，且 rebase 执行器已完成 `f1-pending→git rebase <amendmentHead>→f1-rebase`；退出为 `01→02→03→04→04a→05→06` + replay `06` 与约束 IT GREEN。
- **结构签名检查点：** reviewer 核对 11 张表逐列 DDL、全部 Entity 不继承 `BaseEntity`、枚举在 domain 根、Mapper 继承 `BaseMapperPlus`、`ScriptBranchEvidenceDecisionMapper`/XML 精确存在、scope/creator、唯一/FK/owner 索引/CHECK 与迁移一致。
- **GREEN 独立复跑检查点：** reviewer 使用独立 runId 和相同安全 profile 从空库执行测试，不接受 writer 已有库或日志。
- **正向／反向验收：** 正向建表、完整链、二次 `06`、唯一来源任务/决定修订；反向拒绝跨 workspace、重复 P0-C 表、重复权限种子、脏迁移和非本机数据源。
- **统一 gate：** 运行 Task 1 worktree gate，`ExpectedF1Head` 来自严格 handoff，allowed paths 精确为本任务清单；RED/GREEN 都调用 JVM evidence gate。
- **准确命令／证据：** 模块 `:ai-video-core`；selector `org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT`；profile `'-Pdev,local-integration-test'`；报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT.xml`；命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT' '-Pdev,local-integration-test' verify`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：再次读取 RuoYi skill/backend reference、generator domain/mapper/XML 模板和最近似 P0-C 实体/Mapper；IT 类添加 `@Tag("dev")` 并首步调用 `LocalIntegrationEnvironment.requireFromEnvironment()`。
- [ ] RED：唯一持久化 IT 为 `QuestionnaireMigrationIT.appliesMigrationsOneThroughSixAndReplaysP2Migration`，同一 selector 覆盖 `01→02→03→04→04a→05→06→06`、11 表逐列 metadata、P0-C member/active index、全部 Mapper 装配/约束、跨 workspace 负测、同 revision/来源任务唯一并发、外键、`uk_question_source_task`、事实决定不可变、execution input 摘要冲突 46116 和权限种子；不得另建旧命名持久化 IT。删除 exact Failsafe XML、记录 UTC start，RED 必须由缺少 forward migration/`20260728_06_p2_questionnaire.sql`/表/列断言产生。
- [ ] GREEN：创建文件并执行 migration；Service 之外不写状态机；`06` 只建 P2 对象并扩展分支。
- [ ] GREEN 验证：新 runId 重新执行准确命令，要求 Maven 0、JVM gate GREEN；查询 information_schema 证明每个表/索引/权限种子精确一份。
- [ ] review/提交：独立复跑后只暂存精确文件，提交 `feat(questionnaire): add questionnaire persistence`。

可复制 migration IT 骨架与最小 Mapper 签名：

```java
@Tag("dev")
class QuestionnaireMigrationIT {
    private static final LocalIntegrationEnvironment ENV =
        LocalIntegrationEnvironment.requireFromEnvironment();

    @BeforeEach
    void reset() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        ENV.clearCurrentRunRedisKeys();
    }

    @Test
    void appliesMigrationsOneThroughSixAndReplaysP2Migration() throws Exception {
        Path root = Path.of(System.getProperty("maven.multiModuleProjectDirectory"),
            "docs/sql/ai-video/mysql");
        List<String> files = List.of(
            "20260728_01_p0a_identity_security.sql",
            "20260728_02_p0b_workspace_authorization.sql",
            "20260728_03_p0c_task_quota_direction.sql",
            "20260728_04_p0_seed.sql",
            "20260728_04a_p0c_task_group_guard.sql",
            "20260728_05_p1_knowledge.sql",
            "20260728_06_p2_questionnaire.sql");
        try (Connection connection = ENV.openMySqlConnection()) {
            for (String file : files) {
                ScriptUtils.executeSqlScript(connection,
                    new FileSystemResource(root.resolve(file)));
            }
            ScriptUtils.executeSqlScript(connection,
                new FileSystemResource(root.resolve(files.getLast())));
            assertThat(tableNames(connection)).contains(
                "av_script_direction_revision", "av_script_question",
                "av_script_answer_revision", "av_script_branch_question",
                "av_script_supplement_revision", "av_evidence_batch",
                "av_evidence_source", "av_evidence_fact",
                "av_evidence_fact_decision", "av_script_branch_evidence_decision",
                "av_questionnaire_execution_input");
            assertColumn(connection, "av_script_direction_revision",
                "industry_catalog_version", "bigint", "NO");
            assertColumn(connection, "av_script_answer_revision",
                "answer_identity_json", "json", "NO");
            assertUnique(connection, "av_script_branch", "uk_draft_branch_revision");
            assertForeignKey(connection, "av_script_branch_question",
                "fk_branch_question_answer", "av_script_answer_revision");
        }
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> names = new TreeSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema=DATABASE() AND table_type='BASE TABLE'");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    private static void assertColumn(Connection connection, String table, String column,
                                     String dataType, String nullable) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data_type,is_nullable FROM information_schema.columns " +
                "WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("data_type")).isEqualTo(dataType);
                assertThat(rows.getString("is_nullable")).isEqualTo(nullable);
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private static void assertUnique(Connection connection, String table,
                                     String index) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema=DATABASE() AND table_name=? " +
                "AND index_name=? AND non_unique=0")) {
            statement.setString(1, table);
            statement.setString(2, index);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isGreaterThan(0);
            }
        }
    }

    private static void assertForeignKey(Connection connection, String table,
                                         String constraint, String referencedTable)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT referenced_table_name FROM information_schema.key_column_usage " +
                "WHERE table_schema=DATABASE() AND table_name=? " +
                "AND constraint_name=? AND referenced_table_name IS NOT NULL")) {
            statement.setString(1, table);
            statement.setString(2, constraint);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(referencedTable);
            }
        }
    }
}

public interface QuestionnaireExecutionInputMapper
    extends BaseMapperPlus<QuestionnaireExecutionInput, QuestionnaireExecutionInput> {
    int insertFrozenIfAbsent(QuestionnaireExecutionInput input);
    QuestionnaireExecutionInput selectByRootAndExecution(
        Long rootTaskId, Long executionTaskId);
}
```

### 任务 3：实现答案规范化和稳定哈希

**文件：** 在 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/` 创建 `QuestionIdentityDTO.java`、`NormalizedAnswerDTO.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IAnswerNormalizationService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/impl/AnswerNormalizationServiceImpl.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/AnswerNormalizationServiceTest.java`。

**最小任务卡：**

- **单一目标／不做：** 实现 `answerIdentityJson`、独立 `answerContextJson` 和只基于 identity 的稳定 SHA-256；不读写数据库、不创建任务/额度、不信任客户端问题内容。
- **权威源：** 原业务规格的答案语义、第 1.3 节 exact schema、六 DTO header、Java UTF-8/NFC 约定。
- **治理等级／触发项：** 黄色；触发跨阶段冻结输入、幂等 hash 和用户自由文本，但无数据库/外调。
- **实施者／reviewer／并发：** 开发 C writer，开发 A 算法/隐私 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/QuestionIdentityDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/NormalizedAnswerDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IAnswerNormalizationService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/impl/AnswerNormalizationServiceImpl.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/AnswerNormalizationServiceTest.java`；只处理内存 fixture。
- **允许影响：** 可定义 P2 内部 DTO/Service并按 Task 1 已冻结 header 填充稳定答案 DTO；不得让 core 依赖 HTTP BO、修改公共 hash 工具或日志策略。
- **前置／退出：** F0 可开始；退出时 JSON 字节金丝雀、hash 金丝雀、500 code point 边界和隐私反测 GREEN。
- **结构签名检查点：** reviewer 核对 Service 为 `I...Service/...ServiceImpl`、内部 DTO 位于 `dto`、JSON key 顺序和 `NormalizedAnswerDTO` 只携带规范值。
- **GREEN 独立复跑检查点：** reviewer 删除 exact XML 后独立执行同 selector，并用另一个 Unicode 组合 fixture 重算 SHA。
- **正向／反向验收：** 正向排序/去重/NFC/UTF-8 固定；反向拒绝 `custom` 混入普通代码、未知 option、501 字、自定义未勾选泄漏、默认 Charset、Map 无序序列化。
- **统一 gate：** Task 1 worktree gate allowed paths 精确为五个文件；RED/GREEN 都用 JVM gate。
- **准确命令／证据：** 模块 `:ai-video-core`；selector `org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest`；报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest.xml`；命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest' test`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator service 模板和相似 P1 hash Service；测试类添加 `@Tag("dev")`。
- [ ] RED：固定五键 `answerIdentityJson`、独立 `answerContextJson` 与 SHA 金丝雀；相同 identity、不同 questionText/targetSlot/normalizedValue/slotContributions 必须 hash 相同。删除 exact XML、记录 start；RED 因 `NormalizedAnswerDTO.answerIdentityJson/answerContextJson` symbol 缺失或 hash 断言失败。
- [ ] GREEN：实现规范化，所有字符串 NFC/trim/collapse，数组 code point 排序去重，JSON UTF-8 exact keys；identity 禁止 `questionText/targetSlotCode/normalizedValue/slotContributions`，context 禁止反向参与 hash；禁止废弃答案键、`Map.toString()`、默认 Charset 和区域排序。
- [ ] GREEN 验证：同义排列/Unicode hash 相同，五项 identity 任一变化 hash 不同，context-only 变化 hash 不变；Maven 0 且 JVM gate GREEN。
- [ ] review/提交：独立复跑后提交 `feat(questionnaire): normalize questionnaire answers`。

可复制 RED fixture 与最小实现签名：

```java
@Tag("dev")
class AnswerNormalizationServiceTest {
    private final IAnswerNormalizationService service =
        new AnswerNormalizationServiceImpl(new ObjectMapper());

    @Test
    void hashesOnlyFiveIdentityFieldsAndKeepsContextIndependent() throws Exception {
        String questionHash =
            "9f31dd08e36fca5ad8bb8848ef76e958f8f96f57bf8bd5ea64aa48de56ad2562";
        ScriptQuestion question = question(
            3, questionHash, "这次视频最想传达什么？", "coreMessage", "突出醇香");
        SubmitAnswerCommandDTO input = new SubmitAnswerCommandDTO(
            9L, List.of("option_03", "option_01", "option_03"),
            true, "  咖啡\r\n e\u0301lite  ", 11L, 2L, "q-88-b2-n4-a", 7L);

        NormalizedAnswerDTO first = service.normalize(question, input);
        NormalizedAnswerDTO contextChanged = service.normalize(
            question(4, questionHash, "改写后问题", "callToAction", "立即到店"), input);

        assertThat(first.answerIdentityJson()).isEqualTo(
            "{\"questionId\":\"9\",\"questionHash\":\"9f31dd08e36fca5ad8bb8848ef76e958f8f96f57bf8bd5ea64aa48de56ad2562\"," +
            "\"selectedCodes\":[\"option_01\",\"option_03\"],\"customSelected\":true,\"customText\":\"咖啡 élite\"}");
        assertThat(first.answerHash()).isEqualTo(
            "e1f99e5ac2f55989e059a4caccee90da1f102484bcd1e3b45bc638ea9d24a9b3");
        JsonNode identity = new ObjectMapper().readTree(first.answerIdentityJson());
        List<String> identityKeys = new ArrayList<>();
        identity.fieldNames().forEachRemaining(identityKeys::add);
        assertThat(identityKeys).containsExactly(
            "questionId", "questionHash", "selectedCodes", "customSelected", "customText");
        assertThat(identity.has("questionNo") || identity.has("questionVersionHash") ||
            identity.has("selectedOptionCodes")).isFalse();
        assertThat(contextChanged.answerHash()).isEqualTo(first.answerHash());
        assertThat(contextChanged.answerContextJson()).isNotEqualTo(first.answerContextJson());
    }

    private static ScriptQuestion question(int number, String questionHash,
                                           String text, String targetSlot,
                                           String normalizedValue) {
        ScriptQuestion question = mock(ScriptQuestion.class);
        when(question.getId()).thenReturn(9L);
        when(question.getQuestionNo()).thenReturn(number);
        when(question.getQuestionHash()).thenReturn(questionHash);
        when(question.getQuestionText()).thenReturn(text);
        when(question.getTargetSlotCode()).thenReturn(targetSlot);
        when(question.getOptionsJson()).thenReturn(
            "[{\"code\":\"option_01\",\"label\":\"选项一\",\"normalizedValue\":\"" +
            normalizedValue + "\",\"slotContributions\":[\"coreMessage\"]}," +
            "{\"code\":\"option_03\",\"label\":\"选项三\",\"normalizedValue\":\"精品\"," +
            "\"slotContributions\":[\"audience\"]}]");
        return question;
    }
}

public interface IAnswerNormalizationService {
    NormalizedAnswerDTO normalize(
        ScriptQuestion question, SubmitAnswerCommandDTO command);
}

public record NormalizedAnswerDTO(
    List<String> selectedCodes, boolean customSelected, String customText,
    String answerIdentityJson, String answerContextJson, String answerHash) {}
```

### 任务 4：实现方向、答案复用、改答分支和两阶段推进

**文件：** 在 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/` 创建 `IDirectionRevisionService.java`、`IQuestionTurnService.java`、`IQuestionTurnWriteService.java`、`IQuestionnaireBranchService.java`，在 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/impl/` 创建 `DirectionRevisionServiceImpl.java`、`QuestionTurnServiceImpl.java`、`QuestionTurnWriteServiceImpl.java`、`QuestionnaireBranchServiceImpl.java`；在 `questionnaire/dto/` 创建 `SaveDirectionCommandDTO.java`、`StartQuestionnaireCommandDTO.java`、`SubmitAnswerCommandDTO.java`、`SavedTurnDTO.java`、`QuestionTurnResultDTO.java`、`QuestionnaireAdvanceDTO.java`、`BranchForkResultDTO.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/DirectionRevisionServiceTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/QuestionTurnServiceTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/QuestionnaireBranchIT.java`；修改 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/domain/AvScriptBranch.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptBranchMapper.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/studio/mapper/ScriptDraftMapper.java`。

**最小任务卡：**

- **单一目标／不做：** 实现目录冻结、方向/答案修订、复用、改答分支和保存后两阶段推进；不调用模型/搜索。
- **权威源：** 第 1.2 状态矩阵、P0-B 授权、P0-C 修订/操作槽/额度契约、原业务规格。
- **治理等级／触发项：** 红色；触发工作区归属、乐观并发、分支继承、收费任务前置和六共享文件。
- **实施者／reviewer／并发：** 开发 C writer，开发 B 事务/授权 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行列出的完整仓库相对路径；不得用目录通配符扩张范围，只写当前 workspace/draft/branch。
- **允许影响：** 可修改 P2 修订和 P0-C 分支扩展；禁止修改 P0-C 授权/任务实现、直接写额度、跨租户复制。
- **前置／退出：** 完整 F1 addendum、六文件已移交、`p2-f1-rebase.json` 已严格回读且 target/afterMergeBase 都等于 `amendmentHead`；退出为两单元 + 并发 IT GREEN 及状态矩阵逐格断言。
- **结构签名检查点：** reviewer 核对四个 Service 边界、`QuestionTurnServiceImpl` 无事务而 `QuestionTurnWriteServiceImpl.save` 走独立代理事务、固定 draft→branch 锁序、守卫/继承/audit 顺序、Mapper CAS 和三个目录版本持久字段。
- **GREEN 独立复跑检查点：** reviewer 用第二 runId 并发驱动相同 expected revision，确认唯一胜者和失败者零孤儿写入。
- **正向／反向验收：** 正向覆盖方向首次/同值/替换、答案首次/同 hash/改答、两阶段阻塞、root member 继承与安全审计；反向拒绝目录漂移、未知 code、时长越界、`46103/46123`、跨 workspace、后缀错误继承、审计失败和阻塞时回滚答案。
- **统一 gate：** Task 1 worktree gate，ExpectedF1Head 精确来自 handoff，allowed paths 为本卡清单；三个 selector 各自用 JVM gate。
- **准确命令／证据：** 单元命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest,org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest' test`，报告分别为 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest.xml`；IT 命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.questionnaire.QuestionnaireBranchIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireBranchIT.xml`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator Service/Mapper 模板、P0-C draft/branch 相似实现；三测试类 `@Tag("dev")`，IT 首步 require local environment。
- [ ] RED：按第 1.2 节逐格写测试，包括 aggregate `expectedCatalogVersion` 漂移、同一 published snapshot 派生三个 server-only 子版本、第二次目录读取/客户端子版本字段零容忍、方向自定义 100 字、时长白名单、题号 N 复制/排除、`sourceRootTaskId` 保持、额度/费率阻塞答案已提交；删除三个 exact XML 并分别取得 JVM gate RED。
- [ ] GREEN：Controller 解析的 actor 和 WorkspaceContext 显式传入；写事务固定锁 draft→current branch，调用 `requireGenerationContextWritable` 后才 CAS/写行/继承/audit。同 hash 零写；改答整事务恰一新 branch、复制 `1..N`，再调用上游 inherit；事务提交后由无事务 outer Service 才创建下一收费任务。
- [ ] GREEN 验证：两个单元和 IT 均 Maven 0、三个 JVM gate GREEN；与 P3 create 并发时唯一胜者，46123/46103/审计异常的失败竞争者无 orphan branch/revision/group member/audit/task/operation/quota。
- [ ] review/提交：结构与独立并发复跑通过后提交 `feat(questionnaire): add revisioned questionnaire branches`。

可复制并发失败 fixture 与最小事务边界：

```java
@Tag("dev")
class DirectionRevisionServiceTest {
    @Test
    void derivesServerOnlyVersionsFromOnePublishedAggregateSnapshot() {
        AppActorContext actor = AppActorContext.appUser(1001L);
        assertThat(actor.actorType()).isEqualTo(AppActorType.APP_USER);
        DirectionCatalogSnapshotDTO published = new DirectionCatalogSnapshotDTO(
            20L, "a".repeat(64), 21L, 22L, "2026-07",
            List.of(industry("local_life")),
            Map.of("local_life", List.of(purpose("store_promotion"))),
            durations(30, 45, 60, 90, 120));
        when(catalogService.currentPublishedCatalog()).thenReturn(published,
            publishedWithCatalogVersion(21L));
        SaveDirectionCommandDTO command = new SaveDirectionCommandDTO(
            11L, 2L, 20L, "local_life", null,
            "store_promotion", null, 60);

        service.saveDirection(88L, command, actor);

        verify(catalogService, times(1)).currentPublishedCatalog();
        verify(directionMapper).insert(argThat(row ->
            row.getIndustryCatalogVersion().equals(21L) &&
            row.getPurposeCatalogVersion().equals(22L) &&
            row.getDurationRuleVersion().equals("2026-07")));
        assertThat(Arrays.stream(SaveDirectionCommandDTO.class.getRecordComponents())
            .map(RecordComponent::getName)).containsExactly(
                "draftRevision", "branchRevision", "expectedCatalogVersion",
                "industryCode", "industryCustomText", "purposeCode",
                "purposeCustomText", "targetDurationSeconds");
    }

    @Test
    void rejectsAggregateVersionDriftBeforeWriteAndCannotAcceptSubVersionTampering() {
        when(catalogService.currentPublishedCatalog()).thenReturn(
            publishedWithCatalogVersion(21L));
        SaveDirectionCommandDTO stale = new SaveDirectionCommandDTO(
            11L, 2L, 20L, "local_life", null,
            "store_promotion", null, 60);

        assertThatThrownBy(() -> service.saveDirection(
            88L, stale, AppActorContext.appUser(1001L)))
            .isInstanceOf(AiVideoBusinessException.class)
            .extracting("code").isEqualTo(46122);
        verifyNoInteractions(directionMapper, auditService);
        assertThat(Arrays.stream(SaveDirectionCommandDTO.class.getRecordComponents())
            .map(RecordComponent::getName)).doesNotContain(
                "industryCatalogVersion", "purposeCatalogVersion", "durationRuleVersion");
    }
}

@Tag("dev")
class QuestionTurnServiceTest {
    @Test
    void changedAnswerLocksGuardsForksPrefixInheritsAndAuditsInOrder() {
        AppActorContext actor = AppActorContext.appUser(1001L);
        when(writeService.save(88L, command(), actor)).thenReturn(changedSavedTurn());

        application.submitAnswer(88L, command(), actor);

        InOrder order = inOrder(draftMapper, branchMapper, aiTaskService,
            branchMapper, branchQuestionMapper, answerMapper,
            aiTaskService, auditService);
        order.verify(draftMapper).selectCurrentForUpdate(88L);
        order.verify(branchMapper).selectCurrentForUpdate(88L);
        order.verify(aiTaskService).requireGenerationContextWritable(88L, 2L);
        order.verify(branchMapper).insert(argThat(b -> b.getBranchRevision() == 3L));
        order.verify(branchQuestionMapper).copyPrefix(88L, 700L, 701L, 3, 1001L);
        order.verify(answerMapper).insert(argThat(a -> a.getAnswerIdentityJson() != null));
        order.verify(aiTaskService).inheritQuestionnaireTaskGroupMembers(
            88L, 2L, 3L, List.of(501L, 502L, 503L),
            new TaskInitiatorDTO("app_user", 1001L));
        order.verify(auditService).append(argThat(a ->
            a.action().equals("QUESTION_BRANCH_FORKED") &&
            a.actorType() == AppActorType.APP_USER && a.actorId().equals(1001L)));
        verify(orchestrator).createNextQuestion(argThat(c -> c.branchRevision() == 3L));
    }
}

public interface IQuestionTurnWriteService {
    SavedTurnDTO save(
        Long draftId, SubmitAnswerCommandDTO command, AppActorContext actor);
}

@Service
final class QuestionTurnServiceImpl implements IQuestionTurnService {
    // 此类和 submitAnswer 均不加 @Transactional；writeService 必须是另一个 Spring proxy。
    public QuestionTurnResultDTO submitAnswer(
        Long draftId, SubmitAnswerCommandDTO command, AppActorContext actor) {
        SavedTurnDTO saved = writeService.save(draftId, command, actor);
        if (saved.reused() || !saved.needsPaidQuestion()) return snapshot(saved);
        return createOrReturnBlocking(saved, command, actor);
    }
}

@Service
final class QuestionTurnWriteServiceImpl implements IQuestionTurnWriteService {
    @Transactional(rollbackFor = Exception.class)
    public SavedTurnDTO save(
        Long draftId, SubmitAnswerCommandDTO command, AppActorContext actor) {
        // draft FOR UPDATE → current branch FOR UPDATE → exact revisions → P0-C writable guard
        // → CAS/恰一 branch → copy 1..N → answer/member → P0-C inherit → audit → return
        return doSave(draftId, command, actor);
    }
}
```

`QuestionnaireBranchIT` 必须用两个真实线程/两个连接同时驱动 P2 改答与 P3 create：二者都先锁 draft/current branch；P2 在 guard 后写，P3 按 `slot→quota→task/origin→freeze→enqueue`。任一失败均断言零 orphan。方向替换不继承任何 question root；补充/事实分支沿用相同 CAS 模板但继承全部当前题 root。

### 任务 5：实现三至五题完整性策略和九项补充

**文件：** 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionnaireCompletenessService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/ISupplementRevisionService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/impl/QuestionnaireCompletenessServiceImpl.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/impl/SupplementRevisionServiceImpl.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/SaveSupplementCommandDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/ToneStyleDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto/SupplementRevisionResultDTO.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/QuestionnaireCompletenessServiceTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/SupplementRevisionServiceTest.java`。

**最小任务卡：**

- **单一目标／不做：** 实现三至五题完整性策略和 `canonicalSupplementJson` 的免费修订状态机；不调用 provider、任务或额度。
- **权威源：** 原业务规格问卷/补充规则、第 1.2/1.3 节、P3 冻结输入要求。
- **治理等级／触发项：** 红色；触发 generation context、分支继承、内容上限和下游不可变 hash。
- **实施者／reviewer／并发：** 开发 C writer，开发 A 产品规则 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行列出的完整仓库相对路径；只写当前 branch 的 supplement revision。
- **允许影响：** 可新增 P2 Service/实现/测试；禁止引入新业务层、创建收费任务、修改 P1/P3。
- **前置／退出：** 纯策略 F0 可做，持久化 F1 后；退出为两个 exact selector GREEN 和第 1.2/1.3 全矩阵。
- **结构签名检查点：** reviewer 核对两个 `I*Service/*ServiceImpl`、完整性纯私有方法、九字段 key order、三个字符串数组排序去重、`toneStyles` 的 `{code,customText}` 对象结构/最多 5 项/唯一 custom 规则和 16,000 code point 计算。
- **GREEN 独立复跑检查点：** reviewer 使用边界 15,999/16,000/16,001 与 supplement 首次/同 hash/替换/移除 fixture 独立复跑。
- **正向／反向验收：** 正向前三题固定、4/5 首缺口、五题后补充、免费幂等、普通/custom tone 对象稳定排序；反向拒绝少于三题完成、第六题、未知槽、组合超长、`toneStyles` 字符串数组、超过 5 项、重复 code、普通项非 null customText、多个 custom、customText 越界、错误继承事实/候选、同 hash 增修订。
- **统一 gate：** Task 1 worktree gate allowed paths 精确为六个文件；两个 selector 各自调用 JVM gate。
- **准确命令／证据：** 模块 `:ai-video-core`；selectors `org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest,org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest`；报告分别为 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest.xml`；命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest,org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest' test`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator Service 模板和 P1 canonical hash 相似实现；两个测试类 `@Tag("dev")`。
- [ ] RED：覆盖槽位优先级、九字段 exact JSON/hash、三个字符串数组按值排序去重，以及 `toneStyles` 最多 5 个对象、code 排序去重、普通 code 1–100 且 customText=null、唯一 custom 的 customText 1–200；字符串数组、重复 code、两个 custom 和全部边界反例必须 RED。再覆盖首次/同 hash/替换/移除的三修订增量与继承/排除；删除两个 XML、执行准确命令并分别 JVM gate RED。
- [ ] GREEN：实现策略和补充事务，固定锁 draft→current branch→writable guard，保存规范 JSON/hash 后同事务 audit；同 hash 零写；替换/移除整次只建一个 `supplement_changed` 分支并调用 P0-C inherit 传全部当前题 rootIds，不创建任务/额度。
- [ ] GREEN 验证：Maven 0、两个 JVM gate GREEN；所有补充字段最终参与 `generationInputHash`。
- [ ] review/提交：独立边界复跑后提交 `feat(questionnaire): add completeness and supplements`。

可复制数值矩阵 RED fixture 与最小签名：

```java
@Tag("dev")
class SupplementRevisionServiceTest {
    @Test
    void rejectsEveryFrozenBoundaryBeforeLockOrWrite() {
        List<SaveSupplementCommandDTO> invalid = List.of(
            command("x".repeat(2001), List.of("受众"), List.of("核心"), 60,
                "行动", List.of("事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", Collections.nCopies(9, "受众"), List.of("核心"), 60,
                "行动", List.of("事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), Collections.nCopies(9, "核心"), 60,
                "行动", List.of("事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 61,
                "行动", List.of("事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 60,
                "x".repeat(501), List.of("事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 60,
                "行动", Collections.nCopies(11, "事实"), List.of("禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 60,
                "行动", List.of("事实"), Collections.nCopies(11, "禁项"), List.of(), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 60,
                "行动", List.of("事实"), List.of("禁项"),
                Collections.nCopies(6, new ToneStyleDTO("plain", null)), "备注"),
            command("主题", List.of("受众"), List.of("核心"), 60,
                "行动", List.of("事实"), List.of("禁项"), List.of(), "x".repeat(2001)));

        invalid.forEach(command -> assertThatThrownBy(() ->
            service.save(88L, command, AppActorContext.appUser(1001L)))
            .isInstanceOf(ConstraintViolationException.class));
        verifyNoInteractions(draftMapper, branchMapper, aiTaskService, auditService);
    }

    private static SaveSupplementCommandDTO command(
        String subject, List<String> audience, List<String> coreMessages,
        int duration, String cta, List<String> keep, List<String> prohibited,
        List<ToneStyleDTO> tones, String notes) {
        return new SaveSupplementCommandDTO(
            11L, 2L, subject, audience, coreMessages, duration, cta,
            keep, prohibited, tones, notes);
    }
}

public interface ISupplementRevisionService {
    SupplementRevisionResultDTO save(
        Long draftId, SaveSupplementCommandDTO command, AppActorContext actor);
}
```

### 任务 6：实现知识驱动的出题收费任务

**文件：** 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionGenerationService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionGenerationResultService.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IQuestionnaireExecutionInputService.java`、对应 `QuestionGenerationServiceImpl.java`、`QuestionGenerationResultServiceImpl.java`、`QuestionnaireExecutionInputServiceImpl.java`，以及 `questionnaire/dto` 的 `FrozenQuestionInputDTO.java`、`GeneratedQuestionDTO.java`、`FrozenQuestionnaireInputDTO.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/QuestionGenerationOutputValidator.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/QuestionProviderClient.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/listener/QuestionGenerationTaskHandler.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/resources/schema/question-generation-1.schema.json`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/QuestionGenerationServiceTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/questionnaire/provider/QuestionProviderClientTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/questionnaire/provider/QuestionGenerationOutputValidatorTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/questionnaire/listener/QuestionGenerationTaskHandlerIT.java`。

**最小任务卡：**

- **单一目标／不做：** 实现知识驱动的收费出题、精确 P0-C attempt/lease 和结果写入；不做证据检索。
- **权威源：** P0-C 任务/attempt/lease 契约、提交 `eb5aac8a` P1 两 Service/五 DTO、原业务规格问题 Schema、master 代表性硬门禁。
- **治理等级／触发项：** 红色；触发真实 provider 成本、额度、幂等、租约、P1 跨聚合、旧结果丢弃。
- **实施者／reviewer／并发：** 开发 C writer，开发 A provider/成本 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行列出的完整仓库相对路径；冻结输入按 `(rootTaskId,executionTaskId)` 唯一，业务结果只写当前 workspace/draft/branch。
- **允许影响：** 可消费 P0-C/P1 精确契约并新增 question Handler；不得实现 P0-C/P1 类型、读取其 Mapper/表、修改额度或 scanner。
- **前置／退出：** F2 handoff 严格通过，rebase 执行器已完成 `f2-pending→git rebase <f2Head>→f2-rebase`，生产/全部真实 IT P1 fake 清零；退出为四 selector、attempt≤3、lease/stale 全链 GREEN。
- **结构签名检查点：** reviewer 核对两个 core `I*Service/*ServiceImpl`、infra client/listener、`QuestionGenerationOutputValidator` 精确命名、Handler 唯一注册；`QuestionProviderClient` 只注入全局 `org.dromara.aivideo.provider.ModelProvider` 并紧邻调用，不得创建 P2 同义端口；只 import P1 两 Service/五 DTO。
- **GREEN 独立复跑检查点：** reviewer 用真实 Spring wiring/测试 provider 重跑，不接受 Mockito/本地 Service 实现进入真实 IT，并复算 task/attempt/usage/ledger 行。
- **正向／反向验收：** 正向 initial、唯一 repair、复用、renew lease、知识快照、成功结果；反向拒绝第四次外调、输出任一结构违规、坏冻结输入、旧 lease、stale branch、P1 fake/Mapper 和伪造 usage。
- **统一 gate：** Task 1 worktree gate `-Phase f2-rebase -ExpectedF1Head/-ExpectedF2Head -RequireClean` 已由 rebase 执行器生成不可变记录；本卡日常检查使用 `-Phase inspect` 和精确 allowed paths，四 selector 各自 JVM gate。
- **准确命令／证据：** core 命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest' test`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest.xml`；infra 单元命令 `-pl :ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest,org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest' test`，报告分别为 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest.xml`；IT 命令 `-pl :ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT.xml`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator Service 模板、P0-C task/attempt 实现和 P1 stable header；四测试 `@Tag("dev")`，IT 首步 require local environment。
- [ ] F2 收口：从生产源码和全部真实 `*IT.java` 删除 P1 fake；只允许单元测试作用域受控 fake。真实 wiring 精确 import `IKnowledgeRoutingService`、`IKnowledgeSnapshotService`、五 DTO 与全局 `org.dromara.aivideo.provider.ModelProvider`；扫描禁止 `org.dromara.aivideo.questionnaire..*QuestionModelProvider` 定义。
- [ ] RED：`QuestionGenerationOutputValidatorTest.rejectsEveryStructuralViolationBeforeAttemptCanComplete` 逐项拒绝缺/额外字段、未知 schema、选项数、4–200 文本、2–6 options、label 1–80、value 1–300、空 slotContributions、recommended 超 2/重复/越界；`QuestionGenerationServiceTest` 精确断言三 operation key。Client/IT 用 Mockito `InOrder` 锁定 renew/startAttempt/provider/validate/complete/adopt 时序、完整 lease、attempt 单终态、第四次 provider 零调用和 stale 零写/零 markSuccess。RED 因 validator/Handler symbol 或 InOrder 断言失败。
- [ ] P0-C 构造：固定 `new ChargeableTaskDTO(QUESTION_GENERATE,"question_generate","script_draft",draftId,"question:"+draftId+":"+branchRevision+":"+nextQuestionOrdinal,"questionnaire:"+draftId,"questionnaire:"+draftId+":"+branchRevision,idempotencyKey,requestHash,tariffVersion,new TaskInitiatorDTO("app_user",actorId),new TaskRevisionSnapshotDTO(draftRevision,branchRevision,generationContextRevision,generationInputHash,Map.of()))`；先校验 actor/workspace 一致。
- [ ] 原子编排：`createChargeableTask → freeze immutable input → enqueue` 同事务，`reused=true` 立即返回；冻结 input 包含三目录版本、P1 route/snapshot、三修订和 input hash。
- [ ] provider/lease：每次真实调用前 `startAttempt`；`ProviderUsageDTO(providerRequestId,inputTokens,outputTokens,actualCost,currency)` 使用真实可得值，不可得传 `null`，禁止伪造 0；timeout 必须早于 lease 10 秒，不足先 `renewLease`，后续全部使用返回 lease；根任务最多 3 attempts。
- [ ] 结果事务：strict validate 完成 attempt 后，独立事务首步锁定并重核三摘要；一致时同一事务依次写问题/member、append `QUESTION_GENERATION_RESULT_ADOPTED` 审计，再显式 `aiTaskService.markSuccess(currentLease, TaskResultReferenceDTO.of("question", questionId))`，由 P0-C 同事务 settle；stale 抛精确非重试错误，attempt 保持 success、业务/audit/markSuccess 零调用、P0-C release/零 settle、二次 scanner provider 零调用。
- [ ] GREEN 验证：四条准确命令 Maven 0，四个 JVM gate GREEN；reviewer 核对真实 usage 和唯一 Handler registry。
- [ ] review/提交：独立复跑后提交 `feat(questionnaire): generate paid adaptive questions`。

可复制 attempt 时序 fixture 与最小 Handler：

```java
@Tag("dev")
class QuestionGenerationTaskHandlerIT {
    @Test
    void usesRenewedLeaseAndSingleAttemptTerminalBeforeAdoption() {
        AiTaskExecutionLeaseDTO oldLease = lease("worker-a", Instant.parse("2026-08-02T10:00:08Z"));
        AiTaskExecutionLeaseDTO renewed = lease("worker-a", Instant.parse("2026-08-02T10:01:00Z"));
        when(aiTaskService.renewLease(eq(oldLease), any())).thenReturn(renewed);
        when(attemptService.startAttempt(41L, 42L, "worker-a", "initial",
            "openai", "question-model", INPUT_HASH)).thenReturn(attempt(51L));
        when(modelProvider.generate(any())).thenReturn(validProviderResult());

        handler.handle(oldLease);

        InOrder order = inOrder(aiTaskService, attemptService, modelProvider,
            validator, attemptService, resultService);
        order.verify(aiTaskService).renewLease(eq(oldLease), any());
        order.verify(attemptService).startAttempt(
            41L, 42L, "worker-a", "initial", "openai", "question-model", INPUT_HASH);
        order.verify(modelProvider).generate(any());
        order.verify(validator).validate(any(), eq("audience"), anySet());
        order.verify(attemptService).completeAttempt(eq(51L), any(ProviderUsageDTO.class), anyString());
        order.verify(resultService).adopt(eq(renewed), any(FrozenQuestionInputDTO.class),
            any(GeneratedQuestionDTO.class));
        verify(attemptService, never()).failAttempt(eq(51L), any(), anyString(), anyString());
    }

    @Test
    void fourthProviderAttemptAndStaleResultHaveNoExternalOrBusinessSideEffects() {
        when(attemptService.startAttempt(anyLong(), anyLong(), anyString(), anyString(),
            anyString(), anyString(), anyString())).thenThrow(
                new AiTaskNonRetryableException("AI_TASK_PROVIDER_ATTEMPTS_EXHAUSTED", "已达上限"));
        assertThatThrownBy(() -> handler.handle(validLease()))
            .isInstanceOf(AiTaskNonRetryableException.class);
        verifyNoInteractions(modelProvider);

        when(attemptService.startAttempt(anyLong(), anyLong(), anyString(), anyString(),
            anyString(), anyString(), anyString())).thenReturn(attempt(61L));
        when(modelProvider.generate(any())).thenReturn(validProviderResult());
        doThrow(new AiTaskNonRetryableException("STALE_BRANCH_RESULT", "上下文已更新"))
            .when(resultService).adopt(any(), any(), any());
        assertThatThrownBy(() -> handler.handle(validLease()))
            .isInstanceOf(AiTaskNonRetryableException.class);
        verify(questionMapper, never()).insert(any());
        verify(aiTaskService, never()).markSuccess(any(), any());
    }
}

@Component
final class QuestionGenerationTaskHandler implements IAiTaskExecutionHandler {
    public void handle(AiTaskExecutionLeaseDTO lease) {
        FrozenQuestionInputDTO input = inputService.requireQuestion(
            lease.rootTaskId(), lease.executionTaskId());
        AiTaskExecutionLeaseDTO current = leasePolicy.renewIfNeeded(lease, Duration.ofSeconds(10));
        GeneratedQuestionDTO output = providerClient.generate(current, input);
        resultService.adopt(current, input, output);
    }
}
```

### 任务 7：实现安全事实检索、逐条决定和接受事实快照

**文件：** 创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IEvidenceRetrievalService.java`、`IEvidenceResultService.java`、`IEvidenceDecisionService.java`，对应 `EvidenceRetrievalServiceImpl.java`、`EvidenceResultServiceImpl.java`、`EvidenceDecisionServiceImpl.java`、`QuestionnaireContextServiceImpl.java`、`EvidenceReviewServiceImpl.java`；在 `questionnaire/dto/` 创建 `StartEvidenceRetrievalCommandDTO.java`、`SaveEvidenceDecisionsCommandDTO.java`、`EvidenceDecisionCommandDTO.java`、`FrozenEvidenceInputDTO.java`、`EvidenceRetrievalResultDTO.java`、`EvidenceDecisionResultDTO.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/EvidenceSearchProvider.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/evidence/AllowedExternalUriPolicy.java`、`ForbiddenAddressRanges.java`、`EvidenceDnsResolver.java`、`PinnedHttpsTransport.java`、`EvidenceFetchLimits.java`、`EvidenceContentFetcher.java`、`UnsafeEvidenceUriException.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/listener/EvidenceRetrievalTaskHandler.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/EvidenceReviewServiceTest.java`、`QuestionnaireContextServiceTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/questionnaire/evidence/AllowedExternalUriPolicyTest.java`、`ai-video-api/ruoyi-modules/ai-video/ai-video-infra/src/test/java/org/dromara/aivideo/questionnaire/listener/EvidenceRetrievalTaskHandlerIT.java`；修改任务 4 已创建的 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/QuestionnaireBranchIT.java` 增加锁交错用例；仅允许读取任务 2 已创建的 `ScriptBranchEvidenceDecisionMapper.java`/XML，本卡不得修改它们。

**最小任务卡：**

- **单一目标／不做：** 实现付费安全检索、不可变事实决定、两个稳定上下文 Service 实现；不生成候选文案。
- **权威源：** 原业务规格证据链、P0-C attempt/lease、P2 六 DTO、第 1.2 状态矩阵、master SSRF/代表性硬门禁。
- **治理等级／触发项：** 红色；触发外网 SSRF、真实成本、事实真实性、分支修订和 P3 冻结快照。
- **实施者／reviewer／并发：** 开发 C writer，开发 B 安全/事实链 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行列出的可写完整仓库相对路径及两个只读 Mapper 路径；只读/写当前 workspace/draft/branch 的证据批次、来源、事实和决定。
- **允许影响：** 可新增 evidence provider/listener 和 P2 Service；禁止重定义全局 WebSearch client、创建 core provider 类型、直改 P0-C 计费、跨分支“最新决定”查询。
- **前置／退出：** F2 rebase、任务 2/4/6；退出为五 selector、SSRF 攻击矩阵、决定矩阵、上下文快照/锁交错和 stale 全链 GREEN。
- **结构签名检查点：** reviewer 核对两个稳定实现位于 `service.impl`、方法精确、查询同一只读事务、锁入口 MANDATORY 且非 readOnly、Mapper 精确、provider 仅 infra；P3 外层必须是写事务并先调用锁入口。`EvidenceSearchProvider` 精确注入全局 `org.dromara.aivideo.client.WebSearchClient`，P2 零同义 client 定义。
- **GREEN 独立复跑检查点：** reviewer 用第二 DNS/redirect fixture 和并发决定 fixture 复跑，并逐项核对 accepted/decision `factId` 集合、排序和 revision。
- **正向／反向验收：** 正向安全 URL、search attempt、completed/no_results 两种成功批次、决定首次/复用/整批替换、两 context；反向拒绝内网/元数据/重绑定/危险重定向、坏结构被当 no_results、冲突接受、row id 冒充 revision、跨工作区、stale 和 provider 二次调用。
- **统一 gate：** Task 1 worktree gate F2 参数、allowed paths 精确为本卡清单；五 selector 各自 JVM gate。
- **准确命令／证据：** core 命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest,org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest' test`，报告分别为 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest.xml`、`ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest.xml`；锁 IT 命令 `-pl :ai-video-core -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.questionnaire.QuestionnaireBranchIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireBranchIT.xml`；infra 单元命令 `-pl :ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest' test`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest.xml`；infra IT 命令 `-pl :ai-video-infra -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT.xml`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator Service/Mapper 模板、P0-C attempt 与全局 `org.dromara.aivideo.client.WebSearchClient`；四测试 `@Tag("dev")`，IT 首步 require local environment，真实 IT 精确 import 全局 client 并扫描禁止 `org.dromara.aivideo.questionnaire..*WebSearchClient` 定义。
- [ ] RED：必须包含 `AllowedExternalUriPolicyTest.rejectsUnsafeEvidenceUris`，逐 CIDR 覆盖 IPv4 `0/8,10/8,100.64/10,127/8,169.254/16,172.16/12,192.0.0/24,192.0.2/24,192.168/16,198.18/15,198.51.100/24,203.0.113/24,224/4,240/4`；IPv6 unspecified、loopback、IPv4-mapped、`2001:db8::/32,fc00::/7,fe80::/10,ff00::/8`。仅 HTTPS 443，解析返回的每个地址都检查；禁自动跳转，最多 3 跳且逐跳 DNS/URI 重验；连接固定已验证 IP 同时保留原 host 做 TLS/SNI；响应体 2 MiB、连接 3 秒、总读取 8 秒、仅 html/plain。
- [ ] RED：决定测试按第 1.2 节覆盖首次/同值/整批替换；context 测归属、同一只读事务、稳定排序、不可变 List、三 hash、accepted/decision 集合完全一致和 `decisionRevision != rowId`；反射/source 断言 lock 方法返回精确 DTO、`Propagation.MANDATORY`、非 readOnly。`QuestionnaireBranchIT` 用两个真实连接/barrier 证明 A 锁 draft/current branch 后 B 改答阻塞，A 返回一致 hash 快照并提交后 B 才继续；反向由 B 先提交时 A 锁后读到新 hash，旧 request 必须在 slot/quota/task 前失败。`EvidenceRetrievalTaskHandlerIT` 固定 fixture 精确断言 slot/family/group，并反断言三键不含 branchId/contextRevision；删除五 exact XML并分别取得 JVM gate RED。
- [ ] 上下文实现：`QuestionnaireContextServiceImpl.getCurrentContext` 在一个 `@Transactional(readOnly=true)` 快照中校验归属并读取方向/问答/补充；`EvidenceReviewServiceImpl.getAcceptedContext` 在一个只读快照中连接 branch mapping→决定修订→事实/来源并排序。`QuestionnaireContextServiceImpl.lockCurrentContextForGeneration` 使用 `@Transactional(propagation=Propagation.MANDATORY)` 且非 readOnly，按 tenant/owner scope `SELECT FOR UPDATE` draft→draft.current_branch，校验 branchId=currentBranchId，并在同一调用方写事务返回包含 branchRevision/questionnaireHash/knowledgeContextHash 的一致快照；P3 不得使用外层 readOnly 查询替代。
- [ ] P0-C 构造：evidence 使用 `new ChargeableTaskDTO(EVIDENCE_RETRIEVE,"evidence_retrieve","script_draft",draftId,"evidence:"+draftId+":"+branchRevision,"evidence:"+draftId,"evidence:"+draftId+":"+branchRevision,idempotencyKey,requestHash,tariffVersion,new TaskInitiatorDTO("app_user",actorId),new TaskRevisionSnapshotDTO(draftRevision,branchRevision,generationContextRevision,generationInputHash,Map.of()))`；严格 create/freeze/enqueue/reused、完整 lease/renew 和 attempt≤3。
- [ ] provider/result：`EvidenceSearchProvider` 注入并紧邻调用全局 `org.dromara.aivideo.client.WebSearchClient`；`ProviderUsageDTO` 五字段只写真值/不可得 null；严格 URL/响应校验后 complete attempt。合法空结果是成功：持久化 `status=no_results,source_count=0,fact_count=0` 空 batch，零 source/fact；同事务写 batch→append `EVIDENCE_NO_RESULTS_SAVED`→`markSuccess(...of("evidence_batch",batchId))`→P0-C settle 恰一次。非空同理写 completed batch/source/fact/audit/markSuccess。provider 异常或坏结构必须 failAttempt + P0-C release/零 settle，绝不能伪装 no_results；stale 零业务/audit/markSuccess。
- [ ] 决定事务：Controller app actor 显式传入；固定 draft→current branch→writable guard；首次决定写 origin，同值零写；整批替换恰一 `evidence_decision_changed` branch，复制未变映射并把全部当前题 rootIds 交给 P0-C inherit，最后 append 对应 audit。冲突事实 accepted、审计失败、46123 均零业务副作用。
- [ ] GREEN 验证：五 selector 对应准确命令 Maven 0、五份 JVM gate GREEN；锁 trace 无死锁/倒序/TOCTOU；安全确定性失败不重试，暂态网络可租约恢复；成功路径恰一次 `markSuccess` 并由 P0-C settle，P2 不直接调用 settle/release。
- [ ] review/提交：独立安全/快照复跑后提交 `feat(questionnaire): add reviewed evidence retrieval`。

可复制 SSRF/no-results/attempt RED fixture 与最小结果入口：

```java
@Tag("dev")
class AllowedExternalUriPolicyTest {
    private static final EvidenceFetchLimits LIMITS = new EvidenceFetchLimits(
        2L * 1024 * 1024, Duration.ofSeconds(3), Duration.ofSeconds(8), 3,
        Set.of("text/html", "text/plain"));
    @Mock EvidenceDnsResolver dnsResolver;
    @Mock PinnedHttpsTransport transport;
    AllowedExternalUriPolicy policy;
    EvidenceContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        policy = new AllowedExternalUriPolicy(new ForbiddenAddressRanges());
        fetcher = new EvidenceContentFetcher(policy, dnsResolver, transport, LIMITS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://example.com/a", "https://0.0.0.1/a", "https://10.0.0.1/a",
        "https://100.64.0.1/a", "https://127.0.0.1/a",
        "https://169.254.169.254/latest/meta-data", "https://172.16.0.1/a",
        "https://192.0.0.1/a", "https://192.0.2.1/a", "https://192.168.0.1/a",
        "https://198.18.0.1/a", "https://198.51.100.1/a",
        "https://203.0.113.1/a", "https://224.0.0.1/a", "https://240.0.0.1/a",
        "https://[::]/a", "https://[::1]/a", "https://[::ffff:127.0.0.1]/a",
        "https://[2001:db8::1]/a", "https://[fc00::1]/a",
        "https://[fe80::1]/a", "https://[ff00::1]/a",
        "https://example.com:8443/a", "file:///etc/passwd"
    })
    void rejectsUnsafeEvidenceUris(String value) {
        assertThatThrownBy(() -> policy.requireAllowed(URI.create(value)))
            .isInstanceOf(UnsafeEvidenceUriException.class)
            .hasMessage("外部资料地址不安全");
    }

    @Test
    void rejectsWhenAnyDnsAnswerIsForbiddenBeforeConnect() throws Exception {
        when(dnsResolver.resolve("safe.example")).thenReturn(List.of(
            InetAddress.getByName("93.184.216.34"), InetAddress.getByName("127.0.0.1")));

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/a")))
            .isInstanceOf(UnsafeEvidenceUriException.class);

        verifyNoInteractions(transport);
    }

    @Test
    void pinsValidatedIpAndPreservesOriginalHostForTlsSniWithoutReresolving() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.resolve("safe.example")).thenReturn(List.of(publicAddress));
        when(transport.exchange(any())).thenReturn(new PinnedHttpsTransport.Response(
            200, null, "text/plain", "ok".getBytes(StandardCharsets.UTF_8)));

        fetcher.fetch(URI.create("https://safe.example/a"));

        verify(dnsResolver, times(1)).resolve("safe.example");
        verify(transport).exchange(argThat(request ->
            request.connectAddress().equals(publicAddress)
                && request.tlsServerName().equals("safe.example")
                && request.hostHeader().equals("safe.example")
                && request.connectTimeout().equals(Duration.ofSeconds(3))
                && request.totalReadTimeout().equals(Duration.ofSeconds(8))));
    }

    @Test
    void revalidatesEveryRedirectHopAndRejectsDnsRebindingBeforeSecondConnect() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.resolve("safe.example"))
            .thenReturn(List.of(publicAddress), List.of(InetAddress.getByName("10.0.0.7")));
        when(transport.exchange(any())).thenReturn(new PinnedHttpsTransport.Response(
            302, URI.create("https://safe.example/next"), "text/plain", new byte[0]));

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/start")))
            .isInstanceOf(UnsafeEvidenceUriException.class);

        verify(dnsResolver, times(2)).resolve("safe.example");
        verify(transport, times(1)).exchange(any());
    }

    @Test
    void rejectsFourthRedirectBodyOverTwoMiBAndUnsupportedContentType() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("93.184.216.34");
        when(dnsResolver.resolve(anyString())).thenReturn(List.of(publicAddress));
        when(transport.exchange(any()))
            .thenReturn(redirect("/r1"), redirect("/r2"), redirect("/r3"), redirect("/r4"));
        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/start")))
            .isInstanceOf(UnsafeEvidenceUriException.class)
            .hasMessageContaining("redirect");

        reset(transport);
        when(transport.exchange(any())).thenReturn(new PinnedHttpsTransport.Response(
            200, null, "text/plain", new byte[2 * 1024 * 1024 + 1]));
        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/large")))
            .isInstanceOf(UnsafeEvidenceUriException.class).hasMessageContaining("2 MiB");

        reset(transport);
        when(transport.exchange(any())).thenReturn(new PinnedHttpsTransport.Response(
            200, null, "application/octet-stream", new byte[0]));
        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/binary")))
            .isInstanceOf(UnsafeEvidenceUriException.class).hasMessageContaining("content-type");
    }

    @Test
    void failsClosedOnThreeSecondConnectOrEightSecondTotalReadTimeout() throws Exception {
        when(dnsResolver.resolve("safe.example")).thenReturn(
            List.of(InetAddress.getByName("93.184.216.34")));
        when(transport.exchange(any())).thenThrow(new SocketTimeoutException("timeout"));

        assertThatThrownBy(() -> fetcher.fetch(URI.create("https://safe.example/slow")))
            .isInstanceOf(UnsafeEvidenceUriException.class);
        verify(transport).exchange(argThat(request ->
            request.connectTimeout().equals(Duration.ofSeconds(3))
                && request.totalReadTimeout().equals(Duration.ofSeconds(8))));
    }

    private static PinnedHttpsTransport.Response redirect(String path) {
        return new PinnedHttpsTransport.Response(
            302, URI.create("https://safe.example" + path), "text/plain", new byte[0]);
    }
}

@Tag("dev")
class EvidenceRetrievalTaskHandlerIT {
    @Test
    void persistsNoResultsThenAuditsMarksSuccessAndSettlesOnce() {
        when(attemptService.startAttempt(71L, 72L, "worker-e", "search",
            "search-provider", null, INPUT_HASH)).thenReturn(attempt(73L));
        when(webSearchClient.search(any())).thenReturn(validEmptyResultWithUsage());

        handler.handle(validEvidenceLease());

        InOrder order = inOrder(attemptService, webSearchClient, attemptService,
            evidenceBatchMapper, auditService, aiTaskService);
        order.verify(attemptService).startAttempt(
            71L, 72L, "worker-e", "search", "search-provider", null, INPUT_HASH);
        order.verify(webSearchClient).search(any());
        order.verify(attemptService).completeAttempt(eq(73L), any(), anyString());
        order.verify(evidenceBatchMapper).insert(argThat(batch ->
            batch.getStatus().equals("no_results") &&
            batch.getSourceCount() == 0 && batch.getFactCount() == 0));
        order.verify(auditService).append(argThat(a ->
            a.action().equals("EVIDENCE_NO_RESULTS_SAVED")));
        order.verify(aiTaskService).markSuccess(any(AiTaskExecutionLeaseDTO.class),
            eq(TaskResultReferenceDTO.of("evidence_batch", 801L)));
        verifyNoInteractions(evidenceSourceMapper, evidenceFactMapper);
        verify(quotaBillingService, never()).settle(any());
        verify(quotaBillingService, never()).release(any());
    }

    @Test
    void malformedOrFourthAttemptNeverCallsProviderOrMarksSuccess() {
        when(attemptService.startAttempt(anyLong(), anyLong(), anyString(), anyString(),
            anyString(), nullable(String.class), anyString())).thenThrow(
                new AiTaskNonRetryableException("AI_TASK_PROVIDER_ATTEMPTS_EXHAUSTED", "已达上限"));
        assertThatThrownBy(() -> handler.handle(validEvidenceLease()))
            .isInstanceOf(AiTaskNonRetryableException.class);
        verifyNoInteractions(webSearchClient);
        verify(aiTaskService, never()).markSuccess(any(), any());
    }
}

@Tag("dev")
class QuestionnaireContextServiceTest {
    @Test
    void lockMethodArchUnitTargetsOnlyGenerationLockEntry() throws Exception {
        JavaClass implementation = new ClassFileImporter()
            .importClasses(QuestionnaireContextServiceImpl.class)
            .get(QuestionnaireContextServiceImpl.class);
        List<JavaMethod> candidates = implementation.getMethods().stream()
            .filter(method -> method.getName().equals("lockCurrentContextForGeneration"))
            .filter(method -> method.getRawParameterTypes().stream()
                .map(JavaClass::getName).toList()
                .equals(List.of(Long.class.getName(), Long.class.getName())))
            .toList();
        assertThat(candidates).singleElement().satisfies(target -> {
            assertThat(target.getRawReturnType().isEquivalentTo(QuestionnaireContextDTO.class)).isTrue();
            Transactional tx = target.getAnnotationOfType(Transactional.class);
            assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
            assertThat(tx.readOnly()).isFalse();
            assertThat(target.getMethodCallsFromSelf()).extracting(call -> call.getTarget().getName())
                .contains("selectScopedForUpdate");
        });
    }

    @Test
    void lockEntryIsMandatoryWriteTransactionAndLocksDraftBeforeCurrentBranch() throws Exception {
        Method method = QuestionnaireContextServiceImpl.class.getMethod(
            "lockCurrentContextForGeneration", Long.class, Long.class);
        Transactional tx = method.getAnnotation(Transactional.class);
        assertThat(tx).isNotNull();
        assertThat(tx.propagation()).isEqualTo(Propagation.MANDATORY);
        assertThat(tx.readOnly()).isFalse();

        QuestionnaireContextDTO context = service.lockCurrentContextForGeneration(88L, 701L);

        InOrder order = inOrder(draftMapper, branchMapper);
        order.verify(draftMapper).selectScopedForUpdate(88L, tenantId, ownerType, ownerId);
        order.verify(branchMapper).selectScopedForUpdate(701L, tenantId, ownerType, ownerId);
        assertThat(context.currentBranchId()).isEqualTo(701L);
        assertThat(context.questionnaireHash()).matches("[0-9a-f]{64}");
        assertThat(context.knowledgeContextHash()).matches("[0-9a-f]{64}");
    }
}

public interface IQuestionnaireContextService {
    QuestionnaireContextDTO getCurrentContext(Long draftId, Long branchId);
    QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId, Long branchId);
}

@Transactional(propagation = Propagation.MANDATORY)
public QuestionnaireContextDTO lockCurrentContextForGeneration(
    Long draftId, Long branchId) {
    ScopedDraft draft = draftMapper.selectScopedForUpdate(
        draftId, workspace.tenantId(), workspace.ownerType(), workspace.ownerId());
    if (!draft.currentBranchId().equals(branchId)) throw revisionConflict();
    branchMapper.selectScopedForUpdate(
        branchId, workspace.tenantId(), workspace.ownerType(), workspace.ownerId());
    return assembleLockedContext(draft, branchId);
}

public interface IEvidenceResultService {
    void adopt(AiTaskExecutionLeaseDTO lease, FrozenEvidenceInputDTO input,
        EvidenceRetrievalResultDTO result);
}
```

### 任务 8：暴露用户端 BO/VO、Controller 和权限边界

**文件：** 在 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/bo/` 创建 `SaveDirectionBo.java`、`StartQuestionnaireBo.java`、`SubmitQuestionTurnBo.java`、`SaveSupplementBo.java`、`CreateEvidenceSearchBo.java`、`SaveEvidenceDecisionsBo.java`；在 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/domain/vo/` 创建 `QuestionnaireSnapshotVo.java`、`QuestionnaireAdvanceVo.java`、`QuestionTurnVo.java`、`QuestionTurnBlockingDetailVo.java`、`AdaptiveQuestionVo.java`、`EvidenceSnapshotVo.java`；在 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/main/java/org/dromara/aivideo/user/studio/controller/` 创建 `StudioDirectionController.java`、`StudioQuestionnaireController.java`、`StudioEvidenceController.java`；创建 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/src/test/java/org/dromara/aivideo/user/studio/controller/StudioQuestionnaireControllerTest.java`、`ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/UserQuestionnaireAssemblyIT.java`、`ai-video-api/ruoyi-admin/src/test/java/org/dromara/aivideo/bootstrap/PlatformQuestionnaireIsolationTest.java`。

**最小任务卡：**

- **单一目标／不做：** 暴露 `/api/studio/**` BO/VO/Controller，验证用户启动装配与平台隔离；不在 Controller 编排业务，不把用户装配测试放 ruoyi-admin。
- **权威源：** API_CONTRACT、P0-A/P0-B 双身份隔离、reconciliation canonical `org.dromara.aivideo.user.studio`、原业务规格。
- **治理等级／触发项：** 红色；触发身份域、权限、资源归属、收费入口和双启动模块隔离。
- **实施者／reviewer／并发：** 开发 C writer，开发 B 权限/归属 reviewer，最多 2 人。
- **精确路径／数据范围：** 只允许本卡“文件”行列出的 15 个 main 文件、三个测试，以及经 reviewer 逐项登记的对应模块配置装配引用；请求只作用当前 app_user/workspace/draft/branch，装配引用不得新增业务类型。
- **允许影响：** 可新增 user HTTP 适配与 user-api 装配；ruoyi-admin 只新增平台隔离负测，不装配 P2 用户 Controller。
- **前置／退出：** 任务 4–7 GREEN；退出为 user Controller、platform negative、user-api IT 三 selector GREEN 和 401/403/归属矩阵。
- **结构签名检查点：** reviewer 核对 core 无 BO/VO、Controller 只依赖 Service/Mapper-free mapper adapter、每个写方法首个业务调用是唯一 `AppAuthorizationActorResolver.requireActor()`、`@SaCheckPermission(type="app")`、权限字符串、R 返回和所有 Long→string；六 BO exact field/forbidden-field 扫描，`SaveSupplementBo.toneStyles` 是对象列表。
- **GREEN 独立复跑检查点：** reviewer 分别从 user-api 与 ruoyi-admin 启动测试上下文，证明用户端有三个 Controller、平台端均无。
- **正向／反向验收：** 正向路由/字段/权限/装配；反向拒绝 sys/app 回退、401 当 403、跨 workspace/draft/branch、客户端 actor/workspace/费用字段、平台暴露用户路由。
- **统一 gate：** Task 1 worktree gate，allowed paths 精确为本卡；三个 selector 各自 JVM gate。
- **准确命令／证据：** 用户单元命令 `-pl :ai-video-user -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest' test`，报告 `ai-video-api/ruoyi-modules/ai-video/ai-video-user/target/surefire-reports/TEST-org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest.xml`；平台负测命令 `-pl :ruoyi-admin -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=true -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest' test`，报告 `ai-video-api/ruoyi-admin/target/surefire-reports/TEST-org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest.xml`；用户装配命令 `-pl :ai-video-user-api -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT.xml`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：读取 RuoYi skill/backend reference、generator BO/VO/Controller 模板、现有 user/studio 与双启动相似模块；三测试 `@Tag("dev")`，AssemblyIT 首步 require local environment。
- [ ] RED：覆盖全部九个 method/path、六 BO exact fields/Bean Validation/禁止字段、nextAction 判别形状、BlockingDetail、权限、归属和双启动隔离；补充 tone 对象矩阵；用同编号 `AppActorContext.appUser(1001L)`/`sysUser(1001L)` 证明无身份域回退；审计失败 fixture 必须由 Task4–7 Service IT 证明业务零写。删除三个 XML并取得 JVM gate RED。
- [ ] GREEN：查询权限 `aivideo:studio:query`，免费写 `aivideo:studio:edit`，收费 `aivideo:studio:generate` + `aivideo:quota:use`；Controller 只做校验、resolver、BO→core DTO、Service、core result→VO；用户端 Controller `@Log` 零命中，默认 `LoginHelper/StpUtil` 零命中。
- [ ] GREEN 验证：三准确命令均 0、三个 JVM gate GREEN；所有 Java Long ID/修订/额度/金额为 JSON 十进制 string，固定时长/进度/序号为 number。
- [ ] review/提交：双启动独立复跑后提交 `feat(studio): expose questionnaire APIs`。

可复制 Controller RED fixture 与最小薄适配器：

```java
@WebMvcTest(StudioQuestionnaireController.class)
@Tag("dev")
class StudioQuestionnaireControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean IQuestionTurnService questionTurnService;
    @MockBean IWorkspaceAuthorizationService authorizationService;
    @MockBean AppAuthorizationActorResolver actorResolver;

    @Test
    void freezesNineEndpointsAndSixBosExactly() throws Exception {
        List<EndpointContract> endpoints = List.of(
            new EndpointContract(StudioDirectionController.class, "getDirectionOptions", GET,
                "/api/studio/script-drafts/{draftId}/direction-options", Void.class),
            new EndpointContract(StudioDirectionController.class, "saveDirection", PUT,
                "/api/studio/script-drafts/{draftId}/direction", SaveDirectionBo.class),
            new EndpointContract(StudioQuestionnaireController.class, "startQuestionnaire", POST,
                "/api/studio/script-drafts/{draftId}/questionnaire/start", StartQuestionnaireBo.class),
            new EndpointContract(StudioQuestionnaireController.class, "submitTurn", POST,
                "/api/studio/script-drafts/{draftId}/questionnaire/turns", SubmitQuestionTurnBo.class),
            new EndpointContract(StudioQuestionnaireController.class, "getQuestionnaire", GET,
                "/api/studio/script-drafts/{draftId}/questionnaire", Void.class),
            new EndpointContract(StudioQuestionnaireController.class, "saveSupplement", PUT,
                "/api/studio/script-drafts/{draftId}/questionnaire/supplement", SaveSupplementBo.class),
            new EndpointContract(StudioEvidenceController.class, "createEvidenceSearch", POST,
                "/api/studio/script-drafts/{draftId}/evidence-searches", CreateEvidenceSearchBo.class),
            new EndpointContract(StudioEvidenceController.class, "getEvidence", GET,
                "/api/studio/script-drafts/{draftId}/evidence", Void.class),
            new EndpointContract(StudioEvidenceController.class, "saveEvidenceDecisions", PUT,
                "/api/studio/script-drafts/{draftId}/evidence-decisions", SaveEvidenceDecisionsBo.class));
        assertThat(endpoints).hasSize(9);
        endpoints.forEach(this::assertEndpointContract);

        List<BoContract> bos = List.of(
            new BoContract(SaveDirectionBo.class, List.of("draftRevision", "branchRevision",
                "expectedCatalogVersion", "industryCode", "industryCustomText", "purposeCode",
                "purposeCustomText", "targetDurationSeconds")),
            new BoContract(StartQuestionnaireBo.class, List.of("draftRevision", "branchRevision",
                "idempotencyKey", "expectedTariffVersion")),
            new BoContract(SubmitQuestionTurnBo.class, List.of("questionId", "selectedOptionCodes",
                "customSelected", "customText", "draftRevision", "branchRevision",
                "idempotencyKey", "expectedTariffVersion")),
            new BoContract(SaveSupplementBo.class, List.of("draftRevision", "branchRevision",
                "subject", "audience", "coreMessages", "targetDurationSeconds", "callToAction",
                "mustKeepFacts", "prohibitedContents", "toneStyles", "otherNotes")),
            new BoContract(CreateEvidenceSearchBo.class, List.of("draftRevision", "branchRevision",
                "queryIntent", "idempotencyKey", "expectedTariffVersion")),
            new BoContract(SaveEvidenceDecisionsBo.class,
                List.of("draftRevision", "branchRevision", "decisions")));
        assertThat(bos).hasSize(6);
        bos.forEach(contract -> {
            List<String> actual = objectMapper.getDeserializationConfig()
                .introspect(objectMapper.constructType(contract.type())).findProperties().stream()
                .map(BeanPropertyDefinition::getName).toList();
            assertThat(actual).containsExactlyInAnyOrderElementsOf(contract.fields());
            assertThat(actual).doesNotContain("tenantId", "ownerType", "ownerId", "actorId",
                "taskId", "rootTaskId", "industryCatalogVersion", "purposeCatalogVersion",
                "durationRuleVersion");
        });
    }

    @Test
    void rejectsNonCanonicalDecimalLongFormsAcrossAllSixBos() throws Exception {
        List<String> invalid = List.of("", "+1", "01", "1e3", "1.0", "9223372036854775808");
        List<MalformedBoFixture> fixtures = List.of(
            new MalformedBoFixture(SaveDirectionBo.class,
                "{\"draftRevision\":%s,\"branchRevision\":\"2\",\"expectedCatalogVersion\":\"20\",\"industryCode\":\"local_life\",\"industryCustomText\":null,\"purposeCode\":\"store_promotion\",\"purposeCustomText\":null,\"targetDurationSeconds\":60}"),
            new MalformedBoFixture(StartQuestionnaireBo.class,
                "{\"draftRevision\":%s,\"branchRevision\":\"2\",\"idempotencyKey\":\"start-1\",\"expectedTariffVersion\":\"7\"}"),
            new MalformedBoFixture(SubmitQuestionTurnBo.class,
                "{\"questionId\":\"33\",\"selectedOptionCodes\":[\"option_01\"],\"customSelected\":false,\"customText\":null,\"draftRevision\":%s,\"branchRevision\":\"2\",\"idempotencyKey\":\"turn-1\",\"expectedTariffVersion\":\"7\"}"),
            new MalformedBoFixture(SaveSupplementBo.class,
                "{\"draftRevision\":%s,\"branchRevision\":\"2\",\"subject\":\"门店\",\"audience\":[\"居民\"],\"coreMessages\":[\"新品\"],\"targetDurationSeconds\":60,\"callToAction\":\"到店\",\"mustKeepFacts\":[],\"prohibitedContents\":[],\"toneStyles\":[{\"code\":\"authoritative\",\"customText\":null}],\"otherNotes\":null}"),
            new MalformedBoFixture(CreateEvidenceSearchBo.class,
                "{\"draftRevision\":%s,\"branchRevision\":\"2\",\"queryIntent\":null,\"idempotencyKey\":\"evidence-1\",\"expectedTariffVersion\":\"7\"}"),
            new MalformedBoFixture(SaveEvidenceDecisionsBo.class,
                "{\"draftRevision\":%s,\"branchRevision\":\"2\",\"decisions\":[{\"factId\":\"91\",\"decision\":\"accepted\",\"expectedDecisionRevision\":\"1\"}]}"));
        assertThat(fixtures).hasSize(6);
        for (MalformedBoFixture fixture : fixtures) {
            for (String value : invalid) {
                String json = fixture.jsonTemplate().formatted(
                    objectMapper.writeValueAsString(value));
                assertThatThrownBy(() -> objectMapper.readValue(json, fixture.type()))
                    .as("%s must reject %s", fixture.type().getSimpleName(), value)
                    .isInstanceOf(JsonProcessingException.class);
            }
        }
    }

    @Test
    void submitTurnResolvesOneAppActorAndPassesOnlyCoreCommand() throws Exception {
        AppActorContext actor = AppActorContext.appUser(1001L);
        when(actorResolver.requireActor()).thenReturn(actor);
        when(questionTurnService.submitAnswer(eq(88L), any(), eq(actor)))
            .thenReturn(Fixtures.showSupplementResult());

        mvc.perform(post("/api/studio/script-drafts/88/questionnaire/turns")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                  {"questionId":"33","selectedOptionCodes":["option_01"],
                   "customSelected":false,"customText":null,
                   "draftRevision":"11","branchRevision":"2",
                   "idempotencyKey":"q-88-b2-n4-a","expectedTariffVersion":"7"}
                  """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextAction").value("show_supplement"))
            .andExpect(jsonPath("$.data.draftRevision").value("12"));

        InOrder order = inOrder(actorResolver, authorizationService, questionTurnService);
        order.verify(actorResolver).requireActor();
        order.verify(authorizationService).requireResourceAction(
            "script_draft", 88L, ResourceAction.GENERATE);
        order.verify(authorizationService).requireWorkspacePermission("aivideo:quota:use");
        order.verify(questionTurnService).submitAnswer(eq(88L),
            argThat(c -> c.questionId().equals(33L) && c.branchRevision().equals(2L)),
            same(actor));
        verifyNoMoreInteractions(actorResolver);
    }

    private void assertEndpointContract(EndpointContract expected) {
        Method method = Arrays.stream(expected.controller().getDeclaredMethods())
            .filter(candidate -> candidate.getName().equals(expected.methodName()))
            .findFirst().orElseThrow();
        RequestMapping base = AnnotatedElementUtils.findMergedAnnotation(
            expected.controller(), RequestMapping.class);
        RequestMapping route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        assertThat(singlePath(base) + singlePath(route)).isEqualTo(expected.path());
        assertThat(route.method()).containsExactly(expected.httpMethod());
        List<Class<?>> bodies = Arrays.stream(method.getParameters())
            .filter(parameter -> parameter.isAnnotationPresent(RequestBody.class))
            .map(Parameter::getType).toList();
        if (expected.body() == Void.class) assertThat(bodies).isEmpty();
        else assertThat(bodies).containsExactly(expected.body());
    }

    private static String singlePath(RequestMapping mapping) {
        String[] paths = mapping.path().length == 0 ? mapping.value() : mapping.path();
        return paths.length == 0 ? "" : paths[0];
    }

    private record EndpointContract(Class<?> controller, String methodName,
        RequestMethod httpMethod, String path, Class<?> body) {}
    private record BoContract(Class<?> type, List<String> fields) {}
    private record MalformedBoFixture(Class<?> type, String jsonTemplate) {}
}

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/script-drafts/{draftId}/questionnaire")
final class StudioQuestionnaireController {
    private final AppAuthorizationActorResolver actorResolver;
    private final IWorkspaceAuthorizationService authorizationService;
    private final IQuestionTurnService questionTurnService;

    @PostMapping("/turns")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    R<QuestionTurnVo> submitTurn(@PathVariable Long draftId,
        @Valid @RequestBody SubmitQuestionTurnBo body) {
        AppActorContext actor = actorResolver.requireActor();
        authorizationService.requireResourceAction(
            "script_draft", draftId, ResourceAction.GENERATE);
        authorizationService.requireWorkspacePermission("aivideo:quota:use");
        return R.ok(QuestionTurnVo.from(questionTurnService.submitAnswer(
            draftId, body.toCommand(), actor)));
    }
}
```

方向、start、supplement、evidence-search、evidence-decisions 五个写方法必须采用同一模板；任何 BO 中出现 scope/actor/task 字段由 Jackson unknown-property 与 bean validation 双重拒绝。Controller source scan 必须命中九条端点且对 `@Log|LoginHelper|StpUtil` 为零。

### 任务 9：实现前端类型、API、query key 和独立 Mock

**文件：** 修改 `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts`；创建 `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts`、`ai-video-ui/ai-video-webapp/mock/aivideo-studio.ts`。

**最小任务卡：**

- **单一目标／不做：** 实现前端类型、API、query key 和 P2 独立 Mock；不改工作台根、不承载 P3 fixture。
- **权威源：** API_CONTRACT、六 DTO/HTTP VO、P0-C `requestR<T>`、原业务规格状态与错误码。
- **治理等级／触发项：** 红色；触发 F1 六共享文件所有权、公共请求、缓存隔离、Mock 契约。
- **实施者／reviewer／并发：** 开发 C writer，开发 A API reviewer，最多 2 人。
- **精确路径／数据范围：** 五个 tracked 文件精确为 `ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts`、`ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts`、`ai-video-ui/ai-video-webapp/mock/aivideo-studio.ts`；另只生成 `ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task9-api.json`，仅 Mock/当前 workspace query key。
- **允许影响：** F0 仅 API 测试/Mock 草案；F1 所有权移交后才能写前三个共享文件；禁止页面散落 URL、修改 P3 mock。
- **前置／退出：** F0/F1 分段门禁；退出为 api Vitest GREEN、typecheck/lint、真实 API/Mock schema 双覆盖。
- **结构签名检查点：** reviewer 核对 ID/修订/额度/金额 string、固定时长 number、query key 含 workspace/draft/branch、`requestR<T>` 只解包一次；前端 `ToneStyleInput` 精确为 `{code:string;customText:string|null}`，`toneStyles` 禁止声明成 `string[]`。
- **GREEN 独立复跑检查点：** reviewer 删除 task9 JSON 后独立运行 exact selector 和 Vitest gate，随后运行 lint/typecheck。
- **正向／反向验收：** 正向全部路由/错误/nextAction/fixture及普通/custom tone 对象；反向拒绝 number ID、跨 workspace key、二次解包、`toneStyles:string[]`、重复 code、多个 custom、页面裸 URL、Ant Pro demo 响应和 P3 fixture。
- **统一 gate：** Task 1 worktree gate；F0 调用方 `AllowedPaths` 仅传 test/mock tracked 文件，F1 后传完整五个 tracked 文件；Vitest gate GREEN/RED 自动追加精确 evidence JSON 后再原样透传统一 gate。
- **准确命令／证据：** `$repoRoot = (& git rev-parse --show-toplevel).Trim(); $webRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'; $reportRelative = 'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task9-api.json'; $report = Join-Path $repoRoot $reportRelative; $allowed = @('ai-video-ui/ai-video-webapp/src/services/ai-video/studio/types.ts','ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.ts','ai-video-ui/ai-video-webapp/src/services/ai-video/studio/queryKeys.ts','ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts','ai-video-ui/ai-video-webapp/mock/aivideo-studio.ts'); $started = [DateTimeOffset]::UtcNow; Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue; Push-Location $webRoot; try { npm.cmd test -- src/services/ai-video/studio/api.test.ts --reporter=json --outputFile=$report; if ($LASTEXITCODE -ne 0) { throw 'task9 Vitest GREEN 失败' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'task9 lint 失败' } } finally { Pop-Location }; $gateText = (& git -C $repoRoot rev-parse --git-path 'p2-vitest-evidence-gate.ps1').Trim(); $vitestGate = if ([IO.Path]::IsPathRooted($gateText)) { $gateText } else { Join-Path $repoRoot $gateText }; $gateResult = & $vitestGate -RepoRoot $repoRoot -AllowedPaths $allowed -ReportRelativePath $reportRelative -ExpectedTestFiles @('ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts') -Phase GREEN -StartedAtUtc $started; if ($LASTEXITCODE -ne 0 -or $gateResult -cne 'P2_VITEST_EVIDENCE_OK') { throw 'task9 Vitest evidence gate 失败' }`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：核对 FRONTEND 指南、P0-C adapter/query key 相似实现；涉及 Ant Design 类型时先用项目 `antd` skill 和官方 CLI 文档核验，不凭记忆。
- [ ] RED：覆盖所有路由、RuoYi 单次解包、workspace/draft/branch key、错误码、阻塞 nextAction 及 Mock schema；`api.test.ts` 必须断言 `toneStyles` 为最多 5 个按 code 排序的 `{code,customText}`，普通项 null、custom 最多一项，并用字符串数组 fixture 证明类型/运行时契约失败。删除 task9 JSON、记录 start，npm 必须非零且 Vitest gate RED。
- [ ] GREEN：精确实现类型/API/query key/Mock；`mock/aivideo-studio.ts` 的补充 fixture 同时含普通 tone 对象和唯一 custom tone，不得输出字符串数组；Mock 覆盖 Section 5.6 全矩阵的服务端形态，与真实 API 类型共同编译。
- [ ] GREEN 验证：npm 0、Vitest gate GREEN、lint 0；扫描页面目录无 `/api/studio/`。
- [ ] review/提交：独立复跑后提交 `feat(studio): add questionnaire client contracts`。

可复制 API RED fixture、判别联合和完整 Mock payload：

```ts
export type ToneStyleInput = { code: string; customText: string | null };
export interface DirectionCatalogResponse {
  catalogVersion: string;
  industries: Array<{ code: string; label: string; customAllowed: boolean }>;
  purposesByIndustry: Record<string, Array<{ code: string; label: string; customAllowed: boolean }>>;
  targetDurations: Array<{ seconds: 30 | 45 | 60 | 90 | 120; label: string }>;
}
export interface SaveDirectionRequest {
  draftRevision: string;
  branchRevision: string;
  expectedCatalogVersion: string;
  industryCode: string;
  industryCustomText: string | null;
  purposeCode: string;
  purposeCustomText: string | null;
  targetDurationSeconds: 30 | 45 | 60 | 90 | 120;
}
export type QuestionnaireNextAction =
  | 'wait_task' | 'show_question' | 'show_supplement'
  | 'review_evidence' | 'generate_script'
  | 'resolve_quota' | 'reconfirm_tariff';

export interface BlockingDetail {
  businessCode: 46114 | 46115;
  billingSubject: { type: 'user' | 'organization'; id: string; name: string };
  requiredQuota: string;
  availableQuota: string;
  lockedQuota: string;
  previousTariffVersion: string | null;
  currentTariffVersion: string;
  currentUnitQuota: string;
  draftRevision: string;
  branchRevision: string;
  generationContextRevision: string;
  resumeOperation: 'generate_next_question';
}

interface AdvanceBase {
  draftRevision: string;
  branchRevision: string;
  generationContextRevision: string;
  reused: boolean;
  missingSlots: string[];
}
export type QuestionnaireAdvanceResult =
  | (AdvanceBase & { nextAction: 'wait_task'; rootTask: AiTaskSummary; question: null })
  | (AdvanceBase & { nextAction: 'show_question'; rootTask: null; question: AdaptiveQuestion })
  | (AdvanceBase & { nextAction: 'show_supplement' | 'review_evidence' | 'generate_script'; rootTask: null; question: null })
  | (AdvanceBase & { nextAction: 'resolve_quota' | 'reconfirm_tariff'; rootTask: null;
      question: null; blockingDetail: BlockingDetail });
export type QuestionTurnResult =
  | (AdvanceBase & { answerSaved: true; answerChanged: boolean; answerRevisionId: string;
      nextAction: 'wait_task'; rootTask: AiTaskSummary; question: null; blockingDetail: null })
  | (AdvanceBase & { answerSaved: true; answerChanged: boolean; answerRevisionId: string;
      nextAction: 'show_question'; rootTask: null; question: AdaptiveQuestion; blockingDetail: null })
  | (AdvanceBase & { answerSaved: true; answerChanged: boolean; answerRevisionId: string;
      nextAction: 'show_supplement' | 'review_evidence' | 'generate_script'; rootTask: null; question: null; blockingDetail: null })
  | (AdvanceBase & { answerSaved: true; answerChanged: boolean; answerRevisionId: string;
      nextAction: 'resolve_quota' | 'reconfirm_tariff'; rootTask: null; question: null; blockingDetail: BlockingDetail });

export const studioQueryKeys = {
  all: ['ai-video', 'studio'] as const,
  workspace: (workspaceKey: string) =>
    [...studioQueryKeys.all, 'workspace', workspaceKey] as const,
  draft: (workspaceKey: string, draftId: string) =>
    [...studioQueryKeys.workspace(workspaceKey), 'draft', draftId] as const,
  questionnaire: (workspaceKey: string, draftId: string, branchId: string) =>
    [...studioQueryKeys.draft(workspaceKey, draftId), 'questionnaire', branchId] as const,
  evidence: (workspaceKey: string, draftId: string, branchId: string) =>
    [...studioQueryKeys.draft(workspaceKey, draftId), 'evidence', branchId] as const,
};

export function submitQuestionTurn(draftId: string, body: SubmitQuestionTurnRequest) {
  return requestR<QuestionTurnResult>(
    `/api/studio/script-drafts/${encodeURIComponent(draftId)}/questionnaire/turns`,
    { method: 'POST', data: body });
}
```

```ts
it('sends exact turn body, unwraps R once and scopes query key by workspace/draft/branch', async () => {
  server.use(http.post('/api/studio/script-drafts/88/questionnaire/turns', async ({ request }) => {
    expect(await request.json()).toEqual({
      questionId: '33', selectedOptionCodes: ['option_01'],
      customSelected: false, customText: null,
      draftRevision: '11', branchRevision: '2',
      idempotencyKey: 'q-88-b2-n4-a', expectedTariffVersion: '7',
    });
    return HttpResponse.json({ code: 200, msg: '操作成功', data: blockedTurn });
  }));
  await expect(submitQuestionTurn('88', turnRequest)).resolves.toEqual(blockedTurn);
  expect(studioQueryKeys.questionnaire('org:9', '88', '701')).toEqual([
    'ai-video', 'studio', 'workspace', 'org:9', 'draft', '88',
    'questionnaire', '701',
  ]);
  expect(requestR).not.toHaveBeenCalledWith(expect.anything(),
    expect.objectContaining({ transformResponse: expect.anything() }));
});

it('publishes only the aggregate catalog version and sends no server-only sub-version', async () => {
  const catalog = await getDirectionOptions();
  expect(catalog).toEqual({
    catalogVersion: '20',
    industries: expect.any(Array),
    purposesByIndustry: expect.any(Object),
    targetDurations: expect.any(Array),
  });
  expect(catalog).not.toHaveProperty('industryCatalogVersion');
  expect(catalog).not.toHaveProperty('purposeCatalogVersion');
  expect(catalog).not.toHaveProperty('durationRuleVersion');

  const request: SaveDirectionRequest = {
    draftRevision: '11', branchRevision: '2', expectedCatalogVersion: '20',
    industryCode: 'local_life', industryCustomText: null,
    purposeCode: 'store_promotion', purposeCustomText: null,
    targetDurationSeconds: 60,
  };
  await saveDirection('88', request);
  expect(server.takeLastJsonBody()).toEqual(request);
  for (const forbidden of ['industryCatalogVersion','purposeCatalogVersion','durationRuleVersion']) {
    expect(server.takeLastJsonBody()).not.toHaveProperty(forbidden);
  }
});
```

`mock/aivideo-studio.ts` 至少原样提供这些完整服务端 payload；状态矩阵不得只写 fixture 名：

```ts
export const blockedTurn = {
  answerSaved: true, answerChanged: true, answerRevisionId: '301',
  draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
  reused: false, missingSlots: ['callToAction'],
  nextAction: 'resolve_quota', rootTask: null, question: null,
  blockingDetail: {
    businessCode: 46114,
    billingSubject: { type: 'organization', id: '9', name: '增长团队' },
    requiredQuota: '10', availableQuota: '2', lockedQuota: '0',
    previousTariffVersion: '7', currentTariffVersion: '7', currentUnitQuota: '10',
    draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
    resumeOperation: 'generate_next_question',
  },
} satisfies QuestionTurnResult;

export const showQuestion = {
  draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
  reused: false, missingSlots: ['audience'], nextAction: 'show_question', rootTask: null,
  question: {
    questionId: '33', questionNo: 2, targetSlotCode: 'audience',
    questionText: '这条视频主要面向谁？', selectionMode: 'multiple',
    sourceRootTaskId: '41',
    options: [
      { code: 'option_01', label: '新用户', recommended: true,
        normalizedValue: '新用户', slotContributions: ['audience'] },
      { code: 'option_02', label: '老客户', recommended: false,
        normalizedValue: '老客户', slotContributions: ['audience'] },
      { code: 'custom', label: '自定义', recommended: false,
        normalizedValue: null, slotContributions: [] },
    ],
  },
} satisfies QuestionnaireAdvanceResult;

export const supplementSnapshot = {
  subject: '社区咖啡店', audience: ['周边居民'], coreMessages: ['新鲜烘焙'],
  targetDurationSeconds: 60, callToAction: '到店体验',
  mustKeepFacts: ['每日现烘'], prohibitedContents: ['医疗功效'],
  toneStyles: [
    { code: 'authoritative', customText: null },
    { code: 'custom', customText: '克制而亲切' },
  ], otherNotes: null,
};

export const noResultsEvidence = {
  draftId: '88', branchId: '701', branchRevision: '2',
  batch: { batchId: '801', status: 'no_results', sourceCount: 0, factCount: 0 },
  sources: [], facts: [], decisions: [], nextAction: 'generate_script',
};
```

### 任务 10：实现方向选择和单题回答交互

**文件：** 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx`；修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts` 的 P2 局部受控状态。

**最小任务卡：**

- **单一目标／不做：** 实现方向、进度和单题本地交互；不接任务轮询、证据或根路由。
- **权威源：** 原业务规格、FRONTEND 指南、Ant Design 官方 API、Task 9 类型/Mock。
- **治理等级／触发项：** 黄色；触发用户自由文本、表单可访问性和提交防重，无真实计费编排。
- **实施者／reviewer／并发：** 开发 C writer，开发 A UX/a11y reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行八个 tracked 文件及 `ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task10-questionnaire-ui.json`；组件只持有当前 draft 本地草稿，服务端状态由 props/query 提供。
- **允许影响：** 可修改 DemandStep/model 的 P2 局部接口；禁止根路由、共享 task/quota adapter、P3 组件。
- **前置／退出：** Task 9；退出为三个 test file GREEN、a11y、边界和提交防重。
- **结构签名检查点：** reviewer 核对受控 props、无页面 URL、推荐项仅 visual marker、custom 独立状态和 ARIA/keyboard。
- **GREEN 独立复跑检查点：** reviewer 删除 task10 JSON，键盘完成方向/问题流程并独立跑 exact 三文件。
- **正向／反向验收：** 正向目录选择、2–6 普通项、多选、自定义 500 字、进度；反向拒绝推荐自动提交、`custom` 混入普通 codes、未勾选文本发请求、501 字、未知时长、双击提交。
- **统一 gate：** Task 1 worktree gate；调用方 `AllowedPaths` 精确传八个 tracked 文件，Vitest gate 自动追加该 evidence JSON，覆盖三个 repo-relative 测试文件。
- **准确命令／证据：** `$repoRoot = (& git rev-parse --show-toplevel).Trim(); $webRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'; $reportRelative = 'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task10-questionnaire-ui.json'; $report = Join-Path $repoRoot $reportRelative; $allowed = @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts'); $started = [DateTimeOffset]::UtcNow; Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue; Push-Location $webRoot; try { npm.cmd test -- src/pages/digital-human-studio/components/DirectionForm.test.tsx src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx --reporter=json --outputFile=$report; if ($LASTEXITCODE -ne 0) { throw 'task10 Vitest GREEN 失败' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'task10 lint 失败' } } finally { Pop-Location }; $gateText = (& git -C $repoRoot rev-parse --git-path 'p2-vitest-evidence-gate.ps1').Trim(); $vitestGate = if ([IO.Path]::IsPathRooted($gateText)) { $gateText } else { Join-Path $repoRoot $gateText }; $gateResult = & $vitestGate -RepoRoot $repoRoot -AllowedPaths $allowed -ReportRelativePath $reportRelative -ExpectedTestFiles @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx') -Phase GREEN -StartedAtUtc $started; if ($LASTEXITCODE -ne 0 -or $gateResult -cne 'P2_VITEST_EVIDENCE_OK') { throw 'task10 Vitest evidence gate 失败' }`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：必须先使用项目 `antd` skill，通过官方 CLI 文档核验所用 Form/Select/Checkbox/Input/Progress/Alert API、semantic DOM 与 token；再读现有组件样式。
- [ ] RED：精确测试加载/方向空/校验、单题/推荐仅标记/自定义勾选与 500 code point、防重复提交和键盘；删除 JSON，npm 非零且 Vitest gate RED。
- [ ] GREEN：使用 Ant Design 受控组件，统一 loading/empty/error/403/disabled，复用错误和请求防重。
- [ ] GREEN 验证：npm 0、Vitest gate GREEN、lint 0；无裸 API 路径。
- [ ] review/提交：独立键盘复跑后提交 `feat(studio): add adaptive questionnaire UI`。

可复制 UI RED fixture 与最小提交逻辑；实现前仍必须用项目 `antd` skill/CLI 核验实际组件 API，测试契约不授权凭记忆写 Ant Design props：

```tsx
const directionCatalogFixture = {
  catalogVersion: '20',
  industries: [
    { code: 'local_life', label: '本地生活', customAllowed: false },
    { code: 'custom', label: '自定义', customAllowed: true },
  ],
  purposesByIndustry: {
    local_life: [{ code: 'store_promotion', label: '门店推广', customAllowed: false }],
  },
  targetDurations: [30, 45, 60, 90, 120].map((seconds) => ({ seconds, label: `${seconds} 秒` })),
};
const questionTwoFixture: AdaptiveQuestion = {
  questionId: '33', questionNo: 2, targetSlotCode: 'audience',
  questionText: '这条视频主要面向谁？', selectionMode: 'multiple', sourceRootTaskId: '41',
  options: [
    { code: 'option_01', label: '新用户', recommended: true,
      normalizedValue: '新用户', slotContributions: ['audience'] },
    { code: 'option_02', label: '老客户', recommended: false,
      normalizedValue: '老客户', slotContributions: ['audience'] },
    { code: 'custom', label: '自定义', recommended: false,
      normalizedValue: null, slotContributions: [] },
  ],
};

it('keeps unselected direction text locally, omits it, and renders only current question', async () => {
  const user = userEvent.setup();
  const onDirectionSubmit = vi.fn();
  const onAnswer = vi.fn();
  const { rerender } = render(
    <DirectionForm
      catalogs={directionCatalogFixture}
      value={null}
      submitting={false}
      onSubmit={onDirectionSubmit}
    />,
  );
  await user.selectOptions(screen.getByLabelText('行业'), 'custom');
  await user.type(screen.getByLabelText('自定义行业'), '社区精品咖啡');
  await user.selectOptions(screen.getByLabelText('行业'), 'local_life');
  await user.selectOptions(screen.getByLabelText('用途'), 'store_promotion');
  await user.click(screen.getByRole('button', { name: '保存方向' }));
  expect(screen.getByLabelText('自定义行业')).toHaveValue('社区精品咖啡');
  expect(onDirectionSubmit).toHaveBeenCalledWith(expect.objectContaining({
    expectedCatalogVersion: '20',
    industryCode: 'local_life', industryCustomText: null,
    purposeCode: 'store_promotion', purposeCustomText: null,
  }));
  expect(onDirectionSubmit.mock.calls[0][0]).not.toEqual(expect.objectContaining({
    industryCatalogVersion: expect.anything(), purposeCatalogVersion: expect.anything(),
    durationRuleVersion: expect.anything(),
  }));

  rerender(<AdaptiveQuestionCard question={questionTwoFixture}
    submitting={false} onSubmit={onAnswer} />);
  expect(screen.getByText('第 2 题 / 最多 5 题')).toBeInTheDocument();
  expect(screen.queryByText('第 1 题')).not.toBeInTheDocument();
  expect(screen.queryByText('第 3 题')).not.toBeInTheDocument();
  await user.click(screen.getByLabelText('新用户'));
  await user.click(screen.getByLabelText('自定义'));
  await user.type(screen.getByLabelText('自定义答案'), '周末亲子客群');
  await user.click(screen.getByRole('button', { name: '提交答案' }));
  expect(onAnswer).toHaveBeenCalledWith({
    selectedOptionCodes: ['option_01'],
    customSelected: true,
    customText: '周末亲子客群',
  });
});

it('does not auto-select recommendations and blocks the second click', async () => {
  const user = userEvent.setup();
  const onSubmit = vi.fn(() => new Promise(() => undefined));
  render(<AdaptiveQuestionCard question={questionTwoFixture}
    submitting={false} onSubmit={onSubmit} />);
  expect(screen.getByText('推荐')).toBeInTheDocument();
  expect(screen.getByLabelText('新用户')).not.toBeChecked();
  await user.click(screen.getByLabelText('新用户'));
  await user.dblClick(screen.getByRole('button', { name: '提交答案' }));
  expect(onSubmit).toHaveBeenCalledTimes(1);
});
```

```tsx
const submit = () => {
  if (submitting || submitLock.current) return;
  submitLock.current = true;
  const selectedOptionCodes = [...selected]
    .filter((code) => code !== 'custom').sort(codePointCompare);
  const customSelected = selected.has('custom');
  void onSubmit({
    selectedOptionCodes,
    customSelected,
    ...(customSelected ? { customText: normalizeText(customText) } : { customText: null }),
  }).finally(() => { submitLock.current = false; });
};
```

### 任务 11：实现任务轮询、额度/费率确认和幂等恢复

**文件：** 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx`。

**最小任务卡：**

- **单一目标／不做：** 实现收费任务、额度/费率和网络恢复；不实现补充/证据内容。
- **权威源：** P0-C tasks/quota adapter、ASYNC_TASKS、原业务规格轮询/幂等/取消语义。
- **治理等级／触发项：** 红色；触发真实收费、幂等键、取消、登录退出、重试和后台轮询。
- **实施者／reviewer／并发：** 开发 C writer，开发 B 任务/额度 reviewer，最多 2 人。
- **精确路径／数据范围：** 仅本卡“文件”行八个 tracked 文件及 `ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task11-task-recovery.json`；缓存/定时器按 workspace/draft/branch/rootTaskId 隔离。
- **允许影响：** 可消费 P0-C 公共 adapter；禁止修改其实现、自动生成新幂等键、把取消显示为失败。
- **前置／退出：** P0-C 前端公共契约与 Task 9；退出为四 test file、fake timer、错误/恢复矩阵 GREEN。
- **结构签名检查点：** reviewer 核对轮询状态机、timer cleanup、AbortController、单次 401 logout、显式用户 retry 和 query invalidation 范围。
- **GREEN 独立复跑检查点：** reviewer 删除 task11 JSON，用 fake timers/online/focus 独立复跑，确认无悬挂 timer 或重复收费调用。
- **正向／反向验收：** 正向 1s→2s、终态、恢复、额度/费率确认、reused，以及收费证据任务 `success/no_results` 按正常成功结算且只显示“未找到资料”；反向拒绝 401 循环、取消=失败、网络超时/5xx 无限重试、自动换 key、终态轮询、跨分支缓存、把 `no_results` 当失败退款或再次确认收费。
- **统一 gate：** Task 1 worktree gate；调用方 `AllowedPaths` 精确传八个 tracked 文件，Vitest gate 自动追加该 evidence JSON，expected 四个 repo-relative 文件。
- **准确命令／证据：** `$repoRoot = (& git rev-parse --show-toplevel).Trim(); $webRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'; $reportRelative = 'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task11-task-recovery.json'; $report = Join-Path $repoRoot $reportRelative; $allowed = @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.ts','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.ts','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx'); $started = [DateTimeOffset]::UtcNow; Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue; Push-Location $webRoot; try { npm.cmd test -- src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx --reporter=json --outputFile=$report; if ($LASTEXITCODE -ne 0) { throw 'task11 Vitest GREEN 失败' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'task11 lint 失败' } } finally { Pop-Location }; $gateText = (& git -C $repoRoot rev-parse --git-path 'p2-vitest-evidence-gate.ps1').Trim(); $vitestGate = if ([IO.Path]::IsPathRooted($gateText)) { $gateText } else { Join-Path $repoRoot $gateText }; $gateResult = & $vitestGate -RepoRoot $repoRoot -AllowedPaths $allowed -ReportRelativePath $reportRelative -ExpectedTestFiles @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx') -Phase GREEN -StartedAtUtc $started; if ($LASTEXITCODE -ne 0 -or $gateResult -cne 'P2_VITEST_EVIDENCE_OK') { throw 'task11 Vitest evidence gate 失败' }`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：先用项目 `antd` skill/官方 CLI 核验 Modal/Alert/Progress/Button API；读取 P0-C hook/adapter 相似实现。
- [ ] RED：fake timers 覆盖前 10 秒每秒、之后每 2 秒、终态/离页/branch change/三次网络失败停止、online/focus 单次刷新；覆盖 timeout/5xx 有界用户重试、401 单次退出、取消非失败、过期、46123、额度、费率、reused、`success/no_results`；额度补足或费率确认后的恢复必须调用 questionnaire `start`，携带已保存答案返回的修订、原操作槽 key 和确认后的价格版本，明确断言 turn submit 零次；删除 JSON并取得 Vitest gate RED。
- [ ] GREEN：同一操作槽稳定保存 key，只有用户明确新操作才换；阻塞恢复从已保存 operation 调 `startQuestionnaire`，不得重新提交答案、不得改当前题或泄漏未勾选 custom 本地文本；失败只在用户点击后重试；success/no_results 精确 invalidate 且不触发前端退款或二次收费确认；所有 effect cleanup。
- [ ] GREEN 验证：npm 0、Vitest gate GREEN、lint 0；请求计数和 timer 数均精确。
- [ ] review/提交：独立 fake timer 复跑后提交 `feat(studio): recover questionnaire tasks`。

可复制的恢复 RED fixture 与最小实现（测试中的对象为完整 HTTP payload，不用 fixture 名代替）：

```tsx
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const blockedTurn: QuestionTurnResult = {
  answerSaved: true, answerChanged: true, answerRevisionId: '301',
  draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
  reused: false, missingSlots: ['callToAction'], nextAction: 'resolve_quota',
  rootTask: null, question: null,
  blockingDetail: {
    businessCode: 46114,
    billingSubject: { type: 'organization', id: '9', name: '增长团队' },
    requiredQuota: '10', availableQuota: '2', lockedQuota: '0',
    previousTariffVersion: '7', currentTariffVersion: '7', currentUnitQuota: '10',
    draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
    resumeOperation: 'generate_next_question',
  },
};
const pendingOperation: PendingQuestionOperation = {
  workspaceKey: 'org:9', draftId: '88', draftRevision: '12', branchRevision: '2',
  idempotencyKey: 'q-88-b2-n4-a', expectedTariffVersion: '7',
};

describe('GenerationCostConfirm', () => {
  it('resumesSavedAnswerWithStartAndNeverSubmitsTheTurnAgain', async () => {
    const startQuestionnaire = vi.fn().mockResolvedValue({
      draftRevision: '12', branchRevision: '2', generationContextRevision: '12',
      reused: true, missingSlots: [], nextAction: 'wait_task', question: null,
      rootTask: { rootTaskId: '501', status: 'pending', progress: 0 },
    });
    const submitQuestionTurn = vi.fn();
    render(<GenerationCostConfirm blocking={blockedTurn.blockingDetail}
      onConfirm={() => resumeBlockedTurn(
        pendingOperation, blockedTurn.blockingDetail.currentTariffVersion,
        startQuestionnaire)} />);

    fireEvent.click(screen.getByRole('button', { name: '确认并继续' }));

    expect(await screen.findByText('任务已创建')).toBeInTheDocument();
    expect(startQuestionnaire).toHaveBeenCalledTimes(1);
    expect(startQuestionnaire).toHaveBeenCalledWith('88', {
      draftRevision: '12', branchRevision: '2',
      idempotencyKey: 'q-88-b2-n4-a', expectedTariffVersion: '7',
    });
    expect(submitQuestionTurn).not.toHaveBeenCalled();
  });
});

export interface PendingQuestionOperation {
  workspaceKey: string;
  draftId: string;
  draftRevision: string;
  branchRevision: string;
  idempotencyKey: string;
  expectedTariffVersion: string;
}

export async function resumeBlockedTurn(
  operation: PendingQuestionOperation,
  confirmedTariffVersion: string,
  start: typeof startQuestionnaire,
) {
  return start(operation.draftId, {
    draftRevision: operation.draftRevision,
    branchRevision: operation.branchRevision,
    idempotencyKey: operation.idempotencyKey,
    expectedTariffVersion: confirmedTariffVersion,
  });
}
```

同一 test file 还必须用 fake timers 给出 `0/1/.../10/12` 秒精确请求序列和 cleanup 后 timer=0；`TaskProgressPanel.test.tsx` 直接喂入 `{rootTaskId:'601',status:'success',resultType:'evidence_batch',resultStatus:'no_results',settlementStatus:'settled',chargedQuota:'10'}`，断言“未找到资料”、成功样式、退款/重提/费率确认按钮均不存在。

### 任务 12：实现补充字段和证据审核 UI

**文件：** 创建 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx`；修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx` 的受控组合接口。

**最小任务卡：**

- **单一目标／不做：** 实现九字段补充和逐条证据决定 UI；不改根路由、不触发免费补充计费。
- **权威源：** 原业务规格、第 1.2/1.3 节、Task 7 evidence DTO/API、FRONTEND 指南。
- **治理等级／触发项：** 黄色；触发用户事实确认、危险链接、长文本与 revision conflict。
- **实施者／reviewer／并发：** 开发 C writer，开发 A 产品/a11y reviewer，最多 2 人。
- **精确路径／数据范围：** 只允许本卡“文件”行五个 tracked 文件及 `ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task12-supplement-evidence.json`；状态按当前 workspace/draft/branch/factId 隔离。
- **允许影响：** 可组合 DemandStep 受控 props；禁止根入口、P3、直接请求 URL、自动接受事实。
- **前置／退出：** Tasks 5/7/9；退出为两个 exact test file、键盘、长度/冲突/确认矩阵 GREEN。
- **结构签名检查点：** reviewer 核对九字段、四个字符串数组，以及 `toneStyles` 最多 5 个 `{code,customText}` 对象、code 排序去重、普通项 null、唯一 custom 1–200；同时核对总 code point、accepted/rejected 两值、冲突事实 disabled、外链安全属性和危险操作二次确认。
- **GREEN 独立复跑检查点：** reviewer 删除 task12 JSON，用键盘完成补充/逐条决定并独立运行 gate。
- **正向／反向验收：** 正向补充保存、普通/custom tone 对象、证据初始空/收费搜索 `no_results`/分页/接受/拒绝；反向拒绝 16,001、tone 字符串数组、超过 5 项、重复 code、普通项携带 customText、两个 custom、customText 0/201、把 `no_results` 渲染成失败、冲突默认接受、未保存决定进入 context、revision conflict 静默覆盖、危险批量替换无二次确认。
- **统一 gate：** Task 1 worktree gate；调用方 `AllowedPaths` 精确传五个 tracked 文件，Vitest gate自动追加该 evidence JSON，expected 两个 repo-relative 文件。
- **准确命令／证据：** `$repoRoot = (& git rev-parse --show-toplevel).Trim(); $webRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'; $reportRelative = 'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task12-supplement-evidence.json'; $report = Join-Path $repoRoot $reportRelative; $allowed = @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx'); $started = [DateTimeOffset]::UtcNow; Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue; Push-Location $webRoot; try { npm.cmd test -- src/pages/digital-human-studio/components/SupplementFields.test.tsx src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx --reporter=json --outputFile=$report; if ($LASTEXITCODE -ne 0) { throw 'task12 Vitest GREEN 失败' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'task12 lint 失败' } } finally { Pop-Location }; $gateText = (& git -C $repoRoot rev-parse --git-path 'p2-vitest-evidence-gate.ps1').Trim(); $vitestGate = if ([IO.Path]::IsPathRooted($gateText)) { $gateText } else { Join-Path $repoRoot $gateText }; $gateResult = & $vitestGate -RepoRoot $repoRoot -AllowedPaths $allowed -ReportRelativePath $reportRelative -ExpectedTestFiles @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx') -Phase GREEN -StartedAtUtc $started; if ($LASTEXITCODE -ne 0 -or $gateResult -cne 'P2_VITEST_EVIDENCE_OK') { throw 'task12 Vitest evidence gate 失败' }`。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：先用项目 `antd` skill/官方 CLI 核验 Form/List/Pagination/Alert/Modal/Checkbox/Radio API、semantic DOM 和 token；读取现有安全外链组件。
- [ ] RED：覆盖九字段 exact UI、3.6 全部逐字段上下界、16,000 code point、四个字符串数组按值排序去重；`usesSortedToneObjectsAndOneCustomTone` 覆盖最多 5 个 tone 对象、普通 code 1/100、customText 1/200、排序去重及字符串数组/重复/多 custom/越界反例；再用完整 payload 覆盖证据初始空、收费搜索 `no_results`、分页、失败、冲突、accepted/rejected、expected revision、批量替换二次确认；删除 JSON并取得 Vitest gate RED。
- [ ] GREEN：表单提交保持 `{code,customText}` 对象结构，普通项强制 null、自定义仅一项且不自动提交；冲突事实不可接受并解释；来源新窗使用 `noopener noreferrer`；保存成功只刷新当前 key；失败仅用户点击重试。
- [ ] GREEN 验证：npm 0、Vitest gate GREEN、lint/a11y 0。
- [ ] review/提交：独立键盘复跑后提交 `feat(studio): review questionnaire evidence`。

可复制的补充/证据 RED fixture 与最小请求构造器：

```tsx
import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

const supplement: SaveSupplementRequest = {
  draftRevision: '12', branchRevision: '2', subject: '社区咖啡店',
  audience: ['周边居民'], coreMessages: ['每日新鲜烘焙'],
  targetDurationSeconds: 60, callToAction: '到店体验',
  mustKeepFacts: ['每日现烘'], prohibitedContents: ['医疗功效'],
  toneStyles: [
    { code: 'authoritative', customText: null },
    { code: 'custom', customText: '克制而亲切' },
  ], otherNotes: null,
};

it('usesSortedToneObjectsAndOneCustomTone', async () => {
  const save = vi.fn();
  render(<SupplementFields initialValue={supplement} missingSlots={['audience']}
    onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '保存补充信息' }));
  expect(save).toHaveBeenCalledWith({
    ...supplement,
    toneStyles: [
      { code: 'authoritative', customText: null },
      { code: 'custom', customText: '克制而亲切' },
    ],
  });
});

it.each([
  [{ ...supplement, targetDurationSeconds: 31 }, '请选择 30、45、60、90 或 120 秒'],
  [{ ...supplement, audience: [] }, '受众至少填写 1 项'],
  [{ ...supplement, toneStyles: Array.from({ length: 6 }, (_, i) =>
      ({ code: `tone_${i}`, customText: null })) }, '风格最多选择 5 项'],
  [{ ...supplement, toneStyles: [
      { code: 'custom', customText: '一' }, { code: 'custom', customText: '二' }] },
    '只能填写一个自定义风格'],
])('rejectsEveryNumericMatrixViolation', async (invalid, message) => {
  const save = vi.fn();
  render(<SupplementFields initialValue={invalid as SaveSupplementRequest}
    missingSlots={['audience']} onSave={save} />);
  fireEvent.click(screen.getByRole('button', { name: '保存补充信息' }));
  expect(await screen.findByText(message)).toBeInTheDocument();
  expect(save).not.toHaveBeenCalled();
});

const noResults: EvidenceSnapshot = {
  draftId: '88', branchId: '701', branchRevision: '2',
  batch: { batchId: '801', status: 'no_results', sourceCount: 0, factCount: 0 },
  sources: [], facts: [], decisions: [], nextAction: 'generate_script',
};
const conflicted: EvidenceSnapshot = {
  draftId: '88', branchId: '701', branchRevision: '2',
  batch: { batchId: '802', status: 'completed', sourceCount: 2, factCount: 1 },
  sources: [
    { sourceId: '901', title: '来源 A', safeUrl: 'https://example.com/a' },
    { sourceId: '902', title: '来源 B', safeUrl: 'https://example.org/b' },
  ],
  facts: [{ factId: '1001', text: '营业时间为 8:00', status: 'conflicted',
    sourceIds: ['901', '902'], decision: null, decisionRevision: '0' }],
  decisions: [], nextAction: 'review_evidence',
};

it('separatesPaidNoResultsFromInitialEmptyAndBlocksConflictedAccept', () => {
  const { rerender } = render(<EvidenceReviewPanel snapshot={noResults} />);
  expect(screen.getByText('未找到资料')).toBeInTheDocument();
  expect(screen.queryByText('检索失败')).not.toBeInTheDocument();
  rerender(<EvidenceReviewPanel snapshot={conflicted} />);
  const row = screen.getByText('营业时间为 8:00').closest('[data-fact-id]')!;
  expect(within(row).getByRole('radio', { name: '接受' })).toBeDisabled();
  expect(within(row).getByText('来源存在冲突，请先核实')).toBeInTheDocument();
});

export function buildSupplementRequest(value: SaveSupplementRequest) {
  return {
    ...value,
    audience: [...new Set(value.audience.map(normalizeText))].sort(codePointCompare),
    coreMessages: [...new Set(value.coreMessages.map(normalizeText))].sort(codePointCompare),
    mustKeepFacts: [...new Set(value.mustKeepFacts.map(normalizeText))].sort(codePointCompare),
    prohibitedContents: [...new Set(value.prohibitedContents.map(normalizeText))]
      .sort(codePointCompare),
    toneStyles: normalizeAndValidateToneStyles(value.toneStyles),
  } satisfies SaveSupplementRequest;
}
```

完整矩阵还必须逐项覆盖 `subject 1/2000`、`audience 1..8 × 1/100`、`coreMessages 1..8 × 1/300`、`callToAction 1/500`、`mustKeepFacts 1..10 × 1/500`、`prohibitedContents 1..10 × 1/300`、`otherNotes 0/1/2000/2001` 和总计 `16000/16001`，每个失败都断言 `onSave` 零次；证据失败 fixture 必须包含安全 `traceId` 而不泄漏 provider 原文。

### 任务 13：串行接入工作台，执行全量门禁并冻结 F3

**文件：** 修改 `ai-video-ui/ai-video-webapp/src/app.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioTopbar.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx`；创建 `ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/QuestionnaireEndToEndIT.java`；证据和 handoff 只写当前 worktree 的 Git metadata，不写仓库文件。

**最小任务卡：**

- **单一目标／不做：** 串行集成工作台、执行 P2 全量验收并冻结 F3；不实现 P3 业务、不替 P3 删除 fake 或签署 F4。
- **权威源：** master Task 6 代表性硬门禁、F1/F2 handoff、原业务规格、前 12 卡和 Section 7 strict schema。
- **治理等级／触发项：** 红色；触发共享入口、全链收费/外调、F3 证据、独立审核和下游 rebase。
- **实施者／reviewer／并发：** 开发 C 负责实现/证据但不得判 PASS，开发 B 独立 reviewer，最多 2 人。
- **精确路径／数据范围：** 修改 `ai-video-ui/ai-video-webapp/src/app.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioTopbar.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx`、`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx`；创建 `ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/QuestionnaireEndToEndIT.java`；另只生成 `ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task13-demand-e2e.json`，其余证据/review/handoff 只写当前 worktree Git metadata；E2E 只用安全本机 test DB/Redis。
- **允许影响：** 可串行合入 P2 共享根文件、创建六类证据和 F3；禁止修改 P3 文件、P4、开发/生产数据、让 writer 写 review。
- **前置／退出：** Tasks 1–12、F1/F2 严格校验、`p2-f1-rebase.json` 与 `p2-f2-rebase.json` 完成记录；退出为 Java 20/20、Surefire 14/14、Failsafe 6/6、IT profile 6/6、Vitest 11/11/至少五 JSON、标准/扫描、独立 PASS 和幂等 handoff。
- **结构签名检查点：** reviewer 核对 HEAD 祖先、20 selector/report 注册表、六 Service/DTO registry、06 replay、P3 consumer contract、六 manifest exact schema。
- **GREEN 独立复跑检查点：** reviewer 在同一 candidate HEAD 的 acceptance window 内重跑代表性硬门禁、重算所有 manifest/artifact hash 后才能 CreateNew review。
- **正向／反向验收：** 正向完整状态矩阵、工作区切换 `cancel→clear→reload`、全链/E2E、F3 回读；反向拒绝 403 时回退到个人工作区、错误/漂移 HEAD、旧报告、零测试、skip、hash/mtime 漂移、字段/类型冒充、writer 自签、不同 payload 覆盖和 P3 越界消费。
- **统一 gate：** Task 1 worktree gate `-Phase final -RequireClean -ExpectedOriginalF1Head/-ExpectedF1Head/-ExpectedF2Head`，其中两个 F1 参数分别是不可变原始 head 与 addendum amendment head；所有 JVM/Vitest/manifest 证据使用集中 gate。
- **准确命令／证据：** E2E 命令 `-pl :ai-video-user-api -am -Dmaven.test.skip=false -DskipTests=false -DskipITs=false -Dfailsafe.failIfNoSpecifiedTests=false '-Dit.test=org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT' '-Pdev,local-integration-test' verify`，报告 `ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT.xml`。前端命令 `$repoRoot = (& git rev-parse --show-toplevel).Trim(); $webRoot = Join-Path $repoRoot 'ai-video-ui/ai-video-webapp'; $reportRelative = 'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task13-demand-e2e.json'; $report = Join-Path $repoRoot $reportRelative; $allowed = @('ai-video-ui/ai-video-webapp/src/app.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/index.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/StudioTopbar.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/model.ts','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.tsx','ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx','ai-video-api/ai-video-user-api/src/test/java/org/dromara/aivideo/bootstrap/QuestionnaireEndToEndIT.java'); $started = [DateTimeOffset]::UtcNow; Remove-Item -LiteralPath $report -Force -ErrorAction SilentlyContinue; Push-Location $webRoot; try { npm.cmd test -- src/pages/digital-human-studio/steps/DemandStep.test.tsx --reporter=json --outputFile=$report; if ($LASTEXITCODE -ne 0) { throw 'task13 Vitest GREEN 失败' }; npm.cmd run lint; if ($LASTEXITCODE -ne 0) { throw 'task13 lint 失败' }; npm.cmd run build; if ($LASTEXITCODE -ne 0) { throw 'task13 build 失败' } } finally { Pop-Location }; $gateText = (& git -C $repoRoot rev-parse --git-path 'p2-vitest-evidence-gate.ps1').Trim(); $vitestGate = if ([IO.Path]::IsPathRooted($gateText)) { $gateText } else { Join-Path $repoRoot $gateText }; $gateResult = & $vitestGate -RepoRoot $repoRoot -AllowedPaths $allowed -ReportRelativePath $reportRelative -ExpectedTestFiles @('ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx') -Phase GREEN -StartedAtUtc $started; if ($LASTEXITCODE -ne 0 -or $gateResult -cne 'P2_VITEST_EVIDENCE_OK') { throw 'task13 Vitest evidence gate 失败' }`。全量命令逐模块运行 Section 5.5 的精确 selector，禁止只用聚合退出码代替每份 gate。
- **固定输出：** `完成项`、`风险`、`验证证据`、`阻塞项`。

- [ ] 前置阅读：再次读取 RuoYi skill/backend reference、generator 与相似启动模块；前端先用 `antd` skill/官方 CLI 核验根集成组件 API；E2E `@Tag("dev")` 且首步 require local environment。
- [ ] 输入/rebase 记录验收：Task 13 不再首次执行 rebase；严格读取不可覆盖的 `p0c-f1-handoff.json`、`p0c-f1-contract-addendum.json`、`p1-f2-handoff.json` 及四份 pending/完成记录，先证明 `originalF1Head` 是 `amendmentHead` 祖先，再验证 F1 的 before/after/merge-base 与 `amendmentHead`、F2 的 before/after/merge-base 与 `f2Head`；最终执行 `git merge-base --is-ancestor <originalF1Head> HEAD`、`git merge-base --is-ancestor <f2Head> HEAD`，并扫描最终源码/迁移确认 addendum 的两个方法、member 表和 active index 仍存在。F2 若基于原始 F1，第二次 rebase 重放 amendment 后原 amendment SHA 不要求继续为祖先，但完成记录必须保留该 SHA；缺任一真实 rebase 记录立即停止。生产源码和全部真实 IT 删除 P1 fake。
- [ ] 迁移/后端：执行唯一持久化 selector `QuestionnaireMigrationIT.appliesMigrationsOneThroughSixAndReplaysP2Migration`，它同时证明迁移链/replay、Mapper 约束、跨 workspace 与唯一并发；再逐一运行 14 Surefire + 6 Failsafe selector，删除 exact XML/记录 start/调用 JVM gate GREEN；不得把编译失败或未选中测试计入。通过后以 `FileMode.CreateNew` 创建 Git metadata `p2-evidence/<f3Head>/migration-06-replay.log`，内容必须包含独占行 `P2_MIGRATION_06_REPLAY_OK`，不得覆盖旧文件。
- [ ] master 代表性硬门禁：逐字存在并执行 `QuestionGenerationOutputValidatorTest.rejectsEveryStructuralViolationBeforeAttemptCanComplete`、`questionnaire/evidence/AllowedExternalUriPolicyTest.rejectsUnsafeEvidenceUris`、`QuestionnaireMigrationIT.appliesMigrationsOneThroughSixAndReplaysP2Migration`，逐份 fresh XML。
- [ ] 前端：串行合入六根文件；工作区切换固定先 abort/cancel 旧 workspace 请求与轮询，再 `removeQueries`/清理旧 workspace 本地 operation/custom 草稿，最后只加载新 workspace；新 workspace 403 显示权限态且不得回退个人工作区。执行 task9/10/11/12/13 至少五份 JSON，合计精确 11 test files；再运行 `npm.cmd run lint`、`npm.cmd run build`，按 Section 5.6 逐行验收 API+Mock+组件。
- [ ] P3 下游契约：handoff 明确 P3 必须 rebase 精确 `f3Head`，随后从 production 和全部真实 `*IT.java` 删除 P2 fake；只 import `IQuestionnaireContextService`、`IEvidenceReviewService` 和六 DTO；P2 Mapper/表/Entity/user VO 零依赖。F3 必须现场构造并严格回读六 DTO component registry/component hash/source SHA、两个 Service 完整 signature/source SHA、locked-current-branch protocol、P2 write-guard protocol、identity/revision/order semantics；`p3ConsumerContract` 内嵌同一组对象而非只列名称。该 contract 与零漂移 scan 是 handoff 必填证据。
- [ ] 标准/扫描：运行开发标准、20/20 selector/report/Tag/profile 扫描、core 分层、P1 Mapper/表、P1 fake、P2 registry、敏感数据、PowerShell native/LF/CRLF AST 0、`git diff --check`；检查 `$LASTEXITCODE` 后分别以 `FileMode.CreateNew` 创建 Git metadata `p2-evidence/<f3Head>/standards.log`、`p2-evidence/<f3Head>/scan.log`，末行分别为 `P2_STANDARDS_OK`、`P2_SCAN_OK`，不得覆盖旧文件。
- [ ] 六 manifest：使用 manifest gate CreateNew/回读路径精确为 `p2-evidence/<f3Head>/<kind>.manifest.json` 的 `unit/it/migration/vitest/standards/scan`；`unit` artifact 集合必须恰为 Section 5.5 的 14 个 Surefire XML，`it` 恰为 6 个 Failsafe XML，`migration` 恰为 migration XML + `migration-06-replay.log`，`vitest` 恰含 task9–13 五份 fresh JSON（满足至少五份门槛）且其 `testResults` 并集恰为 11 文件，`standards/scan` 各恰为对应日志；全部由 F3 freeze 逐份实时复验 suite/selector/计数/GREEN/hash/bytes/mtime/window。
- [ ] review/handoff：开发 B 独立重算后执行 Section 7 review CreateNew；writer 再执行 F3 freeze；同 HEAD/payload 二次运行 hash/capturedAt 不变，不同 payload/HEAD 拒绝覆盖。
- [ ] final：worktree gate `-Phase final -RequireClean` 返回 sentinel 后移交 P3，不 push。

可复制的工作区切换 RED fixture 与最小 hook：

```tsx
import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it, vi } from 'vitest';

it('cancelsClearsAndReloadsWithoutForbiddenFallback', async () => {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const cancelQueries = vi.spyOn(queryClient, 'cancelQueries');
  const removeQueries = vi.spyOn(queryClient, 'removeQueries');
  const clearLocalOperation = vi.fn();
  const loadDraft = vi.fn()
    .mockResolvedValueOnce({ draftId: '88', workspaceKey: 'org:9' })
    .mockRejectedValueOnce({ businessCode: 46127, message: '无权访问该工作区' });
  const wrapper = ({ children }: React.PropsWithChildren) =>
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  const { result } = renderHook(() => useStudioDraft({
    queryClient, loadDraft, clearLocalOperation,
    initialWorkspaceKey: 'org:9', draftId: '88',
  }), { wrapper });
  await waitFor(() => expect(loadDraft).toHaveBeenCalledWith('org:9', '88', expect.any(AbortSignal)));

  await act(() => result.current.switchWorkspace('org:10'));

  expect(cancelQueries).toHaveBeenCalledWith({
    queryKey: studioQueryKeys.workspace('org:9'), exact: false,
  });
  expect(removeQueries).toHaveBeenCalledWith({
    queryKey: studioQueryKeys.workspace('org:9'), exact: false,
  });
  expect(clearLocalOperation).toHaveBeenCalledWith('org:9', '88');
  expect(loadDraft).toHaveBeenLastCalledWith('org:10', '88', expect.any(AbortSignal));
  expect(result.current.state).toEqual({ kind: 'forbidden', workspaceKey: 'org:10' });
  expect(loadDraft).not.toHaveBeenCalledWith('personal', '88', expect.anything());
  expect(cancelQueries.mock.invocationCallOrder[0])
    .toBeLessThan(removeQueries.mock.invocationCallOrder[0]);
  expect(removeQueries.mock.invocationCallOrder[0])
    .toBeLessThan(clearLocalOperation.mock.invocationCallOrder[0]);
});

export async function switchStudioWorkspace(
  previousWorkspaceKey: string,
  nextWorkspaceKey: string,
  draftId: string,
  deps: StudioWorkspaceSwitchDependencies,
) {
  await deps.queryClient.cancelQueries({
    queryKey: studioQueryKeys.workspace(previousWorkspaceKey), exact: false,
  });
  deps.queryClient.removeQueries({
    queryKey: studioQueryKeys.workspace(previousWorkspaceKey), exact: false,
  });
  deps.clearLocalOperation(previousWorkspaceKey, draftId);
  return deps.loadDraft(nextWorkspaceKey, draftId, deps.nextAbortSignal());
}
```

可复制的 E2E RED fixture 必须从真实 HTTP 入口贯穿 actor/workspace/方向/三题/补充/证据 `no_results`/脚本写锁，不用 Service mock：

```java
@Tag("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuestionnaireEndToEndIT {
    private static final LocalIntegrationEnvironment ENV =
        LocalIntegrationEnvironment.requireFromEnvironment();

    @LocalServerPort int port;
    @Autowired TestRestTemplate http;

    @BeforeEach
    void reset() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        ENV.clearCurrentRunRedisKeys();
        ENV.applyMigrations("01", "02", "03", "04", "05", "06");
    }

    @Test
    void completesQuestionnaireAndTreatsPaidNoResultsAsSettledSuccess() {
        AppSessionFixture app = ENV.createAppUserInOrganization(1001L, 9L);
        HttpHeaders headers = app.authenticatedHeaders();
        String draftId = createDraft(headers, "org:9");
        putDirection(headers, draftId, "1", "1", "food", "promotion", 60);
        QuestionnaireAdvanceVo first = start(headers, draftId, "2", "1", "q-e2e-1", "7");
        QuestionnaireAdvanceVo second = submit(headers, draftId, first.question(), "option_01");
        QuestionnaireAdvanceVo third = submit(headers, draftId, second.question(), "option_02");
        QuestionnaireAdvanceVo supplement = submit(headers, draftId, third.question(), "option_01");
        saveNineFieldSupplement(headers, draftId, supplement.draftRevision(), supplement.branchRevision());
        EvidenceSnapshotVo evidence = searchAndAwaitEvidence(
            headers, draftId, "e2e-evidence-1", "7", ProviderFixture.noResults());

        assertThat(evidence.batch().status()).isEqualTo("no_results");
        assertThat(evidence.sources()).isEmpty();
        assertThat(evidence.facts()).isEmpty();
        assertThat(ENV.operation("e2e-evidence-1").settlementStatus()).isEqualTo("settled");
        assertThat(ENV.operation("e2e-evidence-1").settledCount()).isEqualTo(1);
        assertThat(ENV.operation("e2e-evidence-1").releasedCount()).isZero();

        ENV.insertNonTerminalScriptGenerationTask(draftId, evidence.branchRevision(), "701");
        assertBusinessError(46123, () -> saveNineFieldSupplement(
            headers, draftId, evidence.draftRevision(), evidence.branchRevision()),
            Map.of("rootTaskId", "701", "taskType", "script_generate", "status", "running"));
    }
}
```

实现者必须把示例中的 HTTP helper 写在该 IT 内并使用真实 JSON body/状态码；RED 的精确失败是缺少路由、`no_results` 被标成失败/释放额度、或 46123 data/零写断言不符。不得用未注册的 provider bean；测试 provider 只由 local integration profile 显式装配。

## 7. F3 handoff 精确 schema 与幂等规则

`p2-f3-handoff.json` 字段及顺序固定为：

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

类型和值固定如下：

- `fullF3Ready`、`migrationRepeat06` 是 JSON boolean，均为 `true`，字符串 `"true"` 必须拒绝。
- `f1Head/f1AmendmentHead/f2Head/f3Head` 是小写 40 位 SHA；`f1Head` 是 addendum 的 `originalF1Head`，`f1AmendmentHead` 精确等于 addendum 的 `amendmentHead`，并现场证明 `f1Head` 是 `f1AmendmentHead` 祖先；`f1Head` 是 `f2Head` 祖先，`f2Head` 是 `f3Head` 祖先，`f3Head` 精确等于 reviewed HEAD。F2 rebase 后不伪造 `f1AmendmentHead` 为最终祖先，而以 rebase 记录、源扫描和稳定契约测试证明其补丁语义仍在。
- `reviewStatus` 精确为 `PASS`；owner 与 reviewer trim 后大小写不敏感比较必须不同；所有时间显式 UTC。
- `migrationChain` 精确为 `['01','02','03','04','04a','05','06']`，其中 `04a` 必须来自 addendum `schemaAddendum.forwardMigration=20260728_04a_p0c_task_group_guard.sql`；`migrationRepeat06=true`。
- `stableServices` 精确为 `['IQuestionnaireContextService','IEvidenceReviewService']`。
- `stableDtos` 精确为 `['QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO','QuestionnaireSupplementRevisionDTO','EvidenceReviewContextDTO','AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO']`。
- `serviceSignatures` 按上述 Service 顺序冻结完整返回类型、方法名、参数类型/名称/顺序；context Service 同时含只读 `getCurrentContext` 和 MANDATORY `lockCurrentContextForGeneration`，evidence Service 含 `getAcceptedContext`。`serviceSourceSha256` 对两个接口源码现场重算。
- `dtoComponentRegistry` 按上述六 DTO 顺序，每项只允许 `components/componentSha256`；components 使用 `Type name` 精确数组，hash 算法为 `SHA-256(UTF-8(canonical compact JSON array))`。`dtoSourceSha256` 对六个 record 源文件现场重算。`QuestionnaireAnswerRevisionDTO` 必须精确含 `answerIdentityJson/answerContextJson`；`QuestionnaireContextDTO` 必须精确含 `questionnaireHash/knowledgeContextHash/generationInputHash`。
- `lockedCurrentBranchProtocol` 精确冻结 `method,propagation,readOnly,scope,lockOrder,currentBranchCheck,snapshot,p3RecheckFields,nextLockOrder`；`p2WriteGuardProtocol` 精确冻结 `method,propagation,lockOrder,guardedWrites,businessCode,errorDataFields,zeroSideEffects`；`contextSemantics` 精确冻结答案 identity/context、补充 identity、事实 identity/decision revision、排序、不可变 List 和 hash 编码。`answerIdentitySchema` 必须逐字段冻结 `questionId:string(decimal),questionHash:string(sha256),selectedCodes:array<string>,customSelected:boolean,customText:string|null` 及字段/数组排序；`answerContextSchema` 必须逐字段冻结 `questionText:string,targetSlotCode:string,selectedOptions:array<object>,customText:string|null`，每个 selected option 精确为 `code:string,normalizedValue:string,slotContributions:array<string>`。两个 schema 各自保存 canonical JSON SHA-256；F3 回读必须同时验证精确字段、类型、null、顺序、digest，并证明顶层与 `p3ConsumerContract.contextSemantics` 深度全等。三对象必须与 Section 3.3 及最终源码/IT 一致。
- `downstreamConsumers` 精确为 `['P3']`。
- `testRegistry` 精确为 `{javaSelectors:20,surefireSelectors:14,failsafeSelectors:6,localIntegrationProfiles:6,vitestFiles:11,vitestReportsMinimum:5}`，六项均为 JSON integer，不接受字符串。
- `p3ConsumerContract` 精确包含 `requiredBaseHead=<f3Head>`、`removeFakesFrom=['production','all-real-it']`、上述两个 stable Service、上述六 DTO、完整 `serviceSignatures/serviceSourceSha256/dtoComponentRegistry/dtoSourceSha256/lockedCurrentBranchProtocol/p2WriteGuardProtocol/contextSemantics`，以及 `forbiddenDependencies=['questionnaire-mapper','questionnaire-table','questionnaire-entity','user-vo']`；P3 F3 rebase gate 必须逐对象回读，不能只检查名称。
- `evidence` 只允许 `unit/it/migration/vitest/standards/scan`；每项只允许 `path/sha256`，路径精确为 `p2-evidence/<f3Head>/<kind>.manifest.json`，SHA 为对应 manifest 实时重算的小写 64 hex。
- `f1HandoffSha256` 只绑定不可变 `p0c-f1-handoff.json`，`f1AddendumSha256` 只绑定不可覆盖 `p0c-f1-contract-addendum.json`，二者都为现场重算的小写 64 hex，绝不能互换或通过重生成原 handoff 来满足。

幂等冻结把除 `capturedAtUtc` 外的字段组成核心 payload。文件不存在时使用 `FileMode.CreateNew` 原子创建，再严格回读；文件存在时先验证完整 schema，再逐字段与当前核心 payload 比较，相同则返回原 hash 和原 `capturedAtUtc`，不同则失败。不得使用 `Set-Content` 覆盖，不得删除旧 handoff 重建，不得让 writer 写独立 review。

### 7.1 acceptance window 可执行脚本

Task 13 在全部实现已提交、开始 fresh 验收前运行下列脚本；它严格消费 F1/F2 handoff，并以 CreateNew/幂等回读创建 `p2-acceptance-window-<candidateHead>.json`。

```powershell
[CmdletBinding()]
param([Parameter(Mandatory = $true)][string] $RepoRoot)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'acceptance 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'acceptance RepoRoot 不准确' }
Set-Location -LiteralPath $root
function Resolve-GitPath([string] $Name) {
  $text = (& git rev-parse --git-path $Name).Trim()
  if ([IO.Path]::IsPathRooted($text)) { return [IO.Path]::GetFullPath($text) }
  return [IO.Path]::GetFullPath((Join-Path $root $text))
}
function Assert-Fields([object] $Value, [string[]] $Expected, [string] $Name) {
  $actual = @($Value.PSObject.Properties.Name)
  if (($actual -join '|') -cne ($Expected -join '|')) { throw "$Name 字段/顺序漂移" }
}
function Assert-Sha([object] $Value, [string] $Name) {
  if ($Value -isnot [string] -or $Value -cnotmatch '^[0-9a-f]{40}$') { throw "$Name 不是小写 SHA" }
}
function Assert-Array([object] $Value, [string[]] $Expected, [string] $Name) {
  if ($Value -isnot [System.Array] -or (@($Value) -join '|') -cne ($Expected -join '|')) { throw "$Name 数组漂移" }
}
function Get-TextSha256([string] $Value) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try {
    return ([BitConverter]::ToString($sha.ComputeHash(
      [Text.UTF8Encoding]::new($false).GetBytes($Value)))).Replace('-','').ToLowerInvariant()
  } finally { $sha.Dispose() }
}
function Get-CanonicalJsonSha256([object] $Value) {
  return Get-TextSha256 ($Value | ConvertTo-Json -Depth 12 -Compress)
}
function Normalize-JavaContract([string] $Source) {
  $value = [regex]::Replace($Source, '(?s)/[*].*?[*]/|//[^\r\n]*', ' ')
  $value = [regex]::Replace($value, '@(?:[A-Za-z_][\w.]*)(?:\([^)]*\))?\s*', '')
  $value = [regex]::Replace($value, '\s+', ' ')
  return [regex]::Replace($value, '\s*([(),;<>{}])\s*', '$1').Trim()
}
function Assert-JavaSignature([string] $Source,[string] $Signature,[string] $Name) {
  $count = [regex]::Matches($Source, [regex]::Escape($Signature)).Count
  if ($count -ne 1) { throw "$Name exact signature 计数必须为 1，实际 $count" }
}
function Assert-P0cTaskContracts([string] $Root) {
  $serviceRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service'
  $task = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskService.java') -Raw -Encoding UTF8)
  $attempt = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskAttemptService.java') -Raw -Encoding UTF8)
  $dispatcher = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskExecutionDispatcher.java') -Raw -Encoding UTF8)
  foreach ($signature in @(
    'TaskCreationResultDTO createChargeableTask(ChargeableTaskDTO request);',
    'TaskCreationResultDTO createFreeTask(FreeTaskDTO request);',
    'List<AiTaskExecutionLeaseDTO> claimExecutableTasks(Instant now,String workerId,Instant leaseExpiresAt,int limit);',
    'AiTaskExecutionLeaseDTO renewLease(AiTaskExecutionLeaseDTO lease,Instant newLeaseExpiresAt);',
    'void recordHandlerFailure(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage,boolean retryable);',
    'void markSuccess(AiTaskExecutionLeaseDTO lease,TaskResultReferenceDTO result);',
    'void markFailed(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $task $signature "IAiTaskService.$signature" }
  foreach ($ownerSignature in @(
    'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
    'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
  )) {
    $signature = Normalize-JavaContract $ownerSignature
    Assert-JavaSignature $task $signature "IAiTaskService.$ownerSignature"
  }
  foreach ($signature in @(
    'AiTaskAttemptHandleDTO startAttempt(Long rootTaskId,Long executionTaskId,String leaseOwner,String callPurpose,String provider,String model,String inputHash);',
    'void completeAttempt(Long attemptId,ProviderUsageDTO usage,String outputHash);',
    'void failAttempt(Long attemptId,ProviderUsageDTO usage,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $attempt $signature "IAiTaskAttemptService.$signature" }
  Assert-JavaSignature $dispatcher 'void enqueue(Long rootTaskId,Long executionTaskId);' 'IAiTaskExecutionDispatcher.enqueue'
}
function Assert-F2StableDtoSources([object] $F2,[string] $Root) {
  $dtoNames = @(
    'KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO',
    'KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO')
  $expectedComponents = [ordered]@{
    KnowledgeRouteRequestDTO=@(
      'Long directionCatalogVersionId','String industryCode','String purposeCode',
      'Integer targetDurationSeconds','List<String> tagCodes')
    KnowledgeRouteResultDTO=@(
      'String routingVersion','String videoTypeCode','List<KnowledgePlanDTO> plans','String contentHash')
    KnowledgePlanDTO=@(
      'String candidateCode','String planCode','Long primaryTemplateVersionId',
      'String angleCode','String differentiatorTechniqueCode')
    KnowledgeSnapshotRequestDTO=@(
      'Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts')
    KnowledgeSnapshotDTO=@(
      'Long snapshotId','Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts',
      'List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials',
      'String contentHash','Instant createdAt')
  }
  Assert-Array $F2.stableDtos $dtoNames 'F2.stableDtos'
  Assert-Fields $F2.stableDtoComponentRegistry $dtoNames 'F2.stableDtoComponentRegistry'
  Assert-Fields $F2.stableDtoSourceSha256 $dtoNames 'F2.stableDtoSourceSha256'
  $dtoRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto'
  foreach ($dtoName in $dtoNames) {
    Assert-Array $F2.stableDtoComponentRegistry.$dtoName $expectedComponents[$dtoName] "F2.stableDtoComponentRegistry.$dtoName"
    $dtoFile = Join-Path $dtoRoot ($dtoName + '.java')
    if (-not (Test-Path -LiteralPath $dtoFile -PathType Leaf)) { throw "F2 稳定 DTO 源码缺失：$dtoName" }
    $source = Get-Content -LiteralPath $dtoFile -Raw -Encoding UTF8
    $header = [regex]::Match($source,
      '(?s)(?:public\s+)?record\s+' + [regex]::Escape($dtoName) + '\s*\((?<components>.*?)\)\s*\{')
    if (-not $header.Success) { throw "F2 稳定 DTO record header 缺失：$dtoName" }
    $actualComponents = @($header.Groups['components'].Value -split ',' |
      ForEach-Object { ($_ -replace '\s+', ' ').Trim() })
    Assert-Array $actualComponents $expectedComponents[$dtoName] "F2.liveComponents.$dtoName"
    $expectedSha = $F2.stableDtoSourceSha256.$dtoName
    if ($expectedSha -isnot [string] -or $expectedSha -cnotmatch '^[0-9a-f]{64}$' -or
        $expectedSha -cne (Get-FileHash -LiteralPath $dtoFile -Algorithm SHA256).Hash.ToLowerInvariant()) {
      throw "F2 稳定 DTO source SHA 漂移：$dtoName"
    }
  }
}
$f1File = Resolve-GitPath 'p0c-f1-handoff.json'
$addendumFile = Resolve-GitPath 'p0c-f1-contract-addendum.json'
$f2File = Resolve-GitPath 'p1-f2-handoff.json'
if (-not (Test-Path -LiteralPath $f1File -PathType Leaf) -or
    -not (Test-Path -LiteralPath $addendumFile -PathType Leaf) -or
    -not (Test-Path -LiteralPath $f2File -PathType Leaf)) {
  throw '缺少原始 F1、F1 addendum 或 F2 handoff'
}
$f1 = Get-Content -LiteralPath $f1File -Raw -Encoding UTF8 | ConvertFrom-Json
$addendum = Get-Content -LiteralPath $addendumFile -Raw -Encoding UTF8 | ConvertFrom-Json
$f2 = Get-Content -LiteralPath $f2File -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $f1 @(
  'f1Head','fullF1Ready','f0Head','p0bCandidateHead','p0cAcceptanceWindowStart','p0cAcceptanceWindowEnd',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','migrations','sharedFiles',
  'sharedFileHandoffTarget','sharedFileBaselineHead','downstreamRebaseOwners','stableServices','internalSpis',
  'stableDomainAndDtos','knowledgeImportRevisionMapping','capturedAtUtc') 'F1 handoff'
Assert-Fields $addendum @(
  'originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc') 'F1 contract addendum'
Assert-Fields $f2 @(
  'fullF2Ready','f1Head','originalF1Head','f1AmendmentHead','f2Head','owner','reviewer','reviewStatus','reviewCompletedAtUtc',
  'p1AcceptanceWindowStart','p1AcceptanceWindowEnd','originalF1HandoffSha256','f1AddendumSha256','migrationChain','migrationRepeat05',
  'stableServices','stableDtos','stableDtoComponentRegistry','stableDtoSourceSha256',
  'downstreamConsumers','revisionMappingContractOwner','evidence','capturedAtUtc') 'F2 handoff'
if ($f1.fullF1Ready -isnot [bool] -or -not $f1.fullF1Ready -or
    $f2.fullF2Ready -isnot [bool] -or -not $f2.fullF2Ready) { throw 'F1/F2 ready 必须是 JSON true' }
Assert-Sha $f1.f1Head 'F1.head'; Assert-Sha $f2.f1Head 'F2.f1Head'; Assert-Sha $f2.originalF1Head 'F2.originalF1Head'
Assert-Sha $f2.f1AmendmentHead 'F2.f1AmendmentHead'; Assert-Sha $f2.f2Head 'F2.head'
Assert-Sha $addendum.originalF1Head 'addendum.originalF1Head'; Assert-Sha $addendum.amendmentHead 'addendum.amendmentHead'
if ($addendum.originalF1HandoffSha256 -isnot [string] -or
    $addendum.originalF1HandoffSha256 -cnotmatch '^[0-9a-f]{64}$') { throw 'addendum original handoff SHA 非法' }
if ($f2.f1Head -cne $addendum.amendmentHead -or $f2.f1AmendmentHead -cne $addendum.amendmentHead -or
    $f2.originalF1Head -cne $f1.f1Head -or $addendum.originalF1Head -cne $f1.f1Head -or
    $addendum.originalF1HandoffSha256 -cne (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $f2.originalF1HandoffSha256 -cne (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $f2.f1AddendumSha256 -cne (Get-FileHash -LiteralPath $addendumFile -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $addendum.owner -isnot [string] -or $addendum.reviewer -isnot [string] -or
    [string]::IsNullOrWhiteSpace($addendum.owner) -or [string]::IsNullOrWhiteSpace($addendum.reviewer) -or
    $addendum.owner.Trim().Equals($addendum.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase) -or
    $addendum.reviewStatus -cne 'PASS' -or $addendum.reviewedHead -cne $addendum.amendmentHead -or
    $f1.sharedFileHandoffTarget -cne 'P2') { throw '原始 F1/addendum/F2 或共享所有权不一致' }
Assert-Array $addendum.requiredMethods @(
  'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
  'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
) 'addendum.requiredMethods'
Assert-Fields $addendum.schemaAddendum @(
  'forwardMigration','taskGroupMemberTable','activeTaskIndex','originValues','creatorTypes',
  'globalLockOrder','scriptGroupKey','inheritanceScope','forbiddenCopies') 'addendum.schemaAddendum'
if ($addendum.schemaAddendum.forwardMigration -cne '20260728_04a_p0c_task_group_guard.sql' -or
    $addendum.schemaAddendum.taskGroupMemberTable -cne 'av_ai_task_group_member' -or
    $addendum.schemaAddendum.activeTaskIndex -cne 'idx_av_ai_task_active_group' -or
    $addendum.schemaAddendum.scriptGroupKey -cne 'script:{draftId}:{branchRevision}' -or
    $addendum.schemaAddendum.inheritanceScope -cne 'membership_only') { throw 'addendum schema 漂移' }
Assert-Array $addendum.schemaAddendum.originValues @('origin','inherited') 'addendum.originValues'
Assert-Array $addendum.schemaAddendum.creatorTypes @('app_user','sys_user') 'addendum.creatorTypes'
Assert-Array $addendum.schemaAddendum.globalLockOrder @(
  'draft','current_branch','operation_slot','quota_account','task_or_group_member') 'addendum.globalLockOrder'
Assert-Array $addendum.schemaAddendum.forbiddenCopies @(
  'task','usage','ledger','operation_slot') 'addendum.forbiddenCopies'
$reviewCompleted = [DateTimeOffset]::Parse($addendum.reviewCompletedAtUtc)
$captured = [DateTimeOffset]::Parse($addendum.capturedAtUtc)
if ($reviewCompleted.Offset -ne [TimeSpan]::Zero -or $captured.Offset -ne [TimeSpan]::Zero -or
    $captured -lt $reviewCompleted) { throw 'F1 addendum review/captured 时间非法' }
$expectedEvidence = [ordered]@{
  'source-signatures'='git-metadata:p0c-f1-addendum/source-signatures.manifest.json'
  'migration-04a'='git-metadata:p0c-f1-addendum/migration-04a.manifest.json'
  'independent-review'='git-metadata:p0c-f1-contract-addendum-review.json'
}
$evidenceItems = @($addendum.evidence)
if ($evidenceItems.Count -ne 3) { throw 'F1 addendum evidence 必须恰为三项' }
for ($index = 0; $index -lt 3; $index++) {
  $item = $evidenceItems[$index]; $kind = @($expectedEvidence.Keys)[$index]
  Assert-Fields $item @('kind','path','sha256') "addendum.evidence[$index]"
  if ($item.sha256 -isnot [string] -or $item.sha256 -cnotmatch '^[0-9a-f]{64}$' -or
      $item.kind -cne $kind -or $item.path -cne $expectedEvidence[$kind]) { throw 'F1 addendum evidence 顺序/字段漂移' }
  $evidenceFile = Resolve-GitPath $item.path.Substring('git-metadata:'.Length)
  if (-not (Test-Path -LiteralPath $evidenceFile -PathType Leaf) -or
      (Get-FileHash -LiteralPath $evidenceFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item.sha256) {
    throw "F1 addendum evidence hash 漂移：$kind"
  }
}
$addendumReview = Get-Content -LiteralPath (Resolve-GitPath 'p0c-f1-contract-addendum-review.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $addendumReview @(
  'owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256',
  'requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc') 'F1 addendum independent review'
if ($addendumReview.requiredMethodsSha256 -isnot [string] -or $addendumReview.requiredMethodsSha256 -cnotmatch '^[0-9a-f]{64}$' -or
    $addendumReview.schemaAddendumSha256 -isnot [string] -or $addendumReview.schemaAddendumSha256 -cnotmatch '^[0-9a-f]{64}$' -or
    $addendumReview.owner -cne $addendum.owner -or $addendumReview.reviewer -cne $addendum.reviewer -or
    $addendumReview.reviewStatus -cne 'PASS' -or $addendumReview.reviewedHead -cne $addendum.amendmentHead -or
    $addendumReview.originalF1Head -cne $addendum.originalF1Head -or
    $addendumReview.originalF1HandoffSha256 -cne $addendum.originalF1HandoffSha256 -or
    $addendumReview.requiredMethodsSha256 -cne (Get-CanonicalJsonSha256 $addendum.requiredMethods) -or
    $addendumReview.schemaAddendumSha256 -cne (Get-CanonicalJsonSha256 $addendum.schemaAddendum) -or
    $addendumReview.reviewCompletedAtUtc -cne $addendum.reviewCompletedAtUtc) { throw 'F1 addendum independent review 绑定漂移' }
& git merge-base --is-ancestor $addendum.originalF1Head $addendum.amendmentHead
if ($LASTEXITCODE -ne 0) { throw '原始 F1 不是 amendmentHead 祖先' }
$forwardMigration = Join-Path $root ('docs/sql/ai-video/mysql/' +
  $addendum.schemaAddendum.forwardMigration)
if (-not (Test-Path -LiteralPath $forwardMigration -PathType Leaf)) { throw 'acceptance 缺 P0-C forward migration' }
$forwardMigrationSource = Get-Content -LiteralPath $forwardMigration -Raw -Encoding UTF8
foreach ($schemaToken in @('av_ai_task_group_member','idx_av_ai_task_active_group')) {
  if ($forwardMigrationSource.IndexOf($schemaToken,[StringComparison]::Ordinal) -lt 0) {
    throw "acceptance forward migration 缺 schema：$schemaToken"
  }
}
Assert-Array $f2.stableServices @('IKnowledgeRoutingService','IKnowledgeSnapshotService') 'F2 stableServices'
Assert-F2StableDtoSources $f2 $root
Assert-Array $f2.migrationChain @('01','02','03','04','04a','05') 'F2 migrationChain'
$head = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant(); Assert-Sha $head 'candidateHead'
& git merge-base --is-ancestor $f2.f2Head $head
if ($LASTEXITCODE -ne 0 -or $head -ceq $f2.f2Head) { throw 'P2 candidate 必须是 F2 非空后继' }
$gate = Resolve-GitPath 'p2-worktree-gate.ps1'
if ((& $gate -RepoRoot $root -Phase inspect -ExpectedOriginalF1Head $f1.f1Head `
    -ExpectedF1Head $addendum.amendmentHead -ExpectedF2Head $f2.f2Head `
    -AllowedPaths @() -RequireClean) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'acceptance 未通过统一 worktree gate'
}
Assert-P0cTaskContracts $root
$migrationText = (@(Get-ChildItem -LiteralPath (Join-Path $root 'docs/sql/ai-video/mysql') -Filter '*.sql' -File) |
  ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw -Encoding UTF8 }) -join "`n"
$forwardMigration = Join-Path $root ('docs/sql/ai-video/mysql/' +
  $addendum.schemaAddendum.forwardMigration)
if (-not (Test-Path -LiteralPath $forwardMigration -PathType Leaf)) { throw '最终源码缺 P0-C forward migration' }
foreach ($schemaToken in @('av_ai_task_group_member','idx_av_ai_task_active_group')) {
  if ($migrationText.IndexOf($schemaToken,[StringComparison]::Ordinal) -lt 0) { throw "最终迁移丢失 addendum schema：$schemaToken" }
}
$windowPath = Resolve-GitPath "p2-acceptance-window-$head.json"
$core = [ordered]@{
  f1Head=$f1.f1Head; amendmentHead=$addendum.amendmentHead
  f2Head=$f2.f2Head; candidateHead=$head
}
if (Test-Path -LiteralPath $windowPath -PathType Leaf) {
  $existing = Get-Content -LiteralPath $windowPath -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $existing @('f1Head','amendmentHead','f2Head','candidateHead','startedAtUtc') 'acceptance window'
  if ($existing.f1Head -cne $core.f1Head -or $existing.amendmentHead -cne $core.amendmentHead -or
      $existing.f2Head -cne $core.f2Head -or $existing.candidateHead -cne $core.candidateHead) {
    throw '既有 acceptance window payload 不同'
  }
} else {
  $document = [ordered]@{
    f1Head=$core.f1Head; amendmentHead=$core.amendmentHead
    f2Head=$core.f2Head; candidateHead=$core.candidateHead
    startedAtUtc=[DateTime]::UtcNow.ToString('o')
  }
  $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($document | ConvertTo-Json -Compress))
  $stream = [IO.File]::Open($windowPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}
'P2_ACCEPTANCE_WINDOW_OK'
```

### 7.2 独立 review 可执行脚本

只有独立 reviewer 可以运行下列脚本；writer 禁止创建、修改或补写 `p2-independent-review.json`。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [Parameter(Mandatory = $true)][string] $Owner,
  [Parameter(Mandatory = $true)][string] $Reviewer,
  [Parameter(Mandatory = $true)][string] $RevisionMappingContractOwner
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'review 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'review RepoRoot 不准确' }
Set-Location -LiteralPath $root
function Resolve-GitPath([string] $Name) {
  $text = (& git rev-parse --git-path $Name).Trim()
  if ([IO.Path]::IsPathRooted($text)) { return [IO.Path]::GetFullPath($text) }
  return [IO.Path]::GetFullPath((Join-Path $root $text))
}
function Assert-Fields([object] $Value,[string[]] $Expected,[string] $Name) {
  if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($Expected -join '|')) { throw "$Name 字段/顺序漂移" }
}
function Assert-Sha([object] $Value,[int] $Length,[string] $Name) {
  if ($Value -isnot [string] -or $Value -cnotmatch ('^[0-9a-f]{' + $Length + '}$')) { throw "$Name SHA 非法" }
}
if ([string]::IsNullOrWhiteSpace($Owner) -or [string]::IsNullOrWhiteSpace($Reviewer) -or
    [string]::IsNullOrWhiteSpace($RevisionMappingContractOwner) -or
    $Owner.Trim().Equals($Reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase)) {
  throw 'owner/reviewer/revision owner 非法或不独立'
}
$head = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant(); Assert-Sha $head 40 'reviewedHead'
$windowPath = Resolve-GitPath "p2-acceptance-window-$head.json"
if (-not (Test-Path -LiteralPath $windowPath -PathType Leaf)) { throw '缺少 acceptance window' }
$window = Get-Content -LiteralPath $windowPath -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $window @('f1Head','amendmentHead','f2Head','candidateHead','startedAtUtc') 'acceptance window'
if ($window.candidateHead -cne $head) { throw 'acceptance candidate HEAD 漂移' }
$started = [DateTimeOffset]::Parse($window.startedAtUtc)
if ($started.Offset -ne [TimeSpan]::Zero) { throw 'acceptance start 不是 UTC' }
$gate = Resolve-GitPath 'p2-worktree-gate.ps1'
if ((& $gate -RepoRoot $root -Phase inspect -ExpectedOriginalF1Head $window.f1Head `
    -ExpectedF1Head $window.amendmentHead -ExpectedF2Head $window.f2Head `
    -AllowedPaths @() -RequireClean) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'review 未通过统一 worktree gate'
}
$manifestGate = Resolve-GitPath 'p2-evidence-manifest-gate.ps1'
$evidence = [ordered]@{}
foreach ($kind in @('unit','it','migration','vitest','standards','scan')) {
  $relative = "p2-evidence/$head/$kind.manifest.json"
  $manifestFile = Resolve-GitPath $relative
  if (-not (Test-Path -LiteralPath $manifestFile -PathType Leaf)) { throw "缺少 $kind manifest" }
  $manifest = Get-Content -LiteralPath $manifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $manifest @('schemaVersion','kind','windowStartUtc','windowEndUtc','artifacts','capturedAtUtc') "$kind manifest"
  $artifactInputs = @($manifest.artifacts | ForEach-Object {
    [ordered]@{ pathScope=$_.pathScope; relativePath=$_.relativePath }
  }) | ConvertTo-Json -Depth 4 -Compress
  $manifestStart = [DateTimeOffset]::Parse($manifest.windowStartUtc)
  $manifestEnd = [DateTimeOffset]::Parse($manifest.windowEndUtc)
  if ($manifestStart -lt $started -or $manifestEnd -gt [DateTimeOffset]::UtcNow) { throw "$kind manifest 窗口越界" }
  if ((& $manifestGate -RepoRoot $root -ManifestRelativePath $relative -Kind $kind `
      -ArtifactsJson $artifactInputs -WindowStartUtc $manifestStart -WindowEndUtc $manifestEnd -Mode verify) -cne 'P2_EVIDENCE_MANIFEST_OK') {
    throw "$kind manifest 回读失败"
  }
  $evidence[$kind] = [ordered]@{
    path=$relative
    sha256=(Get-FileHash -LiteralPath $manifestFile -Algorithm SHA256).Hash.ToLowerInvariant()
  }
}
$reviewPath = Resolve-GitPath 'p2-independent-review.json'
$stableCore = [ordered]@{
  owner=$Owner.Trim(); reviewer=$Reviewer.Trim(); reviewStatus='PASS'; reviewedHead=$head
  f1Head=$window.f1Head; amendmentHead=$window.amendmentHead; f2Head=$window.f2Head
  revisionMappingContractOwner=$RevisionMappingContractOwner.Trim(); evidence=$evidence
}
if (Test-Path -LiteralPath $reviewPath -PathType Leaf) {
  $existing = Get-Content -LiteralPath $reviewPath -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $existing @('owner','reviewer','reviewStatus','reviewedHead','f1Head','amendmentHead','f2Head','reviewCompletedAtUtc','revisionMappingContractOwner','evidence') 'independent review'
  $existingCore = [ordered]@{
    owner=$existing.owner; reviewer=$existing.reviewer; reviewStatus=$existing.reviewStatus
    reviewedHead=$existing.reviewedHead; f1Head=$existing.f1Head
    amendmentHead=$existing.amendmentHead; f2Head=$existing.f2Head
    revisionMappingContractOwner=$existing.revisionMappingContractOwner; evidence=$existing.evidence
  }
  if (($existingCore | ConvertTo-Json -Depth 8 -Compress) -cne ($stableCore | ConvertTo-Json -Depth 8 -Compress)) {
    throw '既有 independent review payload 不同，拒绝覆盖'
  }
} else {
  $document = [ordered]@{
    owner=$stableCore.owner; reviewer=$stableCore.reviewer; reviewStatus='PASS'; reviewedHead=$head
    f1Head=$window.f1Head; amendmentHead=$window.amendmentHead
    f2Head=$window.f2Head; reviewCompletedAtUtc=[DateTime]::UtcNow.ToString('o')
    revisionMappingContractOwner=$stableCore.revisionMappingContractOwner; evidence=$evidence
  }
  $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($document | ConvertTo-Json -Depth 8 -Compress))
  $stream = [IO.File]::Open($reviewPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}
'P2_INDEPENDENT_REVIEW_OK'
```

### 7.3 F3 freeze 可执行脚本

review PASS 后 writer 运行下列脚本；它重新验证 HEAD、F1/F2 hash、六 manifest、review、测试 registry、`06` 证据和 P3 consumer contract，再 CreateNew/幂等回读 handoff。

```powershell
[CmdletBinding()]
param(
  [Parameter(Mandatory = $true)][string] $RepoRoot,
  [switch] $SelfTest
)
$ErrorActionPreference = 'Stop'
$rootText = (& git -C $RepoRoot rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw 'F3 freeze 无法解析仓库根' }
$root = [IO.Path]::GetFullPath($rootText.Trim())
if ($root -cne [IO.Path]::GetFullPath($RepoRoot)) { throw 'F3 freeze RepoRoot 不准确' }
Set-Location -LiteralPath $root
function Resolve-GitPath([string] $Name) {
  $text = (& git rev-parse --git-path $Name).Trim()
  if ([IO.Path]::IsPathRooted($text)) { return [IO.Path]::GetFullPath($text) }
  return [IO.Path]::GetFullPath((Join-Path $root $text))
}
function Assert-Fields([object] $Value,[string[]] $Expected,[string] $Name) {
  if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($Expected -join '|')) { throw "$Name 字段/顺序漂移" }
}
function Assert-Sha([object] $Value,[int] $Length,[string] $Name) {
  if ($Value -isnot [string] -or $Value -cnotmatch ('^[0-9a-f]{' + $Length + '}$')) { throw "$Name SHA 非法" }
}
function Assert-Array([object] $Value,[string[]] $Expected,[string] $Name) {
  if ($Value -isnot [System.Array] -or (@($Value) -join '|') -cne ($Expected -join '|')) { throw "$Name 数组漂移" }
}
function Assert-TrueBoolean([object] $Value,[string] $Name) {
  if ($Value -isnot [bool] -or -not $Value) { throw "$Name 必须是 JSON true" }
}
function Get-TextSha256([string] $Value) {
  $sha = [Security.Cryptography.SHA256]::Create()
  try {
    return ([BitConverter]::ToString($sha.ComputeHash(
      [Text.UTF8Encoding]::new($false).GetBytes($Value)))).Replace('-','').ToLowerInvariant()
  } finally { $sha.Dispose() }
}
function Get-CanonicalJsonSha256([object] $Value) {
  return Get-TextSha256 ($Value | ConvertTo-Json -Depth 16 -Compress)
}
function Normalize-JavaContract([string] $Source) {
  $value = [regex]::Replace($Source, '(?s)/[*].*?[*]/|//[^\r\n]*', ' ')
  $value = [regex]::Replace($value, '@(?:[A-Za-z_][\w.]*)(?:\([^)]*\))?\s*', '')
  $value = [regex]::Replace($value, '\s+', ' ')
  return [regex]::Replace($value, '\s*([(),;<>{}])\s*', '$1').Trim()
}
function Assert-JavaSignature([string] $Source,[string] $Signature,[string] $Name) {
  $count = [regex]::Matches($Source, [regex]::Escape($Signature)).Count
  if ($count -ne 1) { throw "$Name exact signature 计数必须为 1，实际 $count" }
}
function Assert-JavaRecordHeader([string] $Source,[string] $Name,[string[]] $Components) {
  $header = 'public record ' + $Name + '(' + ($Components -join ',') + '){'
  Assert-JavaSignature $Source $header "$Name record header"
}
function Assert-P0cTaskContracts([string] $Root) {
  $serviceRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/task/service'
  $task = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskService.java') -Raw -Encoding UTF8)
  $attempt = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskAttemptService.java') -Raw -Encoding UTF8)
  $dispatcher = Normalize-JavaContract (Get-Content -LiteralPath (Join-Path $serviceRoot 'IAiTaskExecutionDispatcher.java') -Raw -Encoding UTF8)
  foreach ($signature in @(
    'TaskCreationResultDTO createChargeableTask(ChargeableTaskDTO request);',
    'TaskCreationResultDTO createFreeTask(FreeTaskDTO request);',
    'List<AiTaskExecutionLeaseDTO> claimExecutableTasks(Instant now,String workerId,Instant leaseExpiresAt,int limit);',
    'AiTaskExecutionLeaseDTO renewLease(AiTaskExecutionLeaseDTO lease,Instant newLeaseExpiresAt);',
    'void recordHandlerFailure(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage,boolean retryable);',
    'void markSuccess(AiTaskExecutionLeaseDTO lease,TaskResultReferenceDTO result);',
    'void markFailed(AiTaskExecutionLeaseDTO lease,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $task $signature "IAiTaskService.$signature" }
  foreach ($ownerSignature in @(
    'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
    'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
  )) {
    $signature = Normalize-JavaContract $ownerSignature
    Assert-JavaSignature $task $signature "IAiTaskService.$ownerSignature"
  }
  foreach ($signature in @(
    'AiTaskAttemptHandleDTO startAttempt(Long rootTaskId,Long executionTaskId,String leaseOwner,String callPurpose,String provider,String model,String inputHash);',
    'void completeAttempt(Long attemptId,ProviderUsageDTO usage,String outputHash);',
    'void failAttempt(Long attemptId,ProviderUsageDTO usage,String failureCode,String failureMessage);'
  )) { Assert-JavaSignature $attempt $signature "IAiTaskAttemptService.$signature" }
  Assert-JavaSignature $dispatcher 'void enqueue(Long rootTaskId,Long executionTaskId);' 'IAiTaskExecutionDispatcher.enqueue'
}
function Assert-F2StableDtoSources([object] $F2,[string] $Root) {
  $dtoNames = @(
    'KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO',
    'KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO')
  $expectedComponents = [ordered]@{
    KnowledgeRouteRequestDTO=@(
      'Long directionCatalogVersionId','String industryCode','String purposeCode',
      'Integer targetDurationSeconds','List<String> tagCodes')
    KnowledgeRouteResultDTO=@(
      'String routingVersion','String videoTypeCode','List<KnowledgePlanDTO> plans','String contentHash')
    KnowledgePlanDTO=@(
      'String candidateCode','String planCode','Long primaryTemplateVersionId',
      'String angleCode','String differentiatorTechniqueCode')
    KnowledgeSnapshotRequestDTO=@(
      'Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts')
    KnowledgeSnapshotDTO=@(
      'Long snapshotId','Long rootTaskId','Long promptVersionId','Long generationContextRevision',
      'String generationInputHash','KnowledgeRouteResultDTO route',
      'List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts',
      'List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials',
      'String contentHash','Instant createdAt')
  }
  Assert-Array $F2.stableDtos $dtoNames 'F2.stableDtos'
  Assert-Fields $F2.stableDtoComponentRegistry $dtoNames 'F2.stableDtoComponentRegistry'
  Assert-Fields $F2.stableDtoSourceSha256 $dtoNames 'F2.stableDtoSourceSha256'
  $dtoRoot = Join-Path $Root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/knowledge/dto'
  foreach ($dtoName in $dtoNames) {
    Assert-Array $F2.stableDtoComponentRegistry.$dtoName $expectedComponents[$dtoName] "F2.stableDtoComponentRegistry.$dtoName"
    $dtoFile = Join-Path $dtoRoot ($dtoName + '.java')
    if (-not (Test-Path -LiteralPath $dtoFile -PathType Leaf)) { throw "F2 稳定 DTO 源码缺失：$dtoName" }
    $source = Get-Content -LiteralPath $dtoFile -Raw -Encoding UTF8
    $header = [regex]::Match($source,
      '(?s)(?:public\s+)?record\s+' + [regex]::Escape($dtoName) + '\s*\((?<components>.*?)\)\s*\{')
    if (-not $header.Success) { throw "F2 稳定 DTO record header 缺失：$dtoName" }
    $actualComponents = @($header.Groups['components'].Value -split ',' |
      ForEach-Object { ($_ -replace '\s+', ' ').Trim() })
    Assert-Array $actualComponents $expectedComponents[$dtoName] "F2.liveComponents.$dtoName"
    $expectedSha = $F2.stableDtoSourceSha256.$dtoName
    if ($expectedSha -isnot [string] -or $expectedSha -cnotmatch '^[0-9a-f]{64}$' -or
        $expectedSha -cne (Get-FileHash -LiteralPath $dtoFile -Algorithm SHA256).Hash.ToLowerInvariant()) {
      throw "F2 稳定 DTO source SHA 漂移：$dtoName"
    }
  }
}
if ($SelfTest) {
  $rejected = $false
  try { Assert-Fields ([pscustomobject][ordered]@{a=1;extra=2}) @('a') 'extra-field-canary' } catch { $rejected = $true }
  if (-not $rejected) { throw 'F3 extra field 自测意外通过' }
  $rejected = $false
  try { Assert-Array 'P3' @('P3') 'array-scalar-canary' } catch { $rejected = $true }
  if (-not $rejected) { throw 'F3 scalar array 自测意外通过' }
  $rejected = $false
  try { Assert-TrueBoolean 'true' 'boolean-string-canary' } catch { $rejected = $true }
  if (-not $rejected) { throw 'F3 boolean string 自测意外通过' }
  $javaCanary = Normalize-JavaContract 'public record X(Long id, String hash) {} interface S { X lock(Long a, Long b); }'
  Assert-JavaRecordHeader $javaCanary 'X' @('Long id','String hash')
  Assert-JavaSignature $javaCanary 'X lock(Long a,Long b);' 'service-signature-canary'
  $rejected = $false
  try { Assert-JavaRecordHeader $javaCanary 'X' @('String hash','Long id') } catch { $rejected = $true }
  if (-not $rejected) { throw 'F3 DTO component 顺序自测意外通过' }
  if ((Get-CanonicalJsonSha256 @('Long id','String hash')) -cnotmatch '^[0-9a-f]{64}$') {
    throw 'F3 component digest 自测失败'
  }
  $protocolCanary = [pscustomobject][ordered]@{
    method='lock'; propagation='MANDATORY'; readOnly=$false; scope=@('tenant_id')
    lockOrder=@('draft','current_branch'); currentBranchCheck='equal'; snapshot='same'
    p3RecheckFields=@('branchRevision'); nextLockOrder=@('operation_slot')
  }
  Assert-Fields $protocolCanary @(
    'method','propagation','readOnly','scope','lockOrder','currentBranchCheck','snapshot','p3RecheckFields','nextLockOrder') 'protocol-canary'
  $rejected = $protocolCanary.readOnly -isnot [bool] -or $protocolCanary.readOnly
  if ($rejected) { throw 'F3 protocol false boolean 自测失败' }
  $badReadOnly = 'false'
  $rejected = $badReadOnly -isnot [bool] -or $badReadOnly
  if (-not $rejected) { throw 'F3 protocol string boolean 负向自测意外通过' }
  'P2_F3_HANDOFF_SELFTEST_OK'
  exit 0
}
$head = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant(); Assert-Sha $head 40 'F3 HEAD'
$f1File = Resolve-GitPath 'p0c-f1-handoff.json'
$addendumFile = Resolve-GitPath 'p0c-f1-contract-addendum.json'
$f2File = Resolve-GitPath 'p1-f2-handoff.json'
$windowFile = Resolve-GitPath "p2-acceptance-window-$head.json"
$reviewFile = Resolve-GitPath 'p2-independent-review.json'
foreach ($file in @($f1File,$addendumFile,$f2File,$windowFile,$reviewFile)) {
  if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "F3 输入缺失：$file" }
}
$f1 = Get-Content -LiteralPath $f1File -Raw -Encoding UTF8 | ConvertFrom-Json
$addendum = Get-Content -LiteralPath $addendumFile -Raw -Encoding UTF8 | ConvertFrom-Json
$f2 = Get-Content -LiteralPath $f2File -Raw -Encoding UTF8 | ConvertFrom-Json
$window = Get-Content -LiteralPath $windowFile -Raw -Encoding UTF8 | ConvertFrom-Json
$review = Get-Content -LiteralPath $reviewFile -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $f1 @(
  'f1Head','fullF1Ready','f0Head','p0bCandidateHead','p0cAcceptanceWindowStart','p0cAcceptanceWindowEnd',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','migrations','sharedFiles',
  'sharedFileHandoffTarget','sharedFileBaselineHead','downstreamRebaseOwners','stableServices','internalSpis',
  'stableDomainAndDtos','knowledgeImportRevisionMapping','capturedAtUtc') 'F1 handoff'
Assert-Fields $addendum @(
  'originalF1Head','amendmentHead','originalF1HandoffSha256','requiredMethods','schemaAddendum',
  'owner','reviewer','reviewStatus','reviewedHead','reviewCompletedAtUtc','evidence','capturedAtUtc') 'F1 contract addendum'
Assert-Fields $f2 @(
  'fullF2Ready','f1Head','originalF1Head','f1AmendmentHead','f2Head','owner','reviewer','reviewStatus','reviewCompletedAtUtc',
  'p1AcceptanceWindowStart','p1AcceptanceWindowEnd','originalF1HandoffSha256','f1AddendumSha256','migrationChain','migrationRepeat05',
  'stableServices','stableDtos','stableDtoComponentRegistry','stableDtoSourceSha256',
  'downstreamConsumers','revisionMappingContractOwner','evidence','capturedAtUtc') 'F2 handoff'
Assert-TrueBoolean $f1.fullF1Ready 'F1.fullF1Ready'
Assert-TrueBoolean $f2.fullF2Ready 'F2.fullF2Ready'
Assert-TrueBoolean $f2.migrationRepeat05 'F2.migrationRepeat05'
Assert-Sha $f1.f1Head 40 'F1.head'; Assert-Sha $f2.f1Head 40 'F2.f1Head'
Assert-Sha $f2.originalF1Head 40 'F2.originalF1Head'; Assert-Sha $f2.f1AmendmentHead 40 'F2.f1AmendmentHead'
Assert-Sha $f2.f2Head 40 'F2.head'; Assert-Sha $f2.originalF1HandoffSha256 64 'F2.originalF1HandoffSha256'
Assert-Sha $f2.f1AddendumSha256 64 'F2.f1AddendumSha256'
Assert-Sha $addendum.originalF1Head 40 'addendum.originalF1Head'
Assert-Sha $addendum.amendmentHead 40 'addendum.amendmentHead'
Assert-Sha $addendum.originalF1HandoffSha256 64 'addendum.originalF1HandoffSha256'
if ($f2.f1Head -cne $addendum.amendmentHead -or $f2.f1AmendmentHead -cne $addendum.amendmentHead -or
    $f2.originalF1Head -cne $f1.f1Head -or $addendum.originalF1Head -cne $f1.f1Head -or
    $addendum.originalF1HandoffSha256 -cne (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $addendum.owner -isnot [string] -or $addendum.reviewer -isnot [string] -or
    [string]::IsNullOrWhiteSpace($addendum.owner) -or [string]::IsNullOrWhiteSpace($addendum.reviewer) -or
    $addendum.owner.Trim().Equals($addendum.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase) -or
    $addendum.reviewStatus -cne 'PASS' -or $addendum.reviewedHead -cne $addendum.amendmentHead -or
    $f1.sharedFileHandoffTarget -cne 'P2' -or
    (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -cne $f2.originalF1HandoffSha256 -or
    (Get-FileHash -LiteralPath $addendumFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $f2.f1AddendumSha256) {
  throw '原始 F1/addendum/F2 baseline、独立审核、共享所有权或 handoff hash 不一致'
}
Assert-Array $addendum.requiredMethods @(
  'void requireGenerationContextWritable(Long draftId, Long branchRevision);',
  'void inheritQuestionnaireTaskGroupMembers(Long draftId, Long sourceBranchRevision, Long targetBranchRevision, List<Long> retainedRootTaskIds, TaskInitiatorDTO initiator);'
) 'addendum.requiredMethods'
Assert-Fields $addendum.schemaAddendum @(
  'forwardMigration','taskGroupMemberTable','activeTaskIndex','originValues','creatorTypes',
  'globalLockOrder','scriptGroupKey','inheritanceScope','forbiddenCopies') 'addendum.schemaAddendum'
if ($addendum.schemaAddendum.forwardMigration -cne '20260728_04a_p0c_task_group_guard.sql' -or
    $addendum.schemaAddendum.taskGroupMemberTable -cne 'av_ai_task_group_member' -or
    $addendum.schemaAddendum.activeTaskIndex -cne 'idx_av_ai_task_active_group' -or
    $addendum.schemaAddendum.scriptGroupKey -cne 'script:{draftId}:{branchRevision}' -or
    $addendum.schemaAddendum.inheritanceScope -cne 'membership_only') { throw 'addendum schema 漂移' }
Assert-Array $addendum.schemaAddendum.originValues @('origin','inherited') 'addendum.originValues'
Assert-Array $addendum.schemaAddendum.creatorTypes @('app_user','sys_user') 'addendum.creatorTypes'
Assert-Array $addendum.schemaAddendum.globalLockOrder @(
  'draft','current_branch','operation_slot','quota_account','task_or_group_member') 'addendum.globalLockOrder'
Assert-Array $addendum.schemaAddendum.forbiddenCopies @(
  'task','usage','ledger','operation_slot') 'addendum.forbiddenCopies'
$addendumReviewTime = [DateTimeOffset]::Parse($addendum.reviewCompletedAtUtc)
$addendumCaptured = [DateTimeOffset]::Parse($addendum.capturedAtUtc)
if ($addendumReviewTime.Offset -ne [TimeSpan]::Zero -or $addendumCaptured.Offset -ne [TimeSpan]::Zero -or
    $addendumCaptured -lt $addendumReviewTime) { throw 'F1 addendum 时间非法' }
$expectedAddendumEvidence = [ordered]@{
  'source-signatures'='git-metadata:p0c-f1-addendum/source-signatures.manifest.json'
  'migration-04a'='git-metadata:p0c-f1-addendum/migration-04a.manifest.json'
  'independent-review'='git-metadata:p0c-f1-contract-addendum-review.json'
}
$addendumEvidence = @($addendum.evidence)
if ($addendumEvidence.Count -ne 3) { throw 'F1 addendum evidence 必须恰为三项' }
for ($index = 0; $index -lt 3; $index++) {
  $item = $addendumEvidence[$index]; $kind = @($expectedAddendumEvidence.Keys)[$index]
  Assert-Fields $item @('kind','path','sha256') "addendum.evidence[$index]"
  Assert-Sha $item.sha256 64 "addendum.evidence[$index].sha256"
  if ($item.kind -cne $kind -or $item.path -cne $expectedAddendumEvidence[$kind]) { throw 'addendum evidence 顺序/路径漂移' }
  $evidenceFile = Resolve-GitPath $item.path.Substring('git-metadata:'.Length)
  if (-not (Test-Path -LiteralPath $evidenceFile -PathType Leaf) -or
      (Get-FileHash -LiteralPath $evidenceFile -Algorithm SHA256).Hash.ToLowerInvariant() -cne $item.sha256) {
    throw "addendum evidence hash 漂移：$kind"
  }
}
$addendumReview = Get-Content -LiteralPath (Resolve-GitPath 'p0c-f1-contract-addendum-review.json') -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $addendumReview @(
  'owner','reviewer','reviewStatus','reviewedHead','originalF1Head','originalF1HandoffSha256',
  'requiredMethodsSha256','schemaAddendumSha256','reviewCompletedAtUtc') 'F1 addendum independent review'
Assert-Sha $addendumReview.requiredMethodsSha256 64 'review.requiredMethodsSha256'
Assert-Sha $addendumReview.schemaAddendumSha256 64 'review.schemaAddendumSha256'
if ($addendumReview.owner -cne $addendum.owner -or $addendumReview.reviewer -cne $addendum.reviewer -or
    $addendumReview.reviewStatus -cne 'PASS' -or $addendumReview.reviewedHead -cne $addendum.amendmentHead -or
    $addendumReview.originalF1Head -cne $addendum.originalF1Head -or
    $addendumReview.originalF1HandoffSha256 -cne $addendum.originalF1HandoffSha256 -or
    $addendumReview.requiredMethodsSha256 -cne (Get-CanonicalJsonSha256 $addendum.requiredMethods) -or
    $addendumReview.schemaAddendumSha256 -cne (Get-CanonicalJsonSha256 $addendum.schemaAddendum) -or
    $addendumReview.reviewCompletedAtUtc -cne $addendum.reviewCompletedAtUtc) { throw 'F1 addendum independent review 绑定漂移' }
& git merge-base --is-ancestor $addendum.originalF1Head $addendum.amendmentHead
if ($LASTEXITCODE -ne 0) { throw '原始 F1 不是 amendmentHead 祖先' }
$forwardMigration = Join-Path $root ('docs/sql/ai-video/mysql/' +
  $addendum.schemaAddendum.forwardMigration)
if (-not (Test-Path -LiteralPath $forwardMigration -PathType Leaf)) { throw 'F3 缺 P0-C forward migration' }
$forwardMigrationSource = Get-Content -LiteralPath $forwardMigration -Raw -Encoding UTF8
foreach ($schemaToken in @('av_ai_task_group_member','idx_av_ai_task_active_group')) {
  if ($forwardMigrationSource.IndexOf($schemaToken,[StringComparison]::Ordinal) -lt 0) {
    throw "F3 forward migration 缺 schema：$schemaToken"
  }
}
Assert-Array $f2.stableServices @('IKnowledgeRoutingService','IKnowledgeSnapshotService') 'F2 stableServices'
Assert-F2StableDtoSources $f2 $root
Assert-Array $f2.migrationChain @('01','02','03','04','04a','05') 'F2 migrationChain'
Assert-Fields $window @('f1Head','amendmentHead','f2Head','candidateHead','startedAtUtc') 'acceptance window'
Assert-Fields $review @('owner','reviewer','reviewStatus','reviewedHead','f1Head','amendmentHead','f2Head','reviewCompletedAtUtc','revisionMappingContractOwner','evidence') 'independent review'
if ($window.candidateHead -cne $head -or $review.reviewedHead -cne $head -or
    $window.f1Head -cne $f1.f1Head -or $window.amendmentHead -cne $addendum.amendmentHead -or
    $review.f1Head -cne $f1.f1Head -or $review.amendmentHead -cne $addendum.amendmentHead -or
    $review.f2Head -cne $f2.f2Head -or
    $review.reviewStatus -cne 'PASS' -or
    $review.owner.Trim().Equals($review.reviewer.Trim(),[StringComparison]::OrdinalIgnoreCase)) {
  throw 'HEAD/baseline/reviewer 不一致'
}
$windowStart = [DateTimeOffset]::Parse($window.startedAtUtc)
$reviewTime = [DateTimeOffset]::Parse($review.reviewCompletedAtUtc)
if ($windowStart.Offset -ne [TimeSpan]::Zero -or $reviewTime.Offset -ne [TimeSpan]::Zero -or $reviewTime -lt $windowStart) {
  throw 'acceptance/review 时间非法'
}
$gate = Resolve-GitPath 'p2-worktree-gate.ps1'
if ((& $gate -RepoRoot $root -Phase final -ExpectedOriginalF1Head $f1.f1Head `
    -ExpectedF1Head $addendum.amendmentHead -ExpectedF2Head $f2.f2Head `
    -AllowedPaths @() -RequireClean) -cne 'P2_WORKTREE_GATE_OK') {
  throw 'F3 freeze 未通过统一 worktree gate'
}
$evidenceKinds = @('unit','it','migration','vitest','standards','scan')
Assert-Fields $review.evidence $evidenceKinds 'review evidence'
$manifestGate = Resolve-GitPath 'p2-evidence-manifest-gate.ps1'
$manifests = [ordered]@{}
foreach ($kind in $evidenceKinds) {
  $binding = $review.evidence.$kind
  Assert-Fields $binding @('path','sha256') "review evidence $kind"
  $expectedPath = "p2-evidence/$head/$kind.manifest.json"
  if ($binding.path -cne $expectedPath) { throw "$kind manifest path 漂移" }
  Assert-Sha $binding.sha256 64 "$kind manifest"
  $manifest = Resolve-GitPath $binding.path
  if ((Get-FileHash -LiteralPath $manifest -Algorithm SHA256).Hash.ToLowerInvariant() -cne $binding.sha256) {
    throw "$kind manifest hash 漂移"
  }
  $manifestDocument = Get-Content -LiteralPath $manifest -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $manifestDocument @('schemaVersion','kind','windowStartUtc','windowEndUtc','artifacts','capturedAtUtc') "$kind manifest"
  if ($manifestDocument.schemaVersion -cne 'p2-evidence-manifest-1' -or $manifestDocument.kind -cne $kind) {
    throw "$kind manifest schema/kind 漂移"
  }
  $manifestStart = [DateTimeOffset]::Parse($manifestDocument.windowStartUtc)
  $manifestEnd = [DateTimeOffset]::Parse($manifestDocument.windowEndUtc)
  if ($manifestStart -lt $windowStart -or $manifestEnd -gt $reviewTime -or
      $manifestStart.Offset -ne [TimeSpan]::Zero -or $manifestEnd.Offset -ne [TimeSpan]::Zero) {
    throw "$kind manifest 不在已审核 acceptance window"
  }
  $artifactInputs = @($manifestDocument.artifacts | ForEach-Object {
    Assert-Fields $_ @('pathScope','relativePath','sha256','bytes','lastWriteUtc') "$kind artifact"
    [ordered]@{ pathScope=$_.pathScope; relativePath=$_.relativePath }
  }) | ConvertTo-Json -Depth 4 -Compress
  if ((& $manifestGate -RepoRoot $root -ManifestRelativePath $binding.path -Kind $kind `
      -ArtifactsJson $artifactInputs -WindowStartUtc $manifestStart -WindowEndUtc $manifestEnd -Mode verify) -cne 'P2_EVIDENCE_MANIFEST_OK') {
    throw "$kind manifest/artifact 实时回读失败"
  }
  $manifests[$kind] = $manifestDocument
}
function Assert-ArtifactSet([object] $Manifest,[string[]] $Expected,[string] $Name,[switch] $AllowAdditional) {
  $actualRaw = @($Manifest.artifacts | ForEach-Object {
    if ($_.pathScope -isnot [string] -or $_.relativePath -isnot [string]) { throw "$Name artifact 类型非法" }
    "$($_.pathScope):$($_.relativePath.Replace('\','/'))"
  })
  $actual = @($actualRaw | Sort-Object -Unique)
  if ($actual.Count -ne $actualRaw.Count) { throw "$Name 含重复 artifact" }
  $wanted = @($Expected | Sort-Object -Unique)
  if ($AllowAdditional) {
    if (@($wanted | Where-Object { $actual -cnotcontains $_ }).Count -ne 0) { throw "$Name 缺必需 artifact" }
  } elseif (Compare-Object $wanted $actual -CaseSensitive) {
    throw "$Name artifact 集合漂移"
  }
}
$surefireReports = @(
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-user/target/surefire-reports/TEST-org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest.xml',
  'worktree:ai-video-api/ruoyi-admin/target/surefire-reports/TEST-org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest.xml')
$failsafeReports = @(
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireBranchIT.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT.xml',
  'worktree:ai-video-api/ruoyi-modules/ai-video/ai-video-infra/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT.xml',
  'worktree:ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT.xml',
  'worktree:ai-video-api/ai-video-user-api/target/failsafe-reports/TEST-org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT.xml')
Assert-ArtifactSet $manifests.unit $surefireReports 'unit manifest'
Assert-ArtifactSet $manifests.it $failsafeReports 'it manifest'
$jvmGate = Resolve-GitPath 'p2-jvm-evidence-gate.ps1'
foreach ($group in @(
  [ordered]@{ name='unit'; reports=$surefireReports },
  [ordered]@{ name='it'; reports=$failsafeReports })) {
  $startedAt = [DateTimeOffset]::Parse($manifests[$group.name].windowStartUtc)
  foreach ($evidenceKey in $group.reports) {
    if ($evidenceKey -cnotmatch '^worktree:(?<path>.+/TEST-(?<suite>org[.].+)[.]xml)$') {
      throw "$($group.name) JVM registry path 非法：$evidenceKey"
    }
    $reportPath = $Matches.path
    $expectedSuite = $Matches.suite
    $jvmResult = & $jvmGate -RepoRoot $root -AllowedPaths @() -ReportRelativePath $reportPath `
      -ExpectedSuite $expectedSuite -Phase GREEN -StartedAtUtc $startedAt
    if ($LASTEXITCODE -ne 0 -or $jvmResult -cne 'P2_JVM_EVIDENCE_OK') {
      throw "$($group.name) JVM fresh GREEN 复验失败：$expectedSuite"
    }
  }
}
$migrationReportPath = 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/failsafe-reports/TEST-org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT.xml'
$migrationLogPath = "p2-evidence/$head/migration-06-replay.log"
Assert-ArtifactSet $manifests.migration @("worktree:$migrationReportPath","git-metadata:$migrationLogPath") 'migration manifest'
[xml]$migrationReport = Get-Content -LiteralPath (Join-Path $root $migrationReportPath) -Raw -Encoding UTF8
if ($migrationReport.testsuite.name -cne 'org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT' -or
    [long]$migrationReport.testsuite.tests -le 0 -or [long]$migrationReport.testsuite.failures -ne 0 -or
    [long]$migrationReport.testsuite.errors -ne 0 -or [long]$migrationReport.testsuite.skipped -ne 0 -or
    @($migrationReport.testsuite.testcase | Where-Object { $_.name -ceq 'appliesMigrationsOneThroughSixAndReplaysP2Migration' }).Count -ne 1) {
  throw '06 migration fresh XML 未证明完整链、replay 与唯一主测试方法'
}
$migrationLog = Get-Content -LiteralPath (Resolve-GitPath $migrationLogPath) -Raw -Encoding UTF8
if ($migrationLog -notmatch '(?m)^P2_MIGRATION_06_REPLAY_OK$') { throw '缺少 06 replay 独立证据 sentinel' }
$requiredVitestReports = @(
  'worktree:ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task9-api.json',
  'worktree:ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task10-questionnaire-ui.json',
  'worktree:ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task11-task-recovery.json',
  'worktree:ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task12-supplement-evidence.json',
  'worktree:ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task13-demand-e2e.json')
Assert-ArtifactSet $manifests.vitest $requiredVitestReports 'vitest manifest'
$vitestExpectedByReport = [ordered]@{
  'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task9-api.json' = @(
    'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts')
  'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task10-questionnaire-ui.json' = @(
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx')
  'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task11-task-recovery.json' = @(
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx')
  'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task12-supplement-evidence.json' = @(
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx',
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx')
  'ai-video-ui/ai-video-webapp/.vitest-evidence/p2-task13-demand-e2e.json' = @(
    'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx')
}
$vitestGate = Resolve-GitPath 'p2-vitest-evidence-gate.ps1'
$vitestStartedAt = [DateTimeOffset]::Parse($manifests.vitest.windowStartUtc)
foreach ($reportRelative in $vitestExpectedByReport.Keys) {
  $vitestResult = & $vitestGate -RepoRoot $root -AllowedPaths @() -ReportRelativePath $reportRelative `
    -ExpectedTestFiles $vitestExpectedByReport[$reportRelative] -Phase GREEN -StartedAtUtc $vitestStartedAt
  if ($LASTEXITCODE -ne 0 -or $vitestResult -cne 'P2_VITEST_EVIDENCE_OK') {
    throw "Vitest fresh GREEN 复验失败：$reportRelative"
  }
}
$reportedVitestFiles = @()
foreach ($artifact in $manifests.vitest.artifacts) {
  if ($artifact.pathScope -cne 'worktree' -or $artifact.relativePath -notmatch '\.json$') { throw 'vitest manifest 只允许 worktree JSON' }
  $json = Get-Content -LiteralPath (Join-Path $root $artifact.relativePath) -Raw -Encoding UTF8 | ConvertFrom-Json
  $reportedVitestFiles += @($json.testResults | ForEach-Object {
    $full = [IO.Path]::GetFullPath($_.name)
    if (-not $full.StartsWith($root + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Vitest testResults 路径越界' }
    $full.Substring($root.Length + 1).Replace('\','/')
  })
}
$expectedVitestFiles = @(
  'ai-video-ui/ai-video-webapp/src/services/ai-video/studio/api.test.ts',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/DirectionForm.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/QuestionnaireProgress.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/AdaptiveQuestionCard.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useStudioDraft.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/hooks/useQuestionnaireTask.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/GenerationCostConfirm.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/TaskProgressPanel.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/SupplementFields.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/EvidenceReviewPanel.test.tsx',
  'ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/steps/DemandStep.test.tsx')
Assert-Array @($reportedVitestFiles | Sort-Object -Unique) @($expectedVitestFiles | Sort-Object -Unique) 'Vitest 11 文件 registry'
$standardsLogPath = "p2-evidence/$head/standards.log"
$scanLogPath = "p2-evidence/$head/scan.log"
Assert-ArtifactSet $manifests.standards @("git-metadata:$standardsLogPath") 'standards manifest'
Assert-ArtifactSet $manifests.scan @("git-metadata:$scanLogPath") 'scan manifest'
if ((Get-Content -LiteralPath (Resolve-GitPath $standardsLogPath) -Raw -Encoding UTF8) -notmatch '(?m)^P2_STANDARDS_OK$') { throw 'standards sentinel 缺失' }
if ((Get-Content -LiteralPath (Resolve-GitPath $scanLogPath) -Raw -Encoding UTF8) -notmatch '(?m)^P2_SCAN_OK$') { throw 'scan sentinel 缺失' }
$stableServices = @('IQuestionnaireContextService','IEvidenceReviewService')
$stableDtos = @(
  'QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO','QuestionnaireSupplementRevisionDTO',
  'EvidenceReviewContextDTO','AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO')
$dtoDefinitions = [ordered]@{
  QuestionnaireContextDTO=@(
    'Long draftId','Long currentBranchId','Long branchRevision','Long generationContextRevision',
    'String questionnaireHash','String knowledgeContextHash','String generationInputHash',
    'List<QuestionnaireAnswerRevisionDTO> answerRevisions',
    'QuestionnaireSupplementRevisionDTO supplementRevision','boolean contextReady')
  QuestionnaireAnswerRevisionDTO=@(
    'Long questionId','Integer questionNo','String targetSlotCode','String questionHash',
    'Long answerRevisionId','Long answerRevision','String answerHash',
    'String answerIdentityJson','String answerContextJson')
  QuestionnaireSupplementRevisionDTO=@(
    'Long supplementRevisionId','Long supplementRevision','String supplementHash',
    'String canonicalSupplementJson')
  EvidenceReviewContextDTO=@(
    'Long draftId','Long branchId','List<AcceptedEvidenceFactDTO> acceptedFacts',
    'List<EvidenceDecisionRevisionDTO> decisionRevisions')
  AcceptedEvidenceFactDTO=@(
    'Long factId','Long decisionRevision','String factHash','String factText',
    'String sourceTitle','String evidenceRef')
  EvidenceDecisionRevisionDTO=@('Long factId','Long decisionRevision')
}
$dtoRoot = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/dto'
$dtoComponentRegistry = [ordered]@{}
$dtoSourceSha256 = [ordered]@{}
foreach ($dtoName in $stableDtos) {
  $dtoFile = Join-Path $dtoRoot ($dtoName + '.java')
  if (-not (Test-Path -LiteralPath $dtoFile -PathType Leaf)) { throw "稳定 DTO 源码缺失：$dtoName" }
  $dtoSource = Normalize-JavaContract (Get-Content -LiteralPath $dtoFile -Raw -Encoding UTF8)
  $components = @($dtoDefinitions[$dtoName])
  Assert-JavaRecordHeader $dtoSource $dtoName $components
  $dtoComponentRegistry[$dtoName] = [ordered]@{
    components=$components
    componentSha256=(Get-CanonicalJsonSha256 $components)
  }
  $dtoSourceSha256[$dtoName] = (Get-FileHash -LiteralPath $dtoFile -Algorithm SHA256).Hash.ToLowerInvariant()
}
$serviceSignatures = [ordered]@{
  IQuestionnaireContextService=@(
    'QuestionnaireContextDTO getCurrentContext(Long draftId,Long branchId);',
    'QuestionnaireContextDTO lockCurrentContextForGeneration(Long draftId,Long branchId);')
  IEvidenceReviewService=@(
    'EvidenceReviewContextDTO getAcceptedContext(Long draftId,Long branchId);')
}
$serviceRoot = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service'
$serviceSourceSha256 = [ordered]@{}
foreach ($serviceName in $stableServices) {
  $serviceFile = Join-Path $serviceRoot ($serviceName + '.java')
  if (-not (Test-Path -LiteralPath $serviceFile -PathType Leaf)) { throw "稳定 Service 源码缺失：$serviceName" }
  $serviceSource = Normalize-JavaContract (Get-Content -LiteralPath $serviceFile -Raw -Encoding UTF8)
  foreach ($signature in @($serviceSignatures[$serviceName])) {
    Assert-JavaSignature $serviceSource $signature "$serviceName.$signature"
  }
  $serviceSourceSha256[$serviceName] = (Get-FileHash -LiteralPath $serviceFile -Algorithm SHA256).Hash.ToLowerInvariant()
}
$contextContractTestFile = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/QuestionnaireContextServiceTest.java'
$contextContractTestSource = Get-Content -LiteralPath $contextContractTestFile -Raw -Encoding UTF8
foreach ($methodToken in @(
  'lockMethodArchUnitTargetsOnlyGenerationLockEntry',
  'lockEntryIsMandatoryWriteTransactionAndLocksDraftBeforeCurrentBranch',
  'JavaMethod','getAnnotationOfType(Transactional.class)','getMethodCallsFromSelf()',
  'Propagation.MANDATORY','selectScopedForUpdate')) {
  if ($contextContractTestSource.IndexOf($methodToken,[StringComparison]::Ordinal) -lt 0) {
    throw "lock target-method ArchUnit/IT fixture 缺 token：$methodToken"
  }
}
$contextContractReport = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest.xml'
[xml]$contextContractXml = Get-Content -LiteralPath $contextContractReport -Raw -Encoding UTF8
foreach ($methodName in @(
  'lockMethodArchUnitTargetsOnlyGenerationLockEntry',
  'lockEntryIsMandatoryWriteTransactionAndLocksDraftBeforeCurrentBranch')) {
  if (@($contextContractXml.testsuite.testcase | Where-Object { $_.name -ceq $methodName }).Count -ne 1) {
    throw "fresh QuestionnaireContextServiceTest 未执行：$methodName"
  }
}
Assert-P0cTaskContracts $root
$lockedCurrentBranchProtocol = [ordered]@{
  method='QuestionnaireContextDTO IQuestionnaireContextService.lockCurrentContextForGeneration(Long draftId,Long branchId)'
  propagation='MANDATORY'; readOnly=$false
  scope=@('tenant_id','owner_type','owner_id')
  lockOrder=@('draft','current_branch')
  currentBranchCheck='branchId==draft.currentBranchId'
  snapshot='same_outer_write_transaction'
  p3RecheckFields=@('branchRevision','questionnaireHash','knowledgeContextHash')
  nextLockOrder=@('operation_slot','quota_account','task_or_group_member')
}
$p2WriteGuardProtocol = [ordered]@{
  method='IAiTaskService.requireGenerationContextWritable(Long draftId,Long branchRevision)'
  propagation='MANDATORY'
  lockOrder=@('draft','current_branch','generation_context_guard','p2_rows_or_membership','audit')
  guardedWrites=@('direction','answer','supplement','evidence_decision')
  businessCode=[int]46123
  errorDataFields=@('rootTaskId','taskType','status')
  zeroSideEffects=@('business_write','audit','task','usage_operation','quota')
}
$answerIdentitySchema = [ordered]@{
  keys=@('questionId','questionHash','selectedCodes','customSelected','customText')
  types=[ordered]@{
    questionId='decimal_string'; questionHash='sha256_lowercase_hex'
    selectedCodes='array<string>'; customSelected='boolean'; customText='string|null'
  }
  nullable=@('customText')
  orderRules=[ordered]@{
    questionId='positive_decimal_no_plus_no_leading_zero'
    selectedCodes='unicode_code_point_ascending_unique_excludes_custom'
    customText='null_when_customSelected_false'
  }
}
$answerContextSchema = [ordered]@{
  keys=@('questionText','targetSlotCode','selectedOptions','customText')
  types=[ordered]@{
    questionText='string'; targetSlotCode='string'
    selectedOptions='array<object>'; customText='string|null'
  }
  nullable=@('customText')
  selectedOptionKeys=@('code','normalizedValue','slotContributions')
  selectedOptionTypes=[ordered]@{
    code='string'; normalizedValue='string'; slotContributions='array<string>'
  }
  selectedOptionNullable=@()
  orderRules=[ordered]@{
    selectedOptions='code_unicode_code_point_ascending_unique'
    slotContributions='unicode_code_point_ascending_unique'
    strings='nfc_trim_collapse_whitespace'
  }
}
$contextSemantics = [ordered]@{
  answerHashInput='sha256:utf8:answerIdentityJson'
  answerIdentitySchema=$answerIdentitySchema
  answerIdentitySchemaSha256=(Get-CanonicalJsonSha256 $answerIdentitySchema)
  answerContextRole='p3_semantic_input_only_not_hash_input'
  answerContextSchema=$answerContextSchema
  answerContextSchemaSha256=(Get-CanonicalJsonSha256 $answerContextSchema)
  answerOrder=@('questionNo','answerRevision')
  supplementIdentity=@('supplementRevisionId','supplementRevision','supplementHash')
  factIdentity=@('factId','decisionRevision','factHash')
  factOrder=@('factId')
  decisionRevisionMeaning='business_aggregate_revision_not_row_id'
  acceptedFactDecisionSets='exact_same_fact_ids'
  listMutability='immutable_defensive_copy'
  hashEncoding='sha256_lowercase_hex'
}
$answerContractTestFile = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/questionnaire/service/AnswerNormalizationServiceTest.java'
if (-not (Test-Path -LiteralPath $answerContractTestFile -PathType Leaf)) { throw '答案双 JSON 契约测试源码缺失' }
$answerContractTestSource = Get-Content -LiteralPath $answerContractTestFile -Raw -Encoding UTF8
foreach ($contractToken in @(
  'hashesOnlyFiveIdentityFieldsAndKeepsContextIndependent',
  'questionId','questionHash','selectedCodes','customSelected','customText',
  'questionText','targetSlotCode','selectedOptions','normalizedValue','slotContributions',
  'e1f99e5ac2f55989e059a4caccee90da1f102484bcd1e3b45bc638ea9d24a9b3'
)) {
  if ($answerContractTestSource.IndexOf($contractToken,[StringComparison]::Ordinal) -lt 0) {
    throw "答案双 JSON 契约测试缺 token：$contractToken"
  }
}
$answerContractReport = Join-Path $root 'ai-video-api/ruoyi-modules/ai-video/ai-video-core/target/surefire-reports/TEST-org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest.xml'
[xml]$answerContractXml = Get-Content -LiteralPath $answerContractReport -Raw -Encoding UTF8
if (@($answerContractXml.testsuite.testcase | Where-Object {
      $_.name -ceq 'hashesOnlyFiveIdentityFieldsAndKeepsContextIndependent' }).Count -ne 1) {
  throw 'fresh AnswerNormalizationServiceTest 未执行双 JSON 黄金 fixture'
}
$testRegistry = [ordered]@{
  javaSelectors=[int]20; surefireSelectors=[int]14; failsafeSelectors=[int]6
  localIntegrationProfiles=[int]6; vitestFiles=[int]11; vitestReportsMinimum=[int]5
}
$p3ConsumerContract = [ordered]@{
  requiredBaseHead=$head
  removeFakesFrom=@('production','all-real-it')
  stableServices=$stableServices
  stableDtos=$stableDtos
  serviceSignatures=$serviceSignatures
  serviceSourceSha256=$serviceSourceSha256
  dtoComponentRegistry=$dtoComponentRegistry
  dtoSourceSha256=$dtoSourceSha256
  lockedCurrentBranchProtocol=$lockedCurrentBranchProtocol
  p2WriteGuardProtocol=$p2WriteGuardProtocol
  contextSemantics=$contextSemantics
  forbiddenDependencies=@('questionnaire-mapper','questionnaire-table','questionnaire-entity','user-vo')
}
$core = [ordered]@{
  fullF3Ready=$true
  f1Head=$f1.f1Head
  f1AmendmentHead=$addendum.amendmentHead
  f2Head=$f2.f2Head
  f3Head=$head
  owner=$review.owner
  reviewer=$review.reviewer
  reviewStatus=$review.reviewStatus
  reviewCompletedAtUtc=$review.reviewCompletedAtUtc
  p2AcceptanceWindowStart=$window.startedAtUtc
  p2AcceptanceWindowEnd=$review.reviewCompletedAtUtc
  f1HandoffSha256=(Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant()
  f1AddendumSha256=(Get-FileHash -LiteralPath $addendumFile -Algorithm SHA256).Hash.ToLowerInvariant()
  f2HandoffSha256=(Get-FileHash -LiteralPath $f2File -Algorithm SHA256).Hash.ToLowerInvariant()
  migrationChain=@('01','02','03','04','04a','05','06')
  migrationRepeat06=$true
  stableServices=$stableServices
  stableDtos=$stableDtos
  serviceSignatures=$serviceSignatures
  serviceSourceSha256=$serviceSourceSha256
  dtoComponentRegistry=$dtoComponentRegistry
  dtoSourceSha256=$dtoSourceSha256
  lockedCurrentBranchProtocol=$lockedCurrentBranchProtocol
  p2WriteGuardProtocol=$p2WriteGuardProtocol
  contextSemantics=$contextSemantics
  downstreamConsumers=@('P3')
  testRegistry=$testRegistry
  p3ConsumerContract=$p3ConsumerContract
  revisionMappingContractOwner=$review.revisionMappingContractOwner
  evidence=$review.evidence
}
$handoffFields = @(
  'fullF3Ready','f1Head','f1AmendmentHead','f2Head','f3Head','owner','reviewer','reviewStatus','reviewCompletedAtUtc',
  'p2AcceptanceWindowStart','p2AcceptanceWindowEnd','f1HandoffSha256','f1AddendumSha256','f2HandoffSha256','migrationChain',
  'migrationRepeat06','stableServices','stableDtos','serviceSignatures','serviceSourceSha256',
  'dtoComponentRegistry','dtoSourceSha256','lockedCurrentBranchProtocol','p2WriteGuardProtocol','contextSemantics',
  'downstreamConsumers','testRegistry','p3ConsumerContract',
  'revisionMappingContractOwner','evidence','capturedAtUtc')
$handoffFile = Resolve-GitPath 'p2-f3-handoff.json'
$coreJson = $core | ConvertTo-Json -Depth 20 -Compress
if (Test-Path -LiteralPath $handoffFile -PathType Leaf) {
  $existing = Get-Content -LiteralPath $handoffFile -Raw -Encoding UTF8 | ConvertFrom-Json
  Assert-Fields $existing $handoffFields 'F3 handoff'
  $existingCore = [ordered]@{}
  foreach ($key in $core.Keys) { $existingCore[$key] = $existing.$key }
  if (($existingCore | ConvertTo-Json -Depth 20 -Compress) -cne $coreJson) { throw '既有 F3 payload 不同，拒绝覆盖' }
} else {
  $document = [ordered]@{}
  foreach ($key in $core.Keys) { $document[$key] = $core[$key] }
  $document.capturedAtUtc = [DateTime]::UtcNow.ToString('o')
  $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($document | ConvertTo-Json -Depth 20 -Compress))
  $stream = [IO.File]::Open($handoffFile,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
  try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
}
$verified = Get-Content -LiteralPath $handoffFile -Raw -Encoding UTF8 | ConvertFrom-Json
Assert-Fields $verified $handoffFields 'F3 handoff readback'
Assert-TrueBoolean $verified.fullF3Ready 'F3.fullF3Ready'
Assert-TrueBoolean $verified.migrationRepeat06 'F3.migrationRepeat06'
Assert-Sha $verified.f1Head 40 'F3.f1Head'
Assert-Sha $verified.f1AmendmentHead 40 'F3.f1AmendmentHead'
Assert-Sha $verified.f2Head 40 'F3.f2Head'
Assert-Sha $verified.f3Head 40 'F3.f3Head'
Assert-Sha $verified.f1HandoffSha256 64 'F3.f1HandoffSha256'
Assert-Sha $verified.f1AddendumSha256 64 'F3.f1AddendumSha256'
Assert-Sha $verified.f2HandoffSha256 64 'F3.f2HandoffSha256'
if ($verified.f1Head -cne $f1.f1Head -or $verified.f1AmendmentHead -cne $addendum.amendmentHead -or
    $verified.f2Head -cne $f2.f2Head -or $verified.f3Head -cne $head -or
    $verified.f1HandoffSha256 -cne (Get-FileHash -LiteralPath $f1File -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $verified.f1AddendumSha256 -cne (Get-FileHash -LiteralPath $addendumFile -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $verified.f2HandoffSha256 -cne (Get-FileHash -LiteralPath $f2File -Algorithm SHA256).Hash.ToLowerInvariant()) {
  throw 'F3 baseline/addendum/handoff hash readback 漂移'
}
Assert-Array $verified.migrationChain @('01','02','03','04','04a','05','06') 'migrationChain'
Assert-Array $verified.stableServices $stableServices 'stableServices'
Assert-Array $verified.stableDtos $stableDtos 'stableDtos'
Assert-Fields $verified.serviceSignatures $stableServices 'serviceSignatures'
Assert-Fields $verified.serviceSourceSha256 $stableServices 'serviceSourceSha256'
Assert-Fields $verified.dtoComponentRegistry $stableDtos 'dtoComponentRegistry'
Assert-Fields $verified.dtoSourceSha256 $stableDtos 'dtoSourceSha256'
foreach ($serviceName in $stableServices) {
  Assert-Array $verified.serviceSignatures.$serviceName $serviceSignatures[$serviceName] "serviceSignatures.$serviceName"
  Assert-Sha $verified.serviceSourceSha256.$serviceName 64 "serviceSourceSha256.$serviceName"
  if ($verified.serviceSourceSha256.$serviceName -cne $serviceSourceSha256[$serviceName]) { throw "$serviceName source SHA 漂移" }
}
foreach ($dtoName in $stableDtos) {
  Assert-Fields $verified.dtoComponentRegistry.$dtoName @('components','componentSha256') "dtoComponentRegistry.$dtoName"
  Assert-Array $verified.dtoComponentRegistry.$dtoName.components $dtoDefinitions[$dtoName] "$dtoName.components"
  Assert-Sha $verified.dtoComponentRegistry.$dtoName.componentSha256 64 "$dtoName.componentSha256"
  Assert-Sha $verified.dtoSourceSha256.$dtoName 64 "dtoSourceSha256.$dtoName"
  if ($verified.dtoComponentRegistry.$dtoName.componentSha256 -cne (Get-CanonicalJsonSha256 $dtoDefinitions[$dtoName]) -or
      $verified.dtoSourceSha256.$dtoName -cne $dtoSourceSha256[$dtoName]) { throw "$dtoName component/source SHA 漂移" }
}
Assert-Fields $verified.lockedCurrentBranchProtocol @(
  'method','propagation','readOnly','scope','lockOrder','currentBranchCheck','snapshot','p3RecheckFields','nextLockOrder') 'lockedCurrentBranchProtocol'
Assert-Fields $verified.p2WriteGuardProtocol @(
  'method','propagation','lockOrder','guardedWrites','businessCode','errorDataFields','zeroSideEffects') 'p2WriteGuardProtocol'
Assert-Fields $verified.contextSemantics @(
  'answerHashInput','answerIdentitySchema','answerIdentitySchemaSha256','answerContextRole',
  'answerContextSchema','answerContextSchemaSha256','answerOrder','supplementIdentity',
  'factIdentity','factOrder','decisionRevisionMeaning','acceptedFactDecisionSets','listMutability','hashEncoding') 'contextSemantics'
if ($verified.lockedCurrentBranchProtocol.readOnly -isnot [bool] -or
    $verified.lockedCurrentBranchProtocol.readOnly -or
    $verified.lockedCurrentBranchProtocol.propagation -cne 'MANDATORY' -or
    [int]$verified.p2WriteGuardProtocol.businessCode -ne 46123 -or
    $verified.contextSemantics.answerHashInput -cne 'sha256:utf8:answerIdentityJson' -or
    $verified.contextSemantics.answerContextRole -cne 'p3_semantic_input_only_not_hash_input') {
  throw '锁/写守卫/identity-context 语义漂移'
}
Assert-Array $verified.lockedCurrentBranchProtocol.lockOrder @('draft','current_branch') 'lockedCurrentBranchProtocol.lockOrder'
Assert-Array $verified.lockedCurrentBranchProtocol.p3RecheckFields @(
  'branchRevision','questionnaireHash','knowledgeContextHash') 'lockedCurrentBranchProtocol.p3RecheckFields'
Assert-Array $verified.p2WriteGuardProtocol.errorDataFields @(
  'rootTaskId','taskType','status') 'p2WriteGuardProtocol.errorDataFields'
Assert-Fields $verified.contextSemantics.answerIdentitySchema @(
  'keys','types','nullable','orderRules') 'contextSemantics.answerIdentitySchema'
Assert-Array $verified.contextSemantics.answerIdentitySchema.keys @(
  'questionId','questionHash','selectedCodes','customSelected','customText') 'answerIdentitySchema.keys'
Assert-Fields $verified.contextSemantics.answerIdentitySchema.types @(
  'questionId','questionHash','selectedCodes','customSelected','customText') 'answerIdentitySchema.types'
Assert-Array $verified.contextSemantics.answerIdentitySchema.nullable @('customText') 'answerIdentitySchema.nullable'
Assert-Fields $verified.contextSemantics.answerIdentitySchema.orderRules @(
  'questionId','selectedCodes','customText') 'answerIdentitySchema.orderRules'
Assert-Sha $verified.contextSemantics.answerIdentitySchemaSha256 64 'answerIdentitySchemaSha256'
if ($verified.contextSemantics.answerIdentitySchemaSha256 -cne
    (Get-CanonicalJsonSha256 $verified.contextSemantics.answerIdentitySchema)) {
  throw 'answer identity schema digest 漂移'
}
Assert-Fields $verified.contextSemantics.answerContextSchema @(
  'keys','types','nullable','selectedOptionKeys','selectedOptionTypes','selectedOptionNullable','orderRules') 'contextSemantics.answerContextSchema'
Assert-Array $verified.contextSemantics.answerContextSchema.keys @(
  'questionText','targetSlotCode','selectedOptions','customText') 'answerContextSchema.keys'
Assert-Fields $verified.contextSemantics.answerContextSchema.types @(
  'questionText','targetSlotCode','selectedOptions','customText') 'answerContextSchema.types'
Assert-Array $verified.contextSemantics.answerContextSchema.nullable @('customText') 'answerContextSchema.nullable'
Assert-Array $verified.contextSemantics.answerContextSchema.selectedOptionKeys @(
  'code','normalizedValue','slotContributions') 'answerContextSchema.selectedOptionKeys'
Assert-Fields $verified.contextSemantics.answerContextSchema.selectedOptionTypes @(
  'code','normalizedValue','slotContributions') 'answerContextSchema.selectedOptionTypes'
Assert-Array $verified.contextSemantics.answerContextSchema.selectedOptionNullable @() 'answerContextSchema.selectedOptionNullable'
Assert-Fields $verified.contextSemantics.answerContextSchema.orderRules @(
  'selectedOptions','slotContributions','strings') 'answerContextSchema.orderRules'
Assert-Sha $verified.contextSemantics.answerContextSchemaSha256 64 'answerContextSchemaSha256'
if ($verified.contextSemantics.answerContextSchemaSha256 -cne
    (Get-CanonicalJsonSha256 $verified.contextSemantics.answerContextSchema)) {
  throw 'answer context schema digest 漂移'
}
Assert-Fields $verified.p3ConsumerContract @(
  'requiredBaseHead','removeFakesFrom','stableServices','stableDtos','serviceSignatures','serviceSourceSha256',
  'dtoComponentRegistry','dtoSourceSha256','lockedCurrentBranchProtocol','p2WriteGuardProtocol',
  'contextSemantics','forbiddenDependencies') 'p3ConsumerContract'
if (($verified.p3ConsumerContract | ConvertTo-Json -Depth 20 -Compress) -cne
    ($p3ConsumerContract | ConvertTo-Json -Depth 20 -Compress)) { throw 'p3ConsumerContract readback 漂移' }
if (($verified.p3ConsumerContract.contextSemantics | ConvertTo-Json -Depth 20 -Compress) -cne
    ($verified.contextSemantics | ConvertTo-Json -Depth 20 -Compress)) {
  throw 'top-level/nested contextSemantics 不全等'
}
Assert-Fields $verified.testRegistry @('javaSelectors','surefireSelectors','failsafeSelectors','localIntegrationProfiles','vitestFiles','vitestReportsMinimum') 'testRegistry'
foreach ($name in @($testRegistry.Keys)) {
  if ($verified.testRegistry.$name -isnot [long] -and $verified.testRegistry.$name -isnot [int]) { throw "$name 必须是 JSON integer" }
  if ([int]$verified.testRegistry.$name -ne [int]$testRegistry[$name]) { throw "$name 计数漂移" }
}
'P2_F3_HANDOFF_OK'
```

## 8. 最终机械门禁

以下 PowerShell 从仓库根运行；命令本身不得暂存/提交任何文件：

```powershell
$ErrorActionPreference = 'Stop'
$rootText = (& git rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($rootText)) { throw '最终门禁无法解析仓库根' }
$repoRoot = [IO.Path]::GetFullPath($rootText.Trim())
Set-Location -LiteralPath $repoRoot
$targetRelative = 'docs/superpowers/plans/2026-07-28-say-requirements-copy-generation-p2-questionnaire.md'
$target = [IO.Path]::GetFullPath((Join-Path $repoRoot $targetRelative))
if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw 'P2 计划文件不存在' }
$head = (& git rev-parse 'HEAD^{commit}').Trim().ToLowerInvariant()
if ($head -cnotmatch '^[0-9a-f]{40}$') { throw '当前 HEAD 不是 40 位 commit SHA' }
foreach ($authority in @(
  'RULES.md','docs/AI_AGENT_GOVERNANCE.md','docs/API_CONTRACT.md','docs/DOMAIN_MODEL.md',
  'docs/ASYNC_TASKS.md','docs/BACKEND_GUIDE.md','docs/FRONTEND_GUIDE.md'
)) {
  if (-not (Test-Path -LiteralPath (Join-Path $repoRoot $authority) -PathType Leaf)) {
    throw "P2 权威文档缺失：$authority"
  }
}
$statusLines = @(& git status --porcelain=v1 -uall)
if ($LASTEXITCODE -ne 0) { throw '最终计划自检无法读取 git status' }
if ($statusLines.Count -ne 0) {
  $statusLines
  throw '最终计划自检要求完全 clean；任何计划、业务或 metadata 脏文件都拒绝'
}

foreach ($required in @(
  'F0','F1','F2','F3',
  'IKnowledgeRoutingService','IKnowledgeSnapshotService',
  'KnowledgeRouteRequestDTO','KnowledgeRouteResultDTO','KnowledgePlanDTO',
  'KnowledgeSnapshotRequestDTO','KnowledgeSnapshotDTO',
  'IQuestionnaireContextService','IEvidenceReviewService',
  'QuestionnaireContextDTO','QuestionnaireAnswerRevisionDTO',
  'QuestionnaireSupplementRevisionDTO','EvidenceReviewContextDTO',
  'AcceptedEvidenceFactDTO','EvidenceDecisionRevisionDTO',
  '{"code": "authoritative", "customText": null}',
  'ToneStyleInput','usesSortedToneObjectsAndOneCustomTone',
  'AiTaskExecutionLeaseDTO','AiTaskAttemptHandleDTO','ProviderUsageDTO',
  'org.dromara.aivideo.provider.ModelProvider','org.dromara.aivideo.client.WebSearchClient',
  'TaskResultReferenceDTO.of("question", questionId)',
  'TaskResultReferenceDTO.of("evidence_batch", batchId)',
  'question:{draftId}:{branchRevision}:{nextQuestionOrdinal}',
  'questionnaire:{draftId}','questionnaire:{draftId}:{branchRevision}',
  'evidence:{draftId}:{branchRevision}','evidence:{draftId}','evidence:{draftId}:{branchRevision}',
  'STALE_BRANCH_RESULT','p2-rebase-baseline.ps1','git rebase $targetHead',
  'p2-f1-rebase.json','p2-f2-rebase.json','p2-f3-handoff.json',
  'p0c-f1-contract-addendum.json','originalF1HandoffSha256',
  '20260728_04a_p0c_task_group_guard.sql','idx_av_ai_task_active_group',
  'lockCurrentContextForGeneration','serviceSignatures','serviceSourceSha256',
  'dtoComponentRegistry','dtoSourceSha256','lockedCurrentBranchProtocol',
  'p2WriteGuardProtocol','contextSemantics','answerIdentityJson','answerContextJson',
  'AppAuthorizationActorResolver.requireActor()','AppSecurityAuditDTO',
  'no_results','EVIDENCE_NO_RESULTS_SAVED','membership_only'
)) {
  if (-not (Select-String -LiteralPath $target -SimpleMatch $required -Quiet)) {
    throw "P2 计划缺少：$required"
  }
}

$text = Get-Content -LiteralPath $target -Raw -Encoding UTF8
foreach ($obsolete in @(
  ('question_version' + '_hash'),
  ('selected_option_codes' + '_json'),
  ('canonical' + 'AnswerJson')
)) {
  if ($text.IndexOf($obsolete,[StringComparison]::Ordinal) -ge 0) {
    throw "P2 仍含废弃答案契约：$obsolete"
  }
}
$forbiddenCoreLayer = '(?:^|[\\/])ai-video-core[\\/].*src[\\/](?:main|test)[\\/]java[\\/].*[\\/](?:' +
  ('app' + 'lication|po' + 'rt|adap' + 'ter|com' + 'mand|mo' + 'del|aggregate|repository|routing|validation|infra|client') + ')[\\/]'
$legalCorePath = 'ai-video-api/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/service/IEvidenceResultService.java'
$legalInfraPath = 'ai-video-api/ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/EvidenceSearchProvider.java'
$similarModulePath = 'ai-video-api/ai-video-core-tools/src/main/java/org/dromara/aivideo/questionnaire/client/ToolClient.java'
$illegalCoreClient = 'ai-video-api/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/' + 'client/UnsafeHttpClient.java'
$illegalCoreApplication = 'ai-video-api/ai-video-core/src/main/java/org/dromara/aivideo/questionnaire/' + 'application/QuestionnaireApplicationService.java'
if ($legalCorePath -match $forbiddenCoreLayer -or $legalInfraPath -match $forbiddenCoreLayer -or
    $similarModulePath -match $forbiddenCoreLayer) {
  throw 'core 分层扫描对合法 core/infra 或相似模块名路径产生假阳性'
}
if ($illegalCoreClient -notmatch $forbiddenCoreLayer -or $illegalCoreApplication -notmatch $forbiddenCoreLayer) {
  throw 'core 分层扫描未命中真实 core client/application 路径'
}
foreach ($pathMatch in [regex]::Matches($text,'`(?<path>ai-video-[^`\r\n]+)`')) {
  $plannedPath = $pathMatch.Groups['path'].Value
  if ($plannedPath -match $forbiddenCoreLayer) { throw "P2 仍含非 RuoYi 核心业务分层路径：$plannedPath" }
}
$coreBoVo = 'ai-video-core[\\/].*src[\\/]main[\\/]java[\\/].*[\\/]domain[\\/](?:bo|vo)[\\/]'
if ($text -match $coreBoVo) { throw 'P2 仍把 HTTP BO/VO 放在 core' }
$p1DataAccess = 'knowledge[.\\/]+mapper|(?i:\b(?:from|join|update|insert\s+into|delete\s+from)\s+av_knowledge)'
if ($text -match $p1DataAccess) { throw 'P2 仍直接访问 P1 Mapper 或知识表' }
$duplicateP2InfraContract = 'questionnaire[\\/](?:' +
  ('client[\\/]WebSearch' + 'Client|provider[\\/]QuestionModel' + 'Provider') + ')[.]java'
$legalP2Provider = 'ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/QuestionProviderClient.java'
$illegalP2ModelContract = 'ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/provider/QuestionModel' + 'Provider.java'
$illegalP2SearchContract = 'ai-video-infra/src/main/java/org/dromara/aivideo/questionnaire/client/WebSearch' + 'Client.java'
if ($legalP2Provider -match $duplicateP2InfraContract) { throw '重复基础设施契约扫描对合法 provider 产生假阳性' }
if ($illegalP2ModelContract -notmatch $duplicateP2InfraContract -or
    $illegalP2SearchContract -notmatch $duplicateP2InfraContract) {
  throw '重复基础设施契约扫描未命中真实重复文件'
}
if ($text -match $duplicateP2InfraContract) { throw 'P2 仍规划重复的全局 ModelProvider/WebSearchClient 契约' }

$taskHeadings = @(Select-String -LiteralPath $target -Pattern '^### 任务 \d+：')
if ($taskHeadings.Count -ne 13) { throw "P2 任务数必须为 13，实际 $($taskHeadings.Count)" }
$taskSpecificFixtureRegistry = [ordered]@{
  '1'=@('QuestionnaireStableContractTest','freezesUpstreamGuardsAndAnswerIdentityContextHeader')
  '2'=@('QuestionnaireMigrationIT','appliesMigrationsOneThroughSixAndReplaysP2Migration')
  '3'=@('AnswerNormalizationServiceTest','hashesOnlyFiveIdentityFieldsAndKeepsContextIndependent')
  '4'=@('DirectionRevisionServiceTest','derivesServerOnlyVersionsFromOnePublishedAggregateSnapshot')
  '5'=@('QuestionnaireCompletenessServiceTest','rejectsEveryFrozenBoundaryBeforeLockOrWrite')
  '6'=@('QuestionGenerationTaskHandlerIT','usesRenewedLeaseAndSingleAttemptTerminalBeforeAdoption')
  '7'=@('AllowedExternalUriPolicyTest','revalidatesEveryRedirectHopAndRejectsDnsRebindingBeforeSecondConnect')
  '8'=@('StudioQuestionnaireControllerTest','freezesNineEndpointsAndSixBosExactly')
  '9'=@('api.test.ts','publishes only the aggregate catalog version and sends no server-only sub-version')
  '10'=@('DirectionForm.test.tsx','keeps unselected direction text locally, omits it, and renders only current question')
  '11'=@('TaskProgressPanel.test.tsx','resumesSavedAnswerWithStartAndNeverSubmitsTheTurnAgain')
  '12'=@('SupplementFields.test.tsx','usesSortedToneObjectsAndOneCustomTone')
  '13'=@('QuestionnaireEndToEndIT','completesQuestionnaireAndTreatsPaidNoResultsAsSettledSuccess')
}
if ($taskSpecificFixtureRegistry.Count -ne 13) { throw '任务专属 fixture registry 必须为 13 项' }
$requiredCardFields = @(
  '单一目标／不做','权威源','治理等级／触发项','实施者／reviewer／并发',
  '精确路径／数据范围','允许影响','前置／退出','结构签名检查点',
  'GREEN 独立复跑检查点','正向／反向验收','统一 gate','准确命令／证据','固定输出')
for ($index = 0; $index -lt $taskHeadings.Count; $index++) {
  $start = $taskHeadings[$index].LineNumber - 1
  $end = if ($index + 1 -lt $taskHeadings.Count) { $taskHeadings[$index + 1].LineNumber - 2 } else {
    (Select-String -LiteralPath $target -Pattern '^## 7\.' | Select-Object -First 1).LineNumber - 2
  }
  $lines = Get-Content -LiteralPath $target -Encoding UTF8
  $card = ($lines[$start..$end] -join "`n")
  foreach ($field in $requiredCardFields) {
    if ($card.IndexOf($field,[StringComparison]::Ordinal) -lt 0) { throw "任务 $($index + 1) 缺任务卡字段：$field" }
  }
  if ($card -notmatch '(?m)^```(?:java|sql|ts|tsx|json|text)\s*$') {
    throw "任务 $($index + 1) 缺可复制 RED fixture/最小签名代码块"
  }
  foreach ($phaseToken in @('RED','GREEN')) {
    if ($card.IndexOf($phaseToken,[StringComparison]::Ordinal) -lt 0) {
      throw "任务 $($index + 1) 缺 $phaseToken 可执行状态"
    }
  }
  foreach ($fixtureMarker in $taskSpecificFixtureRegistry[[string]($index + 1)]) {
    if ($card.IndexOf($fixtureMarker,[StringComparison]::Ordinal) -lt 0) {
      throw "任务 $($index + 1) 缺专属可执行 fixture/method：$fixtureMarker"
    }
  }
}
$tagMentions = @(Select-String -LiteralPath $target -SimpleMatch '@Tag("dev")')
if ($tagMentions.Count -eq 0) { throw 'P2 缺少 JUnit dev 标签门禁' }

foreach ($forbidden in @(
  (' R' + '0'),(' R' + '1'),('Target' + 'Test'),('Target' + 'IT'),
  ('Question' + 'OutputValidator'),('Questionnaire' + 'PersistenceIT'),
  ('UserQuestionnaire' + 'AssemblyTest'),('user/' + 'questionnaire'),
  ('D:' + '\Workspace'),('Set-Location ' + '.\'))) {
  if ($text.IndexOf($forbidden,[StringComparison]::Ordinal) -ge 0) { throw "P2 计划仍含禁止文本：$forbidden" }
}
$isolatedLocalProfile = "'-P" + "local-integration-test'"
if ($text.IndexOf($isolatedLocalProfile,[StringComparison]::Ordinal) -ge 0) {
  throw 'P2 IT 命令仍孤立激活 local-integration-test，dev group 会丢失'
}
if ($text.IndexOf("'-Pdev,local-integration-test'",[StringComparison]::Ordinal) -lt 0) {
  throw 'P2 IT 命令缺 dev + local-integration-test 组合 profile'
}
$javaSelectors = @(
  'org.dromara.aivideo.questionnaire.QuestionnaireStableContractTest',
  'org.dromara.aivideo.questionnaire.service.AnswerNormalizationServiceTest',
  'org.dromara.aivideo.questionnaire.service.DirectionRevisionServiceTest',
  'org.dromara.aivideo.questionnaire.service.QuestionTurnServiceTest',
  'org.dromara.aivideo.questionnaire.service.QuestionnaireCompletenessServiceTest',
  'org.dromara.aivideo.questionnaire.service.SupplementRevisionServiceTest',
  'org.dromara.aivideo.questionnaire.service.QuestionGenerationServiceTest',
  'org.dromara.aivideo.questionnaire.service.EvidenceReviewServiceTest',
  'org.dromara.aivideo.questionnaire.service.QuestionnaireContextServiceTest',
  'org.dromara.aivideo.questionnaire.provider.QuestionProviderClientTest',
  'org.dromara.aivideo.questionnaire.provider.QuestionGenerationOutputValidatorTest',
  'org.dromara.aivideo.questionnaire.evidence.AllowedExternalUriPolicyTest',
  'org.dromara.aivideo.user.studio.controller.StudioQuestionnaireControllerTest',
  'org.dromara.aivideo.bootstrap.PlatformQuestionnaireIsolationTest',
  'org.dromara.aivideo.questionnaire.QuestionnaireMigrationIT',
  'org.dromara.aivideo.questionnaire.QuestionnaireBranchIT',
  'org.dromara.aivideo.questionnaire.listener.QuestionGenerationTaskHandlerIT',
  'org.dromara.aivideo.questionnaire.listener.EvidenceRetrievalTaskHandlerIT',
  'org.dromara.aivideo.bootstrap.UserQuestionnaireAssemblyIT',
  'org.dromara.aivideo.bootstrap.QuestionnaireEndToEndIT')
if (@($javaSelectors | Sort-Object -Unique).Count -ne 20) { throw 'Java selector registry 不是 20 个唯一值' }
foreach ($selector in $javaSelectors) {
  if ($text.IndexOf($selector,[StringComparison]::Ordinal) -lt 0) { throw "缺 Java selector：$selector" }
}
foreach ($method in @(
  'QuestionGenerationOutputValidatorTest.rejectsEveryStructuralViolationBeforeAttemptCanComplete',
  'AllowedExternalUriPolicyTest.rejectsUnsafeEvidenceUris',
  'QuestionnaireMigrationIT.appliesMigrationsOneThroughSixAndReplaysP2Migration')) {
  if ($text.IndexOf($method,[StringComparison]::Ordinal) -lt 0) { throw "缺 master 代表性硬门禁：$method" }
}

$blocks = [regex]::Matches(
  $text, '(?ms)^```powershell\s*\r?\n(?<body>.*?)^```\s*$')
if ($blocks.Count -ne 9) { throw "P2 PowerShell 代码块必须为 9，实际 $($blocks.Count)" }
for ($index = 0; $index -lt $blocks.Count; $index++) {
  $bodyLf = $blocks[$index].Groups['body'].Value.Replace("`r`n","`n")
  $bodyCrLf = $bodyLf.Replace("`n","`r`n")
  foreach ($variant in @($bodyLf,$bodyCrLf)) {
    $tokens = $null
    $errors = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($variant,[ref]$tokens,[ref]$errors)
    if ($errors.Count -gt 0) { $errors; throw "P2 PowerShell 代码块 $($index + 1) native/LF/CRLF AST 失败" }
  }
  foreach ($requiredText in @('$ErrorActionPreference','rev-parse --show-toplevel','P2_')) {
    if ($bodyLf.IndexOf($requiredText,[StringComparison]::Ordinal) -lt 0) { throw "PowerShell 块 $($index + 1) 缺少 $requiredText" }
  }
}

powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/validate-development-standards.ps1')
if ($LASTEXITCODE -ne 0) { throw '开发标准校验失败' }
git diff --check -- $target
if ($LASTEXITCODE -ne 0) { throw 'P2 计划差异检查失败' }
git status --short
'P2_PLAN_MECHANICAL_GATE_OK'
```

预期：13 张任务卡、四个集中 gate、一个真实 rebase 执行器、共 9 个 PowerShell 块以及 P0-C/P1/P2 精确稳定契约全部命中；核心旧分层、core BO/VO、P1 数据访问、废弃答案契约零命中；PowerShell AST、开发标准和 `git diff --check` 通过；当前并行整改阶段只允许本 P2 文件与其他 writer 的精确 master/P3 计划文件变化。

## 9. P2 完成交接清单

- [ ] F0/F1/F2/F3 的允许、禁止、rebase 和退出证据均已执行，不以“可并行”绕过真实集成顺序。
- [ ] P2 生产源码和真实 IT 只消费 P1 两个 Service 与五个 DTO；隔离单元测试 fake 不进入生产或真实 IT。
- [ ] P2 向 P3 只提供两个稳定 Service 与六个稳定 DTO；P3 无 P2 Mapper/表/Entity/HTTP VO 依赖。
- [ ] core 只有 `domain/dto/mapper/service/service.impl`；user 承担 BO/VO/Controller；infra 承担 P2 provider/listener，并只消费 P0-C 全局 `ModelProvider`、`WebSearchClient`，不重复声明 client/provider 契约。
- [ ] `question_generate/evidence_retrieve` 都满足 create/freeze/enqueue 原子性、复用、完整 lease、attempt 上限和真实 usage。
- [ ] 两个 Handler 的成功结果事务都先完成业务写入，再恰一次调用对应 `markSuccess(currentLease, TaskResultReferenceDTO.of(...))`，由 P0-C 在同一事务 settle；P2 零次直接 `settle/release`。
- [ ] 两个 Handler 都覆盖 `STALE_BRANCH_RESULT`：attempt 保持成功、业务零写入、`markSuccess` 零调用、P0-C release/零 settle、provider 不重试。
- [ ] `06` 在安全本机环境按完整迁移链执行并幂等复跑；所有 IT 使用 `LocalIntegrationEnvironment.requireFromEnvironment()`。
- [ ] 所有 JUnit 类有 `@Tag("dev")`，所有 fresh 测试报告测试数大于 0。
- [ ] 登录、工作区、方向、问答、额度、费率、六种任务状态、修订、补充和证据完整状态矩阵通过。
- [ ] 独立 reviewer 复算六类 manifest 及其 artifacts；writer 未代写 review。
- [ ] `p2-f3-handoff.json` 同 HEAD/同 payload 幂等回读，不同 HEAD/内容 fail-closed，P3 只在完整 F3 后 rebase。
