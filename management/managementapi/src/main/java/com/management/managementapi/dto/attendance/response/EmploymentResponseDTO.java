package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EmploymentResponseDTO(
        UUID id,
        UUID profileId,
        String profileName,
        LocalDate hiredAt,
        LocalDate endedAt,
        /** Todos os períodos, do mais recente para o mais antigo. */
        List<EmploymentTermResponseDTO> terms,
        EmploymentTermResponseDTO currentTerm
) {}
