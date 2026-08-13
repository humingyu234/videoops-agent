import { describe, expect, it, vi } from 'vitest';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createQuestionnaireApi } from './api';

describe('questionnaire api', () => {
  it('posts the demand context to the creator questionnaire endpoint', async () => {
    const request = vi.fn().mockResolvedValue({ questions: [] });
    const api = createQuestionnaireApi({ request } as RuoYiAdapter);
    const input = {
      demandText: '89元家用拖把，面向宝妈做获客口播',
      durationSeconds: 60,
      industryCode: 'ecommerce',
      purposeCode: '获客咨询',
    };

    await api.generate(input);

    expect(request).toHaveBeenCalledWith('/api/studio/questionnaires/generate', {
      data: input,
      method: 'POST',
    });
  });

  it('forwards the abort signal to the request adapter', async () => {
    const request = vi.fn().mockResolvedValue({ questions: [] });
    const api = createQuestionnaireApi({ request } as RuoYiAdapter);
    const controller = new AbortController();
    const input = {
      demandText: '',
      durationSeconds: 60,
      industryCode: 'ecommerce',
      purposeCode: '获客咨询',
    };

    await api.generate(input, { signal: controller.signal });

    expect(request).toHaveBeenCalledWith('/api/studio/questionnaires/generate', {
      data: input,
      method: 'POST',
      signal: controller.signal,
    });
  });
});
