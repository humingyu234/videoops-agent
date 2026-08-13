package org.dromara.aivideo.infra.verification.provider;

import org.dromara.aivideo.infra.verification.AppVerificationDeliveryProperties;
import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mail.core.MailBuilder;

/**
 * 基于框架邮件账户的创作端验证码投递适配器。
 *
 * <p>此类只使用 core 提供的一次性命令，既不查询用户表，也不记录邮箱或验证码。</p>
 */
public class AppMailVerificationProvider {

    private static final String DELIVERY_FAILURE = "验证码邮件投递失败";

    private final AppVerificationDeliveryProperties properties;

    public AppMailVerificationProvider(AppVerificationDeliveryProperties properties) {
        this.properties = properties;
    }

    public AppVerificationChannel channel() {
        return AppVerificationChannel.EMAIL;
    }

    public void deliver(AppVerificationDeliveryDTO command) {
        try {
            AppVerificationDeliveryProperties.Mail mailProperties = properties.getMail();
            MailBuilder.of()
                .to(command.normalizedTarget())
                .subject(mailProperties.getSubject())
                .text(renderContent(mailProperties.getContentTemplate(), command))
                .send();
        } catch (RuntimeException exception) {
            throw new ServiceException(DELIVERY_FAILURE);
        }
    }

    private String renderContent(String template, AppVerificationDeliveryDTO command) {
        return template
            .replace("{code}", command.verificationCode())
            .replace("{expiresInMinutes}", expiresInMinutes(command.expiresInSeconds()));
    }

    private String expiresInMinutes(long expiresInSeconds) {
        long minutes = Math.max(1L, expiresInSeconds / 60L);
        if (expiresInSeconds > 0 && expiresInSeconds % 60L != 0) {
            minutes++;
        }
        return Long.toString(minutes);
    }
}
