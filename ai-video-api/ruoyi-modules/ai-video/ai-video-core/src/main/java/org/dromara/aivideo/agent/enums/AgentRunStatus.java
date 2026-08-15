package org.dromara.aivideo.agent.enums;

import lombok.Getter;

@Getter
public enum AgentRunStatus {
    QUEUED("queued", false),
    RUNNING("running", false),
    WAITING_INPUT("waiting_input", false),
    WAITING_EXTERNAL_TASK("waiting_external_task", false),
    COMPLETED("completed", true),
    FAILED("failed", true),
    CANCELLED("cancelled", true);

    private final String value;
    private final boolean terminal;

    AgentRunStatus(String value, boolean terminal) {
        this.value = value;
        this.terminal = terminal;
    }

    public static AgentRunStatus fromValue(String value) {
        for (AgentRunStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown AgentRun status");
    }
}
