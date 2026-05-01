import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Card from './Card';

describe('Card', () => {
  it('children을 렌더링한다', () => {
    render(<Card>본문</Card>);
    expect(screen.getByText('본문')).toBeInTheDocument();
  });

  it('header / footer 슬롯을 렌더링한다', () => {
    render(
      <Card header={<h2>헤더</h2>} footer={<button>저장</button>}>
        본문
      </Card>,
    );
    expect(screen.getByText('헤더')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
  });

  it('clickable=true일 때 button 역할이 되고 클릭 핸들러가 동작한다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Card clickable onClick={onClick}>
        클릭 가능
      </Card>,
    );
    const card = screen.getByRole('button', { name: /클릭 가능/ });
    await user.click(card);
    expect(onClick).toHaveBeenCalled();
  });

  it('padding=lg 클래스가 적용된다', () => {
    const { container } = render(<Card padding="lg">본문</Card>);
    expect(container.firstChild).toHaveClass(/paddingLg/);
  });
});
