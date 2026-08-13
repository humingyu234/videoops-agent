package org.dromara.aivideo.user.creation;

import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.user.creation.domain.bo.CreateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.bo.UpdateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.vo.CreationAssetVo;
import org.dromara.aivideo.user.creation.domain.vo.LatestCreationOutputVo;
import org.dromara.aivideo.user.task.controller.AiTaskController;
import org.dromara.aivideo.user.task.domain.bo.AiTaskQueryBo;
import org.dromara.aivideo.user.task.domain.bo.RetryAiTaskBo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskListItemVo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskVo;
import org.dromara.aivideo.user.timeline.controller.TimelineTaskController;
import org.dromara.aivideo.user.timeline.controller.TimelineVersionController;
import org.dromara.aivideo.user.timeline.domain.bo.CreateFancyTextSuggestionTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateSubtitleAlignmentTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineConflictCopyBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.RestoreTimelineVersionBo;
import org.dromara.aivideo.user.timeline.domain.bo.SaveTimelineDraftBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineSourceSelectionBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineVersionQueryBo;
import org.dromara.aivideo.user.timeline.domain.vo.TimelineVersionVo;
import org.dromara.common.core.domain.PageResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HTTP-only contract regression tests. Business services continue to own owner checks and state transitions.
 */
@Tag("dev")
class CreationTimelineHttpContractTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void mutatingAppPayloadsRejectForgedOwnerAndStorageFields() {
        assertRejected(CreateCreationProjectBo.class, """
            {"sourceType":"digital_human_job","sourceId":"44","idempotencyKey":"project-1","ownerUserId":"7"}
            """);
        assertRejected(UpdateCreationProjectBo.class, """
            {"projectTitle":"title","ownerUserId":"7"}
            """);
        assertRejected(SaveTimelineDraftBo.class, """
            {"idempotencyKey":"draft-1","expectedRevision":"1","schemaVersion":"timeline-1","timeline":{},"ownerUserId":"7"}
            """);
        assertRejected(CreateTimelineVersionBo.class, """
            {"idempotencyKey":"version-1","expectedRevision":"1","actorId":"7"}
            """);
        assertRejected(RestoreTimelineVersionBo.class, """
            {"idempotencyKey":"restore-1","expectedRevision":"1","projectId":"88"}
            """);
        assertRejected(CreateTimelineConflictCopyBo.class, """
            {"idempotencyKey":"copy-1","baseRevision":"1","schemaVersion":"timeline-1","timeline":{},"ownerUserId":"7"}
            """);
        assertRejected(CreateImagePromptTaskBo.class, """
            {"idempotencyKey":"image-1","expectedRevision":"1","sourceSelection":{"sourceStartOffset":0,"sourceEndOffset":1},"style":"cinematic","storageKey":"private/x"}
            """);
        assertRejected(CreateFancyTextSuggestionTaskBo.class, """
            {"idempotencyKey":"fancy-1","expectedRevision":"1","sourceSelection":{"sourceStartOffset":0,"sourceEndOffset":1},"animationIntensity":"normal","ownerUserId":"7"}
            """);
        assertRejected(CreateSubtitleAlignmentTaskBo.class, """
            {"idempotencyKey":"subtitle-1","expectedRevision":"1","subtitleElementIds":["subtitle-1"],"actorId":"7"}
            """);
        assertRejected(CreateTimelineRenderTaskBo.class, """
            {"idempotencyKey":"render-1","expectedRevision":"1","outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"high","storageKey":"private/x"}}
            """);
        assertRejected(TimelineSourceSelectionBo.class, """
            {"sourceStartOffset":0,"sourceEndOffset":1,"ownerUserId":"7"}
            """);
        assertRejected(RetryAiTaskBo.class, """
            {"idempotencyKey":"retry-1","ownerUserId":"7"}
            """);
    }

    @Test
    void taskEndpointsUseExplicitHttpViewsRatherThanCoreDtos() throws Exception {
        List<Method> taskMethods = List.of(
            AiTaskController.class.getDeclaredMethod("list", AiTaskQueryBo.class),
            AiTaskController.class.getDeclaredMethod("detail", String.class),
            AiTaskController.class.getDeclaredMethod("cancel", String.class, RetryAiTaskBo.class),
            AiTaskController.class.getDeclaredMethod("retry", String.class, RetryAiTaskBo.class),
            TimelineTaskController.class.getDeclaredMethod("createImagePrompt", String.class, CreateImagePromptTaskBo.class),
            TimelineTaskController.class.getDeclaredMethod("createFancyText", String.class, CreateFancyTextSuggestionTaskBo.class),
            TimelineTaskController.class.getDeclaredMethod("createSubtitleAlignment", String.class,
                CreateSubtitleAlignmentTaskBo.class),
            TimelineTaskController.class.getDeclaredMethod("createRender", String.class, CreateTimelineRenderTaskBo.class)
        );

        assertThat(taskMethods).allSatisfy(method -> assertThat(method.getGenericReturnType().getTypeName())
            .doesNotContain(AiTaskDTO.class.getName(), AiTaskSummaryDTO.class.getName()));
        assertThat(AiTaskController.class.getDeclaredMethod("list", AiTaskQueryBo.class).getGenericReturnType()
            .getTypeName()).contains(AiTaskListItemVo.class.getName());
        assertThat(AiTaskController.class.getDeclaredMethod("detail", String.class).getGenericReturnType()
            .getTypeName()).contains(AiTaskVo.class.getName());
    }

    @Test
    void listAndResponseShapesExposeOnlyTheFrozenHttpSurface() throws Exception {
        String versionListType = TimelineVersionController.class
            .getDeclaredMethod("list", String.class, TimelineVersionQueryBo.class).getGenericReturnType().getTypeName();
        assertThat(versionListType).contains(PageResult.class.getName(), TimelineVersionVo.class.getName());

        assertNoInternalFields(AiTaskVo.class);
        assertNoInternalFields(AiTaskListItemVo.class);
        assertNoInternalFields(CreationAssetVo.class);
        assertNoInternalFields(LatestCreationOutputVo.class);
    }

    @Test
    void renderRequestAcceptsOnlyQualityPreset() throws Exception {
        CreateTimelineRenderTaskBo request = jsonMapper.readValue("""
            {"idempotencyKey":"render-quality-1","expectedRevision":"1",\
            "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"qualityPreset":"high"}}
            """, CreateTimelineRenderTaskBo.class);

        assertThat(request.getOutputConfig().getQualityPreset()).isEqualTo("high");
        assertRejected(CreateTimelineRenderTaskBo.class, """
            {"idempotencyKey":"render-quality-1","expectedRevision":"1",\
            "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"quality":"high"}}
            """);
    }

    @Test
    void latestCreationOutputWireViewContainsExactlyTheFourC2Fields() {
        List<String> names = Arrays.stream(LatestCreationOutputVo.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        assertThat(names).containsExactly("projectId", "outputAssetId", "taskId", "createdAt");
        assertThat(names).doesNotContain("assetId", "mimeType", "sizeBytes", "previewUrl", "downloadUrl");
    }

    private void assertRejected(Class<?> type, String json) {
        assertThatThrownBy(() -> jsonMapper.readValue(json, type)).isInstanceOf(Exception.class);
    }

    private void assertNoInternalFields(Class<?> wireType) {
        List<String> names = Arrays.stream(wireType.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();
        assertThat(names).doesNotContain("ownerUserId", "actorId", "storageKey", "requestDigest",
            "requestPayload", "leaseToken", "workerId");
    }

}
