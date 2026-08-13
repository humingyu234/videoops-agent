import {
  ApiError,
  getErrorMessage,
  getHttpStatus,
  isAbortError,
} from './errors';
import { normalizeBinaryResponse } from './blobAdapter';
import type { RuoYiResponse } from './types';

const LOGIN_PATH = '/user/login';
const SUCCESS_CODE = 200;
const SESSION_INVALIDATION_CODES = new Set([401, 46129, 46131]);
const MANAGED_HEADERS = new Set([
  'authorization',
  'clientid',
  'content-language',
]);

export type HttpMethod = 'DELETE' | 'GET' | 'PATCH' | 'POST' | 'PUT';

export type TransferProgress = {
  loaded: number;
  total?: number;
};

export interface RuoYiRequestOptions {
  data?: unknown;
  headers?: Record<string, string>;
  method?: HttpMethod;
  onUploadProgress?: (progress: TransferProgress) => void;
  responseType?: 'blob';
  requiresAuth?: boolean;
  signal?: AbortSignal;
  skipErrorHandler?: boolean;
}

export type HttpRequest = (
  url: string,
  options?: RuoYiRequestOptions,
) => Promise<unknown>;

export interface RuoYiAdapter {
  request<T>(url: string, options?: RuoYiRequestOptions): Promise<T>;
}

export type InvalidatedAppSession = {
  accessToken: string;
  sessionRevision?: number;
};

export function isSessionInvalidationCode(code: number): boolean {
  return SESSION_INVALIDATION_CODES.has(code);
}

type RuoYiAdapterDependencies = {
  clearSession?: () => void;
  clientId: string;
  execute: HttpRequest;
  getAccessToken: () => string | undefined;
  getLanguage: () => string;
  getSessionRevision?: () => number;
  onUnauthorized?: (session: InvalidatedAppSession) => void;
  redirectToLogin?: (path: string) => void;
};

function isRuoYiResponse(value: unknown): value is RuoYiResponse<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false;
  }

  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.code === 'number' &&
    typeof candidate.msg === 'string' &&
    Object.hasOwn(candidate, 'data')
  );
}

function assertNoManagedHeaders(headers: Record<string, string>): void {
  for (const header of Object.keys(headers)) {
    if (MANAGED_HEADERS.has(header.toLowerCase())) {
      throw new Error(`${header} 由 AI 视频请求适配器统一管理`);
    }
  }
}

function normalizeLanguage(language: string): string {
  return language.trim() || 'zh-CN';
}

export function createRuoYiAdapter({
  clearSession,
  clientId,
  execute,
  getAccessToken,
  getLanguage,
  getSessionRevision,
  onUnauthorized,
  redirectToLogin,
}: RuoYiAdapterDependencies): RuoYiAdapter {
  const normalizedClientId = clientId.trim();
  if (!normalizedClientId) {
    throw new Error('创作端客户端标识不能为空');
  }

  const invalidatedSessions = new Set<string>();

  const handleUnauthorized = (
    accessToken: string | undefined,
    sessionRevision: number | undefined,
  ) => {
    if (
      !accessToken ||
      getAccessToken()?.trim() !== accessToken ||
      (getSessionRevision && getSessionRevision() !== sessionRevision)
    ) {
      return;
    }

    const sessionKey = `${sessionRevision ?? 'token'}:${accessToken}`;
    if (invalidatedSessions.has(sessionKey)) {
      return;
    }
    invalidatedSessions.add(sessionKey);

    if (onUnauthorized) {
      onUnauthorized({ accessToken, sessionRevision });
      return;
    }

    if (!clearSession || !redirectToLogin) {
      throw new Error('未配置登录失效处理逻辑');
    }

    clearSession();
    redirectToLogin(LOGIN_PATH);
  };

  const throwApiError = (
    code: number,
    msg: string,
    data?: unknown,
    status?: number,
    invalidateSession = false,
    accessToken?: string,
    sessionRevision?: number,
  ): never => {
    if (invalidateSession && isSessionInvalidationCode(code)) {
      handleUnauthorized(accessToken, sessionRevision);
    }

    throw new ApiError({ code, msg, data, status });
  };

  return {
    async request<T>(
      url: string,
      options: RuoYiRequestOptions = {},
    ): Promise<T> {
      const {
        headers = {},
        requiresAuth = true,
        skipErrorHandler: _skipErrorHandler,
        ...requestOptions
      } = options;
      assertNoManagedHeaders(headers);

      const managedHeaders: Record<string, string> = {
        ...headers,
        clientid: normalizedClientId,
        'content-language': normalizeLanguage(getLanguage()),
      };
      const accessToken = getAccessToken()?.trim();
      const sessionRevision = requiresAuth
        ? getSessionRevision?.()
        : undefined;
      if (requiresAuth && accessToken) {
        managedHeaders.Authorization = `Bearer ${accessToken}`;
      }

      try {
        const response = await execute(url, {
          ...requestOptions,
          headers: managedHeaders,
          skipErrorHandler: true,
        });
        const normalizedResponse = options.responseType === 'blob'
          ? await normalizeBinaryResponse(response)
          : response;

        if (!isRuoYiResponse(normalizedResponse)) {
          return throwApiError(
            500,
            '服务响应格式异常，请稍后重试。',
            undefined,
            undefined,
            requiresAuth,
            accessToken,
            sessionRevision,
          );
        }

        if (normalizedResponse.code !== SUCCESS_CODE) {
          return throwApiError(
            normalizedResponse.code,
            normalizedResponse.msg,
            normalizedResponse.data,
            undefined,
            requiresAuth,
            accessToken,
            sessionRevision,
          );
        }

        return normalizedResponse.data as T;
      } catch (error) {
        if (error instanceof ApiError || isAbortError(error)) {
          throw error;
        }

        const status = getHttpStatus(error);
        if (status === 401 || status === 403) {
          return throwApiError(
            status,
            getErrorMessage(error, status === 401 ? '未登录或登录已失效。' : '权限不足。'),
            undefined,
            status,
            requiresAuth,
            accessToken,
            sessionRevision,
          );
        }

        return throwApiError(
          status ?? 500,
          getErrorMessage(error, '请求失败。'),
          undefined,
          status,
          requiresAuth,
          accessToken,
          sessionRevision,
        );
      }
    },
  };
}
