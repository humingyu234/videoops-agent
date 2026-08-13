package org.dromara.aivideo.user.task.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.task.dto.AiTaskAccessScopeDTO;
import org.dromara.aivideo.task.dto.AiTaskQueryDTO;
import org.dromara.aivideo.task.dto.RetryAiTaskDTO;
import org.dromara.aivideo.task.service.IAiTaskService;
import org.dromara.aivideo.user.task.domain.bo.AiTaskQueryBo;
import org.dromara.aivideo.user.task.domain.bo.RetryAiTaskBo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskListItemVo;
import org.dromara.aivideo.user.task.domain.vo.AiTaskVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tasks")
public class AiTaskController {

    private final IAiTaskService taskService;
    private final AppLoginHelper loginHelper;

    @GetMapping
    @SaCheckPermission(value = "aivideo:task:query", type = "app")
    public R<PageResult<AiTaskListItemVo>> list(@Valid AiTaskQueryBo query) {
        int pageSize = query == null || query.getPageSize() == null ? 20 : query.getPageSize();
        int pageNum = query == null || query.getPageNum() == null ? 1 : query.getPageNum();
        PageResult<org.dromara.aivideo.task.dto.AiTaskSummaryDTO> page = taskService.pageOwned(taskScope(),
            new AiTaskQueryDTO(query == null ? null : query.getTaskType(), query == null ? null : query.getStatus(),
                null, null, null, query == null ? null : query.getKeyword()),
            new PageQuery(pageSize, pageNum));
        return R.ok(PageResult.build(page.getRows().stream().map(AiTaskListItemVo::from).toList(), page.getTotal()));
    }

    @GetMapping("/{taskId}")
    @SaCheckPermission(value = "aivideo:task:query", type = "app")
    public R<AiTaskVo> detail(@PathVariable String taskId) {
        return R.ok(AiTaskVo.from(taskService.getOwned(taskScope(), taskId)));
    }

    @PostMapping("/{taskId}/cancellations")
    @SaCheckPermission(value = "aivideo:task:cancel", type = "app")
    @Log(title = "task cancellation", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> cancel(@PathVariable String taskId, @Valid @RequestBody RetryAiTaskBo body) {
        return R.ok(AiTaskVo.from(taskService.requestCancellation(taskScope(), taskId, body.getIdempotencyKey())));
    }

    @PostMapping("/{taskId}/retry")
    @SaCheckPermission(value = "aivideo:task:retry", type = "app")
    @Log(title = "task retry", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AiTaskVo> retry(@PathVariable String taskId, @Valid @RequestBody RetryAiTaskBo body) {
        return R.ok(AiTaskVo.from(taskService.retryOwned(actorId(),
            new RetryAiTaskDTO(taskId, body.getIdempotencyKey(), requestDigest(taskId, body.getIdempotencyKey())))));
    }

    private long actorId() {
        return loginHelper.getLoginUser().userId();
    }

    private AiTaskAccessScopeDTO taskScope() {
        var principal = loginHelper.getLoginUser().principal();
        return new AiTaskAccessScopeDTO(principal.workspace().tenantId(), principal.appUserId(),
            principal.workspace().workspaceKey());
    }

    private String requestDigest(String taskId, String idempotencyKey) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest((taskId + "\n" + idempotencyKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
