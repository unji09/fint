import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import BottomNav from './BottomNav';

const items = [
  { key: 'home', label: '대시보드', icon: <span /> },
  { key: 'customers', label: '고객', icon: <span /> },
  { key: 'schedule', label: '일정', icon: <span /> },
];

describe('BottomNav', () => {
  it('모든 탭 라벨을 렌더링한다', () => {
    render(<BottomNav items={items} activeKey="home" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /대시보드/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /고객/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /일정/ })).toBeInTheDocument();
  });

  it('activeKey 탭에 aria-current=page를 둔다', () => {
    render(<BottomNav items={items} activeKey="customers" onChange={() => {}} />);
    expect(screen.getByRole('button', { name: /고객/ })).toHaveAttribute(
      'aria-current',
      'page',
    );
  });

  it('탭 클릭 시 onChange가 해당 key로 호출된다', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<BottomNav items={items} activeKey="home" onChange={onChange} />);
    await user.click(screen.getByRole('button', { name: /일정/ }));
    expect(onChange).toHaveBeenCalledWith('schedule');
  });
});
