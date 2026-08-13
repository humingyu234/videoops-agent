package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSessionFactory;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;
import org.dromara.aivideo.knowledge.mapper.KnowledgeBindingMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeItemMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeVersionMapper;
import org.dromara.aivideo.knowledge.mapper.VideoTypeRuleMapper;
import org.dromara.aivideo.knowledge.service.IKnowledgeContextService;
import org.dromara.aivideo.knowledge.service.impl.KnowledgeContextServiceImpl;
import org.dromara.aivideo.testsupport.LocalIntegrationEnvironment;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class KnowledgeContextQueryIT {

    private static final LocalIntegrationEnvironment ENV = LocalIntegrationEnvironment.requireFromEnvironment();
    private static final String EMPTY_HASH = "62dffd7d09a50ad03b651edf697d9ab42a09c9607973ab89036bc2b6abb67e34";

    private Connection connection;
    private AnnotationConfigApplicationContext applicationContext;
    private IKnowledgeContextService service;
    private JsonMapper jsonMapper;

    @BeforeEach
    void setUp() throws Exception {
        ENV.resetDedicatedMySqlSchema();
        connection = ENV.openMySqlConnection();
        reloadSchema(connection);

        applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.getEnvironment().getPropertySources().addFirst(new MapPropertySource("knowledge-it", Map.of(
            "knowledge.jdbc-url", ENV.jdbcUrl(),
            "knowledge.username", ENV.mysqlUsername(),
            "knowledge.password", ENV.mysqlPassword()
        )));
        applicationContext.register(KnowledgeContextQueryConfiguration.class);
        applicationContext.refresh();
        service = applicationContext.getBean(IKnowledgeContextService.class);
        jsonMapper = applicationContext.getBean(JsonMapper.class);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (applicationContext != null) {
            applicationContext.close();
            applicationContext = null;
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    @Test
    void queriesFourLayersDeterministicallyWithAllTieBreakersDurationsAndExclusions() throws Exception {
        insertRoutingFixture();
        KnowledgeContextRequestDTO shortRequest =
            new KnowledgeContextRequestDTO("food", "store_traffic", 15, List.of("fresh", "value"));

        KnowledgeContextDTO first = service.resolve(shortRequest);
        byte[] firstCanonical = canonicalBytes(first);
        KnowledgeContextDTO second = service.resolve(shortRequest);

        assertThat(first.knowledgeVersionIds()).containsExactly(
            9201L, 9202L, 9203L, 9204L, 9205L, 9206L, 9207L, 2001L, 2002L, 2003L, 2004L);
        assertThat(first.knowledgeVersionIds()).doesNotContain(9291L, 9208L, 9209L);
        assertThat(first.excerpts()).startsWith(
            "exact tag winner", "priority winner", "stable a", "stable b",
            "exact wildcard", "wild exact", "wild wildcard");
        assertThat(first.copyRules()).containsExactly(
            "exact", "shared", "exact-wild", "wild-exact", "wild-wild",
            "20秒内：1句钩子+2句卖点+1句行动号召", "禁止虚构价格或效果");
        assertThat(second).isEqualTo(first);
        assertThat(canonicalBytes(second)).isEqualTo(firstCanonical);
        assertThat(second.contentHash()).isEqualTo(first.contentHash());

        KnowledgeContextDTO longResult = service.resolve(
            new KnowledgeContextRequestDTO("food", "store_traffic", 30, List.of("fresh", "value")));
        assertThat(longResult.copyRules()).containsExactly(
            "thirty", "21至60秒：钩子、痛点、卖点、证据、行动号召依次展开", "禁止虚构价格或效果");

        execute("UPDATE av_knowledge_binding SET status = 'retired'");
        execute("UPDATE av_video_type_rule SET status = 'retired'");
        KnowledgeContextDTO empty = service.resolve(shortRequest);
        assertThat(empty.knowledgeVersionIds()).isEmpty();
        assertThat(empty.excerpts()).isEmpty();
        assertThat(empty.copyRules()).isEmpty();
        assertThat(empty.contentHash()).isEqualTo(EMPTY_HASH);
    }

    @Test
    void failsClosedWhenPublishedPointerDoesNotMatchBinding() throws SQLException {
        execute("UPDATE av_knowledge_item SET current_published_version_id = 2002 WHERE knowledge_item_id = 1001");

        assertThatThrownBy(() -> service.resolve(
            new KnowledgeContextRequestDTO("food", "store_traffic", 15, List.of())))
            .isInstanceOf(ServiceException.class);
    }

    @Test
    void failsClosedWhenStoredJsonIsNotAStringArray() throws SQLException {
        execute("UPDATE av_knowledge_item SET tags_json = JSON_OBJECT('bad', true) WHERE knowledge_item_id = 1001");

        assertThatThrownBy(() -> service.resolve(
            new KnowledgeContextRequestDTO("food", "store_traffic", 15, List.of())))
            .isInstanceOf(ServiceException.class)
            .hasMessageNotContaining("开头先给出具体利益点");
    }

    private void insertRoutingFixture() throws SQLException {
        execute("""
            INSERT INTO av_knowledge_item
                (knowledge_item_id, domain_code, knowledge_type_code, stable_code, name,
                 tags_json, current_published_version_id, source_type, source_ref)
            VALUES
                (9101, 'copywriting', 'writing_technique', 'exact_tag2', 'exact tag2',
                 JSON_ARRAY('fresh', 'value'), 9201, 'it', 'it'),
                (9102, 'copywriting', 'writing_technique', 'z_priority', 'priority',
                 JSON_ARRAY('fresh'), 9202, 'it', 'it'),
                (9103, 'copywriting', 'writing_technique', 'a_stable', 'stable a',
                 JSON_ARRAY('fresh'), 9203, 'it', 'it'),
                (9104, 'copywriting', 'writing_technique', 'b_stable', 'stable b',
                 JSON_ARRAY('fresh'), 9204, 'it', 'it'),
                (9105, 'copywriting', 'writing_technique', 'exact_wild', 'exact wild',
                 JSON_ARRAY(), 9205, 'it', 'it'),
                (9106, 'copywriting', 'writing_technique', 'wild_exact', 'wild exact',
                 JSON_ARRAY(), 9206, 'it', 'it'),
                (9107, 'copywriting', 'writing_technique', 'wild_wild', 'wild wild',
                 JSON_ARRAY(), 9207, 'it', 'it'),
                (9108, 'copywriting', 'writing_technique', 'draft_version', 'draft',
                 JSON_ARRAY(), 9208, 'it', 'it'),
                (9109, 'copywriting', 'writing_technique', 'retired_binding', 'retired',
                 JSON_ARRAY(), 9209, 'it', 'it')
            """);
        execute("""
            INSERT INTO av_knowledge_version
                (knowledge_version_id, knowledge_item_id, version_no, status, content, structure_json)
            VALUES
                (9291, 9101, 1, 'published', 'old version', JSON_OBJECT()),
                (9201, 9101, 2, 'published', 'exact tag winner', JSON_OBJECT()),
                (9202, 9102, 1, 'published', 'priority winner', JSON_OBJECT()),
                (9203, 9103, 1, 'published', 'stable a', JSON_OBJECT()),
                (9204, 9104, 1, 'published', 'stable b', JSON_OBJECT()),
                (9205, 9105, 1, 'published', 'exact wildcard', JSON_OBJECT()),
                (9206, 9106, 1, 'published', 'wild exact', JSON_OBJECT()),
                (9207, 9107, 1, 'published', 'wild wildcard', JSON_OBJECT()),
                (9208, 9108, 1, 'draft', 'draft excluded', JSON_OBJECT()),
                (9209, 9109, 1, 'published', 'retired excluded', JSON_OBJECT())
            """);
        execute("""
            INSERT INTO av_knowledge_binding
                (knowledge_binding_id, binding_group_code, version_no, knowledge_item_id,
                 knowledge_version_id, industry_code, purpose_code, video_type_code,
                 angle_codes_json, angle_priorities_json, min_duration_seconds, max_duration_seconds,
                 priority, required_flag, required_slot_codes_json, audience_tag_codes_json,
                 exclusion_conditions_json, status)
            VALUES
                (9301, 'exact_tag2', 1, 9101, 9201, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9302, 'exact_tag2_duplicate', 1, 9101, 9201, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 0, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9303, 'priority_winner', 1, 9102, 9202, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 20, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9304, 'stable_a', 1, 9103, 9203, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 10, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9305, 'stable_b', 1, 9104, 9204, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 10, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9306, 'exact_wild', 1, 9105, 9205, 'food', '*', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1000, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9307, 'wild_exact', 1, 9106, 9206, '*', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1000, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9308, 'wild_wild', 1, 9107, 9207, '*', '*', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1000, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9309, 'draft_version', 1, 9108, 9208, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1000, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'published'),
                (9310, 'retired_binding', 1, 9109, 9209, 'food', 'store_traffic', '*',
                 JSON_ARRAY(), JSON_OBJECT(), NULL, NULL, 1000, 0, JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY(), 'retired')
            """);
        execute("""
            INSERT INTO av_video_type_rule
                (video_type_rule_id, rule_code, version_no, video_type_code, industry_code, purpose_code,
                 min_duration_seconds, max_duration_seconds, required_slot_codes_json,
                 priority, copy_rules_json, status)
            VALUES
                (9401, 'exact_short', 1, '*', 'food', 'store_traffic', 1, 20, JSON_ARRAY(),
                 10, JSON_ARRAY('exact', 'shared'), 'published'),
                (9402, 'exact_wild_short', 1, '*', 'food', '*', 1, 20, JSON_ARRAY(),
                 10, JSON_ARRAY('exact-wild', 'shared'), 'published'),
                (9403, 'wild_exact_short', 1, '*', '*', 'store_traffic', 1, 20, JSON_ARRAY(),
                 10, JSON_ARRAY('wild-exact'), 'published'),
                (9404, 'wild_wild_short', 1, '*', '*', '*', 1, 20, JSON_ARRAY(),
                 1000, JSON_ARRAY('wild-wild'), 'published'),
                (9405, 'exact_long', 1, '*', 'food', 'store_traffic', 21, 60, JSON_ARRAY(),
                 10, JSON_ARRAY('thirty'), 'published'),
                (9406, 'retired_short', 1, '*', 'food', 'store_traffic', 1, 20, JSON_ARRAY(),
                 1000, JSON_ARRAY('retired'), 'retired')
            """);
    }

    private byte[] canonicalBytes(KnowledgeContextDTO context) throws Exception {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("knowledgeVersionIds", context.knowledgeVersionIds());
        canonical.put("excerpts", context.excerpts());
        canonical.put("copyRules", context.copyRules());
        return jsonMapper.writeValueAsBytes(canonical);
    }

    private void execute(String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static void reloadSchema(Connection connection) throws SQLException, IOException {
        Path apiRoot = locateApiRoot();
        executeSqlScript(connection, apiRoot.resolve("../docs/sql/ry_vue.sql"));
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260728_01_p0a_identity_security.sql"));
        executeSqlScript(connection,
            apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql"));
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
        throw new IllegalStateException("无法定位 ai-video-api 根目录");
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
}

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackageClasses = KnowledgeItemMapper.class)
class KnowledgeContextQueryConfiguration {

    @Bean
    DataSource dataSource(@Value("${knowledge.jdbc-url}") String jdbcUrl,
                          @Value("${knowledge.username}") String username,
                          @Value("${knowledge.password}") String password) {
        return new org.apache.ibatis.datasource.unpooled.UnpooledDataSource(
            "com.mysql.cj.jdbc.Driver", jdbcUrl, username, password);
    }

    @Bean
    SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        return factoryBean.getObject();
    }

    @Bean
    JsonMapper jsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    IKnowledgeContextService knowledgeContextService(KnowledgeItemMapper itemMapper,
                                                     KnowledgeVersionMapper versionMapper,
                                                     KnowledgeBindingMapper bindingMapper,
                                                     VideoTypeRuleMapper ruleMapper,
                                                     JsonMapper jsonMapper) {
        return new KnowledgeContextServiceImpl(itemMapper, versionMapper, bindingMapper, ruleMapper, jsonMapper);
    }
}
