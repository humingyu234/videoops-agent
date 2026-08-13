import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type {
  TimelineDocument,
  TimelineElement,
} from '@/services/ai-video/creation-timeline/types';
import TimelineInspector from './TimelineInspector';

vi.mock('./ImageInspector', () => ({
  default: ({
    onPatch,
  }: {
    onPatch?: (patch: { fitMode: 'cover' }) => void;
  }) => (
    <button
      data-testid="image-inspector"
      type="button"
      onClick={() => onPatch?.({ fitMode: 'cover' })}
    >
      Edit image
    </button>
  ),
}));

vi.mock('./PictureInPictureInspector', () => ({
  default: ({
    onPatch,
  }: {
    onPatch?: (patch: { sourceStartMs: number }) => void;
  }) => (
    <button
      data-testid="pip-inspector"
      type="button"
      onClick={() => onPatch?.({ sourceStartMs: 250 })}
    >
      Edit picture in picture
    </button>
  ),
}));

vi.mock('./SubtitleInspector', () => ({
  default: ({
    element,
    onChange,
  }: {
    element: TimelineElement;
    onChange?: (element: TimelineElement) => void;
  }) => (
    <button
      data-testid="subtitle-inspector"
      type="button"
      onClick={() => onChange?.({ ...element, label: 'Updated subtitle' })}
    >
      Edit subtitle
    </button>
  ),
}));

vi.mock('./FancyTextInspector', () => ({
  default: ({
    element,
    onChange,
  }: {
    element: TimelineElement;
    onChange?: (element: TimelineElement) => void;
  }) => (
    <button
      data-testid="fancy-text-inspector"
      type="button"
      onClick={() => onChange?.({ ...element, label: 'Updated fancy text' })}
    >
      Edit fancy text
    </button>
  ),
}));

vi.mock('./AudioInspector', () => ({
  default: ({
    element,
    onChange,
  }: {
    element: TimelineElement;
    onChange?: (element: TimelineElement) => void;
  }) => (
    <button
      data-testid="audio-inspector"
      type="button"
      onClick={() => onChange?.({ ...element, label: 'Updated audio' })}
    >
      Edit audio
    </button>
  ),
}));

vi.mock('./VisualEffectInspector', () => ({
  default: ({
    element,
    onChange,
  }: {
    element: TimelineElement;
    onChange?: (element: TimelineElement) => void;
  }) => (
    <button
      data-testid="visual-effect-inspector"
      type="button"
      onClick={() => onChange?.({ ...element, label: 'Updated visual effect' })}
    >
      Edit visual effect
    </button>
  ),
}));

function createElement(
  elementType: TimelineElement['elementType'],
): TimelineElement {
  return {
    elementId: `${elementType}-1`,
    elementType,
    startMs: 0,
    endMs: 1_000,
    zIndex: 0,
    enabled: true,
    locked: false,
    label: `${elementType} element`,
  } as TimelineElement;
}

function createTimeline(element: TimelineElement): TimelineDocument {
  return {
    schemaVersion: 'timeline-1',
    canvas: {
      width: 1080,
      height: 1920,
      frameRate: 30,
      durationMs: 10_000,
      safeMarginRatio: 0.05,
    },
    tracks: [
      {
        trackId: 'test-track',
        trackType: 'image_overlay',
        area: 'top',
        order: 0,
        locked: false,
        muted: false,
        elements: [element],
      },
    ],
  };
}

describe('TimelineInspector', () => {
  it.each([
    ['image_overlay', 'image-inspector', { fitMode: 'cover' }],
    ['pip_video', 'pip-inspector', { sourceStartMs: 250 }],
    ['subtitle', 'subtitle-inspector', { label: 'Updated subtitle' }],
    ['fancy_text', 'fancy-text-inspector', { label: 'Updated fancy text' }],
    ['audio', 'audio-inspector', { label: 'Updated audio' }],
    [
      'visual_effect',
      'visual-effect-inspector',
      { label: 'Updated visual effect' },
    ],
  ] as const)(
    'renders the %s inspector and forwards its full updated element',
    (elementType, inspectorTestId, patch) => {
      const element = createElement(elementType);
      const onChange = vi.fn();

      render(
        <TimelineInspector
          selectedElementId={element.elementId}
          timeline={createTimeline(element)}
          onChange={onChange}
        />,
      );

      fireEvent.click(screen.getByTestId(inspectorTestId));

      expect(onChange).toHaveBeenCalledWith({ ...element, ...patch });
    },
  );
});
