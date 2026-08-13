package org.dromara.aivideo.platform.workflow.service;

import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.CreateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.RunningHubAccountQueryBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.StatusChangeBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.UpdateRunningHubAccountBo;
import org.dromara.aivideo.platform.workflow.domain.bo.RunningHubAccountAdminBos.ParameterCandidatesBo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.DetailVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.SummaryVo;
import org.dromara.aivideo.platform.workflow.domain.vo.RunningHubAccountAdminVos.ParameterCandidatesVo;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

/** 运营端 RunningHub 账号 HTTP 适配服务。 */
public interface IRunningHubAccountAdminService {

    PageResult<SummaryVo> page(RunningHubAccountQueryBo query, PageQuery pageQuery);

    DetailVo detail(String accountId);

    String create(CreateRunningHubAccountBo command, Long operatorId);

    void update(String accountId, UpdateRunningHubAccountBo command, Long operatorId);

    void delete(String accountId, long expectedRevision, Long operatorId);

    void enable(String accountId, StatusChangeBo command, Long operatorId);

    void disable(String accountId, StatusChangeBo command, Long operatorId);

    ParameterCandidatesVo parameterCandidates(ParameterCandidatesBo command);
}
