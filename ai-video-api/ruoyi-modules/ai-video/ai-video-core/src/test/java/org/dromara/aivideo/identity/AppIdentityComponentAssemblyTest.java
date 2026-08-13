package org.dromara.aivideo.identity;

import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.dto.RegisterAppUserDTO;
import org.dromara.aivideo.identity.service.impl.AppIdentityServiceImpl;
import org.dromara.aivideo.identity.service.impl.AppSecurityAuditServiceImpl;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppSecurityAuditMapper;
import org.dromara.aivideo.identity.mapper.AppSocialIdentityMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.IAppIdentityOperationAuthorizationService;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryGrant;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryReservation;
import org.dromara.aivideo.identity.security.IAppPasswordRecoveryVerificationService;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerificationRequest;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerifier;
import org.dromara.aivideo.identity.security.AppSelfRegistrationGrant;
import org.dromara.aivideo.identity.security.IAppSelfRegistrationVerificationService;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerificationRequest;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerifier;
import org.dromara.aivideo.identity.security.AppVerificationChannel;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证身份核心由真实 Spring 组件扫描装配，并对可插拔安全适配器实施确定性的单一委派规则。
 */
@Tag("dev")
class AppIdentityComponentAssemblyTest {

    @Test
    void createsTransactionalIdentityServiceAndSafelyDeniesWhenNoSecurityAdaptersExist() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(IdentityComponentAssemblyConfiguration.class)) {
            IAppIdentityService identityService = context.getBean(IAppIdentityService.class);

            assertThat(AopUtils.isAopProxy(identityService)).isTrue();
            assertThat(AopUtils.getTargetClass(identityService)).isEqualTo(AppIdentityServiceImpl.class);
            assertThat(context.getBean(AppPasswordPolicy.class)).isNotNull();
            assertThat(context.getBeansOfType(IAppIdentityOperationAuthorizationService.class)).isEmpty();
            assertThat(context.getBeansOfType(IAppSelfRegistrationVerificationService.class)).isEmpty();
            assertThat(context.getBeansOfType(IAppPasswordRecoveryVerificationService.class)).isEmpty();
            assertThat(context.getBean(AppIdentityOperationAuthorizer.class)
                .isAuthorized(AppActorContext.sysUser(1001L), AppIdentityOperation.REGISTER_APP_USER, 0L))
                .isFalse();
            assertThat(context.getBean(AppSelfRegistrationVerifier.class)
                .verifyAndConsume(new AppSelfRegistrationVerificationRequest(
                    "grant-default", "desktop", "component-user", null, null)))
                .isFalse();
            assertThatThrownBy(() -> context.getBean(AppPasswordRecoveryVerifier.class)
                .reserve(new AppPasswordRecoveryVerificationRequest(
                    "challenge-default", "123456", "desktop", 3L)))
                .isInstanceOf(ServiceException.class);
        }
    }

    @Test
    void delegatesOperationalRegistrationThroughTheOnlyConfiguredAuthorizationAdapter() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(OperationAdapterConfiguration.class)) {
            IAppIdentityService identityService = context.getBean(IAppIdentityService.class);
            RecordingOperationAdapter adapter = context.getBean(RecordingOperationAdapter.class);

            assertThat(context.getBeansOfType(IAppIdentityOperationAuthorizationService.class)).hasSize(1);
            assertThatThrownBy(() -> identityService.register(
                new RegisterAppUserDTO("component-sys", "Component#Pass123", "Component Sys", null, null),
                AppActorContext.sysUser(1001L)))
                .isInstanceOf(ServiceException.class);
            assertThat(adapter.invocations()).isEqualTo(1);
        }
    }

    @Test
    void delegatesSelfRegistrationGrantConsumptionThroughTheOnlyConfiguredVerificationAdapter() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(SelfRegistrationAdapterConfiguration.class)) {
            IAppIdentityService identityService = context.getBean(IAppIdentityService.class);
            RecordingSelfRegistrationAdapter adapter = context.getBean(RecordingSelfRegistrationAdapter.class);

            assertThat(context.getBeansOfType(IAppSelfRegistrationVerificationService.class)).hasSize(1);
            assertThatThrownBy(() -> identityService.registerSelf(
                new RegisterAppUserDTO("component-self", "Component#Pass123", "Component Self", null, null),
                new AppSelfRegistrationGrant("grant-component", "desktop")))
                .isInstanceOf(ServiceException.class);
            assertThat(adapter.invocations()).isEqualTo(1);
        }
    }

    @Test
    void delegatesPasswordRecoveryVerificationThroughTheOnlyConfiguredAdapter() {
        try (AnnotationConfigApplicationContext context =
                 new AnnotationConfigApplicationContext(PasswordRecoveryAdapterConfiguration.class)) {
            RecordingPasswordRecoveryAdapter adapter = context.getBean(RecordingPasswordRecoveryAdapter.class);

            AppPasswordRecoveryReservation reservation = context.getBean(AppPasswordRecoveryVerifier.class)
                .reserve(new AppPasswordRecoveryVerificationRequest(
                    "challenge-component", "123456", "desktop", 3L));

            assertThat(context.getBeansOfType(IAppPasswordRecoveryVerificationService.class)).hasSize(1);
            assertThat(adapter.invocations()).isEqualTo(1);
            assertThat(reservation.grant()).isEqualTo(new AppPasswordRecoveryGrant(
                1001L, AppVerificationChannel.PHONE, 2L, 3L));
        }
    }

    @Test
    void failsFastWhenMultipleAuthorizationAdaptersArePresent() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(MultipleOperationAdaptersConfiguration.class))
            .satisfies(error -> assertThat(rootCause(error))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AppIdentityOperationAuthorizationPort")
                .hasMessageContaining("多个"));
    }

    @Test
    void failsFastWhenMultipleSelfRegistrationVerificationAdaptersArePresent() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(MultipleSelfRegistrationAdaptersConfiguration.class))
            .satisfies(error -> assertThat(rootCause(error))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AppSelfRegistrationVerificationPort")
                .hasMessageContaining("多个"));
    }

    @Test
    void failsFastWhenMultiplePasswordRecoveryVerificationAdaptersArePresent() {
        assertThatThrownBy(() -> new AnnotationConfigApplicationContext(MultiplePasswordRecoveryAdaptersConfiguration.class))
            .satisfies(error -> assertThat(rootCause(error))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AppPasswordRecoveryVerificationPort")
                .hasMessageContaining("多个"));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @ComponentScan(
        basePackageClasses = {AppIdentityServiceImpl.class, AppPasswordPolicy.class},
        useDefaultFilters = false,
        includeFilters = @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = {
                AppIdentityServiceImpl.class,
                AppSecurityAuditServiceImpl.class,
                AppPasswordPolicy.class,
                AppIdentityOperationAuthorizer.class,
                AppSelfRegistrationVerifier.class,
                AppPasswordRecoveryVerifier.class
            }
        )
    )
    static class IdentityComponentAssemblyConfiguration {

        @Bean
        AppUserMapper appUserMapper() {
            AppUserMapper mapper = mock(AppUserMapper.class);
            when(mapper.selectUserIdsByAnyIdentifierIncludingDeleted(any())).thenReturn(List.of());
            return mapper;
        }

        @Bean
        AppSocialIdentityMapper appSocialIdentityMapper() {
            return mock(AppSocialIdentityMapper.class);
        }

        @Bean
        AppRoleMapper appRoleMapper() {
            return mock(AppRoleMapper.class);
        }

        @Bean
        AppUserRoleMapper appUserRoleMapper() {
            return mock(AppUserRoleMapper.class);
        }

        @Bean
        AppSecurityAuditMapper appSecurityAuditMapper() {
            return mock(AppSecurityAuditMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
            when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return transactionManager;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class OperationAdapterConfiguration {

        @Bean
        RecordingOperationAdapter recordingOperationAdapter() {
            return new RecordingOperationAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class SelfRegistrationAdapterConfiguration {

        @Bean
        RecordingSelfRegistrationAdapter recordingSelfRegistrationAdapter() {
            return new RecordingSelfRegistrationAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class PasswordRecoveryAdapterConfiguration {

        @Bean
        RecordingPasswordRecoveryAdapter recordingPasswordRecoveryAdapter() {
            return new RecordingPasswordRecoveryAdapter();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class MultipleOperationAdaptersConfiguration {

        @Bean
        RecordingOperationAdapter firstOperationAdapter() {
            return new RecordingOperationAdapter();
        }

        @Bean
        IAppIdentityOperationAuthorizationService secondOperationAdapter() {
            return (actor, operation, targetUserId) -> false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class MultipleSelfRegistrationAdaptersConfiguration {

        @Bean
        RecordingSelfRegistrationAdapter firstSelfRegistrationAdapter() {
            return new RecordingSelfRegistrationAdapter();
        }

        @Bean
        IAppSelfRegistrationVerificationService secondSelfRegistrationAdapter() {
            return request -> false;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(IdentityComponentAssemblyConfiguration.class)
    static class MultiplePasswordRecoveryAdaptersConfiguration {

        @Bean
        RecordingPasswordRecoveryAdapter firstPasswordRecoveryAdapter() {
            return new RecordingPasswordRecoveryAdapter();
        }

        @Bean
        IAppPasswordRecoveryVerificationService secondPasswordRecoveryAdapter() {
            return new IAppPasswordRecoveryVerificationService() {
                @Override
                public AppPasswordRecoveryReservation reserve(AppPasswordRecoveryVerificationRequest request) {
                    return null;
                }

                @Override
                public void commit(AppPasswordRecoveryReservation reservation) {
                }

                @Override
                public void release(AppPasswordRecoveryReservation reservation) {
                }
            };
        }
    }

    static final class RecordingOperationAdapter implements IAppIdentityOperationAuthorizationService {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public boolean isAuthorized(AppActorContext actor, AppIdentityOperation operation, long targetUserId) {
            invocations.incrementAndGet();
            return true;
        }

        int invocations() {
            return invocations.get();
        }
    }

    static final class RecordingSelfRegistrationAdapter implements IAppSelfRegistrationVerificationService {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public boolean verifyAndConsume(AppSelfRegistrationVerificationRequest request) {
            invocations.incrementAndGet();
            return true;
        }

        int invocations() {
            return invocations.get();
        }
    }

    static final class RecordingPasswordRecoveryAdapter implements IAppPasswordRecoveryVerificationService {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public AppPasswordRecoveryReservation reserve(AppPasswordRecoveryVerificationRequest request) {
            invocations.incrementAndGet();
            return new AppPasswordRecoveryReservation(request.challengeId(), "component-reservation",
                new AppPasswordRecoveryGrant(1001L, AppVerificationChannel.PHONE, 2L, 3L));
        }

        @Override
        public void commit(AppPasswordRecoveryReservation reservation) {
        }

        @Override
        public void release(AppPasswordRecoveryReservation reservation) {
        }

        int invocations() {
            return invocations.get();
        }
    }
}
