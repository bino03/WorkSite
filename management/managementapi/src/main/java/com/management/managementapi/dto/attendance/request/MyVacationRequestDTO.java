package com.management.managementapi.dto.attendance.request;

import java.time.LocalDate;

import com.management.managementapi.model.enums.AbsenceHalfDay;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * o funcionário pede férias para si (fase 5b). Sem {@code profileId} nem {@code type} de
 * propósito: o perfil vem da sessão e o tipo é sempre {@code VACATION} — baixas e faltas
 * justificadas continuam a ser lançadas pelo escritório (decidido a 2026-11-23).
 */
public record MyVacationRequestDTO(

        @NotNull(message = "A data de início é obrigatória")
        LocalDate startsOn,

        @NotNull(message = "A data de fim é obrigatória")
        LocalDate endsOn,

        /** Null é tratado como dia inteiro. */
        AbsenceHalfDay halfDay,

        @Size(max = 500, message = "A nota não pode ter mais de 500 caracteres")
        String note
) {}
