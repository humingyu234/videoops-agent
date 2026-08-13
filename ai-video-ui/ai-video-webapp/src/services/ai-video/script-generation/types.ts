import type { QuestionnaireAnswerHistory } from '../questionnaire/types';

export interface GenerateScriptInput {
  industryCode: string;
  purposeCode: string;
  durationSeconds: number;
  demandText: string;
  answerHistory: QuestionnaireAnswerHistory[];
}

export interface GeneratedScript {
  title: string;
  durationSeconds: number;
  body: string;
}

export interface GeneratedScripts {
  scripts: GeneratedScript[];
  knowledgeVersionIds: string[];
  knowledgeHash: string;
  modelMode: 'deepseek' | string;
}
