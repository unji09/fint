package com.ssafy.fint.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화.
 * BaseEntity 의 @CreatedDate / @LastModifiedDate 가 동작하려면 이 설정이 필요하다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
