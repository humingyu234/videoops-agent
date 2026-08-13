import { Outlet, useLocation } from '@umijs/max';

export default function DiscoveryLayout() {
  const { pathname } = useLocation();
  const isTemplateDetail = /^\/discover\/templates\/[^/]+$/.test(pathname);
  const title = isTemplateDetail ? '模板详情' : '发现';
  const description = isTemplateDetail
    ? '了解模板效果与所需素材'
    : '从成熟工作流开始创作，让灵感更快变成作品';

  return (
    <>
      <header className="topbar">
        <div>
          <h1 className="topbar-title">{title}</h1>
          <div className="topbar-sub">{description}</div>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </>
  );
}
