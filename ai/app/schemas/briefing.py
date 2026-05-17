from pydantic import BaseModel, Field


class ContactInfo(BaseModel):
    name: str
    position: str | None = None
    personality: str | None = None


class DealSummary(BaseModel):
    deal_id: int
    title: str
    current_stage: str
    probability: int | None = None
    amount: int | None = None


class MeetingHistory(BaseModel):
    activity_id: int
    title: str
    date: str
    summary: str | None = None
    mood_score: int | None = None
    mood_reason: str | None = None


class SignalItem(BaseModel):
    signal_type: str
    title: str
    summary: str | None = None
    published_at: str
    importance_score: float | None = None


class BriefingRequest(BaseModel):
    activity_id: int
    title: str
    scheduled_at: str
    account_id: int
    account_name: str
    industry: str | None = None
    current_mood: str | None = None
    mood_score: int | None = Field(default=None, ge=0, le=100)
    mood_reason: str | None = None
    contacts: list[ContactInfo] = []
    deals: list[DealSummary] = []
    recent_meetings: list[MeetingHistory] = []
    signals: list[SignalItem] = []
    wiki_summary: str | None = None


class BriefingResponse(BaseModel):
    key_points: list[str] = Field(min_length=1, max_length=5)
    alerts: list[str] = Field(max_length=3)
