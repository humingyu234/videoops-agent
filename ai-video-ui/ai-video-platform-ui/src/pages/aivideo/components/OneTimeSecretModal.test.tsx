import { useState } from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import OneTimeSecretModal from './OneTimeSecretModal';

const TEST_ONLY_ONE_TIME_VALUE = '仅测试用一次性值';

function OneTimeSecretModalHarness() {
  const [value, setValue] = useState<string | undefined>(TEST_ONLY_ONE_TIME_VALUE);
  const [showModal, setShowModal] = useState(true);

  return (
    <>
      <button type="button" onClick={() => setShowModal(true)}>
        重新打开
      </button>
      {showModal ? (
        <OneTimeSecretModal
          label="测试标签"
          title="测试标题"
          value={value}
          onClose={() => {
            setValue(undefined);
            setShowModal(false);
          }}
        />
      ) : null}
    </>
  );
}

describe('一次性秘密提示框', () => {
  it('关闭后不再显示一次性值，重新打开也不会恢复旧值', () => {
    render(<OneTimeSecretModalHarness />);

    fireEvent.click(screen.getByRole('button', { name: /close/i }));

    expect(screen.queryByDisplayValue(TEST_ONLY_ONE_TIME_VALUE)).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '重新打开' }));

    expect(screen.queryByDisplayValue(TEST_ONLY_ONE_TIME_VALUE)).not.toBeInTheDocument();
  });
});
