package org.dromara.aivideo.identity;

import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证创作端角色权限只以 app 身份事实源为准。
 */
@Tag("dev")
class AppPermissionTypeIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private AppIdentityTestFixture fixture;

    @BeforeEach
    void setUp() throws Exception {
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
    void resolvesOnlyEffectiveAppMappingsAndNeverSystemPermissions() throws Exception {
        fixture.insertSystemUser(1001L, "same-user", "System#Pass123");
        fixture.insertSystemRolePermission(1001L, "aivideo:studio:create");
        fixture.insertAppUser(1001L, "same-user", "App#Pass123");

        IAppPermissionService permissionService = fixture.permissionService();
        assertThat(permissionService.roleCodes(1001L)).isEmpty();
        assertThat(permissionService.permissionCodes(1001L)).isEmpty();

        LocalDateTime now = LocalDateTime.now();
        fixture.insertAppUserRole(1001L, 1000101L, AppIdentityStatus.ACTIVE, null, null);
        fixture.insertAppUserRole(1001L, 1000102L, AppIdentityStatus.ACTIVE, now.plusHours(1), null);
        fixture.insertAppUserRole(1001L, 1000103L, AppIdentityStatus.ACTIVE, null, now.minusHours(1));
        fixture.insertAppUserRole(1001L, 1000104L, AppIdentityStatus.INACTIVE, null, null);
        fixture.insertAppRolePermission(1000101L, 1000002L, AppIdentityStatus.ACTIVE);

        assertThat(permissionService.roleCodes(1001L)).containsExactly("personal_creator");
        assertThat(permissionService.permissionCodes(1001L)).containsExactly("aivideo:studio:create");

        fixture.executeUpdate("UPDATE app_role SET status = 'inactive' WHERE role_id = 1000101");
        assertThat(permissionService.roleCodes(1001L)).isEmpty();
        assertThat(permissionService.permissionCodes(1001L)).isEmpty();

        fixture.executeUpdate("UPDATE app_role SET status = 'active' WHERE role_id = 1000101");
        fixture.executeUpdate("UPDATE app_role_permission SET status = 'inactive' WHERE role_id = 1000101");
        assertThat(permissionService.permissionCodes(1001L)).isEmpty();

        fixture.executeUpdate("UPDATE app_role_permission SET status = 'active' WHERE role_id = 1000101");
        fixture.executeUpdate("UPDATE app_permission SET status = 'inactive' WHERE permission_id = 1000002");
        assertThat(permissionService.permissionCodes(1001L)).isEmpty();
    }

    @Test
    void replacesUserRolesWithAnAppOnlyRevisionAndRejectsStaleOrUnauthorizedWrites() throws Exception {
        fixture.insertAppUser(2001L, "permission-user", "App#Pass123");
        fixture.insertSystemUser(2001L, "permission-user", "System#Pass123");
        fixture.insertAppUserRole(2001L, 1000101L, AppIdentityStatus.ACTIVE, null, null);
        fixture.insertAppRole(2000101L, "personal_creator_extra", "额外个人创作者", "personal");

        IAppPermissionService permissionService = fixture.permissionService();
        assertThatThrownBy(() -> permissionService.replaceUserRoles(
            2001L, 1L, Set.of(2000101L), AppActorContext.appUser(2001L)))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> permissionService.replaceUserRoles(
            2001L, 1L, Set.of(2000101L), AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 2001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role WHERE user_id = 2001")).isEqualTo(1L);

        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> permissionService.replaceUserRoles(
            2001L, 1L, Set.of(2000101L), AppActorContext.sysUser(9001L)));

        assertThat(fixture.queryLong("SELECT credential_revision FROM app_user WHERE user_id = 2001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT identity_revision FROM app_user WHERE user_id = 2001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 2001")).isEqualTo(2L);
        assertThat(fixture.queryString("SELECT role_id FROM app_user_role WHERE user_id = 2001"))
            .isEqualTo("2000101");
        assertThat(fixture.queryString("SELECT user_name FROM sys_user WHERE user_id = 2001"))
            .isEqualTo("permission-user");
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE resource_type = 'app_user'
              AND resource_id = '2001'
              AND action = 'roles_replaced'
              AND reason = 'user_role_replacement'
              AND actor_type = 'sys_user'
              AND actor_id = 9001
            """)).isEqualTo(1L);
        assertThat(fixture.sessionInvalidationEvents()).containsExactly(
            new AppSessionInvalidationEvent(Set.of(2001L), AppSessionInvalidationReason.PERMISSION_CHANGED));

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> permissionService.replaceUserRoles(
            2001L, 1L, Set.of(1000101L), AppActorContext.sysUser(9001L))))
            .isInstanceOf(ServiceException.class)
            .extracting("code")
            .isEqualTo(46134);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 2001")).isEqualTo(2L);
        assertThat(fixture.queryString("SELECT role_id FROM app_user_role WHERE user_id = 2001"))
            .isEqualTo("2000101");
        assertThat(fixture.sessionInvalidationEvents()).hasSize(1);
    }

    @Test
    void replacesRolePermissionsAndInvalidatesOnlyCurrentEffectiveAppUsers() throws Exception {
        fixture.insertAppUser(3001L, "current-user", "App#Pass123");
        fixture.insertAppUser(3002L, "expired-user", "App#Pass123");
        fixture.insertAppUser(3003L, "inactive-user", "App#Pass123");
        fixture.insertAppUserRole(3001L, 1000101L, AppIdentityStatus.ACTIVE, null, null);
        fixture.insertAppUserRole(3002L, 1000101L, AppIdentityStatus.ACTIVE, null, LocalDateTime.now().minusHours(1));
        fixture.insertAppUserRole(3003L, 1000101L, AppIdentityStatus.INACTIVE, null, null);

        IAppPermissionService permissionService = fixture.permissionService();
        assertThatThrownBy(() -> permissionService.replaceRolePermissions(
            1000101L, 1L, Set.of(1000002L), AppActorContext.appUser(3001L)))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> permissionService.replaceRolePermissions(
            1000101L, 1L, Set.of(1000002L), AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_role_permission WHERE role_id = 1000101")).isZero();

        fixture.authorizeSystemActor(9001L);
        fixture.withExplicitNonHttpAudit(() -> permissionService.replaceRolePermissions(
            1000101L, 1L, Set.of(1000002L), AppActorContext.sysUser(9001L)));

        assertThat(fixture.queryLong("SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(2L);
        assertThat(fixture.queryString("SELECT permission_id FROM app_role_permission WHERE role_id = 1000101"))
            .isEqualTo("1000002");
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 3001")).isEqualTo(2L);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 3002")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 3003")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT credential_revision FROM app_user WHERE user_id = 3001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT identity_revision FROM app_user WHERE user_id = 3001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_permission WHERE permission_id = 1000002")).isEqualTo(1L);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE resource_type = 'app_role'
              AND resource_id = '1000101'
              AND action = 'permissions_replaced'
              AND reason = 'role_permission_replacement'
              AND actor_type = 'sys_user'
              AND actor_id = 9001
            """)).isEqualTo(1L);
        assertThat(fixture.sessionInvalidationEvents()).containsExactly(
            new AppSessionInvalidationEvent(Set.of(3001L), AppSessionInvalidationReason.PERMISSION_CHANGED));

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> permissionService.replaceRolePermissions(
            1000101L, 1L, Set.of(1000001L), AppActorContext.sysUser(9001L))))
            .isInstanceOf(ServiceException.class)
            .extracting("code")
            .isEqualTo(46134);
        assertThat(fixture.queryLong("SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(2L);
        assertThat(fixture.queryString("SELECT permission_id FROM app_role_permission WHERE role_id = 1000101"))
            .isEqualTo("1000002");
        assertThat(fixture.sessionInvalidationEvents()).hasSize(1);
    }

    @Test
    void rollsBackAnOuterTransactionWithoutDeliveringTheAfterCommitInvalidationEvent() throws Exception {
        fixture.insertAppUser(5001L, "outer-rollback-user", "App#Pass123");
        fixture.insertAppUserRole(5001L, 1000101L, AppIdentityStatus.ACTIVE, null, null);
        fixture.authorizeSystemActor(9001L);

        fixture.withRollbackOnlyTransaction(() -> fixture.withExplicitNonHttpAudit(() ->
            fixture.permissionService().replaceRolePermissions(
                1000101L, 1L, Set.of(1000002L), AppActorContext.sysUser(9001L))));

        assertThat(fixture.queryLong("SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_role_permission WHERE role_id = 1000101")).isZero();
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 5001")).isEqualTo(1L);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE resource_type = 'app_role'
              AND resource_id = '1000101'
              AND action = 'permissions_replaced'
            """)).isZero();
        assertThat(fixture.sessionInvalidationEvents()).isEmpty();
    }

    @Test
    void recordsInvalidationEventsOnlyAfterCommit() throws NoSuchMethodException {
        TransactionalEventListener listener = TestAppSessionInvalidationEvents.class
            .getDeclaredMethod("record", AppSessionInvalidationEvent.class)
            .getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    void rollsBackRolePermissionMutationAndDoesNotPublishAfterCommitEventWhenAuditFails() throws Exception {
        fixture.insertAppUser(4001L, "rollback-user", "App#Pass123");
        fixture.insertAppUserRole(4001L, 1000101L, AppIdentityStatus.ACTIVE, null, null);
        fixture.authorizeSystemActor(9001L);
        fixture.failNextAuditAppend();

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.permissionService().replaceRolePermissions(
            1000101L, 1L, Set.of(1000002L), AppActorContext.sysUser(9001L))))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryLong("SELECT role_revision FROM app_role WHERE role_id = 1000101")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_role_permission WHERE role_id = 1000101")).isZero();
        assertThat(fixture.queryLong("SELECT permission_revision FROM app_user WHERE user_id = 4001")).isEqualTo(1L);
        assertThat(fixture.sessionInvalidationEvents()).isEmpty();
    }
}
