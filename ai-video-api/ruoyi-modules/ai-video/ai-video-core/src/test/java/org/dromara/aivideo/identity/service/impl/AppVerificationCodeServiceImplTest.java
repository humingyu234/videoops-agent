package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.aivideo.identity.dto.AppVerificationCodeRequestDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppVerificationChallengeDTO;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.service.IAppVerificationDeliveryService;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryGrant;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryReservation;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerificationRequest;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.AppLoginVerificationReservation;
import org.dromara.aivideo.identity.security.AppLoginVerificationRequest;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationProperties;
import org.dromara.aivideo.identity.security.AppVerificationScenario;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppVerificationCodeServiceImplTest {

    @Test
    void issuesAnActivePasswordRecoveryChallengeUsingOnlyHmacValuesInRedis() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = issueScriptsReturning(1L, 1L);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        IAppVerificationDeliveryService delivery = mock(IAppVerificationDeliveryService.class);
        when(delivery.channel()).thenReturn(AppVerificationChannel.PHONE);
        when(userMapper.selectOne(anyWrapper())).thenReturn(activePhoneUser());
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            userMapper, redissonClient, List.of(delivery), enabledProperties());

        AppVerificationChallengeDTO challenge = service.issue(
            new AppVerificationCodeRequestDTO(AppVerificationScenario.PASSWORD_RECOVERY,
                AppVerificationChannel.PHONE, "13800138000"),
            new AppAuthClientSnapshotDTO("desktop", 3L));

        assertThat(challenge.challengeId()).isNotBlank();
        assertThat(challenge.maskedTarget()).isEqualTo("138****8000");
        assertThat(challenge.expiresInSeconds()).isEqualTo(600L);
        assertThat(challenge.toString()).doesNotContain(challenge.challengeId(), "13800138000");

        ArgumentCaptor<AppVerificationDeliveryDTO> deliveryCaptor =
            ArgumentCaptor.forClass(AppVerificationDeliveryDTO.class);
        verify(delivery).deliver(deliveryCaptor.capture());
        assertThat(deliveryCaptor.getValue()).satisfies(command -> {
            assertThat(command.scenario()).isEqualTo(AppVerificationScenario.PASSWORD_RECOVERY);
            assertThat(command.normalizedTarget()).isEqualTo("13800138000");
            assertThat(command.verificationCode()).matches("\\d{6}");
            assertThat(command.toString()).doesNotContain(command.normalizedTarget(), command.verificationCode());
        });

        ArgumentCaptor<Object[]> argumentsCaptor = objectArrayCaptor();
        verify(script, times(2)).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), anyList(),
            argumentsCaptor.capture());
        for (Object[] values : argumentsCaptor.getAllValues()) {
            assertThat(values)
                .allSatisfy(value -> assertThat(String.valueOf(value))
                    .doesNotContain("13800138000", deliveryCaptor.getValue().verificationCode()));
        }
    }

    @Test
    void returnsTheSameNeutralChallengeShapeForAnUnknownTargetWithoutPersistingOrDelivering() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = issueScriptsReturning(1L, 1L);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        IAppVerificationDeliveryService delivery = mock(IAppVerificationDeliveryService.class);
        when(delivery.channel()).thenReturn(AppVerificationChannel.EMAIL);
        when(userMapper.selectOne(anyWrapper())).thenReturn(null);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            userMapper, redissonClient, List.of(delivery), enabledProperties());

        AppVerificationChallengeDTO challenge = service.issue(
            new AppVerificationCodeRequestDTO(AppVerificationScenario.PASSWORD_RECOVERY,
                AppVerificationChannel.EMAIL, "missing@example.com"),
            new AppAuthClientSnapshotDTO("desktop", 3L));

        assertThat(challenge.challengeId()).isNotBlank();
        assertThat(challenge.maskedTarget()).isEqualTo("m***@example.com");
        assertThat(challenge.expiresInSeconds()).isEqualTo(600L);
        verify(delivery, never()).deliver(any());
    }

    @Test
    void returnsANeutralChallengeWithoutLookingUpOrDeliveringWhenTheTargetAndClientAreRateLimited() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = issueScriptsReturning(0L, 1L);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        IAppVerificationDeliveryService delivery = mock(IAppVerificationDeliveryService.class);
        when(delivery.channel()).thenReturn(AppVerificationChannel.PHONE);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            userMapper, redissonClient, List.of(delivery), enabledProperties());

        AppVerificationChallengeDTO challenge = service.issue(
            new AppVerificationCodeRequestDTO(AppVerificationScenario.PASSWORD_RECOVERY,
                AppVerificationChannel.PHONE, "13800138000"),
            new AppAuthClientSnapshotDTO("desktop", 3L));

        assertThat(challenge.maskedTarget()).isEqualTo("138****8000");
        assertThat(challenge.expiresInSeconds()).isEqualTo(600L);
        verifyNoInteractions(userMapper);
        verify(delivery, never()).deliver(any());
    }

    @Test
    void keepsTheCooldownAndStopsIncrementingAfterTheDailyQuotaIsExhausted() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = issueScriptsReturning(0L, 1L);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        IAppVerificationDeliveryService delivery = mock(IAppVerificationDeliveryService.class);
        when(delivery.channel()).thenReturn(AppVerificationChannel.PHONE);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            mock(AppUserMapper.class), redissonClient, List.of(delivery), enabledProperties());

        service.issue(new AppVerificationCodeRequestDTO(AppVerificationScenario.PASSWORD_RECOVERY,
            AppVerificationChannel.PHONE, "13800138000"), new AppAuthClientSnapshotDTO("desktop", 3L));

        ArgumentCaptor<String> scriptCaptor = ArgumentCaptor.forClass(String.class);
        verify(script).eval(eq(RScript.Mode.READ_WRITE), scriptCaptor.capture(), eq(RScript.ReturnType.LONG), anyList(),
            anyString(), anyString(), anyString());
        assertThat(scriptCaptor.getValue())
            .contains("local issued = tonumber(redis.call('get', KEYS[2]) or '0')")
            .contains("if issued >= tonumber(ARGV[3]) then")
            .doesNotContain("redis.call('del', KEYS[1])");
    }

    @Test
    void reservesOnlyOneRecoveryChallengeBoundToTheCurrentClientRevision() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = recoveryReservationScriptReturning(true);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            userMapper, redissonClient, List.of(), enabledProperties());

        AppPasswordRecoveryReservation reservation = service.reserve(
            new AppPasswordRecoveryVerificationRequest("challenge-opaque", "123456", "desktop", 3L));

        assertThat(reservation.grant()).isEqualTo(new AppPasswordRecoveryGrant(
            1001L, AppVerificationChannel.PHONE, 7L, 9L));
        assertThat(reservation.toString()).doesNotContain("13800138000", "123456", "challenge-opaque");
        ArgumentCaptor<Object[]> argumentsCaptor = objectArrayCaptor();
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LIST), anyList(),
            argumentsCaptor.capture());
        assertThat(argumentsCaptor.getValue())
            .allSatisfy(value -> assertThat(String.valueOf(value)).doesNotContain("123456"));
        assertThat(argumentsCaptor.getValue()).contains("desktop", "3", "5");
    }

    @Test
    void reservesARecoveryChallengeBeforeThePasswordChangeIsCommitted() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = recoveryReservationScriptReturning(true);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            userMapper, redissonClient, List.of(), enabledProperties());

        AppPasswordRecoveryReservation reservation = service.reserve(
            new AppPasswordRecoveryVerificationRequest("challenge-opaque", "123456", "desktop", 3L));

        assertThat(reservation).isNotNull();
        assertThat(reservation.grant()).isEqualTo(new AppPasswordRecoveryGrant(
            1001L, AppVerificationChannel.PHONE, 7L, 9L));
    }

    @Test
    void reservesOnlyALoginChallengeBoundToTheCurrentClientRevision() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = loginReservationScriptReturning();
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            mock(AppUserMapper.class), redissonClient, List.of(), enabledProperties());

        AppLoginVerificationReservation reservation = service.reserve(
            new AppLoginVerificationRequest("challenge-opaque", "123456", "desktop", 3L));

        assertThat(reservation).isNotNull();
        assertThat(reservation.grant()).isEqualTo(new AppLoginVerificationGrant(
            1001L, AppVerificationChannel.PHONE, 7L, 9L));
        assertThat(reservation.toString()).doesNotContain("13800138000", "123456", "challenge-opaque");
        ArgumentCaptor<Object[]> argumentsCaptor = objectArrayCaptor();
        verify(script).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LIST), anyList(),
            argumentsCaptor.capture());
        assertThat(argumentsCaptor.getValue())
            .contains("login", "desktop", "3", "5");
    }

    @Test
    void rejectsAndDeletesARecoveryReservationWhoseSignedClaimsWereTampered() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = recoveryReservationScriptReturning(false);
        when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(script);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            mock(AppUserMapper.class), redissonClient, List.of(), enabledProperties());

        assertThat(service.reserve(
            new AppPasswordRecoveryVerificationRequest("challenge-opaque", "123456", "desktop", 3L)))
            .isNull();

        verify(script).eval(eq(RScript.Mode.READ_WRITE), contains("reservationId"), eq(RScript.ReturnType.LIST), anyList(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        verify(script).eval(eq(RScript.Mode.READ_WRITE), contains("return redis.call('del'"), eq(RScript.ReturnType.LONG),
            anyList(), anyString());
    }

    @Test
    void refusesToConsumeWhenVerificationIsDisabled() {
        AppVerificationProperties properties = enabledProperties();
        properties.setEnabled(false);
        RedissonClient redissonClient = mock(RedissonClient.class);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            mock(AppUserMapper.class), redissonClient, List.of(), properties);

        assertThat(service.reserve(
            new AppPasswordRecoveryVerificationRequest("challenge-opaque", "123456", "desktop", 3L)))
            .isNull();
        verifyNoInteractions(redissonClient);
    }

    @Test
    void refusesMalformedVerificationCodesBeforeTouchingRedis() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        AppVerificationCodeServiceImpl service = new AppVerificationCodeServiceImpl(
            mock(AppUserMapper.class), redissonClient, List.of(), enabledProperties());

        assertThat(service.reserve(
            new AppPasswordRecoveryVerificationRequest("challenge-opaque", "not-a-code", "desktop", 3L)))
            .isNull();

        verifyNoInteractions(redissonClient);
    }

    private static AppVerificationProperties enabledProperties() {
        AppVerificationProperties properties = new AppVerificationProperties();
        properties.setEnabled(true);
        properties.setHmacSecret("0123456789abcdef0123456789abcdef");
        properties.setTtlSeconds(600L);
        properties.setMaxAttempts(5);
        properties.setCooldownSeconds(60L);
        properties.setMaxIssuesPerDay(5);
        return properties;
    }

    private static AppUser activePhoneUser() {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setDelFlag("0");
        user.setPhoneNormalized("13800138000");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        return user;
    }

    @SuppressWarnings("unchecked")
    private static Wrapper<AppUser> anyWrapper() {
        return any(Wrapper.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Object[]> objectArrayCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Object[].class);
    }

    private static RScript issueScriptsReturning(Long rateLimitResult, Long writeResult) {
        RScript script = mock(RScript.class);
        doAnswer(invocation -> rateLimitResult).when(script).eval(
            eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), anyList(),
            anyString(), anyString(), anyString());
        doAnswer(invocation -> writeResult).when(script).eval(
            eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), anyList(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
            anyString(), anyString(), anyString(), anyString(), anyString());
        return script;
    }

    private static RScript recoveryReservationScriptReturning(boolean validStateMac) {
        RScript script = mock(RScript.class);
        doAnswer(invocation -> {
            String reservationId = invocation.getArgument(10);
            String targetMac = testHmac("target", "phone", "13800138000");
            String codeMac = testHmac("code", "challenge-opaque", "123456");
            String stateMac = testHmac("state", "challenge-opaque", "password_recovery", "phone", "desktop", "3",
                "1001", "7", "9", targetMac, codeMac, "issue-nonce");
            return List.of("ok", reservationId, "1001", "phone", "7", "9", targetMac, codeMac, "issue-nonce",
                validStateMac ? stateMac : "tampered-state-mac");
        }).when(script).eval(
            eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LIST), anyList(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        return script;
    }

    private static RScript loginReservationScriptReturning() {
        RScript script = mock(RScript.class);
        doAnswer(invocation -> {
            String reservationId = invocation.getArgument(10);
            String targetMac = testHmac("target", "phone", "13800138000");
            String codeMac = testHmac("code", "challenge-opaque", "123456");
            String stateMac = testHmac("state", "challenge-opaque", "login", "phone", "desktop", "3",
                "1001", "7", "9", targetMac, codeMac, "issue-nonce");
            return List.of("ok", reservationId, "1001", "phone", "7", "9", targetMac, codeMac, "issue-nonce",
                stateMac);
        }).when(script).eval(
            eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LIST), anyList(),
            anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        return script;
    }

    private static String testHmac(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update("aivideo-app-verification".getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                mac.update((byte) 0);
                mac.update(value.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new AssertionError(exception);
        }
    }
}
