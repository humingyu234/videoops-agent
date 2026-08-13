package org.dromara.aivideo.user.discovery.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.user.discovery.domain.bo.DiscoveryTemplateQueryBo;
import org.dromara.aivideo.user.discovery.domain.vo.DiscoveryHomeVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowCreationConfigVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateCardVo;
import org.dromara.aivideo.user.discovery.domain.vo.WorkflowTemplateDetailVo;
import org.dromara.aivideo.user.discovery.service.IDiscoveryService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户端发现页接口。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController extends BaseController {

    private final IDiscoveryService discoveryService;

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/home")
    public R<DiscoveryHomeVo> home() {
        return R.ok(discoveryService.queryHome());
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/templates")
    public R<PageResult<WorkflowTemplateCardVo>> templates(@Valid DiscoveryTemplateQueryBo query) {
        return R.ok(discoveryService.queryTemplates(query));
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/templates/{templateId}")
    public R<WorkflowTemplateDetailVo> template(@PathVariable String templateId) {
        return R.ok(discoveryService.queryTemplate(templateId));
    }

    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    @GetMapping("/templates/{templateId}/creation-config")
    public R<WorkflowCreationConfigVo> creationConfig(@PathVariable String templateId) {
        return R.ok(discoveryService.queryCreationConfig(templateId));
    }
}
