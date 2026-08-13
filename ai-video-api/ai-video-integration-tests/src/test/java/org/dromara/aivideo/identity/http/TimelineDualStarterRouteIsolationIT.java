package org.dromara.aivideo.identity.http;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies that timeline routes and credentials stay in their owning starter namespace. */
@Tag("dev")
@ResourceLock("timeline-external-http-it")
class TimelineDualStarterRouteIsolationIT {

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
    void operatingStarterDoesNotExposeCreatorTimelineRoutes() throws Exception {
        for (String path : List.of(
            "/api/studio/creation-projects/1",
            "/api/studio/creation-assets",
            "/api/studio/creation-projects/1/timeline-draft",
            "/api/studio/creation-projects/1/timeline-versions",
            "/api/studio/creation-projects/1/outputs/latest",
            "/api/studio/creation-assets/1/content",
            "/api/tasks/1")) {
            HttpResponse<String> response = fixture.operatingGet(path, fixture.systemToken());
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertCode(response, 404);
        }
        HttpResponse<String> render = fixture.operatingPost(
            "/api/studio/creation-projects/1/render-tasks", fixture.systemToken(), "{}");
        assertThat(render.statusCode()).isEqualTo(200);
        assertCode(render, 404);
    }

    @Test
    void creatorStarterRejectsSystemTokenWrongClientAndMissingCredentials() throws Exception {
        String path = "/api/studio/creation-projects/1";

        HttpResponse<String> systemToken = fixture.creatorGet(
            path, fixture.systemToken(), fixture.creatorClientKey());
        assertThat(systemToken.statusCode()).isEqualTo(401);
        assertCode(systemToken, 401);

        String creatorToken = fixture.loginCreator();
        HttpResponse<String> wrongClient = fixture.creatorGet(
            path, creatorToken, fixture.creatorClientKeyB());
        assertThat(wrongClient.statusCode()).isEqualTo(401);
        assertCode(wrongClient, 46130);

        HttpResponse<String> missingToken = fixture.creatorGet(
            path, null, fixture.creatorClientKey());
        assertThat(missingToken.statusCode()).isEqualTo(400);
        assertCode(missingToken, 46132);

        HttpResponse<String> missingAllCredentials = fixture.creatorGet(path, null, null);
        assertThat(missingAllCredentials.statusCode()).isEqualTo(400);
        assertCode(missingAllCredentials, 46132);
    }

    @Test
    void creatorStarterReturnsPermissionDeniedWithoutHidingARegisteredRoute() throws Exception {
        fixture.removeSecondaryCreatorRoleBinding();
        String restrictedToken = fixture.loginCreatorB();

        HttpResponse<String> missingPermission = fixture.creatorGet(
            "/api/studio/creation-projects/1/timeline-draft", restrictedToken, fixture.creatorClientKey());
        assertThat(missingPermission.statusCode()).isEqualTo(200);
        assertCode(missingPermission, 403);

        String creatorToken = fixture.loginCreator();
        HttpResponse<String> registeredTimelineRoute = fixture.creatorGet(
            "/api/studio/creation-projects/1", creatorToken, fixture.creatorClientKey());
        assertThat(registeredTimelineRoute.statusCode()).isEqualTo(200);
        assertCode(registeredTimelineRoute, 46601);
    }

    private static void assertCode(HttpResponse<String> response, int expectedCode) {
        Matcher matcher = RESPONSE_CODE.matcher(response.body());
        assertThat(matcher.find())
            .as("response must contain code, HTTP=%s body=%s", response.statusCode(), response.body())
            .isTrue();
        assertThat(Integer.parseInt(matcher.group(1))).isEqualTo(expectedCode);
    }
}
