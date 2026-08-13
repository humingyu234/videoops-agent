import { useEffect, useState } from 'react';
import styles from '../index.module.css';

const SCENES = [
  { id: 'model', label: 'AI 数字人', sceneClass: styles.sceneModel },
  { id: 'live', label: '直播克隆', sceneClass: styles.sceneLive },
  { id: 'voice', label: '语音合成', sceneClass: styles.sceneVoice },
] as const;

export function LoginSceneBackdrop() {
  const [activeIndex, setActiveIndex] = useState(0);
  const [cycleRevision, setCycleRevision] = useState(0);
  const [reducedMotion, setReducedMotion] = useState(false);

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) {
      return undefined;
    }

    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sync = () => setReducedMotion(media.matches);
    sync();
    media.addEventListener('change', sync);

    return () => media.removeEventListener('change', sync);
  }, []);

  useEffect(() => {
    if (reducedMotion) {
      return undefined;
    }

    const timer = window.setInterval(() => {
      setActiveIndex((index) => (index + 1) % SCENES.length);
    }, 6_000);

    return () => window.clearInterval(timer);
  }, [cycleRevision, reducedMotion]);

  return (
    <>
      <div aria-hidden="true" className={styles.scenes}>
        {SCENES.map((scene, index) => (
          <div
            className={`${styles.scene} ${scene.sceneClass} ${
              index === activeIndex ? styles.sceneActive : ''
            }`}
            key={scene.id}
          />
        ))}
      </div>
      <div aria-hidden="true" className={styles.grain} />
      <aside aria-label="创作场景" className={styles.indicators}>
        <div className={styles.dots}>
          {SCENES.map((scene, index) => (
            <button
              aria-label={`切换到${scene.label}场景`}
              aria-pressed={index === activeIndex}
              className={styles.dotTarget}
              key={scene.id}
              onClick={() => {
                setActiveIndex(index);
                setCycleRevision((revision) => revision + 1);
              }}
              type="button"
            >
              <span className={styles.dot} />
            </button>
          ))}
        </div>
        <div className={styles.caption}>
          {SCENES.map((scene, index) => (
            <span
              aria-current={index === activeIndex ? 'true' : undefined}
              key={scene.id}
            >
              {index > 0 && (
                <span aria-hidden="true" className={styles.captionSeparator}>
                  ·
                </span>
              )}
              {scene.label}
            </span>
          ))}
        </div>
      </aside>
    </>
  );
}
