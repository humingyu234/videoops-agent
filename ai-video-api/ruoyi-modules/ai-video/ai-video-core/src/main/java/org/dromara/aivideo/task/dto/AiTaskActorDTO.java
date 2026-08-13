package org.dromara.aivideo.task.dto;

public record AiTaskActorDTO(String actorType, Long actorId, Long ownerUserId) {
    public AiTaskActorDTO {
        if (actorId == null || actorId <= 0
            || ("app_user".equals(actorType) && (ownerUserId == null || !ownerUserId.equals(actorId)))
            || ("sys_user".equals(actorType) && ownerUserId != null)
            || (!"app_user".equals(actorType) && !"sys_user".equals(actorType))) {
            throw new IllegalArgumentException("invalid task actor");
        }
    }
}
