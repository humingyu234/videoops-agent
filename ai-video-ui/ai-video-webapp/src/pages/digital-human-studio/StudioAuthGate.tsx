import { history, useModel } from '@umijs/max';
import { Button, Result } from 'antd';
import type { ReactNode } from 'react';
import React, { useEffect, useRef } from 'react';
import {
  resolveAppAuthState,
  retryAppAuthVerification,
  type AppAuthState,
} from '@/services/ai-video/auth/authState';
import {
  beginLoginRedirect,
} from '@/services/ai-video/auth/session';

const LOGIN_PATH = '/user/login';
const STUDIO_PATH = '/studio';

function getCurrentInternalLocation(): string {
  const { hash, pathname, search } = history.location;
  const redirect = `${pathname}${search}${hash}`;

  return redirect.startsWith('/') && !redirect.startsWith('//')
    ? redirect
    : STUDIO_PATH;
}

type StudioAuthGateProps = {
  children: ReactNode;
};

type InitialStateModel = {
  initialState?: AppAuthState;
  loading?: boolean;
  refresh?: () => Promise<unknown> | undefined;
};

const StudioAuthGate: React.FC<StudioAuthGateProps> = ({ children }) => {
  const { initialState, loading, refresh } = useModel(
    '@@initialState',
  ) as InitialStateModel;
  const redirectStarted = useRef(false);
  const { accessDenied, hasVerifiedUser, verificationFailed } =
    resolveAppAuthState(initialState);
  const needsLoginRedirect =
    !loading && !hasVerifiedUser && !accessDenied && !verificationFailed;

  useEffect(() => {
    if (
      !needsLoginRedirect ||
      redirectStarted.current ||
      !beginLoginRedirect()
    ) {
      return;
    }

    redirectStarted.current = true;
    history.replace(
      `${LOGIN_PATH}?redirect=${encodeURIComponent(getCurrentInternalLocation())}`,
    );
  }, [needsLoginRedirect]);

  if (loading) {
    return (
      <main aria-busy="true" aria-label="登录状态验证中">
        <p role="status">正在验证登录状态…</p>
      </main>
    );
  }

  if (accessDenied) {
    return (
      <main aria-labelledby="studio-access-denied-title">
        <Result
          status="403"
          subTitle="当前登录状态有效，但无权访问创作工作台。"
          title={<span id="studio-access-denied-title">访问受限</span>}
        />
      </main>
    );
  }

  if (verificationFailed) {
    return (
      <main aria-labelledby="studio-verification-failed-title">
        <Result
          extra={
            <Button onClick={() => retryAppAuthVerification(refresh)} type="primary">
              重新验证
            </Button>
          }
          status="error"
          subTitle="暂时无法验证当前登录状态，请检查网络后重新验证。"
          title={
            <span id="studio-verification-failed-title">
              无法验证登录状态
            </span>
          }
        />
      </main>
    );
  }

  if (!hasVerifiedUser) {
    return (
      <main aria-live="polite" aria-label="正在跳转登录页">
        <p role="status">正在跳转至登录页…</p>
      </main>
    );
  }

  return <>{children}</>;
};

export default StudioAuthGate;
