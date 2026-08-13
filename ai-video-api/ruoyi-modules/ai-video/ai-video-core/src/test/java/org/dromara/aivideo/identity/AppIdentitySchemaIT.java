package org.dromara.aivideo.identity;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在真实 MySQL 8.4 表结构中验证可重复执行的创作端身份迁移。
 */
@Tag("dev")
class AppIdentitySchemaIT {

    private static final Set<String> APP_TABLES = Set.of(
        "app_user",
        "app_auth_client",
        "app_social_identity",
        "app_permission",
        "app_role",
        "app_role_permission",
        "app_user_role",
        "app_login_log",
        "app_security_audit"
    );

    private static final Set<String> APP_MANAGEMENT_PERMISSIONS = Set.of(
        "aivideo:app-user:query",
        "aivideo:app-user:add",
        "aivideo:app-user:edit",
        "aivideo:app-user:reset-password",
        "aivideo:app-user:kickout",
        "aivideo:app-user:assign-role",
        "aivideo:app-role:query",
        "aivideo:app-role:edit",
        "aivideo:app-role:assign-permission",
        "aivideo:app-auth-client:query",
        "aivideo:app-auth-client:edit",
        "aivideo:app-auth-client:rotate-secret",
        "aivideo:app-session:query",
        "aivideo:app-session:kickout",
        "aivideo:app-login-log:query",
        "aivideo:app-security-audit:query"
    );

    private static final Set<String> APP_MANAGEMENT_BUTTON_PERMISSIONS = Set.of(
        "aivideo:app-user:add",
        "aivideo:app-user:edit",
        "aivideo:app-user:reset-password",
        "aivideo:app-user:kickout",
        "aivideo:app-user:assign-role",
        "aivideo:app-role:edit",
        "aivideo:app-role:assign-permission",
        "aivideo:app-auth-client:edit",
        "aivideo:app-auth-client:rotate-secret",
        "aivideo:app-session:kickout"
    );

    private static final Map<String, String> COMPONENT_QUERY_PERMISSIONS = Map.of(
        "aivideo/app-user/index", "aivideo:app-user:query",
        "aivideo/app-role/index", "aivideo:app-role:query",
        "aivideo/app-auth-client/index", "aivideo:app-auth-client:query",
        "aivideo/app-session/index", "aivideo:app-session:query",
        "aivideo/app-login-log/index", "aivideo:app-login-log:query",
        "aivideo/app-security-audit/index", "aivideo:app-security-audit:query"
    );

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private final Path apiRoot = locateApiRoot();

    @BeforeEach
    void reloadRuoYiBaseline() throws SQLException, IOException {
        ENV.resetDedicatedMySqlSchema();
        try (Connection connection = connection()) {
            executeSqlScript(connection, apiRoot.resolve("../docs/sql/ry_vue.sql"));
        }
    }

    @Test
    void createsExactlyTheExpectedAppTablesAndRepeatableSeeds() throws SQLException, IOException {
        try (Connection connection = connection()) {
            executeP0MigrationTwice(connection);

            assertThat(queryStringSet(connection, """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name LIKE 'app!_%' ESCAPE '!'
                """)).containsExactlyInAnyOrderElementsOf(APP_TABLES);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM app_permission")).isEqualTo(15);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM app_role")).isEqualTo(4);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM app_role_permission")).isZero();
            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.key_column_usage
                WHERE table_schema = DATABASE()
                  AND table_name LIKE 'app!_%' ESCAPE '!'
                  AND referenced_table_name LIKE 'sys!_%' ESCAPE '!'
                """)).isZero();
        }
    }

    @Test
    void restrictsTypedActorsToIndependentAndOperationalUsers() throws SQLException, IOException {
        Map<String, List<String>> expectedCheckFragments = Map.of(
            "ck_app_user_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_auth_client_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_social_identity_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_permission_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_role_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_role_permission_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_user_role_actor_types", List.of("created_by_type", "updated_by_type", "app_user", "sys_user"),
            "ck_app_security_audit_actor_type", List.of("actor_type", "app_user", "sys_user")
        );

        try (Connection connection = connection()) {
            executeP0MigrationTwice(connection);

            for (Map.Entry<String, List<String>> entry : expectedCheckFragments.entrySet()) {
                String checkClause = checkClause(connection, entry.getKey());
                assertThat(checkClause).as("约束 %s", entry.getKey()).isNotBlank();
                String normalizedClause = checkClause.toLowerCase(Locale.ROOT);
                for (String expectedFragment : entry.getValue()) {
                    assertThat(normalizedClause)
                        .as("约束 %s", entry.getKey())
                        .contains(expectedFragment);
                }
            }

            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_user (
                    user_id, username, username_normalized, password_hash, personal_tenant_id,
                    display_name, created_by_type, created_by_id, updated_by_type, updated_by_id,
                    create_time, update_time
                ) VALUES (
                    900000000000000001, 'invalid-actor', 'invalid-actor', 'hash', 900000000000000001,
                    'invalid actor', 'service', 1, 'sys_user', 1, NOW(), NOW()
                )
                """))
                .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> executeUpdate(connection, """
                INSERT INTO app_security_audit (
                    audit_id, resource_type, resource_id, action, actor_type, actor_id,
                    reason, request_id, ip_address, occurred_at
                ) VALUES (
                    900000000000000002, 'identity', '900000000000000001', 'test', 'service', 1,
                    'invalid actor', 'request-identity-schema-it', '127.0.0.1', NOW()
                )
                """))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void seedsOnlyTheRequiredOperationalIdentityMenus() throws SQLException, IOException {
        try (Connection connection = connection()) {
            executeP0MigrationTwice(connection);

            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM sys_menu
                WHERE menu_name = '创作端身份安全'
                  AND parent_id = 0
                  AND menu_type = 'M'
                """)).isEqualTo(1);
            for (Map.Entry<String, String> entry : COMPONENT_QUERY_PERMISSIONS.entrySet()) {
                assertThat(queryLong(connection, """
                    SELECT COUNT(*)
                    FROM sys_menu
                    WHERE menu_type = 'C'
                      AND component = ?
                      AND perms = ?
                    """, entry.getKey(), entry.getValue())).isEqualTo(1);
            }

            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM sys_menu
                WHERE perms LIKE 'aivideo:app!-%' ESCAPE '!'
                """)).isEqualTo(16);
            assertThat(queryStringSet(connection, """
                SELECT perms
                FROM sys_menu
                WHERE perms LIKE 'aivideo:app!-%' ESCAPE '!'
                """)).containsExactlyInAnyOrderElementsOf(APP_MANAGEMENT_PERMISSIONS);
            assertThat(queryStringSet(connection, """
                SELECT perms
                FROM sys_menu
                WHERE menu_type = 'F'
                  AND perms LIKE 'aivideo:app!-%' ESCAPE '!'
                """)).containsExactlyInAnyOrderElementsOf(APP_MANAGEMENT_BUTTON_PERMISSIONS);
            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM sys_menu
                WHERE perms LIKE 'aivideo:app!-%' ESCAPE '!'
                  AND (
                    LOWER(perms) LIKE '%impersonate%'
                    OR LOWER(perms) LIKE '%token%'
                    OR LOWER(perms) LIKE '%issue%'
                    OR LOWER(perms) LIKE '%inherit%'
                    OR LOWER(perms) LIKE '%sys!_role%' ESCAPE '!'
                  )
                """)).isZero();
        }
    }

    @Test
    void matchesTheFrozenIdentityColumnAndSeedDefinitions() throws SQLException, IOException, InterruptedException {
        try (Connection connection = connection()) {
            executeP0Migration(connection);

            assertColumn(connection, "app_auth_client", "token_timeout", "bigint", "NO", null);
            assertColumn(connection, "app_auth_client", "active_timeout", "bigint", "NO", null);
            assertColumnDefault(connection, "app_auth_client", "token_timeout", null);
            assertColumnDefault(connection, "app_auth_client", "active_timeout", null);
            assertCheckClauseContains(connection, "ck_app_auth_client_revision", "client_revision", ">", "0");

            assertColumn(connection, "app_permission", "permission_code", "varchar", "NO", 100L);
            assertColumn(connection, "app_permission", "permission_name", "varchar", "NO", 100L);
            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'app_permission'
                  AND column_name IN ('code', 'name')
                """)).isZero();
            assertCheckClauseContains(connection, "ck_app_permission_revision", "permission_revision", ">", "0");

            assertColumn(connection, "app_role", "role_name", "varchar", "NO", 64L);
            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'app_role'
                  AND column_name = 'name'
                """)).isZero();
            assertCheckClauseContains(connection, "ck_app_role_revision", "role_revision", ">", "0");
            assertCheckClauseContains(connection, "ck_app_user_role_validity", "valid_until", ">", "valid_from");

            assertColumn(connection, "app_login_log", "result_code", "int", "NO", null);
            assertColumn(connection, "app_login_log", "failure_category", "varchar", "YES", 32L);
            assertColumn(connection, "app_login_log", "device_summary", "varchar", "YES", 255L);
            assertIndex(connection, "app_login_log", "idx_app_login_user_time", List.of("user_id", "occurred_at"));
            assertIndex(connection, "app_login_log", "idx_app_login_request", List.of("request_id"));

            assertColumn(connection, "app_security_audit", "resource_type", "varchar", "NO", 64L);
            assertIndex(connection, "app_security_audit", "idx_app_audit_resource",
                List.of("resource_type", "resource_id", "occurred_at"));
            assertIndex(connection, "app_security_audit", "idx_app_audit_actor",
                List.of("actor_type", "actor_id", "occurred_at"));

            String permissionUpdateTime = queryString(connection,
                "SELECT update_time FROM app_permission WHERE permission_id = 1000001");
            String roleUpdateTime = queryString(connection,
                "SELECT update_time FROM app_role WHERE role_id = 1000101");
            String menuUpdateTime = queryString(connection,
                "SELECT update_time FROM sys_menu WHERE menu_id = 1761400000000020000");

            Thread.sleep(1100);
            executeP0Migration(connection);

            assertThat(queryString(connection,
                "SELECT update_time FROM app_permission WHERE permission_id = 1000001"))
                .isEqualTo(permissionUpdateTime);
            assertThat(queryString(connection,
                "SELECT update_time FROM app_role WHERE role_id = 1000101"))
                .isEqualTo(roleUpdateTime);
            assertThat(queryString(connection,
                "SELECT update_time FROM sys_menu WHERE menu_id = 1761400000000020000"))
                .isEqualTo(menuUpdateTime);
        }
    }

    private void executeP0MigrationTwice(Connection connection) throws SQLException, IOException {
        executeP0Migration(connection);
        executeP0Migration(connection);
    }

    private void executeP0Migration(Connection connection) throws SQLException, IOException {
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
    }

    private static Path locateApiRoot() {
        List<Path> starts = new ArrayList<>();
        String mavenProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenProjectDirectory != null && !mavenProjectDirectory.isBlank()) {
            starts.add(Path.of(mavenProjectDirectory));
        }
        starts.add(Path.of(System.getProperty("user.dir")));

        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("无法定位包含 ../docs/sql/ry_vue.sql 的 ai-video-api 根目录");
    }

    private Connection connection() throws SQLException {
        return ENV.openMySqlConnection();
    }

    private static void executeSqlScript(Connection connection, Path script) throws SQLException, IOException {
        if (Files.notExists(script)) {
            throw new NoSuchFileException(script.toString());
        }
        ScriptUtils.executeSqlScript(
            connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8)
        );
    }

    private static String checkClause(Connection connection, String constraintName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT check_clause
            FROM information_schema.check_constraints
            WHERE constraint_schema = DATABASE()
              AND constraint_name = ?
            """)) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : "";
            }
        }
    }

    private static void assertCheckClauseContains(Connection connection, String constraintName,
                                                   String... expectedFragments) throws SQLException {
        String checkClause = checkClause(connection, constraintName);
        assertThat(checkClause).as("约束 %s", constraintName).isNotBlank();
        String normalizedClause = checkClause.toLowerCase(Locale.ROOT);
        for (String expectedFragment : expectedFragments) {
            assertThat(normalizedClause).as("约束 %s", constraintName)
                .contains(expectedFragment.toLowerCase(Locale.ROOT));
        }
    }

    private static void assertColumn(Connection connection, String tableName, String columnName,
                                     String dataType, String nullable, Long maximumLength) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT data_type, is_nullable, character_maximum_length
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
            """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("列 %s.%s", tableName, columnName).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo(dataType);
                assertThat(resultSet.getString("is_nullable")).isEqualTo(nullable);
                assertThat(resultSet.getObject("character_maximum_length", Long.class)).isEqualTo(maximumLength);
            }
        }
    }

    private static void assertIndex(Connection connection, String tableName, String indexName,
                                    List<String> expectedColumns) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND index_name = ?
            ORDER BY seq_in_index
            """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> actualColumns = new ArrayList<>();
                while (resultSet.next()) {
                    actualColumns.add(resultSet.getString(1));
                }
                assertThat(actualColumns).as("索引 %s.%s", tableName, indexName)
                    .containsExactlyElementsOf(expectedColumns);
            }
        }
    }

    private static void assertColumnDefault(Connection connection, String tableName, String columnName,
                                            String expectedDefault) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_default
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
            """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("列 %s.%s", tableName, columnName).isTrue();
                assertThat(resultSet.getString(1)).isEqualTo(expectedDefault);
            }
        }
    }

    private static long queryLong(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("查询应返回一行：%s", sql).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static Set<String> queryStringSet(Connection connection, String sql) throws SQLException {
        return Set.copyOf(queryStringList(connection, sql));
    }

    private static String queryString(Connection connection, String sql, Object... parameters) throws SQLException {
        List<String> values = queryStringList(connection, sql, parameters);
        assertThat(values).as("查询应返回一个字符串：%s", sql).hasSize(1);
        return values.getFirst();
    }

    private static List<String> queryStringList(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static void executeUpdate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }
}
