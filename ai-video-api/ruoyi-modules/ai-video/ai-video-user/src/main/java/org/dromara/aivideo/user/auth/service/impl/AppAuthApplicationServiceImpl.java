package org.dromara.aivideo.user.auth.service.impl;

import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.service.IAppVerificationCodeService;
import org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO;
import org.dromara.aivideo.identity.dto.BindSocialIdentityDTO;
import org.dromara.aivideo.identity.dto.ChangeAppPasswordDTO;
import org.dromara.aivideo.identity.dto.RecoverAppPasswordDTO;
import org.dromara.aivideo.identity.dto.AppVerificationCodeRequestDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppVerificationChallengeDTO;
import org.dromara.aivideo.identity.domain.AppAuthMethod;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppLoginLog;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppLoginLogMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppExternalIdentityChannel;
import org.dromara.aivideo.identity.dto.AppExternalIdentityRequestDTO;
import org.dromara.aivideo.identity.service.IAppExternalIdentityService;
import org.dromara.aivideo.identity.dto.AppExternalIdentityDTO;
import org.dromara.aivideo.identity.dto.AppMiniProgramAuthorizationDTO;
import org.dromara.aivideo.identity.dto.AppSocialIdentityAuthorizationDTO;
import org.dromara.aivideo.identity.security.AppAuthenticationSessionIssuer;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.identity.security.AppIssuedAccessToken;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.IAppLoginVerificationService;
import org.dromara.aivideo.identity.security.AppLoginVerificationReservation;
import org.dromara.aivideo.identity.security.AppLoginVerificationRequest;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppCodeLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppMiniProgramLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordChangeBo;
import org.dromara.aivideo.user.auth.domain.bo.AppPasswordResetBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialBindingBo;
import org.dromara.aivideo.user.auth.domain.bo.AppSocialLoginBo;
import org.dromara.aivideo.user.auth.domain.bo.AppVerificationCodeBo;
import org.dromara.aivideo.user.auth.domain.vo.AppLoginVo;
import org.dromara.aivideo.user.auth.domain.vo.AppMeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppSessionVo;
import org.dromara.aivideo.user.auth.domain.vo.AppVerificationChallengeVo;
import org.dromara.aivideo.user.auth.domain.vo.AppWorkspaceVo;
import org.dromara.aivideo.user.auth.service.IAppAuthApplicationService;
import org.dromara.aivideo.user.security.AppAuthErrorCodes;
import org.dromara.aivideo.user.security.AppSecurityException;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 只面向创作端 {@code app} 身份域的认证应用服务。
 */
@Service
public class AppAuthApplicationServiceImpl implements IAppAuthApplicationService {

    private static final String WEB_DEVICE = "web";
    private static final String LOGIN_FAILURE_CATEGORY = "credentials_invalid";
    private static final String APP_SESSION_RESOURCE = "app_session";
    private static final String SESSION_REVOCATION_IGNORED_ACTION = "session_revocation_ignored";
    private static final Pattern SAFE_SESSION_ID = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final IAppIdentityService identityService;
    private final IAppSessionService sessionService;
    private final IAppVerificationCodeService verificationCodeService;
    private final IAppLoginVerificationService loginVerificationPort;
    private final IAppSecurityAuditService securityAuditService;
    private final AppUserMapper userMapper;
    private final AppPersonalWorkspaceSnapshotProvider workspaceProvider;
    private final AppAuthenticationSessionIssuer sessionIssuer;
    private final AppLoginHelper loginHelper;
    private final AppLoginLogMapper loginLogMapper;
    private final Map<AppExternalIdentityChannel, IAppExternalIdentityService> externalIdentityPorts;

    public AppAuthApplicationServiceImpl(IAppIdentityService identityService,
                                          IAppSessionService sessionService,
                                          IAppVerificationCodeService verificationCodeService,
                                          IAppLoginVerificationService loginVerificationPort,
                                          IAppSecurityAuditService securityAuditService,
                                          AppUserMapper userMapper,
                                          AppPersonalWorkspaceSnapshotProvider workspaceProvider,
                                          AppAuthenticationSessionIssuer sessionIssuer,
                                          AppLoginHelper loginHelper,
                                          AppLoginLogMapper loginLogMapper,
                                          List<IAppExternalIdentityService> externalIdentityPorts) {
        this.identityService = Objects.requireNonNull(identityService, "创作端身份服务不能为空");
        this.sessionService = Objects.requireNonNull(sessionService, "创作端会话服务不能为空");
        this.verificationCodeService = Objects.requireNonNull(verificationCodeService, "创作端验证码服务不能为空");
        this.loginVerificationPort = Objects.requireNonNull(loginVerificationPort, "创作端登录验证码服务不能为空");
        this.securityAuditService = Objects.requireNonNull(securityAuditService, "创作端安全审计服务不能为空");
        this.userMapper = Objects.requireNonNull(userMapper, "创作端用户数据访问接口不能为空");
        this.workspaceProvider = Objects.requireNonNull(workspaceProvider, "个人工作区快照提供器不能为空");
        this.sessionIssuer = Objects.requireNonNull(sessionIssuer, "创作端会话签发器不能为空");
        this.loginHelper = Objects.requireNonNull(loginHelper, "创作端登录助手不能为空");
        this.loginLogMapper = Objects.requireNonNull(loginLogMapper, "创作端登录日志数据访问接口不能为空");
        this.externalIdentityPorts = indexExternalIdentityPorts(externalIdentityPorts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppLoginVo passwordLogin(AppPasswordLoginBo body, AppAuthClientSnapshotDTO client) {
        requireLoginRequest(body, client);
        AppAuthenticatedIdentityDTO identity;
        try {
            identity = identityService.authenticatePassword(
                new AuthenticatePasswordDTO(body.identifier(), body.password(), client.clientId()), client);
        } catch (ServiceException exception) {
            appendFailureLog(body.identifier(), client);
            throw credentialsInvalid();
        }

        return issueAuthenticatedLogin(identity, client, AppAuthMethod.PASSWORD, body.identifier(), null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppLoginVo smsLogin(AppCodeLoginBo body, AppAuthClientSnapshotDTO client) {
        return codeLogin(body, client, AppVerificationChannel.PHONE, AppAuthMethod.SMS);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppLoginVo emailLogin(AppCodeLoginBo body, AppAuthClientSnapshotDTO client) {
        return codeLogin(body, client, AppVerificationChannel.EMAIL, AppAuthMethod.EMAIL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppLoginVo socialLogin(AppSocialLoginBo body, AppAuthClientSnapshotDTO client) {
        if (body == null) {
            throw credentialsInvalid();
        }
        return externalIdentityLogin(new AppSocialIdentityAuthorizationDTO(
            body.provider(), body.authorizationCode(), body.state()), AppExternalIdentityChannel.SOCIAL,
            AppAuthMethod.SOCIAL, client);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppLoginVo miniProgramLogin(AppMiniProgramLoginBo body, AppAuthClientSnapshotDTO client) {
        if (body == null) {
            throw credentialsInvalid();
        }
        return externalIdentityLogin(new AppMiniProgramAuthorizationDTO(body.authorizationCode()),
            AppExternalIdentityChannel.MINI_PROGRAM, AppAuthMethod.MINI_PROGRAM, client);
    }

    private AppLoginVo externalIdentityLogin(AppExternalIdentityRequestDTO command,
                                              AppExternalIdentityChannel channel,
                                              AppAuthMethod authMethod,
                                              AppAuthClientSnapshotDTO client) {
        requireVerifiedClient(client);
        AppExternalIdentityDTO externalIdentity;
        AppAuthenticatedIdentityDTO identity;
        try {
            externalIdentity = externalIdentityPort(channel).exchange(command);
            identity = identityService.authenticateExternalIdentity(externalIdentity, client);
        } catch (RuntimeException exception) {
            appendFailureLog(null, client, authMethod);
            throw credentialsInvalid();
        }
        return issueAuthenticatedLogin(identity, client, authMethod, null, null);
    }

    private AppLoginVo issueAuthenticatedLogin(AppAuthenticatedIdentityDTO identity, AppAuthClientSnapshotDTO client,
                                               AppAuthMethod authMethod, String identifier,
                                               AppVerificationChannel verifiedChannel) {
        AppUser user = requireActiveUser(identity.userId());
        String loginIdentifier = identifier != null
            ? identifier
            : verifiedChannel == null ? null : verifiedContactIdentifier(user, verifiedChannel);
        AppWorkspaceSessionSnapshotDTO workspace = workspaceProvider.personalWorkspace(user);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(
            identity.userId(), identity.username(), client.clientId(), identity.credentialRevision(),
            identity.identityRevision(), identity.permissionRevision(), client.clientRevision(), workspace);
        AppIssuedAccessToken issued = sessionIssuer.issue(principal, WEB_DEVICE);
        try {
            appendSuccessfulLogin(identity.userId(), issued.loginUser().sessionId(), loginIdentifier, client, authMethod);
        } catch (RuntimeException exception) {
            revokeCurrentAppSession(exception);
            throw exception;
        }
        return new AppLoginVo(issued.accessToken(), client.clientId(), issued.expireIn(), toWorkspace(workspace));
    }

    private AppLoginVo codeLogin(AppCodeLoginBo body, AppAuthClientSnapshotDTO client,
                                 AppVerificationChannel expectedChannel, AppAuthMethod authMethod) {
        requireVerifiedClient(client);
        if (body == null) {
            throw credentialsInvalid();
        }
        AppLoginVerificationReservation reservation;
        try {
            reservation = loginVerificationPort.reserve(
                new AppLoginVerificationRequest(body.challengeId(), body.verificationCode(), client.clientId(),
                    client.clientRevision()));
        } catch (RuntimeException exception) {
            appendFailureLog(null, client, authMethod);
            throw credentialsInvalid();
        }
        if (reservation == null) {
            appendFailureLog(null, client, authMethod);
            throw credentialsInvalid();
        }
        boolean loginEstablished = false;
        try {
            if (reservation.grant().channel() != expectedChannel) {
                throw credentialsInvalid();
            }
            AppAuthenticatedIdentityDTO identity = identityService.authenticateVerifiedContact(reservation.grant(), client);
            AppLoginVo result = issueAuthenticatedLogin(identity, client, authMethod, null, expectedChannel);
            loginEstablished = true;
            loginVerificationPort.commit(reservation);
            return result;
        } catch (RuntimeException exception) {
            if (!loginEstablished) {
                releaseLoginReservation(reservation, exception);
                appendFailureLog(null, client, authMethod);
            }
            if (exception instanceof AppSecurityException securityException
                && securityException.getCode() == AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID) {
                throw securityException;
            }
            throw credentialsInvalid();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppVerificationChallengeVo requestVerificationCode(AppVerificationCodeBo body,
                                                               AppAuthClientSnapshotDTO client) {
        requireVerifiedClient(client);
        if (body == null) {
            throw credentialsInvalid();
        }
        try {
            AppVerificationChallengeDTO challenge = verificationCodeService.issue(
                new AppVerificationCodeRequestDTO(body.scenario(), body.channel(), body.target()), client);
            if (challenge == null) {
                throw new IllegalStateException("创作端验证码服务未返回挑战摘要");
            }
            return new AppVerificationChallengeVo(
                challenge.challengeId(), challenge.maskedTarget(), challenge.expiresInSeconds());
        } catch (ServiceException exception) {
            throw credentialsInvalid();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void recoverPassword(AppPasswordResetBo body, AppAuthClientSnapshotDTO client) {
        requireVerifiedClient(client);
        if (body == null) {
            throw credentialsInvalid();
        }
        try {
            identityService.recoverPassword(new RecoverAppPasswordDTO(
                body.challengeId(), body.verificationCode(), body.newPassword()), client);
        } catch (ServiceException exception) {
            throw credentialsInvalid();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void changePassword(AppPasswordChangeBo body) {
        AppLoginUser loginUser = loginHelper.getLoginUser();
        long userId = loginUser.userId();
        identityService.changePassword(new ChangeAppPasswordDTO(userId, body.currentPassword(), body.newPassword(),
            loginUser.principal().credentialRevision()), AppActorContext.appUser(userId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bindSocialIdentity(AppSocialBindingBo body) {
        if (body == null) {
            throw credentialsInvalid();
        }
        AppLoginUser loginUser = loginHelper.getLoginUser();
        try {
            AppExternalIdentityDTO externalIdentity = externalIdentityPort(AppExternalIdentityChannel.SOCIAL).exchange(
                new AppSocialIdentityAuthorizationDTO(body.provider(), body.authorizationCode(), body.state()));
            identityService.bindSocialIdentity(new BindSocialIdentityDTO(loginUser.userId(), externalIdentity.provider(),
                externalIdentity.providerSubject(), loginUser.principal().identityRevision()),
                AppActorContext.appUser(loginUser.userId()));
        } catch (RuntimeException exception) {
            throw credentialsInvalid();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbindSocialIdentity(long socialIdentityId) {
        if (socialIdentityId <= 0) {
            throw credentialsInvalid();
        }
        AppLoginUser loginUser = loginHelper.getLoginUser();
        try {
            identityService.unbindSocialIdentity(loginUser.userId(), socialIdentityId,
                AppActorContext.appUser(loginUser.userId()));
        } catch (RuntimeException exception) {
            throw credentialsInvalid();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppSessionVo> listCurrentUserSessions() {
        AppLoginUser loginUser = loginHelper.getLoginUser();
        return sessionService.currentUserSessions(loginUser.userId()).stream()
            .map(this::toSessionVo)
            .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void revokeOwnSession(String sessionId) {
        String safeSessionId = requireSafeSessionId(sessionId);
        AppLoginUser loginUser = loginHelper.getLoginUser();
        long userId = loginUser.userId();
        AppActorContext actor = AppActorContext.appUser(userId);
        if (isSessionOwnedByCurrentUser(userId, safeSessionId)) {
            try {
                sessionService.revokeSession(userId, safeSessionId, actor,
                    AppSecurityAuditReason.SESSION_REVOCATION.code());
                // 会话服务在撤销事务中追加唯一且准确的 session_revoked 审计记录。
                return;
            } catch (ServiceException exception) {
                if (isSessionOwnedByCurrentUser(userId, safeSessionId)) {
                    throw exception;
                }
            }
        }
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_SESSION_RESOURCE, safeSessionId, SESSION_REVOCATION_IGNORED_ACTION, actor.actorType(), actor.actorId(),
            null, null, AppSecurityAuditReason.SESSION_REVOCATION.code()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppMeVo me() {
        AppLoginUser loginUser = loginHelper.getLoginUser();
        AppPrincipalSnapshotDTO principal = loginUser.principal();
        AppUser user = requireActiveUser(loginUser.userId());
        AppWorkspaceSessionSnapshotDTO workspace = principal.workspace();
        if (workspace == null) {
            throw accountUnavailable();
        }
        List<String> roles = workspace.roleCode() == null || workspace.roleCode().isBlank()
            ? List.of() : List.of(workspace.roleCode());
        List<String> permissions = new ArrayList<>(workspace.permissions());
        permissions.sort(String::compareTo);
        return new AppMeVo(Long.toString(loginUser.userId()), user.getUsername(), user.getDisplayName(),
            maskPhone(user.getPhoneNormalized()), maskEmail(user.getEmailNormalized()),
            Boolean.TRUE.equals(user.getMustChangePassword()), roles, List.copyOf(permissions), toWorkspace(workspace));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logoutCurrent() {
        loginHelper.logout();
    }

    /**
     * 使用随机会话编号精确查询并校验归属，避免为了撤销单个会话扫描用户的全部在线会话。
     *
     * @param userId 当前 app 用户编号
     * @param sessionId 待撤销的随机会话编号
     * @return 会话仍存在且归属当前用户时返回 {@code true}
     */
    private boolean isSessionOwnedByCurrentUser(long userId, String sessionId) {
        return sessionService.findBySessionId(sessionId)
            .map(AppSessionSummaryDTO::appUserId)
            .filter(ownerUserId -> ownerUserId != null && ownerUserId == userId)
            .isPresent();
    }

    private AppSessionVo toSessionVo(AppSessionSummaryDTO session) {
        return new AppSessionVo(session.sessionId(), session.clientId(), session.device(), session.lastActiveTime(),
            session.current());
    }

    private String requireSafeSessionId(String sessionId) {
        if (sessionId == null || !SAFE_SESSION_ID.matcher(sessionId).matches()) {
            throw new ServiceException("创作端会话编号格式不安全");
        }
        return sessionId;
    }

    private void requireLoginRequest(AppPasswordLoginBo body, AppAuthClientSnapshotDTO client) {
        if (body == null || client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new AppSecurityException("创作端认证客户端不可用",
                AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE);
        }
    }

    private void requireVerifiedClient(AppAuthClientSnapshotDTO client) {
        if (client == null || isBlank(client.clientId()) || client.clientRevision() <= 0) {
            throw new AppSecurityException("创作端认证客户端不可用",
                AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE);
        }
    }

    private AppUser requireActiveUser(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || !"0".equals(user.getDelFlag()) || user.getStatus() != AppIdentityStatus.ACTIVE) {
            throw accountUnavailable();
        }
        return user;
    }

    private void appendSuccessfulLogin(long userId, String sessionId, String identifier, AppAuthClientSnapshotDTO client,
                                       AppAuthMethod authMethod) {
        AppAuditRequestContext context = AppAuditRequestContextHolder.current();
        AppLoginLog log = baseLoginLog(identifier, client, context, authMethod);
        log.setResultCode(200);
        log.setUserId(userId);
        log.setSessionId(sessionId);
        if (loginLogMapper.insert(log) != 1) {
            throw new ServiceException("创作端登录审计记录失败");
        }
    }

    private void appendFailureLog(String identifier, AppAuthClientSnapshotDTO client) {
        appendFailureLog(identifier, client, AppAuthMethod.PASSWORD);
    }

    private void appendFailureLog(String identifier, AppAuthClientSnapshotDTO client, AppAuthMethod authMethod) {
        try {
            AppAuditRequestContext context = AppAuditRequestContextHolder.current();
            AppLoginLog log = baseLoginLog(identifier, client, context, authMethod);
            log.setResultCode(AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
            log.setFailureCategory(LOGIN_FAILURE_CATEGORY);
            loginLogMapper.insert(log);
        } catch (RuntimeException ignored) {
            // 失败登录绝不因审计写入异常泄露账号或凭据校验细节；此时没有 app 会话可被签发。
        }
    }

    private AppLoginLog baseLoginLog(String identifier, AppAuthClientSnapshotDTO client, AppAuditRequestContext context,
                                     AppAuthMethod authMethod) {
        AppLoginLog log = new AppLoginLog();
        log.setAuthMethod(authMethod);
        log.setMaskedIdentifier(maskIdentifier(identifier));
        log.setClientId(client.clientId());
        log.setIpAddress(context.ipAddress());
        log.setDeviceSummary(WEB_DEVICE);
        log.setRequestId(context.requestId());
        log.setOccurredAt(LocalDateTime.now());
        return log;
    }

    private void revokeCurrentAppSession(Throwable original) {
        try {
            loginHelper.logout();
        } catch (RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }

    private void releaseLoginReservation(AppLoginVerificationReservation reservation, RuntimeException original) {
        try {
            loginVerificationPort.release(reservation);
        } catch (RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);
        }
    }

    private static Map<AppExternalIdentityChannel, IAppExternalIdentityService> indexExternalIdentityPorts(
        List<IAppExternalIdentityService> externalIdentityPorts) {
        if (externalIdentityPorts == null || externalIdentityPorts.isEmpty()) {
            return Map.of();
        }
        Map<AppExternalIdentityChannel, IAppExternalIdentityService> indexed =
            new EnumMap<>(AppExternalIdentityChannel.class);
        for (IAppExternalIdentityService externalIdentityPort : externalIdentityPorts) {
            if (externalIdentityPort == null || externalIdentityPort.channel() == null
                || indexed.putIfAbsent(externalIdentityPort.channel(), externalIdentityPort) != null) {
                throw new IllegalStateException("创作端外部身份适配器配置无效");
            }
        }
        return Map.copyOf(indexed);
    }

    private IAppExternalIdentityService externalIdentityPort(AppExternalIdentityChannel channel) {
        IAppExternalIdentityService externalIdentityPort = externalIdentityPorts.get(channel);
        if (externalIdentityPort == null) {
            throw credentialsInvalid();
        }
        return externalIdentityPort;
    }

    private String verifiedContactIdentifier(AppUser user, AppVerificationChannel channel) {
        String contact = switch (channel) {
            case PHONE -> user.getPhoneNormalized();
            case EMAIL -> user.getEmailNormalized();
        };
        if (isBlank(contact)) {
            throw accountUnavailable();
        }
        return contact;
    }

    private AppWorkspaceVo toWorkspace(AppWorkspaceSessionSnapshotDTO workspace) {
        String name = "personal".equals(workspace.workspaceType()) ? "个人工作区" : "组织工作区";
        return new AppWorkspaceVo(workspace.workspaceKey(), name, workspace.roleCode());
    }

    private AppSecurityException credentialsInvalid() {
        return new AppSecurityException("创作端账号或登录凭据不正确",
            AppAuthErrorCodes.APP_AUTH_CREDENTIALS_INVALID);
    }

    private AppSecurityException accountUnavailable() {
        return new AppSecurityException("创作端账号不可用", AppAuthErrorCodes.APP_ACCOUNT_UNAVAILABLE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String maskIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return "***";
        }
        String value = identifier.trim();
        if (value.indexOf('@') > 0) {
            return maskEmail(value);
        }
        if (value.chars().allMatch(Character::isDigit)) {
            return maskPhone(value);
        }
        return value.substring(0, 1) + "***";
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String value = phone.trim();
        if (value.length() >= 7) {
            return value.substring(0, 3) + "****" + value.substring(value.length() - 4);
        }
        return "***";
    }

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String value = email.trim();
        int at = value.indexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            return value.substring(0, 1) + "***";
        }
        return value.substring(0, 1) + "***" + value.substring(at);
    }
}
