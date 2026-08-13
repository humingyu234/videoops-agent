package org.dromara.aivideo.user.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.workflow.domain.vo.WorkflowOrderDetailVo;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderDetailDTO;
import org.dromara.aivideo.workflow.order.dto.WorkflowOrderOwnerDTO;
import org.dromara.aivideo.workflow.order.service.IWorkflowOrderService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowOrderControllerTest {

    @Test
    void exposesOwnedDetailWithTheAppTaskQueryPermission() throws Exception {
        IWorkflowOrderService service = mock(IWorkflowOrderService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        when(loginHelper.getPrincipal()).thenReturn(principal());
        when(service.queryOwnedDetail(new WorkflowOrderOwnerDTO(1L, "personal-7", 7L), "701"))
            .thenReturn(detail());
        WorkflowOrderController controller = new WorkflowOrderController(service, loginHelper);

        WorkflowOrderDetailVo result = controller.detail("701").getData();

        assertThat(result.orderId()).isEqualTo("701");
        assertThat(result.task().failureMessage()).isEqualTo("生成失败，请稍后重新制作");
        verify(service).queryOwnedDetail(new WorkflowOrderOwnerDTO(1L, "personal-7", 7L), "701");

        var method = WorkflowOrderController.class.getDeclaredMethod("detail", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/{orderId}");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:task:query");
        assertThat(method.getGenericReturnType().getTypeName()).contains(WorkflowOrderDetailVo.class.getName())
            .doesNotContain(WorkflowOrderDetailDTO.class.getName());
    }

    @Test
    void mapsAnAbsentTemplateCoverAsNull() {
        WorkflowOrderDetailDTO source = detail();
        source = new WorkflowOrderDetailDTO(source.orderId(), source.orderNo(), source.createdAt(),
            new WorkflowOrderDetailDTO.Template(source.template().templateId(), source.template().title(), null),
            source.inputs(), source.task(), source.outputs(), source.canCancel(), source.canRemake());

        WorkflowOrderDetailVo result = WorkflowOrderDetailVo.from(source);

        assertThat(result.template().cover()).isNull();
    }

    private static AppPrincipalSnapshotDTO principal() {
        return new AppPrincipalSnapshotDTO(7L, "creator", "web", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("personal-7", "personal", 1L, "app_user", 7L,
                "app_user", 7L, "personal_creator", Set.of(), 1L, null));
    }

    private static WorkflowOrderDetailDTO detail() {
        WorkflowOrderDetailDTO.Media cover = new WorkflowOrderDetailDTO.Media(
            "901", "image", "https://example.test/cover.png", null, 640, 360, "Demo");
        WorkflowOrderDetailDTO.Task task = new WorkflowOrderDetailDTO.Task(
            "702", "workflow_template_generate", "failed", "failed", 50,
            "WORKFLOW_EXECUTION_FAILED", "生成失败，请稍后重新制作", true,
            "2026-08-12T00:00:00Z", "2026-08-12T00:01:00Z");
        return new WorkflowOrderDetailDTO("701", "WF701", "2026-08-12T08:00:00",
            new WorkflowOrderDetailDTO.Template("101", "Demo", cover), List.of(), task, List.of(),
            false, true);
    }
}
