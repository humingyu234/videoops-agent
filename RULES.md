# AI 视频工作台项目硬规则

本文档只写项目级硬规则。具体接口、领域、异步任务、前端、后端和 AI 协作细则分别见 `docs/` 下的专项文档。

## 范围红线

- 当前版本只交付 `docs/PROJECT.md` 定义的“图生数字人口播交付 Agent”纵切面，不以复刻原工作台全部页面为目标。
- 参赛版实现完成后默认用户入口为 `/agent`；当前基线仍以 `/studio` 为首页。原七步 Studio、运营端、发现市场和其他非核心页面可以从参赛导航隐藏，但在工具依赖完成追踪前不得删除源码或破坏既有接口。
- 必须复用统一认证、账号归属、任务、素材、文件与数字人生成链。Agent 是这些能力上方的控制面，不得另建一套虚假的任务、资产或生成状态。
- 比赛演示中的每个“自主”声明都必须有运行轨迹或测试证据；规则脚本、演示供应商、静态 mock 和真实模型必须明确标注，禁止混称。
- 自动重试默认最多两次；超过预算、连续不改善、出现安全风险或需要主观取舍时必须停止并请求人工决策。
- 范围调整先更新 `docs/PROJECT.md` 和必要的 `docs/DECISIONS.md`；实现拆分和验收标准先更新 `docs/PLAN.md` 与对应任务卡；实际进度、证据和下一动作只更新 `docs/EXECUTION.md`，再进入编码。

## 技术边界

- 用户端桌面壳层：`ai-video-desktop`，Electron 必需独立薄包。
- 前端工作区：`ai-video-ui`，统一存放用户端 Web 与管理端/运营端 UI 包。
- 用户端 Web：`ai-video-ui/ai-video-webapp`，React + TypeScript + Ant Design + Ant Design Pro / ProComponents。
- 管理端/运营端 UI：`ai-video-ui/ai-video-platform-ui`，React + TypeScript + Ant Design / ProComponents。
- 后端 Maven 根工程：`ai-video-api`，基于 RuoYi-Vue-Plus 6.x 二开。
- 后端启动模块：`ai-video-api/ai-video-user-api` 面向用户端，`ai-video-api/ruoyi-admin` 面向管理端/运营端。
- Electron 只承载窗口、本地能力、更新、安全边界和内置 Web 加载，不承载业务状态、接口契约或领域模型。

## 架构边界

- 用户端 API 和管理端/运营端 API 可以单独部署，但共享领域能力必须沉淀在后端公共业务模块，不得复制两套核心逻辑。
- 用户端与管理端/运营端的 Controller、BO、VO、权限入口必须分离。
- 任务、素材、模板、文件、额度、AI 服务编排等共享规则只能有一套事实来源。
- 前端不得绕过后端直接决定任务终态、额度扣减、文件授权或数据归属。
- 后端不得把前端展示形态、Electron 本地行为写入核心领域模型。

## 契约优先

- 新增或修改接口、字段、状态、字典、任务规则前，必须先查公共契约。
- 接口规则以 `docs/API_CONTRACT.md` 为准。
- 领域建模规则以 `docs/DOMAIN_MODEL.md` 为准。
- 异步任务规则以 `docs/ASYNC_TASKS.md` 为准。
- Guide 负责“如何组织和完成开发”：前端以 `docs/FRONTEND_GUIDE.md` 为准，后端以 `docs/BACKEND_GUIDE.md` 为准。
- Coding Standards 负责“代码必须如何编写”：前端硬规则以 `docs/FRONTEND_CODING_STANDARDS.md` 为准，后端硬规则以 `docs/BACKEND_CODING_STANDARDS.md` 为准。
- API Contract 负责“前后端如何交换数据”，以 `docs/API_CONTRACT.md` 为准。
- Ant Design、Ant Design Pro / ProComponents 的组件 API、Token、语义结构、布局范式以官方文档、官方 AI 文档、CLI 或 MCP 查询结果为准。
- RuoYi-Vue-Plus 6.x 的分层、公共类、代码生成、分页、响应、权限、字典、文件、日志、缓存等规则以框架源码、官方文档和 RuoYi Plus AI Coding skill 为准。
- 不能在单个页面、单个 Controller 或单个模块中局部自造另一套契约。

## 框架优先级

- 前端实现必须优先使用 Ant Design、Ant Design Pro / ProComponents 和项目既有业务组件。
- 管理类页面优先使用 ProComponents 的列表、表格、筛选、分页、详情和表单范式。
- 后端实现必须优先复用 RuoYi-Vue-Plus 6.x 的响应、分页、权限、字典、文件、缓存、日志、Mapper、对象转换和代码生成能力。
- 涉及后端代码前，必须先读取 RuoYi Plus AI Coding skill；本地 skill 不存在时，必须说明并查官方路径。
- 框架能力已覆盖的内容，不得自行实现重复返回体、分页结构、权限体系、文件体系、字典体系或 Mapper 基类。

## RuoYi 业务对象与分层硬边界

- 后端业务模块必须采用 RuoYi-Vue-Plus 的贫血 Entity（实体）加 Service（业务服务）编排方式；不得在本项目引入 DDD（领域驱动设计）充血聚合、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）作为平行的业务分层。
- 业务聚合允许的标准业务包仅包括 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。共享核心模块没有 HTTP 入口时省略 `domain.bo`、`domain.vo`、`controller`；`dto` 只是数据契约包，不得演变为新的业务编排层。
- Service 接口必须使用 `I...Service` 命名，实现必须放入 `service.impl` 并使用 `...ServiceImpl` 命名。Entity 只承担持久化字段、表映射和简单内聚判断；事务、状态流转、权限／归属、幂等、额度和跨表编排只能由 Service 承担。
- 禁止新增或以 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 命名的业务层来替代 RuoYi 标准层。端侧 HTTP 请求使用 `domain.bo`，HTTP 响应使用 `domain.vo`；AI 视频业务专属的稳定跨模块 Service 数据契约统一放入 `ai-video-core` 对应业务聚合的平级 `dto` 包并使用 `*DTO` 命名，禁止迁入全局 `ruoyi-api`。外部供应商原始请求／响应只放在 `ai-video-infra` 的直接 `client`／`provider` 集成边界。
- `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 等辅助包只允许承载直接框架或外部集成功能，不能演变为第二套业务 Service 层。
- 对 `BaseEntity`、标准目录或对象职责的例外必须同时写入 `docs/DOMAIN_MODEL.md` 与模块规格，说明安全／框架原因、最小范围、替代控制和回归条件；未经项目负责人明确确认，禁止新增例外。

## 权限与数据归属

- 所有业务接口必须有认证、权限或明确的公开访问说明。
- 用户业务数据必须校验账号归属或等价数据范围。
- 管理端/运营端跨用户查询、修改、下载、预览、重试、补偿等操作必须有独立权限和审计记录。
- 前端不得传入并覆盖 `ownerId`、账号归属、额度结果或任务终态。
- 文件下载、预览、任务结果读取必须通过后端授权，不得直接拼接真实文件地址绕过校验。

## 任务与额度

- 长耗时 AI 生成不得在 HTTP 请求线程内同步完成。
- 生成类操作必须走后端任务模型，任务必须可在任务中心追踪。
- 创建任务前必须校验参数、权限、账号归属、额度和幂等。
- 任务状态、进度、失败原因、结果关联以后端持久化为准。
- 额度校验、扣减、退回、补偿和流水记录以后端为准。
- 外部 AI 服务回调或轮询结果不得直接成为最终业务状态，必须经后端校验后回写。

## 前端体验红线

- 所有页面必须处理加载、空数据、搜索无结果、接口失败、权限不足、操作中、操作成功和操作失败。
- 删除、覆盖、取消任务、重试任务、额度消耗类操作必须有明确确认或风险提示。
- 上传能力必须校验类型、大小、数量，并展示进度和失败原因。
- 页面不得散写接口路径、状态字符串、错误语义或权限判断。
- 管理端/运营端页面不得混入用户端 Electron Web 包，用户端页面不得混入管理端/运营端 UI 包。

## 后端实现红线

- Controller 只做请求入口、参数接收、权限、日志、防重和响应包装，不堆业务逻辑。
- BO 承载请求入参与校验，VO 承载前端展示响应，Entity 不直接暴露给前端。
- 状态流转、额度扣减、任务创建、输出写入必须有清晰事务边界。
- 外部服务调用不得包在长事务里。
- 不得绕过 RuoYi-Vue-Plus 的统一响应、分页、权限、字典、文件、日志和数据权限能力。
- 不得以“DDD”“整洁架构”“六边形架构”或“便于解耦”为由新增与 RuoYi 标准目录平行的业务层。

## 本机开发与测试环境

- 后端开发、调试、自动化测试和集成测试一律直接连接开发机本机安装的 MySQL 8（关系型数据库）与 Redis 7（缓存数据库）；禁止使用 Docker、Docker Compose、Testcontainers、WSL、虚拟机、Podman 或其他容器化、虚拟化运行环境。
- 集成测试只能使用本机专用 MySQL 数据库 `ai_video_test` 与 Redis 专用逻辑库；两端 `application-dev.yml` 只保留环境变量引用和非敏感结构配置，实际用户名、口令和密钥只能来自进程环境或不受 Git 跟踪的本地配置，测试日志不得输出凭据。
- 集成测试夹具必须在连接前校验目标仅为 `localhost`、`127.0.0.1` 或 `::1`，MySQL 数据库必须为 `ai_video_test`，Redis 必须使用隔离逻辑库与本次运行前缀；缺少配置或校验不通过时立即失败，绝不回退到开发、预发或生产数据源，也不得执行 `FLUSHALL`。
- 开发库基线数据初始化的唯一入口是 `docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql`。必须使用 `ai-video-user-api/src/main/resources/application-dev.yml` 中最终生效的 `spring.datasource.dynamic.datasource.master` 连接执行，禁止改用 `codex-local-stack.yml`、Docker 配置或其他旁路连接；SQL 在数据库客户端当前选中的数据库中执行，不硬编码或校验库名。具体数据范围与幂等规则见 `docs/DEVELOPMENT_DATABASE_INITIALIZATION.md`。
- 本规则仅约束开发和测试环境，不改变生产部署的基础设施选型；两个启动应用仍必须分别执行路由暴露与安全边界 Smoke Test（冒烟测试）。

## AI 协作规则

- docs/AI_AGENT_GOVERNANCE.md 是项目 AI 协作、风险分级、质量门禁、并发、审查和 Token 治理的唯一权威来源，适用于所有计划、阶段、工具和参与者；不得以 Token 预算、进度或计划编号削减质量门禁。
- 必须执行该治理规范的强制收口规则：一轮完整审查后只允许一次定向复核；同一问题最多两次自动返工，同一无变化失败最多重试两次，普通轮询连续两次无变化即停止；未经用户批准不得扩大范围或派生新任务。
- 用户要求立即完成或停止扩展时，必须停止新增探索、智能体和审查，只关闭已确认的必须修复项并运行一次必要验证；无法通过时报告未完成，不得无限继续。
- 需求、想法、PRD 片段或模块规格必须通过 superpowers `brainstorming` 梳理。
- 实现计划必须通过 superpowers `writing-plans` 生成。
- 使用 `brainstorming` / `writing-plans` 时必须自动读取 `docs/superpowers/templates/` 下的项目模板。
- 不要求用户复制模板；如果需求范围、模块名或规格路径无法判断，只问一个澄清问题。
- 模块规格和实现计划由 superpowers 按自身规则保存，不在 `RULES.md` 固定文件路径。
- 施工任务开始前必须核对真实项目路径、HEAD、工作区、`docs/EXECUTION.md` 和当前 `docs/tasks/` 任务卡；未记录改动不得覆盖，未来任务卡不得在未核对当前源码时直接执行。

## 验收规则

- 验收以 PRD、superpowers 生成的模块规格、实现计划和公共契约为准。
- 实现完成前必须运行对应验证命令，并记录无法验证的原因。
- 前端变更至少验证类型检查、lint 或对应测试；无法运行时必须说明原因。
- 后端变更至少验证 Maven 相关模块构建、测试或最小可行校验；无法运行时必须说明原因。
- 不能把“未验证”描述成“已完成”。
- 没有与当前源码状态匹配的测试、运行或真实边界证据，不得把任务标记为 `DONE`；相关源码、配置、环境或验收标准变化后必须标记 `NEEDS_REVALIDATION`。
