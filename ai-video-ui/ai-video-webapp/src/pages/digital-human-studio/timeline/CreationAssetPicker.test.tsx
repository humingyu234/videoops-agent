import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import type { CreationAssetsApi } from '@/services/ai-video/creation-assets/api';
import type { CreationAsset } from '@/services/ai-video/creation-assets/types';
import CreationAssetPicker, {
  createImageOverlayElement,
  createPictureInPictureElement,
} from './CreationAssetPicker';
import ImageInspector from './ImageInspector';

function asset(overrides: Partial<CreationAsset> = {}): CreationAsset {
  return {
    assetId: '90071992547410001' as never,
    originalName: 'product.png',
    mimeType: 'image/png',
    sha256: 'a'.repeat(64),
    assetType: 'image',
    usageOrigin: 'upload',
    status: 'ready',
    sizeBytes: '1024',
    durationMs: null,
    width: 1080,
    height: 1920,
    hasVideoStream: false,
    hasAudioStream: false,
    createdAt: '2026-08-08T08:00:00Z',
    ...overrides,
  };
}

function apiStub(
  overrides: Partial<CreationAssetsApi> = {},
): CreationAssetsApi {
  return {
    list: vi.fn().mockResolvedValue({ rows: [asset()], total: 3 }),
    upload: vi.fn().mockResolvedValue(asset()),
    detail: vi.fn(),
    content: vi
      .fn()
      .mockResolvedValue(new Blob(['asset'], { type: 'image/png' })),
    delete: vi.fn(),
    ...overrides,
  };
}

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('CreationAssetPicker', () => {
  it('filters ready assets, pages through the current-user list, and keeps preview selection controlled', async () => {
    const api = apiStub();
    const onSelect = vi.fn();
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:controlled-preview'),
      revokeObjectURL: vi.fn(),
    });
    render(
      <CreationAssetPicker
        allowedAssetTypes={['image', 'video', 'audio']}
        api={api}
        open
        pageSize={2}
        selectedAssetId="90071992547410001"
        usageIntent="image_overlay"
        onSelect={onSelect}
      />,
    );

    expect(await screen.findByText('product.png')).toBeInTheDocument();
    expect(api.list).toHaveBeenCalledWith({
      assetType: 'image',
      pageNum: 1,
      pageSize: 2,
    });
    await waitFor(() => {
      expect(api.content).toHaveBeenCalledWith('90071992547410001');
    });
    expect(screen.getByRole('img', { name: 'product.png' })).toHaveAttribute(
      'src',
      'blob:controlled-preview',
    );

    fireEvent.click(screen.getByText('视频'));
    await waitFor(() => {
      expect(api.list).toHaveBeenLastCalledWith({
        assetType: 'video',
        pageNum: 1,
        pageSize: 2,
      });
    });

    fireEvent.click(screen.getByText('2'));
    await waitFor(() => {
      expect(api.list).toHaveBeenLastCalledWith({
        assetType: 'video',
        pageNum: 2,
        pageSize: 2,
      });
    });

    fireEvent.click(
      screen.getByRole('button', { name: '选择素材 product.png' }),
    );
    expect(onSelect).toHaveBeenCalledWith(asset());
  });

  it('keeps empty, forbidden, invalid, and upload-failure states distinct', async () => {
    const { rerender } = render(
      <CreationAssetPicker
        api={apiStub({
          list: vi.fn().mockResolvedValue({ rows: [], total: 0 }),
        })}
        open
        usageIntent="image_overlay"
      />,
    );
    expect(await screen.findByText('没有可用素材')).toBeInTheDocument();

    rerender(
      <CreationAssetPicker
        api={apiStub({
          list: vi
            .fn()
            .mockRejectedValue(
              new ApiError({ code: 403, msg: 'forbidden', status: 403 }),
            ),
        })}
        open
        usageIntent="image_overlay"
      />,
    );
    expect(await screen.findByText('没有素材访问权限')).toBeInTheDocument();

    rerender(
      <CreationAssetPicker
        api={apiStub({
          list: vi
            .fn()
            .mockRejectedValue(
              new ApiError({ code: 404, msg: 'gone', status: 404 }),
            ),
        })}
        open
        usageIntent="image_overlay"
      />,
    );
    expect(await screen.findByText('素材已失效，无法合成')).toBeInTheDocument();

    const upload = vi.fn().mockRejectedValue(new Error('upload failure'));
    rerender(
      <CreationAssetPicker
        api={apiStub({ upload })}
        open
        usageIntent="image_overlay"
      />,
    );
    await screen.findByText('product.png');
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    fireEvent.change(input as HTMLInputElement, {
      target: {
        files: [new File(['image'], 'new.png', { type: 'image/png' })],
      },
    });
    expect(await screen.findByText('上传素材失败')).toBeInTheDocument();
    expect(upload).toHaveBeenCalledWith(
      expect.any(File),
      expect.objectContaining({ usageIntent: 'image_overlay' }),
    );
  });

  it('creates bounded elements from the playhead and validates image crop and fade edits before patching', () => {
    const imageAsset = asset();
    const imageElement = createImageOverlayElement({
      asset: imageAsset,
      elementId: 'image_1',
      positionMs: 9_500,
      projectDurationMs: 10_000,
    });
    expect(imageElement).toMatchObject({
      startMs: 7_000,
      endMs: 10_000,
      assetId: imageAsset.assetId,
      fitMode: 'contain',
    });

    const videoAsset = asset({
      assetId: '90071992547410002' as never,
      assetType: 'video',
      durationMs: 5_000,
      mimeType: 'video/mp4',
      hasVideoStream: true,
    });
    expect(
      createPictureInPictureElement({
        asset: videoAsset,
        elementId: 'pip_1',
        positionMs: 9_500,
        projectDurationMs: 10_000,
      }),
    ).toMatchObject({
      startMs: 7_000,
      endMs: 10_000,
      sourceDurationMs: 5_000,
      loopWhenOverflow: true,
      audioEnabled: false,
    });
    expect(() =>
      createPictureInPictureElement({
        asset: imageAsset,
        elementId: 'pip_2',
        positionMs: 0,
        projectDurationMs: 10_000,
      }),
    ).toThrow('duration');

    const onPatch = vi.fn();
    render(
      <ImageInspector
        element={{
          ...imageElement,
          crop: { xRatio: 0.1, yRatio: 0.1, widthRatio: 0.5, heightRatio: 0.5 },
          fade: { fadeInMs: 2_000, fadeOutMs: 1_500 },
        }}
        onPatch={onPatch}
      />,
    );
    fireEvent.click(screen.getByText('填充'));
    expect(onPatch).toHaveBeenLastCalledWith({ fitMode: 'cover' });

    fireEvent.change(screen.getByRole('spinbutton', { name: '裁剪左边' }), {
      target: { value: '0.6' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '裁剪框必须完全落在源图内',
    );

    fireEvent.change(screen.getByRole('spinbutton', { name: '淡入时长' }), {
      target: { value: '3000' },
    });
    expect(screen.getByRole('alert')).toHaveTextContent(
      '淡入和淡出总和不能超过元素时长',
    );
  });
});
