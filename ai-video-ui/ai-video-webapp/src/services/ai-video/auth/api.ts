import { getIntl, history, request } from '@umijs/max';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiAdapter,
} from '../core/ruoyiAdapter';
import {
  authSession,
  beginLoginRedirect,
} from './session';
import type {
  AuthUser,
  ChangePasswordRequest,
  CodeLoginRequest,
  LoginRequest,
  LoginResult,
  MiniProgramLoginRequest,
  PasswordResetRequest,
  SecuritySession,
  SocialLoginRequest,
  VerificationChallenge,
  VerificationCodeRequest,
} from './types';

export interface AuthApi {
  changePassword(input: ChangePasswordRequest): Promise<void>;
  emailLogin(input: CodeLoginRequest): Promise<LoginResult>;
  login(input: LoginRequest): Promise<LoginResult>;
  logout(): Promise<void>;
  me(): Promise<AuthUser>;
  miniProgramLogin(input: MiniProgramLoginRequest): Promise<LoginResult>;
  requestVerificationCode(
    input: VerificationCodeRequest,
  ): Promise<VerificationChallenge>;
  resetPassword(input: PasswordResetRequest): Promise<void>;
  revokeSession(sessionId: string): Promise<void>;
  sessions(): Promise<SecuritySession[]>;
  smsLogin(input: CodeLoginRequest): Promise<LoginResult>;
  socialLogin(input: SocialLoginRequest): Promise<LoginResult>;
}

function getCurrentLanguage(): string {
  try {
    const locale = getIntl().locale;
    if (locale.trim()) {
      return locale;
    }
  } catch {
    // The runtime can request data before Umi finishes initializing i18n.
  }

  return typeof navigator === 'undefined' ? 'zh-CN' : navigator.language;
}

const runtimeRequest: HttpRequest = (url, options) =>
  request<unknown>(url, { ...(options ?? {}) });

function getConfiguredAppAuthClientId(): string {
  const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
  if (!clientId) {
    throw new Error('创作端登录客户端未配置，请联系管理员。');
  }
  return clientId;
}

export function createAuthApi(adapter: RuoYiAdapter): AuthApi {
  return {
    async changePassword(input) {
      await adapter.request<void>('/api/auth/password', {
        data: input,
        method: 'PUT',
      });
    },

    emailLogin(input) {
      return adapter.request<LoginResult>('/api/auth/email-logins', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    login(input) {
      return adapter.request<LoginResult>('/api/auth/login', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    async logout() {
      try {
        await adapter.request<void>('/api/auth/logout', {
          method: 'POST',
        });
      } finally {
        authSession.clear();
      }
    },

    me() {
      return adapter.request<AuthUser>('/api/auth/me', {
        method: 'GET',
      });
    },

    miniProgramLogin(input) {
      return adapter.request<LoginResult>('/api/auth/mini-program-logins', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    requestVerificationCode(input) {
      return adapter.request<VerificationChallenge>('/api/auth/verification-codes', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    async resetPassword(input) {
      await adapter.request<void>('/api/auth/password-resets', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    async revokeSession(sessionId) {
      await adapter.request<void>(
        `/api/auth/sessions/${encodeURIComponent(sessionId)}`,
        {
          method: 'DELETE',
        },
      );
    },

    sessions() {
      return adapter.request<SecuritySession[]>('/api/auth/sessions', {
        method: 'GET',
      });
    },

    smsLogin(input) {
      return adapter.request<LoginResult>('/api/auth/sms-logins', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },

    socialLogin(input) {
      return adapter.request<LoginResult>('/api/auth/social-logins', {
        data: input,
        method: 'POST',
        requiresAuth: false,
      });
    },
  };
}

let runtimeAuthApi: AuthApi | undefined;
let runtimeAppAdapter: RuoYiAdapter | undefined;

/**
 * Shared creator-side API adapter. Feature APIs use the same client headers,
 * bearer token and one-time session invalidation behavior as authentication.
 */
export function getRuntimeAppAdapter(): RuoYiAdapter {
  if (!runtimeAppAdapter) {
    runtimeAppAdapter = createRuoYiAdapter({
      clientId: getConfiguredAppAuthClientId(),
      execute: runtimeRequest,
      getAccessToken: authSession.getAccessToken,
      getLanguage: getCurrentLanguage,
      getSessionRevision: authSession.getRevision,
      onUnauthorized: (session) => {
        if (
          session.sessionRevision === undefined ||
          !authSession.clearIfCurrent({
            accessToken: session.accessToken,
            revision: session.sessionRevision,
          })
        ) {
          return;
        }

        if (beginLoginRedirect()) {
          history.replace('/user/login');
        }
      },
    });
  }
  return runtimeAppAdapter;
}

function getRuntimeAuthApi(): AuthApi {
  if (!runtimeAuthApi) {
    runtimeAuthApi = createAuthApi(getRuntimeAppAdapter());
  }

  return runtimeAuthApi;
}

export const authApi: AuthApi = {
  changePassword: (input) => getRuntimeAuthApi().changePassword(input),
  emailLogin: (input) => getRuntimeAuthApi().emailLogin(input),
  login: (input) => getRuntimeAuthApi().login(input),
  logout: () => getRuntimeAuthApi().logout(),
  me: () => getRuntimeAuthApi().me(),
  miniProgramLogin: (input) => getRuntimeAuthApi().miniProgramLogin(input),
  requestVerificationCode: (input) =>
    getRuntimeAuthApi().requestVerificationCode(input),
  resetPassword: (input) => getRuntimeAuthApi().resetPassword(input),
  revokeSession: (sessionId) => getRuntimeAuthApi().revokeSession(sessionId),
  sessions: () => getRuntimeAuthApi().sessions(),
  smsLogin: (input) => getRuntimeAuthApi().smsLogin(input),
  socialLogin: (input) => getRuntimeAuthApi().socialLogin(input),
};
