package org.dromara.aivideo.workflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum WorkflowChannel {
    VIDEO_TEMPLATE("video_template"),
    WORKFLOW_INSPIRATION("workflow_inspiration");

    private final String value;

    public static boolean supports(String value) {
        return Arrays.stream(values()).anyMatch(item -> item.value.equals(value));
    }
}
