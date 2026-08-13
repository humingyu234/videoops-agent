package org.dromara.aivideo.identity.service.impl;

import org.dromara.aivideo.identity.event.AppSessionEstablishedEvent;
import org.dromara.aivideo.identity.service.IAppSecurityAuditService;
import org.dromara.aivideo.identity.domain.AppIdentityStatus;
import org.dromara.aivideo.identity.domain.AppSecurityAuditReason;
import org.dromara.aivideo.identity.domain.AppUser;
import org.dromara.aivideo.identity.mapper.AppUserMapper;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppSecurityAuditDTO;
import org.dromara.aivideo.identity.domain.AppSessionInvalidationReason;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppActorContext;
import org.dromara.aivideo.identity.security.AppIdentityOperationAuthorizer;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.identity.security.AppPersonalWorkspaceSnapshotProvider;
import org.dromara.aivideo.identity.security.AppSessionTokenReference;
import org.dromara.aivideo.identity.security.AppSessionTokenRevoker;
import org.dromara.common.core.utils.SpringUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.core.dao.PlusSaTokenDao;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class AppSessionServiceImplTest {

    private static final String SESSION_ID = "9d4cf756-5a8b-424d-86e6-ae4a75ffad8d";
    private static final String ONLINE_SESSION_KEY = "aivideo:app:online:" + SESSION_ID;
    private static final String REDIS_IT_KEY_PREFIX = "aivideo:it:00000000-0000-4000-8000-000000000002:";
    private static AnnotationConfigApplicationContext redisContext;
    private static RedissonClient redisClient;

    @BeforeAll
    static void initializeRedisUtilsWithoutARealRedisServer() {
        redisClient = mock(RedissonClient.class);
        redisContext = new AnnotationConfigApplicationContext();
        redisContext.registerBean(RedissonClient.class, () -> redisClient);
        redisContext.registerBean(SpringUtils.class);
        redisContext.refresh();
        assertThat(RedisUtils.getClient()).isSameAs(redisClient);
    }

    @AfterAll
    static void closeRedisUtilsContext() {
        redisContext.close();
    }

    @BeforeEach
    void resetRedisClient() {
        reset(redisClient);
    }

    @Test
    void registerOnlineSessionUsesTheShortestCurrentIndexTimeoutInsteadOfTheEventTimeout() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        when(loginHelper.getCurrentSessionIndexTimeout()).thenReturn(45L);
        AppSessionServiceImpl service = service(loginHelper);
        RBucket<Object> bucket = bucket();

        service.registerOnlineSession(establishedEvent(120L));

        verify(bucket).set(any(), eq(Duration.ofSeconds(45L)));
    }

    @Test
    void registerOnlineSessionUsesOptionalPhysicalRedisKeyPrefix() throws Exception {
        String redisKeyPrefix = "aivideo:it:00000000-0000-4000-8000-000000000003:";
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        when(loginHelper.getCurrentSessionIndexTimeout()).thenReturn(45L);
        AppSessionServiceImpl service = new AppSessionServiceImpl(Optional.of(loginHelper), mock(AppUserMapper.class),
            Optional.of(mock(AppPersonalWorkspaceSnapshotProvider.class)), mock(AppSessionTokenRevoker.class),
            mock(IAppSecurityAuditService.class), mock(AppIdentityOperationAuthorizer.class), redisKeyPrefix);
        RBucket<Object> bucket = bucket(redisKeyPrefix + ONLINE_SESSION_KEY);

        service.registerOnlineSession(establishedEvent(120L));

        verify(bucket).set(any(), eq(Duration.ofSeconds(45L)));
    }

    @Test
    void saTokenDaoWritesPhysicalPrefixedKeyButKeepsLogicalKeyAtItsBoundary() {
        String logicalKey = "Authorization:app:token:creator";
        RBucket<Object> bucket = bucket(REDIS_IT_KEY_PREFIX + logicalKey);
        PlusSaTokenDao dao = new PlusSaTokenDao(REDIS_IT_KEY_PREFIX);

        dao.set(logicalKey, "value", 60);

        verify(bucket).set("value", Duration.ofSeconds(60));
    }

    @Test
    void saTokenDaoSearchReadsPhysicalPrefixAndReturnsLogicalKeys() {
        String logicalPrefix = "Authorization:app:token:";
        RKeys keys = mock(RKeys.class);
        when(redisClient.getKeys()).thenReturn(keys);
        when(keys.getKeysStream(any(KeysScanOptions.class)))
            .thenReturn(Stream.of(REDIS_IT_KEY_PREFIX + logicalPrefix + "creator"));
        PlusSaTokenDao dao = new PlusSaTokenDao(REDIS_IT_KEY_PREFIX);

        assertThat(dao.searchData(logicalPrefix, "", 0, 10, false))
            .containsExactly(logicalPrefix + "creator");
    }

    @Test
    void saTokenDaoEmptyPrefixPreservesExistingPhysicalKey() {
        String logicalKey = "Authorization:login:token:operator";
        RBucket<Object> bucket = bucket(logicalKey);
        PlusSaTokenDao dao = new PlusSaTokenDao();

        dao.set(logicalKey, "value", 60);

        verify(bucket).set("value", Duration.ofSeconds(60));
    }

    @Test
    void doesNotCreateAnUnboundedOnlineSessionIndexWhenTheCurrentIndexTimeoutIsNotPositive() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionServiceImpl service = service(loginHelper);

        assertNoOnlineIndexWrite(service, loginHelper, 0L);
        assertNoOnlineIndexWrite(service, loginHelper, -2L);
    }

    @Test
    void touchCurrentSessionUsesAnAtomicExistingOnlyWriteSoAConcurrentRevokeCannotRecreateTheIndex() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppLoginUser loginUser = loginUser();
        when(loginHelper.isLogin()).thenReturn(true);
        when(loginHelper.getLoginUser()).thenReturn(loginUser);
        when(loginHelper.getCurrentSessionIndexTimeout()).thenReturn(30L);
        when(loginHelper.getCurrentTokenTimeout()).thenReturn(90L);
        AppSessionServiceImpl service = service(loginHelper);
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(onlineSession());

        service.touchCurrentSession();

        verify(bucket).setIfExists(any(), eq(Duration.ofSeconds(30L)));
        verify(bucket, never()).set(any());
        verify(bucket, never()).set(any(), any(Duration.class));
    }

    @Test
    void findsAnExactOnlineSessionWithoutScanningTheFirstPageOfTheSessionList() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionServiceImpl service = service(loginHelper);
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(onlineSession());

        assertThat(service.findBySessionId(SESSION_ID))
            .hasValueSatisfying(summary -> {
                assertThat(summary.sessionId()).isEqualTo(SESSION_ID);
                assertThat(summary.appUserId()).isEqualTo(1001L);
            });
    }

    @Test
    void defersExactSessionKickoutUntilItsAuditTransactionCommits() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        IAppSecurityAuditService auditService = mock(IAppSecurityAuditService.class);
        AppSessionServiceImpl service = new AppSessionServiceImpl(loginHelper, mock(AppUserMapper.class),
            mock(AppPersonalWorkspaceSnapshotProvider.class), auditService, mock(AppIdentityOperationAuthorizer.class));
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(onlineSession());

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.revokeSession(1001L, SESSION_ID, AppActorContext.appUser(1001L), "local test reason");

            org.mockito.ArgumentCaptor<AppSecurityAuditDTO> auditCommand =
                org.mockito.ArgumentCaptor.forClass(AppSecurityAuditDTO.class);
            verify(auditService).append(auditCommand.capture());
            assertThat(auditCommand.getValue().reason()).isEqualTo(AppSecurityAuditReason.SESSION_REVOCATION.code());
            verify(loginHelper, never()).kickout(any());

            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

            verify(loginHelper).kickout(any());
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }
    }

    @Test
    void invalidatesOnlyOnlineSessionsForTheChangedClient() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppSessionServiceImpl service = service(loginHelper);
        String changedClientId = "creator-web";
        String firstChangedSessionId = "40da4cf7-3e89-4a99-9d92-b2b38c00c001";
        String secondChangedSessionId = "40da4cf7-3e89-4a99-9d92-b2b38c00c002";
        String otherClientSessionId = "40da4cf7-3e89-4a99-9d92-b2b38c00c003";
        String firstChangedKey = onlineSessionKey(firstChangedSessionId);
        String secondChangedKey = onlineSessionKey(secondChangedSessionId);
        String otherClientKey = onlineSessionKey(otherClientSessionId);
        AppSessionTokenReference firstChangedToken = tokenReference("first-client-token-reference");
        AppSessionTokenReference secondChangedToken = tokenReference("second-client-token-reference");
        AppSessionTokenReference otherClientToken = tokenReference("other-client-token-reference");
        RBucket<Object> firstChangedBucket = bucket(firstChangedKey);
        RBucket<Object> secondChangedBucket = bucket(secondChangedKey);
        RBucket<Object> otherClientBucket = bucket(otherClientKey);
        RKeys keys = mock(RKeys.class);
        when(redisClient.getKeys()).thenReturn(keys);
        when(keys.getKeysStream(any(KeysScanOptions.class)))
            .thenReturn(Stream.of(firstChangedKey, secondChangedKey, otherClientKey));
        when(firstChangedBucket.get()).thenReturn(
            onlineSession(firstChangedSessionId, changedClientId, firstChangedToken));
        when(secondChangedBucket.get()).thenReturn(
            onlineSession(secondChangedSessionId, changedClientId, secondChangedToken));
        when(otherClientBucket.get()).thenReturn(
            onlineSession(otherClientSessionId, "creator-mobile", otherClientToken));

        service.invalidateClientSessions(changedClientId, AppSessionInvalidationReason.CLIENT_CHANGED);

        verify(loginHelper).kickout(firstChangedToken);
        verify(loginHelper).kickout(secondChangedToken);
        verify(loginHelper, never()).kickout(otherClientToken);
        verify(firstChangedBucket).delete();
        verify(secondChangedBucket).delete();
        verify(otherClientBucket, never()).delete();
    }

    @Test
    void replaceWorkspaceUsesAnAtomicExistingOnlyWriteSoAConcurrentRevokeCannotRecreateTheIndex() throws Exception {
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppUserMapper userMapper = mock(AppUserMapper.class);
        AppPersonalWorkspaceSnapshotProvider workspaceProvider = mock(AppPersonalWorkspaceSnapshotProvider.class);
        AppWorkspaceSessionSnapshotDTO workspace = workspace();
        AppLoginUser loginUser = loginUser();
        AppUser user = activeUser();
        when(loginHelper.getLoginUser()).thenReturn(loginUser);
        when(loginHelper.getCurrentSessionIndexTimeout()).thenReturn(30L);
        when(loginHelper.getCurrentTokenTimeout()).thenReturn(90L);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(workspaceProvider.personalWorkspace(user)).thenReturn(workspace);
        AppSessionServiceImpl service = new AppSessionServiceImpl(loginHelper, userMapper, workspaceProvider);
        RBucket<Object> bucket = bucket();
        when(bucket.get()).thenReturn(onlineSession());

        service.replaceWorkspace(workspace);

        verify(bucket).setIfExists(any(), eq(Duration.ofSeconds(30L)));
        verify(bucket, never()).set(any());
        verify(bucket, never()).set(any(), any(Duration.class));
    }

    private static void assertNoOnlineIndexWrite(AppSessionServiceImpl service, AppLoginHelper loginHelper,
                                                  long currentIndexTimeout) throws Exception {
        when(loginHelper.getCurrentSessionIndexTimeout()).thenReturn(currentIndexTimeout);
        RBucket<Object> bucket = bucket();

        service.registerOnlineSession(establishedEvent(120L));

        verifyNoInteractions(redisClient, bucket);
    }

    private static AppSessionServiceImpl service(AppLoginHelper loginHelper) {
        return new AppSessionServiceImpl(loginHelper, mock(AppUserMapper.class),
            mock(AppPersonalWorkspaceSnapshotProvider.class));
    }

    @SuppressWarnings("unchecked")
    private static RBucket<Object> bucket() {
        return bucket(ONLINE_SESSION_KEY);
    }

    @SuppressWarnings("unchecked")
    private static RBucket<Object> bucket(String key) {
        RBucket<Object> bucket = mock(RBucket.class);
        when(redisClient.getBucket(key)).thenReturn(bucket);
        return bucket;
    }

    private static AppSessionEstablishedEvent establishedEvent(long eventTimeout) throws Exception {
        return new AppSessionEstablishedEvent(loginUser(), "web", tokenReference(), eventTimeout);
    }

    private static AppLoginUser loginUser() {
        return new AppLoginUser(principal(), SESSION_ID);
    }

    private static AppPrincipalSnapshotDTO principal() {
        return new AppPrincipalSnapshotDTO(1001L, "creator", "creator-web", 2L, 3L, 4L, 7L, workspace());
    }

    private static AppWorkspaceSessionSnapshotDTO workspace() {
        return new AppWorkspaceSessionSnapshotDTO("workspace-key", "personal", 2001L, "app_user", 1001L,
            "personal", 1001L, "personal_creator", java.util.Set.of("creation:script:read"), 3L, null);
    }

    private static AppUser activeUser() {
        AppUser user = new AppUser();
        user.setUserId(1001L);
        user.setStatus(AppIdentityStatus.ACTIVE);
        user.setDelFlag("0");
        return user;
    }

    private static Object onlineSession() throws Exception {
        return onlineSession(SESSION_ID, "creator-web", tokenReference());
    }

    private static Object onlineSession(String sessionId, String clientId, AppSessionTokenReference tokenReference)
        throws Exception {
        Class<?> type = Class.forName(AppSessionServiceImpl.class.getName() + "$AppOnlineSession");
        Constructor<?> constructor = type.getDeclaredConstructor(
            String.class, Long.class, String.class, String.class, String.class, Long.class,
            AppSessionTokenReference.class, LocalDateTime.class);
        constructor.setAccessible(true);
        return constructor.newInstance(sessionId, 1001L, clientId, "web", "personal", 1001L,
            tokenReference, LocalDateTime.now().minusMinutes(1));
    }

    private static AppSessionTokenReference tokenReference() throws Exception {
        return tokenReference("test-only-opaque-token-reference");
    }

    private static AppSessionTokenReference tokenReference(String opaqueReference) throws Exception {
        Constructor<AppSessionTokenReference> constructor = AppSessionTokenReference.class
            .getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        return constructor.newInstance(opaqueReference);
    }

    private static String onlineSessionKey(String sessionId) {
        return "aivideo:app:online:" + sessionId;
    }
}
