package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-A 双启动器 HTTP 令牌隔离集成测试。
 *
 * <p>测试模块不依赖两个 starter 的 Java 类，避免将同名的
 * {@code org.dromara.DromaraApplication} 加入同一个测试类路径；两个应用均由外部 JVM 启动。</p>
 *
 * <p>HTTP 层复用同一个已签发的原始 token 字符串跨入口调用，验证它不能跨命名空间使用。
 * 单个原始字符串同时被两个 JWT 命名空间接受的情形由 {@code AppSessionNamespaceIT} 覆盖；
 * 不能通过伪造 Redis 数据绕过真实登录签发流程。</p>
 */
@Tag("dev")
@ResourceLock("sa-token-manager")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DualTokenIsolationIT {

    private static final Pattern RESPONSE_CODE = Pattern.compile("\\\"code\\\"\\s*:\\s*(\\d+)");

    private static DualStarterHttpFixture fixture;

    @BeforeAll
    static void startExternalStarters() throws Exception {
        fixture = DualStarterHttpFixture.start();
    }

    @AfterAll
    static void stopExternalStarters() throws Exception {
        if (fixture != null) {
            fixture.close();
        }
    }

    @Test
    @Order(1)
    void protectsPersonalQuotaThroughTheRealAuthenticationPermissionAndRevisionChain() throws Exception {
        HttpResponse<String> missingCredentials = fixture.creatorGet(
            "/api/quota/account", null, null);
        assertThat(missingCredentials.statusCode()).isEqualTo(400);
        assertCode(missingCredentials, 46132);

        HttpResponse<String> unauthenticated = fixture.creatorGet(
            "/api/quota/account", "not-a-session", fixture.creatorClientKey());
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertCode(unauthenticated, 401);

        HttpResponse<String> operatingToken = fixture.creatorGet(
            "/api/quota/account", fixture.systemToken(), fixture.creatorClientKey());
        assertThat(operatingToken.statusCode()).isEqualTo(401);
        assertCode(operatingToken, 401);

        String tokenBeforePermissionMigration = fixture.loginCreator();
        HttpResponse<String> permissionDenied = fixture.creatorGet(
            "/api/quota/account", tokenBeforePermissionMigration, fixture.creatorClientKey());
        assertThat(permissionDenied.statusCode()).isEqualTo(200);
        assertCode(permissionDenied, 403);

        fixture.applyPersonalQuotaMigrationsAndSeedAccount();

        HttpResponse<String> staleSession = fixture.creatorGet(
            "/api/quota/account", tokenBeforePermissionMigration, fixture.creatorClientKey());
        assertThat(staleSession.statusCode()).isEqualTo(401);
        assertCode(staleSession, 46131);

        String refreshedCreatorToken = fixture.loginCreator();
        HttpResponse<String> personalQuota = fixture.creatorGet(
            "/api/quota/account", refreshedCreatorToken, fixture.creatorClientKey());
        assertThat(personalQuota.statusCode()).isEqualTo(200);
        assertCode(personalQuota, 200);
        assertThat(personalQuota.body())
            .contains("\"quotaUnit\":\"ai_text_credit\"")
            .contains("\"availableBalance\":\"20000\"")
            .contains("\"lockedBalance\":\"0\"")
            .contains("\"usedBalance\":\"0\"")
            .contains("\"totalBalance\":\"20000\"");
    }

    @Test
    @Order(2)
    void separatesCreatorAndOperatingTokensAtBothHttpEntrypoints() throws Exception {
        String creatorToken = fixture.loginCreator();
        String systemToken = fixture.systemToken();

        assertCode(fixture.creatorGet("/api/auth/me", creatorToken, fixture.creatorClientKey()), 200);
        assertCode(fixture.creatorGet("/api/auth/me", systemToken, fixture.creatorClientKey()), 401);

        assertCode(fixture.operatingGet("/api/admin/app-users", creatorToken), 401);
        assertCode(fixture.operatingGet("/system/user/list", creatorToken), 401);
        assertCode(fixture.operatingGet("/api/admin/app-users", systemToken), 200);
        assertCode(fixture.operatingGet("/system/user/list", systemToken), 200);
    }

    @Test
    @Order(3)
    void rejectsSwappedClientAndRepeatedAuthorizationHeadersAtBothEntrypoints() throws Exception {
        String creatorToken = fixture.loginCreator();
        String systemToken = fixture.systemToken();

        HttpResponse<String> swappedClient = fixture.creatorGet(
            "/api/auth/me", creatorToken, fixture.creatorClientKeyB());
        assertThat(swappedClient.statusCode()).isEqualTo(401);
        assertCode(swappedClient, 46130);

        HttpResponse<String> repeatedCreatorAuthorization = fixture.creatorGetWithRepeatedAuthorization(
            "/api/auth/me", creatorToken, fixture.creatorClientKey());
        assertThat(repeatedCreatorAuthorization.statusCode()).isEqualTo(400);
        assertCode(repeatedCreatorAuthorization, 46132);

        HttpResponse<String> repeatedOperatingAuthorization = fixture.operatingGetWithRepeatedAuthorization(
            "/api/admin/app-users", systemToken);
        assertThat(repeatedOperatingAuthorization.statusCode()).isEqualTo(400);
        assertCode(repeatedOperatingAuthorization, 46132);

        HttpResponse<String> mixedCreatorCredential = fixture.creatorGetWithTokenParameter(
            "/api/auth/me", creatorToken, fixture.creatorClientKey());
        assertThat(mixedCreatorCredential.statusCode()).isEqualTo(400);
        assertCode(mixedCreatorCredential, 46132);

        HttpResponse<String> mixedOperatingCredential = fixture.operatingGetWithTokenParameter(
            "/api/admin/app-users", systemToken);
        assertThat(mixedOperatingCredential.statusCode()).isEqualTo(400);
        assertCode(mixedOperatingCredential, 46132);
    }

    @Test
    @Order(4)
    void rejectsWrongGrantPathAndIpPoliciesWithoutFallingBackToSystemClient() throws Exception {
        String creatorToken = fixture.loginCreator();

        HttpResponse<String> systemClientFallback = fixture.creatorGet(
            "/api/auth/me", creatorToken, fixture.operatingClientId());
        assertThat(systemClientFallback.statusCode()).isEqualTo(401);
        assertCode(systemClientFallback, 46130);
        assertNoCredentialOrPolicyDetails(systemClientFallback, fixture.operatingClientId());

        HttpResponse<String> wrongGrant = fixture.loginCreator(fixture.wrongGrantClientKey());
        assertThat(wrongGrant.statusCode()).isEqualTo(401);
        assertCode(wrongGrant, 46130);
        assertNoCredentialOrPolicyDetails(wrongGrant, fixture.wrongGrantClientKey(), "authorization_code");

        HttpResponse<String> ipRestricted = fixture.loginCreator(fixture.ipRestrictedClientKey());
        assertThat(ipRestricted.statusCode()).isEqualTo(401);
        assertCode(ipRestricted, 46130);
        assertNoCredentialOrPolicyDetails(ipRestricted, fixture.ipRestrictedClientKey(), "203.0.113.1");

        String pathRestrictedToken = fixture.loginCreatorAndExtractToken(fixture.pathRestrictedClientKey());
        HttpResponse<String> pathRestricted = fixture.creatorGet(
            "/api/auth/me", pathRestrictedToken, fixture.pathRestrictedClientKey());
        assertThat(pathRestricted.statusCode()).isEqualTo(401);
        assertCode(pathRestricted, 46130);
        assertNoCredentialOrPolicyDetails(pathRestricted, fixture.pathRestrictedClientKey(), "/api/auth/login");
    }

    @Test
    @Order(5)
    void creatorLogoutKickoutAndPasswordResetDoNotAffectSameNumericSystemAccount() throws Exception {
        String systemToken = fixture.systemToken();

        String logoutToken = fixture.loginCreator();
        assertCode(fixture.creatorPost("/api/auth/logout", logoutToken, ""), 200);
        assertCode(fixture.creatorGet("/api/auth/me", logoutToken, fixture.creatorClientKey()), 401);
        assertCode(fixture.operatingGet("/system/user/list", systemToken), 200);

        String kickoutToken = fixture.loginCreator();
        assertCode(fixture.operatingPost("/api/admin/app-users/" + fixture.sharedNumericUserId() + "/kickouts",
            systemToken, "{\"reasonCode\":\"admin_kickout\"}"), 200);
        assertCode(fixture.creatorGet("/api/auth/me", kickoutToken, fixture.creatorClientKey()), 401);
        assertCode(fixture.operatingGet("/system/user/list", systemToken), 200);

        String resetToken = fixture.loginCreator();
        assertCode(fixture.operatingPost("/api/admin/app-users/" + fixture.sharedNumericUserId()
            + "/password-resets", systemToken, "{\"expectedCredentialRevision\":1}"), 200);
        assertCode(fixture.creatorGet("/api/auth/me", resetToken, fixture.creatorClientKey()), 401);
        assertCode(fixture.operatingGet("/system/user/list", systemToken), 200);
    }

    private static void assertCode(HttpResponse<String> response, int expectedCode) {
        Matcher matcher = RESPONSE_CODE.matcher(response.body());
        assertThat(matcher.find())
            .as("HTTP 响应必须包含 code 字段，状态=%s，响应=%s", response.statusCode(), response.body())
            .isTrue();
        assertThat(Integer.parseInt(matcher.group(1)))
            .as("HTTP 响应业务码，状态=%s，响应=%s", response.statusCode(), response.body())
            .isEqualTo(expectedCode);
    }

    /**
     * 客户端策略拒绝只能返回稳定的通用错误，不能泄露令牌、客户端键或触发拒绝的策略规则。
     */
    private static void assertNoCredentialOrPolicyDetails(HttpResponse<String> response, String... policyDetails) {
        assertThat(response.body()).doesNotContain("\"access_token\"");
        assertThat(response.body()).doesNotContain(policyDetails);
    }
}
