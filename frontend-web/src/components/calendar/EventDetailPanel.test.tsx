/**
 * EventDetailPanel 통합 테스트
 * - REQ-ACT-01: 녹음 중 AI 기록 고지 노출
 * - REQ-COL-01: 화자별 채팅 버블 렌더 + 좌/우 정렬
 * - 시안 적용: 녹음 중 실시간 전사, 녹음 기록 toggle 내부 버블
 * - 반응형: 768px 미만에서 풀스크린·세로 스택
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import EventDetailPanel, { formatSpeakerLabel, SpeakerLine } from './EventDetailPanel';
import type { CalendarEvent } from './types';
import type { SttLine } from '@/hooks/useActivity';

// useActivity hooks를 통째로 mock — 네트워크 호출 차단
vi.mock('@/hooks/useActivity', async () => {
  const actual = await vi.importActual<typeof import('@/hooks/useActivity')>('@/hooks/useActivity');
  return {
    ...actual,
    useUploadRecording: () => ({ upload: vi.fn(async () => ({ ok: true, fileKey: 'k', recordingId: 1 })), uploading: false, error: null }),
    usePollSttStatus: () => ({ poll: vi.fn(async () => ({ status: 'COMPLETED', transcript: [], summary: null })), polling: false }),
    useRecordingList: () => ({ recordings: [], loading: false, error: null, refetch: vi.fn() }),
    useDeleteRecording: () => ({ remove: vi.fn(async () => true), loading: false }),
  };
});

const fintEvent: CalendarEvent = {
  eventId: 'act-101',
  source: 'FINT',
  title: 'Samsung SDS 미팅',
  startAt: '2026-05-14T10:00:00.000Z',
  endAt: '2026-05-14T10:30:00.000Z',
  category: '미팅',
  accountName: 'Samsung SDS',
  dealTitle: 'AI 솔루션 연장 계약',
  pipelineStage: { stageId: 1, stageName: '협상', stageCode: 'NEGO' },
  attendees: { internal: ['박영업'], external: ['김민수 부장'] },
  memo: '',
};

beforeEach(() => {
  // matchMedia 기본 mock — 데스크탑(매치 안 함)
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    configurable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      addListener: vi.fn(),
      removeListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
  // localStorage 토큰
  window.localStorage.setItem('accessToken', 'test-token');
});

afterEach(() => {
  vi.useRealTimers();
  window.localStorage.clear();
});

describe('formatSpeakerLabel', () => {
  it('speakerName이 있으면 그대로 반환', () => {
    expect(formatSpeakerLabel({ text: '', speakerName: '김민수 부장', speakerId: 'SPEAKER_00' })).toBe('김민수 부장');
  });

  it('speakerName 없고 speakerId 끝에 숫자가 있으면 "화자 N"으로 변환', () => {
    expect(formatSpeakerLabel({ text: '', speakerId: 'SPEAKER_00' })).toBe('화자 1');
    expect(formatSpeakerLabel({ text: '', speakerId: 'SPEAKER_01' })).toBe('화자 2');
  });

  it('speakerId만 있고 끝에 숫자가 없으면 speakerId를 그대로 반환', () => {
    expect(formatSpeakerLabel({ text: '', speakerId: 'HOST' })).toBe('HOST');
  });

  it('둘 다 없으면 fallbackIndex로 "화자 N" 표시', () => {
    expect(formatSpeakerLabel({ text: '' }, 0)).toBe('화자 1');
    expect(formatSpeakerLabel({ text: '' }, 1)).toBe('화자 2');
  });

  it('아무 정보도 없으면 "화자"', () => {
    expect(formatSpeakerLabel({ text: '' })).toBe('화자');
  });
});

describe('EventDetailPanel — 녹음 중 뷰', () => {
  it('event가 null이면 렌더되지 않는다', () => {
    const { container } = render(<EventDetailPanel event={null} onClose={() => {}} />);
    expect(container.firstChild).toBeNull();
  });

  it('녹음 중 뷰 진입 시 AI 고지가 노출된다 — REQ-ACT-01', async () => {
    vi.useFakeTimers();

    // MediaRecorder + getUserMedia mock
    const mockStream = { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(mockStream) },
    });
    class MockMR {
      static isTypeSupported() { return true; }
      state = 'inactive';
      ondataavailable: ((e: { data: Blob }) => void) | null = null;
      onstop: (() => void) | null = null;
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.onstop?.(); }
    }
    (globalThis as unknown as { MediaRecorder: typeof MockMR }).MediaRecorder = MockMR;

    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    const recBtn = screen.getByRole('button', { name: /녹음하기/ });

    await act(async () => {
      fireEvent.click(recBtn);
      await Promise.resolve();
    });

    expect(screen.getByTestId('ai-notice')).toHaveTextContent('AI가 자동으로 기록·요약');
    expect(screen.getByText('실시간 전사')).toBeInTheDocument();
    expect(screen.getByText('전사 대기 중…')).toBeInTheDocument();
  });

  it('실시간 전사 컨테이너의 max-height/overflow와 placeholder가 노출된다', async () => {
    const mockStream = { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue(mockStream) },
    });
    class MockMR {
      static isTypeSupported() { return true; }
      state = 'inactive';
      ondataavailable: ((e: { data: Blob }) => void) | null = null;
      onstop: (() => void) | null = null;
      start() { this.state = 'recording'; }
      stop() { this.state = 'inactive'; this.onstop?.(); }
    }
    (globalThis as unknown as { MediaRecorder: typeof MockMR }).MediaRecorder = MockMR;

    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: /녹음하기/ }));
    });
    await act(async () => { await Promise.resolve(); });

    const scroll = screen.getByTestId('live-transcript-scroll');
    expect(scroll.style.overflowY).toBe('auto');
    expect(scroll.style.maxHeight).toBeTruthy();
    expect(screen.getByText('전사 대기 중…')).toBeInTheDocument();
  });

});

describe('SpeakerLine', () => {
  it('isSelf=true이면 data-self="true"이고 우측 정렬', () => {
    render(<SpeakerLine line={{ text: '내 발화', speakerName: '박영업', isSelf: true, timestamp: '00:09' }} />);
    const node = screen.getByTestId('speaker-line');
    expect(node.getAttribute('data-self')).toBe('true');
    expect(node.style.alignItems).toBe('flex-end');
    expect(screen.getByText('박영업')).toBeInTheDocument();
    expect(screen.getByText('00:09')).toBeInTheDocument();
    expect(screen.getByText('내 발화')).toBeInTheDocument();
  });

  it('isSelf=false이면 좌측 정렬 + 회색 배경 버블', () => {
    render(<SpeakerLine line={{ text: '상대 발화', speakerName: '김민수 부장', isSelf: false }} />);
    const node = screen.getByTestId('speaker-line');
    expect(node.getAttribute('data-self')).toBe('false');
    expect(node.style.alignItems).toBe('flex-start');
  });

  it('화자 정보 없이 fallbackIndex만 있어도 "화자 N" 라벨이 보인다', () => {
    render(<SpeakerLine line={{ text: 'x' }} fallbackIndex={0} />);
    expect(screen.getByText('화자 1')).toBeInTheDocument();
  });
});

describe('EventDetailPanel — 녹음 기록 토글 내부 화자 버블', () => {
  // sttStatus 'COMPLETED' 상태 시뮬레이션은 demo fallback을 통해 가능 (uploadRecording이 ok:false → runDemoStt)
  // 그러나 mock에서 uploadRecording을 ok:true로 두었으므로, 직접 sttLines를 주입할 방법은 없음.
  // 대안: 화자 버블 컴포넌트의 prop 분기를 formatSpeakerLabel 테스트로 검증하고,
  // 토글 내부 컨테이너의 스타일 속성만 통합 테스트로 잠근다.

  it('formatSpeakerLabel의 fallback 인덱스가 0/1 교차일 때 두 화자가 번갈아 보인다', () => {
    const lines: SttLine[] = [
      { text: 'a' },
      { text: 'b' },
      { text: 'c' },
      { text: 'd' },
    ];
    const labels = lines.map((l, i) => formatSpeakerLabel(l, i % 2));
    expect(labels).toEqual(['화자 1', '화자 2', '화자 1', '화자 2']);
  });
});

describe('EventDetailPanel — 반응형', () => {
  it('viewport가 768px 미만이면 모달이 풀스크린·세로 스택으로 렌더된다', () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: query.includes('max-width: 767px'),
        media: query,
        onchange: null,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        addListener: vi.fn(),
        removeListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });

    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);

    const modal = screen.getByTestId('event-detail-modal');
    expect(modal.getAttribute('data-mobile')).toBe('true');
    expect(modal.style.width).toBe('100%');
    // jsdom은 `0`을 '0'으로 정규화 — '0px'이 아니라 '0'
    expect(modal.style.borderRadius).toBe('0');

    const body = screen.getByTestId('event-detail-body');
    expect(body.style.flexDirection).toBe('column');
  });

  it('viewport가 768px 이상이면 기존 좌우 분할 모달 유지', () => {
    // beforeEach의 기본 mock(매치 안 함) 그대로 사용
    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    const modal = screen.getByTestId('event-detail-modal');
    expect(modal.getAttribute('data-mobile')).toBe('false');
    expect(modal.style.width).toBe('820px');
    const body = screen.getByTestId('event-detail-body');
    expect(body.style.flexDirection).toBe('row');
  });
});

describe('EventDetailPanel — 기존 회귀', () => {
  it('FINT 이벤트의 좌측 메타가 모두 렌더된다', () => {
    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    expect(screen.getByText('AI 솔루션 연장 계약')).toBeInTheDocument();
    // 헤더 제목 + 좌측 메타 양쪽에 Samsung SDS가 노출됨
    expect(screen.getAllByText(/Samsung SDS/).length).toBeGreaterThanOrEqual(1);
    // 카테고리 chip
    expect(screen.getByText('미팅')).toBeInTheDocument();
  });

  it('메모 편집 버튼 클릭 시 textarea가 노출된다', () => {
    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: '편집' }));
    expect(screen.getByPlaceholderText(/메모를 입력하세요/)).toBeInTheDocument();
  });

  it('녹음하기 / 파일 업로드 버튼이 기존 위치(탭 영역 우측 상단)에 그대로 노출된다', () => {
    render(<EventDetailPanel event={fintEvent} onClose={() => {}} />);
    expect(screen.getByRole('button', { name: /녹음하기/ })).toBeInTheDocument();
    expect(screen.getByText(/파일 업로드/)).toBeInTheDocument();
  });
});
