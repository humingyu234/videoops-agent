package org.dromara.aivideo.identity.event;

import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 创作端会话需要在业务事务提交后失效的领域事件。
 *
 * <p>该事件不依赖具体会话实现；独立 app 会话的实际清理由后续认证运行时监听。</p>
 *
 * @param appUserIds 需要失效会话的创作端用户编号集合
 * @param reason 会话失效原因
 */
public record AppSessionInvalidationEvent(Set<Long> appUserIds, AppSessionInvalidationReason reason) {

    /**
     * 校验事件参数并冻结用户集合。
     */
    public AppSessionInvalidationEvent {
        Objects.requireNonNull(appUserIds, "创作端用户编号集合不能为空");
        Objects.requireNonNull(reason, "会话失效原因不能为空");
        if (appUserIds.isEmpty() || appUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)) {
            throw new IllegalArgumentException("创作端用户编号集合不合法");
        }
        appUserIds = Set.copyOf(new LinkedHashSet<>(appUserIds));
    }

    /**
     * 为指定创作端用户创建会话失效事件。
     *
     * @param appUserIds 需要失效会话的创作端用户编号集合
     * @param reason 会话失效原因
     * @return 不可变会话失效事件
     */
    public static AppSessionInvalidationEvent forUsers(Set<Long> appUserIds,
                                                        AppSessionInvalidationReason reason) {
        return new AppSessionInvalidationEvent(appUserIds, reason);
    }
}
