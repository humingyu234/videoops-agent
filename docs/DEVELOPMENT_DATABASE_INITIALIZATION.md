# 开发数据库初始化

> RunningHub 发现与单执行基线必须先按日期顺序执行 `20260808_01_creation_timeline.sql`、`20260811_01_discovery_runninghub_single_execution.sql` 和 `20260811_02_discovery_runninghub_admin_menu.sql`。本初始化脚本只守卫迁移已应用并幂等补种开发数据，不复制业务 DDL 或删除业务数据。执行后可查询 `information_schema.tables`，确认 `av_discovery_banner`、`av_discovery_category`、`av_discovery_tag`、`av_workflow_template`、`av_workflow_execution_config`、`av_runninghub_account`、`av_workflow_order`、`av_workflow_task_execution`、`av_workflow_order_asset`、`av_file_object`、`av_upload_session` 共 11 张表存在。

本文档规定本机开发库基线数据的唯一初始化方式。它用于代码更新后恢复可登录、可授权、可扣积分、可读取知识库的开发环境，不替代正式数据库迁移。

## 唯一入口

使用 `ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml` 最终生效的主数据源连接 MySQL，然后在数据库客户端直接执行完整文件：

```text
docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql
```

执行初始化前应停止用户端和运营端后端，完成后重新登录，避免旧会话缓存继续使用初始化前的权限修订号。

## 数据库连接硬约束

- SQL 文件本身不建立数据库连接；执行它的数据库客户端必须使用 `ai-video-api/ai-video-user-api/src/main/resources/application-dev.yml` 最终生效的 `spring.datasource.dynamic.datasource.master.url`、`username`、`password`。
- 禁止读取 `codex-local-stack.yml`、Docker Compose、容器、`.env`、临时覆盖文件或 Agent 自建连接配置。
- SQL 在数据库客户端当前选中的数据库中执行，不硬编码或校验数据库名称；执行者必须自行确认当前连接和数据库正确。
- 数据库客户端不得把密码写入共享命令、日志或提交文件。
- 用户端与运营端的 `application-dev.yml` 应保持同一开发数据源；初始化入口仍只以用户端后端配置为准，避免出现两个来源。

## 初始化内容

单次执行会补齐并校验：

- 运营端测试管理员及超级管理员角色关系；
- 用户端测试创作者、个人租户归属和本地认证客户端；
- 4 个内置创作端角色、31 个权限及 `personal_creator` 的 24 个用户端必需权限映射；
- 用户与 `personal_creator` 的长期有效角色关系；
- `ai_text_credit` 个人积分账户；新账户初始可用余额为 `1000000`；
- 83 条已发布知识条目、83 个已发布版本、83 条已发布绑定和 2 条已发布视频类型规则；
- 运营端知识库菜单与权限按钮。

固定开发账号：

| 入口 | 用户名 | 密码 | 角色 |
| --- | --- | --- | --- |
| 用户端 | `creator` | `admin123` | `personal_creator` |
| 运营端 | `admin` | `admin123` | `superadmin` |

直接执行文件：

- `docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql`：已内含账号、客户端、角色权限、积分账户、知识库内容和运营菜单，不需要再分别执行其他种子文件。

## 幂等与数据保护

- 初始化不得执行 `CREATE DATABASE`、`USE`、`DROP TABLE`、`TRUNCATE` 或无条件删除。
- 重复执行只恢复固定基线及停用关系，不重复追加同一账号、角色、权限或知识记录。
- 积分账户只在缺失时创建；已有账户的可用、锁定、已用余额和修订号必须全部保留，重复执行不得补发积分。
- 已有业务任务、生成记录、素材、脚本、时间线、登录日志和安全审计不得清空或伪造。
- 人物、声音及数字人媒体同时依赖本地或 OSS 文件，不属于纯数据库基线，不能只插入孤立数据库记录。
- 当前项目没有积分流水表实现，不得为了初始化虚构流水表或流水数据。

## 结构前提与维护规则

- 开发库必须先具备 RuoYi 基础表；缺失时先执行正式基础结构脚本和项目迁移。初始化入口不会覆盖或重建 RuoYi 基础结构。
- 初始化 SQL 会补齐知识库表；创作端身份表和积分账户表必须先通过正式迁移创建，缺失时 SQL 会立即失败。
- 新增登录必需账号、认证客户端、内置角色、权限、积分单位、知识路由或知识基线时，必须同步更新一键初始化 SQL、结果校验和本文档。
- 新增业务表或字段仍通过版本化迁移交付，不得把正式结构演进偷偷塞进开发种子 SQL。
- 变更本文档或入口规则后必须运行 `scripts/validate-development-standards.ps1`。
