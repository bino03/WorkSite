package com.management.managementapi.dto.attendance.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** criar os dados de emprego de um funcionário, com o primeiro período de condições. */
public record EmploymentUpsertDTO(

        @NotNull(message = "O funcionário é obrigatório")
        UUID profileId,

        @NotNull(message = "A data de admissão é obrigatória")
        LocalDate hiredAt,

        LocalDate endedAt,

        @NotNull(message = "O horário é obrigatório")
        UUID workScheduleId,

        @NotNull(message = "Os dias de férias por ano são obrigatórios")
        @PositiveOrZero(message = "Os dias de férias não podem ser negativos")
        @Max(value = 365, message = "Os dias de férias não podem passar de 365")
        Integer vacationDaysPerYear
) {}
