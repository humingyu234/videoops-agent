package org.dromara.aivideo.identity.dto;

import org.dromara.aivideo.identity.security.AppVerificationScenario;

/**
 * 交给短信或邮件适配器的一次性投递命令。
 *
 * @param scenario 验证码用途
 * @param normalizedTarget 规范化后的投递地址
 * @param verificationCode 一次性验证码明文，只允许本次投递使用
 * @param expiresInSeconds 剩余有效秒数
 */
public record AppVerificationDeliveryDTO(AppVerificationScenario scenario, String normalizedTarget,
                                             String verificationCode, long expiresInSeconds) {

    @Override
    public String toString() {
        return "AppVerificationDeliveryCommand[scenario=" + scenario + ", normalizedTarget=***, verificationCode=***"
            + ", expiresInSeconds=" + expiresInSeconds + "]";
    }
}
