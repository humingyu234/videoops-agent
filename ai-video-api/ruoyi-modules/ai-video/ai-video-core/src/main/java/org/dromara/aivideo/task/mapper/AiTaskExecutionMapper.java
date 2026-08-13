package org.dromara.aivideo.task.mapper;

import org.dromara.aivideo.task.domain.AiTaskExecution;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface AiTaskExecutionMapper extends BaseMapperPlus<AiTaskExecution, AiTaskExecution> {

    /** Counts cluster-wide live running leases at the supplied database time. */
    @Select("""
        SELECT COUNT(*)
        FROM av_ai_task_execution
        WHERE execution_status = 'running'
          AND lease_expires_at > #{databaseNow}
        """)
    long countLiveRunning(@Param("databaseNow") LocalDateTime databaseNow);

    /** Counts active non-RunningHub work so provider leases never block local AI capabilities. */
    @Select("""
        SELECT COUNT(*)
        FROM av_ai_task_execution execution
        INNER JOIN av_ai_task task ON task.task_id = execution.task_id
        WHERE execution.execution_status = 'running'
          AND execution.lease_expires_at > #{databaseNow}
          AND task.task_type NOT IN ('workflow_template_generate', 'workflow_template_test')
        """)
    long countLiveRunningNonWorkflow(@Param("databaseNow") LocalDateTime databaseNow);

    /** Counts one owner's live running leases at the supplied database time. */
    @Select("""
        SELECT COUNT(*)
        FROM av_ai_task_execution
        WHERE execution_status = 'running'
          AND owner_user_id = #{ownerUserId}
          AND lease_expires_at > #{databaseNow}
        """)
    long countLiveRunningByOwner(@Param("ownerUserId") long ownerUserId,
                                 @Param("databaseNow") LocalDateTime databaseNow);

    /** Counts one stable actor's live running leases at the supplied database time. */
    @Select("""
        SELECT COUNT(*)
        FROM av_ai_task_execution
        WHERE execution_status = 'running'
          AND actor_type = #{actorType}
          AND actor_id = #{actorId}
          AND lease_expires_at > #{databaseNow}
        """)
    long countLiveRunningByActor(@Param("actorType") String actorType,
                                 @Param("actorId") long actorId,
                                 @Param("databaseNow") LocalDateTime databaseNow);

    /** Claims only RunningHub workflow work; normal timeline work must not consume this provider capacity. */
    @Select("""
        SELECT execution.*
        FROM av_ai_task_execution execution
        INNER JOIN av_ai_task task ON task.task_id = execution.task_id
        WHERE execution.execution_status = 'queued'
          AND execution.next_run_at <= #{databaseNow}
          AND task.task_type IN ('workflow_template_generate', 'workflow_template_test')
        ORDER BY execution.next_run_at, execution.task_execution_id
        LIMIT #{limit}
        FOR UPDATE SKIP LOCKED
        """)
    List<AiTaskExecution> selectQueuedWorkflowForUpdate(@Param("databaseNow") LocalDateTime databaseNow,
                                                          @Param("limit") int limit);

    /** Counts cluster-wide active RunningHub workflow leases. */
    @Select("""
        SELECT COUNT(*)
        FROM av_ai_task_execution execution
        INNER JOIN av_ai_task task ON task.task_id = execution.task_id
        WHERE execution.execution_status = 'running'
          AND execution.lease_expires_at > #{databaseNow}
          AND task.task_type IN ('workflow_template_generate', 'workflow_template_test')
        """)
    long countLiveRunningWorkflow(@Param("databaseNow") LocalDateTime databaseNow);
}
