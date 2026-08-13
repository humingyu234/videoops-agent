package org.dromara.aivideo.knowledge.dto;

import java.util.List;
import java.util.Objects;

/**
 * 简化知识上下文解析请求。
 */
public record KnowledgeContextRequestDTO(
    String industryCode,
    String purposeCode,
    Integer targetDurationSeconds,
    List<String> tagCodes
) {

    private static final int MAX_CODE_LENGTH = 64;

    public KnowledgeContextRequestDTO {
        industryCode = normalizeCode(industryCode, "行业代码");
        purposeCode = normalizeCode(purposeCode, "用途代码");
        if (targetDurationSeconds == null || targetDurationSeconds <= 0) {
            throw new IllegalArgumentException("目标时长必须为正数");
        }
        Objects.requireNonNull(tagCodes, "标签代码集合不能为空");
        tagCodes = tagCodes.stream()
            .map(KnowledgeContextRequestDTO::normalizeTagCode)
            .distinct()
            .sorted()
            .toList();
    }

    private static String normalizeCode(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException(label + "长度不能超过 64 个字符");
        }
        if ("*".equals(normalized)) {
            throw new IllegalArgumentException(label + "不能包含通配符");
        }
        return normalized;
    }

    private static String normalizeTagCode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("标签代码不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("标签代码不能为空");
        }
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("标签代码长度不能超过 64 个字符");
        }
        return normalized;
    }
}
