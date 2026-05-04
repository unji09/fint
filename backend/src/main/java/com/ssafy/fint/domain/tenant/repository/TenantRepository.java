package com.ssafy.fint.domain.tenant.repository;

import com.ssafy.fint.domain.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCompanyCodeAndIsDeletedFalse(String companyCode);
}
