package com.s14p31a301.fint.feature.businesscard.domain.repository

import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult
import java.io.File

/**
 * 명함 OCR + 담당자 등록 Repository.
 *
 * 실제 네트워크 구현은 Phase 4 데이터 담당자가 구현 (S3 presigned PUT → /ai/ocr → /contacts).
 * 화면 측은 이 인터페이스에만 의존한다.
 */
interface BusinessCardRepository {

    /**
     * 캡처된 명함 이미지 → S3 업로드 → OCR.
     *
     * @return 인식된 [BusinessCardOcrResult] (필드는 nullable)
     */
    suspend fun uploadAndOcr(image: File): Result<BusinessCardOcrResult>

    /**
     * 사용자가 확인/수정한 정보로 담당자 등록.
     *
     * @return 신규 contactId
     */
    suspend fun registerContact(result: BusinessCardOcrResult): Result<Long>
}

