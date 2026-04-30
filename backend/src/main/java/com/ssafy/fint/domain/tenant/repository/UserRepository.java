package com.ssafy.fint.domain.tenant.repository;

import com.ssafy.fint.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByTenant_IdAndEmpNo(Long tenantId, String empNo);
}
