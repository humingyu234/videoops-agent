import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import LibraryView from './LibraryView';

vi.mock('./ScriptLibraryView', () => ({
  default: () => <button type="button">新建文案</button>,
}));

const props = {
  onDetail: vi.fn(),
  onAddAvatar: vi.fn(),
  onOpenAvatarSpace: vi.fn(),
  onAddVoice: vi.fn(),
  onNavigateCreate: vi.fn(),
  onToast: vi.fn(),
};

describe('LibraryView routing', () => {
  it('renders the expandable voice library without opening asset details', () => {
    props.onDetail.mockClear();
    const { container } = render(<LibraryView route="voices" {...props} />);
    const row = container.querySelector<HTMLElement>('.vrow');
    if (!row) throw new Error('voice row not rendered');
    fireEvent.click(row);
    expect(screen.getByText('示范文案')).toBeInTheDocument();
    expect(props.onDetail).not.toHaveBeenCalled();
  });

  it('routes scripts to the real personal library without sample cards', () => {
    const { container } = render(<LibraryView route="scripts" {...props} />);
    expect(screen.getByRole('button', { name: '新建文案' })).toBeVisible();
    expect(container.querySelector('.script-card')).not.toBeInTheDocument();
  });
});
