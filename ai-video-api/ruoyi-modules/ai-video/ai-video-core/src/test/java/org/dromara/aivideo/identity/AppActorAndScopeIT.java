package org.dromara.aivideo.identity;

import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ChangeAppUserStatusDTO;
import org.dromara.aivideo.identity.dto.ResetAppPasswordDTO;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证操作者类型、修订写入和创作端查询范围。
 */
@Tag("dev")
class AppActorAndScopeIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private AppIdentityTestFixture fixture;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        fixture = AppIdentityTestFixture.create(ENV);
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
    void preservesActorTypesWhenAppAndSystemActorsUseTheSameNumericId() throws SQLException {
        fixture.insertSystemUser(1001L, "same-user", "Same#Pass123");
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");

        AppActorContext appActor = AppActorContext.appUser(1001L);
        AppActorContext systemActor = AppActorContext.sysUser(1001L);
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(1), "127.0.0.1"))) {
            fixture.securityAuditService().append(new AppSecurityAuditDTO(
                "app_user", "1001", "self_checked", appActor.actorType(), appActor.actorId(),
                "identity_revision:1", "identity_revision:1", AppSecurityAuditReason.PASSWORD_CHANGE.code()));
        }
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(2), "127.0.0.1"))) {
            fixture.securityAuditService().append(new AppSecurityAuditDTO(
                "app_user", "1001", "status_changed", systemActor.actorType(), systemActor.actorId(),
                "status:active", "status:disabled", AppSecurityAuditReason.ACCOUNT_STATUS_CHANGE.code()));
        }

        assertThat(appActor).isNotEqualTo(systemActor);
        assertThat(appActor.actorType()).isEqualTo(AppActorType.APP_USER);
        assertThat(systemActor.actorType()).isEqualTo(AppActorType.SYS_USER);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE actor_id = 1001
              AND actor_type IN ('app_user', 'sys_user')
            """)).isEqualTo(2L);
        assertThat(fixture.queryString("""
            SELECT GROUP_CONCAT(actor_type ORDER BY actor_type)
            FROM app_security_audit
            WHERE actor_id = 1001
            """)).isEqualTo("app_user,sys_user");
        assertThat(fixture.securityAuditByRequestId(traceId(1)).getActorType())
            .isEqualTo(AppActorType.APP_USER);
        assertThat(fixture.securityAuditByRequestId(traceId(2)).getActorType())
            .isEqualTo(AppActorType.SYS_USER);
    }

    @Test
    void changesOnlyTheRequiredCredentialAndIdentityRevisions() throws SQLException {
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");
        fixture.authorizeSystemActor(1001L);

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(1001L, "Same#Pass123", "Changed#Pass123", 1L),
            AppActorContext.appUser(1001L)));

        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo("2:1:1");
        assertThat(fixture.withExplicitNonHttpAudit(() -> fixture.identityService().authenticatePassword(
            new org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO(
                "same-user", "Changed#Pass123", "desktop"), fixture.activeClient())).userId()).isEqualTo(1001L);
        assertThatThrownBy(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(1001L, "Reset#Pass123", 1L), AppActorContext.sysUser(1001L)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*) FROM app_security_audit WHERE action = 'password_reset'
            """)).isZero();

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(1001L, "Reset#Pass123", 2L), AppActorContext.sysUser(1001L)));
        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo("3:1:1");

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1001L, AppIdentityStatus.DISABLED, 1L),
            AppActorContext.sysUser(1001L)));

        assertThat(fixture.queryString("""
            SELECT CONCAT(status, ':', credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo("disabled:3:2:1");
        assertThatThrownBy(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1001L, AppIdentityStatus.ACTIVE, 1L), AppActorContext.sysUser(1001L)))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().requireActive(1001L))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE actor_type = 'sys_user'
              AND actor_id = 1001
              AND action = 'status_changed'
            """)).isEqualTo(1L);
    }

    @Test
    void tracksRequiredPasswordChangesAcrossOperationalCreateResetAndFirstSelfChange() throws SQLException {
        fixture.authorizeSystemActor(9001L);

        var registered = fixture.withExplicitNonHttpAudit(() -> fixture.identityService().register(
            new org.dromara.aivideo.identity.dto.RegisterAppUserDTO(
                "must-change-user", "Initial#Pass123", "Must Change User", null, null),
            AppActorContext.sysUser(9001L)));

        assertThat(fixture.queryString("""
            SELECT CONCAT(must_change_password, ':', credential_revision)
            FROM app_user WHERE user_id = ?
            """, registered.userId())).isEqualTo("1:1");
        assertThat(fixture.withExplicitNonHttpAudit(() -> fixture.identityService().authenticatePassword(
            new org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO(
                "must-change-user", "Initial#Pass123", "desktop"), fixture.activeClient())).mustChangePassword())
            .isTrue();
        assertThat(fixture.identityService().requireActive(registered.userId()).mustChangePassword()).isTrue();

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(registered.userId(), "Reset#Pass123", 1L), AppActorContext.sysUser(9001L)));
        assertThat(fixture.queryString("""
            SELECT CONCAT(must_change_password, ':', credential_revision)
            FROM app_user WHERE user_id = ?
            """, registered.userId())).isEqualTo("1:2");
        assertThat(fixture.withExplicitNonHttpAudit(() -> fixture.identityService().authenticatePassword(
            new org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO(
                "must-change-user", "Reset#Pass123", "desktop"), fixture.activeClient())).mustChangePassword())
            .isTrue();
        assertThat(fixture.identityService().requireActive(registered.userId()).mustChangePassword()).isTrue();

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(registered.userId(), "Reset#Pass123", "Changed#Pass123", 2L),
            AppActorContext.appUser(registered.userId())));
        assertThat(fixture.queryString("""
            SELECT CONCAT(must_change_password, ':', credential_revision)
            FROM app_user WHERE user_id = ?
            """, registered.userId())).isEqualTo("0:3");
        assertThat(fixture.withExplicitNonHttpAudit(() -> fixture.identityService().authenticatePassword(
            new org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO(
                "must-change-user", "Changed#Pass123", "desktop"), fixture.activeClient())).mustChangePassword())
            .isFalse();
        assertThat(fixture.identityService().requireActive(registered.userId()).mustChangePassword()).isFalse();
    }

    @Test
    void rejectsReusingTheTemporaryPasswordWithoutClearingTheRequiredChangeState() throws SQLException {
        long userId = 1001L;
        fixture.insertAppUser(userId, "must-change-user", "Initial#Pass123");
        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(userId, "Temporary#Pass123", 1L), AppActorContext.sysUser(9001L)));
        long auditCountBeforeRejectedChange = fixture.queryLong("SELECT COUNT(*) FROM app_security_audit");
        int invalidationCountBeforeRejectedChange = fixture.sessionInvalidationEvents().size();

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(userId, "Temporary#Pass123", "Temporary#Pass123", 2L),
            AppActorContext.appUser(userId))))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryString("""
            SELECT CONCAT(must_change_password, ':', credential_revision)
            FROM app_user WHERE user_id = ?
            """, userId)).isEqualTo("1:2");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit"))
            .isEqualTo(auditCountBeforeRejectedChange);
        assertThat(fixture.sessionInvalidationEvents()).hasSize(invalidationCountBeforeRejectedChange);

        assertThat(fixture.withExplicitNonHttpAudit(() -> fixture.identityService().authenticatePassword(
            new org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO(
                "must-change-user", "Temporary#Pass123", "desktop"), fixture.activeClient())).mustChangePassword())
            .isTrue();
    }

    @Test
    void requiresAnExplicitAuditContextAndRestoresNestedScopes() {
        assertThatThrownBy(AppAuditRequestContextHolder::current).isInstanceOf(ServiceException.class);

        AppAuditRequestContext outer = new AppAuditRequestContext(traceId(70), "203.0.113.70");
        try (AppAuditRequestContextHolder.Scope outerScope = AppAuditRequestContextHolder.bindTrusted(outer)) {
            assertThat(AppAuditRequestContextHolder.current()).isEqualTo(outer);
            try (AppAuditRequestContextHolder.Scope innerScope = AppAuditRequestContextHolder.bindTrusted(
                AppAuditRequestContext.nonHttp())) {
                assertThat(AppAuditRequestContextHolder.current()).isEqualTo(AppAuditRequestContext.nonHttp());
            }
            assertThat(AppAuditRequestContextHolder.current()).isEqualTo(outer);
        }

        assertThatThrownBy(AppAuditRequestContextHolder::current).isInstanceOf(ServiceException.class);
    }

    @Test
    void rejectsAppUserPasswordResetWithoutVerifiedRecoveryProof() throws SQLException {
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");
        String passwordHash = fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1001");
        String revisions = fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """);
        long auditCount = fixture.queryLong("SELECT COUNT(*) FROM app_security_audit");

        assertThatThrownBy(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(1001L, "Reset#Pass123", 1L), AppActorContext.appUser(1001L)))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1001"))
            .isEqualTo(passwordHash);
        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo(revisions);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isEqualTo(auditCount);
    }

    @Test
    void treatsSocialBindingAndUnbindingAsIdentityRevisionChanges() throws SQLException {
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");
        fixture.authorizeSystemActor(1001L);

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1001L, "github", "subject-1001", 1L), AppActorContext.appUser(1001L)));
        long socialIdentityId = fixture.queryLong("""
            SELECT social_identity_id
            FROM app_social_identity
            WHERE user_id = 1001 AND provider = 'github'
            """);
        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo("1:2:1");

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().unbindSocialIdentity(
            1001L, socialIdentityId, AppActorContext.sysUser(1001L)));

        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1001
            """)).isEqualTo("1:3:1");
        assertThat(fixture.queryString("SELECT status FROM app_social_identity WHERE social_identity_id = ?", socialIdentityId))
            .isEqualTo("inactive");
    }

    @Test
    void publishesAppOnlySessionInvalidationEventsForCommittedCredentialAndIdentityMutations() throws SQLException {
        long passwordUserId = 11_001L;
        long resetUserId = 11_002L;
        long disabledUserId = 11_003L;
        long socialUserId = 11_004L;
        fixture.insertAppUser(passwordUserId, "password-event-user", "Password#123");
        fixture.insertAppUser(resetUserId, "reset-event-user", "Reset#Pass123");
        fixture.insertAppUser(disabledUserId, "disabled-event-user", "Disabled#Pass123");
        fixture.insertAppUser(socialUserId, "social-event-user", "Social#Pass123");
        fixture.authorizeSystemActor(9001L);

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(passwordUserId, "Password#123", "Password#456", 1L),
            AppActorContext.appUser(passwordUserId)));
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(resetUserId, "Reset#Next123", 1L), AppActorContext.sysUser(9001L)));
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(disabledUserId, AppIdentityStatus.DISABLED, 1L), AppActorContext.sysUser(9001L)));
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(socialUserId, "github", "event-subject", 1L), AppActorContext.appUser(socialUserId)));
        long socialIdentityId = fixture.queryLong("""
            SELECT social_identity_id
            FROM app_social_identity
            WHERE user_id = ? AND provider = 'github'
            """, socialUserId);
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().unbindSocialIdentity(
            socialUserId, socialIdentityId, AppActorContext.appUser(socialUserId)));

        assertThat(fixture.sessionInvalidationEvents()).containsExactly(
            AppSessionInvalidationEvent.forUsers(Set.of(passwordUserId), AppSessionInvalidationReason.CREDENTIAL_CHANGED),
            AppSessionInvalidationEvent.forUsers(Set.of(resetUserId), AppSessionInvalidationReason.CREDENTIAL_CHANGED),
            AppSessionInvalidationEvent.forUsers(Set.of(disabledUserId), AppSessionInvalidationReason.USER_DISABLED),
            AppSessionInvalidationEvent.forUsers(Set.of(socialUserId), AppSessionInvalidationReason.IDENTITY_CHANGED),
            AppSessionInvalidationEvent.forUsers(Set.of(socialUserId), AppSessionInvalidationReason.IDENTITY_CHANGED));
    }

    @Test
    void doesNotDeliverIdentityMutationInvalidationEventWhenOuterTransactionRollsBack() throws SQLException {
        long userId = 11_005L;
        fixture.insertAppUser(userId, "rollback-event-user", "Rollback#Pass123");

        fixture.withRollbackOnlyTransaction(() -> fixture.withExplicitNonHttpAudit(() ->
            fixture.identityService().changePassword(
                new ChangeAppPasswordDTO(userId, "Rollback#Pass123", "Rollback#Next123", 1L),
                AppActorContext.appUser(userId))));

        assertThat(fixture.queryLong("SELECT credential_revision FROM app_user WHERE user_id = ?", userId)).isEqualTo(1L);
        assertThat(fixture.sessionInvalidationEvents()).isEmpty();
    }

    @Test
    void rejectsCrossUserMutationsWithoutChangingTheTargetOrAppendingAnAudit() throws SQLException {
        fixture.insertAppUser(1001L, "actor-user", "Actor#Pass123");
        fixture.insertAppUser(1002L, "target-user", "Target#Pass123");
        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1002L, "github", "target-subject", 1L), AppActorContext.sysUser(9001L)));
        long socialIdentityId = fixture.queryLong("""
            SELECT social_identity_id
            FROM app_social_identity
            WHERE user_id = 1002 AND provider = 'github'
            """);
        String targetPasswordHash = fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1002");
        String targetRevisions = fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1002
            """);
        String socialStatus = fixture.queryString("SELECT status FROM app_social_identity WHERE social_identity_id = ?", socialIdentityId);
        long auditCount = fixture.queryLong("SELECT COUNT(*) FROM app_security_audit");
        AppActorContext crossUserActor = AppActorContext.appUser(1001L);

        assertThatThrownBy(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(1002L, "Target#Pass123", "Changed#Pass123", 1L), crossUserActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(1002L, "Reset#Pass123", 1L), crossUserActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1002L, AppIdentityStatus.DISABLED, 2L), crossUserActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1002L, "google", "target-google", 2L), crossUserActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().unbindSocialIdentity(1002L, socialIdentityId, crossUserActor))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1002"))
            .isEqualTo(targetPasswordHash);
        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1002
            """)).isEqualTo(targetRevisions);
        assertThat(fixture.queryString("SELECT status FROM app_social_identity WHERE social_identity_id = ?", socialIdentityId))
            .isEqualTo(socialStatus);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isEqualTo(auditCount);

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1002L, AppIdentityStatus.DISABLED, 2L), AppActorContext.sysUser(9001L)));
        assertThat(fixture.queryString("SELECT status FROM app_user WHERE user_id = 1002")).isEqualTo("disabled");
    }

    @Test
    void rejectsBareSystemActorsForEveryCrossUserMutationWithoutChangingDataOrAudit() throws SQLException {
        fixture.insertAppUser(1002L, "target-user", "Target#Pass123");
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1002L, "github", "target-subject", 1L), AppActorContext.appUser(1002L)));
        long socialIdentityId = fixture.queryLong("""
            SELECT social_identity_id
            FROM app_social_identity
            WHERE user_id = 1002 AND provider = 'github'
            """);
        String passwordHash = fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1002");
        String revisions = fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1002
            """);
        String socialStatus = fixture.queryString("SELECT status FROM app_social_identity WHERE social_identity_id = ?", socialIdentityId);
        long auditCount = fixture.queryLong("SELECT COUNT(*) FROM app_security_audit");
        AppActorContext bareSystemActor = AppActorContext.sysUser(9001L);

        assertThatThrownBy(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(1002L, "Target#Pass123", "Changed#Pass123", 1L), bareSystemActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().resetPassword(
            new ResetAppPasswordDTO(1002L, "Reset#Pass123", 1L), bareSystemActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1002L, AppIdentityStatus.DISABLED, 2L), bareSystemActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1002L, "google", "target-google", 2L), bareSystemActor))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.identityService().unbindSocialIdentity(1002L, socialIdentityId, bareSystemActor))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryString("SELECT password_hash FROM app_user WHERE user_id = 1002")).isEqualTo(passwordHash);
        assertThat(fixture.queryString("""
            SELECT CONCAT(credential_revision, ':', identity_revision, ':', permission_revision)
            FROM app_user WHERE user_id = 1002
            """)).isEqualTo(revisions);
        assertThat(fixture.queryString("SELECT status FROM app_social_identity WHERE social_identity_id = ?", socialIdentityId))
            .isEqualTo(socialStatus);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isEqualTo(auditCount);

        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changeStatus(
            new ChangeAppUserStatusDTO(1002L, AppIdentityStatus.DISABLED, 2L), bareSystemActor));
        assertThat(fixture.queryString("SELECT status FROM app_user WHERE user_id = 1002")).isEqualTo("disabled");
    }

    @Test
    void keepsDuplicateSocialIdentityAsABusinessFailure() throws SQLException {
        fixture.insertAppUser(1001L, "first-user", "First#Pass123");
        fixture.insertAppUser(1002L, "second-user", "Second#Pass123");
        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1001L, "github", "shared-subject", 1L), AppActorContext.appUser(1001L)));

        assertThatThrownBy(() -> fixture.identityService().bindSocialIdentity(
            new BindSocialIdentityDTO(1002L, "github", "shared-subject", 1L), AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方身份已绑定");
    }

    @Test
    void usesExplicitNonHttpAuditMetadataInsteadOfGeneratingRequestOrIpValues() throws SQLException {
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");

        fixture.withExplicitNonHttpAudit(() -> fixture.identityService().changePassword(
            new ChangeAppPasswordDTO(1001L, "Same#Pass123", "Changed#Pass123", 1L), AppActorContext.appUser(1001L)));

        assertThat(fixture.queryString("""
            SELECT request_id FROM app_security_audit WHERE action = 'password_changed'
            """)).isEqualTo("non-http");
        assertThat(fixture.queryString("""
            SELECT ip_address FROM app_security_audit WHERE action = 'password_changed'
            """)).isEqualTo("non-http");
    }

    @Test
    void propagatesTrustedAuditRequestMetadataWithoutReadingRawRequestValues() throws SQLException {
        fixture.insertAppUser(1001L, "same-user", "Same#Pass123");

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(40), "203.0.113.8"))) {
            fixture.identityService().changePassword(
                new ChangeAppPasswordDTO(1001L, "Same#Pass123", "Changed#Pass123", 1L), AppActorContext.appUser(1001L));
        }

        assertThat(fixture.securityAuditByRequestId(traceId(40)).getIpAddress()).isEqualTo("203.0.113.8");
    }

    @Test
    void rejectsRawCredentialsAndSecretsFromEveryWritableSecurityAuditTextField() throws SQLException {
        List<AppSecurityAuditDTO> sensitiveCommands = List.of(
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "password=Same#Pass123", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "$2a$10$PzR13JrPK4M4xA1s7AsHfORqLGgQg7siF4lAoravYRwrPpBWKJH9e",
                "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "client_secret=secret-value", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "token=app-token-value", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "verification_code=123456", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "password is Same#Pass123", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", "token value app-token-value"),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", "Same#Pass123"),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", "client secret Client#Secret123"),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", "验证码 123456"),
            new AppSecurityAuditDTO(
                "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", "密码 123456"),
            new AppSecurityAuditDTO(
                "app_user", "access_token:raw-value", "password_changed", AppActorType.APP_USER, 1001L,
                "credential_revision:1", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code()));

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(10), "127.0.0.1"))) {
            for (AppSecurityAuditDTO command : sensitiveCommands) {
                assertThatThrownBy(() -> fixture.securityAuditService().append(command))
                    .isInstanceOf(ServiceException.class);
            }
        }
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    @Test
    void rejectsUnknownAuditResourcesAndControlCharacterReasons() throws SQLException {
        List<AppSecurityAuditDTO> invalidCommands = List.of(
            new AppSecurityAuditDTO(
                "unbounded_resource", "1001", "self_checked", AppActorType.APP_USER, 1001L,
                "identity_revision:1", "identity_revision:1", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "user-1001", "self_checked", AppActorType.APP_USER, 1001L,
                "identity_revision:1", "identity_revision:1", AppSecurityAuditReason.PASSWORD_CHANGE.code()),
            new AppSecurityAuditDTO(
                "app_user", "1001", "self_checked", AppActorType.APP_USER, 1001L,
                "identity_revision:1", "identity_revision:1", "含有\n换行"));

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(30), "127.0.0.1"))) {
            for (AppSecurityAuditDTO command : invalidCommands) {
                assertThatThrownBy(() -> fixture.securityAuditService().append(command))
                    .isInstanceOf(ServiceException.class);
            }
        }
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    @Test
    void rejectsDirectAuditAppendWithoutTrustedContextAndDoesNotWrite() throws SQLException {
        AppSecurityAuditDTO command = validSecurityAuditCommand();

        assertThatThrownBy(() -> fixture.securityAuditService().append(command))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    @Test
    void persistsDirectAuditAppendMetadataFromBoundTrustedContext() throws SQLException {
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(50), "203.0.113.50"))) {
            fixture.securityAuditService().append(validSecurityAuditCommand());
        }

        assertThat(fixture.securityAuditByRequestId(traceId(50)).getRequestId()).isEqualTo(traceId(50));
        assertThat(fixture.securityAuditByRequestId(traceId(50)).getIpAddress()).isEqualTo("203.0.113.50");
    }

    @Test
    void allowsDirectAuditAppendWithExplicitNonHttpContext() throws SQLException {
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            AppAuditRequestContext.nonHttp())) {
            fixture.securityAuditService().append(validSecurityAuditCommand());
        }

        assertThat(fixture.securityAuditByRequestId("non-http").getRequestId()).isEqualTo("non-http");
        assertThat(fixture.securityAuditByRequestId("non-http").getIpAddress()).isEqualTo("non-http");
    }

    @Test
    void rejectsFreeFormChineseAuditReasonsThatCouldCarrySensitiveValues() throws SQLException {
        AppSecurityAuditDTO command = new AppSecurityAuditDTO(
            "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
            "credential_revision:1", "credential_revision:2", "用户密码已经重置");

        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            new AppAuditRequestContext(traceId(60), "127.0.0.1"))) {
            assertThatThrownBy(() -> fixture.securityAuditService().append(command))
                .isInstanceOf(ServiceException.class);
        }
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    private static AppSecurityAuditDTO validSecurityAuditCommand() {
        return new AppSecurityAuditDTO(
            "app_user", "1001", "password_changed", AppActorType.APP_USER, 1001L,
            "credential_revision:1", "credential_revision:2", AppSecurityAuditReason.PASSWORD_CHANGE.code());
    }

    private static String traceId(int suffix) {
        return "%032x".formatted(suffix);
    }
}
