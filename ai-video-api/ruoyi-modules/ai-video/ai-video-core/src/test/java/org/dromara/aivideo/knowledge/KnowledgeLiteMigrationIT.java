package org.dromara.aivideo.knowledge;

import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class KnowledgeLiteMigrationIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final String MIGRATION = "../docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql";
    private static final String APPROVED_SEED_SHA256 =
        "6615ca1ed8b425af89287e1620ba17e859d758fc969869c7acb2129e61cabf31";
    private static final List<String> TABLES = List.of(
        "av_knowledge_item",
        "av_knowledge_version",
        "av_knowledge_binding",
        "av_video_type_rule"
    );

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException, IOException {
        ENV.resetDedicatedMySqlSchema();
        connection = ENV.openMySqlConnection();
        reloadSchema(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Test
    void createsTheFourTablesWithExactColumnsIndexesForeignKeysAndChecks() throws SQLException {
        assertThat(queryStrings("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN ('av_knowledge_item', 'av_knowledge_version',
                                 'av_knowledge_binding', 'av_video_type_rule')
            ORDER BY table_name
            """)).containsExactlyElementsOf(TABLES.stream().sorted().toList());

        assertColumns("av_knowledge_item", columns(
            "knowledge_item_id", "bigint",
            "domain_code", "varchar",
            "knowledge_type_code", "varchar",
            "stable_code", "varchar",
            "name", "varchar",
            "summary", "varchar",
            "tags_json", "json",
            "current_published_version_id", "bigint",
            "source_type", "varchar",
            "source_ref", "varchar",
            "create_dept", "bigint",
            "create_by", "bigint",
            "create_time", "datetime",
            "update_by", "bigint",
            "update_time", "datetime"
        ));
        assertColumns("av_knowledge_version", columns(
            "knowledge_version_id", "bigint",
            "knowledge_item_id", "bigint",
            "version_no", "int",
            "status", "varchar",
            "content", "longtext",
            "structure_json", "json",
            "source_summary", "varchar",
            "reviewed_by", "bigint",
            "reviewed_at", "datetime",
            "published_by", "bigint",
            "published_at", "datetime",
            "create_dept", "bigint",
            "create_by", "bigint",
            "create_time", "datetime",
            "update_by", "bigint",
            "update_time", "datetime"
        ));
        assertColumns("av_knowledge_binding", columns(
            "knowledge_binding_id", "bigint",
            "binding_group_code", "varchar",
            "version_no", "int",
            "knowledge_item_id", "bigint",
            "knowledge_version_id", "bigint",
            "industry_code", "varchar",
            "purpose_code", "varchar",
            "video_type_code", "varchar",
            "angle_codes_json", "json",
            "angle_priorities_json", "json",
            "min_duration_seconds", "int",
            "max_duration_seconds", "int",
            "priority", "int",
            "required_flag", "tinyint",
            "required_slot_codes_json", "json",
            "audience_tag_codes_json", "json",
            "exclusion_conditions_json", "json",
            "status", "varchar",
            "create_dept", "bigint",
            "create_by", "bigint",
            "create_time", "datetime",
            "update_by", "bigint",
            "update_time", "datetime"
        ));
        assertColumns("av_video_type_rule", columns(
            "video_type_rule_id", "bigint",
            "rule_code", "varchar",
            "version_no", "int",
            "video_type_code", "varchar",
            "industry_code", "varchar",
            "purpose_code", "varchar",
            "min_duration_seconds", "int",
            "max_duration_seconds", "int",
            "required_slot_codes_json", "json",
            "priority", "int",
            "copy_rules_json", "json",
            "status", "varchar",
            "published_at", "datetime",
            "create_dept", "bigint",
            "create_by", "bigint",
            "create_time", "datetime",
            "update_by", "bigint",
            "update_time", "datetime"
        ));

        assertIndex("av_knowledge_item", true, "stable_code");
        assertIndex("av_knowledge_version", true, "knowledge_item_id", "version_no");
        assertIndex("av_knowledge_binding", true, "binding_group_code", "version_no");
        assertIndex("av_video_type_rule", true, "rule_code", "version_no");
        assertIndex("av_knowledge_binding", false,
            "status", "industry_code", "purpose_code", "video_type_code",
            "min_duration_seconds", "max_duration_seconds", "priority");
        assertIndex("av_video_type_rule", false,
            "status", "industry_code", "purpose_code",
            "min_duration_seconds", "max_duration_seconds", "priority");

        assertForeignKey("av_knowledge_version", "knowledge_item_id", "av_knowledge_item", "knowledge_item_id");
        assertForeignKey("av_knowledge_binding", "knowledge_item_id", "av_knowledge_item", "knowledge_item_id");
        assertForeignKey("av_knowledge_binding", "knowledge_version_id", "av_knowledge_version", "knowledge_version_id");
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name = 'av_knowledge_item'
              AND column_name = 'current_published_version_id'
              AND referenced_table_name IS NOT NULL
            """)).isZero();

        assertCheckNames("av_knowledge_version",
            "chk_av_knowledge_version_version_no", "chk_av_knowledge_version_status");
        assertCheckNames("av_knowledge_binding",
            "chk_av_knowledge_binding_version_no", "chk_av_knowledge_binding_priority",
            "chk_av_knowledge_binding_duration", "chk_av_knowledge_binding_status");
        assertCheckNames("av_video_type_rule",
            "chk_av_video_type_rule_version_no", "chk_av_video_type_rule_priority",
            "chk_av_video_type_rule_duration", "chk_av_video_type_rule_status");

        assertRejected("""
            INSERT INTO av_knowledge_version
                (knowledge_version_id, knowledge_item_id, version_no, status, content)
            VALUES (2991, 1001, 0, 'published', 'invalid')
            """);
        assertRejected("""
            INSERT INTO av_knowledge_version
                (knowledge_version_id, knowledge_item_id, version_no, status, content)
            VALUES (2992, 1001, 99, 'invalid', 'invalid')
            """);
        assertRejected("""
            INSERT INTO av_knowledge_binding
                (knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                 knowledge_version_id, industry_code, purpose_code, video_type_code,
                 angle_codes_json, angle_priorities_json, priority, required_flag,
                 required_slot_codes_json, audience_tag_codes_json, exclusion_conditions_json, status)
            VALUES (3991, 'invalid-version', 0, 1001, 2001, '*', '*', '*',
                    JSON_ARRAY(), JSON_OBJECT(), 0, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_knowledge_binding
                (knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                 knowledge_version_id, industry_code, purpose_code, video_type_code,
                 angle_codes_json, angle_priorities_json, min_duration_seconds, max_duration_seconds,
                 priority, required_flag, required_slot_codes_json, audience_tag_codes_json,
                 exclusion_conditions_json, status)
            VALUES (3992, 'invalid-duration', 99, 1001, 2001, '*', '*', '*',
                    JSON_ARRAY(), JSON_OBJECT(), 20, 10, 0, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_knowledge_binding
                (knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                 knowledge_version_id, industry_code, purpose_code, video_type_code,
                 angle_codes_json, angle_priorities_json, priority, required_flag,
                 required_slot_codes_json, audience_tag_codes_json, exclusion_conditions_json, status)
            VALUES (3993, 'invalid-priority', 99, 1001, 2001, '*', '*', '*',
                    JSON_ARRAY(), JSON_OBJECT(), 1001, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_knowledge_binding
                (knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                 knowledge_version_id, industry_code, purpose_code, video_type_code,
                 angle_codes_json, angle_priorities_json, priority, required_flag,
                 required_slot_codes_json, audience_tag_codes_json, exclusion_conditions_json, status)
            VALUES (3994, 'invalid-status', 99, 1001, 2001, '*', '*', '*',
                    JSON_ARRAY(), JSON_OBJECT(), 0, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'invalid')
            """);
        assertRejected("""
            INSERT INTO av_video_type_rule
                (video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
                 min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                 priority, copy_rules_json, status)
            VALUES (4991, 'invalid-version', 0, '*', '*', '*', 1, 20, JSON_ARRAY(), 0, JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_video_type_rule
                (video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
                 min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                 priority, copy_rules_json, status)
            VALUES (4992, 'invalid-duration', 99, '*', '*', '*', 20, 10, JSON_ARRAY(), 0, JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_video_type_rule
                (video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
                 min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                 priority, copy_rules_json, status)
            VALUES (4993, 'invalid-priority', 99, '*', '*', '*', 1, 20, JSON_ARRAY(), -1001, JSON_ARRAY(), 'published')
            """);
        assertRejected("""
            INSERT INTO av_video_type_rule
                (video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
                 min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                 priority, copy_rules_json, status)
            VALUES (4994, 'invalid-status', 99, '*', '*', '*', 1, 20, JSON_ARRAY(), 0, JSON_ARRAY(), 'invalid')
            """);
    }

    @Test
    void insertsTheApprovedPublishedGlobalSeedSet() throws SQLException {
        assertThat(queryLong("SELECT COUNT(*) FROM av_knowledge_item")).isEqualTo(4L);
        assertThat(queryLong("SELECT COUNT(*) FROM av_knowledge_version")).isEqualTo(4L);
        assertThat(queryLong("SELECT COUNT(*) FROM av_knowledge_binding")).isEqualTo(4L);
        assertThat(queryLong("SELECT COUNT(*) FROM av_video_type_rule")).isEqualTo(2L);

        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM av_knowledge_item item
            JOIN av_knowledge_version version
              ON version.knowledge_version_id = item.current_published_version_id
             AND version.knowledge_item_id = item.knowledge_item_id
             AND version.status = 'published'
            WHERE item.domain_code = 'copywriting'
              AND item.source_ref = 'k0-seed-20260803-01'
            """)).isEqualTo(4L);
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM av_knowledge_binding
            WHERE industry_code = '*'
              AND purpose_code = '*'
              AND video_type_code = '*'
              AND required_flag = 0
              AND min_duration_seconds IS NULL
              AND max_duration_seconds IS NULL
              AND JSON_LENGTH(angle_codes_json) = 0
              AND JSON_TYPE(angle_priorities_json) = 'OBJECT'
              AND JSON_LENGTH(angle_priorities_json) = 0
              AND JSON_LENGTH(required_slot_codes_json) = 0
              AND JSON_LENGTH(audience_tag_codes_json) = 0
              AND JSON_LENGTH(exclusion_conditions_json) = 0
              AND status = 'published'
            """)).isEqualTo(4L);
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM av_video_type_rule
            WHERE industry_code = '*'
              AND purpose_code = '*'
              AND video_type_code = '*'
              AND JSON_LENGTH(required_slot_codes_json) = 0
              AND status = 'published'
              AND published_at = '2026-08-03 00:00:00'
            """)).isEqualTo(2L);

        assertThat(queryStringMap("""
            SELECT item.stable_code, version.content
            FROM av_knowledge_item item
            JOIN av_knowledge_version version ON version.knowledge_version_id = item.current_published_version_id
            ORDER BY item.stable_code
            """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "global_benefit_hook", "开头先给出具体利益点，再说明适用人群。",
                "global_product_proof", "正文必须使用用户提供且可验证的产品事实支撑卖点。",
                "global_action_prompt", "结尾给出一个明确、可执行且不过度承诺的行动。",
                "global_claim_boundary", "不得虚构价格、活动、功效、销量或用户未提供的事实。"
            ));
        assertThat(queryStringMap("""
            SELECT stable_code, CAST(tags_json AS CHAR)
            FROM av_knowledge_item
            ORDER BY stable_code
            """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "global_benefit_hook", "[\"hook\"]",
                "global_product_proof", "[\"proof\"]",
                "global_action_prompt", "[\"cta\"]",
                "global_claim_boundary", "[\"compliance\"]"
            ));
        assertThat(queryStringMap("""
            SELECT binding_group_code,
                   CONCAT_WS('\t', industry_code, purpose_code, video_type_code,
                       CAST(priority AS CHAR), CAST(required_flag AS CHAR),
                       COALESCE(CAST(min_duration_seconds AS CHAR), 'null'),
                       COALESCE(CAST(max_duration_seconds AS CHAR), 'null'),
                       CAST(angle_codes_json AS CHAR), CAST(angle_priorities_json AS CHAR),
                       CAST(required_slot_codes_json AS CHAR), CAST(audience_tag_codes_json AS CHAR),
                       CAST(exclusion_conditions_json AS CHAR), status)
            FROM av_knowledge_binding
            ORDER BY binding_group_code
            """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "global_benefit_hook", "*\t*\t*\t100\t0\tnull\tnull\t[]\t{}\t[]\t[]\t[]\tpublished",
                "global_product_proof", "*\t*\t*\t80\t0\tnull\tnull\t[]\t{}\t[]\t[]\t[]\tpublished",
                "global_action_prompt", "*\t*\t*\t60\t0\tnull\tnull\t[]\t{}\t[]\t[]\t[]\tpublished",
                "global_claim_boundary", "*\t*\t*\t40\t0\tnull\tnull\t[]\t{}\t[]\t[]\t[]\tpublished"
            ));
        assertThat(queryStringMap("""
            SELECT rule_code,
                   CONCAT_WS('\t', industry_code, purpose_code, video_type_code,
                       CAST(min_duration_seconds AS CHAR), CAST(max_duration_seconds AS CHAR),
                       CAST(priority AS CHAR), CAST(required_slot_codes_json AS CHAR),
                       CAST(copy_rules_json AS CHAR), status)
            FROM av_video_type_rule
            ORDER BY rule_code
            """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "short_20s_structure",
                "*\t*\t*\t1\t20\t100\t[]\t[\"20秒内：1句钩子+2句卖点+1句行动号召\", \"禁止虚构价格或效果\"]\tpublished",
                "standard_60s_structure",
                "*\t*\t*\t21\t60\t90\t[]\t[\"21至60秒：钩子、痛点、卖点、证据、行动号召依次展开\", \"禁止虚构价格或效果\"]\tpublished"
            ));
    }

    @Test
    void remainsByteStableAcrossReplayAndDedicatedSchemaRebuild() throws Exception {
        Map<String, Long> countsBefore = seedCounts();
        Map<String, String> contentBefore = seedContent();
        Map<String, String> updateTimesBefore = seedUpdateTimes();
        String shaBefore = canonicalSeedSha256();
        assertThat(shaBefore).isEqualTo(APPROVED_SEED_SHA256);

        executeSqlScript(connection, locateApiRoot().resolve(MIGRATION));

        assertThat(seedCounts()).isEqualTo(countsBefore);
        assertThat(seedContent()).isEqualTo(contentBefore);
        assertThat(seedUpdateTimes()).isEqualTo(updateTimesBefore);
        assertThat(canonicalSeedSha256()).isEqualTo(shaBefore);

        ENV.resetDedicatedMySqlSchema();
        reloadSchema(connection);

        assertThat(seedCounts()).isEqualTo(countsBefore);
        assertThat(seedContent()).isEqualTo(contentBefore);
        assertThat(seedUpdateTimes()).isEqualTo(updateTimesBefore);
        assertThat(canonicalSeedSha256()).isEqualTo(shaBefore);
    }

    private void assertColumns(String tableName, Map<String, String> expected) throws SQLException {
        Map<String, String> actual = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT column_name, data_type
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = ?
            ORDER BY ordinal_position
            """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    actual.put(resultSet.getString(1), resultSet.getString(2));
                }
            }
        }
        assertThat(actual).isEqualTo(expected);
    }

    private void assertIndex(String tableName, boolean unique, String... columns) throws SQLException {
        List<String> indexes = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',')
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND non_unique = ?
            GROUP BY index_name
            """)) {
            statement.setString(1, tableName);
            statement.setInt(2, unique ? 0 : 1);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    indexes.add(resultSet.getString(1));
                }
            }
        }
        assertThat(indexes).contains(String.join(",", columns));
    }

    private void assertForeignKey(String tableName, String columnName,
                                  String referencedTable, String referencedColumn) throws SQLException {
        assertThat(queryLong("""
            SELECT COUNT(*)
            FROM information_schema.key_column_usage
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND column_name = ?
              AND referenced_table_name = ?
              AND referenced_column_name = ?
            """, tableName, columnName, referencedTable, referencedColumn)).isEqualTo(1L);
    }

    private void assertCheckNames(String tableName, String... expectedNames) throws SQLException {
        List<String> actual = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT constraint_name
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = ?
              AND constraint_type = 'CHECK'
            ORDER BY constraint_name
            """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    actual.add(resultSet.getString(1));
                }
            }
        }
        assertThat(actual).containsExactlyInAnyOrder(expectedNames);
    }

    private void assertRejected(String sql) {
        assertThatThrownBy(() -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }).isInstanceOf(SQLException.class);
    }

    private Map<String, Long> seedCounts() throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : TABLES) {
            counts.put(table, queryLong("SELECT COUNT(*) FROM " + table));
        }
        return counts;
    }

    private Map<String, String> seedContent() throws SQLException {
        return queryStringMap("""
            SELECT item.stable_code, version.content
            FROM av_knowledge_item item
            JOIN av_knowledge_version version ON version.knowledge_version_id = item.current_published_version_id
            ORDER BY item.stable_code
            """);
    }

    private Map<String, String> seedUpdateTimes() throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        appendUpdateTimes(result, "av_knowledge_item", "knowledge_item_id");
        appendUpdateTimes(result, "av_knowledge_version", "knowledge_version_id");
        appendUpdateTimes(result, "av_knowledge_binding", "knowledge_binding_id");
        appendUpdateTimes(result, "av_video_type_rule", "video_type_rule_id");
        return result;
    }

    private void appendUpdateTimes(Map<String, String> target, String table, String idColumn) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(
            "SELECT " + idColumn + ", DATE_FORMAT(update_time, '%Y-%m-%d %H:%i:%s.%f') FROM "
                + table + " ORDER BY " + idColumn)) {
            while (resultSet.next()) {
                target.put(table + ':' + resultSet.getString(1), resultSet.getString(2));
            }
        }
    }

    private String canonicalSeedSha256() throws SQLException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateDigest(digest, "av_knowledge_item", """
            SELECT knowledge_item_id, domain_code, knowledge_type_code, stable_code, name, summary,
                   tags_json, current_published_version_id, source_type, source_ref,
                   create_dept, create_by, create_time, update_by, update_time
            FROM av_knowledge_item ORDER BY knowledge_item_id
            """);
        updateDigest(digest, "av_knowledge_version", """
            SELECT knowledge_version_id, knowledge_item_id, version_no, status, content, structure_json,
                   source_summary, reviewed_by, reviewed_at, published_by, published_at,
                   create_dept, create_by, create_time, update_by, update_time
            FROM av_knowledge_version ORDER BY knowledge_version_id
            """);
        updateDigest(digest, "av_knowledge_binding", """
            SELECT knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                   knowledge_version_id, industry_code, purpose_code, video_type_code, angle_codes_json,
                   angle_priorities_json, min_duration_seconds, max_duration_seconds, priority,
                   required_flag, required_slot_codes_json, audience_tag_codes_json,
                   exclusion_conditions_json, status, create_dept, create_by, create_time, update_by, update_time
            FROM av_knowledge_binding ORDER BY knowledge_binding_id
            """);
        updateDigest(digest, "av_video_type_rule", """
            SELECT video_type_rule_id, rule_code, version_no, video_type_code, industry_code,
                   purpose_code, min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                   priority, copy_rules_json, status, published_at,
                   create_dept, create_by, create_time, update_by, update_time
            FROM av_video_type_rule ORDER BY video_type_rule_id
            """);
        return HexFormat.of().formatHex(digest.digest());
    }

    private void updateDigest(MessageDigest digest, String tableName, String sql) throws SQLException {
        digest.update(tableName.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            while (resultSet.next()) {
                for (int column = 1; column <= metadata.getColumnCount(); column++) {
                    String value = resultSet.getString(column);
                    digest.update((value == null ? "<null>" : value).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                }
                digest.update((byte) '\n');
            }
        }
    }

    private long queryLong(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong(1);
            }
        }
    }

    private String queryString(String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString(1);
            }
        }
    }

    private Map<String, String> queryStringMap(String sql) throws SQLException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                result.put(resultSet.getString(1), resultSet.getString(2));
            }
        }
        return result;
    }

    private List<String> queryStrings(String sql) throws SQLException {
        List<String> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                result.add(resultSet.getString(1));
            }
        }
        return result;
    }

    private static void reloadSchema(Connection connection) throws SQLException, IOException {
        Path apiRoot = locateApiRoot();
        executeSqlScript(connection, apiRoot.resolve("../docs/sql/ry_vue.sql"));
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
        executeSqlScript(connection, apiRoot.resolve(MIGRATION));
    }

    private static Path locateApiRoot() {
        List<Path> starts = new ArrayList<>();
        String mavenProjectDirectory = System.getProperty("maven.multiModuleProjectDirectory");
        if (mavenProjectDirectory != null && !mavenProjectDirectory.isBlank()) {
            starts.add(Path.of(mavenProjectDirectory));
        }
        starts.add(Path.of(System.getProperty("user.dir")));

        for (Path start : starts) {
            for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
                if (Files.isRegularFile(current.resolve("../docs/sql/ry_vue.sql"))) {
                    return current;
                }
            }
        }
        throw new IllegalStateException("无法定位包含 ../docs/sql/ry_vue.sql 的 ai-video-api 根目录");
    }

    private static void executeSqlScript(Connection connection, Path script) throws SQLException, IOException {
        if (Files.notExists(script)) {
            throw new NoSuchFileException(script.toString());
        }
        ScriptUtils.executeSqlScript(
            connection,
            new EncodedResource(new FileSystemResource(script), StandardCharsets.UTF_8)
        );
    }

    private static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private static Map<String, String> columns(String... entries) {
        Map<String, String> columns = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            columns.put(entries[index], entries[index + 1]);
        }
        return columns;
    }
}
