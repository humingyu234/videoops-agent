# P0-A 本机受控集成测试整改实施计划

**规格：** `docs/superpowers/specs/2026-08-01-p0a-local-integration-test.md`
**风险级别：** RED（身份、会话、Redis 共享状态和双启动器）；仅一名实现者，完成后由一名独立审查者复核。

## 约束

- JDK 仅在 Maven 子进程设为 `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot`。
- 不修改、暂存或提交根目录未跟踪的 `package-lock.json`。
- 共享开发密码写入并提交在两端 `application-dev.yml`；命令输出、日志、异常和测试报告不得输出密码。
- 全部 `*IT` 命令带 `-Plocal-integration-test`；默认读取已提交的用户端 `application-dev.yml`，环境变量仅用于可选覆盖。配置缺失或校验失败时只运行不依赖 MySQL/Redis 的单元/静态验证，并记录为未完成真实 IT。

## 实施步骤

1. 在根 `ai-video-api/pom.xml` 新增 `local-integration-test` Profile，把 `aivideo.local-integration-test=true` 传给 Failsafe；保留默认跳过 IT 的行为。删除 core 和 `ai-video-integration-tests` POM 中所有 Testcontainers 依赖；让后者以 `test-jar` 依赖 `ai-video-core`。

2. 先在 `ai-video-core` 测试源码添加 `LocalIntegrationEnvironmentTest`，覆盖 Profile/配置/可选环境覆盖/本机地址/库名/JDBC URL 内嵌凭据/Redis DB/前缀/脏基线的 fail-fast 行为。再实现 `LocalIntegrationEnvironment`：默认读取用户端 `application-dev.yml` 的标准数据源和 Redis 配置并接受七个规定变量可选覆盖；安全解析 JDBC URL；仅删除 `ai_video_test` 的表；启动前拒绝非 `aivideo:it:*` 受控命名空间的既有 Redis Key；只删除直接以当前运行前缀开头的键，以及 hash-tag 精确以该前缀开头的 Redisson 伴生键。

3. 先为 `PlusSaTokenDao` 添加失败单元测试，覆盖物理键前缀、逻辑键搜索结果和默认空前缀兼容。实现可选 `sa-token.redis-key-prefix`：所有 Redis 读写、TTL、删除和搜索转换物理键，Caffeine/业务逻辑继续使用逻辑键；`SaTokenConfig` 将该属性传入 DAO。

4. 先为 `AppSessionServiceImpl` 的可选前缀增加行为测试，再把在线会话键与扫描模式接入该属性。保留现有三个公开测试构造器的默认空前缀，生产默认行为不得改变。

5. 迁移 core IT：删除 `IdentityContainers`，把 `AppIdentitySchemaIT`、`AppIdentityIsolationIT`、`AppActorAndScopeIT`、`AppPermissionTypeIT` 和 `AppSessionIntegrationTestFixture` 改为从 `LocalIntegrationEnvironment` 打开 JDBC/Redis。会话夹具的业务客户端使用当前运行 `NameMapper` 并只暴露逻辑键给断言；跨节点物理删除、键基线和清理使用无前缀的原始客户端。

6. 迁移 `DualStarterHttpFixture`：在建表前重置专用 MySQL，使用同一个受控环境初始化脚本和 Redis 前缀；两个外部 starter 获得相同的安全连接参数、Redis DB/密码、`sa-token.redis-key-prefix` 及对应的 `redisson.keyPrefix`。启动前记录原始物理键基线并拒绝非受控基线键，健康后和关闭时断言没有新增当前运行命名空间外键；失败日志同时脱敏密码、密钥、Token 和数据源 URL；失败清理先确认子 JVM 已退出，再恢复 Sa-Token 测试状态并删除本次运行直接键和 Redisson 伴生键，最后断言零残留，不关闭本机服务。

7. 验证：先执行夹具/前缀单元测试及 Maven 编译；静态扫描容器残留、硬编码非测试数据源和 `FLUSHALL`；在七个变量齐全时运行 core IT 与 `DualTokenIsolationIT`。最后用独立审查聚焦前缀是否覆盖 Sa-Token/在线会话、清理范围和生产默认兼容。

## 执行记录（2026-08-01）

- 已完成根 POM `local-integration-test` Profile、Failsafe 启用标志透传、core 与 `ai-video-integration-tests` 的 Testcontainers 依赖移除，以及 `LocalIntegrationEnvironment` test-jar 导出。
- 已完成 P0-A core/双启动器夹具迁移：MySQL 仅允许本机 `ai_video_test`，Redis 仅允许本机非零 DB；每个运行实例使用 `aivideo:it:<UUID>:` 物理键前缀。原始客户端逐键识别并删除直接前缀键，以及名称精确匹配 timeout／idle／last-access／options 四种 MapCache 结构且 hash-tag 属于当前运行的 Redisson 伴生键，不执行全库清理。
- 已同时向外部 starter 注入无结尾冒号的 `redisson.keyPrefix`，使 RuoYi 全局缓存与 Sa-Token／在线会话键落在同一运行命名空间且不重复加前缀。业务客户端使用命名空间映射，基线、物理删除和清理使用原始客户端；启动／关闭路径会拒绝前缀外新增键并校验当前运行零残留。
- 已完成 Sa-Token DAO 和在线会话索引的可选物理键前缀；生产配置默认空字符串，逻辑键和既有行为不变。
- `local-integration-test` Profile 仍是唯一受支持的执行方式；其传入的启用标志用于防止误运行，而非权限边界。本机地址、`ai_video_test`、非零 Redis DB 和运行前缀校验才是不可被该标志绕过的数据安全门禁。
- 已通过 Redis 空凭据兼容测试（3 个）、无外部服务夹具单元测试（8 个）、core 全量单元测试（101 个）、外部 starter JAR 装配测试（2 个）和进程回收／中断恢复／端口解析／前缀注入／脱敏测试（5 个）。子 JVM 启动失败时会先对配置中的密码、密钥、Token 和动态／标准数据源 URL 脱敏，再写入测试异常；回归测试验证真实子 JVM 回显的 MySQL 密码和数据源 URL 不会泄露。启用 `local-integration-test` 而开发配置缺失或不安全时，Failsafe 在首次连接前明确失败，证明没有容器或其他连接回退。
- 已配置本机 MySQL 8.4 的专用 `ai_video_test`/最小权限测试用户，以及本机 Redis 协议 7.2 的独立 DB 15；共享密码保存在并提交于两端 `application-dev.yml`，环境变量可选覆盖，日志和测试报告不得输出密码。
- 最终 `clean verify` 精确覆盖既定 16 个 Failsafe 类：70 tests，0 failures，0 errors，0 skipped，16 份本次 Profile 报告全部存在且无额外目标类；其中 `DualTokenIsolationIT` 4 个真实双 starter HTTP 场景全部通过。结束后外部 starter 进程为 0，Redis DB 15 的 Key 为 0。
