import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import FintChatPanel from './FintChatPanel';
import type { ChatMessage, Step } from '@/types/dashboard';

Element.prototype.scrollIntoView = vi.fn();

vi.mock('./ChartWidgets', () => ({
  BarChartSvg: () => <div data-testid="bar-chart" />,
  LineChartSvg: () => <div data-testid="line-chart" />,
  SegmentChart: () => <div data-testid="segment-chart" />,
  KpiCard: () => <div data-testid="kpi-card" />,
  TableWidget: () => <div data-testid="table-widget" />,
}));

const baseProps = {
  steps: [] as Step[],
  query: '',
  isLoading: false,
  isDone: false,
  widgetTitle: '',
  widgetType: 'BAR_CHART',
  onTitleChange: vi.fn(),
  onCollapse: vi.fn(),
  onDragStart: vi.fn(),
};

function makeUserMsg(id: string, content: string): ChatMessage {
  return { id, role: 'user', content, widget: null, timestamp: new Date().toISOString(), status: 'done' };
}

function makeAssistantMsg(id: string, content: string, widget?: ChatMessage['widget']): ChatMessage {
  return { id, role: 'assistant', content, widget: widget ?? null, timestamp: new Date().toISOString(), status: 'done' };
}

function makeErrorMsg(id: string, errorMessage: string): ChatMessage {
  return { id, role: 'assistant', content: '', widget: null, timestamp: new Date().toISOString(), status: 'error', errorMessage };
}

describe('FintChatPanel — chatHistory', () => {
  it('사용자 메시지를 렌더링한다', () => {
    const history = [makeUserMsg('u1', '월별 매출 추이')];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByText('월별 매출 추이')).toBeInTheDocument();
  });

  it('어시스턴트 메시지의 인사이트 텍스트를 렌더링한다', () => {
    const history = [
      makeUserMsg('u1', '매출 분석해줘'),
      makeAssistantMsg('a1', '매출이 전월 대비 15% 증가했습니다.'),
    ];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByText('매출이 전월 대비 15% 증가했습니다.')).toBeInTheDocument();
  });

  it('에러 어시스턴트 메시지를 에러 스타일로 렌더링한다', () => {
    const history = [
      makeUserMsg('u1', '잘못된 질문'),
      makeErrorMsg('a1', 'CRM 관련 질문만 가능합니다'),
    ];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByText('CRM 관련 질문만 가능합니다')).toBeInTheDocument();
  });

  it('위젯 프리뷰가 있는 어시스턴트 메시지에서 위젯 제목을 표시한다', () => {
    const widget = {
      widgetType: 'BAR_CHART',
      title: '월별 매출 차트',
      data: { labels: ['1월', '2월'], values: [100, 200] },
      config: {},
    };
    const history = [
      makeUserMsg('u1', '매출'),
      makeAssistantMsg('a1', '분석 결과입니다.', widget),
    ];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByText('월별 매출 차트')).toBeInTheDocument();
    expect(screen.getByTestId('bar-chart')).toBeInTheDocument();
  });

  it('여러 대화를 모두 렌더링한다', () => {
    const history = [
      makeUserMsg('u1', '첫 번째 질문'),
      makeAssistantMsg('a1', '첫 번째 답변'),
      makeUserMsg('u2', '두 번째 질문'),
      makeAssistantMsg('a2', '두 번째 답변'),
    ];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByText('첫 번째 질문')).toBeInTheDocument();
    expect(screen.getByText('첫 번째 답변')).toBeInTheDocument();
    expect(screen.getByText('두 번째 질문')).toBeInTheDocument();
    expect(screen.getByText('두 번째 답변')).toBeInTheDocument();
  });

  it('chatHistory가 비어있고 query가 있으면 fallback 사용자 메시지를 표시한다', () => {
    render(<FintChatPanel {...baseProps} chatHistory={[]} query="테스트 질문" />);
    expect(screen.getByText('테스트 질문')).toBeInTheDocument();
  });

  it('chatHistory가 있으면 query fallback을 표시하지 않는다', () => {
    const history = [makeUserMsg('u1', '히스토리 질문')];
    render(<FintChatPanel {...baseProps} chatHistory={history} query="테스트 질문" />);
    expect(screen.getByText('히스토리 질문')).toBeInTheDocument();
    expect(screen.queryByText('테스트 질문')).not.toBeInTheDocument();
  });

  it('isDone 상태에서 마지막 어시스턴트 메시지를 chatHistory에서 제외한다 (드래그 블록에서 별도 렌더링)', () => {
    const history = [
      makeUserMsg('u1', '질문1'),
      makeAssistantMsg('a1', '이전 답변'),
      makeUserMsg('u2', '질문2'),
      makeAssistantMsg('a2', '최신 답변'),
    ];
    render(
      <FintChatPanel
        {...baseProps}
        chatHistory={history}
        isDone={true}
        result={{ data: {}, insightText: '최신 답변' }}
      />,
    );
    expect(screen.getByText('이전 답변')).toBeInTheDocument();
    // '최신 답변'은 chatHistory 블록이 아닌 isDone 드래그 블록에서 렌더링됨
    // 두 블록 모두에서 렌더링되므로 getAllByText로 확인
    const latestAnswers = screen.getAllByText('최신 답변');
    expect(latestAnswers.length).toBe(1);
  });

  it('LINE_CHART 위젯 타입의 프리뷰를 렌더링한다', () => {
    const widget = {
      widgetType: 'LINE_CHART',
      title: '추이 차트',
      data: { labels: ['1월'], values: [100] },
      config: {},
    };
    const history = [makeAssistantMsg('a1', '결과', widget)];
    render(<FintChatPanel {...baseProps} chatHistory={history} />);
    expect(screen.getByTestId('line-chart')).toBeInTheDocument();
  });

  it('에러 메시지가 없는 에러 상태에서 기본 에러 텍스트를 표시한다', () => {
    const errorMsg: ChatMessage = {
      id: 'a1',
      role: 'assistant',
      content: '',
      widget: null,
      timestamp: new Date().toISOString(),
      status: 'error',
    };
    render(<FintChatPanel {...baseProps} chatHistory={[errorMsg]} />);
    expect(screen.getByText('쿼리 처리에 실패했습니다.')).toBeInTheDocument();
  });
});
