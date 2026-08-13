import type { RunningHubAccountSummary, RunningHubParameterCandidate } from '@/api/aivideo/runninghub-account/types';
import type {
  JsonObject,
  RunningHubExecutionMode,
  WorkflowExecutionConfig,
  WorkflowExecutionConfigSave,
  WorkflowTemplateChannel,
  WorkflowTemplateDetail,
  WorkflowTemplateSave
} from '@/api/aivideo/workflow-template/types';

export type WorkflowTemplateJsonField = 'formSchemaText' | 'inputMappingText';

export class WorkflowTemplateFormFieldError extends Error {
  constructor(
    public readonly field: keyof WorkflowTemplateFormValues,
    message: string
  ) {
    super(message);
    this.name = 'WorkflowTemplateFormFieldError';
  }
}

export class JsonFormFieldError extends WorkflowTemplateFormFieldError {
  constructor(field: WorkflowTemplateJsonField, message: string) {
    super(field, message);
    this.name = 'JsonFormFieldError';
  }
}

export const runningHubExecutionModeOptions: Array<{ label: string; value: RunningHubExecutionMode }> = [
  { label: 'RunningHub AI App（默认）', value: 'runninghub_ai_app' },
  { label: 'RunningHub Workflow', value: 'runninghub_workflow' }
];

export type WorkflowFormControl =
  | 'text'
  | 'textarea'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'select'
  | 'multi_select'
  | 'image'
  | 'audio'
  | 'video'
  | 'file';

export interface WorkflowTemplateParameterValue {
  inputKey?: string;
  label?: string;
  description?: string;
  placeholder?: string;
  control?: WorkflowFormControl;
  required?: boolean;
  nodeId?: string;
  fieldName?: string;
  remoteValueType?: string;
  optionsText?: string;
  preservedFormFieldText?: string;
  preservedInputMappingText?: string;
}

export const workflowFormControlOptions: Array<{ label: string; value: WorkflowFormControl }> = [
  { label: '单行文本', value: 'text' },
  { label: '多行文本', value: 'textarea' },
  { label: '整数', value: 'integer' },
  { label: '小数', value: 'decimal' },
  { label: '开关', value: 'boolean' },
  { label: '单选', value: 'select' },
  { label: '多选', value: 'multi_select' },
  { label: '图片', value: 'image' },
  { label: '音频', value: 'audio' },
  { label: '视频', value: 'video' },
  { label: '文件', value: 'file' }
];

const VALUE_TYPE_BY_CONTROL: Record<WorkflowFormControl, string> = {
  audio: 'asset_array',
  boolean: 'boolean',
  decimal: 'decimal',
  file: 'asset_array',
  image: 'asset_array',
  integer: 'integer',
  multi_select: 'string_array',
  select: 'string',
  text: 'string',
  textarea: 'string',
  video: 'asset_array'
};

const REMOTE_TYPE_BY_CONTROL: Record<WorkflowFormControl, string> = {
  audio: 'AUDIO',
  boolean: 'BOOLEAN',
  decimal: 'NUMBER',
  file: 'FILE',
  image: 'IMAGE',
  integer: 'INTEGER',
  multi_select: 'LIST',
  select: 'LIST',
  text: 'STRING',
  textarea: 'STRING',
  video: 'VIDEO'
};

export interface WorkflowTemplateFormValues {
  templateId?: string;
  expectedTemplateRevision?: number;
  expectedConfigRevision?: number;
  channel?: WorkflowTemplateChannel;
  name?: string;
  summary?: string;
  description?: string;
  coverAssetId?: string;
  categoryId?: string;
  tagIdsText?: string;
  formSchemaText?: string;
  recommended?: boolean;
  sortNo?: number;
  estimatedDurationSeconds?: number;
  runningHubAccountId?: string;
  executionMode?: RunningHubExecutionMode;
  workflowId?: string;
  webAppId?: string;
  instanceType?: 'default' | 'plus';
  accessPassword?: string;
  clearAccessPassword?: boolean;
  inputMappingText?: string;
  parameters?: WorkflowTemplateParameterValue[];
  timeoutSeconds?: number;
  executionEnabled?: boolean;
  hasAccessPassword?: boolean;
}

export interface WorkflowTemplatePayloads {
  template: WorkflowTemplateSave;
  config: WorkflowExecutionConfigSave;
}

function prettyJson(value: JsonObject) {
  return JSON.stringify(value, null, 2);
}

export function buildWorkflowTemplateFormValues(
  detail?: WorkflowTemplateDetail,
  config?: WorkflowExecutionConfig
): WorkflowTemplateFormValues {
  if (!detail) {
    return {
      accessPassword: undefined,
      channel: 'video_template',
      clearAccessPassword: false,
      executionEnabled: true,
      executionMode: 'runninghub_ai_app',
      instanceType: 'default',
      formSchemaText: prettyJson({ fields: [], schemaVersion: 'workflow-form-1' }),
      inputMappingText: prettyJson({}),
      parameters: [],
      recommended: false,
      sortNo: 0,
      tagIdsText: '',
      timeoutSeconds: 21600
    };
  }

  return {
    accessPassword: undefined,
    categoryId: detail.categoryId,
    channel: detail.channel,
    clearAccessPassword: false,
    coverAssetId: detail.coverAssetId || undefined,
    description: detail.description || undefined,
    estimatedDurationSeconds: detail.estimatedDurationSeconds || undefined,
    executionEnabled: config?.enabled ?? true,
    executionMode: config?.executionMode || 'runninghub_ai_app',
    instanceType: config?.instanceType || 'default',
    expectedConfigRevision: config?.rowRevision,
    expectedTemplateRevision: detail.rowRevision,
    formSchemaText: prettyJson(detail.formSchema),
    hasAccessPassword: config?.hasAccessPassword ?? false,
    inputMappingText: prettyJson(config?.inputMapping || {}),
    name: detail.name,
    parameters: buildParameterValues(detail.formSchema, config?.inputMapping || {}),
    recommended: detail.recommended,
    runningHubAccountId: config?.runningHubAccountId,
    sortNo: detail.sortNo,
    summary: detail.summary || undefined,
    tagIdsText: detail.tagIds.join(','),
    templateId: detail.templateId,
    timeoutSeconds: config?.timeoutSeconds || 21600,
    webAppId: config?.webAppId || undefined,
    workflowId: config?.workflowId || undefined
  };
}

function asObject(value: unknown): JsonObject | undefined {
  return value && !Array.isArray(value) && typeof value === 'object' ? (value as JsonObject) : undefined;
}

function optionalString(value: unknown) {
  return typeof value === 'string' && value.trim() ? value.trim() : undefined;
}

function isWorkflowFormControl(value: unknown): value is WorkflowFormControl {
  return typeof value === 'string' && Object.hasOwn(VALUE_TYPE_BY_CONTROL, value);
}

function mappingForInput(inputMapping: JsonObject, inputKey: string) {
  const direct = asObject(inputMapping[inputKey]);
  if (direct) return direct;
  const mappings = inputMapping.mappings;
  if (Array.isArray(mappings)) {
    return mappings.map(asObject).find(mapping => mapping?.inputKey === inputKey);
  }
  const legacyFieldName = optionalString(inputMapping[inputKey]);
  return legacyFieldName ? ({ fieldName: legacyFieldName } satisfies JsonObject) : undefined;
}

function formatOptions(value: unknown) {
  if (!Array.isArray(value)) return undefined;
  const lines = value
    .map(asObject)
    .filter(Boolean)
    .map(option => {
      const optionValue = optionalString(option?.value);
      const label = optionalString(option?.label);
      return optionValue && label ? `${optionValue}|${label}` : undefined;
    })
    .filter(Boolean);
  return lines.length ? lines.join('\n') : undefined;
}

function preserveObject(value: JsonObject | undefined) {
  return value ? JSON.stringify(value) : undefined;
}

function restorePreservedObject(value: string | undefined): JsonObject {
  if (!value) return {};
  try {
    return asObject(JSON.parse(value)) || {};
  } catch {
    return {};
  }
}

function buildParameterValues(formSchema: JsonObject, inputMapping: JsonObject): WorkflowTemplateParameterValue[] {
  const fields = formSchema.fields;
  if (!Array.isArray(fields)) return [];
  return fields
    .map(asObject)
    .filter(Boolean)
    .map(field => {
      const inputKey = optionalString(field?.inputKey);
      const control = isWorkflowFormControl(field?.control) ? field.control : 'text';
      const mapping = inputKey ? mappingForInput(inputMapping, inputKey) : undefined;
      return {
        control,
        description: optionalString(field?.description),
        fieldName: optionalString(mapping?.fieldName),
        inputKey,
        label: optionalString(field?.label),
        nodeId: optionalString(mapping?.nodeId),
        optionsText: formatOptions(field?.options),
        placeholder: optionalString(field?.placeholder),
        preservedFormFieldText: preserveObject(field),
        preservedInputMappingText: preserveObject(mapping),
        remoteValueType: optionalString(mapping?.remoteValueType) || REMOTE_TYPE_BY_CONTROL[control],
        required: field?.required === true
      };
    });
}

function controlForCandidate(candidate: RunningHubParameterCandidate): WorkflowFormControl {
  if (candidate.options?.length) return 'select';
  const fieldType = candidate.fieldType?.trim().toUpperCase();
  if (fieldType === 'IMAGE') return 'image';
  if (fieldType === 'AUDIO') return 'audio';
  if (fieldType === 'VIDEO') return 'video';
  if (fieldType === 'FILE') return 'file';
  if (fieldType === 'BOOLEAN' || fieldType === 'BOOL') return 'boolean';
  if (fieldType === 'INTEGER' || fieldType === 'INT') return 'integer';
  if (['NUMBER', 'FLOAT', 'DOUBLE', 'DECIMAL'].includes(fieldType)) return 'decimal';
  if (fieldType === 'LIST' || fieldType === 'SELECT' || fieldType === 'COMBO') return 'select';
  return /prompt|text|description|negative/i.test(candidate.fieldName) ? 'textarea' : 'text';
}

function remoteValueTypeForCandidate(candidate: RunningHubParameterCandidate, control: WorkflowFormControl) {
  const fieldType = candidate.fieldType?.trim().toUpperCase();
  if (!fieldType) return REMOTE_TYPE_BY_CONTROL[control];
  if (['STRING', 'TEXT', 'TEXTAREA', 'MULTILINE'].includes(fieldType)) return 'STRING';
  if (['LIST', 'SELECT', 'COMBO', 'MULTI_SELECT'].includes(fieldType)) return 'LIST';
  if (['BOOLEAN', 'BOOL'].includes(fieldType)) return 'BOOLEAN';
  if (['INTEGER', 'INT', 'SEED'].includes(fieldType)) return 'INTEGER';
  if (['NUMBER', 'FLOAT', 'DOUBLE', 'DECIMAL'].includes(fieldType)) return 'NUMBER';
  if (['IMAGE', 'AUDIO', 'VIDEO', 'FILE'].includes(fieldType)) return fieldType;
  return REMOTE_TYPE_BY_CONTROL[control];
}

function nextInputKey(fieldName: string, nodeId: string, usedInputKeys: Set<string>) {
  let candidate = fieldName
    .trim()
    .replace(/[^A-Za-z0-9._-]+/g, '_')
    .replace(/^[^A-Za-z]+/, '');
  if (!candidate) candidate = `input_${nodeId.replace(/[^A-Za-z0-9_-]+/g, '_') || 'field'}`;
  if (!usedInputKeys.has(candidate)) {
    usedInputKeys.add(candidate);
    return candidate;
  }
  const suffix = nodeId.replace(/[^A-Za-z0-9_-]+/g, '_') || 'node';
  let index = 1;
  let unique = `${candidate}_${suffix}`;
  while (usedInputKeys.has(unique)) unique = `${candidate}_${suffix}_${index++}`;
  usedInputKeys.add(unique);
  return unique;
}

export function mergeRunningHubCandidates(
  candidates: RunningHubParameterCandidate[],
  current: WorkflowTemplateParameterValue[] = []
) {
  const result = [...current];
  const mappedFields = new Set(current.map(item => `${item.nodeId || ''}\u0000${item.fieldName || ''}`));
  const usedInputKeys = new Set(current.map(item => item.inputKey).filter((value): value is string => Boolean(value)));

  candidates.forEach(candidate => {
    const mappingKey = `${candidate.nodeId || ''}\u0000${candidate.fieldName || ''}`;
    if (mappedFields.has(mappingKey)) return;
    mappedFields.add(mappingKey);
    const control = controlForCandidate(candidate);
    result.push({
      control,
      description: optionalString(candidate.description),
      fieldName: candidate.fieldName,
      inputKey: nextInputKey(candidate.fieldName, candidate.nodeId, usedInputKeys),
      label: optionalString(candidate.description) || candidate.fieldName,
      nodeId: candidate.nodeId,
      optionsText: candidate.options?.length
        ? candidate.options.map(option => `${option.value}|${option.label}`).join('\n')
        : undefined,
      remoteValueType: remoteValueTypeForCandidate(candidate, control),
      required: true
    });
  });
  return result;
}

function requiredText(value: string | undefined, field: keyof WorkflowTemplateFormValues, label: string) {
  const normalized = value?.trim();
  if (!normalized) throw new WorkflowTemplateFormFieldError(field, `${label}不能为空`);
  return normalized;
}

function optionalText(value?: string) {
  const normalized = value?.trim();
  return normalized || undefined;
}

function parseJsonObject(text: string | undefined, field: WorkflowTemplateJsonField, label: string): JsonObject {
  let parsed: unknown;
  try {
    parsed = JSON.parse(text || '');
  } catch {
    throw new JsonFormFieldError(field, `${label}不是有效的 JSON`);
  }
  if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
    throw new JsonFormFieldError(field, `${label}必须是 JSON 对象`);
  }
  return parsed as JsonObject;
}

function parseTagIds(value?: string) {
  return [
    ...new Set(
      (value || '')
        .split(/[\s,，]+/)
        .map(item => item.trim())
        .filter(Boolean)
    )
  ];
}

function requiredParameterText(value: string | undefined, index: number, label: string) {
  const normalized = value?.trim();
  if (!normalized) {
    throw new WorkflowTemplateFormFieldError('parameters', `第 ${index + 1} 个参数的${label}不能为空`);
  }
  return normalized;
}

function parseParameterOptions(optionsText: string | undefined, index: number) {
  const options = (optionsText || '')
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => {
      const separator = line.indexOf('|');
      const value = (separator < 0 ? line : line.slice(0, separator)).trim();
      const label = (separator < 0 ? line : line.slice(separator + 1)).trim();
      if (!value || !label) {
        throw new WorkflowTemplateFormFieldError(
          'parameters',
          `第 ${index + 1} 个参数的选项格式应为“值|显示名称”，每行一个`
        );
      }
      return { label, value };
    });
  if (!options.length) {
    throw new WorkflowTemplateFormFieldError('parameters', `第 ${index + 1} 个参数至少需要一个选项`);
  }
  if (new Set(options.map(option => option.value)).size !== options.length) {
    throw new WorkflowTemplateFormFieldError('parameters', `第 ${index + 1} 个参数存在重复的选项值`);
  }
  return options;
}

function buildStructuredInputs(parameters: WorkflowTemplateParameterValue[]) {
  const formFields: JsonObject[] = [];
  const inputMapping: JsonObject = {};
  const inputKeys = new Set<string>();

  parameters.forEach((parameter, index) => {
    const inputKey = requiredParameterText(parameter.inputKey, index, '参数标识');
    if (!/^[A-Za-z][A-Za-z0-9._-]{0,127}$/.test(inputKey)) {
      throw new WorkflowTemplateFormFieldError(
        'parameters',
        `第 ${index + 1} 个参数标识必须以字母开头，且只能包含字母、数字、点、下划线或连字符`
      );
    }
    if (inputKeys.has(inputKey)) {
      throw new WorkflowTemplateFormFieldError('parameters', `参数标识重复：${inputKey}`);
    }
    inputKeys.add(inputKey);

    const label = requiredParameterText(parameter.label, index, '显示名称');
    const nodeId = requiredParameterText(parameter.nodeId, index, '节点 ID');
    const fieldName = requiredParameterText(parameter.fieldName, index, '字段名');
    const control = parameter.control;
    if (!control || !Object.hasOwn(VALUE_TYPE_BY_CONTROL, control)) {
      throw new WorkflowTemplateFormFieldError('parameters', `第 ${index + 1} 个参数请选择控件类型`);
    }
    const valueType = VALUE_TYPE_BY_CONTROL[control];
    const preservedField = restorePreservedObject(parameter.preservedFormFieldText);
    const preservedMapping = restorePreservedObject(parameter.preservedInputMappingText);
    const preservedControl = isWorkflowFormControl(preservedField.control) ? preservedField.control : undefined;
    const field: JsonObject = {
      ...preservedField,
      control,
      inputKey,
      label,
      required: Boolean(parameter.required),
      valueType
    };
    const description = optionalText(parameter.description);
    const placeholder = optionalText(parameter.placeholder);
    if (description) field.description = description;
    if (placeholder) field.placeholder = placeholder;
    if (control === 'select' || control === 'multi_select') {
      field.options = parseParameterOptions(parameter.optionsText, index);
    } else if (preservedControl && preservedControl !== control) {
      delete field.options;
    }
    if (['image', 'audio', 'video', 'file'].includes(control)) {
      const preservedConstraints = asObject(field.constraints) || {};
      field.constraints = {
        ...preservedConstraints,
        assetType: control,
        maxItems: typeof preservedConstraints.maxItems === 'number' ? preservedConstraints.maxItems : 1
      };
    }
    formFields.push(field);
    const defaultTransform = valueType === 'asset_array' ? 'runninghub_file_name' : 'identity';
    const preservedTransform = optionalString(preservedMapping.valueTransform);
    inputMapping[inputKey] = {
      ...preservedMapping,
      fieldName,
      inputKey,
      nodeId,
      remoteValueType: optionalText(parameter.remoteValueType) || REMOTE_TYPE_BY_CONTROL[control],
      required: Boolean(parameter.required),
      valueTransform: preservedControl === control && preservedTransform ? preservedTransform : defaultTransform,
      valueType
    };
  });

  return {
    formSchema: { fields: formFields, schemaVersion: 'workflow-form-1' },
    inputMapping
  };
}

export function toWorkflowTemplatePayloads(values: WorkflowTemplateFormValues): WorkflowTemplatePayloads {
  const executionMode = values.executionMode;
  if (!executionMode || !runningHubExecutionModeOptions.some(option => option.value === executionMode)) {
    throw new WorkflowTemplateFormFieldError('executionMode', '请选择执行模式');
  }
  const workflowId =
    executionMode === 'runninghub_workflow' ? requiredText(values.workflowId, 'workflowId', 'Workflow ID') : undefined;
  const webAppId =
    executionMode === 'runninghub_ai_app' ? requiredText(values.webAppId, 'webAppId', 'Web App ID') : undefined;
  const channel = values.channel;
  if (channel !== 'video_template' && channel !== 'workflow_inspiration') {
    throw new WorkflowTemplateFormFieldError('channel', '请选择模板频道');
  }
  if (typeof values.sortNo !== 'number' || values.sortNo < 0) {
    throw new WorkflowTemplateFormFieldError('sortNo', '排序号必须大于或等于 0');
  }
  const accessPassword = optionalText(values.accessPassword);
  if (accessPassword && values.clearAccessPassword) {
    throw new WorkflowTemplateFormFieldError('accessPassword', '新的访问密码与清空现有访问密码不能同时提交');
  }
  const structuredInputs = values.parameters ? buildStructuredInputs(values.parameters) : undefined;
  const timeoutSeconds =
    typeof values.timeoutSeconds === 'number' && values.timeoutSeconds >= 1 && values.timeoutSeconds <= 86400
      ? values.timeoutSeconds
      : 600;
  return {
    config: {
      clearAccessPassword: Boolean(values.clearAccessPassword),
      enabled: values.executionEnabled ?? true,
      executionMode,
      expectedRevision: values.expectedConfigRevision,
      inputMapping:
        structuredInputs?.inputMapping || parseJsonObject(values.inputMappingText, 'inputMappingText', '输入映射'),
      instanceType: values.instanceType || 'default',
      outputPolicy: {},
      runningHubAccountId: requiredText(values.runningHubAccountId, 'runningHubAccountId', 'RunningHub 账号'),
      timeoutSeconds,
      webAppId,
      workflowId,
      ...(accessPassword ? { accessPassword } : {})
    },
    template: {
      categoryId: requiredText(values.categoryId, 'categoryId', '分类编号'),
      channel,
      coverAssetId: optionalText(values.coverAssetId),
      description: optionalText(values.description),
      estimatedDurationSeconds: values.estimatedDurationSeconds,
      formSchema:
        structuredInputs?.formSchema || parseJsonObject(values.formSchemaText, 'formSchemaText', '动态表单 Schema'),
      name: requiredText(values.name, 'name', '模板名称'),
      recommended: Boolean(values.recommended),
      sortNo: values.sortNo,
      summary: optionalText(values.summary),
      tagIds: parseTagIds(values.tagIdsText)
    }
  };
}

export function toRunningHubAccountOptions(accounts: RunningHubAccountSummary[]) {
  return accounts.map(account => ({
    label: `${account.accountName}${account.hasApiKey ? '' : '（未配置密钥）'}`,
    value: account.accountId
  }));
}
