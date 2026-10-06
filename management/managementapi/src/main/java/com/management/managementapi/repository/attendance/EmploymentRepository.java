package com.management.managementapi.repository.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.Employment;

@Repository
public interface EmploymentRepository extends JpaRepository<Employment, UUID> {

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule" })
    Optional<Employment> findByProfileId(UUID profileId);

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule" })
    Optional<Employment> findWithTermsById(UUID id);

    @EntityGraph(attributePaths = { "terms", "terms.workSchedule", "profile" })
    List<Employment> findAllByOrderByHiredAtDesc();

    boolean existsByProfileId(UUID profileId);
}
