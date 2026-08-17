import { getRuntimeRuoYiAdapter } from '../core/runtimeRuoYiAdapter';
import type { RuoYiAdapter } from '../core/ruoyiAdapter';
import { parseAgentRunDetailWire } from './adapter';
import type {
  AgentApprovalDecisionInput,
  AgentRunDetail,
  AgentRunRevisionInput,
  CreateAgentRunInput,
} from './types';

export interface AgentApi {
  create(input: CreateAgentRunInput): Promise<AgentRunDetail>;
  get(runId: string, signal?: AbortSignal): Promise<AgentRunDetail>;
  advance(
    runId: string,
    input: AgentRunRevisionInput,
  ): Promise<AgentRunDetail>;
  cancel(
    runId: string,
    input: AgentRunRevisionInput,
  ): Promise<AgentRunDetail>;
  decideApproval(
    runId: string,
    approvalId: string,
    input: AgentApprovalDecisionInput,
  ): Promise<AgentRunDetail>;
}

const POSITIVE_DECIMAL_ID = /^[1-9]\d{0,18}$/;
const IDEMPOTENCY_KEY = /^[A-Za-z0-9._:-]{1,48}$/;
const APPROVAL_TYPES = ['initial', 'conditional', 'final'] as const;

function invalid(message: string): never {
  throw new Error(`Invalid agent request: ${message}`);
}

function assertExactKeys(value: unknown, keys: readonly string[]): void {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    invalid('request must be an object');
  }
  const actual = Object.keys(value);
  if (
    actual.length !== keys.length ||
    actual.some((key) => !keys.includes(key))
  ) {
    invalid('request contains missing or unknown fields');
  }
}

function assertId(value: string, field: string): void {
  if (!POSITIVE_DECIMAL_ID.test(value)) {
    invalid(`${field} must be a positive decimal string`);
  }
}

function assertRevision(value: number, field: string, minimum: number): void {
  if (!Number.isSafeInteger(value) || value < minimum) {
    invalid(`${field} is invalid`);
  }
}

function assertCreateInput(input: CreateAgentRunInput): void {
  switch (input.startAt) {
    case 'new':
      assertExactKeys(input, [
        'startAt',
        'scriptText',
        'referenceVoiceId',
        'portraitId',
        'projectTitle',
        'idempotencyKey',
      ]);
      if (!input.scriptText.trim() || [...input.scriptText].length > 1_000) {
        invalid('scriptText is invalid');
      }
      assertId(input.referenceVoiceId, 'referenceVoiceId');
      assertId(input.portraitId, 'portraitId');
      assertProjectTitle(input.projectTitle);
      break;
    case 'voice_job':
      assertExactKeys(input, [
        'startAt',
        'voiceJobId',
        'portraitId',
        'projectTitle',
        'idempotencyKey',
      ]);
      assertId(input.voiceJobId, 'voiceJobId');
      assertId(input.portraitId, 'portraitId');
      assertProjectTitle(input.projectTitle);
      break;
    case 'video_job':
      assertExactKeys(input, [
        'startAt',
        'videoJobId',
        'projectTitle',
        'idempotencyKey',
      ]);
      assertId(input.videoJobId, 'videoJobId');
      assertProjectTitle(input.projectTitle);
      break;
    case 'project':
      assertExactKeys(input, [
        'startAt',
        'projectId',
        'expectedRevision',
        'idempotencyKey',
      ]);
      assertId(input.projectId, 'projectId');
      assertId(input.expectedRevision, 'expectedRevision');
      break;
    case 'render_task':
      assertExactKeys(input, ['startAt', 'taskId', 'idempotencyKey']);
      assertId(input.taskId, 'taskId');
      break;
    default:
      invalid('startAt is invalid');
  }
  if (!IDEMPOTENCY_KEY.test(input.idempotencyKey)) {
    invalid('idempotencyKey is invalid');
  }
}

function assertProjectTitle(value: string): void {
  if (!value.trim() || [...value].length > 128) {
    invalid('projectTitle is invalid');
  }
}

function assertRevisionInput(input: AgentRunRevisionInput): void {
  assertExactKeys(input, ['rowVersion', 'contractRevision']);
  assertRevision(input.rowVersion, 'rowVersion', 0);
  assertRevision(input.contractRevision, 'contractRevision', 1);
}

function assertApprovalInput(input: AgentApprovalDecisionInput): void {
  assertExactKeys(input, [
    'rowVersion',
    'contractRevision',
    'approvalRevision',
    'type',
    'approved',
  ]);
  assertRevision(input.rowVersion, 'rowVersion', 0);
  assertRevision(input.contractRevision, 'contractRevision', 1);
  assertRevision(input.approvalRevision, 'approvalRevision', 1);
  if (!APPROVAL_TYPES.includes(input.type)) invalid('type is invalid');
  if (typeof input.approved !== 'boolean') invalid('approved must be a boolean');
}

function runPath(runId: string): string {
  assertId(runId, 'runId');
  return `/api/agent/runs/${encodeURIComponent(runId)}`;
}

export function createAgentApi(adapter: RuoYiAdapter): AgentApi {
  return {
    async create(input) {
      assertCreateInput(input);
      return parseAgentRunDetailWire(
        await adapter.request<unknown>('/api/agent/runs', {
          method: 'POST',
          data: input,
        }),
      );
    },
    async get(runId, signal) {
      return parseAgentRunDetailWire(
        await adapter.request<unknown>(runPath(runId), {
          method: 'GET',
          signal,
        }),
      );
    },
    async advance(runId, input) {
      assertRevisionInput(input);
      return parseAgentRunDetailWire(
        await adapter.request<unknown>(`${runPath(runId)}/advancements`, {
          method: 'POST',
          data: input,
        }),
      );
    },
    async cancel(runId, input) {
      assertRevisionInput(input);
      return parseAgentRunDetailWire(
        await adapter.request<unknown>(`${runPath(runId)}/cancellations`, {
          method: 'POST',
          data: input,
        }),
      );
    },
    async decideApproval(runId, approvalId, input) {
      assertId(approvalId, 'approvalId');
      assertApprovalInput(input);
      return parseAgentRunDetailWire(
        await adapter.request<unknown>(
          `${runPath(runId)}/approvals/${encodeURIComponent(approvalId)}/decision`,
          { method: 'POST', data: input },
        ),
      );
    },
  };
}

let runtimeApi: AgentApi | undefined;

export function getRuntimeAgentApi(): AgentApi {
  if (!runtimeApi) {
    runtimeApi = createAgentApi(getRuntimeRuoYiAdapter());
  }
  return runtimeApi;
}
