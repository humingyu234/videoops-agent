package org.dromara.aivideo.identity.service.impl;

import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppSecurityAudit;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.mapper.AppSecurityAuditMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppSecurityAuditServiceImplTest {

    private static final String SESSION_ID = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";

    @Test
    void acceptsOnlySafeAppSessionIdentifiersForSessionRevocationAudits() {
        AppSecurityAuditMapper mapper = mock(AppSecurityAuditMapper.class);
        when(mapper.insert(any(AppSecurityAudit.class))).thenReturn(1);
        AppSecurityAuditServiceImpl service = new AppSecurityAuditServiceImpl(mapper);

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext("00000000000000000000000000000001", "127.0.0.1"))) {
            service.append(new AppSecurityAuditDTO(
                "app_session", SESSION_ID, "session_revoked", AppActorType.APP_USER, 1001L,
                null, null, AppSecurityAuditReason.SESSION_REVOCATION.code()));
        }

        ArgumentCaptor<AppSecurityAudit> auditCaptor = ArgumentCaptor.forClass(AppSecurityAudit.class);
        verify(mapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getResourceType()).isEqualTo("app_session");
        assertThat(auditCaptor.getValue().getResourceId()).isEqualTo(SESSION_ID);
        assertThat(auditCaptor.getValue().getReason()).isEqualTo(AppSecurityAuditReason.SESSION_REVOCATION.code());
    }

    @Test
    void rejectsUnsafeAppSessionIdentifiersBeforeWritingAnAudit() {
        AppSecurityAuditMapper mapper = mock(AppSecurityAuditMapper.class);
        AppSecurityAuditServiceImpl service = new AppSecurityAuditServiceImpl(mapper);

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext("00000000000000000000000000000002", "127.0.0.1"))) {
            assertThatThrownBy(() -> service.append(new AppSecurityAuditDTO(
                "app_session", "token-raw-value", "session_revoked", AppActorType.APP_USER, 1001L,
                null, null, AppSecurityAuditReason.SESSION_REVOCATION.code())))
                .isInstanceOf(ServiceException.class);
        }

        verify(mapper, never()).insert(any(AppSecurityAudit.class));
    }
}
