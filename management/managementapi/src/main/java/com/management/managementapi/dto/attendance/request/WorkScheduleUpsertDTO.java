package com.management.managementapi.dto.attendance.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * criar ou substituir um horário. Os dias vêm sempre completos: um horário é
 * definido pelo conjunto dos seus dias, e um dia que não venha na lista não é
 * dia de trabalho.
 */
public record WorkScheduleUpsertDTO(

        @NotBlank(message = "O nome do horário é obrigatório")
        @Size(max = 120, message = "O nome do horário não pode ter mais de 120 caracteres")
        String name,

        @Size(max = 500, message = "As notas não podem ter mais de 500 caracteres")
        String notes,

        @NotEmpty(message = "Um horário tem de ter pelo menos um dia de trabalho")
        @Valid
        List<WorkScheduleDayDTO> days
) {}
