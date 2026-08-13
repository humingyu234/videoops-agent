import { beforeEach, describe, expect, it, vi } from 'vitest';
import request from '@/api/request';
import type { WorkflowExecutionConfigSave, WorkflowTemplateSave } from './types';
import {
  createWorkflowTemplate,
  deleteWorkflowTemplate,
  disableWorkflowTemplate,
  enableWorkflowTemplate,
  getWorkflowExecutionConfig,
  getWorkflowTemplate,
  listWorkflowTemplateOptions,
  pageWorkflowTemplates,
  saveWorkflowExecutionConfig,
  updateWorkflowTemplate
} from './index';

vi.mock('@/api/request', () => ({ default: vi.fn() }));

const requestMock = vi.mocked(request);

describe('运营端工作流模板 API', () => {
  beforeEach(() => requestMock.mockReset());

  it('将列表参数映射为后端分页并返回 ProTable 结果', async () => {
    requestMock.mockResolvedValue({
      code: 200,
      data: { rows: [{ templateId: '9007199254740993', name: '商品口播' }], total: 1 },
      msg: '操作成功'
    });

    await expect(
      pageWorkflowTemplates({
        categoryId: '11',
        channel: 'video_template',
        current: 2,
        keyword: '口播',
        pageSize: 20,
        recommended: true,
        sort: 'latest',
        status: 'draft'
      })
    ).resolves.toEqual({ data: [{ templateId: '9007199254740993', name: '商品口播' }], success: true, total: 1 });
    expect(requestMock).toHaveBeenCalledWith({
      method: 'get',
      params: {
        categoryId: '11',
        channel: 'video_template',
        keyword: '口播',
        pageNum: 2,
        pageSize: 20,
        recommended: true,
        sort: 'latest',
        status: 'draft'
      },
      url: '/api/admin/workflow-templates'
    });
  });

  it('使用基础资源完成新增、详情、修改和带修订号删除', async () => {
    requestMock
      .mockResolvedValueOnce({ code: 200, data: '9007199254740993', msg: '操作成功' })
      .mockResolvedValueOnce({ code: 200, data: { templateId: '9007199254740993' }, msg: '操作成功' })
      .mockResolvedValue({ code: 200, data: undefined, msg: '操作成功' });
    const input: WorkflowTemplateSave = {
      categoryId: '11',
      channel: 'video_template',
      coverAssetId: undefined,
      description: '详情',
      estimatedDurationSeconds: 60,
      formSchema: { fields: [], schemaVersion: 'workflow-form-1' },
      name: '商品口播',
      recommended: false,
      sortNo: 0,
      summary: '摘要',
      tagIds: ['21']
    };

    await expect(createWorkflowTemplate(input)).resolves.toBe('9007199254740993');
    await getWorkflowTemplate('9007199254740993');
    await updateWorkflowTemplate('9007199254740993', { ...input, expectedRevision: 3 });
    await deleteWorkflowTemplate('9007199254740993', 4);

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      data: input,
      method: 'post',
      url: '/api/admin/workflow-templates'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      method: 'get',
      url: '/api/admin/workflow-templates/9007199254740993'
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      data: { ...input, expectedRevision: 3 },
      method: 'put',
      url: '/api/admin/workflow-templates/9007199254740993'
    });
    expect(requestMock).toHaveBeenNthCalledWith(4, {
      method: 'delete',
      params: { expectedRevision: 4 },
      url: '/api/admin/workflow-templates/9007199254740993'
    });
  });

  it('通过独立 execution-config 资源读写唯一配置', async () => {
    const input: WorkflowExecutionConfigSave = {
      clearAccessPassword: false,
      enabled: true,
      executionMode: 'runninghub_workflow',
      expectedRevision: 2,
      inputMapping: { prompt: 'prompt' },
      outputPolicy: {},
      runningHubAccountId: '31',
      timeoutSeconds: 600,
      workflowId: 'wf-1'
    };
    requestMock.mockResolvedValue({ code: 200, data: { ...input, executionConfigId: '41' }, msg: '操作成功' });

    await getWorkflowExecutionConfig('9007199254740993');
    await saveWorkflowExecutionConfig('9007199254740993', input);

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      method: 'get',
      url: '/api/admin/workflow-templates/9007199254740993/execution-config'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      data: input,
      headers: { repeatSubmit: false },
      method: 'put',
      url: '/api/admin/workflow-templates/9007199254740993/execution-config'
    });
  });

  it('启停均为 POST，并通过固定 options 资源取选项', async () => {
    requestMock.mockResolvedValue({ code: 200, data: [], msg: '操作成功' });

    await enableWorkflowTemplate('77', 5);
    await disableWorkflowTemplate('77', 6);
    await listWorkflowTemplateOptions();

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      data: { expectedRevision: 5 },
      method: 'post',
      url: '/api/admin/workflow-templates/77/enable'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      data: { expectedRevision: 6 },
      method: 'post',
      url: '/api/admin/workflow-templates/77/disable'
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      method: 'get',
      url: '/api/admin/workflow-templates/options'
    });
  });
});
