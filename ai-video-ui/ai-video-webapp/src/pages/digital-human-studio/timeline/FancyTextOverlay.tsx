import type { PointerEvent } from 'react';
import type {
  FancyTextElement,
  TimelineTransform,
} from '@/services/ai-video/creation-timeline/types';
import { getFancyTextTemplate } from './fancyTextTemplates';

export interface FancyTextOverlayProps {
  element: FancyTextElement;
  onTransformChange?: (transform: TimelineTransform) => void;
}

function clamp(value: number, minimum: number, maximum: number): number {
  return Math.min(maximum, Math.max(minimum, value));
}

function previewStyleFor(element: FancyTextElement) {
  switch (element.templateCode) {
    case 'gold_impact':
      return {
        color: element.accentColor,
        textShadow: `0 0 2px ${element.color}`,
      };
    case 'neon_breathe':
      return {
        color: element.color,
        textShadow: `0 0 12px ${element.accentColor}`,
      };
    case 'handwriting_reveal':
      return { color: element.color, textDecoration: 'underline' };
    case 'bubble_bounce':
      return {
        color: element.color,
        borderRadius: 999,
        backgroundColor: element.accentColor,
      };
    case 'title_wipe':
      return {
        color: element.color,
        borderBottom: `4px solid ${element.accentColor}`,
      };
    default:
      return { color: element.color, fontWeight: 700 };
  }
}

export default function FancyTextOverlay({
  element,
  onTransformChange,
}: FancyTextOverlayProps) {
  const template = getFancyTextTemplate(element.templateCode);
  const updateTransform = (event: PointerEvent<HTMLElement>) => {
    const rectangle = event.currentTarget.getBoundingClientRect();
    if (rectangle.width <= 0 || rectangle.height <= 0) return;
    const centeredX =
      (event.clientX - rectangle.left) / rectangle.width -
      element.transform.widthRatio / 2;
    const centeredY =
      (event.clientY - rectangle.top) / rectangle.height -
      element.transform.heightRatio / 2;
    onTransformChange?.({
      ...element.transform,
      xRatio: clamp(centeredX, 0, 1 - element.transform.widthRatio),
      yRatio: clamp(centeredY, 0, 1 - element.transform.heightRatio),
    });
  };

  return (
    <section
      aria-label="花字预览"
      data-template={template.code}
      style={{ position: 'relative', width: '100%', height: '100%' }}
      onPointerUp={updateTransform}
    >
      <span
        style={{
          position: 'absolute',
          left: `${element.transform.xRatio * 100}%`,
          top: `${element.transform.yRatio * 100}%`,
          width: `${element.transform.widthRatio * 100}%`,
          height: `${element.transform.heightRatio * 100}%`,
          opacity: element.transform.opacity,
          transform: `rotate(${element.transform.rotationDeg}deg)`,
          fontFamily: element.fontCode,
          ...previewStyleFor(element),
        }}
      >
        {element.text}
      </span>
    </section>
  );
}
