import { Helmet, history, useModel } from '@umijs/max';
import { type KeyboardEvent, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { authApi } from '@/services/ai-video/auth/api';
import { authSession } from '@/services/ai-video/auth/session';
import type { LoginResult } from '@/services/ai-video/auth/types';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';
import { LoginBrandMark } from './components/LoginBrandMark';
import {
  type LoginFailure,
  LoginFeedback,
  type LoginNotice,
} from './components/LoginFeedback';
import { LoginSceneBackdrop } from './components/LoginSceneBackdrop';
import { PasswordLoginPanel } from './components/PasswordLoginPanel';
import { WechatQrConstructionPanel } from './components/WechatQrConstructionPanel';
import styles from './index.module.css';

const STUDIO_PATH = '/studio';
const PASSWORD_CHANGED_NOTICE = 'password-changed';
const SESSION_REVOKED_NOTICE = 'session-revoked';

type LoginMethod = 'password' | 'wechat-qr';
type Authenticate = (request: () => Promise<LoginResult>) => Promise<void>;

function getLoginNotice(): LoginNotice | undefined {
  if (typeof window === 'undefined') return undefined;

  const notice = new URLSearchParams(window.location.search).get('notice');
  if (notice === PASSWORD_CHANGED_NOTICE || notice === SESSION_REVOKED_NOTICE) {
    return notice;
  }

  return undefined;
}

/** 只允许站内相对路径，避免登录回跳形成开放重定向。 */
const getSafeRedirectUrl = (redirect: string | null): string => {
  if (!redirect?.startsWith('/') || redirect.startsWith('//')) {
    return STUDIO_PATH;
  }

  try {
    const parsed = new URL(redirect, window.location.origin);
    if (parsed.origin !== window.location.origin) return STUDIO_PATH;
    if (parsed.pathname === '/user' || parsed.pathname === '/user/') {
      return STUDIO_PATH;
    }

    return `${parsed.pathname}${parsed.search}${parsed.hash}`;
  } catch {
    return STUDIO_PATH;
  }
};

function getLoginFailure(error: unknown): LoginFailure {
  if (error instanceof ApiError) {
    if (error.code === 46130) return 'client-unavailable';
    if (error.code === 401 || error.code === 46128 || error.code === 46129) {
      return 'credentials';
    }
  }

  if (getHttpStatus(error) === 401) return 'credentials';
  return 'network';
}

function isForbidden(error: unknown): boolean {
  return (
    (error instanceof ApiError && error.code === 403) ||
    getHttpStatus(error) === 403
  );
}

function Login() {
  const [activeMethod, setActiveMethod] = useState<LoginMethod>('password');
  const [failure, setFailure] = useState<LoginFailure>();
  const [submitting, setSubmitting] = useState(false);
  const loginInFlight = useRef(false);
  const passwordTabRef = useRef<HTMLButtonElement>(null);
  const qrTabRef = useRef<HTMLButtonElement>(null);
  const { setInitialState } = useModel('@@initialState');
  const notice = getLoginNotice();

  const activateTab = (method: LoginMethod) => {
    setActiveMethod(method);
    const target = method === 'password' ? passwordTabRef : qrTabRef;
    target.current?.focus();
  };

  const handleTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    let target: LoginMethod | undefined;
    if (event.key === 'Home') target = 'password';
    if (event.key === 'End') target = 'wechat-qr';
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      target = activeMethod === 'password' ? 'wechat-qr' : 'password';
    }
    if (!target) return;
    event.preventDefault();
    activateTab(target);
  };

  const authenticate: Authenticate = async (request) => {
    if (loginInFlight.current) return;

    loginInFlight.current = true;
    setFailure(undefined);
    setSubmitting(true);

    try {
      const result = await request();
      authSession.save({
        accessToken: result.access_token,
        persistent: false,
      });

      try {
        const userInfo = await authApi.me();
        if (!userInfo) {
          authSession.clear();
          setFailure('session-verification');
          return;
        }

        flushSync(() => {
          setInitialState((state) => ({
            ...state,
            accessDenied: false,
            currentUser: userInfo,
            verificationFailed: false,
          }));
        });
      } catch (error) {
        if (isForbidden(error)) {
          flushSync(() => {
            setInitialState((state) => ({
              ...state,
              accessDenied: true,
              currentUser: undefined,
              verificationFailed: false,
            }));
          });
          history.replace(STUDIO_PATH);
          return;
        }

        authSession.clear();
        setFailure(getLoginFailure(error));
        return;
      }

      const urlParams = new URL(window.location.href).searchParams;
      history.replace(getSafeRedirectUrl(urlParams.get('redirect')));
    } catch (error) {
      setFailure(getLoginFailure(error));
    } finally {
      loginInFlight.current = false;
      setSubmitting(false);
    }
  };

  return (
    <main className={styles.loginPage}>
      <Helmet>
        <title>素造智能体 · 开启你的创作</title>
      </Helmet>
      <LoginSceneBackdrop />
      <header className={styles.topnav}>
        <div className={styles.brand}>
          <LoginBrandMark className={styles.brandMark} />
          <span>素造智能体</span>
        </div>
      </header>
      <div className={styles.stage}>
        <section className={styles.heroCopy}>
          <span className={styles.heroEyebrow}>NEW · 数字人 1.0</span>
          <h1 className={styles.heroTitle}>
            让创意
            <br />
            <span className={styles.heroAccent}>化为数字生命</span>
          </h1>
          <p className={styles.heroSub}>
            从形象生成到实时直播，一站式 AI
            数字人创作平台。现在登录，开启你的创作之旅。
          </p>
        </section>
        <section aria-labelledby="login-card-title" className={styles.cardWrap}>
          <div className={styles.card}>
            <div className={styles.cardBrand}>
              <LoginBrandMark className={styles.brandMark} />
              <span>素造智能体</span>
            </div>
            <h2 className={styles.cardTitle} id="login-card-title">
              开启你的创作
            </h2>
            <p className={styles.cardSub}>登录以继续使用数字人创作工具</p>
            <div className={styles.tabsRoot}>
              <div
                className={styles.tabsHeader}
                data-active={activeMethod}
                role="tablist"
              >
                <button
                  aria-controls="password-login-panel"
                  aria-selected={activeMethod === 'password'}
                  className={styles.tabItem}
                  disabled={submitting}
                  id="password-login-tab"
                  onClick={() => setActiveMethod('password')}
                  onKeyDown={handleTabKeyDown}
                  ref={passwordTabRef}
                  role="tab"
                  tabIndex={activeMethod === 'password' ? 0 : -1}
                  type="button"
                >
                  账号密码
                </button>
                <button
                  aria-controls="wechat-qr-login-panel"
                  aria-selected={activeMethod === 'wechat-qr'}
                  className={styles.tabItem}
                  disabled={submitting}
                  id="wechat-qr-login-tab"
                  onClick={() => setActiveMethod('wechat-qr')}
                  onKeyDown={handleTabKeyDown}
                  ref={qrTabRef}
                  role="tab"
                  tabIndex={activeMethod === 'wechat-qr' ? 0 : -1}
                  type="button"
                >
                  扫码登录
                </button>
                <span aria-hidden="true" className={styles.tabIndicator} />
              </div>
              <div
                aria-labelledby={`${activeMethod}-login-tab`}
                className={styles.tabPanel}
                id={`${activeMethod}-login-panel`}
                role="tabpanel"
              >
                {activeMethod === 'password' ? (
                  <PasswordLoginPanel
                    authenticate={authenticate}
                    submitting={submitting}
                  />
                ) : (
                  <WechatQrConstructionPanel />
                )}
              </div>
            </div>
          </div>
        </section>
      </div>
      <LoginFeedback failure={failure} notice={notice} />
    </main>
  );
}

export default Login;
