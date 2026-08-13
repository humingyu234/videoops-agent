package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 第 5 步数字人成片到第 6 步时间轴编辑，再到第 7 步最终成片的真实双启动器闭环。
 */
@Tag("dev")
@ResourceLock("timeline-external-http-it")
class TimelineStep5To7IT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration RENDER_TIMEOUT = Duration.ofSeconds(30);

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
    void completesTheRealStep5ToStep7TimelineWorkflow() throws Exception {
        String token = fixture.loginCreator();
        DualStarterHttpFixture.TimelineSource source = fixture.seedTimelineDigitalHumanSource();

        JsonNode createdProject = createProject(token, source);
        String projectId = createdProject.required("projectId").asString();
        String initialRevision = createdProject.required("currentDraftRevision").asString();

        JsonNode project = requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId, token, fixture.creatorClientKey()), 200);
        assertThat(project.required("projectId").asString()).isEqualTo(projectId);
        assertThat(project.required("sourceType").asString()).isEqualTo("digital_human_job");
        assertThat(project.required("sourceId").asString()).isEqualTo(source.videoJobId());

        JsonNode initialDraft = requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId + "/timeline-draft",
            token, fixture.creatorClientKey()), 200);
        assertThat(initialDraft.required("revision").asString()).isEqualTo(initialRevision);
        assertThat(initialDraft.required("timeline").required("schemaVersion").asString())
            .isEqualTo("timeline-1");

        JsonNode savedDraft = saveDraft(token, projectId, source.runId(), initialRevision,
            initialDraft.required("timeline"));
        String savedRevision = savedDraft.required("revision").asString();
        assertThat(Long.parseLong(savedRevision)).isEqualTo(Long.parseLong(initialRevision) + 1L);
        assertThat(savedDraft.required("replayed").asBoolean()).isFalse();

        JsonNode version = createVersion(token, projectId, source.runId(), savedRevision);
        assertThat(version.required("projectId").asString()).isEqualTo(projectId);
        assertThat(version.required("sourceDraftRevision").asString()).isEqualTo(savedRevision);
        assertThat(version.required("versionReason").asString()).isEqualTo("manual_save");

        JsonNode createdTask = requireData(fixture.creatorPost(
            "/api/studio/creation-projects/" + projectId + "/render-tasks", token,
            renderRequest(source.runId(), savedRevision)), 200);
        String taskId = createdTask.required("taskId").asString();

        JsonNode terminalTask = waitForTerminalTask(token, taskId);
        assertThat(terminalTask.required("status").asString())
            .as("render task=%s", terminalTask)
            .isEqualTo("success");

        JsonNode latestOutput = requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId + "/outputs/latest",
            token, fixture.creatorClientKey()), 200);
        assertThat(latestOutput.required("projectId").asString()).isEqualTo(projectId);
        assertThat(latestOutput.required("taskId").asString()).isEqualTo(taskId);
        String outputAssetId = latestOutput.required("outputAssetId").asString();
        assertThat(outputAssetId).isNotBlank();

        HttpResponse<byte[]> output = fixture.creatorGetBytes(
            "/api/studio/creation-assets/" + outputAssetId + "/content", token);
        assertThat(output.statusCode()).isEqualTo(200);
        assertThat(output.body()).isNotEmpty();
    }

    private static JsonNode createProject(String token, DualStarterHttpFixture.TimelineSource source)
        throws Exception {
        String body = """
            {"sourceType":"digital_human_job","sourceId":"%s","projectTitle":"第5至7步闭环",\
            "idempotencyKey":"timeline-step5to7-project-%s"}
            """.formatted(source.videoJobId(), source.runId());
        return requireData(fixture.creatorPost("/api/studio/creation-projects", token, body), 200);
    }

    private static JsonNode saveDraft(String token, String projectId, String runId,
                                      String expectedRevision, JsonNode timeline) throws Exception {
        ObjectNode body = JSON.createObjectNode();
        body.put("idempotencyKey", "timeline-step5to7-draft-" + runId);
        body.put("expectedRevision", expectedRevision);
        body.put("schemaVersion", "timeline-1");
        body.set("timeline", timeline);
        return requireData(fixture.creatorPut(
            "/api/studio/creation-projects/" + projectId + "/timeline-draft",
            token, JSON.writeValueAsString(body)), 200);
    }

    private static JsonNode createVersion(String token, String projectId, String runId,
                                          String expectedRevision) throws Exception {
        String body = """
            {"idempotencyKey":"timeline-step5to7-version-%s","expectedRevision":"%s"}
            """.formatted(runId, expectedRevision);
        return requireData(fixture.creatorPost(
            "/api/studio/creation-projects/" + projectId + "/timeline-versions", token, body), 200);
    }

    private static String renderRequest(String runId, String expectedRevision) {
        return """
            {"idempotencyKey":"timeline-step5to7-render-%s","expectedRevision":"%s",\
            "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"standard"}}
            """.formatted(runId, expectedRevision);
    }

    private static JsonNode waitForTerminalTask(String token, String taskId) throws Exception {
        long deadline = System.nanoTime() + RENDER_TIMEOUT.toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            latest = requireData(fixture.creatorGet(
                "/api/tasks/" + taskId, token, fixture.creatorClientKey()), 200);
            String status = latest.required("status").asString();
            if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                return latest;
            }
            Thread.sleep(250);
        }
        fail("渲染任务未在时限内进入终态，最后状态：" + (latest == null ? "none" : latest));
        return latest;
    }

    private static JsonNode requireData(HttpResponse<String> response, int expectedCode) throws Exception {
        JsonNode root = JSON.readTree(response.body());
        assertThat(root.required("code").asInt())
            .as("HTTP=%s body=%s", response.statusCode(), response.body())
            .isEqualTo(expectedCode);
        JsonNode data = root.path("data");
        assertThat(data.isMissingNode() || data.isNull())
            .as("HTTP=%s body=%s", response.statusCode(), response.body())
            .isFalse();
        return data;
    }
}
