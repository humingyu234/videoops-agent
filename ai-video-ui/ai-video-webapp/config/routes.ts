export default [
  {
    path: '/',
    component: './digital-human-studio',
    layout: false,
    routes: [
      {
        path: '/',
        redirect: '/studio',
      },
      {
        path: '/studio',
        name: '数字人创作',
      },
      {
        path: '/discover',
        name: '发现',
        component: './discovery/layout',
        access: 'canStudioQuery',
        routes: [
          {
            path: '/discover',
            component: './discovery',
          },
          {
            path: '/discover/templates/:templateId/create',
            component: './discovery/template-create',
          },
          {
            path: '/discover/templates/:templateId',
            name: '模板详情',
            component: './discovery/template-detail',
          },
        ],
      },
    ],
  },
  {
    path: '/orders/:orderId',
    name: '模板结果',
    component: './workflow-orders/detail',
    layout: false,
    access: 'canTaskQuery',
  },
  {
    path: '/tasks',
    name: '任务中心',
    component: './tasks',
    layout: false,
    access: 'canTaskQuery',
  },
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: '登录',
        path: '/user/login',
        component: './user/login',
      },
      {
        name: '找回密码',
        path: '/user/password-reset',
        component: './user/password-reset',
      },
      {
        name: '账号安全',
        path: '/user/security',
        component: './user/security',
      },
    ],
  },
  {
    path: '/welcome',
    name: '欢迎',
    icon: 'smile',
    component: './Welcome',
  },
  {
    path: '/admin',
    name: '管理页',
    icon: 'crown',
    access: 'canAdmin',
    routes: [
      {
        path: '/admin',
        redirect: '/admin/sub-page',
      },
      {
        path: '/admin/sub-page',
        name: '二级管理页',
        component: './Admin',
      },
    ],
  },
  {
    name: '查询表格',
    icon: 'table',
    path: '/list',
    component: './table-list',
  },
  {
    component: './exception/404',
    layout: false,
    path: './*',
  },
];
