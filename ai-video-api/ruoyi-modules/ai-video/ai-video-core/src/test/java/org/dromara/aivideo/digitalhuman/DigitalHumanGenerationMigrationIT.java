package org.dromara.aivideo.digitalhuman;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在本机专用 MySQL 库中验证数字人前向迁移和并发幂等仲裁。
 */
@Tag("dev")
class DigitalHumanGenerationMigrationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final Pattern SAFE_TABLE = Pattern.compile("av_dh_generation_job_it_[a-f0-9]{32}");

    private String tableName;

    @AfterEach
    void dropTemporaryTable() throws SQLException {
        if (tableName == null || !SAFE_TABLE.matcher(tableName).matches()) {
            return;
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS `" + tableName + "`");
        }
    }

    @Test
    void appliesForwardPollLeaseMigrationAndAllowsOnlyOneConcurrentIdempotentInsert() throws Exception {
        tableName = "av_dh_generation_job_it_" + UUID.randomUUID().toString().replace("-", "");
        Path migrationRoot = findApiRoot().resolve("../docs/sql/ai-video/mysql");
        executeMigration(migrationRoot.resolve("20260803_02_digital_human_vertical_flow.sql"));
        executeMigration(migrationRoot.resolve("20260803_03_digital_human_poll_lease.sql"));

        assertThat(pollColumns()).containsExactlyInAnyOrder("poll_token", "poll_lease_until", "poll_error_count");

        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<InsertOutcome> insert = () -> insertSameIntent(start, UUID.randomUUID().getMostSignificantBits());
            Future<InsertOutcome> first = executor.submit(insert);
            Future<InsertOutcome> second = executor.submit(insert);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(InsertOutcome.INSERTED, InsertOutcome.DUPLICATE);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        try (Connection connection = ENV.openMySqlConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getLong(1)).isEqualTo(1L);
        }
    }

    private InsertOutcome insertSameIntent(CyclicBarrier start, long rawId) throws Exception {
        long id = rawId & Long.MAX_VALUE;
        if (id == 0L) {
            id = 1L;
        }
        String sql = """
            INSERT INTO `%s` (
                id, tenant_id, owner_user_id, job_type, status, stage, progress,
                idempotency_key, input_hash, input_media_key, provider, voice_confirmed,
                create_by, update_by
            ) VALUES (?, 101, 202, 'voice_generate', 'queued', 'queued', 0,
                'same-concurrent-intent', ?, '1/input/reference.wav', 'indextts2', 0, 202, 202)
            """.formatted(tableName);
        try (Connection connection = ENV.openMySqlConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, id);
                statement.setString(2, "a".repeat(64));
                start.await(10, TimeUnit.SECONDS);
                statement.executeUpdate();
                connection.commit();
                return InsertOutcome.INSERTED;
            } catch (SQLException exception) {
                connection.rollback();
                if (exception.getSQLState() != null && exception.getSQLState().startsWith("23")) {
                    return InsertOutcome.DUPLICATE;
                }
                throw exception;
            }
        }
    }

    private List<String> pollColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection connection = ENV.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT column_name
                 FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = ?
                   AND column_name IN ('poll_token', 'poll_lease_until', 'poll_error_count')
                 """)) {
            statement.setString(1, tableName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        return columns;
    }

    private void executeMigration(Path migration) throws Exception {
        String constraintSuffix = tableName.substring(tableName.length() - 8);
        String sql = Files.readString(migration, StandardCharsets.UTF_8)
            .replace("av_dh_generation_job", tableName)
            .replace("ck_av_dh_job_type", "ck_dh_it_type_" + constraintSuffix)
            .replace("ck_av_dh_job_status", "ck_dh_it_status_" + constraintSuffix)
            .replace("ck_av_dh_job_progress", "ck_dh_it_progress_" + constraintSuffix)
            .replace("ck_av_dh_job_parent", "ck_dh_it_parent_" + constraintSuffix)
            .trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1);
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Path findApiRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("../docs/sql/ai-video/mysql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("无法定位 ai-video-api 迁移目录");
    }

    private enum InsertOutcome {
        INSERTED,
        DUPLICATE
    }
}
