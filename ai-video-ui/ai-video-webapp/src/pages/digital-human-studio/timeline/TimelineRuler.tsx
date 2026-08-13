export default function TimelineRuler({ durationMs }: { durationMs: number }) {
  const seconds = Math.max(1, Math.ceil(durationMs / 1000));
  const tickCount = Math.min(6, seconds);
  const ticks = Array.from({ length: tickCount + 1 }, (_, index) =>
    Math.round((seconds * index) / tickCount),
  );

  return (
    <section aria-label="时间刻度" className="timeline-ruler-v2">
      {ticks.map((second) => (
        <span key={second} style={{ left: `${(second / seconds) * 100}%` }}>
          00:{String(second).padStart(2, '0')}
        </span>
      ))}
    </section>
  );
}
