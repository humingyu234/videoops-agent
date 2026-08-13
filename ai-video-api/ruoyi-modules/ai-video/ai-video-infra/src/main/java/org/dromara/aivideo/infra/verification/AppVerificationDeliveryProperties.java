package org.dromara.aivideo.infra.verification;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 创作端验证码投递渠道配置。
 *
 * <p>默认不启用任何渠道；每个渠道只有完整配置后才会向 core 注册投递端口。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.verification.delivery")
public class AppVerificationDeliveryProperties {

    /** 短信投递配置。 */
    private Sms sms = new Sms();

    /** 邮件投递配置。 */
    private Mail mail = new Mail();

    /**
     * sms4j 短信模板渠道配置。
     */
    @Getter
    @Setter
    public static class Sms {

        /** 是否显式启用短信渠道。 */
        private boolean enabled;

        /** sms4j 中已注册短信供应商的配置标识。 */
        private String configId;

        /** 供应商侧验证码短信模板标识。 */
        private String templateId;

        /** 短信模板中验证码变量名称。 */
        private String codeParameter = "code";

        /** 短信模板中有效分钟数变量名称。 */
        private String expiresInMinutesParameter = "expiresInMinutes";
    }

    /**
     * 邮件模板渠道配置。
     */
    @Getter
    @Setter
    public static class Mail {

        /** 是否显式启用邮件渠道。 */
        private boolean enabled;

        /** 验证码邮件主题。 */
        private String subject;

        /**
         * 验证码邮件正文模板，必须包含 {@code {code}} 和 {@code {expiresInMinutes}} 占位符。
         */
        private String contentTemplate;
    }
}
