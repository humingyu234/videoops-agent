import {
  ArrowLeftOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  PlusOutlined,
  UpOutlined
} from '@ant-design/icons';
import {
  Alert,
  Button,
  Card,
  Checkbox,
  Collapse,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Tag,
  Typography
} from 'antd';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { RunningHubAccountSummary, RunningHubParameterCandidate } from '@/api/aivideo/runninghub-account/types';
import type { RunningHubExecutionMode } from '@/api/aivideo/workflow-template/types';
import { inspectRunningHubParameterCandidates } from '@/api/aivideo/runninghub-account';
import ImageUpload from '@/components/common/ImageUpload';
import RichTextEditor from '@/components/common/RichTextEditor';
import { useDict } from '@/hooks/useDict';
import { dictOptions } from '@/utils/dict';
import {
  buildWorkflowTemplateFormValues,
  mergeRunningHubCandidates,
  toRunningHubAccountOptions,
  workflowFormControlOptions,
  WorkflowTemplateFormFieldError,
  type WorkflowFormControl,
  type WorkflowTemplateFormValues,
  type WorkflowTemplateParameterValue
} from './templateFormModel';
import '../index.less';

interface WorkflowTemplateEditorProps {
  accountName?: string | null;
  accountOptions: RunningHubAccountSummary[];
  categoryOptions: Array<{ label: string; value: string }>;
  errorMessage?: string;
  initialValues?: WorkflowTemplateFormValues;
  readonly?: boolean;
  submitting: boolean;
  onClose: () => void;
  onFinish: (values: WorkflowTemplateFormValues) => Promise<boolean>;
}

type CandidateNotice = { message: string; type: 'error' | 'warning' };

const channelOptions = [
  { label: '视频模板', value: 'video_template' },
  { label: '工作流灵感', value: 'workflow_inspiration' }
];

const executionModes: Array<{
  description: string;
  label: string;
  recommended?: boolean;
  value: RunningHubExecutionMode;
}> = [
  {
    description: '使用已发布的 RunningHub AI App',
    label: 'AI App',
    recommended: true,
    value: 'runninghub_ai_app'
  },
  {
    description: '直接运行 RunningHub ComfyUI 工作流',
    label: 'ComfyUI 工作流',
    value: 'runninghub_workflow'
  }
];

const legalExecutionModes = new Set<RunningHubExecutionMode>(['runninghub_ai_app', 'runninghub_workflow']);

function candidateKey(candidate: RunningHubParameterCandidate) {
  return `${candidate.nodeId}\u0000${candidate.fieldName}`;
}

function parameterMappingKey(parameter: WorkflowTemplateParameterValue) {
  return `${parameter.nodeId || ''}\u0000${parameter.fieldName || ''}`;
}

function inspectionTarget(values: WorkflowTemplateFormValues) {
  const resourceId = values.executionMode === 'runninghub_workflow' ? values.workflowId : values.webAppId;
  return `${values.runningHubAccountId || ''}\u0000${values.executionMode || ''}\u0000${resourceId || ''}`;
}

function HiddenField() {
  return null;
}

function sanitizeInitialValues(initialValues?: WorkflowTemplateFormValues): WorkflowTemplateFormValues {
  const defaults = buildWorkflowTemplateFormValues();
  const requestedMode = initialValues?.executionMode;
  return {
    ...defaults,
    ...initialValues,
    accessPassword: undefined,
    executionMode: requestedMode && legalExecutionModes.has(requestedMode) ? requestedMode : 'runninghub_ai_app',
    parameters: initialValues?.parameters || []
  };
}

function parameterControlLabel(control?: WorkflowFormControl) {
  return workflowFormControlOptions.find(option => option.value === control)?.label || '未选择控件';
}

export default function WorkflowTemplateEditor({
  accountName,
  accountOptions,
  categoryOptions,
  errorMessage,
  initialValues,
  readonly = false,
  submitting,
  onClose,
  onFinish
}: WorkflowTemplateEditorProps) {
  const [form] = Form.useForm<WorkflowTemplateFormValues>();
  const [parameterForm] = Form.useForm<WorkflowTemplateParameterValue>();
  const normalizedInitialValues = useMemo(() => sanitizeInitialValues(initialValues), [initialValues]);
  const [candidateModalOpen, setCandidateModalOpen] = useState(false);
  const [candidateResourceName, setCandidateResourceName] = useState<string>();
  const [candidateNotice, setCandidateNotice] = useState<CandidateNotice>();
  const [candidates, setCandidates] = useState<RunningHubParameterCandidate[]>([]);
  const [selectedCandidateKeys, setSelectedCandidateKeys] = useState<string[]>([]);
  const [inspecting, setInspecting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [parameterDrawerOpen, setParameterDrawerOpen] = useState(false);
  const [editingParameterIndex, setEditingParameterIndex] = useState<number | null>(null);
  const [localErrorMessage, setLocalErrorMessage] = useState<string>();
  const inspectionSequenceRef = useRef(0);
  const inspectionTargetRef = useRef<string | undefined>(undefined);
  const savingRef = useRef(false);
  const executionMode =
    Form.useWatch('executionMode', form) || normalizedInitialValues.executionMode || 'runninghub_ai_app';
  const watchedParameters = Form.useWatch('parameters', { form, preserve: true });
  const parameters: WorkflowTemplateParameterValue[] = watchedParameters || normalizedInitialValues.parameters || [];
  const clearAccessPassword = Form.useWatch('clearAccessPassword', form);
  const dicts = useDict('aivideo_runninghub_instance_type');
  const instanceTypeOptions = dictOptions(dicts.aivideo_runninghub_instance_type);
  const editing = Boolean(normalizedInitialValues.templateId);
  const hasAccessPassword = Boolean(normalizedInitialValues.hasAccessPassword);
  const locked = readonly || submitting || saving;
  const runningHubAccountOptions =
    readonly && normalizedInitialValues.runningHubAccountId
      ? [
          {
            label: accountName || normalizedInitialValues.runningHubAccountId,
            value: normalizedInitialValues.runningHubAccountId
          }
        ]
      : toRunningHubAccountOptions(accountOptions);

  const resetCandidateState = useCallback(() => {
    setCandidateModalOpen(false);
    setCandidateResourceName(undefined);
    setCandidateNotice(undefined);
    setCandidates([]);
    setSelectedCandidateKeys([]);
  }, []);

  const invalidateInspection = useCallback(() => {
    inspectionSequenceRef.current += 1;
    inspectionTargetRef.current = undefined;
    setInspecting(false);
  }, []);

  const clearExecutionParameters = useCallback(() => {
    invalidateInspection();
    form.setFieldsValue({
      accessPassword: undefined,
      clearAccessPassword: hasAccessPassword ? true : form.getFieldValue('clearAccessPassword'),
      parameters: []
    });
    resetCandidateState();
  }, [form, hasAccessPassword, invalidateInspection, resetCandidateState]);

  useEffect(() => {
    form.resetFields();
    form.setFieldsValue(normalizedInitialValues);
    setLocalErrorMessage(undefined);
    setParameterDrawerOpen(false);
    setEditingParameterIndex(null);
    parameterForm.resetFields();
    invalidateInspection();
    resetCandidateState();
  }, [form, invalidateInspection, normalizedInitialValues, parameterForm, resetCandidateState]);

  useEffect(
    () => () => {
      inspectionSequenceRef.current += 1;
      inspectionTargetRef.current = undefined;
      form.setFieldValue('accessPassword', undefined);
      parameterForm.resetFields();
    },
    [form, parameterForm]
  );

  const locateField = (name: Parameters<typeof form.scrollToField>[0]) => {
    try {
      form.scrollToField(name, { block: 'center' });
    } catch {
      // happy-dom and older WebViews may not expose scrolling; the inline error remains visible.
    }
  };

  const close = () => {
    if (submitting || savingRef.current) return;
    invalidateInspection();
    form.setFieldValue('accessPassword', undefined);
    resetCandidateState();
    parameterForm.resetFields();
    onClose();
  };

  const save = async () => {
    if (submitting || savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setLocalErrorMessage(undefined);
    try {
      await form.validateFields();
      const values = form.getFieldsValue(true);
      const saved = await onFinish(values);
      if (saved) form.setFieldValue('accessPassword', undefined);
    } catch (error) {
      if (error instanceof WorkflowTemplateFormFieldError) {
        form.setFields([{ errors: [error.message], name: error.field }]);
        locateField(error.field);
        return;
      }
      if (error && typeof error === 'object' && 'errorFields' in error) {
        const first = (error as { errorFields?: Array<{ name: Array<string | number> }> }).errorFields?.[0];
        if (first) locateField(first.name);
        return;
      }
      setLocalErrorMessage(error instanceof Error ? error.message : '保存失败，请保留当前输入后重试');
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  const changeExecutionMode = (mode: RunningHubExecutionMode) => {
    if (locked) return;
    clearExecutionParameters();
    form.setFieldsValue({
      executionMode: mode,
      instanceType: form.getFieldValue('instanceType') || 'default',
      webAppId: mode === 'runninghub_ai_app' ? form.getFieldValue('webAppId') : undefined,
      workflowId: mode === 'runninghub_workflow' ? form.getFieldValue('workflowId') : undefined
    });
  };

  const inspectParameters = async () => {
    if (locked) return;
    const requestSequence = ++inspectionSequenceRef.current;
    let targetSnapshot: string | undefined;
    inspectionTargetRef.current = undefined;
    setInspecting(true);
    setCandidateNotice(undefined);
    try {
      const resourceField = executionMode === 'runninghub_ai_app' ? 'webAppId' : 'workflowId';
      const values = await form.validateFields(['runningHubAccountId', 'executionMode', resourceField]);
      targetSnapshot = inspectionTarget(values);
      inspectionTargetRef.current = targetSnapshot;
      const result = await inspectRunningHubParameterCandidates({
        accountId: values.runningHubAccountId!,
        executionMode: values.executionMode!,
        webAppId: values.executionMode === 'runninghub_ai_app' ? values.webAppId : undefined,
        workflowId: values.executionMode === 'runninghub_workflow' ? values.workflowId : undefined
      });
      const currentTarget = inspectionTarget(
        form.getFieldsValue(['runningHubAccountId', 'executionMode', 'webAppId', 'workflowId'])
      );
      if (
        requestSequence !== inspectionSequenceRef.current ||
        targetSnapshot !== inspectionTargetRef.current ||
        targetSnapshot !== currentTarget
      ) {
        return;
      }
      if (!result.candidates.length) {
        setCandidateNotice({ message: 'RunningHub 未返回可配置参数', type: 'warning' });
        return;
      }
      setCandidateResourceName(result.webAppName || undefined);
      setCandidates(result.candidates);
      setSelectedCandidateKeys(result.candidates.map(candidateKey));
      setCandidateModalOpen(true);
    } catch (error) {
      if (
        requestSequence !== inspectionSequenceRef.current ||
        targetSnapshot !== inspectionTargetRef.current
      ) {
        return;
      }
      if (error && typeof error === 'object' && 'errorFields' in error) {
        const first = (error as { errorFields?: Array<{ name: Array<string | number> }> }).errorFields?.[0];
        if (first) locateField(first.name);
        return;
      }
      setCandidateNotice({
        message: error instanceof Error ? error.message : '读取 RunningHub 参数失败',
        type: 'error'
      });
    } finally {
      if (
        requestSequence === inspectionSequenceRef.current &&
        targetSnapshot === inspectionTargetRef.current
      ) {
        setInspecting(false);
      }
    }
  };

  const applyCandidates = () => {
    if (locked) return;
    const selected = candidates.filter(candidate => selectedCandidateKeys.includes(candidateKey(candidate)));
    const candidateKeys = new Set(candidates.map(candidateKey));
    const selectedKeys = new Set(selected.map(candidateKey));
    const retained = parameters.filter(parameter => {
      const key = parameterMappingKey(parameter);
      return !candidateKeys.has(key) || selectedKeys.has(key);
    });
    const merged = mergeRunningHubCandidates(selected, retained);
    form.setFieldValue('parameters', merged);
    setCandidateModalOpen(false);
    setCandidateNotice(undefined);
  };

  const openParameterEditor = (index: number | null) => {
    if (locked) return;
    setEditingParameterIndex(index);
    parameterForm.resetFields();
    parameterForm.setFieldsValue(
      index === null ? { control: 'text', remoteValueType: 'STRING', required: true } : parameters[index]
    );
    setParameterDrawerOpen(true);
  };

  const saveParameter = async () => {
    if (locked) return;
    try {
      await parameterForm.validateFields();
      const value = parameterForm.getFieldsValue(true);
      const next = [...parameters];
      if (editingParameterIndex === null) next.push(value);
      else next[editingParameterIndex] = { ...next[editingParameterIndex], ...value };
      form.setFieldValue('parameters', next);
      setParameterDrawerOpen(false);
      setEditingParameterIndex(null);
      parameterForm.resetFields();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      throw error;
    }
  };

  const removeParameter = (index: number) => {
    if (locked) return;
    form.setFieldValue(
      'parameters',
      parameters.filter((_, parameterIndex) => parameterIndex !== index)
    );
  };

  const moveParameter = (index: number, offset: -1 | 1) => {
    if (locked) return;
    const target = index + offset;
    if (target < 0 || target >= parameters.length) return;
    const next = [...parameters];
    [next[index], next[target]] = [next[target], next[index]];
    form.setFieldValue('parameters', next);
  };

  const title = readonly
    ? `查看工作流模板“${normalizedInitialValues.name || ''}”`
    : editing
      ? `修改工作流模板“${normalizedInitialValues.name || ''}”`
      : '新增工作流模板';

  return (
    <div className="workflow-template-editor">
      <header className="workflow-template-editor__header">
        <Space size="middle">
          <Button aria-label="返回" disabled={locked} icon={<ArrowLeftOutlined />} type="text" onClick={close}>
            返回
          </Button>
          <Typography.Title level={3}>{title}</Typography.Title>
        </Space>
        {!readonly && (
          <Space>
            <Button disabled={locked} onClick={close}>
              取消
            </Button>
            <Button disabled={locked} loading={submitting || saving} type="primary" onClick={() => void save()}>
              保存模板
            </Button>
          </Space>
        )}
      </header>

      {(errorMessage || localErrorMessage) && (
        <Alert
          className="workflow-template-editor__error"
          description="请保留当前输入并原地重试执行配置。"
          title={errorMessage || localErrorMessage}
          showIcon
          type="error"
        />
      )}

      <Form
        className="workflow-template-editor__content"
        disabled={locked}
        form={form}
        initialValues={normalizedInitialValues}
        layout="vertical"
      >
        <Form.Item hidden name="templateId">
          <Input />
        </Form.Item>
        <Form.Item hidden name="expectedTemplateRevision">
          <Input />
        </Form.Item>
        <Form.Item hidden name="expectedConfigRevision">
          <Input />
        </Form.Item>
        <Form.Item hidden name="formSchemaText">
          <Input />
        </Form.Item>
        <Form.Item hidden name="inputMappingText">
          <Input />
        </Form.Item>
        <Form.Item hidden name="tagIdsText">
          <Input />
        </Form.Item>
        <Form.Item hidden name="summary">
          <Input />
        </Form.Item>
        <Form.Item hidden name="hasAccessPassword" valuePropName="checked">
          <Checkbox />
        </Form.Item>
        <Form.Item hidden name="clearAccessPassword" valuePropName="checked">
          <Checkbox />
        </Form.Item>
        <Form.Item hidden name="parameters">
          <HiddenField />
        </Form.Item>

        <main className="workflow-template-editor__form-column">
          <Card className="workflow-template-editor__section" size="small" title="模板基础资料">
            <div className="workflow-template-editor__grid workflow-template-editor__grid--three">
              <Form.Item label="频道" name="channel" rules={[{ required: true, message: '请选择模板频道' }]}>
                <Select options={channelOptions} />
              </Form.Item>
              <Form.Item label="模板名称" name="name" rules={[{ required: true, message: '模板名称不能为空' }]}>
                <Input />
              </Form.Item>
              <Form.Item label="模板分类" name="categoryId" rules={[{ required: true, message: '请选择模板分类' }]}>
                <Select optionFilterProp="label" options={categoryOptions} showSearch />
              </Form.Item>
            </div>
            <Form.Item label="封面" name="coverAssetId">
              <ImageUpload disabled={locked} limit={1} />
            </Form.Item>
            <Form.Item label="详情" name="description">
              <RichTextEditor minHeight={192} readOnly={locked} />
            </Form.Item>
            <div className="workflow-template-editor__grid">
              <Form.Item label="排序号" name="sortNo" rules={[{ required: true, message: '排序号不能为空' }]}>
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item label="预计耗时（秒）" name="estimatedDurationSeconds">
                <InputNumber min={1} style={{ width: '100%' }} />
              </Form.Item>
            </div>
            <Form.Item name="recommended" valuePropName="checked">
              <Checkbox>设为推荐模板</Checkbox>
            </Form.Item>
          </Card>

          <Card className="workflow-template-editor__section" size="small" title="RunningHub 应用">
            <Form.Item label="执行模式" name="executionMode" rules={[{ required: true, message: '请选择执行模式' }]}>
              <Radio.Group
                className="workflow-template-editor__mode-cards"
                onChange={event => changeExecutionMode(event.target.value as RunningHubExecutionMode)}
              >
                {executionModes.map(mode => (
                  <Radio.Button key={mode.value} value={mode.value}>
                    <span className="workflow-template-editor__mode-title">
                      {mode.label}
                      {mode.recommended && <Tag color="blue">推荐</Tag>}
                    </span>
                    <Typography.Text type="secondary">{mode.description}</Typography.Text>
                  </Radio.Button>
                ))}
              </Radio.Group>
            </Form.Item>
            <div className="workflow-template-editor__grid">
              <Form.Item
                label="RunningHub 账号"
                name="runningHubAccountId"
                rules={[{ required: true, message: '请选择 RunningHub 账号' }]}
              >
                <Select options={runningHubAccountOptions} onChange={clearExecutionParameters} />
              </Form.Item>
              {executionMode === 'runninghub_ai_app' ? (
                <Form.Item
                  label="Web App ID"
                  name="webAppId"
                  rules={[{ required: true, message: 'Web App ID 不能为空' }]}
                >
                  <Input onChange={clearExecutionParameters} />
                </Form.Item>
              ) : (
                <Form.Item
                  label="Workflow ID"
                  name="workflowId"
                  rules={[{ required: true, message: 'Workflow ID 不能为空' }]}
                >
                  <Input onChange={clearExecutionParameters} />
                </Form.Item>
              )}
            </div>
            <Form.Item label="显存规格" name="instanceType">
              <Select options={instanceTypeOptions} placeholder="请选择显存规格" />
            </Form.Item>
            {!readonly && (
              <Button disabled={locked} loading={inspecting} onClick={() => void inspectParameters()}>
                读取 RunningHub 参数
              </Button>
            )}
            {candidateNotice && (
              <Alert
                className="workflow-template-editor__notice"
                title={candidateNotice.message}
                showIcon
                type={candidateNotice.type}
              />
            )}
          </Card>

          <Card
            className="workflow-template-editor__section"
            extra={
              !readonly && (
                <Button disabled={locked} icon={<PlusOutlined />} type="primary" onClick={() => openParameterEditor(null)}>
                  添加参数
                </Button>
              )
            }
            size="small"
            title="用户输入参数"
          >
            <Alert title="公开字段用于用户表单；节点 ID、字段名等技术映射只在编辑面板中维护。" showIcon type="info" />
            <Form.Item noStyle shouldUpdate>
              {() => {
                const errors = form.getFieldError('parameters');
                return errors.length ? <Form.ErrorList errors={errors} /> : null;
              }}
            </Form.Item>
            {parameters.length ? (
              <div className="workflow-template-editor__parameter-list">
                {parameters.map((parameter, index) => (
                  <div
                    className="workflow-template-editor__parameter-row"
                    key={`${parameter.inputKey || 'parameter'}-${index}`}
                  >
                    <div className="workflow-template-editor__parameter-summary">
                      <Space size={6} wrap>
                        <Typography.Text strong>{parameter.label || `参数 ${index + 1}`}</Typography.Text>
                        <Tag>{parameterControlLabel(parameter.control)}</Tag>
                        {parameter.required && <Tag color="blue">必填</Tag>}
                      </Space>
                      {parameter.description && (
                        <Typography.Text type="secondary">{parameter.description}</Typography.Text>
                      )}
                    </div>
                    {!readonly && (
                      <Space size={2}>
                        <Button
                          aria-label={`上移参数 ${parameter.label || index + 1}`}
                          disabled={locked || index === 0}
                          icon={<UpOutlined />}
                          size="small"
                          type="text"
                          onClick={() => moveParameter(index, -1)}
                        />
                        <Button
                          aria-label={`下移参数 ${parameter.label || index + 1}`}
                          disabled={locked || index === parameters.length - 1}
                          icon={<DownOutlined />}
                          size="small"
                          type="text"
                          onClick={() => moveParameter(index, 1)}
                        />
                        <Button
                          aria-label={`编辑参数 ${parameter.label || index + 1}`}
                          disabled={locked}
                          icon={<EditOutlined />}
                          size="small"
                          type="text"
                          onClick={() => openParameterEditor(index)}
                        />
                        <Popconfirm
                          cancelText="取消"
                          okText="确认删除"
                          title={`删除参数“${parameter.label || index + 1}”？`}
                          onConfirm={() => removeParameter(index)}
                        >
                          <Button
                            aria-label={`删除参数 ${parameter.label || index + 1}`}
                            danger
                            disabled={locked}
                            icon={<DeleteOutlined />}
                            size="small"
                            type="text"
                          />
                        </Popconfirm>
                      </Space>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <Empty description="暂未配置用户输入参数" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>

          <Card className="workflow-template-editor__section" size="small" title="访问安全">
            {hasAccessPassword && (
              <Alert
                title={readonly ? '访问密码已配置，明文不会展示。' : '访问密码不会回填；留空保持原值。'}
                showIcon
                type="info"
              />
            )}
            {!readonly && (
              <>
                <Form.Item label="新的访问密码" name="accessPassword">
                  <Input.Password
                    autoComplete="new-password"
                    disabled={locked || Boolean(clearAccessPassword)}
                    placeholder="可选；留空保持原值"
                  />
                </Form.Item>
                {hasAccessPassword &&
                  (clearAccessPassword ? (
                    <div className="workflow-template-editor__password-warning">
                      <Alert title="保存后将移除当前访问密码" showIcon type="warning" />
                      <Button disabled={locked} type="link" onClick={() => form.setFieldValue('clearAccessPassword', false)}>
                        撤销移除
                      </Button>
                    </div>
                  ) : (
                    <Popconfirm
                      cancelText="取消"
                      description="这里只标记为待移除，提交保存后才会生效。"
                      okText="确认移除"
                      title="移除当前访问密码？"
                      onConfirm={() => {
                        form.setFieldValue('accessPassword', undefined);
                        form.setFieldValue('clearAccessPassword', true);
                      }}
                    >
                      <Button danger disabled={locked}>移除访问密码</Button>
                    </Popconfirm>
                  ))}
              </>
            )}
          </Card>
        </main>

      </Form>

      <Drawer
        closable={!locked}
        destroyOnHidden
        forceRender
        footer={
          <Space>
            <Button disabled={locked} onClick={() => !locked && setParameterDrawerOpen(false)}>取消</Button>
            <Button disabled={locked} loading={submitting || saving} type="primary" onClick={() => void saveParameter()}>
              保存参数
            </Button>
          </Space>
        }
        open={parameterDrawerOpen}
        maskClosable={!locked}
        title={editingParameterIndex === null ? '新增参数' : '编辑参数'}
        size={560}
        onClose={() => !locked && setParameterDrawerOpen(false)}
      >
        <Form disabled={locked} form={parameterForm} layout="vertical">
          <Form.Item hidden name="preservedFormFieldText">
            <Input />
          </Form.Item>
          <Form.Item hidden name="preservedInputMappingText">
            <Input />
          </Form.Item>
          <div className="workflow-template-editor__grid">
            <Form.Item label="参数标识" name="inputKey" rules={[{ required: true, message: '参数标识不能为空' }]}>
              <Input placeholder="例如 prompt" />
            </Form.Item>
            <Form.Item label="用户端显示名称" name="label" rules={[{ required: true, message: '显示名称不能为空' }]}>
              <Input placeholder="例如 提示词" />
            </Form.Item>
          </div>
          <Form.Item label="用户端控件" name="control" rules={[{ required: true, message: '请选择控件类型' }]}>
            <Select options={workflowFormControlOptions} />
          </Form.Item>
          <div className="workflow-template-editor__grid">
            <Form.Item label="参数说明" name="description">
              <Input />
            </Form.Item>
            <Form.Item label="输入提示" name="placeholder">
              <Input />
            </Form.Item>
          </div>
          <Form.Item label="选择项" name="optionsText">
            <Input.TextArea autoSize={{ minRows: 2, maxRows: 6 }} placeholder="每行一个，格式：值|显示名称" />
          </Form.Item>
          <Form.Item name="required" valuePropName="checked">
            <Checkbox>用户必须填写</Checkbox>
          </Form.Item>
          <Collapse
            items={[
              {
                children: (
                  <>
                    <Form.Item
                      label="RunningHub 节点 ID"
                      name="nodeId"
                      rules={[{ required: true, message: '节点 ID 不能为空' }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item
                      label="RunningHub 字段名"
                      name="fieldName"
                      rules={[{ required: true, message: '字段名不能为空' }]}
                    >
                      <Input />
                    </Form.Item>
                    <Form.Item label="远端参数类型" name="remoteValueType">
                      <Input placeholder="例如 STRING、IMAGE、LIST" />
                    </Form.Item>
                  </>
                ),
                forceRender: true,
                key: 'mapping',
                label: '技术映射'
              }
            ]}
          />
        </Form>
      </Drawer>

      <Modal
        cancelButtonProps={{ disabled: locked }}
        closable={!locked}
        destroyOnHidden
        cancelText="取消"
        okText="加入参数表单"
        mask={{ closable: !locked }}
        okButtonProps={{ disabled: locked }}
        open={candidateModalOpen}
        title={candidateResourceName ? `选择“${candidateResourceName}”开放给用户的参数` : '选择开放给用户的参数'}
        onCancel={() => !locked && setCandidateModalOpen(false)}
        onOk={applyCandidates}
      >
        <Alert title="默认全选；取消勾选的字段继续使用 RunningHub 中的默认值。" showIcon type="warning" />
        <div className="workflow-template-editor__candidate-list">
          {candidates.map(candidate => {
            const key = candidateKey(candidate);
            return (
              <Checkbox
                checked={selectedCandidateKeys.includes(key)}
                disabled={locked}
                key={key}
                onChange={event =>
                  setSelectedCandidateKeys(current =>
                    event.target.checked ? [...current, key] : current.filter(item => item !== key)
                  )
                }
              >
                <Typography.Text strong>{candidate.description || candidate.fieldName}</Typography.Text>
                <Typography.Text type="secondary">
                  {' '}
                  · {candidate.nodeName} / {candidate.fieldName}（节点 {candidate.nodeId}，{candidate.fieldType}）
                </Typography.Text>
              </Checkbox>
            );
          })}
        </div>
      </Modal>
    </div>
  );
}
