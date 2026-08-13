package org.dromara.aivideo.platform.workflow.domain.vo;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/** 运营端工作流模板响应模型，不包含任何明文凭据。 */
public final class WorkflowTemplateAdminVos {

    private WorkflowTemplateAdminVos() {
    }

    public record SummaryVo(
        String templateId,
        String channel,
        String name,
        String slug,
        String summary,
        String status,
        boolean recommended,
        String categoryId,
        String categoryName,
        boolean executionConfigured,
        boolean executionEnabled,
        String accountName,
        long rowRevision,
        LocalDateTime enabledAt,
        LocalDateTime updateTime
    ) {
    }

    public record DetailVo(
        String templateId,
        String channel,
        String name,
        String slug,
        String summary,
        String description,
        String coverAssetId,
        String categoryId,
        List<String> tagIds,
        JsonNode formSchema,
        String schemaHash,
        String status,
        boolean recommended,
        int sortNo,
        Integer estimatedDurationSeconds,
        String billingMode,
        long rowRevision,
        LocalDateTime enabledAt,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        ExecutionConfigVo executionConfig
    ) {
    }

    public record ExecutionConfigVo(
        String executionConfigId,
        String templateId,
        String runningHubAccountId,
        String executionMode,
        String workflowId,
        String webAppId,
        String instanceType,
        JsonNode inputMapping,
        JsonNode outputPolicy,
        int timeoutSeconds,
        boolean enabled,
        boolean hasAccessPassword,
        String lastTestStatus,
        long rowRevision,
        LocalDateTime updateTime
    ) {
    }

    public record OptionVo(String value, String label, String status) {
    }
}
