package org.dromara.aivideo.identity.event;

import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppSessionTokenReference;

import java.util.Objects;

/**
 * 创作端 app 登录令牌会话已建立后的同步内部事件。
 * <p>
 * 事件只携带服务端不透明令牌引用，绝不传递令牌原文；监听器可据此写入或撤销在线会话索引。
 *
 * @param loginUser 已写入 app 令牌会话的创作端登录用户
 * @param device 已脱敏的设备类型
 * @param tokenReference app 令牌服务端不透明引用
 * @param tokenTimeout app 令牌剩余有效秒数，负数表示永不过期
 */
public record AppSessionEstablishedEvent(
    AppLoginUser loginUser,
    String device,
    AppSessionTokenReference tokenReference,
    long tokenTimeout
) {

    /**
     * 校验同步事件的必要字段，避免监听器写入无法关联的在线索引。
     */
    public AppSessionEstablishedEvent {
        loginUser = Objects.requireNonNull(loginUser, "创作端登录用户不能为空");
        if (device == null || device.isBlank()) {
            throw new IllegalArgumentException("创作端设备类型不能为空");
        }
        tokenReference = Objects.requireNonNull(tokenReference, "创作端令牌引用不能为空");
    }
}
