package com.ssafy.fint.domain.deal.repository;

import com.ssafy.fint.domain.deal.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query("""
            select d from Deal d
            join d.account a
            join a.user u
            where d.dealId = :dealId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Deal> findByIdAndTenantId(@Param("dealId") Long dealId, @Param("tenantId") Long tenantId);
}
