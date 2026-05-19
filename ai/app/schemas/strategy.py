from enum import Enum

from pydantic import BaseModel, Field


class TriggerType(str, Enum):
    NEWS_UPDATED = "NEWS_UPDATED"
    DART_UPDATED = "DART_UPDATED"
    NEWS_MAPPED_TO_NEW_ACCOUNT = "NEWS_MAPPED_TO_NEW_ACCOUNT"
    DART_MAPPED_TO_NEW_ACCOUNT = "DART_MAPPED_TO_NEW_ACCOUNT"
    EXTERNAL_SIGNAL_UPDATED = "EXTERNAL_SIGNAL_UPDATED"
    IMPORTANT_KEYWORD_DETECTED = "IMPORTANT_KEYWORD_DETECTED"
    MEETING_CREATED = "MEETING_CREATED"


class NextActionRequest(BaseModel):
    account_id: int
    trigger_type: TriggerType
    news_article_ids: list[int] | None = None
    dart_disclosure_ids: list[int] | None = None
    meeting_ids: list[int] | None = None
    context: str | None = None


class NextActionResponse(BaseModel):
    action: str
    reason: str
    category: str
    related_type: str
    importance_score: int = Field(ge=0, le=5)
    success_probability: int
    sources: dict
    recommended_script: str
    pipeline_stage_id: int


class FeedbackRequest(BaseModel):
    action_id: str
    action_name: str
    label: str = Field(pattern=r"^(good|bad|neutral)$")
    label_reason: str | None = None
    session_id: str | None = None
