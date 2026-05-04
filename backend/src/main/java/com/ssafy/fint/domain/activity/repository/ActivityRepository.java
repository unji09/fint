package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository
        extends JpaRepository<Activity, Long>, ActivityRepositoryCustom {
}
