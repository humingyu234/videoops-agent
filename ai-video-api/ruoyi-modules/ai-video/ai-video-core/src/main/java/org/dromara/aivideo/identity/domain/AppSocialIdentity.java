package org.dromara.aivideo.identity.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创作端第三方身份绑定实体。
 */
@Getter
@Setter
@TableName("app_social_identity")
public class AppSocialIdentity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 第三方身份编号。
     */
    @TableId(value = "social_identity_id", type = IdType.ASSIGN_ID)
    private Long socialIdentityId;

    /**
     * 创作端用户编号。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 第三方提供方。
     */
    @TableField("provider")
    private String provider;

    /**
     * 第三方主体标识。
     */
    @TableField("provider_subject")
    private String providerSubject;

    /**
     * 绑定状态。
     */
    @TableField("status")
    private AppIdentityStatus status;

    /**
     * 创建操作者类型。
     */
    @TableField("created_by_type")
    private AppActorType createdByType;

    /**
     * 创建操作者编号。
     */
    @TableField("created_by_id")
    private Long createdById;

    /**
     * 更新操作者类型。
     */
    @TableField("updated_by_type")
    private AppActorType updatedByType;

    /**
     * 更新操作者编号。
     */
    @TableField("updated_by_id")
    private Long updatedById;

    /**
     * 创建时间。
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}
