# VideoOps Agent

面向 Agent 赛道的 AI 视频交付智能体：用户用自然语言描述交付目标并提供形象、音色或素材，Agent 负责澄清约束、编排数字人口播生成链、观察异步任务、自动验收、有限返工，并在关键节点请求人工确认。

> 本仓库是脱敏后的开发基线，不代表 Agent 控制面或真实端到端链路已经完成。当前阶段、风险、阻塞、证据和下一动作只见 [docs/EXECUTION.md](docs/EXECUTION.md)。

## 参赛纵切面

```text
目标/素材
  -> 需求澄清与 Delivery Brief
  -> Agent 制订执行计划
  -> 调用现有形象/音色/数字人/时间轴/渲染能力
  -> 观察异步任务
  -> 自动检查并有限返工
  -> 人工确认
  -> 可下载作品 + 完整执行轨迹
```

- 默认参赛入口：`/agent`（待实现）。
- 原七步 Studio：保留源码，首期从默认导航隐藏，作为内部调试与人工接管界面。
- 核心视频链：复用现有认证、资产、音色、数字人任务、时间轴和渲染服务，不重复建设。
- 自动返工：必须有次数、时间与成本上限，不能无限生成。

## 当前代码基线

本仓库由公司项目缓存的 `origin/main@6ef783d3cc2ef83b420a52080e40b1eff39203e1` 导出为无历史快照，再进行配置脱敏。创建当日无法连接 Gitee，因此该提交是“最近一次已获取基线”，不是对远端当天最新状态的保证。详见 [docs/BASELINE.md](docs/BASELINE.md)。

用户此前的“AI 对话后创建形象并保存”分支没有直接并入本基线。它的异步任务、私有资产、人工确认和保存链设计会作为后续迁移参考；当前图像供应商是本地 Demo，不能作为真实生成能力宣传。

## 工程结构

```text
ai-video-ui/       React 用户端与运营端
ai-video-api/      RuoYi-Vue-Plus 多模块后端
ai-video-worker/   Python 媒体/任务工作进程
ai-video-desktop/  Electron 薄壳
docs/              产品范围、架构、契约和实施计划
scripts/           本地验证与安全脚本
```

## 开始前先读

Codex 自动从 [AGENTS.md](AGENTS.md) 进入项目。会修改仓库、环境或进度时继续读取 [docs/EXECUTION.md](docs/EXECUTION.md)；实际执行时只读其中指向的当前详细计划。不要默认加载历史规格、计划或全部契约。

人类成员和专项任务按需读取：

1. [RULES.md](RULES.md)：项目工程、安全和数据硬边界。
2. [docs/PROJECT.md](docs/PROJECT.md) 与 [docs/DECISIONS.md](docs/DECISIONS.md)：产品范围、完成标准和已确认取舍。
3. [docs/PLAN.md](docs/PLAN.md)：仅用于调整 T0～T7 路线、范围或依赖。
4. [docs/DOCUMENT_MAP.md](docs/DOCUMENT_MAP.md)：按任务选择专项文档和 skill。
5. [docs/BASELINE.md](docs/BASELINE.md)：仅用于来源追溯、分支迁移或公开发布安全核对。
6. 前后端编码时读取对应指南、编码规范及本次变更涉及的契约章节。

## 安全

- 不提交任何真实密钥、口令、Token、私有地址或证书。
- 本地配置通过环境变量注入；变量名示例见 `.env.example`。
- 原项目出现过真实形态凭据；原凭据应独立轮换。本仓库的脱敏不等于原凭据自动失效。
- 推送公开 GitHub 前必须重新执行密钥扫描，并人工检查扫描结果。

## 路线图

先跑通人工黄金链，再实现交付契约/可恢复状态、类型化工具、受约束编排、自动评价与有限返工；最后完成 `/agent` 页面和公开交付。完整路线见 [docs/PLAN.md](docs/PLAN.md)，当前准确动作见 [docs/EXECUTION.md](docs/EXECUTION.md)。
