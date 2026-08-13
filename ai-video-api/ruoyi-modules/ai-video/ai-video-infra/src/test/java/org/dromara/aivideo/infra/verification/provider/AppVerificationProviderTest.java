package org.dromara.aivideo.infra.verification.provider;

import org.dromara.aivideo.infra.verification.AppVerificationDeliveryConfiguration;
import org.dromara.aivideo.infra.verification.AppVerificationDeliveryProperties;
import org.dromara.aivideo.identity.dto.AppVerificationDeliveryDTO;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.identity.security.AppVerificationScenario;
import org.dromara.aivideo.identity.service.IAppVerificationDeliveryService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mail.core.MailBuilder;
import org.dromara.sms4j.api.SmsBlend;
import org.dromara.sms4j.api.entity.SmsResponse;
import org.dromara.sms4j.core.factory.SmsFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that creator verification delivery is opt-in and never exposes a code in failures.
 */
@Tag("dev")
class AppVerificationProviderTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AppVerificationDeliveryConfiguration.class);

    @Test
    void doesNotRegisterDeliveryPortsWithoutTheCreatorSecuritySwitchAndCompleteChannelConfiguration() {
        contextRunner.run(context -> {
                assertThat(context.getBeansOfType(IAppVerificationDeliveryService.class)).isEmpty();
        });

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.verification.delivery.sms.enabled=true",
            "app.security.verification.delivery.sms.config-id=sms-primary")
            .run(context -> {
                assertThat(context.getBeansOfType(IAppVerificationDeliveryService.class)).isEmpty();
            });

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.verification.delivery.sms.enabled=true",
            "app.security.verification.delivery.sms.config-id=sms-primary",
            "app.security.verification.delivery.sms.template-id=SMS_123",
            "app.security.verification.delivery.sms.code-parameter=")
            .run(context -> {
                assertThat(context.getBeansOfType(IAppVerificationDeliveryService.class)).isEmpty();
            });

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.verification.delivery.mail.enabled=true",
            "app.security.verification.delivery.mail.subject=创作端验证码",
            "app.security.verification.delivery.mail.content-template=验证码 {code}")
            .run(context -> {
                assertThat(context.getBeansOfType(IAppVerificationDeliveryService.class)).isEmpty();
            });
    }

    @Test
    void registersOnlyFullyConfiguredChannelsAfterTheCreatorSecuritySwitchIsEnabled() {
        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.verification.delivery.sms.enabled=true",
            "app.security.verification.delivery.sms.config-id=sms-primary",
            "app.security.verification.delivery.sms.template-id=SMS_123")
            .run(context -> {
                assertThat(context).hasSingleBean(IAppVerificationDeliveryService.class);
                assertThat(context.containsBean("appSmsVerificationDelivery")).isTrue();
                assertThat(context.containsBean("appMailVerificationDelivery")).isFalse();
                assertThat(context.getBean(IAppVerificationDeliveryService.class).channel())
                    .isEqualTo(AppVerificationChannel.PHONE);
            });

        contextRunner.withPropertyValues(
            "app.security.token.enabled=true",
            "app.security.verification.delivery.mail.enabled=true",
            "app.security.verification.delivery.mail.subject=创作端验证码",
            "app.security.verification.delivery.mail.content-template=验证码 {code}，{expiresInMinutes} 分钟内有效。")
            .run(context -> {
                assertThat(context).hasSingleBean(IAppVerificationDeliveryService.class);
                assertThat(context.containsBean("appSmsVerificationDelivery")).isFalse();
                assertThat(context.containsBean("appMailVerificationDelivery")).isTrue();
                assertThat(context.getBean(IAppVerificationDeliveryService.class).channel())
                    .isEqualTo(AppVerificationChannel.EMAIL);
            });
    }

    @Test
    void sendsPhoneVerificationThroughTheConfiguredSmsTemplateWithoutLeakingSecretsOnFailure() {
        AppSmsVerificationProvider delivery = new AppSmsVerificationProvider(smsProperties());
        AppVerificationDeliveryDTO command = command("13800138000", "123456");
        SmsBlend smsBlend = mock(SmsBlend.class);
        SmsResponse response = new SmsResponse();
        response.setSuccess(true);
        when(smsBlend.sendMessage(eq(command.normalizedTarget()), eq("SMS_123"),
            org.mockito.ArgumentMatchers.<LinkedHashMap<String, String>>any())).thenReturn(response);

        try (MockedStatic<SmsFactory> factory = org.mockito.Mockito.mockStatic(SmsFactory.class)) {
            factory.when(() -> SmsFactory.getSmsBlend("sms-primary")).thenReturn(smsBlend);

            delivery.deliver(command);
        }

        ArgumentCaptor<LinkedHashMap<String, String>> variables = linkedHashMapCaptor();
        verify(smsBlend).sendMessage(eq("13800138000"), eq("SMS_123"), variables.capture());
        assertThat(variables.getValue()).containsEntry("code", "123456")
            .containsEntry("expiresInMinutes", "10");
        assertThat(command.toString()).doesNotContain("13800138000", "123456");

        SmsResponse failed = new SmsResponse();
        failed.setSuccess(false);
        when(smsBlend.sendMessage(eq(command.normalizedTarget()), eq("SMS_123"),
            org.mockito.ArgumentMatchers.<LinkedHashMap<String, String>>any())).thenReturn(failed);
        try (MockedStatic<SmsFactory> factory = org.mockito.Mockito.mockStatic(SmsFactory.class)) {
            factory.when(() -> SmsFactory.getSmsBlend("sms-primary")).thenReturn(smsBlend);

            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> delivery.deliver(command));
            assertThat(failure)
                .isInstanceOf(ServiceException.class)
                .hasMessage("验证码短信投递失败");
            assertThat(failure.getMessage()).doesNotContain("13800138000", "123456");
        }
    }

    @Test
    void rendersMailTemplatePlaceholdersBeforeSendingAndRedactsProviderFailures() {
        AppMailVerificationProvider delivery = new AppMailVerificationProvider(mailProperties());
        AppVerificationDeliveryDTO command = command("member@example.com", "654321");
        MailBuilder mailBuilder = mock(MailBuilder.class);
        when(mailBuilder.to("member@example.com")).thenReturn(mailBuilder);
        when(mailBuilder.subject("创作端验证码")).thenReturn(mailBuilder);
        when(mailBuilder.text("验证码 654321，10 分钟内有效。")).thenReturn(mailBuilder);

        try (MockedStatic<MailBuilder> builder = org.mockito.Mockito.mockStatic(MailBuilder.class)) {
            builder.when(MailBuilder::of).thenReturn(mailBuilder);

            delivery.deliver(command);
        }

        verify(mailBuilder).to("member@example.com");
        verify(mailBuilder).subject("创作端验证码");
        verify(mailBuilder).text("验证码 654321，10 分钟内有效。");
        verify(mailBuilder).send();
        assertThat(command.toString()).doesNotContain("member@example.com", "654321");

        doThrow(new IllegalStateException("member@example.com 654321")).when(mailBuilder).send();
        try (MockedStatic<MailBuilder> builder = org.mockito.Mockito.mockStatic(MailBuilder.class)) {
            builder.when(MailBuilder::of).thenReturn(mailBuilder);

            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> delivery.deliver(command));
            assertThat(failure)
                .isInstanceOf(ServiceException.class)
                .hasMessage("验证码邮件投递失败");
            assertThat(failure.getMessage()).doesNotContain("member@example.com", "654321");
        }
    }

    private static AppVerificationDeliveryProperties smsProperties() {
        AppVerificationDeliveryProperties properties = new AppVerificationDeliveryProperties();
        properties.getSms().setEnabled(true);
        properties.getSms().setConfigId("sms-primary");
        properties.getSms().setTemplateId("SMS_123");
        return properties;
    }

    private static AppVerificationDeliveryProperties mailProperties() {
        AppVerificationDeliveryProperties properties = new AppVerificationDeliveryProperties();
        properties.getMail().setEnabled(true);
        properties.getMail().setSubject("创作端验证码");
        properties.getMail().setContentTemplate("验证码 {code}，{expiresInMinutes} 分钟内有效。");
        return properties;
    }

    private static AppVerificationDeliveryDTO command(String target, String code) {
        return new AppVerificationDeliveryDTO(AppVerificationScenario.PASSWORD_RECOVERY, target, code, 600L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<LinkedHashMap<String, String>> linkedHashMapCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LinkedHashMap.class);
    }
}
