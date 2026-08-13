package org.dromara.aivideo.infra.identity.provider;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.dromara.aivideo.infra.identity.AppExternalIdentityConditions;
import org.dromara.aivideo.infra.identity.AppExternalIdentityProperties;
import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.dto.AppExternalIdentityRequestDTO;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppSocialIdentityAuthorizationDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.social.config.properties.SocialProperties;
import org.dromara.common.social.utils.SocialUtils;

import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通过项目统一 {@link SocialUtils} 交换社交身份的创作端适配器。
 *
 * <p>本类不读取用户表、不创建用户，也不签发任何会话。</p>
 */
public class AppSocialIdentityProvider {

    private final AppExternalIdentityProperties properties;
    private final SocialProperties socialProperties;
    private final AppExternalIdentityReplayGuard replayGuard;
    private final AppSocialAuthorizationClient authorizationClient;
    private final Set<String> allowedProviders;

    /**
     * 生产装配构造器，固定使用项目现有的 SocialUtils。
     */
    public AppSocialIdentityProvider(AppExternalIdentityProperties properties, SocialProperties socialProperties,
                                    AppExternalIdentityReplayGuard replayGuard) {
        this(properties, socialProperties, replayGuard, SocialUtils::loginAuth);
    }

    AppSocialIdentityProvider(AppExternalIdentityProperties properties, SocialProperties socialProperties,
                             AppExternalIdentityReplayGuard replayGuard,
                             AppSocialAuthorizationClient authorizationClient) {
        this.properties = properties;
        this.socialProperties = socialProperties;
        this.replayGuard = replayGuard;
        this.authorizationClient = authorizationClient;
        List<String> configuredProviders = properties == null || properties.getSocial() == null
            ? List.of()
            : properties.getSocial().getAllowedProviders();
        this.allowedProviders = configuredProviders == null
            ? Set.of()
            : configuredProviders.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(AppSocialIdentityProvider::normalizeProvider)
                .collect(Collectors.toUnmodifiableSet());
    }

    public AppExternalIdentityChannel channel() {
        return AppExternalIdentityChannel.SOCIAL;
    }

    public AppExternalIdentityDTO exchange(AppExternalIdentityRequestDTO command) {
        if (!(command instanceof AppSocialIdentityAuthorizationDTO socialCommand) || !isOperational()) {
            throw invalidAuthorization();
        }
        String provider = normalizeProvider(socialCommand.provider());
        if (!allowedProviders.contains(provider) || !AppExternalIdentityConditions.isSupportedSocialProvider(provider)) {
            throw invalidAuthorization();
        }
        replayGuard.consumeSocial(provider, socialCommand.authorizationCode(), socialCommand.state());
        AuthResponse<AuthUser> response;
        try {
            response = authorizationClient.exchange(provider, socialCommand.authorizationCode(), socialCommand.state(),
                socialProperties);
        } catch (RuntimeException exception) {
            throw invalidAuthorization();
        }
        if (response == null || !response.ok() || response.getData() == null
            || isBlank(response.getData().getSource()) || isBlank(response.getData().getUuid())) {
            throw invalidAuthorization();
        }
        String returnedProvider = normalizeProvider(response.getData().getSource());
        if (!provider.equals(returnedProvider) || !allowedProviders.contains(returnedProvider)) {
            throw invalidAuthorization();
        }
        return new AppExternalIdentityDTO(returnedProvider, response.getData().getUuid());
    }

    private boolean isOperational() {
        return properties != null
            && properties.isOperational()
            && properties.getSocial() != null
            && properties.getSocial().isEnabled()
            && !allowedProviders.isEmpty()
            && socialProperties != null
            && replayGuard != null
            && authorizationClient != null;
    }

    private static String normalizeProvider(String provider) {
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ServiceException invalidAuthorization() {
        return new ServiceException("第三方授权无效");
    }
}

/**
 * 供适配器内部替换的 SocialUtils 调用点，避免向领域端口泄露 JustAuth 类型。
 */
@FunctionalInterface
interface AppSocialAuthorizationClient {

    AuthResponse<AuthUser> exchange(String provider, String authorizationCode, String state,
                                    SocialProperties socialProperties);
}
