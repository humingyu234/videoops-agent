package org.dromara.aivideo.user.task.domain.vo;

import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;

public record AiTaskListItemVo(
    String taskId,
    String taskType,
    String resourceType,
    String resourceId,
    String projectId,
    String status,
    String stage,
    int progress,
    boolean canCancel,
    boolean canRetry,
    String errorCode,
    String errorSummary,
    String createdAt,
    String updatedAt
) {

    public static AiTaskListItemVo from(AiTaskSummaryDTO dto) {
        return new AiTaskListItemVo(dto.taskId(), dto.taskType(), dto.resourceType(), dto.resourceId(),
            dto.projectId(), dto.status(), dto.stage(), dto.progress(), dto.cancellable(), dto.retryable(),
            dto.errorCode(), dto.safeMessage(), dto.createdAt() == null ? null : dto.createdAt().toString(),
            dto.updatedAt() == null ? null : dto.updatedAt().toString());
    }
}
