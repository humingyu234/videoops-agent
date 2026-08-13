import type { FC } from 'react';

export const LoginBrandMark: FC<{ className?: string }> = ({ className }) => (
  <span aria-hidden="true" className={className}>
    <svg aria-hidden="true" fill="none" viewBox="0 0 24 24">
      <circle cx="12" cy="9" r="3.5" stroke="currentColor" strokeWidth="1.8" />
      <path
        d="M5 20c0-3.5 3-6 7-6s7 2.5 7 6"
        stroke="currentColor"
        strokeLinecap="round"
        strokeWidth="1.8"
      />
    </svg>
  </span>
);
