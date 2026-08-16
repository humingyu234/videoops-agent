package org.dromara.aivideo.timeline;

import org.dromara.aivideo.creation.service.CreationMediaHandle;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaProbeDTO;
import org.dromara.aivideo.timeline.dto.TimelineMediaQualityInspectionDTO;
import org.dromara.aivideo.timeline.dto.TimelineRenderCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureCommandDTO;
import org.dromara.aivideo.timeline.dto.TimelineTextMeasureResultDTO;
import org.dromara.aivideo.timeline.service.ITimelineAiSuggestionService;
import org.dromara.aivideo.timeline.service.ITimelineMediaRenderService;
import org.dromara.aivideo.timeline.service.TimelineRenderOutputHandle;
import org.dromara.aivideo.timeline.service.TimelineTaskProgressListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Service;

import java.io.IOException;
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
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Controlled MySQL isolation probe. It rewrites only copied frozen DDL into randomly named test tables.
 */
@Tag("dev")
class CreationTimelineIsolationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final long OWNER_A = 71L;
    private static final long OWNER_B = 72L;
    private static final String ASSET = "asset";
    private static final String PROJECT = "project";
    private static final String DRAFT = "draft";
    private static final String VERSION = "version";
    private static final String REFERENCE = "reference";
    private static final String RECEIPT = "receipt";
    private static final String TASK = "task";
    private static final String EXECUTION = "execution";
    private static final String ATTEMPT = "attempt";
    private static final Pattern SAFE_TABLE = Pattern.compile(
        "cti_(asset|project|draft|version|reference|receipt|task|execution|attempt)_[a-f0-9]{32}");

    private final Map<String, String> tables = new LinkedHashMap<>();

    @AfterEach
    void dropTemporaryTables() throws SQLException {
        if (tables.isEmpty()) {
            return;
        }
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (String table : new ArrayList<>(tables.values()).reversed()) {
                assertSafeTable(table);
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
        }
    }

    @Test
    void twoOwnersKeepProjectsAssetsDraftsVersionsTasksAndOutputsIsolated() throws Exception {
        createFrozenTables();

        try (Connection connection = ENV.openMySqlConnection()) {
            OwnerGraph first = insertOwnerGraph(connection, OWNER_A, 1_000L);
            OwnerGraph second = insertOwnerGraph(connection, OWNER_B, 2_000L);

            assertOwnerScoped(connection, ASSET, "asset_id", first.ownerId(), first.assetIds(), second.ownerId(),
                second.assetIds());
            assertOwnerScoped(connection, PROJECT, "project_id", first.ownerId(), List.of(first.projectId()),
                second.ownerId(), List.of(second.projectId()));
            assertOwnerScoped(connection, DRAFT, "timeline_draft_id", first.ownerId(), List.of(first.draftId()),
                second.ownerId(), List.of(second.draftId()));
            assertOwnerScoped(connection, VERSION, "timeline_version_id", first.ownerId(), List.of(first.versionId()),
                second.ownerId(), List.of(second.versionId()));
            assertOwnerScoped(connection, REFERENCE, "timeline_asset_ref_id", first.ownerId(), List.of(first.referenceId()),
                second.ownerId(), List.of(second.referenceId()));
            assertOwnerScoped(connection, RECEIPT, "timeline_write_receipt_id", first.ownerId(), List.of(first.receiptId()),
                second.ownerId(), List.of(second.receiptId()));
            assertOwnerScoped(connection, TASK, "task_id", first.ownerId(), List.of(first.taskId()),
                second.ownerId(), List.of(second.taskId()));
            assertOwnerScoped(connection, EXECUTION, "task_execution_id", first.ownerId(), List.of(first.executionId()),
                second.ownerId(), List.of(second.executionId()));
            assertOwnerScoped(connection, ATTEMPT, "task_attempt_id", first.ownerId(), List.of(first.attemptId()),
                second.ownerId(), List.of(second.attemptId()));

            assertThat(count(connection, "SELECT COUNT(*) FROM `" + table(REFERENCE)
                + "` WHERE owner_user_id = ? AND asset_id = ?", OWNER_A, first.baseVideoAssetId())).isEqualTo(1L);
            assertThat(count(connection, "SELECT COUNT(*) FROM `" + table(PROJECT)
                + "` WHERE owner_user_id = ? AND current_output_asset_id = ?", OWNER_B, second.outputAssetId()))
                .isEqualTo(1L);
        }
    }

    @Test
    void frozenActorChecksRejectCrossOwnerWritesAndKeepAuditFactsOwnerBound() throws Exception {
        createFrozenTables();
        try (Connection connection = ENV.openMySqlConnection()) {
            OwnerGraph graph = insertOwnerGraph(connection, OWNER_A, 3_000L);
            assertAuditMatchesOwner(connection, OWNER_A);

            assertThatThrownBy(() -> insertAsset(connection, 9_999L, OWNER_A, "video", "upload", null,
                OWNER_B, OWNER_A, OWNER_A)).isInstanceOf(SQLException.class);
            assertThat(count(connection, "SELECT COUNT(*) FROM `" + table(ASSET) + "` WHERE asset_id = ?", 9_999L))
                .isZero();
            assertThat(graph.outputAssetId()).isEqualTo(3_003L);
        }
    }

    @Test
    void testScopeMediaFakesAreNotProductionBeans() throws Exception {
        assertThat(ITimelineMediaRenderService.class.isAssignableFrom(TestTimelineMediaRenderService.class)).isTrue();
        assertThat(ITimelineAiSuggestionService.class.isAssignableFrom(TestTimelineAiSuggestionService.class)).isTrue();
        assertThat(TestTimelineMediaRenderService.class.getAnnotation(Service.class)).isNull();
        assertThat(TestTimelineAiSuggestionService.class.getAnnotation(Service.class)).isNull();

        Path productionSource = findApiRoot().resolve("ruoyi-modules/ai-video/ai-video-core/src/main/java");
        try (Stream<Path> files = Files.walk(productionSource)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                .map(this::readSource)
                .filter(source -> source.contains("implements ITimelineMediaRenderService")
                    || source.contains("implements ITimelineAiSuggestionService"))
                .toList()).isEmpty();
        }
    }

    private void createFrozenTables() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        tables.put(ASSET, "cti_asset_" + suffix);
        tables.put(PROJECT, "cti_project_" + suffix);
        tables.put(DRAFT, "cti_draft_" + suffix);
        tables.put(VERSION, "cti_version_" + suffix);
        tables.put(REFERENCE, "cti_reference_" + suffix);
        tables.put(RECEIPT, "cti_receipt_" + suffix);
        tables.put(TASK, "cti_task_" + suffix);
        tables.put(EXECUTION, "cti_execution_" + suffix);
        tables.put(ATTEMPT, "cti_attempt_" + suffix);

        String migration = Files.readString(findApiRoot().resolve(
            "../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql"), StandardCharsets.UTF_8);
        try (Connection connection = ENV.openMySqlConnection(); Statement statement = connection.createStatement()) {
            for (Map.Entry<String, String> entry : tables.entrySet()) {
                statement.execute(extractAndRewriteDdl(migration, sourceTable(entry.getKey()), entry.getValue()));
            }
        }
    }

    private OwnerGraph insertOwnerGraph(Connection connection, long ownerId, long base) throws SQLException {
        long baseVideoAssetId = base + 1;
        long primaryAudioAssetId = base + 2;
        long outputAssetId = base + 3;
        long projectId = base + 10;
        long draftId = base + 20;
        long versionId = base + 30;
        long referenceId = base + 40;
        long receiptId = base + 50;
        long taskId = base + 60;
        long executionId = base + 70;
        long attemptId = base + 80;

        insertAsset(connection, baseVideoAssetId, ownerId, "video", "upload", null, ownerId, ownerId, ownerId);
        insertAsset(connection, primaryAudioAssetId, ownerId, "audio", "upload", null, ownerId, ownerId, ownerId);
        insertAsset(connection, outputAssetId, ownerId, "video", "timeline_render_output", taskId, ownerId, ownerId,
            ownerId);
        insertProject(connection, projectId, ownerId, baseVideoAssetId, primaryAudioAssetId, outputAssetId);
        insertDraft(connection, draftId, ownerId, projectId);
        insertVersion(connection, versionId, ownerId, projectId);
        insertReference(connection, referenceId, ownerId, projectId, versionId, baseVideoAssetId);
        insertReceipt(connection, receiptId, ownerId, projectId, versionId);
        insertTask(connection, taskId, ownerId, projectId, versionId, outputAssetId);
        insertExecution(connection, executionId, ownerId, taskId, projectId, versionId, outputAssetId);
        insertAttempt(connection, attemptId, ownerId, taskId, executionId);
        return new OwnerGraph(ownerId, baseVideoAssetId, primaryAudioAssetId, outputAssetId, projectId, draftId,
            versionId, referenceId, receiptId, taskId, executionId, attemptId);
    }

    private void insertAsset(Connection connection, long assetId, long ownerId, String assetType, String usageOrigin,
                             Long sourceRefId, long actorId, long createBy, long updateBy) throws SQLException {
        boolean video = "video".equals(assetType);
        execute(connection, """
            INSERT INTO `%s` (
                asset_id, owner_user_id, asset_type, usage_origin, source_ref_id, asset_status, storage_key,
                mime_type, size_bytes, sha256, duration_ms, width, height, has_video_stream, has_audio_stream,
                idempotency_key, request_digest, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, ?, 'ready', ?, ?, 1000, ?, 1000, ?, ?, ?, ?, ?, ?, 'app_user', ?, ?, ?)
            """.formatted(table(ASSET)), assetId, ownerId, assetType, usageOrigin, sourceRefId,
            "private/it/" + assetId, video ? "video/mp4" : "audio/mpeg", hash(assetId),
            video ? 1080 : null, video ? 1920 : null, video ? 1 : 0, video ? 0 : 1,
            "asset-" + assetId, hash(assetId + 100), actorId, createBy, updateBy);
    }

    private void insertProject(Connection connection, long projectId, long ownerId, long baseVideoAssetId,
                               long primaryAudioAssetId, long outputAssetId) throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                project_id, owner_user_id, project_title, idempotency_key, request_digest, source_type, source_ref_id,
                base_video_asset_id, primary_audio_asset_id, script_text_snapshot, canvas_width, canvas_height,
                frame_rate, duration_ms, project_status, current_output_asset_id, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, ?, 'digital_human_job', ?, ?, ?, 'server snapshot', 1080, 1920, 30, 1000,
                      'ready', ?, 'app_user', ?, ?, ?)
            """.formatted(table(PROJECT)), projectId, ownerId, "project-" + projectId, "project-" + projectId,
            hash(projectId), projectId + 900, baseVideoAssetId, primaryAudioAssetId, outputAssetId, ownerId, ownerId, ownerId);
    }

    private void insertDraft(Connection connection, long draftId, long ownerId, long projectId) throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                timeline_draft_id, owner_user_id, project_id, revision, schema_version, content_json, content_hash,
                duration_ms, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 1, 'timeline-1', JSON_OBJECT('schemaVersion', 'timeline-1', 'tracks', JSON_ARRAY()), ?,
                      1000, 'app_user', ?, ?, ?)
            """.formatted(table(DRAFT)), draftId, ownerId, projectId, hash(draftId), ownerId, ownerId, ownerId);
    }

    private void insertVersion(Connection connection, long versionId, long ownerId, long projectId) throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                timeline_version_id, owner_user_id, project_id, version_no, source_draft_revision, version_reason,
                idempotency_key, request_digest, schema_version, content_json, content_hash, duration_ms, source_version_id,
                actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 1, 1, 'manual_save', ?, ?, 'timeline-1',
                      JSON_OBJECT('schemaVersion', 'timeline-1', 'tracks', JSON_ARRAY()), ?, 1000, NULL,
                      'app_user', ?, ?, ?)
            """.formatted(table(VERSION)), versionId, ownerId, projectId, "version-" + versionId, hash(versionId),
            hash(versionId + 100), ownerId, ownerId, ownerId);
    }

    private void insertReference(Connection connection, long referenceId, long ownerId, long projectId, long versionId,
                                 long assetId) throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                timeline_asset_ref_id, owner_user_id, project_id, document_type, document_id, element_id, asset_id,
                usage_type, start_ms, end_ms, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, 'version', ?, 'base-video', ?, 'base_video', 0, 1000, 'app_user', ?, ?, ?)
            """.formatted(table(REFERENCE)), referenceId, ownerId, projectId, versionId, assetId, ownerId, ownerId, ownerId);
    }

    private void insertReceipt(Connection connection, long receiptId, long ownerId, long projectId, long versionId)
        throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                timeline_write_receipt_id, owner_user_id, project_id, operation_type, idempotency_key, request_digest,
                expected_revision, result_revision, result_version_id, response_summary_json, actor_type, actor_id,
                create_by, update_by
            ) VALUES (?, ?, ?, 'manual_version', ?, ?, 1, 1, ?, JSON_OBJECT('versionId', ?), 'app_user', ?, ?, ?)
            """.formatted(table(RECEIPT)), receiptId, ownerId, projectId, "receipt-" + receiptId, hash(receiptId),
            versionId, Long.toString(versionId), ownerId, ownerId, ownerId);
    }

    private void insertTask(Connection connection, long taskId, long ownerId, long projectId, long versionId,
                            long outputAssetId) throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        execute(connection, """
            INSERT INTO `%s` (
                task_id, owner_user_id, task_type, resource_type, resource_id, input_version_id, idempotency_key,
                request_digest, request_schema_version, request_payload_json, task_status, stage, progress_percent,
                row_version, cancel_requested, active_execution_id, result_asset_id, result_schema_version,
                result_payload_json, error_code, error_summary, quota_policy_version, estimated_usage, started_at,
                finished_at, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, 'timeline_render', 'creation_project', ?, ?, ?, ?, 'timeline-1', JSON_OBJECT(),
                      'success', 'completed', 100, 1, 0, NULL, ?, NULL, NULL, NULL, NULL, 'timeline-free-1', 0,
                      ?, ?, 'app_user', ?, ?, ?)
            """.formatted(table(TASK)), taskId, ownerId, projectId, versionId, "task-" + taskId, hash(taskId),
            outputAssetId, now, now, ownerId, ownerId, ownerId);
    }

    private void insertExecution(Connection connection, long executionId, long ownerId, long taskId, long projectId,
                                 long versionId, long outputAssetId) throws SQLException {
        execute(connection, """
            INSERT INTO `%s` (
                task_execution_id, owner_user_id, task_id, resource_id, execution_no, execution_status, stage,
                progress_percent, row_version, next_run_at, lease_owner, lease_token, lease_expires_at,
                cancel_requested_snapshot, input_version_id, output_config_digest, result_asset_id, error_code,
                error_summary, actor_type, actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'success', 'completed', 100, 1, NULL, NULL, NULL, NULL, 0, ?, NULL, ?,
                      NULL, NULL, 'app_user', ?, ?, ?)
            """.formatted(table(EXECUTION)), executionId, ownerId, taskId, projectId, versionId, outputAssetId,
            ownerId, ownerId, ownerId);
    }

    private void insertAttempt(Connection connection, long attemptId, long ownerId, long taskId, long executionId)
        throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        execute(connection, """
            INSERT INTO `%s` (
                task_attempt_id, owner_user_id, task_id, task_execution_id, attempt_no, attempt_status, row_version,
                worker_id, lease_token_digest, started_at, finished_at, exit_category, error_summary, actor_type,
                actor_id, create_by, update_by
            ) VALUES (?, ?, ?, ?, 1, 'success', 1, 'it-worker', ?, ?, ?, 'completed', NULL, 'app_user', ?, ?, ?)
            """.formatted(table(ATTEMPT)), attemptId, ownerId, taskId, executionId, hash(attemptId), now, now,
            ownerId, ownerId, ownerId);
    }

    private void assertOwnerScoped(Connection connection, String tableKey, String idColumn, long firstOwner,
                                   List<Long> firstIds, long secondOwner, List<Long> secondIds) throws SQLException {
        assertThat(idsForOwner(connection, tableKey, idColumn, firstOwner)).containsExactlyInAnyOrderElementsOf(firstIds);
        assertThat(idsForOwner(connection, tableKey, idColumn, secondOwner)).containsExactlyInAnyOrderElementsOf(secondIds);
        for (long secondId : secondIds) {
            assertThat(count(connection, "SELECT COUNT(*) FROM `" + table(tableKey) + "` WHERE owner_user_id = ? AND "
                + idColumn + " = ?", firstOwner, secondId)).isZero();
        }
        assertAuditMatchesOwner(connection, firstOwner, tableKey);
        assertAuditMatchesOwner(connection, secondOwner, tableKey);
    }

    private List<Long> idsForOwner(Connection connection, String tableKey, String idColumn, long ownerId)
        throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + idColumn + " FROM `"
            + table(tableKey) + "` WHERE owner_user_id = ? ORDER BY " + idColumn)) {
            statement.setLong(1, ownerId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(result.getLong(1));
                }
            }
        }
        return ids;
    }

    private void assertAuditMatchesOwner(Connection connection, long ownerId) throws SQLException {
        for (String tableKey : tables.keySet()) {
            assertAuditMatchesOwner(connection, ownerId, tableKey);
        }
    }

    private void assertAuditMatchesOwner(Connection connection, long ownerId, String tableKey) throws SQLException {
        assertThat(count(connection, "SELECT COUNT(*) FROM `" + table(tableKey)
            + "` WHERE owner_user_id = ? AND (actor_id <> owner_user_id OR create_by <> owner_user_id"
            + " OR update_by <> owner_user_id)", ownerId)).isZero();
    }

    private long count(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getLong(1);
            }
        }
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
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

    private String sourceTable(String key) {
        return switch (key) {
            case ASSET -> "av_creation_asset";
            case PROJECT -> "av_creation_project";
            case DRAFT -> "av_timeline_draft";
            case VERSION -> "av_timeline_version";
            case REFERENCE -> "av_timeline_asset_ref";
            case RECEIPT -> "av_timeline_write_receipt";
            case TASK -> "av_ai_task";
            case EXECUTION -> "av_ai_task_execution";
            case ATTEMPT -> "av_ai_task_attempt";
            default -> throw new IllegalArgumentException("Unknown table key " + key);
        };
    }

    private String table(String key) {
        String table = tables.get(key);
        if (table == null) {
            throw new IllegalStateException("Missing table " + key);
        }
        assertSafeTable(table);
        return table;
    }

    private void assertSafeTable(String table) {
        if (!SAFE_TABLE.matcher(table).matches()) {
            throw new IllegalStateException("Unsafe temporary table name " + table);
        }
    }

    private String hash(long seed) {
        return String.format("%064x", seed);
    }

    private String readSource(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + path, exception);
        }
    }

    private Path findApiRoot() {
        for (Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); current != null;
             current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql"))) {
                return current;
            }
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }

    private record OwnerGraph(long ownerId, long baseVideoAssetId, long primaryAudioAssetId, long outputAssetId,
                              long projectId, long draftId, long versionId, long referenceId, long receiptId,
                              long taskId, long executionId, long attemptId) {
        private List<Long> assetIds() {
            return List.of(baseVideoAssetId, primaryAudioAssetId, outputAssetId);
        }
    }

    /** Test-only port double; deliberately not a Spring bean. */
    private static final class TestTimelineMediaRenderService implements ITimelineMediaRenderService {
        @Override
        public TimelineMediaProbeDTO probe(CreationMediaHandle input) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineMediaQualityInspectionDTO inspectQuality(CreationMediaHandle input) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineTextMeasureResultDTO measureText(TimelineTextMeasureCommandDTO command) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineRenderOutputHandle render(TimelineRenderCommandDTO command, List<CreationMediaHandle> inputs,
                                                 TimelineTaskProgressListener progress,
                                                 BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public void cancel(String executionId, String attemptId) {
            // Test doubles do not issue external media calls.
        }
    }

    /** Test-only port double; deliberately not a Spring bean. */
    private static final class TestTimelineAiSuggestionService implements ITimelineAiSuggestionService {
        @Override
        public TimelineImagePromptResultDTO generateImagePrompt(TimelineImagePromptCommandDTO command,
                                                                 TimelineTaskProgressListener progress,
                                                                 BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineFancyTextSuggestionResultDTO suggestFancyText(TimelineFancyTextSuggestionCommandDTO command,
                                                                      TimelineTaskProgressListener progress,
                                                                      BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineSubtitleAlignmentResultDTO alignFromTrustedCues(TimelineSubtitleAlignmentCommandDTO command,
                                                                        TimelineTaskProgressListener progress,
                                                                        BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException("test double");
        }

        @Override
        public TimelineSubtitleAlignmentResultDTO alignFromAudio(TimelineSubtitleAlignmentCommandDTO command,
                                                                  CreationMediaHandle primaryAudio,
                                                                  TimelineTaskProgressListener progress,
                                                                  BooleanSupplier cancellationRequested) {
            throw new UnsupportedOperationException("test double");
        }
    }
}
