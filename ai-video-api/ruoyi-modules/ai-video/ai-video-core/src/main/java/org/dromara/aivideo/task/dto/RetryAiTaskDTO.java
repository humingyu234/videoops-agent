package org.dromara.aivideo.task.dto;

public record RetryAiTaskDTO(
    String sourceTaskId,
    String idempotencyKey,
    String requestDigest
) {
}
