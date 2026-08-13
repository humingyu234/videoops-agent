package org.dromara.common.mybatis.handler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.exception.NotLoginException;
import org.dromara.common.core.domain.R;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.MyBatisSystemException;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies wrapped authentication failures cannot leak raw credentials through MyBatis logging.
 */
@Tag("dev")
class MybatisExceptionHandlerTest {

    private final MybatisExceptionHandler handler = new MybatisExceptionHandler();

    @Test
    void doesNotWriteRawTokenDetailsWhenAuthenticationFailureIsWrappedByMyBatis() {
        String rawTokenMarker = "raw-token-must-not-be-logged";
        Logger logger = (Logger) LoggerFactory.getLogger(MybatisExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            R<Void> response = handler.handleCannotFindDataSourceException(
                new MyBatisSystemException(NotLoginException.newInstance("app", NotLoginException.NOT_TOKEN,
                    "token is invalid: " + rawTokenMarker, rawTokenMarker)),
                new MockHttpServletRequest("GET", "/api/admin/app-users"));

            assertThat(response).extracting(R::getCode, R::getMsg)
                .containsExactly(401, "认证失败，无法访问系统资源");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains(rawTokenMarker));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
