package org.dromara.aivideo.infra.identity.provider;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.aivideo.infra.identity.AppExternalIdentityProperties;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 外部授权码和回调状态令牌的一次性回放保护。
 *
 * <p>Redis 仅接收 HMAC 派生键，绝不保存或拼接授权码、状态令牌或小程序 code 明文。</p>
 */
public class AppExternalIdentityReplayGuard {

    private static final String REPLAY_KEY_PREFIX = "aivideo:app:external-identity:replay:{external-identity}:";
    private static final String REPLAY_SCRIPT = """
        for _, key in ipairs(KEYS) do
            if redis.call('exists', key) == 1 then
                return 0
            end
        end
        for _, key in ipairs(KEYS) do
            redis.call('set', key, '1', 'EX', ARGV[1])
        end
        return 1
        """;

    private final AppExternalIdentityProperties properties;
    private final RedissonClient redissonClient;

    public AppExternalIdentityReplayGuard(AppExternalIdentityProperties properties, RedissonClient redissonClient) {
        this.properties = properties;
        this.redissonClient = redissonClient;
    }

    /**
     * 原子消费社交授权码和 state，任一值重复都拒绝本次授权。
     */
    void consumeSocial(String provider, String authorizationCode, String state) {
        reserve(List.of(
            replayKey("social-code", provider, authorizationCode),
            replayKey("social-state", provider, state)), "第三方授权无效");
    }

    /**
     * 原子消费微信小程序 code。
     */
    void consumeMiniProgram(String authorizationCode) {
        reserve(List.of(replayKey("mini-program-code", "wechat_mini_program", authorizationCode)), "小程序授权无效");
    }

    private void reserve(List<String> keys, String failureMessage) {
        if (properties == null || !properties.isOperational() || redissonClient == null) {
            throw new ServiceException(failureMessage);
        }
        try {
            List<Object> scriptKeys = new ArrayList<>(keys);
            Long result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                REPLAY_SCRIPT,
                RScript.ReturnType.LONG,
                scriptKeys,
                Long.toString(properties.getReplayTtlSeconds()));
            if (!Long.valueOf(1L).equals(result)) {
                throw new ServiceException(failureMessage);
            }
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException(failureMessage);
        }
    }

    private String replayKey(String category, String provider, String value) {
        return REPLAY_KEY_PREFIX + category + ":" + hmac(provider, value);
    }

    private String hmac(String... values) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getReplayHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update("aivideo-app-external-identity".getBytes(StandardCharsets.UTF_8));
            for (String value : values) {
                mac.update((byte) 0);
                mac.update(value.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new ServiceException("外部授权无效");
        }
    }
}
