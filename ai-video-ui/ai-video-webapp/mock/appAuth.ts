import type { Request, Response } from 'express';

const user = {
  id: '10001',
  username: 'creator',
  displayName: '创作者',
  avatarUrl: '/discovery/presenter-studio.webp',
  permissions: [
    'aivideo:studio:query',
    'aivideo:studio:generate',
    'aivideo:task:query',
    'aivideo:task:cancel',
    'aivideo:asset:query',
    'aivideo:asset:upload',
    'aivideo:asset:download',
    'aivideo:quota:query',
  ],
  roles: ['personal_creator'],
  workspace: {
    id: '20001',
    name: '个人工作区',
    roleCode: 'personal_creator',
  },
};

const ok = (res: Response, data: unknown) =>
  res.json({ code: 200, msg: '操作成功', data });

export default {
  'POST /api/auth/login': (_req: Request, res: Response) =>
    ok(res, {
      access_token: 'discovery-local-preview-token',
      client_id: 'desktop-web',
      currentWorkspace: user.workspace,
      expire_in: 7200,
    }),
  'GET /api/auth/me': (_req: Request, res: Response) => ok(res, user),
  'POST /api/auth/logout': (_req: Request, res: Response) => ok(res, null),
  'GET /api/quota/account': (_req: Request, res: Response) =>
    ok(res, {
      quotaUnit: 'ai_text_credit',
      availableBalance: '8800',
      lockedBalance: '0',
      usedBalance: '0',
      totalBalance: '8800',
    }),
};
