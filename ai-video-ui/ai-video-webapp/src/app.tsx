import { LinkOutlined } from '@ant-design/icons';
import type { Settings as LayoutSettings } from '@ant-design/pro-components';
import { SettingDrawer } from '@ant-design/pro-components';
import type { RequestConfig, RunTimeLayoutConfig } from '@umijs/max';
import { history, Link } from '@umijs/max';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import React from 'react';

// Initialize dayjs plugins globally
dayjs.extend(relativeTime);

import {
  AvatarDropdown,
  DocLink,
  ErrorBoundary,
  Footer,
  LangDropdown,
  OfflineBanner,
  VersionDropdown,
} from '@/components';
import { AppAuthRouteGate } from '@/components/AppAuthRouteGate';
import { AppQueryProvider } from '@/query/appQueryClient';
import { authApi } from '@/services/ai-video/auth/api';
import {
  resolveAppAuthState,
  type AppAuthState,
} from '@/services/ai-video/auth/authState';
import {
  type AppLocation,
  isPublicIdentityPath,
  redirectToLogin,
} from '@/services/ai-video/auth/routeGuard';
import { authSession } from '@/services/ai-video/auth/session';
import type { AuthUser } from '@/services/ai-video/auth/types';
import { ApiError, getHttpStatus } from '@/services/ai-video/core/errors';
import { isSessionInvalidationCode } from '@/services/ai-video/core/ruoyiAdapter';
import defaultSettings from '../config/defaultSettings';
import { errorConfig } from './requestErrorConfig';

const apiBaseUrl = process.env.APP_API_BASE_URL ?? '';
const isDev = process.env.NODE_ENV === 'development';

export type AppInitialState = AppAuthState & {
  settings?: Partial<LayoutSettings>;
  loading?: boolean;
  fetchUserInfo?: () => Promise<AuthUser | undefined>;
  settingDrawerOpen?: boolean;
};

/**
 * ProLayout is intentionally disabled for /studio and /user routes. Its
 * onPageChange hook therefore cannot be the only authentication gate.
 *
 * This Umi-level hook covers every client-side route, including layout-free
 * pages and unknown URLs. A bearer token is only a navigation hint here;
 * getInitialState and the backend still verify the session server-side.
 */
export function onRouteChange({ location }: { location: AppLocation }): void {
  if (
    !isPublicIdentityPath(location.pathname) &&
    !authSession.getAccessToken()
  ) {
    redirectToLogin(location);
  }
}

export function innerProvider(container: React.ReactNode) {
  return (
    <AppQueryProvider>
      <AppAuthRouteGate>{container}</AppAuthRouteGate>
    </AppQueryProvider>
  );
}

/**
 * @see https://umijs.org/docs/api/runtime-config#getinitialstate
 * */
export async function getInitialState(): Promise<AppInitialState> {
  const fetchUserInfo = async () => {
    try {
      return await authApi.me();
    } catch {
      // The adapter performs the one-time 401 cleanup and redirect.
      return undefined;
    }
  };
  // 如果不是登录页面，执行
  const { location } = history;
  if (!isPublicIdentityPath(location.pathname)) {
    try {
      const currentUser = await authApi.me();
      return {
        fetchUserInfo,
        currentUser,
        settings: defaultSettings as Partial<LayoutSettings>,
        settingDrawerOpen: false,
      };
    } catch (error) {
      const accessDenied = hasTokenBackedForbiddenResponse(error);
      const verificationFailed =
        !accessDenied && hasTokenBackedVerificationFailure(error);
      return {
        accessDenied,
        fetchUserInfo,
        settings: defaultSettings as Partial<LayoutSettings>,
        settingDrawerOpen: false,
        verificationFailed,
      };
    }
  }
  return {
    fetchUserInfo,
    settings: defaultSettings as Partial<LayoutSettings>,
    settingDrawerOpen: false,
  };
}

function hasTokenBackedForbiddenResponse(error: unknown): boolean {
  const forbidden =
    (error instanceof ApiError && error.code === 403) ||
    getHttpStatus(error) === 403;
  return forbidden && Boolean(authSession.getAccessToken());
}

function hasTokenBackedVerificationFailure(error: unknown): boolean {
  if (!authSession.getAccessToken()) {
    return false;
  }

  const code =
    error instanceof ApiError ? error.code : getHttpStatus(error);
  return code === undefined || !isSessionInvalidationCode(code);
}

// ProLayout 支持的api https://procomponents.ant.design/components/layout
export const layout: RunTimeLayoutConfig = ({
  initialState,
  setInitialState,
  loading,
}) => {
  return {
    menuItemRender: (item, dom) => {
      if (item.path) {
        return (
          <Link to={item.path} prefetch>
            {dom}
          </Link>
        );
      }
      return dom;
    },
    actionsRender: () => {
      // `locale: false` opts out of the language switcher. ProLayout's own
      // `locale` prop is a locale string, so narrow to the boolean toggle here.
      const localeEnabled =
        (initialState?.settings as { locale?: boolean })?.locale !== false;
      return [
        <DocLink key="doc" />,
        <VersionDropdown key="version" />,
        localeEnabled && <LangDropdown key="lang" />,
      ].filter(Boolean);
    },
    avatarProps: {
      src: initialState?.currentUser?.avatarUrl,
      title: 'ProUser',
      render: (_, avatarChildren) => (
        <AvatarDropdown>{avatarChildren}</AvatarDropdown>
      ),
    },
    // waterMarkProps: {
    //   content: initialState?.currentUser?.name,
    // },
    footerRender: () => <Footer />,
    onPageChange: () => {
      const { location } = history;
      const { accessDenied, hasVerifiedUser, verificationFailed } =
        resolveAppAuthState(initialState);
      // 如果没有登录，重定向到 login
      if (
        !loading &&
        !hasVerifiedUser &&
        !accessDenied &&
        !verificationFailed &&
        !isPublicIdentityPath(location.pathname)
      ) {
        redirectToLogin(location);
      }
    },
    bgLayoutImgList: [
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/D2LWSqNny4sAAAAAAAAAAAAAFl94AQBr',
        left: 85,
        bottom: 100,
        height: '303px',
      },
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/C2TWRpJpiC0AAAAAAAAAAAAAFl94AQBr',
        bottom: -68,
        right: -45,
        height: '303px',
      },
      {
        src: 'https://mdn.alipayobjects.com/yuyan_qk0oxh/afts/img/F6vSTbj8KpYAAAAAAAAAAAAAFl94AQBr',
        bottom: 0,
        left: 0,
        width: '331px',
      },
    ],
    links: isDev
      ? [
          <Link key="openapi" to="/umi/plugin/openapi" target="_blank">
            <LinkOutlined />
            <span>OpenAPI 文档</span>
          </Link>,
        ]
      : [],
    // Replace ProLayout's default ErrorBoundary with our offline-aware version,
    // so chunk load errors show friendly messages instead of "Something went wrong."
    ErrorBoundary,
    menuHeaderRender: undefined,
    // 自定义 403 页面
    // unAccessible: <div>unAccessible</div>,
    // 增加一个 loading 的状态
    childrenRender: (children) => {
      // if (initialState?.loading) return <PageLoading />;
      return (
        <>
          {children}
          <SettingDrawer
            disableUrlParams
            enableDarkTheme
            collapse={initialState?.settingDrawerOpen}
            onCollapseChange={(open) => {
              setInitialState((s) => ({
                ...s,
                settingDrawerOpen: open,
              }));
            }}
            settings={initialState?.settings}
            onSettingChange={(settings) => {
              setInitialState((s) => ({
                ...s,
                settings,
              }));
            }}
          />
        </>
      );
    },
    ...initialState?.settings,
  };
};

/**
 * @name request 配置，可以配置错误处理
 * 它基于 axios 提供了一套统一的网络请求和错误处理方案。
 * @doc https://umijs.org/docs/max/request#配置
 */
export const request: RequestConfig = {
  baseURL: apiBaseUrl,
  ...errorConfig,
};

export function rootContainer(container: React.ReactNode) {
  return (
    <>
      <OfflineBanner />
      <ErrorBoundary>{container}</ErrorBoundary>
    </>
  );
}
