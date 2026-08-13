package org.dromara.aivideo.platform.workflow.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.CreateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.RunningHubAccountQueryBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.StatusChangeBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.UpdateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.ParameterCandidatesBo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.SummaryVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.ParameterCandidateVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.ParameterCandidatesVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.ParameterOptionVo;
import org.dromara.aivideo.platform.workflow.service.IRunningHubAccountAdminService;
import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.aivideo.workflow.dto.RunningHubParameterInspectionDTOs;
import org.dromara.aivideo.workflow.service.IRunningHubAccountService;
import org.dromara.aivideo.workflow.service.IRunningHubParameterInspectionService;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/** 只负责运营端 BO/VO 与 Core DTO 的映射。 */
@Service
@RequiredArgsConstructor
public class RunningHubAccountAdminServiceImpl implements IRunningHubAccountAdminService {

    private final IRunningHubAccountService runningHubAccountService;
    private final IRunningHubParameterInspectionService parameterInspectionService;

    @Override
    public PageResult<SummaryVo> page(RunningHubAccountQueryBo query, PageQuery pageQuery) {
        RunningHubAccountDTOs.Query dto = new RunningHubAccountDTOs.Query(
            query == null ? null : query.getKeyword(), query == null ? null : query.getEnabled());
        PageResult<RunningHubAccountDTOs.Summary> page = runningHubAccountService.queryPage(dto, pageQuery);
        return PageResult.build(page.getRows().stream().map(this::toSummary).toList(), page.getTotal());
    }

    @Override
    public DetailVo detail(String accountId) {
        return toDetail(runningHubAccountService.queryDetail(accountId));
    }

    @Override
    public String create(CreateRunningHubAccountBo command, Long operatorId) {
        char[] apiKey = secretChars(command.apiKey());
        try {
            return runningHubAccountService.create(operatorId,
                new RunningHubAccountDTOs.Save(command.accountName(), apiKey, null));
        } finally {
            clear(apiKey);
        }
    }

    @Override
    public void update(String accountId, UpdateRunningHubAccountBo command, Long operatorId) {
        char[] apiKey = secretChars(command.apiKey());
        try {
            runningHubAccountService.update(operatorId, accountId,
                new RunningHubAccountDTOs.Save(command.accountName(), apiKey, command.expectedRevision()));
        } finally {
            clear(apiKey);
        }
    }

    @Override
    public void delete(String accountId, long expectedRevision, Long operatorId) {
        runningHubAccountService.delete(operatorId, accountId, expectedRevision);
    }

    @Override
    public void enable(String accountId, StatusChangeBo command, Long operatorId) {
        runningHubAccountService.enable(operatorId, accountId, command.expectedRevision());
    }

    @Override
    public void disable(String accountId, StatusChangeBo command, Long operatorId) {
        runningHubAccountService.disable(operatorId, accountId, command.expectedRevision());
    }

    @Override
    public ParameterCandidatesVo parameterCandidates(ParameterCandidatesBo command) {
        RunningHubParameterInspectionDTOs.Result result = parameterInspectionService.inspect(
            new RunningHubParameterInspectionDTOs.Request(
                command.accountId(), command.executionMode(), command.workflowId(), command.webAppId()));
        return new ParameterCandidatesVo(result.webAppName(), result.candidates().stream()
            .map(candidate -> new ParameterCandidateVo(
                candidate.nodeId(), candidate.nodeName(), candidate.fieldName(), candidate.fieldType(),
                candidate.description(), candidate.defaultValue(), candidate.options().stream()
                    .map(option -> new ParameterOptionVo(option.value(), option.label()))
                    .toList()))
            .toList());
    }

    private SummaryVo toSummary(RunningHubAccountDTOs.Summary dto) {
        return new SummaryVo(
            dto.accountId(), dto.accountName(), dto.apiKeyMasked(), dto.hasApiKey(), dto.enabled(),
            dto.lastHealthStatus(), dto.lastHealthTime(), dto.lastHealthSummary(), dto.rowRevision(), dto.updateTime());
    }

    private DetailVo toDetail(RunningHubAccountDTOs.Detail dto) {
        return new DetailVo(
            dto.accountId(), dto.accountName(), dto.apiKeyMasked(), dto.hasApiKey(), dto.enabled(),
            dto.lastHealthStatus(), dto.lastHealthTime(), dto.lastHealthSummary(), dto.credentialUpdatedAt(),
            dto.rowRevision(), dto.createTime(), dto.updateTime());
    }

    private char[] secretChars(String value) {
        return value == null || value.isBlank() ? null : value.toCharArray();
    }

    private void clear(char[] value) {
        if (value != null) {
            Arrays.fill(value, '\0');
        }
    }
}
