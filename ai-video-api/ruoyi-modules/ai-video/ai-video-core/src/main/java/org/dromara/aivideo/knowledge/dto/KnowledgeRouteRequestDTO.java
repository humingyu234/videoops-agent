package org.dromara.aivideo.knowledge.dto;

import java.util.List;
import java.util.Objects;

/**
 * 知识路由请求。
 */
public record KnowledgeRouteRequestDTO(
    Long directionCatalogVersionId,
    String industryCode,
    String purposeCode,
    Integer targetDurationSeconds,
    List<String> tagCodes
) {

    public KnowledgeRouteRequestDTO {
        requirePositive(directionCatalogVersionId, "方向目录版本编号");
        requireNonBlank(industryCode, "行业代码");
        requireNonBlank(purposeCode, "用途代码");
        requirePositive(targetDurationSeconds, "目标时长");
        Objects.requireNonNull(tagCodes, "标签代码集合不能为空");
        if (tagCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("标签代码不能为空");
        }
        tagCodes = tagCodes.stream().sorted().toList();
    }

    private static void requirePositive(Number value, String label) {
        if (value == null || value.longValue() <= 0) {
            throw new IllegalArgumentException(label + "必须为正数");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
