package org.dromara.aivideo.user.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentRunTraceDTO;
import org.dromara.aivideo.agent.service.IAgentRunOrchestrationService;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentRunTraceService;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanOwnerDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.user.agent.domain.bo.AgentApprovalDecisionBo;
import org.dromara.aivideo.user.agent.domain.bo.AgentRunRevisionBo;
import org.dromara.aivideo.user.agent.domain.bo.CreateAgentRunBo;
import org.dromara.aivideo.user.agent.domain.vo.AgentRunDetailVo;
import org.dromara.aivideo.user.agent.service.IAgentRunApplicationService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Composes immutable contracts, recoverable orchestration and an owner-scoped durable-fact trace. */
@Service
@ConditionalOnAppSecurityEnabled
public class AgentRunApplicationServiceImpl implements IAgentRunApplicationService {

    private static final Pattern CLIENT_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,48}");
    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern SHA256 = Pattern.compile("[a-f0-9]{64}");
    private static final Set<String> READ_PERMISSIONS = Set.of("aivideo:studio:query");
    private static final Set<String> MUTATION_PERMISSIONS = Set.of("aivideo:studio:generate");
    private static final Set<String> ADVANCE_BASE_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query");
    private static final Set<String> NEW_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:voice:query",
        "aivideo:portrait:query", "aivideo:creation:edit", "aivideo:creation:generate",
        "aivideo:task:query", "aivideo:creation-asset:query");
    private static final Set<String> VOICE_JOB_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:portrait:query",
        "aivideo:creation:edit", "aivideo:creation:generate", "aivideo:task:query",
        "aivideo:creation-asset:query");
    private static final Set<String> VIDEO_JOB_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:creation:edit",
        "aivideo:creation:generate", "aivideo:task:query", "aivideo:creation-asset:query");
    private static final Set<String> PROJECT_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:creation:generate",
        "aivideo:task:query", "aivideo:creation-asset:query");
    private static final Set<String> RENDER_TASK_PERMISSIONS = Set.of(
        "aivideo:studio:generate", "aivideo:studio:query", "aivideo:task:query",
        "aivideo:creation-asset:query");

    private final IAgentRunService runService;
    private final IAgentRunOrchestrationService orchestrationService;
    private final IAgentRunTraceService traceService;
    private final IDigitalHumanGenerationService generationService;
    private final JsonMapper jsonMapper;

    public AgentRunApplicationServiceImpl(IAgentRunService runService,
                                          IAgentRunOrchestrationService orchestrationService,
                                          IAgentRunTraceService traceService,
                                          IDigitalHumanGenerationService generationService,
                                          JsonMapper jsonMapper) {
        this.runService = Objects.requireNonNull(runService, "runService");
        this.orchestrationService = Objects.requireNonNull(orchestrationService, "orchestrationService");
        this.traceService = Objects.requireNonNull(traceService, "traceService");
        this.generationService = Objects.requireNonNull(generationService, "generationService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public AgentRunDetailVo create(AppPrincipalSnapshotDTO principal, CreateAgentRunBo body) {
        String startAt = requireCreate(body);
        requirePrincipal(principal, createPermissions(startAt));
        String baseKey = body.getIdempotencyKey();

        ObjectNode brief = jsonMapper.createObjectNode();
        brief.put("startAt", startAt);
        switch (startAt) {
            case "new" -> {
                brief.put("scriptText", body.getScriptText());
                brief.put("referenceVoiceId", body.getReferenceVoiceId());
                brief.put("portraitId", body.getPortraitId());
                brief.put("projectTitle", body.getProjectTitle());
            }
            case "voice_job" -> {
                DigitalHumanJobDTO job = reusableJob(principal, body.getVoiceJobId(),
                    DigitalHumanJobType.VOICE_GENERATE);
                brief.put("voiceJobId", body.getVoiceJobId());
                brief.put("inputHash", job.inputHash());
                brief.put("portraitId", body.getPortraitId());
                brief.put("projectTitle", body.getProjectTitle());
            }
            case "video_job" -> {
                DigitalHumanJobDTO job = reusableJob(principal, body.getVideoJobId(),
                    DigitalHumanJobType.VIDEO_GENERATE);
                brief.put("videoJobId", body.getVideoJobId());
                brief.put("inputHash", job.inputHash());
                brief.put("projectTitle", body.getProjectTitle());
            }
            case "project" -> {
                brief.put("projectId", body.getProjectId());
                brief.put("expectedRevision", body.getExpectedRevision());
            }
            case "render_task" -> brief.put("taskId", body.getTaskId());
            default -> throw new ServiceException("Agent 交付输入无效", 46702);
        }
        IAgentRunService.DeliveryBriefVersionView briefVersion = stableMutation(() ->
            runService.appendDeliveryBrief(principal,
                new IAgentRunService.AppendDeliveryBriefCommand(null, null, baseKey + ".brief", json(brief))));

        ObjectNode profile = jsonMapper.createObjectNode();
        profile.put("maxRunSeconds", 3_600);
        profile.put("maxResumeAttempts", 20);
        profile.put("maxProviderSubmissions", requiredProviderSubmissions(startAt));
        profile.put("maxRenderRetries", 1);
        profile.put("pollIntervalSeconds", 5);
        IAgentRunService.AcceptanceProfileVersionView profileVersion = stableMutation(() ->
            runService.appendAcceptanceProfile(principal, new IAgentRunService.AppendAcceptanceProfileCommand(
                null, null, briefVersion.deliveryBriefVersionId(), baseKey + ".profile", json(profile))));

        IAgentRunService.AgentRunView run = stableMutation(() -> runService.createRun(principal,
            new IAgentRunService.CreateAgentRunCommand(briefVersion.deliveryBriefVersionId(),
                profileVersion.acceptanceProfileVersionId(), baseKey + ".run")));
        return detail(principal, run.agentRunId(), null);
    }

    @Override
    public AgentRunDetailVo detail(AppPrincipalSnapshotDTO principal, String agentRunId) {
        requirePrincipal(principal, READ_PERMISSIONS);
        return detail(principal, positiveId(agentRunId, "AgentRun 不存在"), null);
    }

    @Override
    public AgentRunDetailVo advance(AppPrincipalSnapshotDTO principal, String agentRunId,
                                    AgentRunRevisionBo body) {
        requirePrincipal(principal, ADVANCE_BASE_PERMISSIONS);
        long runId = positiveId(agentRunId, "AgentRun 不存在");
        requireRevision(body);
        IAgentRunService.ExecutionSnapshot snapshot = requireOwnedSnapshot(principal, runId);
        requirePrincipal(principal, createPermissions(frozenStartAt(snapshot.deliveryBriefJson())));
        AgentRunOrchestrationDTOs.AdvanceResult result = stableMutation(() ->
            orchestrationService.advance(principal, new AgentRunOrchestrationDTOs.AdvanceCommand(
                runId, body.getRowVersion(), body.getContractRevision(), workerId())));
        return detail(principal, runId, result);
    }

    @Override
    public AgentRunDetailVo cancel(AppPrincipalSnapshotDTO principal, String agentRunId,
                                   AgentRunRevisionBo body) {
        requirePrincipal(principal, MUTATION_PERMISSIONS);
        long runId = positiveId(agentRunId, "AgentRun 不存在");
        requireOwnedRun(principal, runId);
        requireRevision(body);
        AgentRunOrchestrationDTOs.AdvanceResult result = stableMutation(() ->
            orchestrationService.cancel(principal, new AgentRunOrchestrationDTOs.CancelCommand(
                runId, body.getRowVersion(), body.getContractRevision())));
        return detail(principal, runId, result);
    }

    @Override
    public AgentRunDetailVo decideApproval(AppPrincipalSnapshotDTO principal, String agentRunId,
                                           String approvalId, AgentApprovalDecisionBo body) {
        requirePrincipal(principal, MUTATION_PERMISSIONS);
        long runId = positiveId(agentRunId, "AgentRun 不存在");
        requireOwnedRun(principal, runId);
        long pendingApprovalId = positiveId(approvalId, "Agent 审批不存在");
        requireDecision(body);
        AgentRunOrchestrationDTOs.AdvanceResult result = stableMutation(() ->
            orchestrationService.decideApproval(principal, new AgentRunOrchestrationDTOs.ApprovalCommand(
                runId, body.getRowVersion(), body.getContractRevision(), pendingApprovalId,
                body.getApprovalRevision(), body.getType(), body.getApproved())));
        return detail(principal, runId, result);
    }

    private AgentRunDetailVo detail(AppPrincipalSnapshotDTO principal, long runId,
                                    AgentRunOrchestrationDTOs.AdvanceResult action) {
        IAgentRunService.AgentRunView run = requireOwnedRun(principal, runId);
        AgentRunOrchestrationDTOs.PlanResult plan = stableMutation(() -> orchestrationService.plan(principal, runId));
        AgentRunTraceDTO trace = stableMutation(() -> traceService.getOwnedTrace(principal, runId));
        IAgentRunService.ApprovalView approval = run.pendingApprovalId() == null ? null
            : stableMutation(() -> runService.getOwnedApproval(principal, runId, run.pendingApprovalId()));

        return new AgentRunDetailVo(runVo(run), planVo(plan), traceVo(trace), approvalVo(approval),
            "completed".equals(run.runStatus()) ? text(run.candidateAssetId()) : null, actionVo(action));
    }

    private AgentRunDetailVo.RunVo runVo(IAgentRunService.AgentRunView run) {
        return new AgentRunDetailVo.RunVo(text(run.agentRunId()), run.runStatus(), run.rowVersion(),
            run.contractRevision(), run.waitingTaskSource(), text(run.waitingTaskId()),
            text(run.candidateAssetId()), run.qualityRepairCount(), text(run.pendingApprovalId()),
            run.approvalRevision(), run.resumeAfter(), run.finishedAt(), run.errorCode(), run.errorSummary());
    }

    private AgentRunDetailVo.PlanVo planVo(AgentRunOrchestrationDTOs.PlanResult plan) {
        List<AgentRunDetailVo.PlanStepVo> steps = plan.steps().stream()
            .map(step -> new AgentRunDetailVo.PlanStepVo(step.sequence(), step.stepType(), step.toolName(),
                step.disposition(), step.reason() == null ? step.disposition() : step.reason()))
            .toList();
        return new AgentRunDetailVo.PlanVo(plan.startAt(), steps, plan.missingFields(),
            plan.requiredProviderSubmissions(), plan.executable());
    }

    private AgentRunDetailVo.TraceVo traceVo(AgentRunTraceDTO trace) {
        List<AgentRunDetailVo.TraceItemVo> items = trace.facts().stream()
            .map(fact -> new AgentRunDetailVo.TraceItemVo(fact.persistedAt(), fact.factType(), fact.status(),
                fact.factType(), text(fact.factId()), fact.detailCode() == null ? fact.stepCode() : fact.detailCode(),
                fact.errorCode(), fact.safeSummary()))
            .toList();
        return new AgentRunDetailVo.TraceVo(trace.completeness(), items);
    }

    private AgentRunDetailVo.ApprovalVo approvalVo(IAgentRunService.ApprovalView approval) {
        if (approval == null) {
            return null;
        }
        return new AgentRunDetailVo.ApprovalVo(text(approval.approvalId()), approval.approvalType(),
            approval.approvalStatus(), approval.revision(), approval.requestSummary());
    }

    private AgentRunDetailVo.ActionVo actionVo(AgentRunOrchestrationDTOs.AdvanceResult result) {
        return result == null ? null : new AgentRunDetailVo.ActionVo(
            result.outcome(), result.errorCode(), result.safeMessage(), result.missingFields());
    }

    private void requirePrincipal(AppPrincipalSnapshotDTO principal, Set<String> requiredPermissions) {
        AppWorkspaceSessionSnapshotDTO workspace = principal == null ? null : principal.workspace();
        boolean canonical = principal != null && principal.appUserId() != null && principal.appUserId() > 0
            && workspace != null && workspace.tenantId() != null && workspace.tenantId() > 0
            && workspace.workspaceKey() != null && !workspace.workspaceKey().isBlank()
            && "personal".equals(workspace.workspaceType()) && "app_user".equals(workspace.ownerType())
            && Objects.equals(principal.appUserId(), workspace.ownerId())
            && workspace.permissions() != null && workspace.permissions().containsAll(requiredPermissions);
        if (!canonical) {
            throw new ServiceException("Agent 执行权限不足", 46703);
        }
    }

    private IAgentRunService.AgentRunView requireOwnedRun(AppPrincipalSnapshotDTO principal, long runId) {
        try {
            return runService.getOwnedRun(principal, runId);
        } catch (ServiceException exception) {
            if (exception.getCode() != null) {
                throw exception;
            }
            throw new ServiceException("AgentRun 不存在", 46704);
        }
    }

    private IAgentRunService.ExecutionSnapshot requireOwnedSnapshot(AppPrincipalSnapshotDTO principal, long runId) {
        try {
            return runService.getOwnedExecutionSnapshot(principal, runId);
        } catch (ServiceException exception) {
            if (exception.getCode() != null) {
                throw exception;
            }
            throw new ServiceException("AgentRun 不存在", 46704);
        }
    }

    private String frozenStartAt(String briefJson) {
        try {
            JsonNode brief = jsonMapper.readTree(briefJson);
            String startAt = brief != null && brief.isObject() && brief.get("startAt") != null
                && brief.get("startAt").isTextual() ? brief.get("startAt").textValue() : null;
            if (createPermissions(startAt).isEmpty()) {
                throw new ServiceException("Agent 冻结合同无效", 46705);
            }
            return startAt;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("Agent 冻结合同无效", 46705);
        }
    }

    private <T> T stableMutation(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (ServiceException exception) {
            if (exception.getCode() != null) {
                throw exception;
            }
            throw new ServiceException("Agent 请求已冲突，请刷新后重试", 46705);
        }
    }

    private String requireCreate(CreateAgentRunBo body) {
        String startAt = body == null ? null : body.getStartAt();
        boolean validCommon = body != null && body.getIdempotencyKey() != null
            && CLIENT_KEY.matcher(body.getIdempotencyKey()).matches();
        boolean validShape = switch (startAt == null ? "" : startAt) {
            case "new" -> text(body.getScriptText(), 1_000)
                && positiveIdOrZero(body.getReferenceVoiceId()) > 0
                && positiveIdOrZero(body.getPortraitId()) > 0 && text(body.getProjectTitle(), 128)
                && absent(body.getVoiceJobId(), body.getVideoJobId(), body.getProjectId(),
                    body.getExpectedRevision(), body.getTaskId());
            case "voice_job" -> positiveIdOrZero(body.getVoiceJobId()) > 0
                && positiveIdOrZero(body.getPortraitId()) > 0 && text(body.getProjectTitle(), 128)
                && absent(body.getScriptText(), body.getReferenceVoiceId(), body.getVideoJobId(),
                    body.getProjectId(), body.getExpectedRevision(), body.getTaskId());
            case "video_job" -> positiveIdOrZero(body.getVideoJobId()) > 0 && text(body.getProjectTitle(), 128)
                && absent(body.getScriptText(), body.getReferenceVoiceId(), body.getPortraitId(),
                    body.getVoiceJobId(), body.getProjectId(), body.getExpectedRevision(), body.getTaskId());
            case "project" -> positiveIdOrZero(body.getProjectId()) > 0
                && positiveIdOrZero(body.getExpectedRevision()) > 0
                && absent(body.getScriptText(), body.getReferenceVoiceId(), body.getPortraitId(),
                    body.getProjectTitle(), body.getVoiceJobId(), body.getVideoJobId(), body.getTaskId());
            case "render_task" -> positiveIdOrZero(body.getTaskId()) > 0
                && absent(body.getScriptText(), body.getReferenceVoiceId(), body.getPortraitId(),
                    body.getProjectTitle(), body.getVoiceJobId(), body.getVideoJobId(), body.getProjectId(),
                    body.getExpectedRevision());
            default -> false;
        };
        if (!validCommon || !validShape) {
            throw new ServiceException("Agent 交付输入无效", 46702);
        }
        return startAt;
    }

    private DigitalHumanJobDTO reusableJob(AppPrincipalSnapshotDTO principal, String jobId,
                                            DigitalHumanJobType expectedType) {
        DigitalHumanJobDTO job;
        try {
            job = generationService.getStoredJob(positiveIdOrZero(jobId), new DigitalHumanOwnerDTO(
                principal.workspace().tenantId(), principal.appUserId()));
        } catch (ServiceException exception) {
            throw new ServiceException("可复用任务不存在或状态无效", 46704);
        }
        if (job == null) {
            throw new ServiceException("可复用任务不存在或状态无效", 46704);
        }
        boolean validVoice = expectedType != DigitalHumanJobType.VOICE_GENERATE
            || job.voiceConfirmed() && job.parentJobId() == null;
        boolean validVideo = expectedType != DigitalHumanJobType.VIDEO_GENERATE
            || job.parentJobId() != null && job.parentJobId() > 0;
        if (job.jobId() == null || job.jobId() != positiveIdOrZero(jobId) || job.jobType() != expectedType
            || job.status() != DigitalHumanJobStatus.SUCCEEDED || !job.outputAvailable()
            || job.inputHash() == null || !SHA256.matcher(job.inputHash()).matches()
            || !validVoice || !validVideo) {
            throw new ServiceException("可复用任务不存在或状态无效", 46704);
        }
        return job;
    }

    private Set<String> createPermissions(String startAt) {
        return switch (startAt == null ? "" : startAt) {
            case "new" -> NEW_PERMISSIONS;
            case "voice_job" -> VOICE_JOB_PERMISSIONS;
            case "video_job" -> VIDEO_JOB_PERMISSIONS;
            case "project" -> PROJECT_PERMISSIONS;
            case "render_task" -> RENDER_TASK_PERMISSIONS;
            default -> Set.of();
        };
    }

    private int requiredProviderSubmissions(String startAt) {
        return switch (startAt) {
            case "new" -> 2;
            case "voice_job" -> 1;
            case "video_job", "project", "render_task" -> 0;
            default -> throw new ServiceException("Agent 交付输入无效", 46702);
        };
    }

    private boolean text(String value, int maxCodePoints) {
        return !blank(value) && value.codePointCount(0, value.length()) <= maxCodePoints;
    }

    private boolean absent(String... values) {
        return java.util.Arrays.stream(values).allMatch(Objects::isNull);
    }

    private void requireRevision(AgentRunRevisionBo body) {
        if (body == null || body.getRowVersion() == null || body.getRowVersion() < 0
            || body.getContractRevision() == null || body.getContractRevision() <= 0) {
            throw new ServiceException("AgentRun 版本无效", 46702);
        }
    }

    private void requireDecision(AgentApprovalDecisionBo body) {
        if (body == null || body.getRowVersion() == null || body.getRowVersion() < 0
            || body.getContractRevision() == null || body.getContractRevision() <= 0
            || body.getApprovalRevision() == null || body.getApprovalRevision() <= 0
            || body.getType() == null || !Set.of("initial", "conditional", "final").contains(body.getType())
            || body.getApproved() == null) {
            throw new ServiceException("Agent 审批版本无效", 46702);
        }
    }

    private String workerId() {
        return "agent-http:" + AppAuditRequestContextHolder.current().requestId();
    }

    private long positiveId(String value, String message) {
        long id = positiveIdOrZero(value);
        if (id == 0) {
            throw new ServiceException(message, 46704);
        }
        return id;
    }

    private long positiveIdOrZero(String value) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String json(ObjectNode node) {
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw new ServiceException("Agent 交付输入无效", 46702);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String text(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private String text(long value) {
        return Long.toString(value);
    }
}
