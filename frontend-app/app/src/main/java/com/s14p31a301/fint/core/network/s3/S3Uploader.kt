package com.s14p31a301.fint.core.network.s3

import java.io.File

/**
 * S3 Presigned URL 업로드 담당.
 * 1) 서버에 presigned URL 요청
 * 2) 해당 URL로 PUT 업로드
 * 3) 업로드 완료된 file key 반환
 */
class S3Uploader {
    suspend fun upload(presignedUrl: String, file: File, contentType: String): Result<String> {
        // TODO
        return Result.failure(NotImplementedError())
    }
}

