package org.dromara.aivideo.identity.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.dromara.aivideo.identity.service.IAppAuthClientService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.dto.RotateAppAuthClientSecretDTO;
import org.dromara.aivideo.identity.dto.UpdateAppAuthClientDTO;
import org.dromara.aivideo.identity.event.AppClientSessionInvalidationEvent;
import org.dromara.aivideo.identity.domain.AppAuthClient;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证创作端认证客户端变更只在事务提交后失效对应客户端会话。
 */
@Tag("dev")
class AppAuthClientServiceImplTest {

    private static final long CLIENT_RECORD_ID = 5201L;
    private static final long CLIENT_REVISION = 7L;
    private static final AppActorContext PLATFORM_ACTOR = AppActorContext.sysUser(9001L);

    @Test
    void commitsPolicyUpdateAfterAuditingAndInvalidatesOnlyItsClientSessions() {
        try (ClientMutationHarness harness = newHarness()) {
            String clientId = "app-policy-client";
            harness.stubExistingClient(clientId);

            harness.service().update(updateCommand(AppIdentityStatus.ACTIVE), PLATFORM_ACTOR);

            assertThat(harness.sequence()).containsExactly("audit", "commit", "invalidate:" + clientId);
            assertThat(harness.invalidationListener().invalidations()).containsExactly(
                AppClientSessionInvalidationEvent.clientChanged(clientId));
            assertAudit(harness, "auth_client_updated", AppSecurityAuditReason.AUTH_CLIENT_CHANGE);
        }
    }

    @Test
    void commitsClientDisableAfterAuditingAndInvalidatesOnlyItsClientSessions() {
        try (ClientMutationHarness harness = newHarness()) {
            String clientId = "app-disabled-client";
            harness.stubExistingClient(clientId);

            harness.service().update(updateCommand(AppIdentityStatus.DISABLED), PLATFORM_ACTOR);

            assertThat(harness.sequence()).containsExactly("audit", "commit", "invalidate:" + clientId);
            assertThat(harness.invalidationListener().invalidations()).containsExactly(
                AppClientSessionInvalidationEvent.clientChanged(clientId));
            assertAudit(harness, "auth_client_updated", AppSecurityAuditReason.AUTH_CLIENT_CHANGE);
        }
    }

    @Test
    void commitsSecretRotationAfterAuditingAndInvalidatesOnlyItsClientSessions() {
        try (ClientMutationHarness harness = newHarness()) {
            String clientId = "app-rotation-client";
            harness.stubExistingClient(clientId);

            harness.service().rotateSecret(new RotateAppAuthClientSecretDTO(CLIENT_RECORD_ID, CLIENT_REVISION), PLATFORM_ACTOR);

            assertThat(harness.sequence()).containsExactly("audit", "commit", "invalidate:" + clientId);
            assertThat(harness.invalidationListener().invalidations()).containsExactly(
                AppClientSessionInvalidationEvent.clientChanged(clientId));
            assertAudit(harness, "auth_client_secret_rotated", AppSecurityAuditReason.AUTH_CLIENT_SECRET_ROTATION);
        }
    }

    @Test
    void rollsBackWithoutInvalidatingAnyClientSessionWhenClientRevisionCasFails() {
        try (ClientMutationHarness harness = newHarness()) {
            harness.stubExistingClient("app-cas-client");
            when(harness.authClientMapper().update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppAuthClient>>any()))
                .thenReturn(0);

            assertThatThrownBy(() -> harness.service().update(updateCommand(AppIdentityStatus.ACTIVE), PLATFORM_ACTOR))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("修订冲突");

            assertThat(harness.sequence()).containsExactly("rollback");
            assertThat(harness.invalidationListener().invalidations()).isEmpty();
            verifyNoInteractions(harness.securityAuditService());
        }
    }

    @Test
    void rollsBackWithoutInvalidatingAnyClientSessionWhenAuditWriteFails() {
        try (ClientMutationHarness harness = newHarness()) {
            harness.stubExistingClient("app-audit-client");
            doAnswer(invocation -> {
                harness.sequence().add("audit");
                throw new ServiceException("审计写入失败");
            }).when(harness.securityAuditService()).append(any(AppSecurityAuditDTO.class));

            assertThatThrownBy(() -> harness.service().rotateSecret(
                new RotateAppAuthClientSecretDTO(CLIENT_RECORD_ID, CLIENT_REVISION), PLATFORM_ACTOR))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("审计写入失败");

            assertThat(harness.sequence()).containsExactly("audit", "rollback");
            assertThat(harness.invalidationListener().invalidations()).isEmpty();
        }
    }

    private static void assertAudit(ClientMutationHarness harness, String action, AppSecurityAuditReason reason) {
        ArgumentCaptor<AppSecurityAuditDTO> audit = ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
        verify(harness.securityAuditService()).append(audit.capture());
        assertThat(audit.getValue()).satisfies(command -> {
            assertThat(command.resourceType()).isEqualTo("app_auth_client");
            assertThat(command.resourceId()).isEqualTo(Long.toString(CLIENT_RECORD_ID));
            assertThat(command.action()).isEqualTo(action);
            assertThat(command.actorType()).isEqualTo(PLATFORM_ACTOR.actorType());
            assertThat(command.actorId()).isEqualTo(PLATFORM_ACTOR.actorId());
            assertThat(command.reason()).isEqualTo(reason.code());
        });
    }

    private static UpdateAppAuthClientDTO updateCommand(AppIdentityStatus status) {
        return new UpdateAppAuthClientDTO(CLIENT_RECORD_ID, "creator-web", "password", "/api/**", null,
            3600L, 1800L, status, CLIENT_REVISION);
    }

    private static ClientMutationHarness newHarness() {
        initializeAppAuthClientMetadata();
        AppAuthClientMapper authClientMapper = mock(AppAuthClientMapper.class);
        IAppSecurityAuditService securityAuditService = mock(IAppSecurityAuditService.class);
        AppIdentityOperationAuthorizer operationAuthorizer = mock(AppIdentityOperationAuthorizer.class);
        List<String> sequence = new ArrayList<>();
        doAnswer(invocation -> {
            sequence.add("audit");
            return null;
        }).when(securityAuditService).append(any(AppSecurityAuditDTO.class));
        when(operationAuthorizer.isAuthorized(any(), any(), anyLong())).thenReturn(true);

        RecordingClientInvalidationListener invalidationListener = new RecordingClientInvalidationListener(sequence);
        RecordingTransactionManager transactionManager = new RecordingTransactionManager(sequence);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TransactionConfiguration.class);
        context.registerBean(PlatformTransactionManager.class, () -> transactionManager);
        context.registerBean(AppAuthClientMapper.class, () -> authClientMapper);
        context.registerBean(IAppSecurityAuditService.class, () -> securityAuditService);
        context.registerBean(AppPasswordPolicy.class, AppPasswordPolicy::new);
        context.registerBean(AppIdentityOperationAuthorizer.class, () -> operationAuthorizer);
        context.registerBean(RecordingClientInvalidationListener.class, () -> invalidationListener);
        context.registerBean(AppAuthClientServiceImpl.class);
        context.refresh();

        IAppAuthClientService service = context.getBean(IAppAuthClientService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        return new ClientMutationHarness(context, service, authClientMapper, securityAuditService, invalidationListener, sequence);
    }

    private static void initializeAppAuthClientMetadata() {
        if (TableInfoHelper.getTableInfo(AppAuthClient.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AppAuthClient.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TransactionConfiguration {
    }

    private record ClientMutationHarness(
        AnnotationConfigApplicationContext context,
        IAppAuthClientService service,
        AppAuthClientMapper authClientMapper,
        IAppSecurityAuditService securityAuditService,
        RecordingClientInvalidationListener invalidationListener,
        List<String> sequence
    ) implements AutoCloseable {

        void stubExistingClient(String clientId) {
            AppAuthClient client = new AppAuthClient();
            client.setId(CLIENT_RECORD_ID);
            client.setClientId(clientId);
            client.setClientKey("creator-web");
            client.setClientRevision(CLIENT_REVISION);
            client.setStatus(AppIdentityStatus.ACTIVE);
            client.setDelFlag("0");
            when(authClientMapper.selectOne(ArgumentMatchers.<Wrapper<AppAuthClient>>any())).thenReturn(client);
            when(authClientMapper.update(isNull(), ArgumentMatchers.<LambdaUpdateWrapper<AppAuthClient>>any()))
                .thenReturn(1);
        }

        @Override
        public void close() {
            context.close();
        }
    }

    static final class RecordingClientInvalidationListener {

        private final List<String> sequence;
        private final List<AppClientSessionInvalidationEvent> invalidations = new ArrayList<>();

        private RecordingClientInvalidationListener(List<String> sequence) {
            this.sequence = sequence;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void consume(AppClientSessionInvalidationEvent event) {
            invalidations.add(event);
            sequence.add("invalidate:" + event.clientId());
        }

        private List<AppClientSessionInvalidationEvent> invalidations() {
            return List.copyOf(invalidations);
        }
    }

    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final List<String> sequence;

        private RecordingTransactionManager(List<String> sequence) {
            this.sequence = sequence;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            sequence.add("commit");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            sequence.add("rollback");
        }
    }
}
