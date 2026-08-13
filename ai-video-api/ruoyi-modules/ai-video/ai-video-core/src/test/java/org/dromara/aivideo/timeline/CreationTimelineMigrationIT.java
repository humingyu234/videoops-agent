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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreationTimelineMigrationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final Path API_ROOT = locateApiRoot();
    private static final Path MIGRATION = API_ROOT.resolve(
        "../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql");
    private static final long TEST_USER_ID = 900008081L;
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
    private static final Map<String, String> EXPECTED_HASHES = Map.ofEntries(
        Map.entry("av_creation_asset", "f582f721fc1d10dccbcaf4baf0f43655091f9e1199e6d637df8ab3ef28f4155c"),
        Map.entry("av_creation_project", "00dd171a5ce335bece21d76fa236185a0b10c43436c4560679278a1e6119c620"),
        Map.entry("av_timeline_draft", "d61aa055f9d459c3900e2e6bcf66d773edf88f2bbf63240b418a202d93f5e2ea"),
        Map.entry("av_timeline_version", "b264e284c0b4491713d378cf89fb0fbcbcf55fd6fa139385f9cecd9e295e3ab6"),
        Map.entry("av_timeline_asset_ref", "193f89e81c367e37356058e289e42f5834c5cc76b3f8e1a35770ff066b6d25bc"),
        Map.entry("av_timeline_write_receipt", "af86ecc97b7e313b5978455a065c6f1520f36cb6d215f5fc2862288e8d2da0ff"),
        Map.entry("av_ai_task", "9939c095bd43ea1693832b8b6ea2c9360ba25f979efcd747947ec498a08750e5"),
        Map.entry("av_ai_task_execution", "d1ec85075b0946df506e34312962c1a967a99201e2342e87a5877d73525c297e"),
        Map.entry("av_ai_task_attempt", "844076998f3d2400b2097783da23ea8741e908932b40461499948c0fa3ad8b50")
    );
    private static final Map<String, List<String>> EXPECTED_CHECKS = expectedChecks();

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
        assertCleanBaseline();
        assertThat(queryLong(targetTableCountSql())).isZero();
        insertEffectivePersonalCreator(connection);
        connection.close();
        connection = null;
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = ENV.openMySqlConnection();
        assertCleanBaseline();
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
            cleanupReservedPermissionFacts();
            dropTargetTables();
            cleanupTestUser();
            restoreAuditSnapshots(classRoleBefore, classUsersBefore);
            assertThat(queryLong(targetTableCountSql())).isZero();
            assertThat(readRoleAuditSnapshot()).isEqualTo(classRoleBefore);
            assertThat(readAffectedUserAuditSnapshots()).containsExactlyElementsOf(classUsersBefore);
        } finally {
            connection.close();
            connection = null;
        }
    }

    @Test
    @Order(1)
    void createsExactSchemaEnforcesTaskResultsAndReplayIsStable() throws Exception {
        execute(connection, MIGRATION);

        assertGroupConcatMaxLenUnchanged();
        assertSchemaContract();
        assertPermissionFactsCreatedOnce();
        assertAuditRevisionsIncreasedOnce();
        String fingerprint = schemaFingerprint();

        execute(connection, MIGRATION);

        assertGroupConcatMaxLenUnchanged();
        assertThat(schemaFingerprint()).isEqualTo(fingerprint);
        assertPermissionFactsCreatedOnce();
        assertAuditRevisionsIncreasedOnce();
        assertAiTaskDataContract();
    }

    @Test
    @Order(2)
    void sameColumnCountSchemaDriftFailsBeforeAnyCreateOrPermissionWrite() throws Exception {
        executeUpdate("DROP TABLE IF EXISTS av_creation_asset");
        long otherTargetTableCount = queryLong(targetTableCountSql());
        createTargetTableFromMigration("av_creation_asset");
        executeUpdate("""
            ALTER TABLE av_creation_asset
                MODIFY owner_user_id BIGINT NULL,
                MODIFY has_video_stream TINYINT NOT NULL DEFAULT 1,
                DROP INDEX idx_av_creation_asset_owner_status,
                DROP CHECK ck_av_creation_asset_status
            """);
        assertThat(queryLong("SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'av_creation_asset'"))
            .isEqualTo(25L);

        assertThatThrownBy(() -> execute(connection, MIGRATION)).isInstanceOf(RuntimeException.class);

        assertGroupConcatMaxLenUnchanged();
        assertThat(queryLong(targetTableCountSql())).isEqualTo(otherTargetTableCount + 1L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031"))
            .isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231"))
            .isZero();
        assertAuditSnapshotsUnchanged();

        executeUpdate("DROP TABLE av_creation_asset");
        execute(connection, MIGRATION);
        assertGroupConcatMaxLenUnchanged();
        assertSchemaContract();
        String fingerprint = schemaFingerprint();
        execute(connection, MIGRATION);
        assertGroupConcatMaxLenUnchanged();
        assertThat(schemaFingerprint()).isEqualTo(fingerprint);
    }

    private void assertCleanBaseline() throws SQLException {
        assertThat(queryLong("SELECT COUNT(*) FROM app_role WHERE role_id = 1000101 "
            + "AND role_code = 'personal_creator' AND scope_type = 'personal' "
            + "AND status = 'active' AND del_flag = '0'"))
            .isEqualTo(1L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031 "
            + "OR permission_code IN ('aivideo:creation:query','aivideo:creation:edit',"
            + "'aivideo:creation:generate','aivideo:creation-asset:query','aivideo:creation-asset:upload',"
            + "'aivideo:creation-asset:delete','aivideo:task:retry')"))
            .isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231 "
            + "OR (role_id = 1000101 AND permission_id BETWEEN 1000025 AND 1000031)"))
            .isZero();
    }

    private void assertSchemaContract() throws SQLException {
        assertThat(queryStrings("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('av_creation_asset','av_creation_project','av_timeline_draft',
                                 'av_timeline_version','av_timeline_asset_ref','av_timeline_write_receipt',
                                 'av_ai_task','av_ai_task_execution','av_ai_task_attempt')
            ORDER BY table_name
            """)).containsExactlyElementsOf(TABLES.stream().sorted().toList());
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name IN ('av_creation_asset','av_creation_project','av_timeline_draft',
                                 'av_timeline_version','av_timeline_asset_ref','av_timeline_write_receipt',
                                 'av_ai_task','av_ai_task_execution','av_ai_task_attempt')
              AND referenced_table_name IS NOT NULL
            """)).isZero();
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name IN ('av_creation_asset','av_creation_project','av_timeline_draft',
                                 'av_timeline_version','av_timeline_asset_ref','av_timeline_write_receipt',
                                 'av_ai_task','av_ai_task_execution','av_ai_task_attempt')
              AND column_name IN ('tenant_id','workspace_id')
            """)).isZero();

        assertThat(structureHashes()).containsExactlyInAnyOrderEntriesOf(EXPECTED_HASHES);
        for (String table : TABLES) {
            assertThat(checkNames(table)).as("CHECK constraints for %s", table)
                .containsExactlyElementsOf(EXPECTED_CHECKS.get(table));
        }
    }

    private void assertPermissionFactsCreatedOnce() throws SQLException {
        assertThat(queryLong("SELECT COUNT(*) FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031"))
            .isEqualTo(7L);
        assertThat(queryLong("SELECT COUNT(*) FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231"))
            .isEqualTo(7L);
    }

    private void assertAuditRevisionsIncreasedOnce() throws SQLException {
        assertThat(readRoleAuditSnapshot().roleRevision()).isEqualTo(roleBefore.roleRevision() + 1);
        Map<Long, UserAuditSnapshot> current = indexUsers(readAffectedUserAuditSnapshots());
        for (UserAuditSnapshot before : usersBefore) {
            assertThat(current.get(before.userId()).permissionRevision())
                .isEqualTo(before.permissionRevision() + 1);
        }
    }

    private void assertAiTaskDataContract() throws SQLException {
        long taskId = 910000000L;
        for (String status : List.of("pending", "queued", "running", "failed", "cancelled")) {
            insertTask(taskId++, "timeline_render", status, null, null, null, "ok");
        }
        insertTask(taskId++, "timeline_render", "success", 880000001L, null, null, "ok");
        for (String type : List.of(
            "timeline_image_prompt_generate", "timeline_fancy_text_suggest", "timeline_subtitle_align")) {
            insertTask(taskId++, type, "success", null, "timeline-result-1", "ok", "ok");
        }

        for (String status : List.of("pending", "queued", "running", "failed", "cancelled")) {
            long rejectedId = taskId++;
            assertThatThrownBy(() -> insertTask(
                rejectedId, "timeline_render", status, 880000002L, null, null, "ok"))
                .isInstanceOf(SQLException.class);
        }
        for (String type : List.of(
            "timeline_image_prompt_generate", "timeline_fancy_text_suggest", "timeline_subtitle_align")) {
            long rejectedId = taskId++;
            assertThatThrownBy(() -> insertTask(rejectedId, type, "success", null, null, null, "ok"))
                .isInstanceOf(SQLException.class);
        }
        long renderWithoutAsset = taskId++;
        assertThatThrownBy(() -> insertTask(
            renderWithoutAsset, "timeline_render", "success", null, null, null, "ok"))
            .isInstanceOf(SQLException.class);
        long renderWithPayload = taskId++;
        assertThatThrownBy(() -> insertTask(
            renderWithPayload, "timeline_render", "success", 880000003L, "wrong", "wrong", "ok"))
            .isInstanceOf(SQLException.class);
        long unsupportedSuccess = taskId++;
        assertThatThrownBy(() -> insertTask(
            unsupportedSuccess, "timeline_unknown", "success", null, null, null, "ok"))
            .isInstanceOf(SQLException.class);

        String exactJsonValue = jsonValueForTotalBytes(65536);
        String oversizedJsonValue = jsonValueForTotalBytes(65537);
        assertThat(queryLong("SELECT OCTET_LENGTH(CAST(JSON_OBJECT('v', ?) AS CHAR))", exactJsonValue))
            .isEqualTo(65536L);
        assertThat(queryLong("SELECT OCTET_LENGTH(CAST(JSON_OBJECT('v', ?) AS CHAR))", oversizedJsonValue))
            .isEqualTo(65537L);
        insertTask(taskId++, "timeline_render", "pending", null, null, null, exactJsonValue);
        long oversizedRequest = taskId++;
        assertThatThrownBy(() -> insertTask(
            oversizedRequest, "timeline_render", "pending", null, null, null, oversizedJsonValue))
            .isInstanceOf(SQLException.class);
        insertTask(taskId++, "timeline_fancy_text_suggest", "success", null,
            "timeline-result-1", exactJsonValue, "ok");
        long oversizedResult = taskId;
        assertThatThrownBy(() -> insertTask(oversizedResult, "timeline_fancy_text_suggest", "success", null,
            "timeline-result-1", oversizedJsonValue, "ok"))
            .isInstanceOf(SQLException.class);
    }

    private void insertTask(long taskId, String taskType, String taskStatus, Long resultAssetId,
                            String resultSchemaVersion, String resultValue, String requestValue) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO av_ai_task (
                task_id, owner_user_id, task_type, resource_type, resource_id, input_version_id,
                idempotency_key, request_digest, request_schema_version, request_payload_json,
                task_status, stage, result_asset_id, result_schema_version, result_payload_json,
                quota_policy_version, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 'creation_project', 1, NULL, ?, ?, 'timeline-request-1', JSON_OBJECT('v', ?),
                      ?, 'accepted', ?, ?, IF(? IS NULL, NULL, JSON_OBJECT('v', ?)),
                      'timeline-free-1', 'app_user', ?, ?, ?)
            """)) {
            int index = 1;
            statement.setLong(index++, taskId);
            statement.setLong(index++, TEST_USER_ID);
            statement.setString(index++, taskType);
            statement.setString(index++, "task-it-" + taskId);
            statement.setString(index++, "a".repeat(64));
            statement.setString(index++, requestValue);
            statement.setString(index++, taskStatus);
            statement.setObject(index++, resultAssetId);
            statement.setString(index++, resultSchemaVersion);
            statement.setString(index++, resultValue);
            statement.setString(index++, resultValue);
            statement.setLong(index++, TEST_USER_ID);
            statement.setLong(index++, TEST_USER_ID);
            statement.setLong(index, TEST_USER_ID);
            statement.executeUpdate();
        }
    }

    private String jsonValueForTotalBytes(int targetBytes) throws SQLException {
        int baseBytes = Math.toIntExact(queryLong(
            "SELECT OCTET_LENGTH(CAST(JSON_OBJECT('v', '') AS CHAR))"));
        int valueBytes = targetBytes - baseBytes;
        assertThat(valueBytes).isPositive();
        return "界".repeat(valueBytes / 3) + "a".repeat(valueBytes % 3);
    }

    private String schemaFingerprint() throws SQLException {
        List<String> hashes = new ArrayList<>();
        structureHashes().forEach((table, hash) -> hashes.add(table + "=" + hash));
        return String.join("\n", hashes);
    }

    private Map<String, String> structureHashes() throws SQLException {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String table : TABLES) {
            hashes.put(table, structureHash(table));
        }
        return hashes;
    }

    private String structureHash(String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SHOW CREATE TABLE `" + table + "`")) {
            assertThat(resultSet.next()).isTrue();
            String normalized = resultSet.getString(2)
                .replace("\r\n", "\n")
                .replaceAll("(?i)\\sAUTO_INCREMENT=\\d+", "")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
            return sha256(normalized);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private void assertGroupConcatMaxLenUnchanged() throws SQLException {
        assertThat(queryLong("SELECT @@SESSION.group_concat_max_len")).isEqualTo(groupConcatMaxLenBefore);
    }

    private List<String> checkNames(String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT check_constraint.constraint_name
            FROM information_schema.table_constraints table_constraint
            JOIN information_schema.check_constraints check_constraint
              ON check_constraint.constraint_schema = table_constraint.constraint_schema
             AND check_constraint.constraint_name = table_constraint.constraint_name
            WHERE table_constraint.constraint_schema = DATABASE()
              AND table_constraint.table_schema = DATABASE()
              AND table_constraint.table_name = ?
              AND table_constraint.constraint_type = 'CHECK'
            ORDER BY check_constraint.constraint_name
            """)) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        }
    }

    private void createTargetTableFromMigration(String table) throws Exception {
        String sql = Files.readString(MIGRATION, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+"
            + Pattern.quote(table) + "\\s*\\(.*?\\)\\s*ENGINE\\s*=\\s*InnoDB.*?;").matcher(sql);
        assertThat(matcher.find()).isTrue();
        executeUpdate(matcher.group().replaceFirst(";\\s*$", ""));
    }

    private void cleanupCreatedFacts() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        }
        cleanupReservedPermissionFacts();
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

    private void cleanupReservedPermissionFacts() throws SQLException {
        executeUpdate("DELETE FROM app_role_permission WHERE id BETWEEN 1000225 AND 1000231 "
            + "OR permission_id BETWEEN 1000025 AND 1000031 "
            + "OR permission_id IN (SELECT permission_id FROM app_permission WHERE permission_code IN ("
            + "'aivideo:creation:query','aivideo:creation:edit','aivideo:creation:generate',"
            + "'aivideo:creation-asset:query','aivideo:creation-asset:upload',"
            + "'aivideo:creation-asset:delete','aivideo:task:retry'))");
        executeUpdate("DELETE FROM app_permission WHERE permission_id BETWEEN 1000025 AND 1000031 "
            + "OR permission_code IN ('aivideo:creation:query','aivideo:creation:edit',"
            + "'aivideo:creation:generate','aivideo:creation-asset:query','aivideo:creation-asset:upload',"
            + "'aivideo:creation-asset:delete','aivideo:task:retry')");
    }

    private void cleanupTestUser() throws SQLException {
        executeUpdate("DELETE FROM app_user_role WHERE id = 990008081 OR user_id = ?", TEST_USER_ID);
        executeUpdate("DELETE FROM app_user WHERE user_id = ? OR username_normalized = 'timeline-user'", TEST_USER_ID);
    }

    private void dropTargetTables() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES.reversed()) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
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
            ) VALUES (?, 'timeline-user', 'timeline-user', 'test-only-hash', ?, 'Timeline User',
                      'active', 0, 1, 1, 7, 'sys_user', 1761100000000000001,
                      'sys_user', 1761100000000000001, NOW(), NOW(), '0')
            """)) {
            statement.setLong(1, TEST_USER_ID);
            statement.setLong(2, TEST_USER_ID);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (990008081, ?, 1000101, 'active', ?, ?, 'sys_user', 1761100000000000001,
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

    private static String targetTableCountSql() {
        return "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
            + "AND table_name IN ('" + String.join("','", TABLES) + "')";
    }

    private static Map<String, List<String>> expectedChecks() {
        Map<String, List<String>> checks = new LinkedHashMap<>();
        checks.put("av_creation_asset", sorted("ck_av_creation_asset_actor", "ck_av_creation_asset_deleted",
            "ck_av_creation_asset_dimensions", "ck_av_creation_asset_duration", "ck_av_creation_asset_origin",
            "ck_av_creation_asset_size", "ck_av_creation_asset_status", "ck_av_creation_asset_stream_flags",
            "ck_av_creation_asset_type"));
        checks.put("av_creation_project", sorted("ck_av_creation_project_actor", "ck_av_creation_project_canvas",
            "ck_av_creation_project_deleted", "ck_av_creation_project_duration", "ck_av_creation_project_source",
            "ck_av_creation_project_status"));
        checks.put("av_timeline_draft", sorted("ck_av_timeline_draft_actor", "ck_av_timeline_draft_content_size",
            "ck_av_timeline_draft_deleted", "ck_av_timeline_draft_duration", "ck_av_timeline_draft_revision",
            "ck_av_timeline_draft_schema"));
        checks.put("av_timeline_version", sorted("ck_av_timeline_version_actor",
            "ck_av_timeline_version_content_size", "ck_av_timeline_version_duration", "ck_av_timeline_version_no",
            "ck_av_timeline_version_reason", "ck_av_timeline_version_schema"));
        checks.put("av_timeline_asset_ref", sorted("ck_av_timeline_asset_ref_actor",
            "ck_av_timeline_asset_ref_document", "ck_av_timeline_asset_ref_time", "ck_av_timeline_asset_ref_usage"));
        checks.put("av_timeline_write_receipt", sorted("ck_av_timeline_write_receipt_actor",
            "ck_av_timeline_write_receipt_operation", "ck_av_timeline_write_receipt_revision",
            "ck_av_timeline_write_receipt_summary_size"));
        checks.put("av_ai_task", sorted("ck_av_ai_task_actor", "ck_av_ai_task_cancel",
            "ck_av_ai_task_free_policy", "ck_av_ai_task_name", "ck_av_ai_task_progress",
            "ck_av_ai_task_request_size", "ck_av_ai_task_result_payload_size", "ck_av_ai_task_row_version",
            "ck_av_ai_task_status", "ck_av_ai_task_success_result"));
        checks.put("av_ai_task_execution", sorted("ck_av_ai_task_execution_actor",
            "ck_av_ai_task_execution_cancel", "ck_av_ai_task_execution_lease", "ck_av_ai_task_execution_no",
            "ck_av_ai_task_execution_progress", "ck_av_ai_task_execution_row_version",
            "ck_av_ai_task_execution_status"));
        checks.put("av_ai_task_attempt", sorted("ck_av_ai_task_attempt_actor", "ck_av_ai_task_attempt_no",
            "ck_av_ai_task_attempt_row_version", "ck_av_ai_task_attempt_status",
            "ck_av_ai_task_attempt_terminal_time"));
        return checks;
    }

    private static List<String> sorted(String... values) {
        return List.of(values).stream().sorted().toList();
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

    private record RoleAuditSnapshot(long roleRevision, String updatedByType, Long updatedById,
                                     LocalDateTime updateTime) {
    }

    private record UserAuditSnapshot(long userId, long permissionRevision, String updatedByType, Long updatedById,
                                     LocalDateTime updateTime) {
    }
}
