package org.dromara.aivideo.identity.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创作端独立用户实体。
 */
@Getter
@Setter
@TableName("app_user")
public class AppUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创作端用户编号。
     */
    @TableId(value = "user_id", type = IdType.ASSIGN_ID)
    private Long userId;

    /**
     * 用户名。
     */
    @TableField("username")
    private String username;

    /**
     * 标准化用户名。
     */
    @TableField("username_normalized")
    private String usernameNormalized;

    /**
     * 密码摘要。
     */
    @TableField("password_hash")
    private String passwordHash;

    /**
     * 标准化手机号。
     */
    @TableField("phone_normalized")
    private String phoneNormalized;

    /**
     * 标准化邮箱。
     */
    @TableField("email_normalized")
    private String emailNormalized;

    /**
     * 个人租户编号。
     */
    @TableField("personal_tenant_id")
    private Long personalTenantId;

    /**
     * 显示名称。
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 用户状态。
     */
    @TableField("status")
    private AppIdentityStatus status;

    /**
     * 是否必须修改密码。
     */
    @TableField("must_change_password")
    private Boolean mustChangePassword;

    /**
     * 凭据修订号。
     */
    @TableField("credential_revision")
    private Long credentialRevision;

    /**
     * 身份修订号。
     */
    @TableField("identity_revision")
    private Long identityRevision;

    /**
     * 权限修订号。
     */
    @TableField("permission_revision")
    private Long permissionRevision;

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

    /**
     * 删除标志。
     */
    @TableField("del_flag")
    @TableLogic
    private String delFlag;
}
