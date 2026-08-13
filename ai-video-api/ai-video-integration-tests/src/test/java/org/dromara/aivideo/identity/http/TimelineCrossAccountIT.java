package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/** Real dual-starter acceptance test for owner-scoped timeline resources. */
@Tag("dev")
@ResourceLock("timeline-external-http-it")
class TimelineCrossAccountIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration TASK_TIMEOUT = Duration.ofSeconds(30);

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
    void accountBCannotReadOrMutateAccountATimelineResources() throws Exception {
        String accountAToken = fixture.loginCreator();
        String accountBToken = fixture.loginCreatorB();
        DualStarterHttpFixture.TimelineSource source = fixture.seedTimelineDigitalHumanSource();

        JsonNode project = requireData(fixture.creatorPost("/api/studio/creation-projects", accountAToken, """
            {"sourceType":"digital_human_job","sourceId":"%s","projectTitle":"cross-account",\
            "idempotencyKey":"cross-project-%s"}
            """.formatted(source.videoJobId(), source.runId())), 200);
        String projectId = project.required("projectId").asString();

        JsonNode draft = requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId + "/timeline-draft",
            accountAToken, fixture.creatorClientKey()), 200);
        String saveDraftBody = """
            {"idempotencyKey":"cross-draft-%s","expectedRevision":"%s","schemaVersion":"timeline-1",\
            "timeline":%s}
            """.formatted(source.runId(), draft.required("revision").asString(),
            JSON.writeValueAsString(draft.required("timeline")));
        JsonNode savedDraft = requireData(fixture.creatorPut(
            "/api/studio/creation-projects/" + projectId + "/timeline-draft", accountAToken, saveDraftBody), 200);
        String revision = savedDraft.required("revision").asString();

        JsonNode version = requireData(fixture.creatorPost(
            "/api/studio/creation-projects/" + projectId + "/timeline-versions", accountAToken,
            "{\"idempotencyKey\":\"cross-version-%s\",\"expectedRevision\":\"%s\"}"
                .formatted(source.runId(), revision)), 200);
        String versionId = version.required("versionId").asString();

        JsonNode task = requireData(fixture.creatorPost(
            "/api/studio/creation-projects/" + projectId + "/render-tasks", accountAToken, """
                {"idempotencyKey":"cross-render-%s","expectedRevision":"%s",\
                "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"standard"}}
                """.formatted(source.runId(), revision)), 200);
        String taskId = task.required("taskId").asString();
        JsonNode completedTask = waitForTaskSuccess(accountAToken, taskId);
        String outputAssetId = completedTask.required("resultAssetId").asString();

        JsonNode latestOutput = requireData(fixture.creatorGet(
            "/api/studio/creation-projects/" + projectId + "/outputs/latest",
            accountAToken, fixture.creatorClientKey()), 200);
        assertThat(latestOutput.required("outputAssetId").asString()).isEqualTo(outputAssetId);
        requireData(fixture.creatorGet("/api/studio/creation-assets/" + outputAssetId,
            accountAToken, fixture.creatorClientKey()), 200);

        requireCode(fixture.creatorGet("/api/studio/creation-projects/" + projectId,
            accountBToken, fixture.creatorClientKey()), 46601);
        requireCode(fixture.creatorPut("/api/studio/creation-projects/" + projectId,
            accountBToken, "{\"projectTitle\":\"forbidden\"}"), 46601);
        requireCode(fixture.creatorGet("/api/studio/creation-projects/" + projectId + "/timeline-draft",
            accountBToken, fixture.creatorClientKey()), 46601);
        requireCode(fixture.creatorPut("/api/studio/creation-projects/" + projectId + "/timeline-draft",
            accountBToken, saveDraftBody), 46601);
        requireCode(fixture.creatorGet("/api/studio/creation-projects/" + projectId + "/timeline-versions",
            accountBToken, fixture.creatorClientKey()), 46601);
        requireCode(fixture.creatorPost(
            "/api/studio/creation-projects/" + projectId + "/timeline-versions/" + versionId + "/restorations",
            accountBToken,
            "{\"idempotencyKey\":\"cross-restore-%s\",\"expectedRevision\":\"%s\"}"
                .formatted(source.runId(), revision)), 46601);
        requireCode(fixture.creatorGet("/api/tasks/" + taskId,
            accountBToken, fixture.creatorClientKey()), 46601);
        requireCode(fixture.creatorPost("/api/tasks/" + taskId + "/retry", accountBToken,
            "{\"idempotencyKey\":\"cross-retry-%s\"}".formatted(source.runId())), 46601);
        requireCode(fixture.creatorGet("/api/studio/creation-projects/" + projectId + "/outputs/latest",
            accountBToken, fixture.creatorClientKey()), 46601);
        requireCode(fixture.creatorGet("/api/studio/creation-assets/" + outputAssetId,
            accountBToken, fixture.creatorClientKey()), 46606);
        requireCode(fixture.creatorDelete("/api/studio/creation-assets/" + outputAssetId,
            accountBToken), 46606);

        requireData(fixture.creatorGet("/api/studio/creation-projects/" + projectId,
            accountAToken, fixture.creatorClientKey()), 200);
        requireData(fixture.creatorGet("/api/studio/creation-assets/" + outputAssetId,
            accountAToken, fixture.creatorClientKey()), 200);
    }

    private JsonNode waitForTaskSuccess(String token, String taskId) throws Exception {
        long deadline = System.nanoTime() + TASK_TIMEOUT.toNanos();
        JsonNode latest = null;
        while (System.nanoTime() < deadline) {
            latest = requireData(fixture.creatorGet(
                "/api/tasks/" + taskId, token, fixture.creatorClientKey()), 200);
            String status = latest.required("status").asString();
            if ("success".equals(status)) {
                return latest;
            }
            if ("failed".equals(status) || "cancelled".equals(status)) {
                fail("Account A render did not succeed: " + latest);
            }
            Thread.sleep(250);
        }
        fail("Account A render did not finish within 30 seconds: " + latest);
        return latest;
    }

    private static JsonNode requireData(HttpResponse<String> response, int expectedCode) throws Exception {
        JsonNode root = requireCode(response, expectedCode);
        JsonNode data = root.path("data");
        assertThat(data.isMissingNode() || data.isNull())
            .as("HTTP=%s body=%s", response.statusCode(), response.body())
            .isFalse();
        return data;
    }

    private static JsonNode requireCode(HttpResponse<String> response, int expectedCode) throws Exception {
        JsonNode root = JSON.readTree(response.body());
        assertThat(root.required("code").asInt())
            .as("HTTP=%s body=%s", response.statusCode(), response.body())
            .isEqualTo(expectedCode);
        return root;
    }
}
