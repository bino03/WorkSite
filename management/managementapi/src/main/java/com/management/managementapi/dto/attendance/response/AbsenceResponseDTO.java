package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceStatus;
import com.management.managementapi.model.enums.AbsenceType;

public record AbsenceResponseDTO(
        UUID id,
        UUID profileId,
        String profileName,
        AbsenceType type,
        LocalDate startsOn,
        LocalDate endsOn,
        AbsenceHalfDay halfDay,
        AbsenceStatus status,
        String note,
        String requestedByName,
        String approvedByName,
        OffsetDateTime approvedAt,
        /** Dias úteis que esta ausência consome — fora de fins de semana e feriados. */
        double workingDays,
        List<AbsenceDocumentResponseDTO> documents,
        OffsetDateTime deletedAt
) {}
