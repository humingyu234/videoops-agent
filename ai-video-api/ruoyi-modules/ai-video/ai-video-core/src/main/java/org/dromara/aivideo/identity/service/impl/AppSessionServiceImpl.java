package org.dromara.aivideo.identity.service.impl;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.event.AppClientSessionInvalidationEvent;
import org.dromara.aivideo.identity.event.AppSessionEndedEvent;
import org.dromara.aivideo.identity.event.AppSessionEstablishedEvent;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppSessionQueryDTO;
import org.dromara.aivideo.identity.dto.AppSessionSummaryDTO;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.ConditionalOnAppSessionRuntimeEnabled;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppSessionRequestAccess;
import org.dromara.aivideo.identity.security.AppSessionTokenReference;
import org.dromara.aivideo.identity.security.AppSessionTokenRevoker;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.utils.RedisUtils;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 基于独立 app Sa-Token 命名空间和 Redis 在线索引的创作端会话服务实现。
 */
@Service
@ConditionalOnAppSessionRuntimeEnabled
public class AppSessionServiceImpl implements IAppSessionService {

    private static final String ONLINE_SESSION_KEY_PREFIX = "aivideo:app:online:";
    private static final int WORKSPACE_NOT_AVAILABLE_CODE = 46126;

    private final Optional<AppSessionRequestAccess> requestAccess;
    private final AppUserMapper userMapper;
    private final Optional<AppPersonalWorkspaceSnapshotProvider> personalWorkspaceSnapshotProvider;
    private final AppSessionTokenRevoker sessionTokenRevoker;
    private final IAppSecurityAuditService securityAuditService;
    private final AppIdentityOperationAuthorizer operationAuthorizer;
    private final String redisKeyPrefix;

    /**
     * 创建创作端会话服务。
     *
     * @param requestAccess 当前请求的 app 会话访问入口；运营端仅装配会话撤销运行时
     * @param userMapper 创作端用户事实源
     * @param personalWorkspaceSnapshotProvider 个人工作区规范快照提供器
     * @param sessionTokenRevoker app 令牌撤销器
     * @param securityAuditService 创作端安全审计服务
     * @param operationAuthorizer 创作端运营操作授权器
     */
    @Autowired
    public AppSessionServiceImpl(Optional<AppSessionRequestAccess> requestAccess,
                                 AppUserMapper userMapper,
                                 Optional<AppPersonalWorkspaceSnapshotProvider> personalWorkspaceSnapshotProvider,
                                 AppSessionTokenRevoker sessionTokenRevoker,
                                 IAppSecurityAuditService securityAuditService,
                                 AppIdentityOperationAuthorizer operationAuthorizer,
                                 @Value("${sa-token.redis-key-prefix:}") String redisKeyPrefix) {
        this.requestAccess = requestAccess == null ? Optional.empty() : requestAccess;
        this.redisKeyPrefix = normalizeRedisKeyPrefix(redisKeyPrefix);
        this.userMapper = Objects.requireNonNull(userMapper, "创作端用户数据访问接口不能为空");
        this.personalWorkspaceSnapshotProvider = personalWorkspaceSnapshotProvider == null
            ? Optional.empty() : personalWorkspaceSnapshotProvider;
        this.sessionTokenRevoker = Objects.requireNonNull(sessionTokenRevoker, "创作端会话撤销器不能为空");
        this.securityAuditService = Objects.requireNonNull(securityAuditService, "创作端安全审计服务不能为空");
        this.operationAuthorizer = Objects.requireNonNull(operationAuthorizer, "创作端运营授权器不能为空");
    }

    /**
     * 供不触发会话撤销写路径的核心单元测试构造。
     */
    public AppSessionServiceImpl(AppLoginHelper loginHelper,
                                 AppUserMapper userMapper,
                                 AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider) {
        this(loginHelper, userMapper, personalWorkspaceSnapshotProvider, null, null, "");
    }

    public AppSessionServiceImpl(AppLoginHelper loginHelper,
                                 AppUserMapper userMapper,
                                 AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider,
                                 String redisKeyPrefix) {
        this(loginHelper, userMapper, personalWorkspaceSnapshotProvider, null, null, redisKeyPrefix);
    }

    /**
     * 兼容核心单元测试和完整创作端安全运行时的构造入口。
     *
     * <p>生产认证令牌签发仅由 {@code ai-video-user} 模块的
     * {@code AppAuthenticationSessionIssuer} 承担；本构造器不会暴露给运营端启动模块。</p>
     *
     * @param loginHelper 创作端 app 登录访问入口
     * @param userMapper 创作端用户事实源
     * @param personalWorkspaceSnapshotProvider 个人工作区规范快照提供器
     * @param securityAuditService 创作端安全审计服务
     * @param operationAuthorizer 创作端运营操作授权器
     */
    public AppSessionServiceImpl(AppLoginHelper loginHelper,
                                 AppUserMapper userMapper,
                                 AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider,
                                 IAppSecurityAuditService securityAuditService,
                                 AppIdentityOperationAuthorizer operationAuthorizer) {
        this(loginHelper, userMapper, personalWorkspaceSnapshotProvider, securityAuditService, operationAuthorizer, "");
    }

    private AppSessionServiceImpl(AppLoginHelper loginHelper,
                                  AppUserMapper userMapper,
                                  AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider,
                                  IAppSecurityAuditService securityAuditService,
                                  AppIdentityOperationAuthorizer operationAuthorizer,
                                  String redisKeyPrefix) {
        AppLoginHelper requiredLoginHelper = Objects.requireNonNull(loginHelper, "创作端登录助手不能为空");
        this.requestAccess = Optional.of(requiredLoginHelper);
        this.redisKeyPrefix = normalizeRedisKeyPrefix(redisKeyPrefix);
        this.userMapper = Objects.requireNonNull(userMapper, "创作端用户数据访问接口不能为空");
        this.personalWorkspaceSnapshotProvider = Optional.of(Objects.requireNonNull(personalWorkspaceSnapshotProvider,
            "个人工作区快照提供器不能为空"));
        this.sessionTokenRevoker = delegatedTokenRevoker(requiredLoginHelper);
        this.securityAuditService = securityAuditService;
        this.operationAuthorizer = operationAuthorizer;
    }

    /**
     * 在 app 令牌会话建立成功后同步写入在线索引；任何写入异常都会回传到登录调用栈，
     * 由 {@link AppLoginHelper} 注销刚刚建立的 app 会话。
     *
     * @param event 已建立 app 令牌会话的内部事件
     */
    @EventListener
    void registerOnlineSession(AppSessionEstablishedEvent event) {
        AppLoginUser loginUser = event.loginUser();
        AppPrincipalSnapshotDTO principal = loginUser.principal();
        writeOnlineSession(new AppOnlineSession(
            loginUser.sessionId(),
            loginUser.userId(),
            principal.clientId(),
            event.device(),
            principal.workspace().workspaceType(),
            principal.workspace().ownerId(),
                event.tokenReference(),
            LocalDateTime.now()), requestAccess.map(AppSessionRequestAccess::getCurrentSessionIndexTimeout).orElse(0L));
    }

    /**
     * 在 app 正常注销后同步删除对应的在线索引；事件不携带令牌原文。
     *
     * @param event 已注销 app 会话的内部事件
     */
    @EventListener
    void removeOnlineSession(AppSessionEndedEvent event) {
        AppOnlineSession onlineSession = readOnlineSession(event.sessionId());
        if (onlineSession != null) {
            deleteOnlineSession(onlineSession);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<AppSessionSummaryDTO> page(AppSessionQueryDTO query) {
        AppSessionQueryDTO effectiveQuery = query == null ? new AppSessionQueryDTO() : query;
        List<AppOnlineSession> sessions = onlineSessions().stream()
            .filter(session -> effectiveQuery.getAppUserId() == null
                || Objects.equals(effectiveQuery.getAppUserId(), session.appUserId()))
            .filter(session -> effectiveQuery.getClientId() == null || effectiveQuery.getClientId().isBlank()
                || Objects.equals(effectiveQuery.getClientId(), session.clientId()))
            .sorted(Comparator.comparing(AppOnlineSession::lastActiveTime).reversed())
            .toList();
        List<AppSessionSummaryDTO> summaries = paginate(sessions, effectiveQuery).stream()
            .map(session -> toSummary(session, currentSessionId()))
            .toList();
        return PageResult.build(summaries, sessions.size());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AppSessionSummaryDTO> findBySessionId(String sessionId) {
        AppOnlineSession onlineSession = readOnlineSession(sessionId);
        return onlineSession == null
            ? Optional.empty()
            : Optional.of(toSummary(onlineSession, currentSessionId()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AppSessionSummaryDTO> currentUserSessions(long userId) {
        if (userId <= 0) {
            return List.of();
        }
        String currentSessionId = currentSessionId();
        return onlineSessions().stream()
            .filter(session -> session.appUserId() == userId)
            .sorted(Comparator.comparing(AppOnlineSession::lastActiveTime).reversed())
            .map(session -> toSummary(session, currentSessionId))
            .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void touchCurrentSession() {
        AppSessionRequestAccess access = requestAccess.orElse(null);
        if (access == null || !access.isLogin()) {
            return;
        }
        AppLoginUser loginUser = access.getLoginUser();
        AppOnlineSession onlineSession = readOnlineSession(loginUser.sessionId());
        if (onlineSession == null) {
            return;
        }
        updateOnlineSessionIfExists(new AppOnlineSession(
            onlineSession.sessionId(), onlineSession.appUserId(), onlineSession.clientId(), onlineSession.device(),
            onlineSession.workspaceType(), onlineSession.workspaceOwnerId(), onlineSession.tokenReference(),
            LocalDateTime.now()), access.getCurrentSessionIndexTimeout());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeSession(long actorUserId, String sessionId, AppActorContext actor, String reason) {
        AppOnlineSession onlineSession = requireOnlineSession(sessionId);
        if (actorUserId != onlineSession.appUserId()) {
            throw new ServiceException("创作端会话不属于指定用户");
        }
        authorizeRevoke(actor, onlineSession.appUserId());
        if (reason == null || reason.isBlank()) {
            throw new ServiceException("创作端会话撤销原因不能为空");
        }
        appendSessionRevocationAudit(onlineSession, actor, reason);
        revokeOnlineSessionAfterCommit(onlineSession);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AppPrincipalSnapshotDTO replaceWorkspace(AppWorkspaceSessionSnapshotDTO workspace) {
        AppSessionRequestAccess access = requireRequestAccess();
        AppLoginUser currentLoginUser = access.getLoginUser();
        AppPrincipalSnapshotDTO principal = currentLoginUser.principal();
        AppWorkspaceSessionSnapshotDTO canonicalWorkspace = canonicalPersonalWorkspace(principal, workspace);
        AppPrincipalSnapshotDTO replaced = new AppPrincipalSnapshotDTO(
            principal.appUserId(), principal.username(), principal.clientId(), principal.credentialRevision(),
            principal.identityRevision(), principal.permissionRevision(), principal.clientRevision(), canonicalWorkspace);
        AppLoginUser replacedLoginUser = new AppLoginUser(replaced, currentLoginUser.sessionId());
        access.replaceCurrentLoginUser(replacedLoginUser);

        AppOnlineSession onlineSession = readOnlineSession(currentLoginUser.sessionId());
        if (onlineSession != null) {
            updateOnlineSessionIfExists(new AppOnlineSession(
                onlineSession.sessionId(), onlineSession.appUserId(), onlineSession.clientId(), onlineSession.device(),
                canonicalWorkspace.workspaceType(), canonicalWorkspace.ownerId(), onlineSession.tokenReference(),
                LocalDateTime.now()),
                access.getCurrentSessionIndexTimeout());
        }
        return replaced;
    }

    /**
     * P0-A 只接受个人工作区选择，并始终从 app 用户事实源重建规范快照。
     *
     * <p>调用方传入的工作区快照不是授权依据：租户、所有者、计费主体、角色、权限和修订字段均会被忽略。
     * P0-B 实现成员事实源和授权服务前，组织工作区及任何成员修订一律拒绝。</p>
     *
     * @param principal 当前 app 会话主体
     * @param requestedWorkspace 调用方请求的工作区选择
     * @return 从当前有效 app 用户重建的个人工作区规范快照
     */
    private AppSessionRequestAccess requireRequestAccess() {
        return requestAccess.orElseThrow(() -> new ServiceException("创作端请求会话上下文不可用"));
    }

    private AppPersonalWorkspaceSnapshotProvider requirePersonalWorkspaceSnapshotProvider() {
        return personalWorkspaceSnapshotProvider.orElseThrow(
            () -> new ServiceException("创作端个人工作区快照能力不可用"));
    }

    private static AppSessionTokenRevoker delegatedTokenRevoker(AppLoginHelper loginHelper) {
        return new AppSessionTokenRevoker() {
            @Override
            public void kickoutUserSessions(long appUserId) {
                loginHelper.kickoutUserSessions(appUserId);
            }

            @Override
            public void kickout(AppSessionTokenReference tokenReference) {
                loginHelper.kickout(tokenReference);
            }
        };
    }

    private AppWorkspaceSessionSnapshotDTO canonicalPersonalWorkspace(AppPrincipalSnapshotDTO principal,
                                                                     AppWorkspaceSessionSnapshotDTO requestedWorkspace) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0
            || requestedWorkspace == null
            || !"personal".equals(requestedWorkspace.workspaceType())
            || requestedWorkspace.membershipRevision() != null) {
            throw workspaceNotAvailable();
        }
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
            .eq(AppUser::getUserId, principal.appUserId())
            .eq(AppUser::getDelFlag, "0"));
        if (user == null || user.getStatus() != AppIdentityStatus.ACTIVE) {
            throw workspaceNotAvailable();
        }
        AppWorkspaceSessionSnapshotDTO canonical = requirePersonalWorkspaceSnapshotProvider().personalWorkspace(user);
        if (!Objects.equals(requestedWorkspace.workspaceKey(), canonical.workspaceKey())) {
            throw workspaceNotAvailable();
        }
        return canonical;
    }

    /**
     * 构造统一的工作区不可用错误，避免向调用方泄露组织、成员或租户事实。
     *
     * @return 工作区不可用业务异常
     */
    private ServiceException workspaceNotAvailable() {
        return new ServiceException("当前工作区不可用", WORKSPACE_NOT_AVAILABLE_CODE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateUserSessions(Long appUserId, AppSessionInvalidationReason reason) {
        if (appUserId == null || appUserId <= 0 || reason == null) {
            return;
        }
        sessionTokenRevoker.kickoutUserSessions(appUserId);
        onlineSessions().stream()
            .filter(session -> Objects.equals(session.appUserId(), appUserId))
            .forEach(this::deleteOnlineSession);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invalidateOrganizationSessions(Long organizationId, AppSessionInvalidationReason reason) {
        if (organizationId == null || organizationId <= 0 || reason == null) {
            return;
        }
        onlineSessions().stream()
            .filter(session -> "organization".equals(session.workspaceType())
                && Objects.equals(session.workspaceOwnerId(), organizationId))
            .forEach(this::revokeOnlineSession);
    }

    /**
     * 在身份数据事务提交后撤销受影响用户的 app 会话。
     *
     * @param event 创作端会话失效事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void consumeSessionInvalidation(AppSessionInvalidationEvent event) {
        event.appUserIds().forEach(appUserId -> invalidateUserSessions(appUserId, event.reason()));
    }

    /**
     * 在认证客户端策略事务提交后撤销受影响客户端范围内的 app 会话。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void consumeClientSessionInvalidation(AppClientSessionInvalidationEvent event) {
        invalidateClientSessions(event.clientId(), event.reason());
    }

    /**
     * 按认证客户端失效其全部 app 会话，供后续客户端领域事件调用。
     *
     * @param clientId 创作端认证客户端标识
     * @param reason 会话失效原因
     */
    void invalidateClientSessions(String clientId, AppSessionInvalidationReason reason) {
        if (clientId == null || clientId.isBlank() || reason == null) {
            return;
        }
        onlineSessions().stream()
            .filter(session -> clientId.equals(session.clientId()))
            .forEach(this::revokeOnlineSession);
    }

    /**
     * 写入在线索引，并与 app 令牌有效期保持一致。
     *
     * @param session 在线会话内部记录
     * @param timeout app 令牌剩余有效秒数
     */
    private void writeOnlineSession(AppOnlineSession session, long timeout) {
        if (timeout <= 0) {
            return;
        }
        RedisUtils.setCacheObject(onlineSessionKey(session.sessionId()), session, Duration.ofSeconds(timeout));
    }

    private void updateOnlineSessionIfExists(AppOnlineSession session, long timeout) {
        if (timeout <= 0) {
            return;
        }
        RedisUtils.setObjectIfExists(onlineSessionKey(session.sessionId()), session, Duration.ofSeconds(timeout));
    }

    /**
     * 扫描创作端专属在线索引并忽略或清理异常记录。
     *
     * @return 当前可读取的在线会话记录
     */
    private List<AppOnlineSession> onlineSessions() {
        List<AppOnlineSession> sessions = new ArrayList<>();
        for (String key : RedisUtils.keys(redisKeyPrefix + ONLINE_SESSION_KEY_PREFIX + "*")) {
            Object value = RedisUtils.getCacheObject(key);
            if (value instanceof AppOnlineSession session) {
                sessions.add(session);
            } else {
                RedisUtils.deleteObject(key);
            }
        }
        return sessions;
    }

    /**
     * 读取一个随机会话编号对应的在线索引。
     *
     * @param sessionId 随机会话编号
     * @return 在线会话内部记录；不存在时返回空
     */
    private AppOnlineSession readOnlineSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        Object value = RedisUtils.getCacheObject(onlineSessionKey(sessionId));
        return value instanceof AppOnlineSession session ? session : null;
    }

    /**
     * 查询指定会话编号并在不存在时拒绝操作。
     *
     * @param sessionId 随机会话编号
     * @return 在线会话内部记录
     */
    private AppOnlineSession requireOnlineSession(String sessionId) {
        AppOnlineSession onlineSession = readOnlineSession(sessionId);
        if (onlineSession == null) {
            throw new ServiceException("创作端会话不存在或已失效");
        }
        return onlineSession;
    }

    /**
     * 让一个 app 令牌强制下线并删除它的在线索引。
     *
     * @param onlineSession 在线会话内部记录
     */
    private void revokeOnlineSession(AppOnlineSession onlineSession) {
        sessionTokenRevoker.kickout(onlineSession.tokenReference());
        deleteOnlineSession(onlineSession);
    }

    /**
     * 删除在线会话索引。
     *
     * @param onlineSession 在线会话内部记录
     */
    private void deleteOnlineSession(AppOnlineSession onlineSession) {
        RedisUtils.deleteObject(onlineSessionKey(onlineSession.sessionId()));
    }

    /**
     * 根据当前 app 会话生成公开摘要中的当前标记。
     *
     * @return 当前随机会话编号；当前请求未登录时返回空
     */
    private String currentSessionId() {
        AppSessionRequestAccess access = requestAccess.orElse(null);
        if (access == null || !access.isLogin()) {
            return null;
        }
        return access.getLoginUser().sessionId();
    }

    /**
     * 将内部在线会话映射为不含令牌原文的公开摘要。
     *
     * @param session 在线会话内部记录
     * @param currentSessionId 当前随机会话编号
     * @return 公开会话摘要
     */
    private AppSessionSummaryDTO toSummary(AppOnlineSession session, String currentSessionId) {
        return new AppSessionSummaryDTO(session.sessionId(), session.clientId(), session.device(),
            session.lastActiveTime(), Objects.equals(session.sessionId(), currentSessionId), session.appUserId());
    }

    /**
     * 对内存中的在线会话结果进行安全分页。
     *
     * @param sessions 已排序的在线会话列表
     * @param query 分页查询条件
     * @return 当前页会话列表
     */
    private List<AppOnlineSession> paginate(List<AppOnlineSession> sessions, AppSessionQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() <= 0
            ? PageQuery.DEFAULT_PAGE_NUM : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0
            ? PageQuery.DEFAULT_PAGE_SIZE : query.getPageSize();
        long start = (long) (pageNum - 1) * pageSize;
        if (start >= sessions.size()) {
            return List.of();
        }
        long end = Math.min(start + pageSize, sessions.size());
        return sessions.subList((int) start, (int) end);
    }

    /**
     * 校验撤销动作不会越过创作端与运营端的身份边界。
     *
     * @param actor 已认证的操作者
     * @param targetUserId 被操作的创作端用户编号
     */
    private void authorizeRevoke(AppActorContext actor, long targetUserId) {
        if (actor == null || actor.actorType() == null || actor.actorId() <= 0) {
            throw new ServiceException("创作端会话操作者无效");
        }
        if (actor.actorType() == AppActorType.APP_USER) {
            if (actor.actorId() != targetUserId) {
                throw new ServiceException("创作端用户无权撤销其他用户会话");
            }
            return;
        }
        if (actor.actorType() != AppActorType.SYS_USER
            || operationAuthorizer == null
            || !operationAuthorizer.isAuthorized(actor, AppIdentityOperation.REVOKE_APP_SESSION, targetUserId)) {
            throw new ServiceException("运营端身份操作未获授权");
        }
    }

    private void appendSessionRevocationAudit(AppOnlineSession onlineSession, AppActorContext actor, String reason) {
        if (securityAuditService == null) {
            return;
        }
        String auditReason = actor.actorType() == AppActorType.SYS_USER
            ? requireAdminKickoutAuditReason(reason)
            : AppSecurityAuditReason.SESSION_REVOCATION.code();
        securityAuditService.append(new AppSecurityAuditDTO(
            "app_session",
            onlineSession.sessionId(),
            "session_revoked",
            actor.actorType(),
            actor.actorId(),
            null,
            null,
            auditReason));
    }

    /**
     * 在当前事务成功提交后撤销 app 会话，确保审计记录失败或事务回滚时不会产生无审计的强制下线。
     */
    private void revokeOnlineSessionAfterCommit(AppOnlineSession onlineSession) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // 仅供直接构造的单元测试或非事务内部调用；生产入口由 {@link Transactional} 保证在提交后执行。
            revokeOnlineSession(onlineSession);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revokeOnlineSession(onlineSession);
            }
        });
    }

    private String requireAdminKickoutAuditReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (!AppSecurityAuditReason.isAdminKickoutCode(normalized)) {
            throw new ServiceException("运营端强制下线原因不合法");
        }
        return normalized;
    }

    /**
     * 构造创作端专属在线索引键。
     *
     * @param sessionId 随机会话编号
     * @return Redis 在线索引键
     */
    private String onlineSessionKey(String sessionId) {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9-]{1,64}")) {
            throw new ServiceException("创作端会话编号格式不安全");
        }
        return redisKeyPrefix + ONLINE_SESSION_KEY_PREFIX + sessionId;
    }

    private static String normalizeRedisKeyPrefix(String value) {
        return value == null ? "" : value;
    }

    /**
     * 仅供 Redis 在线索引持久化的内部会话记录，绝不作为接口 DTO 返回。
     *
     * @param sessionId 随机会话编号
     * @param appUserId 创作端用户编号
     * @param clientId 创作端认证客户端标识
     * @param device 可展示设备类型
     * @param workspaceType 工作区类型
     * @param workspaceOwnerId 工作区所有者编号
     * @param tokenReference app 令牌服务端不透明引用，仅用于委派 app 登录助手撤销
     * @param lastActiveTime 最近活动时间
     */
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE
    )
    private static class AppOnlineSession implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        private final String sessionId;
        private final Long appUserId;
        private final String clientId;
        private final String device;
        private final String workspaceType;
        private final Long workspaceOwnerId;
        private final AppSessionTokenReference tokenReference;
        private final LocalDateTime lastActiveTime;

        @JsonCreator
        private AppOnlineSession(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("appUserId") Long appUserId,
            @JsonProperty("clientId") String clientId,
            @JsonProperty("device") String device,
            @JsonProperty("workspaceType") String workspaceType,
            @JsonProperty("workspaceOwnerId") Long workspaceOwnerId,
            @JsonProperty("tokenReference") AppSessionTokenReference tokenReference,
            @JsonProperty("lastActiveTime") LocalDateTime lastActiveTime
        ) {
            this.sessionId = sessionId;
            this.appUserId = appUserId;
            this.clientId = clientId;
            this.device = device;
            this.workspaceType = workspaceType;
            this.workspaceOwnerId = workspaceOwnerId;
            this.tokenReference = tokenReference;
            this.lastActiveTime = lastActiveTime;
        }

        private String sessionId() {
            return sessionId;
        }

        private Long appUserId() {
            return appUserId;
        }

        private String clientId() {
            return clientId;
        }

        private String device() {
            return device;
        }

        private String workspaceType() {
            return workspaceType;
        }

        private Long workspaceOwnerId() {
            return workspaceOwnerId;
        }

        private AppSessionTokenReference tokenReference() {
            return tokenReference;
        }

        private LocalDateTime lastActiveTime() {
            return lastActiveTime;
        }
    }
}
