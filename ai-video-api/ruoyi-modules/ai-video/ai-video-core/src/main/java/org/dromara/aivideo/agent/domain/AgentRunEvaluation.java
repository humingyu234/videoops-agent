package org.dromara.aivideo.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** Immutable quality facts for one bounded AgentRun candidate. */
@Data
@TableName("av_agent_run_evaluation")
@AppAuditRequired
public class AgentRunEvaluation extends BaseEntity {

    @TableId(value = "evaluation_id", type = IdType.INPUT)
    private Long evaluationId;
    private Long agentRunId;
    private Long ownerUserId;
    private Long candidateNo;
    private Long renderTaskId;
    private Long resultAssetId;
    private Long projectId;
    private String ruleSetVersion;
    private String qualityJson;
    private String qualityDigest;
    private String decision;
    private String repairScope;
    private String actorType;
    private Long actorId;
}
