import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ComponentProps } from 'react';
import { ApiError } from '@/services/ai-video/core/errors';
import SecurityPage from './index';

const {
  mockAuthSessionClear,
  mockBeginLoginRedirect,
  mockChangePassword,
  mockFormOnFinish,
  mockHistoryReplace,
  mockRevokeSession,
  mockSetInitialState,
  mockSessions,
  mockUseModel,
} = vi.hoisted(() => ({
  mockAuthSessionClear: vi.fn(),
  mockBeginLoginRedirect: vi.fn(),
  mockChangePassword: vi.fn(),
  mockFormOnFinish: vi.fn(),
  mockHistoryReplace: vi.fn(),
  mockRevokeSession: vi.fn(),
  mockSetInitialState: vi.fn(),
  mockSessions: vi.fn(),
  mockUseModel: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: {
    replace: mockHistoryReplace,
  },
  useModel: mockUseModel,
}));

vi.mock('antd', async () => {
  const React = await import('react');
  const actual = await vi.importActual<typeof import('antd')>('antd');
  const Form = Object.assign(
    (props: ComponentProps<typeof actual.Form>) => {
      mockFormOnFinish(props.onFinish);
      return React.createElement(actual.Form, props);
    },
    { Item: actual.Form.Item },
  );

  return {
    ...actual,
    Form,
  };
});

vi.mock('@/services/ai-video/auth/api', () => ({
  authApi: {
    changePassword: mockChangePassword,
    revokeSession: mockRevokeSession,
    sessions: mockSessions,
  },
}));

vi.mock('@/services/ai-video/auth/session', () => ({
  authSession: {
    clear: mockAuthSessionClear,
  },
  beginLoginRedirect: mockBeginLoginRedirect,
}));

const verifiedUser = {
  email: 'creator@example.com',
  id: 'creator-1',
  passwordResetRequired: true,
  phone: '13812345678',
  username: 'creator_test',
};

const securitySessions = [
  {
    clientId: 'creator-web',
    current: true,
    deviceName: '网页端',
    id: '9d4cf756-5a8b-424d-86e6-ae4a75ffad8d',
    lastActiveAt: '2026-07-30T10:15:00',
  },
  {
    clientId: 'creator-desktop',
    current: false,
    deviceName: '桌面端',
    id: '7c5d830d-b460-4b3d-b8ad-c3a1e8e2a4ed',
    lastActiveAt: '2026-07-30T09:45:00',
  },
];

function renderSecurityPage(options?: {
  currentUser?: typeof verifiedUser;
  loading?: boolean;
}) {
  mockUseModel.mockReturnValue({
    initialState: { currentUser: options?.currentUser ?? verifiedUser },
    loading: options?.loading ?? false,
    setInitialState: mockSetInitialState,
  });

  return render(<SecurityPage />);
}

function fillValidPasswordChangeForm() {
  fireEvent.change(screen.getByLabelText('当前密码'), {
    target: { value: 'CurrentPass1' },
  });
  fireEvent.change(screen.getByLabelText('新密码'), {
    target: { value: 'NewPassword1' },
  });
  fireEvent.change(screen.getByLabelText('确认新密码'), {
    target: { value: 'NewPassword1' },
  });
}

describe('SecurityPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockBeginLoginRedirect.mockReturnValue(true);
    mockChangePassword.mockResolvedValue(undefined);
    mockRevokeSession.mockResolvedValue(undefined);
    mockSessions.mockResolvedValue([]);
    window.history.replaceState({}, '', '/user/security');
  });

  it('在身份验证加载期间不展示受保护内容', () => {
    renderSecurityPage({ loading: true });

    expect(screen.getByRole('status')).toHaveTextContent('正在验证登录状态');
    expect(document.querySelector('.ant-spin')).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: '账号安全' }),
    ).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('没有已验证用户时保留当前地址并跳转登录页', async () => {
    window.history.replaceState({}, '', '/user/security?tab=password#form');
    mockUseModel.mockReturnValue({
      initialState: { currentUser: undefined },
      loading: false,
      setInitialState: mockSetInitialState,
    });

    render(<SecurityPage />);

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?redirect=%2Fuser%2Fsecurity%3Ftab%3Dpassword%23form',
      );
    });
    expect(
      screen.queryByRole('heading', { name: '账号安全' }),
    ).not.toBeInTheDocument();
  });

  it('展示已验证账号的脱敏安全信息和强制改密说明', () => {
    renderSecurityPage();

    expect(
      screen.getByRole('heading', { name: '账号安全' }),
    ).toBeVisible();
    expect(screen.getByText('creator_test')).toBeVisible();
    expect(screen.getByText('138****5678')).toBeVisible();
    expect(screen.getByText('c***@example.com')).toBeVisible();
    expect(screen.getByText('需要先修改密码')).toBeVisible();
    expect(
      screen.getByText('当前账号已被要求修改密码，请完成修改后重新登录。'),
    ).toBeVisible();
  });

  it('在调用接口前验证新密码强度和确认密码一致性', async () => {
    renderSecurityPage();
    fireEvent.change(screen.getByLabelText('当前密码'), {
      target: { value: 'CurrentPass1' },
    });
    fireEvent.change(screen.getByLabelText('新密码'), {
      target: { value: 'short1' },
    });
    fireEvent.change(screen.getByLabelText('确认新密码'), {
      target: { value: 'different1' },
    });

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));

    expect(
      await screen.findByText('新密码至少 8 位，且必须同时包含字母和数字'),
    ).toBeInTheDocument();
    expect(await screen.findByText('两次输入的新密码不一致')).toBeInTheDocument();
    expect(mockChangePassword).not.toHaveBeenCalled();
  });

  it('提交时仅发送线上契约字段，禁用重复提交，成功后清理身份并跳转登录', async () => {
    let resolveChangePassword: (() => void) | undefined;
    mockChangePassword.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveChangePassword = resolve;
        }),
    );
    renderSecurityPage();
    fillValidPasswordChangeForm();

    const submitButton = screen.getByRole('button', { name: '修改密码' });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockChangePassword).toHaveBeenCalledWith({
        currentPassword: 'CurrentPass1',
        newPassword: 'NewPassword1',
      });
    });
    expect(submitButton).toBeDisabled();
    fireEvent.click(submitButton);
    expect(mockChangePassword).toHaveBeenCalledTimes(1);

    resolveChangePassword?.();

    await waitFor(() => {
      expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?notice=password-changed',
      );
    });
    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: { accessDenied?: boolean; currentUser?: typeof verifiedUser },
    ) => { accessDenied?: boolean; currentUser?: typeof verifiedUser };
    expect(
      updateInitialState({
        accessDenied: true,
        currentUser: verifiedUser,
      }),
    ).toEqual({
      accessDenied: false,
      currentUser: undefined,
    });
    expect(
      screen.queryByText('密码修改成功，请使用新密码重新登录'),
    ).not.toBeInTheDocument();
  });

  it('在同一表单的连续两次 submit 中仅发起一次改密请求', async () => {
    let resolveChangePassword: (() => void) | undefined;
    mockChangePassword.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveChangePassword = resolve;
        }),
    );
    const { container } = renderSecurityPage();
    fillValidPasswordChangeForm();

    const passwordChangeForm = container.querySelector('form');
    if (!passwordChangeForm) {
      throw new Error('password change form is unavailable');
    }
    const onFinish = mockFormOnFinish.mock.calls.at(-1)?.[0];
    if (typeof onFinish !== 'function') {
      throw new Error('password change submit handler is unavailable');
    }

    const onFormSubmit = (event: Event) => {
      event.preventDefault();
      event.stopPropagation();
      onFinish({
        confirmPassword: 'NewPassword1',
        currentPassword: 'CurrentPass1',
        newPassword: 'NewPassword1',
      });
    };
    passwordChangeForm.addEventListener('submit', onFormSubmit);

    act(() => {
      passwordChangeForm.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      );
      passwordChangeForm.dispatchEvent(
        new Event('submit', { bubbles: true, cancelable: true }),
      );
    });

    try {
      await waitFor(() => {
        expect(mockChangePassword).toHaveBeenCalledTimes(1);
      });
    } finally {
      passwordChangeForm.removeEventListener('submit', onFormSubmit);
      await act(async () => {
        resolveChangePassword?.();
        await Promise.resolve();
      });
    }
  });

  it('普通后端失败使用确定中文反馈且不泄漏后端信息', async () => {
    mockChangePassword.mockRejectedValueOnce(
      new ApiError({ code: 500, msg: 'current password is secret detail' }),
    );
    renderSecurityPage();
    fillValidPasswordChangeForm();

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));

    expect(
      await screen.findByText('修改密码失败，请检查当前密码后重试'),
    ).toBeInTheDocument();
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
    expect(
      screen.queryByText('current password is secret detail'),
    ).not.toBeInTheDocument();
  });

  it('shows a controlled 403 result for a forbidden password change without leaking the upstream detail', async () => {
    mockChangePassword.mockRejectedValueOnce(
      new ApiError({
        code: 403,
        msg: 'password permission detail',
        status: 403,
      }),
    );
    renderSecurityPage();
    fillValidPasswordChangeForm();

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));

    expect(await screen.findByText('访问受限')).toBeVisible();
    expect(
      screen.queryByText('password permission detail'),
    ).not.toBeInTheDocument();
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('会话修订已过期时清理身份并要求重新登录', async () => {
    mockChangePassword.mockRejectedValueOnce(
      new ApiError({ code: 46131, msg: 'stale revision' }),
    );
    renderSecurityPage();
    fillValidPasswordChangeForm();

    fireEvent.click(screen.getByRole('button', { name: '修改密码' }));

    await waitFor(() => {
      expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith('/user/login');
    });
    expect(
      await screen.findByText('登录状态已失效，请重新登录'),
    ).toBeInTheDocument();
  });

  it('加载并展示当前账号的安全会话，不展示令牌等敏感字段', async () => {
    mockSessions.mockResolvedValueOnce(securitySessions);

    renderSecurityPage();

    expect(await screen.findByText('网页端')).toBeVisible();
    expect(screen.getByText('桌面端')).toBeVisible();
    expect(screen.getByText('当前设备')).toBeVisible();
    expect(screen.getByText('客户端：creator-web')).toBeVisible();
    expect(screen.getByText('客户端：creator-desktop')).toBeVisible();
    expect(screen.getByRole('button', { name: '退出此设备' })).toBeVisible();
    expect(screen.getByRole('button', { name: '撤销会话' })).toBeVisible();
    expect(mockSessions).toHaveBeenCalledTimes(1);
  });

  it('会话加载失败时提供确定中文错误和重试，不泄露后端详情', async () => {
    mockSessions.mockRejectedValueOnce(
      new ApiError({ code: 500, msg: 'internal session detail' }),
    );
    mockSessions.mockResolvedValueOnce(securitySessions);

    renderSecurityPage();

    expect(
      await screen.findByText('会话列表加载失败，请稍后重试'),
    ).toBeVisible();
    expect(screen.queryByText('internal session detail')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '重新加载会话' }));

    expect(await screen.findByText('网页端')).toBeVisible();
    expect(mockSessions).toHaveBeenCalledTimes(2);
  });

  it('加载会话时使用骨架屏并保持受保护内容可见', () => {
    mockSessions.mockImplementation(
      () => new Promise<never>(() => undefined),
    );

    renderSecurityPage();

    expect(screen.getByRole('status', { name: '正在加载登录会话' })).toBeVisible();
    expect(document.querySelector('.ant-skeleton')).toBeInTheDocument();
    expect(screen.getByText('creator_test')).toBeVisible();
  });

  it('会话列表为空时展示确定的空状态', async () => {
    renderSecurityPage();

    expect(
      await screen.findByText('当前没有可管理的登录会话'),
    ).toBeVisible();
  });

  it('会话加载时发现登录已失效会清理身份并跳转登录', async () => {
    mockSessions.mockRejectedValueOnce(
      new ApiError({ code: 46131, msg: 'stale revision' }),
    );

    renderSecurityPage();

    await waitFor(() => {
      expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith('/user/login');
    });
  });

  it('会话接口返回 403 时展示权限不足结果而不跳转登录', async () => {
    mockSessions.mockRejectedValueOnce(
      new ApiError({ code: 403, msg: 'session permission detail', status: 403 }),
    );

    renderSecurityPage();

    expect(await screen.findByText('访问受限')).toBeVisible();
    expect(screen.queryByText('session permission detail')).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('撤销其他设备前要求确认，成功后刷新会话列表', async () => {
    mockSessions.mockResolvedValueOnce(securitySessions);
    mockSessions.mockResolvedValueOnce([securitySessions[0]]);

    renderSecurityPage();

    fireEvent.click(await screen.findByRole('button', { name: '撤销会话' }));
    const confirmButton = (await screen.findAllByRole('button', {
      name: '确认撤销',
    })).at(-1);
    if (!confirmButton) {
      throw new Error('session revocation confirmation is unavailable');
    }
    fireEvent.click(confirmButton);

    await waitFor(() => {
      expect(mockRevokeSession).toHaveBeenCalledWith(securitySessions[1].id);
      expect(mockSessions).toHaveBeenCalledTimes(2);
    });
    expect(screen.queryByText('桌面端')).not.toBeInTheDocument();
    expect(mockAuthSessionClear).not.toHaveBeenCalled();
  });

  it('撤销当前设备成功后清理身份并回到登录页', async () => {
    mockSessions.mockResolvedValueOnce(securitySessions);

    renderSecurityPage();

    fireEvent.click(await screen.findByRole('button', { name: '退出此设备' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认退出' }));

    await waitFor(() => {
      expect(mockRevokeSession).toHaveBeenCalledWith(securitySessions[0].id);
      expect(mockAuthSessionClear).toHaveBeenCalledTimes(1);
      expect(mockSetInitialState).toHaveBeenCalledTimes(1);
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?notice=session-revoked',
      );
    });
    const updateInitialState = mockSetInitialState.mock.calls[0]?.[0] as (
      state: { accessDenied?: boolean; currentUser?: typeof verifiedUser },
    ) => { accessDenied?: boolean; currentUser?: typeof verifiedUser };
    expect(
      updateInitialState({
        accessDenied: true,
        currentUser: verifiedUser,
      }),
    ).toEqual({
      accessDenied: false,
      currentUser: undefined,
    });
  });

  it('撤销会话返回 403 时展示权限不足结果，不泄露后端详情', async () => {
    mockSessions.mockResolvedValueOnce(securitySessions);
    mockRevokeSession.mockRejectedValueOnce(
      new ApiError({ code: 403, msg: 'revoke permission detail', status: 403 }),
    );

    renderSecurityPage();

    fireEvent.click(await screen.findByRole('button', { name: '撤销会话' }));
    const confirmButton = (await screen.findAllByRole('button', {
      name: '确认撤销',
    })).at(-1);
    if (!confirmButton) {
      throw new Error('session revocation confirmation is unavailable');
    }
    fireEvent.click(confirmButton);

    expect(await screen.findByText('访问受限')).toBeVisible();
    expect(screen.queryByText('revoke permission detail')).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('撤销会话发生普通失败时只展示固定中文反馈', async () => {
    mockSessions.mockResolvedValueOnce(securitySessions);
    mockRevokeSession.mockRejectedValueOnce(
      new ApiError({ code: 500, msg: 'revoke internal detail' }),
    );

    renderSecurityPage();

    fireEvent.click(await screen.findByRole('button', { name: '撤销会话' }));
    const confirmButton = (await screen.findAllByRole('button', {
      name: '确认撤销',
    })).at(-1);
    if (!confirmButton) {
      throw new Error('session revocation confirmation is unavailable');
    }
    fireEvent.click(confirmButton);

    expect(
      await screen.findByText('撤销会话失败，请稍后重试'),
    ).toBeInTheDocument();
    expect(screen.queryByText('revoke internal detail')).not.toBeInTheDocument();
  });

  it('同一会话的确认撤销连续触发时只发送一次请求', async () => {
    let resolveRevocation: (() => void) | undefined;
    mockSessions.mockResolvedValueOnce(securitySessions);
    mockRevokeSession.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveRevocation = resolve;
        }),
    );

    renderSecurityPage();

    fireEvent.click(await screen.findByRole('button', { name: '撤销会话' }));
    const confirmButton = (await screen.findAllByRole('button', {
      name: '确认撤销',
    })).at(-1);
    if (!confirmButton) {
      throw new Error('session revocation confirmation is unavailable');
    }
    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);

    try {
      await waitFor(() => {
        expect(mockRevokeSession).toHaveBeenCalledTimes(1);
      });
    } finally {
      await act(async () => {
        resolveRevocation?.();
        await Promise.resolve();
      });
    }
  });
});
