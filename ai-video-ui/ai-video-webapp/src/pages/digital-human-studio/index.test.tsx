import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import type { ReactNode } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  authSession,
  beginLoginRedirect,
  resetLoginRedirect,
} from '@/services/ai-video/auth/session';
import Studio from './index';

const {
  mockAdapterRequest,
  mockBaseStep,
  mockCreateCreationTimelineApi,
  mockCreateProject,
  mockHistory,
  mockHistoryReplace,
  mockLibraryView,
  mockDemandStep,
  mockMessageOpen,
  mockRefresh,
  mockStudioSider,
  mockStudioTopbar,
  mockTimelineStep,
  mockExportStep,
  mockUseModel,
  mockUsePersonalQuotaAccount,
} = vi.hoisted(() => {
  const mockHistoryReplace = vi.fn();
  const mockCreateProject = vi.fn();

  return {
    mockAdapterRequest: vi.fn(),
    mockBaseStep: vi.fn(),
    mockCreateCreationTimelineApi: vi.fn(() => ({
      createProject: mockCreateProject,
    })),
    mockCreateProject,
    mockHistory: {
      location: {
        hash: '',
        pathname: '/studio',
        search: '',
      },
      replace: mockHistoryReplace,
    },
    mockHistoryReplace,
    mockLibraryView: vi.fn(),
    mockDemandStep: vi.fn(),
    mockMessageOpen: vi.fn(),
    mockRefresh: vi.fn(),
    mockStudioSider: vi.fn(),
    mockStudioTopbar: vi.fn(),
    mockTimelineStep: vi.fn(),
    mockExportStep: vi.fn(),
    mockUseModel: vi.fn(),
    mockUsePersonalQuotaAccount: vi.fn(() => ({
      retry: vi.fn(),
      state: { status: 'loading' },
    })),
  };
});

vi.mock('@/services/ai-video/auth/api', () => ({
  getRuntimeAppAdapter: () => ({ request: mockAdapterRequest }),
}));

vi.mock('@/services/ai-video/creation-timeline/api', () => ({
  createCreationTimelineApi: mockCreateCreationTimelineApi,
}));

vi.mock('@/services/ai-video/core/runtimeRuoYiAdapter', () => ({
  getRuntimeRuoYiAdapter: vi.fn(() => ({})),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

vi.mock('@umijs/max', () => ({
  Outlet: () =>
    mockHistory.location.pathname.startsWith('/discover') ? (
      <div>发现子路由内容</div>
    ) : null,
  history: mockHistory,
  useLocation: () => mockHistory.location,
  useModel: mockUseModel,
}));

vi.mock('antd', () => ({
  Button: ({
    children,
    onClick,
  }: {
    children: ReactNode;
    onClick?: () => void;
  }) => (
    <button onClick={onClick} type="button">
      {children}
    </button>
  ),
  ConfigProvider: ({ children }: { children: ReactNode }) => children,
  Modal: () => null,
  Result: ({
    subTitle,
    title,
  }: {
    subTitle?: ReactNode;
    title?: ReactNode;
  }) => (
    <div>
      {title}
      {subTitle}
    </div>
  ),
  message: {
    useMessage: () => [{ open: mockMessageOpen }, null],
  },
}));

vi.mock('./components/LibraryView', () => ({
  default: (props: unknown) => {
    mockLibraryView(props);
    return null;
  },
}));
vi.mock('./components/StudioDetailDrawer', () => ({ default: () => null }));
vi.mock('./components/StudioIcon', () => ({ default: () => null }));
vi.mock('./components/StudioSider', () => ({
  default: (props: unknown) => {
    mockStudioSider(props);
    return null;
  },
}));
vi.mock('./components/StudioTopbar', () => ({
  default: (props: { onNewProject: () => void }) => {
    mockStudioTopbar(props);
    return (
      <button type="button" onClick={props.onNewProject}>
        新建项目
      </button>
    );
  },
}));
vi.mock('./components/WorkflowSteps', () => ({ default: () => null }));
vi.mock('./steps/AssetStep', () => ({ default: () => null }));
vi.mock('./steps/BaseStep', () => ({
  default: (props: {
    onNext: () => void;
    state: { videoJob: unknown };
    update: (patch: Record<string, unknown>) => void;
  }) => {
    mockBaseStep(props);
    return (
      <div>
        <button
          type="button"
          onClick={() =>
            props.update({
              videoJob: {
                errorMessage: null,
                jobId: '70000000000000001',
                jobType: 'video_generate',
                outputAvailable: true,
                parentJobId: '60000000000000001',
                progress: 100,
                stage: 'completed',
                status: 'succeeded',
                voiceConfirmed: true,
              },
            })
          }
        >
          标记数字人任务成功
        </button>
        <button type="button" onClick={props.onNext}>
          进入时间轴编辑
        </button>
      </div>
    );
  },
}));
vi.mock('./steps/DemandStep', () => ({
  default: (props: {
    isGeneratingScript: boolean;
    onFinish: () => void;
    onNext: () => void;
    state: Record<string, unknown>;
    update: (patch: Record<string, unknown>) => void;
  }) => {
    mockDemandStep(props);
    const { isGeneratingScript, onNext, state, update } = props;
    return (
      <div>
        受保护的创作工作台内容
        <output data-testid="questionnaire-state">
          {JSON.stringify({
            questionnaire: state.questionnaire,
            survey: state.survey,
            surveyOtherAnswers: state.surveyOtherAnswers,
          })}
        </output>
        <button
          type="button"
          onClick={() =>
            update({
              industry: 'education',
              questionnaire: {
                knowledgeHash: 'knowledge-hash',
                knowledgeVersionIds: ['101'],
                modelMode: 'deepseek',
                questions: [
                  {
                    description: '请补充具体客户',
                    id: 'target-customer',
                    options: [{ label: '其他', value: 'other' }],
                    required: true,
                    title: '目标客户是谁？',
                  },
                ],
              },
              purpose: '课程讲解',
              survey: { 'target-customer': ['other'] },
              surveyOtherAnswers: { 'target-customer': '企业采购负责人' },
            })
          }
        >
          填写测试问卷
        </button>
        <button
          type="button"
          onClick={() =>
            update({
              industry: 'education',
              questionnaire: {
                knowledgeHash: 'knowledge-hash',
                knowledgeVersionIds: ['101'],
                modelMode: 'deepseek',
                questions: [
                  {
                    description: '请补充具体客户',
                    id: 'target-customer',
                    options: [{ label: '其它', value: 'custom_other' }],
                    required: true,
                    title: '目标客户是谁？',
                  },
                ],
              },
              purpose: '课程讲解',
              survey: { 'target-customer': ['custom_other'] },
              surveyOtherAnswers: { 'target-customer': '高校实验室负责人' },
            })
          }
        >
          填写其它测试问卷
        </button>
        <button disabled={isGeneratingScript} type="button" onClick={onNext}>
          {isGeneratingScript ? '正在生成文案…' : '生成文案'}
        </button>
      </div>
    );
  },
}));
vi.mock('./steps/ExportStep', () => ({
  default: (props: unknown) => {
    mockExportStep(props);
    return <div data-testid="export-step" />;
  },
}));
vi.mock('./steps/ScriptStep', () => ({
  default: ({
    onPrevious,
    state,
  }: {
    onPrevious: () => void;
    state: { scriptBodies: string[] };
  }) => (
    <div>
      <output data-testid="generated-script">{state.scriptBodies[0]}</output>
      <button type="button" onClick={onPrevious}>
        上一步
      </button>
    </div>
  ),
}));
vi.mock('./steps/TimelineStep', () => ({
  default: (props: {
    onNext: () => void;
    onRenderTaskChange?: (task: unknown) => void;
  }) => {
    mockTimelineStep(props);
    return (
      <>
        <button type="button" onClick={props.onNext}>
          去预览作品
        </button>
        <button
          type="button"
          onClick={() => {
            props.onRenderTaskChange?.({
              kind: 'render',
              taskId: '90071992547409937',
            });
            props.onNext();
          }}
        >
          创建合成并预览
        </button>
      </>
    );
  },
}));
vi.mock('./steps/VoiceStep', () => ({ default: () => null }));
vi.mock('./usePersonalQuotaAccount', () => ({
  usePersonalQuotaAccount: mockUsePersonalQuotaAccount,
}));

describe('数字人创作页登录门禁', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetLoginRedirect();
    authSession.clear();
    localStorage.clear();
    mockHistory.location = {
      hash: '#demand',
      pathname: '/studio',
      search: '?source=direct',
    };
    mockHistoryReplace.mockImplementation((target: string) => {
      const location = new URL(target, 'http://localhost');
      mockHistory.location = {
        hash: location.hash,
        pathname: location.pathname,
        search: location.search,
      };
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: undefined },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockResolvedValue({
      knowledgeHash: 'knowledge-hash',
      knowledgeVersionIds: ['101'],
      modelMode: 'deepseek',
      scripts: [
        {
          body: '这是 DeepSeek 生成的教育培训文案。',
          durationSeconds: 60,
          title: '清晰讲解版',
        },
      ],
    });
  });

  it('匿名访问时不渲染创作内容，即使本地存在令牌也替换跳转到登录页', async () => {
    localStorage.setItem('ai-video.app.access-token', 'forged-token');

    render(<Studio />);

    expect(screen.getByRole('status')).toHaveTextContent('正在跳转至登录页');
    expect(
      screen.queryByText('受保护的创作工作台内容'),
    ).not.toBeInTheDocument();
    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/user/login?redirect=%2Fstudio%3Fsource%3Ddirect%23demand',
      );
    });
    expect(mockHistoryReplace).toHaveBeenCalledTimes(1);
  });

  it('认证状态加载中时只显示可访问的中文加载提示', () => {
    mockUseModel.mockReturnValue({
      initialState: undefined,
      loading: true,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(screen.getByRole('status')).toHaveTextContent('正在验证登录状态');
    expect(
      screen.queryByText('受保护的创作工作台内容'),
    ).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('初始状态已包含经验证用户时才渲染创作内容', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(screen.getByText('受保护的创作工作台内容')).toBeVisible();
    expect(mockUsePersonalQuotaAccount).toHaveBeenCalledWith('app-user-001');
    expect(mockStudioSider).toHaveBeenCalledWith(
      expect.objectContaining({
        currentUser: { id: 'app-user-001' },
        quotaState: { status: 'loading' },
      }),
    );
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('发现路由复用同一工作台侧栏并渲染子路由内容', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.pathname = '/discover';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'discover' }),
    );
    expect(screen.getByText('发现子路由内容')).toBeVisible();
    expect(screen.queryByText('受保护的创作工作台内容')).not.toBeInTheDocument();
  });

  it('从共享侧栏进入时打开查询参数指定的原有内容区', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=voices';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(mockStudioSider).toHaveBeenCalledWith(
      expect.objectContaining({ activeKey: 'voices' }),
    );
  });

  it('无效内容区参数会回退到创作区', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=unknown';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(mockStudioSider).toHaveBeenCalledWith(
      expect.objectContaining({ activeKey: 'create' }),
    );
  });

  it('切换原有菜单时同步地址，刷新不会回到旧内容区', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=voices';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    const { unmount } = render(<Studio />);
    const siderProps = mockStudioSider.mock.lastCall?.[0] as {
      onRouteChange: (route: string) => void;
    };

    act(() => siderProps.onRouteChange('works'));

    expect(mockHistoryReplace).toHaveBeenCalledWith('/studio?view=works');
    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'works' }),
    );

    unmount();
    mockStudioSider.mockClear();
    render(<Studio />);
    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'works' }),
    );
  });

  it('从素材库返回创作区时同步地址', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=voices';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    render(<Studio />);
    const libraryProps = mockLibraryView.mock.lastCall?.[0] as {
      onNavigateCreate: (step: number) => void;
    };

    act(() => libraryProps.onNavigateCreate(2));

    expect(mockHistoryReplace).toHaveBeenCalledWith('/studio?view=create');
    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'create' }),
    );
  });

  it('新建项目时同步创作区地址', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=voices';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    render(<Studio />);
    const topbarProps = mockStudioTopbar.mock.lastCall?.[0] as {
      onNewProject: () => void;
    };

    act(() => topbarProps.onNewProject());

    expect(mockHistoryReplace).toHaveBeenCalledWith('/studio?view=create');
    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'create' }),
    );
  });

  it('从成功的数字人底片创建项目，并在时间轴和预览步骤保留同一项目 ID', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=create&step=4';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockCreateProject.mockResolvedValue({
      projectId: '90071992547409931',
    });

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '标记数字人任务成功' }));
    fireEvent.click(screen.getByRole('button', { name: '进入时间轴编辑' }));

    await waitFor(() => {
      expect(mockCreateProject).toHaveBeenCalledWith(
        expect.objectContaining({
          sourceId: '70000000000000001',
          sourceType: 'digital_human_job',
        }),
      );
    });
    expect(mockHistoryReplace).toHaveBeenCalledWith(
      '/studio?view=create&step=5&projectId=90071992547409931',
    );
    expect(mockTimelineStep).toHaveBeenLastCalledWith(
      expect.objectContaining({ projectId: '90071992547409931' }),
    );

    fireEvent.click(screen.getByRole('button', { name: '去预览作品' }));

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/studio?view=create&step=6&projectId=90071992547409931',
      );
    });
    expect(mockExportStep).toHaveBeenLastCalledWith(
      expect.objectContaining({ projectId: '90071992547409931' }),
    );
  });

  it('在合成任务创建后将任务 ID 写入第 7 步地址以支持刷新恢复', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search =
      '?view=create&step=5&projectId=90071992547409931';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);
    fireEvent.click(screen.getByRole('button', { name: '创建合成并预览' }));

    await waitFor(() => {
      expect(mockHistoryReplace).toHaveBeenCalledWith(
        '/studio?view=create&step=6&projectId=90071992547409931&renderTaskId=90071992547409937',
      );
    });
  });

  it('完成创作时同步作品区地址', () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockHistory.location.search = '?view=create';
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    render(<Studio />);
    const demandProps = mockDemandStep.mock.lastCall?.[0] as {
      onFinish: () => void;
    };

    act(() => demandProps.onFinish());

    expect(mockHistoryReplace).toHaveBeenCalledWith('/studio?view=works');
    expect(mockStudioSider).toHaveBeenLastCalledWith(
      expect.objectContaining({ activeKey: 'works' }),
    );
  });

  it('keeps a token-backed access-denied studio route on the Chinese 403 result without a login redirect', () => {
    authSession.save({
      accessToken: 'forbidden-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { accessDenied: true, currentUser: undefined },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(screen.getByText('访问受限')).toBeVisible();
    expect(
      screen.queryByText('受保护的创作工作台内容'),
    ).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('keeps a token-backed verification failure on a retryable Chinese error instead of redirecting to login', () => {
    authSession.save({
      accessToken: 'temporary-failure-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: undefined, verificationFailed: true },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    expect(screen.getByText('无法验证登录状态')).toBeVisible();
    expect(
      screen.queryByText('受保护的创作工作台内容'),
    ).not.toBeInTheDocument();
    expect(mockHistoryReplace).not.toHaveBeenCalled();
  });

  it('does not replace the global guard redirect after that guard has captured the studio target', async () => {
    expect(beginLoginRedirect()).toBe(true);
    mockHistory.location = {
      hash: '',
      pathname: '/user/login',
      search: '',
    };

    render(<Studio />);

    await waitFor(() => {
      expect(mockHistoryReplace).not.toHaveBeenCalled();
    });
  });

  it('generates scripts before advancing and keeps the questionnaire snapshot when returning', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));

    expect(
      await screen.findByText('这是 DeepSeek 生成的教育培训文案。'),
    ).toBeVisible();
    expect(mockAdapterRequest).toHaveBeenCalledWith(
      '/api/studio/scripts/generate',
      expect.objectContaining({
        data: expect.objectContaining({
          answerHistory: [
            {
              questionId: 'target-customer',
              questionTitle: '目标客户是谁？',
              selectedLabels: ['企业采购负责人'],
              selectedValues: ['企业采购负责人'],
            },
          ],
        }),
        method: 'POST',
      }),
    );

    fireEvent.click(screen.getByRole('button', { name: '上一步' }));

    expect(screen.getByTestId('questionnaire-state')).toHaveTextContent(
      '目标客户是谁？',
    );
    expect(screen.getByTestId('questionnaire-state')).toHaveTextContent(
      '企业采购负责人',
    );
  });

  it('uses the manual text for an alternative other label in script generation context', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });

    render(<Studio />);
    fireEvent.click(screen.getByRole('button', { name: '填写其它测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));

    expect(
      await screen.findByText('这是 DeepSeek 生成的教育培训文案。'),
    ).toBeVisible();
    expect(mockAdapterRequest).toHaveBeenCalledWith(
      '/api/studio/scripts/generate',
      expect.objectContaining({
        data: expect.objectContaining({
          answerHistory: [
            expect.objectContaining({
              selectedLabels: ['高校实验室负责人'],
              selectedValues: ['高校实验室负责人'],
            }),
          ],
        }),
      }),
    );
  });

  it('disables script generation while the request is pending and prevents duplicate submission', async () => {
    const pendingGeneration = deferred<{
      knowledgeHash: string;
      knowledgeVersionIds: string[];
      modelMode: string;
      scripts: Array<{
        body: string;
        durationSeconds: number;
        title: string;
      }>;
    }>();
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockReturnValueOnce(pendingGeneration.promise);

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));

    const pendingButton = await screen.findByRole('button', {
      name: '正在生成文案…',
    });
    expect(pendingButton).toBeDisabled();
    fireEvent.click(pendingButton);
    expect(mockAdapterRequest).toHaveBeenCalledTimes(1);

    pendingGeneration.resolve({
      knowledgeHash: 'knowledge-hash',
      knowledgeVersionIds: ['101'],
      modelMode: 'deepseek',
      scripts: [
        {
          body: '请求完成后的文案',
          durationSeconds: 60,
          title: '完成版',
        },
      ],
    });
    expect(await screen.findByText('请求完成后的文案')).toBeVisible();
  });

  it('drops a pending script response after the user creates a new project', async () => {
    const pendingGeneration = deferred<{
      knowledgeHash: string;
      knowledgeVersionIds: string[];
      modelMode: string;
      scripts: Array<{
        body: string;
        durationSeconds: number;
        title: string;
      }>;
    }>();
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockReturnValueOnce(pendingGeneration.promise);

    render(<Studio />);
    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));
    expect(
      await screen.findByRole('button', { name: '正在生成文案…' }),
    ).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '新建项目' }));
    pendingGeneration.resolve({
      knowledgeHash: 'stale-knowledge-hash',
      knowledgeVersionIds: ['101'],
      modelMode: 'deepseek',
      scripts: [
        {
          body: '不应写入新项目的旧文案',
          durationSeconds: 60,
          title: '旧项目文案',
        },
      ],
    });

    await waitFor(() =>
      expect(screen.getByTestId('questionnaire-state')).toHaveTextContent(
        '"questionnaire":null',
      ),
    );
    expect(screen.queryByTestId('generated-script')).not.toBeInTheDocument();
    expect(
      screen.queryByText('不应写入新项目的旧文案'),
    ).not.toBeInTheDocument();
  });

  it('drops a pending script response after the generation context changes', async () => {
    const pendingGeneration = deferred<{
      knowledgeHash: string;
      knowledgeVersionIds: string[];
      modelMode: string;
      scripts: Array<{
        body: string;
        durationSeconds: number;
        title: string;
      }>;
    }>();
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockReturnValueOnce(pendingGeneration.promise);

    render(<Studio />);
    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));
    expect(
      await screen.findByRole('button', { name: '正在生成文案…' }),
    ).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '填写其它测试问卷' }));

    expect(screen.getByRole('button', { name: '生成文案' })).toBeEnabled();
    expect(screen.getByTestId('questionnaire-state')).toHaveTextContent(
      '高校实验室负责人',
    );

    await act(async () => {
      pendingGeneration.resolve({
        knowledgeHash: 'stale-knowledge-hash',
        knowledgeVersionIds: ['101'],
        modelMode: 'deepseek',
        scripts: [
          {
            body: '不应写入新上下文的旧文案',
            durationSeconds: 60,
            title: '旧上下文文案',
          },
        ],
      });
      await pendingGeneration.promise;
    });

    expect(screen.queryByTestId('generated-script')).not.toBeInTheDocument();
    expect(
      screen.queryByText('不应写入新上下文的旧文案'),
    ).not.toBeInTheDocument();
  });

  it('re-enables script generation after a failed request', async () => {
    const pendingGeneration = deferred<never>();
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockReturnValueOnce(pendingGeneration.promise);

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));
    expect(
      await screen.findByRole('button', { name: '正在生成文案…' }),
    ).toBeDisabled();

    pendingGeneration.reject(new Error('生成失败'));

    expect(
      await screen.findByRole('button', { name: '生成文案' }),
    ).toBeEnabled();
  });

  it('stays on the demand step and shows the provider error when script generation fails', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockRejectedValue(new Error('DeepSeek 文案生成失败'));

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));

    await waitFor(() =>
      expect(mockMessageOpen).toHaveBeenCalledWith(
        expect.objectContaining({
          content: 'DeepSeek 文案生成失败',
          type: 'error',
        }),
      ),
    );
    expect(screen.getByText('受保护的创作工作台内容')).toBeVisible();
    expect(screen.queryByTestId('generated-script')).not.toBeInTheDocument();
  });

  it('rejects a non-DeepSeek script response instead of displaying fallback copy', async () => {
    authSession.save({
      accessToken: 'verified-app-token',
      persistent: false,
    });
    mockUseModel.mockReturnValue({
      initialState: { currentUser: { id: 'app-user-001' } },
      loading: false,
      refresh: mockRefresh,
    });
    mockAdapterRequest.mockResolvedValue({
      knowledgeHash: 'knowledge-hash',
      knowledgeVersionIds: ['101'],
      modelMode: 'knowledge-fallback',
      scripts: [
        {
          body: '这是一段静态兜底文案。',
          durationSeconds: 60,
          title: '兜底版',
        },
      ],
    });

    render(<Studio />);

    fireEvent.click(screen.getByRole('button', { name: '填写测试问卷' }));
    fireEvent.click(screen.getByRole('button', { name: '生成文案' }));

    await waitFor(() =>
      expect(mockMessageOpen).toHaveBeenCalledWith(
        expect.objectContaining({
          content: '文案生成未使用 DeepSeek，请重试',
          type: 'error',
        }),
      ),
    );
    expect(screen.getByText('受保护的创作工作台内容')).toBeVisible();
    expect(
      screen.queryByText('这是一段静态兜底文案。'),
    ).not.toBeInTheDocument();
  });
});
