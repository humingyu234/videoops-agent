import { getIntl, history, request } from '@umijs/max';
import { ApiError } from '../core/errors';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiAdapter,
} from '../core/ruoyiAdapter';
import { authSession, beginLoginRedirect } from '../auth/session';
import {
  DIGITAL_HUMAN_JOB_STAGES,
  DIGITAL_HUMAN_JOB_STATUSES,
  DIGITAL_HUMAN_JOB_TYPES,
  type CreateVideoJobInput,
  type CreateVoiceJobInput,
  type DigitalHumanJob,
  type DigitalHumanJobStage,
  type DigitalHumanJobStatus,
  type DigitalHumanJobType,
} from './types';

const VOICE_JOBS_PATH = '/api/studio/voice-jobs';
const VIDEO_JOBS_PATH = '/api/studio/video-jobs';
const JOBS_PATH = '/api/studio/jobs';

export type DigitalHumanMediaRequest = (
  url: string,
  signal?: AbortSignal,
) => Promise<Blob>;

export interface DigitalHumanApi {
  confirmVoiceJob(jobId: string, signal?: AbortSignal): Promise<DigitalHumanJob>;
  createVideoJob(
    input: CreateVideoJobInput,
    signal?: AbortSignal,
  ): Promise<DigitalHumanJob>;
  createVoiceJob(
    input: CreateVoiceJobInput,
    signal?: AbortSignal,
  ): Promise<DigitalHumanJob>;
  getJob(jobId: string, signal?: AbortSignal): Promise<DigitalHumanJob>;
  getJobMedia(jobId: string, signal?: AbortSignal): Promise<Blob>;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isOneOf<T extends string>(
  value: unknown,
  allowed: readonly T[],
): value is T {
  return typeof value === 'string' && allowed.includes(value as T);
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string';
}

function parseDigitalHumanJob(value: unknown): DigitalHumanJob {
  if (!isRecord(value)) {
    throw new ApiError({ code: 500, msg: '任务响应格式异常，请稍后重试。' });
  }
  const {
    errorMessage,
    jobId,
    jobType,
    outputAvailable,
    parentJobId,
    progress,
    stage,
    status,
    voiceConfirmed,
  } = value;
  if (
    typeof jobId !== 'string' ||
    !jobId ||
    !isNullableString(parentJobId) ||
    !isOneOf<DigitalHumanJobType>(jobType, DIGITAL_HUMAN_JOB_TYPES) ||
    !isOneOf<DigitalHumanJobStatus>(status, DIGITAL_HUMAN_JOB_STATUSES) ||
    !isOneOf<DigitalHumanJobStage>(stage, DIGITAL_HUMAN_JOB_STAGES) ||
    typeof progress !== 'number' ||
    !Number.isFinite(progress) ||
    progress < 0 ||
    progress > 100 ||
    typeof voiceConfirmed !== 'boolean' ||
    typeof outputAvailable !== 'boolean' ||
    !isNullableString(errorMessage)
  ) {
    throw new ApiError({ code: 500, msg: '任务响应格式异常，请稍后重试。' });
  }
  return {
    errorMessage,
    jobId,
    jobType,
    outputAvailable,
    parentJobId,
    progress,
    stage,
    status,
    voiceConfirmed,
  };
}

function jobPath(jobId: string): string {
  return `${JOBS_PATH}/${encodeURIComponent(jobId)}`;
}

export function createDigitalHumanApi(
  adapter: RuoYiAdapter,
  mediaRequest: DigitalHumanMediaRequest,
): DigitalHumanApi {
  return {
    async confirmVoiceJob(jobId, signal) {
      const value = await adapter.request<unknown>(
        `${VOICE_JOBS_PATH}/${encodeURIComponent(jobId)}/confirmation`,
        { method: 'POST', ...(signal ? { signal } : {}) },
      );
      return parseDigitalHumanJob(value);
    },

    async createVideoJob(input, signal) {
      let data: FormData | { voiceJobId: string; portraitId: string };
      if ('portraitId' in input) {
        data = { voiceJobId: input.voiceJobId, portraitId: input.portraitId as string };
      } else {
        const form = new FormData();
        form.set('voiceJobId', input.voiceJobId);
        form.set('portraitImage', input.portraitImage);
        data = form;
      }
      const value = await adapter.request<unknown>(VIDEO_JOBS_PATH, {
        data,
        headers: { 'Idempotency-Key': input.idempotencyKey },
        method: 'POST',
        ...(signal ? { signal } : {}),
      });
      return parseDigitalHumanJob(value);
    },

    async createVoiceJob(input, signal) {
      let data: FormData | { scriptText: string; referenceVoiceId: string };
      if ('referenceVoiceId' in input) {
        data = { scriptText: input.scriptText, referenceVoiceId: input.referenceVoiceId as string };
      } else {
        const form = new FormData();
        form.set('scriptText', input.scriptText);
        form.set('referenceAudio', input.referenceAudio);
        data = form;
      }
      const value = await adapter.request<unknown>(VOICE_JOBS_PATH, {
        data,
        headers: { 'Idempotency-Key': input.idempotencyKey },
        method: 'POST',
        ...(signal ? { signal } : {}),
      });
      return parseDigitalHumanJob(value);
    },

    async getJob(jobId, signal) {
      const value = await adapter.request<unknown>(jobPath(jobId), {
        method: 'GET',
        ...(signal ? { signal } : {}),
      });
      return parseDigitalHumanJob(value);
    },

    getJobMedia(jobId, signal) {
      return mediaRequest(`${jobPath(jobId)}/media`, signal);
    },
  };
}

function getCurrentLanguage(): string {
  try {
    const locale = getIntl().locale;
    if (locale.trim()) return locale;
  } catch {
    // Umi may request data before i18n initialization finishes.
  }
  return typeof navigator === 'undefined' ? 'zh-CN' : navigator.language;
}

function getConfiguredClientId(): string {
  const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
  if (!clientId) {
    throw new Error('创作端登录客户端未配置，请联系管理员。');
  }
  return clientId;
}

const runtimeRequest: HttpRequest = (url, options) =>
  request<unknown>(url, { ...(options ?? {}) });

async function normalizeMediaResponse(response: unknown): Promise<unknown> {
  if (!(response instanceof Blob)) return response;
  const contentType = response.type.split(';', 1)[0]?.trim().toLowerCase();
  if (
    contentType === 'audio/wav' ||
    contentType === 'audio/x-wav' ||
    contentType === 'video/mp4'
  ) {
    return { code: 200, data: response, msg: 'ok' };
  }
  if (contentType === 'application/json' || contentType?.endsWith('+json')) {
    try {
      return JSON.parse(await response.text()) as unknown;
    } catch {
      // Fall through to the provider-neutral response error below.
    }
  }
  return {
    code: 500,
    data: null,
    msg: '媒体响应格式异常，请稍后重试。',
  };
}

const runtimeMediaRequest: HttpRequest = async (url, options) => {
  const response = await request<unknown>(url, {
    ...(options ?? {}),
    responseType: 'blob',
  });
  return normalizeMediaResponse(response);
};

let runtimeApi: DigitalHumanApi | undefined;

function getRuntimeApi(): DigitalHumanApi {
  if (!runtimeApi) {
    const clientId = getConfiguredClientId();
    const onUnauthorized = (session: {
      accessToken: string;
      sessionRevision?: number;
    }) => {
      if (
        session.sessionRevision === undefined ||
        !authSession.clearIfCurrent({
          accessToken: session.accessToken,
          revision: session.sessionRevision,
        })
      ) {
        return;
      }
      if (beginLoginRedirect()) history.replace('/user/login');
    };
    const adapter = createRuoYiAdapter({
      clientId,
      execute: runtimeRequest,
      getAccessToken: authSession.getAccessToken,
      getLanguage: getCurrentLanguage,
      getSessionRevision: authSession.getRevision,
      onUnauthorized,
    });
    const mediaAdapter = createRuoYiAdapter({
      clientId,
      execute: runtimeMediaRequest,
      getAccessToken: authSession.getAccessToken,
      getLanguage: getCurrentLanguage,
      getSessionRevision: authSession.getRevision,
      onUnauthorized,
    });
    runtimeApi = createDigitalHumanApi(
      adapter,
      (url, signal) =>
        mediaAdapter.request<Blob>(url, {
          method: 'GET',
          ...(signal ? { signal } : {}),
        }),
    );
  }
  return runtimeApi;
}

export function createIdempotencyKey(kind: 'video' | 'voice'): string {
  const randomPart =
    typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `dh-${kind}-${randomPart}`;
}

export const digitalHumanApi: DigitalHumanApi = {
  confirmVoiceJob: (jobId, signal) =>
    getRuntimeApi().confirmVoiceJob(jobId, signal),
  createVideoJob: (input, signal) =>
    getRuntimeApi().createVideoJob(input, signal),
  createVoiceJob: (input, signal) =>
    getRuntimeApi().createVoiceJob(input, signal),
  getJob: (jobId, signal) => getRuntimeApi().getJob(jobId, signal),
  getJobMedia: (jobId, signal) => getRuntimeApi().getJobMedia(jobId, signal),
};
