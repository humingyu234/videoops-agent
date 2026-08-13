package org.dromara.aivideo.timeline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_timeline_draft") @AppAuditRequired
public class TimelineDraft extends BaseEntity {
    @TableId(value = "timeline_draft_id", type = IdType.INPUT) private Long timelineDraftId;
    private Long ownerUserId; private Long projectId; private Long revision; private String schemaVersion;
    private String contentJson; private String contentHash; private Long durationMs; private String actorType;
    private Long actorId; @TableLogic private String delFlag;
}
