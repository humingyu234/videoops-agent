package org.dromara.common.web.interceptor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.dromara.common.web.filter.RepeatedlyRequestWrapper;
import org.dromara.common.core.utils.SpringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies global request timing logs never retain sensitive credential values.
 */
@Tag("dev")
class PlusWebInvokeTimeInterceptorTest {

    private static AnnotationConfigApplicationContext applicationContext;

    @BeforeAll
    static void setUpJsonMapper() {
        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.registerBean(SpringUtils.class);
        applicationContext.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
        applicationContext.refresh();
    }

    @AfterAll
    static void closeApplicationContext() {
        applicationContext.close();
    }

    @Test
    void removesCreatorCredentialFieldsFromValidJsonRequestLogs() throws Exception {
        String currentPassword = "current-password-must-not-be-logged";
        String accessToken = "access-token-must-not-be-logged";
        String clientSecret = "client-secret-must-not-be-logged";
        String verificationCode = "verification-code-must-not-be-logged";

        List<String> messages = logMessagesForJson("""
            {"currentPassword":"%s","accessToken":"%s","clientSecret":"%s","verificationCode":"%s"}
            """.formatted(currentPassword, accessToken, clientSecret, verificationCode));

        assertThat(messages).noneMatch(message -> message.contains(currentPassword)
            || message.contains(accessToken)
            || message.contains(clientSecret)
            || message.contains(verificationCode));
    }

    @Test
    void omitsMalformedJsonInsteadOfFallingBackToRawRequestBodyLogging() throws Exception {
        String rawPassword = "malformed-json-password-must-not-be-logged";

        List<String> messages = logMessagesForJson("{\"password\":\"" + rawPassword + "\"");

        assertThat(messages).noneMatch(message -> message.contains(rawPassword));
    }

    private List<String> logMessagesForJson(String json) throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PlusWebInvokeTimeInterceptor.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/auth/password");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(json.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        RepeatedlyRequestWrapper requestWrapper = new RepeatedlyRequestWrapper(request, response);
        PlusWebInvokeTimeInterceptor interceptor = new PlusWebInvokeTimeInterceptor();

        try {
            interceptor.preHandle(requestWrapper, response, this);
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        } finally {
            interceptor.afterCompletion(requestWrapper, response, this, null);
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        }
    }
}
