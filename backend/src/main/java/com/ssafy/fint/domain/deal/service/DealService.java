package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.DealContact;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    // TODO(수정 : DEAL-LLM): LLM 기반 수주 확률 계산 도입 시 이 상수를 LLM 호출 결과로 교체.
    // TODO(수정 : DEAL-새 담당자): 화면 맟 진행흐름 결정될 경우, 기존 담당자 아닐 경우 새 담당자로 추가 로직 완성.
    private static final short DUMMY_PROBABILITY = 70;

    private final DealRepository dealRepository;
    private final DealContactRepository dealContactRepository;
    private final AccountRepository accountRepository;
    private final ContactService contactService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    @Transactional
    public DealCreateResponse create(DealCreateRequest request) {
        CustomUserDetails me = currentUser();
        Long tenantId = me.getTenantId();

        Account account = accountRepository.findByIdAndTenantId(request.accountId(), tenantId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.ACCOUNT_NOT_FOUND));

        Team team = null;
        if (request.teamId() != null) {
            team = teamRepository.findByTeamIdAndTenant_TenantId(request.teamId(), tenantId)
                    .orElseThrow(() -> new BusinessException(DealErrorCode.TEAM_NOT_FOUND));
        }

        Deal deal = Deal.builder()
                .account(account)
                .team(team)
                .title(request.title())
                .expectedClose(request.expectedClose())
                .amount(request.amount())
                .probability(DUMMY_PROBABILITY)
                .build();

        Deal savedDeal = dealRepository.save(deal);

        List<DealCreateResponse.ContactDetail> contactDetails =
                linkContacts(savedDeal, account, request.contacts(), me.getUserId());

        log.debug("[DealCreate] dealId={} tenantId={} accountId={} teamId={} contactCount={}",
                savedDeal.getDealId(), tenantId, request.accountId(), request.teamId(),
                contactDetails.size());
        return DealCreateResponse.from(savedDeal, contactDetails);
    }

    private List<DealCreateResponse.ContactDetail> linkContacts(
            Deal deal,
            Account account,
            List<DealCreateRequest.ContactInput> inputs,
            Long currentUserId
    ) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }

        User user = userRepository.getReferenceById(currentUserId);
        List<DealCreateResponse.ContactDetail> result = new ArrayList<>(inputs.size());

        for (DealCreateRequest.ContactInput input : inputs) {
            Contact contact = input.isExisting()
                    ? contactService.getByIdAndAccount(input.contactId(), account.getAccountId())
                    : contactService.createDummy(account);

            dealContactRepository.save(DealContact.builder()
                    .deal(deal)
                    .contact(contact)
                    .user(user)
                    .build());

            result.add(DealCreateResponse.ContactDetail.from(contact));
        }
        return result;
    }

    private CustomUserDetails currentUser() {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
            return me;
    }
}
