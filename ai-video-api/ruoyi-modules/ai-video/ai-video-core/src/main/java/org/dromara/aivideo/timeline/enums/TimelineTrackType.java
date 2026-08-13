package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TimelineTrackType {
    FANCY_TEXT("fancy_text"),
    SUBTITLE("subtitle"),
    VISUAL_EFFECT("visual_effect"),
    IMAGE_OVERLAY("image_overlay"),
    PIP_VIDEO("pip_video"),
    MAIN_VIDEO("main_video"),
    PRIMARY_AUDIO("primary_audio"),
    BACKGROUND_MUSIC("background_music"),
    SOUND_EFFECT("sound_effect");

    private final String value;

    TimelineTrackType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TimelineTrackType fromValue(String value) {
        for (TimelineTrackType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported timeline track type: " + value);
    }
}
