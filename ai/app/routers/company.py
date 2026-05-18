import asyncio

from fastapi import APIRouter, Depends

from app.clients import get_dart_client
from app.clients.dart import CompanyInfo, DartClient
from app.core.errors import BusinessException, CommonErrorCode
from app.core.response import ApiResponse
from app.core.security import get_tenant_id
from app.schemas.company import (
    CompanyEnrichRequest,
    CompanyEnrichResponse,
    CompanyInfoItem,
)

router = APIRouter(
    prefix="/api/v1/company",
    tags=["Company"],
)


def _to_item(info: CompanyInfo) -> CompanyInfoItem:
    return CompanyInfoItem(
        corp_code=info.corp_code,
        corp_name=info.corp_name,
        biz_no=info.bizr_no or None,
        industry_code=info.induty_code or None,
        ceo_name=info.ceo_nm or None,
        address=info.adres or None,
        phone=info.phn_no or None,
        established_date=info.est_dt or None,
    )


@router.post("/enrich")
async def enrich_company(
    body: CompanyEnrichRequest,
    tenant_id: int = Depends(get_tenant_id),
    dart_client: DartClient | None = Depends(get_dart_client),
) -> ApiResponse[CompanyEnrichResponse]:
    if dart_client is None:
        raise BusinessException(
            CommonErrorCode.EXTERNAL_API_FAILED,
            "DART_API_KEY가 설정되지 않았습니다",
        )

    await dart_client.ensure_corp_codes()
    corp_matches = dart_client.find_corp_fuzzy(body.company_name)

    if not corp_matches:
        return ApiResponse.ok(CompanyEnrichResponse(matched=False))

    if len(corp_matches) == 1:
        info = await dart_client.get_company_info(corp_matches[0].corp_code)
        if info:
            return ApiResponse.ok(
                CompanyEnrichResponse(matched=True, company=_to_item(info)),
            )
        return ApiResponse.ok(
            CompanyEnrichResponse(
                matched=True,
                company=CompanyInfoItem(
                    corp_code=corp_matches[0].corp_code,
                    corp_name=corp_matches[0].corp_name,
                ),
            ),
        )

    # 다수 후보 — 병렬로 company.json 조회
    infos = await asyncio.gather(
        *(dart_client.get_company_info(c.corp_code) for c in corp_matches),
    )
    candidates: list[CompanyInfoItem] = []
    for corp, info in zip(corp_matches, infos):
        if info:
            candidates.append(_to_item(info))
        else:
            candidates.append(
                CompanyInfoItem(corp_code=corp.corp_code, corp_name=corp.corp_name),
            )

    return ApiResponse.ok(
        CompanyEnrichResponse(matched=False, candidates=candidates),
    )
