// REQ-COL-01: STT + 화자분리 응답을 SttLine으로 매핑한다.
// usePollSttStatus가 백엔드의 다양한 응답 형태(camelCase / snake_case / speaker 단일)를 모두 받아들이는지 검증.

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { usePollSttStatus, useUploadRecording } from './useActivity';

// fetchWithAuth를 mock — 1회 응답으로 COMPLETED 도달시켜 즉시 종료
vi.mock('./useAuth', () => ({
  fetchWithAuth: vi.fn(),
}));

import { fetchWithAuth } from './useAuth';

const mockFetch = fetchWithAuth as unknown as ReturnType<typeof vi.fn>;

type FetchSpy = {
  mock: { calls: [RequestInfo | URL, RequestInit?][] };
  mockImplementation: (fn: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) => unknown;
  mockResolvedValue: (value: Response) => unknown;
  mockRestore: () => void;
};

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

// ─── useUploadRecording — multipart 3단계 흐름 ─────────────────────────────

const INIT_RESPONSE = {
  uploadId: 'uid-abc',
  fileKey: 'meetings/uuid.m4a',
  partUploadUrls: [
    { partNumber: 1, uploadUrl: 'https://s3.example.com/part1' },
    { partNumber: 2, uploadUrl: 'https://s3.example.com/part2' },
    { partNumber: 3, uploadUrl: 'https://s3.example.com/part3' },
  ],
  expiresIn: 3600,
  uploadType: 'MULTIPART',
};

const COMPLETE_RESPONSE = { fileKey: 'meetings/uuid.m4a', fileSize: 11 * 1024 * 1024 };

describe('useUploadRecording — multipart 3단계 흐름', () => {
  // 11 MB → 3 파트 (5 MB + 5 MB + 1 MB)
  const BLOB_11MB = new Blob([new Uint8Array(11 * 1024 * 1024)], { type: 'audio/m4a' });

  let fetchSpy: FetchSpy;

  beforeEach(() => {
    mockFetch.mockReset();
    fetchSpy = vi.spyOn(globalThis, 'fetch') as unknown as FetchSpy;
  });

  afterEach(() => {
    fetchSpy.mockRestore();
  });

  it('happy path: init → S3 PUT × 3 → complete → recording 순서·파라미터 검증', async () => {
    mockFetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: INIT_RESPONSE }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: COMPLETE_RESPONSE }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: { activityId: 10, sttStatus: 'PROCESSING' } }) });

    fetchSpy.mockImplementation(async () =>
      ({ ok: true, status: 200, headers: new Headers({ ETag: '"etag-mock"' }) }) as Response,
    );

    const { result } = renderHook(() => useUploadRecording());
    let uploadResult: Awaited<ReturnType<typeof result.current.upload>>;

    await act(async () => {
      uploadResult = await result.current.upload(10, BLOB_11MB, 'm4a', { duration: 300, title: 'B사 영업 미팅' });
    });

    expect(uploadResult!.ok).toBe(true);
    expect(uploadResult!.fileKey).toBe('meetings/uuid.m4a');

    // B. init 파라미터 검증
    const initBody = JSON.parse(mockFetch.mock.calls[0][1].body as string);
    expect(initBody.partCount).toBe(3);
    expect(initBody.fileType).toBe('AUDIO');
    expect(initBody.purpose).toBe('MEETING_RECORD');

    // C. S3 PUT 3회
    const s3Calls = fetchSpy.mock.calls.filter(([url]) => String(url).includes('s3.example.com'));
    expect(s3Calls).toHaveLength(3);

    // D. complete 파라미터 검증
    const completeBody = JSON.parse(mockFetch.mock.calls[1][1].body as string);
    expect(completeBody.uploadId).toBe('uid-abc');
    expect(completeBody.parts).toHaveLength(3);
    expect(completeBody.parts[0]).toMatchObject({ partNumber: 1, etag: '"etag-mock"' });

    // E. recording body: duration + title + fileKey
    const recBody = JSON.parse(mockFetch.mock.calls[2][1].body as string);
    expect(recBody.duration).toBe(300);
    expect(recBody.title).toBe('B사 영업 미팅');
    expect(recBody.fileKey).toBe('meetings/uuid.m4a');
  });

  it('파일이 5 MB 이하이면 partCount=1, S3 PUT 1회', async () => {
    const BLOB_1MB = new Blob([new Uint8Array(1024 * 1024)], { type: 'audio/webm' });
    const initRes1 = {
      ...INIT_RESPONSE,
      partUploadUrls: [{ partNumber: 1, uploadUrl: 'https://s3.example.com/part1' }],
    };

    mockFetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: initRes1 }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: COMPLETE_RESPONSE }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: {} }) });

    fetchSpy.mockResolvedValue(
      ({ ok: true, status: 200, headers: new Headers({ ETag: '"etag-1"' }) }) as Response,
    );

    const { result } = renderHook(() => useUploadRecording());
    await act(async () => { await result.current.upload(10, BLOB_1MB, 'webm'); });

    const initBody = JSON.parse(mockFetch.mock.calls[0][1].body as string);
    expect(initBody.partCount).toBe(1);

    const s3Calls = fetchSpy.mock.calls.filter(([url]) => String(url).includes('s3.example.com'));
    expect(s3Calls).toHaveLength(1);
  });

  it('init 요청 실패 → ok:false 반환, S3 호출 없음', async () => {
    mockFetch.mockResolvedValueOnce({ ok: false, status: 500, json: async () => ({}) });

    const { result } = renderHook(() => useUploadRecording());
    let uploadResult: Awaited<ReturnType<typeof result.current.upload>>;

    await act(async () => {
      uploadResult = await result.current.upload(10, BLOB_11MB, 'm4a');
    });

    expect(uploadResult!.ok).toBe(false);
    expect(result.current.uploading).toBe(false);
    expect(result.current.error).toBeTruthy();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('S3 파트 PUT 실패 → ok:false 반환, complete 호출 없음', async () => {
    mockFetch.mockResolvedValueOnce({ ok: true, json: async () => ({ data: INIT_RESPONSE }) });

    fetchSpy.mockImplementation(async (url: RequestInfo | URL) => {
      if (String(url).includes('part2')) {
        return ({ ok: false, status: 403, headers: new Headers() }) as Response;
      }
      return ({ ok: true, status: 200, headers: new Headers({ ETag: '"etag-mock"' }) }) as Response;
    });

    const { result } = renderHook(() => useUploadRecording());
    let uploadResult: Awaited<ReturnType<typeof result.current.upload>>;

    await act(async () => {
      uploadResult = await result.current.upload(10, BLOB_11MB, 'm4a');
    });

    expect(uploadResult!.ok).toBe(false);
    // init만 호출되고 complete(2번째 fetchWithAuth)는 없어야 함
    expect(mockFetch.mock.calls.length).toBe(1);
  });

  it('meta 생략 시 recording body에 duration:0, title:"" 전송', async () => {
    const BLOB_1MB = new Blob([new Uint8Array(1024 * 1024)], { type: 'audio/webm' });
    const initRes1 = {
      ...INIT_RESPONSE,
      partUploadUrls: [{ partNumber: 1, uploadUrl: 'https://s3.example.com/part1' }],
    };

    mockFetch
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: initRes1 }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: COMPLETE_RESPONSE }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ data: {} }) });

    fetchSpy.mockResolvedValue(
      ({ ok: true, status: 200, headers: new Headers({ ETag: '"e"' }) }) as Response,
    );

    const { result } = renderHook(() => useUploadRecording());
    await act(async () => { await result.current.upload(10, BLOB_1MB, 'webm'); /* meta 생략 */ });

    const recBody = JSON.parse(mockFetch.mock.calls[2][1].body as string);
    expect(recBody.duration).toBe(0);
    expect(recBody.title).toBe('');
  });
});
