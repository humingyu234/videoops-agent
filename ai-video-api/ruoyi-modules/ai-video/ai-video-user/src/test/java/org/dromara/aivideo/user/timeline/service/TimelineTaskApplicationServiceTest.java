package org.dromara.aivideo.user.timeline.service;

import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskRenderPayloadDTO;
import org.dromara.aivideo.task.dto.CreateFreeAiTaskDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.dto.TimelineDocumentDTO;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.enums.TimelineOutputQuality;
import org.dromara.aivideo.timeline.service.ITimelineDraftService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.TimelineSourceSelectionBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineTaskApplicationServiceTest {

    @Mock
    private ICreationProjectService projectService;
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private ITimelineDraftService draftService;
    @Mock
    private IAiTaskService taskService;

    @Test
    void derivesImagePromptTextFromTheOwnersPersistedScriptSnapshot() {
        CreateImagePromptTaskBo body = new CreateImagePromptTaskBo();
        body.setIdempotencyKey("image-task-1");
        body.setExpectedRevision("3");
        body.setStyle("cinematic");
        TimelineSourceSelectionBo selection = new TimelineSourceSelectionBo();
        selection.setSourceStartOffset(1);
        selection.setSourceEndOffset(3);
        body.setSourceSelection(selection);
        when(projectService.getOwned(7L, "88")).thenReturn(project());
        when(draftService.getOwned(7L, "88")).thenReturn(draft());
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(taskService.createFreeTask(eq(7L), any(CreateFreeAiTaskDTO.class))).thenReturn(task());

        var result = service().createImagePrompt(7L, "88", body);

        assertThat(result.taskId()).isEqualTo("701");
        ArgumentCaptor<CreateFreeAiTaskDTO> command = ArgumentCaptor.forClass(CreateFreeAiTaskDTO.class);
        verify(taskService).createFreeTask(eq(7L), command.capture());
        assertThat(command.getValue().requestDigest()).matches("[0-9a-f]{64}");
        AiTaskImagePromptPayloadDTO payload = (AiTaskImagePromptPayloadDTO) command.getValue().payload();
        assertThat(payload.command().sourceStartOffset()).isEqualTo(1);
        assertThat(payload.command().sourceEndOffset()).isEqualTo(3);
        assertThat(payload.command().sourceText()).isEqualTo("乙丙");
        assertThat(payload.command().contextBefore()).isEmpty();
        assertThat(payload.command().contextAfter()).isEmpty();
    }

    @Test
    void renderUsesQualityPresetForTheTaskPayloadAndDigest() {
        CreateTimelineRenderTaskBo body = new CreateTimelineRenderTaskBo();
        body.setIdempotencyKey("render-task-1");
        body.setExpectedRevision("3");
        CreateTimelineRenderTaskBo.OutputConfig outputConfig = new CreateTimelineRenderTaskBo.OutputConfig();
        outputConfig.setResolutionPreset("match_canvas");
        outputConfig.setFrameRate(30);
        outputConfig.setQualityPreset("high");
        body.setOutputConfig(outputConfig);
        when(projectService.getOwned(7L, "88")).thenReturn(project());
        when(draftService.getOwned(7L, "88")).thenReturn(draft());
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(taskService.createFreeTask(eq(7L), any(CreateFreeAiTaskDTO.class))).thenReturn(task());

        service().createRender(7L, "88", body);

        ArgumentCaptor<CreateFreeAiTaskDTO> command = ArgumentCaptor.forClass(CreateFreeAiTaskDTO.class);
        verify(taskService).createFreeTask(eq(7L), command.capture());
        assertThat(command.getValue().requestDigest()).isEqualTo(digest(
            "timeline_render", "88", "3", "match_canvas", "30", "high"));
        AiTaskRenderPayloadDTO payload = (AiTaskRenderPayloadDTO) command.getValue().payload();
        assertThat(payload.command().outputConfig().qualityPreset()).isEqualTo(TimelineOutputQuality.HIGH);
        assertThat(payload.command().fontRegistryVersion()).isEqualTo(TimelineContractLimits.FONT_REGISTRY_VERSION);
        assertThat(payload.command().fontRegistrySha256()).isEqualTo(TimelineContractLimits.FONT_REGISTRY_SHA256);
    }

    private TimelineTaskApplicationService service() {
        return new TimelineTaskApplicationService(projectService, assetService, draftService, taskService);
    }

    private ICreationProjectService.CreationProjectDTO project() {
        return new ICreationProjectService.CreationProjectDTO("88", "Project", "digital_human_job", "99", "100",
            "101", "editing", 1080, 1920, 30, 10_000L, 3L, "timeline-1", null, Instant.EPOCH, Instant.EPOCH);
    }

    private ITimelineDraftService.TimelineDraftView draft() {
        return new ITimelineDraftService.TimelineDraftView("88", "700", "3", "timeline-1", "a".repeat(64),
            new TimelineDocumentDTO("timeline-1", null, List.of()), Instant.EPOCH);
    }

    private DigitalHumanCreationSourceDTO source() {
        return new DigitalHumanCreationSourceDTO("99", "100", "101", "甲乙丙", 10_000L, 1080, 1920, 30, List.of());
    }

    private AiTaskDTO task() {
        return new AiTaskDTO("701", "timeline_image_prompt_generate", "queued", "queued", "creation_project",
            "88", "88", "3", null, null, null, null, "2026-08-08T00:00:00Z", "2026-08-08T00:00:00Z", null,
            0, true, false);
    }

    private String digest(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
