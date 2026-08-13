package org.dromara.aivideo.user.discovery.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** 用户端基于模板创建内容所需的表单配置。 */
public record WorkflowCreationConfigVo(
    String templateId,
    String schemaVersion,
    String schemaHash,
    List<InputFieldVo> fields,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer estimatedDurationSeconds,
    BillingPolicyVo billingPolicy
) {
    public static WorkflowCreationConfigVo from(WorkflowTemplateDTOs.CreationConfig source) {
        return new WorkflowCreationConfigVo(
            source.templateId(), source.schemaVersion(), source.schemaHash(), fieldsFrom(source.fields()),
            source.estimatedDurationSeconds(), billingPolicyFrom(source.billingPolicy()));
    }

    private static List<InputFieldVo> fieldsFrom(List<WorkflowTemplateDTOs.InputField> source) {
        return source == null ? List.of() : source.stream().map(InputFieldVo::from).toList();
    }

    private static BillingPolicyVo billingPolicyFrom(WorkflowTemplateDTOs.BillingPolicy source) {
        return source == null ? null : new BillingPolicyVo(source.mode());
    }

    public record InputFieldVo(
        String inputKey,
        @JsonInclude(JsonInclude.Include.NON_NULL) String semanticKey,
        String label,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        String control,
        String valueType,
        boolean required,
        @JsonInclude(JsonInclude.Include.NON_NULL) JsonNode defaultValue,
        @JsonInclude(JsonInclude.Include.NON_NULL) String placeholder,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<InputOptionVo> options,
        @JsonInclude(JsonInclude.Include.NON_NULL) InputConstraintsVo constraints
    ) {
        private static InputFieldVo from(WorkflowTemplateDTOs.InputField source) {
            return new InputFieldVo(
                source.inputKey(), source.semanticKey(), source.label(), source.description(), source.control(),
                source.valueType(), source.required(), source.defaultValue(), source.placeholder(),
                optionsFrom(source.options()), InputConstraintsVo.from(source.constraints()));
        }

        private static List<InputOptionVo> optionsFrom(List<WorkflowTemplateDTOs.InputOption> source) {
            return source == null || source.isEmpty() ? null : source.stream()
                .map(option -> new InputOptionVo(option.value(), option.label()))
                .toList();
        }
    }

    public record InputOptionVo(String value, String label) {
    }

    public record InputConstraintsVo(
        @JsonInclude(JsonInclude.Include.NON_NULL) String min,
        @JsonInclude(JsonInclude.Include.NON_NULL) String max,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer minLength,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxLength,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer minItems,
        @JsonInclude(JsonInclude.Include.NON_NULL) Integer maxItems,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assetType,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> allowedExtensions,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<String> allowedContentTypes,
        @JsonInclude(JsonInclude.Include.NON_NULL) String maxBytesPerAsset
    ) {
        private static InputConstraintsVo from(WorkflowTemplateDTOs.InputConstraints source) {
            return source == null ? null : new InputConstraintsVo(
                source.min(), source.max(), source.minLength(), source.maxLength(), source.minItems(),
                source.maxItems(), source.assetType(), source.allowedExtensions(), source.allowedContentTypes(),
                source.maxBytesPerAsset());
        }
    }

    public record BillingPolicyVo(String mode) {
    }
}
