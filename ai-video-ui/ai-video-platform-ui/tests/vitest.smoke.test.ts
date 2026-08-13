import { expect, it } from 'vitest';

it('provides happy-dom and jest-dom matchers for platform tests', () => {
  const element = document.createElement('button');
  element.textContent = '平台测试环境';
  document.body.append(element);

  try {
    expect(element).toBeInTheDocument();
    expect(element).toHaveTextContent('平台测试环境');
  } finally {
    element.remove();
  }
});
