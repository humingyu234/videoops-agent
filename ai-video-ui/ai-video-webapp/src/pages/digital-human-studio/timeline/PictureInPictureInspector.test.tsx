import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { PipVideoElement } from '@/services/ai-video/creation-timeline/types';
import PictureInPictureInspector, {
  positionPipAtCorner,
} from './PictureInPictureInspector';

const pip: PipVideoElement = {
  elementId: 'pip_1',
  elementType: 'pip_video',
  startMs: 1_000,
  endMs: 4_000,
  zIndex: 100,
  enabled: true,
  locked: false,
  label: '演示画中画',
  assetId: '90071992547410002' as never,
  transform: {
    xRatio: 0.63,
    yRatio: 0.08,
    widthRatio: 0.32,
    heightRatio: 0.32,
    rotationDeg: 0,
    opacity: 1,
  },
  fitMode: 'cover',
  crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
  fade: { fadeInMs: 100, fadeOutMs: 100 },
  sourceDurationMs: 2_000,
  sourceStartMs: 0,
  loopWhenOverflow: true,
  audioEnabled: false,
};

describe('PictureInPictureInspector', () => {
  it('maps only the four shortcut corners to normalized positions with independent margins', () => {
    expect(
      positionPipAtCorner(pip.transform, 'bottom-left', 0.05, 0.08),
    ).toMatchObject({ xRatio: 0.05, yRatio: 0.6 });
    expect(
      positionPipAtCorner(pip.transform, 'top-right', 0.1, 0.05),
    ).toMatchObject({ xRatio: 0.58, yRatio: 0.05 });
  });

  it('patches corner, size, opacity, fade, and source start without offering a PiP audio toggle', () => {
    const onPatch = vi.fn();
    render(<PictureInPictureInspector element={pip} onPatch={onPatch} />);

    fireEvent.click(screen.getByText('左下'));
    expect(onPatch).toHaveBeenLastCalledWith({
      transform: expect.objectContaining({ xRatio: 0.05, yRatio: 0.6 }),
    });

    fireEvent.change(screen.getByRole('spinbutton', { name: '画中画尺寸' }), {
      target: { value: '0.4' },
    });
    expect(onPatch).toHaveBeenLastCalledWith({
      transform: expect.objectContaining({ widthRatio: 0.4, heightRatio: 0.4 }),
    });

    fireEvent.change(screen.getByRole('spinbutton', { name: '透明度' }), {
      target: { value: '0.6' },
    });
    expect(onPatch).toHaveBeenLastCalledWith({
      transform: expect.objectContaining({ opacity: 0.6 }),
    });

    fireEvent.change(screen.getByRole('spinbutton', { name: '来源起点' }), {
      target: { value: '500' },
    });
    expect(onPatch).toHaveBeenLastCalledWith({ sourceStartMs: 500 });
    expect(
      screen.getByText('画中画将循环播放，且首版保持静音。'),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('checkbox', { name: /声音/ }),
    ).not.toBeInTheDocument();
  });

  it('rejects source and fade settings that would violate C0 playback or the displayed duration', () => {
    const onPatch = vi.fn();
    render(<PictureInPictureInspector element={pip} onPatch={onPatch} />);

    fireEvent.change(screen.getByRole('spinbutton', { name: '来源起点' }), {
      target: { value: '2000' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '来源起点必须小于素材时长',
    );
    expect(onPatch).not.toHaveBeenCalled();

    fireEvent.change(screen.getByRole('spinbutton', { name: '淡入时长' }), {
      target: { value: '3000' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '淡入和淡出总和不能超过元素时长',
    );
  });
});
