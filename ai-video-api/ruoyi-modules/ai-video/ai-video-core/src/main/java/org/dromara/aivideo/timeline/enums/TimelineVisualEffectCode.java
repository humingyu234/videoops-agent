package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineVisualEffectCode {
    FADE_IN("fade_in"),
    FADE_OUT("fade_out"),
    GENTLE_ZOOM_IN("gentle_zoom_in"),
    GENTLE_ZOOM_OUT("gentle_zoom_out"),
    LIGHT_BLUR("light_blur");

    private final String value;

    TimelineVisualEffectCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineVisualEffectCode fromValue(String value) {
        for (TimelineVisualEffectCode candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline visual effect: " + value);
    }
}
