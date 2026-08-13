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
 * 创作端只追加安全审计实体。
 */
@Getter
@Setter
@TableName("app_security_audit")
public class AppSecurityAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 审计编号。
     */
    @TableId(value = "audit_id", type = IdType.ASSIGN_ID)
    private Long auditId;

    /**
     * 被操作资源类型。
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 被操作资源编号。
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 安全动作。
     */
    @TableField("action")
    private String action;

    /**
     * 操作者类型。
     */
    @TableField("actor_type")
    private AppActorType actorType;

    /**
     * 操作者编号。
     */
    @TableField("actor_id")
    private Long actorId;

    /**
     * 变更前摘要。
     */
    @TableField("before_digest")
    private String beforeDigest;

    /**
     * 变更后摘要。
     */
    @TableField("after_digest")
    private String afterDigest;

    /**
     * 操作原因。
     */
    @TableField("reason")
    private String reason;

    /**
     * 请求追踪编号。
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 请求 IP 地址。
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 发生时间。
     */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
