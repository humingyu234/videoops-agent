package org.dromara.aivideo.identity.service.impl;

import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.domain.AppSecurityAudit;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.mapper.AppSecurityAuditMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 仅追加创作端安全审计记录的实现。
 */
@Service
public class AppSecurityAuditServiceImpl implements IAppSecurityAuditService {

    private static final Pattern SENSITIVE_FRAGMENT = Pattern.compile(
        "(?i)(?<![A-Za-z0-9])(?:password|token|client[_\\s-]?secret|verification[_\\s-]?code)(?![A-Za-z0-9])");
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]\\$\\d{2}\\$");
    private static final Pattern SAFE_DIGEST = Pattern.compile(
        "(?:(?:credential_revision|identity_revision|permission_revision|role_revision|client_revision):[1-9]\\d*|status:(?:active|inactive|disabled))");
    private static final Pattern SAFE_CODE = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern SAFE_RESOURCE_ID = Pattern.compile("[1-9]\\d{0,18}");
    private static final Pattern SAFE_SESSION_RESOURCE_ID = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final String APP_SESSION_RESOURCE_TYPE = "app_session";
    private static final String APP_AUTH_CLIENT_RESOURCE_TYPE = "app_auth_client";
    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of(
        "app_user", "app_social_identity", "app_role", APP_AUTH_CLIENT_RESOURCE_TYPE, APP_SESSION_RESOURCE_TYPE);

    private final AppSecurityAuditMapper securityAuditMapper;

    public AppSecurityAuditServiceImpl(AppSecurityAuditMapper securityAuditMapper) {
        this.securityAuditMapper = securityAuditMapper;
    }

    /**
     * 追加一条不可包含敏感值的安全审计。
     *
     * @param command 审计命令
     */
    @Override
    public void append(AppSecurityAuditDTO command) {
        AppAuditRequestContext requestContext = AppAuditRequestContextHolder.current();
        validate(command);

        AppSecurityAudit audit = new AppSecurityAudit();
        audit.setResourceType(command.resourceType());
        audit.setResourceId(command.resourceId());
        audit.setAction(command.action());
        audit.setActorType(command.actorType());
        audit.setActorId(command.actorId());
        audit.setBeforeDigest(command.beforeDigest());
        audit.setAfterDigest(command.afterDigest());
        audit.setReason(command.reason());
        audit.setRequestId(requestContext.requestId());
        audit.setIpAddress(requestContext.ipAddress());
        audit.setOccurredAt(LocalDateTime.now());

        if (securityAuditMapper.insert(audit) != 1) {
            throw new ServiceException("安全审计追加失败");
        }
    }

    private void validate(AppSecurityAuditDTO command) {
        if (command == null
            || isBlank(command.resourceType())
            || isBlank(command.resourceId())
            || isBlank(command.action())
            || command.actorType() == null
            || command.actorId() == null
            || command.actorId() <= 0
            || isBlank(command.reason())) {
            throw new ServiceException("安全审计参数不完整");
        }
        assertNoSensitiveValue(
            command.resourceType(), command.resourceId(), command.beforeDigest(), command.afterDigest());
        assertAllowedResourceType(command.resourceType());
        assertSafeCode(command.action(), "审计动作");
        assertSafeResourceId(command.resourceType(), command.resourceId());
        assertAllowedReasonCode(command.reason());
        assertSafeDigest(command.beforeDigest());
        assertSafeDigest(command.afterDigest());
    }

    private void assertAllowedResourceType(String resourceType) {
        if (!ALLOWED_RESOURCE_TYPES.contains(resourceType)) {
            throw new ServiceException("安全审计资源类型不允许");
        }
    }

    private void assertSafeDigest(String digest) {
        if (digest != null && !SAFE_DIGEST.matcher(digest).matches()) {
            throw new ServiceException("安全审计摘要格式不安全");
        }
    }

    private void assertSafeResourceId(String resourceType, String resourceId) {
        Pattern expectedPattern = APP_SESSION_RESOURCE_TYPE.equals(resourceType)
            ? SAFE_SESSION_RESOURCE_ID : SAFE_RESOURCE_ID;
        assertMatches(expectedPattern, resourceId, "资源编号");
    }

    private void assertSafeCode(String value, String fieldName) {
        assertMatches(SAFE_CODE, value, fieldName);
    }

    private void assertAllowedReasonCode(String reason) {
        if (!AppSecurityAuditReason.isAllowedCode(reason)) {
            throw new ServiceException("安全审计原因代码不允许");
        }
    }

    private void assertMatches(Pattern pattern, String value, String fieldName) {
        if (!pattern.matcher(value).matches()) {
            throw new ServiceException(fieldName + "格式不安全");
        }
    }

    private void assertNoSensitiveValue(String... values) {
        for (String value : values) {
            if (value != null && (SENSITIVE_FRAGMENT.matcher(value).find() || BCRYPT_HASH.matcher(value).find())) {
                throw new ServiceException("安全审计不得记录敏感值");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
