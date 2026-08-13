package org.dromara.aivideo.workflow.dto;

import java.time.LocalDateTime;

/**
 * RunningHub 账号跨模块数据契约。
 */
public final class RunningHubAccountDTOs {

    private RunningHubAccountDTOs() {
    }

    public record Query(String keyword, Boolean enabled) {
    }

    public record Summary(String accountId, String accountName, String apiKeyMasked, boolean hasApiKey,
                          boolean enabled, String lastHealthStatus, LocalDateTime lastHealthTime,
                          String lastHealthSummary, long rowRevision, LocalDateTime updateTime) {
    }

    public record Detail(String accountId, String accountName, String apiKeyMasked, boolean hasApiKey,
                         boolean enabled, String lastHealthStatus, LocalDateTime lastHealthTime,
                         String lastHealthSummary, LocalDateTime credentialUpdatedAt, long rowRevision,
                         LocalDateTime createTime, LocalDateTime updateTime) {
    }

    public record Save(String accountName, char[] apiKey, Long expectedRevision) {
        @Override
        public String toString() {
            return "Save[accountName=" + accountName + ", apiKey=<redacted>, expectedRevision="
                + expectedRevision + "]";
        }
    }

    public record Option(String value, String label) {
    }

    /** 仅供基础设施检查边界读取的加密凭据，不得映射到 HTTP VO。 */
    public record InspectionCredential(String accountId, String accountName, String encryptedApiKey) {
        @Override
        public String toString() {
            return "InspectionCredential[accountId=" + accountId + ", accountName=" + accountName
                + ", encryptedApiKey=<redacted>]";
        }
    }
}
