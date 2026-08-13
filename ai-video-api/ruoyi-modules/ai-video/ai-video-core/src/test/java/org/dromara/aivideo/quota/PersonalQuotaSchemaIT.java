package org.dromara.aivideo.quota;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 在专用 MySQL 8 测试库中验证个人积分账户迁移及权限修订语义。
 */
@Tag("dev")
class PersonalQuotaSchemaIT {

    private static final long ACTIVE_USER_ID = 9_100_000_000_000_001L;
    private static final long EXPIRED_USER_ID = 9_100_000_000_000_002L;
    private static final long PERSONAL_CREATOR_ROLE_ID = 1_000_101L;
    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private final Path apiRoot = locateApiRoot();

    @BeforeEach
    void reloadIdentityBaseline() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        try (Connection connection = connection()) {
            executeSqlScript(connection, apiRoot.resolve("../docs/sql/ry_vue.sql"));
            executeIdentityMigration(connection);
        }
    }

    @Test
    void createsOnlyPersonalAccountsAndAdvancesPermissionRevisionsExactlyOncePerMappingChange() throws Exception {
        try (Connection connection = connection()) {
            insertUser(connection, ACTIVE_USER_ID, "quota-active");
            insertUser(connection, EXPIRED_USER_ID, "quota-expired");
            insertUserRole(connection, ACTIVE_USER_ID, false);
            insertUserRole(connection, EXPIRED_USER_ID, true);

            long initialRoleRevision = queryLong(connection,
                "SELECT role_revision FROM app_role WHERE role_id = ?", PERSONAL_CREATOR_ROLE_ID);
            long initialActiveRevision = permissionRevision(connection, ACTIVE_USER_ID);
            long initialExpiredRevision = permissionRevision(connection, EXPIRED_USER_ID);

            executeQuotaAccountMigration(connection);

            assertThat(queryLong(connection, """
                SELECT COUNT(*)
                FROM app_role_permission rp
                INNER JOIN app_role r ON r.role_id = rp.role_id
                INNER JOIN app_permission p ON p.permission_id = rp.permission_id
                WHERE r.role_code = 'personal_creator'
                  AND p.permission_code = 'aivideo:quota:query'
                  AND rp.status = 'active'
                """)).isEqualTo(1L);
            assertThat(roleRevision(connection)).isEqualTo(initialRoleRevision + 1L);
            assertThat(permissionRevision(connection, ACTIVE_USER_ID)).isEqualTo(initialActiveRevision + 1L);
            assertThat(permissionRevision(connection, EXPIRED_USER_ID)).isEqualTo(initialExpiredRevision);

            executeQuotaAccountMigration(connection);

            assertThat(roleRevision(connection)).isEqualTo(initialRoleRevision + 1L);
            assertThat(permissionRevision(connection, ACTIVE_USER_ID)).isEqualTo(initialActiveRevision + 1L);
            assertThat(permissionRevision(connection, EXPIRED_USER_ID)).isEqualTo(initialExpiredRevision);

            executeUsedBalanceMigration(connection);
            executeUsedBalanceMigration(connection);

            assertColumn(connection, "used_balance", "bigint", "NO", "0");
            assertCheckClause(connection, "ck_av_quota_personal_subject", "subject_type", "=", "app_user");
            assertCheckClause(connection, "ck_av_quota_available_nonnegative", "available_balance", ">=", "0");
            assertCheckClause(connection, "ck_av_quota_locked_nonnegative", "locked_balance", ">=", "0");
            assertCheckClause(connection, "ck_av_quota_used_nonnegative", "used_balance", ">=", "0");
            assertIndex(connection, "uk_av_quota_subject_unit",
                List.of("tenant_id", "subject_type", "subject_id", "unit_code"));

            insertQuotaAccount(connection, 9_200_000_000_000_001L, ACTIVE_USER_ID, "app_user", 10L, 2L, 3L);
            assertThatThrownBy(() -> insertQuotaAccount(connection, 9_200_000_000_000_002L,
                ACTIVE_USER_ID, "app_user", 1L, 0L, 0L)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertQuotaAccount(connection, 9_200_000_000_000_003L,
                ACTIVE_USER_ID, "organization", 1L, 0L, 0L)).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertQuotaAccount(connection, 9_200_000_000_000_004L,
                EXPIRED_USER_ID, "app_user", -1L, 0L, 0L)).isInstanceOf(SQLException.class);

            executeUpdate(connection, """
                UPDATE app_role_permission rp
                INNER JOIN app_role r ON r.role_id = rp.role_id
                INNER JOIN app_permission p ON p.permission_id = rp.permission_id
                SET rp.status = 'inactive'
                WHERE r.role_code = 'personal_creator'
                  AND p.permission_code = 'aivideo:quota:query'
                """);
            executeQuotaAccountMigration(connection);

            assertThat(roleRevision(connection)).isEqualTo(initialRoleRevision + 2L);
            assertThat(permissionRevision(connection, ACTIVE_USER_ID)).isEqualTo(initialActiveRevision + 2L);
            assertThat(permissionRevision(connection, EXPIRED_USER_ID)).isEqualTo(initialExpiredRevision);
        }
    }

    @Test
    void failsClosedWhenTheReservedMappingIdBelongsToAnotherRelationship() throws Exception {
        try (Connection connection = connection()) {
            executeUpdate(connection, """
                INSERT INTO app_role_permission (
                    id, role_id, permission_id, status,
                    created_by_type, created_by_id, updated_by_type, updated_by_id
                ) VALUES (1000211, 1000102, 1000012, 'active',
                    'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001)
                """);

            assertThatThrownBy(() -> executeQuotaAccountMigration(connection))
                .isInstanceOf(ScriptStatementFailedException.class)
                .hasRootCauseInstanceOf(SQLException.class);

            assertThat(queryLong(connection, """
                SELECT COUNT(*) FROM app_role_permission
                WHERE role_id = 1000102 AND permission_id = 1000012 AND status = 'active'
                """)).isEqualTo(1L);
            assertThat(queryLong(connection, """
                SELECT COUNT(*) FROM app_role_permission
                WHERE role_id = 1000101 AND permission_id = 1000011
                """)).isZero();
        }
    }

    @Test
    void failsClosedWhenAnExistingUsedBalanceColumnHasTheWrongDefinition() throws Exception {
        try (Connection connection = connection()) {
            executeQuotaAccountMigration(connection);
            executeUpdate(connection, """
                ALTER TABLE av_quota_account
                ADD COLUMN used_balance INT NULL DEFAULT NULL AFTER locked_balance
                """);

            assertThatThrownBy(() -> executeUsedBalanceMigration(connection))
                .isInstanceOf(ScriptStatementFailedException.class)
                .hasRootCauseInstanceOf(SQLException.class);
        }
    }

    @Test
    void failsClosedWhenTheUsedBalanceCheckNameMasksAnotherExpression() throws Exception {
        try (Connection connection = connection()) {
            executeQuotaAccountMigration(connection);
            executeUsedBalanceMigration(connection);
            executeUpdate(connection, "ALTER TABLE av_quota_account DROP CHECK ck_av_quota_used_nonnegative");
            executeUpdate(connection, """
                ALTER TABLE av_quota_account
                ADD CONSTRAINT ck_av_quota_used_nonnegative CHECK (used_balance >= -1)
                """);

            assertThatThrownBy(() -> executeUsedBalanceMigration(connection))
                .isInstanceOf(ScriptStatementFailedException.class)
                .hasRootCauseInstanceOf(SQLException.class);
        }
    }

    private void executeIdentityMigration(Connection connection) throws Exception {
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
    }

    private void executeQuotaAccountMigration(Connection connection) throws Exception {
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_02_personal_quota_account.sql"));
    }

    private void executeUsedBalanceMigration(Connection connection) throws Exception {
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_03_quota_used_balance.sql"));
    }

    private static void insertUser(Connection connection, long userId, String username) throws SQLException {
        executeUpdate(connection, """
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id, del_flag
            ) VALUES (?, ?, ?, 'hash', ?, ?, 'active', 1, 1, 1,
                'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, '0')
            """, userId, username, username, userId + 100L, username);
    }

    private static void insertUserRole(Connection connection, long userId, boolean expired) throws SQLException {
        String validity = expired
            ? "DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)"
            : "NULL, NULL";
        executeUpdate(connection, """
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, 1000101, 'active', %s,
                'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001)
            """.formatted(validity), userId + 1_000L, userId);
    }

    private static void insertQuotaAccount(Connection connection, long id, long userId, String subjectType,
                                           long available, long locked, long used) throws SQLException {
        executeUpdate(connection, """
            INSERT INTO av_quota_account (
                id, tenant_id, subject_type, subject_id, unit_code,
                available_balance, locked_balance, used_balance, account_revision
            ) VALUES (?, ?, ?, ?, 'ai_text_credit', ?, ?, ?, 0)
            """, id, userId + 100L, subjectType, userId, available, locked, used);
    }

    private static long roleRevision(Connection connection) throws SQLException {
        return queryLong(connection, "SELECT role_revision FROM app_role WHERE role_code = 'personal_creator'");
    }

    private static long permissionRevision(Connection connection, long userId) throws SQLException {
        return queryLong(connection, "SELECT permission_revision FROM app_user WHERE user_id = ?", userId);
    }

    private static void assertColumn(Connection connection, String columnName, String dataType,
                                     String nullable, String defaultValue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT data_type, column_type, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'av_quota_account'
              AND column_name = ?
            """)) {
            statement.setString(1, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("data_type")).isEqualTo(dataType);
                assertThat(resultSet.getString("column_type").toLowerCase(Locale.ROOT)).doesNotContain("unsigned");
                assertThat(resultSet.getString("is_nullable")).isEqualTo(nullable);
                assertThat(resultSet.getString("column_default")).isEqualTo(defaultValue);
            }
        }
    }

    private static void assertCheckClause(Connection connection, String constraintName,
                                          String... expectedFragments) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT check_clause
            FROM information_schema.check_constraints
            WHERE constraint_schema = DATABASE()
              AND constraint_name = ?
            """)) {
            statement.setString(1, constraintName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                String clause = resultSet.getString(1).toLowerCase(Locale.ROOT);
                assertThat(clause).contains(expectedFragments);
            }
        }
    }

    private static void assertIndex(Connection connection, String indexName,
                                    List<String> expectedColumns) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'av_quota_account'
              AND index_name = ?
            ORDER BY seq_in_index
            """)) {
            statement.setString(1, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> actualColumns = new ArrayList<>();
                while (resultSet.next()) {
                    actualColumns.add(resultSet.getString(1));
                }
                assertThat(actualColumns).containsExactlyElementsOf(expectedColumns);
            }
        }
    }

    private static void executeUpdate(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            statement.executeUpdate();
        }
    }

    private static long queryLong(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private static void executeSqlScript(Connection connection, Path script) throws Exception {
        ScriptUtils.executeSqlScript(connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
    }

    private Connection connection() throws SQLException {
        return ENV.openMySqlConnection();
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
        throw new IllegalStateException("无法定位 ai-video-api 根目录");
    }
}
