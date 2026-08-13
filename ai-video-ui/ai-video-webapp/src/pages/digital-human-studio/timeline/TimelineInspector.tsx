import { Empty } from 'antd';
import type {
  TimelineDocument,
  TimelineElement,
} from '@/services/ai-video/creation-timeline/types';
import AudioInspector from './AudioInspector';
import FancyTextInspector from './FancyTextInspector';
import ImageInspector from './ImageInspector';
import PictureInPictureInspector from './PictureInPictureInspector';
import SubtitleInspector from './SubtitleInspector';
import { findTimelineElement } from './selectors';
import VisualEffectInspector from './VisualEffectInspector';

const typeLabel: Record<TimelineElement['elementType'], string> = {
  audio: '音频',
  fancy_text: '花字',
  image_overlay: '图片',
  main_video: '主视频',
  pip_video: '画中画',
  subtitle: '字幕',
  visual_effect: '特效',
};

function formatDuration(milliseconds: number): string {
  return `${(milliseconds / 1000).toFixed(1)} 秒`;
}

function ElementInspector({
  element,
  onChange,
}: {
  element: TimelineElement;
  onChange?: (element: TimelineElement) => void;
}) {
  switch (element.elementType) {
    case 'image_overlay':
      return (
        <ImageInspector
          element={element}
          onPatch={(patch) => onChange?.({ ...element, ...patch })}
        />
      );
    case 'pip_video':
      return (
        <PictureInPictureInspector
          element={element}
          onPatch={(patch) => onChange?.({ ...element, ...patch })}
        />
      );
    case 'subtitle':
      return <SubtitleInspector element={element} onChange={onChange} />;
    case 'fancy_text':
      return <FancyTextInspector element={element} onChange={onChange} />;
    case 'audio':
      return <AudioInspector element={element} onChange={onChange} />;
    case 'visual_effect':
      return <VisualEffectInspector element={element} onChange={onChange} />;
    case 'main_video':
      return null;
  }
}

export default function TimelineInspector({
  timeline,
  selectedElementId,
  onChange,
}: {
  timeline: TimelineDocument;
  selectedElementId?: string;
  onChange?: (element: TimelineElement) => void;
}) {
  const selection = selectedElementId
    ? findTimelineElement(timeline, selectedElementId)?.element
    : undefined;

  if (!selection) {
    return (
      <aside aria-label="元素信息" className="timeline-inspector-v2">
        <h2>项目画布</h2>
        <dl>
          <div>
            <dt>分辨率</dt>
            <dd>
              {timeline.canvas.width} × {timeline.canvas.height}
            </dd>
          </div>
          <div>
            <dt>帧率</dt>
            <dd>{timeline.canvas.frameRate} fps</dd>
          </div>
          <div>
            <dt>总时长</dt>
            <dd>{formatDuration(timeline.canvas.durationMs)}</dd>
          </div>
        </dl>
        <Empty
          description="请选择时间轴元素"
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      </aside>
    );
  }

  return (
    <aside aria-label="元素信息" className="timeline-inspector-v2">
      <h2>
        {typeLabel[selection.elementType]} · {selection.label}
      </h2>
      <dl>
        <div>
          <dt>开始</dt>
          <dd>{formatDuration(selection.startMs)}</dd>
        </div>
        <div>
          <dt>结束</dt>
          <dd>{formatDuration(selection.endMs)}</dd>
        </div>
        <div>
          <dt>时长</dt>
          <dd>{formatDuration(selection.endMs - selection.startMs)}</dd>
        </div>
      </dl>
      <ElementInspector element={selection} onChange={onChange} />
    </aside>
  );
}
