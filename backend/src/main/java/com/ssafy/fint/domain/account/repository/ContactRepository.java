package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, Long> {

    @Query("""
            select c from Contact c
            join c.account a
            where c.contactId = :contactId
              and a.accountId = :accountId
              and c.isDeleted = false
            """)
    Optional<Contact> findByIdAndAccount(
            @Param("contactId") Long contactId,
            @Param("accountId") Long accountId
    );

    List<Contact> findAllByAccount_AccountId(Long accountId);

    Optional<Contact> findByContactId(Long contactId);
}
