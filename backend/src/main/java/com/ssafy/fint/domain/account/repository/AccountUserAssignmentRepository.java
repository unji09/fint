package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountUserAssignmentRepository extends JpaRepository<AccountUserAssignment, Long> {
}
