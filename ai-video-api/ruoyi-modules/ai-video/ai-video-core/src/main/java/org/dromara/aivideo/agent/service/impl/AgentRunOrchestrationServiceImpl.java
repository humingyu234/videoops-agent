package org.dromara.aivideo.agent.service.impl;

import org.dromara.aivideo.agent.dto.AgentRunOrchestrationDTOs;
import org.dromara.aivideo.agent.dto.AgentToolDTOs;
import org.dromara.aivideo.agent.service.IAgentRunOrchestrationService;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.agent.service.IAgentToolService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Closed T4 state machine over the recoverable AgentRun and the eight T3 tools.
 */
@Service
@ConditionalOnAppSecurityEnabled
public class AgentRunOrchestrationServiceImpl implements IAgentRunOrchestrationService {

    private static final String QUEUED = "queued";
    private static final String RUNNING = "running";
    private static final String WAITING_INPUT = "waiting_input";
    private static final String WAITING_EXTERNAL_TASK = "waiting_external_task";
    private static final String COMPLETED = "completed";
    private static final String FAILED = "failed";
    private static final String CANCELLED = "cancelled";

    private static final String DIGITAL_HUMAN_TASK = "digital_human_generation";
    private static final String AI_TASK = "ai_task";

    private static final String SUBMIT_VOICE = "submit_voice_generation";
    private static final String CONFIRM_VOICE = "confirm_voice_generation";
    private static final String GET_GENERATION = "get_generation_status";
    private static final String SUBMIT_VIDEO = "submit_digital_human_video";
    private static final String PREPARE_PROJECT = "prepare_timeline_project";
    private static final String RENDER_TIMELINE = "render_timeline";
    private static final String GET_RENDER = "get_timeline_render_status";
    private static final String INSPECT_OUTPUT = "inspect_timeline_output";

    private static final String VOICE_GENERATE = "voice_generate";
    private static final String VIDEO_GENERATE = "video_generate";

    private static final Pattern POSITIVE_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private static final int MIN_RUN_SECONDS = 1;
    private static final int MAX_RUN_SECONDS = 86_400;
    private static final int MIN_RESUME_ATTEMPTS = 1;
    private static final int MAX_RESUME_ATTEMPTS = 1_000;
    private static final int MIN_PROVIDER_SUBMISSIONS = 0;
    private static final int MAX_PROVIDER_SUBMISSIONS = 2;
    private static final int MIN_RENDER_RETRIES = 0;
    private static final int MAX_RENDER_RETRIES = 1;
    private static final int MIN_POLL_SECONDS = 1;
    private static final int MAX_POLL_SECONDS = 300;

    private static final List<String> PERMISSION_ORDER = List.of(
        "aivideo:studio:generate",
        "aivideo:studio:query",
        "aivideo:voice:query",
        "aivideo:portrait:query",
        "aivideo:creation:edit",
        "aivideo:creation:generate",
        "aivideo:task:query",
        "aivideo:creation-asset:query"
    );

    private static final List<StepDefinition> STEPS = List.of(
        new StepDefinition(1, "submit_voice", SUBMIT_VOICE),
        new StepDefinition(2, "wait_voice", GET_GENERATION),
        new StepDefinition(3, "confirm_voice", CONFIRM_VOICE),
        new StepDefinition(4, "submit_video", SUBMIT_VIDEO),
        new StepDefinition(5, "wait_video", GET_GENERATION),
        new StepDefinition(6, "prepare_project", PREPARE_PROJECT),
        new StepDefinition(7, "submit_render", RENDER_TIMELINE),
        new StepDefinition(8, "wait_render", GET_RENDER),
        new StepDefinition(9, "inspect_output", INSPECT_OUTPUT)
    );

    private final IAgentRunService runService;
    private final IAgentToolService toolService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Autowired
    public AgentRunOrchestrationServiceImpl(IAgentRunService runService,
                                            IAgentToolService toolService,
                                            JsonMapper jsonMapper) {
        this(runService, toolService, jsonMapper, Clock.systemUTC());
    }

    AgentRunOrchestrationServiceImpl(IAgentRunService runService,
                                     IAgentToolService toolService,
                                     JsonMapper jsonMapper,
                                     Clock clock) {
        this.runService = Objects.requireNonNull(runService, "runService");
        this.toolService = Objects.requireNonNull(toolService, "toolService");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AgentRunOrchestrationDTOs.PlanResult plan(AppPrincipalSnapshotDTO principal, long agentRunId) {
        IAgentRunService.ExecutionSnapshot snapshot = runService.getOwnedExecutionSnapshot(principal, agentRunId);
        ParsedContract contract = parse(snapshot.deliveryBriefJson(), snapshot.acceptanceProfileJson());
        return plan(principal, snapshot.run(), contract);
    }

    @Override
    public AgentRunOrchestrationDTOs.AdvanceResult advance(
        AppPrincipalSnapshotDTO principal, AgentRunOrchestrationDTOs.AdvanceCommand command) {
        requireAdvanceCommand(command);
        IAgentRunService.ExecutionSnapshot snapshot = runService.getOwnedExecutionSnapshot(
            principal, command.agentRunId());
        IAgentRunService.AgentRunView run = snapshot.run();
        if (!expected(run, command.expectedRowVersion(), command.expectedContractRevision())) {
            return stateConflict(run);
        }
        if (terminal(run.runStatus())) {
            return terminal(run);
        }

        ParsedContract contract = parse(snapshot.deliveryBriefJson(), snapshot.acceptanceProfileJson());
        AgentRunOrchestrationDTOs.PlanResult plan = plan(principal, run, contract);
        if (!plan.executable()) {
            return blockBeforeTools(principal, run, plan.missingFields());
        }
        if (WAITING_INPUT.equals(run.runStatus())) {
            return blocked(run, List.of("deliveryBriefVersionId"));
        }
        if (deadlineReached(run, contract.policy())) {
            return stop(principal, run.agentRunId(), run.rowVersion(), run.contractRevision(), FAILED,
                "AGENT_RUN_TIMEOUT", "Agent 执行已达到时间上限");
        }
        if (run.leaseGeneration() > contract.policy().maxResumeAttempts()) {
            return stop(principal, run.agentRunId(), run.rowVersion(), run.contractRevision(), FAILED,
                "AGENT_RESUME_BUDGET_EXHAUSTED", "Agent 恢复次数已达到上限");
        }

        IAgentRunService.AgentRunLease lease = runService.claim(principal,
            new IAgentRunService.ClaimAgentRunCommand(run.agentRunId(), run.rowVersion(), run.contractRevision(),
                command.workerId(), leaseSeconds(contract.policy())));
        if (lease == null) {
            return stateConflict(run);
        }
        try {
            if (lease.waitingTaskSource() != null && lease.waitingTaskId() != null) {
                return resumeWaiting(principal, contract, lease, run.retryCount());
            }
            return start(principal, contract, lease);
        } catch (OrchestrationFailure failure) {
            return stop(principal, lease.agentRunId(), lease.rowVersion(), lease.contractRevision(),
                failure.terminalStatus, failure.code, failure.safeMessage);
        } catch (ServiceException exception) {
            FailureFact fact = serviceFailure(exception);
            return stop(principal, lease.agentRunId(), lease.rowVersion(), lease.contractRevision(), FAILED,
                fact.code(), fact.safeMessage());
        } catch (RuntimeException exception) {
            return stop(principal, lease.agentRunId(), lease.rowVersion(), lease.contractRevision(), FAILED,
                "AGENT_EXECUTION_FAILED", "Agent 执行失败，请转人工处理");
        }
    }

    @Override
    public AgentRunOrchestrationDTOs.AdvanceResult cancel(
        AppPrincipalSnapshotDTO principal, AgentRunOrchestrationDTOs.CancelCommand command) {
        if (command == null || command.agentRunId() <= 0 || command.expectedRowVersion() < 0
            || command.expectedContractRevision() <= 0) {
            throw new IllegalArgumentException("cancel command is invalid");
        }
        IAgentRunService.ExecutionSnapshot snapshot = runService.getOwnedExecutionSnapshot(
            principal, command.agentRunId());
        IAgentRunService.AgentRunView run = snapshot.run();
        if (!expected(run, command.expectedRowVersion(), command.expectedContractRevision())) {
            return stateConflict(run);
        }
        if (terminal(run.runStatus())) {
            return terminal(run);
        }
        return stop(principal, run.agentRunId(), run.rowVersion(), run.contractRevision(), CANCELLED,
            "AGENT_RUN_CANCELLED", "Agent 执行已取消");
    }

    private AgentRunOrchestrationDTOs.AdvanceResult start(AppPrincipalSnapshotDTO principal,
                                                           ParsedContract contract,
                                                           IAgentRunService.AgentRunLease lease) {
        GoldenInput input = contract.input();
        return switch (input.startAt()) {
            case NEW -> {
                AgentToolDTOs.GenerationJobResult voice = generation(call(principal, SUBMIT_VOICE,
                    object("idempotencyKey", key(lease.agentRunId(), "voice", 0),
                        "scriptText", input.scriptText(), "referenceVoiceId", input.referenceVoiceId())));
                requireGeneration(voice, VOICE_GENERATE, null);
                if (failedGeneration(voice)) {
                    throw generationFailure(voice);
                }
                yield parkInitial(principal, lease, DIGITAL_HUMAN_TASK, positiveLong(voice.jobId()),
                    contract.policy());
            }
            case VOICE_JOB -> {
                AgentToolDTOs.GenerationJobResult voice = generation(call(principal, GET_GENERATION,
                    object("jobId", input.voiceJobId())));
                requireGeneration(voice, VOICE_GENERATE, input.voiceJobId());
                if (failedGeneration(voice)) {
                    throw generationFailure(voice);
                }
                yield parkInitial(principal, lease, DIGITAL_HUMAN_TASK, positiveLong(voice.jobId()),
                    contract.policy());
            }
            case VIDEO_JOB -> {
                AgentToolDTOs.GenerationJobResult video = generation(call(principal, GET_GENERATION,
                    object("jobId", input.videoJobId())));
                requireGeneration(video, VIDEO_GENERATE, input.videoJobId());
                if (failedGeneration(video)) {
                    throw generationFailure(video);
                }
                yield parkInitial(principal, lease, DIGITAL_HUMAN_TASK, positiveLong(video.jobId()),
                    contract.policy());
            }
            case PROJECT -> {
                AgentToolDTOs.RenderTaskResult render = renderTask(call(principal, RENDER_TIMELINE,
                    object("idempotencyKey", key(lease.agentRunId(), "render", 0),
                        "projectId", input.projectId(), "expectedRevision", input.expectedRevision())));
                requireRenderTask(render, input.projectId(), input.expectedRevision());
                requireParkableRender(principal, render);
                yield parkInitial(principal, lease, AI_TASK, positiveLong(render.taskId()), contract.policy());
            }
            case RENDER_TASK -> {
                AgentToolDTOs.RenderStatusResult render = renderStatus(call(principal, GET_RENDER,
                    object("taskId", input.taskId())));
                requireRenderStatus(render, input.taskId());
                if (failedRender(render) || cancelledRender(render)) {
                    throw renderFailure(render);
                }
                yield parkInitial(principal, lease, AI_TASK, positiveLong(render.taskId()), contract.policy());
            }
        };
    }

    private AgentRunOrchestrationDTOs.AdvanceResult resumeWaiting(AppPrincipalSnapshotDTO principal,
                                                                   ParsedContract contract,
                                                                   IAgentRunService.AgentRunLease lease,
                                                                   long retryCount) {
        if (DIGITAL_HUMAN_TASK.equals(lease.waitingTaskSource())) {
            return resumeGeneration(principal, contract, lease);
        }
        if (AI_TASK.equals(lease.waitingTaskSource())) {
            return resumeRender(principal, contract, lease, retryCount);
        }
        throw invalidResult();
    }

    private AgentRunOrchestrationDTOs.AdvanceResult resumeGeneration(AppPrincipalSnapshotDTO principal,
                                                                      ParsedContract contract,
                                                                      IAgentRunService.AgentRunLease lease) {
        long waitingId = lease.waitingTaskId();
        AgentToolDTOs.GenerationJobResult job = generation(call(principal, GET_GENERATION,
            object("jobId", Long.toString(waitingId))));
        requireGeneration(job, null, Long.toString(waitingId));
        if (activeGeneration(job)) {
            return defer(principal, lease, DIGITAL_HUMAN_TASK, waitingId, contract.policy());
        }
        if (failedGeneration(job)) {
            throw generationFailure(job);
        }
        if (!succeededGeneration(job) || !job.outputAvailable()) {
            throw invalidResult();
        }
        if (VOICE_GENERATE.equals(job.jobType())) {
            return advanceFromVoice(principal, contract, lease, job);
        }
        if (VIDEO_GENERATE.equals(job.jobType())) {
            return advanceFromVideo(principal, contract, lease, job);
        }
        throw invalidResult();
    }

    private AgentRunOrchestrationDTOs.AdvanceResult advanceFromVoice(AppPrincipalSnapshotDTO principal,
                                                                     ParsedContract contract,
                                                                     IAgentRunService.AgentRunLease lease,
                                                                     AgentToolDTOs.GenerationJobResult voice) {
        GoldenInput input = contract.input();
        if (input.portraitId() == null || input.projectTitle() == null) {
            throw new OrchestrationFailure(FAILED, "AGENT_INPUT_REQUIRED", "人物与项目标题需要重新确认");
        }
        if (!voice.voiceConfirmed()) {
            AgentToolDTOs.GenerationJobResult confirmed = generation(call(principal, CONFIRM_VOICE,
                object("jobId", voice.jobId())));
            requireGeneration(confirmed, VOICE_GENERATE, voice.jobId());
            if (!succeededGeneration(confirmed) || !confirmed.outputAvailable() || !confirmed.voiceConfirmed()) {
                throw invalidResult();
            }
        }
        AgentToolDTOs.GenerationJobResult video = generation(call(principal, SUBMIT_VIDEO,
            object("idempotencyKey", key(lease.agentRunId(), "video", 0),
                "voiceJobId", voice.jobId(), "portraitId", input.portraitId())));
        requireGeneration(video, VIDEO_GENERATE, null);
        if (!Objects.equals(video.parentJobId(), voice.jobId())) {
            throw invalidResult();
        }
        if (failedGeneration(video)) {
            throw generationFailure(video);
        }
        long videoId = positiveLong(video.jobId());
        IAgentRunService.WaitingReceipt receipt = runService.advanceExternalTask(principal,
            new IAgentRunService.AdvanceExternalTaskCommand(lease.proof(), DIGITAL_HUMAN_TASK, lease.waitingTaskId(),
                DIGITAL_HUMAN_TASK, videoId, resumeAfter(contract.policy())));
        return waiting(receipt, lease.agentRunId());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult advanceFromVideo(AppPrincipalSnapshotDTO principal,
                                                                     ParsedContract contract,
                                                                     IAgentRunService.AgentRunLease lease,
                                                                     AgentToolDTOs.GenerationJobResult video) {
        GoldenInput input = contract.input();
        if (input.projectTitle() == null) {
            throw new OrchestrationFailure(FAILED, "AGENT_INPUT_REQUIRED", "项目标题需要重新确认");
        }
        AgentToolDTOs.ProjectResult project = project(call(principal, PREPARE_PROJECT,
            object("idempotencyKey", key(lease.agentRunId(), "project", 0),
                "videoJobId", video.jobId(), "projectTitle", input.projectTitle())));
        requireProject(project);
        AgentToolDTOs.RenderTaskResult render = renderTask(call(principal, RENDER_TIMELINE,
            object("idempotencyKey", key(lease.agentRunId(), "render", 0),
                "projectId", project.projectId(), "expectedRevision", project.currentDraftRevision())));
        requireRenderTask(render, project.projectId(), project.currentDraftRevision());
        requireParkableRender(principal, render);
        long renderId = positiveLong(render.taskId());
        IAgentRunService.WaitingReceipt receipt = runService.advanceExternalTask(principal,
            new IAgentRunService.AdvanceExternalTaskCommand(lease.proof(), DIGITAL_HUMAN_TASK, lease.waitingTaskId(),
                AI_TASK, renderId, resumeAfter(contract.policy())));
        return waiting(receipt, lease.agentRunId());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult resumeRender(AppPrincipalSnapshotDTO principal,
                                                                  ParsedContract contract,
                                                                  IAgentRunService.AgentRunLease lease,
                                                                  long retryCount) {
        long waitingId = lease.waitingTaskId();
        AgentToolDTOs.RenderStatusResult render = renderStatus(call(principal, GET_RENDER,
            object("taskId", Long.toString(waitingId))));
        requireRenderStatus(render, Long.toString(waitingId));
        if (activeRender(render)) {
            return defer(principal, lease, AI_TASK, waitingId, contract.policy());
        }
        if (cancelledRender(render)) {
            throw new OrchestrationFailure(CANCELLED, "EXTERNAL_TASK_CANCELLED", "渲染任务已取消");
        }
        if (failedRender(render)) {
            if (render.retryable() && retryCount < contract.policy().maxRenderRetries()) {
                int attempt = Math.toIntExact(retryCount + 1);
                AgentToolDTOs.RenderTaskResult retry = renderTask(call(principal, RENDER_TIMELINE,
                    object("idempotencyKey", key(lease.agentRunId(), "render", attempt),
                        "projectId", render.projectId(), "expectedRevision", render.draftRevision())));
                requireRenderTask(retry, render.projectId(), render.draftRevision());
                requireParkableRender(principal, retry);
                IAgentRunService.WaitingReceipt receipt = runService.retryExternalTask(principal,
                    new IAgentRunService.RetryExternalTaskCommand(lease.proof(), waitingId,
                        positiveLong(retry.taskId()), resumeAfter(contract.policy())));
                return waiting(receipt, lease.agentRunId());
            }
            throw renderFailure(render);
        }
        if (!"success".equals(render.status()) || render.resultAssetId() == null) {
            throw invalidResult();
        }
        AgentToolDTOs.OutputInspectionResult output = output(call(principal, INSPECT_OUTPUT,
            object("taskId", render.taskId())));
        requireOutput(output, render);
        long assetId = positiveLong(output.assetId());
        boolean completed = runService.completeExternalTask(principal,
            new IAgentRunService.CompleteExternalTaskCommand(lease.proof(), AI_TASK, waitingId, assetId,
                resultSummary(output)));
        if (!completed) {
            return stateConflict(lease.agentRunId(), lease.rowVersion(), lease.contractRevision());
        }
        return new AgentRunOrchestrationDTOs.AdvanceResult(lease.agentRunId(), COMPLETED, "completed",
            null, null, assetId, List.of(), null, null);
    }

    private AgentRunOrchestrationDTOs.AdvanceResult parkInitial(AppPrincipalSnapshotDTO principal,
                                                                 IAgentRunService.AgentRunLease lease,
                                                                 String taskSource,
                                                                 long taskId,
                                                                 ExecutionPolicy policy) {
        IAgentRunService.WaitingReceipt receipt = runService.waitForExternalTask(principal,
            new IAgentRunService.WaitForExternalTaskCommand(lease.proof(), taskSource, taskId,
                resumeAfter(policy)));
        return waiting(receipt, lease.agentRunId());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult defer(AppPrincipalSnapshotDTO principal,
                                                           IAgentRunService.AgentRunLease lease,
                                                           String taskSource,
                                                           long taskId,
                                                           ExecutionPolicy policy) {
        IAgentRunService.WaitingReceipt receipt = runService.deferExternalTask(principal,
            new IAgentRunService.DeferExternalTaskCommand(lease.proof(), taskSource, taskId,
                resumeAfter(policy)));
        return waiting(receipt, lease.agentRunId());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult waiting(IAgentRunService.WaitingReceipt receipt,
                                                             long agentRunId) {
        if (receipt == null) {
            return stateConflict(agentRunId, -1, -1);
        }
        return new AgentRunOrchestrationDTOs.AdvanceResult(agentRunId, WAITING_EXTERNAL_TASK, "waiting",
            receipt.taskSource(), receipt.taskId(), null, List.of(), null, null);
    }

    private AgentRunOrchestrationDTOs.PlanResult plan(AppPrincipalSnapshotDTO principal,
                                                       IAgentRunService.AgentRunView run,
                                                       ParsedContract contract) {
        List<String> missing = new ArrayList<>(contract.issues());
        Set<String> required = requiredPermissions(contract.input() == null ? null : contract.input().startAt());
        if (!canonicalPrincipal(principal)) {
            addMissing(missing, "principal");
        } else {
            Set<String> actual = principal.workspace().permissions();
            for (String permission : required) {
                if (!actual.contains(permission)) {
                    addMissing(missing, "permission:" + permission);
                }
            }
        }
        int providerSubmissions = requiredProviderSubmissions(
            contract.input() == null ? null : contract.input().startAt());
        if (contract.policy() != null && contract.policy().maxProviderSubmissions() < providerSubmissions) {
            addMissing(missing, "profile.maxProviderSubmissions");
        }

        StartAt startAt = contract.input() == null ? null : contract.input().startAt();
        int skippedThrough = skippedThrough(startAt);
        List<AgentRunOrchestrationDTOs.PlanStep> steps = STEPS.stream()
            .map(step -> new AgentRunOrchestrationDTOs.PlanStep(step.sequence(), step.stepType(), step.toolName(),
                startAt == null ? "blocked" : step.sequence() <= skippedThrough ? "skipped" : "required",
                startAt == null ? "invalid_contract" : step.sequence() <= skippedThrough
                    ? "provided_" + startAt.value : null))
            .toList();
        return new AgentRunOrchestrationDTOs.PlanResult(run.agentRunId(), startAt == null ? null : startAt.value,
            steps, List.copyOf(missing), PERMISSION_ORDER.stream().filter(required::contains).toList(),
            providerSubmissions, missing.isEmpty());
    }

    private ParsedContract parse(String briefJson, String profileJson) {
        List<String> issues = new ArrayList<>();
        GoldenInput input = parseBrief(briefJson, issues);
        ExecutionPolicy policy = parseProfile(profileJson, issues);
        return new ParsedContract(input, policy, List.copyOf(issues));
    }

    private GoldenInput parseBrief(String json, List<String> issues) {
        JsonNode node = objectJson(json, "brief", issues);
        if (node == null) {
            return null;
        }
        String startValue = textual(node, "startAt", 32, "brief.startAt", issues);
        StartAt startAt = StartAt.from(startValue);
        if (startAt == null) {
            addMissing(issues, "brief.startAt");
            return null;
        }
        Set<String> expected = switch (startAt) {
            case NEW -> Set.of("startAt", "scriptText", "referenceVoiceId", "portraitId", "projectTitle");
            case VOICE_JOB -> Set.of("startAt", "voiceJobId", "portraitId", "projectTitle");
            case VIDEO_JOB -> Set.of("startAt", "videoJobId", "projectTitle");
            case PROJECT -> Set.of("startAt", "projectId", "expectedRevision");
            case RENDER_TASK -> Set.of("startAt", "taskId");
        };
        exactFields(node, expected, "brief", issues);

        String scriptText = null;
        String referenceVoiceId = null;
        String portraitId = null;
        String projectTitle = null;
        String voiceJobId = null;
        String videoJobId = null;
        String projectId = null;
        String expectedRevision = null;
        String taskId = null;
        switch (startAt) {
            case NEW -> {
                scriptText = textual(node, "scriptText", 1_000, "brief.scriptText", issues);
                referenceVoiceId = positiveId(node, "referenceVoiceId", "brief.referenceVoiceId", issues);
                portraitId = positiveId(node, "portraitId", "brief.portraitId", issues);
                projectTitle = textual(node, "projectTitle", 128, "brief.projectTitle", issues);
            }
            case VOICE_JOB -> {
                voiceJobId = positiveId(node, "voiceJobId", "brief.voiceJobId", issues);
                portraitId = positiveId(node, "portraitId", "brief.portraitId", issues);
                projectTitle = textual(node, "projectTitle", 128, "brief.projectTitle", issues);
            }
            case VIDEO_JOB -> {
                videoJobId = positiveId(node, "videoJobId", "brief.videoJobId", issues);
                projectTitle = textual(node, "projectTitle", 128, "brief.projectTitle", issues);
            }
            case PROJECT -> {
                projectId = positiveId(node, "projectId", "brief.projectId", issues);
                expectedRevision = positiveId(node, "expectedRevision", "brief.expectedRevision", issues);
            }
            case RENDER_TASK -> taskId = positiveId(node, "taskId", "brief.taskId", issues);
        }
        return new GoldenInput(startAt, scriptText, referenceVoiceId, portraitId, projectTitle, voiceJobId,
            videoJobId, projectId, expectedRevision, taskId);
    }

    private ExecutionPolicy parseProfile(String json, List<String> issues) {
        JsonNode node = objectJson(json, "profile", issues);
        if (node == null) {
            return null;
        }
        exactFields(node, Set.of("maxRunSeconds", "maxResumeAttempts", "maxProviderSubmissions",
            "maxRenderRetries", "pollIntervalSeconds"), "profile", issues);
        Integer maxRunSeconds = integer(node, "maxRunSeconds", MIN_RUN_SECONDS, MAX_RUN_SECONDS, issues);
        Integer maxResumeAttempts = integer(node, "maxResumeAttempts", MIN_RESUME_ATTEMPTS,
            MAX_RESUME_ATTEMPTS, issues);
        Integer maxProviderSubmissions = integer(node, "maxProviderSubmissions", MIN_PROVIDER_SUBMISSIONS,
            MAX_PROVIDER_SUBMISSIONS, issues);
        Integer maxRenderRetries = integer(node, "maxRenderRetries", MIN_RENDER_RETRIES,
            MAX_RENDER_RETRIES, issues);
        Integer pollIntervalSeconds = integer(node, "pollIntervalSeconds", MIN_POLL_SECONDS,
            MAX_POLL_SECONDS, issues);
        if (maxRunSeconds == null || maxResumeAttempts == null || maxProviderSubmissions == null
            || maxRenderRetries == null || pollIntervalSeconds == null) {
            return null;
        }
        return new ExecutionPolicy(maxRunSeconds, maxResumeAttempts, maxProviderSubmissions, maxRenderRetries,
            pollIntervalSeconds);
    }

    private JsonNode objectJson(String json, String label, List<String> issues) {
        if (json == null || json.isBlank()) {
            addMissing(issues, label);
            return null;
        }
        try {
            JsonNode node = jsonMapper.readTree(json);
            if (node == null || !node.isObject()) {
                addMissing(issues, label);
                return null;
            }
            return node;
        } catch (Exception exception) {
            addMissing(issues, label);
            return null;
        }
    }

    private void exactFields(JsonNode node, Set<String> expected, String prefix, List<String> issues) {
        Set<String> actual = node.properties().stream().map(Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (String field : expected) {
            if (!actual.contains(field)) {
                addMissing(issues, prefix + "." + field);
            }
        }
        for (String field : actual) {
            if (!expected.contains(field)) {
                addMissing(issues, prefix + "." + field + ":unexpected");
            }
        }
    }

    private String textual(JsonNode node, String field, int maxCodePoints, String issue, List<String> issues) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue() == null || value.textValue().isBlank()
            || value.textValue().codePointCount(0, value.textValue().length()) > maxCodePoints) {
            addMissing(issues, issue);
            return null;
        }
        return value.textValue();
    }

    private String positiveId(JsonNode node, String field, String issue, List<String> issues) {
        String value = textual(node, field, 19, issue, issues);
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            addMissing(issues, issue);
            return null;
        }
        try {
            Long.parseLong(value);
            return value;
        } catch (NumberFormatException exception) {
            addMissing(issues, issue);
            return null;
        }
    }

    private Integer integer(JsonNode node, String field, int minimum, int maximum, List<String> issues) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            addMissing(issues, "profile." + field);
            return null;
        }
        long number = value.longValue();
        if (number < minimum || number > maximum) {
            addMissing(issues, "profile." + field);
            return null;
        }
        return (int) number;
    }

    private Set<String> requiredPermissions(StartAt startAt) {
        if (startAt == null) {
            return Set.of();
        }
        return switch (startAt) {
            case NEW -> Set.copyOf(PERMISSION_ORDER);
            case VOICE_JOB -> Set.of("aivideo:studio:generate", "aivideo:studio:query",
                "aivideo:portrait:query", "aivideo:creation:edit", "aivideo:creation:generate",
                "aivideo:task:query", "aivideo:creation-asset:query");
            case VIDEO_JOB -> Set.of("aivideo:studio:query", "aivideo:creation:edit",
                "aivideo:creation:generate", "aivideo:task:query", "aivideo:creation-asset:query");
            case PROJECT -> Set.of("aivideo:creation:generate", "aivideo:task:query",
                "aivideo:creation-asset:query");
            case RENDER_TASK -> Set.of("aivideo:task:query", "aivideo:creation-asset:query");
        };
    }

    private boolean canonicalPrincipal(AppPrincipalSnapshotDTO principal) {
        AppWorkspaceSessionSnapshotDTO workspace = principal == null ? null : principal.workspace();
        return principal != null && principal.appUserId() != null && principal.appUserId() > 0
            && workspace != null && workspace.tenantId() != null && workspace.tenantId() > 0
            && workspace.workspaceKey() != null && !workspace.workspaceKey().isBlank()
            && "personal".equals(workspace.workspaceType()) && "app_user".equals(workspace.ownerType())
            && Objects.equals(principal.appUserId(), workspace.ownerId()) && workspace.permissions() != null;
    }

    private AgentToolDTOs.Result call(AppPrincipalSnapshotDTO principal, String toolName, ObjectNode arguments) {
        return toolService.execute(principal, new AgentToolDTOs.Call(toolName, arguments));
    }

    private ObjectNode object(String firstName, String firstValue, String... remaining) {
        if (firstValue == null || remaining.length % 2 != 0) {
            throw invalidResult();
        }
        ObjectNode node = jsonMapper.createObjectNode();
        node.put(firstName, firstValue);
        for (int index = 0; index < remaining.length; index += 2) {
            if (remaining[index + 1] == null) {
                throw invalidResult();
            }
            node.put(remaining[index], remaining[index + 1]);
        }
        return node;
    }

    private AgentToolDTOs.GenerationJobResult generation(AgentToolDTOs.Result result) {
        if (result instanceof AgentToolDTOs.GenerationJobResult job) {
            return job;
        }
        throw invalidResult();
    }

    private AgentToolDTOs.ProjectResult project(AgentToolDTOs.Result result) {
        if (result instanceof AgentToolDTOs.ProjectResult project) {
            return project;
        }
        throw invalidResult();
    }

    private AgentToolDTOs.RenderTaskResult renderTask(AgentToolDTOs.Result result) {
        if (result instanceof AgentToolDTOs.RenderTaskResult task) {
            return task;
        }
        throw invalidResult();
    }

    private AgentToolDTOs.RenderStatusResult renderStatus(AgentToolDTOs.Result result) {
        if (result instanceof AgentToolDTOs.RenderStatusResult status) {
            return status;
        }
        throw invalidResult();
    }

    private AgentToolDTOs.OutputInspectionResult output(AgentToolDTOs.Result result) {
        if (result instanceof AgentToolDTOs.OutputInspectionResult output) {
            return output;
        }
        throw invalidResult();
    }

    private void requireGeneration(AgentToolDTOs.GenerationJobResult job, String expectedType,
                                   String expectedJobId) {
        if (job == null || job.jobId() == null || job.jobType() == null || job.status() == null
            || job.stage() == null || (expectedType != null && !expectedType.equals(job.jobType()))
            || (expectedJobId != null && !expectedJobId.equals(job.jobId()))) {
            throw invalidResult();
        }
        positiveLong(job.jobId());
    }

    private void requireProject(AgentToolDTOs.ProjectResult project) {
        if (project == null || project.projectId() == null || project.currentDraftRevision() == null
            || project.projectStatus() == null || project.canvasWidth() <= 0 || project.canvasHeight() <= 0
            || project.frameRate() <= 0 || project.durationMs() <= 0) {
            throw invalidResult();
        }
        positiveLong(project.projectId());
        positiveLong(project.currentDraftRevision());
    }

    private void requireRenderTask(AgentToolDTOs.RenderTaskResult task, String projectId, String revision) {
        if (task == null || task.taskId() == null || task.status() == null || task.stage() == null
            || !Objects.equals(projectId, task.projectId()) || !Objects.equals(revision, task.draftRevision())) {
            throw invalidResult();
        }
        positiveLong(task.taskId());
    }

    private void requireRenderStatus(AgentToolDTOs.RenderStatusResult status, String taskId) {
        if (status == null || !Objects.equals(taskId, status.taskId()) || status.status() == null
            || status.stage() == null || status.projectId() == null || status.draftRevision() == null) {
            throw invalidResult();
        }
        positiveLong(status.taskId());
        positiveLong(status.projectId());
        positiveLong(status.draftRevision());
    }

    private void requireParkableRender(AppPrincipalSnapshotDTO principal, AgentToolDTOs.RenderTaskResult task) {
        if ("pending".equals(task.status()) || "queued".equals(task.status())
            || "running".equals(task.status()) || "success".equals(task.status())) {
            return;
        }
        if (FAILED.equals(task.status()) || CANCELLED.equals(task.status())) {
            AgentToolDTOs.RenderStatusResult status = renderStatus(call(principal, GET_RENDER,
                object("taskId", task.taskId())));
            requireRenderStatus(status, task.taskId());
            throw renderFailure(status);
        }
        throw invalidResult();
    }

    private void requireOutput(AgentToolDTOs.OutputInspectionResult output,
                               AgentToolDTOs.RenderStatusResult render) {
        if (output == null || !Objects.equals(render.taskId(), output.taskId())
            || !Objects.equals(render.resultAssetId(), output.assetId()) || !"ready".equals(output.status())
            || !"video".equals(output.assetType()) || !"timeline_render_output".equals(output.usageOrigin())
            || output.mimeType() == null || !output.mimeType().startsWith("video/") || output.sha256() == null
            || output.sizeBytes() <= 0 || !output.hasVideoStream() || !output.hasAudioStream()
            || output.downloadPath() == null || output.downloadPath().isBlank()) {
            throw invalidResult();
        }
        positiveLong(output.assetId());
    }

    private boolean activeGeneration(AgentToolDTOs.GenerationJobResult job) {
        return "queued".equals(job.status()) || "running".equals(job.status());
    }

    private boolean succeededGeneration(AgentToolDTOs.GenerationJobResult job) {
        return "succeeded".equals(job.status());
    }

    private boolean failedGeneration(AgentToolDTOs.GenerationJobResult job) {
        return "failed".equals(job.status());
    }

    private boolean activeRender(AgentToolDTOs.RenderStatusResult render) {
        return "pending".equals(render.status()) || "queued".equals(render.status())
            || "running".equals(render.status());
    }

    private boolean failedRender(AgentToolDTOs.RenderStatusResult render) {
        return FAILED.equals(render.status());
    }

    private boolean cancelledRender(AgentToolDTOs.RenderStatusResult render) {
        return CANCELLED.equals(render.status());
    }

    private OrchestrationFailure generationFailure(AgentToolDTOs.GenerationJobResult job) {
        if (!failedGeneration(job) || job.errorCode() == null || job.errorCode().isBlank()
            || job.safeMessage() == null || job.safeMessage().isBlank()) {
            return invalidResult();
        }
        return new OrchestrationFailure(FAILED, job.errorCode(), job.safeMessage());
    }

    private OrchestrationFailure renderFailure(AgentToolDTOs.RenderStatusResult render) {
        if (cancelledRender(render)) {
            return new OrchestrationFailure(CANCELLED, "EXTERNAL_TASK_CANCELLED", "渲染任务已取消");
        }
        if (!failedRender(render) || render.errorCode() == null || render.errorCode().isBlank()
            || render.safeMessage() == null || render.safeMessage().isBlank()) {
            return invalidResult();
        }
        return new OrchestrationFailure(FAILED, render.errorCode(), render.safeMessage());
    }

    private String resultSummary(AgentToolDTOs.OutputInspectionResult output) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("schemaVersion", "agent-result-1");
        node.put("taskId", output.taskId());
        node.put("assetId", output.assetId());
        node.put("sha256", output.sha256());
        node.put("sizeBytes", output.sizeBytes());
        node.put("hasVideoStream", output.hasVideoStream());
        node.put("hasAudioStream", output.hasAudioStream());
        try {
            return jsonMapper.writeValueAsString(node);
        } catch (Exception exception) {
            throw invalidResult();
        }
    }

    private AgentRunOrchestrationDTOs.AdvanceResult blockBeforeTools(AppPrincipalSnapshotDTO principal,
                                                                      IAgentRunService.AgentRunView run,
                                                                      List<String> missing) {
        if (QUEUED.equals(run.runStatus())) {
            boolean blocked = runService.blockForInput(principal, new IAgentRunService.BlockForInputCommand(
                run.agentRunId(), run.rowVersion(), run.contractRevision(), "AGENT_INPUT_REQUIRED",
                "Agent 执行需要补充确认输入"));
            if (!blocked) {
                return stateConflict(run);
            }
            return new AgentRunOrchestrationDTOs.AdvanceResult(run.agentRunId(), WAITING_INPUT, "blocked",
                null, null, null, missing, "AGENT_INPUT_REQUIRED", "Agent 执行需要补充确认输入");
        }
        return blocked(run, missing);
    }

    private AgentRunOrchestrationDTOs.AdvanceResult blocked(IAgentRunService.AgentRunView run,
                                                             List<String> missing) {
        return new AgentRunOrchestrationDTOs.AdvanceResult(run.agentRunId(), run.runStatus(), "blocked",
            run.waitingTaskSource(), run.waitingTaskId(), run.candidateAssetId(), missing,
            "AGENT_INPUT_REQUIRED", "Agent 执行需要补充确认输入");
    }

    private AgentRunOrchestrationDTOs.AdvanceResult stop(AppPrincipalSnapshotDTO principal,
                                                          long agentRunId,
                                                          long rowVersion,
                                                          long contractRevision,
                                                          String terminalStatus,
                                                          String errorCode,
                                                          String safeMessage) {
        boolean stopped = runService.stopOwnedRun(principal, new IAgentRunService.StopOwnedRunCommand(
            agentRunId, rowVersion, contractRevision, terminalStatus, errorCode, safeMessage));
        if (!stopped) {
            return stateConflict(agentRunId, rowVersion, contractRevision);
        }
        return new AgentRunOrchestrationDTOs.AdvanceResult(agentRunId, terminalStatus,
            CANCELLED.equals(terminalStatus) ? "cancelled" : "manual_required", null, null, null, List.of(),
            errorCode, safeMessage);
    }

    private AgentRunOrchestrationDTOs.AdvanceResult terminal(IAgentRunService.AgentRunView run) {
        return new AgentRunOrchestrationDTOs.AdvanceResult(run.agentRunId(), run.runStatus(), "terminal",
            null, null, run.candidateAssetId(), List.of(), run.errorCode(), run.errorSummary());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult stateConflict(IAgentRunService.AgentRunView run) {
        return stateConflict(run.agentRunId(), run.rowVersion(), run.contractRevision());
    }

    private AgentRunOrchestrationDTOs.AdvanceResult stateConflict(long agentRunId, long rowVersion,
                                                                   long contractRevision) {
        return new AgentRunOrchestrationDTOs.AdvanceResult(agentRunId, null, "state_conflict", null, null,
            null, List.of(), "AGENT_RUN_STATE_CONFLICT", "AgentRun 状态已变化，请重新读取");
    }

    private FailureFact serviceFailure(ServiceException exception) {
        if (Objects.equals(exception.getCode(), 46703)) {
            return new FailureFact("AGENT_PERMISSION_DENIED", "Agent 工具权限不足");
        }
        if (Objects.equals(exception.getCode(), 46603)) {
            return new FailureFact("AGENT_RECONFIRM_REQUIRED", "项目版本已变化，请重新确认");
        }
        if (Objects.equals(exception.getCode(), 46704)) {
            return new FailureFact("AGENT_TOOL_RESULT_INVALID", "Agent 工具结果未通过安全校验");
        }
        return new FailureFact("AGENT_TOOL_FAILED", "Agent 工具执行失败，请转人工处理");
    }

    private boolean deadlineReached(IAgentRunService.AgentRunView run, ExecutionPolicy policy) {
        Instant startedAt = run.startedAt() == null ? run.stateChangedAt() : run.startedAt();
        if (startedAt == null) {
            return true;
        }
        try {
            return !clock.instant().isBefore(startedAt.plusSeconds(policy.maxRunSeconds()));
        } catch (DateTimeException | ArithmeticException exception) {
            return true;
        }
    }

    private Instant resumeAfter(ExecutionPolicy policy) {
        return clock.instant().plusSeconds(policy.pollIntervalSeconds());
    }

    private long leaseSeconds(ExecutionPolicy policy) {
        return Math.min(300, Math.max(30, (long) policy.pollIntervalSeconds() * 2));
    }

    private String key(long runId, String step, int attempt) {
        return "agent-run:" + runId + ":" + step + ":" + attempt;
    }

    private long positiveLong(String value) {
        if (value == null || !POSITIVE_ID.matcher(value).matches()) {
            throw invalidResult();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw invalidResult();
        }
    }

    private boolean expected(IAgentRunService.AgentRunView run, long rowVersion, long contractRevision) {
        return run.rowVersion() == rowVersion && run.contractRevision() == contractRevision;
    }

    private boolean terminal(String status) {
        return COMPLETED.equals(status) || FAILED.equals(status) || CANCELLED.equals(status);
    }

    private void requireAdvanceCommand(AgentRunOrchestrationDTOs.AdvanceCommand command) {
        if (command == null || command.agentRunId() <= 0 || command.expectedRowVersion() < 0
            || command.expectedContractRevision() <= 0 || command.workerId() == null
            || !WORKER_ID.matcher(command.workerId()).matches()) {
            throw new IllegalArgumentException("advance command is invalid");
        }
    }

    private int skippedThrough(StartAt startAt) {
        if (startAt == null) {
            return 0;
        }
        return switch (startAt) {
            case NEW -> 0;
            case VOICE_JOB -> 1;
            case VIDEO_JOB -> 4;
            case PROJECT -> 6;
            case RENDER_TASK -> 7;
        };
    }

    private int requiredProviderSubmissions(StartAt startAt) {
        if (startAt == null) {
            return 0;
        }
        return switch (startAt) {
            case NEW -> 2;
            case VOICE_JOB -> 1;
            case VIDEO_JOB, PROJECT, RENDER_TASK -> 0;
        };
    }

    private void addMissing(List<String> issues, String issue) {
        if (!issues.contains(issue)) {
            issues.add(issue);
        }
    }

    private OrchestrationFailure invalidResult() {
        return new OrchestrationFailure(FAILED, "AGENT_TOOL_RESULT_INVALID",
            "Agent 工具结果未通过安全校验");
    }

    private enum StartAt {
        NEW("new"),
        VOICE_JOB("voice_job"),
        VIDEO_JOB("video_job"),
        PROJECT("project"),
        RENDER_TASK("render_task");

        private final String value;

        StartAt(String value) {
            this.value = value;
        }

        private static StartAt from(String value) {
            for (StartAt startAt : values()) {
                if (startAt.value.equals(value)) {
                    return startAt;
                }
            }
            return null;
        }
    }

    private record GoldenInput(
        StartAt startAt,
        String scriptText,
        String referenceVoiceId,
        String portraitId,
        String projectTitle,
        String voiceJobId,
        String videoJobId,
        String projectId,
        String expectedRevision,
        String taskId
    ) {
    }

    private record ExecutionPolicy(
        int maxRunSeconds,
        int maxResumeAttempts,
        int maxProviderSubmissions,
        int maxRenderRetries,
        int pollIntervalSeconds
    ) {
    }

    private record ParsedContract(GoldenInput input, ExecutionPolicy policy, List<String> issues) {
    }

    private record StepDefinition(int sequence, String stepType, String toolName) {
    }

    private record FailureFact(String code, String safeMessage) {
    }

    private static final class OrchestrationFailure extends RuntimeException {
        private final String terminalStatus;
        private final String code;
        private final String safeMessage;

        private OrchestrationFailure(String terminalStatus, String code, String safeMessage) {
            super(code);
            this.terminalStatus = terminalStatus;
            this.code = code;
            this.safeMessage = safeMessage;
        }
    }
}
