package org.dromara.aivideo.voice;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class VoiceDeletePermissionMigrationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final Path API_ROOT = locateApiRoot();
    private static final Path MIGRATION = API_ROOT.resolve(
        "../docs/sql/ai-video/mysql/20260804_01_voice_delete_permission.sql");

    @BeforeEach
    void resetSchema() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        try (Connection connection = ENV.openMySqlConnection()) {
            execute(connection, API_ROOT.resolve("../docs/sql/ry_vue.sql"));
            execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
            execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260803_01_user_portrait.sql"));
            execute(connection, API_ROOT.resolve("../docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql"));
        }
    }

    @Test
    void secondExecutionIsNoOpAndRevisionsIncreaseOnlyOnce() throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            insertEffectivePersonalCreator(connection, 900001L, 5L);

            execute(connection, MIGRATION);
            execute(connection, MIGRATION);

            assertThat(queryLong(connection,
                "SELECT COUNT(*) FROM app_permission WHERE permission_id = 1000024")).isEqualTo(1L);
            assertThat(queryLong(connection,
                "SELECT COUNT(*) FROM app_role_permission WHERE id = 1000224")).isEqualTo(1L);
            assertThat(queryLong(connection,
                "SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(2L);
            assertThat(queryLong(connection,
                "SELECT permission_revision FROM app_user WHERE user_id = 900001")).isEqualTo(6L);
        }
    }

    @Test
    void conflictingPermissionIdFailsClosedWithoutRewritingOrBinding() throws Exception {
        assertPermissionConflictFailsClosed(1000024L, "aivideo:voice:other");
    }

    @Test
    void conflictingPermissionCodeFailsClosedWithoutRewritingOrBinding() throws Exception {
        assertPermissionConflictFailsClosed(1000999L, "aivideo:voice:delete");
    }

    private void assertPermissionConflictFailsClosed(long permissionId, String permissionCode) throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            insertPermission(connection, permissionId, permissionCode);

            assertThatThrownBy(() -> execute(connection, MIGRATION)).isInstanceOf(RuntimeException.class);
            connection.rollback();

            assertThat(queryString(connection,
                "SELECT permission_code FROM app_permission WHERE permission_id = " + permissionId))
                .isEqualTo(permissionCode);
            assertThat(queryLong(connection,
                "SELECT COUNT(*) FROM app_role_permission WHERE id = 1000224")).isZero();
        }
    }

    private static void insertEffectivePersonalCreator(Connection connection, long userId, long permissionRevision)
        throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, must_change_password, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time, del_flag
            ) VALUES (?, ?, ?, ?, ?, ?, 'active', 0, 1, 1, ?,
                      'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW(), '0')
            """)) {
            statement.setLong(1, userId);
            statement.setString(2, "voice-delete-user");
            statement.setString(3, "voice-delete-user");
            statement.setString(4, "test-only-hash");
            statement.setLong(5, userId);
            statement.setString(6, "Voice Delete User");
            statement.setLong(7, permissionRevision);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (?, ?, 1000101, 'active', ?, ?,
                      'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
            """)) {
            statement.setLong(1, 9900001L);
            statement.setLong(2, userId);
            statement.setObject(3, LocalDateTime.now().minusMinutes(1));
            statement.setObject(4, LocalDateTime.now().plusMinutes(10));
            statement.executeUpdate();
        }
    }

    private static void insertPermission(Connection connection, long permissionId, String permissionCode)
        throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_permission (
                permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id, create_time, update_time
            ) VALUES (?, ?, 'conflict', 'voice', 'delete', 1, 'active',
                      'sys_user', 1761100000000000001, 'sys_user', 1761100000000000001, NOW(), NOW())
            """)) {
            statement.setLong(1, permissionId);
            statement.setString(2, permissionCode);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, Path script) {
        ScriptUtils.executeSqlScript(connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8));
    }

    private static long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getLong(1);
        }
    }

    private static String queryString(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
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
}
