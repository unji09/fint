package com.ssafy.fint.domain.contact.repository;


import com.ssafy.fint.domain.contact.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {


    List<Contact> findAllByAccountIdAndIsDeletedFalse(Long accountId);
    Optional<Contact> findByContactIdAndIsDeletedFalse(Long contactId);

}
