package org.dromara.aivideo.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** 知识条目对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_knowledge_item")
public class KnowledgeItem extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long knowledgeItemId;
    private String domainCode;
    private String knowledgeTypeCode;
    private String stableCode;
    private String name;
    private String summary;
    private String tagsJson;
    private Long currentPublishedVersionId;
    private String sourceType;
    private String sourceRef;
}
