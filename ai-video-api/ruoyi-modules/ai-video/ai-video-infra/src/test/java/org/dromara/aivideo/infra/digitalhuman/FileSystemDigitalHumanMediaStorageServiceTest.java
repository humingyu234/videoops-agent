package org.dromara.aivideo.infra.digitalhuman;

import org.dromara.aivideo.digitalhuman.dto.DigitalHumanStoredMediaDTO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class FileSystemDigitalHumanMediaStorageServiceTest {

    @TempDir
    private Path mediaRoot;

    @Test
    void deletesStoredMediaIdempotently() {
        FileSystemDigitalHumanMediaStorageService storage = storage();
        DigitalHumanStoredMediaDTO stored = storage.storeInput(
            41L, "reference.wav", "audio/wav", wav());

        assertThat(Files.isRegularFile(mediaRoot.resolve(stored.key()))).isTrue();
        assertThatCode(() -> storage.delete(stored.key())).doesNotThrowAnyException();
        assertThatCode(() -> storage.delete(stored.key())).doesNotThrowAnyException();
        assertThatThrownBy(() -> storage.read(stored.key()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("不可用");
    }

    @Test
    void requiresFileExtensionMimeTypeAndMagicBytesToAgree() {
        FileSystemDigitalHumanMediaStorageService storage = storage();

        assertThatThrownBy(() -> storage.storeInput(
            42L, "reference.mp3", "audio/wav", wav()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("无效");
        assertThatThrownBy(() -> storage.storeInput(
            42L, "reference.wav", "audio/wav", "not-a-wav".getBytes(StandardCharsets.US_ASCII)))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("无效");
        assertThat(mediaRoot.resolve("42/input")).doesNotExist();
    }

    @Test
    void rejectsSymbolicLinkInTheStoragePath() throws Exception {
        FileSystemDigitalHumanMediaStorageService storage = storage();
        Path outside = Files.createDirectories(mediaRoot.resolve("outside"));
        Path jobDirectory = Files.createDirectories(mediaRoot.resolve("43"));
        try {
            Files.createSymbolicLink(jobDirectory.resolve("input"), outside);
        } catch (IOException | UnsupportedOperationException exception) {
            Assumptions.assumeTrue(false, "当前文件系统不允许创建符号链接");
        }

        assertThatThrownBy(() -> storage.storeInput(
            43L, "reference.wav", "audio/wav", wav()))
            .isInstanceOf(RuntimeException.class);
        try (Stream<Path> files = Files.list(outside)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void rejectsStoredMediaThatWasTamperedAfterWrite() throws Exception {
        FileSystemDigitalHumanMediaStorageService storage = storage();
        DigitalHumanStoredMediaDTO stored = storage.storeInput(
            44L, "reference.wav", "audio/wav", wav());
        Files.write(mediaRoot.resolve(stored.key()), "not-a-wav".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> storage.read(stored.key()))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("不可用");
    }

    private FileSystemDigitalHumanMediaStorageService storage() {
        return new FileSystemDigitalHumanMediaStorageService(mediaRoot.toString());
    }

    private static byte[] wav() {
        return "RIFF\u0004\u0000\u0000\u0000WAVEdata".getBytes(StandardCharsets.ISO_8859_1);
    }
}
