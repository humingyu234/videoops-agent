package org.dromara.aivideo.user.studio.service.impl;

import org.dromara.aivideo.knowledge.dto.KnowledgeContextDTO;
import org.dromara.aivideo.knowledge.dto.KnowledgeContextRequestDTO;
import org.dromara.aivideo.knowledge.service.IKnowledgeContextService;
import org.dromara.aivideo.questionnaire.dto.QuestionnaireAnswerDTO;
import org.dromara.aivideo.script.dto.ScriptGeneratedVersionDTO;
import org.dromara.aivideo.script.dto.ScriptGenerationRequestDTO;
import org.dromara.aivideo.script.service.IScriptModelService;
import org.dromara.aivideo.user.studio.domain.bo.QuestionnaireAnswerBo;
import org.dromara.aivideo.user.studio.domain.bo.ScriptGenerateBo;
import org.dromara.aivideo.user.studio.domain.vo.ScriptGenerateVo;
import org.dromara.aivideo.user.studio.domain.vo.ScriptVersionVo;
import org.dromara.aivideo.user.studio.service.IScriptGenerationService;
import org.dromara.common.core.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

/** 用户端 DeepSeek 文案生成服务实现。 */
@Service
public class ScriptGenerationServiceImpl implements IScriptGenerationService {

    private static final long COPYWRITING_FLOW_VERSION_ID = 2084460032627961859L;

    private final IKnowledgeContextService knowledgeContextService;
    private final Optional<IScriptModelService> scriptModelService;

    public ScriptGenerationServiceImpl(IKnowledgeContextService knowledgeContextService,
                                       Optional<IScriptModelService> scriptModelService) {
        this.knowledgeContextService = Objects.requireNonNull(knowledgeContextService);
        this.scriptModelService = Objects.requireNonNull(scriptModelService);
    }

    @Override
    public ScriptGenerateVo generate(ScriptGenerateBo request) {
        validateAnswerHistory(request.answerHistory());
        IScriptModelService modelService = scriptModelService.orElseThrow(
            () -> new ServiceException("DeepSeek 文案生成服务未配置"));
        KnowledgeContextDTO context = knowledgeContextService.resolve(new KnowledgeContextRequestDTO(
            request.industryCode(), request.purposeCode(), request.durationSeconds(), List.of()));
        List<ScriptGeneratedVersionDTO> generated = modelService.generate(toModelRequest(request, context));
        if (generated.size() != 3) {
            throw new ServiceException("DeepSeek 文案生成结果无效，请重试");
        }
        return new ScriptGenerateVo(
            generated.stream()
                .map(version -> new ScriptVersionVo(
                    version.title(), version.durationSeconds(), version.body()))
                .toList(),
            context.knowledgeVersionIds().stream().map(String::valueOf).toList(),
            context.contentHash(),
            "deepseek");
    }

    private ScriptGenerationRequestDTO toModelRequest(ScriptGenerateBo request,
                                                       KnowledgeContextDTO context) {
        List<String> flowExcerpts = IntStream.range(0, context.knowledgeVersionIds().size())
            .filter(index -> COPYWRITING_FLOW_VERSION_ID == context.knowledgeVersionIds().get(index))
            .mapToObj(index -> compactKnowledge(context.excerpts().get(index)))
            .toList();
        if (flowExcerpts.isEmpty()) {
            throw new ServiceException("文案生成流程知识未配置");
        }
        List<QuestionnaireAnswerDTO> answerHistory = request.answerHistory().stream()
            .map(answer -> new QuestionnaireAnswerDTO(
                answer.questionId(), answer.questionTitle(),
                answer.selectedValues(), answer.selectedLabels()))
            .toList();
        return new ScriptGenerationRequestDTO(
            request.industryCode(), request.purposeCode(), request.durationSeconds(),
            request.demandText(), answerHistory, flowExcerpts,
            context.copyRules().stream().limit(20).toList());
    }

    private static String compactKnowledge(String content) {
        String normalized = content.replace("\r\n", "\n").trim();
        int conversationStart = normalized.indexOf("**User**");
        String rulesOnly = conversationStart >= 0
            ? normalized.substring(0, conversationStart).trim()
            : normalized;
        return rulesOnly.length() <= 16000 ? rulesOnly : rulesOnly.substring(0, 16000);
    }

    private static void validateAnswerHistory(List<QuestionnaireAnswerBo> answerHistory) {
        Set<String> questionIds = new HashSet<>();
        for (QuestionnaireAnswerBo answer : answerHistory) {
            if (answer == null
                || answer.questionId() == null || answer.questionId().isBlank()
                || answer.questionTitle() == null || answer.questionTitle().isBlank()
                || answer.selectedValues().isEmpty() || answer.selectedLabels().isEmpty()
                || answer.selectedValues().size() != answer.selectedLabels().size()
                || answer.selectedValues().stream().anyMatch(ScriptGenerationServiceImpl::isBlank)
                || answer.selectedLabels().stream().anyMatch(ScriptGenerationServiceImpl::isBlank)
                || !questionIds.add(answer.questionId())) {
                throw new ServiceException("问卷回答历史无效");
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
