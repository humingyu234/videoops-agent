import type { RunningHubExecutionMode } from '@/api/aivideo/workflow-template/types';
import type { PageQuery } from '@/api/types';

export type RunningHubAccountId = string;

export interface RunningHubAccountSummary {
  accountId: RunningHubAccountId;
  accountName: string;
  apiKeyMasked?: string | null;
  hasApiKey: boolean;
  enabled: boolean;
  lastHealthStatus?: string | null;
  lastHealthTime?: string | null;
  lastHealthSummary?: string | null;
  rowRevision: number;
  updateTime?: string | null;
}

export interface RunningHubAccountDetail extends RunningHubAccountSummary {
  credentialUpdatedAt?: string | null;
  createTime?: string | null;
}

export interface CreateRunningHubAccountInput {
  accountName: string;
  apiKey: string;
}

export interface UpdateRunningHubAccountInput {
  accountName: string;
  apiKey?: string;
  expectedRevision: number;
}

export interface RunningHubAccountQuery extends PageQuery {
  keyword?: string;
  enabled?: boolean;
}

export type RunningHubAccountTableParams = RunningHubAccountQuery & {
  current?: number;
  pageSize?: number;
};

export interface RunningHubParameterCandidateRequest {
  accountId: RunningHubAccountId;
  executionMode: RunningHubExecutionMode;
  webAppId?: string;
  workflowId?: string;
}

export interface RunningHubParameterCandidateOption {
  value: string;
  label: string;
}

export interface RunningHubParameterCandidate {
  nodeId: string;
  nodeName: string;
  fieldName: string;
  fieldType: string;
  description?: string | null;
  defaultValue?: unknown;
  options: RunningHubParameterCandidateOption[];
}

export interface RunningHubParameterCandidates {
  webAppName?: string | null;
  candidates: RunningHubParameterCandidate[];
}
