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
 * 创作端只追加登录日志实体。
 */
@Getter
@Setter
@TableName("app_login_log")
public class AppLoginLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 登录日志编号。
     */
    @TableId(value = "login_log_id", type = IdType.ASSIGN_ID)
    private Long loginLogId;

    /**
     * 认证方式。
     */
    @TableField("auth_method")
    private AppAuthMethod authMethod;

    /**
     * 脱敏后的账号标识。
     */
    @TableField("masked_identifier")
    private String maskedIdentifier;

    /**
     * 客户端标识。
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 认证结果编码。
     */
    @TableField("result_code")
    private Integer resultCode;

    /**
     * 失败分类。
     */
    @TableField("failure_category")
    private String failureCategory;

    /**
     * 创作端用户编号。
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 创作端会话编号。
     */
    @TableField("session_id")
    private String sessionId;

    /**
     * 请求 IP 地址。
     */
    @TableField("ip_address")
    private String ipAddress;

    /**
     * 设备摘要。
     */
    @TableField("device_summary")
    private String deviceSummary;

    /**
     * 请求追踪编号。
     */
    @TableField("request_id")
    private String requestId;

    /**
     * 发生时间。
     */
    @TableField("occurred_at")
    private LocalDateTime occurredAt;
}
