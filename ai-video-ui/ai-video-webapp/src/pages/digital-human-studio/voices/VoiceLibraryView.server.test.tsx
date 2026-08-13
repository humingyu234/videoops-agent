import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from '@testing-library/react';
import { Modal } from 'antd';
import {
  afterAll,
  afterEach,
  beforeAll,
  describe,
  expect,
  it,
  vi,
} from 'vitest';

import type { VoiceApi } from '@/services/ai-video/voice/api';
import type { Voice, VoicePage } from '@/services/ai-video/voice/types';

type VoiceLibraryComponent = typeof import('./VoiceLibraryView').default;
type DeleteModalConfig = {
  onOk?: () => Promise<void> | void;
};

const API_VOICE: Voice = {
  voiceId: 'race-voice',
  assetId: '101',
  voiceType: 'origin',
  name: '竞态声音',
  gender: 'female',
  tags: [],
  transcriptText: '用于验证旧请求不会复活已删除声音。',
  durationMillis: 3_000,
  transcriptionStatus: 'ready',
  attemptCount: 1,
  recordRevision: '1',
  createTime: '2026-08-04T00:00:00',
  updateTime: '2026-08-04T00:00:00',
};

const page = (rows: Voice[]): VoicePage => ({ rows, total: rows.length });

const deferred = <T,>() => {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
};

const mockDeleteModal = () => {
  let config: DeleteModalConfig | undefined;
  const destroy = vi.fn();
  const update = vi.fn();
  const confirm = vi.fn((next: DeleteModalConfig) => {
    config = next;
    return { destroy, update };
  });
  vi.spyOn(Modal, 'useModal').mockReturnValue([{ confirm }, null] as never);
  return {
    confirm,
    destroy,
    config: () => {
      if (!config) throw new Error('delete modal was not opened');
      return config;
    },
  };
};

let VoiceLibraryView: VoiceLibraryComponent;
let voiceApi: VoiceApi;

beforeAll(async () => {
  vi.stubEnv('NODE_ENV', 'production');
  ({ voiceApi } = await import('@/services/ai-video/voice/api'));
  ({ default: VoiceLibraryView } = await import('./VoiceLibraryView'));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

afterAll(() => {
  vi.unstubAllEnvs();
});

describe('VoiceLibraryView production request races', () => {
  it('does not revive a deleted voice when the older list request returns last', async () => {
    const initial = deferred<VoicePage>();
    const refresh = deferred<VoicePage>();
    const listSpy = vi
      .spyOn(voiceApi, 'list')
      .mockReturnValueOnce(initial.promise)
      .mockReturnValueOnce(refresh.promise);
    vi.spyOn(voiceApi, 'delete').mockResolvedValue(undefined);
    const controlledModal = mockDeleteModal();
    const onToast = vi.fn();
    const { container } = render(
      <VoiceLibraryView onAddVoice={vi.fn()} onToast={onToast} />,
    );

    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(1));
    act(() => window.dispatchEvent(new Event('aivideo:voice-changed')));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));
    await act(async () => {
      refresh.resolve(page([API_VOICE]));
      await refresh.promise;
    });

    fireEvent.click(
      await screen.findByRole('button', { name: API_VOICE.name }),
    );
    fireEvent.click(screen.getByRole('button', { name: '删除声音' }));
    await act(async () => {
      await controlledModal.config().onOk?.();
    });
    expect(
      container.querySelector('[data-od-id="voice-race-voice"]'),
    ).not.toBeInTheDocument();

    await act(async () => {
      initial.resolve(page([API_VOICE]));
      await initial.promise;
    });

    expect(
      container.querySelector('[data-od-id="voice-race-voice"]'),
    ).not.toBeInTheDocument();
    expect(onToast).toHaveBeenCalledWith('声音已删除', 'success');
  });

  it.each([
    'resolve',
    'reject',
  ] as const)('ignores a pending list %s after unmount and destroys its delete modal', async (settlement) => {
    const initial = deferred<VoicePage>();
    const pendingRefresh = deferred<VoicePage>();
    const listSpy = vi
      .spyOn(voiceApi, 'list')
      .mockReturnValueOnce(initial.promise)
      .mockReturnValueOnce(pendingRefresh.promise);
    const controlledModal = mockDeleteModal();
    const onToast = vi.fn();
    const { unmount } = render(
      <VoiceLibraryView onAddVoice={vi.fn()} onToast={onToast} />,
    );
    await act(async () => {
      initial.resolve(page([API_VOICE]));
      await initial.promise;
    });
    const card = await screen.findByRole('button', { name: API_VOICE.name });
    fireEvent.click(card);
    const voiceCard = card.closest<HTMLElement>('.vcard');
    if (!voiceCard) throw new Error('voice card was not rendered');
    fireEvent.click(
      within(voiceCard).getByRole('button', {
        name: '删除声音',
      }),
    );
    act(() => window.dispatchEvent(new Event('aivideo:voice-changed')));
    await waitFor(() => expect(listSpy).toHaveBeenCalledTimes(2));

    unmount();
    await act(async () => {
      if (settlement === 'resolve') {
        pendingRefresh.resolve(page([API_VOICE]));
        await pendingRefresh.promise;
      } else {
        pendingRefresh.reject(new Error('late list failure'));
        await pendingRefresh.promise.catch(() => undefined);
      }
    });

    expect(controlledModal.destroy).toHaveBeenCalledTimes(1);
    expect(onToast).not.toHaveBeenCalled();
  });
});
