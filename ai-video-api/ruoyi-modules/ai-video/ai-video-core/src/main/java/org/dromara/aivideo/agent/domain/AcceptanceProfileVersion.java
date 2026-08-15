package org.dromara.aivideo.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

/** Immutable, owner-scoped acceptance preference snapshot. */
@Data
@TableName("av_acceptance_profile_version")
@AppAuditRequired
public class AcceptanceProfileVersion extends BaseEntity {

    @TableId(value = "acceptance_profile_version_id", type = IdType.INPUT)
    private Long acceptanceProfileVersionId;
    private Long acceptanceProfileId;
    private Long ownerUserId;
    private Long deliveryBriefVersionId;
    private Long versionNo;
    private Long parentVersionId;
    private String schemaVersion;
    private String policyVersion;
    private String profileJson;
    private String profileHash;
    private String idempotencyKey;
    private String requestDigest;
    private String actorType;
    private Long actorId;
}
