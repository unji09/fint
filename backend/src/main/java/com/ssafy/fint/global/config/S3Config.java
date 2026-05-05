package com.ssafy.fint.global.config;

import com.ssafy.fint.global.config.properties.AwsS3Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * S3Client / S3Presigner 빈 등록.
 *
 * - access-key / secret-key 가 비어 있으면 DefaultCredentialsProvider 사용
 *   (EC2 IAM role, env, ~/.aws/credentials 등 탐색).
 * - 명시적 키가 주어지면 StaticCredentialsProvider 사용.
 */
@Configuration
@EnableConfigurationProperties(AwsS3Properties.class)
public class S3Config {

    @Bean
    public S3Client s3Client(AwsS3Properties props) {
        return S3Client.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(resolveCredentials(props))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(AwsS3Properties props) {
        return S3Presigner.builder()
                .region(Region.of(props.region()))
                .credentialsProvider(resolveCredentials(props))
                .build();
    }

    private AwsCredentialsProvider resolveCredentials(AwsS3Properties props) {
        if (props.accessKey() != null && !props.accessKey().isBlank()
                && props.secretKey() != null && !props.secretKey().isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(props.accessKey(), props.secretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }
}
