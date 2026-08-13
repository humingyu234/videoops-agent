import type {
  CreateRunningHubAccountInput,
  RunningHubAccountDetail,
  UpdateRunningHubAccountInput
} from '@/api/aivideo/runninghub-account/types';

export interface RunningHubAccountFormValues {
  accountId?: string;
  accountName?: string;
  apiKey?: string;
  expectedRevision?: number;
}

export function buildRunningHubAccountFormValues(detail?: RunningHubAccountDetail): RunningHubAccountFormValues {
  if (!detail) {
    return { accountName: undefined, apiKey: undefined };
  }
  return {
    accountId: detail.accountId,
    accountName: detail.accountName,
    apiKey: undefined,
    expectedRevision: detail.rowRevision
  };
}

export function toCreateRunningHubAccountInput(
  values: RunningHubAccountFormValues
): CreateRunningHubAccountInput | undefined {
  const accountName = values.accountName?.trim();
  const apiKey = values.apiKey?.trim();
  if (!accountName || !apiKey) return undefined;
  return { accountName, apiKey };
}

export function toUpdateRunningHubAccountInput(
  values: RunningHubAccountFormValues
): UpdateRunningHubAccountInput | undefined {
  const accountName = values.accountName?.trim();
  if (!values.accountId || !accountName || typeof values.expectedRevision !== 'number') return undefined;
  const apiKey = values.apiKey?.trim();
  return {
    accountName,
    expectedRevision: values.expectedRevision,
    ...(apiKey ? { apiKey } : {})
  };
}
