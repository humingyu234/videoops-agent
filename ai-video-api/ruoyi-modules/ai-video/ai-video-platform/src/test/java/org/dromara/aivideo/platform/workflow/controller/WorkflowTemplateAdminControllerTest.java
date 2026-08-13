package org.dromara.aivideo.platform.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Validation;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.CreateWorkflowTemplateBo;
import org.dromara.aivideo.platform.workflow.domain.bo.WorkflowTemplateAdminBos.ExecutionConfigBo;
import org.dromara.aivideo.platform.workflow.domain.vo.WorkflowTemplateAdminVos.ExecutionConfigVo;
import org.dromara.aivideo.platform.workflow.service.IWorkflowTemplateAdminService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowTemplateAdminControllerTest {

    @Test
    void createReturnsTemplateIdInResponseData() {
        IWorkflowTemplateAdminService service = mock(IWorkflowTemplateAdminService.class);
        CreateWorkflowTemplateBo command = mock(CreateWorkflowTemplateBo.class);
        when(service.create(command, 7L)).thenReturn("2087080581023277057");

        try (MockedStatic<LoginHelper> loginHelper = mockStatic(LoginHelper.class)) {
            loginHelper.when(LoginHelper::getUserId).thenReturn(7L);

            R<String> response = new WorkflowTemplateAdminController(service).create(command);

            assertThat(response.getData()).isEqualTo("2087080581023277057");
        }
    }

    @Test
    void exposesExactlyTenFrozenRoutesIncludingOptions() {
        Set<Route> routes = routesOf(WorkflowTemplateAdminController.class);

        assertThat(routes).containsExactlyInAnyOrder(
            route(RequestMethod.GET, "/api/admin/workflow-templates", "aivideo:workflow-template:query"),
            route(RequestMethod.POST, "/api/admin/workflow-templates", "aivideo:workflow-template:add"),
            route(RequestMethod.GET, "/api/admin/workflow-templates/{templateId}", "aivideo:workflow-template:query"),
            route(RequestMethod.PUT, "/api/admin/workflow-templates/{templateId}", "aivideo:workflow-template:edit"),
            route(RequestMethod.DELETE, "/api/admin/workflow-templates/{templateId}", "aivideo:workflow-template:remove"),
            route(RequestMethod.GET, "/api/admin/workflow-templates/{templateId}/execution-config",
                "aivideo:workflow-template:query"),
            route(RequestMethod.PUT, "/api/admin/workflow-templates/{templateId}/execution-config",
                "aivideo:workflow-template:edit"),
            route(RequestMethod.POST, "/api/admin/workflow-templates/{templateId}/enable",
                "aivideo:workflow-template:enable"),
            route(RequestMethod.POST, "/api/admin/workflow-templates/{templateId}/disable",
                "aivideo:workflow-template:disable"),
            route(RequestMethod.GET, "/api/admin/workflow-templates/options", "aivideo:workflow-template:query")
        );
        assertThat(routes).noneMatch(route -> route.path().contains("connection-tests"));
    }

    @Test
    void protectsEveryWriteWithAuditAndRepeatSubmit() {
        for (Method method : WorkflowTemplateAdminController.class.getDeclaredMethods()) {
            RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (mapping == null || Arrays.asList(mapping.method()).contains(RequestMethod.GET)) {
                continue;
            }
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Log.class))
                .as("%s must be audited", method).isNotNull();
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, RepeatSubmit.class))
                .as("%s must reject duplicate submissions", method).isNotNull();
        }

        Method configSave = method(RequestMethod.PUT, "/{templateId}/execution-config");
        Log log = AnnotatedElementUtils.findMergedAnnotation(configSave, Log.class);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    @Test
    void requestAndResponseModelsKeepSecretsAndServerFieldsOut() {
        assertThat(componentNames(CreateWorkflowTemplateBo.class))
            .doesNotContain("templateId", "status", "enabledAt", "rowRevision", "expectedRevision");
        assertThat(componentNames(ExecutionConfigVo.class)).doesNotContain("accessPassword");

        var mapper = JsonMapper.builder().build();
        var invalid = new ExecutionConfigBo(
            "201", "other_provider", null, null, null, "plain-secret", false,
            mapper.createObjectNode(), mapper.createObjectNode(), 60, true, 0L);
        assertThat(invalid.toString()).doesNotContain("plain-secret");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(invalid))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("executionMode");
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

    private static Method method(RequestMethod requestMethod, String path) {
        return Arrays.stream(WorkflowTemplateAdminController.class.getDeclaredMethods())
            .filter(candidate -> {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(candidate, RequestMapping.class);
                return mapping != null && Arrays.asList(mapping.method()).contains(requestMethod)
                    && Arrays.asList(mapping.path()).contains(path);
            })
            .findFirst()
            .orElseThrow();
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
