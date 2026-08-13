package org.dromara.aivideo.identity.security;

/**
 * app 会话撤销的最小运行时能力。
 *
 * <p>运营端只依赖此接口撤销既有 app 会话；接口不提供登录、签发或令牌原文读取方法。</p>
 */
public interface AppSessionTokenRevoker {

    void kickoutUserSessions(long appUserId);

    void kickout(AppSessionTokenReference tokenReference);
}
