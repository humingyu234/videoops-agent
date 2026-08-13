package org.dromara.aivideo.platform.workflow.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.CreateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.RunningHubAccountQueryBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.StatusChangeBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.UpdateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.ParameterCandidatesBo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.SummaryVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.ParameterCandidatesVo;
import org.dromara.aivideo.platform.workflow.service.IRunningHubAccountAdminService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.redis.annotation.RepeatSubmit;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.web.core.BaseController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 运营端 RunningHub 账号管理入口。 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/runninghub-accounts")
public class RunningHubAccountAdminController extends BaseController {

    private final IRunningHubAccountAdminService runningHubAccountAdminService;

    @SaCheckPermission("aivideo:runninghub-account:query")
    @GetMapping("")
    public R<PageResult<SummaryVo>> page(@Valid RunningHubAccountQueryBo query, PageQuery pageQuery) {
        return R.ok(runningHubAccountAdminService.page(query, pageQuery));
    }

    @SaCheckPermission("aivideo:runninghub-account:add")
    @Log(title = "RunningHub 账号管理", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    @RepeatSubmit
    @PostMapping("")
    public R<String> create(@Valid @RequestBody CreateRunningHubAccountBo command) {
        return R.ok(runningHubAccountAdminService.create(command, LoginHelper.getUserId()));
    }

    @SaCheckPermission("aivideo:runninghub-account:query")
    @GetMapping("/{accountId}")
    public R<DetailVo> detail(@PathVariable String accountId) {
        return R.ok(runningHubAccountAdminService.detail(accountId));
    }

    @SaCheckPermission("aivideo:runninghub-account:edit")
    @Log(title = "RunningHub 账号管理", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    @RepeatSubmit
    @PutMapping("/{accountId}")
    public R<Void> update(@PathVariable String accountId,
                          @Valid @RequestBody UpdateRunningHubAccountBo command) {
        runningHubAccountAdminService.update(accountId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:runninghub-account:remove")
    @Log(title = "RunningHub 账号管理", businessType = BusinessType.DELETE)
    @RepeatSubmit
    @DeleteMapping("/{accountId}")
    public R<Void> delete(@PathVariable String accountId,
                          @RequestParam @PositiveOrZero long expectedRevision) {
        runningHubAccountAdminService.delete(accountId, expectedRevision, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:runninghub-account:enable")
    @Log(title = "RunningHub 账号启用", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{accountId}/enable")
    public R<Void> enable(@PathVariable String accountId, @Valid @RequestBody StatusChangeBo command) {
        runningHubAccountAdminService.enable(accountId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:runninghub-account:disable")
    @Log(title = "RunningHub 账号停用", businessType = BusinessType.UPDATE)
    @RepeatSubmit
    @PostMapping("/{accountId}/disable")
    public R<Void> disable(@PathVariable String accountId, @Valid @RequestBody StatusChangeBo command) {
        runningHubAccountAdminService.disable(accountId, command, LoginHelper.getUserId());
        return R.ok();
    }

    @SaCheckPermission("aivideo:runninghub-account:query")
    @Log(title = "RunningHub 参数候选读取", businessType = BusinessType.OTHER,
        isSaveResponseData = false)
    @RepeatSubmit
    @PostMapping("/parameter-candidates")
    public R<ParameterCandidatesVo> parameterCandidates(
        @Valid @RequestBody ParameterCandidatesBo command) {
        return R.data(runningHubAccountAdminService.parameterCandidates(command));
    }
}
