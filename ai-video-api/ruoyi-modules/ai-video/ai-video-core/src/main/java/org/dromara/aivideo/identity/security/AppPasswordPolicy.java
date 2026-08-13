package org.dromara.aivideo.identity.security;

import cn.hutool.crypto.digest.BCrypt;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 创作端密码策略与 BCrypt 散列工具。
 */
@Component
public class AppPasswordPolicy {

    private static final int BCRYPT_MAX_PASSWORD_UTF8_BYTES = 72;

    /**
     * 校验并散列密码。
     *
     * @param rawPassword 明文密码
     * @return BCrypt 密码摘要
     */
    public String hash(String rawPassword) {
        validate(rawPassword);
        return BCrypt.hashpw(rawPassword);
    }

    /**
     * 校验明文密码是否匹配摘要。
     *
     * @param rawPassword 明文密码
     * @param passwordHash BCrypt 密码摘要
     * @return 是否匹配
     */
    public boolean matches(String rawPassword, String passwordHash) {
        return rawPassword != null && passwordHash != null
            && utf8Length(rawPassword) <= BCRYPT_MAX_PASSWORD_UTF8_BYTES
            && BCrypt.checkpw(rawPassword, passwordHash);
    }

    /**
     * 校验密码最小复杂度。
     *
     * @param rawPassword 明文密码
     */
    public void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8) {
            throw new ServiceException("密码长度不能少于 8 位");
        }
        if (utf8Length(rawPassword) > BCRYPT_MAX_PASSWORD_UTF8_BYTES) {
            throw new ServiceException("密码 UTF-8 编码长度不能超过 72 字节");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new ServiceException("密码必须同时包含字母和数字");
        }
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
