package org.dromara.aivideo.task.dto;

public record AiTaskAttemptDTO(
    String attemptId,
    String executionId,
    String status,
    String workerId,
    String startedAt,
    String finishedAt,
    String errorCode,
    int attemptNo
) {
}
