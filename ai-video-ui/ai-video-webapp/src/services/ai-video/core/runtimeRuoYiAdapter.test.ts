import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createBlobAdapter } from './blobAdapter';

const {
  mockBeginLoginRedirect,
  mockGetAccessToken,
  mockGetIntl,
  mockGetRevision,
  mockHistoryReplace,
  mockRequest,
} = vi.hoisted(() => ({
  mockBeginLoginRedirect: vi.fn(),
  mockGetAccessToken: vi.fn(),
  mockGetIntl: vi.fn(),
  mockGetRevision: vi.fn(),
  mockHistoryReplace: vi.fn(),
  mockRequest: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  getIntl: mockGetIntl,
  history: { replace: mockHistoryReplace },
  request: mockRequest,
}));

vi.mock('../auth/session', () => ({
  authSession: {
    clearIfCurrent: vi.fn(),
    getAccessToken: mockGetAccessToken,
    getRevision: mockGetRevision,
  },
  beginLoginRedirect: mockBeginLoginRedirect,
}));

describe('runtime RuoYi Blob transport', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.clearAllMocks();
    vi.stubEnv('APP_AUTH_CLIENT_ID', 'desktop-web');
    mockGetAccessToken.mockReturnValue('access-token');
    mockGetIntl.mockReturnValue({ locale: 'zh-CN' });
    mockGetRevision.mockReturnValue(1);
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('passes Blob mode and Range through @umijs/max, then returns playable media', async () => {
    const media = new Blob(['video'], { type: 'video/mp4' });
    mockRequest.mockResolvedValue(media);
    const { getRuntimeRuoYiAdapter } = await import('./runtimeRuoYiAdapter');

    await expect(
      createBlobAdapter(getRuntimeRuoYiAdapter()).read(
        '/api/studio/creation-assets/1/content',
        { range: 'bytes=0-4' },
      ),
    ).resolves.toBe(media);
    expect(mockRequest).toHaveBeenCalledWith(
      '/api/studio/creation-assets/1/content',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer access-token',
          Range: 'bytes=0-4',
          clientid: 'desktop-web',
        }),
        method: 'GET',
        responseType: 'blob',
      }),
    );
  });

  it('turns a JSON Blob 403 into an ApiError instead of media', async () => {
    mockRequest.mockResolvedValue(
      new Blob([JSON.stringify({ code: 403, msg: 'forbidden', data: null })], {
        type: 'application/json',
      }),
    );
    const { getRuntimeRuoYiAdapter } = await import('./runtimeRuoYiAdapter');

    await expect(
      createBlobAdapter(getRuntimeRuoYiAdapter()).read(
        '/api/studio/creation-assets/1/content',
      ),
    ).rejects.toMatchObject({ code: 403, msg: 'forbidden' });
  });
});
