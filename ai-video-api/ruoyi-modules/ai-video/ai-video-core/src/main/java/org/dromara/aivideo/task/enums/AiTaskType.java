package org.dromara.aivideo.task.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum AiTaskType {
    TIMELINE_IMAGE_PROMPT_GENERATE("timeline_image_prompt_generate"),
    TIMELINE_FANCY_TEXT_SUGGEST("timeline_fancy_text_suggest"),
    TIMELINE_SUBTITLE_ALIGN("timeline_subtitle_align"),
    TIMELINE_RENDER("timeline_render"),
    WORKFLOW_TEMPLATE_GENERATE("workflow_template_generate"),
    WORKFLOW_TEMPLATE_TEST("workflow_template_test");

    private final String value;

    AiTaskType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static AiTaskType fromValue(String value) {
        for (AiTaskType candidate : values()) {
            if (candidate.value.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unsupported AI task type: " + value);
    }
}
