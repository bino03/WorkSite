package com.management.managementapi.dto.attendance.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * mudar as condições de um funcionário a partir de uma data. Não altera o período
 * em vigor: fecha-o no dia anterior e abre um novo, para que os meses já passados
 * continuem a ser calculados com o que estava em vigor na altura.
 */
public record EmploymentTermDTO(

        @NotNull(message = "O horário é obrigatório")
        UUID workScheduleId,

        @NotNull(message = "Os dias de férias por ano são obrigatórios")
        @PositiveOrZero(message = "Os dias de férias não podem ser negativos")
        @Max(value = 365, message = "Os dias de férias não podem passar de 365")
        Integer vacationDaysPerYear,

        @NotNull(message = "A data de início é obrigatória")
        LocalDate validFrom
) {}
