package org.dromara.aivideo.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** 知识绑定对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_knowledge_binding")
public class KnowledgeBinding extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long knowledgeBindingId;
    private String bindingGroupCode;
    private Integer versionNo;
    private Long knowledgeItemId;
    private Long knowledgeVersionId;
    private String industryCode;
    private String purposeCode;
    private String videoTypeCode;
    private String angleCodesJson;
    private String anglePrioritiesJson;
    private Integer minDurationSeconds;
    private Integer maxDurationSeconds;
    private Integer priority;
    private Boolean requiredFlag;
    private String requiredSlotCodesJson;
    private String audienceTagCodesJson;
    private String exclusionConditionsJson;
    private String status;
}
