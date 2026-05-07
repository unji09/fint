package com.ssafy.fint.domain.ai.repository;

import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    @Query("""
            select distinct s from AiSuggestion s
            join s.account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where a.accountId = :accountId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            order by s.createdAt desc
            """)
    List<AiSuggestion> findAllByAccountIdAndTenantId(
            @Param("accountId") Long accountId,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select s from AiSuggestion s
            join s.account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where s.aiSuggestionId = :suggestionId
              and a.accountId = :accountId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<AiSuggestion> findByIdAndAccountIdAndTenantId(
            @Param("suggestionId") Long suggestionId,
            @Param("accountId") Long accountId,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select s from AiSuggestion s
            join fetch s.account a
            join fetch s.pipelineStage
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where u.userId = :userId
              and u.isDeleted = false
              and s.isRead = false
            order by s.createdAt desc
            """)
    List<AiSuggestion> findUnreadByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
