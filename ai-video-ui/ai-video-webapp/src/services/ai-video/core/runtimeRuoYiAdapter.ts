import { getIntl, history, request } from '@umijs/max';
import {
  authSession,
  beginLoginRedirect,
} from '../auth/session';
import {
  createRuoYiAdapter,
  type HttpRequest,
  type RuoYiAdapter,
} from './ruoyiAdapter';

function getCurrentLanguage(): string {
  try {
    const locale = getIntl().locale;
    if (locale.trim()) {
      return locale;
    }
  } catch {
    // Runtime requests may start before Umi finishes initializing i18n.
  }

  return typeof navigator === 'undefined' ? 'zh-CN' : navigator.language;
}

const runtimeRequest: HttpRequest = (url, options = {}) => {
  const { responseType, ...requestOptions } = options;
  return request<unknown>(url, {
    ...requestOptions,
    ...(responseType === 'blob' ? { responseType } : {}),
  });
};

function getConfiguredAppAuthClientId(): string {
  const clientId = process.env.APP_AUTH_CLIENT_ID?.trim();
  if (!clientId) {
    throw new Error('创作端登录客户端未配置，请联系管理员。');
  }
  return clientId;
}

let runtimeAdapter: RuoYiAdapter | undefined;

export function getRuntimeRuoYiAdapter(): RuoYiAdapter {
  if (!runtimeAdapter) {
    runtimeAdapter = createRuoYiAdapter({
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

  return runtimeAdapter;
}
