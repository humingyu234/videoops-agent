package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskResourceType {
    CREATION_PROJECT("creation_project"),
    WORKFLOW_ORDER("workflow_order"),
    WORKFLOW_TEMPLATE("workflow_template");

    private final String value;

    AiTaskResourceType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskResourceType fromValue(String value) {
        for (AiTaskResourceType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task resource type: " + value);
    }
}
