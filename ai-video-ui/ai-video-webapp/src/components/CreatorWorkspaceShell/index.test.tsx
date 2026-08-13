import { fireEvent, render, screen, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import CreatorWorkspaceShell from './index';

const { mockHistoryPush, mockUsePersonalQuotaAccount } = vi.hoisted(() => ({
  mockHistoryPush: vi.fn(),
  mockUsePersonalQuotaAccount: vi.fn(() => ({
    retry: vi.fn(),
    state: { status: 'loading' as const },
  })),
}));

const user = {
  avatarUrl: undefined,
  displayName: '创作者小素',
  id: 'user-001',
};

vi.mock('@umijs/max', () => ({
  history: { push: mockHistoryPush },
  Link: ({ children, to, ...props }: { children: ReactNode; to: string }) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
  useModel: () => ({ initialState: { currentUser: user } }),
}));

vi.mock('@/pages/digital-human-studio/usePersonalQuotaAccount', () => ({
  usePersonalQuotaAccount: mockUsePersonalQuotaAccount,
}));

describe('CreatorWorkspaceShell', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
  });

  it('只复用现有创作台菜单并把页面内容放到右侧', () => {
    const { container } = render(
      <CreatorWorkspaceShell activeKey="discover" title="发现">
        <div>模板内容</div>
      </CreatorWorkspaceShell>,
    );

    const navigation = screen.getByRole('navigation');
    const menuButtons = within(navigation).getAllByRole('button');
    expect(menuButtons.map((button) => button.textContent)).toEqual([
      '创作',
      '形象',
      '声音',
      '文案',
      '作品',
      '发现',
    ]);
    expect(screen.getByRole('button', { name: /发现$/ })).toHaveAttribute(
      'aria-current',
      'page',
    );
    expect(screen.queryByRole('link', { name: '创作台' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '任务中心' })).not.toBeInTheDocument();
    expect(screen.getByText('创作者小素')).toBeInTheDocument();
    expect(screen.getByText('模板内容')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { level: 1, name: '发现' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('main')).toContainElement(
      screen.getByText('模板内容'),
    );
    expect(container.querySelectorAll('.nav-item.active')).toHaveLength(1);
    expect(container.firstElementChild).toHaveClass('studio-shell', 'app');
  });

  it('点击现有菜单时进入对应的创作台内容', () => {
    render(
      <CreatorWorkspaceShell activeKey="discover">模板内容</CreatorWorkspaceShell>,
    );

    fireEvent.click(screen.getByRole('button', { name: /声音$/ }));

    expect(mockHistoryPush).toHaveBeenCalledWith('/studio?view=voices');
  });

  it('沿用现有键保存侧栏折叠状态', () => {
    const { container } = render(
      <CreatorWorkspaceShell activeKey="tasks">任务列表</CreatorWorkspaceShell>,
    );

    fireEvent.click(screen.getByRole('button', { name: '收起菜单' }));

    expect(localStorage.setItem).toHaveBeenCalledWith(
      'dh-sidebar-collapsed',
      '1',
    );
    expect(container.firstElementChild).toHaveClass('collapsed');
  });
});
