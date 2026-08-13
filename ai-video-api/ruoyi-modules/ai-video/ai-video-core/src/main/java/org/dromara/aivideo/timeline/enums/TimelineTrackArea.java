package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineTrackArea {
    TOP("top"),
    CENTER("center"),
    BOTTOM("bottom");

    private final String value;

    TimelineTrackArea(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineTrackArea fromValue(String value) {
        for (TimelineTrackArea candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline track area: " + value);
    }
}
