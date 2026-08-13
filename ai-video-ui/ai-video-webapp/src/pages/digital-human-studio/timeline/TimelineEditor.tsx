import { Result, Skeleton, Splitter } from 'antd';
import { useState } from 'react';
import type {
  TimelineDocument,
  TimelineElement,
  TimelineTrackType,
} from '@/services/ai-video/creation-timeline/types';
import ElementAddBar, { type TimelineAddAction } from './ElementAddBar';
import TimelineInspector from './TimelineInspector';
import TimelinePreview from './TimelinePreview';
import TimelineTracks from './TimelineTracks';
import { EMPTY_TIMELINE, type TimelineAction } from './types';

export type TimelineEditorStatus =
  | 'ready'
  | 'loading'
  | 'empty'
  | 'error'
  | 'forbidden'
  | 'asset-invalid';

export interface TimelineEditorProps {
  timeline?: TimelineDocument;
  selectedElementId?: string;
  status?: TimelineEditorStatus;
  previewVideoUrl?: string;
  previewAudioUrl?: string;
  previewVideoBlob?: Blob;
  previewAudioBlob?: Blob;
  onSelect?: (elementId?: string) => void;
  onAddElement?: (
    action: TimelineAddAction,
    trackType: TimelineTrackType,
  ) => void;
  onElementChange?: (element: TimelineElement) => void;
  onAction?: (action: TimelineAction) => void;
}

function EditorState({
  status,
}: {
  status: Exclude<TimelineEditorStatus, 'ready'>;
}) {
  if (status === 'loading') {
    return (
      <section
        aria-busy="true"
        aria-label="正在加载时间轴"
        className="timeline-editor-state"
      >
        <Skeleton active paragraph={{ rows: 6 }} />
        <span>正在加载时间轴</span>
      </section>
    );
  }

  if (status === 'empty') {
    return (
      <section aria-label="时间轴为空" className="timeline-editor-state">
        <Result
          status="info"
          title="暂无可编辑时间轴"
          subTitle="请先完成数字人底片生成。"
        />
      </section>
    );
  }

  if (status === 'forbidden') {
    return (
      <section aria-label="时间轴权限不足" className="timeline-editor-state">
        <Result
          status="403"
          title="无权访问此创作项目"
          subTitle="请确认当前账号拥有该项目的编辑权限。"
        />
      </section>
    );
  }

  if (status === 'asset-invalid') {
    return (
      <section aria-label="时间轴素材失效" className="timeline-editor-state">
        <Result
          status="warning"
          title="素材已失效，无法合成"
          subTitle="请替换失效素材后继续编辑。"
        />
      </section>
    );
  }

  return (
    <section aria-label="时间轴加载失败" className="timeline-editor-state">
      <Result status="error" title="时间轴加载失败" subTitle="请稍后重试。" />
    </section>
  );
}

export default function TimelineEditor({
  timeline = EMPTY_TIMELINE,
  selectedElementId,
  status = 'ready',
  previewVideoUrl,
  previewAudioUrl,
  previewVideoBlob,
  previewAudioBlob,
  onSelect,
  onAddElement,
  onElementChange,
  onAction,
}: TimelineEditorProps) {
  const [playheadMs, setPlayheadMs] = useState(0);

  if (status !== 'ready') return <EditorState status={status} />;

  return (
    <Splitter className="timeline-editor-v2" orientation="horizontal">
      <Splitter.Panel defaultSize="68%" min="480px">
        <div className="timeline-editor-v2__main">
          <TimelinePreview
            audioUrl={previewAudioUrl}
            audioBlob={previewAudioBlob}
            selectedElementId={selectedElementId}
            timeline={timeline}
            videoUrl={previewVideoUrl}
            videoBlob={previewVideoBlob}
            onAction={onAction}
            onPositionChange={setPlayheadMs}
            onSelect={onSelect}
          />
          <ElementAddBar onAddElement={onAddElement} />
          <TimelineTracks
            selectedElementId={selectedElementId}
            timeline={timeline}
            playheadMs={playheadMs}
            onAction={onAction}
            onSelect={onSelect}
          />
        </div>
      </Splitter.Panel>
      <Splitter.Panel defaultSize="32%" min="264px">
        <TimelineInspector
          selectedElementId={selectedElementId}
          timeline={timeline}
          onChange={onElementChange}
        />
      </Splitter.Panel>
    </Splitter>
  );
}
