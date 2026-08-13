package org.dromara.aivideo.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.time.LocalDateTime;

/** 视频类型规则对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_video_type_rule")
public class VideoTypeRule extends BaseEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long videoTypeRuleId;
    private String ruleCode;
    private Integer versionNo;
    private String videoTypeCode;
    private String industryCode;
    private String purposeCode;
    private Integer minDurationSeconds;
    private Integer maxDurationSeconds;
    private String requiredSlotCodesJson;
    private Integer priority;
    private String copyRulesJson;
    private String status;
    private LocalDateTime publishedAt;
}
