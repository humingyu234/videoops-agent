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

    /** Park an unclaimed run until its frozen delivery contract is clarified. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'waiting_input',
            row_version = row_version + 1,
            resume_after = NULL,
            error_code = #{errorCode},
            error_summary = #{errorSummary},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'queued'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND lease_owner IS NULL
          AND lease_token_digest IS NULL
          AND lease_expires_at IS NULL
          AND waiting_task_source IS NULL
          AND waiting_task_id IS NULL
          AND waiting_contract_revision IS NULL
        """)
    int blockForInput(@Param("agentRunId") long agentRunId,
                      @Param("ownerUserId") long ownerUserId,
                      @Param("expectedContractRevision") long expectedContractRevision,
                      @Param("expectedRowVersion") long expectedRowVersion,
                      @Param("errorCode") String errorCode,
                      @Param("errorSummary") String errorSummary,
                      @Param("databaseNow") LocalDateTime databaseNow);

    /** Freeze the untouched contract until its exact initial approval is decided. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'waiting_approval',
            row_version = row_version + 1,
            pending_approval_id = #{approvalId},
            approval_revision = approval_revision + 1,
            resume_after = NULL,
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'queued'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND pending_approval_id IS NULL
          AND approval_revision + 1 = #{approvalRevision}
          AND lease_owner IS NULL
          AND lease_token_digest IS NULL
          AND lease_expires_at IS NULL
          AND waiting_task_source IS NULL
          AND waiting_task_id IS NULL
          AND waiting_contract_revision IS NULL
        """)
    int requestInitialApproval(@Param("agentRunId") long agentRunId,
                               @Param("ownerUserId") long ownerUserId,
                               @Param("expectedContractRevision") long expectedContractRevision,
                               @Param("expectedRowVersion") long expectedRowVersion,
                               @Param("approvalId") long approvalId,
                               @Param("approvalRevision") long approvalRevision,
                               @Param("databaseNow") LocalDateTime databaseNow);

    /** Park one successful quality candidate for an exact conditional or final human decision. */
    @Update("""
        UPDATE av_agent_run run
        JOIN av_agent_run_evaluation evaluation
          ON evaluation.evaluation_id = #{evaluationId}
         AND evaluation.agent_run_id = run.agent_run_id
         AND evaluation.owner_user_id = run.owner_user_id
         AND evaluation.candidate_no = run.quality_repair_count
         AND evaluation.render_task_id = run.waiting_task_id
        SET run.run_status = 'waiting_approval',
            run.row_version = run.row_version + 1,
            run.pending_approval_id = #{approvalId},
            run.approval_revision = run.approval_revision + 1,
            run.candidate_asset_id = evaluation.result_asset_id,
            run.resume_after = NULL,
            run.lease_owner = NULL,
            run.lease_token_digest = NULL,
            run.lease_expires_at = NULL,
            run.state_changed_at = #{databaseNow},
            run.update_by = #{ownerUserId},
            run.update_time = #{databaseNow}
        WHERE run.agent_run_id = #{agentRunId}
          AND run.owner_user_id = #{ownerUserId}
          AND run.run_status = 'waiting_external_task'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
          AND run.lease_generation = #{expectedLeaseGeneration}
          AND run.lease_token_digest = #{leaseTokenDigest}
          AND run.lease_expires_at > #{databaseNow}
          AND run.waiting_task_source = 'ai_task'
          AND run.waiting_contract_revision = #{expectedContractRevision}
          AND run.pending_approval_id IS NULL
          AND run.approval_revision + 1 = #{approvalRevision}
          AND evaluation.decision = #{requiredDecision}
          AND EXISTS (
              SELECT 1
              FROM av_ai_task task
              JOIN av_creation_asset asset
                ON asset.asset_id = task.result_asset_id
               AND asset.owner_user_id = task.owner_user_id
               AND asset.source_ref_id = task.task_id
               AND asset.asset_type = 'video'
               AND asset.usage_origin = 'timeline_render_output'
               AND asset.asset_status = 'ready'
               AND asset.del_flag = '0'
              WHERE task.task_id = evaluation.render_task_id
                AND task.owner_user_id = #{ownerUserId}
                AND task.task_type = 'timeline_render'
                AND task.task_status = 'success'
                AND asset.asset_id = evaluation.result_asset_id
          )
        """)
    int requestQualityApproval(@Param("agentRunId") long agentRunId,
                               @Param("ownerUserId") long ownerUserId,
                               @Param("expectedContractRevision") long expectedContractRevision,
                               @Param("expectedRowVersion") long expectedRowVersion,
                               @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                               @Param("leaseTokenDigest") String leaseTokenDigest,
                               @Param("evaluationId") long evaluationId,
                               @Param("approvalId") long approvalId,
                               @Param("approvalRevision") long approvalRevision,
                               @Param("requiredDecision") String requiredDecision,
                               @Param("databaseNow") LocalDateTime databaseNow);

    /** Initial approval releases the unchanged frozen contract to the queue. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'queued',
            row_version = row_version + 1,
            pending_approval_id = NULL,
            error_code = NULL,
            error_summary = NULL,
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'waiting_approval'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND pending_approval_id = #{approvalId}
          AND approval_revision = #{approvalRevision}
          AND waiting_task_source IS NULL
          AND waiting_task_id IS NULL
          AND candidate_asset_id IS NULL
        """)
    int approveInitial(@Param("agentRunId") long agentRunId,
                       @Param("ownerUserId") long ownerUserId,
                       @Param("expectedContractRevision") long expectedContractRevision,
                       @Param("expectedRowVersion") long expectedRowVersion,
                       @Param("approvalId") long approvalId,
                       @Param("approvalRevision") long approvalRevision,
                       @Param("databaseNow") LocalDateTime databaseNow);

    /** Conditional approval requests a new confirmed input instead of spending automatically. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'waiting_input',
            row_version = row_version + 1,
            pending_approval_id = NULL,
            waiting_task_source = NULL,
            waiting_task_id = NULL,
            waiting_contract_revision = NULL,
            candidate_asset_id = NULL,
            error_code = 'APPROVAL_INPUT_REQUIRED',
            error_summary = #{inputSummary},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'waiting_approval'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND pending_approval_id = #{approvalId}
          AND approval_revision = #{approvalRevision}
          AND waiting_task_source = 'ai_task'
          AND waiting_task_id IS NOT NULL
          AND candidate_asset_id IS NOT NULL
        """)
    int approveConditional(@Param("agentRunId") long agentRunId,
                           @Param("ownerUserId") long ownerUserId,
                           @Param("expectedContractRevision") long expectedContractRevision,
                           @Param("expectedRowVersion") long expectedRowVersion,
                           @Param("approvalId") long approvalId,
                           @Param("approvalRevision") long approvalRevision,
                           @Param("inputSummary") String inputSummary,
                           @Param("databaseNow") LocalDateTime databaseNow);

    /** Final approval completes only the exact final evaluation and owned ready render asset. */
    @Update("""
        UPDATE av_agent_run run
        JOIN av_agent_run_approval approval
          ON approval.approval_id = run.pending_approval_id
         AND approval.agent_run_id = run.agent_run_id
         AND approval.owner_user_id = run.owner_user_id
         AND approval.approval_type = 'final'
         AND approval.approval_status = 'approved'
         AND approval.revision = run.approval_revision
        JOIN av_agent_run_evaluation evaluation
          ON evaluation.evaluation_id = approval.evaluation_id
         AND evaluation.agent_run_id = run.agent_run_id
         AND evaluation.owner_user_id = run.owner_user_id
         AND evaluation.decision = 'final'
         AND evaluation.render_task_id = run.waiting_task_id
         AND evaluation.result_asset_id = run.candidate_asset_id
        JOIN av_ai_task task
          ON task.task_id = evaluation.render_task_id
         AND task.owner_user_id = run.owner_user_id
         AND task.task_type = 'timeline_render'
         AND task.task_status = 'success'
         AND task.result_asset_id = evaluation.result_asset_id
        JOIN av_creation_asset asset
          ON asset.asset_id = evaluation.result_asset_id
         AND asset.owner_user_id = run.owner_user_id
         AND asset.source_ref_id = task.task_id
         AND asset.asset_type = 'video'
         AND asset.usage_origin = 'timeline_render_output'
         AND asset.asset_status = 'ready'
         AND asset.del_flag = '0'
        SET run.run_status = 'completed',
            run.row_version = run.row_version + 1,
            run.pending_approval_id = NULL,
            run.waiting_task_source = NULL,
            run.waiting_task_id = NULL,
            run.waiting_contract_revision = NULL,
            run.result_summary_json = CAST(#{resultSummaryJson} AS JSON),
            run.result_digest = #{resultDigest},
            run.error_code = NULL,
            run.error_summary = NULL,
            run.finished_at = #{databaseNow},
            run.state_changed_at = #{databaseNow},
            run.update_by = #{ownerUserId},
            run.update_time = #{databaseNow}
        WHERE run.agent_run_id = #{agentRunId}
          AND run.owner_user_id = #{ownerUserId}
          AND run.run_status = 'waiting_approval'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
          AND run.pending_approval_id = #{approvalId}
          AND run.approval_revision = #{approvalRevision}
        """)
    int approveFinal(@Param("agentRunId") long agentRunId,
                     @Param("ownerUserId") long ownerUserId,
                     @Param("expectedContractRevision") long expectedContractRevision,
                     @Param("expectedRowVersion") long expectedRowVersion,
                     @Param("approvalId") long approvalId,
                     @Param("approvalRevision") long approvalRevision,
                     @Param("resultSummaryJson") String resultSummaryJson,
                     @Param("resultDigest") String resultDigest,
                     @Param("databaseNow") LocalDateTime databaseNow);

    /** Any exact rejection terminates the run and invalidates its pending task identity. */
    @Update("""
        UPDATE av_agent_run
        SET run_status = 'cancelled',
            row_version = row_version + 1,
            pending_approval_id = NULL,
            lease_owner = NULL,
            lease_token_digest = NULL,
            lease_expires_at = NULL,
            resume_after = NULL,
            waiting_task_source = NULL,
            waiting_task_id = NULL,
            waiting_contract_revision = NULL,
            candidate_asset_id = NULL,
            result_summary_json = NULL,
            result_digest = NULL,
            error_code = 'APPROVAL_REJECTED',
            error_summary = #{rejectionSummary},
            finished_at = #{databaseNow},
            state_changed_at = #{databaseNow},
            update_by = #{ownerUserId},
            update_time = #{databaseNow}
        WHERE agent_run_id = #{agentRunId}
          AND owner_user_id = #{ownerUserId}
          AND run_status = 'waiting_approval'
          AND contract_revision = #{expectedContractRevision}
          AND row_version = #{expectedRowVersion}
          AND pending_approval_id = #{approvalId}
          AND approval_revision = #{approvalRevision}
        """)
    int rejectApproval(@Param("agentRunId") long agentRunId,
                       @Param("ownerUserId") long ownerUserId,
                       @Param("expectedContractRevision") long expectedContractRevision,
                       @Param("expectedRowVersion") long expectedRowVersion,
                       @Param("approvalId") long approvalId,
                       @Param("approvalRevision") long approvalRevision,
                       @Param("rejectionSummary") String rejectionSummary,
                       @Param("databaseNow") LocalDateTime databaseNow);

    /** Replace one evaluated candidate with exactly one bounded dependency-compatible render task. */
    @Update("""
        UPDATE av_agent_run run
        JOIN av_agent_run_evaluation evaluation
          ON evaluation.evaluation_id = #{evaluationId}
         AND evaluation.agent_run_id = run.agent_run_id
         AND evaluation.owner_user_id = run.owner_user_id
         AND evaluation.candidate_no = run.quality_repair_count
         AND evaluation.render_task_id = run.waiting_task_id
         AND evaluation.decision = 'repair'
         AND evaluation.repair_scope = #{repairScope}
        JOIN av_ai_task old_task
          ON old_task.task_id = evaluation.render_task_id
         AND old_task.owner_user_id = run.owner_user_id
         AND old_task.task_type = 'timeline_render'
         AND old_task.task_status = 'success'
         AND old_task.result_asset_id = evaluation.result_asset_id
        JOIN av_ai_task next_task
          ON next_task.task_id = #{nextRenderTaskId}
         AND next_task.owner_user_id = run.owner_user_id
         AND next_task.task_id <> old_task.task_id
         AND next_task.task_type = 'timeline_render'
         AND next_task.resource_type = 'creation_project'
         AND next_task.task_status IN ('pending', 'queued', 'running', 'success')
        SET run.row_version = run.row_version + 1,
            run.quality_repair_count = run.quality_repair_count + 1,
            run.waiting_task_id = next_task.task_id,
            run.candidate_asset_id = NULL,
            run.lease_expires_at = #{resumeAfter},
            run.resume_after = #{resumeAfter},
            run.state_changed_at = #{databaseNow},
            run.update_by = #{ownerUserId},
            run.update_time = #{databaseNow}
        WHERE run.agent_run_id = #{agentRunId}
          AND run.owner_user_id = #{ownerUserId}
          AND run.run_status = 'waiting_external_task'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
          AND run.lease_generation = #{expectedLeaseGeneration}
          AND run.lease_token_digest = #{leaseTokenDigest}
          AND run.lease_expires_at > #{databaseNow}
          AND run.waiting_task_source = 'ai_task'
          AND run.waiting_contract_revision = #{expectedContractRevision}
          AND run.quality_repair_count < 2
          AND (
              (
                  #{repairScope} = 'render'
                  AND next_task.resource_id = old_task.resource_id
                  AND (
                      next_task.input_version_id = old_task.input_version_id
                      OR (next_task.input_version_id IS NULL AND old_task.input_version_id IS NULL)
                  )
              )
              OR
              (
                  #{repairScope} = 'timeline_render'
                  AND next_task.resource_id <> old_task.resource_id
                  AND EXISTS (
                      SELECT 1
                      FROM av_creation_project old_project
                      JOIN av_creation_project next_project
                        ON next_project.owner_user_id = old_project.owner_user_id
                       AND next_project.source_type = 'digital_human_job'
                       AND next_project.source_ref_id = old_project.source_ref_id
                       AND next_project.del_flag = '0'
                      WHERE old_project.project_id = old_task.resource_id
                        AND old_project.owner_user_id = #{ownerUserId}
                        AND old_project.source_type = 'digital_human_job'
                        AND old_project.del_flag = '0'
                        AND next_project.project_id = next_task.resource_id
                  )
              )
          )
        """)
    int startQualityRepair(@Param("agentRunId") long agentRunId,
                           @Param("ownerUserId") long ownerUserId,
                           @Param("expectedContractRevision") long expectedContractRevision,
                           @Param("expectedRowVersion") long expectedRowVersion,
                           @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                           @Param("leaseTokenDigest") String leaseTokenDigest,
                           @Param("evaluationId") long evaluationId,
                           @Param("repairScope") String repairScope,
                           @Param("nextRenderTaskId") long nextRenderTaskId,
                           @Param("resumeAfter") LocalDateTime resumeAfter,
                           @Param("databaseNow") LocalDateTime databaseNow);

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
                        AND job.job_type IN ('voice_generate', 'video_generate')
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

    /** Move the same fenced task to its next observation time without changing its identity. */
    @Update("""
        UPDATE av_agent_run
        SET row_version = row_version + 1,
            lease_expires_at = #{resumeAfter},
            resume_after = #{resumeAfter},
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
          AND lease_expires_at > #{databaseNow}
          AND waiting_task_source = #{taskSource}
          AND waiting_task_id = #{taskId}
          AND waiting_contract_revision = #{expectedContractRevision}
          AND (
              (
                  #{taskSource} = 'ai_task'
                  AND EXISTS (
                      SELECT 1
                      FROM av_ai_task task
                      WHERE task.task_id = #{taskId}
                        AND task.owner_user_id = #{ownerUserId}
                        AND task.task_type = 'timeline_render'
                        AND task.task_status IN ('pending', 'queued', 'running')
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
                        AND job.job_type IN ('voice_generate', 'video_generate')
                        AND job.status IN ('queued', 'running')
                  )
              )
          )
        """)
    int deferExternalTask(@Param("agentRunId") long agentRunId,
                          @Param("ownerUserId") long ownerUserId,
                          @Param("expectedContractRevision") long expectedContractRevision,
                          @Param("expectedRowVersion") long expectedRowVersion,
                          @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                          @Param("leaseTokenDigest") String leaseTokenDigest,
                          @Param("taskSource") String taskSource,
                          @Param("taskId") long taskId,
                          @Param("resumeAfter") LocalDateTime resumeAfter,
                          @Param("databaseNow") LocalDateTime databaseNow);

    /** Replace a successful generation task with its exact child task while keeping the same lease fence. */
    @Update("""
        UPDATE av_agent_run
        SET row_version = row_version + 1,
            lease_expires_at = #{resumeAfter},
            resume_after = #{resumeAfter},
            waiting_task_source = #{nextTaskSource},
            waiting_task_id = #{nextTaskId},
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
          AND lease_expires_at > #{databaseNow}
          AND waiting_task_source = #{completedTaskSource}
          AND waiting_task_id = #{completedTaskId}
          AND waiting_contract_revision = #{expectedContractRevision}
          AND (
              (
                  #{completedTaskSource} = 'digital_human_generation'
                  AND #{nextTaskSource} = 'digital_human_generation'
                  AND EXISTS (
                      SELECT 1
                      FROM av_dh_generation_job completed_job
                      JOIN av_dh_generation_job next_job
                        ON next_job.parent_job_id = completed_job.id
                       AND next_job.owner_user_id = completed_job.owner_user_id
                      WHERE completed_job.id = #{completedTaskId}
                        AND completed_job.owner_user_id = #{ownerUserId}
                        AND completed_job.job_type = 'voice_generate'
                        AND completed_job.status = 'succeeded'
                        AND next_job.id = #{nextTaskId}
                        AND next_job.job_type = 'video_generate'
                        AND next_job.status IN ('queued', 'running', 'succeeded')
                  )
              )
              OR
              (
                  #{completedTaskSource} = 'digital_human_generation'
                  AND #{nextTaskSource} = 'ai_task'
                  AND EXISTS (
                      SELECT 1
                      FROM av_dh_generation_job completed_job
                      JOIN av_creation_project project
                        ON project.owner_user_id = completed_job.owner_user_id
                       AND project.source_type = 'digital_human_job'
                       AND project.source_ref_id = completed_job.id
                       AND project.del_flag = '0'
                      JOIN av_ai_task next_task
                        ON next_task.owner_user_id = project.owner_user_id
                       AND next_task.resource_type = 'creation_project'
                       AND next_task.resource_id = project.project_id
                      WHERE completed_job.id = #{completedTaskId}
                        AND completed_job.owner_user_id = #{ownerUserId}
                        AND completed_job.job_type = 'video_generate'
                        AND completed_job.status = 'succeeded'
                        AND next_task.task_id = #{nextTaskId}
                        AND next_task.task_type = 'timeline_render'
                        AND next_task.task_status IN ('pending', 'queued', 'running', 'success')
                  )
              )
          )
        """)
    int advanceExternalTask(@Param("agentRunId") long agentRunId,
                            @Param("ownerUserId") long ownerUserId,
                            @Param("expectedContractRevision") long expectedContractRevision,
                            @Param("expectedRowVersion") long expectedRowVersion,
                            @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                            @Param("leaseTokenDigest") String leaseTokenDigest,
                            @Param("completedTaskSource") String completedTaskSource,
                            @Param("completedTaskId") long completedTaskId,
                            @Param("nextTaskSource") String nextTaskSource,
                            @Param("nextTaskId") long nextTaskId,
                            @Param("resumeAfter") LocalDateTime resumeAfter,
                            @Param("databaseNow") LocalDateTime databaseNow);

    /** Replace one failed timeline render with one same-project, same-revision retry task. */
    @Update("""
        UPDATE av_agent_run
        SET row_version = row_version + 1,
            retry_count = retry_count + 1,
            lease_expires_at = #{resumeAfter},
            resume_after = #{resumeAfter},
            waiting_task_id = #{retryTaskId},
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
          AND lease_expires_at > #{databaseNow}
          AND waiting_task_source = 'ai_task'
          AND waiting_task_id = #{failedTaskId}
          AND waiting_contract_revision = #{expectedContractRevision}
          AND EXISTS (
              SELECT 1
              FROM av_ai_task failed_task
              JOIN av_ai_task retry_task
                ON retry_task.owner_user_id = failed_task.owner_user_id
               AND retry_task.resource_type = failed_task.resource_type
               AND retry_task.resource_id = failed_task.resource_id
               AND (
                   retry_task.input_version_id = failed_task.input_version_id
                   OR (retry_task.input_version_id IS NULL AND failed_task.input_version_id IS NULL)
               )
              WHERE failed_task.task_id = #{failedTaskId}
                AND failed_task.owner_user_id = #{ownerUserId}
                AND failed_task.task_type = 'timeline_render'
                AND failed_task.resource_type = 'creation_project'
                AND failed_task.task_status = 'failed'
                AND retry_task.task_id = #{retryTaskId}
                AND retry_task.task_id <> failed_task.task_id
                AND retry_task.task_type = 'timeline_render'
                AND retry_task.task_status IN ('pending', 'queued', 'running', 'success')
          )
        """)
    int retryExternalTask(@Param("agentRunId") long agentRunId,
                          @Param("ownerUserId") long ownerUserId,
                          @Param("expectedContractRevision") long expectedContractRevision,
                          @Param("expectedRowVersion") long expectedRowVersion,
                          @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                          @Param("leaseTokenDigest") String leaseTokenDigest,
                          @Param("failedTaskId") long failedTaskId,
                          @Param("retryTaskId") long retryTaskId,
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
                                AND job.job_type IN ('voice_generate', 'video_generate')
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

    /** Stop any owned non-terminal run and invalidate every outstanding lease or late result. */
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
            pending_approval_id = NULL,
            candidate_asset_id = NULL,
            result_summary_json = NULL,
            result_digest = NULL,
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
          AND run_status IN ('queued', 'waiting_input', 'waiting_approval', 'running', 'waiting_external_task')
        """)
    int stopOwnedRun(@Param("agentRunId") long agentRunId,
                     @Param("ownerUserId") long ownerUserId,
                     @Param("expectedContractRevision") long expectedContractRevision,
                     @Param("expectedRowVersion") long expectedRowVersion,
                     @Param("terminalStatus") String terminalStatus,
                     @Param("errorCode") String errorCode,
                     @Param("errorSummary") String errorSummary,
                     @Param("databaseNow") LocalDateTime databaseNow);
}
