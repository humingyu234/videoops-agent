package org.dromara.aivideo.user.auth.domain.vo;

import java.time.LocalDateTime;

/**
 * 当前创作端用户可查看的安全会话投影。
 *
 * @param id 随机会话编号
 * @param clientId 创作端认证客户端标识
 * @param deviceName 脱敏后的设备类型
 * @param lastActiveAt 最近活动时间
 * @param current 是否为当前会话
 */
public record AppSessionVo(
    String id,
    String clientId,
    String deviceName,
    LocalDateTime lastActiveAt,
    boolean current
) {
}
