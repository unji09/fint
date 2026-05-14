// REQ-COL-01: STT + 화자분리 응답을 SttLine으로 매핑한다.
// usePollSttStatus가 백엔드의 다양한 응답 형태(camelCase / snake_case / speaker 단일)를 모두 받아들이는지 검증.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePollSttStatus } from './useActivity';

// fetchWithAuth를 mock — 1회 응답으로 COMPLETED 도달시켜 즉시 종료
vi.mock('./useAuth', () => ({
  fetchWithAuth: vi.fn(),
}));

import { fetchWithAuth } from './useAuth';

const mockFetch = fetchWithAuth as unknown as ReturnType<typeof vi.fn>;

function makeResponse(data: unknown) {
  return {
    ok: true,
    json: async () => ({ data }),
  } as Response;
}

describe('usePollSttStatus 화자 정보 매핑', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('camelCase 응답(speakerId/speakerName/isSelf)을 SttLine에 그대로 매핑한다', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        sttStatus: 'COMPLETED',
        sttTranscript: [
          { timestamp: '00:03', text: '안녕하세요', speakerId: 'SPEAKER_00', speakerName: '김민수 부장', isSelf: false },
          { timestamp: '00:09', text: '반갑습니다', speakerId: 'SPEAKER_01', speakerName: '박영업', isSelf: true },
        ],
      }),
    );

    const { result } = renderHook(() => usePollSttStatus());
    const onUpdate = vi.fn();

    await act(async () => {
      await result.current.poll(1, onUpdate, { intervalMs: 1, timeoutMs: 100 });
    });

    const last = onUpdate.mock.calls.at(-1)?.[0];
    expect(last.status).toBe('COMPLETED');
    expect(last.transcript).toHaveLength(2);
    expect(last.transcript[0]).toMatchObject({
      timestamp: '00:03',
      text: '안녕하세요',
      speakerId: 'SPEAKER_00',
      speakerName: '김민수 부장',
      isSelf: false,
    });
    expect(last.transcript[1].isSelf).toBe(true);
  });

  it('snake_case 응답(speaker_id/speaker_name/is_self)도 SttLine에 매핑한다', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        sttStatus: 'COMPLETED',
        sttTranscript: [
          { ts: '00:03', content: '안녕', speaker_id: 'SPEAKER_00', speaker_name: '김 부장', is_self: false },
        ],
      }),
    );

    const { result } = renderHook(() => usePollSttStatus());
    const onUpdate = vi.fn();

    await act(async () => {
      await result.current.poll(1, onUpdate, { intervalMs: 1, timeoutMs: 100 });
    });

    const last = onUpdate.mock.calls.at(-1)?.[0];
    expect(last.transcript[0]).toMatchObject({
      timestamp: '00:03',
      text: '안녕',
      speakerId: 'SPEAKER_00',
      speakerName: '김 부장',
      isSelf: false,
    });
  });

  it('speaker 필드만 있는 응답도 speakerId로 매핑한다', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        sttStatus: 'COMPLETED',
        sttTranscript: [{ timestamp: '00:00', text: '말씀', speaker: 'SPEAKER_02' }],
      }),
    );

    const { result } = renderHook(() => usePollSttStatus());
    const onUpdate = vi.fn();

    await act(async () => {
      await result.current.poll(1, onUpdate, { intervalMs: 1, timeoutMs: 100 });
    });

    const last = onUpdate.mock.calls.at(-1)?.[0];
    expect(last.transcript[0].speakerId).toBe('SPEAKER_02');
    expect(last.transcript[0].speakerName).toBeUndefined();
    expect(last.transcript[0].isSelf).toBeUndefined();
  });

  it('화자 정보가 없는 응답도 기존 필드만 매핑되고 undefined로 둔다 (호환성)', async () => {
    mockFetch.mockResolvedValueOnce(
      makeResponse({
        sttStatus: 'COMPLETED',
        sttTranscript: [{ timestamp: '00:00', text: '단일 화자' }],
      }),
    );

    const { result } = renderHook(() => usePollSttStatus());
    const onUpdate = vi.fn();

    await act(async () => {
      await result.current.poll(1, onUpdate, { intervalMs: 1, timeoutMs: 100 });
    });

    const last = onUpdate.mock.calls.at(-1)?.[0];
    expect(last.transcript[0]).toMatchObject({ timestamp: '00:00', text: '단일 화자' });
    expect(last.transcript[0].speakerId).toBeUndefined();
    expect(last.transcript[0].speakerName).toBeUndefined();
    expect(last.transcript[0].isSelf).toBeUndefined();
  });
});
