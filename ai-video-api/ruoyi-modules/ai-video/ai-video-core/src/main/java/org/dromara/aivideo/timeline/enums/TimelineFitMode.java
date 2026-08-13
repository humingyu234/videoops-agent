package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineFitMode {
    CONTAIN("contain"),
    COVER("cover");

    private final String value;

    TimelineFitMode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineFitMode fromValue(String value) {
        for (TimelineFitMode candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline fit mode: " + value);
    }
}
