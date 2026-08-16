import {
  assertExactKeys,
  assertNoSensitiveWireKeys,
  assertRecord,
  readArray,
  readEnum,
  readString,
} from '../core/wire';
import type {
  AgentActionResult,
  AgentPendingApproval,
  AgentPlan,
  AgentPlanStep,
  AgentRunDetail,
  AgentRunFact,
  AgentTrace,
  AgentTraceItem,
} from './types';

type WireRecord = Record<string, unknown>;

const RUN_STATUSES = [
  'queued',
  'running',
  'waiting_input',
  'waiting_external_task',
  'waiting_approval',
  'completed',
  'failed',
  'cancelled',
] as const;
const PLAN_STARTS = [
  'new',
  'voice_job',
  'video_job',
  'project',
  'render_task',
] as const;
const PLAN_STEP_TYPES = [
  'submit_voice',
  'wait_voice',
  'confirm_voice',
  'submit_video',
  'wait_video',
  'prepare_project',
  'submit_render',
  'wait_render',
  'inspect_output',
] as const;
const PLAN_TOOL_NAMES = [
  'submit_voice_generation',
  'get_generation_status',
  'confirm_voice_generation',
  'submit_digital_human_video',
  'prepare_timeline_project',
  'render_timeline',
  'get_timeline_render_status',
  'inspect_timeline_output',
] as const;
const PLAN_DISPOSITIONS = ['required', 'blocked', 'skipped'] as const;
const EXPECTED_TOOL_BY_STEP: Record<
  (typeof PLAN_STEP_TYPES)[number],
  (typeof PLAN_TOOL_NAMES)[number]
> = {
  submit_voice: 'submit_voice_generation',
  wait_voice: 'get_generation_status',
  confirm_voice: 'confirm_voice_generation',
  submit_video: 'submit_digital_human_video',
  wait_video: 'get_generation_status',
  prepare_project: 'prepare_timeline_project',
  submit_render: 'render_timeline',
  wait_render: 'get_timeline_render_status',
  inspect_output: 'inspect_timeline_output',
};
const APPROVAL_TYPES = ['initial', 'conditional', 'final'] as const;
const POSITIVE_DECIMAL_ID = /^[1-9]\d{0,18}$/;

function invalid(message: string): never {
  throw new Error(`Invalid wire response: ${message}`);
}

function readNonBlankString(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!value.trim()) invalid(`${key} must not be blank`);
  return value;
}

function readDecimalId(record: WireRecord, key: string): string {
  const value = readString(record, key);
  if (!POSITIVE_DECIMAL_ID.test(value)) {
    invalid(`${key} must be a positive decimal string`);
  }
  return value;
}

function readNullableDecimalId(
  record: WireRecord,
  key: string,
): string | null {
  if (record[key] === null) return null;
  return readDecimalId(record, key);
}

function readInteger(
  record: WireRecord,
  key: string,
  minimum: number,
): number {
  const value = record[key];
  if (!Number.isSafeInteger(value) || (value as number) < minimum) {
    invalid(`${key} must be a safe integer greater than or equal to ${minimum}`);
  }
  return value as number;
}

function readBoolean(record: WireRecord, key: string): boolean {
  const value = record[key];
  if (typeof value !== 'boolean') invalid(`${key} must be a boolean`);
  return value;
}

function readNullableString(record: WireRecord, key: string): string | null {
  if (record[key] === null) return null;
  return readString(record, key);
}

function parseRun(value: unknown): AgentRunFact {
  const run = assertRecord(value, 'agentRun');
  assertExactKeys(run, [
    'runId',
    'status',
    'rowVersion',
    'contractRevision',
    'waitingTaskSource',
    'waitingTaskId',
    'candidateAssetId',
    'qualityRepairCount',
    'pendingApprovalId',
    'approvalRevision',
    'resumeAfter',
    'finishedAt',
    'errorCode',
    'safeMessage',
  ]);
  return {
    runId: readDecimalId(run, 'runId'),
    status: readEnum(run, 'status', RUN_STATUSES),
    rowVersion: readInteger(run, 'rowVersion', 0),
    contractRevision: readInteger(run, 'contractRevision', 1),
    waitingTaskSource: readNullableString(run, 'waitingTaskSource'),
    waitingTaskId: readNullableDecimalId(run, 'waitingTaskId'),
    candidateAssetId: readNullableDecimalId(run, 'candidateAssetId'),
    qualityRepairCount: readInteger(run, 'qualityRepairCount', 0),
    pendingApprovalId: readNullableDecimalId(run, 'pendingApprovalId'),
    approvalRevision: readInteger(run, 'approvalRevision', 0),
    resumeAfter: readNullableString(run, 'resumeAfter'),
    finishedAt: readNullableString(run, 'finishedAt'),
    errorCode: readNullableString(run, 'errorCode'),
    safeMessage: readNullableString(run, 'safeMessage'),
  };
}

function parsePlanStep(value: unknown, index: number): AgentPlanStep {
  const step = assertRecord(value, `agentPlan.steps[${index}]`);
  assertExactKeys(step, [
    'sequence',
    'stepType',
    'toolName',
    'disposition',
    'reason',
  ]);
  const stepType = readEnum(step, 'stepType', PLAN_STEP_TYPES);
  const toolName = readEnum(step, 'toolName', PLAN_TOOL_NAMES);
  if (EXPECTED_TOOL_BY_STEP[stepType] !== toolName) {
    invalid(`agentPlan.steps[${index}] contains a mismatched tool`);
  }
  return {
    sequence: readInteger(step, 'sequence', 1),
    stepType,
    toolName,
    disposition: readEnum(step, 'disposition', PLAN_DISPOSITIONS),
    reason: readString(step, 'reason'),
  };
}

function parsePlan(value: unknown): AgentPlan {
  const plan = assertRecord(value, 'agentPlan');
  assertExactKeys(plan, [
    'startAt',
    'steps',
    'missingFields',
    'requiredProviderSubmissions',
    'executable',
  ]);
  return {
    startAt: readEnum(plan, 'startAt', PLAN_STARTS),
    steps: readArray(plan, 'steps', parsePlanStep),
    missingFields: readArray(plan, 'missingFields', (item, index) => {
      if (typeof item !== 'string' || !item.trim()) {
        invalid(`agentPlan.missingFields[${index}] must be a non-blank string`);
      }
      return item;
    }),
    requiredProviderSubmissions: readInteger(
      plan,
      'requiredProviderSubmissions',
      0,
    ),
    executable: readBoolean(plan, 'executable'),
  };
}

function parseTraceItem(value: unknown, index: number): AgentTraceItem {
  const item = assertRecord(value, `agentTrace.items[${index}]`);
  assertExactKeys(item, [
    'occurredAt',
    'type',
    'status',
    'subjectType',
    'subjectId',
    'label',
    'errorCode',
    'safeMessage',
  ]);
  return {
    occurredAt: readNonBlankString(item, 'occurredAt'),
    type: readNonBlankString(item, 'type'),
    status: readNonBlankString(item, 'status'),
    subjectType: readNonBlankString(item, 'subjectType'),
    subjectId: readDecimalId(item, 'subjectId'),
    label: readNonBlankString(item, 'label'),
    errorCode: readNullableString(item, 'errorCode'),
    safeMessage: readNullableString(item, 'safeMessage'),
  };
}

function parseTrace(value: unknown): AgentTrace {
  const trace = assertRecord(value, 'agentTrace');
  assertExactKeys(trace, ['completeness', 'items']);
  return {
    completeness: readEnum(trace, 'completeness', ['durable_facts'] as const),
    items: readArray(trace, 'items', parseTraceItem),
  };
}

function parsePendingApproval(value: unknown): AgentPendingApproval | null {
  if (value === null) return null;
  const approval = assertRecord(value, 'pendingApproval');
  assertExactKeys(approval, [
    'approvalId',
    'type',
    'status',
    'revision',
    'requestSummary',
  ]);
  return {
    approvalId: readDecimalId(approval, 'approvalId'),
    type: readEnum(approval, 'type', APPROVAL_TYPES),
    status: readEnum(approval, 'status', ['pending'] as const),
    revision: readInteger(approval, 'revision', 1),
    requestSummary: readNonBlankString(approval, 'requestSummary'),
  };
}

function parseAction(value: unknown): AgentActionResult | null {
  if (value === null || value === undefined) return null;
  const action = assertRecord(value, 'agentAction');
  assertExactKeys(action, [
    'outcome',
    'errorCode',
    'safeMessage',
    'missingFields',
  ]);
  return {
    outcome: readNonBlankString(action, 'outcome'),
    errorCode: readNullableString(action, 'errorCode'),
    safeMessage: readNullableString(action, 'safeMessage'),
    missingFields: readArray(action, 'missingFields', (item, index) => {
      if (typeof item !== 'string' || !item.trim()) {
        invalid(`agentAction.missingFields[${index}] must be a non-blank string`);
      }
      return item;
    }),
  };
}

export function parseAgentRunDetailWire(value: unknown): AgentRunDetail {
  assertNoSensitiveWireKeys(value);
  const detail = assertRecord(value, 'agentRunDetail');
  assertExactKeys(detail, [
    'run',
    'plan',
    'trace',
    'pendingApproval',
    'finalOutputAssetId',
    'action',
  ]);
  const run = parseRun(detail.run);
  const pendingApproval = parsePendingApproval(detail.pendingApproval);
  const finalOutputAssetId = readNullableDecimalId(
    detail,
    'finalOutputAssetId',
  );
  const pendingApprovalId = pendingApproval?.approvalId ?? null;
  if (pendingApprovalId !== run.pendingApprovalId) {
    invalid('pendingApproval must match run.pendingApprovalId');
  }
  if (pendingApproval && pendingApproval.revision !== run.approvalRevision) {
    invalid('pendingApproval revision must match run.approvalRevision');
  }
  if (finalOutputAssetId && finalOutputAssetId !== run.candidateAssetId) {
    invalid('finalOutputAssetId must match run.candidateAssetId');
  }
  if (finalOutputAssetId && run.status !== 'completed') {
    invalid('finalOutputAssetId is only valid for a completed run');
  }
  if (run.status === 'completed' && !finalOutputAssetId) {
    invalid('completed run must contain finalOutputAssetId');
  }
  return {
    run,
    plan: parsePlan(detail.plan),
    trace: parseTrace(detail.trace),
    pendingApproval,
    finalOutputAssetId,
    action: parseAction(detail.action),
  };
}
