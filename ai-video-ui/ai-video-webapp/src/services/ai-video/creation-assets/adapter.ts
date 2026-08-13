import { assertRecord, readDecimalString, readEnum, readString } from '../core/wire';
import type { DecimalId } from '../creation-timeline/types';
import type { CreationAsset } from './types';

type WireRecord = Record<string, unknown>;

const assetKeys = [
  'assetId', 'originalName', 'mimeType', 'sha256', 'assetType', 'usageOrigin', 'status', 'sizeBytes',
  'durationMs', 'width', 'height', 'hasVideoStream', 'hasAudioStream', 'createdAt',
] as const;

function invalid(message: string): never {
  throw new Error(`Invalid wire response: ${message}`);
}

function assertExactAssetKeys(record: WireRecord): void {
  if (Object.keys(record).some((key) => !assetKeys.includes(key as typeof assetKeys[number]))) {
    invalid('creationAsset contains an unknown field');
  }
  const missing = assetKeys.find((key) => !Object.hasOwn(record, key));
  if (missing) invalid(`creationAsset.${missing} is required`);
}

function readNullableInteger(record: WireRecord, key: string, minimum: number, maximum: number): number | null {
  const value = record[key];
  if (value === null) return null;
  if (!Number.isSafeInteger(value) || (value as number) < minimum || (value as number) > maximum) {
    invalid(`${key} must be an in-range integer or null`);
  }
  return value as number;
}

function readBoolean(record: WireRecord, key: string): boolean {
  if (typeof record[key] !== 'boolean') invalid(`${key} must be a boolean`);
  return record[key] as boolean;
}

function readPositiveDecimal(record: WireRecord, key: string): string {
  const value = readDecimalString(record, key);
  if (!/^[1-9]\d*$/.test(value)) invalid(`${key} must be a positive decimal string`);
  return value;
}

export function parseCreationAssetWire(value: unknown): CreationAsset {
  const record = assertRecord(value, 'creationAsset');
  assertExactAssetKeys(record);
  const sha256 = readString(record, 'sha256');
  if (!/^[0-9a-f]{64}$/.test(sha256)) invalid('sha256 must be a SHA-256');
  return {
    assetId: readPositiveDecimal(record, 'assetId') as DecimalId,
    originalName: readString(record, 'originalName'),
    mimeType: readString(record, 'mimeType'),
    sha256,
    assetType: readEnum(record, 'assetType', ['image', 'video', 'audio'] as const),
    usageOrigin: readEnum(record, 'usageOrigin', ['upload', 'digital_human_output', 'timeline_render_output'] as const),
    status: readEnum(record, 'status', ['pending', 'ready', 'failed'] as const),
    sizeBytes: readPositiveDecimal(record, 'sizeBytes'),
    durationMs: readNullableInteger(record, 'durationMs', 0, 120000),
    width: readNullableInteger(record, 'width', 1, 8192),
    height: readNullableInteger(record, 'height', 1, 8192),
    hasVideoStream: readBoolean(record, 'hasVideoStream'),
    hasAudioStream: readBoolean(record, 'hasAudioStream'),
    createdAt: readString(record, 'createdAt'),
  };
}
