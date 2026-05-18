from pydantic import BaseModel, Field


class CompanyEnrichRequest(BaseModel):
    company_name: str = Field(
        ...,
        min_length=1,
        description="OCR에서 추출한 회사명",
        examples=["삼성SDS", "LG CNS", "삼성전자"],
    )


class CompanyInfoItem(BaseModel):
    corp_code: str = Field(description="DART 고유번호 (8자리)")
    corp_name: str = Field(description="DART 등록 법인명")
    biz_no: str | None = Field(default=None, description="사업자등록번호")
    industry_code: str | None = Field(default=None, description="업종코드 (KSIC)")
    ceo_name: str | None = Field(default=None, description="대표자명")
    address: str | None = Field(default=None, description="본사 주소")
    phone: str | None = Field(default=None, description="대표 전화번호")
    established_date: str | None = Field(default=None, description="설립일 (YYYYMMDD)")


class CompanyEnrichResponse(BaseModel):
    matched: bool = Field(description="정확히 1건 매칭 시 true")
    company: CompanyInfoItem | None = Field(default=None, description="자동 적용 대상")
    candidates: list[CompanyInfoItem] = Field(default_factory=list, description="사용자 선택용 후보")
