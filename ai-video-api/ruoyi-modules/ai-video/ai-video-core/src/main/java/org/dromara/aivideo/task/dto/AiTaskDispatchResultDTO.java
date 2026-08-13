package org.dromara.aivideo.task.dto;

public record AiTaskDispatchResultDTO(
    String outcome,
    String taskId,
    String executionId
) {
    public AiTaskDispatchResultDTO {
        if (!"none".equals(outcome)
            && !"completed".equals(outcome)
            && !"failed".equals(outcome)
            && !"cancelled".equals(outcome)
            && !"lease_lost".equals(outcome)) {
            throw new IllegalArgumentException("unsupported dispatch outcome");
        }
    }
}
