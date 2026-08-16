import { describe, expect, it } from 'vitest';
import routes from '../config/routes';

describe('creator routes', () => {
  it('makes the real Agent the default while preserving the protected Studio workspace', () => {
    const workspaceRoute = routes.find(
      (route) =>
        route.path === '/' && route.component === './digital-human-studio',
    );

    expect(workspaceRoute).toEqual(
      expect.objectContaining({
        layout: false,
        routes: expect.arrayContaining([
          expect.objectContaining({ path: '/', redirect: '/agent' }),
          expect.objectContaining({ path: '/studio' }),
        ]),
      }),
    );
    expect(routes).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          path: '/agent',
          name: 'VideoOps Agent',
          component: './agent',
          layout: false,
        }),
        expect.objectContaining({
          path: '/orders/:orderId',
          name: '模板结果',
          layout: false,
          access: 'canTaskQuery',
        }),
        expect.objectContaining({
          path: '/tasks',
          layout: false,
          access: 'canTaskQuery',
        }),
      ]),
    );
    expect(
      workspaceRoute?.routes?.find((route) => route.path === '/discover'),
    ).toEqual(
      expect.objectContaining({
        component: './discovery/layout',
        access: 'canStudioQuery',
        routes: expect.arrayContaining([
          expect.objectContaining({
            path: '/discover',
            component: './discovery',
          }),
          expect.objectContaining({
            path: '/discover/templates/:templateId',
            component: './discovery/template-detail',
          }),
          expect.objectContaining({
            path: '/discover/templates/:templateId/create',
            component: './discovery/template-create',
          }),
        ]),
      }),
    );
  });
});
