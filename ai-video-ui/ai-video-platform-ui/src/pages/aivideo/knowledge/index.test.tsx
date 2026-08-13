import type { ReactNode } from 'react';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useUserStore } from '@/stores/userStore';
import KnowledgePage from './index';

const apiMocks = vi.hoisted(() => ({
  addKnowledgeItem: vi.fn(),
  deleteKnowledgeItem: vi.fn(),
  getKnowledgeItem: vi.fn(),
  importKnowledgeItems: vi.fn(),
  pageKnowledgeItems: vi.fn(async () => ({ data: [], success: true, total: 0 })),
  publishKnowledgeItem: vi.fn(),
  updateKnowledgeItem: vi.fn(),
  updateKnowledgeStatus: vi.fn()
}));

vi.mock('antd', async importOriginal => {
  const actual = await importOriginal<typeof import('antd')>();
  const Dragger = ({
    accept,
    children,
    disabled,
    multiple,
    onChange
  }: {
    accept?: string;
    children?: ReactNode;
    disabled?: boolean;
    multiple?: boolean;
    onChange?: (info: { fileList: Array<{ name: string; originFileObj: File; uid: string }> }) => void;
  }) => (
    <div>
      {children}
      <input
        accept={accept}
        disabled={disabled}
        multiple={multiple}
        type="file"
        onChange={event => {
          const fileList = Array.from(event.currentTarget.files ?? []).map((file, index) => ({
            name: file.name,
            originFileObj: file,
            uid: `test-upload-${index}-${file.name}`
          }));
          onChange?.({ fileList });
        }}
      />
    </div>
  );

  return {
    ...actual,
    Upload: { ...actual.Upload, Dragger }
  };
});

const listRow = {
  content: '这是知识正文',
  id: '1',
  knowledgeType: 'primary_template',
  name: '口播规范',
  status: 'draft',
  summary: '适用于商品口播',
  updateTime: '2026-08-03 12:00:00',
  versionNo: 3
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(next => {
    resolve = next;
  });
  return { promise, resolve };
}

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: { children: ReactNode }) => <section>{children}</section>,
  ProTable: ({
    columns,
    toolBarRender
  }: {
    columns: Array<Record<string, unknown>>;
    toolBarRender?: () => ReactNode[];
  }) => {
    const renderColumn = (dataIndex: string) => {
      const column = columns.find(item => item.dataIndex === dataIndex);
      const renderCell = column?.render as ((node: unknown, row: typeof listRow) => ReactNode) | undefined;
      return renderCell?.(null, listRow);
    };
    const actionColumn = columns.find(column => column.valueType === 'option');
    const renderActions = actionColumn?.render as ((node: unknown, row: typeof listRow) => ReactNode) | undefined;
    return (
      <div>
        <div data-testid="column-titles">
          {columns.map((column, index) => (
            <span key={`${String(column.dataIndex || 'operation')}-${index}`}>{String(column.title || '')}</span>
          ))}
        </div>
        {toolBarRender?.()}
        <div>{renderColumn('name')}</div>
        <div>{renderColumn('knowledgeType')}</div>
        <div>{renderColumn('status')}</div>
        <div>{renderActions?.(null, listRow)}</div>
      </div>
    );
  }
}));

vi.mock('@/api/aivideo/knowledge', () => apiMocks);

function grant(...permissions: string[]) {
  useUserStore.getState().setUserInfo({
    permissions,
    roles: [],
    user: { userId: '100', userName: 'operator' }
  });
}

describe('运营知识库管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    apiMocks.getKnowledgeItem.mockResolvedValue(listRow);
    apiMocks.importKnowledgeItems.mockResolvedValue({
      failedCount: 0,
      files: [],
      skippedCount: 0,
      successCount: 1,
      totalCount: 1
    });
    useUserStore.getState().clearUserInfo();
  });

  it('没有查询权限时展示 403', () => {
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    expect(screen.getByText('403')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '导入知识库' })).not.toBeInTheDocument();
  });

  it('列表只显示业务列并将类型、状态翻译为中文', () => {
    grant(
      'aivideo:knowledge:query',
      'aivideo:knowledge:add',
      'aivideo:knowledge:edit',
      'aivideo:knowledge:remove',
      'aivideo:knowledge:import'
    );

    render(
      <App>
        <KnowledgePage />
      </App>
    );

    expect(screen.getByTestId('column-titles')).toHaveTextContent('名称知识类型状态更新时间操作');
    expect(screen.getByTestId('column-titles')).not.toHaveTextContent('来源路径');
    expect(screen.getByTestId('column-titles')).not.toHaveTextContent('内容大小');
    expect(screen.getByTestId('column-titles')).not.toHaveTextContent('版本号');
    expect(screen.getByText('基础模板')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新增知识' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '导入知识库' })).toBeInTheDocument();
  });

  it('点击名称可以查看包含摘要和正文的知识详情', async () => {
    grant('aivideo:knowledge:query');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '查看“口播规范”详情' }));

    await waitFor(() => expect(apiMocks.getKnowledgeItem).toHaveBeenCalledWith('1'));
    expect(await screen.findByText('适用于商品口播')).toBeInTheDocument();
    expect(screen.getByText('这是知识正文')).toBeInTheDocument();
    expect(screen.getByText('版本 3')).toBeInTheDocument();
  });

  it('列表状态可以直接修改', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:edit');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    const statusSelect = screen.getByRole('combobox', { name: '修改“口播规范”的状态' });
    fireEvent.mouseDown(statusSelect);
    fireEvent.click(await screen.findByText('已发布'));

    await waitFor(() => expect(apiMocks.updateKnowledgeStatus).toHaveBeenCalledWith('1', 'published'));
  });

  it('不同状态按照语义严重程度显示不同颜色', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:edit');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    expect(screen.getByTestId('knowledge-status-draft')).toHaveClass('ant-tag-default');

    const statusSelect = screen.getByRole('combobox', { name: '修改“口播规范”的状态' });
    fireEvent.mouseDown(statusSelect);

    expect(await screen.findByTestId('knowledge-status-reviewing')).toHaveClass('ant-tag-processing');
    expect(screen.getByTestId('knowledge-status-published')).toHaveClass('ant-tag-success');
    expect(screen.getByTestId('knowledge-status-retired')).toHaveClass('ant-tag-error');
  });

  it('导入知识库支持常用文本格式并允许逐文件编辑名称、类型和状态', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:import');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '导入知识库' }));

    expect(await screen.findByText('批量导入知识库')).toBeInTheDocument();
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input?.accept).toBe('.md,.markdown,.txt,.text,.json,.csv,.yaml,.yml');

    const file = new File(['正文'], '营销话术.txt', { type: 'text/plain' });
    if (!input) throw new Error('未找到知识库文件选择框');
    fireEvent.change(input, { target: { files: [file] } });

    expect(await screen.findByDisplayValue('营销话术')).toBeInTheDocument();
    expect(screen.getByLabelText('营销话术.txt 的知识类型')).toBeInTheDocument();
    expect(screen.getByLabelText('营销话术.txt 的状态')).toBeInTheDocument();
  });

  it('逐文件值随导入请求提交并展示跳过结果', async () => {
    apiMocks.importKnowledgeItems.mockResolvedValue({
      failedCount: 0,
      files: [{ fileName: '营销话术.txt', message: '内容重复', status: 'skipped' }],
      skippedCount: 1,
      successCount: 0,
      totalCount: 1
    });
    grant('aivideo:knowledge:query', 'aivideo:knowledge:import');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '导入知识库' }));
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) throw new Error('未找到知识库文件选择框');
    fireEvent.change(input, {
      target: { files: [new File(['正文'], '营销话术.txt', { type: 'text/plain' })] }
    });

    const typeSelect = await screen.findByRole('combobox', { name: '营销话术.txt 的知识类型' });
    fireEvent.mouseDown(typeSelect);
    fireEvent.click(await screen.findByText('心理策略'));
    const statusSelect = screen.getByRole('combobox', { name: '营销话术.txt 的状态' });
    fireEvent.mouseDown(statusSelect);
    fireEvent.click(await screen.findByText('审核中'));
    fireEvent.change(screen.getByLabelText('营销话术.txt 的知识名称'), { target: { value: '成交心理话术' } });
    fireEvent.click(screen.getByRole('button', { name: '开始导入' }));

    await waitFor(() =>
      expect(apiMocks.importKnowledgeItems).toHaveBeenCalledWith({
        rows: [
          expect.objectContaining({
            knowledgeType: 'psychology',
            name: '成交心理话术',
            status: 'reviewing'
          })
        ]
      })
    );
    expect(await screen.findByText('内容重复')).toBeInTheDocument();
    expect(screen.getByText('已跳过')).toBeInTheDocument();
    expect(screen.getByText('批量导入知识库')).toBeInTheDocument();
  });

  it('选择文件时拦截单个超过 10 MB 的文件', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:import');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '导入知识库' }));
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) throw new Error('未找到知识库文件选择框');
    fireEvent.change(input, {
      target: { files: [new File([new Uint8Array(10 * 1024 * 1024 + 1)], '超大知识.txt')] }
    });

    expect(await screen.findByText(/单个文件不能超过 10 MB/)).toBeInTheDocument();
    expect(screen.queryByDisplayValue('超大知识')).not.toBeInTheDocument();
  });

  it('选择文件时限制最多 20 个', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:import');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '导入知识库' }));
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) throw new Error('未找到知识库文件选择框');
    const tooMany = Array.from({ length: 21 }, (_, index) => new File(['x'], `知识-${index + 1}.txt`));
    fireEvent.change(input, { target: { files: tooMany } });

    expect(await screen.findByText(/一次最多导入 20 个文件/)).toBeInTheDocument();
    expect(screen.getAllByLabelText(/ 的知识名称$/)).toHaveLength(20);
  });

  it('选择文件时限制总大小不超过 20 MB', async () => {
    grant('aivideo:knowledge:query', 'aivideo:knowledge:import');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '导入知识库' }));
    const input = document.querySelector<HTMLInputElement>('input[type="file"]');
    if (!input) throw new Error('未找到知识库文件选择框');
    fireEvent.change(input, {
      target: {
        files: Array.from(
          { length: 3 },
          (_, index) => new File([new Uint8Array(8 * 1024 * 1024)], `大文件-${index + 1}.txt`)
        )
      }
    });
    expect(await screen.findByText(/文件总大小不能超过 20 MB/)).toBeInTheDocument();
    expect(screen.getAllByLabelText(/大文件-\d+\.txt 的知识名称/)).toHaveLength(2);
  });

  it('详情只接受最后一次请求的响应', async () => {
    const first = deferred<typeof listRow>();
    const second = deferred<typeof listRow>();
    apiMocks.getKnowledgeItem.mockImplementationOnce(() => first.promise).mockImplementationOnce(() => second.promise);
    grant('aivideo:knowledge:query');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    const viewButton = screen.getByRole('button', { name: '查看“口播规范”详情' });
    fireEvent.click(viewButton);
    fireEvent.click(viewButton);
    await act(async () => second.resolve({ ...listRow, content: '最后一次响应' }));
    expect(await screen.findByText('最后一次响应')).toBeInTheDocument();
    await act(async () => first.resolve({ ...listRow, content: '过期响应' }));

    await waitFor(() => expect(screen.queryByText('过期响应')).not.toBeInTheDocument());
    expect(screen.getByText('最后一次响应')).toBeInTheDocument();
  });

  it('状态更新期间禁用状态选择', async () => {
    const update = deferred<void>();
    apiMocks.updateKnowledgeStatus.mockReturnValue(update.promise);
    grant('aivideo:knowledge:query', 'aivideo:knowledge:edit');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    const statusSelect = screen.getByRole('combobox', { name: '修改“口播规范”的状态' });
    fireEvent.mouseDown(statusSelect);
    fireEvent.click(await screen.findByText('已发布'));
    await waitFor(() => expect(apiMocks.updateKnowledgeStatus).toHaveBeenCalled());
    expect(screen.getByRole('combobox', { name: '修改“口播规范”的状态' })).toBeDisabled();
    await act(async () => update.resolve());
  });

  it('保存知识期间禁用取消操作', async () => {
    const create = deferred<void>();
    apiMocks.addKnowledgeItem.mockReturnValue(create.promise);
    grant('aivideo:knowledge:query', 'aivideo:knowledge:add');
    render(
      <App>
        <KnowledgePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '新增知识' }));
    fireEvent.change(await screen.findByRole('textbox', { name: '名称' }), { target: { value: '新知识' } });
    fireEvent.change(screen.getByRole('textbox', { name: '正文' }), { target: { value: '新知识正文' } });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    await waitFor(() => expect(apiMocks.addKnowledgeItem).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: /取\s*消/ })).toBeDisabled();
    await act(async () => create.resolve());
  });
});
