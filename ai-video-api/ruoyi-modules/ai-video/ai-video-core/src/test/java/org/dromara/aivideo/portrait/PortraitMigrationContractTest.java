package org.dromara.aivideo.portrait;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class PortraitMigrationContractTest {

    @Test
    void forwardMigrationAlignsImageFormatsAndCreateIdempotency() throws Exception {
        Path migration = locateMigration();

        assertThat(migration).exists();
        String sql = Files.readString(migration).toLowerCase(Locale.ROOT);

        assertThat(sql).contains("idempotency_key", "request_digest", "uk_av_portrait_idempotency");
        assertThat(sql).contains("'jpeg'", "'png'", "'webp'", "'gif'");
        assertThat(sql).contains("ck_av_asset_portrait_type");
    }

    private Path locateMigration() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path migration = directory.resolve("../docs/sql/ai-video/mysql/20260804_01_portrait_library_remediation.sql");
            if (Files.exists(migration)) {
                return migration;
            }
            directory = directory.getParent();
        }
        return Path.of("../docs/sql/ai-video/mysql/20260804_01_portrait_library_remediation.sql");
    }
}
