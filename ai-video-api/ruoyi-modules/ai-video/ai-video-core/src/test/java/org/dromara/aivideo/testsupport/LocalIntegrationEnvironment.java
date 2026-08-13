package org.dromara.aivideo.testsupport;

import org.dromara.common.redis.handler.KeyPrefixHandler;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * P0-A 本机受控集成测试环境。
 *
 * <p>此夹具只允许连接本机专用 MySQL 库和独立 Redis 逻辑库。它不提供任何开发、预发或生产
 * 数据源回退，也不会停止本机服务或执行 Redis 全库清理。</p>
 */
public final class LocalIntegrationEnvironment {

    static final String ENABLED_PROPERTY = "aivideo.local-integration-test";
    static final String DEVELOPMENT_CONFIG_PROPERTY = "aivideo.local-integration-config";
    private static final String TEST_DATABASE = "ai_video_test";
    private static final String TEST_REDIS_DATABASE = "15";
    private static final String REDIS_PREFIX = "aivideo:it:";
    private static final String MYSQL_URL_PROPERTY = "spring.datasource.dynamic.datasource.master.url";
    private static final String MYSQL_USERNAME_PROPERTY = "spring.datasource.dynamic.datasource.master.username";
    private static final String MYSQL_PASSWORD_PROPERTY = "spring.datasource.dynamic.datasource.master.password";
    private static final String REDIS_HOST_PROPERTY = "spring.data.redis.host";
    private static final String REDIS_PORT_PROPERTY = "spring.data.redis.port";
    private static final String REDIS_PASSWORD_PROPERTY = "spring.data.redis.password";
    private static final Set<String> REDISSON_MAP_CACHE_PREFIXES = Set.of(
        "redisson__timeout__set:",
        "redisson__idle__set:",
        "redisson__map_cache__last_access__set:",
        "redisson__execute_task_once_latch:"
    );
    private static final String REDISSON_MAP_CACHE_OPTIONS_SUFFIX = ":redisson_options";
    private static final Pattern RUN_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final String jdbcUrl;
    private final String mysqlUsername;
    private final String mysqlPassword;
    private final String redisHost;
    private final int redisPort;
    private final int redisDatabase;
    private final String redisPassword;
    private final String redisKeyPrefix;

    private LocalIntegrationEnvironment(String jdbcUrl, String mysqlUsername, String mysqlPassword,
                                        String redisHost, int redisPort, int redisDatabase, String redisPassword,
                                        String redisKeyPrefix) {
        this.jdbcUrl = jdbcUrl;
        this.mysqlUsername = mysqlUsername;
        this.mysqlPassword = mysqlPassword;
        this.redisHost = redisHost;
        this.redisPort = redisPort;
        this.redisDatabase = redisDatabase;
        this.redisPassword = redisPassword;
        this.redisKeyPrefix = redisKeyPrefix;
    }

    /**
     * 从已提交的开发配置与可选环境变量覆盖构建受控测试环境。
     *
     * @return 已验证、但尚未建立外部连接的测试环境
     */
    public static LocalIntegrationEnvironment requireFromEnvironment() {
        String configPath = System.getProperty(DEVELOPMENT_CONFIG_PROPERTY);
        Path developmentConfig = configPath == null || configPath.isBlank() ? null : Path.of(configPath);
        return from(System.getenv(), System.getProperty(ENABLED_PROPERTY), UUID.randomUUID().toString(),
            developmentConfig);
    }

    static LocalIntegrationEnvironment from(Map<String, String> environment, String enabledMarker, String runId,
                                            Path developmentConfig) {
        Map<String, String> effectiveConfiguration = new HashMap<>(loadDevelopmentConfiguration(developmentConfig));
        if (environment != null) {
            effectiveConfiguration.putAll(environment);
        }
        String configuredJdbcUrl = effectiveConfiguration.get("AI_VIDEO_IT_MYSQL_URL");
        if (configuredJdbcUrl != null) {
            effectiveConfiguration.put("AI_VIDEO_IT_MYSQL_URL", deriveTestJdbcUrl(configuredJdbcUrl));
        }
        effectiveConfiguration.put("AI_VIDEO_IT_REDIS_DATABASE", TEST_REDIS_DATABASE);
        return from(effectiveConfiguration, enabledMarker, runId);
    }

    static LocalIntegrationEnvironment from(Map<String, String> environment, String enabledMarker, String runId) {
        if (!"true".equalsIgnoreCase(enabledMarker)) {
            throw new IllegalStateException("本机集成测试需显式启用 aivideo.local-integration-test=true（推荐使用 -Plocal-integration-test）");
        }
        if (environment == null) {
            throw new IllegalStateException("缺少本机集成测试连接配置");
        }

        String jdbcUrl = requireNonBlank(environment, "AI_VIDEO_IT_MYSQL_URL");
        String mysqlUsername = requireNonBlank(environment, "AI_VIDEO_IT_MYSQL_USERNAME");
        String mysqlPassword = requirePresent(environment, "AI_VIDEO_IT_MYSQL_PASSWORD");
        String redisHost = requireNonBlank(environment, "AI_VIDEO_IT_REDIS_HOST");
        String redisPortValue = requireNonBlank(environment, "AI_VIDEO_IT_REDIS_PORT");
        String redisDatabaseValue = requireNonBlank(environment, "AI_VIDEO_IT_REDIS_DATABASE");
        String redisPassword = requirePresent(environment, "AI_VIDEO_IT_REDIS_PASSWORD");

        validateMySqlUrl(jdbcUrl);
        validateLocalHost(redisHost, "Redis");
        int redisPort = parsePort(redisPortValue);
        int redisDatabase = parseRedisDatabase(redisDatabaseValue);
        String redisKeyPrefix = redisKeyPrefix(runId);
        return new LocalIntegrationEnvironment(jdbcUrl, mysqlUsername, mysqlPassword, redisHost, redisPort,
            redisDatabase, redisPassword, redisKeyPrefix);
    }

    private static Map<String, String> loadDevelopmentConfiguration(Path configPath) {
        if (configPath == null) {
            return Map.of();
        }
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalStateException("本机集成测试开发配置不存在: " + configPath.toAbsolutePath().normalize());
        }
        try {
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new FileSystemResource(configPath));
            Properties properties = factory.getObject();
            Map<String, String> configuration = new HashMap<>();
            if (properties != null) {
                String developmentJdbcUrl = properties.getProperty(MYSQL_URL_PROPERTY);
                if (developmentJdbcUrl != null) {
                    configuration.put("AI_VIDEO_IT_MYSQL_URL", developmentJdbcUrl);
                }
                copyProperty(properties, MYSQL_USERNAME_PROPERTY, configuration, "AI_VIDEO_IT_MYSQL_USERNAME");
                copyProperty(properties, MYSQL_PASSWORD_PROPERTY, configuration, "AI_VIDEO_IT_MYSQL_PASSWORD");
                copyProperty(properties, REDIS_HOST_PROPERTY, configuration, "AI_VIDEO_IT_REDIS_HOST");
                copyProperty(properties, REDIS_PORT_PROPERTY, configuration, "AI_VIDEO_IT_REDIS_PORT");
                copyProperty(properties, REDIS_PASSWORD_PROPERTY, configuration, "AI_VIDEO_IT_REDIS_PASSWORD");
            }
            return configuration;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法读取本机集成测试开发配置: "
                + configPath.toAbsolutePath().normalize(), exception);
        }
    }

    private static void copyProperty(Properties properties, String propertyKey,
                                     Map<String, String> configuration, String environmentKey) {
        String value = properties.getProperty(propertyKey);
        if (value != null) {
            configuration.put(environmentKey, value);
        }
    }

    private static String deriveTestJdbcUrl(String developmentJdbcUrl) {
        int queryIndex = developmentJdbcUrl.indexOf('?');
        int fragmentIndex = developmentJdbcUrl.indexOf('#');
        int suffixIndex;
        if (queryIndex < 0) {
            suffixIndex = fragmentIndex < 0 ? developmentJdbcUrl.length() : fragmentIndex;
        } else if (fragmentIndex < 0) {
            suffixIndex = queryIndex;
        } else {
            suffixIndex = Math.min(queryIndex, fragmentIndex);
        }
        int databaseSeparator = developmentJdbcUrl.lastIndexOf('/', suffixIndex - 1);
        int authorityStart = developmentJdbcUrl.indexOf("//");
        if (authorityStart < 0 || databaseSeparator <= authorityStart + 1 || databaseSeparator + 1 >= suffixIndex) {
            throw new IllegalStateException("MySQL 开发配置 URL 缺少数据库名");
        }
        return developmentJdbcUrl.substring(0, databaseSeparator + 1)
            + TEST_DATABASE
            + developmentJdbcUrl.substring(suffixIndex);
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public String mysqlUsername() {
        return mysqlUsername;
    }

    public String mysqlPassword() {
        return mysqlPassword;
    }

    public String redisHost() {
        return redisHost;
    }

    public int redisPort() {
        return redisPort;
    }

    public int redisDatabase() {
        return redisDatabase;
    }

    public String redisPassword() {
        return redisPassword;
    }

    /**
     * 返回仅属于本次测试运行的物理 Redis 键前缀。
     *
     * @return `aivideo:it:&lt;runId&gt;:` 格式前缀
     */
    public String redisKeyPrefix() {
        return redisKeyPrefix;
    }

    /**
     * 返回供 RuoYi {@code redisson.keyPrefix} 使用的不带结尾冒号前缀。
     *
     * @return `aivideo:it:&lt;runId&gt;` 格式前缀
     */
    public String redissonKeyPrefix() {
        return redisKeyPrefix.substring(0, redisKeyPrefix.length() - 1);
    }

    /**
     * 建立到已验证测试库的 JDBC 连接。
     *
     * @return 测试库 JDBC 连接
     * @throws SQLException 连接或安全二次校验失败
     */
    public Connection openMySqlConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl, mysqlUsername, mysqlPassword);
        boolean valid = false;
        try {
            verifyConnectedDatabase(connection);
            valid = true;
            return connection;
        } finally {
            if (!valid) {
                connection.close();
            }
        }
    }

    /**
     * 只清空专用 `ai_video_test` 中的表；调用方负责按既定顺序执行基础 SQL 和模块迁移。
     *
     * @throws SQLException 清理失败
     */
    public void resetDedicatedMySqlSchema() throws SQLException {
        try (Connection connection = openMySqlConnection()) {
            List<String> tableNames = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_type = 'BASE TABLE'
                """); ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tableNames.add(resultSet.getString(1));
                }
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                try {
                    for (String tableName : tableNames) {
                        statement.execute("DROP TABLE IF EXISTS `" + tableName.replace("`", "``") + "`");
                    }
                } finally {
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1");
                }
            }
        }
    }

    /**
     * 建立只用于受控键检查或清理的 Redis 客户端。调用方必须关闭返回的客户端。
     *
     * @return 连接指定独立逻辑库的客户端
     */
    public RedissonClient openRedisClient() {
        Config config = newRedisConfig();
        config.setCodec(StringCodec.INSTANCE);
        return Redisson.create(config);
    }

    /**
     * 创建已绑定本机独立 Redis 逻辑库的 Redisson 配置。
     *
     * @return 可由需要业务序列化编解码的测试夹具继续配置的 Redisson 配置
     */
    public Config newRedisConfig() {
        Config config = new Config();
        var singleServer = config.useSingleServer()
            .setAddress("redis://" + redisHostForUri() + ':' + redisPort)
            .setDatabase(redisDatabase);
        if (!redisPassword.isBlank()) {
            singleServer.setPassword(redisPassword);
        }
        return config;
    }

    /**
     * 创建供测试业务代码使用的 Redisson 配置，使所有未手工加前缀的 Key 仍归属于当前运行。
     *
     * <p>清理客户端不得使用此配置，否则无法按物理前缀核对当前逻辑库中的真实 Key。</p>
     *
     * @return 已设置当前运行 NameMapper 的 Redisson 配置
     */
    public Config newNamespacedRedisConfig() {
        Config config = newRedisConfig();
        config.setNameMapper(new KeyPrefixHandler(redissonKeyPrefix()));
        return config;
    }

    /**
     * 使用无 NameMapper 的客户端快照当前独立逻辑库中的物理 Key。
     *
     * @return 不可变物理 Key 集合
     */
    public Set<String> snapshotRedisKeys() {
        RedissonClient client = openRedisClient();
        try {
            Set<String> keys = new HashSet<>();
            for (String key : client.getKeys().getKeys()) {
                keys.add(key);
            }
            return Set.copyOf(keys);
        } finally {
            client.shutdown();
        }
    }

    /**
     * 断言专用 Redis 逻辑库的启动前基线只包含其他受控集成测试运行的键。
     *
     * <p>若基线已经存在固定全局键，仅比较启动前后的 Key 集合无法发现 starter 对该键的覆盖写，
     * 因此必须在启动子进程前拒绝此类脏基线。</p>
     *
     * @param baselineKeys 启动子进程前的物理 Key 快照
     */
    public void assertRedisBaselineKeysControlled(Set<String> baselineKeys) {
        Objects.requireNonNull(baselineKeys, "Redis 基线 Key 不能为空");
        long uncontrolledKeyCount = baselineKeys.stream()
            .filter(key -> !isRedisKeyOwnedByNamespace(key, REDIS_PREFIX))
            .count();
        if (uncontrolledKeyCount > 0) {
            throw new IllegalStateException("专用 Redis 逻辑库基线存在 " + uncontrolledKeyCount
                + " 个非受控集成测试命名空间 Key，请先人工核对并清理");
        }
    }

    /**
     * 断言基线之后新增的所有 Redis Key 都属于当前运行前缀。
     *
     * @param baselineKeys 启动子进程前的物理 Key 快照
     */
    public void assertOnlyCurrentRunRedisKeysAdded(Set<String> baselineKeys) {
        Objects.requireNonNull(baselineKeys, "Redis 基线 Key 不能为空");
        long unexpectedKeyCount = snapshotRedisKeys().stream()
            .filter(key -> !baselineKeys.contains(key))
            .filter(key -> !isCurrentRunRedisKey(key))
            .count();
        if (unexpectedKeyCount > 0) {
            throw new IllegalStateException("检测到 " + unexpectedKeyCount + " 个不属于当前运行前缀的新 Redis Key");
        }
    }

    /**
     * 断言当前运行前缀已经清理完毕。
     */
    public void assertCurrentRunRedisKeysCleared() {
        long remainingKeyCount = snapshotRedisKeys().stream()
            .filter(this::isCurrentRunRedisKey)
            .count();
        if (remainingKeyCount > 0) {
            throw new IllegalStateException("当前运行前缀仍残留 " + remainingKeyCount + " 个 Redis Key");
        }
    }

    /**
     * 仅删除本次运行 UUID 前缀下的键，绝不执行全库清理。
     */
    public void clearCurrentRunRedisKeys() {
        RedissonClient client = openRedisClient();
        try {
            List<String> ownedKeys = new ArrayList<>();
            for (String key : client.getKeys().getKeys()) {
                if (isCurrentRunRedisKey(key)) {
                    ownedKeys.add(key);
                }
            }
            if (!ownedKeys.isEmpty()) {
                client.getKeys().delete(ownedKeys.toArray(String[]::new));
            }
        } finally {
            client.shutdown();
        }
    }

    /**
     * 判断物理 Key 是否属于当前测试运行。
     *
     * <p>除直接以运行前缀开头的业务 Key 外，仅识别 Redisson MapCache 实际使用的
     * timeout、idle、last-access 与 options 伴生 Key，不接受名称相似的任意 Key。</p>
     *
     * @param key 物理 Redis Key
     * @return 是否可由当前运行断言和清理
     */
    boolean isCurrentRunRedisKey(String key) {
        return isRedisKeyOwnedByNamespace(key, redisKeyPrefix);
    }

    private static boolean isRedisKeyOwnedByNamespace(String key, String namespacePrefix) {
        if (key == null || namespacePrefix == null || namespacePrefix.isEmpty()) {
            return false;
        }
        if (key.startsWith(namespacePrefix)) {
            return true;
        }

        int hashTagStart = key.indexOf('{');
        if (hashTagStart < 0 || !key.startsWith(namespacePrefix, hashTagStart + 1)) {
            return false;
        }
        int hashTagEnd = key.indexOf('}', hashTagStart + 1 + namespacePrefix.length());
        if (hashTagEnd < 0) {
            return false;
        }

        boolean redissonPrefix = REDISSON_MAP_CACHE_PREFIXES.stream()
            .anyMatch(prefix -> hashTagStart == prefix.length() && key.startsWith(prefix));
        boolean redissonSuffix = hashTagStart == 0
            && REDISSON_MAP_CACHE_OPTIONS_SUFFIX.equals(key.substring(hashTagEnd + 1));
        return redissonPrefix || redissonSuffix;
    }

    private static String requirePresent(Map<String, String> environment, String variable) {
        if (!environment.containsKey(variable)) {
            throw new IllegalStateException("缺少本机集成测试连接配置项：" + variable);
        }
        String value = environment.get(variable);
        return value == null ? "" : value;
    }

    private static String requireNonBlank(Map<String, String> environment, String variable) {
        String value = requirePresent(environment, variable);
        if (value.isBlank()) {
            throw new IllegalStateException("本机集成测试连接配置项不能为空：" + variable);
        }
        return value;
    }

    private static void validateMySqlUrl(String jdbcUrl) {
        if (!jdbcUrl.regionMatches(true, 0, "jdbc:mysql://", 0, "jdbc:mysql://".length())) {
            throw new IllegalStateException("MySQL 集成测试 URL 必须是本机 jdbc:mysql 地址");
        }
        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MySQL 集成测试 URL 格式不安全", exception);
        }
        rejectEmbeddedJdbcCredentials(uri);
        validateLocalHost(uri.getHost(), "MySQL");
        String path = uri.getPath();
        String database = path == null ? "" : path.replaceFirst("^/", "");
        if (!TEST_DATABASE.equals(database)) {
            throw new IllegalStateException("MySQL 集成测试只能使用专用库 ai_video_test");
        }
    }

    private static void rejectEmbeddedJdbcCredentials(URI uri) {
        if (uri.getRawUserInfo() != null) {
            throw new IllegalStateException("MySQL 集成测试 URL 禁止内嵌用户名或密码凭据");
        }
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return;
        }
        try {
            for (String parameter : rawQuery.split("&")) {
                String rawName = parameter.split("=", 2)[0];
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
                if ("user".equals(name) || "username".equals(name) || "password".equals(name)) {
                    throw new IllegalStateException("MySQL 集成测试 URL 禁止内嵌用户名或密码凭据");
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MySQL 集成测试 URL 查询参数格式不安全", exception);
        }
    }

    private static void validateLocalHost(String rawHost, String service) {
        String host = normalizeHost(rawHost);
        if (!("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host))) {
            throw new IllegalStateException(service + " 集成测试只能连接本机 localhost、127.0.0.1 或 ::1");
        }
    }

    private static String normalizeHost(String rawHost) {
        if (rawHost == null) {
            return "";
        }
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException("out of range");
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("AI_VIDEO_IT_REDIS_PORT 必须是有效端口", exception);
        }
    }

    private static int parseRedisDatabase(String value) {
        try {
            int database = Integer.parseInt(value);
            if (database <= 0) {
                throw new NumberFormatException("must be dedicated");
            }
            return database;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Redis 集成测试必须使用非零独立逻辑库", exception);
        }
    }

    private static String redisKeyPrefix(String runId) {
        if (runId == null || !RUN_ID_PATTERN.matcher(runId).matches()) {
            throw new IllegalStateException("本机集成测试运行编号格式不安全");
        }
        return REDIS_PREFIX + runId + ':';
    }

    private void verifyConnectedDatabase(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || !TEST_DATABASE.equals(resultSet.getString(1))) {
                throw new SQLException("MySQL 连接未落到专用测试库 ai_video_test");
            }
        }
    }

    private String redisHostForUri() {
        return redisHost.contains(":") ? '[' + redisHost + ']' : redisHost;
    }
}
