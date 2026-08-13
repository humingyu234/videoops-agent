package org.dromara.aivideo.user.creation.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.creation.service.ICreationProjectService;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.creation.domain.bo.CreateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.bo.UpdateCreationProjectBo;
import org.dromara.aivideo.user.creation.domain.vo.CreationProjectVo;
import org.dromara.aivideo.user.creation.domain.vo.LatestCreationOutputVo;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/studio/creation-projects")
public class CreationProjectController {

    private final ICreationProjectService projectService;
    private final AppLoginHelper loginHelper;

    @PostMapping
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "creation project", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<CreationProjectVo> create(@Valid @RequestBody CreateCreationProjectBo body) {
        return R.ok(CreationProjectVo.from(projectService.create(actorId(),
            new ICreationProjectService.CreateProjectCommand(body.getSourceType(), body.getSourceId(),
                body.getProjectTitle(), body.getIdempotencyKey()))));
    }

    @GetMapping("/{projectId}")
    @SaCheckPermission(value = "aivideo:creation:query", type = "app")
    public R<CreationProjectVo> detail(@PathVariable String projectId) {
        return R.ok(CreationProjectVo.from(projectService.getOwned(actorId(), projectId)));
    }

    @PutMapping("/{projectId}")
    @SaCheckPermission(value = "aivideo:creation:edit", type = "app")
    @Log(title = "creation project", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<CreationProjectVo> update(@PathVariable String projectId,
                                       @Valid @RequestBody UpdateCreationProjectBo body) {
        return R.ok(CreationProjectVo.from(projectService.updateTitleOwned(actorId(), projectId,
            new ICreationProjectService.UpdateProjectTitleCommand(body.getProjectTitle()))));
    }

    @GetMapping("/{projectId}/outputs/latest")
    @SaCheckPermission(value = "aivideo:creation:query", type = "app")
    public R<LatestCreationOutputVo> latestOutput(@PathVariable String projectId) {
        return R.ok(LatestCreationOutputVo.from(projectService.getLatestOutputOwned(actorId(), projectId)));
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }
}
