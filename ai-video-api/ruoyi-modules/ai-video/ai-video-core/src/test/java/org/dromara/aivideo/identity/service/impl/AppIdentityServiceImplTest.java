package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.RecoverAppPasswordDTO;
import org.dromara.aivideo.identity.dto.UpdateAppUserProfileDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppSocialIdentityMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryGrant;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryReservation;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerificationRequest;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerifier;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerifier;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppIdentityServiceImplTest {

    @Test
    void omittedContactsMustKeepExistingValuesWhenUpdatingOnlyTheDisplayName() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        AppUser current = new AppUser();
        current.setUserId(1001L);
        current.setDelFlag("0");
        current.setUsernameNormalized("creator");
        current.setPhoneNormalized("13800000000");
        current.setEmailNormalized("creator@example.com");
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(current);
        when(userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(any())).thenReturn(List.of(1001L));
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        when(operationAuthorizer.isAuthorized(eq(AppActorContext.sysUser(9001L)),
            eq(org.dromara.aivideo.identity.security.AppIdentityOperation.UPDATE_APP_USER), eq(1001L))).thenReturn(true);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            mock(IAppSecurityAuditService.class),
            new AppPasswordPolicy(),
            operationAuthorizer,
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            mock(ApplicationEventPublisher.class));

        service.updateProfile(new UpdateAppUserProfileDTO(1001L, "创作者", null, null, false, false, 5L),
            AppActorContext.sysUser(9001L));

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateCaptor = updateWrapperCaptor();
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
            .contains("13800000000", "creator@example.com");
    }

    @Test
    void explicitClearFlagMustClearOnlyTheRequestedContact() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        AppUser current = profileUser();
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(current);
        when(userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(any())).thenReturn(List.of(1001L));
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(userMapper, operationAuthorizer);

        service.updateProfile(new UpdateAppUserProfileDTO(1001L, "创作者", null, null, true, false, 5L),
            AppActorContext.sysUser(9001L));

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateCaptor = updateWrapperCaptor();
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
            .containsNull()
            .contains("creator@example.com")
            .doesNotContain("13800000000");
    }

    @Test
    void replacingOneContactMustKeepTheOtherExistingContact() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(profileUser());
        when(userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(any())).thenReturn(List.of(1001L));
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(userMapper, operationAuthorizer);

        service.updateProfile(new UpdateAppUserProfileDTO(1001L, "创作者", "13900000000", null, false, false, 5L),
            AppActorContext.sysUser(9001L));

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateCaptor = updateWrapperCaptor();
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
            .contains("13900000000", "creator@example.com")
            .doesNotContain("13800000000");
    }

    @Test
    void rejectsMaskedContactTextInsteadOfPersistingItAsANewContact() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(profileUser());
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(userMapper, operationAuthorizer);

        assertThatThrownBy(() -> service.updateProfile(
            new UpdateAppUserProfileDTO(1001L, "创作者", "138****0000", null, false, false, 5L),
            AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class);

        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
    }

    @Test
    void rejectsAContactValueTogetherWithItsClearFlag() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(profileUser());
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(userMapper, operationAuthorizer);

        assertThatThrownBy(() -> service.updateProfile(
            new UpdateAppUserProfileDTO(1001L, "创作者", "13900000000", null, true, false, 5L),
            AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class);

        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
    }

    @Test
    void rejectsBlankContactInsteadOfTreatingItAsAnImplicitClear() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(profileUser());
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(userMapper, operationAuthorizer);

        assertThatThrownBy(() -> service.updateProfile(
            new UpdateAppUserProfileDTO(1001L, "创作者", "  ", null, false, false, 5L),
            AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class);

        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
    }

    @Test
    void profileUpdateAuditsOnlyRevisionMetadataAndInvalidatesTheAffectedAppSessions() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(profileUser());
        when(userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(any())).thenReturn(List.of(1001L));
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        authorizeProfileUpdate(operationAuthorizer);
        AppIdentityServiceImpl service = profileUpdateService(
            userMapper, operationAuthorizer, securityAuditService, eventPublisher);

        service.updateProfile(new UpdateAppUserProfileDTO(
            1001L, "创作者", "13900000000", null, false, false, 5L), AppActorContext.sysUser(9001L));

        org.mockito.ArgumentCaptor<AppSecurityAuditDTO> auditCaptor =
            org.mockito.ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
        verify(securityAuditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.resourceType()).isEqualTo("app_user");
            assertThat(audit.resourceId()).isEqualTo("1001");
            assertThat(audit.action()).isEqualTo("profile_updated");
            assertThat(audit.actorType()).isEqualTo(AppActorType.SYS_USER);
            assertThat(audit.actorId()).isEqualTo(9001L);
            assertThat(audit.beforeDigest()).isEqualTo("identity_revision:5");
            assertThat(audit.afterDigest()).isEqualTo("identity_revision:6");
            assertThat(audit.reason()).isEqualTo(AppSecurityAuditReason.USER_PROFILE_CHANGE.code());
            assertThat(audit.toString()).doesNotContain("13900000000", "creator@example.com");
        });

        org.mockito.ArgumentCaptor<AppSessionInvalidationEvent> eventCaptor =
            org.mockito.ArgumentCaptor.forClass(AppSessionInvalidationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appUserIds()).containsExactly(1001L);
        assertThat(eventCaptor.getValue().reason()).isEqualTo(AppSessionInvalidationReason.IDENTITY_CHANGED);
    }

    @Test
    void rejectsReusingTheCurrentPasswordBeforeUpdatingAuditingOrInvalidatingSessions() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AppPasswordPolicy passwordPolicy = new AppPasswordPolicy();
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setPasswordHash(passwordPolicy.hash("Temporary#Pass123"));
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            passwordPolicy,
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            eventPublisher);

        assertThatThrownBy(() -> service.changePassword(
            new ChangeAppPasswordDTO(1001L, "Temporary#Pass123", "Temporary#Pass123", 2L),
            AppActorContext.appUser(1001L)))
            .isInstanceOf(ServiceException.class);

        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
        verifyNoInteractions(securityAuditService, eventPublisher);
    }

    @Test
    void recoversPasswordOnlyFromAnAtomicallyConsumedAppRecoveryGrantAndInvalidatesAppSessions() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AppPasswordRecoveryVerifier recoveryVerifier = mock(AppPasswordRecoveryVerifier.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        AppPasswordPolicy passwordPolicy = new AppPasswordPolicy();
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setPhoneNormalized("13800138000");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setMustChangePassword(true);
        user.setPasswordHash(passwordPolicy.hash("Temporary#Pass123"));
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        when(recoveryVerifier.reserve(any())).thenReturn(recoveryReservation());
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            passwordPolicy,
            operationAuthorizer,
            mock(AppSelfRegistrationVerifier.class),
            recoveryVerifier,
            eventPublisher);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.recoverPassword(new RecoverAppPasswordDTO("challenge-opaque", "123456", "Recovered#Pass456"),
                new AppAuthClientSnapshotDTO("creator-web", 3L));
            verify(recoveryVerifier, never()).commit(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            TransactionSynchronizationManager.getSynchronizations().forEach(
                synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateCaptor = updateWrapperCaptor();
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSegment())
            .contains("phone_normalized", "credential_revision", "identity_revision", "status", "del_flag");
        assertThat(updateCaptor.getValue().getParamNameValuePairs().values())
            .noneMatch(value -> "123456".equals(value));
        org.mockito.ArgumentCaptor<AppSecurityAuditDTO> auditCaptor =
            org.mockito.ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
        verify(securityAuditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).isEqualTo(new AppSecurityAuditDTO(
            "app_user", "1001", "password_recovered", AppActorType.APP_USER, 1001L,
            "credential_revision:7", "credential_revision:8", AppSecurityAuditReason.PASSWORD_RECOVERY.code()));
        org.mockito.ArgumentCaptor<AppSessionInvalidationEvent> eventCaptor =
            org.mockito.ArgumentCaptor.forClass(AppSessionInvalidationEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().appUserIds()).containsExactly(1001L);
        assertThat(eventCaptor.getValue().reason()).isEqualTo(AppSessionInvalidationReason.CREDENTIAL_CHANGED);
        org.mockito.ArgumentCaptor<AppPasswordRecoveryVerificationRequest> recoveryRequestCaptor =
            org.mockito.ArgumentCaptor.forClass(AppPasswordRecoveryVerificationRequest.class);
        verify(recoveryVerifier).reserve(recoveryRequestCaptor.capture());
        assertThat(recoveryRequestCaptor.getValue()).satisfies(request -> {
            assertThat(request.clientId()).isEqualTo("creator-web");
            assertThat(request.clientRevision()).isEqualTo(3L);
            assertThat(request.toString()).doesNotContain("challenge-opaque", "123456");
        });
        verify(recoveryVerifier).commit(recoveryReservation());
        verifyNoInteractions(operationAuthorizer);
    }

    @Test
    void rejectsARecoveryGrantWhenTheVerifiedChannelNoLongerHasAContact() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppPasswordRecoveryVerifier recoveryVerifier = mock(AppPasswordRecoveryVerifier.class);
        AppPasswordPolicy passwordPolicy = new AppPasswordPolicy();
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setPasswordHash(passwordPolicy.hash("Temporary#Pass123"));
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        when(recoveryVerifier.reserve(any())).thenReturn(recoveryReservation());
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            passwordPolicy,
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            recoveryVerifier,
            eventPublisher);

        assertThatThrownBy(() -> service.recoverPassword(
            new RecoverAppPasswordDTO("challenge-opaque", "123456", "Recovered#Pass456"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class);

        verify(userMapper, never()).update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any());
        verifyNoInteractions(securityAuditService, eventPublisher);
    }

    @Test
    void constrainsRecoveryByTheEmailContactWhenTheConsumedGrantUsesEmail() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppPasswordRecoveryVerifier recoveryVerifier = mock(AppPasswordRecoveryVerifier.class);
        AppPasswordPolicy passwordPolicy = new AppPasswordPolicy();
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setEmailNormalized("creator@example.com");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setPasswordHash(passwordPolicy.hash("Temporary#Pass123"));
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        when(recoveryVerifier.reserve(any())).thenReturn(new AppPasswordRecoveryReservation(
            "challenge-opaque", "reservation-opaque", new AppPasswordRecoveryGrant(
                1001L, AppVerificationChannel.EMAIL, 7L, 9L)));
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            mock(IAppSecurityAuditService.class),
            passwordPolicy,
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            recoveryVerifier,
            mock(ApplicationEventPublisher.class));

        service.recoverPassword(new RecoverAppPasswordDTO("challenge-opaque", "123456", "Recovered#Pass456"),
            new AppAuthClientSnapshotDTO("creator-web", 3L));

        org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateCaptor = updateWrapperCaptor();
        verify(userMapper).update(isNull(), updateCaptor.capture());
        assertThat(updateCaptor.getValue().getSqlSegment())
            .contains("email_normalized")
            .doesNotContain("phone_normalized");
    }

    @Test
    void validatesTheNewRecoveryPasswordBeforeConsumingTheOneTimeChallenge() {
        AppPasswordRecoveryVerifier recoveryVerifier = mock(AppPasswordRecoveryVerifier.class);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            mock(AppUserMapper.class),
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            mock(IAppSecurityAuditService.class),
            new AppPasswordPolicy(),
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            recoveryVerifier,
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.recoverPassword(
            new RecoverAppPasswordDTO("challenge-opaque", "123456", "short"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class);

        verifyNoInteractions(recoveryVerifier);
    }

    @Test
    void releasesTheReservedRecoveryChallengeWhenTheAuditWriteFails() {
        initializeAppUserMetadata();
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppPasswordRecoveryVerifier recoveryVerifier = mock(AppPasswordRecoveryVerifier.class);
        AppPasswordPolicy passwordPolicy = new AppPasswordPolicy();
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setPhoneNormalized("13800138000");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setPasswordHash(passwordPolicy.hash("Temporary#Pass123"));
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        when(userMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppUser>>any())).thenReturn(1);
        AppPasswordRecoveryReservation reservation = recoveryReservation();
        when(recoveryVerifier.reserve(any())).thenReturn(reservation);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        doThrow(new ServiceException("audit failure")).when(securityAuditService).append(any());
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            passwordPolicy,
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            recoveryVerifier,
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.recoverPassword(
            new RecoverAppPasswordDTO("challenge-opaque", "123456", "Recovered#Pass456"),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class)
            .hasMessageContaining("audit failure");

        verify(recoveryVerifier).release(reservation);
        verify(recoveryVerifier, never()).commit(any());
    }

    @Test
    void authenticatesOnlyAnActiveUserWhoseVerifiedLoginGrantStillMatchesCurrentRevisions() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setUsername("creator");
        user.setPhoneNormalized("13800138000");
        user.setCredentialRevision(7L);
        user.setIdentityRevision(9L);
        user.setPermissionRevision(11L);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            new AppPasswordPolicy(),
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            mock(ApplicationEventPublisher.class));

        AppAuthenticatedIdentityDTO identity = service.authenticateVerifiedContact(
            new AppLoginVerificationGrant(1001L, AppVerificationChannel.PHONE, 7L, 9L),
            new AppAuthClientSnapshotDTO("creator-web", 3L));

        assertThat(identity).isEqualTo(new AppAuthenticatedIdentityDTO(1001L, "creator", false, 7L, 9L, 11L));
        org.mockito.ArgumentCaptor<AppSecurityAuditDTO> auditCaptor =
            org.mockito.ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
        verify(securityAuditService).append(auditCaptor.capture());
        assertThat(auditCaptor.getValue()).satisfies(audit -> {
            assertThat(audit.action()).isEqualTo("verification_code_authenticated");
            assertThat(audit.reason()).isEqualTo("verification_code_authentication");
            assertThat(audit.beforeDigest()).isEqualTo("credential_revision:7");
            assertThat(audit.afterDigest()).isEqualTo("credential_revision:7");
        });
    }

    @Test
    void rejectsAStaleLoginGrantBeforeWritingAuthenticationAudit() {
        AppUserMapper userMapper = mock(AppUserMapper.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setDelFlag("0");
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setPhoneNormalized("13800138000");
        user.setCredentialRevision(8L);
        user.setIdentityRevision(9L);
        when(userMapper.selectOne(ArgumentMatchers.<Wrapper<AppUser>>any())).thenReturn(user);
        AppIdentityServiceImpl service = new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            new AppPasswordPolicy(),
            mock(AppIdentityOperationAuthorizer.class),
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            mock(ApplicationEventPublisher.class));

        assertThatThrownBy(() -> service.authenticateVerifiedContact(
            new AppLoginVerificationGrant(1001L, AppVerificationChannel.PHONE, 7L, 9L),
            new AppAuthClientSnapshotDTO("creator-web", 3L)))
            .isInstanceOf(ServiceException.class);

        verifyNoInteractions(securityAuditService);
    }

    private static void initializeAppUserMetadata() {
        if (TableInfoHelper.getTableInfo(AppUser.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppUser.class);
        }
    }

    private static AppPasswordRecoveryReservation recoveryReservation() {
        return new AppPasswordRecoveryReservation("challenge-opaque", "reservation-opaque",
            new AppPasswordRecoveryGrant(1001L, AppVerificationChannel.PHONE, 7L, 9L));
    }

    private static AppUser profileUser() {
        AppUser current = new AppUser();
        current.setUserId(1001L);
        current.setDelFlag("0");
        current.setUsernameNormalized("creator");
        current.setPhoneNormalized("13800000000");
        current.setEmailNormalized("creator@example.com");
        return current;
    }

    private static void authorizeProfileUpdate(AppIdentityOperationAuthorizer operationAuthorizer) {
        when(operationAuthorizer.isAuthorized(eq(AppActorContext.sysUser(9001L)),
            eq(org.dromara.aivideo.identity.security.AppIdentityOperation.UPDATE_APP_USER), eq(1001L))).thenReturn(true);
    }

    private static AppIdentityServiceImpl profileUpdateService(AppUserMapper userMapper,
                                                               AppIdentityOperationAuthorizer operationAuthorizer) {
        return profileUpdateService(userMapper, operationAuthorizer,
            mock(IAppSecurityAuditService.class), mock(ApplicationEventPublisher.class));
    }

    private static AppIdentityServiceImpl profileUpdateService(AppUserMapper userMapper,
                                                               AppIdentityOperationAuthorizer operationAuthorizer,
                                                               IAppSecurityAuditService securityAuditService,
                                                               ApplicationEventPublisher eventPublisher) {
        return new AppIdentityServiceImpl(
            userMapper,
            mock(AppSocialIdentityMapper.class),
            mock(AppRoleMapper.class),
            mock(AppUserRoleMapper.class),
            securityAuditService,
            new AppPasswordPolicy(),
            operationAuthorizer,
            mock(AppSelfRegistrationVerifier.class),
            mock(AppPasswordRecoveryVerifier.class),
            eventPublisher);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.mockito.ArgumentCaptor<LambdaUpdateWrapper<AppUser>> updateWrapperCaptor() {
        return (org.mockito.ArgumentCaptor) org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
