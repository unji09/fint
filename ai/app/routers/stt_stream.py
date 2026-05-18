import logging

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from app.clients.gpu_stt import GpuSttClient
from app.core.errors import BusinessException
from app.core.security import decode_tenant_id
from app.schemas.stt import SttSegment, SttStreamChunk

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/stt", tags=["STT Stream"])

_MAX_CHUNK_BYTES = 1 * 1024 * 1024
# Whisper no_speech_prob 임계값 — 이 이상이면 hallucination으로 간주하고 버린다
_NO_SPEECH_THRESHOLD = 0.6
# Opus DTX 무음 프레임 최소 크기 임계값 (2s@128kbps ≈ 32KB, DTX 무음 ≈ < 2KB)
_SILENT_CLUSTER_BYTES = 1500

# webm Cluster element ID — 이 바이트 앞까지가 컨테이너 헤더(EBML+Segment+Tracks)
_WEBM_CLUSTER_ID = b"\x1f\x43\xb6\x75"

# 전사 파라미터 — 2 clusters ≈ 4s, beam_size=5
_FAST_CLUSTER_COUNT = 2
_FAST_BEAM_SIZE = 5

# Whisper가 YouTube/방송 학습 데이터 또는 initial_prompt에서 복사하는 알려진 환각 패턴.
# initial_prompt 전체 문장이 아닌 일부 키워드로 등록해 변형도 잡는다.
_HALLUCINATION_PATTERNS = (
    "시청해주셔서 감사합니다",
    "구독과 좋아요",
    "자막 제공",
    "자막을 사용",
    "다음 영상에서 만나요",
    "제작지원으로 제작",
    "미팅 내용을 전사합니다",   # "영업 미팅 내용을 전사합니다" 포함 — initial_prompt 반복
    "MBC 뉴스",
    "KBS 뉴스",
    "SBS 뉴스",
    "翻訳",
    "字幕",
    "Subtitles by",
    "amara.org",
)

# 환각 패턴을 제거한 뒤 남은 실제 발화로 간주하기 위한 최소 글자 수
# 한국어는 한 글자가 영어 단어 수준의 정보량을 담으므로 4자로 설정
# (ex: "짠" 1자 → 필터, "안녕하세요" 5자 → 통과)
_MIN_REAL_LEN = 4


def _extract_webm_header(first_chunk: bytes) -> bytes:
    """첫 번째 청크에서 Cluster 이전의 컨테이너 헤더만 추출한다."""
    idx = first_chunk.find(_WEBM_CLUSTER_ID)
    return first_chunk[:idx] if idx != -1 else b""


def _clean_text(text: str, no_speech_prob: float) -> str:
    """
    환각 패턴을 제거하고 남은 실제 발화를 반환한다.

    - no_speech_prob 임계값 초과 → 전체 무음으로 판단, "" 반환
    - 환각 패턴이 중간/끝에 있으면 해당 위치부터 잘라낸다
    - 잘라낸 뒤 남은 텍스트가 _MIN_REAL_LEN 미만이면 "" 반환
    """
    if no_speech_prob >= _NO_SPEECH_THRESHOLD:
        return ""
    for pattern in _HALLUCINATION_PATTERNS:
        idx = text.find(pattern)
        if idx >= 0:
            text = text[:idx].rstrip(" .,。!?　")
    if len(text) < _MIN_REAL_LEN:
        return ""
    return text


@router.websocket("/stream/{activity_id}")
async def stt_stream(
    websocket: WebSocket,
    activity_id: str,
    token: str = Query(...),
) -> None:
    try:
        tenant_id = decode_tenant_id(token)
    except BusinessException:
        await websocket.close(code=4001, reason="Unauthorized")
        return

    gpu_stt: GpuSttClient | None = getattr(websocket.app.state, "gpu_stt_client", None)
    if gpu_stt is None:
        await websocket.close(code=4003, reason="GPU STT server not configured")
        return

    await websocket.accept()

    session_key = f"{tenant_id}:{activity_id}"

    # MediaRecorder timeslice 모드의 fragmented webm 처리:
    # 첫 번째 청크에만 EBML+Segment+Tracks 헤더가 포함되어 있으므로 저장해두고
    # 이후 Cluster들을 두 개의 버퍼(_FAST_, _REFINE_)에 각각 누적한다.
    webm_header: bytes = b""
    is_first_chunk = True
    elapsed_ms: int = 0

    # 전사 버퍼 — _FAST_CLUSTER_COUNT 개 모이면 즉시 전송
    fast_buffer: list[bytes] = []
    fast_buffer_start_ms: int = 0

    # 직전 전사 결과 — initial_prompt 문맥으로 사용
    prev_text: str = ""

    async def _send_transcript(
        clusters: list[bytes],
        range_start: int,
        range_end: int,
        context: str,
    ) -> str:
        """전사 요청 → GPU → WebSocket 전송. 텍스트 반환, 빈 문자열이면 필터됨."""
        payload = webm_header + b"".join(clusters)
        log.info("[STT fast] → GPU /stt/chunk payload=%d bytes range=[%d,%d]",
                 len(payload), range_start, range_end)
        try:
            chunk = await gpu_stt.transcribe_chunk(
                payload,
                session_id=f"{session_key}:fast",
                prev_text=context,
                beam_size=_FAST_BEAM_SIZE,
            )
            text = chunk.get("text", "").strip()
            no_speech_prob = float(chunk.get("no_speech_prob", 0.0))

            text = _clean_text(text, no_speech_prob)
            if not text:
                log.info("[STT fast] skip: no_speech=%.2f", no_speech_prob)
                return ""

            speaker_id: str = str(chunk.get("speaker_id", "SPEAKER_00"))

            msg = SttStreamChunk(
                type="transcript",
                is_draft=False,
                range_start_ms=range_start,
                range_end_ms=range_end,
                segment=SttSegment(
                    text=text,
                    speaker_id=speaker_id,
                    start_ms=int(chunk.get("start_ms", 0)) + range_start,
                    end_ms=int(chunk.get("end_ms", 0)) + range_start,
                ),
            )
            await websocket.send_json(msg.model_dump())
            log.info("[STT fast] sent range=[%d,%d]: %r", range_start, range_end, text[:60])
            return text

        except BusinessException as e:
            log.warning("[STT fast] business error: %s", e.detail)
            await websocket.send_json(SttStreamChunk(
                type="error",
                message=str(e.detail),
            ).model_dump())
            return ""
        except Exception as e:
            log.exception("[STT fast] unexpected error: %s", e)
            await websocket.send_json(SttStreamChunk(
                type="error",
                message="전사 처리 중 오류가 발생했습니다.",
            ).model_dump())
            return ""

    try:
        while True:
            audio_bytes = await websocket.receive_bytes()

            log.info("[STT rx] session=%s total_bytes=%d", session_key, len(audio_bytes))

            if len(audio_bytes) > _MAX_CHUNK_BYTES:
                await websocket.send_json(SttStreamChunk(
                    type="error",
                    message=f"청크 크기 초과 (최대 {_MAX_CHUNK_BYTES // 1024} KB)",
                ).model_dump())
                continue

            # ── EOS 신호 (0-byte) — 남은 버퍼 플러시 후 stream_ended 전송 ────
            # 클라이언트가 녹음을 종료할 때 마지막으로 0-byte 프레임을 전송한다.
            # refine_buffer에 아직 처리하지 못한 클러스터가 남아있을 수 있으므로
            # 이 시점에 동기로 처리하고 완료 신호를 돌려준다.
            if len(audio_bytes) == 0:
                log.info("[STT EOS] session=%s fast_buf=%d", session_key, len(fast_buffer))
                # 버퍼 잔량 플러시
                if fast_buffer:
                    clusters_snap = list(fast_buffer)
                    fast_start = fast_buffer_start_ms
                    fast_buffer.clear()
                    new_text = await _send_transcript(
                        clusters_snap, fast_start, elapsed_ms, prev_text,
                    )
                    if new_text:
                        prev_text = new_text[-100:]
                try:
                    await websocket.send_json({"type": "stream_ended"})
                except Exception:
                    pass
                break

            if is_first_chunk:
                webm_header = _extract_webm_header(audio_bytes)
                is_first_chunk = False
                cluster_data = audio_bytes[len(webm_header):]
                log.info("[STT rx] first chunk: header=%d bytes, cluster=%d bytes",
                         len(webm_header), len(cluster_data))
            else:
                cluster_data = audio_bytes

            # Opus DTX 무음 프레임 — 버퍼에 누적하지 않고 시간만 진행
            if len(cluster_data) < _SILENT_CLUSTER_BYTES:
                log.info("[STT chunk] skip silent cluster size=%d", len(cluster_data))
                elapsed_ms += 2000
                continue

            # 버퍼가 비어있던 상태에서 첫 클러스터 도착 → 윈도우 시작 시각 기록
            if len(fast_buffer) == 0:
                fast_buffer_start_ms = elapsed_ms

            fast_buffer.append(cluster_data)
            log.info("[STT buf] fast=%d/%d elapsed_ms=%d",
                     len(fast_buffer), _FAST_CLUSTER_COUNT, elapsed_ms)

            cluster_end_ms = elapsed_ms + 2000

            # ── 전사 ──────────────────────────────────────────────────────────
            if len(fast_buffer) >= _FAST_CLUSTER_COUNT:
                clusters_snap = list(fast_buffer)
                fast_start = fast_buffer_start_ms
                fast_end = cluster_end_ms
                fast_buffer.clear()

                new_text = await _send_transcript(
                    clusters_snap, fast_start, fast_end, prev_text,
                )
                if new_text:
                    prev_text = new_text[-100:]

            elapsed_ms = cluster_end_ms

    except WebSocketDisconnect:
        pass
    finally:
        await gpu_stt.clear_session(f"{session_key}:fast")
