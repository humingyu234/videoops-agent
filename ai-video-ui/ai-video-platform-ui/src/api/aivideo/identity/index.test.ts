import { beforeEach, describe, expect, it, vi } from 'vitest';
import request from '@/api/request';
import {
  changeAppUserStatus,
  createAppAuthClient,
  createAppRole,
  createAppUser,
  kickoutAppSession,
  kickoutAppUser,
  pageAppAuthClients,
  pageAppLoginLogs,
  pageAppRoles,
  pageAppSecurityAudits,
  pageAppSessions,
  pageAppUsers,
  replaceAppRolePermissions,
  replaceAppUserRoles,
  resetAppUserPassword,
  rotateAppAuthClientSecret,
  updateAppAuthClient,
  updateAppRole,
  updateAppUser
} from './index';

vi.mock('@/api/request', () => ({
  default: vi.fn()
}));

const requestMock = vi.mocked(request);

describe('创作端身份管理 API 适配器', () => {
  beforeEach(() => {
    requestMock.mockReset();
    requestMock.mockResolvedValue({
      code: 200,
      msg: '操作成功',
      data: {
        total: 0,
        rows: []
      }
    });
  });

  it('分页适配器只访问运营端 app 管理资源，并将 ProTable 分页转换为 RuoYi 参数', async () => {
    const users = await pageAppUsers({ current: 2, pageSize: 20, username: 'creator', status: 'active' });
    await pageAppRoles({ current: 1, pageSize: 10, roleCode: 'personal_creator' });
    await pageAppAuthClients({ current: 1, pageSize: 10, clientKey: 'desktop' });
    await pageAppSessions({ current: 1, pageSize: 10, appUserId: '9007199254740993' });
    await pageAppLoginLogs({ current: 1, pageSize: 10, clientId: 'client-web' });
    await pageAppSecurityAudits({ current: 1, pageSize: 10, actorType: 'sys_user' });

    expect(users).toEqual({ data: [], total: 0, success: true });
    expect(requestMock).toHaveBeenNthCalledWith(
      1,
      expect.objectContaining({
        method: 'get',
        params: { pageNum: 2, pageSize: 20, status: 'active', username: 'creator' },
        url: '/api/admin/app-users'
      })
    );

    const urls = requestMock.mock.calls.map(([config]) => config.url);
    expect(urls).toEqual([
      '/api/admin/app-users',
      '/api/admin/app-roles',
      '/api/admin/app-auth-clients',
      '/api/admin/app-sessions',
      '/api/admin/app-login-logs',
      '/api/admin/app-security-audits'
    ]);
    expect(urls.every(url => url?.startsWith('/api/admin/app-'))).toBe(true);
    expect(urls).not.toContain('/api/auth/login');
  });

  it('敏感写操作只经运营端 app 管理资源，不附带创作端令牌或冒充参数', async () => {
    await createAppUser({
      displayName: '创作者',
      email: 'creator@example.com',
      roleIds: ['role-1'],
      username: 'creator'
    });
    await updateAppUser('user-1', { displayName: '新名称', expectedIdentityRevision: '7' });
    await changeAppUserStatus('user-1', { expectedIdentityRevision: '7', status: 'disabled' });
    await resetAppUserPassword('user-1', { expectedCredentialRevision: '8' });
    await kickoutAppUser('user-1', { reasonCode: 'admin_kickout_security' });
    await replaceAppUserRoles('user-1', { expectedPermissionRevision: '9', roleIds: ['role-2'] });
    await createAppRole({ roleCode: 'creator_role', roleName: '创作者角色', scopeType: 'personal', status: 'active' });
    await updateAppRole('role-1', { expectedRoleRevision: '10', roleName: '新角色名称', status: 'active' });
    await replaceAppRolePermissions('role-1', { expectedRoleRevision: '10', permissionIds: ['permission-1'] });
    await createAppAuthClient({
      accessPaths: '/studio/**',
      activeTimeout: 1800,
      clientKey: 'creator-web',
      grantTypes: 'password',
      status: 'active',
      tokenTimeout: 3600
    });
    await updateAppAuthClient('client-1', {
      accessPaths: '/studio/**',
      activeTimeout: 1800,
      clientKey: 'creator-web',
      expectedClientRevision: '11',
      grantTypes: 'password',
      status: 'active',
      tokenTimeout: 3600
    });
    await rotateAppAuthClientSecret('client-1', { expectedClientRevision: '11' });
    await kickoutAppSession('session-1', { reasonCode: 'admin_kickout_security' });

    const calls = requestMock.mock.calls.map(([config]) => config);

    expect(calls).toEqual([
      expect.objectContaining({ method: 'post', url: '/api/admin/app-users' }),
      expect.objectContaining({ method: 'put', url: '/api/admin/app-users/user-1' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-users/user-1/status-changes' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-users/user-1/password-resets' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-users/user-1/kickouts' }),
      expect.objectContaining({ method: 'put', url: '/api/admin/app-users/user-1/roles' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-roles' }),
      expect.objectContaining({ method: 'put', url: '/api/admin/app-roles/role-1' }),
      expect.objectContaining({ method: 'put', url: '/api/admin/app-roles/role-1/permissions' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-auth-clients' }),
      expect.objectContaining({ method: 'put', url: '/api/admin/app-auth-clients/client-1' }),
      expect.objectContaining({ method: 'post', url: '/api/admin/app-auth-clients/client-1/secret-rotations' }),
      expect.objectContaining({ method: 'delete', url: '/api/admin/app-sessions/session-1' })
    ]);
    expect(calls.every(call => call.url?.startsWith('/api/admin/app-'))).toBe(true);
    expect(calls.flatMap(call => Object.keys(call))).not.toContain('headers');
    const forbiddenDirectIdentityKeys = ['accessToken', 'authorization', 'clientid', 'impersonateUserId', 'token'];
    const directIdentityKeys = calls.flatMap(call => Object.keys(call.data ?? {}));

    expect(directIdentityKeys.filter(key => forbiddenDirectIdentityKeys.includes(key))).toEqual([]);
  });
});
