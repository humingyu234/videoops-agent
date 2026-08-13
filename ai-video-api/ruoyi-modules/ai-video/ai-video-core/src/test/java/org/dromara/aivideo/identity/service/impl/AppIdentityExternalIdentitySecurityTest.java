package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppRole;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppSocialIdentity;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.domain.AppUserRole;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppSocialIdentityMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerifier;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerifier;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppIdentityExternalIdentitySecurityTest {

    @Test
    void authenticatesOnlyAnActiveAppUserBoundToTheVerifiedExternalIdentity() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        AppSocialIdentity socialIdentity = activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001");
        AppUser user = activeUser(1001L);
        when(socialIdentityMapper.selectOne(any())).thenReturn(socialIdentity);
        when(userMapper.selectOne(any())).thenReturn(user);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, auditService,
            mock(ApplicationEventPublisher.class));

        AppAuthenticatedIdentityDTO result = service.authenticateExternalIdentity(
            new AppExternalIdentityDTO("wechat_open", "open-id-1001"),
            new AppAuthClientSnapshotDTO("creator-web", 3L));

        assertThat(result).isEqualTo(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 7L, 9L, 11L));
        ArgumentCaptor<AppSecurityAuditDTO> auditCaptor = ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
        verify(auditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).isEqualTo(new AppSecurityAuditDTO(
            "app_social_identity", "9001", "external_identity_authenticated", AppActorType.APP_USER, 1001L,
            "identity_revision:9", "identity_revision:9",
            AppSecurityAuditReason.EXTERNAL_IDENTITY_AUTHENTICATION.code()));
        verify(userMapper, never()).insert(any(AppUser.class));
    }

    @Test
    void rejectsAnUnboundExternalIdentityWithoutCreatingAUserOrWritingAnAudit() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        when(socialIdentityMapper.selectOne(any())).thenReturn(null);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, auditService,
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.authenticateExternalIdentity(
            new AppExternalIdentityDTO("wechat_open", "unbound-open-id"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方登录不可用");

        verify(userMapper, never()).insert(any(AppUser.class));
        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsABoundExternalIdentityWhenItsAppUserIsDisabledWithoutWritingASuccessAudit() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        AppUser disabledUser = activeUser(1001L);
        disabledUser.setStatus(AppIdentityStatus.DISABLED);
        when(socialIdentityMapper.selectOne(any())).thenReturn(
            activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001"));
        when(userMapper.selectOne(any())).thenReturn(disabledUser);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, auditService,
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.authenticateExternalIdentity(
            new AppExternalIdentityDTO("wechat_open", "open-id-1001"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方登录不可用");

        verifyNoInteractions(auditService);
    }

    @Test
    void rejectsABoundExternalIdentityWhenItsAppUserIsLogicallyDeletedWithoutWritingASuccessAudit() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        when(socialIdentityMapper.selectOne(any())).thenReturn(
            activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001"));
        when(userMapper.selectOne(any())).thenReturn(null);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, auditService,
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.authenticateExternalIdentity(
            new AppExternalIdentityDTO("wechat_open", "open-id-1001"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class);

        ArgumentCaptor<Wrapper<AppUser>> userQueryCaptor = userQueryCaptor();
        verify(userMapper).selectOne(userQueryCaptor.capture());
        assertThat(userQueryCaptor.getValue().getSqlSegment()).contains("del_flag");
        verifyNoInteractions(auditService);
    }

    @Test
    void refusesToUnbindTheLastUsableLoginMethodWithoutMutatingIdentityState() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AppUser user = activeUser(1001L);
        user.setPasswordHash(null);
        AppSocialIdentity target = activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(socialIdentityMapper.selectOne(any())).thenReturn(target);
        when(socialIdentityMapper.selectCount(any())).thenReturn(0L);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, auditService, eventPublisher);

        assertThatThrownBy(() -> service.unbindSocialIdentity(1001L, 9001L, AppActorContext.appUser(1001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("第三方身份解绑不可用");

        verify(socialIdentityMapper, never()).update(isNull(),
            ArgumentMatchers.<LambdaUpdateWrapper<AppSocialIdentity>>any());
        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
        verifyNoInteractions(auditService, eventPublisher);
    }

    @Test
    void permitsUnbindingWhenAnotherActiveSocialIdentityRemains() {
        initializeIdentityMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        AppUser user = activeUser(1001L);
        user.setPasswordHash(null);
        AppSocialIdentity target = activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(socialIdentityMapper.selectOne(any())).thenReturn(target);
        when(socialIdentityMapper.selectCount(any())).thenReturn(1L);
        when(socialIdentityMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppSocialIdentity>>any()))
            .thenReturn(1);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, mock(IAppSecurityAuditService.class),
            mock(ApplicationEventPublisher.class));

        service.unbindSocialIdentity(1001L, 9001L, AppActorContext.appUser(1001L));

        verify(socialIdentityMapper).selectCount(ArgumentMatchers.<Wrapper<AppSocialIdentity>>any());
        verify(socialIdentityMapper).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppSocialIdentity>>any());
        verify(userMapper).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
    }

    @Test
    void permitsUnbindingWhenPasswordPhoneOrEmailStillProvidesALoginPath() {
        initializeIdentityMetadata();
        assertCanUnbindWithLocalCredential(user -> user.setPasswordHash("stored-password-hash"));
        assertCanUnbindWithLocalCredential(user -> user.setPhoneNormalized("13800138000"));
        assertCanUnbindWithLocalCredential(user -> user.setEmailNormalized("creator@example.com"));
    }

    private static void assertCanUnbindWithLocalCredential(java.util.function.Consumer<AppUser> credential) {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppSocialIdentityMapper socialIdentityMapper = mock(AppSocialIdentityMapper.class);
        AppUser user = activeUser(1001L);
        user.setPasswordHash(null);
        credential.accept(user);
        AppSocialIdentity target = activeSocialIdentity(9001L, 1001L, "wechat_open", "open-id-1001");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(socialIdentityMapper.selectOne(any())).thenReturn(target);
        when(socialIdentityMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppSocialIdentity>>any()))
            .thenReturn(1);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        AppIdentityServiceImpl service = service(userMapper, socialIdentityMapper, mock(IAppSecurityAuditService.class),
            mock(ApplicationEventPublisher.class));

        service.unbindSocialIdentity(1001L, 9001L, AppActorContext.appUser(1001L));

        verify(socialIdentityMapper, never()).selectCount(ArgumentMatchers.<Wrapper<AppSocialIdentity>>any());
    }

    private static AppIdentityServiceImpl service(AppUserMapper userMapper,
                                                   AppSocialIdentityMapper socialIdentityMapper,
                                                   IAppSecurityAuditService auditService,
                                                   ApplicationEventPublisher eventPublisher) {
        return new AppIdentityServiceImpl(
            userMapper,
            socialIdentityMapper,
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            auditService,
            new AppPasswordPolicy(),
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            eventPublisher);
    }

    private static AppUser activeUser(long userId) {
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setUsername("creator");
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setPasswordHash("stored-password-hash");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setPermissionRevision(11L);
        return user;
    }

    private static AppSocialIdentity activeSocialIdentity(long socialIdentityId, long userId,
                                                            String provider, String providerSubject) {
        AppSocialIdentity socialIdentity = new AppSocialIdentity();
        socialIdentity.setSocialIdentityId(socialIdentityId);
        socialIdentity.setUserId(userId);
        socialIdentity.setProvider(provider);
        socialIdentity.setProviderSubject(providerSubject);
        socialIdentity.setStatus(AppIdentityStatus.ACTIVE);
        return socialIdentity;
    }

    private static void initializeIdentityMetadata() {
        initializeMetadata(AppUser.class);
        initializeMetadata(AppSocialIdentity.class);
    }

    private static void initializeMetadata(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Wrapper<AppUser>> userQueryCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }
}
