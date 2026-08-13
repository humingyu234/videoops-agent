package org.dromara.aivideo.knowledge;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("dev")
class KnowledgeLiteBoundaryTest {

    private static final String CORE_MAIN =
        "ruoyi-modules/ai-video/ai-video-core/src/main/java/";
    private static final String KNOWLEDGE_PACKAGE =
        "org/dromara/aivideo/knowledge/";
    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final Path KNOWLEDGE_ROOT = PROJECT_ROOT.resolve(CORE_MAIN + KNOWLEDGE_PACKAGE);
    private static final Path MIGRATION = PROJECT_ROOT.resolve(
        "../docs/sql/ai-video/mysql/20260803_01_p1_knowledge_lite.sql");

    private static final Map<String, Pattern> FORBIDDEN_REFERENCES = forbiddenReferences();
    private static final Pattern CREATE_TABLE = Pattern.compile(
        "(?im)^\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([a-z0-9_]+)`?");
    private static final Pattern FORBIDDEN_TYPE = Pattern.compile(
        "\\b(?:class|interface|record|enum)\\s+([A-Za-z_$][\\w$]*(?:Controller|BO|VO))\\b");
    private static final Pattern FULL_P1_DTO = Pattern.compile(
        "\\bKnowledge(?:Plan|RouteRequest|RouteResult|SnapshotRequest|Snapshot)DTO\\b");

    @Test
    void scannerFixtureFindsForbiddenReferences() {
        List<String> sources = List.of(
            "import org.dromara.common.satoken.utils.LoginHelper;",
            "import org.springframework.web.reactive.function.client.WebClient;",
            "class Fixture { WebClient client; }"
        );

        assertEquals(List.of("LoginHelper", "WebClient"), findForbiddenReferences(sources));
    }

    @Test
    void stableContextContractsExistOnlyAtTheirExactCorePaths() throws IOException {
        assertExactLocation(
            "KnowledgeContextRequestDTO.java",
            CORE_MAIN + KNOWLEDGE_PACKAGE + "dto/KnowledgeContextRequestDTO.java");
        assertExactLocation(
            "KnowledgeContextDTO.java",
            CORE_MAIN + KNOWLEDGE_PACKAGE + "dto/KnowledgeContextDTO.java");
        assertExactLocation(
            "IKnowledgeContextService.java",
            CORE_MAIN + KNOWLEDGE_PACKAGE + "service/IKnowledgeContextService.java");
    }

    @Test
    void productionKnowledgeHasNoIdentityNetworkAiOrVendorCoupling() throws IOException {
        List<String> productionSources = readSources(javaSources(KNOWLEDGE_ROOT));

        assertEquals(List.of(), findForbiddenReferences(productionSources));
    }

    @Test
    void k0DoesNotReachTasksAccountingScriptsOrWorkspacesAndOwnsOnlyFourTables() throws IOException {
        Map<String, Pattern> forbiddenBusinessReferences = new LinkedHashMap<>();
        forbiddenBusinessReferences.put("av_ai_task", wordPattern("av_ai_task"));
        forbiddenBusinessReferences.put("quota", wordPattern("quota"));
        forbiddenBusinessReferences.put("ledger", wordPattern("ledger"));
        forbiddenBusinessReferences.put("usage", wordPattern("usage"));
        forbiddenBusinessReferences.put("script draft", Pattern.compile(
            "\\bscript(?:[_\\s.-]?draft)\\b", Pattern.CASE_INSENSITIVE));
        forbiddenBusinessReferences.put("workspace", wordPattern("workspace"));

        assertEquals(
            List.of(),
            findMatches(readSources(javaSources(KNOWLEDGE_ROOT)), forbiddenBusinessReferences));

        assertTrue(Files.isRegularFile(MIGRATION), () -> "Missing K0 migration: " + MIGRATION);
        Matcher matcher = CREATE_TABLE.matcher(Files.readString(MIGRATION));
        Set<String> actualTables = new TreeSet<>();
        while (matcher.find()) {
            actualTables.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        assertEquals(
            Set.of(
                "av_knowledge_item",
                "av_knowledge_version",
                "av_knowledge_binding",
                "av_video_type_rule"),
            actualTables);
    }

    @Test
    void knowledgeUsesRuoYiLayersWithoutParallelPackagesOrWebTransferTypes() throws IOException {
        Set<String> forbiddenPackages = Set.of("application", "port", "adapter", "command", "model");
        List<String> packageViolations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(KNOWLEDGE_ROOT)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                Path relative = KNOWLEDGE_ROOT.relativize(path);
                for (Path segment : relative) {
                    if (forbiddenPackages.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                        packageViolations.add(toUnix(relative));
                        break;
                    }
                }
            });
        }

        List<String> typeViolations = new ArrayList<>();
        for (Path source : javaSources(KNOWLEDGE_ROOT)) {
            Matcher matcher = FORBIDDEN_TYPE.matcher(Files.readString(source));
            while (matcher.find()) {
                typeViolations.add(toUnix(KNOWLEDGE_ROOT.relativize(source)) + ":" + matcher.group(1));
            }
        }

        assertEquals(List.of(), packageViolations);
        assertEquals(List.of(), typeViolations);
    }

    @Test
    void k0TypesDoNotConsumeFullP1DtosOrInventRevisionAndFactFields() throws IOException {
        List<Path> k0Sources = List.of(
            knowledgePath("dto/KnowledgeContextRequestDTO.java"),
            knowledgePath("dto/KnowledgeContextDTO.java"),
            knowledgePath("service/IKnowledgeContextService.java"),
            knowledgePath("service/impl/KnowledgeContextServiceImpl.java"),
            knowledgePath("domain/KnowledgeItem.java"),
            knowledgePath("domain/KnowledgeVersion.java"),
            knowledgePath("domain/KnowledgeBinding.java"),
            knowledgePath("domain/VideoTypeRule.java"),
            knowledgePath("mapper/KnowledgeItemMapper.java"),
            knowledgePath("mapper/KnowledgeVersionMapper.java"),
            knowledgePath("mapper/KnowledgeBindingMapper.java"),
            knowledgePath("mapper/VideoTypeRuleMapper.java"),
            knowledgePath("KnowledgeDomainCode.java"),
            knowledgePath("KnowledgeTypeCode.java"),
            knowledgePath("KnowledgeVersionStatus.java"));

        List<String> p1DtoViolations = new ArrayList<>();
        for (Path source : k0Sources) {
            assertTrue(Files.isRegularFile(source), () -> "Missing K0 source: " + source);
            Matcher matcher = FULL_P1_DTO.matcher(Files.readString(source));
            while (matcher.find()) {
                p1DtoViolations.add(toUnix(KNOWLEDGE_ROOT.relativize(source)) + ":" + matcher.group());
            }
        }
        assertEquals(List.of(), p1DtoViolations);

        Set<String> fabricatedFieldNames = Set.of(
            "revision", "revisionId", "revisionNo",
            "version", "versionId", "versionNo",
            "fact", "factId", "factIds", "facts");
        List<String> actualContractFields = Stream.of(
                KnowledgeContextRequestDTO.class,
                KnowledgeContextDTO.class)
            .flatMap(type -> Stream.of(type.getRecordComponents()))
            .map(RecordComponent::getName)
            .filter(fabricatedFieldNames::contains)
            .sorted()
            .toList();
        assertEquals(List.of(), actualContractFields);
    }

    private static List<String> findForbiddenReferences(List<String> sources) {
        return findMatches(sources, FORBIDDEN_REFERENCES);
    }

    private static List<String> findMatches(List<String> sources, Map<String, Pattern> rules) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (Map.Entry<String, Pattern> rule : rules.entrySet()) {
            if (sources.stream().anyMatch(source -> rule.getValue().matcher(source).find())) {
                matches.add(rule.getKey());
            }
        }
        return List.copyOf(matches);
    }

    private static Map<String, Pattern> forbiddenReferences() {
        Map<String, Pattern> rules = new LinkedHashMap<>();
        rules.put("LoginHelper", wordPattern("LoginHelper"));
        rules.put("AppLoginHelper", wordPattern("AppLoginHelper"));
        rules.put("StpUtil", wordPattern("StpUtil"));
        rules.put("RestClient", wordPattern("RestClient"));
        rules.put("WebClient", wordPattern("WebClient"));
        rules.put("Feign", Pattern.compile(
            "\\bFeign(?:Client)?\\b|org\\.springframework\\.cloud\\.openfeign\\.",
            Pattern.CASE_INSENSITIVE));
        rules.put("Spring AI", Pattern.compile("org\\.springframework\\.ai\\.", Pattern.CASE_INSENSITIVE));
        rules.put("vendor SDK", Pattern.compile(
            "(?m)^\\s*import\\s+(?:com\\.openai|com\\.anthropic|com\\.alibaba\\.dashscope|"
                + "com\\.baidubce\\.qianfan|com\\.tencentcloudapi|com\\.huaweicloud\\.sdk|"
                + "com\\.volcengine)(?:\\.|;)",
            Pattern.CASE_INSENSITIVE));
        return rules;
    }

    private static Pattern wordPattern(String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
    }

    private static void assertExactLocation(String fileName, String expectedRelativePath) throws IOException {
        List<String> actual;
        try (Stream<Path> paths = Files.walk(PROJECT_ROOT)) {
            actual = paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equals(fileName))
                .filter(path -> path.toString().endsWith(".java"))
                .map(PROJECT_ROOT::relativize)
                .map(KnowledgeLiteBoundaryTest::toUnix)
                .sorted()
                .toList();
        }
        assertEquals(List.of(expectedRelativePath), actual, fileName + " must have one stable location");
    }

    private static List<Path> javaSources(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .sorted()
                .toList();
        }
    }

    private static List<String> readSources(List<Path> paths) throws IOException {
        List<String> sources = new ArrayList<>(paths.size());
        for (Path path : paths) {
            sources.add(Files.readString(path));
        }
        return List.copyOf(sources);
    }

    private static Path knowledgePath(String relative) {
        return KNOWLEDGE_ROOT.resolve(relative);
    }

    private static String toUnix(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (isProjectRoot(current)) {
                return current;
            }
            Path nested = current.resolve("ai-video-api");
            if (isProjectRoot(nested)) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Unable to locate ai-video-api project root");
    }

    private static boolean isProjectRoot(Path candidate) {
        return Files.isDirectory(candidate.resolve(CORE_MAIN + KNOWLEDGE_PACKAGE))
            && Files.isDirectory(candidate.resolve("../docs/sql/ai-video/mysql"));
    }
}
