import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ApiError } from '@/services/ai-video/core/errors';
import PasswordResetPage from './index';

const {
  mockHistoryReplace,
  mockRequestVerificationCode,
  mockResetPassword,
} = vi.hoisted(() => ({
  mockHistoryReplace: vi.fn(),
  mockRequestVerificationCode: vi.fn(),
  mockResetPassword: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => (
    <a href={to}>{children}</a>
  ),
  history: {
    replace: mockHistoryReplace,
  },
}));

vi.mock('@/components', () => ({
  Footer: () => <footer>AI 视频工作台</footer>,
}));

vi.mock('@/services/ai-video/auth/api', () => ({
  authApi: {
    requestVerificationCode: mockRequestVerificationCode,
    resetPassword: mockResetPassword,
  },
}));

function requestCode() {
  fireEvent.change(screen.getByLabelText('手机号'), {
    target: { value: '13812345678' },
  });
  fireEvent.click(screen.getByRole('button', { name: '获取验证码' }));
}

function completeRecoveryForm() {
  fireEvent.change(screen.getByLabelText('验证码'), {
    target: { value: '123456' },
  });
  fireEvent.change(screen.getByLabelText('新密码'), {
    target: { value: 'NewPassword1' },
  });
  fireEvent.change(screen.getByLabelText('确认新密码'), {
    target: { value: 'NewPassword1' },
  });
}

describe('PasswordResetPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockRequestVerificationCode.mockResolvedValue({
      challenge_id: 'opaque-recovery-challenge',
      expires_in: 600,
      masked_target: '138****5678',
    });
    mockResetPassword.mockResolvedValue(undefined);
    window.history.replaceState({}, '', '/user/password-reset');
  });

  it('uses the password recovery scenario and only displays the masked challenge target', async () => {
    render(<PasswordResetPage />);

    requestCode();

    await waitFor(() => {
      expect(mockRequestVerificationCode).toHaveBeenCalledWith({
        channel: 'PHONE',
        scenario: 'PASSWORD_RECOVERY',
        target: '13812345678',
      });
    });
    expect(await screen.findByText('验证码请求已提交')).toBeVisible();
    expect(screen.getByText('138****5678')).toBeVisible();
    expect(screen.queryByText('13812345678')).not.toBeInTheDocument();
    expect(screen.queryByText('注册')).not.toBeInTheDocument();
  });

  it('submits only the frozen reset fields once and returns to login after success', async () => {
    render(<PasswordResetPage />);

    requestCode();
    await screen.findByText('验证码请求已提交');
    completeRecoveryForm();
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));

    await waitFor(() => {
      expect(mockResetPassword).toHaveBeenCalledWith({
        challengeId: 'opaque-recovery-challenge',
        newPassword: 'NewPassword1',
        verificationCode: '123456',
      });
      expect(mockHistoryReplace).toHaveBeenCalledWith('/user/login');
    });
    expect(mockResetPassword).toHaveBeenCalledTimes(1);
  });

  it('clears the challenge when switching channels and requires a new verification code', async () => {
    render(<PasswordResetPage />);

    requestCode();
    await screen.findByText('验证码请求已提交');
    fireEvent.click(screen.getByRole('button', { name: '邮箱找回' }));

    expect(screen.getByLabelText('邮箱')).toBeVisible();
    fireEvent.change(screen.getByLabelText('邮箱'), {
      target: { value: 'creator@example.com' },
    });
    completeRecoveryForm();
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));

    expect(await screen.findByText('请先获取验证码')).toBeVisible();
    expect(mockResetPassword).not.toHaveBeenCalled();
  });

  it('invalidates the old challenge feedback when the recovery target changes', async () => {
    render(<PasswordResetPage />);

    requestCode();
    await screen.findByText('验证码请求已提交');
    fireEvent.change(screen.getByLabelText('手机号'), {
      target: { value: '13912345678' },
    });

    expect(screen.queryByText('验证码请求已提交')).not.toBeInTheDocument();
    completeRecoveryForm();
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));

    expect(await screen.findByText('请先获取验证码')).toBeVisible();
    expect(mockResetPassword).not.toHaveBeenCalled();
  });

  it('uses a fixed recovery failure message without displaying the upstream detail', async () => {
    mockResetPassword.mockRejectedValueOnce(
      new ApiError({ code: 46128, msg: 'verification detail must not leak' }),
    );
    render(<PasswordResetPage />);

    requestCode();
    await screen.findByText('验证码请求已提交');
    completeRecoveryForm();
    fireEvent.click(screen.getByRole('button', { name: '重置密码' }));

    expect(
      await screen.findByText('验证码或账号信息不正确，请重新获取验证码后重试'),
    ).toBeVisible();
    expect(
      screen.queryByText('verification detail must not leak'),
    ).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });
});
