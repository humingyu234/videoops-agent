import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { portraitApi } from '@/services/ai-video/portrait/api';
import type { Portrait } from '@/services/ai-video/portrait/types';
import { voiceApi } from '@/services/ai-video/voice/api';
import type { Voice } from '@/services/ai-video/voice/types';
import { initialStudioState, type StudioState } from '../model';
import AssetStep from './AssetStep';

vi.mock('@/services/ai-video/portrait/api', () => ({
  portraitApi: {
    list: vi.fn(),
    upload: vi.fn(),
    create: vi.fn(),
  },
}));

vi.mock('@/services/ai-video/voice/api', () => ({
  voiceApi: {
    list: vi.fn(),
    upload: vi.fn(),
    accessUrl: vi.fn(),
  },
}));

vi.mock('../voices/useVoicePlayback', () => ({
  useVoicePlayback: () => ({
    playingVoiceId: undefined,
    progressByVoice: {},
    toggle: vi.fn(),
    play: vi.fn(),
    stop: vi.fn(),
  }),
}));

const portrait: Portrait = {
  portraitId: 'portrait-ready',
  name: '测试形象',
  gender: 'female',
  sceneTags: ['口播'],
  availabilityStatus: 'ready',
  recordRevision: '1',
  createTime: '2026-08-06 10:00:00',
  updateTime: '2026-08-06 10:00:00',
};

const voice: Voice = {
  voiceId: 'voice-origin',
  assetId: 'asset-voice-origin',
  voiceType: 'origin',
  name: '测试原声',
  gender: 'female',
  tags: ['自然'],
  durationMillis: 12_000,
  transcriptionStatus: 'unparsed',
  attemptCount: 0,
  recordRevision: '1',
  createTime: '2026-08-06 10:00:00',
  updateTime: '2026-08-06 10:00:00',
};

const createState = (): StudioState => ({
  ...initialStudioState,
  selectedAvatar: null,
  selectedVoice: null,
});

const renderStep = (onNext = vi.fn()) => {
  function Harness() {
    const [state, setState] = useState<StudioState>(createState());
    return (
      <AssetStep
        state={state}
        update={(patch) => setState((current) => ({ ...current, ...patch }))}
        onAddAvatar={vi.fn()}
        onAddVoice={vi.fn()}
        onFinish={vi.fn()}
        onNext={onNext}
        onPrevious={vi.fn()}
        onToast={vi.fn()}
      />
    );
  }

  render(<Harness />);
  return { onNext };
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(portraitApi.list).mockResolvedValue({ rows: [portrait], total: 1 });
  vi.mocked(voiceApi.list).mockResolvedValue({ rows: [voice], total: 1 });
});

describe('AssetStep', () => {
  it('loads six portraits and six origin voices per page', async () => {
    renderStep();

    await waitFor(() => {
      expect(portraitApi.list).toHaveBeenCalledWith({ pageNum: 1, pageSize: 6 });
      expect(voiceApi.list).toHaveBeenCalledWith({
        voiceType: 'origin',
        pageNum: 1,
        pageSize: 6,
      });
    });
  });

  it('enables the next step after selecting a ready portrait and an origin voice', async () => {
    const { onNext } = renderStep();

    const nextButton = screen.getByRole('button', { name: /下一步/ });
    expect(nextButton).toBeDisabled();

    fireEvent.click(await screen.findByRole('button', { name: '选择形象 测试形象' }));
    fireEvent.click(await screen.findByRole('button', { name: '选择声音 测试原声' }));

    await waitFor(() => expect(nextButton).toBeEnabled());
    fireEvent.click(nextButton);
    expect(onNext).toHaveBeenCalledOnce();
  });

  it('reloads the corresponding list from each refresh button', async () => {
    renderStep();

    await waitFor(() => {
      expect(portraitApi.list).toHaveBeenCalledTimes(1);
      expect(voiceApi.list).toHaveBeenCalledTimes(1);
    });

    const refreshButtons = screen.getAllByRole('button', { name: /刷新/ });
    fireEvent.click(refreshButtons[0]);
    fireEvent.click(refreshButtons[1]);

    await waitFor(() => {
      expect(portraitApi.list).toHaveBeenCalledTimes(2);
      expect(voiceApi.list).toHaveBeenCalledTimes(2);
    });
  });
});
