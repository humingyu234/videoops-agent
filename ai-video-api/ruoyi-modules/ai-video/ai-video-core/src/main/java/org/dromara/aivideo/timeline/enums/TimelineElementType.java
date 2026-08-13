package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineElementType {
    MAIN_VIDEO("main_video"),
    IMAGE_OVERLAY("image_overlay"),
    PIP_VIDEO("pip_video"),
    SUBTITLE("subtitle"),
    FANCY_TEXT("fancy_text"),
    AUDIO("audio"),
    VISUAL_EFFECT("visual_effect");

    private final String value;

    TimelineElementType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineElementType fromValue(String value) {
        for (TimelineElementType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline element type: " + value);
    }
}
