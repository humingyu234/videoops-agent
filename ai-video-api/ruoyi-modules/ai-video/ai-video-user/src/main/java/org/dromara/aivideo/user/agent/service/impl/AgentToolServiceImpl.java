package org.dromara.aivideo.user.agent.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.creation.dto.CreationAssetDTO;
import org.dromara.aivideo.creation.enums.CreationAssetStatus;
import org.dromara.aivideo.creation.enums.CreationAssetType;
import org.dromara.aivideo.creation.enums.CreationAssetUsageOrigin;
import org.dromara.aivideo.creation.service.ICreationAssetService;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.dto.CreateDigitalHumanVideoByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationByResourceDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanResourceGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskStatus;
import org.dromara.aivideo.task.enums.AiTaskType;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.timeline.dto.TimelineOutputQualityDTO;
import org.dromara.aivideo.timeline.service.ITimelineOutputQualityService;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Explicit T3 tool whitelist over the existing app services.
 */
@Service
@ConditionalOnAppSecurityEnabled
@RequiredArgsConstructor
public class AgentToolServiceImpl implements IAgentToolService {

    private static final int UNKNOWN_TOOL = 46701;
    private static final int INVALID_ARGUMENTS = 46702;
    private static final int FORBIDDEN = 46703;
    private static final int INVALID_RESULT = 46704;

    private static final String SUBMIT_VOICE = "submit_voice_generation";
    private static final String CONFIRM_VOICE = "confirm_voice_generation";
    private static final String GET_GENERATION = "get_generation_status";
    private static final String SUBMIT_VIDEO = "submit_digital_human_video";
    private static final String PREPARE_PROJECT = "prepare_timeline_project";
    private static final String RENDER_TIMELINE = "render_timeline";
    private static final String GET_RENDER = "get_timeline_render_status";
    private static final String INSPECT_OUTPUT = "inspect_timeline_output";

    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final IDigitalHumanResourceGenerationService resourceGenerationService;
    private final IDigitalHumanGenerationService generationService;
    private final ICreationProjectService projectService;
    private final ICreationAssetService assetService;
    private final TimelineTaskApplicationService timelineTaskService;
    private final IAiTaskService taskService;
    private final ITimelineOutputQualityService qualityService;

    @Override
    public AgentToolDTOs.Result execute(AppPrincipalSnapshotDTO principal, AgentToolDTOs.Call call) {
        if (call == null || call.toolName() == null || call.toolName().isBlank()
            || call.arguments() == null || !call.arguments().isObject()) {
            throw invalidArguments();
        }
        return switch (call.toolName()) {
            case SUBMIT_VOICE -> submitVoice(principal, call.arguments());
            case CONFIRM_VOICE -> confirmVoice(principal, call.arguments());
            case GET_GENERATION -> generationStatus(principal, call.arguments());
            case SUBMIT_VIDEO -> submitVideo(principal, call.arguments());
            case PREPARE_PROJECT -> prepareProject(principal, call.arguments());
            case RENDER_TIMELINE -> renderTimeline(principal, call.arguments());
            case GET_RENDER -> renderStatus(principal, call.arguments());
            case INSPECT_OUTPUT -> inspectOutput(principal, call.arguments());
            default -> throw new ServiceException("未知 Agent 工具", UNKNOWN_TOOL);
        };
    }

    private AgentToolDTOs.Result submitVoice(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal,
            "aivideo:studio:generate", "aivideo:voice:query");
        requireExactFields(node, Set.of("idempotencyKey", "scriptText", "referenceVoiceId"));
        AgentToolDTOs.SubmitVoiceArgs args = new AgentToolDTOs.SubmitVoiceArgs(
            idempotencyKey(node, "idempotencyKey"), text(node, "scriptText", 1000),
            positiveId(node, "referenceVoiceId"));
        return job(resourceGenerationService.createVoiceJob(new CreateVoiceGenerationByResourceDTO(
            principal, args.idempotencyKey(), args.scriptText(), args.referenceVoiceId())));
    }

    private AgentToolDTOs.Result confirmVoice(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal, "aivideo:studio:generate");
        requireExactFields(node, Set.of("jobId"));
        AgentToolDTOs.JobArgs args = new AgentToolDTOs.JobArgs(positiveId(node, "jobId"));
        return job(generationService.confirmVoiceJob(parseId(args.jobId()), context.digitalHumanOwner()));
    }

    private AgentToolDTOs.Result generationStatus(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal, "aivideo:studio:query");
        requireExactFields(node, Set.of("jobId"));
        AgentToolDTOs.JobArgs args = new AgentToolDTOs.JobArgs(positiveId(node, "jobId"));
        return job(generationService.getJob(parseId(args.jobId()), context.digitalHumanOwner()));
    }

    private AgentToolDTOs.Result submitVideo(AppPrincipalSnapshotDTO principal, JsonNode node) {
        requirePrincipal(principal, "aivideo:studio:generate", "aivideo:portrait:query");
        requireExactFields(node, Set.of("idempotencyKey", "voiceJobId", "portraitId"));
        AgentToolDTOs.SubmitVideoArgs args = new AgentToolDTOs.SubmitVideoArgs(
            idempotencyKey(node, "idempotencyKey"), positiveId(node, "voiceJobId"),
            positiveId(node, "portraitId"));
        return job(resourceGenerationService.createVideoJob(new CreateDigitalHumanVideoByResourceDTO(
            principal, args.idempotencyKey(), parseId(args.voiceJobId()), args.portraitId())));
    }

    private AgentToolDTOs.Result prepareProject(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal, "aivideo:creation:edit");
        requireExactFields(node, Set.of("idempotencyKey", "videoJobId", "projectTitle"));
        AgentToolDTOs.PrepareProjectArgs args = new AgentToolDTOs.PrepareProjectArgs(
            idempotencyKey(node, "idempotencyKey"), positiveId(node, "videoJobId"),
            text(node, "projectTitle", 128));
        ICreationProjectService.CreationProjectDTO project = projectService.create(context.actorId(),
            new ICreationProjectService.CreateProjectCommand("digital_human_job", args.videoJobId(),
                args.projectTitle(), args.idempotencyKey()));
        if (project == null || project.projectId() == null || project.projectStatus() == null
            || project.currentDraftRevision() <= 0) {
            throw invalidResult();
        }
        return new AgentToolDTOs.ProjectResult(project.projectId(), project.projectStatus(),
            Long.toString(project.currentDraftRevision()), project.canvasWidth(), project.canvasHeight(),
            project.frameRate(), project.durationMs());
    }

    private AgentToolDTOs.Result renderTimeline(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal, "aivideo:creation:generate");
        requireExactFields(node, Set.of("idempotencyKey", "projectId", "expectedRevision"));
        AgentToolDTOs.RenderTimelineArgs args = new AgentToolDTOs.RenderTimelineArgs(
            idempotencyKey(node, "idempotencyKey"), positiveId(node, "projectId"),
            positiveId(node, "expectedRevision"));
        CreateTimelineRenderTaskBo body = new CreateTimelineRenderTaskBo();
        body.setIdempotencyKey(args.idempotencyKey());
        body.setExpectedRevision(args.expectedRevision());
        CreateTimelineRenderTaskBo.OutputConfig output = new CreateTimelineRenderTaskBo.OutputConfig();
        output.setResolutionPreset("match_canvas");
        output.setFrameRate(30);
        output.setQualityPreset("high");
        body.setOutputConfig(output);
        return renderTask(timelineTaskService.createRender(context.actorId(), args.projectId(), body));
    }

    private AgentToolDTOs.Result renderStatus(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal, "aivideo:task:query");
        requireExactFields(node, Set.of("taskId"));
        AgentToolDTOs.TaskArgs args = new AgentToolDTOs.TaskArgs(positiveId(node, "taskId"));
        AiTaskDTO task = requireRenderTask(taskService.getOwned(context.taskScope(), args.taskId()));
        requireStableFailure(task.status(), task.errorCode(), task.safeMessage());
        String resultAssetId = null;
        String sourceType = null;
        String sourceId = null;
        String projectTitle = null;
        if (AiTaskStatus.SUCCESS.value().equals(task.status())) {
            resultAssetId = requireOutput(context.actorId(), task).asset().assetId();
            ICreationProjectService.CreationProjectDTO project = projectService.getOwned(
                context.actorId(), task.projectId());
            if (project == null || !"digital_human_job".equals(project.sourceType())
                || !task.projectId().equals(project.projectId()) || !isPositiveId(project.sourceId())
                || project.projectTitle() == null || project.projectTitle().isBlank()) {
                throw invalidResult();
            }
            sourceType = project.sourceType();
            sourceId = project.sourceId();
            projectTitle = project.projectTitle();
        }
        return new AgentToolDTOs.RenderStatusResult(task.taskId(), task.status(), task.stage(), task.progress(),
            task.projectId(), task.draftRevision(), resultAssetId, task.cancellable(), task.retryable(),
            task.errorCode(), task.safeMessage(), sourceType, sourceId, projectTitle);
    }

    private AgentToolDTOs.Result inspectOutput(AppPrincipalSnapshotDTO principal, JsonNode node) {
        PrincipalContext context = requirePrincipal(principal,
            "aivideo:task:query", "aivideo:creation-asset:query");
        requireExactFields(node, Set.of("taskId"));
        AgentToolDTOs.TaskArgs args = new AgentToolDTOs.TaskArgs(positiveId(node, "taskId"));
        AiTaskDTO task = requireRenderTask(taskService.getOwned(context.taskScope(), args.taskId()));
        if (!AiTaskStatus.SUCCESS.value().equals(task.status())) {
            throw invalidResult();
        }
        CreationAssetDTO asset = requireOutput(context.actorId(), task).asset();
        TimelineOutputQualityDTO quality = qualityService.evaluate(context.actorId(), task, asset);
        return new AgentToolDTOs.OutputInspectionResult(task.taskId(), asset.assetId(), asset.status().value(),
            asset.assetType().value(), asset.usageOrigin().value(), asset.mimeType(), asset.sha256(), asset.sizeBytes(),
            asset.durationMs(), asset.width(), asset.height(), asset.hasVideoStream(), asset.hasAudioStream(),
            "/api/studio/creation-assets/" + asset.assetId() + "/content", quality);
    }

    private AgentToolDTOs.GenerationJobResult job(DigitalHumanJobDTO value) {
        if (value == null || value.jobId() == null || value.jobId() <= 0 || value.jobType() == null
            || value.status() == null || value.stage() == null) {
            throw invalidResult();
        }
        if (value.status() == DigitalHumanJobStatus.FAILED
            && missingFailureFact(value.errorCode(), value.errorMessage())) {
            throw invalidResult();
        }
        return new AgentToolDTOs.GenerationJobResult(Long.toString(value.jobId()),
            value.parentJobId() == null ? null : Long.toString(value.parentJobId()), value.jobType().getValue(),
            value.status().getValue(), value.stage().getValue(), value.progress(), value.voiceConfirmed(),
            value.outputAvailable(), value.errorCode(), value.errorMessage());
    }

    private AgentToolDTOs.RenderTaskResult renderTask(AiTaskDTO task) {
        task = requireRenderTask(task);
        return new AgentToolDTOs.RenderTaskResult(task.taskId(), task.status(), task.stage(), task.projectId(),
            task.draftRevision());
    }

    private AiTaskDTO requireRenderTask(AiTaskDTO task) {
        if (task == null || !AiTaskType.TIMELINE_RENDER.value().equals(task.taskType())
            || !AiTaskResourceType.CREATION_PROJECT.value().equals(task.resourceType())
            || task.taskId() == null || task.projectId() == null
            || !Objects.equals(task.projectId(), task.resourceId()) || task.draftRevision() == null) {
            throw invalidResult();
        }
        return task;
    }

    private OutputFact requireOutput(long actorId, AiTaskDTO task) {
        if (task.resultAssetId() == null || task.resultAssetId().isBlank()) {
            throw invalidResult();
        }
        CreationAssetDTO asset = assetService.getOwnedTimelineRenderOutput(
            actorId, task.taskId(), task.resultAssetId());
        if (asset == null || asset.status() != CreationAssetStatus.READY || asset.assetType() != CreationAssetType.VIDEO
            || asset.usageOrigin() != CreationAssetUsageOrigin.TIMELINE_RENDER_OUTPUT
            || asset.mimeType() == null || !asset.mimeType().startsWith("video/")
            || asset.sha256() == null || !SHA256.matcher(asset.sha256()).matches()
            || asset.sizeBytes() <= 0 || !asset.hasVideoStream()) {
            throw invalidResult();
        }
        return new OutputFact(asset);
    }

    private void requireStableFailure(String status, String errorCode, String safeMessage) {
        if (AiTaskStatus.FAILED.value().equals(status) && missingFailureFact(errorCode, safeMessage)) {
            throw invalidResult();
        }
    }

    private boolean missingFailureFact(String errorCode, String safeMessage) {
        return errorCode == null || errorCode.isBlank() || safeMessage == null || safeMessage.isBlank();
    }

    private PrincipalContext requirePrincipal(AppPrincipalSnapshotDTO principal, String... permissions) {
        AppWorkspaceSessionSnapshotDTO workspace = principal == null ? null : principal.workspace();
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0 || workspace == null
            || workspace.tenantId() == null || workspace.tenantId() <= 0
            || workspace.workspaceKey() == null || workspace.workspaceKey().isBlank()
            || !"personal".equals(workspace.workspaceType()) || !"app_user".equals(workspace.ownerType())
            || !Objects.equals(principal.appUserId(), workspace.ownerId()) || workspace.permissions() == null) {
            throw new ServiceException("当前 Agent 工作区不可用", FORBIDDEN);
        }
        for (String permission : permissions) {
            if (!workspace.permissions().contains(permission)) {
                throw new ServiceException("Agent 工具权限不足", FORBIDDEN);
            }
        }
        return new PrincipalContext(principal.appUserId(), new DigitalHumanOwnerDTO(
            workspace.tenantId(), principal.appUserId()), new AiTaskAccessScopeDTO(
            workspace.tenantId(), principal.appUserId(), workspace.workspaceKey()));
    }

    private void requireExactFields(JsonNode node, Set<String> expected) {
        Set<String> actual = node.properties().stream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet());
        if (!actual.equals(expected)) {
            throw invalidArguments();
        }
    }

    private String text(JsonNode node, String field, int maxCodePoints) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalidArguments();
        }
        String text = value.textValue();
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > maxCodePoints) {
            throw invalidArguments();
        }
        return text;
    }

    private String positiveId(JsonNode node, String field) {
        String value = text(node, field, 19);
        if (!POSITIVE_ID.matcher(value).matches()) {
            throw invalidArguments();
        }
        try {
            Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidArguments();
        }
        return value;
    }

    private String idempotencyKey(JsonNode node, String field) {
        String value = text(node, field, 64);
        if (!IDEMPOTENCY_KEY.matcher(value).matches()) {
            throw invalidArguments();
        }
        return value;
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidArguments();
        }
    }

    private boolean isPositiveId(String value) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            return false;
        }
        try {
            return Long.parseLong(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private ServiceException invalidArguments() {
        return new ServiceException("Agent 工具参数不符合契约", INVALID_ARGUMENTS);
    }

    private ServiceException invalidResult() {
        return new ServiceException("Agent 工具结果不符合契约", INVALID_RESULT);
    }

    private record PrincipalContext(long actorId, DigitalHumanOwnerDTO digitalHumanOwner,
                                    AiTaskAccessScopeDTO taskScope) {
    }

    private record OutputFact(CreationAssetDTO asset) {
    }
}
