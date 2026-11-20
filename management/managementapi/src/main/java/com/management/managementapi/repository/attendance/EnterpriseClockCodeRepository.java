package com.management.managementapi.repository.attendance;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.EnterpriseClockCode;

@Repository
public interface EnterpriseClockCodeRepository extends JpaRepository<EnterpriseClockCode, UUID> {

    Optional<EnterpriseClockCode> findByEnterpriseId(UUID enterpriseId);

    @EntityGraph(attributePaths = { "enterprise" })
    Optional<EnterpriseClockCode> findByCode(String code);
}
