package org.dromara.aivideo.workflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.workflow.domain.WorkflowTaskExecution;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;

/** Conditional persistence for provider submission and terminal facts. */
public interface WorkflowTaskExecutionMapper
    extends BaseMapperPlus<WorkflowTaskExecution, WorkflowTaskExecution> {

    @Select("SELECT * FROM av_workflow_task_execution WHERE task_id = #{taskId}")
    WorkflowTaskExecution selectByTaskId(@Param("taskId") long taskId);

    @Update("""
        UPDATE av_workflow_task_execution
        SET submission_state='submitting', runninghub_account_id=#{accountId}, execution_mode=#{mode},
            submission_started_at=#{startedAt}, provider_deadline_at=#{deadlineAt}, update_time=NOW()
        WHERE task_id=#{taskId} AND submission_state='not_started'
        """)
    int markSubmitting(@Param("taskId") long taskId, @Param("accountId") long accountId,
                       @Param("orderId") long orderId, @Param("mode") String mode,
                       @Param("startedAt") LocalDateTime startedAt,
                       @Param("deadlineAt") LocalDateTime deadlineAt);

    @Update("""
        UPDATE av_workflow_task_execution
        SET submission_state='accepted', external_task_id=#{externalTaskId}, submitted_at=#{submittedAt},
            external_status=#{externalStatus}, update_time=NOW()
        WHERE task_id=#{taskId} AND submission_state='submitting' AND external_task_id IS NULL
        """)
    int markAccepted(@Param("taskId") long taskId, @Param("externalTaskId") String externalTaskId,
                     @Param("submittedAt") LocalDateTime submittedAt,
                     @Param("externalStatus") String externalStatus);

    @Update("""
        UPDATE av_workflow_task_execution
        SET last_polled_at=#{polledAt}, external_status=#{externalStatus}, poll_count=#{pollCount}, update_time=NOW()
        WHERE task_id=#{taskId} AND submission_state='accepted'
        """)
    int recordPoll(@Param("taskId") long taskId, @Param("polledAt") LocalDateTime polledAt,
                   @Param("externalStatus") String externalStatus, @Param("pollCount") int pollCount,
                   @Param("ignored") Object ignored);

    @Update("""
        UPDATE av_workflow_task_execution
        SET submission_state='finished', external_status=#{externalStatus}, provider_error_code=#{errorCode},
            provider_error_summary=#{errorSummary}, result_manifest_json=#{resultManifest}, update_time=NOW()
        WHERE task_id=#{taskId} AND submission_state IN ('accepted','submitting')
        """)
    int markFinished(@Param("taskId") long taskId, @Param("externalStatus") String externalStatus,
                     @Param("errorCode") String errorCode, @Param("errorSummary") String errorSummary,
                     @Param("resultManifest") String resultManifest);
}
