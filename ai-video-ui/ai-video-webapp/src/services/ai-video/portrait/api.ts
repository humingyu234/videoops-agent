import { getIntl, history, request } from '@umijs/max';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiAdapter,
} from '../core/ruoyiAdapter';
import { authSession, beginLoginRedirect } from '../auth/session';
import type {
  Portrait,
  PortraitAccessUrl,
  PortraitAsset,
  PortraitInput,
  PortraitPage,
  PortraitUpdateInput,
} from './types';

export interface PortraitApi {
  list(input?: { keyword?: string; availabilityStatus?: string; gender?: string; pageNum?: number; pageSize?: number }): Promise<PortraitPage>;
  detail(portraitId: string): Promise<Portrait>;
  upload(file: File): Promise<PortraitAsset>;
  create(input: PortraitInput): Promise<Portrait>;
  update(portraitId: string, input: PortraitUpdateInput): Promise<Portrait>;
  remove(portraitId: string, expectedRevision: string): Promise<void>;
  accessUrl(portraitId: string): Promise<PortraitAccessUrl>;
}

export function createPortraitApi(adapter: RuoYiAdapter): PortraitApi {
  return {
    list(input = {}) {
      const query = new URLSearchParams();
      Object.entries({ pageNum: 1, pageSize: 20, ...input }).forEach(([key, value]) => {
        if (value !== undefined && value !== '') query.set(key, String(value));
      });
      return adapter.request<PortraitPage>(`/api/portraits?${query.toString()}`, { method: 'GET' });
    },
    detail(portraitId) {
      return adapter.request<Portrait>(`/api/portraits/${encodeURIComponent(portraitId)}`, { method: 'GET' });
    },
    upload(file) {
      const data = new FormData();
      data.append('file', file);
      return adapter.request<PortraitAsset>('/api/assets/uploads/portrait-images', { method: 'POST', data });
    },
    create(input) {
      return adapter.request<Portrait>('/api/portraits', { method: 'POST', data: input });
    },
    update(portraitId, input) {
      return adapter.request<Portrait>(`/api/portraits/${encodeURIComponent(portraitId)}`, { method: 'PUT', data: input });
    },
    async remove(portraitId, expectedRevision) {
      await adapter.request<void>(`/api/portraits/${encodeURIComponent(portraitId)}?expectedRevision=${encodeURIComponent(expectedRevision)}`, { method: 'DELETE' });
    },
    accessUrl(portraitId) {
      return adapter.request<PortraitAccessUrl>(
        `/api/portraits/${encodeURIComponent(portraitId)}/access-url`, { method: 'GET' },
      );
    },
  };
}

const runtimeRequest: HttpRequest = (url, options) => request<unknown>(url, { ...(options ?? {}) });
let runtimeApi: PortraitApi | undefined;

function runtime(): PortraitApi {
  if (!runtimeApi) {
    const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
    if (!clientId) throw new Error('创作端登录客户端未配置，请联系管理员。');
    runtimeApi = createPortraitApi(createRuoYiAdapter({
      clientId,
      execute: runtimeRequest,
      getAccessToken: authSession.getAccessToken,
      getLanguage: () => {
        try { return getIntl().locale || 'zh-CN'; } catch { return 'zh-CN'; }
      },
      getSessionRevision: authSession.getRevision,
      onUnauthorized: (session) => {
        if (session.sessionRevision !== undefined && authSession.clearIfCurrent({
          accessToken: session.accessToken,
          revision: session.sessionRevision,
        }) && beginLoginRedirect()) history.replace('/user/login');
      },
    }));
  }
  return runtimeApi;
}

export const portraitApi: PortraitApi = {
  list: (input) => runtime().list(input),
  detail: (id) => runtime().detail(id),
  upload: (file) => runtime().upload(file),
  create: (input) => runtime().create(input),
  update: (id, input) => runtime().update(id, input),
  remove: (id, revision) => runtime().remove(id, revision),
  accessUrl: (id) => runtime().accessUrl(id),
};
