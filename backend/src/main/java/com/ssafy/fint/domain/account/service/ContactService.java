package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Contact;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    // TODO(SPEC): 담당자 등록 명세 확정 후 createDummy 를 제거하고
    //             정식 create(name/title/phone/email/personality/source) 메서드로 교체.
    private static final String DUMMY_CONTACT_NAME = "신규 담당자";

    private final ContactRepository contactRepository;

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
}
