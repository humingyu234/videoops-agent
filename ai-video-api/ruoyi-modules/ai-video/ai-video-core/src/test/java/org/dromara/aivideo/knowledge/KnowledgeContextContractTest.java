package org.dromara.aivideo.knowledge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

@Tag("dev")
class KnowledgeContextContractTest {

    private static final String REQUEST_TYPE =
        "org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO";
    private static final String CONTEXT_TYPE =
        "org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO";
    private static final String SERVICE_TYPE =
        "org.dromara.aivideo.knowledge.service.IKnowledgeContextService";
    private static final String VALID_HASH = "0123456789abcdef".repeat(4);

    @Test
    void freezesRecordLayoutsAndReadOnlyServiceSignature() throws Exception {
        Class<?> requestType = Class.forName(REQUEST_TYPE);
        Class<?> contextType = Class.forName(CONTEXT_TYPE);
        Class<?> serviceType = Class.forName(SERVICE_TYPE);

        assertRecordLayout(
            requestType,
            new String[]{"industryCode", "purposeCode", "targetDurationSeconds", "tagCodes"},
            new Class<?>[]{String.class, String.class, Integer.class, List.class},
            new String[]{String.class.getName(), String.class.getName(), Integer.class.getName(), listOf(String.class)}
        );
        assertRecordLayout(
            contextType,
            new String[]{"knowledgeVersionIds", "excerpts", "copyRules", "contentHash"},
            new Class<?>[]{List.class, List.class, List.class, String.class},
            new String[]{listOf(Long.class), listOf(String.class), listOf(String.class), String.class.getName()}
        );

        assertThat(serviceType.isInterface()).isTrue();
        assertThat(serviceType.getDeclaredMethods()).hasSize(1);
        Method resolve = serviceType.getDeclaredMethod("resolve", requestType);
        assertThat(resolve.getReturnType()).isEqualTo(contextType);
        assertThat(resolve.getParameterTypes()).containsExactly(requestType);
        assertThat(Modifier.isPublic(resolve.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(resolve.getModifiers())).isTrue();
    }

    @Test
    void requestRejectsInvalidStableInputs() throws Exception {
        assertRejected(() -> newRequest(null, "PURPOSE", 60, List.of()));
        assertRejected(() -> newRequest(" ", "PURPOSE", 60, List.of()));
        assertRejected(() -> newRequest("I".repeat(65), "PURPOSE", 60, List.of()));
        assertRejected(() -> newRequest(" * ", "PURPOSE", 60, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", null, 60, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", " ", 60, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", "P".repeat(65), 60, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", " * ", 60, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", null, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", 0, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", -1, List.of()));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", 60, null));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", 60, List.of(" ")));
        assertRejected(() -> newRequest("INDUSTRY", "PURPOSE", 60, List.of("T".repeat(65))));
        assertRejected(() -> newRequest(
            "INDUSTRY", "PURPOSE", 60, new ArrayList<>(Arrays.asList("TAG", null))));
    }

    @Test
    void requestNormalizesCodesAndOwnsSortedUniqueTags() throws Exception {
        List<String> source = new ArrayList<>(List.of(" TAG_B ", "TAG_A", "TAG_B", " TAG_C"));
        Object request = newRequest(" INDUSTRY ", "\tPURPOSE\n", 60, source);
        source.set(0, "CHANGED");
        source.add("TAG_D");

        assertThat(value(request, "industryCode")).isEqualTo("INDUSTRY");
        assertThat(value(request, "purposeCode")).isEqualTo("PURPOSE");
        assertThat(value(request, "targetDurationSeconds")).isEqualTo(60);
        List<Object> tags = listValue(request, "tagCodes");
        assertThat(tags).containsExactly("TAG_A", "TAG_B", "TAG_C");
        assertThat(tags).isNotSameAs(source);
        assertImmutable(tags, "TAG_D");

        Object boundary = newRequest(" I" + "N".repeat(63) + " ", "PURPOSE", 1, List.of(" TAG "));
        assertThat(value(boundary, "industryCode")).isEqualTo("I" + "N".repeat(63));
        assertThat(listValue(boundary, "tagCodes")).containsExactly("TAG");

        Object embeddedAsterisks = assertConstructed(
            () -> newRequest("IND*USTRY", "PUR*POSE", 60, List.of()));
        assertThat(value(embeddedAsterisks, "industryCode")).isEqualTo("IND*USTRY");
        assertThat(value(embeddedAsterisks, "purposeCode")).isEqualTo("PUR*POSE");
    }

    @Test
    void contextRejectsInvalidAlignedContent() throws Exception {
        assertRejected(() -> newContext(null, List.of(), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(), null, List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(), List.of(), null, VALID_HASH));
        assertRejected(() -> newContext(List.of(1L), List.of(), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(), List.of("excerpt"), List.of(), VALID_HASH));
        assertRejected(() -> newContext(new ArrayList<>(Arrays.asList(1L, null)),
            List.of("one", "two"), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(0L), List.of("excerpt"), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(-1L), List.of("excerpt"), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(1L, 1L), List.of("one", "two"), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(1L),
            new ArrayList<>(Arrays.asList((String) null)), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(1L), List.of(" "), List.of(), VALID_HASH));
        assertRejected(() -> newContext(List.of(), List.of(), List.of(" "), VALID_HASH));
        assertRejected(() -> newContext(List.of(), List.of(),
            new ArrayList<>(Arrays.asList((String) null)), VALID_HASH));
    }

    @Test
    void contextRejectsNonSha256ContentHash() throws Exception {
        assertRejected(() -> newContext(List.of(), List.of(), List.of(), null));
        assertRejected(() -> newContext(List.of(), List.of(), List.of(), "0".repeat(63)));
        assertRejected(() -> newContext(List.of(), List.of(), List.of(), "A".repeat(64)));
        assertRejected(() -> newContext(List.of(), List.of(), List.of(), "g".repeat(64)));
    }

    @Test
    void contextOwnsAlignedListsAndNormalizesCopyRulesInFirstSeenOrder() throws Exception {
        List<Long> idsSource = new ArrayList<>(List.of(2L, 1L));
        List<String> excerptsSource = new ArrayList<>(List.of("excerpt-two", "excerpt-one"));
        List<String> rulesSource = new ArrayList<>(List.of(" rule-b ", "rule-a", "rule-b", " rule-c "));
        Object context = newContext(idsSource, excerptsSource, rulesSource, VALID_HASH);
        idsSource.set(0, 99L);
        excerptsSource.clear();
        rulesSource.add("rule-d");

        List<Object> ids = listValue(context, "knowledgeVersionIds");
        List<Object> excerpts = listValue(context, "excerpts");
        List<Object> rules = listValue(context, "copyRules");
        assertThat(ids).containsExactly(2L, 1L);
        assertThat(excerpts).containsExactly("excerpt-two", "excerpt-one");
        assertThat(rules).containsExactly("rule-b", "rule-a", "rule-c");
        assertThat(ids).isNotSameAs(idsSource);
        assertThat(excerpts).isNotSameAs(excerptsSource);
        assertThat(rules).isNotSameAs(rulesSource);
        assertThat(value(context, "contentHash")).isEqualTo(VALID_HASH);
        assertImmutable(ids, 3L);
        assertImmutable(excerpts, "excerpt-three");
        assertImmutable(rules, "rule-d");

        Object empty = newContext(List.of(), List.of(), List.of(), VALID_HASH);
        assertThat(listValue(empty, "knowledgeVersionIds")).isEmpty();
        assertThat(listValue(empty, "excerpts")).isEmpty();
        assertThat(listValue(empty, "copyRules")).isEmpty();
    }

    private static Object newRequest(
        String industryCode,
        String purposeCode,
        Integer targetDurationSeconds,
        List<String> tagCodes
    ) throws ReflectiveOperationException {
        return instantiate(
            REQUEST_TYPE,
            new Class<?>[]{String.class, String.class, Integer.class, List.class},
            industryCode,
            purposeCode,
            targetDurationSeconds,
            tagCodes
        );
    }

    private static Object newContext(
        List<Long> knowledgeVersionIds,
        List<String> excerpts,
        List<String> copyRules,
        String contentHash
    ) throws ReflectiveOperationException {
        return instantiate(
            CONTEXT_TYPE,
            new Class<?>[]{List.class, List.class, List.class, String.class},
            knowledgeVersionIds,
            excerpts,
            copyRules,
            contentHash
        );
    }

    private static Object instantiate(
        String typeName,
        Class<?>[] parameterTypes,
        Object... arguments
    ) throws ReflectiveOperationException {
        return Class.forName(typeName).getDeclaredConstructor(parameterTypes).newInstance(arguments);
    }

    private static Object value(Object record, String componentName) throws ReflectiveOperationException {
        return record.getClass().getMethod(componentName).invoke(record);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> listValue(Object record, String componentName) throws ReflectiveOperationException {
        return (List<Object>) value(record, componentName);
    }

    private static void assertRejected(ReflectiveFactory factory) throws Exception {
        try {
            factory.create();
            fail("Expected record construction to be rejected");
        } catch (InvocationTargetException exception) {
            assertThat(exception.getCause())
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
        }
    }

    private static Object assertConstructed(ReflectiveFactory factory) throws ReflectiveOperationException {
        try {
            return factory.create();
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Expected record construction to succeed", exception.getCause());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertImmutable(List<?> values, Object newValue) {
        assertThatThrownBy(() -> ((List) values).add(newValue))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertRecordLayout(
        Class<?> recordType,
        String[] componentNames,
        Class<?>[] componentTypes,
        String[] genericTypeNames
    ) {
        assertThat(recordType.isRecord()).isTrue();
        assertThat(Modifier.isPublic(recordType.getModifiers())).isTrue();
        RecordComponent[] components = recordType.getRecordComponents();
        assertThat(components).extracting(RecordComponent::getName).containsExactly(componentNames);
        assertThat(components).extracting(RecordComponent::getType).containsExactly(componentTypes);
        assertThat(components).extracting(component -> component.getGenericType().getTypeName())
            .containsExactly(genericTypeNames);
    }

    private static String listOf(Class<?> elementType) {
        return List.class.getName() + "<" + elementType.getName() + ">";
    }

    @FunctionalInterface
    private interface ReflectiveFactory {

        Object create() throws ReflectiveOperationException;
    }
}
