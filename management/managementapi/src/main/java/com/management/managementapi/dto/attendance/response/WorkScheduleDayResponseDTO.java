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
        int breakMinutes,
        /** Minutos de trabalho previstos neste dia, já sem a pausa. */
        long expectedMinutes
) {}
