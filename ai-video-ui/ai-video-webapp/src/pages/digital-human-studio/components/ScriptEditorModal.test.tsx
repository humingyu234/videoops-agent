import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import ScriptEditorModal from './ScriptEditorModal';

describe('ScriptEditorModal', () => {
  it('submits title and body once while saving', async () => {
    const onSubmit = vi.fn(() => new Promise<void>(() => undefined));
    render(
      <ScriptEditorModal
        open
        idempotencyKey="intent-1"
        onCancel={vi.fn()}
        onSubmit={onSubmit}
      />,
    );

    fireEvent.change(screen.getByLabelText('标题'), {
      target: { value: '夏季新品' },
    });
    fireEvent.change(screen.getByLabelText('文案正文'), {
      target: { value: '这是一段正文' },
    });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));

    expect(onSubmit).toHaveBeenCalledTimes(1);
    expect(onSubmit).toHaveBeenCalledWith({
      displayTitle: '夏季新品',
      scriptText: '这是一段正文',
      idempotencyKey: 'intent-1',
    });
  });

  it('validates title and body by unicode code points', async () => {
    render(
      <ScriptEditorModal
        open
        idempotencyKey="intent-1"
        onCancel={vi.fn()}
        onSubmit={vi.fn()}
      />,
    );
    fireEvent.change(screen.getByLabelText('标题'), {
      target: { value: '😀'.repeat(101) },
    });
    fireEvent.change(screen.getByLabelText('文案正文'), {
      target: { value: '正文' },
    });
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
    expect(
      await screen.findByText('标题不能超过 100 个字符'),
    ).toBeInTheDocument();
  });
});
