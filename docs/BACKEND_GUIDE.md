# 后端指南

## 技术基座与启动模块

`ai-video-api` 基于 RuoYi-Vue-Plus 6.x 二开，是后端 Maven 根工程。它包含两个可单独部署的启动模块：

- `ai-video-user-api`：用户端 API，新增 Maven module，定位类似 RuoYi 的 `ruoyi-admin` 启动模块。
- `ruoyi-admin`：管理端/运营端 API。

新增 AI 视频业务优先沉淀到共享业务模块；用户端和管理端/运营端启动模块只保留接口装配、配置和部署入口。不要把跨端业务逻辑堆入启动模块。

## 后端任务开始前

后端任务必须先阅读 RuoYi Plus AI Coding skill，再查阅本项目文档和代码。推荐本地 skill 路径为：

`.agents/skills/ruoyi-plus-ai-coding/SKILL.md`

建议的阅读顺序：

1. RuoYi Plus AI Coding skill。
2. [后端编码规范](BACKEND_CODING_STANDARDS.md)。
3. 先检索并只读与任务相关的 [API 契约](API_CONTRACT.md)、[领域模型](DOMAIN_MODEL.md) 和 [异步任务契约](ASYNC_TASKS.md) 章节，不整本预载。
4. generator 模板、同类已实现模块及其测试。

本地 skill 缺失时，明确说明缺失并查阅 RuoYi-Vue-Plus 6.x 官方文档或等待团队安装；不得凭记忆替代框架约束。

## RuoYi 标准分层硬约束

后端业务对象和目录必须按 RuoYi-Vue-Plus 6.x 代码生成器与同类模块实现，不能因为模块复杂而另起一套架构风格。

- 每个业务聚合只能使用 `domain`、与其平级的 `dto`、`mapper`、`service`、`service.impl`；端侧 HTTP 模块另可使用 `domain.bo`、`domain.vo`、`controller`。共享核心模块无 HTTP 入口时省略 BO、VO 和 Controller；`dto` 只定义数据契约，不承担业务编排。
- Service 接口必须命名为 `I...Service`，实现类必须命名为 `...ServiceImpl` 并位于 `service.impl`。Entity 是贫血持久化对象，业务编排、事务、状态、归属、幂等与跨 Mapper 写入全部进入 Service。
- 禁止在业务模块把 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 作为额外业务层或替代标准层；不允许以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）名义绕开此规则。
- 端侧请求对象归 `domain.bo`，响应对象归 `domain.vo`；AI 视频业务专属的稳定跨模块 Service 契约归 `ai-video-core` 对应聚合的平级 `dto` 包，类型使用 `*DTO` 命名，不得放入全局 `ruoyi-api`。外部 AI、OSS、队列、回调和通知的供应商原始对象与技术实现放在 `ai-video-infra` 的直接 `client`／`provider` 集成边界，不创建“端口层”。
- `config`、`security`、`event`、`listener`、`constant`、`enums`、`properties`、`utils`、`client`、`provider` 仅可作为直接框架或外部集成辅助包，不能承担平行的业务编排职责。任何例外先更新 `docs/DOMAIN_MODEL.md` 与模块规格，并取得项目负责人明确确认。

## 模块职责与推荐目录

后端总工程建议保持以下职责边界：

```text
ai-video-api/
  ai-video-user-api/     # 用户端启动、配置与接口装配
  ruoyi-admin/           # 管理端/运营端启动、配置与接口装配
  ruoyi-api/             # 框架与全局公共 API，不承载 ai-video 业务 DTO
  ruoyi-common/
  ruoyi-extend/
  ruoyi-modules/
    ai-video-core/       # 共享业务聚合与领域能力
    ai-video-infra/      # 外部 AI、OSS、回调、通知等集成
    ai-video-user/       # 用户端接口适配
    ai-video-platform/   # 管理端/运营端接口适配
```

- `ai-video-core`：共享 Entity、状态规则、RuoYi Service、Mapper，以及各业务聚合平级 `dto` 包中的稳定跨模块 DTO。
- `ai-video-infra`：外部 AI 服务、OSS、队列、回调、通知等集成能力。
- `ai-video-user`：用户端接口适配、用户端 BO/VO 与权限入口。
- `ai-video-platform`：管理端/运营端接口适配、管理端 BO/VO 与权限入口。
- `ai-video-user-api` 与 `ruoyi-admin`：启动、配置、路由装配和部署入口。

业务模块按聚合而非技术层散落组织。当前推荐聚合包括：`dashboard`、`creation`、`template`、`draft`、`digitalhuman`、`voice`、`asset`、`task`、`quota`、`notice`。

## generator 与同类模块查找流程

新增或扩展后端模块前，按以下流程执行：

1. 在 RuoYi 代码生成器模板中确认框架默认文件、依赖和目录布局。
2. 在 `ruoyi-modules` 中检索同类资源、权限、文件或任务模块。
3. 选择与目标最接近的模块，沿用其包结构、命名、异常模型、测试方式和配置位置。
4. 确认共享能力能否复用，再决定新增业务模块、适配层或基础设施集成。
5. 先同步公共契约，再实现代码与测试。

## 新模块开发流程

### 新增业务模块

1. 明确模块归属：核心业务、外部集成、用户端适配或平台端适配。
2. 在对应 Maven module 中建立业务聚合目录，并按 generator 与同类模块的布局展开。
3. 将用户端、平台端共用业务下沉到 `ai-video-core`；只在各端模块保留入口差异。
4. 补齐模块装配、依赖声明、调用入口及与公共契约一致的测试。
5. 在编码前核对目标目录；若既有目录不符合上述硬约束，先完成经确认的整改计划，不得在非标准层继续扩展功能。

### 扩展复杂模块

1. 先确认领域模型、状态边界、权限边界和异步处理边界。
2. 涉及跨模块字段、状态、接口或任务规则时，先更新公共契约并评审影响面。
3. 外部 AI、文件、回调和通知能力集中在 `ai-video-infra` 或既有框架扩展点，不在启动模块散落实现。
4. 只在明确需要时引入新依赖；先确认 RuoYi 已有能力和项目既有依赖不能满足需求。

### 复用基础能力

优先从 RuoYi-Vue-Plus 既有公共能力、项目现有模块和基础设施中寻找复用点。统一响应、分页、映射、权限、字典、日志、文件、缓存等具体使用约束，以[后端编码规范](BACKEND_CODING_STANDARDS.md)为准。

## 文档职责边界

本指南定义工程组织、模块边界和实现流程；不重复维护编码细则或线上协议。

- [后端编码规范](BACKEND_CODING_STANDARDS.md)：分层、对象命名、查询、权限、校验、事务、缓存、文件、测试与其他编码规则。
- [API 契约](API_CONTRACT.md)：接口路径、统一响应、鉴权、请求与响应格式、错误处理和 SSE 约束。
- [领域模型](DOMAIN_MODEL.md)：业务实体、字段语义、归属、状态和生命周期。
- [异步任务契约](ASYNC_TASKS.md)：任务创建、进度、回调、重试、幂等和额度处理。

任何实现前先按上述文档确认边界；公共契约发生变化时，先完成契约同步，再开始跨端实现。
