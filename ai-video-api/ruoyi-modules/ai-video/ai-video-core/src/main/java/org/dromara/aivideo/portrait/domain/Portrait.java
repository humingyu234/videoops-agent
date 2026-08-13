package org.dromara.aivideo.portrait.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;

/** 用户人物形象。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("av_portrait")
public class Portrait extends BaseEntity {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(value = "portrait_id", type = IdType.ASSIGN_ID)
    private Long portraitId;
    private Long tenantId;
    private String workspaceId;
    private Long ownerId;
    private Long assetId;
    private String name;
    private String gender;
    private String sceneTagsJson;
    private String note;
    private String idempotencyKey;
    private String requestDigest;
    @Version
    private Long recordRevision;
    @TableLogic
    private String delFlag;
}
