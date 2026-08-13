package org.dromara.aivideo.timeline.dto;

import java.util.List;

public record TimelineRenderCommandDTO(
    String taskId,
    String executionId,
    String attemptId,
    String inputVersionId,
    String fontRegistryVersion,
    String fontRegistrySha256,
    TimelineDocumentDTO timeline,
    TimelineOutputConfigDTO outputConfig,
    List<TimelineAssetReferenceDTO> assets
) {
}
