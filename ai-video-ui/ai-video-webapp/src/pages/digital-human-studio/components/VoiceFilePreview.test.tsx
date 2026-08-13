import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import VoiceFilePreview from './VoiceFilePreview';

describe('VoiceFilePreview', () => {
  beforeEach(() => {
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:voice-preview'),
      revokeObjectURL: vi.fn(),
    });
  });

  afterEach(() => vi.unstubAllGlobals());

  it('creates a playable local preview and releases it on unmount', () => {
    const file = new File(['RIFF1234WAVEfmt '], 'sample.wav', { type: 'audio/wav' });
    const view = render(<VoiceFilePreview file={file} />);

    expect(screen.getByLabelText('播放所选声音')).toHaveAttribute('src', 'blob:voice-preview');
    expect(screen.getByText('sample.wav')).toBeVisible();

    view.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:voice-preview');
  });
});
