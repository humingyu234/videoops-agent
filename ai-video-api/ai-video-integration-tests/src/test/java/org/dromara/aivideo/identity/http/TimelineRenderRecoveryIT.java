package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 真实双启动器、MySQL、Redis、FFmpeg 与对象存储下的时间轴成品恢复验收。
 */
@Tag("dev")
@ResourceLock("timeline-external-http-it")
class TimelineRenderRecoveryIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration FINALIZATION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(30);
    private static final String FINALIZATION_TRIGGER = "tr_av_creation_project_render_it";

    private static DualStarterHttpFixture fixture;

    @BeforeAll
    static void startExternalStarters() throws Exception {
        fixture = DualStarterHttpFixture.start();
    }

    @AfterAll
    static void stopExternalStarters() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    void resumesTheSameRenderExecutionAfterTheCreatorJvmDiesDuringFinalization() throws Exception {
        String beans = fixture.creatorActuatorBeans();
        assertThat(beans)
            .contains("FfmpegTimelineMediaRenderService")
            .contains("TimelineAiSuggestionServiceImpl")
            .doesNotContain("UnavailableTimelineMediaRenderService")
            .doesNotContain("UnavailableTimelineAiSuggestionService");

        String creatorToken = fixture.loginCreator();
        DualStarterHttpFixture.TimelineSource source = fixture.seedTimelineDigitalHumanSource();
        String projectId = createProject(creatorToken, source.videoJobId());
        String revision = project(creatorToken, projectId).required("currentDraftRevision").asString();
        String idempotencyKey = "timeline-render-recovery-" + source.runId();
        String requestBody = renderRequest(idempotencyKey, revision, "standard");

        installFinalizationPause(projectId);
        try {
            JsonNode createdTask = requireData(fixture.creatorPost(
                "/api/studio/creation-projects/" + projectId + "/render-tasks", creatorToken, requestBody), 200);
            String taskId = createdTask.required("taskId").asString();

            waitForMarker(taskId, projectId, FINALIZATION_TIMEOUT);
            RecoveryState beforeCrash = recoveryState(taskId, projectId);
            assertThat(beforeCrash.taskStatus()).isEqualTo("running");
            assertThat(beforeCrash.executionStatus()).isEqualTo("running");
            assertThat(beforeCrash.assetStatus()).isEqualTo("pending");
            assertThat(beforeCrash.projectOutputAssetId()).isNull();
            assertThat(beforeCrash.outputAssetId()).isNotNull();
            assertThat(beforeCrash.outputStorageKey()).isNotBlank();

            fixture.stopCreator();
            waitForCreatorRollback(taskId, projectId, beforeCrash, FINALIZATION_TIMEOUT);
            expireLease(beforeCrash.executionId());
            removeFinalizationPause();

            fixture.restartCreator();
            creatorToken = fixture.loginCreator();
            JsonNode completedTask = waitForTaskTerminal(creatorToken, taskId, RECOVERY_TIMEOUT);
            assertThat(completedTask.required("status").asString()).isEqualTo("success");

            RecoveryState completed = recoveryState(taskId, projectId);
            assertThat(completed.taskId()).isEqualTo(beforeCrash.taskId());
            assertThat(completed.executionId()).isEqualTo(beforeCrash.executionId());
            assertThat(completed.inputVersionId()).isEqualTo(beforeCrash.inputVersionId());
            assertThat(completed.outputAssetId()).isEqualTo(beforeCrash.outputAssetId());
            assertThat(completed.outputStorageKey()).isEqualTo(beforeCrash.outputStorageKey());
            assertThat(completed.taskStatus()).isEqualTo("success");
            assertThat(completed.executionStatus()).isEqualTo("success");
            assertThat(completed.assetStatus()).isEqualTo("ready");
            assertThat(completed.taskResultAssetId()).isEqualTo(completed.outputAssetId());
            assertThat(completed.executionResultAssetId()).isEqualTo(completed.outputAssetId());
            assertThat(completed.projectOutputAssetId()).isEqualTo(completed.outputAssetId());
            assertThat(completed.rootCount()).isEqualTo(1);
            assertThat(completed.executionCount()).isEqualTo(1);
            assertThat(completed.inputVersionCount()).isEqualTo(1);
            assertThat(completed.attemptCount()).isEqualTo(2);
            assertThat(completed.abandonedAttemptCount()).isEqualTo(1);
            assertThat(completed.successAttemptCount()).isEqualTo(1);
            assertThat(completed.outputSha256()).matches("[a-f0-9]{64}");
            assertThat(completed.outputSize()).isPositive();

            HttpResponse<byte[]> output = fixture.creatorGetBytes(
                "/api/studio/creation-assets/" + completed.outputAssetId() + "/content", creatorToken);
            assertThat(output.statusCode()).isEqualTo(200);
            assertThat(output.body()).hasSize(Math.toIntExact(completed.outputSize()));
            assertThat(sha256(output.body())).isEqualTo(completed.outputSha256());

            JsonNode replay = requireData(fixture.creatorPost(
                "/api/studio/creation-projects/" + projectId + "/render-tasks", creatorToken, requestBody), 200);
            assertThat(replay.required("taskId").asString()).isEqualTo(taskId);
            RecoveryState afterReplay = recoveryState(taskId, projectId);
            assertThat(afterReplay.outputAssetId()).isEqualTo(completed.outputAssetId());
            assertThat(afterReplay.outputStorageKey()).isEqualTo(completed.outputStorageKey());
            assertThat(afterReplay.outputSha256()).isEqualTo(completed.outputSha256());
            assertThat(afterReplay.attemptCount()).isEqualTo(2);

            HttpResponse<String> conflictingReplay = fixture.creatorPost(
                "/api/studio/creation-projects/" + projectId + "/render-tasks", creatorToken,
                renderRequest(idempotencyKey, revision, "high"));
            requireCode(conflictingReplay, 46609);
            RecoveryState afterConflict = recoveryState(taskId, projectId);
            assertThat(afterConflict.rootCount()).isEqualTo(1);
            assertThat(afterConflict.outputAssetId()).isEqualTo(completed.outputAssetId());
            assertThat(afterConflict.outputStorageKey()).isEqualTo(completed.outputStorageKey());
            assertThat(afterConflict.outputSha256()).isEqualTo(completed.outputSha256());
        } finally {
            removeFinalizationPause();
        }
    }

    private String createProject(String token, String sourceId) throws Exception {
        String body = """
            {"sourceType":"digital_human_job","sourceId":"%s","projectTitle":"恢复验收",\
            "idempotencyKey":"timeline-project-%s"}
            """.formatted(sourceId, sourceId);
        JsonNode project = requireData(fixture.creatorPost("/api/studio/creation-projects", token, body), 200);
        return project.required("projectId").asString();
    }

    private JsonNode project(String token, String projectId) throws Exception {
        return requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId, token, fixture.creatorClientKey()), 200);
    }

    private static String renderRequest(String idempotencyKey, String revision, String qualityPreset) {
        return """
            {"idempotencyKey":"%s","expectedRevision":"%s",\
            "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"%s"}}
            """.formatted(idempotencyKey, revision, qualityPreset);
    }

    private void installFinalizationPause(String projectId) throws Exception {
        long numericProjectId = positiveId(projectId);
        try (Connection connection = fixture.openMySqlConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + FINALIZATION_TRIGGER);
            statement.execute("""
                CREATE TRIGGER %s BEFORE UPDATE ON av_creation_project FOR EACH ROW
                BEGIN
                  IF NEW.project_id = %d AND OLD.current_output_asset_id IS NULL
                     AND NEW.current_output_asset_id IS NOT NULL THEN
                    DO SLEEP(30);
                  END IF;
                END
                """.formatted(FINALIZATION_TRIGGER, numericProjectId));
        }
    }

    private void removeFinalizationPause() throws Exception {
        if (fixture == null) {
            return;
        }
        try (Connection connection = fixture.openMySqlConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS " + FINALIZATION_TRIGGER);
        }
    }

    private void waitForMarker(String taskId, String projectId, Duration timeout) throws Exception {
        long expectedTaskId = positiveId(taskId);
        positiveId(projectId);
        long deadline = System.nanoTime() + timeout.toNanos();
        TaskMarkerState latest = null;
        while (System.nanoTime() < deadline) {
            try (Connection connection = fixture.openMySqlConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     """
                         SELECT t.task_status, t.stage, t.progress_percent, t.error_code, t.error_summary,
                                EXISTS(
                                  SELECT 1 FROM information_schema.PROCESSLIST p
                                  WHERE p.ID <> CONNECTION_ID()
                                    AND p.DB = DATABASE()
                                    AND LOWER(COALESCE(p.STATE, '')) = 'user sleep'
                                    AND (
                                      LOWER(COALESCE(p.INFO, '')) LIKE '%update%av_creation_project%'
                                      OR LOWER(TRIM(COALESCE(p.INFO, ''))) = 'do sleep(30)'
                                    )
                                ) AS marker_reached
                         FROM av_ai_task t
                         WHERE t.task_id = ?
                         """)) {
                statement.setLong(1, expectedTaskId);
                try (ResultSet result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    latest = new TaskMarkerState(result.getBoolean("marker_reached"),
                        result.getString("task_status"), result.getString("stage"),
                        result.getInt("progress_percent"), result.getString("error_code"),
                        result.getString("error_summary"));
                }
            }
            if (latest.markerReached()) {
                return;
            }
            if ("success".equals(latest.status()) || "failed".equals(latest.status())
                || "cancelled".equals(latest.status())) {
                fail("渲染任务在进入项目成品更新点前已终止：" + latest);
            }
            Thread.sleep(250);
        }
        fail("最终事务未进入项目成品更新点，最后任务状态：" + latest);
    }

    private void waitForCreatorRollback(String taskId, String projectId, RecoveryState expected,
                                        Duration timeout) throws Exception {
        waitUntil(timeout, () -> {
            RecoveryState state = recoveryState(taskId, projectId);
            return "running".equals(state.taskStatus())
                && "running".equals(state.executionStatus())
                && "pending".equals(state.assetStatus())
                && state.projectOutputAssetId() == null
                && expected.outputAssetId().equals(state.outputAssetId());
        }, "终止用户端 JVM 后最终事务未回滚到 pending/running 状态");
    }

    private void expireLease(String executionId) throws Exception {
        try (Connection connection = fixture.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 UPDATE av_ai_task_execution
                 SET lease_expires_at = UTC_TIMESTAMP() - INTERVAL 1 SECOND
                 WHERE task_execution_id = ? AND execution_status = 'running'
                 """)) {
            statement.setLong(1, positiveId(executionId));
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private JsonNode waitForTaskTerminal(String token, String taskId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            latest = requireData(fixture.creatorGet("/api/tasks/" + taskId, token, fixture.creatorClientKey()), 200);
            String status = latest.required("status").asString();
            if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                return latest;
            }
            Thread.sleep(250);
        }
        fail("恢复后的任务未在时限内进入终态，最后状态=" + (latest == null ? "none" : latest));
        return latest;
    }

    private RecoveryState recoveryState(String taskId, String projectId) throws Exception {
        try (Connection connection = fixture.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT t.task_id, t.task_status, t.result_asset_id AS task_result_asset_id,
                        e.task_execution_id, e.execution_status, e.input_version_id,
                        e.result_asset_id AS execution_result_asset_id,
                        a.asset_id, a.asset_status, a.storage_key, a.sha256, a.size_bytes,
                        p.current_output_asset_id,
                        (SELECT COUNT(*) FROM av_ai_task rt WHERE rt.task_id = t.task_id) AS root_count,
                        (SELECT COUNT(*) FROM av_ai_task_execution re WHERE re.task_id = t.task_id) AS execution_count,
                        (SELECT COUNT(*) FROM av_timeline_version rv WHERE rv.project_id = p.project_id) AS version_count,
                        (SELECT COUNT(*) FROM av_ai_task_attempt ra WHERE ra.task_id = t.task_id) AS attempt_count,
                        (SELECT COUNT(*) FROM av_ai_task_attempt ra WHERE ra.task_id = t.task_id AND ra.attempt_status = 'abandoned') AS abandoned_count,
                        (SELECT COUNT(*) FROM av_ai_task_attempt ra WHERE ra.task_id = t.task_id AND ra.attempt_status = 'success') AS success_count
                 FROM av_ai_task t
                 JOIN av_ai_task_execution e ON e.task_execution_id = t.active_execution_id
                 JOIN av_creation_project p ON p.project_id = t.resource_id
                 LEFT JOIN av_creation_asset a ON a.owner_user_id = t.owner_user_id
                     AND a.usage_origin = 'timeline_render_output' AND a.source_ref_id = t.task_id
                 WHERE t.task_id = ? AND p.project_id = ?
                 """)) {
            statement.setLong(1, positiveId(taskId));
            statement.setLong(2, positiveId(projectId));
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return new RecoveryState(
                    Long.toString(result.getLong("task_id")), result.getString("task_status"),
                    nullableId(result, "task_result_asset_id"), Long.toString(result.getLong("task_execution_id")),
                    result.getString("execution_status"), nullableId(result, "input_version_id"),
                    nullableId(result, "execution_result_asset_id"), nullableId(result, "asset_id"),
                    result.getString("asset_status"), result.getString("storage_key"), result.getString("sha256"),
                    result.getLong("size_bytes"), nullableId(result, "current_output_asset_id"),
                    result.getInt("root_count"), result.getInt("execution_count"), result.getInt("version_count"),
                    result.getInt("attempt_count"), result.getInt("abandoned_count"), result.getInt("success_count"));
            }
        }
    }

    private static String nullableId(ResultSet result, String column) throws Exception {
        long value = result.getLong(column);
        return result.wasNull() ? null : Long.toString(value);
    }

    private static JsonNode requireData(HttpResponse<String> response, int expectedCode) throws Exception {
        requireCode(response, expectedCode);
        JsonNode root = JSON.readTree(response.body());
        JsonNode data = root.path("data");
        assertThat(data.isMissingNode() || data.isNull()).isFalse();
        return data;
    }

    private static void requireCode(HttpResponse<String> response, int expectedCode) throws Exception {
        JsonNode root = JSON.readTree(response.body());
        assertThat(root.required("code").asInt())
            .as("HTTP=%s body=%s", response.statusCode(), response.body())
            .isEqualTo(expectedCode);
    }

    private static void waitUntil(Duration timeout, CheckedCondition condition, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.evaluate()) {
                return;
            }
            Thread.sleep(100);
        }
        fail(failureMessage);
    }

    private static long positiveId(String value) {
        long parsed = Long.parseLong(value);
        if (parsed <= 0) {
            throw new IllegalArgumentException("业务 ID 必须为正数");
        }
        return parsed;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean evaluate() throws Exception;
    }

    private record RecoveryState(
        String taskId,
        String taskStatus,
        String taskResultAssetId,
        String executionId,
        String executionStatus,
        String inputVersionId,
        String executionResultAssetId,
        String outputAssetId,
        String assetStatus,
        String outputStorageKey,
        String outputSha256,
        long outputSize,
        String projectOutputAssetId,
        int rootCount,
        int executionCount,
        int inputVersionCount,
        int attemptCount,
        int abandonedAttemptCount,
        int successAttemptCount
    ) {
    }

    private record TaskMarkerState(boolean markerReached, String status, String stage, int progressPercent,
                                   String errorCode, String errorSummary) {
    }
}
