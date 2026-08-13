package org.dromara.aivideo.user.asset.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.asset.service.IAssetService;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.user.asset.domain.vo.WorkflowAssetAccessUrlVo;
import org.dromara.common.core.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** App-only access boundary for private workflow assets. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/assets")
public class WorkflowAssetController {

    private final IAssetService assetService;
    private final AppLoginHelper loginHelper;

    @GetMapping("/{assetId}/access-url")
    @SaCheckPermission(value = "aivideo:asset:download", type = "app")
    public R<WorkflowAssetAccessUrlVo> accessUrl(@PathVariable String assetId) {
        return R.ok(WorkflowAssetAccessUrlVo.from(
            assetService.createWorkflowAccessUrl(assetId, loginHelper.getPrincipal())));
    }
}
