export type WorkflowChannel = 'video_template' | 'workflow_inspiration';
export type WorkflowMediaType = 'image' | 'video';
export type WorkflowAssetType = 'image' | 'audio' | 'video' | 'file';
export type WorkflowInputValueType =
  | 'string'
  | 'integer'
  | 'decimal'
  | 'boolean'
  | 'string_array'
  | 'asset_array';
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

export interface WorkflowMedia {
  mediaId: string;
  mediaType: WorkflowMediaType;
  url: string;
  posterUrl?: string;
  width: number;
  height: number;
  alt: string;
}

export interface DiscoveryTag {
  tagCode: string;
  label: string;
}

export interface DiscoveryCategory {
  categoryCode: string;
  label: string;
  templateCount: string;
}

export interface DiscoveryChannel {
  channel: WorkflowChannel;
  label: string;
  description: string;
  templateCount: string;
}

export interface WorkflowTemplateCard {
  templateId: string;
  title: string;
  summary: string;
  channel: WorkflowChannel;
  category: { categoryCode: string; label: string };
  tags: DiscoveryTag[];
  cover: WorkflowMedia | null;
  preview?: WorkflowMedia;
  usageCount?: string;
  estimatedDurationSeconds?: number;
  enabledAt: string;
}

export interface DiscoveryBanner {
  bannerId: string;
  title: string;
  subtitle?: string;
  target:
    | { type: 'template'; templateId: string }
    | { type: 'channel'; channel: WorkflowChannel };
  media: WorkflowMedia;
}

export interface DiscoveryHome {
  banners: DiscoveryBanner[];
  recommendations: WorkflowTemplateCard[];
  channels: DiscoveryChannel[];
  categories: DiscoveryCategory[];
  tags: DiscoveryTag[];
}

export interface WorkflowTemplatePage {
  rows: WorkflowTemplateCard[];
  total: number;
}

export interface WorkflowRequiredInput {
  semanticKey?: string;
  label: string;
  valueType: WorkflowInputValueType;
  assetType?: WorkflowAssetType;
  required: boolean;
}

export interface WorkflowTemplateDetail extends WorkflowTemplateCard {
  description: string;
  cases: WorkflowMedia[];
  requiredInputs: WorkflowRequiredInput[];
}

export type WorkflowAssetReference = { assetId: string };
export type WorkflowFormDefaultValue =
  | string
  | boolean
  | string[]
  | WorkflowAssetReference[];

export interface WorkflowInputOption {
  value: string;
  label: string;
}

export interface WorkflowInputConstraints {
  min?: string;
  max?: string;
  minLength?: number;
  maxLength?: number;
  minItems?: number;
  maxItems?: number;
  assetType?: WorkflowAssetType;
  allowedExtensions?: string[];
  allowedContentTypes?: string[];
  maxBytesPerAsset?: string;
}

export interface WorkflowInputField {
  inputKey: string;
  semanticKey?: string;
  label: string;
  description?: string;
  control: WorkflowFormControl;
  valueType: WorkflowInputValueType;
  required: boolean;
  defaultValue?: WorkflowFormDefaultValue;
  placeholder?: string;
  options?: WorkflowInputOption[];
  constraints?: WorkflowInputConstraints;
}

export interface WorkflowCreationConfig {
  templateId: string;
  schemaVersion: 'workflow-form-1';
  schemaHash: `sha256:${string}`;
  fields: WorkflowInputField[];
  estimatedDurationSeconds?: number;
  billingPolicy: { mode: 'free' };
}

export interface TemplateListParams {
  pageNum: number;
  pageSize: number;
  channel?: WorkflowChannel;
  categoryCode?: string;
  tagCodes?: string;
  keyword?: string;
  sort?: 'latest' | 'recommended';
}
