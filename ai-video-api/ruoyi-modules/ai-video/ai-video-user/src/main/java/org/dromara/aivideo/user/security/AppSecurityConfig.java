package org.dromara.aivideo.user.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

/**
 * 创作端 API 的独立拦截器配置。
 */
@Configuration
@ConditionalOnAppSecurityEnabled
public class AppSecurityConfig implements WebMvcConfigurer {

    private final AppAuthenticationInterceptor authenticationInterceptor;

    /**
     * 创建创作端 API 安全配置。
     *
     * @param authenticationInterceptor 创作端认证拦截器
     */
    public AppSecurityConfig(AppAuthenticationInterceptor authenticationInterceptor) {
        this.authenticationInterceptor = Objects.requireNonNull(authenticationInterceptor, "创作端认证拦截器不能为空");
    }

    /**
     * 在 Controller 注解鉴权前先完成 app 登录态、客户端策略及会话修订校验。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
            .addPathPatterns("/api/**")
            .order(-200);
        registry.addInterceptor(asyncAwarePermissionInterceptor())
            .addPathPatterns("/api/**")
            .order(-190);
    }

    private static SaInterceptor asyncAwarePermissionInterceptor() {
        return new SaInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
                throws Exception {
                return request.getDispatcherType() == DispatcherType.ASYNC
                    || super.preHandle(request, response, handler);
            }
        };
    }
}
