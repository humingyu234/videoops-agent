package org.dromara.common.satoken.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.exception.NotLoginException;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies authentication failures keep raw credential details out of server logs.
 */
@Tag("dev")
class SaTokenExceptionHandlerTest {

    private final SaTokenExceptionHandler handler = new SaTokenExceptionHandler();

    @Test
    void doesNotWriteRawTokenDetailsToTheAuthenticationFailureLog() {
        String rawTokenMarker = "raw-token-must-not-be-logged";
        Logger logger = (Logger) LoggerFactory.getLogger(SaTokenExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            R<Void> response = handler.handleNotLoginException(
                NotLoginException.newInstance("app", NotLoginException.NOT_TOKEN,
                    "token is invalid: " + rawTokenMarker, rawTokenMarker),
                new MockHttpServletRequest("GET", "/api/admin/app-users"));

            assertThat(response).extracting(R::getCode, R::getMsg)
                .containsExactly(401, "登录状态异常，请重新登录");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(rawTokenMarker));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
