package org.dromara.aivideo.task.dto;

import org.dromara.aivideo.task.enums.AiTaskResourceType;
import org.dromara.aivideo.task.enums.AiTaskType;

/** Frozen command for the dedicated workflow task creation boundary. */
public record CreateWorkflowAiTaskDTO(
    AiTaskType taskType,
    AiTaskResourceType resourceType,
    String resourceId,
    String idempotencyKey,
    String requestDigest,
    WorkflowAiTaskPayloadDTO payload
) {
    public CreateWorkflowAiTaskDTO {
        boolean generate = taskType == AiTaskType.WORKFLOW_TEMPLATE_GENERATE
            && resourceType == AiTaskResourceType.WORKFLOW_ORDER
            && payload != null && payload.orderId() != null && payload.orderId().equals(resourceId);
        boolean test = taskType == AiTaskType.WORKFLOW_TEMPLATE_TEST
            && resourceType == AiTaskResourceType.WORKFLOW_TEMPLATE
            && payload != null && payload.orderId() == null && payload.templateId().equals(resourceId);
        if ((!generate && !test) || !WorkflowAiTaskPayloadDTO.positiveId(resourceId)
            || idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{1,64}")
            || requestDigest == null || !requestDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid workflow task command");
        }
    }
}
