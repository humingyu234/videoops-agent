package org.dromara.aivideo.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.aivideo.agent.domain.AcceptanceProfileVersion;
import org.dromara.aivideo.agent.domain.AgentRun;
import org.dromara.aivideo.agent.domain.DeliveryBriefVersion;
import org.dromara.aivideo.agent.enums.AgentRunStatus;
import org.dromara.aivideo.agent.mapper.AcceptanceProfileVersionMapper;
import org.dromara.aivideo.agent.mapper.AgentRunMapper;
import org.dromara.aivideo.agent.mapper.DeliveryBriefVersionMapper;
import org.dromara.aivideo.agent.service.IAgentRunService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Minimal persistence owner for T2 versioned contracts and recoverable AgentRun fences. */
@Service
public class AgentRunServiceImpl implements IAgentRunService {

    static final String DELIVERY_BRIEF_SCHEMA = "delivery-brief-1";
    static final String DELIVERY_TYPE = "image_to_digital_human_video";
    static final String ACCEPTANCE_PROFILE_SCHEMA = "acceptance-profile-1";
    static final String ACCEPTANCE_POLICY = "acceptance-policy-1";
    static final String AGENT_RUN_SCHEMA = "agent-run-1";

    private static final String APP_USER = "app_user";
    private static final int MAX_JSON_BYTES = 65_536;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;
    private static final long MAX_LEASE_SECONDS = 300;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern WORKER_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Set<String> TASK_SOURCES = Set.of("digital_human_generation", "ai_task");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeliveryBriefVersionMapper briefMapper;
    private final AcceptanceProfileVersionMapper profileMapper;
    private final AgentRunMapper runMapper;
    private final JsonMapper jsonMapper;

    public AgentRunServiceImpl(DeliveryBriefVersionMapper briefMapper,
                               AcceptanceProfileVersionMapper profileMapper,
                               AgentRunMapper runMapper,
                               JsonMapper jsonMapper) {
        this.briefMapper = Objects.requireNonNull(briefMapper, "briefMapper");
        this.profileMapper = Objects.requireNonNull(profileMapper, "profileMapper");
        this.runMapper = Objects.requireNonNull(runMapper, "runMapper");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper");
    }

    @Override
    public DeliveryBriefVersionView appendDeliveryBrief(AppPrincipalSnapshotDTO principal,
                                                         AppendDeliveryBriefCommand command) {
        long owner = owner(principal);
        if (command == null) {
            throw invalid("交付目标请求不能为空");
        }
        String key = idempotencyKey(command.idempotencyKey());
        String canonicalJson = canonicalObject(command.briefJson(), "交付目标");
        String contentHash = sha256(canonicalJson);
        String requestDigest = sha256("brief\n" + DELIVERY_BRIEF_SCHEMA + "\n" + DELIVERY_TYPE + "\n"
            + nullable(command.briefId()) + "\n"
            + nullable(command.parentVersionId()) + "\n" + contentHash);

        DeliveryBriefVersion existing = findBriefByIdempotency(owner, key);
        if (existing != null) {
            return briefReplay(existing, requestDigest);
        }
        VersionAppend append = briefAppend(owner, command.briefId(), command.parentVersionId());

        DeliveryBriefVersion version = new DeliveryBriefVersion();
        version.setDeliveryBriefVersionId(IdWorker.getId());
        version.setBriefId(append.stableId());
        version.setOwnerUserId(owner);
        version.setVersionNo(append.versionNo());
        version.setParentVersionId(append.parentVersionId());
        version.setSchemaVersion(DELIVERY_BRIEF_SCHEMA);
        version.setDeliveryType(DELIVERY_TYPE);
        version.setBriefJson(canonicalJson);
        version.setBriefHash(contentHash);
        version.setIdempotencyKey(key);
        version.setRequestDigest(requestDigest);
        auditCreate(version, owner);
        try {
            return inAudit(owner, () -> {
                if (briefMapper.insert(version) != 1) {
                    throw invalid("交付目标版本创建失败");
                }
                return toView(version);
            });
        } catch (DuplicateKeyException exception) {
            DeliveryBriefVersion winner = findBriefByIdempotency(owner, key);
            if (winner != null) {
                return briefReplay(winner, requestDigest);
            }
            throw conflict("交付目标版本已变化");
        }
    }

    @Override
    public AcceptanceProfileVersionView appendAcceptanceProfile(AppPrincipalSnapshotDTO principal,
                                                                 AppendAcceptanceProfileCommand command) {
        long owner = owner(principal);
        if (command == null || command.deliveryBriefVersionId() <= 0) {
            throw invalid("验收偏好请求无效");
        }
        requireBrief(owner, command.deliveryBriefVersionId());
        String key = idempotencyKey(command.idempotencyKey());
        String canonicalJson = canonicalObject(command.profileJson(), "验收偏好");
        String contentHash = sha256(canonicalJson);
        String requestDigest = sha256("profile\n" + ACCEPTANCE_PROFILE_SCHEMA + "\n" + ACCEPTANCE_POLICY + "\n"
            + nullable(command.acceptanceProfileId()) + "\n"
            + nullable(command.parentVersionId()) + "\n" + command.deliveryBriefVersionId() + "\n" + contentHash);

        AcceptanceProfileVersion existing = findProfileByIdempotency(owner, key);
        if (existing != null) {
            return profileReplay(existing, requestDigest);
        }
        VersionAppend append = profileAppend(owner, command.acceptanceProfileId(), command.parentVersionId());

        AcceptanceProfileVersion version = new AcceptanceProfileVersion();
        version.setAcceptanceProfileVersionId(IdWorker.getId());
        version.setAcceptanceProfileId(append.stableId());
        version.setOwnerUserId(owner);
        version.setDeliveryBriefVersionId(command.deliveryBriefVersionId());
        version.setVersionNo(append.versionNo());
        version.setParentVersionId(append.parentVersionId());
        version.setSchemaVersion(ACCEPTANCE_PROFILE_SCHEMA);
        version.setPolicyVersion(ACCEPTANCE_POLICY);
        version.setProfileJson(canonicalJson);
        version.setProfileHash(contentHash);
        version.setIdempotencyKey(key);
        version.setRequestDigest(requestDigest);
        auditCreate(version, owner);
        try {
            return inAudit(owner, () -> {
                if (profileMapper.insert(version) != 1) {
                    throw invalid("验收偏好版本创建失败");
                }
                return toView(version);
            });
        } catch (DuplicateKeyException exception) {
            AcceptanceProfileVersion winner = findProfileByIdempotency(owner, key);
            if (winner != null) {
                return profileReplay(winner, requestDigest);
            }
            throw conflict("验收偏好版本已变化");
        }
    }

    @Override
    public AgentRunView createRun(AppPrincipalSnapshotDTO principal, CreateAgentRunCommand command) {
        long owner = owner(principal);
        if (command == null || command.deliveryBriefVersionId() <= 0
            || command.acceptanceProfileVersionId() <= 0) {
            throw invalid("AgentRun 请求无效");
        }
        DeliveryBriefVersion brief = requireBrief(owner, command.deliveryBriefVersionId());
        AcceptanceProfileVersion profile = requireProfile(owner, command.acceptanceProfileVersionId());
        if (!Objects.equals(profile.getDeliveryBriefVersionId(), brief.getDeliveryBriefVersionId())) {
            throw notFound("版本化交付契约不存在");
        }
        String key = idempotencyKey(command.idempotencyKey());
        String requestDigest = sha256("run\n" + AGENT_RUN_SCHEMA + "\n"
            + brief.getDeliveryBriefVersionId() + "\n"
            + profile.getAcceptanceProfileVersionId());
        AgentRun existing = findRunByIdempotency(owner, key);
        if (existing != null) {
            return runReplay(existing, requestDigest);
        }

        LocalDateTime databaseNow = databaseNow();
        AgentRun run = new AgentRun();
        run.setAgentRunId(IdWorker.getId());
        run.setOwnerUserId(owner);
        run.setDeliveryBriefVersionId(brief.getDeliveryBriefVersionId());
        run.setAcceptanceProfileVersionId(profile.getAcceptanceProfileVersionId());
        run.setContractRevision(1L);
        run.setSchemaVersion(AGENT_RUN_SCHEMA);
        run.setIdempotencyKey(key);
        run.setRequestDigest(requestDigest);
        run.setRunStatus(AgentRunStatus.QUEUED.getValue());
        run.setRowVersion(0L);
        run.setLeaseGeneration(0L);
        run.setRetryCount(0L);
        run.setStateChangedAt(databaseNow);
        auditCreate(run, owner);
        try {
            return inAudit(owner, () -> {
                if (runMapper.insert(run) != 1) {
                    throw invalid("AgentRun 创建失败");
                }
                return toView(run);
            });
        } catch (DuplicateKeyException exception) {
            AgentRun winner = findRunByIdempotency(owner, key);
            if (winner != null) {
                return runReplay(winner, requestDigest);
            }
            throw conflict("AgentRun 创建冲突");
        }
    }

    @Override
    public AgentRunView getOwnedRun(AppPrincipalSnapshotDTO principal, long agentRunId) {
        return toView(requireRun(owner(principal), agentRunId));
    }

    @Override
    public ExecutionSnapshot getOwnedExecutionSnapshot(AppPrincipalSnapshotDTO principal, long agentRunId) {
        long owner = owner(principal);
        AgentRun run = requireRun(owner, agentRunId);
        DeliveryBriefVersion brief = requireBrief(owner, run.getDeliveryBriefVersionId());
        AcceptanceProfileVersion profile = requireProfile(owner, run.getAcceptanceProfileVersionId());
        if (!Objects.equals(profile.getDeliveryBriefVersionId(), brief.getDeliveryBriefVersionId())) {
            throw notFound("版本化交付契约不存在");
        }
        return new ExecutionSnapshot(toView(run), brief.getBriefJson(), brief.getBriefHash(),
            profile.getProfileJson(), profile.getProfileHash());
    }

    @Override
    public boolean blockForInput(AppPrincipalSnapshotDTO principal, BlockForInputCommand command) {
        long owner = owner(principal);
        if (command == null || command.agentRunId() <= 0 || command.expectedRowVersion() < 0
            || command.expectedContractRevision() <= 0) {
            throw invalid("AgentRun 输入阻塞请求无效");
        }
        String errorCode = requiredErrorCode(command.errorCode());
        String errorSummary = requiredErrorSummary(command.errorSummary());
        LocalDateTime now = databaseNow();
        return inAudit(owner, () -> runMapper.blockForInput(command.agentRunId(), owner,
            command.expectedContractRevision(), command.expectedRowVersion(), errorCode, errorSummary, now)) == 1;
    }

    @Override
    public AgentRunLease claim(AppPrincipalSnapshotDTO principal, ClaimAgentRunCommand command) {
        long owner = owner(principal);
        if (command == null || command.agentRunId() <= 0 || command.expectedRowVersion() < 0
            || command.expectedContractRevision() <= 0 || command.leaseSeconds() <= 0
            || command.leaseSeconds() > MAX_LEASE_SECONDS
            || !WORKER_ID.matcher(nullable(command.workerId())).matches()) {
            throw invalid("AgentRun 领取请求无效");
        }
        AgentRun current = findRun(owner, command.agentRunId());
        if (current == null || !Objects.equals(current.getRowVersion(), command.expectedRowVersion())
            || !Objects.equals(current.getContractRevision(), command.expectedContractRevision())) {
            return null;
        }
        AgentRunStatus status = status(current);
        if (status.isTerminal()) {
            return null;
        }

        LocalDateTime now = databaseNow();
        LocalDateTime expiresAt = now.plusSeconds(command.leaseSeconds());
        String token = newLeaseToken();
        String tokenDigest = sha256(token);
        long generation = number(current.getLeaseGeneration());
        int updated;
        if (status == AgentRunStatus.WAITING_EXTERNAL_TASK) {
            if (!validWaitingIdentity(current)) {
                throw invalid("AgentRun 等待任务状态损坏");
            }
            updated = inAudit(owner, () -> runMapper.recoverWaitingLease(current.getAgentRunId(), owner,
                command.expectedContractRevision(), command.expectedRowVersion(), generation,
                current.getWaitingTaskSource(), current.getWaitingTaskId(), command.workerId(), tokenDigest,
                now, expiresAt));
        } else if (status == AgentRunStatus.QUEUED || status == AgentRunStatus.RUNNING) {
            updated = inAudit(owner, () -> runMapper.claimLease(current.getAgentRunId(), owner,
                command.expectedContractRevision(), command.expectedRowVersion(), generation,
                command.workerId(), tokenDigest, now, expiresAt));
        } else {
            return null;
        }
        if (updated != 1) {
            return null;
        }
        return new AgentRunLease(current.getAgentRunId(), command.expectedRowVersion() + 1,
            command.expectedContractRevision(), generation + 1, token, instant(expiresAt),
            current.getWaitingTaskSource(), current.getWaitingTaskId());
    }

    @Override
    public WaitingReceipt waitForExternalTask(AppPrincipalSnapshotDTO principal,
                                              WaitForExternalTaskCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease()) || !TASK_SOURCES.contains(command.taskSource())
            || command.taskId() <= 0 || command.resumeAfter() == null) {
            throw invalid("外部任务等待请求无效");
        }
        LocalDateTime now = databaseNow();
        LocalDateTime resumeAfter = futureResumeAfter(command.resumeAfter(), now);
        LeaseProof lease = command.lease();
        int updated = inAudit(owner, () -> runMapper.waitForExternalTask(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            command.taskSource(), command.taskId(), resumeAfter, now));
        if (updated != 1) {
            return null;
        }
        LeaseProof waitingLease = new LeaseProof(lease.agentRunId(), lease.rowVersion() + 1,
            lease.contractRevision(), lease.leaseGeneration(), lease.leaseToken());
        return new WaitingReceipt(waitingLease, command.taskSource(), command.taskId(), command.resumeAfter());
    }

    @Override
    public WaitingReceipt deferExternalTask(AppPrincipalSnapshotDTO principal,
                                             DeferExternalTaskCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease()) || !TASK_SOURCES.contains(command.taskSource())
            || command.taskId() <= 0 || command.resumeAfter() == null) {
            throw invalid("外部任务延期请求无效");
        }
        LocalDateTime now = databaseNow();
        LocalDateTime resumeAfter = futureResumeAfter(command.resumeAfter(), now);
        LeaseProof lease = command.lease();
        int updated = inAudit(owner, () -> runMapper.deferExternalTask(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            command.taskSource(), command.taskId(), resumeAfter, now));
        return waitingReceipt(updated, lease, command.taskSource(), command.taskId(), command.resumeAfter());
    }

    @Override
    public WaitingReceipt advanceExternalTask(AppPrincipalSnapshotDTO principal,
                                               AdvanceExternalTaskCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease())
            || !"digital_human_generation".equals(command.completedTaskSource())
            || !("digital_human_generation".equals(command.nextTaskSource())
            || "ai_task".equals(command.nextTaskSource()))
            || command.completedTaskId() <= 0 || command.nextTaskId() <= 0
            || command.completedTaskId() == command.nextTaskId() || command.resumeAfter() == null) {
            throw invalid("外部任务推进请求无效");
        }
        LocalDateTime now = databaseNow();
        LocalDateTime resumeAfter = futureResumeAfter(command.resumeAfter(), now);
        LeaseProof lease = command.lease();
        int updated = inAudit(owner, () -> runMapper.advanceExternalTask(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            command.completedTaskSource(), command.completedTaskId(), command.nextTaskSource(),
            command.nextTaskId(), resumeAfter, now));
        return waitingReceipt(updated, lease, command.nextTaskSource(), command.nextTaskId(),
            command.resumeAfter());
    }

    @Override
    public WaitingReceipt retryExternalTask(AppPrincipalSnapshotDTO principal,
                                             RetryExternalTaskCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease()) || command.failedTaskId() <= 0
            || command.retryTaskId() <= 0 || command.failedTaskId() == command.retryTaskId()
            || command.resumeAfter() == null) {
            throw invalid("渲染任务重试请求无效");
        }
        LocalDateTime now = databaseNow();
        LocalDateTime resumeAfter = futureResumeAfter(command.resumeAfter(), now);
        LeaseProof lease = command.lease();
        int updated = inAudit(owner, () -> runMapper.retryExternalTask(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            command.failedTaskId(), command.retryTaskId(), resumeAfter, now));
        return waitingReceipt(updated, lease, "ai_task", command.retryTaskId(), command.resumeAfter());
    }

    @Override
    public boolean completeExternalTask(AppPrincipalSnapshotDTO principal,
                                        CompleteExternalTaskCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease()) || !TASK_SOURCES.contains(command.taskSource())
            || command.taskId() <= 0 || command.candidateAssetId() <= 0) {
            throw invalid("外部任务结果无效");
        }
        String resultJson = canonicalObject(command.resultSummaryJson(), "AgentRun 结果");
        String resultDigest = sha256(resultJson);
        LocalDateTime now = databaseNow();
        LeaseProof lease = command.lease();
        return inAudit(owner, () -> runMapper.completeExternalTask(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            command.taskSource(), command.taskId(), command.candidateAssetId(), resultJson, resultDigest, now)) == 1;
    }

    @Override
    public boolean finishLease(AppPrincipalSnapshotDTO principal, FinishAgentRunCommand command) {
        long owner = owner(principal);
        if (command == null || !validLease(command.lease())) {
            throw invalid("AgentRun 结束请求无效");
        }
        AgentRunStatus terminal;
        try {
            terminal = AgentRunStatus.fromValue(command.terminalStatus());
        } catch (IllegalArgumentException exception) {
            throw invalid("AgentRun 终态无效");
        }
        if (!terminal.isTerminal()) {
            throw invalid("AgentRun 终态无效");
        }

        TerminalPayload payload = terminalPayload(terminal, command);
        LocalDateTime now = databaseNow();
        LeaseProof lease = command.lease();
        return inAudit(owner, () -> runMapper.finishLease(lease.agentRunId(), owner,
            lease.contractRevision(), lease.rowVersion(), lease.leaseGeneration(), sha256(lease.leaseToken()),
            terminal.getValue(), payload.candidateAssetId(), payload.resultJson(), payload.resultDigest(),
            payload.errorCode(), payload.errorSummary(), now)) == 1;
    }

    @Override
    public boolean stopOwnedRun(AppPrincipalSnapshotDTO principal, StopOwnedRunCommand command) {
        long owner = owner(principal);
        if (command == null || command.agentRunId() <= 0 || command.expectedRowVersion() < 0
            || command.expectedContractRevision() <= 0) {
            throw invalid("AgentRun 停止请求无效");
        }
        AgentRunStatus terminal;
        try {
            terminal = AgentRunStatus.fromValue(command.terminalStatus());
        } catch (IllegalArgumentException exception) {
            throw invalid("AgentRun 停止终态无效");
        }
        if (terminal != AgentRunStatus.FAILED && terminal != AgentRunStatus.CANCELLED) {
            throw invalid("AgentRun 停止终态无效");
        }
        String errorCode = requiredErrorCode(command.errorCode());
        String errorSummary = requiredErrorSummary(command.errorSummary());
        LocalDateTime now = databaseNow();
        return inAudit(owner, () -> runMapper.stopOwnedRun(command.agentRunId(), owner,
            command.expectedContractRevision(), command.expectedRowVersion(), terminal.getValue(),
            errorCode, errorSummary, now)) == 1;
    }

    private VersionAppend briefAppend(long owner, Long stableId, Long parentVersionId) {
        if (stableId == null && parentVersionId == null) {
            return new VersionAppend(IdWorker.getId(), 1L, null);
        }
        requirePositivePair(stableId, parentVersionId, "交付目标版本链无效");
        DeliveryBriefVersion parent = briefMapper.selectOne(new LambdaQueryWrapper<DeliveryBriefVersion>()
            .eq(DeliveryBriefVersion::getDeliveryBriefVersionId, parentVersionId)
            .eq(DeliveryBriefVersion::getBriefId, stableId)
            .eq(DeliveryBriefVersion::getOwnerUserId, owner));
        DeliveryBriefVersion latest = latestBrief(owner, stableId);
        if (parent == null || latest == null
            || !Objects.equals(latest.getDeliveryBriefVersionId(), parent.getDeliveryBriefVersionId())) {
            throw conflict("交付目标父版本不是当前版本");
        }
        return new VersionAppend(stableId, parent.getVersionNo() + 1, parentVersionId);
    }

    private VersionAppend profileAppend(long owner, Long stableId, Long parentVersionId) {
        if (stableId == null && parentVersionId == null) {
            return new VersionAppend(IdWorker.getId(), 1L, null);
        }
        requirePositivePair(stableId, parentVersionId, "验收偏好版本链无效");
        AcceptanceProfileVersion parent = profileMapper.selectOne(
            new LambdaQueryWrapper<AcceptanceProfileVersion>()
                .eq(AcceptanceProfileVersion::getAcceptanceProfileVersionId, parentVersionId)
                .eq(AcceptanceProfileVersion::getAcceptanceProfileId, stableId)
                .eq(AcceptanceProfileVersion::getOwnerUserId, owner));
        AcceptanceProfileVersion latest = latestProfile(owner, stableId);
        if (parent == null || latest == null
            || !Objects.equals(latest.getAcceptanceProfileVersionId(), parent.getAcceptanceProfileVersionId())) {
            throw conflict("验收偏好父版本不是当前版本");
        }
        return new VersionAppend(stableId, parent.getVersionNo() + 1, parentVersionId);
    }

    private DeliveryBriefVersion latestBrief(long owner, long briefId) {
        return briefMapper.selectOne(new LambdaQueryWrapper<DeliveryBriefVersion>()
            .eq(DeliveryBriefVersion::getOwnerUserId, owner)
            .eq(DeliveryBriefVersion::getBriefId, briefId)
            .orderByDesc(DeliveryBriefVersion::getVersionNo)
            .last("LIMIT 1"));
    }

    private AcceptanceProfileVersion latestProfile(long owner, long profileId) {
        return profileMapper.selectOne(new LambdaQueryWrapper<AcceptanceProfileVersion>()
            .eq(AcceptanceProfileVersion::getOwnerUserId, owner)
            .eq(AcceptanceProfileVersion::getAcceptanceProfileId, profileId)
            .orderByDesc(AcceptanceProfileVersion::getVersionNo)
            .last("LIMIT 1"));
    }

    private DeliveryBriefVersion requireBrief(long owner, long versionId) {
        DeliveryBriefVersion version = briefMapper.selectOne(new LambdaQueryWrapper<DeliveryBriefVersion>()
            .eq(DeliveryBriefVersion::getDeliveryBriefVersionId, versionId)
            .eq(DeliveryBriefVersion::getOwnerUserId, owner));
        if (version == null) {
            throw notFound("交付目标版本不存在");
        }
        return version;
    }

    private AcceptanceProfileVersion requireProfile(long owner, long versionId) {
        AcceptanceProfileVersion version = profileMapper.selectOne(new LambdaQueryWrapper<AcceptanceProfileVersion>()
            .eq(AcceptanceProfileVersion::getAcceptanceProfileVersionId, versionId)
            .eq(AcceptanceProfileVersion::getOwnerUserId, owner));
        if (version == null) {
            throw notFound("验收偏好版本不存在");
        }
        return version;
    }

    private DeliveryBriefVersion findBriefByIdempotency(long owner, String key) {
        return briefMapper.selectOne(new LambdaQueryWrapper<DeliveryBriefVersion>()
            .eq(DeliveryBriefVersion::getOwnerUserId, owner)
            .eq(DeliveryBriefVersion::getIdempotencyKey, key));
    }

    private AcceptanceProfileVersion findProfileByIdempotency(long owner, String key) {
        return profileMapper.selectOne(new LambdaQueryWrapper<AcceptanceProfileVersion>()
            .eq(AcceptanceProfileVersion::getOwnerUserId, owner)
            .eq(AcceptanceProfileVersion::getIdempotencyKey, key));
    }

    private AgentRun findRunByIdempotency(long owner, String key) {
        return runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
            .eq(AgentRun::getOwnerUserId, owner)
            .eq(AgentRun::getIdempotencyKey, key));
    }

    private AgentRun findRun(long owner, long runId) {
        if (runId <= 0) {
            return null;
        }
        return runMapper.selectOne(new LambdaQueryWrapper<AgentRun>()
            .eq(AgentRun::getAgentRunId, runId)
            .eq(AgentRun::getOwnerUserId, owner));
    }

    private AgentRun requireRun(long owner, long runId) {
        AgentRun run = findRun(owner, runId);
        if (run == null) {
            throw notFound("AgentRun 不存在");
        }
        return run;
    }

    private DeliveryBriefVersionView briefReplay(DeliveryBriefVersion version, String digest) {
        if (!Objects.equals(version.getRequestDigest(), digest)) {
            throw conflict("幂等键已用于不同的交付目标请求");
        }
        return toView(version);
    }

    private AcceptanceProfileVersionView profileReplay(AcceptanceProfileVersion version, String digest) {
        if (!Objects.equals(version.getRequestDigest(), digest)) {
            throw conflict("幂等键已用于不同的验收偏好请求");
        }
        return toView(version);
    }

    private AgentRunView runReplay(AgentRun run, String digest) {
        if (!Objects.equals(run.getRequestDigest(), digest)) {
            throw conflict("幂等键已用于不同的 AgentRun 请求");
        }
        return toView(run);
    }

    private TerminalPayload terminalPayload(AgentRunStatus status, FinishAgentRunCommand command) {
        if (status == AgentRunStatus.COMPLETED) {
            if (command.candidateAssetId() == null || command.candidateAssetId() <= 0
                || command.errorCode() != null || command.errorSummary() != null) {
                throw invalid("AgentRun 成功结果无效");
            }
            String resultJson = canonicalObject(command.resultSummaryJson(), "AgentRun 结果");
            return new TerminalPayload(command.candidateAssetId(), resultJson, sha256(resultJson), null, null);
        }
        if (command.candidateAssetId() != null || command.resultSummaryJson() != null) {
            throw invalid("AgentRun 失败结果不得包含候选资产");
        }
        if (status == AgentRunStatus.FAILED) {
            String code = requiredErrorCode(command.errorCode());
            String summary = requiredErrorSummary(command.errorSummary());
            return new TerminalPayload(null, null, null, code, summary);
        }
        return new TerminalPayload(null, null, null, optionalErrorCode(command.errorCode()),
            optionalErrorSummary(command.errorSummary()));
    }

    private String canonicalObject(String json, String label) {
        if (json == null || json.isBlank()) {
            throw invalid(label + " JSON 不能为空");
        }
        Deque<Set<String>> objectFields = new ArrayDeque<>();
        try (JsonParser parser = jsonMapper.tokenStreamFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    objectFields.push(new HashSet<>());
                } else if (parser.currentToken() == JsonToken.PROPERTY_NAME) {
                    if (objectFields.isEmpty() || !objectFields.peek().add(parser.getText())) {
                        throw invalid(label + " JSON 包含重复属性");
                    }
                } else if (parser.currentToken() == JsonToken.END_OBJECT) {
                    objectFields.pop();
                }
            }
            JsonNode root = jsonMapper.readTree(json);
            if (root == null || !root.isObject() || root.isEmpty()) {
                throw invalid(label + " JSON 必须是非空对象");
            }
            String canonical = canonicalize(root);
            if (canonical.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_BYTES) {
                throw invalid(label + " JSON 超过 64KiB");
            }
            return canonical;
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid(label + " JSON 格式无效");
        }
    }

    private String canonicalize(JsonNode node) {
        if (node.isObject()) {
            return node.properties().stream()
                .sorted((left, right) -> left.getKey().compareTo(right.getKey()))
                .map(entry -> quote(entry.getKey()) + ":" + canonicalize(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        }
        if (node.isArray()) {
            return node.valueStream().map(this::canonicalize).collect(Collectors.joining(",", "[", "]"));
        }
        if (node.isTextual()) {
            return quote(node.textValue());
        }
        if (node.isBoolean()) {
            return Boolean.toString(node.booleanValue());
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isNumber()) {
            var decimal = node.decimalValue().stripTrailingZeros();
            if ((long) decimal.precision() + Math.abs((long) decimal.scale()) > MAX_JSON_BYTES) {
                throw invalid("JSON 数字范围过大");
            }
            return decimal.signum() == 0 ? "0" : decimal.toPlainString();
        }
        throw invalid("JSON 包含不支持的值");
    }

    private String quote(String value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw invalid("JSON 字符串无效");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String newLeaseToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private DeliveryBriefVersionView toView(DeliveryBriefVersion version) {
        return new DeliveryBriefVersionView(version.getDeliveryBriefVersionId(), version.getBriefId(),
            version.getVersionNo(), version.getParentVersionId(), version.getSchemaVersion(),
            version.getDeliveryType(), version.getBriefHash());
    }

    private AcceptanceProfileVersionView toView(AcceptanceProfileVersion version) {
        return new AcceptanceProfileVersionView(version.getAcceptanceProfileVersionId(),
            version.getAcceptanceProfileId(), version.getDeliveryBriefVersionId(), version.getVersionNo(),
            version.getParentVersionId(), version.getSchemaVersion(), version.getPolicyVersion(),
            version.getProfileHash());
    }

    private AgentRunView toView(AgentRun run) {
        return new AgentRunView(run.getAgentRunId(), run.getDeliveryBriefVersionId(),
            run.getAcceptanceProfileVersionId(), number(run.getContractRevision()), run.getRunStatus(),
            number(run.getRowVersion()), number(run.getLeaseGeneration()), run.getWaitingTaskSource(),
            run.getWaitingTaskId(), run.getCandidateAssetId(), instant(run.getStateChangedAt()),
            number(run.getRetryCount()), instant(run.getStartedAt()), instant(run.getResumeAfter()),
            instant(run.getFinishedAt()), run.getErrorCode(), run.getErrorSummary());
    }

    private void auditCreate(DeliveryBriefVersion version, long owner) {
        version.setActorType(APP_USER);
        version.setActorId(owner);
        version.setCreateBy(owner);
        version.setUpdateBy(owner);
    }

    private void auditCreate(AcceptanceProfileVersion version, long owner) {
        version.setActorType(APP_USER);
        version.setActorId(owner);
        version.setCreateBy(owner);
        version.setUpdateBy(owner);
    }

    private void auditCreate(AgentRun run, long owner) {
        run.setActorType(APP_USER);
        run.setActorId(owner);
        run.setCreateBy(owner);
        run.setUpdateBy(owner);
    }

    private <T> T inAudit(long owner, Supplier<T> action) {
        try (AuditFillContext.Scope ignored = AuditFillContext.open(owner)) {
            return action.get();
        }
    }

    private long owner(AppPrincipalSnapshotDTO principal) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0) {
            throw notFound("当前用户不存在");
        }
        return principal.appUserId();
    }

    private AgentRunStatus status(AgentRun run) {
        try {
            return AgentRunStatus.fromValue(run.getRunStatus());
        } catch (IllegalArgumentException exception) {
            throw invalid("AgentRun 状态损坏");
        }
    }

    private boolean validLease(LeaseProof lease) {
        return lease != null && lease.agentRunId() > 0 && lease.rowVersion() >= 0
            && lease.contractRevision() > 0 && lease.leaseGeneration() > 0
            && lease.leaseToken() != null && !lease.leaseToken().isBlank();
    }

    private boolean validWaitingIdentity(AgentRun run) {
        return TASK_SOURCES.contains(run.getWaitingTaskSource()) && run.getWaitingTaskId() != null
            && run.getWaitingTaskId() > 0
            && Objects.equals(run.getWaitingContractRevision(), run.getContractRevision());
    }

    private LocalDateTime futureResumeAfter(Instant value, LocalDateTime databaseNow) {
        LocalDateTime resumeAfter = local(value);
        if (!resumeAfter.isAfter(databaseNow)) {
            throw invalid("外部任务恢复时间必须晚于当前时间");
        }
        return resumeAfter;
    }

    private WaitingReceipt waitingReceipt(int updated, LeaseProof lease, String taskSource, long taskId,
                                           Instant resumeAfter) {
        if (updated != 1) {
            return null;
        }
        LeaseProof nextLease = new LeaseProof(lease.agentRunId(), lease.rowVersion() + 1,
            lease.contractRevision(), lease.leaseGeneration(), lease.leaseToken());
        return new WaitingReceipt(nextLease, taskSource, taskId, resumeAfter);
    }

    private String idempotencyKey(String value) {
        if (!IDEMPOTENCY_KEY.matcher(nullable(value)).matches()) {
            throw invalid("幂等键无效");
        }
        return value;
    }

    private void requirePositivePair(Long stableId, Long parentVersionId, String message) {
        if (stableId == null || stableId <= 0 || parentVersionId == null || parentVersionId <= 0) {
            throw invalid(message);
        }
    }

    private String requiredErrorCode(String value) {
        if (!ERROR_CODE.matcher(nullable(value)).matches()) {
            throw invalid("AgentRun 错误码无效");
        }
        return value;
    }

    private String optionalErrorCode(String value) {
        return value == null ? null : requiredErrorCode(value);
    }

    private String requiredErrorSummary(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ERROR_SUMMARY_LENGTH) {
            throw invalid("AgentRun 错误摘要无效");
        }
        return value;
    }

    private String optionalErrorSummary(String value) {
        return value == null ? null : requiredErrorSummary(value);
    }

    private LocalDateTime databaseNow() {
        LocalDateTime value = runMapper.selectDatabaseNow();
        if (value == null) {
            throw new IllegalStateException("database time unavailable");
        }
        return value;
    }

    private LocalDateTime local(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private String nullable(Object value) {
        return value == null ? "" : value.toString();
    }

    private ServiceException invalid(String message) {
        return new ServiceException(message);
    }

    private ServiceException conflict(String message) {
        return new ServiceException(message);
    }

    private ServiceException notFound(String message) {
        return new ServiceException(message);
    }

    private record VersionAppend(long stableId, long versionNo, Long parentVersionId) {
    }

    private record TerminalPayload(Long candidateAssetId, String resultJson, String resultDigest,
                                   String errorCode, String errorSummary) {
    }
}
