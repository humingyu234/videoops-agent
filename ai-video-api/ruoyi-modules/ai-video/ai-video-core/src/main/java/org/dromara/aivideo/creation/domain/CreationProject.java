package org.dromara.aivideo.creation.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.dromara.common.mybatis.audit.AppAuditRequired;
import org.dromara.common.mybatis.core.domain.BaseEntity;

@Data @TableName("av_creation_project") @AppAuditRequired
public class CreationProject extends BaseEntity {
    @TableId(value = "project_id", type = IdType.INPUT) private Long projectId;
    private Long ownerUserId; private String projectTitle; private String idempotencyKey; private String requestDigest;
    private String sourceType; private Long sourceRefId; private Long baseVideoAssetId; private Long primaryAudioAssetId;
    private String scriptTextSnapshot; private Integer canvasWidth; private Integer canvasHeight; private Integer frameRate;
    private Long durationMs; private String projectStatus; private Long currentOutputAssetId; private String actorType;
    private Long actorId; @TableLogic private String delFlag;
}
