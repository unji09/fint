from enum import StrEnum
from typing import Literal

from pydantic import BaseModel, field_validator


class SttJobStatus(StrEnum):
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


class SttRequest(BaseModel):
    s3_key: str
    language: str = "ko"
    diarize: bool = False


class SttResponse(BaseModel):
    transcript: str


class SttSegment(BaseModel):
    text: str
    speaker_id: str = "SPEAKER_00"
    start_ms: int = 0
    end_ms: int = 0

    @field_validator("start_ms", "end_ms", mode="before")
    @classmethod
    def _coerce_ms(cls, v: object) -> int:
        # GPU 서버가 float 타임스탬프를 반환하는 경우 강제 변환
        try:
            return int(float(v)) if v is not None else 0
        except (TypeError, ValueError):
            return 0

    @field_validator("speaker_id", mode="before")
    @classmethod
    def _coerce_speaker(cls, v: object) -> str:
        return str(v) if v is not None else "SPEAKER_00"


class SttDiarizedResponse(BaseModel):
    segments: list[SttSegment]


class SttJobResponse(BaseModel):
    job_id: str
    status: SttJobStatus
    segments: list[SttSegment] | None = None
    error: str | None = None


class SttStreamChunk(BaseModel):
    type: Literal["transcript", "error", "end"]
    segment: SttSegment | None = None
    message: str | None = None
