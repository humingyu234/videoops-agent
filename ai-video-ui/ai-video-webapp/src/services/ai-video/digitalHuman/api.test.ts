import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  RuoYiAdapter,
  RuoYiRequestOptions,
} from '../core/ruoyiAdapter';
import { authSession, resetLoginRedirect } from '../auth/session';

const { historyReplace, runtimeRequest } = vi.hoisted(() => {
  process.env.APP_AUTH_CLIENT_ID = 'configured-desktop-web';
  return {
    historyReplace: vi.fn(),
    runtimeRequest: vi.fn(),
  };
});

vi.mock('@umijs/max', () => ({
  getIntl: () => ({ locale: 'zh-CN' }),
  history: { replace: historyReplace },
  request: runtimeRequest,
}));

import { createDigitalHumanApi, digitalHumanApi } from './api';
import type { DigitalHumanJob } from './types';

const voiceJob: DigitalHumanJob = {
  errorMessage: null,
  jobId: 'voice-job-1',
  jobType: 'voice_generate',
  outputAvailable: false,
  parentJobId: null,
  progress: 5,
  stage: 'queued',
  status: 'queued',
  voiceConfirmed: false,
};

const videoJob: DigitalHumanJob = {
  ...voiceJob,
  jobId: 'video-job-1',
  jobType: 'video_generate',
  parentJobId: voiceJob.jobId,
  stage: 'video_submitted',
};

function createHarness(outcomes: unknown[]) {
  const calls: Array<{ url: string; options?: RuoYiRequestOptions }> = [];
  const adapter: RuoYiAdapter = {
    async request<T>(url: string, options?: RuoYiRequestOptions): Promise<T> {
      calls.push({ url, options });
      const outcome = outcomes.shift();
      if (outcome === undefined) throw new Error('未配置测试响应');
      return outcome as T;
    },
  };
  const mediaRequest = vi.fn(async () => new Blob(['media']));

  return {
    api: createDigitalHumanApi(adapter, mediaRequest),
    calls,
    mediaRequest,
  };
}

describe('digital human API', () => {
  beforeEach(() => {
    authSession.clear();
    resetLoginRedirect();
    historyReplace.mockReset();
    runtimeRequest.mockReset();
  });

  it('creates voice and video jobs with frozen multipart fields and idempotency headers', async () => {
    const harness = createHarness([voiceJob, videoJob]);
    const referenceAudio = new File(['voice'], 'reference.wav', {
      type: 'audio/wav',
    });
    const portraitImage = new File(['portrait'], 'portrait.png', {
      type: 'image/png',
    });

    await expect(
      harness.api.createVoiceJob({
        idempotencyKey: 'voice-key-1',
        referenceAudio,
        scriptText: '已确认的口播正文',
      }),
    ).resolves.toEqual(voiceJob);
    await expect(
      harness.api.createVideoJob({
        idempotencyKey: 'video-key-1',
        portraitImage,
        voiceJobId: voiceJob.jobId,
      }),
    ).resolves.toEqual(videoJob);

    expect(harness.calls[0]).toMatchObject({
      url: '/api/studio/voice-jobs',
      options: {
        headers: { 'Idempotency-Key': 'voice-key-1' },
        method: 'POST',
      },
    });
    const voiceForm = harness.calls[0]?.options?.data;
    expect(voiceForm).toBeInstanceOf(FormData);
    expect((voiceForm as FormData).get('scriptText')).toBe('已确认的口播正文');
    expect((voiceForm as FormData).get('referenceAudio')).toBe(referenceAudio);

    expect(harness.calls[1]).toMatchObject({
      url: '/api/studio/video-jobs',
      options: {
        headers: { 'Idempotency-Key': 'video-key-1' },
        method: 'POST',
      },
    });
    const videoForm = harness.calls[1]?.options?.data;
    expect(videoForm).toBeInstanceOf(FormData);
    expect((videoForm as FormData).get('voiceJobId')).toBe(voiceJob.jobId);
    expect((videoForm as FormData).get('portraitImage')).toBe(portraitImage);
  });

  it('uses frozen confirmation, query, and raw media endpoints', async () => {
    const confirmedJob = { ...voiceJob, voiceConfirmed: true };
    const harness = createHarness([confirmedJob, confirmedJob]);

    await expect(harness.api.confirmVoiceJob(voiceJob.jobId)).resolves.toEqual(
      confirmedJob,
    );
    await expect(harness.api.getJob(voiceJob.jobId)).resolves.toEqual(
      confirmedJob,
    );
    await expect(harness.api.getJobMedia(voiceJob.jobId)).resolves.toBeInstanceOf(
      Blob,
    );

    expect(harness.calls).toEqual([
      {
        url: '/api/studio/voice-jobs/voice-job-1/confirmation',
        options: { method: 'POST' },
      },
      {
        url: '/api/studio/jobs/voice-job-1',
        options: { method: 'GET' },
      },
    ]);
    expect(harness.mediaRequest).toHaveBeenCalledWith(
      '/api/studio/jobs/voice-job-1/media',
      undefined,
    );
  });

  it.each([
    ['audio/wav', 'voice'],
    ['audio/x-wav', 'voice-alias'],
    ['video/mp4', 'video'],
  ])('returns a real %s media response', async (contentType, content) => {
    authSession.save({ accessToken: 'valid-media-token', persistent: false });
    const media = new Blob([content], { type: contentType });
    runtimeRequest.mockResolvedValueOnce(media);

    await expect(digitalHumanApi.getJobMedia('job-media')).resolves.toBe(media);
  });

  it.each(['audio/mpeg', 'audio/mp4', 'video/webm'])(
    'rejects the unsupported %s media response',
    async (contentType) => {
      authSession.save({
        accessToken: 'invalid-media-token',
        persistent: false,
      });
      runtimeRequest.mockResolvedValueOnce(
        new Blob(['unexpected-media'], { type: contentType }),
      );

      await expect(
        digitalHumanApi.getJobMedia('job-invalid-media'),
      ).rejects.toMatchObject({
        code: 500,
        msg: '媒体响应格式异常，请稍后重试。',
        name: 'ApiError',
      });
    },
  );

  it('rejects a JSON R.fail blob instead of treating it as media', async () => {
    authSession.save({ accessToken: 'forbidden-media-token', persistent: false });
    runtimeRequest.mockResolvedValueOnce(
      new Blob(
        [JSON.stringify({ code: 403, data: null, msg: '没有媒体读取权限' })],
        { type: 'application/json;charset=UTF-8' },
      ),
    );

    await expect(digitalHumanApi.getJobMedia('job-forbidden')).rejects.toMatchObject(
      {
        code: 403,
        msg: '没有媒体读取权限',
        name: 'ApiError',
      },
    );
    expect(authSession.getAccessToken()).toBe('forbidden-media-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('applies the shared session invalidation semantics to an HTTP 401', async () => {
    authSession.save({ accessToken: 'expired-media-token', persistent: false });
    runtimeRequest.mockRejectedValueOnce(
      Object.assign(new Error('登录已失效'), { response: { status: 401 } }),
    );

    await expect(digitalHumanApi.getJobMedia('job-expired')).rejects.toMatchObject(
      {
        code: 401,
        name: 'ApiError',
        status: 401,
      },
    );
    expect(authSession.getAccessToken()).toBeUndefined();
    expect(historyReplace).toHaveBeenCalledTimes(1);
    expect(historyReplace).toHaveBeenCalledWith('/user/login');
  });

  it('normalizes an HTTP 5xx media failure', async () => {
    authSession.save({ accessToken: 'failed-media-token', persistent: false });
    runtimeRequest.mockRejectedValueOnce(
      Object.assign(new Error('媒体服务不可用'), { response: { status: 503 } }),
    );

    await expect(digitalHumanApi.getJobMedia('job-unavailable')).rejects.toMatchObject(
      {
        code: 503,
        msg: '媒体服务不可用',
        name: 'ApiError',
        status: 503,
      },
    );
  });

  it('preserves AbortError so callers can silently stop media work', async () => {
    authSession.save({ accessToken: 'cancelled-media-token', persistent: false });
    const abortError = new DOMException('Aborted', 'AbortError');
    runtimeRequest.mockRejectedValueOnce(abortError);

    await expect(digitalHumanApi.getJobMedia('job-cancelled')).rejects.toBe(
      abortError,
    );
    expect(authSession.getAccessToken()).toBe('cancelled-media-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });
});
