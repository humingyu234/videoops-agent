package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskAttemptStatus {
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    ABANDONED("abandoned");

    private final String value;

    AiTaskAttemptStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskAttemptStatus fromValue(String value) {
        for (AiTaskAttemptStatus candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task attempt status: " + value);
    }
}
