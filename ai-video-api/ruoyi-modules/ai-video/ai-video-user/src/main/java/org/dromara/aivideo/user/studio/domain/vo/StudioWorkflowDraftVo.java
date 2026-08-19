package org.dromara.aivideo.user.studio.domain.vo;

import org.dromara.aivideo.studio.draft.dto.StudioWorkflowDraftDTO;

/** 工作台草稿响应。 */
public record StudioWorkflowDraftVo(
    String revision,
    int currentStep,
    String schemaVersion,
    String snapshotJson,
    String updatedAt
) {
    public static StudioWorkflowDraftVo from(StudioWorkflowDraftDTO value) {
        if (value == null) return null;
        return new StudioWorkflowDraftVo(value.revision(), value.currentStep(), value.schemaVersion(),
            value.snapshotJson(), value.updatedAt() == null ? null : value.updatedAt().toString());
    }
}
