import { history, useModel } from '@umijs/max';
import { Button, Result } from 'antd';
import { useEffect, useState, type ReactNode } from 'react';
import { isPublicIdentityPath, redirectToLogin } from '@/services/ai-video/auth/routeGuard';
import {
  clearInvalidatedAppAuthState,
  resolveAppAuthState,
  retryAppAuthVerification,
  type AppAuthState,
} from '@/services/ai-video/auth/authState';
import {
  resetLoginRedirect,
  subscribeToAuthSessionClear,
} from '@/services/ai-video/auth/session';

type AppInitialState = AppAuthState & Record<string, unknown>;

type InitialStateModel = {
  initialState?: AppInitialState;
  loading?: boolean;
  refresh?: () => Promise<unknown> | undefined;
  setInitialState?: (
    updater: (
      state: AppInitialState | undefined,
    ) => AppInitialState | undefined,
  ) => void;
};

type AppAuthRouteGateProps = {
  children: ReactNode;
};

/**
 * Applies verified app-session access control to every route tree, including
 * routes that intentionally opt out of ProLayout.
 */
export function AppAuthRouteGate({ children }: AppAuthRouteGateProps) {
  const { initialState, loading = true, refresh, setInitialState } = useModel(
    '@@initialState',
  ) as InitialStateModel;
  const [location, setLocation] = useState(() => history.location);
  const isPublicRoute = isPublicIdentityPath(location.pathname);
  const { accessDenied, hasVerifiedUser, verificationFailed } =
    resolveAppAuthState(initialState);

  useEffect(() => history.listen(({ location: nextLocation }) => {
    setLocation(nextLocation);
  }), []);

  useEffect(() => {
    if (!setInitialState) {
      return undefined;
    }

    return subscribeToAuthSessionClear(() => {
      setInitialState((state) => clearInvalidatedAppAuthState(state));
    });
  }, [setInitialState]);

  useEffect(() => {
    if (isPublicRoute) {
      // A completed redirect must not suppress a later anonymous navigation
      // to another protected route in the same SPA session.
      resetLoginRedirect();
    }
  }, [isPublicRoute]);

  useEffect(() => {
    if (
      !isPublicRoute &&
      !loading &&
      !hasVerifiedUser &&
      !accessDenied &&
      !verificationFailed
    ) {
      redirectToLogin(location);
    }
  }, [
    accessDenied,
    hasVerifiedUser,
    isPublicRoute,
    loading,
    location,
    verificationFailed,
  ]);

  if (isPublicRoute || hasVerifiedUser) {
    return <>{children}</>;
  }

  if (accessDenied) {
    return (
      <Result
        status="403"
        subTitle="当前登录状态有效，但无权访问此功能。"
        title="访问受限"
      />
    );
  }

  if (verificationFailed) {
    return (
      <Result
        extra={
          <Button onClick={() => retryAppAuthVerification(refresh)} type="primary">
            重新验证
          </Button>
        }
        status="error"
        subTitle="暂时无法验证当前登录状态，请检查网络后重新验证。"
        title="无法验证登录状态"
      />
    );
  }

  return (
    <div aria-live="polite" role="status">
      {loading ? '正在验证登录状态' : '正在跳转到登录页'}
    </div>
  );
}
