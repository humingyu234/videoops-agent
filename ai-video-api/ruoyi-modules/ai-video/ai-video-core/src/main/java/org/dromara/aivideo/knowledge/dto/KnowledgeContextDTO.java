package org.dromara.aivideo.knowledge.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 简化知识上下文解析结果。
 */
public record KnowledgeContextDTO(
    List<Long> knowledgeVersionIds,
    List<String> excerpts,
    List<String> copyRules,
    String contentHash
) {

    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public KnowledgeContextDTO {
        knowledgeVersionIds = List.copyOf(
            Objects.requireNonNull(knowledgeVersionIds, "知识版本编号集合不能为空"));
        excerpts = List.copyOf(Objects.requireNonNull(excerpts, "知识正文集合不能为空"));
        if (knowledgeVersionIds.size() != excerpts.size()) {
            throw new IllegalArgumentException("知识版本编号与正文必须一一对应");
        }

        Set<Long> uniqueVersionIds = new HashSet<>();
        for (int index = 0; index < knowledgeVersionIds.size(); index++) {
            Long knowledgeVersionId = knowledgeVersionIds.get(index);
            if (knowledgeVersionId <= 0) {
                throw new IllegalArgumentException("知识版本编号必须为正数");
            }
            if (!uniqueVersionIds.add(knowledgeVersionId)) {
                throw new IllegalArgumentException("知识版本编号不能重复");
            }
            if (excerpts.get(index).trim().isEmpty()) {
                throw new IllegalArgumentException("知识正文不能为空");
            }
        }

        Objects.requireNonNull(copyRules, "文案规则集合不能为空");
        copyRules = copyRules.stream()
            .map(KnowledgeContextDTO::normalizeCopyRule)
            .distinct()
            .toList();
        if (contentHash == null || !SHA256_PATTERN.matcher(contentHash).matches()) {
            throw new IllegalArgumentException("内容哈希必须为 64 位小写十六进制 SHA-256");
        }
    }

    private static String normalizeCopyRule(String value) {
        if (value == null) {
            throw new IllegalArgumentException("文案规则不能为空");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("文案规则不能为空");
        }
        return normalized;
    }
}
