import { getRuntimeAppAdapter } from '../auth/api';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import type {
  GeneratedQuestionnaire,
  GenerateQuestionnaireInput,
  GenerateQuestionnaireOptions,
} from './types';

export interface QuestionnaireApi {
  generate(
    input: GenerateQuestionnaireInput,
    options?: GenerateQuestionnaireOptions,
  ): Promise<GeneratedQuestionnaire>;
}

export function createQuestionnaireApi(adapter: RuoYiAdapter): QuestionnaireApi {
  return {
    generate(input, options) {
      return adapter.request<GeneratedQuestionnaire>(
        '/api/studio/questionnaires/generate',
        {
          data: input,
          method: 'POST',
          ...(options?.signal ? { signal: options.signal } : {}),
        },
      );
    },
  };
}

export const questionnaireApi: QuestionnaireApi = {
  generate: (input, options) =>
    createQuestionnaireApi(getRuntimeAppAdapter()).generate(input, options),
};
