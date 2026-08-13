import type { ReactNode } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createRunningHubAccount,
  deleteRunningHubAccount,
  disableRunningHubAccount,
  enableRunningHubAccount,
  pageRunningHubAccounts,
  updateRunningHubAccount
} from '@/api/aivideo/runninghub-account';
import { useUserStore } from '@/stores/userStore';
import {
  buildRunningHubAccountFormValues,
  toCreateRunningHubAccountInput,
  toUpdateRunningHubAccountInput
} from './components/accountFormModel';
import RunningHubAccountPage from './index';

interface TableHarness {
  request?: (params: { current?: number; pageSize?: number }) => Promise<unknown>;
}

const tableHarness = vi.hoisted<TableHarness>(() => ({}));
const apiMocks = vi.hoisted(() => ({
  createRunningHubAccount: vi.fn(),
  deleteRunningHubAccount: vi.fn(),
  disableRunningHubAccount: vi.fn(),
  enableRunningHubAccount: vi.fn(),
  getRunningHubAccount: vi.fn(),
  pageRunningHubAccounts: vi.fn(async () => ({ data: [], success: true, total: 0 })),
  updateRunningHubAccount: vi.fn()
}));

const enabledRow = {
  accountId: '91',
  accountName: '主账号',
  apiKeyMasked: 'rh_****1234',
  enabled: true,
  hasApiKey: true,
  rowRevision: 3
};

vi.mock('@/api/aivideo/runninghub-account', () => apiMocks);

vi.mock('@/components/common/RowActions', () => ({
  default: ({
    actions
  }: {
    actions: Array<
      false | null | undefined | { key: string; label: string; confirm?: ReactNode; onClick?: () => void }
    >;
  }) => (
    <div>
      {actions.filter(Boolean).map(action => {
        if (!action) return null;
        return (
          <button key={action.key} data-confirm={String(action.confirm || '')} type="button" onClick={action.onClick}>
            {action.label}
          </button>
        );
      })}
    </div>
  )
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: { children: ReactNode }) => <section>{children}</section>,
  ProTable: ({
    columns,
    pagination,
    request,
    toolBarRender
  }: {
    columns: Array<{ render?: (node: unknown, row: typeof enabledRow) => ReactNode; valueType?: string }>;
    pagination?: object;
    request: TableHarness['request'];
    toolBarRender?: () => ReactNode[];
  }) => {
    tableHarness.request = request;
    const option = columns.find(column => column.valueType === 'option');
    return (
      <div>
        <span data-testid="pagination">{JSON.stringify(pagination)}</span>
        {toolBarRender?.()}
        {option?.render?.(null, enabledRow)}
        {option?.render?.(null, { ...enabledRow, accountId: '92', accountName: '备用账号', enabled: false })}
      </div>
    );
  }
}));

vi.mock('./components/RunningHubAccountFormModal', () => ({
  default: ({
    detail,
    open,
    readonly,
    submitting,
    onFinish
  }: {
    detail?: typeof enabledRow;
    open: boolean;
    readonly?: boolean;
    submitting: boolean;
    onFinish: (values: { accountName: string; apiKey?: string }) => Promise<boolean>;
  }) =>
    open ? (
      <div
        data-testid="account-form"
        data-account-name={detail?.accountName}
        data-readonly={String(Boolean(readonly))}
        data-submitting={String(submitting)}
      >
        {!readonly && (
          <button type="button" onClick={() => void onFinish({ accountName: '主账号', apiKey: 'new-secret' })}>
            提交账号
          </button>
        )}
      </div>
    ) : null
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(next => {
    resolve = next;
  });
  return { promise, resolve };
}

function grant(...permissions: string[]) {
  useUserStore.getState().setUserInfo({
    permissions,
    roles: [],
    user: { userId: '100', userName: 'operator' }
  });
}

describe('RunningHub 账号管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tableHarness.request = undefined;
    useUserStore.getState().clearUserInfo();
    apiMocks.createRunningHubAccount.mockResolvedValue('91');
    apiMocks.deleteRunningHubAccount.mockResolvedValue(undefined);
    apiMocks.disableRunningHubAccount.mockResolvedValue(undefined);
    apiMocks.enableRunningHubAccount.mockResolvedValue(undefined);
    apiMocks.getRunningHubAccount.mockResolvedValue(enabledRow);
    apiMocks.updateRunningHubAccount.mockResolvedValue(undefined);
  });

  it('没有查询权限时展示 403', () => {
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    expect(screen.getByText('无权限访问')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增账号' })).not.toBeInTheDocument();
  });

  it('使用 ProTable 分页请求，并让空列表和加载失败保持为标准 request 结果', async () => {
    grant('aivideo:runninghub-account:query');
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    expect(screen.getByTestId('pagination')).toHaveTextContent('"defaultPageSize":10');
    await expect(tableHarness.request?.({ current: 1, pageSize: 10 })).resolves.toEqual({
      data: [],
      success: true,
      total: 0
    });
    expect(pageRunningHubAccounts).toHaveBeenCalledWith({ current: 1, pageSize: 10 });

    apiMocks.pageRunningHubAccounts.mockRejectedValueOnce(new Error('network'));
    await expect(tableHarness.request?.({ current: 2, pageSize: 10 })).rejects.toThrow('network');
  });

  it('仅有查询权限时可查看只读详情，但不能修改或提交', async () => {
    grant('aivideo:runninghub-account:query');
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    expect(screen.queryByRole('button', { name: '修改' })).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: '查看' })[0]);

    await waitFor(() => expect(apiMocks.getRunningHubAccount).toHaveBeenCalledWith('91'));
    expect(await screen.findByTestId('account-form')).toHaveAttribute('data-readonly', 'true');
    expect(screen.getByTestId('account-form')).toHaveAttribute('data-account-name', '主账号');
    expect(screen.queryByRole('button', { name: '提交账号' })).not.toBeInTheDocument();
  });

  it('账号详情加载失败时保持表单关闭', async () => {
    apiMocks.getRunningHubAccount.mockRejectedValueOnce(new Error('detail failed'));
    grant('aivideo:runninghub-account:query');
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    fireEvent.click(screen.getAllByRole('button', { name: '查看' })[0]);

    await waitFor(() => expect(apiMocks.getRunningHubAccount).toHaveBeenCalledWith('91'));
    expect(screen.queryByTestId('account-form')).not.toBeInTheDocument();
  });

  it('编辑权限仍独立打开可提交表单', async () => {
    grant('aivideo:runninghub-account:query', 'aivideo:runninghub-account:edit');
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    fireEvent.click(screen.getAllByRole('button', { name: '修改' })[0]);

    expect(await screen.findByTestId('account-form')).toHaveAttribute('data-readonly', 'false');
    expect(screen.getByRole('button', { name: '提交账号' })).toBeInTheDocument();
  });

  it('按精确权限展示删除和启停操作，并携带明确确认', async () => {
    grant(
      'aivideo:runninghub-account:query',
      'aivideo:runninghub-account:remove',
      'aivideo:runninghub-account:enable',
      'aivideo:runninghub-account:disable'
    );
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    const deleteButtons = screen.getAllByRole('button', { name: '删除' });
    expect(deleteButtons[0]).toHaveAttribute('data-confirm', expect.stringContaining('确认删除'));
    fireEvent.click(deleteButtons[0]);
    fireEvent.click(screen.getByRole('button', { name: '停用' }));
    fireEvent.click(screen.getByRole('button', { name: '启用' }));

    await waitFor(() => expect(deleteRunningHubAccount).toHaveBeenCalledWith('91', 3));
    expect(disableRunningHubAccount).toHaveBeenCalledWith('91', 3);
    expect(enableRunningHubAccount).toHaveBeenCalledWith('92', 3);
  });

  it('创建提交期间保持 submitting，成功后才关闭表单', async () => {
    const create = deferred<string>();
    apiMocks.createRunningHubAccount.mockReturnValue(create.promise);
    grant('aivideo:runninghub-account:query', 'aivideo:runninghub-account:add');
    render(
      <App>
        <RunningHubAccountPage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '新增账号' }));
    fireEvent.click(screen.getByRole('button', { name: '提交账号' }));
    await waitFor(() => expect(createRunningHubAccount).toHaveBeenCalled());
    expect(screen.getByTestId('account-form')).toHaveAttribute('data-submitting', 'true');

    await act(async () => create.resolve('91'));
    await waitFor(() => expect(screen.queryByTestId('account-form')).not.toBeInTheDocument());
  });

  it('创建要求 API Key，编辑从不回填掩码且留空表示不变', () => {
    expect(toCreateRunningHubAccountInput({ accountName: '主账号', apiKey: '   ' })).toBeUndefined();
    expect(
      buildRunningHubAccountFormValues({
        ...enabledRow,
        apiKeyMasked: 'rh_****1234',
        credentialUpdatedAt: null,
        createTime: null
      })
    ).toEqual({ accountId: '91', accountName: '主账号', apiKey: undefined, expectedRevision: 3 });
    expect(
      toUpdateRunningHubAccountInput({ accountId: '91', accountName: '主账号', apiKey: '', expectedRevision: 3 })
    ).toEqual({ accountName: '主账号', expectedRevision: 3 });
    expect(updateRunningHubAccount).not.toHaveBeenCalled();
  });
});
