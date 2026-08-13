package org.dromara.aivideo.infra.questionnaire;

import org.dromara.common.core.exception.ServiceException;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 防止模型把较长的知识正文原样返回给创作端。 */
final class KnowledgeOutputGuard {

    private static final int MIN_VERBATIM_LENGTH = 32;

    private KnowledgeOutputGuard() {
    }

    static void rejectVerbatimLeak(String generatedOutput, List<String> knowledgeExcerpts) {
        String normalizedOutput = normalize(generatedOutput);
        if (normalizedOutput.length() < MIN_VERBATIM_LENGTH || knowledgeExcerpts == null
            || knowledgeExcerpts.isEmpty()) {
            return;
        }
        Set<String> outputWindows = windows(normalizedOutput);
        for (String excerpt : knowledgeExcerpts) {
            String normalizedExcerpt = normalize(excerpt);
            if (normalizedExcerpt.length() < MIN_VERBATIM_LENGTH) {
                continue;
            }
            for (int index = 0; index <= normalizedExcerpt.length() - MIN_VERBATIM_LENGTH; index++) {
                if (outputWindows.contains(normalizedExcerpt.substring(index, index + MIN_VERBATIM_LENGTH))) {
                    throw new ServiceException("DeepSeek 输出包含不可披露的知识内容");
                }
            }
        }
    }

    private static Set<String> windows(String value) {
        Set<String> result = new HashSet<>(Math.max(16, value.length()));
        for (int index = 0; index <= value.length() - MIN_VERBATIM_LENGTH; index++) {
            result.add(value.substring(index, index + MIN_VERBATIM_LENGTH));
        }
        return result;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        StringBuilder compact = new StringBuilder(normalized.length());
        normalized.codePoints()
            .filter(codePoint -> !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint))
            .forEach(compact::appendCodePoint);
        return compact.toString();
    }
}
