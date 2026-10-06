package com.management.managementapi.dto.attendance.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.management.managementapi.model.enums.AbsenceType;
import com.management.managementapi.service.attendance.DayAttendance.DayStatus;

public record DayAttendanceDTO(
        LocalDate date,
        long workedMinutes,
        long expectedMinutes,
        long overtimeMinutes,
        long latenessMinutes,
        DayStatus status,
        @JsonFormat(pattern = "HH:mm") LocalTime firstIn,
        @JsonFormat(pattern = "HH:mm") LocalTime lastOut,
        boolean incomplete,
        /** True quando o dia é falta por justificar ou está mal preenchido. */
        boolean needsAttention,
        /** O nome do feriado, quando o dia é feriado. */
        String holidayName,
        /** O tipo da ausência aprovada que cobre o dia, ou null. */
        AbsenceType absenceType
) {}
