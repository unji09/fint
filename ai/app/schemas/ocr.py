from pydantic import BaseModel, Field


class BusinessCardOcrRequest(BaseModel):
    s3_key: str = Field(..., description="S3 객체 키 (business-cards/{contactId}/{uuid}.{ext})")


class BusinessCardOcrResponse(BaseModel):
    name: str | None = None
    company: str | None = None
    title: str | None = None
    phone: str | None = None
    email: str | None = None


class BusinessCardClassification(BaseModel):
    name: str | None = None
    company: str | None = None
    title: str | None = None
