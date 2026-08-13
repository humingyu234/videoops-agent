package org.dromara.aivideo.identity.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 仅供服务端会话索引持有的 app 令牌不透明引用。
 *
 * <p>该类型不提供令牌原文读取方法；令牌撤销只能委派给 {@link AppSessionTokenRevoker}。</p>
 */
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.ANY,
    getterVisibility = JsonAutoDetect.Visibility.NONE,
    isGetterVisibility = JsonAutoDetect.Visibility.NONE,
    setterVisibility = JsonAutoDetect.Visibility.NONE
)
public final class AppSessionTokenReference implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("tokenValue")
    private final String tokenValue;

    /**
     * 由 app 登录助手创建令牌服务端引用。
     *
     * @param tokenValue app 令牌原文
     */
    @JsonCreator
    AppSessionTokenReference(@JsonProperty("tokenValue") String tokenValue) {
        this.tokenValue = Objects.requireNonNull(tokenValue, "创作端令牌不能为空");
    }

    /**
     * 委派 app 登录逻辑强制令牌下线，不向调用方暴露令牌原文。
     *
     * @param logic 创作端 app 登录逻辑
     */
    void kickoutWith(AppStpLogic logic) {
        Objects.requireNonNull(logic, "创作端登录逻辑不能为空").kickoutByTokenValue(tokenValue);
    }
}
