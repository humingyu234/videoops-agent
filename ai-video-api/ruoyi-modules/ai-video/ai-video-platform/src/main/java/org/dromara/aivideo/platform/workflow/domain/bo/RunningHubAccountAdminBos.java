package org.dromara.aivideo.platform.workflow.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 运营端 RunningHub 账号请求模型。 */
public final class RunningHubAccountAdminBos {

    private RunningHubAccountAdminBos() {
    }

    @Getter
    @Setter
    public static class RunningHubAccountQueryBo {
        @Size(max = 128)
        private String keyword;
        private Boolean enabled;
    }

    public record CreateRunningHubAccountBo(
        @NotBlank @Size(max = 128) String accountName,
        @NotBlank @Size(max = 4096) String apiKey
    ) {
        @Override
        public String toString() {
            return "CreateRunningHubAccountBo[accountName=" + accountName + ", apiKey=<redacted>]";
        }
    }

    public record UpdateRunningHubAccountBo(
        @NotBlank @Size(max = 128) String accountName,
        @Size(max = 4096) String apiKey,
        @NotNull @PositiveOrZero Long expectedRevision
    ) {
        @Override
        public String toString() {
            return "UpdateRunningHubAccountBo[accountName=" + accountName
                + ", apiKey=<redacted>, expectedRevision=" + expectedRevision + "]";
        }
    }

    public record StatusChangeBo(@NotNull @PositiveOrZero Long expectedRevision) {
    }

    public record ParameterCandidatesBo(
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String accountId,
        @NotBlank @Pattern(regexp = "runninghub_workflow|runninghub_ai_app") String executionMode,
        @Pattern(regexp = "[1-9][0-9]{0,19}") String workflowId,
        @Pattern(regexp = "[1-9][0-9]{0,19}") String webAppId
    ) {
        @AssertTrue(message = "执行模式与 RunningHub 远端编号不匹配")
        public boolean isRemoteIdSelectionValid() {
            if ("runninghub_ai_app".equals(executionMode)) {
                return hasText(webAppId) && !hasText(workflowId);
            }
            if ("runninghub_workflow".equals(executionMode)) {
                return hasText(workflowId) && !hasText(webAppId);
            }
            return false;
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
