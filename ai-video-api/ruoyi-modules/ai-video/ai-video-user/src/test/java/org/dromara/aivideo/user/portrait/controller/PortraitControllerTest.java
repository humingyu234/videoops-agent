package org.dromara.aivideo.user.portrait.controller;

import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.asset.dto.AssetDTO;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.AppLoginUser;
import org.dromara.aivideo.portrait.service.IPortraitService;
import org.dromara.aivideo.user.portrait.domain.bo.CreatePortraitBo;
import org.dromara.aivideo.user.portrait.domain.bo.UpdatePortraitBo;
import org.dromara.aivideo.user.security.AppSecurityExceptionHandler;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
class PortraitControllerTest {

    @Test
    void usesBusinessIdempotencyInsteadOfGenericRepeatSubmitForUploadCreateAndDelete() throws Exception {
        assertThat(PortraitController.class.getMethod("upload", MultipartFile.class)
            .isAnnotationPresent(RepeatSubmit.class)).isFalse();
        assertThat(PortraitController.class.getMethod("create", CreatePortraitBo.class)
            .isAnnotationPresent(RepeatSubmit.class)).isFalse();
        assertThat(PortraitController.class.getMethod("delete", String.class, String.class)
            .isAnnotationPresent(RepeatSubmit.class)).isFalse();
        assertThat(PortraitController.class.getMethod("update", String.class, UpdatePortraitBo.class)
            .isAnnotationPresent(RepeatSubmit.class)).isTrue();
    }

    @Test
    void derivesListScopeFromCurrentAppSession() throws Exception {
        IPortraitService portraitService = mock(IPortraitService.class);
        IAssetService assetService = mock(IAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = principal();
        when(loginHelper.getPrincipal()).thenReturn(principal);
        when(portraitService.queryPage(any(), eq(principal), any())).thenReturn(PageResult.build(List.of(), 0));
        MockMvc mvc = mvc(portraitService, assetService, loginHelper);

        mvc.perform(get("/api/portraits").param("keyword", "主播"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        verify(portraitService).queryPage(any(), eq(principal), any());
    }

    @Test
    void uploadsExactlyOneImageWithoutCallerControlledOwnership() throws Exception {
        IPortraitService portraitService = mock(IPortraitService.class);
        IAssetService assetService = mock(IAssetService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = principal();
        when(loginHelper.getPrincipal()).thenReturn(principal);
        when(assetService.uploadPortraitImage(any(), eq(principal)))
            .thenReturn(new AssetDTO("9001", "ready", null, "portrait.png", "image/png",
                "png", 100, 100, 1024L, null));
        MockMvc mvc = mvc(portraitService, assetService, loginHelper);
        MockMultipartFile file = new MockMultipartFile("file", "portrait.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/assets/uploads/portrait-images").file(file)
                .param("ownerId", "9999").param("tenantId", "9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assetId").value("9001"));

        verify(assetService).uploadPortraitImage(any(), eq(principal));
    }

    private MockMvc mvc(IPortraitService portraitService, IAssetService assetService, AppLoginHelper loginHelper) {
        return MockMvcBuilders.standaloneSetup(new PortraitController(portraitService, assetService, loginHelper))
            .setControllerAdvice(new AppSecurityExceptionHandler()).build();
    }

    private AppPrincipalSnapshotDTO principal() {
        AppWorkspaceSessionSnapshotDTO workspace = new AppWorkspaceSessionSnapshotDTO(
            "personal-1001", "personal", 2001L, "app_user", 1001L,
            "app_user", 1001L, "personal_creator",
            Set.of("aivideo:portrait:query", "aivideo:portrait:add"), 1L, null);
        return new AppPrincipalSnapshotDTO(1001L, "creator", "creator-web", 1L, 1L, 1L, 1L, workspace);
    }
}
