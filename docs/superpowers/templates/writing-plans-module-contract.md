# Writing Plans 模块契约模板

当用户要求基于模块规格使用 `writing-plans` 生成实现计划时，自动套用本模板，不需要用户复制提示词。

## 自动识别输入

- 如果用户提供规格文件路径，直接使用该路径。
- 如果用户只说“基于刚才的规格”，使用最近一次生成或讨论的规格文件。
- 如果找不到规格文件，只问一个澄清问题：“请给出要生成实现计划的规格文件路径。”
- 模块名优先从规格标题或用户消息推断；无法判断时再询问。

## 计划目标

计划必须能让前端、后端和联调并行推进，不能只写笼统任务。

## 任务维度

### 1. 契约与类型

- 更新 `docs/API_CONTRACT.md`。
- 更新 `docs/DOMAIN_MODEL.md` 或说明不需要更新。
- 定义前端 TypeScript DTO / VO / enum。
- 定义后端 Bo / Vo / Entity / 字典。

### 2. 后端任务

- RuoYi-Vue-Plus 分层文件清单：业务聚合使用 `domain`、与其平级的 `dto`、`mapper`、`service/I...Service`、`service.impl/...ServiceImpl`；端侧 HTTP 模块另使用 `domain.bo`、`domain.vo`、`controller`，共享核心模块无 HTTP 入口时明确说明省略三者。
- 计划中的 AI 视频业务专属稳定跨模块 Service DTO 必须落在 `ai-video-core` 对应业务聚合的平级 `dto` 包，使用 `*DTO` 命名，并验证没有迁入全局 `ruoyi-api`；供应商原始对象只留在 `ai-video-infra` 的直接集成边界。
- 明确验证业务包不存在以 `application`、`application.impl`、`port`、`adapter`、`command`、`model` 为名的平行分层；不得以 DDD（领域驱动设计）、Clean Architecture（整洁架构）或 Hexagonal Architecture（六边形架构）替代 RuoYi 的贫血 Entity 加 Service 编排。
- 如存在安全或框架例外，任务必须先给出 `docs/DOMAIN_MODEL.md` 与模块规格的例外记录、项目负责人确认和回归条件；未确认时不得写实现任务。
- Controller 权限标识、日志、防重复提交。
- Service 业务流程、`ownerId` 校验、状态流转。
- Mapper 查询、分页、排序白名单。
- 任务、额度、文件、通知相关处理。
- 单元测试或接口测试。

### 3. 前端任务

- 页面、路由、布局。
- Ant Design / ProComponents 组件选择。
- API service、adapter、mock。
- 页面状态：加载、空、失败、权限、提交中、成功、失败。
- 表单校验、按钮状态、错误提示。
- 任务轮询、额度不足、下载、重新生成等交互。

### 4. 联调与验证

- 前后端联调顺序。
- mock 替换真实接口的步骤。
- 需要运行的验证命令。
- 每个验收点如何验证。

### 5. 本机受控集成测试

- 后端计划必须把 MySQL 8（关系型数据库）和 Redis 7（缓存数据库）的真实集成测试写为本机原生服务，禁止写入 Docker、Docker Compose、Testcontainers、WSL、虚拟机、Podman 或其他容器化、虚拟化前提。
- 计划必须复用或建设 `LocalIntegrationEnvironment`（本机受控集成环境夹具），并在所有 `*IT`（集成测试）命令前使用 `local-integration-test`（本机集成测试）Maven（Java 项目构建工具）配置。
- 夹具默认复用用户端 `application-dev.yml` 的 `spring.datasource` 与 `spring.data.redis` 连接信息，并允许同名 `AI_VIDEO_IT_*` 环境变量覆盖；执行时固定切换到 MySQL 专用库 `ai_video_test`、Redis DB 15 和本次运行前缀，缺失或不安全时立即失败。
- 所有清理步骤只能清理 `ai_video_test` 与本次测试 Redis 前缀；禁止开发、预发、生产数据源和 `FLUSHALL`，测试日志不得输出密码。

### 6. AI 智能体协作与 Token 治理

- 计划开始前必须读取 docs/AI_AGENT_GOVERNANCE.md；每个可独立实施的任务必须按其要求写明风险、任务卡、并发、审查和验证安排。
- 本模板只接入治理规范，不复制或改写治理正文；信息不足时必须读取权威来源或澄清，不得用完整历史对话或无关大文档替代任务卡。

## 使用要求

- 仍然严格遵循 `writing-plans` skill 的计划格式、任务粒度、禁止占位符和自检要求。
- 每个任务必须包含精确文件路径、步骤、测试或验证命令。
- 不允许写 TODO、待补充、类似上面这种占位符。
- 涉及后端集成测试时，必须落实“本机受控集成测试”维度；生产部署策略不因该开发测试约定被改变。
