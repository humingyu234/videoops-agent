# 文档与 Skill 路由

本文只在无法从根 `AGENTS.md` 判断专项资料时读取，不是冷启动必读清单。

## 最小施工链

1. Codex 自动读取根 `AGENTS.md`。
2. 会修改仓库、开发环境或施工进度时，读取 `docs/EXECUTION.md`。
3. 实际执行施工步骤时，只继续读取 `docs/EXECUTION.md` 指向的当前详细计划。
4. `docs/PLAN.md` 只在调整阶段路线、范围或依赖时读取。
5. 纯解释、只读审计和局部检索只加载问题直接涉及的源码与文档。

`docs/EXECUTION.md` 是当前阶段、风险、阻塞、证据和唯一下一动作的实时来源。入口、README 和其他规范不复制这些动态内容。

## Skill 路由

项目 Skill 统一从仓库根 `.agents/skills/` 读取：

| 任务 | Skill | 后续资料 |
| --- | --- | --- |
| 创建或大改需求规格 | `.agents/skills/brainstorming/SKILL.md` | 与本次范围直接相关的产品、决策和契约章节 |
| 创建或更新实现计划 | `.agents/skills/writing-plans/SKILL.md` | 已确认规格、当前源码和 `docs/EXECUTION.md` |
| RuoYi 后端 | `.agents/skills/ruoyi-plus-ai-coding/SKILL.md` | `ai-video-api/AGENTS.md`、后端指南/规范及相关契约章节 |
| 管理端 CRUD 前端 | `.agents/skills/frontend-crud-coding/SKILL.md` | 前端指南/规范及相关 API 章节 |
| Ant Design React 前端 | `.agents/skills/antd/SKILL.md` | 最近的嵌套 `AGENTS.md`、前端规范和官方组件资料 |

规格和计划 Skill 只在任务确实要求对应产物时使用，不作为文档小改、解释、诊断或既有计划执行的固定前置流程。路径缺失时明确报告，不得凭记忆假装已读取。

`ai-video-ui/ai-video-webapp/.claude/skills/antd` 是给 Claude Code 的兼容镜像，不是第六个 Codex Skill；规范校验会比较它与根 `antd` Skill 的 SHA-256，防止双份内容漂移。该目录中的 `pro-upgrade` 只在用户明确要求升级 Ant Design Pro 时使用，不进入日常施工路由。

## 按任务选择文档

- 产品目标、范围或最终完成标准：`docs/PROJECT.md`；形成新取舍时同步 `docs/DECISIONS.md`。
- 阶段路线、范围或依赖：`docs/PLAN.md`；实时进度仍只写 `docs/EXECUTION.md`。
- 当前施工、环境操作、阻塞、证据或下一动作：`docs/EXECUTION.md` 及其指向的唯一详细计划。
- 系统分层、Electron、安全边界或关键数据流：`docs/ARCHITECTURE.md` 的相关章节。
- API 路径、响应、鉴权、上传下载或错误码：`docs/API_CONTRACT.md` 的相关章节。
- 业务对象、字段、状态、字典、归属或表设计：`docs/DOMAIN_MODEL.md` 的相关章节。
- 任务创建、幂等、回调、轮询、重试、终态或额度：`docs/ASYNC_TASKS.md` 的相关章节。
- 用户端 React 页面：`ai-video-ui/ai-video-webapp/AGENTS.md`、`docs/FRONTEND_GUIDE.md` 和 `docs/FRONTEND_CODING_STANDARDS.md` 的相关章节。
- 管理端/运营端 UI：先看 `ai-video-ui/ai-video-platform-ui` 既有结构，再读前端指南/规范的相关章节。
- 后端 API 或共享模块：`ai-video-api/AGENTS.md`、`docs/BACKEND_GUIDE.md` 和 `docs/BACKEND_CODING_STANDARDS.md` 的相关章节。
- 开发数据库初始化：`docs/DEVELOPMENT_DATABASE_INITIALIZATION.md` 与其指定的唯一 SQL。
- AI 协作、风险分级、审查或证据治理：`docs/AI_AGENT_GOVERNANCE.md`；AI 资料与 skill 路由见 `docs/AI_CODING_RULES.md`。
- 导入来源、旧分支、脱敏或公开发布：`docs/BASELINE.md`。

`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md` 和编码规范体积较大。先检索标题、资源名、接口前缀、状态或业务对象，只读取本次变更涉及的章节；跨契约变更再检查相邻章节，不要整本预载。

发现页 RunningHub 单执行任务才读取 `docs/contracts/discovery-runninghub/`，并同步核对 API、领域、异步任务和架构中的对应章节。创作时间轴任务才读取 `docs/contracts/creation-timeline/` 及对应章节。

## 历史和阶段资料

- `docs/superpowers/specs/` 与 `docs/superpowers/plans/` 默认视为历史资料，不主动读取。唯一施工例外是 `docs/EXECUTION.md` 当前明确指向的详细计划。
- 旧阶段任务卡已退出活动施工链；只有追溯旧验收边界时才从 Git 历史读取。
- `ai-video.md`、`ai-video-pages.md` 和被新文档取代的旧规格/计划只用于追溯，不能覆盖当前源码、公共契约或 `docs/EXECUTION.md`。

## 工程边界速查

```text
ai-video-desktop/                 Electron 薄壳、本地能力与安全策略
ai-video-ui/ai-video-webapp/      用户端 React Web
ai-video-ui/ai-video-platform-ui/ 管理端/运营端 React UI
ai-video-api/ai-video-user-api/   用户端 API 启动模块
ai-video-api/ruoyi-admin/         管理端/运营端 API 启动模块
ai-video-api/ruoyi-modules/       共享业务与外部集成模块
ai-video-worker/                  媒体与任务工作进程
```

Electron 目录已经存在；它只能承载窗口、preload、本地能力、更新、安全策略和 Web 加载。前端不得决定任务终态、额度、文件授权或账号归属，用户端与运营端入口不得混包。

## 何时更新

- 范围或最终验收变化：更新 `docs/PROJECT.md`，必要时记录 `docs/DECISIONS.md`。
- 路线、范围或依赖变化：更新 `docs/PLAN.md`；实时状态、证据、阻塞和下一动作只更新 `docs/EXECUTION.md`。
- API、领域或异步契约变化：更新对应专项文档，不以 Guide 或编码规范替代。
- 工程组织变化：更新前后端 Guide；编码硬规则变化：更新对应 Coding Standards。
- AI 风险、审查或证据规则变化：更新 `docs/AI_AGENT_GOVERNANCE.md`，并检查入口与本路由是否仍为薄引用。
