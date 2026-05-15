import logging

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from app.clients.gpu_stt import GpuSttClient
from app.core.errors import BusinessException
from app.core.security import decode_tenant_id
from app.schemas.stt import SttSegment, SttStreamChunk
from app.core.speaker_session import SpeakerSessionManager

log = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/stt", tags=["STT Stream"])

_MAX_CHUNK_BYTES = 1 * 1024 * 1024
# Whisper no_speech_prob 임계값 — 이 이상이면 hallucination으로 간주하고 버린다
_NO_SPEECH_THRESHOLD = 0.6
# 6초(3×2s) 문맥으로 보내야 webm 파서 오류가 줄고 Whisper 환각이 감소한다
_CLUSTER_BUFFER_COUNT = 3

# webm Cluster element ID — 이 바이트 앞까지가 컨테이너 헤더(EBML+Segment+Tracks)
_WEBM_CLUSTER_ID = b"\x1f\x43\xb6\x75"

# Whisper가 YouTube/방송 학습 데이터에서 복사하는 알려진 환각 패턴
_HALLUCINATION_PATTERNS = (
    "시청해주셔서 감사합니다",
    "구독과 좋아요",
    "자막 제공",
    "자막을 사용",
    "다음 영상에서 만나요",
    "MBC 뉴스",
    "KBS 뉴스",
    "SBS 뉴스",
    "翻訳",
    "字幕",
    "Subtitles by",
    "amara.org",
)


def _extract_webm_header(first_chunk: bytes) -> bytes:
    """첫 번째 청크에서 Cluster 이전의 컨테이너 헤더만 추출한다."""
    idx = first_chunk.find(_WEBM_CLUSTER_ID)
    return first_chunk[:idx] if idx != -1 else b""


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

    manager: SpeakerSessionManager = websocket.app.state.speaker_session_manager
    session_key = f"{tenant_id}:{activity_id}"
    # 새 녹음 연결마다 이전 프로파일을 초기화해 화자 ID가 누적 증가하지 않도록 한다
    manager.remove(session_key)
    session = manager.get_or_create(session_key)

    # MediaRecorder timeslice 모드의 fragmented webm 처리:
    # 첫 번째 청크에만 EBML+Segment+Tracks 헤더가 포함되어 있으므로 저장해두고
    # 이후 Cluster 들을 _CLUSTER_BUFFER_COUNT 개 모아 한 번에 GPU에 전달한다.
    # 단일 Cluster를 보낼 때 발생하는 파서 오류(File ended prematurely 등)를 방지하고
    # Whisper에 더 긴 문맥(6s)을 제공해 환각을 줄인다.
    webm_header: bytes = b""
    is_first_chunk = True
    elapsed_ms: int = 0
    cluster_buffer: list[bytes] = []  # 아직 GPU에 보내지 않은 Cluster raw bytes

    try:
        while True:
            audio_bytes = await websocket.receive_bytes()

            if len(audio_bytes) > _MAX_CHUNK_BYTES:
                error_msg = SttStreamChunk(
                    type="error",
                    message=f"청크 크기 초과 (최대 {_MAX_CHUNK_BYTES // 1024} KB)",
                )
                await websocket.send_json(error_msg.model_dump())
                continue

            if is_first_chunk:
                webm_header = _extract_webm_header(audio_bytes)
                is_first_chunk = False
                cluster_data = audio_bytes[len(webm_header):]
            else:
                cluster_data = audio_bytes

            # Opus DTX(무음) 프레임은 매우 작다. 2초 발화 기준 Opus@32kbps = ~8KB.
            # 1.5KB 미만 Cluster는 무음으로 간주 — GPU에 누적하지 않고 시간만 진행
            if len(cluster_data) < 1500:
                log.info("[STT chunk] skip silent cluster size=%d", len(cluster_data))
                elapsed_ms += 2000
                continue

            cluster_buffer.append(cluster_data)

            # 아직 충분한 Cluster가 모이지 않으면 대기
            if len(cluster_buffer) < _CLUSTER_BUFFER_COUNT:
                continue

            # _CLUSTER_BUFFER_COUNT 개 모임 → 완전한 webm 구성 후 GPU 전송
            payload = webm_header + b"".join(cluster_buffer)
            buffered_count = len(cluster_buffer)
            cluster_buffer.clear()

            try:
                chunk = await gpu_stt.transcribe_chunk(payload)
                text = chunk.get("text", "").strip()
                log.info("[STT chunk] raw=%r no_speech=%.2f len=%d",
                         text[:80] if text else "", chunk.get("no_speech_prob", 0), len(text))

                if not text:
                    elapsed_ms += buffered_count * 2000
                    continue

                if any(p in text for p in _HALLUCINATION_PATTERNS):
                    log.info("[STT chunk] skip: hallucination pattern matched: %r", text[:60])
                    elapsed_ms += buffered_count * 2000
                    continue

                # Whisper가 음성 없음으로 판단한 구간
                no_speech_prob = float(chunk.get("no_speech_prob", 0.0))
                if no_speech_prob >= _NO_SPEECH_THRESHOLD:
                    log.info("[STT chunk] skip: no_speech_prob=%.2f", no_speech_prob)
                    elapsed_ms += buffered_count * 2000
                    continue

                embedding = chunk.get("embedding", [])
                log.info("[STT chunk] embedding len=%d all_zero=%s start_ms=%s elapsed_ms=%d",
                         len(embedding),
                         all(v == 0.0 for v in embedding) if embedding else True,
                         chunk.get("start_ms"), elapsed_ms)
                speaker_id = session.assign(embedding)

                chunk_end_ms: int = int(chunk.get("end_ms", 0))
                transcript_msg = SttStreamChunk(
                    type="transcript",
                    segment=SttSegment(
                        text=text,
                        speaker_id=speaker_id,
                        start_ms=int(chunk.get("start_ms", 0)) + elapsed_ms,
                        end_ms=chunk_end_ms + elapsed_ms,
                    ),
                )
                await websocket.send_json(transcript_msg.model_dump())
                log.info("[STT chunk] sent: %r", text[:60])

                # chunk 안에서 speech가 일찍 끝나도 실제 오디오 구간은 항상 buffered_count×2s
                # chunk_end_ms를 누적하면 장시간 세션에서 타임스탬프가 수십 초씩 drift되므로 고정값 사용
                elapsed_ms += buffered_count * 2000

            except BusinessException as e:
                elapsed_ms += buffered_count * 2000
                error_msg = SttStreamChunk(type="error", message=e.detail)
                await websocket.send_json(error_msg.model_dump())
            except Exception as e:
                elapsed_ms += buffered_count * 2000
                log.exception("[STT chunk] unexpected error: %s", e)

    except WebSocketDisconnect:
        pass

    finally:
        manager.remove(session_key)
