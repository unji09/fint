package com.s14p31a301.fint.core.network.s3

import android.util.Log
import com.s14p31a301.fint.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
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
        /**
         * S3 SSE 헤더. presigned URL 이 KMS 로 서명되었다면 PUT 요청에도 동일하게
         * 보내야 한다. 기본값 `aws:kms` (S3 Presigned URL 업로드 규칙 참조).
         */
        sseHeader: String? = SSE_KMS,
        /**
         * SSE-KMS Key Id (또는 alias). 서버가 presigned URL 발급 시
         * `ssekmsKeyId` 로 서명했다면 PUT 요청 헤더 `x-amz-server-side-encryption-aws-kms-key-id`
         * 값이 동일해야 한다. 다르거나 누락되면 `SignatureDoesNotMatch`.
         * 기본값: alias/crm-fint-s3-key (S3 Presigned URL 업로드 규칙.md §1).
         */
        sseKmsKeyId: String? = DEFAULT_KMS_KEY_ID,
        /** 확장용 추가 헤더. */
        extraHeaders: Map<String, String> = emptyMap(),
        progressListener: ProgressListener? = null,
    ): kotlin.Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mediaType = contentType.toMediaTypeOrNull()
            val body = ProgressRequestBody(file, mediaType, progressListener)

            // 명세(S3 Presigned URL 업로드 규칙.md) 검증용 로그.
            //  - 버킷: crm-private-bucket-fint
            //  - Content-Type 일치, SSE-KMS 헤더 + KMS key id 동봉
            val httpUrl = presignedUrl.toHttpUrlOrNull()
            val host = httpUrl?.host ?: "<invalid-url>"
            val pathHead = httpUrl?.encodedPath?.take(120) ?: ""
            val bucketOk = host.startsWith("$EXPECTED_BUCKET.") ||
                pathHead.startsWith("/$EXPECTED_BUCKET/")
            Log.d(
                TAG,
                "PUT host=$host path=$pathHead bucketOk=$bucketOk " +
                    "contentType=$contentType sse=$sseHeader kmsKeyId=$sseKmsKeyId size=${file.length()}"
            )
            if (!bucketOk) {
                Log.w(
                    TAG,
                    "Presigned URL bucket mismatch! expected=$EXPECTED_BUCKET host=$host path=$pathHead"
                )
            }

            val builder = Request.Builder()
                .url(presignedUrl)
                .put(body)
                .header("Content-Type", contentType)
            if (sseHeader != null) {
                builder.header("x-amz-server-side-encryption", sseHeader)
            }
            // SSE-KMS Key Id: presigned URL 서명에 ssekmsKeyId 가 포함됐다면 동일 값을 헤더로
            // 보내야 SignatureDoesNotMatch 를 피할 수 있다.
            if (!sseKmsKeyId.isNullOrBlank()) {
                builder.header("x-amz-server-side-encryption-aws-kms-key-id", sseKmsKeyId)
            }
            extraHeaders.forEach { (k, v) -> builder.header(k, v) }
            val request = builder.build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // S3 는 실패 시 XML(AccessDenied / SignatureDoesNotMatch 등) 본문을 돌려준다.
                    // 디버깅 위해 반드시 함께 로그.
                    val errBody = runCatching { response.body?.string() }.getOrNull()
                    Log.e(
                        TAG,
                        "S3 upload failed: code=${response.code} msg=${response.message} body=$errBody"
                    )
                    error("S3 upload failed: ${response.code} ${response.message} body=$errBody")
                }
                Log.d(TAG, "S3 upload OK: code=${response.code}")
            }
            Unit
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
        const val SSE_KMS = "aws:kms"
        /**
         * 명세 (S3 Presigned URL 업로드 규칙.md §1) 의 기본 KMS Key alias.
         * 서버가 presigned URL 을 ssekmsKeyId 포함 서명으로 발급하므로
         * 클라이언트도 동일 값을 헤더로 송신해야 한다.
         */
        const val DEFAULT_KMS_KEY_ID = "alias/crm-fint-s3-key"
        /** 명세 (S3 Presigned URL 업로드 규칙.md) 상의 명함/녹음 공용 버킷명. */
        const val EXPECTED_BUCKET = "crm-private-bucket-fint"
        private const val TAG = "S3Uploader"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor { msg -> Log.d(TAG, msg) }
                            .setLevel(HttpLoggingInterceptor.Level.HEADERS)
                    )
                }
            }
            .build()
    }
}
