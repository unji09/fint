package com.ssafy.fint.domain.deal.repository;

import com.ssafy.fint.domain.deal.entity.Deal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query("""
            select distinct d from Deal d
            join d.account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where d.dealId = :dealId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Deal> findByIdAndTenantId(@Param("dealId") Long dealId, @Param("tenantId") Long tenantId);

    @Query(value = """
            select d from Deal d
            where exists (
                select 1 from AccountUserAssignment aua
                where aua.account = d.account
                  and aua.user.tenant.tenantId = :tenantId
                  and aua.user.isDeleted = false
            )
              and (:accountId is null or d.account.accountId = :accountId)
            """,
            countQuery = """
            select count(d) from Deal d
            where exists (
                select 1 from AccountUserAssignment aua
                where aua.account = d.account
                  and aua.user.tenant.tenantId = :tenantId
                  and aua.user.isDeleted = false
            )
              and (:accountId is null or d.account.accountId = :accountId)
            """)
    Page<Deal> findAllByTenant(@Param("tenantId") Long tenantId,
                               @Param("accountId") Long accountId,
                               Pageable pageable);

    @Query(value = """
            select d from Deal d
            where d.team.teamId = :teamId
              and (:accountId is null or d.account.accountId = :accountId)
            """,
            countQuery = """
            select count(d) from Deal d
            where d.team.teamId = :teamId
              and (:accountId is null or d.account.accountId = :accountId)
            """)
    Page<Deal> findAllByTeam(@Param("teamId") Long teamId,
                             @Param("accountId") Long accountId,
                             Pageable pageable);
}
