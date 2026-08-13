package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineOutputQuality {
    STANDARD("standard"),
    HIGH("high");

    private final String value;

    TimelineOutputQuality(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineOutputQuality fromValue(String value) {
        for (TimelineOutputQuality candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline output quality: " + value);
    }
}
