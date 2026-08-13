package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.common.mybatis.core.mapper.BaseMapperPlus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class KnowledgeLiteDomainRulesTest {

    private static final String PACKAGE = "org.dromara.aivideo.knowledge";

    @Test
    void entitiesFollowTheKnowledgeLitePersistenceContract() throws Exception {
        assertEntity(
            "KnowledgeItem",
            "av_knowledge_item",
            "knowledgeItemId",
            fields(
                "knowledgeItemId", Long.class,
                "domainCode", String.class,
                "knowledgeTypeCode", String.class,
                "stableCode", String.class,
                "name", String.class,
                "summary", String.class,
                "tagsJson", String.class,
                "currentPublishedVersionId", Long.class,
                "sourceType", String.class,
                "sourceRef", String.class
            )
        );
        assertEntity(
            "KnowledgeVersion",
            "av_knowledge_version",
            "knowledgeVersionId",
            fields(
                "knowledgeVersionId", Long.class,
                "knowledgeItemId", Long.class,
                "versionNo", Integer.class,
                "status", String.class,
                "content", String.class,
                "structureJson", String.class,
                "sourceSummary", String.class,
                "reviewedBy", Long.class,
                "reviewedAt", LocalDateTime.class,
                "publishedBy", Long.class,
                "publishedAt", LocalDateTime.class
            )
        );
        assertEntity(
            "KnowledgeBinding",
            "av_knowledge_binding",
            "knowledgeBindingId",
            fields(
                "knowledgeBindingId", Long.class,
                "bindingGroupCode", String.class,
                "versionNo", Integer.class,
                "knowledgeItemId", Long.class,
                "knowledgeVersionId", Long.class,
                "industryCode", String.class,
                "purposeCode", String.class,
                "videoTypeCode", String.class,
                "angleCodesJson", String.class,
                "anglePrioritiesJson", String.class,
                "minDurationSeconds", Integer.class,
                "maxDurationSeconds", Integer.class,
                "priority", Integer.class,
                "requiredFlag", Boolean.class,
                "requiredSlotCodesJson", String.class,
                "audienceTagCodesJson", String.class,
                "exclusionConditionsJson", String.class,
                "status", String.class
            )
        );
        assertEntity(
            "VideoTypeRule",
            "av_video_type_rule",
            "videoTypeRuleId",
            fields(
                "videoTypeRuleId", Long.class,
                "ruleCode", String.class,
                "versionNo", Integer.class,
                "videoTypeCode", String.class,
                "industryCode", String.class,
                "purposeCode", String.class,
                "minDurationSeconds", Integer.class,
                "maxDurationSeconds", Integer.class,
                "requiredSlotCodesJson", String.class,
                "priority", Integer.class,
                "copyRulesJson", String.class,
                "status", String.class,
                "publishedAt", LocalDateTime.class
            )
        );
    }

    @Test
    void mappersDirectlyExposeEntityToEntityBaseMapperPlusTypes() throws Exception {
        assertMapper("KnowledgeItemMapper", "KnowledgeItem");
        assertMapper("KnowledgeVersionMapper", "KnowledgeVersion");
        assertMapper("KnowledgeBindingMapper", "KnowledgeBinding");
        assertMapper("VideoTypeRuleMapper", "VideoTypeRule");
    }

    @Test
    void enumsExposeOnlyTheApprovedStableCodes() throws Exception {
        assertEnumCodes("KnowledgeDomainCode", Map.of("COPYWRITING", "copywriting"));
        assertEnumCodes("KnowledgeTypeCode", Map.of(
            "PRIMARY_TEMPLATE", "primary_template",
            "WRITING_TECHNIQUE", "writing_technique",
            "PSYCHOLOGY", "psychology",
            "CASE", "case",
            "MANDATORY_RULE", "mandatory_rule"
        ));
        assertEnumCodes("KnowledgeVersionStatus", Map.of(
            "DRAFT", "draft",
            "REVIEWING", "reviewing",
            "PUBLISHED", "published",
            "RETIRED", "retired"
        ));
    }

    private static void assertEntity(String simpleName, String tableName, String idField,
                                     Map<String, Class<?>> expectedFields) throws Exception {
        Class<?> entityClass = Class.forName(PACKAGE + ".domain." + simpleName);

        assertThat(entityClass.getSuperclass()).isEqualTo(BaseEntity.class);
        assertThat(entityClass.getAnnotation(TableName.class))
            .isNotNull()
            .extracting(TableName::value)
            .isEqualTo(tableName);

        Field primaryKey = entityClass.getDeclaredField(idField);
        assertThat(primaryKey.getAnnotation(TableId.class))
            .isNotNull()
            .extracting(TableId::type)
            .isEqualTo(IdType.ASSIGN_ID);

        Map<String, Class<?>> actualFields = Arrays.stream(entityClass.getDeclaredFields())
            .filter(field -> !field.isSynthetic())
            .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
            .collect(Collectors.toMap(Field::getName, Field::getType, (left, right) -> left, LinkedHashMap::new));
        assertThat(actualFields).containsExactlyEntriesOf(expectedFields);
    }

    private static void assertMapper(String mapperSimpleName, String entitySimpleName) throws Exception {
        Class<?> mapperClass = Class.forName(PACKAGE + ".mapper." + mapperSimpleName);
        Class<?> entityClass = Class.forName(PACKAGE + ".domain." + entitySimpleName);

        assertThat(mapperClass.getGenericInterfaces()).hasSize(1);
        Type directInterface = mapperClass.getGenericInterfaces()[0];
        assertThat(directInterface).isInstanceOf(ParameterizedType.class);
        ParameterizedType mapperType = (ParameterizedType) directInterface;
        assertThat(mapperType.getRawType()).isEqualTo(BaseMapperPlus.class);
        assertThat(mapperType.getActualTypeArguments()).containsExactly(entityClass, entityClass);
    }

    private static void assertEnumCodes(String simpleName, Map<String, String> expectedCodes) throws Exception {
        Class<?> enumClass = Class.forName(PACKAGE + "." + simpleName);
        Field codeField = enumClass.getDeclaredField("code");
        codeField.setAccessible(true);

        Map<String, String> actualCodes = Arrays.stream(enumClass.getEnumConstants())
            .collect(Collectors.toMap(
                value -> ((Enum<?>) value).name(),
                value -> readCode(codeField, value)
            ));
        assertThat(actualCodes).containsExactlyInAnyOrderEntriesOf(expectedCodes);
    }

    private static String readCode(Field codeField, Object value) {
        try {
            return (String) codeField.get(value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static Map<String, Class<?>> fields(Object... entries) {
        Map<String, Class<?>> fields = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            fields.put((String) entries[index], (Class<?>) entries[index + 1]);
        }
        return fields;
    }
}
