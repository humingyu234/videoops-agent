package org.dromara.aivideo.identity;

import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.service.impl.AppSessionServiceImpl;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证创作端会话失效事件只在事务提交后由会话服务处理。
 */
@Tag("dev")
class AppSessionInvalidationListenerContractTest {

    @Test
    void consumesIdentityInvalidationEventsAfterCommitInsideTheSessionService() {
        Method listener = Arrays.stream(AppSessionServiceImpl.class.getDeclaredMethods())
            .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{AppSessionInvalidationEvent.class}))
            .filter(method -> method.isAnnotationPresent(TransactionalEventListener.class))
            .findFirst()
            .orElseThrow();

        TransactionalEventListener annotation = listener.getAnnotation(TransactionalEventListener.class);
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
