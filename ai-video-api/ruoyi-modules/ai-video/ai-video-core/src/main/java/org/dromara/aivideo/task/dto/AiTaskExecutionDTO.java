package org.dromara.aivideo.task.dto;

public record AiTaskExecutionDTO(
    String executionId,
    String taskId,
    String executionStatus,
    String workerId,
    String leaseExpiresAt,
    String startedAt,
    String finishedAt,
    String inputVersionId,
    String resultAssetId,
    String errorCode,
    int executionNo,
    int rowVersion,
    int progress
) {
}
