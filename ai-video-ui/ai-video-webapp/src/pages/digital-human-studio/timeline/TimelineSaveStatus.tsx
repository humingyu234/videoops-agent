import { Alert, Button, Tag } from 'antd';
import type { TimelineSaveStatus as TimelineSaveStatusValue } from '@/services/ai-video/creation-timeline/types';

const labels: Record<Exclude<TimelineSaveStatusValue['kind'], 'conflict'>, string> = {
  saved: '已保存', dirty: '未保存', saving: '保存中', failed: '保存失败',
};

export default function TimelineSaveStatus({ saveStatus, onRetry, onReloadServer, onSaveConflictCopy }: {
  saveStatus: TimelineSaveStatusValue;
  onRetry?: () => void;
  onReloadServer: () => void;
  onSaveConflictCopy: () => void;
}) {
  if (saveStatus.kind === 'conflict') {
    return <Alert showIcon type="warning" title="草稿冲突" description={`服务端版本 ${saveStatus.serverRevision}`} action={<><Button size="small" onClick={onReloadServer}>重新加载服务端版本</Button><Button size="small" onClick={onSaveConflictCopy}>另存为冲突版本</Button></>} />;
  }
  if (saveStatus.kind === 'failed') {
    return <Alert showIcon type="error" title="保存失败" action={onRetry ? <Button size="small" onClick={onRetry}>重试</Button> : undefined} />;
  }
  return <Tag color={saveStatus.kind === 'saved' ? 'success' : 'processing'}>{labels[saveStatus.kind]}</Tag>;
}
