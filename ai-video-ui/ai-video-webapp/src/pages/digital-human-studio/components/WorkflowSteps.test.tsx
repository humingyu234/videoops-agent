import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import WorkflowSteps from './WorkflowSteps';

describe('WorkflowSteps', () => {
  it('blocks navigating to the next unfinished step', () => {
    const onChange = vi.fn();
    const onBlocked = vi.fn();
    render(
      <WorkflowSteps current={0} onBlocked={onBlocked} onChange={onChange} />,
    );

    fireEvent.click(screen.getByRole('button', { name: /确认文案/ }));

    expect(onChange).not.toHaveBeenCalled();
    expect(onBlocked).toHaveBeenCalledTimes(1);
  });
});
