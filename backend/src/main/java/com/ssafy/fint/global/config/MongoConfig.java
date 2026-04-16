package com.ssafy.fint.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * MongoDB Auditing 활성화.
 * BaseDocument 의 @CreatedDate / @LastModifiedDate 가 동작하려면 이 설정이 필요하다.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {
}
