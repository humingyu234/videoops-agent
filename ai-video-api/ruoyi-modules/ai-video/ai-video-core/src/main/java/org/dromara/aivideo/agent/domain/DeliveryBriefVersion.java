package org.dromara.aivideo.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** Immutable, owner-scoped delivery brief snapshot. */
@Data
@TableName("av_delivery_brief_version")
@AppAuditRequired
public class DeliveryBriefVersion extends BaseEntity {

    @TableId(value = "delivery_brief_version_id", type = IdType.INPUT)
    private Long deliveryBriefVersionId;
    private Long briefId;
    private Long ownerUserId;
    private Long versionNo;
    private Long parentVersionId;
    private String schemaVersion;
    private String deliveryType;
    private String briefJson;
    private String briefHash;
    private String idempotencyKey;
    private String requestDigest;
    private String actorType;
    private Long actorId;
}
