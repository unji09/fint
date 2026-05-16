package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Recording;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecordingRepository extends JpaRepository<Recording, Long> {

    List<Recording> findByActivity_ActivityIdAndTenantIdOrderByCreatedAtDesc(Long activityId, Long tenantId);

    @Query("""
            select r from Recording r
            join fetch r.activity a
            where r.recordingId = :recordingId
              and r.tenantId = :tenantId
            """)
    Optional<Recording> findByIdAndTenantId(@Param("recordingId") Long recordingId,
                                             @Param("tenantId") Long tenantId);
}
