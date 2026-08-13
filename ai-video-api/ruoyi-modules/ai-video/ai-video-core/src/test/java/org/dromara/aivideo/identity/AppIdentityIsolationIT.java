package org.dromara.aivideo.identity;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.event.AppSessionInvalidationEvent;
import org.dromara.aivideo.identity.dto.AuthenticatePasswordDTO;
import org.dromara.aivideo.identity.dto.RegisterAppUserDTO;
import org.dromara.aivideo.identity.service.impl.AppIdentityServiceImpl;
import org.dromara.aivideo.identity.service.impl.AppPermissionServiceImpl;
import org.dromara.aivideo.identity.service.impl.AppSecurityAuditServiceImpl;
import org.dromara.aivideo.identity.dto.AppAuthenticatedIdentityDTO;
import org.dromara.aivideo.identity.dto.AppAuthClientSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppIdentitySnapshotDTO;
import org.dromara.aivideo.identity.dto.AppRegisteredIdentityDTO;
import org.dromara.aivideo.identity.domain.AppActorType;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAudit;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.mapper.AppPermissionMapper;
import org.dromara.aivideo.identity.mapper.AppRoleMapper;
import org.dromara.aivideo.identity.mapper.AppRolePermissionMapper;
import org.dromara.aivideo.identity.mapper.AppRolePermissionMapper;
import org.dromara.aivideo.identity.mapper.AppSecurityAuditMapper;
import org.dromara.aivideo.identity.mapper.AppSocialIdentityMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.mapper.AppUserRoleMapper;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.identity.security.AppIdentityOperation;
import org.dromara.aivideo.identity.security.IAppIdentityOperationAuthorizationService;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppPasswordPolicy;
import org.dromara.aivideo.identity.security.IAppPasswordRecoveryVerificationService;
import org.dromara.aivideo.identity.security.AppPasswordRecoveryVerifier;
import org.dromara.aivideo.identity.security.AppSelfRegistrationGrant;
import org.dromara.aivideo.identity.security.IAppSelfRegistrationVerificationService;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerificationRequest;
import org.dromara.aivideo.identity.security.AppSelfRegistrationVerifier;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.mybatis.spring.annotation.MapperScan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证创作端身份事实源不会与运营端账号混用。
 */
@Tag("dev")
class AppIdentityIsolationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    private AppIdentityTestFixture fixture;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        fixture = AppIdentityTestFixture.create(ENV);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (fixture != null) {
            try {
                fixture.close();
            } finally {
                fixture = null;
            }
        }
    }

    @Test
    void authenticatesOnlyTheAppUserWhenSystemUserHasTheSameIdAndUsernameButDifferentPasswords() throws SQLException {
        fixture.insertSystemUser(1001L, "same-user", "System#Pass123");
        fixture.insertAppUser(1001L, "same-user", "App#Pass123");

        AppAuthenticatedIdentityDTO identity = fixture.withExplicitNonHttpAudit(
            () -> fixture.identityService().authenticatePassword(
                new AuthenticatePasswordDTO("same-user", "App#Pass123", "desktop"), fixture.activeClient()));

        assertThat(identity.userId()).isEqualTo(1001L);
        assertThat(identity.mustChangePassword()).isFalse();
        assertThatThrownBy(() -> fixture.identityService().authenticatePassword(
            new AuthenticatePasswordDTO("same-user", "System#Pass123", "desktop"), fixture.activeClient()))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM sys_user WHERE user_id = 1001")).isEqualTo(1L);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user WHERE user_id = 1001")).isEqualTo(1L);
        assertThat(BCrypt.checkpw("System#Pass123",
            fixture.queryString("SELECT password FROM sys_user WHERE user_id = 1001"))).isTrue();
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE actor_type = 'app_user'
              AND actor_id = 1001
              AND action = 'password_authenticated'
            """)).isEqualTo(1L);
    }

    @Test
    void rejectsForgedAppUserActorsFromOperationalRegistrationWithoutWritingIdentityFacts() throws SQLException {
        assertThatThrownBy(() -> fixture.identityService().register(
            new RegisterAppUserDTO("forged-registration", "Forged#Pass123", "Forged Actor", null, null),
            AppActorContext.appUser(987654321L)))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    @Test
    void consumesABoundSelfRegistrationGrantOnlyOnceAndAttributesEveryFactToTheNewUser() throws SQLException {
        RegisterAppUserDTO command = new RegisterAppUserDTO(
            "Self-Registered", "Self#Pass123", "Self Registered", "13800138000", "SELF@EXAMPLE.COM");

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().registerSelf(
            command, new AppSelfRegistrationGrant("unknown-grant", "desktop"))))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();

        AppSelfRegistrationGrant grant = fixture.issueSelfRegistrationGrant("grant-self", command);
        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().registerSelf(
            command, new AppSelfRegistrationGrant(grant.registrationGrantId(), "other-client"))))
            .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().registerSelf(
            new RegisterAppUserDTO("other-self", "Self#Pass123", "Other Self", "13800138000", "SELF@EXAMPLE.COM"),
            grant)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();

        AppRegisteredIdentityDTO registered = fixture.withExplicitNonHttpAudit(
            () -> fixture.identityService().registerSelf(command, grant));
        String actor = "app_user:" + registered.userId() + ":app_user:" + registered.userId();

        assertThat(fixture.queryString("SELECT must_change_password FROM app_user WHERE user_id = ?", registered.userId()))
            .isEqualTo("0");
        assertThat(fixture.queryString("""
            SELECT CONCAT(created_by_type, ':', created_by_id, ':', updated_by_type, ':', updated_by_id)
            FROM app_user
            WHERE user_id = ?
            """, registered.userId())).isEqualTo(actor);
        assertThat(fixture.queryString("""
            SELECT CONCAT(created_by_type, ':', created_by_id, ':', updated_by_type, ':', updated_by_id)
            FROM app_user_role
            WHERE user_id = ?
            """, registered.userId())).isEqualTo(actor);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE actor_type = 'app_user'
              AND actor_id = ?
              AND action = 'registered'
            """, registered.userId())).isEqualTo(1L);
        long usersBeforeReplay = fixture.queryLong("SELECT COUNT(*) FROM app_user");
        long rolesBeforeReplay = fixture.queryLong("SELECT COUNT(*) FROM app_user_role");
        long auditsBeforeReplay = fixture.queryLong("SELECT COUNT(*) FROM app_security_audit");
        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(
            () -> fixture.identityService().registerSelf(command, grant)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isEqualTo(usersBeforeReplay);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isEqualTo(rolesBeforeReplay);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isEqualTo(auditsBeforeReplay);

        AppAuthenticatedIdentityDTO authenticated = fixture.withExplicitNonHttpAudit(
            () -> fixture.identityService().authenticatePassword(
                new AuthenticatePasswordDTO("self-registered", "Self#Pass123", "desktop"), fixture.activeClient()));
        AppIdentitySnapshotDTO snapshot = fixture.identityService().requireActive(registered.userId());
        assertThat(authenticated.mustChangePassword()).isFalse();
        assertThat(snapshot.mustChangePassword()).isFalse();
    }

    @Test
    void registersAnIndependentPersonalCreatorAndKeepsNormalizedIdentityUnique() throws SQLException {
        fixture.insertSystemUser(1001L, "same-user", "Same#Pass123");
        AppActorContext systemActor = AppActorContext.sysUser(1001L);

        assertThatThrownBy(() -> fixture.identityService().register(
            new RegisterAppUserDTO("bare-system-creator", "Same#Pass123", "Bare System Creator", null, null),
            systemActor))
            .isInstanceOf(ServiceException.class)
            .hasMessage("运营端身份操作未获授权");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
        fixture.authorizeSystemActor(1001L);

        AppRegisteredIdentityDTO registered = fixture.withExplicitNonHttpAudit(() -> fixture.identityService().register(
            new RegisterAppUserDTO("  New-Creator  ", "Same#Pass123", "新创作者", "13800138000", "NEW@EXAMPLE.COM"),
            systemActor));

        assertThat(registered.userId()).isPositive();
        assertThat(registered.personalTenantId()).isPositive();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user WHERE user_id = ?", registered.userId()))
            .isEqualTo(1L);
        assertThat(fixture.queryString("SELECT username_normalized FROM app_user WHERE user_id = ?", registered.userId()))
            .isEqualTo("new-creator");
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_user_role user_role
            JOIN app_role role ON role.role_id = user_role.role_id
            WHERE user_role.user_id = ?
              AND role.role_code = 'personal_creator'
            """, registered.userId())).isEqualTo(1L);
        assertThat(fixture.queryLong("""
            SELECT COUNT(*)
            FROM app_security_audit
            WHERE actor_type = 'sys_user'
              AND actor_id = 1001
              AND resource_type = 'app_user'
              AND resource_id = ?
              AND action = 'registered'
            """, Long.toString(registered.userId()))).isEqualTo(1L);
        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().register(
            new RegisterAppUserDTO("new-creator", "Same#Pass123", "重复用户", null, null),
            systemActor)))
            .isInstanceOf(ServiceException.class);
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM sys_user WHERE user_id = 1001")).isEqualTo(1L);
    }

    @Test
    void rejectsWritesWithoutAnExplicitAuditContextAndRollsBackAllIdentityFacts() throws SQLException {
        fixture.authorizeSystemActor(1001L);

        assertThatThrownBy(() -> fixture.identityService().register(
            new RegisterAppUserDTO("missing-audit-context", "Context#Pass123", "Missing Context", null, null),
            AppActorContext.sysUser(1001L)))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit")).isZero();
    }

    @Test
    void rollsBackRegistrationWhenSecurityAuditAppendFails() throws SQLException {
        fixture.authorizeSystemActor(1001L);
        fixture.failNextAuditAppend();

        assertThatThrownBy(() -> fixture.withExplicitNonHttpAudit(() -> fixture.identityService().register(
            new RegisterAppUserDTO("rollback-creator", "Same#Pass123", "回滚创作者", null, null),
            AppActorContext.sysUser(1001L))))
            .isInstanceOf(ServiceException.class);

        assertThat(fixture.queryLong("""
            SELECT COUNT(*) FROM app_user WHERE username_normalized = 'rollback-creator'
            """)).isZero();
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isZero();
        assertThat(fixture.queryLong("""
            SELECT COUNT(*) FROM app_security_audit WHERE action = 'registered'
            """)).isZero();
    }

    @Test
    void rejectsRegistrationWhenAnyCandidateMatchesAnyExistingIdentifierColumn() throws SQLException {
        fixture.insertAppUser(1001L, "existing-user", "Existing#Pass123");
        fixture.executeUpdate("UPDATE app_user SET email_normalized = ? WHERE user_id = ?",
            "reserved@example.com", 1001L);
        fixture.authorizeSystemActor(9001L);

        assertThatThrownBy(() -> fixture.identityService().register(
            new RegisterAppUserDTO("reserved@example.com", "Candidate#Pass123", "候选用户", null, null),
            AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("创作端身份已存在");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user")).isEqualTo(1L);
    }

    @Test
    void mapsSoftDeletedUsernameConflictToAStableBusinessException() throws SQLException {
        fixture.insertAppUser(1001L, "deleted-user", "Deleted#Pass123");
        fixture.executeUpdate("UPDATE app_user SET del_flag = '2' WHERE user_id = 1001");
        fixture.authorizeSystemActor(9001L);

        assertThatThrownBy(() -> fixture.identityService().register(
            new RegisterAppUserDTO("deleted-user", "Candidate#Pass123", "候选用户", null, null),
            AppActorContext.sysUser(9001L)))
            .isInstanceOf(ServiceException.class)
            .hasMessage("创作端身份已存在");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user WHERE username_normalized = 'deleted-user'"))
            .isEqualTo(1L);
    }

    @Test
    void rejectsAmbiguousPasswordIdentifierWithoutDisclosingAResolvedAccount() throws SQLException {
        fixture.insertAppUser(1001L, "shared-identifier", "First#Pass123");
        fixture.insertAppUser(1002L, "other-user", "Second#Pass123");
        fixture.executeUpdate("UPDATE app_user SET email_normalized = ? WHERE user_id = ?",
            "shared-identifier", 1002L);

        assertThatThrownBy(() -> fixture.identityService().authenticatePassword(
            new AuthenticatePasswordDTO("shared-identifier", "First#Pass123", "desktop"), fixture.activeClient()))
            .isInstanceOf(ServiceException.class)
            .hasMessage("账号或密码错误");
        assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit WHERE action = 'password_authenticated'"))
            .isZero();
    }

    @Test
    void mapsConcurrentRegistrationCompetitionToAStableBusinessException() throws Exception {
        RegisterAppUserDTO command = new RegisterAppUserDTO(
            "race-identifier", "Race#Pass123", "并发创作者", null, null);
        List<AppSelfRegistrationGrant> grants = List.of(
            fixture.issueSelfRegistrationGrant("race-grant-first", command),
            fixture.issueSelfRegistrationGrant("race-grant-second", command));
        AtomicInteger nextGrant = new AtomicInteger();
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<RegistrationAttempt> registration = () -> {
                startBarrier.await(30, TimeUnit.SECONDS);
                try {
                    try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
                        AppAuditRequestContext.nonHttp())) {
                        AppRegisteredIdentityDTO registered = fixture.identityService().registerSelf(
                            command, grants.get(nextGrant.getAndIncrement()));
                        return new RegistrationAttempt(registered, null);
                    }
                } catch (Throwable throwable) {
                    return new RegistrationAttempt(null, throwable);
                }
            };
            Future<RegistrationAttempt> first = executor.submit(registration);
            Future<RegistrationAttempt> second = executor.submit(registration);
            List<RegistrationAttempt> attempts = List.of(
                first.get(60, TimeUnit.SECONDS),
                second.get(60, TimeUnit.SECONDS));

            List<RegistrationAttempt> successfulAttempts = attempts.stream()
                .filter(attempt -> attempt.registered() != null)
                .toList();
            List<RegistrationAttempt> failedAttempts = attempts.stream()
                .filter(attempt -> attempt.failure() != null)
                .toList();
            assertThat(successfulAttempts).hasSize(1);
            assertThat(failedAttempts).hasSize(1);
            assertThat(failedAttempts.getFirst().failure()).isInstanceOf(ServiceException.class);
            assertThat(failedAttempts.getFirst().failure().getMessage())
                .doesNotContain("DuplicateKeyException", "SQL", "app_user", "uk_app_user");
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user WHERE username_normalized = 'race-identifier'"))
                .isEqualTo(1L);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isEqualTo(1L);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit WHERE action = 'registered'"))
                .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void mapsConcurrentCrossColumnRegistrationCompetitionToTheSameBusinessFailure() throws Exception {
        RegisterAppUserDTO usernameCommand = new RegisterAppUserDTO(
            "cross-column-identifier", "Race#Pass123", "Cross Username", null, null);
        RegisterAppUserDTO emailCommand = new RegisterAppUserDTO(
            "other-race-user", "Race#Pass123", "Cross Email", null, "cross-column-identifier");
        AppSelfRegistrationGrant usernameGrant = fixture.issueSelfRegistrationGrant(
            "cross-column-username", usernameCommand);
        AppSelfRegistrationGrant emailGrant = fixture.issueSelfRegistrationGrant(
            "cross-column-email", emailCommand);
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<RegistrationAttempt> usernameRegistration = () -> {
                startBarrier.await(30, TimeUnit.SECONDS);
                try {
                    try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
                        AppAuditRequestContext.nonHttp())) {
                        return new RegistrationAttempt(
                            fixture.identityService().registerSelf(usernameCommand, usernameGrant), null);
                    }
                } catch (Throwable throwable) {
                    return new RegistrationAttempt(null, throwable);
                }
            };
            Callable<RegistrationAttempt> emailRegistration = () -> {
                startBarrier.await(30, TimeUnit.SECONDS);
                try {
                    try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
                        AppAuditRequestContext.nonHttp())) {
                        return new RegistrationAttempt(
                            fixture.identityService().registerSelf(emailCommand, emailGrant), null);
                    }
                } catch (Throwable throwable) {
                    return new RegistrationAttempt(null, throwable);
                }
            };
            Future<RegistrationAttempt> usernameAttempt = executor.submit(usernameRegistration);
            Future<RegistrationAttempt> emailAttempt = executor.submit(emailRegistration);
            List<RegistrationAttempt> attempts = List.of(
                usernameAttempt.get(60, TimeUnit.SECONDS),
                emailAttempt.get(60, TimeUnit.SECONDS));

            List<RegistrationAttempt> successfulAttempts = attempts.stream()
                .filter(attempt -> attempt.registered() != null)
                .toList();
            List<RegistrationAttempt> failedAttempts = attempts.stream()
                .filter(attempt -> attempt.failure() != null)
                .toList();
            assertThat(successfulAttempts).hasSize(1);
            assertThat(failedAttempts).hasSize(1);
            assertThat(failedAttempts.getFirst().failure()).isInstanceOf(ServiceException.class);
            assertThat(failedAttempts.getFirst().failure().getMessage())
                .doesNotContain("DuplicateKeyException", "SQL", "app_user", "uk_app_user");
            assertThat(fixture.queryLong("""
                SELECT COUNT(*)
                FROM app_user
                WHERE username_normalized = 'cross-column-identifier'
                   OR email_normalized = 'cross-column-identifier'
                """)).isEqualTo(1L);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_user_role")).isEqualTo(1L);
            assertThat(fixture.queryLong("SELECT COUNT(*) FROM app_security_audit WHERE action = 'registered'"))
                .isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void keepsIdentityCodeFreeOfOperationalIdentityAndDataPermissionDependencies() throws IOException {
        Path identitySource = AppIdentityTestFixture.locateApiRoot()
            .resolve("ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity");
        Set<String> forbiddenFragments = Set.of(
            "@DataPermission",
            "BaseEntity",
            "org.dromara.system",
            "org.dromara.common.satoken.utils.LoginHelper",
            "org.dromara.system.api.model.LoginUser"
        );
        Pattern systemTableAccess = Pattern.compile("\\b(from|join|into|update)\\s+sys_(user|client)\\b");

        try (Stream<Path> paths = Files.walk(identitySource)) {
            List<Path> sourceFiles = paths
                .filter(path -> path.toString().endsWith(".java"))
                .toList();
            assertThat(sourceFiles).isNotEmpty();
            for (Path sourceFile : sourceFiles) {
                String source = Files.readString(sourceFile, StandardCharsets.UTF_8);
                String normalized = source.toLowerCase(Locale.ROOT);
                for (String forbiddenFragment : forbiddenFragments) {
                    assertThat(normalized)
                        .as("身份源码 %s 不得包含 %s", sourceFile.getFileName(), forbiddenFragment)
                        .doesNotContain(forbiddenFragment.toLowerCase(Locale.ROOT));
                }
                assertThat(systemTableAccess.matcher(normalized).find())
                    .as("身份源码 %s 不得直接访问运营端身份表", sourceFile.getFileName())
                    .isFalse();
            }
        }
    }

    private record RegistrationAttempt(AppRegisteredIdentityDTO registered, Throwable failure) {
    }
}

/**
 * 为身份服务集成测试提供真实 MySQL 与 MyBatis Mapper 夹具。
 */
final class AppIdentityTestFixture implements AutoCloseable {

    private final Connection inspectionConnection;
    private final AnnotationConfigApplicationContext applicationContext;
    private final IAppIdentityService identityService;
    private final IAppPermissionService permissionService;
    private final FailingAuditService securityAuditService;
    private final TestAppIdentityOperationAuthorizer operationAuthorizer;
    private final TestAppSelfRegistrationVerifier selfRegistrationVerifier;
    private final TestAppSessionInvalidationEvents sessionInvalidationEvents;
    private final TransactionTemplate transactionTemplate;
    private final AtomicLong nextAssociationId = new AtomicLong(900_000_000_000_000_000L);

    private AppIdentityTestFixture(Connection inspectionConnection, AnnotationConfigApplicationContext applicationContext,
                                    IAppIdentityService identityService,
                                    IAppPermissionService permissionService,
                                    FailingAuditService securityAuditService,
                                    TestAppIdentityOperationAuthorizer operationAuthorizer,
                                    TestAppSelfRegistrationVerifier selfRegistrationVerifier,
                                    TestAppSessionInvalidationEvents sessionInvalidationEvents,
                                    TransactionTemplate transactionTemplate) {
        this.inspectionConnection = inspectionConnection;
        this.applicationContext = applicationContext;
        this.identityService = identityService;
        this.permissionService = permissionService;
        this.securityAuditService = securityAuditService;
        this.operationAuthorizer = operationAuthorizer;
        this.selfRegistrationVerifier = selfRegistrationVerifier;
        this.sessionInvalidationEvents = sessionInvalidationEvents;
        this.transactionTemplate = transactionTemplate;
    }

    static AppIdentityTestFixture create(LocalIntegrationEnvironment environment) throws SQLException, IOException {
        environment.resetDedicatedMySqlSchema();
        Connection inspectionConnection = environment.openMySqlConnection();
        AnnotationConfigApplicationContext applicationContext = null;
        try {
            reloadDatabase(inspectionConnection);

            applicationContext = new AnnotationConfigApplicationContext();
            applicationContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource("identity-it", Map.of(
                "identity.jdbc-url", environment.jdbcUrl(),
                "identity.username", environment.mysqlUsername(),
                "identity.password", environment.mysqlPassword()
            )));
            applicationContext.register(IdentityTransactionConfiguration.class);
            applicationContext.refresh();

            return new AppIdentityTestFixture(
                inspectionConnection,
                applicationContext,
                applicationContext.getBean(IAppIdentityService.class),
                applicationContext.getBean(IAppPermissionService.class),
                applicationContext.getBean(FailingAuditService.class),
                applicationContext.getBean(TestAppIdentityOperationAuthorizer.class),
                applicationContext.getBean(TestAppSelfRegistrationVerifier.class),
                applicationContext.getBean(TestAppSessionInvalidationEvents.class),
                new TransactionTemplate(applicationContext.getBean(PlatformTransactionManager.class)));
        } catch (SQLException | IOException | RuntimeException | Error exception) {
            closeAfterCreateFailure(inspectionConnection, applicationContext, exception);
            throw exception;
        }
    }

    IAppIdentityService identityService() {
        return identityService;
    }

    IAppPermissionService permissionService() {
        return permissionService;
    }

    List<AppSessionInvalidationEvent> sessionInvalidationEvents() {
        return sessionInvalidationEvents.events();
    }

    IAppSecurityAuditService securityAuditService() {
        return securityAuditService;
    }

    AppSecurityAuditMapper securityAuditMapper() {
        return applicationContext.getBean(AppSecurityAuditMapper.class);
    }

    AppSecurityAudit securityAuditByRequestId(String requestId) {
        List<AppSecurityAudit> audits = securityAuditMapper().selectListByRequestId(requestId);
        assertThat(audits).as("请求追踪编号 %s 的安全审计必须唯一", requestId).hasSize(1);
        return audits.getFirst();
    }

    void failNextAuditAppend() {
        securityAuditService.failNextAppend();
    }

    void authorizeSystemActor(long actorId) {
        operationAuthorizer.allowAllOperations(actorId);
    }

    AppSelfRegistrationGrant issueSelfRegistrationGrant(String grantId, RegisterAppUserDTO command) {
        return selfRegistrationVerifier.issue(grantId, activeClient().clientId(), command);
    }

    <T> T withExplicitNonHttpAudit(Supplier<T> action) {
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            AppAuditRequestContext.nonHttp())) {
            return action.get();
        }
    }

    void withExplicitNonHttpAudit(Runnable action) {
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            AppAuditRequestContext.nonHttp())) {
            action.run();
        }
    }

    /**
     * 在外层事务中执行成功操作后显式标记回滚。
     *
     * @param action 需要参与外层事务的操作
     */
    void withRollbackOnlyTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> {
            action.run();
            status.setRollbackOnly();
        });
    }

    AppAuthClientSnapshotDTO activeClient() {
        return new AppAuthClientSnapshotDTO("desktop", 1L);
    }

    void insertSystemUser(long userId, String username, String password) throws SQLException {
        try (PreparedStatement statement = inspectionConnection.prepareStatement("""
            INSERT INTO sys_user (user_id, user_name, nick_name, password)
            VALUES (?, ?, '运营同名用户', ?)
            """)) {
            statement.setLong(1, userId);
            statement.setString(2, username);
            statement.setString(3, BCrypt.hashpw(password));
            statement.executeUpdate();
        }
    }

    void insertAppUser(long userId, String username, String password) throws SQLException {
        try (PreparedStatement statement = inspectionConnection.prepareStatement("""
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, ?, ?, '创作端同名用户', 'active', 1, 1, 1, 'app_user', ?, 'app_user', ?)
            """)) {
            statement.setLong(1, userId);
            statement.setString(2, username);
            statement.setString(3, username.toLowerCase(Locale.ROOT));
            statement.setString(4, BCrypt.hashpw(password));
            statement.setLong(5, 10_000L + userId);
            statement.setLong(6, userId);
            statement.setLong(7, userId);
            statement.executeUpdate();
        }
    }

    void insertAppRole(long roleId, String roleCode, String roleName, String scopeType) throws SQLException {
        executeUpdate("""
            INSERT INTO app_role (
                role_id, role_code, role_name, scope_type, built_in, role_revision, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, ?, 0, 1, 'active', 'sys_user', 9001, 'sys_user', 9001)
            """, roleId, roleCode, roleName, scopeType);
    }

    void insertAppUserRole(long userId, long roleId, AppIdentityStatus status,
                           LocalDateTime validFrom, LocalDateTime validUntil) throws SQLException {
        executeUpdate("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, ?, ?, ?, 'sys_user', 9001, 'sys_user', 9001)
            """, nextAssociationId.getAndIncrement(), userId, roleId, status.getValue(), validFrom, validUntil);
    }

    void insertAppRolePermission(long roleId, long permissionId, AppIdentityStatus status) throws SQLException {
        executeUpdate("""
            INSERT INTO app_role_permission (
                id, role_id, permission_id, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, ?, 'sys_user', 9001, 'sys_user', 9001)
            """, nextAssociationId.getAndIncrement(), roleId, permissionId, status.getValue());
    }

    void insertSystemRolePermission(long userId, String permissionCode) throws SQLException {
        long roleId = 800_000_000_000_000_000L + userId;
        long menuId = 810_000_000_000_000_000L + userId;
        executeUpdate("""
            INSERT INTO sys_role (
                role_id, role_name, role_key, role_sort, data_scope,
                menu_check_strictly, dept_check_strictly, status, del_flag, create_time
            ) VALUES (?, '创作端同名运营角色', 'aivideo-test', 1, '1', 1, 1, '0', '0', NOW())
            """, roleId);
        executeUpdate("""
            INSERT INTO sys_menu (
                menu_id, menu_name, parent_id, order_num, path, is_frame, is_cache,
                menu_type, visible, status, perms, icon, create_time
            ) VALUES (?, '创作端同名运营权限', 0, 1, 'aivideo-test', 'N', 'Y',
                'F', '0', '0', ?, '#', NOW())
            """, menuId, permissionCode);
        executeUpdate("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        executeUpdate("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
    }

    void executeUpdate(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = inspectionConnection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    long queryLong(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = inspectionConnection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("查询应返回一行：%s", sql).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    String queryString(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = inspectionConnection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("查询应返回一行：%s", sql).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    @Override
    public void close() throws Exception {
        try {
            applicationContext.close();
        } finally {
            inspectionConnection.close();
        }
    }

    static Path locateApiRoot() {
        List<Path> starts = new ArrayList<>();
        String mavenProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenProjectDirectory != null && !mavenProjectDirectory.isBlank()) {
            starts.add(Path.of(mavenProjectDirectory));
        }
        starts.add(Path.of(System.getProperty("user.dir")));

        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("无法定位包含 ../docs/sql/ry_vue.sql 的 ai-video-api 根目录");
    }

    private static void reloadDatabase(Connection connection) throws SQLException, IOException {
        Path apiRoot = locateApiRoot();
        executeSqlScript(connection, apiRoot.resolve("../docs/sql/ry_vue.sql"));
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
    }

    private static void closeAfterCreateFailure(Connection inspectionConnection,
                                                AnnotationConfigApplicationContext applicationContext,
                                                Throwable failure) {
        if (applicationContext != null) {
            try {
                applicationContext.close();
            } catch (RuntimeException closeException) {
                failure.addSuppressed(closeException);
            }
        }
        try {
            inspectionConnection.close();
        } catch (SQLException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static void executeSqlScript(Connection connection, Path script) throws SQLException, IOException {
        if (Files.notExists(script)) {
            throw new NoSuchFileException(script.toString());
        }
        ScriptUtils.executeSqlScript(
            connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8)
        );
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }
}

/**
 * 为集成测试构建最小 Spring 事务切片，确保服务方法通过事务代理执行。
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement(proxyTargetClass = true)
@MapperScan(basePackageClasses = AppUserMapper.class)
class IdentityTransactionConfiguration {

    /**
     * 创建测试专用数据源。
     *
     * @param jdbcUrl MySQL 连接地址
     * @param username 数据库用户名
     * @param password 数据库密码
     * @return 测试数据源
     */
    @Bean
    DataSource dataSource(@Value("${identity.jdbc-url}") String jdbcUrl,
                          @Value("${identity.username}") String username,
                          @Value("${identity.password}") String password) {
        return new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
            "com.mysql.cj.jdbc.Driver", jdbcUrl, username, password);
    }

    /**
     * 创建 Mapper 使用的 MyBatis 会话工厂。
     *
     * @param dataSource 测试数据源
     * @return MyBatis 会话工厂
     * @throws Exception 会话工厂初始化异常
     */
    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    /**
     * 创建事务管理器。
     *
     * @param dataSource 测试数据源
     * @return JDBC 事务管理器
     */
    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * 创建可按需失败的审计服务，以验证注册原子性。
     *
     * @param mapper 审计 Mapper
     * @return 可失败审计服务
     */
    @Bean
    FailingAuditService securityAuditService(AppSecurityAuditMapper mapper) {
        return new FailingAuditService(new AppSecurityAuditServiceImpl(mapper));
    }

    @Bean
    TestAppIdentityOperationAuthorizer appIdentityOperationAuthorizationPort() {
        return new TestAppIdentityOperationAuthorizer();
    }

    @Bean
    TestAppSelfRegistrationVerifier appSelfRegistrationVerificationPort() {
        return new TestAppSelfRegistrationVerifier();
    }

    @Bean
    AppIdentityOperationAuthorizer appIdentityOperationAuthorizer(
        ObjectProvider<IAppIdentityOperationAuthorizationService> authorizationPorts) {
        return new AppIdentityOperationAuthorizer(authorizationPorts);
    }

    @Bean
    AppSelfRegistrationVerifier appSelfRegistrationVerifier(
        ObjectProvider<IAppSelfRegistrationVerificationService> verificationPorts) {
        return new AppSelfRegistrationVerifier(verificationPorts);
    }

    @Bean
    AppPasswordRecoveryVerifier appPasswordRecoveryVerifier(
        ObjectProvider<IAppPasswordRecoveryVerificationService> verificationPorts) {
        return new AppPasswordRecoveryVerifier(verificationPorts);
    }

    @Bean
    AppPasswordPolicy appPasswordPolicy() {
        return new AppPasswordPolicy();
    }

    @Bean
    TestAppSessionInvalidationEvents appSessionInvalidationEvents() {
        return new TestAppSessionInvalidationEvents();
    }

    /**
     * 创建由 Spring 事务代理增强的身份服务。
     *
     * @param userMapper 创作端用户 Mapper
     * @param socialIdentityMapper 第三方身份 Mapper
     * @param roleMapper 创作端角色 Mapper
     * @param userRoleMapper 创作端用户角色 Mapper
     * @param securityAuditService 审计服务
     * @return 创作端身份服务
     */
    @Bean
    IAppIdentityService appIdentityService(AppUserMapper userMapper,
                                           AppSocialIdentityMapper socialIdentityMapper,
                                           AppRoleMapper roleMapper,
                                           AppUserRoleMapper userRoleMapper,
                                           FailingAuditService securityAuditService,
                                           AppPasswordPolicy passwordPolicy,
                                           AppIdentityOperationAuthorizer operationAuthorizer,
                                           AppSelfRegistrationVerifier selfRegistrationVerifier,
                                           AppPasswordRecoveryVerifier passwordRecoveryVerifier,
                                           ApplicationEventPublisher eventPublisher) {
        return new AppIdentityServiceImpl(
            userMapper,
            socialIdentityMapper,
            roleMapper,
            userRoleMapper,
            securityAuditService,
            passwordPolicy,
            operationAuthorizer,
            selfRegistrationVerifier,
            passwordRecoveryVerifier,
            eventPublisher);
    }

    /**
     * 创建角色权限服务的事务代理。
     *
     * @param userMapper 创作端用户 Mapper
     * @param roleMapper 创作端角色 Mapper
     * @param permissionMapper 创作端权限 Mapper
     * @param userRoleMapper 创作端用户角色 Mapper
     * @param rolePermissionMapper 创作端角色权限 Mapper
     * @param securityAuditService 安全审计服务
     * @param operationAuthorizer 运营端操作授权器
     * @param eventPublisher 领域事件发布器
     * @return 创作端角色权限服务
     */
    @Bean
    IAppPermissionService appPermissionService(AppUserMapper userMapper,
                                              AppRoleMapper roleMapper,
                                              AppPermissionMapper permissionMapper,
                                              AppUserRoleMapper userRoleMapper,
                                              AppRolePermissionMapper rolePermissionMapper,
                                              FailingAuditService securityAuditService,
                                              AppIdentityOperationAuthorizer operationAuthorizer,
                                              ApplicationEventPublisher eventPublisher) {
        return new AppPermissionServiceImpl(
            userMapper,
            roleMapper,
            permissionMapper,
            userRoleMapper,
            rolePermissionMapper,
            securityAuditService,
            operationAuthorizer,
            eventPublisher);
    }
}

/**
 * 记录提交后创作端会话失效事件的测试替身，不承担真实会话清理。
 */
final class TestAppSessionInvalidationEvents {

    private final List<AppSessionInvalidationEvent> events = new CopyOnWriteArrayList<>();

    /**
     * 仅在外围事务成功提交后记录事件。
     *
     * @param event 创作端会话失效事件
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void record(AppSessionInvalidationEvent event) {
        events.add(event);
    }

    /**
     * 返回已记录事件的不可变快照。
     *
     * @return 已记录的提交后事件
     */
    List<AppSessionInvalidationEvent> events() {
        return List.copyOf(events);
    }
}

/**
 * 集成测试中显式授予运营端身份操作权限的端口替身。
 */
final class TestAppIdentityOperationAuthorizer implements IAppIdentityOperationAuthorizationService {

    private final Set<Long> authorizedActorIds = ConcurrentHashMap.newKeySet();

    void allowAllOperations(long actorId) {
        authorizedActorIds.add(actorId);
    }

    @Override
    public boolean isAuthorized(AppActorContext actor, AppIdentityOperation operation, long targetUserId) {
        return actor != null
            && actor.actorType() == AppActorType.SYS_USER
            && authorizedActorIds.contains(actor.actorId());
    }
}

/**
 * 集成测试中显式放行受信任自注册校验的端口替身。
 */
final class TestAppSelfRegistrationVerifier implements IAppSelfRegistrationVerificationService {

    private final Map<String, RegistrationGrantBinding> grants = new ConcurrentHashMap<>();

    AppSelfRegistrationGrant issue(String grantId, String clientId, RegisterAppUserDTO command) {
        grants.put(grantId, new RegistrationGrantBinding(
            clientId,
            normalizeRequired(command.username()),
            normalizeOptional(command.phone()),
            normalizeOptional(command.email()),
            new AtomicBoolean()));
        return new AppSelfRegistrationGrant(grantId, clientId);
    }

    @Override
    public boolean verifyAndConsume(AppSelfRegistrationVerificationRequest request) {
        RegistrationGrantBinding binding = grants.get(request.registrationGrantId());
        return binding != null
            && binding.clientId().equals(request.clientId())
            && binding.usernameNormalized().equals(request.usernameNormalized())
            && java.util.Objects.equals(binding.phoneNormalized(), request.phoneNormalized())
            && java.util.Objects.equals(binding.emailNormalized(), request.emailNormalized())
            && binding.consumed().compareAndSet(false, true);
    }

    private String normalizeRequired(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private record RegistrationGrantBinding(String clientId, String usernameNormalized,
                                            String phoneNormalized, String emailNormalized,
                                            AtomicBoolean consumed) {
    }
}

/**
 * 在指定下一次写审计时失败，用于验证事务整体回滚。
 */
final class FailingAuditService implements IAppSecurityAuditService {

    private final IAppSecurityAuditService delegate;
    private final AtomicBoolean failNextAppend = new AtomicBoolean();

    FailingAuditService(IAppSecurityAuditService delegate) {
        this.delegate = delegate;
    }

    void failNextAppend() {
        failNextAppend.set(true);
    }

    @Override
    public void append(AppSecurityAuditDTO command) {
        if (failNextAppend.compareAndSet(true, false)) {
            throw new ServiceException("测试要求审计写入失败");
        }
        delegate.append(command);
    }
}
