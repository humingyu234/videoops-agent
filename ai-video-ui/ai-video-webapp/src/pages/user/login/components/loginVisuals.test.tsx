import { act, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { LoginFeedback } from './LoginFeedback';
import { LoginSceneBackdrop } from './LoginSceneBackdrop';
import { WechatQrConstructionPanel } from './WechatQrConstructionPanel';

function installMatchMedia(matches: boolean) {
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: vi.fn(
      (query: string): MediaQueryList => ({
        addEventListener: vi.fn(),
        addListener: vi.fn(),
        dispatchEvent: vi.fn(),
        matches,
        media: query,
        onchange: null,
        removeEventListener: vi.fn(),
        removeListener: vi.fn(),
      }),
    ),
    writable: true,
  });
}

describe('login visual primitives', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    installMatchMedia(false);
  });

  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('cycles creation scenes every six seconds and restarts after manual selection', () => {
    render(<LoginSceneBackdrop />);

    const modelButton = screen.getByRole('button', {
      name: '切换到AI 数字人场景',
    });
    const liveButton = screen.getByRole('button', {
      name: '切换到直播克隆场景',
    });
    const voiceButton = screen.getByRole('button', {
      name: '切换到语音合成场景',
    });

    expect(modelButton).toHaveAttribute('aria-pressed', 'true');

    act(() => {
      vi.advanceTimersByTime(6_000);
    });
    expect(liveButton).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(voiceButton);
    expect(voiceButton).toHaveAttribute('aria-pressed', 'true');

    act(() => {
      vi.advanceTimersByTime(5_999);
    });
    expect(voiceButton).toHaveAttribute('aria-pressed', 'true');

    act(() => {
      vi.advanceTimersByTime(1);
    });
    expect(modelButton).toHaveAttribute('aria-pressed', 'true');
  });

  it('stops automatic cycling for reduced motion while keeping manual controls active', () => {
    installMatchMedia(true);
    render(<LoginSceneBackdrop />);

    const modelButton = screen.getByRole('button', {
      name: '切换到AI 数字人场景',
    });
    const liveButton = screen.getByRole('button', {
      name: '切换到直播克隆场景',
    });

    act(() => {
      vi.advanceTimersByTime(12_000);
    });
    expect(modelButton).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(liveButton);
    expect(liveButton).toHaveAttribute('aria-pressed', 'true');
  });

  it('renders a deterministic non-interactive WeChat construction placeholder', () => {
    const { container } = render(<WechatQrConstructionPanel />);

    expect(
      screen.getByRole('status', { name: '微信扫码登录建设中' }),
    ).toHaveTextContent('建设中');
    expect(screen.getByText('微信')).toBeVisible();
    expect(
      container.querySelector('[data-qr-placeholder][aria-hidden="true"]'),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
    expect(screen.queryByText('钉钉')).not.toBeInTheDocument();
  });

  it('switches from credential failure alert to password-changed status', () => {
    const { rerender } = render(<LoginFeedback failure="credentials" />);

    expect(screen.getByRole('alert')).toHaveTextContent('账号或凭据不正确');

    rerender(<LoginFeedback notice="password-changed" />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(
      '密码修改成功，请使用新密码重新登录',
    );

    act(() => {
      vi.advanceTimersByTime(2_600);
    });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
