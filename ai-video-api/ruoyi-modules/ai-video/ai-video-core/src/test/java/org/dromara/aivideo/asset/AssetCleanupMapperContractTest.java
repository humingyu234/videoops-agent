package org.dromara.aivideo.asset;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class AssetCleanupMapperContractTest {

    @Test
    void cleanupSqlUsesTtlUnboundCheckAndAtomicReservation() throws Exception {
        String xml = Files.readString(Path.of(
            "src/main/resources/mapper/asset/AssetFileMapper.xml")).toLowerCase(Locale.ROOT);

        assertThat(xml).contains("category = 'portrait_image'")
            .contains("create_time &lt; #{cutoff}")
            .contains("not exists")
            .contains("from av_portrait")
            .contains("status = 'delete_pending'")
            .contains("limit #{limit}")
            .contains("for update");
    }
}
