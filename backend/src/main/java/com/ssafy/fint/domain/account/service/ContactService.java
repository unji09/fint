package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.contact.dto.request.ContactCreateRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactOcrInitRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactUpdateRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactCreateResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactListResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrInitResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactOcrInitResult;
import com.ssafy.fint.domain.contact.event.ContactCreatedEvent;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    // TODO(SPEC): 담당자 등록 명세 확정 후 createDummy 를 제거하고
    //             정식 create(name/title/phone/email/personality/source) 메서드로 교체.
    private static final String DUMMY_CONTACT_NAME = "신규 담당자";
    private static final String DEFAULT_INDUSTRY = "미분류";

    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final AccountUserAssignmentRepository accountUserAssignmentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public Contact getByIdAndAccount(Long contactId, Long accountId) {
        return contactRepository.findByIdAndAccount(contactId, accountId)
                .orElseThrow(() -> new BusinessException(AccountErrorCode.CONTACT_NOT_FOUND));
    }

    @Transactional
    public Contact createDummy(Account account) {
        Contact contact = Contact.builder()
                .account(account)
                .name(DUMMY_CONTACT_NAME)
                .build();
        Contact saved = contactRepository.save(contact);
        log.debug("[ContactCreateDummy] contactId={} accountId={}", saved.getContactId(), account.getAccountId());
        return saved;
    }

    @Transactional
    public void updateContact(Long contactId, ContactUpdateRequest request) {
        Contact contact = contactRepository.findByContactId(contactId)
            .orElseThrow(() -> new BusinessException(AccountErrorCode.CONTACT_NOT_FOUND));

        contact.update(
            request.getName(),
            request.getTitle(),
            request.getPhone(),
            request.getEmail(),
            request.getPersonality()
        );
    }

    @Transactional
    public void deleteContact(Long contactId) {
        Contact contact = contactRepository.findByContactId(contactId)
            .orElseThrow(() -> new BusinessException(AccountErrorCode.CONTACT_NOT_FOUND));

        contact.softDelete();
    }

    public List<ContactListResponse> getContacts(Long accountId) {
        return contactRepository.findAllByAccount_AccountId(accountId)
            .stream()
            .map(ContactListResponse::from)
            .toList();
    }

    @Transactional
    public ContactOcrInitResult initContactByOcr(CustomUserDetails me, ContactOcrInitRequest request) {
        Long tenantId = me.getTenantId();

        AccountLookupResult accountResult = findOrCreateAccount(request.getAccountName(), tenantId, me.getUserId());
        Account account = accountResult.account();

        Optional<Contact> existing = findDuplicate(account.getAccountId(), request);
        if (existing.isPresent()) {
            log.info("[ContactOcrInit] duplicate found contactId={} accountId={}",
                    existing.get().getContactId(), account.getAccountId());
            return new ContactOcrInitResult(ContactOcrInitResponse.from(existing.get()), false);
        }

        Contact contact = Contact.builder()
                .account(account)
                .name(request.getName())
                .title(request.getTitle())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();
        Contact saved = contactRepository.save(contact);
        log.info("[ContactOcrInit] created contactId={} accountId={}", saved.getContactId(), account.getAccountId());
        if (accountResult.created()) {
            eventPublisher.publishEvent(new ContactCreatedEvent(tenantId, account.getAccountId(), saved.getContactId()));
        }
        return new ContactOcrInitResult(ContactOcrInitResponse.from(saved), true);
    }

    private AccountLookupResult findOrCreateAccount(String accountName, Long tenantId, Long userId) {
        List<Account> accounts = accountRepository.findByNameAndTenantId(accountName, tenantId);
        if (!accounts.isEmpty()) {
            return new AccountLookupResult(accounts.getFirst(), false);
        }

        Account saved = accountRepository.save(
                Account.builder()
                        .name(accountName)
                        .industry(DEFAULT_INDUSTRY)
                        .build());

        User owner = userRepository.getReferenceById(userId);
        accountUserAssignmentRepository.save(
                AccountUserAssignment.builder().account(saved).user(owner).build());

        log.info("[ContactOcrInit] new account accountId={} name={}", saved.getAccountId(), accountName);
        return new AccountLookupResult(saved, true);
    }

    record AccountLookupResult(Account account, boolean created) {
    }

    private Optional<Contact> findDuplicate(Long accountId, ContactOcrInitRequest request) {
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            Optional<Contact> byEmail = contactRepository.findFirstByAccount_AccountIdAndEmail(accountId, request.getEmail());
            if (byEmail.isPresent()) return byEmail;
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            Optional<Contact> byPhone = contactRepository.findFirstByAccount_AccountIdAndPhone(accountId, request.getPhone());
            if (byPhone.isPresent()) return byPhone;
        }

        return contactRepository.findFirstByAccount_AccountIdAndName(accountId, request.getName());
    }

    @Transactional
    public ContactCreateResponse createContact(ContactCreateRequest request) {
        Account account = accountRepository.findById(request.getAccountId())
            .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Contact contact = Contact.builder()
            .account(account)
            .name(request.getName())
            .title(request.getTitle())
            .phone(request.getPhone())
            .email(request.getEmail())
            .personality(request.getPersonality())
            .build();

        return ContactCreateResponse.from(contactRepository.save(contact));
    }
}
