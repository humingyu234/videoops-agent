import type { PageResult, R } from '@/api/types';
import request, { type RequestConfig } from '@/api/request';
import { formatDateTimeRange, toPageQuery, toTableData } from '@/utils/ruoyi';
import type {
  AppAuthClientAdmin,
  AppAuthClientQuery,
  AppAuthClientSecret,
  AppLoginLogAdmin,
  AppLoginLogQuery,
  AppPermissionAdmin,
  AppRoleAdmin,
  AppRoleQuery,
  AppSecurityAuditAdmin,
  AppSecurityAuditQuery,
  AppSessionAdmin,
  AppSessionQuery,
  AppTableParams,
  AppUserAdmin,
  AppUserDetail,
  AppUserInitialPassword,
  AppUserQuery,
  ChangeAppUserStatusInput,
  CreateAppAuthClientInput,
  CreateAppRoleInput,
  CreateAppUserInput,
  KickoutAppSessionInput,
  KickoutAppUserInput,
  ReplaceAppRolePermissionsInput,
  ReplaceAppUserRolesInput,
  ResetAppUserPasswordInput,
  RotateAppAuthClientSecretInput,
  UpdateAppAuthClientInput,
  UpdateAppRoleInput,
  UpdateAppUserInput
} from './types';

const ADMIN_APP_RESOURCE = '/api/admin';

function getResourcePath(resource: string) {
  return `${ADMIN_APP_RESOURCE}/${resource}`;
}

async function requestData<T, D = unknown>(config: RequestConfig<D>) {
  const response = await request<R<T>, D>(config);
  return response.data;
}

async function requestPage<T, Q extends object>(resource: string, params: AppTableParams<Q>) {
  const response = await request<R<PageResult<T>>>({
    method: 'get',
    params: toPageQuery(params),
    url: getResourcePath(resource)
  });
  return toTableData(response);
}

function toOccurredAtQuery<T extends object>(params: AppTableParams<T & { occurredAtRange?: AppLoginLogQuery['occurredAtRange'] }>) {
  const { occurredAtRange, ...rest } = params;
  const range = formatDateTimeRange(occurredAtRange);
  return {
    ...toPageQuery(rest),
    ...(range ? { occurredAfter: range[0], occurredBefore: range[1] } : {})
  };
}

export function pageAppUsers(params: AppTableParams<AppUserQuery>) {
  return requestPage<AppUserAdmin, AppUserQuery>('app-users', params);
}

export function getAppUser(id: string) {
  return requestData<AppUserDetail>({ method: 'get', url: getResourcePath(`app-users/${id}`) });
}

export function createAppUser(data: CreateAppUserInput) {
  return requestData<AppUserInitialPassword, CreateAppUserInput>({
    data,
    method: 'post',
    url: getResourcePath('app-users')
  });
}

export function updateAppUser(id: string, data: UpdateAppUserInput) {
  return requestData<void, UpdateAppUserInput>({
    data,
    method: 'put',
    url: getResourcePath(`app-users/${id}`)
  });
}

export function changeAppUserStatus(id: string, data: ChangeAppUserStatusInput) {
  return requestData<void, ChangeAppUserStatusInput>({
    data,
    method: 'post',
    url: getResourcePath(`app-users/${id}/status-changes`)
  });
}

export function resetAppUserPassword(id: string, data: ResetAppUserPasswordInput) {
  return requestData<AppUserInitialPassword, ResetAppUserPasswordInput>({
    data,
    method: 'post',
    url: getResourcePath(`app-users/${id}/password-resets`)
  });
}

export function kickoutAppUser(id: string, data: KickoutAppUserInput) {
  return requestData<void, KickoutAppUserInput>({
    data,
    method: 'post',
    url: getResourcePath(`app-users/${id}/kickouts`)
  });
}

export function replaceAppUserRoles(id: string, data: ReplaceAppUserRolesInput) {
  return requestData<void, ReplaceAppUserRolesInput>({
    data,
    method: 'put',
    url: getResourcePath(`app-users/${id}/roles`)
  });
}

export function pageAppRoles(params: AppTableParams<AppRoleQuery>) {
  return requestPage<AppRoleAdmin, AppRoleQuery>('app-roles', params);
}

export function createAppRole(data: CreateAppRoleInput) {
  return requestData<AppRoleAdmin, CreateAppRoleInput>({ data, method: 'post', url: getResourcePath('app-roles') });
}

export function updateAppRole(id: string, data: UpdateAppRoleInput) {
  return requestData<void, UpdateAppRoleInput>({ data, method: 'put', url: getResourcePath(`app-roles/${id}`) });
}

export function replaceAppRolePermissions(id: string, data: ReplaceAppRolePermissionsInput) {
  return requestData<void, ReplaceAppRolePermissionsInput>({
    data,
    method: 'put',
    url: getResourcePath(`app-roles/${id}/permissions`)
  });
}

export function listAppPermissions() {
  return requestData<AppPermissionAdmin[]>({ method: 'get', url: getResourcePath('app-permissions') });
}

export function pageAppAuthClients(params: AppTableParams<AppAuthClientQuery>) {
  return requestPage<AppAuthClientAdmin, AppAuthClientQuery>('app-auth-clients', params);
}

export function createAppAuthClient(data: CreateAppAuthClientInput) {
  return requestData<AppAuthClientSecret, CreateAppAuthClientInput>({
    data,
    method: 'post',
    url: getResourcePath('app-auth-clients')
  });
}

export function updateAppAuthClient(id: string, data: UpdateAppAuthClientInput) {
  return requestData<void, UpdateAppAuthClientInput>({
    data,
    method: 'put',
    url: getResourcePath(`app-auth-clients/${id}`)
  });
}

export function rotateAppAuthClientSecret(id: string, data: RotateAppAuthClientSecretInput) {
  return requestData<AppAuthClientSecret, RotateAppAuthClientSecretInput>({
    data,
    method: 'post',
    url: getResourcePath(`app-auth-clients/${id}/secret-rotations`)
  });
}

export function pageAppSessions(params: AppTableParams<AppSessionQuery>) {
  return requestPage<AppSessionAdmin, AppSessionQuery>('app-sessions', params);
}

export function kickoutAppSession(id: string, data: KickoutAppSessionInput) {
  return requestData<void, KickoutAppSessionInput>({
    data,
    method: 'delete',
    url: getResourcePath(`app-sessions/${id}`)
  });
}

export async function pageAppLoginLogs(params: AppTableParams<AppLoginLogQuery>) {
  const response = await request<R<PageResult<AppLoginLogAdmin>>>({
    method: 'get',
    params: toOccurredAtQuery(params),
    url: getResourcePath('app-login-logs')
  });
  return toTableData(response);
}

export async function pageAppSecurityAudits(params: AppTableParams<AppSecurityAuditQuery>) {
  const response = await request<R<PageResult<AppSecurityAuditAdmin>>>({
    method: 'get',
    params: toOccurredAtQuery(params),
    url: getResourcePath('app-security-audits')
  });
  return toTableData(response);
}
