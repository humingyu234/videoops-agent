package org.dromara.aivideo.task;

import org.dromara.aivideo.task.dto.AiTaskActorDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.CreateWorkflowAiTaskDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskPayloadDTO;
import org.dromara.aivideo.task.dto.WorkflowAiTaskResultPayloadDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

@Tag("dev")
class WorkflowAiTaskContractTest {
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void freezesWorkflowTaskTypesResourcesAndActorShape() {
        assertThat(AiTaskType.valueOf("WORKFLOW_TEMPLATE_GENERATE")).isNotNull();
        assertThat(AiTaskType.valueOf("WORKFLOW_TEMPLATE_TEST")).isNotNull();
        assertThat(AiTaskResourceType.valueOf("WORKFLOW_ORDER")).isNotNull();
        assertThat(AiTaskResourceType.valueOf("WORKFLOW_TEMPLATE")).isNotNull();
        assertThat(new AiTaskActorDTO("sys_user", 7L, null).ownerUserId()).isNull();
        assertThat(new AiTaskAccessScopeDTO(11L, 7L, "personal-23").ownerUserId()).isEqualTo(7L);
    }

    @Test
    void rejectsInvalidActorsAndKeepsOwnerSemanticsFrozen() {
        assertThat(new AiTaskActorDTO("app_user", 7L, 7L).ownerUserId()).isEqualTo(7L);
        assertThat(new AiTaskActorDTO("sys_user", 9L, null).ownerUserId()).isNull();
        assertThatThrownBy(() -> new AiTaskActorDTO("app_user", null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiTaskActorDTO("sys_user", 0L, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiTaskActorDTO("app_user", 7L, 8L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workflowPayloadIsBoundedAndRejectsExecutionConfigurationFacts() throws Exception {
        WorkflowAiTaskPayloadDTO safe = new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "a".repeat(64),
            Map.of("prompt", jsonMapper.readTree("\"hello\"")));
        assertThat(safe.inputs()).containsKey("prompt");

        assertThatThrownBy(() -> new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "a".repeat(64),
            Map.of("provider", jsonMapper.readTree("\"runninghub\""))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "a".repeat(64),
            Map.of("safe", jsonMapper.readTree("{\"executionConfig\":{}}"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "forbidden workflow field: {0}")
    @MethodSource("canonicalForbiddenFields")
    void workflowPayloadRejectsCanonicalForbiddenFieldsAtEveryDepth(String forbiddenField) throws Exception {
        assertThatThrownBy(() -> new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "a".repeat(64),
            Map.of(forbiddenField, jsonMapper.readTree("\"sensitive\""))))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "a".repeat(64),
            Map.of("safe", jsonMapper.readTree("{\"nested\":{\"" + forbiddenField + "\":\"sensitive\"}}"))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workflowCommandsAndResultsExposeOnlyStableInternalFacts() throws Exception {
        WorkflowAiTaskPayloadDTO payload = new WorkflowAiTaskPayloadDTO("101", "201", "sha256:" + "b".repeat(64),
            Map.of("prompt", jsonMapper.readTree("\"hello\"")));
        CreateWorkflowAiTaskDTO command = new CreateWorkflowAiTaskDTO(AiTaskType.WORKFLOW_TEMPLATE_GENERATE,
            AiTaskResourceType.WORKFLOW_ORDER, "101", "workflow-key", "c".repeat(64), payload);
        assertThat(command.payload()).isSameAs(payload);

        WorkflowAiTaskResultPayloadDTO result = new WorkflowAiTaskResultPayloadDTO(List.of("301"),
            Map.of("durationMs", jsonMapper.readTree("1200")));
        assertThat(result.resultAssetIds()).containsExactly("301");
        assertThatThrownBy(() -> new WorkflowAiTaskResultPayloadDTO(List.of("301"),
            Map.of("remoteUrl", jsonMapper.readTree("\"https://example.invalid/file\""))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void workflowPayloadsRoundTripThroughTheProductionStrictReader() throws Exception {
        WorkflowAiTaskPayloadDTO request = new WorkflowAiTaskPayloadDTO("101", "201",
            "sha256:" + "d".repeat(64), Map.of("prompt", jsonMapper.readTree("\"hello\"")));
        String requestJson = jsonMapper.writeValueAsString(request);
        WorkflowAiTaskPayloadDTO decodedRequest = jsonMapper.readerFor(WorkflowAiTaskPayloadDTO.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(requestJson);
        assertThat(decodedRequest).isEqualTo(request);

        WorkflowAiTaskResultPayloadDTO result = new WorkflowAiTaskResultPayloadDTO(List.of("301"),
            Map.of("durationMs", jsonMapper.readTree("1200")));
        String resultJson = jsonMapper.writeValueAsString(result);
        WorkflowAiTaskResultPayloadDTO decodedResult = jsonMapper.readerFor(WorkflowAiTaskResultPayloadDTO.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(resultJson);
        assertThat(decodedResult).isEqualTo(result);
    }

    static List<String> canonicalForbiddenFields() throws Exception {
        Path contract = contractDirectory().resolve("user-wire-forbidden-fields.json");
        return JsonMapper.builder().build().readTree(Files.readString(contract, StandardCharsets.UTF_8))
            .required("forbiddenFields").valueStream().map(node -> node.asText()).toList();
    }

    private static Path contractDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path direct = current.resolve("docs/contracts/discovery-runninghub");
            if (Files.isDirectory(direct)) {
                return direct;
            }
            current = current.getParent();
        }
        return Path.of("docs/contracts/discovery-runninghub").toAbsolutePath();
    }
}
