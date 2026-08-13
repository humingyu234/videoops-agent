import { beforeEach, describe, expect, it, vi } from 'vitest';
import request from '@/api/request';
import {
  createRunningHubAccount,
  deleteRunningHubAccount,
  disableRunningHubAccount,
  enableRunningHubAccount,
  getRunningHubAccount,
  pageRunningHubAccounts,
  updateRunningHubAccount
} from './index';

vi.mock('@/api/request', () => ({ default: vi.fn() }));

const requestMock = vi.mocked(request);

describe('运营端 RunningHub 账号 API', () => {
  beforeEach(() => requestMock.mockReset());

  it('将列表参数映射为分页并只消费脱敏账号状态', async () => {
    const row = {
      accountId: '9007199254740993',
      accountName: '主账号',
      apiKeyMasked: 'rh_****1234',
      enabled: true,
      hasApiKey: true,
      rowRevision: 3
    };
    requestMock.mockResolvedValue({ code: 200, data: { rows: [row], total: 1 }, msg: '操作成功' });

    await expect(pageRunningHubAccounts({ current: 3, enabled: true, keyword: '主', pageSize: 10 })).resolves.toEqual({
      data: [row],
      success: true,
      total: 1
    });
    expect(requestMock).toHaveBeenCalledWith({
      method: 'get',
      params: { enabled: true, keyword: '主', pageNum: 3, pageSize: 10 },
      url: '/api/admin/runninghub-accounts'
    });
  });

  it('使用固定资源完成新增、详情、修改和带修订号删除', async () => {
    requestMock
      .mockResolvedValueOnce({ code: 200, data: '91', msg: '操作成功' })
      .mockResolvedValueOnce({ code: 200, data: { accountId: '91', apiKeyMasked: '****' }, msg: '操作成功' })
      .mockResolvedValue({ code: 200, data: undefined, msg: '操作成功' });

    await expect(createRunningHubAccount({ accountName: '主账号', apiKey: 'secret' })).resolves.toBe('91');
    await getRunningHubAccount('91');
    await updateRunningHubAccount('91', { accountName: '主账号 2', apiKey: '', expectedRevision: 2 });
    await deleteRunningHubAccount('91', 3);

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      data: { accountName: '主账号', apiKey: 'secret' },
      headers: { repeatSubmit: false },
      method: 'post',
      url: '/api/admin/runninghub-accounts'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      method: 'get',
      url: '/api/admin/runninghub-accounts/91'
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      data: { accountName: '主账号 2', apiKey: '', expectedRevision: 2 },
      headers: { repeatSubmit: false },
      method: 'put',
      url: '/api/admin/runninghub-accounts/91'
    });
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      method: 'delete',
      params: { expectedRevision: 3 },
      url: '/api/admin/runninghub-accounts/91'
    });
  });

  it('启用和停用均为 POST，不定义额外 update-key 端点', async () => {
    requestMock.mockResolvedValue({ code: 200, data: undefined, msg: '操作成功' });

    await enableRunningHubAccount('91', 4);
    await disableRunningHubAccount('91', 5);

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      data: { expectedRevision: 4 },
      method: 'post',
      url: '/api/admin/runninghub-accounts/91/enable'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      data: { expectedRevision: 5 },
      method: 'post',
      url: '/api/admin/runninghub-accounts/91/disable'
    });
    expect(requestMock.mock.calls.flatMap(([config]) => [config.url])).not.toContain(
      '/api/admin/runninghub-accounts/91/update-key'
    );
  });
});
