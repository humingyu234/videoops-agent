package org.dromara.aivideo.knowledge;

import org.dromara.aivideo.knowledge.dto.KnowledgePlanDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeRouteRequestDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeRouteResultDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeSnapshotDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeSnapshotRequestDTO;
import org.dromara.aivideo.knowledge.service.IKnowledgeRoutingService;
import org.dromara.aivideo.knowledge.service.IKnowledgeSnapshotService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class KnowledgeContractTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void freezesCrossStageServiceSignatures() throws NoSuchMethodException {
        assertThat(IKnowledgeRoutingService.class.getDeclaredMethods()).hasSize(1);
        assertMethod(
            IKnowledgeRoutingService.class.getDeclaredMethod("route", KnowledgeRouteRequestDTO.class),
            KnowledgeRouteResultDTO.class,
            KnowledgeRouteRequestDTO.class
        );

        assertThat(IKnowledgeSnapshotService.class.getDeclaredMethods())
            .extracting(Method::getName)
            .containsExactlyInAnyOrder("create", "getByRootTaskId");
        assertMethod(
            IKnowledgeSnapshotService.class.getDeclaredMethod("create", KnowledgeSnapshotRequestDTO.class),
            KnowledgeSnapshotDTO.class,
            KnowledgeSnapshotRequestDTO.class
        );
        assertMethod(
            IKnowledgeSnapshotService.class.getDeclaredMethod("getByRootTaskId", Long.class),
            KnowledgeSnapshotDTO.class,
            Long.class
        );
    }

    @Test
    void freezesExactRecordComponentsAndTopLevelDtoInventory() throws Exception {
        assertRecordLayout(
            KnowledgeRouteRequestDTO.class,
            new String[]{"directionCatalogVersionId", "industryCode", "purposeCode", "targetDurationSeconds", "tagCodes"},
            new Class<?>[]{Long.class, String.class, String.class, Integer.class, List.class},
            new String[]{Long.class.getName(), String.class.getName(), String.class.getName(), Integer.class.getName(),
                listOf(String.class)}
        );
        assertRecordLayout(
            KnowledgePlanDTO.class,
            new String[]{"candidateCode", "planCode", "primaryTemplateVersionId", "angleCode",
                "differentiatorTechniqueCode"},
            new Class<?>[]{String.class, String.class, Long.class, String.class, String.class},
            new String[]{String.class.getName(), String.class.getName(), Long.class.getName(), String.class.getName(),
                String.class.getName()}
        );
        assertRecordLayout(
            KnowledgeRouteResultDTO.class,
            new String[]{"routingVersion", "videoTypeCode", "plans", "contentHash"},
            new Class<?>[]{String.class, String.class, List.class, String.class},
            new String[]{String.class.getName(), String.class.getName(), listOf(KnowledgePlanDTO.class),
                String.class.getName()}
        );
        assertRecordLayout(
            KnowledgeSnapshotRequestDTO.class,
            new String[]{"rootTaskId", "promptVersionId", "generationContextRevision", "generationInputHash", "route",
                "acceptedFacts"},
            new Class<?>[]{Long.class, Long.class, Long.class, String.class, KnowledgeRouteResultDTO.class, List.class},
            new String[]{Long.class.getName(), Long.class.getName(), Long.class.getName(), String.class.getName(),
                KnowledgeRouteResultDTO.class.getName(),
                listOf(KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO.class)}
        );
        assertRecordLayout(
            KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO.class,
            new String[]{"factId", "decisionRevision", "factText", "evidenceRef"},
            new Class<?>[]{Long.class, Long.class, String.class, String.class},
            new String[]{Long.class.getName(), Long.class.getName(), String.class.getName(), String.class.getName()}
        );
        assertRecordLayout(
            KnowledgeSnapshotDTO.class,
            new String[]{"snapshotId", "rootTaskId", "promptVersionId", "generationContextRevision",
                "generationInputHash", "route", "acceptedFacts", "knowledgeMaterials", "contentHash", "createdAt"},
            new Class<?>[]{Long.class, Long.class, Long.class, Long.class, String.class, KnowledgeRouteResultDTO.class,
                List.class, List.class, String.class, Instant.class},
            new String[]{Long.class.getName(), Long.class.getName(), Long.class.getName(), Long.class.getName(),
                String.class.getName(), KnowledgeRouteResultDTO.class.getName(),
                listOf(KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO.class),
                listOf(KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO.class), String.class.getName(),
                Instant.class.getName()}
        );
        assertRecordLayout(
            KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO.class,
            new String[]{"knowledgeVersionId", "bindingVersionId", "videoRuleVersionId", "contentExcerpt",
                "injectionOrder"},
            new Class<?>[]{Long.class, Long.class, Long.class, String.class, Integer.class},
            new String[]{Long.class.getName(), Long.class.getName(), Long.class.getName(), String.class.getName(),
                Integer.class.getName()}
        );

        Path classesDirectory = Path.of(
            KnowledgeRouteRequestDTO.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path dtoDirectory = classesDirectory.getParent().getParent()
            .resolve("src/main/java/org/dromara/aivideo/knowledge/dto");
        try (var files = Files.list(dtoDirectory)) {
            assertThat(files.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".java")))
                .containsExactlyInAnyOrder(
                    "KnowledgeRouteRequestDTO.java",
                    "KnowledgeRouteResultDTO.java",
                    "KnowledgePlanDTO.java",
                    "KnowledgeSnapshotRequestDTO.java",
                    "KnowledgeSnapshotDTO.java",
                    "KnowledgeContextRequestDTO.java",
                    "KnowledgeContextDTO.java"
                );
        }
    }

    @Test
    void routeRequestValidatesStableInputsAndOwnsDeterministicallySortedTags() {
        assertRejected(() -> new KnowledgeRouteRequestDTO(null, "INDUSTRY", "PURPOSE", 60, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(0L, "INDUSTRY", "PURPOSE", 60, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, " ", "PURPOSE", 60, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, "INDUSTRY", "", 60, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, "INDUSTRY", "PURPOSE", null, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, "INDUSTRY", "PURPOSE", 0, List.of()));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, "INDUSTRY", "PURPOSE", 60, null));
        assertRejected(() -> new KnowledgeRouteRequestDTO(1L, "INDUSTRY", "PURPOSE", 60, List.of(" ")));
        assertRejected(() -> new KnowledgeRouteRequestDTO(
            1L, "INDUSTRY", "PURPOSE", 60, new ArrayList<>(Arrays.asList("TAG_A", null))));

        List<String> source = new ArrayList<>(List.of("TAG_C", "TAG_A", "TAG_B"));
        KnowledgeRouteRequestDTO request = new KnowledgeRouteRequestDTO(
            1L, "INDUSTRY", "PURPOSE", 60, source);
        source.set(0, "TAG_CHANGED");
        source.add("TAG_D");

        assertThat(request.tagCodes()).containsExactly("TAG_A", "TAG_B", "TAG_C");
        assertThat(request.tagCodes()).isNotSameAs(source);
        assertThatThrownBy(() -> request.tagCodes().add("TAG_D"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void plansValidateStableCodesAndPositiveTemplateVersion() {
        assertRejected(() -> new KnowledgePlanDTO(" ", "PLAN_A", 1L, "ANGLE_A", "TECHNIQUE_A"));
        assertRejected(() -> new KnowledgePlanDTO("A", null, 1L, "ANGLE_A", "TECHNIQUE_A"));
        assertRejected(() -> new KnowledgePlanDTO("A", "PLAN_A", null, "ANGLE_A", "TECHNIQUE_A"));
        assertRejected(() -> new KnowledgePlanDTO("A", "PLAN_A", 0L, "ANGLE_A", "TECHNIQUE_A"));
        assertRejected(() -> new KnowledgePlanDTO("A", "PLAN_A", 1L, "", "TECHNIQUE_A"));
        assertRejected(() -> new KnowledgePlanDTO("A", "PLAN_A", 1L, "ANGLE_A", " "));
    }

    @Test
    void routeResultEnforcesExactlyUniqueAbcPlansAndOwnsTheirOrder() {
        KnowledgePlanDTO planA = plan("A", "PLAN_A", 1L, "ANGLE_A", "TECHNIQUE_A");
        KnowledgePlanDTO planB = plan("B", "PLAN_B", 2L, "ANGLE_B", "TECHNIQUE_B");
        KnowledgePlanDTO planC = plan("C", "PLAN_C", 3L, "ANGLE_C", "TECHNIQUE_C");

        assertRejected(() -> new KnowledgeRouteResultDTO(" ", "VIDEO_TYPE", List.of(planA, planB, planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO("routing-v1", "", List.of(planA, planB, planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO("routing-v1", "VIDEO_TYPE", List.of(planA, planB, planC), null));
        assertRejected(() -> new KnowledgeRouteResultDTO("routing-v1", "VIDEO_TYPE", null, "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO("routing-v1", "VIDEO_TYPE", List.of(planA, planB), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO(
            "routing-v1", "VIDEO_TYPE", List.of(planA, planB, planC, plan("D", "PLAN_D", 4L, "ANGLE_D", "TECHNIQUE_D")), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO("routing-v1", "VIDEO_TYPE", List.of(planB, planA, planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO(
            "routing-v1", "VIDEO_TYPE",
            List.of(planA, plan("A", "PLAN_B", 2L, "ANGLE_B", "TECHNIQUE_B"), planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO(
            "routing-v1", "VIDEO_TYPE",
            List.of(planA, plan("B", "PLAN_A", 2L, "ANGLE_B", "TECHNIQUE_B"), planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO(
            "routing-v1", "VIDEO_TYPE",
            List.of(planA, plan("B", "PLAN_B", 1L, "ANGLE_A", "TECHNIQUE_A"), planC), "hash"));
        assertRejected(() -> new KnowledgeRouteResultDTO(
            "routing-v1", "VIDEO_TYPE", new ArrayList<>(Arrays.asList(planA, null, planC)), "hash"));

        List<KnowledgePlanDTO> source = new ArrayList<>(List.of(planA, planB, planC));
        KnowledgeRouteResultDTO route = new KnowledgeRouteResultDTO("routing-v1", "VIDEO_TYPE", source, "route-hash");
        source.set(0, plan("A", "CHANGED", 9L, "ANGLE_CHANGED", "TECHNIQUE_CHANGED"));

        assertThat(route.plans()).containsExactly(planA, planB, planC);
        assertThat(route.plans()).isNotSameAs(source);
        assertThatThrownBy(() -> route.plans().add(planA)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotRequestValidatesNestedFactsAndOwnsTheirExplicitOrder() {
        assertRejected(() -> new KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO(null, 1L, "fact", "evidence"));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO(1L, 0L, "fact", "evidence"));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO(1L, 1L, " ", "evidence"));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO(1L, 1L, "fact", ""));

        KnowledgeRouteResultDTO route = validRoute();
        KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO factOne = fact(1L);
        KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO factTwo = fact(2L);
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(null, 2L, 3L, "input-hash", route, List.of()));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(1L, 0L, 3L, "input-hash", route, List.of()));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(1L, 2L, 0L, "input-hash", route, List.of()));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(1L, 2L, 3L, " ", route, List.of()));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(1L, 2L, 3L, "input-hash", null, List.of()));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(1L, 2L, 3L, "input-hash", route, null));
        assertRejected(() -> new KnowledgeSnapshotRequestDTO(
            1L, 2L, 3L, "input-hash", route, new ArrayList<>(Arrays.asList(factOne, null))));

        List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> source = new ArrayList<>(List.of(factTwo, factOne));
        KnowledgeSnapshotRequestDTO request = new KnowledgeSnapshotRequestDTO(
            1L, 2L, 3L, "input-hash", route, source);
        source.clear();

        assertThat(request.acceptedFacts()).containsExactly(factTwo, factOne);
        assertThat(request.acceptedFacts()).isNotSameAs(source);
        assertThatThrownBy(() -> request.acceptedFacts().add(factOne))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotValidatesNestedMaterialsAndOwnsAllExplicitCollectionOrder() {
        assertRejected(() -> new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(null, 2L, 3L, "excerpt", 1));
        assertRejected(() -> new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(1L, 0L, 3L, "excerpt", 1));
        assertRejected(() -> new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(1L, 2L, 0L, "excerpt", 1));
        assertRejected(() -> new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(1L, 2L, 3L, " ", 1));
        assertRejected(() -> new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(1L, 2L, 3L, "excerpt", 0));

        KnowledgeRouteResultDTO route = validRoute();
        KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO factOne = fact(1L);
        KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO factTwo = fact(2L);
        KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO materialOne = material(1L, 1);
        KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO materialTwo = material(2L, 2);
        List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> facts = List.of(factOne);
        List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> materials = List.of(materialOne);

        assertRejected(() -> snapshot(null, 2L, 3L, 4L, "input-hash", route, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 0L, 3L, 4L, "input-hash", route, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 0L, 4L, "input-hash", route, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 0L, "input-hash", route, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "", route, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "input-hash", null, facts, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "input-hash", route, null, materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(
            1L, 2L, 3L, 4L, "input-hash", route,
            new ArrayList<>(Arrays.asList(factOne, null)), materials, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "input-hash", route, facts, null, "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(
            1L, 2L, 3L, 4L, "input-hash", route, facts,
            new ArrayList<>(Arrays.asList(materialOne, null)), "content-hash", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "input-hash", route, facts, materials, " ", CREATED_AT));
        assertRejected(() -> snapshot(1L, 2L, 3L, 4L, "input-hash", route, facts, materials, "content-hash", null));

        List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> factsSource =
            new ArrayList<>(List.of(factTwo, factOne));
        List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> materialsSource =
            new ArrayList<>(List.of(materialTwo, materialOne));
        KnowledgeSnapshotDTO snapshot = snapshot(
            1L, 2L, 3L, 4L, "input-hash", route, factsSource, materialsSource, "content-hash", CREATED_AT);
        factsSource.clear();
        materialsSource.clear();

        assertThat(snapshot.acceptedFacts()).containsExactly(factTwo, factOne);
        assertThat(snapshot.knowledgeMaterials()).containsExactly(materialTwo, materialOne);
        assertThat(snapshot.acceptedFacts()).isNotSameAs(factsSource);
        assertThat(snapshot.knowledgeMaterials()).isNotSameAs(materialsSource);
        assertThatThrownBy(() -> snapshot.acceptedFacts().add(factOne))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> snapshot.knowledgeMaterials().add(materialOne))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static void assertMethod(Method method, Class<?> returnType, Class<?>... parameterTypes) {
        assertThat(method.getReturnType()).isEqualTo(returnType);
        assertThat(method.getParameterTypes()).containsExactly(parameterTypes);
        assertThat(Modifier.isPublic(method.getModifiers())).isTrue();
        assertThat(Modifier.isAbstract(method.getModifiers())).isTrue();
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

    private static void assertRejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable).isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class);
    }

    private static KnowledgePlanDTO plan(
        String candidateCode,
        String planCode,
        Long templateVersionId,
        String angleCode,
        String techniqueCode
    ) {
        return new KnowledgePlanDTO(candidateCode, planCode, templateVersionId, angleCode, techniqueCode);
    }

    private static KnowledgeRouteResultDTO validRoute() {
        return new KnowledgeRouteResultDTO(
            "routing-v1",
            "VIDEO_TYPE",
            List.of(
                plan("A", "PLAN_A", 1L, "ANGLE_A", "TECHNIQUE_A"),
                plan("B", "PLAN_B", 2L, "ANGLE_B", "TECHNIQUE_B"),
                plan("C", "PLAN_C", 3L, "ANGLE_C", "TECHNIQUE_C")
            ),
            "route-hash"
        );
    }

    private static KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO fact(Long id) {
        return new KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO(
            id, id, "fact-" + id, "evidence-" + id);
    }

    private static KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO material(Long id, Integer order) {
        return new KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO(
            id, id + 10, id + 20, "excerpt-" + id, order);
    }

    private static KnowledgeSnapshotDTO snapshot(
        Long snapshotId,
        Long rootTaskId,
        Long promptVersionId,
        Long generationContextRevision,
        String generationInputHash,
        KnowledgeRouteResultDTO route,
        List<KnowledgeSnapshotRequestDTO.AcceptedFactSnapshotDTO> acceptedFacts,
        List<KnowledgeSnapshotDTO.KnowledgeMaterialSnapshotDTO> knowledgeMaterials,
        String contentHash,
        Instant createdAt
    ) {
        return new KnowledgeSnapshotDTO(
            snapshotId,
            rootTaskId,
            promptVersionId,
            generationContextRevision,
            generationInputHash,
            route,
            acceptedFacts,
            knowledgeMaterials,
            contentHash,
            createdAt
        );
    }
}
