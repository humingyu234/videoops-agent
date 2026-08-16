import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgentApi } from '@/services/ai-video/agent/api';
import type {
  AgentRunDetail,
  AgentRunStatus,
} from '@/services/ai-video/agent/types';
import type { PortraitApi } from '@/services/ai-video/portrait/api';
import type { Portrait } from '@/services/ai-video/portrait/types';
import type { VoiceApi } from '@/services/ai-video/voice/api';
import type { Voice } from '@/services/ai-video/voice/types';
import AgentPage from './index';

const portrait: Portrait = {
  portraitId: '201',
  name: '人物 A',
  gender: 'unspecified',
  sceneTags: [],
  availabilityStatus: 'ready',
  recordRevision: '1',
  createTime: '2026-08-16T10:00:00Z',
  updateTime: '2026-08-16T10:00:00Z',
};
const voice: Voice = {
  voiceId: '101',
  assetId: '301',
  voiceType: 'origin',
  name: '原声音 A',
  gender: 'unspecified',
  tags: [],
  transcriptionStatus: 'unparsed',
  attemptCount: 0,
  recordRevision: '1',
  createTime: '2026-08-16T10:00:00Z',
  updateTime: '2026-08-16T10:00:00Z',
};

function runDetail(
  status: AgentRunStatus,
  overrides: Partial<AgentRunDetail> = {},
): AgentRunDetail {
  const approval =
    status === 'waiting_approval'
      ? {
          approvalId: '901',
          type: 'initial' as const,
          status: 'pending' as const,
          revision: 2,
          requestSummary: '确认后将提交两次 Provider 任务',
        }
      : null;
  const finalOutputAssetId = status === 'completed' ? '701' : null;
  return {
    run: {
      runId: '401',
      status,
      rowVersion: status === 'completed' ? 9 : 3,
      contractRevision: 1,
      waitingTaskSource:
        status === 'waiting_external_task' ? 'digital_human_generation' : null,
      waitingTaskId: status === 'waiting_external_task' ? '501' : null,
      candidateAssetId: finalOutputAssetId,
      qualityRepairCount: 0,
      pendingApprovalId: approval?.approvalId ?? null,
      approvalRevision: approval?.revision ?? 0,
      resumeAfter:
        status === 'waiting_external_task' ? '2026-08-16T12:00:02Z' : null,
      finishedAt: status === 'completed' ? '2026-08-16T12:00:10Z' : null,
      errorCode: null,
      safeMessage: null,
    },
    plan: {
      startAt: 'new',
      steps: [
        {
          sequence: 1,
          stepType: 'submit_voice',
          toolName: 'submit_voice_generation',
          disposition: 'required',
          reason: '需要生成本次口播声音',
        },
      ],
      missingFields: [],
      requiredProviderSubmissions: 2,
      executable: true,
    },
    trace: {
      completeness: 'durable_facts',
      items: [
        {
          occurredAt: '2026-08-16T12:00:00Z',
          type: 'agent_run',
          status,
          subjectType: 'agent_run',
          subjectId: '401',
          label: status === 'completed' ? '成品已确认' : '等待负责人批准',
          errorCode: null,
          safeMessage: null,
        },
      ],
    },
    pendingApproval: approval,
    finalOutputAssetId,
    action: null,
    ...overrides,
  };
}

function dependencies(agentOverrides: Partial<AgentApi> = {}) {
  const agentClient: AgentApi = {
    create: vi.fn().mockResolvedValue(runDetail('waiting_approval')),
    get: vi.fn().mockResolvedValue(runDetail('waiting_approval')),
    advance: vi.fn().mockResolvedValue(runDetail('completed')),
    cancel: vi.fn().mockResolvedValue(runDetail('cancelled')),
    decideApproval: vi.fn().mockResolvedValue(runDetail('completed')),
    ...agentOverrides,
  };
  const portraitClient: Pick<PortraitApi, 'list'> = {
    list: vi.fn().mockResolvedValue({ rows: [portrait], total: 1 }),
  };
  const voiceClient: Pick<VoiceApi, 'list'> = {
    list: vi.fn().mockResolvedValue({ rows: [voice], total: 1 }),
  };
  const assetsClient = {
    content: vi
      .fn()
      .mockResolvedValue(new Blob(['mp4'], { type: 'video/mp4' })),
  };
  return { agentClient, portraitClient, voiceClient, assetsClient };
}

async function createRun() {
  await screen.findByText('人物 A');
  await screen.findByText('原声音 A');
  fireEvent.click(screen.getByRole('button', { name: '创建并开始受限执行' }));
}

describe('/agent product entry', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(
      () => undefined,
    );
    window.localStorage.clear();
    window.history.replaceState({}, '', '/agent');
    vi.mocked(URL.createObjectURL).mockReturnValue('blob:agent-output');
  });

  it('creates a NEW run from owned ready/origin assets without browser authority fields', async () => {
    const clients = dependencies();
    render(<AgentPage {...clients} pollingIntervalMs={5} />);

    expect(screen.getByLabelText('中文口播脚本')).toHaveValue(
      '大家好，我是由 VideoOps Agent 驱动的数字人。它能把一句视频交付目标拆成清晰步骤，选择真实人物和声音，调用数字人、字幕与渲染工具，持续跟踪任务状态，并在出现问题时保留证据、局部修复。现在你看到的内容，不是演示数据，而是一条真实、可追踪、可复现的视频生产链路。',
    );

    await createRun();

    await waitFor(() => expect(clients.agentClient.create).toHaveBeenCalled());
    const input = vi.mocked(clients.agentClient.create).mock.calls[0]?.[0];
    expect(Object.keys(input ?? {}).sort()).toEqual(
      [
        'idempotencyKey',
        'portraitId',
        'projectTitle',
        'referenceVoiceId',
        'scriptText',
        'startAt',
      ].sort(),
    );
    expect(input).toMatchObject({
      startAt: 'new',
      portraitId: '201',
      referenceVoiceId: '101',
    });
    expect(input?.idempotencyKey).toMatch(/^agent-/);
    expect(await screen.findByText('等待initial批准')).toBeInTheDocument();
    expect(screen.getByText('等待负责人批准')).toBeInTheDocument();
    expect(
      window.localStorage.getItem('videoops-agent:last-owned-run-id'),
    ).toBe('401');
  });

  it('fences approval with exact revisions and downloads the exact final asset', async () => {
    const clients = dependencies();
    render(<AgentPage {...clients} pollingIntervalMs={5} />);
    await createRun();
    const approve = await screen.findByRole('button', {
      name: '批准并继续',
    });

    fireEvent.click(approve);

    await waitFor(() =>
      expect(clients.agentClient.decideApproval).toHaveBeenCalledWith(
        '401',
        '901',
        {
          rowVersion: 3,
          contractRevision: 1,
          approvalRevision: 2,
          type: 'initial',
          approved: true,
        },
      ),
    );
    expect(await screen.findByLabelText('Agent 最终成品')).toHaveAttribute(
      'src',
      'blob:agent-output',
    );
    expect(clients.assetsClient.content).toHaveBeenCalledWith(
      '701',
      expect.objectContaining({ signal: expect.any(AbortSignal) }),
    );
    fireEvent.click(screen.getByRole('button', { name: /下载最终 MP4/ }));
  });

  it('resumes a saved run through the owner-scoped GET route and rejects invalid query IDs', async () => {
    const valid = dependencies();
    window.history.replaceState({}, '', '/agent?runId=401');
    expect(window.location.search).toBe('?runId=401');
    const rendered = render(<AgentPage {...valid} pollingIntervalMs={5} />);

    await waitFor(() =>
      expect(valid.agentClient.get).toHaveBeenCalledWith('401'),
    );
    expect(await screen.findByText('RUN #401')).toBeInTheDocument();
    rendered.unmount();

    const invalid = dependencies();
    window.history.replaceState({}, '', '/agent?runId=not-an-id');
    render(<AgentPage {...invalid} pollingIntervalMs={5} />);

    expect(
      await screen.findByText('链接中的 AgentRun ID 无效，未发起读取。'),
    ).toBeInTheDocument();
    expect(invalid.agentClient.get).not.toHaveBeenCalled();
  });

  it('polls the same waiting task to terminal once and then stops advancing', async () => {
    const waiting = runDetail('waiting_external_task');
    const clients = dependencies({
      create: vi.fn().mockResolvedValue(waiting),
      advance: vi.fn().mockResolvedValue(runDetail('completed')),
    });
    render(<AgentPage {...clients} pollingIntervalMs={5} />);
    await createRun();

    await waitFor(() =>
      expect(clients.agentClient.advance).toHaveBeenCalledWith('401', {
        rowVersion: 3,
        contractRevision: 1,
      }),
    );
    await screen.findByText('已完成');
    await new Promise((resolve) => window.setTimeout(resolve, 30));
    expect(clients.agentClient.advance).toHaveBeenCalledTimes(1);
  });

  it('stops automatic advancement after the bounded failure budget', async () => {
    const clients = dependencies({
      create: vi.fn().mockResolvedValue(runDetail('waiting_external_task')),
      advance: vi
        .fn()
        .mockRejectedValue(new Error('temporary network failure')),
    });
    render(<AgentPage {...clients} pollingIntervalMs={1} />);
    await createRun();

    expect(
      await screen.findByText(
        '连续查询失败，自动推进已暂停。任务身份已保留，请手动恢复。',
      ),
    ).toBeInTheDocument();
    expect(clients.agentClient.advance).toHaveBeenCalledTimes(5);
  });
});
