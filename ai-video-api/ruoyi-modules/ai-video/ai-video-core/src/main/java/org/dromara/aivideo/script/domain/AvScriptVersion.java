package org.dromara.aivideo.script.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 不可变文案版本。 */
@Data
@TableName("av_script_version")
public class AvScriptVersion {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ownerType;
    private Long ownerId;
    private Long createdByUserId;
    private Long scriptId;
    private Long parentVersionId;
    private Integer versionNo;
    private String sourceType;
    private String scriptText;
    private Integer effectiveCharacterCount;
    private Integer estimatedDurationSeconds;
    private Integer effectiveCharsPerMinute;
    private String ruleConfigVersionsJson;
    private String manualIdempotencyKey;
    private String manualRequestHash;
    private String resultDisplayTitle;
    private Long resultScriptRevision;
    private LocalDateTime createdAt;
}
