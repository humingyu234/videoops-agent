package org.dromara.aivideo.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_ai_task") @AppAuditRequired
public class AiTask extends BaseEntity {
    @TableId(value = "task_id", type = IdType.INPUT) private Long taskId;
    private Long ownerUserId; private String taskType; private String resourceType; private Long resourceId;
    private Long inputVersionId; private String idempotencyKey; private String requestDigest; private String requestSchemaVersion;
    private String requestPayloadJson; private String taskStatus; private String stage; private Integer progressPercent;
    private Long rowVersion; private Boolean cancelRequested; private Long activeExecutionId; private Long resultAssetId;
    private String resultSchemaVersion; private String resultPayloadJson; private String errorCode; private String errorSummary;
    private String quotaPolicyVersion; private Long estimatedUsage; private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime finishedAt; private String actorType; private Long actorId;
}
