import { App } from 'antd';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type {
  ComponentPropsWithoutRef,
  ReactElement,
  ReactNode,
} from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AvatarDropdown } from '@/components/RightContent/AvatarDropdown';
import { ApiError } from '@/services/ai-video/core/errors';
import SecurityPlaceholder from '../security';
import Login from './index';

const {
  mockAuthLogin,
  mockAuthLogout,
  mockAuthMe,
  mockAuthEmailLogin,
  mockAuthMiniProgramLogin,
  mockAuthRequestVerificationCode,
  mockAuthSmsLogin,
  mockAuthSocialLogin,
  mockAuthSessionClear,
  mockAuthSessionSave,
  mockBeginLoginRedirect,
  mockFetchUserInfo,
  mockFlushSync,
  mockGetAccessToken,
  mockHistoryPush,
  mockHistoryReplace,
  mockLegacyLogin,
  mockLegacyLogout,
  mockSetInitialState,
  mockUseModel,
} = vi.hoisted(() => ({
  mockAuthLogin: vi.fn(),
  mockAuthLogout: vi.fn(),
  mockAuthMe: vi.fn(),
  mockAuthEmailLogin: vi.fn(),
  mockAuthMiniProgramLogin: vi.fn(),
  mockAuthRequestVerificationCode: vi.fn(),
  mockAuthSmsLogin: vi.fn(),
  mockAuthSocialLogin: vi.fn(),
  mockAuthSessionClear: vi.fn(),
  mockAuthSessionSave: vi.fn(),
  mockBeginLoginRedirect: vi.fn(),
  mockFetchUserInfo: vi.fn(),
  mockFlushSync: vi.fn((callback: () => unknown) => callback()),
  mockGetAccessToken: vi.fn(),
  mockHistoryPush: vi.fn(),
  mockHistoryReplace: vi.fn(),
  mockLegacyLogin: vi.fn(),
  mockLegacyLogout: vi.fn(),
  mockSetInitialState: vi.fn(),
  mockUseModel: vi.fn(),
}));

vi.mock('antd', async () => {
  const React = await import('react');

  type ButtonProps = Omit<ComponentPropsWithoutRef<'button'>, 'type'> & {
    autoInsertSpace?: boolean;
    block?: boolean;
    classNames?: { root?: string };
    htmlType?: 'button' | 'reset' | 'submit';
    loading?: boolean;
    type?: string;
  };
  type FormValues = Record<string, string>;
  type FormContextValue = {
    disabled: boolean;
    setValue: (name: string, value: string) => void;
    values: FormValues;
  };
  type InputProps = Omit<ComponentPropsWithoutRef<'input'>, 'prefix'> & {
    prefix?: ReactNode;
    suffix?: ReactNode;
  };

  const FormContext = React.createContext<FormContextValue | null>(null);

  const Button = ({
    autoInsertSpace: _autoInsertSpace,
    block: _block,
    classNames: _classNames,
    htmlType = 'button',
    loading = false,
    type: _type,
    ...props
  }: ButtonProps) => (
    <button {...props} disabled={props.disabled || loading} type={htmlType} />
  );
  const FormRoot = ({
    children,
    disabled = false,
    onFinish,
  }: {
    children: ReactNode;
    disabled?: boolean;
    onFinish?: (values: FormValues) => void;
  }) => {
    const [values, setValues] = React.useState<FormValues>({});

    return (
      <form
        data-testid="login-form"
        onSubmit={(event) => {
          event.preventDefault();
          onFinish?.(values);
        }}
      >
        <FormContext
          value={{
            disabled,
            setValue: (name, value) =>
              setValues((current) => ({ ...current, [name]: value })),
            values,
          }}
        >
          {children}
        </FormContext>
      </form>
    );
  };
  const FormItem = ({
    children,
    label,
    name,
  }: {
    children: ReactNode;
    label?: ReactNode;
    name?: string;
  }) => {
    const field = React.isValidElement(children)
      ? React.cloneElement(
          children as ReactElement<{ id?: string; name?: string }>,
          { id: name, name },
        )
      : children;

    return label ? <label htmlFor={name}>{label}{field}</label> : field;
  };
  const Form = Object.assign(FormRoot, { Item: FormItem });
  const InputRoot = ({
    prefix,
    suffix,
    name,
    onChange,
    ...props
  }: InputProps) => {
    const form = React.useContext(FormContext);
    const value = name && form ? (form.values[name] ?? '') : props.value;

    return (
      <span>
        {prefix}
        <input
          {...props}
          disabled={props.disabled || form?.disabled}
          name={name}
          onChange={(event) => {
            onChange?.(event);
            if (form && name) {
              form.setValue(name, event.target.value);
            }
          }}
          value={value}
        />
        {suffix}
      </span>
    );
  };
  const Input = Object.assign(InputRoot, {
    Password: (props: InputProps) => <InputRoot {...props} type="password" />,
  });
  const Alert = ({
    description,
    title,
  }: {
    description?: ReactNode;
    title?: ReactNode;
  }) => (
    <div role="alert">
      {title}
      {description}
    </div>
  );
  const AppComponent = Object.assign(
    ({ children }: { children: ReactNode }) => <>{children}</>,
    {
      useApp: () => ({
        message: {
          error: vi.fn(),
          success: vi.fn(),
        },
      }),
    },
  );

  return {
    Alert,
    App: AppComponent,
    Button,
    Form,
    Input,
    Spin: ({ description }: { description?: ReactNode }) => <span>{description ?? '加载中'}</span>,
    Tabs: ({
      activeKey,
      items,
      onChange,
    }: {
      activeKey?: string;
      items?: Array<{
        children?: ReactNode;
        disabled?: boolean;
        key: string;
        label: ReactNode;
      }>;
      onChange?: (key: string) => void;
    }) => {
      const [uncontrolledActiveKey, setUncontrolledActiveKey] = React.useState(
        items?.[0]?.key,
      );
      const selectedKey = activeKey ?? uncontrolledActiveKey;
      const selectedItem = items?.find((item) => item.key === selectedKey);

      return (
        <div>
          <div role="tablist">
            {items?.map((item) => (
              <button
                aria-controls={`${item.key}-login-panel`}
                aria-selected={item.key === selectedKey}
                disabled={item.disabled}
                id={`${item.key}-login-tab`}
                key={item.key}
                onClick={() => {
                  setUncontrolledActiveKey(item.key);
                  onChange?.(item.key);
                }}
                role="tab"
                type="button"
              >
                {item.label}
              </button>
            ))}
          </div>
          <div
            aria-labelledby={`${selectedKey}-login-tab`}
            id={`${selectedKey}-login-panel`}
            role="tabpanel"
          >
            {selectedItem?.children}
          </div>
        </div>
      );
    },
  };
});

vi.mock('react-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-dom')>();

  return {
    ...actual,
    flushSync: mockFlushSync,
  };
});

vi.mock('@umijs/max', () => ({
  FormattedMessage: ({ defaultMessage }: { defaultMessage?: string }) =>
    defaultMessage ?? null,
  Helmet: ({ children }: { children: ReactNode }) => children,
  Link: ({ children, to }: { children: ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  SelectLang: () => null,
  history: {
    push: mockHistoryPush,
    replace: mockHistoryReplace,
  },
  useIntl: () => ({
    formatMessage: ({ defaultMessage }: { defaultMessage?: string }) =>
      defaultMessage ?? '',
  }),
  useModel: mockUseModel,
}));

vi.mock('@/components', () => ({
  Footer: () => <footer data-testid="shared-footer" />,
}));

vi.mock('@/components/HeaderDropdown', () => ({
  default: ({
    children,
    menu,
  }: {
    children: ReactNode;
    menu?: { onClick?: (event: { key: string }) => void };
  }) => (
    <div>
      {children}
      <button
        type="button"
        onClick={() => menu?.onClick?.({ key: 'logout' })}
      >
        触发退出
      </button>
    </div>
  ),
}));

vi.mock('@/services/ai-video/auth/api', () => ({
  authApi: {
    emailLogin: mockAuthEmailLogin,
    login: mockAuthLogin,
    logout: mockAuthLogout,
    me: mockAuthMe,
    miniProgramLogin: mockAuthMiniProgramLogin,
    requestVerificationCode: mockAuthRequestVerificationCode,
    smsLogin: mockAuthSmsLogin,
    socialLogin: mockAuthSocialLogin,
  },
}));

vi.mock('@/services/ai-video/auth/session', () => ({
  beginLoginRedirect: mockBeginLoginRedirect,
  authSession: {
    clear: mockAuthSessionClear,
    getAccessToken: mockGetAccessToken,
    save: mockAuthSessionSave,
  },
}));

vi.mock('@/services/ant-design-pro/api', () => ({
  login: mockLegacyLogin,
  outLogin: mockLegacyLogout,
}));

function renderLogin() {
  return render(
    <App>
      <Login />
    </App>,
  );
}

function fillCredentials(
  identifier = 'creator@example.com',
  password = 'correct-password',
) {
  fireEvent.change(screen.getByLabelText('账号', { selector: 'input' }), {
    target: { value: identifier },
  });
  fireEvent.change(screen.getByLabelText('密码', { selector: 'input' }), {
    target: { value: password },
  });
}

function submitLogin() {
  fireEvent.click(screen.getByRole('button', { name: '登录' }));
}

describe('creator login', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHistoryReplace.mockReset();
    mockSetInitialState.mockReset();
    mockFlushSync.mockImplementation((callback) => callback());
    window.history.replaceState({}, '', '/user/login');
    mockAuthLogin.mockResolvedValue({
      access_token: 'app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    mockAuthEmailLogin.mockResolvedValue({
      access_token: 'app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    mockAuthMiniProgramLogin.mockResolvedValue({
      access_token: 'app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    mockAuthRequestVerificationCode.mockResolvedValue({
      challenge_id: 'login-challenge-001',
      expires_in: 600,
      masked_target: '138****5678',
    });
    mockAuthSmsLogin.mockResolvedValue({
      access_token: 'app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    mockAuthSocialLogin.mockResolvedValue({
      access_token: 'app-access-token',
      client_id: 'desktop-web',
      expire_in: 7200,
    });
    mockAuthLogout.mockResolvedValue(undefined);
    mockAuthMe.mockResolvedValue({ id: 'app-user-001' });
    mockFetchUserInfo.mockResolvedValue({ id: 'app-user-001' });
    mockGetAccessToken.mockReturnValue(undefined);
    mockBeginLoginRedirect.mockReturnValue(true);
    mockLegacyLogin.mockResolvedValue({ status: 'error' });
    mockLegacyLogout.mockResolvedValue(undefined);
    mockUseModel.mockReturnValue({
      initialState: {
        currentUser: { id: 'app-user-001' },
        fetchUserInfo: mockFetchUserInfo,
      },
      setInitialState: mockSetInitialState,
    });
  });

  it('shows the fixed password-changed notice after a security-page redirect', () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?notice=password-changed',
    );

    renderLogin();

    expect(screen.getByRole('status')).toHaveTextContent(
      '密码修改成功，请使用新密码重新登录',
    );
  });

  it('shows only password and WeChat QR login without postponed controls', () => {
    renderLogin();

    expect(screen.getByRole('tab', { name: '账号密码' })).toBeVisible();
    expect(screen.getByRole('tab', { name: '扫码登录' })).toBeVisible();
    expect(screen.getAllByRole('tab')).toHaveLength(2);
    expect(screen.queryByText('记住我')).not.toBeInTheDocument();
    expect(screen.queryByText('忘记密码？')).not.toBeInTheDocument();
    expect(screen.queryByText('其他方式')).not.toBeInTheDocument();
    expect(screen.queryByText('立即注册')).not.toBeInTheDocument();
    expect(screen.queryByText('短信验证码登录')).not.toBeInTheDocument();
    expect(screen.queryByText('邮箱验证码登录')).not.toBeInTheDocument();
    expect(screen.queryByText('第三方授权登录')).not.toBeInTheDocument();
    expect(screen.queryByText('微信小程序登录')).not.toBeInTheDocument();
    expect(screen.queryByTestId('shared-footer')).not.toBeInTheDocument();
  });

  it('supports the standard keyboard model for login tabs', () => {
    renderLogin();

    const passwordTab = screen.getByRole('tab', { name: '账号密码' });
    const qrTab = screen.getByRole('tab', { name: '扫码登录' });

    expect(passwordTab).toHaveAttribute('tabindex', '0');
    expect(qrTab).toHaveAttribute('tabindex', '-1');

    passwordTab.focus();
    fireEvent.keyDown(passwordTab, { key: 'ArrowRight' });
    expect(qrTab).toHaveAttribute('aria-selected', 'true');
    expect(qrTab).toHaveAttribute('tabindex', '0');
    expect(qrTab).toHaveFocus();

    fireEvent.keyDown(qrTab, { key: 'Home' });
    expect(passwordTab).toHaveAttribute('aria-selected', 'true');
    expect(passwordTab).toHaveFocus();

    fireEvent.keyDown(passwordTab, { key: 'End' });
    expect(qrTab).toHaveAttribute('aria-selected', 'true');
    expect(qrTab).toHaveFocus();

    fireEvent.keyDown(qrTab, { key: 'ArrowLeft' });
    expect(passwordTab).toHaveAttribute('aria-selected', 'true');
    expect(passwordTab).toHaveFocus();
  });

  it('does not invoke any login API from the QR construction panel', () => {
    renderLogin();

    fireEvent.click(screen.getByRole('tab', { name: '扫码登录' }));

    expect(
      screen.getByRole('status', { name: '微信扫码登录建设中' }),
    ).toBeVisible();
    expect(mockAuthLogin).not.toHaveBeenCalled();
    expect(mockAuthRequestVerificationCode).not.toHaveBeenCalled();
    expect(mockAuthSmsLogin).not.toHaveBeenCalled();
    expect(mockAuthEmailLogin).not.toHaveBeenCalled();
    expect(mockAuthSocialLogin).not.toHaveBeenCalled();
    expect(mockAuthMiniProgramLogin).not.toHaveBeenCalled();
  });

  it('shows the fixed session-revoked notice after exiting the current device', () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?notice=session-revoked',
    );

    renderLogin();

    expect(screen.getByRole('status')).toHaveTextContent(
      '当前设备的登录会话已退出，请重新登录。',
    );
  });

  it('does not render arbitrary login notice query text', () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?notice=untrusted-query-message',
    );

    renderLogin();

    expect(
      screen.queryByText('untrusted-query-message'),
    ).not.toBeInTheDocument();
  });

  it('keeps the password-changed notice visible when a login failure is also shown', async () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?notice=password-changed',
    );
    mockAuthLogin.mockRejectedValueOnce(
      new ApiError({ code: 46128, msg: 'invalid credentials' }),
    );

    renderLogin();
    fillCredentials();
    submitLogin();

    expect(
      await screen.findByText('密码修改成功，请使用新密码重新登录'),
    ).toBeVisible();
    expect(await screen.findByText('账号或凭据不正确')).toBeVisible();
    expect(screen.getByRole('status')).toHaveTextContent(
      '密码修改成功，请使用新密码重新登录',
    );
    expect(screen.getByRole('alert')).toHaveTextContent('账号或凭据不正确');
  });

  it('starts only one login request when the same form is submitted twice before it resolves', async () => {
    let resolveLogin:
      | ((result: {
          access_token: string;
          client_id: string;
          expire_in: number;
        }) => void)
      | undefined;
    mockAuthLogin.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveLogin = resolve;
        }),
    );
    renderLogin();
    fillCredentials();

    const loginForm = screen.getByTestId('login-form');
    fireEvent.submit(loginForm);
    fireEvent.submit(loginForm);

    try {
      await waitFor(() => {
        expect(mockAuthLogin).toHaveBeenCalledTimes(1);
      });
    } finally {
      await act(async () => {
        resolveLogin?.({
          access_token: 'app-access-token',
          client_id: 'desktop-web',
          expire_in: 7200,
        });
      });
    }
  });

  it('keeps the empty login button enabled but does not submit invalid values', () => {
    renderLogin();

    const submitButton = screen.getByRole('button', { name: '登录' });
    expect(submitButton).toBeEnabled();
    fireEvent.click(submitButton);

    expect(mockAuthLogin).not.toHaveBeenCalled();
  });

  it('verifies the saved session with me before setting the user and returning safely', async () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?redirect=%2Fstudio%3Fsource%3Dlogin',
    );
    let resolveCurrentUser: ((user: { id: string }) => void) | undefined;
    mockAuthMe.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveCurrentUser = resolve;
        }),
    );

    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockAuthLogin).toHaveBeenCalledWith({
        identifier: 'creator@example.com',
        password: 'correct-password',
      });
    });
    expect(mockAuthSessionSave).toHaveBeenCalledWith({
      accessToken: 'app-access-token',
      persistent: false,
    });
    await waitFor(() => {
      expect(mockAuthMe).toHaveBeenCalledTimes(1);
    });
    expect(mockFetchUserInfo).not.toHaveBeenCalled();
    expect(mockHistoryReplace).not.toHaveBeenCalled();

    resolveCurrentUser?.({ id: 'app-user-verified' });

    await waitFor(() => {
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith('/studio?source=login');
    });
    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: {
        currentUser?: { id: string };
        verificationFailed?: boolean;
      },
    ) => {
      accessDenied?: boolean;
      currentUser?: { id: string };
      verificationFailed?: boolean;
    };
    expect(
      updateInitialState({
        currentUser: undefined,
        verificationFailed: true,
      }),
    ).toEqual({
      accessDenied: false,
      currentUser: { id: 'app-user-verified' },
      verificationFailed: false,
    });
    expect(mockLegacyLogin).not.toHaveBeenCalled();
  });

  it('clears prior access-denied and verification-failed states after session verification succeeds', async () => {
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
    });

    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: {
        accessDenied?: boolean;
        currentUser?: { id: string };
        verificationFailed?: boolean;
      },
    ) => {
      accessDenied?: boolean;
      currentUser?: { id: string };
      verificationFailed?: boolean;
    };
    expect(
      updateInitialState({
        accessDenied: true,
        currentUser: undefined,
        verificationFailed: true,
      }),
    ).toEqual({
      accessDenied: false,
      currentUser: { id: 'app-user-001' },
      verificationFailed: false,
    });
  });

  it('commits the verified user before returning to a protected redirect target', async () => {
    window.history.replaceState({}, '', '/user/login?redirect=%2Fstudio');
    let isSynchronouslyCommitting = false;
    let committedCurrentUser: { id: string } | undefined;
    const redirects: string[] = [];

    mockFlushSync.mockImplementation((callback) => {
      isSynchronouslyCommitting = true;
      try {
        return callback();
      } finally {
        isSynchronouslyCommitting = false;
      }
    });
    mockSetInitialState.mockImplementation((update) => {
      if (!isSynchronouslyCommitting) {
        return;
      }

      committedCurrentUser = (
        update as (state: { currentUser?: { id: string } }) => {
          currentUser?: { id: string };
        }
      )({ currentUser: undefined }).currentUser;
    });
    mockHistoryReplace.mockImplementation((target: string) => {
      redirects.push(target);
      if (target === '/studio' && !committedCurrentUser) {
        redirects.push('/user/login?redirect=%2Fstudio');
      }
    });

    renderLogin();
    fillCredentials();
    submitLogin();

    await waitFor(() => {
      expect(redirects).toEqual(['/studio']);
    });
    expect(committedCurrentUser).toEqual({ id: 'app-user-001' });
  });

  it('uses the studio page when the protected redirect target is the empty user route', async () => {
    window.history.replaceState({}, '', '/user/login?redirect=%2Fuser');
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith('/studio');
    });
  });

  it('always saves password login as a nonpersistent session', async () => {
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockAuthSessionSave).toHaveBeenCalledWith({
        accessToken: 'app-access-token',
        persistent: false,
      });
    });
  });

  it('uses the studio default when redirect is not a same-origin relative path', async () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?redirect=https%3A%2F%2Funsafe.example%2Fstudio',
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith('/studio');
    });
  });

  it('uses the studio default when redirect begins with a scheme-relative host', async () => {
    window.history.replaceState(
      {},
      '',
      '/user/login?redirect=%2F%2Fevil.example%2Fstudio',
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith('/studio');
    });
  });

  it('commits a token-backed 403 state and enters the protected result without exposing the upstream detail', async () => {
    mockAuthMe.mockRejectedValueOnce(
      new ApiError({ code: 403, msg: 'forbidden' }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    await waitFor(() => {
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith('/studio');
    });
    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: {
        accessDenied?: boolean;
        currentUser?: { id: string };
        verificationFailed?: boolean;
      },
    ) => {
      accessDenied?: boolean;
      currentUser?: { id: string };
      verificationFailed?: boolean;
    };
    expect(
      updateInitialState({
        accessDenied: false,
        currentUser: { id: 'previous-user' },
        verificationFailed: true,
      }),
    ).toEqual({
      accessDenied: true,
      currentUser: undefined,
      verificationFailed: false,
    });
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
    expect(screen.queryByText('forbidden')).not.toBeInTheDocument();
    expect(mockLegacyLogin).not.toHaveBeenCalled();
  });

  it('clears an unavailable account session and keeps the credential error neutral', async () => {
    mockAuthMe.mockRejectedValueOnce(
      new ApiError({ code: 46129, msg: 'account disabled' }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('账号或凭据不正确'),
    ).toBeVisible();
    expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('clears a rejected 401 session instead of relying on the global redirect latch', async () => {
    mockAuthMe.mockRejectedValueOnce(
      new ApiError({ code: 401, msg: 'expired token' }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('账号或凭据不正确'),
    ).toBeVisible();
    expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('clears a network-failed verification and remains on the login page', async () => {
    mockAuthMe.mockRejectedValueOnce(new Error('connection reset'));
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('网络连接不可用，请检查网络后重试。'),
    ).toBeVisible();
    expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('clears the session and blocks recovery when me returns no user at runtime', async () => {
    mockAuthMe.mockResolvedValueOnce(undefined);
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('登录状态验证失败，请稍后重试。'),
    ).toBeVisible();
    expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('uses one credential error message without exposing the upstream failure detail', async () => {
    mockAuthLogin.mockRejectedValueOnce(
      new ApiError({
        code: 46128,
        msg: 'creator@example.com does not exist',
      }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('账号或凭据不正确'),
    ).toBeVisible();
    expect(
      screen.queryByText('creator@example.com does not exist'),
    ).not.toBeInTheDocument();
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
  });

  it('keeps an existing app session when an unauthenticated login request reports an unavailable account', async () => {
    mockAuthLogin.mockRejectedValueOnce(
      new ApiError({ code: 46129, msg: 'account disabled' }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('账号或凭据不正确'),
    ).toBeVisible();
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
    expect(mockAuthMe).not.toHaveBeenCalled();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('shows a controlled client-unavailable state', async () => {
    mockAuthLogin.mockRejectedValueOnce(
      new ApiError({ code: 46130, msg: 'client disabled' }),
    );
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '登录客户端不可用',
    );
  });

  it('shows a controlled network-failure state', async () => {
    mockAuthLogin.mockRejectedValueOnce(new Error('connection reset'));
    renderLogin();
    fillCredentials();

    submitLogin();

    expect(
      await screen.findByText('网络连接不可用，请检查网络后重试。'),
    ).toBeVisible();
  });

  it('uses the creator auth logout API from the account menu', async () => {
    render(<AvatarDropdown>用户菜单</AvatarDropdown>);

    fireEvent.click(screen.getByRole('button', { name: '触发退出' }));

    await waitFor(() => {
      expect(mockAuthLogout).toHaveBeenCalledTimes(1);
    });
    expect(mockLegacyLogout).not.toHaveBeenCalled();
  });

  it('clears a stale access-denied state when the user logs out', async () => {
    render(<AvatarDropdown>用户菜单</AvatarDropdown>);

    fireEvent.click(screen.getByRole('button', { name: '触发退出' }));

    await waitFor(() => {
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
    });

    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: { accessDenied?: boolean; currentUser?: { id: string } },
    ) => { accessDenied?: boolean; currentUser?: { id: string } };
    expect(
      updateInitialState({
        accessDenied: true,
        currentUser: { id: 'app-user-001' },
      }),
    ).toEqual({
      accessDenied: false,
      currentUser: undefined,
    });
  });

  it('keeps account security unavailable while verified user state is loading', () => {
    window.history.replaceState({}, '', '/user/security?tab=sessions#active');
    mockGetAccessToken.mockReturnValue('unverified-app-token');
    mockUseModel.mockReturnValue({
      initialState: undefined,
      loading: true,
      setInitialState: mockSetInitialState,
    });

    render(<SecurityPlaceholder />);

    expect(screen.getByRole('status')).toHaveTextContent('正在验证登录状态');
    expect(mockHistoryReplace).not.toHaveBeenCalled();
    expect(
      screen.queryByRole('heading', { name: '账号安全' }),
    ).not.toBeInTheDocument();
  });

  it('redirects a raw-token security session to login without exposing protected content', async () => {
    window.history.replaceState({}, '', '/user/security?tab=sessions#active');
    mockGetAccessToken.mockReturnValue('unverified-app-token');
    mockUseModel.mockReturnValue({
      initialState: { currentUser: undefined },
      loading: false,
      setInitialState: mockSetInitialState,
    });

    render(<SecurityPlaceholder />);

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?redirect=%2Fuser%2Fsecurity%3Ftab%3Dsessions%23active',
      );
    });
    expect(
      screen.queryByText('尚无法验证当前登录状态或没有访问权限。'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: '账号安全' }),
    ).not.toBeInTheDocument();
  });

  it('redirects the security page to login when neither user nor token exists', async () => {
    window.history.replaceState({}, '', '/user/security');
    mockGetAccessToken.mockReturnValue(undefined);
    mockUseModel.mockReturnValue({
      initialState: { currentUser: undefined },
      setInitialState: mockSetInitialState,
    });

    render(<SecurityPlaceholder />);

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?redirect=%2Fuser%2Fsecurity',
      );
    });
    expect(
      screen.queryByRole('heading', { name: '账号安全' }),
    ).not.toBeInTheDocument();
  });
});
