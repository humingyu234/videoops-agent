import { describe, expect, it, vi } from 'vitest';
import { createRuoYiAdapter } from './ruoyiAdapter';

describe('RuoYi adapter transfer options', () => {
  it('forwards typed upload progress while retaining managed authentication headers', async () => {
    const execute = vi.fn().mockResolvedValue({ code: 200, msg: 'ok', data: { uploaded: true } });
    const onUploadProgress = vi.fn();
    const adapter = createRuoYiAdapter({
      clientId: 'desktop-web', execute, getAccessToken: () => 'token', getLanguage: () => 'zh-CN',
    });

    await expect(adapter.request('/api/studio/creation-assets', {
      method: 'POST', data: new FormData(), onUploadProgress,
    })).resolves.toEqual({ uploaded: true });
    expect(execute).toHaveBeenCalledWith('/api/studio/creation-assets', expect.objectContaining({
      headers: expect.objectContaining({ Authorization: 'Bearer token', clientid: 'desktop-web' }),
      onUploadProgress,
    }));
  });

  it('turns a JSON Blob error envelope into an ApiError in the limited Blob response mode', async () => {
    const execute = vi.fn().mockResolvedValue(
      new Blob([JSON.stringify({ code: 403, msg: 'forbidden', data: null })], {
        type: 'application/json',
      }),
    );
    const adapter = createRuoYiAdapter({
      clientId: 'desktop-web',
      execute,
      getAccessToken: () => 'token',
      getLanguage: () => 'zh-CN',
    });

    await expect(
      adapter.request('/api/studio/creation-assets/1/content', {
        method: 'GET',
        responseType: 'blob',
      }),
    ).rejects.toMatchObject({ code: 403, msg: 'forbidden' });
    expect(execute).toHaveBeenCalledWith(
      '/api/studio/creation-assets/1/content',
      expect.objectContaining({ responseType: 'blob', skipErrorHandler: true }),
    );
  });
});
