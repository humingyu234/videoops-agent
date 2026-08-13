package org.dromara.aivideo.user.timeline.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.task.dto.AiTaskDTO;
import org.dromara.aivideo.user.timeline.domain.bo.CreateFancyTextSuggestionTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateImagePromptTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateSubtitleAlignmentTaskBo;
import org.dromara.aivideo.user.timeline.domain.bo.CreateTimelineRenderTaskBo;
import org.dromara.aivideo.user.timeline.service.TimelineTaskApplicationService;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("dev")
class TimelineTaskControllerTest {

    @Test
    void taskCreationRoutesUseTheSingleGeneratePermissionAndSafeLogs() throws Exception {
        assertTaskRoute("createImagePrompt", CreateImagePromptTaskBo.class, "/{projectId}/image-prompt-tasks");
        assertTaskRoute("createFancyText", CreateFancyTextSuggestionTaskBo.class,
            "/{projectId}/fancy-text-suggestion-tasks");
        assertTaskRoute("createSubtitleAlignment", CreateSubtitleAlignmentTaskBo.class,
            "/{projectId}/subtitle-alignment-tasks");
        assertTaskRoute("createRender", CreateTimelineRenderTaskBo.class, "/{projectId}/render-tasks");
    }

    @Test
    void delegatesTaskCreationWithTheAuthenticatedActor() {
        TimelineTaskApplicationService service = mock(TimelineTaskApplicationService.class);
        CreateImagePromptTaskBo body = new CreateImagePromptTaskBo();
        when(service.createImagePrompt(7L, "88", body)).thenReturn(task());

        var response = new TimelineTaskController(service, login(7L)).createImagePrompt("88", body);

        assertThat(response.getData().taskId()).isEqualTo("701");
    }

    @Test
    void legacyRenderQualityFieldIsRejectedBeforeTheServiceCanBeCalled() {
        TimelineTaskApplicationService service = mock(TimelineTaskApplicationService.class);

        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("""
            {"idempotencyKey":"render-legacy-quality","expectedRevision":"1",
            "outputConfig":{"resolutionPreset":"match_canvas","frameRate":30,"quality":"high"}}
            """, CreateTimelineRenderTaskBo.class)).isInstanceOf(Exception.class);

        verifyNoInteractions(service);
    }

    private void assertTaskRoute(String name, Class<?> body, String expectedRoute) throws Exception {
        var method = TimelineTaskController.class.getDeclaredMethod(name, String.class, body);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly(expectedRoute);
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:creation:generate");
        Log log = method.getAnnotation(Log.class);
        assertThat(log.isSaveRequestData()).isFalse();
        assertThat(log.isSaveResponseData()).isFalse();
    }

    private AppLoginHelper login(long actorId) {
        AppLoginHelper helper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(principal.appUserId()).thenReturn(actorId);
        when(helper.getLoginUser()).thenReturn(new AppLoginUser(principal, "session"));
        return helper;
    }

    private AiTaskDTO task() {
        return new AiTaskDTO("701", "timeline_image_prompt_generate", "queued", "queued",
            "creation_project", "88", "88", "3", null, null, null, null,
            "2026-08-08T00:00:00Z", "2026-08-08T00:00:00Z", null, 0, true, false);
    }
}
