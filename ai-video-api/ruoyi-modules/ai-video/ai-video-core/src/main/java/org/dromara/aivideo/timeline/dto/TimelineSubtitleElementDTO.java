package org.dromara.aivideo.timeline.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dromara.aivideo.timeline.enums.TimelineElementType;

public record TimelineSubtitleElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    String sourceTextSnapshot,
    String displayText,
    int sourceStartOffset,
    int sourceEndOffset,
    String fontCode,
    String fontVersion,
    String fontSha256,
    int fontSizePx,
    String color,
    boolean backgroundEnabled,
    @JsonInclude(JsonInclude.Include.NON_NULL) String backgroundColor,
    boolean outlineEnabled,
    @JsonInclude(JsonInclude.Include.NON_NULL) String outlineColor,
    int outlineWidthPx,
    String safeAreaAnchor,
    String alignment
) implements TimelineElementDTO {
}
