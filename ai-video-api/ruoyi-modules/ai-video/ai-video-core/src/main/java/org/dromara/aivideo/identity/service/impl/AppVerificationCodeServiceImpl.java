package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.dromara.aivideo.identity.service.IAppVerificationCodeService;
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
import org.dromara.aivideo.identity.security.IAppPasswordRecoveryVerificationService;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerificationRequest;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.IAppLoginVerificationService;
import org.dromara.aivideo.identity.security.AppLoginVerificationReservation;
import org.dromara.aivideo.identity.security.AppLoginVerificationRequest;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationProperties;
import org.dromara.aivideo.identity.security.AppVerificationScenario;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.common.core.exception.ServiceException;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 基于 Redis Lua 脚本的创作端验证码状态机。
 *
 * <p>挑战内容只保存 HMAC 摘要；校验、错误次数递增和正确验证码的删除必须在同一 Lua 脚本内完成。</p>
 */
@Service
@ConditionalOnAppSecurityEnabled
public class AppVerificationCodeServiceImpl implements IAppVerificationCodeService, IAppPasswordRecoveryVerificationService,
    IAppLoginVerificationService {

    private static final String STATE_VERSION = "v1";
    private static final String KEY_PREFIX = "aivideo:app:verification:";
    private static final long TTL_SECONDS = 600L;
    private static final int MAX_ATTEMPTS = 5;
    private static final long COOLDOWN_SECONDS = 60L;
    private static final int MAX_ISSUES_PER_DAY = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String ISSUE_SCRIPT = """
        if redis.call('exists', KEYS[1]) == 1 then
            return 0
        end
        redis.call('hset', KEYS[1],
            'v', ARGV[1],
            'scenario', ARGV[2],
            'channel', ARGV[3],
            'clientId', ARGV[4],
            'clientRevision', ARGV[5],
            'userId', ARGV[6],
            'credentialRevision', ARGV[7],
            'identityRevision', ARGV[8],
            'targetMac', ARGV[9],
            'codeMac', ARGV[10],
            'attempts', '0',
            'issueNonce', ARGV[11],
            'stateMac', ARGV[12])
        redis.call('pexpire', KEYS[1], ARGV[13])
        return 1
        """;

    private static final String CLEANUP_SCRIPT = """
        if redis.call('hget', KEYS[1], 'issueNonce') == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """;

    private static final String RESERVE_ISSUE_RATE_LIMIT_SCRIPT = """
        if not redis.call('set', KEYS[1], '1', 'NX', 'EX', ARGV[1]) then
            return 0
        end
        local issued = tonumber(redis.call('get', KEYS[2]) or '0')
        if issued >= tonumber(ARGV[3]) then
            return 0
        end
        issued = redis.call('incr', KEYS[2])
        if issued == 1 then
            redis.call('expire', KEYS[2], ARGV[2])
        end
        return 1
        """;

    private static final String RESERVE_VERIFICATION_SCRIPT = """
        local value = redis.call('hmget', KEYS[1],
            'v', 'scenario', 'channel', 'clientId', 'clientRevision',
            'userId', 'credentialRevision', 'identityRevision', 'targetMac', 'codeMac',
            'issueNonce', 'stateMac', 'attempts', 'reservationId')
        if not value[1]
            or value[1] ~= ARGV[1]
            or value[2] ~= ARGV[2]
            or value[4] ~= ARGV[3]
            or value[5] ~= ARGV[4] then
            return {}
        end
        if value[10] ~= ARGV[5] then
            local attempts = redis.call('hincrby', KEYS[1], 'attempts', 1)
            if attempts >= tonumber(ARGV[6]) then
                redis.call('del', KEYS[1])
            end
            return {}
        end
        if value[14] then
            return {}
        end
        redis.call('hset', KEYS[1], 'reservationId', ARGV[7])
        return { 'ok', ARGV[7], value[6], value[3], value[7], value[8], value[9], value[10], value[11], value[12] }
        """;

    private static final String COMMIT_RESERVATION_SCRIPT = """
        if redis.call('hget', KEYS[1], 'reservationId') == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """;

    private static final String RELEASE_RESERVATION_SCRIPT = """
        if redis.call('hget', KEYS[1], 'reservationId') == ARGV[1] then
            return redis.call('hdel', KEYS[1], 'reservationId')
        end
        return 0
        """;

    private final AppUserMapper userMapper;
    private final RedissonClient redissonClient;
    private final Map<AppVerificationChannel, IAppVerificationDeliveryService> deliveries;
    private final AppVerificationProperties properties;

    public AppVerificationCodeServiceImpl(AppUserMapper userMapper, RedissonClient redissonClient,
                                          List<IAppVerificationDeliveryService> deliveryPorts,
                                          AppVerificationProperties properties) {
        this.userMapper = userMapper;
        this.redissonClient = redissonClient;
        this.deliveries = indexDeliveries(deliveryPorts);
        this.properties = properties;
    }

    @Override
    public AppVerificationChallengeDTO issue(AppVerificationCodeRequestDTO request, AppAuthClientSnapshotDTO client) {
        validateRequest(request, client);
        String normalizedTarget = normalizeTarget(request.channel(), request.target());
        AppVerificationChallengeDTO neutralChallenge = neutralChallenge(normalizedTarget, request.channel());
        if (!properties.isOperational() || !deliveries.containsKey(request.channel())) {
            return neutralChallenge;
        }
        if (!reserveIssueRateLimit(request.channel(), normalizedTarget, client)) {
            return neutralChallenge;
        }
        AppUser subject = findActiveSubject(request.channel(), normalizedTarget);
        if (subject == null) {
            return neutralChallenge;
        }

        String challengeId = neutralChallenge.challengeId();
        String verificationCode = nextVerificationCode();
        String issueNonce = nextOpaqueId();
        if (!persistChallenge(challengeId, request, client, subject, normalizedTarget, verificationCode, issueNonce)) {
            return neutralChallenge;
        }
        try {
            deliveries.get(request.channel()).deliver(new AppVerificationDeliveryDTO(
                request.scenario(), normalizedTarget, verificationCode, TTL_SECONDS));
        } catch (RuntimeException exception) {
            deleteIfIssuedBy(challengeId, issueNonce);
        }
        return neutralChallenge;
    }

    /**
     * 原子校验并预留找回密码挑战；任何异常或状态不匹配均返回 {@code null}。
     */
    @Override
    public AppPasswordRecoveryReservation reserve(AppPasswordRecoveryVerificationRequest request) {
        if (!isValidReservationRequest(request == null ? null : request.challengeId(),
            request == null ? null : request.verificationCode(), request == null ? null : request.clientId(),
            request == null ? 0L : request.clientRevision())) {
            return null;
        }
        String reservationId = nextOpaqueId();
        try {
            List<Object> result = reserveChallenge(request.challengeId(), request.verificationCode(), request.clientId(),
                request.clientRevision(), AppVerificationScenario.PASSWORD_RECOVERY, reservationId);
            AppPasswordRecoveryReservation reservation = parseRecoveryReservation(request, reservationId, result);
            if (reservation == null && isSuccessfulReservationResult(result, reservationId)) {
                commitReservation(request.challengeId(), reservationId);
            }
            return reservation;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void commit(AppPasswordRecoveryReservation reservation) {
        if (reservation != null) {
            commitReservation(reservation.challengeId(), reservation.reservationId());
        }
    }

    @Override
    public void release(AppPasswordRecoveryReservation reservation) {
        if (reservation != null) {
            releaseReservation(reservation.challengeId(), reservation.reservationId());
        }
    }

    /**
     * 原子校验并预留登录场景验证码；会话与登录审计成功后才会最终消费。
     */
    @Override
    public AppLoginVerificationReservation reserve(AppLoginVerificationRequest request) {
        if (!isValidReservationRequest(request == null ? null : request.challengeId(),
            request == null ? null : request.verificationCode(), request == null ? null : request.clientId(),
            request == null ? 0L : request.clientRevision())) {
            return null;
        }
        String reservationId = nextOpaqueId();
        try {
            List<Object> result = reserveChallenge(request.challengeId(), request.verificationCode(), request.clientId(),
                request.clientRevision(), AppVerificationScenario.LOGIN, reservationId);
            AppLoginVerificationReservation reservation = parseLoginReservation(request, reservationId, result);
            if (reservation == null && isSuccessfulReservationResult(result, reservationId)) {
                commitReservation(request.challengeId(), reservationId);
            }
            return reservation;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void commit(AppLoginVerificationReservation reservation) {
        if (reservation != null) {
            commitReservation(reservation.challengeId(), reservation.reservationId());
        }
    }

    @Override
    public void release(AppLoginVerificationReservation reservation) {
        if (reservation != null) {
            releaseReservation(reservation.challengeId(), reservation.reservationId());
        }
    }

    private boolean persistChallenge(String challengeId, AppVerificationCodeRequestDTO request,
                                     AppAuthClientSnapshotDTO client, AppUser subject, String normalizedTarget,
                                     String verificationCode, String issueNonce) {
        try {
            String targetMac = targetMac(request.channel(), normalizedTarget);
            String codeMac = codeMac(challengeId, verificationCode);
            Long result = script().eval(
                RScript.Mode.READ_WRITE,
                ISSUE_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(challengeKey(challengeId)),
                STATE_VERSION,
                request.scenario().key(),
                channelKey(request.channel()),
                client.clientId(),
                Long.toString(client.clientRevision()),
                Long.toString(subject.getUserId()),
                Long.toString(subject.getCredentialRevision()),
                Long.toString(subject.getIdentityRevision()),
                targetMac,
                codeMac,
                issueNonce,
                stateMac(challengeId, request.scenario().key(), channelKey(request.channel()), client.clientId(),
                    client.clientRevision(), subject.getUserId(), subject.getCredentialRevision(),
                    subject.getIdentityRevision(), targetMac, codeMac, issueNonce),
                Long.toString(TTL_SECONDS * 1_000));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void deleteIfIssuedBy(String challengeId, String issueNonce) {
        try {
            script().eval(
                RScript.Mode.READ_WRITE,
                CLEANUP_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(challengeKey(challengeId)),
                issueNonce);
        } catch (RuntimeException ignored) {
            // 投递失败时宁可不报告内部状态；过期时间仍可限制残留挑战。
        }
    }

    /**
     * 以目标和客户端的 HMAC 派生 Redis 限流键；两个键使用同一 hash tag，兼容 Redis Cluster Lua。
     */
    private boolean reserveIssueRateLimit(AppVerificationChannel channel, String normalizedTarget,
                                          AppAuthClientSnapshotDTO client) {
        String targetMac = targetMac(channel, normalizedTarget);
        String clientMac = hmac("client", client.clientId());
        try {
            Long result = script().eval(
                RScript.Mode.READ_WRITE,
                RESERVE_ISSUE_RATE_LIMIT_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(rateLimitKey(targetMac, clientMac, "cooldown"), rateLimitKey(targetMac, clientMac, "daily")),
                Long.toString(COOLDOWN_SECONDS),
                Long.toString(24 * 60 * 60),
                Integer.toString(MAX_ISSUES_PER_DAY));
            return Long.valueOf(1L).equals(result);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private AppUser findActiveSubject(AppVerificationChannel channel, String normalizedTarget) {
        LambdaQueryWrapper<AppUser> query = new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getDelFlag, "0")
            .eq(AppUser::getStatus, AppIdentityStatus.ACTIVE);
        switch (channel) {
            case PHONE -> query.eq(AppUser::getPhoneNormalized, normalizedTarget);
            case EMAIL -> query.eq(AppUser::getEmailNormalized, normalizedTarget);
        }
        return userMapper.selectOne(query);
    }

    private AppVerificationChallengeDTO neutralChallenge(String normalizedTarget, AppVerificationChannel channel) {
        return new AppVerificationChallengeDTO(nextOpaqueId(), maskTarget(channel, normalizedTarget), TTL_SECONDS);
    }

    private Map<AppVerificationChannel, IAppVerificationDeliveryService> indexDeliveries(
        List<IAppVerificationDeliveryService> deliveryPorts) {
        Map<AppVerificationChannel, IAppVerificationDeliveryService> indexed = new EnumMap<>(AppVerificationChannel.class);
        for (IAppVerificationDeliveryService delivery : deliveryPorts) {
            if (delivery == null || delivery.channel() == null) {
                throw new IllegalStateException("创作端验证码投递适配器不完整");
            }
            if (indexed.putIfAbsent(delivery.channel(), delivery) != null) {
                throw new IllegalStateException("同一创作端验证码渠道配置了多个投递适配器");
            }
        }
        return Map.copyOf(indexed);
    }

    private void validateRequest(AppVerificationCodeRequestDTO request, AppAuthClientSnapshotDTO client) {
        if (request == null || request.scenario() == null || request.channel() == null
            || client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new ServiceException("验证码申请参数无效");
        }
    }

    private String normalizeTarget(AppVerificationChannel channel, String target) {
        if (isBlank(target)) {
            throw new ServiceException("验证码申请参数无效");
        }
        String normalized = target.trim().toLowerCase(java.util.Locale.ROOT);
        boolean valid = switch (channel) {
            case PHONE -> normalized.matches("^1[3-9]\\d{9}$");
            case EMAIL -> normalized.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
        };
        if (!valid) {
            throw new ServiceException("验证码申请参数无效");
        }
        return normalized;
    }

    private AppPasswordRecoveryReservation parseRecoveryReservation(AppPasswordRecoveryVerificationRequest request,
                                                                     String reservationId, List<Object> values) {
        if (!isSuccessfulReservationResult(values, reservationId) || values.size() != 10) {
            return null;
        }
        try {
            AppVerificationChannel channel = parseChannel(String.valueOf(values.get(3)));
            long userId = Long.parseLong(String.valueOf(values.get(2)));
            long credentialRevision = Long.parseLong(String.valueOf(values.get(4)));
            long identityRevision = Long.parseLong(String.valueOf(values.get(5)));
            String targetMac = String.valueOf(values.get(6));
            String verifiedCodeMac = String.valueOf(values.get(7));
            String issueNonce = String.valueOf(values.get(8));
            String storedStateMac = String.valueOf(values.get(9));
            String expectedStateMac = stateMac(request.challengeId(), AppVerificationScenario.PASSWORD_RECOVERY.key(),
                channelKey(channel), request.clientId(), request.clientRevision(), userId, credentialRevision,
                identityRevision, targetMac, verifiedCodeMac, issueNonce);
            if (!constantTimeEquals(expectedStateMac, storedStateMac)
                || !constantTimeEquals(verifiedCodeMac, codeMac(request.challengeId(), request.verificationCode()))) {
                return null;
            }
            return new AppPasswordRecoveryReservation(request.challengeId(), reservationId,
                new AppPasswordRecoveryGrant(userId, channel, credentialRevision, identityRevision));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private AppLoginVerificationReservation parseLoginReservation(AppLoginVerificationRequest request,
                                                                   String reservationId, List<Object> values) {
        if (!isSuccessfulReservationResult(values, reservationId) || values.size() != 10) {
            return null;
        }
        try {
            AppVerificationChannel channel = parseChannel(String.valueOf(values.get(3)));
            long userId = Long.parseLong(String.valueOf(values.get(2)));
            long credentialRevision = Long.parseLong(String.valueOf(values.get(4)));
            long identityRevision = Long.parseLong(String.valueOf(values.get(5)));
            String targetMac = String.valueOf(values.get(6));
            String verifiedCodeMac = String.valueOf(values.get(7));
            String issueNonce = String.valueOf(values.get(8));
            String storedStateMac = String.valueOf(values.get(9));
            String expectedStateMac = stateMac(request.challengeId(), AppVerificationScenario.LOGIN.key(),
                channelKey(channel), request.clientId(), request.clientRevision(), userId, credentialRevision,
                identityRevision, targetMac, verifiedCodeMac, issueNonce);
            if (!constantTimeEquals(expectedStateMac, storedStateMac)
                || !constantTimeEquals(verifiedCodeMac, codeMac(request.challengeId(), request.verificationCode()))) {
                return null;
            }
            return new AppLoginVerificationReservation(request.challengeId(), reservationId,
                new AppLoginVerificationGrant(userId, channel, credentialRevision, identityRevision));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isSuccessfulReservationResult(List<Object> values, String reservationId) {
        return values != null
            && values.size() >= 2
            && "ok".equals(String.valueOf(values.getFirst()))
            && constantTimeEquals(reservationId, String.valueOf(values.get(1)));
    }

    private void commitReservation(String challengeId, String reservationId) {
        try {
            script().eval(
                RScript.Mode.READ_WRITE,
                COMMIT_RESERVATION_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(challengeKey(challengeId)),
                reservationId);
        } catch (RuntimeException ignored) {
            // 密码事务已提交时，保留到期预留比将挑战重新放开更安全。
        }
    }

    private void releaseReservation(String challengeId, String reservationId) {
        try {
            script().eval(
                RScript.Mode.READ_WRITE,
                RELEASE_RESERVATION_SCRIPT,
                RScript.ReturnType.LONG,
                List.of(challengeKey(challengeId)),
                reservationId);
        } catch (RuntimeException ignored) {
            // Redis 失效时保持短期预留；调用方仍会保留原始业务失败。
        }
    }

    private AppVerificationChannel parseChannel(String value) {
        return switch (value) {
            case "phone" -> AppVerificationChannel.PHONE;
            case "email" -> AppVerificationChannel.EMAIL;
            default -> throw new IllegalArgumentException("未知验证码渠道");
        };
    }

    private boolean isValidReservationRequest(String challengeId, String verificationCode, String clientId,
                                               long clientRevision) {
        return properties.isOperational() && !isBlank(challengeId) && isSixDigitCode(verificationCode)
            && !isBlank(clientId) && clientRevision > 0;
    }

    private List<Object> reserveChallenge(String challengeId, String verificationCode, String clientId,
                                          long clientRevision, AppVerificationScenario scenario,
                                          String reservationId) {
        return script().eval(
            RScript.Mode.READ_WRITE,
            RESERVE_VERIFICATION_SCRIPT,
            RScript.ReturnType.LIST,
            List.of(challengeKey(challengeId)),
            STATE_VERSION,
            scenario.key(),
            clientId,
            Long.toString(clientRevision),
            codeMac(challengeId, verificationCode),
            Integer.toString(MAX_ATTEMPTS),
            reservationId);
    }

    private String targetMac(AppVerificationChannel channel, String normalizedTarget) {
        return hmac("target", channelKey(channel), normalizedTarget);
    }

    private String codeMac(String challengeId, String verificationCode) {
        return hmac("code", challengeId, verificationCode);
    }

    private String stateMac(String challengeId, String scenario, String channel, String clientId,
                            long clientRevision, long userId, long credentialRevision, long identityRevision,
                            String targetMac, String codeMac, String issueNonce) {
        return hmac("state", challengeId, scenario, channel, clientId, Long.toString(clientRevision),
            Long.toString(userId), Long.toString(credentialRevision), Long.toString(identityRevision), targetMac,
            codeMac, issueNonce);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String hmac(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update("aivideo-app-verification".getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                mac.update((byte) 0);
                mac.update(value.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("创作端验证码 HMAC 初始化失败", exception);
        }
    }

    private RScript script() {
        return redissonClient.getScript(StringCodec.INSTANCE);
    }

    private String challengeKey(String challengeId) {
        return KEY_PREFIX + challengeId;
    }

    private String rateLimitKey(String targetMac, String clientMac, String bucket) {
        return KEY_PREFIX + "rate-limit:{" + targetMac + "}:" + bucket + ":" + clientMac;
    }

    private String nextOpaqueId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String nextVerificationCode() {
        return Integer.toString(100_000 + RANDOM.nextInt(900_000));
    }

    private String channelKey(AppVerificationChannel channel) {
        return switch (channel) {
            case PHONE -> "phone";
            case EMAIL -> "email";
        };
    }

    private String maskTarget(AppVerificationChannel channel, String target) {
        return switch (channel) {
            case PHONE -> target.substring(0, 3) + "****" + target.substring(target.length() - 4);
            case EMAIL -> target.substring(0, 1) + "***" + target.substring(target.indexOf('@'));
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isSixDigitCode(String value) {
        return value != null && value.matches("^\\d{6}$");
    }
}
