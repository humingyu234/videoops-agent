# 后端局部规则

本文件补充仓库根 `AGENTS.md`，只适用于 `ai-video-api`。

## 开始前

- 使用 JDK 21 和仓库 Maven Wrapper；按改动模块运行相关构建与测试，不默认构建无关模块。
- 从仓库根读取 `.agents/skills/ruoyi-plus-ai-coding/SKILL.md`，再读取 `docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md` 及本次变更涉及的契约章节。
- 新增标准 CRUD 前查 generator 模板；修改复杂模块前查同模块最相似实现、调用点和测试。

## 模块与分层

- `ai-video-user-api` 是用户端启动模块，`ruoyi-admin` 是管理端/运营端启动模块；共享业务沉入 `ruoyi-modules`，启动模块只做装配、配置和部署入口。
- 业务聚合保持 `domain`、平级 `dto`、`mapper`、`service`、`service.impl`；HTTP 模块另可有 `domain.bo`、`domain.vo`、`controller`。
- Service 使用 `I...Service` / `...ServiceImpl`。Controller 只做入口、校验、权限、日志、防重和响应包装；事务、状态、归属、额度、幂等与跨表编排进入 Service。
- 禁止新增 `application`、`port`、`adapter`、`command`、`model` 等平行业务层，也不得以 DDD、Clean Architecture 或 Hexagonal Architecture 替代 RuoYi 标准分层。
- AI 视频稳定跨模块 Service DTO 放在 `ai-video-core` 对应聚合的平级 `dto` 包；供应商原始对象和直接集成留在 `ai-video-infra` 的 `client` / `provider` 边界。

## 安全与任务

- 用户端和运营端 Controller、BO、VO、权限与审计入口分离；所有用户数据、文件、任务和额度操作校验账号归属或数据范围。
- 长耗时生成进入后端任务模型；外部调用不包在长事务中，回调/轮询结果经校验后持久化，未知提交结果不得盲目重发。
- 本机 MySQL、Redis、测试库和初始化 SQL 严格遵守根 `RULES.md`，目标不明确时不得执行写入、迁移、清理或补偿。

## 验证

运行受影响 Maven 模块的测试、构建或最小真实边界检查；两个启动应用涉及路由或安全变化时分别验证。明确记录未运行项，不用 Mock 或静态阅读替代数据库、Provider、OSS、回调或端到端证据。
