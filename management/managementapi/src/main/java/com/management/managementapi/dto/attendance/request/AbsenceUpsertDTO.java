package com.management.managementapi.dto.attendance.request;

import java.time.LocalDate;
import java.util.UUID;

import com.management.managementapi.model.enums.AbsenceHalfDay;
import com.management.managementapi.model.enums.AbsenceType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * marcar uma ausência. Nasce sempre `PENDING`: aprovar é um passo à parte
 * (`POST /{id}/approve`), para que o ato de decidir fique registado com quem e quando.
 */
public record AbsenceUpsertDTO(

        @NotNull(message = "O funcionário é obrigatório")
        UUID profileId,

        @NotNull(message = "O tipo de ausência é obrigatório")
        AbsenceType type,

        @NotNull(message = "A data de início é obrigatória")
        LocalDate startsOn,

        @NotNull(message = "A data de fim é obrigatória")
        LocalDate endsOn,

        /** Null é tratado como dia inteiro. */
        AbsenceHalfDay halfDay,

        @Size(max = 500, message = "A nota não pode ter mais de 500 caracteres")
        String note
) {}
