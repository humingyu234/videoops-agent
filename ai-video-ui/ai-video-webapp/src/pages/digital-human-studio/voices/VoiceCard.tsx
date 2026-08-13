import React, { useRef } from 'react';
import StudioIcon from '../components/StudioIcon';
import type { VoiceItem } from '../model';
import {
  buildVoiceTicks,
  clampVoicePercent,
  formatVoiceSeconds,
  getVoiceWords,
} from './voiceTimeline';

interface VoiceCardProps {
  voice: VoiceItem;
  expanded: boolean;
  editing: boolean;
  playing: boolean;
  progress: number;
  draft: string;
  onToggle: () => void;
  onPlayToggle: () => void;
  onSeek: (percent: number) => void;
  onEdit: () => void;
  onCancelEdit: () => void;
  onSaveEdit: () => void;
  onDraftChange: (value: string) => void;
  onRetry?: () => void;
  onResync?: () => void;
  onDelete?: () => void;
  deleting?: boolean;
}

const activateOnKeyboard = (event: React.KeyboardEvent, action: () => void) => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    action();
  }
};

const VoiceCard: React.FC<VoiceCardProps> = ({
  voice,
  expanded,
  editing,
  playing,
  progress,
  draft,
  onToggle,
  onPlayToggle,
  onSeek,
  onEdit,
  onCancelEdit,
  onSaveEdit,
  onDraftChange,
  onRetry,
  onResync,
  onDelete,
  deleting = false,
}) => {
  const draggingPointer = useRef<number | null>(null);
  const hasExactTimeline =
    voice.timelineExact === true && (voice.timeline?.length ?? 0) > 0;
  const needsResync =
    Boolean(voice.recordRevision) &&
    voice.status === 'verified' &&
    !hasExactTimeline;
  const words = hasExactTimeline
    ? (voice.timeline ?? [])
    : voice.recordRevision
      ? []
      : getVoiceWords(voice.sents, voice.secs);
  const ticks = buildVoiceTicks(voice.secs);
  const currentSeconds = clampVoicePercent(progress) * voice.secs;
  const playerId = `voice-player-${voice.id}`;

  const seekFromPointer = (event: React.PointerEvent<HTMLDivElement>) => {
    const rect = event.currentTarget.getBoundingClientRect();
    if (rect.width <= 0) return;
    onSeek(clampVoicePercent((event.clientX - rect.left) / rect.width));
  };

  const stopBubble = (event: React.SyntheticEvent) => event.stopPropagation();

  return (
    <article
      className={`vcard${expanded ? ' expanded' : ''}${playing ? ' playing' : ''}`}
      data-od-id={`voice-${voice.id}`}
    >
      {/* biome-ignore lint/a11y/useSemanticElements: the row contains its own play button, so it cannot be a button element */}
      <div
        aria-controls={playerId}
        aria-expanded={expanded}
        aria-label={voice.name}
        className="vrow"
        role="button"
        tabIndex={0}
        onClick={onToggle}
        onKeyDown={(event) => activateOnKeyboard(event, onToggle)}
      >
        <button
          aria-label={`${playing ? '暂停' : '播放'} ${voice.name}`}
          className="voice-play"
          type="button"
          onClick={(event) => {
            event.stopPropagation();
            onPlayToggle();
          }}
          onKeyDown={(event) => event.stopPropagation()}
        >
          <StudioIcon name={playing ? 'pause' : 'play'} />
        </button>
        <div className="vinfo">
          <div className="vname">
            {voice.name}
            <span
              className={`tag ${voice.status === 'verified' ? 'tag-success' : 'tag-warn'}`}
            >
              {voice.status === 'verified'
                ? '已校验'
                : voice.status === 'failed'
                  ? '解析失败'
                  : '校验中'}
            </span>
            <span className="tag tag-soft">
              {voice.type === 'clone'
                ? '克隆'
                : voice.type === 'origin'
                  ? '原声'
                  : '公共'}
            </span>
          </div>
          <div className="vmeta">
            {voice.meta} · {voice.script.slice(0, 22)}…
          </div>
        </div>
        {(voice.status === 'failed' || voice.transcriptionStatus === 'unparsed') && onRetry && (
          <button
            className="vrow-retry"
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onRetry();
            }}
            onKeyDown={(event) => event.stopPropagation()}
          >
            {voice.transcriptionStatus === 'unparsed' ? '开始解析' : '重新解析'}
          </button>
        )}
        {needsResync && onResync && (
          <button
            className="vrow-retry vrow-resync"
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onResync();
            }}
            onKeyDown={(event) => event.stopPropagation()}
          >
            重新同步
          </button>
        )}
        <span className="vdur">{voice.dur}</span>
        <span aria-hidden="true" className="vchev">
          <StudioIcon name="right" />
        </span>
      </div>

      <div aria-hidden={!expanded} className="vplayer" id={playerId}>
        {expanded && (
          <>
            <div className="vtimeline">
              <div className="vtl-top">
                <span className="vtl-cur">
                  {formatVoiceSeconds(currentSeconds)}
                </span>
                <span className="vtl-dur">{voice.dur}</span>
              </div>
              <div
                aria-label={`${voice.name} 时间轴`}
                aria-valuemax={100}
                aria-valuemin={0}
                aria-valuenow={Math.round(clampVoicePercent(progress) * 100)}
                className="vtl-track"
                role="slider"
                tabIndex={0}
                onPointerCancel={(event) => {
                  if (draggingPointer.current === event.pointerId) {
                    draggingPointer.current = null;
                  }
                }}
                onPointerDown={(event) => {
                  draggingPointer.current = event.pointerId;
                  event.currentTarget.setPointerCapture?.(event.pointerId);
                  seekFromPointer(event);
                }}
                onPointerMove={(event) => {
                  if (draggingPointer.current === event.pointerId)
                    seekFromPointer(event);
                }}
                onPointerUp={(event) => {
                  if (draggingPointer.current !== event.pointerId) return;
                  seekFromPointer(event);
                  event.currentTarget.releasePointerCapture?.(event.pointerId);
                  draggingPointer.current = null;
                }}
              >
                <span className="vtl-rail" />
                <span
                  className="vtl-fill"
                  style={{ width: `${clampVoicePercent(progress) * 100}%` }}
                />
                <span
                  className="vtl-head"
                  style={{ left: `${clampVoicePercent(progress) * 100}%` }}
                />
                <span className="vtl-ticks">
                  {ticks.map((tick) => (
                    <React.Fragment key={tick.seconds}>
                      <i
                        className={`vtl-tick${tick.major ? ' vtl-tick-major' : ''}`}
                        style={{ left: `${tick.leftPercent}%` }}
                      />
                      {tick.label && (
                        <i
                          className="vtl-ticklab"
                          style={{ left: `${tick.leftPercent}%` }}
                        >
                          {tick.label}
                        </i>
                      )}
                    </React.Fragment>
                  ))}
                </span>
              </div>
            </div>

            <div
              className="vptext-bar"
              onClick={stopBubble}
              onKeyDown={stopBubble}
            >
              <span className={`vptext-bar-lab${editing ? ' editing' : ''}`}>
                {editing ? '编辑中…' : '示范文案'}
              </span>
              <span className="vedit-actions">
                {editing ? (
                  <>
                    <button
                      className="vedit-btn ghost"
                      type="button"
                      onClick={onCancelEdit}
                    >
                      取消
                    </button>
                    <button
                      className="vedit-btn"
                      type="button"
                      onClick={onSaveEdit}
                    >
                      保存
                    </button>
                  </>
                ) : (
                  <>
                    {voice.status === 'verified' && (
                      <button
                        className="vedit-btn"
                        type="button"
                        onClick={onEdit}
                      >
                        编辑
                      </button>
                    )}
                    {voice.type !== 'public' && onDelete && (
                      <button
                        aria-label="删除声音"
                        className="vedit-btn voice-delete-action"
                        disabled={deleting}
                        type="button"
                        onClick={(event) => {
                          event.stopPropagation();
                          onDelete();
                        }}
                        onKeyDown={(event) => event.stopPropagation()}
                      >
                        <StudioIcon name="delete" />{' '}
                        {deleting ? '删除中…' : '删除'}
                      </button>
                    )}
                  </>
                )}
              </span>
            </div>
            {editing ? (
              /* biome-ignore lint/a11y/useSemanticElements: contentEditable is required for the inline rich-text editing surface */
              <div
                aria-label="编辑示范文案"
                className="vptext"
                contentEditable
                role="textbox"
                suppressContentEditableWarning
                tabIndex={0}
                onClick={stopBubble}
                onInput={(event) =>
                  onDraftChange(event.currentTarget.textContent ?? '')
                }
                onKeyDown={stopBubble}
              >
                {draft}
              </div>
            ) : (
              <div className="vptext" data-vtext={voice.id}>
                {words.length === 0
                  ? voice.script
                  : words.map((word) => {
                      const done =
                        word.start + word.dur <= currentSeconds && progress > 0;
                      const now =
                        !done && word.start <= currentSeconds && progress > 0;
                      const seek = () => onSeek(word.start / voice.secs);
                      return (
                        /* biome-ignore lint/a11y/useSemanticElements: inline spans preserve continuous Chinese text layout */
                        <span
                          className={`vpword vw${word.isPunct ? ' punct' : ''}${done ? ' done' : ''}${now ? ' now' : ''}`}
                          key={word.start}
                          role="button"
                          tabIndex={0}
                          onClick={seek}
                          onKeyDown={(event) => activateOnKeyboard(event, seek)}
                        >
                          {word.word}
                        </span>
                      );
                    })}
              </div>
            )}
            {!editing && (
              <div className={`vhint${needsResync ? ' vsync-warning' : ''}`}>
                {needsResync
                  ? '文案未与音频同步，请重新同步'
                  : '点击播放按钮试听 · 点击整行展开/收起 · 点击任意词元 / 时间轴 → 跳到该处播放'}
              </div>
            )}
          </>
        )}
      </div>
    </article>
  );
};

export default VoiceCard;
