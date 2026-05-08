package com.s14p31a301.fint.core.network.s3

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.buffer
import okio.source
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * S3 Presigned URL 업로드.
 *
 * Repository 흐름:
 *   1) 서버 `POST /files/presigned` → presignedUrl + fileKey 수신
 *   2) [upload] 호출 → 해당 URL로 PUT 업로드
 *   3) 서버에 fileKey 로 후속 요청 (OCR / STT / 첨부)
 *
 * S3 PUT 은 Authorization 헤더를 붙이면 안 되므로 **별도 OkHttpClient** 사용 권장.
 */
class S3Uploader(
    private val client: OkHttpClient = defaultClient(),
) {

    /** 진행률 콜백: bytesWritten / totalBytes */
    fun interface ProgressListener {
        fun onProgress(bytesWritten: Long, totalBytes: Long)
    }

    /**
     * @return 성공 시 응답 본문 문자열 (S3는 보통 빈 본문). fileKey 는 호출자가 이미 알고 있음.
     */
    suspend fun upload(
        presignedUrl: String,
        file: File,
        contentType: String,
        progressListener: ProgressListener? = null,
    ): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = contentType.toMediaTypeOrNull()
            val body = ProgressRequestBody(file, mediaType, progressListener)

            val request = Request.Builder()
                .url(presignedUrl)
                .put(body)
                .header("Content-Type", contentType)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("S3 upload failed: ${response.code} ${response.message}")
                }
            }
        }
    }

    private class ProgressRequestBody(
        private val file: File,
        private val mediaType: okhttp3.MediaType?,
        private val listener: ProgressListener?,
    ) : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength()
            var written = 0L
            file.source().buffer().use { source ->
                val buffer = okio.Buffer()
                while (true) {
                    val read = source.read(buffer, BUFFER_SIZE)
                    if (read == -1L) break
                    sink.write(buffer, read)
                    written += read
                    listener?.onProgress(written, total)
                }
            }
        }

        companion object {
            private const val BUFFER_SIZE = 8L * 1024L
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}
