package org.dromara.aivideo.user.auth.domain.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 创作端登录成功响应。
 *
 * @param accessToken 仅本次响应返回的 app 访问令牌
 * @param clientId 已验证创作端认证客户端标识
 * @param expireIn 令牌剩余有效秒数
 * @param currentWorkspace 当前默认工作区摘要
 */
public record AppLoginVo(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("client_id") String clientId,
    @JsonProperty("expire_in") long expireIn,
    AppWorkspaceVo currentWorkspace
) {

    @Override
    public String toString() {
        return "AppLoginVo[accessToken=***, clientId=" + clientId + ", expireIn=" + expireIn
            + ", currentWorkspace=" + currentWorkspace + "]";
    }
}
