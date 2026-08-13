package org.dromara.aivideo.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.aivideo.identity.security.AppSecurityRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 创作端独立认证被显式关闭时的失效关闭门禁。
 *
 * <p>默认 sys 安全链会排除 {@code /api/**}，因此不能让关闭 app 认证的部署
 * 继续把创作端接口裸露给外部。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class CreatorApiDisabledFilter extends OncePerRequestFilter {

    private static final String DISABLED_RESPONSE =
        "{\"code\":46130,\"msg\":\"创作端认证服务不可用\",\"data\":null}";

    private final boolean appSecurityEnabled;

    /**
     * @param configuredValue app token security flag; only the exact boolean {@code true} permits the app chain
     * @param resourceLoader creator runtime marker resource loader
     */
    @Autowired
    public CreatorApiDisabledFilter(@Value("${app.security.token.enabled:false}") String configuredValue,
                                    ResourceLoader resourceLoader) {
        this(configuredValue, resourceLoader == null ? null : resourceLoader.getClassLoader());
    }

    CreatorApiDisabledFilter(String configuredValue, ClassLoader classLoader) {
        this.appSecurityEnabled = AppSecurityRuntime.isCreatorSecurityEnabled(configuredValue, classLoader);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return appSecurityEnabled || !AppCredentialIngressFilter.isCreatorApiRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        response.setStatus(503);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(DISABLED_RESPONSE);
    }
}
