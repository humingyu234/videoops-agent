# P0-A 本机受控集成测试整改规格

**状态：** 已实现并通过 2026-08-01 本机真实 MySQL/Redis 验收。来源为 P0-A 阻塞说明后的“继续”。本规格落实既有 P0-A 计划和 `RULES.md` 的本机测试决定，不改变业务接口、数据库契约或生产部署。

## 目标与边界

- 所有 P0-A `*IT` 只连接本机 MySQL 8 的 `ai_video_test` 和本机 Redis 7 的非零独立逻辑库。
- 连接信息和凭据默认读取用户端 `application-dev.yml` 的标准 `spring.datasource`、`spring.data.redis` 配置；夹具固定派生 MySQL 专用库 `ai_video_test` 和 Redis DB 15。`AI_VIDEO_IT_MYSQL_URL`、`AI_VIDEO_IT_MYSQL_USERNAME`、`AI_VIDEO_IT_MYSQL_PASSWORD`、`AI_VIDEO_IT_REDIS_HOST`、`AI_VIDEO_IT_REDIS_PORT`、`AI_VIDEO_IT_REDIS_DATABASE`、`AI_VIDEO_IT_REDIS_PASSWORD` 仅作为可选覆盖。
- 官方 Maven 命令通过显式 `local-integration-test` Profile 向 Failsafe 传入启用标志与用户端 `application-dev.yml` 路径；启用标志、开发配置或必要字段缺失时，在任何连接、建表、迁移、清理或子进程启动前失败。该标志是防止误运行的操作门禁，不是权限或数据安全边界；数据安全由后续本机地址、精确库名、Redis DB 和运行前缀的硬校验保证。
- 夹具校验 MySQL URL 与 Redis 主机仅为 `localhost`、`127.0.0.1` 或 `::1`，且 MySQL 库名严格为 `ai_video_test`；用户名和密码使用标准独立配置字段，不嵌入 JDBC URL；Redis DB 必须大于零。
- 每次夹具创建生成只含安全 UUID 字符的 `aivideo:it:<runId>:` 前缀。清理只逐键删除直接以该前缀开头的业务键，以及 hash-tag 精确以该前缀开头、名称严格匹配 Redisson MapCache timeout／idle／last-access／options 结构的伴生键；绝不执行 `FLUSHALL` 或 `FLUSHDB`，不停止/重启本机 MySQL 或 Redis。
- 删除所有 Testcontainers 生产、测试、POM 依赖与说明；保留现有 SQL、业务契约和断言语义。

## Redis 运行前缀决策

现有 P0-A 会话和 Sa-Token 会把逻辑键直接写为 `aivideo:app:online:*` 与 `Authorization:*`。仅选择独立 Redis DB 无法满足“当前运行前缀”和“只清当前运行键”的硬约束。

采用方案 A：在 `PlusSaTokenDao` 增加可选、默认空的**物理 Redis 键前缀**，并让 `AppSessionServiceImpl` 接受同一可选前缀。逻辑键、HTTP `Authorization` 头、Sa-Token 登录类型和生产默认行为完全不变；仅在 IT 通过 `sa-token.redis-key-prefix=aivideo:it:<runId>:` 指定时，实际 Redis 键被隔离。搜索结果仍返回逻辑键，Caffeine 缓存仍以逻辑键索引。

外部 starter 还必须同时设置 `redisson.keyPrefix=aivideo:it:<runId>`（无结尾冒号），使 RuoYi 启动阶段创建的全局缓存键也归属于同一次运行。二者映射到同一个物理前缀，`KeyPrefixHandler` 对已经带该前缀的 Sa-Token／在线会话键不得重复加前缀。Redisson MapCache 可能额外创建 `redisson__timeout__set:{<物理对象名>}`、`redisson__idle__set:{<物理对象名>}`、`redisson__map_cache__last_access__set:{<物理对象名>}` 与 `{<物理对象名>}:redisson_options` 伴生键；其 hash-tag 必须从同一运行前缀开始，且名称必须精确匹配上述结构，夹具据此做严格归属和清理。core 会话 IT 的业务 Redisson 客户端使用同样的 `NameMapper`，断言只观察还原后的逻辑键；基线快照、跨节点物理删除和最终清理始终使用无 `NameMapper` 的原始客户端。

不采用方案 B（只依赖 Redis DB），因为它无法满足当前运行前缀；不采用方案 C（反射篡改静态 Redis 客户端），因为会隐藏生产装配差异且无法约束两个外部 starter。

## 架构

`LocalIntegrationEnvironment` 位于 `ai-video-core` 测试源码，随现有 test-jar 导出。它负责读取并验证 `application-dev.yml`、合并可选环境变量覆盖、打开 JDBC 连接、只在 `ai_video_test` 中删除全部表、创建用于键清理的 Redis 客户端、生成运行前缀，并提供启动器所需的安全连接参数。它不打印密码。

core 身份/会话 IT 和黑盒双启动器夹具都只经该夹具取得连接参数。双启动器向两个 JVM 显式传入本机数据源、Redis DB/密码、相同的 `sa-token.redis-key-prefix` 与对应的 `redisson.keyPrefix`。启动前先记录原始物理键基线，并拒绝基线中任何不属于 `aivideo:it:*` 受控运行命名空间或其严格 Redisson 伴生结构的键，避免既有固定全局 Key 被覆盖却逃过 Key 集合差分；两个 starter 健康后以及关闭时，都断言基线之后新增的键只属于当前 run。启动失败或测试结束时先回收子 JVM，确认进程退出后才关闭测试客户端、清理当前 run 的直接键与伴生键，并断言当前 run 不再残留键。子 JVM 失败日志除密码、密钥和 Token 外，还必须防御性脱敏数据源 URL。

## 验收

1. 静态扫描在 `ai-video-api` 中找不到 Testcontainers 依赖、导入、注解或容器工厂。
2. 根 POM 存在 `local-integration-test` Profile，Failsafe 透传启用标志。
3. `LocalIntegrationEnvironment` 的无外部服务单元测试覆盖：缺 Profile、缺变量、非本机地址、非 `ai_video_test`、JDBC URL 内嵌凭据、Redis DB 为零、非受控 Redis 基线键、严格 Redisson 伴生键归属，以及合法配置生成的隔离前缀。
4. `PlusSaTokenDao` 的单元测试证明前缀只改变物理键、搜索返回逻辑键；`AppSessionServiceImpl` 的现有逻辑键语义保持；全局 Redisson 前缀与 Sa-Token 前缀映射到同一物理命名空间且不重复加前缀。
5. 双启动器启动和清理路径能证明本次新增键全部归属当前 run，清理后当前 run 的直接键和 Redisson 伴生键均为零；任何越出受控结构的新键都使测试失败。
6. 已提交的用户端 `application-dev.yml` 提供默认本机配置时，P0-A core 和 `DualTokenIsolationIT` 可通过 `-Plocal-integration-test` 运行；配置缺失或校验失败时必须明确失败，不能回退到容器或其他数据源。2026-08-01 最终验收实际执行既定 16 类、70 tests，0 failures、0 errors、0 skipped，且结束后 Redis DB 15 与外部 starter 均零残留。
