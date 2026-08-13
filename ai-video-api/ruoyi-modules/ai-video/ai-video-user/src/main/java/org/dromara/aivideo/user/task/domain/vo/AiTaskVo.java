package org.dromara.aivideo.user.task.domain.vo;

import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskResultPayloadDTO;

public record AiTaskVo(
    String taskId,
    String taskType,
    String resourceType,
    String resourceId,
    String projectId,
    String draftRevision,
    String inputVersionId,
    String status,
    String stage,
    int progress,
    boolean canCancel,
    boolean canRetry,
    String resultAssetId,
    AiTaskResultPayloadDTO result,
    String errorCode,
    String errorSummary,
    String createdAt,
    String updatedAt
) {

    public static AiTaskVo from(AiTaskDTO dto) {
        return new AiTaskVo(dto.taskId(), dto.taskType(), dto.resourceType(), dto.resourceId(), dto.projectId(),
            dto.draftRevision(), dto.inputVersionId(), dto.status(), dto.stage(), dto.progress(), dto.cancellable(),
            dto.retryable(), dto.resultAssetId(), dto.resultPayload(), dto.errorCode(), dto.safeMessage(),
            dto.createdAt(), dto.updatedAt());
    }
}
