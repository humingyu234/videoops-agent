export type AgentRunStatus =
  | 'queued'
  | 'running'
  | 'waiting_input'
  | 'waiting_external_task'
  | 'waiting_approval'
  | 'completed'
  | 'failed'
  | 'cancelled';

export type AgentPlanStartAt =
  | 'new'
  | 'voice_job'
  | 'video_job'
  | 'project'
  | 'render_task';

export type AgentApprovalType = 'initial' | 'conditional' | 'final';

export interface AgentRunFact {
  runId: string;
  status: AgentRunStatus;
  rowVersion: number;
  contractRevision: number;
  waitingTaskSource: string | null;
  waitingTaskId: string | null;
  candidateAssetId: string | null;
  qualityRepairCount: number;
  pendingApprovalId: string | null;
  approvalRevision: number;
  resumeAfter: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  safeMessage: string | null;
}

export interface AgentPlanStep {
  sequence: number;
  stepType: string;
  toolName: string;
  disposition: string;
  reason: string;
}

export interface AgentPlan {
  startAt: AgentPlanStartAt;
  steps: AgentPlanStep[];
  missingFields: string[];
  requiredProviderSubmissions: number;
  executable: boolean;
}

export interface AgentTraceItem {
  occurredAt: string;
  type: string;
  status: string;
  subjectType: string;
  subjectId: string;
  label: string;
  errorCode: string | null;
  safeMessage: string | null;
}

export interface AgentTrace {
  completeness: 'durable_facts';
  items: AgentTraceItem[];
}

export interface AgentPendingApproval {
  approvalId: string;
  type: AgentApprovalType;
  status: 'pending';
  revision: number;
  requestSummary: string;
}

export interface AgentActionResult {
  outcome: string;
  errorCode: string | null;
  safeMessage: string | null;
  missingFields: string[];
}

export interface AgentRunDetail {
  run: AgentRunFact;
  plan: AgentPlan;
  trace: AgentTrace;
  pendingApproval: AgentPendingApproval | null;
  finalOutputAssetId: string | null;
  action: AgentActionResult | null;
}

export interface CreateNewAgentRunInput {
  startAt: 'new';
  scriptText: string;
  referenceVoiceId: string;
  portraitId: string;
  projectTitle: string;
  idempotencyKey: string;
}

export interface CreateVoiceJobAgentRunInput {
  startAt: 'voice_job';
  voiceJobId: string;
  portraitId: string;
  projectTitle: string;
  idempotencyKey: string;
}

export interface CreateVideoJobAgentRunInput {
  startAt: 'video_job';
  videoJobId: string;
  projectTitle: string;
  idempotencyKey: string;
}

export interface CreateProjectAgentRunInput {
  startAt: 'project';
  projectId: string;
  expectedRevision: string;
  idempotencyKey: string;
}

export interface CreateRenderTaskAgentRunInput {
  startAt: 'render_task';
  taskId: string;
  idempotencyKey: string;
}

export type CreateAgentRunInput =
  | CreateNewAgentRunInput
  | CreateVoiceJobAgentRunInput
  | CreateVideoJobAgentRunInput
  | CreateProjectAgentRunInput
  | CreateRenderTaskAgentRunInput;

export interface AgentRunRevisionInput {
  rowVersion: number;
  contractRevision: number;
}

export interface AgentApprovalDecisionInput extends AgentRunRevisionInput {
  approvalRevision: number;
  type: AgentApprovalType;
  approved: boolean;
}
