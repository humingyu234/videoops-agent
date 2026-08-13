import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { AppAuthRouteGate } from './index';

const {
  mockBeginLoginRedirect,
  mockGetAccessToken,
  mockHistory,
  mockReplace,
  mockRefresh,
  mockResetLoginRedirect,
  mockSubscribeToAuthSessionClear,
  triggerAuthSessionClear,
  mockUseModel,
} =
  vi.hoisted(() => {
    const mockReplace = vi.fn();
    let authSessionClearListener: (() => void) | undefined;
    const mockSubscribeToAuthSessionClear = vi.fn(
      (listener: () => void) => {
        authSessionClearListener = listener;
        return () => {
          authSessionClearListener = undefined;
        };
      },
    );

    return {
      mockBeginLoginRedirect: vi.fn(),
      mockGetAccessToken: vi.fn(),
      mockHistory: {
        listen: vi.fn(() => () => undefined),
        location: {
          pathname: '/user',
          search: '',
          hash: '',
      },
      replace: mockReplace,
      },
      mockReplace,
      mockRefresh: vi.fn(),
      mockResetLoginRedirect: vi.fn(),
      mockSubscribeToAuthSessionClear,
      mockUseModel: vi.fn(),
      triggerAuthSessionClear: () => authSessionClearListener?.(),
    };
  });

vi.mock('@umijs/max', () => ({
  history: mockHistory,
  useModel: mockUseModel,
}));

vi.mock('@/services/ai-video/auth/session', () => ({
  authSession: {
    getAccessToken: mockGetAccessToken,
  },
  beginLoginRedirect: mockBeginLoginRedirect,
  resetLoginRedirect: mockResetLoginRedirect,
  subscribeToAuthSessionClear: mockSubscribeToAuthSessionClear,
}));

describe('AppAuthRouteGate', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockBeginLoginRedirect.mockReturnValue(true);
    mockGetAccessToken.mockReturnValue('verified-app-token');
    mockHistory.location = {
      pathname: '/user',
      search: '',
      hash: '',
    };
    mockUseModel.mockReturnValue({
      initialState: undefined,
      loading: false,
      refresh: mockRefresh,
    });
  });

  function renderGate(children: ReactNode = <div>受保护内容</div>) {
    return render(<AppAuthRouteGate>{children}</AppAuthRouteGate>);
  }

  it('does not render a layout-free protected route for an anonymous visitor', async () => {
    renderGate();

    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('正在跳转到登录页');
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/user/login?redirect=%2Fuser');
    });
  });

  it('keeps protected content hidden until the server session check finishes', () => {
    mockUseModel.mockReturnValue({
      initialState: undefined,
      loading: true,
      refresh: mockRefresh,
    });

    renderGate();

    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('正在验证登录状态');
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('renders a protected route only after a verified app user is available', () => {
    mockUseModel.mockReturnValue({
      initialState: {
        currentUser: {
          displayName: '本地测试创作者',
          id: 'creator_test',
        },
      },
      loading: false,
      refresh: mockRefresh,
    });

    renderGate();

    expect(screen.getByText('受保护内容')).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('does not render protected content when the app token disappeared but currentUser is stale', async () => {
    mockGetAccessToken.mockReturnValue(undefined);
    mockUseModel.mockReturnValue({
      initialState: {
        currentUser: {
          displayName: 'Stale creator',
          id: 'creator_test',
        },
      },
      loading: false,
      refresh: mockRefresh,
    });

    renderGate();

    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('正在跳转到登录页');
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/user/login?redirect=%2Fuser');
    });
  });

  it('keeps a token-backed 403 result on a protected route instead of redirecting to login', () => {
    mockUseModel.mockReturnValue({
      initialState: {
        accessDenied: true,
      },
      loading: false,
      refresh: mockRefresh,
    });

    renderGate();

    expect(screen.getByText('访问受限')).toBeVisible();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('keeps a token-backed verification failure visible and retries the server verification', () => {
    mockUseModel.mockReturnValue({
      initialState: {
        verificationFailed: true,
      },
      loading: false,
      refresh: mockRefresh,
    });

    renderGate();

    expect(screen.getByText('无法验证登录状态')).toBeVisible();
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '重新验证' }));

    expect(mockRefresh).toHaveBeenCalledTimes(1);
  });

  it('renders protected content after retry refreshes a verified app user', async () => {
    let modelState: {
      initialState: {
        currentUser?: { displayName: string; id: string };
        verificationFailed?: boolean;
      };
      loading: boolean;
    } = {
      initialState: { verificationFailed: true },
      loading: false,
    };
    const refresh = vi.fn(async () => {
      modelState = {
        ...modelState,
        initialState: {
          currentUser: {
            displayName: 'Recovered creator',
            id: 'creator_recovered',
          },
          verificationFailed: false,
        },
      };
    });
    mockUseModel.mockImplementation(() => ({
      ...modelState,
      refresh,
    }));

    const { rerender } = renderGate();

    fireEvent.click(screen.getByRole('button', { name: '重新验证' }));
    await waitFor(() => {
      expect(refresh).toHaveBeenCalledTimes(1);
    });
    rerender(<AppAuthRouteGate><div>受保护内容</div></AppAuthRouteGate>);

    expect(screen.getByText('受保护内容')).toBeVisible();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('clears stale identity state synchronously when the shared session is invalidated', async () => {
    let modelState = {
      initialState: {
        accessDenied: true,
        currentUser: {
          displayName: 'Stale creator',
          id: 'creator_test',
        },
        verificationFailed: true,
      },
      loading: false,
      refresh: mockRefresh,
    };
    const setInitialState = vi.fn((update) => {
      modelState = {
        ...modelState,
        initialState: update(modelState.initialState),
      };
    });
    mockUseModel.mockImplementation(() => ({
      ...modelState,
      setInitialState,
    }));

    const { rerender } = renderGate();

    await waitFor(() => {
      expect(mockSubscribeToAuthSessionClear).toHaveBeenCalledTimes(1);
    });
    expect(screen.getByText('受保护内容')).toBeVisible();

    act(() => {
      mockGetAccessToken.mockReturnValue(undefined);
      triggerAuthSessionClear();
    });
    rerender(<AppAuthRouteGate><div>受保护内容</div></AppAuthRouteGate>);

    expect(modelState.initialState).toEqual({
      accessDenied: false,
      currentUser: undefined,
      verificationFailed: false,
    });
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/user/login?redirect=%2Fuser');
    });
  });

  it.each(['/user/login', '/user/password-reset'])(
    'keeps the public identity route %s available without a session',
    (pathname) => {
      mockHistory.location = {
        pathname,
        search: '',
        hash: '',
      };

      renderGate();

      expect(screen.getByText('受保护内容')).toBeInTheDocument();
      expect(mockReplace).not.toHaveBeenCalled();
    },
  );

  it('does not keep the deferred registration address public', async () => {
    mockHistory.location = {
      pathname: '/user/register',
      search: '',
      hash: '',
    };

    renderGate();

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/user/login?redirect=%2Fuser%2Fregister');
    });
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument();
  });

  it('releases the redirect latch after the login route becomes available', async () => {
    mockHistory.location = {
      pathname: '/user/login',
      search: '?redirect=%2Fuser',
      hash: '',
    };

    renderGate();

    await waitFor(() => {
      expect(mockResetLoginRedirect).toHaveBeenCalledTimes(1);
    });
  });
});
