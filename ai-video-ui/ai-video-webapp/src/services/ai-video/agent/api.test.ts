import { describe, expect, it, vi } from 'vitest';
import { parseAgentRunDetailWire } from './adapter';
import { createAgentApi } from './api';

function detailWire(overrides: Record<string, unknown> = {}) {
  return {
    run: {
      runId: '401',
      status: 'waiting_approval',
      rowVersion: 3,
      contractRevision: 1,
      waitingTaskSource: null,
      waitingTaskId: null,
      candidateAssetId: null,
      qualityRepairCount: 0,
      pendingApprovalId: '901',
      approvalRevision: 2,
      resumeAfter: null,
      finishedAt: null,
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
          type: 'approval',
          status: 'pending',
          subjectType: 'agent_run',
          subjectId: '401',
          label: '等待初始批准',
          errorCode: null,
          safeMessage: null,
        },
      ],
    },
    pendingApproval: {
      approvalId: '901',
      type: 'initial',
      status: 'pending',
      revision: 2,
      requestSummary: '确认后将提交两次 Provider 任务',
    },
    finalOutputAssetId: null,
    action: null,
    ...overrides,
  };
}

describe('agent API contract', () => {
  it('parses the exact durable-fact response with decimal string identities', () => {
    const detail = parseAgentRunDetailWire(detailWire());

    expect(detail.run).toMatchObject({
      runId: '401',
      status: 'waiting_approval',
      rowVersion: 3,
    });
    expect(detail.pendingApproval).toMatchObject({
      approvalId: '901',
      revision: 2,
      type: 'initial',
    });
    expect(detail.trace.items[0]?.label).toBe('等待初始批准');
    expect(detail.action).toBeNull();
  });

  it('preserves stable immediate action feedback beside persisted run facts', () => {
    const detail = parseAgentRunDetailWire(
      detailWire({
        action: {
          outcome: 'state_conflict',
          errorCode: 'AGENT_RUN_STATE_CONFLICT',
          safeMessage: 'AgentRun 状态已变化，请重新读取',
          missingFields: [],
        },
      }),
    );

    expect(detail.action).toEqual({
      outcome: 'state_conflict',
      errorCode: 'AGENT_RUN_STATE_CONFLICT',
      safeMessage: 'AgentRun 状态已变化，请重新读取',
      missingFields: [],
    });
  });

  it.each([
    ['queued', null],
    ['completed', '701'],
  ] as const)(
    'accepts a %s run without a pending approval',
    (status, finalOutputAssetId) => {
      const base = detailWire();
      const detail = parseAgentRunDetailWire({
        ...base,
        run: {
          ...(base.run as Record<string, unknown>),
          status,
          candidateAssetId: finalOutputAssetId,
          pendingApprovalId: null,
          approvalRevision: 0,
        },
        pendingApproval: null,
        finalOutputAssetId,
      });

      expect(detail.run.status).toBe(status);
      expect(detail.pendingApproval).toBeNull();
      expect(detail.finalOutputAssetId).toBe(finalOutputAssetId);
    },
  );

  it.each([
    [
      'unknown fields',
      () =>
        parseAgentRunDetailWire({
          ...detailWire(),
          leaseToken: 'forbidden',
        }),
    ],
    [
      'unsafe number identities',
      () =>
        parseAgentRunDetailWire({
          ...detailWire(),
          run: { ...(detailWire().run as object), runId: 401 },
        }),
    ],
    [
      'approval/run mismatches',
      () =>
        parseAgentRunDetailWire({
          ...detailWire(),
          pendingApproval: {
            ...(detailWire().pendingApproval as object),
            approvalId: '902',
          },
        }),
    ],
    [
      'a final output before completion',
      () => {
        const wire = detailWire();
        return parseAgentRunDetailWire({
          ...wire,
          run: {
            ...(wire.run as Record<string, unknown>),
            candidateAssetId: '701',
          },
          finalOutputAssetId: '701',
        });
      },
    ],
    [
      'an unknown plan tool',
      () => {
        const wire = detailWire();
        const plan = wire.plan as Record<string, unknown>;
        const steps = plan.steps as Record<string, unknown>[];
        return parseAgentRunDetailWire({
          ...wire,
          plan: {
            ...plan,
            steps: [{ ...steps[0], toolName: 'execute_arbitrary_command' }],
          },
        });
      },
    ],
    [
      'missing action keys',
      () => {
        const wire = detailWire();
        delete (wire as Partial<typeof wire>).action;
        return parseAgentRunDetailWire(wire);
      },
    ],
  ])('rejects %s before the page can trust the response', (_label, parse) => {
    expect(parse).toThrow(/Invalid wire response/);
  });

  it('sends only the frozen create, advance, cancel, and approval fields', async () => {
    const request = vi.fn().mockResolvedValue(detailWire());
    const api = createAgentApi({ request });
    const createInput = {
      startAt: 'new' as const,
      scriptText: '真实口播脚本',
      referenceVoiceId: '101',
      portraitId: '201',
      projectTitle: 'T7 黄金链',
      idempotencyKey: 'agent-create-one',
    };

    await api.create(createInput);
    await api.advance('401', { rowVersion: 3, contractRevision: 1 });
    await api.cancel('401', { rowVersion: 3, contractRevision: 1 });
    await api.decideApproval('401', '901', {
      rowVersion: 3,
      contractRevision: 1,
      approvalRevision: 2,
      type: 'initial',
      approved: true,
    });

    expect(request.mock.calls).toEqual([
      ['/api/agent/runs', { method: 'POST', data: createInput }],
      [
        '/api/agent/runs/401/advancements',
        {
          method: 'POST',
          data: { rowVersion: 3, contractRevision: 1 },
        },
      ],
      [
        '/api/agent/runs/401/cancellations',
        {
          method: 'POST',
          data: { rowVersion: 3, contractRevision: 1 },
        },
      ],
      [
        '/api/agent/runs/401/approvals/901/decision',
        {
          method: 'POST',
          data: {
            rowVersion: 3,
            contractRevision: 1,
            approvalRevision: 2,
            type: 'initial',
            approved: true,
          },
        },
      ],
    ]);
  });

  it('rejects owner, worker, lease, and conflicting approval identities locally', async () => {
    const request = vi.fn();
    const api = createAgentApi({ request });

    await expect(
      api.advance(
        '401',
        {
          rowVersion: 3,
          contractRevision: 1,
          workerId: 'browser-worker',
        } as never,
      ),
    ).rejects.toThrow(/unknown fields/);
    await expect(
      api.create({
        startAt: 'new',
        scriptText: '脚本',
        referenceVoiceId: '101',
        portraitId: '201',
        projectTitle: '项目',
        idempotencyKey: 'intent-one',
        ownerId: '999',
      } as never),
    ).rejects.toThrow(/unknown fields/);
    await expect(
      api.decideApproval('401', '901', {
        rowVersion: 3,
        contractRevision: 1,
        approvalRevision: 2,
        type: 'initial',
        approved: true,
        leaseToken: 'forbidden',
      } as never),
    ).rejects.toThrow(/unknown fields/);
    expect(request).not.toHaveBeenCalled();
  });
});
