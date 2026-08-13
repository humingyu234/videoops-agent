package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.task.enums.AiTaskStage;

public record TimelineProgressDTO(
    AiTaskStage stage,
    int percent,
    String safeMessage
) {
    public TimelineProgressDTO {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
    }
}
