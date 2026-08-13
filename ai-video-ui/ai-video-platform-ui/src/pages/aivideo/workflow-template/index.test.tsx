import type { ReactNode } from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createWorkflowTemplate,
  deleteWorkflowTemplate,
  disableWorkflowTemplate,
  enableWorkflowTemplate,
  saveWorkflowExecutionConfig
} from '@/api/aivideo/workflow-template';
import { useUserStore } from '@/stores/userStore';
import {
  buildWorkflowTemplateFormValues,
  JsonFormFieldError,
  mergeRunningHubCandidates,
  runningHubExecutionModeOptions,
  toWorkflowTemplatePayloads
} from './components/templateFormModel';
import WorkflowTemplatePage from './index';

const apiMocks = vi.hoisted(() => ({
  createWorkflowTemplate: vi.fn(),
  deleteWorkflowTemplate: vi.fn(),
  disableWorkflowTemplate: vi.fn(),
  enableWorkflowTemplate: vi.fn(),
  getWorkflowExecutionConfig: vi.fn(),
  getWorkflowTemplate: vi.fn(),
  pageWorkflowTemplates: vi.fn(async () => ({ data: [], success: true, total: 0 })),
  saveWorkflowExecutionConfig: vi.fn(),
  updateWorkflowTemplate: vi.fn()
}));
const accountApiMocks = vi.hoisted(() => ({
  pageRunningHubAccounts: vi.fn(async () => ({ data: [], success: true, total: 0 }))
}));
const dictApiMocks = vi.hoisted(() => ({
  getDicts: vi.fn(async () => ({
    code: 200,
    data: [{ dictCode: '1', dictLabel: '通用', dictSort: 0, dictType: 'aivideo_discovery_category', dictValue: '1' }]
  }))
}));

const templateRow = {
  accountName: '主账号',
  categoryId: '11',
  categoryName: '电商',
  channel: 'video_template',
  executionConfigured: true,
  executionEnabled: true,
  name: '商品口播',
  recommended: false,
  rowRevision: 5,
  slug: 'product-pitch',
  status: 'disabled',
  templateId: '71'
};

const validFormValues = {
  categoryId: '11',
  channel: 'video_template' as const,
  description: '详情',
  estimatedDurationSeconds: 60,
  executionEnabled: true,
  executionMode: 'runninghub_workflow' as const,
  formSchemaText: '{"schemaVersion":"workflow-form-1","fields":[]}',
  inputMappingText: '{"prompt":"prompt"}',
  name: '商品口播',
  recommended: false,
  runningHubAccountId: '31',
  sortNo: 0,
  summary: '摘要',
  tagIdsText: '21,22',
  timeoutSeconds: 600,
  workflowId: 'wf-1'
};

vi.mock('@/api/aivideo/workflow-template', () => apiMocks);
vi.mock('@/api/aivideo/runninghub-account', () => accountApiMocks);
vi.mock('@/api/system/dict/data', () => dictApiMocks);

vi.mock('@/components/common/RowActions', () => ({
  default: ({
    actions
  }: {
    actions: Array<
      false | null | undefined | { key: string; label: string; confirm?: ReactNode; onClick?: () => void }
    >;
  }) => (
    <div>
      {actions.filter(Boolean).map(action => {
        if (!action) return null;
        return (
          <button key={action.key} data-confirm={String(action.confirm || '')} type="button" onClick={action.onClick}>
            {action.label}
          </button>
        );
      })}
    </div>
  )
}));

vi.mock('@ant-design/pro-components', () => ({
  PageContainer: ({ children }: { children: ReactNode }) => <section>{children}</section>,
  ProTable: ({
    columns,
    pagination,
    toolBarRender
  }: {
    columns: Array<{ render?: (node: unknown, row: typeof templateRow) => ReactNode; valueType?: string }>;
    pagination?: object;
    toolBarRender?: () => ReactNode[];
  }) => {
    const option = columns.find(column => column.valueType === 'option');
    return (
      <div>
        <span data-testid="template-pagination">{JSON.stringify(pagination)}</span>
        {toolBarRender?.()}
        {option?.render?.(null, templateRow)}
      </div>
    );
  }
}));

vi.mock('./components/WorkflowTemplateEditor', () => ({
  default: ({
    accountName,
    errorMessage,
    readonly,
    submitting,
    onClose,
    onFinish
  }: {
    accountName?: string | null;
    errorMessage?: string;
    readonly?: boolean;
    submitting: boolean;
    onClose: () => void;
    onFinish: (values: typeof validFormValues) => Promise<boolean>;
  }) => (
    <div
      data-testid="template-editor"
      data-account-name={accountName || ''}
      data-error-message={errorMessage || ''}
      data-readonly={String(Boolean(readonly))}
      data-submitting={String(submitting)}
    >
      {errorMessage && <span>{errorMessage}</span>}
      {!readonly && (
        <>
          <button disabled={submitting} type="button" onClick={() => void onFinish(validFormValues)}>
            保存模板
          </button>
          <button
            disabled={submitting}
            type="button"
            onClick={() =>
              void onFinish({
                ...validFormValues,
                inputMappingText: '{"prompt":"changed-after-failure"}',
                name: '失败后篡改的模板'
              })
            }
          >
            保存失败后修改
          </button>
        </>
      )}
      <button disabled={submitting} type="button" onClick={onClose}>
        返回列表
      </button>
    </div>
  )
}));

function grant(...permissions: string[]) {
  useUserStore.getState().setUserInfo({
    permissions,
    roles: [],
    user: { userId: '100', userName: 'operator' }
  });
}

describe('工作流模板管理页', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useUserStore.getState().clearUserInfo();
    apiMocks.createWorkflowTemplate.mockResolvedValue('71');
    apiMocks.deleteWorkflowTemplate.mockResolvedValue(undefined);
    apiMocks.disableWorkflowTemplate.mockResolvedValue(undefined);
    apiMocks.enableWorkflowTemplate.mockResolvedValue(undefined);
    apiMocks.getWorkflowExecutionConfig.mockResolvedValue({
      enabled: true,
      executionConfigId: '81',
      executionMode: 'runninghub_workflow',
      hasAccessPassword: true,
      inputMapping: { prompt: 'prompt' },
      outputPolicy: { primary: 'video' },
      rowRevision: 2,
      runningHubAccountId: '31',
      templateId: '71',
      timeoutSeconds: 600,
      workflowId: 'wf-1'
    });
    apiMocks.getWorkflowTemplate.mockResolvedValue({
      ...templateRow,
      billingMode: 'free',
      coverAssetId: null,
      createTime: null,
      description: '详情',
      estimatedDurationSeconds: 60,
      executionConfig: null,
      formSchema: { fields: [], schemaVersion: 'workflow-form-1' },
      schemaHash: 'sha256:test',
      sortNo: 0,
      summary: '摘要',
      tagIds: ['21', '22']
    });
    apiMocks.saveWorkflowExecutionConfig.mockResolvedValue({ executionConfigId: '81' });
  });

  it('没有查询权限时展示 403', () => {
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    expect(screen.getByText('无权限访问')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增模板' })).not.toBeInTheDocument();
  });

  it('使用分页表格，并为删除和人工验证启用提供明确确认', async () => {
    grant(
      'aivideo:workflow-template:query',
      'aivideo:workflow-template:remove',
      'aivideo:workflow-template:enable',
      'aivideo:workflow-template:disable'
    );
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    expect(screen.getByTestId('template-pagination')).toHaveTextContent('"defaultPageSize":10');
    expect(screen.getByRole('button', { name: '删除' })).toHaveAttribute(
      'data-confirm',
      expect.stringContaining('确认删除')
    );
    expect(screen.getByRole('button', { name: '启用' })).toHaveAttribute(
      'data-confirm',
      expect.stringContaining('已人工验证模板与配置')
    );

    fireEvent.click(screen.getByRole('button', { name: '删除' }));
    fireEvent.click(screen.getByRole('button', { name: '启用' }));
    await waitFor(() => expect(deleteWorkflowTemplate).toHaveBeenCalledWith('71', 5));
    expect(enableWorkflowTemplate).toHaveBeenCalledWith('71', 5);
    expect(disableWorkflowTemplate).not.toHaveBeenCalled();
  });

  it('仅有模板查询权限时可查看只读详情，且查看不加载账号列表', async () => {
    grant('aivideo:workflow-template:query');
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    expect(screen.queryByRole('button', { name: '修改' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '查看' }));

    await waitFor(() => expect(apiMocks.getWorkflowTemplate).toHaveBeenCalledWith('71'));
    expect(apiMocks.getWorkflowExecutionConfig).toHaveBeenCalledWith('71');
    expect(accountApiMocks.pageRunningHubAccounts).not.toHaveBeenCalled();
    expect(await screen.findByTestId('template-editor')).toHaveAttribute('data-readonly', 'true');
    expect(screen.getByTestId('template-editor')).toHaveAttribute('data-account-name', '主账号');
    expect(screen.queryByRole('button', { name: '保存模板' })).not.toBeInTheDocument();
  });

  it('缺少 RunningHub 账号查询权限时隐藏模板新增和修改', () => {
    grant('aivideo:workflow-template:query', 'aivideo:workflow-template:add', 'aivideo:workflow-template:edit');
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    expect(screen.getByRole('button', { name: '查看' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '新增模板' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '修改' })).not.toBeInTheDocument();
  });

  it('新增打开后隐藏列表，返回后恢复列表', async () => {
    grant('aivideo:workflow-template:query', 'aivideo:workflow-template:add', 'aivideo:runninghub-account:query');
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    expect(screen.getByTestId('template-pagination')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));

    expect(await screen.findByTestId('template-editor')).toBeInTheDocument();
    expect(screen.queryByTestId('template-pagination')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '返回列表' }));

    expect(await screen.findByTestId('template-pagination')).toBeInTheDocument();
    expect(screen.queryByTestId('template-editor')).not.toBeInTheDocument();
  });

  it('模板详情加载失败时仍留在列表', async () => {
    apiMocks.getWorkflowTemplate.mockRejectedValueOnce(new Error('detail failed'));
    grant('aivideo:workflow-template:query');
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '查看' }));

    await waitFor(() => expect(apiMocks.getWorkflowTemplate).toHaveBeenCalledWith('71'));
    expect(accountApiMocks.pageRunningHubAccounts).not.toHaveBeenCalled();
    expect(screen.queryByTestId('template-editor')).not.toBeInTheDocument();
    expect(screen.getByTestId('template-pagination')).toBeInTheDocument();
  });

  it('配置失败后锁定编辑器，并用首次 payload 只重试配置且仍可返回', async () => {
    apiMocks.saveWorkflowExecutionConfig.mockRejectedValueOnce(new Error('config failed'));
    grant('aivideo:workflow-template:query', 'aivideo:workflow-template:add', 'aivideo:runninghub-account:query');
    render(
      <App>
        <WorkflowTemplatePage />
      </App>
    );

    fireEvent.click(screen.getByRole('button', { name: '新增模板' }));
    fireEvent.click(await screen.findByRole('button', { name: '保存模板' }));

    await waitFor(() => expect(createWorkflowTemplate).toHaveBeenCalledTimes(1));
    expect(await screen.findByText('草稿已保存，配置保存失败')).toBeInTheDocument();
    expect(screen.getByTestId('template-editor')).toHaveAttribute('data-submitting', 'true');
    const firstConfigPayload = apiMocks.saveWorkflowExecutionConfig.mock.calls[0]?.[1];
    const modifiedSave = screen.getByRole('button', { name: '保存失败后修改' });
    expect(modifiedSave).toBeDisabled();
    fireEvent.click(modifiedSave);
    expect(saveWorkflowExecutionConfig).toHaveBeenCalledTimes(1);
    expect(screen.getAllByRole('button', { name: '返回列表' }).some(button => !button.hasAttribute('disabled'))).toBe(
      true
    );

    fireEvent.click(screen.getByRole('button', { name: '重试保存配置' }));
    await waitFor(() => expect(saveWorkflowExecutionConfig).toHaveBeenCalledTimes(2));
    expect(saveWorkflowExecutionConfig).toHaveBeenNthCalledWith(2, '71', firstConfigPayload);
    expect(createWorkflowTemplate).toHaveBeenCalledTimes(1);
    expect(enableWorkflowTemplate).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.queryByTestId('template-editor')).not.toBeInTheDocument());
  });

  it('仅提供两个 RunningHub 模式，并将 JSON 错误定位到具体字段', () => {
    expect(runningHubExecutionModeOptions).toEqual([
      { label: 'RunningHub AI App（默认）', value: 'runninghub_ai_app' },
      { label: 'RunningHub Workflow', value: 'runninghub_workflow' }
    ]);
    expect(buildWorkflowTemplateFormValues().executionMode).toBe('runninghub_ai_app');
    expect(buildWorkflowTemplateFormValues().timeoutSeconds).toBe(21600);
    const payloads = toWorkflowTemplatePayloads(validFormValues);
    expect(payloads.config).toEqual(expect.objectContaining({ webAppId: undefined, workflowId: 'wf-1' }));
    expect(payloads.template).not.toHaveProperty('slug');
    expect(toWorkflowTemplatePayloads({ ...validFormValues, instanceType: 'plus' }).config.instanceType).toBe('plus');
    expect(toWorkflowTemplatePayloads({
      ...validFormValues,
      executionMode: 'runninghub_ai_app',
      instanceType: 'plus',
      webAppId: '2084534713108226049',
      workflowId: undefined
    }).config.instanceType).toBe('plus');

    try {
      toWorkflowTemplatePayloads({ ...validFormValues, inputMappingText: '{' });
      throw new Error('应当抛出 JSON 字段错误');
    } catch (error) {
      expect(error).toBeInstanceOf(JsonFormFieldError);
      expect((error as JsonFormFieldError).field).toBe('inputMappingText');
    }
  });

  it('把结构化参数编辑值转换为用户表单和 RunningHub nodeInfoList 映射', () => {
    const payloads = toWorkflowTemplatePayloads({
      ...validFormValues,
      executionMode: 'runninghub_ai_app',
      formSchemaText: undefined,
      inputMappingText: undefined,
      parameters: [
        {
          control: 'textarea',
          description: '描述要生成的画面',
          fieldName: 'prompt',
          inputKey: 'prompt',
          label: '提示词',
          nodeId: '122',
          remoteValueType: 'STRING',
          required: true
        },
        {
          control: 'image',
          fieldName: 'image',
          inputKey: 'referenceImage',
          label: '参考图',
          nodeId: '39',
          remoteValueType: 'IMAGE',
          required: false
        }
      ],
      webAppId: '1877265245566922753',
      workflowId: undefined
    });

    expect(payloads.template.formSchema).toEqual({
      fields: [
        {
          control: 'textarea',
          description: '描述要生成的画面',
          inputKey: 'prompt',
          label: '提示词',
          required: true,
          valueType: 'string'
        },
        {
          constraints: { assetType: 'image', maxItems: 1 },
          control: 'image',
          inputKey: 'referenceImage',
          label: '参考图',
          required: false,
          valueType: 'asset_array'
        }
      ],
      schemaVersion: 'workflow-form-1'
    });
    expect(payloads.config.inputMapping).toEqual({
      prompt: {
        fieldName: 'prompt',
        inputKey: 'prompt',
        nodeId: '122',
        remoteValueType: 'STRING',
        required: true,
        valueTransform: 'identity',
        valueType: 'string'
      },
      referenceImage: {
        fieldName: 'image',
        inputKey: 'referenceImage',
        nodeId: '39',
        remoteValueType: 'IMAGE',
        required: false,
        valueTransform: 'runninghub_file_name',
        valueType: 'asset_array'
      }
    });
    expect(payloads.config.outputPolicy).toEqual({});
  });

  it('编辑现有模板时保留未暴露在结构化表单里的字段约束和映射转换', () => {
    const values = buildWorkflowTemplateFormValues(
      {
        categoryId: '11',
        categoryName: '电商',
        channel: 'video_template',
        description: null,
        estimatedDurationSeconds: 60,
        formSchema: {
          fields: [
            {
              constraints: { maxLength: 800, minLength: 2 },
              control: 'textarea',
              defaultValue: '保留默认提示词',
              inputKey: 'prompt',
              label: '提示词',
              required: true,
              semanticKey: 'prompt.positive',
              valueType: 'string'
            }
          ],
          schemaVersion: 'workflow-form-1'
        },
        name: '商品口播',
        recommended: false,
        rowRevision: 5,
        sortNo: 0,
        status: 'disabled',
        summary: null,
        tagIds: [],
        templateId: '71'
      } as never,
      {
        configId: '91',
        enabled: true,
        executionMode: 'runninghub_ai_app',
        hasAccessPassword: false,
        inputMapping: {
          prompt: {
            fieldName: 'prompt',
            inputKey: 'prompt',
            nodeId: '12',
            remoteValueType: 'STRING',
            required: true,
            valueTransform: 'trim',
            valueType: 'string'
          }
        },
        outputPolicy: {
          allowedOutputTypes: ['jpg'],
          maxBytesPerResult: 1048576,
          maxResultCount: 1,
          primaryOutputType: 'jpg'
        },
        rowRevision: 3,
        runningHubAccountId: '31',
        templateId: '71',
        timeoutSeconds: 600,
        webAppId: '1877265245566922753',
        workflowId: null
      } as never
    );

    values.name = '只修改模板名称';
    const payloads = toWorkflowTemplatePayloads(values);

    expect((payloads.template.formSchema.fields as Array<Record<string, unknown>>)[0]).toEqual(
      expect.objectContaining({
        constraints: { maxLength: 800, minLength: 2 },
        defaultValue: '保留默认提示词',
        semanticKey: 'prompt.positive'
      })
    );
    expect(payloads.config.inputMapping.prompt).toEqual(expect.objectContaining({ valueTransform: 'trim' }));
    expect(payloads.config.outputPolicy).toEqual({});
  });

  it('把运营选中的 RunningHub 候选节点合并为可编辑参数，并避免重复映射', () => {
    const parameters = mergeRunningHubCandidates(
      [
        {
          description: '输出比例',
          fieldName: 'aspect_ratio',
          fieldType: 'LIST',
          nodeId: '37',
          nodeName: 'Flux Kontext',
          options: [
            { label: '1:1', value: '1:1' },
            { label: '16:9', value: '16:9' }
          ]
        },
        {
          description: '上传图像',
          fieldName: 'image',
          fieldType: 'IMAGE',
          nodeId: '39',
          nodeName: 'LoadImage',
          options: []
        },
        {
          description: '提示词',
          fieldName: 'prompt',
          fieldType: 'text',
          nodeId: '40',
          nodeName: 'CLIPTextEncode',
          options: []
        }
      ],
      [{ control: 'text', fieldName: 'prompt', inputKey: 'prompt', label: '提示词', nodeId: '52' }]
    );

    expect(parameters).toHaveLength(4);
    expect(parameters[1]).toEqual(
      expect.objectContaining({
        control: 'select',
        fieldName: 'aspect_ratio',
        inputKey: 'aspect_ratio',
        nodeId: '37',
        optionsText: '1:1|1:1\n16:9|16:9',
        remoteValueType: 'LIST'
      })
    );
    expect(parameters[2]).toEqual(
      expect.objectContaining({ control: 'image', fieldName: 'image', inputKey: 'image', nodeId: '39' })
    );
    expect(parameters[3]).toEqual(expect.objectContaining({ control: 'textarea', remoteValueType: 'STRING' }));

    expect(mergeRunningHubCandidates([parameters[2] as never], parameters)).toEqual(parameters);
  });

  it('运营表单不暴露内部 Slug 和原始 JSON，并使用分类下拉与明确的密码移除动作', () => {
    const source = readFileSync(
      join(process.cwd(), 'src/pages/aivideo/workflow-template/components/WorkflowTemplateEditor.tsx'),
      'utf8'
    );

    expect(source).not.toContain('label="模板 Slug"');
    expect(source).not.toContain('label="动态表单 Schema（JSON）"');
    expect(source).not.toContain('label="输入映射（JSON）"');
    expect(source).not.toContain('label="输出策略（JSON）"');
    expect(source).not.toContain('name="outputPolicyText"');
    expect(source).toContain('label="模板分类"');
    expect(source).toContain('用户输入参数');
    expect(source).toContain('读取 RunningHub 参数');
    expect(source).not.toContain('允许输出类型');
    expect(source).not.toContain('主输出类型');
    expect(source).not.toContain('输出与运行设置');
    expect(source).not.toContain('用户输入预览');
    expect(source).toContain('访问安全');
    expect(source).toContain('移除访问密码');
  });

  it('动态页面注册精确包含两套管理页', () => {
    const source = readFileSync(join(process.cwd(), 'src/pages/dynamicPage.tsx'), 'utf8');
    expect(source).toContain("'aivideo/workflow-template/index': WorkflowTemplatePage");
    expect(source).toContain("'aivideo/runninghub-account/index': RunningHubAccountPage");
  });
});
