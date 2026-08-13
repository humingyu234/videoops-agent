import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ErrorBoundary from './index';

const { mockGetIntl } = vi.hoisted(() => ({
  mockGetIntl: vi.fn(),
}));

vi.mock('@umijs/max', () => ({
  getIntl: mockGetIntl,
}));

function BrokenContent(): never {
  throw new Error('页面渲染失败');
}

describe('ErrorBoundary', () => {
  beforeEach(() => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    mockGetIntl.mockImplementation(() => {
      throw new Error('国际化运行时尚未初始化');
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows the default fallback when the locale runtime is unavailable', () => {
    render(
      <ErrorBoundary>
        <BrokenContent />
      </ErrorBoundary>,
    );

    expect(screen.getByText('Something went wrong')).toBeVisible();
  });
});
