package com.management.managementapi.repository.attendance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.WorkSchedule;

@Repository
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, UUID> {

    @EntityGraph(attributePaths = "days")
    Optional<WorkSchedule> findByIdAndDeletedAtIsNull(UUID id);

    Page<WorkSchedule> findByDeletedAtIsNullOrderByNameAsc(Pageable pageable);

    List<WorkSchedule> findByDeletedAtIsNotNullOrderByDeletedAtDesc();

    boolean existsByNameIgnoreCaseAndDeletedAtIsNull(String name);

    boolean existsByNameIgnoreCaseAndDeletedAtIsNullAndIdNot(String name, UUID id);
}
