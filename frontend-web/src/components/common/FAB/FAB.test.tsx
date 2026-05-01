import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import FAB from './FAB';

describe('FAB', () => {
  it('aria-label이 button 접근 이름이 된다', () => {
    render(<FAB ariaLabel="고객사 추가" />);
    expect(screen.getByRole('button', { name: '고객사 추가' })).toBeInTheDocument();
  });

  it('기본 아이콘으로 SVG가 렌더링된다', () => {
    const { container } = render(<FAB ariaLabel="추가" />);
    const button = screen.getByRole('button', { name: '추가' });
    expect(button).toBeInTheDocument();
    expect(container.querySelector('svg')).toBeInTheDocument();
  });

  it('icon prop으로 아이콘을 교체할 수 있다', () => {
    render(<FAB ariaLabel="검색" icon={<span data-testid="custom-icon" />} />);
    expect(screen.getByTestId('custom-icon')).toBeInTheDocument();
  });

  it('클릭 시 onClick이 호출된다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<FAB ariaLabel="추가" onClick={onClick} />);
    await user.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalled();
  });
});
