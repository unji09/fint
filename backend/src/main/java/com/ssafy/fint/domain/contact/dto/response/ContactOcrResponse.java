package com.ssafy.fint.domain.contact.dto.response;

/**
 * 명함 OCR 추출 응답.
 * FastAPI {@code data.company} 는 {@link Account#name()} 으로 매핑된다.
 */
public record ContactOcrResponse(
        String name,
        String title,
        String phone,
        String email,
        Account account
) {

    public record Account(String name) {
    }

    public static ContactOcrResponse of(String name,
                                        String title,
                                        String phone,
                                        String email,
                                        String companyName) {
        return new ContactOcrResponse(
                name,
                title,
                phone,
                email,
                companyName == null ? null : new Account(companyName)
        );
    }
}
