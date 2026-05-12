package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealListResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.DealContact;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DealService.findList 단위 테스트")
class DealServiceListTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_TEAM_ID = 100L;
    private static final Long ACCOUNT_ID = 50L;
    private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(
            0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "dealId"));

    @Mock private DealRepository dealRepository;
    @Mock private DealContactRepository dealContactRepository;
    @Mock private UserRepository userRepository;

    @Mock @SuppressWarnings("unused") private AccountRepository accountRepository;
    @Mock @SuppressWarnings("unused") private ContactService contactService;
    @Mock @SuppressWarnings("unused") private TeamRepository teamRepository;
    @Mock @SuppressWarnings("unused") private PipelineStageRepository pipelineStageRepository;
    @Mock @SuppressWarnings("unused") private ActivityRepository activityRepository;

    @InjectMocks
    private DealService dealService;

    private CustomUserDetails me(String role) {
        return new CustomUserDetails(CURRENT_USER_ID, CURRENT_TENANT_ID, role);
    }

    private User callerWithTeam(Long teamId) {
        User caller = mock(User.class);
        if (teamId == null) {
            lenient().when(caller.getTeam()).thenReturn(null);
        } else {
            Team team = mock(Team.class);
            lenient().when(team.getTeamId()).thenReturn(teamId);
            lenient().when(caller.getTeam()).thenReturn(team);
        }
        return caller;
    }

    private Deal dealMock(Long dealId) {
        Deal deal = mock(Deal.class);
        given(deal.getDealId()).willReturn(dealId);
        given(deal.getTitle()).willReturn("딜-" + dealId);
        given(deal.getAmount()).willReturn(BigDecimal.ONE);
        given(deal.getExpectedClose()).willReturn(LocalDate.of(2026, 6, 30));
        return deal;
    }

    private DealContact dealContactMock(Long dealId, Long userId, String userName) {
        Deal deal = mock(Deal.class);
        given(deal.getDealId()).willReturn(dealId);

        User user = mock(User.class);
        given(user.getUserId()).willReturn(userId);
        given(user.getName()).willReturn(userName);

        DealContact dc = mock(DealContact.class);
        given(dc.getDeal()).willReturn(deal);
        given(dc.getUser()).willReturn(user);
        return dc;
    }

    private Page<Deal> pageOf(List<Deal> deals, long totalElements) {
        return new PageImpl<>(deals, DEFAULT_PAGEABLE, totalElements);
    }

    @Nested
    @DisplayName("권한별 조회 범위")
    class ScopeByRole {

        @Test
        @DisplayName("ADMIN 은 findAllByTenant 가 호출되고 accountId 가 그대로 전달된다")
        void admin_은_tenant_전체_조회() {
            User caller = callerWithTeam(CURRENT_TEAM_ID);
            Deal deal = dealMock(1L);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, ACCOUNT_ID, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(anyList())).willReturn(List.of());

            DealListResponse response = dealService.findList(me("ADMIN"), ACCOUNT_ID, null, DEFAULT_PAGEABLE);

            assertThat(response.data()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(1L);
            verify(dealRepository).findAllByTenant(CURRENT_TENANT_ID, ACCOUNT_ID, null, DEFAULT_PAGEABLE);
            verify(dealRepository, never()).findAllByTeam(anyLong(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("MEMBER + team 보유 시 findAllByTeam 이 호출된다 (MANAGER 도 동일 분기)")
        void member_team_보유는_team_조회() {
            User caller = callerWithTeam(CURRENT_TEAM_ID);
            Deal deal = dealMock(1L);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTeam(CURRENT_TEAM_ID, ACCOUNT_ID, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(anyList())).willReturn(List.of());

            dealService.findList(me("MEMBER"), ACCOUNT_ID, null, DEFAULT_PAGEABLE);

            verify(dealRepository).findAllByTeam(CURRENT_TEAM_ID, ACCOUNT_ID, null, DEFAULT_PAGEABLE);
            verify(dealRepository, never()).findAllByTenant(anyLong(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("MEMBER + team 미보유 시 findAllByTenant 로 fallback 된다 (accountId=null 전파)")
        void member_team_없으면_tenant_전체_조회() {
            User caller = callerWithTeam(null);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(), 0L));

            dealService.findList(me("MEMBER"), null, null, DEFAULT_PAGEABLE);

            verify(dealRepository).findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE);
            verify(dealRepository, never()).findAllByTeam(anyLong(), any(), any(), any(Pageable.class));
        }

        @Test
        @DisplayName("contactId 지정 시 findAllByTenant 호출에 contactId 가 그대로 전달된다")
        void contactId_지정시_tenant_쿼리에_전달() {
            Long contactId = 77L;
            User caller = callerWithTeam(null);
            Deal deal = dealMock(1L);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, contactId, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(anyList())).willReturn(List.of());

            DealListResponse response = dealService.findList(me("ADMIN"), null, contactId, DEFAULT_PAGEABLE);

            assertThat(response.data()).hasSize(1);
            verify(dealRepository).findAllByTenant(CURRENT_TENANT_ID, null, contactId, DEFAULT_PAGEABLE);
        }

        @Test
        @DisplayName("contactId 지정 시 findAllByTeam 호출에 contactId 가 그대로 전달된다")
        void contactId_지정시_team_쿼리에_전달() {
            Long contactId = 88L;
            User caller = callerWithTeam(CURRENT_TEAM_ID);
            Deal deal = dealMock(2L);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTeam(CURRENT_TEAM_ID, ACCOUNT_ID, contactId, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(anyList())).willReturn(List.of());

            dealService.findList(me("MEMBER"), ACCOUNT_ID, contactId, DEFAULT_PAGEABLE);

            verify(dealRepository).findAllByTeam(CURRENT_TEAM_ID, ACCOUNT_ID, contactId, DEFAULT_PAGEABLE);
        }
    }

    @Nested
    @DisplayName("assignees 매핑")
    class AssigneesMapping {

        @Test
        @DisplayName("같은 userId 가 여러 contact 로 들어와도 userId 기준 첫 entry 만 살아남는다")
        void userId_기준_중복_제거_첫번째_유지() {
            User caller = callerWithTeam(null);
            Deal deal = dealMock(1L);
            DealContact dcFirst = dealContactMock(1L, 10L, "홍길동");
            DealContact dcSecond = dealContactMock(1L, 10L, "홍 길동");
            DealContact dcOther = dealContactMock(1L, 11L, "이몽룡");

            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(List.of(1L)))
                    .willReturn(List.of(dcFirst, dcSecond, dcOther));

            DealListResponse response = dealService.findList(me("MEMBER"), null, null, DEFAULT_PAGEABLE);

            List<DealListResponse.DealAssignee> assignees = response.data().get(0).assignees();
            assertThat(assignees)
                    .hasSize(2)
                    .extracting(DealListResponse.DealAssignee::userId, DealListResponse.DealAssignee::name)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.api.Assertions.tuple(10L, "홍길동"),
                            org.assertj.core.api.Assertions.tuple(11L, "이몽룡"));
        }

        @Test
        @DisplayName("dealContact 매핑이 없는 deal 의 assignees 는 빈 리스트로 응답된다")
        void deal에_매핑된_contact_없으면_빈_assignees() {
            User caller = callerWithTeam(null);
            Deal deal1 = dealMock(1L);
            Deal deal2 = dealMock(2L);
            DealContact dc = dealContactMock(1L, 10L, "홍길동");

            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal1, deal2), 2L));
            given(dealContactRepository.findAllByDealIdIn(List.of(1L, 2L)))
                    .willReturn(List.of(dc));

            DealListResponse response = dealService.findList(me("MEMBER"), null, null, DEFAULT_PAGEABLE);

            DealListResponse.DealSummary deal2Summary = response.data().stream()
                    .filter(s -> s.dealId().equals(2L))
                    .findFirst()
                    .orElseThrow();
            assertThat(deal2Summary.assignees()).isEmpty();
        }
    }

    @Nested
    @DisplayName("페이지네이션 / 응답 매핑")
    class ResponseMapping {

        @Test
        @DisplayName("page.totalElements 가 응답 totalElements 로 매핑된다")
        void totalElements_매핑() {
            User caller = callerWithTeam(null);
            Deal deal = dealMock(1L);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 137L));
            given(dealContactRepository.findAllByDealIdIn(anyList())).willReturn(List.of());

            DealListResponse response = dealService.findList(me("ADMIN"), null, null, DEFAULT_PAGEABLE);

            assertThat(response.data()).hasSize(1);
            assertThat(response.totalElements()).isEqualTo(137L);
        }

        @Test
        @DisplayName("DealSummary 필드가 Deal 의 값으로 정확히 매핑된다")
        void summary_필드_매핑_검증() {
            User caller = callerWithTeam(null);
            Deal deal = mock(Deal.class);
            given(deal.getDealId()).willReturn(7L);
            given(deal.getTitle()).willReturn("삼성 1차 제안");
            given(deal.getAmount()).willReturn(new BigDecimal("12345.67"));
            given(deal.getExpectedClose()).willReturn(LocalDate.of(2026, 12, 31));

            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(deal), 1L));
            given(dealContactRepository.findAllByDealIdIn(List.of(7L)))
                    .willReturn(List.of());

            DealListResponse response = dealService.findList(me("ADMIN"), null, null, DEFAULT_PAGEABLE);

            DealListResponse.DealSummary s = response.data().get(0);
            assertThat(s.dealId()).isEqualTo(7L);
            assertThat(s.title()).isEqualTo("삼성 1차 제안");
            assertThat(s.amount()).isEqualByComparingTo("12345.67");
            assertThat(s.expectedClose()).isEqualTo(LocalDate.of(2026, 12, 31));
        }
    }

    @Nested
    @DisplayName("경계 케이스")
    class EdgeCases {

        @Test
        @DisplayName("deal 0 건이면 dealContactRepository 호출 없이 빈 리스트가 반환된다")
        void deal_0건이면_contact_조회_안함() {
            User caller = callerWithTeam(null);
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.of(caller));
            given(dealRepository.findAllByTenant(CURRENT_TENANT_ID, null, null, DEFAULT_PAGEABLE))
                    .willReturn(pageOf(List.of(), 0L));

            DealListResponse response = dealService.findList(me("ADMIN"), null, null, DEFAULT_PAGEABLE);

            assertThat(response.data()).isEmpty();
            assertThat(response.totalElements()).isZero();
            verifyNoInteractions(dealContactRepository);
        }

        @Test
        @DisplayName("사용자 조회 실패 시 INVALID_TOKEN 예외가 발생하고 repository 는 호출되지 않는다")
        void 사용자_조회_실패시_INVALID_TOKEN() {
            given(userRepository.findById(CURRENT_USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.findList(me("ADMIN"), null, null, DEFAULT_PAGEABLE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            verifyNoInteractions(dealRepository);
            verifyNoInteractions(dealContactRepository);
        }
    }
}
