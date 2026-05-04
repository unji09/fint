import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Stepper from './Stepper';

const steps = [
  { label: 'FINT 데이터베이스 조회' },
  { label: '페이지 생성' },
  { label: '담당자 정보 업데이트' },
  { label: '완료' },
];

describe('Stepper', () => {
  it('모든 step 라벨을 렌더링한다', () => {
    render(<Stepper steps={steps} current={0} />);
    expect(screen.getByText('FINT 데이터베이스 조회')).toBeInTheDocument();
    expect(screen.getByText('완료')).toBeInTheDocument();
  });

  it('current 단계는 aria-current=step', () => {
    render(<Stepper steps={steps} current={1} />);
    expect(screen.getByText('페이지 생성').closest('li')).toHaveAttribute(
      'aria-current',
      'step',
    );
  });

  it('current 이전 단계는 completed 클래스를 적용한다', () => {
    render(<Stepper steps={steps} current={2} />);
    const firstStep = screen.getByText('FINT 데이터베이스 조회').closest('li');
    expect(firstStep).toHaveClass(/completed/);
  });

  it('horizontal orientation 클래스가 적용된다', () => {
    const { container } = render(
      <Stepper steps={steps} current={0} orientation="horizontal" />,
    );
    expect(container.firstChild).toHaveClass(/horizontal/);
  });
});
