package com.ssafy.fint.domain.contact.controller;

import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.contact.dto.request.ContactCreateRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactOcrInitRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactOcrRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactUpdateRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactCreateResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactListResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrInitResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrInitResult;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrResponse;
import com.ssafy.fint.domain.contact.service.ContactOcrService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Contact", description = "고객 담당자 관리 API")
@RestController
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;
    private final ContactOcrService contactOcrService;

    @Operation(summary = "담당자 등록", description = "고객사에 담당자를 등록합니다.")
    @PostMapping("/contacts")
    public ResponseEntity<ApiResponse<ContactCreateResponse>> createContact(
        @RequestBody @Valid ContactCreateRequest request
    ) {

        ContactCreateResponse response = contactService.createContact(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @Operation(summary = "담당자 전체 조회", description = "고객사에 속한 담당자 목록을 조회합니다.")
    @GetMapping("/accounts/{accountId}/contacts")
    public ResponseEntity<ApiResponse<List<ContactListResponse>>> getContacts(
        @PathVariable Long accountId
    ) {
        List<ContactListResponse> response = contactService.getContacts(accountId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @Operation(summary = "담당자 수정", description = "담당자 정보를 수정합니다.")
    @PatchMapping("/contacts/{contactId}")
    public ResponseEntity<Void> updateContact(
        @PathVariable Long contactId,
        @RequestBody @Valid ContactUpdateRequest request
    ) {
        contactService.updateContact(contactId, request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "담당자 삭제", description = "담당자를 소프트 삭제합니다.")
    @DeleteMapping("/contacts/{contactId}")
    public ResponseEntity<Void> deleteContact(
        @PathVariable Long contactId
    ) {
        contactService.deleteContact(contactId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "OCR 기반 담당자 등록", description = "OCR 추출 정보로 고객사 조회/생성 후 담당자를 등록합니다.")
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    @PostMapping("/contacts/ocr/init")
    public ResponseEntity<ApiResponse<ContactOcrInitResponse>> initContactByOcr(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestBody @Valid ContactOcrInitRequest request
    ) {
        ContactOcrInitResult result = contactService.initContactByOcr(me, request);
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(result.response()));
        }
        return ResponseEntity.ok(ApiResponse.ok(result.response()));
    }

    @Operation(
            summary = "명함 OCR 추출",
            description = "S3 에 업로드된 명함 이미지(fileKey)를 FastAPI OCR 서비스로 전달해 담당자 정보를 추출합니다."
    )
    @PreAuthorize("hasRole('MEMBER') or hasRole('ADMIN')")
    @PostMapping("/contacts/ocr")
    public ResponseEntity<ApiResponse<ContactOcrResponse>> extractBusinessCardOcr(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestBody @Valid ContactOcrRequest request
    ) {
        ContactOcrResponse response = contactOcrService.extract(me, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}

