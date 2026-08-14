# VideoOps Agent AI 协作入口

本文档是所有 AI 编程 Agent 和团队成员进入本项目时必须先读的入口规则，也是本项目 AI 协作的唯一主入口。

## 自动入口约定

- 支持 `AGENTS.md` 的 AI 工具进入本项目后，必须自动读取本文档，不要求用户在每次对话中手动说明“按 `AGENTS.md` 执行”。
- 不同 AI 工具如果不自动识别 `AGENTS.md`，只允许增加很薄的转发入口，例如 `CLAUDE.md`、`GEMINI.md`、`.cursor/rules/*.mdc`、`.github/copilot-instructions.md`。
- 转发入口只写“先读取并遵守 `AGENTS.md`”，不得复制本文档规则，避免多处规则不同步。
- 本文档负责决定后续按任务类型读取哪些规则文档；不要在每次对话中一次性加载 `docs/` 下所有 Markdown。
- 如果当前工具无法自动读取任何项目入口文件，才需要用户在首次对话中手动提示读取 `AGENTS.md`。

## 工作原则

- 当前交付目标是世界人工智能大会 Agent 赛道的可验证参赛纵切面，范围以 `docs/PROJECT.md`、`docs/DECISIONS.md` 和 `docs/PLAN.md` 为准；当前施工现场只以 `docs/EXECUTION.md` 为准。原 `ai-video.md` 与 `ai-video-pages.md` 仅作为产品能力库与历史需求参考。
- 参赛版实现完成后，用户默认入口改为 `/agent`。当前基线仍从 `/` 进入 `/studio`；原七步 Studio 后续只作为内部调试、人工接管和能力验证入口，不进入参赛默认导航，在工具依赖完成追踪前禁止物理删除。
- 先复用已存在的认证、资产、音色、数字人任务、时间轴和渲染链路，再新增 Agent 控制面；不得重写已有生成供应商链路来制造“Agent 感”。
- 每个 Agent 动作必须可追踪，自动返工必须有次数、时间和成本上限，高风险或主观决策必须进入人工确认。
- 新增功能、页面、接口、字段、状态前，先查项目公共契约，不允许局部自造。
- 前端以 Ant Design、Ant Design Pro / ProComponents、Electron 内置 Web 容器为约束。
- 后端以 RuoYi-Vue-Plus 6.x 二开为约束，必须采用其贫血 Entity（实体）加 Service（业务服务）编排风格，优先复用框架已有能力；不得自行改用 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）的业务分层。
- 所有生成任务必须能进入任务中心追踪，所有管理页必须具备加载、空、失败、权限不足、分页等状态。

## 必读顺序

1. `RULES.md`
2. `docs/PROJECT.md`、`docs/DECISIONS.md`、`docs/PLAN.md`、`docs/BASELINE.md`
3. 做施工任务时继续读 `docs/EXECUTION.md`、其中指定的当前 `docs/tasks/Tn-*.md` 和当前详细施工计划；纯解释或只读问答不改变施工状态
4. `docs/DOCUMENT_MAP.md`
5. 接口、领域或异步任务相关变更继续读 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`
6. 前端任务继续读 `docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md` 和 `docs/API_CONTRACT.md`
7. 后端任务继续读 `ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md` 和 `docs/API_CONTRACT.md`
8. 所有 AI 协作任务继续读 `docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`

文档规范变更后必须运行 `scripts/validate-development-standards.ps1`；入口文档只负责路由，不复制编码手册正文。

## 跨任务施工协议

- 开工先确认绝对项目路径、分支、HEAD 和 `git status`，再对照 `docs/EXECUTION.md`。存在未记录改动时先查来源，禁止覆盖。
- 一次只推进当前任务卡。未来 `DRAFT` 卡进入施工前，必须依据当时源码和前置证据生成/更新 writing-plans 详细计划并冻结为 `ACTIVE`。
- `docs/EXECUTION.md` 是当前进度、阻塞和下一动作的唯一来源；`docs/PLAN.md` 只在阶段状态转换时同步，任务卡保存冻结边界和最终验收记录。
- 每次通过子步骤、状态变化、出现阻塞或准备交接时更新 `docs/EXECUTION.md`；不写逐分钟流水账。
- 收工必须记录实际验证的 `PASS`、`FAIL`、`NOT_RUN`、对应源码状态和下一条准确动作。没有当前源码匹配的运行/测试证据，不得标记 `DONE`。
- 相关源码、配置、环境或验收标准变化后，旧证据必须转为 `NEEDS_REVALIDATION`；Mock、静态阅读、AI 总结或只打开页面不能证明真实 Provider/端到端能力。

## Superpowers 项目模板

- 使用任何 AI 协作、派发、审查或并发工作流前，必须先遵守 docs/AI_AGENT_GOVERNANCE.md；它是质量门禁、风险分级、任务卡、并发、审查和 Token 治理的唯一权威来源。
- 使用 `brainstorming` 为需求、想法、PRD 片段或模块生成规格时，必须自动读取 `docs/superpowers/templates/brainstorming-module-contract.md`，并从用户消息中识别需求范围、候选模块或子流程。
- 使用 `writing-plans` 为模块生成实现计划时，必须自动读取 `docs/superpowers/templates/writing-plans-module-contract.md`，并将规格文件路径和模块名作为模板输入。
- 不要求用户复制模板；如果需求范围、模块名或规格路径无法从上下文判断，只问一个澄清问题。

## 团队协作机制

- 所有 AI 工作必须遵守 `docs/AI_AGENT_GOVERNANCE.md` 的“禁止无限流程与强制收口”：禁止递归全量审查、无限返工、无变化重试、无期限等待和未经批准的范围扩张；用户要求收口时立即停止新增工作，只完成已确认阻塞和一次必要验证。
- 目标明确且不改变运行时代码、业务契约、安全、数据或资金规则的文档／配置小改走轻量路径，不新建规格、实现计划、子智能体或独立审查任务。
- 三名全栈开发不做长期固定模块归属，按阶段、模块和上下文动态分配任务。
- 每个模块或较大需求开始前临时指定一名契约 owner。
- 契约 owner 负责使用 `brainstorming` 梳理规格、组织另外两名开发 review，并在确认后使用 `writing-plans` 生成实现计划。
- Review 至少覆盖三个视角：前端页面/状态/字段/交互，后端 RuoYi 分层/权限/任务/额度/账号归属，联调顺序/mock/验收风险。
- 实现阶段按计划并行分配任务，不受契约 owner 角色限制。
- 跨模块字段、状态、接口或任务规则变化，必须先同步公共契约，再进入实现。

## 前端 AI 规则

- 不凭训练记忆写 Ant Design API。涉及组件 API、Token、Demo、语义结构时，优先查 Ant Design 官方 AI 文档、`llms-full-cn.txt`、`design.md` 或 `@ant-design/cli`。
- 管理类页面优先采用 ProComponents 思路：`ProTable`、`ProForm`、`ProDescriptions`、`ProList`。
- 生产型工作台页面可使用 Ant Design 基础组件组合，但必须复用统一上传、任务、额度、错误、空态契约。
- Electron 只承载壳层、窗口、本地能力和内置 Web 打开，不承载业务状态和接口契约。

## 后端 AI 规则

- 后端任务必须先读取 RuoYi Plus AI Coding skill，再修改代码。
  - 官方位置：`https://gitee.com/dromara/RuoYi-Vue-Plus/tree/6.X/.codex/skills/ruoyi-plus-ai-coding`
  - 本项目本地路径：`ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`
  - 如果本地路径不存在，必须明确说明未找到本地 skill，并先查官方路径或等待团队安装，不允许仅凭记忆实现。
- 遵循 RuoYi Plus AI Coding 规则：先读 generator 模板和相似模块，再修改代码。
- 【硬约束】后端业务对象与目录必须服从 `RULES.md` 的“RuoYi 业务对象与分层硬边界”及 `docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/DOMAIN_MODEL.md`；不得以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）创建平行业务层。
- 任何安全或框架例外必须先在 `docs/DOMAIN_MODEL.md` 和模块规格中登记并取得项目负责人明确确认；入口不重复编码细则。
- 不替换 RuoYi-Vue-Plus 已有公共能力，例如 `R`、`PageQuery`、`PageResult`、`BaseMapperPlus`、`MapstructUtils`、字典、权限、日志、文件、缓存能力。
- 权限标识按 `${module}:${business}:${action}` 命名。

## 禁止事项

- 禁止把同一业务状态写成多套枚举。
- 禁止前端页面直接散落接口路径、状态字符串和错误码。
- 禁止后端 Controller 堆业务逻辑。
- 禁止绕过权限、用户账号归属校验、文件访问校验、额度校验。
- 禁止为赶进度省略 PRD 中要求的空态、失败态、加载态、权限态、删除确认。

<!-- antd-cli setup start -->
## Ant Design CLI Skill

Use the shared Ant Design skill at `.agents/skills/antd/SKILL.md` before working on Ant Design code in this repository.

The skill teaches agents when and how to call `@ant-design/cli` commands such as `antd info`, `antd doc`, `antd demo`, `antd token`, `antd semantic`, and `antd changelog`.

<!-- antd-cli setup end -->
