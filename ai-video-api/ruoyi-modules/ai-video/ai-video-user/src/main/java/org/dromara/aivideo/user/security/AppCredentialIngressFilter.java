package org.dromara.aivideo.user.security;

import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.constant.HttpStatus;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 在读取 app 身份前拒绝多通道、重复或拼接的创作端认证凭据。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnAppSecurityEnabled
public class AppCredentialIngressFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CLIENT_ID_HEADER = "clientid";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INVALID_CREDENTIAL_RESPONSE =
        "{\"code\":46132,\"msg\":\"认证凭据格式不合法\",\"data\":null}";
    private static final PathPatternParser PATH_PATTERN_PARSER = new PathPatternParser();
    private static final PathPattern CREATOR_API_ROOT_PATTERN = PATH_PATTERN_PARSER.parse("/api");
    private static final PathPattern CREATOR_API_PATTERN = PATH_PATTERN_PARSER.parse("/api/**");
    private static final Pattern LOWERCASE_UUID_SESSION_PATH = Pattern.compile(
        "^/api/auth/sessions/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final List<PublicAuthenticationEndpoint> PUBLIC_AUTH_ENDPOINTS = List.of(
        new PublicAuthenticationEndpoint("/api/auth/login", "password"),
        new PublicAuthenticationEndpoint("/api/auth/sms-logins", "sms"),
        new PublicAuthenticationEndpoint("/api/auth/email-logins", "email"),
        new PublicAuthenticationEndpoint("/api/auth/social-logins", "social"),
        new PublicAuthenticationEndpoint("/api/auth/mini-program-logins", "mini_program"),
        new PublicAuthenticationEndpoint("/api/auth/verification-codes", "verification"),
        new PublicAuthenticationEndpoint("/api/auth/password-resets", "password_recovery")
    );

    /**
     * 判断当前请求是否属于创作端 API。
     *
     * @param request 当前请求
     * @return 不是创作端 API 时返回 true，跳过过滤
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !isCreatorApiRequest(request);
    }

    /**
     * 验证创作端请求凭据仅来自单一请求头，并缓存不含令牌原文的元数据。
     *
     * @param request 当前请求
     * @param response 当前响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 处理异常
     * @throws IOException 响应写入异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        List<String> authorizations = headerValues(request, AUTHORIZATION_HEADER);
        List<String> clientIds = headerValues(request, CLIENT_ID_HEADER);
        if (isCredentialFreeCorsPreflightRequest(request, authorizations, clientIds)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean publicAuthenticationRequest = isPublicAuthenticationRequest(request);
        if (hasCredentialOutsideHeaders(request)
            || !hasSingleClientId(clientIds)
            || !hasExpectedAuthorization(authorizations, publicAuthenticationRequest)) {
            reject(response);
            return;
        }

        request.setAttribute(StrictCredentialHeaders.REQUEST_ATTRIBUTE,
            new StrictCredentialHeaders(clientIds.getFirst(), !authorizations.isEmpty()));
        filterChain.doFilter(request, response);
    }

    /**
     * 只让无实际认证凭据的标准 CORS 预检请求继续交由后续 CORS 过滤器处理。
     *
     * @param request 当前请求
     * @param authorizations Authorization 请求头值
     * @param clientIds clientid 请求头值
     * @return 是不携带认证凭据的标准预检请求时返回 true
     */
    private boolean isCredentialFreeCorsPreflightRequest(HttpServletRequest request,
                                                          List<String> authorizations,
                                                          List<String> clientIds) {
        return CorsUtils.isPreFlightRequest(request)
            && authorizations.isEmpty()
            && clientIds.isEmpty()
            && !hasCredentialOutsideHeaders(request);
    }

    /**
     * 判断指定请求是否是精确允许的公开认证入口。
     *
     * @param request 当前请求
     * @return 是公开认证入口时返回 true
     */
    static boolean isPublicAuthenticationRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
            && publicAuthenticationEndpoint(request) != null;
    }

    /**
     * 读取去除 context path 后的请求路径。
     *
     * @param request 当前请求
     * @return 用于策略匹配的请求路径
     */
    static boolean isCreatorApiRequest(HttpServletRequest request) {
        try {
            PathContainer path = requestPath(request);
            return CREATOR_API_ROOT_PATTERN.matches(path) || CREATOR_API_PATTERN.matches(path);
        } catch (RuntimeException exception) {
            return true;
        }
    }

    static boolean matchesRequestPath(HttpServletRequest request, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        try {
            return PATH_PATTERN_PARSER.parse(pattern).matches(requestPath(request));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 严格匹配会话撤销资源：仅允许一个小写 UUID 路径段。
     *
     * @param request 当前请求
     * @return 路径恰为 {@code /api/auth/sessions/{lowercase-uuid}} 时返回 {@code true}
     */
    static boolean matchesLowercaseUuidSessionPath(HttpServletRequest request) {
        try {
            return LOWERCASE_UUID_SESSION_PATH.matcher(requestPath(request).value()).matches();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String publicAuthenticationGrant(HttpServletRequest request) {
        PublicAuthenticationEndpoint endpoint = publicAuthenticationEndpoint(request);
        if (endpoint == null) {
            throw new AppSecurityException("非法的创作端公开认证路径", AppAuthErrorCodes.APP_AUTH_CLIENT_UNAVAILABLE);
        }
        return endpoint.grantType();
    }

    private static PublicAuthenticationEndpoint publicAuthenticationEndpoint(HttpServletRequest request) {
        for (PublicAuthenticationEndpoint endpoint : PUBLIC_AUTH_ENDPOINTS) {
            if (endpoint.pattern().matches(requestPath(request))) {
                return endpoint;
            }
        }
        return null;
    }

    private static PathContainer requestPath(HttpServletRequest request) {
        return ServletRequestPathUtils.parseAndCache(request).pathWithinApplication();
    }

    private record PublicAuthenticationEndpoint(PathPattern pattern, String grantType) {

        private PublicAuthenticationEndpoint(String pathPattern, String grantType) {
            this(PATH_PATTERN_PARSER.parse(pathPattern), grantType);
        }
    }

    private boolean hasSingleClientId(List<String> clientIds) {
        return clientIds.size() == 1 && isSingleHeaderToken(clientIds.getFirst());
    }

    private boolean hasExpectedAuthorization(List<String> authorizations, boolean publicAuthenticationRequest) {
        if (publicAuthenticationRequest) {
            return authorizations.isEmpty();
        }
        return authorizations.size() == 1 && isBearerToken(authorizations.getFirst());
    }

    private boolean hasCredentialOutsideHeaders(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (isCredentialName(cookie.getName())) {
                    return true;
                }
            }
        }
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            if (isCredentialName(parameterNames.nextElement())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCredentialName(String name) {
        if (name == null) {
            return false;
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        return "authorization".equals(normalized) || "token".equals(normalized) || "clientid".equals(normalized);
    }

    private boolean isBearerToken(String value) {
        if (value == null || !value.startsWith(BEARER_PREFIX) || value.contains(",")) {
            return false;
        }
        String token = value.substring(BEARER_PREFIX.length());
        return !token.isBlank() && token.chars().noneMatch(Character::isWhitespace);
    }

    private boolean isSingleHeaderToken(String value) {
        return value != null && !value.isBlank() && !value.contains(",")
            && value.chars().noneMatch(Character::isWhitespace);
    }

    private List<String> headerValues(HttpServletRequest request, String name) {
        Enumeration<String> values = request.getHeaders(name);
        return values == null ? List.of() : Collections.list(values);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(INVALID_CREDENTIAL_RESPONSE);
    }
}
