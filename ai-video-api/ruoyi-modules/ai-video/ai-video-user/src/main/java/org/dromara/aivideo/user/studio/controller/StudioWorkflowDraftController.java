package org.dromara.aivideo.user.studio.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.studio.draft.service.IStudioWorkflowDraftService;
import org.dromara.aivideo.user.studio.domain.bo.StudioWorkflowDraftSaveBo;
import org.dromara.aivideo.user.studio.domain.vo.StudioWorkflowDraftVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 刷新或重新登录后恢复人工工作台的最小入口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/workflow-draft/current")
public class StudioWorkflowDraftController {
    private final IStudioWorkflowDraftService service;
    private final AppLoginHelper loginHelper;

    @GetMapping
    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    public R<StudioWorkflowDraftVo> getCurrent() {
        return R.ok(StudioWorkflowDraftVo.from(service.getCurrent(loginHelper.getPrincipal())));
    }

    @PutMapping
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "studio workflow draft", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<StudioWorkflowDraftVo> save(@Valid @RequestBody StudioWorkflowDraftSaveBo body) {
        var command = new IStudioWorkflowDraftService.SaveCommand(body.expectedRevision(), body.currentStep(),
            body.schemaVersion(), body.snapshotJson());
        return R.ok(StudioWorkflowDraftVo.from(service.save(command, loginHelper.getPrincipal())));
    }

    @DeleteMapping
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "studio workflow draft", businessType = BusinessType.DELETE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<Void> clear() {
        service.clear(loginHelper.getPrincipal());
        return R.ok();
    }
}
