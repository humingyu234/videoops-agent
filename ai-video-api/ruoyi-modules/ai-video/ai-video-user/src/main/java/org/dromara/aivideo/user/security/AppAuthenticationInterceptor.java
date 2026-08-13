package org.dromara.aivideo.user.security;

import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import cn.dev33.satoken.exception.NotLoginException;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppSessionRevisionGuard;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * 创作端 HTTP 身份门禁。
 *
 * <p>此拦截器只使用 {@link AppLoginHelper} 所属的 {@code app} 登录命名空间，
 * 不会读取运营端默认 Sa-Token 登录态。</p>
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppAuthenticationInterceptor implements HandlerInterceptor {

    private final AppClientPolicyService clientPolicyService;
    private final AppLoginHelper loginHelper;
    private final AppSessionRevisionGuard revisionGuard;
    private final AppUserMapper userMapper;
    private final IAppSessionService sessionService;

    /**
     * 创建创作端 HTTP 身份门禁。
     *
     * @param clientPolicyService 创作端客户端策略
     * @param loginHelper 创作端 app 登录助手
     * @param revisionGuard 创作端会话修订守卫
     * @param userMapper 创作端用户数据访问接口
     * @param sessionService 创作端会话服务
     */
    public AppAuthenticationInterceptor(AppClientPolicyService clientPolicyService, AppLoginHelper loginHelper,
                                        AppSessionRevisionGuard revisionGuard, AppUserMapper userMapper,
                                        IAppSessionService sessionService) {
        this.clientPolicyService = Objects.requireNonNull(clientPolicyService, "创作端客户端策略不能为空");
        this.loginHelper = Objects.requireNonNull(loginHelper, "创作端登录助手不能为空");
        this.revisionGuard = Objects.requireNonNull(revisionGuard, "创作端会话修订守卫不能为空");
        this.userMapper = Objects.requireNonNull(userMapper, "创作端用户数据访问接口不能为空");
        this.sessionService = Objects.requireNonNull(sessionService, "创作端会话服务不能为空");
    }

    /**
     * 按“app 登录态、客户端策略、会话修订”的固定顺序保护创作端路由。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param handler 即将执行的处理器
     * @return 请求可以继续时返回 true
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            return true;
        }
        StrictCredentialHeaders credentials = strictCredentials(request);
        if (AppCredentialIngressFilter.isPublicAuthenticationRequest(request)) {
            AppAuthClientSnapshotDTO client = clientPolicyService.validate(request, credentials, null);
            request.setAttribute(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client);
            return true;
        }

        if (!credentials.hasAuthorization() || !loginHelper.isLogin()) {
            throw appNotLogin();
        }

        AppLoginUser loginUser;
        try {
            loginUser = loginHelper.getLoginUser();
        } catch (ServiceException exception) {
            throw appNotLogin();
        }

        AppAuthClientSnapshotDTO client = clientPolicyService.validate(request, credentials, loginUser.principal());
        request.setAttribute(AppClientPolicyService.VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE, client);
        validateCurrentSession();
        rejectWhenPasswordChangeRequired(request, loginUser.userId());
        sessionService.touchCurrentSession();
        return true;
    }

    private StrictCredentialHeaders strictCredentials(HttpServletRequest request) {
        Object value = request.getAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE);
        if (value instanceof StrictCredentialHeaders credentials) {
            return credentials;
        }
        throw new AppSecurityException("认证凭据格式不合法", AppAuthErrorCodes.MULTIPLE_AUTH_CREDENTIALS_REJECTED);
    }

    private void validateCurrentSession() {
        try {
            revisionGuard.checkCurrentSession();
        } catch (ServiceException exception) {
            if (Integer.valueOf(AppAuthErrorCodes.APP_SESSION_REVISION_STALE).equals(exception.getCode())) {
                throw new AppSecurityException("创作端会话修订已过期",
                    AppAuthErrorCodes.APP_SESSION_REVISION_STALE);
            }
            throw appNotLogin();
        }
    }

    private void rejectWhenPasswordChangeRequired(HttpServletRequest request, long appUserId) {
        AppUser user = userMapper.selectById(appUserId);
        if (user != null && Boolean.TRUE.equals(user.getMustChangePassword())
            && !allowsPasswordChangeCompletion(request)) {
            throw new AppSecurityException("请先修改密码后再继续操作",
                AppAuthErrorCodes.APP_PASSWORD_RESET_REQUIRED);
        }
    }

    /**
     * 密码强制修改期间，只允许读取本人信息、改密、退出与会话管理。
     */
    private boolean allowsPasswordChangeCompletion(HttpServletRequest request) {
        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)
            && AppCredentialIngressFilter.matchesRequestPath(request, "/api/auth/me")) {
            return true;
        }
        if ("PUT".equalsIgnoreCase(method)
            && AppCredentialIngressFilter.matchesRequestPath(request, "/api/auth/password")) {
            return true;
        }
        if ("POST".equalsIgnoreCase(method)
            && AppCredentialIngressFilter.matchesRequestPath(request, "/api/auth/logout")) {
            return true;
        }
        return ("GET".equalsIgnoreCase(method)
            && AppCredentialIngressFilter.matchesRequestPath(request, "/api/auth/sessions"))
            || ("DELETE".equalsIgnoreCase(method)
            && AppCredentialIngressFilter.matchesLowercaseUuidSessionPath(request));
    }

    private NotLoginException appNotLogin() {
        return NotLoginException.newInstance("app", NotLoginException.NOT_TOKEN,
            "创作端登录状态异常，请重新登录", null);
    }
}
