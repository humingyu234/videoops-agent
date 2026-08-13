package org.dromara.aivideo.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/** 知识版本对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_knowledge_version")
public class KnowledgeVersion extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long knowledgeVersionId;
    private Long knowledgeItemId;
    private Integer versionNo;
    private String status;
    private String content;
    private String structureJson;
    private String sourceSummary;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private Long publishedBy;
    private LocalDateTime publishedAt;
}
