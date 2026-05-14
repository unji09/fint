package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.DartDisclosure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DartDisclosureRepository extends JpaRepository<DartDisclosure, Long> {

    Optional<DartDisclosure> findByRceptNo(String rceptNo);
}
