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
 * 创作端角色实体。
 */
@Getter
@Setter
@TableName("app_role")
public class AppRole implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 角色编号。
     */
    @TableId(value = "role_id", type = IdType.ASSIGN_ID)
    private Long roleId;

    /**
     * 角色编码。
     */
    @TableField("role_code")
    private String roleCode;

    /**
     * 角色名称。
     */
    @TableField("role_name")
    private String roleName;

    /**
     * 作用域类型。
     */
    @TableField("scope_type")
    private String scopeType;

    /**
     * 是否为内置角色。
     */
    @TableField("built_in")
    private Boolean builtIn;

    /**
     * 角色修订号。
     */
    @TableField("role_revision")
    private Long roleRevision;

    /**
     * 角色状态。
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

    /**
     * 删除标志。
     */
    @TableField("del_flag")
    @TableLogic
    private String delFlag;
}
