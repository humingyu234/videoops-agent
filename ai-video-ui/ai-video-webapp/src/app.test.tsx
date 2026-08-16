import { isValidElement, type ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import routes from '../config/routes';
import { ApiError } from './services/ai-video/core/errors';

type RouteEntry = {
  path?: string;
  routes?: RouteEntry[];
};

function collectRoutePaths(routeEntries: RouteEntry[]): string[] {
  return routeEntries.flatMap((route) => [
    ...(route.path ? [route.path] : []),
    ...collectRoutePaths(route.routes ?? []),
  ]);
}

const {
  mockAuthMe,
  mockBeginLoginRedirect,
  mockGetAccessToken,
  mockHistory,
  mockReplace,
} = vi.hoisted(() => {
  const mockReplace = vi.fn();

  return {
    mockAuthMe: vi.fn(),
    mockBeginLoginRedirect: vi.fn(),
    mockGetAccessToken: vi.fn(),
    mockHistory: {
      location: {
        pathname: '/welcome',
        search: '',
        hash: '',
      },
      replace: mockReplace,
    },
    mockReplace,
  };
});

vi.mock('@umijs/max', () => ({
  history: mockHistory,
  Link: ({ children }: { children: ReactNode }) => children,
}));

vi.mock('@/services/ai-video/auth/api', () => ({
  authApi: {
    me: mockAuthMe,
  },
}));

vi.mock('@/services/ai-video/auth/session', () => ({
  authSession: {
    getAccessToken: mockGetAccessToken,
  },
  beginLoginRedirect: mockBeginLoginRedirect,
  subscribeToAuthSessionClear: vi.fn(() => () => undefined),
}));

vi.mock('@/components', () => ({
  AvatarDropdown: ({ children }: { children: ReactNode }) => children,
  DocLink: () => null,
  ErrorBoundary: ({ children }: { children: ReactNode }) => children,
  Footer: () => null,
  LangDropdown: () => null,
  OfflineBanner: () => null,
  VersionDropdown: () => null,
}));

vi.mock('@ant-design/pro-components', () => ({
  SettingDrawer: () => null,
}));

vi.mock('@ant-design/icons', () => ({
  LinkOutlined: () => null,
}));

vi.mock('./requestErrorConfig', () => ({
  errorConfig: {},
}));

vi.mock('../config/defaultSettings', () => ({
  default: { navTheme: 'light' },
}));

describe('app runtime auth integration', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHistory.location = {
      pathname: '/welcome',
      search: '',
      hash: '',
    };
    mockAuthMe.mockResolvedValue({
      id: 'user_001',
      displayName: 'Creator',
    });
    mockBeginLoginRedirect.mockReturnValue(true);
    mockGetAccessToken.mockReturnValue(undefined);
  });

  it('wraps the auth route gate inside the application query provider', async () => {
    const [{ innerProvider }, { AppQueryProvider }, { AppAuthRouteGate }] =
      await Promise.all([
        import('./app'),
        import('@/query/appQueryClient'),
        import('@/components/AppAuthRouteGate'),
      ]);
    const providerTree = innerProvider(<span>content</span>);

    expect(isValidElement(providerTree)).toBe(true);
    if (!isValidElement<{ children: ReactNode }>(providerTree)) {
      throw new Error('Expected innerProvider to return a React element');
    }

    expect(providerTree.type).toBe(AppQueryProvider);

    const authGate = providerTree.props.children;
    expect(isValidElement(authGate)).toBe(true);
    if (!isValidElement(authGate)) {
      throw new Error('Expected AppQueryProvider to contain a React element');
    }
    expect(authGate.type).toBe(AppAuthRouteGate);
  });

  it('loads the authenticated app user into initial state outside public routes', async () => {
    const { getInitialState } = await import('./app');

    const state = await getInitialState();

    expect(mockAuthMe).toHaveBeenCalledTimes(1);
    expect(state.currentUser).toEqual({
      id: 'user_001',
      displayName: 'Creator',
    });
    expect(state.settings).toEqual({ navTheme: 'light' });
    expect(state.settingDrawerOpen).toBe(false);
  });

  it('leaves 401 redirect ownership to the shared adapter', async () => {
    const { getInitialState } = await import('./app');
    mockAuthMe.mockRejectedValue(new ApiError({ code: 401, msg: 'expired' }));

    const state = await getInitialState();

    expect(state.currentUser).toBeUndefined();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('preserves a valid session and does not redirect on a 403 response', async () => {
    const { getInitialState } = await import('./app');
    mockGetAccessToken.mockReturnValue('valid-app-token');
    mockAuthMe.mockRejectedValue(
      new ApiError({ code: 403, msg: 'forbidden', status: 403 }),
    );

    const state = await getInitialState();

    expect(state.currentUser).toBeUndefined();
    expect(state.accessDenied).toBe(true);
    expect(mockGetAccessToken()).toBe('valid-app-token');
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it.each([
    ['a server failure', new ApiError({ code: 500, msg: 'upstream detail' })],
    ['a network failure', new Error('connection reset by peer')],
  ])(
    'keeps a valid token and exposes a retryable verification failure after %s',
    async (_label, failure) => {
      const { getInitialState } = await import('./app');
      mockGetAccessToken.mockReturnValue('valid-app-token');
      mockAuthMe.mockRejectedValueOnce(failure);

      const state = await getInitialState();

      expect(state.currentUser).toBeUndefined();
      expect(state.accessDenied).toBeFalsy();
      expect(state.verificationFailed).toBe(true);
      expect(mockGetAccessToken()).toBe('valid-app-token');
      expect(mockReplace).not.toHaveBeenCalled();
    },
  );

  it('does not request the current user from the login route', async () => {
    const { getInitialState } = await import('./app');
    mockHistory.location = {
      pathname: '/user/login',
      search: '',
      hash: '',
    };

    const state = await getInitialState();

    expect(mockAuthMe).not.toHaveBeenCalled();
    expect(state.currentUser).toBeUndefined();
    expect(state.fetchUserInfo).toBeDefined();
  });

  it('does not request the current user from the password-reset route', async () => {
    const { getInitialState } = await import('./app');
    mockHistory.location = {
      pathname: '/user/password-reset',
      search: '',
      hash: '',
    };

    const state = await getInitialState();

    expect(mockAuthMe).not.toHaveBeenCalled();
    expect(state.currentUser).toBeUndefined();
  });

  it('declares only the supported public identity routes and the protected security route', () => {
    const routePaths = collectRoutePaths(routes as RouteEntry[]);

    expect(routePaths).toEqual(
      expect.arrayContaining([
        '/user/login',
        '/user/password-reset',
        '/user/security',
      ]),
    );
    expect(routePaths).not.toContain('/user/register');
  });

  it.each(['/agent', '/studio', '/user/register-result', '/user/register'])(
    'restores the session for the protected legacy route %s',
    async (pathname) => {
      const { getInitialState } = await import('./app');
      mockHistory.location = {
        pathname,
        search: '',
        hash: '',
      };

      await getInitialState();

      expect(mockAuthMe).toHaveBeenCalledTimes(1);
    },
  );

  it('redirects on page change when there is neither a user nor an access token', async () => {
    const { layout } = await import('./app');
    const runtimeLayout = layout({
      initialState: undefined,
      loading: false,
    } as Parameters<typeof layout>[0]);

    runtimeLayout.onPageChange?.();

    expect(mockBeginLoginRedirect).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fwelcome',
    );
  });

  it('redirects anonymous visitors from the layout-free /user route', async () => {
    const { onRouteChange } = await import('./app');
    mockHistory.location = {
      pathname: '/user',
      search: '?from=direct',
      hash: '#identity',
    };

    onRouteChange({
      location: mockHistory.location,
    } as Parameters<typeof onRouteChange>[0]);

    expect(mockBeginLoginRedirect).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fuser%3Ffrom%3Ddirect%23identity',
    );
  });

  it('does not redirect on page change while verified initial state is loading', async () => {
    const { layout } = await import('./app');
    const runtimeLayout = layout({
      initialState: undefined,
      loading: true,
    } as Parameters<typeof layout>[0]);

    runtimeLayout.onPageChange?.();

    expect(mockBeginLoginRedirect).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('redirects on page change when only an unverified access token is present', async () => {
    const { layout } = await import('./app');
    mockGetAccessToken.mockReturnValue('valid-app-token');
    const runtimeLayout = layout({} as Parameters<typeof layout>[0]);

    runtimeLayout.onPageChange?.();

    expect(mockBeginLoginRedirect).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fwelcome',
    );
  });

  it('does not redirect a token-backed verification failure from the layout', async () => {
    const { layout } = await import('./app');
    mockGetAccessToken.mockReturnValue('valid-app-token');
    const runtimeLayout = layout({
      initialState: { verificationFailed: true },
      loading: false,
    } as Parameters<typeof layout>[0]);

    runtimeLayout.onPageChange?.();

    expect(mockBeginLoginRedirect).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('redirects on page change when currentUser is stale after its app token is cleared', async () => {
    const { layout } = await import('./app');
    const runtimeLayout = layout({
      initialState: {
        currentUser: { id: 'creator_test' },
      },
      loading: false,
    } as Parameters<typeof layout>[0]);

    runtimeLayout.onPageChange?.();

    expect(mockBeginLoginRedirect).toHaveBeenCalledTimes(1);
    expect(mockReplace).toHaveBeenCalledWith(
      '/user/login?redirect=%2Fwelcome',
    );
  });

  it.each(['/user/login', '/user/password-reset'])(
    'does not redirect anonymous visitors from the public route %s',
    async (pathname) => {
      const { layout } = await import('./app');
      mockHistory.location = {
        pathname,
        search: '',
        hash: '',
      };
      const runtimeLayout = layout({} as Parameters<typeof layout>[0]);

      runtimeLayout.onPageChange?.();

      expect(mockBeginLoginRedirect).not.toHaveBeenCalled();
      expect(mockReplace).not.toHaveBeenCalled();
    },
  );
});
