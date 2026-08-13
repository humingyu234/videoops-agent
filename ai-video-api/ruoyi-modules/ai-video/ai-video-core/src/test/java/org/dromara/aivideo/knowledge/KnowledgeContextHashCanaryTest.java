package org.dromara.aivideo.knowledge;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class KnowledgeContextHashCanaryTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Test
    void freezesCanonicalBytesAndSha256() throws Exception {
        byte[] bytes = canonicalBytes(
            List.of(2001L, 2002L),
            List.of("先给利益点。", "用事实支撑卖点。"),
            List.of("15秒：1句钩子+2句卖点+1句行动号召", "禁止虚构价格或效果"));

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(
            "{\"knowledgeVersionIds\":[2001,2002],\"excerpts\":[\"先给利益点。\",\"用事实支撑卖点。\"],"
                + "\"copyRules\":[\"15秒：1句钩子+2句卖点+1句行动号召\",\"禁止虚构价格或效果\"]}");
        assertThat(sha256(bytes)).isEqualTo("0e29ebac61ef88a724b0365743f9bed2db9aef7bbe40c94f8f92b79ae9863346");
    }

    @Test
    void freezesEmptyCanonicalBytesAndSha256() throws Exception {
        byte[] bytes = canonicalBytes(List.of(), List.of(), List.of());

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo(
            "{\"knowledgeVersionIds\":[],\"excerpts\":[],\"copyRules\":[]}");
        assertThat(sha256(bytes)).isEqualTo("62dffd7d09a50ad03b651edf697d9ab42a09c9607973ab89036bc2b6abb67e34");
    }

    private static byte[] canonicalBytes(List<Long> ids, List<String> excerpts, List<String> rules) throws Exception {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("knowledgeVersionIds", ids);
        canonical.put("excerpts", excerpts);
        canonical.put("copyRules", rules);
        return JSON_MAPPER.writeValueAsBytes(canonical);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
