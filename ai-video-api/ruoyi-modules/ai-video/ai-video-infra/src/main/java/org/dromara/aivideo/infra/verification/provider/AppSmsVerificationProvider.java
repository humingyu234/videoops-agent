package org.dromara.aivideo.infra.verification.provider;

import org.dromara.aivideo.infra.verification.AppVerificationDeliveryProperties;
import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;

import java.util.LinkedHashMap;

/**
 * 基于 sms4j 的创作端短信验证码投递适配器。
 *
 * <p>此类只使用 core 提供的一次性命令，既不查询用户表，也不记录手机号或验证码。</p>
 */
public class AppSmsVerificationProvider {

    private static final String DELIVERY_FAILURE = "验证码短信投递失败";

    private final AppVerificationDeliveryProperties properties;

    public AppSmsVerificationProvider(AppVerificationDeliveryProperties properties) {
        this.properties = properties;
    }

    public AppVerificationChannel channel() {
        return AppVerificationChannel.PHONE;
    }

    public void deliver(AppVerificationDeliveryDTO command) {
        try {
            AppVerificationDeliveryProperties.Sms smsProperties = properties.getSms();
            SmsBlend smsBlend = SmsFactory.getSmsBlend(smsProperties.getConfigId());
            if (smsBlend == null) {
                throw new IllegalStateException("未找到验证码短信供应商");
            }
            LinkedHashMap<String, String> parameters = new LinkedHashMap<>();
            parameters.put(smsProperties.getCodeParameter(), command.verificationCode());
            parameters.put(smsProperties.getExpiresInMinutesParameter(), expiresInMinutes(command.expiresInSeconds()));
            SmsResponse response = smsBlend.sendMessage(command.normalizedTarget(), smsProperties.getTemplateId(), parameters);
            if (response == null || !response.isSuccess()) {
                throw new IllegalStateException("验证码短信供应商返回失败");
            }
        } catch (RuntimeException exception) {
            throw new ServiceException(DELIVERY_FAILURE);
        }
    }

    private String expiresInMinutes(long expiresInSeconds) {
        long minutes = Math.max(1L, expiresInSeconds / 60L);
        if (expiresInSeconds > 0 && expiresInSeconds % 60L != 0) {
            minutes++;
        }
        return Long.toString(minutes);
    }
}
