package org.dromara.aivideo.infra.timeline;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class TimelineContractFixtureTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> REQUIRED_C0_FILES = List.of(
        "timeline-1.schema.json",
        "timeline-draft.example.json",
        "font-registry.json"
    );

    @Test
    void readsTheCanonicalC0FilesDirectlyFromTheRepositoryRoot() throws IOException {
        Path contractDirectory = contractDirectory();
        assertThat(contractDirectory).isDirectory();

        for (String fileName : REQUIRED_C0_FILES) {
            Path file = contractDirectory.resolve(fileName);
            assertThat(file).as(fileName).isRegularFile();
            assertThat(JSON.readTree(Files.readString(file, StandardCharsets.UTF_8))).as(fileName).isNotNull();
        }

        JsonNode schema = readCanonical("timeline-1.schema.json");
        JsonNode draft = readCanonical("timeline-draft.example.json");
        assertThat(schema.at("/x-ai-video-output-encoding/container").textValue()).isEqualTo("mp4");
        assertThat(schema.at("/x-ai-video-semantics/pipLoopFormula").textValue()).isNotBlank();
        assertThat(draft.at("/timeline/schemaVersion").textValue()).isEqualTo("timeline-1");
        assertThat(draft.at("/timeline/canvas/width").intValue()).isEqualTo(1080);
        assertThat(draft.at("/timeline/canvas/height").intValue()).isEqualTo(1920);
    }

    @Test
    void failsInsteadOfFallingBackToACopiedMediaFixtureWhenCanonicalC0IsMissing() {
        assertThatThrownBy(() -> requiredCanonicalFile("missing-c0-contract.json"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("canonical timeline contract file is missing");
    }

    private static JsonNode readCanonical(String fileName) throws IOException {
        return JSON.readTree(requiredCanonicalFile(fileName).toFile());
    }

    private static Path contractDirectory() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/contracts/creation-timeline");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("canonical timeline contract directory is missing");
    }

    private static Path requiredCanonicalFile(String fileName) {
        Path file = contractDirectory().resolve(fileName);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("canonical timeline contract file is missing");
        }
        return file;
    }
}
