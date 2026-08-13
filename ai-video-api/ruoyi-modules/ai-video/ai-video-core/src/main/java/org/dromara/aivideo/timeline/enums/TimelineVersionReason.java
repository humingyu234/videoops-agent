package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineVersionReason {
    MANUAL_SAVE("manual_save"),
    RESTORED("restored"),
    RENDER_INPUT("render_input"),
    CONFLICT_COPY("conflict_copy");

    private final String value;

    TimelineVersionReason(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineVersionReason fromValue(String value) {
        for (TimelineVersionReason candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline version reason: " + value);
    }
}
