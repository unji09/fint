from pydantic import BaseModel


class SttRequest(BaseModel):
    s3_key: str
    language: str = "ko"


class SttResponse(BaseModel):
    transcript: str
