package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.aivideo.creation.domain.CreationAsset;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationAssetMapper;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.aivideo.task.mapper.AiTaskExecutionMapper;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.aivideo.timeline.domain.TimelineAssetRef;
import org.dromara.aivideo.timeline.domain.TimelineDraft;
import org.dromara.aivideo.timeline.domain.TimelineVersion;
import org.dromara.aivideo.timeline.mapper.TimelineAssetRefMapper;
import org.dromara.aivideo.timeline.mapper.TimelineDraftMapper;
import org.dromara.aivideo.timeline.mapper.TimelineVersionMapper;
import org.dromara.aivideo.timeline.service.ITimelineConsistencyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class TimelineConsistencyServiceImplTest {

    @Mock
    private CreationProjectMapper projectMapper;
    @Mock
    private TimelineDraftMapper draftMapper;
    @Mock
    private TimelineVersionMapper versionMapper;
    @Mock
    private TimelineAssetRefMapper assetRefMapper;
    @Mock
    private CreationAssetMapper assetMapper;
    @Mock
    private AiTaskMapper taskMapper;
    @Mock
    private AiTaskExecutionMapper executionMapper;

    @Test
    void reportsEachTimelineConsistencyRiskWithoutRepairingAnyFact() {
        CreationProject project = project(31L);
        project.setCurrentOutputAssetId(555L);
        TimelineDraft first = draft(901L, 31L, "{\"schemaVersion\":\"timeline-1\",\"tracks\":[]}");
        TimelineDraft duplicate = draft(902L, 31L, "{\"schemaVersion\":\"timeline-1\",\"tracks\":[]}");
        TimelineDraft orphan = draft(903L, 999L, "{}");
        TimelineVersion orphanVersion = version(1001L, 998L);
        TimelineAssetRef driftedReference = reference(901L, 777L);
        CreationAsset failedReferencedAsset = asset(777L, "failed", "upload");
        CreationAsset stalePendingOutput = asset(778L, "pending", "timeline_render_output");
        stalePendingOutput.setUpdateTime(LocalDateTime.of(2026, 8, 8, 8, 0));
        AiTask missingVersionAndOutput = task(2001L, null, null);
        AiTaskExecution expiredLease = execution(3001L, LocalDateTime.of(2026, 8, 8, 9, 0));

        when(projectMapper.selectList(any(Wrapper.class))).thenReturn(List.of(project));
        when(draftMapper.selectList(any(Wrapper.class))).thenReturn(List.of(first, duplicate, orphan));
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(orphanVersion));
        when(assetRefMapper.selectList(any(Wrapper.class))).thenReturn(List.of(driftedReference));
        when(assetMapper.selectList(any(Wrapper.class))).thenReturn(List.of(failedReferencedAsset, stalePendingOutput));
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(missingVersionAndOutput));
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(expiredLease));

        ITimelineConsistencyService.ConsistencyReport report = service().scan();
        Set<String> codes = report.findings().stream().map(ITimelineConsistencyService.ConsistencyFinding::code)
            .collect(java.util.stream.Collectors.toSet());

        assertThat(codes).contains("ORPHAN_DRAFT", "MULTIPLE_DRAFTS", "ORPHAN_VERSION", "REFERENCE_DRIFT",
            "INVALID_REFERENCED_ASSET", "TASK_VERSION_MISSING", "SUCCESS_TASK_OUTPUT_MISSING",
            "PROJECT_OUTPUT_DRIFT", "EXPIRED_EXECUTION_LEASE", "STALE_PENDING_OUTPUT");
        assertThat(report.findings()).allSatisfy(finding -> assertThat(finding.safeSummary())
            .doesNotContain("private/creation/secret.mp4"));
        verify(draftMapper, never()).update(any(TimelineDraft.class), any(LambdaUpdateWrapper.class));
        verify(versionMapper, never()).update(any(TimelineVersion.class), any(LambdaUpdateWrapper.class));
        verify(assetRefMapper, never()).delete(any(Wrapper.class));
        verify(assetMapper, never()).update(any(CreationAsset.class), any(LambdaUpdateWrapper.class));
    }

    @Test
    void reportsReferenceDriftWhenTimelineMediaHasNoReferenceProjection() {
        CreationProject project = project(31L);
        TimelineDraft draft = draft(901L, 31L, timelineWithImageAsset(777L));

        stubScan(List.of(project), List.of(draft), List.of(), List.of(), List.of(asset(777L, "ready", "upload")),
            List.of(), List.of());

        ITimelineConsistencyService.ConsistencyReport report = service().scan();

        assertThat(report.findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("REFERENCE_DRIFT");
            assertThat(finding.safeSummary()).contains("documentId=901").doesNotContain("secret.mp4");
        });
    }

    @Test
    void reportsReferenceDriftWhenProjectionUsesAnotherProject() {
        CreationProject project = project(31L);
        TimelineDraft draft = draft(901L, 31L, timelineWithImageAsset(777L));
        TimelineAssetRef crossProjectReference = matchingReference(901L, 32L, 777L);

        stubScan(List.of(project), List.of(draft), List.of(), List.of(crossProjectReference),
            List.of(asset(777L, "ready", "upload")), List.of(), List.of());

        ITimelineConsistencyService.ConsistencyReport report = service().scan();

        assertThat(report.findings()).anySatisfy(finding -> {
            assertThat(finding.code()).isEqualTo("REFERENCE_DRIFT");
            assertThat(finding.safeSummary()).isEqualTo("referenceId=801");
        });
    }

    private TimelineConsistencyServiceImpl service() {
        return new TimelineConsistencyServiceImpl(projectMapper, draftMapper, versionMapper, assetRefMapper, assetMapper,
            taskMapper, executionMapper, JsonMapper.builder().build(),
            Clock.fixed(Instant.parse("2026-08-08T10:00:00Z"), ZoneOffset.UTC));
    }

    private CreationProject project(long projectId) {
        CreationProject project = new CreationProject();
        project.setProjectId(projectId);
        project.setOwnerUserId(7L);
        project.setProjectStatus("editing");
        project.setDelFlag("0");
        return project;
    }

    private TimelineDraft draft(long draftId, long projectId, String contentJson) {
        TimelineDraft draft = new TimelineDraft();
        draft.setTimelineDraftId(draftId);
        draft.setOwnerUserId(7L);
        draft.setProjectId(projectId);
        draft.setRevision(1L);
        draft.setSchemaVersion("timeline-1");
        draft.setContentJson(contentJson);
        draft.setContentHash("a".repeat(64));
        draft.setDurationMs(30_000L);
        draft.setDelFlag("0");
        return draft;
    }

    private TimelineVersion version(long versionId, long projectId) {
        TimelineVersion version = new TimelineVersion();
        version.setTimelineVersionId(versionId);
        version.setOwnerUserId(7L);
        version.setProjectId(projectId);
        version.setVersionNo(1L);
        version.setSourceDraftRevision(1L);
        version.setVersionReason("manual_save");
        version.setIdempotencyKey("version-" + versionId);
        version.setRequestDigest("b".repeat(64));
        version.setSchemaVersion("timeline-1");
        version.setContentJson("{}");
        version.setContentHash("a".repeat(64));
        version.setDurationMs(30_000L);
        return version;
    }

    private TimelineAssetRef reference(long draftId, long assetId) {
        TimelineAssetRef ref = new TimelineAssetRef();
        ref.setTimelineAssetRefId(801L);
        ref.setOwnerUserId(7L);
        ref.setProjectId(31L);
        ref.setDocumentType("draft");
        ref.setDocumentId(draftId);
        ref.setElementId("missing_element");
        ref.setAssetId(assetId);
        ref.setUsageType("image");
        ref.setStartMs(0L);
        ref.setEndMs(1_000L);
        return ref;
    }

    private TimelineAssetRef matchingReference(long draftId, long projectId, long assetId) {
        TimelineAssetRef ref = reference(draftId, assetId);
        ref.setProjectId(projectId);
        ref.setElementId("image-1");
        return ref;
    }

    private String timelineWithImageAsset(long assetId) {
        return """
            {"schemaVersion":"timeline-1","tracks":[{"trackType":"image_overlay","elements":[
            {"elementId":"image-1","assetId":"%s","startMs":0,"endMs":1000}
            ]}]}
            """.formatted(assetId);
    }

    private void stubScan(List<CreationProject> projects, List<TimelineDraft> drafts, List<TimelineVersion> versions,
                          List<TimelineAssetRef> references, List<CreationAsset> assets, List<AiTask> tasks,
                          List<AiTaskExecution> executions) {
        when(projectMapper.selectList(any(Wrapper.class))).thenReturn(projects);
        when(draftMapper.selectList(any(Wrapper.class))).thenReturn(drafts);
        when(versionMapper.selectList(any(Wrapper.class))).thenReturn(versions);
        when(assetRefMapper.selectList(any(Wrapper.class))).thenReturn(references);
        when(assetMapper.selectList(any(Wrapper.class))).thenReturn(assets);
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(tasks);
        when(executionMapper.selectList(any(Wrapper.class))).thenReturn(executions);
    }

    private CreationAsset asset(long assetId, String status, String origin) {
        CreationAsset asset = new CreationAsset();
        asset.setAssetId(assetId);
        asset.setOwnerUserId(7L);
        asset.setAssetStatus(status);
        asset.setUsageOrigin(origin);
        asset.setStorageKey("private/creation/secret.mp4");
        asset.setDelFlag("0");
        return asset;
    }

    private AiTask task(long taskId, Long inputVersionId, Long resultAssetId) {
        AiTask task = new AiTask();
        task.setTaskId(taskId);
        task.setOwnerUserId(7L);
        task.setTaskType("timeline_render");
        task.setResourceType("creation_project");
        task.setResourceId(31L);
        task.setInputVersionId(inputVersionId);
        task.setTaskStatus("success");
        task.setResultAssetId(resultAssetId);
        return task;
    }

    private AiTaskExecution execution(long executionId, LocalDateTime leaseExpiresAt) {
        AiTaskExecution execution = new AiTaskExecution();
        execution.setTaskExecutionId(executionId);
        execution.setOwnerUserId(7L);
        execution.setTaskId(2001L);
        execution.setExecutionStatus("running");
        execution.setLeaseExpiresAt(leaseExpiresAt);
        return execution;
    }
}
