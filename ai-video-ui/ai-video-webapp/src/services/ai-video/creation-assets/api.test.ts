import { describe, expect, it, vi } from 'vitest';
import { createCreationAssetsApi } from './api';

const assetWire = {
  assetId: '90071992547410001', originalName: 'product.png', mimeType: 'image/png', sha256: 'a'.repeat(64),
  assetType: 'image', usageOrigin: 'upload', status: 'ready', sizeBytes: '1024', durationMs: null,
  width: 1080, height: 1920, hasVideoStream: false, hasAudioStream: false, createdAt: '2026-08-08T08:00:00+08:00',
};

describe('creation asset api', () => {
  it('keeps list, upload, detail, and delete paths in one API module', async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ total: 0, rows: [] })
      .mockResolvedValueOnce(assetWire)
      .mockResolvedValueOnce(undefined)
      .mockResolvedValueOnce(new Blob(['image'], { type: 'image/png' }));
    const api = createCreationAssetsApi({ request });

    await api.list({ assetType: 'image', pageNum: 2, pageSize: 20 });
    await api.upload(new File(['image'], 'product.png', { type: 'image/png' }), {
      usageIntent: 'image_overlay', idempotencyKey: 'upload-1',
    });
    await api.delete('90071992547410001');
    await api.content('90071992547410001', { range: 'bytes=0-4' });

    expect(request.mock.calls[0]).toEqual([
      '/api/studio/creation-assets?status=ready&pageNum=2&pageSize=20&assetType=image',
      { method: 'GET' },
    ]);
    expect(request.mock.calls[1][0]).toBe('/api/studio/creation-assets');
    expect(request.mock.calls[1][1]).toMatchObject({ method: 'POST' });
    expect(request.mock.calls[2]).toEqual([
      '/api/studio/creation-assets/90071992547410001', { method: 'DELETE' },
    ]);
    expect(request.mock.calls[3]).toEqual([
      '/api/studio/creation-assets/90071992547410001/content',
      {
        headers: { Range: 'bytes=0-4' },
        method: 'GET',
        responseType: 'blob',
      },
    ]);
  });
});
