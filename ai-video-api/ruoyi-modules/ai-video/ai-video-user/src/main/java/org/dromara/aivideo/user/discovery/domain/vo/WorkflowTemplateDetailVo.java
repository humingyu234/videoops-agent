package org.dromara.aivideo.user.discovery.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.dromara.aivideo.workflow.dto.WorkflowTemplateDTOs;

import java.util.List;

/** 用户端工作流模板详情。 */
public record WorkflowTemplateDetailVo(
    String templateId,
    String title,
    String summary,
    String channel,
    WorkflowTemplateCardVo.CategoryVo category,
    List<WorkflowTemplateCardVo.TagVo> tags,
    WorkflowTemplateCardVo.MediaVo cover,
    @JsonInclude(JsonInclude.Include.NON_NULL) WorkflowTemplateCardVo.MediaVo preview,
    @JsonInclude(JsonInclude.Include.NON_NULL) String usageCount,
    @JsonInclude(JsonInclude.Include.NON_NULL) Integer estimatedDurationSeconds,
    String enabledAt,
    String description,
    List<WorkflowTemplateCardVo.MediaVo> cases,
    List<RequiredInputVo> requiredInputs
) {
    public static WorkflowTemplateDetailVo from(WorkflowTemplateDTOs.PublicDetail source) {
        return new WorkflowTemplateDetailVo(
            source.templateId(), source.title(), source.summary(), source.channel(),
            WorkflowTemplateCardVo.categoryFrom(source.category()), WorkflowTemplateCardVo.tagsFrom(source.tags()),
            WorkflowTemplateCardVo.mediaFrom(source.cover()), WorkflowTemplateCardVo.mediaFrom(source.preview()),
            source.usageCount(), source.estimatedDurationSeconds(),
            WorkflowTemplateCardVo.dateTimeFrom(source.enabledAt()), source.description(),
            WorkflowTemplateCardVo.mediaFrom(source.cases()), requiredInputsFrom(source.requiredInputs()));
    }

    private static List<RequiredInputVo> requiredInputsFrom(List<WorkflowTemplateDTOs.RequiredInput> source) {
        return source == null ? List.of() : source.stream().map(required -> new RequiredInputVo(
            required.semanticKey(), required.label(), required.valueType(), required.assetType(), required.required()))
            .toList();
    }

    public record RequiredInputVo(
        @JsonInclude(JsonInclude.Include.NON_NULL) String semanticKey,
        String label,
        String valueType,
        @JsonInclude(JsonInclude.Include.NON_NULL) String assetType,
        boolean required
    ) {
    }
}
