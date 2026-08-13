package org.dromara.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.security.config.properties.SecurityProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;

/**
 * 在默认 Sa-Token 链解析前拒绝歧义认证凭据。
 */
public class StrictHeaderCredentialFilter extends OncePerRequestFilter {

    private static final int INVALID_CREDENTIAL_CODE = 46132;

    private static final String CLIENT_ID_HEADER = "clientid";

    private static final String TOKEN_PARAMETER = "token";

    private static final String INVALID_CREDENTIAL_RESPONSE =
        "{\"code\":" + INVALID_CREDENTIAL_CODE + ",\"msg\":\"认证凭据格式不合法\",\"data\":null}";

    private final SecurityProperties securityProperties;

    public StrictHeaderCredentialFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String[] excludes = securityProperties.getExcludes();
        if (excludes == null || excludes.length == 0) {
            return false;
        }
        String requestPath = requestPath(request);
        return Arrays.stream(excludes)
            .filter(StringUtils::isNotBlank)
            .anyMatch(pattern -> StringUtils.isMatch(pattern, requestPath));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (hasForbiddenCredentialParameter(request) || hasForbiddenCredentialCookie(request)
            || !hasValidAuthorizationHeader(request) || !hasValidClientIdHeader(request)) {
            writeInvalidCredentialResponse(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasForbiddenCredentialParameter(HttpServletRequest request) {
        return request.getParameterMap().keySet().stream().anyMatch(this::isCredentialName);
    }

    private boolean hasForbiddenCredentialCookie(HttpServletRequest request) {
        Enumeration<String> cookieHeaders = request.getHeaders(HttpHeaders.COOKIE);
        while (cookieHeaders.hasMoreElements()) {
            String cookieHeader = cookieHeaders.nextElement();
            if (cookieHeader == null) {
                continue;
            }
            for (String cookie : cookieHeader.split(";")) {
                int separatorIndex = cookie.indexOf('=');
                String cookieName = (separatorIndex < 0 ? cookie : cookie.substring(0, separatorIndex)).trim();
                if (isCredentialName(cookieName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasValidAuthorizationHeader(HttpServletRequest request) {
        return hasValidSingleHeader(request, HttpHeaders.AUTHORIZATION,
            value -> value.matches("Bearer [^\\s,]+")
                || (isActuatorRequest(request) && value.matches("Basic [^\\s,]+")));
    }

    private boolean hasValidClientIdHeader(HttpServletRequest request) {
        return hasValidSingleHeader(request, CLIENT_ID_HEADER,
            value -> !containsWhitespace(value) && !value.contains(","));
    }

    private boolean hasValidSingleHeader(HttpServletRequest request, String headerName, HeaderValueValidator validator) {
        Enumeration<String> values = request.getHeaders(headerName);
        if (!values.hasMoreElements()) {
            return true;
        }
        String value = values.nextElement();
        if (values.hasMoreElements() || value == null || value.isBlank() || value.contains(",")) {
            return false;
        }
        return validator.isValid(value);
    }

    private boolean isCredentialName(String name) {
        return HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)
            || TOKEN_PARAMETER.equalsIgnoreCase(name)
            || CLIENT_ID_HEADER.equalsIgnoreCase(name);
    }

    private boolean containsWhitespace(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint));
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.isNotBlank(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private boolean isActuatorRequest(HttpServletRequest request) {
        String requestPath = requestPath(request);
        return "/actuator".equals(requestPath) || requestPath.startsWith("/actuator/");
    }

    private void writeInvalidCredentialResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(INVALID_CREDENTIAL_RESPONSE);
    }

    @FunctionalInterface
    private interface HeaderValueValidator {

        boolean isValid(String value);
    }

}
