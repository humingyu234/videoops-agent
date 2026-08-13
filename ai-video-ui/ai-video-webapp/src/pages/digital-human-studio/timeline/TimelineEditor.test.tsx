import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import type { TimelineDocument } from '@/services/ai-video/creation-timeline/types';
import TimelineEditor from './TimelineEditor';

const timeline: TimelineDocument = {
  schemaVersion: 'timeline-1',
  canvas: {
    width: 1080,
    height: 1920,
    frameRate: 30,
    durationMs: 60_000,
    safeMarginRatio: 0.05,
  },
  tracks: [
    {
      trackId: 'subtitle-track',
      trackType: 'subtitle',
      area: 'top',
      order: 1,
      locked: false,
      muted: false,
      elements: [
        {
          elementId: 'subtitle-1',
          elementType: 'subtitle',
          startMs: 0,
          endMs: 3_500,
          zIndex: 0,
          enabled: true,
          locked: false,
          label: '测试字幕',
          sourceTextSnapshot: '测试字幕',
          displayText: '测试字幕',
          sourceStartOffset: 0,
          sourceEndOffset: 4,
          fontCode: 'noto_sans_cjk_sc_regular',
          fontVersion: '1',
          fontSha256: 'sha',
          fontSizePx: 42,
          color: '#ffffff',
          backgroundEnabled: false,
          outlineEnabled: true,
          outlineColor: '#000000',
          outlineWidthPx: 2,
          safeAreaAnchor: 'lower',
          alignment: 'center',
        },
      ],
    },
    {
      trackId: 'image-track',
      trackType: 'image_overlay',
      area: 'top',
      order: 2,
      locked: false,
      muted: false,
      elements: [
        {
          elementId: 'image-1',
          elementType: 'image_overlay',
          startMs: 1_000,
          endMs: 4_000,
          zIndex: 1,
          enabled: true,
          locked: false,
          label: 'image-overlay',
          assetId: '90071992547410001' as never,
          transform: {
            xRatio: 0.1,
            yRatio: 0.1,
            widthRatio: 0.3,
            heightRatio: 0.3,
            rotationDeg: 0,
            opacity: 1,
          },
          fitMode: 'contain',
          crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
          fade: { fadeInMs: 0, fadeOutMs: 0 },
          sourceStartOffset: 0,
          sourceEndOffset: 0,
          adoptedPrompt: null,
          sourceTaskId: null,
        },
      ],
    },
    {
      trackId: 'pip-track',
      trackType: 'pip_video',
      area: 'top',
      order: 3,
      locked: false,
      muted: false,
      elements: [
        {
          elementId: 'pip-1',
          elementType: 'pip_video',
          startMs: 1_000,
          endMs: 4_000,
          zIndex: 2,
          enabled: true,
          locked: false,
          label: 'pip-overlay',
          assetId: '90071992547410002' as never,
          transform: {
            xRatio: 0.55,
            yRatio: 0.55,
            widthRatio: 0.3,
            heightRatio: 0.3,
            rotationDeg: 0,
            opacity: 1,
          },
          fitMode: 'contain',
          crop: { xRatio: 0, yRatio: 0, widthRatio: 1, heightRatio: 1 },
          fade: { fadeInMs: 0, fadeOutMs: 0 },
          sourceDurationMs: 2_000,
          sourceStartMs: 0,
          loopWhenOverflow: true,
          audioEnabled: false,
        },
      ],
    },
    {
      trackId: 'fancy-track',
      trackType: 'fancy_text',
      area: 'top',
      order: 4,
      locked: false,
      muted: false,
      elements: [
        {
          elementId: 'fancy-1',
          elementType: 'fancy_text',
          startMs: 1_000,
          endMs: 4_000,
          zIndex: 3,
          enabled: true,
          locked: false,
          label: 'fancy-overlay',
          text: 'Fancy',
          templateCode: 'keyword_pop',
          fontCode: 'noto_sans_cjk_sc_regular',
          fontVersion: '1',
          fontSha256: 'sha',
          color: '#ffffff',
          accentColor: '#000000',
          transform: {
            xRatio: 0.2,
            yRatio: 0.2,
            widthRatio: 0.3,
            heightRatio: 0.2,
            rotationDeg: 0,
            opacity: 1,
          },
          animationIntensity: 'normal',
          enterDurationMs: 120,
          exitDurationMs: 120,
          suggestionTaskId: null,
          suggestionReason: null,
        },
      ],
    },
    {
      trackId: 'main-track',
      trackType: 'main_video',
      area: 'center',
      order: 0,
      locked: true,
      muted: false,
      elements: [
        {
          elementId: 'main-1',
          elementType: 'main_video',
          startMs: 0,
          endMs: 60_000,
          zIndex: 0,
          enabled: true,
          locked: true,
          label: 'main-video',
          assetId: '90071992547410003' as never,
          sourceDurationMs: 60_000,
          sourceStartMs: 0,
          fitMode: 'cover',
        },
      ],
    },
    {
      trackId: 'audio-track',
      trackType: 'primary_audio',
      area: 'bottom',
      order: 0,
      locked: true,
      muted: false,
      elements: [],
    },
    {
      trackId: 'sfx-track',
      trackType: 'sound_effect',
      area: 'bottom',
      order: 1,
      locked: false,
      muted: false,
      elements: [],
    },
  ],
};

describe('TimelineEditor', () => {
  it('places preview, add controls, tracks, and inspector in the confirmed editor order', () => {
    render(<TimelineEditor timeline={timeline} />);

    const preview = screen.getByRole('region', { name: '画面预览' });
    const addBar = screen.getByRole('toolbar', { name: '添加时间轴元素' });
    const tracks = screen.getByRole('region', { name: '时间轴轨道' });
    const inspector = screen.getByRole('complementary', { name: '元素信息' });

    expect(
      preview.compareDocumentPosition(addBar) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      addBar.compareDocumentPosition(tracks) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(screen.getByRole('button', { name: '添加图片' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加画中画' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加字幕' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加花字' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加背景音乐' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加音效' })).toBeEnabled();
    expect(screen.getByRole('button', { name: '添加特效' })).toBeEnabled();
    expect(inspector).toBeInTheDocument();
  });

  it('keeps visual tracks above the fixed main video and audio tracks below it', () => {
    render(<TimelineEditor timeline={timeline} />);

    expect(
      screen
        .getAllByTestId('timeline-track')
        .map((track) => track.dataset.trackType),
    ).toEqual([
      'subtitle',
      'image_overlay',
      'pip_video',
      'fancy_text',
      'main_video',
      'primary_audio',
      'sound_effect',
    ]);
  });

  it('synchronizes timeline selection with the inspector and presents canvas information when unselected', () => {
    const onSelect = vi.fn();
    function Harness() {
      const [selectedElementId, setSelectedElementId] = useState<string>();
      const select = (elementId?: string) => {
        onSelect(elementId);
        setSelectedElementId(elementId);
      };
      return (
        <TimelineEditor
          selectedElementId={selectedElementId}
          timeline={timeline}
          onSelect={select}
        />
      );
    }

    render(<Harness />);

    expect(screen.getByText('项目画布')).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', { name: '选择时间轴片段 测试字幕' }),
    );
    expect(onSelect).toHaveBeenCalledWith('subtitle-1');
    expect(screen.getByText('字幕 · 测试字幕')).toBeInTheDocument();
    fireEvent.click(
      screen.getByRole('button', { name: '选择预览元素 测试字幕' }),
    );
    expect(onSelect).toHaveBeenLastCalledWith('subtitle-1');
  });

  it('revokes controlled Blob URLs when preview media changes or unmounts', () => {
    const createObjectUrl = vi
      .fn()
      .mockReturnValueOnce('blob:preview-1')
      .mockReturnValueOnce('blob:preview-2');
    const revokeObjectUrl = vi.fn();
    vi.stubGlobal('URL', {
      createObjectURL: createObjectUrl,
      revokeObjectURL: revokeObjectUrl,
    });
    const first = new Blob(['first'], { type: 'video/mp4' });
    const second = new Blob(['second'], { type: 'video/mp4' });

    const { rerender, unmount } = render(
      <TimelineEditor previewVideoBlob={first} timeline={timeline} />,
    );
    expect(createObjectUrl).toHaveBeenCalledWith(first);
    rerender(<TimelineEditor previewVideoBlob={second} timeline={timeline} />);
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:preview-1');
    unmount();
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:preview-2');
    vi.unstubAllGlobals();
  });

  it('uses the main video clock for a movable playhead and commits every clip edit through one reducer action', async () => {
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    const play = vi
      .spyOn(HTMLMediaElement.prototype, 'play')
      .mockResolvedValue(undefined);
    const onAction = vi.fn();
    const { container } = render(
      <TimelineEditor
        previewVideoUrl="https://media.example/main.mp4"
        timeline={timeline}
        onAction={onAction}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /播放预览/ }));
    await waitFor(() => expect(play).toHaveBeenCalledOnce());

    const video = screen.getByLabelText('创作预览视频') as HTMLVideoElement;
    video.currentTime = 2.5;
    fireEvent.timeUpdate(video);
    await waitFor(() =>
      expect(
        (container.querySelector('.timeline-playhead-v2') as HTMLElement)
          .style.left,
      ).not.toBe('0%'),
    );

    const clip = screen.getByRole('button', {
      name: /时间轴片段.*image-overlay/,
    });
    expect(clip).toHaveClass('timeline-clip-v3');
    fireEvent.pointerDown(clip, { clientX: 0, pointerId: 1 });
    fireEvent.pointerMove(clip, { clientX: 20, pointerId: 1 });
    expect(onAction).not.toHaveBeenCalled();
    fireEvent.pointerUp(clip, { clientX: 20, pointerId: 1 });
    expect(onAction).toHaveBeenCalledTimes(1);
    expect(onAction).toHaveBeenLastCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_200, endMs: 4_200 },
    });

    const startHandle = clip.querySelector(
      '.timeline-clip-v3__handle--start',
    ) as HTMLElement;
    fireEvent.pointerDown(startHandle, { clientX: 0, pointerId: 2 });
    fireEvent.pointerMove(startHandle, { clientX: 10, pointerId: 2 });
    fireEvent.pointerUp(startHandle, { clientX: 10, pointerId: 2 });
    expect(onAction).toHaveBeenCalledTimes(2);
    expect(onAction).toHaveBeenLastCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_100, endMs: 4_000 },
    });

    const endHandle = clip.querySelector(
      '.timeline-clip-v3__handle--end',
    ) as HTMLElement;
    fireEvent.pointerDown(endHandle, { clientX: 0, pointerId: 3 });
    fireEvent.pointerMove(endHandle, { clientX: 10, pointerId: 3 });
    fireEvent.pointerUp(endHandle, { clientX: 10, pointerId: 3 });
    expect(onAction).toHaveBeenCalledTimes(3);
    expect(onAction).toHaveBeenLastCalledWith({
      type: 'elementPatched',
      elementId: 'image-1',
      patch: { startMs: 1_000, endMs: 4_100 },
    });
  });

  it('renders active preview overlays only in range and sends preview dragging through the reducer once', async () => {
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: vi.fn(),
    });
    const onAction = vi.fn();
    render(
      <TimelineEditor
        previewVideoUrl="https://media.example/main.mp4"
        timeline={timeline}
        onAction={onAction}
      />,
    );

    const video = screen.getByLabelText('创作预览视频') as HTMLVideoElement;
    video.currentTime = 2;
    fireEvent.timeUpdate(video);

    const image = await screen.findByTestId('timeline-preview-element-image-1');
    expect(screen.getByTestId('timeline-preview-element-pip-1')).toBeVisible();
    expect(
      screen.getByTestId('timeline-preview-subtitle-subtitle-1'),
    ).toBeVisible();
    expect(screen.getByTestId('timeline-preview-element-fancy-1')).toBeVisible();
    vi.spyOn(image, 'getBoundingClientRect').mockReturnValue({
      bottom: 1_000,
      height: 1_000,
      left: 0,
      right: 1_000,
      top: 0,
      width: 1_000,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    });
    fireEvent.pointerDown(image, { clientX: 100, clientY: 100, pointerId: 4 });
    fireEvent.pointerMove(image, { clientX: 300, clientY: 400, pointerId: 4 });
    expect(onAction).not.toHaveBeenCalled();
    fireEvent.pointerUp(image, { clientX: 300, clientY: 400, pointerId: 4 });
    expect(onAction).toHaveBeenCalledTimes(1);
    expect(onAction).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'elementPatched',
        elementId: 'image-1',
        patch: {
          transform: expect.objectContaining({
            xRatio: 0.30000000000000004,
            yRatio: 0.4,
          }),
        },
      }),
    );

    video.currentTime = 5;
    fireEvent.timeUpdate(video);
    await waitFor(() => {
      expect(screen.queryByTestId('timeline-preview-element-image-1')).toBeNull();
      expect(screen.queryByTestId('timeline-preview-element-pip-1')).toBeNull();
      expect(
        screen.queryByTestId('timeline-preview-subtitle-subtitle-1'),
      ).toBeNull();
      expect(screen.queryByTestId('timeline-preview-element-fancy-1')).toBeNull();
    });
  });

  it.each([
    ['loading', '正在加载时间轴'],
    ['empty', '暂无可编辑时间轴'],
    ['error', '时间轴加载失败'],
    ['forbidden', '无权访问此创作项目'],
    ['asset-invalid', '素材已失效，无法合成'],
  ] as const)(
    'renders the %s state without pretending the editor is ready',
    (status, message) => {
      render(<TimelineEditor timeline={timeline} status={status} />);
      expect(screen.getByText(message)).toBeInTheDocument();
    },
  );
});
