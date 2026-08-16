package org.dromara.aivideo.user.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.identity.security.AppLoginHelper;
import org.dromara.aivideo.identity.security.ConditionalOnAppSecurityEnabled;
import org.dromara.aivideo.user.agent.domain.bo.AgentApprovalDecisionBo;
import org.dromara.aivideo.user.agent.domain.bo.AgentRunRevisionBo;
import org.dromara.aivideo.user.agent.domain.bo.CreateAgentRunBo;
import org.dromara.aivideo.user.agent.domain.vo.AgentRunDetailVo;
import org.dromara.aivideo.user.agent.service.IAgentRunApplicationService;
import org.dromara.common.core.domain.R;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Real, owner-scoped HTTP entry for constrained AgentRun execution. */
@Validated
@RestController
@RequiredArgsConstructor
@ConditionalOnAppSecurityEnabled
@RequestMapping("/api/agent/runs")
public class AgentRunController {

    private final IAgentRunApplicationService applicationService;
    private final AppLoginHelper loginHelper;

    @PostMapping
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "agent run", businessType = BusinessType.INSERT,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AgentRunDetailVo> create(@Valid @RequestBody CreateAgentRunBo body) {
        return R.ok(applicationService.create(loginHelper.getPrincipal(), body));
    }

    @GetMapping("/{agentRunId}")
    @SaCheckPermission(value = "aivideo:studio:query", type = "app")
    public R<AgentRunDetailVo> detail(@PathVariable String agentRunId) {
        return R.ok(applicationService.detail(loginHelper.getPrincipal(), agentRunId));
    }

    @PostMapping("/{agentRunId}/advancements")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "agent run advance", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AgentRunDetailVo> advance(@PathVariable String agentRunId,
                                       @Valid @RequestBody AgentRunRevisionBo body) {
        return R.ok(applicationService.advance(loginHelper.getPrincipal(), agentRunId, body));
    }

    @PostMapping("/{agentRunId}/cancellations")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "agent run cancellation", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AgentRunDetailVo> cancel(@PathVariable String agentRunId,
                                      @Valid @RequestBody AgentRunRevisionBo body) {
        return R.ok(applicationService.cancel(loginHelper.getPrincipal(), agentRunId, body));
    }

    @PostMapping("/{agentRunId}/approvals/{approvalId}/decision")
    @SaCheckPermission(value = "aivideo:studio:generate", type = "app")
    @Log(title = "agent run approval", businessType = BusinessType.UPDATE,
        isSaveRequestData = false, isSaveResponseData = false)
    public R<AgentRunDetailVo> decideApproval(@PathVariable String agentRunId,
                                              @PathVariable String approvalId,
                                              @Valid @RequestBody AgentApprovalDecisionBo body) {
        return R.ok(applicationService.decideApproval(
            loginHelper.getPrincipal(), agentRunId, approvalId, body));
    }
}
