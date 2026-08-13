import { Drawer } from 'antd';
import React from 'react';
import type { DetailRequest } from './LibraryView';

interface StudioDetailDrawerProps {
  detail?: DetailRequest;
  onClose: () => void;
}

const StudioDetailDrawer: React.FC<StudioDetailDrawerProps> = ({
  detail,
  onClose,
}) => (
  <Drawer
    className="studio-detail-drawer"
    footer={detail?.footer}
    open={Boolean(detail)}
    title={
      <div>
        <div className="drawer-title">{detail?.title}</div>
        <div className="drawer-sub">{detail?.subtitle}</div>
      </div>
    }
    size={440}
    onClose={onClose}
  >
    {detail?.content}
  </Drawer>
);

export default StudioDetailDrawer;
