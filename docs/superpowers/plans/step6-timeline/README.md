# 创作第 6 步完整前后端时间轴实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 把用户端七步创作流程的第 6 步从静态演示改造成可恢复、可编辑、可版本化、可提交服务端重合成并由统一任务中心追踪的完整时间轴编辑器，同时闭合第 5 步来源和第 7 步成品预览下载。

**架构：** 当前登录用户拥有稳定创作项目；关系表保存归属、修订、版本、引用和任务事实，`timeline-1` JSON 保存轨道与元素；浏览器负责交互预览，Java Service 负责归属、事务和任务编排，媒体基础设施通过 ffprobe、FFmpeg 与 ASS 生成最终视频。全部新增业务对象继续采用 RuoYi-Vue-Plus 的贫血 Entity、Mapper、Service、Controller 分层，不建立物理外键，也不增加租户或工作区归属。

**技术栈：** Java 21、Spring Boot 4.1.0、RuoYi-Vue-Plus 6.0.0-BETA、MyBatis-Plus、MySQL 8、Redis 7、JUnit 5、Mockito、React 19、Umi Max 4、Ant Design 6、React Query 5、Vitest、TypeScript 7、ffprobe、FFmpeg、ASS。

---

## 拆分计划入口

本计划按三台设备、三个 Codex 账号拆分。编号用于文件排序，不是版本号或串行优先级。执行顺序是：A 先完成 00；公共契约合入主分支并发布 C0_SHA 后，A、B、C 并行执行 10、20、30；最后由 A 执行 90。

| 编号 | 计划 | 负责人 | 开始条件 |
| --- | --- | --- | --- |
| 00 | [公共契约基线](00-contract-baseline.md) | A | 立即开始 |
| 10 | [后端实现](10-backend-a.md) | A | 00 合入主分支并发布 C0_SHA |
| 20 | [前端实现](20-frontend-b.md) | B | 00 合入主分支并发布 C0_SHA |
| 30 | [媒体与 AI](30-media-ai-c.md) | C | 00 合入主分支并发布 C0_SHA |
| 90 | [集成与验收](90-integration.md) | A | 10、20、30 均提交 PR |

每个 Codex 只需读取本 README、自己的编号计划、计划直接引用的公共契约和项目规则。公共字段、状态、接口和数据库结构不得复制进各执行计划形成第二套事实来源。

## 0. 权威输入、范围与执行纪律

### 0.1 唯一设计输入

- 权威规格：`docs/superpowers/specs/2026-08-08-creation-step-6-full-stack-timeline-design.md`。
- 公共规则：`AGENTS.md`、`RULES.md`、`docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、`docs/ASYNC_TASKS.md`、`docs/ARCHITECTURE.md`。
- 前端规则：`docs/FRONTEND_GUIDE.md`、`docs/FRONTEND_CODING_STANDARDS.md`、`.agents/skills/antd/SKILL.md`。
- 后端规则：`.agents/skills/ruoyi-plus-ai-coding/SKILL.md`、`docs/BACKEND_GUIDE.md`、`docs/BACKEND_CODING_STANDARDS.md`。
- AI 协作规则：`docs/AI_AGENT_GOVERNANCE.md`、`docs/AI_CODING_RULES.md`。

历史时间轴计划只保留为历史记录，不读取、不复制、不修补，也不作为前置条件。本计划不授权顺带重构第 1 至第 4 步、发现页、运营端、旧数字人任务表、旧素材表或旧账号归属代码。

权威规格决定产品范围和用户行为；本计划负责把设计审查中尚未冻结的字段、错误、幂等、字体供应链和恢复语义收敛为可执行 C0。若规格使用概念名而本计划给出更精确的枚举或资源操作（例如冲突副本），实施以本计划的 C0 明细为准，并由任务 3 前向同步到公共契约；这不是重新启用任何历史计划。

### 0.2 三台设备的固定拓扑

本文所有创建、修改、测试、资源和暂存路径都写成完整仓库相对路径；实施者不得用省略路径、模糊目录或自行推断的同名文件替代。

| 阶段 | 负责人 | 分支 | 唯一输出 |
| --- | --- | --- | --- |
| C0 契约冻结 | A（契约负责人） | `codex/step6-contract` | 公共文档、唯一迁移、契约夹具、跨模块 DTO／枚举／接口、所有权清单 |
| 后端业务 | A（后端负责人） | `codex/step6-backend` | Entity、Mapper、Service、Controller、权限与数据一致性测试 |
| 前端编辑器 | B（前端负责人） | `codex/step6-ui` | 时间轴页面、预览、交互、前端 adapter、任务轮询、时间轴 Mock 开关／构建门禁与测试 |
| 媒体与 AI | C（媒体与 AI 负责人） | `codex/step6-media` | ffprobe／FFmpeg／ASS、AI 建议实现、Worker 媒体执行、`aivideo.timeline.*` 配置与测试 |
| 集成交付 | A（集成负责人） | `codex/step6-integration` | 合并、真实联调、迁移／启动／跨账号／媒体验收证据 |

三条功能分支必须从集成负责人发布的同一个 40 位 `C0_SHA` 创建。任何设备首次需要修改所有权清单之外的文件时先暂停受影响工作，由契约负责人更新所有权清单；不得先写后解决冲突。

前端共享文件也必须唯一归属前端设备：`src/services/ai-video/core/blobAdapter.ts`、`src/services/ai-video/core/ruoyiAdapter.ts` 及其测试、`config/config.ts`、`src/config.test.ts`、`package.json`、`scripts/verify-creation-timeline-production.mjs`、`src/services/ai-video/tasks/**`、`src/pages/tasks/**`、两个时间轴 Mock 和 `public/timeline-fonts/**`。本轮不新增 npm 依赖，因此禁止修改 `package-lock.json`；如实施时证明必须新增依赖，前端任务先暂停，由契约负责人通过所有权变更卡把 lockfile 唯一授予前端设备后再继续。

### 0.3 每个 Codex 任务开始前的固定检查

- [ ] 阅读本计划中自己的任务段、权威规格对应章节和直接相关规则文件。
- [ ] 执行 `git fetch origin --prune`。
- [ ] 执行 `git rev-parse HEAD`，确认功能分支创建前的提交号与发布的 `C0_SHA` 完全相同。
- [ ] 执行 `git status --short`，记录已有改动；不暂存、不格式化、不覆盖他人文件。
- [ ] 只使用精确路径暂存，例如 `git add -- docs/contracts/creation-timeline/ownership-manifest.md`；禁止使用全量暂存。
- [ ] 每个任务遵循红灯测试、最小实现、绿灯测试、精确提交四步。
- [ ] 输出固定包含：完成项、风险、验证命令及结果、阻塞项、提交号。

### 0.4 本机受控集成测试约定

所有 `*IT` 使用现有 `LocalIntegrationEnvironment`，只连接专用本机测试库和 Redis 逻辑库。共享连接信息和凭据直接读取用户端 `application-dev.yml` 的标准 `spring.datasource`、`spring.data.redis` 配置，夹具固定把数据库派生为 `ai_video_test`、Redis 逻辑库设为 15；`AI_VIDEO_IT_*` 环境变量仅用于可选覆盖，无需每次手工设置。运行时启用 `-Plocal-integration-test` 即可。

测试夹具必须生成本次运行前缀，只清理本次创建的数据与 Redis 键；禁止 `FLUSHALL`、`FLUSHDB`、容器替代或连接业务库。凭据可以随两端 `application-dev.yml` 提交，但测试日志、报告和异常不得输出凭据。

### 0.5 合并与契约变更规则

- 合并顺序固定为：C0 → 一次主分支同步检查点 → 后端 → 媒体 → 完整用户端启动 → 前端关闭 Mock → 全链路验证 → 独立审查 → 主分支。
- C0 共享或执行后，`20260808_01_creation_timeline.sql` 永久不可修改。
- 如果实现证明冻结契约有误，发现者提交最小契约变更卡，受影响设备暂停；原契约负责人从最新集成分支创建 `codex/step6-contract-c1`，只新增前向提交，DDL 只能新增 `20260808_02_creation_timeline_c1.sql`。
- 本需求只允许一次自动 C1。再次需要公共契约变更时停止并请项目负责人确认。
- 三个功能分支只通过 PR 合入集成分支；不强推集成分支或主分支，不使用 cherry-pick 拼装功能。

### 0.6 每个实现任务的强制微步模板

下文一个复选框如果同时列出多个类型或场景，执行时必须拆成每步约 2 至 5 分钟、可以单独观察结果的微步，不能用“实现全部 DTO／接口／页面”作为一次操作：

1. 只新增或修改当前微步的测试，运行任务给出的精确命令并保存预期失败原因；编译失败也必须能明确指向尚未创建的类型或方法。
2. 只写让该红灯通过的最小生产代码，禁止顺带重构相邻模块。
3. 重跑同一精确测试，再运行任务指定的模块门禁；命令与退出码写入任务交付消息。
4. 按任务“文件”清单逐项执行 `git add -- <精确路径>`；清单未登记的文件不得暂存。执行 `git diff --cached --name-only` 与 `git diff --cached --check`，确认只有本任务文件后再使用任务给出的提交信息。

任务 2 至任务 6 的代码块是 C0 的可复制最小骨架；实现者必须先让代码块对应的测试失败，再逐类型落地。其余任务至少以“一个测试方法或一个表驱动场景 → 最小实现 → 同一测试绿灯”为一个微步。
