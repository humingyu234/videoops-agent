package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskStage {
    QUEUED("queued"),
    WAITING_FOR_DISPATCH("waiting_for_dispatch"),
    PREPARING_INPUTS("preparing_inputs"),
    SUBMITTING_TO_PROVIDER("submitting_to_provider"),
    CONFIRMING_PROVIDER_ACCEPTANCE("confirming_provider_acceptance"),
    PROVIDER_PROCESSING("provider_processing"),
    PROCESSING_RESULTS("processing_results"),
    PREPARING_ASSETS("preparing_assets"),
    READING_ASSETS("reading_assets"),
    BUILDING_ASS("building_ass"),
    BUILDING_RENDER_PLAN("building_render_plan"),
    ENCODING("encoding"),
    VERIFYING_OUTPUT("verifying_output"),
    REGISTERING_OUTPUT("registering_output"),
    COMPLETED("completed"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    AiTaskStage(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskStage fromValue(String value) {
        for (AiTaskStage candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task stage: " + value);
    }
}
