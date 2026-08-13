import { describe, expect, it } from 'vitest';
import { parseCreationAssetWire } from './adapter';

const assetWire = {
  assetId: '90071992547410001',
  originalName: 'product.png',
  mimeType: 'image/png',
  sha256: 'a'.repeat(64),
  assetType: 'image',
  usageOrigin: 'upload',
  status: 'ready',
  sizeBytes: '1024',
  durationMs: null,
  width: 1080,
  height: 1920,
  hasVideoStream: false,
  hasAudioStream: false,
  createdAt: '2026-08-08T08:00:00+08:00',
};

describe('creation asset adapter', () => {
  it('parses the safe asset metadata shape without storage locations', () => {
    expect(parseCreationAssetWire(assetWire)).toMatchObject({
      assetId: '90071992547410001', assetType: 'image', status: 'ready', sizeBytes: '1024',
    });
  });

  it('rejects internal storage fields and unsafe numeric identifiers', () => {
    expect(() => parseCreationAssetWire({ ...assetWire, storageKey: 'private/key' })).toThrow('contains an unknown field');
    expect(() => parseCreationAssetWire({ ...assetWire, assetId: 90071992547410001 })).toThrow('assetId must be a canonical decimal string');
  });
});
