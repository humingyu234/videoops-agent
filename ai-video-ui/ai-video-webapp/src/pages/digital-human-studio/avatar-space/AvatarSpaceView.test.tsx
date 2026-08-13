import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { portraitApi } from '@/services/ai-video/portrait/api';

import AvatarSpaceView from './AvatarSpaceView';

vi.mock('@/services/ai-video/portrait/api', () => ({
  portraitApi: {
    accessUrl: vi.fn(),
  },
}));

describe('AvatarSpaceView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(portraitApi.accessUrl).mockResolvedValue({
      expiresAt: '2026-08-04T12:00:00',
      contentType: 'image/png',
      url: '/selected-signed.png',
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('starts from a portrait selected in the library with a fresh access URL', async () => {
    render(
      <AvatarSpaceView
        initialAvatar={{
          kind: 'portrait',
          portraitId: 'portrait-1',
          name: '女01',
        }}
        onBack={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(portraitApi.accessUrl).toHaveBeenCalledWith('portrait-1');
    expect(
      await screen.findByRole('img', { name: '上传形象' }),
    ).toHaveAttribute('src', '/selected-signed.png');
    expect(screen.getByText('当前形象：')).toBeVisible();
    expect(
      screen.getByText(/已加载形象「女01」.*现在告诉我你想怎么改吧/),
    ).toBeVisible();
  });

  it('offers a retry when the selected portrait access URL cannot be loaded', async () => {
    vi.mocked(portraitApi.accessUrl)
      .mockRejectedValueOnce(new Error('expired'))
      .mockResolvedValueOnce({
        expiresAt: '2026-08-04T12:00:00',
        contentType: 'image/png',
        url: '/retried.png',
      });

    render(
      <AvatarSpaceView
        initialAvatar={{
          kind: 'portrait',
          portraitId: 'portrait-1',
          name: '女01',
        }}
        onBack={vi.fn()}
        onToast={vi.fn()}
      />,
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '形象图片加载失败',
    );
    fireEvent.click(screen.getByRole('button', { name: '重新加载' }));

    expect(
      await screen.findByRole('img', { name: '上传形象' }),
    ).toHaveAttribute('src', '/retried.png');
    expect(portraitApi.accessUrl).toHaveBeenCalledTimes(2);
  });

  it('renders the reference welcome screen and returns to the portrait list', () => {
    const onBack = vi.fn();
    render(<AvatarSpaceView onBack={onBack} onToast={vi.fn()} />);

    expect(screen.getAllByText('形象空间')).toHaveLength(2);
    expect(
      screen.getByText('上传一张形象，预览对话式修改界面（功能建设中）'),
    ).toBeVisible();
    ['换配饰', '改发型', '换服装', '调风格'].forEach((label) => {
      expect(
        screen.getByRole('button', { name: new RegExp(label) }),
      ).toBeVisible();
    });
    expect(screen.getByRole('textbox', { name: '形象修改描述' })).toBeVisible();
    expect(screen.getByRole('button', { name: '发送修改描述' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: '返回形象列表' }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it('previews an uploaded image and enables conversation', async () => {
    render(<AvatarSpaceView onBack={vi.fn()} onToast={vi.fn()} />);

    const file = new File(['portrait'], 'portrait.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText('选择形象图片'), {
      target: { files: [file] },
    });

    expect(await screen.findByRole('img', { name: '上传形象' })).toBeVisible();
    expect(screen.getByText('原始形象')).toBeVisible();
    expect(screen.getByText('当前形象：')).toBeVisible();

    fireEvent.change(screen.getByRole('textbox', { name: '形象修改描述' }), {
      target: { value: '换成短发' },
    });
    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: '发送修改描述' }),
      ).toBeEnabled();
    });
  });

  it('accepts WebP and GIF files with matching extensions and MIME types', async () => {
    const onToast = vi.fn();
    render(<AvatarSpaceView onBack={vi.fn()} onToast={onToast} />);

    const input = screen.getByLabelText('选择形象图片');
    expect(input).toHaveAttribute(
      'accept',
      '.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif',
    );

    fireEvent.change(input, {
      target: {
        files: [new File(['webp'], 'portrait.webp', { type: 'image/webp' })],
      },
    });
    expect((await screen.findAllByText('portrait')).length).toBeGreaterThan(0);

    fireEvent.change(input, {
      target: {
        files: [new File(['gif'], 'animated.gif', { type: 'image/gif' })],
      },
    });
    expect((await screen.findAllByText('animated')).length).toBeGreaterThan(0);
    expect(onToast).not.toHaveBeenCalled();
  });

  it('rejects unsupported or mismatched images and keeps the current avatar', async () => {
    const onToast = vi.fn();
    render(<AvatarSpaceView onBack={vi.fn()} onToast={onToast} />);

    const input = screen.getByLabelText('选择形象图片');
    fireEvent.change(input, {
      target: {
        files: [new File(['png'], 'current.png', { type: 'image/png' })],
      },
    });
    expect((await screen.findAllByText('current')).length).toBeGreaterThan(0);

    for (const file of [
      new File(['svg'], 'portrait.svg', { type: 'image/svg+xml' }),
      new File(['bmp'], 'portrait.bmp', { type: 'image/bmp' }),
      new File(['jpeg'], 'portrait.png', { type: 'image/jpeg' }),
    ]) {
      fireEvent.change(input, { target: { files: [file] } });
    }

    expect(onToast).toHaveBeenCalledTimes(3);
    expect(onToast).toHaveBeenLastCalledWith(
      expect.stringContaining('仅支持 JPG、JPEG、PNG、WebP、GIF'),
      'error',
    );
    expect(screen.getAllByText('current').length).toBeGreaterThan(0);
  });

  it('drops delayed replies from an image that has already been replaced', () => {
    vi.useFakeTimers();

    class ImmediateFileReader {
      error: DOMException | null = null;
      onerror: ((event: ProgressEvent<FileReader>) => void) | null = null;
      onload: ((event: ProgressEvent<FileReader>) => void) | null = null;
      readyState = 2;
      result: string | ArrayBuffer | null = null;

      abort() {}
      readAsArrayBuffer() {}
      readAsBinaryString() {}
      readAsText() {}
      readAsDataURL(file: Blob) {
        const namedFile = file as File;
        this.result = `data:${namedFile.type};base64,${namedFile.name}`;
        this.onload?.({ target: this } as unknown as ProgressEvent<FileReader>);
      }
      EMPTY = 0 as const;
      LOADING = 1 as const;
      DONE = 2 as const;
    }

    vi.stubGlobal('FileReader', ImmediateFileReader);
    render(<AvatarSpaceView onBack={vi.fn()} onToast={vi.fn()} />);
    const input = screen.getByLabelText('选择形象图片');

    fireEvent.change(input, {
      target: {
        files: [new File(['one'], 'first.png', { type: 'image/png' })],
      },
    });
    fireEvent.change(input, {
      target: {
        files: [new File(['two'], 'second.png', { type: 'image/png' })],
      },
    });
    act(() => vi.runAllTimers());

    expect(screen.queryByText('first')).not.toBeInTheDocument();
    expect(screen.queryByText(/已加载形象「first」/)).not.toBeInTheDocument();
    expect(screen.getAllByText('second')).toHaveLength(2);
    expect(screen.getByText(/已加载形象「second」/)).toBeVisible();
  });

  it('ignores a stale read error after a newer image has loaded', async () => {
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

    const onToast = vi.fn();
    vi.stubGlobal('FileReader', ControlledFileReader);
    render(<AvatarSpaceView onBack={vi.fn()} onToast={onToast} />);
    const input = screen.getByLabelText('选择形象图片');

    fireEvent.change(input, {
      target: {
        files: [new File(['first'], 'first.png', { type: 'image/png' })],
      },
    });
    fireEvent.change(input, {
      target: {
        files: [new File(['second'], 'second.png', { type: 'image/png' })],
      },
    });

    act(() => ControlledFileReader.instances[1]?.succeed());
    expect((await screen.findAllByText('second')).length).toBeGreaterThan(0);
    act(() => ControlledFileReader.instances[0]?.fail());

    expect(screen.getAllByText('second').length).toBeGreaterThan(0);
    expect(onToast).not.toHaveBeenCalledWith(
      '图片读取失败，请重新选择',
      'error',
    );
  });

  it('marks modification as under construction without fake generation or save success', () => {
    vi.useFakeTimers();

    class ImmediateFileReader {
      error: DOMException | null = null;
      onerror: ((event: ProgressEvent<FileReader>) => void) | null = null;
      onload: ((event: ProgressEvent<FileReader>) => void) | null = null;
      readyState = 2;
      result: string | ArrayBuffer | null = 'data:image/png;base64,portrait';
      abort() {}
      readAsArrayBuffer() {}
      readAsBinaryString() {}
      readAsText() {}
      readAsDataURL() {
        this.onload?.({ target: this } as unknown as ProgressEvent<FileReader>);
      }
      EMPTY = 0 as const;
      LOADING = 1 as const;
      DONE = 2 as const;
    }

    const onToast = vi.fn();
    vi.stubGlobal('FileReader', ImmediateFileReader);
    render(<AvatarSpaceView onBack={vi.fn()} onToast={onToast} />);
    fireEvent.change(screen.getByLabelText('选择形象图片'), {
      target: {
        files: [new File(['image'], 'portrait.png', { type: 'image/png' })],
      },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '形象修改描述' }), {
      target: { value: '换成短发' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送修改描述' }));
    act(() => vi.runAllTimers());

    expect(screen.getByText(/形象修改功能建设中/)).toBeVisible();
    expect(
      screen.queryByRole('img', { name: '生成结果' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: /保存到形象/ }),
    ).not.toBeInTheDocument();
    expect(onToast).not.toHaveBeenCalledWith(expect.stringContaining('已保存'));
  });

  it('keeps the enabled send icon white inside the studio shell', () => {
    const stylePath = resolve(
      process.cwd(),
      'src/pages/digital-human-studio/style.css',
    );
    const stylesheet = readFileSync(stylePath, 'utf8');

    expect(stylesheet).toMatch(
      /\.avatar-space-send:not\(:disabled\)\s*\{[^}]*color:\s*#fff;/,
    );
  });
});
