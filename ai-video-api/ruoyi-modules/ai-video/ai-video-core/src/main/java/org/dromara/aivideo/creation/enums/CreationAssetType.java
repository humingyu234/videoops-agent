package org.dromara.aivideo.creation.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CreationAssetType {
    VIDEO("video"),
    IMAGE("image"),
    AUDIO("audio");

    private final String value;

    CreationAssetType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CreationAssetType fromValue(String value) {
        for (CreationAssetType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported creation asset type: " + value);
    }
}
