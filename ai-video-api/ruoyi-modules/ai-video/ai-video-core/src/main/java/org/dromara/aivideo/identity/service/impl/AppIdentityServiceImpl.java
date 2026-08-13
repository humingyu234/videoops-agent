package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO;
import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ChangeAppUserStatusDTO;
import org.dromara.aivideo.identity.dto.RegisterAppUserDTO;
import org.dromara.aivideo.identity.dto.RecoverAppPasswordDTO;
import org.dromara.aivideo.identity.dto.ResetAppPasswordDTO;
import org.dromara.aivideo.identity.dto.UpdateAppUserProfileDTO;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppIdentitySnapshotDTO;
import org.dromara.aivideo.identity.dto.AppRegisteredIdentityDTO;
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
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppLoginVerificationGrant;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryGrant;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryReservation;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerificationRequest;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerifier;
import org.dromara.aivideo.identity.security.AppSelfRegistrationGrant;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerificationRequest;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerifier;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 创作端身份、密码和第三方身份的独立实现。
 */
@Service
public class AppIdentityServiceImpl implements IAppIdentityService {

    private static final String PERSONAL_CREATOR_ROLE = "personal_creator";
    private static final String APP_USER_RESOURCE = "app_user";
    private static final String SOCIAL_IDENTITY_RESOURCE = "app_social_identity";

    private final AppUserMapper userMapper;
    private final AppSocialIdentityMapper socialIdentityMapper;
    private final AppRoleMapper roleMapper;
    private final AppUserRoleMapper userRoleMapper;
    private final IAppSecurityAuditService securityAuditService;
    private final AppPasswordPolicy passwordPolicy;
    private final AppIdentityOperationAuthorizer operationAuthorizer;
    private final AppSelfRegistrationVerifier selfRegistrationVerifier;
    private final AppPasswordRecoveryVerifier passwordRecoveryVerifier;
    private final ApplicationEventPublisher eventPublisher;

    public AppIdentityServiceImpl(AppUserMapper userMapper,
                                  AppSocialIdentityMapper socialIdentityMapper,
                                  AppRoleMapper roleMapper,
                                  AppUserRoleMapper userRoleMapper,
                                  IAppSecurityAuditService securityAuditService,
                                  AppPasswordPolicy passwordPolicy,
                                  AppIdentityOperationAuthorizer operationAuthorizer,
                                  AppSelfRegistrationVerifier selfRegistrationVerifier,
                                  AppPasswordRecoveryVerifier passwordRecoveryVerifier,
                                  ApplicationEventPublisher eventPublisher) {
        this.userMapper = userMapper;
        this.socialIdentityMapper = socialIdentityMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.securityAuditService = securityAuditService;
        this.passwordPolicy = passwordPolicy;
        this.operationAuthorizer = operationAuthorizer;
        this.selfRegistrationVerifier = selfRegistrationVerifier;
        this.passwordRecoveryVerifier = passwordRecoveryVerifier;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 注册创作端用户、个人租户和默认个人创作者角色。
     *
     * @param command 注册命令
     * @param actor 操作者
     * @return 新建身份
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public AppRegisteredIdentityDTO register(RegisterAppUserDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null) {
            throw new ServiceException("注册参数不能为空");
        }
        requirePlatformOperation(actor, AppIdentityOperation.REGISTER_APP_USER, 0L);
        return registerUnderActor(command, actor, true);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public AppRegisteredIdentityDTO registerSelf(RegisterAppUserDTO command, AppSelfRegistrationGrant grant) {
        if (command == null) {
            throw new ServiceException("注册参数不能为空");
        }
        if (grant == null) {
            throw new ServiceException("自注册凭证不能为空");
        }
        String registrationGrantId = trimRequired(grant.registrationGrantId(), "自注册凭证");
        String clientId = trimRequired(grant.clientId(), "客户端标识");
        String usernameNormalized = normalizeRequired(command.username(), "用户名");
        String phoneNormalized = normalizeOptional(command.phone());
        String emailNormalized = normalizeOptional(command.email());
        AppSelfRegistrationVerificationRequest verificationRequest =
            new AppSelfRegistrationVerificationRequest(
                registrationGrantId, clientId, usernameNormalized, phoneNormalized, emailNormalized);
        if (!selfRegistrationVerifier.verifyAndConsume(verificationRequest)) {
            throw new ServiceException("自注册验证未通过");
        }
        return registerUnderActor(command, AppActorContext.appUser(IdWorker.getId()), false);
    }

    private AppRegisteredIdentityDTO registerUnderActor(RegisterAppUserDTO command, AppActorContext actor,
                                                     boolean mustChangePassword) {
        if (command == null) {
            throw new ServiceException("注册参数不能为空");
        }
        String username = trimRequired(command.username(), "用户名");
        String usernameNormalized = normalizeRequired(command.username(), "用户名");
        String phoneNormalized = normalizeOptional(command.phone());
        String emailNormalized = normalizeOptional(command.email());

        AppUser user = new AppUser();
        if (actor.actorType() == AppActorType.APP_USER) {
            user.setUserId(actor.actorId());
        }
        user.setUsername(username);
        user.setUsernameNormalized(usernameNormalized);
        user.setPasswordHash(passwordPolicy.hash(command.password()));
        user.setPhoneNormalized(phoneNormalized);
        user.setEmailNormalized(emailNormalized);
        user.setPersonalTenantId(IdWorker.getId());
        user.setDisplayName(defaultDisplayName(command.displayName(), username));
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setMustChangePassword(mustChangePassword);
        user.setCredentialRevision(1L);
        user.setIdentityRevision(1L);
        user.setPermissionRevision(1L);
        applyCreateActor(user, actor);
        try {
            assertIdentityAvailable(usernameNormalized, phoneNormalized, emailNormalized);
            if (userMapper.insert(user) != 1 || user.getUserId() == null) {
                throw new ServiceException("创作端用户注册失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ServiceException("创作端身份已存在", exception);
        } catch (ConcurrencyFailureException exception) {
            throw new ServiceException("创作端身份注册冲突，请重试", exception);
        }

        AppRole personalCreator = roleMapper.selectOne(new LambdaQueryWrapper<AppRole>()
            .eq(AppRole::getRoleCode, PERSONAL_CREATOR_ROLE)
            .eq(AppRole::getStatus, AppIdentityStatus.ACTIVE));
        if (personalCreator == null) {
            throw new ServiceException("个人创作者角色不存在");
        }
        AppUserRole userRole = new AppUserRole();
        userRole.setUserId(user.getUserId());
        userRole.setRoleId(personalCreator.getRoleId());
        userRole.setStatus(AppIdentityStatus.ACTIVE);
        applyCreateActor(userRole, actor);
        if (userRoleMapper.insert(userRole) != 1) {
            throw new ServiceException("创作端用户角色分配失败");
        }

        appendAudit(APP_USER_RESOURCE, user.getUserId(), "registered", actor,
            null, "identity_revision:1", AppSecurityAuditReason.IDENTITY_REGISTRATION.code());
        return new AppRegisteredIdentityDTO(user.getUserId(), user.getPersonalTenantId());
    }

    /**
     * 仅从创作端用户事实源认证密码。
     *
     * @param command 认证命令
     * @param client 已校验客户端快照
     * @return 已认证身份
     */
    @Override
    public AppAuthenticatedIdentityDTO authenticatePassword(AuthenticatePasswordDTO command, AppAuthClientSnapshotDTO client) {
        if (command == null || client == null || isBlank(command.clientId())
            || !command.clientId().equals(client.clientId()) || client.clientRevision() <= 0) {
            throw new ServiceException("认证客户端参数无效");
        }
        String identifier = normalizeRequired(command.identifier(), "认证标识");
        List<AppUser> users = userMapper.selectList(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getDelFlag, "0")
            .and(wrapper -> wrapper.eq(AppUser::getUsernameNormalized, identifier)
                .or().eq(AppUser::getPhoneNormalized, identifier)
                .or().eq(AppUser::getEmailNormalized, identifier)));
        if (users.size() != 1 || !passwordPolicy.matches(command.password(), users.getFirst().getPasswordHash())) {
            throw new ServiceException("账号或密码错误");
        }
        AppUser user = users.getFirst();
        if (user.getStatus() != AppIdentityStatus.ACTIVE) {
            throw new ServiceException("创作端账号不可用");
        }
        AppActorContext actor = AppActorContext.appUser(user.getUserId());
        appendAudit(APP_USER_RESOURCE, user.getUserId(), "password_authenticated", actor,
            "credential_revision:" + user.getCredentialRevision(),
            "credential_revision:" + user.getCredentialRevision(), AppSecurityAuditReason.PASSWORD_AUTHENTICATION.code());
        return new AppAuthenticatedIdentityDTO(user.getUserId(), user.getUsername(),
            Boolean.TRUE.equals(user.getMustChangePassword()), user.getCredentialRevision(),
            user.getIdentityRevision(), user.getPermissionRevision());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppAuthenticatedIdentityDTO authenticateVerifiedContact(AppLoginVerificationGrant grant,
                                                                AppAuthClientSnapshotDTO client) {
        if (grant == null || client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new ServiceException("认证客户端参数无效");
        }
        AppUser user = requireUser(grant.userId());
        if (user.getStatus() != AppIdentityStatus.ACTIVE
            || !Objects.equals(user.getCredentialRevision(), grant.credentialRevision())
            || !Objects.equals(user.getIdentityRevision(), grant.identityRevision())
            || isBlank(verifiedContact(user, grant.channel()))) {
            throw new ServiceException("创作端账号不可用");
        }
        AppActorContext actor = AppActorContext.appUser(user.getUserId());
        appendAudit(APP_USER_RESOURCE, user.getUserId(), "verification_code_authenticated", actor,
            "credential_revision:" + user.getCredentialRevision(),
            "credential_revision:" + user.getCredentialRevision(),
            AppSecurityAuditReason.VERIFICATION_CODE_AUTHENTICATION.code());
        return new AppAuthenticatedIdentityDTO(user.getUserId(), user.getUsername(),
            Boolean.TRUE.equals(user.getMustChangePassword()), user.getCredentialRevision(),
            user.getIdentityRevision(), user.getPermissionRevision());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppAuthenticatedIdentityDTO authenticateExternalIdentity(AppExternalIdentityDTO externalIdentity,
                                                                 AppAuthClientSnapshotDTO client) {
        if (externalIdentity == null || client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new ServiceException("认证客户端参数无效");
        }
        String provider = normalizeRequired(externalIdentity.provider(), "第三方提供方");
        String providerSubject = trimRequired(externalIdentity.providerSubject(), "第三方主体标识");
        AppSocialIdentity socialIdentity = socialIdentityMapper.selectOne(new LambdaQueryWrapper<AppSocialIdentity>()
            .eq(AppSocialIdentity::getProvider, provider)
            .eq(AppSocialIdentity::getProviderSubject, providerSubject)
            .eq(AppSocialIdentity::getStatus, AppIdentityStatus.ACTIVE));
        if (socialIdentity == null) {
            throw externalIdentityLoginUnavailable();
        }
        AppUser user = requireUser(socialIdentity.getUserId());
        if (user.getStatus() != AppIdentityStatus.ACTIVE) {
            throw externalIdentityLoginUnavailable();
        }
        AppActorContext actor = AppActorContext.appUser(user.getUserId());
        appendAudit(SOCIAL_IDENTITY_RESOURCE, socialIdentity.getSocialIdentityId(), "external_identity_authenticated", actor,
            "identity_revision:" + user.getIdentityRevision(),
            "identity_revision:" + user.getIdentityRevision(),
            AppSecurityAuditReason.EXTERNAL_IDENTITY_AUTHENTICATION.code());
        return new AppAuthenticatedIdentityDTO(user.getUserId(), user.getUsername(),
            Boolean.TRUE.equals(user.getMustChangePassword()), user.getCredentialRevision(),
            user.getIdentityRevision(), user.getPermissionRevision());
    }

    /**
     * 获取已启用的创作端身份快照。
     *
     * @param userId 用户编号
     * @return 身份快照
     */
    @Override
    public AppIdentitySnapshotDTO requireActive(long userId) {
        AppUser user = requireUser(userId);
        if (user.getStatus() != AppIdentityStatus.ACTIVE) {
            throw new ServiceException("创作端账号不可用");
        }
        return new AppIdentitySnapshotDTO(user.getUserId(), user.getUsername(), user.getStatus(),
            Boolean.TRUE.equals(user.getMustChangePassword()),
            user.getCredentialRevision(), user.getIdentityRevision(), user.getPermissionRevision());
    }

    /**
     * 在预期凭据修订号匹配时修改密码。
     *
     * @param command 改密命令
     * @param actor 操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangeAppPasswordDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null) {
            throw new ServiceException("修改密码参数不能为空");
        }
        requireTargetOperationActor(command.userId(), actor, AppIdentityOperation.CHANGE_PASSWORD);
        AppUser current = requireUser(command.userId());
        if (!passwordPolicy.matches(command.currentPassword(), current.getPasswordHash())) {
            throw new ServiceException("当前密码错误");
        }
        if (passwordPolicy.matches(command.newPassword(), current.getPasswordHash())) {
            throw new ServiceException("新密码不能与当前密码相同");
        }
        String passwordHash = passwordPolicy.hash(command.newPassword());
        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, command.userId())
            .eq(AppUser::getCredentialRevision, command.expectedCredentialRevision())
            .set(AppUser::getPasswordHash, passwordHash)
            .set(AppUser::getMustChangePassword, false)
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("credential_revision = credential_revision + 1"));
        assertExactlyOne(affectedRows, "凭据修订冲突");
        appendAudit(APP_USER_RESOURCE, command.userId(), "password_changed", actor,
            "credential_revision:" + command.expectedCredentialRevision(),
            "credential_revision:" + (command.expectedCredentialRevision() + 1), AppSecurityAuditReason.PASSWORD_CHANGE.code());
        publishSessionInvalidation(command.userId(), AppSessionInvalidationReason.CREDENTIAL_CHANGED);
    }

    /**
     * 在预期凭据修订号匹配时重置密码。
     *
     * @param command 重置命令
     * @param actor 操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetAppPasswordDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null) {
            throw new ServiceException("重置密码参数不能为空");
        }
        requirePlatformOperation(actor, AppIdentityOperation.RESET_PASSWORD, command.userId());
        requireUser(command.userId());
        String passwordHash = passwordPolicy.hash(command.newPassword());
        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, command.userId())
            .eq(AppUser::getCredentialRevision, command.expectedCredentialRevision())
            .set(AppUser::getPasswordHash, passwordHash)
            .set(AppUser::getMustChangePassword, true)
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("credential_revision = credential_revision + 1"));
        assertExactlyOne(affectedRows, "凭据修订冲突");
        appendAudit(APP_USER_RESOURCE, command.userId(), "password_reset", actor,
            "credential_revision:" + command.expectedCredentialRevision(),
            "credential_revision:" + (command.expectedCredentialRevision() + 1), AppSecurityAuditReason.PASSWORD_RESET.code());
        publishSessionInvalidation(command.userId(), AppSessionInvalidationReason.CREDENTIAL_CHANGED);
    }

    /**
     * 以受信任适配器已原子消费的验证码恢复创作端密码。
     *
     * <p>此方法不复用运营端重置密码授权：目标用户、联系方式和修订号均由一次性恢复凭证
     * 推导，并在更新时再次以联系方式与双修订号比较，防止旧挑战覆盖改密、换绑或停用后的状态。</p>
     *
     * @param command 不含用户编号、联系方式和修订号的找回命令
     * @param client 已通过入口策略验证的创作端客户端
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverPassword(RecoverAppPasswordDTO command, AppAuthClientSnapshotDTO client) {
        if (command == null || client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new ServiceException("找回密码参数无效");
        }
        // 先拒绝格式不合规的新密码，避免无效请求消耗一次性验证码挑战。
        passwordPolicy.validate(command.newPassword());
        AppPasswordRecoveryReservation reservation = passwordRecoveryVerifier.reserve(
            new AppPasswordRecoveryVerificationRequest(command.challengeId(), command.verificationCode(),
                client.clientId(), client.clientRevision()));
        boolean completionRegistered = registerRecoveryReservationCompletion(reservation);
        try {
            AppPasswordRecoveryGrant grant = reservation.grant();
            AppUser current = requireUser(grant.userId());
            String verifiedTarget = requireValidPasswordRecoveryGrant(current, grant);
            if (passwordPolicy.matches(command.newPassword(), current.getPasswordHash())) {
                throw new ServiceException("新密码不能与当前密码相同");
            }
            AppActorContext actor = AppActorContext.appUser(grant.userId());
            LambdaUpdateWrapper<AppUser> update = new LambdaUpdateWrapper<AppUser>()
                .eq(AppUser::getUserId, grant.userId())
                .eq(AppUser::getDelFlag, "0")
                .eq(AppUser::getStatus, AppIdentityStatus.ACTIVE)
                .eq(AppUser::getCredentialRevision, grant.credentialRevision())
                .eq(AppUser::getIdentityRevision, grant.identityRevision())
                .set(AppUser::getPasswordHash, passwordPolicy.hash(command.newPassword()))
                .set(AppUser::getMustChangePassword, false)
                .set(AppUser::getUpdatedByType, actor.actorType())
                .set(AppUser::getUpdatedById, actor.actorId())
                .setSql("credential_revision = credential_revision + 1");
            switch (grant.channel()) {
                case PHONE -> update.eq(AppUser::getPhoneNormalized, verifiedTarget);
                case EMAIL -> update.eq(AppUser::getEmailNormalized, verifiedTarget);
            }
            int affectedRows = userMapper.update(null, update);
            assertExactlyOne(affectedRows, "找回密码凭据已过期或冲突");
            appendAudit(APP_USER_RESOURCE, grant.userId(), "password_recovered", actor,
                "credential_revision:" + grant.credentialRevision(),
                "credential_revision:" + (grant.credentialRevision() + 1),
                AppSecurityAuditReason.PASSWORD_RECOVERY.code());
            publishSessionInvalidation(grant.userId(), AppSessionInvalidationReason.CREDENTIAL_CHANGED);
            if (!completionRegistered) {
                passwordRecoveryVerifier.commit(reservation);
            }
        } catch (RuntimeException | Error exception) {
            if (!completionRegistered) {
                passwordRecoveryVerifier.release(reservation);
            }
            throw exception;
        }
    }

    /**
     * 将验证码预留与密码事务绑定：提交后失效，回滚后释放。无事务的直接单元调用仍会在方法末尾完成预留。
     */
    private boolean registerRecoveryReservationCompletion(AppPasswordRecoveryReservation reservation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return false;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                passwordRecoveryVerifier.commit(reservation);
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    passwordRecoveryVerifier.release(reservation);
                }
            }
        });
        return true;
    }

    /**
     * 在预期身份修订号匹配时变更用户状态。
     *
     * @param command 状态变更命令
     * @param actor 操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(ChangeAppUserStatusDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null || command.status() == null) {
            throw new ServiceException("状态变更参数不能为空");
        }
        requirePlatformOperation(actor, AppIdentityOperation.CHANGE_STATUS, command.userId());
        AppUser current = requireUser(command.userId());
        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, command.userId())
            .eq(AppUser::getIdentityRevision, command.expectedIdentityRevision())
            .set(AppUser::getStatus, command.status())
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("identity_revision = identity_revision + 1"));
        assertExactlyOne(affectedRows, "身份修订冲突");
        appendAudit(APP_USER_RESOURCE, command.userId(), "status_changed", actor,
            "status:" + enumValue(current.getStatus()), "status:" + enumValue(command.status()), AppSecurityAuditReason.ACCOUNT_STATUS_CHANGE.code());
        publishSessionInvalidation(command.userId(), statusInvalidationReason(command.status()));
    }

    /**
     * 修改运营端维护的创作端用户展示资料与联系方式。
     *
     * <p>联系方式属于身份快照的一部分；任何资料变更都会递增身份修订并在事务提交后撤销 app 会话，
     * 避免旧工作区快照继续使用已修改的身份事实。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UpdateAppUserProfileDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null) {
            throw new ServiceException("创作端用户资料参数不能为空");
        }
        requirePlatformOperation(actor, AppIdentityOperation.UPDATE_APP_USER, command.userId());
        if (command.expectedIdentityRevision() <= 0) {
            throw new ServiceException("身份修订号无效");
        }
        AppUser current = requireUser(command.userId());
        String displayName = trimRequired(command.displayName(), "显示名称");
        String phoneNormalized = resolveContactUpdate(command.phone(), command.clearPhone(),
            current.getPhoneNormalized(), "手机号");
        String emailNormalized = resolveContactUpdate(command.email(), command.clearEmail(),
            current.getEmailNormalized(), "邮箱");
        assertIdentityAvailableForUpdate(current.getUserId(), current.getUsernameNormalized(), phoneNormalized,
            emailNormalized);

        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, command.userId())
            .eq(AppUser::getDelFlag, "0")
            .eq(AppUser::getIdentityRevision, command.expectedIdentityRevision())
            .set(AppUser::getDisplayName, displayName)
            .set(AppUser::getPhoneNormalized, phoneNormalized)
            .set(AppUser::getEmailNormalized, emailNormalized)
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("identity_revision = identity_revision + 1"));
        assertExactlyOne(affectedRows, "身份修订冲突");
        appendAudit(APP_USER_RESOURCE, command.userId(), "profile_updated", actor,
            "identity_revision:" + command.expectedIdentityRevision(),
            "identity_revision:" + (command.expectedIdentityRevision() + 1),
            AppSecurityAuditReason.USER_PROFILE_CHANGE.code());
        publishSessionInvalidation(command.userId(), AppSessionInvalidationReason.IDENTITY_CHANGED);
    }

    /**
     * 绑定第三方身份并递增身份修订号。
     *
     * @param command 绑定命令
     * @param actor 操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindSocialIdentity(BindSocialIdentityDTO command, AppActorContext actor) {
        requireActor(actor);
        if (command == null) {
            throw new ServiceException("第三方身份绑定参数不能为空");
        }
        requireTargetOperationActor(command.userId(), actor, AppIdentityOperation.BIND_SOCIAL_IDENTITY);
        requireActive(command.userId());
        String provider = normalizeRequired(command.provider(), "第三方提供方");
        String providerSubject = trimRequired(command.providerSubject(), "第三方主体标识");
        AppSocialIdentity socialIdentity = new AppSocialIdentity();
        socialIdentity.setUserId(command.userId());
        socialIdentity.setProvider(provider);
        socialIdentity.setProviderSubject(providerSubject);
        socialIdentity.setStatus(AppIdentityStatus.ACTIVE);
        applyCreateActor(socialIdentity, actor);
        try {
            AppSocialIdentity existing = socialIdentityMapper.selectOne(new LambdaQueryWrapper<AppSocialIdentity>()
                .eq(AppSocialIdentity::getProvider, provider)
                .eq(AppSocialIdentity::getProviderSubject, providerSubject));
            if (existing != null) {
                throw new ServiceException("第三方身份已绑定");
            }
            if (socialIdentityMapper.insert(socialIdentity) != 1 || socialIdentity.getSocialIdentityId() == null) {
                throw new ServiceException("第三方身份绑定失败");
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ServiceException("第三方身份已绑定", exception);
        } catch (ConcurrencyFailureException exception) {
            throw new ServiceException("第三方身份绑定冲突，请重试", exception);
        }
        incrementIdentityRevision(command.userId(), command.expectedIdentityRevision(), actor);
        appendAudit(SOCIAL_IDENTITY_RESOURCE, socialIdentity.getSocialIdentityId(), "social_identity_bound", actor,
            "identity_revision:" + command.expectedIdentityRevision(),
            "identity_revision:" + (command.expectedIdentityRevision() + 1), AppSecurityAuditReason.SOCIAL_IDENTITY_BIND.code());
        publishSessionInvalidation(command.userId(), AppSessionInvalidationReason.IDENTITY_CHANGED);
    }

    /**
     * 解绑第三方身份并递增当前身份修订号。
     *
     * @param userId 用户编号
     * @param socialIdentityId 第三方身份编号
     * @param actor 操作者
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindSocialIdentity(long userId, long socialIdentityId, AppActorContext actor) {
        requireActor(actor);
        requireTargetOperationActor(userId, actor, AppIdentityOperation.UNBIND_SOCIAL_IDENTITY);
        AppUser user = requireUser(userId);
        AppSocialIdentity socialIdentity = socialIdentityMapper.selectOne(new LambdaQueryWrapper<AppSocialIdentity>()
            .eq(AppSocialIdentity::getSocialIdentityId, socialIdentityId)
            .eq(AppSocialIdentity::getUserId, userId)
            .eq(AppSocialIdentity::getStatus, AppIdentityStatus.ACTIVE));
        if (socialIdentity == null) {
            throw socialIdentityUnbindUnavailable();
        }
        if (!hasUsableLoginMethodAfterUnbinding(user, socialIdentityId)) {
            throw socialIdentityUnbindUnavailable();
        }
        int affectedRows = socialIdentityMapper.update(null, new LambdaUpdateWrapper<AppSocialIdentity>()
            .eq(AppSocialIdentity::getSocialIdentityId, socialIdentityId)
            .eq(AppSocialIdentity::getUserId, userId)
            .eq(AppSocialIdentity::getStatus, AppIdentityStatus.ACTIVE)
            .set(AppSocialIdentity::getStatus, AppIdentityStatus.INACTIVE)
            .set(AppSocialIdentity::getUpdatedByType, actor.actorType())
            .set(AppSocialIdentity::getUpdatedById, actor.actorId()));
        assertExactlyOne(affectedRows, "第三方身份解绑冲突");
        incrementIdentityRevision(userId, user.getIdentityRevision(), actor);
        appendAudit(SOCIAL_IDENTITY_RESOURCE, socialIdentityId, "social_identity_unbound", actor,
            "identity_revision:" + user.getIdentityRevision(),
            "identity_revision:" + (user.getIdentityRevision() + 1), AppSecurityAuditReason.SOCIAL_IDENTITY_UNBIND.code());
        publishSessionInvalidation(userId, AppSessionInvalidationReason.IDENTITY_CHANGED);
    }

    private void assertIdentityAvailable(String username, String phone, String email) {
        Set<String> identifiers = new LinkedHashSet<>();
        identifiers.add(username);
        if (phone != null) {
            identifiers.add(phone);
        }
        if (email != null) {
            identifiers.add(email);
        }
        if (!userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(identifiers).isEmpty()) {
            throw new ServiceException("创作端身份已存在");
        }
    }

    private void assertIdentityAvailableForUpdate(long userId, String username, String phone, String email) {
        Set<String> identifiers = new LinkedHashSet<>();
        identifiers.add(username);
        if (phone != null) {
            identifiers.add(phone);
        }
        if (email != null) {
            identifiers.add(email);
        }
        boolean occupiedByAnotherUser = userMapper.selectUserIdsByAnyIdentifierIncludingDeleted(identifiers)
            .stream()
            .anyMatch(existingUserId -> existingUserId != null && existingUserId != userId);
        if (occupiedByAnotherUser) {
            throw new ServiceException("创作端身份已存在");
        }
    }

    private AppUser requireUser(long userId) {
        if (userId <= 0) {
            throw new ServiceException("创作端用户编号无效");
        }
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getUserId, userId)
            .eq(AppUser::getDelFlag, "0"));
        if (user == null) {
            throw new ServiceException("创作端用户不存在");
        }
        return user;
    }

    /**
     * 验证一次性找回凭证仍与当前创作端身份状态一致。
     *
     * @param current 当前数据库身份
     * @param grant 已原子消费的恢复凭证
     * @return 已标准化且仍匹配的联系方式
     */
    private String requireValidPasswordRecoveryGrant(AppUser current, AppPasswordRecoveryGrant grant) {
        if (current.getStatus() != AppIdentityStatus.ACTIVE
            || !Objects.equals(current.getCredentialRevision(), grant.credentialRevision())
            || !Objects.equals(current.getIdentityRevision(), grant.identityRevision())) {
            throw new ServiceException("找回密码凭据已过期或冲突");
        }
        return switch (grant.channel()) {
            case PHONE -> requireRecoveryContact(current.getPhoneNormalized());
            case EMAIL -> requireRecoveryContact(current.getEmailNormalized());
        };
    }

    /**
     * 找回凭证仅保留渠道和身份修订；实际联系方式始终从当前 app_user 记录读取。
     */
    private String requireRecoveryContact(String contact) {
        if (isBlank(contact)) {
            throw new ServiceException("找回密码凭据已过期或冲突");
        }
        return contact;
    }

    private String verifiedContact(AppUser user, AppVerificationChannel channel) {
        return switch (channel) {
            case PHONE -> user.getPhoneNormalized();
            case EMAIL -> user.getEmailNormalized();
        };
    }

    /**
     * 确保解绑后仍至少保留一个可登录的本地或第三方身份。
     *
     * <p>当前身份模型没有单独的联系方式已验证标记；短信和邮件登录会在登录时再次验证验证码，
     * 因此非空的规范化联系方式表示仍存在可用的验证码登录路径。</p>
     */
    private boolean hasUsableLoginMethodAfterUnbinding(AppUser user, long socialIdentityId) {
        if (!isBlank(user.getPasswordHash()) || !isBlank(user.getPhoneNormalized())
            || !isBlank(user.getEmailNormalized())) {
            return true;
        }
        Long activeAlternativeCount = socialIdentityMapper.selectCount(new LambdaQueryWrapper<AppSocialIdentity>()
            .eq(AppSocialIdentity::getUserId, user.getUserId())
            .ne(AppSocialIdentity::getSocialIdentityId, socialIdentityId)
            .eq(AppSocialIdentity::getStatus, AppIdentityStatus.ACTIVE));
        return activeAlternativeCount != null && activeAlternativeCount > 0;
    }

    private ServiceException externalIdentityLoginUnavailable() {
        return new ServiceException("第三方登录不可用");
    }

    private ServiceException socialIdentityUnbindUnavailable() {
        return new ServiceException("第三方身份解绑不可用");
    }

    private void incrementIdentityRevision(long userId, long expectedRevision, AppActorContext actor) {
        int affectedRows = userMapper.update(null, new LambdaUpdateWrapper<AppUser>()
            .eq(AppUser::getUserId, userId)
            .eq(AppUser::getIdentityRevision, expectedRevision)
            .set(AppUser::getUpdatedByType, actor.actorType())
            .set(AppUser::getUpdatedById, actor.actorId())
            .setSql("identity_revision = identity_revision + 1"));
        assertExactlyOne(affectedRows, "身份修订冲突");
    }

    private void appendAudit(String resourceType, long resourceId, String action, AppActorContext actor,
                             String beforeDigest, String afterDigest, String reason) {
        securityAuditService.append(new AppSecurityAuditDTO(
            resourceType,
            Long.toString(resourceId),
            action,
            actor.actorType(),
            actor.actorId(),
            beforeDigest,
            afterDigest,
            reason));
    }

    private void publishSessionInvalidation(long userId, AppSessionInvalidationReason reason) {
        eventPublisher.publishEvent(AppSessionInvalidationEvent.forUsers(Set.of(userId), reason));
    }

    private AppSessionInvalidationReason statusInvalidationReason(AppIdentityStatus status) {
        return status == AppIdentityStatus.DISABLED
            ? AppSessionInvalidationReason.USER_DISABLED
            : AppSessionInvalidationReason.IDENTITY_CHANGED;
    }

    private void applyCreateActor(AppUser user, AppActorContext actor) {
        user.setCreatedByType(actor.actorType());
        user.setCreatedById(actor.actorId());
        user.setUpdatedByType(actor.actorType());
        user.setUpdatedById(actor.actorId());
    }

    private void applyCreateActor(AppUserRole userRole, AppActorContext actor) {
        userRole.setCreatedByType(actor.actorType());
        userRole.setCreatedById(actor.actorId());
        userRole.setUpdatedByType(actor.actorType());
        userRole.setUpdatedById(actor.actorId());
    }

    private void applyCreateActor(AppSocialIdentity socialIdentity, AppActorContext actor) {
        socialIdentity.setCreatedByType(actor.actorType());
        socialIdentity.setCreatedById(actor.actorId());
        socialIdentity.setUpdatedByType(actor.actorType());
        socialIdentity.setUpdatedById(actor.actorId());
    }

    private void requireActor(AppActorContext actor) {
        if (actor == null || actor.actorType() == null || actor.actorId() <= 0) {
            throw new ServiceException("操作者参数无效");
        }
    }

    private void requireTargetOperationActor(long targetUserId, AppActorContext actor, AppIdentityOperation operation) {
        if (actor.actorType() == AppActorType.APP_USER) {
            if (actor.actorId() != targetUserId) {
                throw new ServiceException("无权操作其他创作端用户");
            }
            return;
        }
        requirePlatformOperation(actor, operation, targetUserId);
    }

    private void requirePlatformOperation(AppActorContext actor, AppIdentityOperation operation, long targetUserId) {
        if (actor.actorType() != AppActorType.SYS_USER
            || !operationAuthorizer.isAuthorized(actor, operation, targetUserId)) {
            throw new ServiceException("运营端身份操作未获授权");
        }
    }

    private void assertExactlyOne(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new ServiceException(message);
        }
    }

    private String defaultDisplayName(String displayName, String username) {
        return isBlank(displayName) ? username : displayName.trim();
    }

    private String normalizeRequired(String value, String fieldName) {
        return trimRequired(value, fieldName).toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return isBlank(value) ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 解析运营端资料更新中的联系方式补丁，避免脱敏详情回填时意外删除真实联系方式。
     *
     * @param submittedValue 本次请求提交的新值；{@code null} 表示保持原值
     * @param clearRequested 是否明确请求清空
     * @param currentValue 已保存的规范化值
     * @param fieldName 中文字段名
     * @return 更新后应保存的规范化值
     */
    private String resolveContactUpdate(String submittedValue, boolean clearRequested, String currentValue,
                                        String fieldName) {
        if (clearRequested) {
            if (submittedValue != null) {
                throw new ServiceException(fieldName + "不能与清空标志同时提交");
            }
            return null;
        }
        if (submittedValue == null) {
            return currentValue;
        }
        if (isBlank(submittedValue)) {
            throw new ServiceException(fieldName + "不能为空；如需清空请使用明确的清空标志");
        }
        if (submittedValue.indexOf('*') >= 0) {
            throw new ServiceException(fieldName + "不能提交脱敏展示值");
        }
        return submittedValue.trim().toLowerCase(Locale.ROOT);
    }

    private String trimRequired(String value, String fieldName) {
        if (isBlank(value)) {
            throw new ServiceException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String enumValue(AppIdentityStatus status) {
        return status.getValue();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
