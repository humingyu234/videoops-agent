package org.dromara.aivideo.user.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskImagePromptResultPayloadDTO;
import org.dromara.aivideo.task.dto.AiTaskSummaryDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.user.task.domain.bo.AiTaskQueryBo;
import org.dromara.aivideo.user.task.domain.bo.RetryAiTaskBo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskListItemVo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskVo;
import org.dromara.aivideo.timeline.dto.TimelineImagePromptResultDTO;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class AiTaskControllerTest {

    @Test
    void usesTheUnifiedTaskRoutesAndPermissions() throws Exception {
        var list = AiTaskController.class.getDeclaredMethod("list", AiTaskQueryBo.class);
        assertThat(list.getAnnotation(GetMapping.class).value()).isEmpty();
        assertPermission(list.getAnnotation(SaCheckPermission.class), "aivideo:task:query");
        assertThat(list.getAnnotation(Log.class)).isNull();

        var detail = AiTaskController.class.getDeclaredMethod("detail", String.class);
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{taskId}");
        assertPermission(detail.getAnnotation(SaCheckPermission.class), "aivideo:task:query");

        var cancel = AiTaskController.class.getDeclaredMethod("cancel", String.class, RetryAiTaskBo.class);
        assertThat(cancel.getAnnotation(PostMapping.class).value()).containsExactly("/{taskId}/cancellations");
        assertPermission(cancel.getAnnotation(SaCheckPermission.class), "aivideo:task:cancel");
        assertSafeLog(cancel.getAnnotation(Log.class));

        var retry = AiTaskController.class.getDeclaredMethod("retry", String.class, RetryAiTaskBo.class);
        assertThat(retry.getAnnotation(PostMapping.class).value()).containsExactly("/{taskId}/retry");
        assertPermission(retry.getAnnotation(SaCheckPermission.class), "aivideo:task:retry");
        assertSafeLog(retry.getAnnotation(Log.class));
    }

    @Test
    void listsAndCancelsOnlyTheAuthenticatedActorsTasks() {
        IAiTaskService service = mock(IAiTaskService.class);
        when(service.pageOwned(any(AiTaskAccessScopeDTO.class), any(), any())).thenReturn(PageResult.build(List.of(summary()), 1L));
        RetryAiTaskBo cancellation = new RetryAiTaskBo();
        cancellation.setIdempotencyKey("cancel-key");
        when(service.requestCancellation(any(AiTaskAccessScopeDTO.class), org.mockito.Mockito.eq("701"),
            org.mockito.Mockito.eq("cancel-key"))).thenReturn(task());
        AiTaskController controller = new AiTaskController(service, login(7L));

        assertThat(controller.list(new AiTaskQueryBo()).getData().getRows()).hasSize(1);
        assertThat(controller.cancel("701", cancellation).getData().taskId()).isEqualTo("701");
        verify(service).requestCancellation(new AiTaskAccessScopeDTO(1L, 7L, "personal-7"), "701", "cancel-key");
    }

    @Test
    void usesTheOpaqueCurrentWorkspaceKeyWhenListingWorkflowTasks() {
        IAiTaskService service = mock(IAiTaskService.class);
        when(service.pageOwned(any(AiTaskAccessScopeDTO.class), any(), any()))
            .thenReturn(PageResult.build(List.of(summary()), 1L));
        AiTaskController controller = new AiTaskController(service, login(7L, "personal-7"));

        assertThat(controller.list(new AiTaskQueryBo()).getData().getRows()).hasSize(1);

        verify(service).pageOwned(eq(new AiTaskAccessScopeDTO(1L, 7L, "personal-7")), any(), any());
    }

    @Test
    void mapsInternalTaskDtosToExplicitHttpWireViews() throws Exception {
        AiTaskImagePromptResultPayloadDTO result = new AiTaskImagePromptResultPayloadDTO(
            new TimelineImagePromptResultDTO("provider-task", List.of()));
        AiTaskDTO detail = new AiTaskDTO("701", "timeline_image_prompt_generate", "success", "completed",
            "creation_project", "88", "88", "3", "501", null, null, null,
            "2026-08-08T00:00:00Z", "2026-08-08T00:00:01Z", result, 100, false, true);
        AiTaskSummaryDTO summary = new AiTaskSummaryDTO("701", "timeline_image_prompt_generate", "success",
            "completed", "creation_project", "88", "88", "2026-08-08T00:00:00Z",
            "2026-08-08T00:00:01Z", null, null, 100, false, true);

        assertThat(AiTaskVo.from(detail)).isEqualTo(new AiTaskVo("701", "timeline_image_prompt_generate",
            "creation_project", "88", "88", "3", "501", "success", "completed", 100,
            false, true, null, result, null, null, "2026-08-08T00:00:00Z", "2026-08-08T00:00:01Z"));
        assertThat(AiTaskListItemVo.from(summary)).isEqualTo(new AiTaskListItemVo("701",
            "timeline_image_prompt_generate", "creation_project", "88", "88", "success", "completed",
            100, false, true, null, null, "2026-08-08T00:00:00Z", "2026-08-08T00:00:01Z"));

        assertThat(AiTaskController.class.getDeclaredMethod("detail", String.class).getGenericReturnType()
            .getTypeName()).contains(AiTaskVo.class.getName()).doesNotContain(AiTaskDTO.class.getName());
        assertThat(AiTaskController.class.getDeclaredMethod("list", AiTaskQueryBo.class).getGenericReturnType()
            .getTypeName()).contains(AiTaskListItemVo.class.getName()).doesNotContain(AiTaskSummaryDTO.class.getName());
    }

    private void assertPermission(SaCheckPermission permission, String expected) {
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly(expected);
    }

    private void assertSafeLog(Log log) {
        assertThat(log).isNotNull();
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private AppLoginHelper login(long actorId) {
        return login(actorId, "personal-" + actorId);
    }

    private AppLoginHelper login(long actorId, String workspaceKey) {
        AppLoginHelper helper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = new AppPrincipalSnapshotDTO(actorId, "creator", "web", 1L, 1L, 1L, 1L,
            new org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO(workspaceKey, "personal", 1L,
                "app_user", actorId, "app_user", actorId, "personal_creator", java.util.Set.of(), 1L, null));
        when(helper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        return helper;
    }

    private AiTaskSummaryDTO summary() {
        return new AiTaskSummaryDTO("701", "future_type", "queued", "queued", "creation_project", "88", "88",
            "2026-08-08T00:00:00Z", "2026-08-08T00:00:00Z", null, null, 0, true, false);
    }

    private AiTaskDTO task() {
        return new AiTaskDTO("701", "future_type", "queued", "queued", "creation_project", "88", "88",
            "3", null, null, null, null, "2026-08-08T00:00:00Z", "2026-08-08T00:00:00Z",
            null, 0, true, false);
    }
}
