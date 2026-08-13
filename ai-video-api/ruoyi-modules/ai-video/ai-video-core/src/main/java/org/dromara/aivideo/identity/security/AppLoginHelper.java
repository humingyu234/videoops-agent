package org.dromara.aivideo.identity.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.session.SaSession;
import org.dromara.aivideo.identity.event.AppSessionEndedEvent;
import org.dromara.aivideo.identity.event.AppSessionEstablishedEvent;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 创作端 app 登录会话的唯一访问入口。
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppLoginHelper implements AppSessionRequestAccess {

    /**
     * 创作端认证客户端不可用。
     */
    private static final int APP_AUTH_CLIENT_UNAVAILABLE_CODE = 46130;

    private static final Set<String> SAFE_DEVICE_TYPES = Set.of("desktop", "web", "mobile", "electron");

    /**
     * app 令牌会话中保存创作端登录用户的固定键。
     */
    static final String LOGIN_USER_SESSION_KEY = "app:login-user:v1";

    private final AppStpLogicRegistrar registrar;
    private final ApplicationEventPublisher eventPublisher;
    private final AppAuthClientMapper authClientMapper;

    /**
     * 创建创作端登录助手。
     *
     * @param registrar app 登录逻辑注册器
     * @param eventPublisher 应用内部事件发布器
     * @param authClientMapper 创作端认证客户端数据访问接口
     */
    public AppLoginHelper(AppStpLogicRegistrar registrar, ApplicationEventPublisher eventPublisher,
                          AppAuthClientMapper authClientMapper) {
        this.registrar = Objects.requireNonNull(registrar, "创作端登录逻辑注册器不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "创作端会话事件发布器不能为空");
        this.authClientMapper = Objects.requireNonNull(authClientMapper, "创作端认证客户端数据访问接口不能为空");
    }

    /**
     * 判断当前请求是否持有有效 app 登录态。
     *
     * @return 当前请求已登录时返回 true
     */
    @Override
    public boolean isLogin() {
        return registrar.logic().isLogin();
    }

    /**
     * 读取当前 app 令牌会话中的创作端登录用户。
     *
     * @return 当前创作端登录用户
     */
    @Override
    public AppLoginUser getLoginUser() {
        AppLoginUser loginUser = registrar.logic().currentLoginUser(registrar.logic().getLoginId());
        if (loginUser == null) {
            throw new ServiceException("创作端登录会话不存在或不匹配");
        }
        return loginUser;
    }

    /**
     * 读取当前 app 令牌会话中的主体快照。
     *
     * @return 当前创作端主体快照
     */
    public AppPrincipalSnapshotDTO getPrincipal() {
        return getLoginUser().principal();
    }

    /**
     * 创建当前请求的 app 登录会话并写入独立主体快照。
     *
     * @param principal 创作端主体快照
     * @param deviceType 创作端设备类型
     * @return 已写入 app 令牌会话的创作端登录用户
     */
    public AppLoginUser login(AppPrincipalSnapshotDTO principal, String deviceType) {
        if (principal == null || principal.appUserId() == null || principal.appUserId() <= 0) {
            throw new ServiceException("创作端登录会话参数不完整");
        }
        AppAuthClient authClient = requireVerifiedAuthClient(principal);
        AppStpLogic logic = registrar.logic();
        String safeDevice = safeDeviceType(deviceType);
        boolean sessionEstablished = false;
        try {
            logic.login(principal.appUserId(), logic.createSaLoginParameter()
                .setDeviceType(safeDevice)
                .setTimeout(authClient.getTokenTimeout())
                .setActiveTimeout(authClient.getActiveTimeout()));
            sessionEstablished = true;
            SaSession tokenSession = logic.getTokenSession(true);
            AppLoginUser loginUser = new AppLoginUser(principal, UUID.randomUUID().toString());
            tokenSession.set(LOGIN_USER_SESSION_KEY, loginUser);
            eventPublisher.publishEvent(new AppSessionEstablishedEvent(
                loginUser, safeDevice, currentSessionTokenReference(), getCurrentTokenTimeout()));
            return loginUser;
        } catch (RuntimeException | Error exception) {
            if (sessionEstablished) {
                logic.logout();
            }
            throw exception;
        }
    }

    /**
     * 替换当前 app 令牌会话中的主体快照，不接触默认 login 命名空间。
     *
     * @param loginUser 替换后的创作端登录用户
     */
    @Override
    public void replaceCurrentLoginUser(AppLoginUser loginUser) {
        if (loginUser == null) {
            throw new ServiceException("创作端登录用户不能为空");
        }
        SaSession tokenSession = registrar.logic().getTokenSession(false);
        if (tokenSession == null) {
            throw new ServiceException("创作端令牌会话不存在");
        }
        tokenSession.set(LOGIN_USER_SESSION_KEY, loginUser);
    }

    /**
     * 返回当前 app 令牌的服务端不透明引用，引用本身不暴露令牌原文。
     *
     * @return 当前 app 令牌服务端引用
     */
    public AppSessionTokenReference currentSessionTokenReference() {
        String tokenValue = registrar.logic().getTokenValue();
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new ServiceException("创作端令牌不存在");
        }
        return new AppSessionTokenReference(tokenValue);
    }

    /**
     * 返回当前 app 令牌的剩余有效秒数。
     *
     * @return 剩余有效秒数；-1 表示永久有效
     */
    public long getCurrentTokenTimeout() {
        return registrar.logic().getTokenTimeout();
    }

    /**
     * 返回当前 app 在线会话索引允许保留的剩余秒数。
     *
     * <p>在线索引绝不能超过令牌的绝对有效期或空闲有效期；任一维度已失效、永久或不可读取时，
     * 返回 {@code 0} 让调用方跳过写入，而不是创建无 TTL 的孤儿索引。</p>
     *
     * @return 两种有效期均为正数时的最小剩余秒数；否则返回 {@code 0}
     */
    @Override
    public long getCurrentSessionIndexTimeout() {
        long tokenTimeout = getCurrentTokenTimeout();
        long activeTimeout = registrar.logic().getTokenActiveTimeout();
        if (tokenTimeout <= 0 || activeTimeout <= 0) {
            return 0L;
        }
        return Math.min(tokenTimeout, activeTimeout);
    }

    /**
     * 使指定创作端用户的全部 app 会话强制下线，不查询默认 login 命名空间。
     *
     * @param appUserId 创作端用户编号
     */
    public void kickoutUserSessions(long appUserId) {
        if (appUserId <= 0) {
            return;
        }
        registrar.logic().kickout(appUserId);
    }

    /**
     * 强制指定 app 令牌引用下线，不影响默认 login 命名空间。
     *
     * @param tokenReference app 令牌服务端引用
     */
    public void kickout(AppSessionTokenReference tokenReference) {
        if (tokenReference != null) {
            tokenReference.kickoutWith(registrar.logic());
        }
    }

    /**
     * 仅注销当前 app 令牌会话，不影响默认 login 命名空间。
     */
    public void logout() {
        if (!isLogin()) {
            return;
        }
        AppLoginUser loginUser = getLoginUser();
        AppStpLogic logic = registrar.logic();
        logic.logout();
        logic.clearActiveTimeoutCheckMarker();
        eventPublisher.publishEvent(new AppSessionEndedEvent(loginUser.sessionId()));
    }

    /**
     * 提供 app 登录逻辑给同一身份安全包内的会话服务。
     *
     * @return app 登录逻辑
     */
    AppStpLogic logic() {
        return registrar.logic();
    }

    /**
     * 将调用方设备标识收敛为固定枚举，避免把原始 User-Agent 或设备指纹写入 Redis 在线索引。
     *
     * @param deviceType 调用方提供的设备类型
     * @return 可安全保存和展示的设备类型
     */
    private String safeDeviceType(String deviceType) {
        if (deviceType == null || deviceType.isBlank()) {
            return "unknown";
        }
        String normalized = deviceType.trim().toLowerCase(Locale.ROOT);
        return SAFE_DEVICE_TYPES.contains(normalized) ? normalized : "unknown";
    }

    /**
     * 只从 app_auth_client 读取当前会话客户端，拒绝停用、删除、修订不一致或超时策略无效的客户端。
     *
     * @param principal 待建立会话的创作端主体快照
     * @return 已验证且可用于签发 app 会话的认证客户端
     */
    private AppAuthClient requireVerifiedAuthClient(AppPrincipalSnapshotDTO principal) {
        if (principal.clientId() == null || principal.clientId().isBlank()
            || principal.clientRevision() == null || principal.clientRevision() <= 0) {
            throw unavailableAuthClient();
        }
        AppAuthClient authClient = authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getClientId, principal.clientId())
            .eq(AppAuthClient::getStatus, AppIdentityStatus.ACTIVE)
            .eq(AppAuthClient::getDelFlag, "0"));
        if (authClient == null || authClient.getStatus() != AppIdentityStatus.ACTIVE
            || !Objects.equals(principal.clientRevision(), authClient.getClientRevision())
            || !hasValidTimeoutPolicy(authClient)) {
            throw unavailableAuthClient();
        }
        return authClient;
    }

    /**
     * 创建不泄漏具体失败原因的创作端客户端不可用异常。
     *
     * @return 固定契约错误码的业务异常
     */
    private ServiceException unavailableAuthClient() {
        return new ServiceException("创作端认证客户端不可用", APP_AUTH_CLIENT_UNAVAILABLE_CODE);
    }

    /**
     * 判断认证客户端的绝对和空闲超时是否构成有限、可执行的会话策略。
     * 空闲超时不能长于绝对超时，以免策略含义矛盾并绕过预期的有效期上限。
     *
     * @param authClient 创作端认证客户端
     * @return 超时策略有效时返回 true
     */
    private boolean hasValidTimeoutPolicy(AppAuthClient authClient) {
        Long tokenTimeout = authClient.getTokenTimeout();
        Long activeTimeout = authClient.getActiveTimeout();
        return tokenTimeout != null && tokenTimeout > 0
            && activeTimeout != null && activeTimeout > 0
            && activeTimeout <= tokenTimeout;
    }
}
