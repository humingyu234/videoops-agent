package org.dromara.aivideo.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.agent.domain.AgentRunApproval;
import org.dromara.aivideo.agent.domain.AgentRunEvaluation;
import org.dromara.aivideo.agent.dto.AgentRunTraceDTO;
import org.dromara.aivideo.agent.mapper.AgentRunApprovalMapper;
import org.dromara.aivideo.agent.mapper.AgentRunEvaluationMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentRunTraceService;
import org.dromara.aivideo.creation.domain.CreationProject;
import org.dromara.aivideo.creation.mapper.CreationProjectMapper;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanGenerationJob;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.mapper.DigitalHumanGenerationJobMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.aivideo.task.mapper.AiTaskMapper;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Reconstructs a safe snapshot from existing persisted Agent golden-chain facts. */
@Service
@RequiredArgsConstructor
public class AgentRunTraceServiceImpl implements IAgentRunTraceService {

    private static final String DIGITAL_HUMAN_TASK = "digital_human_generation";
    private static final String AI_TASK = "ai_task";
    private static final String DIGITAL_HUMAN_SOURCE = "digital_human_job";
    private static final String CREATION_PROJECT = "creation_project";
    private static final String TIMELINE_RENDER = "timeline_render";
    private static final int MAX_SAFE_SUMMARY_CODE_POINTS = 200;

    private final IAgentRunService runService;
    private final DigitalHumanGenerationJobMapper generationJobMapper;
    private final CreationProjectMapper projectMapper;
    private final AiTaskMapper taskMapper;
    private final AgentRunEvaluationMapper evaluationMapper;
    private final AgentRunApprovalMapper approvalMapper;

    @Override
    public AgentRunTraceDTO getOwnedTrace(AppPrincipalSnapshotDTO principal, long agentRunId) {
        PrincipalScope scope = requireScope(principal, agentRunId);
        IAgentRunService.AgentRunView run = runService.getOwnedRun(principal, agentRunId);
        Map<String, StepIdentity> identities = identities(agentRunId);

        List<AgentRunEvaluation> evaluations = ownedEvaluations(scope.ownerId(), agentRunId);
        List<AgentRunApproval> approvals = ownedApprovals(scope.ownerId(), agentRunId);

        Set<Long> linkedTaskIds = positiveIds(evaluations.stream()
            .map(AgentRunEvaluation::getRenderTaskId).toList());
        if (AI_TASK.equals(run.waitingTaskSource()) && positive(run.waitingTaskId())) {
            linkedTaskIds.add(run.waitingTaskId());
        }
        List<AiTask> tasks = ownedTasks(scope.ownerId(), taskKeys(identities), linkedTaskIds);

        Set<Long> linkedProjectIds = positiveIds(evaluations.stream()
            .map(AgentRunEvaluation::getProjectId).toList());
        tasks.stream().map(AiTask::getResourceId).filter(this::positive).forEach(linkedProjectIds::add);
        List<CreationProject> projects = ownedProjects(scope.ownerId(), projectKeys(identities), linkedProjectIds);

        Set<Long> linkedJobIds = new HashSet<>();
        if (DIGITAL_HUMAN_TASK.equals(run.waitingTaskSource()) && positive(run.waitingTaskId())) {
            linkedJobIds.add(run.waitingTaskId());
        }
        projects.stream().map(CreationProject::getSourceRefId).filter(this::positive).forEach(linkedJobIds::add);
        List<DigitalHumanGenerationJob> jobs = ownedJobs(scope, jobKeys(identities), linkedJobIds);
        includeOwnedParentJobs(scope, jobs);

        List<FactDraft> facts = new ArrayList<>();
        jobs.forEach(job -> facts.add(jobFact(job, identities)));
        projects.forEach(project -> facts.add(projectFact(project, identities)));
        tasks.forEach(task -> facts.add(taskFact(task, identities)));
        evaluations.forEach(evaluation -> facts.add(evaluationFact(evaluation)));
        approvals.forEach(approval -> facts.add(approvalFact(approval)));
        facts.add(runFact(run));
        facts.sort(Comparator
            .comparing(FactDraft::persistedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(FactDraft::sortOrder)
            .thenComparing(FactDraft::factType)
            .thenComparingLong(FactDraft::factId));

        List<AgentRunTraceDTO.Fact> ordered = new ArrayList<>(facts.size());
        for (int index = 0; index < facts.size(); index++) {
            ordered.add(facts.get(index).toFact(index + 1));
        }
        return new AgentRunTraceDTO(run.agentRunId(), AgentRunTraceDTO.DURABLE_FACTS, run.runStatus(),
            run.contractRevision(), run.rowVersion(), ordered);
    }

    private PrincipalScope requireScope(AppPrincipalSnapshotDTO principal, long agentRunId) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0 || agentRunId <= 0
            || principal.workspace() == null || principal.workspace().tenantId() == null
            || principal.workspace().tenantId() <= 0
            || principal.workspace().workspaceKey() == null || principal.workspace().workspaceKey().isBlank()
            || !"personal".equals(principal.workspace().workspaceType())
            || !"app_user".equals(principal.workspace().ownerType())
            || !Objects.equals(principal.appUserId(), principal.workspace().ownerId())) {
            throw new ServiceException("AgentRun Trace 不存在");
        }
        return new PrincipalScope(principal.appUserId(), principal.workspace().tenantId());
    }

    private List<AgentRunEvaluation> ownedEvaluations(long ownerId, long agentRunId) {
        List<AgentRunEvaluation> rows = rows(evaluationMapper.selectList(
            new LambdaQueryWrapper<AgentRunEvaluation>()
                .eq(AgentRunEvaluation::getOwnerUserId, ownerId)
                .eq(AgentRunEvaluation::getAgentRunId, agentRunId)
                .orderByAsc(AgentRunEvaluation::getCandidateNo)
                .orderByAsc(AgentRunEvaluation::getEvaluationId)));
        return rows.stream().filter(row -> Objects.equals(row.getOwnerUserId(), ownerId)
            && Objects.equals(row.getAgentRunId(), agentRunId)).toList();
    }

    private List<AgentRunApproval> ownedApprovals(long ownerId, long agentRunId) {
        List<AgentRunApproval> rows = rows(approvalMapper.selectList(
            new LambdaQueryWrapper<AgentRunApproval>()
                .eq(AgentRunApproval::getOwnerUserId, ownerId)
                .eq(AgentRunApproval::getAgentRunId, agentRunId)
                .orderByAsc(AgentRunApproval::getRevision)
                .orderByAsc(AgentRunApproval::getApprovalId)));
        return rows.stream().filter(row -> Objects.equals(row.getOwnerUserId(), ownerId)
            && Objects.equals(row.getAgentRunId(), agentRunId)).toList();
    }

    private List<AiTask> ownedTasks(long ownerId, Set<String> keys, Set<Long> linkedIds) {
        LambdaQueryWrapper<AiTask> query = new LambdaQueryWrapper<AiTask>()
            .eq(AiTask::getOwnerUserId, ownerId)
            .eq(AiTask::getTaskType, TIMELINE_RENDER)
            .eq(AiTask::getResourceType, CREATION_PROJECT);
        query.and(group -> {
            group.in(AiTask::getIdempotencyKey, keys);
            if (!linkedIds.isEmpty()) {
                group.or().in(AiTask::getTaskId, linkedIds);
            }
        });
        return rows(taskMapper.selectList(query)).stream()
            .filter(task -> Objects.equals(task.getOwnerUserId(), ownerId)
                && TIMELINE_RENDER.equals(task.getTaskType()) && CREATION_PROJECT.equals(task.getResourceType())
                && (keys.contains(task.getIdempotencyKey()) || linkedIds.contains(task.getTaskId())))
            .toList();
    }

    private List<CreationProject> ownedProjects(long ownerId, Set<String> keys, Set<Long> linkedIds) {
        LambdaQueryWrapper<CreationProject> query = new LambdaQueryWrapper<CreationProject>()
            .eq(CreationProject::getOwnerUserId, ownerId)
            .eq(CreationProject::getSourceType, DIGITAL_HUMAN_SOURCE)
            .eq(CreationProject::getDelFlag, "0");
        query.and(group -> {
            group.in(CreationProject::getIdempotencyKey, keys);
            if (!linkedIds.isEmpty()) {
                group.or().in(CreationProject::getProjectId, linkedIds);
            }
        });
        return rows(projectMapper.selectList(query)).stream()
            .filter(project -> Objects.equals(project.getOwnerUserId(), ownerId)
                && DIGITAL_HUMAN_SOURCE.equals(project.getSourceType()) && "0".equals(project.getDelFlag())
                && (keys.contains(project.getIdempotencyKey()) || linkedIds.contains(project.getProjectId())))
            .toList();
    }

    private List<DigitalHumanGenerationJob> ownedJobs(PrincipalScope scope, Set<String> keys,
                                                       Set<Long> linkedIds) {
        LambdaQueryWrapper<DigitalHumanGenerationJob> query = new LambdaQueryWrapper<DigitalHumanGenerationJob>()
            .eq(DigitalHumanGenerationJob::getTenantId, scope.tenantId())
            .eq(DigitalHumanGenerationJob::getOwnerUserId, scope.ownerId());
        query.and(group -> {
            group.in(DigitalHumanGenerationJob::getIdempotencyKey, keys);
            if (!linkedIds.isEmpty()) {
                group.or().in(DigitalHumanGenerationJob::getId, linkedIds);
            }
        });
        return rows(generationJobMapper.selectList(query)).stream()
            .filter(job -> ownedJob(scope, job)
                && (keys.contains(job.getIdempotencyKey()) || linkedIds.contains(job.getId())))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private void includeOwnedParentJobs(PrincipalScope scope, List<DigitalHumanGenerationJob> jobs) {
        Set<Long> existing = positiveIds(jobs.stream().map(DigitalHumanGenerationJob::getId).toList());
        Set<Long> parentIds = positiveIds(jobs.stream().map(DigitalHumanGenerationJob::getParentJobId).toList());
        parentIds.removeAll(existing);
        if (parentIds.isEmpty()) {
            return;
        }
        List<DigitalHumanGenerationJob> parents = rows(generationJobMapper.selectList(
            new LambdaQueryWrapper<DigitalHumanGenerationJob>()
                .eq(DigitalHumanGenerationJob::getTenantId, scope.tenantId())
                .eq(DigitalHumanGenerationJob::getOwnerUserId, scope.ownerId())
                .in(DigitalHumanGenerationJob::getId, parentIds)));
        parents.stream().filter(job -> ownedJob(scope, job) && parentIds.contains(job.getId())
                && job.getJobType() == DigitalHumanJobType.VOICE_GENERATE)
            .forEach(jobs::add);
    }

    private boolean ownedJob(PrincipalScope scope, DigitalHumanGenerationJob job) {
        return Objects.equals(job.getTenantId(), scope.tenantId())
            && Objects.equals(job.getOwnerUserId(), scope.ownerId())
            && (job.getJobType() == DigitalHumanJobType.VOICE_GENERATE
            || job.getJobType() == DigitalHumanJobType.VIDEO_GENERATE);
    }

    private FactDraft runFact(IAgentRunService.AgentRunView run) {
        String relatedType = switch (nullToEmpty(run.waitingTaskSource())) {
            case DIGITAL_HUMAN_TASK -> "generation_job";
            case AI_TASK -> "ai_task";
            default -> null;
        };
        return new FactDraft("agent_run", run.agentRunId(), "agent_run", null, run.runStatus(),
            run.waitingTaskSource(), null, relatedType, relatedType == null ? null : run.waitingTaskId(),
            run.candidateAssetId(), run.errorCode(), safeSummary(run.errorSummary()), run.stateChangedAt(), 100);
    }

    private FactDraft jobFact(DigitalHumanGenerationJob job, Map<String, StepIdentity> identities) {
        StepIdentity identity = identities.get(job.getIdempotencyKey());
        if (identity == null) {
            identity = job.getJobType() == DigitalHumanJobType.VOICE_GENERATE
                ? new StepIdentity("submit_voice", 0L, 10)
                : new StepIdentity("submit_video", 0L, 20);
        }
        return new FactDraft("generation_job", job.getId(), identity.stepCode(), identity.attempt(),
            job.getStatus() == null ? null : job.getStatus().getValue(),
            job.getStage() == null ? null : job.getStage().getValue(), job.getProgress(),
            positive(job.getParentJobId()) ? "generation_job" : null,
            positive(job.getParentJobId()) ? job.getParentJobId() : null, null,
            job.getErrorCode(), safeSummary(job.getErrorMessage()), instant(job.getUpdateTime(), job.getCreateTime()),
            identity.sortOrder());
    }

    private FactDraft projectFact(CreationProject project, Map<String, StepIdentity> identities) {
        StepIdentity identity = identities.getOrDefault(project.getIdempotencyKey(),
            new StepIdentity("prepare_project", null, 30));
        return new FactDraft("creation_project", project.getProjectId(), identity.stepCode(), identity.attempt(),
            project.getProjectStatus(), null, null,
            positive(project.getSourceRefId()) ? "generation_job" : null,
            positive(project.getSourceRefId()) ? project.getSourceRefId() : null,
            project.getCurrentOutputAssetId(), null, null, instant(project.getUpdateTime(), project.getCreateTime()),
            identity.sortOrder());
    }

    private FactDraft taskFact(AiTask task, Map<String, StepIdentity> identities) {
        StepIdentity identity = identities.getOrDefault(task.getIdempotencyKey(),
            new StepIdentity("submit_render", null, 40));
        return new FactDraft("ai_task", task.getTaskId(), identity.stepCode(), identity.attempt(),
            task.getTaskStatus(), task.getStage(), task.getProgressPercent(), "creation_project", task.getResourceId(),
            task.getResultAssetId(), task.getErrorCode(), safeSummary(task.getErrorSummary()),
            instant(task.getUpdateTime(), task.getFinishedAt(), task.getStartedAt(), task.getCreateTime()),
            identity.sortOrder());
    }

    private FactDraft evaluationFact(AgentRunEvaluation evaluation) {
        return new FactDraft("quality_evaluation", evaluation.getEvaluationId(), "quality_evaluation",
            evaluation.getCandidateNo(), evaluation.getDecision(), evaluation.getRepairScope(), null, "ai_task",
            evaluation.getRenderTaskId(), evaluation.getResultAssetId(), null, null,
            instant(evaluation.getCreateTime(), evaluation.getUpdateTime()), 80);
    }

    private FactDraft approvalFact(AgentRunApproval approval) {
        String relatedType = approval.getEvaluationId() == null ? "agent_run" : "quality_evaluation";
        Long relatedId = approval.getEvaluationId() == null ? approval.getAgentRunId() : approval.getEvaluationId();
        String summary = approval.getDecisionSummary() == null
            ? approval.getRequestSummary() : approval.getDecisionSummary();
        return new FactDraft("approval", approval.getApprovalId(), "approval", approval.getRevision(),
            approval.getApprovalStatus(), approval.getApprovalType(), null, relatedType, relatedId, null, null,
            safeSummary(summary), instant(approval.getDecidedAt(), approval.getUpdateTime(), approval.getCreateTime()),
            90);
    }

    private Map<String, StepIdentity> identities(long runId) {
        Map<String, StepIdentity> identities = new LinkedHashMap<>();
        identities.put(key(runId, "voice", 0), new StepIdentity("submit_voice", 0L, 10));
        identities.put(key(runId, "video", 0), new StepIdentity("submit_video", 0L, 20));
        identities.put(key(runId, "project", 0), new StepIdentity("prepare_project", 0L, 30));
        identities.put(key(runId, "render", 0), new StepIdentity("submit_render", 0L, 40));
        identities.put(key(runId, "render", 1), new StepIdentity("submit_render", 1L, 41));
        for (long attempt = 1; attempt <= 2; attempt++) {
            identities.put(key(runId, "repair-project", attempt),
                new StepIdentity("repair_project", attempt, 50 + Math.toIntExact(attempt) * 2));
            identities.put(key(runId, "repair-render", attempt),
                new StepIdentity("repair_render", attempt, 51 + Math.toIntExact(attempt) * 2));
        }
        return identities;
    }

    private Set<String> jobKeys(Map<String, StepIdentity> identities) {
        return keysFor(identities, "submit_voice", "submit_video");
    }

    private Set<String> projectKeys(Map<String, StepIdentity> identities) {
        return keysFor(identities, "prepare_project", "repair_project");
    }

    private Set<String> taskKeys(Map<String, StepIdentity> identities) {
        return keysFor(identities, "submit_render", "repair_render");
    }

    private Set<String> keysFor(Map<String, StepIdentity> identities, String... stepCodes) {
        Set<String> expected = Set.of(stepCodes);
        Set<String> keys = new HashSet<>();
        identities.forEach((key, identity) -> {
            if (expected.contains(identity.stepCode())) {
                keys.add(key);
            }
        });
        return keys;
    }

    private String key(long runId, String step, long attempt) {
        return "agent-run:" + runId + ":" + step + ":" + attempt;
    }

    private <T> List<T> rows(List<T> values) {
        return values == null ? List.of() : values;
    }

    private Set<Long> positiveIds(List<Long> values) {
        Set<Long> ids = new HashSet<>();
        values.stream().filter(this::positive).forEach(ids::add);
        return ids;
    }

    private boolean positive(Long value) {
        return value != null && value > 0;
    }

    private Instant instant(LocalDateTime... values) {
        for (LocalDateTime value : values) {
            if (value != null) {
                return value.toInstant(ZoneOffset.UTC);
            }
        }
        return null;
    }

    private String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[\\p{Cc}\\p{Cf}]+", " ").replaceAll("\\s+", " ").trim();
        if (normalized.codePointCount(0, normalized.length()) <= MAX_SAFE_SUMMARY_CODE_POINTS) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, MAX_SAFE_SUMMARY_CODE_POINTS);
        return normalized.substring(0, end);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record PrincipalScope(long ownerId, long tenantId) {
    }

    private record StepIdentity(String stepCode, Long attempt, int sortOrder) {
    }

    private record FactDraft(String factType, long factId, String stepCode, Long attempt, String status,
                             String detailCode, Integer progressPercent, String relatedFactType, Long relatedFactId,
                             Long resultAssetId, String errorCode, String safeSummary, Instant persistedAt,
                             int sortOrder) {

        private AgentRunTraceDTO.Fact toFact(int sequence) {
            return new AgentRunTraceDTO.Fact(sequence, factType, factId, stepCode, attempt, status, detailCode,
                progressPercent, relatedFactType, relatedFactId, resultAssetId, errorCode, safeSummary, persistedAt);
        }
    }
}
