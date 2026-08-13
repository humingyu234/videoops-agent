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
 * 创作端角色与权限关联实体。
 */
@Getter
@Setter
@TableName("app_role_permission")
public class AppRolePermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 关联编号。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 角色编号。
     */
    @TableField("role_id")
    private Long roleId;

    /**
     * 权限编号。
     */
    @TableField("permission_id")
    private Long permissionId;

    /**
     * 关联状态。
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
