# AI 编码资料路由

本文用于代码生成或修改时选择项目资料和领域 Skill，不是冷启动必读，也不定义通用开发流程。

## 开始前

- 先遵守根 `AGENTS.md`；会修改仓库、环境或进度时读取 `docs/EXECUTION.md`，实际施工再读取其中指向的当前详细计划。
- 先查目标模块的真实代码、测试和相邻实现，再读取本次变更涉及的指南、编码规范和契约章节。
- 不凭训练记忆编写 Ant Design 或 RuoYi-Vue-Plus API；Skill 路径缺失时明确报告，不假装已经读取。
- 文档规范变更后运行 `scripts/validate-development-standards.ps1`；检查失败时报告当前失败，不绕过或改写为通过。

## Skill 路由

| 任务 | 项目 Skill |
| --- | --- |
| Ant Design React | `.agents/skills/antd/SKILL.md` |
| 管理端 CRUD 前端 | `.agents/skills/frontend-crud-coding/SKILL.md` |
| RuoYi 后端 | `.agents/skills/ruoyi-plus-ai-coding/SKILL.md` |
| 明确要求创建或大改需求规格 | `.agents/skills/brainstorming/SKILL.md` |
| 明确要求创建或更新实现计划 | `.agents/skills/writing-plans/SKILL.md` |

规格和计划 Skill 不作为解释、诊断、小型文档修改或执行既有计划的固定前置。项目不建立 VideoOps 总 Skill。

## Ant Design 与 React

- 用户端任务读取最近的嵌套 `AGENTS.md`、`docs/FRONTEND_GUIDE.md` 和 `docs/FRONTEND_CODING_STANDARDS.md` 的相关章节。
- 管理端 CRUD 优先复用 `ai-video-ui/ai-video-platform-ui` 的既有结构和 frontend CRUD Skill。
- 写组件前用 Ant Design Skill 或官方资料核对当前版本的 API、Token、语义结构和 Demo；修改后对变更文件运行适用的 CLI lint。
- 官方入口：`https://ant.design/docs/react/for-agents-cn`、`https://ant.design/llms-full-cn.txt`、`https://ant.design/design.md`。
- 页面必须使用统一 API 层和状态语义，处理加载、空、失败、权限不足、操作中和危险操作确认；不得在页面散写接口路径、错误码或业务终态。

## RuoYi 后端

- 先读 RuoYi Skill，再读 `ai-video-api/AGENTS.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md` 的相关章节。
- 新增标准 CRUD 前查看 generator 模板；修改复杂模块前查看同模块最相似实现及测试。
- 标准业务包保持 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可有 `domain.bo`、`domain.vo`、`controller`。
- Service 使用 `I...Service` / `...ServiceImpl`；Entity 是贫血持久化对象，事务、状态、归属、幂等和跨表编排进入 Service。
- 禁止用 `application`、`port`、`adapter`、`command`、`model` 等平行业务层替代 RuoYi 标准结构，也不得以 DDD、Clean Architecture 或 Hexagonal Architecture 绕开。
- 稳定的 AI 视频跨模块 Service 契约放在 `ai-video-core` 对应聚合的平级 `dto` 包；供应商原始对象留在 `ai-video-infra` 的直接 `client` / `provider` 边界。
- 优先复用 RuoYi 的响应、分页、Mapper、映射、权限、字典、文件、缓存和日志能力，公共模块修改保持兼容。

## 契约读取

- 改 API 路径、响应、鉴权、上传下载或错误码：定位 `docs/API_CONTRACT.md` 的相关资源章节。
- 改字段、状态、字典、账号归属或表设计：定位 `docs/DOMAIN_MODEL.md` 的相关对象章节。
- 改任务创建、回调、轮询、重试、终态、幂等或额度：定位 `docs/ASYNC_TASKS.md` 的相关任务章节。
- 大型契约先按标题、接口前缀、对象名或状态检索；只读本次边界及必要相邻章节，不整本预载。

## 修改前后检查

修改前确认复用点、权限/归属/额度/文件/任务风险、公共契约影响和可观察验收信号。修改后检查前后端字段与状态一致、Controller 无业务堆积、生成任务可追踪、文件访问已授权，并运行与变更匹配的测试、构建或真实边界证明。

命中身份、文件、额度、Provider、异步任务、外部副作用或公共契约风险时，再读取 `docs/AI_AGENT_GOVERNANCE.md`；普通局部编码不预载治理全文。
