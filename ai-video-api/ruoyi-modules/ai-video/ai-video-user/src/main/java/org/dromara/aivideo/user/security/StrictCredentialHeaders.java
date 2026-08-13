package org.dromara.aivideo.user.security;

import java.util.Objects;

/**
 * 已通过单一请求头校验的创作端凭据元数据。
 *
 * <p>该对象刻意不保存令牌原文，避免将认证秘密放入 request attribute。</p>
 */
public final class StrictCredentialHeaders {

    /**
     * 请求属性键。
     */
    public static final String REQUEST_ATTRIBUTE = StrictCredentialHeaders.class.getName();

    private final String clientId;
    private final boolean authorizationPresent;

    /**
     * 创建已经完成入口校验的凭据元数据。
     *
     * @param clientId 唯一的创作端客户端键
     * @param authorizationPresent 是否携带唯一且格式正确的 Authorization 请求头
     */
    public StrictCredentialHeaders(String clientId, boolean authorizationPresent) {
        this.clientId = Objects.requireNonNull(clientId, "创作端客户端键不能为空");
        this.authorizationPresent = authorizationPresent;
    }

    /**
     * 返回创作端客户端键。
     *
     * @return clientid 请求头值
     */
    public String clientId() {
        return clientId;
    }

    /**
     * 判断请求是否带有唯一且格式正确的 Bearer 凭据。
     *
     * @return 已携带 Bearer 凭据时返回 true
     */
    public boolean hasAuthorization() {
        return authorizationPresent;
    }

    /**
     * 避免日志框架意外输出令牌原文。
     *
     * @return 脱敏后的调试表示
     */
    @Override
    public String toString() {
        return "StrictCredentialHeaders[clientId=" + clientId
            + ", authorizationPresent=" + authorizationPresent + ']';
    }
}
