package org.dromara.aivideo.task.dto;

/** App-user scope required when a workflow order task is read or mutated. */
public record AiTaskAccessScopeDTO(Long tenantId, Long ownerUserId, String workspaceId) {
    public AiTaskAccessScopeDTO {
        if (tenantId == null || tenantId <= 0 || ownerUserId == null || ownerUserId <= 0
            || workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("invalid task access scope");
        }
    }
}
