package org.dromara.aivideo.user.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.dromara.aivideo.asset.dto.CreateUploadSessionDTO;
import org.dromara.aivideo.asset.dto.UploadSessionDTO;
import org.dromara.aivideo.asset.service.IFileUploadService;
import org.dromara.aivideo.identity.dto.AppPrincipalSnapshotDTO;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.asset.domain.bo.WorkflowUploadSessionBo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("dev")
class WorkflowUploadControllerTest {

    @Test
    void usesOnlyAppUploadPermissionAndDoesNotExposeProviderOrObjectStorageFields() throws Exception {
        Method create = WorkflowUploadController.class.getMethod("create", WorkflowUploadSessionBo.class);
        Method transferContent = WorkflowUploadController.class.getMethod("transferContent", String.class,
            HttpServletRequest.class);
        Method complete = WorkflowUploadController.class.getMethod("complete", String.class);
        assertPermission(create);
        assertPermission(transferContent);
        assertPermission(complete);
        assertThat(create.getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(transferContent.getAnnotation(PutMapping.class).value()).containsExactly("/{uploadId}/content");
        assertThat(WorkflowUploadController.class.getDeclaredFields()).noneMatch(field ->
            field.getName().contains("runningHub") || field.getName().contains("objectStorage"));
    }

    @Test
    void createsUploadForAuthenticatedPrincipalOnlyAndReturnsNoObjectKey() {
        IFileUploadService service = mock(IFileUploadService.class);
        AppLoginHelper loginHelper = mock(AppLoginHelper.class);
        AppPrincipalSnapshotDTO principal = mock(AppPrincipalSnapshotDTO.class);
        when(loginHelper.getPrincipal()).thenReturn(principal);
        when(service.createWorkflowInputSession(any(CreateUploadSessionDTO.class), any(AppPrincipalSnapshotDTO.class)))
            .thenReturn(new UploadSessionDTO("11", null, "single", "initialized", LocalDateTime.now(),
                null, Map.of(), null, null));
        WorkflowUploadSessionBo body = new WorkflowUploadSessionBo();
        body.setPurpose("workflow_input");
        body.setTemplateId("101");
        body.setSchemaHash("schema-1");
        body.setInputKey("promptImage");
        body.setFileName("hero.png");
        body.setDeclaredContentType("image/png");
        body.setSizeBytes(1024L);
        body.setIdempotencyKey("upload-1");

        var response = new WorkflowUploadController(service, loginHelper).create(body);

        assertThat(response.getData().uploadId()).isEqualTo("11");
        assertThat(response.getData().singlePutUrl()).isEqualTo("/api/assets/uploads/11/content");
        assertThat(response.getData().getClass().getRecordComponents()).extracting(component -> component.getName())
            .doesNotContain("fileId", "objectKey", "provider", "executionMode");
        verify(service).createWorkflowInputSession(new CreateUploadSessionDTO(
            "101", "schema-1", "promptImage", "hero.png", "image/png", 1024L, "upload-1"), principal);
    }

    @Test
    void rejectsForgedOwnershipAndProviderFields() {
        assertThatThrownBy(() -> JsonMapper.builder().build().readValue("""
            {"purpose":"workflow_input","templateId":"101","schemaHash":"schema-1","inputKey":"promptImage",
             "fileName":"hero.png","declaredContentType":"image/png","sizeBytes":1024,"idempotencyKey":"upload-1",
             "ownerUserId":"9","provider":"self_hosted_comfyui"}
            """, WorkflowUploadSessionBo.class)).isInstanceOf(Exception.class);
    }

    private void assertPermission(Method method) {
        SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.type()).isEqualTo("app");
        assertThat(permission.value()).containsExactly("aivideo:asset:upload");
    }
}
