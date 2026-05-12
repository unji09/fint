package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
import com.ssafy.fint.domain.deal.dto.DealListResponse;
import com.ssafy.fint.domain.deal.dto.DealStageResponse;
import com.ssafy.fint.domain.deal.dto.DealListResponse.DealAssignee;
import com.ssafy.fint.domain.deal.dto.DealListResponse.DealSummary;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.dto.DealUpdateResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.DealContact;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DealService {

    // TODO(수정 : DEAL-LLM): LLM 기반 수주 확률 계산 도입 시 이 상수를 LLM 호출 결과로 교체.
    // TODO(수정 : DEAL-새 담당자): 화면- 및 진행흐름 결정될 경우, 기존 담당자 아닐 경우 새 담당자로 추가 로직 완성.
    private static final short DUMMY_PROBABILITY = 84;

    private final DealRepository dealRepository;
    private final DealContactRepository dealContactRepository;
    private final AccountRepository accountRepository;
    private final ContactService contactService;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final ActivityRepository activityRepository;

    @Transactional
    public DealCreateResponse create(CustomUserDetails me, DealCreateRequest request) {
        Long tenantId = me.getTenantId();

        Account account = accountRepository.findByIdAndTenantId(request.accountId(), tenantId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

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

    /**
     * 딜 부분 수정. null 이 아닌 필드만 반영한다.
     * pipelineStageId 가 주어지면 단계 변경까지 동일 트랜잭션에서 처리한다.
     *
     * won/lost 처리 규칙:
     * - lostReason 만 단독으로 들어오면 deal 의 기존 lostAt 을 유지한 채 reason 만 갱신
     */
    @Transactional
    public DealUpdateResponse update(CustomUserDetails me, Long dealId, DealUpdateRequest request) {
        Long tenantId = me.getTenantId();

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.DEAL_NOT_FOUND));

        if (request.title() != null) {
            deal.changeTitle(request.title());
        }
        if (request.amount() != null) {
            deal.changeAmount(request.amount());
        }
        if (request.expectedClose() != null) {
            deal.changeExpectedClose(request.expectedClose());
        }
        if (request.wonAt() != null) {
            deal.markWon(request.wonAt());
        }
        if (request.lostAt() != null) {
            deal.markLost(request.lostAt(), request.lostReason());
        } else if (request.lostReason() != null && deal.getLostAt() != null) {
            deal.markLost(deal.getLostAt(), request.lostReason());
        }
        if (request.pipelineStageId() != null) {
            PipelineStage stage = pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(request.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(DealErrorCode.PIPELINE_STAGE_NOT_FOUND));
            deal.moveToStage(stage.getName());
        }

        log.debug("[DealUpdate] dealId={} tenantId={} pipelineStageId={}",
                dealId, tenantId, request.pipelineStageId());
        return DealUpdateResponse.from(deal);
    }

    @Transactional
    public DealDetailResponse findDetail(CustomUserDetails me, Long dealId) {
        Long tenantId = me.getTenantId();

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.DEAL_NOT_FOUND));

        resolveCurrentStage(deal);

        long meetingCount = activityRepository.countMeetingsByDealId(deal.getDealId());

        List<DealDetailResponse.ContactDetail> contacts = dealContactRepository
                .findAllByDealId(deal.getDealId())
                .stream()
                .map(DealContact::getContact)
                .map(DealDetailResponse.ContactDetail::from)
                .toList();

        return DealDetailResponse.of(deal, meetingCount, contacts);
    }

    @Transactional(readOnly = true)
    public DealListResponse findList(CustomUserDetails me, Long accountId, Long contactId, Pageable pageable) {
        User caller = userRepository.findById(me.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));

        boolean tenantWide = UserRole.ADMIN.name().equals(me.getRole()) || caller.getTeam() == null;

        Page<Deal> page = tenantWide
                ? dealRepository.findAllByTenant(me.getTenantId(), accountId, contactId, pageable)
                : dealRepository.findAllByTeam(caller.getTeam().getTeamId(), accountId, contactId, pageable);

        List<Deal> deals = page.getContent();
        if (deals.isEmpty()) {
            return new DealListResponse(List.of(), page.getTotalElements());
        }

        List<Long> dealIds = deals.stream().map(Deal::getDealId).toList();
        Map<Long, List<DealAssignee>> assigneesByDealId = dealContactRepository
                .findAllByDealIdIn(dealIds).stream()
                .collect(Collectors.groupingBy(
                        dc -> dc.getDeal().getDealId(),
                        Collectors.mapping(
                                dc -> new DealAssignee(dc.getUser().getUserId(), dc.getUser().getName()),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        DealService::dedupByUserId)
                        )
                ));

        List<DealSummary> summaries = deals.stream()
                .map(d -> new DealSummary(
                        d.getDealId(),
                        d.getTitle(),
                        d.getAmount(),
                        d.getExpectedClose(),
                        assigneesByDealId.getOrDefault(d.getDealId(), List.of())
                ))
                .toList();

        return new DealListResponse(summaries, page.getTotalElements());
    }

    private static List<DealAssignee> dedupByUserId(List<DealAssignee> assignees) {
        return assignees.stream()
                .collect(Collectors.toMap(
                        DealAssignee::userId,
                        a -> a,
                        (first, duplicate) -> first,
                        LinkedHashMap::new))
                .values().stream()
                .toList();
    }

    @Transactional
    public DealStageResponse findCurrentStage(CustomUserDetails me, Long dealId) {
        Deal deal = dealRepository.findByIdAndTenantId(dealId, me.getTenantId())
                .orElseThrow(() -> new BusinessException(DealErrorCode.DEAL_NOT_FOUND));
        return resolveCurrentStage(deal);
    }

    @Transactional
    public DealStageResponse resolveCurrentStage(Deal deal) {
        return activityRepository.findLatestPipelineActivityByDealId(deal.getDealId())
            .map(Activity::getPipelineStage)
            .map(stage -> {
                deal.moveToStage(stage.getName());
                return new DealStageResponse(stage.getPipelineStageId(), stage.getName());
            })
            .orElseGet(() -> {
                deal.moveToStage(null);
                return DealStageResponse.EMPTY;
            });
    }

    @Transactional
    public void softDelete(CustomUserDetails me, Long dealId) {
        Long tenantId = me.getTenantId();

        Deal deal = dealRepository.findByIdAndTenantId(dealId, tenantId)
                .orElseThrow(() -> new BusinessException(DealErrorCode.DEAL_NOT_FOUND));

        deal.softDelete();
        log.debug("[DealSoftDelete] dealId={} tenantId={}", dealId, tenantId);
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

        Set<Long> linkedContactIds = new HashSet<>();
        List<DealCreateResponse.ContactDetail> result = new ArrayList<>(inputs.size());

        for (DealCreateRequest.ContactInput input : inputs) {
            if (input.isExisting() && !linkedContactIds.add(input.contactId())) {
                continue;
            }

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
}
