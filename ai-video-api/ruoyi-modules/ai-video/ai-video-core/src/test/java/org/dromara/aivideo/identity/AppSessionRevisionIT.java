package org.dromara.aivideo.identity;

import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证真实 MySQL 中的四类会话修订变更都会使当前 app 会话失效。
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
class AppSessionRevisionIT {

    private AppSessionIntegrationTestFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AppSessionIntegrationTestFixture.create();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) {
            try {
                fixture.close();
            } finally {
                fixture = null;
            }
        }
    }

    @Test
    void invalidatesCurrentAppSessionForEveryRevisionFactSource() throws Exception {
        assertStaleAfterRevisionChange(63_001L, "credential_revision");
        assertStaleAfterRevisionChange(63_002L, "identity_revision");
        assertStaleAfterRevisionChange(63_003L, "permission_revision");
        assertStaleAfterRevisionChange(63_004L, "client_revision");
    }

    @Test
    void keepsTheCurrentAppSessionWhenAllRevisionFactsAreUnchanged() throws Exception {
        long userId = 63_005L;
        fixture.insertActiveUserAndClient(userId);
        fixture.loginApp(userId);

        assertThatCode(() -> fixture.revisionGuard().checkCurrentSession()).doesNotThrowAnyException();
        assertThat(fixture.loginHelper().isLogin()).isTrue();
        assertThat(fixture.sessionService().currentUserSessions(userId)).hasSize(1);
    }

    @Test
    void refreshesCurrentSessionLastActiveTimeAfterAValidatedRequest() throws Exception {
        long userId = 63_006L;
        fixture.insertActiveUserAndClient(userId);
        fixture.loginApp(userId);
        LocalDateTime before = fixture.sessionService().currentUserSessions(userId).getFirst().lastActiveTime();

        Thread.sleep(1_100L);
        fixture.sessionService().touchCurrentSession();

        LocalDateTime after = fixture.sessionService().currentUserSessions(userId).getFirst().lastActiveTime();
        assertThat(after).isAfter(before);
    }

    private void assertStaleAfterRevisionChange(long userId, String revisionColumn) throws Exception {
        fixture.insertActiveUserAndClient(userId);
        fixture.loginApp(userId);
        fixture.incrementRevision(userId, revisionColumn);

        assertThatThrownBy(() -> fixture.revisionGuard().checkCurrentSession())
            .isInstanceOf(ServiceException.class)
            .hasFieldOrPropertyWithValue("code", 46131);
        assertThat(fixture.loginHelper().isLogin()).isFalse();
        assertThat(fixture.sessionService().currentUserSessions(userId)).isEmpty();
    }
}
