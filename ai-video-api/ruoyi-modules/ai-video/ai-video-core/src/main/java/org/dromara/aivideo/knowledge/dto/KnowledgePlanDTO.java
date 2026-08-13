package org.dromara.aivideo.knowledge.dto;

/**
 * 确定性知识候选方案。
 */
public record KnowledgePlanDTO(
    String candidateCode,
    String planCode,
    Long primaryTemplateVersionId,
    String angleCode,
    String differentiatorTechniqueCode
) {

    public KnowledgePlanDTO {
        requireNonBlank(candidateCode, "候选代码");
        requireNonBlank(planCode, "方案代码");
        if (primaryTemplateVersionId == null || primaryTemplateVersionId <= 0) {
            throw new IllegalArgumentException("主模板版本编号必须为正数");
        }
        requireNonBlank(angleCode, "角度代码");
        requireNonBlank(differentiatorTechniqueCode, "差异化技法代码");
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
