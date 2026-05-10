package com.ssafy.fint.domain.deal.repository;

import com.ssafy.fint.domain.deal.entity.DealContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DealContactRepository extends JpaRepository<DealContact, Long> {

    @Query("""
            select dc from DealContact dc
            join fetch dc.contact c
            where dc.deal.dealId = :dealId
            """)
    List<DealContact> findAllByDealId(@Param("dealId") Long dealId);

    @Query("""
            select dc from DealContact dc
            join fetch dc.user u
            where dc.deal.dealId in :dealIds
            """)
    List<DealContact> findAllByDealIdIn(@Param("dealIds") List<Long> dealIds);
}
