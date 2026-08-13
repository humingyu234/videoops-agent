import { beforeEach, describe, expect, it, vi } from 'vitest';

const { historyReplace, messageError } = vi.hoisted(() => ({
  historyReplace: vi.fn(),
  messageError: vi.fn(),
}));

vi.mock('antd', () => ({
  message: { error: messageError },
}));

vi.mock('@umijs/max', () => ({
  getIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage: string }) =>
      defaultMessage,
  }),
  history: { replace: historyReplace },
}));

import { errorConfig } from './requestErrorConfig';
import {
  authSession,
  resetLoginRedirect,
  subscribeToAuthSessionClear,
} from './services/ai-video/auth/session';
import { ApiError } from './services/ai-video/core/errors';

class HttpStatusError extends Error {
  readonly response: { status: number };

  constructor(status: number) {
    super(`HTTP ${status}`);
    this.response = { status };
  }
}

const errorHandler = errorConfig.errorConfig?.errorHandler;

if (!errorHandler) {
  throw new Error('Expected a configured request error handler');
}

function authenticatedRequest(token: string) {
  return {
    headers: { Authorization: `Bearer ${token}` },
    requiresAuth: true,
  } as never;
}

const unauthenticatedRequest = { requiresAuth: false } as never;

describe('requestErrorConfig', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authSession.clear();
    resetLoginRedirect();
  });

  it('does not show a toast for an aborted request', () => {
    const abortError = new Error('request cancelled');
    abortError.name = 'AbortError';

    errorHandler(abortError, {});

    expect(messageError).not.toHaveBeenCalled();
  });

  it('does not clear authSession or redirect for repeated direct HTTP 401 errors', () => {
    authSession.save({
      accessToken: 'expired-app-token',
      persistent: true,
    });

    errorHandler(new HttpStatusError(401), authenticatedRequest('expired-app-token'));
    errorHandler(new HttpStatusError(401), authenticatedRequest('expired-app-token'));

    expect(authSession.getAccessToken()).toBe('expired-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it.each([46129, 46131])(
    'does not clear the app session for a direct authenticated business %i response',
    (code) => {
      authSession.save({
        accessToken: 'invalidated-app-token',
        persistent: false,
      });

      errorHandler(
        new ApiError({ code, msg: 'session invalidated' }),
        authenticatedRequest('invalidated-app-token'),
      );
      errorHandler(
        new ApiError({ code, msg: 'session invalidated' }),
        authenticatedRequest('invalidated-app-token'),
      );

      expect(authSession.getAccessToken()).toBe('invalidated-app-token');
      expect(historyReplace).not.toHaveBeenCalled();
    },
  );

  it.each([46129, 46131])(
    'does not clear an existing app session for an unauthenticated business %i response',
    (code) => {
      authSession.save({
        accessToken: 'active-app-token',
        persistent: false,
      });

      errorHandler(
        new ApiError({ code, msg: 'login rejected' }),
        { requiresAuth: false } as never,
      );

      expect(authSession.getAccessToken()).toBe('active-app-token');
      expect(historyReplace).not.toHaveBeenCalled();
    },
  );

  it.each([401, 46129, 46131])(
    'does not clear an existing app session for an unauthenticated business %i response',
    (code) => {
      authSession.save({
        accessToken: 'active-app-token',
        persistent: false,
      });

      errorHandler(
        new ApiError({ code, msg: 'login rejected' }),
        unauthenticatedRequest,
      );

      expect(authSession.getAccessToken()).toBe('active-app-token');
      expect(historyReplace).not.toHaveBeenCalled();
    },
  );

  it('does not clear an existing app session for an unauthenticated HTTP 401 response', () => {
    authSession.save({
      accessToken: 'active-app-token',
      persistent: false,
    });

    errorHandler(
      new HttpStatusError(401),
      unauthenticatedRequest,
    );

    expect(authSession.getAccessToken()).toBe('active-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('does not let a delayed authenticated HTTP 401 response clear a newer app token', () => {
    authSession.save({
      accessToken: 'new-app-token',
      persistent: false,
    });

    errorHandler(
      new HttpStatusError(401),
      authenticatedRequest('old-app-token'),
    );

    expect(authSession.getAccessToken()).toBe('new-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('does not let a delayed HTTP 401 clear a replacement session that reuses the same token value', () => {
    authSession.save({
      accessToken: 'reused-app-token',
      persistent: false,
    });
    const delayedRequest = authenticatedRequest('reused-app-token');

    authSession.save({
      accessToken: 'reused-app-token',
      persistent: false,
    });

    errorHandler(new HttpStatusError(401), delayedRequest);

    expect(authSession.getAccessToken()).toBe('reused-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('does not notify identity listeners for a direct HTTP 401 response', () => {
    authSession.save({
      accessToken: 'expired-app-token',
      persistent: false,
    });
    const observedTokens: Array<string | undefined> = [];
    const unsubscribe = subscribeToAuthSessionClear(() => {
      observedTokens.push(authSession.getAccessToken());
    });

    try {
      errorHandler(
        new HttpStatusError(401),
        authenticatedRequest('expired-app-token'),
      );
    } finally {
      unsubscribe();
    }

    expect(observedTokens).toEqual([]);
    expect(authSession.getAccessToken()).toBe('expired-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('keeps a token-backed 403 identity state when a direct response returns HTTP 401', () => {
    authSession.save({
      accessToken: 'valid-app-token',
      persistent: false,
    });
    const observedTokens: Array<string | undefined> = [];
    const unsubscribe = subscribeToAuthSessionClear(() => {
      observedTokens.push(authSession.getAccessToken());
    });

    try {
      expect(() => errorHandler(new HttpStatusError(403), {})).toThrow(ApiError);
      expect(authSession.getAccessToken()).toBe('valid-app-token');

      errorHandler(
        new HttpStatusError(401),
        authenticatedRequest('valid-app-token'),
      );
    } finally {
      unsubscribe();
    }

    expect(observedTokens).toEqual([]);
    expect(authSession.getAccessToken()).toBe('valid-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('throws ApiError for HTTP 403 while retaining the session and avoiding login redirect', () => {
    authSession.save({
      accessToken: 'valid-app-token',
      persistent: false,
    });

    expect(() => errorHandler(new HttpStatusError(403), {})).toThrow(ApiError);
    expect(() => errorHandler(new HttpStatusError(403), {})).toThrow(
      expect.objectContaining({ code: 403 }),
    );
    expect(authSession.getAccessToken()).toBe('valid-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('rethrows when skipErrorHandler is set', () => {
    const requestError = new Error('caller handles this');

    expect(() => errorHandler(requestError, { skipErrorHandler: true })).toThrow(
      requestError,
    );
    expect(messageError).not.toHaveBeenCalled();
  });

  it('shows the offline message while the browser is offline', () => {
    const navigatorPrototype = Object.getPrototypeOf(navigator) as object;
    const originalOnLine = Object.getOwnPropertyDescriptor(
      navigatorPrototype,
      'onLine',
    );

    Object.defineProperty(navigatorPrototype, 'onLine', {
      configurable: true,
      get: () => false,
    });

    try {
      errorHandler(new Error('network unavailable'), {});
    } finally {
      if (originalOnLine) {
        Object.defineProperty(navigatorPrototype, 'onLine', originalOnLine);
      } else {
        Reflect.deleteProperty(navigatorPrototype, 'onLine');
      }
    }

    expect(messageError).toHaveBeenCalledWith(
      '网络不可用，请检查网络连接后重试。',
    );
  });

  it('shows a status-specific message for ordinary HTTP errors', () => {
    errorHandler(new HttpStatusError(500), {});

    expect(messageError).toHaveBeenCalledWith('请求失败（500）');
  });
});
