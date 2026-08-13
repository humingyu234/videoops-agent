import type { Request, Response } from 'express';
import { getMockTasks } from './workflowOrders';
const ok = (res: Response, data: unknown) => res.json({ code: 200, msg: '操作成功', data });
export default {
  'GET /api/tasks': (req: Request, res: Response) => {
    const pageNum = Math.max(1, Number(req.query.pageNum) || 1);
    const pageSize = Math.max(1, Number(req.query.pageSize) || 10);
    const status = typeof req.query.status === 'string' ? req.query.status : undefined;
    const all = getMockTasks().filter((task) => !status || task.status === status);
    const start = (pageNum - 1) * pageSize;
    return ok(res, { total: all.length, rows: all.slice(start, start + pageSize) });
  },
};
