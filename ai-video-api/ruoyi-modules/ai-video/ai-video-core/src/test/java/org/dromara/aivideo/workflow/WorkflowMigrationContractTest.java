package org.dromara.aivideo.workflow;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class WorkflowMigrationContractTest {

    private static final String MIGRATION = "../docs/sql/ai-video/mysql/20260811_01_discovery_runninghub_single_execution.sql";
    private static final String WORKSPACE_KEY_MIGRATION = "../docs/sql/ai-video/mysql/20260812_01_workflow_workspace_key.sql";
    private static final String RUNNINGHUB_UPLOAD_MIGRATION = "../docs/sql/ai-video/mysql/20260812_03_workflow_upload_session_runninghub_reference.sql";
    private static final String RUNNINGHUB_INSTANCE_TYPE_DICT_MIGRATION = "../docs/sql/ai-video/mysql/20260812_06_runninghub_instance_type_dictionary.sql";
    private static final String RUNNINGHUB_ASSET_REFERENCE_MIGRATION = "../docs/sql/ai-video/mysql/20260812_07_workflow_asset_owner_object_key.sql";
    private static final String INIT_MIGRATION = "../docs/sql/ai-video/mysql/20260810_00_development_database_initialization.sql";
    private static final String MENU_MIGRATION = "../docs/sql/ai-video/mysql/20260811_02_discovery_runninghub_admin_menu.sql";
    private static final List<String> TABLES = List.of(
        "av_discovery_banner", "av_discovery_category", "av_discovery_tag", "av_workflow_template",
        "av_workflow_execution_config", "av_runninghub_account", "av_workflow_order",
        "av_workflow_task_execution", "av_workflow_order_asset", "av_file_object", "av_upload_session"
    );

    @Test
    void definesForwardOnlySingleExecutionFactsWithoutPhysicalForeignKeys() throws IOException {
        String sql = read(MIGRATION);

        for (String table : TABLES) {
            assertThat(createTable(sql, table)).startsWith("CREATE TABLE IF NOT EXISTS " + table + " ");
        }
        assertThat(sql).doesNotContain("FOREIGN KEY", "self_hosted_comfyui", "template_version", "config_snapshot");
        assertThat(sql).doesNotContain("ADD COLUMN IF NOT EXISTS");

        String executionConfig = createTable(sql, "av_workflow_execution_config");
        assertThat(executionConfig).contains(
            "uk_av_workflow_execution_config_template UNIQUE (tenant_id,template_id)",
            "runninghub_account_id", "workflow_id", "webapp_id", "instance_type VARCHAR(16) NULL",
            "last_test_template_revision", "last_test_execution_revision", "last_test_account_revision",
            "last_test_task_id", "last_test_time", "last_test_summary",
            "row_revision", "timeout_seconds", "enabled",
            "create_by BIGINT NULL", "update_by BIGINT NULL", "del_flag CHAR(1) NOT NULL DEFAULT '0'",
            "CHECK (last_test_status IN ('never','running','success','failed'))",
            "CHECK ((execution_mode = 'runninghub_workflow' AND workflow_id IS NOT NULL AND webapp_id IS NULL) "
                + "OR (execution_mode = 'runninghub_ai_app' AND webapp_id IS NOT NULL AND workflow_id IS NULL))"
        ).doesNotContain(
            "last_test_revision", "test_eligible", "credential_reference", "output_mapping_json",
            ", status VARCHAR", "'not_tested'", "'passed'", ", account_id BIGINT",
            "runninghub_workflow_id", "runninghub_ai_app_id"
        );

        String account = createTable(sql, "av_runninghub_account");
        assertThat(account).contains(
            "api_key_ciphertext TEXT NOT NULL", "last_health_status", "credential_updated_at", "enabled",
            "row_revision BIGINT NOT NULL DEFAULT 0",
            "create_by BIGINT NULL", "update_by BIGINT NULL", "del_flag CHAR(1) NOT NULL DEFAULT '0'",
            "active_account_name VARCHAR(128) GENERATED ALWAYS AS "
                + "(CASE WHEN del_flag = '0' THEN account_name ELSE NULL END) STORED",
            "UNIQUE KEY uk_av_runninghub_account_tenant_name (tenant_id,active_account_name)"
        ).doesNotContain(
            "credential_reference", ", revision BIGINT NOT NULL DEFAULT 0",
            "UNIQUE KEY uk_av_runninghub_account_tenant_name (tenant_id,account_name)"
        );

        String order = createTable(sql, "av_workflow_order");
        assertThat(order).contains(
            "uk_av_workflow_order_idempotency UNIQUE (tenant_id,workspace_id,owner_user_id,idempotency_key)",
            "order_no", "schema_hash", "input_payload_json", "request_hash", "billing_mode", "usage_operation_id",
            "template_title_snapshot", "template_cover_snapshot_json", "input_display_snapshot_json"
        ).doesNotContain(
            ", status VARCHAR", "request_schema_hash", "input_asset_ids_json", "output_asset_ids_json"
        );
        assertThat(createTable(sql, "av_workflow_task_execution")).contains(
            "runninghub_account_id BIGINT NOT NULL",
            "uk_av_workflow_task_execution_external UNIQUE (runninghub_account_id,external_task_id)",
            "resource_type = 'workflow_order' AND order_id IS NOT NULL"
        ).doesNotContain(
            ", account_id BIGINT", "UNIQUE (account_id,external_task_id)",
            "runninghub_workflow_id", "runninghub_ai_app_id"
        );
        assertThat(sql).contains(
            "actor_type=''app_user'' AND owner_user_id IS NOT NULL AND actor_id=owner_user_id",
            "actor_type=''sys_user'' AND owner_user_id IS NULL",
            "timeline-free-1", "workflow-free-1",
            "ALTER TABLE av_asset ADD COLUMN file_id",
            "ALTER TABLE av_asset ADD COLUMN thumbnail_file_id",
            "ALTER TABLE av_asset ADD COLUMN reference_count"
        );
    }

    @Test
    void seedsRunningHubInstanceTypeThroughTheSystemDictionary() throws IOException {
        String sql = read(RUNNINGHUB_INSTANCE_TYPE_DICT_MIGRATION);

        assertThat(sql).contains(
            "'aivideo_runninghub_instance_type'",
            "'标准（24GB）', 'default'",
            "'Plus（48GB）', 'plus'"
        );
    }

    @Test
    void upgradesWorkflowWorkspaceColumnsToTheOpaqueSessionWorkspaceKey() throws IOException {
        String sql = read(WORKSPACE_KEY_MIGRATION);

        assertThat(sql).contains(
            "ALTER TABLE av_workflow_order MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL",
            "ALTER TABLE av_workflow_order_asset MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL",
            "ALTER TABLE av_file_object MODIFY COLUMN workspace_id VARCHAR(128) NULL",
            "ALTER TABLE av_upload_session MODIFY COLUMN workspace_id VARCHAR(128) NOT NULL"
        );
        assertThat(sql).doesNotContain("DROP TABLE", "DELETE FROM", "TRUNCATE");
    }

    @Test
    void requiresRunningHubBackedUploadSessionMetadataBeforeSeedingDevelopmentData() throws IOException {
        String migration = read(RUNNINGHUB_UPLOAD_MIGRATION);
        String initialization = read(INIT_MIGRATION);

        assertThat(migration).contains(
            "asset_id BIGINT NULL", "original_file_name VARCHAR(255) NULL",
            "declared_content_type VARCHAR(128) NULL", "declared_size_bytes BIGINT NULL",
            "runninghub_file_name VARCHAR(512) NULL");
        assertThat(initialization).contains(
            "'asset_id','original_file_name','declared_content_type','declared_size_bytes','runninghub_file_name'");
    }

    @Test
    void allowsEachRunningHubUploadToCreateItsOwnAssetRecord() throws IOException {
        String sql = read(RUNNINGHUB_ASSET_REFERENCE_MIGRATION);

        assertThat(sql).contains("DROP INDEX uk_av_asset_object_key", "DROP INDEX uk_av_asset_owner_object_key")
            .doesNotContain("ADD CONSTRAINT uk_av_asset_owner_object_key", "UNIQUE (tenant_id,workspace_id,owner_id,object_key)");
    }

    @Test
    void replacesLegacyTaskConstraintsAndPersistsExecutionFacts() throws IOException {
        String sql = read(MIGRATION);

        assertThat(sql).contains(
            "DROP INDEX uk_av_ai_task_idempotency", "uk_av_ai_task_actor_idempotency",
            "DROP INDEX uk_av_ai_task_execution_no", "uk_av_ai_task_execution_no UNIQUE (task_id, execution_no)",
            "DROP INDEX uk_av_ai_task_attempt_no", "uk_av_ai_task_attempt_no UNIQUE (task_execution_id, attempt_no)",
            "DROP CHECK ck_av_ai_task_actor", "DROP CHECK ck_av_ai_task_execution_actor", "DROP CHECK ck_av_ai_task_attempt_actor",
            "workflow-free-1", "workflow_template_test", "runninghub_ai_app"
        );
        assertThat(createTable(sql, "av_workflow_order")).contains("workspace_id", "input_payload_json", "request_hash");
        assertThat(createTable(sql, "av_upload_session")).contains("workspace_id", "context_scope");

        String execution = createTable(sql, "av_workflow_task_execution");
        assertThat(execution).contains(
            "submission_started_at", "submitted_at", "provider_deadline_at", "last_polled_at", "poll_count",
            "provider_usage_json", "result_manifest_json",
            "CHECK (cost_reconciliation_status IN ('not_reported','reported','unknown'))"
        ).doesNotContain(
            "reconciliation_state", "submission_deadline_at", "poll_next_at",
            "result_payload_json", "result_asset_ids_json", "cost_usage", "cost_reconciled_usage",
            "'not_required'", "'pending'", "'reconciled'"
        );
    }

    @Test
    void preservesTimelineResultShapesAndFreezesRunningHubFacts() throws IOException {
        String sql = read(MIGRATION);
        assertThat(sql).contains(
            "timeline_render", "result_asset_id IS NOT NULL", "result_schema_version IS NULL",
            "timeline_image_prompt_generate", "result_payload_json IS NOT NULL",
            "workflow_template_generate", "workflow_template_test"
        );

        assertThat(createTable(sql, "av_workflow_template")).contains(
            "description MEDIUMTEXT NULL", "form_schema_json", "execution_relevant_updated_at", "channel",
            "billing_mode", "row_revision",
            "create_by BIGINT NULL", "update_by BIGINT NULL", "del_flag CHAR(1) NOT NULL DEFAULT '0'",
            "CHECK (status IN ('draft','pending_test','enabled','disabled'))"
        );
        assertThat(createTable(sql, "av_workflow_execution_config"))
            .contains("access_password_ciphertext TEXT NULL");
        assertThat(createTable(sql, "av_runninghub_account")).contains("api_key_masked", "last_health_status", "row_revision");
        assertThat(sql).contains(
            "ALTER TABLE av_workflow_template MODIFY COLUMN description MEDIUMTEXT NULL",
            "ALTER TABLE av_runninghub_account MODIFY COLUMN api_key_ciphertext TEXT NOT NULL",
            "ALTER TABLE av_workflow_execution_config MODIFY COLUMN access_password_ciphertext TEXT NULL",
            "data_type<>'mediumtext'", "data_type<>'text'"
        );

        String orderAsset = createTable(sql, "av_workflow_order_asset");
        assertThat(orderAsset).contains(
            "tenant_id", "owner_user_id", "workspace_id", "asset_role", "input_key", "sort_order", "is_primary",
            "asset_position_key VARCHAR(128) GENERATED ALWAYS AS (COALESCE(input_key,'')) STORED",
            "UNIQUE KEY uk_av_workflow_order_asset "
                + "(order_id,asset_role,asset_position_key,sort_order,asset_id)"
        ).doesNotContain(
            "UNIQUE KEY uk_av_workflow_order_asset (order_id,asset_role,input_key,sort_order,asset_id)"
        );

        String uploadSession = createTable(sql, "av_upload_session");
        assertThat(uploadSession).contains(
            "tenant_id", "workspace_id", "owner_user_id", "context_scope",
            "UNIQUE KEY uk_av_upload_session_owner_idempotency (tenant_id,workspace_id,owner_user_id,idempotency_key)"
        ).doesNotContain(
            "UNIQUE KEY uk_av_upload_session_owner_idempotency (owner_user_id,idempotency_key)"
        );
    }

    @Test
    void grantsExactWorkflowAdministrationAndAssetPermissions() throws IOException {
        String menuSql = read(MENU_MIGRATION);
        assertThat(menuSql).contains(
            "aivideo/workflow-template/index", "aivideo/runninghub-account/index"
        ).doesNotContain(
            "aivideo/discovery-home/index", "aivideo/workflow-order/index"
        );
        assertThat(menuSql).contains(
            "aivideo:workflow-template:query", "aivideo:workflow-template:add",
            "aivideo:workflow-template:edit", "aivideo:workflow-template:remove",
            "aivideo:workflow-template:enable", "aivideo:workflow-template:disable",
            "aivideo:runninghub-account:query", "aivideo:runninghub-account:add",
            "aivideo:runninghub-account:edit", "aivideo:runninghub-account:remove",
            "aivideo:runninghub-account:enable", "aivideo:runninghub-account:disable"
        ).doesNotContain(
            "aivideo:discover-home:query", "aivideo:discover-home:edit",
            "aivideo:workflow-template:inspect", "aivideo:workflow-template:test",
            "aivideo:runninghub-account:test", "aivideo:runninghub-account:update-key",
            "aivideo:workflow-order:query", "aivideo:workflow-order:asset-access"
        );
        assertThat(menuSql).contains(
            "DELETE FROM sys_role_menu WHERE menu_id IN (1761400000000020200,1761400000000020201,1761400000000020202,1761400000000020215,1761400000000020216,1761400000000020225,1761400000000020228,1761400000000020230,1761400000000020231,1761400000000020232)",
            "DELETE FROM sys_menu WHERE menu_id IN (1761400000000020200,1761400000000020201,1761400000000020202,1761400000000020215,1761400000000020216,1761400000000020225,1761400000000020228,1761400000000020230,1761400000000020231,1761400000000020232)"
        );

        String initSql = read(INIT_MIGRATION);
        assertThat(normalizeSql(initSql)).contains(
            normalizeSql("(SELECT COUNT(*) FROM sys_menu WHERE component IN ('aivideo/workflow-template/index','aivideo/runninghub-account/index')) = 2"),
            normalizeSql("(SELECT COUNT(DISTINCT perms) FROM sys_menu WHERE perms IN ('aivideo:workflow-template:query','aivideo:workflow-template:add','aivideo:workflow-template:edit','aivideo:workflow-template:remove','aivideo:workflow-template:enable','aivideo:workflow-template:disable','aivideo:runninghub-account:query','aivideo:runninghub-account:add','aivideo:runninghub-account:edit','aivideo:runninghub-account:remove','aivideo:runninghub-account:enable','aivideo:runninghub-account:disable')) = 12"),
            normalizeSql("NOT EXISTS (SELECT 1 FROM sys_menu WHERE component IN ('aivideo/discovery-home/index','aivideo/workflow-order/index'))"),
            normalizeSql("NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms IN ('aivideo:discover-home:query','aivideo:discover-home:edit','aivideo:workflow-template:inspect','aivideo:workflow-template:test','aivideo:runninghub-account:test','aivideo:runninghub-account:update-key','aivideo:workflow-order:query','aivideo:workflow-order:asset-access'))")
        );
        String sql = read(MIGRATION);
        assertThat(sql).contains("aivideo:asset:query", "aivideo:asset:upload", "aivideo:asset:download", "personal_creator");
    }

    @Test
    void seedsDefaultDiscoveryCategoryThroughSystemDictionary() throws IOException {
        String initSql = normalizeSql(read(INIT_MIGRATION));

        assertThat(initSql).contains(
            "aivideo_discovery_category",
            normalizeSql("INSERT IGNORE INTO sys_dict_type"),
            normalizeSql("INSERT INTO sys_dict_data")
        );
    }

    @Test
    void verifiesNamedCheckConstraintClausesInDevelopmentGuard() throws IOException {
        String initSql = read(INIT_MIGRATION);

        assertCheckGuard(initSql, "ck_av_workflow_template_status",
            "status IN ('draft','pending_test','enabled','disabled')");
        assertCheckGuard(initSql, "ck_av_workflow_execution_config_last_test_status",
            "last_test_status IN ('never','running','success','failed')");
        assertCheckGuard(initSql, "ck_av_workflow_execution_config_mode",
            "execution_mode = 'runninghub_workflow' AND workflow_id IS NOT NULL AND webapp_id IS NULL "
                + "OR execution_mode = 'runninghub_ai_app' AND webapp_id IS NOT NULL AND workflow_id IS NULL");
        assertCheckGuard(initSql, "ck_av_workflow_task_execution_cost_reconciliation",
            "cost_reconciliation_status IN ('not_reported','reported','unknown')");
        assertCheckGuard(initSql, "ck_av_workflow_task_execution_resource",
            "resource_type = 'workflow_order' AND order_id IS NOT NULL "
                + "OR resource_type = 'workflow_template' AND order_id IS NULL");
        assertDataTypeGuard(initSql, "av_workflow_template", "description", "mediumtext");
        assertDataTypeGuard(initSql, "av_runninghub_account", "api_key_ciphertext", "text");
        assertDataTypeGuard(initSql, "av_workflow_execution_config", "access_password_ciphertext", "text");
    }

    @Test
    void rejectsDeprecatedWorkflowOrderColumnsInDevelopmentGuard() throws IOException {
        String initSql = normalizeSql(read(INIT_MIGRATION));

        assertDeprecatedColumnsGuard(initSql, "av_workflow_template",
            "'input_schema_json','test_eligible','cover_oss_id'");
        assertDeprecatedColumnsGuard(initSql, "av_workflow_execution_config",
            "'account_id','credential_reference','runninghub_workflow_id','runninghub_ai_app_id',"
                + "'output_mapping_json','test_eligible','last_test_revision','status'");
        assertDeprecatedColumnsGuard(initSql, "av_runninghub_account",
            "'credential_reference','health_status','status','revision'");
        assertDeprecatedColumnsGuard(initSql, "av_workflow_task_execution",
            "'account_id','submission_deadline_at','poll_next_at','reconciliation_state','reconciled_at',"
                + "'result_payload_json','result_asset_ids_json','cost_usage','cost_reconciled_usage',"
                + "'error_code','error_summary','revision'");
        assertDeprecatedColumnsGuard(initSql, "av_workflow_order",
            "'status','request_schema_hash','input_asset_ids_json','output_asset_ids_json'");
    }

    private static String read(String relativePath) throws IOException {
        Path source = locateApiRoot().resolve(relativePath);
        assertThat(source).isRegularFile();
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static String createTable(String sql, String table) {
        String marker = "CREATE TABLE IF NOT EXISTS " + table + " ";
        int start = sql.indexOf(marker);
        assertThat(start).as("CREATE TABLE statement for %s", table).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(';', start);
        assertThat(end).as("terminating semicolon for %s", table).isGreaterThan(start);
        return sql.substring(start, end + 1);
    }

    private static void assertCheckGuard(String initSql, String constraintName, String expectedExpression) {
        String guard = initSql.lines()
            .filter(line -> line.contains("constraint_name='" + constraintName + "'"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing initialization guard for " + constraintName));
        assertThat(normalizeSql(guard)).contains(
            "information_schema.check_constraints", "check_clause", normalizeSql(expectedExpression)
        );
    }

    private static void assertDeprecatedColumnsGuard(String initSql, String tableName, String deprecatedColumns) {
        assertThat(initSql).contains(
            "notexistsselect1frominformation_schema.columnswhere"
                + "table_schema=databaseandtable_name='" + tableName + "'andcolumn_namein"
                + deprecatedColumns
        );
    }

    private static void assertDataTypeGuard(String initSql, String tableName, String columnName, String dataType) {
        assertThat(normalizeSql(initSql)).contains(normalizeSql(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"
                + tableName + "' AND column_name='" + columnName + "' AND data_type='" + dataType + "') = 1"
        ));
    }

    private static String normalizeSql(String sql) {
        return sql.toLowerCase(java.util.Locale.ROOT)
            .replace("`", "")
            .replace("''", "'")
            .replaceAll("[\\s()]", "");
    }

    private static Path locateApiRoot() {
        for (Path start : List.of(Path.of(System.getProperty("maven.multiModuleProjectDirectory", "")), Path.of(System.getProperty("user.dir")))) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }
}
