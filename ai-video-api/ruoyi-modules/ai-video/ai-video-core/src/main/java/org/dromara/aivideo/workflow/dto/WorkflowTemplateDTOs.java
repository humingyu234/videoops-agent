package org.dromara.aivideo.workflow.dto;

import lombok.Data;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工作流模板跨模块数据契约。
 */
public final class WorkflowTemplateDTOs {

    private WorkflowTemplateDTOs() {
    }

    public record AdminQuery(String channel, String status, String keyword, String categoryId,
                             Boolean recommended, String sort) {
    }

    public record AdminSummary(String templateId, String channel, String name, String slug, String summary,
                               String status, boolean recommended, String categoryId, String categoryName,
                               boolean executionConfigured, boolean executionEnabled, String accountName,
                               long rowRevision, LocalDateTime enabledAt, LocalDateTime updateTime) {
    }

    public record AdminDetail(String templateId, String channel, String name, String slug, String summary,
                              String description, String coverAssetId, String categoryId, List<String> tagIds,
                              String formSchema, String schemaHash, String status, boolean recommended,
                              int sortNo, Integer estimatedDurationSeconds, String billingMode,
                              long rowRevision, LocalDateTime enabledAt, LocalDateTime createTime,
                              LocalDateTime updateTime, ExecutionConfig executionConfig) {
    }

    public record Save(String channel, String name, String slug, String summary, String description,
                       String coverAssetId, String categoryId, String tagsJson, String formSchema,
                       boolean recommended, int sortNo, Integer estimatedDurationSeconds,
                       Long expectedRevision) {
    }

    public record Option(String value, String label, String status) {
    }

    public record ExecutionConfig(String executionConfigId, String templateId, String runningHubAccountId,
                                  String executionMode, String workflowId, String webAppId,
                                  String instanceType,
                                  String inputMappingJson, String outputPolicyJson, int timeoutSeconds,
                                  boolean enabled, boolean hasAccessPassword, String lastTestStatus,
                                  long rowRevision, LocalDateTime updateTime) {
    }

    public record ExecutionConfigSave(String executionMode, String runningHubAccountId, String workflowId,
                                      String webAppId, String instanceType, String inputMappingJson, String outputPolicyJson,
                                      int timeoutSeconds, boolean enabled, boolean clearAccessPassword,
                                      char[] accessPassword,
                                      long expectedRevision) {
        @Override
        public String toString() {
            return "ExecutionConfigSave[executionMode=" + executionMode
                + ", runningHubAccountId=" + runningHubAccountId
                + ", workflowId=" + workflowId
                + ", webAppId=" + webAppId
                + ", instanceType=" + instanceType
                + ", inputMappingJson=" + inputMappingJson
                + ", outputPolicyJson=" + outputPolicyJson
                + ", timeoutSeconds=" + timeoutSeconds
                + ", enabled=" + enabled
                + ", clearAccessPassword=" + clearAccessPassword
                + ", accessPassword=<redacted>, expectedRevision=" + expectedRevision + "]";
        }
    }

    public record PublicQuery(String channel, String categoryCode, List<String> tagCodes, String keyword,
                              String sort) {
    }

    public record Category(String categoryCode, String label) {
    }

    public record Tag(String tagCode, String label) {
    }

    public record Media(String mediaId, String mediaType, String url, String posterUrl, int width,
                        int height, String alt) {
    }

    public record PublicCard(String templateId, String title, String summary, String channel,
                             Category category, List<Tag> tags, Media cover, Media preview, String usageCount,
                             Integer estimatedDurationSeconds, LocalDateTime enabledAt) {
    }

    public record RequiredInput(String semanticKey, String label, String valueType, String assetType,
                                boolean required) {
    }

    public record PublicDetail(String templateId, String title, String summary, String channel,
                               Category category, List<Tag> tags, Media cover, Media preview, String usageCount,
                               Integer estimatedDurationSeconds, LocalDateTime enabledAt, String description,
                               List<Media> cases, List<RequiredInput> requiredInputs) {
    }

    public record InputOption(String value, String label) {
    }

    public record InputConstraints(String min, String max, Integer minLength, Integer maxLength,
                                   Integer minItems, Integer maxItems, String assetType,
                                   List<String> allowedExtensions, List<String> allowedContentTypes,
                                   String maxBytesPerAsset) {
    }

    public record InputField(String inputKey, String semanticKey, String label, String description,
                             String control, String valueType, boolean required, JsonNode defaultValue,
                             String placeholder, List<InputOption> options, InputConstraints constraints) {
    }

    public record BillingPolicy(String mode) {
    }

    public record CreationConfig(String templateId, String schemaVersion, String schemaHash,
                                 List<InputField> fields, Integer estimatedDurationSeconds,
                                 BillingPolicy billingPolicy) {
    }

    public record ChannelSummary(String channel, String label, String description, String templateCount) {
    }

    public record CategorySummary(String categoryCode, String label, String templateCount) {
    }

    public record DiscoveryHome(List<Object> banners, List<PublicCard> recommendations,
                                List<ChannelSummary> channels, List<CategorySummary> categories,
                                List<Tag> tags) {
    }

    /**
     * MyBatis 联表投影，仅供 Core Mapper 与 Service 使用。
     */
    @Data
    public static class TemplateRow {
        private Long templateId;
        private String channel;
        private String name;
        private String slug;
        private String summary;
        private String description;
        private Long coverAssetId;
        private String coverUrl;
        private Long categoryId;
        private String categoryName;
        private String tagsJson;
        private String formSchemaJson;
        private String schemaHash;
        private String status;
        private Boolean recommended;
        private Integer sortNo;
        private Integer estimatedDurationSeconds;
        private String billingMode;
        private LocalDateTime enabledAt;
        private Long rowRevision;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
        private Long executionConfigId;
        private Long runninghubAccountId;
        private String accountName;
        private String executionMode;
        private String workflowId;
        private String webappId;
        private String instanceType;
        private String inputMappingJson;
        private String outputPolicyJson;
        private Integer timeoutSeconds;
        private Boolean executionEnabled;
        private Boolean hasAccessPassword;
        private String lastTestStatus;
        private Long executionRevision;
        private LocalDateTime executionUpdateTime;
    }

    @Data
    public static class CountRow {
        private String code;
        private String label;
        private Long templateCount;
    }
}
