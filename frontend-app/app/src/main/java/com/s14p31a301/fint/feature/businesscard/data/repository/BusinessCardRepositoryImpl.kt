package com.s14p31a301.fint.feature.businesscard.data.repository

import com.s14p31a301.fint.feature.businesscard.data.remote.BusinessCardApi
import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult
import com.s14p31a301.fint.feature.businesscard.domain.repository.BusinessCardRepository
import com.s14p31a301.fint.core.network.s3.S3Uploader
import java.io.File

/**
 * 카메라 캡처 → S3 업로드 → OCR → contacts 등록 흐름의 Repository.
 *
 * ⚠️ 본문은 Phase 4 데이터/네트워크 담당자가 채워야 함.
 * 현재는 화면 흐름 점검용 stub: 모든 메서드가 [NotImplementedError] 를 담은 [Result.failure] 반환.
 * 화면(VM) 측은 실패 시 demo fallback 으로 동작하도록 설계되어 있음.
 */
class BusinessCardRepositoryImpl(
    private val api: BusinessCardApi,
    private val s3Uploader: S3Uploader,
) : BusinessCardRepository {

    override suspend fun uploadAndOcr(image: File): Result<BusinessCardOcrResult> {
        // TODO(API): POST /files/presigned → S3 PUT → POST /ai/ocr
        // 1. val presigned = api.requestPresigned(...)
        // 2. s3Uploader.upload(presigned.url, image)
        // 3. val ocr = api.ocr(presigned.fileKey)
        // 4. return Result.success(ocr.toDomain(presigned.fileKey))
        return Result.failure(NotImplementedError("BusinessCardRepository.uploadAndOcr — Phase 4 API 담당자 구현 필요"))
    }

    override suspend fun registerContact(result: BusinessCardOcrResult): Result<Long> {
        // TODO(API): POST /contacts
        return Result.failure(NotImplementedError("BusinessCardRepository.registerContact — Phase 4 API 담당자 구현 필요"))
    }
}
