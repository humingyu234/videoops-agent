package org.dromara.aivideo.workflow.order.dto;

public record WorkflowOrderOwnerDTO(Long tenantId, String workspaceId, Long ownerUserId) {
    public WorkflowOrderOwnerDTO {
        if (tenantId == null || tenantId <= 0 || workspaceId == null || workspaceId.isBlank()
            || ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("invalid workflow order owner");
        }
    }
}
