package org.dromara.aivideo.workflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkflowTemplateStatus {
    DRAFT("draft"),
    PENDING_TEST("pending_test"),
    ENABLED("enabled"),
    DISABLED("disabled");

    private final String value;
}
