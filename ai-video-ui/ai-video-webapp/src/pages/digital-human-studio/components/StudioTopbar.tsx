import React from 'react';
import StudioIcon from './StudioIcon';

interface StudioTopbarProps {
  title: string;
  description: string;
  onNewProject: () => void;
  onNotifications: () => void;
}

const StudioTopbar: React.FC<StudioTopbarProps> = ({
  title,
  description,
  onNewProject,
  onNotifications,
}) => (
  <header className="topbar">
    <div>
      <div className="topbar-title">{title}</div>
      <div className="topbar-sub">{description}</div>
    </div>
    <div className="topbar-actions">
      <button
        aria-label="通知"
        className="icon-btn notification-button"
        type="button"
        onClick={onNotifications}
      >
        <StudioIcon name="bell" />
        <i />
      </button>
      <button
        className="btn btn-primary btn-sm"
        type="button"
        onClick={onNewProject}
      >
        <StudioIcon name="plus" /> 新建作品
      </button>
    </div>
  </header>
);

export default StudioTopbar;
