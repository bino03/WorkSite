package com.management.managementapi.repository.attendance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.EmploymentTerm;

@Repository
public interface EmploymentTermRepository extends JpaRepository<EmploymentTerm, UUID> {

    /**
     * Um horário referenciado por qualquer período — mesmo um já fechado — não pode
     * ser alterado: os meses desse período têm de continuar a recalcular-se com o
     * horário que estava em vigor.
     */
    boolean existsByWorkScheduleId(UUID workScheduleId);
}
