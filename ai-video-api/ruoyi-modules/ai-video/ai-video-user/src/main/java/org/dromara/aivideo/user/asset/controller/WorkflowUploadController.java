package org.dromara.aivideo.user.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.dto.CompleteUploadDTO;
import org.dromara.aivideo.asset.dto.CreateUploadSessionDTO;
import org.dromara.aivideo.asset.service.IFileUploadService;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.asset.domain.bo.WorkflowUploadSessionBo;
import org.dromara.aivideo.user.asset.domain.vo.WorkflowUploadSessionVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.exception.ServiceException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets/uploads")
public class WorkflowUploadController {

    private static final String WORKFLOW_INPUT_PURPOSE = "workflow_input";

    private final IFileUploadService uploadService;
    private final AppLoginHelper loginHelper;

    @PostMapping
    @SaCheckPermission(value = "aivideo:asset:upload", type = "app")
    public R<WorkflowUploadSessionVo> create(@Valid @RequestBody WorkflowUploadSessionBo body) {
        if (!WORKFLOW_INPUT_PURPOSE.equals(body.getPurpose())) {
            throw new ServiceException("不支持的上传用途", 46211);
        }
        var session = uploadService.createWorkflowInputSession(
            new CreateUploadSessionDTO(body.getTemplateId(), body.getSchemaHash(), body.getInputKey(),
                body.getFileName(), body.getDeclaredContentType(), body.getSizeBytes(), body.getIdempotencyKey()),
            loginHelper.getPrincipal());
        return R.ok(WorkflowUploadSessionVo.from(session, "/api/assets/uploads/" + session.uploadId() + "/content"));
    }

    @PutMapping("/{uploadId}/content")
    @SaCheckPermission(value = "aivideo:asset:upload", type = "app")
    public R<WorkflowUploadSessionVo> transferContent(@PathVariable String uploadId, HttpServletRequest request) {
        return R.ok(WorkflowUploadSessionVo.from(uploadService.transferWorkflowInputContent(uploadId,
            request.getContentType(), request.getContentLengthLong(), requestInput(request), loginHelper.getPrincipal())));
    }

    @PostMapping("/{uploadId}/complete")
    @SaCheckPermission(value = "aivideo:asset:upload", type = "app")
    public R<WorkflowUploadSessionVo> complete(@PathVariable String uploadId) {
        return R.ok(WorkflowUploadSessionVo.from(uploadService.completeWorkflowInputSession(uploadId,
            new CompleteUploadDTO("single"), loginHelper.getPrincipal())));
    }

    private java.io.InputStream requestInput(HttpServletRequest request) {
        try {
            return request.getInputStream();
        } catch (java.io.IOException exception) {
            throw new ServiceException("读取上传内容失败", 46211);
        }
    }
}
