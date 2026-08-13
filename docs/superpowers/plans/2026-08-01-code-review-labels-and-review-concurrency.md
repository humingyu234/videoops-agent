# Code Review 标签与风险感知并发治理实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在不修改 `.codex/skills/**` 的前提下，由项目治理主文档统一 Code Review 标签，并为红色任务建立受控的标准纯审查并发模式。

**架构：** `docs/AI_AGENT_GOVERNANCE.md` 继续作为唯一权威来源，负责收窄上游 Review skill 在本项目中的最终输出和并发边界。实施或混合阶段保持原风险并发上限；只有不实施项目变更的独立纯审查阶段可按条件使用 1 个主审加最多 2 个专项审查者。

**技术栈：** Markdown、PowerShell、Git、项目开发规范校验脚本

**规格：** `docs/superpowers/specs/2026-08-01-code-review-labels-and-review-concurrency-design.md`

---

## 文件结构

**修改：**

- `docs/AI_AGENT_GOVERNANCE.md`：统一最终审查标签，定义标准纯审查模式及与例外审批、Token 阈值的关系。

**只读检查，不修改：**

- `.codex/skills/chinese-code-review/SKILL.md`：确认上游 skill 保持原样。
- `.codex/skills/receiving-code-review/SKILL.md`：确认上游 skill 保持原样。
- `AGENTS.md`：确认仍薄引用治理主文档。
- `RULES.md`：确认仍薄引用治理主文档。
- `docs/AI_CODING_RULES.md`：确认仍薄引用治理主文档。
- `docs/DOCUMENT_MAP.md`：确认权威文档边界未漂移。
- `docs/superpowers/templates/brainstorming-module-contract.md`：确认没有重复治理正文。
- `docs/superpowers/templates/writing-plans-module-contract.md`：确认没有重复治理正文。

## 工作任务卡

- **单一目标：** 修改项目治理主文档，不修改任何上游 skill。
- **不做范围：** 不修改业务代码、P0-A 计划、接口/领域契约、测试环境或 `.codex/skills/**`。
- **风险：** 红色高风险；变更影响所有后续审查及身份、权限、兼容性等红色任务的审查组织。
- **权威来源：** 上述规格、`RULES.md`、`docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`。
- **验收：** 最终标签统一；风险不降级；纯审查模式边界明确；上游 skill 零差异；规范校验通过。
- **并发：** 实施阶段最多 2 个智能体；1 个实施者完成规则变更，1 个独立审查者只读复核，不派生智能体。
- **输出：** 完成项、风险、验证证据、未验证项和非精确 Token 代理指标。

### 任务 1：更新治理主文档

**文件：**

- 修改：`docs/AI_AGENT_GOVERNANCE.md:50-65`
- 修改：`docs/AI_AGENT_GOVERNANCE.md:86-101`
- 修改：`docs/AI_AGENT_GOVERNANCE.md:109-114`

- [ ] **步骤 1：验证现状与保护上游 skill**

运行：

```powershell
$governance = 'docs/AI_AGENT_GOVERNANCE.md'
$legacyLabel = Select-String -LiteralPath $governance -Pattern '审查结论只能标记为“阻塞”“建议”或“信息”'
if (-not $legacyLabel) { throw '现行三类审查标签基线不存在' }

$pureReviewMode = Select-String -LiteralPath $governance -Pattern '标准纯审查模式'
if ($pureReviewMode) { throw '标准纯审查模式已经存在，必须先重新核对计划' }

git diff --quiet -- .codex/skills
if ($LASTEXITCODE -ne 0) { throw '上游 skill 已存在工作区改动，停止实施' }
git diff --cached --quiet -- .codex/skills
if ($LASTEXITCODE -ne 0) { throw '上游 skill 已存在暂存区改动，停止实施' }
```

预期：旧标签基线存在；标准纯审查模式尚不存在；`.codex/skills/**` 没有工作区差异。

- [ ] **步骤 2：统一最终审查标签**

将治理主文档第 6 节的审查分类改为：

```markdown
- 最终审查结论只能标记为 `[必须修复]`、`[建议修改]` 或 `[仅供参考]`：
  - `[必须修复]` 用于安全漏洞、数据破坏或丢失风险、确定的逻辑/契约/兼容性错误，以及未通过的强制质量门禁；解决前不得合并或关闭任务。
  - `[建议修改]` 用于不阻塞合并的性能、可维护性、校验、可观测性或工程质量改进；不得伪装成 `[必须修复]` 扩大范围。
  - `[仅供参考]` 用于不要求本次采取行动的替代方案、命名/风格信息和背景说明。
- `[问题]` 只用于审查过程中的澄清；形成最终结论前必须转化为上述三类之一或撤销，不得进入最终问题清单。
```

保留“没有变化时禁止完整复审”和“交付必须列出实际验证”的既有规则。

- [ ] **步骤 3：区分实施并发与标准纯审查模式**

将第 4 节表格的并发列明确为“实施或混合阶段同一任务的并发智能体”，保持红色最多 2、黄色最多 2、绿色最多 3 的现有上限。

在表格规则后增加：

```markdown
### 4.1 红色任务的标准纯审查模式

红色任务在不实施项目变更的纯审查阶段，最多可以同时使用 3 个智能体：1 个独立主审和最多 2 个独立专项审查者。主审不得是同一工作任务的原实施者；专项范围必须在任务卡中预先声明且互不重叠。

该模式必须同时满足：

- 所有参与者只能读取源文件、执行构建/测试和诊断，不修改受版本控制内容或非测试外部状态。
- 构建产物以及通过项目安全夹具写入并清理的专用测试数据不视为实施变更。
- 任务卡必须说明为什么一个专项审查者不足；只需要一个专项视角时仍最多使用 2 个智能体。
- 专项审查者不得互相依赖或派生智能体；主审必须重新核对每项 `[必须修复]` 的代码、契约或测试证据并去重。
- 原实施者使用 `receiving-code-review` 验证反馈不构成独立审查，也不会自动适用该标准模式。
- Token 使用达到第 7 节的 70% 阈值后不得新增审查者；达到 85% 时必须形成负责人检查点；必要安全审查和验证不得停止。

符合上述条件属于治理内置标准模式，不需要逐次申请例外。超过 3 个智能体、允许实施写入或偏离其他条件时，必须按第 9 节取得负责人明确批准并记录。
```

将原“4.1 红色任务的专项验证”顺延为“4.2 红色任务的专项验证”，正文保持不变。

- [ ] **步骤 4：收紧例外条款**

在第 9 节增加：

```markdown
- 符合第 4.1 节全部条件的标准纯审查模式不属于例外；超出人数、只读边界、独立性或任务卡条件时，才适用本节审批与记录要求。
```

不得删除原有的负责人批准、原因、影响范围、剩余风险和额外验证记录要求。

- [ ] **步骤 5：运行针对性规则断言**

运行：

```powershell
$governance = 'docs/AI_AGENT_GOVERNANCE.md'
$requiredPatterns = @(
  '\[必须修复\]',
  '\[建议修改\]',
  '\[仅供参考\]',
  '\[问题\].*过程',
  '标准纯审查模式',
  '1 个独立主审',
  '最多 2 个独立专项审查者',
  '70%.*不得新增审查者',
  '85%.*检查点',
  '不属于例外'
)
foreach ($pattern in $requiredPatterns) {
  if (-not (Select-String -LiteralPath $governance -Pattern $pattern)) {
    throw "治理规则缺少：$pattern"
  }
}
if (Select-String -LiteralPath $governance -Pattern '审查结论只能标记为“阻塞”“建议”或“信息”') {
  throw '旧最终审查标签仍然存在'
}
git diff --quiet -- .codex/skills
if ($LASTEXITCODE -ne 0) { throw '实施意外修改了上游 skill' }
git diff --cached --quiet -- .codex/skills
if ($LASTEXITCODE -ne 0) { throw '实施意外暂存了上游 skill' }
```

预期：所有新规则均命中，旧标签不存在，上游 skill 无差异。

- [ ] **步骤 6：运行完整文档验证**

运行：

```powershell
& powershell -NoProfile -ExecutionPolicy Bypass -File './scripts/validate-development-standards.ps1'
if ($LASTEXITCODE -ne 0) { throw '开发规范校验失败' }

git diff --check
if ($LASTEXITCODE -ne 0) { throw '差异格式校验失败' }

rg -n '审查结论只能|标准纯审查模式|\[问题\]' docs/AI_AGENT_GOVERNANCE.md

$thinReferences = @(
  'AGENTS.md',
  'RULES.md',
  'docs/AI_CODING_RULES.md',
  'docs/DOCUMENT_MAP.md',
  'docs/superpowers/templates/brainstorming-module-contract.md',
  'docs/superpowers/templates/writing-plans-module-contract.md'
)
foreach ($path in $thinReferences) {
  if (-not (Select-String -LiteralPath $path -Pattern 'AI_AGENT_GOVERNANCE.md')) {
    throw "缺少治理主文档薄引用：$path"
  }
}
```

预期：输出 `DEVELOPMENT_STANDARDS_OK`；`git diff --check` 无错误；扫描结果只展示新的最终标签和标准纯审查边界。

- [ ] **步骤 7：执行独立只读审查**

独立审查者只读取规格、治理主文档差异和验证输出，按以下清单出具结论：

```text
[必须修复] 是否仍能借纯审查名义实施写入或绕过独立性
[必须修复] 是否弱化红色任务风险、专项验证或 Token 停损规则
[必须修复] 是否修改了 .codex/skills/**
[建议修改] 是否存在重复规则或术语歧义
[仅供参考] 已保持不变的既有门禁
```

预期：没有 `[必须修复]`；如有 `[建议修改]`，由实施者验证后决定是否在本次最小范围内修正。

- [ ] **步骤 8：提交治理变更**

仅暂存治理主文档：

```powershell
git add -- docs/AI_AGENT_GOVERNANCE.md
$staged = @(git diff --cached --name-only)
if ($staged.Count -ne 1 -or $staged[0] -ne 'docs/AI_AGENT_GOVERNANCE.md') {
  throw "暂存范围不正确：$($staged -join ', ')"
}
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw '暂存差异格式校验失败' }
git commit -m 'docs: 统一代码审查标签与并发规则'
```

预期：提交只包含 `docs/AI_AGENT_GOVERNANCE.md`，当前 P0-A 工作区改动和 `.codex/skills/**` 均不进入提交。

## 最终交付记录

交付时必须列出：

- 修改的项目治理条款。
- `.codex/skills/**` 零差异证据。
- 开发规范校验、规则断言和 `git diff --check` 结果。
- 独立审查结果及未验证项。
- 活动智能体数、引用范围、工具调用、审查轮次和测试结果，并标记为“非精确 Token 账单”。
