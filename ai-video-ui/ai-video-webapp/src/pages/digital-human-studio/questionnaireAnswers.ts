import type { QuestionnaireOption } from '@/services/ai-video/questionnaire/types';

const OTHER_OPTION_VALUES = new Set([
  'other',
  '__other',
  '其他',
  '其它',
]);

export function isQuestionnaireOtherOption(
  option: QuestionnaireOption,
): boolean {
  const label = option.label.trim();
  const value = option.value.trim().toLowerCase();
  return (
    /^(?:其他|其它)(?:$|[\s（(:：])/.test(label) ||
    OTHER_OPTION_VALUES.has(value)
  );
}
