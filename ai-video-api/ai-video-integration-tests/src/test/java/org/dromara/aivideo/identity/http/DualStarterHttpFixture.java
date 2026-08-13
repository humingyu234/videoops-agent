package org.dromara.aivideo.identity.http;

import cn.hutool.crypto.digest.BCrypt;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.constant.SystemConstants;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双外部启动器 HTTP 集成测试夹具。
 *
 * <p>夹具只在 Failsafe 运行时使用经校验的本机测试环境，并使用 {@code java -jar} 启动两个独立 JVM。
 * 因此测试 JVM 不会加载任一 starter 的 {@code org.dromara.DromaraApplication}。</p>
 */
final class DualStarterHttpFixture implements AutoCloseable {

    private static final long SHARED_NUMERIC_USER_ID = SystemConstants.SUPER_ADMIN_USER_ID;
    private static final long SECONDARY_NUMERIC_USER_ID = 1_761_400_000_000_090_201L;
    private static final long SECONDARY_PERSONAL_TENANT_ID = 1_761_000_000_000_000_102L;
    private static final long SECONDARY_USER_ROLE_ID = 1_761_400_000_000_090_203L;
    private static final long PERSONAL_CREATOR_ROLE_ID = 1_000_101L;
    private static final long TASK_QUERY_BINDING_ID = 1_761_400_000_000_090_101L;
    private static final long TASK_CANCEL_BINDING_ID = 1_761_400_000_000_090_102L;
    private static final String CREATOR_USERNAME = "dual-http-user";
    private static final String CREATOR_PASSWORD = "DualHttp#Pass123";
    private static final String CREATOR_USERNAME_B = "dual-http-user-b";
    private static final String CREATOR_PASSWORD_B = "DualHttp#Pass456";
    private static final String CREATOR_CLIENT_KEY = "http-it-app-client-key";
    private static final String CREATOR_CLIENT_KEY_B = "http-it-app-client-key-b";
    private static final String WRONG_GRANT_CLIENT_KEY = "http-it-wrong-grant-client-key";
    private static final String PATH_RESTRICTED_CLIENT_KEY = "http-it-login-only-client-key";
    private static final String IP_RESTRICTED_CLIENT_KEY = "http-it-ip-restricted-client-key";
    private static final String OPERATING_CLIENT_ID = "e5cd7e4891bf95d1d19206ce24a7b32e";
    private static final String OPERATING_USERNAME = "admin";
    private static final String OPERATING_PASSWORD = "admin123";
    private static final String SYSTEM_JWT_SECRET = "http-it-system-jwt-secret-at-least-32-bytes";
    private static final String APP_JWT_SECRET = "http-it-app-jwt-secret-at-least-32-bytes";
    private static final String WORKSPACE_KEY_SECRET = "http-it-workspace-key-secret-at-least-32-bytes";
    private static final String ACTUATOR_USERNAME = "http-it-actuator";
    private static final String ACTUATOR_PASSWORD = "http-it-actuator-password";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PROCESS_STOP_TIMEOUT = Duration.ofSeconds(10);
    private static final List<Pattern> STARTED_PORT_PATTERNS = List.of(
        Pattern.compile("Tomcat started on port (\\d+)"),
        Pattern.compile("Started .*ServerConnector.*\\{[^{}]*:(\\d+)\\}\\s*$")
    );
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern RESPONSE_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*(\\d+)");
    private static final Set<String> SAFE_FIXTURE_TABLES = Set.of(
        "av_timeline_asset_ref", "av_timeline_write_receipt", "av_timeline_version", "av_timeline_draft",
        "av_ai_task_attempt", "av_ai_task_execution", "av_ai_task", "av_creation_project",
        "av_dh_generation_job"
    );

    private final LocalIntegrationEnvironment environment;
    private final Path creatorJar;
    private final StarterArguments starterArguments;
    private final TimelineRuntime timelineRuntime;
    private ExternalStarterProcess creatorStarter;
    private final ExternalStarterProcess operatingStarter;
    private final String systemToken;
    private final HttpClient httpClient;
    private final Set<String> redisKeysBeforeStart;
    private String lastCreatorToken;

    private DualStarterHttpFixture(LocalIntegrationEnvironment environment,
                                   Path creatorJar,
                                   StarterArguments starterArguments,
                                   TimelineRuntime timelineRuntime,
                                   ExternalStarterProcess creatorStarter,
                                   ExternalStarterProcess operatingStarter,
                                   String systemToken,
                                   Set<String> redisKeysBeforeStart) {
        this.environment = environment;
        this.creatorJar = creatorJar;
        this.starterArguments = starterArguments;
        this.timelineRuntime = timelineRuntime;
        this.creatorStarter = creatorStarter;
        this.operatingStarter = operatingStarter;
        this.systemToken = systemToken;
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
        this.redisKeysBeforeStart = redisKeysBeforeStart;
    }

    /**
     * 初始化完整本机测试库并启动两个外部 starter。
     *
     * @return 已通过健康探针的 HTTP 测试夹具
     * @throws Exception 本机测试环境、数据库初始化或任一外部进程启动失败
     */
    static DualStarterHttpFixture start() throws Exception {
        Path starterJarDirectory = requiredAbsoluteDirectory("it.starter.jar.directory");
        Path apiRoot = requiredAbsoluteDirectory("it.api.root");
        Path creatorJar = requiredRegularFile(starterJarDirectory.resolve("ai-video-user-api.jar"));
        Path operatingJar = requiredRegularFile(starterJarDirectory.resolve("ruoyi-admin.jar"));

        LocalIntegrationEnvironment environment = LocalIntegrationEnvironment.requireFromEnvironment();
        ExternalStarterProcess creatorStarter = null;
        ExternalStarterProcess operatingStarter = null;
        Set<String> redisKeysBeforeStart = null;
        TimelineRuntime timelineRuntime = null;

        try {
            environment.clearCurrentRunRedisKeys();
            redisKeysBeforeStart = environment.snapshotRedisKeys();
            environment.assertRedisBaselineKeysControlled(redisKeysBeforeStart);
            initializeDatabase(environment, apiRoot);

            StarterArguments starterArguments = StarterArguments.from(environment);
            timelineRuntime = TimelineRuntime.create(apiRoot);
            creatorStarter = ExternalStarterProcess.start("creator-starter", creatorJar, starterArguments,
                timelineRuntime.creatorEnvironment());
            operatingStarter = ExternalStarterProcess.start("operating-starter", operatingJar, starterArguments,
                Map.of(
                    "AIVIDEO_TIMELINE_ENABLED", "false",
                    "MANAGEMENT_ENDPOINT_BEANS_ENABLED", "false",
                    "MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health"
                ));
            String systemToken = loginOperating(operatingStarter);
            environment.assertOnlyCurrentRunRedisKeysAdded(redisKeysBeforeStart);

            return new DualStarterHttpFixture(environment, creatorJar, starterArguments, timelineRuntime,
                creatorStarter, operatingStarter,
                systemToken, redisKeysBeforeStart);
        } catch (Exception | Error failure) {
            closeAfterStartFailure(failure, operatingStarter, creatorStarter, environment,
                redisKeysBeforeStart);
            cleanupFixtureIdentityAfterStartFailure(environment, failure);
            closeResource(timelineRuntime, failure);
            throw failure;
        }
    }

    String creatorClientKey() {
        return CREATOR_CLIENT_KEY;
    }

    String creatorClientKeyB() {
        return CREATOR_CLIENT_KEY_B;
    }

    String wrongGrantClientKey() {
        return WRONG_GRANT_CLIENT_KEY;
    }

    String pathRestrictedClientKey() {
        return PATH_RESTRICTED_CLIENT_KEY;
    }

    String ipRestrictedClientKey() {
        return IP_RESTRICTED_CLIENT_KEY;
    }

    String operatingClientId() {
        return OPERATING_CLIENT_ID;
    }

    String sharedNumericUserId() {
        return String.valueOf(SHARED_NUMERIC_USER_ID);
    }

    String systemToken() {
        return systemToken;
    }

    String loginCreator() throws IOException, InterruptedException {
        return loginCreatorAndExtractToken(CREATOR_CLIENT_KEY);
    }

    String loginCreatorB() throws IOException, InterruptedException {
        return loginCreatorAndExtractToken(CREATOR_CLIENT_KEY, CREATOR_USERNAME_B, CREATOR_PASSWORD_B);
    }

    HttpResponse<String> loginCreator(String clientKey) throws IOException, InterruptedException {
        return creatorLoginRequest(clientKey);
    }

    String loginCreatorAndExtractToken(String clientKey) throws IOException, InterruptedException {
        return loginCreatorAndExtractToken(clientKey, CREATOR_USERNAME, CREATOR_PASSWORD);
    }

    private String loginCreatorAndExtractToken(String clientKey, String username, String password)
        throws IOException, InterruptedException {
        HttpResponse<String> response = creatorLoginRequest(clientKey, username, password);
        requireCode(response, 200, "创作端密码登录");

        Matcher matcher = ACCESS_TOKEN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("创作端登录响应未返回 access_token，响应=" + response.body());
        }
        String token = matcher.group(1);
        if (CREATOR_CLIENT_KEY.equals(clientKey) && CREATOR_USERNAME.equals(username)) {
            lastCreatorToken = token;
        }
        return token;
    }

    private HttpResponse<String> creatorLoginRequest(String clientKey) throws IOException, InterruptedException {
        return creatorLoginRequest(clientKey, CREATOR_USERNAME, CREATOR_PASSWORD);
    }

    private HttpResponse<String> creatorLoginRequest(String clientKey, String username, String password)
        throws IOException, InterruptedException {
        return request(
            requireCreatorStarter().endpoint("/api/auth/login"),
            "POST",
            null,
            clientKey,
            "{\"identifier\":\"" + username + "\",\"password\":\"" + password + "\"}",
            false
        );
    }

    HttpResponse<String> creatorGet(String path, String token, String clientKey)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "GET", token, clientKey, null, false);
    }

    HttpResponse<String> creatorGetWithRepeatedAuthorization(String path, String token, String clientKey)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "GET", token, clientKey, null, true);
    }

    HttpResponse<String> creatorGetWithTokenParameter(String path, String token, String clientKey)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path + "?token=outside-header"), "GET", token, clientKey, null, false);
    }

    HttpResponse<String> creatorPost(String path, String token, String body)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "POST", token, CREATOR_CLIENT_KEY, body, false);
    }

    HttpResponse<String> creatorPost(String path, String token, String clientKey, String body)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "POST", token, clientKey, body, false);
    }

    HttpResponse<String> creatorPut(String path, String token, String body)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "PUT", token, CREATOR_CLIENT_KEY, body, false);
    }

    HttpResponse<String> creatorDelete(String path, String token)
        throws IOException, InterruptedException {
        return request(requireCreatorStarter().endpoint(path), "DELETE", token, CREATOR_CLIENT_KEY, null, false);
    }

    HttpResponse<byte[]> creatorGetBytes(String path, String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(requireCreatorStarter().endpoint(path))
            .timeout(HTTP_TIMEOUT)
            .header("Accept", "application/octet-stream")
            .header("clientid", CREATOR_CLIENT_KEY)
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    String creatorActuatorBeans() throws IOException, InterruptedException {
        String credentials = ACTUATOR_USERNAME + ':' + ACTUATOR_PASSWORD;
        HttpRequest request = HttpRequest.newBuilder(requireCreatorStarter().endpoint("/actuator/beans"))
            .timeout(HTTP_TIMEOUT)
            .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8)))
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("用户端 beans 端点不可用，HTTP=" + response.statusCode());
        }
        return response.body();
    }

    Connection openMySqlConnection() throws SQLException {
        return environment.openMySqlConnection();
    }

    void removeSecondaryCreatorRoleBinding() throws SQLException {
        try (Connection connection = environment.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 DELETE FROM app_user_role
                 WHERE id = ? AND user_id = ? AND role_id = ?
                 """)) {
            statement.setLong(1, SECONDARY_USER_ROLE_ID);
            statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
            statement.setLong(3, PERSONAL_CREATOR_ROLE_ID);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("未能精确移除受限账号的创作角色绑定");
            }
        }
    }

    void stopCreator() {
        ExternalStarterProcess starter = creatorStarter;
        creatorStarter = null;
        if (starter != null) {
            starter.close();
        }
    }

    void restartCreator() throws IOException, InterruptedException {
        if (creatorStarter != null && creatorStarter.isAlive()) {
            throw new IllegalStateException("用户端 starter 仍在运行，不能重复启动");
        }
        creatorStarter = ExternalStarterProcess.start("creator-starter-restarted", creatorJar, starterArguments,
            timelineRuntime.creatorEnvironment());
        lastCreatorToken = null;
    }

    HttpResponse<String> operatingGet(String path, String token) throws IOException, InterruptedException {
        return request(operatingStarter.endpoint(path), "GET", token, OPERATING_CLIENT_ID, null, false);
    }

    HttpResponse<String> operatingGetWithRepeatedAuthorization(String path, String token)
        throws IOException, InterruptedException {
        return request(operatingStarter.endpoint(path), "GET", token, OPERATING_CLIENT_ID, null, true);
    }

    HttpResponse<String> operatingGetWithTokenParameter(String path, String token)
        throws IOException, InterruptedException {
        return request(operatingStarter.endpoint(path + "?token=outside-header"), "GET", token,
            OPERATING_CLIENT_ID, null, false);
    }

    HttpResponse<String> operatingPost(String path, String token, String body)
        throws IOException, InterruptedException {
        return request(operatingStarter.endpoint(path), "POST", token, OPERATING_CLIENT_ID, body, false);
    }

    /**
     * 在真实 Starter 运行期间应用个人积分迁移并为当前创作者写入受控测试账户。
     *
     * <p>迁移会推进当前有效 personal_creator 用户的权限修订号，因此调用前签发的
     * app token 必须在下一次请求时由真实修订守卫拒绝。</p>
     */
    void applyPersonalQuotaMigrationsAndSeedAccount() throws Exception {
        Path apiRoot = requiredAbsoluteDirectory("it.api.root");
        Path accountMigration = requiredRegularFile(
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_02_personal_quota_account.sql"));
        Path usedBalanceMigration = requiredRegularFile(
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_03_quota_used_balance.sql"));

        try (Connection connection = environment.openMySqlConnection()) {
            executeSqlScript(connection, accountMigration);
            executeSqlScript(connection, usedBalanceMigration);
            try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO av_quota_account (
                    id, tenant_id, subject_type, subject_id, unit_code,
                    available_balance, locked_balance, used_balance, account_revision
                ) VALUES (?, ?, 'app_user', ?, 'ai_text_credit', 20000, 0, 0, 0)
                """)) {
                statement.setLong(1, 1_761_400_000_000_090_007L);
                statement.setLong(2, 1_761_000_000_000_000_101L);
                statement.setLong(3, SHARED_NUMERIC_USER_ID);
                statement.executeUpdate();
            }
        }
    }

    TimelineSource seedTimelineDigitalHumanSource() throws Exception {
        String runId = timelineRuntime.runId();
        long suffix = Integer.toUnsignedLong(runId.substring(0, 12).hashCode());
        long voiceJobId = 8_100_000_000_000_000_000L + suffix * 2L;
        long videoJobId = voiceJobId + 1L;
        String voiceKey = voiceJobId + "/output/" + UUID.randomUUID() + ".wav";
        String videoKey = videoJobId + "/output/" + UUID.randomUUID() + ".mp4";
        Path voiceTarget = timelineRuntime.digitalHumanMediaRoot().resolve(voiceKey);
        Path videoTarget = timelineRuntime.digitalHumanMediaRoot().resolve(videoKey);
        Files.createDirectories(voiceTarget.getParent());
        Files.createDirectories(videoTarget.getParent());
        Files.copy(timelineRuntime.primaryAudioFixture(), voiceTarget, StandardCopyOption.COPY_ATTRIBUTES);
        Files.copy(timelineRuntime.baseVideoFixture(), videoTarget, StandardCopyOption.COPY_ATTRIBUTES);

        byte[] voiceBytes = Files.readAllBytes(voiceTarget);
        byte[] videoBytes = Files.readAllBytes(videoTarget);
        try (Connection connection = environment.openMySqlConnection()) {
            insertDigitalHumanJob(connection, voiceJobId, null, "voice_generate", "indextts2",
                voiceKey, "audio/wav", voiceBytes, true, runId);
            insertDigitalHumanJob(connection, videoJobId, voiceJobId, "video_generate", "comfyui",
                videoKey, "video/mp4", videoBytes, false, runId);
        }
        return new TimelineSource(runId, Long.toString(voiceJobId), Long.toString(videoJobId));
    }

    private static void insertDigitalHumanJob(Connection connection, long jobId, Long parentJobId,
                                              String jobType, String provider, String outputKey,
                                              String outputType, byte[] content, boolean voiceConfirmed,
                                              String runId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO av_dh_generation_job (
                id, tenant_id, owner_user_id, job_type, status, stage, progress, parent_job_id,
                idempotency_key, input_hash, script_text, input_media_key, output_media_key,
                output_media_type, output_media_size, output_media_sha256, provider, provider_job_id,
                poll_token, poll_lease_until, poll_error_count, voice_confirmed,
                create_by, update_by
            ) VALUES (?, ?, ?, ?, 'succeeded', 'completed', 100, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      NULL, NULL, 0, ?, ?, ?)
            """)) {
            statement.setLong(1, jobId);
            statement.setLong(2, 1_761_000_000_000_000_101L);
            statement.setLong(3, SHARED_NUMERIC_USER_ID);
            statement.setString(4, jobType);
            if (parentJobId == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, parentJobId);
            }
            statement.setString(6, "timeline-it-" + jobType + '-' + runId);
            statement.setString(7, sha256((jobType + '\n' + runId).getBytes(StandardCharsets.UTF_8)));
            statement.setString(8, "真实媒体恢复验收");
            statement.setString(9, jobId + "/input/" + UUID.randomUUID() + ("voice_generate".equals(jobType)
                ? ".wav" : ".png"));
            statement.setString(10, outputKey);
            statement.setString(11, outputType);
            statement.setLong(12, content.length);
            statement.setString(13, sha256(content));
            statement.setString(14, provider);
            statement.setString(15, "timeline-it-" + runId);
            statement.setBoolean(16, voiceConfirmed);
            statement.setLong(17, SHARED_NUMERIC_USER_ID);
            statement.setLong(18, SHARED_NUMERIC_USER_ID);
            statement.executeUpdate();
        }
    }

    record TimelineSource(String runId, String voiceJobId, String videoJobId) {
    }

    @Override
    public void close() throws Exception {
        Throwable failure = null;
        failure = cleanupTimelineData(failure);
        failure = closeResource(operatingStarter, failure);
        failure = closeResource(creatorStarter, failure);
        if (starterProcessesStopped(operatingStarter, creatorStarter)) {
            failure = cleanupFixtureIdentity(failure);
            failure = assertOnlyCurrentRunRedisKeysAdded(environment, redisKeysBeforeStart, failure);
            failure = clearCurrentRunRedisKeys(environment, failure);
            failure = assertCurrentRunRedisKeysCleared(environment, failure);
            failure = closeResource(timelineRuntime, failure);
        } else {
            failure = appendFailure(failure, new IllegalStateException(
                "外部 starter 尚未全部退出，已跳过 Redis 清理以避免存活进程在零残留断言后重新写入"));
        }
        if (failure != null) {
            if (failure instanceof Exception exception) {
                throw exception;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("关闭双启动器 HTTP 测试资源失败", failure);
        }
    }

    private Throwable cleanupTimelineData(Throwable previousFailure) {
        try {
            if (!tableExists("av_creation_asset")) {
                return previousFailure;
            }
            List<String> assetIds = fixtureAssetIds();
            removeFixtureReferences();
            if (!assetIds.isEmpty()) {
                if (creatorStarter == null || !creatorStarter.isAlive()) {
                    restartCreator();
                }
                String token = lastCreatorToken;
                if (token == null || token.isBlank()) {
                    token = loginCreator();
                }
                for (String assetId : assetIds) {
                    HttpResponse<String> response = request(requireCreatorStarter().endpoint(
                            "/api/studio/creation-assets/" + assetId),
                        "DELETE", token, CREATOR_CLIENT_KEY, null, false);
                    requireCode(response, 200, "清理测试创作素材");
                }
            }
            try (Connection connection = environment.openMySqlConnection()) {
                executeOwnerDelete(connection, "av_dh_generation_job");
            }
        } catch (Exception | Error cleanupFailure) {
            return appendFailure(previousFailure, cleanupFailure);
        }
        return previousFailure;
    }

    private List<String> fixtureAssetIds() throws SQLException {
        List<String> assetIds = new ArrayList<>();
        try (Connection connection = environment.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT asset_id FROM av_creation_asset
                 WHERE owner_user_id = ? AND del_flag = '0'
                 ORDER BY asset_id
                 """)) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    assetIds.add(Long.toString(result.getLong(1)));
                }
            }
        }
        return assetIds;
    }

    private void removeFixtureReferences() throws SQLException {
        try (Connection connection = environment.openMySqlConnection()) {
            for (String table : List.of(
                "av_timeline_asset_ref", "av_timeline_write_receipt", "av_timeline_version", "av_timeline_draft",
                "av_ai_task_attempt", "av_ai_task_execution", "av_ai_task", "av_creation_project")) {
                executeOwnerDelete(connection, table);
            }
        }
    }

    private static void executeOwnerDelete(Connection connection, String table) throws SQLException {
        if (!SAFE_FIXTURE_TABLES.contains(table)) {
            throw new IllegalArgumentException("不允许清理的测试表：" + table);
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM " + table + " WHERE owner_user_id IN (?, ?)")) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
            statement.executeUpdate();
        }
    }

    private Throwable cleanupFixtureIdentity(Throwable previousFailure) {
        try (Connection connection = environment.openMySqlConnection()) {
            deleteFixtureRolePermissions(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM app_user_role WHERE user_id IN (?, ?)")) {
                statement.setLong(1, SHARED_NUMERIC_USER_ID);
                statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM app_user WHERE (user_id = ? AND username = ?) OR (user_id = ? AND username = ?)")) {
                statement.setLong(1, SHARED_NUMERIC_USER_ID);
                statement.setString(2, CREATOR_USERNAME);
                statement.setLong(3, SECONDARY_NUMERIC_USER_ID);
                statement.setString(4, CREATOR_USERNAME_B);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM app_auth_client
                WHERE client_key IN (?, ?, ?, ?, ?)
                """)) {
                statement.setString(1, CREATOR_CLIENT_KEY);
                statement.setString(2, CREATOR_CLIENT_KEY_B);
                statement.setString(3, WRONG_GRANT_CLIENT_KEY);
                statement.setString(4, PATH_RESTRICTED_CLIENT_KEY);
                statement.setString(5, IP_RESTRICTED_CLIENT_KEY);
                statement.executeUpdate();
            }
        } catch (Exception | Error cleanupFailure) {
            return appendFailure(previousFailure, cleanupFailure);
        }
        return previousFailure;
    }

    private HttpResponse<String> request(URI endpoint,
                                         String method,
                                         String token,
                                         String clientKey,
                                         String body,
                                         boolean repeatedAuthorization)
        throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
            .timeout(HTTP_TIMEOUT)
            .header("Accept", "application/json");
        if (clientKey != null) {
            request.header("clientid", clientKey);
        }
        if (token != null) {
            String authorization = "Bearer " + token;
            request.header("Authorization", authorization);
            if (repeatedAuthorization) {
                request.header("Authorization", authorization);
            }
        }

        if ("GET".equals(method)) {
            request.GET();
        } else if ("POST".equals(method)) {
            if (body == null || body.isBlank()) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            }
        } else if ("PUT".equals(method)) {
            request.header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body, StandardCharsets.UTF_8));
        } else if ("DELETE".equals(method)) {
            request.DELETE();
        } else {
            throw new IllegalArgumentException("不支持的 HTTP 方法：" + method);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private ExternalStarterProcess requireCreatorStarter() {
        ExternalStarterProcess starter = creatorStarter;
        if (starter == null || !starter.isAlive()) {
            throw new IllegalStateException("用户端 starter 未运行");
        }
        return starter;
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = environment.openMySqlConnection();
             PreparedStatement statement = connection.prepareStatement("""
                 SELECT COUNT(*) FROM information_schema.TABLES
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'
                 """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static void initializeDatabase(LocalIntegrationEnvironment environment, Path apiRoot) throws Exception {
        Path initializationSnapshot = requiredRegularFile(
            apiRoot.getParent().resolve("docs/sql/20260809_01_ai_video_test_initialization.sql"));
        Path migrationRoot = requiredAbsoluteDirectory(apiRoot.resolve("../docs/sql/ai-video/mysql"));
        try (Connection connection = environment.openMySqlConnection()) {
            int tableCount = databaseTableCount(connection);
            if (tableCount == 0) {
                executeInitializationSnapshot(connection, initializationSnapshot);
            } else {
                requireTables(connection, List.of("app_user", "app_role", "app_permission", "app_role_permission"));
            }
            installVoiceSchema(connection, migrationRoot);
            installDigitalHumanSchema(connection, migrationRoot);
            installTimelineSchema(connection, migrationRoot);
            preparePrivateOssFixture(connection);
            prepareFixtureIdentity(connection);
            insertDualIdentityFixture(connection);
        }
    }

    private static void preparePrivateOssFixture(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT DATABASE()");
             ResultSet result = statement.executeQuery()) {
            if (!result.next() || !"ai_video_test".equals(result.getString(1))) {
                throw new IllegalStateException("OSS 测试夹具只能修改专用数据库 ai_video_test");
            }
        }

        List<Long> activeConfigIds = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT oss_config_id
            FROM sys_oss_config
            WHERE status = 'Y'
            """); ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                activeConfigIds.add(result.getLong(1));
            }
        }
        if (activeConfigIds.size() != 1) {
            throw new IllegalStateException("测试数据库必须恰好存在一条启用的 OSS 配置，实际数量="
                + activeConfigIds.size());
        }

        long activeConfigId = activeConfigIds.getFirst();
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE sys_oss_config
            SET access_policy = '0'
            WHERE oss_config_id = ? AND status = 'Y'
            """)) {
            statement.setLong(1, activeConfigId);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT access_policy
            FROM sys_oss_config
            WHERE oss_config_id = ? AND status = 'Y'
            """)) {
            statement.setLong(1, activeConfigId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !"0".equals(result.getString(1)) || result.next()) {
                    throw new IllegalStateException("启用的 OSS 测试配置未能切换为 private 策略");
                }
            }
        }
    }

    private static Path requiredAbsoluteDirectory(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IllegalStateException("测试所需目录不存在：" + normalized);
        }
        return normalized;
    }

    private static int databaseTableCount(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
            """); ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        }
    }

    private static void executeInitializationSnapshot(Connection connection, Path snapshot) throws Exception {
        StringBuilder script = new StringBuilder();
        for (String line : Files.readAllLines(snapshot, StandardCharsets.UTF_8)) {
            String trimmed = line.trim().toUpperCase(java.util.Locale.ROOT);
            if (trimmed.startsWith("CREATE DATABASE") || trimmed.startsWith("USE `")) {
                continue;
            }
            script.append(line).append('\n');
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
        }
        try {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(
                new ByteArrayResource(script.toString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        } finally {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
        }
    }

    private static void installDigitalHumanSchema(Connection connection, Path migrationRoot) throws Exception {
        boolean tableExists = tableExists(connection, "av_dh_generation_job");
        if (!tableExists) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260803_02_digital_human_vertical_flow.sql")));
        }
        int leaseColumnCount = columnCount(connection, "av_dh_generation_job",
            List.of("poll_token", "poll_lease_until", "poll_error_count"));
        if (leaseColumnCount == 0) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260803_03_digital_human_poll_lease.sql")));
        } else if (leaseColumnCount != 3) {
            throw new IllegalStateException("数字人轮询租约迁移处于部分应用状态");
        }
    }

    private static void installVoiceSchema(Connection connection, Path migrationRoot) throws Exception {
        if (!tableExists(connection, "av_asset")) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260803_01_user_portrait.sql")));
        }
        if (!tableExists(connection, "av_voice")) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260803_04_voice_upload_transcription.sql")));
        }
        if (columnCount(connection, "av_voice", List.of("transcript_timeline_json")) == 0) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260803_05_voice_transcript_timeline.sql")));
        }
        executeSqlScript(connection, requiredRegularFile(
            migrationRoot.resolve("20260806_01_creation_asset_selection.sql")));
        requireTables(connection, List.of("av_asset", "av_voice"));
    }

    private static void installTimelineSchema(Connection connection, Path migrationRoot) throws Exception {
        List<String> timelineTables = List.of(
            "av_creation_asset", "av_creation_project", "av_timeline_draft", "av_timeline_version",
            "av_timeline_asset_ref", "av_timeline_write_receipt", "av_ai_task", "av_ai_task_execution",
            "av_ai_task_attempt");
        int installed = tableCount(connection, timelineTables);
        if (installed == 0) {
            executeSqlScript(connection, requiredRegularFile(
                migrationRoot.resolve("20260808_01_creation_timeline.sql")));
        } else if (installed != timelineTables.size()) {
            throw new IllegalStateException("时间轴迁移处于部分应用状态");
        }
        try (var migrations = Files.list(migrationRoot)) {
            List<Path> optionalC1 = migrations
                .filter(path -> path.getFileName().toString().matches("20260808_02_creation_timeline.*\\.sql"))
                .sorted()
                .toList();
            if (optionalC1.size() > 1) {
                throw new IllegalStateException("发现多个时间轴 _02 迁移，无法确定受控顺序");
            }
            if (optionalC1.size() == 1) {
                executeSqlScript(connection, requiredRegularFile(optionalC1.getFirst()));
            }
        }
        requireTables(connection, timelineTables);
    }

    private static void requireTables(Connection connection, List<String> tables) throws SQLException {
        if (tableCount(connection, tables) != tables.size()) {
            throw new IllegalStateException("测试数据库缺少必需表");
        }
    }

    private static int tableCount(Connection connection, List<String> tables) throws SQLException {
        int count = 0;
        for (String table : tables) {
            if (tableExists(connection, table)) {
                count++;
            }
        }
        return count;
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND TABLE_TYPE = 'BASE TABLE'
            """)) {
            statement.setString(1, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private static int columnCount(Connection connection, String table, List<String> columns) throws SQLException {
        int count = 0;
        for (String column : columns) {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                """)) {
                statement.setString(1, table);
                statement.setString(2, column);
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    count += result.getInt(1);
                }
            }
        }
        return count;
    }

    private static void prepareFixtureIdentity(Connection connection) throws SQLException {
        deleteFixtureRolePermissions(connection);
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM app_user_role WHERE user_id IN (?, ?)")) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM app_user WHERE (user_id = ? AND username = ?) OR (user_id = ? AND username = ?)")) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            statement.setString(2, CREATOR_USERNAME);
            statement.setLong(3, SECONDARY_NUMERIC_USER_ID);
            statement.setString(4, CREATOR_USERNAME_B);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM app_auth_client WHERE client_key IN (?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, CREATOR_CLIENT_KEY);
            statement.setString(2, CREATOR_CLIENT_KEY_B);
            statement.setString(3, WRONG_GRANT_CLIENT_KEY);
            statement.setString(4, PATH_RESTRICTED_CLIENT_KEY);
            statement.setString(5, IP_RESTRICTED_CLIENT_KEY);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT
              (SELECT COUNT(*) FROM app_user WHERE user_id IN (?, ?) OR username_normalized IN (?, ?)) +
              (SELECT COUNT(*) FROM app_auth_client WHERE client_key IN (?, ?, ?, ?, ?))
            """)) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
            statement.setString(3, CREATOR_USERNAME);
            statement.setString(4, CREATOR_USERNAME_B);
            statement.setString(5, CREATOR_CLIENT_KEY);
            statement.setString(6, CREATOR_CLIENT_KEY_B);
            statement.setString(7, WRONG_GRANT_CLIENT_KEY);
            statement.setString(8, PATH_RESTRICTED_CLIENT_KEY);
            statement.setString(9, IP_RESTRICTED_CLIENT_KEY);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) != 0) {
                    throw new IllegalStateException("固定双启动器身份夹具无法精确清理");
                }
            }
        }
    }

    private static void insertDualIdentityFixture(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user (
                user_id, username, username_normalized, password_hash, personal_tenant_id, display_name,
                status, credential_revision, identity_revision, permission_revision,
                created_by_type, created_by_id, updated_by_type, updated_by_id, del_flag
            ) VALUES (?, ?, ?, ?, ?, ?, 'active', 1, 1, 1, 'sys_user', ?, 'sys_user', ?, '0')
            """)) {
            statement.setLong(1, SHARED_NUMERIC_USER_ID);
            statement.setString(2, CREATOR_USERNAME);
            statement.setString(3, CREATOR_USERNAME);
            statement.setString(4, BCrypt.hashpw(CREATOR_PASSWORD));
            statement.setLong(5, 1_761_000_000_000_000_101L);
            statement.setString(6, "HTTP 双端同号用户");
            statement.setLong(7, SHARED_NUMERIC_USER_ID);
            statement.setLong(8, SHARED_NUMERIC_USER_ID);
            statement.executeUpdate();

            statement.setLong(1, SECONDARY_NUMERIC_USER_ID);
            statement.setString(2, CREATOR_USERNAME_B);
            statement.setString(3, CREATOR_USERNAME_B);
            statement.setString(4, BCrypt.hashpw(CREATOR_PASSWORD_B));
            statement.setLong(5, SECONDARY_PERSONAL_TENANT_ID);
            statement.setString(6, "HTTP 跨账号隔离用户");
            statement.setLong(7, SECONDARY_NUMERIC_USER_ID);
            statement.setLong(8, SECONDARY_NUMERIC_USER_ID);
            statement.executeUpdate();
        }

        insertAppClient(connection, 1_761_400_000_000_090_001L,
            "http-it-app-client", CREATOR_CLIENT_KEY, "password", "/api/**", null);
        insertAppClient(connection, 1_761_400_000_000_090_002L,
            "http-it-app-client-b", CREATOR_CLIENT_KEY_B, "password", "/api/**", null);
        insertAppClient(connection, 1_761_400_000_000_090_004L,
            "http-it-wrong-grant-client", WRONG_GRANT_CLIENT_KEY, "authorization_code", "/api/**", null);
        insertAppClient(connection, 1_761_400_000_000_090_005L,
            "http-it-login-only-client", PATH_RESTRICTED_CLIENT_KEY, "password", "/api/auth/login", null);
        insertAppClient(connection, 1_761_400_000_000_090_006L,
            "http-it-ip-restricted-client", IP_RESTRICTED_CLIENT_KEY, "password", "/api/**", "203.0.113.1");

        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_user_role (
                id, user_id, role_id, status, valid_from, valid_until,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            ) VALUES (?, ?, 1000101, 'active', NULL, NULL, 'sys_user', ?, 'sys_user', ?)
            """)) {
            statement.setLong(1, 1_761_400_000_000_090_003L);
            statement.setLong(2, SHARED_NUMERIC_USER_ID);
            statement.setLong(3, SHARED_NUMERIC_USER_ID);
            statement.setLong(4, SHARED_NUMERIC_USER_ID);
            statement.executeUpdate();

            statement.setLong(1, SECONDARY_USER_ROLE_ID);
            statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
            statement.setLong(3, SECONDARY_NUMERIC_USER_ID);
            statement.setLong(4, SECONDARY_NUMERIC_USER_ID);
            statement.executeUpdate();
        }

        insertFixtureRolePermission(connection, TASK_QUERY_BINDING_ID, "aivideo:task:query");
        insertFixtureRolePermission(connection, TASK_CANCEL_BINDING_ID, "aivideo:task:cancel");
    }

    private static void insertFixtureRolePermission(Connection connection, long bindingId, String permissionCode)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_role_permission (
                id, role_id, permission_id, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id
            )
            SELECT ?, ?, permission.permission_id, 'active',
                   'sys_user', ?, 'sys_user', ?
            FROM app_permission permission
            WHERE permission.permission_code = ?
              AND permission.status = 'active'
              AND NOT EXISTS (
                SELECT 1 FROM app_role_permission binding
                WHERE binding.role_id = ?
                  AND binding.permission_id = permission.permission_id
                  AND binding.status = 'active'
              )
            """)) {
            statement.setLong(1, bindingId);
            statement.setLong(2, PERSONAL_CREATOR_ROLE_ID);
            statement.setLong(3, SHARED_NUMERIC_USER_ID);
            statement.setLong(4, SHARED_NUMERIC_USER_ID);
            statement.setString(5, permissionCode);
            statement.setLong(6, PERSONAL_CREATOR_ROLE_ID);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*)
            FROM app_role_permission binding
            JOIN app_permission permission ON permission.permission_id = binding.permission_id
            WHERE binding.role_id = ? AND binding.status = 'active'
              AND permission.permission_code = ? AND permission.status = 'active'
            """)) {
            statement.setLong(1, PERSONAL_CREATOR_ROLE_ID);
            statement.setString(2, permissionCode);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (result.getInt(1) != 1) {
                    throw new IllegalStateException("测试账号权限绑定缺失：" + permissionCode);
                }
            }
        }
    }

    private static void deleteFixtureRolePermissions(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            DELETE FROM app_role_permission
            WHERE role_id = ? AND id IN (?, ?)
            """)) {
            statement.setLong(1, PERSONAL_CREATOR_ROLE_ID);
            statement.setLong(2, TASK_QUERY_BINDING_ID);
            statement.setLong(3, TASK_CANCEL_BINDING_ID);
            statement.executeUpdate();
        }
    }

    private static void insertAppClient(Connection connection, long id, String clientId, String clientKey,
                                        String grantTypes, String accessPaths, String ipWhitelist) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO app_auth_client (
                id, client_id, client_key, grant_types, access_paths, ip_whitelist,
                token_timeout, active_timeout, client_revision, status,
                created_by_type, created_by_id, updated_by_type, updated_by_id, del_flag
            ) VALUES (?, ?, ?, ?, ?, ?, 3600, 1800, 1, 'active',
                'sys_user', ?, 'sys_user', ?, '0')
            """)) {
            statement.setLong(1, id);
            statement.setString(2, clientId);
            statement.setString(3, clientKey);
            statement.setString(4, grantTypes);
            statement.setString(5, accessPaths);
            statement.setString(6, ipWhitelist);
            statement.setLong(7, SHARED_NUMERIC_USER_ID);
            statement.setLong(8, SHARED_NUMERIC_USER_ID);
            statement.executeUpdate();
        }
    }

    private static void executeSqlScript(Connection connection, Path script) throws Exception {
        ScriptUtils.executeSqlScript(
            connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8)
        );
    }

    private static Path requiredAbsoluteDirectory(String property) {
        String rawPath = System.getProperty(property);
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalStateException("缺少 Failsafe 系统属性：" + property);
        }
        Path path = Path.of(rawPath).normalize();
        if (!path.isAbsolute() || !Files.isDirectory(path)) {
            throw new IllegalStateException("系统属性必须指向已存在的绝对目录：" + property + "=" + rawPath);
        }
        return path;
    }

    private static Path requiredRegularFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("测试所需文件不存在：" + path.toAbsolutePath());
        }
        return path.toAbsolutePath().normalize();
    }

    private static void requireCode(HttpResponse<String> response, int expectedCode, String operation) {
        Matcher matcher = RESPONSE_CODE.matcher(response.body());
        if (!matcher.find() || Integer.parseInt(matcher.group(1)) != expectedCode) {
            throw new IllegalStateException(operation + "失败，状态=" + response.statusCode() + "，响应=" + response.body());
        }
    }

    private static void closeAfterStartFailure(Throwable failure,
                                               ExternalStarterProcess operatingStarter,
                                               ExternalStarterProcess creatorStarter,
                                               LocalIntegrationEnvironment environment,
                                               Set<String> redisKeysBeforeStart) {
        Throwable closeFailure = closeResource(operatingStarter, null);
        closeFailure = closeResource(creatorStarter, closeFailure);
        if (!hasUnstoppedStarterFailure(failure)
            && starterProcessesStopped(operatingStarter, creatorStarter)) {
            closeFailure = assertOnlyCurrentRunRedisKeysAdded(environment, redisKeysBeforeStart, closeFailure);
            closeFailure = clearCurrentRunRedisKeys(environment, closeFailure);
            closeFailure = assertCurrentRunRedisKeysCleared(environment, closeFailure);
        } else {
            closeFailure = appendFailure(closeFailure, new IllegalStateException(
                "外部 starter 尚未全部退出，已跳过 Redis 清理；请按异常中的 PID 人工处置"));
        }
        if (closeFailure != null) {
            failure.addSuppressed(closeFailure);
        }
    }

    private static void cleanupFixtureIdentityAfterStartFailure(LocalIntegrationEnvironment environment,
                                                                 Throwable failure) {
        if (environment == null) {
            return;
        }
        try (Connection connection = environment.openMySqlConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM app_user_role WHERE user_id IN (?, ?)")) {
                statement.setLong(1, SHARED_NUMERIC_USER_ID);
                statement.setLong(2, SECONDARY_NUMERIC_USER_ID);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM app_user WHERE (user_id = ? AND username = ?) OR (user_id = ? AND username = ?)")) {
                statement.setLong(1, SHARED_NUMERIC_USER_ID);
                statement.setString(2, CREATOR_USERNAME);
                statement.setLong(3, SECONDARY_NUMERIC_USER_ID);
                statement.setString(4, CREATOR_USERNAME_B);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                DELETE FROM app_auth_client WHERE client_key IN (?, ?, ?, ?, ?)
                """)) {
                statement.setString(1, CREATOR_CLIENT_KEY);
                statement.setString(2, CREATOR_CLIENT_KEY_B);
                statement.setString(3, WRONG_GRANT_CLIENT_KEY);
                statement.setString(4, PATH_RESTRICTED_CLIENT_KEY);
                statement.setString(5, IP_RESTRICTED_CLIENT_KEY);
                statement.executeUpdate();
            }
        } catch (Exception cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static Throwable closeResource(AutoCloseable resource, Throwable previousFailure) {
        if (resource == null) {
            return previousFailure;
        }
        try {
            resource.close();
        } catch (Exception | Error closeFailure) {
            if (previousFailure == null) {
                return closeFailure;
            }
            previousFailure.addSuppressed(closeFailure);
        }
        return previousFailure;
    }

    private static String loginOperating(ExternalStarterProcess operatingStarter)
        throws IOException, InterruptedException {
        String body = "{\"clientId\":\"" + OPERATING_CLIENT_ID
            + "\",\"grantType\":\"password\",\"username\":\"" + OPERATING_USERNAME
            + "\",\"password\":\"" + OPERATING_PASSWORD + "\"}";
        HttpRequest request = HttpRequest.newBuilder(operatingStarter.endpoint("/auth/login"))
            .timeout(HTTP_TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        Matcher codeMatcher = RESPONSE_CODE.matcher(response.body());
        boolean hasResponseCode = codeMatcher.find();
        String responseCode = hasResponseCode ? codeMatcher.group(1) : "missing";
        if (response.statusCode() != 200 || !hasResponseCode || !"200".equals(responseCode)) {
            throw new IllegalStateException("运营端测试账号登录失败，HTTP=" + response.statusCode()
                + "，业务码=" + responseCode);
        }
        Matcher tokenMatcher = ACCESS_TOKEN.matcher(response.body());
        if (!tokenMatcher.find()) {
            throw new IllegalStateException("运营端测试账号登录响应缺少 access_token");
        }
        return tokenMatcher.group(1);
    }

    private static boolean starterProcessesStopped(ExternalStarterProcess... starters) {
        for (ExternalStarterProcess starter : starters) {
            if (starter != null && starter.isAlive()) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasUnstoppedStarterFailure(Throwable failure) {
        return failure instanceof UnstoppedExternalStarterException;
    }

    private static Throwable appendFailure(Throwable previousFailure, Throwable nextFailure) {
        if (previousFailure == null) {
            return nextFailure;
        }
        previousFailure.addSuppressed(nextFailure);
        return previousFailure;
    }

    private static Throwable assertOnlyCurrentRunRedisKeysAdded(LocalIntegrationEnvironment environment,
                                                                 Set<String> redisKeysBeforeStart,
                                                                 Throwable previousFailure) {
        if (environment == null || redisKeysBeforeStart == null) {
            return previousFailure;
        }
        try {
            environment.assertOnlyCurrentRunRedisKeysAdded(redisKeysBeforeStart);
        } catch (RuntimeException assertionFailure) {
            if (previousFailure == null) {
                return assertionFailure;
            }
            previousFailure.addSuppressed(assertionFailure);
        }
        return previousFailure;
    }

    private static Throwable assertCurrentRunRedisKeysCleared(LocalIntegrationEnvironment environment,
                                                               Throwable previousFailure) {
        if (environment == null) {
            return previousFailure;
        }
        try {
            environment.assertCurrentRunRedisKeysCleared();
        } catch (RuntimeException assertionFailure) {
            if (previousFailure == null) {
                return assertionFailure;
            }
            previousFailure.addSuppressed(assertionFailure);
        }
        return previousFailure;
    }

    private static Throwable clearCurrentRunRedisKeys(LocalIntegrationEnvironment environment,
                                                       Throwable previousFailure) {
        if (environment == null) {
            return previousFailure;
        }
        try {
            environment.clearCurrentRunRedisKeys();
        } catch (RuntimeException cleanupFailure) {
            if (previousFailure == null) {
                return cleanupFailure;
            }
            previousFailure.addSuppressed(cleanupFailure);
        }
        return previousFailure;
    }

    record TimelineRuntime(String runId, Path root, Path workRoot, Path digitalHumanMediaRoot,
                           Path fontRoot, Path ffmpeg, Path ffprobe,
                           Path baseVideoFixture, Path primaryAudioFixture) implements AutoCloseable {

        static TimelineRuntime create(Path apiRoot) throws IOException {
            String runId = UUID.randomUUID().toString().replace("-", "");
            Path root = apiRoot.resolve("ai-video-integration-tests/target/timeline-http-it/" + runId)
                .toAbsolutePath().normalize();
            try {
                Path workRoot = root.resolve("work");
                Path digitalHumanMediaRoot = root.resolve("digital-human-media");
                Files.createDirectories(workRoot);
                Files.createDirectories(digitalHumanMediaRoot);
                Path fontRoot = requiredAbsoluteDirectory(apiRoot.resolve(
                    "ruoyi-modules/ai-video/ai-video-infra/src/main/resources/timeline/fonts"));
                Path mediaFixtures = requiredAbsoluteDirectory(apiRoot.resolve(
                    "ruoyi-modules/ai-video/ai-video-infra/src/test/resources/timeline/media"));
                return new TimelineRuntime(runId, root, workRoot, digitalHumanMediaRoot, fontRoot,
                    resolveExecutable("AIVIDEO_TIMELINE_FFMPEG_PATH", "ffmpeg"),
                    resolveExecutable("AIVIDEO_TIMELINE_FFPROBE_PATH", "ffprobe"),
                    requiredRegularFile(mediaFixtures.resolve("base-portrait-with-audio.mp4")),
                    requiredRegularFile(mediaFixtures.resolve("primary.wav")));
            } catch (IOException | RuntimeException failure) {
                try {
                    deleteTree(root);
                } catch (IOException cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        }

        Map<String, String> creatorEnvironment() {
            return Map.ofEntries(
                Map.entry("AIVIDEO_TIMELINE_ENABLED", "true"),
                Map.entry("AIVIDEO_TIMELINE_FFMPEG_PATH", ffmpeg.toString()),
                Map.entry("AIVIDEO_TIMELINE_FFPROBE_PATH", ffprobe.toString()),
                Map.entry("AIVIDEO_TIMELINE_WORK_ROOT", workRoot.toString()),
                Map.entry("AIVIDEO_TIMELINE_FONT_ROOT", fontRoot.toString()),
                Map.entry("AIVIDEO_TIMELINE_WORKER_ID", "timeline-http-it-" + runId),
                Map.entry("AIVIDEO_TIMELINE_POLL_DELAY", "PT0.2S"),
                Map.entry("AIVIDEO_TIMELINE_RECOVERY_BATCH_LIMIT", "10"),
                Map.entry("AIVIDEO_TIMELINE_PER_USER_CONCURRENCY_LIMIT", "1"),
                Map.entry("AIVIDEO_TIMELINE_SYSTEM_CONCURRENCY_LIMIT", "1"),
                Map.entry("DIGITAL_HUMAN_MEDIA_ROOT", digitalHumanMediaRoot.toString()),
                Map.entry("MANAGEMENT_ENDPOINT_BEANS_ENABLED", "true"),
                Map.entry("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "health,beans")
            );
        }

        @Override
        public void close() throws IOException {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            Path realRoot = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path expectedParent = root.getParent().toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realRoot.startsWith(expectedParent) || realRoot.equals(expectedParent)) {
                throw new IOException("拒绝清理非本次时间轴测试目录");
            }
            deleteTree(realRoot);
        }

        private static void deleteTree(Path root) throws IOException {
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (Files.isSymbolicLink(path)) {
                        throw new IOException("时间轴测试目录中出现符号链接，拒绝递归清理");
                    }
                    Files.deleteIfExists(path);
                }
            }
        }

        private static Path resolveExecutable(String overrideVariable, String command) throws IOException {
            String override = System.getenv(overrideVariable);
            if (override != null && !override.isBlank()) {
                return requireExecutable(Path.of(override));
            }
            String pathValue = System.getenv("PATH");
            if (pathValue != null) {
                String executable = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                    .contains("win") ? command + ".exe" : command;
                for (String directory : pathValue.split(Pattern.quote(java.io.File.pathSeparator))) {
                    if (directory == null || directory.isBlank()) {
                        continue;
                    }
                    Path candidate = Path.of(directory).resolve(executable);
                    if (Files.isRegularFile(candidate)) {
                        return requireExecutable(candidate);
                    }
                }
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isBlank()) {
                String executable = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                    .contains("win") ? command + ".exe" : command;
                Path winGetLink = Path.of(localAppData, "Microsoft", "WinGet", "Links", executable);
                if (Files.isRegularFile(winGetLink)) {
                    return requireExecutable(winGetLink);
                }
            }
            throw new IOException("找不到时间轴测试所需可执行文件：" + command);
        }

        private static Path requireExecutable(Path path) throws IOException {
            Path real = path.toAbsolutePath().normalize().toRealPath();
            if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(real)
                || !Files.isExecutable(real)) {
                throw new IOException("时间轴测试可执行文件无效：" + path.getFileName());
            }
            return real;
        }
    }

    record StarterArguments(String jdbcUrl, String databaseUsername, String databasePassword,
                            String redisHost, int redisPort, int redisDatabase, String redisPassword,
                            String redisKeyPrefix) {

        static StarterArguments from(LocalIntegrationEnvironment environment) {
            return new StarterArguments(environment.jdbcUrl(), environment.mysqlUsername(), environment.mysqlPassword(),
                environment.redisHost(), environment.redisPort(), environment.redisDatabase(), environment.redisPassword(),
                environment.redisKeyPrefix());
        }

        List<String> command(Path jar) {
            List<String> command = new ArrayList<>();
            command.add(javaExecutable().toString());
            command.add("-jar");
            command.add(jar.toString());
            command.add("--server.port=0");
            command.add("--spring.main.banner-mode=off");
            command.add("--captcha.enable=false");
            command.add("--api-decrypt.enabled=false");
            command.add("--spring.boot.admin.client.enabled=false");
            command.add("--snail-job.enabled=false");
            command.add("--snail-ai.enabled=false");
            command.add("--warm-flow.enabled=false");
            command.add("--liteflow.enable=false");
            command.add("--easy-es.enable=false");
            command.add("--spring.ai.mcp.server.enabled=false");
            command.add("--spring.ai.mcp.client.enabled=false");
            command.add("--spring.cloud.nacos.discovery.enabled=false");
            command.add("--spring.cloud.nacos.config.enabled=false");
            return command;
        }

        void applyConnectionEnvironment(ProcessBuilder builder) {
            builder.environment().put("SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_URL", jdbcUrl);
            builder.environment().put("SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_USERNAME", databaseUsername);
            builder.environment().put("SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_PASSWORD", databasePassword);
            builder.environment().put("SPRING_DATASOURCE_DYNAMIC_DATASOURCE_MASTER_DRIVER_CLASS_NAME",
                "com.mysql.cj.jdbc.Driver");
            builder.environment().put("SPRING_DATA_REDIS_HOST", redisHost);
            builder.environment().put("SPRING_DATA_REDIS_PORT", Integer.toString(redisPort));
            builder.environment().put("SPRING_DATA_REDIS_DATABASE", Integer.toString(redisDatabase));
            builder.environment().put("SPRING_DATA_REDIS_PASSWORD", redisPassword);
            builder.environment().put("SA_TOKEN_REDIS_KEY_PREFIX", redisKeyPrefix);
            builder.environment().put("REDISSON_KEY_PREFIX", redissonKeyPrefix());
        }

        private String redissonKeyPrefix() {
            if (redisKeyPrefix == null || !redisKeyPrefix.endsWith(":")) {
                throw new IllegalStateException("Redis 测试运行前缀必须以冒号结尾");
            }
            return redisKeyPrefix.substring(0, redisKeyPrefix.length() - 1);
        }

        private static Path javaExecutable() {
            String executable = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            Path path = Path.of(System.getProperty("java.home"), "bin", executable);
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("找不到运行外部 starter 所需的 Java 可执行文件：" + path);
            }
            return path;
        }
    }

    static final class ExternalStarterProcess implements AutoCloseable {

        private static final int MAXIMUM_LOG_LINES = 120;

        private final String name;
        private final Process process;
        private final CompletableFuture<Integer> port;
        private final Deque<String> recentLogs;
        private final Duration startupTimeout;
        private final List<String> sensitiveValues;

        private ExternalStarterProcess(String name, Process process,
                                       CompletableFuture<Integer> port, Deque<String> recentLogs,
                                       Duration startupTimeout, List<String> sensitiveValues) {
            this.name = name;
            this.process = process;
            this.port = port;
            this.recentLogs = recentLogs;
            this.startupTimeout = startupTimeout;
            this.sensitiveValues = sensitiveValues;
        }

        static ExternalStarterProcess start(String name, Path jar, StarterArguments starterArguments)
            throws IOException, InterruptedException {
            return start(name, jar, starterArguments, Map.of());
        }

        static ExternalStarterProcess start(String name, Path jar, StarterArguments starterArguments,
                                            Map<String, String> extraEnvironment)
            throws IOException, InterruptedException {
            ProcessBuilder builder = new ProcessBuilder(starterArguments.command(jar));
            builder.directory(jar.getParent().toFile());
            builder.redirectErrorStream(true);
            starterArguments.applyConnectionEnvironment(builder);
            builder.environment().put("SYS_SA_TOKEN_JWT_SECRET", SYSTEM_JWT_SECRET);
            builder.environment().put("APP_SA_TOKEN_JWT_SECRET", APP_JWT_SECRET);
            builder.environment().put("APP_SECURITY_TOKEN_WORKSPACE_KEY_SECRET", WORKSPACE_KEY_SECRET);
            builder.environment().put("ACTUATOR_BASIC_USERNAME", ACTUATOR_USERNAME);
            builder.environment().put("ACTUATOR_BASIC_PASSWORD", ACTUATOR_PASSWORD);
            builder.environment().putAll(extraEnvironment);

            return start(name, builder, STARTUP_TIMEOUT);
        }

        /**
         * 使用给定的进程命令启动外部 starter；同包测试可用短超时覆盖失败清理路径。
         */
        static ExternalStarterProcess start(String name, ProcessBuilder builder, Duration startupTimeout)
            throws IOException, InterruptedException {
            if (startupTimeout == null || startupTimeout.isZero() || startupTimeout.isNegative()) {
                throw new IllegalArgumentException("外部 starter 启动超时必须为正数");
            }
            List<String> sensitiveValues = sensitiveEnvironmentValues(builder.environment());
            Process process = builder.start();
            CompletableFuture<Integer> port = new CompletableFuture<>();
            Deque<String> recentLogs = new ArrayDeque<>();
            ExternalStarterProcess starter = new ExternalStarterProcess(name, process, port, recentLogs, startupTimeout,
                sensitiveValues);
            try {
                starter.readOutput();
                starter.awaitReady();
                return starter;
            } catch (Exception | Error failure) {
                Throwable closeFailure = null;
                try {
                    // 启动尚未返回给调用方时只能由本方法回收，避免遗漏已创建的子 JVM。
                    starter.close();
                } catch (Exception | Error cleanupFailure) {
                    closeFailure = cleanupFailure;
                }
                if (starter.isAlive()) {
                    UnstoppedExternalStarterException unstoppedFailure =
                        new UnstoppedExternalStarterException(name, process.pid(), failure);
                    if (closeFailure != null) {
                        unstoppedFailure.addSuppressed(closeFailure);
                    }
                    throw unstoppedFailure;
                }
                if (closeFailure != null) {
                    failure.addSuppressed(closeFailure);
                }
                throw failure;
            }
        }

        URI endpoint(String path) {
            return URI.create("http://127.0.0.1:" + awaitPort() + path);
        }

        @Override
        public void close() {
            List<ProcessHandle> descendants = process.descendants().toList();
            descendants.forEach(ProcessHandle::destroy);
            if (!process.isAlive()) {
                destroyRemainingDescendants(descendants);
                return;
            }
            process.destroy();
            if (!waitForExitUninterruptibly(PROCESS_STOP_TIMEOUT)) {
                descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                if (!waitForExitUninterruptibly(PROCESS_STOP_TIMEOUT)) {
                    throw new IllegalStateException(name + " 外部 starter 在强制终止后仍未退出，PID=" + process.pid());
                }
            }
            destroyRemainingDescendants(descendants);
        }

        private void destroyRemainingDescendants(List<ProcessHandle> descendants) {
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
            long deadline = System.nanoTime() + PROCESS_STOP_TIMEOUT.toNanos();
            while (descendants.stream().anyMatch(ProcessHandle::isAlive) && System.nanoTime() < deadline) {
                Thread.onSpinWait();
            }
            List<Long> alive = descendants.stream().filter(ProcessHandle::isAlive).map(ProcessHandle::pid).toList();
            if (!alive.isEmpty()) {
                throw new IllegalStateException(name + " 的子进程未能终止，PID=" + alive);
            }
        }

        private boolean waitForExitUninterruptibly(Duration timeout) {
            boolean interrupted = Thread.interrupted();
            long deadline = System.nanoTime() + timeout.toNanos();
            try {
                while (process.isAlive()) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return !process.isAlive();
                    }
                    try {
                        if (process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                            return true;
                        }
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
                return true;
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        boolean isAlive() {
            return process.isAlive();
        }

        private void readOutput() {
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendLog(line);
                        Integer startedPort = parseStartedPort(line);
                        if (startedPort != null) {
                            port.complete(startedPort);
                        }
                    }
                } catch (IOException exception) {
                    port.completeExceptionally(exception);
                }
            }, name + "-output-reader");
            outputReader.setDaemon(true);
            outputReader.start();
        }

        static Integer parseStartedPort(String line) {
            if (line == null) {
                return null;
            }
            for (Pattern pattern : STARTED_PORT_PATTERNS) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return Integer.parseInt(matcher.group(1));
                }
            }
            return null;
        }

        private void awaitReady() throws InterruptedException {
            int startedPort = awaitPort();
            URI healthEndpoint = URI.create("http://127.0.0.1:" + startedPort + "/actuator/health");
            long deadline = System.nanoTime() + startupTimeout.toNanos();
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) {
                    throw startupFailure("进程在健康探针前退出");
                }
                try {
                    String basicCredentials = ACTUATOR_USERNAME + ':' + ACTUATOR_PASSWORD;
                    HttpRequest request = HttpRequest.newBuilder(healthEndpoint)
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            basicCredentials.getBytes(StandardCharsets.UTF_8)))
                        .GET()
                        .build();
                    int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding())
                        .statusCode();
                    if (status == 200 || status == 401 || status == 404) {
                        return;
                    }
                } catch (IOException ignored) {
                    // 端口已分配但 Web 上下文仍在初始化，继续等待。
                }
                Thread.sleep(250);
            }
            throw startupFailure("健康探针在时限内未就绪");
        }

        private int awaitPort() {
            try {
                return port.get(startupTimeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw startupFailure("等待启动端口时被中断", exception);
            } catch (ExecutionException | TimeoutException exception) {
                throw startupFailure("未从启动日志解析到 Web 服务端口", exception);
            }
        }

        private void appendLog(String line) {
            synchronized (recentLogs) {
                if (recentLogs.size() >= MAXIMUM_LOG_LINES) {
                    recentLogs.removeFirst();
                }
                recentLogs.addLast(redactSensitiveValues(line));
            }
        }

        private static List<String> sensitiveEnvironmentValues(Map<String, String> environment) {
            return environment.entrySet().stream()
                .filter(entry -> isSensitiveEnvironmentVariable(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .toList();
        }

        private static boolean isSensitiveEnvironmentVariable(String variableName) {
            if (variableName == null) {
                return false;
            }
            return variableName.matches("(?i).*(password|secret|token|key).*")
                || variableName.matches("(?i)SPRING_DATASOURCE(?:_.*)?_URL");
        }

        private String redactSensitiveValues(String line) {
            String redacted = line;
            for (String sensitiveValue : sensitiveValues) {
                redacted = redacted.replace(sensitiveValue, "***");
            }
            return redacted;
        }

        private IllegalStateException startupFailure(String message) {
            return startupFailure(message, null);
        }

        private IllegalStateException startupFailure(String message, Throwable cause) {
            List<String> logs;
            synchronized (recentLogs) {
                logs = List.copyOf(recentLogs);
            }
            return new IllegalStateException(name + " 启动失败：" + message + System.lineSeparator()
                + String.join(System.lineSeparator(), logs), cause);
        }
    }

    static final class UnstoppedExternalStarterException extends IllegalStateException {

        private UnstoppedExternalStarterException(String name, long processId, Throwable cause) {
            super(name + " 外部 starter 未能确认退出，PID=" + processId
                + "；已禁止清理 Redis，请人工处置该进程", cause);
        }
    }
}
