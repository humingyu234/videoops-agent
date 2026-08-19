package org.dromara.aivideo.studio.draft.dto;

import java.time.LocalDateTime;

/** 工作台草稿的精确客户端契约。 */
public record StudioWorkflowDraftDTO(
    String revision,
    int currentStep,
    String schemaVersion,
    String snapshotJson,
    LocalDateTime updatedAt
) {
}
