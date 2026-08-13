package org.dromara.aivideo.identity.dto;

import java.time.LocalDateTime;

/**
 * 不含令牌原文的创作端会话公开摘要。
 *
 * @param sessionId 随机公开会话编号
 * @param clientId 创作端认证客户端标识
 * @param device 脱敏后的设备标识
 * @param lastActiveTime 最近活动时间
 * @param current 是否为当前 app 会话
 * @param appUserId 会话归属的创作端用户编号；运营端投影使用，用户端不公开
 */
public record AppSessionSummaryDTO(
    String sessionId,
    String clientId,
    String device,
    LocalDateTime lastActiveTime,
    boolean current,
    Long appUserId
) {

    /**
     * 兼容用户端仅展示自身会话的既有投影构造方式。
     */
    public AppSessionSummaryDTO(String sessionId, String clientId, String device, LocalDateTime lastActiveTime,
                             boolean current) {
        this(sessionId, clientId, device, lastActiveTime, current, null);
    }
}
