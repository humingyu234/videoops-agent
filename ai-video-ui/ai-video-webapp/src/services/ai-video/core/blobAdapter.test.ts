import { describe, expect, it, vi } from 'vitest';
import { ApiError } from './errors';
import { createBlobAdapter, normalizeBinaryResponse } from './blobAdapter';

describe('blob adapter', () => {
  it('reads an authorized Blob with a single Range through the shared adapter', async () => {
    const request = vi.fn().mockResolvedValue(new Blob(['media'], { type: 'video/mp4' }));
    const blobs = createBlobAdapter({ request });

    await expect(blobs.read('/api/studio/creation-assets/1/content', { range: 'bytes=0-4' })).resolves.toBeInstanceOf(Blob);
    expect(request).toHaveBeenCalledWith('/api/studio/creation-assets/1/content', {
      headers: { Range: 'bytes=0-4' }, method: 'GET', responseType: 'blob',
    });
  });

  it('turns JSON 401, 403, and business responses into RuoYi error envelopes', async () => {
    await expect(normalizeBinaryResponse(new Blob([JSON.stringify({ code: 401, msg: 'expired', data: null })], { type: 'application/json' }))).resolves.toEqual({ code: 401, msg: 'expired', data: null });
    await expect(normalizeBinaryResponse(new Blob([JSON.stringify({ code: 403, msg: 'forbidden', data: null })], { type: 'application/json' }))).resolves.toEqual({ code: 403, msg: 'forbidden', data: null });
    await expect(normalizeBinaryResponse(new Blob([JSON.stringify({ code: 46606, msg: 'asset invalid', data: null })], { type: 'application/json' }))).resolves.toEqual({ code: 46606, msg: 'asset invalid', data: null });
  });

  it('does not reinterpret an adapter authorization error as a playable Blob', async () => {
    const blobs = createBlobAdapter({
      request: vi.fn().mockRejectedValue(new ApiError({ code: 403, msg: 'forbidden' })),
    });

    await expect(blobs.read('/api/studio/creation-assets/1/content')).rejects.toMatchObject({ code: 403 });
  });
});
