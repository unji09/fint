from datetime import datetime

from pydantic import BaseModel, Field


class NextActionRequest(BaseModel):
    account_id: int = Field(alias="accountId")
    context: str | None = None


class SourceInfo(BaseModel):
    news: list[str] = Field(default_factory=list)
    dart: list[str] = Field(default_factory=list)
    crm: list[str] = Field(default_factory=list)


class NextActionResponse(BaseModel):
    next_action_id: int = Field(serialization_alias="nextActionId")
    account_id: int = Field(serialization_alias="accountId")
    action: str
    reason: str
    category: str
    priority: int
    success_probability: int = Field(serialization_alias="successProbability")
    sources: SourceInfo
    recommended_script: str = Field(serialization_alias="recommendedScript")
    risk: str
    created_at: datetime = Field(serialization_alias="createdAt")
