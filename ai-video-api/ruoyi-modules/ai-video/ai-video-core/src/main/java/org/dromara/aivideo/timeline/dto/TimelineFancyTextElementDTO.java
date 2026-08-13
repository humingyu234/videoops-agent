package org.dromara.aivideo.timeline.dto;

import org.dromara.aivideo.timeline.enums.FancyTextTemplateCode;
import org.dromara.aivideo.timeline.enums.TimelineElementType;

public record TimelineFancyTextElementDTO(
    String elementId,
    TimelineElementType elementType,
    long startMs,
    long endMs,
    int zIndex,
    boolean enabled,
    boolean locked,
    String label,
    String text,
    FancyTextTemplateCode templateCode,
    String fontCode,
    String fontVersion,
    String fontSha256,
    String color,
    String accentColor,
    TimelineVisualTransformDTO transform,
    String animationIntensity,
    long enterDurationMs,
    long exitDurationMs,
    String suggestionTaskId,
    String suggestionReason
) implements TimelineElementDTO {
}
