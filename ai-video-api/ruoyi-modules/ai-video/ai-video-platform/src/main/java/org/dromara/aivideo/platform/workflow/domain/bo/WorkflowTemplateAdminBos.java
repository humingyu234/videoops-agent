package org.dromara.aivideo.platform.workflow.domain.bo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** 运营端工作流模板请求模型。 */
public final class WorkflowTemplateAdminBos {

    private static final String ID_PATTERN = "[1-9]\\d{0,18}";
    private static final String CHANNEL_PATTERN = "video_template|workflow_inspiration";
    private static final String STATUS_PATTERN = "draft|pending_test|enabled|disabled";
    private static final String MODE_PATTERN = "runninghub_workflow|runninghub_ai_app";

    private WorkflowTemplateAdminBos() {
    }

    @Getter
    @Setter
    public static class WorkflowTemplateQueryBo {
        @Pattern(regexp = CHANNEL_PATTERN)
        private String channel;
        @Pattern(regexp = STATUS_PATTERN)
        private String status;
        @Size(max = 128)
        private String keyword;
        @Pattern(regexp = ID_PATTERN)
        private String categoryId;
        private Boolean recommended;
        @Pattern(regexp = "latest|name|sort_no")
        private String sort;
    }

    public record CreateWorkflowTemplateBo(
        @NotBlank @Pattern(regexp = CHANNEL_PATTERN) String channel,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String summary,
        @Size(max = 20000) String description,
        @Pattern(regexp = ID_PATTERN) String coverAssetId,
        @NotBlank @Pattern(regexp = ID_PATTERN) String categoryId,
        @NotNull @Size(max = 50) List<@Pattern(regexp = ID_PATTERN) String> tagIds,
        @NotNull JsonNode formSchema,
        boolean recommended,
        @Min(0) int sortNo,
        @Positive Integer estimatedDurationSeconds
    ) {
    }

    public record UpdateWorkflowTemplateBo(
        @NotBlank @Pattern(regexp = CHANNEL_PATTERN) String channel,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 500) String summary,
        @Size(max = 20000) String description,
        @Pattern(regexp = ID_PATTERN) String coverAssetId,
        @NotBlank @Pattern(regexp = ID_PATTERN) String categoryId,
        @NotNull @Size(max = 50) List<@Pattern(regexp = ID_PATTERN) String> tagIds,
        @NotNull JsonNode formSchema,
        boolean recommended,
        @Min(0) int sortNo,
        @Positive Integer estimatedDurationSeconds,
        @NotNull @PositiveOrZero Long expectedRevision
    ) {
    }

    public record StatusChangeBo(@NotNull @PositiveOrZero Long expectedRevision) {
    }

    public record ExecutionConfigBo(
        @NotBlank @Pattern(regexp = ID_PATTERN) String runningHubAccountId,
        @NotBlank @Pattern(regexp = MODE_PATTERN) String executionMode,
        @Size(max = 128) String workflowId,
        @Size(max = 128) String webAppId,
        @Pattern(regexp = "default|plus") String instanceType,
        @Size(max = 4096) String accessPassword,
        boolean clearAccessPassword,
        @NotNull JsonNode inputMapping,
        @NotNull JsonNode outputPolicy,
        @Min(1) @Max(86400) int timeoutSeconds,
        @NotNull Boolean enabled,
        @PositiveOrZero Long expectedRevision
    ) {
        @Override
        public String toString() {
            return "ExecutionConfigBo[runningHubAccountId=" + runningHubAccountId
                + ", executionMode=" + executionMode
                + ", workflowId=" + workflowId
                + ", webAppId=" + webAppId
                + ", instanceType=" + instanceType
                + ", accessPassword=<redacted>"
                + ", clearAccessPassword=" + clearAccessPassword
                + ", inputMapping=" + inputMapping
                + ", outputPolicy=" + outputPolicy
                + ", timeoutSeconds=" + timeoutSeconds
                + ", enabled=" + enabled
                + ", expectedRevision=" + expectedRevision + "]";
        }
    }
}
