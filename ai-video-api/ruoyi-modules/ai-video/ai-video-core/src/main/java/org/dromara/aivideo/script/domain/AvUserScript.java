package org.dromara.aivideo.script.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户私有文案主体。 */
@Data
@TableName("av_user_script")
public class AvUserScript {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long tenantId;
    private String ownerType;
    private Long ownerId;
    private Long createdByUserId;
    private Long draftId;
    private String displayTitle;
    private Long currentVersionId;
    private Long currentConfirmedVersionId;
    private String createIdempotencyKey;
    private String createRequestHash;
    private Long scriptRevision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private String deleted;
}
