package org.dromara.aivideo.user.voice.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.voice.service.IVoiceService;
import org.dromara.aivideo.user.voice.domain.bo.CreateVoiceBo;
import org.dromara.aivideo.user.voice.domain.bo.StartVoiceTranscriptionBo;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class VoiceControllerContractTest {
    @Test
    void uploadUsesAppPermissionAndExactRoute() {
        var method = Arrays.stream(VoiceController.class.getDeclaredMethods())
            .filter(item -> item.getName().equals("upload"))
            .findFirst().orElseThrow();
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/api/voices");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:voice:upload");
    }

    @Test
    void startTranscriptionUsesExactRoutePermissionAndRepeatSubmit() throws Exception {
        var method = VoiceController.class.getDeclaredMethod(
            "startTranscription", String.class, StartVoiceTranscriptionBo.class);

        assertThat(method.getAnnotation(PostMapping.class).value())
            .containsExactly("/api/voices/{voiceId}/transcription/start");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:voice:transcribe");
        assertThat(method.getAnnotation(RepeatSubmit.class)).isNotNull();
    }

    @Test
    void fingerprintTreatsOmittedTranscriptionRequestAsTrueAndFalseAsDistinct() throws Exception {
        VoiceController controller = new VoiceController(
            mock(IVoiceService.class), mock(IAssetService.class), mock(AppLoginHelper.class));
        var method = VoiceController.class.getDeclaredMethod("fingerprint", byte[].class, CreateVoiceBo.class);
        method.setAccessible(true);
        byte[] digest = new byte[] {1, 2, 3};
        CreateVoiceBo omitted = new CreateVoiceBo("idem", "name", null, null, List.of(), null, null);
        CreateVoiceBo explicitTrue = new CreateVoiceBo("idem", "name", null, null, List.of(), null, true);
        CreateVoiceBo explicitFalse = new CreateVoiceBo("idem", "name", null, null, List.of(), null, false);

        String omittedFingerprint = (String) method.invoke(controller, digest, omitted);
        String trueFingerprint = (String) method.invoke(controller, digest, explicitTrue);
        String falseFingerprint = (String) method.invoke(controller, digest, explicitFalse);

        assertThat(omittedFingerprint).isEqualTo(trueFingerprint).isNotEqualTo(falseFingerprint);
    }

    @Test
    void deleteUsesExactRoutePermissionAndAuditContract() throws Exception {
        var method = VoiceController.class.getDeclaredMethod("delete", String.class);

        assertThat(method.getAnnotation(DeleteMapping.class).value()).containsExactly("/api/voices/{voiceId}");
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:voice:delete");
        Log log = method.getAnnotation(Log.class);
        assertThat(log.title()).isEqualTo("用户声音");
        assertThat(log.businessType()).isEqualTo(BusinessType.DELETE);
        assertThat(method.getAnnotation(RepeatSubmit.class)).isNull();
        assertThat(method.getGenericReturnType().getTypeName()).isEqualTo("org.dromara.common.core.domain.R<java.lang.Void>");
    }

    @Test
    void deleteDelegatesOwnedDeletionWithCurrentPrincipal() {
        IVoiceService voiceService = mock(IVoiceService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(loginHelper.getPrincipal()).thenReturn(principal);
        VoiceController controller = new VoiceController(voiceService, mock(IAssetService.class), loginHelper);

        assertThat(controller.delete("9001").getCode()).isEqualTo(200);

        verify(voiceService).deleteOwnedVoice("9001", principal);
    }
}
