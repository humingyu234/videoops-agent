package org.dromara.aivideo.user.studio.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 保存当前用户唯一工作台草稿。 */
public record StudioWorkflowDraftSaveBo(
    @Min(0) long expectedRevision,
    @Min(0) @Max(6) int currentStep,
    @NotBlank @Pattern(regexp = "studio-workflow-1") String schemaVersion,
    @NotBlank @Size(max = 131072) String snapshotJson
) {
}
