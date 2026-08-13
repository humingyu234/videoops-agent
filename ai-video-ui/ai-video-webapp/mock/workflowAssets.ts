import type { Request, Response } from 'express';
const ok = (res: Response, data: unknown) => res.json({ code: 200, msg: '操作成功', data });
export default {
  'POST /api/assets/upload-sessions': (_req: Request, res: Response) => ok(res, { uploadSessionId: 'upload-session-dev-1', uploadUrl: '/api/assets/mock-put', expiresAt: '2026-08-05T12:00:00+08:00' }),
  'POST /api/assets/upload-sessions/:sessionId/complete': (req: Request, res: Response) => ok(res, { assetId: `asset-${req.params.sessionId}`, status: 'ready' }),
  'GET /api/assets/:assetId/access-url': (_req: Request, res: Response) => ok(res, { url: '/discovery/skincare.webp', expiresAt: '2026-08-05T12:00:00+08:00' }),
};
