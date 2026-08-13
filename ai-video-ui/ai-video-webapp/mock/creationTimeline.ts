import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { Request, Response } from 'express';

export const CREATION_TIMELINE_MOCK_MARKER = 'AI_VIDEO_CREATION_TIMELINE_MOCK_MARKER';

const contractDirectory = resolve(
  process.cwd(),
  '../../docs/contracts/creation-timeline',
);

function fixture(fileName: string): Record<string, unknown> {
  return JSON.parse(readFileSync(resolve(contractDirectory, fileName), 'utf8')) as Record<string, unknown>;
}

function ok(response: Response, data: unknown): void {
  response.send({ code: 200, msg: 'ok', data });
}

export default {
  'GET /api/studio/creation-projects/:projectId': (_request: Request, response: Response) => {
    ok(response, fixture('project.example.json'));
  },
  'GET /api/studio/creation-projects/:projectId/timeline-draft': (_request: Request, response: Response) => {
    ok(response, fixture('timeline-draft.example.json'));
  },
  'PUT /api/studio/creation-projects/:projectId/timeline-draft': (_request: Request, response: Response) => {
    ok(response, {
      ...fixture('timeline-draft.example.json'),
      replayed: false,
      superseded: false,
      normalizationChanges: [],
    });
  },
  'POST /api/studio/creation-projects/:projectId/:taskKind-tasks': (_request: Request, response: Response) => {
    ok(response, fixture('timeline-task.example.json'));
  },
};
