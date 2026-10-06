package com.management.managementapi.repository.attendance;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.management.managementapi.model.attendance.AbsenceDocument;

@Repository
public interface AbsenceDocumentRepository extends JpaRepository<AbsenceDocument, UUID> {
}
