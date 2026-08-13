import type { PageResult, R } from '@/api/types';
import request from '@/api/request';
import { toPageQuery, toTableData } from '@/utils/ruoyi';
import type {
  CreateRunningHubAccountInput,
  RunningHubAccountDetail,
  RunningHubAccountId,
  RunningHubAccountSummary,
  RunningHubAccountTableParams,
  RunningHubParameterCandidateRequest,
  RunningHubParameterCandidates,
  UpdateRunningHubAccountInput
} from './types';

const RUNNINGHUB_ACCOUNT_RESOURCE = '/api/admin/runninghub-accounts';

function accountResource(accountId: RunningHubAccountId) {
  return `${RUNNINGHUB_ACCOUNT_RESOURCE}/${encodeURIComponent(accountId)}`;
}

export async function pageRunningHubAccounts(params: RunningHubAccountTableParams) {
  const response = await request<R<PageResult<RunningHubAccountSummary>>>({
    method: 'get',
    params: toPageQuery(params),
    url: RUNNINGHUB_ACCOUNT_RESOURCE
  });
  return toTableData(response);
}

export async function createRunningHubAccount(data: CreateRunningHubAccountInput) {
  const response = await request<R<RunningHubAccountId>, CreateRunningHubAccountInput>({
    data,
    headers: { repeatSubmit: false },
    method: 'post',
    url: RUNNINGHUB_ACCOUNT_RESOURCE
  });
  return response.data;
}

export async function getRunningHubAccount(accountId: RunningHubAccountId) {
  const response = await request<R<RunningHubAccountDetail>>({
    method: 'get',
    url: accountResource(accountId)
  });
  return response.data;
}

export async function updateRunningHubAccount(accountId: RunningHubAccountId, data: UpdateRunningHubAccountInput) {
  const response = await request<R<void>, UpdateRunningHubAccountInput>({
    data,
    headers: { repeatSubmit: false },
    method: 'put',
    url: accountResource(accountId)
  });
  return response.data;
}

export async function deleteRunningHubAccount(accountId: RunningHubAccountId, expectedRevision: number) {
  const response = await request<R<void>>({
    method: 'delete',
    params: { expectedRevision },
    url: accountResource(accountId)
  });
  return response.data;
}

export async function enableRunningHubAccount(accountId: RunningHubAccountId, expectedRevision: number) {
  const response = await request<R<void>, { expectedRevision: number }>({
    data: { expectedRevision },
    method: 'post',
    url: `${accountResource(accountId)}/enable`
  });
  return response.data;
}

export async function disableRunningHubAccount(accountId: RunningHubAccountId, expectedRevision: number) {
  const response = await request<R<void>, { expectedRevision: number }>({
    data: { expectedRevision },
    method: 'post',
    url: `${accountResource(accountId)}/disable`
  });
  return response.data;
}

export async function inspectRunningHubParameterCandidates(data: RunningHubParameterCandidateRequest) {
  const response = await request<R<RunningHubParameterCandidates>, RunningHubParameterCandidateRequest>({
    data,
    method: 'post',
    url: `${RUNNINGHUB_ACCOUNT_RESOURCE}/parameter-candidates`
  });
  return response.data;
}
