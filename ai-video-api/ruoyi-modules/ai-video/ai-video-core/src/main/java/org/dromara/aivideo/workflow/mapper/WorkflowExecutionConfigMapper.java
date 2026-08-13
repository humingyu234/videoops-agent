package org.dromara.aivideo.workflow.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.dromara.aivideo.workflow.domain.WorkflowExecutionConfig;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;

public interface WorkflowExecutionConfigMapper
    extends BaseMapperPlus<WorkflowExecutionConfig, WorkflowExecutionConfig> {

    @Select("""
        SELECT execution_config_id, tenant_id, template_id, runninghub_account_id,
               access_password_ciphertext, execution_mode, workflow_id, webapp_id, instance_type,
               input_mapping_json, output_policy_json, timeout_seconds, enabled,
               last_test_status, last_test_task_id, last_test_template_revision,
               last_test_execution_revision, last_test_account_revision, last_test_time,
               last_test_summary, row_revision, create_by, update_by, del_flag, create_time, update_time
        FROM av_workflow_execution_config
        WHERE tenant_id = #{tenantId} AND template_id = #{templateId} AND del_flag = '0'
        """)
    WorkflowExecutionConfig selectCurrent(@Param("tenantId") long tenantId,
                                          @Param("templateId") long templateId);

    @Update("""
        UPDATE av_workflow_execution_config
        SET runninghub_account_id = #{config.runninghubAccountId},
            access_password_ciphertext = #{config.accessPasswordCiphertext},
            execution_mode = #{config.executionMode}, workflow_id = #{config.workflowId},
            webapp_id = #{config.webappId}, instance_type = #{config.instanceType}, input_mapping_json = #{config.inputMappingJson},
            output_policy_json = #{config.outputPolicyJson}, timeout_seconds = #{config.timeoutSeconds},
            enabled = #{config.enabled}, update_by = #{actorId}, update_time = NOW(),
            row_revision = row_revision + 1
        WHERE tenant_id = #{config.tenantId} AND template_id = #{config.templateId}
          AND del_flag = '0' AND row_revision = #{expectedRevision}
        """)
    int updateCurrentCas(@Param("config") WorkflowExecutionConfig config,
                         @Param("expectedRevision") long expectedRevision,
                         @Param("actorId") long actorId);

    @Select("""
        SELECT COUNT(*) FROM av_workflow_execution_config
        WHERE tenant_id = #{tenantId} AND runninghub_account_id = #{accountId} AND del_flag = '0'
        """)
    long countActiveReferences(@Param("tenantId") long tenantId, @Param("accountId") long accountId);

    @Update("""
        UPDATE av_workflow_execution_config
        SET del_flag = '1', update_by = #{actorId}, update_time = NOW(), row_revision = row_revision + 1
        WHERE tenant_id = #{tenantId} AND template_id = #{templateId} AND del_flag = '0'
        """)
    int logicalDeleteCurrent(@Param("tenantId") long tenantId, @Param("templateId") long templateId,
                             @Param("actorId") long actorId);
}
