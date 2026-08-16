package org.dromara.aivideo.agent.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.dromara.aivideo.agent.domain.AgentRunEvaluation;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

public interface AgentRunEvaluationMapper extends BaseMapperPlus<AgentRunEvaluation, AgentRunEvaluation> {

    /** Insert immutable quality facts only while the exact owned render result is under the live lease fence. */
    @Insert("""
        INSERT INTO av_agent_run_evaluation (
            evaluation_id, agent_run_id, owner_user_id, candidate_no,
            render_task_id, result_asset_id, project_id, rule_set_version,
            quality_json, quality_digest, decision, repair_scope,
            actor_type, actor_id, create_dept, create_by, create_time, update_by, update_time
        )
        SELECT
            #{row.evaluationId}, run.agent_run_id, run.owner_user_id, #{row.candidateNo},
            task.task_id, asset.asset_id, project.project_id, #{row.ruleSetVersion},
            CAST(#{row.qualityJson} AS JSON), #{row.qualityDigest}, #{row.decision}, #{row.repairScope},
            'app_user', run.owner_user_id, #{row.createDept}, run.owner_user_id,
            #{databaseNow}, run.owner_user_id, #{databaseNow}
        FROM av_agent_run run
        JOIN av_ai_task task
          ON task.task_id = run.waiting_task_id
         AND task.owner_user_id = run.owner_user_id
         AND task.task_type = 'timeline_render'
         AND task.task_status = 'success'
        JOIN av_creation_asset asset
          ON asset.asset_id = task.result_asset_id
         AND asset.asset_id = #{row.resultAssetId}
         AND asset.owner_user_id = run.owner_user_id
         AND asset.source_ref_id = task.task_id
         AND asset.asset_type = 'video'
         AND asset.usage_origin = 'timeline_render_output'
         AND asset.asset_status = 'ready'
         AND asset.sha256 = #{artifactSha256}
         AND asset.del_flag = '0'
        JOIN av_creation_project project
          ON project.project_id = task.resource_id
         AND project.project_id = #{row.projectId}
         AND project.owner_user_id = run.owner_user_id
         AND project.del_flag = '0'
        WHERE run.agent_run_id = #{row.agentRunId}
          AND run.owner_user_id = #{row.ownerUserId}
          AND run.run_status = 'waiting_external_task'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
          AND run.lease_generation = #{expectedLeaseGeneration}
          AND run.lease_token_digest = #{leaseTokenDigest}
          AND run.lease_expires_at > #{databaseNow}
          AND run.waiting_task_source = 'ai_task'
          AND run.waiting_task_id = #{row.renderTaskId}
          AND run.waiting_contract_revision = #{expectedContractRevision}
          AND run.quality_repair_count = #{row.candidateNo}
        """)
    int insertFenced(@Param("row") AgentRunEvaluation row,
                     @Param("artifactSha256") String artifactSha256,
                     @Param("expectedContractRevision") long expectedContractRevision,
                     @Param("expectedRowVersion") long expectedRowVersion,
                     @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                     @Param("leaseTokenDigest") String leaseTokenDigest,
                     @Param("databaseNow") java.time.LocalDateTime databaseNow);

    /** Replay remains valid only under the same owner, lease, task, candidate, and immutable digest. */
    @Select("""
        SELECT COUNT(*)
        FROM av_agent_run_evaluation evaluation
        JOIN av_agent_run run
          ON run.agent_run_id = evaluation.agent_run_id
         AND run.owner_user_id = evaluation.owner_user_id
        WHERE evaluation.evaluation_id = #{evaluationId}
          AND evaluation.agent_run_id = #{agentRunId}
          AND evaluation.owner_user_id = #{ownerUserId}
          AND evaluation.candidate_no = #{candidateNo}
          AND evaluation.render_task_id = #{renderTaskId}
          AND evaluation.result_asset_id = #{resultAssetId}
          AND evaluation.project_id = #{projectId}
          AND evaluation.rule_set_version = #{ruleSetVersion}
          AND evaluation.quality_digest = #{qualityDigest}
          AND evaluation.decision = #{decision}
          AND evaluation.repair_scope = #{repairScope}
          AND run.run_status = 'waiting_external_task'
          AND run.contract_revision = #{expectedContractRevision}
          AND run.row_version = #{expectedRowVersion}
          AND run.lease_generation = #{expectedLeaseGeneration}
          AND run.lease_token_digest = #{leaseTokenDigest}
          AND run.lease_expires_at > #{databaseNow}
          AND run.waiting_task_source = 'ai_task'
          AND run.waiting_task_id = evaluation.render_task_id
          AND run.waiting_contract_revision = #{expectedContractRevision}
          AND run.quality_repair_count = evaluation.candidate_no
        """)
    long countFencedReplay(@Param("evaluationId") long evaluationId,
                           @Param("agentRunId") long agentRunId,
                           @Param("ownerUserId") long ownerUserId,
                           @Param("candidateNo") long candidateNo,
                           @Param("renderTaskId") long renderTaskId,
                           @Param("resultAssetId") long resultAssetId,
                           @Param("projectId") long projectId,
                           @Param("ruleSetVersion") String ruleSetVersion,
                           @Param("qualityDigest") String qualityDigest,
                           @Param("decision") String decision,
                           @Param("repairScope") String repairScope,
                           @Param("expectedContractRevision") long expectedContractRevision,
                           @Param("expectedRowVersion") long expectedRowVersion,
                           @Param("expectedLeaseGeneration") long expectedLeaseGeneration,
                           @Param("leaseTokenDigest") String leaseTokenDigest,
                           @Param("databaseNow") java.time.LocalDateTime databaseNow);
}
