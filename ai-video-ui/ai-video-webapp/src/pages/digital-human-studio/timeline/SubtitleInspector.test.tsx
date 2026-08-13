import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { SubtitleElement } from '@/services/ai-video/creation-timeline/types';
import SubtitleInspector from './SubtitleInspector';

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
  fontVersion: '2.004',
  fontSha256: '2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b',
  fontSizePx: 48,
  color: '#FFFFFFFF',
  backgroundEnabled: true,
  backgroundColor: '#00000080',
  outlineEnabled: true,
  outlineColor: '#000000FF',
  outlineWidthPx: 2,
  safeAreaAnchor: 'lower',
  alignment: 'center',
};

describe('SubtitleInspector', () => {
  it('only emits C0 font, size, color, anchor, and alignment values', () => {
    const onChange = vi.fn();
    render(<SubtitleInspector element={subtitle} onChange={onChange} />);

    fireEvent.click(screen.getByText('思源宋体'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({
        fontCode: 'noto_serif_cjk_sc_regular',
        fontVersion: '2.003',
        fontSha256:
          '2a2eae2628df83556c54018c41e20fa532c1b862c5256ae8b3f23feb918d12ca',
      }),
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: '字体大小' }), {
      target: { value: '121' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '字体大小必须在 12 到 120 之间',
    );

    fireEvent.change(screen.getByRole('textbox', { name: '文字颜色' }), {
      target: { value: '#ffffff' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '颜色必须为 #RRGGBBAA 大写格式',
    );

    fireEvent.click(screen.getByText('上方'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ safeAreaAnchor: 'upper' }),
    );
    fireEvent.click(screen.getByText('左对齐'));
    expect(onChange).toHaveBeenLastCalledWith(
      expect.objectContaining({ alignment: 'left' }),
    );
  });

  it('rejects a mixed font registry triple before emitting a subtitle change', () => {
    const onChange = vi.fn();
    render(
      <SubtitleInspector
        element={{ ...subtitle, fontVersion: '2.003' }}
        onChange={onChange}
      />,
    );

    fireEvent.change(screen.getByRole('textbox', { name: '文字颜色' }), {
      target: { value: '#000000FF' },
    });

    expect(onChange).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('removes disabled background and outline colors instead of retaining hidden fields', () => {
    const onChange = vi.fn();
    render(<SubtitleInspector element={subtitle} onChange={onChange} />);

    fireEvent.click(screen.getByRole('switch', { name: '显示背景' }));
    const withoutBackground = onChange.mock.calls.at(
      -1,
    )?.[0] as SubtitleElement;
    expect(withoutBackground.backgroundEnabled).toBe(false);
    expect(Object.hasOwn(withoutBackground, 'backgroundColor')).toBe(false);

    fireEvent.click(screen.getByRole('switch', { name: '显示描边' }));
    const withoutOutline = onChange.mock.calls.at(-1)?.[0] as SubtitleElement;
    expect(withoutOutline.outlineEnabled).toBe(false);
    expect(Object.hasOwn(withoutOutline, 'outlineColor')).toBe(false);
    expect(withoutOutline.outlineWidthPx).toBe(0);
    expect(Object.keys(withoutOutline)).not.toContain('transform');
  });
});
