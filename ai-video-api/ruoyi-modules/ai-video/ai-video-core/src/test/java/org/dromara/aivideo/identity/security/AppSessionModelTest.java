package org.dromara.aivideo.identity.security;

import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证创作端会话快照和独立登录逻辑的固定契约。
 */
@Tag("dev")
class AppSessionModelTest {

    @Test
    void freezesTheElevenFieldWorkspaceContractAndItsPermissionSet() {
        Set<String> sourcePermissions = new LinkedHashSet<>(Set.of("copy:generate"));
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "opaque-workspace-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", sourcePermissions, 3L, null);

        sourcePermissions.add("copy:read");

        assertThat(Arrays.stream(AppWorkspaceSessionSnapshotDTO.class.getRecordComponents())
            .map(component -> component.getName()))
            .containsExactly(
                "workspaceKey", "workspaceType", "tenantId", "ownerType", "ownerId",
                "billingSubjectType", "billingSubjectId", "roleCode", "permissions",
                "workspaceRevision", "membershipRevision");
        assertThat(workspace.permissions()).containsExactly("copy:generate");
        assertThatThrownBy(() -> workspace.permissions().add("copy:read"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void buildsAStablePersonalWorkspaceSnapshotWithoutLeakingTheTenantIdentifier() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setWorkspaceKeySecret("workspace-key-secret-for-unit-test-32-bytes");
        AppPersonalWorkspaceSnapshotProvider provider = new AppPersonalWorkspaceSnapshotProvider(
            new FixedPermissionService(Set.of("copy:generate", "copy:read")), properties);
        AppUser user = personalUser();

        AppWorkspaceSessionSnapshotDTO first = provider.personalWorkspace(user);
        AppWorkspaceSessionSnapshotDTO second = provider.personalWorkspace(user);

        assertThat(first.workspaceKey()).isEqualTo(second.workspaceKey()).isNotEqualTo("2001");
        assertThat(first.workspaceType()).isEqualTo("personal");
        assertThat(first.tenantId()).isEqualTo(2001L);
        assertThat(first.ownerType()).isEqualTo("app_user");
        assertThat(first.ownerId()).isEqualTo(1001L);
        assertThat(first.billingSubjectType()).isEqualTo("personal");
        assertThat(first.billingSubjectId()).isEqualTo(1001L);
        assertThat(first.roleCode()).isEqualTo("personal_creator");
        assertThat(first.permissions()).containsExactlyInAnyOrder("copy:generate", "copy:read");
        assertThat(first.workspaceRevision()).isEqualTo(7L);
        assertThat(first.membershipRevision()).isNull();
    }

    @Test
    void keepsTheEightFieldPrincipalAndAppLoginUserIndependentFromSystemLoginUser() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "opaque-workspace-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", Set.of("copy:generate"), 7L, null);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            1001L, "creator", "desktop", 2L, 7L, 5L, 3L, workspace);
        AppLoginUser loginUser = new AppLoginUser(principal, "app-session-1");

        assertThat(Arrays.stream(AppPrincipalSnapshotDTO.class.getRecordComponents())
            .map(component -> component.getName()))
            .containsExactly("appUserId", "username", "clientId", "credentialRevision", "identityRevision",
                "permissionRevision", "clientRevision", "workspace");
        assertThat(loginUser.userId()).isEqualTo(1001L);
        assertThat(loginUser.principal()).isSameAs(principal);
    }

    @Test
    void declaresTheAppLogicThroughARegularRegistrarInsteadOfASecondStpLogicBean() {
        assertThat(AppStpLogic.class.getSuperclass().getSimpleName()).isEqualTo("StpLogicJwtForSimple");
        assertThat(AppStpLogicRegistrar.class.isAnnotationPresent(Component.class)).isTrue();
        assertThat(Arrays.stream(AppStpLogicRegistrar.class.getDeclaredMethods())
            .anyMatch(method -> method.isAnnotationPresent(Bean.class)))
            .isFalse();
        assertThat(Arrays.stream(AppStpLogic.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("getPermissionList") || method.getName().equals("getRoleList"))
            .allMatch(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{Object.class})))
            .isTrue();
    }

    @Test
    void keepsPublicSessionDtosAndTheirJsonFreeOfTokenFields() throws Exception {
        AppSessionSummaryDTO summary = new AppSessionSummaryDTO("public-session-id", "desktop", "web", null, true);
        String json = JsonMapper.builder().build().writeValueAsString(summary);

        assertThat(json).doesNotContain("token", "tokenValue");
        assertThat(Stream.of(AppSessionSummaryDTO.class, AppPrincipalSnapshotDTO.class, AppWorkspaceSessionSnapshotDTO.class)
            .flatMap(type -> Arrays.stream(type.getRecordComponents()))
            .map(component -> component.getName().toLowerCase())
            .noneMatch(component -> component.contains("token")))
            .isTrue();
    }

    private AppUser personalUser() {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setPersonalTenantId(2001L);
        user.setIdentityRevision(7L);
        return user;
    }

    /**
     * 为工作区快照测试提供确定性权限集合。
     */
    private record FixedPermissionService(Set<String> permissions) implements IAppPermissionService {

        @Override
        public Set<String> roleCodes(long userId) {
            return Set.of("personal_creator");
        }

        @Override
        public Set<String> permissionCodes(long userId) {
            return permissions;
        }

        @Override
        public void replaceUserRoles(long userId, long expectedPermissionRevision, Set<Long> roleIds,
                                     org.dromara.aivideo.identity.security.AppActorContext actor) {
            throw new UnsupportedOperationException("测试替身不支持写入");
        }

        @Override
        public void replaceRolePermissions(long roleId, long expectedRoleRevision, Set<Long> permissionIds,
                                           org.dromara.aivideo.identity.security.AppActorContext actor) {
            throw new UnsupportedOperationException("测试替身不支持写入");
        }
    }
}
