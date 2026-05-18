package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.AccountDartDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountDartDisclosureRepository extends JpaRepository<AccountDartDisclosure, Long> {

    boolean existsByAccount_AccountIdAndDartDisclosure_DartDisclosureId(Long accountId, Long dartDisclosureId);

    @Query("""
            SELECT a FROM AccountDartDisclosure a
            JOIN FETCH a.dartDisclosure
            WHERE a.account.accountId = :accountId
              AND a.dartDisclosure.rceptDt >= :since
            ORDER BY a.dartDisclosure.rceptDt DESC
            """)
    List<AccountDartDisclosure> findRecentByAccountId(
            @Param("accountId") Long accountId,
            @Param("since") String since
    );
}
