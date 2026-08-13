package org.dromara.aivideo.timeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class CreationTimelineMigrationContractTest {

    private static final String MIGRATION =
        "../docs/sql/ai-video/mysql/20260808_01_creation_timeline.sql";
    private static final String MIGRATION_IT =
        "ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelineMigrationIT.java";
    private static final String PERMISSION_IT =
        "ruoyi-modules/ai-video/ai-video-core/src/test/java/org/dromara/aivideo/timeline/CreationTimelinePermissionIT.java";
    private static final Set<String> TABLES = Set.of(
        "av_creation_asset",
        "av_creation_project",
        "av_timeline_draft",
        "av_timeline_version",
        "av_timeline_asset_ref",
        "av_timeline_write_receipt",
        "av_ai_task",
        "av_ai_task_execution",
        "av_ai_task_attempt"
    );
    private static final Set<String> IMMUTABLE_TABLES = Set.of(
        "av_timeline_version",
        "av_timeline_asset_ref",
        "av_timeline_write_receipt",
        "av_ai_task_execution",
        "av_ai_task_attempt"
    );
    private static final Map<Long, ExpectedPermission> PERMISSIONS = expectedPermissions();

    @Test
    void freezesNineTableNoForeignKeySchema() throws IOException {
        String sql = migrationSql();
        Matcher matcher = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+([a-z0-9_]+)").matcher(sql);
        Set<String> actual = new LinkedHashSet<>();
        int createCount = 0;
        while (matcher.find()) {
            createCount++;
            actual.add(matcher.group(1).toLowerCase());
        }

        assertThat(createCount).isEqualTo(TABLES.size());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(TABLES);
        for (String table : TABLES) {
            Matcher block = Pattern.compile(
                "(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+" + table
                    + "\\s*\\((.*?)\\)\\s*ENGINE\\s*=").matcher(sql);
            assertThat(block.find()).as("DDL block for %s", table).isTrue();
            String ddl = block.group(1);
            assertThat(ddl).containsIgnoringCase("PRIMARY KEY");
            assertThat(ddl).doesNotContainIgnoringCase("FOREIGN KEY", "tenant_id", "workspace_id");
            if (IMMUTABLE_TABLES.contains(table)) {
                assertThat(ddl).doesNotContainIgnoringCase("del_flag");
            }
            assertThat(block.find()).as("single CREATE for %s", table).isFalse();
        }
        assertThat(sql).contains(
            "content_json JSON",
            "request_payload_json JSON",
            "result_payload_json JSON",
            "result_schema_version VARCHAR",
            "response_summary_json JSON",
            "owner_user_id BIGINT",
            "actor_type VARCHAR",
            "actor_id BIGINT"
        );
        assertThat(sql).doesNotContainIgnoringCase("result_payload_schema_version");
    }

    @Test
    void freezesIndexesChecksAndImmutableResultRules() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains(
            "uk_av_creation_asset_idempotency",
            "uk_av_creation_asset_source",
            "idx_av_creation_asset_owner_status",
            "uk_av_creation_project_idempotency",
            "idx_av_creation_project_source",
            "idx_av_creation_project_base_video",
            "idx_av_creation_project_primary_audio",
            "idx_av_creation_project_output",
            "uk_av_timeline_draft_project",
            "uk_av_timeline_version_no",
            "uk_av_timeline_version_idempotency",
            "idx_av_timeline_version_history",
            "idx_av_timeline_asset_ref_document",
            "idx_av_timeline_asset_ref_asset",
            "uk_av_timeline_asset_ref_projection",
            "uk_av_timeline_write_receipt_idempotency",
            "uk_av_ai_task_idempotency",
            "idx_av_ai_task_owner_status",
            "idx_av_ai_task_resource",
            "idx_av_ai_task_result_asset",
            "uk_av_ai_task_execution_no",
            "idx_av_ai_task_execution_dispatch",
            "idx_av_ai_task_execution_recovery",
            "idx_av_ai_task_execution_result_asset",
            "uk_av_ai_task_attempt_no"
        );
        assertThat(sql).contains(
            "'manual_save', 'restored', 'render_input', 'conflict_copy'",
            "'draft_save', 'manual_version', 'version_restore', 'conflict_version'",
            "OCTET_LENGTH(CAST(request_payload_json AS CHAR)) <= 65536",
            "OCTET_LENGTH(CAST(result_payload_json AS CHAR)) <= 65536",
            "result_schema_version IS NULL",
            "result_schema_version IS NOT NULL",
            "task_type = 'timeline_render'",
            "result_asset_id IS NOT NULL",
            "result_payload_json IS NULL",
            "result_asset_id IS NULL",
            "result_payload_json IS NOT NULL"
        );
        assertThat(sql).doesNotContain("OR task_type NOT IN");
    }

    @Test
    void freezesPermissionIdsAndFailClosedGuards() throws IOException {
        String sql = migrationSql();
        String compactSql = sql.replaceAll("\\s+", " ");
        for (Map.Entry<Long, ExpectedPermission> entry : PERMISSIONS.entrySet()) {
            ExpectedPermission expected = entry.getValue();
            assertThat(compactSql).contains("(" + entry.getKey() + ", " + expected.bindingId()
                + ", '" + expected.permissionCode() + "'");
        }

        assertThat(sql).contains(
            "role_id = 1000101",
            "role_code = 'personal_creator'",
            "scope_type = 'personal'",
            "WHERE NOT EXISTS",
            "app_user.permission_revision = app_user.permission_revision + 1",
            "role_revision = role_revision + 1",
            "information_schema.COLUMNS",
            "information_schema.STATISTICS",
            "information_schema.TABLE_CONSTRAINTS"
        );
        assertThat(sql).contains(
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_ddl_guard",
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions",
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permission_guard",
            "SET @creation_timeline_permission_pre_ok",
            "PREPARE creation_timeline_permission_pre_assert_stmt",
            "SET @creation_timeline_permission_post_ok",
            "SET @creation_timeline_permission_finish_sql = IF(",
            "'COMMIT'",
            "'ROLLBACK'",
            "PREPARE creation_timeline_permission_post_assert_stmt"
        );
        assertThat(sql).doesNotContainIgnoringCase(
            "ON DUPLICATE KEY UPDATE", "MAX(id)", "SET SESSION group_concat_max_len",
            "creation_timeline_previous_group_concat_max_len");

        int firstPermissionMap = sql.indexOf("CREATE TEMPORARY TABLE tmp_creation_timeline_permissions");
        int preOk = sql.indexOf("SET @creation_timeline_permission_pre_ok");
        int preMapCleanup = sql.indexOf(
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions", preOk);
        int preAssert = sql.indexOf("PREPARE creation_timeline_permission_pre_assert_stmt", preMapCleanup);
        int secondPermissionMap = sql.indexOf(
            "CREATE TEMPORARY TABLE tmp_creation_timeline_permissions", firstPermissionMap + 1);
        int transactionStart = sql.indexOf("START TRANSACTION");
        assertThat(firstPermissionMap).isGreaterThanOrEqualTo(0).isLessThan(preOk);
        assertThat(preOk).isLessThan(preMapCleanup);
        assertThat(preMapCleanup).isLessThan(preAssert);
        assertThat(preAssert).isLessThan(secondPermissionMap);
        assertThat(secondPermissionMap).isLessThan(transactionStart);

        int postOk = sql.indexOf("SET @creation_timeline_permission_post_ok", transactionStart);
        int finishTransaction = sql.indexOf("PREPARE creation_timeline_permission_finish_stmt", postOk);
        int postMapCleanup = sql.indexOf(
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_permissions", finishTransaction);
        int postAssert = sql.indexOf("PREPARE creation_timeline_permission_post_assert_stmt", postMapCleanup);
        assertThat(transactionStart).isLessThan(postOk);
        assertThat(postOk).isLessThan(finishTransaction);
        assertThat(finishTransaction).isLessThan(postMapCleanup);
        assertThat(postMapCleanup).isLessThan(postAssert);
        assertThat(testSource(MIGRATION_IT)).doesNotContain("resetDedicatedMySqlSchema")
            .contains("@BeforeAll", "@AfterAll", "dropTargetTables()", "cleanupReservedPermissionFacts()");
        assertThat(testSource(PERMISSION_IT)).doesNotContain("resetDedicatedMySqlSchema")
            .contains("@BeforeAll", "@AfterAll", "dropTargetTables()", "cleanupConflictFacts()");
        int firstTargetCreate = TABLES.stream()
            .mapToInt(table -> sql.indexOf("CREATE TABLE IF NOT EXISTS " + table))
            .min()
            .orElseThrow();
        int globalPreflight = sql.indexOf("creation_timeline_global_preflight_ok");
        assertThat(globalPreflight).isGreaterThanOrEqualTo(0).isLessThan(firstTargetCreate);
        assertThat(sql.indexOf("binding_precondition"))
            .isLessThan(sql.indexOf("INSERT INTO app_permission"));
        assertThat(sql.indexOf("START TRANSACTION")).isGreaterThan(firstTargetCreate);
    }

    @Test
    void requiresCompleteHashBeforeAndImmediatelyAfterEveryTargetCreate() throws IOException {
        String sql = migrationSql();

        assertThat(sql).contains(
            "COLUMN_TYPE", "IS_NULLABLE", "COLUMN_DEFAULT", "EXTRA",
            "CHARACTER_SET_NAME", "COLLATION_NAME", "ORDINAL_POSITION",
            "INDEX_NAME", "NON_UNIQUE", "SEQ_IN_INDEX", "SUB_PART",
            "CHECK_CLAUSE", "ENGINE", "TABLE_COLLATION", "SHA2(",
            "FROM information_schema.TABLE_CONSTRAINTS table_constraint",
            "JOIN information_schema.CHECK_CONSTRAINTS check_constraint",
            "check_constraint.CONSTRAINT_SCHEMA = table_constraint.CONSTRAINT_SCHEMA",
            "check_constraint.CONSTRAINT_NAME = table_constraint.CONSTRAINT_NAME",
            "table_constraint.TABLE_NAME = table_info.TABLE_NAME",
            "table_constraint.CONSTRAINT_TYPE = ''CHECK''"
        );
        assertThat(sql).contains("SELECT /*+ SET_VAR(group_concat_max_len=1048576) */ SHA2(CONCAT(");
        assertThat(sql.indexOf("creation_timeline_global_preflight_ok"))
            .isLessThan(sql.indexOf("CREATE TABLE IF NOT EXISTS av_creation_asset"));
        for (String table : TABLES) {
            int create = sql.indexOf("CREATE TABLE IF NOT EXISTS " + table);
            int nextCreate = TABLES.stream()
                .mapToInt(next -> sql.indexOf("CREATE TABLE IF NOT EXISTS " + next))
                .filter(index -> index > create)
                .min()
                .orElse(sql.indexOf("START TRANSACTION"));
            String immediateTail = sql.substring(create, nextCreate);
            assertThat(immediateTail)
                .as("post-create complete fingerprint for %s", table)
                .contains("creation_timeline_post_create_" + table,
                    "EXECUTE creation_timeline_fingerprint_stmt");
        }

        int finalGuard = sql.indexOf("SET @creation_timeline_ddl_contract_ok");
        int finalGuardCleanup = sql.indexOf(
            "DROP TEMPORARY TABLE IF EXISTS tmp_creation_timeline_ddl_guard", finalGuard);
        int fingerprintCleanup = sql.indexOf(
            "DEALLOCATE PREPARE creation_timeline_fingerprint_stmt", finalGuard);
        int finalGuardAssert = sql.indexOf("PREPARE creation_timeline_ddl_assert_stmt", finalGuard);
        assertThat(finalGuard).isLessThan(finalGuardCleanup);
        assertThat(finalGuardCleanup).isLessThan(finalGuardAssert);
        assertThat(fingerprintCleanup).isLessThan(finalGuardAssert);
    }

    private static Map<Long, ExpectedPermission> expectedPermissions() {
        Map<Long, ExpectedPermission> expected = new LinkedHashMap<>();
        expected.put(1000025L, new ExpectedPermission(1000225L, "aivideo:creation:query"));
        expected.put(1000026L, new ExpectedPermission(1000226L, "aivideo:creation:edit"));
        expected.put(1000027L, new ExpectedPermission(1000227L, "aivideo:creation:generate"));
        expected.put(1000028L, new ExpectedPermission(1000228L, "aivideo:creation-asset:query"));
        expected.put(1000029L, new ExpectedPermission(1000229L, "aivideo:creation-asset:upload"));
        expected.put(1000030L, new ExpectedPermission(1000230L, "aivideo:creation-asset:delete"));
        expected.put(1000031L, new ExpectedPermission(1000231L, "aivideo:task:retry"));
        return expected;
    }

    private static String migrationSql() throws IOException {
        return testSource(MIGRATION);
    }

    private static String testSource(String relativePath) throws IOException {
        Path source = locateApiRoot().resolve(relativePath);
        assertThat(source).isRegularFile();
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private static Path locateApiRoot() {
        List<Path> starts = List.of(
            Path.of(System.getProperty("maven.multiModuleProjectDirectory", "")),
            Path.of(System.getProperty("user.dir"))
        );
        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }

    private record ExpectedPermission(long bindingId, String permissionCode) {
    }
}
