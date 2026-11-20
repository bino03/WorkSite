package com.management.managementapi.dto.attendance.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

public record WorkScheduleDayResponseDTO(
        UUID id,
        DayOfWeek weekday,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        /** null num dia de um só período. */
        @JsonFormat(pattern = "HH:mm") LocalTime lunchStart,
        @JsonFormat(pattern = "HH:mm") LocalTime lunchEnd,
        /** Derivado do almoço; 0 sem almoço. */
        int breakMinutes,
        /** Minutos de trabalho previstos neste dia, já sem a pausa. */
        long expectedMinutes
) {}
