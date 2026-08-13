import { getIntl, history, request } from '@umijs/max';
import { authSession, beginLoginRedirect } from '../auth/session';
import { createRuoYiAdapter, type HttpRequest, type RuoYiAdapter } from '../core/ruoyiAdapter';
import type { Voice, VoiceAccessUrl, VoiceMetadataInput, VoicePage } from './types';

export interface VoiceApi {
  list(input?: { keyword?: string; voiceType?: string; transcriptionStatus?: string; pageNum?: number; pageSize?: number }): Promise<VoicePage>;
  detail(voiceId: string): Promise<Voice>;
  delete(voiceId: string): Promise<void>;
  upload(file: File, metadata: VoiceMetadataInput): Promise<Voice>;
  accessUrl(voiceId: string): Promise<VoiceAccessUrl>;
  updateTranscript(voiceId: string, input: { transcriptText: string; expectedRevision: string }): Promise<Voice>;
  retry(voiceId: string, expectedRevision: string): Promise<Voice>;
  start(voiceId: string, expectedRevision: string): Promise<Voice>;
  resync(voiceId: string, expectedRevision: string): Promise<Voice>;
}

function matchesAscii(bytes: Uint8Array, offset: number, value: string): boolean {
  return Array.from(value).every((character, index) => bytes[offset + index] === character.charCodeAt(0));
}

function isRiffWave(bytes: Uint8Array): boolean {
  return bytes.length >= 12 && matchesAscii(bytes, 0, 'RIFF') && matchesAscii(bytes, 8, 'WAVE');
}

function replaceFileExtension(fileName: string, extension: string): string {
  const dotIndex = fileName.lastIndexOf('.');
  const baseName = dotIndex > 0 ? fileName.slice(0, dotIndex) : fileName || 'voice';
  return `${baseName}.${extension}`;
}

export async function normalizeVoiceUploadFile(file: File): Promise<File> {
  const header = new Uint8Array(await file.slice(0, 12).arrayBuffer());
  if (!isRiffWave(header)) return file;
  const canonicalName = replaceFileExtension(file.name, 'wav');
  if (file.name.toLowerCase() === canonicalName.toLowerCase() && file.type.toLowerCase() === 'audio/wav') {
    return file;
  }
  return new File([file], canonicalName, {
    type: 'audio/wav',
    lastModified: file.lastModified,
  });
}

export function createVoiceApi(adapter: RuoYiAdapter): VoiceApi {
  return {
    list(input = {}) {
      const query = new URLSearchParams();
      Object.entries({ pageNum: 1, pageSize: 20, ...input }).forEach(([key, value]) => {
        if (value !== undefined && value !== '') query.set(key, String(value));
      });
      return adapter.request<VoicePage>(`/api/voices?${query.toString()}`, { method: 'GET' });
    },
    detail: (id) => adapter.request<Voice>(`/api/voices/${encodeURIComponent(id)}`, { method: 'GET' }),
    delete: (id) => adapter.request<void>(`/api/voices/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    async upload(file, metadata) {
      const normalizedFile = await normalizeVoiceUploadFile(file);
      const data = new FormData();
      data.append('file', normalizedFile);
      data.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }));
      return adapter.request<Voice>('/api/voices', { method: 'POST', data });
    },
    accessUrl: (id) => adapter.request<VoiceAccessUrl>(`/api/voices/${encodeURIComponent(id)}/access-url`, { method: 'GET' }),
    updateTranscript: (id, input) => adapter.request<Voice>(`/api/voices/${encodeURIComponent(id)}/transcript`, { method: 'PUT', data: input }),
    retry: (id, expectedRevision) => adapter.request<Voice>(`/api/voices/${encodeURIComponent(id)}/transcription/retry`, {
      method: 'POST', data: { expectedRevision },
    }),
    start: (id, expectedRevision) => adapter.request<Voice>(`/api/voices/${encodeURIComponent(id)}/transcription/start`, {
      method: 'POST', data: { expectedRevision },
    }),
    resync: (id, expectedRevision) => adapter.request<Voice>(`/api/voices/${encodeURIComponent(id)}/transcription/resync`, {
      method: 'POST', data: { expectedRevision },
    }),
  };
}

const runtimeRequest: HttpRequest = (url, options) => request<unknown>(url, { ...(options ?? {}) });
let runtimeApi: VoiceApi | undefined;

function runtime(): VoiceApi {
  if (!runtimeApi) {
    const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
    if (!clientId) throw new Error('创作端登录客户端未配置，请联系管理员。');
    runtimeApi = createVoiceApi(createRuoYiAdapter({
      clientId,
      execute: runtimeRequest,
      getAccessToken: authSession.getAccessToken,
      getLanguage: () => { try { return getIntl().locale || 'zh-CN'; } catch { return 'zh-CN'; } },
      getSessionRevision: authSession.getRevision,
      onUnauthorized: (session) => {
        if (session.sessionRevision !== undefined && authSession.clearIfCurrent({
          accessToken: session.accessToken, revision: session.sessionRevision,
        }) && beginLoginRedirect()) history.replace('/user/login');
      },
    }));
  }
  return runtimeApi;
}

export const voiceApi: VoiceApi = {
  list: (input) => runtime().list(input),
  detail: (id) => runtime().detail(id),
  delete: (id) => runtime().delete(id),
  upload: (file, metadata) => runtime().upload(file, metadata),
  accessUrl: (id) => runtime().accessUrl(id),
  updateTranscript: (id, input) => runtime().updateTranscript(id, input),
  retry: (id, revision) => runtime().retry(id, revision),
  start: (id, revision) => runtime().start(id, revision),
  resync: (id, revision) => runtime().resync(id, revision),
};
