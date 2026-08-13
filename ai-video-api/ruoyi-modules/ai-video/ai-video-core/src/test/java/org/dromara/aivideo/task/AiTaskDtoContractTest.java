package org.dromara.aivideo.task;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import tools.jackson.databind.ObjectMapper;
import org.dromara.aivideo.task.dto.AiTaskCompletionDTO;
import org.dromara.aivideo.task.dto.AiTaskDispatchResultDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskFancyTextResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskRequestPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSubtitleAlignmentResultPayloadDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.timeline.dto.TimelineFancyTextSuggestionResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleAlignmentResultDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
public class AiTaskDtoContractTest {

    private static final String TASK_DTO = "org.dromara.aivideo.task.dto.";
    private static final String TASK_ENUM = "org.dromara.aivideo.task.enums.";
    private static final String TIMELINE_DTO = "org.dromara.aivideo.timeline.dto.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void recordsFreezeComponentOrderJavaTypesAndStrongPayloadBoundaries() throws Exception {
        Map<String, List<Component>> contracts = new LinkedHashMap<>();
        contracts.put(TASK_DTO + "CreateFreeAiTaskDTO", components(
            "taskType", TASK_ENUM + "AiTaskType", "resourceType", TASK_ENUM + "AiTaskResourceType",
            "resourceId", "java.lang.String", "projectId", "java.lang.String", "draftRevision", "java.lang.String",
            "inputVersionId", "java.lang.String", "idempotencyKey", "java.lang.String", "requestDigest", "java.lang.String",
            "quotaPolicyVersion", "java.lang.String", "estimatedUsage", "long",
            "payload", TASK_DTO + "AiTaskRequestPayloadDTO"));
        contracts.put(TASK_DTO + "AiTaskImagePromptPayloadDTO", components(
            "command", TIMELINE_DTO + "TimelineImagePromptCommandDTO"));
        contracts.put(TASK_DTO + "AiTaskFancyTextPayloadDTO", components(
            "command", TIMELINE_DTO + "TimelineFancyTextSuggestionCommandDTO"));
        contracts.put(TASK_DTO + "AiTaskSubtitleAlignmentPayloadDTO", components(
            "command", TIMELINE_DTO + "TimelineSubtitleAlignmentCommandDTO"));
        contracts.put(TASK_DTO + "AiTaskRenderPayloadDTO", components(
            "command", TIMELINE_DTO + "TimelineRenderCommandDTO"));
        contracts.put(TASK_DTO + "AiTaskImagePromptResultPayloadDTO", components(
            "result", TIMELINE_DTO + "TimelineImagePromptResultDTO"));
        contracts.put(TASK_DTO + "AiTaskFancyTextResultPayloadDTO", components(
            "result", TIMELINE_DTO + "TimelineFancyTextSuggestionResultDTO"));
        contracts.put(TASK_DTO + "AiTaskSubtitleAlignmentResultPayloadDTO", components(
            "result", TIMELINE_DTO + "TimelineSubtitleAlignmentResultDTO"));
        contracts.put(TASK_DTO + "AiTaskDTO", components(
            "taskId", "java.lang.String", "taskType", "java.lang.String", "status", "java.lang.String",
            "stage", "java.lang.String", "resourceType", "java.lang.String", "resourceId", "java.lang.String",
            "projectId", "java.lang.String", "draftRevision", "java.lang.String", "inputVersionId", "java.lang.String",
            "resultAssetId", "java.lang.String", "errorCode", "java.lang.String", "safeMessage", "java.lang.String",
            "createdAt", "java.lang.String", "updatedAt", "java.lang.String",
            "resultPayload", TASK_DTO + "AiTaskResultPayloadDTO", "progress", "int",
            "cancellable", "boolean", "retryable", "boolean"));
        contracts.put(TASK_DTO + "AiTaskSummaryDTO", components(
            "taskId", "java.lang.String", "taskType", "java.lang.String", "status", "java.lang.String",
            "stage", "java.lang.String", "resourceType", "java.lang.String", "resourceId", "java.lang.String",
            "projectId", "java.lang.String", "createdAt", "java.lang.String", "updatedAt", "java.lang.String",
            "errorCode", "java.lang.String", "safeMessage", "java.lang.String", "progress", "int",
            "cancellable", "boolean", "retryable", "boolean"));
        contracts.put(TASK_DTO + "AiTaskQueryDTO", components(
            "taskType", "java.lang.String", "status", "java.lang.String", "resourceType", "java.lang.String",
            "resourceId", "java.lang.String", "projectId", "java.lang.String", "keyword", "java.lang.String"));
        contracts.put(TASK_DTO + "AiTaskExecutionDTO", components(
            "executionId", "java.lang.String", "taskId", "java.lang.String", "executionStatus", "java.lang.String",
            "workerId", "java.lang.String", "leaseExpiresAt", "java.lang.String", "startedAt", "java.lang.String",
            "finishedAt", "java.lang.String", "inputVersionId", "java.lang.String", "resultAssetId", "java.lang.String",
            "errorCode", "java.lang.String", "executionNo", "int", "rowVersion", "int", "progress", "int"));
        contracts.put(TASK_DTO + "AiTaskAttemptDTO", components(
            "attemptId", "java.lang.String", "executionId", "java.lang.String", "status", "java.lang.String",
            "workerId", "java.lang.String", "startedAt", "java.lang.String", "finishedAt", "java.lang.String",
            "errorCode", "java.lang.String", "attemptNo", "int"));
        contracts.put(TASK_DTO + "RetryAiTaskDTO", components(
            "sourceTaskId", "java.lang.String", "idempotencyKey", "java.lang.String", "requestDigest", "java.lang.String"));
        contracts.put(TASK_DTO + "AiTaskDispatchResultDTO", components(
            "outcome", "java.lang.String", "taskId", "java.lang.String", "executionId", "java.lang.String"));

        for (Map.Entry<String, List<Component>> contract : contracts.entrySet()) {
            assertRecordContract(contract.getKey(), contract.getValue());
        }
    }

    @Test
    void sealedPayloadPermitsAreExactAndDoNotAdmitGenericMaps() throws Exception {
        assertPermits(TASK_DTO + "AiTaskRequestPayloadDTO", Set.of(
            TASK_DTO + "AiTaskImagePromptPayloadDTO",
            TASK_DTO + "AiTaskFancyTextPayloadDTO",
            TASK_DTO + "AiTaskSubtitleAlignmentPayloadDTO",
            TASK_DTO + "AiTaskRenderPayloadDTO",
            TASK_DTO + "WorkflowAiTaskPayloadDTO"));
        assertPermits(TASK_DTO + "AiTaskResultPayloadDTO", Set.of(
            TASK_DTO + "AiTaskImagePromptResultPayloadDTO",
            TASK_DTO + "AiTaskFancyTextResultPayloadDTO",
            TASK_DTO + "AiTaskSubtitleAlignmentResultPayloadDTO",
            TASK_DTO + "WorkflowAiTaskResultPayloadDTO"));

        for (String className : List.of(
            TASK_DTO + "CreateFreeAiTaskDTO", TASK_DTO + "AiTaskDTO", TASK_DTO + "AiTaskCompletionDTO")) {
            Class<?> type = load(className);
            assertFalse(Arrays.stream(type.isRecord() ? type.getRecordComponents() : new RecordComponent[0])
                .anyMatch(component -> Map.class.isAssignableFrom(component.getType())), className);
        }
    }

    @Test
    void enumsFreezeAllWireLiterals() throws Exception {
        Map<String, List<String>> contracts = new LinkedHashMap<>();
        contracts.put(TASK_ENUM + "AiTaskStatus", List.of("pending", "queued", "running", "success", "failed", "cancelled"));
        contracts.put(TASK_ENUM + "AiTaskExecutionStatus", List.of("queued", "running", "success", "failed", "cancelled"));
        contracts.put(TASK_ENUM + "AiTaskAttemptStatus", List.of("running", "success", "failed", "cancelled", "abandoned"));
        contracts.put(TASK_ENUM + "AiTaskType", List.of(
            "timeline_image_prompt_generate", "timeline_fancy_text_suggest", "timeline_subtitle_align", "timeline_render",
            "workflow_template_generate", "workflow_template_test"));
        contracts.put(TASK_ENUM + "AiTaskStage", List.of(
            "queued", "waiting_for_dispatch", "preparing_inputs", "submitting_to_provider",
            "confirming_provider_acceptance", "provider_processing", "processing_results",
            "preparing_assets", "reading_assets", "building_ass", "building_render_plan", "encoding",
            "verifying_output", "registering_output", "completed", "failed", "cancelled"));
        contracts.put(TASK_ENUM + "AiTaskResourceType", List.of(
            "creation_project", "workflow_order", "workflow_template"));

        for (Map.Entry<String, List<String>> contract : contracts.entrySet()) {
            assertEnumWireValues(contract.getKey(), contract.getValue());
        }
    }

    @Test
    void internalLeaseMutationDtosAreFinalIgnoredAndCannotLeakTokens() throws Exception {
        Map<String, List<Component>> contracts = new LinkedHashMap<>();
        contracts.put(TASK_DTO + "AiTaskLeaseDTO", components(
            "taskId", "java.lang.String", "executionId", "java.lang.String", "attemptId", "java.lang.String",
            "leaseToken", "java.lang.String", "workerId", "java.lang.String", "actorType", "java.lang.String",
            "actorId", "java.lang.String",
            "inputVersionId", "java.lang.String", "executionNo", "int", "attemptNo", "int", "rowVersion", "int"));
        contracts.put(TASK_DTO + "AiTaskProgressDTO", components(
            "executionId", "java.lang.String", "leaseToken", "java.lang.String", "expectedRowVersion", "int",
            "percent", "int", "stage", TASK_ENUM + "AiTaskStage", "safeMessage", "java.lang.String"));
        contracts.put(TASK_DTO + "AiTaskCompletionDTO", components(
            "executionId", "java.lang.String", "leaseToken", "java.lang.String", "resultAssetId", "java.lang.String",
            "errorCode", "java.lang.String", "safeMessage", "java.lang.String",
            "resultPayload", TASK_DTO + "AiTaskResultPayloadDTO", "expectedRowVersion", "int",
            "success", "boolean", "retryable", "boolean"));

        for (Map.Entry<String, List<Component>> contract : contracts.entrySet()) {
            Class<?> type = load(contract.getKey());
            assertTrue(Modifier.isFinal(type.getModifiers()), contract.getKey());
            assertFalse(type.isRecord(), contract.getKey());
            assertFalse(Serializable.class.isAssignableFrom(type), contract.getKey());
            assertNotNull(type.getAnnotation(JsonIgnoreType.class), contract.getKey());
            assertEquals(contract.getKey().endsWith("AiTaskLeaseDTO") ? 2 : 1,
                type.getDeclaredConstructors().length, contract.getKey());
            assertConstructorAndGetters(type, contract.getValue());
            assertTrue(Arrays.stream(type.getDeclaredMethods()).map(Method::getName).noneMatch("toString"::equals),
                contract.getKey());
        }

        for (String publicType : List.of(TASK_DTO + "AiTaskDTO", TASK_DTO + "AiTaskSummaryDTO")) {
            Class<?> type = load(publicType);
            assertFalse(Arrays.stream(type.getRecordComponents()).anyMatch(component -> component.getName().equals("leaseToken")));
            assertFalse(Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equals("leaseToken")));
        }
    }

    @Test
    void internalProgressAndCompletionRejectUnsafeMessageLength() throws Exception {
        Class<?> stageType = load(TASK_ENUM + "AiTaskStage");
        Object stage = stageType.getEnumConstants()[0];

        Class<?> progress = load(TASK_DTO + "AiTaskProgressDTO");
        Constructor<?> progressConstructor = progress.getDeclaredConstructor(
            String.class, String.class, int.class, int.class, stageType, String.class);
        progressConstructor.newInstance("1", "secret", 0, 0, stage, "安".repeat(200));
        InvocationTargetException progressFailure = assertThrows(InvocationTargetException.class,
            () -> progressConstructor.newInstance("1", "secret", 0, 0, stage, "安".repeat(201)));
        assertInstanceOf(IllegalArgumentException.class, progressFailure.getCause());

        Class<?> completion = load(TASK_DTO + "AiTaskCompletionDTO");
        Class<?> resultPayload = load(TASK_DTO + "AiTaskResultPayloadDTO");
        Constructor<?> completionConstructor = completion.getDeclaredConstructor(
            String.class, String.class, String.class, String.class, String.class, resultPayload,
            int.class, boolean.class, boolean.class);
        completionConstructor.newInstance("1", "secret", "asset-1", null, "安".repeat(200), null, 0, true, false);
        InvocationTargetException completionFailure = assertThrows(InvocationTargetException.class,
            () -> completionConstructor.newInstance(
                "1", "secret", "asset-1", null, "安".repeat(201), null, 0, true, false));
        assertInstanceOf(IllegalArgumentException.class, completionFailure.getCause());
    }

    @Test
    void createFreeTaskPayloadMustMatchTaskType() {
        Map<AiTaskType, AiTaskRequestPayloadDTO> matchingPayloads = Map.of(
            AiTaskType.TIMELINE_IMAGE_PROMPT_GENERATE, new AiTaskImagePromptPayloadDTO(null),
            AiTaskType.TIMELINE_FANCY_TEXT_SUGGEST, new AiTaskFancyTextPayloadDTO(null),
            AiTaskType.TIMELINE_SUBTITLE_ALIGN, new AiTaskSubtitleAlignmentPayloadDTO(null),
            AiTaskType.TIMELINE_RENDER, new AiTaskRenderPayloadDTO(null));

        matchingPayloads.forEach((taskType, payload) -> {
            assertDoesNotThrow(() -> createFreeTask(taskType, payload));
            matchingPayloads.values().stream()
                .filter(candidate -> candidate.getClass() != payload.getClass())
                .forEach(candidate -> assertThrows(IllegalArgumentException.class,
                    () -> createFreeTask(taskType, candidate)));
        });
        assertThrows(IllegalArgumentException.class,
            () -> createFreeTask(null, new AiTaskRenderPayloadDTO(null)));
        assertThrows(IllegalArgumentException.class,
            () -> createFreeTask(AiTaskType.TIMELINE_RENDER, null));
    }

    @Test
    void successfulCompletionRequiresExactlyOneMatchingOutputChannel() {
        List<AiTaskResultPayloadDTO> suggestionResults = List.of(
            new AiTaskImagePromptResultPayloadDTO(new TimelineImagePromptResultDTO("task-1", List.of())),
            new AiTaskFancyTextResultPayloadDTO(new TimelineFancyTextSuggestionResultDTO("task-1", List.of())),
            new AiTaskSubtitleAlignmentResultPayloadDTO(
                new TimelineSubtitleAlignmentResultDTO("task-1", "trusted_cue", List.of())));

        suggestionResults.forEach(payload -> assertDoesNotThrow(
            () -> completion(null, payload, true)));
        assertDoesNotThrow(() -> completion("asset-1", null, true));

        assertThrows(IllegalArgumentException.class, () -> completion(null, null, true));
        assertThrows(IllegalArgumentException.class, () -> completion(" ", null, true));
        assertThrows(IllegalArgumentException.class,
            () -> completion("asset-1", suggestionResults.get(0), true));

        assertDoesNotThrow(() -> completion(null, null, false));
        assertThrows(IllegalArgumentException.class,
            () -> completion("asset-on-failure", null, false));
        assertThrows(IllegalArgumentException.class,
            () -> completion(null, suggestionResults.get(0), false));
        assertThrows(IllegalArgumentException.class,
            () -> completion("asset-on-failure", suggestionResults.get(0), false));
    }

    @Test
    void dispatchOutcomeIsWhitelisted() {
        for (String outcome : List.of("none", "completed", "failed", "cancelled", "lease_lost")) {
            assertDoesNotThrow(() -> new AiTaskDispatchResultDTO(outcome, "task-1", "execution-1"));
        }
        assertThrows(IllegalArgumentException.class,
            () -> new AiTaskDispatchResultDTO("unknown", "task-1", "execution-1"));
        assertThrows(IllegalArgumentException.class,
            () -> new AiTaskDispatchResultDTO(null, "task-1", "execution-1"));
    }

    private static CreateFreeAiTaskDTO createFreeTask(AiTaskType taskType, AiTaskRequestPayloadDTO payload) {
        return new CreateFreeAiTaskDTO(taskType, AiTaskResourceType.CREATION_PROJECT, "resource-1", "project-1",
            "1", "version-1", "idempotency-1", "digest-1", "quota-v1", 1L, payload);
    }

    private static AiTaskCompletionDTO completion(String resultAssetId, AiTaskResultPayloadDTO resultPayload,
                                                   boolean success) {
        return new AiTaskCompletionDTO("execution-1", "lease-1", resultAssetId,
            success ? null : "FAILED", success ? null : "failed", resultPayload, 0, success, !success);
    }

    private static void assertPermits(String className, Set<String> expected) throws Exception {
        Class<?> type = load(className);
        assertTrue(type.isSealed(), className);
        assertEquals(expected, Arrays.stream(type.getPermittedSubclasses()).map(Class::getName).collect(Collectors.toSet()));
    }

    private static void assertConstructorAndGetters(Class<?> type, List<Component> expected) throws Exception {
        Constructor<?> constructor = Arrays.stream(type.getDeclaredConstructors())
            .filter(candidate -> Arrays.stream(candidate.getGenericParameterTypes())
                .map(java.lang.reflect.Type::getTypeName).toList()
                .equals(expected.stream().map(Component::type).toList()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing frozen constructor: " + type.getName()));
        assertEquals(expected.stream().map(Component::type).toList(),
            Arrays.stream(constructor.getGenericParameterTypes()).map(java.lang.reflect.Type::getTypeName).toList());
        assertEquals(expected.size(), Arrays.stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).count());
        for (Component component : expected) {
            String getterName = (component.type().equals("boolean") ? "is" : "get")
                + Character.toUpperCase(component.name().charAt(0)) + component.name().substring(1);
            Method getter = type.getMethod(getterName);
            assertEquals(component.type(), getter.getGenericReturnType().getTypeName(), type.getName() + "." + getterName);
        }
    }

    private static void assertEnumWireValues(String className, List<String> expected) throws Exception {
        Class<?> enumType = load(className);
        assertTrue(enumType.isEnum(), className);
        List<String> actual = new ArrayList<>();
        for (Object constant : enumType.getEnumConstants()) {
            actual.add(MAPPER.valueToTree(constant).textValue());
        }
        assertEquals(expected, actual, className);
        for (String value : expected) {
            assertNotNull(MAPPER.readValue('"' + value + '"', enumType));
        }
        assertThrows(Exception.class, () -> MAPPER.readValue("\"unknown_contract_value\"", enumType));
    }

    private static void assertRecordContract(String className, List<Component> expected) throws Exception {
        Class<?> type = load(className);
        assertTrue(type.isRecord(), className + " must be a record");
        RecordComponent[] actual = type.getRecordComponents();
        assertEquals(expected.size(), actual.length, className + " component count");
        for (int index = 0; index < expected.size(); index++) {
            assertEquals(expected.get(index).name(), actual[index].getName(), className + " component " + index);
            assertEquals(expected.get(index).type(), actual[index].getGenericType().getTypeName(),
                className + "." + actual[index].getName());
        }
        assertEquals(expected.size(), Arrays.stream(type.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers())).count(), className + " must not add instance fields");
    }

    private static List<Component> components(String... nameAndType) {
        if (nameAndType.length % 2 != 0) {
            throw new IllegalArgumentException("name/type pairs required");
        }
        List<Component> result = new ArrayList<>();
        for (int index = 0; index < nameAndType.length; index += 2) {
            result.add(new Component(nameAndType[index], nameAndType[index + 1]));
        }
        return result;
    }

    private static Class<?> load(String className) throws ClassNotFoundException {
        return Class.forName(className);
    }

    private record Component(String name, String type) {
    }
}
