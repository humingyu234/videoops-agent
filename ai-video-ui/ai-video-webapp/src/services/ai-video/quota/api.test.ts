import { describe, expect, it } from 'vitest';
import {
  createRuoYiAdapter,
  type RuoYiRequestOptions,
} from '../core/ruoyiAdapter';
import { createQuotaApi } from './api';

function createHarness(response: unknown) {
  const calls: Array<{ url: string; options?: RuoYiRequestOptions }> = [];
  const adapter = createRuoYiAdapter({
    clientId: 'creator-web',
    execute: async (url, options) => {
      calls.push({ url, options });
      return { code: 200, data: response, msg: 'ok' };
    },
    getAccessToken: () => 'app-token',
    getLanguage: () => 'zh-CN',
  });

  return { adapter, calls };
}

describe('personal quota API', () => {
  it('queries the current account without sending a user id and preserves large string balances', async () => {
    const { adapter, calls } = createHarness({
      quotaUnit: 'ai_text_credit',
      availableBalance: '900719925474099312345',
      lockedBalance: '7',
      usedBalance: '11',
      totalBalance: '900719925474099312352',
    });

    await expect(createQuotaApi(adapter).getPersonalAccount()).resolves.toEqual({
      quotaUnit: 'ai_text_credit',
      availableBalance: '900719925474099312345',
      lockedBalance: '7',
      usedBalance: '11',
      totalBalance: '900719925474099312352',
    });

    expect(calls).toEqual([
      expect.objectContaining({
        url: '/api/quota/account',
        options: expect.objectContaining({ method: 'GET' }),
      }),
    ]);
    expect(calls[0]?.options).not.toHaveProperty('data');
  });

  it('rejects a response that would require JavaScript number coercion', async () => {
    const { adapter } = createHarness({
      quotaUnit: 'ai_text_credit',
      availableBalance: '8640',
      lockedBalance: '0',
      usedBalance: 0,
      totalBalance: '8640',
    });

    await expect(
      createQuotaApi(adapter).getPersonalAccount(),
    ).rejects.toThrow('积分响应格式异常');
  });
});
