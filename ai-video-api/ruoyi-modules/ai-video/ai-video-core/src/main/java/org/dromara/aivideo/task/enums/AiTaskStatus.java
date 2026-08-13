package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskStatus {
    PENDING("pending"),
    QUEUED("queued"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    AiTaskStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskStatus fromValue(String value) {
        for (AiTaskStatus candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task status: " + value);
    }
}
