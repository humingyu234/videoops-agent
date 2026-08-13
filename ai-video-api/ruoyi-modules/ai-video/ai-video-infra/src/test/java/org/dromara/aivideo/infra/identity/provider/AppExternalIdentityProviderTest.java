package org.dromara.aivideo.infra.identity.provider;

import me.zhyd.oauth.enums.AuthResponseStatus;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import org.dromara.aivideo.infra.identity.AppExternalIdentityConfiguration;
import org.dromara.aivideo.infra.identity.AppExternalIdentityProperties;
import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.service.IAppExternalIdentityService;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppMiniProgramAuthorizationDTO;
import org.dromara.aivideo.identity.dto.AppSocialIdentityAuthorizationDTO;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.social.config.properties.SocialLoginConfigProperties;
import org.dromara.common.social.config.properties.SocialProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 创作端社交与微信小程序身份适配的隔离和敏感值保护测试。
 */
@Tag("dev")
class AppExternalIdentityProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AppExternalIdentityConfiguration.class)
        .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
        .withBean(SocialProperties.class, SocialProperties::new);

    @Test
    void doesNotRegisterExternalIdentityPortsUntilEachChannelIsExplicitlyAndCompletelyConfigured() {
        contextRunner.run(context -> assertThat(context.getBeansOfType(IAppExternalIdentityService.class)).isEmpty());

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.external-identity.enabled=true",
            "app.security.external-identity.replay-hmac-secret=0123456789abcdef0123456789abcdef",
            "app.security.external-identity.social.enabled=true",
            "app.security.external-identity.social.allowed-providers[0]=github")
            .run(context -> assertThat(context.getBeansOfType(IAppExternalIdentityService.class)).isEmpty());

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.external-identity.enabled=true",
            "app.security.external-identity.replay-hmac-secret=0123456789abcdef0123456789abcdef",
            "app.security.external-identity.mini-program.enabled=true",
            "app.security.external-identity.mini-program.app-id=creator-mini")
            .run(context -> assertThat(context.getBeansOfType(IAppExternalIdentityService.class)).isEmpty());
    }

    @Test
    void registersOnlyTheExplicitlyConfiguredExternalIdentityChannel() {
        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.external-identity.enabled=true",
            "app.security.external-identity.replay-hmac-secret=0123456789abcdef0123456789abcdef",
            "app.security.external-identity.social.enabled=true",
            "app.security.external-identity.social.allowed-providers[0]=github",
            "justauth.type.github.client-id=creator-github",
            "justauth.type.github.client-secret=creator-github-secret",
            "justauth.type.github.redirect-uri=https://creator.example.test/auth/callback")
            .run(context -> {
                assertThat(context).hasBean("appSocialIdentityGateway");
                assertThat(context).doesNotHaveBean("appMiniProgramIdentityGateway");
                assertThat(context.getBean("appSocialIdentityGateway", IAppExternalIdentityService.class).channel())
                    .isEqualTo(AppExternalIdentityChannel.SOCIAL);
            });

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.external-identity.enabled=true",
            "app.security.external-identity.replay-hmac-secret=0123456789abcdef0123456789abcdef",
            "app.security.external-identity.mini-program.enabled=true",
            "app.security.external-identity.mini-program.app-id=creator-mini",
            "app.security.external-identity.mini-program.app-secret=creator-mini-secret")
            .run(context -> {
                assertThat(context).doesNotHaveBean("appSocialIdentityGateway");
                assertThat(context).hasBean("appMiniProgramIdentityGateway");
                assertThat(context.getBean("appMiniProgramIdentityGateway", IAppExternalIdentityService.class).channel())
                    .isEqualTo(AppExternalIdentityChannel.MINI_PROGRAM);
            });
    }

    @Test
    void resolvesSocialIdentityOnlyToTheSocialUtilsProviderAndSubjectAfterReplayReservation() {
        AppExternalIdentityReplayGuard replayGuard = acceptingReplayGuard();
        SocialProperties socialProperties = socialProperties();
        AppSocialIdentityProvider gateway = new AppSocialIdentityProvider(externalIdentityProperties(), socialProperties,
            replayGuard, (provider, code, state, properties) -> successfulSocialResponse());
        AppSocialIdentityAuthorizationDTO command = new AppSocialIdentityAuthorizationDTO(
            "github", "social-code-secret", "social-state-secret");

        AppExternalIdentityDTO result = gateway.exchange(command);

        assertThat(result.provider()).isEqualTo("github");
        assertThat(result.providerSubject()).isEqualTo("github-subject-42");
        assertThat(result.toString()).doesNotContain("github-subject-42");
        verify(replayGuard).consumeSocial("github", "social-code-secret", "social-state-secret");
    }

    @Test
    void rejectsSocialProviderResponsesThatDoNotMatchTheRequestedAllowlistedProviderWithoutLeakingSecrets() {
        AppExternalIdentityReplayGuard replayGuard = acceptingReplayGuard();
        AppSocialIdentityProvider gateway = new AppSocialIdentityProvider(externalIdentityProperties(), socialProperties(),
            replayGuard, (provider, code, state, properties) -> successfulSocialResponse("gitee", "social-subject"));

        assertThatThrownBy(() -> gateway.exchange(new AppSocialIdentityAuthorizationDTO(
            "github", "social-code-secret", "social-state-secret")))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方授权无效")
            .hasMessageNotContaining("social-code-secret")
            .hasMessageNotContaining("social-state-secret");
    }

    @Test
    void resolvesMiniProgramIdentityOnlyToTheFixedProviderAndOpenId() {
        AppExternalIdentityReplayGuard replayGuard = acceptingReplayGuard();
        AuthRequest request = mock(AuthRequest.class);
        when(request.login(any())).thenReturn(successfulMiniProgramResponse());
        AppMiniProgramIdentityProvider gateway = new AppMiniProgramIdentityProvider(externalIdentityProperties(), replayGuard,
            config -> request);

        AppExternalIdentityDTO result = gateway.exchange(new AppMiniProgramAuthorizationDTO("mini-code-secret"));

        assertThat(result.provider()).isEqualTo("wechat_mini_program");
        assertThat(result.providerSubject()).isEqualTo("mini-openid-42");
        assertThat(result.toString()).doesNotContain("mini-openid-42");
        verify(replayGuard).consumeMiniProgram("mini-code-secret");
        verify(request).login(any());
    }

    @Test
    void consumesOnlyHmacDerivedReplayKeysAndFailsClosedBeforeCallingAnExternalProviderAgain() {
        AppExternalIdentityProperties properties = externalIdentityProperties();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        when(script.eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), any(List.class), any()))
            .thenReturn(0L);
        AppExternalIdentityReplayGuard replayGuard = new AppExternalIdentityReplayGuard(properties, redissonClient);

        assertThatThrownBy(() -> replayGuard.consumeSocial("github", "social-code-secret", "social-state-secret"))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方授权无效")
            .hasMessageNotContaining("social-code-secret")
            .hasMessageNotContaining("social-state-secret");

        ArgumentCaptor<List<Object>> keys = keyCaptor();
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), keys.capture(), any());
        assertThat(keys.getValue()).allSatisfy(key -> assertThat((String) key)
            .doesNotContain("social-code-secret", "social-state-secret"));
    }

    private static AppExternalIdentityReplayGuard acceptingReplayGuard() {
        AppExternalIdentityReplayGuard replayGuard = mock(AppExternalIdentityReplayGuard.class);
        return replayGuard;
    }

    private static AppExternalIdentityProperties externalIdentityProperties() {
        AppExternalIdentityProperties properties = new AppExternalIdentityProperties();
        properties.setEnabled(true);
        properties.setReplayHmacSecret("0123456789abcdef0123456789abcdef");
        properties.getSocial().setEnabled(true);
        properties.getSocial().setAllowedProviders(List.of("github"));
        properties.getMiniProgram().setEnabled(true);
        properties.getMiniProgram().setAppId("creator-mini");
        properties.getMiniProgram().setAppSecret("creator-mini-secret");
        return properties;
    }

    private static SocialProperties socialProperties() {
        SocialLoginConfigProperties config = new SocialLoginConfigProperties();
        config.setClientId("creator-github");
        config.setClientSecret("creator-github-secret");
        config.setRedirectUri("https://creator.example.test/auth/callback");
        SocialProperties properties = new SocialProperties();
        properties.setType(Map.of("github", config));
        return properties;
    }

    private static AuthResponse<AuthUser> successfulSocialResponse() {
        return successfulSocialResponse("github", "github-subject-42");
    }

    private static AuthResponse<AuthUser> successfulSocialResponse(String provider, String subject) {
        AuthUser user = new AuthUser();
        user.setSource(provider);
        user.setUuid(subject);
        return new AuthResponse<>(AuthResponseStatus.SUCCESS.getCode(), "ok", user);
    }

    private static AuthResponse<AuthUser> successfulMiniProgramResponse() {
        AuthToken token = new AuthToken();
        token.setOpenId("mini-openid-42");
        token.setUnionId("must-not-be-returned");
        token.setAccessToken("must-not-be-returned");
        AuthUser user = new AuthUser();
        user.setToken(token);
        return new AuthResponse<>(AuthResponseStatus.SUCCESS.getCode(), "ok", user);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<Object>> keyCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
