package org.dromara.aivideo.platform.knowledge.domain.vo;

import java.time.LocalDateTime;

/** 运营端知识条目列表行。 */
public record KnowledgeItemAdminVo(
    Long id,
    String name,
    String knowledgeType,
    String status,
    Integer versionNo,
    LocalDateTime updateTime
) {
}
