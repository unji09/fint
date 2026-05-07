package com.ssafy.fint.domain.contact.service;

import com.ssafy.fint.domain.contact.dto.request.ContactCreateRequest;
import com.ssafy.fint.domain.contact.dto.request.ContactUpdateRequest;
import com.ssafy.fint.domain.contact.dto.response.ContactCreateResponse;
import com.ssafy.fint.domain.contact.dto.response.ContactListResponse;
import com.ssafy.fint.domain.contact.entity.Contact;
import com.ssafy.fint.domain.contact.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContactService {

    private final ContactRepository contactRepository;

    @Transactional
    public ContactCreateResponse createContact(ContactCreateRequest request) {
        Contact contact = Contact.builder()
            .accountId(request.getAccountId())
            .name(request.getName())
            .title(request.getTitle())
            .phone(request.getPhone())
            .email(request.getEmail())
            .personality(request.getPersonality())
            .build();

        return ContactCreateResponse.from(contactRepository.save(contact));
    }

    public List<ContactListResponse> getContacts(Long accountId) {
        return contactRepository.findAllByAccountIdAndIsDeletedFalse(accountId)
            .stream()
            .map(ContactListResponse::from)
            .toList();
    }

    @Transactional
    public void updateContact(Long contactId, ContactUpdateRequest request) {
        Contact contact = contactRepository.findByContactIdAndIsDeletedFalse(contactId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 담당자입니다."));

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
        Contact contact = contactRepository.findByContactIdAndIsDeletedFalse(contactId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 담당자입니다."));

        contact.softDelete();
    }
}
