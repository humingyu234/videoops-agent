import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { RunningHubAccountSummary } from '@/api/aivideo/runninghub-account/types';
import {
  buildWorkflowTemplateFormValues,
  WorkflowTemplateFormFieldError,
  type WorkflowTemplateFormValues
} from './templateFormModel';
import WorkflowTemplateEditor from './WorkflowTemplateEditor';

const { inspectCandidates } = vi.hoisted(() => ({
  inspectCandidates: vi.fn()
}));

vi.mock('@/api/aivideo/runninghub-account', () => ({
  inspectRunningHubParameterCandidates: inspectCandidates
}));

vi.mock('@/components/common/ImageUpload', () => ({
  default: () => <input aria-label="封面上传" />
}));

vi.mock('@/components/common/RichTextEditor', () => ({
  default: () => <textarea aria-label="详情富文本" />
}));

const accounts: RunningHubAccountSummary[] = [
  {
    accountId: '31',
    accountName: '主账号',
    enabled: true,
    hasApiKey: true,
    rowRevision: 1
  },
  {
    accountId: '32',
    accountName: '备用账号',
    enabled: true,
    hasApiKey: true,
    rowRevision: 1
  }
];

const editableValues: WorkflowTemplateFormValues = {
  ...buildWorkflowTemplateFormValues(),
  categoryId: '11',
  name: '商品口播',
  parameters: [
    {
      control: 'textarea',
      fieldName: 'prompt',
      inputKey: 'prompt',
      label: '提示词',
      nodeId: '122',
      remoteValueType: 'STRING',
      required: true
    }
  ],
  runningHubAccountId: '31',
  webAppId: '1877265245566922753'
};

interface RenderEditorOptions {
  errorMessage?: string;
  initialValues?: WorkflowTemplateFormValues;
  readonly?: boolean;
  submitting?: boolean;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(nextResolve => {
    resolve = nextResolve;
  });
  return { promise, resolve };
}

function renderEditor(options: RenderEditorOptions = {}) {
  const onClose = vi.fn();
  const onFinish = vi.fn(async () => true);
  let currentOptions = options;
  const view = () => (
    <App>
      <WorkflowTemplateEditor
        accountName="主账号"
        accountOptions={accounts}
        categoryOptions={[{ label: '电商', value: '11' }]}
        errorMessage={currentOptions.errorMessage}
        initialValues={currentOptions.initialValues || editableValues}
        readonly={currentOptions.readonly}
        submitting={currentOptions.submitting || false}
        onClose={onClose}
        onFinish={onFinish}
      />
    </App>
  );
  const result = render(view());
  return {
    ...result,
    onClose,
    onFinish,
    rerenderEditor(nextOptions: RenderEditorOptions) {
      currentOptions = { ...currentOptions, ...nextOptions };
      result.rerender(view());
    }
  };
}

describe('WorkflowTemplateEditor', () => {
  beforeEach(() => {
    inspectCandidates.mockReset();
  });

  it('默认使用合法的 AI App 模式并在校验后提交完整表单值', async () => {
    const { onFinish } = renderEditor({
      initialValues: { ...editableValues, executionMode: undefined }
    });

    expect(screen.getByRole('radio', { name: /AI App/ })).toBeChecked();
    expect(screen.getByText('频道')).toBeInTheDocument();
    expect(screen.getByLabelText('封面上传')).toBeInTheDocument();
    expect(screen.getByLabelText('详情富文本')).toBeInTheDocument();
    expect(screen.getByText('访问安全')).toBeInTheDocument();
    expect(screen.queryByText('标签编号')).not.toBeInTheDocument();
    expect(screen.queryByText('封面素材编号')).not.toBeInTheDocument();
    expect(screen.queryByText('摘要')).not.toBeInTheDocument();
    expect(screen.queryByText('输出与运行设置')).not.toBeInTheDocument();
    expect(screen.queryByTestId('user-preview')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));

    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
    expect(onFinish).toHaveBeenCalledWith(
      expect.objectContaining({
        channel: 'video_template',
        executionMode: 'runninghub_ai_app',
        name: '商品口播',
        webAppId: '1877265245566922753'
      })
    );
  });

  it('校验失败时不提交并显示字段错误', async () => {
    const { onFinish } = renderEditor({ initialValues: { ...editableValues, name: '' } });

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));

    expect(await screen.findByText('模板名称不能为空')).toBeInTheDocument();
    expect(onFinish).not.toHaveBeenCalled();
  });

  it('提交中锁定主表单、参数面板和全部变更动作', async () => {
    const editor = renderEditor();
    fireEvent.click(screen.getByRole('button', { name: '编辑参数 提示词' }));
    expect(await screen.findByRole('textbox', { name: '用户端显示名称' })).toBeEnabled();

    editor.rerenderEditor({ submitting: true });

    expect(screen.getByRole('textbox', { name: '模板名称' })).toBeDisabled();
    expect(screen.getByRole('radio', { name: /AI App/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '读取 RunningHub 参数' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '编辑参数 提示词' })).toBeDisabled();
    expect(screen.getByRole('textbox', { name: '用户端显示名称' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /保存参数/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /保存模板/ })).toBeDisabled();
  });

  it('保存进行中防止重复提交', async () => {
    const pending = deferred<boolean>();
    const { onFinish } = renderEditor();
    onFinish.mockReturnValue(pending.promise);

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
    const saveButton = screen.getByRole('button', { name: /保存模板/ });
    expect(saveButton).toBeDisabled();
    fireEvent.click(saveButton);
    await act(async () => Promise.resolve());
    expect(onFinish).toHaveBeenCalledTimes(1);
    await act(async () => {
      pending.resolve(true);
      await pending.promise;
    });
  });

  it('将父层参数转换错误定位并展示在参数区', async () => {
    const { onFinish } = renderEditor();
    onFinish.mockRejectedValueOnce(new WorkflowTemplateFormFieldError('parameters', '参数映射错误'));

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));

    expect(await screen.findByText('参数映射错误')).toBeInTheDocument();
  });

  it('账号和资源 ID 变化都会清空已配置参数', async () => {
    const first = renderEditor();
    expect(screen.getAllByText('提示词').length).toBeGreaterThan(0);

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'RunningHub 账号' }));
    fireEvent.click(await screen.findByText('备用账号'));
    await waitFor(() => expect(screen.queryAllByText('提示词')).toHaveLength(0));

    first.unmount();
    renderEditor();
    fireEvent.change(screen.getByRole('textbox', { name: 'Web App ID' }), {
      target: { value: 'new-app-id' }
    });
    await waitFor(() => expect(screen.queryAllByText('提示词')).toHaveLength(0));
  });

  it('读取候选参数后默认全选并合并到参数列表', async () => {
    inspectCandidates.mockResolvedValue({
      candidates: [
        {
          description: '口播脚本',
          fieldName: 'text',
          fieldType: 'STRING',
          nodeId: '223',
          nodeName: '文本输入'
        }
      ],
      webAppName: '商品口播应用'
    });
    renderEditor({ initialValues: { ...editableValues, parameters: [] } });

    fireEvent.click(screen.getByRole('button', { name: '读取 RunningHub 参数' }));

    const candidate = await screen.findByRole('checkbox', { name: /口播脚本/ });
    expect(candidate).toBeChecked();
    expect(inspectCandidates).toHaveBeenCalledWith({
      accountId: '31',
      executionMode: 'runninghub_ai_app',
      webAppId: '1877265245566922753',
      workflowId: undefined
    });
    fireEvent.click(screen.getByRole('button', { name: '加入参数表单' }));

    expect(await screen.findByRole('button', { name: '编辑参数 口播脚本' })).toBeInTheDocument();
  });

  it('确认候选时移除本次取消勾选的远端参数并保留其他参数', async () => {
    inspectCandidates.mockResolvedValue({
      candidates: [
        {
          description: '已有候选',
          fieldName: 'prompt',
          fieldType: 'STRING',
          nodeId: '122',
          nodeName: '提示词节点'
        },
        {
          description: '新增候选',
          fieldName: 'seed',
          fieldType: 'INTEGER',
          nodeId: '223',
          nodeName: '随机种节点'
        }
      ],
      webAppName: '商品口播应用'
    });
    renderEditor({
      initialValues: {
        ...editableValues,
        parameters: [
          editableValues.parameters![0],
          {
            control: 'text',
            fieldName: 'manual_field',
            inputKey: 'manual',
            label: '手工参数',
            nodeId: 'manual-node',
            remoteValueType: 'STRING',
            required: false
          }
        ]
      }
    });

    fireEvent.click(screen.getByRole('button', { name: '读取 RunningHub 参数' }));
    const uncheckedCandidate = await screen.findByRole('checkbox', { name: /已有候选/ });
    fireEvent.click(uncheckedCandidate);
    fireEvent.click(screen.getByRole('button', { name: '加入参数表单' }));

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '编辑参数 提示词' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: '编辑参数 手工参数' })).toBeInTheDocument();
      expect(screen.getByRole('button', { name: '编辑参数 新增候选' })).toBeInTheDocument();
    });
  });

  it('全部候选取消后仍可确认并仅保留非候选参数', async () => {
    inspectCandidates.mockResolvedValue({
      candidates: [
        {
          description: '已有候选',
          fieldName: 'prompt',
          fieldType: 'STRING',
          nodeId: '122',
          nodeName: '提示词节点'
        }
      ],
      webAppName: '商品口播应用'
    });
    renderEditor({
      initialValues: {
        ...editableValues,
        parameters: [
          editableValues.parameters![0],
          {
            control: 'text',
            fieldName: 'manual_field',
            inputKey: 'manual',
            label: '手工参数',
            nodeId: 'manual-node',
            remoteValueType: 'STRING',
            required: false
          }
        ]
      }
    });

    fireEvent.click(screen.getByRole('button', { name: '读取 RunningHub 参数' }));
    fireEvent.click(await screen.findByRole('checkbox', { name: /已有候选/ }));
    fireEvent.click(screen.getByRole('button', { name: '加入参数表单' }));

    await waitFor(() => {
      expect(screen.queryByRole('button', { name: '编辑参数 提示词' })).not.toBeInTheDocument();
      expect(screen.getByRole('button', { name: '编辑参数 手工参数' })).toBeInTheDocument();
    });
  });

  it('资源身份变化后忽略旧候选请求且旧请求不结束新请求的加载态', async () => {
    type CandidateResult = {
      candidates: Array<{
        description: string;
        fieldName: string;
        fieldType: string;
        nodeId: string;
        nodeName: string;
      }>;
      webAppName: string;
    };
    const oldRequest = deferred<CandidateResult>();
    const newRequest = deferred<CandidateResult>();
    inspectCandidates.mockReturnValueOnce(oldRequest.promise).mockReturnValueOnce(newRequest.promise);
    renderEditor({ initialValues: { ...editableValues, parameters: [] } });

    const readButton = screen.getByRole('button', { name: '读取 RunningHub 参数' });
    fireEvent.click(readButton);
    await waitFor(() => expect(inspectCandidates).toHaveBeenCalledTimes(1));
    fireEvent.change(screen.getByRole('textbox', { name: 'Web App ID' }), {
      target: { value: 'new-app-id' }
    });
    fireEvent.click(readButton);
    await waitFor(() => expect(inspectCandidates).toHaveBeenCalledTimes(2));

    await act(async () => {
      oldRequest.resolve({
        candidates: [
          {
            description: '旧候选',
            fieldName: 'old',
            fieldType: 'STRING',
            nodeId: 'old-node',
            nodeName: '旧节点'
          }
        ],
        webAppName: '旧应用'
      });
      await oldRequest.promise;
    });
    expect(screen.queryByText('旧候选')).not.toBeInTheDocument();
    expect(readButton).toHaveClass('ant-btn-loading');

    await act(async () => {
      newRequest.resolve({
        candidates: [
          {
            description: '新候选',
            fieldName: 'next',
            fieldType: 'STRING',
            nodeId: 'new-node',
            nodeName: '新节点'
          }
        ],
        webAppName: '新应用'
      });
      await newRequest.promise;
    });
    expect(await screen.findByRole('checkbox', { name: /新候选/ })).toBeChecked();
    expect(screen.queryByText('旧候选')).not.toBeInTheDocument();
  });

  it('可在编辑面板修改公开字段并保留技术映射折叠区', async () => {
    renderEditor();

    fireEvent.click(screen.getByRole('button', { name: '编辑参数 提示词' }));
    expect(await screen.findByText('技术映射')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '用户端显示名称' }), {
      target: { value: '新提示词' }
    });
    fireEvent.click(screen.getByRole('button', { name: '保存参数' }));

    expect(await screen.findByRole('button', { name: '编辑参数 新提示词' })).toBeInTheDocument();
  });

  it('访问密码不回显并须二次确认后才标记移除', async () => {
    const { onFinish } = renderEditor({
      initialValues: {
        ...editableValues,
        accessPassword: 'should-not-echo',
        hasAccessPassword: true
      }
    });

    expect(await screen.findByLabelText('新的访问密码')).toHaveValue('');
    fireEvent.click(screen.getByRole('button', { name: '移除访问密码' }));
    fireEvent.click(await screen.findByRole('button', { name: '确认移除' }));
    expect(await screen.findByText('保存后将移除当前访问密码')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
    expect(onFinish).toHaveBeenCalledWith(
      expect.objectContaining({ accessPassword: undefined, clearAccessPassword: true })
    );
  });

  it('资源身份变化时自动移除已有隐藏密码并提示重配', async () => {
    const { onFinish } = renderEditor({
      initialValues: { ...editableValues, hasAccessPassword: true }
    });

    fireEvent.change(screen.getByRole('textbox', { name: 'Web App ID' }), {
      target: { value: 'new-app-id' }
    });
    expect(await screen.findByText('保存后将移除当前访问密码')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));
    await waitFor(() => expect(onFinish).toHaveBeenCalledTimes(1));
    expect(onFinish).toHaveBeenCalledWith(
      expect.objectContaining({ accessPassword: undefined, clearAccessPassword: true })
    );
  });

  it('展示保存错误且不再渲染用户输入预览', () => {
    renderEditor({ errorMessage: '草稿已保存，配置保存失败' });

    expect(screen.getByText('草稿已保存，配置保存失败')).toBeInTheDocument();
    expect(screen.queryByTestId('user-preview')).not.toBeInTheDocument();
  });

  it('只读查看时隐藏所有变更动作', () => {
    renderEditor({ readonly: true });

    expect(screen.queryByRole('button', { name: '保存模板' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '读取 RunningHub 参数' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /编辑参数/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '添加参数' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '移除访问密码' })).not.toBeInTheDocument();
  });
});
