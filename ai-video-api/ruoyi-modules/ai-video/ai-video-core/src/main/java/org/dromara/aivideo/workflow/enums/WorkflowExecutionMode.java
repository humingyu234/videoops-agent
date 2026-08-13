package org.dromara.aivideo.workflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkflowExecutionMode {
    RUNNINGHUB_WORKFLOW("runninghub_workflow"),
    RUNNINGHUB_AI_APP("runninghub_ai_app");

    private final String value;
}
