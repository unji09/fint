from pydantic import BaseModel, Field


class BusinessCardOcrRequest(BaseModel):
    s3_key: str = Field(
        ...,
        description=(
            "S3 객체 키 "
            "(business-cards/{contactId}/{uuid}.{ext})"
        ),
        examples=[
            "business-cards/1/abc123.jpg",
        ],
    )


class BusinessCardOcrResponse(BaseModel):
    name: str | None = Field(
        default=None,
        description="이름",
        examples=["김민수"],
    )

    company: str | None = Field(
        default=None,
        description="회사명",
        examples=["ABC Tech"],
    )

    title: str | None = Field(
        default=None,
        description="직함",
        examples=["Backend Engineer"],
    )

    phone: str | None = Field(
        default=None,
        description="전화번호",
        examples=["010-1234-5678"],
    )

    email: str | None = Field(
        default=None,
        description="이메일",
        examples=["minsu.kim@abctech.com"],
    )