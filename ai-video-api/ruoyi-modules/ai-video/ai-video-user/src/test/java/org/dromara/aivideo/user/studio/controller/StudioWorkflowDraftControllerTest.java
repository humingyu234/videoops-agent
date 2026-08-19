package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.studio.draft.dto.StudioWorkflowDraftDTO;
import org.dromara.aivideo.studio.draft.service.IStudioWorkflowDraftService;
import org.dromara.aivideo.user.studio.domain.bo.StudioWorkflowDraftSaveBo;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class StudioWorkflowDraftControllerTest {

    @Test
    void exposesOnlyCurrentOwnerRoutesWithAppPermissionsAndSafeLogs() throws Exception {
        var get = StudioWorkflowDraftController.class.getDeclaredMethod("getCurrent");
        assertPermission(get.getAnnotation(SaCheckPermission.class), "aivideo:studio:query");

        var save = StudioWorkflowDraftController.class.getDeclaredMethod("save", StudioWorkflowDraftSaveBo.class);
        assertPermission(save.getAnnotation(SaCheckPermission.class), "aivideo:studio:generate");
        assertSafeLog(save.getAnnotation(Log.class));

        var clear = StudioWorkflowDraftController.class.getDeclaredMethod("clear");
        assertPermission(clear.getAnnotation(SaCheckPermission.class), "aivideo:studio:generate");
        assertSafeLog(clear.getAnnotation(Log.class));
    }

    @Test
    void delegatesUsingOnlyAuthenticatedPrincipal() {
        IStudioWorkflowDraftService service = mock(IStudioWorkflowDraftService.class);
        AppLoginHelper login = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(login.getPrincipal()).thenReturn(principal);
        when(service.save(any(), any())).thenReturn(new StudioWorkflowDraftDTO(
            "2", 3, "studio-workflow-1", "{}", LocalDateTime.MIN));
        StudioWorkflowDraftSaveBo body = new StudioWorkflowDraftSaveBo(
            1, 3, "studio-workflow-1", "{}");

        new StudioWorkflowDraftController(service, login).save(body);

        verify(service).save(new IStudioWorkflowDraftService.SaveCommand(
            1, 3, "studio-workflow-1", "{}"), principal);
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
}
