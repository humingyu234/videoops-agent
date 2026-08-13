package org.dromara.aivideo.platform.knowledge.domain.vo;

import java.time.LocalDateTime;

/** 运营端知识详情。 */
public record KnowledgeItemDetailVo(
    Long id,
    String name,
    String knowledgeType,
    String status,
    Integer versionNo,
    String summary,
    String content,
    LocalDateTime updateTime
) {
}
