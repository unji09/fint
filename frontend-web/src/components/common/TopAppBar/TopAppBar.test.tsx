import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TopAppBar from './TopAppBar';

describe('TopAppBar', () => {
  it('타이틀을 노출한다', () => {
    render(<TopAppBar title="고객사 목록" />);
    expect(screen.getByRole('heading', { name: '고객사 목록' })).toBeInTheDocument();
  });

  it('left 버튼 클릭 시 onLeftClick이 호출된다', async () => {
    const user = userEvent.setup();
    const onLeftClick = vi.fn();
    render(<TopAppBar title="제목" onLeftClick={onLeftClick} />);
    await user.click(screen.getByRole('button', { name: '뒤로' }));
    expect(onLeftClick).toHaveBeenCalled();
  });

  it('rightActions를 렌더링한다', () => {
    render(
      <TopAppBar
        title="제목"
        rightActions={<button type="button">알림</button>}
      />,
    );
    expect(screen.getByRole('button', { name: '알림' })).toBeInTheDocument();
  });

  it('leftIcon=null이면 좌측 버튼이 노출되지 않는다', () => {
    render(<TopAppBar title="제목" leftIcon={null} />);
    expect(screen.queryByRole('button', { name: '뒤로' })).not.toBeInTheDocument();
  });
});
