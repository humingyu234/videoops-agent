# Brainstorming 需求契约模板

当用户提供一段需求、想法、PRD 片段或模块名，并要求使用 `brainstorming` 生成规格时，自动套用本模板，不需要用户复制提示词。

## 自动识别需求范围

- 如果用户提供的是一段需求，先提取：用户目标、涉及页面、核心操作、数据对象、异步任务、权限/额度/文件等边界。
- 根据需求自动归类到候选模块，例如“视频创作”“素材管理”“任务中心”“数字人与声音”“模板与草稿”“全局能力”。
- 如果需求跨多个模块，先指出拆分建议，并推荐本次规格聚焦的第一个模块或子流程。
- 如果用户消息中出现明确模块名，例如“视频创作”“素材管理”“任务中心”，直接使用该模块名。
- 如果用户只说“这个模块”“当前功能”，先从最近上下文推断需求范围和模块名。
- 如果无法判断需求范围，只问一个澄清问题：“这段需求主要要落在哪个页面或业务流程？”

## 项目背景

- 项目：AI 视频工作台。
- 前端：React + TypeScript + Ant Design + Ant Design Pro / ProComponents + Electron 内置 Web。
- 后端：RuoYi-Vue-Plus 6.x 二开。
- 范围：不做 MVP 裁剪，必须覆盖 PRD 中该模块的完整页面、状态、操作和异常场景。

## 规格必须覆盖

不要只写功能描述，必须同时覆盖前端、后端和协作契约。

前端必须包含：

- 页面入口、路由、布局和导航位置。
- 页面状态：加载、空、搜索无结果、接口失败、权限不足、提交中、成功、失败。
- 字段清单：展示字段、编辑字段、只读字段、必填字段、默认值。
- 交互规则：按钮可用性、删除确认、错误提示、重新生成、取消、下载、轮询等。
- Ant Design / ProComponents 组件建议。
- API adapter、mock 数据、TypeScript 类型。
- 额度不足、任务失败、权限不足、素材不可用等异常交互。

后端必须包含：

- RuoYi-Vue-Plus 分层：业务聚合使用 `domain` Entity、与其平级的 `dto`、`mapper`、`service` 的 `I...Service`、`service.impl` 的 `...ServiceImpl`；端侧 HTTP 模块另使用 `domain.bo` BO、`domain.vo` VO、`controller`，共享核心模块无 HTTP 入口时明确省略三者。
- AI 视频业务专属的稳定跨模块 Service 数据契约必须位于 `ai-video-core` 对应业务聚合的平级 `dto` 包并使用 `*DTO` 命名；不得放入 `domain`、`service` 或全局 `ruoyi-api`，供应商原始对象只属于 `ai-video-infra` 的直接集成边界。
- 明确声明不引入 `application`、`port`、`adapter`、`command`、`model` 等平行业务层，不采用 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 贫血 Entity 加 Service 编排。
- 若因安全或框架确需偏离 `BaseEntity`、目录或对象职责，列出原因、最小范围、替代控制、回归条件和项目负责人确认；没有确认不得进入计划。
- 权限标识：`${module}:${business}:${action}`。
- 用户账号归属：`ownerId` 规则和后端校验点。
- 数据模型草案，以及是否需要调整 `docs/DOMAIN_MODEL.md`。
- API 入参、出参、错误码。
- 任务创建、幂等、状态流转、回调或轮询。
- 额度校验、冻结、扣减、退回。
- 文件/素材引用、下载/预览权限。
- 字典、状态枚举、日志审计。

协作必须包含：

- 先读取并遵守 docs/AI_AGENT_GOVERNANCE.md，并按其要求给出每个候选工作任务的风险、任务卡、审查和验证信息；不得复制或枚举治理正文。
- 前后端并行开发切分。
- 哪些内容可以 mock 先行。
- 哪些内容必须等后端接口。
- 哪些契约变更必须先 review。
- 需要同步更新的公共文档：`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md`。

## 使用要求

- 仍然严格遵循 `brainstorming` skill：先澄清、再给方案、再形成规格，不直接进入实现。
- 不得用完整历史对话或整份无关规划替代任务卡；规格只引用权威来源，信息不足时要求澄清或读取原文。
- 规格完成后提示用户审查；用户批准后再进入 `writing-plans`。
