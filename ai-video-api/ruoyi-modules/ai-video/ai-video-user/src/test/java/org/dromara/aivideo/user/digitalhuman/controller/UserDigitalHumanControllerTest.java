package org.dromara.aivideo.user.digitalhuman.controller;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStage;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobStatus;
import org.dromara.aivideo.digitalhuman.domain.DigitalHumanJobType;
import org.dromara.aivideo.digitalhuman.dto.CreateVoiceGenerationJobDTO;
import org.dromara.aivideo.digitalhuman.dto.DigitalHumanJobDTO;
import org.dromara.aivideo.digitalhuman.service.IDigitalHumanGenerationService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.dto.AppWorkspaceSessionSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVideoJobBo;
import org.dromara.aivideo.user.digitalhuman.domain.bo.CreateVoiceJobBo;
import org.dromara.aivideo.user.digitalhuman.domain.vo.DigitalHumanJobVo;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("dev")
@ResourceLock("sa-token-manager")
class UserDigitalHumanControllerTest {

    @Test
    void declaresAppPermissionsForEveryDigitalHumanEndpoint() throws Exception {
        assertPermission("createVoiceJob", "aivideo:studio:generate",
            String.class, CreateVoiceJobBo.class, MultipartHttpServletRequest.class);
        assertPermission("confirmVoiceJob", "aivideo:studio:generate", Long.class);
        assertPermission("createVideoJob", "aivideo:studio:generate",
            String.class, CreateVideoJobBo.class, MultipartHttpServletRequest.class);
        assertPermission("getJob", "aivideo:studio:query", Long.class);
        assertPermission("getMedia", "aivideo:studio:query", Long.class);
    }

    @Test
    void exposesIdentifiersAsDecimalStrings() {
        DigitalHumanJobVo value = DigitalHumanJobVo.from(new DigitalHumanJobDTO(
            9002L, 9001L, DigitalHumanJobType.VIDEO_GENERATE, DigitalHumanJobStatus.QUEUED,
            DigitalHumanJobStage.VIDEO_SUBMITTED, 0, false, false, null));

        assertThat((Object) value.jobId()).isInstanceOf(String.class);
        assertThat((Object) value.parentJobId()).isInstanceOf(String.class);
    }

    @Test
    void createsVoiceJobUsingOnlyCurrentSessionOwnership() throws Exception {
        IDigitalHumanGenerationService service = mock(IDigitalHumanGenerationService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        when(loginHelper.getPrincipal()).thenReturn(principal());
        when(service.createVoiceJob(org.mockito.ArgumentMatchers.any())).thenReturn(new DigitalHumanJobDTO(
            9001L, null, DigitalHumanJobType.VOICE_GENERATE, DigitalHumanJobStatus.QUEUED,
            DigitalHumanJobStage.QUEUED, 0, false, false, null));
        StpLogic previous = installAppLogic(appLogic);
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserDigitalHumanController(service, loginHelper))
                .addInterceptors(new SaInterceptor())
                .build();

            mvc.perform(multipart("/api/studio/voice-jobs")
                    .file(new MockMultipartFile("referenceAudio", "reference.wav", "audio/wav", new byte[]{1, 2, 3}))
                    .param("scriptText", "公开测试文案")
                    .header("Idempotency-Key", "voice-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value("9001"))
                .andExpect(jsonPath("$.data.status").value("queued"));

            verify(appLogic).checkPermissionAnd("aivideo:studio:generate");
            ArgumentCaptor<CreateVoiceGenerationJobDTO> captor =
                ArgumentCaptor.forClass(CreateVoiceGenerationJobDTO.class);
            verify(service).createVoiceJob(captor.capture());
            assertThat(captor.getValue().owner().tenantId()).isEqualTo(2001L);
            assertThat(captor.getValue().owner().userId()).isEqualTo(1001L);
        } finally {
            restoreAppLogic(previous);
        }
    }

    @Test
    void rejectsUnknownVoiceMultipartFields() {
        IDigitalHumanGenerationService service = mock(IDigitalHumanGenerationService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        when(loginHelper.getPrincipal()).thenReturn(principal());
        when(service.createVoiceJob(org.mockito.ArgumentMatchers.any())).thenReturn(new DigitalHumanJobDTO(
            9001L, null, DigitalHumanJobType.VOICE_GENERATE, DigitalHumanJobStatus.QUEUED,
            DigitalHumanJobStage.QUEUED, 0, false, false, null));
        StpLogic previous = installAppLogic(appLogic);
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserDigitalHumanController(service, loginHelper))
                .addInterceptors(new SaInterceptor())
                .build();

            assertThatThrownBy(() -> mvc.perform(multipart("/api/studio/voice-jobs")
                    .file(new MockMultipartFile(
                        "referenceAudio", "reference.wav", "audio/wav", new byte[]{1, 2, 3}))
                    .param("scriptText", "公开测试文案")
                    .param("tenantId", "9999")
                    .header("Idempotency-Key", "voice-1")))
                .hasRootCauseInstanceOf(ServiceException.class);

            verifyNoInteractions(service);
        } finally {
            restoreAppLogic(previous);
        }
    }

    @Test
    void rejectsDuplicateVideoMultipartFields() {
        IDigitalHumanGenerationService service = mock(IDigitalHumanGenerationService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        when(loginHelper.getPrincipal()).thenReturn(principal());
        when(service.createVideoJob(org.mockito.ArgumentMatchers.any())).thenReturn(new DigitalHumanJobDTO(
            9002L, 9001L, DigitalHumanJobType.VIDEO_GENERATE, DigitalHumanJobStatus.QUEUED,
            DigitalHumanJobStage.VIDEO_SUBMITTED, 0, false, false, null));
        StpLogic previous = installAppLogic(appLogic);
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserDigitalHumanController(service, loginHelper))
                .addInterceptors(new SaInterceptor())
                .build();

            assertThatThrownBy(() -> mvc.perform(multipart("/api/studio/video-jobs")
                    .file(new MockMultipartFile(
                        "portraitImage", "portrait.png", "image/png", new byte[]{1, 2, 3}))
                    .param("voiceJobId", "9001", "9002")
                    .header("Idempotency-Key", "video-1")))
                .hasRootCauseInstanceOf(ServiceException.class);

            verifyNoInteractions(service);
        } finally {
            restoreAppLogic(previous);
        }
    }

    @Test
    void rejectsMissingAppPermissionBeforeResolvingOwnerOrCallingService() {
        IDigitalHumanGenerationService service = mock(IDigitalHumanGenerationService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        StpLogic appLogic = mock(StpLogic.class);
        when(appLogic.getLoginType()).thenReturn("app");
        doThrow(new NotPermissionException("aivideo:studio:query", "app"))
            .when(appLogic).checkPermissionAnd("aivideo:studio:query");
        StpLogic previous = installAppLogic(appLogic);
        try {
            MockMvc mvc = MockMvcBuilders.standaloneSetup(new UserDigitalHumanController(service, loginHelper))
                .addInterceptors(new SaInterceptor())
                .build();

            assertThatThrownBy(() -> mvc.perform(get("/api/studio/jobs/{jobId}", 9001L)))
                .hasRootCauseInstanceOf(NotPermissionException.class);
            verify(appLogic).checkPermissionAnd("aivideo:studio:query");
            verifyNoInteractions(service, loginHelper);
        } finally {
            restoreAppLogic(previous);
        }
    }

    private static AppPrincipalSnapshotDTO principal() {
        return new AppPrincipalSnapshotDTO(1001L, "creator", "desktop", 1L, 1L, 1L, 1L,
            new AppWorkspaceSessionSnapshotDTO("personal", "personal", 2001L, "app_user", 1001L,
                "app_user", 1001L, "personal_creator", Set.of(), 1L, null));
    }

    private static void assertPermission(String methodName, String expected, Class<?>... parameterTypes)
        throws NoSuchMethodException {
        SaCheckPermission permission = AnnotatedElementUtils.findMergedAnnotation(
            UserDigitalHumanController.class.getDeclaredMethod(methodName, parameterTypes), SaCheckPermission.class);

        assertThat(permission).as("%s 必须声明 app 权限", methodName).isNotNull();
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly(expected);
    }

    private static StpLogic installAppLogic(StpLogic logic) {
        StpLogic previous = null;
        try {
            previous = SaManager.getStpLogic("app", false);
        } catch (SaTokenException ignored) {
            // 当前定向测试进程尚未注册 app logic。
        }
        SaManager.putStpLogic(logic);
        return previous;
    }

    private static void restoreAppLogic(StpLogic previous) {
        SaManager.removeStpLogic("app");
        if (previous != null) {
            SaManager.putStpLogic(previous);
        }
    }
}
