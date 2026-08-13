# AI 编程规则

## 总则

AI 生成代码前必须先读取入口规则和公共契约。不得凭模型训练记忆直接编写 Ant Design 或 RuoYi-Vue-Plus 代码。

- 前端修改前必须读取 `docs/FRONTEND_CODING_STANDARDS.md`；后端修改前必须读取 `ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md` 和 `docs/BACKEND_CODING_STANDARDS.md`。
- 文档规范变更后必须运行 `scripts/validate-development-standards.ps1`。
- 当前已有检查与目标质量门禁必须区分；未落地的质量门禁不能描述为已启用。

## AI 智能体协作与 Token 治理

- 所有 AI 协作、智能体派发、独立审查或并发实施前，必须先读取并遵守 docs/AI_AGENT_GOVERNANCE.md；该文档是风险分级、质量门禁、任务卡、并发、审查和 Token 治理的唯一权威来源。
- 禁止把质量优先解释为无限流程：完整审查、定向复核、自动返工、无变化重试、等待轮询和范围扩张必须服从治理主文档的明确上限与强制收口条件。
- 用户要求收口后，不得继续发现新问题、增加智能体或重新全量审查；只处理已确认的必须修复项并执行一次必要验证。
- 工作说明、模块规格和实现计划必须按治理规范提供所需信息；本文只负责接入，不复制或改写治理正文。

## Ant Design AI 资料

前端相关任务优先参考：

- Ant Design For Agents: `https://ant.design/docs/react/for-agents-cn`
- Ant Design design.md: `https://ant.design/design.md`
- Ant Design LLMs: `https://ant.design/llms.txt`、`https://ant.design/llms-full-cn.txt`
- 单组件文档：`https://ant.design/components/<name>-cn.md`
- 单组件语义文档：`https://ant.design/components/<name-cn>/semantic.md`

如果可用，使用 `@ant-design/cli`：

```bash
antd info Button --lang zh
antd doc Table --lang zh
antd demo Select basic
antd token DatePicker
antd semantic Table
antd lint ./src
antd doctor
```

如果可配置 MCP，使用官方 MCP：

```json
{
  "mcpServers": {
    "antd": {
      "command": "npx",
      "args": ["-y", "@ant-design/cli", "mcp"]
    }
  }
}
```

## RuoYi Plus AI Coding

后端相关任务遵循 RuoYi-Vue-Plus 6.x 官方 `ruoyi-plus-ai-coding` skill 思路：

- 本项目优先读取 `ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`。

- 新增标准 CRUD 前先读取 generator 模板。
- 修改复杂模块前先读取同模块相似实现。
- 【硬约束】标准业务包只能保持 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。无 HTTP 入口的共享核心模块省略 BO、VO 和 Controller。Service 接口使用 `I...Service`，实现放入 `service.impl`；`dto` 只承载数据契约。
- 【硬约束】不得以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 的贫血 Entity（实体）加 Service（业务服务）编排；禁止用 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 作为平行的业务分层。
- `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 只可承担直接技术职责。端侧请求归 BO、响应归 VO；AI 视频业务专属的稳定跨模块 Service 契约归 `ai-video-core` 对应聚合平级 `dto` 包中的 `*DTO`，不得放入全局 `ruoyi-api`；新增例外必须先写入 `docs/DOMAIN_MODEL.md` 与模块规格，并取得项目负责人明确确认。
- 优先复用 `BaseMapperPlus`、`PageQuery`、`PageResult`、`R`、`MapstructUtils`。
- 公共模块修改必须保持 API 兼容。

## AI 修改前检查

- 是否已有同类模块、同类页面或同类接口可参考。
- 是否需要同步更新领域模型、状态机、API 契约、验收规则。
- 是否涉及权限、用户账号归属、额度、文件访问或任务状态。
- 是否需要新增字典或枚举。
- 是否正在新增或大改模块规格；如果是，必须先通过 superpowers `brainstorming` 生成规格，再进入实现计划。
- 是否正在修改验收项；如果是，必须确认来源来自 PRD、模块规格、API 契约、异步任务契约或安全规则。
- 后端目录是否符合 RuoYi 标准分层；如果既有模块不符合，是否已先获得整改计划与项目负责人确认，而非继续在非标准层扩展。

## AI 修改后检查

- 前端是否统一处理加载、空、失败、权限、分页。
- 后端是否保持分层，Controller 是否只做参数接收和响应包装。
- 后端是否不存在以 `application`、`port` 或同类目录替代 `service`、`service.impl`、BO、VO 的情况；技术辅助包是否未承载业务编排。
- 接口路径、权限标识、状态枚举是否与契约一致。
- 生成任务是否进入任务中心。
- 上传/下载是否有权限和安全校验。

## Superpowers 项目模板

新增或大改模块时，不要直接让 AI “实现某功能”。必须先用 `brainstorming` 生成模块规格，规格确认后再用 `writing-plans` 生成实现计划。

模板文件：

- `docs/superpowers/templates/brainstorming-module-contract.md`
- `docs/superpowers/templates/writing-plans-module-contract.md`

使用规则：

- 当用户要求 `brainstorming` 需求规格、模块规格或 PRD 拆解时，AI 必须自动读取 brainstorming 模板，不要求用户复制模板。
- 当用户要求 `writing-plans` 实现计划时，AI 必须自动读取 writing-plans 模板，不要求用户复制模板。
- 两种模板使用前都必须先读取 docs/AI_AGENT_GOVERNANCE.md，并按其要求产出信息；模板只能接入规范，不得复制整套治理正文。
- 需求范围、候选模块和子流程从用户消息或上下文自动推断；无法判断时只问一个澄清问题。
- 规格文件路径从用户消息或最近生成的规格自动推断；无法判断时只问一个澄清问题。
