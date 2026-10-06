package com.management.managementapi.dto.attendance.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.management.managementapi.model.enums.TimeDirection;
import com.management.managementapi.model.enums.TimeEntryChange;

/** o rasto de uma alteração: o que a picagem dizia antes. */
public record TimeEntryRevisionResponseDTO(
        UUID id,
        TimeEntryChange change,
        String changedByName,
        OffsetDateTime changedAt,
        String reason,
        OffsetDateTime previousHappenedAt,
        TimeDirection previousDirection,
        UUID previousEnterpriseId,
        String previousNote,
        OffsetDateTime previousDeletedAt
) {}
