package com.management.managementapi.repository.attendance;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.Holiday;

@Repository
public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    /** Intervalo fechado nos dois extremos, como todas as datas deste módulo. */
    List<Holiday> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);

    List<Holiday> findAllByOrderByDateAsc();
}
