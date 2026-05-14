package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.AccountDartDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountDartDisclosureRepository extends JpaRepository<AccountDartDisclosure, Long> {

    boolean existsByAccount_AccountIdAndDartDisclosure_DartDisclosureId(Long accountId, Long dartDisclosureId);
}
