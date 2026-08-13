package org.dromara.aivideo.platform.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Validation;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.CreateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.UpdateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.SummaryVo;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class RunningHubAccountAdminControllerTest {

    @Test
    void exposesAccountRoutesAndReadOnlyParameterCandidateInspectionWithoutApiKeyEndpoint() {
        Set<Route> routes = routesOf(RunningHubAccountAdminController.class);

        assertThat(routes).containsExactlyInAnyOrder(
            route(RequestMethod.GET, "/api/admin/runninghub-accounts", "aivideo:runninghub-account:query"),
            route(RequestMethod.POST, "/api/admin/runninghub-accounts", "aivideo:runninghub-account:add"),
            route(RequestMethod.GET, "/api/admin/runninghub-accounts/{accountId}",
                "aivideo:runninghub-account:query"),
            route(RequestMethod.PUT, "/api/admin/runninghub-accounts/{accountId}",
                "aivideo:runninghub-account:edit"),
            route(RequestMethod.DELETE, "/api/admin/runninghub-accounts/{accountId}",
                "aivideo:runninghub-account:remove"),
            route(RequestMethod.POST, "/api/admin/runninghub-accounts/{accountId}/enable",
                "aivideo:runninghub-account:enable"),
            route(RequestMethod.POST, "/api/admin/runninghub-accounts/{accountId}/disable",
                "aivideo:runninghub-account:disable"),
            route(RequestMethod.POST, "/api/admin/runninghub-accounts/parameter-candidates",
                "aivideo:runninghub-account:query")
        );
        assertThat(routes).noneMatch(route -> route.path().contains("api-key") || route.path().endsWith("/options"));
    }

    @Test
    void protectsWritesAndNeverAuditsApiKeyBodies() {
        for (Method method : RunningHubAccountAdminController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null || Arrays.asList(mapping.method()).contains(RequestMethod.GET)) {
                continue;
            }
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Log.class)).isNotNull();
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, RepeatSubmit.class)).isNotNull();
        }

        for (RequestMethod requestMethod : Set.of(RequestMethod.POST, RequestMethod.PUT)) {
            String methodName = requestMethod == RequestMethod.POST ? "create" : "update";
            Method method = Arrays.stream(RunningHubAccountAdminController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
            Log log = AnnotatedElementUtils.findMergedAnnotation(method, Log.class);
            assertThat(log.isSaveRequestData()).isFalse();
            assertThat(log.isSaveResponseData()).isFalse();
        }
    }

    @Test
    void updateAllowsBlankApiKeyAndResponsesExposeOnlyMaskedState() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateRunningHubAccountBo("account", " "))).isNotEmpty();
            assertThat(validator.validate(new UpdateRunningHubAccountBo("account", "", 0L))).isEmpty();
        }

        assertThat(componentNames(SummaryVo.class)).doesNotContain("apiKey");
        assertThat(componentNames(DetailVo.class)).doesNotContain("apiKey");
        String json = JsonMapper.builder().build().writeValueAsString(new DetailVo(
            "201", "primary", "***1234", true, false, "unknown", null, null,
            null, 0L, LocalDateTime.of(2026, 8, 11, 10, 0), null));
        assertThat(json).contains("apiKeyMasked", "hasApiKey", "***1234")
            .doesNotContain("plain-api-key", "\"apiKey\"");
    }

    @Test
    void inspectionRequestValidatesOneRemoteIdForTheSelectedMode() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new RunningHubAccountAdminBos.ParameterCandidatesBo(
                "201", "runninghub_ai_app", null, "1937084629516193794"))).isEmpty();
            assertThat(validator.validate(new RunningHubAccountAdminBos.ParameterCandidatesBo(
                "201", "runninghub_workflow", "1980237776367083521", null))).isEmpty();
            assertThat(validator.validate(new RunningHubAccountAdminBos.ParameterCandidatesBo(
                "201", "runninghub_ai_app", "1980237776367083521", null))).isNotEmpty();
        }
    }

    private static Set<Route> routesOf(Class<?> controllerClass) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
        String basePath = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
        Set<Route> routes = new LinkedHashSet<>();
        for (Method method : controllerClass.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null) {
                continue;
            }
            SaCheckPermission permission = AnnotatedElementUtils.findMergedAnnotation(method, SaCheckPermission.class);
            assertThat(permission).as("%s must declare a sys permission", method).isNotNull();
            assertThat(permission.type()).isEmpty();
            assertThat(permission.value()).hasSize(1);
            String[] paths = mapping.path().length == 0 ? new String[]{""} : mapping.path();
            for (RequestMethod requestMethod : mapping.method()) {
                for (String path : paths) {
                    routes.add(route(requestMethod, basePath + path, permission.value()[0]));
                }
            }
        }
        return routes;
    }

    private static Set<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents()).map(RecordComponent::getName)
            .collect(java.util.stream.Collectors.toSet());
    }

    private static Route route(RequestMethod method, String path, String permission) {
        return new Route(method, path, permission);
    }

    private record Route(RequestMethod method, String path, String permission) {
    }
}
