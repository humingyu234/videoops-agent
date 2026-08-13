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
 * 创作端权限注册实体。
 */
@Getter
@Setter
@TableName("app_permission")
public class AppPermission implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 权限编号。
     */
    @TableId(value = "permission_id", type = IdType.ASSIGN_ID)
    private Long permissionId;

    /**
     * 权限编码。
     */
    @TableField("permission_code")
    private String permissionCode;

    /**
     * 权限名称。
     */
    @TableField("permission_name")
    private String permissionName;

    /**
     * 资源类型。
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 操作类型。
     */
    @TableField("action")
    private String action;

    /**
     * 权限修订号。
     */
    @TableField("permission_revision")
    private Long permissionRevision;

    /**
     * 权限状态。
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
