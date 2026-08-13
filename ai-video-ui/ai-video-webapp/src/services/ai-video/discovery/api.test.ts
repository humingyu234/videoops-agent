import { describe, expect, it, vi } from 'vitest';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createDiscoveryApi } from './api';

const HASH = `sha256:${'a'.repeat(64)}`;

function createAdapter(payload: unknown): RuoYiAdapter {
  return { request: vi.fn().mockResolvedValue(payload) };
}

describe('discovery API', () => {
  it('exposes only the four phase-one reads and calls their exact paths', async () => {
    const adapter = createAdapter(undefined);
    const request = vi.mocked(adapter.request);
    request
      .mockResolvedValueOnce({
        banners: [],
        categories: [],
        channels: [],
        recommendations: [],
        tags: [],
      })
      .mockResolvedValueOnce({ rows: [], total: 0 })
      .mockResolvedValueOnce({
        templateId: '101',
        title: '口播模板',
        summary: '快速生成口播视频',
        channel: 'video_template',
        category: { categoryCode: '11', label: '营销' },
        tags: [],
        cover: null,
        enabledAt: '2026-08-11T09:30:00',
        description: '模板详情',
        cases: [],
        requiredInputs: [],
      })
      .mockResolvedValueOnce({
        templateId: '101',
        schemaVersion: 'workflow-form-1',
        schemaHash: HASH,
        fields: [],
        billingPolicy: { mode: 'free' },
      });
    const api = createDiscoveryApi(adapter);

    expect(Object.keys(api).sort()).toEqual([
      'getCreationConfig',
      'getHome',
      'getTemplate',
      'getTemplates',
    ]);
    await api.getHome();
    await api.getTemplates({
      pageNum: 2,
      pageSize: 10,
      channel: 'video_template',
      categoryCode: '11',
      tagCodes: '21,22',
      keyword: '口播',
      sort: 'latest',
    });
    await api.getTemplate('101');
    await api.getCreationConfig('101');

    expect(request).toHaveBeenNthCalledWith(1, '/api/discovery/home');
    expect(request).toHaveBeenNthCalledWith(
      2,
      '/api/discovery/templates?pageNum=2&pageSize=10&channel=video_template&categoryCode=11&tagCodes=21%2C22&keyword=%E5%8F%A3%E6%92%AD&sort=latest',
    );
    expect(request).toHaveBeenNthCalledWith(
      3,
      '/api/discovery/templates/101',
    );
    expect(request).toHaveBeenNthCalledWith(
      4,
      '/api/discovery/templates/101/creation-config',
    );
  });

  it('rejects an invalid template id before issuing a request', async () => {
    const adapter = createAdapter(undefined);
    const api = createDiscoveryApi(adapter);

    await expect(api.getTemplate('../secret')).rejects.toThrow(
      'templateId must be a decimal id',
    );
    expect(adapter.request).not.toHaveBeenCalled();
  });
});
