package org.dromara.aivideo.user.security;

import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.common.core.utils.NetUtils;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 仅以 app_auth_client 为事实源校验创作端客户端策略。
 */
@Component
@ConditionalOnAppSecurityEnabled
public class AppClientPolicyService {

    /** 已完成入口策略验证的创作端客户端快照请求属性。 */
    public static final String VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE =
        AppClientPolicyService.class.getName() + ".verifiedClientSnapshot";

    private static final String CLIENT_RULE_SEPARATOR_REGEX = "[,;\\r\\n]+";

    private final AppAuthClientMapper authClientMapper;

    /**
     * 创建创作端客户端策略服务。
     *
     * @param authClientMapper 创作端认证客户端数据访问接口
     */
    public AppClientPolicyService(AppAuthClientMapper authClientMapper) {
        this.authClientMapper = Objects.requireNonNull(authClientMapper, "创作端认证客户端数据访问接口不能为空");
    }

    /**
     * 校验当前请求的创作端客户端、允许路径、IP 与 app 会话冻结客户端信息。
     *
     * @param request 当前请求
     * @param credentials 经入口过滤器验证的凭据元数据
     * @param principal 已登录请求的 app 主体快照；公开认证请求传入 null
     */
    public AppAuthClientSnapshotDTO validate(HttpServletRequest request, StrictCredentialHeaders credentials,
                                          AppPrincipalSnapshotDTO principal) {
        AppAuthClient authClient = findClient(credentials.clientId());
        if (!isActive(authClient)
            || !allowsRequestPath(authClient, request)
            || !allowsClientIp(authClient, directPeerIp(request))
            || !matchesFrozenClient(authClient, principal)
            || !allowsPublicGrant(authClient, request)) {
            throw unavailableClient();
        }
        return new AppAuthClientSnapshotDTO(authClient.getClientId(), authClient.getClientRevision());
    }

    /**
     * 从已通过入口策略校验的请求中读取真实创作端客户端标识和修订号。
     *
     * <p>请求头 {@code clientid} 是 app_auth_client.client_key，不能直接作为内部 clientId 使用。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 仅由认证拦截器写入的客户端事实快照
     */
    public static AppAuthClientSnapshotDTO requireVerifiedClientSnapshot(HttpServletRequest request) {
        Object snapshot = request == null ? null : request.getAttribute(VERIFIED_CLIENT_SNAPSHOT_REQUEST_ATTRIBUTE);
        if (snapshot instanceof AppAuthClientSnapshotDTO client
            && !StringUtils.isBlank(client.clientId()) && client.clientRevision() > 0) {
            return client;
        }
        throw new AppSecurityException("创作端认证客户端未通过入口校验",
            AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE);
    }

    private AppAuthClient findClient(String clientKey) {
        return authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getClientKey, clientKey)
            .eq(AppAuthClient::getDelFlag, "0"));
    }

    private boolean isActive(AppAuthClient authClient) {
        return authClient != null && authClient.getStatus() == AppIdentityStatus.ACTIVE
            && "0".equals(authClient.getDelFlag())
            && authClient.getClientId() != null && !authClient.getClientId().isBlank()
            && authClient.getClientRevision() != null && authClient.getClientRevision() > 0;
    }

    private boolean allowsRequestPath(AppAuthClient authClient, HttpServletRequest request) {
        List<String> paths = StringUtils.str2List(authClient.getAccessPaths(), CLIENT_RULE_SEPARATOR_REGEX, true, true);
        return paths.stream().anyMatch(path -> AppCredentialIngressFilter.matchesRequestPath(request, path));
    }

    private boolean allowsClientIp(AppAuthClient authClient, String clientIp) {
        if (StringUtils.isBlank(authClient.getIpWhitelist())) {
            return true;
        }
        List<String> ipRules = StringUtils.str2List(authClient.getIpWhitelist(), CLIENT_RULE_SEPARATOR_REGEX, true, true);
        return ipRules.stream().anyMatch(rule -> NetUtils.isMatchIpRule(rule, clientIp));
    }

    /**
     * 客户端白名单是访问控制边界，不信任由外部请求方自行伪造的 X-Forwarded-For 等转发头。
     * 当前启动模块尚未配置受信任反向代理链，因此只使用 Servlet 容器观测到的直连对端地址；
     * 部署在反向代理之后时，白名单应登记受信任代理地址，后续如需解析真实客户端地址必须先引入
     * 显式的受信任代理配置。
     *
     * @param request 当前请求
     * @return 容器观测到的直连对端 IP
     */
    private String directPeerIp(HttpServletRequest request) {
        return StringUtils.strip(request.getRemoteAddr(), "[]");
    }

    private boolean matchesFrozenClient(AppAuthClient authClient, AppPrincipalSnapshotDTO principal) {
        return principal == null || (Objects.equals(authClient.getClientId(), principal.clientId())
            && Objects.equals(authClient.getClientRevision(), principal.clientRevision()));
    }

    private boolean allowsPublicGrant(AppAuthClient authClient, HttpServletRequest request) {
        if (!AppCredentialIngressFilter.isPublicAuthenticationRequest(request)) {
            return true;
        }
        String requiredGrant = AppCredentialIngressFilter.publicAuthenticationGrant(request);
        List<String> allowedGrants = StringUtils.str2List(authClient.getGrantTypes(), CLIENT_RULE_SEPARATOR_REGEX, true, true);
        return allowedGrants.contains(requiredGrant);
    }

    private AppSecurityException unavailableClient() {
        return new AppSecurityException("创作端认证客户端不可用", AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE);
    }
}
