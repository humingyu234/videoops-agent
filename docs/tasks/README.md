# 阶段任务卡

任务卡冻结一个阶段的目标、边界和验收，不替代产品定义、模块规格或详细实现计划。

## 权威关系

- `docs/PROJECT.md`：产品目标和范围。
- `docs/DECISIONS.md`：已确认取舍及原因。
- `docs/PLAN.md`：T0～T7 路线和粗状态。
- `docs/EXECUTION.md`：当前现场的唯一实时状态。
- 本目录：阶段边界、反向场景、验证方法和最终验收记录。
- `docs/superpowers/specs/`：经 brainstorming 确认的设计。
- `docs/superpowers/plans/`：当前阶段依据最新源码生成的详细施工步骤。

三类进度文档不一致时停止编码，先修正文档；不要猜测哪个更新。未来卡为 `DRAFT`，进入阶段时先核对最新源码、前置证据和风险，再生成/更新详细计划并冻结为 `ACTIVE`。

## 卡片列表

- [T0 安全基线](T0-baseline.md) — `ACCEPTED`
- [T1 人工黄金链](T1-golden-path.md) — `ACTIVE`，尚未实际开工
- [T2 交付契约与运行状态](T2-contract-state.md) — `DRAFT`
- [T3 类型化工具适配](T3-tool-adapters.md) — `DRAFT`
- [T4 编排、澄清与恢复](T4-orchestrator.md) — `DRAFT`
- [T5 三层质量评价](T5-evaluation.md) — `DRAFT`
- [T6 局部返工与人工批准](T6-repair-approval.md) — `DRAFT`
- [T7 Agent 页面与参赛交付](T7-agent-delivery.md) — `DRAFT`

## 证据规则

证据编号使用 `Tn-Exx`。每条证据必须说明：支持的验收项、`REAL/DEMO/MOCK` 环境、源码状态、具体命令/任务/产物、结果。日志和截图不得包含密钥、Token、私有 URL、用户隐私或未授权素材。

完成后把最终源码、时间、证据、未验证项和剩余风险写回阶段卡。相关源码、配置、环境或标准变化后，旧证据失效，阶段转为 `NEEDS_REVALIDATION`。
