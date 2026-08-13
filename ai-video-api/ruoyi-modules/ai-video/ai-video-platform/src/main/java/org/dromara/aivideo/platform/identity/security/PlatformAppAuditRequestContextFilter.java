package org.dromara.aivideo.platform.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.common.core.utils.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 为运营端创作身份管理接口绑定受信任审计上下文。
 *
 * <p>该过滤器不读取、不验证创作端令牌；管理接口始终由默认 sys 会话和
 * {@code @SaCheckPermission} 负责鉴权。请求结束后恢复原上下文，避免线程复用导致审计串写。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PlatformAppAuditRequestContextFilter extends OncePerRequestFilter {

    private static final String APP_MANAGEMENT_PREFIX = "/api/admin/app-";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !requestPath(request).startsWith(APP_MANAGEMENT_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AppAuditRequestContext context = new AppAuditRequestContext(newRequestId(), directPeerIp(request));
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(context)) {
            filterChain.doFilter(request, response);
        }
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String directPeerIp(HttpServletRequest request) {
        return StringUtils.strip(request.getRemoteAddr(), "[]");
    }
}
