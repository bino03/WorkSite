package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntrySource;

public record TimeEntryResponseDTO(
        UUID id,
        UUID profileId,
        String profileName,
        UUID enterpriseId,
        String enterpriseName,
        OffsetDateTime happenedAt,
        /** O dia a que a picagem pertence, em `Europe/Lisbon` — não é o dia em UTC. */
        LocalDate localDate,
        TimeDirection direction,
        TimeEntrySource source,
        String registeredByName,
        String note,
        OffsetDateTime deletedAt
) {}
