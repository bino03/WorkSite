package com.management.managementapi.repository.attendance;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.RetiredClockCodeAttempt;

@Repository
public interface RetiredClockCodeAttemptRepository extends JpaRepository<RetiredClockCodeAttempt, UUID> {

    boolean existsByRetiredCodeIdAndProfileIdAndAttemptedOn(UUID retiredCodeId, UUID profileId, LocalDate attemptedOn);
}
