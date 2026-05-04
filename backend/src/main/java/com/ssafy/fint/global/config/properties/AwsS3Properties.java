package com.ssafy.fint.global.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS S3 관련 설정 바인딩 (`aws.s3.*`).
 *
 * - bucket / region / accessKey / secretKey: 프로필별 yml에서 주입 (env: S3_BUCKET 등)
 * - presign / upload: application.yml 공통 정책
 */
@ConfigurationProperties(prefix = "aws.s3")
public record AwsS3Properties(
        String bucket,
        String region,
        String accessKey,
        String secretKey,
        Presign presign,
        Kms kms,
        Upload upload
) {

    public record Presign(
            long singleExpirationSeconds,
            long multipartExpirationSeconds,
            long downloadExpirationSeconds
    ) {}

    /** SSE-KMS 키 설정. (S3 업로드 규칙 §1) */
    public record Kms(String keyId) {}

    public record Upload(
            long maxSingleSize,
            long maxOcrSize,
            KeyPrefix keyPrefix
    ) {
        public record KeyPrefix(String meeting, String businessCard) {}
    }
}
