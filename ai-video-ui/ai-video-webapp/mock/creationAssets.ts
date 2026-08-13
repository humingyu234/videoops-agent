import type { Request, Response } from 'express';

export const CREATION_ASSETS_MOCK_MARKER = 'AI_VIDEO_CREATION_TIMELINE_MOCK_MARKER';

const asset = {
  assetId: '90071992547410001',
  originalName: 'timeline-image.png',
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

function ok(response: Response, data: unknown): void {
  response.send({ code: 200, msg: 'ok', data });
}

export default {
  'GET /api/studio/creation-assets': (_request: Request, response: Response) => {
    ok(response, { total: 1, rows: [asset] });
  },
  'GET /api/studio/creation-assets/:assetId': (_request: Request, response: Response) => {
    ok(response, asset);
  },
  'POST /api/studio/creation-assets': (_request: Request, response: Response) => {
    ok(response, asset);
  },
};
