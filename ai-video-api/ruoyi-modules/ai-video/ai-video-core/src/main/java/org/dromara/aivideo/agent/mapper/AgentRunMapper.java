package org.dromara.aivideo.agent.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.agent.domain.AgentRun;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

import java.time.LocalDateTime;

public interface AgentRunMapper extends BaseMapperPlus<AgentRun, AgentRun> {

    @Select("SELECT UTC_TIMESTAMP(6)")
    LocalDateTime selectDatabaseNow();

    /** Claim a queued run or recover the same identity after its running lease expires. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'running',
            row_version = row_version + 1,
            lease_generation = lease_generation + 1,
            lease_owner = #{leaseOwner},
            lease_token_digest = #{leaseTokenDigest},
            lease_expires_at = #{leaseExpiresAt},
            resume_after = NULL,
            started_at = COALESCE(started_at, #{databaseNow}),
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_generation = #{expectedLeaseGeneration}
          AND (
              (run_status = 'queued' AND (resume_after IS NULL OR resume_after <= #{databaseNow}))
              OR
              (run_status = 'running' AND lease_expires_at IS NOT NULL
                  AND lease_expires_at <= #{databaseNow})
          )
        """)
    int claimLease(@Param("agentRunId") long agentRunId,
                   @Param("ownerUserId") long ownerUserId,
                   @Param("expectedContractRevision") long expectedContractRevision,
                   @Param("expectedRowVersion") long expectedRowVersion,
                   @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("leaseTokenDigest") String leaseTokenDigest,
                   @Param("databaseNow") LocalDateTime databaseNow,
                   @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /** Rotate the fencing token of an expired waiting lease without changing its persisted task identity. */
    @Update("""
        UPDATE av_agent_run
        SET row_version = row_version + 1,
            lease_generation = lease_generation + 1,
            lease_owner = #{leaseOwner},
            lease_token_digest = #{leaseTokenDigest},
            lease_expires_at = #{leaseExpiresAt},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'waiting_external_task'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_generation = #{expectedLeaseGeneration}
          AND lease_expires_at IS NOT NULL
          AND lease_expires_at <= #{databaseNow}
          AND resume_after IS NOT NULL
          AND resume_after <= #{databaseNow}
          AND waiting_task_source = #{taskSource}
          AND waiting_task_id = #{taskId}
          AND waiting_contract_revision = #{expectedContractRevision}
        """)
    int recoverWaitingLease(@Param("agentRunId") long agentRunId,
                            @Param("ownerUserId") long ownerUserId,
                            @Param("expectedContractRevision") long expectedContractRevision,
                            @Param("expectedRowVersion") long expectedRowVersion,
                            @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                            @Param("taskSource") String taskSource,
                            @Param("taskId") long taskId,
                            @Param("leaseOwner") String leaseOwner,
                            @Param("leaseTokenDigest") String leaseTokenDigest,
                            @Param("databaseNow") LocalDateTime databaseNow,
                            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    /** Persist the external task identity and move the lease fence to the next observation time. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'waiting_external_task',
            row_version = row_version + 1,
            lease_expires_at = #{resumeAfter},
            resume_after = #{resumeAfter},
            waiting_task_source = #{taskSource},
            waiting_task_id = #{taskId},
            waiting_contract_revision = #{expectedContractRevision},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'running'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_generation = #{expectedLeaseGeneration}
          AND lease_token_digest = #{leaseTokenDigest}
          AND lease_expires_at > #{databaseNow}
          AND (
              (
                  #{taskSource} = 'ai_task'
                  AND EXISTS (
                      SELECT 1
                      FROM av_ai_task task
                      WHERE task.task_id = #{taskId}
                        AND task.owner_user_id = #{ownerUserId}
                        AND task.task_type = 'timeline_render'
                        AND task.task_status IN ('pending', 'queued', 'running', 'success')
                  )
              )
              OR
              (
                  #{taskSource} = 'digital_human_generation'
                  AND EXISTS (
                      SELECT 1
                      FROM av_dh_generation_job job
                      WHERE job.id = #{taskId}
                        AND job.owner_user_id = #{ownerUserId}
                        AND job.job_type = 'video_generate'
                        AND job.status IN ('queued', 'running', 'succeeded')
                  )
              )
          )
        """)
    int waitForExternalTask(@Param("agentRunId") long agentRunId,
                            @Param("ownerUserId") long ownerUserId,
                            @Param("expectedContractRevision") long expectedContractRevision,
                            @Param("expectedRowVersion") long expectedRowVersion,
                            @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                            @Param("leaseTokenDigest") String leaseTokenDigest,
                            @Param("taskSource") String taskSource,
                            @Param("taskId") long taskId,
                            @Param("resumeAfter") LocalDateTime resumeAfter,
                            @Param("databaseNow") LocalDateTime databaseNow);

    /** Accept the exact persisted task result once; stale task or contract facts affect zero rows. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'completed',
            row_version = row_version + 1,
            resume_after = NULL,
            waiting_task_source = NULL,
            waiting_task_id = NULL,
            waiting_contract_revision = NULL,
            candidate_asset_id = #{candidateAssetId},
            result_summary_json = #{resultSummaryJson},
            result_digest = #{resultDigest},
            lease_owner = NULL,
            lease_token_digest = NULL,
            lease_expires_at = NULL,
            finished_at = #{databaseNow},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'waiting_external_task'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_generation = #{expectedLeaseGeneration}
          AND lease_token_digest = #{leaseTokenDigest}
          AND waiting_task_source = #{taskSource}
          AND waiting_task_id = #{taskId}
          AND waiting_contract_revision = #{expectedContractRevision}
          AND EXISTS (
              SELECT 1
              FROM av_creation_asset asset
              WHERE asset.asset_id = #{candidateAssetId}
                AND asset.owner_user_id = #{ownerUserId}
                AND asset.asset_status = 'ready'
                AND asset.asset_type = 'video'
                AND asset.del_flag = '0'
                AND asset.source_ref_id = #{taskId}
                AND (
                    (
                        #{taskSource} = 'ai_task'
                        AND asset.usage_origin = 'timeline_render_output'
                        AND EXISTS (
                            SELECT 1
                            FROM av_ai_task task
                            WHERE task.task_id = #{taskId}
                              AND task.owner_user_id = #{ownerUserId}
                              AND task.task_status = 'success'
                              AND task.result_asset_id = asset.asset_id
                        )
                    )
                    OR
                    (
                        #{taskSource} = 'digital_human_generation'
                        AND asset.usage_origin = 'digital_human_output'
                        AND EXISTS (
                            SELECT 1
                            FROM av_dh_generation_job job
                            WHERE job.id = #{taskId}
                              AND job.owner_user_id = #{ownerUserId}
                              AND job.status = 'succeeded'
                              AND job.job_type = 'video_generate'
                        )
                    )
                )
          )
        """)
    int completeExternalTask(@Param("agentRunId") long agentRunId,
                             @Param("ownerUserId") long ownerUserId,
                             @Param("expectedContractRevision") long expectedContractRevision,
                             @Param("expectedRowVersion") long expectedRowVersion,
                             @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                             @Param("leaseTokenDigest") String leaseTokenDigest,
                             @Param("taskSource") String taskSource,
                             @Param("taskId") long taskId,
                             @Param("candidateAssetId") long candidateAssetId,
                             @Param("resultSummaryJson") String resultSummaryJson,
                             @Param("resultDigest") String resultDigest,
                             @Param("databaseNow") LocalDateTime databaseNow);

    /** End a running lease, or fail/cancel a waiting lease whose external task reached the same terminal state. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = #{terminalStatus},
            row_version = row_version + 1,
            lease_owner = NULL,
            lease_token_digest = NULL,
            lease_expires_at = NULL,
            resume_after = NULL,
            waiting_task_source = NULL,
            waiting_task_id = NULL,
            waiting_contract_revision = NULL,
            candidate_asset_id = #{candidateAssetId},
            result_summary_json = #{resultSummaryJson},
            result_digest = #{resultDigest},
            error_code = #{errorCode},
            error_summary = #{errorSummary},
            finished_at = #{databaseNow},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_generation = #{expectedLeaseGeneration}
          AND lease_token_digest = #{leaseTokenDigest}
          AND lease_expires_at > #{databaseNow}
          AND (
              run_status = 'running'
              OR (
                  run_status = 'waiting_external_task'
                  AND #{terminalStatus} IN ('failed', 'cancelled')
                  AND waiting_contract_revision = #{expectedContractRevision}
                  AND (
                      (
                          waiting_task_source = 'ai_task'
                          AND EXISTS (
                              SELECT 1
                              FROM av_ai_task task
                              WHERE task.task_id = waiting_task_id
                                AND task.owner_user_id = #{ownerUserId}
                                AND task.task_type = 'timeline_render'
                                AND task.task_status = #{terminalStatus}
                          )
                      )
                      OR
                      (
                          waiting_task_source = 'digital_human_generation'
                          AND EXISTS (
                              SELECT 1
                              FROM av_dh_generation_job job
                              WHERE job.id = waiting_task_id
                                AND job.owner_user_id = #{ownerUserId}
                                AND job.job_type = 'video_generate'
                                AND job.status = #{terminalStatus}
                          )
                      )
                  )
              )
          )
          AND (
              #{terminalStatus} <> 'completed'
              OR EXISTS (
                  SELECT 1
                  FROM av_creation_asset asset
                  WHERE asset.asset_id = #{candidateAssetId}
                    AND asset.owner_user_id = #{ownerUserId}
                    AND asset.asset_status = 'ready'
                    AND asset.asset_type = 'video'
                    AND asset.del_flag = '0'
                    AND asset.usage_origin IN ('digital_human_output', 'timeline_render_output')
              )
          )
        """)
    int finishLease(@Param("agentRunId") long agentRunId,
                    @Param("ownerUserId") long ownerUserId,
                    @Param("expectedContractRevision") long expectedContractRevision,
                    @Param("expectedRowVersion") long expectedRowVersion,
                    @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                    @Param("leaseTokenDigest") String leaseTokenDigest,
                    @Param("terminalStatus") String terminalStatus,
                    @Param("candidateAssetId") Long candidateAssetId,
                    @Param("resultSummaryJson") String resultSummaryJson,
                    @Param("resultDigest") String resultDigest,
                    @Param("errorCode") String errorCode,
                    @Param("errorSummary") String errorSummary,
                    @Param("databaseNow") LocalDateTime databaseNow);
}
