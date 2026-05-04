import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Input from './Input';

describe('Input', () => {
  it('label을 렌더링하고 input과 연결한다', () => {
    render(<Input label="이메일" />);
    const input = screen.getByLabelText('이메일');
    expect(input).toBeInTheDocument();
  });

  it('placeholder를 노출한다', () => {
    render(<Input placeholder="이메일을 입력하세요" />);
    expect(screen.getByPlaceholderText('이메일을 입력하세요')).toBeInTheDocument();
  });

  it('사용자 입력을 처리한다', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<Input label="이름" onChange={onChange} />);
    await user.type(screen.getByLabelText('이름'), '홍길동');
    expect(onChange).toHaveBeenCalled();
  });

  it('error가 있으면 메시지를 노출하고 aria-invalid=true를 둔다', () => {
    render(<Input label="이메일" error="형식이 올바르지 않습니다" />);
    const input = screen.getByLabelText('이메일');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('형식이 올바르지 않습니다')).toBeInTheDocument();
  });

  it('helperText를 노출한다 (error 없을 때만)', () => {
    const { rerender } = render(<Input label="비밀번호" helperText="8자 이상" />);
    expect(screen.getByText('8자 이상')).toBeInTheDocument();

    rerender(<Input label="비밀번호" helperText="8자 이상" error="필수 입력" />);
    expect(screen.queryByText('8자 이상')).not.toBeInTheDocument();
    expect(screen.getByText('필수 입력')).toBeInTheDocument();
  });

  it('leftAdornment / rightAdornment를 렌더링한다', () => {
    render(
      <Input
        label="검색"
        leftAdornment={<span data-testid="search-icon" />}
        rightAdornment={<span data-testid="clear-icon" />}
      />,
    );
    expect(screen.getByTestId('search-icon')).toBeInTheDocument();
    expect(screen.getByTestId('clear-icon')).toBeInTheDocument();
  });

  it('forwardRef로 input ref를 노출한다', () => {
    let inputRef: HTMLInputElement | null = null;
    render(
      <Input
        ref={(el) => {
          inputRef = el;
        }}
      />,
    );
    expect(inputRef).toBeInstanceOf(HTMLInputElement);
  });

  it('disabled 상태를 반영한다', () => {
    render(<Input label="이름" disabled />);
    expect(screen.getByLabelText('이름')).toBeDisabled();
  });
});
