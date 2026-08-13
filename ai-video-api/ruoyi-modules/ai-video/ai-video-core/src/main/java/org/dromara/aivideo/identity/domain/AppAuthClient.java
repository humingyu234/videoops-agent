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
 * 创作端独立认证客户端实体。
 */
@Getter
@Setter
@TableName("app_auth_client")
public class AppAuthClient implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 认证客户端编号。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 客户端标识。
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 客户端键。
     */
    @TableField("client_key")
    private String clientKey;

    /**
     * 客户端密钥摘要。
     */
    @TableField("client_secret_hash")
    private String clientSecretHash;

    /**
     * 允许的授权类型。
     */
    @TableField("grant_types")
    private String grantTypes;

    /**
     * 允许访问路径。
     */
    @TableField("access_paths")
    private String accessPaths;

    /**
     * IP 白名单。
     */
    @TableField("ip_whitelist")
    private String ipWhitelist;

    /**
     * 令牌固定超时秒数。
     */
    @TableField("token_timeout")
    private Long tokenTimeout;

    /**
     * 令牌活跃超时秒数。
     */
    @TableField("active_timeout")
    private Long activeTimeout;

    /**
     * 客户端修订号。
     */
    @TableField("client_revision")
    private Long clientRevision;

    /**
     * 客户端状态。
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
