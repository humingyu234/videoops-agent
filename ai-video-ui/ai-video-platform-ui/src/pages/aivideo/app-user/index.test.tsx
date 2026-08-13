import type { ReactNode } from 'react';
import { App } from 'antd';
import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useUserStore } from '@/stores/userStore';
import AppUserPage from './index';

vi.mock('@ant-design/pro-components', () => ({
  ModalForm: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  PageContainer: ({ children }: { children: ReactNode }) => <section>{children}</section>,
  ProFormSelect: () => null,
  ProTable: ({ toolBarRender }: { toolBarRender?: () => ReactNode[] }) => <div>{toolBarRender?.()}</div>
}));

vi.mock('@/api/aivideo/identity', () => ({
  pageAppUsers: vi.fn(async () => ({ data: [], total: 0, success: true })),
  getAppUser: vi.fn(),
  createAppUser: vi.fn(),
  updateAppUser: vi.fn(),
  changeAppUserStatus: vi.fn(),
  resetAppUserPassword: vi.fn(),
  kickoutAppUser: vi.fn(),
  replaceAppUserRoles: vi.fn(),
  pageAppRoles: vi.fn(async () => ({ data: [], total: 0, success: true }))
}));

vi.mock('./components/AppUserFormModal', () => ({
  default: () => null
}));

vi.mock('./components/AppUserSecurityDrawer', () => ({
  default: () => null
}));

describe('创作端用户管理页', () => {
  beforeEach(() => {
    useUserStore.getState().setUserInfo({
      permissions: ['aivideo:app-user:query'],
      roles: [],
      user: { userId: '100', userName: 'operator' }
    });
  });

  it('没有精确运营权限时隐藏写操作，且页面不存在冒充或令牌签发入口', () => {
    const { container } = render(
      <App>
        <AppUserPage />
      </App>
    );

    expect(screen.queryByRole('button', { name: '新增' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '修改' })).not.toBeInTheDocument();
    expect(container).not.toHaveTextContent('冒充');
    expect(container).not.toHaveTextContent('以用户身份登录');
    expect(container).not.toHaveTextContent('签发令牌');
  });
});
