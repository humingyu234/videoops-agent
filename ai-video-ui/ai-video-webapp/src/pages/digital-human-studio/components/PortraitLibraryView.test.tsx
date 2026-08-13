import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { Modal } from 'antd';
import { act } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { portraitApi } from '@/services/ai-video/portrait/api';
import type { Portrait } from '@/services/ai-video/portrait/types';

import PortraitLibraryView from './PortraitLibraryView';

vi.mock('@/services/ai-video/portrait/api', () => ({
  portraitApi: {
    list: vi.fn().mockResolvedValue({ rows: [], total: 0 }),
    detail: vi.fn(),
    upload: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}));

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe('PortraitLibraryView primary actions', () => {
  it('opens avatar space with the selected portrait already loaded', async () => {
    const portrait: Portrait = {
      portraitId: 'portrait-1',
      name: '女01',
      gender: 'female',
      sceneTags: [],
      availabilityStatus: 'ready',
      previewUrl: 'http://127.0.0.1:9000/portrait-1.png',
      fileFormat: 'png',
      width: 1080,
      height: 1440,
      sizeBytes: '1024',
      recordRevision: '1',
      createTime: '2026-08-03 10:00:00',
      updateTime: '2026-08-03 10:00:00',
    };
    vi.mocked(portraitApi.list).mockResolvedValueOnce({
      rows: [portrait],
      total: 1,
    });
    const onOpenSpace = vi.fn();
    render(<PortraitLibraryView onOpenSpace={onOpenSpace} onToast={vi.fn()} />);

    fireEvent.click(
      await screen.findByRole('button', { name: '进入形象空间' }),
    );

    expect(onOpenSpace).toHaveBeenCalledWith({
      kind: 'portrait',
      name: portrait.name,
      portraitId: portrait.portraitId,
    });
  });

  it('opens avatar space from the portrait list toolbar', async () => {
    const onOpenSpace = vi.fn();
    render(<PortraitLibraryView onOpenSpace={onOpenSpace} onToast={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: /形象空间/ }));

    expect(onOpenSpace).toHaveBeenCalledOnce();
  });

  it('keeps both create action labels white', async () => {
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);

    expect(await screen.findByRole('button', { name: /新增形象/ })).toHaveClass(
      'portrait-primary-action',
    );
    expect(
      screen.getByRole('button', { name: '上传第一张人物照片' }),
    ).toHaveClass('portrait-primary-action');
  });

  it('opens the same create dialog from the empty-state action', async () => {
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);

    fireEvent.click(
      await screen.findByRole('button', { name: '上传第一张人物照片' }),
    );

    expect(screen.getByText('新增人物形象')).toBeInTheDocument();
    expect(screen.getByText('点击或拖拽上传一张人物照片')).toBeInTheDocument();
  });

  it('replaces the uploader with an enlargeable preview that can be removed', async () => {
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));

    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).not.toBeNull();
    fireEvent.change(input as HTMLInputElement, {
      target: {
        files: [new File(['image'], 'portrait.png', { type: 'image/png' })],
      },
    });

    await waitFor(() => {
      expect(
        screen.queryByText('点击或拖拽上传一张人物照片'),
      ).not.toBeInTheDocument();
    });
    await screen.findByRole('img', { name: 'portrait.png' });
    fireEvent.click(screen.getByTitle('portrait.png'));
    expect(await screen.findByText('图片预览')).toBeInTheDocument();

    fireEvent.click(screen.getByTitle(/删除文件|Remove file/i));
    await waitFor(() => {
      expect(
        screen.getByText('点击或拖拽上传一张人物照片'),
      ).toBeInTheDocument();
    });
    expect(
      screen.queryByRole('img', { name: 'portrait.png' }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['portrait.webp', 'image/webp'],
    ['portrait.gif', 'image/gif'],
  ])('replaces the uploader with a thumbnail for %s', async (name, type) => {
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));

    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    expect(input).toHaveAttribute(
      'accept',
      '.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif',
    );
    fireEvent.change(input as HTMLInputElement, {
      target: { files: [new File(['image'], name, { type })] },
    });

    expect(await screen.findByRole('img', { name })).toBeInTheDocument();
    expect(
      screen.queryByText('点击或拖拽上传一张人物照片'),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['portrait.svg', 'image/svg+xml'],
    ['portrait.webp', 'image/png'],
  ])('warns and keeps the uploader for invalid %s', async (name, type) => {
    const warning = vi.spyOn(Modal, 'warning').mockImplementation(() => ({
      destroy: vi.fn(),
      update: vi.fn(),
    }));
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));

    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    fireEvent.change(input as HTMLInputElement, {
      target: { files: [new File(['image'], name, { type })] },
    });

    await waitFor(() => {
      expect(warning).toHaveBeenCalledWith({
        title: '文件类型不支持',
        content: '仅支持 JPG、JPEG、PNG、WebP、GIF',
      });
    });
    expect(screen.getByText('点击或拖拽上传一张人物照片')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name })).not.toBeInTheDocument();
    warning.mockRestore();
  });

  it.each([
    'success',
    'failure',
  ] as const)('keeps the second thumbnail when the first read finishes with %s later', async (firstCompletion) => {
    class ControlledFileReader {
      static instances: ControlledFileReader[] = [];
      error: DOMException | null = null;
      onerror: ((event: ProgressEvent<FileReader>) => void) | null = null;
      onload: ((event: ProgressEvent<FileReader>) => void) | null = null;
      readyState = 1;
      result: string | ArrayBuffer | null = null;
      file?: File;

      abort() {}
      readAsArrayBuffer() {}
      readAsBinaryString() {}
      readAsText() {}
      readAsDataURL(file: Blob) {
        this.file = file as File;
        ControlledFileReader.instances.push(this);
      }
      succeed() {
        this.result = `data:${this.file?.type};base64,${this.file?.name}`;
        this.onload?.({ target: this } as unknown as ProgressEvent<FileReader>);
      }
      fail() {
        this.error = new DOMException('read failed');
        this.onerror?.({
          target: this,
        } as unknown as ProgressEvent<FileReader>);
      }
      EMPTY = 0 as const;
      LOADING = 1 as const;
      DONE = 2 as const;
    }

    const error = vi.spyOn(Modal, 'error').mockImplementation(() => ({
      destroy: vi.fn(),
      update: vi.fn(),
    }));
    vi.stubGlobal('FileReader', ControlledFileReader);
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');

    fireEvent.change(input as HTMLInputElement, {
      target: {
        files: [new File(['first'], 'first.png', { type: 'image/png' })],
      },
    });
    await waitFor(() => {
      expect(ControlledFileReader.instances).toHaveLength(1);
    });
    const nextInput =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    fireEvent.change(nextInput as HTMLInputElement, {
      target: {
        files: [new File(['second'], 'second.png', { type: 'image/png' })],
      },
    });
    await waitFor(() => {
      expect(ControlledFileReader.instances).toHaveLength(2);
    });

    await act(async () => {
      ControlledFileReader.instances[1]?.succeed();
      await Promise.resolve();
    });
    expect(
      await screen.findByRole('img', { name: 'second.png' }),
    ).toBeInTheDocument();

    await act(async () => {
      if (firstCompletion === 'success') {
        ControlledFileReader.instances[0]?.succeed();
      } else {
        ControlledFileReader.instances[0]?.fail();
      }
      await Promise.resolve();
    });

    expect(screen.getByRole('img', { name: 'second.png' })).toBeInTheDocument();
    expect(
      screen.queryByRole('img', { name: 'first.png' }),
    ).not.toBeInTheDocument();
    expect(error).not.toHaveBeenCalled();
  });

  it('reuses the uploaded asset and idempotency key when create is retried', async () => {
    vi.spyOn(Modal, 'error').mockImplementation(() => ({
      destroy: vi.fn(),
      update: vi.fn(),
    }));
    vi.mocked(portraitApi.upload).mockResolvedValue({
      assetId: 'asset-1',
      availabilityStatus: 'ready',
    });
    vi.mocked(portraitApi.create)
      .mockRejectedValueOnce(new Error('temporary failure'))
      .mockResolvedValueOnce({} as Portrait);
    render(<PortraitLibraryView onOpenSpace={vi.fn()} onToast={vi.fn()} />);
    fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));
    const input =
      document.querySelector<HTMLInputElement>('input[type="file"]');
    fireEvent.change(input as HTMLInputElement, {
      target: {
        files: [new File(['image'], 'portrait.png', { type: 'image/png' })],
      },
    });
    await screen.findByRole('img', { name: 'portrait.png' });
    fireEvent.change(screen.getByPlaceholderText('例如：亲切女主播'), {
      target: { value: '测试形象' },
    });

    fireEvent.click(screen.getByRole('button', { name: '上传并创建' }));
    await waitFor(() => expect(portraitApi.create).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: '上传并创建' }));
    await waitFor(() => expect(portraitApi.create).toHaveBeenCalledTimes(2));

    expect(portraitApi.upload).toHaveBeenCalledTimes(1);
    const first = vi.mocked(portraitApi.create).mock.calls[0]?.[0];
    const second = vi.mocked(portraitApi.create).mock.calls[1]?.[0];
    expect(second?.assetId).toBe(first?.assetId);
    expect(second?.idempotencyKey).toBe(first?.idempotencyKey);
  });
});
