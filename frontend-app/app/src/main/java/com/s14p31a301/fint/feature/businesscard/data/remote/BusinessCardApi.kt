package com.s14p31a301.fint.feature.businesscard.data.remote

import com.s14p31a301.fint.core.network.dto.ApiResponse
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.CreateContactRequestDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.CreateContactResponseDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.OcrRequestDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.OcrResponseDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.PresignedUrlRequestDto
import com.s14p31a301.fint.feature.businesscard.data.remote.dto.PresignedUrlResponseDto
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * 명함 OCR 관련 서버 API.
 *
 *  1) [requestPresignedUrl] POST /files/presigned-url (purpose=OCR, fileType=IMAGE)
 *  2) S3 PUT 업로드는 [com.s14p31a301.fint.core.network.s3.S3Uploader] 사용
 *     (Content-Type: image/jpeg, x-amz-server-side-encryption: aws:kms)
 *  3) [ocr]           POST /contacts/ocr   (mydocs/file_api/명함 OCR 추출.md)
 *  4) [createContact] POST /contacts ("담당자로 저장")
 */
interface BusinessCardApi {

    @POST("files/presigned-url")
    suspend fun requestPresignedUrl(
        @Body body: PresignedUrlRequestDto,
    ): ApiResponse<PresignedUrlResponseDto>

    @POST("contacts/ocr")
    suspend fun ocr(
        @Body body: OcrRequestDto,
    ): ApiResponse<OcrResponseDto>

    @POST("contacts")
    suspend fun createContact(
        @Body body: CreateContactRequestDto,
    ): ApiResponse<CreateContactResponseDto>
}
