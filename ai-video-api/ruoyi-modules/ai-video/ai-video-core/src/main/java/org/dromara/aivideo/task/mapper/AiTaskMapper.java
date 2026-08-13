package org.dromara.aivideo.task.mapper;

import org.dromara.aivideo.task.domain.AiTask;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

public interface AiTaskMapper extends BaseMapperPlus<AiTask, AiTask> {

    /**
     * Locks one deterministic active root task so cluster-wide dispatch capacity is checked serially.
     *
     * @return locked task id, or {@code null} when no active task exists
     */
    @Select("""
        SELECT task_id
        FROM av_ai_task
        WHERE task_status IN ('queued', 'running')
        ORDER BY task_id
        LIMIT 1
        FOR UPDATE
        """)
    Long lockDispatchCapacityGuard();

    /** @return current database time used by lease capacity checks and writes */
    @Select("SELECT CURRENT_TIMESTAMP(6)")
    LocalDateTime selectDatabaseNow();
}
