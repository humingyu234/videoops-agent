package org.dromara.aivideo.creation.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CreationAssetUsageOrigin {
    UPLOAD("upload"),
    DIGITAL_HUMAN_OUTPUT("digital_human_output"),
    TIMELINE_RENDER_OUTPUT("timeline_render_output");

    private final String value;

    CreationAssetUsageOrigin(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CreationAssetUsageOrigin fromValue(String value) {
        for (CreationAssetUsageOrigin candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported creation asset origin: " + value);
    }
}
