package org.dromara.aivideo.identity.security;

import org.dromara.common.core.exception.ServiceException;

import java.util.Objects;

/**
 * 用户端认证边界一次性返回的创作端访问令牌。
 *
 * <p>该对象只存在于用户端适配模块，不能被运营端依赖或用于会话、审计、日志、业务 DTO。</p>
 *
 * @param loginUser 已建立的创作端会话主体
 * @param accessToken 当前请求中新签发的 app 令牌原文
 * @param expireIn 令牌剩余有效秒数
 */
public record AppIssuedAccessToken(AppLoginUser loginUser, String accessToken, long expireIn) {

    public AppIssuedAccessToken {
        Objects.requireNonNull(loginUser, "创作端登录用户不能为空");
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceException("创作端访问令牌不能为空");
        }
        if (expireIn <= 0) {
            throw new ServiceException("创作端访问令牌有效期无效");
        }
    }

    @Override
    public String toString() {
        return "AppIssuedAccessToken[loginUser=" + loginUser + ", accessToken=***, expireIn=" + expireIn + "]";
    }
}
