package org.dromara.aivideo.knowledge.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 不可变的根任务知识快照。
 */
public record KnowledgeSnapshotDTO(
    Long snapshotId,
    Long rootTaskId,
    Long promptVersionId,
    Long generationContextRevision,
    String generationInputHash,
    KnowledgeRouteResultDTO route,
    List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts,
    List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials,
    String contentHash,
    Instant createdAt
) {

    public KnowledgeSnapshotDTO {
        requirePositive(snapshotId, "快照编号");
        requirePositive(rootTaskId, "根任务编号");
        requirePositive(promptVersionId, "提示词版本编号");
        requirePositive(generationContextRevision, "生成上下文修订号");
        requireNonBlank(generationInputHash, "生成输入哈希");
        route = Objects.requireNonNull(route, "知识路由结果不能为空");
        acceptedFacts = List.copyOf(Objects.requireNonNull(acceptedFacts, "已采纳事实集合不能为空"));
        knowledgeMaterials = List.copyOf(Objects.requireNonNull(knowledgeMaterials, "知识材料集合不能为空"));
        requireNonBlank(contentHash, "内容哈希");
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
    }

    /**
     * 注入快照的知识材料。
     */
    public record KnowledgeMaterialSnapshotDTO(
        Long knowledgeVersionId,
        Long bindingVersionId,
        Long videoRuleVersionId,
        String contentExcerpt,
        Integer injectionOrder
    ) {

        public KnowledgeMaterialSnapshotDTO {
            requirePositive(knowledgeVersionId, "知识版本编号");
            requirePositive(bindingVersionId, "绑定版本编号");
            requirePositive(videoRuleVersionId, "视频规则版本编号");
            requireNonBlank(contentExcerpt, "内容摘录");
            if (injectionOrder == null || injectionOrder <= 0) {
                throw new IllegalArgumentException("注入顺序必须为正数");
            }
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
