package com.management.managementapi.dto.attendance.response;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * uma pessoa da equipa num dia, para o cartão "Quem está" do painel Hoje.
 *
 * @param scheduledStart a hora de entrada que o horário prevê nesse dia; null se o
 *                       horário não prevê trabalho (folga) ou se não tem horário —
 *                       é o que separa "ainda não chegou" de "falta"
 * @param entries        as picagens vivas do dia, por ordem; a última diz se está a
 *                       trabalhar agora e em que obra
 */
public record TeamDayPersonDTO(
        UUID profileId,
        String profileName,
        LocalTime scheduledStart,
        List<TimeEntryResponseDTO> entries
) {}
