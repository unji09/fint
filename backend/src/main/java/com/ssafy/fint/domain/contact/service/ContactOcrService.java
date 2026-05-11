package com.ssafy.fint.domain.contact.service;

import com.ssafy.fint.domain.contact.client.BusinessCardOcrApiResponse;
import com.ssafy.fint.domain.contact.client.BusinessCardOcrClient;
import com.ssafy.fint.domain.contact.dto.request.ContactOcrRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 명함 OCR 추출 오케스트레이션.
 * Spring 은 인증/권한 검사 후 FastAPI {@code /api/v1/ocr/business-card} 로 위임하고,
 * 응답을 외부 API 응답 스펙({@link ContactOcrResponse}) 에 맞춰 매핑한다.
 */
@Service
@RequiredArgsConstructor
public class ContactOcrService {

    private final BusinessCardOcrClient businessCardOcrClient;

    public ContactOcrResponse extract(CustomUserDetails me, ContactOcrRequest request) {
        BusinessCardOcrApiResponse.Data data =
                businessCardOcrClient.extract(me.getTenantId(), request.getFileKey());

        return ContactOcrResponse.of(
                data.name(),
                data.title(),
                data.phone(),
                data.email(),
                data.company()
        );
    }
}
