package org.dromara.aivideo.workflow;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class WorkflowRunningHubMigrationIT {
    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();

    @Test
    void createsSingleExecutionFactsWithoutForeignKeys() throws Exception {
        try (Connection connection = ENV.openMySqlConnection()) {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(migration()), StandardCharsets.UTF_8));
            try {
                execute(connection, "DELETE FROM av_workflow_task_execution WHERE task_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_workflow_order_asset WHERE order_asset_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_runninghub_account WHERE account_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_ai_task WHERE task_id BETWEEN 991000 AND 991099");
                ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(migration()), StandardCharsets.UTF_8));
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('av_discovery_banner','av_discovery_category','av_discovery_tag','av_workflow_template','av_workflow_execution_config','av_runninghub_account','av_workflow_order','av_workflow_task_execution','av_workflow_order_asset','av_file_object','av_upload_session')")).isEqualTo(11L);
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.key_column_usage WHERE table_schema = DATABASE() AND table_name LIKE 'av_workflow%' AND referenced_table_name IS NOT NULL")).isZero();
            assertThat(queryLong(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='av_workflow_task_execution' AND column_name IN ('runninghub_account_id','execution_mode','submission_state','submitted_at','provider_deadline_at','last_polled_at','poll_count','provider_usage_json','cost_reconciliation_status','result_manifest_json')")).isEqualTo(10L);
                long structureGuard = queryLong(connection, "SELECT (" + initializationPredicate() + ")");
                String structureDiagnostics = structureGuard == 1L ? "" : structureDiagnostics(connection);
                assertThat(structureGuard)
                    .withFailMessage("RunningHub initialization structure guard failed.%n%s", structureDiagnostics)
                    .isEqualTo(1L);
                assertActorAndConstraintDml(connection);
                assertSoftDeleteAndOrderAssetDml(connection);
            } finally {
                execute(connection, "DELETE FROM av_workflow_task_execution WHERE task_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_workflow_order_asset WHERE order_asset_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_runninghub_account WHERE account_id BETWEEN 991000 AND 991099");
                execute(connection, "DELETE FROM av_ai_task WHERE task_id BETWEEN 991000 AND 991099");
            }
        }
    }

    private static void assertActorAndConstraintDml(Connection connection) throws Exception {
        execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,actor_type,actor_id,create_by,update_by) VALUES (991001,991,'workflow_template_generate','workflow_order',1,'it-app','x','v1','{}','pending','created','workflow-free-1','app_user',991,1,1)");
        execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,actor_type,actor_id,create_by,update_by) VALUES (991002,NULL,'workflow_template_test','workflow_template',1,'it-sys','x','v1','{}','pending','created','workflow-free-1','sys_user',7,1,1)");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,actor_type,actor_id,create_by,update_by) VALUES (991003,991,'workflow_template_generate','workflow_order',1,'it-app','x','v1','{}','pending','created','workflow-free-1','app_user',991,1,1)"))
            .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,order_id,resource_type,execution_mode,submission_state) VALUES (991001,991001,0,1,NULL,'workflow_order','runninghub_workflow','not_started')"))
            .isInstanceOf(Exception.class);
        execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,result_asset_id,actor_type,actor_id,create_by,update_by) VALUES (991004,991,'timeline_render','timeline',1,'it-timeline','x','v1','{}','success','created','timeline-free-1',0,77,'app_user',991,1,1)");
        execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,result_schema_version,result_payload_json,actor_type,actor_id,create_by,update_by) VALUES (991005,NULL,'workflow_template_generate','workflow_template',1,'it-workflow-success','x','v1','{}','success','created','workflow-free-1',0,'v1','{}','sys_user',7,1,1)");
        execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,actor_type,actor_id,create_by,update_by) VALUES (991009,NULL,'workflow_template_test','workflow_template',1,'it-workflow-execution','x','v1','{}','pending','created','workflow-free-1',0,'sys_user',7,1,1)");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,result_schema_version,actor_type,actor_id,create_by,update_by) VALUES (991006,NULL,'timeline_render','timeline',1,'it-bad-timeline','x','v1','{}','success','created','timeline-free-1',0,'v1','sys_user',7,1,1)")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,result_asset_id,actor_type,actor_id,create_by,update_by) VALUES (991007,NULL,'workflow_template_generate','workflow_template',1,'it-bad-workflow','x','v1','{}','success','created','workflow-free-1',0,77,'sys_user',7,1,1)")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_ai_task (task_id,owner_user_id,task_type,resource_type,resource_id,idempotency_key,request_digest,request_schema_version,request_payload_json,task_status,stage,quota_policy_version,estimated_usage,actor_type,actor_id,create_by,update_by) VALUES (991008,991,'workflow_template_generate','workflow_template',1,'it-bad-free','x','v1','{}','pending','created','workflow-free-1',1,'app_user',991,1,1)")).isInstanceOf(Exception.class);
        execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,order_id,resource_type,execution_mode,submission_state) VALUES (991004,991009,0,1,NULL,'workflow_template','runninghub_workflow','not_started')");
        execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,order_id,resource_type,execution_mode,submission_state) VALUES (991005,991005,0,1,NULL,'workflow_template','runninghub_ai_app','accepted')");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,resource_type,execution_mode,submission_state) VALUES (991006,991006,0,1,'workflow_template','invalid','not_started')")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,resource_type,execution_mode,submission_state) VALUES (991007,991007,0,1,'workflow_order','runninghub_workflow','not_started')")).isInstanceOf(Exception.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_workflow_task_execution (workflow_task_execution_id,task_id,tenant_id,runninghub_account_id,order_id,resource_type,execution_mode,submission_state) VALUES (991008,991008,0,1,1,'workflow_template','runninghub_workflow','not_started')")).isInstanceOf(Exception.class);
    }

    private static void assertSoftDeleteAndOrderAssetDml(Connection connection) throws Exception {
        execute(connection, "INSERT INTO av_runninghub_account (account_id,tenant_id,account_name,api_key_ciphertext,api_key_masked,enabled,last_health_status,create_by,update_by) VALUES (991001,991,'it-runninghub','cipher-1','***1',1,'unknown',1,1)");
        execute(connection, "UPDATE av_runninghub_account SET del_flag='1' WHERE account_id=991001");
        execute(connection, "INSERT INTO av_runninghub_account (account_id,tenant_id,account_name,api_key_ciphertext,api_key_masked,enabled,last_health_status,create_by,update_by) VALUES (991002,991,'it-runninghub','cipher-2','***2',1,'unknown',1,1)");

        execute(connection, "INSERT INTO av_workflow_order_asset (order_asset_id,tenant_id,owner_user_id,workspace_id,order_id,asset_id,asset_role,input_key,sort_order,is_primary) VALUES (991001,0,991,991,991001,991001,'output',NULL,0,1)");
        assertThatThrownBy(() -> execute(connection, "INSERT INTO av_workflow_order_asset (order_asset_id,tenant_id,owner_user_id,workspace_id,order_id,asset_id,asset_role,input_key,sort_order,is_primary) VALUES (991002,0,991,991,991001,991001,'output',NULL,0,1)"))
            .isInstanceOf(Exception.class);
        execute(connection, "INSERT INTO av_workflow_order_asset (order_asset_id,tenant_id,owner_user_id,workspace_id,order_id,asset_id,asset_role,input_key,sort_order,is_primary) VALUES (991003,0,991,991,991002,991002,'input','portrait',0,0)");
        execute(connection, "INSERT INTO av_workflow_order_asset (order_asset_id,tenant_id,owner_user_id,workspace_id,order_id,asset_id,asset_role,input_key,sort_order,is_primary) VALUES (991004,0,991,991,991002,991002,'input','mask',0,0)");
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private static long queryLong(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) { result.next(); return result.getLong(1); }
    }

    private static String structureDiagnostics(Connection connection) throws Exception {
        String tables = "'av_discovery_banner','av_discovery_category','av_discovery_tag','av_workflow_template',"
            + "'av_workflow_execution_config','av_runninghub_account','av_workflow_order','av_workflow_task_execution',"
            + "'av_workflow_order_asset','av_file_object','av_upload_session'";
        StringBuilder diagnostics = new StringBuilder();
        appendDiagnostics(diagnostics, "11 tables and primary-key columns",
            queryText(connection, "SELECT t.table_name, t.table_type, c.column_name AS primary_key_column, c.ordinal_position "
                + "FROM information_schema.tables t LEFT JOIN (SELECT k.table_schema,k.table_name,k.column_name,k.ordinal_position "
                + "FROM information_schema.key_column_usage k WHERE k.constraint_name='PRIMARY') c "
                + "ON c.table_schema=t.table_schema AND c.table_name=t.table_name "
                + "WHERE t.table_schema=DATABASE() AND t.table_name IN (" + tables + ") "
                + "ORDER BY t.table_name,c.ordinal_position"));
        appendDiagnostics(diagnostics, "predicate-critical columns",
            queryText(connection, "SELECT table_name,column_name,is_nullable,column_default,extra,generation_expression "
                + "FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN (" + tables + ") AND column_name IN ("
                + "'channel','form_schema_json','schema_hash','billing_mode','execution_relevant_updated_at','runninghub_account_id','workflow_id','webapp_id','instance_type','timeout_seconds','enabled','last_test_status','last_test_task_id','last_test_template_revision','last_test_execution_revision','last_test_account_revision','last_test_time','last_test_summary','active_account_name','last_health_status','credential_updated_at','api_key_masked','order_no','input_payload_json','request_hash','usage_operation_id','template_title_snapshot','template_cover_snapshot_json','input_display_snapshot_json','execution_mode','submission_state','submission_started_at','submitted_at','provider_deadline_at','last_polled_at','poll_count','provider_usage_json','cost_reconciliation_status','result_manifest_json','tenant_id','owner_user_id','workspace_id','asset_role','input_key','asset_position_key','sort_order','is_primary','context_scope','row_revision','create_by','update_by','del_flag') "
                + "ORDER BY table_name,ordinal_position"));
        appendDiagnostics(diagnostics, "generated columns",
            queryText(connection, "SELECT table_name,column_name,extra,generation_expression,"
                + "REGEXP_REPLACE(REPLACE(REPLACE(LOWER(generation_expression),'`',''),'_utf8mb4',''),'[[:space:]()]','') AS normalized_expression "
                + "FROM information_schema.columns WHERE table_schema=DATABASE() AND "
                + "(table_name,column_name) IN (('av_runninghub_account','active_account_name'),('av_workflow_order_asset','asset_position_key')) "
                + "ORDER BY table_name,column_name"));
        appendDiagnostics(diagnostics, "unique-index column order",
            queryText(connection, "SELECT table_name,index_name,non_unique,GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_in_order "
                + "FROM information_schema.statistics WHERE table_schema=DATABASE() AND index_name IN ("
                + "'uk_av_ai_task_actor_idempotency','uk_av_runninghub_account_tenant_name','uk_av_workflow_execution_config_template','uk_av_workflow_order_idempotency','uk_av_workflow_task_execution_external','uk_av_workflow_order_asset','uk_av_upload_session_owner_idempotency') "
                + "GROUP BY table_name,index_name,non_unique ORDER BY table_name,index_name"));
        appendDiagnostics(diagnostics, "normalized check clauses",
            queryText(connection, "SELECT tc.table_name,tc.constraint_name,"
                + "REGEXP_REPLACE(REPLACE(REPLACE(LOWER(cc.check_clause),'`',''),'_utf8mb4',''),'[[:space:]()]','') AS normalized_check_clause "
                + "FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema=tc.constraint_schema AND cc.constraint_name=tc.constraint_name "
                + "WHERE tc.constraint_schema=DATABASE() AND tc.constraint_type='CHECK' AND tc.constraint_name IN ("
                + "'ck_av_workflow_template_status','ck_av_workflow_execution_config_last_test_status','ck_av_workflow_execution_config_mode','ck_av_workflow_task_execution_cost_reconciliation','ck_av_workflow_task_execution_resource') "
                + "ORDER BY tc.table_name,tc.constraint_name"));
        appendDiagnostics(diagnostics, "legacy columns still present",
            queryText(connection, "SELECT table_name,column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND ("
                + "(table_name='av_workflow_template' AND column_name IN ('input_schema_json','test_eligible','cover_oss_id')) OR "
                + "(table_name='av_workflow_execution_config' AND column_name IN ('account_id','credential_reference','runninghub_workflow_id','runninghub_ai_app_id','output_mapping_json','test_eligible','last_test_revision','status')) OR "
                + "(table_name='av_runninghub_account' AND column_name IN ('credential_reference','health_status','status','revision')) OR "
                + "(table_name='av_workflow_task_execution' AND column_name IN ('account_id','submission_deadline_at','poll_next_at','reconciliation_state','reconciled_at','result_payload_json','result_asset_ids_json','cost_usage','cost_reconciled_usage','error_code','error_summary','revision')) OR "
                + "(table_name='av_workflow_order' AND column_name IN ('status','request_schema_hash','input_asset_ids_json','output_asset_ids_json'))) "
                + "ORDER BY table_name,column_name"));
        return diagnostics.toString();
    }

    private static void appendDiagnostics(StringBuilder diagnostics, String label, String value) {
        diagnostics.append("--- ").append(label).append(" ---\n").append(value);
    }

    private static String queryText(Connection connection, String sql) throws Exception {
        StringBuilder result = new StringBuilder();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = rows.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                if (column > 1) result.append('|');
                result.append(metadata.getColumnLabel(column));
            }
            result.append('\n');
            while (rows.next()) {
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    if (column > 1) result.append('|');
                    String value = rows.getString(column);
                    result.append(value == null ? "<null>" : value.replace("\n", "\\n").replace("\r", "\\r"));
                }
                result.append('\n');
            }
        }
        return result.toString();
    }

    private static Path migration() {
        for (Path start : List.of(Path.of(System.getProperty("maven.multiModuleProjectDirectory", "")), Path.of(System.getProperty("user.dir")))) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) return current.resolve("../docs/sql/ai-video/mysql/20260811_01_discovery_runninghub_single_execution.sql");
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }

    private static String initializationPredicate() throws Exception {
        String sql = Files.readString(migration().resolveSibling("20260810_00_development_database_initialization.sql"), StandardCharsets.UTF_8)
            .replace("\r\n", "\n");
        String marker = "SET @development_runninghub_structure_ok = (";
        int start = sql.indexOf(marker);
        assertThat(start).isGreaterThanOrEqualTo(0);
        start += marker.length();
        int end = sql.indexOf("\n);", start);
        assertThat(end).isGreaterThan(start);
        return sql.substring(start, end).trim();
    }
}
