package org.dromara.aivideo.task.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

@JsonIgnoreType
public final class AiTaskCompletionDTO {

    private final String executionId;
    private final String leaseToken;
    private final String resultAssetId;
    private final String errorCode;
    private final String safeMessage;
    private final AiTaskResultPayloadDTO resultPayload;
    private final int expectedRowVersion;
    private final boolean success;
    private final boolean retryable;

    public AiTaskCompletionDTO(String executionId, String leaseToken, String resultAssetId,
                               String errorCode, String safeMessage, AiTaskResultPayloadDTO resultPayload,
                               int expectedRowVersion, boolean success, boolean retryable) {
        if (safeMessage != null && safeMessage.codePointCount(0, safeMessage.length()) > 200) {
            throw new IllegalArgumentException("safeMessage must not exceed 200 Unicode code points");
        }
        if (success) {
            boolean hasResultAsset = resultAssetId != null && !resultAssetId.isBlank();
            boolean hasResultPayload = resultPayload != null;
            if (hasResultAsset == hasResultPayload) {
                throw new IllegalArgumentException(
                    "successful completion must contain exactly one result channel");
            }
        } else if (resultAssetId != null || resultPayload != null) {
            throw new IllegalArgumentException("failed completion must not contain a successful result");
        }
        this.executionId = executionId;
        this.leaseToken = leaseToken;
        this.resultAssetId = resultAssetId;
        this.errorCode = errorCode;
        this.safeMessage = safeMessage;
        this.resultPayload = resultPayload;
        this.expectedRowVersion = expectedRowVersion;
        this.success = success;
        this.retryable = retryable;
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getLeaseToken() {
        return leaseToken;
    }

    public String getResultAssetId() {
        return resultAssetId;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSafeMessage() {
        return safeMessage;
    }

    public AiTaskResultPayloadDTO getResultPayload() {
        return resultPayload;
    }

    public int getExpectedRowVersion() {
        return expectedRowVersion;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
