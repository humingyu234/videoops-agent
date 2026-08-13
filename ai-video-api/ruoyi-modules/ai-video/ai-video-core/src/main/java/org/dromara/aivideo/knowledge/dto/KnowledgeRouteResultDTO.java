package org.dromara.aivideo.knowledge.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 知识路由结果。
 */
public record KnowledgeRouteResultDTO(
    String routingVersion,
    String videoTypeCode,
    List<KnowledgePlanDTO> plans,
    String contentHash
) {

    private static final List<String> CANDIDATE_ORDER = List.of("A", "B", "C");

    public KnowledgeRouteResultDTO {
        requireNonBlank(routingVersion, "路由版本");
        requireNonBlank(videoTypeCode, "视频类型代码");
        requireNonBlank(contentHash, "内容哈希");
        plans = List.copyOf(Objects.requireNonNull(plans, "候选方案集合不能为空"));
        if (plans.size() != CANDIDATE_ORDER.size()) {
            throw new IllegalArgumentException("候选方案必须恰好包含 A、B、C 三项");
        }

        Set<String> candidateCodes = new HashSet<>();
        Set<String> planCodes = new HashSet<>();
        Set<List<?>> planTriples = new HashSet<>();
        for (int index = 0; index < plans.size(); index++) {
            KnowledgePlanDTO plan = plans.get(index);
            if (!CANDIDATE_ORDER.get(index).equals(plan.candidateCode())) {
                throw new IllegalArgumentException("候选方案必须按 A、B、C 排列");
            }
            if (!candidateCodes.add(plan.candidateCode())) {
                throw new IllegalArgumentException("候选代码必须唯一");
            }
            if (!planCodes.add(plan.planCode())) {
                throw new IllegalArgumentException("方案代码必须唯一");
            }
            if (!planTriples.add(List.of(
                plan.primaryTemplateVersionId(),
                plan.angleCode(),
                plan.differentiatorTechniqueCode()))) {
                throw new IllegalArgumentException("主模板、角度与差异化技法组合必须唯一");
            }
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
