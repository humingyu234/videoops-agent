package org.dromara.aivideo.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_ai_task_execution") @AppAuditRequired
public class AiTaskExecution extends BaseEntity {
    @TableId(value = "task_execution_id", type = IdType.INPUT) private Long taskExecutionId;
    private Long ownerUserId; private Long taskId; private Long resourceId; private Long executionNo;
    private String executionStatus; private String stage; private Integer progressPercent; @Version private Long rowVersion;
    private java.time.LocalDateTime nextRunAt; private String leaseOwner; private String leaseToken;
    private java.time.LocalDateTime leaseExpiresAt; private Boolean cancelRequestedSnapshot; private Long inputVersionId;
    private String outputConfigDigest; private Long resultAssetId; private String errorCode; private String errorSummary;
    private String actorType; private Long actorId;
}
