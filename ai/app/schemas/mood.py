from pydantic import BaseModel, Field


class MoodAnalysisResult(BaseModel):
    mood_score: int = Field(ge=0, le=100)
    reason: str
    key_signals: list[str]


class MoodAnalysisRequest(BaseModel):
    activity_id: int
    account_id: int
    transcript: str