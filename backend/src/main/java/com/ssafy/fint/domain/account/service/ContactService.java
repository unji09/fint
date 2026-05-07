package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.contact.dto.request.ContactCreateRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactUpdateRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactCreateResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactListResponse;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    // TODO(SPEC): 담당자 등록 명세 확정 후 createDummy 를 제거하고
    //             정식 create(name/title/phone/email/personality/source) 메서드로 교체.
    private static final String DUMMY_CONTACT_NAME = "신규 담당자";

    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;

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
