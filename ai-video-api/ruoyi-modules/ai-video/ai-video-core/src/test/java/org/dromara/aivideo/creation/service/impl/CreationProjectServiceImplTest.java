package org.dromara.aivideo.creation.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.dto.CreationOutputDTO;
import org.dromara.aivideo.creation.dto.DigitalHumanCreationSourceDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.constant.TimelineContractLimits;
import org.dromara.aivideo.timeline.constant.TimelineErrorCodes;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.dto.TimelineSubtitleElementDTO;
import org.dromara.aivideo.timeline.service.ISubtitleNormalizationService;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class CreationProjectServiceImplTest {

    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineDraftMapper draftMapper;
    @Mock
    private ICreationAssetService assetService;
    @Mock
    private CreationAssetMapper assetMapper;
    @Mock
    private AiTaskMapper taskMapper;

    @BeforeAll
    static void initializeMybatisMetadata() {
        initialize(CreationProject.class);
        initialize(TimelineDraft.class);
    }

    @Test
    void createsFixedCanvasProjectFromTheResolvedDigitalHumanSourceWithDifferentMediaFacts() {
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(projectMapper.insert(any(CreationProject.class))).thenReturn(1);
        when(draftMapper.insert(any(TimelineDraft.class))).thenReturn(1);

        ICreationProjectService.CreationProjectDTO created = service().create(7L, command());

        ArgumentCaptor<CreationProject> projectCaptor = ArgumentCaptor.forClass(CreationProject.class);
        ArgumentCaptor<TimelineDraft> draftCaptor = ArgumentCaptor.forClass(TimelineDraft.class);
        verify(projectMapper).insert(projectCaptor.capture());
        verify(draftMapper).insert(draftCaptor.capture());

        CreationProject project = projectCaptor.getValue();
        TimelineDraft draft = draftCaptor.getValue();
        assertThat(created.projectId()).isEqualTo(project.getProjectId().toString());
        assertThat(project.getOwnerUserId()).isEqualTo(7L);
        assertThat(project.getSourceType()).isEqualTo("digital_human_job");
        assertThat(project.getSourceRefId()).isEqualTo(99L);
        assertThat(project.getBaseVideoAssetId()).isEqualTo(501L);
        assertThat(project.getPrimaryAudioAssetId()).isEqualTo(502L);
        assertThat(project.getScriptTextSnapshot()).isEqualTo("server snapshot");
        assertThat(project.getCanvasWidth()).isEqualTo(1080);
        assertThat(project.getCanvasHeight()).isEqualTo(1920);
        assertThat(project.getFrameRate()).isEqualTo(30);
        assertThat(project.getDurationMs()).isEqualTo(3_000L);
        assertThat(project.getProjectStatus()).isEqualTo("editing");
        assertThat(project.getActorType()).isEqualTo("app_user");
        assertThat(project.getActorId()).isEqualTo(7L);
        assertThat(draft.getOwnerUserId()).isEqualTo(7L);
        assertThat(draft.getProjectId()).isEqualTo(project.getProjectId());
        assertThat(draft.getRevision()).isEqualTo(1L);
        assertThat(draft.getSchemaVersion()).isEqualTo(TimelineContractLimits.SCHEMA_VERSION);
        assertThat(draft.getDurationMs()).isEqualTo(3_000L);
        assertThat(draft.getContentJson()).contains(
            "timeline-1", "main_video", "subtitle", "server snapshot", "501",
            "\"width\":1080", "\"height\":1920", "\"frameRate\":30", "\"fitMode\":\"cover\"");
        verify(assetService).resolveDigitalHumanSource(7L, "99");
    }

    @Test
    void replaysTheExistingProjectForTheSameOwnerKeyAndRequestDigest() {
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(projectMapper.insert(any(CreationProject.class))).thenReturn(1);
        when(draftMapper.insert(any(TimelineDraft.class))).thenReturn(1);
        ICreationProjectService.CreationProjectDTO first = service().create(7L, command());
        ArgumentCaptor<CreationProject> projectCaptor = ArgumentCaptor.forClass(CreationProject.class);
        ArgumentCaptor<TimelineDraft> draftCaptor = ArgumentCaptor.forClass(TimelineDraft.class);
        verify(projectMapper).insert(projectCaptor.capture());
        verify(draftMapper).insert(draftCaptor.capture());

        reset(projectMapper, draftMapper);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(projectCaptor.getValue());
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draftCaptor.getValue());

        ICreationProjectService.CreationProjectDTO replay = service().create(7L, command());

        assertThat(replay.projectId()).isEqualTo(first.projectId());
        verify(projectMapper, never()).insert(any(CreationProject.class));
        verify(draftMapper, never()).insert(any(TimelineDraft.class));
    }

    @Test
    void rejectsTheSameIdempotencyKeyWhenTheRequestDigestDiffers() {
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(projectMapper.insert(any(CreationProject.class))).thenReturn(1);
        when(draftMapper.insert(any(TimelineDraft.class))).thenReturn(1);
        service().create(7L, command());
        ArgumentCaptor<CreationProject> projectCaptor = ArgumentCaptor.forClass(CreationProject.class);
        verify(projectMapper).insert(projectCaptor.capture());

        reset(projectMapper, draftMapper);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(projectCaptor.getValue());

        assertThatThrownBy(() -> service().create(7L,
            new ICreationProjectService.CreateProjectCommand("digital_human_job", "99", "changed title", "create-99")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(TimelineErrorCodes.TIMELINE_IDEMPOTENCY_CONFLICT));
        verify(projectMapper, never()).insert(any(CreationProject.class));
        verify(draftMapper, never()).insert(any(TimelineDraft.class));
    }

    @Test
    void duplicateKeyRaceReadsBackTheWinningProjectAfterTheTransactionRollsBack() {
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(projectMapper.insert(any(CreationProject.class))).thenReturn(1);
        when(draftMapper.insert(any(TimelineDraft.class))).thenReturn(1);
        ICreationProjectService.CreationProjectDTO first = service().create(7L, command());
        ArgumentCaptor<CreationProject> projectCaptor = ArgumentCaptor.forClass(CreationProject.class);
        ArgumentCaptor<TimelineDraft> draftCaptor = ArgumentCaptor.forClass(TimelineDraft.class);
        verify(projectMapper).insert(projectCaptor.capture());
        verify(draftMapper).insert(draftCaptor.capture());

        reset(projectMapper, draftMapper);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(null, projectCaptor.getValue());
        when(projectMapper.insert(any(CreationProject.class))).thenThrow(new DuplicateKeyException("duplicate"));
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draftCaptor.getValue());

        ICreationProjectService.CreationProjectDTO replay = service().create(7L, command());

        assertThat(replay.projectId()).isEqualTo(first.projectId());
        verify(projectMapper).insert(any(CreationProject.class));
        verify(draftMapper, never()).insert(any(TimelineDraft.class));
    }

    @Test
    void rejectsAnUnreadableOrForeignSourceWithoutWritingAProject() {
        when(assetService.resolveDigitalHumanSource(8L, "99"))
            .thenThrow(new ServiceException("创作来源不可用", TimelineErrorCodes.CREATION_SOURCE_INVALID));

        assertThatThrownBy(() -> service().create(8L, command()))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(TimelineErrorCodes.CREATION_SOURCE_INVALID));

        verify(projectMapper, never()).insert(any(CreationProject.class));
        verifyNoInteractions(draftMapper);
    }

    @Test
    void rejectsMalformedResolvedSourceBeforeOpeningTheCreationTransaction() {
        DigitalHumanCreationSourceDTO malformed = new DigitalHumanCreationSourceDTO(
            "99", "501", null, "server snapshot", 3_000L, 0, 1024, 25, List.of());
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(malformed);

        assertThatThrownBy(() -> service().create(7L, command()))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(TimelineErrorCodes.CREATION_SOURCE_INVALID));

        verify(projectMapper, never()).insert(any(CreationProject.class));
        verifyNoInteractions(draftMapper);
    }

    @Test
    void creationCommandCannotAcceptOwnerScriptTenantWorkspacePathOrMediaUrlFields() {
        assertThat(ICreationProjectService.CreateProjectCommand.class.isRecord()).isTrue();
        assertThat(ICreationProjectService.CreateProjectCommand.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .containsExactly("sourceType", "sourceId", "projectTitle", "idempotencyKey");
    }

    @Test
    void projectAndDraftWriteFailurePropagatesSoTheShortTransactionRollsBack() {
        when(assetService.resolveDigitalHumanSource(7L, "99")).thenReturn(source());
        when(projectMapper.insert(any(CreationProject.class))).thenReturn(1);
        when(draftMapper.insert(any(TimelineDraft.class))).thenThrow(new IllegalStateException("draft failed"));

        assertThatThrownBy(() -> service().create(7L, command())).isInstanceOf(IllegalStateException.class);
        verify(projectMapper, times(1)).insert(any(CreationProject.class));
        verify(draftMapper, times(1)).insert(any(TimelineDraft.class));
    }

    @Test
    void readsAndRenamesOnlyTheCurrentOwnersNonArchivedProject() {
        CreationProject project = project(71L, 7L, "before", null, "editing");
        TimelineDraft draft = draft(project);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);
        when(draftMapper.selectOne(any(Wrapper.class))).thenReturn(draft);
        when(projectMapper.update(any(), any(Wrapper.class))).thenReturn(1);

        ICreationProjectService.CreationProjectDTO loaded = service().getOwned(7L, "71");
        ICreationProjectService.CreationProjectDTO renamed = service().updateTitleOwned(7L, "71",
            new ICreationProjectService.UpdateProjectTitleCommand("after"));

        assertThat(loaded.projectId()).isEqualTo("71");
        assertThat(loaded.projectTitle()).isEqualTo("before");
        assertThat(renamed.projectTitle()).isEqualTo("after");
        verify(projectMapper).update(any(), any(Wrapper.class));
    }

    @Test
    void rejectsTitleChangesForArchivedProjectsBeforeWriting() {
        CreationProject project = project(71L, 7L, "archived", null, "archived");
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);

        assertThatThrownBy(() -> service().updateTitleOwned(7L, "71",
            new ICreationProjectService.UpdateProjectTitleCommand("after")))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(TimelineErrorCodes.CREATION_PROJECT_STATE_CONFLICT));

        verify(projectMapper, never()).update(any(), any(Wrapper.class));
    }

    @Test
    void returnsTheExactC2OutputOnlyAfterValidatingTheAssetAndRootRenderTask() {
        CreationProject project = project(71L, 7L, "ready", 91L, "ready");
        CreationAsset asset = outputAsset(91L, 7L, 701L);
        AiTask task = completedRenderTask(701L, 7L, 71L, 91L);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(asset);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        CreationOutputDTO output = service().getLatestOutputOwned(7L, "71");

        assertThat(output.projectId()).isEqualTo("71");
        assertThat(output.outputAssetId()).isEqualTo("91");
        assertThat(output.taskId()).isEqualTo("701");
        assertThat(output.createdAt()).isEqualTo(Instant.parse("2026-08-09T12:00:00Z"));
        verify(assetMapper).selectOne(any(Wrapper.class));
        verify(taskMapper).selectOne(any(Wrapper.class));
    }

    @Test
    void rejectsLatestOutputWhenTheAssetOrRootTaskFactsDoNotMatchTheProject() {
        CreationProject project = project(71L, 7L, "ready", 91L, "ready");

        assertLatestOutputInvalid(project, outputAsset(91L, 8L, 701L), completedRenderTask(701L, 7L, 71L, 91L));
        assertLatestOutputInvalid(project, outputAsset(91L, 7L, 701L), completedRenderTask(702L, 7L, 71L, 91L));
        assertLatestOutputInvalid(project, outputAsset(91L, 7L, 701L), completedRenderTask(701L, 8L, 71L, 91L));
        assertLatestOutputInvalid(project, outputAsset(91L, 7L, 701L), completedRenderTask(701L, 7L, 72L, 91L));
        assertLatestOutputInvalid(project, outputAsset(91L, 7L, 701L), completedRenderTask(701L, 7L, 71L, 92L));
    }

    private CreationProjectServiceImpl service() {
        ISubtitleNormalizationService normalizer = (script, subtitles, width, safeMargin) ->
            new ISubtitleNormalizationService.NormalizationResult(subtitles.stream()
                .map(this::normalizedSubtitle).toList(), List.of());
        return new CreationProjectServiceImpl(projectMapper, draftMapper, assetService, assetMapper, taskMapper,
            normalizer, JsonMapper.builder().build());
    }

    private TimelineSubtitleElementDTO normalizedSubtitle(TimelineSubtitleElementDTO source) {
        return new TimelineSubtitleElementDTO(source.elementId(), source.elementType(), source.startMs(),
            source.endMs(), source.zIndex(), source.enabled(), source.locked(), source.label(),
            source.sourceTextSnapshot(), source.displayText().replace(" ", ""), source.sourceStartOffset(),
            source.sourceEndOffset(), source.fontCode(), source.fontVersion(), source.fontSha256(),
            source.fontSizePx(), source.color(), source.backgroundEnabled(), source.backgroundColor(),
            source.outlineEnabled(), source.outlineColor(), source.outlineWidthPx(), source.safeAreaAnchor(),
            source.alignment());
    }

    private void assertLatestOutputInvalid(CreationProject project, CreationAsset asset, AiTask task) {
        reset(projectMapper, assetMapper, taskMapper);
        when(projectMapper.selectOne(any(Wrapper.class))).thenReturn(project);
        when(assetMapper.selectOne(any(Wrapper.class))).thenReturn(asset);
        when(taskMapper.selectOne(any(Wrapper.class))).thenReturn(task);

        assertThatThrownBy(() -> service().getLatestOutputOwned(7L, "71"))
            .isInstanceOfSatisfying(ServiceException.class, exception -> assertThat(exception.getCode())
                .isEqualTo(TimelineErrorCodes.TIMELINE_ASSET_INVALID));
    }

    private CreationAsset outputAsset(long assetId, long ownerUserId, long sourceTaskId) {
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(assetId);
        asset.setOwnerUserId(ownerUserId);
        asset.setAssetType(CreationAssetType.VIDEO.value());
        asset.setUsageOrigin(CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT.value());
        asset.setAssetStatus(CreationAssetStatus.READY.value());
        asset.setSourceRefId(sourceTaskId);
        asset.setCreateTime(LocalDateTime.of(2026, 8, 9, 12, 0));
        asset.setDelFlag("0");
        return asset;
    }

    private AiTask completedRenderTask(long taskId, long ownerUserId, long projectId, long resultAssetId) {
        AiTask task = new AiTask();
        task.setTaskId(taskId);
        task.setOwnerUserId(ownerUserId);
        task.setTaskType(AiTaskType.TIMELINE_RENDER.value());
        task.setResourceType(AiTaskResourceType.CREATION_PROJECT.value());
        task.setResourceId(projectId);
        task.setTaskStatus(AiTaskStatus.SUCCESS.value());
        task.setResultAssetId(resultAssetId);
        return task;
    }

    private ICreationProjectService.CreateProjectCommand command() {
        return new ICreationProjectService.CreateProjectCommand("digital_human_job", "99", "digital human project", "create-99");
    }

    private DigitalHumanCreationSourceDTO source() {
        return new DigitalHumanCreationSourceDTO(
            "99", "501", "502", "server snapshot", 3_000L, 768, 1024, 25, List.of());
    }

    private CreationProject project(long projectId, long ownerUserId, String title, Long outputAssetId,
                                    String status) {
        CreationProject project = new CreationProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(ownerUserId);
        project.setProjectTitle(title);
        project.setSourceType("digital_human_job");
        project.setSourceRefId(99L);
        project.setBaseVideoAssetId(501L);
        project.setPrimaryAudioAssetId(502L);
        project.setProjectStatus(status);
        project.setCanvasWidth(1080);
        project.setCanvasHeight(1920);
        project.setFrameRate(30);
        project.setDurationMs(3_000L);
        project.setCurrentOutputAssetId(outputAssetId);
        project.setDelFlag("0");
        return project;
    }

    private TimelineDraft draft(CreationProject project) {
        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(81L);
        draft.setOwnerUserId(project.getOwnerUserId());
        draft.setProjectId(project.getProjectId());
        draft.setRevision(3L);
        draft.setSchemaVersion(TimelineContractLimits.SCHEMA_VERSION);
        draft.setContentHash("b".repeat(64));
        draft.setDelFlag("0");
        return draft;
    }

    private static void initialize(Class<?> type) {
        if (TableInfoHelper.getTableInfo(type) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        }
    }
}
