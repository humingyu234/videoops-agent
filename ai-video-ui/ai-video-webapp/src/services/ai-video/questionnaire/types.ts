export interface QuestionnaireOption {
  label: string;
  value: string;
}

export interface QuestionnaireQuestion {
  id: string;
  title: string;
  description: string;
  required: boolean;
  options?: QuestionnaireOption[];
}

export interface QuestionnaireAnswerHistory {
  questionId: string;
  questionTitle: string;
  selectedValues: string[];
  selectedLabels: string[];
}

export interface GenerateQuestionnaireInput {
  industryCode: string;
  purposeCode: string;
  durationSeconds: number;
  demandText: string;
  answeredSlots?: string[];
  answerHistory?: QuestionnaireAnswerHistory[];
}

export interface GenerateQuestionnaireOptions {
  signal?: AbortSignal;
}

export interface GeneratedQuestionnaire {
  questions: QuestionnaireQuestion[];
  knowledgeVersionIds: string[];
  knowledgeHash: string;
  modelMode: 'deepseek' | 'knowledge-fallback' | string;
}
