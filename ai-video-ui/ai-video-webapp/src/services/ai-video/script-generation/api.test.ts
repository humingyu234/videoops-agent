import { describe, expect, it, vi } from 'vitest';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createScriptGenerationApi } from './api';

describe('script generation api', () => {
  it('posts the complete questionnaire context to the studio script endpoint', async () => {
    const request = vi.fn().mockResolvedValue({
      knowledgeHash: 'knowledge-hash',
      knowledgeVersionIds: ['101'],
      modelMode: 'deepseek',
      scripts: [],
    });
    const api = createScriptGenerationApi({ request } as unknown as RuoYiAdapter);

    await api.generate({
      answerHistory: [
        {
          questionId: 'target-customer',
          questionTitle: '目标客户是谁？',
          selectedLabels: ['企业采购负责人'],
          selectedValues: ['enterprise-buyer'],
        },
      ],
      demandText: '面向企业采购的课程',
      durationSeconds: 60,
      industryCode: 'education',
      purposeCode: '课程讲解',
    });

    expect(request).toHaveBeenCalledWith('/api/studio/scripts/generate', {
      data: {
        answerHistory: [
          {
            questionId: 'target-customer',
            questionTitle: '目标客户是谁？',
            selectedLabels: ['企业采购负责人'],
            selectedValues: ['enterprise-buyer'],
          },
        ],
        demandText: '面向企业采购的课程',
        durationSeconds: 60,
        industryCode: 'education',
        purposeCode: '课程讲解',
      },
      method: 'POST',
    });
  });
});
