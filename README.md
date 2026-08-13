# VideoOps Agent

面向 Agent 赛道的 AI 视频交付智能体：用户用自然语言描述交付目标并提供形象、音色或素材，Agent 负责澄清约束、编排数字人口播生成链、观察异步任务、自动验收、有限返工，并在关键节点请求人工确认。

> 当前状态：已建立不含原 Git 历史的脱敏代码基线；Agent 控制面尚未实现。这里记录的是诚实的开发起点，不是已完成声明。

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

1. [docs/PROJECT.md](docs/PROJECT.md)：产品目标、保留/隐藏/延期范围与验收标准。
2. [docs/PLAN.md](docs/PLAN.md)：按纵切面拆分的开发任务。
3. [docs/BASELINE.md](docs/BASELINE.md)：来源提交、已知分支与安全边界。
4. [AGENTS.md](AGENTS.md) 与 [RULES.md](RULES.md)：AI 协作和工程硬规则。
5. [docs/FRONTEND_CODING_STANDARDS.md](docs/FRONTEND_CODING_STANDARDS.md) 与 [docs/BACKEND_CODING_STANDARDS.md](docs/BACKEND_CODING_STANDARDS.md)：进入前后端编码时的专项标准。

## 安全

- 不提交任何真实密钥、口令、Token、私有地址或证书。
- 本地配置通过环境变量注入；变量名示例见 `.env.example`。
- 原项目出现过真实形态凭据；原凭据应独立轮换。本仓库的脱敏不等于原凭据自动失效。
- 推送公开 GitHub 前必须重新执行密钥扫描，并人工检查扫描结果。

## 路线图

先跑通人工黄金链，再实现 Delivery Brief、最小编排器、可观察轨迹、自动评价与有限返工；最后才做视觉包装、更多供应商或开放式多智能体。完整任务见 [docs/PLAN.md](docs/PLAN.md)。
