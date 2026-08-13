import { describe, expect, it, vi } from 'vitest';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { createWorkflowUploadsApi } from './api';

describe('workflow uploads API', () => {
  it('creates, transfers, and completes an upload session through the public endpoints', async () => {
    const adapter: RuoYiAdapter = { request: vi.fn()
      .mockResolvedValueOnce({ uploadId: '11', singlePutUrl: '/api/assets/uploads/11/content', requiredHeaders: {}, status: 'created' })
      .mockResolvedValueOnce({ uploadId: '11', status: 'transferred' })
      .mockResolvedValueOnce({ uploadId: '11', assetId: '31', assetStatus: 'ready', status: 'completed' }) };
    const api = createWorkflowUploadsApi(adapter);
    const file = new File(['image'], 'source.png', { type: 'image/png' });

    await api.create({ templateId: '101', schemaHash: `sha256:${'a'.repeat(64)}`, inputKey: 'source', file, idempotencyKey: 'key-1' });
    await api.transfer('/api/assets/uploads/11/content', file);
    await api.complete('11');

    expect(adapter.request).toHaveBeenNthCalledWith(1, '/api/assets/uploads', expect.objectContaining({
      method: 'POST', data: expect.objectContaining({ purpose: 'workflow_input', templateId: '101', inputKey: 'source' }),
    }));
    expect(adapter.request).toHaveBeenNthCalledWith(2, '/api/assets/uploads/11/content', expect.objectContaining({
      method: 'PUT', data: file, headers: { 'Content-Type': 'image/png' },
    }));
    expect(adapter.request).toHaveBeenNthCalledWith(3, '/api/assets/uploads/11/complete', { method: 'POST' });
  });
});
