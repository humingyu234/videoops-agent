import { useCallback, useEffect, useRef, useState } from 'react';

import { voiceApi } from '@/services/ai-video/voice/api';
import type { VoiceItem } from '../model';
import { clampVoicePercent } from './voiceTimeline';

export interface VoicePlayback {
  play: (voice: VoiceItem, startPercent?: number) => void;
  stop: () => void;
  toggle: (voice: VoiceItem) => void;
  playingVoiceId: string | null;
  progressByVoice: Record<string, number>;
}

const disposeAudio = (audio: HTMLAudioElement | null) => {
  if (!audio) return;
  audio.pause();
  audio.removeAttribute('src');
  audio.load();
};

export const useVoicePlayback = (): VoicePlayback => {
  const [playingVoiceId, setPlayingVoiceId] = useState<string | null>(null);
  const [progressByVoice, setProgressByVoice] = useState<Record<string, number>>(
    {},
  );
  const frameIdRef = useRef<number | undefined>(undefined);
  const generationRef = useRef(0);
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const audioVoiceIdRef = useRef<string | null>(null);
  const desiredStartRef = useRef(0);
  const pendingAccessRef = useRef<{ voiceId: string; generation: number } | null>(null);

  const cancelScheduledFrame = useCallback(() => {
    if (frameIdRef.current !== undefined) {
      cancelAnimationFrame(frameIdRef.current);
      frameIdRef.current = undefined;
    }
  }, []);

  const stop = useCallback(() => {
    generationRef.current += 1;
    cancelScheduledFrame();
    disposeAudio(audioRef.current);
    audioRef.current = null;
    audioVoiceIdRef.current = null;
    pendingAccessRef.current = null;
    setPlayingVoiceId(null);
  }, [cancelScheduledFrame]);

  const play = useCallback(
    (voice: VoiceItem, startPercent = 0) => {
      if ((!Number.isFinite(voice.secs) || voice.secs <= 0) && !voice.recordRevision) {
        stop();
        return;
      }

      const start = clampVoicePercent(startPercent);
      if (voice.recordRevision) {
        setProgressByVoice((current) => ({ ...current, [voice.id]: start }));
        setPlayingVoiceId(voice.id);

        desiredStartRef.current = start;
        if (audioVoiceIdRef.current === voice.id) {
          const currentAudio = audioRef.current;
          if (currentAudio) {
            const duration = Number.isFinite(currentAudio.duration) && currentAudio.duration > 0
              ? currentAudio.duration : voice.secs;
            currentAudio.currentTime = duration * start;
            void currentAudio.play().catch(() => {
              if (audioRef.current === currentAudio) setPlayingVoiceId(null);
            });
          }
          return;
        }

        generationRef.current += 1;
        const generation = generationRef.current;
        cancelScheduledFrame();
        disposeAudio(audioRef.current);
        audioRef.current = null;
        audioVoiceIdRef.current = voice.id;
        pendingAccessRef.current = { voiceId: voice.id, generation };

        const playWithFreshUrl = async (refreshAttempt: number): Promise<void> => {
          try {
            const access = await voiceApi.accessUrl(voice.id);
            if (generationRef.current !== generation || audioVoiceIdRef.current !== voice.id) return;
            pendingAccessRef.current = null;
            const audio = new Audio(access.url);
            audioRef.current = audio;
            const seekToLatestPosition = () => {
              const duration = Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : voice.secs;
              audio.currentTime = duration * desiredStartRef.current;
            };
            const syncProgress = () => {
              if (generationRef.current !== generation || audioRef.current !== audio) return;
              const duration = Number.isFinite(audio.duration) && audio.duration > 0 ? audio.duration : voice.secs;
              if (duration > 0) setProgressByVoice((current) => ({ ...current, [voice.id]: clampVoicePercent(audio.currentTime / duration) }));
            };
            seekToLatestPosition();
            audio.addEventListener('loadedmetadata', () => { seekToLatestPosition(); syncProgress(); }, { once: true });
            audio.addEventListener('timeupdate', syncProgress);
            audio.addEventListener('ended', () => {
              if (generationRef.current === generation && audioRef.current === audio) {
                setProgressByVoice((current) => ({ ...current, [voice.id]: 0 }));
                setPlayingVoiceId(null);
              }
            }, { once: true });
            audio.addEventListener('error', () => {
              if (generationRef.current !== generation || audioRef.current !== audio) return;
              disposeAudio(audio);
              audioRef.current = null;
              if (refreshAttempt === 0) {
                pendingAccessRef.current = { voiceId: voice.id, generation };
                void playWithFreshUrl(1);
              } else {
                audioVoiceIdRef.current = null;
                setPlayingVoiceId(null);
              }
            }, { once: true });
            await audio.play();
          } catch {
            if (generationRef.current === generation && audioVoiceIdRef.current === voice.id) {
              disposeAudio(audioRef.current);
              audioRef.current = null;
              audioVoiceIdRef.current = null;
              pendingAccessRef.current = null;
              setPlayingVoiceId(null);
            }
          }
        };
        void playWithFreshUrl(0);
        return;
      }

      generationRef.current += 1;
      const generation = generationRef.current;
      cancelScheduledFrame();
      disposeAudio(audioRef.current);
      audioRef.current = null;
      audioVoiceIdRef.current = null;
      pendingAccessRef.current = null;
      const startedAt = performance.now();
      setProgressByVoice((current) => ({ ...current, [voice.id]: start }));
      setPlayingVoiceId(voice.id);

      const updateProgress = () => {
        if (generationRef.current !== generation) {
          return;
        }

        const elapsed =
          (performance.now() - startedAt) / 1_000 + voice.secs * start;
        const percent = clampVoicePercent(elapsed / voice.secs);

        if (percent >= 1) {
          frameIdRef.current = undefined;
          setProgressByVoice((current) => ({ ...current, [voice.id]: 0 }));
          setPlayingVoiceId(null);
          return;
        }

        setProgressByVoice((current) => ({ ...current, [voice.id]: percent }));
        frameIdRef.current = requestAnimationFrame(updateProgress);
      };

      frameIdRef.current = requestAnimationFrame(updateProgress);
    },
    [cancelScheduledFrame, stop],
  );

  const toggle = useCallback(
    (voice: VoiceItem) => {
      if (playingVoiceId === voice.id) {
        stop();
        return;
      }
      play(voice, 0);
    },
    [play, playingVoiceId, stop],
  );

  useEffect(
    () => () => {
      generationRef.current += 1;
      cancelScheduledFrame();
      disposeAudio(audioRef.current);
      audioRef.current = null;
      audioVoiceIdRef.current = null;
      pendingAccessRef.current = null;
    },
    [cancelScheduledFrame],
  );

  return {
    play,
    stop,
    toggle,
    playingVoiceId,
    progressByVoice,
  };
};
