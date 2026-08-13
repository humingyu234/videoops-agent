package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.dto.RunningHubExecutionDTOs;

/** RunningHub create/query/result-materialization boundary. */
public interface IRunningHubExecutionClient {

    RunningHubExecutionDTOs.Submission submit(RunningHubExecutionDTOs.SubmitRequest request);

    RunningHubExecutionDTOs.QueryResult query(String accountId, String externalTaskId);

    RunningHubExecutionDTOs.StoredOutput materializeOutput(RunningHubExecutionDTOs.Output output,
                                                            RunningHubExecutionDTOs.OutputStoragePolicy policy,
                                                            long orderId);
}
