import type { ConfigType } from 'dayjs';
import type { PageQuery } from '@/api/types';

export type AppIdentityStatus = 'active' | 'inactive' | 'disabled';
export type AppRoleScopeType = 'personal' | 'organization';
export type AppActorType = 'app_user' | 'sys_user';
/** RuoYi serializes safe Long values as numbers and large Long values as strings. */
export type AppRevision = string | number;
export type AppKickoutReasonCode =
  | 'admin_kickout'
  | 'admin_kickout_security'
  | 'admin_kickout_policy'
  | 'admin_kickout_support';

export type AppTableParams<T extends object> = T & {
  current?: number;
  pageSize?: number;
};

export interface AppUserAdmin {
  id: string;
  username: string;
  displayName: string;
  maskedPhone?: string | null;
  maskedEmail?: string | null;
  status: AppIdentityStatus;
  mustChangePassword: boolean;
  credentialRevision: AppRevision;
  identityRevision: AppRevision;
  permissionRevision: AppRevision;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AppUserDetail {
  user: AppUserAdmin;
  roles: AppRoleAdmin[];
  sessions: AppSessionAdmin[];
}

export interface AppUserInitialPassword {
  user: AppUserAdmin;
  initialPassword: string;
}

export interface AppUserQuery extends PageQuery {
  username?: string;
  status?: AppIdentityStatus;
}

export interface CreateAppUserInput {
  username: string;
  displayName: string;
  phone?: string;
  email?: string;
  roleIds: string[];
}

export interface UpdateAppUserInput {
  displayName: string;
  phone?: string;
  email?: string;
  clearPhone?: boolean;
  clearEmail?: boolean;
  expectedIdentityRevision: AppRevision;
}

export interface ChangeAppUserStatusInput {
  status: Exclude<AppIdentityStatus, 'inactive'>;
  expectedIdentityRevision: AppRevision;
}

export interface ResetAppUserPasswordInput {
  expectedCredentialRevision: AppRevision;
}

export interface KickoutAppUserInput {
  reasonCode: AppKickoutReasonCode;
}

export interface ReplaceAppUserRolesInput {
  expectedPermissionRevision: AppRevision;
  roleIds: string[];
}

export interface AppRoleAdmin {
  id: string;
  roleCode: string;
  roleName: string;
  scopeType: AppRoleScopeType;
  builtIn: boolean;
  status: AppIdentityStatus;
  roleRevision: AppRevision;
  permissionIds: string[];
  userReferenceCount: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AppRoleQuery extends PageQuery {
  roleCode?: string;
  scopeType?: AppRoleScopeType;
  status?: AppIdentityStatus;
}

export interface CreateAppRoleInput {
  roleCode: string;
  roleName: string;
  scopeType: AppRoleScopeType;
  status: AppIdentityStatus;
}

export interface UpdateAppRoleInput {
  roleName: string;
  status: AppIdentityStatus;
  expectedRoleRevision: AppRevision;
}

export interface ReplaceAppRolePermissionsInput {
  expectedRoleRevision: AppRevision;
  permissionIds: string[];
}

export interface AppPermissionAdmin {
  id: string;
  permissionCode: string;
  permissionName: string;
  resourceType?: string | null;
  action?: string | null;
  status: AppIdentityStatus;
  permissionRevision: AppRevision;
}

export interface AppAuthClientAdmin {
  id: string;
  clientId: string;
  clientKey: string;
  grantTypes: string;
  accessPaths: string;
  ipWhitelist?: string | null;
  tokenTimeout: number;
  activeTimeout: number;
  status: AppIdentityStatus;
  clientRevision: AppRevision;
  hasSecret: boolean;
  activeSessionCount: number;
  createTime?: string | null;
  updateTime?: string | null;
}

export interface AppAuthClientSecret {
  client: AppAuthClientAdmin;
  clientSecret: string;
}

export interface AppAuthClientQuery extends PageQuery {
  clientKey?: string;
  status?: AppIdentityStatus;
}

export interface CreateAppAuthClientInput {
  clientKey: string;
  grantTypes: string;
  accessPaths: string;
  ipWhitelist?: string;
  tokenTimeout: number;
  activeTimeout: number;
  status: AppIdentityStatus;
}

export interface UpdateAppAuthClientInput extends CreateAppAuthClientInput {
  expectedClientRevision: AppRevision;
}

export interface RotateAppAuthClientSecretInput {
  expectedClientRevision: AppRevision;
}

export interface AppSessionAdmin {
  id: string;
  appUserId: string;
  clientId?: string | null;
  deviceName?: string | null;
  lastActiveAt?: string | null;
}

export interface AppSessionQuery extends PageQuery {
  appUserId?: string;
  clientId?: string;
}

export interface KickoutAppSessionInput {
  reasonCode: AppKickoutReasonCode;
}

export interface AppLoginLogAdmin {
  id: string;
  authMethod?: string | null;
  maskedIdentifier?: string | null;
  clientId?: string | null;
  resultCode?: number | null;
  failureCategory?: string | null;
  appUserId?: string | null;
  sessionId?: string | null;
  ipAddress?: string | null;
  deviceSummary?: string | null;
  requestId?: string | null;
  occurredAt?: string | null;
}

export interface AppLoginLogQuery extends PageQuery {
  appUserId?: string;
  clientId?: string;
  resultCode?: number;
  occurredAtRange?: [ConfigType, ConfigType] | null;
}

export interface AppSecurityAuditAdmin {
  id: string;
  resourceType?: string | null;
  resourceId?: string | null;
  action?: string | null;
  actorType?: AppActorType | null;
  actorId?: string | null;
  beforeDigest?: string | null;
  afterDigest?: string | null;
  reason?: string | null;
  requestId?: string | null;
  ipAddress?: string | null;
  occurredAt?: string | null;
}

export interface AppSecurityAuditQuery extends PageQuery {
  resourceType?: string;
  resourceId?: string;
  action?: string;
  actorType?: AppActorType;
  actorId?: string;
  occurredAtRange?: [ConfigType, ConfigType] | null;
}

export interface AppUserFormValues {
  id?: string;
  username?: string;
  displayName?: string;
  phone?: string;
  email?: string;
  clearPhone?: boolean;
  clearEmail?: boolean;
  roleIds?: string[];
  expectedIdentityRevision?: AppRevision;
}
