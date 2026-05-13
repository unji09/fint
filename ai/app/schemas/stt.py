from typing import Literal

from pydantic import BaseModel


class SttRequest(BaseModel):
    s3_key: str
    language: str = "ko"
    diarize: bool = False


class SttResponse(BaseModel):
    transcript: str


class SttSegment(BaseModel):
    text: str
    speaker_id: str
    start_ms: int
    end_ms: int


class SttDiarizedResponse(BaseModel):
    segments: list[SttSegment]


class SttStreamChunk(BaseModel):
    type: Literal["transcript", "error", "end"]
    segment: SttSegment | None = None
    message: str | None = None
