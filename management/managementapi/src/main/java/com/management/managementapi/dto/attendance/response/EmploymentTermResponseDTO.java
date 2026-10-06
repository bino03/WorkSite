package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.util.UUID;

public record EmploymentTermResponseDTO(
        UUID id,
        UUID workScheduleId,
        String workScheduleName,
        int vacationDaysPerYear,
        LocalDate validFrom,
        LocalDate validTo,
        /** True no período em vigor — o único sem {@code validTo}. */
        boolean current
) {}
