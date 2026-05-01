import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import StageBadge from './StageBadge';
import TemperatureBadge from './TemperatureBadge';
import SignalSourceBadge from './SignalSourceBadge';
import PersonaBadge from './PersonaBadge';

describe('StageBadge', () => {
  it('스테이지 라벨을 표시한다', () => {
    render(<StageBadge stage="FIRST_MEETING" />);
    expect(screen.getByText('첫 미팅 준비')).toBeInTheDocument();
  });

  it('성사·실패 라벨도 정상 매핑된다', () => {
    render(<StageBadge stage="CLOSED" />);
    expect(screen.getByText('성사 / 실패')).toBeInTheDocument();
  });
});

describe('TemperatureBadge', () => {
  it('HOT 라벨을 표시하고 컬러 토큰이 적용된다', () => {
    render(<TemperatureBadge temperature="HOT" />);
    const badge = screen.getByText('HOT');
    expect(badge).toBeInTheDocument();
    expect(badge.style.color).toBe('var(--temp-hot)');
  });
});

describe('SignalSourceBadge', () => {
  it('DART/NEWS/LOG/AI 라벨이 정상 매핑된다', () => {
    const { rerender } = render(<SignalSourceBadge source="DART" />);
    expect(screen.getByText('DART')).toBeInTheDocument();

    rerender(<SignalSourceBadge source="AI" />);
    expect(screen.getByText('AI 분석')).toBeInTheDocument();
  });
});

describe('PersonaBadge', () => {
  it('블로커 라벨이 정상 노출된다', () => {
    render(<PersonaBadge persona="BLOCKER" />);
    expect(screen.getByText('블로커 — 공략')).toBeInTheDocument();
  });

  it('챔피언 페르소나의 bg/fg 토큰이 적용된다', () => {
    render(<PersonaBadge persona="CHAMPION" />);
    const badge = screen.getByText('챔피언 — 활용');
    expect(badge.style.color).toBe('var(--persona-champion-fg)');
    expect(badge.style.backgroundColor).toBe('var(--persona-champion-bg)');
  });
});
