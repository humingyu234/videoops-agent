package org.dromara.aivideo.identity.security;

/**
 * 用户端请求上下文中读取或更新既有 app 会话所需的最小能力。
 *
 * <p>该接口不包含登录、令牌签发或令牌原文读取能力，因此运营端可以装配会话管理服务而不装配用户端登录助手。</p>
 */
public interface AppSessionRequestAccess {

    boolean isLogin();

    AppLoginUser getLoginUser();

    void replaceCurrentLoginUser(AppLoginUser loginUser);

    long getCurrentSessionIndexTimeout();
}
