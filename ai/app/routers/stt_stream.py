from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from app.clients.gpu_stt import GpuSttClient
from app.core.errors import BusinessException
from app.core.security import decode_tenant_id
from app.schemas.stt import SttSegment, SttStreamChunk
from app.core.speaker_session import SpeakerSessionManager

router = APIRouter(prefix="/api/v1/stt", tags=["STT Stream"])

_MAX_CHUNK_BYTES = 1 * 1024 * 1024


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
    session = manager.get_or_create(session_key)

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

            try:
                chunk = await gpu_stt.transcribe_chunk(audio_bytes)
                text = chunk.get("text", "").strip()

                if not text:
                    continue

                speaker_id = session.assign(chunk.get("embedding", []))

                transcript_msg = SttStreamChunk(
                    type="transcript",
                    segment=SttSegment(
                        text=text,
                        speaker_id=speaker_id,
                        start_ms=chunk.get("start_ms", 0),
                        end_ms=chunk.get("end_ms", 0),
                    ),
                )
                await websocket.send_json(transcript_msg.model_dump())

            except BusinessException as e:
                error_msg = SttStreamChunk(type="error", message=e.detail)
                await websocket.send_json(error_msg.model_dump())

    except WebSocketDisconnect:
        pass

    finally:
        manager.remove(session_key)
