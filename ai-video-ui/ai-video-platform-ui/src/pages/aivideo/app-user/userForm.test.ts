import { describe, expect, it } from 'vitest';
import { toAppUserUpdateInput } from './userForm';

describe('创作端用户资料编辑表单', () => {
  it('仅提交运营人员新输入的联系方式；空值保持原值，清空使用显式标记', () => {
    expect(
      toAppUserUpdateInput({
        displayName: '新的显示名称',
        expectedIdentityRevision: '7',
        id: '9007199254740993',
        phone: ''
      })
    ).toEqual({
      displayName: '新的显示名称',
      expectedIdentityRevision: '7'
    });

    expect(
      toAppUserUpdateInput({
        clearEmail: true,
        displayName: '新的显示名称',
        expectedIdentityRevision: '7',
        id: '9007199254740993',
        phone: ' 13800138000 '
      })
    ).toEqual({
      clearEmail: true,
      displayName: '新的显示名称',
      expectedIdentityRevision: '7',
      phone: '13800138000'
    });
  });

  it('accepts a numeric revision returned by the API', () => {
    expect(
      toAppUserUpdateInput({
        displayName: '新的显示名称',
        expectedIdentityRevision: 7,
        id: '9007199254740993'
      })
    ).toEqual({
      displayName: '新的显示名称',
      expectedIdentityRevision: 7
    });
  });

  it('rejects masked contact text instead of sending it to the API', () => {
    const baseValues = {
      displayName: '新的显示名称',
      expectedIdentityRevision: 7,
      id: '9007199254740993'
    };

    expect(toAppUserUpdateInput({ ...baseValues, phone: '138****8000' })).toBeUndefined();
    expect(toAppUserUpdateInput({ ...baseValues, email: 'cre***@example.com' })).toBeUndefined();
  });

  it('拒绝同时输入联系方式和对应清空标记，避免向后端发送冲突请求', () => {
    expect(
      toAppUserUpdateInput({
        clearPhone: true,
        displayName: '新的显示名称',
        expectedIdentityRevision: '7',
        id: '9007199254740993',
        phone: '13800138000'
      })
    ).toBeUndefined();
  });
});
