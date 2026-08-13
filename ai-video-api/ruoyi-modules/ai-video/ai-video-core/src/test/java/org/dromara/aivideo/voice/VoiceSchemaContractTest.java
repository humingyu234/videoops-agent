package org.dromara.aivideo.voice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class VoiceSchemaContractTest {

    @Test
    void migrationFreezesVoiceTranscriptionContract() throws IOException {
        Path apiRoot = locateApiRoot();
        Path migration = apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE av_voice");
        assertThat(sql).contains("UNIQUE KEY uk_av_voice_owner_idempotency");
        assertThat(sql).contains("UNIQUE KEY uk_av_voice_tenant_asset");
        assertThat(sql).contains("KEY idx_av_voice_owner_list");
        assertThat(sql).contains("KEY idx_av_voice_transcription_claim");
        assertThat(sql).contains("CHECK (transcription_status IN ('pending','transcribing','ready','failed'))");
    }

    @Test
    void timelineMigrationAddsWhisperCueStorage() throws IOException {
        Path apiRoot = locateApiRoot();
        Path migration = apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_05_voice_transcript_timeline.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);

        assertThat(sql).contains("ADD COLUMN transcript_timeline_json JSON DEFAULT NULL");
    }

    @Test
    void deletePermissionMigrationIsFailClosedAndIdempotent() throws IOException {
        Path apiRoot = locateApiRoot();
        Path migration = apiRoot.resolve("../docs/sql/ai-video/mysql/20260804_01_voice_delete_permission.sql");

        assertThat(migration).exists();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertThat(sql).contains("START TRANSACTION", "CREATE TEMPORARY TABLE", "CHECK (valid_value = 1)");
        assertThat(sql).contains("1000024", "'aivideo:voice:delete'", "'声音删除'", "'voice'", "'delete'");
        assertThat(sql).contains("1000224", "1000101", "'personal_creator'", "'personal'");
        assertThat(sql).contains("INSERT INTO app_permission", "WHERE NOT EXISTS", "INSERT INTO app_role_permission");
        assertThat(sql).contains("SET @voice_delete_binding_inserted = ROW_COUNT()");
        assertThat(sql).contains("role_revision = role_revision + 1",
            "app_user.permission_revision = app_user.permission_revision + 1");
        assertThat(sql).contains("@voice_delete_binding_inserted = 1", "valid_from", "valid_until", "DROP TEMPORARY TABLE", "COMMIT");
        assertThat(sql).doesNotContainIgnoringCase("ON DUPLICATE KEY UPDATE");

        Path existing = apiRoot.resolve("../docs/sql/ai-video/mysql/20260803_04_voice_upload_transcription.sql");
        assertThat(existing).exists();
        String existingSql = Files.readString(existing, StandardCharsets.UTF_8);
        assertThat(existingSql).contains("1000020", "1000021", "1000022", "1000023",
            "1000220", "1000221", "1000222", "1000223");
    }

    private static Path locateApiRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("../docs/sql/ai-video/mysql"))) {
                return current;
            }
            Path nested = current.resolve("ai-video-api");
            if (Files.isDirectory(nested.resolve("../docs/sql/ai-video/mysql"))) {
                return nested;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate ai-video-api root");
    }
}
