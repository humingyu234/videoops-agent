package org.dromara.aivideo.voice.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.time.LocalDateTime;

/** 用户私有声音及其后台转写状态。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_voice")
public class Voice extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "voice_id", type = IdType.ASSIGN_ID)
    private Long voiceId;
    private Long tenantId;
    private String workspaceId;
    private Long ownerId;
    private Long assetId;
    private String idempotencyKey;
    private String uploadFingerprint;
    private String voiceType;
    private String name;
    private String gender;
    private String style;
    private String tagsJson;
    private String note;
    private String transcriptText;
    private String transcriptTimelineJson;
    private String detectedLanguage;
    private Long durationMillis;
    private String transcriptionStatus;
    private String failureCode;
    private String failureMessage;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private String leaseOwner;
    private LocalDateTime leaseExpiresAt;
    private Long recordRevision;
    @TableLogic
    private String delFlag;
}
