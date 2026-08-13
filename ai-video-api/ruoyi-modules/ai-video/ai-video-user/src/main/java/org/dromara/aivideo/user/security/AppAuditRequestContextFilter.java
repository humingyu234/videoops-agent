package org.dromara.aivideo.user.security;

import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
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
 * 为每个创作端 HTTP 请求绑定受信任的审计上下文。
 *
 * <p>追踪编号由服务端生成，地址只取容器观测到的直连对端，绝不信任 X-Forwarded-For。
 * 作用域在请求结束时恢复，避免 Tomcat 工作线程复用时串写审计主体。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnAppSecurityEnabled
public class AppAuditRequestContextFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AppCredentialIngressFilter.isCreatorApiRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AppAuditRequestContext context = new AppAuditRequestContext(newTraceId(), directPeerIp(request));
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(context)) {
            filterChain.doFilter(request, response);
        }
    }

    private String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String directPeerIp(HttpServletRequest request) {
        return StringUtils.strip(request.getRemoteAddr(), "[]");
    }
}
