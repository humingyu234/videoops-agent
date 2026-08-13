package org.dromara.aivideo.timeline;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the persistence guarantees needed by immutable timeline versions without changing the frozen migration.
 */
@Tag("dev")
class TimelinePersistenceIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final Pattern SAFE_TABLE = Pattern.compile("ct[vrw]_it_[a-f0-9]{32}");
    private static final String VERSION = "version";
    private static final String ASSET_REFERENCE = "assetReference";
    private static final String WRITE_RECEIPT = "writeReceipt";

    private final Map<String, String> tables = new LinkedHashMap<>();

    @AfterEach
    void dropTemporaryTables() throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String table : tables.values()) {
                assertSafeTable(table);
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
        }
    }

    @Test
    void frozenTimelineTablesKeepTransactionUniqueAndReverseLookupGuaranteesWithoutForeignKeys() throws Exception {
        createFrozenTimelineTables();

        try (Connection connection = ENV.openMySqlConnection()) {
            assertNoPhysicalForeignKeys(connection);
            assertReverseLookupIndexes(connection);
            assertRollbackLeavesNoFacts(connection);
            assertUniqueConstraintsAndReverseLookup(connection);
        }
    }

    private void createFrozenTimelineTables() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        tables.put(VERSION, "ctv_it_" + suffix);
        tables.put(ASSET_REFERENCE, "ctr_it_" + suffix);
        tables.put(WRITE_RECEIPT, "ctw_it_" + suffix);

        String migration = Files.readString(findApiRoot().resolve(
            "../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql"), StandardCharsets.UTF_8);
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> entry : tables.entrySet()) {
                statement.execute(extractAndRewriteDdl(migration, sourceTable(entry.getKey()), entry.getValue()));
            }
        }
    }

    private void assertNoPhysicalForeignKeys(Connection connection) throws SQLException {
        String placeholders = String.join(", ", List.of("?", "?", "?"));
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name IN (%s)
              AND referenced_table_name IS NOT NULL
            """.formatted(placeholders))) {
            int index = 1;
            for (String table : tables.values()) {
                statement.setString(index++, table);
            }
            assertThat(singleLong(statement)).isZero();
        }
    }

    private void assertReverseLookupIndexes(Connection connection) throws SQLException {
        String referenceTable = table(ASSET_REFERENCE);
        assertThat(indexColumns(connection, referenceTable, "idx_" + referenceTable + "_document"))
            .containsExactly("owner_user_id", "document_type", "document_id");
        assertThat(indexColumns(connection, referenceTable, "idx_" + referenceTable + "_asset"))
            .containsExactly("owner_user_id", "asset_id", "project_id");
    }

    private void assertRollbackLeavesNoFacts(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            insertVersion(connection, 1L, 71L, 81L, 1L, "rollback-version");
            insertAssetReference(connection, 11L, 71L, 81L, 1L, 601L);
            insertReceipt(connection, 21L, 71L, 81L, "rollback-receipt");

            assertThatThrownBy(() -> insertReceipt(connection, 22L, 71L, 81L, "rollback-receipt"))
                .isInstanceOf(SQLException.class);
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }

        assertThat(tableCount(connection, table(VERSION))).isZero();
        assertThat(tableCount(connection, table(ASSET_REFERENCE))).isZero();
        assertThat(tableCount(connection, table(WRITE_RECEIPT))).isZero();
    }

    private void assertUniqueConstraintsAndReverseLookup(Connection connection) throws SQLException {
        insertVersion(connection, 101L, 71L, 81L, 1L, "version-unique-1");

        assertThatThrownBy(() -> insertVersion(connection, 102L, 71L, 81L, 1L, "version-unique-2"))
            .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertVersion(connection, 103L, 71L, 81L, 2L, "version-unique-1"))
            .isInstanceOf(SQLException.class);

        insertAssetReference(connection, 201L, 71L, 81L, 101L, 601L);
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT timeline_asset_ref_id
            FROM `%s`
            WHERE owner_user_id = ?
              AND asset_id = ?
              AND project_id = ?
            """.formatted(table(ASSET_REFERENCE)))) {
            statement.setLong(1, 71L);
            statement.setLong(2, 601L);
            statement.setLong(3, 81L);
            assertThat(singleLong(statement)).isEqualTo(201L);
        }
    }

    private void insertVersion(Connection connection, long versionId, long ownerUserId, long projectId,
                               long versionNo, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO `%s` (
                timeline_version_id, owner_user_id, project_id, version_no, source_draft_revision,
                version_reason, idempotency_key, request_digest, schema_version, content_json, content_hash,
                duration_ms, source_version_id, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'manual_save', ?, ?, 'timeline-1', JSON_OBJECT('tracks', JSON_ARRAY()), ?,
                      1000, NULL, 'app_user', ?, ?, ?)
            """.formatted(table(VERSION)))) {
            int index = 1;
            statement.setLong(index++, versionId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, projectId);
            statement.setLong(index++, versionNo);
            statement.setString(index++, idempotencyKey);
            statement.setString(index++, "a".repeat(64));
            statement.setString(index++, "b".repeat(64));
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index, ownerUserId);
            statement.executeUpdate();
        }
    }

    private void insertAssetReference(Connection connection, long referenceId, long ownerUserId, long projectId,
                                      long documentId, long assetId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO `%s` (
                timeline_asset_ref_id, owner_user_id, project_id, document_type, document_id, element_id,
                asset_id, usage_type, start_ms, end_ms, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 'version', ?, 'base-video-1', ?, 'base_video', 0, 1000,
                      'app_user', ?, ?, ?)
            """.formatted(table(ASSET_REFERENCE)))) {
            int index = 1;
            statement.setLong(index++, referenceId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, projectId);
            statement.setLong(index++, documentId);
            statement.setLong(index++, assetId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index, ownerUserId);
            statement.executeUpdate();
        }
    }

    private void insertReceipt(Connection connection, long receiptId, long ownerUserId, long projectId,
                               String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO `%s` (
                timeline_write_receipt_id, owner_user_id, project_id, operation_type, idempotency_key,
                request_digest, expected_revision, result_revision, result_version_id, response_summary_json,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 'manual_version', ?, ?, 1, NULL, 1, JSON_OBJECT('contentHash', ?),
                      'app_user', ?, ?, ?)
            """.formatted(table(WRITE_RECEIPT)))) {
            int index = 1;
            statement.setLong(index++, receiptId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, projectId);
            statement.setString(index++, idempotencyKey);
            statement.setString(index++, "c".repeat(64));
            statement.setString(index++, "d".repeat(64));
            statement.setLong(index++, ownerUserId);
            statement.setLong(index++, ownerUserId);
            statement.setLong(index, ownerUserId);
            statement.executeUpdate();
        }
    }

    private List<String> indexColumns(Connection connection, String table, String indexName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND index_name = ?
            ORDER BY seq_in_index
            """)) {
            statement.setString(1, table);
            statement.setString(2, indexName);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
        }
        return columns;
    }

    private long tableCount(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM `" + table + "`")) {
            return singleLong(statement);
        }
    }

    private long singleLong(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private String extractAndRewriteDdl(String migration, String sourceTable, String targetTable) {
        Pattern pattern = Pattern.compile("(?s)CREATE TABLE IF NOT EXISTS " + Pattern.quote(sourceTable)
            + " \\(.*?\\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT=.*?;");
        Matcher matcher = pattern.matcher(migration);
        if (!matcher.find()) {
            throw new IllegalStateException("Frozen migration does not contain DDL for " + sourceTable);
        }
        assertSafeTable(targetTable);
        return matcher.group().replace(sourceTable, targetTable);
    }

    private String table(String key) {
        String table = tables.get(key);
        if (table == null) {
            throw new IllegalStateException("Missing temporary table: " + key);
        }
        assertSafeTable(table);
        return table;
    }

    private String sourceTable(String key) {
        return switch (key) {
            case VERSION -> "av_timeline_version";
            case ASSET_REFERENCE -> "av_timeline_asset_ref";
            case WRITE_RECEIPT -> "av_timeline_write_receipt";
            default -> throw new IllegalArgumentException("Unknown timeline table: " + key);
        };
    }

    private void assertSafeTable(String table) {
        if (!SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException("Unsafe temporary table name: " + table);
        }
    }

    private Path findApiRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate ai-video-api migration root");
    }
}
