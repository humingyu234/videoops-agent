package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.dto.RunningHubAccountDTOs;
import org.dromara.common.core.domain.PageResult;
import org.dromara.common.mybatis.core.page.PageQuery;

import java.util.List;

public interface IRunningHubAccountService {

    PageResult<RunningHubAccountDTOs.Summary> queryPage(
        RunningHubAccountDTOs.Query query, PageQuery pageQuery);

    RunningHubAccountDTOs.Detail queryDetail(String accountId);

    String create(Long actorId, RunningHubAccountDTOs.Save command);

    void update(Long actorId, String accountId, RunningHubAccountDTOs.Save command);

    void enable(Long actorId, String accountId, long expectedRevision);

    void disable(Long actorId, String accountId, long expectedRevision);

    void delete(Long actorId, String accountId, long expectedRevision);

    List<RunningHubAccountDTOs.Option> queryOptions();

    RunningHubAccountDTOs.InspectionCredential queryInspectionCredential(String accountId);
}
