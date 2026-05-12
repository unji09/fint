package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.DealContact;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DealService.create 단위 테스트")
class DealServiceCreateTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long TEAM_ID = 100L;
    private static final Long NEW_DEAL_ID = 42L;
    private static final Long CONTACT_ID_1 = 201L;

    @Mock private DealRepository dealRepository;
    @Mock private DealContactRepository dealContactRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ContactService contactService;
    @Mock private TeamRepository teamRepository;
    @Mock private UserRepository userRepository;

    @Mock @SuppressWarnings("unused") private PipelineStageRepository pipelineStageRepository;
    @Mock @SuppressWarnings("unused") private ActivityRepository activityRepository;

    @InjectMocks
    private DealService dealService;

    private CustomUserDetails me() {
        return new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
    }

    @Test
    @DisplayName("team/contacts 없이 생성 → deal INSERT 만 발생하고 contacts 빈 리스트")
    void 최소_입력_생성() {
        Account account = accountMock();
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        stubDealSave();

        DealCreateResponse res = dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, null, "신규 딜", null, null, null));

        assertThat(res.dealId()).isEqualTo(NEW_DEAL_ID);
        assertThat(res.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(res.teamId()).isNull();
        assertThat(res.title()).isEqualTo("신규 딜");
        assertThat(res.probability()).isEqualTo((short) 70);
        assertThat(res.contacts()).isEmpty();

        verify(dealRepository).save(any(Deal.class));
        verifyNoInteractions(teamRepository);
        verifyNoInteractions(contactService);
        verify(dealContactRepository, never()).save(any());
    }

    @Test
    @DisplayName("teamId 제공 시 team 조회 후 deal 에 team 이 설정된다")
    void team_포함_생성() {
        Account account = accountMock();
        Team team = mock(Team.class);
        given(team.getTeamId()).willReturn(TEAM_ID);
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        given(teamRepository.findByTeamIdAndTenant_TenantId(TEAM_ID, TENANT_ID))
                .willReturn(Optional.of(team));
        stubDealSave();

        DealCreateResponse res = dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, TEAM_ID, "팀 딜", null, null, null));

        assertThat(res.teamId()).isEqualTo(TEAM_ID);
        verify(teamRepository).findByTeamIdAndTenant_TenantId(TEAM_ID, TENANT_ID);
    }

    @Test
    @DisplayName("기존 담당자(contactId != null) 연결 시 contactService.getByIdAndAccount 호출")
    void 기존_담당자_연결() {
        Account account = accountMock();
        Contact contact = contactMock(CONTACT_ID_1);
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        stubDealSave();
        given(userRepository.getReferenceById(USER_ID)).willReturn(mock(User.class));
        given(contactService.getByIdAndAccount(CONTACT_ID_1, ACCOUNT_ID)).willReturn(contact);

        DealCreateResponse res = dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, null, "딜", null, null,
                        List.of(new DealCreateRequest.ContactInput(CONTACT_ID_1))));

        assertThat(res.contacts()).hasSize(1);
        assertThat(res.contacts().get(0).contactId()).isEqualTo(CONTACT_ID_1);
        verify(contactService).getByIdAndAccount(CONTACT_ID_1, ACCOUNT_ID);
        verify(dealContactRepository).save(any(DealContact.class));
    }

    @Test
    @DisplayName("신규 담당자(contactId=null) 연결 시 contactService.createDummy 호출")
    void 신규_담당자_더미_생성() {
        Account account = accountMock();
        Contact dummy = contactMock(999L);
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        stubDealSave();
        given(userRepository.getReferenceById(USER_ID)).willReturn(mock(User.class));
        given(contactService.createDummy(account)).willReturn(dummy);

        DealCreateResponse res = dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, null, "딜", null, null,
                        List.of(new DealCreateRequest.ContactInput(null))));

        assertThat(res.contacts()).hasSize(1);
        verify(contactService).createDummy(account);
        verify(contactService, never()).getByIdAndAccount(any(), any());
    }

    @Test
    @DisplayName("같은 contactId 가 중복 입력되면 첫 번째만 처리되고 나머지는 무시된다")
    void 중복_contactId_dedup() {
        Account account = accountMock();
        Contact contact = contactMock(CONTACT_ID_1);
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        stubDealSave();
        given(userRepository.getReferenceById(USER_ID)).willReturn(mock(User.class));
        given(contactService.getByIdAndAccount(CONTACT_ID_1, ACCOUNT_ID)).willReturn(contact);

        DealCreateResponse res = dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, null, "딜", null, null,
                        List.of(
                                new DealCreateRequest.ContactInput(CONTACT_ID_1),
                                new DealCreateRequest.ContactInput(CONTACT_ID_1))));

        assertThat(res.contacts()).hasSize(1);
        verify(contactService, times(1)).getByIdAndAccount(CONTACT_ID_1, ACCOUNT_ID);
        verify(dealContactRepository, times(1)).save(any(DealContact.class));
    }

    @Test
    @DisplayName("accountId 가 존재하지 않으면 ACCOUNT_NOT_FOUND 예외 발생")
    void 고객사_없으면_ACCOUNT_NOT_FOUND() {
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, null, "딜", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);

        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("teamId 가 존재하지 않으면 TEAM_NOT_FOUND 예외 발생")
    void 팀_없으면_TEAM_NOT_FOUND() {
        Account account = accountMock();
        given(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .willReturn(Optional.of(account));
        given(teamRepository.findByTeamIdAndTenant_TenantId(TEAM_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.create(me(),
                new DealCreateRequest(ACCOUNT_ID, TEAM_ID, "딜", null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DealErrorCode.TEAM_NOT_FOUND);

        verify(dealRepository, never()).save(any());
    }

    private Account accountMock() {
        Account account = mock(Account.class);
        lenient().when(account.getAccountId()).thenReturn(ACCOUNT_ID);
        return account;
    }

    private Contact contactMock(Long contactId) {
        Contact contact = mock(Contact.class);
        given(contact.getContactId()).willReturn(contactId);
        given(contact.getName()).willReturn("담당자-" + contactId);
        return contact;
    }

    private void stubDealSave() {
        given(dealRepository.save(any(Deal.class))).willAnswer(inv -> {
            Deal d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "dealId", NEW_DEAL_ID);
            return d;
        });
    }
}
