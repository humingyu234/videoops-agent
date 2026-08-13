import styles from '../index.module.css';

const QR_SIZE = 156;
const MODULES = 25;
const CELL = QR_SIZE / MODULES;

function pseudoRandom(seed: number) {
  const value = Math.sin(seed * 9301 + 49297) * 233280;
  return value - Math.floor(value);
}

function isFinder(row: number, column: number) {
  return (
    (row < 7 && column < 7) ||
    (row < 7 && column >= MODULES - 7) ||
    (row >= MODULES - 7 && column < 7)
  );
}

const DATA_MODULES = Array.from({ length: MODULES * MODULES }, (_, index) => ({
  column: index % MODULES,
  row: Math.floor(index / MODULES),
})).filter(
  ({ column, row }) =>
    !isFinder(row, column) && pseudoRandom(row * MODULES + column + 17) > 0.52,
);

function Finder({ column, row }: { column: number; row: number }) {
  const x = column * CELL;
  const y = row * CELL;

  return (
    <g>
      <rect
        fill="#1d1d1f"
        height={CELL * 7}
        rx={CELL * 1.4}
        width={CELL * 7}
        x={x}
        y={y}
      />
      <rect
        fill="#fff"
        height={CELL * 5}
        rx={CELL}
        width={CELL * 5}
        x={x + CELL}
        y={y + CELL}
      />
      <rect
        fill="#0071e3"
        height={CELL * 3}
        rx={CELL * 0.7}
        width={CELL * 3}
        x={x + CELL * 2}
        y={y + CELL * 2}
      />
    </g>
  );
}

function Alignment() {
  const x = (MODULES - 9) * CELL;
  const y = (MODULES - 9) * CELL;

  return (
    <g>
      <rect
        fill="#1d1d1f"
        height={CELL * 5}
        rx={CELL}
        width={CELL * 5}
        x={x}
        y={y}
      />
      <rect
        fill="#fff"
        height={CELL * 3}
        rx={CELL * 0.7}
        width={CELL * 3}
        x={x + CELL}
        y={y + CELL}
      />
      <rect
        fill="#0071e3"
        height={CELL}
        rx={CELL * 0.3}
        width={CELL}
        x={x + CELL * 2}
        y={y + CELL * 2}
      />
    </g>
  );
}

export function WechatQrConstructionPanel() {
  return (
    <div className={styles.qrPanel}>
      <div className={styles.qrFrame}>
        <svg
          aria-hidden="true"
          className={styles.qrPattern}
          data-qr-placeholder
          viewBox={`0 0 ${QR_SIZE} ${QR_SIZE}`}
        >
          <rect fill="#fff" height={QR_SIZE} width={QR_SIZE} />
          {DATA_MODULES.map(({ column, row }) => (
            <circle
              cx={(column + 0.5) * CELL}
              cy={(row + 0.5) * CELL}
              fill="#1d1d1f"
              key={`${row}-${column}`}
              r={CELL * 0.42}
            />
          ))}
          <Finder column={0} row={0} />
          <Finder column={MODULES - 7} row={0} />
          <Finder column={0} row={MODULES - 7} />
          <Alignment />
        </svg>
        <div
          aria-label="微信扫码登录建设中"
          className={styles.qrConstruction}
          role="status"
        >
          建设中
        </div>
      </div>
      <p className={styles.qrTip}>
        请使用 <strong>微信</strong> 扫描二维码登录
      </p>
    </div>
  );
}
