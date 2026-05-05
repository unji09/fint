package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Customer", description = "고객사(Account) 정보 관리 API")
public interface AccountSwagger {

    @Operation(
            summary = "고객사 등록",
            description = "현재 로그인한 사원을 owner로 하는 고객사를 등록한다. bizNo는 선택값이다."
    )
    ApiResponse<AccountRegisterResponse> register(AccountRegisterRequest request);

    @Operation(
            summary = "고객사 삭제",
            description = "본인이 소유한 고객사를 소프트 삭제한다(is_deleted=true). " +
                    "타 사용자 / 타 테넌트 소유 또는 미존재는 모두 NOT_FOUND 로 응답한다."
    )
    void delete(Long accountId);
}
