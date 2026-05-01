import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Textarea from './Textarea';

describe('Textarea', () => {
  it('label과 textarea를 연결한다', () => {
    render(<Textarea label="메모" />);
    expect(screen.getByLabelText('메모')).toBeInTheDocument();
  });

  it('error 시 aria-invalid=true와 메시지를 노출한다', () => {
    render(<Textarea label="메모" error="필수 입력" />);
    expect(screen.getByLabelText('메모')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('필수 입력')).toBeInTheDocument();
  });

  it('maxLength 속성이 textarea에 전달된다', () => {
    render(<Textarea label="메모" maxLength={100} />);
    expect(screen.getByLabelText('메모')).toHaveAttribute('maxlength', '100');
  });

  it('사용자 입력을 받는다', async () => {
    const user = userEvent.setup();
    render(<Textarea label="메모" />);
    const ta = screen.getByLabelText('메모');
    await user.type(ta, '테스트');
    expect(ta).toHaveValue('테스트');
  });
});
