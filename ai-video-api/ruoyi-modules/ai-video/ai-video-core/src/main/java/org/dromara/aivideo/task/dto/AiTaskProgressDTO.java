package org.dromara.aivideo.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.dromara.aivideo.task.enums.AiTaskStage;

@JsonIgnoreType
public final class AiTaskProgressDTO {

    private final String executionId;
    private final String leaseToken;
    private final int expectedRowVersion;
    private final int percent;
    private final AiTaskStage stage;
    private final String safeMessage;

    public AiTaskProgressDTO(String executionId, String leaseToken, int expectedRowVersion,
                             int percent, AiTaskStage stage, String safeMessage) {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
        this.executionId = executionId;
        this.leaseToken = leaseToken;
        this.expectedRowVersion = expectedRowVersion;
        this.percent = percent;
        this.stage = stage;
        this.safeMessage = safeMessage;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public int getExpectedRowVersion() {
        return expectedRowVersion;
    }

    public int getPercent() {
        return percent;
    }

    public AiTaskStage getStage() {
        return stage;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
