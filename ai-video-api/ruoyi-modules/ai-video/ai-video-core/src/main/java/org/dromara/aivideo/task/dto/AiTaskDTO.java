package org.dromara.aivideo.task.dto;

public record AiTaskDTO(
    String taskId,
    String taskType,
    String status,
    String stage,
    String resourceType,
    String resourceId,
    String projectId,
    String draftRevision,
    String inputVersionId,
    String resultAssetId,
    String errorCode,
    String safeMessage,
    String createdAt,
    String updatedAt,
    AiTaskResultPayloadDTO resultPayload,
    int progress,
    boolean cancellable,
    boolean retryable
) {
    public AiTaskDTO {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
    }
}
