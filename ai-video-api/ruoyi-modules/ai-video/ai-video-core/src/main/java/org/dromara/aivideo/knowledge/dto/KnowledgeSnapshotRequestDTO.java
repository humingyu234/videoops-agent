package org.dromara.aivideo.knowledge.dto;

import java.util.List;
import java.util.Objects;

/**
 * 根任务知识快照创建请求。
 */
public record KnowledgeSnapshotRequestDTO(
    Long rootTaskId,
    Long promptVersionId,
    Long generationContextRevision,
    String generationInputHash,
    KnowledgeRouteResultDTO route,
    List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts
) {

    public KnowledgeSnapshotRequestDTO {
        requirePositive(rootTaskId, "根任务编号");
        requirePositive(promptVersionId, "提示词版本编号");
        requirePositive(generationContextRevision, "生成上下文修订号");
        requireNonBlank(generationInputHash, "生成输入哈希");
        route = Objects.requireNonNull(route, "知识路由结果不能为空");
        acceptedFacts = List.copyOf(Objects.requireNonNull(acceptedFacts, "已采纳事实集合不能为空"));
    }

    /**
     * 已采纳事实快照。
     */
    public record AcceptedFactSnapshotDTO(
        Long factId,
        Long decisionRevision,
        String factText,
        String evidenceRef
    ) {

        public AcceptedFactSnapshotDTO {
            requirePositive(factId, "事实编号");
            requirePositive(decisionRevision, "决策修订号");
            requireNonBlank(factText, "事实文本");
            requireNonBlank(evidenceRef, "证据引用");
        }
    }

    private static void requirePositive(Long value, String label) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(label + "必须为正数");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
    }
}
