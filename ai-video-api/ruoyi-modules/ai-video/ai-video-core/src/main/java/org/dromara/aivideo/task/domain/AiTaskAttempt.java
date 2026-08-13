package org.dromara.aivideo.task.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_ai_task_attempt") @AppAuditRequired
public class AiTaskAttempt extends BaseEntity {
    @TableId(value = "task_attempt_id", type = IdType.INPUT) private Long taskAttemptId;
    private Long ownerUserId; private Long taskId; private Long taskExecutionId; private Long attemptNo;
    private String attemptStatus; @Version private Long rowVersion; private String workerId; private String leaseTokenDigest;
    private java.time.LocalDateTime startedAt; private java.time.LocalDateTime finishedAt; private String exitCategory;
    private String errorSummary; private String actorType; private Long actorId;
}
