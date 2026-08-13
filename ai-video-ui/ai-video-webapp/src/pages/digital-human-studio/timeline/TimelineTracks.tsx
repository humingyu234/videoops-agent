import { Empty } from 'antd';
import type {
  TimelineDocument,
  TimelineTrack,
  TimelineTrackType,
} from '@/services/ai-video/creation-timeline/types';
import TimelineClip from './TimelineClip';
import TimelinePlayhead from './TimelinePlayhead';
import TimelineRuler from './TimelineRuler';
import type { TimelineAction } from './types';

const trackLabel: Record<TimelineTrackType, string> = {
  fancy_text: '花字',
  subtitle: '字幕',
  visual_effect: '视觉特效',
  image_overlay: '图片',
  pip_video: '画中画',
  main_video: '主视频',
  primary_audio: '原始声音',
  background_music: '背景音乐',
  sound_effect: '音效',
};

function orderedTracks(tracks: TimelineTrack[]): TimelineTrack[] {
  const byOrder = (left: TimelineTrack, right: TimelineTrack) =>
    left.order - right.order;
  const visual = tracks.filter((track) => track.area === 'top').sort(byOrder);
  const main = tracks
    .filter((track) => track.trackType === 'main_video')
    .sort(byOrder);
  const center = tracks
    .filter(
      (track) => track.area === 'center' && track.trackType !== 'main_video',
    )
    .sort(byOrder);
  const audio = tracks.filter((track) => track.area === 'bottom').sort(byOrder);
  return [...visual, ...main, ...center, ...audio];
}

export default function TimelineTracks({
  timeline,
  selectedElementId,
  playheadMs = 0,
  onAction,
  onSelect,
}: {
  timeline: TimelineDocument;
  selectedElementId?: string;
  playheadMs?: number;
  onAction?: (action: TimelineAction) => void;
  onSelect?: (elementId?: string) => void;
}) {
  const tracks = orderedTracks(timeline.tracks);

  return (
    <section aria-label="时间轴轨道" className="timeline-tracks-v2">
      <TimelineRuler durationMs={timeline.canvas.durationMs} />
      <div className="timeline-tracks-v2__body">
        <TimelinePlayhead
          durationMs={timeline.canvas.durationMs}
          positionMs={playheadMs}
        />
        {tracks.length === 0 ? (
          <Empty
            description="暂无轨道；创建项目后将在此显示"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          />
        ) : (
          tracks.map((track) => (
            <article
              data-testid="timeline-track"
              data-track-type={track.trackType}
              key={track.trackId}
              className="timeline-track-v2"
            >
              <div className="timeline-track-v2__label">
                <span>{trackLabel[track.trackType]}</span>
                {track.trackType === 'main_video' && <strong>固定</strong>}
              </div>
                <div className="timeline-track-v2__lane">
                  {track.elements.map((element) => (
                    <TimelineClip
                      durationMs={timeline.canvas.durationMs}
                      element={element}
                      key={element.elementId}
                      playheadMs={playheadMs}
                      selected={selectedElementId === element.elementId}
                      timeline={timeline}
                      track={track}
                      zoom={1}
                      onAction={onAction}
                      onSelect={onSelect}
                    />
                  ))}
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
