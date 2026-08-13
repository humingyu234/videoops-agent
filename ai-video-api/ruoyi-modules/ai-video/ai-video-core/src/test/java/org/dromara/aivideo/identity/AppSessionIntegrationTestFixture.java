package org.dromara.aivideo.identity;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.mock.SaTokenContextMockUtil;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.github.benmanes.caffeine.cache.Cache;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.dromara.aivideo.identity.service.IAppPermissionService;
import org.dromara.aivideo.identity.service.IAppIdentityService;
import org.dromara.aivideo.identity.service.IAppSessionService;
import org.dromara.aivideo.identity.service.impl.AppSessionServiceImpl;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppAuthClientMapper;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContext;
import org.dromara.aivideo.identity.security.AppAuditRequestContextHolder;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppSaTokenProperties;
import org.dromara.aivideo.identity.security.AppSessionRevisionGuard;
import org.dromara.aivideo.identity.security.AppStpLogicRegistrar;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.satoken.core.dao.PlusSaTokenDao;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.CompositeCodec;
import org.redisson.codec.TypedJsonJackson3Codec;
import org.redisson.config.Config;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Task6 Redis 与 MySQL 联测共享夹具。
 *
 * <p>夹具只使用经校验的本机专用数据库和 Redis 逻辑库。</p>
 */
final class AppSessionIntegrationTestFixture implements AutoCloseable {

    private static final String ONLINE_SESSION_PATTERN = "aivideo:app:online:*";
    private static final String SA_TOKEN_PATTERN = "Authorization:*";
    private static final String INITIAL_PASSWORD = "Session#Pass123";

    private final AnnotationConfigApplicationContext applicationContext;
    private final Connection connection;
    private final SaTokenDao previousSaTokenDao;
    private final StpLogic defaultLoginLogic;
    private final SaTokenConfig previousDefaultLoginConfig;
    private final AppLoginHelper loginHelper;
    private final IAppSessionService sessionService;
    private final AppSessionRevisionGuard revisionGuard;
    private final IAppPermissionService permissionService;
    private final IAppIdentityService identityService;
    private final AppUserMapper userMapper;
    private final AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider;
    private final TestAppIdentityOperationAuthorizer operationAuthorizer;
    private final TransactionTemplate transactionTemplate;

    private AppSessionIntegrationTestFixture(AnnotationConfigApplicationContext applicationContext,
                                             Connection connection,
                                             SaTokenDao previousSaTokenDao,
                                             StpLogic defaultLoginLogic,
                                             SaTokenConfig previousDefaultLoginConfig) {
        this.applicationContext = applicationContext;
        this.connection = connection;
        this.previousSaTokenDao = previousSaTokenDao;
        this.defaultLoginLogic = defaultLoginLogic;
        this.previousDefaultLoginConfig = previousDefaultLoginConfig;
        this.loginHelper = applicationContext.getBean(AppLoginHelper.class);
        this.sessionService = applicationContext.getBean(IAppSessionService.class);
        this.revisionGuard = applicationContext.getBean(AppSessionRevisionGuard.class);
        this.permissionService = applicationContext.getBean(IAppPermissionService.class);
        this.identityService = applicationContext.getBean(IAppIdentityService.class);
        this.userMapper = applicationContext.getBean(AppUserMapper.class);
        this.personalWorkspaceSnapshotProvider = applicationContext.getBean(AppPersonalWorkspaceSnapshotProvider.class);
        this.operationAuthorizer = applicationContext.getBean(TestAppIdentityOperationAuthorizer.class);
        this.transactionTemplate = new TransactionTemplate(applicationContext.getBean(PlatformTransactionManager.class));
    }

    /**
     * 创建可同时访问真实 MySQL Mapper、Redis 在线索引和 app Sa-Token 命名空间的测试夹具。
     *
     * @return 已清空状态的联测夹具
     * @throws Exception 初始化数据库或 Spring 上下文失败
     */
    static AppSessionIntegrationTestFixture create() throws Exception {
        LocalIntegrationEnvironment environment = AppSessionIntegrationRuntime.environment();
        RedissonClient redissonClient = AppSessionIntegrationRuntime.redissonClient();

        // 复用已验证的身份基线建表流程，随后在独立上下文中增加 Task6 的 Redis 会话组件。
        try (AppIdentityTestFixture ignored = AppIdentityTestFixture.create(environment)) {
            // 建表夹具关闭后再创建包含 Redis 会话监听器的上下文。
        }
        clearFixtureState();

        AnnotationConfigApplicationContext context = null;
        Connection connection = null;
        SaTokenDao previousSaTokenDao = SaManager.getSaTokenDao();
        StpLogic defaultLoginLogic = null;
        SaTokenConfig previousDefaultLoginConfig = null;
        try {
            SaManager.removeStpLogic("app");
            SaTokenContextMockUtil.setMockContext();

            context = new AnnotationConfigApplicationContext();
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("app-session-it", Map.of(
                "identity.jdbc-url", environment.jdbcUrl(),
                "identity.username", environment.mysqlUsername(),
                "identity.password", environment.mysqlPassword(),
                "app.security.token.enabled", "true",
                "sa-token.redis-key-prefix", environment.redisKeyPrefix()
            )));
            context.registerBean(RedissonClient.class, () -> redissonClient,
                beanDefinition -> beanDefinition.setDestroyMethodName(""));
            context.register(IdentityTransactionConfiguration.class, AppSessionIntegrationConfiguration.class);
            context.refresh();

            // PlusSaTokenDao 必须在 SpringUtils 已绑定 RedissonClient 的上下文刷新后才可创建。
            SaManager.setSaTokenDao(new PlusSaTokenDao(environment.redisKeyPrefix()));
            defaultLoginLogic = StpUtil.getStpLogic();
            previousDefaultLoginConfig = defaultLoginLogic.getConfig();
            defaultLoginLogic.setConfig(defaultLoginConfig());
            connection = environment.openMySqlConnection();
            return new AppSessionIntegrationTestFixture(context, connection, previousSaTokenDao,
                defaultLoginLogic, previousDefaultLoginConfig);
        } catch (Exception | Error exception) {
            closeAfterCreateFailure(context, connection, previousSaTokenDao, defaultLoginLogic,
                previousDefaultLoginConfig, exception);
            throw exception;
        }
    }

    /**
     * 新建可由修订守卫校验的创作端用户和认证客户端。
     *
     * @param userId 创作端用户编号
     * @throws SQLException 写入失败
     */
    void insertActiveUserAndClient(long userId) throws SQLException {
        executeUpdate("""
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, ?, ?, ?, 'active', 1, 1, 1,
                'sys_user', 9100, 'sys_user', 9100)
            """, userId, username(userId), username(userId), BCrypt.hashpw(INITIAL_PASSWORD),
            80_000L + userId, "会话用户" + userId);

        executeUpdate("""
            INSERT INTO app_auth_client (
                id, client_id, client_key, grant_types, access_paths, token_timeout, active_timeout,
                client_revision, status, created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, 'password', '/studio', 3600, 1800, 1, 'active',
                'sys_user', 9100, 'sys_user', 9100)
            """, 90_000L + userId, clientId(userId), "session-client-key-" + userId);
    }

    /**
     * 使用真实 app 登录入口建立会话，事件监听器会同步写入 Redis 在线索引。
     *
     * @param userId 创作端用户编号
     * @return app 令牌会话中的登录用户
     * @throws SQLException 查询修订号失败
     */
    AppLoginUser loginApp(long userId) throws SQLException {
        return loginHelper.login(principal(userId), "desktop");
    }

    /**
     * 仅为 P0-A 会话索引集成测试模拟已由后续 P0-B 授权服务验证过的组织工作区会话。
     *
     * <p>此方法绝不代表 P0-A 可直接切换组织工作区；生产切换必须由 P0-B 基于成员事实源重新解析。</p>
     *
     * @param userId 创作端用户编号
     * @param trustedWorkspace 已验证的工作区快照
     * @return 写入真实 app 会话和在线索引的登录用户
     * @throws SQLException 读取修订号失败
     */
    AppLoginUser loginAppWithTrustedWorkspaceForTest(long userId,
                                                      AppWorkspaceSessionSnapshotDTO trustedWorkspace) throws SQLException {
        AppPrincipalSnapshotDTO personalPrincipal = principal(userId);
        AppPrincipalSnapshotDTO trustedPrincipal = new AppPrincipalSnapshotDTO(
            personalPrincipal.appUserId(),
            personalPrincipal.username(),
            personalPrincipal.clientId(),
            personalPrincipal.credentialRevision(),
            personalPrincipal.identityRevision(),
            personalPrincipal.permissionRevision(),
            personalPrincipal.clientRevision(),
            trustedWorkspace);
        return loginHelper.login(trustedPrincipal, "desktop");
    }

    /**
     * 在默认 login 命名空间创建固定原文令牌，供命名空间隔离断言使用。
     *
     * @param systemUserId 运营端用户编号
     * @return 默认 login 命名空间令牌原文
     */
    String loginSystem(long systemUserId) {
        return loginSystem(systemUserId, "system-it-" + UUID.randomUUID());
    }

    /**
     * 在默认 login 命名空间创建指定原文令牌，仅供命名空间隔离联测构造同原文 token 场景。
     *
     * @param systemUserId 运营端用户编号
     * @param tokenValue 受控测试令牌原文
     * @return 默认 login 命名空间令牌原文
     */
    String loginSystem(long systemUserId, String tokenValue) {
        defaultLoginLogic.login(systemUserId,
            defaultLoginLogic.createSaLoginParameter().setToken(tokenValue));
        return tokenValue;
    }

    /**
     * 返回 session 服务实现，供联测读取在线会话索引。
     *
     * @return 创作端会话服务
     */
    IAppSessionService sessionService() {
        return sessionService;
    }

    /**
     * 返回真实 MyBatis 修订守卫。
     *
     * @return 创作端会话修订守卫
     */
    AppSessionRevisionGuard revisionGuard() {
        return revisionGuard;
    }

    /**
     * 返回创作端身份服务，供会话失效联调测试触发真实的事务事件。
     *
     * @return 创作端身份服务
     */
    IAppIdentityService identityService() {
        return identityService;
    }

    /**
     * 返回会话测试用户的初始明文密码。
     *
     * @return 初始明文密码
     */
    String initialPassword() {
        return INITIAL_PASSWORD;
    }

    /**
     * 授权测试中的运营端操作者执行创作端身份变更。
     *
     * @param actorId 运营端操作者编号
     */
    void authorizeSystemActor(long actorId) {
        operationAuthorizer.allowAllOperations(actorId);
    }

    /**
     * 在可信的非 HTTP 审计上下文中执行动作。
     *
     * @param action 待执行动作
     */
    void withNonHttpAudit(Runnable action) {
        try (AppAuditRequestContextHolder.Scope ignored = AppAuditRequestContextHolder.bindTrusted(
            AppAuditRequestContext.nonHttp())) {
            action.run();
        }
    }

    /**
     * 在外层事务中执行动作并显式回滚。
     *
     * @param action 待回滚动作
     */
    void withRollbackOnlyTransaction(Runnable action) {
        transactionTemplate.executeWithoutResult(status -> {
            action.run();
            status.setRollbackOnly();
        });
    }

    /**
     * 查询指定用户与第三方提供方对应的身份编号。
     *
     * @param userId 创作端用户编号
     * @param provider 第三方提供方
     * @return 第三方身份编号
     * @throws SQLException 查询失败
     */
    long socialIdentityId(long userId, String provider) throws SQLException {
        return queryLong("""
            SELECT social_identity_id
            FROM app_social_identity
            WHERE user_id = ? AND provider = ?
            """, userId, provider);
    }

    /**
     * 返回当前 app 登录助手。
     *
     * @return 创作端登录助手
     */
    AppLoginHelper loginHelper() {
        return loginHelper;
    }

    /**
     * 返回 Redis 中指定模式的原始键名，用于验证命名空间而非暴露接口 DTO。
     *
     * @param pattern Redis 键匹配模式
     * @return 匹配到的键名快照
     */
    List<String> redisKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        for (String key : AppSessionIntegrationRuntime.redissonClient().getKeys()
            .getKeysByPattern(pattern)) {
            keys.add(key);
        }
        return keys;
    }

    /**
     * 直接删除 Redis 键，模拟另一应用节点完成写入或撤销而不触发本 JVM 的 Sa-Token 本地缓存失效。
     *
     * @param key 要删除的完整 Redis 键
     */
    void deleteRedisKeyAsAnotherNode(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis 键不能为空");
        }
        LocalIntegrationEnvironment environment = AppSessionIntegrationRuntime.environment();
        RedissonClient rawClient = environment.openRedisClient();
        try {
            rawClient.getKeys().delete(environment.redisKeyPrefix() + key);
        } finally {
            rawClient.shutdown();
        }
    }

    /**
     * 使指定用户的创作端会话失效。
     *
     * @param userId 创作端用户编号
     */
    void invalidateUserSessions(long userId) {
        sessionService.invalidateUserSessions(userId,
            org.dromara.aivideo.identity.domain.AppSessionInvalidationReason.ADMIN_KICKOUT);
    }

    /**
     * 写入真实角色、权限及用户角色关系，为角色权限变更事件准备事实数据。
     *
     * @param userId 创作端用户编号
     * @throws SQLException 写入失败
     */
    void prepareRolePermissionMutation(long userId) throws SQLException {
        executeUpdate("""
            INSERT INTO app_role (
                role_id, role_code, role_name, scope_type, built_in, role_revision, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, 'personal', 0, 1, 'active', 'sys_user', 9100, 'sys_user', 9100)
            """, roleId(userId), "session-role-" + userId, "会话角色" + userId);
        executeUpdate("""
            INSERT INTO app_permission (
                permission_id, permission_code, permission_name, resource_type, action, permission_revision, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, 'session', 'query', 1, 'active', 'sys_user', 9100, 'sys_user', 9100)
            """, permissionId(userId), "aivideo:session:" + userId, "会话权限" + userId);
        executeUpdate("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, ?, 'active', NULL, NULL, 'sys_user', 9100, 'sys_user', 9100)
            """, userRoleId(userId), userId, roleId(userId));
    }

    /**
     * 提交真实角色权限替换，以触发 Task5 的 AFTER_COMMIT 会话失效事件。
     *
     * @param userId 创作端用户编号
     */
    void replaceRolePermissionsAndCommit(long userId) {
        operationAuthorizer.allowAllOperations(9100L);
        withNonHttpAudit(() -> permissionService.replaceRolePermissions(
            roleId(userId), 1L, Set.of(permissionId(userId)), AppActorContext.sysUser(9100L)));
    }

    /**
     * 在外层事务内替换真实角色权限后标记回滚；Task5 的 AFTER_COMMIT 监听器不得撤销在线会话。
     *
     * @param userId 创作端用户编号
     */
    void replaceRolePermissionsAndRollback(long userId) {
        operationAuthorizer.allowAllOperations(9100L);
        transactionTemplate.executeWithoutResult(status -> {
            withNonHttpAudit(() -> permissionService.replaceRolePermissions(
                roleId(userId), 1L, Set.of(permissionId(userId)), AppActorContext.sysUser(9100L)));
            status.setRollbackOnly();
        });
    }

    /**
     * 使一个用户或客户端修订号递增，用于验证修订守卫。
     *
     * @param userId 创作端用户编号
     * @param revisionColumn 允许的修订字段
     * @throws SQLException 写入失败
     */
    void incrementRevision(long userId, String revisionColumn) throws SQLException {
        String sql = switch (revisionColumn) {
            case "credential_revision", "identity_revision", "permission_revision" ->
                "UPDATE app_user SET " + revisionColumn + " = " + revisionColumn + " + 1 WHERE user_id = ?";
            case "client_revision" ->
                "UPDATE app_auth_client SET client_revision = client_revision + 1 WHERE client_id = ?";
            default -> throw new IllegalArgumentException("不允许的会话修订字段：" + revisionColumn);
        };
        if ("client_revision".equals(revisionColumn)) {
            executeUpdate(sql, clientId(userId));
        } else {
            executeUpdate(sql, userId);
        }
    }

    /**
     * 返回 app 专属在线索引键模式。
     *
     * @return Redis 在线索引匹配模式
     */
    static String onlineSessionPattern() {
        return ONLINE_SESSION_PATTERN;
    }

    /**
     * 返回所有 Sa-Token 原始键匹配模式。
     *
     * @return Sa-Token Redis 键匹配模式
     */
    static String saTokenPattern() {
        return SA_TOKEN_PATTERN;
    }

    @Override
    public void close() throws Exception {
        try {
            clearFixtureState();
        } finally {
            try {
                applicationContext.close();
            } finally {
                try {
                    defaultLoginLogic.setConfig(previousDefaultLoginConfig);
                    SaManager.removeStpLogic("app");
                    SaManager.setSaTokenDao(previousSaTokenDao);
                } finally {
                    SaTokenContextMockUtil.clearContext();
                    connection.close();
                }
            }
        }
    }

    private AppPrincipalSnapshotDTO principal(long userId) throws SQLException {
        long credentialRevision = queryLong("SELECT credential_revision FROM app_user WHERE user_id = ?", userId);
        long identityRevision = queryLong("SELECT identity_revision FROM app_user WHERE user_id = ?", userId);
        long permissionRevision = queryLong("SELECT permission_revision FROM app_user WHERE user_id = ?", userId);
        long clientRevision = queryLong("SELECT client_revision FROM app_auth_client WHERE client_id = ?", clientId(userId));
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new SQLException("会话联测未找到创作端用户：" + userId);
        }
        AppWorkspaceSessionSnapshotDTO workspace = personalWorkspaceSnapshotProvider.personalWorkspace(user);
        return new AppPrincipalSnapshotDTO(userId, username(userId), clientId(userId), credentialRevision,
            identityRevision, permissionRevision, clientRevision, workspace);
    }

    private void executeUpdate(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            statement.executeUpdate();
        }
    }

    private long queryLong(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("会话联测查询未返回数据：" + sql);
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    /**
     * 清理 Task6 联测的 Redis 与 PlusSaTokenDao 进程内一级缓存。
     * <p>
     * PlusSaTokenDao 当前未提供测试可调用的缓存清理 API；该缓存为私有静态字段，若只删 Redis，
     * 同一 JVM 内重跑固定用户编号的用例可能读到五秒内的旧会话。因此测试夹具在唯一受控位置
     * 通过反射执行 {@code invalidateAll}，不向生产代码增加测试接口。
     *
     */
    private static void clearFixtureState() {
        AppSessionIntegrationRuntime.environment().clearCurrentRunRedisKeys();
        clearPlusSaTokenDaoCaffeine();
    }

    /**
     * 清理 PlusSaTokenDao 的私有静态 Caffeine 缓存，确保每次夹具创建都从 Redis 与数据库事实源开始。
     */
    @SuppressWarnings("unchecked")
    private static void clearPlusSaTokenDaoCaffeine() {
        try {
            Field caffeineField = PlusSaTokenDao.class.getDeclaredField("CAFFEINE");
            caffeineField.setAccessible(true);
            Cache<String, Object> caffeine = (Cache<String, Object>) caffeineField.get(null);
            caffeine.invalidateAll();
            caffeine.cleanUp();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法清理 PlusSaTokenDao 测试缓存", exception);
        }
    }

    private static SaTokenConfig defaultLoginConfig() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setIsReadHeader(true);
        config.setIsReadBody(false);
        config.setIsReadCookie(false);
        config.setIsConcurrent(true);
        config.setIsShare(false);
        config.setDynamicActiveTimeout(false);
        return config;
    }

    private static String username(long userId) {
        return "session-user-" + userId;
    }

    private static String clientId(long userId) {
        return "session-client-" + userId;
    }

    private static long roleId(long userId) {
        return 7_000_000L + userId;
    }

    private static long permissionId(long userId) {
        return 8_000_000L + userId;
    }

    private static long userRoleId(long userId) {
        return 9_000_000L + userId;
    }

    private static void closeAfterCreateFailure(AnnotationConfigApplicationContext context,
                                                Connection connection,
                                                SaTokenDao previousSaTokenDao,
                                                StpLogic defaultLoginLogic,
                                                SaTokenConfig previousDefaultLoginConfig,
                                                Throwable failure) {
        try {
            clearFixtureState();
        } catch (RuntimeException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
        if (context != null) {
            try {
                context.close();
            } catch (RuntimeException closeException) {
                failure.addSuppressed(closeException);
            }
        }
        if (defaultLoginLogic != null) {
            defaultLoginLogic.setConfig(previousDefaultLoginConfig);
        }
        SaManager.removeStpLogic("app");
        SaManager.setSaTokenDao(previousSaTokenDao);
        SaTokenContextMockUtil.clearContext();
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                failure.addSuppressed(closeException);
            }
        }
    }
}

/**
 * 在多个 Task6 IT 之间共享同一套经校验的本机 MySQL/Redis 测试连接配置。
 */
final class AppSessionIntegrationRuntime {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static RedissonClient redissonClient;

    private AppSessionIntegrationRuntime() {
    }

    /**
     * 返回已校验的本机集成测试环境。
     */
    static LocalIntegrationEnvironment environment() {
        return ENV;
    }

    /**
     * 返回使用正式 Redis JSON 编解码策略的共享 Redisson 客户端。
     *
     * @return Redis 测试客户端
     */
    static synchronized RedissonClient redissonClient() {
        if (redissonClient == null) {
            redissonClient = Redisson.create(redissonConfig());
        }
        return redissonClient;
    }

    private static Config redissonConfig() {
        SimpleModule simpleModule = new SimpleModule();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        simpleModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        simpleModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        JsonMapper jsonMapper = JsonMapper.builder()
            .addModules(simpleModule)
            .defaultTimeZone(TimeZone.getDefault())
            .changeDefaultVisibility(visibilityChecker -> visibilityChecker
                .withVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY))
            .activateDefaultTyping(BasicPolymorphicTypeValidator.builder()
                .allowIfSubType((context, type) -> true)
                .build(), DefaultTyping.NON_FINAL)
            .build();
        TypedJsonJackson3Codec jsonCodec = new TypedJsonJackson3Codec(Object.class, jsonMapper);
        Config config = ENV.newNamespacedRedisConfig();
        config.setCodec(new CompositeCodec(StringCodec.INSTANCE, jsonCodec, jsonCodec));
        return config;
    }
}

/**
 * 为 Task6 联测装配真实 MyBatis 事务基础上的 app 会话组件。
 */
@Configuration(proxyBeanMethods = false)
class AppSessionIntegrationConfiguration {

    /**
     * 提供不依赖环境变量的测试专用 app 令牌密钥。
     *
     * @return 已启用的 app 令牌配置
     */
    @Bean
    AppSaTokenProperties appSaTokenProperties() {
        AppSaTokenProperties properties = new AppSaTokenProperties();
        properties.setEnabled(true);
        properties.setJwtSecret("task6-session-it-jwt-secret-at-least-32-bytes");
        properties.setWorkspaceKeySecret("task6-session-it-workspace-key-secret-at-least-32-bytes");
        return properties;
    }

    /**
     * 注册独立 app Sa-Token 登录逻辑。
     *
     * @param properties app 令牌配置
     * @return app 登录逻辑注册器
     */
    @Bean
    AppStpLogicRegistrar appStpLogicRegistrar(AppSaTokenProperties properties) {
        return new AppStpLogicRegistrar(properties);
    }

    /**
     * 注册 app 登录入口及同步内部事件发布能力。
     *
     * @param registrar app 登录逻辑注册器
     * @param eventPublisher Spring 事件发布器
     * @return app 登录助手
     */
    @Bean
    AppLoginHelper appLoginHelper(AppStpLogicRegistrar registrar,
                                  ApplicationEventPublisher eventPublisher,
                                  AppAuthClientMapper authClientMapper) {
        return new AppLoginHelper(registrar, eventPublisher, authClientMapper);
    }

    /**
     * 注册会话服务，使 app 登录事件同步写入 Redis 在线索引。
     *
     * @param loginHelper app 登录助手
     * @param userMapper app 用户事实源
     * @param personalWorkspaceSnapshotProvider 个人工作区规范快照提供器
     * @return 创作端会话服务
     */
    @Bean
    AppPersonalWorkspaceSnapshotProvider appPersonalWorkspaceSnapshotProvider(IAppPermissionService permissionService,
                                                                                AppSaTokenProperties tokenProperties) {
        return new AppPersonalWorkspaceSnapshotProvider(permissionService, tokenProperties);
    }

    /**
     * 注册会话服务，使 app 登录事件同步写入 Redis 在线索引。
     *
     * @param loginHelper app 登录助手
     * @param userMapper app 用户事实源
     * @param personalWorkspaceSnapshotProvider 个人工作区规范快照提供器
     * @return 创作端会话服务
     */
    @Bean
    IAppSessionService appSessionService(AppLoginHelper loginHelper,
                                         AppUserMapper userMapper,
                                         AppPersonalWorkspaceSnapshotProvider personalWorkspaceSnapshotProvider,
                                         @Value("${sa-token.redis-key-prefix:}") String redisKeyPrefix) {
        return new AppSessionServiceImpl(loginHelper, userMapper, personalWorkspaceSnapshotProvider, redisKeyPrefix);
    }

    /**
     * 注册会话修订守卫并复用真实创作端 Mapper。
     *
     * @param loginHelper app 登录助手
     * @param userMapper 创作端用户 Mapper
     * @param authClientMapper 创作端认证客户端 Mapper
     * @param sessionService 创作端会话服务
     * @return 会话修订守卫
     */
    @Bean
    AppSessionRevisionGuard appSessionRevisionGuard(AppLoginHelper loginHelper,
                                                    org.dromara.aivideo.identity.mapper.AppUserMapper userMapper,
                                                    org.dromara.aivideo.identity.mapper.AppAuthClientMapper authClientMapper,
                                                    IAppSessionService sessionService) {
        return new AppSessionRevisionGuard(loginHelper, userMapper, authClientMapper, sessionService);
    }

    /**
     * 绑定 RedisUtils 所需的 Spring 上下文入口。
     *
     * @return Spring 工具对象
     */
    @Bean
    SpringUtils springUtils() {
        return new SpringUtils();
    }
}
