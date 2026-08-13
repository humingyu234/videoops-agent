package org.dromara.aivideo.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** Durable provider submission and polling facts for one unified workflow task. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "av_workflow_task_execution", excludeProperty = {"createDept", "createBy", "updateBy"})
public class WorkflowTaskExecution extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "workflow_task_execution_id", type = IdType.ASSIGN_ID)
    private Long workflowTaskExecutionId;
    private Long taskId;
    private Long tenantId;
    private Long runninghubAccountId;
    private Long orderId;
    private String resourceType;
    private String executionMode;
    private String externalTaskId;
    private String submissionState;
    private LocalDateTime submissionStartedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime providerDeadlineAt;
    private LocalDateTime lastPolledAt;
    private Integer pollCount;
    private String externalStatus;
    private String providerErrorCode;
    private String providerErrorSummary;
    private Long providerDurationMs;
    private String providerUsageJson;
    private String costReconciliationStatus;
    private String resultManifestJson;
}
