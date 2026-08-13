import type {
  DiscoveryBanner,
  DiscoveryCategory,
  DiscoveryChannel,
  DiscoveryHome,
  DiscoveryTag,
  WorkflowAssetReference,
  WorkflowAssetType,
  WorkflowCreationConfig,
  WorkflowFormControl,
  WorkflowFormDefaultValue,
  WorkflowInputConstraints,
  WorkflowInputField,
  WorkflowInputOption,
  WorkflowInputValueType,
  WorkflowMedia,
  WorkflowRequiredInput,
  WorkflowTemplateCard,
  WorkflowTemplateDetail,
  WorkflowTemplatePage,
} from './types';

type WireRecord = Record<string, unknown>;

const CHANNELS = ['video_template', 'workflow_inspiration'] as const;
const MEDIA_TYPES = ['image', 'video'] as const;
const ASSET_TYPES = ['image', 'audio', 'video', 'file'] as const;
const VALUE_TYPES = [
  'string',
  'integer',
  'decimal',
  'boolean',
  'string_array',
  'asset_array',
] as const;
const CONTROLS = [
  'text',
  'textarea',
  'integer',
  'decimal',
  'boolean',
  'select',
  'multi_select',
  'image',
  'audio',
  'video',
  'file',
] as const;
const CONTROL_VALUE_TYPES: Record<WorkflowFormControl, WorkflowInputValueType> = {
  text: 'string',
  textarea: 'string',
  integer: 'integer',
  decimal: 'decimal',
  boolean: 'boolean',
  select: 'string',
  multi_select: 'string_array',
  image: 'asset_array',
  audio: 'asset_array',
  video: 'asset_array',
  file: 'asset_array',
};
const FILE_CONTROLS = new Set<WorkflowFormControl>([
  'image',
  'audio',
  'video',
  'file',
]);
const DECIMAL_ID = /^[1-9]\d*$/;
const NON_NEGATIVE_INTEGER_STRING = /^(?:0|[1-9]\d*)$/;
const INTEGER_STRING = /^-?(?:0|[1-9]\d*)$/;
const DECIMAL_STRING = /^-?(?:0|[1-9]\d*)(?:\.\d+)?$/;
const SCHEMA_HASH = /^sha256:[0-9a-f]{64}$/;
const FORBIDDEN_VALUES = new Set([
  'self_hosted_comfyui',
  'runninghub_workflow',
  'runninghub_ai_app',
]);
const FORBIDDEN_KEYS = new Set(
  [
    'providerKind',
    'executionMode',
    'executionPlanId',
    'templateVersionId',
    'workflowId',
    'webAppId',
    'nodeId',
    'runningHubTaskId',
  ].map((key) => key.toLowerCase()),
);

function invalid(message: string): never {
  throw new Error(`Invalid discovery response: ${message}`);
}

function assertNoForbiddenWireData(value: unknown): void {
  const visited = new WeakSet<object>();

  function visit(current: unknown): void {
    if (typeof current === 'string') {
      if (FORBIDDEN_VALUES.has(current)) invalid('forbidden wire data');
      return;
    }
    if (typeof current !== 'object' || current === null) return;
    if (visited.has(current)) return;
    visited.add(current);
    if (Array.isArray(current)) {
      current.forEach(visit);
      return;
    }
    for (const [key, child] of Object.entries(current)) {
      if (FORBIDDEN_KEYS.has(key.toLowerCase())) {
        invalid('forbidden wire data');
      }
      visit(child);
    }
  }

  visit(value);
}

function record(value: unknown, field: string): WireRecord {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    invalid(`${field} must be an object`);
  }
  return value as WireRecord;
}

function exactKeys(
  value: WireRecord,
  required: readonly string[],
  optional: readonly string[] = [],
): void {
  const allowed = new Set([...required, ...optional]);
  if (
    required.some((key) => !Object.hasOwn(value, key)) ||
    Object.keys(value).some((key) => !allowed.has(key))
  ) {
    invalid('expected exact keys');
  }
}

function string(value: WireRecord, key: string): string {
  const result = value[key];
  if (typeof result !== 'string') invalid(`${key} must be a string`);
  return result;
}

function optionalString(value: WireRecord, key: string): string | undefined {
  if (!Object.hasOwn(value, key)) return undefined;
  return string(value, key);
}

function nonEmptyString(value: WireRecord, key: string): string {
  const result = string(value, key);
  if (!result.trim()) invalid(`${key} must not be empty`);
  return result;
}

export function assertDiscoveryDecimalId(value: unknown, field: string): string {
  if (typeof value !== 'string' || !DECIMAL_ID.test(value)) {
    invalid(`${field} must be a decimal id`);
  }
  return value;
}

function id(value: WireRecord, key: string): string {
  return assertDiscoveryDecimalId(value[key], key);
}

function enumValue<const T extends string>(
  value: WireRecord,
  key: string,
  allowed: readonly T[],
): T {
  const result = value[key];
  if (typeof result !== 'string' || !allowed.includes(result as T)) {
    invalid(`${key} contains an unknown enum value`);
  }
  return result as T;
}

function boolean(value: WireRecord, key: string): boolean {
  const result = value[key];
  if (typeof result !== 'boolean') invalid(`${key} must be a boolean`);
  return result;
}

function integer(
  value: WireRecord,
  key: string,
  minimum = 0,
): number {
  const result = value[key];
  if (!Number.isSafeInteger(result) || (result as number) < minimum) {
    invalid(`${key} must be a safe non-negative integer`);
  }
  return result as number;
}

function array<T>(
  value: WireRecord,
  key: string,
  parser: (item: unknown, index: number) => T,
): T[] {
  const result = value[key];
  if (!Array.isArray(result)) invalid(`${key} must be an array`);
  return result.map(parser);
}

function optionalArray<T>(
  value: WireRecord,
  key: string,
  parser: (item: unknown, index: number) => T,
): T[] | undefined {
  if (!Object.hasOwn(value, key)) return undefined;
  return array(value, key, parser);
}

function parseUrl(value: string, field: string): string {
  if (!value || /[\\\u0000-\u001f\u007f]/.test(value)) {
    invalid(`${field} must be same-origin or HTTPS`);
  }
  if (/^\/(?!\/)/.test(value)) return value;
  try {
    const parsed = new URL(value);
    if (parsed.protocol === 'https:' && parsed.hostname) return value;
  } catch {
    // Fall through to the stable parser error.
  }
  invalid(`${field} must be same-origin or HTTPS`);
}

function media(value: unknown, field: string): WorkflowMedia {
  const item = record(value, field);
  exactKeys(
    item,
    ['mediaId', 'mediaType', 'url', 'width', 'height', 'alt'],
    ['posterUrl'],
  );
  const posterUrl = optionalString(item, 'posterUrl');
  return {
    mediaId: id(item, 'mediaId'),
    mediaType: enumValue(item, 'mediaType', MEDIA_TYPES),
    url: parseUrl(nonEmptyString(item, 'url'), `${field}.url`),
    ...(posterUrl === undefined
      ? {}
      : { posterUrl: parseUrl(posterUrl, `${field}.posterUrl`) }),
    // RunningHub's media metadata may omit source dimensions.  Zero means
    // unknown here; layout must not reject an otherwise usable cover.
    width: integer(item, 'width'),
    height: integer(item, 'height'),
    alt: string(item, 'alt'),
  };
}

function tag(value: unknown, field: string): DiscoveryTag {
  const item = record(value, field);
  exactKeys(item, ['tagCode', 'label']);
  return { tagCode: id(item, 'tagCode'), label: nonEmptyString(item, 'label') };
}

function category(value: unknown, field: string): DiscoveryCategory {
  const item = record(value, field);
  exactKeys(item, ['categoryCode', 'label', 'templateCount']);
  const templateCount = string(item, 'templateCount');
  if (!NON_NEGATIVE_INTEGER_STRING.test(templateCount)) {
    invalid('templateCount must be a non-negative integer string');
  }
  return {
    categoryCode: id(item, 'categoryCode'),
    label: nonEmptyString(item, 'label'),
    templateCount,
  };
}

function channel(value: unknown, field: string): DiscoveryChannel {
  const item = record(value, field);
  exactKeys(item, ['channel', 'label', 'description', 'templateCount']);
  const templateCount = string(item, 'templateCount');
  if (!NON_NEGATIVE_INTEGER_STRING.test(templateCount)) {
    invalid('templateCount must be a non-negative integer string');
  }
  return {
    channel: enumValue(item, 'channel', CHANNELS),
    label: nonEmptyString(item, 'label'),
    description: string(item, 'description'),
    templateCount,
  };
}

const CARD_REQUIRED = [
  'templateId',
  'title',
  'summary',
  'channel',
  'category',
  'tags',
  'cover',
  'enabledAt',
] as const;
const CARD_OPTIONAL = [
  'preview',
  'usageCount',
  'estimatedDurationSeconds',
] as const;

function cardFields(value: WireRecord): WorkflowTemplateCard {
  const categoryRecord = record(value.category, 'category');
  exactKeys(categoryRecord, ['categoryCode', 'label']);
  const coverValue = value.cover;
  const previewValue = Object.hasOwn(value, 'preview')
    ? media(value.preview, 'preview')
    : undefined;
  const usageCount = optionalString(value, 'usageCount');
  if (
    usageCount !== undefined &&
    !NON_NEGATIVE_INTEGER_STRING.test(usageCount)
  ) {
    invalid('usageCount must be a non-negative integer string');
  }
  return {
    templateId: id(value, 'templateId'),
    title: nonEmptyString(value, 'title'),
    summary: string(value, 'summary'),
    channel: enumValue(value, 'channel', CHANNELS),
    category: {
      categoryCode: id(categoryRecord, 'categoryCode'),
      label: nonEmptyString(categoryRecord, 'label'),
    },
    tags: array(value, 'tags', (item, index) => tag(item, `tags[${index}]`)),
    cover: coverValue === null ? null : media(coverValue, 'cover'),
    ...(previewValue === undefined ? {} : { preview: previewValue }),
    ...(usageCount === undefined ? {} : { usageCount }),
    ...(Object.hasOwn(value, 'estimatedDurationSeconds')
      ? {
          estimatedDurationSeconds: integer(
            value,
            'estimatedDurationSeconds',
          ),
        }
      : {}),
    enabledAt: nonEmptyString(value, 'enabledAt'),
  };
}

function templateCard(value: unknown, field: string): WorkflowTemplateCard {
  const item = record(value, field);
  exactKeys(item, CARD_REQUIRED, CARD_OPTIONAL);
  return cardFields(item);
}

function banner(value: unknown, field: string): DiscoveryBanner {
  const item = record(value, field);
  exactKeys(item, ['bannerId', 'title', 'target', 'media'], ['subtitle']);
  const target = record(item.target, `${field}.target`);
  const type = enumValue(target, 'type', ['template', 'channel'] as const);
  const parsedTarget = type === 'template'
    ? (() => {
        exactKeys(target, ['type', 'templateId']);
        return { type, templateId: id(target, 'templateId') } as const;
      })()
    : (() => {
        exactKeys(target, ['type', 'channel']);
        return {
          type,
          channel: enumValue(target, 'channel', CHANNELS),
        } as const;
      })();
  const subtitle = optionalString(item, 'subtitle');
  return {
    bannerId: id(item, 'bannerId'),
    title: nonEmptyString(item, 'title'),
    ...(subtitle === undefined ? {} : { subtitle }),
    target: parsedTarget,
    media: media(item.media, `${field}.media`),
  };
}

function requiredInput(value: unknown, field: string): WorkflowRequiredInput {
  const item = record(value, field);
  exactKeys(item, ['label', 'valueType', 'required'], [
    'semanticKey',
    'assetType',
  ]);
  const semanticKey = optionalString(item, 'semanticKey');
  const assetType = Object.hasOwn(item, 'assetType')
    ? enumValue(item, 'assetType', ASSET_TYPES)
    : undefined;
  return {
    ...(semanticKey === undefined ? {} : { semanticKey }),
    label: nonEmptyString(item, 'label'),
    valueType: enumValue(item, 'valueType', VALUE_TYPES),
    ...(assetType === undefined ? {} : { assetType }),
    required: boolean(item, 'required'),
  };
}

function option(value: unknown, field: string): WorkflowInputOption {
  const item = record(value, field);
  exactKeys(item, ['value', 'label']);
  return {
    value: nonEmptyString(item, 'value'),
    label: nonEmptyString(item, 'label'),
  };
}

function stringList(
  value: WireRecord,
  key: string,
): string[] | undefined {
  return optionalArray(value, key, (item) => {
    if (typeof item !== 'string' || !item.trim()) {
      invalid(`${key} must contain non-empty strings`);
    }
    return item;
  });
}

function constraints(
  value: unknown,
  field: string,
): WorkflowInputConstraints {
  const item = record(value, field);
  exactKeys(item, [], [
    'min',
    'max',
    'minLength',
    'maxLength',
    'minItems',
    'maxItems',
    'assetType',
    'allowedExtensions',
    'allowedContentTypes',
    'maxBytesPerAsset',
  ]);
  const min = optionalString(item, 'min');
  const max = optionalString(item, 'max');
  if (min !== undefined && !DECIMAL_STRING.test(min)) invalid('min is invalid');
  if (max !== undefined && !DECIMAL_STRING.test(max)) invalid('max is invalid');
  const assetType = Object.hasOwn(item, 'assetType')
    ? enumValue(item, 'assetType', ASSET_TYPES)
    : undefined;
  const maxBytesPerAsset = optionalString(item, 'maxBytesPerAsset');
  if (
    maxBytesPerAsset !== undefined &&
    !NON_NEGATIVE_INTEGER_STRING.test(maxBytesPerAsset)
  ) {
    invalid('maxBytesPerAsset is invalid');
  }
  const allowedExtensions = stringList(item, 'allowedExtensions');
  const allowedContentTypes = stringList(item, 'allowedContentTypes');
  return {
    ...(min === undefined ? {} : { min }),
    ...(max === undefined ? {} : { max }),
    ...(Object.hasOwn(item, 'minLength')
      ? { minLength: integer(item, 'minLength') }
      : {}),
    ...(Object.hasOwn(item, 'maxLength')
      ? { maxLength: integer(item, 'maxLength') }
      : {}),
    ...(Object.hasOwn(item, 'minItems')
      ? { minItems: integer(item, 'minItems') }
      : {}),
    ...(Object.hasOwn(item, 'maxItems')
      ? { maxItems: integer(item, 'maxItems') }
      : {}),
    ...(assetType === undefined ? {} : { assetType }),
    ...(allowedExtensions === undefined ? {} : { allowedExtensions }),
    ...(allowedContentTypes === undefined ? {} : { allowedContentTypes }),
    ...(maxBytesPerAsset === undefined ? {} : { maxBytesPerAsset }),
  };
}

function assetReferences(value: unknown, field: string): WorkflowAssetReference[] {
  if (!Array.isArray(value)) invalid(`${field} must be an array`);
  return value.map((entry, index) => {
    const item = record(entry, `${field}[${index}]`);
    exactKeys(item, ['assetId']);
    return { assetId: id(item, 'assetId') };
  });
}

function defaultValue(
  value: unknown,
  valueType: WorkflowInputValueType,
  field: string,
): WorkflowFormDefaultValue {
  if (valueType === 'boolean') {
    if (typeof value !== 'boolean') invalid(`${field} must be a boolean`);
    return value;
  }
  if (valueType === 'string_array') {
    if (!Array.isArray(value) || value.some((item) => typeof item !== 'string')) {
      invalid(`${field} must be a string array`);
    }
    if (new Set(value).size !== value.length) {
      invalid(`${field} must not contain duplicates`);
    }
    return value as string[];
  }
  if (valueType === 'asset_array') return assetReferences(value, field);
  if (typeof value !== 'string') invalid(`${field} must be a string`);
  if (valueType === 'integer' && !INTEGER_STRING.test(value)) {
    invalid(`${field} must be a canonical integer string`);
  }
  if (valueType === 'decimal' && !DECIMAL_STRING.test(value)) {
    invalid(`${field} must be a canonical decimal string`);
  }
  return value;
}

function inputField(value: unknown, field: string): WorkflowInputField {
  const item = record(value, field);
  exactKeys(
    item,
    ['inputKey', 'label', 'control', 'valueType', 'required'],
    [
      'semanticKey',
      'description',
      'defaultValue',
      'placeholder',
      'options',
      'constraints',
    ],
  );
  const control = enumValue(item, 'control', CONTROLS);
  const valueType = enumValue(item, 'valueType', VALUE_TYPES);
  if (CONTROL_VALUE_TYPES[control] !== valueType) {
    invalid('control and valueType are incompatible');
  }
  const semanticKey = optionalString(item, 'semanticKey');
  const description = optionalString(item, 'description');
  const placeholder = optionalString(item, 'placeholder');
  const options = optionalArray(item, 'options', (entry, index) =>
    option(entry, `${field}.options[${index}]`),
  );
  if (control === 'select' || control === 'multi_select') {
    if (!options?.length || new Set(options.map(({ value }) => value)).size !== options.length) {
      invalid('select controls require unique non-empty options');
    }
  } else if (options !== undefined) {
    invalid('options are only valid for select controls');
  }
  const parsedConstraints = Object.hasOwn(item, 'constraints')
    ? constraints(item.constraints, `${field}.constraints`)
    : undefined;
  if (FILE_CONTROLS.has(control)) {
    if (
      !parsedConstraints ||
      parsedConstraints.assetType !== (control as WorkflowAssetType)
    ) {
      invalid('file controls require matching constraints.assetType');
    }
    if (Object.hasOwn(item, 'defaultValue')) {
      invalid('file controls cannot define defaultValue');
    }
  }
  const parsedDefault = Object.hasOwn(item, 'defaultValue')
    ? defaultValue(item.defaultValue, valueType, `${field}.defaultValue`)
    : undefined;
  return {
    inputKey: nonEmptyString(item, 'inputKey'),
    ...(semanticKey === undefined ? {} : { semanticKey }),
    label: nonEmptyString(item, 'label'),
    ...(description === undefined ? {} : { description }),
    control,
    valueType,
    required: boolean(item, 'required'),
    ...(parsedDefault === undefined ? {} : { defaultValue: parsedDefault }),
    ...(placeholder === undefined ? {} : { placeholder }),
    ...(options === undefined ? {} : { options }),
    ...(parsedConstraints === undefined
      ? {}
      : { constraints: parsedConstraints }),
  };
}

export function parseDiscoveryHome(value: unknown): DiscoveryHome {
  assertNoForbiddenWireData(value);
  const item = record(value, 'home');
  exactKeys(item, [
    'banners',
    'recommendations',
    'channels',
    'categories',
    'tags',
  ]);
  return {
    banners: array(item, 'banners', (entry, index) =>
      banner(entry, `banners[${index}]`),
    ),
    recommendations: array(item, 'recommendations', (entry, index) =>
      templateCard(entry, `recommendations[${index}]`),
    ),
    channels: array(item, 'channels', (entry, index) =>
      channel(entry, `channels[${index}]`),
    ),
    categories: array(item, 'categories', (entry, index) =>
      category(entry, `categories[${index}]`),
    ),
    tags: array(item, 'tags', (entry, index) =>
      tag(entry, `tags[${index}]`),
    ),
  };
}

export function parseWorkflowTemplatePage(value: unknown): WorkflowTemplatePage {
  assertNoForbiddenWireData(value);
  const item = record(value, 'templatePage');
  exactKeys(item, ['rows', 'total']);
  return {
    rows: array(item, 'rows', (entry, index) =>
      templateCard(entry, `rows[${index}]`),
    ),
    total: integer(item, 'total'),
  };
}

export function parseWorkflowTemplateDetail(
  value: unknown,
): WorkflowTemplateDetail {
  assertNoForbiddenWireData(value);
  const item = record(value, 'templateDetail');
  exactKeys(
    item,
    [...CARD_REQUIRED, 'description', 'cases', 'requiredInputs'],
    CARD_OPTIONAL,
  );
  return {
    ...cardFields(item),
    description: string(item, 'description'),
    cases: array(item, 'cases', (entry, index) =>
      media(entry, `cases[${index}]`),
    ),
    requiredInputs: array(item, 'requiredInputs', (entry, index) =>
      requiredInput(entry, `requiredInputs[${index}]`),
    ),
  };
}

export function parseWorkflowCreationConfig(
  value: unknown,
): WorkflowCreationConfig {
  assertNoForbiddenWireData(value);
  const item = record(value, 'creationConfig');
  exactKeys(
    item,
    ['templateId', 'schemaVersion', 'schemaHash', 'fields', 'billingPolicy'],
    ['estimatedDurationSeconds'],
  );
  if (item.schemaVersion !== 'workflow-form-1') {
    invalid('schemaVersion contains an unknown enum value');
  }
  const schemaHash = string(item, 'schemaHash');
  if (!SCHEMA_HASH.test(schemaHash)) invalid('schemaHash is invalid');
  const fields = array(item, 'fields', (entry, index) =>
    inputField(entry, `fields[${index}]`),
  );
  if (new Set(fields.map(({ inputKey }) => inputKey)).size !== fields.length) {
    invalid('inputKey values must be unique');
  }
  const billingPolicy = record(item.billingPolicy, 'billingPolicy');
  exactKeys(billingPolicy, ['mode']);
  if (billingPolicy.mode !== 'free') {
    invalid('billingPolicy.mode contains an unknown enum value');
  }
  return {
    templateId: id(item, 'templateId'),
    schemaVersion: 'workflow-form-1',
    schemaHash: schemaHash as `sha256:${string}`,
    fields,
    ...(Object.hasOwn(item, 'estimatedDurationSeconds')
      ? {
          estimatedDurationSeconds: integer(
            item,
            'estimatedDurationSeconds',
          ),
        }
      : {}),
    billingPolicy: { mode: 'free' },
  };
}
