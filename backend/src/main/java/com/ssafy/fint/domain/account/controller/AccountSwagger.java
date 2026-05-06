package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Customer", description = "고객사(Account) 정보 관리 API")
public interface AccountSwagger {

    @Operation(
            summary = "고객사 등록",
            description = "현재 로그인한 사원을 owner로 하는 고객사를 등록한다. bizNo는 선택값이다."
    )
    ApiResponse<AccountRegisterResponse> register(AccountRegisterRequest request);

    @Operation(
            summary = "고객사 수정",
            description = "고객사 정보를 부분 수정한다. name·industry·bizNo 는 모두 nullable 이며 null 인 필드는 변경되지 않는다. " +
                    "타 사용자 / 타 테넌트 소유 또는 미존재는 모두 NOT_FOUND 로 응답한다."
    )
    void update(Long accountId, AccountUpdateRequest request);

    @Operation(
            summary = "고객사 삭제",
            description = "본인이 소유한 고객사를 소프트 삭제한다(is_deleted=true). " +
                    "타 사용자 / 타 테넌트 소유 또는 미존재는 모두 NOT_FOUND 로 응답한다."
    )
    void delete(Long accountId);

    @Operation(
            summary = "고객사 시그널 조회",
            description = "고객사 관련 외부 시그널(NEWS/DART)을 occurred_at 내림차순으로 조회한다. " +
                    "source 미지정 시 모든 출처 통합, 지정 시 해당 출처만. size 미지정 시 기본 20 건. " +
                    "타 사용자 / 타 테넌트 소유 또는 미존재는 NOT_FOUND 로 응답한다."
    )
    ApiResponse<List<AccountSignalResponse>> findSignals(Long accountId, String source, Integer size);
}
