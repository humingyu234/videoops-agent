package org.dromara.aivideo.agent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.agent.domain.AgentRunApproval;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;

public interface AgentRunApprovalMapper extends BaseMapperPlus<AgentRunApproval, AgentRunApproval> {

    /** Persist an exact pending approval decision; the surrounding transaction owns the run transition. */
    @Update("""
        UPDATE av_agent_run_approval approval
        JOIN av_agent_run run
          ON run.agent_run_id = approval.agent_run_id
         AND run.owner_user_id = approval.owner_user_id
         AND run.pending_approval_id = approval.approval_id
         AND run.approval_revision = approval.revision
        SET approval.approval_status = #{decision},
            approval.decision_summary = #{decisionSummary},
            approval.decided_by = #{ownerUserId},
            approval.decided_at = #{databaseNow},
            approval.update_by = #{ownerUserId},
            approval.update_time = #{databaseNow}
        WHERE approval.approval_id = #{approvalId}
          AND approval.agent_run_id = #{agentRunId}
          AND approval.owner_user_id = #{ownerUserId}
          AND approval.approval_type = #{approvalType}
          AND approval.approval_status = 'pending'
          AND approval.revision = #{approvalRevision}
          AND run.run_status = 'waiting_approval'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
        """)
    int decidePending(@Param("approvalId") long approvalId,
                      @Param("agentRunId") long agentRunId,
                      @Param("ownerUserId") long ownerUserId,
                      @Param("approvalType") String approvalType,
                      @Param("approvalRevision") long approvalRevision,
                      @Param("expectedContractRevision") long expectedContractRevision,
                      @Param("expectedRowVersion") long expectedRowVersion,
                      @Param("decision") String decision,
                      @Param("decisionSummary") String decisionSummary,
                      @Param("databaseNow") LocalDateTime databaseNow);
}
