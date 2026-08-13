package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineAssetUsageType {
    BASE_VIDEO("base_video"),
    PRIMARY_AUDIO("primary_audio"),
    IMAGE("image"),
    PIP_VIDEO("pip_video"),
    BACKGROUND_MUSIC("background_music"),
    SOUND_EFFECT("sound_effect");

    private final String value;

    TimelineAssetUsageType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineAssetUsageType fromValue(String value) {
        for (TimelineAssetUsageType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline asset usage: " + value);
    }
}
