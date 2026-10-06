package com.management.managementapi.repository.attendance;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.TimeEntryRevision;

@Repository
public interface TimeEntryRevisionRepository extends JpaRepository<TimeEntryRevision, UUID> {

    List<TimeEntryRevision> findByTimeEntryIdOrderByCreatedAtDesc(UUID timeEntryId);
}
