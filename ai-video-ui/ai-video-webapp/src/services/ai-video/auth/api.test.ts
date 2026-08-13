import { beforeEach, describe, expect, it, vi } from 'vitest';
import { errorConfig } from '@/requestErrorConfig';
import type { RuoYiResponse } from '../core/types';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiRequestOptions,
} from '../core/ruoyiAdapter';

const { antdMessage, antdNotification, historyReplace, runtimeRequest } =
  vi.hoisted(() => {
    process.env.APP_AUTH_CLIENT_ID = 'configured-desktop-web';
    return {
      antdMessage: { error: vi.fn() },
      antdNotification: { open: vi.fn() },
      historyReplace: vi.fn(),
      runtimeRequest: vi.fn(),
    };
  });

vi.mock('antd', () => ({
  message: antdMessage,
  notification: antdNotification,
}));

vi.mock('@umijs/max', () => ({
  getIntl: () => ({ locale: 'en-US' }),
  history: { replace: historyReplace },
  request: runtimeRequest,
}));

import { authApi, createAuthApi } from './api';
import { authSession, resetLoginRedirect } from './session';
import type {
  CodeLoginRequest,
  LoginRequest,
  MiniProgramLoginRequest,
  PasswordResetRequest,
  SecuritySession,
  SocialLoginRequest,
  VerificationChallenge,
  VerificationCodeRequest,
} from './types';

type ExpectedSecuritySession = {
  clientId: string;
  current: boolean;
  deviceName: string;
  id: string;
  lastActiveAt: string;
};

type Equal<Left, Right> =
  (<Value>() => Value extends Left ? 1 : 2) extends
  <Value>() => Value extends Right ? 1 : 2
    ? (<Value>() => Value extends Right ? 1 : 2) extends
      <Value>() => Value extends Left ? 1 : 2
      ? true
      : false
    : false;

type Expect<Condition extends true> = Condition;

type _SecuritySessionMatchesApiContract = Expect<
  Equal<SecuritySession, ExpectedSecuritySession>
>;

type ExpectedVerificationCodeRequest = {
  channel: 'EMAIL' | 'PHONE';
  scenario: 'LOGIN' | 'PASSWORD_RECOVERY';
  target: string;
};

type ExpectedVerificationChallenge = {
  challenge_id: string;
  expires_in: number;
  masked_target: string;
};

type ExpectedPasswordResetRequest = {
  challengeId: string;
  newPassword: string;
  verificationCode: string;
};

type ExpectedCodeLoginRequest = {
  challengeId: string;
  verificationCode: string;
};

type ExpectedSocialLoginRequest = {
  authorizationCode: string;
  provider: string;
  state: string;
};

type ExpectedMiniProgramLoginRequest = {
  authorizationCode: string;
};

type _VerificationCodeRequestMatchesApiContract = Expect<
  Equal<VerificationCodeRequest, ExpectedVerificationCodeRequest>
>;

type _VerificationChallengeMatchesApiContract = Expect<
  Equal<VerificationChallenge, ExpectedVerificationChallenge>
>;

type _PasswordResetRequestMatchesApiContract = Expect<
  Equal<PasswordResetRequest, ExpectedPasswordResetRequest>
>;

type _CodeLoginRequestMatchesApiContract = Expect<
  Equal<CodeLoginRequest, ExpectedCodeLoginRequest>
>;

type _SocialLoginRequestMatchesApiContract = Expect<
  Equal<SocialLoginRequest, ExpectedSocialLoginRequest>
>;

type _MiniProgramLoginRequestMatchesApiContract = Expect<
  Equal<MiniProgramLoginRequest, ExpectedMiniProgramLoginRequest>
>;

class HttpStatusError extends Error {
  readonly response: { status: number };

  constructor(status: number) {
    super(`HTTP ${status}`);
    this.response = { status };
  }
}

function createHarness(
  outcomes: Array<RuoYiResponse<unknown> | Error>,
) {
  const calls: Array<{
    url: string;
    options?: RuoYiRequestOptions;
  }> = [];
  const clearSession = vi.fn();
  const redirectToLogin = vi.fn();

  const execute: HttpRequest = async (url, options) => {
    calls.push({ url, options });
    const outcome = outcomes.shift();
    if (outcome === undefined) {
      throw new Error('No fake response was configured');
    }
    if (outcome instanceof Error) {
      throw outcome;
    }
    return outcome;
  };

  const adapter = createRuoYiAdapter({
    clientId: 'desktop-web',
    clearSession,
    execute,
    getAccessToken: () => 'app-access-token',
    getLanguage: () => 'en-US',
    redirectToLogin,
  });

  return {
    api: createAuthApi(adapter),
    calls,
    clearSession,
    redirectToLogin,
    adapter,
  };
}

describe('user auth API adapter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    authSession.clear();
    resetLoginRedirect();
  });

  it('does not toast a cancelled request through the centralized error handler', () => {
    const errorHandler = errorConfig.errorConfig?.errorHandler;
    if (!errorHandler) {
      throw new Error('Expected a configured request error handler');
    }

    const abortError = new Error('cancelled');
    abortError.name = 'AbortError';
    errorHandler(abortError, {});

    expect(antdMessage.error).not.toHaveBeenCalled();
  });

  it('uses a Chinese fallback when an API response does not match the RuoYi contract', async () => {
    const harness = createHarness([{} as RuoYiResponse<unknown>]);

    await expect(
      harness.adapter.request('/api/auth/me', { requiresAuth: false }),
    ).rejects.toMatchObject({
      code: 500,
      msg: '服务响应格式异常，请稍后重试。',
    });
  });

  it('keeps the shared session for repeated direct global HTTP 401 errors', () => {
    const errorHandler = errorConfig.errorConfig?.errorHandler;
    if (!errorHandler) {
      throw new Error('Expected a configured request error handler');
    }

    localStorage.setItem('ai-video.app.access-token', 'expired-app-token');
    const authenticatedRequest = {
      headers: { Authorization: 'Bearer expired-app-token' },
      requiresAuth: true,
    } as never;
    errorHandler(new HttpStatusError(401), authenticatedRequest);
    errorHandler(new HttpStatusError(401), authenticatedRequest);

    expect(authSession.getAccessToken()).toBe('expired-app-token');
    expect(historyReplace).not.toHaveBeenCalled();
  });

  it('throws ApiError without redirecting for a global HTTP 403 error', () => {
    const errorHandler = errorConfig.errorConfig?.errorHandler;
    if (!errorHandler) {
      throw new Error('Expected a configured request error handler');
    }

    authSession.save({
      accessToken: 'valid-app-token',
      persistent: false,
    });

    expect.assertions(3);
    try {
      errorHandler(new HttpStatusError(403), {});
    } catch (error) {
      expect(error).toMatchObject({ name: 'ApiError', code: 403 });
    }
    expect(historyReplace).not.toHaveBeenCalled();
    expect(authSession.getAccessToken()).toBe('valid-app-token');
  });

  it('keeps the selected persistence policy in authSession', () => {
    authSession.save({
      accessToken: 'temporary-app-token',
      persistent: false,
    });

    expect(authSession.getAccessToken()).toBe('temporary-app-token');
    expect(localStorage.getItem('ai-video.app.access-token')).toBeNull();
    expect(sessionStorage.getItem('ai-video.app.access-token')).toBe(
      'temporary-app-token',
    );

    authSession.save({
      accessToken: 'persistent-app-token',
      persistent: true,
    });

    expect(authSession.getAccessToken()).toBe('persistent-app-token');
    expect(localStorage.getItem('ai-video.app.access-token')).toBe(
      'persistent-app-token',
    );
    expect(sessionStorage.getItem('ai-video.app.access-token')).toBeNull();
  });

  it('adds exactly one app token, client id, and current language for me', async () => {
    const harness = createHarness([
      {
        code: 200,
        msg: 'ok',
        data: { id: 'user_001', displayName: 'Creator' },
      },
    ]);

    await expect(harness.api.me()).resolves.toMatchObject({
      id: 'user_001',
    });

    expect(harness.calls).toHaveLength(1);
    expect(harness.calls[0]).toMatchObject({
      url: '/api/auth/me',
      options: {
        method: 'GET',
        headers: {
          Authorization: 'Bearer app-access-token',
          clientid: 'desktop-web',
          'content-language': 'en-US',
        },
      },
    });

    const headers = harness.calls[0]?.options?.headers ?? {};
    expect(
      Object.keys(headers).filter(
        (header) => header.toLowerCase() === 'authorization',
      ),
    ).toHaveLength(1);
    expect(
      Object.keys(headers).filter(
        (header) => header.toLowerCase() === 'clientid',
      ),
    ).toHaveLength(1);
  });

  it('uses exactly the session fields emitted by the API', async () => {
    const session = {
      clientId: 'creator-web',
      current: true,
      deviceName: 'web',
      id: '9d4cf756-5a8b-424d-86e6-ae4a75ffad8d',
      lastActiveAt: '2026-07-30T10:15:00',
    } satisfies SecuritySession;
    const harness = createHarness([
      {
        code: 200,
        msg: 'ok',
        data: [session],
      },
    ]);

    await expect(harness.api.sessions()).resolves.toEqual([session]);

    expect(harness.calls).toEqual([
      expect.objectContaining({
        url: '/api/auth/sessions',
        options: expect.objectContaining({ method: 'GET' }),
      }),
    ]);
  });

  it('uses the public password recovery wire contract without an app access token', async () => {
    const verificationRequest = {
      channel: 'PHONE',
      scenario: 'PASSWORD_RECOVERY',
      target: '13812345678',
    } satisfies VerificationCodeRequest;
    const resetRequest = {
      challengeId: 'opaque-recovery-challenge',
      newPassword: 'NewPassword1',
      verificationCode: '123456',
    } satisfies PasswordResetRequest;
    const verificationChallenge = {
      challenge_id: 'opaque-recovery-challenge',
      expires_in: 600,
      masked_target: '138****5678',
    } satisfies VerificationChallenge;
    const harness = createHarness([
      { code: 200, msg: 'ok', data: verificationChallenge },
      { code: 200, msg: 'ok', data: null },
    ]);

    await expect(
      harness.api.requestVerificationCode(verificationRequest),
    ).resolves.toEqual(verificationChallenge);
    await expect(harness.api.resetPassword(resetRequest)).resolves.toBeUndefined();

    expect(harness.calls).toEqual([
      expect.objectContaining({
        url: '/api/auth/verification-codes',
        options: expect.objectContaining({
          data: verificationRequest,
          headers: {
            clientid: 'desktop-web',
            'content-language': 'en-US',
          },
          method: 'POST',
        }),
      }),
      expect.objectContaining({
        url: '/api/auth/password-resets',
        options: expect.objectContaining({
          data: resetRequest,
          headers: {
            clientid: 'desktop-web',
            'content-language': 'en-US',
          },
          method: 'POST',
        }),
      }),
    ]);

    for (const call of harness.calls) {
      expect(call.options?.headers).not.toHaveProperty('Authorization');
    }
  });

  it('uses all non-password public login contracts without an app access token', async () => {
    const smsRequest = {
      challengeId: 'sms-login-challenge',
      verificationCode: '123456',
    } satisfies CodeLoginRequest;
    const emailRequest = {
      challengeId: 'email-login-challenge',
      verificationCode: '123456',
    } satisfies CodeLoginRequest;
    const socialRequest = {
      authorizationCode: 'social-authorization-code',
      provider: 'wechat',
      state: 'verified-state',
    } satisfies SocialLoginRequest;
    const miniProgramRequest = {
      authorizationCode: 'mini-program-authorization-code',
    } satisfies MiniProgramLoginRequest;
    const loginResult = {
      access_token: 'new-app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    };
    const harness = createHarness([
      { code: 200, msg: 'ok', data: loginResult },
      { code: 200, msg: 'ok', data: loginResult },
      { code: 200, msg: 'ok', data: loginResult },
      { code: 200, msg: 'ok', data: loginResult },
    ]);
    await expect(harness.api.smsLogin(smsRequest)).resolves.toEqual(loginResult);
    await expect(harness.api.emailLogin(emailRequest)).resolves.toEqual(loginResult);
    await expect(harness.api.socialLogin(socialRequest)).resolves.toEqual(loginResult);
    await expect(harness.api.miniProgramLogin(miniProgramRequest)).resolves.toEqual(loginResult);

    expect(harness.calls).toEqual([
      expect.objectContaining({
        url: '/api/auth/sms-logins',
        options: expect.objectContaining({ data: smsRequest, method: 'POST' }),
      }),
      expect.objectContaining({
        url: '/api/auth/email-logins',
        options: expect.objectContaining({ data: emailRequest, method: 'POST' }),
      }),
      expect.objectContaining({
        url: '/api/auth/social-logins',
        options: expect.objectContaining({ data: socialRequest, method: 'POST' }),
      }),
      expect.objectContaining({
        url: '/api/auth/mini-program-logins',
        options: expect.objectContaining({ data: miniProgramRequest, method: 'POST' }),
      }),
    ]);

    for (const call of harness.calls) {
      expect(call.options?.headers).not.toHaveProperty('Authorization');
    }
  });

  it('rejects caller-provided authorization, clientid, and content-language headers', async () => {
    const harness = createHarness([]);

    await expect(
      harness.adapter.request('/api/auth/me', {
        headers: { Authorization: 'Bearer caller-token' },
      }),
    ).rejects.toThrow('Authorization');
    await expect(
      harness.adapter.request('/api/auth/me', {
        headers: { clientid: 'another-client' },
      }),
    ).rejects.toThrow('clientid');
    await expect(
      harness.adapter.request('/api/auth/me', {
        headers: { 'Content-Language': 'fr-FR' },
      }),
    ).rejects.toThrow('Content-Language');

    expect(harness.calls).toHaveLength(0);
  });

  it('clears the local session even when server logout fails', async () => {
    const harness = createHarness([new Error('logout request failed')]);
    authSession.save({
      accessToken: 'active-app-token',
      persistent: false,
    });

    await expect(harness.api.logout()).rejects.toMatchObject({ code: 500 });

    expect(authSession.getAccessToken()).toBeUndefined();
  });

  it('uses the frozen password login fields and deliberately does not expose registration', async () => {
    const loginRequest: LoginRequest = {
      identifier: 'creator',
      password: 'Password1!',
    };
    const harness = createHarness([
      {
        code: 200,
        msg: 'ok',
        data: {
          access_token: 'new-app-access-token',
          client_id: 'desktop-web',
          expire_in: 7200,
        },
      },
    ]);

    await expect(
      harness.api.login(loginRequest),
    ).resolves.toEqual({
      access_token: 'new-app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    expect('register' in harness.api).toBe(false);
    expect(authSession.getAccessToken()).toBeUndefined();

    expect(harness.calls[0]).toMatchObject({
      url: '/api/auth/login',
      options: {
        data: loginRequest,
        method: 'POST',
        headers: {
          clientid: 'desktop-web',
          'content-language': 'en-US',
        },
      },
    });
    expect(harness.calls[0]?.options?.headers).not.toHaveProperty(
      'Authorization',
    );
  });

  it('takes the app client id from centralized APP_AUTH_CLIENT_ID configuration', async () => {
    runtimeRequest.mockResolvedValueOnce({
      code: 200,
      msg: 'ok',
      data: { id: 'user_003', displayName: 'Configured creator' },
    });

    await expect(authApi.me()).resolves.toMatchObject({ id: 'user_003' });

    expect(runtimeRequest).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({
        headers: expect.objectContaining({
          clientid: 'configured-desktop-web',
        }),
      }),
    );
  });

  it('allows a renewed session to trigger a later one-time 401 redirect', async () => {
    authSession.save({
      accessToken: 'expired-app-token',
      persistent: false,
    });
    runtimeRequest
      .mockResolvedValueOnce({ code: 401, msg: 'expired', data: null })
      .mockResolvedValueOnce({ code: 401, msg: 'expired', data: null });

    await expect(authApi.me()).rejects.toMatchObject({ code: 401 });
    expect(historyReplace).toHaveBeenCalledTimes(1);

    authSession.save({
      accessToken: 'renewed-app-token',
      persistent: false,
    });

    await expect(authApi.me()).rejects.toMatchObject({ code: 401 });
    expect(historyReplace).toHaveBeenCalledTimes(2);
  });

  it.each([46129, 46131])(
    'clears the runtime app session and redirects only once for repeated business %i responses',
    async (code) => {
      authSession.save({
        accessToken: 'invalidated-app-token',
        persistent: false,
      });
      runtimeRequest
        .mockResolvedValueOnce({ code, msg: 'session invalidated', data: null })
        .mockResolvedValueOnce({ code, msg: 'session invalidated', data: null });

      await expect(authApi.me()).rejects.toMatchObject({ code });
      await expect(authApi.me()).rejects.toMatchObject({ code });

      expect(authSession.getAccessToken()).toBeUndefined();
      expect(historyReplace).toHaveBeenCalledTimes(1);
      expect(historyReplace).toHaveBeenCalledWith('/user/login');
    },
  );

  it.each([401, 46131])(
    'does not let a delayed %i response revoke a newer runtime session',
    async (code) => {
      authSession.save({
        accessToken: 'old-app-token',
        persistent: false,
      });
      let resolveOldResponse: ((response: unknown) => void) | undefined;
      runtimeRequest.mockImplementationOnce(
        () => new Promise<unknown>((resolve) => {
          resolveOldResponse = resolve;
        }),
      );

      const oldRequest = authApi.me();
      authSession.save({
        accessToken: 'new-app-token',
        persistent: false,
      });
      if (!resolveOldResponse) {
        throw new Error('Expected the old request to be pending');
      }
      resolveOldResponse({ code, msg: 'old session invalidated', data: null });

      await expect(oldRequest).rejects.toMatchObject({ code });
      expect(authSession.getAccessToken()).toBe('new-app-token');
      expect(historyReplace).not.toHaveBeenCalled();
    },
  );

  it.each([401, 46129, 46131])(
    'does not let a delayed %i response revoke a replacement runtime session with the same token value',
    async (code) => {
      authSession.save({
        accessToken: 'reused-app-token',
        persistent: false,
      });
      let resolveOldResponse: ((response: unknown) => void) | undefined;
      runtimeRequest.mockImplementationOnce(
        () => new Promise<unknown>((resolve) => {
          resolveOldResponse = resolve;
        }),
      );

      const oldRequest = authApi.me();
      authSession.save({
        accessToken: 'reused-app-token',
        persistent: false,
      });
      if (!resolveOldResponse) {
        throw new Error('Expected the old request to be pending');
      }
      resolveOldResponse({ code, msg: 'old session invalidated', data: null });

      await expect(oldRequest).rejects.toMatchObject({ code });
      expect(authSession.getAccessToken()).toBe('reused-app-token');
      expect(historyReplace).not.toHaveBeenCalled();
    },
  );

  it('clears the session and redirects to login only once for repeated business 401 responses', async () => {
    const harness = createHarness([
      { code: 401, msg: 'expired', data: null },
      { code: 401, msg: 'expired', data: null },
    ]);

    await expect(harness.api.me()).rejects.toMatchObject({ code: 401 });
    await expect(harness.api.me()).rejects.toMatchObject({ code: 401 });

    expect(harness.clearSession).toHaveBeenCalledTimes(1);
    expect(harness.redirectToLogin).toHaveBeenCalledTimes(1);
    expect(harness.redirectToLogin).toHaveBeenCalledWith('/user/login');
  });

  it('clears the session and redirects to login only once for repeated HTTP 401 responses', async () => {
    const harness = createHarness([
      new HttpStatusError(401),
      new HttpStatusError(401),
    ]);

    await expect(harness.api.me()).rejects.toMatchObject({ code: 401 });
    await expect(harness.api.me()).rejects.toMatchObject({ code: 401 });

    expect(harness.clearSession).toHaveBeenCalledTimes(1);
    expect(harness.redirectToLogin).toHaveBeenCalledTimes(1);
    expect(harness.redirectToLogin).toHaveBeenCalledWith('/user/login');
  });

  it.each([46129, 46131])(
    'clears authenticated adapter requests and redirects only once for repeated business %i responses',
    async (code) => {
      const harness = createHarness([
        { code, msg: 'session invalidated', data: null },
        { code, msg: 'session invalidated', data: null },
      ]);

      await expect(harness.api.me()).rejects.toMatchObject({ code });
      await expect(harness.api.me()).rejects.toMatchObject({ code });

      expect(harness.clearSession).toHaveBeenCalledTimes(1);
      expect(harness.redirectToLogin).toHaveBeenCalledTimes(1);
      expect(harness.redirectToLogin).toHaveBeenCalledWith('/user/login');
    },
  );

  it.each([401, 46129, 46131])(
    'does not invalidate an existing session when an unauthenticated login request returns business %i',
    async (code) => {
      const harness = createHarness([
        { code, msg: 'login rejected', data: null },
      ]);

      await expect(
        harness.api.login({
          identifier: 'creator@example.com',
          password: 'correct-password',
        }),
      ).rejects.toMatchObject({ code });

      expect(harness.clearSession).not.toHaveBeenCalled();
      expect(harness.redirectToLogin).not.toHaveBeenCalled();
    },
  );

  it('throws an ApiError without redirecting for a 403 response', async () => {
    const harness = createHarness([
      { code: 403, msg: 'forbidden', data: null },
    ]);

    await expect(harness.api.me()).rejects.toMatchObject({
      name: 'ApiError',
      code: 403,
    });

    expect(harness.clearSession).not.toHaveBeenCalled();
    expect(harness.redirectToLogin).not.toHaveBeenCalled();
  });

  it('uses a Chinese message when the app login client is not configured', async () => {
    const configuredClientId = process.env.APP_AUTH_CLIENT_ID;
    delete process.env.APP_AUTH_CLIENT_ID;
    vi.resetModules();

    try {
      const { authApi: unconfiguredAuthApi } = await import('./api');

      expect(() => unconfiguredAuthApi.me()).toThrow(
        '创作端登录客户端未配置，请联系管理员。',
      );
    } finally {
      process.env.APP_AUTH_CLIENT_ID = configuredClientId;
    }
  });
});
