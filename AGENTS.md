# VideoOps Agent 项目入口

本文件是 Codex 的唯一自动入口；专项文档、历史规格和计划均按需读取。

## 冷启动

- 纯解释或只读审计只读取问题所需资料，不加载整套 `docs/`。
- 会修改仓库、环境或进度时，先确认路径、分支、HEAD、`git status`，再读 `docs/EXECUTION.md`；禁止覆盖用户改动。
- 实际执行当前施工步骤：只继续读取 `docs/EXECUTION.md` 指向的当前详细计划；不要顺带读取同目录其他历史规格、计划或任务卡。
- `docs/PLAN.md` 只在调整路线、范围或依赖时读取。
- 当前阶段、风险、阻塞、证据和唯一下一动作只以 `docs/EXECUTION.md` 为准；入口文件不得复制实时状态。

## 项目不变量

- 当前只交付“图生数字人口播交付 Agent”参赛纵切面；最终入口为 `/agent`，当前 `/studio` 和七步流程作为真实链、人工接管与调试入口保留，不得提前物理删除。
- Agent 必须复用现有认证、账号归属、素材、音色、数字人任务、时间轴、渲染、文件和额度能力，不得另造一套虚假任务、资产或生成状态。
- 自主执行、模型、Provider 和完成声明必须有当前源码与运行证据；明确区分 `REAL`、`DEMO`、`MOCK`。
- 自动重试和返工必须有次数、时间与成本上限；涉及付费调用、安全风险、不可逆操作或主观取舍时进入人工确认。
- Electron 仅承载壳层与本地安全能力；后端遵循 RuoYi-Vue-Plus 6.x 的贫血 Entity + Service，不引入 DDD/Clean/Hexagonal 平行业务层。

## 高风险边界

- 认证、权限、账号归属、文件访问、额度、任务终态和审计以服务端事实为准；前端不得提交并覆盖这些结果。
- 长耗时生成必须进入后端任务模型。Provider 回调或轮询结果须经后端校验后持久化，不能直接成为业务终态。
- 外部结果未知时先对账或转人工，禁止盲目重提；删除、覆盖、取消、重试、额度和批量操作必须有确认或风险提示。
- 不提交或输出真实密钥、口令、Token、签名 URL、私有地址、证书、用户隐私或未授权素材。
- 本机数据库、Redis、初始化和集成测试遵守 `RULES.md`，不得用容器、旁路连接或模糊目标操作数据。

## 按任务读取

- 产品范围、完成标准或路线决策：`docs/PROJECT.md`、`docs/DECISIONS.md`；仅调整路线时再读 `docs/PLAN.md`。
- 当前施工、环境操作或进度更新：`docs/EXECUTION.md`；实际施工再读其中指向的唯一详细计划。
- API、字段、状态、归属或异步任务：先定位并只读 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md` 的相关章节。
- 前端：最近的嵌套 `AGENTS.md`、`docs/FRONTEND_CODING_STANDARDS.md`；Ant Design 读 `.agents/skills/antd/SKILL.md`，管理端 CRUD 读 `.agents/skills/frontend-crud-coding/SKILL.md`。
- 后端：`ai-video-api/AGENTS.md`、`.agents/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_CODING_STANDARDS.md` 及相关契约章节。
- AI 协作、风险分级、审查或证据治理：`docs/AI_AGENT_GOVERNANCE.md`；AI 编码资料路由见 `docs/AI_CODING_RULES.md`。
- 仅在明确创建规格或计划时读取 `.agents/skills/brainstorming/SKILL.md` 或 `.agents/skills/writing-plans/SKILL.md`。
- 不确定专项文档归属时才读取 `docs/DOCUMENT_MAP.md`。

## 证据新鲜度

- 结论对应当前 HEAD 或 dirty diff；源码、配置、环境或标准变化后，旧证据标记 `NEEDS_REVALIDATION`。
- Mock、静态阅读、AI 总结或只打开页面不能单独证明真实 Provider、外部边界或端到端链路。
- 收工检查 diff，运行匹配风险的原生检查；文档规范变更运行 `scripts/validate-development-standards.ps1`。记录 `PASS`、`FAIL`、`NOT_RUN` 与剩余风险，未验证不得称完成。
