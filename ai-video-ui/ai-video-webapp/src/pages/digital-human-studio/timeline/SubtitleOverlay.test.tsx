import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  SubtitleElement,
  TimelineDocument,
} from '@/services/ai-video/creation-timeline/types';
import SubtitleOverlay from './SubtitleOverlay';
import { applySubtitleServerNormalization } from './subtitle';

const subtitle: SubtitleElement = {
  elementId: 'subtitle_1',
  elementType: 'subtitle',
  startMs: 0,
  endMs: 3_000,
  zIndex: 10,
  enabled: true,
  locked: false,
  label: '字幕一',
  sourceTextSnapshot: '欢迎使用 AI 视频！',
  displayText: '欢迎使用AI视频',
  sourceStartOffset: 0,
  sourceEndOffset: 10,
  fontCode: 'noto_sans_cjk_sc_regular',
  fontVersion: '1.000',
  fontSha256: 'a'.repeat(64),
  fontSizePx: 48,
  color: '#FFFFFFFF',
  backgroundEnabled: false,
  outlineEnabled: false,
  outlineWidthPx: 0,
  safeAreaAnchor: 'lower',
  alignment: 'center',
};

const timeline: TimelineDocument = {
  schemaVersion: 'timeline-1',
  canvas: {
    width: 1080,
    height: 1920,
    frameRate: 30,
    durationMs: 3_000,
    safeMarginRatio: 0.05,
  },
  tracks: [],
};

describe('SubtitleOverlay', () => {
  it('keeps the complete display text and warns instead of silently truncating it', () => {
    render(
      <SubtitleOverlay
        element={{ ...subtitle, displayText: '完整字幕文本不能被前端截断' }}
        measureText={() => 400}
        safeAreaWidthPx={300}
      />,
    );

    expect(screen.getByText('完整字幕文本不能被前端截断')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      '字幕可能超出安全区，服务端保存会规范化。',
    );
  });

  it('maps a preview pointer position back to C0 anchor and alignment only', () => {
    const onPlacementChange = vi.fn();
    render(
      <SubtitleOverlay
        element={subtitle}
        onPlacementChange={onPlacementChange}
      />,
    );
    const overlay = screen.getByLabelText('字幕预览');
    vi.spyOn(overlay, 'getBoundingClientRect').mockReturnValue({
      bottom: 200,
      height: 200,
      left: 0,
      right: 400,
      toJSON: () => ({}),
      top: 0,
      width: 400,
      x: 0,
      y: 0,
    });

    fireEvent.pointerUp(overlay, { clientX: 24, clientY: 100 });

    expect(onPlacementChange).toHaveBeenCalledWith({
      safeAreaAnchor: 'center',
      alignment: 'left',
    });
    expect(onPlacementChange.mock.calls[0]?.[0]).not.toHaveProperty(
      'transform',
    );
  });

  it('uses only server-returned timeline and normalized element ids for highlighting', () => {
    const normalizedTimeline = { ...timeline, tracks: [] };
    const result = applySubtitleServerNormalization(normalizedTimeline, [
      { elementId: subtitle.elementId },
    ]);

    expect(result.timeline).toBe(normalizedTimeline);
    expect(result.normalizedElementIds).toEqual(new Set([subtitle.elementId]));

    render(
      <SubtitleOverlay
        element={subtitle}
        normalizedElementIds={result.normalizedElementIds}
      />,
    );
    expect(screen.getByLabelText('字幕预览')).toHaveAttribute(
      'data-normalized',
      'true',
    );
  });
});
