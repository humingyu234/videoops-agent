package org.dromara.aivideo.task.dto;

public record AiTaskSummaryDTO(
    String taskId,
    String taskType,
    String status,
    String stage,
    String resourceType,
    String resourceId,
    String projectId,
    String createdAt,
    String updatedAt,
    String errorCode,
    String safeMessage,
    int progress,
    boolean cancellable,
    boolean retryable
) {
    public AiTaskSummaryDTO {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
    }
}
