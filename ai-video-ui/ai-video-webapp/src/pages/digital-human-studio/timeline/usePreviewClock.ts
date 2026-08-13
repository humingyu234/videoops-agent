import {
  type RefObject,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

export interface PreviewClockOptions {
  durationMs: number;
  videoRef: RefObject<HTMLVideoElement | null>;
}

function clampPosition(positionMs: number, durationMs: number): number {
  return Math.max(
    0,
    Math.min(durationMs, Number.isFinite(positionMs) ? positionMs : 0),
  );
}

export function usePreviewClock({ durationMs, videoRef }: PreviewClockOptions) {
  const [positionMs, setPositionMs] = useState(0);
  const [playing, setPlaying] = useState(false);
  const frameIdRef = useRef<number | undefined>(undefined);

  const cancelFrame = useCallback(() => {
    if (frameIdRef.current === undefined) return;
    cancelAnimationFrame(frameIdRef.current);
    frameIdRef.current = undefined;
  }, []);

  const syncFromVideo = useCallback(() => {
    const video = videoRef.current;
    if (!video) return;
    setPositionMs(clampPosition(video.currentTime * 1_000, durationMs));
  }, [durationMs, videoRef]);

  const scheduleFrame = useCallback(() => {
    const tick = () => {
      frameIdRef.current = undefined;
      const video = videoRef.current;
      if (!video || video.paused || video.ended || document.hidden) return;
      syncFromVideo();
      frameIdRef.current = requestAnimationFrame(tick);
    };

    const video = videoRef.current;
    if (
      !video ||
      video.paused ||
      video.ended ||
      document.hidden ||
      frameIdRef.current !== undefined
    )
      return;
    frameIdRef.current = requestAnimationFrame(tick);
  }, [syncFromVideo, videoRef]);

  const pause = useCallback(() => {
    const video = videoRef.current;
    cancelFrame();
    video?.pause();
    setPlaying(false);
    syncFromVideo();
  }, [cancelFrame, syncFromVideo, videoRef]);

  const play = useCallback(async () => {
    const video = videoRef.current;
    if (!video || durationMs <= 0) return;
    try {
      await video.play();
      if (videoRef.current !== video || video.ended) return;
      setPlaying(true);
      scheduleFrame();
    } catch {
      cancelFrame();
      setPlaying(false);
    }
  }, [cancelFrame, durationMs, scheduleFrame, videoRef]);

  const seek = useCallback(
    (nextPositionMs: number) => {
      const bounded = clampPosition(nextPositionMs, durationMs);
      const video = videoRef.current;
      if (video) video.currentTime = bounded / 1_000;
      setPositionMs(bounded);
    },
    [durationMs, videoRef],
  );

  const toggle = useCallback(() => {
    if (playing) {
      pause();
      return Promise.resolve();
    }
    return play();
  }, [pause, play, playing]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;

    const onPlay = () => {
      setPlaying(true);
      scheduleFrame();
    };
    const onPause = () => {
      cancelFrame();
      setPlaying(false);
      syncFromVideo();
    };
    const onEnded = () => {
      cancelFrame();
      setPlaying(false);
      setPositionMs(durationMs);
    };
    const onVisibilityChange = () => {
      if (document.hidden) {
        cancelFrame();
      } else if (!video.paused && !video.ended) {
        scheduleFrame();
      }
    };

    video.addEventListener('play', onPlay);
    video.addEventListener('pause', onPause);
    video.addEventListener('seeking', syncFromVideo);
    video.addEventListener('timeupdate', syncFromVideo);
    video.addEventListener('ended', onEnded);
    document.addEventListener('visibilitychange', onVisibilityChange);
    syncFromVideo();

    return () => {
      cancelFrame();
      video.removeEventListener('play', onPlay);
      video.removeEventListener('pause', onPause);
      video.removeEventListener('seeking', syncFromVideo);
      video.removeEventListener('timeupdate', syncFromVideo);
      video.removeEventListener('ended', onEnded);
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [cancelFrame, durationMs, scheduleFrame, syncFromVideo, videoRef]);

  return { positionMs, playing, play, pause, seek, toggle };
}
