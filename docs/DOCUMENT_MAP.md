# 文档地图

本文档是 `docs/` 的入口地图，说明项目技术栈、包边界、阅读顺序和文档边界。具体规则以各专项文档为准。

## 项目技术栈

用户端：

- 桌面壳层：Electron，必需独立薄包。
- Web 前端：React + TypeScript。
- UI 体系：Ant Design、Ant Design Pro / ProComponents。
- 前端工程：Ant Design Pro / Umi Max 结构。

后端 API：

- Maven 根工程：`ai-video-api`，基于 RuoYi-Vue-Plus 6.x 二开。
- 启动模块：`ruoyi-admin`、`ai-video-user-api`，可单独部署。
- ORM 与分页：MyBatis-Plus、RuoYi-Vue-Plus `PageQuery` / `PageResult`。
- 认证权限：复用 RuoYi-Vue-Plus / Sa-Token / 权限注解体系。
- 文件能力：复用 RuoYi-Vue-Plus OSS / 文件能力。
- 异步任务：长耗时 AI 生成统一通过后端任务模型承载。

管理端 / 运营端：

- UI：React + TypeScript + Ant Design / ProComponents。
- 前端工作区：`ai-video-ui`，统一存放用户端 Web 与管理端/运营端 UI 包。
- 管理端/运营端前端工程：`ai-video-ui/ai-video-platform-ui`，与用户端 Web 分包管理。

AI 协作：

- 协作、并发、审查与 Token 治理：docs/AI_AGENT_GOVERNANCE.md。
- 需求与规格：superpowers `brainstorming`。
- 实现计划：superpowers `writing-plans`。
- 后端编码：优先参考 RuoYi Plus AI Coding skill。
- 前端组件：优先参考 Ant Design 官方文档、AI 文档、CLI 或 MCP。

## 包边界

```text
ai-video-desktop/       # 用户端 Electron 壳层，必需包，待创建
ai-video-ui/            # 前端工作区
  ai-video-webapp/      # 用户端 Ant Design Pro Web
  ai-video-platform-ui/ # 管理端/运营端前端页面
ai-video-api/           # RuoYi-Vue-Plus 后端 Maven 根工程
  ruoyi-admin/          # 管理端/运营端 API 启动模块，可单独部署
  ai-video-user-api/    # 用户端 API 启动模块，可单独部署
docs/                   # 项目公共规则、契约和指南
```

- `ai-video-desktop` 只负责窗口、preload bridge、本地能力、更新和加载 Web；目录创建前，不要假定已有 Electron 代码可修改。
- `ai-video-ui` 只做前端包分组，不承载业务状态、接口契约或运行时代码。
- `ai-video-ui/ai-video-webapp` 负责用户端路由、页面、组件、状态和后端 API 调用。
- `ai-video-api/ai-video-user-api` 负责用户端接口，可单独部署。
- `ai-video-api/ruoyi-admin` 负责管理端/运营端接口，可单独部署。
- `ai-video-api` 下应沉淀共享领域模块，统一任务、素材、模板、文件、额度和外部 AI 服务编排等后端能力。
- `ai-video-ui/ai-video-platform-ui` 负责管理端/运营端前端页面，不承载用户端 Electron Web 页面。
- 公共规则和跨端契约放在 `docs/`，模块级规格和实现计划由 superpowers 按流程生成。

## 先读什么

- AI Agent：先读 `AGENTS.md`、`docs/PROJECT.md`、`docs/PLAN.md` 和 `docs/BASELINE.md`，再按任务类型读取本文档列出的专项文档。
- 人类开发：先读 `README.md`、`docs/PROJECT.md`、`docs/PLAN.md`、`RULES.md` 和 `docs/ARCHITECTURE.md`，再读对应前端、后端或契约文档。
- 进入具体模块开发前：先通过 superpowers 生成或确认模块规格和实现计划。

## 按任务选择文档

- 改 AI 协作、风险分级、质量门禁、并发、审查或 Token 治理：先读 docs/AI_AGENT_GOVERNANCE.md，再读 docs/AI_CODING_RULES.md、docs/superpowers/templates/。
- 做前端页面：`docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md`、`docs/API_CONTRACT.md`。
- 做 Electron 能力：`docs/FRONTEND_GUIDE.md` 的 Electron 章节、`docs/ARCHITECTURE.md`。
- 做用户端 API：`ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`。
- 做管理端/运营端 API：`ai-video-api/.codex/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`。
- 做管理端/运营端 UI：先参考 `ai-video-ui/ai-video-platform-ui` 既有结构，再对照 `docs/FRONTEND_GUIDE.md` 与 `docs/FRONTEND_CODING_STANDARDS.md` 的 Ant Design / ProComponents 规则。
- 做异步生成任务：`docs/ASYNC_TASKS.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`。
- 改字段、状态、字典或表设计：`docs/DOMAIN_MODEL.md`、`docs/API_CONTRACT.md`。
- 改 AI 协作流程、brainstorming 或 writing-plans 模板规则：先读 docs/AI_AGENT_GOVERNANCE.md；涉及风险、质量门禁、并发、审查或 Token 语义时必须先更新治理主文档，再更新 docs/AI_CODING_RULES.md 或 docs/superpowers/templates/ 的薄接入。
- 改前端代码风格：`docs/FRONTEND_CODING_STANDARDS.md`。
- 改后端代码风格：`docs/BACKEND_CODING_STANDARDS.md`。
- 改后端模块目录、业务对象职责或分层方式：必须同时阅读 `docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/DOMAIN_MODEL.md` 和本地 RuoYi Plus AI Coding skill；RuoYi 标准目录是硬约束，不能引入 DDD、整洁架构或六边形架构的平行业务层。
- 初始化或校验本机开发数据库基线数据：`docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`。

## 文档边界

- `docs/PROJECT.md`：参赛产品目标、用户主链、范围和总体完成标准。
- `docs/PLAN.md`：当前阶段任务、非目标、验收信号和检查项。
- `docs/BASELINE.md`：导入来源、分支迁移状态、安全处理和可追溯限制。

### 发现页 RunningHub 单执行公共契约（2026-08-11）

发现页单执行变更必须同时阅读并同步 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md` 与 `docs/contracts/discovery-runninghub/`。其中 JSON 夹具及其 `WorkflowContractFixtureTest` 约束 `workflow-form-1`、禁止用户 wire 字段、任务/阶段矩阵和稳定错误码；运营端唯一执行配置的架构边界见 `docs/ARCHITECTURE.md`。

- docs/AI_AGENT_GOVERNANCE.md：AI 智能体协作、风险分级、质量门禁、任务卡、并发、审查、Token 预算与例外治理。
- `docs/ARCHITECTURE.md`：系统分层、职责边界、状态归属和关键数据流。
- `docs/API_CONTRACT.md`：接口规则，不维护完整业务 API 清单。
- `docs/ASYNC_TASKS.md`：异步任务、幂等、回调、轮询和额度处理规则。
- `docs/DOMAIN_MODEL.md`：领域建模规则，不维护完整表结构或状态机全集。
- `docs/FRONTEND_GUIDE.md`：前端工程、包、页面与模块组织，以及 Electron 集成流程；不承载编码硬规则。
- `docs/BACKEND_GUIDE.md`：后端工程、包、模块组织与开发流程；不承载编码硬规则。
- `docs/FRONTEND_CODING_STANDARDS.md`：前端语言、框架、组件、接口、性能和测试的编码硬规则。
- `docs/BACKEND_CODING_STANDARDS.md`：后端语言、分层、数据访问、安全、异步和测试的编码硬规则。
- `docs/AI_CODING_RULES.md`：AI 编程、superpowers 模板和外部规则来源。
- `docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`：开发数据库初始化入口、连接来源、种子数据、幂等与排除范围。

## 何时更新文档

- 改 AI 协作、风险分级、质量门禁、并发、审查或 Token 治理：更新 docs/AI_AGENT_GOVERNANCE.md，并同步检查 AGENTS.md、RULES.md、docs/AI_CODING_RULES.md、docs/DOCUMENT_MAP.md 和 superpowers 模板的薄引用。
- 改公共接口格式、分页、上传下载或 API 适配规则：更新 `docs/API_CONTRACT.md`。
- 改领域对象、字段含义、状态、字典或数据归属规则：更新 `docs/DOMAIN_MODEL.md`。
- 改异步任务、幂等、回调、进度、失败或额度规则：更新 `docs/ASYNC_TASKS.md`。
- 改前端工程、包、页面或模块组织，以及 Electron 集成流程：更新 `docs/FRONTEND_GUIDE.md`。
- 改后端工程、包、模块组织或开发流程：更新 `docs/BACKEND_GUIDE.md`。
- 改前端语言、框架、组件使用、接口消费、性能、测试等硬编码规范：更新 `docs/FRONTEND_CODING_STANDARDS.md`。
- 改后端语言、分层、数据访问、安全、事务、异步、测试等硬编码规范：更新 `docs/BACKEND_CODING_STANDARDS.md`。
- 改后端业务对象设计或目录分层：同步更新 `RULES.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`、`docs/DOMAIN_MODEL.md`、`docs/ARCHITECTURE.md`、`docs/AI_CODING_RULES.md` 及 superpowers 模板；未完成同步前不得实施代码。
- API、领域和异步任务契约分别仍更新 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`，不得以 Guide 或编码规范替代。
- 改 AI 协作、brainstorming / writing-plans 模板规则：先检查 docs/AI_AGENT_GOVERNANCE.md；涉及风险、质量门禁、并发、审查或 Token 语义时先更新治理主文档，再更新 docs/AI_CODING_RULES.md 或 docs/superpowers/templates/ 的薄接入。
