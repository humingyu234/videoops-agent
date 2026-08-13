package org.dromara.aivideo.knowledge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.dromara.aivideo.knowledge.service.IKnowledgeContextService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic read service for the lightweight knowledge context.
 */
@Service
public class KnowledgeContextServiceImpl implements IKnowledgeContextService {

    private final KnowledgeItemMapper itemMapper;
    private final KnowledgeVersionMapper versionMapper;
    private final KnowledgeBindingMapper bindingMapper;
    private final VideoTypeRuleMapper ruleMapper;
    private final JsonMapper jsonMapper;

    public KnowledgeContextServiceImpl(KnowledgeItemMapper itemMapper, KnowledgeVersionMapper versionMapper,
                                       KnowledgeBindingMapper bindingMapper, VideoTypeRuleMapper ruleMapper,
                                       JsonMapper jsonMapper) {
        this.itemMapper = Objects.requireNonNull(itemMapper, "knowledge item mapper must not be null");
        this.versionMapper = Objects.requireNonNull(versionMapper, "knowledge version mapper must not be null");
        this.bindingMapper = Objects.requireNonNull(bindingMapper, "knowledge binding mapper must not be null");
        this.ruleMapper = Objects.requireNonNull(ruleMapper, "video type rule mapper must not be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "json mapper must not be null");
    }

    @Override
    public KnowledgeContextDTO resolve(KnowledgeContextRequestDTO request) {
        if (request == null) {
            throw new ServiceException("知识上下文请求不能为空");
        }
        try {
            return resolveChecked(request);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException("知识上下文解析失败");
        }
    }

    private KnowledgeContextDTO resolveChecked(KnowledgeContextRequestDTO request) throws Exception {
        List<KnowledgeBinding> bindings = requireList(bindingMapper.selectList(bindingQuery(request)));
        List<VideoTypeRule> rules = requireList(ruleMapper.selectList(ruleQuery(request)));

        List<KnowledgeBinding> eligibleBindings = bindings.stream()
            .filter(binding -> matchesBinding(binding, request))
            .toList();
        List<BindingCandidate> candidates = loadCandidates(eligibleBindings, request);
        candidates.sort(bindingComparator(request));

        List<Long> versionIds = new ArrayList<>();
        List<String> excerpts = new ArrayList<>();
        Set<Long> seenVersionIds = new HashSet<>();
        for (BindingCandidate candidate : candidates) {
            Long versionId = candidate.version().getKnowledgeVersionId();
            if (seenVersionIds.add(versionId)) {
                versionIds.add(versionId);
                excerpts.add(candidate.content());
            }
        }

        List<String> copyRules = resolveCopyRules(rules, request);
        String hash = contentHash(versionIds, excerpts, copyRules);
        return new KnowledgeContextDTO(versionIds, excerpts, copyRules, hash);
    }

    private List<BindingCandidate> loadCandidates(List<KnowledgeBinding> bindings,
                                                   KnowledgeContextRequestDTO request) {
        if (bindings.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> itemIds = bindings.stream()
            .map(KnowledgeBinding::getKnowledgeItemId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> versionIds = bindings.stream()
            .map(KnowledgeBinding::getKnowledgeVersionId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (itemIds.contains(null) || versionIds.contains(null)) {
            throw new ServiceException("知识绑定数据不完整");
        }

        Map<Long, KnowledgeItem> items = uniqueMap(requireList(itemMapper.selectList(
            new LambdaQueryWrapper<KnowledgeItem>().in(KnowledgeItem::getKnowledgeItemId, itemIds))),
            KnowledgeItem::getKnowledgeItemId);
        Map<Long, KnowledgeVersion> versions = uniqueMap(requireList(versionMapper.selectList(
            new LambdaQueryWrapper<KnowledgeVersion>()
                .in(KnowledgeVersion::getKnowledgeVersionId, versionIds)
                .eq(KnowledgeVersion::getStatus, "published"))), KnowledgeVersion::getKnowledgeVersionId);

        List<BindingCandidate> candidates = new ArrayList<>();
        for (KnowledgeBinding binding : bindings) {
            KnowledgeItem item = items.get(binding.getKnowledgeItemId());
            if (item == null) {
                throw new ServiceException("知识绑定条目不存在");
            }
            if (!Objects.equals(binding.getKnowledgeVersionId(), item.getCurrentPublishedVersionId())) {
                throw new ServiceException("知识绑定未指向条目当前发布版本");
            }
            KnowledgeVersion version = versions.get(binding.getKnowledgeVersionId());
            if (version == null || !"published".equals(version.getStatus())) {
                continue;
            }
            if (!Objects.equals(binding.getKnowledgeItemId(), version.getKnowledgeItemId())) {
                throw new ServiceException("知识绑定版本归属不一致");
            }
            String content = normalizeContent(version.getContent());
            Set<String> tags = new HashSet<>(parseStringArray(item.getTagsJson(), "知识标签"));
            int tagHits = (int) request.tagCodes().stream().filter(tags::contains).count();
            candidates.add(new BindingCandidate(binding, item, version, content, tagHits));
        }
        return candidates;
    }

    private List<String> resolveCopyRules(List<VideoTypeRule> rules, KnowledgeContextRequestDTO request) {
        List<VideoTypeRule> eligibleRules = rules.stream()
            .filter(rule -> matchesRule(rule, request))
            .sorted(ruleComparator(request))
            .toList();
        Set<String> seenRules = new LinkedHashSet<>();
        for (VideoTypeRule rule : eligibleRules) {
            seenRules.addAll(parseStringArray(rule.getCopyRulesJson(), "文案规则"));
        }
        return List.copyOf(seenRules);
    }

    private LambdaQueryWrapper<KnowledgeBinding> bindingQuery(KnowledgeContextRequestDTO request) {
        return new LambdaQueryWrapper<KnowledgeBinding>()
            .eq(KnowledgeBinding::getStatus, "published")
            .and(query -> query.eq(KnowledgeBinding::getIndustryCode, request.industryCode())
                .or().eq(KnowledgeBinding::getIndustryCode, "*"))
            .and(query -> query.eq(KnowledgeBinding::getPurposeCode, request.purposeCode())
                .or().eq(KnowledgeBinding::getPurposeCode, "*"));
    }

    private LambdaQueryWrapper<VideoTypeRule> ruleQuery(KnowledgeContextRequestDTO request) {
        return new LambdaQueryWrapper<VideoTypeRule>()
            .eq(VideoTypeRule::getStatus, "published")
            .and(query -> query.eq(VideoTypeRule::getIndustryCode, request.industryCode())
                .or().eq(VideoTypeRule::getIndustryCode, "*"))
            .and(query -> query.eq(VideoTypeRule::getPurposeCode, request.purposeCode())
                .or().eq(VideoTypeRule::getPurposeCode, "*"));
    }

    private boolean matchesBinding(KnowledgeBinding binding, KnowledgeContextRequestDTO request) {
        return binding != null
            && "published".equals(binding.getStatus())
            && matchesRoute(binding.getIndustryCode(), binding.getPurposeCode(), request)
            && "*".equals(binding.getVideoTypeCode())
            && Boolean.FALSE.equals(binding.getRequiredFlag())
            && "[]".equals(binding.getAngleCodesJson())
            && "{}".equals(binding.getAnglePrioritiesJson())
            && "[]".equals(binding.getRequiredSlotCodesJson())
            && "[]".equals(binding.getAudienceTagCodesJson())
            && "[]".equals(binding.getExclusionConditionsJson())
            && matchesDuration(binding.getMinDurationSeconds(), binding.getMaxDurationSeconds(),
                request.targetDurationSeconds());
    }

    private boolean matchesRule(VideoTypeRule rule, KnowledgeContextRequestDTO request) {
        return rule != null
            && "published".equals(rule.getStatus())
            && matchesRoute(rule.getIndustryCode(), rule.getPurposeCode(), request)
            && "*".equals(rule.getVideoTypeCode())
            && "[]".equals(rule.getRequiredSlotCodesJson())
            && matchesDuration(rule.getMinDurationSeconds(), rule.getMaxDurationSeconds(),
                request.targetDurationSeconds());
    }

    private boolean matchesRoute(String industryCode, String purposeCode, KnowledgeContextRequestDTO request) {
        return (request.industryCode().equals(industryCode) || "*".equals(industryCode))
            && (request.purposeCode().equals(purposeCode) || "*".equals(purposeCode));
    }

    private boolean matchesDuration(Integer minimum, Integer maximum, int target) {
        if (minimum == null || maximum == null) {
            return minimum == null && maximum == null;
        }
        return minimum <= target && target <= maximum;
    }

    private Comparator<BindingCandidate> bindingComparator(KnowledgeContextRequestDTO request) {
        return Comparator.comparingInt((BindingCandidate candidate) -> specificity(
                candidate.binding().getIndustryCode(), candidate.binding().getPurposeCode(), request))
            .thenComparing(Comparator.comparingInt(BindingCandidate::tagHits).reversed())
            .thenComparing(Comparator.comparingInt(
                (BindingCandidate candidate) -> requiredInteger(candidate.binding().getPriority())).reversed())
            .thenComparing(candidate -> requiredString(candidate.item().getStableCode()))
            .thenComparing(Comparator.comparingInt(
                (BindingCandidate candidate) -> requiredInteger(candidate.version().getVersionNo())).reversed())
            .thenComparing(candidate -> requiredLong(candidate.version().getKnowledgeVersionId()))
            .thenComparing(candidate -> requiredLong(candidate.binding().getKnowledgeBindingId()));
    }

    private Comparator<VideoTypeRule> ruleComparator(KnowledgeContextRequestDTO request) {
        return Comparator.comparingInt((VideoTypeRule rule) ->
                specificity(rule.getIndustryCode(), rule.getPurposeCode(), request))
            .thenComparing(Comparator.comparingInt(
                (VideoTypeRule rule) -> requiredInteger(rule.getPriority())).reversed())
            .thenComparing(rule -> requiredString(rule.getRuleCode()))
            .thenComparing(Comparator.comparingInt(
                (VideoTypeRule rule) -> requiredInteger(rule.getVersionNo())).reversed())
            .thenComparing(rule -> requiredLong(rule.getVideoTypeRuleId()));
    }

    private int specificity(String industryCode, String purposeCode, KnowledgeContextRequestDTO request) {
        boolean exactIndustry = request.industryCode().equals(industryCode);
        boolean exactPurpose = request.purposeCode().equals(purposeCode);
        if (exactIndustry && exactPurpose) {
            return 1;
        }
        if (exactIndustry) {
            return 2;
        }
        if (exactPurpose) {
            return 3;
        }
        return 4;
    }

    private List<String> parseStringArray(String json, String label) {
        try {
            String[] values = jsonMapper.readValue(json, String[].class);
            if (values == null) {
                throw new ServiceException(label + "格式损坏");
            }
            List<String> normalized = Arrays.stream(values)
                .map(value -> normalizeArrayValue(value, label))
                .toList();
            return List.copyOf(normalized);
        } catch (ServiceException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ServiceException(label + "格式损坏");
        }
    }

    private String normalizeArrayValue(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new ServiceException(label + "格式损坏");
        }
        return value.trim();
    }

    private String normalizeContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new ServiceException("已发布知识正文不能为空");
        }
        return content.trim();
    }

    private String contentHash(List<Long> versionIds, List<String> excerpts, List<String> copyRules) throws Exception {
        LinkedHashMap<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("knowledgeVersionIds", versionIds);
        canonical.put("excerpts", excerpts);
        canonical.put("copyRules", copyRules);
        byte[] bytes = jsonMapper.writeValueAsBytes(canonical);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private <T> List<T> requireList(List<T> values) {
        if (values == null) {
            throw new ServiceException("知识上下文查询失败");
        }
        return values;
    }

    private <T> Map<Long, T> uniqueMap(List<T> values, Function<T, Long> idExtractor) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T value : values) {
            if (value == null) {
                throw new ServiceException("知识上下文数据不完整");
            }
            Long id = idExtractor.apply(value);
            if (id == null || result.putIfAbsent(id, value) != null) {
                throw new ServiceException("知识上下文数据不完整");
            }
        }
        return result;
    }

    private int requiredInteger(Integer value) {
        if (value == null) {
            throw new ServiceException("知识上下文数据不完整");
        }
        return value;
    }

    private long requiredLong(Long value) {
        if (value == null) {
            throw new ServiceException("知识上下文数据不完整");
        }
        return value;
    }

    private String requiredString(String value) {
        if (value == null) {
            throw new ServiceException("知识上下文数据不完整");
        }
        return value;
    }

    private record BindingCandidate(KnowledgeBinding binding, KnowledgeItem item, KnowledgeVersion version,
                                    String content, int tagHits) {
    }
}
