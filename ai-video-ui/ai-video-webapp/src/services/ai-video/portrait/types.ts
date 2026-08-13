export type PortraitGender = 'female' | 'male' | 'unspecified';
export type PortraitStatus = 'processing' | 'ready' | 'failed';

export interface Portrait {
  portraitId: string;
  name: string;
  gender: PortraitGender;
  sceneTags: string[];
  note?: string;
  availabilityStatus: PortraitStatus;
  failureReason?: string;
  previewUrl?: string;
  previewExpiresAt?: string;
  originalFileName?: string;
  contentType?: string;
  fileFormat?: string;
  width?: number;
  height?: number;
  sizeBytes?: string;
  recordRevision: string;
  createTime: string;
  updateTime: string;
}

export interface PortraitPage {
  rows: Portrait[];
  total: number;
}

export interface PortraitInput {
  assetId: string;
  name: string;
  gender: PortraitGender;
  sceneTags: string[];
  note?: string;
  idempotencyKey: string;
}

export interface PortraitUpdateInput extends Omit<PortraitInput, 'assetId' | 'idempotencyKey'> {
  expectedRevision: string;
}

export interface PortraitAccessUrl {
  url: string;
  expiresAt: string;
  contentType: string;
}

export interface PortraitAsset {
  assetId: string;
  availabilityStatus: PortraitStatus;
}
