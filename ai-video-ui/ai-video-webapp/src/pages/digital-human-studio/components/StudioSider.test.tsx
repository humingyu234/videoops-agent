import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { AuthUser } from '@/services/ai-video/auth/types';
import type { PersonalQuotaQueryState } from '../usePersonalQuotaAccount';
import StudioSider from './StudioSider';

const user: AuthUser = {
  displayName: '张三',
  id: 'app-user-001',
  username: 'zhangsan',
  workspace: { id: 'ignored', name: '不应展示的工作区' },
};

function renderSider(
  quotaState: PersonalQuotaQueryState,
  onRetryQuota = vi.fn(),
) {
  render(
    <StudioSider
      activeKey="create"
      collapsed={false}
      currentUser={user}
      quotaState={quotaState}
      onCollapsedChange={vi.fn()}
      onDiscover={vi.fn()}
      onRetryQuota={onRetryQuota}
      onRouteChange={vi.fn()}
    />,
  );

  return { onRetryQuota };
}

describe('StudioSider personal profile and quota', () => {
  it('只把当前 Studio 内容区标记为当前页', () => {
    renderSider({ status: 'loading' });

    expect(screen.getByRole('button', { name: /创作$/ })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.getByRole('button', { name: /声音$/ })).not.toHaveAttribute(
      'aria-current',
    );
  });

  it('shows the verified personal profile without organization or workspace data', () => {
    renderSider({ status: 'loading' });

    expect(screen.getByText('张三')).toBeVisible();
    expect(screen.getByText('zhangsan')).toBeVisible();
    expect(screen.getByText('张')).toBeVisible();
    expect(screen.queryByText('不应展示的工作区')).not.toBeInTheDocument();
    expect(screen.queryByText('北辰内容工作室')).not.toBeInTheDocument();
  });

  it('falls back to username and does not duplicate it on the second line', () => {
    render(
      <StudioSider
        activeKey="create"
        collapsed={false}
        currentUser={{ id: 'app-user-002', username: 'creator' }}
        quotaState={{ status: 'loading' }}
        onCollapsedChange={vi.fn()}
        onDiscover={vi.fn()}
        onRetryQuota={vi.fn()}
        onRouteChange={vi.fn()}
      />,
    );

    expect(screen.getAllByText('creator')).toHaveLength(1);
    expect(screen.getByText('c')).toBeVisible();
  });

  it('keeps placeholders while the quota request is loading', () => {
    renderSider({ status: 'loading' });

    expect(screen.getByLabelText('个人积分加载中')).toHaveAttribute(
      'aria-busy',
      'true',
    );
    expect(screen.getAllByText('--').length).toBeGreaterThanOrEqual(2);
    expect(screen.queryByText('12,800')).not.toBeInTheDocument();
  });

  it('renders large balances and percentage using exact integer strings', () => {
    renderSider({
      account: {
        quotaUnit: 'ai_text_credit',
        availableBalance: '900719925474099312345',
        lockedBalance: '7',
        usedBalance: '11',
        totalBalance: '900719925474099312352',
      },
      status: 'success',
    });

    expect(screen.getByText('900,719,925,474,099,312,352')).toBeVisible();
    expect(screen.getByText('900,719,925,474,099,312,345')).toBeVisible();
    expect(screen.getByText('已用积分 11')).toBeVisible();
    expect(screen.queryByText(/冻结积分/)).not.toBeInTheDocument();
    expect(screen.getByText('99%')).toBeVisible();
    expect(screen.queryByText(/本月已用/)).not.toBeInTheDocument();
  });

  it('handles a zero total without dividing by zero', () => {
    renderSider({
      account: {
        quotaUnit: 'ai_text_credit',
        availableBalance: '0',
        lockedBalance: '0',
        usedBalance: '0',
        totalBalance: '0',
      },
      status: 'success',
    });

    expect(screen.getByText('0%')).toBeVisible();
  });

  it.each([
    ['missing', '积分账户不存在，请联系管理员', true],
    ['forbidden', '无权查看积分', false],
    ['failed', '积分加载失败', true],
  ] as const)('shows the %s quota state and only offers retry when allowed', (status, message, retryable) => {
    const { onRetryQuota } = renderSider({ status });

    expect(screen.getByText(message)).toBeVisible();
    const retry = screen.queryByRole('button', { name: '重试' });
    if (retryable) {
      if (!retry) throw new Error('Expected a retry button');
      fireEvent.click(retry);
      expect(onRetryQuota).toHaveBeenCalledTimes(1);
    } else {
      expect(retry).not.toBeInTheDocument();
    }
    expect(screen.getByText('张三')).toBeVisible();
  });
});
