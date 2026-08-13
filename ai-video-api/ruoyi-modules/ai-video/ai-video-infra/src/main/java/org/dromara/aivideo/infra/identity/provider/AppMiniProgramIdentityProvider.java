package org.dromara.aivideo.infra.identity.provider;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;
import org.dromara.aivideo.infra.identity.AppExternalIdentityProperties;
import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.dto.AppExternalIdentityRequestDTO;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppMiniProgramAuthorizationDTO;
import org.dromara.common.core.exception.ServiceException;

import java.util.function.Function;

/**
 * 微信小程序外部身份适配器。
 *
 * <p>只从微信响应中保留固定来源 {@code wechat_mini_program} 和 {@code openId}，不透传 token、unionId 或用户资料。</p>
 */
public class AppMiniProgramIdentityProvider {

    private static final String PROVIDER = "wechat_mini_program";

    private final AppExternalIdentityProperties properties;
    private final AppExternalIdentityReplayGuard replayGuard;
    private final Function<AuthConfig, AuthRequest> requestFactory;

    /**
     * 生产装配构造器。
     */
    public AppMiniProgramIdentityProvider(AppExternalIdentityProperties properties,
                                         AppExternalIdentityReplayGuard replayGuard) {
        this(properties, replayGuard, AuthWechatMiniProgramRequest::new);
    }

    AppMiniProgramIdentityProvider(AppExternalIdentityProperties properties,
                                  AppExternalIdentityReplayGuard replayGuard,
                                  Function<AuthConfig, AuthRequest> requestFactory) {
        this.properties = properties;
        this.replayGuard = replayGuard;
        this.requestFactory = requestFactory;
    }

    public AppExternalIdentityChannel channel() {
        return AppExternalIdentityChannel.MINI_PROGRAM;
    }

    public AppExternalIdentityDTO exchange(AppExternalIdentityRequestDTO command) {
        if (!(command instanceof AppMiniProgramAuthorizationDTO miniProgramCommand) || !isOperational()) {
            throw invalidAuthorization();
        }
        replayGuard.consumeMiniProgram(miniProgramCommand.authorizationCode());
        AuthResponse<AuthUser> response;
        try {
            AuthRequest request = requestFactory.apply(AuthConfig.builder()
                .clientId(properties.getMiniProgram().getAppId())
                .clientSecret(properties.getMiniProgram().getAppSecret())
                .ignoreCheckRedirectUri(true)
                .ignoreCheckState(true)
                .build());
            if (request == null) {
                throw invalidAuthorization();
            }
            AuthCallback callback = new AuthCallback();
            callback.setCode(miniProgramCommand.authorizationCode());
            response = request.login(callback);
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidAuthorization();
        }
        if (response == null || !response.ok() || response.getData() == null
            || response.getData().getToken() == null || isBlank(response.getData().getToken().getOpenId())) {
            throw invalidAuthorization();
        }
        return new AppExternalIdentityDTO(PROVIDER, response.getData().getToken().getOpenId());
    }

    private boolean isOperational() {
        return properties != null
            && properties.isOperational()
            && properties.getMiniProgram() != null
            && properties.getMiniProgram().isEnabled()
            && !isBlank(properties.getMiniProgram().getAppId())
            && !isBlank(properties.getMiniProgram().getAppSecret())
            && replayGuard != null
            && requestFactory != null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ServiceException invalidAuthorization() {
        return new ServiceException("小程序授权无效");
    }
}
