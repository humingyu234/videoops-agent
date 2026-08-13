import { history, useModel } from '@umijs/max';
import React, { useState } from 'react';
import StudioSider from '@/pages/digital-human-studio/components/StudioSider';
import type { StudioRoute } from '@/pages/digital-human-studio/model';
import { usePersonalQuotaAccount } from '@/pages/digital-human-studio/usePersonalQuotaAccount';
import type { AppAuthState } from '@/services/ai-video/auth/authState';
import '@/pages/digital-human-studio/style.css';

export type CreatorWorkspaceKey = 'discover' | 'studio' | 'tasks';

export interface CreatorWorkspaceShellProps {
  activeKey: CreatorWorkspaceKey;
  children: React.ReactNode;
  description?: React.ReactNode;
  headerActions?: React.ReactNode;
  title?: React.ReactNode;
}

const SIDEBAR_STORAGE_KEY = 'dh-sidebar-collapsed';

function readInitialCollapsed(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.localStorage.getItem(SIDEBAR_STORAGE_KEY) === '1'
  );
}

const CreatorWorkspaceShell: React.FC<CreatorWorkspaceShellProps> = ({
  activeKey,
  children,
  description,
  headerActions,
  title,
}) => {
  const { initialState } = useModel('@@initialState') as {
    initialState?: AppAuthState;
  };
  const currentUser = initialState?.currentUser;
  const quotaQuery = usePersonalQuotaAccount(currentUser?.id);
  const [collapsed, setCollapsed] = useState(readInitialCollapsed);

  if (!currentUser) {
    return null;
  }

  const navigateToStudio = (route: StudioRoute) => {
    history.push(`/studio?view=${route}`);
  };

  return (
    <div
      className={`studio-shell app ${collapsed ? 'collapsed' : ''}`}
      data-workspace-key={activeKey}
    >
      <StudioSider
        activeKey={activeKey === 'discover' ? 'discover' : undefined}
        collapsed={collapsed}
        currentUser={currentUser}
        quotaState={quotaQuery.state}
        onCollapsedChange={(value) => {
          setCollapsed(value);
          window.localStorage.setItem(
            SIDEBAR_STORAGE_KEY,
            value ? '1' : '0',
          );
        }}
        onDiscover={() => history.push('/discover')}
        onRetryQuota={quotaQuery.retry}
        onRouteChange={navigateToStudio}
      />
      <div className="main">
        {(title || description || headerActions) && (
          <header className="topbar">
            <div>
              {title && <h1 className="topbar-title">{title}</h1>}
              {description && <div className="topbar-sub">{description}</div>}
            </div>
            {headerActions && (
              <div className="topbar-actions">{headerActions}</div>
            )}
          </header>
        )}
        <main className="content">{children}</main>
      </div>
    </div>
  );
};

export default CreatorWorkspaceShell;
