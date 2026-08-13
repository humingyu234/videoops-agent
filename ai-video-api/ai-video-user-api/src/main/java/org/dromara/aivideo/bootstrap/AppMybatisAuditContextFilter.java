package org.dromara.aivideo.bootstrap;

import cn.dev33.satoken.util.SaTokenConsts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.common.mybatis.audit.AuditFillContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

/**
 * Binds the authenticated app actor for MyBatis audit filling.
 */
@Component
@Order(SaTokenConsts.SA_TOKEN_CONTEXT_FILTER_ORDER + 1)
@ConditionalOnAppSecurityEnabled
public class AppMybatisAuditContextFilter extends OncePerRequestFilter {

    private final AppLoginHelper loginHelper;

    public AppMybatisAuditContextFilter(AppLoginHelper loginHelper) {
        this.loginHelper = Objects.requireNonNull(loginHelper, "app 登录助手不能为空");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath == null || contextPath.isEmpty()
            ? uri : uri.substring(Math.min(uri.length(), contextPath.length()));
        return !("/api".equals(path) || (path != null && path.startsWith("/api/")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!loginHelper.isLogin()) {
            filterChain.doFilter(request, response);
            return;
        }
        try (AuditFillContext.Scope ignored = AuditFillContext.open(loginHelper.getLoginUser().userId())) {
            filterChain.doFilter(request, response);
        }
    }
}
