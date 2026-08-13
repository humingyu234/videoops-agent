package org.dromara.aivideo.knowledge;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.dromara.aivideo.knowledge.domain.KnowledgeBinding;
import org.dromara.aivideo.knowledge.domain.KnowledgeItem;
import org.dromara.aivideo.knowledge.domain.KnowledgeVersion;
import org.dromara.aivideo.knowledge.domain.VideoTypeRule;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;
import org.dromara.aivideo.knowledge.mapper.KnowledgeBindingMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeItemMapper;
import org.dromara.aivideo.knowledge.mapper.KnowledgeVersionMapper;
import org.dromara.aivideo.knowledge.mapper.VideoTypeRuleMapper;
import org.dromara.aivideo.knowledge.service.impl.KnowledgeContextServiceImpl;
import org.dromara.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Tag("dev")
class KnowledgeContextServiceTest {

    private static final String EMPTY_HASH = "62dffd7d09a50ad03b651edf697d9ab42a09c9607973ab89036bc2b6abb67e34";
    private static final KnowledgeContextRequestDTO REQUEST =
        new KnowledgeContextRequestDTO("food", "store_traffic", 15, List.of("fresh", "value"));

    @Mock
    private KnowledgeItemMapper itemMapper;
    @Mock
    private KnowledgeVersionMapper versionMapper;
    @Mock
    private KnowledgeBindingMapper bindingMapper;
    @Mock
    private VideoTypeRuleMapper ruleMapper;

    private JsonMapper jsonMapper;
    private KnowledgeContextServiceImpl service;

    @BeforeEach
    void setUp() {
        jsonMapper = JsonMapper.builder().build();
        service = new KnowledgeContextServiceImpl(itemMapper, versionMapper, bindingMapper, ruleMapper, jsonMapper);
    }

    @Test
    void resolvesUnorderedFixturesByAllTieBreakersAndDeduplicatesBindingsAndRules() throws Exception {
        List<KnowledgeBinding> bindings = List.of(
            binding(61, 6, 106, "*", "*", 100, "published"),
            binding(12, 1, 101, "food", "store_traffic", 9, "published"),
            binding(51, 5, 105, "*", "store_traffic", 100, "published"),
            binding(31, 3, 103, "food", "store_traffic", 10, "published"),
            binding(81, 8, 108, "food", "store_traffic", 100, "published"),
            binding(41, 4, 104, "food", "*", 100, "published"),
            binding(21, 2, 102, "food", "store_traffic", 10, "published"),
            binding(11, 1, 101, "food", "store_traffic", 9, "published"),
            binding(71, 7, 107, "food", "store_traffic", 100, "retired")
        );
        List<KnowledgeItem> items = List.of(
            item(8, "draft", "[]", 108), item(6, "wild-wild", "[]", 106),
            item(4, "exact-wild", "[]", 104), item(1, "z-exact", "[\"fresh\",\"value\"]", 101),
            item(3, "a-exact", "[\"fresh\"]", 103), item(5, "wild-exact", "[]", 105),
            item(2, "b-exact", "[\"fresh\"]", 102)
        );
        List<KnowledgeVersion> versions = List.of(
            version(106, 6, 1, "published", "wild-wild"),
            version(102, 2, 3, "published", "stable-b"),
            version(108, 8, 1, "draft", "draft"),
            version(104, 4, 1, "published", "exact-wild"),
            version(101, 1, 2, "published", "  tag winner  "),
            version(105, 5, 1, "published", "wild-exact"),
            version(103, 3, 4, "published", "stable-a")
        );
        List<VideoTypeRule> rules = List.of(
            rule(8, "retired", 1, "food", "store_traffic", 200, null, null,
                "[\"retired\"]", "retired"),
            rule(4, "rule-wild-wild", 1, "*", "*", 50, null, null,
                "[\"wild-wild\"]", "published"),
            rule(2, "rule-b", 2, "food", "store_traffic", 10, 10, 20,
                "[\"duplicate\",\"second\"]", "published"),
            rule(3, "rule-exact-wild", 1, "food", "*", 99, null, null,
                "[\"exact-wild\"]", "published"),
            rule(7, "duration-30", 1, "food", "store_traffic", 999, 30, 30,
                "[\"thirty\"]", "published"),
            rule(1, "rule-a", 1, "food", "store_traffic", 10, null, null,
                "[\"first\",\" duplicate \"]", "published"),
            rule(5, "rule-wild-exact", 1, "*", "store_traffic", 99, null, null,
                "[\"wild-exact\"]", "published")
        );
        stub(bindings, items, versions, rules);

        KnowledgeContextDTO result = service.resolve(REQUEST);

        assertThat(result.knowledgeVersionIds()).containsExactly(101L, 103L, 102L, 104L, 105L, 106L);
        assertThat(result.excerpts()).containsExactly(
            "tag winner", "stable-a", "stable-b", "exact-wild", "wild-exact", "wild-wild");
        assertThat(result.copyRules()).containsExactly(
            "first", "duplicate", "second", "exact-wild", "wild-exact", "wild-wild");
        assertThat(result.contentHash()).isEqualTo(hashOf(result.knowledgeVersionIds(), result.excerpts(), result.copyRules()));
        verify(bindingMapper, times(1)).selectList(any(Wrapper.class));
        verify(itemMapper, times(1)).selectList(any(Wrapper.class));
        verify(versionMapper, times(1)).selectList(any(Wrapper.class));
        verify(ruleMapper, times(1)).selectList(any(Wrapper.class));
    }

    @Test
    void rejectsEveryUnsupportedBindingAndRuleConditionInsteadOfIgnoringIt() {
        KnowledgeBinding valid = binding(1, 1, 101, "food", "store_traffic", 1, "published");
        KnowledgeBinding videoType = binding(2, 2, 102, "food", "store_traffic", 100, "published");
        videoType.setVideoTypeCode("product");
        KnowledgeBinding required = binding(3, 3, 103, "food", "store_traffic", 100, "published");
        required.setRequiredFlag(true);
        KnowledgeBinding angles = binding(4, 4, 104, "food", "store_traffic", 100, "published");
        angles.setAngleCodesJson("[\"front\"]");
        KnowledgeBinding priorities = binding(5, 5, 105, "food", "store_traffic", 100, "published");
        priorities.setAnglePrioritiesJson("{\"front\":1}");
        KnowledgeBinding slots = binding(6, 6, 106, "food", "store_traffic", 100, "published");
        slots.setRequiredSlotCodesJson("[\"hook\"]");
        KnowledgeBinding audience = binding(7, 7, 107, "food", "store_traffic", 100, "published");
        audience.setAudienceTagCodesJson("[\"parent\"]");
        KnowledgeBinding exclusions = binding(8, 8, 108, "food", "store_traffic", 100, "published");
        exclusions.setExclusionConditionsJson("[\"price\"]");
        KnowledgeBinding partialDuration = binding(9, 9, 109, "food", "store_traffic", 100, "published");
        partialDuration.setMinDurationSeconds(1);

        VideoTypeRule validRule = rule(1, "valid", 1, "food", "store_traffic", 1, 15, 15,
            "[\"valid\"]", "published");
        VideoTypeRule typedRule = rule(2, "typed", 1, "food", "store_traffic", 100, null, null,
            "[\"typed\"]", "published");
        typedRule.setVideoTypeCode("product");
        VideoTypeRule slottedRule = rule(3, "slotted", 1, "food", "store_traffic", 100, null, null,
            "[\"slotted\"]", "published");
        slottedRule.setRequiredSlotCodesJson("[\"hook\"]");
        VideoTypeRule partialRule = rule(4, "partial", 1, "food", "store_traffic", 100, null, null,
            "[\"partial\"]", "published");
        partialRule.setMaxDurationSeconds(30);

        stub(List.of(partialDuration, exclusions, audience, slots, priorities, angles, required, videoType, valid),
            List.of(item(1, "only", "[]", 101)),
            List.of(version(101, 1, 1, "published", "only")),
            List.of(partialRule, slottedRule, typedRule, validRule));

        KnowledgeContextDTO result = service.resolve(REQUEST);

        assertThat(result.knowledgeVersionIds()).containsExactly(101L);
        assertThat(result.copyRules()).containsExactly("valid");
    }

    @Test
    void returnsCanonicalEmptyResultWithoutItemOrVersionQueries() {
        when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        KnowledgeContextDTO result = service.resolve(REQUEST);

        assertThat(result.knowledgeVersionIds()).isEmpty();
        assertThat(result.excerpts()).isEmpty();
        assertThat(result.copyRules()).isEmpty();
        assertThat(result.contentHash()).isEqualTo(EMPTY_HASH);
        verify(itemMapper, never()).selectList(any(Wrapper.class));
        verify(versionMapper, never()).selectList(any(Wrapper.class));
    }

    @Test
    void failsClosedForMalformedTagsAndBlankPublishedContentWithoutLeakingContent() {
        for (String tagsJson : List.of("not-json", "{}", "[\"ok\",null]", "[\"  \"]")) {
            stub(List.of(binding(1, 1, 101, "food", "store_traffic", 1, "published")),
                List.of(item(1, "bad", tagsJson, 101)),
                List.of(version(101, 1, 1, "published", "secret-product-body")), List.of());
            assertThatThrownBy(() -> service.resolve(REQUEST))
                .isInstanceOf(ServiceException.class)
                .hasMessageNotContaining("secret-product-body");
        }

        stub(List.of(binding(1, 1, 101, "food", "store_traffic", 1, "published")),
            List.of(item(1, "blank", "[]", 101)),
            List.of(version(101, 1, 1, "published", "   \n")), List.of());
        assertThatThrownBy(() -> service.resolve(REQUEST)).isInstanceOf(ServiceException.class);
    }

    @Test
    void failsClosedForMalformedCopyRules() {
        for (String copyRulesJson : List.of("not-json", "{}", "[\"ok\",null]", "[\"  \"]")) {
            stub(List.of(), List.of(), List.of(), List.of(
                rule(1, "bad", 1, "food", "store_traffic", 1, null, null, copyRulesJson, "published")));
            assertThatThrownBy(() -> service.resolve(REQUEST))
                .isInstanceOf(ServiceException.class)
                .hasMessageNotContaining(copyRulesJson);
        }
    }

    @Test
    void failsClosedForPublishedPointerItemAndVersionMismatch() {
        KnowledgeBinding binding = binding(1, 1, 101, "food", "store_traffic", 1, "published");

        stub(List.of(binding), List.of(item(1, "pointer", "[]", 999)),
            List.of(version(101, 1, 1, "published", "body")), List.of());
        assertThatThrownBy(() -> service.resolve(REQUEST)).isInstanceOf(ServiceException.class);

        stub(List.of(binding), List.of(item(1, "owner", "[]", 101)),
            List.of(version(101, 2, 1, "published", "body")), List.of());
        assertThatThrownBy(() -> service.resolve(REQUEST)).isInstanceOf(ServiceException.class);
    }

    @Test
    void excludesUnpublishedVersionAndRetiredBinding() {
        stub(List.of(
                binding(1, 1, 101, "food", "store_traffic", 1, "retired"),
                binding(2, 2, 102, "food", "store_traffic", 1, "published")),
            List.of(item(2, "draft", "[]", 102)),
            List.of(version(102, 2, 1, "draft", "draft")), List.of());

        KnowledgeContextDTO result = service.resolve(REQUEST);

        assertThat(result.knowledgeVersionIds()).isEmpty();
        assertThat(result.contentHash()).isEqualTo(EMPTY_HASH);
    }

    @Test
    void failsClosedWhenMapperThrowsAndDoesNotReturnFallback() {
        when(bindingMapper.selectList(any(Wrapper.class)))
            .thenThrow(new IllegalStateException("database rejected secret-product-profile"));

        assertThatThrownBy(() -> service.resolve(REQUEST))
            .isInstanceOf(ServiceException.class)
            .hasMessageNotContaining("secret-product-profile");
    }

    @Test
    void rejectsNullRequest() {
        assertThatThrownBy(() -> service.resolve(null)).isInstanceOf(ServiceException.class);
    }

    private void stub(List<KnowledgeBinding> bindings, List<KnowledgeItem> items,
                      List<KnowledgeVersion> versions, List<VideoTypeRule> rules) {
        when(bindingMapper.selectList(any(Wrapper.class))).thenReturn(bindings);
        if (!bindings.isEmpty()) {
            when(itemMapper.selectList(any(Wrapper.class))).thenReturn(items);
            when(versionMapper.selectList(any(Wrapper.class))).thenReturn(versions);
        }
        when(ruleMapper.selectList(any(Wrapper.class))).thenReturn(rules);
    }

    private String hashOf(List<Long> ids, List<String> excerpts, List<String> rules) throws Exception {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("knowledgeVersionIds", ids);
        canonical.put("excerpts", excerpts);
        canonical.put("copyRules", rules);
        byte[] bytes = jsonMapper.writeValueAsBytes(canonical);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static KnowledgeBinding binding(long id, long itemId, long versionId, String industry,
                                              String purpose, int priority, String status) {
        KnowledgeBinding value = new KnowledgeBinding();
        value.setKnowledgeBindingId(id);
        value.setBindingGroupCode("binding-" + id);
        value.setVersionNo(1);
        value.setKnowledgeItemId(itemId);
        value.setKnowledgeVersionId(versionId);
        value.setIndustryCode(industry);
        value.setPurposeCode(purpose);
        value.setVideoTypeCode("*");
        value.setAngleCodesJson("[]");
        value.setAnglePrioritiesJson("{}");
        value.setPriority(priority);
        value.setRequiredFlag(false);
        value.setRequiredSlotCodesJson("[]");
        value.setAudienceTagCodesJson("[]");
        value.setExclusionConditionsJson("[]");
        value.setStatus(status);
        return value;
    }

    private static KnowledgeItem item(long id, String stableCode, String tagsJson, long pointer) {
        KnowledgeItem value = new KnowledgeItem();
        value.setKnowledgeItemId(id);
        value.setStableCode(stableCode);
        value.setTagsJson(tagsJson);
        value.setCurrentPublishedVersionId(pointer);
        return value;
    }

    private static KnowledgeVersion version(long id, long itemId, int versionNo, String status, String content) {
        KnowledgeVersion value = new KnowledgeVersion();
        value.setKnowledgeVersionId(id);
        value.setKnowledgeItemId(itemId);
        value.setVersionNo(versionNo);
        value.setStatus(status);
        value.setContent(content);
        return value;
    }

    private static VideoTypeRule rule(long id, String code, int versionNo, String industry, String purpose,
                                      int priority, Integer minDuration, Integer maxDuration,
                                      String copyRulesJson, String status) {
        VideoTypeRule value = new VideoTypeRule();
        value.setVideoTypeRuleId(id);
        value.setRuleCode(code);
        value.setVersionNo(versionNo);
        value.setVideoTypeCode("*");
        value.setIndustryCode(industry);
        value.setPurposeCode(purpose);
        value.setMinDurationSeconds(minDuration);
        value.setMaxDurationSeconds(maxDuration);
        value.setRequiredSlotCodesJson("[]");
        value.setPriority(priority);
        value.setCopyRulesJson(copyRulesJson);
        value.setStatus(status);
        return value;
    }
}
