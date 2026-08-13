package org.dromara.aivideo.timeline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_timeline_version") @AppAuditRequired
public class TimelineVersion extends BaseEntity {
    @TableId(value = "timeline_version_id", type = IdType.INPUT) private Long timelineVersionId;
    private Long ownerUserId; private Long projectId; private Long versionNo; private Long sourceDraftRevision;
    private String versionReason; private String idempotencyKey; private String requestDigest; private String schemaVersion;
    private String contentJson; private String contentHash; private Long durationMs; private Long sourceVersionId;
    private String actorType; private Long actorId;
}
