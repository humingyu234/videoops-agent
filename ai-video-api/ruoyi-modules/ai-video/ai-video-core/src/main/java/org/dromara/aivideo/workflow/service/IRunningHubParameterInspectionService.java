package org.dromara.aivideo.workflow.service;

import org.dromara.aivideo.workflow.dto.RunningHubParameterInspectionDTOs;

/** 读取 RunningHub AI App 或 Workflow 的可配置参数候选。 */
public interface IRunningHubParameterInspectionService {

    RunningHubParameterInspectionDTOs.Result inspect(RunningHubParameterInspectionDTOs.Request request);
}
