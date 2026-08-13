package org.dromara.common.mybatis.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AuditFillContextTest {

    @Test
    void nestedScopesRestorePreviousActorAndCleanThread() {
        try (AuditFillContext.Scope outer = AuditFillContext.open(11L)) {
            assertThat(AuditFillContext.currentActorId()).isEqualTo(11L);
            try (AuditFillContext.Scope inner = AuditFillContext.open(22L)) {
                assertThat(AuditFillContext.currentActorId()).isEqualTo(22L);
            }
            assertThat(AuditFillContext.currentActorId()).isEqualTo(11L);
        }
        assertThat(AuditFillContext.isBound()).isFalse();
    }

    @Test
    void outOfOrderCloseFailsWithoutLeakingContext() {
        AuditFillContext.Scope outer = AuditFillContext.open(11L);
        AuditFillContext.Scope inner = AuditFillContext.open(22L);
        assertThatThrownBy(outer::close).isInstanceOf(RuntimeException.class);
        inner.close();
        outer.close();
        assertThat(AuditFillContext.isBound()).isFalse();
    }
}
