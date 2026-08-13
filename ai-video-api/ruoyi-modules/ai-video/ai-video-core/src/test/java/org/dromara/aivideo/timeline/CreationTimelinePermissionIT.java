package org.dromara.aivideo.timeline;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreationTimelinePermissionIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final Path API_ROOT = locateApiRoot();
    private static final Path MIGRATION = API_ROOT.resolve(
        "../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql");
    private static final long TEST_USER_ID = 900008082L;
    private static final long CONFLICT_PERMISSION_ID = 1900026L;
    private static final long CONFLICT_BINDING_ID = 1900230L;
    private static final List<String> TABLES = List.of(
        "av_creation_asset",
        "av_creation_project",
        "av_timeline_draft",
        "av_timeline_version",
        "av_timeline_asset_ref",
        "av_timeline_write_receipt",
        "av_ai_task",
        "av_ai_task_execution",
        "av_ai_task_attempt"
    );
    private static final List<ExpectedPermission> EXPECTED = List.of(
        new ExpectedPermission(1000025L, 1000225L, "aivideo:creation:query"),
        new ExpectedPermission(1000026L, 1000226L, "aivideo:creation:edit"),
        new ExpectedPermission(1000027L, 1000227L, "aivideo:creation:generate"),
        new ExpectedPermission(1000028L, 1000228L, "aivideo:creation-asset:query"),
        new ExpectedPermission(1000029L, 1000229L, "aivideo:creation-asset:upload"),
        new ExpectedPermission(1000030L, 1000230L, "aivideo:creation-asset:delete"),
        new ExpectedPermission(1000031L, 1000231L, "aivideo:task:retry")
    );

    private Connection connection;
    private RoleAuditSnapshot classRoleBefore;
    private List<UserAuditSnapshot> classUsersBefore;
    private RoleAuditSnapshot roleBefore;
    private List<UserAuditSnapshot> usersBefore;
    private long groupConcatMaxLenBefore;

    @BeforeAll
    void prepareClassFixture() throws Exception {
        connection = ENV.openMySqlConnection();
        ensurePublicBaseline();
        classRoleBefore = readRoleAuditSnapshot();
        classUsersBefore = readAffectedUserAuditSnapshots();
        assertThat(reservedPermissionFacts()).isEmpty();
        assertThat(reservedBindingFacts()).isEmpty();
        assertThat(queryLong(targetTableCountSql())).isZero();
        insertEffectivePersonalCreator(connection);
        connection.close();
        connection = null;
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = ENV.openMySqlConnection();
        assertThat(reservedPermissionFacts()).isEmpty();
        assertThat(reservedBindingFacts()).isEmpty();
        roleBefore = readRoleAuditSnapshot();
        usersBefore = readAffectedUserAuditSnapshots();
        groupConcatMaxLenBefore = queryLong("SELECT @@SESSION.group_concat_max_len");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            try {
                reopenConnection();
                cleanupCreatedFacts();
            } finally {
                connection.close();
                connection = null;
            }
        }
    }

    @AfterAll
    void cleanupClassFixture() throws Exception {
        if (connection == null || connection.isClosed()) {
            connection = ENV.openMySqlConnection();
        }
        try {
            cleanupConflictFacts();
            dropTargetTables();
            cleanupTestUser();
            restoreAuditSnapshots(classRoleBefore, classUsersBefore);
            assertThat(reservedPermissionFacts()).isEmpty();
            assertThat(reservedBindingFacts()).isEmpty();
            assertThat(readRoleAuditSnapshot()).isEqualTo(classRoleBefore);
            assertThat(readAffectedUserAuditSnapshots()).containsExactlyElementsOf(classUsersBefore);
        } finally {
            connection.close();
            connection = null;
        }
    }

    @Test
    @Order(1)
    void grantsExactPermissionsOnceWithoutChangingExistingTaskPermissions() throws Exception {
        String taskQueryBefore = permissionFingerprint("aivideo:task:query");
        String taskCancelBefore = permissionFingerprint("aivideo:task:cancel");

        execute(connection, MIGRATION);

        assertSessionStateClean();
        RoleAuditSnapshot roleAfterFirstRun = readRoleAuditSnapshot();
        List<UserAuditSnapshot> usersAfterFirstRun = readAffectedUserAuditSnapshots();
        for (ExpectedPermission expected : EXPECTED) {
            assertThat(queryLong("""
                SELECT COUNT(*)
                FROM app_permission permission
                JOIN app_role_permission binding ON binding.permission_id = permission.permission_id
                WHERE permission.permission_id = ?
                  AND permission.permission_code = ?
                  AND permission.status = 'active'
                  AND binding.id = ?
                  AND binding.role_id = 1000101
                  AND binding.status = 'active'
                """, expected.permissionId(), expected.permissionCode(), expected.bindingId()))
                .isEqualTo(1L);
        }
        assertThat(roleAfterFirstRun.roleRevision()).isEqualTo(roleBefore.roleRevision() + 1);
        Map<Long, UserAuditSnapshot> usersAfterFirstRunById = indexUsers(usersAfterFirstRun);
        for (UserAuditSnapshot before : usersBefore) {
            assertThat(usersAfterFirstRunById.get(before.userId()).permissionRevision())
                .isEqualTo(before.permissionRevision() + 1);
        }
        assertThat(permissionFingerprint("aivideo:task:query")).isEqualTo(taskQueryBefore);
        assertThat(permissionFingerprint("aivideo:task:cancel")).isEqualTo(taskCancelBefore);

        execute(connection, MIGRATION);

        assertSessionStateClean();
        assertThat(queryLong("SELECT COUNT(*) FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031"))
            .isEqualTo(7L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231"))
            .isEqualTo(7L);
        assertThat(readRoleAuditSnapshot()).isEqualTo(roleAfterFirstRun);
        assertThat(readAffectedUserAuditSnapshots()).containsExactlyElementsOf(usersAfterFirstRun);
        assertThat(permissionFingerprint("aivideo:task:query")).isEqualTo(taskQueryBefore);
        assertThat(permissionFingerprint("aivideo:task:cancel")).isEqualTo(taskCancelBefore);
    }

    @Test
    @Order(2)
    void allReservedPermissionAndBindingConflictsFailClosedWithoutPartialGrant() throws Exception {
        createTargetTablesFromMigration();
        String taskQueryBefore = permissionFingerprint("aivideo:task:query");
        String taskCancelBefore = permissionFingerprint("aivideo:task:cancel");

        assertConflictFailsClosed(() -> executeUpdate("""
            INSERT INTO app_permission (
                permission_id, permission_code, permission_name, resource_type, action,
                permission_revision, status, created_by_type, created_by_id,
                updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (1000025, 'aivideo:creation:wrong', '冲突权限', 'creation', 'wrong',
                      1, 'active', 'sys_user', 1761100000000000001,
                      'sys_user', 1761100000000000001, NOW(), NOW())
            """), taskQueryBefore, taskCancelBefore);
        assertConflictFailsClosed(() -> executeUpdate("""
            INSERT INTO app_permission (
                permission_id, permission_code, permission_name, resource_type, action,
                permission_revision, status, created_by_type, created_by_id,
                updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (?, 'aivideo:creation:edit', '冲突权限码', 'creation', 'wrong',
                      1, 'active', 'sys_user', 1761100000000000001,
                      'sys_user', 1761100000000000001, NOW(), NOW())
            """, CONFLICT_PERMISSION_ID), taskQueryBefore, taskCancelBefore);
        assertConflictFailsClosed(() -> executeUpdate("""
            INSERT INTO app_role_permission (
                id, role_id, permission_id, status, created_by_type, created_by_id,
                updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (1000227, 1000101, 1000009, 'active', 'sys_user', 1761100000000000001,
                      'sys_user', 1761100000000000001, NOW(), NOW())
            """), taskQueryBefore, taskCancelBefore);
        assertConflictFailsClosed(() -> {
            executeUpdate("""
                INSERT INTO app_permission (
                    permission_id, permission_code, permission_name, resource_type, action,
                    permission_revision, status, created_by_type, created_by_id,
                    updated_by_type, updated_by_id, create_time, update_time
                ) VALUES (1000030, 'aivideo:creation-asset:delete', 'binding-conflict-prerequisite',
                          'creation-asset', 'delete', 1, 'active', 'sys_user', 1761100000000000001,
                          'sys_user', 1761100000000000001, NOW(), NOW())
                """);
            executeUpdate("""
                INSERT INTO app_role_permission (
                    id, role_id, permission_id, status, created_by_type, created_by_id,
                    updated_by_type, updated_by_id, create_time, update_time
                ) VALUES (?, 1000101, 1000030, 'active', 'sys_user', 1761100000000000001,
                          'sys_user', 1761100000000000001, NOW(), NOW())
                """, CONFLICT_BINDING_ID);
        }, taskQueryBefore, taskCancelBefore);

        execute(connection, MIGRATION);
        assertSessionStateClean();
        assertThat(reservedPermissionFacts()).hasSize(7);
        assertThat(reservedBindingFacts()).hasSize(7);
        assertThat(readRoleAuditSnapshot().roleRevision()).isEqualTo(roleBefore.roleRevision() + 1);
        Map<Long, UserAuditSnapshot> recoveredUsers = indexUsers(readAffectedUserAuditSnapshots());
        for (UserAuditSnapshot before : usersBefore) {
            assertThat(recoveredUsers.get(before.userId()).permissionRevision())
                .isEqualTo(before.permissionRevision() + 1);
        }
    }

    private void assertConflictFailsClosed(CheckedSqlRunnable arrange, String taskQueryBefore,
                                           String taskCancelBefore) throws Exception {
        arrange.run();
        List<String> permissionsBefore = reservedPermissionFacts();
        List<String> bindingsBefore = reservedBindingFacts();

        assertThatThrownBy(() -> execute(connection, MIGRATION)).isInstanceOf(RuntimeException.class);

        assertThat(reservedPermissionFacts()).containsExactlyElementsOf(permissionsBefore);
        assertThat(reservedBindingFacts()).containsExactlyElementsOf(bindingsBefore);
        assertThat(permissionFingerprint("aivideo:task:query")).isEqualTo(taskQueryBefore);
        assertThat(permissionFingerprint("aivideo:task:cancel")).isEqualTo(taskCancelBefore);
        assertSessionStateClean();
        assertAuditSnapshotsUnchanged();

        cleanupConflictFacts();
        assertThat(reservedPermissionFacts()).isEmpty();
        assertThat(reservedBindingFacts()).isEmpty();
        assertAuditSnapshotsUnchanged();
    }

    private void assertSessionStateClean() throws SQLException {
        assertThat(queryLong("SELECT @@SESSION.group_concat_max_len")).isEqualTo(groupConcatMaxLenBefore);
        assertTemporaryTableNameAvailable("tmp_creation_timeline_ddl_guard");
        assertTemporaryTableNameAvailable("tmp_creation_timeline_permissions");
        assertTemporaryTableNameAvailable("tmp_creation_timeline_permission_guard");
    }

    private void assertTemporaryTableNameAvailable(String table) throws SQLException {
        executeUpdate("CREATE TEMPORARY TABLE `" + table + "` (probe TINYINT NOT NULL)");
        executeUpdate("DROP TEMPORARY TABLE `" + table + "`");
    }

    private String permissionFingerprint(String permissionCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT CONCAT(permission_id, '|', permission_code, '|', permission_name, '|', resource_type, '|',
                          action, '|', permission_revision, '|', status, '|', created_by_type, '|', created_by_id,
                          '|', updated_by_type, '|', updated_by_id, '|', create_time, '|', update_time)
            FROM app_permission WHERE permission_code = ?
            """)) {
            statement.setString(1, permissionCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private List<String> reservedPermissionFacts() throws SQLException {
        return queryStrings("""
            SELECT CONCAT_WS('|', permission_id, permission_code, permission_name, resource_type, action,
                             permission_revision, status, created_by_type, created_by_id,
                             updated_by_type, updated_by_id, create_time, update_time)
            FROM app_permission
            WHERE permission_id BETWEEN 1000025 AND 1000031
               OR permission_id = 1900026
               OR permission_code IN ('aivideo:creation:query','aivideo:creation:edit',
                                      'aivideo:creation:generate','aivideo:creation-asset:query',
                                      'aivideo:creation-asset:upload','aivideo:creation-asset:delete',
                                      'aivideo:task:retry')
            ORDER BY permission_id
            """);
    }

    private List<String> reservedBindingFacts() throws SQLException {
        return queryStrings("""
            SELECT CONCAT_WS('|', id, role_id, permission_id, status, created_by_type, created_by_id,
                             updated_by_type, updated_by_id, create_time, update_time)
            FROM app_role_permission
            WHERE id BETWEEN 1000225 AND 1000231
               OR id = 1900230
               OR (role_id = 1000101 AND permission_id BETWEEN 1000025 AND 1000031)
            ORDER BY id
            """);
    }

    private void cleanupConflictFacts() throws SQLException {
        executeUpdate("DELETE FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231 "
            + "OR id = ? OR permission_id BETWEEN 1000025 AND 1000031 "
            + "OR permission_id = ? OR permission_id IN (SELECT permission_id FROM app_permission "
            + "WHERE permission_code IN ('aivideo:creation:query','aivideo:creation:edit',"
            + "'aivideo:creation:generate','aivideo:creation-asset:query','aivideo:creation-asset:upload',"
            + "'aivideo:creation-asset:delete','aivideo:task:retry'))",
            CONFLICT_BINDING_ID, CONFLICT_PERMISSION_ID);
        executeUpdate("DELETE FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031 "
            + "OR permission_id = ? OR permission_code IN ('aivideo:creation:query','aivideo:creation:edit',"
            + "'aivideo:creation:generate','aivideo:creation-asset:query','aivideo:creation-asset:upload',"
            + "'aivideo:creation-asset:delete','aivideo:task:retry')", CONFLICT_PERMISSION_ID);
    }

    private void cleanupCreatedFacts() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        }
        cleanupConflictFacts();
        restoreAuditSnapshots();
        assertAuditSnapshotsUnchanged();
    }

    private void ensurePublicBaseline() throws SQLException {
        long tableCount = queryLong("SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'");
        if (tableCount == 0L) {
            executeBaseline(connection);
        }
        assertThat(queryLong("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
            + "AND table_name IN ('app_role','app_permission','app_role_permission','app_user','app_user_role')"))
            .isEqualTo(5L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_role WHERE role_id = 1000101 "
            + "AND role_code = 'personal_creator' AND scope_type = 'personal' "
            + "AND status = 'active' AND del_flag = '0'"))
            .isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_permission "
            + "WHERE permission_code IN ('aivideo:task:query','aivideo:task:cancel')"))
            .isEqualTo(2L);
    }

    private void cleanupTestUser() throws SQLException {
        executeUpdate("DELETE FROM app_user_role WHERE id = 990008082 OR user_id = ?", TEST_USER_ID);
        executeUpdate("DELETE FROM app_user WHERE user_id = ? "
            + "OR username_normalized = 'timeline-permission-user'", TEST_USER_ID);
    }

    private void dropTargetTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES.reversed()) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
        }
    }

    private static String targetTableCountSql() {
        return "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
            + "AND table_name IN ('" + String.join("','", TABLES) + "')";
    }

    private void createTargetTablesFromMigration() throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        for (String table : TABLES) {
            Matcher matcher = Pattern.compile("(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+"
                + Pattern.quote(table) + "\\s*\\(.*?\\)\\s*ENGINE\\s*=\\s*InnoDB.*?;").matcher(sql);
            assertThat(matcher.find()).isTrue();
            executeUpdate(matcher.group().replaceFirst(";\\s*$", ""));
        }
    }

    private RoleAuditSnapshot readRoleAuditSnapshot() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("""
            SELECT role_revision, updated_by_type, updated_by_id, update_time
            FROM app_role WHERE role_id = 1000101
            """)) {
            assertThat(resultSet.next()).isTrue();
            return new RoleAuditSnapshot(resultSet.getLong(1), resultSet.getString(2),
                resultSet.getObject(3, Long.class), resultSet.getObject(4, LocalDateTime.class));
        }
    }

    private List<UserAuditSnapshot> readAffectedUserAuditSnapshots() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("""
            SELECT DISTINCT app_user.user_id, app_user.permission_revision, app_user.updated_by_type,
                            app_user.updated_by_id, app_user.update_time
            FROM app_user app_user
            JOIN app_user_role app_user_role
              ON app_user_role.user_id = app_user.user_id AND app_user_role.role_id = 1000101
            WHERE app_user.status = 'active' AND app_user.del_flag = '0'
              AND app_user_role.status = 'active'
              AND (app_user_role.valid_from IS NULL OR app_user_role.valid_from <= NOW())
              AND (app_user_role.valid_until IS NULL OR app_user_role.valid_until > NOW())
            ORDER BY app_user.user_id
            """)) {
            List<UserAuditSnapshot> snapshots = new ArrayList<>();
            while (resultSet.next()) {
                snapshots.add(new UserAuditSnapshot(resultSet.getLong(1), resultSet.getLong(2),
                    resultSet.getString(3), resultSet.getObject(4, Long.class),
                    resultSet.getObject(5, LocalDateTime.class)));
            }
            return snapshots;
        }
    }

    private void restoreAuditSnapshots() throws SQLException {
        restoreAuditSnapshots(roleBefore, usersBefore);
    }

    private void restoreAuditSnapshots(RoleAuditSnapshot roleSnapshot,
                                       List<UserAuditSnapshot> userSnapshots) throws SQLException {
        executeUpdate("UPDATE app_role SET role_revision = ?, updated_by_type = ?, updated_by_id = ?, "
                + "update_time = ? WHERE role_id = 1000101",
            roleSnapshot.roleRevision(), roleSnapshot.updatedByType(), roleSnapshot.updatedById(),
            roleSnapshot.updateTime());
        for (UserAuditSnapshot snapshot : userSnapshots) {
            executeUpdate("UPDATE app_user SET permission_revision = ?, updated_by_type = ?, updated_by_id = ?, "
                    + "update_time = ? WHERE user_id = ?",
                snapshot.permissionRevision(), snapshot.updatedByType(), snapshot.updatedById(),
                snapshot.updateTime(), snapshot.userId());
        }
    }

    private void assertAuditSnapshotsUnchanged() throws SQLException {
        assertThat(readRoleAuditSnapshot()).isEqualTo(roleBefore);
        assertThat(readAffectedUserAuditSnapshots()).containsExactlyElementsOf(usersBefore);
    }

    private static Map<Long, UserAuditSnapshot> indexUsers(List<UserAuditSnapshot> users) {
        Map<Long, UserAuditSnapshot> indexed = new LinkedHashMap<>();
        for (UserAuditSnapshot user : users) {
            indexed.put(user.userId(), user);
        }
        return indexed;
    }

    private void reopenConnection() throws SQLException {
        connection.close();
        connection = ENV.openMySqlConnection();
    }

    private long queryLong(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                values.add(resultSet.getString(1));
            }
        }
        return values;
    }

    private void executeUpdate(String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private static void insertEffectivePersonalCreator(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, must_change_password, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time, del_flag
            ) VALUES (?, 'timeline-permission-user', 'timeline-permission-user', 'test-only-hash', ?,
                      'Timeline Permission User', 'active', 0, 1, 1, 11,
                      'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0')
            """)) {
            statement.setLong(1, TEST_USER_ID);
            statement.setLong(2, TEST_USER_ID);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (990008082, ?, 1000101, 'active', ?, ?, 'sys_user', 1761100000000000001,
                      'sys_user', 1761100000000000001, NOW(), NOW())
            """)) {
            statement.setLong(1, TEST_USER_ID);
            statement.setObject(2, LocalDateTime.now().minusMinutes(1));
            statement.setObject(3, LocalDateTime.now().plusMinutes(10));
            statement.executeUpdate();
        }
    }

    private static void executeBaseline(Connection connection) {
        execute(connection, API_ROOT.resolve("../docs/sql/ry_vue.sql"));
        execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
        execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260803_01_user_portrait.sql"));
        execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql"));
        execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260804_01_voice_delete_permission.sql"));
    }

    private static void execute(Connection connection, Path script) {
        ScriptUtils.executeSqlScript(connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
    }

    private static Path locateApiRoot() {
        List<Path> starts = List.of(
            Path.of(System.getProperty("maven.multiModuleProjectDirectory", "")),
            Path.of(System.getProperty("user.dir"))
        );
        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }

    private record ExpectedPermission(long permissionId, long bindingId, String permissionCode) {
    }

    private record RoleAuditSnapshot(long roleRevision, String updatedByType, Long updatedById,
                                     LocalDateTime updateTime) {
    }

    private record UserAuditSnapshot(long userId, long permissionRevision, String updatedByType, Long updatedById,
                                     LocalDateTime updateTime) {
    }

    @FunctionalInterface
    private interface CheckedSqlRunnable {
        void run() throws SQLException;
    }
}
