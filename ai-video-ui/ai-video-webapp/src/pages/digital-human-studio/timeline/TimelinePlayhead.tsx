export default function TimelinePlayhead({
  durationMs,
  positionMs,
}: {
  durationMs: number;
  positionMs: number;
}) {
  const left =
    durationMs > 0
      ? Math.max(0, Math.min(100, (positionMs / durationMs) * 100))
      : 0;
  return <div className="timeline-playhead-v2" style={{ left: `${left}%` }} />;
}
