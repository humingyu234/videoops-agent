package org.dromara.aivideo.creation.dto;

public record RenderOutputFailureDTO(
    String assetId,
    String taskId,
    String failureCode,
    String safeSummary
) {
}
