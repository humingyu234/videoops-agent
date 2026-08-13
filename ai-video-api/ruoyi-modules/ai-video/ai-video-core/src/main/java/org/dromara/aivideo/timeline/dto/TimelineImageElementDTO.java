package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.TimelineElementType;
import org.dromara.aivideo.timeline.enums.TimelineFitMode;

public record TimelineImageElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    String assetId,
    TimelineVisualTransformDTO transform,
    TimelineFitMode fitMode,
    TimelineCropDTO crop,
    TimelineFadeDTO fade,
    int sourceStartOffset,
    int sourceEndOffset,
    String adoptedPrompt,
    String sourceTaskId
) implements TimelineElementDTO {
}
