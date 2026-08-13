package org.dromara.aivideo.creation.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data
@TableName("av_creation_asset")
@AppAuditRequired
public class CreationAsset extends BaseEntity {
    @TableId(value = "asset_id", type = IdType.INPUT) private Long assetId;
    private Long ownerUserId; private String assetType; private String usageOrigin; private Long sourceRefId;
    private String assetStatus; private String storageKey; private String mimeType; private Long sizeBytes;
    private String sha256; private Long durationMs; private Integer width; private Integer height;
    private Boolean hasVideoStream; private Boolean hasAudioStream; private String idempotencyKey;
    private String requestDigest; private String actorType; private Long actorId; @TableLogic private String delFlag;
}
