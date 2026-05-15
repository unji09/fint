package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.contact.dto.request.ContactOcrInitRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrInitResult;
import com.ssafy.fint.domain.contact.event.ContactCreatedEvent;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContactService OCR 기반 담당자 등록 단위 테스트")
class ContactServiceOcrInitTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long CONTACT_ID = 100L;

    @Mock private ContactRepository contactRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountUserAssignmentRepository accountUserAssignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ContactService contactService;

    @Nested
    @DisplayName("신규 고객사 + 신규 담당자")
    class NewAccountNewContact {

        @Test
        @DisplayName("고객사가 없으면 신규 고객사와 담당자를 생성하고 created=true 를 반환한다")
        void 신규_고객사_신규_담당자_생성() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", "010-1234-5678", "kim@samsung.com");

            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of());

            Account savedAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.save(any(Account.class))).willReturn(savedAccount);
            given(userRepository.getReferenceById(USER_ID)).willReturn(mock(User.class));

            given(contactRepository.findFirstByAccount_AccountIdAndEmail(ACCOUNT_ID, "kim@samsung.com"))
                    .willReturn(Optional.empty());
            given(contactRepository.findFirstByAccount_AccountIdAndPhone(ACCOUNT_ID, "010-1234-5678"))
                    .willReturn(Optional.empty());
            given(contactRepository.findFirstByAccount_AccountIdAndName(ACCOUNT_ID, "김철수"))
                    .willReturn(Optional.empty());

            Contact savedContact = mockContact(CONTACT_ID, savedAccount, "김철수", "팀장", "010-1234-5678", "kim@samsung.com");
            given(contactRepository.save(any(Contact.class))).willReturn(savedContact);

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isTrue();
            assertThat(result.response().contactId()).isEqualTo(CONTACT_ID);
            assertThat(result.response().accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(result.response().name()).isEqualTo("김철수");
            verify(accountRepository).save(any(Account.class));
            verify(accountUserAssignmentRepository).save(any(AccountUserAssignment.class));
            verify(contactRepository).save(any(Contact.class));

            ArgumentCaptor<ContactCreatedEvent> captor = ArgumentCaptor.forClass(ContactCreatedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            ContactCreatedEvent event = captor.getValue();
            assertThat(event.tenantId()).isEqualTo(TENANT_ID);
            assertThat(event.accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(event.contactId()).isEqualTo(CONTACT_ID);
        }
    }

    @Nested
    @DisplayName("기존 고객사 + 신규 담당자")
    class ExistingAccountNewContact {

        @Test
        @DisplayName("고객사가 존재하고 담당자가 없으면 신규 담당자만 생성하고 created=true 를 반환한다")
        void 기존_고객사_신규_담당자_생성() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", "010-1234-5678", "kim@samsung.com");

            Account existingAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of(existingAccount));

            given(contactRepository.findFirstByAccount_AccountIdAndEmail(ACCOUNT_ID, "kim@samsung.com"))
                    .willReturn(Optional.empty());
            given(contactRepository.findFirstByAccount_AccountIdAndPhone(ACCOUNT_ID, "010-1234-5678"))
                    .willReturn(Optional.empty());
            given(contactRepository.findFirstByAccount_AccountIdAndName(ACCOUNT_ID, "김철수"))
                    .willReturn(Optional.empty());

            Contact savedContact = mockContact(CONTACT_ID, existingAccount, "김철수", "팀장", "010-1234-5678", "kim@samsung.com");
            given(contactRepository.save(any(Contact.class))).willReturn(savedContact);

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isTrue();
            assertThat(result.response().accountId()).isEqualTo(ACCOUNT_ID);
            verify(accountRepository, never()).save(any(Account.class));
            verify(contactRepository).save(any(Contact.class));
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("기존 고객사 + 중복 담당자")
    class ExistingAccountDuplicateContact {

        @Test
        @DisplayName("email 이 일치하는 담당자가 있으면 기존 담당자를 반환하고 created=false")
        void email_중복이면_기존_담당자_반환() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", "010-1234-5678", "kim@samsung.com");

            Account existingAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of(existingAccount));

            Contact existingContact = mockContact(CONTACT_ID, existingAccount, "김철수", "팀장", "010-1234-5678", "kim@samsung.com");
            given(contactRepository.findFirstByAccount_AccountIdAndEmail(ACCOUNT_ID, "kim@samsung.com"))
                    .willReturn(Optional.of(existingContact));

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isFalse();
            assertThat(result.response().contactId()).isEqualTo(CONTACT_ID);
            verify(contactRepository, never()).save(any(Contact.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("email 없이 phone 이 일치하는 담당자가 있으면 기존 담당자를 반환하고 created=false")
        void phone_중복이면_기존_담당자_반환() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", "010-1234-5678", null);

            Account existingAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of(existingAccount));

            Contact existingContact = mockContact(CONTACT_ID, existingAccount, "김철수", "팀장", "010-1234-5678", null);
            given(contactRepository.findFirstByAccount_AccountIdAndPhone(ACCOUNT_ID, "010-1234-5678"))
                    .willReturn(Optional.of(existingContact));

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isFalse();
            assertThat(result.response().contactId()).isEqualTo(CONTACT_ID);
            verify(contactRepository, never()).save(any(Contact.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("email/phone 없이 name 이 일치하는 담당자가 있으면 기존 담당자를 반환하고 created=false")
        void name_중복이면_기존_담당자_반환() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", null, null);

            Account existingAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of(existingAccount));

            Contact existingContact = mockContact(CONTACT_ID, existingAccount, "김철수", "팀장", null, null);
            given(contactRepository.findFirstByAccount_AccountIdAndName(ACCOUNT_ID, "김철수"))
                    .willReturn(Optional.of(existingContact));

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isFalse();
            assertThat(result.response().contactId()).isEqualTo(CONTACT_ID);
            verify(contactRepository, never()).save(any(Contact.class));
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        @DisplayName("email 불일치 + phone 일치 시 기존 담당자를 반환하고 created=false")
        void email_불일치_phone_일치시_기존_담당자_반환() {
            CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            ContactOcrInitRequest request = createRequest("삼성물산", "김철수", "팀장", "010-1234-5678", "new@samsung.com");

            Account existingAccount = mockAccount(ACCOUNT_ID, "삼성물산");
            given(accountRepository.findByNameAndTenantId("삼성물산", TENANT_ID))
                    .willReturn(List.of(existingAccount));

            given(contactRepository.findFirstByAccount_AccountIdAndEmail(ACCOUNT_ID, "new@samsung.com"))
                    .willReturn(Optional.empty());

            Contact existingContact = mockContact(CONTACT_ID, existingAccount, "김철수", "팀장", "010-1234-5678", "old@samsung.com");
            given(contactRepository.findFirstByAccount_AccountIdAndPhone(ACCOUNT_ID, "010-1234-5678"))
                    .willReturn(Optional.of(existingContact));

            ContactOcrInitResult result = contactService.initContactByOcr(me, request);

            assertThat(result.created()).isFalse();
            assertThat(result.response().contactId()).isEqualTo(CONTACT_ID);
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    // --- helper methods ---

    private ContactOcrInitRequest createRequest(String accountName, String name,
                                                String title, String phone, String email) {
        ContactOcrInitRequest request = new ContactOcrInitRequest();
        ReflectionTestUtils.setField(request, "accountName", accountName);
        ReflectionTestUtils.setField(request, "name", name);
        ReflectionTestUtils.setField(request, "title", title);
        ReflectionTestUtils.setField(request, "phone", phone);
        ReflectionTestUtils.setField(request, "email", email);
        return request;
    }

    private Account mockAccount(Long accountId, String name) {
        Account account = mock(Account.class);
        given(account.getAccountId()).willReturn(accountId);
        return account;
    }

    private Contact mockContact(Long contactId, Account account,
                                String name, String title, String phone, String email) {
        Contact contact = mock(Contact.class);
        given(contact.getContactId()).willReturn(contactId);
        given(contact.getAccount()).willReturn(account);
        given(contact.getName()).willReturn(name);
        given(contact.getTitle()).willReturn(title);
        given(contact.getPhone()).willReturn(phone);
        given(contact.getEmail()).willReturn(email);
        given(contact.getCreatedAt()).willReturn(OffsetDateTime.now());
        return contact;
    }
}