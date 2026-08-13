import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { WorkflowCreationConfig } from '@/services/ai-video/discovery/types';
import DiscoveryPage from './index';
import { TemplateRunForm } from './template-create';
import TemplateDetailPage from './template-detail';

const mocks = vi.hoisted(() => ({
  authState: {
    initialState: {
      currentUser: { id: '1001', workspace: { id: '2001' } },
    },
  } as { initialState?: unknown },
  completeUpload: vi.fn(),
  getCreationConfig: vi.fn(),
  createOrder: vi.fn(),
  createUpload: vi.fn(),
  getHome: vi.fn(),
  getTemplates: vi.fn(),
  getTemplate: vi.fn(),
  push: vi.fn(),
  templateId: '101',
  transferUpload: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  history: { push: mocks.push },
  useModel: () => mocks.authState,
  useParams: () => ({ templateId: mocks.templateId }),
}));
vi.mock('@/components/CreatorWorkspaceShell', () => ({
  default: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));
vi.mock('@/services/ai-video/discovery/api', () => ({
  discoveryApi: {
    getCreationConfig: mocks.getCreationConfig,
    getHome: mocks.getHome,
    getTemplates: mocks.getTemplates,
    getTemplate: mocks.getTemplate,
  },
}));
vi.mock('@/services/ai-video/workflow-orders/api', () => ({
  workflowOrdersApi: { create: mocks.createOrder },
}));
vi.mock('@/services/ai-video/workflow-uploads/api', () => ({
  workflowUploadsApi: {
    complete: mocks.completeUpload,
    create: mocks.createUpload,
    transfer: mocks.transferUpload,
  },
}));

const cover = {
  mediaId: '31',
  mediaType: 'image',
  url: '/discovery/skincare.webp',
  width: 1200,
  height: 1000,
  alt: '效果预览',
};

const card = {
  templateId: '101',
  title: '护肤产品氛围广告',
  summary: '一张图生成品牌短片',
  channel: 'video_template',
  category: { categoryCode: '11', label: '产品广告' },
  tags: [{ tagCode: '21', label: '热门' }],
  cover,
  usageCount: '1280',
  estimatedDurationSeconds: 30,
  enabledAt: '2026-08-11T08:00:00',
};

const home = {
  banners: [],
  recommendations: [card],
  channels: [
    {
      channel: 'video_template',
      label: '视频模板',
      description: '',
      templateCount: '1',
    },
    {
      channel: 'workflow_inspiration',
      label: '创作灵感',
      description: '',
      templateCount: '0',
    },
  ],
  categories: [{ categoryCode: '11', label: '产品广告', templateCount: '1' }],
  tags: [],
};

const detail = {
  ...card,
  description: '完整介绍',
  cases: [],
  requiredInputs: [
    {
      semanticKey: 'source',
      label: '主体图片',
      valueType: 'asset_array',
      assetType: 'image',
      required: true,
    },
  ],
};

const creationConfig: WorkflowCreationConfig = {
  templateId: '101',
  schemaVersion: 'workflow-form-1',
  schemaHash: `sha256:${'a'.repeat(64)}`,
  fields: [],
  estimatedDurationSeconds: 30,
  billingPolicy: { mode: 'free' },
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

function renderWithQuery(ui: React.ReactElement) {
  return render(
    <QueryClientProvider
      client={
        new QueryClient({ defaultOptions: { queries: { retry: false } } })
      }
    >
      {ui}
    </QueryClientProvider>,
  );
}

describe('discovery user pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.authState.initialState = {
      currentUser: { id: '1001', workspace: { id: '2001' } },
    };
    mocks.templateId = '101';
    mocks.getHome.mockResolvedValue(home);
    mocks.getTemplates.mockResolvedValue({ rows: [card], total: 1 });
    mocks.getTemplate.mockResolvedValue(detail);
    mocks.getCreationConfig.mockResolvedValue(creationConfig);
    mocks.createOrder.mockResolvedValue({ orderId: '901', taskId: '902' });
    mocks.createUpload.mockResolvedValue({
      singlePutUrl: '/api/assets/uploads/content/test',
      status: 'created',
      uploadId: '701',
    });
    mocks.transferUpload.mockResolvedValue({
      status: 'uploaded',
      uploadId: '701',
    });
    mocks.completeUpload.mockResolvedValue({
      assetId: '801',
      assetStatus: 'ready',
      status: 'completed',
      uploadId: '701',
    });
  });

  it('keeps the list usable while the independent home query is loading', async () => {
    const pendingHome = deferred<typeof home>();
    mocks.getHome.mockReturnValue(pendingHome.promise);

    renderWithQuery(<DiscoveryPage />);

    expect(
      await screen.findByRole('button', {
        name: '查看模板：护肤产品氛围广告',
      }),
    ).toBeVisible();
    expect(screen.getByLabelText('发现首页加载中')).toBeVisible();
    pendingHome.resolve(home);
  });

  it('opens a template detail from the discovery list', async () => {
    renderWithQuery(<DiscoveryPage />);
    fireEvent.click(
      await screen.findByRole('button', {
        name: '查看模板：护肤产品氛围广告',
      }),
    );

    expect(mocks.push).toHaveBeenCalledWith('/discover/templates/101');
  });

  it('does not render the channel count switch above the template categories', async () => {
    renderWithQuery(<DiscoveryPage />);

    expect(await screen.findByRole('button', { name: '全部' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    expect(screen.queryByText('视频模板 1')).not.toBeInTheDocument();
    expect(screen.queryByText('创作灵感 0')).not.toBeInTheDocument();
  });

  it('retries a failed template list without hiding successful home content', async () => {
    mocks.getTemplates
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ rows: [card], total: 1 });
    renderWithQuery(<DiscoveryPage />);

    expect(await screen.findByText('模板列表加载失败')).toBeVisible();
    expect(screen.getByText('本周精选工作流')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '重新加载模板列表' }));

    expect(
      await screen.findByRole('button', {
        name: '查看模板：护肤产品氛围广告',
      }),
    ).toBeVisible();
    expect(mocks.getTemplates).toHaveBeenCalledTimes(2);
  });

  it('renders controlled empty states for home and the paged list', async () => {
    mocks.getHome.mockResolvedValue({
      banners: [],
      recommendations: [],
      channels: [],
      categories: [],
      tags: [],
    });
    mocks.getTemplates.mockResolvedValue({ rows: [], total: 0 });

    renderWithQuery(<DiscoveryPage />);

    expect(await screen.findByText('暂无发现首页内容')).toBeVisible();
    expect(screen.getByText('没有找到匹配的模板')).toBeVisible();
  });

  it('requests the selected server page instead of appending hidden pages', async () => {
    mocks.getTemplates.mockResolvedValue({ rows: [card], total: 25 });
    renderWithQuery(<DiscoveryPage />);
    await screen.findByRole('button', {
      name: '查看模板：护肤产品氛围广告',
    });

    fireEvent.click(screen.getByTitle('2'));

    await waitFor(() =>
      expect(mocks.getTemplates).toHaveBeenLastCalledWith(
        expect.objectContaining({ pageNum: 2, pageSize: 10 }),
      ),
    );
  });

  it('does not show recommendation helper text or a template total', async () => {
    renderWithQuery(<DiscoveryPage />);

    await screen.findByRole('button', {
      name: '查看模板：护肤产品氛围广告',
    });
    expect(screen.queryByText('从成熟模板开始创作')).not.toBeInTheDocument();
    expect(screen.queryByText('共 1 个工作流')).not.toBeInTheDocument();
  });

  it('renders workflow media uploads as wrapping preview cards', () => {
    const { container } = renderWithQuery(
      <TemplateRunForm
        config={{
          ...creationConfig,
          fields: [
            {
              control: 'image',
              inputKey: 'referenceImages',
              label: '参考图片',
              required: true,
              valueType: 'asset_array',
            },
          ],
        }}
        templateId="101"
      />,
    );

    expect(
      container.querySelector('.ant-upload-list-picture-card'),
    ).toBeInTheDocument();
    expect(
      container.querySelector('[data-testid="workflow-asset-upload"]'),
    ).toBeInTheDocument();
    expect(
      container.querySelector('[data-testid="workflow-upload-trigger"]'),
    ).toHaveTextContent('上传素材');
    expect(
      container.querySelector('.ant-upload-select .ant-btn'),
    ).not.toBeInTheDocument();
  });

  it('submits uploaded and entered dynamic workflow values', async () => {
    const open = vi.spyOn(window, 'open').mockReturnValue(null);
    const { container } = renderWithQuery(
      <TemplateRunForm
        config={{
          ...creationConfig,
          fields: [
            {
              constraints: { assetType: 'image', maxItems: 1 },
              control: 'image',
              inputKey: 'image',
              label: '原图',
              required: true,
              valueType: 'asset_array',
            },
            {
              constraints: { assetType: 'image', maxItems: 1 },
              control: 'image',
              inputKey: 'image_548',
              label: '服装图',
              required: true,
              valueType: 'asset_array',
            },
            {
              control: 'textarea',
              inputKey: 'text',
              label: 'text',
              required: true,
              valueType: 'string',
            },
            {
              control: 'integer',
              inputKey: 'value',
              label: '尺寸最长边',
              required: true,
              valueType: 'integer',
            },
          ],
        }}
        templateId="101"
      />,
    );

    const [sourceInput, garmentInput] = Array.from(
      container.querySelectorAll<HTMLInputElement>('input[type="file"]'),
    );
    if (!sourceInput || !garmentInput) throw new Error('上传控件未渲染');
    fireEvent.change(sourceInput, {
      target: {
        files: [new File(['source'], 'source.png', { type: 'image/png' })],
      },
    });
    fireEvent.change(garmentInput, {
      target: {
        files: [new File(['garment'], 'garment.png', { type: 'image/png' })],
      },
    });
    await waitFor(() => {
      expect(mocks.completeUpload).toHaveBeenCalledTimes(2);
      expect(
        container.querySelectorAll('.ant-upload-list-item-done'),
      ).toHaveLength(2);
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'text' }), {
      target: { value: '给模特换上服装' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: '尺寸最长边' }), {
      target: { value: '1080' },
    });
    fireEvent.click(screen.getByRole('button', { name: '立即运行' }));

    await waitFor(() =>
      expect(mocks.createOrder).toHaveBeenCalledWith(
        expect.objectContaining({
          inputs: {
            image: [{ assetId: '801' }],
            image_548: [{ assetId: '801' }],
            text: '给模特换上服装',
            value: 1080,
          },
        }),
      ),
    );
    expect(mocks.push).toHaveBeenCalledWith('/orders/901');
    open.mockRestore();
  });

  it('shows a no-cover placeholder without creating a broken image', async () => {
    mocks.getTemplates.mockResolvedValue({
      rows: [{ ...card, cover: null }],
      total: 1,
    });
    renderWithQuery(<DiscoveryPage />);

    const listCard = await screen.findByRole('button', {
      name: '查看模板：护肤产品氛围广告',
    });
    expect(within(listCard).getByText('暂无封面')).toBeVisible();
    expect(within(listCard).queryByAltText('效果预览')).not.toBeInTheDocument();
  });

  it('does not start private queries before real user and workspace ids exist', async () => {
    mocks.authState.initialState = { currentUser: undefined };

    renderWithQuery(<DiscoveryPage />);

    expect(screen.getByText('正在确认登录身份')).toBeVisible();
    await waitFor(() => {
      expect(mocks.getHome).not.toHaveBeenCalled();
      expect(mocks.getTemplates).not.toHaveBeenCalled();
    });
  });

  it('opens the template result in a reserved new window after the order is created', async () => {
    const replace = vi.fn();
    const resultWindow = {
      closed: false,
      close: vi.fn(),
      location: { replace },
      opener: window,
    } as unknown as Window;
    const open = vi.spyOn(window, 'open').mockReturnValue(resultWindow);

    renderWithQuery(<TemplateDetailPage />);

    fireEvent.click(await screen.findByRole('button', { name: '立即运行' }));

    expect(open).toHaveBeenCalledWith('about:blank', '_blank');
    expect(resultWindow.opener).toBeNull();
    await waitFor(() =>
      expect(mocks.createOrder).toHaveBeenCalledWith(
        expect.objectContaining({ templateId: '101', inputs: {} }),
      ),
    );
    expect(replace).toHaveBeenCalledWith(
      new URL('/orders/901', window.location.origin).href,
    );
    expect(mocks.push).not.toHaveBeenCalledWith('/orders/901');

    open.mockRestore();
  });

  it('places the cover and template introduction in the left detail column', async () => {
    renderWithQuery(<TemplateDetailPage />);

    const leftColumn = await screen.findByTestId('template-detail-left');
    const rightColumn = screen.getByTestId('template-detail-right');

    expect(
      within(leftColumn).getByRole('img', { name: '效果预览' }),
    ).toBeVisible();
    expect(
      within(leftColumn).queryByText('模板效果预览'),
    ).not.toBeInTheDocument();
    expect(
      within(leftColumn).getByRole('heading', { level: 2, name: '模板介绍' }),
    ).toBeVisible();
    expect(
      within(rightColumn).getByRole('heading', {
        level: 2,
        name: '护肤产品氛围广告',
      }),
    ).toBeVisible();
    expect(
      within(rightColumn).getByRole('heading', {
        level: 2,
        name: '制作前需要准备',
      }),
    ).toBeVisible();
    expect(
      within(rightColumn).queryByText('需要准备内容'),
    ).not.toBeInTheDocument();
  });

  it('loads detail and creation config in parallel and exposes immediate run', async () => {
    const pendingDetail = deferred<typeof detail>();
    const pendingConfig = deferred<typeof creationConfig>();
    mocks.getTemplate.mockReturnValue(pendingDetail.promise);
    mocks.getCreationConfig.mockReturnValue(pendingConfig.promise);

    renderWithQuery(<TemplateDetailPage />);

    await waitFor(() => {
      expect(mocks.getTemplate).toHaveBeenCalledWith('101');
      expect(mocks.getCreationConfig).toHaveBeenCalledWith('101');
    });
    pendingDetail.resolve(detail);
    pendingConfig.resolve(creationConfig);

    expect(
      await screen.findByRole('heading', {
        level: 2,
        name: '护肤产品氛围广告',
      }),
    ).toBeVisible();
    expect(screen.getByRole('button', { name: '立即运行' })).toBeEnabled();
    expect(mocks.push).not.toHaveBeenCalledWith(
      '/discover/templates/101/create',
    );
  });

  it('shows a stable configuration-unavailable state for 46503', async () => {
    mocks.getCreationConfig.mockRejectedValue(
      new ApiError({ code: 46503, msg: 'upstream detail' }),
    );
    renderWithQuery(<TemplateDetailPage />);

    expect(await screen.findByText('制作配置暂不可用')).toBeVisible();
    expect(screen.queryByText('upstream detail')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: '立即运行' }),
    ).not.toBeInTheDocument();
  });

  it('handles 46501 and 403 without leaking backend messages', async () => {
    mocks.getTemplate.mockRejectedValue(
      new ApiError({ code: 46501, msg: 'private template detail' }),
    );
    renderWithQuery(<TemplateDetailPage />);
    expect(await screen.findByText('模板暂不可用')).toBeVisible();
    expect(
      screen.queryByText('private template detail'),
    ).not.toBeInTheDocument();

    vi.clearAllMocks();
    mocks.getTemplate.mockRejectedValue(
      new ApiError({ code: 403, msg: 'permission internals', status: 403 }),
    );
    mocks.getCreationConfig.mockRejectedValue(
      new ApiError({ code: 403, msg: 'permission internals', status: 403 }),
    );
    renderWithQuery(<TemplateDetailPage />);
    expect(await screen.findByText('暂无模板查看权限')).toBeVisible();
    expect(screen.queryByText('permission internals')).not.toBeInTheDocument();
  });

  it('retries a transient creation-config failure without reloading detail', async () => {
    mocks.getCreationConfig
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(creationConfig);
    renderWithQuery(<TemplateDetailPage />);

    expect(await screen.findByText('制作配置加载失败')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: '重新加载制作配置' }));

    expect(
      await screen.findByRole('button', { name: '立即运行' }),
    ).toBeEnabled();
    expect(mocks.getTemplate).toHaveBeenCalledTimes(1);
    expect(mocks.getCreationConfig).toHaveBeenCalledTimes(2);
  });
});
