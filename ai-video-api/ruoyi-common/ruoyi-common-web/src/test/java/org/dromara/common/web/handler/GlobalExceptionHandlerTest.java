package org.dromara.common.web.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonParseException;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies request-validation diagnostics do not log rejected credential values.
 */
@Tag("dev")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void doesNotLogOrReturnTheRejectedPasswordValue() throws NoSuchMethodException {
        String rawPassword = "validation-password-must-not-be-logged";
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
            new PasswordPayload(rawPassword), "passwordPayload");
        bindingResult.addError(new FieldError("passwordPayload", "password", rawPassword, false,
            null, null, "密码长度不能超过 256 个字符"));
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("accept", PasswordPayload.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
            new MethodParameter(method, 0), bindingResult);

        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            R<Void> response = handler.handleMethodArgumentNotValidException(exception);

            assertThat(response.getMsg()).isEqualTo("密码长度不能超过 256 个字符");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(rawPassword));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void doesNotReturnTheRawMismatchedRequestParameterValue() throws NoSuchMethodException {
        String rawToken = "query-token-must-not-be-returned";
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("acceptPage", Integer.class);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
            rawToken, Integer.class, "pageNum", new MethodParameter(method, 0), null);

        R<Void> response = handler.handleMethodArgumentTypeMismatchException(
            exception, new MockHttpServletRequest("GET", "/api/admin/app-users"));

        assertThat(response.getMsg()).doesNotContain(rawToken);
    }

    @Test
    void doesNotLogTheRejectedQueryParameterValue() {
        String rawQueryValue = "query-password-must-not-be-logged";
        BindException exception = new BindException(new PasswordPayload(rawQueryValue), "passwordPayload");
        exception.addError(new FieldError("passwordPayload", "password", rawQueryValue, false,
            null, null, "密码长度不能超过 256 个字符"));

        assertLogDoesNotContain(rawQueryValue, () -> {
            R<Void> response = handler.handleBindException(exception);
            assertThat(response.getMsg()).isEqualTo("密码长度不能超过 256 个字符");
        });
    }

    @Test
    void doesNotLogRawJsonOrMessageReaderExceptionDetails() {
        String rawRequestMarker = "parser-message-must-not-be-logged";
        JsonParseException jsonParseException = mock(JsonParseException.class);
        HttpMessageNotReadableException messageNotReadableException = mock(HttpMessageNotReadableException.class);
        when(jsonParseException.getMessage()).thenReturn(rawRequestMarker);
        when(messageNotReadableException.getMessage()).thenReturn(rawRequestMarker);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");

        assertLogDoesNotContain(rawRequestMarker, () -> {
            handler.handleJsonParseException(jsonParseException, request);
            handler.handleHttpMessageNotReadableException(messageNotReadableException, request);
        });
    }

    private void assertLogDoesNotContain(String marker, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(marker));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @SuppressWarnings("unused")
    private void accept(PasswordPayload payload) {
    }

    @SuppressWarnings("unused")
    private void acceptPage(Integer pageNum) {
    }

    private record PasswordPayload(String password) {
    }
}
