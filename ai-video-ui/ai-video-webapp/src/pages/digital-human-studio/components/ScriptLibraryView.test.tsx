import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/services/ai-video/core/errors';
import { userScriptApi } from '@/services/ai-video/script/api';
import ScriptLibraryView, {
  pageAfterDeletingLastRow,
} from './ScriptLibraryView';

vi.mock('@/services/ai-video/script/api', () => ({
  userScriptApi: {
    list: vi.fn(),
    detail: vi.fn(),
    version: vi.fn(),
    create: vi.fn(),
    createVersion: vi.fn(),
    remove: vi.fn(),
  },
}));

const api = vi.mocked(userScriptApi);

describe('ScriptLibraryView', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.list.mockResolvedValue({ rows: [], total: 0 });
  });

  it('returns to the previous page after deleting its only row', () => {
    expect(pageAfterDeletingLastRow(2, 1)).toBe(1);
    expect(pageAfterDeletingLastRow(2, 2)).toBe(2);
  });

  it('loads page 1 and offers manual creation without sample data', async () => {
    render(<ScriptLibraryView onToast={vi.fn()} />);
    expect(screen.getByRole('button', { name: /新建文案/ })).toBeVisible();
    expect(screen.queryByText('AI 生成文案')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(api.list).toHaveBeenCalledWith(
        expect.objectContaining({ pageNum: 1, pageSize: 20 }),
      ),
    );
    expect(await screen.findByText('还没有文案')).toBeVisible();
  });

  it('shows a permission state and retries failed lists', async () => {
    api.list
      .mockRejectedValueOnce(new ApiError({ code: 403, msg: '权限不足' }))
      .mockResolvedValueOnce({ rows: [], total: 0 });
    render(<ScriptLibraryView onToast={vi.fn()} />);
    expect(await screen.findByText('暂无文案查看权限')).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }));
    await waitFor(() => expect(api.list).toHaveBeenCalledTimes(2));
  });
});
