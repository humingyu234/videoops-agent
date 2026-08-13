package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户端认证边界受限的 app 会话签发器。
 *
 * <p>该类只存在于 {@code ai-video-user} 模块；运营端的 classpath 与 Spring 上下文均不装配它。</p>
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppAuthenticationSessionIssuer {

    private final AppLoginHelper loginHelper;

    public AppAuthenticationSessionIssuer(AppLoginHelper loginHelper) {
        this.loginHelper = Objects.requireNonNull(loginHelper, "创作端登录助手不能为空");
    }

    /**
     * 建立 app 会话并一次性取得认证响应所需的令牌和有效期。
     *
     * @param principal 已由用户端身份和客户端事实源构造的可信主体快照
     * @param deviceType 受控设备类型
     * @return 仅供用户端认证边界立即响应的令牌结果
     */
    public AppIssuedAccessToken issue(AppPrincipalSnapshotDTO principal, String deviceType) {
        AppLoginUser loginUser = loginHelper.login(principal, deviceType);
        try {
            String accessToken = loginHelper.logic().getTokenValue();
            long expireIn = loginHelper.getCurrentTokenTimeout();
            return new AppIssuedAccessToken(loginUser, accessToken, expireIn);
        } catch (RuntimeException | Error exception) {
            revokeCurrentAppSession(exception);
            throw exception;
        }
    }

    private void revokeCurrentAppSession(Throwable original) {
        try {
            loginHelper.logout();
        } catch (RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }
}
