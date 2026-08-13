package org.dromara.aivideo.timeline.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_timeline_write_receipt") @AppAuditRequired
public class TimelineWriteReceipt extends BaseEntity {
    @TableId(value = "timeline_write_receipt_id", type = IdType.INPUT) private Long timelineWriteReceiptId;
    private Long ownerUserId; private Long projectId; private String operationType; private String idempotencyKey;
    private String requestDigest; private Long expectedRevision; private Long resultRevision; private Long resultVersionId;
    private String responseSummaryJson; private String actorType; private Long actorId;
}
