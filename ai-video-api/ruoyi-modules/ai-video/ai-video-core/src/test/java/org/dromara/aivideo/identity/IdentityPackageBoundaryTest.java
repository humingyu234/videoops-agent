package org.dromara.aivideo.identity;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class IdentityPackageBoundaryTest {

    private static final String IDENTITY_SOURCE =
        "ruoyi-modules/ai-video/ai-video-core/src/main/java/org/dromara/aivideo/identity";

    @Test
    void shouldUseRuoyiIdentityPackagesAndNames() throws IOException {
        Path identitySource = locateApiRoot().resolve(IDENTITY_SOURCE);

        assertThat(javaFiles(identitySource.resolve("application")))
            .as("identity/application production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("adapter")))
            .as("identity/adapter production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("command")))
            .as("identity/command production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("port")))
            .as("identity/port production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("model")))
            .as("identity/model production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("domain/dto")))
            .as("identity/domain/dto production sources")
            .isEmpty();
        assertThat(javaFiles(identitySource.resolve("service/dto")))
            .as("identity/service/dto production sources")
            .isEmpty();

        assertThat(javaFileNames(identitySource.resolve("dto")))
            .as("identity DTO source names")
            .isNotEmpty()
            .allMatch(name -> name.endsWith("DTO.java"));
        assertThat(directJavaFileNames(identitySource.resolve("service")))
            .as("identity Service interface source names")
            .isNotEmpty()
            .allMatch(name -> name.startsWith("I") && name.endsWith("Service.java"));
        assertThat(javaFileNames(identitySource.resolve("service/impl")))
            .as("identity Service implementation source names")
            .isNotEmpty()
            .allMatch(name -> name.endsWith("ServiceImpl.java"));
    }

    @Test
    void shouldNotPublishAiVideoDtosFromGlobalRuoyiApi() throws IOException {
        Path globalAiVideoSource = locateApiRoot().resolve("ruoyi-api/src/main/java/org/dromara/aivideo");

        assertThat(javaFiles(globalAiVideoSource))
            .as("ai-video production sources in global ruoyi-api")
            .isEmpty();
    }

    private static List<Path> javaFiles(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".java"))
                .toList();
        }
    }

    private static List<String> javaFileNames(Path directory) throws IOException {
        return javaFiles(directory).stream()
            .map(path -> path.getFileName().toString())
            .toList();
    }

    private static List<String> directJavaFileNames(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".java"))
                .toList();
        }
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
}
