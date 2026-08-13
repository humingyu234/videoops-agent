package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.dromara.aivideo.identity.service.IAppAuthClientService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.dto.CreateAppAuthClientDTO;
import org.dromara.aivideo.identity.dto.RotateAppAuthClientSecretDTO;
import org.dromara.aivideo.identity.dto.UpdateAppAuthClientDTO;
import org.dromara.aivideo.identity.event.AppClientSessionInvalidationEvent;
import org.dromara.aivideo.identity.dto.AppAuthClientSecretDTO;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 创作端认证客户端的安全写模型实现。
 */
@Service
public class AppAuthClientServiceImpl implements IAppAuthClientService {

    private static final String APP_AUTH_CLIENT_RESOURCE = "app_auth_client";
    private static final char[] SECRET_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final AppAuthClientMapper authClientMapper;
    private final AppPasswordPolicy passwordPolicy;
    private final IAppSecurityAuditService securityAuditService;
    private final AppIdentityOperationAuthorizer operationAuthorizer;
    private final ApplicationEventPublisher eventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建认证客户端写模型服务。
     */
    public AppAuthClientServiceImpl(AppAuthClientMapper authClientMapper, AppPasswordPolicy passwordPolicy,
                                    IAppSecurityAuditService securityAuditService,
                                    AppIdentityOperationAuthorizer operationAuthorizer,
                                    ApplicationEventPublisher eventPublisher) {
        this.authClientMapper = Objects.requireNonNull(authClientMapper, "创作端认证客户端数据访问接口不能为空");
        this.passwordPolicy = Objects.requireNonNull(passwordPolicy, "创作端密码策略不能为空");
        this.securityAuditService = Objects.requireNonNull(securityAuditService, "创作端安全审计服务不能为空");
        this.operationAuthorizer = Objects.requireNonNull(operationAuthorizer, "创作端运营授权器不能为空");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "领域事件发布器不能为空");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAuthClientSecretDTO create(CreateAppAuthClientDTO command, AppActorContext actor) {
        requirePlatformOperation(actor, AppIdentityOperation.CREATE_APP_AUTH_CLIENT, 0L);
        if (command == null || command.status() == null) {
            throw new ServiceException("创作端认证客户端参数不能为空");
        }
        String clientKey = normalizeClientKey(command.clientKey());
        assertClientKeyAvailable(clientKey, null);
        String clientSecret = generateSecret();
        AppAuthClient client = new AppAuthClient();
        client.setClientId(generateClientId());
        client.setClientKey(clientKey);
        client.setClientSecretHash(passwordPolicy.hash(clientSecret));
        applyPolicy(client, command.grantTypes(), command.accessPaths(), command.ipWhitelist(), command.tokenTimeout(),
            command.activeTimeout(), command.status());
        client.setClientRevision(1L);
        applyCreateActor(client, actor);
        if (authClientMapper.insert(client) != 1 || client.getId() == null) {
            throw new ServiceException("创作端认证客户端创建失败");
        }
        appendAudit(client, "auth_client_created", null, "client_revision:1",
            AppSecurityAuditReason.AUTH_CLIENT_CREATION, actor);
        return new AppAuthClientSecretDTO(client.getId(), client.getClientId(), client.getClientKey(), clientSecret);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateAppAuthClientDTO command, AppActorContext actor) {
        if (command == null || command.status() == null || command.expectedClientRevision() <= 0) {
            throw new ServiceException("创作端认证客户端修订号无效");
        }
        requirePlatformOperation(actor, AppIdentityOperation.UPDATE_APP_AUTH_CLIENT, command.id());
        AppAuthClient current = requireClient(command.id());
        String clientKey = normalizeClientKey(command.clientKey());
        assertClientKeyAvailable(clientKey, current.getId());
        validatePolicy(command.grantTypes(), command.accessPaths(), command.tokenTimeout(), command.activeTimeout());
        int affectedRows = authClientMapper.update(null, new LambdaUpdateWrapper<AppAuthClient>()
            .eq(AppAuthClient::getId, command.id())
            .eq(AppAuthClient::getDelFlag, "0")
            .eq(AppAuthClient::getClientRevision, command.expectedClientRevision())
            .set(AppAuthClient::getClientKey, clientKey)
            .set(AppAuthClient::getGrantTypes, normalizeRequired(command.grantTypes(), "授权方式"))
            .set(AppAuthClient::getAccessPaths, normalizeRequired(command.accessPaths(), "允许访问路径"))
            .set(AppAuthClient::getIpWhitelist, normalizeOptional(command.ipWhitelist()))
            .set(AppAuthClient::getTokenTimeout, command.tokenTimeout())
            .set(AppAuthClient::getActiveTimeout, command.activeTimeout())
            .set(AppAuthClient::getStatus, command.status())
            .set(AppAuthClient::getUpdatedByType, actor.actorType())
            .set(AppAuthClient::getUpdatedById, actor.actorId())
            .setSql("client_revision = client_revision + 1"));
        assertExactlyOne(affectedRows, "创作端认证客户端修订冲突");
        appendAudit(current, "auth_client_updated", "client_revision:" + command.expectedClientRevision(),
            "client_revision:" + (command.expectedClientRevision() + 1), AppSecurityAuditReason.AUTH_CLIENT_CHANGE, actor);
        publishClientInvalidation(current.getClientId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AppAuthClientSecretDTO rotateSecret(RotateAppAuthClientSecretDTO command, AppActorContext actor) {
        if (command == null || command.expectedClientRevision() <= 0) {
            throw new ServiceException("创作端认证客户端修订号无效");
        }
        requirePlatformOperation(actor, AppIdentityOperation.ROTATE_APP_AUTH_CLIENT_SECRET, command.id());
        AppAuthClient current = requireClient(command.id());
        String clientSecret = generateSecret();
        int affectedRows = authClientMapper.update(null, new LambdaUpdateWrapper<AppAuthClient>()
            .eq(AppAuthClient::getId, command.id())
            .eq(AppAuthClient::getDelFlag, "0")
            .eq(AppAuthClient::getClientRevision, command.expectedClientRevision())
            .set(AppAuthClient::getClientSecretHash, passwordPolicy.hash(clientSecret))
            .set(AppAuthClient::getUpdatedByType, actor.actorType())
            .set(AppAuthClient::getUpdatedById, actor.actorId())
            .setSql("client_revision = client_revision + 1"));
        assertExactlyOne(affectedRows, "创作端认证客户端修订冲突");
        appendAudit(current, "auth_client_secret_rotated", "client_revision:" + command.expectedClientRevision(),
            "client_revision:" + (command.expectedClientRevision() + 1),
            AppSecurityAuditReason.AUTH_CLIENT_SECRET_ROTATION, actor);
        publishClientInvalidation(current.getClientId());
        return new AppAuthClientSecretDTO(current.getId(), current.getClientId(), current.getClientKey(), clientSecret);
    }

    private void applyPolicy(AppAuthClient client, String grantTypes, String accessPaths, String ipWhitelist,
                             long tokenTimeout, long activeTimeout, AppIdentityStatus status) {
        validatePolicy(grantTypes, accessPaths, tokenTimeout, activeTimeout);
        client.setGrantTypes(normalizeRequired(grantTypes, "授权方式"));
        client.setAccessPaths(normalizeRequired(accessPaths, "允许访问路径"));
        client.setIpWhitelist(normalizeOptional(ipWhitelist));
        client.setTokenTimeout(tokenTimeout);
        client.setActiveTimeout(activeTimeout);
        client.setStatus(status);
    }

    private void validatePolicy(String grantTypes, String accessPaths, long tokenTimeout, long activeTimeout) {
        normalizeRequired(grantTypes, "授权方式");
        normalizeRequired(accessPaths, "允许访问路径");
        if (tokenTimeout <= 0 || activeTimeout <= 0 || activeTimeout > tokenTimeout) {
            throw new ServiceException("创作端认证客户端令牌时限不合法");
        }
    }

    private AppAuthClient requireClient(long id) {
        if (id <= 0) {
            throw new ServiceException("创作端认证客户端编号无效");
        }
        AppAuthClient client = authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getId, id)
            .eq(AppAuthClient::getDelFlag, "0"));
        if (client == null) {
            throw new ServiceException("创作端认证客户端不存在");
        }
        return client;
    }

    private void assertClientKeyAvailable(String clientKey, Long selfId) {
        AppAuthClient existing = authClientMapper.selectOne(new LambdaQueryWrapper<AppAuthClient>()
            .eq(AppAuthClient::getClientKey, clientKey)
            .eq(AppAuthClient::getDelFlag, "0"));
        if (existing != null && !Objects.equals(existing.getId(), selfId)) {
            throw new ServiceException("创作端认证客户端键已存在");
        }
    }

    private void requirePlatformOperation(AppActorContext actor, AppIdentityOperation operation, long targetId) {
        if (actor == null || actor.actorType() != AppActorType.SYS_USER || actor.actorId() <= 0
            || !operationAuthorizer.isAuthorized(actor, operation, targetId)) {
            throw new ServiceException("运营端身份操作未获授权");
        }
    }

    private void applyCreateActor(AppAuthClient client, AppActorContext actor) {
        client.setCreatedByType(actor.actorType());
        client.setCreatedById(actor.actorId());
        client.setUpdatedByType(actor.actorType());
        client.setUpdatedById(actor.actorId());
    }

    private void appendAudit(AppAuthClient client, String action, String beforeDigest, String afterDigest,
                             AppSecurityAuditReason reason, AppActorContext actor) {
        securityAuditService.append(new AppSecurityAuditDTO(
            APP_AUTH_CLIENT_RESOURCE,
            Long.toString(client.getId()),
            action,
            actor.actorType(),
            actor.actorId(),
            beforeDigest,
            afterDigest,
            reason.code()));
    }

    private void publishClientInvalidation(String clientId) {
        eventPublisher.publishEvent(AppClientSessionInvalidationEvent.clientChanged(clientId));
    }

    private void assertExactlyOne(int affectedRows, String message) {
        if (affectedRows != 1) {
            throw new ServiceException(message);
        }
    }

    private String normalizeClientKey(String value) {
        String normalized = normalizeRequired(value, "客户端键").toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{1,63}")) {
            throw new ServiceException("创作端认证客户端键格式不合法");
        }
        return normalized;
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateClientId() {
        return "app-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String generateSecret() {
        char[] characters = new char[32];
        for (int index = 0; index < characters.length; index++) {
            characters[index] = SECRET_ALPHABET[secureRandom.nextInt(SECRET_ALPHABET.length)];
        }
        return new String(characters);
    }
}
