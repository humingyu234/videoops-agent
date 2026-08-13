package org.dromara.aivideo.timeline.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FancyTextTemplateCode {
    KEYWORD_POP("keyword_pop"),
    GOLD_IMPACT("gold_impact"),
    NEON_BREATHE("neon_breathe"),
    HANDWRITING_REVEAL("handwriting_reveal"),
    BUBBLE_BOUNCE("bubble_bounce"),
    TITLE_WIPE("title_wipe");

    private final String value;

    FancyTextTemplateCode(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static FancyTextTemplateCode fromValue(String value) {
        for (FancyTextTemplateCode candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported fancy text template: " + value);
    }
}
