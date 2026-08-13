package org.dromara.aivideo.user.creation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.creation.dto.CreationOutputDTO;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.user.creation.domain.bo.CreateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.bo.UpdateCreationProjectBo;
import org.dromara.common.log.annotation.Log;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class CreationProjectControllerTest {

    @Test
    void routesUseOwnerSafeAppPermissionsAndSafeMutationLogs() throws Exception {
        var create = CreationProjectController.class.getDeclaredMethod("create", CreateCreationProjectBo.class);
        assertPermission(create.getAnnotation(SaCheckPermission.class), "aivideo:creation:edit");
        assertSafeMutationLog(create.getAnnotation(Log.class));
        assertThat(create.getAnnotation(PostMapping.class).value()).isEmpty();

        var detail = CreationProjectController.class.getDeclaredMethod("detail", String.class);
        assertPermission(detail.getAnnotation(SaCheckPermission.class), "aivideo:creation:query");
        assertThat(detail.getAnnotation(Log.class)).isNull();
        assertThat(detail.getAnnotation(GetMapping.class).value()).containsExactly("/{projectId}");

        var update = CreationProjectController.class.getDeclaredMethod("update", String.class, UpdateCreationProjectBo.class);
        assertPermission(update.getAnnotation(SaCheckPermission.class), "aivideo:creation:edit");
        assertSafeMutationLog(update.getAnnotation(Log.class));
    }

    @Test
    void createsProjectForTheAuthenticatedActorOnly() {
        ICreationProjectService service = mock(ICreationProjectService.class);
        AppLoginHelper login = login(7L);
        CreateCreationProjectBo body = new CreateCreationProjectBo();
        body.setSourceType("digital_human_job");
        body.setSourceId("44");
        body.setProjectTitle("demo");
        body.setIdempotencyKey("project-key");
        when(service.create(any(Long.class), any(ICreationProjectService.CreateProjectCommand.class)))
            .thenReturn(project());

        new CreationProjectController(service, login).create(body);

        verify(service).create(7L, new ICreationProjectService.CreateProjectCommand(
            "digital_human_job", "44", "demo", "project-key"));
    }

    @Test
    void latestOutputMapsTheDedicatedCoreDtoToTheExactC2WireShape() throws Exception {
        ICreationProjectService service = mock(ICreationProjectService.class);
        when(service.getLatestOutputOwned(7L, "88")).thenReturn(output());

        var response = new CreationProjectController(service, login(7L)).latestOutput("88");

        assertThat(response.getData().projectId()).isEqualTo("88");
        assertThat(response.getData().outputAssetId()).isEqualTo("99");
        assertThat(response.getData().taskId()).isEqualTo("701");
        assertThat(response.getData().createdAt()).isEqualTo(Instant.EPOCH.toString());
        String serialized = JsonMapper.builder().build().writeValueAsString(response.getData());
        assertThat(serialized).contains("projectId", "outputAssetId", "taskId", "createdAt")
            .doesNotContain("assetId", "mimeType", "sizeBytes", "previewUrl", "downloadUrl");
        verify(service).getLatestOutputOwned(7L, "88");
    }

    @Test
    void strictProjectRequestRejectsForgedOwnershipFields() {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("""
            {"sourceType":"digital_human_job","sourceId":"44","idempotencyKey":"k","ownerUserId":"7"}
            """, CreateCreationProjectBo.class)).isInstanceOf(Exception.class);
    }

    private void assertPermission(SaCheckPermission permission, String expected) {
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly(expected);
    }

    private void assertSafeMutationLog(Log log) {
        assertThat(log).isNotNull();
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

    private ICreationProjectService.CreationProjectDTO project() {
        return new ICreationProjectService.CreationProjectDTO("88", "demo", "digital_human_job", "44", "55",
            "66", "editing", 1080, 1920, 30, 1_000L, 1L, "timeline-1", null, Instant.EPOCH, Instant.EPOCH);
    }

    private CreationOutputDTO output() {
        return new CreationOutputDTO("88", "99", "701", Instant.EPOCH);
    }
}
