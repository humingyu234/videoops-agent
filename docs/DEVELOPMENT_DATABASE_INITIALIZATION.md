# 独立开发数据库初始化边界

公司项目只作为一次性脱敏代码基线。禁止在共享 `ai_video` 上迁移或初始化，也禁止把公司业务库整库复制到 VideoOps Agent。

独立开发目标固定为本机 `videoops_agent_dev`。唯一 canonical bootstrap 入口是 `docs/sql/videoops-agent/mysql/bootstrap-manifest.json`：`001`、`010`、`020`、`030`、`040`、`050`、`060`、`070`、`080`、`090`、`100`、`110`、`120`、`130` 为 schema 步骤，`900` 为唯一 seed 步骤。目标身份、顺序、用途、允许写入表和 SHA-256 只以 manifest 为准，本文不复制哈希。

## 空库重建顺序

1. 使用受控管理员身份只读查询 `information_schema.schemata`。目标必须是 `127.0.0.1:3306/videoops_agent_dev`，且 schema 不存在；若已经存在但归属不明，立即停止，不覆盖、不 `DROP`。
2. 创建 `videoops_agent_dev`，字符集 `utf8mb4`，排序规则 `utf8mb4_0900_ai_ci`。创建仅限该 schema 的运行账号 `videoops_agent`；它只保留 `SELECT`、`INSERT`、`UPDATE`、`DELETE`，不得获得全局、其他 schema、表级、列级、例程或 `GRANT OPTION` 权限。管理员/迁移身份只用于受控 bootstrap，不能成为应用依赖。
3. 运行 `scripts/validate-videoops-database-bootstrap.ps1`。只有输出 `VIDEOOPS_DATABASE_BOOTSTRAP_OK` 且 `scripts/tests/validate-videoops-database-bootstrap.Tests.ps1` 全部通过，才可读取 manifest 执行。
4. 按 manifest 逐文件执行 `001` 至 `130`。每步执行前复核 SHA-256，执行后检查退出码和文件自身 postcondition；任一步失败立即停止并只读对账。MySQL DDL 可能隐式提交，禁止 `--force`、整串当事务、盲目重跑或继续后续步骤。
5. 所有 schema postcondition 通过且 41 张表总行数为 0 后，在同一个 MySQL 会话安全注入 `@videoops_creator_password_hash`，执行 `900_minimal_seed.sql`。不得把明文口令或 BCrypt 摘要放进参数、仓库、日志或聊天。使用相同会话摘要重复执行一次，退出码和 postcondition 必须再次通过，行数不得变化。
6. 写入前后分别采集共享 `ai_video` 的结构/关键计数摘要与 Redis DB0/DB14 脱敏计数；任何差异都立即失败，不自动恢复旧数据。

禁止使用 Docker、WSL、整库 dump 或旁路连接替代本机 MySQL 8。密码只能经项目本地 Git 忽略的安全存储、临时子进程环境或专用 login-path 使用；不得复制到配置、SQL、命令参数、日志或持久环境。

## Bootstrap 包边界

`docs/sql/ry_vue.sql`、`docs/sql/ai-video/mysql/` 下的旧迁移和 `20260810_00_development_database_initialization.sql` 只保留为来源审计，不得对 `videoops_agent_dev` 整文件执行：

- `ry_vue.sql` 混合 24 张表结构与 332 条框架、demo、身份和 OSS 配置插入；T1 所需的六张最小框架表已收口到 `001_framework_schema.sql`。
- 旧迁移混有管理端、RunningHub、旧权限 seed 和非幂等 ALTER；T1 最终结构已按真实黄金链提取到 `010` 至 `090`，T2 的三张 Agent 控制面表独立收口在 `100_agent_run_schema.sql`，T4 只以 `110_agent_run_orchestration.sql` 窄升级现有 AgentRun。
- `20260810_00` 含广账号、角色、权限和知识内容；纯合成最小数据已收口到 `900_minimal_seed.sql`。

`ry_job.sql`、`ry_ai.sql`、`ry_workflow.sql` 不是当前 T1 用户端黄金链前置。将来若真实入口需要相应模块，再作为独立增量验收，不扩张本次 bootstrap。

## 最小 seed 与完成守卫

`900` 只允许写 manifest 白名单中的 11 张表：一个纯合成 creator、认证 client、个人角色和 12 项黄金链权限映射，一个纯合成积分账户，以及一份知识 item/version/binding 和一份视频规则。知识版本固定为源码当前要求的 `2084460032627961859`。

它不得包含真实人物、声音、OSS 对象、生成任务、Timeline 运行记录、AgentRun、交付契约、RunningHub 账号、Provider 凭据或公司业务数据。`sys_oss_config` 与人物、声音、资产、任务、创作、时间轴及 T2 Agent 控制面表在 bootstrap 后必须仍为 0 行。

当前 MySQL 8.4 完成合同为：

- schema 身份精确为 `videoops_agent_dev / utf8mb4 / utf8mb4_0900_ai_ci`；
- 41 张 InnoDB base table；无 view、trigger、routine 或 event；
- 128 个实际索引（其中 7 个由 MySQL 为外键自动建立）和 133 个全部启用的 CHECK；
- `av_asset.file_id` 为 nullable signed `BIGINT`；
- 最小 seed 共 33 行，使用相同摘要重复执行后仍为 33；
- `videoops_agent` 能读取新库，访问 `ai_video` 必须得到权限拒绝；
- `ai_video` 结构/关键计数与 Redis DB0/DB14 脱敏快照前后相同。

真实资产只在 T1.3 按来源许可另行一次性、最小化导入到 VideoOps 自己的记录和存储 namespace。真实 OSS、Provider 与付费生成不属于数据库 bootstrap。
