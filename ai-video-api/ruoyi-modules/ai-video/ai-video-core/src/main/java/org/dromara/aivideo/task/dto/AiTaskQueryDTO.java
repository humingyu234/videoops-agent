package org.dromara.aivideo.task.dto;

public record AiTaskQueryDTO(
    String taskType,
    String status,
    String resourceType,
    String resourceId,
    String projectId,
    String keyword
) {
}
