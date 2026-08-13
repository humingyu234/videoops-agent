package org.dromara.aivideo.timeline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_timeline_asset_ref") @AppAuditRequired
public class TimelineAssetRef extends BaseEntity {
    @TableId(value = "timeline_asset_ref_id", type = IdType.INPUT) private Long timelineAssetRefId;
    private Long ownerUserId; private Long projectId; private String documentType; private Long documentId;
    private String elementId; private Long assetId; private String usageType; private Long startMs; private Long endMs;
    private String actorType; private Long actorId;
}
