import { getIntl, history, request } from '@umijs/max';
import { authSession, beginLoginRedirect } from '../auth/session';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiAdapter,
} from '../core/ruoyiAdapter';
import type {
  ScriptVersion,
  UserScriptDetail,
  UserScriptEditInput,
  UserScriptInput,
  UserScriptListQuery,
  UserScriptPage,
  UserScriptSaveResult,
} from './types';

export interface UserScriptApi {
  list(input?: UserScriptListQuery): Promise<UserScriptPage>;
  detail(scriptId: string): Promise<UserScriptDetail>;
  version(scriptId: string, versionId: string): Promise<ScriptVersion>;
  create(input: UserScriptInput): Promise<UserScriptSaveResult>;
  createVersion(scriptId: string, input: UserScriptEditInput): Promise<UserScriptSaveResult>;
  remove(scriptId: string): Promise<void>;
}

const scriptUrl = (scriptId: string) =>
  `/api/studio/scripts/${encodeURIComponent(scriptId)}`;

export function createUserScriptApi(adapter: RuoYiAdapter): UserScriptApi {
  return {
    list(input = {}) {
      const query = new URLSearchParams();
      Object.entries({ pageNum: 1, pageSize: 20, ...input }).forEach(
        ([key, value]) => {
          if (value !== undefined && value !== '') query.set(key, String(value));
        },
      );
      return adapter.request<UserScriptPage>(
        `/api/studio/scripts?${query.toString()}`,
        { method: 'GET' },
      );
    },
    detail(scriptId) {
      return adapter.request<UserScriptDetail>(scriptUrl(scriptId), {
        method: 'GET',
      });
    },
    version(scriptId, versionId) {
      return adapter.request<ScriptVersion>(
        `${scriptUrl(scriptId)}/versions/${encodeURIComponent(versionId)}`,
        { method: 'GET' },
      );
    },
    create(input) {
      return adapter.request<UserScriptSaveResult>('/api/studio/scripts', {
        method: 'POST',
        data: input,
      });
    },
    createVersion(scriptId, input) {
      return adapter.request<UserScriptSaveResult>(
        `${scriptUrl(scriptId)}/versions`,
        { method: 'POST', data: input },
      );
    },
    async remove(scriptId) {
      await adapter.request<void>(scriptUrl(scriptId), { method: 'DELETE' });
    },
  };
}

const runtimeRequest: HttpRequest = (url, options) =>
  request<unknown>(url, { ...(options ?? {}) });
let runtimeApi: UserScriptApi | undefined;

function runtime(): UserScriptApi {
  if (!runtimeApi) {
    const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
    if (!clientId) throw new Error('创作端登录客户端未配置，请联系管理员。');
    runtimeApi = createUserScriptApi(
      createRuoYiAdapter({
        clientId,
        execute: runtimeRequest,
        getAccessToken: authSession.getAccessToken,
        getLanguage: () => {
          try {
            return getIntl().locale || 'zh-CN';
          } catch {
            return 'zh-CN';
          }
        },
        getSessionRevision: authSession.getRevision,
        onUnauthorized: (session) => {
          if (
            session.sessionRevision !== undefined &&
            authSession.clearIfCurrent({
              accessToken: session.accessToken,
              revision: session.sessionRevision,
            }) &&
            beginLoginRedirect()
          ) {
            history.replace('/user/login');
          }
        },
      }),
    );
  }
  return runtimeApi;
}

export const userScriptApi: UserScriptApi = {
  list: (input) => runtime().list(input),
  detail: (id) => runtime().detail(id),
  version: (scriptId, versionId) => runtime().version(scriptId, versionId),
  create: (input) => runtime().create(input),
  createVersion: (scriptId, input) => runtime().createVersion(scriptId, input),
  remove: (id) => runtime().remove(id),
};
