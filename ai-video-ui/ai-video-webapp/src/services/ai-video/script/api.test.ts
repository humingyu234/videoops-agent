import { describe, expect, it, vi } from 'vitest';
import { createUserScriptApi } from './api';

describe('user script api', () => {
  it('centralizes all six user script endpoints and keeps ids as strings', async () => {
    const request = vi.fn().mockResolvedValue({ rows: [], total: 0 });
    const api = createUserScriptApi({ request });
    const input = { displayTitle: '夏季新品', scriptText: '正文', idempotencyKey: 'intent-1' };

    await api.list({ keyword: '新品', pageNum: 2, pageSize: 20 });
    await api.create(input);
    await api.detail('1765400000000000001');
    await api.version('1765400000000000001', '1765400000000000002');
    await api.createVersion('1765400000000000001', {
      ...input,
      parentVersionId: '1765400000000000002',
      expectedScriptRevision: '3',
    });
    await api.remove('1765400000000000001');

    expect(request).toHaveBeenCalledWith(
      '/api/studio/scripts?pageNum=2&pageSize=20&keyword=%E6%96%B0%E5%93%81',
      { method: 'GET' },
    );
    expect(request).toHaveBeenCalledWith('/api/studio/scripts', { method: 'POST', data: input });
    expect(request).toHaveBeenCalledWith('/api/studio/scripts/1765400000000000001', { method: 'GET' });
    expect(request).toHaveBeenCalledWith(
      '/api/studio/scripts/1765400000000000001/versions/1765400000000000002',
      { method: 'GET' },
    );
    expect(request).toHaveBeenCalledWith(
      '/api/studio/scripts/1765400000000000001/versions',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(request).toHaveBeenCalledWith('/api/studio/scripts/1765400000000000001', { method: 'DELETE' });
  });
});
