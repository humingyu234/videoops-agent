package org.dromara.aivideo.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/** Recoverable Agent control-plane state; existing task tables remain the execution facts. */
@Data
@TableName("av_agent_run")
@AppAuditRequired
public class AgentRun extends BaseEntity {

    @TableId(value = "agent_run_id", type = IdType.INPUT)
    private Long agentRunId;
    private Long ownerUserId;
    private Long deliveryBriefVersionId;
    private Long acceptanceProfileVersionId;
    private Long contractRevision;
    private String schemaVersion;
    private String idempotencyKey;
    private String requestDigest;
    private String runStatus;
    private Long rowVersion;
    private Long leaseGeneration;
    private String leaseOwner;
    private String leaseTokenDigest;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime resumeAfter;
    private String waitingTaskSource;
    private Long waitingTaskId;
    private Long waitingContractRevision;
    private Long candidateAssetId;
    private String resultSummaryJson;
    private String resultDigest;
    private String errorCode;
    private String errorSummary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime stateChangedAt;
    private String actorType;
    private Long actorId;
}
