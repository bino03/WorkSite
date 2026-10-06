package com.management.managementapi.dto.attendance.request;

import java.time.LocalDate;

import com.management.managementapi.model.enums.HolidayScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record HolidayUpsertDTO(

        @NotNull(message = "A data do feriado é obrigatória")
        LocalDate date,

        @NotBlank(message = "O nome do feriado é obrigatório")
        @Size(max = 120, message = "O nome não pode ter mais de 120 caracteres")
        String name,

        @NotNull(message = "O âmbito é obrigatório")
        HolidayScope scope,

        /** Obrigatório nos municipais, recusado nos nacionais. */
        @Size(max = 120, message = "O concelho não pode ter mais de 120 caracteres")
        String municipality
) {}
