import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { Modal } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '@/services/ai-video/core/errors';
import { voiceApi } from '@/services/ai-video/voice/api';
import type { Voice } from '@/services/ai-video/voice/types';
import { VOICES, type VoiceItem } from '../model';
import VoiceLibraryView from './VoiceLibraryView';

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  const mutableVoice = VOICES[0] as VoiceItem;
  delete mutableVoice.recordRevision;
  delete mutableVoice.timelineExact;
  delete mutableVoice.timeline;
});

const apiVoice = (overrides: Partial<Voice> = {}): Voice => ({
  voiceId: VOICES[0].id,
  assetId: '10',
  voiceType: 'origin',
  name: VOICES[0].name,
  gender: 'female',
  tags: [],
  transcriptText: VOICES[0].script,
  durationMillis: VOICES[0].secs * 1000,
  transcriptionStatus: 'ready',
  attemptCount: 1,
  recordRevision: '4',
  createTime: '2026-08-03T00:00:00',
  updateTime: '2026-08-03T00:00:00',
  ...overrides,
});

const renderLibrary = () => {
  const onAddVoice = vi.fn();
  const onToast = vi.fn();
  const view = render(
    <VoiceLibraryView onAddVoice={onAddVoice} onToast={onToast} />,
  );
  return { ...view, onAddVoice, onToast };
};

const deferred = <T,>() => {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
};

const openDelete = (container: HTMLElement, index = 0) => {
  const card = container.querySelectorAll<HTMLElement>('.vcard')[index];
  fireEvent.click(
    within(card).getByRole('button', { name: VOICES[index].name }),
  );
  fireEvent.click(within(card).getByRole('button', { name: '删除声音' }));
};

type DeleteModalConfig = {
  title?: React.ReactNode;
  content?: React.ReactNode;
  okText?: React.ReactNode;
  cancelText?: React.ReactNode;
  okButtonProps?: { danger?: boolean };
  onOk?: () => Promise<void> | void;
  afterClose?: () => void;
};

type DeleteModalUpdate = {
  cancelButtonProps?: { disabled?: boolean };
  keyboard?: boolean;
};

const mockDeleteModal = () => {
  let config: DeleteModalConfig | undefined;
  const destroy = vi.fn();
  const update = vi.fn((_next: DeleteModalUpdate) => undefined);
  const confirm = vi.fn((next: DeleteModalConfig) => {
    config = next;
    return { destroy, update };
  });
  vi.spyOn(Modal, 'useModal').mockReturnValue([{ confirm }, null] as never);
  return {
    confirm,
    destroy,
    update,
    config: () => {
      if (!config) throw new Error('delete modal was not opened');
      return config;
    },
  };
};

describe('VoiceLibraryView', () => {
  it('paginates seven voices into a six-item first page and vs-202 second page', () => {
    const { container } = renderLibrary();
    expect(container.querySelectorAll('.vcard')).toHaveLength(6);
    expect(screen.getByText('共 7 条')).toBeInTheDocument();
    expect(screen.getByText('1-6 / 7')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '2' }));
    expect(container.querySelectorAll('.vcard')).toHaveLength(1);
    expect(
      container.querySelector('[data-od-id="voice-vs-202"]'),
    ).toBeInTheDocument();
    expect(screen.getByText('7-7 / 7')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一页' })).toBeInTheDocument();
  });

  it('combines type, status, and debounced search filters and resets paging', () => {
    vi.useFakeTimers();
    const { container } = renderLibrary();
    fireEvent.click(screen.getByRole('button', { name: '2' }));
    fireEvent.click(screen.getByRole('button', { name: '原声' }));
    fireEvent.click(screen.getByRole('button', { name: '校验中' }));
    expect(screen.queryByText('7-7 / 7')).not.toBeInTheDocument();

    const input = screen.getByPlaceholderText('声音名称');
    fireEvent.change(input, { target: { value: '不存在的声音' } });
    act(() => vi.advanceTimersByTime(249));
    expect(container.querySelectorAll('.vcard')).toHaveLength(1);
    act(() => vi.advanceTimersByTime(1));
    expect(container.querySelectorAll('.vcard')).toHaveLength(0);
    expect(screen.getByText('没有匹配的声音')).toBeInTheDocument();
    expect(screen.getByText('试试调整筛选或上传原声音')).toBeInTheDocument();
  });

  it('keeps a pending debounced search when a filter changes', () => {
    vi.useFakeTimers();
    const { container } = renderLibrary();
    fireEvent.change(screen.getByPlaceholderText('声音名称'), {
      target: { value: '不存在' },
    });
    fireEvent.click(screen.getByRole('button', { name: '原声' }));

    act(() => vi.advanceTimersByTime(249));
    expect(container.querySelectorAll('.vcard')).toHaveLength(3);
    act(() => vi.advanceTimersByTime(1));
    expect(container.querySelectorAll('.vcard')).toHaveLength(0);
    expect(screen.getByText('共 0 条')).toBeInTheDocument();
    expect(screen.getByText('没有匹配的声音')).toBeInTheDocument();
  });

  it('uploads, expands multiple rows, and switches playback between voices', () => {
    const { container, onAddVoice } = renderLibrary();
    fireEvent.click(screen.getByRole('button', { name: '上传原声' }));
    expect(onAddVoice).toHaveBeenCalledTimes(1);

    const rows = container.querySelectorAll<HTMLElement>('.vrow');
    fireEvent.click(rows[0]);
    fireEvent.click(rows[1]);
    expect(container.querySelectorAll('.vcard.expanded')).toHaveLength(2);

    const playButtons = screen.getAllByRole('button', { name: /^播放 / });
    fireEvent.click(playButtons[0]);
    expect(container.querySelectorAll('.vcard.playing')).toHaveLength(1);
    fireEvent.click(playButtons[1]);
    expect(container.querySelectorAll('.vcard.playing')).toHaveLength(1);
    expect(container.querySelectorAll('.vcard')[1]).toHaveClass('playing');
  });

  it('restarts from zero after a stopped marker while seek still uses its position', () => {
    const { container } = renderLibrary();
    fireEvent.click(screen.getAllByRole('button', { name: /^播放 / })[0]);
    const track = screen.getByRole('slider');
    Object.defineProperty(track, 'getBoundingClientRect', {
      value: () => ({ left: 0, width: 100 }),
    });
    fireEvent.pointerDown(track, { clientX: 50, pointerId: 7 });
    fireEvent.click(screen.getByRole('button', { name: /^暂停 / }));
    expect(container.querySelector<HTMLElement>('.vtl-fill')).toHaveStyle({
      width: '50%',
    });

    fireEvent.click(screen.getAllByRole('button', { name: /^播放 / })[0]);
    expect(container.querySelector<HTMLElement>('.vtl-fill')).toHaveStyle({
      width: '0%',
    });
    expect(container.querySelector('.vtl-cur')).toHaveTextContent('0:00');
  });

  it('cancels edits, rejects empty drafts, and saves compacted text', () => {
    const { container, onToast } = renderLibrary();
    const row = container.querySelector<HTMLElement>('.vrow');
    if (!row) throw new Error('voice row not rendered');
    fireEvent.click(row);
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    expect(
      screen.queryByRole('textbox', { name: '编辑示范文案' }),
    ).not.toBeInTheDocument();
    expect(container.querySelector('.vcard')).toHaveClass('expanded');

    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    let textbox = screen.getByRole('textbox', { name: '编辑示范文案' });
    textbox.textContent = '   ';
    fireEvent.input(textbox);
    fireEvent.click(screen.getByRole('button', { name: '保存' }));
    expect(onToast).toHaveBeenLastCalledWith('文案不能为空', 'error');
    expect(
      screen.getByRole('textbox', { name: '编辑示范文案' }),
    ).toBeInTheDocument();

    textbox = screen.getByRole('textbox', { name: '编辑示范文案' });
    textbox.textContent = ' 新的   示范 文案。 ';
    fireEvent.input(textbox);
    fireEvent.click(screen.getByRole('button', { name: '保存' }));
    expect(onToast).toHaveBeenLastCalledWith('文案已保存', 'success');
    expect(screen.getByText(/新的 示范 文案。/)).toBeInTheDocument();
    expect(container.querySelector('.vcard')).toHaveClass('expanded');
  });

  it('stops playback when a playing card is filtered or paged out', () => {
    const { container } = renderLibrary();
    fireEvent.click(screen.getAllByRole('button', { name: /^播放 / })[0]);
    expect(container.querySelector('.vcard.playing')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '公共' }));
    expect(container.querySelector('.vcard.playing')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '全部类型' }));
    fireEvent.click(screen.getAllByRole('button', { name: /^播放 / })[0]);
    fireEvent.click(screen.getByRole('button', { name: '2' }));
    expect(container.querySelector('.vcard.playing')).not.toBeInTheDocument();
  });

  it('warns before resync and only calls the API after confirmation', async () => {
    Object.assign(VOICES[0] as VoiceItem, {
      recordRevision: '3',
      timelineExact: false,
      timeline: [],
    });
    vi.spyOn(voiceApi, 'resync').mockResolvedValue(
      apiVoice({
        transcriptionStatus: 'pending',
        transcriptTimeline: [],
        recordRevision: '4',
      }),
    );
    const { onToast } = renderLibrary();

    fireEvent.click(screen.getByRole('button', { name: '重新同步' }));
    expect(
      await screen.findByText('重新同步会覆盖当前文案，包括人工编辑内容。'),
    ).toBeInTheDocument();
    expect(voiceApi.resync).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
    expect(voiceApi.resync).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: '重新同步' }));
    const confirmButtons = await screen.findAllByRole('button', {
      name: '确认重新同步',
    });
    const confirmButton = confirmButtons.at(-1);
    if (!confirmButton) throw new Error('resync confirmation was not rendered');
    fireEvent.click(confirmButton);

    await waitFor(() =>
      expect(voiceApi.resync).toHaveBeenCalledWith(VOICES[0].id, '3'),
    );
    expect(onToast).toHaveBeenCalledWith('已重新开始同步文案', 'success');
  });

  it('marks exact synchronization unavailable after a manual transcript edit', async () => {
    Object.assign(VOICES[0] as VoiceItem, {
      recordRevision: '3',
      timelineExact: true,
      timeline: [{ word: '原文', start: 0, dur: 1, isPunct: false }],
    });
    vi.spyOn(voiceApi, 'updateTranscript').mockResolvedValue(
      apiVoice({
        transcriptText: '人工修改文案',
        transcriptTimeline: undefined,
        recordRevision: '4',
      }),
    );
    renderLibrary();

    fireEvent.click(screen.getByLabelText(VOICES[0].name));
    fireEvent.click(screen.getByRole('button', { name: '编辑' }));
    const textbox = screen.getByRole('textbox', { name: '编辑示范文案' });
    textbox.textContent = '人工修改文案';
    fireEvent.input(textbox);
    fireEvent.click(screen.getByRole('button', { name: '保存' }));

    expect(
      await screen.findByText('文案未与音频同步，请重新同步'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: '重新同步' }),
    ).toBeInTheDocument();
  });

  it('shows exact delete confirmation and cancel does not call the API', async () => {
    const controlledModal = mockDeleteModal();
    const deleteSpy = vi.spyOn(voiceApi, 'delete').mockResolvedValue(undefined);
    const { container } = renderLibrary();

    openDelete(container);
    expect(controlledModal.config()).toMatchObject({
      title: `删除声音“${VOICES[0].name}”？`,
      content: '删除后无法恢复，音频文件和文案将同时删除。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
    });
    controlledModal.config().afterClose?.();

    expect(deleteSpy).not.toHaveBeenCalled();
  });

  it('locks all delete flows, sends one pending request, then removes the target', async () => {
    const controlledModal = mockDeleteModal();
    const pending = deferred<void>();
    const deleteSpy = vi
      .spyOn(voiceApi, 'delete')
      .mockReturnValue(pending.promise);
    const { container, onToast } = renderLibrary();
    fireEvent.click(screen.getAllByRole('button', { name: /^播放 / })[0]);

    fireEvent.click(
      within(container.querySelectorAll<HTMLElement>('.vcard')[0]).getByRole(
        'button',
        { name: '删除声音' },
      ),
    );
    fireEvent.click(
      within(container.querySelectorAll<HTMLElement>('.vcard')[1]).getByRole(
        'button',
        { name: VOICES[1].name },
      ),
    );
    fireEvent.click(
      within(container.querySelectorAll<HTMLElement>('.vcard')[1]).getByRole(
        'button',
        { name: '删除声音' },
      ),
    );
    expect(controlledModal.confirm).toHaveBeenCalledTimes(1);

    const requests: Array<void | Promise<void>> = [];
    act(() => {
      requests.push(controlledModal.config().onOk?.());
      requests.push(controlledModal.config().onOk?.());
    });
    const request = requests[0] as Promise<void>;
    const duplicateRequest = requests[1] as Promise<void>;
    expect(duplicateRequest).toBe(request);
    let duplicateSettled = false;
    void duplicateRequest?.then(() => {
      duplicateSettled = true;
    });
    await Promise.resolve();
    expect(duplicateSettled).toBe(false);
    expect(deleteSpy).toHaveBeenCalledTimes(1);
    expect(
      within(container.querySelectorAll<HTMLElement>('.vcard')[0]).getByRole(
        'button',
        { name: '删除声音' },
      ),
    ).toBeDisabled();
    await act(async () => {
      pending.resolve(undefined);
      await request;
    });
    controlledModal.config().afterClose?.();

    await waitFor(() =>
      expect(
        container.querySelector('[data-od-id="voice-vs-003"]'),
      ).not.toBeInTheDocument(),
    );
    expect(container.querySelector('.vcard.playing')).not.toBeInTheDocument();
    expect(onToast).toHaveBeenCalledWith('声音已删除', 'success');
  });

  it('keeps every other delete flow locked if the modal closes while the request is pending', async () => {
    const controlledModal = mockDeleteModal();
    const pending = deferred<void>();
    const deleteSpy = vi
      .spyOn(voiceApi, 'delete')
      .mockReturnValue(pending.promise);
    const { container } = renderLibrary();

    openDelete(container);
    let request!: Promise<void>;
    act(() => {
      request = controlledModal.config().onOk?.() as Promise<void>;
    });
    await waitFor(() => expect(deleteSpy).toHaveBeenCalledTimes(1));
    controlledModal.config().afterClose?.();

    openDelete(container, 1);
    expect(controlledModal.confirm).toHaveBeenCalledTimes(1);
    expect(deleteSpy).toHaveBeenCalledTimes(1);

    await act(async () => {
      pending.resolve(undefined);
      await request;
    });
    await waitFor(() =>
      expect(
        container.querySelector('[data-od-id="voice-vs-003"]'),
      ).not.toBeInTheDocument(),
    );
    const expandedCard =
      container.querySelector<HTMLElement>('.vcard.expanded');
    if (!expandedCard)
      throw new Error('the second voice should remain expanded');
    fireEvent.click(
      within(expandedCard).getByRole('button', { name: '删除声音' }),
    );
    expect(controlledModal.confirm).toHaveBeenCalledTimes(2);
  });

  it('disables modal cancellation only while the delete request is pending', async () => {
    const controlledModal = mockDeleteModal();
    const pending = deferred<void>();
    vi.spyOn(voiceApi, 'delete').mockReturnValue(pending.promise);
    const { container } = renderLibrary();

    openDelete(container);
    const request = controlledModal.config().onOk?.() as Promise<void>;
    await waitFor(() => expect(voiceApi.delete).toHaveBeenCalledTimes(1));
    try {
      expect(controlledModal.update).toHaveBeenCalledWith({
        cancelButtonProps: { disabled: true },
        keyboard: false,
      });
    } finally {
      pending.reject(new Error('delete failed'));
      await expect(request).rejects.toThrow('delete failed');
    }
    expect(controlledModal.update).toHaveBeenLastCalledWith({
      cancelButtonProps: { disabled: false },
      keyboard: true,
    });
  });

  it('keeps the voice and reports permission failure once', async () => {
    const controlledModal = mockDeleteModal();
    vi.spyOn(voiceApi, 'delete').mockRejectedValue(
      new ApiError({ code: 403, msg: 'forbidden', status: 403 }),
    );
    const { container, onToast } = renderLibrary();
    openDelete(container);
    await expect(controlledModal.config().onOk?.()).rejects.toBeInstanceOf(
      ApiError,
    );

    await waitFor(() =>
      expect(onToast).toHaveBeenCalledWith('没有删除声音的权限', 'error'),
    );
    expect(onToast).toHaveBeenCalledTimes(1);
    expect(
      container.querySelector('[data-od-id="voice-vs-003"]'),
    ).toBeInTheDocument();
  });

  it.each([
    401, 46129, 46131,
  ])('does not show a page toast for session code %s', async (code) => {
    const controlledModal = mockDeleteModal();
    vi.spyOn(voiceApi, 'delete').mockRejectedValue(
      new ApiError({ code, msg: 'session' }),
    );
    const { container, onToast } = renderLibrary();
    openDelete(container);
    await expect(controlledModal.config().onOk?.()).rejects.toBeInstanceOf(
      ApiError,
    );

    await waitFor(() => expect(voiceApi.delete).toHaveBeenCalled());
    expect(onToast).not.toHaveBeenCalled();
    expect(
      container.querySelector('[data-od-id="voice-vs-003"]'),
    ).toBeInTheDocument();
  });

  it('does not toast aborts and lets a generic failure retry in the same modal', async () => {
    const controlledModal = mockDeleteModal();
    const abort = new DOMException('aborted', 'AbortError');
    const deleteSpy = vi
      .spyOn(voiceApi, 'delete')
      .mockRejectedValueOnce(abort)
      .mockRejectedValueOnce(new Error('server'))
      .mockResolvedValueOnce(undefined);
    const { container, onToast } = renderLibrary();
    openDelete(container);
    await expect(controlledModal.config().onOk?.()).rejects.toBe(abort);
    await waitFor(() => expect(deleteSpy).toHaveBeenCalledTimes(1));
    expect(onToast).not.toHaveBeenCalled();

    await expect(controlledModal.config().onOk?.()).rejects.toThrow('server');
    await waitFor(() =>
      expect(onToast).toHaveBeenCalledWith(
        '声音删除失败，请刷新后重试',
        'error',
      ),
    );
    expect(
      container.querySelector('[data-od-id="voice-vs-003"]'),
    ).toBeInTheDocument();

    await act(async () => {
      await controlledModal.config().onOk?.();
    });
    controlledModal.config().afterClose?.();
    await waitFor(() =>
      expect(
        container.querySelector('[data-od-id="voice-vs-003"]'),
      ).not.toBeInTheDocument(),
    );
    expect(deleteSpy).toHaveBeenCalledTimes(3);
  });

  it('lets the same modal retry after the delete API throws synchronously', async () => {
    const controlledModal = mockDeleteModal();
    const deleteSpy = vi
      .spyOn(voiceApi, 'delete')
      .mockImplementationOnce(() => {
        throw new Error('sync delete failure');
      })
      .mockResolvedValueOnce(undefined);
    const { container } = renderLibrary();
    openDelete(container);

    await expect(controlledModal.config().onOk?.()).rejects.toThrow(
      'sync delete failure',
    );
    await act(async () => {
      await controlledModal.config().onOk?.();
    });

    expect(deleteSpy).toHaveBeenCalledTimes(2);
    expect(
      container.querySelector('[data-od-id="voice-vs-003"]'),
    ).not.toBeInTheDocument();
  });

  it('destroys only its own open delete modal on unmount', async () => {
    const controlledModal = mockDeleteModal();
    vi.spyOn(voiceApi, 'delete').mockResolvedValue(undefined);
    const { container, unmount } = renderLibrary();
    openDelete(container);
    expect(controlledModal.confirm).toHaveBeenCalledTimes(1);

    unmount();

    expect(controlledModal.destroy).toHaveBeenCalledTimes(1);
  });
});
