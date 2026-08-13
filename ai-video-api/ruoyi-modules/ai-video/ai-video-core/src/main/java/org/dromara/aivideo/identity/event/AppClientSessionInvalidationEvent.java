package org.dromara.aivideo.identity.event;

import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.common.core.exception.ServiceException;

/**
 * 认证客户端策略或密钥变更后的 app 会话失效事件。
 *
 * @param clientId 创作端认证客户端标识
 * @param reason 固定的会话失效原因
 */
public record AppClientSessionInvalidationEvent(String clientId, AppSessionInvalidationReason reason) {

    public AppClientSessionInvalidationEvent {
        if (clientId == null || clientId.isBlank() || reason != AppSessionInvalidationReason.CLIENT_CHANGED) {
            throw new ServiceException("创作端客户端会话失效事件参数无效");
        }
    }

    /**
     * 创建客户端策略变更事件。
     *
     * @param clientId 创作端认证客户端标识
     * @return 客户端范围会话失效事件
     */
    public static AppClientSessionInvalidationEvent clientChanged(String clientId) {
        return new AppClientSessionInvalidationEvent(clientId, AppSessionInvalidationReason.CLIENT_CHANGED);
    }
}
