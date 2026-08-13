package org.dromara.aivideo.creation.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CreationAssetStatus {
    PENDING("pending"),
    READY("ready"),
    FAILED("failed");

    private final String value;

    CreationAssetStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static CreationAssetStatus fromValue(String value) {
        for (CreationAssetStatus candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported creation asset status: " + value);
    }
}
