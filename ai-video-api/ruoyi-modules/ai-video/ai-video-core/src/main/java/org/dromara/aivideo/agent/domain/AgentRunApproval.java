package org.dromara.aivideo.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/** Auditable initial, conditional, or final human approval for one AgentRun. */
@Data
@TableName("av_agent_run_approval")
@AppAuditRequired
public class AgentRunApproval extends BaseEntity {

    @TableId(value = "approval_id", type = IdType.INPUT)
    private Long approvalId;
    private Long agentRunId;
    private Long ownerUserId;
    private Long evaluationId;
    private String approvalType;
    private String approvalStatus;
    private String subjectDigest;
    private Long revision;
    private String requestSummary;
    private String decisionSummary;
    private Long decidedBy;
    private LocalDateTime decidedAt;
    private String actorType;
    private Long actorId;
}
