package org.dromara.aivideo.timeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reports persistence inconsistencies without modifying timeline, task, asset, or project facts. */
@Service
public class TimelineConsistencyServiceImpl implements ITimelineConsistencyService {

    private static final String DRAFT = "draft";
    private static final String VERSION = "version";
    private static final Duration PENDING_OUTPUT_TIMEOUT = Duration.ofHours(1);

    private final CreationProjectMapper projectMapper;
    private final TimelineDraftMapper draftMapper;
    private final TimelineVersionMapper versionMapper;
    private final TimelineAssetRefMapper assetRefMapper;
    private final CreationAssetMapper assetMapper;
    private final AiTaskMapper taskMapper;
    private final AiTaskExecutionMapper executionMapper;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    TimelineConsistencyServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                   TimelineVersionMapper versionMapper, TimelineAssetRefMapper assetRefMapper,
                                   CreationAssetMapper assetMapper, AiTaskMapper taskMapper,
                                   AiTaskExecutionMapper executionMapper, JsonMapper jsonMapper, Clock clock) {
        this.projectMapper = Objects.requireNonNull(projectMapper, "projectMapper");
        this.draftMapper = Objects.requireNonNull(draftMapper, "draftMapper");
        this.versionMapper = Objects.requireNonNull(versionMapper, "versionMapper");
        this.assetRefMapper = Objects.requireNonNull(assetRefMapper, "assetRefMapper");
        this.assetMapper = Objects.requireNonNull(assetMapper, "assetMapper");
        this.taskMapper = Objects.requireNonNull(taskMapper, "taskMapper");
        this.executionMapper = Objects.requireNonNull(executionMapper, "executionMapper");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Autowired
    public TimelineConsistencyServiceImpl(CreationProjectMapper projectMapper, TimelineDraftMapper draftMapper,
                                          TimelineVersionMapper versionMapper, TimelineAssetRefMapper assetRefMapper,
                                          CreationAssetMapper assetMapper, AiTaskMapper taskMapper,
                                          AiTaskExecutionMapper executionMapper, JsonMapper jsonMapper) {
        this(projectMapper, draftMapper, versionMapper, assetRefMapper, assetMapper, taskMapper, executionMapper,
            jsonMapper, Clock.systemUTC());
    }

    @Override
    public ConsistencyReport scan() {
        List<CreationProject> projects = list(projectMapper.selectList(new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getDelFlag, "0")));
        List<TimelineDraft> drafts = list(draftMapper.selectList(new LambdaQueryWrapper<TimelineDraft>()
            .eq(TimelineDraft::getDelFlag, "0")));
        List<TimelineVersion> versions = list(versionMapper.selectList(new LambdaQueryWrapper<>()));
        List<TimelineAssetRef> references = list(assetRefMapper.selectList(new LambdaQueryWrapper<>()));
        List<CreationAsset> assets = list(assetMapper.selectList(new LambdaQueryWrapper<CreationAsset>()
            .eq(CreationAsset::getDelFlag, "0")));
        List<AiTask> tasks = list(taskMapper.selectList(new LambdaQueryWrapper<>()));
        List<AiTaskExecution> executions = list(executionMapper.selectList(new LambdaQueryWrapper<>()));

        Map<String, CreationProject> projectByKey = new HashMap<>();
        for (CreationProject project : projects) {
            if (project != null && project.getOwnerUserId() != null && project.getProjectId() != null) {
                projectByKey.put(projectKey(project.getOwnerUserId(), project.getProjectId()), project);
            }
        }
        Map<String, CreationAsset> assetByKey = new HashMap<>();
        for (CreationAsset asset : assets) {
            if (asset != null && asset.getOwnerUserId() != null && asset.getAssetId() != null) {
                assetByKey.put(assetKey(asset.getOwnerUserId(), asset.getAssetId()), asset);
            }
        }
        Map<String, TimelineVersion> versionByKey = new HashMap<>();
        for (TimelineVersion version : versions) {
            if (version != null && version.getOwnerUserId() != null && version.getTimelineVersionId() != null) {
                versionByKey.put(versionKey(version.getOwnerUserId(), version.getTimelineVersionId()), version);
            }
        }

        List<ConsistencyFinding> findings = new ArrayList<>();
        inspectDrafts(drafts, projectByKey, findings);
        inspectVersions(versions, projectByKey, findings);
        inspectReferences(drafts, versions, references, assetByKey, findings);
        inspectTasks(tasks, versionByKey, assetByKey, findings);
        inspectProjectOutputs(projects, assetByKey, findings);
        inspectLeases(executions, findings);
        inspectPendingOutputs(assets, findings);
        return new ConsistencyReport(findings);
    }

    private void inspectDrafts(List<TimelineDraft> drafts, Map<String, CreationProject> projects,
                               List<ConsistencyFinding> findings) {
        Map<String, Integer> counts = new HashMap<>();
        for (TimelineDraft draft : drafts) {
            if (!hasOwnerAndProject(draft == null ? null : draft.getOwnerUserId(),
                draft == null ? null : draft.getProjectId())) {
                continue;
            }
            String key = projectKey(draft.getOwnerUserId(), draft.getProjectId());
            counts.merge(key, 1, Integer::sum);
            if (!projects.containsKey(key)) {
                add(findings, "ORPHAN_DRAFT", "draftId=" + safeId(draft.getTimelineDraftId()));
            }
        }
        counts.forEach((key, count) -> {
            if (count > 1) {
                add(findings, "MULTIPLE_DRAFTS", "project=" + safeProjectFromKey(key));
            }
        });
    }

    private void inspectVersions(List<TimelineVersion> versions, Map<String, CreationProject> projects,
                                 List<ConsistencyFinding> findings) {
        for (TimelineVersion version : versions) {
            if (version == null || !hasOwnerAndProject(version.getOwnerUserId(), version.getProjectId())) {
                continue;
            }
            if (!projects.containsKey(projectKey(version.getOwnerUserId(), version.getProjectId()))) {
                add(findings, "ORPHAN_VERSION", "versionId=" + safeId(version.getTimelineVersionId()));
            }
        }
    }

    private void inspectReferences(List<TimelineDraft> drafts, List<TimelineVersion> versions,
                                   List<TimelineAssetRef> references, Map<String, CreationAsset> assets,
                                   List<ConsistencyFinding> findings) {
        Map<String, DocumentSnapshot> documents = new HashMap<>();
        for (TimelineDraft draft : drafts) {
            if (draft != null && hasOwnerAndProject(draft.getOwnerUserId(), draft.getProjectId())
                && draft.getTimelineDraftId() != null) {
                documents.put(documentKey(draft.getOwnerUserId(), draft.getProjectId(), DRAFT,
                    draft.getTimelineDraftId()), new DocumentSnapshot(draft.getTimelineDraftId(), draft.getContentJson()));
            }
        }
        for (TimelineVersion version : versions) {
            if (version != null && hasOwnerAndProject(version.getOwnerUserId(), version.getProjectId())
                && version.getTimelineVersionId() != null) {
                documents.put(documentKey(version.getOwnerUserId(), version.getProjectId(), VERSION,
                    version.getTimelineVersionId()), new DocumentSnapshot(version.getTimelineVersionId(), version.getContentJson()));
            }
        }
        Map<String, List<TimelineAssetRef>> matchingReferences = new HashMap<>();
        for (TimelineAssetRef reference : references) {
            if (reference == null) {
                continue;
            }
            String key = reference.getOwnerUserId() == null || reference.getProjectId() == null
                || reference.getDocumentType() == null || reference.getDocumentId() == null ? null
                : documentKey(reference.getOwnerUserId(), reference.getProjectId(), reference.getDocumentType(),
                    reference.getDocumentId());
            DocumentSnapshot document = key == null ? null : documents.get(key);
            if (document == null || !document.contains(reference, jsonMapper)) {
                add(findings, "REFERENCE_DRIFT", "referenceId=" + safeId(reference.getTimelineAssetRefId()));
            } else {
                matchingReferences.computeIfAbsent(key, ignored -> new ArrayList<>()).add(reference);
            }
            CreationAsset asset = reference.getAssetId() == null ? null
                : reference.getOwnerUserId() == null ? null
                : assets.get(assetKey(reference.getOwnerUserId(), reference.getAssetId()));
            if (asset == null || !"ready".equals(asset.getAssetStatus())) {
                add(findings, "INVALID_REFERENCED_ASSET", "referenceId=" + safeId(reference.getTimelineAssetRefId()));
            }
        }
        for (Map.Entry<String, DocumentSnapshot> entry : documents.entrySet()) {
            List<TimelineAssetRef> documentReferences = matchingReferences.getOrDefault(entry.getKey(), List.of());
            for (ReferenceProjection projection : entry.getValue().references(jsonMapper)) {
                boolean present = documentReferences.stream().anyMatch(projection::matches);
                if (!present) {
                    add(findings, "REFERENCE_DRIFT", "documentId=" + entry.getValue().documentId()
                        + "; elementId=" + projection.elementId());
                }
            }
        }
    }

    private void inspectTasks(List<AiTask> tasks, Map<String, TimelineVersion> versions,
                              Map<String, CreationAsset> assets, List<ConsistencyFinding> findings) {
        for (AiTask task : tasks) {
            if (task == null || task.getOwnerUserId() == null || task.getTaskId() == null) {
                continue;
            }
            TimelineVersion inputVersion = task.getInputVersionId() == null ? null
                : versions.get(versionKey(task.getOwnerUserId(), task.getInputVersionId()));
            if ("creation_project".equals(task.getResourceType())
                && (inputVersion == null || !Objects.equals(task.getResourceId(), inputVersion.getProjectId()))) {
                add(findings, "TASK_VERSION_MISSING", "taskId=" + safeId(task.getTaskId()));
            }
            if ("timeline_render".equals(task.getTaskType()) && "success".equals(task.getTaskStatus())) {
                CreationAsset output = task.getResultAssetId() == null ? null
                    : assets.get(assetKey(task.getOwnerUserId(), task.getResultAssetId()));
                if (output == null || !"ready".equals(output.getAssetStatus())) {
                    add(findings, "SUCCESS_TASK_OUTPUT_MISSING", "taskId=" + safeId(task.getTaskId()));
                }
            }
        }
    }

    private void inspectProjectOutputs(List<CreationProject> projects, Map<String, CreationAsset> assets,
                                       List<ConsistencyFinding> findings) {
        for (CreationProject project : projects) {
            if (project == null || project.getOwnerUserId() == null || project.getProjectId() == null
                || project.getCurrentOutputAssetId() == null) {
                continue;
            }
            CreationAsset output = assets.get(assetKey(project.getOwnerUserId(), project.getCurrentOutputAssetId()));
            if (output == null || !"ready".equals(output.getAssetStatus())
                || !"timeline_render_output".equals(output.getUsageOrigin())) {
                add(findings, "PROJECT_OUTPUT_DRIFT", "projectId=" + safeId(project.getProjectId()));
            }
        }
    }

    private void inspectLeases(List<AiTaskExecution> executions, List<ConsistencyFinding> findings) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (AiTaskExecution execution : executions) {
            if (execution != null && "running".equals(execution.getExecutionStatus())
                && execution.getLeaseExpiresAt() != null && execution.getLeaseExpiresAt().isBefore(now)) {
                add(findings, "EXPIRED_EXECUTION_LEASE", "executionId=" + safeId(execution.getTaskExecutionId()));
            }
        }
    }

    private void inspectPendingOutputs(List<CreationAsset> assets, List<ConsistencyFinding> findings) {
        LocalDateTime deadline = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minus(PENDING_OUTPUT_TIMEOUT);
        for (CreationAsset asset : assets) {
            if (asset == null || !"timeline_render_output".equals(asset.getUsageOrigin())
                || !"pending".equals(asset.getAssetStatus())) {
                continue;
            }
            LocalDateTime updatedAt = asset.getUpdateTime() == null ? asset.getCreateTime() : asset.getUpdateTime();
            if (updatedAt != null && updatedAt.isBefore(deadline)) {
                add(findings, "STALE_PENDING_OUTPUT", "assetId=" + safeId(asset.getAssetId()));
            }
        }
    }

    private void add(List<ConsistencyFinding> findings, String code, String safeSummary) {
        findings.add(new ConsistencyFinding(code, safeSummary.length() > 512 ? safeSummary.substring(0, 512) : safeSummary));
    }

    private boolean hasOwnerAndProject(Long ownerUserId, Long projectId) {
        return ownerUserId != null && projectId != null;
    }

    private String projectKey(long ownerUserId, long projectId) {
        return ownerUserId + ":" + projectId;
    }

    private String assetKey(long ownerUserId, long assetId) {
        return ownerUserId + ":" + assetId;
    }

    private String versionKey(long ownerUserId, long versionId) {
        return ownerUserId + ":" + versionId;
    }

    private String documentKey(long ownerUserId, long projectId, String documentType, long documentId) {
        return ownerUserId + ":" + projectId + ":" + documentType + ":" + documentId;
    }

    private String safeProjectFromKey(String key) {
        int separator = key.indexOf(':');
        return separator < 0 ? "unknown" : key.substring(separator + 1);
    }

    private String safeId(Long value) {
        return value == null ? "unknown" : Long.toString(value);
    }

    private <T> List<T> list(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record DocumentSnapshot(long documentId, String contentJson) {
        private boolean contains(TimelineAssetRef reference, JsonMapper jsonMapper) {
            return references(jsonMapper).stream().anyMatch(projection -> projection.matches(reference));
        }

        private List<ReferenceProjection> references(JsonMapper jsonMapper) {
            List<ReferenceProjection> projections = new ArrayList<>();
            try {
                JsonNode root = jsonMapper.readTree(contentJson);
                for (JsonNode track : root.path("tracks")) {
                    String usageType = usageForTrack(track.path("trackType").textValue());
                    if (usageType == null) {
                        continue;
                    }
                    for (JsonNode element : track.path("elements")) {
                        String elementId = element.path("elementId").textValue();
                        Long assetId = parseAssetId(element.path("assetId").textValue());
                        long startMs = element.path("startMs").asLong(Long.MIN_VALUE);
                        long endMs = element.path("endMs").asLong(Long.MIN_VALUE);
                        if (elementId != null && assetId != null && startMs != Long.MIN_VALUE
                            && endMs != Long.MIN_VALUE) {
                            projections.add(new ReferenceProjection(elementId, assetId, usageType, startMs, endMs));
                        }
                    }
                }
            } catch (Exception exception) {
                return List.of();
            }
            return projections;
        }

        private Long parseAssetId(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        private String usageForTrack(String trackType) {
            if (trackType == null) {
                return null;
            }
            return switch (trackType) {
                case "main_video" -> "base_video";
                case "image_overlay" -> "image";
                case "pip_video" -> "pip_video";
                case "primary_audio", "background_music", "sound_effect" -> trackType;
                default -> null;
            };
        }
    }

    private record ReferenceProjection(String elementId, Long assetId, String usageType, long startMs, long endMs) {
        private boolean matches(TimelineAssetRef reference) {
            return Objects.equals(elementId, reference.getElementId())
                && Objects.equals(assetId, reference.getAssetId())
                && Objects.equals(usageType, reference.getUsageType())
                && Objects.equals(startMs, reference.getStartMs())
                && Objects.equals(endMs, reference.getEndMs());
        }
    }
}
