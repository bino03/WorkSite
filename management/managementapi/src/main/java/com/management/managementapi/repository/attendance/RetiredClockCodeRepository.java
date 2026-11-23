package com.management.managementapi.repository.attendance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.RetiredClockCode;

@Repository
public interface RetiredClockCodeRepository extends JpaRepository<RetiredClockCode, UUID> {

    @EntityGraph(attributePaths = { "enterprise" })
    Optional<RetiredClockCode> findByCode(String code);
}
