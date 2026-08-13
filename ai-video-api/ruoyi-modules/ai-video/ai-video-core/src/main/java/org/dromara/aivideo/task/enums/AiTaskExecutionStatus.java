package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskExecutionStatus {
    QUEUED("queued"),
    RUNNING("running"),
    SUCCESS("success"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    AiTaskExecutionStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskExecutionStatus fromValue(String value) {
        for (AiTaskExecutionStatus candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task execution status: " + value);
    }
}
