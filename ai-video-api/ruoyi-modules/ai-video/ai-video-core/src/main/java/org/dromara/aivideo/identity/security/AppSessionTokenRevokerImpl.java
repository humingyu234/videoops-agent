package org.dromara.aivideo.identity.security;

import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 仅封装 app 登录命名空间的会话撤销操作。
 */
@Component
@ConditionalOnAppSessionRuntimeEnabled
class AppSessionTokenRevokerImpl implements AppSessionTokenRevoker {

    private final AppStpLogicRegistrar registrar;

    AppSessionTokenRevokerImpl(AppStpLogicRegistrar registrar) {
        this.registrar = Objects.requireNonNull(registrar, "创作端登录逻辑注册器不能为空");
    }

    @Override
    public void kickoutUserSessions(long appUserId) {
        if (appUserId > 0) {
            registrar.logic().kickout(appUserId);
        }
    }

    @Override
    public void kickout(AppSessionTokenReference tokenReference) {
        if (tokenReference != null) {
            tokenReference.kickoutWith(registrar.logic());
        }
    }
}
