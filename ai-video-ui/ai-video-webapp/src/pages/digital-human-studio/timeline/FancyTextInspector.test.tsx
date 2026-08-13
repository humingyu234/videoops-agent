import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { FancyTextElement } from '@/services/ai-video/creation-timeline/types';
import FancyTextInspector from './FancyTextInspector';
import FancyTextOverlay from './FancyTextOverlay';
import { FIXED_TIMELINE_FONTS } from './fancyTextTemplates';

const fancyText: FancyTextElement = {
  elementId: 'fancy_1',
  elementType: 'fancy_text',
  startMs: 100,
  endMs: 3_100,
  zIndex: 20,
  enabled: true,
  locked: false,
  label: '花字一',
  text: '专业视频',
  templateCode: 'keyword_pop',
  fontCode: 'noto_sans_cjk_sc_regular',
  fontVersion: '2.004',
  fontSha256:
    '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b',
  color: '#FFFFFFFF',
  accentColor: '#FFCC00FF',
  transform: {
    xRatio: 0.2,
    yRatio: 0.2,
    widthRatio: 0.4,
    heightRatio: 0.2,
    rotationDeg: 0,
    opacity: 1,
  },
  animationIntensity: 'normal',
  enterDurationMs: 300,
  exitDurationMs: 300,
  suggestionTaskId: null,
  suggestionReason: null,
};

const readyFontStatus = {
  status: 'ready' as const,
  fonts: [...FIXED_TIMELINE_FONTS],
};

describe('FancyTextInspector', () => {
  it('changes only C0 fields and preserves text timing when switching templates', () => {
    const onChange = vi.fn();
    render(
      <FancyTextInspector
        element={fancyText}
        fontStatus={readyFontStatus}
        onChange={onChange}
      />,
    );

    fireEvent.change(screen.getByRole('textbox', { name: '花字文字' }), {
      target: { value: '新标题' },
    });
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ text: '新标题' }),
    );

    fireEvent.click(screen.getByText('思源宋体'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        fontCode: 'noto_serif_cjk_sc_regular',
        fontVersion: '2.003',
        fontSha256:
          '2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca',
      }),
    );

    fireEvent.click(screen.getByText('金色冲击'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        templateCode: 'gold_impact',
        text: fancyText.text,
        startMs: fancyText.startMs,
        endMs: fancyText.endMs,
      }),
    );

    fireEvent.change(screen.getByRole('textbox', { name: '文字颜色' }), {
      target: { value: '#ffffff' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '颜色必须为 #RRGGBBAA 大写格式',
    );
  });

  it('blocks edits when the fixed registered font does not match the element', () => {
    const onChange = vi.fn();
    render(
      <FancyTextInspector
        element={fancyText}
        fontStatus={{ status: 'font-invalid', reason: 'font-mismatch' }}
        onChange={onChange}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      '字体不可用，已阻止保存和合成。',
    );
    fireEvent.click(screen.getByText('金色冲击'));
    expect(onChange).not.toHaveBeenCalled();
  });

  it('blocks edits until the fixed fonts have a verified ready status', () => {
    const onChange = vi.fn();
    render(<FancyTextInspector element={fancyText} onChange={onChange} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('radio')[1]);
    expect(onChange).not.toHaveBeenCalled();
  });

  it('keeps preview dragging inside the normalized canvas transform', () => {
    const onTransformChange = vi.fn();
    render(
      <FancyTextOverlay
        element={fancyText}
        onTransformChange={onTransformChange}
      />,
    );
    const overlay = screen.getByLabelText('花字预览');
    vi.spyOn(overlay, 'getBoundingClientRect').mockReturnValue({
      bottom: 200,
      height: 200,
      left: 0,
      right: 100,
      toJSON: () => ({}),
      top: 0,
      width: 100,
      x: 0,
      y: 0,
    });

    fireEvent.pointerUp(overlay, { clientX: 100, clientY: 200 });
    expect(onTransformChange).toHaveBeenCalledWith(
      expect.objectContaining({ xRatio: 0.6, yRatio: 0.8 }),
    );
  });
});
