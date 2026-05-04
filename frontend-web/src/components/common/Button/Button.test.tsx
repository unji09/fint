import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Button from './Button';

describe('Button', () => {
  it('children을 렌더링한다', () => {
    render(<Button>저장</Button>);
    expect(screen.getByRole('button', { name: '저장' })).toBeInTheDocument();
  });

  it('기본 variant는 primary다', () => {
    render(<Button>저장</Button>);
    expect(screen.getByRole('button')).toHaveClass(/primary/);
  });

  it('variant=danger일 때 danger 클래스를 적용한다', () => {
    render(<Button variant="danger">삭제</Button>);
    expect(screen.getByRole('button')).toHaveClass(/danger/);
  });

  it('variant=sub / ghost 클래스도 정상 적용된다', () => {
    const { rerender } = render(<Button variant="sub">서브</Button>);
    expect(screen.getByRole('button')).toHaveClass(/sub/);

    rerender(<Button variant="ghost">고스트</Button>);
    expect(screen.getByRole('button')).toHaveClass(/ghost/);
  });

  it('size 별 클래스가 적용된다', () => {
    const { rerender } = render(<Button size="sm">sm</Button>);
    expect(screen.getByRole('button')).toHaveClass(/sm/);

    rerender(<Button size="lg">lg</Button>);
    expect(screen.getByRole('button')).toHaveClass(/lg/);
  });

  it('클릭 시 onClick 핸들러가 호출된다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>저장</Button>);
    await user.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('disabled 상태에서 클릭이 무시된다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button onClick={onClick} disabled>
        저장
      </Button>,
    );
    await user.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('loading 상태에서 spinner를 노출하고 aria-busy=true를 둔다', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(
      <Button onClick={onClick} loading>
        저장
      </Button>,
    );
    const button = screen.getByRole('button');
    expect(button).toHaveAttribute('aria-busy', 'true');
    await user.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('iconLeft / iconRight 노드를 렌더링한다', () => {
    render(
      <Button iconLeft={<span data-testid="left" />} iconRight={<span data-testid="right" />}>
        저장
      </Button>,
    );
    expect(screen.getByTestId('left')).toBeInTheDocument();
    expect(screen.getByTestId('right')).toBeInTheDocument();
  });

  it('fullWidth=true일 때 fullWidth 클래스를 적용한다', () => {
    render(<Button fullWidth>full</Button>);
    expect(screen.getByRole('button')).toHaveClass(/fullWidth/);
  });

  it('forwardRef로 ref를 노출한다', () => {
    let buttonRef: HTMLButtonElement | null = null;
    render(
      <Button
        ref={(el) => {
          buttonRef = el;
        }}
      >
        ref
      </Button>,
    );
    expect(buttonRef).toBeInstanceOf(HTMLButtonElement);
  });
});
