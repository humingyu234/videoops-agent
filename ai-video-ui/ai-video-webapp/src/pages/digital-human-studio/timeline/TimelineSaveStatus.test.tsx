import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import TimelineSaveStatus from './TimelineSaveStatus';

describe('TimelineSaveStatus', () => {
  it('renders conflict actions without presenting the draft as saved', () => {
    render(<TimelineSaveStatus saveStatus={{ kind: 'conflict', baseRevision: '3', serverRevision: '4', snapshot: { schemaVersion: 'timeline-1', canvas: { width: 1080, height: 1920, frameRate: 30, durationMs: 1, safeMarginRatio: 0.05 }, tracks: [] } }} onReloadServer={vi.fn()} onSaveConflictCopy={vi.fn()} />);
    expect(screen.getByText('草稿冲突')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新加载服务端版本' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '另存为冲突版本' })).toBeInTheDocument();
    expect(screen.queryByText('已保存')).not.toBeInTheDocument();
  });
});
