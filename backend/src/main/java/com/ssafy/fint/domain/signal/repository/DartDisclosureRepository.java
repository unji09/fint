package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.DartDisclosure;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DartDisclosureRepository extends JpaRepository<DartDisclosure, Long> {

    Optional<DartDisclosure> findByRceptNo(String rceptNo);

    @Query("SELECT d FROM DartDisclosure d " +
           "JOIN AccountDartDisclosure ad ON ad.dartDisclosure = d " +
           "WHERE ad.account.accountId = :accountId " +
           "ORDER BY d.rceptDt DESC")
    List<DartDisclosure> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);
}
