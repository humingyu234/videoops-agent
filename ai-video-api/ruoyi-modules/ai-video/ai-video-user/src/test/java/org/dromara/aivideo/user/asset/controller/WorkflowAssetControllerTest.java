package org.dromara.aivideo.user.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.asset.dto.AssetAccessUrlDTO;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowAssetControllerTest {

    @Test
    void exposesOnlyTheAppDownloadBoundaryForOwnedWorkflowResults() throws Exception {
        IAssetService assetService = mock(IAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 8, 12, 10, 0);
        when(loginHelper.getPrincipal()).thenReturn(principal);
        when(assetService.createWorkflowAccessUrl("92", principal)).thenReturn(
            new AssetAccessUrlDTO("https://private.example.test/output.png", expiresAt, "image/png"));

        var result = new WorkflowAssetController(assetService, loginHelper).accessUrl("92").getData();

        assertThat(result.url()).isEqualTo("https://private.example.test/output.png");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
        verify(assetService).createWorkflowAccessUrl("92", principal);
        var method = WorkflowAssetController.class.getMethod("accessUrl", String.class);
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly("/{assetId}/access-url");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:asset:download");
    }
}
