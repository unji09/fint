package com.s14p31a301.fint.feature.businesscard.data.repository

import android.util.Log
import com.s14p31a301.fint.core.network.s3.S3Uploader
import com.s14p31a301.fint.core.network.toAppError
import com.s14p31a301.fint.core.network.unwrap
import com.s14p31a301.fint.feature.businesscard.data.remote.BusinessCardApi
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.CreateContactRequestDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.OcrRequestDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.PresignedUrlRequestDto
import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult
import com.s14p31a301.fint.feature.businesscard.domain.repository.BusinessCardRepository
import java.io.File

/**
 * 카메라 캡처 → S3 업로드 → OCR → contacts 등록 흐름 Repository.
 *
 * 흐름:
 *   1) POST /files/presigned-url  (fileType=IMAGE, purpose=OCR)
 *   2) S3 PUT (Content-Type: image/jpeg, x-amz-server-side-encryption: aws:kms)
 *   3) POST /contacts/ocr         (fileKey)
 *   4) POST /contacts/ocr/init    ("담당자로 저장" 시점)
 *
 * 명세 참조:
 *  - mydocs/file_api/Presigned URL 발급.md
 *  - mydocs/file_api/S3 Presigned URL 업로드 규칙.md
 *  - mydocs/file_api/명함 OCR 추출.md
 */
class BusinessCardRepositoryImpl(
    private val api: BusinessCardApi,
    private val s3Uploader: S3Uploader,
) : BusinessCardRepository {

    override suspend fun uploadAndOcr(image: File): Result<BusinessCardOcrResult> = runCatching {
        Log.d(TAG, "[1/3] presigned-url 요청: file=${image.name} size=${image.length()}")
        val presigned = api.requestPresignedUrl(
            PresignedUrlRequestDto(
                fileName = image.name,
                contentType = IMAGE_CONTENT_TYPE,
                fileType = "IMAGE",
                purpose = "OCR",
                fileSize = image.length(),
            )
        ).unwrap()
        Log.d(TAG, "[1/3] presigned 응답: fileKey=${presigned.fileKey} expiresIn=${presigned.expiresIn}")
        Log.d(TAG, "[1/3] uploadUrl=${presigned.uploadUrl}")

        Log.d(TAG, "[2/3] S3 PUT 업로드 시작 (SSE-KMS)")
        s3Uploader.upload(
            presignedUrl = presigned.uploadUrl,
            file = image,
            contentType = IMAGE_CONTENT_TYPE,
            sseHeader = S3Uploader.SSE_KMS,
        ).onFailure { Log.e(TAG, "[2/3] S3 업로드 실패", it) }
            .getOrThrow()
        Log.d(TAG, "[2/3] S3 PUT 업로드 완료")

        Log.d(TAG, "[3/3] /contacts/ocr 요청: fileKey=${presigned.fileKey}")
        val ocr = api.ocr(OcrRequestDto(fileKey = presigned.fileKey)).unwrap()
        Log.d(TAG, "[3/3] OCR 응답: name=${ocr.name} title=${ocr.title} company=${ocr.account?.name}")

        BusinessCardOcrResult(
            name = ocr.name,
            company = ocr.account?.name,
            position = ocr.title,
            phone = ocr.phone,
            email = ocr.email,
            address = null,
            rawText = null,
            imageFileKey = presigned.fileKey,
        )
    }.onFailure { Log.e(TAG, "uploadAndOcr 실패", it) }
        .recoverCatching { throw it.toAppError() }

    override suspend fun registerContact(result: BusinessCardOcrResult): Result<Long> = runCatching {
        val name = result.name?.takeIf { it.isNotBlank() }
            ?: error("name is required")
        val accountName = result.company?.takeIf { it.isNotBlank() }
            ?: error("company is required")
        Log.d(TAG, "POST /contacts/ocr/init: name=$name accountName=$accountName")
        val response = api.createContact(
            CreateContactRequestDto(
                accountName = accountName,
                name = name,
                title = result.position,
                phone = result.phone,
                email = result.email,
            )
        ).unwrap()
        Log.d(TAG, "POST /contacts/ocr/init 응답: contactId=${response.contactId} accountId=${response.accountId}")
        response.contactId
    }.onFailure { Log.e(TAG, "registerContact 실패", it) }
        .recoverCatching { throw it.toAppError() }

    private companion object {
        const val IMAGE_CONTENT_TYPE = "image/jpeg"
        const val TAG = "BusinessCardRepo"
    }
}
