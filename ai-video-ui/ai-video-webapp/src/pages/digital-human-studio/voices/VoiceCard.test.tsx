import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { VOICES } from '../model';
import VoiceCard from './VoiceCard';

const renderCard = (
  overrides: Partial<React.ComponentProps<typeof VoiceCard>> = {},
) => {
  const props: React.ComponentProps<typeof VoiceCard> = {
    voice: VOICES[0],
    expanded: false,
    editing: false,
    playing: false,
    progress: 0,
    draft: VOICES[0].script,
    onToggle: vi.fn(),
    onPlayToggle: vi.fn(),
    onSeek: vi.fn(),
    onEdit: vi.fn(),
    onCancelEdit: vi.fn(),
    onSaveEdit: vi.fn(),
    onDraftChange: vi.fn(),
    ...overrides,
  };
  const view = render(<VoiceCard {...props} />);
  return { ...view, props };
};

describe('VoiceCard', () => {
  it('toggles from the row while isolating the play control', () => {
    const { container, props } = renderCard();
    const row = container.querySelector<HTMLElement>('.vrow');

    expect(container.querySelector('.vcard')).toHaveAttribute(
      'data-od-id',
      `voice-${VOICES[0].id}`,
    );
    expect(row).toHaveAttribute('aria-expanded', 'false');
    if (!row) throw new Error('voice row not rendered');
    fireEvent.click(row);
    fireEvent.keyDown(row, { key: 'Enter' });
    fireEvent.keyDown(row, { key: ' ' });
    expect(props.onToggle).toHaveBeenCalledTimes(3);

    fireEvent.click(
      screen.getByRole('button', { name: `播放 ${VOICES[0].name}` }),
    );
    expect(props.onPlayToggle).toHaveBeenCalledTimes(1);
    expect(props.onToggle).toHaveBeenCalledTimes(3);
  });

  it('isolates Enter and Space keyboard events on the play control', () => {
    const { props } = renderCard();
    const play = screen.getByRole('button', {
      name: `播放 ${VOICES[0].name}`,
    });

    fireEvent.keyDown(play, { key: 'Enter' });
    fireEvent.keyDown(play, { key: ' ' });

    expect(props.onToggle).not.toHaveBeenCalled();
  });

  it('renders expanded playback state, seeks on the timeline, and marks words', () => {
    const { container, props } = renderCard({
      expanded: true,
      playing: true,
      progress: 0.5,
    });
    const card = container.querySelector('.vcard');
    expect(card).toHaveClass('expanded', 'playing');
    expect(
      screen.getByRole('button', { name: `暂停 ${VOICES[0].name}` }),
    ).toBeInTheDocument();
    expect(screen.getByText('0:31')).toBeInTheDocument();

    const track = screen.getByRole('slider');
    Object.defineProperty(track, 'getBoundingClientRect', {
      value: () => ({ left: 10, width: 200 }),
    });
    fireEvent.pointerDown(track, { clientX: 110, pointerId: 4 });
    expect(props.onSeek).toHaveBeenLastCalledWith(0.5);

    const words = container.querySelectorAll<HTMLElement>('.vpword');
    expect(words.length).toBeGreaterThan(2);
    expect(words[0]).not.toHaveClass('now');
    expect(container.querySelectorAll('.vpword.done').length).toBeGreaterThan(
      0,
    );
    expect(container.querySelectorAll('.vpword.now').length).toBeGreaterThan(0);
    fireEvent.click(words[0]);
    fireEvent.keyDown(words[0], { key: 'Enter' });
    expect(props.onSeek).toHaveBeenLastCalledWith(0);
  });

  it('renders the editing textbox and wires draft, cancel, and save actions', () => {
    const { container, props } = renderCard({ expanded: true, editing: true });
    const textbox = screen.getByRole('textbox', { name: '编辑示范文案' });
    expect(textbox).toHaveAttribute('contenteditable', 'true');
    textbox.textContent = '更新后的文案';
    fireEvent.input(textbox);
    expect(props.onDraftChange).toHaveBeenCalledWith('更新后的文案');

    fireEvent.click(screen.getByRole('button', { name: '取消' }));
    fireEvent.click(screen.getByRole('button', { name: '保存' }));
    expect(props.onCancelEdit).toHaveBeenCalledTimes(1);
    expect(props.onSaveEdit).toHaveBeenCalledTimes(1);
    expect(
      container.querySelector('.vptext-bar-lab.editing'),
    ).toHaveTextContent('编辑中…');
  });

  it('shows retry directly on a collapsed failed card', () => {
    const onRetry = vi.fn();
    const { props } = renderCard({
      voice: { ...VOICES[0], status: 'failed', transcriptionStatus: 'failed' },
      onRetry,
    });

    fireEvent.click(screen.getByRole('button', { name: '重新解析' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(props.onToggle).not.toHaveBeenCalled();
  });

  it('seeks with exact Whisper cue timing', () => {
    const onSeek = vi.fn();
    const { container } = renderCard({
      expanded: true,
      voice: {
        ...VOICES[0],
        secs: 10,
        timelineExact: true,
        timeline: [
          { word: '微信', start: 0.12, dur: 0.36, isPunct: false },
          { word: '公众号', start: 0.5, dur: 0.42, isPunct: false },
        ],
      },
      onSeek,
    });

    expect(container.querySelectorAll('.vpword')).toHaveLength(2);
    fireEvent.click(screen.getByRole('button', { name: '微信' }));
    expect(onSeek).toHaveBeenCalledWith(0.012);
  });

  it('renders unsynchronized API text as plain text with a resync action', () => {
    const onResync = vi.fn();
    const { container, props } = renderCard({
      expanded: true,
      voice: {
        ...VOICES[0],
        recordRevision: '3',
        timelineExact: false,
        timeline: [],
      },
      onResync,
    });

    expect(container.querySelectorAll('.vpword')).toHaveLength(0);
    expect(
      screen.getByText('文案未与音频同步，请重新同步'),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新同步' }));
    expect(onResync).toHaveBeenCalledTimes(1);
    expect(props.onToggle).not.toHaveBeenCalled();
  });

  it('deletes an expanded owned voice without triggering other card actions', () => {
    const onDelete = vi.fn();
    const { props } = renderCard({ expanded: true, onDelete });

    fireEvent.click(screen.getByRole('button', { name: '删除声音' }));

    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(props.onToggle).not.toHaveBeenCalled();
    expect(props.onPlayToggle).not.toHaveBeenCalled();
    expect(props.onSeek).not.toHaveBeenCalled();
    expect(props.onEdit).not.toHaveBeenCalled();
  });

  it('does not offer delete for public or editing voices', () => {
    const { rerender, props } = renderCard({
      expanded: true,
      voice: { ...VOICES[0], owner: 'public', type: 'public' },
      onDelete: vi.fn(),
    });
    expect(
      screen.queryByRole('button', { name: '删除声音' }),
    ).not.toBeInTheDocument();

    rerender(<VoiceCard {...props} voice={VOICES[0]} editing />);
    expect(
      screen.queryByRole('button', { name: '删除声音' }),
    ).not.toBeInTheDocument();
  });

  it('offers delete for a non-public voice even when its owner is public', () => {
    renderCard({
      expanded: true,
      voice: { ...VOICES[0], owner: 'public', type: 'origin' },
      onDelete: vi.fn(),
    });

    expect(
      screen.getByRole('button', { name: '删除声音' }),
    ).toBeInTheDocument();
  });

  it('disables delete while the request is pending', () => {
    renderCard({ expanded: true, deleting: true, onDelete: vi.fn() });

    expect(screen.getByRole('button', { name: '删除声音' })).toBeDisabled();
    expect(screen.getByText('删除中…')).toBeInTheDocument();
  });
});
