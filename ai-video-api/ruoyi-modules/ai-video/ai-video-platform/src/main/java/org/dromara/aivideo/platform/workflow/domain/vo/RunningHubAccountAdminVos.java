package org.dromara.aivideo.platform.workflow.domain.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 运营端 RunningHub 账号响应模型，只暴露凭据脱敏状态。 */
public final class RunningHubAccountAdminVos {

    private RunningHubAccountAdminVos() {
    }

    public record SummaryVo(
        String accountId,
        String accountName,
        String apiKeyMasked,
        boolean hasApiKey,
        boolean enabled,
        String lastHealthStatus,
        LocalDateTime lastHealthTime,
        String lastHealthSummary,
        long rowRevision,
        LocalDateTime updateTime
    ) {
    }

    public record DetailVo(
        String accountId,
        String accountName,
        String apiKeyMasked,
        boolean hasApiKey,
        boolean enabled,
        String lastHealthStatus,
        LocalDateTime lastHealthTime,
        String lastHealthSummary,
        LocalDateTime credentialUpdatedAt,
        long rowRevision,
        LocalDateTime createTime,
        LocalDateTime updateTime
    ) {
    }

    public record ParameterCandidatesVo(String webAppName, List<ParameterCandidateVo> candidates) {
        public ParameterCandidatesVo {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record ParameterCandidateVo(
        String nodeId,
        String nodeName,
        String fieldName,
        String fieldType,
        String description,
        String defaultValue,
        List<ParameterOptionVo> options
    ) {
        public ParameterCandidateVo {
            options = options == null ? List.of() : List.copyOf(options);
        }
    }

    public record ParameterOptionVo(String value, String label) {
    }
}
