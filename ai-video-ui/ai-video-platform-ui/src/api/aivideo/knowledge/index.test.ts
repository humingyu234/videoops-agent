import { beforeEach, describe, expect, expectTypeOf, it, vi } from 'vitest';
import request from '@/api/request';
import type { KnowledgeItemDetail, KnowledgeItemId, KnowledgeItemSaveForm } from './types';
import {
  addKnowledgeItem,
  deleteKnowledgeItem,
  getKnowledgeItem,
  importKnowledgeItems,
  pageKnowledgeItems,
  updateKnowledgeItem,
  updateKnowledgeStatus
} from './index';

vi.mock('@/api/request', () => ({
  default: vi.fn()
}));

const requestMock = vi.mocked(request);

describe('运营端知识库 API', () => {
  beforeEach(() => {
    requestMock.mockReset();
  });

  it('对外暴露字符串 ID 和真实详情字段', () => {
    expectTypeOf<KnowledgeItemId>().toEqualTypeOf<string>();
    expectTypeOf<KnowledgeItemDetail>().toEqualTypeOf<{
      id: string;
      name: string;
      knowledgeType: 'primary_template' | 'writing_technique' | 'psychology' | 'case' | 'mandatory_rule';
      status: 'draft' | 'reviewing' | 'published' | 'retired';
      versionNo: number;
      summary: string | null;
      content: string;
      updateTime: string | null;
    }>();
    expectTypeOf(addKnowledgeItem).returns.resolves.toEqualTypeOf<KnowledgeItemId>();
  });

  it('保留 ProTable 分页响应适配', async () => {
    requestMock.mockResolvedValue({
      code: 200,
      msg: '操作成功',
      data: { rows: [{ id: '1', name: '口播规范' }], total: 1 }
    });

    const result = await pageKnowledgeItems({
      current: 2,
      knowledgeType: 'writing_technique',
      name: '口播',
      pageSize: 20,
      status: 'published'
    });

    expect(result).toEqual({ data: [{ id: '1', name: '口播规范' }], success: true, total: 1 });
    expect(requestMock).toHaveBeenCalledWith({
      method: 'get',
      params: {
        knowledgeType: 'writing_technique',
        name: '口播',
        pageNum: 2,
        pageSize: 20,
        status: 'published'
      },
      url: '/api/admin/knowledge-items'
    });
  });

  it('查询知识详情', async () => {
    const detail = {
      content: '开头要先给出核心利益',
      id: '9007199254740993',
      knowledgeType: 'primary_template',
      name: '口播模板',
      summary: null,
      status: 'draft',
      updateTime: '2026-08-03 17:20:00',
      versionNo: 1
    };
    requestMock.mockResolvedValue({ code: 200, data: detail, msg: '操作成功' });

    await expect(getKnowledgeItem('9007199254740993')).resolves.toEqual(detail);
    expect(requestMock).toHaveBeenCalledWith({
      method: 'get',
      url: '/api/admin/knowledge-items/9007199254740993'
    });
  });

  it('新增、修改和删除知识', async () => {
    requestMock
      .mockResolvedValueOnce({ code: 200, data: '9007199254740993', msg: '操作成功' })
      .mockResolvedValue({ code: 200, data: undefined, msg: '操作成功' });
    const form: KnowledgeItemSaveForm = {
      content: '知识正文',
      knowledgeType: 'mandatory_rule',
      name: '必须遵守的规则',
      status: 'reviewing',
      summary: '用于问卷的强制规则'
    };

    await expect(addKnowledgeItem(form)).resolves.toBe('9007199254740993');
    await updateKnowledgeItem('9007199254740993', form);
    await deleteKnowledgeItem('9007199254740993');

    expect(requestMock).toHaveBeenNthCalledWith(1, {
      data: form,
      method: 'post',
      url: '/api/admin/knowledge-items'
    });
    expect(requestMock).toHaveBeenNthCalledWith(2, {
      data: form,
      method: 'put',
      url: '/api/admin/knowledge-items/9007199254740993'
    });
    expect(requestMock).toHaveBeenNthCalledWith(3, {
      method: 'delete',
      url: '/api/admin/knowledge-items/9007199254740993'
    });
  });

  it('通过独立接口修改知识状态', async () => {
    requestMock.mockResolvedValue({ code: 200, data: undefined, msg: '操作成功' });

    await updateKnowledgeStatus('9007199254740993', 'retired');

    expect(requestMock).toHaveBeenCalledWith({
      data: { status: 'retired' },
      method: 'put',
      url: '/api/admin/knowledge-items/9007199254740993/status'
    });
  });

  it('导入知识库时为每个文件按顺序携带可编辑元数据', async () => {
    const summary = { failedCount: 0, files: [], skippedCount: 0, successCount: 2, totalCount: 2 };
    requestMock.mockResolvedValue({ code: 200, data: summary, msg: '操作成功' });
    const primary = new File(['模板正文'], '模板.txt', { type: 'text/plain' });
    const caseFile = new File(['{"title":"案例"}'], '案例.json', { type: 'application/json' });

    const result = await importKnowledgeItems({
      rows: [
        { file: primary, knowledgeType: 'primary_template', name: '电商模板', status: 'draft' },
        { file: caseFile, knowledgeType: 'case', name: '成功案例', status: 'published' }
      ]
    });

    expect(result).toEqual(summary);
    const config = requestMock.mock.calls[0][0];
    expect(config).toEqual(expect.objectContaining({ method: 'post', url: '/api/admin/knowledge-items/imports' }));
    expect(config.data).toBeInstanceOf(FormData);
    const formData = config.data as FormData;
    expect(formData.getAll('files')).toEqual([primary, caseFile]);
    expect(formData.getAll('names')).toEqual(['电商模板', '成功案例']);
    expect(formData.getAll('knowledgeTypes')).toEqual(['primary_template', 'case']);
    expect(formData.getAll('statuses')).toEqual(['draft', 'published']);
  });
});
